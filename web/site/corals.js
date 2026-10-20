// ReefLive web: where should the new coral live? (mirrors the Android CoralPick.kt). In My tank, once a coral's bag is
// emptied the coral waits (core CoralPicks): its best spots glow on the rock (the renderer draws the rings) and each
// gets a numbered badge with stars and the reason ("Bright light, gentle flow"). Tap a badge to put it there, "Pick for
// me" for the best spot, or "Later" (a pill brings the spots back). Self-contained: its own DOM and CSS.

let live = null, SB = null, say = () => {}, cue = () => {}, isMine = () => false;
let root, panel, title, hint, laterPill;
let hidden = false;
const badges = new Map(); // rank -> element

const CSS = `
#coralLayer { position: absolute; inset: 0; pointer-events: none; z-index: 6; }
#coralLayer .spot { position: absolute; transform: translate(-50%, -100%); display: flex; flex-direction: column; align-items: center;
  max-width: 180px; padding: 5px 10px; border-radius: 16px; border: 2.5px solid #1B1340; box-shadow: 0 3px 0 #1B1340;
  background: #5FD7FF; color: #1B1340; font: 700 14px/1.15 -apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif;
  text-align: center; cursor: pointer; pointer-events: auto; user-select: none; -webkit-user-select: none; }
#coralLayer .spot.best { background: #FFE14D; }
#coralLayer .spot small { display: block; font-size: 11px; font-weight: 600; }
#coralLayer .spot:active { transform: translate(-50%, -100%) scale(.95); }
#coralPanel { position: absolute; left: 50%; bottom: calc(var(--safe-bottom) + 84px); transform: translateX(-50%); z-index: 6;
  display: flex; flex-direction: column; align-items: center; gap: 8px; width: min(420px, 92vw); pointer-events: none; }
#coralPanel .glass { padding: 8px 14px; text-align: center; }
#coralPanel .glass b { display: block; }
#coralPanel .glass span { font-size: 13px; }
#coralPanel .row { display: flex; gap: 8px; }
`;

/** Wires the module to the page: [opts] = { live, SB, say, cue, isMine }. */
export function initCorals(opts) {
  ({ live, SB, say, cue } = opts);
  isMine = opts.isMine || (() => true);
  const style = document.createElement('style'); style.textContent = CSS; document.head.appendChild(style);
  const host = document.getElementById('live');
  root = document.createElement('div'); root.id = 'coralLayer'; host.appendChild(root);
  panel = document.createElement('div'); panel.id = 'coralPanel'; panel.className = 'hidden';
  panel.innerHTML = `<p class="glass"><b id="coralTitle"></b><span id="coralHint"></span></p>
    <div class="row"><button class="bagBtn" id="coralAuto">✨ Pick for me</button><button class="bagBtn plain" id="coralLater">Later</button></div>
    <button class="bagBtn" id="coralBack">🪸 Place coral</button>`;
  host.appendChild(panel);
  title = panel.querySelector('#coralTitle'); hint = panel.querySelector('#coralHint'); laterPill = panel.querySelector('#coralBack');
  panel.querySelector('#coralAuto').addEventListener('click', () => place(-1));
  panel.querySelector('#coralLater').addEventListener('click', () => { hidden = true; cue('TICK'); tickCorals(); });
  laterPill.addEventListener('click', () => { hidden = false; cue('TICK'); tickCorals(); });
  setInterval(tickCorals, 100);
}

function place(rank) {
  const p = JSON.parse(live.coralPick() || 'null');
  if (!p || !SB.act('placeCoral', null, rank)) return;
  hidden = false;
  cue('FEED');
  say(`Nice spot! Your ${p.name} will grow right there.`);
  setTimeout(tickCorals, 150);
}

/** Moves the badges with the camera and shows or hides the panel (call several times a second). */
export function tickCorals() {
  if (!root) return;
  const p = isMine() ? JSON.parse(live.coralPick() || 'null') : null;
  const show = p != null && !hidden;
  const canvas = document.getElementById('tank');
  const k = canvas.getBoundingClientRect().width / Math.max(1, canvas.width);
  const seen = new Set();
  if (show) for (const s of p.spots) {
    if (s.x < 0) continue;
    seen.add(s.rank);
    let el = badges.get(s.rank);
    if (!el) {
      el = document.createElement('button'); el.className = 'spot'; el.type = 'button';
      el.addEventListener('click', () => place(Number(el.dataset.rank)));
      root.appendChild(el); badges.set(s.rank, el);
    }
    el.dataset.rank = s.rank;
    el.classList.toggle('best', s.rank === 0);
    const html = `${s.rank + 1} ${'★'.repeat(s.stars)}${'☆'.repeat(3 - s.stars)}<small>${s.reason}</small>`;
    if (el.innerHTML !== html) { el.innerHTML = html; el.setAttribute('aria-label', `Spot ${s.rank + 1}, ${s.stars} stars: ${s.reason}. Tap to put the coral here.`); }
    el.style.left = `${s.x * k}px`; el.style.top = `${s.y * k}px`;
  }
  for (const [rank, el] of badges) if (!seen.has(rank)) { el.remove(); badges.delete(rank); }
  panel.classList.toggle('hidden', p == null);
  if (!p) { hidden = false; return; }
  const more = p.waiting > 1 ? ` (${p.waiting} to place)` : '';
  for (const el of panel.querySelectorAll('.glass, .row')) el.classList.toggle('hidden', hidden);
  laterPill.classList.toggle('hidden', !hidden);
  laterPill.textContent = `🪸 Place ${p.name}${p.waiting > 1 ? ` (${p.waiting})` : ''}`;
  title.textContent = `Where should your ${p.name} live?${more}`;
  hint.textContent = p.spots.length ? 'Tap a glowing spot. Gold is the best one.' : 'No free spot right now. Pick for me finds some room.';
}
