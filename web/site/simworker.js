// ReefLive web: the simulation in a Web Worker (module worker), so the page's thread only draws and runs the UI.
// It runs the same Kotlin as the page (kotlin/ReefLive-web.mjs): ReefSim steps the tanks on its own clock, ReefSandbox
// and ReefMyTank answer the page's calls, and after every step the newest snapshot goes to the page (simwire.js).
// The page side is SimHost (simhost.js), which also runs all of this on its own thread when workers are off.
import ctors from './simwire-hook.js';
import { ReefSim, ReefSandbox, ReefMyTank, ReefWire } from './kotlin/ReefLive-web.mjs';
import { Encoder, classPrint } from './simwire.js';

const LOADED = ctors.length;   // the classes the Kotlin modules defined as they loaded (the page checks it is the same table)
const K = { sim: ReefSim.getInstance(), sb: ReefSandbox.getInstance(), my: ReefMyTank.getInstance() };
const wire = ReefWire.getInstance();
let enc = null;
let running = false;
let timer = 0;
let last = 0;
let sent;             // the snapshot sent last (undefined: none yet)
const FRAME_MS = 16;  // the page's frame rate: the 20 Hz ticks land as they did on the page
const stats = { sim: 0, pack: 0, at: 0, n: 0 };

const post = (msg, transfer) => self.postMessage(msg, transfer || []);
const packed = (m) => (m.arena ? [m.arena] : []);

/** An effect for the 3D view or a page event (Kotlin PostFx). */
function fx(op, args) {
  const m = enc.encode(args);
  post({ t: 'fx', op, m }, packed(m));
}

/** Sends the newest snapshot if it changed since the last one sent. */
function sendSnapshot() {
  const s = K.sim.latest();
  if (s === sent) return;
  sent = s;
  const t0 = performance.now();
  const m = enc.encode(s == null ? null : wire.snapshot(Date.now()), { sticky: true, keep: wire.slowParts() });
  post({ t: 'snap', m }, packed(m));
  stats.pack += performance.now() - t0;
}

function loop() {
  timer = 0;
  if (!running) return;
  const now = performance.now();
  const dt = last ? Math.min(0.25, Math.max(0, (now - last) / 1000)) : 0;
  last = now;
  try { K.sim.frame(dt); } catch (e) { console.error(e); }
  stats.sim += performance.now() - now;
  try { sendSnapshot(); } catch (e) { console.error(e); }
  if (now - stats.at >= 1000) {
    const k = stats.at ? 1000 / (now - stats.at) : 0;
    post({ t: 'stats', sim: stats.sim * k, pack: stats.pack * k });
    stats.sim = 0; stats.pack = 0; stats.at = now;
  }
  timer = setTimeout(loop, Math.max(1, FRAME_MS - (performance.now() - now)));
}

function start() { if (running) return; running = true; last = 0; if (!timer) timer = setTimeout(loop, 0); }
function stop() { running = false; if (timer) { clearTimeout(timer); timer = 0; } }

function run(d) {
  if (d.o === 'sim' && d.m === 'setVisible') {
    // the page was hidden: the sim waits with the frames; back: it skips ahead by the time away
    const [visible, away] = d.a;
    K.sim.setVisible(visible, away);
    if (visible) start(); else stop();
    return undefined;
  }
  return K[d.o][d.m](...d.a);
}

async function boot(d) {
  if (LOADED !== d.loaded || classPrint(ctors, LOADED) !== d.print) throw new Error(`class tables differ (${classPrint(ctors, LOADED)} vs ${d.print})`);
  K.sim.useRemoteStore(d.store, (key, value) => post({ t: 'store', key, value }));
  K.sim.useRemoteFx(fx);
  K.sim.setReducedMotion(!!d.reducedMotion);
  enc = new Encoder({ ctors, canon: wire.canon(), sticky: wire.stickyClasses(), forbidden: wire.forbiddenClasses() });
  await K.sim.boot(d.base);
  start();
}

let booted = null;
self.onmessage = async (e) => {
  const d = e.data;
  if (d.t === 'boot') {
    booted = boot(d);
    try { await booted; post({ t: 'ready' }); } catch (err) { console.error(err); post({ t: 'failed', why: String(err?.message || err) }); }
    return;
  }
  // commands wait for the sim to be built (they keep their order: each waits on the same promise)
  if (booted) try { await booted; } catch { return; }
  switch (d.t) {
    case 'send':
      try { run(d); } catch (err) { console.error(err); }
      break;
    case 'call':
      try { post({ t: 'reply', id: d.id, v: await run(d) }); } catch (err) { console.error(err); post({ t: 'reply', id: d.id, err: String(err?.message || err) }); }
      break;
    default:
      return;
  }
  // a command can change the tank on screen without a step (a mode switch, My tank set up): show it at once
  if (enc) try { sendSnapshot(); } catch (err) { console.error(err); }
};

// the module (and the Kotlin in it) loaded: the page knows this browser can run the sim here
post({ t: 'hello' });
