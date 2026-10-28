// usage: node tools/qa/storewarm.mjs <siteDir> [fps=5] [label]
// env: QA_VERBOSE=1 (progress and page console on stderr), QA_PORT (default 0 = any free port), QA_URL (use a running server instead), QA_BROWSER (Chromium binary,
//      default /opt/pw-browsers/chromium), QA_NODE_MODULES (where `playwright` lives), GL=1 (really draw; default stubs GPU draws)
// Time from tapping a store on the street (My tank > Shop) to its shop UI, with requestAnimationFrame held to <fps>
// (a slow phone or a throttled tab). Run it on a build of main and on a build with the change and compare.
import { createRequire } from 'module';
import http from 'http';
import fs from 'fs';
import path from 'path';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const [siteDir, fpsArg = '5', label = 'run'] = process.argv.slice(2);
const FPS = +fpsArg;
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.css': 'text/css', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.png': 'image/png', '.wasm': 'application/wasm' };
const srv = http.createServer((q, r) => {
  let f = path.join(siteDir, decodeURIComponent(q.url.split('?')[0]));
  if (f.endsWith('/')) f += 'index.html';
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); });
});
await new Promise((ok) => srv.listen(+(process.env.QA_PORT || 0), '127.0.0.1', ok));
const url = process.env.QA_URL || `http://127.0.0.1:${srv.address().port}/`;
const b = await chromium.launch({ executablePath: process.env.QA_BROWSER || '/opt/pw-browsers/chromium', args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--disable-dev-shm-usage', '--disable-renderer-backgrounding', '--disable-background-timer-throttling'] });
try {
  const ctx = await b.newContext({ viewport: { width: 1100, height: 760 }, serviceWorkers: 'block', timezoneId: 'Pacific/Auckland' });
  await ctx.addInitScript((stub) => {
    try { localStorage.setItem('reeflive.picture', JSON.stringify('LOW')); localStorage.setItem('reeflive.tipsSeen', 'true'); localStorage.setItem('reeflive.newsSeenIds', '[]'); } catch (e) {}
    if (stub) { const G = WebGL2RenderingContext.prototype; for (const n of ['drawElements', 'drawArrays', 'drawElementsInstanced', 'drawArraysInstanced', 'drawRangeElements', 'clear', 'blitFramebuffer']) G[n] = function () {}; }
    window.__fps = 60;
    const raf = window.requestAnimationFrame.bind(window);
    let last = 0;   // callbacks at most window.__fps a second
    window.requestAnimationFrame = (cb) => {
      const wait = Math.max(0, last + 1000 / window.__fps - performance.now());
      return raf((t) => { if (wait > 0 && performance.now() - last < 1000 / window.__fps) { window.requestAnimationFrame(cb); return; } last = performance.now(); cb(t); });
    };
  }, process.env.GL !== '1');
  const p = await ctx.newPage();
  const say = (m) => process.env.QA_VERBOSE && console.error(`[${((Date.now() - T0) / 1000).toFixed(1)}s] ${m}`);
  const T0 = Date.now();
  const errs = [];
  p.on('pageerror', (e) => { errs.push(String(e).slice(0, 200)); if (process.env.QA_VERBOSE) console.error(`  page error: ${errs.at(-1)}`); });
  if (process.env.QA_VERBOSE) p.on('console', (m) => console.error(`  page ${m.type()}: ${m.text().slice(0, 200)}`));
  say('goto');
  await p.goto(url, { waitUntil: 'load' });
  await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 400000, polling: 500 });
  say('booted');
  await p.waitForFunction(() => document.getElementById('mytank')?.open, null, { timeout: 60000, polling: 250 });
  await p.evaluate(() => document.querySelector('#mytankList [data-build]').click());
  say('tank built');
  await p.waitForTimeout(1500);
  // throttle before the street opens, so its background warm-up gets no head start; then tap the first open store at once
  await p.evaluate((fps) => { window.__fps = fps; for (const d of document.querySelectorAll('dialog[open]')) if (d.id !== 'mytank') d.close(); document.querySelector('#rail [data-cat="LIFE"]')?.click(); }, FPS);
  await p.waitForFunction(() => document.querySelector('#card [data-store]:not([aria-disabled="true"])'), null, { timeout: 60000, polling: 250 });
  say('store tapped');
  const t0 = Date.now();
  await p.evaluate(() => document.querySelector('#card [data-store]:not([aria-disabled="true"])').click());
  const sawBar = p.waitForSelector('#storeUi .stwalk', { timeout: 15000 }).then(() => true, () => false);
  await p.waitForFunction(() => document.body.classList.contains('instore') && !document.getElementById('storeUi').classList.contains('hidden') && !document.querySelector('#storeUi .stwalk'), null, { timeout: 900000, polling: 100 });
  const ms = Date.now() - t0;
  console.log(JSON.stringify({ label, fps: FPS, walkInMs: ms, progressBarSeen: await sawBar, pageErrors: errs }));
} finally { await b.close(); srv.close(); }
