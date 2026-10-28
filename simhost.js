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

/**
 * The sim in a worker: commands and calls are messages, answered in order. The worker behind it can change (a restart
 * after a crash): `hold()` keeps new messages back meanwhile, `release()` sends them to the new worker, `flushTo()`
 * runs them on the page's thread when the sim moved there.
 */
function viaWorker() {
  let seq = 0;
  let cur = null;
  let held = null;
  const waiting = new Map();
  const t = {
    name: 'worker',
    waiting,
    view: inThread.view,
    /** A message for the sim's worker (in order; kept back while a restart is under way). */
    post: (msg) => { if (held) held.push(msg); else cur?.postMessage(msg); },
    use: (w) => { cur = w; },
    hold: () => { held = held || []; },
    release: () => { const h = held || []; held = null; for (const m of h) cur?.postMessage(m); },
    /** The sim now runs on the page's thread: what was held runs there, in order. */
    flushTo: () => {
      const h = held || [];
      held = null;
      for (const m of h) {
        if (m.t === 'send') { try { K[m.o][m.m](...m.a); } catch (e) { console.error(e); } continue; }
        const p = waiting.get(m.id);
        waiting.delete(m.id);
        if (!p) continue;
        try { p.resolve(K[m.o][m.m](...m.a)); } catch (e) { p.reject(e); }
      }
    },
    /** Every call still waiting for an answer fails (the worker is gone). */
    lost: (why) => {
      const all = [...waiting.values()];
      waiting.clear();
      held = null;
      for (const p of all) p.reject(new Error(why));
    },
    send: (o, m, a) => { if (o === 'live') K.view[m](...a); else t.post({ t: 'send', o: SIDE[o], m, a }); },
    call: (o, m, a) => new Promise((resolve, reject) => {
      const id = ++seq;
      waiting.set(id, { resolve, reject });
      t.post({ t: 'call', id, o: SIDE[o], m, a });
    }),
  };
  return t;
}

let transport = inThread;
/** The sim's mode and speed as the page last set them: a restarted sim is put back there. */
const resume = { mode: null, speed: null };

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
  for (const m of API[o].send) f[m] = (...a) => { if (o === 'live' && m === 'setSpeed') resume.speed = a; transport.send(o, m, a); };
  for (const m of API[o].call) {
    f[m] = (...a) => {
      if (o === 'sb' && m === 'setMode') resume.mode = a;
      return transport.call(o, m, a).then((v) => remember(o, m, a, v));
    };
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
    bootBase = base;
    let sess = null;
    try { sess = startWorker(base); } catch (e) { console.warn('[SimHost] no worker:', e); }
    if (sess) {
      const view = K.view.start(canvas, base, quality, events);
      try {
        await sess.ready;
        sess.up();
        await view;
        return;
      } catch (e) {
        console.warn('[SimHost] the sim worker did not start, running the sim on the page:', e?.message || e);
        sess.close();
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

// ---------------------------------------------------------------------------------------------------- page-side storage

/**
 * The page's copy of My tank's saves as the worker last wrote them (it is also in localStorage; this is the same text, kept
 * so that a page that is closing, or a worker that has to restart, never waits on the worker).
 */
const mirror = new Map();
const touched = new Set();
function loadMirror() {
  for (const k of STORE_KEYS) { try { const v = localStorage.getItem(k); if (v != null) mirror.set(k, v); } catch { /* private mode */ } }
}
loadMirror();
function writeKey(k) {
  try {
    const v = mirror.get(k);
    if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v);
  } catch { /* private mode, or full: tried again at the next flush */ }
}
/** Mirrored copy -> localStorage, synchronously: the latest save the worker made, whatever the worker is doing now. */
function flushMirror() { for (const k of touched) writeKey(k); }
function onStore(key, value) {
  if (value == null) mirror.delete(key); else mirror.set(key, value);
  touched.add(key);
  writeKey(key);
}
// the page is going away (hidden, or closed outright): write the mirrored copy now, without asking the worker
document.addEventListener('visibilitychange', () => { if (document.hidden) flushMirror(); });
window.addEventListener('pagehide', flushMirror);

// ---------------------------------------------------------------------------------------------------- the worker

/** Heartbeat: a ping every [every] ms; a worker that has not answered one in [timeout] ms is dead. (QA changes them.) [stable]: see lostSim. */
const heartbeat = { every: 2000, timeout: 15000, stable: 60000 };
const READY_TIMEOUT_MS = 120000;
const MAX_RESTARTS = 2;
let bootBase = '';
let tw = null;          // the worker transport (one for the page's life: the worker behind it can change)
let current = null;     // the worker session in use
let restarts = 0;
let stableSince = 0;
const qaTest = { failBoots: 0 };

/** A small, non-blocking note at the bottom of the page. */
function notice(text) {
  try {
    const el = document.createElement('div');
    el.setAttribute('role', 'status');
    el.className = 'simnotice';
    el.textContent = text;
    el.style.cssText = 'position:fixed;left:50%;bottom:16px;transform:translateX(-50%);max-width:min(92vw,420px);padding:8px 14px;' +
      'border-radius:10px;background:rgba(20,30,40,.92);color:#fff;font:13px/1.35 system-ui,sans-serif;z-index:99999;' +
      'pointer-events:none;box-shadow:0 2px 10px rgba(0,0,0,.35)';
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 8000);
  } catch { /* no page to tell */ }
}

/**
 * Starts a worker; `ready` resolves once its sim is built (and rejects if it cannot start). Until `up()` is called a
 * failure only rejects `ready`; after, a worker error or a missing heartbeat means the sim is lost (lostSim).
 */
function startWorker(base) {
  if (!tw) tw = viaWorker();
  const w = new Worker(new URL('./simworker.js', import.meta.url), { type: 'module' });
  const dec = new Decoder({ ctors, canon: ReefWire.getInstance().canon() });
  let unpack = 0, unpackAt = performance.now();
  let alive = false, closed = false, running = false;
  let ok, ko;
  const ready = new Promise((a, b) => { ok = a; ko = b; });
  let beat = 0, pinging = 0, pingN = 0, helloTimer = 0, readyTimer = 0;
  const sess = { w, ready };
  const stop = () => { closed = true; clearInterval(beat); clearTimeout(readyTimer); clearTimeout(helloTimer); };
  const die = (why) => {
    if (closed) return;
    stop();
    try { w.terminate(); } catch { /* gone */ }
    if (!running) { ko(new Error(why)); return; }
    if (current === sess) lostSim(why);
  };
  w.onmessage = (e) => {
    const d = e.data;
    switch (d.t) {
      case 'hello': alive = true; break;
      case 'ready': ok(); break;
      case 'failed': ko(new Error(d.why)); break;
      case 'pong': if (d.n === pingN) pinging = 0; break;
      case 'reply': {
        const p = tw.waiting.get(d.id);
        if (!p) break;
        tw.waiting.delete(d.id);
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
      case 'store': onStore(d.key, d.value); break;
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
    die(why);
  };
  w.onmessageerror = () => die('sim worker message error');
  // a browser that cannot run module workers never says hello
  helloTimer = setTimeout(() => { if (!alive) die('the worker did not answer'); }, 20000);
  readyTimer = setTimeout(() => die('the worker took too long to start'), READY_TIMEOUT_MS);
  const store = {};
  for (const [k, v] of mirror) store[k] = v;
  const testFail = qaTest.failBoots > 0;
  if (testFail) qaTest.failBoots--;
  w.postMessage({ t: 'boot', base, store, reducedMotion: reducedMotion(), loaded: LOADED, print: classPrint(ctors, LOADED), testFail });
  /** The sim runs here from now on. */
  sess.up = () => {
    if (closed) throw new Error('the worker is gone');
    running = true;
    clearTimeout(readyTimer);
    current = sess;
    tw.use(w);
    transport = tw;
    K.view.bindRemote((m, a) => tw.post({ t: 'send', o: 'sim', m, a }));
    pinging = 0;
    beat = setInterval(() => {
      if (pinging) { if (performance.now() - pinging > heartbeat.timeout) die('the sim worker stopped answering'); return; }
      pinging = performance.now();
      w.postMessage({ t: 'ping', n: ++pingN });
    }, heartbeat.every);
  };
  sess.kill = () => { stop(); try { w.terminate(); } catch { /* gone */ } };
  sess.close = () => { sess.kill(); tw.lost('worker closed'); };
  return sess;
}

/**
 * The worker is gone after boot (error, or no heartbeat): every call waiting for it fails, then the sim restarts in a
 * new worker from the last mirrored My tank. After MAX_RESTARTS restarts that do not hold, the sim moves to the page's
 * thread (as with ?noworker), with no reload. New calls wait meanwhile and are answered by whichever comes up.
 */
async function lostSim(why) {
  console.error(`[SimHost] ${why}`);
  current?.kill();
  current = null;
  tw.lost(why);
  tw.hold();
  flushMirror();
  // a restarted worker that lasted [stable] ms was a good restart: this crash gets its tries afresh
  if (performance.now() - stableSince > heartbeat.stable) restarts = 0;
  while (restarts < MAX_RESTARTS) {
    restarts++;
    const sess = startWorker(bootBase);
    try {
      await sess.ready;
      sess.up();
      stableSince = performance.now();
      tw.release();
      replayState();
      notice('The tank engine restarted. Your tank was restored from its last save.');
      return;
    } catch (e) {
      console.warn('[SimHost] the worker restart failed:', e?.message || e);
      sess.kill();
      current = null;
    }
  }
  // the restarts failed: the sim runs on this thread
  try {
    K.sim.setReducedMotion(reducedMotion());
    K.view.bindInThread();
    await K.sim.boot(bootBase);
    transport = inThread;
    replayState();
    tw.flushTo();
    notice('The tank engine could not restart, so it now runs in the page. Your tank was restored from its last save.');
  } catch (e) {
    console.error('[SimHost] the sim could not run on the page either:', e);
    transport = inThread;
    tw.lost(`sim unavailable: ${e?.message || e}`);
    notice('The tank engine stopped. Please reload the page.');
  }
}

/** Puts a restarted sim back in the mode and at the speed the page last set. */
function replayState() {
  try {
    if (resume.mode) transport.send('sb', 'setMode', resume.mode);
    if (resume.speed) K.view.setSpeed(...resume.speed);
  } catch (e) { console.error(e); }
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
// the Promise facade itself, for QA scripts (the page is one bundle: importing simhost.js again would load a second copy)
window.reefhost = { live, sb, my, where, snapshot, heartbeat, test: {
  /** QA: the worker dies without a word (the heartbeat notices). */
  crash: () => current?.w.terminate(),
  /** QA: the worker reports an error. */
  error: () => current?.w.dispatchEvent(new ErrorEvent('error', { message: 'test error' })),
  /** QA: the worker stops answering (stuck in a loop). */
  hang: () => current?.w.postMessage({ t: 'test', hang: true }),
  /** QA: the next [n] workers started fail to boot. */
  failBoots: (n) => { qaTest.failBoots = n; },
} };
window.reeflive = qa('live', live);
window.reefsandbox = qa('sb', sb);
window.reefmytank = qa('my', my);
