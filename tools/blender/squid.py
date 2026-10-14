"""Hummingbird bobtail squid, Euprymna berryi (docs/research/cephalopod_squid.md, SIMULATION §20).

    /tmp/claude-0/bpyenv/bin/python tools/blender/squid.py      -> models/bobtail_squid.glb + _lod1.glb

Unit length facing +X like the fish, but the "snout" is the arm crown: arm tips at x = +0.5, mantle apex at
x = -0.5 (bobtails hover and hunt arms first and jet mantle first, backwards). Proportions from Jolly et al. 2022
(mantle ~3.5 cm of ~6 cm total, round mantle, short stout arms) and MBL photographs: a rounded sac-like mantle,
two round ear-shaped fins mid-mantle, a broad head with very large eyes, eight short arms and two longer, thinner
feeding tentacles with clubs.

COLOR_0 (fish.vert motion mode 4, fish.frag uChroma):
  body  : R = dorsal factor (0 ventral midline .. 1 dorsal midline; chromatophore density and the ventral light-organ
          glow), G = 0 (no gill flare), B = 0, A = spine u = 0.5 - x
  fin   : B = hinge (0 attachment .. 1 free edge), A = u of the fin base
  eye   : A = u of the eye centre, extras.pivot
  leg   : R = class / 8 (1 arm, 2 tentacle), G = index / 8 (arm 0..7 from the dorsal pair round to the ventral pair;
          tentacle 0/1), B = hinge (0 base .. 1 tip), A = u of the arm crown
The atlas carries a static chromatophore pattern (pale cream base, brown/orange/yellow chromatophores densest
dorsally, blue-green iridophore flecks); fish.frag expands and fades procedural chromatophores over it at runtime.
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import geomlib as G  # noqa: E402
import paintlib as P  # noqa: E402
from inverts import geo_obj, make_root, finish, smooth_path, gltf_pt  # noqa: E402

SID = "bobtail_squid"
LENGTH_CM = 6.0
BUDGET = 4600
BUDGET_LOD1 = 1700

# body profile (Blender frame: x forward to the arms, y side, z up): x, half-width, half-height, centre z
PROFILE = [
    (0.235, 0.040, 0.038, 0.000),   # arm crown
    (0.215, 0.062, 0.058, 0.004),
    (0.190, 0.085, 0.072, 0.008),
    (0.160, 0.105, 0.082, 0.010),   # head across the eyes
    (0.125, 0.104, 0.082, 0.008),
    (0.095, 0.090, 0.078, 0.006),   # neck
    (0.070, 0.112, 0.105, 0.004),   # mantle opening (collar)
    (0.040, 0.142, 0.132, 0.004),
    (0.000, 0.168, 0.152, 0.004),
    (-0.060, 0.190, 0.170, 0.004),
    (-0.130, 0.202, 0.180, 0.004),
    (-0.210, 0.200, 0.178, 0.002),
    (-0.290, 0.188, 0.168, 0.000),
    (-0.360, 0.164, 0.148, -0.002),
    (-0.420, 0.128, 0.116, -0.004),
    (-0.465, 0.082, 0.076, -0.006),
    (-0.492, 0.034, 0.032, -0.006),
]
ARM_CROWN_X = 0.228
# arm lengths (unit body lengths) by pair from the dorsal pair (I) to the ventral pair (IV); bobtail arms are short,
# II-III the longest
ARM_LEN = [0.215, 0.245, 0.250, 0.225]
TENTACLE_LEN = 0.275


def _mirror_h(a):
    """around coordinate (0..1, 0 = dorsal) -> flank height (1 dorsal .. 0 ventral), mirrored left/right."""
    return 1 - np.abs(((np.asarray(a) + 0.5) % 1.0) - 0.5) * 2


# ----------------------------------------------------------------------------
# texture
# ----------------------------------------------------------------------------
CHROMA = ["#5E2410", "#7A3416", "#9C4A1A", "#B8661F", "#CF8A2E", "#D9A441"]
IRID = ["#3FD0C8", "#5FE0B0", "#4AA8E0"]


DOT_SCALE = [1.0]   # pixel radii are for the 1024-wide LOD0 atlas; the LOD1 atlas halves them


def _dots(cv, pts, radius_px, colors, opacity, rs, aspect=1.0):
    """Stamps soft round dots (pixel radius per dot; [aspect] = v/u pixel density ratio) onto a canvas, locally."""
    for (u, v), r, ci in zip(pts, radius_px, colors):
        r = max(0.6, r * DOT_SCALE[0])
        cx, cy = u * cv.w, (1 - v) * cv.h
        x0, x1 = int(max(0, cx - r - 2)), int(min(cv.w, cx + r + 3))
        y0, y1 = int(max(0, cy - r * aspect - 2)), int(min(cv.h, cy + r * aspect + 3))
        if x1 <= x0 or y1 <= y0:
            continue
        xx, yy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        d = np.sqrt((xx - cx) ** 2 + ((yy - cy) / aspect) ** 2)
        m = (1 - P.smoothstep(r - 0.9, r + 0.9, d)) * opacity
        col = C.hex2rgb(ci)
        sub = cv.rgb[y0:y1, x0:x1]
        cv.rgb[y0:y1, x0:x1] = sub * (1 - m[..., None]) + col[None, None] * m[..., None]


def paint_body(cv, aspect=1.0, seed=11):
    """x = body t (0 arm crown .. 1 mantle apex), y = flank height (1 dorsal .. 0 ventral)."""
    rs = P.rng(seed)
    u, v = cv.u, cv.v
    cv.vgrad("#EEE4D6", "#DCC09E", 0.0, 1.0, 0.9)
    # ventral mantle: silvery reflector over the light organ (the ink sac and reflector show through the skin)
    cv.blend(P.smoothstep(0.32, 0.05, v) * P.band(u, 0.3, 0.75, 0.08), "#D6E3E6", 0.55)
    # translucency: a faint warm cast where the gills and viscera sit
    cv.blend(P.band(u, 0.35, 0.8, 0.12) * P.band(v, 0.25, 0.6, 0.15), "#E7C9B0", 0.3)
    n = P.fbm(cv.h, cv.w, 12, 3, seed)
    cv.multiply(0.95 + 0.08 * n)
    # chromatophores: densest on the dorsal mantle and head, sparse on the belly; three size classes
    pts, rad, cols = [], [], []
    for _ in range(15000):
        pu, pv = rs.uniform(0, 1), rs.uniform(0, 1)
        dens = 0.06 + 0.94 * pv ** 1.6
        if pu < 0.08:
            dens *= 0.6
        if rs.uniform() > dens:
            continue
        pts.append((pu, pv))
        k = rs.uniform()
        rad.append(1.4 + 2.0 * rs.uniform() if k < 0.7 else 3.2 + 2.2 * rs.uniform())
        cols.append(CHROMA[min(len(CHROMA) - 1, int(rs.uniform() ** (1.2 - 0.6 * pv) * len(CHROMA)))])
    _dots(cv, pts, rad, cols, 0.92, rs, aspect)
    # iridophore flecks: blue-green spangles on the dorsal mantle and round the head
    pts, rad, cols = [], [], []
    for _ in range(700):
        pu, pv = rs.uniform(0, 1), rs.uniform(0.35, 1)
        if rs.uniform() > (0.4 + 0.6 * P.band(np.array(pu), 0.08, 0.3, 0.05)) * pv:
            continue
        pts.append((pu, pv))
        rad.append(1.2 + 1.4 * rs.uniform())
        cols.append(IRID[int(rs.integers(0, len(IRID)))])
    _dots(cv, pts, rad, cols, 0.75, rs, aspect)
    # darker dorsal saddle on the head between the eyes
    cv.blend(P.band(u, 0.06, 0.2, 0.04) * P.smoothstep(0.75, 0.95, v), "#8A4A22", 0.35)


def paint_fin(cv, aspect=1.0, seed=12):
    """x = along the fin base (front .. back), y = hinge (0 base .. 1 edge): translucent with a few chromatophores."""
    rs = P.rng(seed)
    cv.vgrad("#E2CDB2", "#F2E8DC", 0.0, 1.0)
    cv.a[:] = 0.92 - 0.35 * cv.v
    pts, rad, cols = [], [], []
    for _ in range(500):
        pu, pv = rs.uniform(0, 1), rs.uniform(0, 1)
        if rs.uniform() > 0.9 - 0.8 * pv:
            continue
        pts.append((pu, pv))
        rad.append(0.9 + 1.4 * rs.uniform())
        cols.append(CHROMA[int(rs.integers(1, len(CHROMA)))])
    _dots(cv, pts, rad, cols, 0.85, rs, aspect)
    # pale edge
    cv.blend(P.smoothstep(0.85, 1.0, cv.v), "#F6F0E8", 0.6)


def paint_arm(cv, aspect=1.0, seed=13):
    """x = along the arm (0 base .. 1 tip), y = around (1 aboral/outer .. 0 oral face with the suckers)."""
    rs = P.rng(seed)
    u, v = cv.u, cv.v
    cv.vgrad("#F1E6DA", "#D8B996", 0.0, 1.0)
    pts, rad, cols = [], [], []
    for _ in range(2200):
        pu, pv = rs.uniform(0, 1), rs.uniform(0, 1)
        if rs.uniform() > 0.05 + 0.9 * pv ** 1.4:
            continue
        pts.append((pu, pv))
        rad.append(0.9 + 1.5 * rs.uniform())
        cols.append(CHROMA[int(rs.integers(0, len(CHROMA)))])
    _dots(cv, pts, rad, cols, 0.9, rs, aspect)
    # suckers on the oral face: two rows of small pale rings
    for row in (0.12, 0.26):
        n = 28
        for i in range(n):
            pu = 0.04 + 0.92 * (i + 0.5 * (row > 0.2)) / n
            r = 2.6 * (1 - 0.6 * pu)
            _dots(cv, [(pu, row)], [r], ["#F7EFE6"], 0.9, rs, aspect)
            _dots(cv, [(pu, row)], [r * 0.45], ["#CDB8A4"], 0.9, rs, aspect)


def paint_tentacle(cv, aspect=1.0, seed=14):
    """x = along the tentacle (0 base .. 1 club tip): pale stalk, the club speckled with tiny suckers."""
    rs = P.rng(seed)
    cv.vgrad("#F2E9DE", "#E2CBB0", 0.0, 1.0)
    club = P.smoothstep(0.72, 0.8, cv.u)
    cv.blend(club * P.smoothstep(0.5, 0.2, cv.v), "#F8F2EA", 0.8)
    pts = [(rs.uniform(0.78, 1.0), rs.uniform(0.02, 0.4)) for _ in range(160)]
    _dots(cv, pts, [1.0 + 0.6 * rs.uniform() for _ in pts], ["#C9B29A"] * len(pts), 0.8, rs, aspect)
    pts = [(rs.uniform(0, 1), rs.uniform(0.5, 1)) for _ in range(300)]
    _dots(cv, pts, [0.9 + 1.0 * rs.uniform() for _ in pts], [CHROMA[int(rs.integers(1, 5))] for _ in pts], 0.8, rs, aspect)


class SquidAtlas:
    """Atlas with rows sized to each part so the painted chromatophores stay round on the mesh: the body gets most
    of the height (it is long and wide), the fins use the left third of their row (a short attachment line).
    Region k = (u0, row0, u1, row1) in pixels of a w x h canvas."""
    ROWS = [(0.0, 0.0, 1.0, 0.625), (0.0, 0.625, 0.3, 0.75), (0.0, 0.75, 1.0, 0.875), (0.0, 0.875, 1.0, 1.0)]

    def __init__(self, w, h):
        self.cv = P.Canvas(w, h, "#EEE4D6")
        self.w, self.h = w, h

    def rect(self, k):
        u0, r0, u1, r1 = self.ROWS[k]
        return int(u0 * self.w), int(r0 * self.h), int(u1 * self.w), int(r1 * self.h)

    def uv(self, k, t, a=None):
        x0, y0, x1, y1 = self.rect(k)
        t = np.clip(np.asarray(t, float), 0.002, 0.998)
        a = np.full_like(t, 0.5) if a is None else np.clip(np.asarray(a, float), 0.02, 0.98)
        u = (x0 + t * (x1 - x0)) / self.w
        v = 1 - (y0 + (1 - a) * (y1 - y0)) / self.h
        return np.stack([u, v], -1)

    def paint(self, k, fn, extent):
        """extent: (along, around) of the region on the unit model, so dots can be stamped round."""
        x0, y0, x1, y1 = self.rect(k)
        sub = P.Canvas(x1 - x0, y1 - y0)
        aspect = ((y1 - y0) / extent[1]) / ((x1 - x0) / extent[0])
        fn(sub, aspect)
        self.cv.rgb[y0:y1, x0:x1] = sub.rgb
        self.cv.a[y0:y1, x0:x1] = sub.a


def build_atlas(lod):
    at = SquidAtlas(1024 if lod == 0 else 512, 1024 if lod == 0 else 512)
    DOT_SCALE[0] = at.w / 1024
    # (along, around) in body lengths: body 0.73 long x half its girth; fin base 0.23 x reach 0.15; arm 0.25 x half
    # its girth; tentacle 0.27 x half its girth
    at.paint(0, paint_body, (0.73, 0.58))
    at.paint(1, paint_fin, (0.23, 0.15))
    at.paint(2, paint_arm, (0.25, 0.05))
    at.paint(3, paint_tentacle, (0.27, 0.03))
    path = os.path.join(C.BUILD, f"{SID}_atlas{'_lod1' if lod else ''}.png")
    at.cv.save(path, alpha=True)
    return at, path


# ----------------------------------------------------------------------------
# geometry
# ----------------------------------------------------------------------------
def body_geo(at, g, sides, n):
    prof = np.asarray(PROFILE, float)
    xs = np.linspace(prof[0, 0], prof[-1, 0], n)
    order = np.argsort(prof[:, 0])
    W = np.interp(xs, prof[order, 0], prof[order, 1])
    H = np.interp(xs, prof[order, 0], prof[order, 2])
    Z = np.interp(xs, prof[order, 0], prof[order, 3])
    # smooth the piecewise-linear profile a little
    k = np.array([0.25, 0.5, 0.25])
    for arr in (W, H):
        arr[1:-1] = np.convolve(arr, k, mode="same")[1:-1]
    V, UVt, A = [], [], []
    for i, x in enumerate(xs):
        for j in range(sides):
            ang = 2 * math.pi * j / sides
            # slightly flatter belly (the mantle rests on the sand)
            zc = math.cos(ang)
            zf = zc if zc > 0 else zc * 0.92
            V.append((x, W[i] * math.sin(ang), Z[i] + H[i] * zf))
            UVt.append(((prof[0, 0] - x) / (prof[0, 0] - prof[-1, 0]), j / sides))
    F = []
    for i in range(n - 1):
        for j in range(sides):
            a = i * sides + j
            b = i * sides + (j + 1) % sides
            F.append((a, a + sides, b + sides, b))
    # caps: the arm crown (front) and the mantle apex (back)
    front = len(V)
    V.append((xs[0] + 0.006, 0.0, Z[0]))
    UVt.append((0.0, 0.0))
    back = len(V)
    V.append((xs[-1] - 0.008, 0.0, Z[-1]))
    UVt.append((1.0, 0.5))
    last = (n - 1) * sides
    for j in range(sides):
        F.append(((j + 1) % sides, j, front))
        F.append((last + j, last + (j + 1) % sides, back))
    V = np.array(V)
    UVt = np.array(UVt)
    h = _mirror_h(UVt[:, 1])
    UV = at.uv(0, UVt[:, 0], h)
    dorsal = np.clip(0.5 + 0.5 * (V[:, 2] - np.r_[np.repeat(Z, sides), Z[0], Z[-1]]) /
                     np.r_[np.repeat(H, sides), H[0], H[-1]], 0, 1)
    COL = np.stack([dorsal, np.zeros(len(V)), np.zeros(len(V)), 0.5 - V[:, 0]], -1)
    g.add(V, F, UV, COL)
    # funnel: a short cone under the head, opening forward (the jet nozzle)
    fp = smooth_path([(0.045, 0.0, -0.085), (0.10, 0.0, -0.088), (0.145, 0.0, -0.078)], 5 if sides > 10 else 3)
    Vt, Ft, tv, av = G.tube(fp, np.linspace(0.04, 0.022, len(fp)), 8 if sides > 10 else 5, cap_end=True,
                            up=(0, 0, 1))
    g.add(Vt, Ft, at.uv(0, 0.18 + 0.1 * tv, np.full(len(Vt), 0.05)),
          np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)), np.zeros(len(Vt)), 0.5 - Vt[:, 0]], -1))
    return xs, W, H, Z


def fin_geo(at, g, rings, segs, xs, W, H, Z):
    """Two round ear-shaped fins on the dorsolateral mantle: thin single sheets (fin shader is two-sided)."""
    cx, half_len, reach = -0.17, 0.115, 0.15
    for side in (1, -1):
        wc = float(np.interp(cx, xs[::-1], W[::-1]))
        hc = float(np.interp(cx, xs[::-1], H[::-1]))
        base_c = np.array([cx, side * wc * 0.86, 0.38 * hc])
        out = np.array([0.0, side * 1.0, 0.22])
        out /= np.linalg.norm(out)
        V, UVt, B = [], [], []
        for i in range(rings + 1):
            r = i / rings
            for j in range(segs + 1):
                th = math.pi * j / segs
                ax = half_len * math.cos(th) * (1 - 0.25 * r)
                # attachment follows the mantle surface; the free edge is a rounded ear, cupped a little upward
                x = cx + ax
                wx = float(np.interp(x, xs[::-1], W[::-1]))
                hx = float(np.interp(x, xs[::-1], H[::-1]))
                b = np.array([x, side * wx * 0.86, 0.38 * hx])
                p = b + out * (reach * r * math.sin(th) ** 0.45) + np.array([0, 0, 0.02 * r * r * math.sin(th)])
                V.append(p)
                UVt.append((j / segs, r))
                B.append(r)
        F = []
        for i in range(rings):
            for j in range(segs):
                a = i * (segs + 1) + j
                f = (a, a + 1, a + segs + 2, a + segs + 1)
                F.append(f if side > 0 else f[::-1])
        V = np.array(V)
        UVt = np.array(UVt)
        UV = at.uv(1, UVt[:, 0], UVt[:, 1])
        COL = np.stack([np.zeros(len(V)), np.zeros(len(V)), np.array(B), np.full(len(V), 0.5 - cx)], -1)
        g.add(V, F, UV, COL)
    return cx


def eye_geo(g, segs, rings):
    c = np.array([0.150, 0.0, 0.030])
    for side in (1, -1):
        p = c + np.array([0.0, side * 0.088, 0.0])
        Ve, Fe = G.ellipsoid(p, (0.056, 0.05, 0.052), segs, rings)
        # iris/pupil side facing out: mat 1 (dark) on the outer half, mat 0 (iridescent rim) inside
        g.add(Ve, Fe, None, [(0, 0, 0, 0.5 - c[0])] * len(Ve), mat=0)
        Vp, Fp = G.ellipsoid(p + np.array([0.0, side * 0.034, 0.002]), (0.036, 0.022, 0.034), segs, max(4, rings - 2))
        g.add(Vp, Fp, None, [(0, 0, 0, 0.5 - c[0])] * len(Vp), mat=1)
    return c


def arms_geo(at, g, n, sides):
    """Eight short arms round the crown and the two feeding tentacles (between arms III and IV)."""
    base_r = 0.034
    x0 = ARM_CROWN_X
    tips = []
    for k in range(8):
        pair = k // 2
        side = 1 if k % 2 == 0 else -1
        ang = math.radians(22.5 + 45 * pair) * side  # 0 = dorsal
        radial = np.array([0.0, math.sin(ang), math.cos(ang)])
        L = ARM_LEN[pair]
        base = np.array([x0, 0.0, 0.0]) + radial * base_r
        splay = math.radians(16 + 4 * pair)
        d = np.array([math.cos(splay), 0, 0]) + radial * math.sin(splay)
        # tips curl a little inward and down (resting posture)
        ctrl = [base, base + d * L * 0.35, base + d * L * 0.7 + radial * 0.01,
                base + d * L * 0.98 - radial * 0.025 + np.array([0, 0, -0.012])]
        pts = G.bezier(*ctrl, n)
        ln = np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1))
        pts = base + (pts - base) * (L / ln)
        R = 0.021 * (1 - 0.8 * np.linspace(0, 1, n) ** 1.2) + 0.0025
        Vt, Ft, tv, av = G.tube(pts, R, sides, cap_end=True, up=tuple(radial))
        # around: 0 = the radial (outer, aboral) side -> flank height 1; the oral face toward the axis -> 0
        UV = at.uv(2, tv, _mirror_h(av))
        COL = np.stack([np.full(len(Vt), 1 / 8), np.full(len(Vt), k / 8), tv, np.full(len(Vt), 0.5 - x0)], -1)
        g.add(Vt, Ft, UV, COL)
        tips.append(pts[-1])
    for t, side in ((0, 1), (1, -1)):
        ang = math.radians(135) * side
        radial = np.array([0.0, math.sin(ang), math.cos(ang)])
        base = np.array([x0 - 0.004, 0.0, 0.0]) + radial * base_r * 0.8
        d = np.array([1.0, 0, 0]) + radial * math.tan(math.radians(9))
        d /= np.linalg.norm(d)
        L = TENTACLE_LEN
        ctrl = [base, base + d * L * 0.33, base + d * L * 0.66 + radial * 0.008, base + d * L]
        nn = n + 3
        pts = G.bezier(*ctrl, nn)
        tt = np.linspace(0, 1, nn)
        # thin stalk, the club widening over the last fifth
        R = 0.0085 * (1 - 0.3 * tt) + 0.008 * np.exp(-((tt - 0.88) / 0.08) ** 2)
        R[-1] = 0.004
        Vt, Ft, tv, av = G.tube(pts, R, max(4, sides - 1), cap_end=True, up=tuple(radial), squash=0.8)
        UV = at.uv(3, tv, _mirror_h(av))
        COL = np.stack([np.full(len(Vt), 2 / 8), np.full(len(Vt), t / 8), tv, np.full(len(Vt), 0.5 - x0)], -1)
        g.add(Vt, Ft, UV, COL)
        tips.append(pts[-1])
    return tips


def build(lod=0):
    C.reset_scene()
    at, tex = build_atlas(lod)
    img = C.load_image(tex, name=f"{SID}{'_lod1' if lod else ''}")
    mskin = C.material(f"{SID}_skin", rough=0.35, image=img)
    mfin = C.material(f"{SID}_fin", rough=0.45, image=img, image_alpha=True, double=True)
    meye_rim = C.material(f"{SID}_eye_rim", color="#6E7F62", rough=0.2, metal=0.3)
    meye = C.material(f"{SID}_eye", color="#07090A", rough=0.08)
    root = make_root("Root", dict(
        species=SID, lengthCm=LENGTH_CM, unit="body length 1.0 = arm tips (+X) .. mantle apex, facing +X",
        iridescence=0.45, roughness=0.35, motion="fin_jet", lod=lod,
        source="Euprymna berryi: Jolly et al. 2022 (Front Mar Sci 9:1039775); MBL Cephalopod program"))
    if lod == 0:
        b_sides, b_n, f_r, f_s, e_s, e_r, a_n, a_s = 22, 40, 5, 12, 14, 10, 10, 6
    else:
        b_sides, b_n, f_r, f_s, e_s, e_r, a_n, a_s = 12, 18, 2, 6, 8, 6, 5, 4
    gb = G.Geo()
    xs, W, H, Z = body_geo(at, gb, b_sides, b_n)
    gf = G.Geo()
    fin_geo(at, gf, f_r, f_s, xs, W, H, Z)
    ge = G.Geo()
    ec = eye_geo(ge, e_s, e_r)
    ga = G.Geo()
    arms_geo(at, ga, a_n, a_s)
    body = geo_obj("Body", gb, root, "body", [mskin])
    fins = geo_obj("Fins", gf, root, "fin", [mfin], extras={"count": 2})
    eyes = geo_obj("Eyes", ge, root, "eye", [meye_rim, meye], extras={"pivot": gltf_pt(ec)})
    arms = geo_obj("Arms", ga, root, "leg", [mskin], extras={"count": 10, "arms": 8, "tentacles": 2})
    objs = [body, fins, eyes, arms]
    # normalise to unit length (x extent 1.0, centred) and fix COLOR_0.a = 0.5 - x of each vertex's station
    lo = min(C.get_verts(o)[:, 0].min() for o in objs)
    hi = max(C.get_verts(o)[:, 0].max() for o in objs)
    sc = 1.0 / (hi - lo)
    off = -(lo + hi) / 2
    for o in objs:
        Vv = C.get_verts(o)
        Vv[:, 0] += off
        Vv *= sc
        o.data.vertices.foreach_set("co", Vv.ravel())
        o.data.update()
        cols = C.get_cols(o)
        # A was 0.5 - x_raw for the station; map x_raw -> x_norm
        x_raw = 0.5 - cols[:, 3]
        cols[:, 3] = np.clip(0.5 - (x_raw + off) * sc, 0.0, 1.0)
        C.set_cols(o, cols)
    pv = (np.asarray(ec, float) + np.array([off, 0, 0])) * sc
    C.set_extras(eyes, pivot=gltf_pt(pv))
    for o in (body, eyes, arms):
        C.recalc_normals(o)
    name = SID + ("_lod1" if lod else "")
    return finish(root, objs, os.path.join(C.MODELS, f"{name}.glb"), BUDGET_LOD1 if lod else BUDGET)


def stations(path=None):
    """Collision stations (u, top, bottom, halfWidth in body lengths) measured from the exported body mesh."""
    C.reset_scene()
    bpy = C.bpy
    bpy.ops.import_scene.gltf(filepath=path or os.path.join(C.MODELS, f"{SID}.glb"))
    body = next(o for o in bpy.data.objects if o.type == 'MESH' and o.get("part") == "body")
    V = np.array([body.matrix_world @ v.co for v in body.data.vertices])  # blender frame
    out = []
    for u in (0.0, 0.15, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0):
        x = 0.5 - u
        sel = np.abs(V[:, 0] - x) < 0.03
        if not sel.any():
            out.append((u, 0.0, 0.0, 0.0))
            continue
        out.append((u, round(float(V[sel, 2].max()), 3), round(float(V[sel, 2].min()), 3),
                    round(float(np.abs(V[sel, 1]).max()), 3)))
    return out


def build_all():
    t0 = build(0)
    t1 = build(1)
    return t0, t1


if __name__ == "__main__":
    print(build_all())
    print("stations", stations())
