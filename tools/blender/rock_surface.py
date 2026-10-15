"""Rock surface pass for rockscape.glb (numpy only; runs in plain python3 or inside Blender after the SDF bake).

The decimated LOD0 rock is crumpled at its own triangle scale (~0.8 cm edges): its vertex normals swing ~30 degrees
from one corner of a triangle to the next, and every lighting term built on them showed the triangles as facets up
close. This pass leaves the positions (and so the collision SDF, the anchors and everything resting on the rock)
untouched and rewrites only two attributes in place:

  NORMAL   area-weighted face normals, welded across UV seams and smoothed over a ~2 cm patch. The fine relief the
           smoothing removes comes back in rock.frag as procedural relief whose octaves fade with the pixel
           footprint (the rock's distance detail levels).
  COLOR_0  r cavity (concave pockets at ~1.5 cm, 0..1), g ridge (convex edges, 0..1), b open sky (SDF ambient
           occlusion: 1 open water, 0 deep in a crevice or under an overhang), a the broad limestone blotch noise
           (common.glsl fbm3 at 0.05 / cm, a 20 cm pattern the vertices resolve easily). rock.frag reads these instead
           of working them out per pixel: curvature was costly and broke along the triangle edges, and the blotch
           noise cost three noise octaves per pixel.

Usage: python3 tools/blender/rock_surface.py [path.glb ...]   (default: models/rockscape.glb; needs sim/rock_sdf.bin)
Idempotent: everything is derived from the positions and the SDF.
"""
import json
import os
import struct
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")
TANK_D = 45.7
SAND_Y, SAND_Y_BACK = 3.5, 6.0

NORMAL_SMOOTH_ITERS = 4     # ~2 cm with 0.8 cm edges
CURV_SMOOTH_ITERS = 3
AO_STEPS = ((0.8, 0.30), (1.6, 0.30), (3.0, 0.25), (5.0, 0.15))  # (distance cm, weight)


def _load(path):
    b = bytearray(open(path, "rb").read())
    jl = struct.unpack_from("<I", b, 12)[0]
    j = json.loads(bytes(b[20:20 + jl]))
    bin_off = 20 + jl + 8
    return b, j, bin_off


def _view(b, j, bin_off, i):
    a = j["accessors"][i]
    bv = j["bufferViews"][a["bufferView"]]
    n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
    dt = {5126: np.float32, 5125: np.uint32, 5123: np.uint16, 5121: np.uint8}[a["componentType"]]
    off = bin_off + bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = bv.get("byteStride", 0)
    assert stride in (0, n * np.dtype(dt).itemsize), "interleaved buffers not supported"
    arr = np.frombuffer(b, dtype=dt, count=a["count"] * n, offset=off)
    return (arr.reshape(-1, n) if n > 1 else arr), off


def load_sdf(path):
    raw = open(path, "rb").read()
    assert raw[:4] == b"RSDF"
    nx, ny, nz = struct.unpack_from("<3I", raw, 4)
    ox, oy, oz, cell = struct.unpack_from("<4f", raw, 16)
    d = np.frombuffer(raw, dtype="<f2", count=nx * ny * nz, offset=32).astype(np.float32).reshape(nz, ny, nx)
    return d, np.array([ox, oy, oz], np.float32), cell


def sdf_sample(grid, origin, cell, p):
    """Trilinear SDF (cm, > 0 outside the rock) at points p (N x 3); clamped to the grid."""
    nz, ny, nx = grid.shape
    g = (p - origin) / cell
    g = np.clip(g, 0, np.array([nx - 1.001, ny - 1.001, nz - 1.001]))
    i = np.floor(g).astype(np.int64)
    f = g - i
    x0, y0, z0 = i[:, 0], i[:, 1], i[:, 2]
    out = np.zeros(len(p), np.float32)
    for dz in (0, 1):
        for dy in (0, 1):
            for dx in (0, 1):
                w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                out += w * grid[z0 + dz, y0 + dy, x0 + dx]
    return out


def _fract(x):
    return x - np.floor(x)


def hash13(p):
    """common.glsl hash13 (float32, so the baked noise matches the shader's lattice closely enough)."""
    p = _fract(p.astype(np.float32) * np.float32(0.1031))
    p = p + (p * (p[:, ::-1] + np.float32(31.32))).sum(1, keepdims=True)
    return _fract((p[:, 0] + p[:, 1]) * p[:, 2])


def vnoise(p):
    i = np.floor(p)
    f = p - i
    u = f * f * (3 - 2 * f)
    c = {}
    for dz in (0, 1):
        for dy in (0, 1):
            for dx in (0, 1):
                c[dx, dy, dz] = hash13(i + np.array([dx, dy, dz], np.float32))
    lerp = lambda a, b, t: a + (b - a) * t
    a = lerp(lerp(c[0, 0, 0], c[1, 0, 0], u[:, 0]), lerp(c[0, 1, 0], c[1, 1, 0], u[:, 0]), u[:, 1])
    b = lerp(lerp(c[0, 0, 1], c[1, 0, 1], u[:, 0]), lerp(c[0, 1, 1], c[1, 1, 1], u[:, 0]), u[:, 1])
    return lerp(a, b, u[:, 2])


def fbm3(p):
    return vnoise(p) * 0.5 + vnoise(p * 2.03 + 17.1) * 0.3 + vnoise(p * 4.11 + 41.7) * 0.2


def _smooth(vals, nbr_i, nbr_j, deg, iters):
    for _ in range(iters):
        acc = vals.copy()
        np.add.at(acc, nbr_i, vals[nbr_j])
        vals = acc / (deg[:, None] + 1.0) if vals.ndim > 1 else acc / (deg + 1.0)
    return vals


def process(path, sdf):
    b, j, bin_off = _load(path)
    prim = j["meshes"][0]["primitives"][0]
    P, _ = _view(b, j, bin_off, prim["attributes"]["POSITION"])
    I, _ = _view(b, j, bin_off, prim["indices"])
    _, n_off = _view(b, j, bin_off, prim["attributes"]["NORMAL"])
    _, c_off = _view(b, j, bin_off, prim["attributes"]["COLOR_0"])
    ca = j["accessors"][prim["attributes"]["COLOR_0"]]
    assert ca["componentType"] == 5123 and ca["type"] == "VEC4", "COLOR_0 must be normalized uint16 RGBA"
    P = P.astype(np.float64)
    tri = I.reshape(-1, 3).astype(np.int64)

    # weld the UV-seam copies so the smoothing crosses seams
    key, weld = np.unique(np.round(P, 4), axis=0, return_inverse=True)
    weld = weld.ravel()
    nw = len(key)
    T = weld[tri]
    fn = np.cross(key[T[:, 1]] - key[T[:, 0]], key[T[:, 2]] - key[T[:, 0]])   # length = 2 x area
    vn = np.zeros((nw, 3))
    for k in range(3):
        np.add.at(vn, T[:, k], fn)
    # one-ring adjacency (directed edges, duplicates from shared edges are harmless weights)
    ei = np.concatenate([T[:, 0], T[:, 1], T[:, 2], T[:, 1], T[:, 2], T[:, 0]])
    ej = np.concatenate([T[:, 1], T[:, 2], T[:, 0], T[:, 0], T[:, 1], T[:, 2]])
    deg = np.bincount(ei, minlength=nw).astype(np.float64)
    vn /= np.linalg.norm(vn, axis=1, keepdims=True) + 1e-12
    raw_n = vn.copy()
    vn = _smooth(vn, ei, ej, deg, NORMAL_SMOOTH_ITERS)
    vn /= np.linalg.norm(vn, axis=1, keepdims=True) + 1e-12

    # mean curvature from the umbrella Laplacian along the smoothed normal (> 0: neighbours stand above, a pocket)
    nb = np.zeros((nw, 3))
    np.add.at(nb, ei, key[ej])
    lap = nb / deg[:, None] - key
    elen = np.linalg.norm(key[ej] - key[ei], axis=1)
    e2 = np.bincount(ei, weights=elen * elen, minlength=nw) / deg
    h = (lap * vn).sum(1) / np.maximum(e2, 1e-6)
    h = _smooth(h, ei, ej, deg, CURV_SMOOTH_ITERS)
    h -= np.median(h)   # the boulders are convex overall: measure pockets and edges against the typical surface
    s = np.percentile(np.abs(h), 90) + 1e-9
    cavity = np.clip(h / s, 0, 1) ** 0.8
    ridge = np.clip(-h / s, 0, 1) ** 0.8

    # open sky: SDF ambient occlusion along the (raw) surface normal, plus the sand bank round the rock feet
    grid, origin, cell = sdf
    occ = np.zeros(nw)
    probe_n = raw_n * 0.5 + vn * 0.5
    probe_n /= np.linalg.norm(probe_n, axis=1, keepdims=True) + 1e-12
    for d, w in AO_STEPS:
        sd = sdf_sample(grid, origin, cell, (key + probe_n * d).astype(np.float32))
        occ += w * np.clip((d - sd) / d, 0, 1)
    sand = SAND_Y + (SAND_Y_BACK - SAND_Y) * (1 - np.clip(key[:, 2] / TANK_D, 0, 1))
    bank = np.clip((key[:, 1] - sand) / 2.5, 0, 1)
    sky = np.clip(1 - occ * 1.25, 0, 1) * (0.55 + 0.45 * bank)
    sky = _smooth(sky, ei, ej, deg, 1)

    # write back per original (seam-split) vertex
    out_n = vn[weld].astype(np.float32)
    big = fbm3((key * 0.05 + np.array([3.1, 7.7, 1.3])).astype(np.float32))
    col = np.stack([cavity[weld], ridge[weld], sky[weld], big[weld]], 1)
    out_c = np.round(np.clip(col, 0, 1) * 65535).astype("<u2")
    b[n_off:n_off + out_n.nbytes] = out_n.astype("<f4").tobytes()
    b[c_off:c_off + out_c.nbytes] = out_c.tobytes()
    open(path, "wb").write(bytes(b))
    print(f"[rock_surface] {os.path.basename(path)}: {len(P)} verts ({nw} welded), "
          f"cavity mean {cavity.mean():.2f}, ridge mean {ridge.mean():.2f}, sky mean {sky.mean():.2f}")


def run(paths=None):
    sdf = load_sdf(os.path.join(ASSETS, "sim", "rock_sdf.bin"))
    for p in paths or [os.path.join(ASSETS, "models", "rockscape.glb")]:
        process(p, sdf)


if __name__ == "__main__":
    run(sys.argv[1:] or None)
