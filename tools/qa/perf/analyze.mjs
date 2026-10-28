// usage: node analyze.mjs <label> [topN]
import fs from 'fs';
import path from 'path';
const OUT = path.resolve(process.env.QA_OUT || 'qa-out'); fs.mkdirSync(OUT, { recursive: true });
const [label, topArg = '30'] = process.argv.slice(2);
const ev = JSON.parse(fs.readFileSync(`${OUT}/${label}.trace.json`, 'utf8'));
// renderer main thread
const names = ev.filter((e) => e.ph === 'M' && e.name === 'thread_name');
const main = names.find((e) => e.args.name === 'CrRendererMain' && ev.some((x) => x.pid === e.pid && x.name === 'FireAnimationFrame'));
const { pid, tid } = main;
const M = ev.filter((e) => e.pid === pid && e.tid === tid);
const ts = M.filter((e) => e.ts).map((e) => e.ts);
const t0 = Math.min(...ts), t1 = Math.max(...ts);
const span = (t1 - t0) / 1e6;
const sumDur = (pred) => M.filter((e) => e.ph === 'X' && pred(e)).reduce((a, e) => a + (e.dur || 0), 0) / 1000;
const cnt = (pred) => M.filter((e) => e.ph === 'X' && pred(e)).length;
const frames = M.filter((e) => e.ph === 'X' && e.name === 'FireAnimationFrame');
const nFrames = frames.length;
// rAF callbacks on the same frame share one FireAnimationFrame each; frames = distinct BeginMainThreadFrame
const bmf = M.filter((e) => e.name === 'BeginMainThreadFrame').length;
const tasks = M.filter((e) => e.ph === 'X' && (e.name === 'RunTask' || e.name === 'ThreadControllerImpl::RunTask'));
const busy = tasks.reduce((a, e) => a + e.dur, 0) / 1000;
const gcNames = new Set(['MinorGC', 'MajorGC', 'V8.GCScavenger', 'V8.GCIncrementalMarking', 'V8.GCFinalizeMC', 'V8.GC_SCAVENGER', 'V8.GC_MC_INCREMENTAL', 'V8.GCCompactor', 'V8.GC_MARK_COMPACTOR']);
const gcEv = M.filter((e) => e.ph === 'X' && (e.name === 'MinorGC' || e.name === 'MajorGC'));
const gcMs = gcEv.reduce((a, e) => a + e.dur, 0) / 1000;
const minor = M.filter((e) => e.ph === 'X' && e.name === 'MinorGC'), major = M.filter((e) => e.ph === 'X' && e.name === 'MajorGC');
const cat = {
  rAF: sumDur((e) => e.name === 'FireAnimationFrame'),
  timers: sumDur((e) => e.name === 'TimerFire'),
  events: sumDur((e) => e.name === 'EventDispatch'),
  style: sumDur((e) => e.name === 'UpdateLayoutTree'),
  layout: sumDur((e) => e.name === 'Layout'),
  paint: sumDur((e) => e.name === 'Paint' || e.name === 'PrePaint'),
  composite: sumDur((e) => e.name === 'Commit' || e.name === 'Layerize' || e.name === 'UpdateLayer'),
  gc: gcMs,
};
const out = { label, span: +span.toFixed(2), frames: bmf || nFrames, fps: +((bmf || nFrames) / span).toFixed(1), busyPct: +(100 * busy / (span * 1000)).toFixed(1) };
out.msPerFrame = Object.fromEntries(Object.entries(cat).map(([k, v]) => [k, +(v / Math.max(1, out.frames)).toFixed(2)]));
out.gc = { minorPerSec: +(minor.length / span).toFixed(1), majorPerSec: +(major.length / span).toFixed(2), msPerSec: +(gcMs / span).toFixed(1), worstMs: +Math.max(0, ...gcEv.map((e) => e.dur / 1000)).toFixed(1) };
const ftimes = frames.map((e) => e.dur / 1000).sort((a, b) => a - b);
out.rafMs = { p50: +(ftimes[Math.floor(ftimes.length * 0.5)] || 0).toFixed(1), p90: +(ftimes[Math.floor(ftimes.length * 0.9)] || 0).toFixed(1), max: +(ftimes.at(-1) || 0).toFixed(1) };
const timers = M.filter((e) => e.ph === 'X' && e.name === 'TimerFire').map((e) => e.dur / 1000).sort((a, b) => b - a);
out.timerWorst = timers.slice(0, 3).map((v) => +v.toFixed(1));

const nodes = new Map(); const samples = []; const deltas = [];
let pspan = span;
if (fs.existsSync(`${OUT}/${label}.cpuprofile`)) {
  const cp = JSON.parse(fs.readFileSync(`${OUT}/${label}.cpuprofile`, 'utf8'));
  for (const n of cp.nodes) nodes.set(n.id, n);
  for (const n of cp.nodes) for (const c of n.children || []) nodes.get(c).parent = n.id;
  samples.push(...cp.samples); deltas.push(...cp.timeDeltas);
  pspan = (cp.endTime - cp.startTime) / 1e6;
}
const self = new Map(), incl = new Map();
const key = (n) => { const f = n.callFrame; return `${f.functionName || '(anon)'} ${(f.url || '').split('/').pop()}:${f.lineNumber ?? ''}`; };
let total = 0;
for (let i = 0; i < samples.length; i++) {
  const dt = (deltas[i + 1] ?? deltas[i] ?? 0) / 1000; total += dt;
  let n = nodes.get(samples[i]); if (!n) continue;
  const k = key(n); self.set(k, (self.get(k) || 0) + dt);
  const seen = new Set();
  while (n) { const kk = key(n); if (!seen.has(kk)) { seen.add(kk); incl.set(kk, (incl.get(kk) || 0) + dt); } n = n.parent != null ? nodes.get(n.parent) : null; }
}
const top = (m) => [...m.entries()].sort((a, b) => b[1] - a[1]).slice(0, +topArg).map(([k, v]) => `${(v / pspan).toFixed(1).padStart(7)} ms/s  ${k}`);
out.profiledMsPerSec = +(total / pspan).toFixed(1); out.profileSpan = +pspan.toFixed(1);
if (fs.existsSync(`${OUT}/${label}.meta.json`)) out.page = JSON.parse(fs.readFileSync(`${OUT}/${label}.meta.json`, 'utf8')).pc;
console.log(JSON.stringify(out, null, 1));
console.log('--- top self (ms per wall second)'); console.log(top(self).join('\n'));
const want = process.env.INCL ? new RegExp(process.env.INCL) : /stepWall|snapshot|onDrawFrame|advanceInternal|frame |tick |state |panel |animals |bubbles|coralPick|tickBags|tickCorals|stats |JSON|stringify|parse/;
console.log('--- inclusive (selected)'); console.log([...incl.entries()].filter(([k]) => want.test(k)).sort((a, b) => b[1] - a[1]).slice(0, 40).map(([k, v]) => `${(v / pspan).toFixed(1).padStart(7)} ms/s  ${k}`).join('\n'));
fs.writeFileSync(`${OUT}/${label}.summary.json`, JSON.stringify({ ...out, self: top(self), }, null, 1));
