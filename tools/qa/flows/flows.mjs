// usage: node flows.mjs <siteDir> <label> [query]
// Drives the main flows through the page's own UI and reports pass / fail per step, page errors and screenshots.
import { createRequire } from 'module';
import http from 'http';
import fs from 'fs';
import path from 'path';
const require = createRequire(process.env.QA_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const [siteDir, label, query = ''] = process.argv.slice(2);
const OUT = path.join(path.resolve(process.env.QA_OUT || 'qa-out'), label);
fs.mkdirSync(OUT, { recursive: true });
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.css': 'text/css', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.png': 'image/png' };
const srv = http.createServer((q, r) => {
  let f = path.join(siteDir, decodeURIComponent(q.url.split('?')[0]));
  if (f.endsWith('/')) f += 'index.html';
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); });
});
await new Promise((ok) => srv.listen(+(process.env.QA_PORT || 0), '127.0.0.1', ok));
const url = `http://127.0.0.1:${srv.address().port}/${query ? `?${query}` : ''}`;
const b = await chromium.launch({ executablePath: process.env.QA_BROWSER || '/opt/pw-browsers/chromium', args: ['--use-angle=swiftshader', '--use-gl=angle', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox', '--disable-gpu-watchdog', '--disable-dev-shm-usage', '--disable-renderer-backgrounding', '--disable-background-timer-throttling'] });
const results = [];
const logs = [];
const t0 = Date.now();
try {
  const ctx = await b.newContext({ viewport: { width: 1100, height: 760 }, deviceScaleFactor: 1, serviceWorkers: 'block', acceptDownloads: true, timezoneId: 'Pacific/Auckland' });
  await ctx.addInitScript((STUB) => {
    try { if (!localStorage.getItem('reeflive.picture')) { localStorage.setItem('reeflive.picture', JSON.stringify('LOW')); localStorage.setItem('reeflive.tipsSeen', 'true'); localStorage.setItem('reeflive.newsSeenIds', '[]'); } } catch (e) {}
    if (STUB) { const G = WebGL2RenderingContext.prototype; for (const n of ['drawElements', 'drawArrays', 'drawElementsInstanced', 'drawArraysInstanced', 'drawRangeElements', 'clear', 'blitFramebuffer']) G[n] = function () {}; }
    const g = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (t, o) { if (t && t.startsWith('webgl')) o = Object.assign({}, o, { preserveDrawingBuffer: true }); return g.call(this, t, o); };
  }, process.env.GL !== "1");   // GPU draw calls stubbed by default (frame rate then no longer depends on machine load); GL=1 renders for real
  const p = await ctx.newPage();
  const dialogs = [];
  p.on('dialog', (d) => { dialogs.push(`${d.type()}: ${d.message().slice(0, 80)}`); d.accept(); });
  p.on('console', (m) => { if (m.type() === 'error' || (m.type() === 'warning' && /sim|worker|Reef/i.test(m.text()))) logs.push(`${m.type()}: ${m.text().slice(0, 300)}`); });
  p.on('pageerror', (e) => logs.push(`PAGEERR ${String(e).slice(0, 300)}`));
  const H = (code, arg) => p.evaluate(code, arg);
  // the page's own SimHost module (same instance as the page's)
  const host = (expr) => p.evaluate(`(async () => { let live, sb, my; try { const h = window.reefhost || await import('./simhost.js'); live = h.live; sb = h.sb; my = h.my; } catch (e) { const k = await import('./kotlin/ReefLive-web.mjs'); live = k.ReefLive.getInstance(); sb = k.ReefSandbox.getInstance(); my = k.ReefMyTank.getInstance(); } return (${expr}); })()`);
  const shot = async (n) => { try { const d = await H(() => document.getElementById('tank').toDataURL('image/png')); fs.writeFileSync(`${OUT}/${n}.png`, Buffer.from(d.split(',')[1], 'base64')); } catch (e) { logs.push(`shot ${n}: ${e.message}`); } };
  const page = async (n) => { await p.screenshot({ path: `${OUT}/${n}.page.png` }); };
  const say = () => H(() => document.getElementById('hermieText')?.textContent || document.getElementById('toast')?.textContent || '');
  async function step(name, fn) {
    const s = Date.now();
    try { const note = await fn(); results.push({ name, ok: true, ms: Date.now() - s, note }); console.log(`PASS ${name} (${Date.now() - s} ms) ${note ?? ''}`); }
    catch (e) { results.push({ name, ok: false, ms: Date.now() - s, err: e.message.slice(0, 300) }); console.log(`FAIL ${name}: ${e.message.slice(0, 300)}`); await page(`fail_${name.replace(/\W+/g, '_')}`).catch(() => {}); }
  }
  const until = async (fn, what, ms = 60000) => { const end = Date.now() + ms; for (;;) { const v = await fn(); if (v) return v; if (Date.now() > end) throw new Error(`timeout: ${what}`); await p.waitForTimeout(250); } };
  // a real tap: the pointer arrives first and is held across a few frames (an instant down/up can land inside one frame and be missed)
  // the idle screensaver starts after 30 s without input (long waits for bags etc.): one tap wakes it, so wake it first
  const wake = async () => { for (let i = 0; i < 5 && await H(() => !document.getElementById('saver')?.classList.contains('hidden')); i++) { await p.mouse.click(5, 5); await p.waitForTimeout(1500); } };
  const tap = async (x, y) => { await wake(); await p.mouse.move(x, y); await p.waitForTimeout(250); await p.mouse.down(); await p.waitForTimeout(150); await p.mouse.up(); };
  const closeDialogs = () => H(() => { for (const d of document.querySelectorAll('dialog[open]')) if (d.id !== 'mytank') d.close(); });
  const animals = async () => JSON.parse(await host('sb.animals()'));
  const panel = async () => JSON.parse(await host('sb.panel()'));
  const cat = (c) => H((c) => document.querySelector(`#rail [data-cat="${c}"]`)?.click(), c);
  const clickCard = (sel) => H((sel) => { const el = document.querySelector(`#card ${sel}`); if (!el) throw new Error(`no ${sel}`); el.click(); }, sel);

  await step('boot', async () => {
    await p.goto(url, { waitUntil: 'load' });
    await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 400000, polling: 500 });
    return `${((Date.now() - t0) / 1000).toFixed(1)} s`;
  });
  await step('my tank: pick a build', async () => {
    await until(() => H(() => document.getElementById('mytank')?.open), 'setup dialog');
    await H(() => document.querySelector('#mytankList [data-build]').click());
    await until(async () => JSON.parse(await host('my.info()')).exists, 'tank exists');
    await closeDialogs();
    return JSON.parse(await host('my.info()')).build;
  });
  await p.waitForTimeout(2000);
  await shot('mytank_empty');
  await step('store: walk in, buy, carry home', async () => {
    await closeDialogs();
    await cat('LIFE');
    await until(() => H(() => document.querySelector('#card [data-store]:not([aria-disabled="true"])')), 'an open store');
    await H(() => document.querySelector('#card [data-store]:not([aria-disabled="true"])').click());
    await until(() => H(() => !document.getElementById('storeUi').classList.contains('hidden')), 'store ui', 900000);
    await p.waitForTimeout(2500);
    await shot('store');
    const coins = (await H(() => document.querySelector('.money b')?.textContent)) || '?';
    // the cheapest unlocked animal
    const id = await H(() => { const items = [...document.querySelectorAll('.stitem:not(.locked)')]; items.sort((a, b) => parseInt(a.querySelector('.pr').textContent.replace(/\D/g, '')) - parseInt(b.querySelector('.pr').textContent.replace(/\D/g, ''))); return items[0]?.dataset.spick; });
    if (!id) throw new Error('no item');
    await H((id) => document.querySelector(`[data-spick="${id}"]`).click(), id);
    await until(() => H(() => document.querySelector('[data-sadd]')), 'card');
    await H(() => document.querySelector('[data-sadd]').click());
    await H(() => document.querySelector('[data-scounter]').click());
    await until(() => H(() => document.querySelector('[data-spay]')), 'counter');
    if (await H(() => document.querySelector('[data-spay]').disabled)) throw new Error(`cannot pay (coins ${coins})`);
    await H(() => document.querySelector('[data-spay]').click());
    await until(() => H(() => document.getElementById('storeUi').classList.contains('hidden')), 'home again');
    return `${id}, coins ${coins}`;
  });
  await step('bags: hurry, animal comes out', async () => {
    await until(() => H(() => !document.getElementById('bagPanel')?.classList.contains('hidden')), 'bag panel', 30000);
    await H(() => document.getElementById('bagHurry').click());
    await until(async () => (await animals()).filter((a) => a.alive).length > 0, 'animal out of the bag', 600000);
    return `${(await animals()).length} animals`;
  });
  await p.waitForTimeout(3000);
  await shot('mytank_stocked');
  await step('feed from the shelf', async () => {
    await wake();
    await cat('SUPPLIES');
    await until(() => H(() => document.querySelector('#shelfbar [data-supply="flake"]')), 'shelf');
    await H(() => document.querySelector('#shelfbar [data-supply="flake"]').click());
    await until(() => H(() => document.getElementById('aimBar') && !document.getElementById('aimBar').classList.contains('hidden')), 'aim bar', 20000);
    const box = await p.locator('#tank').boundingBox();
    await tap(box.x + box.width / 2, box.y + box.height * 0.45);
    await until(() => H(() => document.getElementById('aimBar').classList.contains('hidden')), 'fed');
    await p.waitForTimeout(800);
    return await say();
  });
  await step('sandbox slider (temperature)', async () => {
    await cat('TANK');
    await until(() => H(() => document.querySelector('#card [data-more]')), 'tank card');
    await clickCard('[data-more]');
    await until(() => H(() => document.querySelector('#card [data-cmode]')), 'more');
    await H(() => { const t = [...document.querySelectorAll('#card [data-tab]')].find((x) => x.dataset.tab === 'WATER'); t?.click(); });
    await until(() => H(() => document.querySelector('#card [data-param="temp"]')), 'temp row');
    const before = (await panel()).values.temp.v;
    await H(() => { const r = document.querySelector('#card [data-param="temp"]'); r.value = String(Math.min(1000, Number(r.value) + 120)); r.dispatchEvent(new Event('input', { bubbles: true })); });
    await until(async () => Math.abs((await panel()).values.temp.v - before) > 0.2, 'temp moved', 20000);
    return `${before.toFixed(2)} → ${(await panel()).values.temp.v.toFixed(2)}`;
  });
  await step('preset (heater stuck)', async () => {
    await until(() => H(() => document.querySelector('#card [data-preset="HEATER_STUCK"]')), 'preset');
    await clickCard('[data-preset="HEATER_STUCK"]');
    await p.waitForTimeout(600);
    return await say();
  });
  await step('make a fish sick', async () => {
    await until(() => H(() => document.querySelector('#card [data-sick]')), 'sick tile');
    await clickCard('[data-sick]');
    await until(() => H(() => document.querySelector('#card [data-infect="ICH"]')), 'illness list');
    await clickCard('[data-infect="ICH"]');
    await until(async () => (await animals()).some((a) => a.disease), 'disease shows', 60000);
    return (await animals()).filter((a) => a.disease).map((a) => `${a.name}: ${a.disease}`).join(', ');
  });
  await H(() => document.querySelector('#card [data-close]')?.click());
  await step('tank health card', async () => {
    await H(() => document.getElementById('statusBtn').click());
    await until(() => H(() => document.querySelector('#card #hungerRows') && document.querySelector('#card .fixes, #card p strong')), 'health card');
    await p.waitForTimeout(1200);
    await page('health');
    const n = await H(() => document.querySelectorAll('#card [data-fix]').length);
    if (n) { await clickCard('[data-fix="0"]'); await p.waitForTimeout(500); }
    return `${n} fixes offered`;
  });
  await H(() => document.querySelector('#card [data-close]')?.click());
  await step('time-lapse', async () => {
    await cat('LOOK');
    await until(() => H(() => document.querySelector('#card [data-lapse]')), 'look card');
    await clickCard('[data-lapse]');
    await until(() => H(() => document.querySelector('#lapse [data-lrun]')), 'lapse sheet');
    await H(() => document.querySelector('#lapse [data-lpreset="0"]').click());
    await H(() => document.querySelector('#lapse [data-lrun]').click());
    await until(() => H(() => document.querySelector('#lapseBody .verdicts')), 'verdicts', 120000);
    await page('timelapse');
    const head = await H(() => document.querySelector('#lapseBody h3')?.textContent + ' / ' + document.querySelector('#lapseBody p')?.textContent);
    await H(() => document.getElementById('lapseClose').click());
    return head;
  });
  let saved;
  await step('save and reload My tank', async () => {
    // buy one more so the reload has something to keep
    saved = await host('my.info()');
    await host('my.save()');
    await p.waitForTimeout(1500);
    const ids = JSON.parse(await host('my.info()'));
    const n0 = (await animals()).length;
    await p.reload({ waitUntil: 'load' });
    await p.waitForFunction(() => document.getElementById('loading')?.classList.contains('done'), null, { timeout: 400000, polling: 500 });
    await until(async () => JSON.parse(await host('my.info()')).exists, 'tank after reload');
    await closeDialogs();
    await p.waitForTimeout(2500);
    const n1 = (await animals()).length;
    const info = JSON.parse(await host('my.info()'));
    if (info.name !== ids.name || info.buildId !== ids.buildId) throw new Error(`info changed ${JSON.stringify(info)}`);
    if (n1 < n0 || n0 === 0) throw new Error(`animals ${n0} → ${n1}`);
    return `${info.build}, animals ${n0} → ${n1}`;
  });
  await step('follow and take a fish out', async () => {
    const list = await animals();
    if (!list.length) throw new Error('no animals');
    const before = list.length;
    await cat('LOOK');
    await until(() => H(() => document.querySelector('#card [data-follow]')), 'follow list');
    await clickCard('[data-follow]');
    await until(() => H(() => !document.getElementById('follow').classList.contains('hidden')), 'following');
    await p.waitForTimeout(2500);
    await shot('follow');
    await H(() => document.querySelector('#follow [data-finfo]').click());
    await until(() => H(() => document.querySelector('#fish [data-fremove]')), 'fish card');
    await H(() => document.querySelector('#fish [data-fremove]').click());
    await until(async () => (await animals()).length < before, 'animal gone', 20000);
    return `${before} → ${(await animals()).length}`;
  });
  await step('watch mode, speed 60x', async () => {
    await H(() => document.querySelector('.seg [data-mode="watch"]').click());
    await p.waitForTimeout(1500);
    await closeDialogs();
    await shot('watch');
    const t1 = JSON.parse(await host('live.state()')).time;
    await cat('LOOK');
    await until(() => H(() => document.querySelector('#card [data-time]')), 'time slider');
    await H(() => { const r = document.querySelector('#card [data-time]'); r.value = String(r.dataset.stops.split(',').indexOf('60')); r.dispatchEvent(new Event('input', { bubbles: true })); });
    await p.waitForTimeout(4000);
    const t2 = JSON.parse(await host('live.state()')).time;
    await shot('watch60');
    await H(() => { const r = document.querySelector('#card [data-time]'); r.value = '0'; r.dispatchEvent(new Event('input', { bubbles: true })); });
    return `sim advanced ${(t2 - t1).toFixed(0)} s in ~4 s`;
  });
  await step('watch: knock and feed', async () => {
    await H(() => document.querySelector('#card [data-close]')?.click());
    await wake();
    await cat('SUPPLIES');
    await until(() => H(() => document.querySelector('#shelfbar [data-supply="pellet"]')), 'shelf');
    await H(() => document.querySelector('#shelfbar [data-supply="pellet"]').click());
    await until(() => H(() => document.getElementById('aimBar') && !document.getElementById('aimBar').classList.contains('hidden')), 'aim', 20000);
    const box = await p.locator('#tank').boundingBox();
    await tap(box.x + box.width * 0.4, box.y + box.height * 0.4);
    await p.waitForTimeout(1500);
    return await say();
  });
  await step('tracker + backup export/import', async () => {
    await H(() => document.querySelector('.tab[data-tab="params"]').click());
    await H(() => document.getElementById('logBtn').click());
    await until(() => H(() => document.querySelector('#logFields input[name="temp"]')), 'log form');
    await H(() => { document.querySelector('#logFields input[name="temp"]').value = '78'; });
    await H(() => document.querySelector('#logForm button[value="save"]').click());
    await p.waitForTimeout(500);
    const [dl] = await Promise.all([p.waitForEvent('download'), H(() => document.getElementById('exportBtn').click())]);
    const file = `${OUT}/backup.json`;
    await dl.saveAs(file);
    const data = JSON.parse(fs.readFileSync(file, 'utf8'));
    await p.setInputFiles('#importFile', file);
    await p.waitForTimeout(800);
    await page('tracker');
    const alerts = dialogs.filter((d) => d.startsWith('alert'));
    if (alerts.length) throw new Error(alerts.join(' | '));
    await H(() => document.querySelector('.tab[data-tab="live"]').click());
    return `${data.readings.length} readings in backup`;
  });
  await step('QA hooks', async () => {
    const s = await H(() => window.reeflive.state());
    const c = await H(() => window.reeflive.cam());
    if (!s || !JSON.parse(s)?.organisms) throw new Error(`state ${s?.slice?.(0, 80)}`);
    if (!c || c === 'null') throw new Error('cam');
    return `state ok, cam ${c.slice(0, 40)}`;
  });
  console.log('dialogs', dialogs);
} finally {
  await b.close();
  srv.close();
}
const ok = results.filter((r) => r.ok).length;
console.log(`\n${label}: ${ok}/${results.length} steps passed in ${((Date.now() - t0) / 1000).toFixed(0)} s`);
console.log('page errors:', logs.length ? `\n  ${logs.slice(0, 30).join('\n  ')}` : 'none');
fs.writeFileSync(`${OUT}/results.json`, JSON.stringify({ results, logs }, null, 1));
