"""Vectorized 3D noise in numpy (value noise fBm + Worley) for rock displacement and texturing."""
import numpy as np


def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791) \
        ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float64) / float(0xFFFFFF)


def value3(p, scale, seed=0):
    q = np.asarray(p, float) / scale
    i = np.floor(q)
    f = q - i
    f = f * f * (3 - 2 * f)
    ix, iy, iz = i[:, 0].astype(np.int64), i[:, 1].astype(np.int64), i[:, 2].astype(np.int64)
    out = 0.0
    for dx in (0, 1):
        wx = f[:, 0] if dx else 1 - f[:, 0]
        for dy in (0, 1):
            wy = f[:, 1] if dy else 1 - f[:, 1]
            for dz in (0, 1):
                wz = f[:, 2] if dz else 1 - f[:, 2]
                out = out + wx * wy * wz * _hash(ix + dx, iy + dy, iz + dz, seed)
    return out


def fbm3(p, scale, octaves=4, seed=0, gain=0.5):
    acc = 0.0
    amp = 1.0
    tot = 0.0
    for o in range(octaves):
        acc = acc + amp * value3(p, scale / (2 ** o), seed + 31 * o)
        tot += amp
        amp *= gain
    return acc / tot


def worley3(p, cell, seed=0, chunk=200000):
    """Returns F1, F2 distances (same units as p) to jittered feature points (one per cell)."""
    p = np.asarray(p, float)
    F1 = np.empty(len(p))
    F2 = np.empty(len(p))
    for s in range(0, len(p), chunk):
        q = p[s:s + chunk] / cell
        i = np.floor(q).astype(np.int64)
        f1 = np.full(len(q), 1e9)
        f2 = np.full(len(q), 1e9)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    cx, cy, cz = i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz
                    fx = cx + _hash(cx, cy, cz, seed)
                    fy = cy + _hash(cx, cy, cz, seed + 1)
                    fz = cz + _hash(cx, cy, cz, seed + 2)
                    d = np.sqrt((q[:, 0] - fx) ** 2 + (q[:, 1] - fy) ** 2 + (q[:, 2] - fz) ** 2)
                    m = d < f1
                    f2 = np.where(m, f1, np.minimum(f2, d))
                    f1 = np.where(m, d, f1)
        F1[s:s + chunk] = f1 * cell
        F2[s:s + chunk] = f2 * cell
    return F1, F2
