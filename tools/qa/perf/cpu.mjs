// usage: node cpu.mjs <siteDir> <label> <speed> <seconds>
// Contention-robust: CPU time the page's renderer process actually used (/proc utime+stime), GPU rasterization
// stubbed, plus frames (ReefLive's own frame loop, counted via the stats overlay hook) over the same window.
import { createRequire } from 'module';
import http from 'http';
import fs from 'fs';
import path from 'path';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const [siteDir, label, speedArg = '1', secArg = '20'] = process.argv.slice(2);
const OUT = path.resolve(process.env.QA_OUT || 'qa-out'); fs.mkdirSync(OUT, { recursive: true });
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.css': 'text/css' };
const srv = http.createServer((q, r) => {
  let f = path.join(siteDir, decodeURIComponent(q.url.split('?')[0])); if (f.endsWith('/')) f += 'index.html';
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); });
});
await new Promise((ok) => srv.listen(+(process.env.QA_PORT || 0), '127.0.0.1', ok));
const udd = fs.mkdtempSync(`${OUT}/udd-`);
const ctx = await chromium.launchPersistentContext(udd, { executablePath: process.env.QA_BROWSER || '/opt/pw-browsers/chromium', viewport: { width: 412, height: 915 }, deviceScaleFactor: 0.25, serviceWorkers: 'block',
  args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--disable-dev-shm-usage', '--disable-renderer-backgrounding', '--disable-background-timer-throttling'] });
const ticks = () => {
  let best = null;
  for (const pid of fs.readdirSync('/proc').filter((d) => /^\d+$/.test(d))) {
    let cmd; try { cmd = fs.readFileSync(`/proc/${pid}/cmdline`, 'utf8'); } catch { continue; }
    if (!cmd.includes('--type=renderer') || !cmd.includes(udd)) continue;
    const st = fs.readFileSync(`/proc/${pid}/stat`, 'utf8').split(') ')[1].split(' ');
    const t = +st[11] + +st[12];
    if (!best || t > best.t) best = { pid, t };
  }
  return best;
};
try {
  await ctx.addInitScript(() => {
    localStorage.setItem('reeflive.picture', JSON.stringify('MEDIUM')); localStorage.setItem('reeflive.tipsSeen', 'true');
    const G = WebGL2RenderingContext.prototype;
    for (const n of ['drawElements', 'drawArrays', 'drawElementsInstanced', 'drawArraysInstanced', 'clear', 'blitFramebuffer']) G[n] = function () {};
  });
  const p = ctx.pages()[0] || await ctx.newPage();
  await p.goto(`http://127.0.0.1:${srv.address().port}/?stats`, { waitUntil: 'load' });
  await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 400000, polling: 1000 });
  await p.evaluate(() => { document.getElementById('mytank')?.close(); document.querySelector('.seg [data-mode=watch]').click(); });
  if (+speedArg !== 1) await p.evaluate((v) => { window.reefsandbox.setMode('sandbox'); window.reeflive.setSpeed(v); }, +speedArg);
  await p.waitForTimeout(25000);
  // frames: the stats overlay line "N fps" once a second; sum N over the window
  await p.evaluate(() => { window.__fr = []; const el = document.querySelector('pre'); new MutationObserver(() => { const m = /^(\d+) fps/.exec(el.textContent); if (m) window.__fr.push(+m[1]); }).observe(el, { childList: true, characterData: true, subtree: true }); });
  const a = ticks(); const w0 = Date.now();
  await p.waitForTimeout(+secArg * 1000);
  const b = ticks(); const wall = (Date.now() - w0) / 1000;
  const fr = await p.evaluate(() => window.__fr);
  const hz = 100; // USER_HZ
  const cpuMs = (b.t - a.t) * 1000 / hz;
  const frames = fr.reduce((s, v) => s + v, 0) * (wall / Math.max(1, fr.length));
  const out = { label, speed: +speedArg, wall, cpuMsPerSec: +(cpuMs / wall).toFixed(1), fps: +(frames / wall).toFixed(1), cpuMsPerFrame: +(cpuMs / Math.max(1, frames)).toFixed(2) };
  console.log(JSON.stringify(out));
  fs.appendFileSync(`${OUT}/cpu.jsonl`, JSON.stringify(out) + '\n');
} finally { await ctx.close(); srv.close(); fs.rmSync(udd, { recursive: true, force: true }); }
