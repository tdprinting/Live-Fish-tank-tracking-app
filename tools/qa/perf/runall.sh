#!/bin/bash
# usage: runall.sh <siteA> <siteB> [queryB]   e.g. main's build vs a branch's: profiles both at 1x and 60x, two rounds,
# interleaved (a shared machine drifts), then writes $QA_OUT/summary.txt. Output in $QA_OUT (default ./qa-out).
set -e
A=$(realpath "$1"); B=$(realpath "$2"); QB=${3:-}
HERE=$(cd "$(dirname "$0")" && pwd)
export QA_OUT=$(realpath -m "${QA_OUT:-qa-out}"); mkdir -p "$QA_OUT"; cd "$QA_OUT"
for round in 1 2; do
  for speed in 1 60; do
    STUB=1 node "$HERE/prof.mjs" "$A" a_${speed}x_$round $speed 10 > a_${speed}x_$round.log 2>&1
    QA_QUERY=$QB STUB=1 node "$HERE/prof.mjs" "$B" b_${speed}x_$round $speed 10 > b_${speed}x_$round.log 2>&1
  done
done
for l in a b; do for speed in 1 60; do for round in 1 2; do
  echo "== ${l}_${speed}x_$round"; node "$HERE/analyze.mjs" ${l}_${speed}x_$round 0 2>&1 | head -40
done; done; done > summary.txt
node "$HERE/table.mjs" a_1x_1 a_1x_2 a_60x_1 a_60x_2 b_1x_1 b_1x_2 b_60x_1 b_60x_2 | tee table.txt
