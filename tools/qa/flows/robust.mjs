// usage: node robust.mjs <siteDir>   -- save-on-close and worker crash recovery checks
import { createRequire } from 'module';
import http from 'http';
import fs from 'fs';
import path from 'path';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const siteDir = process.argv[2];
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.css': 'text/css', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.png': 'image/png' };
const srv = http.createServer((q, r) => {
  let f = path.join(siteDir, decodeURIComponent(q.url.split('?')[0]));
  if (f.endsWith('/')) f += 'index.html';
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); });
});
await new Promise((ok) => srv.listen(+(process.env.QA_PORT || 0), '127.0.0.1', ok));
const base = `http://127.0.0.1:${srv.address().port}/`;
const b = await chromium.launch({ executablePath: process.env.QA_BROWSER || '/opt/pw-browsers/chromium', args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--disable-dev-shm-usage'] });
let fails = 0;
const ok = (name, cond, note = '') => { console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${note}`); if (!cond) fails++; };
const logs = [];
async function open(ctx, query = '') {
  const p = await ctx.newPage();
  p.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') logs.push(`${m.type()}: ${m.text().slice(0, 200)}`); });
  p.on('pageerror', (e) => logs.push(`PAGEERR ${String(e).slice(0, 200)}`));
  await p.goto(base + query, { waitUntil: 'load' });
  await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 300000, polling: 500 });
  return p;
}
const host = (p, expr) => p.evaluate(`(async () => { const h = window.reefhost; const live = h.live, sb = h.sb, my = h.my; return (${expr}); })()`);
try {
  const ctx = await b.newContext({ viewport: { width: 1000, height: 700 }, serviceWorkers: 'block', timezoneId: 'Pacific/Auckland' });
  await ctx.addInitScript(() => {
    try { if (!localStorage.getItem('reeflive.picture')) { localStorage.setItem('reeflive.picture', JSON.stringify('LOW')); localStorage.setItem('reeflive.tipsSeen', 'true'); localStorage.setItem('reeflive.newsSeenIds', '[]'); } } catch (e) {}
  });
  // ---- 1. save on close
  let p = await open(ctx);
  ok('boots in a worker', (await host(p, 'h.where()')) === 'worker');
  await p.evaluate(() => document.querySelector('.seg [data-mode="mine"]').click());
  await p.waitForFunction(() => document.getElementById('mytank')?.open, null, { timeout: 60000 });
  await p.evaluate(() => document.querySelector('#mytankList [data-build]').click());
  await p.waitForFunction(async () => JSON.parse(await window.reefhost.my.info()).exists, null, { timeout: 60000 });
  await p.evaluate(() => { for (const d of document.querySelectorAll('dialog[open]')) d.close(); });
  await p.waitForTimeout(1500);
  const paces = JSON.parse(await host(p, 'my.paces()'));
  const target = paces.find((x) => !x.on);
  await host(p, `my.setPace(${JSON.stringify(target.id)})`);
  await p.waitForTimeout(1200); // well inside 2 s, and far from the 30 s timer
  await p.close({ runBeforeUnload: true });
  p = await open(ctx);
  await p.evaluate(() => document.querySelector('.seg [data-mode="mine"]').click());
  await p.waitForTimeout(1500);
  const after = JSON.parse(await host(p, 'my.paces()'));
  ok('pace change survived close within 2 s', after.find((x) => x.on)?.id === target.id, `wanted ${target.id}, got ${after.find((x) => x.on)?.id}`);

  // ---- 2. recovery
  const paceNow = () => host(p, 'my.paces()').then((s) => JSON.parse(s).find((x) => x.on)?.id);
  const noticeCount = () => p.evaluate(() => document.querySelectorAll('.simnotice').length);
  await p.evaluate(() => { window.reefhost.heartbeat.every = 200; window.reefhost.heartbeat.timeout = 1500; window.reefhost.heartbeat.stable = 0; });
  // 2a crash (terminate) -> heartbeat -> restart
  const pend = p.evaluate(() => window.reefhost.sb.panel().then(() => 'resolved', (e) => 'rejected:' + e.message));
  await p.evaluate(() => { window.reefhost.test.crash(); });
  const pendRes = await Promise.race([pend, new Promise((r) => setTimeout(() => r('HUNG'), 20000))]);
  ok('pending call settles after crash', pendRes !== 'HUNG', pendRes);
  await p.waitForFunction(() => document.querySelector('.simnotice'), null, { timeout: 120000 });
  ok('restarted in worker', (await host(p, 'h.where()')) === 'worker');
  const later = await Promise.race([host(p, 'my.info()').then(() => 'ok', () => 'rej'), new Promise((r) => setTimeout(() => r('HUNG'), 20000))]);
  ok('call after restart answers', later === 'ok', later);
  ok('tank restored after restart', (await paceNow()) === target.id);
  const snap1 = await host(p, 'h.snapshot() != null');
  ok('snapshots flow after restart', snap1);
  // 2b worker error event
  await p.evaluate(() => { window.reefhost.test.error(); });
  await p.waitForTimeout(500);
  const inflight = await Promise.race([host(p, 'my.info()').then(() => 'ok', () => 'rej'), new Promise((r) => setTimeout(() => r('HUNG'), 120000))]);
  ok('call during error-restart settles', inflight !== 'HUNG', inflight);
  ok('still a worker after error restart', (await host(p, 'h.where()')) === 'worker');
  ok('tank restored after error restart', (await paceNow()) === target.id);
  // 2c hang
  await p.evaluate(() => { window.reefhost.test.hang(); });
  const hres = await Promise.race([host(p, 'my.info()').then(() => 'ok', () => 'rej'), new Promise((r) => setTimeout(() => r('HUNG'), 120000))]);
  ok('call to hung worker settles', hres !== 'HUNG', hres);
  await p.waitForTimeout(1000);
  await Promise.race([host(p, 'my.info()').catch(() => 0), new Promise((r) => setTimeout(r, 90000))]);
  ok('back in a worker after hang', (await host(p, 'h.where()')) === 'worker');
  ok('tank restored after hang restart', (await paceNow()) === target.id);
  // 2d restarts fail twice -> in-thread
  await p.evaluate(() => { window.reefhost.test.failBoots(2); window.reefhost.test.crash(); });
  const during = Promise.race([host(p, 'my.info()').then(() => 'ok', () => 'rej'), new Promise((r) => setTimeout(() => r('HUNG'), 120000))]);
  await p.waitForFunction(() => window.reefhost.where() === 'page', null, { timeout: 120000 });
  ok('fell back to the page thread', true);
  ok('call held across fallback settles', (await during) !== 'HUNG');
  ok('no reload (same document)', await p.evaluate(() => performance.getEntriesByType('navigation').length === 1 && document.getElementById('loading')?.classList.contains('done')));
  ok('tank restored in-thread', (await paceNow()) === target.id);
  ok('notice shown', (await noticeCount()) >= 1);
  ok('in-thread snapshot', await host(p, 'h.snapshot() != null'));
  await p.waitForTimeout(1500);
  ok('mode kept (mine)', JSON.parse(await host(p, 'my.info()')).exists && (await host(p, 'sb.panel()')) != null);
} catch (e) { console.log('ERROR', e); fails++; }
finally {
  console.log(logs.slice(0, 25).join('\n'));
  await b.close();
  srv.close();
  console.log(fails ? `${fails} FAILED` : 'ALL PASSED');
  process.exit(fails ? 1 : 0);
}
