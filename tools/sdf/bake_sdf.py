"""Rock SDF bake (ASSETS.md §3.5, SIMULATION.md §5.2).

Loads models/rockscape.glb with bpy, builds a BVH, and writes assets/sim/rock_sdf.bin:

    'RSDF' | uint32 nx, ny, nz | float32 originX, originY, originZ | float32 cell      (32-byte header, LE)
    nx*ny*nz float16 signed distance in cm (positive outside rock)
    nx*ny*nz uint8 cover score (0 open .. 255 fully enclosed)

Index order: x fastest, then y, then z  ->  i = x + nx * (y + ny * z).
Grid: 1 cm cells over the tank interior (121.9 x 61 x 33 cm), sample points at cell centres,
origin = (0.5, 0.5, 0.5), dims 122 x 61 x 33.

Inside test: sign of dot(p - nearest, face normal) (the rock mesh is closed except the flat
cut at y = 0.4, which is below the sand). Cover: fraction of 26 directions (unit-cube
neighbourhood) that hit rock within 20 cm, computed on a 2 cm grid and trilinearly upsampled.

Run: /tmp/claude-0/bpyenv/bin/python tools/sdf/bake_sdf.py
"""
import os
import struct
import sys
import time

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "blender"))

import common as C  # noqa: E402
from common import bpy, w2b  # noqa: E402

NX, NY, NZ = 122, 61, 33
CELL = 1.0
ORIGIN = (0.5, 0.5, 0.5)
COVER_CELL = 2.0
COVER_RANGE = 20.0


def load_bvh(path):
    from mathutils.bvhtree import BVHTree
    C.reset_scene()
    bpy.ops.import_scene.gltf(filepath=path)
    objs = [o for o in bpy.context.scene.objects if o.type == 'MESH']
    dg = bpy.context.evaluated_depsgraph_get()
    verts, polys = [], []
    for o in objs:
        oe = o.evaluated_get(dg)
        me = oe.to_mesh()
        mw = o.matrix_world
        base = len(verts)
        verts += [tuple(mw @ v.co) for v in me.vertices]
        polys += [tuple(base + i for i in p.vertices) for p in me.polygons]
        oe.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys), len(polys)


def sample_dist(bvh, pts):
    out = np.empty(len(pts), np.float32)
    for i, p in enumerate(pts):
        pb = w2b(p)
        loc, nrm, idx, d = bvh.find_nearest(pb)
        if loc is None:
            out[i] = 60.0
            continue
        s = (pb[0] - loc[0]) * nrm[0] + (pb[1] - loc[1]) * nrm[1] + (pb[2] - loc[2]) * nrm[2]
        out[i] = -d if s < 0 else d
    return out


def cover_dirs():
    d = []
    for x in (-1, 0, 1):
        for y in (-1, 0, 1):
            for z in (-1, 0, 1):
                if x or y or z:
                    v = np.array([x, y, z], float)
                    d.append(v / np.linalg.norm(v))
    return d


def sample_cover(bvh, pts, inside):
    from mathutils import Vector
    dirs = [Vector(w2b(tuple(d))) for d in cover_dirs()]
    out = np.empty(len(pts), np.float32)
    for i, p in enumerate(pts):
        if inside[i]:
            out[i] = 1.0
            continue
        o = Vector(w2b(p))
        hits = 0
        for d in dirs:
            loc, nrm, idx, dist = bvh.ray_cast(o, d, COVER_RANGE)
            if loc is not None:
                hits += 1
        out[i] = hits / len(dirs)
    return out


def trilinear_up(coarse, cshape, fine_pts):
    """coarse values on a grid with cell COVER_CELL and centres at (k+0.5)*COVER_CELL."""
    cnx, cny, cnz = cshape
    g = coarse.reshape(cnz, cny, cnx)
    f = fine_pts / COVER_CELL - 0.5
    i0 = np.floor(f).astype(int)
    t = f - i0
    out = np.zeros(len(fine_pts))
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                ix = np.clip(i0[:, 0] + dx, 0, cnx - 1)
                iy = np.clip(i0[:, 1] + dy, 0, cny - 1)
                iz = np.clip(i0[:, 2] + dz, 0, cnz - 1)
                w = (t[:, 0] if dx else 1 - t[:, 0]) * (t[:, 1] if dy else 1 - t[:, 1]) * (t[:, 2] if dz else 1 - t[:, 2])
                out += w * g[iz, iy, ix]
    return out


def grid_points(nx, ny, nz, origin, cell):
    z, y, x = np.meshgrid(np.arange(nz), np.arange(ny), np.arange(nx), indexing="ij")
    return np.stack([origin[0] + x.ravel() * cell, origin[1] + y.ravel() * cell, origin[2] + z.ravel() * cell], -1)


def bake(glb=None, out=None):
    t0 = time.time()
    glb = glb or os.path.join(C.MODELS, "rockscape.glb")
    out = out or os.path.join(C.SIM, "rock_sdf.bin")
    bvh, nf = load_bvh(glb)
    pts = grid_points(NX, NY, NZ, ORIGIN, CELL)
    d = sample_dist(bvh, [tuple(p) for p in pts])
    d = np.clip(d, -60, 60)
    t1 = time.time()
    cshape = (int(np.ceil(122 / COVER_CELL)), int(np.ceil(61 / COVER_CELL)), int(np.ceil(33 / COVER_CELL)))
    cpts = grid_points(*cshape, (COVER_CELL / 2,) * 3, COVER_CELL)
    cd = sample_dist(bvh, [tuple(p) for p in cpts])
    cov = sample_cover(bvh, [tuple(p) for p in cpts], cd < 0)
    cover = trilinear_up(cov, cshape, pts)
    cover[d < 0] = 1.0
    t2 = time.time()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "wb") as f:
        f.write(b"RSDF")
        f.write(struct.pack("<3I4f", NX, NY, NZ, *ORIGIN, CELL))
        f.write(d.astype("<f2").tobytes())
        f.write(np.clip(np.round(cover * 255), 0, 255).astype(np.uint8).tobytes())
    inside = float((d < 0).mean())
    print(f"[sdf] {os.path.basename(glb)}: {nf} faces; dist {t1 - t0:.1f}s, cover {t2 - t1:.1f}s; "
          f"{inside * 100:.1f}% cells inside rock; d range {d.min():.1f}..{d.max():.1f}; "
          f"wrote {out} ({os.path.getsize(out)} bytes)")
    return out


if __name__ == "__main__":
    bake()
