// ReefLive web: how Kotlin objects (snapshots, effect arguments) travel from the sim worker to the page. The page and
// the worker run the same compiled Kotlin, so an object is sent as its class (a number both sides agree on, see
// simwire-hook.js) and its fields, and comes back on the other side as an object of that very class, methods and all.
//
// An encoded value is a plain structured-clone tree. Numbers, strings, booleans, null and undefined go as they are;
// everything else is an array whose first element says what it is:
//   [shape, ...fields]        a Kotlin object; shape (>= 0) names its class and field names (sent once per shape)
//   [ARR, ...items]           a JS array (Kotlin Array, an ArrayList's storage)
//   [VIEW, kind, at, length]  a typed array (FloatArray, IntArray...): its bytes are in the message's one arena buffer,
//                             transferred, never copied; the page reads them through views on it
//   [REF, i]                  the i-th object of this message again (shared or cyclic)
//   [CANON, i]                the i-th of the objects both sides hold themselves (enum entries, constants)
//   [DEF, id, body]/[STICKY, id]  a value the page keeps between snapshots (sent once, then by id while still in use)
//   [TYPED, type, body]       a Kotlin array tagged with $type$ (BooleanArray, CharArray, LongArray)
//   [PLAIN, keys, ...values]  a plain JS object
//   [FN]                      a function (never needed on the other side): null
const ARR = -1, REF = -2, CANON = -3, STICKY = -4, TYPED = -5, PLAIN = -6, FN = -7, DEF = -8, VIEW = -9;
const KINDS = [Float32Array, Float64Array, Int32Array, Int16Array, Int8Array, Uint8Array, Uint16Array, Uint32Array, Uint8ClampedArray];

/**
 * A short fingerprint of the class table as the modules defined it ([n] classes: the count right after they loaded):
 * the worker and the page must have the same one. (Classes defined later, on one side only, are matched by name.)
 */
export function classPrint(ctors, n = ctors.length) {
  let h = 2166136261;
  for (const c of ctors.slice(0, n)) { const n = `${c.name}/${c.$metadata$?.simpleName ?? ''};`; for (let i = 0; i < n.length; i++) h = Math.imul(h ^ n.charCodeAt(i), 16777619); }
  return `${n}:${(h >>> 0).toString(36)}`;
}

/** Worker side. [canon] objects, [sticky] / [forbidden] classes: see com.reeflive.web.ReefWire. */
export class Encoder {
  constructor({ ctors, canon, sticky = [], forbidden = [] }) {
    this.ctors = ctors;
    this.ctorIndex = new Map(ctors.map((c, i) => [c, i]));
    this.canon = new Map();
    canon.forEach((o, i) => { if (o !== null && typeof o === 'object' && !this.canon.has(o)) this.canon.set(o, i); });
    this.stickyClasses = new Set(sticky);
    this.forbidden = new Set(forbidden);
    this.shapes = new Map();
    this.nextShape = 0;
    this.stickyIds = new WeakMap();
    this.nextSticky = 0;
    this.liveSticky = new Set();
    this.warned = new Set();
  }

  /**
   * Encodes [value]. With [sticky] (the snapshot stream) values of the sticky classes and the [keep] objects are sent
   * once and then by id while each message still uses them. Returns { v, shapes, arena, sticky, count }: post it
   * with [arena] transferred.
   */
  encode(value, { sticky = false, keep = null } = {}) {
    this.newShapes = [];
    this.refs = new Map();
    this.views = [];
    this.arenaSize = 0;
    this.sticky = sticky;
    this.keep = keep ? new Set(keep.filter((x) => x !== null && typeof x === 'object')) : null;
    this.curSticky = new Set();
    this.count = 0;
    const v = this.enc(value);
    let arena = null;
    if (this.views.length) {
      arena = new ArrayBuffer(this.arenaSize);
      const u8 = new Uint8Array(arena);
      for (const [ta, at] of this.views) u8.set(new Uint8Array(ta.buffer, ta.byteOffset, ta.byteLength), at);
    }
    if (sticky) this.liveSticky = this.curSticky;
    this.refs = null; this.views = null; this.keep = null;
    return { v, shapes: this.newShapes, arena, sticky, count: this.count };
  }

  warn(what) {
    if (this.warned.has(what)) return;
    this.warned.add(what);
    console.warn(`[simwire] ${what}`);
  }

  enc(x) {
    if (x === null || typeof x !== 'object') return typeof x === 'function' ? [FN] : x;
    const c = this.canon.get(x);
    if (c !== undefined) return [CANON, c];
    const r = this.refs.get(x);
    if (r !== undefined) return [REF, r];
    if (ArrayBuffer.isView(x)) return this.view(x);
    if (Array.isArray(x)) {
      this.refs.set(x, this.refs.size);
      this.count++;
      const out = [ARR];
      for (let i = 0; i < x.length; i++) out.push(this.enc(x[i]));
      return x.$type$ !== undefined ? [TYPED, x.$type$, out] : out;
    }
    const proto = Object.getPrototypeOf(x);
    if (proto === Object.prototype || proto === null) return this.plain(x);
    const ctor = proto.constructor;
    if (this.forbidden.has(ctor)) { this.warn(`a ${ctor.name} (live sim object) was left out of a message`); return null; }
    if (this.sticky && (this.stickyClasses.has(ctor) || this.keep?.has(x))) {
      let id = this.stickyIds.get(x);
      if (id !== undefined && this.liveSticky.has(id)) { this.curSticky.add(id); return [STICKY, id]; }
      if (id === undefined) { id = this.nextSticky++; this.stickyIds.set(x, id); }
      this.curSticky.add(id);
      return [DEF, id, this.object(x, ctor)];
    }
    return this.object(x, ctor);
  }

  object(x, ctor) {
    let ci = this.ctorIndex.get(ctor);
    if (ci === undefined && this.ctors.length !== this.ctorIndex.size) {
      this.ctorIndex = new Map(this.ctors.map((k, i) => [k, i]));
      ci = this.ctorIndex.get(ctor);
    }
    if (ci === undefined) { this.warn(`unknown class ${ctor?.name}: sent as a plain object`); return this.plain(x); }
    this.refs.set(x, this.refs.size);
    this.count++;
    const keys = Object.keys(x);
    let list = this.shapes.get(ctor);
    if (!list) this.shapes.set(ctor, list = []);
    let shape = -1;
    for (const s of list) {
      const k = s.keys;
      if (k.length !== keys.length) continue;
      let same = true;
      for (let i = 0; i < k.length; i++) if (k[i] !== keys[i]) { same = false; break; }
      if (same) { shape = s.id; break; }
    }
    if (shape < 0) {
      shape = this.nextShape++;
      list.push({ keys, id: shape });
      this.newShapes.push([shape, ci, keys, ctor.name]);
    }
    const out = [shape];
    for (let i = 0; i < keys.length; i++) out.push(this.enc(x[keys[i]]));
    return out;
  }

  plain(x) {
    this.refs.set(x, this.refs.size);
    this.count++;
    const keys = Object.keys(x);
    const out = [PLAIN, keys];
    for (let i = 0; i < keys.length; i++) out.push(this.enc(x[keys[i]]));
    return out;
  }

  view(x) {
    const kind = KINDS.indexOf(x.constructor);
    if (kind < 0) { this.warn(`unsupported ${x.constructor?.name}`); return null; }
    this.refs.set(x, this.refs.size);
    const at = (this.arenaSize + 7) & ~7;
    this.arenaSize = at + x.byteLength;
    this.views.push([x, at]);
    const v = [VIEW, kind, at, x.length];
    return x.$type$ !== undefined ? [TYPED, x.$type$, v] : v;
  }
}

/** Page side: turns what an [Encoder] made back into objects of the page's own Kotlin classes. */
export class Decoder {
  constructor({ ctors, canon }) {
    this.ctors = ctors;
    this.canon = canon;
    this.shapes = [];
    this.kept = new Map();
  }

  decode(msg) {
    for (const [id, ci, keys, name] of msg.shapes) {
      // the class tables match at boot; a class defined later on one side only would be found by name
      let ctor = this.ctors[ci];
      if (ctor?.name !== name) ctor = this.ctors.find((c) => c.name === name) ?? ctor;
      if (!ctor) throw new Error(`simwire: no class ${name}`);
      this.shapes[id] = { proto: ctor.prototype, keys };
    }
    this.refs = [];
    this.arena = msg.arena;
    this.used = msg.sticky ? new Set() : null;
    const v = this.dec(msg.v);
    // a kept value the newest snapshot no longer uses is dropped here, as the worker drops it
    if (msg.sticky) for (const id of this.kept.keys()) if (!this.used.has(id)) this.kept.delete(id);
    this.refs = null; this.arena = null; this.used = null;
    return v;
  }

  dec(x) {
    if (!Array.isArray(x)) return x;
    const t = x[0];
    if (t >= 0) {
      const s = this.shapes[t];
      const o = Object.create(s.proto);
      this.refs.push(o);
      const k = s.keys;
      for (let i = 0; i < k.length; i++) o[k[i]] = this.dec(x[i + 1]);
      return o;
    }
    switch (t) {
      case ARR: {
        const a = [];
        this.refs.push(a);
        for (let i = 1; i < x.length; i++) a.push(this.dec(x[i]));
        return a;
      }
      case VIEW: { const v = new KINDS[x[1]](this.arena, x[2], x[3]); this.refs.push(v); return v; }
      case REF: return this.refs[x[1]];
      case CANON: return this.canon[x[1]];
      case STICKY: this.used?.add(x[1]); return this.kept.get(x[1]);
      case DEF: { const o = this.dec(x[2]); this.kept.set(x[1], o); this.used?.add(x[1]); return o; }
      case TYPED: { const a = this.dec(x[2]); a.$type$ = x[1]; return a; }
      case PLAIN: {
        const keys = x[1];
        const o = {};
        this.refs.push(o);
        for (let i = 0; i < keys.length; i++) o[keys[i]] = this.dec(x[i + 2]);
        return o;
      }
      case FN: return null;
      default: throw new Error(`simwire: bad tag ${t}`);
    }
  }
}
