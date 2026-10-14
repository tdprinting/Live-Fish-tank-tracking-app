// ReefLive web: page UI around the shared Kotlin sim + renderer (kotlin/ReefLive-web.mjs).
import { ReefLive } from './kotlin/ReefLive-web.mjs';
import { startLive, setLiveVisible } from './live.js';

const live = ReefLive.getInstance();
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
  if (name === 'live') startLive(() => applyLatestReadings());
  else renderParams();
  setLiveVisible(name === 'live' && !document.hidden);
}

document.addEventListener('visibilitychange', () => {
  setLiveVisible(!document.hidden && $('live').classList.contains('active'));
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
