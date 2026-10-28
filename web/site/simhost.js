// ReefLive web: the page's one door to the simulation (SimHost). The sim runs in a Web Worker (simworker.js), so the
// page's thread only draws the tank and runs the UI; with `?noworker` in the URL, or where module workers fail, it runs
// on the page's thread as it always did. Every page → sim call goes through here, so the page never knows which.
//
// The calls fall into three kinds:
//   view  the 3D view, the camera and the static tables: answered on the page thread, synchronously, always.
//   send  a command for the sim: fire and forget, applied in the order sent (and before any later call).
//   call  needs the sim's answer: returns a Promise. Code that acts on the answer awaits it, so the answer is fresh.
// Every call also has a synchronous `peek` (live.peek.state()): the latest answer this page has seen for the same
// arguments (null before the first), and it asks for a fresh one. For drawing only, never for deciding what to do;
// await the call first when the drawing must be up to date (a card that opens).
//
// Inventory (ReefLive = live, ReefSandbox = sb, ReefMyTank = my; Kotlin ReefLive is the view, ReefSim the sim):
//   live view: setVisible follow select followZoom followOrbit setViewLight shelfOpen aimArm gearTaps shelfRow shelfPick
//              shelfLocked shelfHidden resetCamera nursery showRoom musicOpen musicState roomVisible screensaver subView
//              setQuality cam cinema bagDropX scapeShapes parameters readingWarning cycleStatus acclimationPlan
//              scapeTouch touchTank (the touch is the view's; the rock / ripple goes to the sim as a command)
//              bagState coralPick netFlush subState (read from the sim's latest snapshot, the one the view draws)
//   live send: setSpeed setDynamicRocks restoreRocks applyReadings scapeUndo scapeClear submarine steerSub subWatch
//              subWatchNext (the view forwards them to ReefSim, in order with its own taps and fingers)
//   live call: start state
//   sb view:   bubbles (projected over the view's camera)
//   sb call:   catalog setMode act applyParam fix feedAt spotAct panel animals animal hunger corals fixes spotMenu micro
//              sound timelapse fact radio
//   my view:   storeBubbles (the store's people are the view's)
//   my send:   startOver setPace rename testedWater dismissAway decorUse decorPreview storeCounter answerTrade leaveStore
//              visitCancel warmStores hidden visible save
//   my call:   info builds paces records economy supplies away decorShop decorBuy roomTanks stores visit visitItems trade
//              offers displaySpecies start useSupply useFishFood buyRecord collectHappy turnStep checkout sell showTank
//              visitReady buildStep building
import ctors from './simwire-hook.js';
import { ReefLive, ReefSim, ReefSandbox, ReefMyTank, ReefWire } from './kotlin/ReefLive-web.mjs';
import { Decoder, classPrint } from './simwire.js';

/** The classes the Kotlin modules defined as they loaded (before any Kotlin ran): the worker's must be the same. */
const LOADED = ctors.length;
const K = { view: ReefLive.getInstance(), sim: ReefSim.getInstance(), sb: ReefSandbox.getInstance(), my: ReefMyTank.getInstance() };
/** Kotlin object behind each facade's send / call. */
const SIDE = { live: 'sim', sb: 'sb', my: 'my' };

const words = (s) => s.trim().split(/\s+/);
export const API = {
  live: {
    view: words(`setVisible follow select followZoom followOrbit setViewLight shelfOpen aimArm gearTaps shelfRow shelfPick
      shelfLocked shelfHidden resetCamera nursery showRoom musicOpen musicState roomVisible screensaver subView setQuality cam
      cinema bagDropX scapeShapes parameters readingWarning cycleStatus acclimationPlan scapeTouch touchTank bagState
      coralPick netFlush subState`),
    send: words('setSpeed setDynamicRocks restoreRocks applyReadings scapeUndo scapeClear submarine steerSub subWatch subWatchNext'),
    call: words('state'),
  },
  sb: {
    view: words('bubbles'),
    send: [],
    call: words(`catalog setMode act applyParam fix feedAt spotAct panel animals animal hunger corals fixes spotMenu micro sound
      timelapse fact radio`),
  },
  my: {
    view: words('storeBubbles'),
    send: words(`startOver setPace rename testedWater dismissAway decorUse decorPreview storeCounter answerTrade leaveStore
      visitCancel warmStores hidden visible save`),
    call: words(`info builds paces records economy supplies away decorShop decorBuy roomTanks stores visit visitItems trade offers
      displaySpecies start useSupply useFishFood buyRecord collectHappy turnStep checkout sell showTank visitReady buildStep
      building`),
  },
};
/**
 * Calls whose answer is used up by asking (economy() hands out the sand dollars earned since the last ask, away() runs
 * the next chunk of a catch-up) or that change the tank: a peek never asks for these, it only shows the last answer.
 */
const PEEK_ONLY = new Set(['my.economy', 'my.away', 'my.visitReady', 'my.buildStep', 'my.start', 'my.useSupply', 'my.useFishFood',
  'my.buyRecord', 'my.collectHappy', 'my.decorBuy', 'my.turnStep', 'my.checkout', 'my.sell', 'my.showTank', 'my.visit',
  'sb.setMode', 'sb.act', 'sb.applyParam', 'sb.fix', 'sb.feedAt', 'sb.spotAct', 'sb.timelapse', 'sb.sound']);

/** The sim on the page's thread: the Kotlin objects are called directly. */
const inThread = {
  name: 'page',
  view: (o, m, a) => K.view[m](...a),
  // live's commands go through the view (in order with its taps); the view calls ReefSim
  send: (o, m, a) => { (o === 'live' ? K.view : K[SIDE[o]])[m](...a); },
  call: (o, m, a) => {
    try { return Promise.resolve(K[SIDE[o]][m](...a)); } catch (e) { return Promise.reject(e); }
  },
  peek: (o, m, a) => K[SIDE[o]][m](...a),
};

/** The sim in a worker: commands and calls are messages, answered in order. */
function viaWorker(w) {
  let seq = 0;
  const waiting = new Map();
  return {
    name: 'worker',
    waiting,
    view: inThread.view,
    send: (o, m, a) => { if (o === 'live') K.view[m](...a); else w.postMessage({ t: 'send', o: SIDE[o], m, a }); },
    call: (o, m, a) => new Promise((resolve, reject) => {
      const id = ++seq;
      waiting.set(id, { resolve, reject });
      w.postMessage({ t: 'call', id, o: SIDE[o], m, a });
    }),
  };
}

let transport = inThread;

const latest = new Map();
const keyOf = (o, m, a) => (a.length ? `${o}.${m}${JSON.stringify(a)}` : `${o}.${m}`);
function remember(o, m, a, v) {
  // economy(): the gains go to whoever called; a peek shows the rest
  latest.set(keyOf(o, m, a), o === 'my' && m === 'economy' && typeof v === 'string' ? v.replace(/"gains":\[.*\]\}$/, '"gains":[]}') : v);
  return v;
}

function facade(o) {
  const f = { peek: {} };
  for (const m of API[o].view) f[m] = (...a) => transport.view(o, m, a);
  for (const m of API[o].send) f[m] = (...a) => transport.send(o, m, a);
  for (const m of API[o].call) {
    f[m] = (...a) => transport.call(o, m, a).then((v) => remember(o, m, a, v));
    f.peek[m] = (...a) => {
      if (transport === inThread && !PEEK_ONLY.has(`${o}.${m}`)) return remember(o, m, a, inThread.peek(o, m, a));
      if (!PEEK_ONLY.has(`${o}.${m}`)) f[m](...a).catch(() => {});
      return latest.has(keyOf(o, m, a)) ? latest.get(keyOf(o, m, a)) : null;
    };
  }
  return f;
}

export const live = facade('live');
export const sb = facade('sb');
export const my = facade('my');

/** Where the sim runs: 'worker' or 'page'. */
export const where = () => transport.name;
let lastSnap = null;
/** The newest snapshot the view draws (QA: compare the worker's copy with the page's own sim). */
export const snapshot = () => (transport === inThread ? K.sim.latest() : lastSnap);

const STORE_KEYS = ['reeflive.mytank', 'reeflive.mytank.bak'];
const reducedMotion = () => { try { return matchMedia('(prefers-reduced-motion: reduce)').matches; } catch { return false; } };

/**
 * Starts the live tank on [canvas] (ReefLive.start: [events] gets onStatus, onPick, onError...): the sim in a worker
 * unless the URL says `?noworker` or the worker cannot start, then on this thread. Resolves once both are running.
 */
live.start = async (canvas, base, quality, events) => {
  const fail = (t) => { console.error(t); try { events.onError?.(t?.message || String(t)); } catch { /* the page's handler */ } throw t; };
  const noWorker = new URLSearchParams(location.search).has('noworker') || typeof Worker === 'undefined';
  if (!noWorker) {
    let w = null;
    try { w = startWorker(base); } catch (e) { console.warn('[SimHost] no worker:', e); }
    if (w) {
      const view = K.view.start(canvas, base, quality, events);
      try {
        await w.ready;
        transport = w.transport;
        await view;
        return;
      } catch (e) {
        console.warn('[SimHost] the sim worker did not start, running the sim on the page:', e?.message || e);
        w.close();
        // the view keeps loading meanwhile; it draws this thread's sim from here on
        try { useInThread(); await K.sim.boot(base); } catch (err) { fail(err); }
        try { await view; } catch (err) { fail(err); }
        return;
      }
    }
  }
  try {
    useInThread();
    await K.sim.boot(base);
    await K.view.start(canvas, base, quality, events);
  } catch (err) { fail(err); }
};

function useInThread() {
  transport = inThread;
  K.sim.setReducedMotion(reducedMotion());
  K.view.bindInThread();
}

/** Starts the worker; `ready` resolves once its sim is built (and rejects if it cannot start). */
function startWorker(base) {
  const w = new Worker(new URL('./simworker.js', import.meta.url), { type: 'module' });
  const t = viaWorker(w);
  const dec = new Decoder({ ctors, canon: ReefWire.getInstance().canon() });
  let unpack = 0, unpackAt = performance.now();
  let alive = false, closed = false;
  let ok, ko;
  const ready = new Promise((a, b) => { ok = a; ko = b; });
  const lost = (why) => {
    for (const p of t.waiting.values()) p.reject(new Error(why));
    t.waiting.clear();
  };
  w.onmessage = (e) => {
    const d = e.data;
    switch (d.t) {
      case 'hello': alive = true; break;
      case 'ready': ok(); break;
      case 'failed': ko(new Error(d.why)); break;
      case 'reply': {
        const p = t.waiting.get(d.id);
        if (!p) break;
        t.waiting.delete(d.id);
        if (d.err !== undefined) p.reject(new Error(d.err)); else p.resolve(d.v);
        break;
      }
      case 'snap': {
        const t0 = performance.now();
        K.view.mirror(lastSnap = dec.decode(d.m));
        unpack += performance.now() - t0;
        break;
      }
      case 'fx': K.view.applyFx(d.op, dec.decode(d.m)); break;
      case 'store':
        try { if (d.value == null) localStorage.removeItem(d.key); else localStorage.setItem(d.key, d.value); } catch { /* private mode */ }
        break;
      case 'stats': {
        const now = performance.now();
        K.view.workerStats(d.sim, d.pack, unpack * 1000 / Math.max(1, now - unpackAt));
        unpack = 0; unpackAt = now;
        break;
      }
      default: break;
    }
  };
  w.onerror = (e) => {
    e.preventDefault?.();
    const why = `sim worker error: ${e.message || 'could not load'}`;
    console.error(why);
    ko(new Error(why));
    lost(why);
  };
  // a browser that cannot run module workers never says hello
  setTimeout(() => { if (!alive && !closed) ko(new Error('the worker did not answer')); }, 20000);
  const store = {};
  for (const k of STORE_KEYS) { try { const v = localStorage.getItem(k); if (v != null) store[k] = v; } catch { /* private mode */ } }
  w.postMessage({ t: 'boot', base, store, reducedMotion: reducedMotion(), loaded: LOADED, print: classPrint(ctors, LOADED) });
  K.view.bindRemote((m, a) => w.postMessage({ t: 'send', o: 'sim', m, a }));
  return { ready, transport: t, close: () => { closed = true; lost('worker closed'); w.terminate(); } };
}

/**
 * Console / QA handles (window.reeflive, window.reefsandbox, window.reefmytank), named like the Kotlin objects: view
 * and send calls go straight through; a call answers synchronously with its latest answer (scripts poll it). The
 * Promise versions are the module's exports (import { live } from './simhost.js').
 */
function qa(o, f) {
  return new Proxy({}, {
    get(_, m) {
      if (typeof m !== 'string') return undefined;
      if (m === 'where') return where;
      if (m === 'snapshot') return snapshot;
      if (f.peek[m]) return (...a) => (transport === inThread ? K[SIDE[o]][m](...a) : f.peek[m](...a));
      return f[m];
    },
  });
}
window.reeflive = qa('live', live);
window.reefsandbox = qa('sb', sb);
window.reefmytank = qa('my', my);
