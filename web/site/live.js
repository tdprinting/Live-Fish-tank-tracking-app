// ReefLive web: the live tank screen, mirroring the Android live screen (LiveTankScreen + LiveMenu + SupplyShelf +
// MicroscopeView + TimelapseSheet). The sim, renderer and the shared tables come from Kotlin (kotlin/ReefLive-web.mjs):
// ReefLive drives the 3D view, ReefSandbox is the Android view model's actions and readings.
import { ReefLive, ReefSandbox, ReefMyTank } from './kotlin/ReefLive-web.mjs';
import { initBags, pickBag } from './bags.js';
import { initCorals } from './corals.js';
import { GLYPHS } from './glyphs.js';
import { playFlush } from './flush.js';
import { initCare } from './care.js';
import { initStores, streetHtml } from './stores.js';
import { initRoom, roomHtml, roomClick, endTry, roomNav } from './room.js';
import { initRadio, musicBarHtml, musicSync, radioBuyOffer, radioDropOffer, radioKick, radioOffer, radioOn, radioPick, radioShelfHtml, radioSong, radioStep, radioToggle, radioVolume } from './radio.js';

const live = ReefLive.getInstance();
const SB = ReefSandbox.getInstance();
const MY = ReefMyTank.getInstance();
window.reeflive = live; window.reefsandbox = SB; // handy from the Safari console
const $ = (id) => document.getElementById(id);
const store = {
  get(key, fallback) { try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* private mode */ } },
};
/** The Picture setting for the tank: a preset, or AUTO with the level the last visit settled on. */
// A first visit on a phone or tablet (touch screen) starts Auto on Medium: High's fill rate is what made phones slow,
// and Auto still climbs to High when frames run fast at full resolution.
const firstAutoLevel = () => {
  try { return window.matchMedia('(pointer: coarse)').matches ? 'MEDIUM' : 'HIGH'; } catch { return 'HIGH'; }
};
const pictureArg = () => (ui.quality === 'AUTO' ? `AUTO:${store.get('reeflive.autoLevel', firstAutoLevel())}` : ui.quality);
const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

let C = null;              // catalog (static tables)
let P = null;              // panel (current values, readouts, happiness)
let animals = [];          // living animals (cards)
const ui = {
  mode: 'watch', watchFast: false, speed: 1, card: null, more: false, tab: 'WATER', controlMode: 'SET',
  picked: null, following: null, tour: false, shelf: false, shelfRow: 0, cleaning: false, chromeHidden: false,
  torch: false, viewLight: 'REAL', // Picture: AUTO (follows the device) or a preset picked by hand. A new key: the old one also held automatic drops
  quality: store.get('reeflive.picture', 'AUTO'),
  sound: store.get('reeflive.sound', false), volume: store.get('reeflive.volume', 0.6),
  water: store.get('reeflive.water', false), hermie: store.get('reeflive.hermie', true), happy: store.get('reeflive.happy', true),
  rocks: store.get('reeflive.rocksDynamic', true),
  addCount: 1, addSize: 'JUVENILE', addGroup: 'FISH',
};
let onReadingsNeeded = () => {};
/** Every lever works: My tank (the keeper's own saved tank). Watch's fast-forward copy keeps Watch's menu. */
const editable = () => ui.mode !== 'watch';
let statsEl = null;
function statsBox() {
  if (!statsEl) {
    statsEl = document.createElement('pre');
    statsEl.style.cssText = 'position:fixed;left:8px;top:calc(env(safe-area-inset-top) + 8px);z-index:9999;margin:0;' +
      'padding:6px 8px;border-radius:8px;background:rgba(0,0,0,.6);color:#9ef;font:11px/1.35 ui-monospace,monospace;pointer-events:none';
    document.body.appendChild(statsEl);
  }
  return statsEl;
}

// ================================================================ start
let started = false;
export function startLive(applyReadings) {
  onReadingsNeeded = applyReadings;
  if (started) return;
  started = true;
  // My tank's sand dollars: the chip first in the top bar's right side, the Care card it opens
  window.reefcare = initCare({ my: MY, isMine: () => ui.mode === 'mine', say, cue: (n) => cue(n), host: document.querySelector('.topright') || $('statusBtn').parentElement });
  // the fish stores: walking a store's display tanks replaces the tank in the 3D view until you leave (stores.js)
  window.reefstores = initStores({ my: MY, say, cue: (n) => cue(n), onLeave: () => { renderRail(); stockChanged(); } });
  // the room shop: stands for the tank, tried on in 3D before buying (room.js)
  initRoom({ my: MY, live, say, cue: (n) => cue(n), onChange: () => renderCard(), onTurn: () => { ui.picked = null; ui.following = null; live.follow(null); renderAll(); } });
  live.start($('tank'), new URL('.', location.href).href, pictureArg(), {
    onStatus: (t) => { if (t) $('loadText').textContent = t; },
    // Auto settled on a cheaper (or climbed back to a richer) preset: start from it next visit
    onQuality: (q) => store.set('reeflive.autoLevel', q),
    onError: (t) => { $('loadText').textContent = `Sorry, the 3D tank could not start: ${t}`; $('loading').querySelector('.spinner')?.remove(); },
    // every touch answers (UI_STYLE §10.8): a ring glints around the animal in the tank, plus a tiny buzz here
    onPick: (id) => { if (id) buzz(5); if (window.reefstores?.active()) { window.reefstores.pick(id); return; } if (sub.on) { if (id) subWatch(id); return; } if (id) select(id); else if (!ui.following) select(null); },
    onFollow: (id) => { ui.following = id; if (id) ui.picked = id; renderFish(); renderFollow(); },
    // pressed and held right on an animal: ride along with it
    onHold: (id) => { buzz(18); const a = animals.find((x) => x.id === id); say(a ? `Riding along with ${a.name}! Back to tank to stop.` : 'Riding along!'); },
    onFed: () => { cue('PELLET'); say('Yum! Food drops right where you pressed.'); },
    onKnock: () => say('Knock knock! Who comes to look?'),
    onTouch: (surface) => touchTank(surface),
    // a fingertip held on the glass (UI_STYLE §10.8): slide it and fish follow; flick it as it lifts for a puff of current
    onFinger: (surface) => { if (surface) { try { navigator.vibrate?.(6); } catch { /* not on iOS */ } fingerSay(); } },
    onFlick: () => say('Whoosh! A puff of current.'),
    onScrape: (cm) => scrapeSound(cm),
    onFlipperTap: () => setCleaning(true),
    onShelfPick: (id) => useSupply(id),
    // a tap on another display tank or the counter in a store's 3D aisle
    onStoreStand: (i) => window.reefstores?.walk(i),
    onStoreCounter: () => window.reefstores?.counter(),
    onStoreTrade: () => window.reefstores?.trade(),
    // the music corner left of the tank: the record stand and the radio (radio.js)
    onMusicView: (on) => { ui.music = on; if (!on) radioDropOffer(); renderMusic(); if (on) say('The music corner! Tap a record to play it, or turn the radio knob.'); },
    onMusicRecord: (i) => { buzz(5); radioKick(); radioOffer(i); },
    onMusicVolume: (v) => radioVolume(v.volume, v.done),
    onMusicTune: () => { radioKick(); radioStep(1); },
    onMusicPower: () => { radioKick(); radioToggle(); },
    // touch controls: food from the shelf lands where the tank is tapped; a tap on gear pops out its menu
    onAim: (h) => aimFed(h),
    onSpot: (t) => openSpot(t),
    onFixed: (kind) => { say(FIX_SAY[kind] || 'Fixed!'); setTimeout(tick, 150); if (ui.card === 'HEALTH') renderCard(); },
    onPestTreated: (r) => { say(r > 0 ? 'Got it! Watch it shrink away.' : r === 0 ? "It ducked into the rock just in time! Try again when it's open." : 'No pest anemones right now. Nothing to treat!'); setTimeout(tick, 150); },
    onFixedAll: () => { toast('All fixed! The tank check is happy.'); if (ui.card === 'HEALTH') renderCard(); },
    // a rock settled or toppled after the sand under it was dug out: remember where it lies now
    onRocks: (state) => { store.set('reeflive.rocks', state); say('Crunch! A rock shifted. The gobies dug out too much sand under it.'); },
    // open the page with ?stats to show frame rate and timings (for checking speed on a real phone)
    ...(new URLSearchParams(location.search).has('stats') ? { onStats: (t) => statsBox().textContent = t } : {}),
    // the aquascape builder added or took out a rock: keep the new rockwork
    onRockscape: (state) => store.set('reeflive.rocks', state),
  }).then(() => {
    live.restoreRocks(store.get('reeflive.rocks', ''));
    live.setDynamicRocks(ui.rocks);
    C = JSON.parse(SB.catalog());
    initBags({ live, SB, say, cue, onCancel: () => { ui.card = 'LIFE'; renderAll(); } });
    initRadio({
      SB, live, store, ctx: audioCtx, say, changed: () => renderMusic(),
      records: () => (ui.mode === 'mine' ? JSON.parse(MY.records()) : []),
      buy: (file) => { const ok = ui.mode === 'mine' && MY.buyRecord(file); if (ok) MY.save(); return ok; },
      coins: () => (ui.mode === 'mine' ? JSON.parse(MY.economy())?.coins ?? null : null),
    });
    initCorals({ live, SB, say, cue, isMine: () => ui.mode === 'mine', onShow: () => { if (ui.card) closeCard(); } });
    $('loading').classList.add('done');
    applyReadings();
    renderAll();
    setInterval(tick, 500);
    tick();
    say(C.tips.hello);
    // the page opens on My tank, the main tank (Watch is the real tank as logged, one tap away)
    setMode('mine');
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
  ui.clockAt = s;
  if (s) $('clock').textContent = `${PHASE_ICON[s.phase] ?? ''} ${hhmm(s.time)}${P.speed !== 1 ? ` · ${P.speed === 0 ? 'paused' : `${P.speed}×`}` : ''}`;
  ui.worst = P.readouts.some((r) => r.status === 'DANGER') ? 'danger' : P.readouts.some((r) => r.status === 'CAUTION') ? 'caution' : 'good';
  $('waterDot').className = `warn ${ui.worst}${ui.worst === 'good' ? ' hidden' : ''}`;
  $('happyFace').textContent = faceFor(P.happy);
  $('happyFace').classList.toggle('hidden', !ui.happy);
  if (ui.card === 'HEALTH') refreshHealth();
  renderFish();
  renderFollow();
  roomNav(ui.mode === 'mine' && !ui.card && !ui.following && !window.reefstores?.active());
  if (ui.card && document.activeElement?.type !== 'range' && !ui.dragging) refreshCardValues();
  updateSound();
  if (ui.tour && !ui.saver && ++ui.tourTicks >= 24) nextTour();
  if (!$('micro').classList.contains('hidden')) drawMicro();
  if (sub.on) renderSubInfo();
  checkFlush();
}
// the fish net lifted a body out (core FishNet): the toilet goodbye, once per body (flush.js)
let flushSeen;
function checkFlush() {
  const f = JSON.parse(live.netFlush());
  const key = f ? f.key : null;
  if (flushSeen === undefined) { flushSeen = key; return; }   // never replay one from before the page looked
  if (!f || key === flushSeen) return;
  flushSeen = key;
  playFlush(f, { onSplash: () => cue('FEED', 1), onFlush: () => cue('FLUSH', 0.9, 0.7, 1.7) });
}
const faceFor = (h) => (h >= 80 ? '😄' : h >= 60 ? '🙂' : h >= 40 ? '😐' : h >= 20 ? '😟' : '😫');
const PHASE_ICON = { Night: '🌙', Dawn: '🌅', Morning: '☀️', Day: '☀️', Midday: '☀️', Afternoon: '☀️', Dusk: '🌇', Evening: '🌇' };
const hhmm = (sec) => { const t = Math.max(0, Math.floor(sec)); return `${String(Math.floor((t % 86400) / 3600)).padStart(2, '0')}:${String(Math.floor((t % 3600) / 60)).padStart(2, '0')}`; };
const clockText = (sec) => {
  const t = Math.max(0, Math.floor(sec));
  const d = Math.floor(t / 86400) + 1, h = Math.floor((t % 86400) / 3600), m = Math.floor((t % 3600) / 60);
  return `Day ${d} · ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
};

// ================================================================ Hermie, toasts
let hermieTimer = 0, tipIndex = 0;
function buzz(ms) { try { navigator.vibrate?.(ms); } catch { /* not on iOS */ } }
function say(text) {
  if (!ui.hermie) { toast(text); return; }
  $('hermieText').textContent = text;
  $('hermie').classList.add('talk');
  clearTimeout(hermieTimer);
  hermieTimer = setTimeout(() => $('hermie').classList.remove('talk'), 6000);
}
function nextTip() {
  const list = ui.mode === 'mine' ? C.tips.hermieMyTank : editable() ? C.tips.hermieSandbox : C.tips.hermieWatch;
  say(list[tipIndex++ % list.length]);
  cue('TICK');
}
$('hermie').addEventListener('click', nextTip);
$('statusBtn').addEventListener('click', () => {
  cue('TICK');
  if (ui.shelf) closeShelf();
  if (ui.card === 'HEALTH') { closeCard(); return; }
  ui.card = 'HEALTH'; ui.more = false;
  renderCard(); renderRail();
});
let toastTimer = 0;
function toast(text) {
  $('toast').textContent = text;
  $('toast').classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $('toast').classList.add('hidden'), 3500);
}
function locked() { say('That only works in My tank. Tap My tank at the top to try it.'); cue('TICK'); }

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
  try { news = await (await fetch('whats-new.json', { cache: 'no-store' })).json(); } catch { news = []; }
  if (!news.length) { if (!firstVisit) showFact(); return; }
  // remembered by PR number, not "newest number seen": updates merge out of number order (#111 after #131), so
  // "everything above the last number" kept popping the same notes up on every visit
  const seenList = store.get('reeflive.newsSeenIds', null);
  const oldLast = store.get('reeflive.newsSeen', 0);
  store.set('reeflive.newsSeenIds', news.map((e) => e.n));
  if (firstVisit || (!seenList && oldLast)) return; // brand new (the tips come first), or saw the popup before this fix
  const seen = new Set(seenList || []);
  const fresh = seenList ? news.filter((e) => !seen.has(e.n)) : news.slice(0, 8);
  if (fresh.length) { setTimeout(() => showNews(fresh, true), 1500); return; }
  showFact();
}
/** No new updates: a reef fact or reef-keeping tip instead, a new one every time the app opens (core ReefFacts). */
function showFact() {
  const n = store.get('reeflive.factN', Math.floor(Math.random() * 1000));
  store.set('reeflive.factN', n + 1);
  const f = JSON.parse(SB.fact(n));
  const show = () => {
    // the "While you were away" card (care.js) goes first: the fact waits until it is closed
    const away = $('awayModal');
    if (away && !away.classList.contains('hidden')) { setTimeout(show, 1000); return; }
    $('newsTitle').textContent = `🐠 ${f.title}`;
    $('newsList').innerHTML = `<li>${esc(f.text)}</li>`;
    const d = $('news');
    if (!d.open) d.showModal();
  };
  setTimeout(show, 1500);
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
  const sb = editable(), mine = ui.mode === 'mine';
  $('tipsTitle').textContent = mine ? C.tips.myTankTitle : sb ? C.tips.sandboxTitle : C.tips.watchTitle;
  $('tipsList').innerHTML = (mine ? C.tips.myTank : sb ? C.tips.sandbox : C.tips.watch).map((t) => `<li>${esc(t)}</li>`).join('');
  $('tips').showModal();
}

// ================================================================ mode
document.querySelectorAll('.seg [data-mode]').forEach((b) => { b.insertAdjacentHTML('afterbegin', icon(b.dataset.ico)); b.addEventListener('click', () => setMode(b.dataset.mode)); });
function setMode(m) {
  if (m === ui.mode && !ui.watchFast) return;
  ui.watchFast = false;
  if (window.reefstores?.active()) window.reefstores.leave(true);
  endTry(true);
  ui.mode = m;
  SB.setMode(m);
  ui.picked = null; ui.following = null; live.follow(null); renderFish();
  // My tank keeps the speed it was left at; the other two start at real time
  if (m === 'mine') ui.speed = JSON.parse(SB.panel() ?? 'null')?.speed ?? 1;
  else { ui.speed = 1; live.setSpeed(1); }
  live.shelfLocked(shelfLockIds());
  document.querySelectorAll('.seg [data-mode]').forEach((b) => b.classList.toggle('on', b.dataset.mode === m));
  closeCard();
  if (m === 'mine') {
    const info = JSON.parse(MY.info());
    if (!info.exists) showMyTankSetup();
    else say(`${info.name}: your very own ${info.build}. Everything you do here is saved.`);
  } else {
    if ($('mytank').open) $('mytank').close();
    say('Back to your real tank. Just watch and feed.');
  }
  musicSync();
  renderRail();
  tick();
}

// ================================================================ My tank: pick a tank, saved in this browser
/**
 * A little drawing of a tank build, every build at the same scale so the sizes compare at a glance: the glass box in
 * a three-quarter view, water, sand, the black rim (none on a rimless tank) and the stand.
 */
function tankSketch(b) {
  const k = 0.72, sk = 0.42;                      // px per cm; how much of the depth shows on the slant
  const L = b.l * k, H = b.h * k, D = b.d * k * sk, W = (b.h - 2.5) * k, S = 4.5 * k;
  const vw = 121.9 * k + 61 * k * sk + 4, vh = 61 * k + 61 * k * sk + 12;
  const x0 = 2 + (vw - 4 - L - D) / 2, y0 = vh - 9;  // front-bottom-left corner, above the stand
  const p = (pts) => pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const top = y0 - H, wy = y0 - W;
  const rim = b.rimless ? '' : `<path d="M${x0} ${top}h${L}l${D} ${-D}h${-L}z" fill="none" stroke="#111" stroke-width="2.2"/>
    <path d="M${x0} ${y0}h${L}" stroke="#111" stroke-width="2.2"/>`;
  return `<svg class="tank-sketch" viewBox="0 0 ${vw.toFixed(0)} ${vh.toFixed(0)}" aria-hidden="true">
    <rect x="${x0 - 2}" y="${y0 + 1}" width="${L + D + 4}" height="7" rx="1.5" fill="#6b4a2e"/>
    <polygon points="${p([[x0 + D, wy - D], [x0 + L + D, wy - D], [x0 + L + D, y0 - D], [x0 + L, y0], [x0, y0], [x0, wy]])}" fill="#2a8fc4" opacity=".35"/>
    <polygon points="${p([[x0, y0 - S], [x0 + L, y0 - S], [x0 + L + D, y0 - S - D], [x0 + L + D, y0 - D], [x0 + L, y0], [x0, y0]])}" fill="#e8dcc0"/>
    <polygon points="${p([[x0, wy], [x0 + L, wy], [x0 + L + D, wy - D], [x0 + D, wy - D]])}" fill="#7fd0ff" opacity=".45"/>
    <path d="M${x0} ${top}h${L}v${H}h${-L}z M${x0 + L} ${top}l${D} ${-D}v${H}l${-D} ${D} M${x0} ${top}l${D} ${-D}h${L}" fill="none" stroke="#bfe8ff" stroke-width="1" opacity=".9"/>
    ${rim}
  </svg>`;
}

function showMyTankSetup() {
  const builds = JSON.parse(MY.builds());
  $('mytankList').innerHTML = builds.map((b) => `<button class="opt build" data-build="${b.id}">${tankSketch(b)}<strong>${icon('tank')} ${esc(b.label)}</strong>
    <small>${b.gallons} gal · ${esc(b.size)}${b.rimless ? ' · rimless' : ''}</small><span>${esc(b.blurb)}</span></button>`).join('');
  const d = $('mytank');
  if (!d.open) d.showModal();
}
$('mytankList').addEventListener('click', (e) => {
  const id = e.target.closest('[data-build]')?.dataset.build;
  if (!id) return;
  cue('TICK');
  if (!MY.start(id)) return;
  $('mytank').close();
  // ask the browser to keep this site's storage (best effort; an installed home-screen app keeps it anyway)
  try { navigator.storage?.persist?.(); } catch { /* not offered */ }
  say('A brand new tank! Tap Shop to visit the fish stores and bring home your first animals.');
  ui.card = 'LIFE'; renderCard(); renderRail(); tick();
});
$('mytankClose').addEventListener('click', () => { $('mytank').close(); if (ui.mode === 'mine' && !JSON.parse(MY.info()).exists) setMode('watch'); });
// saved every 30 s while on screen, and whenever the page goes away
setInterval(() => { if (ui.mode === 'mine') MY.save(); }, 30000);
document.addEventListener('visibilitychange', () => { if (document.hidden) MY.hidden(); else MY.visible(); });
window.addEventListener('pagehide', () => MY.hidden());

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
// candy tones from the Android live theme (UI_STYLE §10.1). A few big bubbles a child can read: Look, Supplies (the 3D
// shelf), Tank Health and, in My tank, Shop and Tank; Settings is the gear at the top (Android LiveMenu)
const CATS = {
  LOOK: { ico: 'eye', label: 'Look', tone: 'cyan' },
  SUPPLIES: { ico: 'basket', label: 'Supplies', tone: 'orange' },
  HEALTH: { ico: 'heart', label: 'Tank Health', tone: 'green' },
  LIFE: { ico: 'shop', label: 'Shop', tone: 'pink' },
  TANK: { ico: 'bulb', label: 'Tank', tone: 'yellow' },
  SETTINGS: { ico: 'dots', label: 'Settings & help', tone: 'white' },
};
function renderRail() {
  const cats = editable() ? ['LOOK', 'SUPPLIES', 'HEALTH', 'LIFE', 'TANK'] : ['LOOK', 'SUPPLIES', 'HEALTH'];
  $('rail').innerHTML = cats.map((c) => `<button class="tool tone-${CATS[c].tone} ${ui.card === c || (c === 'SUPPLIES' && ui.shelf) ? 'active' : ''}" data-cat="${c}"><span class="ico">${icon(CATS[c].ico)}</span><span class="lbl">${CATS[c].label}</span></button>`).join('');
  $('setBtn').classList.toggle('on', ui.card === 'SETTINGS');
}
function openCat(c) {
  cue('TICK');
  if (c === 'SUPPLIES') { ui.shelf ? closeShelf() : openShelf(); return; }
  if (ui.shelf) closeShelf();
  if (ui.card === c) { closeCard(); return; }
  ui.card = c; ui.more = false;
  renderCard();
  renderRail();
}
$('rail').addEventListener('click', (e) => { const c = e.target.closest('[data-cat]')?.dataset.cat; if (c) openCat(c); });
$('setBtn').insertAdjacentHTML('afterbegin', icon('dots'));
$('setBtn').addEventListener('click', () => openCat('SETTINGS'));
function closeCard() { ui.card = null; $('card').classList.add('hidden'); renderRail(); }
$('tank').addEventListener('pointerdown', () => { if (ui.card) { closeCard(); ui.cardClosedAt = performance.now(); } });

/** An icon by name: a ReefGlyphs glyph, `food:KIND` (FoodArt picture), `species:id:fallbackGlyph` (species art), else text. */
function icon(name) {
  if (name.startsWith('food:')) return `<img class="art" src="food/${name.slice(5)}.png" alt="">`;
  if (name.startsWith('species:')) {
    const [, id, fb] = name.split(':');
    return `<img class="art sp" src="species/${id}.webp" alt="" onerror="this.nextElementSibling.hidden=false;this.remove()"><span hidden>${icon(fb)}</span>`;
  }
  const g = GLYPHS[name];
  return g ? `<svg class="gi${name === 'powerheadRight' ? ' flip' : ''}" viewBox="0 0 24 24" aria-hidden="true">${g}</svg>` : esc(name);
}
const tile = (attrs, ico, label, on = false, lock = false, badge = '') =>
  `<button class="opt ${on ? 'on' : ''} ${lock ? 'lock' : ''}" ${attrs}><span class="ico">${icon(ico)}</span>${esc(label)}${badge}</button>`;
const val = (id) => P?.values?.[id]?.v;
const isOn = (id) => (val(id) ?? 0) >= 0.5;

function renderCard() {
  if (!ui.card) return;
  // opening the tank check is testing the water (My tank's daily job)
  if (ui.card === 'FIX' || ui.card === 'HEALTH') MY.testedWater();
  const sb = editable();
  let h = '';
  switch (ui.card) {
    case 'LOOK': h = lookCard(sb); break;
    case 'HEALTH': h = healthCard(); break;
    case 'LIFE': h = lifeCard(sb); break;
    case 'TANK': h = tankCard(sb); break;
    case 'SETTINGS': h = settingsCard(sb); break;
  }
  $('card').className = `sheet tone-${CATS[ui.card].tone}`;
  $('card').innerHTML = `<div class="card-head"><strong><span class="hico">${icon(CATS[ui.card].ico)}</span>${CATS[ui.card].label}</strong>
    <span>${['LIFE', 'TANK'].includes(ui.card) ? `<button class="pillbtn" data-more>${ui.more ? 'Less' : 'More'}</button>` : ''}
    <button class="icon-btn" data-close aria-label="Close">✕</button></span></div><div class="card-body">${h}</div>`;
  $('card').classList.remove('hidden');
  if (ui.card === 'HEALTH') refreshHealth();
}

// ---- Look: time, follow a fish, tour, sub, microscope (Watch adds the lamp looks: they only change the picture)
function timeSlider(stops) {
  const i = Math.max(0, stops.indexOf(ui.speed));
  return `<label class="slider"><span>Time</span><output>${ui.speed === 0 ? 'Paused' : ui.speed === 1 ? 'Real time' : `${ui.speed}× faster`}</output>
    <input type="range" data-time min="0" max="${stops.length - 1}" step="1" value="${i}" data-stops="${stops.join(',')}"></label>`;
}
const STOPS = [1, 10, 60, 240, 600, 1000];
// My tank: how fast slow things (growing, spreading, babies) happen; animals still move at real speed
function paceTiles() {
  const ps = JSON.parse(MY.paces());
  const ico = { REAL: 'clockSun', FAST: 'seaweed', FASTER: 'timelapse' };
  return `<h4>Growing speed</h4><div class="options">${ps.map((p) => tile(`data-pace="${p.id}" title="${esc(p.blurb)}"`, ico[p.id], p.label, p.on)).join('')}</div>`;
}
function lookCard(sb) {
  const looks = [['REAL', 'clockSun', 'Real clock'], ['DAY', 'sun', 'Day'], ['DUSK', 'led', 'Dusk'], ['NIGHT', 'moon', 'Night'], ['MOON', 'moon', 'Moonlight'], ['BLUE', 'uvLamp', 'Blue light']];
  const fish = animals.slice(0, 12);
  return `${timeSlider(sb ? [0, ...STOPS] : STOPS)}${ui.mode === 'mine' ? paceTiles() : ''}
    ${fish.length ? `<h4>Follow a fish</h4><div class="options">${fish.map((a) => tile(`data-follow="${a.id}"`, 'fish', a.name, ui.following === a.id)).join('')}</div>` : ''}
    <h4>Look around</h4><div class="options">${tile('data-tour', 'camera', ui.tour ? 'Stop tour' : 'Auto tour', ui.tour)}${tile('data-sub', 'sub', 'Submarine', sub.on)}
      ${tile('data-microscope', 'microscope', 'Look closer')}${tile('data-lapse', 'timelapse', 'Time-lapse')}${tile('data-torch', 'torch', 'Torch', ui.torch)}
      ${tile('data-saver', 'moon', 'Screensaver')}${tile('data-hide', 'eyeOff', 'Hide buttons')}</div>
    ${sb ? '' : `<h4>Lamp look</h4><div class="options">${looks.map(([k, i, n]) => tile(`data-look="${k}"`, i, n, ui.viewLight === k)).join('')}</div>`}`;
}

// ---- Tank Health: the water, who is hungry (core HungryFeed) and the tank check's fixes (core TankDoctor)
const FIX_ICON = { EMPTY_SKIMMER: 'skimmer', SWAP_SOCK: 'sock', FRESH_CARBON: 'carbon', WATER_CHANGE: 'bucket', TOP_OFF: 'droplet', LOWER_SALT: 'dropMinus', RAISE_SALT: 'dropPlus',
  DOSE_MG: 'bottle', DOSE_CA: 'bottle', DOSE_ALK: 'bottle', HEATER: 'flame', PUMPS: 'powerhead', FEED_CORALS: 'feedTin' };
function healthCard() {
  return `<div id="healthLive">${healthLive()}</div>
    <div class="options"><button class="opt wide" id="feedHungry" data-feedhungry><span class="ico">${icon('feedTin')}</span><span class="lbl"></span></button></div>
    <div class="hunger" id="hungerRows"></div>
    <div id="coralLive"></div>
    <div class="options"><button class="opt wide" id="reefCare" data-reefcare><span class="ico">${icon('branch')}</span><span class="lbl"></span></button></div>
    <div class="hunger" id="coralRows"></div>
    <h4>Water &amp; gear</h4>${fixList()}
    <div class="row-gap"><button class="btn tests" data-tab="params">${icon('flask')} Log a water test</button><button class="btn ghost" data-tip>${icon('crab')} Give me a tip</button></div>`;
}
/** Happy face, water and the hunger header: no buttons inside, so the tick can rebuild it under a finger. */
function healthLive() {
  if (!P) return '';
  const st = { GOOD: 'good', CAUTION: 'caution', DANGER: 'danger' };
  const s = ui.clockAt;
  const words = ui.worst === 'good' ? 'The water is just right.' : ui.worst === 'caution' ? 'Something in the water needs a look.' : 'The water needs help now!';
  ui.hungerRows = JSON.parse(SB.hunger());
  const hungry = ui.hungerRows.filter((r) => r.hungry).length;
  return `<div class="hero"><span class="big">${faceFor(P.happy)}</span><div class="grow">
      <div class="hero-t">Tank happy <b>${P.happy}%</b></div><i class="meter wide"><i class="${P.happy >= 70 ? 'good' : P.happy >= 40 ? 'caution' : 'danger'}" style="width:${P.happy}%"></i></i>
      <div class="muted">${s ? `${clockText(s.time)} · ${esc(s.phase)}` : ''}</div></div></div>
    <h4>Water <span class="note ${ui.worst}">${words}</span></h4>
    <div class="readings">${P.readouts.map((r) => `<div class="reading ${st[r.status] ?? 'unknown'}"><span>${esc(r.label)}</span><b>${esc(r.text)}</b></div>`).join('')}</div>
    <h4>Hungry tummies <span class="note ${hungry ? 'caution' : 'good'}">${hungry ? `${hungry} hungry` : ui.hungerRows.length ? 'Everyone is full' : 'No animals yet'}</span></h4>`;
}
/** Refreshes the live parts of Tank Health: the header, the feed button's words and every hunger bar. */
function refreshHealth() {
  const el = $('healthLive');
  if (!el) return;
  el.innerHTML = healthLive();
  const rows = ui.hungerRows || [];
  const hungry = rows.filter((r) => r.hungry).length;
  const b = $('feedHungry');
  b.querySelector('.lbl').textContent = hungry ? (hungry === 1 ? 'Feed the hungry one' : `Feed all ${hungry} hungry ones`) : 'Nobody is hungry right now';
  b.classList.toggle('on', hungry > 0); b.classList.toggle('lock', !hungry);
  refreshCorals();
  $('hungerRows').innerHTML = rows.map((r) => `<div class="stat ${r.hungry ? 'hungry' : ''}"><span>${esc(r.name)}${r.food ? ` <small class="muted">likes ${esc(r.food.toLowerCase())}</small>` : ''}</span>
    <i class="meter"><i class="hunger" style="width:${Math.round(Math.max(0, Math.min(1, r.hunger)) * 100)}%"></i></i></div>`).join('');
}
/** Corals & inverts: how each coral is doing (core CoralHealth) and the one-tap care button (core CoralCare). */
function refreshCorals() {
  const c = JSON.parse(SB.corals() || '{}');
  ui.coralCare = c;
  const list = c.corals || [];
  const pale = list.filter((k) => k.pale).length;
  const sick = list.filter((k) => !k.pale && k.health < 0.7).length;
  const note = !list.length ? 'No corals yet' : pale ? `${pale} turning pale` : sick ? `${sick} not happy` : 'All doing well';
  $('coralLive').innerHTML = `<h4>Corals &amp; inverts <span class="note ${pale ? 'danger' : sick ? 'caution' : 'good'}">${note}</span></h4>`;
  const b = $('reefCare');
  b.querySelector('.lbl').textContent = c.words ? `Give corals and inverts what they need (${c.price} sand dollars)` : 'Corals and inverts have all they need';
  b.classList.toggle('on', !!c.words); b.classList.toggle('lock', !c.words);
  $('coralRows').innerHTML = list.map((k) => `<div class="stat ${k.health < 0.7 ? 'hungry' : ''}"><span>${esc(k.name)}${k.need ? ` <small class="muted">${esc(k.need)}</small>` : ''}</span>
    <i class="meter"><i class="${k.health >= 0.7 ? 'good' : k.health >= 0.4 ? 'caution' : 'danger'}" style="width:${Math.round(Math.max(0, Math.min(1, k.health)) * 100)}%"></i></i></div>`).join('');
}
function fixList() {
  const f = JSON.parse(SB.fixes());
  if (!f) return '';
  if (!f.list.length) {
    return f.busy ? '<p><strong>Fixing...</strong></p><p class="muted">Watch the tank: the fixes are going in one by one.</p>'
      : '<p><strong>Your tank looks great!</strong></p><p class="muted">The water and the gear are all in good shape. Check again after a few days.</p>';
  }
  return `<p class="muted">${f.list.length === 1 ? 'The tank needs one thing.' : `The tank needs ${f.list.length} things. Fix them one by one or all at once.`}</p>
    ${f.list.length > 1 ? `<div class="options">${tile('data-fixall', 'waveSparkle', `Fix everything (${f.list.length})`)}</div>` : ''}
    <div class="fixes">${f.list.map((x, i) => `<div class="fix ${x.urgent ? 'urgent' : ''}"><span class="ico">${icon(FIX_ICON[x.kind] || 'waveSparkle')}</span>
      <span class="txt"><strong>${esc(x.title)}</strong><span class="muted">${esc(x.why)}</span></span>
      <button class="pillbtn on" data-fix="${i}">Fix</button></div>`).join('')}</div>`;
}
const FIX_SAY = { EMPTY_SKIMMER: "Eww, skimmer goo! Cup's clean now.", SWAP_SOCK: 'Stinky old sock out, fresh one in!', FRESH_CARBON: 'Fresh carbon for sparkly water.',
  WATER_CHANGE: 'Fresh salt water! The fish love it.', TOP_OFF: 'Topped off with fresh water.', LOWER_SALT: 'A little less salty now.', RAISE_SALT: 'A little saltier now.',
  DOSE_MG: 'Coral vitamins in! Just the right amount.', DOSE_CA: 'Coral vitamins in! Just the right amount.', DOSE_ALK: 'Coral vitamins in! Just the right amount.',
  HEATER: 'New heater on. Nice and warm again.', PUMPS: 'Whoosh! The water is moving again.', FEED_CORALS: 'Yum, coral food!' };

// ---- Shop (My tank's fish stores)
function lifeCard(sb) {
  // My tank buys its animals at the fish stores (docs/ECONOMY.md §5); the free gallery stays in the Sandbox
  if (ui.mode === 'mine') {
    return `${streetHtml()}
    ${roomHtml()}
    <h4>Helpers</h4><div class="options">
      ${tile('data-micro="DOSE_TISBE"', 'food:PODS', 'Pods', false, !sb)}${tile('data-micro="DOSE_PHYTO"', 'food:PLANKTON', 'Phyto', false, !sb)}
      ${tile('data-shells', 'shell', 'Spare shells', false, !sb)}</div>`;
  }
  const groups = [['FISH', 'fish', 'Fish'], ['INVERTS', 'shrimp', 'Inverts'], ['CORALS', 'branch', 'Corals']];
  const list = C.stock.filter((s) => s.group === ui.addGroup);
  let h = `<div class="pills">${groups.map(([g, i, n]) => `<button class="pillbtn ${ui.addGroup === g ? 'on' : ''}" data-group="${g}">${icon(i)} ${n}</button>`).join('')}</div>
    <p class="muted">Pick how many, then tap an animal. It comes in a bag: drag the bag onto the tank and it floats until they are ready.</p>
    <div class="pills"><span class="muted">How many</span>${[1, 2, 3, 5, 10].map((n) => `<button class="pillbtn ${ui.addCount === n ? 'on' : ''}" data-count="${n}">${n}</button>`).join('')}</div>
    ${ui.addGroup !== 'CORALS' ? `<div class="pills"><span class="muted">Size</span>${C.sizes.map((z) => `<button class="pillbtn ${ui.addSize === z.id ? 'on' : ''}" data-size="${z.id}">${esc(z.label)}</button>`).join('')}</div>` : ''}
    <div class="options">${list.map((s) => tile(`data-add="${s.id}"`, `species:${s.species || s.id}:${STOCK_ICON[s.group]}`, s.name, false, !sb)).join('')}</div>
    <h4>Helpers</h4><div class="options">
      ${tile('data-micro="DOSE_TISBE"', 'food:PODS', 'Pods', false, !sb)}${tile('data-micro="DOSE_PHYTO"', 'food:PLANKTON', 'Phyto', false, !sb)}
      ${tile('data-pest="PEPPERMINT_SHRIMP"', 'shrimp', 'Peppermint shrimp', false, !sb)}${tile('data-pest="BERGHIA"', 'slug', 'Berghia slugs', false, !sb)}
      ${tile('data-shells', 'shell', 'Spare shells', false, !sb)}</div>`;
  if (ui.more) {
    h += `<h4>More tiny life</h4><div class="options">${C.micro.filter((m) => !m.id.startsWith('TREAT') && m.id !== 'FLATWORM_FRAG').map((m) => tile(`data-micro="${m.id}"`, 'microscope', m.label, false, !sb)).join('')}</div>
      <p class="muted">Tap an animal in the tank to resize it or take it out.</p>`;
  }
  return h;
}
const STOCK_ICON = { FISH: 'fish', INVERTS: 'shrimp', CORALS: 'branch' };

// ---- Tank
function tankCard(sb) {
  const t = val('temp'), sg = val('sal');
  let h = `<div class="options">
    ${tile('data-nudge="temp:1"', 'flame', `Warmer${t != null ? ` (${t.toFixed(1)}°C)` : ''}`, false, !sb)}${tile('data-nudge="temp:-1"', 'snowflake', 'Cooler', false, !sb)}
    ${tile('data-nudge="sal:0.002"', 'salt', `Saltier${sg != null ? ` (${sg.toFixed(3)})` : ''}`, false, !sb)}${tile('data-nudge="sal:-0.002"', 'dropMinus', 'Less salty', false, !sb)}
    ${tile('data-lights="on"', 'sun', 'Lights on', false, !sb)}${tile('data-lights="off"', 'moon', 'Lights off', false, !sb)}${tile('data-lights="auto"', 'clockSun', 'Normal day', false, !sb)}
    ${tile('data-flip="pump_left"', 'powerhead', `Left pump ${isOn('pump_left') ? 'on' : 'off'}`, isOn('pump_left'), !sb)}
    ${tile('data-flip="pump_right"', 'powerheadRight', `Right pump ${isOn('pump_right') ? 'on' : 'off'}`, isOn('pump_right'), !sb)}
    ${tile('data-flip="eq_skimmer"', 'skimmer', 'Skimmer', isOn('eq_skimmer'), !sb)}${tile('data-flip="eq_sock"', 'sock', 'Filter sock', isOn('eq_sock'), !sb)}
    ${tile('data-flip="eq_carbon"', 'carbon', 'Carbon', isOn('eq_carbon'), !sb)}${tile('data-flip="eq_refugium"', 'refugium', 'Algae garden', isOn('eq_refugium'), !sb)}
    ${tile('data-flip="eq_uv"', 'uvLamp', 'Germ zapper', isOn('eq_uv'), !sb)}${tile('data-opentreat', 'bottle', 'Medicine', false, !sb)}
    ${tile('data-scape', 'rocks', 'Build rockwork', false, !sb)}
  </div>`;
  if (ui.more || ui.treat) h += tankMore(sb) + troubleMore(sb);
  else h += '<p class="muted">More has the exact sliders, medicine and trouble you can cause on purpose.</p>';
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
      ${[['alk', 1, 'Alk +1'], ['ca', 20, 'Ca +20'], ['mg', 50, 'Mg +50'], ['no3', 5, 'NO3 +5'], ['po4', 0.05, 'PO4 +0.05']].map(([id, d, n]) => tile(`data-dose="${id}:${d}"`, 'syringe', n, false, !sb)).join('')}</div>
    <h4>Medicine</h4><div class="options">${C.treatments.map((t) => tile(`data-treat="${t.id}:1"`, 'bottle', `Start ${t.label}`, false, !sb) + tile(`data-treat="${t.id}:0"`, 'stop', `Stop ${t.label}`, false, !sb)).join('')}</div>
    <h4>Other levers</h4><div class="options">
      ${tile('data-predation', P?.predation ? '🦈' : '🕊', P?.predation ? 'Fish can eat fish' : 'No eating', P?.predation, !sb)}
      ${tile('data-acclimate', 'heart', 'Acclimate all', false, !sb)}${tile('data-rocks', 'rocks', 'Rearrange rock', false, !sb)}</div>`;
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

// ---- Tank → More: trouble on purpose (it was its own bubble)
function troubleMore(sb) {
  let h = `<h4>Make trouble on purpose</h4><p class="muted">${ui.mode === 'mine' ? 'This is your saved tank, so trouble here sticks. Tank Health can fix most of it.' : 'Break something, then speed up time to see what happens.'}</p><div class="options">
    ${tile('data-heater-act="break"', 'brokenHeater', 'Heater breaks', false, !sb)}${tile('data-preset="HEATER_STUCK"', 'flame', 'Heater stuck on', false, !sb)}
    ${tile('data-pumpfail', 'powerhead', 'Pumps stop tonight', false, !sb)}${tile('data-preset="POWER_OUTAGE"', 'plugOff', 'Power cut', false, !sb)}
    ${tile('data-heatwave', 'heatWave', 'Heat wave', false, !sb)}${tile('data-preset="SALINITY_CRASH"', 'salt', 'Salt crash', false, !sb)}
    ${tile('data-preset="COPPER_OVERDOSE"', 'copperBottle', 'Copper spill', false, !sb)}${tile('data-preset="OVERFEEDING"', 'plate', 'Too much food', false, !sb)}${tile('data-preset="ROCKSLIDE"', 'rocks', 'Rockslide', false, !sb)}
    ${tile('data-preset="MISSED_WATER_CHANGES"', 'calendarX', 'No water changes', false, !sb)}${tile('data-aip="1"', 'pestAnemone', 'Pest anemone', false, !sb)}
    ${tile('data-aip="-1"', 'zap', `Zap pest${P?.aiptasia ? ` (${P.aiptasia})` : ''}`, false, !sb)}${tile('data-sick', 'sickFish', 'A fish gets sick', false, !sb)}
  </div>`;
  if (ui.sickPick) h += `<h4>Which illness?</h4><div class="options">${C.diseases.map((d) => tile(`data-infect="${d.id}"`, 'sickFish', d.label, false, !sb)).join('')}</div>`;
  h += `<h4>What-if presets</h4><div class="presets">${C.presets.map((p) => `<button class="preset ${sb ? '' : 'lock'}" data-preset="${p.id}"><strong>${esc(p.label)}</strong><span>${esc(p.blurb)}</span><em>${esc(p.hint)}</em></button>`).join('')}</div>
    <h4>Scenarios</h4><div class="options">${C.scenarios.map((s) => tile(`data-scenario="${s.id}"`, 'waveSparkle', s.label, false, !sb)).join('')}</div>
    <p class="muted">Slow vs sudden: fish cope with a slow change far better than a jump. "Ramp over a day" above makes any slider change slowly.</p>`;
  return h;
}

// ---- Settings & help (the gear at the top)
function settingsCard(sb) {
  const row = (attr, ico, label, on) => `<button class="toggle-row" ${attr}><span class="rlbl">${icon(ico)} ${esc(label)}</span><span class="switch ${on ? 'on' : ''}"><i></i></span></button>`;
  return `<div class="rows">
      ${row('data-sound', ui.sound ? 'speaker' : 'speakerOff', 'Sound', ui.sound)}
      ${row('data-radio', 'radio', radioOn() ? `Radio: ${radioSong().title}` : 'Radio (quiet tunes)', radioOn())}
      ${radioOn() ? `<div class="toggle-row"><span class="rlbl muted">${esc(radioSong().blurb)}</span><span class="pills"><button class="pillbtn" data-radiostep="-1" aria-label="Last song">${icon('skipBack')} Back</button><button class="pillbtn" data-radiostep="1" aria-label="Next song">Next ${icon('skipNext')}</button></span></div>` : ''}
      <div class="toggle-row"><span class="rlbl">${icon('vinyl')} Record shelf</span><span class="pills"><button class="pillbtn" data-musiccorner>See the stand</button><button class="pillbtn ${ui.records ? 'on' : ''}" data-records>${ui.records ? 'Hide' : 'Open'}</button></span></div>
      ${ui.records ? radioShelfHtml(esc) : ''}
      <label class="slider"><span>Volume</span><output>${Math.round(ui.volume * 100)}%</output><input type="range" data-volume min="0" max="1" step="0.05" value="${ui.volume}"></label>
      ${row('data-hermie-toggle', 'crab', 'Hermie the helper', ui.hermie)}${row('data-happy-toggle', 'heart', 'Happy face', ui.happy)}
      ${row('data-thoughts-toggle', 'whatIf', 'Thought bubbles', thoughts.on)}
      ${row('data-rocks-toggle', 'rocks', 'Rocks can shift', ui.rocks)}
      <div class="toggle-row"><span class="rlbl">${icon('sparkle')} Picture</span><span class="pills">${['AUTO', 'LOW', 'MEDIUM', 'HIGH'].map((q) => `<button class="pillbtn ${ui.quality === q ? 'on' : ''}" data-quality="${q}">${q[0] + q.slice(1).toLowerCase()}</button>`).join('')}</span></div></div>
    <h4>Help</h4><div class="options">${tile('data-tips', 'help', 'What can I do?')}${tile('data-news', 'sparkle', "What's new")}${tile('data-camreset', 'eye', 'Reset view')}</div>
    ${sb ? `<h4>Start again</h4><div class="options">${tile('data-reset', 'reset', 'Start over')}</div>` : ''}`;
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
  const sbOnly = (fn) => { if (!editable()) { locked(); return; } fn(); };
  if ('close' in d || b.classList.contains('tests')) { closeCard(); return; }
  if ('feedhungry' in d) {
    const n = (ui.hungerRows || []).filter((r) => r.hungry).length;
    if (!n) { say('Everyone is full right now. Check back in a few hours.'); cue('TICK'); return; }
    if (!MY.useFishFood()) { say('No fish food left on the shelf! Buy some at the fish stores: tap Shop.'); cue('TICK'); return; }
    stockChanged();
    SB.act('feedHungry', null, 0); cue('PELLET');
    say(n === 1 ? 'Dinner time! Its favourite food drops right above it.' : `Dinner time! All ${n} hungry ones get their favourite food, right above them.`);
    closeCard(); return;
  }
  if ('reefcare' in d) {
    sbOnly(() => {
      const c = ui.coralCare || {};
      if (!c.words) { say('The corals and inverts have all they need right now.'); cue('TICK'); return; }
      if (!SB.act('reefCare', null, c.price || 0)) { say(`Not enough sand dollars: coral care costs ${c.price}. Do today's chores to earn more.`); cue('TICK'); return; }
      cue('PELLET');
      say(`Coral care! In goes ${c.words}.`);
      closeCard();
    });
    return;
  }
  if ('fixall' in d) { SB.fix(-1); say("Let's fix it all up! Watch the tank."); cue('TICK'); closeCard(); return; }
  if (d.fix != null) { const one = document.querySelectorAll('#card [data-fix]').length === 1; SB.fix(Number(d.fix)); cue('TICK'); if (one) { closeCard(); return; } renderCard(); return; }
  if ('more' in d) { ui.more = !ui.more; ui.treat = false; ui.sickPick = false; }
  else if (d.pace) {
    MY.setPace(d.pace);
    const p = JSON.parse(MY.paces()).find((x) => x.id === d.pace);
    if (p) say(`${p.blurb}. Fish still swim at real speed.`);
  }
  else if (d.look) { ui.viewLight = d.look; live.setViewLight(d.look); say(d.look === 'REAL' ? 'The lamp follows the clock again.' : 'A new look for the lamp. Only the picture changes.'); }
  else if ('torch' in d) setTorch(!ui.torch);
  else if ('feedshelf' in d) { closeCard(); openShelf(); return; }
  else if ('tour' in d) { ui.tour = !ui.tour; ui.tourTicks = 0; ui.tourIndex = -1; if (ui.tour) nextTour(); else { ui.following = null; live.follow(null); } }
  else if ('hide' in d) { setChrome(true); return; }
  else if ('saver' in d) { closeCard(); setSaver(true); return; }
  else if ('sub' in d) { setSub(!sub.on); return; }
  else if ('sound' in d) setSoundOn(!ui.sound);
  else if ('radio' in d) radioToggle();
  else if (d.radiostep) radioStep(Number(d.radiostep));
  else if ('records' in d) ui.records = !ui.records;
  else if ('musiccorner' in d) { closeCard(); live.musicOpen(true); return; }
  else if (d.record != null) radioPick(Number(d.record));
  else if (d.follow) { closeCard(); followFish(d.follow); return; }
  else if (d.room) { roomClick(d.room, closeCard); return; }
  else if (d.store) { if (b.getAttribute('aria-disabled') === 'true') { say('Do your care jobs to level up and this store opens!'); return; } closeCard(); window.reefstores.enter(d.store); return; }
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
  else if (d.infect) { if (confirm(ui.mode === 'mine' ? 'Make a fish in your tank sick? It is saved like everything else here.' : 'Make a fish sick? This is only the pretend tank.')) act('infect', d.infect, 0, 'A fish is getting sick. Watch its card.'); ui.sickPick = false; }
  else if (d.scenario) act('scenario', d.scenario, 0, `${C.scenarios.find((s) => s.id === d.scenario).label}. Speed up time to see it play out.`);
  else if ('lapse' in d) { closeCard(); openLapse(); return; }
  else if ('reset' in d) {
    if (ui.mode === 'mine') {
      if (confirm('Start My tank over? Every animal and coral in it goes, and you pick a new empty tank. This cannot be undone.')) { MY.startOver(); closeCard(); showMyTankSetup(); return; }
    } else if (confirm('Reset the pretend tank to how your real tank is now?')) act('reset', null, 0, 'All fresh! The pretend tank matches your real one again.');
  }
  else if ('tips' in d) showTips();
  else if ('tip' in d) { closeCard(); nextTip(); return; }
  else if ('news' in d) { closeCard(); showNews(); return; }
  else if ('microscope' in d) { closeCard(); openMicro(); return; }
  else if ('knock' in d) { act('knock'); cue('CLICK'); }
  else if ('clean' in d) { closeCard(); setCleaning(!ui.cleaning); return; }
  else if ('scape' in d) { closeCard(); openScape(); return; }
  else if ('rocksToggle' in d) {
    ui.rocks = !ui.rocks; store.set('reeflive.rocksDynamic', ui.rocks); live.setDynamicRocks(ui.rocks);
    say(ui.rocks ? 'Rocks can shift now. Dig out too much sand and one may topple!' : 'The rocks stay put, however much the gobies dig.');
  }
  else if ('hermieToggle' in d) { ui.hermie = !ui.hermie; store.set('reeflive.hermie', ui.hermie); $('hermie').classList.toggle('hidden', !ui.hermie); }
  else if ('happyToggle' in d) { ui.happy = !ui.happy; store.set('reeflive.happy', ui.happy); }
  else if ('thoughtsToggle' in d) setThoughts(!thoughts.on);
  else if ('camreset' in d) { ui.following = null; ui.tour = false; live.follow(null); live.resetCamera(); }
  else if (d.quality) { ui.quality = d.quality; store.set('reeflive.picture', ui.quality); live.setQuality(pictureArg()); }
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
  } else if ('radiovol' in d) {
    radioVolume(Number(r.value));
    r.previousElementSibling.textContent = `${Math.round(Number(r.value) * 100)}%`;
  }
});

// ================================================================ fish card, follow, tour
function select(id) { ui.picked = id; renderFish(); }
// the clown parents' "Family" panel (core Brood): eggs, larvae, the nursery tank and its buttons
function familyHtml(f) {
  return `<div class="family"><b>🥚 ${esc(f.title)}</b>${f.lines.map((l) => `<p class="muted">${esc(l)}</p>`).join('')}
    <div class="row-gap wrap">${f.actions.map((x) => `<button class="btn small ${x.enabled ? '' : 'ghost'}" data-fam="${esc(x.id)}" ${x.enabled ? '' : 'disabled'} title="${esc(x.note)}">${esc(x.label)}</button>`).join('')}</div>
    ${f.actions.filter((x) => x.note && (!x.enabled || x.id === 'RAISE_ON')).slice(0, 1).map((x) => `<p class="muted small">${esc(x.note)}</p>`).join('')}</div>`;
}
const FAMILY_SAYS = {
  RAISE_ON: 'When the eggs hatch, a flashlight will lure the babies so you can scoop them into a nursery tank.',
  ROTIFERS: 'Rotifers in! Tiny larvae can only eat food this small.',
  BRINE: 'Baby brine shrimp in. Look at those little bellies turn orange!',
  WATER_CHANGE: 'Fresh water for the babies.',
  RELEASE: 'Off you go, little one! Welcome to the big tank.',
  SPAWN_NOW: 'Watch the pair clean a spot on the rock. Eggs are coming!',
  STOP_NURSERY: 'The fish store will take good care of them.',
  FASTER: 'Nursery time sped up. Watch them grow!',
};
function renderFish() {
  live.select(ui.picked || null); // the picked animal keeps full pose detail (TankRenderer.selectedId)
  // a dead animal is not in the living list: its own card offers the net (SB.animal has every animal)
  let a = ui.picked && animals.find((x) => x.id === ui.picked);
  if (!a && ui.picked) { const d = JSON.parse(SB.animal(ui.picked)); if (d && !d.alive) a = d; }
  // while following, the big card stays out of the view unless the name bar was tapped for more
  $('fish').classList.toggle('hidden', !a || ui.chromeHidden || (!!ui.following && !ui.followInfo));
  if (!a) return;
  if ($('fish').dataset.id === a.id && ui.fishDragging) return;
  $('fish').dataset.id = a.id;
  const bar = (n, v, cls) => `<div class="stat"><span>${n}</span><i class="meter"><i class="${cls}" style="width:${Math.round(Math.max(0, Math.min(1, v)) * 100)}%"></i></i></div>`;
  const sb = editable();
  $('fish').innerHTML = `<div class="card-head"><div><strong>${esc(a.name)}</strong><div class="muted">${esc(a.species)}${a.stage ? ` · ${esc(a.stage)}` : ''} · ${a.cm.toFixed(1)} cm</div></div>
      <button class="icon-btn" data-fclose aria-label="Close">✕</button></div>
    ${bar('Health', a.health, 'good')}${bar('Stress', a.stress, 'caution')}${bar('Hunger', a.hunger, 'hunger')}
    ${a.thinking ? thinkHtml(a) : `<p class="mood"><b>${esc(a.mood)}</b> · ${esc(a.behavior)}</p>`}
    ${a.disease ? `<p class="warn">🤒 ${esc(a.disease)}${a.symptoms.length ? `: ${esc(a.symptoms.join(', '))}` : ''}</p>` : ''}
    ${a.alive && a.family ? familyHtml(a.family) : ''}
    ${a.relations.length ? `<div class="rels">${a.relations.map((r) => `<span class="rel ${r.affinity > 0.15 ? 'good' : r.affinity < -0.15 ? 'danger' : ''}">${esc(r.label)} ${esc(r.name)}</span>`).join('')}</div>` : ''}
    ${!a.alive ? `<p class="muted">${ui.netting === a.id ? 'Here comes the net…' : `Oh no, ${esc(a.name)} didn't make it. Scoop them out to keep the water clean.`}</p>
    <div class="row-gap"><button class="btn small" data-fnet ${ui.netting === a.id ? 'disabled' : ''}>🥅 ${ui.netting === a.id ? 'Scooping…' : 'Scoop out with the net'}</button></div>` : `
    <div class="row-gap"><button class="btn small" data-ffollow>${ui.following === a.id ? 'Stop following' : 'Follow'}</button>
      ${sb ? '<button class="btn small ghost" data-fremove>Take out</button>' : ''}${ui.mode === 'mine' ? '<button class="btn small ghost" data-fname>Name it</button>' : ''}</div>`}
    ${sb && a.alive ? `<label class="slider"><span>Size</span><output>${a.cm.toFixed(1)} cm</output><input type="range" data-fsize min="0.5" max="40" step="0.5" value="${a.cm}"></label>` : ''}`;
}
$('fish').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  if ('fclose' in d) { ui.picked = null; ui.followInfo = false; renderFish(); renderFollow(); }
  else if ('ffollow' in d) followFish(ui.following === ui.picked ? null : ui.picked);
  else if ('fam' in d) {
    if (d.fam === 'VIEW_NURSERY') { ui.picked = null; live.nursery(true); say('Here is the nursery tank. Drag anywhere to go back to the big tank.'); }
    else { SB.act('breed', d.fam, 0); cue('TICK'); if (FAMILY_SAYS[d.fam]) say(FAMILY_SAYS[d.fam]); setTimeout(() => { animals = JSON.parse(SB.animals()); renderFish(); }, 150); }
  }
  else if ('fnet' in d) {
    // the card steps aside so the net can be watched; the goodbye plays once it is out
    const name = $('fish').querySelector('strong')?.textContent || 'them';
    SB.act('netOut', ui.picked, 0); cue('TICK');
    say(`Here comes the net! Let's scoop ${name} out gently so the water stays clean.`);
    ui.netting = ui.picked; ui.picked = null;
  }
  else if ('fremove' in d) { if (confirm(ui.mode === 'mine' ? 'Take this animal out of your tank for good?' : 'Take this animal out of the pretend tank?')) { act('remove', ui.picked, 0, 'Out it goes, gently, in a cup.'); ui.picked = null; } }
  else if ('fname' in d) {
    const a = animals.find((x) => x.id === ui.picked);
    const n = a && prompt('Name this animal', a.name);
    if (n && n.trim()) { MY.rename(a.id, n.trim().slice(0, 24)); say(`Hello, ${n.trim().slice(0, 24)}!`); setTimeout(tick, 150); }
  }
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
  const was = ui.following;
  ui.following = id; if (id) ui.picked = id;
  ui.followInfo = false;
  if (id && ui.card) closeCard();
  live.follow(id);
  if (id && !was && !ui.tour) say('Drag to look around. Tap another fish to follow it!');
  renderFish(); renderFollow();
}
/** Sets innerHTML only when it changed, so a button under the finger is not swapped out mid-tap by the 2 Hz refresh. */
function setHtml(el, html) { if (el._html !== html) { el._html = html; el.innerHTML = html; } }
function renderFollow() {
  const i = animals.findIndex((x) => x.id === ui.following);
  const hide = i < 0 || !!ui.card || ui.shelf || ui.chromeHidden && !ui.tour;
  $('follow').classList.toggle('hidden', hide);
  $('followCam').classList.toggle('hidden', hide);
  $('chrome').classList.toggle('following', !hide);
  if (hide) return;
  const a = animals[i];
  const many = animals.length > 1;
  const line = a.thinking || a.caption || a.behavior || '';
  setHtml($('follow'), `<div class="fstoprow"><button class="fstop" data-fback>✕ Stop following</button></div>
    <div class="fnav glass">${many ? '<button class="fbig" data-fprev aria-label="Previous fish">◀</button>' : ''}
    <button class="fcap ${ui.followInfo ? 'open' : ''}" data-finfo aria-label="More about ${esc(a.name)}"><span class="fname"><strong>${esc(a.name)}</strong>${many ? `<span class="muted"> ${i + 1}/${animals.length}</span>` : ''}</span><span class="fline">${esc(line)}</span></button>
    ${many ? '<button class="fbig" data-fnext aria-label="Next fish">▶</button>' : ''}</div>`);
}
$('follow').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  cue('TICK');
  const i = animals.findIndex((x) => x.id === ui.following);
  if ('fback' in d) { ui.tour = false; followFish(null); live.resetCamera(); }
  else if ('fprev' in d) followFish(animals[(i - 1 + animals.length) % animals.length].id);
  else if ('fnext' in d) followFish(animals[(i + 1) % animals.length].id);
  else if ('finfo' in d) { ui.followInfo = !ui.followInfo; ui.picked = ui.following; renderFish(); renderFollow(); }
});
// keyboard while following: Esc stops, + / − zoom, arrows step to the previous / next fish, T turns
document.addEventListener('keydown', (e) => {
  if (!ui.following || e.target.closest?.('input, textarea, select')) return;
  const i = animals.findIndex((x) => x.id === ui.following);
  if (e.key === 'Escape') { ui.tour = false; followFish(null); live.resetCamera(); }
  else if (e.key === '+' || e.key === '=') live.followZoom(1);
  else if (e.key === '-' || e.key === '_') live.followZoom(-1);
  else if (e.key === 't' || e.key === 'T') live.followOrbit(1);
  else if (e.key === 'ArrowLeft' && animals.length > 1) followFish(animals[(i - 1 + animals.length) % animals.length].id);
  else if (e.key === 'ArrowRight' && animals.length > 1) followFish(animals[(i + 1) % animals.length].id);
  else return;
  e.preventDefault();
});
// the follow camera's buttons: closer / further (eases there; bounces at the limits) and swing round the fish
$('followCam').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  cue('TICK');
  b.classList.remove('pop'); void b.offsetWidth; b.classList.add('pop');
  if (b.dataset.zoom) live.followZoom(Number(b.dataset.zoom));
  else if (b.dataset.orbit) live.followOrbit(Number(b.dataset.orbit));
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
  { id: 'siphon', row: 1, label: 'Siphon gunk', run: () => act('maintain', 'SIPHON_SAND', 0, 'Slurp! Fish poop and mulm vacuumed off the sand.') },
  { id: 'rinse_filter', row: 1, label: 'Rinse filter', run: () => act('maintain', 'RINSE_FILTER', 0, 'The filter floss is rinsed. Trapped poop is gone before it rots.') },
  { id: 'zap_pest', row: 1, label: 'Aiptasia-X', sandbox: true, run: () => { closeShelf(); act('treatAiptasia', 'AIPTASIA_X', 0, 'Aiptasia-X into the biggest pest anemone\'s mouth...'); } },
  { id: 'settle_in', row: 1, label: 'Settle in', sandbox: true, run: () => act('acclimate', null, 0, 'Everyone gets used to the water now.') },
  { id: 'glass_cleaner', row: 2, label: 'Clean glass', run: () => { closeShelf(); setCleaning(true); } },
  { id: 'test_kit', row: 2, label: 'Test kit', run: () => testKit() },
  { id: 'microscope', row: 2, label: 'Look closer', run: () => { closeShelf(); openMicro(); } },
  { id: 'knocker', row: 2, label: 'Knock glass', run: () => { closeShelf(); act('knock'); cue('CLICK'); } },
  { id: 'move_rocks', row: 2, label: 'Move rocks', sandbox: true, run: () => act('rearrangeRock', null, 0, 'Rocks shuffled! Watch everyone find a new spot.') },
  { id: 'joes_juice', row: 2, label: "Joe's Juice", sandbox: true, run: () => { closeShelf(); act('treatAiptasia', 'JOES_JUICE', 0, "Joe's Juice over the biggest pest anemone's mouth..."); } },
  { id: 'kalk_paste', row: 2, label: 'Kalk paste', sandbox: true, run: () => { closeShelf(); act('treatAiptasia', 'KALK_PASTE', 0, 'A syringe of kalk paste for the biggest pest anemone...'); } },
  { id: 'pest_laser', row: 2, label: 'Pest laser', sandbox: true, run: () => { closeShelf(); act('treatAiptasia', 'LASER', 0, 'Aiming the laser at the biggest pest anemone...'); } },
];
const ROWS = [['feedTin', 'Feed'], ['droplet', 'Care'], ['flask', 'Tools']];
// My tank's shelf holds only what was bought at the fish stores (core SupplyBook); Watch never runs out
let stock = null;
function refreshStock() {
  try { stock = ui.mode === 'mine' ? JSON.parse(MY.supplies()) : null; } catch { stock = null; }
}
/** Uses left of supply [id] on My tank's shelf, or -1 when it never runs out. */
function left(id) { const n = stock?.left?.[id]; return n == null ? -1 : n; }
function shelfLockIds() {
  refreshStock();
  live.shelfHidden(SUPPLIES.filter((s) => left(s.id) === 0).map((s) => s.id).join(','));
  return SUPPLIES.filter((s) => s.sandbox && !editable()).map((s) => s.id).join(',');
}
function stockChanged() { live.shelfLocked(shelfLockIds()); if (ui.shelf) renderShelf(); }
/** The badge on a shelf tile: uses left, or nothing for a tool that is kept (or anything outside My tank). */
function stockBadge(id) {
  const n = left(id);
  if (n < 0) return '';
  if (stock.keeps.includes(id)) return n ? '' : '<i class="cnt out">Shop</i>';
  return `<i class="cnt ${n ? n <= 5 ? 'low' : '' : 'out'}">${n ? `×${n}` : 'Shop'}</i>`;
}
function outOf(s) {
  cue('TICK');
  say(`No ${supplyLabel(s).toLowerCase()} left! Buy more at the fish stores: tap Shop.`);
}
function supplyLabel(s) { return s.food ? C.foods.find((f) => f.id === s.food)?.label ?? s.food : s.label; }
function openShelf() {
  ui.shelf = true; closeCard();
  stockChanged();
  ui.picked = null; renderFish();
  if (ui.following) followFish(null);
  live.shelfOpen(true); live.shelfRow(ui.shelfRow);
  renderShelf(); renderRail();
  say('Tap a food or tool on the shelf to use it.');
}
function closeShelf() { ui.shelf = false; live.shelfOpen(false); $('shelfbar').classList.add('hidden'); renderRail(); }
function renderShelf() {
  const sb = editable();
  $('shelfbar').innerHTML = `<div class="card-head"><div class="pills">${ROWS.map(([i, n], k) => `<button class="pillbtn ${ui.shelfRow === k ? 'on' : ''}" data-row="${k}">${icon(i)} ${n}</button>`).join('')}</div>
    <button class="btn small ghost" data-back>Back to tank</button></div>
    ${ui.mode === 'mine' ? '<p class="muted">What runs out leaves the shelf. Buy more at the fish stores: tap Shop.</p>' : ''}
    <div class="options">${SUPPLIES.filter((s) => s.row === ui.shelfRow && left(s.id) !== 0).map((s) => tile(`data-supply="${s.id}"`, s.food ? `food:${s.food}` : SUPPLY_ICON[s.id] ?? 'bucket', supplyLabel(s), false, (s.sandbox && !sb) || left(s.id) === 0, stockBadge(s.id))).join('')}</div>`;
  $('shelfbar').classList.remove('hidden');
}
const FOOD_ICON = { FLAKE: '🟠', PELLET: '🟤', FLOATING_PELLET: '🟫', ALGAE_WAFER: '🟢', MYSIS: '🦐', BRINE: '🦐', KRILL: '🦐', PRAWN: '🍤', CYCLOPS: '🔴', NORI: '🥬', CORAL_FOOD: '🪸', PLANKTON: '✨', PODS: '🐛' };
const SUPPLY_ICON = { water_change: 'bucket', water_small: 'droplet', dose_alk: 'shell', dose_ca: 'branch', dose_mg: 'rings', new_sock: 'sock', fresh_carbon: 'carbon', trim_algae: 'scissors', siphon: 'fishWaste', rinse_filter: 'sock', zap_pest: 'zap', settle_in: 'heart', glass_cleaner: 'flipper', test_kit: 'flask', microscope: 'microscope', knocker: 'touch', move_rocks: 'rocks' };
$('shelfbar').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  if ('back' in d) closeShelf();
  else if (d.row) { ui.shelfRow = Number(d.row); live.shelfRow(ui.shelfRow); renderShelf(); }
  else if (d.supply) live.shelfPick(d.supply); // lifts the prop in 3D; the renderer calls back onShelfPick
});
// ---- the music corner bar (under the 3D record stand and radio)
function renderMusic() {
  const bar = $('musicbar');
  if (!ui.music) { bar.classList.add('hidden'); return; }
  bar.innerHTML = musicBarHtml(esc, icon);
  bar.classList.remove('hidden');
}
$('musicbar').addEventListener('click', (e) => {
  const d = e.target.closest('button')?.dataset;
  if (!d) return;
  radioKick();
  if ('mback' in d) live.musicOpen(false);
  else if ('mbuy' in d) radioBuyOffer();
  else if ('mnot' in d) radioDropOffer();
  else if ('mpower' in d) radioToggle();
  else if ('mnext' in d) radioStep(1);
});

/** A prop picked on the shelf (tapped in 3D or on its tag): run it like SupplyItem.use. */
function useSupply(id) {
  const s = SUPPLIES.find((x) => x.id === id);
  if (!s) return;
  if (s.sandbox && !editable()) { locked(); return; }
  if (left(s.id) === 0) { outOf(s); return; }
  if (s.food) {
    const f = C.foods.find((x) => x.id === s.food);
    // nori goes on its clip; every other food waits in the hand until the tank is tapped where it should go
    if (s.food === 'NORI') { if (!MY.useSupply('nori')) { outOf(s); return; } stockChanged(); SB.act('feed', s.food, 0); cue('FEED'); say(`${f.label}! ${f.blurb}`); setTimeout(closeShelf, 700); return; }
    setTimeout(() => { closeShelf(); startAim(s.food); }, 500);
    return;
  }
  if (left(s.id) > 0 && !MY.useSupply(s.id)) { outOf(s); return; }
  stockChanged();
  s.run();
  if (s.id !== 'test_kit' && ui.shelf) setTimeout(closeShelf, 700);
}
function testKit() {
  const r = P.readouts.map((x) => `${x.label} ${x.text}`).join(' · ');
  const extra = ['ph', 'alk', 'ca', 'mg', 'nh3', 'no2'].map((id) => { const p = C.params.find((x) => x.id === id); return `${p.short} ${fmt(p, val(id))}`; }).join(' · ');
  say(`Test kit: ${r} · ${extra}`);
}

// ================================================================ screensaver
// Calm mode over the keeper's own view (the shared CinemaSession moves the camera): from the Screensaver tile, or
// by itself after 30 s with no touch, mouse or key. One tap fades back to exactly where they were: camera, follow,
// open card and buttons are all left as they were, only faded out meanwhile.
const IDLE_MS = 30000;
let lastInput = performance.now();
let saverFull = false;
['pointerdown', 'pointermove', 'wheel', 'keydown', 'touchstart'].forEach((ev) =>
  document.addEventListener(ev, () => { lastInput = performance.now(); }, { capture: true, passive: true }));
/** Never mid-action: a dialog, store walk, drag, the shelf, cleaning, netting or the microscope. */
function saverBlocked() {
  return !!document.querySelector('dialog[open]') || ui.shelf || ui.cleaning || ui.dragging || ui.fishDragging || ui.netting ||
    !$('micro').classList.contains('hidden') || ($('storeUi') && !$('storeUi').classList.contains('hidden')) ||
    !$('live').classList.contains('active') || document.visibilityState !== 'visible';
}
setInterval(() => {
  if (!ui.saver && !saverBlocked() && performance.now() - lastInput > IDLE_MS) setSaver(true, false);
}, 1000);
function chromeLayers() { return [...document.querySelectorAll('#live > :not(#tank):not(#saver)'), document.querySelector('header.bar')].filter(Boolean); }
function setSaver(on, full = true) {
  if (on) {
    if (ui.saver) return;
    ui.saver = true;
    live.screensaver(true);
    $('saver').classList.remove('hidden');
    $('saverHint').style.opacity = 1; setTimeout(() => { $('saverHint').style.opacity = 0; }, 3000);
    saverFull = full && !!document.documentElement.requestFullscreen;
    if (saverFull) document.documentElement.requestFullscreen().catch(() => {});
    requestAnimationFrame(saverFrame);
  } else if (ui.saver) {
    live.screensaver(false);   // fades back; saverFrame tidies up once it is over
  }
}
let chromeFade = 0;
function saverFrame() {
  if (!ui.saver) return;
  const c = JSON.parse(live.cinema());
  if (!c) {   // over: the view is back where it was
    ui.saver = false; chromeFade = 0;
    $('saver').classList.add('hidden');
    chromeLayers().forEach((el) => { el.style.opacity = ''; });
    if (saverFull && document.fullscreenElement) document.exitFullscreen().catch(() => {});
    lastInput = performance.now();
    return;
  }
  // the buttons fade out with the first dip and back in on the way out
  chromeFade = c.leaving ? c.fade : Math.max(chromeFade, c.fade);
  chromeLayers().forEach((el) => { el.style.opacity = 1 - chromeFade; });
  $('saverBlack').style.opacity = c.fade;
  $('saverCaption').style.opacity = c.alpha;
  if ($('saverCaption').textContent !== c.caption) $('saverCaption').textContent = c.caption;
  requestAnimationFrame(saverFrame);
}
// the layer takes every touch while the screensaver plays: a tap only leaves, it never reaches the tank
['pointerdown', 'click'].forEach((ev) => $('saver').addEventListener(ev, (e) => { e.preventDefault(); e.stopPropagation(); setSaver(false); }));
document.addEventListener('keydown', (e) => { if (ui.saver) { e.preventDefault(); setSaver(false); } }, { capture: true });

// ================================================================ chrome, torch, cleaning
function setChrome(hidden) {
  ui.chromeHidden = hidden;
  $('chrome').classList.toggle('hidden', hidden);
  $('showChrome').classList.toggle('hidden', !hidden);
  if (hidden) closeCard();
  renderFish(); renderFollow();
}
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
  say(on ? 'Rub the glass with the scraper. Squeaky clean!' : 'Sparkly glass!');
}
$('cleanDone').addEventListener('click', () => setCleaning(false));

// ================================================================ aquascape builder
// Pick a white dry rock, tap the tank where it goes: it drops in and settles on the sand or rock under it.
const scape = { on: false, shape: 'BOULDER', turn: 0, takeOut: false };
function openScape() { scape.on = true; renderScape(); say('Pick a rock, then tap the tank where it should go.'); }
function renderScape() {
  let bar = $('scapeBar');
  if (!bar) { bar = document.createElement('div'); bar.id = 'scapeBar'; bar.className = 'scapebar glass'; document.body.appendChild(bar); }
  bar.classList.toggle('hidden', !scape.on);
  live.gearTaps(!scape.on);
  if (!scape.on) return;
  const shapes = JSON.parse(live.scapeShapes());
  const label = shapes.find((s) => s.id === scape.shape)?.label.toLowerCase() || 'rock';
  bar.innerHTML = `<strong>${scape.takeOut ? 'Tap a rock to take it out' : `Tap the tank to drop a ${label}`}</strong>
    <span class="sub">New dry rock is white. Over a few months it turns brown, then purple and pink like live rock.</span>
    <div class="pills">${shapes.map((s) => `<button class="pillbtn ${!scape.takeOut && s.id === scape.shape ? 'on' : ''}" data-shape="${s.id}">${s.label}</button>`).join('')}</div>
    <div class="pills"><button class="pillbtn" data-turn>Turn ${scape.turn}°</button><button class="pillbtn ${scape.takeOut ? 'on' : ''}" data-takeout>Take out</button>
    <button class="pillbtn" data-undo>Undo</button><button class="pillbtn" data-empty>Empty tank</button><button class="pillbtn on" data-done>Done</button></div>`;
}
document.addEventListener('click', (e) => {
  const b = e.target.closest('#scapeBar button');
  if (!b) return;
  const d = b.dataset;
  if (d.shape) { scape.shape = d.shape; scape.takeOut = false; }
  else if ('turn' in d) scape.turn = (scape.turn + 45) % 360;
  else if ('takeout' in d) scape.takeOut = !scape.takeOut;
  else if ('undo' in d) live.scapeUndo();
  else if ('empty' in d) { if (confirm('Take out all the rock and build from scratch? Corals on them drop to the sand.')) live.scapeClear(); }
  else if ('done' in d) scape.on = false;
  renderScape();
});
function scapeTap() {
  if (!live.scapeTouch(scape.shape, scape.turn, scape.takeOut)) return;
  knockSound(0.5);
  try { navigator.vibrate?.(8); } catch { /* not on iOS */ }
}

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
  const sb = editable();
  const census = m.census.map((c) => `<span class="chip">${esc(c.label)} ${c.total >= 1000 ? `${(c.total / 1000).toFixed(1)}k` : Math.round(c.total)} ${c.trend > 0.01 ? '↑' : c.trend < -0.01 ? '↓' : ''}</span>`).join('');
  const inView = m.groups.map((gr) => `<span class="chip" style="color:${GROUP_COLOR[m.groups.indexOf(gr) % GROUP_COLOR.length]}">${esc(gr.label)} ${gr.sprites}${gr.nauplii ? ` + ${gr.nauplii} babies` : ''}</span>`).join('') || '<span class="muted">Nothing big enough to see at this zoom.</span>';
  const bact = micro.bacteria ? `<h4>Bacteria</h4><div class="rows">${m.bacteria.guilds.map((b) => `<div class="toggle-row"><span>${esc(b.short)} <small class="muted">${esc(b.amount)} · ${esc(b.effect)}</small></span><span class="${b.good === false ? 'danger' : 'good'}">${b.trend > 0.01 ? '↑' : b.trend < -0.01 ? '↓' : '·'}</span></div>`).join('')}</div>
    ${m.bacteria.cloudy ? '<p class="warn">Milky water: a bacterial bloom.</p>' : ''}${m.bacteria.h2s > 0.002 ? '<p class="warn">Smells of rotten eggs: hydrogen sulfide in the sand.</p>' : ''}
    <div class="options">${C.bacteria.map((b) => tile(`data-baction="${b.id}"`, 'flask', b.label, false, !sb)).join('')}</div>` : '';
  $('microInfo').innerHTML = `<div class="chips">${inView}</div><h4>In the whole tank</h4><div class="chips">${census}<span class="chip">Phyto ${Math.round(m.phyto)}/mL</span></div>
    <h4>Add or treat</h4><div class="options">${C.micro.map((a) => tile(`data-maction="${a.id}"`, a.id.startsWith('TREAT') ? 'bottle' : 'bubbles', a.label, false, !sb)).join('')}</div>${bact}`;
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
/** The page's one AudioContext (tank sounds and the radio share it). */
function audioCtx() { if (!actx) actx = new (window.AudioContext || window.webkitAudioContext)(); return actx; }
const LOOPS = { flow: 'tank_flow_loop', hum: 'tank_hum_loop', hob: 'tank_hob_loop', bubbles: 'tank_bubbles_loop', murmur: 'store_murmur_loop' };
const CUES = { BELL: 'store_door_bell', FLUSH: 'tank_bubbles_loop', FEED: 'tank_feed_plop', PELLET: 'tank_pellet_drop', CLICK: 'tank_shrimp_click', TICK: 'tank_ui_tick', ALERT: 'tank_alert_chime', SCRAPE: 'tank_glass_scrape', SPARKLE: 'tank_reward_sparkle' };
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
  if (on) {
    audioCtx().resume();
    Object.values(LOOPS).concat(Object.values(CUES)).forEach(loadSound);
  } else stopLoops();
  say(on ? 'Sound on. Listen to the bubbles!' : 'Sound off.');
}
function stopLoops() { for (const k of Object.keys(loops)) { try { loops[k].src.stop(); } catch { /* already stopped */ } delete loops[k]; } }
function updateSound() {
  if (!ui.sound || !actx || document.hidden) return;
  const s = JSON.parse(SB.sound(ui.volume));
  if (!s) return;
  // in a fish store: the shop's room tone under the display tank's water (stores.js rings the door bell)
  s.murmur = window.reefstores?.active() ? 0.35 * ui.volume : 0;
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
function cue(name, gain = 0.8, rate = 1, seconds = 0) {
  if (!ui.sound || !actx) return;
  const now = performance.now();
  if (now - (lastCue[name] || 0) < (name === 'TICK' ? 60 : 400)) return;
  lastCue[name] = now;
  const b = buffers[CUES[name]];
  if (!b) return;
  const src = actx.createBufferSource(); src.buffer = b;
  const g = actx.createGain(); g.gain.value = gain * ui.volume;
  src.playbackRate.value = rate;
  src.connect(g).connect(actx.destination); src.start();
  if (seconds > 0) src.stop(actx.currentTime + seconds);
}
// a tap with no fish under it (Android LiveTaps): undo what is open first, else touch the tank there
let touchSaidAt = 0;
function touchTank(surface) {
  if (scape.on) { scapeTap(); return; }
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
document.addEventListener('pointerdown', () => {
  if (ui.sound && !actx) setSoundOn(true); else if (actx?.state === 'suspended') actx.resume();
  radioKick();
}, { once: false, passive: true });

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
// Be a little submarine, built for fish watching: tap a fish and the sub glides over and keeps it company at a polite
// distance (hover, circle or swim alongside); the stick (or W A S D / arrows) drifts and steers, and while watching it
// swings round the fish and moves closer or further. Up and down (or Space / E and Shift / Q) dive and climb.
// The fish see it by species and personality unless it is invisible.
const sub = { on: false, visible: true, light: true, inside: false, joy: [0, 0], rise: 0, keys: new Set(), sent: '', watch: null, mode: 'HOVER', sentAt: 0 };
function setSub(on) {
  sub.on = on;
  if (on) { closeCard(); if (ui.tour) ui.tour = false; ui.following = null; if (ui.cleaning) setCleaning(false); if (ui.shelf) closeShelf(); }
  document.body.classList.toggle('subbing', on);
  $('subui').classList.toggle('hidden', !on);
  sub.joy = [0, 0]; sub.rise = 0; sub.keys.clear(); sub.sent = ''; sub.watch = null;
  live.submarine(on, sub.visible, sub.light);
  live.subView(sub.inside);
  if (on) live.steerSub(0, 0, 0, 0);
  moveKnob(0, 0);
  renderSub();
  toast(on ? 'Dive dive! Tap a fish and the sub swims over to watch it. The yellow stick drifts and steers.' : 'The submarine is back in its dock.');
  if (!on) { renderRail(); renderFollow(); }
}
/** Sends the sub to keep [id] company (null = steer freely again). */
function subWatch(id, mode = sub.mode) {
  sub.watch = id; sub.mode = mode; sub.sentAt = performance.now();
  live.subWatch(id ?? '', mode);
  cue('TICK');
  const a = id && animals.find((x) => x.id === id);
  if (a) toast(mode === 'CIRCLE' ? `Circling ${a.name}, slowly.` : mode === 'ALONGSIDE' ? `Swimming along with ${a.name}.` : `Gliding over to ${a.name}. Slow and polite!`);
  renderSub();
}
/** ◀ ▶: the sim picks the next fish (or the nearest one when none is watched); the card follows once it lands. */
function subStep(d) {
  live.subWatchNext(d);
  sub.sentAt = 0;
  cue('TICK');
}
function renderSub() {
  const opt = (id, on, label) => { $(id).classList.toggle('on', on); $(id).setAttribute('aria-pressed', String(on)); $(id).querySelector('span').textContent = label; };
  opt('subGhost', !sub.visible, sub.visible ? 'Invisible' : 'Invisible: on');
  opt('subCam', sub.inside, sub.inside ? 'Inside: on' : 'Inside');
  opt('subLight', sub.light, sub.light ? 'Light' : 'Light off');
  const a = sub.watch && animals.find((x) => x.id === sub.watch);
  // the card always shows: idle it offers ◀ ▶ to pick a fish (or tap one); watching it names the fish and its modes
  $('subWatch').classList.toggle('idle', !a);
  $('subWatchName').textContent = a ? `Watching ${a.name}` : 'Tap a fish, or ▶ for the nearest';
  for (const b of document.querySelectorAll('.swmode')) b.classList.toggle('on', !!a && b.dataset.mode === sub.mode);
  $('subJoyLabel').textContent = a ? 'Swing round · closer' : 'Drift & steer';
  renderSubInfo();
}
function renderSubInfo() {
  let s = null;
  try { s = JSON.parse(live.subState()); } catch { /* not out yet */ }
  // the sim is the truth (it ends a watch when the fish is gone); give a fresh command a moment to land first
  if (s && performance.now() - sub.sentAt > 1500 && (s.watch ?? null) !== sub.watch) {
    sub.watch = s.watch ?? null;
    if (s.watch) { sub.mode = s.mode; const a = animals.find((x) => x.id === s.watch); if (a) toast(`Gliding over to ${a.name}. Slow and polite!`); }
    renderSub(); return;
  }
  const a = sub.watch && animals.find((x) => x.id === sub.watch);
  const who = !sub.visible ? '👻 The fish cannot see you' : s ? `👀 ${s.curious} curious · 💨 ${s.wary} shy` : '🟡 Submarine';
  $('subInfo').textContent = who;
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
function moveKnob(x, y) { $('subKnob').style.transform = `translate(${x * 42}px, ${y * 42}px)`; }
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
  $('subLetGo').addEventListener('click', () => { subWatch(null); toast('Free to steer again.'); });
  $('subPrev').addEventListener('click', () => subStep(-1));
  $('subNext').addEventListener('click', () => subStep(1));
  for (const b of document.querySelectorAll('.swmode')) b.addEventListener('click', () => { if (sub.watch) subWatch(sub.watch, b.dataset.mode); });
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
  startThoughts();
  $('hermie').classList.toggle('hidden', !ui.hermie);
  live.shelfLocked(shelfLockIds());
  renderRail();
}

// ================================================================ touch controls (Android LiveTankScreen onAim / onSpot)
// Food picked on the shelf stays "in hand" until the tank is tapped: it goes in right at that fish, coral or spot.
const aimUi = { food: null };
function startAim(food) {
  aimUi.food = food;
  live.aimArm(true);
  const f = C.foods.find((x) => x.id === food);
  let bar = $('aimBar');
  if (!bar) {
    bar = document.createElement('div'); bar.id = 'aimBar'; bar.className = 'aimbar glass';
    bar.addEventListener('click', (e) => { if (e.target.closest('[data-aimcancel]')) stopAim(); });
    document.body.appendChild(bar);
  }
  bar.innerHTML = `<span class="ico">${icon(`food:${food}`)}</span><strong>Tap a fish, coral or spot to drop the ${esc(f.label.toLowerCase())} there</strong>
    <button class="pillbtn" data-aimcancel>Cancel</button>`;
  bar.classList.remove('hidden');
}
function stopAim() {
  aimUi.food = null;
  live.aimArm(false);
  $('aimBar')?.classList.add('hidden');
}
function aimFed(h) {
  const food = aimUi.food;
  if (!food) return;
  stopAim();
  if (!MY.useSupply(food.toLowerCase())) { outOf(SUPPLIES.find((s) => s.food === food)); return; }
  stockChanged();
  SB.feedAt(food, h.x, h.y, h.z);
  const f = C.foods.find((x) => x.id === food);
  cue(['PELLET', 'FLOATING_PELLET', 'ALGAE_WAFER'].includes(food) ? 'PELLET' : 'FEED');
  const who = h.id ? animals.find((a) => a.id === h.id)?.name : null;
  say(who ? `${f.label} for ${who}! ${f.blurb}` : `${f.label}! ${f.blurb}`);
}

// A tap on a piece of gear (skimmer, filter, heater, pumps, lamps, top-off) pops out its own little menu right there.
const spotUi = { spot: null };
function openSpot(t) {
  const m = JSON.parse(SB.spotMenu(t.spot));
  if (!m) return;
  spotUi.spot = t.spot;
  buzz(5);
  if (ui.card) closeCard();
  let el = $('spotPop');
  if (!el) {
    el = document.createElement('div'); el.id = 'spotPop'; el.className = 'spotpop';
    el.addEventListener('click', (e) => {
      const b = e.target.closest('button');
      if (!b) return;
      if ('close' in b.dataset) { closeSpot(); return; }
      const label = b.dataset.label;
      if (SB.spotAct(spotUi.spot, b.dataset.opt)) { cue('TICK'); say(`${label}: on it!`); setTimeout(tick, 150); }
      closeSpot();
    });
    document.body.appendChild(el);
  }
  el.innerHTML = `<div class="ring"></div><div class="card ${m.warn ? 'warn' : ''}">
    <div class="head"><span class="ico">${icon(m.glyph)}</span><strong>${esc(m.title)}</strong><button class="x" data-close aria-label="Close">${icon('close')}</button></div>
    <p>${esc(m.status)}</p>
    ${m.options.length ? `<div class="opts">${m.options.map((o) => `<button class="${o.urgent ? 'urgent' : ''}" data-opt="${o.id}" data-label="${esc(o.label)}"><span class="ico">${icon(o.glyph)}</span>${esc(o.label)}</button>`).join('')}</div>`
      : '<p class="muted">Switch to My tank to change the gear.</p>'}</div>`;
  const r = $('tank').getBoundingClientRect();
  const x = r.left + t.x, y = r.top + t.y;
  el.style.setProperty('--x', `${x}px`); el.style.setProperty('--y', `${y}px`);
  el.classList.remove('hidden', 'below', 'pop');
  const card = el.querySelector('.card');
  const w = card.offsetWidth, hgt = card.offsetHeight;
  const below = y - 30 - hgt < 8;
  el.classList.toggle('below', below);
  card.style.left = `${Math.min(Math.max(8, x - w / 2), innerWidth - w - 8)}px`;
  card.style.top = `${below ? y + 30 : y - 30 - hgt}px`;
  void el.offsetWidth;   // restart the pop animation
  el.classList.add('pop');
}
function closeSpot() { spotUi.spot = null; $('spotPop')?.classList.add('hidden'); }
$('tank').addEventListener('pointerdown', () => { if (spotUi.spot) closeSpot(); });
