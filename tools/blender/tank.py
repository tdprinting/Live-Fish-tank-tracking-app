"""Tank shell (docs/specs/tank_design.md §1): tank.glb.

Nodes: GlassFront/Back/Left/Right/Bottom (solid 6.35 mm panes), Seams (silicone fillets), GlassFilm (green algae
film at the front-bottom), Backing (black background behind the back pane), RimTop + RimBrace + RimCaps (black
top frame: closed swept L-profile with inner lip, centre brace, corner caps), RimBottom (bottom trim), Sand
(closed solid), WaterVolume (closed box, extras.hidden: preview only) + WaterSurface (two-sided grid), StandTop
(walnut slab) and the room props StandBody, StandMesh, StandBins, Floor, Wall, SideWall, SwitchPlate (all
extras.hidden: preview only; the runtime does not draw them).

World coordinates (cm, Y up, z = 0 back glass inner face, z = TANK[2] = 45.7 front glass inner face; common.TANK); geometry is authored
in world space and converted to Blender with W2B (a proper rotation, so face winding is preserved). Every mesh
is closed; build() prints the non-manifold edge count per object (must be 0).
"""
import math
import os
import struct

import numpy as np

import common as C
import noise3d as N
import paintlib as P
from common import bpy

TANK = C.TANK             # (121.9, 40.6, 45.7): 48 x 16 x 18 in interior (core TankDims.kt)
SAND_GLASS_INSET = 0.05  # cm between the sand bed's skirt and the inner glass faces
GT = 0.635                # glass thickness (6.35 mm)
SAND_Y = C.SAND_Y         # front glass; C.SAND_Y_BACK at the back glass (rockscape.sand_base)
WATER_Y = C.WATER_Y       # 2.5 cm under the top of the glass (open top)
W2B = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float)
S = P.smoothstep


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


def obox(center, size, xdir, ydir):
    """Closed box centred at `center` with local axes x=xdir, y=ydir."""
    x = np.asarray(xdir, float) / np.linalg.norm(xdir)
    y = np.asarray(ydir, float) - np.dot(ydir, x) * x
    y /= np.linalg.norm(y)
    z = np.cross(x, y)
    V, F = box(-np.asarray(size) / 2, np.asarray(size) / 2)
    return np.asarray(center, float) + V[:, :1] * x + V[:, 1:2] * y + V[:, 2:3] * z, F


def prism(prof, mapf, w0, w1):
    """Closed prism: 2D profile loop `prof` [(s, t)] extruded along w0..w1; mapf(s, t, w) -> world point."""
    n = len(prof)
    V = [mapf(s, t, w0) for s, t in prof] + [mapf(s, t, w1) for s, t in prof]
    F = [(i, (i + 1) % n, n + (i + 1) % n, n + i) for i in range(n)]
    F += [tuple(range(n))[::-1], tuple(range(n, 2 * n))]
    return np.array(V, float), F


def sweep_rect(prof, x0, x1, z0, z1):
    """Closed ring: profile loop [(o, y)] (o = outward offset from the rectangle edge) swept around the
    rectangle x0..x1 / z0..z1 with mitred corners."""
    n = len(prof)
    V = []
    for o, y in prof:
        V += [(x0 - o, y, z0 - o), (x1 + o, y, z0 - o), (x1 + o, y, z1 + o), (x0 - o, y, z1 + o)]
    F = []
    for i in range(n):
        a, b = 4 * i, 4 * ((i + 1) % n)
        for j in range(4):
            k = (j + 1) % 4
            F.append((a + j, a + k, b + k, b + j))
    return np.array(V, float), F


def rounded(pts, r, at, seg=3):
    """Replace vertex index `at` of the polyline loop with an arc of radius r (fillet)."""
    p = np.asarray(pts[at], float)
    a = np.asarray(pts[at - 1], float)
    b = np.asarray(pts[(at + 1) % len(pts)], float)
    da, db = (a - p) / np.linalg.norm(a - p), (b - p) / np.linalg.norm(b - p)
    c = p + (da + db) * r  # 90-degree corners only
    arc = []
    for k in range(seg + 1):
        t = k / seg
        q = p + da * r * (1 - t) + db * r * t
        d = q - c
        arc.append(tuple(c + d / np.linalg.norm(d) * r))
    return list(pts[:at]) + arc + list(pts[at + 1:])


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


def boxes(*specs, mat=0, uv_scale=None):
    g = WGeo()
    for lo, hi in specs:
        V, F = box(lo, hi)
        g.add(V, F, box_uv(V, lo, hi) if uv_scale is None else np.c_[V[:, 0] / uv_scale, V[:, 1] / uv_scale], mat=mat)
    return g


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


def non_manifold(obj):
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    n = sum(1 for e in bm.edges if not e.is_manifold)
    bm.free()
    return n


# ----------------------------------------------------------------------------
def sand_geo():
    """Closed sand solid (spec §1.6): heightfield top, skirt walls at the glass, flat bottom at y = -0.5.
    1.5 cm grid (finer rows toward the front glass for the film), ~3.7k tris."""
    import rockscape as R
    L, _, D = TANK
    xs = np.r_[np.arange(0, L - 0.1, 1.5), L]
    zs = np.r_[np.arange(0, D - 3.5, 2.6), D - 3.0, D - 2.0, D - 1.2, D - 0.6, D]
    X, Z = np.meshgrid(xs, zs)
    sdf = read_sdf()
    if sdf is not None:
        d = np.minimum.reduce([sdf(X, y, Z) for y in (4.0, 5.5, 7.0)])
    else:
        d = np.full(X.shape, 99.0)
    H = R.sand_height(X, Z, d)
    pts = np.stack([X.ravel(), np.zeros(X.size), Z.ravel()], -1)
    H += 0.7 * (N.fbm3(pts, 4.0, 3, 17).reshape(X.shape) - 0.5)  # fBm 0.35 cm at 4 cm
    nz_, nx_ = X.shape
    # keep the bed 0.5 mm inside the panes: a skirt coplanar with the glass z-fights with the inner pane face
    E = SAND_GLASS_INSET
    top = np.stack([np.clip(X.ravel(), E, L - E), H.ravel(), np.clip(Z.ravel(), E, D - E)], -1)
    F = []
    for j in range(nz_ - 1):
        for i in range(nx_ - 1):
            a = j * nx_ + i
            F.append((a, a + nx_, a + nx_ + 1, a + 1))  # upward normal (+x right, +z front)
    # boundary loop of the top grid (counter-clockwise seen from above) -> skirt + bottom fan
    ring = [j * nx_ for j in range(nz_)] + [(nz_ - 1) * nx_ + i for i in range(1, nx_)] + \
        [j * nx_ + nx_ - 1 for j in range(nz_ - 2, -1, -1)] + [i for i in range(nx_ - 2, 0, -1)]
    nt = len(top)
    bot = top[ring].copy()
    bot[:, 1] = -0.5
    nb = len(bot)
    # skirt (the cross-section seen through the glass) on its own vertices: wall-projected uv (along the pane, down
    # the wall) instead of the bed's planar xz uv, which is constant down the wall and smeared it into vertical
    # streaks, and its own normals instead of ones averaged with the bed's top ring
    sk_top = top[ring].copy()
    sk_bot = bot.copy()
    V = np.concatenate([top, bot, [[L / 2, -0.5, D / 2]], sk_top, sk_bot])
    s0, s1 = nt + nb + 1, nt + 2 * nb + 1
    for k in range(nb):
        k1 = (k + 1) % nb
        F.append((s0 + k, s1 + k, s1 + k1, s0 + k1))
        F.append((nt + nb, nt + k1, nt + k))
    UV = np.c_[V[:, 0] / 30.475, 1 - V[:, 2] / 30.475]
    sk = V[s0:]
    on_side = (sk[:, 0] <= E + 1e-4) | (sk[:, 0] >= L - E - 1e-4)
    along = np.where(on_side, sk[:, 2], sk[:, 0])
    UV[s0:] = np.c_[along / 30.475, 1 - sk[:, 1] / 30.475]
    # COLOR_0.r = front algae film: first 2 cm of sand along the front glass, strongest x 30-70 and x 0-15
    xv, zv = V[:, 0], V[:, 2]
    strong = np.maximum(S(26, 32, xv) * (1 - S(68, 74, xv)), 1 - S(13, 18, xv))
    film = S(D - 2.0, D - 0.2, zv) * (0.45 + 0.55 * strong) * (0.8 + 0.2 * np.sin(xv * 0.37))
    COL = np.zeros((len(V), 4))
    COL[:, 0] = np.clip(film, 0, 1)
    COL[:, 3] = 1
    g = WGeo()
    g.add(V, F, UV, COL)
    return g


def pebble(center, radii, segs=7, rings=4, rot=None):
    """Closed UV-sphere pebble (single pole vertices, so bmesh sees it as manifold)."""
    V = [(0.0, 1.0, 0.0)]
    for i in range(1, rings):
        th = math.pi * i / rings
        for k in range(segs):
            ph = 2 * math.pi * k / segs
            V.append((math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph)))
    V.append((0.0, -1.0, 0.0))
    V = np.array(V) * np.asarray(radii, float)
    if rot is not None:
        V = V @ np.asarray(rot).T
    V = V + np.asarray(center, float)
    F = [(0, 1 + (k + 1) % segs, 1 + k) for k in range(segs)]
    for i in range(rings - 2):
        a, b = 1 + i * segs, 1 + (i + 1) * segs
        F += [(a + k, a + (k + 1) % segs, b + (k + 1) % segs, b + k) for k in range(segs)]
    last = 1 + (rings - 2) * segs
    F += [(last + k, last + (k + 1) % segs, len(V) - 1) for k in range(segs)]
    return V, F


def rubble_geo(seed=23, count=44):
    """Coral rubble pebbles 1-3 cm on the sand, banked within ~9 cm of the rock feet and in the lanes (the photos
    show bits of rubble all along the rock bases). Half-buried; sand material (they are bleached aragonite)."""
    import rockscape as R
    rs = np.random.default_rng(seed)
    sdf = read_sdf()
    D = TANK[2]
    g = WGeo()
    placed = 0
    tries = 0
    while placed < count and tries < 4000:
        tries += 1
        x, z = rs.uniform(4, 118), rs.uniform(3, D - 2)
        d = float(np.minimum.reduce([sdf(x, y, z) for y in (4.0, 5.5, 7.0)])) if sdf is not None else 99.0
        if d < 1.2 or (d > 9.0 and rs.uniform() > 0.12):
            continue
        bm = R.BURROW_MOUTH
        if (abs(x - bm[0]) < 7 and z > bm[1] - 2) or (abs(x - 68) < 6 and z > D - 9):  # burrow mouth / magnet / zoa
            continue
        h = float(R.sand_height(x, z, d))
        r = rs.uniform(0.45, 1.4)
        radii = (r * rs.uniform(0.8, 1.4), r * rs.uniform(0.5, 0.8), r * rs.uniform(0.8, 1.2))
        a = rs.uniform(0, math.pi)
        rot = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        V, F = pebble((x, h - radii[1] * 0.35, z), radii, rot=rot)
        g.add(V, F, np.c_[V[:, 0] / 30.475 * 3, 1 - V[:, 2] / 30.475 * 3])
        placed += 1
    return g


def seams_geo():
    """Silicone quarter-round fillets (r 2.5 mm, 8 segments) at the 4 vertical inner corners and 4 bottom edges,
    overlapping the glass by 0.3 mm."""
    L, Hh, D = TANK
    r, e = 0.25, 0.03
    prof = [(-e, -e), (r, -e)] + [(r + r * math.cos(th), r + r * math.sin(th))
                                  for th in np.radians(np.linspace(270, 180, 9))[1:-1]] + [(0, r), (-e, r)]
    prof = [prof[0], prof[1], (r, 0.0)] + prof[2:]
    g = WGeo()
    for cx, ux in ((0.0, 1), (L, -1)):
        for cz, uz in ((0.0, 1), (D, -1)):
            V, F = prism(prof, lambda s, t, w, cx=cx, cz=cz, ux=ux, uz=uz: (cx + ux * s, w, cz + uz * t), 0.0, Hh - 0.6)
            g.add(V, F)
    for cz, uz in ((0.0, 1), (D, -1)):
        V, F = prism(prof, lambda s, t, w, cz=cz, uz=uz: (w, s, cz + uz * t), 0.0, L)
        g.add(V, F)
    for cx, ux in ((0.0, 1), (L, -1)):
        V, F = prism(prof, lambda s, t, w, cx=cx, ux=ux: (cx + ux * t, s, w), 0.0, D)
        g.add(V, F)
    return g


def rim_geos():
    """Top frame (closed swept L-profile with the 28 mm inner lip and the underside glass channel), centre brace,
    corner caps, and the bottom trim (spec §1.4, §1.5). Profiles in cm: (outward offset from the glass outer
    edge, y)."""
    L, Hh, D = TANK
    x0, x1, z0, z1 = -GT, L + GT, -GT, D + GT
    a, b = 0.4, 0.1  # skirt outer / inner face (3 mm skirt, 1 mm outside the glass)
    R = Hh + 0.4     # lip top (C.RIM_Y); the profile below is relative to it
    top = [(a, R - 3.8), (a, R), (-2.4, R), (-2.4, R - 0.5), (b, R - 0.5), (b, R - 3.5), (-0.6, R - 3.5), (-0.6, R - 3.8)]
    top = rounded(top, 0.1, 2, 1)   # inner lip edge r 1 mm
    top = rounded(top, 0.3, 1, 3)   # outer top edge r 3 mm
    gt = WGeo()
    V, F = sweep_rect(top[::-1], x0, x1, z0, z1)
    gt.add(V, F)
    gb = WGeo()
    V, F = box((L / 2 - 2.05, R - 1.0, -0.3), (L / 2 + 3.95, R, D + 0.3))  # centre brace, 60 mm
    gb.add(V, F)
    for cx in (x0 - a, x1 + a - 1.2):  # corner caps 12 x 12 mm, 1 mm proud
        for cz in (z0 - a, z1 + a - 1.2):
            V, F = box((cx, R, cz), (cx + 1.2, R + 0.1, cz + 1.2))
            gb.add(V, F)
    bot = [(a, -3.6), (a, 0.6), (b, 0.6), (b, -3.3), (-0.8, -3.3), (-0.8, -3.6)]
    bot = rounded(bot, 0.2, 1, 2)
    gbot = WGeo()
    V, F = sweep_rect(bot[::-1], x0, x1, z0, z1)
    gbot.add(V, F)
    return gt, gb, gbot


def mesh_tex(path, size=64):
    """Wire-mesh card texture: 4 x 4 cells of 1 cm per tile, 1.5 px wires, alpha-tested."""
    img = np.zeros((size, size, 4))
    yy, xx = np.mgrid[0:size, 0:size]
    w = (np.minimum((xx % 16), 16 - (xx % 16)) < 1.2) | (np.minimum((yy % 16), 16 - (yy % 16)) < 1.2)
    img[..., :3] = 0.8
    img[..., 3] = w.astype(float)
    C.write_png(path, (img * 255).astype(np.uint8))
    return path


def stand_geos():
    """Stand body (white, X-lattice door, two open bays with shelves), wire-mesh card, bins (spec §1.8)."""
    L, D = TANK[0], TANK[2]
    body = WGeo()
    white, inside = 0, 1
    for lo, hi, m in (
            ((-2.01, -79.01, -2.01), (0, -7.6, D + 2.01), white), ((122, -79, -2), (124, -7.6, D + 2), white),  # sides
            ((-2, -79, -2), (124, -7.6, -0.5), inside),                                         # back
            ((0, -79, -0.5), (122, -76, D + 2), inside), ((0, -9.6, -0.5), (122, -7.6, D + 2), white),  # floor / top
            ((30, -79, -0.5), (32, -7.6, D + 2), white), ((78, -79, -0.5), (80, -7.6, D + 2), white),   # dividers
            ((32, -46, -0.5), (78, -44, D + 1), inside), ((80, -46, -0.5), (122, -44, D + 1), inside),  # shelves
            ((-2, -12.6, D), (124, -7.6, D + 2), white), ((-2, -79, D), (124, -74, D + 2), white),    # face rails
            ((-2, -79, D), (1, -7.6, D + 2), white), ((27, -79, D), (32, -7.6, D + 2), white),        # door stiles
            ((78, -79, D), (80, -7.6, D + 2), white), ((121, -79, D), (124, -7.6, D + 2), white)):
        e = 0.002 + 0.003 * (len(body.V) // 8)  # unique tiny inset per piece: touching pieces share no edge
        V, F = box(np.add(lo, e), np.subtract(hi, e))
        body.add(V, F, mat=m)
    # door X-lattice: 3 cm bars corner-to-corner over the inner opening x 1-27, y -74..-12.6
    (ax, ay), (bx, by) = (1, -74), (27, -12.6)
    for p, q in (((ax, ay), (bx, by)), ((ax, by), (bx, ay))):
        c = ((p[0] + q[0]) / 2, (p[1] + q[1]) / 2, D + 1.2)
        d = (q[0] - p[0], q[1] - p[1], 0)
        ln = math.hypot(d[0], d[1])
        V, F = obox(c, (ln, 3.0, 1.2), d, (0, 0, 1) if False else (-d[1], d[0], 0))
        body.add(V, F, mat=white)
    for fx in (-2, 120):  # feet 4 x 4 x 2
        for fz in (-2, D - 2):
            V, F = box((fx, -81, fz), (fx + 4, -79, fz + 4))
            body.add(V, F, mat=white)
    mesh = WGeo()
    lo, hi = (1, -74, D + 0.4), (27, -12.6, D + 0.6)
    V, F = box(lo, hi)
    mesh.add(V, F, np.c_[V[:, 0] / 4.0, V[:, 1] / 4.0])
    bins = boxes(((40, -76, 4), (75, -56, D - 4)), ((84, -44, 4), (119, -24, D - 4)))
    return body, mesh, bins


def room_geos():
    floor = boxes(((-120, -82, -8), (240, -81, 220)))
    wall = boxes(((-120, -81, -8), (240, 160, -6)), mat=0)
    side = boxes(((-16, -81, -8), (-14, 160, TANK[2] + 27)))
    sw = boxes(((12, 80, -6), (24, 100, -5.4)))
    for tx in (15.5, 20.5):
        V, F = box((tx - 0.5, 88.75, -5.4), (tx + 0.5, 91.25, -4.8))
        sw.add(V, F)
    return floor, wall, side, sw


def build():
    C.reset_scene()
    L, Hh, D = TANK
    root = C.empty("Root")
    C.set_extras(root, units="cm", interior=[L, Hh, D], sandY=SAND_Y, sandYBack=C.SAND_Y_BACK, waterY=WATER_Y,
                 glassThickness=GT, spec="docs/specs/tank_design.md")
    glass = C.material("glass", "#DDEFE4", rough=0.02, alpha=0.12, blend=True, double=True)
    backing = C.material("backing", "#070808", rough=0.9)
    silicone = C.material("silicone", "#C9D0CC", rough=0.3, alpha=0.6, blend=True)
    film = C.material("algae_film", "#6F8A3C", rough=0.92, alpha=0.55, blend=True, double=True)
    rim = C.material("plastic_black", "#141518", rough=0.45)
    water = C.material("water", "#9FD3E6", rough=0.05, alpha=0.3, blend=True, double=True)
    wvol = C.material("water_volume", "#2E8A78", rough=0.0, alpha=0.15, blend=True)
    walnut = C.material("walnut", "#3B2518", rough=0.35)
    paint = C.material("stand_white", "#F1EDE6", rough=0.6)
    inside = C.material("stand_inside", "#E8E4DD", rough=0.7)
    mpng = mesh_tex(os.path.join(C.BUILD, "stand_mesh.png"))
    meshm = C.material("wire_mesh", "#CFCFCF", rough=0.5, image=C.load_image(mpng, name="stand_mesh"),
                       image_alpha=True, clip=True, double=True)
    binm = C.material("bin_clear", "#E9ECEE", rough=0.2, alpha=0.5, blend=True)
    floorm = C.material("floor_wood", "#8A6A4D", rough=0.6)
    wallm = C.material("wall", "#E8DFD0", rough=0.9)
    sidem = C.material("side_wall", "#EADFCF", rough=0.9)
    switchm = C.material("switch", "#F4F2EE", rough=0.4)
    sand_png = os.path.join(C.BUILD, "sand_albedo.png")
    import textures
    textures.build_all(verbose=False)
    sand = C.material("sand", "#EDE7D9", rough=0.92, image=C.load_image(sand_png, name="sand_albedo"))

    objs = []
    for name, lo, hi, part in (
            ("GlassFront", (-GT, -GT, D), (L + GT, Hh, D + GT), "glass"),
            ("GlassBack", (-GT, -GT, -GT), (L + GT, Hh, 0), "backglass"),
            ("GlassLeft", (-GT, -GT, 0), (0, Hh, D), "glass"),
            ("GlassRight", (L, -GT, 0), (L + GT, Hh, D), "glass"),
            ("GlassBottom", (0, -GT, 0), (L, 0, D), "glass")):
        objs.append(boxes((lo, hi)).obj(name, root, part, [glass]))
    objs.append(seams_geo().obj("Seams", root, "glass", [silicone], smooth=True))
    fy = SAND_Y - 0.2
    objs.append(boxes(((0, fy, D - 0.05), (L, fy + 1.4, D))).obj("GlassFilm", root, "glass", [film]))
    objs.append(boxes(((-1, -1, -1.0), (L + 1.1, Hh + 1.0, -0.7))).obj("Backing", root, "backglass", [backing]))
    gt, gb, gbot = rim_geos()
    objs.append(gt.obj("RimTop", root, "rim", [rim]))
    objs.append(gb.obj("RimBrace", root, "rim", [rim]))
    objs.append(gbot.obj("RimBottom", root, "rim", [rim]))
    objs.append(sand_geo().obj("Sand", root, "sand", [sand], smooth=True))
    objs.append(rubble_geo().obj("Rubble", root, "sand", [sand], smooth=True))
    objs.append(boxes(((0, 0, 0), (L, WATER_Y, D))).obj(
        "WaterVolume", root, "water", [wvol],
        extras={"hidden": True, "volume": {"absorption": "#2E8A78", "densityPerCm": 0.045, "ior": 1.333}}))
    nx = 52                          # ~2.3 cm cells, same quad count as the old 61 x 17 grid
    nz = max(4, round(nx * D / L))
    xs, zs = np.linspace(0, L, nx + 1), np.linspace(0, D, nz + 1)
    X, Z = np.meshgrid(xs, zs)
    V = np.stack([X.ravel(), np.full(X.size, WATER_Y), Z.ravel()], -1)
    F = [(j * (nx + 1) + i, (j + 1) * (nx + 1) + i, (j + 1) * (nx + 1) + i + 1, j * (nx + 1) + i + 1)
         for j in range(nz) for i in range(nx)]
    g = WGeo()
    g.add(V, F, np.c_[X.ravel() / L, 1 - Z.ravel() / D])
    objs.append(g.obj("WaterSurface", root, "water", [water], smooth=True, extras={"twoSided": True}))
    objs.append(boxes(((-4, -7.6, -4), (L + 4, -3.6, D + 4))).obj("StandTop", root, "stand", [walnut]))
    body, mesh, bins = stand_geos()
    hid = {"hidden": True, "room": True}
    objs.append(body.obj("StandBody", root, "stand", [paint, inside], extras=hid))
    objs.append(mesh.obj("StandMesh", root, "stand", [meshm], extras=hid))
    objs.append(bins.obj("StandBins", root, "stand", [binm], extras=hid))
    floor, wall, side, sw = room_geos()
    objs.append(floor.obj("Floor", root, "stand", [floorm], extras=hid))
    objs.append(wall.obj("Wall", root, "stand", [wallm], extras=hid))
    objs.append(side.obj("SideWall", root, "stand", [sidem], extras=hid))
    objs.append(sw.obj("SwitchPlate", root, "stand", [switchm], extras=hid))
    bad = {}
    for o in objs:
        C.recalc_normals(o)
        if o.name != "WaterSurface":
            nm = non_manifold(o)
            if nm:
                bad[o.name] = nm
        C.triangulate(o)
    print("[tank] non-manifold edges:", bad or 0)
    path = os.path.join(C.MODELS, "tank.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    vis = sum(C.tri_count(o) for o in objs if not o.get("hidden"))
    print(f"[tank] tank.glb: {tris} tris ({vis} drawn by the runtime), {os.path.getsize(path) / 1024:.0f} KB")
    return tris


if __name__ == "__main__":
    build()
