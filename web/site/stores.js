// My tank's fish stores (docs/ECONOMY.md §5; Android StoreScreens.kt): the street of storefronts, then walking the
// store's display tanks in the 3D view, picking animals into the shop bag and paying at the counter.
import { SAND_DOLLAR } from './care.js';

const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const FALLBACK = { FISH: '🐠', INVERTS: '🦐', CORALS: '🪸' };
const pic = (it) => it.pack ? '<span class="sp">🧴</span>' : `<img class="sp" src="species/${it.species || it.id}.webp" alt="" onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'sp',textContent:'${FALLBACK[it.group] || '🐠'}'}))">`;

let my, say, cue, onLeave;
let ui, data = null, at = 0, picked = null, bag = [], counterOpen = false, sellOpen = false, offer = null;
// a visit set off but not at the door yet: the first display tank is still being built (ReefMyTank.visitReady)
let pending = null;
// the store's people talk in speech bubbles over their heads (render StorePeople; live.js styles .tbubble)
const talk = { layer: null, els: [], raf: 0 };

// ---- display tanks are built a few ms a frame (core DisplayBuild): their rockwork is a real physics settle, which in
// one go froze the page for seconds. The street warms the first tank of the stores you may walk into; a store warms
// the tanks beside the one you are at. Background slices skip frames that already run long.
const LAST = 'reeflive.laststore';
const warm = { raf: 0, last: 0 };
async function warmLoop(t) {
  warm.raf = -1;   // running (the sim answers asynchronously): startWarm leaves it be
  if (!my) { warm.raf = 0; return; }
  const waiting = pending;
  const gap = t - warm.last;
  warm.last = t;
  let more = false;
  try {
    // someone waiting at the door gets most of the frame; background work only on frames that are not already slow
    // (40 ms: a device held at 30 fps still warms the next tank)
    if (waiting) {
      if (await my.visitReady(9)) {
        // walked in; unless the keeper turned back meanwhile
        if (pending === waiting) { pending = null; arrive(waiting.d); } else my.leaveStore();
      }
      more = true;
    } else if (gap < 40) more = await my.buildStep(4); else more = await my.building();
  } catch (e) { console.error(e); more = false; if (waiting && pending === waiting) abandon('The store is closed right now. Try again in a moment.'); }
  warm.raf = more || pending ? requestAnimationFrame(warmLoop) : 0;
}
function startWarm() { if (!warm.raf && my) warm.raf = requestAnimationFrame(warmLoop); }

/** The street for My tank's "Shop" card: one storefront per store, locked ones dark with their level. */
export function streetHtml() {
  let s = null;
  try { s = JSON.parse(my.peek.stores()); } catch { s = null; }
  if (!s) return '';
  try {
    // the store you went to last first, then the others that are open
    const open = s.stores.filter((f) => f.open).map((f) => f.id);
    let last = null; try { last = localStorage.getItem(LAST); } catch { last = null; }
    if (last && open.includes(last)) open.splice(open.indexOf(last), 1), open.unshift(last);
    my.warmStores(open);
    startWarm();
  } catch (e) { console.error(e); }
  return `<p class="muted">Visit a fish store to buy new animals with your sand dollars.</p><div class="street">${s.stores.map((f) =>
    `<button class="front ${f.open ? '' : 'shut'}" data-store="${f.id}" ${f.open ? '' : 'aria-disabled="true"'}>
      <img src="stores/${f.id}.webp" alt="">
      <b>${esc(f.label)}</b>${f.open ? '' : `<span class="opens">🔒 Opens at level ${f.level}</span>`}</button>`).join('')}</div>`;
}

export function initStores(opts) {
  ({ my, say, cue } = opts);
  onLeave = opts.onLeave || (() => {});
  ui = document.createElement('div');
  ui.id = 'storeUi';
  ui.className = 'hidden';
  (document.getElementById('live') || document.body).appendChild(ui);
  ui.addEventListener('click', onClick);
  return { enter, active: () => data != null || pending != null, pick, leave, walk: (i) => go(i), counter: () => openCounter(), trade: showTrade };
}

let entering = false;
async function enter(id) {
  if (data || pending || entering) return;
  let d = null;
  entering = true;
  try { d = JSON.parse(await my.visit(id)); } catch { d = null; } finally { entering = false; }
  if (!d) { say?.('That store is not open for you yet. Do your care jobs to level up!'); return; }
  try { localStorage.setItem(LAST, id); } catch { /* private mode */ }
  cue?.('TICK');
  // the tank is built while My tank keeps running; when it is ready the walk in starts (arrive)
  pending = { id, d };
  say?.(`Heading to ${d.label}...`);
  startWarm();
}

/** The store's first tank is ready and the 3D view is on the sidewalk: the shop UI comes up. */
function arrive(d) {
  data = d; at = 0; picked = null; bag = []; counterOpen = false; sellOpen = false;
  document.body.classList.add('instore');
  ui.classList.remove('hidden');
  cue?.('TICK');
  say?.(`${d.greeting} Tap a tank to walk over to it, and the counter to pay.`);
  offer = null;
  render();
  startTalk();
  // the bell on the door rings as the walk in from the sidewalk reaches it
  setTimeout(() => { if (data) cue?.('BELL'); }, matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 1500);
}

function abandon(msg) {
  pending = null;
  try { my.visitCancel(); } catch { /* nothing to cancel */ }
  if (msg) say?.(msg);
}

function startTalk() {
  if (talk.raf) return;
  if (!talk.layer) { talk.layer = document.createElement('div'); talk.layer.className = 'tbubbles'; document.getElementById('tank').after(talk.layer); }
  const frame = () => {
    if (!data) { stopTalk(); return; }
    talk.raf = requestAnimationFrame(frame);
    let list = [];
    try { list = JSON.parse(my.storeBubbles()); } catch { list = []; }
    const cnv = document.getElementById('tank'), k = cnv.clientWidth / Math.max(1, cnv.width);
    while (talk.els.length < list.length) { const el = document.createElement('div'); el.className = 'tbubble'; el.style.maxWidth = '200px'; talk.layer.appendChild(el); talk.els.push(el); }
    talk.els.forEach((el, i) => {
      const b = list[i];
      if (!b) { el.style.display = 'none'; return; }
      el.style.display = '';
      if (el.textContent !== b.text) el.textContent = b.text;
      el.style.left = `${Math.max(100, Math.min(cnv.clientWidth - 100, b.x * k))}px`;
      el.style.top = `${Math.max(40, b.y * k - 10)}px`;
      el.style.opacity = b.a.toFixed(2);
    });
  };
  frame();
}

function stopTalk() {
  cancelAnimationFrame(talk.raf); talk.raf = 0;
  for (const el of talk.els) el.remove();
  talk.els = [];
}

/** A store regular walked up with a swap (core StoreTrades): the trade card. */
async function showTrade() {
  if (!data) return;
  try { offer = JSON.parse(await my.trade()); } catch { offer = null; }
  if (!data) return;
  if (!offer) return;
  cue?.('TICK');
  render();
}

function answerTrade(yes) {
  const o = offer;
  offer = null;
  my.answerTrade(yes);
  cue?.('TICK');
  if (yes && o) say?.(`Traded! Your ${o.get} is floating in a bag at the top of your tank.`);
  render();
}

function tradeCard(o) {
  return `<div class="caremodal"><div class="carecard glass trade">
    <h3>${esc(o.trader)} wants to trade!</h3>
    <p>“${esc(o.line)}”</p>
    <p><b class="give">You give:</b> ${o.frag ? `a frag of your ${esc(o.give)} (it stays in your tank, a little smaller)` : `one ${esc(o.give)}`}</p>
    <p><b class="get">You get:</b> a ${esc(o.get)}, in a bag at the top of your tank</p>
    <div class="row-gap"><button class="pill" data-strade="no">No thanks</button><button class="pill collect" data-strade="yes">Trade!</button></div>
  </div></div>`;
}

/** The checkout: the camera walks to the counter in 3D and the till opens. */
function openCounter() {
  if (!data) return;
  counterOpen = true;
  picked = null;
  my.storeCounter(true);
  cue?.('TICK');
  render();
}

function closeCounter() {
  counterOpen = false;
  my.storeCounter(false);
  render();
}

function leave(quiet) {
  if (pending) { abandon(); if (!quiet) say?.('Back home. Come again soon!'); onLeave(); return; }
  if (!data) return;
  my.leaveStore();
  data = null; bag = []; picked = null; counterOpen = false; offer = null;
  stopTalk();
  document.body.classList.remove('instore');
  ui.classList.add('hidden');
  ui.innerHTML = '';
  if (!quiet) say?.('Back home. Come again soon!');
  onLeave();
}

async function refresh() { try { const d = JSON.parse(await my.visitItems()); if (d && data) data = d; } catch { /* keep */ } }

function go(i) {
  if (!data) return;
  const n = data.tanks.length;
  at = (i + n) % n;
  picked = null;
  cue?.('TICK');
  render();
  // a tank that is not built yet (the row is built ahead as you walk along it) comes in a moment
  const want = at;
  const show = async () => {
    if (!data || want !== at) return;
    let ok = false;
    try { ok = await my.showTank(want); } catch (e) { console.error(e); ok = true; }
    if (!ok) { startWarm(); requestAnimationFrame(show); } else startWarm();
  };
  show();
}

/** An animal tapped in the display tank: its card. */
async function pick(id) {
  if (!data || !id) return;
  const sp = await my.displaySpecies(id);
  if (sp) { picked = sp; render(); }
}

const tank = () => data.tanks[at];
const SUP = 'supply:';
/** An animal card, or a supply pack ("supply:<id>" in the bag) from behind the counter. */
const item = (id) => id.startsWith(SUP) ? supply(id.slice(SUP.length)) : data.tanks.flatMap((t) => t.items).find((x) => x.id === id);
const supply = (id) => { const s = data.supplies?.find((x) => x.id === id); return s && { ...s, left: s.keeps ? (s.owned ? 0 : 1) : 99 }; };
const inBag = (id) => bag.filter((x) => x === id).length;
const total = () => bag.reduce((s, id) => s + (item(id)?.price || 0), 0);

function add(id) {
  const it = item(id);
  if (!it || it.locked) return;
  if (inBag(id) >= it.left) { say?.(it.keeps ? `You only need one ${it.name.toLowerCase()}.` : `That is all the ${it.name} they have today.`); return; }
  bag.push(id);
  cue?.('TICK');
  const b = ui.querySelector('.bagbtn');
  b?.classList.remove('plop'); void b?.offsetWidth; b?.classList.add('plop');
  render();
}

function remove(id) { const k = bag.lastIndexOf(id); if (k >= 0) bag.splice(k, 1); render(); }

let paying = false;
async function pay() {
  if (paying) return;
  let r = null;
  paying = true;
  try { r = JSON.parse(await my.checkout(JSON.stringify(bag))); } catch { r = null; } finally { paying = false; }
  if (!r) return;
  if (r.result !== 'OK') {
    const why = { TOO_POOR: 'Not enough sand dollars yet.', SOLD_OUT: 'Someone bought the last one!', LEVEL_LOCKED: 'That one needs a higher level.' };
    say?.(why[r.result] || 'The till got stuck. Try again.');
    await refresh(); render();
    return;
  }
  payFx(r.total);
  if (!r.animals) {
    // only supplies: they are on the shelf at home now, and the keeper keeps shopping
    bag = [];
    await refresh(); render();
    cue?.('TICK');
    say?.(`Paid ${r.total}! Your supplies are waiting on the shelf at home.`);
    return;
  }
  const n = r.animals;
  data = null; bag = []; counterOpen = false;
  document.body.classList.remove('instore');
  ui.classList.add('hidden'); ui.innerHTML = '';
  cue?.('FEED');
  say?.(`Paid ${r.total}! ${r.supplies ? 'Your supplies are on the shelf, and ' : ''}${n === 1 ? (r.supplies ? 'your new animal is' : 'Your new animal is') : `${r.supplies ? 'your' : 'Your'} ${n} new animals are`} floating in bags at the top of the tank. They come out when the water in the bag is warm.`);
  onLeave();
}

/** The reward burst run backwards: sand dollars hop out of the money chip into the till. */
function payFx(amount) {
  const chip = document.querySelector('.money');
  if (!chip || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const r = chip.getBoundingClientRect();
  const sx = r.left + 16, sy = r.top + r.height / 2, tx = innerWidth / 2, ty = innerHeight * 0.55;
  const coins = Math.min(10, 3 + Math.round(amount / 25));
  for (let i = 0; i < coins; i++) {
    const c = document.createElement('div');
    c.className = 'fxcoin';
    c.innerHTML = SAND_DOLLAR;
    document.body.appendChild(c);
    const mx = (sx + tx) / 2 + (Math.random() - 0.5) * 160, my2 = Math.min(sy, ty) - 60 - Math.random() * 60;
    const frames = [0, 0.25, 0.5, 0.75, 1].map((t) => {
      const x = (1 - t) ** 2 * sx + 2 * (1 - t) * t * mx + t * t * tx, y = (1 - t) ** 2 * sy + 2 * (1 - t) * t * my2 + t * t * ty;
      return { transform: `translate(${x - 13}px, ${y - 13}px) scale(${1 - 0.4 * t}) rotate(${t * 300}deg)`, opacity: t > 0.85 ? 0 : 1 };
    });
    c.animate(frames, { duration: 700, delay: i * 60, easing: 'ease-in', fill: 'forwards' }).onfinish = () => c.remove();
  }
  const m = document.createElement('div');
  m.className = 'fxplus minus';
  m.textContent = `−${amount}`;
  m.style.left = `${sx}px`; m.style.top = `${sy + 20}px`;
  document.body.appendChild(m);
  m.animate([{ transform: 'translate(-50%,0)', opacity: 1 }, { transform: 'translate(-50%,40px)', opacity: 0 }], { duration: 1100, easing: 'ease-out' }).onfinish = () => m.remove();
}

function render() {
  if (!data) return;
  const t = tank();
  const coins = data.coins;
  const sum = total();
  const p = picked && item(picked);
  ui.innerHTML = `
    <div class="sthead glass">
      <button class="sback" data-sleave aria-label="Leave the store and go home">‹ Back</button>
      <div class="stname"><b>${esc(data.label)}</b><small>${esc(t.title)} · ${t.animals} animals</small></div>
      <button class="pill sellbtn" data-ssell>Sell</button>
    </div>
    <div class="sttabs">${data.tanks.map((x, i) => `<button class="pillbtn ${i === at ? 'on' : ''}" data-stank="${i}">${esc(x.title)}</button>`).join('')}</div>
    ${data.tanks.length > 1 ? `<button class="starrow l" data-sgo="-1" aria-label="Previous tank">◀</button><button class="starrow r" data-sgo="1" aria-label="Next tank">▶</button>` : ''}
    ${p ? card(p, coins) : ''}
    <div class="ststrip">${t.items.map((it) => `<button class="stitem ${it.locked ? 'locked' : ''} ${picked === it.id ? 'on' : ''}" data-spick="${it.id}">
      ${it.deal ? '<i class="rib deal">Deal</i>' : it.wanted ? '<i class="rib want">Wanted</i>' : ''}
      ${pic(it)}<span class="nm">${esc(it.name)}</span>
      <span class="pr">${it.locked ? `🔒 Lv ${it.level}` : `${SAND_DOLLAR}${it.price}`}</span>
      ${inBag(it.id) ? `<i class="inbag">${inBag(it.id)}</i>` : ''}</button>`).join('')}</div>
    <button class="bagbtn ${sum > coins ? 'poor' : ''}" data-scounter>🛍️ <b>${bag.length}</b>${bag.length ? ` · ${SAND_DOLLAR}${sum}` : ''}</button>
    ${counterOpen ? counter(coins, sum) : ''}
    ${sellOpen ? sellSheet() : ''}
    ${offer ? tradeCard(offer) : ''}`;
}

function card(p, coins) {
  const n = inBag(p.id);
  const can = !p.locked && n < p.left;
  const need = p.price - coins;
  return `<div class="stcard glass">
    <button class="x" data-sclose aria-label="Close">✕</button>
    <div class="row">${pic(p)}<div><h3>${esc(p.name)}</h3>
      <p class="price">${SAND_DOLLAR}<b>${p.price}</b>${p.deal ? ' <i class="rib deal">30% off today</i>' : ''}</p>
      <p class="muted">${p.left} in the shop today${n ? ` · ${n} in your bag` : ''}</p></div></div>
    ${p.locked ? `<p class="lockline">🔒 Unlocks at level ${p.level}. Keep doing your care jobs!</p>` : need > 0 ? `<p class="muted">You need ${need} more sand dollars.</p>` : ''}
    ${p.warning ? `<p class="warnline">⚠️ ${esc(p.warning)}</p>` : ''}
    <div class="row-gap">${n ? `<button class="pill" data-sremove="${p.id}">− One less</button>` : ''}
      <button class="pill collect" data-sadd="${p.id}" ${can ? '' : 'disabled'}>${p.locked ? 'Locked' : p.left <= 0 ? 'Sold out' : n >= p.left ? 'All in your bag' : 'Add to bag'}</button></div>
  </div>`;
}

function counter(coins, sum) {
  const rows = [...new Set(bag)].map((id) => {
    const it = item(id), n = inBag(id);
    return `<div class="crow">${pic(it)}<span class="nm">${esc(it.name)}</span>
      <button class="pm" data-sremove="${id}">−</button><b>${n}</b><button class="pm" data-sadd="${id}">+</button>
      <span class="pr">${SAND_DOLLAR}${it.price * n}</span></div>`;
  }).join('');
  const short = sum - coins;
  return `<div class="caremodal" data-scounterbg><div class="carecard glass counter">
    <h3>The counter</h3>${rows || '<p class="muted">Your bag is empty.</p>'}
    ${supplyShelf()}
    <div class="ctotal"><span>Total</span><b>${SAND_DOLLAR}${sum}</b></div>
    <p class="muted">You have ${coins}. After paying: <b>${Math.max(0, coins - sum)}</b></p>
    <div class="carefoot" style="gap:10px"><button class="pill" data-scounterclose>Keep shopping</button>
      <button class="pill collect" data-spay ${short > 0 || !bag.length ? 'disabled' : ''}>${short > 0 ? `Need ${short} more` : `Pay ${sum}`}</button></div>
  </div></div>`;
}

/** The supplies behind the counter (core SupplyBook): foods, salt, doses and tools for the shelf at home. */
function supplyShelf() {
  const rows = (data.supplies || []).map((s) => {
    const n = inBag(SUP + s.id);
    const have = s.keeps ? (s.owned ? 'You have one' : '') : `You have ${s.have}`;
    const btn = s.locked ? `<span class="pr">🔒 Lv ${s.level}</span>`
      : s.owned ? '<span class="pr owned">✓</span>'
      : `${n ? `<button class="pm" data-sremove="${SUP}${s.id}">−</button><b>${n}</b>` : ''}<button class="pm" data-sadd="${SUP}${s.id}" ${s.keeps && n ? 'disabled' : ''}>+</button><span class="pr">${SAND_DOLLAR}${s.price}</span>`;
    return `<div class="crow sup ${s.have === 0 && !s.keeps ? 'empty' : ''}"><span class="nm">${esc(s.name)}<br><small class="muted">${esc(s.pack)}${have ? ` · ${have}` : ''}</small></span>${btn}</div>`;
  }).join('');
  return `<details class="supplies" open><summary>Supplies for your shelf</summary>${rows}</details>`;
}

/** The Sell sheet (docs/ECONOMY.md §6): the store's offer for each of your animals, wanted ones first. */
function sellSheet() {
  let list = [];
  try { list = JSON.parse(my.peek.offers()) || []; } catch { list = []; }
  const rows = list.map((o) => {
    const why = o.tooSmallCm ? `Too little: grows to ${o.tooSmallCm.toFixed(1)} cm (now ${o.cm.toFixed(1)})` : o.amount ? '' : 'This store does not buy these';
    return `<div class="crow">${pic({ id: o.speciesId, group: o.group })}<span class="nm">${esc(o.name)}${o.wanted ? ' <i class="rib want">Wanted</i>' : ''}${why ? `<br><small class="muted">${why}</small>` : ''}</span>
      ${o.amount ? `<button class="pill collect" data-ssellid="${o.id}" data-spet="${o.pet ? esc(o.name) : ''}">${SAND_DOLLAR}${o.amount}</button>` : ''}</div>`;
  }).join('');
  return `<div class="caremodal" data-ssellbg><div class="carecard glass counter">
    <h3>Sell to ${esc(data.label)}</h3>
    <p class="muted">Grown-up, healthy animals are worth more. Wanted ones pay extra today.</p>
    ${rows || '<p class="muted">Nothing in your tank to sell yet.</p>'}
    <div class="carefoot"><button class="pill" data-ssellclose>Done</button></div></div></div>`;
}

async function sell(id, pet) {
  if (pet && !confirm(`Say goodbye to ${pet}?`)) return;
  const paid = await my.sell(id);
  if (paid > 0) { cue?.('TICK'); say?.(`Sold for ${paid} sand dollars! The store will take good care of it.`); await refresh(); }
  await my.offers();
  render();
}

function onClick(e) {
  const t = e.target.closest('[data-strade]');
  if (t) { answerTrade(t.dataset.strade === 'yes'); return; }
  const b = e.target.closest('[data-ssell],[data-ssellid],[data-ssellclose],[data-ssellbg],[data-sleave],[data-stank],[data-sgo],[data-spick],[data-sadd],[data-sremove],[data-sclose],[data-scounter],[data-scounterclose],[data-spay],[data-scounterbg]');
  if (!b) return;
  const d = b.dataset;
  if (d.ssellid) sell(d.ssellid, d.spet);
  else if ('ssell' in d) { sellOpen = true; cue?.('TICK'); my.offers().then(render, render); }
  else if ('ssellclose' in d || ('ssellbg' in d && e.target === b)) { sellOpen = false; render(); }
  else if ('sleave' in d) leave();
  else if (d.stank != null) go(Number(d.stank));
  else if (d.sgo) go(at + Number(d.sgo));
  else if (d.spick) { picked = picked === d.spick ? null : d.spick; cue?.('TICK'); render(); }
  else if (d.sadd) add(d.sadd);
  else if (d.sremove) remove(d.sremove);
  else if ('sclose' in d) { picked = null; render(); }
  else if ('scounter' in d) openCounter();
  else if ('scounterclose' in d) closeCounter();
  else if ('spay' in d) pay();
  else if ('scounterbg' in d && e.target === b) closeCounter();
}
