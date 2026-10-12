"""Generates the tileable UI caustic net used by CausticBackground (UI_STYLE §2, layer 3).

Voronoi edge intensity over a periodic jittered grid, 3 octaves, gamma 2.2, written as a
white-on-black grayscale WebP: app/src/main/res/drawable-nodpi/caustic_tile.webp (512x512).

    python3 tools/textures/ui_caustic_tile.py app/src/main/res/drawable-nodpi/caustic_tile.webp
"""
import sys

import numpy as np
from PIL import Image

SIZE = 512


def voronoi_edges(size, cells, seed):
    rng = np.random.default_rng(seed)
    pts = (np.stack(np.meshgrid(np.arange(cells), np.arange(cells), indexing="ij"), -1) + rng.random((cells, cells, 2))) / cells
    pts = pts.reshape(-1, 2)
    # periodic copies so the tile wraps seamlessly
    offs = np.array([[dx, dy] for dx in (-1, 0, 1) for dy in (-1, 0, 1)])
    allp = (pts[None, :, :] + offs[:, None, :]).reshape(-1, 2)
    xs = (np.arange(size) + 0.5) / size
    f1 = np.empty((size, size))
    f2 = np.empty((size, size))
    for y in range(0, size, 8):
        ys = (np.arange(y, y + 8) + 0.5) / size
        g = np.stack(np.meshgrid(xs, ys), -1).reshape(-1, 1, 2)
        d = np.sqrt(((g - allp[None, :, :]) ** 2).sum(-1))
        part = np.partition(d, 1, axis=1)
        f1[y:y + 8] = part[:, 0].reshape(8, size)
        f2[y:y + 8] = part[:, 1].reshape(8, size)
    edge = (f2 - f1) * cells  # 0 on edges
    v = np.clip(1.0 - edge / 0.22, 0, 1) ** 3
    return v


def main(out):
    acc = np.zeros((SIZE, SIZE))
    for octave, (cells, w) in enumerate([(6, 1.0), (11, 0.55), (19, 0.3)]):
        acc += w * voronoi_edges(SIZE, cells, 7 + octave)
    acc /= acc.max()
    acc = acc ** (1 / 2.2)
    img = Image.fromarray((acc * 255).astype(np.uint8), "L")
    img.save(out, "WEBP", quality=88, method=6)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1])
