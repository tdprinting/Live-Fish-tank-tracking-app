// ReefLive web: the live tank screen, mirroring the Android live screen (LiveTankScreen + LiveMenu + SupplyShelf +
// MicroscopeView + TimelapseSheet). The sim, renderer and the shared tables come from Kotlin (kotlin/ReefLive-web.mjs):
// ReefLive drives the 3D view, ReefSandbox is the Android view model's actions and readings.
import { ReefLive, ReefSandbox } from './kotlin/ReefLive-web.mjs';
import { initBags, pickBag } from './bags.js';

const live = ReefLive.getInstance();
const SB = ReefSandbox.getInstance();
window.reeflive = live; window.reefsandbox = SB; // handy from the Safari console
const $ = (id) => document.getElementById(id);
const store = {
  get(key, fallback) { try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* private mode */ } },
};
const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

let C = null;              // catalog (static tables)
let P = null;              // panel (current values, readouts, happiness)
let animals = [];          // living animals (cards)
const ui = {
  mode: 'watch', watchFast: false, speed: 1, card: null, more: false, tab: 'WATER', controlMode: 'SET',
  picked: null, following: null, tour: false, shelf: false, shelfRow: 0, cleaning: false, chromeHidden: false,
  torch: false, viewLight: 'REAL', quality: store.get('reeflive.quality', 'HIGH'),
  sound: store.get('reeflive.sound', false), volume: store.get('reeflive.volume', 0.6),
  water: store.get('reeflive.water', false), hermie: store.get('reeflive.hermie', true), happy: store.get('reeflive.happy', true),
  addCount: 1, addSize: 'JUVENILE', addGroup: 'FISH',
};
let onReadingsNeeded = () => {};

// ================================================================ start
let started = false;
export function startLive(applyReadings) {
  onReadingsNeeded = applyReadings;
  if (started) return;
  started = true;
  live.start($('tank'), new URL('.', location.href).href, ui.quality, {
    onStatus: (t) => { if (t) $('loadText').textContent = t; },
    onError: (t) => { $('loadText').textContent = `Sorry, the 3D tank could not start: ${t}`; $('loading').querySelector('.spinner')?.remove(); },
    onPick: (id) => { if (id) select(id); else if (!ui.following) select(null); },
    onFollow: (id) => { ui.following = id; if (id) ui.picked = id; renderFish(); renderFollow(); },
    onFed: () => { cue('PELLET'); say('Yum! Food drops right where you pressed.'); },
    onKnock: () => say('Knock knock! Who comes to look?'),
    onTouch: (surface) => touchTank(surface),
    // a fingertip held on the glass (UI_STYLE §10.8): slide it and fish follow; flick it as it lifts for a puff of current
    onFinger: (surface) => { if (surface) { try { navigator.vibrate?.(6); } catch { /* not on iOS */ } fingerSay(); } },
    onFlick: () => say('Whoosh! A puff of current.'),
    onScrape: (cm) => scrapeSound(cm),
    onFlipperTap: () => setCleaning(true),
    onShelfPick: (id) => useSupply(id),
  }).then(() => {
    C = JSON.parse(SB.catalog());
    initBags({ live, SB, say, cue, onCancel: () => { ui.card = 'LIFE'; renderAll(); } });
    $('loading').classList.add('done');
    applyReadings();
    renderAll();
    setInterval(tick, 500);
    tick();
    say(C.tips.hello);
    const firstVisit = !store.get('reeflive.tipsSeen', false);
    if (firstVisit) { store.set('reeflive.tipsSeen', true); setTimeout(showTips, 1200); }
    checkNews(firstVisit);
  }).catch(() => {});
}
export function setLiveVisible(v) { live.setVisible(v); if (!v) stopLoops(); }

// keep the side rail and fish card below however many rows the top HUD wraps to
try { const top = document.querySelector('#live .top'), chrome = $('chrome');
  if (top && window.ResizeObserver) new ResizeObserver(() => chrome.style.setProperty('--topH', `${top.offsetHeight + 8}px`)).observe(top); } catch (e) { /* keep CSS fallback */ }

// ================================================================ periodic refresh
function tick() {
  P = JSON.parse(SB.panel());
  if (!P) return;
  animals = JSON.parse(SB.animals());
  const s = JSON.parse(live.state());
  if (s) $('clock').textContent = `${clockText(s.time)} · ${s.phase}${P.speed !== 1 ? ` · ${P.speed === 0 ? 'paused' : `${P.speed}×`}` : ''}`;
  const worst = P.readouts.some((r) => r.status === 'DANGER') ? 'danger' : P.readouts.some((r) => r.status === 'CAUTION') ? 'caution' : 'good';
  $('waterBtn').className = `pill glass water-${worst}${ui.water ? ' on' : ''}`;
  $('waterSum').textContent = worst === 'good' ? 'Water OK' : worst === 'caution' ? 'Water: check' : 'Water: help!';
  $('strip').classList.toggle('hidden', !ui.water);
  $('strip').innerHTML = P.readouts.map((r) => `<span class="chip ${r.status.toLowerCase()}">${esc(r.label)} ${esc(r.text)}</span>`).join('');
  $('happy').classList.toggle('hidden', !ui.happy);
  $('happyVal').textContent = `${P.happy}%`;
  $('happyBar').style.width = `${P.happy}%`;
  $('happyBar').className = P.happy >= 70 ? 'good' : P.happy >= 40 ? 'caution' : 'danger';
  $('happyFace').textContent = P.happy >= 80 ? '😄' : P.happy >= 60 ? '🙂' : P.happy >= 40 ? '😐' : P.happy >= 20 ? '😟' : '😫';
  renderFish();
  renderFollow();
  if (ui.card && document.activeElement?.type !== 'range' && !ui.dragging) refreshCardValues();
  updateSound();
  if (ui.tour && ++ui.tourTicks >= 24) nextTour();
  if (!$('micro').classList.contains('hidden')) drawMicro();
  if (sub.on) renderSubInfo();
}
const clockText = (sec) => {
  const t = Math.max(0, Math.floor(sec));
  const d = Math.floor(t / 86400) + 1, h = Math.floor((t % 86400) / 3600), m = Math.floor((t % 3600) / 60);
  return `Day ${d} · ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
};

// ================================================================ Hermie, toasts
let hermieTimer = 0, tipIndex = 0;
function say(text) {
  if (!ui.hermie) { toast(text); return; }
  $('hermieText').textContent = text;
  $('hermie').classList.add('talk');
  clearTimeout(hermieTimer);
  hermieTimer = setTimeout(() => $('hermie').classList.remove('talk'), 6000);
}
$('waterBtn').addEventListener('click', () => { ui.water = !ui.water; store.set('reeflive.water', ui.water); cue('TICK'); tick(); });
$('hermie').addEventListener('click', () => {
  const list = ui.mode === 'sandbox' ? C.tips.hermieSandbox : C.tips.hermieWatch;
  say(list[tipIndex++ % list.length]);
  cue('TICK');
});
let toastTimer = 0;
function toast(text) {
  $('toast').textContent = text;
  $('toast').classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $('toast').classList.add('hidden'), 3500);
}
function locked() { say('That only works in the pretend tank. Tap Sandbox at the top to try it.'); cue('TICK'); }

/** Runs a ReefSandbox action; in Watch mode sandbox-only actions explain themselves instead. */
function act(name, arg = null, num = 0, line = null) {
  const ok = SB.act(name, arg, num);
  if (!ok) { locked(); return false; }
  if (line) say(line);
  cue('TICK');
  setTimeout(tick, 150);
  return true;
}

// ---- What's new: updates merged since this browser last opened the app (whats-new.json, built from main's history)
let news = [];
async function checkNews(firstVisit) {
  try { news = await (await fetch('whats-new.json', { cache: 'no-store' })).json(); } catch { return; }
  if (!news.length) return;
  const last = store.get('reeflive.newsSeen', 0);
  store.set('reeflive.newsSeen', news[0].n);
  if (firstVisit) return; // brand new: the tips come first, everything is new anyway
  const fresh = last ? news.filter((e) => e.n > last) : news.slice(0, 8);
  if (fresh.length) setTimeout(() => showNews(fresh, true), 1500);
}
function showNews(list = news.slice(0, 15), since = false) {
  if (!list.length) { say('Nothing new yet.'); return; }
  const shown = list.slice(0, 15);
  $('newsTitle').textContent = since ? `New since your last visit (${list.length})` : "What's new";
  $('newsList').innerHTML = shown.map((e) => `<li>${esc(e.text)}<small>${esc(niceDate(e.date))}</small></li>`).join('')
    + (list.length > shown.length ? `<li class="muted">…and ${list.length - shown.length} more</li>` : '');
  const d = $('news');
  if (!d.open) d.showModal();
}
function niceDate(iso) {
  const t = new Date(`${iso}T12:00:00`);
  return isNaN(t) ? iso : t.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function showTips() {
  const sb = ui.mode === 'sandbox';
  $('tipsTitle').textContent = sb ? C.tips.sandboxTitle : C.tips.watchTitle;
  $('tipsList').innerHTML = (sb ? C.tips.sandbox : C.tips.watch).map((t) => `<li>${esc(t)}</li>`).join('');
  $('tips').showModal();
}

// ================================================================ mode
document.querySelectorAll('[data-mode]').forEach((b) => b.addEventListener('click', () => setMode(b.dataset.mode)));
function setMode(m) {
  if (m === ui.mode && !ui.watchFast) return;
  ui.watchFast = false;
  ui.mode = m;
  SB.setMode(m);
  ui.speed = 1; live.setSpeed(1);
  live.shelfLocked(m === 'sandbox' ? '' : SUPPLIES.filter((s) => s.sandbox).map((s) => s.id).join(','));
  document.querySelectorAll('[data-mode]').forEach((b) => b.classList.toggle('on', b.dataset.mode === m));
  closeCard();
  say(m === 'sandbox' ? 'This is a pretend tank. Nothing here changes your real one.' : 'Back to your real tank.');
  renderRail();
  tick();
}

/** Watch mode runs the real tank at 1x; faster time plays on a pretend copy, like the Android app. */
function setSpeed(v) {
  if (ui.mode === 'watch') {
    if (v !== 1 && !ui.watchFast) { SB.setMode('sandbox'); ui.watchFast = true; say('Fast time plays on a copy. Your real tank keeps real time.'); }
    if (v === 1 && ui.watchFast) { SB.setMode('watch'); ui.watchFast = false; }
  }
  ui.speed = v;
  live.setSpeed(v);
  tick();
}

// ================================================================ menu rail and cards
const CATS = {
  VIEW: { ico: '👀', label: 'Watch' },
  SUPPLIES: { ico: '🧺', label: 'Supplies' },
  LIFE: { ico: '🐠', label: 'Add life' },
  TANK: { ico: '💡', label: 'Tank' },
  TROUBLE: { ico: '⚡', label: 'Trouble' },
  MORE: { ico: '⋯', label: 'More' },
};
function renderRail() {
  const cats = ui.mode === 'sandbox' ? ['SUPPLIES', 'LIFE', 'TANK', 'TROUBLE', 'MORE'] : ['VIEW', 'SUPPLIES', 'MORE'];
  $('rail').innerHTML = cats.map((c) => `<button class="tool ${ui.card === c || (c === 'SUPPLIES' && ui.shelf) ? 'active' : ''}" data-cat="${c}"><span class="ico">${CATS[c].ico}</span><span>${CATS[c].label}</span></button>`).join('');
}
$('rail').addEventListener('click', (e) => {
  const c = e.target.closest('[data-cat]')?.dataset.cat;
  if (!c) return;
  cue('TICK');
  if (c === 'SUPPLIES') { ui.shelf ? closeShelf() : openShelf(); return; }
  if (ui.shelf) closeShelf();
  if (ui.card === c) { closeCard(); return; }
  ui.card = c; ui.more = false;
  renderCard();
  renderRail();
});
function closeCard() { ui.card = null; $('card').classList.add('hidden'); renderRail(); }
$('tank').addEventListener('pointerdown', () => { if (ui.card) { closeCard(); ui.cardClosedAt = performance.now(); } });

const tile = (attrs, ico, label, on = false, lock = false) =>
  `<button class="opt ${on ? 'on' : ''} ${lock ? 'lock' : ''}" ${attrs}><span class="ico">${ico}</span>${esc(label)}</button>`;
const val = (id) => P?.values?.[id]?.v;
const isOn = (id) => (val(id) ?? 0) >= 0.5;

function renderCard() {
  if (!ui.card) return;
  const sb = ui.mode === 'sandbox';
  let h = '';
  switch (ui.card) {
    case 'VIEW': h = viewCard(); break;
    case 'LIFE': h = lifeCard(sb); break;
    case 'TANK': h = tankCard(sb); break;
    case 'TROUBLE': h = troubleCard(sb); break;
    case 'MORE': h = moreCard(sb); break;
  }
  $('card').innerHTML = `<div class="card-head"><strong>${CATS[ui.card].ico} ${CATS[ui.card].label}</strong>
    <span>${ui.card !== 'VIEW' && ui.card !== 'MORE' ? `<button class="btn small ghost" data-more>${ui.more ? 'Less' : 'More'}</button>` : ''}
    <button class="icon-btn" data-close aria-label="Close">✕</button></span></div><div class="card-body">${h}</div>`;
  $('card').classList.remove('hidden');
}

// ---- Watch (VIEW)
function timeSlider(stops) {
  const i = Math.max(0, stops.indexOf(ui.speed));
  return `<label class="slider"><span>Time</span><output>${ui.speed === 0 ? 'Paused' : ui.speed === 1 ? 'Real time' : `${ui.speed}× faster`}</output>
    <input type="range" data-time min="0" max="${stops.length - 1}" step="1" value="${i}" data-stops="${stops.join(',')}"></label>`;
}
const STOPS = [1, 10, 60, 240, 600, 1000];
function viewCard() {
  const looks = [['REAL', '🕒', 'Real clock'], ['DAY', '☀️', 'Day'], ['DUSK', '🌇', 'Dusk'], ['NIGHT', '🌙', 'Night'], ['MOON', '🌕', 'Moonlight']];
  const fish = animals.slice(0, 12);
  return `${timeSlider(STOPS)}
    <h4>Lamp looks</h4><div class="options">${looks.map(([k, i, n]) => tile(`data-look="${k}"`, i, n, ui.viewLight === k)).join('')}</div>
    <div class="options">${tile('data-torch', '🔦', 'Torch', ui.torch)}${tile('data-feedshelf', '🍤', 'Feed')}${tile('data-tour', '🎬', 'Auto tour', ui.tour)}${tile('data-sub', '🟡', 'Submarine', sub.on)}
      ${tile('data-hide', '🎞', 'Cinematic')}${tile('data-saver', '🌙', 'Screensaver')}${tile('data-sound', ui.sound ? '🔊' : '🔇', ui.sound ? 'Sound on' : 'Sound off', ui.sound)}</div>
    <h4>Follow a fish</h4><div class="options">${fish.map((a) => tile(`data-follow="${a.id}"`, '🐟', a.name, ui.following === a.id)).join('') || '<p class="muted">No fish yet.</p>'}</div>`;
}

// ---- Add life
function lifeCard(sb) {
  const groups = [['FISH', '🐠', 'Fish'], ['INVERTS', '🦐', 'Inverts'], ['CORALS', '🪸', 'Corals']];
  const list = C.stock.filter((s) => s.group === ui.addGroup);
  let h = `<div class="pills">${groups.map(([g, i, n]) => `<button class="pillbtn ${ui.addGroup === g ? 'on' : ''}" data-group="${g}">${i} ${n}</button>`).join('')}</div>
    <p class="muted">Pick how many, then tap an animal. It comes in a bag: drag the bag onto the tank and it floats until they are ready.</p>
    <div class="pills"><span class="muted">How many</span>${[1, 2, 3, 5, 10].map((n) => `<button class="pillbtn ${ui.addCount === n ? 'on' : ''}" data-count="${n}">${n}</button>`).join('')}</div>
    ${ui.addGroup !== 'CORALS' ? `<div class="pills"><span class="muted">Size</span>${C.sizes.map((z) => `<button class="pillbtn ${ui.addSize === z.id ? 'on' : ''}" data-size="${z.id}">${esc(z.label)}</button>`).join('')}</div>` : ''}
    <div class="options">${list.map((s) => tile(`data-add="${s.id}"`, STOCK_ICON[s.group], s.name, false, !sb)).join('')}</div>
    <h4>Helpers</h4><div class="options">
      ${tile('data-micro="DOSE_TISBE"', '🦠', 'Pods', false, !sb)}${tile('data-micro="DOSE_PHYTO"', '🟢', 'Phyto', false, !sb)}
      ${tile('data-pest="PEPPERMINT_SHRIMP"', '🦐', 'Peppermint shrimp', false, !sb)}${tile('data-pest="BERGHIA"', '🐌', 'Berghia slugs', false, !sb)}
      ${tile('data-shells', '🐚', 'Spare shells', false, !sb)}</div>`;
  if (ui.more) {
    h += `<h4>More tiny life</h4><div class="options">${C.micro.filter((m) => !m.id.startsWith('TREAT') && m.id !== 'FLATWORM_FRAG').map((m) => tile(`data-micro="${m.id}"`, '🔬', m.label, false, !sb)).join('')}</div>
      <p class="muted">Tap an animal in the tank to resize it or take it out.</p>`;
  }
  return h;
}
const STOCK_ICON = { FISH: '🐠', INVERTS: '🦀', CORALS: '🪸' };

// ---- Tank
function tankCard(sb) {
  const t = val('temp'), sg = val('sal');
  let h = `<div class="options">
    ${tile('data-nudge="temp:1"', '🔥', `Warmer${t != null ? ` (${t.toFixed(1)}°C)` : ''}`, false, !sb)}${tile('data-nudge="temp:-1"', '❄️', 'Cooler', false, !sb)}
    ${tile('data-nudge="sal:0.002"', '🧂', `Saltier${sg != null ? ` (${sg.toFixed(3)})` : ''}`, false, !sb)}${tile('data-nudge="sal:-0.002"', '💧', 'Less salty', false, !sb)}
    ${tile('data-lights="on"', '☀️', 'Lights on', false, !sb)}${tile('data-lights="off"', '🌙', 'Lights off', false, !sb)}${tile('data-lights="auto"', '🕒', 'Normal day', false, !sb)}
    ${tile('data-flip="pump_left"', '🌀', `Left pump ${isOn('pump_left') ? 'on' : 'off'}`, isOn('pump_left'), !sb)}
    ${tile('data-flip="pump_right"', '🌀', `Right pump ${isOn('pump_right') ? 'on' : 'off'}`, isOn('pump_right'), !sb)}
    ${tile('data-flip="eq_skimmer"', '🫧', 'Skimmer', isOn('eq_skimmer'), !sb)}${tile('data-flip="eq_sock"', '🧦', 'Filter sock', isOn('eq_sock'), !sb)}
    ${tile('data-flip="eq_carbon"', '⚫', 'Carbon', isOn('eq_carbon'), !sb)}${tile('data-flip="eq_refugium"', '🌿', 'Algae garden', isOn('eq_refugium'), !sb)}
    ${tile('data-flip="eq_uv"', '🟣', 'Germ zapper', isOn('eq_uv'), !sb)}${tile('data-opentreat', '💊', 'Medicine', false, !sb)}
  </div>`;
  if (ui.more || ui.treat) h += tankMore(sb);
  return h;
}
function tankMore(sb) {
  const cats = C.categories.filter((c) => ['WATER', 'CHEMISTRY', 'NUTRIENTS', 'LIGHT_FLOW', 'PESTS'].includes(c.id));
  const rows = C.params.filter((p) => p.category === ui.tab);
  return `<h4>How changes happen</h4><div class="pills">${C.controlModes.map((m) => `<button class="pillbtn ${ui.controlMode === m.id ? 'on' : ''}" data-cmode="${m.id}">${esc(m.label)}</button>`).join('')}</div>
    <div class="pills tabs2">${cats.map((c) => `<button class="pillbtn ${ui.tab === c.id ? 'on' : ''}" data-tab="${c.id}">${esc(c.label)}</button>`).join('')}</div>
    <div class="rows">${rows.map((p) => p.toggle ? toggleRow(p, sb) : paramRow(p, sb)).join('')}</div>
    ${ui.tab === 'LIGHT_FLOW' ? `<label class="slider"><span>Heater set point</span><output id="heaterOut">${(store.get('reeflive.heaterC', 25.5)).toFixed(1)} °C</output>
      <input type="range" data-heater min="20" max="32" step="0.5" value="${store.get('reeflive.heaterC', 25.5)}" ${sb ? '' : 'disabled'}></label>` : ''}
    <h4>Quick doses</h4><div class="options">
      ${[['alk', 1, 'Alk +1'], ['ca', 20, 'Ca +20'], ['mg', 50, 'Mg +50'], ['no3', 5, 'NO3 +5'], ['po4', 0.05, 'PO4 +0.05']].map(([id, d, n]) => tile(`data-dose="${id}:${d}"`, '💉', n, false, !sb)).join('')}</div>
    <h4>Medicine</h4><div class="options">${C.treatments.map((t) => tile(`data-treat="${t.id}:1"`, '💊', `Start ${t.label}`, false, !sb) + tile(`data-treat="${t.id}:0"`, '⏹', `Stop ${t.label}`, false, !sb)).join('')}</div>
    <h4>Other levers</h4><div class="options">
      ${tile('data-predation', P?.predation ? '🦈' : '🕊', P?.predation ? 'Fish can eat fish' : 'No eating', P?.predation, !sb)}
      ${tile('data-acclimate', '💚', 'Acclimate all', false, !sb)}${tile('data-rocks', '🪨', 'Rearrange rock', false, !sb)}</div>`;
}
function fmt(p, v) { return v == null ? '—' : `${Number(v).toFixed(p.decimals)}${p.unit ? ` ${p.unit}` : ''}`; }
const toPos = (p, v) => { const f = Math.min(1, Math.max(0, (v - p.min) / (p.max - p.min))); return p.curve === 1 ? f : f ** (1 / p.curve); };
const fromPos = (p, t) => { const f = p.curve === 1 ? t : t ** p.curve; const v = p.min + f * (p.max - p.min); return Math.round(v / p.step) * p.step; };
function paramRow(p, sb) {
  const v = val(p.id) ?? p.min;
  const st = P?.values?.[p.id]?.status?.toLowerCase() ?? 'unknown';
  const cap = P?.values?.[p.id]?.caption;
  return `<label class="slider zone-${st}"><span>${esc(p.label)}${cap ? ` <small class="muted">${esc(cap)}</small>` : ''}</span><output data-out="${p.id}">${fmt(p, v)}</output>
    <input type="range" data-param="${p.id}" min="0" max="1000" step="1" value="${Math.round(toPos(p, v) * 1000)}" ${sb ? '' : 'disabled'}>${zoneBar(p)}</label>`;
}
function zoneBar(p) {
  const z = p.zones;
  if (!z) return '';
  const x = (v) => `${(toPos(p, Math.min(p.max, Math.max(p.min, v))) * 100).toFixed(1)}%`;
  const parts = [];
  if (z.dangerBelow != null) parts.push(`<i class="z danger" style="left:0;width:${x(z.dangerBelow)}"></i>`);
  parts.push(`<i class="z good" style="left:${x(z.safeFrom)};width:calc(${x(z.safeTo)} - ${x(z.safeFrom)})"></i>`);
  if (z.dangerAbove != null) parts.push(`<i class="z danger" style="left:${x(z.dangerAbove)};right:0"></i>`);
  return `<span class="zones">${parts.join('')}</span>`;
}
function toggleRow(p, sb) {
  return `<div class="toggle-row"><span>${esc(p.label)}</span>${`<button class="switch ${isOn(p.id) ? 'on' : ''}" data-flip="${p.id}" ${sb ? '' : 'disabled'} aria-label="${esc(p.label)}"><i></i></button>`}</div>`;
}
function refreshCardValues() {
  // keep the open card in step with the sim without rebuilding it under the finger
  document.querySelectorAll('#card [data-out]').forEach((o) => {
    const p = C.params.find((x) => x.id === o.dataset.out);
    o.textContent = fmt(p, val(p.id));
    const r = o.parentElement.querySelector('input[type=range]');
    if (r && !ui.dragging) r.value = Math.round(toPos(p, val(p.id) ?? p.min) * 1000);
    o.parentElement.className = `slider zone-${(P.values[p.id]?.status ?? 'unknown').toLowerCase()}`;
  });
}

// ---- Trouble
function troubleCard(sb) {
  let h = `<div class="options">
    ${tile('data-heater-act="break"', '💥', 'Heater breaks', false, !sb)}${tile('data-heater-act="fix"', '🔧', 'Fix heater', false, !sb)}
    ${tile('data-preset="HEATER_STUCK"', '🔥', 'Heater stuck on', false, !sb)}${tile('data-pumpfail', '🌀', 'Pumps stop tonight', false, !sb)}
    ${tile('data-lights="off"', '💡', 'Lamp goes out', false, !sb)}${tile('data-preset="POWER_OUTAGE"', '🔌', 'Power cut', false, !sb)}
    ${tile('data-heatwave', '🥵', 'Heat wave', false, !sb)}${tile('data-preset="SALINITY_CRASH"', '🧂', 'Salt crash', false, !sb)}
    ${tile('data-preset="COPPER_OVERDOSE"', '🧪', 'Copper spill', false, !sb)}${tile('data-preset="OVERFEEDING"', '🍽️', 'Too much food', false, !sb)}
    ${tile('data-preset="MISSED_WATER_CHANGES"', '📅', 'No water changes', false, !sb)}${tile('data-aip="1"', '🌱', 'Pest anemone', false, !sb)}
    ${tile('data-aip="-1"', '⚡', `Zap pest${P?.aiptasia ? ` (${P.aiptasia})` : ''}`, false, !sb)}${tile('data-sick', '🤒', 'A fish gets sick', false, !sb)}
  </div>`;
  if (ui.sickPick) h += `<h4>Which illness?</h4><div class="options">${C.diseases.map((d) => tile(`data-infect="${d.id}"`, '🦠', d.label, false, !sb)).join('')}</div>`;
  if (ui.more) {
    h += `<h4>What-if presets</h4><div class="presets">${C.presets.map((p) => `<button class="preset ${sb ? '' : 'lock'}" data-preset="${p.id}"><strong>${esc(p.label)}</strong><span>${esc(p.blurb)}</span><em>${esc(p.hint)}</em></button>`).join('')}</div>
      <h4>Scenarios</h4><div class="options">${C.scenarios.map((s) => tile(`data-scenario="${s.id}"`, '🌊', s.label, false, !sb)).join('')}</div>
      <p class="muted">Slow vs sudden: fish cope with a slow change far better than a jump. Under Tank → More, "Ramp over a day" makes any slider change slowly.</p>`;
  }
  return h;
}

// ---- More
function moreCard(sb) {
  return `${timeSlider(sb ? [0, ...STOPS] : STOPS)}
    <div class="options">${tile('data-lapse', '⏱', 'Time-lapse')}${sb ? tile('data-reset', '♻️', 'Reset tank') : ''}</div>
    <h4>Tools</h4><div class="options">
      ${tile('data-tips', '❓', 'What can I do?')}${tile('data-news', '✨', "What's new")}${tile('data-microscope', '🔬', 'Look closer')}
      ${tile('data-sound', ui.sound ? '🔊' : '🔇', ui.sound ? 'Sound on' : 'Sound off', ui.sound)}${tile('data-knock', '✊', 'Knock glass')}
      ${tile('data-clean', '🧽', 'Clean glass', ui.cleaning)}${tile('data-hide', '🙈', 'Hide buttons')}
      ${tile('data-hermie-toggle', '🦀', ui.hermie ? 'Hermie on' : 'Hermie off', ui.hermie)}${tile('data-happy-toggle', '😊', ui.happy ? 'Happy meter on' : 'Happy meter off', ui.happy)}
      ${tile('data-thoughts-toggle', '💭', 'Thought bubbles', thoughts.on)}${tile('data-camreset', '🎥', 'Reset view')}</div>
    <h4>Picture quality</h4><div class="pills">${['LOW', 'MEDIUM', 'HIGH'].map((q) => `<button class="pillbtn ${ui.quality === q ? 'on' : ''}" data-quality="${q}">${q[0] + q.slice(1).toLowerCase()}</button>`).join('')}</div>
    <label class="slider"><span>Sound volume</span><output>${Math.round(ui.volume * 100)}%</output><input type="range" data-volume min="0" max="1" step="0.05" value="${ui.volume}"></label>`;
}

// ---- card clicks
const LINES = {
  warmer: 'The heater warms the water up.', cooler: 'Cooler water. Brrr!', saltier: 'A pinch more salt.', fresher: 'Fresh water mixes in.',
  lightsOn: 'Lights on! Good morning, reef.', lightsOff: 'Lights off. Sleepy time.', lightsAuto: 'Back to the normal day and night.',
};
$('card').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  const d = b.dataset;
  const sbOnly = (fn) => { if (ui.mode !== 'sandbox') { locked(); return; } fn(); };
  if ('close' in d) { closeCard(); return; }
  if ('more' in d) { ui.more = !ui.more; ui.treat = false; ui.sickPick = false; }
  else if (d.look) { ui.viewLight = d.look; live.setViewLight(d.look); say(d.look === 'REAL' ? 'The lamp follows the clock again.' : 'A new look for the lamp. Only the picture changes.'); }
  else if ('torch' in d) setTorch(!ui.torch);
  else if ('feedshelf' in d) { closeCard(); openShelf(); return; }
  else if ('tour' in d) { ui.tour = !ui.tour; ui.tourTicks = 0; ui.tourIndex = -1; if (ui.tour) nextTour(); else { ui.following = null; live.follow(null); } }
  else if ('hide' in d) { setChrome(true); return; }
  else if ('saver' in d) { closeCard(); setSaver(true); return; }
  else if ('sub' in d) { setSub(!sub.on); return; }
  else if ('sound' in d) setSoundOn(!ui.sound);
  else if (d.follow) { closeCard(); followFish(d.follow); return; }
  else if (d.group) ui.addGroup = d.group;
  else if (d.count) ui.addCount = Number(d.count);
  else if (d.size) ui.addSize = d.size;
  else if (d.add) sbOnly(() => {
    // new animals come in a shop bag: it goes in your hand, to drag onto the tank (bags.js)
    const s = C.stock.find((x) => x.id === d.add);
    closeCard(); cue('TICK');
    pickBag(s, ui.addCount, s.group === 'CORALS' ? 'JUVENILE' : ui.addSize);
    return;
  });
  else if (d.micro) act('micro', d.micro, 0, `${C.micro.find((m) => m.id === d.micro).label}. Look closer to see them!`);
  else if (d.pest) act('pestControl', d.pest, 0, d.pest === 'BERGHIA' ? 'Berghia slugs only eat pest anemones.' : 'Peppermint shrimp eat pest anemones. Yum!');
  else if ('shells' in d) act('spareShells', null, 0, 'Spare shells for the hermit crabs to move into.');
  else if (d.nudge) sbOnly(() => {
    const [id, delta] = d.nudge.split(':');
    const p = C.params.find((x) => x.id === id);
    const v = Math.min(p.max, Math.max(p.min, (val(id) ?? p.min) + Number(delta)));
    SB.applyParam(id, v, ui.controlMode); cue('TICK');
    say(id === 'temp' ? (Number(delta) > 0 ? LINES.warmer : LINES.cooler) : (Number(delta) > 0 ? LINES.saltier : LINES.fresher));
  });
  else if (d.lights) act('lights', d.lights, 0, d.lights === 'on' ? LINES.lightsOn : d.lights === 'off' ? LINES.lightsOff : LINES.lightsAuto);
  else if (d.flip) sbOnly(() => {
    const on = !isOn(d.flip);
    SB.applyParam(d.flip, on ? 1 : 0, 'SET'); cue('TICK');
    const p = C.params.find((x) => x.id === d.flip);
    say(`${p.label}: ${on ? 'on' : 'off'}.`);
  });
  else if ('opentreat' in d) sbOnly(() => { ui.treat = true; ui.more = true; ui.tab = 'PESTS'; });
  else if (d.cmode) ui.controlMode = d.cmode;
  else if (d.tab) ui.tab = d.tab;
  else if (d.dose) { const [id, n] = d.dose.split(':'); act('dose', id, Number(n), `Dosed ${id.toUpperCase()}. It spreads from the return.`); }
  else if (d.treat) { const [id, on] = d.treat.split(':'); const t = C.treatments.find((x) => x.id === id); act('treatment', id, Number(on), on === '1' ? `${t.label} started.` : `${t.label} stopped.`); }
  else if ('predation' in d) act('predation', null, P?.predation ? 0 : 1, P?.predation ? 'No eating each other. Phew!' : 'Big fish may eat small ones now.');
  else if ('acclimate' in d) act('acclimate', null, 0, 'Everyone gets used to the water now.');
  else if ('rocks' in d) act('rearrangeRock', null, 0, 'Rocks shuffled! Watch everyone find a new spot.');
  else if (d.heaterAct) act('heater', d.heaterAct, 0, d.heaterAct === 'break' ? 'Uh oh, the heater broke. The water will cool.' : 'Heater fixed. Toasty again.');
  else if (d.preset) {
    const p = C.presets.find((x) => x.id === d.preset);
    act('preset', d.preset, 0, `${p.label}. ${p.hint}`);
  }
  else if ('pumpfail' in d) act('pumpFailTonight', null, 0, 'The pumps will stop at 11 tonight for six hours. Speed up time to see.');
  else if ('heatwave' in d) act('heatWave', null, 0, 'A heat wave: the water warms 3 °C over a day.');
  else if (d.aip) act('aiptasia', null, Number(d.aip), Number(d.aip) > 0 ? 'A pest anemone popped up on the rock.' : 'Zap! One pest anemone gone.');
  else if ('sick' in d) sbOnly(() => { ui.sickPick = !ui.sickPick; });
  else if (d.infect) { if (confirm('Make a fish sick? This is only the pretend tank.')) act('infect', d.infect, 0, 'A fish is getting sick. Watch its card.'); ui.sickPick = false; }
  else if (d.scenario) act('scenario', d.scenario, 0, `${C.scenarios.find((s) => s.id === d.scenario).label}. Speed up time to see it play out.`);
  else if ('lapse' in d) { closeCard(); openLapse(); return; }
  else if ('reset' in d) { if (confirm('Reset the pretend tank to how your real tank is now?')) act('reset', null, 0, 'All fresh! The pretend tank matches your real one again.'); }
  else if ('tips' in d) showTips();
  else if ('news' in d) { closeCard(); showNews(); return; }
  else if ('microscope' in d) { closeCard(); openMicro(); return; }
  else if ('knock' in d) { act('knock'); cue('CLICK'); }
  else if ('clean' in d) { closeCard(); setCleaning(!ui.cleaning); return; }
  else if ('hermieToggle' in d) { ui.hermie = !ui.hermie; store.set('reeflive.hermie', ui.hermie); $('hermie').classList.toggle('hidden', !ui.hermie); }
  else if ('happyToggle' in d) { ui.happy = !ui.happy; store.set('reeflive.happy', ui.happy); }
  else if ('thoughtsToggle' in d) setThoughts(!thoughts.on);
  else if ('camreset' in d) { ui.following = null; ui.tour = false; live.follow(null); live.resetCamera(); }
  else if (d.quality) { ui.quality = d.quality; store.set('reeflive.quality', ui.quality); live.setQuality(ui.quality); }
  else return;
  renderCard();
});
$('card').addEventListener('input', (e) => {
  const r = e.target;
  const d = r.dataset;
  if ('time' in d) {
    const stops = d.stops.split(',').map(Number);
    const v = stops[Number(r.value)];
    r.previousElementSibling.textContent = v === 0 ? 'Paused' : v === 1 ? 'Real time' : `${v}× faster`;
    setSpeed(v);
  } else if (d.param) {
    const p = C.params.find((x) => x.id === d.param);
    const v = fromPos(p, Number(r.value) / 1000);
    r.parentElement.querySelector('output').textContent = fmt(p, v);
    ui.dragging = true;
    clearTimeout(ui.dragTimer);
    ui.dragTimer = setTimeout(() => { SB.applyParam(p.id, v, ui.controlMode); ui.dragging = false; tick(); }, 180);
  } else if ('heater' in d) {
    $('heaterOut').textContent = `${Number(r.value).toFixed(1)} °C`;
    store.set('reeflive.heaterC', Number(r.value));
    SB.act('heater', 'set', Number(r.value));
  } else if ('volume' in d) {
    ui.volume = Number(r.value); store.set('reeflive.volume', ui.volume);
    r.previousElementSibling.textContent = `${Math.round(ui.volume * 100)}%`;
  }
});

// ================================================================ fish card, follow, tour
function select(id) { ui.picked = id; renderFish(); }
function renderFish() {
  const a = ui.picked && animals.find((x) => x.id === ui.picked);
  $('fish').classList.toggle('hidden', !a || ui.chromeHidden);
  if (!a) return;
  if ($('fish').dataset.id === a.id && ui.fishDragging) return;
  $('fish').dataset.id = a.id;
  const bar = (n, v, cls) => `<div class="stat"><span>${n}</span><i class="meter"><i class="${cls}" style="width:${Math.round(Math.max(0, Math.min(1, v)) * 100)}%"></i></i></div>`;
  const sb = ui.mode === 'sandbox';
  $('fish').innerHTML = `<div class="card-head"><div><strong>${esc(a.name)}</strong><div class="muted">${esc(a.species)}${a.stage ? ` · ${esc(a.stage)}` : ''} · ${a.cm.toFixed(1)} cm</div></div>
      <button class="icon-btn" data-fclose aria-label="Close">✕</button></div>
    ${bar('Health', a.health, 'good')}${bar('Stress', a.stress, 'caution')}${bar('Hunger', a.hunger, 'hunger')}
    ${a.thinking ? thinkHtml(a) : `<p class="mood"><b>${esc(a.mood)}</b> · ${esc(a.behavior)}</p>`}
    ${a.disease ? `<p class="warn">🤒 ${esc(a.disease)}${a.symptoms.length ? `: ${esc(a.symptoms.join(', '))}` : ''}</p>` : ''}
    ${a.relations.length ? `<div class="rels">${a.relations.map((r) => `<span class="rel ${r.affinity > 0.15 ? 'good' : r.affinity < -0.15 ? 'danger' : ''}">${esc(r.label)} ${esc(r.name)}</span>`).join('')}</div>` : ''}
    <div class="row-gap"><button class="btn small" data-ffollow>${ui.following === a.id ? 'Stop following' : 'Follow'}</button>
      ${sb ? '<button class="btn small ghost" data-fremove>Take out</button>' : ''}</div>
    ${sb ? `<label class="slider"><span>Size</span><output>${a.cm.toFixed(1)} cm</output><input type="range" data-fsize min="0.5" max="40" step="0.5" value="${a.cm}"></label>` : ''}`;
}
$('fish').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  if ('fclose' in d) { ui.picked = null; renderFish(); }
  else if ('ffollow' in d) followFish(ui.following === ui.picked ? null : ui.picked);
  else if ('fremove' in d) { if (confirm('Take this animal out of the pretend tank?')) { act('remove', ui.picked, 0, 'Out it goes, gently, in a cup.'); ui.picked = null; } }
  renderFish();
});
$('fish').addEventListener('input', (e) => {
  if (!('fsize' in e.target.dataset)) return;
  ui.fishDragging = true;
  e.target.previousElementSibling.textContent = `${Number(e.target.value).toFixed(1)} cm`;
  clearTimeout(ui.sizeTimer);
  ui.sizeTimer = setTimeout(() => { SB.act('setSize', ui.picked, Number(e.target.value)); ui.fishDragging = false; }, 200);
});
function followFish(id) {
  ui.following = id; if (id) ui.picked = id;
  if (id && ui.card) closeCard();
  live.follow(id);
  renderFish(); renderFollow();
}
function renderFollow() {
  const i = animals.findIndex((x) => x.id === ui.following);
  $('follow').classList.toggle('hidden', i < 0 || !!ui.card || ui.shelf || ui.chromeHidden && !ui.tour);
  if (i < 0) return;
  const a = animals[i];
  $('follow').innerHTML = `<button class="icon-btn" data-fprev aria-label="Previous">◀</button>
    <div class="fcap"><strong>${esc(a.name)}</strong> <span class="muted">${i + 1} / ${animals.length}</span><div class="muted">${esc(a.thinking || a.caption || a.behavior)}</div></div>
    <button class="icon-btn" data-fnext aria-label="Next">▶</button><button class="btn small ghost" data-fback>Back to tank</button>`;
}
$('follow').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  const i = animals.findIndex((x) => x.id === ui.following);
  if ('fback' in d) { ui.tour = false; followFish(null); live.resetCamera(); }
  else if ('fprev' in d) followFish(animals[(i - 1 + animals.length) % animals.length].id);
  else if ('fnext' in d) followFish(animals[(i + 1) % animals.length].id);
});
function nextTour() {
  ui.tourTicks = 0;
  if (!animals.length) return;
  ui.tourIndex = ((ui.tourIndex ?? -1) + 1) % Math.min(12, animals.length);
  followFish(animals[ui.tourIndex].id);
}

// ================================================================ supplies (3D shelf)
const SUPPLIES = [
  ...['FLAKE', 'PELLET', 'FLOATING_PELLET', 'ALGAE_WAFER', 'MYSIS', 'BRINE', 'KRILL', 'PRAWN', 'CYCLOPS', 'NORI', 'CORAL_FOOD', 'PLANKTON', 'PODS']
    .map((k) => ({ id: k.toLowerCase(), row: 0, food: k })),
  { id: 'water_change', row: 1, label: 'Change water', sandbox: true, run: () => act('waterChange', null, 0.25, 'Out with the old, in with the new. 25 % fresh water!') },
  { id: 'water_small', row: 1, label: 'Small change', sandbox: true, run: () => act('waterChange', null, 0.10, 'A small 10 % water change.') },
  { id: 'dose_alk', row: 1, label: 'Shell builder', sandbox: true, run: () => act('dose', 'alk', 1, 'Alkalinity +1 for coral skeletons.') },
  { id: 'dose_ca', row: 1, label: 'Calcium', sandbox: true, run: () => act('dose', 'ca', 20, 'Calcium +20.') },
  { id: 'dose_mg', row: 1, label: 'Magnesium', sandbox: true, run: () => act('dose', 'mg', 50, 'Magnesium +50.') },
  { id: 'new_sock', row: 1, label: 'New sock', sandbox: true, run: () => act('maintain', 'CHANGE_SOCK', 0, 'A clean filter sock. Fresh!') },
  { id: 'fresh_carbon', row: 1, label: 'Fresh carbon', sandbox: true, run: () => act('maintain', 'REPLACE_CARBON', 0, 'New carbon keeps the water clear.') },
  { id: 'trim_algae', row: 1, label: 'Trim algae', sandbox: true, run: () => act('maintain', 'HARVEST_MACRO', 0, 'Snip snip! The algae garden is trimmed.') },
  { id: 'zap_pest', row: 1, label: 'Zap pest', sandbox: true, run: () => act('aiptasia', null, -1, 'Zap! One pest anemone gone.') },
  { id: 'settle_in', row: 1, label: 'Settle in', sandbox: true, run: () => act('acclimate', null, 0, 'Everyone gets used to the water now.') },
  { id: 'glass_cleaner', row: 2, label: 'Clean glass', run: () => { closeShelf(); setCleaning(true); } },
  { id: 'test_kit', row: 2, label: 'Test kit', run: () => testKit() },
  { id: 'microscope', row: 2, label: 'Look closer', run: () => { closeShelf(); openMicro(); } },
  { id: 'knocker', row: 2, label: 'Knock glass', run: () => { closeShelf(); act('knock'); cue('CLICK'); } },
  { id: 'move_rocks', row: 2, label: 'Move rocks', sandbox: true, run: () => act('rearrangeRock', null, 0, 'Rocks shuffled! Watch everyone find a new spot.') },
];
const ROWS = [['🍤', 'Feed'], ['💧', 'Care'], ['🧰', 'Tools']];
function supplyLabel(s) { return s.food ? C.foods.find((f) => f.id === s.food)?.label ?? s.food : s.label; }
function openShelf() {
  ui.shelf = true; closeCard();
  ui.picked = null; renderFish();
  if (ui.following) followFish(null);
  live.shelfOpen(true); live.shelfRow(ui.shelfRow);
  renderShelf(); renderRail();
  say('Tap a food or tool on the shelf to use it.');
}
function closeShelf() { ui.shelf = false; live.shelfOpen(false); $('shelfbar').classList.add('hidden'); renderRail(); }
function renderShelf() {
  const sb = ui.mode === 'sandbox';
  $('shelfbar').innerHTML = `<div class="card-head"><div class="pills">${ROWS.map(([i, n], k) => `<button class="pillbtn ${ui.shelfRow === k ? 'on' : ''}" data-row="${k}">${i} ${n}</button>`).join('')}</div>
    <button class="btn small ghost" data-back>Back to tank</button></div>
    <div class="options">${SUPPLIES.filter((s) => s.row === ui.shelfRow).map((s) => tile(`data-supply="${s.id}"`, s.food ? FOOD_ICON[s.food] ?? '🍤' : SUPPLY_ICON[s.id] ?? '🧰', supplyLabel(s), false, s.sandbox && !sb)).join('')}</div>`;
  $('shelfbar').classList.remove('hidden');
}
const FOOD_ICON = { FLAKE: '🟠', PELLET: '🟤', FLOATING_PELLET: '🟫', ALGAE_WAFER: '🟢', MYSIS: '🦐', BRINE: '🦐', KRILL: '🦐', PRAWN: '🍤', CYCLOPS: '🔴', NORI: '🥬', CORAL_FOOD: '🪸', PLANKTON: '✨', PODS: '🐛' };
const SUPPLY_ICON = { water_change: '🪣', water_small: '💧', dose_alk: '🐚', dose_ca: '🦴', dose_mg: '⭕', new_sock: '🧦', fresh_carbon: '⚫', trim_algae: '✂️', zap_pest: '⚡', settle_in: '💚', glass_cleaner: '🧽', test_kit: '🧪', microscope: '🔬', knocker: '✊', move_rocks: '🪨' };
$('shelfbar').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  if ('back' in d) closeShelf();
  else if (d.row) { ui.shelfRow = Number(d.row); live.shelfRow(ui.shelfRow); renderShelf(); }
  else if (d.supply) live.shelfPick(d.supply); // lifts the prop in 3D; the renderer calls back onShelfPick
});
/** A prop picked on the shelf (tapped in 3D or on its tag): run it like SupplyItem.use. */
function useSupply(id) {
  const s = SUPPLIES.find((x) => x.id === id);
  if (!s) return;
  if (s.sandbox && ui.mode !== 'sandbox') { locked(); return; }
  if (s.food) {
    const f = C.foods.find((x) => x.id === s.food);
    SB.act('feed', s.food, 0);
    cue(['PELLET', 'FLOATING_PELLET', 'ALGAE_WAFER'].includes(s.food) ? 'PELLET' : 'FEED');
    say(`${f.label}! ${f.blurb}`);
    setTimeout(closeShelf, 700);
    return;
  }
  s.run();
  if (s.id !== 'test_kit' && ui.shelf) setTimeout(closeShelf, 700);
}
function testKit() {
  const r = P.readouts.map((x) => `${x.label} ${x.text}`).join(' · ');
  const extra = ['ph', 'alk', 'ca', 'mg', 'nh3', 'no2'].map((id) => { const p = C.params.find((x) => x.id === id); return `${p.short} ${fmt(p, val(id))}`; }).join(' · ');
  say(`Test kit: ${r} · ${extra}`);
}

// ================================================================ screensaver
// Full-screen calm mode: the shared CinemaDirector moves the camera; this only draws the dip-to-black and caption.
function setSaver(on) {
  ui.saver = on;
  document.body.classList.toggle('saver-on', on);
  if (on) { ui.tour = false; setChrome(true); $('showChrome').classList.add('hidden'); }
  live.screensaver(on);
  $('saver').classList.toggle('hidden', !on);
  if (on) { $('saverHint').style.opacity = 1; setTimeout(() => { $('saverHint').style.opacity = 0; }, 3000); requestAnimationFrame(saverFrame); }
  else setChrome(false);
  if (on && document.documentElement.requestFullscreen) document.documentElement.requestFullscreen().catch(() => {});
  if (!on && document.fullscreenElement) document.exitFullscreen().catch(() => {});
}
function saverFrame() {
  if (!ui.saver) return;
  const c = JSON.parse(live.cinema());
  if (c) {
    $('saverBlack').style.opacity = c.fade;
    $('saverCaption').style.opacity = c.alpha;
    if ($('saverCaption').textContent !== c.caption) $('saverCaption').textContent = c.caption;
  }
  requestAnimationFrame(saverFrame);
}
$('saver').addEventListener('click', () => setSaver(false));
document.addEventListener('keydown', (e) => { if (ui.saver && e.key === 'Escape') setSaver(false); });

// ================================================================ chrome, torch, cleaning
function setChrome(hidden) {
  ui.chromeHidden = hidden;
  $('chrome').classList.toggle('hidden', hidden);
  $('showChrome').classList.toggle('hidden', !hidden);
  if (hidden) closeCard();
  renderFish(); renderFollow();
}
$('hideBtn').addEventListener('click', () => setChrome(true));
$('showChrome').addEventListener('click', () => setChrome(false));
function setTorch(on) {
  ui.torch = on;
  $('torch').classList.toggle('hidden', !on);
  if (on) { moveTorch(innerWidth / 2, innerHeight / 2); say('Torch on. Point it with your finger to find the night crawlers.'); }
}
function moveTorch(x, y) {
  const r = $('tank').getBoundingClientRect();
  $('torch').style.setProperty('--x', `${x - r.left}px`);
  $('torch').style.setProperty('--y', `${y - r.top}px`);
}
$('tank').addEventListener('pointermove', (e) => { if (ui.torch && e.buttons) moveTorch(e.clientX, e.clientY); });
$('tank').addEventListener('pointerdown', (e) => { if (ui.torch) moveTorch(e.clientX, e.clientY); });
function setCleaning(on) {
  ui.cleaning = on;
  SB.act('cleaning', null, on ? 1 : 0);
  $('cleanHint').classList.toggle('hidden', !on);
  $('cleanBtn').classList.toggle('on', on);
  say(on ? 'Rub the glass with the scraper. Squeaky clean!' : 'Sparkly glass!');
}
$('cleanBtn').addEventListener('click', () => setCleaning(!ui.cleaning));
$('cleanDone').addEventListener('click', () => setCleaning(false));
$('microBtn').addEventListener('click', () => openMicro());
$('soundBtn').addEventListener('click', () => setSoundOn(!ui.sound));

// ================================================================ microscope
const micro = { scene: 'ROCK', zoom: 'X10', bacteria: false, seed: 1, sprites: [] };
function openMicro() {
  $('micro').classList.remove('hidden');
  renderMicroControls();
  drawMicro(true);
  say('Look closer! Pick rock, glass, sand or water, then a zoom.');
}
$('microClose').addEventListener('click', () => $('micro').classList.add('hidden'));
function renderMicroControls() {
  $('microScenes').innerHTML = C.microScenes.map((s) => `<button class="pillbtn ${micro.scene === s.id ? 'on' : ''}" data-scene="${s.id}">${esc(s.label)}</button>`).join('')
    + `<button class="pillbtn ${micro.bacteria ? 'on' : ''}" data-bact>🦠 Bacteria</button>`;
  $('microZooms').innerHTML = C.microZooms.map((z) => `<button class="pillbtn ${micro.zoom === z.id ? 'on' : ''}" data-zoom="${z.id}">${esc(z.label)}</button>`).join('');
}
$('micro').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  if (d.scene) { micro.scene = d.scene; micro.seed++; }
  else if (d.zoom) { micro.zoom = d.zoom; micro.seed++; }
  else if ('bact' in d) micro.bacteria = !micro.bacteria;
  else if (d.maction) { act('micro', d.maction, 0, `${C.micro.find((m) => m.id === d.maction).label}.`); setTimeout(() => drawMicro(true), 300); return; }
  else if (d.baction) { act('bacteria', d.baction, 0, `${C.bacteria.find((m) => m.id === d.baction).label}.`); return; }
  else return;
  renderMicroControls();
  drawMicro(true);
});
const GROUP_COLOR = ['#e9d8a6', '#94d2bd', '#ee9b00', '#ca6702', '#bb3e03', '#ae2012', '#9b2226', '#0a9396', '#e0aaff', '#ffd6a5', '#caffbf', '#9bf6ff'];
function drawMicro(resample) {
  const m = JSON.parse(SB.micro(micro.scene, micro.zoom, micro.seed + Math.floor(Date.now() / 3.6e6)));
  if (!m) return;
  const cv = $('microCanvas');
  const dpr = Math.min(2, devicePixelRatio || 1);
  const w = cv.clientWidth, h = cv.clientHeight;
  if (cv.width !== Math.round(w * dpr)) { cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); }
  const g = cv.getContext('2d');
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  const bg = { ROCK: ['#3b3a36', '#26241f'], GLASS: ['#0f2c3a', '#07141c'], SAND: ['#cdbf9c', '#a99b78'], WATER: ['#0d3550', '#061a29'] }[micro.scene];
  const grd = g.createRadialGradient(w / 2, h / 2, 10, w / 2, h / 2, Math.max(w, h) / 1.4);
  grd.addColorStop(0, bg[0]); grd.addColorStop(1, bg[1]);
  g.fillStyle = grd; g.fillRect(0, 0, w, h);
  if (resample || micro.sprites.length === 0 || micro.key !== `${micro.scene}${micro.zoom}`) {
    micro.key = `${micro.scene}${micro.zoom}`;
    micro.sprites = [];
    m.groups.forEach((gr, gi) => {
      const px = Math.max(2, (gr.sizeMm / m.edgeMm) * Math.max(w, h));
      for (let i = 0; i < gr.sprites + gr.nauplii; i++) {
        micro.sprites.push({ x: Math.random() * w, y: Math.random() * h, a: Math.random() * 6.28, s: i >= gr.sprites ? px * 0.35 : px, c: GROUP_COLOR[gi % GROUP_COLOR.length], v: 0.3 + Math.random() });
      }
    });
  }
  for (const s of micro.sprites) {
    s.a += (Math.random() - 0.5) * 0.6; s.x = (s.x + Math.cos(s.a) * s.v * 6 + w) % w; s.y = (s.y + Math.sin(s.a) * s.v * 6 + h) % h;
    g.save(); g.translate(s.x, s.y); g.rotate(s.a);
    g.fillStyle = s.c; g.beginPath(); g.ellipse(0, 0, s.s / 2, Math.max(1, s.s / 4), 0, 0, 6.28); g.fill();
    if (s.s > 6) { g.strokeStyle = s.c; g.lineWidth = 1; g.beginPath(); g.moveTo(s.s / 2, 0); g.lineTo(s.s * 0.8, -s.s * 0.25); g.moveTo(s.s / 2, 0); g.lineTo(s.s * 0.8, s.s * 0.25); g.stroke(); }
    g.restore();
  }
  for (let i = 0; i < m.cells; i++) { g.fillStyle = 'rgba(110,200,90,.8)'; g.beginPath(); g.arc(Math.random() * w, Math.random() * h, 1.5, 0, 6.28); g.fill(); }
  if (m.haze > 0) { g.fillStyle = `rgba(80,160,70,${m.haze * 0.6})`; g.fillRect(0, 0, w, h); }
  if (m.night) { g.fillStyle = 'rgba(0,0,20,.25)'; g.fillRect(0, 0, w, h); }
  const sb = ui.mode === 'sandbox';
  const census = m.census.map((c) => `<span class="chip">${esc(c.label)} ${c.total >= 1000 ? `${(c.total / 1000).toFixed(1)}k` : Math.round(c.total)} ${c.trend > 0.01 ? '↑' : c.trend < -0.01 ? '↓' : ''}</span>`).join('');
  const inView = m.groups.map((gr) => `<span class="chip" style="color:${GROUP_COLOR[m.groups.indexOf(gr) % GROUP_COLOR.length]}">${esc(gr.label)} ${gr.sprites}${gr.nauplii ? ` + ${gr.nauplii} babies` : ''}</span>`).join('') || '<span class="muted">Nothing big enough to see at this zoom.</span>';
  const bact = micro.bacteria ? `<h4>Bacteria</h4><div class="rows">${m.bacteria.guilds.map((b) => `<div class="toggle-row"><span>${esc(b.short)} <small class="muted">${esc(b.amount)} · ${esc(b.effect)}</small></span><span class="${b.good === false ? 'danger' : 'good'}">${b.trend > 0.01 ? '↑' : b.trend < -0.01 ? '↓' : '·'}</span></div>`).join('')}</div>
    ${m.bacteria.cloudy ? '<p class="warn">Milky water: a bacterial bloom.</p>' : ''}${m.bacteria.h2s > 0.002 ? '<p class="warn">Smells of rotten eggs: hydrogen sulfide in the sand.</p>' : ''}
    <div class="options">${C.bacteria.map((b) => tile(`data-baction="${b.id}"`, '🧫', b.label, false, !sb)).join('')}</div>` : '';
  $('microInfo').innerHTML = `<div class="chips">${inView}</div><h4>In the whole tank</h4><div class="chips">${census}<span class="chip">Phyto ${Math.round(m.phyto)}/mL</span></div>
    <h4>Add or treat</h4><div class="options">${C.micro.map((a) => tile(`data-maction="${a.id}"`, a.id.startsWith('TREAT') ? '💊' : '🦠', a.label, false, !sb)).join('')}</div>${bact}`;
}
let microAnim = 0;
(function animateMicro() {
  if (!$('micro').classList.contains('hidden') && micro.sprites.length) {
    // light animation between the 500 ms samples: just move sprites
    drawMicroFrame();
  }
  microAnim = requestAnimationFrame(animateMicro);
}());
function drawMicroFrame() {
  // cheap redraw of the cached sprites only every few frames (the tank keeps rendering underneath)
  if ((++micro.frame || (micro.frame = 1)) % 4) return;
  const cv = $('microCanvas'); const g = cv.getContext('2d');
  const w = cv.clientWidth, h = cv.clientHeight;
  g.globalAlpha = 0.35; g.fillStyle = { ROCK: '#2e2c27', GLASS: '#0a1e28', SAND: '#bcae8a', WATER: '#0a2a40' }[micro.scene]; g.fillRect(0, 0, w, h); g.globalAlpha = 1;
  for (const s of micro.sprites) {
    s.a += (Math.random() - 0.5) * 0.4; s.x = (s.x + Math.cos(s.a) * s.v * 2 + w) % w; s.y = (s.y + Math.sin(s.a) * s.v * 2 + h) % h;
    g.save(); g.translate(s.x, s.y); g.rotate(s.a); g.fillStyle = s.c; g.beginPath(); g.ellipse(0, 0, s.s / 2, Math.max(1, s.s / 4), 0, 0, 6.28); g.fill(); g.restore();
  }
}

// ================================================================ time-lapse
const lapse = { param: 'temp', target: 31, ramp: true, rampHours: 24, hours: 168, label: '', result: null };
function openLapse() { $('lapse').classList.remove('hidden'); renderLapse(); }
$('lapseClose').addEventListener('click', () => $('lapse').classList.add('hidden'));
const HOURS = [['6 h', 6], ['1 d', 24], ['3 d', 72], ['1 wk', 168], ['2 wk', 336], ['4 wk', 672]];
function renderLapse() {
  const p = C.lapseParams.find((x) => x.id === lapse.param);
  if (lapse.result) { renderLapseResult(); return; }
  $('lapseBody').innerHTML = `<p class="muted">Fast-forward a copy of the tank and see who copes. Your tank on screen keeps going.</p>
    <h4>Try one</h4><div class="pills">${C.lapsePresets.map((x, i) => `<button class="pillbtn" data-lpreset="${i}">${esc(x.label)}</button>`).join('')}</div>
    <h4>What changes</h4><div class="pills">${C.lapseParams.map((x) => `<button class="pillbtn ${lapse.param === x.id ? 'on' : ''}" data-lparam="${x.id}">${esc(x.label)}</button>`).join('')}</div>
    <label class="slider"><span>To</span><output id="lapseOut">${fmt(p, lapse.target)}</output>
      <input type="range" data-ltarget min="0" max="1000" value="${Math.round(toPos(p, lapse.target) * 1000)}"></label>
    <h4>How</h4><div class="pills"><button class="pillbtn ${!lapse.ramp ? 'on' : ''}" data-lramp="0">Step (all at once)</button>
      ${HOURS.slice(0, 4).map(([n, hh]) => `<button class="pillbtn ${lapse.ramp && lapse.rampHours === hh ? 'on' : ''}" data-lramp="${hh}">Ramp over ${n}</button>`).join('')}</div>
    <h4>For how long</h4><div class="pills">${HOURS.map(([n, hh]) => `<button class="pillbtn ${lapse.hours === hh ? 'on' : ''}" data-lhours="${hh}">${n}</button>`).join('')}</div>
    <div class="row-gap"><button class="btn" data-lrun>▶ Run time-lapse</button></div>`;
}
$('lapse').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  if (d.lpreset) { const x = C.lapsePresets[Number(d.lpreset)]; Object.assign(lapse, { param: x.param, target: x.target, ramp: x.ramp, rampHours: x.ramp ? x.rampHours : lapse.rampHours, hours: x.hours, label: x.label }); }
  else if (d.lparam) { lapse.param = d.lparam; const p = C.lapseParams.find((x) => x.id === d.lparam); lapse.target = Math.min(p.max, Math.max(p.min, val(d.lparam === 'sg' ? 'sal' : d.lparam) ?? p.min)); lapse.label = ''; }
  else if (d.lramp) { lapse.ramp = d.lramp !== '0'; if (lapse.ramp) lapse.rampHours = Number(d.lramp); lapse.label = ''; }
  else if (d.lhours) lapse.hours = Number(d.lhours);
  else if ('lrun' in d) { runLapse(); return; }
  else if ('lnew' in d) lapse.result = null;
  else return;
  renderLapse();
});
$('lapse').addEventListener('input', (e) => {
  if (!('ltarget' in e.target.dataset)) return;
  const p = C.lapseParams.find((x) => x.id === lapse.param);
  lapse.target = fromPos(p, Number(e.target.value) / 1000); lapse.label = '';
  $('lapseOut').textContent = fmt(p, lapse.target);
});
function runLapse() {
  $('lapseBody').innerHTML = '<div class="loading inline"><div class="spinner"></div><p>Fast-forwarding a copy of the tank…</p></div>';
  setTimeout(() => {
    try { lapse.result = JSON.parse(SB.timelapse(lapse.param, lapse.target, lapse.ramp, lapse.rampHours, lapse.hours, lapse.label)); }
    catch (err) { lapse.result = null; $('lapseBody').innerHTML = `<p class="warn">The time-lapse failed: ${esc(err.message)}</p>`; return; }
    renderLapse();
  }, 60);
}
function renderLapseResult() {
  const r = lapse.result;
  const W = 300, H = 70;
  const line = (vals, lo, hi) => vals.map((v, i) => `${i ? 'L' : 'M'}${((i / Math.max(1, vals.length - 1)) * W).toFixed(1)},${(H - ((v - lo) / ((hi - lo) || 1)) * (H - 6) - 3).toFixed(1)}`).join('');
  const lo = Math.min(...r.param), hi = Math.max(...r.param);
  const VCLS = ['good', 'caution', 'caution', 'danger'];
  $('lapseBody').innerHTML = `<h3>${esc(r.label)}</h3><p>${esc(r.headline)}</p>
    <svg class="lchart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none"><path d="${line(r.param, lo, hi)}" fill="none" stroke="#5CE1E6" stroke-width="2" vector-effect="non-scaling-stroke"/></svg>
    <p class="muted">${esc(C.lapseParams.find((x) => x.id === lapse.param).label)} from ${lo.toFixed(2)} to ${hi.toFixed(2)} over ${r.hours.length ? Math.round(r.hours[r.hours.length - 1]) : 0} h</p>
    <div class="verdicts">${r.species.map((s) => `<div class="verdict ${VCLS[Math.min(3, s.severity)]}"><div><strong>${esc(s.name)}</strong>${s.count > 1 ? ` <span class="muted">×${s.count}</span>` : ''}
      <div class="muted">${esc(s.verdict)}${s.died ? ` · ${s.died} died` : ''}${s.onset != null ? ` · from hour ${Math.round(s.onset)}` : ''}${s.cause ? ` · ${esc(s.cause)}` : ''}</div></div>
      <svg viewBox="0 0 ${W} 30" preserveAspectRatio="none"><path d="${line(s.health, 0, 1).replace(/,(-?[\d.]+)/g, (m, y) => `,${(Number(y) * 30 / H).toFixed(1)}`)}" fill="none" stroke="currentColor" stroke-width="2" vector-effect="non-scaling-stroke"/></svg></div>`).join('')}</div>
    <div class="row-gap"><button class="btn ghost" data-lnew>New time-lapse</button></div>`;
}

// ================================================================ sound (TankSoundMix beds and cues, Web Audio)
let actx = null; const buffers = {}; const loops = {};
const LOOPS = { flow: 'tank_flow_loop', hum: 'tank_hum_loop', hob: 'tank_hob_loop', bubbles: 'tank_bubbles_loop' };
const CUES = { FEED: 'tank_feed_plop', PELLET: 'tank_pellet_drop', CLICK: 'tank_shrimp_click', TICK: 'tank_ui_tick', ALERT: 'tank_alert_chime', SCRAPE: 'tank_glass_scrape' };
async function loadSound(name) {
  if (buffers[name]) return buffers[name];
  for (const ext of ['m4a', 'ogg']) {
    try {
      const r = await fetch(`sounds/${name}.${ext}`);
      if (!r.ok) continue;
      buffers[name] = await actx.decodeAudioData(await r.arrayBuffer());
      return buffers[name];
    } catch { /* try the next format */ }
  }
  return null;
}
function setSoundOn(on) {
  ui.sound = on; store.set('reeflive.sound', on);
  $('soundBtn').textContent = on ? '🔊' : '🔇';
  if (on) {
    if (!actx) actx = new (window.AudioContext || window.webkitAudioContext)();
    actx.resume();
    Object.values(LOOPS).concat(Object.values(CUES)).forEach(loadSound);
  } else stopLoops();
  say(on ? 'Sound on. Listen to the bubbles!' : 'Sound off.');
}
function stopLoops() { for (const k of Object.keys(loops)) { try { loops[k].src.stop(); } catch { /* already stopped */ } delete loops[k]; } }
function updateSound() {
  if (!ui.sound || !actx || document.hidden) return;
  const s = JSON.parse(SB.sound(ui.volume));
  if (!s) return;
  for (const [k, name] of Object.entries(LOOPS)) {
    const target = s[k];
    if (!loops[k] && buffers[name] && target > 0.001) {
      const src = actx.createBufferSource(); src.buffer = buffers[name]; src.loop = true;
      const gain = actx.createGain(); gain.gain.value = 0;
      src.connect(gain).connect(actx.destination); src.start();
      loops[k] = { src, gain };
    }
    if (loops[k]) loops[k].gain.gain.setTargetAtTime(target, actx.currentTime, 0.4);
  }
  s.cues.forEach((c) => cue(c));
}
const lastCue = {};
function cue(name, gain = 0.8) {
  if (!ui.sound || !actx) return;
  const now = performance.now();
  if (now - (lastCue[name] || 0) < (name === 'TICK' ? 60 : 400)) return;
  lastCue[name] = now;
  const b = buffers[CUES[name]];
  if (!b) return;
  const src = actx.createBufferSource(); src.buffer = b;
  const g = actx.createGain(); g.gain.value = gain * ui.volume;
  src.connect(g).connect(actx.destination); src.start();
}
// a tap with no fish under it (Android LiveTaps): undo what is open first, else touch the tank there
let touchSaidAt = 0;
function touchTank(surface) {
  if (ui.chromeHidden) { setChrome(false); return; }
  if (performance.now() - (ui.cardClosedAt || 0) < 700) return;
  if (ui.picked && !ui.following) { select(null); return; }
  if (!live.touchTank()) return;
  knockSound(surface === 'GLASS' ? 1 : 0.7);
  try { navigator.vibrate?.(8); } catch { /* not on iOS */ }
  if (performance.now() - touchSaidAt > 9000) {
    touchSaidAt = performance.now();
    say({ GLASS: 'Tap tap! Who comes to look?', SAND: 'A little sand puff. Who comes to check?', SURFACE: 'Plip! Hungry fish come up to look.' }[surface] || 'Soft corals pull in, the shy ones duck.');
  }
}
let fingerSaidAt = 0;
function fingerSay() {
  if (performance.now() - fingerSaidAt < 20000) return;
  fingerSaidAt = performance.now();
  say('Slide your finger. Who follows? Rest it and they nibble.');
}
function knockSound(gain) {
  const b = ui.sound && actx && buffers[CUES.PELLET];
  if (!b) return;
  const src = actx.createBufferSource(); src.buffer = b; src.playbackRate.value = 0.5 + Math.random() * 0.08;
  const g = actx.createGain(); g.gain.value = 0.55 * gain * ui.volume;
  src.connect(g).connect(actx.destination); src.start();
}
function scrapeSound(cm) { cue('SCRAPE', Math.min(1, 0.45 * Math.max(0.4, Math.min(1, cm / 1.5)) / 0.45)); }
// iOS only allows audio after a tap: resume the context on the first touch when sound was left on
document.addEventListener('pointerdown', () => { if (ui.sound && !actx) setSoundOn(true); else if (actx?.state === 'suspended') actx.resume(); }, { once: false, passive: true });

// ================================================================ what is it thinking (Android ThinkingBlock / ThoughtBubbleLayer)
// The card's "Thinking…" lines come held from Kotlin (ThoughtHold); More → "Thought bubbles" puts speech bubbles over at
// most three animals (ThoughtBubblePicker in Kotlin picks them, fades them; this only draws them where the camera sees them).
function thinkHtml(a) {
  return `<div class="think"><b>Thinking…</b><p>${esc(a.thinking)}</p>${a.thinkNext ? `<p class="muted">${esc(a.thinkNext)}</p>` : ''}${a.feeling ? `<p class="muted">${esc(a.feeling)}</p>` : ''}</div>`;
}
const thoughts = { on: store.get('reeflive.thoughts', false), raf: 0, layer: null, els: new Map() };
(() => {
  const css = document.createElement('style');
  css.textContent = `.think{margin:8px 0 4px;font-size:13px}.think b{font-size:12px;opacity:.75}.think p{margin:2px 0}
    .tbubbles{position:absolute;inset:0;pointer-events:none;overflow:hidden;z-index:1}
    .tbubble{position:absolute;transform:translate(-50%,-100%);max-width:170px;padding:5px 10px;border-radius:999px;background:#F2F6FF;
      color:#1B1340;border:2px solid #1B1340;box-shadow:0 3px 0 #1B1340;font:600 13px/16px system-ui,sans-serif;text-align:center;white-space:normal;transition:opacity .2s}
    .tbubble::after{content:'';position:absolute;left:50%;bottom:-8px;margin-left:-6px;border:6px solid transparent;border-top:8px solid #1B1340;border-bottom:0}`;
  document.head.appendChild(css);
})();
function setThoughts(on) {
  thoughts.on = on; store.set('reeflive.thoughts', on);
  say(on ? 'Look! Now you can see what the fish are thinking.' : 'The fish keep their thoughts to themselves.');
  if (on) startThoughts(); else stopThoughts();
}
function startThoughts() {
  if (thoughts.raf || !thoughts.on) return;
  if (!thoughts.layer) { thoughts.layer = document.createElement('div'); thoughts.layer.className = 'tbubbles'; $('tank').after(thoughts.layer); }
  const frame = () => {
    thoughts.raf = requestAnimationFrame(frame);
    const hide = ui.shelf || ui.cleaning || sub.on || !$('micro').classList.contains('hidden');
    const list = hide ? [] : JSON.parse(SB.bubbles(true));
    const cnv = $('tank'), k = cnv.clientWidth / Math.max(1, cnv.width), seen = new Set();
    for (const b of list) {
      let el = thoughts.els.get(b.id);
      if (!el) { el = document.createElement('div'); el.className = 'tbubble'; thoughts.layer.appendChild(el); thoughts.els.set(b.id, el); }
      if (el.textContent !== b.text) el.textContent = b.text;
      el.style.left = `${Math.max(90, Math.min(cnv.clientWidth - 90, b.x * k))}px`;
      el.style.top = `${Math.max(40, b.y * k - 10)}px`;
      el.style.opacity = b.a.toFixed(2);
      seen.add(b.id);
    }
    for (const [id, el] of thoughts.els) if (!seen.has(id)) { el.remove(); thoughts.els.delete(id); }
  };
  frame();
}
function stopThoughts() {
  cancelAnimationFrame(thoughts.raf); thoughts.raf = 0; SB.bubbles(false);
  for (const el of thoughts.els.values()) el.remove();
  thoughts.els.clear();
}
document.addEventListener('visibilitychange', () => { if (document.hidden) stopThoughts(); else startThoughts(); });
// ================================================================ submarine (Android SubmarineControls)
// Be a little submarine: a stick (or W A S D / arrows) steers, the up and down buttons (or Space / E and Shift / Q)
// dive and climb. The fish see it by species and personality unless it is invisible.
const sub = { on: false, visible: true, light: true, inside: false, joy: [0, 0], rise: 0, keys: new Set(), sent: '' };
function setSub(on) {
  sub.on = on;
  if (on) { closeCard(); if (ui.tour) ui.tour = false; ui.following = null; if (ui.cleaning) setCleaning(false); if (ui.shelf) closeShelf(); }
  document.body.classList.toggle('subbing', on);
  $('subui').classList.toggle('hidden', !on);
  sub.joy = [0, 0]; sub.rise = 0; sub.keys.clear(); sub.sent = '';
  live.submarine(on, sub.visible, sub.light);
  live.subView(sub.inside);
  if (on) live.steerSub(0, 0, 0, 0);
  moveKnob(0, 0);
  renderSub();
  toast(on ? 'Dive dive! Steer with the yellow stick (or W A S D). Swim slowly and the curious fish come to look.' : 'The submarine is back in its dock.');
  if (!on) { renderRail(); renderFollow(); }
}
function renderSub() {
  $('subGhost').classList.toggle('on', !sub.visible);
  $('subGhost').textContent = sub.visible ? '👻 Invisible' : '👻 Invisible: on';
  $('subCam').classList.toggle('on', sub.inside);
  $('subCam').textContent = sub.inside ? '🎥 Outside view' : '🎥 Inside view';
  $('subLight').classList.toggle('on', sub.light);
  $('subLight').textContent = sub.light ? '💡 Light on' : '💡 Light off';
  renderSubInfo();
}
function renderSubInfo() {
  let s = null;
  try { s = JSON.parse(live.subState()); } catch { /* not out yet */ }
  $('subInfo').textContent = !sub.visible ? '👻 The fish cannot see you'
    : s ? `👀 ${s.curious} curious · 💨 ${s.wary} shy` : '🟡 Submarine';
}
const clamp1 = (v) => Math.max(-1, Math.min(1, v));
function steerSub() {
  if (!sub.on) return;
  const k = sub.keys;
  const key = (...names) => (names.some((n) => k.has(n)) ? 1 : 0);
  const thrust = clamp1(-sub.joy[1] + key('w', 'arrowup') - key('s', 'arrowdown'));
  const turn = clamp1(sub.joy[0] + key('d', 'arrowright') - key('a', 'arrowleft'));
  const rise = clamp1(sub.rise + key(' ', 'e') - key('shift', 'q'));
  $('subUp').classList.toggle('on', rise > 0); $('subDown').classList.toggle('on', rise < 0);
  const msg = `${thrust.toFixed(2)},${turn.toFixed(2)},${rise.toFixed(2)}`;
  if (msg === sub.sent) return;
  sub.sent = msg;
  live.steerSub(thrust, turn, rise, 0);
}
function moveKnob(x, y) { $('subKnob').style.transform = `translate(${x * 40}px, ${y * 40}px)`; }
(() => {
  const joy = $('subJoy');
  let id = null;
  const at = (e) => {
    const r = joy.getBoundingClientRect();
    let x = (e.clientX - (r.left + r.width / 2)) / (r.width / 2 - 14), y = (e.clientY - (r.top + r.height / 2)) / (r.height / 2 - 14);
    const l = Math.hypot(x, y); if (l > 1) { x /= l; y /= l; }
    sub.joy = [Math.abs(x) < 0.12 ? 0 : x, Math.abs(y) < 0.12 ? 0 : y];
    moveKnob(x, y); steerSub();
  };
  joy.addEventListener('pointerdown', (e) => { id = e.pointerId; joy.setPointerCapture(id); at(e); e.preventDefault(); });
  joy.addEventListener('pointermove', (e) => { if (e.pointerId === id) at(e); });
  const end = (e) => { if (e.pointerId !== id) return; id = null; sub.joy = [0, 0]; moveKnob(0, 0); steerSub(); };
  joy.addEventListener('pointerup', end); joy.addEventListener('pointercancel', end);
  for (const [btn, v] of [['subUp', 1], ['subDown', -1]]) {
    const b = $(btn);
    b.addEventListener('pointerdown', (e) => { b.setPointerCapture(e.pointerId); sub.rise = v; steerSub(); e.preventDefault(); });
    const stop = () => { sub.rise = 0; steerSub(); };
    b.addEventListener('pointerup', stop); b.addEventListener('pointercancel', stop);
  }
  $('subGhost').addEventListener('click', () => {
    sub.visible = !sub.visible; live.submarine(true, sub.visible, sub.light); renderSub();
    toast(sub.visible ? 'Peek-a-boo! The fish can see you again.' : 'Invisible! Now the fish do not notice you at all.');
  });
  $('subCam').addEventListener('click', () => { sub.inside = !sub.inside; live.subView(sub.inside); renderSub(); });
  $('subLight').addEventListener('click', () => { sub.light = !sub.light; live.submarine(true, sub.visible, sub.light); renderSub(); });
  $('subDone').addEventListener('click', () => setSub(false));
  const KEYS = ['w', 'a', 's', 'd', 'q', 'e', ' ', 'shift', 'arrowup', 'arrowdown', 'arrowleft', 'arrowright'];
  const typing = (e) => /^(INPUT|TEXTAREA|SELECT)$/.test(e.target?.tagName || '');
  addEventListener('keydown', (e) => {
    if (!sub.on || typing(e)) return;
    const k = e.key.toLowerCase();
    if (k === 'escape') { setSub(false); return; }
    if (!KEYS.includes(k)) return;
    e.preventDefault(); sub.keys.add(k); steerSub();
  });
  addEventListener('keyup', (e) => { const k = e.key.toLowerCase(); if (sub.keys.delete(k)) steerSub(); });
  addEventListener('blur', () => { if (sub.keys.size) { sub.keys.clear(); steerSub(); } });
})();

// ================================================================ render all
function renderAll() {
  $('soundBtn').textContent = ui.sound ? '🔊' : '🔇';
  startThoughts();
  $('hermie').classList.toggle('hidden', !ui.hermie);
  live.shelfLocked(SUPPLIES.filter((s) => s.sandbox).map((s) => s.id).join(','));
  renderRail();
}
