import pandas as pd
import os
B = os.environ.get('QA_BQA_DIR', 'qa-out/bqa') + '/'   # directory holding the run CSVs named in fs below (edit fs to your runs)
fs = {'1x base': 'outb/x1.csv', '1x new': 'out_v5/x1.csv', '60x base': 'outb/x60.csv', '60x new': 'out_v5/x60.csv', '240x base': 'outb/x240.csv', '240x new': 'out_v5/x240.csv'}
r = {}
for k, f in fs.items():
    d = pd.read_csv(B + f, usecols=['t', 'name', 'hunger']); d = d[d.t < 6 * 3600]; r[k] = d.groupby('name').hunger.mean()
print(pd.DataFrame(r).round(2).to_string())
