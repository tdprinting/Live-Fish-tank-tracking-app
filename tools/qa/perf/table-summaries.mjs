// usage: node table-summaries.mjs : one line per <label>.summary.json in $QA_OUT (default ./qa-out), written by analyze.mjs
import path from 'path';
import fs from 'fs';
const dir = path.resolve(process.env.QA_OUT || 'qa-out') + '/';
for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.summary.json')).sort()) {
  const s = JSON.parse(fs.readFileSync(dir + f));
  const busyMsPerSec = (s.busyPct * 10).toFixed(0);
  console.log(s.label.padEnd(18), 'fps', String(s.fps).padStart(5), 'busy', String(s.busyPct).padStart(5) + '%', '=', busyMsPerSec.padStart(4), 'ms/s', ' rAF ms/frame', String(s.msPerFrame.rAF).padStart(6), ' rAF ms/s', (s.msPerFrame.rAF * s.fps).toFixed(0).padStart(4), ' gc ms/s', s.gc.msPerSec, ' long', s.page?.long);
}
