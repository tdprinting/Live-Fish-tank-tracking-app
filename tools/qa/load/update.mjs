// Update path: a visitor with the old build's service worker gets the new build. node update.mjs <old> <new> <port>
import { createRequire } from 'module';
import fs from 'fs';
import { spawn } from 'child_process';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const HERE = new URL('.', import.meta.url).pathname.replace(/\/$/, '');   // where serve.mjs is
const D = (process.env.QA_OUT || 'qa-out').replace(/\/$/, '');   // profiles, screenshots, logs
fs.mkdirSync(D, { recursive: true });
const [oldSite, newSite, port] = [process.argv[2], process.argv[3], +process.argv[4]];
const base = (process.env.QA_URL || `https://localhost:${port}`);
let srv = null;
const serve = async (site) => { if (srv) { srv.kill(); await new Promise((r) => setTimeout(r, 500)); } srv = spawn('node', [`${HERE}/serve.mjs`, site, String(port), '0', '0'], { stdio: 'ignore' }); await new Promise((r) => setTimeout(r, 1200)); };
process.on('exit', () => { try { srv?.kill(); } catch {} });
const udd = `${D}/profile-upd`; fs.rmSync(udd, { recursive: true, force: true });
const ctx = await chromium.launchPersistentContext(udd, { executablePath: (process.env.QA_BROWSER || '/opt/pw-browsers/chromium'), headless: true, ignoreHTTPSErrors: true,
  args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--in-process-gpu', '--disable-gpu-watchdog', '--disable-renderer-backgrounding', '--disable-background-timer-throttling', '--disable-dev-shm-usage', '--ignore-certificate-errors'],
  viewport: { width: 360, height: 640 } });
await ctx.addInitScript(() => { try { localStorage.setItem('reeflive.picture', JSON.stringify('MEDIUM')); } catch {} });
const errs = [];
async function visit(name, waitMs) {
  const p = await ctx.newPage();
  p.on('pageerror', (e) => errs.push(name + ' ' + String(e).slice(0, 200)));
  await fetch(`${base}/__reset`);
  const t0 = Date.now();
  await p.goto(`${base}/`, { waitUntil: 'commit' });
  const ok = await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 300000, polling: 300 }).then(() => true).catch(() => false);
  const tDone = Date.now() - t0;
  await p.waitForTimeout(waitMs);
  const info = await p.evaluate(async () => { const out = {}; for (const k of await caches.keys()) out[k] = (await (await caches.open(k)).keys()).length; return { newPage: typeof window.reefFetch === 'function', caches: out }; });
  const st = await (await fetch(`${base}/__stats`)).json();
  console.log(name, JSON.stringify({ ok, tDone, ...info, netBytes: st.bytesOut, reqs: st.reqs, files: st.log.filter((l) => l[3] > 50000).map((l) => l[1]).slice(0, 12) }));
  await p.close();
}
try {
  await serve(oldSite);
  await visit('old-1', 30000);
  await visit('old-2', 30000); // the old worker caches data from the second visit on
  await serve(newSite);
  await visit('new-1 (old worker serves, new installs)', 40000);
  await visit('new-2', 40000);
  await visit('new-3', 10000);
  console.log('errors', JSON.stringify(errs.slice(0, 10)));
} finally { await ctx.close(); srv?.kill(); }
