// usage: [STUB=1] [QA_QUERY=noworker] [QA_OUT=dir] node prof.mjs <siteDir> <label> [speed=1] [seconds=10]
// Serves siteDir, opens it as a mid-range phone (412x915, CPU x4), and records one devtools.timeline trace with the
// V8 sampling profiler inside it. Writes <label>.json (summary) and <label>.trace.json.
import { createRequire } from 'module';
import http from 'http';
import fs from 'fs';
import path from 'path';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const [siteDir, label, speedArg = '1', secArg = '10'] = process.argv.slice(2);
const speed = +speedArg, secs = +secArg;
const OUT = path.resolve(process.env.QA_OUT || 'qa-out'); fs.mkdirSync(OUT, { recursive: true });
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.css': 'text/css', '.wasm': 'application/wasm', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.png': 'image/png' };
const srv = http.createServer((q, r) => {
  let f = path.join(siteDir, decodeURIComponent(q.url.split('?')[0]));
  if (f.endsWith('/')) f += 'index.html';
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); });
});
await new Promise((ok) => srv.listen(+(process.env.QA_PORT || 0), '127.0.0.1', ok));
const port = srv.address().port;
const b = await chromium.launch({ executablePath: process.env.QA_BROWSER || '/opt/pw-browsers/chromium', args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--disable-dev-shm-usage', '--disable-renderer-backgrounding', '--disable-background-timer-throttling', '--js-flags=--expose-gc'] });
try {
  const ctx = await b.newContext({ viewport: { width: 412, height: 915 }, deviceScaleFactor: +(process.env.DSF || 0.25), isMobile: true, hasTouch: true, serviceWorkers: 'block' });
  await ctx.addInitScript(() => { try { localStorage.setItem('reeflive.picture', JSON.stringify('MEDIUM')); localStorage.setItem('reeflive.tipsSeen', 'true'); } catch (e) {} });
  await ctx.addInitScript(() => {
    const C = window.__pc = { tex: 0, raf: 0, long: 0, longMs: 0 };
    const G = WebGL2RenderingContext.prototype; const it = G.isTexture; G.isTexture = function (t) { C.tex++; return it.call(this, t); };
    const r = window.requestAnimationFrame; window.requestAnimationFrame = function (f) { return r.call(window, (t) => { const a = performance.now(); C.raf++; try { f(t); } finally { C.rafMs += performance.now() - a; } }); };
    for (const n of ['setInterval', 'setTimeout']) { const o = window[n]; window[n] = function (f, ...rest) { if (typeof f !== 'function') return o.call(window, f, ...rest); return o.call(window, function (...x) { const a = performance.now(); C.timer = (C.timer || 0) + 1; try { return f.apply(this, x); } finally { C.timerMs = (C.timerMs || 0) + performance.now() - a; } }, ...rest); }; }
    try { new PerformanceObserver((l) => { for (const e of l.getEntries()) { C.long++; C.longMs += e.duration; } }).observe({ type: 'longtask', buffered: true }); } catch (e) {}
  });
  if (process.env.STUB) await ctx.addInitScript(() => {
    // GPU-stubbed: skip rasterization only (draws, clears, blits); every other GL call and all JS still run
    const G = WebGL2RenderingContext.prototype;
    for (const n of ['drawElements', 'drawArrays', 'drawElementsInstanced', 'drawArraysInstanced', 'drawRangeElements', 'clear', 'blitFramebuffer']) G[n] = function () {};
  });
  if (process.env.GLCOUNT) await ctx.addInitScript(() => {
    const G = WebGL2RenderingContext.prototype; const C = window.__gl = {}; let unit = 0; const bound = {}; let vao = null, prog = null;
    for (const n of Object.getOwnPropertyNames(G)) { const d = Object.getOwnPropertyDescriptor(G, n); if (typeof d.value !== 'function') continue; const f = d.value;
      G[n] = function (...a) {
        C[n] = (C[n] || 0) + 1;
        if (n === 'activeTexture') { if (a[0] === unit) C.redundantActive = (C.redundantActive || 0) + 1; unit = a[0]; }
        if (n === 'bindTexture') { const k = unit + ':' + a[0]; if (bound[k] === a[1]) C.redundantBindTex = (C.redundantBindTex || 0) + 1; bound[k] = a[1]; }
        if (n === 'bindVertexArray') { if (vao === a[0]) C.redundantVao = (C.redundantVao || 0) + 1; vao = a[0]; }
        if (n === 'useProgram') { if (prog === a[0]) C.redundantProg = (C.redundantProg || 0) + 1; prog = a[0]; }
        return f.apply(this, a); }; }
  });
  const p = await ctx.newPage();
  let reqs = 0; p.on('request', () => reqs++);
  global.__reqs = () => reqs;
  const logs = [];
  p.on('console', (m) => { if (['error'].includes(m.type())) logs.push(m.text().slice(0, 200)); });
  p.on('pageerror', (e) => logs.push('PAGEERR ' + String(e).slice(0, 300)));
  console.log('goto', Date.now()); await p.goto(`http://127.0.0.1:${port}/?stats${process.env.QA_QUERY ? '&' + process.env.QA_QUERY : ''}`, { waitUntil: 'load' });
  await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 400000, polling: 1000 });
  console.log('loaded', Date.now());
  // the page opens on My tank (setup dialog on a first visit): go to Watch, the logged tank with the default stock
  await p.evaluate(() => { document.getElementById('mytank')?.close(); document.querySelector('.seg [data-mode=watch]').click(); });
  if (speed !== 1) await p.evaluate((v) => { const b = document.querySelector(`[data-speed="${v}"]`); if (b) b.click(); else { window.reefsandbox.setMode('sandbox'); window.reeflive.setSpeed(v); } }, speed);
  await p.waitForTimeout(+(process.env.WARM || 25000));
  console.log('warm', Date.now());
  if (process.env.GLCOUNT) { await p.evaluate(() => { for (const k in window.__gl) window.__gl[k] = 0; window.__pc.raf = 0; }); await p.waitForTimeout(5000); const r = await p.evaluate(() => ({ gl: window.__gl, raf: window.__pc.raf })); const st = await p.evaluate(() => [...document.querySelectorAll('pre')].map((e) => e.textContent).join('|')); console.log(st); console.log(JSON.stringify(Object.entries(r.gl).sort((a, b) => b[1] - a[1]).slice(0, 40))); process.exit(0); }
  if (process.env.QUICK) { for (let i = 0; i < secs; i++) { await p.waitForTimeout(1000); console.log(i, 'req', global.__reqs(), JSON.stringify(await p.evaluate(() => window.__pc)), (await p.evaluate(() => [...document.querySelectorAll('pre')].map((e) => e.textContent).join('|'))).replace(/\n/g, ' / ')); } console.log('logs', logs); process.exit(0); }
  const cdp = await ctx.newCDPSession(p);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: +(process.env.THR || 4) });
  console.log('throttled', Date.now()); await sleep(5000);
  await p.evaluate(() => { const C = window.__pc; C.t0 = performance.now(); C.raf = 0; C.rafMs = 0; C.timer = 0; C.timerMs = 0; C.long = 0; C.longMs = 0; C.sim = 0; C.draw = 0; C.frames = 0; });
  const chunks = [];
  cdp.on('Tracing.dataCollected', (e) => chunks.push(...e.value));
  const done = new Promise((ok) => cdp.once('Tracing.tracingComplete', ok));
  console.log('trace start', Date.now());
  await cdp.send('Tracing.start', { categories: ['devtools.timeline', 'toplevel', 'disabled-by-default-devtools.timeline.frame', 'v8.execute', 'blink.user_timing'].join(','), transferMode: 'ReportEvents' });
  await sleep(secs * 1000);
  await cdp.send('Tracing.end'); await done;
  console.log('trace end', Date.now());
  const pc = await p.evaluate(() => ({ ...window.__pc, wall: performance.now() - window.__pc.t0 }));
  fs.writeFileSync(`${OUT}/${label}.trace.json`, JSON.stringify(chunks));
  await cdp.send('Profiler.enable');
  await cdp.send('Profiler.setSamplingInterval', { interval: 250 });
  const raf0 = await p.evaluate(() => window.__pc.raf);
  await cdp.send('Profiler.start');
  await sleep(secs * 1000);
  const { profile } = await cdp.send('Profiler.stop');
  const profFrames = (await p.evaluate(() => window.__pc.raf)) - raf0;
  console.log('profile end', Date.now());
  fs.writeFileSync(`${OUT}/${label}.cpuprofile`, JSON.stringify(profile));
  const stats = await p.evaluate(() => [...document.querySelectorAll('pre')].map((e) => e.textContent).join('|'));
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 });
  fs.writeFileSync(`${OUT}/${label}.meta.json`, JSON.stringify({ pc, stats, logs, profFrames }, null, 1));
  console.log('pc', JSON.stringify(pc)); console.log('stats', stats.replace(/\n/g, ' / '));
  console.log('logs', logs.slice(0, 5));
} finally {
  await b.close();
  srv.close();
}
