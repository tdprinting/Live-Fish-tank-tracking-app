// usage: node smoke.mjs <siteDir> <label> [query] [seconds]
import { createRequire } from 'module';
import http from 'http';
import fs from 'fs';
import path from 'path';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const [siteDir, label, query = '', secs = '20'] = process.argv.slice(2);
const OUT = path.join(path.resolve(process.env.QA_OUT || 'qa-out'), label);
fs.mkdirSync(OUT, { recursive: true });
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.css': 'text/css', '.webp': 'image/webp', '.png': 'image/png', '.svg': 'image/svg+xml' };
const srv = http.createServer((q, r) => {
  let f = path.join(siteDir, decodeURIComponent(q.url.split('?')[0]));
  if (f.endsWith('/')) f += 'index.html';
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); });
});
await new Promise((ok) => srv.listen(+(process.env.QA_PORT || 0), '127.0.0.1', ok));
const b = await chromium.launch({ executablePath: process.env.QA_BROWSER || '/opt/pw-browsers/chromium', args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--disable-dev-shm-usage', '--disable-renderer-backgrounding', '--disable-background-timer-throttling'] });
try {
  const ctx = await b.newContext({ viewport: { width: 900, height: 640 }, deviceScaleFactor: 1, serviceWorkers: 'block' });
  await ctx.addInitScript((STUB) => {
    try { localStorage.setItem('reeflive.picture', JSON.stringify('LOW')); localStorage.setItem('reeflive.tipsSeen', 'true'); localStorage.setItem('reeflive.newsSeenIds', '[]'); } catch (e) {}
    if (STUB) { const G = WebGL2RenderingContext.prototype; for (const n of ['drawElements', 'drawArrays', 'drawElementsInstanced', 'drawArraysInstanced', 'drawRangeElements', 'clear', 'blitFramebuffer']) G[n] = function () {}; }
    const g = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (t, o) { if (t && t.startsWith('webgl')) o = Object.assign({}, o, { preserveDrawingBuffer: true }); return g.call(this, t, o); };
  }, !!process.env.STUB);
  const p = await ctx.newPage();
  p.on('console', (m) => { if (m.type() !== 'log' || /simwire|SimHost|worker/i.test(m.text())) console.log(`[${m.type()}] ${m.text().slice(0, 400)}`); });
  p.on('pageerror', (e) => console.log(`PAGEERR ${String(e).slice(0, 400)}`));
  p.on('worker', (w) => { console.log('worker started', w.url()); w.on('console', (m) => console.log(`[worker ${m.type()}] ${m.text().slice(0, 400)}`)); });
  const t0 = Date.now();
  await p.goto(`http://127.0.0.1:${srv.address().port}/?stats${query ? `&${query}` : ''}`, { waitUntil: 'load' });
  await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 300000, polling: 500 });
  console.log('loaded in', (Date.now() - t0) / 1000, 's; where', await p.evaluate(() => window.reeflive.where?.()));
  await p.evaluate(() => { document.getElementById('mytank')?.close(); document.querySelector('.seg [data-mode=watch]').click(); });
  for (let i = 0; i < +secs; i += 5) {
    await p.waitForTimeout(5000);
    const st = await p.evaluate(() => [...document.querySelectorAll('pre')].map((e) => e.textContent).join(' | ').replace(/\n/g, ' / '));
    const s = await p.evaluate(() => window.reeflive.state());
    console.log(`t+${i + 5}s`, st, '| state', String(s).slice(0, 120));
  }
  if (process.env.FOLLOW) { await p.evaluate(() => { const a = JSON.parse(window.reefsandbox.animals() || '[]'); const c = a.find((x) => /clown/i.test(x.species)) || a[0]; window.reeflive.follow(c.id); }); await p.waitForTimeout(12000); }
  const d = await p.evaluate(() => document.getElementById('tank').toDataURL('image/png'));
  fs.writeFileSync(`${OUT}/watch.png`, Buffer.from(d.split(',')[1], 'base64'));
} finally { await b.close(); srv.close(); }
