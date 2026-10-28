"""metrics.py run.csv [startHour=8] : per-issue metrics for the behaviour QA."""
import pandas as pd, numpy as np, sys
f = sys.argv[1]; h0 = float(sys.argv[2]) if len(sys.argv) > 2 else 8.0
d = pd.read_csv(f)
if 'disp' not in d.columns: d['disp'] = d['beh']
d['disp'] = d['disp'].fillna(d['beh'])
d['hour'] = (d.t / 3600 + h0) % 24
dt = d[d.name == 'Dori'].t.diff().median()
WL = 38.1
print(f, 'sample dt %.2f s, span %.1f h' % (dt, d.t.max() / 3600))

def runs(g, col, val):
    m = (g[col] == val).values; t = g.t.values
    out = []; st = None
    for i in range(len(m)):
        if m[i] and st is None: st = t[i]
        if not m[i] and st is not None: out.append(t[i] - st); st = None
    if st is not None: out.append(t[-1] - st + dt)
    return out

g = d[d.name == 'Dori'].sort_values('t')
day = g[(g.hour >= 10) & (g.hour < 19)]
r = runs(g, 'beh', 'Racing across the tank')
print('1) Dori racing: share %.1f%%, runs %d, max run %.0f s, median %.0f s' % (100 * (g.beh == 'Racing across the tank').mean(), len(r), max(r) if r else 0, np.median(r) if r else 0))
r = runs(g, 'beh', 'Hiding under the rock')
print('2) Dori hiding: share all %.1f%% day %.1f%%, runs %d, max %.0f s, median %.0f s, night share hiding %.1f%%' % (
    100 * (g.beh == 'Hiding under the rock').mean(), 100 * (day.beh == 'Hiding under the rock').mean(), len(r), max(r) if r else 0, np.median(r) if r else 0,
    100 * (g[(g.hour >= 21) | (g.hour < 7)].beh == 'Hiding under the rock').mean()))
first = g[g.beh == 'Hiding under the rock'].t.min()
print('   first hide at %.0f s' % first if first == first else '   no hide')
print('5) display label changes per sim-min / A-B-A within 8 s (displayed label; raw in brackets)')
for n in ['Sandy', 'Dennis', 'Dallas', 'Shakira', 'Nemo', 'Marlin', 'Dori', 'Hermit 1']:
    gg = d[d.name == n].sort_values('t')
    if gg.empty: continue
    mins = (gg.t.max() - gg.t.min()) / 60
    def stat(col):
        b = gg[col].values; ch = (b[1:] != b[:-1]).sum()
        rr = []; cur = None
        for t, bh in gg[['t', col]].values:
            if bh != cur: rr.append([bh, t]); cur = bh
        aba = sum(1 for i in range(2, len(rr)) if rr[i][0] == rr[i - 2][0] and rr[i][1] - rr[i - 2][1] < 8)
        return ch / mins, aba
    a, b = stat('disp'); c, e = stat('beh')
    print('   %-10s %.2f/min ABA %d   [raw %.2f/min ABA %d]' % (n, a, b, c, e))
print('7) blennies at night (21-07h): share asleep (sleep flag / label)')
for n in ['Dennis', 'Dallas']:
    gg = d[(d.name == n) & ((d.hour >= 21) | (d.hour < 7))]
    if gg.empty: continue
    print('   %-8s sleep %.0f%%  label-asleep %.0f%%  top %s' % (n, 100 * gg.sleep.mean(), 100 * gg.disp.str.startswith('Asleep').mean(), gg.disp.value_counts().index[0]))
print('8) hermits: footprint p5-p95 (x,y,z cm), share within 8 cm of waterline, speed p50 (1 s displacement), top labels')
for n in ['Hermit 1', 'Hermit 2', 'Mr. Crabs']:
    gg = d[d.name == n].sort_values('t')
    x, y, z = gg.sx, gg.sy, gg.sz
    ext = [q.quantile(.95) - q.quantile(.05) for q in (x, y, z)]
    disp = np.sqrt(x.diff() ** 2 + y.diff() ** 2 + z.diff() ** 2) / gg.t.diff()
    print('   %-9s ext %.0f,%.0f,%.0f  top-band %.0f%%  speed(disp) p50 %.2f p90 %.2f, vel p50 %.2f cm/s  %s' % (n, ext[0], ext[1], ext[2], 100 * (y > WL - 8).mean(), disp.quantile(.5), disp.quantile(.9), gg.spd.quantile(.5),
          dict((gg.disp.value_counts(normalize=True) * 100).round(0).head(4))))
