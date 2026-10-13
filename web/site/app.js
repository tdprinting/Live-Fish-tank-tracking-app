// ReefLive web: page UI around the shared Kotlin sim + renderer (kotlin/ReefLive-web.mjs).
import { ReefLive } from './kotlin/ReefLive-web.mjs';

const live = ReefLive.getInstance();
window.reeflive = live; // handy from the Safari console
const $ = (id) => document.getElementById(id);
const store = {
  get(key, fallback) { try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* private mode */ } },
};

// ---------------------------------------------------------------- tabs
let started = false;
document.querySelectorAll('.tab').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));
function showTab(name) {
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('.view').forEach((v) => v.classList.toggle('active', v.id === name));
  store.set('reeflive.tab', name);
  if (name === 'live') startLive();
  else renderParams();
  live.setVisible(name === 'live' && !document.hidden);
}

// ---------------------------------------------------------------- live tank
const quality = store.get('reeflive.quality', /iPhone|iPod|Android/.test(navigator.userAgent) ? 'LOW' : 'MEDIUM');

function startLive() {
  if (started) return;
  started = true;
  live.start($('tank'), new URL('.', location.href).href, quality, {
    onStatus: (t) => { if (t) $('loadText').textContent = t; },
    onError: (t) => { $('loadText').textContent = `Sorry, the 3D tank could not start: ${t}`; $('loading').querySelector('.spinner').remove(); },
    onPick: (id) => showPick(id),
    onFollow: (id) => { following = id; updatePick(); },
  }).then(() => {
    $('loading').classList.add('done');
    applyLatestReadings();
    setInterval(updateHud, 500);
    updateHud();
    setTimeout(() => { $('hint').style.opacity = 0; }, 9000);
  }).catch(() => {});
}

document.addEventListener('visibilitychange', () => {
  live.setVisible(!document.hidden && $('live').classList.contains('active'));
});

let lastState = null;
const fmtTime = (sec) => {
  const m = Math.floor((sec / 60) % 1440);
  const h = Math.floor(m / 60), mm = String(m % 60).padStart(2, '0');
  return `${((h + 11) % 12) + 1}:${mm} ${h < 12 ? 'AM' : 'PM'}`;
};
function updateHud() {
  const s = JSON.parse(live.state());
  if (!s) return;
  lastState = s;
  $('hudTime').textContent = fmtTime(s.time);
  $('hudPhase').textContent = `${s.phase}${s.speed !== 1 ? ` · ${s.speed}×` : ''}`;
  const tempF = s.tempC * 9 / 5 + 32;
  const chips = [
    ['Temp', `${tempF.toFixed(1)}°F`, statusOf('temp', tempF)],
    ['Salinity', s.sg.toFixed(3), statusOf('sal', s.sg)],
    ['pH', s.ph.toFixed(2), statusOf('ph', s.ph)],
    ['NO₃', `${s.no3.toFixed(1)}`, statusOf('no3', s.no3)],
    ['PO₄', `${s.po4.toFixed(3)}`, statusOf('po4', s.po4)],
  ];
  if (s.nh3 > 0.02) chips.push(['NH₃', s.nh3.toFixed(2), statusOf('nh3', s.nh3)]);
  $('hudChips').innerHTML = chips.map(([n, v, st]) => `<span class="chip ${st}">${n} ${v}</span>`).join('');
  updatePick();
}

let picked = null, following = null;
function showPick(id) {
  picked = id;
  updatePick();
}
function updatePick() {
  const o = lastState && picked && lastState.organisms.find((x) => x.id === picked);
  $('pick').classList.toggle('hidden', !o);
  if (!o) return;
  $('pickName').textContent = o.name || o.species;
  $('pickSpecies').textContent = o.species;
  $('pickHealth').style.width = `${Math.round(Math.max(0, Math.min(1, o.health)) * 100)}%`;
  $('pickFollow').textContent = following === o.id ? 'Stop following' : 'Follow';
}
$('pickFollow').addEventListener('click', () => {
  following = following === picked ? null : picked;
  live.follow(following);
  updatePick();
});
$('pickClose').addEventListener('click', () => { picked = null; updatePick(); });
$('camReset').addEventListener('click', () => { following = null; live.follow(null); live.resetCamera(); flash($('camReset')); });

function flash(el) { el.classList.add('active'); setTimeout(() => el.classList.remove('active'), 300); }

// ---------------------------------------------------------------- sandbox sheets (every button does something visible)
const sim = { lights: 'auto', pumps: true, speed: 1, temp: null, sg: null, no3: null, po4: null };
const FOODS = [
  ['FLAKE', '🟠', 'Flakes'], ['PELLET', '🟤', 'Pellets'], ['MYSIS', '🦐', 'Mysis'], ['BRINE', '🦐', 'Brine shrimp'],
  ['NORI', '🥬', 'Nori'], ['PODS', '🐛', 'Copepods'], ['PLANKTON', '✨', 'Plankton'], ['PRAWN', '🍤', 'Prawn chunk'],
];
const SHEETS = {
  feed: () => `<h3>Feed the tank</h3><div class="options">${FOODS.map(([k, i, n]) =>
    `<button class="opt" data-feed="${k}"><span class="ico">${i}</span>${n}</button>`).join('')}</div>
    <p class="muted">Tip: long-press in the tank to drop food right there.</p>`,
  light: () => `<h3>Lights</h3><div class="options">
    ${[['auto', '🕒', 'Schedule'], ['on', '☀️', 'On'], ['off', '🌙', 'Off']].map(([k, i, n]) =>
      `<button class="opt ${sim.lights === k ? 'on' : ''}" data-lights="${k}"><span class="ico">${i}</span>${n}</button>`).join('')}
    <button class="opt ${sim.pumps ? 'on' : ''}" data-pumps><span class="ico">🌀</span>Pumps ${sim.pumps ? 'on' : 'off'}</button></div>`,
  water: () => {
    const s = lastState || {};
    const v = (k, d) => sim[k] ?? d;
    return `<h3>Water</h3>
    ${slider('temp', 'Temperature', '°F', 70, 88, 0.5, v('temp', s.tempC ? s.tempC * 9 / 5 + 32 : 77))}
    ${slider('sg', 'Salinity', 'SG', 1.015, 1.035, 0.001, v('sg', s.sg ?? 1.025))}
    ${slider('no3', 'Nitrate', 'ppm', 0, 80, 1, v('no3', s.no3 ?? 5))}
    ${slider('po4', 'Phosphate', 'ppm', 0, 0.5, 0.01, v('po4', s.po4 ?? 0.03))}
    <div class="options"><button class="opt" data-wc="10"><span class="ico">🪣</span>10% water change</button>
    <button class="opt" data-reset-water><span class="ico">↩️</span>Use my readings</button></div>`;
  },
  time: () => `<h3>Speed</h3><div class="options">${[[0, '⏸️', 'Pause'], [1, '▶️', 'Real time'], [10, '⏩', '10×'], [60, '⏭️', '1 min/s'], [600, '🚀', '10 min/s']]
    .map(([k, i, n]) => `<button class="opt ${sim.speed === k ? 'on' : ''}" data-speed="${k}"><span class="ico">${i}</span>${n}</button>`).join('')}</div>
    <h3 style="margin-top:14px">Picture quality</h3><div class="options">${['LOW', 'MEDIUM', 'HIGH']
    .map((q) => `<button class="opt ${quality === q || store.get('reeflive.quality') === q ? 'on' : ''}" data-quality="${q}">${q[0] + q.slice(1).toLowerCase()}</button>`).join('')}</div>`,
  events: () => `<h3>What if…</h3><div class="options">${live.scenarios().split('\n').map((l) => l.split('\t'))
    .map(([k, n]) => `<button class="opt" data-scenario="${k}"><span class="ico">${SCEN_ICONS[k] || '🌊'}</span>${n}</button>`).join('')}</div>
    <p class="muted">These only change the simulation, never your logged readings.</p>`,
};
const SCEN_ICONS = { HEAT_WAVE: '🔥', NITRATE_SPIKE: '🧪', SALINITY_SWING: '🧂', POWER_OUTAGE: '🔌', ALGAE_BLOOM: '🌿', OVERFEEDING: '🍽️' };
function slider(id, label, unit, min, max, step, value) {
  const dec = String(step).split('.')[1]?.length || 0;
  return `<label class="slider"><span>${label}</span><output id="out-${id}">${Number(value).toFixed(dec)} ${unit}</output>
    <input type="range" data-param="${id}" data-unit="${unit}" data-dec="${dec}" min="${min}" max="${max}" step="${step}" value="${value}"></label>`;
}

let openSheet = null;
document.querySelectorAll('.tool[data-sheet]').forEach((b) => b.addEventListener('click', () => {
  const name = b.dataset.sheet;
  document.querySelectorAll('.tool').forEach((t) => t.classList.toggle('active', t === b && openSheet !== name));
  if (openSheet === name) { closeSheet(); return; }
  openSheet = name;
  $('sheet').innerHTML = SHEETS[name]();
  $('sheet').classList.remove('hidden');
}));
function closeSheet() { openSheet = null; $('sheet').classList.add('hidden'); document.querySelectorAll('.tool').forEach((t) => t.classList.remove('active')); }
$('tank').addEventListener('pointerdown', () => { if (openSheet) closeSheet(); });

$('sheet').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  const d = b.dataset;
  if (d.feed) live.feed(d.feed);
  else if (d.lights) { sim.lights = d.lights; live.lights(d.lights); }
  else if ('pumps' in d) { sim.pumps = !sim.pumps; live.pumps(sim.pumps); }
  else if (d.wc) live.waterChange(Number(d.wc));
  else if ('resetWater' in d) { Object.assign(sim, { temp: null, sg: null, no3: null, po4: null }); applyLatestReadings(true); }
  else if (d.speed) { sim.speed = Number(d.speed); live.setSpeed(sim.speed); }
  else if (d.quality) { store.set('reeflive.quality', d.quality); live.setQuality(d.quality); }
  else if (d.scenario) live.scenario(d.scenario);
  flash(b);
  if (openSheet && !d.feed && !d.scenario && !d.wc) $('sheet').innerHTML = SHEETS[openSheet]();
  setTimeout(updateHud, 120);
});
$('sheet').addEventListener('input', (e) => {
  const r = e.target;
  if (!r.dataset.param) return;
  const v = Number(r.value);
  $(`out-${r.dataset.param}`).textContent = `${v.toFixed(Number(r.dataset.dec))} ${r.dataset.unit}`;
  sim[r.dataset.param] = v;
  if (r.dataset.param === 'temp') live.setParam('temp', (v - 32) * 5 / 9);
  else live.setParam(r.dataset.param, v);
});

// ---------------------------------------------------------------- parameter tracker
const PARAMS = JSON.parse(live.parameters());
const byId = Object.fromEntries(PARAMS.map((p) => [p.id, p]));
const readings = () => store.get('reeflive.readings', []);

function statusOf(id, v) {
  const p = byId[id];
  if (!p || v == null || Number.isNaN(v)) return 'unknown';
  if ((p.dangerLo != null && v <= p.dangerLo) || (p.dangerHi != null && v >= p.dangerHi)) return 'danger';
  if (v >= p.idealLo - 1e-9 && v <= p.idealHi + 1e-9) return 'good';
  if (v >= p.okLo - 1e-9 && v <= p.okHi + 1e-9) return 'caution';
  return 'danger';
}
const STATUS_TEXT = { good: '✓ Ideal', caution: '⚠ Watch', danger: '⛔ Act now', unknown: '? Not logged' };

function latestValues() {
  const out = {};
  for (const r of readings().slice().sort((a, b) => a.t - b.t)) Object.assign(out, r.values);
  return out;
}

/** Sends logged readings to the sim (only changed values; [resend] pushes all of them again). */
function applyLatestReadings(resend) {
  const v = latestValues();
  if (!Object.keys(v).length) return;
  if (resend) live.applyReadings('{}');
  live.applyReadings(JSON.stringify(v));
}

function spark(id) {
  const pts = readings().filter((r) => r.values[id] != null).sort((a, b) => a.t - b.t).slice(-20);
  if (pts.length < 2) return '';
  const vals = pts.map((r) => r.values[id]);
  const p = byId[id];
  const lo = Math.min(...vals, p.idealLo), hi = Math.max(...vals, p.idealHi);
  const y = (v) => 30 - ((v - lo) / ((hi - lo) || 1)) * 26;
  const x = (i) => (i / (pts.length - 1)) * 100;
  const band = `<rect x="0" y="${y(p.idealHi)}" width="100" height="${Math.max(1, y(p.idealLo) - y(p.idealHi))}" fill="rgba(124,242,208,.12)"/>`;
  const line = vals.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join('');
  return `<svg viewBox="0 0 100 34" preserveAspectRatio="none">${band}<path d="${line}" fill="none" stroke="#5CE1E6" stroke-width="1.6" vector-effect="non-scaling-stroke"/></svg>`;
}

function renderParams() {
  const latest = latestValues();
  const shown = PARAMS.filter((p) => p.favorite || latest[p.id] != null);
  $('paramGrid').innerHTML = shown.map((p) => {
    const v = latest[p.id];
    const st = statusOf(p.id, v);
    return `<div class="card"><div class="name"><span>${p.name}</span><span class="status ${st}">${STATUS_TEXT[st]}</span></div>
      <div class="val">${v == null ? '—' : Number(v).toFixed(p.precision)} <small>${p.unit}</small></div>
      <div class="range">Ideal ${p.idealLo === p.idealHi ? p.idealLo : `${p.idealLo}–${p.idealHi}`} ${p.unit}</div>${spark(p.id)}</div>`;
  }).join('');
  const rs = readings().slice().sort((a, b) => b.t - a.t);
  $('history').innerHTML = rs.length ? rs.map((r) => `<div class="entry"><div><strong>${new Date(r.t).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}</strong>
      <div class="vals">${Object.entries(r.values).map(([k, v]) => `${byId[k]?.name ?? k} ${Number(v).toFixed(byId[k]?.precision ?? 2)} ${byId[k]?.unit ?? ''}`).join(' · ')}</div></div>
      <button class="icon-btn" data-del="${r.t}" aria-label="Delete">🗑</button></div>`).join('')
    : '<p class="empty">No tests logged yet. Tap “Log a test” to add your first.</p>';
}
$('history').addEventListener('click', (e) => {
  const t = e.target.closest('[data-del]')?.dataset.del;
  if (!t || !confirm('Delete this test?')) return;
  store.set('reeflive.readings', readings().filter((r) => String(r.t) !== t));
  renderParams();
});

$('logBtn').addEventListener('click', () => {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  $('logWhen').value = now.toISOString().slice(0, 16);
  $('logFields').innerHTML = PARAMS.filter((p) => p.favorite).concat(PARAMS.filter((p) => !p.favorite)).map((p) =>
    `<label class="field">${p.name} (${p.unit})<input type="number" inputmode="decimal" step="any" name="${p.id}" placeholder="${p.idealLo === p.idealHi ? p.idealLo : `${p.idealLo}–${p.idealHi}`}"></label>`).join('');
  $('logDialog').showModal();
});
$('logForm').addEventListener('submit', (e) => {
  if (e.submitter?.value !== 'save') return;
  const values = {};
  $('logFields').querySelectorAll('input').forEach((i) => { if (i.value !== '') values[i.name] = Number(i.value); });
  if (!Object.keys(values).length) return;
  const t = new Date($('logWhen').value).getTime() || Date.now();
  store.set('reeflive.readings', readings().concat([{ t, values }]));
  renderParams();
  applyLatestReadings();
});

$('exportBtn').addEventListener('click', () => {
  const blob = new Blob([JSON.stringify({ app: 'ReefLive', version: 1, readings: readings() }, null, 2)], { type: 'application/json' });
  const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: `reeflive-backup-${new Date().toISOString().slice(0, 10)}.json` });
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
});
$('importFile').addEventListener('change', async (e) => {
  const f = e.target.files[0];
  if (!f) return;
  try {
    const data = JSON.parse(await f.text());
    const incoming = Array.isArray(data.readings) ? data.readings : [];
    const seen = new Set(readings().map((r) => r.t));
    store.set('reeflive.readings', readings().concat(incoming.filter((r) => r && r.t && r.values && !seen.has(r.t))));
    renderParams();
    applyLatestReadings();
  } catch { alert('That file is not a ReefLive backup.'); }
  e.target.value = '';
});

// ---------------------------------------------------------------- install hint + offline
const standalone = window.navigator.standalone || matchMedia('(display-mode: standalone)').matches;
if (/iPhone|iPad|iPod/.test(navigator.userAgent) && !standalone && !store.get('reeflive.installHint', false)) $('install').classList.remove('hidden');
$('installClose').addEventListener('click', () => { $('install').classList.add('hidden'); store.set('reeflive.installHint', true); });
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});

showTab(store.get('reeflive.tab', 'live'));
