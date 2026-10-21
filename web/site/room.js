// My tank's room shop (core custom/room/Decor.kt via ReefMyTank.decor*; Android RoomShop.kt): stands, their colours,
// extras, room scenes, wallpapers and floors, bought with sand dollars. A tap on something new tries it on in the 3D view (the camera steps back to show
// the stand) with a Buy bar; a tap on something you own puts it to use.
import { SAND_DOLLAR } from './care.js';

const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

let my, live, say, cue, onChange, onTurn;
let bar = null;
let nav = null;
let navKey = '';
let trying = null;

export function initRoom(opts) {
  ({ my, live, say, cue } = opts);
  onChange = opts.onChange || (() => {});
  onTurn = opts.onTurn || (() => {});
  // the arrows round the room when the keeper has more than one tank
  nav = document.createElement('div');
  nav.id = 'roomNav';
  nav.className = 'roomnav hidden';
  (document.getElementById('live') || document.body).appendChild(nav);
  nav.addEventListener('click', (e) => {
    const b = e.target.closest('[data-turn]');
    if (!b) return;
    cue?.('TICK');
    if (my.turnStep(Number(b.dataset.turn))) {
      live.showRoom();
      let r = null;
      try { r = JSON.parse(my.roomTanks()); } catch { r = null; }
      const t = r?.tanks.find((x) => x.active);
      if (t) say?.(`Here's your ${t.gallons} gallon tank${t.name && t.name !== 'My tank' ? `, ${t.name}` : ''}!`);
      navKey = '';
      onTurn();
    }
  });
  bar = document.createElement('div');
  bar.id = 'tryBar';
  bar.className = 'trybar hidden';
  (document.getElementById('live') || document.body).appendChild(bar);
  bar.addEventListener('click', (e) => {
    if (e.target.closest('[data-trybuy]')) buy();
    else if (e.target.closest('[data-tryno]')) endTry(true);
  });
}

/** Shows the arrows round the room (more than one tank, [show] when nothing covers the tank); cheap to call often. */
export function roomNav(show) {
  if (!nav) return;
  let r = null;
  if (show) { try { r = JSON.parse(my.roomTanks()); } catch { r = null; } }
  const tanks = r?.tanks ?? [];
  const on = show && tanks.length > 1;
  const key = on ? JSON.stringify(r) : '';
  if (key === navKey) return;
  navKey = key;
  nav.classList.toggle('hidden', !on);
  if (!on) return;
  const i = tanks.findIndex((t) => t.active);
  const left = tanks[(i - 1 + tanks.length) % tanks.length];
  const right = tanks[(i + 1) % tanks.length];
  nav.innerHTML = `<button class="turn l" data-turn="-1" aria-label="Turn to your ${left.gallons} gallon tank"><span>‹</span><small>${left.gallons} gal</small></button>
    <button class="turn r" data-turn="1" aria-label="Turn to your ${right.gallons} gallon tank"><span>›</span><small>${right.gallons} gal</small></button>`;
}

/** A little drawing of a tank of [gallons] on its stand (the room shop's tank tiles). */
function tankPic(gallons) {
  const w = Math.min(40, 14 + gallons * 0.22), h = Math.min(18, 9 + gallons * 0.06), x = 24 - w / 2;
  return `<svg class="standpic" viewBox="0 0 48 33" aria-hidden="true"><rect x="${x}" y="${24 - h}" width="${w}" height="${h}" rx="1" fill="#2b6f8f" stroke="#cfeff8" stroke-width=".8"/>
    <rect x="${x + 1}" y="${24 - h * 0.3}" width="${w - 2}" height="${h * 0.3 - 0.8}" fill="#e3d2a8" opacity=".8"/><text x="24" y="${22 - h * 0.4}" font-size="7" text-anchor="middle">🐠</text>
    <rect x="${x - 1}" y="24" width="${w + 2}" height="8" fill="#f1ede6"/></svg>`;
}

/** A little drawing of a stand in its colours (the shop tile's picture). */
function standPic(kind, paint, trim) {
  const top = `<rect x="2" y="6" width="44" height="4" rx="1" fill="${trim}"/>`;
  const tank = '<rect x="5" y="0" width="38" height="6" fill="#2b6f8f" opacity=".55"/>';
  const body = {
    classic: `<rect x="4" y="10" width="40" height="22" fill="${paint}"/><path d="M6 12 L18 30 M18 12 L6 30" stroke="${trim}" stroke-width="1.6" opacity=".5"/><rect x="21" y="12" width="21" height="8" fill="#0003"/><rect x="21" y="22" width="21" height="8" fill="#0003"/>`,
    shaker: `<rect x="4" y="10" width="40" height="20" fill="${paint}"/><rect x="7" y="13" width="15" height="14" fill="none" stroke="#0003"/><rect x="26" y="13" width="15" height="14" fill="none" stroke="#0003"/><rect x="6" y="30" width="36" height="2" fill="#0005"/>`,
    iron: `<rect x="4" y="10" width="3" height="22" fill="${paint}"/><rect x="41" y="10" width="3" height="22" fill="${paint}"/><rect x="4" y="19" width="40" height="2" fill="${trim}"/><rect x="4" y="29" width="40" height="2" fill="${trim}"/><rect x="10" y="22" width="7" height="7" fill="#eee"/>`,
    modern: `<rect x="4" y="10" width="40" height="19" rx="1" fill="${paint}"/><path d="M17.5 11v18M30.5 11v18" stroke="#0004"/><rect x="6" y="29" width="36" height="3" fill="#1118"/>`,
    rustic: `<rect x="3" y="10" width="42" height="22" fill="${paint}"/><path d="M8 11v20M13 11v20M18 11v20M30 11v20M35 11v20M40 11v20" stroke="#0002"/><path d="M6 28 L22 14 M26 14 L42 28" stroke="${trim}" stroke-width="2" opacity=".45"/>`,
    reef_pro: `<rect x="4" y="10" width="40" height="19" fill="${paint}"/><rect x="17" y="12" width="14" height="15" fill="#0b2a44"/><rect x="18" y="20" width="12" height="6" fill="#2a7fbf" opacity=".7"/><rect x="4" y="10" width="40" height="1" fill="#9fe8ff"/><rect x="5" y="29" width="38" height="3" fill="${trim}"/>`,
  }[kind] || `<rect x="4" y="10" width="40" height="22" fill="${paint}"/>`;
  return `<svg class="standpic" viewBox="0 0 48 33" aria-hidden="true">${tank}${body}${top}</svg>`;
}

const SCENE_ICON = { living: '🛋️', sunny: '🌳', beach: '🏖️', cabin: '❄️', blossom: '🌸', city: '🌃', space: '🪐' };

/** A little drawing of a room scene: its wall, a window onto its sky, the tank on its stand. */
function scenePic(id, sky, wall) {
  return `<svg class="standpic" viewBox="0 0 48 33" aria-hidden="true"><rect width="48" height="33" fill="${wall}"/>
    <rect x="9" y="3" width="30" height="20" rx="1.5" fill="${sky}" stroke="#fff8" stroke-width="1"/>
    <text x="24" y="17" font-size="11" text-anchor="middle">${SCENE_ICON[id] || '🏠'}</text>
    <rect x="0" y="29" width="48" height="4" fill="#0003"/><rect x="13" y="18" width="22" height="7" fill="#2b6f8f" opacity=".75"/>
    <rect x="12" y="25" width="24" height="6" fill="#f1ede6"/></svg>`;
}

const swatchRow = (tiles) => `<div class="swatches">${tiles.map((t) => `<button class="swatch ${t.inUse ? 'on' : ''} ${t.locked ? 'lock' : ''}" data-room="${t.key}" aria-label="${esc(t.label)}">
      <span class="sw" style="background:linear-gradient(135deg, ${t.paint} 0 62%, ${t.trim} 62% 100%)"></span><small>${esc(t.label)}</small>${t.owned ? '' : t.locked ? `<small>🔒 ${t.level}</small>` : `<small class="cost">${SAND_DOLLAR}${t.price}</small>`}</button>`).join('')}</div>`;

const price = (t) => t.owned ? (t.inUse ? '<span class="tag on">In use</span>' : '<span class="tag">Yours</span>')
  : t.locked ? `<span class="tag lock">🔒 Level ${t.level}</span>`
  : t.price === 0 ? '<span class="tag">Free</span>' : `<span class="tag cost">${SAND_DOLLAR}${t.price}</span>`;

/** The room section of My tank's Shop card. */
export function roomHtml() {
  let s = null;
  try { s = JSON.parse(my.decorShop()); } catch { s = null; }
  if (!s) return '';
  const stands = s.tiles.filter((t) => t.kind === 'stand');
  const colors = s.tiles.filter((t) => t.kind === 'color');
  const extras = s.tiles.filter((t) => t.kind === 'extra');
  const scenes = s.tiles.filter((t) => t.kind === 'scene');
  const walls = s.tiles.filter((t) => t.kind === 'wall');
  const floors = s.tiles.filter((t) => t.kind === 'floor');
  const tanks = s.tiles.filter((t) => t.kind === 'tank');
  const cur = stands.find((t) => t.inUse);
  const kind = (key) => key.split(':')[1];
  return `<h4>Your room</h4><p class="muted">Make the room round your tank your own. Tap anything to try it on.</p>
    <div class="rtiles">${scenes.map((t) => `<button class="rtile ${t.inUse ? 'on' : ''} ${t.locked ? 'lock' : ''}" data-room="${t.key}">
      ${scenePic(kind(t.key), t.paint, t.trim)}<b>${esc(t.label)}</b>${price(t)}</button>`).join('')}</div>
    <h4>Walls</h4>${swatchRow(walls)}
    <h4>Floor</h4>${swatchRow(floors)}
    <h4>More tanks</h4><p class="muted">Put another tank on a different wall and turn round the room to visit it.</p>
    <div class="rtiles">${tanks.map((t) => `<button class="rtile ${t.locked ? 'lock' : ''}" data-room="${t.key}">
      ${tankPic(Number.parseInt(t.label, 10) || 40)}<b>${esc(t.label)}</b>${price(t)}</button>`).join('')}</div>
    <h4>Stand</h4>
    <div class="rtiles">${stands.map((t) => `<button class="rtile ${t.inUse ? 'on' : ''} ${t.locked ? 'lock' : ''}" data-room="${t.key}">
      ${standPic(kind(t.key), t.paint, t.trim)}<b>${esc(t.label)}</b>${price(t)}</button>`).join('')}</div>
    <h4>${esc(cur ? cur.label : 'Stand')} colours</h4>
    ${swatchRow(colors)}
    <h4>Extras</h4><div class="rtiles">${extras.map((t) => `<button class="rtile ${t.inUse ? 'on' : ''} ${t.locked ? 'lock' : ''}" data-room="${t.key}">
      <span class="xpic">${kind(t.key) === 'glow' ? '💡' : '✨'}</span><b>${esc(t.label)}</b>${t.owned ? `<span class="tag ${t.inUse ? 'on' : ''}">${t.inUse ? 'On' : 'Off'}</span>` : price(t)}</button>`).join('')}</div>`;
}

/** A tap on a room tile ([key]); [closeCard] hides the card so the tank and stand show. Returns true when handled. */
export function roomClick(key, closeCard) {
  let s = null;
  try { s = JSON.parse(my.decorShop()); } catch { return false; }
  const t = s?.tiles.find((x) => x.key === key);
  if (!t) return false;
  cue?.('TICK');
  if (t.owned) {
    my.decorUse(key);
    live.showRoom();
    if (t.kind === 'extra') say?.(t.inUse ? `${t.label} off.` : `${t.label} on!`);
    else say?.(`${t.label} it is!`);
    onChange();
    return true;
  }
  if (t.locked) {
    let full = false;
    try { full = JSON.parse(my.roomTanks())?.tanks.length >= 4; } catch { full = false; }
    say?.(t.kind === 'tank' && full ? 'Every wall has a tank already!' : `That one opens at level ${t.level}. Keep doing your care jobs!`);
    return true;
  }
  trying = t;
  if (t.kind !== 'tank') { my.decorPreview(key); live.showRoom(); }
  closeCard();
  const afford = s.coins >= t.price;
  bar.innerHTML = `<span class="what"><b>${esc(t.label)}</b> ${SAND_DOLLAR}${t.price}</span>
    <button class="pillbtn on" data-trybuy ${afford ? '' : 'aria-disabled="true"'}>Buy</button><button class="pillbtn" data-tryno>Not now</button>`;
  bar.classList.remove('hidden');
  say?.(afford ? 'How does it look? Buy it to keep it.' : `You need ${t.price - s.coins} more sand dollars for this one.`);
  return true;
}

function buy() {
  if (!trying) return;
  let r = null;
  try { r = JSON.parse(my.decorBuy(trying.key)); } catch { r = null; }
  if (r?.result === 'OK') {
    cue?.('FEED');
    say?.(trying.kind === 'stand' ? `Your new ${trying.label.toLowerCase()} is in!` : trying.kind === 'scene' ? `Welcome to your ${trying.label.toLowerCase()}!`
      : trying.kind === 'tank' ? `Your new ${trying.label} tank is set up on another wall. Use the arrows to turn round the room!` : `${trying.label}: yours now!`);
    const tank = trying.kind === 'tank';
    endTry(false);
    if (tank) { live.showRoom(); navKey = ''; onTurn(); }
    onChange();
  } else if (r?.result === 'TOO_POOR') {
    say?.(`Not enough sand dollars yet. Do your care jobs to earn more!`);
  } else if (r?.result === 'ROOM_FULL') {
    say?.('Every wall has a tank already!');
    endTry(true);
  } else if (r?.result === 'LEVEL_LOCKED') {
    say?.('That one opens at a higher level.');
  } else endTry(true);
}

/** Ends a try-on; [revert] puts the room back as it was. */
export function endTry(revert) {
  if (revert && trying) my.decorPreview('');
  trying = null;
  bar?.classList.add('hidden');
}
