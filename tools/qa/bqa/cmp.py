"""cmp.py a.csv b.csv ... [--from H --to H] : per-animal hunger mean, behaviour shares in a sim-time window."""
import pandas as pd, numpy as np, sys
args = sys.argv[1:]
fr, to = 0.0, 1e9
if '--from' in args:
    i = args.index('--from'); fr = float(args[i + 1]) * 3600; del args[i:i + 2]
if '--to' in args:
    i = args.index('--to'); to = float(args[i + 1]) * 3600; del args[i:i + 2]
mode = 'hunger'
if '--beh' in args: mode = 'beh'; args.remove('--beh')
fs = args
ds = {}
for f in fs:
    d = pd.read_csv(f, usecols=['t', 'name', 'kind', 'beh', 'hunger', 'sleep'])
    d = d[(d.t >= fr) & (d.t < to)]
    ds['/'.join(f.split('/')[-2:]).replace('.csv','')] = d
names = sorted(set.intersection(*[set(d.name) for d in ds.values()]))
if mode == 'hunger':
    print('%-24s' % 'name' + ''.join('%22s' % k for k in ds))
    for n in names:
        row = '%-24s' % n
        for k, d in ds.items():
            g = d[d.name == n]
            # mean, p10, p90
            row += '%8.2f %5.2f %5.2f ' % (g.hunger.mean(), g.hunger.quantile(.1), g.hunger.quantile(.9))
        print(row)
else:
    for n in names:
        print('==', n)
        sh = {}
        for k, d in ds.items():
            g = d[d.name == n]
            sh[k] = (g.beh.value_counts(normalize=True) * 100)
        t = pd.DataFrame(sh).fillna(0).round(1)
        t = t[(t.max(axis=1) >= 2)]
        print(t.sort_values(t.columns[0], ascending=False).to_string())
