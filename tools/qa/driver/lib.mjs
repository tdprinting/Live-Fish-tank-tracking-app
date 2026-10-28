import { createRequire } from 'module';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
export const { chromium } = require('playwright');
// Q: working directory (jobs/ in, screenshots and job .out files out); QA_URL: the served site
import path from 'path';
export const Q = path.resolve(process.env.QA_DIR || 'qa-out');
export async function open(w = 1280, h = 720) {
  const b = await chromium.launch({ executablePath: process.env.QA_BROWSER || '/opt/pw-browsers/chromium', args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--in-process-gpu', '--disable-dev-shm-usage', '--disable-renderer-backgrounding', '--disable-background-timer-throttling'] });
  const ctx = await b.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1, serviceWorkers: 'block', timezoneId: 'Pacific/Auckland' });
  await ctx.addInitScript(() => { try { localStorage.setItem('reeflive.picture', JSON.stringify('LOW')); } catch (e) {} });
  await ctx.addInitScript(() => { const g = HTMLCanvasElement.prototype.getContext; HTMLCanvasElement.prototype.getContext = function (t, o) { if (t && t.startsWith('webgl')) o = Object.assign({}, o, { preserveDrawingBuffer: true }); return g.call(this, t, o); }; });
  const p = await ctx.newPage();
  const logs = [];
  p.on('console', m => { if (['error', 'warning'].includes(m.type())) logs.push(m.type() + ': ' + m.text().slice(0, 200)); });
  p.on('pageerror', e => logs.push('PAGEERR ' + String(e).slice(0, 200)));
  await p.goto(process.env.QA_URL || 'http://localhost:8765/?stats', { waitUntil: 'load' });
  await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 400000, polling: 2000 }).catch(e => logs.push('load wait: ' + e.message.slice(0, 80)));
  await p.waitForTimeout(3000);
  // dismiss what's-new popup etc.
  return { b, p, logs };
}
import fs from 'fs';
export async function shot(p, n) { const d = await p.evaluate(() => document.getElementById('tank').toDataURL('image/png')); fs.writeFileSync(`${Q}/${n}.png`, Buffer.from(d.split(',')[1], 'base64')); }
