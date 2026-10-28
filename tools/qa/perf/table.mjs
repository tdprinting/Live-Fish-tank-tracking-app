// usage: node table.mjs label...: one line per run with the headline numbers (main thread, ×4 CPU, GPU stubbed)
import { execFileSync } from 'child_process';
import path from 'path';
import fs from 'fs';
const OUT = path.resolve(process.env.QA_OUT || 'qa-out'), HERE = path.dirname(new URL(import.meta.url).pathname);
const pick = (txt, re) => { let s = 0; for (const l of txt.split('\n')) { const m = /^\s*([\d.]+) ms\/s\s+(.*)$/.exec(l); if (m && re.test(m[2])) s += +m[1]; } return s; };
console.log('run        fps  pfFrames/s  render ms/frame  sim ms/s(adv+snap)  |  fps  rAF ms/f  sim ms/s  snap ms/s  render ms/s  rocks ms/s  timers ms/f  style+layout ms/f  GC ms/s  minorGC/s  majorGC/s  worstGC ms  frame-loop ms/s');
for (const label of process.argv.slice(2)) {
  const txt = execFileSync('node', [`${HERE}/analyze.mjs`, label, '0'], { env: { ...process.env, INCL: '.' } }).toString();
  const j = JSON.parse(txt.slice(0, txt.indexOf('\n---')));
  const adv = pick(txt, /^advanceInternal /), snap = pick(txt, /^snapshot ReefLive/), rend = pick(txt, /^protoOf\.x5c ReefLive-web\.mjs:30351$|^protoOf\.\w+ ReefLive-web\.mjs:\d+$/.test('') ? /x/ : /^protoOf\.(x5c|e5d|n5d) ReefLive-web.mjs:30(351|358|437) *$/), rocks = pick(txt, /^stepRocks /), loop = pick(txt, /^frame_1 /);
  const f = (v, d = 1) => String(v.toFixed(d)).padStart(9);
  const meta = JSON.parse(fs.readFileSync(`${OUT}/${label}.meta.json`, 'utf8'));
  const pfps = (meta.profFrames || 0) / j.profileSpan;
  process.stdout.write(label.padEnd(9) + f(pfps) + f(pfps ? rend / pfps : 0, 2) + f(adv + snap) + '  |');
  console.log('', f(j.fps), f(j.msPerFrame.rAF), f(adv), f(snap), f(rend), f(rocks), f(j.msPerFrame.timers, 2), f(j.msPerFrame.style + j.msPerFrame.layout, 2), f(j.gc.msPerSec), f(j.gc.minorPerSec), f(j.gc.majorPerSec, 2), f(j.gc.worstMs), f(loop));
}
