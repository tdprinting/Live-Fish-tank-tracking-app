#!/usr/bin/env python3
"""Summarises the JUnit XML of the "Core tests" shards: totals, the slowest classes and methods, and per-bin wall time.

Usage: test_report.py <results dir> [--weights]
--weights prints measured lines in the core/test-weights.txt format (classes >= 20 s whole, the split classes by method)
to refresh the shard packing.
"""
import collections
import glob
import os
import sys
import xml.etree.ElementTree as ET

root = sys.argv[1]
classes, methods = [], []
bins = collections.defaultdict(float)
tests = skipped = failed = 0
for f in sorted(glob.glob(os.path.join(root, '**', 'TEST-*.xml'), recursive=True)):
    r = ET.parse(f).getroot()
    t = float(r.get('time', 0))
    # .../test-results/testBin7/TEST-x.xml -> testBin7
    bins[os.path.basename(os.path.dirname(f))] += t
    classes.append((t, r.get('name'), int(r.get('tests', 0))))
    tests += int(r.get('tests', 0))
    skipped += int(r.get('skipped', 0))
    failed += int(r.get('failures', 0)) + int(r.get('errors', 0))
    for tc in r.iter('testcase'):
        methods.append((float(tc.get('time', 0)), tc.get('classname').rsplit('.', 1)[-1], tc.get('name')))

if '--weights' in sys.argv:
    split = {'RealismAcceptanceTest', 'AiptasiaTest', 'AiTest'}
    group = ['c03_cleanerServicesTang', 'c04_cleanerCooldown', 'c12_tangNeverBullies', 'c13_cardinalDiel']
    for t, name, _ in sorted(classes, reverse=True):
        simple = name.rsplit('.', 1)[-1]
        if simple not in split and t >= 20:
            print(round(t), simple)
    g = sum(t for t, c, m in methods if c == 'RealismAcceptanceTest' and m in group)
    print(round(g), ' + '.join('RealismAcceptanceTest.' + m for m in group))
    for t, c, m in sorted(methods, reverse=True):
        if c in split and not (c == 'RealismAcceptanceTest' and m in group):
            print(max(1, round(t)), c + '.' + m)
    sys.exit(0)

print(f'tests {tests}, skipped {skipped}, failed {failed}, classes {len(classes)}, '
      f'test time {sum(c[0] for c in classes) / 60:.1f} min')
print('\nper bin (one JVM each), minutes:')
for b, t in sorted(bins.items(), key=lambda x: -x[1]):
    print(f'  {t / 60:5.1f}  {b}')
print('\nslowest classes:')
for t, name, n in sorted(classes, reverse=True)[:15]:
    print(f'  {t:7.1f} s  {name.rsplit(".", 1)[-1]} ({n} tests)')
print('\nslowest methods:')
for t, c, m in sorted(methods, reverse=True)[:15]:
    print(f'  {t:7.1f} s  {c}.{m}')
