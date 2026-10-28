// ReefLive web: the page's one door to the simulation (SimHost). Every page → sim call goes through here, so the sim
// can run somewhere else than the page (a Web Worker) without the page knowing.
//
// The calls fall into three kinds:
//   view  the 3D view, the camera and the static tables: answered on the page thread, synchronously, always.
//   send  a command for the sim: fire and forget, applied in the order sent (and before any later call).
//   call  needs the sim's answer: returns a Promise. Code that acts on the answer awaits it, so the answer is fresh.
// Every call also has a synchronous `peek` (live.peek.state()): the latest answer this page has seen for the same
// arguments (null before the first), and it asks for a fresh one. For drawing only, never for deciding what to do;
// await the call first when the drawing must be up to date (a card that opens).
//
// Inventory (ReefLive = live, ReefSandbox = sb, ReefMyTank = my):
//   live view: setVisible follow select followZoom followOrbit setViewLight shelfOpen aimArm gearTaps shelfRow shelfPick
//              shelfLocked shelfHidden resetCamera nursery showRoom musicOpen musicState roomVisible screensaver subView
//              setQuality cam cinema bagDropX scapeShapes parameters readingWarning cycleStatus acclimationPlan
//              scapeTouch touchTank (the touch is the view's; the rock / ripple goes to the sim as a command)
//              bagState coralPick netFlush subState (read from the sim's latest snapshot, the one the view draws)
//   live send: setSpeed setDynamicRocks restoreRocks applyReadings scapeUndo scapeClear submarine steerSub subWatch
//              subWatchNext
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
import { ReefLive, ReefSandbox, ReefMyTank } from './kotlin/ReefLive-web.mjs';

const K = { live: ReefLive.getInstance(), sb: ReefSandbox.getInstance(), my: ReefMyTank.getInstance() };

const words = (s) => s.trim().split(/\s+/);
export const API = {
  live: {
    view: words(`setVisible follow select followZoom followOrbit setViewLight shelfOpen aimArm gearTaps shelfRow shelfPick
      shelfLocked shelfHidden resetCamera nursery showRoom musicOpen musicState roomVisible screensaver subView setQuality cam
      cinema bagDropX scapeShapes parameters readingWarning cycleStatus acclimationPlan scapeTouch touchTank bagState
      coralPick netFlush subState`),
    send: words('setSpeed setDynamicRocks restoreRocks applyReadings scapeUndo scapeClear submarine steerSub subWatch subWatchNext'),
    call: words('start state'),
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
 * the next chunk of a catch-up): a peek never asks for these, it only shows the last answer (without the gains).
 */
const PEEK_ONLY = new Set(['my.economy', 'my.away', 'my.visitReady', 'my.buildStep', 'my.start', 'my.useSupply', 'my.useFishFood',
  'my.buyRecord', 'my.collectHappy', 'my.decorBuy', 'my.turnStep', 'my.checkout', 'my.sell', 'my.showTank', 'my.visit',
  'sb.setMode', 'sb.act', 'sb.applyParam', 'sb.fix', 'sb.feedAt', 'sb.spotAct', 'sb.timelapse', 'sb.sound', 'live.start']);

/** Runs the sim on the page thread: the Kotlin objects are called directly. */
const inThread = {
  view: (o, m, a) => K[o][m](...a),
  send: (o, m, a) => { K[o][m](...a); },
  call: (o, m, a) => {
    try { return Promise.resolve(K[o][m](...a)); } catch (e) { return Promise.reject(e); }
  },
  peek: (o, m, a) => K[o][m](...a),
};

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

/**
 * Console / QA handles (window.reeflive, window.reefsandbox, window.reefmytank), named like the Kotlin objects: view
 * and send calls go straight through; a call answers synchronously with its peek (scripts poll), its Promise is
 * `name.call(...)` style via the module (import { live } from './simhost.js').
 */
function qa(o, f) {
  return new Proxy({}, {
    get(_, m) {
      if (typeof m !== 'string') return undefined;
      if (f.peek[m]) return (...a) => (transport === inThread ? K[o][m](...a) : f.peek[m](...a));
      return f[m];
    },
  });
}
window.reeflive = qa('live', live);
window.reefsandbox = qa('sb', sb);
window.reefmytank = qa('my', my);
