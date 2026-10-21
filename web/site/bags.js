// ReefLive web: new livestock arrives in a shop bag (mirrors the Android BagCarry.kt). Picking an animal in the Shop puts
// the bag in your hand; drag it onto the tank (or tap the tank) and it drops onto the water with a splash. It floats
// for 30 sim minutes while the bag water evens out to the tank, a timer rides over it, and "Hurry up" fast-forwards
// the clock. Then it tips over and the animals come out (core AcclimationBags). Self-contained: its own DOM and CSS.

let live = null, SB = null, say = () => {}, cue = () => {}, onCancel = () => {};
let held = null;           // { id, name, group, count, size }
let lastHurry = false;
const timers = new Map();  // bag id -> element
let root, ghost, help, panel, panelText, hurryBtn;

const ICON = { FISH: '🐠', INVERTS: '🦀', CORALS: '🪸' };

const CSS = `
#bagLayer { position: absolute; inset: 0; pointer-events: none; z-index: 6; }
#bagLayer .bagTimer { position: absolute; transform: translate(-50%, 6px); display: flex; align-items: center; gap: 8px;
  padding: 5px 12px 5px 6px; border-radius: 999px; background: #F2F6FF; color: #1B1340; border: 3px solid #1B1340;
  font: 600 13px/1.15 -apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif; white-space: nowrap;
  box-shadow: 0 3px 0 #1B1340; }
#bagLayer .bagTimer small { display: block; font-size: 11px; opacity: .75; }
#bagLayer .ring { width: 30px; height: 30px; border-radius: 50%; flex: none;
  background: conic-gradient(#5FD7FF var(--p), rgba(95,215,255,.22) 0); -webkit-mask: radial-gradient(circle, transparent 9px, #000 10px);
  mask: radial-gradient(circle, transparent 9px, #000 10px); }
#bagGhost { position: absolute; left: 0; top: 0; width: 96px; height: 120px; transform: translate(-50%, -50%);
  pointer-events: none; z-index: 8; transition: transform .08s; }
#bagGhost .bag { position: absolute; inset: 0; }
#bagGhost .who { position: absolute; left: 50%; bottom: 16px; transform: translateX(-50%); font-size: 40px; }
#bagGhost .n { position: absolute; right: -6px; top: 0; padding: 2px 8px; border-radius: 999px; background: #FFB13D;
  color: #1B1340; border: 2px solid #1B1340; font: 700 14px/1.2 sans-serif; }
#bagGhost.idle { animation: bagSway 1.1s ease-in-out infinite alternate; }
@keyframes bagSway { from { rotate: -5deg; } to { rotate: 5deg; } }
#bagCatch { position: absolute; inset: 0; z-index: 7; touch-action: none; cursor: grabbing; }
#bagHelp { position: absolute; left: 50%; bottom: calc(var(--safe-bottom) + 20px); transform: translateX(-50%); z-index: 9;
  display: flex; flex-direction: column; align-items: center; gap: 8px; width: min(420px, 92vw); text-align: center;
  pointer-events: none; user-select: none; -webkit-user-select: none; }
#bagHelp p { margin: 0; padding: 8px 14px; }
#bagHelp p.miss { background: rgba(255,225,77,.25); }
.bagBtn { border: 3px solid #1B1340; border-radius: 999px; padding: 8px 18px; font-weight: 700; color: #1B1340;
  background: #FFE14D; box-shadow: 0 3px 0 #1B1340; cursor: pointer; pointer-events: auto; min-height: 44px; }
.bagBtn.plain { background: #F2F6FF; }
#bagPanel { position: absolute; left: 50%; bottom: calc(var(--safe-bottom) + 84px); transform: translateX(-50%); z-index: 6;
  display: flex; align-items: center; gap: 8px; pointer-events: none; }
#bagPanel .glass { padding: 8px 14px; white-space: nowrap; }
`;

const BAG_SVG = `<svg class="bag" viewBox="0 0 96 120" aria-hidden="true">
  <path d="M42 14 C29 36 6 50 6 82 C6 110 29 118 48 118 C67 118 90 110 90 82 C90 50 67 36 54 14 Z" fill="rgba(255,255,255,.22)"/>
  <path d="M10 66 L86 66 C92 84 88 114 48 115 C8 114 4 84 10 66 Z" fill="rgba(95,215,255,.45)"/>
  <path d="M42 14 C29 36 6 50 6 82 C6 110 29 118 48 118 C67 118 90 110 90 82 C90 50 67 36 54 14 Z" fill="none" stroke="#1B1340" stroke-width="3"/>
  <ellipse cx="48" cy="8" rx="9" ry="6" fill="#1B1340"/><rect x="39" y="13" width="18" height="6" rx="3" fill="#FFE14D"/>
  <path d="M19 60 L15 86" stroke="rgba(255,255,255,.7)" stroke-width="4" stroke-linecap="round"/></svg>`;

/** Wires the module to the page: [opts] = { live, SB, say, cue, onCancel }. */
export function initBags(opts) {
  ({ live, SB, say, cue } = opts);
  onCancel = opts.onCancel || (() => {});
  const style = document.createElement('style'); style.textContent = CSS; document.head.appendChild(style);
  const host = document.getElementById('live');
  root = document.createElement('div'); root.id = 'bagLayer'; host.appendChild(root);
  panel = document.createElement('div'); panel.id = 'bagPanel'; panel.className = 'hidden';
  panel.innerHTML = '<span class="glass" id="bagPanelText"></span><button class="bagBtn" id="bagHurry">⏩ Hurry up</button>';
  host.appendChild(panel);
  panelText = panel.querySelector('#bagPanelText'); hurryBtn = panel.querySelector('#bagHurry');
  setInterval(tickBags, 250);
  hurryBtn.addEventListener('click', () => {
    const on = !lastHurry;
    if (SB.act('hurryBags', null, on ? 1 : 0)) { lastHurry = on; cue('TICK'); say(on ? 'Zoom! Time flies until the bags open.' : 'Back to your speed.'); tickBags(); }
  });
}

/** Puts a bag of [count] × [stock] (catalog entry { id, name, group }) at [size] in the hand. */
export function pickBag(stock, count, size) {
  held = { id: stock.id, name: stock.name, group: stock.group, count, size };
  const host = document.getElementById('live');
  const canvas = document.getElementById('tank');
  ghost = document.createElement('div'); ghost.id = 'bagGhost'; ghost.className = 'idle';
  ghost.innerHTML = `${BAG_SVG}<span class="who">${ICON[stock.group] || '🐠'}</span>${count > 1 ? `<span class="n">×${count}</span>` : ''}`;
  const rest = () => { const r = host.getBoundingClientRect(); place(r.width / 2, r.height - 230); };
  const catcher = document.createElement('div'); catcher.id = 'bagCatch';
  help = document.createElement('div'); help.id = 'bagHelp';
  help.innerHTML = `<p class="glass">Drag the bag onto the tank and let go. It floats while the water in it warms up.</p>
    <button class="bagBtn plain">Cancel</button>`;
  host.append(catcher, ghost, help);
  rest();
  const lift = 60;
  let down = false;
  catcher.addEventListener('pointerdown', (e) => {
    down = true; ghost.classList.remove('idle'); catcher.setPointerCapture?.(e.pointerId); move(e);
  });
  const local = (e) => { const r = host.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
  const move = (e) => { if (!down) return; const [x, y] = local(e); place(x, y - lift); };
  catcher.addEventListener('pointermove', move);
  catcher.addEventListener('pointerup', (e) => {
    if (!down) return;
    down = false;
    const [x, y] = local(e);
    // the canvas fills the live view: CSS px → canvas px
    const r = canvas.getBoundingClientRect(), hr = host.getBoundingClientRect();
    const k = canvas.width / r.width;
    const gx = live.bagDropX((x + hr.left - r.left) * k, (y - lift + hr.top - r.top) * k);
    if (gx < 0) {
      help.querySelector('p').textContent = 'Missed the tank! Let go over the water.';
      help.querySelector('p').classList.add('miss');
      ghost.classList.add('idle'); rest();
      return;
    }
    const h = held;
    drop();
    if (SB.act('bag', `${h.id}:${h.size}:${gx.toFixed(1)}`, h.count)) {
      cue('FEED');
      say(`Plop! ${h.count === 1 ? `The ${h.name}` : `${h.count} ${h.name}`} float in the bag so the water inside warms up. Out in 30 minutes, or tap Hurry up.`);
      setTimeout(tickBags, 200);
    }
  });
  help.querySelector('button').addEventListener('click', () => { drop(); onCancel(); });
}

/** True while a bag is in the hand. */
export const holdingBag = () => held != null;

function place(x, y) { if (ghost) ghost.style.transform = `translate(${x}px, ${y}px) translate(-50%, -50%)`; }

function drop() {
  held = null;
  for (const id of ['bagGhost', 'bagCatch', 'bagHelp']) document.getElementById(id)?.remove();
  ghost = null; help = null;
}

/** Refreshes the timers over the floating bags and the Hurry up panel (call a few times a second). */
export function tickBags() {
  if (!root) return;
  const s = JSON.parse(live.bagState() || 'null');
  const bags = s?.bags || [];
  lastHurry = !!s?.hurry;
  const canvas = document.getElementById('tank');
  const k = canvas.getBoundingClientRect().width / Math.max(1, canvas.width);
  const seen = new Set();
  for (const b of bags) {
    if (b.phase >= 2 || b.x < 0) continue;
    seen.add(b.id);
    let el = timers.get(b.id);
    if (!el) { el = document.createElement('div'); el.className = 'bagTimer'; root.appendChild(el); timers.set(b.id, el); }
    const t1 = b.phase === 0 ? 'Splash!' : b.min >= 1 ? `${Math.round(b.min)} min` : 'Almost!';
    el.innerHTML = `<i class="ring" style="--p:${Math.round(b.progress * 100)}%"></i><span>${t1}<small>${b.bagT.toFixed(1)}° → ${b.tankT.toFixed(1)}°</small></span>`;
    el.style.left = `${b.x * k}px`; el.style.top = `${b.y * k}px`;
  }
  for (const [id, el] of timers) if (!seen.has(id)) { el.remove(); timers.delete(id); }
  const floating = bags.filter((b) => b.phase <= 1);
  panel.classList.toggle('hidden', floating.length === 0 || held != null);
  if (floating.length) {
    const mins = Math.round(Math.max(...floating.map((b) => b.min)));
    panelText.textContent = `${floating.length === 1 ? 'Bag floating' : `${floating.length} bags floating`}${mins >= 1 ? ` · out in ${mins} min` : ' · opening'}`;
    hurryBtn.textContent = lastHurry ? 'Normal speed' : '⏩ Hurry up';
    hurryBtn.classList.toggle('plain', lastHurry);
    hurryBtn.style.pointerEvents = 'auto';
  }
}
