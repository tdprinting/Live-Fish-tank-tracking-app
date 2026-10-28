// Cold + warm load of the web app through the shaped server. usage: node measure.mjs <port> <label> [cpuRate=4] <siteDir> [mbps=9] [rttMs=100]   (env: QA_OUT, QA_BROWSER)
import { createRequire } from 'module';
import fs from 'fs';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const HERE = new URL('.', import.meta.url).pathname.replace(/\/$/, '');   // where serve.mjs is
const D = (process.env.QA_OUT || 'qa-out').replace(/\/$/, '');   // profiles, screenshots, logs
fs.mkdirSync(D, { recursive: true });
const port = +process.argv[2]; const label = process.argv[3]; const cpu = +(process.argv[4] || 4);
const siteDir = process.argv[5]; const mbps = process.argv[6] || '9'; const rttMs = process.argv[7] || '100';
const { spawn } = await import('child_process');
const srv = spawn('node', [`${HERE}/serve.mjs`, siteDir, String(port), mbps, rttMs], { stdio: 'inherit' });
await new Promise((r) => setTimeout(r, 1500));
const errs = [];
const base = (process.env.QA_URL || `https://localhost:${port}`);
const udd = `${D}/profile-${label}`; fs.rmSync(udd, { recursive: true, force: true });
const ctx = await chromium.launchPersistentContext(udd, {
  executablePath: (process.env.QA_BROWSER || '/opt/pw-browsers/chromium'), headless: true, ignoreHTTPSErrors: true,
  args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--in-process-gpu', '--disable-dev-shm-usage', '--disable-renderer-backgrounding', '--disable-background-timer-throttling', '--ignore-certificate-errors'],
  viewport: { width: 360, height: 640 }, deviceScaleFactor: 1, isMobile: true, hasTouch: true, timezoneId: 'Pacific/Auckland',
});
await ctx.addInitScript(() => { try { if (!localStorage.getItem('reeflive.picture')) localStorage.setItem('reeflive.picture', JSON.stringify('MEDIUM')); } catch {} });
await ctx.addInitScript(() => {
  // per-frame draw-call counter + load marks
  const P = WebGL2RenderingContext.prototype; let n = 0;
  for (const k of ['drawElements', 'drawArrays', 'drawElementsInstanced', 'drawArraysInstanced']) { const f = P[k]; P[k] = function (...a) { n++; return f.apply(this, a); }; }
  const bd = P.bufferData; window.__ups = [];
  P.bufferData = function (t, d, ...r) { const n = d && d.byteLength; if (n > 300000) window.__ups.push([performance.now(), n]); return bd.call(this, t, d, ...r); };
  const frames = []; window.__frames = frames; const t0 = performance.timeOrigin;
  const loop = () => { if (n) frames.push([performance.now(), n]); n = 0; requestAnimationFrame(loop); }; requestAnimationFrame(loop);
  window.__longtasks = 0;
  try { new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__longtasks += e.duration; }).observe({ type: 'longtask', buffered: true }); } catch {}
});
async function visit(name, page) {
  await fetch(`${base}/__reset`, { dispatcher: undefined }).catch(() => {});
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) errs.push(name + ' ' + m.type() + ': ' + m.text().slice(0, 200)); });
  page.on('pageerror', (e) => errs.push(name + ' PAGEERR ' + String(e).slice(0, 300)));
  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: cpu });
  const t0 = Date.now();
  let swHits = 0, pageReqs = 0;
  page.on('requestfinished', async (r) => { pageReqs++; const res = await r.response().catch(() => null); if (res?.fromServiceWorker()) swHits++; });
  await page.goto(`${base}/`, { waitUntil: 'commit' });
  const done = await page.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: +(process.env.LOADTO || 600000), polling: 100 }).then(() => Date.now() - t0).catch(() => -1);
  const s1 = await (await fetch(`${base}/__stats`)).json();
  // wait for the network to go quiet for 4 s (the scene has everything it asked for)
  let lastN = -1, quietSince = Date.now(), s2 = s1;
  for (;;) {
    await new Promise((r) => setTimeout(r, 500));
    s2 = await (await fetch(`${base}/__stats`)).json();
    if (s2.bytesOut !== lastN) { lastN = s2.bytesOut; quietSince = Date.now(); }
    if (Date.now() - quietSince > 4000 || Date.now() - t0 > 600000) break;
  }
  const lastByte = s2.log.length ? Math.max(...s2.log.map((l) => l[0])) : t0;
  await page.waitForTimeout(2000);
  const frames = await page.evaluate(() => window.__frames);
  const nav = await page.evaluate(() => performance.timeOrigin);
  const ups = await page.evaluate(() => window.__ups);
  const rel = (t) => Math.round(nav + t - t0);
  const firstBig = ups.length ? rel(ups[0][0]) : -1; const lastBig = ups.length ? rel(ups[ups.length - 1][0]) : -1;
  const lastEnd = s2.log.length ? Math.max(...s2.log.map((l) => l[4] || l[0])) - t0 : 0;
  const fin = Math.max(...frames.slice(-20).map((f) => f[1]));
  // first frame with any draw / first frame with >= 60% and >= 95% of the steady draw count
  const at = (k) => { const f = frames.find((x) => x[1] >= k); return f ? Math.round(nav + f[0] - t0) : -1; };
  const firstDraw = frames.length ? Math.round(nav + frames[0][0] - t0) : -1;
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 });
  await page.screenshot({ path: `${D}/${label}-${name}.png`, timeout: 60000 }).catch(() => {});
  const out = { name, loadingDone: done, firstBigUpload: firstBig, lastBigUpload: lastBig, lastByteEnd: lastEnd, bigUploads: ups.length, firstDraw, draw60: at(fin * 0.6), draw95: at(fin * 0.95), steadyDraws: fin, netQuiet: lastByte - t0,
    bytesAtDone: s1.bytesOut, reqsAtDone: s1.reqs, bytesTotal: s2.bytesOut, reqsTotal: s2.reqs, pageReqs, swHits,
    top: Object.entries(s2.log.reduce((m, l) => { const k = l[1].split('/').slice(0, 3).join('/'); m[k] = (m[k] || 0) + l[3]; return m; }, {})).sort((a, b) => b[1] - a[1]).slice(0, 8).map(([k, v]) => `${k} ${(v / 1e6).toFixed(2)}MB`) };
  fs.writeFileSync(`${D}/${label}-${name}-log.json`, JSON.stringify(s2.log.map((l) => [l[0] - t0, l[1], l[2], l[3]])));
  return out;
}
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
const p1 = ctx.pages()[0] || await ctx.newPage();
const coldStart = Date.now();
const cold = await visit('cold', p1);
console.log(JSON.stringify(cold));
await new Promise((r) => setTimeout(r, Math.max(8000, coldStart + 100000 - Date.now()))); // the page hands its files to the service worker at 30 s and 90 s
await p1.close();
const p2 = await ctx.newPage();
const warm = await visit('warm', p2);
console.log(JSON.stringify(warm));
await ctx.close();
console.log(JSON.stringify(errs.slice(0, 20)));
srv.kill();
