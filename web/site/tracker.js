// ReefLive web: the keeper's tools beside the water tests (research 01 §3.9, §4.2-4.5, §5; coverage_gaps T-1, T-2, T-4):
// the new-tank cycle tracker, the acclimation timer and the pest / algae / disease log. Stage detection and the
// acclimation steps are shared Kotlin (core tracker/, via ReefLive.cycleStatus / acclimationPlan); this file stores,
// draws and times them. Everything stays on this device: localStorage for records, IndexedDB for photos.
import { GLYPHS } from './glyphs.js';

const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const icon = (n) => (GLYPHS[n] ? `<svg class="gi" viewBox="0 0 24 24" aria-hidden="true">${GLYPHS[n]}</svg>` : '');
const DAY = 864e5;
const CYCLE_IDS = ['nh3', 'no2', 'no3'];
const CYCLE_NAMES = { nh3: 'Ammonia', no2: 'Nitrite', no3: 'Nitrate' };
// chart series (dataviz: validated for the dark surface; identity also carried by legend and direct labels)
const SERIES = { nh3: '#C77712', no2: '#9A5CF0', no3: '#1FA6C9' };
const localInput = (t) => { const d = new Date(t); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };
const fmtDay = (t) => new Date(t).toLocaleDateString([], { month: 'short', day: 'numeric' });
const fmtWhen = (t) => new Date(t).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
const fmtClock = (t) => new Date(t).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
const mmss = (ms) => { const s = Math.max(0, Math.ceil(ms / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
const num = (v, p = 2) => (v == null ? '—' : String(parseFloat(Number(v).toFixed(p))));

// ---------------------------------------------------------------- problem catalog (research 01 §4.3-4.5)
// [id, label, usual fixes, sim link]: the link opens the matching sandbox preset on a pretend copy of the tank
export const PROBLEMS = {
  pest: { label: 'Pest', glyph: 'pestAnemone', types: [
    ['aiptasia', 'Aiptasia', "Peppermint shrimp, Berghia nudibranchs, Joe's Juice or Aiptasia-X on each one.", { aip: 3, label: 'Pest anemones' }],
    ['bristle', 'Bristle worms', 'Most are harmless scavengers. Trap or remove the big fireworm types by hand.'],
    ['flatworm', 'Flatworms', 'Siphon them out, then a flatworm treatment with carbon ready. Results vary by product.'],
    ['vermetid', 'Vermetid snails', 'Remove the tubes by hand or cover them with putty. Hard to wipe out.'],
    ['other', 'Other pest', ''],
  ] },
  algae: { label: 'Algae', glyph: 'seaweed', types: [
    ['hair', 'Hair algae', 'Lower phosphate (water changes, GFO), add grazers, raise flow.'],
    ['cyano', 'Cyanobacteria (red slime)', 'More flow, less food, siphon it out, lower nutrients. Antibiotics only as a last resort.', { preset: 'CYANO_OUTBREAK', label: 'Red slime' }],
    ['dinos', 'Dinoflagellates', 'Bring nitrate and phosphate up to target, a 2 to 3 day blackout, more flow.', { preset: 'DINO_OUTBREAK', label: 'Dinoflagellates' }],
    ['diatoms', 'Diatoms (brown)', 'Common in new tanks. Remove silicate, add grazers; often clears by itself.'],
    ['bubble', 'Bubble algae (Valonia)', 'Pull them off whole without bursting them. Emerald crabs help a little.', { preset: 'BUBBLE_ALGAE', label: 'Bubble algae' }],
    ['other', 'Other algae', ''],
  ] },
  disease: { label: 'Disease', glyph: 'sickFish', types: [
    ['ich', 'Marine ich (white spot)', 'Copper or tank transfer in quarantine; the display must run fishless 72 days.', { infect: 'ICH', label: 'Ich' }],
    ['velvet', 'Marine velvet', 'Act fast: copper or formalin in quarantine. Hyposalinity does not work.', { infect: 'VELVET', label: 'Velvet' }],
    ['brook', 'Brooklynella', 'Freshwater dip and formalin baths. Copper usually does not help.', { infect: 'BROOKLYNELLA', label: 'Brooklynella' }],
    ['uronema', 'Uronema', 'Early isolation, then copper or formalin in quarantine.', { infect: 'URONEMA', label: 'Uronema' }],
    ['flukes', 'Flukes', 'Praziquantel in quarantine, repeated per the label.', { infect: 'FLUKES', label: 'Flukes' }],
    ['hlle', 'Head and lateral line erosion', 'Better water and food: vitamins, varied diet, fresh carbon.', { infect: 'HLLE', label: 'HLLE' }],
    ['bacterial', 'Fin rot / bacterial', 'Clean water, quarantine, an antibiotic if it spreads.', { infect: 'BACTERIAL', label: 'Bacterial infection' }],
    ['other', 'Other disease', ''],
  ] },
};
const SEVERITY = [null, ['Mild', 'unknown', '•'], ['Moderate', 'caution', '⚠'], ['Severe', 'danger', '⛔']];
const OUTCOMES = { ongoing: ['Ongoing', 'caution'], improving: ['Improving', 'caution'], resolved: ['Resolved', 'good'], worse: ['Getting worse', 'danger'], lost: ['Animal lost', 'danger'] };
const typeOf = (kind, id) => PROBLEMS[kind]?.types.find((t) => t[0] === id) ?? [id, id, ''];

// ---------------------------------------------------------------- photos (IndexedDB: localStorage is too small)
let dbp = null;
function db() {
  if (!dbp) dbp = new Promise((ok, no) => {
    if (!('indexedDB' in window)) { no(new Error('no IndexedDB')); return; }
    const r = indexedDB.open('reeflive', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('photos');
    r.onsuccess = () => ok(r.result);
    r.onerror = () => no(r.error);
  });
  return dbp;
}
async function idb(mode, fn) {
  const d = await db();
  return new Promise((ok, no) => {
    const tx = d.transaction('photos', mode);
    const req = fn(tx.objectStore('photos'));
    tx.oncomplete = () => ok(req?.result);
    tx.onerror = () => no(tx.error);
  });
}
const photoGet = (id) => idb('readonly', (s) => s.get(id)).catch(() => null);
const photoPut = (id, data) => idb('readwrite', (s) => s.put(data, id));
const photoDel = (id) => idb('readwrite', (s) => s.delete(id)).catch(() => {});
/** A phone photo shrunk to at most 1280 px on the long side, as a JPEG data URL (about 150 to 300 KB). */
async function shrinkPhoto(file) {
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = no; i.src = url; });
    const k = Math.min(1, 1280 / Math.max(img.naturalWidth, img.naturalHeight));
    const c = Object.assign(document.createElement('canvas'), { width: Math.round(img.naturalWidth * k), height: Math.round(img.naturalHeight * k) });
    c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
    return c.toDataURL('image/jpeg', 0.8);
  } finally { URL.revokeObjectURL(url); }
}

// ---------------------------------------------------------------- alerts: sound, buzz, notification
let actx = null;
function unlockAudio() {
  try { actx = actx || new (window.AudioContext || window.webkitAudioContext)(); if (actx.state === 'suspended') actx.resume(); } catch { actx = null; }
}
function chime() {
  if (!actx) return;
  try {
    const t0 = actx.currentTime + 0.02;
    [880, 1175, 1568].forEach((f, i) => {
      const o = actx.createOscillator(), g = actx.createGain();
      o.type = 'sine'; o.frequency.value = f;
      g.gain.setValueAtTime(0.0001, t0 + i * 0.22);
      g.gain.exponentialRampToValueAtTime(0.35, t0 + i * 0.22 + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + i * 0.22 + 0.5);
      o.connect(g).connect(actx.destination);
      o.start(t0 + i * 0.22); o.stop(t0 + i * 0.22 + 0.55);
    });
  } catch { /* audio blocked */ }
}
async function notify(title, body, opts = {}) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return false;
  const o = { body, tag: 'reeflive-accl', renotify: true, icon: 'icons/icon-192.png', badge: 'icons/icon-192.png', vibrate: [200, 100, 200], ...opts };
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) { await reg.showNotification(title, o); return true; }
  } catch { /* fall through */ }
  try { new Notification(title, o); return true; } catch { return false; }
}

// ================================================================ the tracker
/**
 * [live] is ReefLive (Kotlin), [store] the page's localStorage wrapper, [readings] the water tests, [addReading] /
 * [deleteReading] change them (the water tests page and the live tank follow), [onPreview] opens a sandbox link and
 * [openTracker] shows one of these tools.
 */
export function initTracker({ live, store, readings, addReading, deleteReading, onPreview, openTracker }) {
  const $ = (id) => document.getElementById(id);
  let sub = store.get('reeflive.sub', 'tests');
  let filter = 'all';
  const charts = {};

  function showSub(name) {
    sub = ['tests', 'cycle', 'accl', 'log'].includes(name) ? name : 'tests';
    store.set('reeflive.sub', sub);
    document.querySelectorAll('#subtabs [data-sub]').forEach((b) => { b.classList.toggle('on', b.dataset.sub === sub); b.setAttribute('aria-selected', b.dataset.sub === sub); });
    document.querySelectorAll('.subview').forEach((v) => v.classList.toggle('hidden', v.id !== `sub-${sub}`));
    render();
  }
  $('subtabs').addEventListener('click', (e) => { const s = e.target.closest('[data-sub]')?.dataset.sub; if (s) showSub(s); });
  function render() {
    if (sub === 'cycle') renderCycle();
    else if (sub === 'accl') renderAccl();
    else if (sub === 'log') renderLog();
  }

  // ============================================================ 1. new-tank cycle tracker
  const cycle = () => store.get('reeflive.cycle', null);
  const cycleReadings = (c) => readings()
    .filter((r) => r.t >= c.start - DAY && CYCLE_IDS.some((k) => r.values?.[k] != null))
    .sort((a, b) => a.t - b.t);
  const STAGE_TONE = { WAITING: 'unknown', AMMONIA: 'caution', NITRITE: 'caution', CLEARING: 'good', CYCLED: 'good' };
  const STAGE_MARK = { unknown: '?', caution: '⚠', good: '✓' };

  function renderCycle() {
    const c = cycle();
    const el = $('sub-cycle');
    if (!c) {
      el.innerHTML = `<div class="card tcard"><h2>Cycle a new tank</h2>
        <p class="muted">Before fish go in, bacteria have to grow that turn ammonia into nitrite and then nitrate. It takes 4 to 8 weeks, or 2 to 4 with cured live rock or bottled bacteria. Log ammonia, nitrite and nitrate every day or two and ReefLive shows the stage and how long is left.</p>
        <div class="fields one"><label class="field">Cycle started<input type="date" id="cyStart" value="${localInput(Date.now()).slice(0, 10)}"></label>
        <label class="check"><input type="checkbox" id="cyBoost"> Live rock or bottled bacteria</label></div>
        <div class="row-gap"><button class="btn" id="cyBegin">Start tracking</button></div></div>`;
      return;
    }
    const rs = cycleReadings(c);
    const st = JSON.parse(live.cycleStatus(JSON.stringify({ start: c.start, now: Date.now(), boosted: !!c.boosted,
      readings: rs.map((r) => ({ t: r.t, nh3: r.values.nh3 ?? null, no2: r.values.no2 ?? null, no3: r.values.no3 ?? null })) })));
    const tone = STAGE_TONE[st.stage];
    const steps = [['AMMONIA', 'Ammonia spike'], ['NITRITE', 'Nitrite spike'], ['CYCLED', 'Cycled']];
    const at = { WAITING: -1, AMMONIA: 0, NITRITE: 1, CLEARING: 2, CYCLED: 3 }[st.stage];
    const stepper = `<ol class="stepper">${steps.map(([, l], i) => `<li class="${i < at ? 'done' : i === at ? 'now' : ''}"><i>${i < at ? '✓' : i + 1}</i><span>${l}</span></li>`).join('')}</ol>`;
    const big = st.stage === 'CYCLED' ? 'Cycled' : st.stage === 'CLEARING' ? `${st.daysLeft} more ${st.daysLeft === 1 ? 'day' : 'days'} at 0`
      : `About ${st.daysLeft} days to go`;
    const sub2 = st.stage === 'CYCLED' ? `Ready on day ${st.day}` : st.stage === 'CLEARING' ? `Day ${st.zeroDays} of ${st.checkDays} with 0 ammonia and 0 nitrite`
      : `Typical: ${st.daysLeftLo} to ${st.daysLeftHi} days`;
    el.innerHTML = `<div class="card tcard cyhero">
        <div class="row-between"><span class="spill ${tone}">${STAGE_MARK[tone]} ${esc(st.label)}</span><span class="muted">Day ${st.day} · since ${fmtDay(c.start)}</span></div>
        <div class="bignum">${esc(big)}</div><div class="muted">${esc(sub2)}</div>
        ${stepper}
        <p class="advice">${esc(st.advice)}</p>
        <div class="readings">${CYCLE_IDS.map((k) => `<div class="reading"><span>${CYCLE_NAMES[k]}</span><b>${num(st[k])}</b><span>ppm</span></div>`).join('')}</div>
        <div class="row-gap"><button class="btn" id="cyLog">＋ Log a cycle test</button></div>
      </div>
      <h2>Ammonia and nitrite</h2>${chart('cyA', rs, ['nh3', 'no2'], c.start)}
      <h2>Nitrate</h2>${chart('cyN', rs, ['no3'], c.start)}
      <h2>Cycle tests</h2>
      <div class="history">${rs.length ? rs.slice().reverse().map((r) => `<div class="entry"><div><strong>${fmtWhen(r.t)}</strong>
        <div class="vals">${CYCLE_IDS.filter((k) => r.values[k] != null).map((k) => `${CYCLE_NAMES[k]} ${num(r.values[k])} ppm`).join(' · ')}</div></div>
        <button class="icon-btn" data-cydel="${r.t}" aria-label="Delete test">${icon('trash')}</button></div>`).join('')
        : '<p class="empty">No cycle tests yet. Tap “Log a cycle test” after you add the ammonia source.</p>'}</div>
      <p class="muted">Cycle tests are saved with your water tests, so they also show on the Water tests page and in the live tank.</p>
      <div class="row-gap"><label class="check"><input type="checkbox" id="cyBoost2" ${c.boosted ? 'checked' : ''}> Live rock or bottled bacteria</label>
        <button class="btn ghost small" id="cyStop">${st.stage === 'CYCLED' ? 'Finish' : 'Stop tracking'}</button></div>`;
    hookChart('cyA'); hookChart('cyN');
  }

  /** A small line chart of [ids] over time; one y-axis (nitrate gets its own chart). Hover or drag for values. */
  function chart(key, rs, ids, start) {
    const pts = ids.map((k) => rs.filter((r) => r.values[k] != null).map((r) => [r.t, Number(r.values[k])]));
    if (pts.every((p) => !p.length)) return '<p class="empty">Nothing logged yet.</p>';
    const W = 320, H = 150, L = 30, R = 58, T = 10, B = 22;
    const t0 = Math.min(start, ...pts.flat().map((p) => p[0])), t1 = Math.max(Date.now(), ...pts.flat().map((p) => p[0]));
    const vmax = Math.max(...pts.flat().map((p) => p[1]), ids.includes('no3') ? 5 : 1);
    const nice = (v) => { const e = 10 ** Math.floor(Math.log10(v)); return [1, 2, 2.5, 5, 10].map((m) => m * e).find((m) => m >= v); };
    const top = nice(vmax);
    const x = (t) => L + ((t - t0) / ((t1 - t0) || 1)) * (W - L - R), y = (v) => T + (1 - v / top) * (H - T - B);
    const grid = [0, top / 2, top].map((v) => `<line x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}" class="grid"/><text x="${L - 5}" y="${y(v) + 3.5}" class="ax" text-anchor="end">${num(v)}</text>`).join('');
    const xl = `<text x="${L}" y="${H - 6}" class="ax">${fmtDay(t0)}</text><text x="${W - R}" y="${H - 6}" class="ax" text-anchor="end">${fmtDay(t1)}</text>`;
    const lines = ids.map((k, i) => {
      const p = pts[i];
      if (!p.length) return '';
      const d = p.map(([t, v], j) => `${j ? 'L' : 'M'}${x(t).toFixed(1)},${y(v).toFixed(1)}`).join('');
      const [lt, lv] = p[p.length - 1];
      return `<path d="${d}" fill="none" stroke="${SERIES[k]}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`
        + p.map(([t, v]) => `<circle cx="${x(t).toFixed(1)}" cy="${y(v).toFixed(1)}" r="4" fill="${SERIES[k]}" stroke="var(--abyss0)" stroke-width="2"/>`).join('')
        + `<text x="${x(lt) + 7}" y="${y(lv) + 4 + (ids.length > 1 && i ? 11 : 0)}" class="dl">${CYCLE_NAMES[k]}</text>`;
    }).join('');
    charts[key] = { ids, rs: rs.filter((r) => ids.some((k) => r.values[k] != null)), x, W, L, R };
    const legend = ids.length > 1 ? `<div class="legend">${ids.map((k) => `<span><i style="background:${SERIES[k]}"></i>${CYCLE_NAMES[k]}</span>`).join('')}</div>` : '';
    return `${legend}<div class="chart" id="${key}"><svg viewBox="0 0 ${W} ${H}" role="img" aria-label="${ids.map((k) => CYCLE_NAMES[k]).join(' and ')} over time">${grid}${xl}${lines}
      <line class="cross hidden" y1="${T}" y2="${H - B}"/></svg><div class="tip hidden"></div></div>`;
  }
  function hookChart(key) {
    const host = document.getElementById(key), c = charts[key];
    if (!host || !c) return;
    const svg = host.querySelector('svg'), cross = host.querySelector('.cross'), tip = host.querySelector('.tip');
    const show = (e) => {
      const b = svg.getBoundingClientRect();
      const sx = ((e.clientX - b.left) / b.width) * c.W;
      let best = null, bd = Infinity;
      for (const r of c.rs) { const d = Math.abs(c.x(r.t) - sx); if (d < bd) { bd = d; best = r; } }
      if (!best) return;
      const px = c.x(best.t);
      cross.setAttribute('x1', px); cross.setAttribute('x2', px); cross.classList.remove('hidden');
      tip.innerHTML = `<strong>${fmtWhen(best.t)}</strong>${c.ids.filter((k) => best.values[k] != null).map((k) => `<span><i style="background:${SERIES[k]}"></i>${CYCLE_NAMES[k]} ${num(best.values[k])} ppm</span>`).join('')}`;
      tip.classList.remove('hidden');
      const left = (px / c.W) * b.width;
      tip.style.left = `${Math.min(Math.max(0, left - tip.offsetWidth / 2), b.width - tip.offsetWidth)}px`;
    };
    const hide = () => { cross.classList.add('hidden'); tip.classList.add('hidden'); };
    svg.addEventListener('pointermove', show);
    svg.addEventListener('pointerdown', show);
    svg.addEventListener('pointerleave', hide);
  }

  $('sub-cycle').addEventListener('click', (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.id === 'cyBegin') {
      const d = $('cyStart').value ? new Date(`${$('cyStart').value}T00:00`).getTime() : Date.now();
      store.set('reeflive.cycle', { start: Math.min(d, Date.now()), boosted: $('cyBoost').checked });
      renderCycle();
    } else if (b.id === 'cyLog') {
      $('cyWhen').value = localInput(Date.now());
      $('cycleForm').querySelectorAll('input[type=number]').forEach((i) => { i.value = ''; });
      $('cycleDialog').showModal();
    } else if (b.id === 'cyStop') {
      if (!confirm('Stop tracking this cycle? Your tests stay in Water tests.')) return;
      store.set('reeflive.cycle', null);
      renderCycle();
    } else if (b.dataset.cydel) {
      if (!confirm('Delete this test?')) return;
      deleteReading(Number(b.dataset.cydel));
      renderCycle();
    }
  });
  $('sub-cycle').addEventListener('change', (e) => {
    if (e.target.id !== 'cyBoost2') return;
    store.set('reeflive.cycle', { ...cycle(), boosted: e.target.checked });
    renderCycle();
  });
  $('cycleForm').addEventListener('submit', (e) => {
    if (e.submitter?.value !== 'save') return;
    const values = {};
    $('cycleForm').querySelectorAll('input[type=number]').forEach((i) => { if (i.value !== '') values[i.name] = Number(i.value); });
    if (!Object.keys(values).length) return;
    addReading({ t: new Date($('cyWhen').value).getTime() || Date.now(), values });
    renderCycle();
  });

  // ============================================================ 2. acclimation timer
  // State is wall-clock based (step start times), so a throttled or frozen page catches up exactly when it wakes.
  const accl = () => store.get('reeflive.accl', null);
  const saveAccl = (a) => { store.set('reeflive.accl', a); };
  let draft = store.get('reeflive.acclDraft', { name: '', method: 'DRIP', sensitive: false, awake: true });
  let wake = null;
  let swTimerFor = -1;

  const planOf = (d) => JSON.parse(live.acclimationPlan(d.method, !!d.sensitive));
  const remaining = (a, now = Date.now()) => (a.paused != null ? a.paused : a.stepStart + a.steps[a.i].seconds * 1000 - now);
  const timed = (a) => a && a.i < a.steps.length && a.steps[a.i].seconds > 0;

  /** Moves past finished timers. Returns the steps that finished (for the chime / notification). */
  function advance(a, now = Date.now()) {
    const done = [];
    while (timed(a) && a.paused == null && remaining(a, now) <= 0) {
      const end = a.stepStart + a.steps[a.i].seconds * 1000;
      done.push(a.steps[a.i]);
      a.i++;
      a.stepStart = end;            // the next timer starts when this one ended, not when the page woke up
    }
    if (a.i >= a.steps.length) a.finished = a.finished || now;
    return done;
  }
  function next(a) {
    if (a.i >= a.steps.length) return;
    a.i++; a.stepStart = Date.now(); a.paused = null;
    if (a.i >= a.steps.length) a.finished = Date.now();
  }
  const nextLine = (a) => (a.i < a.steps.length ? `Next: ${a.steps[a.i].title}${a.steps[a.i].seconds ? '' : ' (tap Done when finished)'}` : 'All steps done.');

  function tickAccl() {
    const a = accl();
    if (!a || a.finished && a.i >= a.steps.length && a.alerted === a.i) { pill(a); if (wake) letSleep(); return; }
    const done = advance(a);
    if (done.length || (a.i >= a.steps.length && a.alerted !== a.i)) {
      a.alerted = a.i;
      saveAccl(a);
      const last = done[done.length - 1];
      const title = last ? `${last.title}: done` : 'Acclimation done';
      chime();
      try { navigator.vibrate?.([200, 100, 200, 100, 300]); } catch { /* not on iOS */ }
      if (document.hidden) notify(`${a.name ? `${a.name}: ` : ''}${title}`, nextLine(a));
      flash(`${title}. ${nextLine(a)}`);
      if (sub === 'accl') renderAccl();
    }
    scheduleBackground(a);
    pill(a);
    if (sub === 'accl') updateClock(a);
  }
  setInterval(tickAccl, 1000);
  document.addEventListener('visibilitychange', () => { tickAccl(); if (!document.hidden) keepAwake(); });

  /**
   * While the page sleeps its timers stall, so the next step's end is also handed to the service worker (it can keep
   * a short timer alive) and, where the browser has notification triggers, scheduled outright.
   */
  async function scheduleBackground(a) {
    if (!a || !timed(a) || a.paused != null) return;
    if (swTimerFor === a.i * 1e13 + a.stepStart) return;
    swTimerFor = a.i * 1e13 + a.stepStart;
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const at = a.stepStart + a.steps[a.i].seconds * 1000;
    const body = a.i + 1 < a.steps.length ? `Next: ${a.steps[a.i + 1].title}` : 'All steps done.';
    const title = `${a.name ? `${a.name}: ` : ''}${a.steps[a.i].title}: done`;
    try {
      const reg = await navigator.serviceWorker?.getRegistration();
      if (!reg) return;
      if ('showTrigger' in Notification.prototype && 'TimestampTrigger' in window) {
        // eslint-disable-next-line no-undef
        reg.showNotification(title, { body, tag: `reeflive-accl-${a.i}`, showTrigger: new TimestampTrigger(at), icon: 'icons/icon-192.png' }).catch(() => {});
      } else reg.active?.postMessage({ type: 'accl-alarm', at, title, body });
    } catch { /* best effort */ }
  }
  async function cancelBackground() {
    swTimerFor = -1;
    try {
      const reg = await navigator.serviceWorker?.getRegistration();
      reg?.active?.postMessage({ type: 'accl-cancel' });
      (await reg?.getNotifications?.({ includeTriggered: true }))?.filter((n) => n.tag?.startsWith('reeflive-accl')).forEach((n) => n.close());
    } catch { /* best effort */ }
  }
  async function keepAwake() {
    const a = accl();
    if (!a || a.i >= a.steps.length || !a.awake || document.hidden || wake) return;
    try { wake = await navigator.wakeLock?.request('screen'); wake?.addEventListener('release', () => { wake = null; }); } catch { wake = null; }
  }
  function letSleep() { try { wake?.release(); } catch { /* gone */ } wake = null; }

  function flash(text) {
    const f = $('acclFlash');
    f.textContent = text;
    f.classList.remove('hidden');
    clearTimeout(flash.t);
    flash.t = setTimeout(() => f.classList.add('hidden'), 6000);
  }
  /** The floating pill on every other page while an acclimation runs. */
  function pill(a) {
    const p = $('acclPill');
    const here = sub === 'accl' && $('params').classList.contains('active');
    if (!a || a.i >= a.steps.length || here) { p.classList.add('hidden'); return; }
    const s = a.steps[a.i];
    p.innerHTML = `${icon('timelapse')}<span><b>${s.seconds ? `${mmss(remaining(a))} ·` : 'Your turn:'}</b> ${esc(s.title)}</span>`;
    p.classList.toggle('paused', a.paused != null);
    p.classList.remove('hidden');
  }
  $('acclPill').addEventListener('click', () => openTracker('accl'));

  function renderAccl() {
    const el = $('sub-accl');
    const a = accl();
    if (!a) {
      const steps = planOf(draft);
      const mins = Math.round(steps.reduce((s, x) => s + x.seconds, 0) / 60);
      el.innerHTML = `<div class="card tcard"><h2>Acclimate a new arrival</h2>
        <p class="muted">Step by step with timers, a chime and a notification when each step ends. Float the bag for temperature, then drip or add tank water slowly so the animal gets used to your water.</p>
        <div class="fields one"><label class="field">Who is arriving? (optional)<input type="text" id="acName" maxlength="40" placeholder="e.g. Cleaner shrimp" value="${esc(draft.name)}"></label></div>
        <div class="pills" role="radiogroup" aria-label="Method">${[['DRIP', 'Drip'], ['FLOAT', 'Float and add water']].map(([m, l]) => `<button class="pillbtn ${draft.method === m ? 'on' : ''}" data-method="${m}" role="radio" aria-checked="${draft.method === m}">${l}</button>`).join('')}</div>
        <label class="check"><input type="checkbox" id="acSens" ${draft.sensitive ? 'checked' : ''}> Sensitive animal (shrimp, starfish, urchin): go slower</label>
        <label class="check"><input type="checkbox" id="acAwake" ${draft.awake ? 'checked' : ''}> Keep the screen on while it runs</label>
        <ol class="plan">${steps.map((s) => `<li><b>${esc(s.title)}</b><span>${s.seconds ? `${s.seconds / 60} min` : 'by hand'}</span></li>`).join('')}</ol>
        <p class="muted">About ${mins} minutes of timers.</p>
        <div class="row-gap"><button class="btn" id="acGo">Start</button></div></div>`;
      return;
    }
    const s = a.steps[a.i];
    const doneAll = a.i >= a.steps.length;
    const endAt = (() => { let t = a.paused != null ? Date.now() + a.paused : a.stepStart; for (let j = a.i; j < a.steps.length; j++) t += j === a.i && a.paused != null ? 0 : a.steps[j].seconds * 1000; return t; })();
    el.innerHTML = `<div class="card tcard acnow">
        <div class="row-between"><span class="muted">${esc(a.name || 'New arrival')} · ${a.method === 'DRIP' ? 'Drip' : 'Float'}${a.sensitive ? ' · slow' : ''}</span>
          <span class="muted">${doneAll ? `Done ${fmtClock(a.finished)}` : `Step ${a.i + 1} of ${a.steps.length}`}</span></div>
        ${doneAll ? `<div class="bignum">All done</div><p class="advice">Welcome home${a.name ? `, ${esc(a.name)}` : ''}. Keep the lights low for the rest of the day and watch it at the next feeding.</p>
          <div class="row-gap"><button class="btn" id="acClear">Finish</button><button class="btn ghost" id="acToLog">Note a problem</button></div>`
        : `<h2>${esc(s.title)}</h2><p class="advice">${esc(s.detail)}</p>
          ${s.seconds ? `<div class="clock" id="acClock">${mmss(remaining(a))}</div><div class="lvbar"><i id="acBar"></i></div>
            <div class="row-gap"><button class="btn ghost" id="acPause">${a.paused != null ? 'Resume' : 'Pause'}</button><button class="btn ghost" id="acPlus">+1 min</button><button class="btn ghost" id="acSkip">Skip</button></div>`
          : '<div class="row-gap"><button class="btn" id="acDone">Done, next step</button></div>'}
          <p class="muted">Finishes around ${fmtClock(endAt)}${a.paused != null ? ' (paused)' : ''}.</p>`}
      </div>
      <ol class="plan live">${a.steps.map((x, j) => `<li class="${j < a.i ? 'done' : j === a.i ? 'now' : ''}"><b>${j < a.i ? '✓ ' : ''}${esc(x.title)}</b><span>${x.seconds ? `${x.seconds / 60} min` : 'by hand'}</span></li>`).join('')}</ol>
      ${doneAll ? '' : `<p class="muted">${notifyHint()}</p><div class="row-gap"><button class="btn ghost small" id="acStop">Stop</button></div>`}`;
    updateClock(a);
  }
  function notifyHint() {
    if (!('Notification' in window)) return 'This browser cannot show notifications, so keep ReefLive open: the chime plays when a step ends.';
    if (Notification.permission === 'denied') return 'Notifications are blocked for this site, so keep ReefLive open to hear the chime.';
    return 'If you switch apps, a notification says when a step ends; some phones pause web pages when the screen locks, and ReefLive catches up as soon as you come back.';
  }
  function updateClock(a) {
    const c = $('acClock'), bar = $('acBar');
    if (!c || !timed(a)) return;
    const rem = remaining(a);
    c.textContent = mmss(rem);
    if (bar) bar.style.width = `${Math.min(100, Math.max(0, 100 - (rem / (a.steps[a.i].seconds * 1000)) * 100)).toFixed(1)}%`;
  }

  $('sub-accl').addEventListener('click', async (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    const a = accl();
    if (b.dataset.method) { draft.method = b.dataset.method; store.set('reeflive.acclDraft', draft); renderAccl(); return; }
    if (b.id === 'acGo') {
      unlockAudio();
      draft.name = $('acName').value.trim();
      store.set('reeflive.acclDraft', draft);
      if ('Notification' in window && Notification.permission === 'default') { try { await Notification.requestPermission(); } catch { /* old Safari */ } }
      saveAccl({ name: draft.name, method: draft.method, sensitive: draft.sensitive, awake: draft.awake, steps: planOf(draft), i: 0, stepStart: Date.now(), paused: null, started: Date.now(), alerted: -1 });
      keepAwake();
    } else if (!a) return;
    else if (b.id === 'acDone') { unlockAudio(); next(a); a.alerted = a.i; saveAccl(a); }
    else if (b.id === 'acSkip') { next(a); a.alerted = a.i; saveAccl(a); cancelBackground(); }
    else if (b.id === 'acPlus') { if (a.paused != null) a.paused += 60000; else a.stepStart += 60000; saveAccl(a); cancelBackground(); }
    else if (b.id === 'acPause') {
      if (a.paused != null) { a.stepStart = Date.now() + a.paused - a.steps[a.i].seconds * 1000; a.paused = null; } else { a.paused = remaining(a); cancelBackground(); }
      saveAccl(a);
    } else if (b.id === 'acStop') { if (!confirm('Stop this acclimation?')) return; saveAccl(null); cancelBackground(); letSleep(); }
    else if (b.id === 'acClear') { saveAccl(null); letSleep(); }
    else if (b.id === 'acToLog') { saveAccl(null); letSleep(); showSub('log'); openProblem(null, { where: a.name }); return; }
    renderAccl(); pill(accl());
  });
  $('sub-accl').addEventListener('change', (e) => {
    if (e.target.id === 'acSens') draft.sensitive = e.target.checked;
    else if (e.target.id === 'acAwake') draft.awake = e.target.checked;
    else return;
    store.set('reeflive.acclDraft', draft);
    renderAccl();
  });
  $('sub-accl').addEventListener('input', (e) => { if (e.target.id === 'acName') { draft.name = e.target.value; store.set('reeflive.acclDraft', draft); } });

  // ============================================================ 3. pest / algae / disease log
  const problems = () => store.get('reeflive.problems', []);
  const saveProblems = (list) => store.set('reeflive.problems', list);
  let editing = null;          // the entry in the dialog (null = new)
  let pendingPhoto;            // undefined: unchanged; null: removed; string: a new data URL

  function renderLog() {
    const all = problems().slice().sort((a, b) => b.t - a.t);
    const list = filter === 'all' ? all : all.filter((p) => p.kind === filter);
    const open = all.filter((p) => !['resolved', 'lost'].includes(p.outcome)).length;
    let month = '';
    const rows = list.map((p) => {
      const [, label, , link] = typeOf(p.kind, p.type);
      const m = new Date(p.t).toLocaleDateString([], { month: 'long', year: 'numeric' });
      const head = m !== month ? `<li class="tl-month">${esc(m)}</li>` : '';
      month = m;
      const sev = SEVERITY[p.severity] ?? SEVERITY[1], out = OUTCOMES[p.outcome] ?? OUTCOMES.ongoing;
      return `${head}<li class="tl-item k-${p.kind}"><i class="tl-dot">${icon(PROBLEMS[p.kind]?.glyph)}</i><div class="card tl-card">
        <div class="row-between"><strong>${esc(p.kind === 'other' || p.type === 'other' ? (p.name || label) : label)}</strong><span class="muted">${fmtDay(p.t)}</span></div>
        <div class="pills"><span class="spill ${sev[1]}">${sev[2]} ${sev[0]}</span><span class="spill ${out[1]}">${out[1] === 'good' ? '✓' : out[1] === 'danger' ? '⛔' : '⚠'} ${out[0]}</span>${p.where ? `<span class="muted">${esc(p.where)}</span>` : ''}</div>
        ${p.photo ? `<button class="thumb" data-photo="${p.photo}" aria-label="Open photo"><img alt="Photo of ${esc(label)}"></button>` : ''}
        ${p.notes ? `<p>${esc(p.notes)}</p>` : ''}
        ${p.treatment ? `<p><span class="muted">Treatment:</span> ${esc(p.treatment)}</p>` : ''}
        <div class="row-gap"><button class="btn ghost small" data-edit="${p.id}">Update</button>${link ? `<button class="btn ghost small" data-sim="${p.kind}:${p.type}">${icon('whatIf')} See it in the sandbox</button>` : ''}</div>
      </div></li>`;
    }).join('');
    $('sub-log').innerHTML = `<div class="row-between"><p class="muted">${all.length ? `${open} open · ${all.length - open} closed` : 'Pests, algae and sick fish, with dates, photos and what you tried.'}</p>
        <button class="btn" id="pbAdd">＋ Log a problem</button></div>
      <div class="pills" role="tablist">${[['all', 'All'], ['pest', 'Pests'], ['algae', 'Algae'], ['disease', 'Disease']].map(([k, l]) => `<button class="pillbtn ${filter === k ? 'on' : ''}" data-filter="${k}">${l}</button>`).join('')}</div>
      ${list.length ? `<ol class="timeline">${rows}</ol>` : `<p class="empty">${all.length ? 'Nothing in this group.' : 'Nothing logged yet. Tap “Log a problem” when a pest, an algae or a sick fish shows up.'}</p>`}`;
    $('sub-log').querySelectorAll('[data-photo]').forEach(async (b) => { const d = await photoGet(b.dataset.photo); if (d) b.querySelector('img').src = d; else b.remove(); });
  }

  function typeOptions(kind, sel) {
    return PROBLEMS[kind].types.map(([id, l]) => `<option value="${id}" ${id === sel ? 'selected' : ''}>${esc(l)}</option>`).join('');
  }
  function fixHint() {
    const [, , fix, link] = typeOf($('pbKind').value, $('pbType').value);
    $('pbFix').innerHTML = fix ? `<b>Usual fixes:</b> ${esc(fix)}` : '';
    $('pbFix').classList.toggle('hidden', !fix);
    $('pbOther').classList.toggle('hidden', $('pbType').value !== 'other');
    $('pbSim').classList.toggle('hidden', !link);
  }
  async function openProblem(p, preset = {}) {
    editing = p;
    pendingPhoto = undefined;
    const kind = p?.kind ?? preset.kind ?? (filter !== 'all' ? filter : 'algae');
    $('pbTitle').textContent = p ? 'Update problem' : 'Log a problem';
    $('pbWhen').value = localInput(p?.t ?? Date.now());
    $('pbKind').value = kind;
    $('pbType').innerHTML = typeOptions(kind, p?.type);
    $('pbName').value = p?.name ?? '';
    $('pbSev').value = String(p?.severity ?? 2);
    $('pbWhere').value = p?.where ?? preset.where ?? '';
    $('pbNotes').value = p?.notes ?? '';
    $('pbTreat').value = p?.treatment ?? '';
    $('pbOut').value = p?.outcome ?? 'ongoing';
    $('pbFile').value = '';
    $('pbDel').classList.toggle('hidden', !p);
    $('pbPhoto').innerHTML = '';
    if (p?.photo) { const d = await photoGet(p.photo); if (d) showPhoto(d); }
    fixHint();
    $('problemDialog').showModal();
  }
  function showPhoto(d) {
    $('pbPhoto').innerHTML = d ? `<img src="${d}" alt="Photo"><button type="button" class="btn ghost small" id="pbNoPhoto">Remove photo</button>` : '';
  }
  $('pbKind').addEventListener('change', () => { $('pbType').innerHTML = typeOptions($('pbKind').value); fixHint(); });
  $('pbType').addEventListener('change', fixHint);
  $('pbFile').addEventListener('change', async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try { pendingPhoto = await shrinkPhoto(f); showPhoto(pendingPhoto); } catch { alert('That picture could not be read.'); }
  });
  $('pbPhoto').addEventListener('click', (e) => { if (e.target.id === 'pbNoPhoto') { pendingPhoto = null; showPhoto(null); } });
  $('pbSim').addEventListener('click', () => { const l = typeOf($('pbKind').value, $('pbType').value)[3]; if (l) { $('problemDialog').close(); onPreview(l); } });
  $('problemForm').addEventListener('submit', async (e) => {
    const v = e.submitter?.value;
    if (v === 'delete') {
      if (!editing || !confirm('Delete this entry?')) { e.preventDefault(); return; }
      if (editing.photo) photoDel(editing.photo);
      saveProblems(problems().filter((p) => p.id !== editing.id));
      renderLog();
      return;
    }
    if (v !== 'save') return;
    const id = editing?.id ?? `p${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
    let photo = editing?.photo ?? null;
    if (pendingPhoto !== undefined) {
      if (photo) photoDel(photo);
      photo = null;
      if (pendingPhoto) {
        const pid = `ph-${id}-${Date.now().toString(36)}`;
        try { await photoPut(pid, pendingPhoto); photo = pid; } catch { alert('The photo could not be saved on this device (storage full?). The entry is saved without it.'); }
      }
    }
    const entry = { id, t: new Date($('pbWhen').value).getTime() || Date.now(), kind: $('pbKind').value, type: $('pbType').value,
      name: $('pbType').value === 'other' ? $('pbName').value.trim() : '', severity: Number($('pbSev').value), where: $('pbWhere').value.trim(),
      notes: $('pbNotes').value.trim(), treatment: $('pbTreat').value.trim(), outcome: $('pbOut').value, updated: Date.now() };
    if (photo) entry.photo = photo;
    saveProblems(problems().filter((p) => p.id !== id).concat([entry]));
    try { navigator.storage?.persist?.(); } catch { /* not offered */ }
    renderLog();
  });
  $('sub-log').addEventListener('click', async (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.id === 'pbAdd') openProblem(null);
    else if (b.dataset.filter) { filter = b.dataset.filter; renderLog(); }
    else if (b.dataset.edit) openProblem(problems().find((p) => p.id === b.dataset.edit));
    else if (b.dataset.sim) { const [k, t] = b.dataset.sim.split(':'); const l = typeOf(k, t)[3]; if (l) onPreview(l); }
    else if (b.dataset.photo) {
      const d = await photoGet(b.dataset.photo);
      if (!d) return;
      $('photoView').querySelector('img').src = d;
      $('photoView').showModal();
    }
  });

  // ============================================================ backup
  /** Everything the tracker keeps, for the page's Export backup (photos inline as data URLs). */
  async function backup() {
    const list = problems();
    const photos = {};
    for (const p of list) if (p.photo) { const d = await photoGet(p.photo); if (d) photos[p.photo] = d; }
    return { cycle: cycle(), problems: list, photos };
  }
  async function restore(data) {
    if (data.cycle && !cycle()) store.set('reeflive.cycle', data.cycle);
    if (Array.isArray(data.problems)) {
      const have = new Map(problems().map((p) => [p.id, p]));
      for (const p of data.problems) if (p && p.id && p.kind && (!have.has(p.id) || (p.updated ?? 0) > (have.get(p.id).updated ?? 0))) have.set(p.id, p);
      saveProblems([...have.values()]);
    }
    for (const [id, d] of Object.entries(data.photos || {})) if (typeof d === 'string' && d.startsWith('data:image/')) { try { await photoPut(id, d); } catch { /* storage full */ } }
    render();
  }

  tickAccl();
  keepAwake();
  return { showSub, render, backup, restore, sub: () => sub };
}
