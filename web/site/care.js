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
  let shown = null;          // the number on the chip, counting up to view.coins
  const reduce = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---- "While you were away" (docs/ECONOMY.md §9; Android WelcomeBack.kt): the days race by, then what happened
  const away = document.createElement('div');
  away.className = 'caremodal hidden';
  away.id = 'awayModal';
  document.body.appendChild(away);
  let awayLoop = false;
  function awayFrame() {
    let a = null;
    try { a = JSON.parse(my.away()); } catch { a = null; }
    if (!a || !isMine()) { away.classList.add('hidden'); awayLoop = false; return; }
    away.classList.remove('hidden');
    const f = a.daysDone - Math.floor(a.daysDone), day = f < 0.5, k = day ? f / 0.5 : (f - 0.5) / 0.5;
    const arc = `<div class="dayarc ${day ? 'day' : 'night'}"><i class="orb" style="left:${(10 + 80 * k).toFixed(1)}%;bottom:${(18 + Math.sin(k * Math.PI) * 55).toFixed(1)}%"></i><b class="sea"></b></div>`;
    if (a.running) {
      away.innerHTML = `<div class="carecard glass">${arc}<h3>While you were away…</h3>
        <p class="lvl" style="color:#FFE14D">Day ${Math.floor(a.daysDone) + 1} of ${Math.max(1, Math.ceil(a.days))}</p>
        <div class="lvbar"><i style="width:${Math.max(3, a.progress * 100).toFixed(1)}%;background:#5FD7FF"></i></div>
        <p class="muted">Your tank kept living: fish got hungry, water changed, algae grew.</p></div>`;
      requestAnimationFrame(awayFrame);
      return;
    }
    awayLoop = false;
    away.innerHTML = `<div class="carecard glass">${arc}<h3>${esc(a.headline)}</h3>
      ${a.lines.map((l) => `<p>• ${esc(l)}</p>`).join('')}
      ${a.deaths.length ? `<h4>Goodbye</h4>${a.deaths.map((d) => `<p class="muted">${esc(d.name)} (${esc(d.cause)})</p>`).join('')}<p class="muted">The net will take them out. Feeding every day and the Care card jobs keep everyone healthy.</p>` : ''}
      <div class="carefoot" style="gap:10px"><button class="pill" id="awayLater">Later</button>${a.lines.length || a.deaths.length ? '<button class="pill collect" id="awayCare">Take care of it</button>' : ''}</div></div>`;
    away.querySelector('#awayLater').addEventListener('click', () => { cue?.('TICK'); my.dismissAway(); away.classList.add('hidden'); });
    away.querySelector('#awayCare')?.addEventListener('click', () => {
      cue?.('TICK'); my.dismissAway(); away.classList.add('hidden'); open = true; render(); modal.classList.remove('hidden');
    });
  }

  function poll() {
    if (!isMine()) { chip.classList.add('hidden'); if (open) close(); return; }
    if (!awayLoop && away.classList.contains('hidden') && my.away() !== 'null') { awayLoop = true; requestAnimationFrame(awayFrame); }
    let v = null;
    try { v = JSON.parse(my.economy()); } catch { v = null; }
    if (!v) { chip.classList.add('hidden'); return; }
    let delay = 0;
    for (const g of v.gains || []) {
      if (g.levelUp > 0) say(`Level ${g.levelUp}! ${g.opens}`.trim()); else say(`+${g.amount} sand dollars: ${g.label}`);
      setTimeout(() => { burst(g.amount); cue?.('TICK'); }, delay);
      if (g.levelUp > 0) setTimeout(() => levelUp(g), delay + 900);
      delay += 450;
    }
    const changed = JSON.stringify({ ...v, gains: [] }) !== JSON.stringify(view && { ...view, gains: [] });
    view = v;
    chip.classList.remove('hidden');
    if (changed) {
      if (shown === null || reduce()) shown = v.coins;
      chip.innerHTML = `${SAND_DOLLAR}<b>${shown}</b><span class="lv">★${v.level}</span>${v.attention ? '<i class="ping"></i>' : ''}`;
      if (shown !== v.coins) setTimeout(() => countTo(v.coins), (v.gains || []).length ? 1100 : 0);
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

  /** The chip's number rolls up (or down) to [to], then the chip gulps. */
  function countTo(to) {
    const from = shown, t0 = performance.now(), ms = 700;
    const step = (now) => {
      const k = Math.min(1, (now - t0) / ms);
      shown = Math.round(from + (to - from) * (1 - (1 - k) ** 3));
      const b = chip.querySelector('b');
      if (b) b.textContent = shown;
      if (k < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  function gulp() {
    chip.classList.remove('gulp'); void chip.offsetWidth; chip.classList.add('gulp');
  }

  /**
   * Sand dollars rise out of the tank in bubbles and swim along a curve into the chip; a "+15" floats up from the
   * middle. Web Animations only (no per-frame JS), so it costs nothing once it is over.
   */
  function burst(amount) {
    if (chip.classList.contains('hidden')) return;
    const end = chip.getBoundingClientRect();
    const tx = end.left + 18, ty = end.top + end.height / 2;
    const W = innerWidth, H = innerHeight;
    const sx = W / 2, sy = H * 0.62;
    const plus = document.createElement('div');
    plus.className = 'fxplus';
    plus.textContent = `+${amount}`;
    plus.style.left = `${sx}px`; plus.style.top = `${sy}px`;
    document.body.appendChild(plus);
    plus.animate([
      { transform: 'translate(-50%,-50%) scale(.6)', opacity: 0 },
      { transform: 'translate(-50%,-90%) scale(1.15)', opacity: 1, offset: 0.2 },
      { transform: 'translate(-50%,-260%) scale(1)', opacity: 0 },
    ], { duration: reduce() ? 600 : 1300, easing: 'ease-out' }).onfinish = () => plus.remove();
    if (reduce()) { gulp(); return; }
    const n = Math.min(8, 3 + Math.floor(amount / 10));
    let landed = 0;
    for (let i = 0; i < n; i++) {
      const c = document.createElement('div');
      c.className = 'fxcoin';
      c.innerHTML = SAND_DOLLAR;
      document.body.appendChild(c);
      const fx = sx + (Math.random() - 0.5) * W * 0.5, fy = sy + (Math.random() - 0.5) * 80;
      const cx = (fx + tx) / 2 + (Math.random() - 0.5) * 200, cy = Math.min(fy, ty) + 40;
      // a bezier from the water to the chip, sampled into keyframes with a wobble that calms as it arrives
      const frames = [];
      for (let k = 0; k <= 12; k++) {
        const t = k / 12, e = 1 - (1 - t) ** 2, u = 1 - e;
        const x = u * u * fx + 2 * u * e * cx + e * e * tx + Math.sin((t * 6 + i) * Math.PI) * 8 * (1 - e);
        const y = u * u * fy + 2 * u * e * cy + e * e * ty;
        const sc = t < 0.15 ? t / 0.15 : 1 - 0.4 * e;
        frames.push({ transform: `translate(${x}px,${y}px) translate(-50%,-50%) scale(${sc.toFixed(3)}) rotate(${Math.round(t * 540 + i * 40)}deg)`, opacity: t > 0.92 ? 0.4 : 1 });
      }
      const a = c.animate(frames, { duration: 1100, delay: i * 70, easing: 'linear', fill: 'backwards' });
      a.onfinish = () => { c.remove(); if (++landed === n) gulp(); else if (landed === 1) gulp(); };
    }
  }

  /** Level up: the water dims, bubbles and a school of fish rush past, a big star pops with the new level. */
  function levelUp(g) {
    const d = document.createElement('div');
    d.className = 'levelup';
    const bubbles = Array.from({ length: 28 }, () => {
      const r = 6 + Math.random() * 18;
      return `<i class="lb" style="left:${(Math.random() * 100).toFixed(1)}%;width:${r}px;height:${r}px;animation-duration:${(2 + Math.random() * 2).toFixed(2)}s;animation-delay:${(Math.random() * 1.2).toFixed(2)}s"></i>`;
    }).join('');
    const colors = ['#FFB13D', '#5FD7FF', '#FFE14D', '#FF7AA8'];
    const fish = colors.concat(colors.slice(0, 2)).map((col, i) =>
      `<svg class="lf" viewBox="0 0 40 24" style="top:${[22, 30, 38, 62, 70, 78][i]}%;animation-delay:${(i * 0.18).toFixed(2)}s"><path d="M38 12Q20 1 8 12Q20 23 38 12ZM10 12L1 4L1 20Z" fill="${col}" stroke="#1B1340" stroke-width="2" stroke-linejoin="round"/><circle cx="30" cy="10" r="2" fill="#1B1340"/></svg>`).join('');
    d.innerHTML = `${bubbles}${fish}<div class="lvbox"><div class="rays"></div>
      <svg class="lstar" viewBox="0 0 100 100"><path d="M50 4L61 37L96 37L68 58L78 92L50 71L22 92L32 58L4 37L39 37Z" fill="#FFE14D" stroke="#1B1340" stroke-width="5" stroke-linejoin="round"/><circle cx="40" cy="38" r="6" fill="#fff" opacity=".55"/></svg>
      <div class="lvtitle">Level ${g.levelUp}!</div>${g.opens ? `<div class="lvopens">${esc(g.opens)}</div>` : ''}<div class="lvtap">Tap to keep going</div></div>`;
    document.body.appendChild(d);
    const bye = () => { d.classList.add('out'); setTimeout(() => d.remove(), 350); };
    d.addEventListener('click', bye);
    setTimeout(bye, 3800);
  }

  function close() { open = false; modal.classList.add('hidden'); }
  chip.addEventListener('click', () => { cue?.('TICK'); open = true; render(); modal.classList.remove('hidden'); });
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  setInterval(poll, 1000);
  poll();
  return { poll, burst, levelUp };
}
