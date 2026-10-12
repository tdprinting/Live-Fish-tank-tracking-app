"""Tank shell (ASSETS.md §3.3, §5): tank.glb = GlassFront/Left/Right, GlassBack, Sand, WaterSurface, Rim, Stand.

World coordinates (cm, Y up, z = 0 back glass, z = 33 front glass). Geometry is authored in world space
and converted to Blender with W2B (a proper rotation, so face winding is preserved).

Sand: 2 cm heightfield (finer rows near the front glass for the film mask) with mounds against the rock
feet read from assets/sim/rock_sdf.bin; UV tiles every 30.5 cm (4 x along the length); COLOR_0.r = the
green film mask along the front glass. The sand albedo is embedded in tank.glb; textures/sand_normal.png is
a separate tiling detail map.
"""
import os
import struct

import numpy as np

import common as C
import paintlib as P
from common import bpy

TANK = (121.9, 61.0, 33.0)
SAND_Y = 3.0
WATER_Y = 56.0
W2B = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float)


def to_b(V):
    return np.asarray(V, float) @ W2B.T


def box(lo, hi):
    """Axis-aligned box in world coords with outward winding."""
    lo, hi = np.asarray(lo, float), np.asarray(hi, float)
    V = np.array([[(hi if i & 1 else lo)[0], (hi if i & 2 else lo)[1], (hi if i & 4 else lo)[2]] for i in range(8)])
    F = [(0, 4, 6, 2), (1, 3, 7, 5), (0, 1, 5, 4), (2, 6, 7, 3), (0, 2, 3, 1), (4, 5, 7, 6)]
    return V, F


def box_uv(V, lo, hi):
    """Planar UV (x along u, y along v) for small props."""
    lo, hi = np.asarray(lo, float), np.asarray(hi, float)
    return np.c_[(V[:, 0] - lo[0]) / max(hi[0] - lo[0], 1e-6), (V[:, 1] - lo[1]) / max(hi[1] - lo[1], 1e-6)]


def quad(p0, p1, p2, p3):
    return np.array([p0, p1, p2, p3], float), [(0, 1, 2, 3)]


class WGeo:
    """World-space mesh accumulator."""

    def __init__(self):
        self.V, self.F, self.UV, self.COL, self.FM = [], [], [], [], []

    def add(self, V, F, UV=None, col=(0, 0, 0, 1), mat=0):
        b = len(self.V)
        V = np.asarray(V, float)
        self.V += list(V)
        self.F += [tuple(int(i) + b for i in f) for f in F]
        self.UV += list(UV) if UV is not None else [(0.5, 0.5)] * len(V)
        c = np.asarray(col, float)
        self.COL += list(c) if c.ndim == 2 else [tuple(c)] * len(V)
        self.FM += [mat] * len(F)

    def obj(self, name, parent, part, mats, smooth=False, extras=None):
        return C.mesh_object(name, to_b(self.V), self.F, uvs=np.array(self.UV, float),
                             cols=np.array(self.COL, float), parent=parent, part=part, materials=mats,
                             face_mats=self.FM, smooth=smooth, extras=extras)

    def tris(self):
        return sum(len(f) - 2 for f in self.F)


def read_sdf(path=None):
    path = path or os.path.join(C.SIM, "rock_sdf.bin")
    if not os.path.exists(path):
        return None
    b = open(path, "rb").read()
    nx, ny, nz = struct.unpack_from("<3I", b, 4)
    ox, oy, oz, cell = struct.unpack_from("<4f", b, 16)
    n = nx * ny * nz
    d = np.frombuffer(b, "<f2", n, 32).astype(float).reshape(nz, ny, nx)

    def q(x, y, z):
        ix = np.clip(np.round((np.asarray(x) - ox) / cell).astype(int), 0, nx - 1)
        iy = np.clip(np.round((np.asarray(y) - oy) / cell).astype(int), 0, ny - 1)
        iz = np.clip(np.round((np.asarray(z) - oz) / cell).astype(int), 0, nz - 1)
        return d[iz, iy, ix]
    return q


def sand_mesh(mat):
    xs = np.r_[np.arange(0, 121.9, 2.0), 121.9]
    zs = np.r_[np.arange(0, 31, 2.0), 31.0, 31.8, 32.4, 33.0]
    X, Z = np.meshgrid(xs, zs)
    sdf = read_sdf()
    H = np.full(X.shape, SAND_Y)
    if sdf is not None:
        d = np.minimum(sdf(X, 4.0, Z), sdf(X, 6.0, Z))
        H += 1.5 * np.exp(-np.maximum(d, 0) / 4.0)
    # lanes (bare, slightly scooped) and noise
    lane = (P.smoothstep(25, 29, X) * (1 - P.smoothstep(42, 45, X)) + P.smoothstep(53, 56, X) * (1 - P.smoothstep(64, 66, X)))
    H -= 0.5 * lane * P.smoothstep(12, 18, Z)
    rs = np.random.default_rng(5)
    noise = rs.normal(0, 1, X.shape)
    noise = (noise + np.roll(noise, 1, 0) + np.roll(noise, 1, 1) + np.roll(noise, -1, 1)) / 4
    H += 0.3 * noise
    # flatten toward the glass walls (the sand meets the glass at the nominal depth)
    edge = np.minimum.reduce([X, 121.9 - X, Z + 6, 33 - Z]) / 3.0
    H = SAND_Y + (H - SAND_Y) * np.clip(edge, 0, 1)
    nzv, nxv = X.shape
    V = np.stack([X.ravel(), H.ravel(), Z.ravel()], -1)
    F = []
    for j in range(nzv - 1):
        for i in range(nxv - 1):
            a = j * nxv + i
            # world: +x right, +z front; upward normal needs (a, a+nx, a+nx+1, a+1)
            F.append((a, a + nxv, a + nxv + 1, a + 1))
    UV = np.c_[X.ravel() / 30.475, 1 - Z.ravel() / 30.475]
    film = P.smoothstep(31.0, 32.6, Z) * (0.65 + 0.35 * np.cos(X * 0.37) * np.sin(X * 0.11 + 1.0))
    COL = np.zeros((len(V), 4))
    COL[:, 0] = np.clip(film.ravel(), 0, 1)
    COL[:, 3] = 1
    g = WGeo()
    g.add(V, F, UV, COL)
    return g


def rim_geo():
    """Black plastic frame: top and bottom, 2.5 cm wide x 3 cm tall around the outside of the glass."""
    g = WGeo()
    L, Hh, D = TANK
    t = 0.6  # glass thickness
    w = 2.5
    for y0, y1 in ((Hh - 2.0, Hh + 1.0), (-2.5, 0.5)):
        # front / back bars span the full outer width; side bars fit between
        for z0, z1 in ((D + t - 0.3, D + t + w - 0.3), (-t - w + 0.3, -t + 0.3)):
            V, F = box((-t - w + 0.3, y0, z0), (L + t + w - 0.3, y1, z1))
            g.add(V, F)
        for x0, x1 in ((-t - w + 0.3, -t + 0.3), (L + t - 0.3, L + t + w - 0.3)):
            V, F = box((x0, y0, -t + 0.3), (x1, y1, D + t - 0.3))
            g.add(V, F)
    # inner top lip (the dark band seen through the front glass at the waterline)
    V, F = box((-0.2, Hh - 3.2, D - 0.2), (L + 0.2, Hh - 2.0, D + t))
    g.add(V, F)
    return g


def build():
    C.reset_scene()
    L, Hh, D = TANK
    root = C.empty("Root")
    C.set_extras(root, units="cm", interior=[L, Hh, D], sandY=SAND_Y, waterY=WATER_Y)
    glass = C.material("glass", "#DDEEEE", rough=0.05, alpha=0.12, blend=True, double=True)
    back = C.material("backglass", "#0B1A20", rough=0.2)
    rim = C.material("rim", "#15171A", rough=0.4)
    water = C.material("water", "#9FD3E6", rough=0.05, alpha=0.3, blend=True, double=True)
    stand = C.material("stand", "#2B211C", rough=0.7)
    sand_png = os.path.join(C.BUILD, "sand_albedo.png")
    if not os.path.exists(sand_png):
        import textures
        textures.build_all(verbose=False)
    sand = C.material("sand", "#E6DFD0", rough=0.9, image=C.load_image(sand_png, name="sand_albedo"))

    objs = []
    # glass panes: single quads at the interior faces, normals pointing out of the water
    for name, V, part, mat in (
            ("GlassFront", [(0, 0, D), (L, 0, D), (L, Hh, D), (0, Hh, D)], "glass", glass),
            ("GlassLeft", [(0, 0, 0), (0, 0, D), (0, Hh, D), (0, Hh, 0)], "glass", glass),
            ("GlassRight", [(L, 0, D), (L, 0, 0), (L, Hh, 0), (L, Hh, D)], "glass", glass),
            ("GlassBack", [(L, 0, 0), (0, 0, 0), (0, Hh, 0), (L, Hh, 0)], "backglass", back)):
        g = WGeo()
        Vq = np.array(V, float)
        uv = [(0, 0), (1, 0), (1, 1), (0, 1)]
        if name == "GlassBack":  # the back glass faces the viewer (into the tank)
            Vq = Vq[[1, 0, 3, 2]]
        g.add(Vq, [(0, 1, 2, 3)], uv)
        objs.append(g.obj(name, root, part, [mat]))
    sg = sand_mesh(sand)
    objs.append(sg.obj("Sand", root, "sand", [sand], smooth=True))
    # water surface: 24 x 6 grid (vertex waves optional at runtime), normal up
    xs = np.linspace(0, L, 25)
    zs = np.linspace(0, D, 7)
    X, Z = np.meshgrid(xs, zs)
    V = np.stack([X.ravel(), np.full(X.size, WATER_Y), Z.ravel()], -1)
    F = [(j * 25 + i, (j + 1) * 25 + i, (j + 1) * 25 + i + 1, j * 25 + i + 1) for j in range(6) for i in range(24)]
    g = WGeo()
    g.add(V, F, np.c_[X.ravel() / L, 1 - Z.ravel() / D])
    objs.append(g.obj("WaterSurface", root, "water", [water], smooth=True))
    objs.append(rim_geo().obj("Rim", root, "rim", [rim]))
    g = WGeo()
    V, F = box((-4, -78, -4), (L + 4, -2.5, D + 4))
    g.add(V, F)
    objs.append(g.obj("Stand", root, "stand", [stand], extras={"hidden": True}))
    for o in objs:
        C.triangulate(o)
    path = os.path.join(C.MODELS, "tank.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[tank] tank.glb: {tris} tris, {os.path.getsize(path) / 1024:.0f} KB")
    return tris


if __name__ == "__main__":
    build()
