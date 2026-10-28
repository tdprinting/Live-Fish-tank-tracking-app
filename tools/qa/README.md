# QA tools

Scripts from earlier QA sessions, kept so a later session can re-run them. Nothing here is part of CI.

Common rules:

- Chromium and Playwright are preinstalled (`/opt/pw-browsers/chromium`, `/opt/node22/lib/node_modules`). **Never run `playwright install`.**
  Override with `QA_BROWSER` (binary) and `QA_NODE_MODULES` (directory that contains `playwright`).
- Scripts that serve a built site (`web/build/site` after `./gradlew -PcoreOnly :web:site --offline`) pick a free port; set `QA_PORT` to fix it.
  Scripts that need an already running server take `QA_URL`.
- Output goes to `$QA_OUT` (default `./qa-out`, git-ignored).
- **Stub GPU draws on a loaded machine.** SwiftShader draws make the frame rate depend on machine load, so timings drift. `flows.mjs` and
  `storewarm.mjs` stub `draw*`/`clear` by default (`GL=1` draws for real); `smoke.mjs` and `prof.mjs` stub with `STUB=1`. Use real GL only for screenshots.
- Kill what you start (servers, browsers) when done; a long run left behind slows the next one.

| Tool | Usage |
| --- | --- |
| `storewarm.mjs` | `node tools/qa/storewarm.mjs <siteDir> [fps=5] [label]`: time from tapping a store to its shop UI with `requestAnimationFrame` held to `fps`; prints one JSON line. Needs a real build (`./gradlew -PcoreOnly :web:site --offline`): the page code is bundled into `app.js`, so copying `web/site/*.js` into an older build changes nothing. |
| `flows/flows.mjs` | `node tools/qa/flows/flows.mjs <siteDir> <label> [query]`: drives the main flows through the page's own UI (boot, My tank, store, care, sandbox ...), PASS/FAIL per step, screenshots in `$QA_OUT/<label>/`. |
| `flows/smoke.mjs` | `node tools/qa/flows/smoke.mjs <siteDir> <label> [query] [seconds]`: loads the page and watches it for errors, FPS and a screenshot (`FOLLOW=1` rides along with a fish). |
| `flows/robust.mjs` | `node tools/qa/flows/robust.mjs <siteDir>`: save-on-close and sim-worker crash-recovery checks. |
| `driver/driver.mjs` | `QA_URL=http://localhost:8765/?stats QA_DIR=/some/dir node tools/qa/driver/driver.mjs`: keeps one browser open and runs `jobs/NAME.mjs` files (default export `async ({p, shot, L, logs, Q, fs, b}) => ...`) dropped in `$QA_DIR/jobs`; results in `NAME.mjs.out`. Needs a server on `QA_URL`. |
| `perf/prof.mjs` | `[STUB=1] [QA_QUERY=noworker] node tools/qa/perf/prof.mjs <siteDir> <label> [speed=1] [seconds=10]`: phone-class (412x915, CPU x4) trace plus V8 sampling profile into `$QA_OUT`. |
| `perf/analyze.mjs` | `node tools/qa/perf/analyze.mjs <label> [topN]`: summarises `<label>.trace.json` (main-thread time by function, GC, frames). |
| `perf/table.mjs` | `node tools/qa/perf/table.mjs <label>...`: one headline-numbers line per profiled run; `table-summaries.mjs` prints every `*.summary.json` in `$QA_OUT`. |
| `perf/cpu.mjs` | `node tools/qa/perf/cpu.mjs <siteDir> <label> <speed> <seconds>`: renderer CPU seconds from `/proc` (robust to machine load) next to the frame count. |
| `perf/runall.sh` | `tools/qa/perf/runall.sh <siteA> <siteB> [queryB]`: profiles two builds at 1x and 60x, two interleaved rounds, then `summary.txt` and a table. |
| `load/serve.mjs` | `node tools/qa/load/serve.mjs <root> <port> <mbps> <rttMs>`: HTTP/2 static server like GitHub Pages (gzip text, ETag, shaped bandwidth and latency; `0 0` = unshaped); makes a self-signed cert with openssl. |
| `load/measure.mjs` | `node tools/qa/load/measure.mjs <port> <label> <cpuRate> <siteDir> [mbps=9] [rttMs=100]`: cold and warm page load through the shaped server (starts and stops `serve.mjs`). |
| `load/update.mjs` | `node tools/qa/load/update.mjs <oldSite> <newSite> <port>`: a visitor with the old build's service worker gets the new build. |
| `BehaviourQa` (Kotlin) | `./gradlew -PcoreOnly -Pbqa -PbqaOut=/tmp/run.csv -PbqaSpeed=60 -PbqaMinutes=360 -PbqaStartHour=8 [-PbqaEvery=N -PbqaSeed=N] :core:test --offline --tests '*BehaviourQa'`: headless run of the default tank, one CSV row per animal per sample. Opt-in like `SimBenchmark`'s `-Pbench`; `core/src/test/kotlin/com/reeflive/core/sim/BehaviourQa.kt`. |
| `bqa/metrics.py` | `python3 tools/qa/bqa/metrics.py run.csv [startHour=8]`: per-issue behaviour metrics for one run. |
| `bqa/cmp.py` | `python3 tools/qa/bqa/cmp.py a.csv b.csv ... [--from H --to H] [--beh]`: per-animal hunger mean (or behaviour shares) between runs, in a sim-time window. |
| `bqa/htab.py` | `QA_BQA_DIR=dir python3 tools/qa/bqa/htab.py`: hunger table over fixed run files (edit the `fs` map at the top for your runs). |

Python tools need `pandas` and `numpy`.
