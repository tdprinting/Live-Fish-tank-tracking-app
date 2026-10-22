// ReefLive web: the goodbye after the fish net lifts a dead animal out (mirrors the Android ToiletFlush.kt). A little
// bathroom card pops up, the net tips the body into the toilet bowl with a plop, the lever goes down and the water
// swirls it away, then "Bye bye, <name>!". Light and kid-friendly; tap anywhere to skip. Self-contained: own DOM + CSS.

const TOTAL = 4600, NET_IN_END = 800, TIP_END = 1050, DROP_END = 1350, LEVER_START = 1550;
const SWIRL_START = 1700, SWIRL_END = 3300, CAPTION_START = 3100, FADE_START = 4100;

const CSS = `
#flushLayer { position: absolute; inset: 0; z-index: 20; display: flex; flex-direction: column; align-items: center;
  justify-content: center; gap: 6px; background: rgba(0,0,0,0); cursor: pointer; user-select: none; -webkit-user-select: none; }
#flushLayer canvas { width: 260px; height: 260px; }
#flushLayer .bye { color: #F2F6FF; font: 700 24px/1.2 -apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif; margin-top: 8px; }
#flushLayer .sub { color: rgba(242,246,255,.8); font: 500 15px/1.3 -apple-system, BlinkMacSystemFont, sans-serif; }
`;

let layer = null, raf = 0;
const ease = (x) => x * x * (3 - 2 * x);
const clamp = (x) => Math.max(0, Math.min(1, x));
const hex = (n) => `#${(n >>> 0).toString(16).padStart(6, '0').slice(-6)}`;

/** Plays the goodbye for [f] = { name, isFish, body, accent } ; [hooks] = { onSplash, onFlush }. */
export function playFlush(f, hooks = {}) {
  stop();
  if (!document.getElementById('flushCss')) {
    const st = document.createElement('style'); st.id = 'flushCss'; st.textContent = CSS; document.head.appendChild(st);
  }
  const host = document.getElementById('live') || document.body;
  layer = document.createElement('div'); layer.id = 'flushLayer';
  layer.setAttribute('role', 'img'); layer.setAttribute('aria-label', `${f.name} is flushed away. Tap to close.`);
  const box = document.createElement('div');
  const cv = document.createElement('canvas');
  const dpr = Math.min(3, window.devicePixelRatio || 1);
  cv.width = cv.height = Math.round(260 * dpr);
  const bye = document.createElement('div'); bye.className = 'bye'; bye.textContent = `Bye bye, ${f.name}!`;
  const sub = document.createElement('div'); sub.className = 'sub'; sub.textContent = 'Off to the big blue sea 💙';
  box.style.cssText = 'display:flex;flex-direction:column;align-items:center;';
  box.append(cv, bye, sub); layer.append(box); host.append(layer);
  layer.addEventListener('click', stop);
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const speed = reduce ? TOTAL / 2200 : 1;
  const ctx = cv.getContext('2d');
  const t0 = performance.now();
  let splashed = false, pulled = false;
  const frame = (now) => {
    const ms = (now - t0) * speed;
    if (ms >= TOTAL) { stop(); return; }
    if (!splashed && ms >= DROP_END) { splashed = true; hooks.onSplash?.(); }
    if (!pulled && ms >= LEVER_START) { pulled = true; hooks.onFlush?.(); }
    const pin = ease(clamp(ms / 350)), pout = clamp((ms - FADE_START) / (TOTAL - FADE_START));
    const shown = pin * (1 - pout);
    layer.style.background = `rgba(0,0,0,${0.45 * shown})`;
    const pop = (0.6 + 0.4 * pin + 0.06 * Math.sin(Math.PI * pin)) * (1 - 0.15 * pout);
    box.style.transform = `scale(${pop})`; box.style.opacity = shown;
    const cap = clamp((ms - CAPTION_START) / 400);
    bye.style.opacity = cap; bye.style.transform = `translateY(${(1 - ease(cap)) * 12}px)`;
    sub.style.opacity = clamp((ms - CAPTION_START - 250) / 400);
    ctx.setTransform(dpr * 260, 0, 0, dpr * 260, 0, 0);   // draw in a unit square
    ctx.clearRect(0, 0, 1, 1);
    scene(ctx, ms, f.isFish, hex(f.body), hex(f.accent));
    raf = requestAnimationFrame(frame);
  };
  raf = requestAnimationFrame(frame);
}

function stop() {
  cancelAnimationFrame(raf); raf = 0;
  layer?.remove(); layer = null;
}

function rrect(c, x, y, w, h, r) { c.beginPath(); c.roundRect ? c.roundRect(x, y, w, h, r) : c.rect(x, y, w, h); }
function oval(c, x, y, w, h) { c.beginPath(); c.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, Math.PI * 2); }
function line(c, x0, y0, x1, y1, w, color, round = false) {
  c.beginPath(); c.moveTo(x0, y0); c.lineTo(x1, y1); c.lineWidth = w; c.strokeStyle = color; c.lineCap = round ? 'round' : 'butt'; c.stroke();
}

function scene(c, ms, isFish, body, accent) {
  // tiles and floor
  rrect(c, 0, 0, 1, 1, 0.08); c.fillStyle = '#CFE6F2'; c.fill();
  for (let k = 1; k <= 4; k++) { line(c, 0, k * 0.2, 1, k * 0.2, 0.006, '#B5D3E4'); line(c, k * 0.2, 0, k * 0.2, 0.62, 0.006, '#B5D3E4'); }
  c.fillStyle = '#A9C9DB'; c.fillRect(0, 0.62, 1, 0.38);
  // cistern and lever
  rrect(c, 0.27, 0.09, 0.46, 0.27, 0.05); c.fillStyle = '#DCE6EE'; c.fill();
  rrect(c, 0.28, 0.08, 0.44, 0.26, 0.05); c.fillStyle = '#FBFDFF'; c.fill(); c.lineWidth = 0.008; c.strokeStyle = '#8BA4B8'; c.stroke();
  const press = ms < LEVER_START ? 0 : ms < LEVER_START + 150 ? (ms - LEVER_START) / 150 : ms < LEVER_START + 650 ? 1 : Math.max(0, 1 - (ms - LEVER_START - 650) / 250);
  c.save(); c.translate(0.34, 0.15); c.rotate(-28 * ease(press) * Math.PI / 180); c.translate(-0.34, -0.15);
  line(c, 0.34, 0.15, 0.22, 0.15, 0.028, '#B8C4CE', true);
  c.beginPath(); c.arc(0.34, 0.15, 0.022, 0, Math.PI * 2); c.fillStyle = '#B8C4CE'; c.fill();
  c.restore();
  // pedestal, bowl, seat
  c.beginPath(); c.moveTo(0.38, 0.64); c.lineTo(0.62, 0.64); c.lineTo(0.6, 0.9); c.lineTo(0.4, 0.9); c.closePath();
  c.fillStyle = '#FBFDFF'; c.fill(); c.lineWidth = 0.008; c.strokeStyle = '#8BA4B8'; c.stroke();
  oval(c, 0.15, 0.43, 0.7, 0.38); c.fillStyle = '#DCE6EE'; c.fill();
  oval(c, 0.16, 0.41, 0.68, 0.36); c.fillStyle = '#FBFDFF'; c.fill(); c.lineWidth = 0.008; c.strokeStyle = '#8BA4B8'; c.stroke();
  // water, swirling once the lever goes down
  const swirlT = clamp((ms - SWIRL_START) / (SWIRL_END - SWIRL_START));
  const swirling = ms >= LEVER_START;
  c.save(); oval(c, 0.26, 0.47, 0.48, 0.24); c.clip();
  c.fillStyle = '#4FC3F7'; c.fillRect(0.26, 0.47, 0.48, 0.24);
  c.globalAlpha = swirling ? 0.25 + 0.55 * ease(swirlT) : 0.2; oval(c, 0.42, 0.55, 0.16, 0.08); c.fillStyle = '#1E88C8'; c.fill(); c.globalAlpha = 1;
  if (swirling) {
    const spin = (ms - LEVER_START) / 1000 * 2 * Math.PI * (1 + swirlT * 1.5);
    const fade = 1 - clamp((ms - SWIRL_END) / 500);
    c.save(); c.translate(0.5, 0.59); c.scale(1, 0.5); c.rotate(spin);
    for (let k = 0; k < 3; k++) {
      c.beginPath();
      for (let i = 0; i <= 24; i++) {
        const a = k * 2.09 + i * 0.17, rr = 0.2 - i * 0.0075;
        const x = rr * Math.cos(a), y = rr * Math.sin(a);
        if (i === 0) c.moveTo(x, y); else c.lineTo(x, y);
      }
      c.lineWidth = 0.012; c.lineCap = 'round'; c.strokeStyle = `rgba(255,255,255,${0.55 * fade})`; c.stroke();
    }
    c.restore();
  }
  const ring = (ms - DROP_END) / 500;
  if (ring >= 0 && ring <= 1) {
    const rr = 0.04 + 0.14 * ring;
    oval(c, 0.5 - rr, 0.59 - rr * 0.5, 2 * rr, rr); c.lineWidth = 0.01; c.strokeStyle = `rgba(255,255,255,${0.8 * (1 - ring)})`; c.stroke();
  }
  c.restore();
  oval(c, 0.26, 0.47, 0.48, 0.24); c.lineWidth = 0.006; c.strokeStyle = '#8BA4B8'; c.stroke();
  // the body: falling, floating, then round and round and gone
  let fish = null;
  if (ms >= TIP_END && ms < DROP_END) { const f = (ms - TIP_END) / (DROP_END - TIP_END); fish = [0.56 - 0.06 * f, 0.33 + 0.26 * f * f, 1, 40 + 140 * f]; }
  else if (ms >= DROP_END && ms < SWIRL_START) fish = [0.5, 0.59 + 0.008 * Math.sin((ms - DROP_END) / 90), 1, 180];
  else if (ms >= SWIRL_START && ms < SWIRL_END) {
    const s = ease(swirlT), a = swirlT * swirlT * 7 * Math.PI, rr = 0.15 * (1 - s);
    fish = [0.5 + rr * Math.cos(a), 0.59 + rr * 0.5 * Math.sin(a), 1 - s * 0.95, 180 + a * 57.3];
  }
  if (fish) drawBody(c, fish[0], fish[1], 0.11 * fish[2], fish[3], isFish, body, accent);
  // the net: in from the top right, tips, back out
  if (ms < LEVER_START) {
    const inT = ease(clamp(ms / NET_IN_END)), outT = ease(clamp((ms - DROP_END) / (LEVER_START - DROP_END)));
    const tip = ease(clamp((ms - NET_IN_END) / (TIP_END - NET_IN_END)));
    const cx = 1.15 - 0.55 * inT + 0.6 * outT, cy = -0.15 + 0.45 * inT - 0.5 * outT;
    c.save(); c.translate(cx, cy); c.rotate(-65 * tip * Math.PI / 180);
    line(c, 0.12, -0.02, 0.5, -0.3, 0.025, '#1F6E45', true);
    if (ms < TIP_END) drawBody(c, 0, 0.05, 0.11, 0, isFish, body, accent);
    c.beginPath(); c.moveTo(-0.12, 0); c.quadraticCurveTo(0, 0.2, 0.12, 0); c.closePath(); c.fillStyle = 'rgba(255,255,255,.28)'; c.fill();
    for (let i = -3; i <= 3; i++) line(c, i * 0.034, 0, i * 0.012, 0.1 * (1 - (i * i) / 12), 0.004, 'rgba(255,255,255,.6)');
    oval(c, -0.12, -0.03, 0.24, 0.06); c.lineWidth = 0.014; c.strokeStyle = '#2E9E62'; c.stroke();
    c.restore();
  }
  // bubbles after it is gone
  if (ms > SWIRL_END - 200 && ms < SWIRL_END + 900) {
    const b = (ms - SWIRL_END + 200) / 1100;
    for (let k = 0; k < 4; k++) {
      const bt = clamp(b * 1.4 - k * 0.15);
      if (bt <= 0 || bt >= 1) continue;
      c.beginPath(); c.arc(0.5 + 0.03 * Math.sin(k * 2.1 + bt * 6), 0.58 - 0.2 * bt, 0.012 + 0.006 * k, 0, Math.PI * 2);
      c.lineWidth = 0.005; c.strokeStyle = `rgba(255,255,255,${0.8 * (1 - bt)})`; c.stroke();
    }
  }
}

/** A cartoon fish (or a crab-ish blob for an invert) of half-length [r] at (x, y), rotated [deg], with X eyes. */
function drawBody(c, x, y, r, deg, isFish, body, accent) {
  c.save(); c.translate(x, y); c.rotate(deg * Math.PI / 180);
  const xeye = (ex, ey, k, w) => { line(c, ex - k, ey - k, ex + k, ey + k, w, '#1B1340', true); line(c, ex - k, ey + k, ex + k, ey - k, w, '#1B1340', true); };
  if (isFish) {
    c.beginPath(); c.moveTo(-r * 0.8, 0); c.lineTo(-r * 1.35, -r * 0.5); c.lineTo(-r * 1.25, 0); c.lineTo(-r * 1.35, r * 0.5); c.closePath();
    c.fillStyle = accent; c.fill();
    oval(c, -r, -r * 0.55, 2 * r, 1.1 * r); c.fillStyle = body; c.fill();
    line(c, r * 0.15, -r * 0.5, r * 0.15, r * 0.5, r * 0.16, accent);
    c.beginPath(); c.moveTo(-r * 0.3, -r * 0.5); c.lineTo(r * 0.05, -r * 0.85); c.lineTo(r * 0.3, -r * 0.48); c.closePath(); c.fillStyle = accent; c.fill();
    xeye(r * 0.6, -r * 0.12, r * 0.13, r * 0.08);
  } else {
    for (let i = -2; i <= 2; i++) line(c, i * r * 0.3, 0, i * r * 0.45, r * 0.75, r * 0.1, body, true);
    oval(c, -r * 0.8, -r * 0.5, 1.6 * r, r); c.fillStyle = body; c.fill();
    c.globalAlpha = 0.6; oval(c, -r * 0.5, -r * 0.35, r, 0.4 * r); c.fillStyle = accent; c.fill(); c.globalAlpha = 1;
    for (const s of [-1, 1]) xeye(s * r * 0.3, -r * 0.6, r * 0.1, r * 0.07);
  }
  c.restore();
}
