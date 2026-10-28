// Runs job files (jobs/NAME.mjs, default export async (ctx)=>...) that I write locally; no network listener.
import { open, shot, Q } from './lib.mjs';
import fs from 'fs';
fs.mkdirSync(`${Q}/jobs`, { recursive: true });
// usage: [W=800 H=450 QA_DIR=dir QA_URL=url] node driver.mjs   (then drop jobs/NAME.mjs files into $QA_DIR/jobs)
const { b, p, logs } = await open(+process.env.W || 800, +process.env.H || 450);
console.log('ready');
const seen = new Set();
for (;;) {
  for (const f of fs.readdirSync(`${Q}/jobs`).filter(f => f.endsWith('.mjs')).sort()) {
    if (seen.has(f)) continue; seen.add(f);
    const t0 = Date.now(); const out = [];
    const L = (...a) => out.push(a.map(x => typeof x === 'string' ? x : JSON.stringify(x)).join(' '));
    try { const m = await import(`${Q}/jobs/${f}?${Date.now()}`); await m.default({ p, shot, L, logs, Q, fs, b }); }
    catch (e) { L('ERR', e.message.slice(0, 300)); }
    fs.writeFileSync(`${Q}/jobs/${f}.out`, out.join('\n') + `\n(done ${((Date.now() - t0) / 1000).toFixed(0)}s)\n`);
  }
  await new Promise(r => setTimeout(r, 1000));
}
