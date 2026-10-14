#!/usr/bin/env node
/*
 * Headless preview of the live-tank renderer in WebGL2 with the REAL shaders from app/src/main/assets/shaders
 * (includes resolved and defines injected after #version as ShaderProgram.preprocess does).
 *
 * --scene full (default) follows TankRenderer's frame: shadow-map pass (ShadowMap.kt, when shadow.frag exists),
 * tank.glb, rockscape, equipment, reef_details, the demo corals (soft bodies + instanced polyps), a few fish in a
 * static straight pose (fish.vert spline skinning), fins, light shafts, water surface, glass, then PostFx (bloom on
 * High, composite.frag). --scene rocksand draws only rock + sand. The std140 Frame UBO is filled like
 * FrameUniforms.fill (constants from Material.kt, LightingState.kt, Camera.kt); textures are bound as TankRenderer
 * does; no sim inputs (no surface heightfield, sand map, algae or casters). 2x supersampling stands in for MSAA.
 * It doubles as a GLSL ES 3.00 compile check of every program it builds (plus particles / snow): errors are printed
 * and the exit code is 1.
 *
 * Usage (Chromium + Playwright are preinstalled; no `playwright install`):
 *   node tools/graphics/shader_preview/render.cjs [--out DIR] [--scene full|rocksand] [--light day|dusk|night|all]
 *        [--quality 0|1|2] [--views front,wide,rock,sand,fish] [--time SECONDS] [--shaders DIR]
 * Writes DIR/<view>_<light>.png (default DIR = build/shader_preview, light = day, quality = 2 / High).
 * --shaders points at an alternate shader folder (e.g. a copy of the old shaders for before/after shots).
 * `--check` only compiles quality 0, 1 and 2 and exits.
 * Playwright is resolved from local node_modules, then the global npm root; Chromium from PLAYWRIGHT_BROWSERS_PATH
 * (default /opt/pw-browsers) when the bundled revision is missing.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const http = require('http');
const { execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '../..', '..');
const ASSETS = path.join(ROOT, 'app/src/main/assets');

function arg(name, def) {
  const i = process.argv.indexOf('--' + name);
  return i > 0 && i + 1 < process.argv.length ? process.argv[i + 1] : def;
}

function loadPlaywright() {
  try { return require('playwright'); } catch (_) { /* fall through */ }
  const g = execSync('npm root -g').toString().trim();
  return require(path.join(g, 'playwright'));
}

function findChromium() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  if (!fs.existsSync(base)) return undefined;
  for (const d of fs.readdirSync(base).filter((n) => /^chromium-\d+$/.test(n)).sort().reverse()) {
    for (const rel of ['chrome-linux/chrome', 'chrome-linux64/chrome']) {
      const p = path.join(base, d, rel);
      if (fs.existsSync(p)) return p;
    }
  }
  return undefined;
}

// --- LightingState.from (pure port) -------------------------------------------------------------------------------
const CHANNEL_RGB = [[0.85, 0.93, 1.0], [0.12, 0.60, 1.0], [0.14, 0.34, 1.0], [1.0, 0.3, 0.3], [0.3, 1.0, 0.54],
  [0.42, 0.24, 1.0], [0.35 * 0.5, 0.2 * 0.5, 0.9 * 0.5], [0.17 * 0.15, 0.23 * 0.15, 0.43 * 0.15]];
const WEIGHTS = [4, 4, 4, 1, 1, 1, 1, 1].map((w) => w / 16);
const FOG_TINT = [0x0E / 255, 0x3B / 255, 0x5A / 255];
const FOG_FLOOR = [0x02 / 255, 0x06 / 255, 0x0C / 255];
function lighting(ch) {
  const c = ch.map((v) => Math.min(Math.max(v, 0), 1));
  const rawI = Math.min(c.reduce((s, v, i) => s + v * WEIGHTS[i], 0), 1);
  const night = rawI < 0.03;
  const use = night ? c.map((v, i) => (i === 7 ? v : 0)) : c;
  const key = [0, 0, 0];
  for (let i = 0; i < 8; i++) for (let k = 0; k < 3; k++) key[k] += use[i] * CHANNEL_RGB[i][k] * WEIGHTS[i];
  const intensity = night ? Math.max(rawI, c[7] * WEIGHTS[7]) : rawI;
  const kmax = Math.max(key[0], key[1], key[2], 1e-4);
  const blue = c[1] + c[2] + c[5];
  return {
    intensity, night,
    ambient: key.map((v) => v * 0.18),
    fog: key.map((v, i) => v / kmax * FOG_TINT[i] * intensity + FOG_FLOOR[i]),
    blueFraction: Math.min(Math.max(blue / (c[0] + blue + 1e-4), 0), 1),
    lightColor: key.map((v) => v * (intensity / kmax)),
  };
}
// AI Soft Coral default schedule (core LightSchedule): midday plateau and the 2 % moon at night.
const LIGHTS = { day: [0.45, 0.60, 0.65, 0.20, 0.20, 0.45, 0.35, 0], dusk: [0.10, 0.35, 0.45, 0.05, 0.05, 0.35, 0.20, 0],
  night: [0, 0, 0, 0, 0, 0, 0, 0.02] };

// --- camera (Camera.kt: fov 50, near = 3 % of the aim distance clamped 0.25..1, far 800) -------------------------
const TANK = [121.9, 61, 33];
function fitDistance(aspect) {
  const t = Math.tan(25 * Math.PI / 180);
  const front = TANK[2] - 16;
  return Math.min(Math.max(front + Math.max(TANK[1] * 1.04 / (2 * t), TANK[0] * 1.04 / (2 * t * aspect)), 80), 150);
}
const VIEWS = {
  // default orbit (target DEFAULT_TARGET, pitch DEFAULT_PITCH, fitted distance), landscape phone aspect
  front: (() => { const w = 1200, h = 600, d = fitDistance(w / h), p = 0.08;
    return { w, h, eye: [61, 27 + d * Math.sin(p), 16 + d * Math.cos(p)], aim: [61, 27, 16] }; })(),
  // zoomed out and pitched up: fixtures, water surface and the tank top in frame
  wide: { w: 1200, h: 800, eye: [61, 60, 175], aim: [61, 40, 16] },
  rock: { w: 800, h: 600, eye: [30, 24, 52], aim: [30, 14, 20] },
  sand: { w: 800, h: 600, eye: [70, 14, 42], aim: [62, 3, 22] },
  fish: { w: 800, h: 600, eye: [66, 26, 52], aim: [74, 22, 21] },
  // close-up of the sand cross-section through the front glass (skirt against the pane)
  front_glass_sand: { w: 800, h: 600, eye: [52, 6, 50], aim: [56, 2.5, 33] },
  // under the left AI Prime, looking up past the water line at the lens
  fixture: { w: 800, h: 600, eye: [40, 66, 44], aim: [32, 89, 16] },
  // close-ups (zoomed-in orbit / follow cam): a clown, the tang and the rock face from a few cm away
  close_clown: { w: 800, h: 600, eye: [74, 27, 34], aim: [70, 24, 22] },
  close_tang: { w: 800, h: 600, eye: [50, 38, 32], aim: [44, 34, 18] },
  close_rock: { w: 800, h: 600, eye: [32, 18, 36], aim: [30, 14, 20] },
};

function lookAt(e, c) {
  const f = norm(sub(c, e)), s = norm(cross(f, [0, 1, 0])), u = cross(s, f);
  return [s[0], u[0], -f[0], 0, s[1], u[1], -f[1], 0, s[2], u[2], -f[2], 0, -dot(s, e), -dot(u, e), dot(f, e), 1];
}
function perspective(fovy, a, n, f) {
  const t = 1 / Math.tan(fovy * Math.PI / 360);
  return [t / a, 0, 0, 0, 0, t, 0, 0, 0, 0, (f + n) / (n - f), -1, 0, 0, 2 * f * n / (n - f), 0];
}
function mul(a, b) { // column-major a * b
  const o = new Array(16).fill(0);
  for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) for (let k = 0; k < 4; k++) o[c * 4 + r] += a[k * 4 + r] * b[c * 4 + k];
  return o;
}
function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
function norm(a) { const l = Math.hypot(...a); return a.map((v) => v / l); }

// FrameUniforms.fill: 3 mat4 + 12 vec4 + 4 algae vec4 + uFx + 12 casters + uShadowMat + uShadow = 184 floats.
function frameData(view, light, quality, time, shadow) {
  const d = new Array(184).fill(0);
  const aimDist = Math.hypot(...sub(view.aim, view.eye));
  const near = Math.min(Math.max(aimDist * 0.03, 0.25), 1);
  const V = lookAt(view.eye, view.aim), P = perspective(50, view.w / view.h, near, 800);
  V.forEach((v, i) => { d[i] = v; }); P.forEach((v, i) => { d[16 + i] = v; }); mul(P, V).forEach((v, i) => { d[32 + i] = v; });
  d[48] = view.eye[0]; d[49] = view.eye[1]; d[50] = view.eye[2]; d[51] = time;
  const cosHalf = Math.cos(60 * Math.PI / 180);
  const LP = [[32, 90, 16], [91, 90, 16]];
  for (let i = 0; i < 2; i++) {
    d[52 + i * 4] = LP[i][0]; d[53 + i * 4] = LP[i][1]; d[54 + i * 4] = LP[i][2]; d[55 + i * 4] = cosHalf;
    d[60 + i * 4] = light.lightColor[0]; d[61 + i * 4] = light.lightColor[1]; d[62 + i * 4] = light.lightColor[2]; d[63 + i * 4] = light.intensity;
  }
  d[68] = light.ambient[0]; d[69] = light.ambient[1]; d[70] = light.ambient[2]; d[71] = light.blueFraction;
  d[72] = light.fog[0]; d[73] = light.fog[1]; d[74] = light.fog[2]; d[75] = 0.005;
  d[76] = 56; d[77] = 0.85; d[78] = 0.04; d[79] = quality;
  d[80] = light.night ? 1 : 0; d[81] = 0; d[82] = 1; d[83] = 3;
  d[84] = 0.0072; d[85] = 0.0016; d[86] = 0.0010; d[87] = 0.0004;
  d[88] = TANK[0]; d[89] = TANK[1]; d[90] = TANK[2];
  // 92..95 surface dims (no heightfield), 96..111 algae cover (EMPTY)
  d[113] = quality === 2 ? 1.3 : 1; d[114] = quality === 2 ? 1 : 0; d[115] = quality === 0 ? 0.5 : 1; // setEffects
  if (shadow && shadow.on) { // ShadowMap.fill
    shadow.matrix.forEach((v, i) => { d[164 + i] = v; });
    d[180] = 1; d[181] = shadow.cmPerTexel; d[182] = 0.85; d[183] = 0.12;
  }
  return d;
}

// --- tiny static server over the repo (the page fetches GLBs, PNGs and shader sources) ----------------------------
function serve(shaderDir) {
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png', '.glb': 'model/gltf-binary' };
  const srv = http.createServer((req, res) => {
    let u = decodeURIComponent(req.url.split('?')[0]);
    let file;
    if (u.startsWith('/shaders/')) file = path.join(shaderDir, path.basename(u));
    else if (u.startsWith('/assets/')) file = path.join(ASSETS, path.normalize(u.slice(8)).replace(/^(\.\.[/\\])+/, ''));
    else file = path.join(__dirname, path.basename(u) || 'page.html');
    fs.readFile(file, (err, buf) => {
      if (err) { res.writeHead(404); res.end(); return; }
      res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'text/plain' });
      res.end(buf);
    });
  });
  return new Promise((r) => srv.listen(0, '127.0.0.1', () => r(srv)));
}

async function main() {
  const out = path.resolve(arg('out', path.join(ROOT, 'build/shader_preview')));
  const shaderDir = path.resolve(arg('shaders', path.join(ASSETS, 'shaders')));
  const quality = parseInt(arg('quality', '2'), 10);
  const time = parseFloat(arg('time', '12.5'));
  const lightArg = arg('light', 'day');
  const lights = lightArg === 'all' ? ['day', 'dusk', 'night'] : lightArg === 'both' ? ['day', 'night'] : lightArg.split(',');
  const scene = arg('scene', 'full');
  const views = arg('views', scene === 'full' ? 'front,wide,rock,fish' : 'front,rock,sand').split(',');
  const check = process.argv.includes('--check');
  fs.mkdirSync(out, { recursive: true });

  const srv = await serve(shaderDir);
  const port = srv.address().port;
  const { chromium } = loadPlaywright();
  const opts = { args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] };
  let browser;
  try { browser = await chromium.launch(opts); } catch (e) {
    const exe = findChromium();
    if (!exe) throw e;
    browser = await chromium.launch({ ...opts, executablePath: exe });
  }
  const page = await browser.newPage();
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') console.error('[page]', m.text()); });
  if (check) {
    let bad = 0;
    for (const q of [0, 1, 2]) {
      await page.goto(`http://127.0.0.1:${port}/page.html`);
      const r = await page.evaluate((a) => window.preview.init(a), { quality: String(q), scene, compileOnly: true });
      for (const e of r.errors) console.error(e);
      bad += r.errors.length;
      console.log(`quality ${q}: ${r.errors.length ? 'FAILED' : 'all programs compile and link'}`);
    }
    await browser.close(); srv.close(); process.exit(bad ? 1 : 0);
  }
  await page.goto(`http://127.0.0.1:${port}/page.html`);
  const init = await page.evaluate((q) => window.preview.init(q), { quality: String(quality), scene });
  if (init.errors.length) {
    for (const e of init.errors) console.error(e);
    await browser.close(); srv.close(); process.exit(1);
  }
  console.log(`shaders OK (${scene}, quality ${quality}, shadow map ${init.shadow.on ? 'on' : 'off'}); parts:`, init.parts.join(', '));
  for (const l of lights) {
    for (const v of views) {
      const view = VIEWS[v];
      if (!view) { console.error('unknown view', v); continue; }
      const li = lighting(LIGHTS[l]);
      const png = await page.evaluate((a) => window.preview.render(a), { w: view.w, h: view.h, quality, light: li,
        frame: frameData(view, li, quality, time, init.shadow) });
      const f = path.join(out, `${v}_${l}.png`);
      fs.writeFileSync(f, Buffer.from(png.split(',')[1], 'base64'));
      console.log('wrote', path.relative(process.cwd(), f));
    }
  }
  await browser.close();
  srv.close();
}

main().catch((e) => { console.error(e); process.exit(1); });
