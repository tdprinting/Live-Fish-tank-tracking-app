// ReefLive web: My tank's sand dollars (docs/ECONOMY.md), mirroring the Android CareCard.kt: the money chip in the
// top bar and the Care card it opens (today's jobs, the bonuses, the level bar and the goals). The numbers and the
// rules live in Kotlin (core custom/economy via ReefMyTank.economy()); this file only draws them.

const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

/** A sand dollar: a pale disc with the five-petal flower (same drawing as Android drawSandDollar). */
export const SAND_DOLLAR = (() => {
  const petals = [0, 1, 2, 3, 4].map((i) => {
    const a = (i * 72 - 90) * Math.PI / 180;
    return `<line x1="12" y1="12" x2="${(12 + Math.cos(a) * 6.1).toFixed(2)}" y2="${(12 + Math.sin(a) * 6.1).toFixed(2)}"/>`;
  }).join('');
  return `<svg class="sanddollar" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10.2" fill="#F3E3C3" stroke="#1B1340" stroke-width="1.6"/>` +
    `<g stroke="#B8925A" stroke-width="1.8" stroke-linecap="round">${petals}</g><circle cx="12" cy="12" r="1.3" fill="#B8925A"/></svg>`;
})();

/**
 * Starts the chip and the card. [my] is ReefMyTank, [isMine] says whether the page shows My tank, [say] is Hermie,
 * [cue] plays a sound by name. The chip goes first in [host] (the top bar's right side).
 */
export function initCare({ my, isMine, say, cue, host }) {
  const chip = document.createElement('button');
  chip.className = 'money hidden';
  chip.id = 'moneyChip';
  chip.setAttribute('aria-label', 'Sand dollars and today\'s jobs');
  host.prepend(chip);
  const modal = document.createElement('div');
  modal.className = 'caremodal hidden';
  modal.id = 'careModal';
  document.body.appendChild(modal);
  let view = null;
  let open = false;

  function poll() {
    if (!isMine()) { chip.classList.add('hidden'); if (open) close(); return; }
    let v = null;
    try { v = JSON.parse(my.economy()); } catch { v = null; }
    if (!v) { chip.classList.add('hidden'); return; }
    for (const g of v.gains || []) {
      if (g.levelUp > 0) say(`Level ${g.levelUp}! ${g.opens}`.trim()); else say(`+${g.amount} sand dollars: ${g.label}`);
      cue?.('TICK');
    }
    const changed = JSON.stringify({ ...v, gains: [] }) !== JSON.stringify(view && { ...view, gains: [] });
    view = v;
    chip.classList.remove('hidden');
    if (changed) {
      chip.innerHTML = `${SAND_DOLLAR}<b>${v.coins}</b><span class="lv">★${v.level}</span>${v.attention ? '<i class="ping"></i>' : ''}`;
      if (open) render();
    }
  }

  function render() {
    const v = view;
    if (!v) return;
    const f = v.levelNeed > 0 ? Math.min(1, v.levelStars / v.levelNeed) : 1;
    const streak = v.streak > 0 || v.multiplier > 1
      ? `<p class="streak">🔥 ${v.streak} day${v.streak === 1 ? '' : 's'} in a row${v.multiplier > 1 ? ` · pay +${Math.round((v.multiplier - 1) * 100)} %` : ''}</p>` : '';
    const happy = v.happyPaid ? 'Collected today. Come back tomorrow!'
      : v.happyBonus > 0 ? `Your healthy animals earned ${v.happyBonus} today.` : 'Healthy animals and corals earn a bonus every day.';
    modal.innerHTML = `<div class="carecard glass" role="dialog" aria-label="Care card">
      <div class="carehead">${SAND_DOLLAR}<div><h3>${v.coins} sand dollars</h3><p class="muted">Take care of your tank to earn more</p></div></div>
      <p class="lvl">${v.levelNeed > 0 ? `Level ${v.level} · ${v.levelNeed - v.levelStars} stars to level ${v.level + 1}` : `Level ${v.level} · top level!`}</p>
      <div class="lvbar"><i style="width:${Math.max(3, f * 100).toFixed(1)}%"></i></div>
      <h4>Today's jobs</h4>
      ${v.tasks.map((t) => `<div class="job ${t.done ? 'done' : ''}"><span class="tick">${t.done ? '✓' : '○'}</span>
        <span class="what"><b>${esc(t.label)}</b>${t.done ? '' : `<small>${esc(t.hint)}</small>`}</span><span class="pay">+${t.pay}</span></div>`).join('')}
      <p>${v.allDonePaid ? `All done today! +${v.allDoneBonus} paid` : `Finish them all: +${v.allDoneBonus} more`}</p>
      ${streak}
      <h4>Happy tank bonus</h4>
      <div class="happyrow"><p>${happy}</p>${!v.happyPaid && v.happyBonus > 0 ? '<button class="pill collect" id="careCollect">Collect</button>' : ''}</div>
      <h4>Goals</h4>
      ${v.goals.map((g) => `<div class="goal ${g.done ? 'done' : ''}"><span>${g.done ? '★' : '☆'}</span><span class="what">${esc(g.label)}</span><span class="pay">+${g.reward}</span></div>`).join('')}
      <div class="carefoot"><button class="pill" id="careClose">Close</button></div>
    </div>`;
    modal.querySelector('#careClose').addEventListener('click', () => { cue?.('TICK'); close(); });
    modal.querySelector('#careCollect')?.addEventListener('click', () => { cue?.('TICK'); my.collectHappy(); poll(); render(); });
  }

  function close() { open = false; modal.classList.add('hidden'); }
  chip.addEventListener('click', () => { cue?.('TICK'); open = true; render(); modal.classList.remove('hidden'); });
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  setInterval(poll, 1000);
  poll();
  return { poll };
}
