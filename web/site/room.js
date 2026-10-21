// My tank's room shop (core custom/room/Decor.kt via ReefMyTank.decor*; Android RoomShop.kt): stands, their colours and
// extras, bought with sand dollars. A tap on something new tries it on in the 3D view (the camera steps back to show
// the stand) with a Buy bar; a tap on something you own puts it to use.
import { SAND_DOLLAR } from './care.js';

const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

let my, live, say, cue, onChange;
let bar = null;
let trying = null;

export function initRoom(opts) {
  ({ my, live, say, cue } = opts);
  onChange = opts.onChange || (() => {});
  bar = document.createElement('div');
  bar.id = 'tryBar';
  bar.className = 'trybar hidden';
  (document.getElementById('live') || document.body).appendChild(bar);
  bar.addEventListener('click', (e) => {
    if (e.target.closest('[data-trybuy]')) buy();
    else if (e.target.closest('[data-tryno]')) endTry(true);
  });
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
  const cur = stands.find((t) => t.inUse);
  const kind = (key) => key.split(':')[1];
  return `<h4>Your room</h4><p class="muted">A stand for your tank. Tap one to try it on.</p>
    <div class="rtiles">${stands.map((t) => `<button class="rtile ${t.inUse ? 'on' : ''} ${t.locked ? 'lock' : ''}" data-room="${t.key}">
      ${standPic(kind(t.key), t.paint, t.trim)}<b>${esc(t.label)}</b>${price(t)}</button>`).join('')}</div>
    <h4>${esc(cur ? cur.label : 'Stand')} colours</h4>
    <div class="swatches">${colors.map((t) => `<button class="swatch ${t.inUse ? 'on' : ''} ${t.locked ? 'lock' : ''}" data-room="${t.key}" aria-label="${esc(t.label)}">
      <span class="sw" style="background:linear-gradient(135deg, ${t.paint} 0 62%, ${t.trim} 62% 100%)"></span><small>${esc(t.label)}</small>${t.owned ? '' : `<small class="cost">${SAND_DOLLAR}${t.price}</small>`}</button>`).join('')}</div>
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
  if (t.locked) { say?.(`That one opens at level ${t.level}. Keep doing your care jobs!`); return true; }
  trying = t;
  my.decorPreview(key);
  live.showRoom();
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
    say?.(trying.kind === 'stand' ? `Your new ${trying.label.toLowerCase()} is in!` : `${trying.label}: yours now!`);
    endTry(false);
    onChange();
  } else if (r?.result === 'TOO_POOR') {
    say?.(`Not enough sand dollars yet. Do your care jobs to earn more!`);
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
