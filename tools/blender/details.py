"""Static reef and glass details from the 2026-10-13 backlog (ASSETS.md §4.1) -> models/reef_details.glb.

Everything is in WORLD coordinates (cm) like the corals. The renderer draws this file as a scene model with
`drawStatic` (opaque pass, then its blended materials after the algae cards), so nothing here sways; the two
hair-algae items that should move with the flow live in `algae_tufts.glb` instead (corals.algae_tufts).

Nodes (all under `Root`):
  CorallineLow    part "body"       alpha-masked coralline crust layer draped 0.12 cm over the lower-left rock under
                                    the clowns (the R5 mound's right flank); the coat is a texture, not geometry
  EncrustYellow   part "body"       lemon-yellow lumpy encrusting colony (~6 x 5 cm) beside the clowns' sleep spot
  MushroomsRed    part "body"       three small rust-red mushroom clumps on the open front sand
  MagCleaner      part "equipment"  dark grey oval magnet cleaner (inner half) on the left glass, algae fringe
  MagCleanerOuter part "equipment"  its outer half on the outside of the left pane
  FilmBackGlass   part "backglass"  blended green algae haze 0.06 cm in front of the back pane

COLOR_0 follows the coral convention (r thickness, g phase, b sway weight = 0 everywhere: static, a = 1).
"""
import math
import os

import numpy as np

import common as C
import corals as K
import paintlib as P
import tank as T
from common import bpy, hex2rgb

S = P.smoothstep
UP = np.array([0.0, 1.0, 0.0])
RS = np.random.default_rng
unit = K.unit
tex = K.tex

# placements (world cm); see ASSETS.md §4.1
CORALLINE_CENTER = np.array([76.0, 9.0, 26.0])
CORALLINE_RADII = np.array([7.5, 6.5, 6.5])
ENCRUST_SITE = np.array([80.0, 17.8, 21.8])  # R5/arch-leg face 7 cm left-front of the clowns' hairy-mushroom clump
MUSHROOM_CLUMPS = ((27.0, 30.0, 5), (47.5, 29.6, 4), (96.0, 29.2, 5))  # (x, z, discs)
MAG_CENTER = (22.0, 11.0)  # (y, z) on the left pane, behind the Flipper's line of sight
GLASS_T = 0.635


def rock_arrays():
    """World-space vertices and triangles of rockscape.glb (LOD0)."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.join(C.MODELS, "rockscape.glb"))
    new = [o for o in bpy.data.objects if o not in before]
    V, F = [], []
    for o in new:
        if o.type != 'MESH':
            continue
        mw = o.matrix_world
        b = len(V)
        V += [C.b2w(tuple(mw @ v.co)) for v in o.data.vertices]
        F += [tuple(b + i for i in p.vertices) for p in o.data.polygons]
    for o in new:
        bpy.data.objects.remove(o, do_unlink=True)
    return np.array(V, float), np.array(F, int)


class Sand:
    """Ray queries against the tank's Sand node."""

    def __init__(self):
        from mathutils.bvhtree import BVHTree
        before = set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=os.path.join(C.MODELS, "tank.glb"))
        new = [o for o in bpy.data.objects if o not in before]
        sand = [o for o in new if o.type == 'MESH' and o.name.startswith("Sand")][0]
        mw = sand.matrix_world
        self.bvh = BVHTree.FromPolygons([tuple(mw @ v.co) for v in sand.data.vertices],
                                        [tuple(p.vertices) for p in sand.data.polygons])
        for o in new:
            bpy.data.objects.remove(o, do_unlink=True)

    def height(self, x, z):
        from mathutils import Vector
        loc, *_ = self.bvh.ray_cast(Vector(C.w2b((x, 40.0, z))), Vector(C.w2b((0, -1.0, 0))), 60)
        return C.b2w(loc)[1] if loc is not None else 4.0


def clip_material(name, image, cutoff=0.5, rough=0.8, emissive=None, emissive_strength=0.0):
    """Alpha-tested material: Alpha = 1 - (texture alpha < cutoff), which the glTF exporter writes as
    alphaMode MASK with alphaCutoff = cutoff (drawn in the opaque pass)."""
    m = C.material(name, rough=rough, image=image, double=True, emissive=emissive,
                   emissive_strength=emissive_strength)
    nt = m.node_tree
    tx = [n for n in nt.nodes if n.type == 'TEX_IMAGE'][0]
    bsdf = nt.nodes["Principled BSDF"]
    lt = nt.nodes.new("ShaderNodeMath")
    lt.operation = 'LESS_THAN'
    lt.inputs[1].default_value = cutoff
    sub = nt.nodes.new("ShaderNodeMath")
    sub.operation = 'SUBTRACT'
    sub.inputs[0].default_value = 1.0
    nt.links.new(tx.outputs["Alpha"], lt.inputs[0])
    nt.links.new(lt.outputs[0], sub.inputs[1])
    nt.links.new(sub.outputs[0], bsdf.inputs["Alpha"])
    m.surface_render_method = 'DITHERED'
    return m


def tangent_frame(n):
    n = unit(n)
    t1 = unit(np.cross(UP if abs(n[1]) < 0.95 else np.array([0, 0, 1.0]), n))
    return t1, np.cross(n, t1)


def vertex_normals(V, F):
    fn = np.cross(V[F[:, 1]] - V[F[:, 0]], V[F[:, 2]] - V[F[:, 0]])
    vn = np.zeros_like(V)
    for k in range(3):
        np.add.at(vn, F[:, k], fn)
    return unit(vn)


# ----------------------------------------------------------------------------
# 3. coralline crust layer (texture-driven) on the lower-left rock under the clowns
# ----------------------------------------------------------------------------
def coralline(V, F, target_tris=1100):
    cen = V[F].mean(1)
    fn = unit(np.cross(V[F[:, 1]] - V[F[:, 0]], V[F[:, 2]] - V[F[:, 0]]))
    d = (((cen - CORALLINE_CENTER) / CORALLINE_RADII) ** 2).sum(1)
    sel = (d < 1.0) & (fn[:, 1] > -0.55) & (fn[:, 2] > -0.35) & (cen[:, 1] > 3.0)
    Fs = F[sel]
    used = np.unique(Fs)
    remap = -np.ones(len(V), int)
    remap[used] = np.arange(len(used))
    Vs, Fs = V[used], remap[Fs]
    print(f"[details] coralline: {len(Fs)} rock tris selected")
    if len(Fs) > target_tris:  # collapse-decimate the patch (the offset is applied after, so it stays outside)
        o = C.mesh_object("tmp_coralline", T.to_b(Vs), [tuple(f) for f in Fs], smooth=False)
        md = o.modifiers.new("dec", 'DECIMATE')
        md.ratio = target_tris / len(Fs)
        C.apply_modifiers(o)
        C.triangulate(o)
        Vs = np.array([C.b2w(tuple(v.co)) for v in o.data.vertices], float)
        Fs = np.array([tuple(p.vertices) for p in o.data.polygons], int)
        bpy.data.objects.remove(o, do_unlink=True)
    vn = vertex_normals(Vs, Fs)
    Vs = Vs + vn * 0.12
    n0 = unit(vn.mean(0))
    t1, t2 = tangent_frame(n0)
    dp = Vs - CORALLINE_CENTER
    Rr = 9.0
    uv = np.c_[0.5 + dp @ t1 / (2 * Rr), 0.5 + dp @ t2 / (2 * Rr)]
    col = np.tile([0.0, 0.0, 0.0, 1.0], (len(Vs), 1))
    # texture: dense pink/purple coat with plate margins and a ragged, lobed outer edge
    N = 512
    cv = P.Canvas(N, N, "#7A4E86", 0.0)
    rs = RS(301)
    r = np.hypot(cv.u - 0.5, cv.v - 0.5) * 2  # 1 = 9 cm from the centre
    f1 = P.fbm(N, N, 4, 4, 302)
    f2 = P.fbm(N, N, 12, 3, 303)
    core = 1 - S(0.62, 0.80, r + 0.22 * (f1 - 0.5))
    plates = np.zeros((N, N), np.float32)
    rim = np.zeros((N, N), np.float32)
    for (pu, pv) in P.poisson(rs, 70, 0.045, box=(0.08, 0.92, 0.08, 0.92)):
        rr = rs.uniform(0.03, 0.07)
        dd = np.hypot(cv.u - pu, cv.v - pv) / rr
        if math.hypot(pu - 0.5, pv - 0.5) * 2 > 0.9:
            continue
        plates = np.maximum(plates, 1 - S(0.85, 1.0, dd + 0.25 * (f2 - 0.5)))
        rim = np.maximum(rim, S(0.7, 0.92, dd) * (1 - S(0.92, 1.02, dd)))
    cover = np.maximum(core, plates * (1 - S(0.75, 0.95, r)))
    holes = S(0.70, 0.76, f2) * S(0.15, 0.4, r)  # small bare spots inside the coat
    cv.a = np.clip(cover - holes, 0, 1).astype(np.float32)
    cv.blend(S(0.40, 0.65, f1), "#B07592", 0.8)          # mauve
    cv.blend(S(0.52, 0.72, f2), "#D2608C", 0.8)          # pink
    cv.blend(1 - S(0.22, 0.42, f1), "#5E3C78", 0.85)     # deep purple
    cv.blend(rim * (1 - core * 0.6), "#E7A9C0", 0.75)    # pale growing plate margins
    edge = S(0.55, 0.75, r) * (1 - S(0.75, 0.85, r))
    cv.blend(edge, "#E2A4C2", 0.45)
    dots = (P.fbm(N, N, 48, 1, 304) > 0.78).astype(np.float32)  # conceptacles
    cv.blend(dots, "#5E3A6C", 0.5)
    cv.multiply(0.88 + 0.24 * P.fbm(N, N, 24, 2, 305))
    m = clip_material("coralline_crust", tex("coralline_crust", cv, alpha=True), 0.5, rough=0.9)
    return Vs, Fs, uv, col, m


# ----------------------------------------------------------------------------
# 2. lemon-yellow encrusting colony
# ----------------------------------------------------------------------------
def encrusting(rock):
    hit, hn, _ = rock.nearest(ENCRUST_SITE)
    n0 = unit(hn * 0.7 + np.array([0, 0, 1.0]) * 0.3)
    t1, t2 = tangent_frame(n0)
    print(f"[details] encrusting yellow at {np.round(hit, 1)} normal {np.round(n0, 2)}")
    rs = RS(311)
    a, b = 3.0, 2.5  # 6 x 5 cm
    rings, segs = 7, 20
    lumps = [(rs.uniform(-0.8, 0.8), rs.uniform(-0.75, 0.75), rs.uniform(0.2, 0.45), rs.uniform(0.13, 0.26))
             for _ in range(18)]
    ph1, ph2 = rs.uniform(0, 6.28, 2)
    V, UV, F = [], [], []

    def point(x, y):  # x, y in [-1, 1] ellipse units
        off = t1 * a * x + t2 * b * y
        p0 = hit + off
        h_, nn = rock.ray(p0 + n0 * 4.0, -n0, 10.0)
        if h_ is None:
            h_, nn, _ = rock.nearest(p0)
        rr = math.hypot(x, y)
        hgt = 0.18 + sum(am * math.exp(-((x - lx) ** 2 + (y - ly) ** 2) / s ** 2) for lx, ly, am, s in lumps)
        hgt = hgt * (1 - S(0.6, 1.0, rr)) - 0.08 * S(0.85, 1.0, rr)
        nb = unit(unit(nn) * 0.5 + n0 * 0.5)
        return h_ + nb * hgt

    V.append(point(0, 0))
    UV.append((0.5, 0.5))
    for k in range(1, rings + 1):
        t = k / rings
        for j in range(segs):
            th = 2 * math.pi * j / segs
            edge = 1 + 0.12 * math.sin(3 * th + ph1) + 0.07 * math.sin(5 * th + ph2)
            x, y = t * edge * math.cos(th), t * edge * math.sin(th)
            V.append(point(x, y))
            UV.append((0.5 + 0.42 * x, 0.5 + 0.42 * y))
    for j in range(segs):
        F.append((0, 1 + j, 1 + (j + 1) % segs))
    for k in range(1, rings):
        b0, b1 = 1 + (k - 1) * segs, 1 + k * segs
        for j in range(segs):
            j2 = (j + 1) % segs
            F.append((b0 + j, b1 + j, b1 + j2, b0 + j2))
    V = np.array(V)
    col = np.tile([0.35, 0.0, 0.0, 1.0], (len(V), 1))
    # texture: lemon yellow, paler lump tops, a fine darker reticulate surface and a few oscula on the lumps
    N = 128
    cv = P.Canvas(N, N, "#F0D432")
    x = (cv.u - 0.5) / 0.42
    y = (cv.v - 0.5) / 0.42
    top = np.zeros((N, N), np.float32)
    for lx, ly, am, s in lumps:
        top = np.maximum(top, am / 0.5 * np.exp(-((x - lx) ** 2 + (y - ly) ** 2) / (s * 0.6) ** 2))
    cv.blend(top, "#FBEA6A", 0.7)
    vor = P.voronoi(N, N, 90, 312)
    net = 1 - S(0.0, 1.6, vor[1] - vor[0])
    cv.blend(net, "#C9A52A", 0.35)
    cv.blend(S(0.55, 0.85, P.fbm(N, N, 6, 3, 313)), "#E2BE2C", 0.4)
    rr = np.hypot(x, y)
    cv.blend(S(0.75, 1.05, rr), "#C8A83A", 0.5)  # thinner, duller margin over the rock
    for lx, ly, am, s in lumps[:6]:
        cv.blend(P.disc_px(cv, 0.5 + 0.42 * lx, 0.5 + 0.42 * ly, 0.012, 1.0), "#6E5A1A", 0.85)
    m = C.material("encrust_yellow", rough=0.75, image=tex("encrust_yellow", cv), emissive="#F2DA3A",
                   emissive_strength=0.05)
    return V, F, np.array(UV), col, m, hit


# ----------------------------------------------------------------------------
# 6. rust-red mushroom clumps on the front sand
# ----------------------------------------------------------------------------
def mushrooms(sand):
    rs = RS(321)
    V, F, UV, COL = [], [], [], []
    segs = 8
    sites = []
    for ci, (cx, cz, nd) in enumerate(MUSHROOM_CLUMPS):
        sites.append((cx, sand.height(cx, cz), cz))
        pts = P.poisson(rs, nd, 1.5, box=(-1.8, 1.8, -1.4, 1.4))
        for di, (ox, oz) in enumerate(pts):
            x, z = cx + ox, min(cz + oz, 31.6)
            y0 = sand.height(x, z)
            r = rs.uniform(0.6, 1.1)
            h = rs.uniform(0.35, 0.9)
            n = unit(UP + np.array([rs.normal(0, 0.3), 0, rs.normal(0, 0.3) + 0.15]))
            t1, t2 = tangent_frame(n)
            base = np.array([x, y0 - 0.2, z])
            ctr = np.array([x, y0 + h, z]) + n * 0.1
            cup = rs.uniform(-0.12, 0.15)  # >0 cupped, <0 domed
            ph = rs.uniform(0, 6.28)
            b = len(V)
            V.append(ctr - n * cup)
            UV.append((0.25, 0.5))
            for ring, rf in ((1, 0.5), (2, 1.0)):
                for j in range(segs):
                    th = 2 * math.pi * j / segs
                    wob = 0.07 * math.sin(3 * th + ph) * rf
                    p = ctr + (t1 * math.cos(th) + t2 * math.sin(th)) * r * rf * (1 + 0.08 * math.sin(2 * th + ph))
                    p = p + n * (wob - cup * (1 - rf) + (0.04 if rf < 1 else -0.06))
                    V.append(p)
                    UV.append((0.25 + 0.23 * rf * math.cos(th), 0.5 + 0.46 * rf * math.sin(th)))
            for j in range(segs):  # stalk foot ring, buried in the sand
                th = 2 * math.pi * j / segs
                V.append(base + (t1 * math.cos(th) + t2 * math.sin(th)) * r * 0.32)
                UV.append((0.75, 0.25 + 0.5 * j / segs))
            r1, r2, r3 = b + 1, b + 1 + segs, b + 1 + 2 * segs
            for j in range(segs):
                j2 = (j + 1) % segs
                F.append((b, r1 + j, r1 + j2))
                F.append((r1 + j, r2 + j, r2 + j2, r1 + j2))
                F.append((r2 + j, r3 + j, r3 + j2, r2 + j2))  # underside / stalk to the foot
            COL += [(0.5, (ci * 0.3 + di * 0.13) % 1, 0.0, 1.0)] * (1 + 3 * segs)
    # texture: left half disc top (polar), right half stalk / underside
    cv = P.Canvas(128, 64, "#9E3B22")
    du, dv = (cv.u - 0.25) / 0.23, (cv.v - 0.5) / 0.46
    rr = np.hypot(du, dv)
    th = np.arctan2(dv, du)
    left = cv.u < 0.5
    cv.blend(S(0.5, 0.95, 0.5 + 0.5 * np.sin(th * 22)) * S(0.25, 0.6, rr), "#B8553A", 0.45)
    cv.blend(1 - S(0.18, 0.38, rr), "#6E2414", 0.8)
    cv.blend(1 - S(0.04, 0.09, rr), "#C98A6A", 0.9)
    cv.blend(S(0.82, 1.0, rr), "#C0603E", 0.6)
    cv.blend((P.fbm(64, 128, 24, 1, 322) > 0.74).astype(np.float32) * S(0.3, 0.5, rr), "#D99A7A", 0.6)
    cv.multiply(0.9 + 0.2 * P.fbm(64, 128, 10, 2, 323))
    stalk = (~left).astype(np.float32)
    cv.blend(stalk, "#B0583C", 1.0)
    cv.blend(stalk * P.fbm(64, 128, 8, 2, 324), "#8E4028", 0.4)
    m = C.material("mushroom_red_small", rough=0.7, image=tex("mushroom_red_small", cv), emissive="#C04A2A",
                   emissive_strength=0.04)
    return np.array(V), F, np.array(UV), np.array(COL), m, sites


# ----------------------------------------------------------------------------
# 5. magnetic glass cleaner on the left pane
# ----------------------------------------------------------------------------
def mag_half(x0, sign, ay, az, thick, v0, v1, segs=16):
    """Rounded oval puck lying on the plane x = x0, growing toward sign*x. UV v runs v0 (rim on the glass) ->
    v1 (top centre); u = angle. Closed (flat pad cap on the glass)."""
    cy, cz = MAG_CENTER
    prof = ((1.0, 0.0), (1.0, 0.35), (0.97, 0.75), (0.86, 0.95), (0.6, 1.0))  # (radius scale, height fraction)
    V, UV, F = [], [], []
    for k, (s, hf) in enumerate(prof):
        for j in range(segs):
            th = 2 * math.pi * j / segs
            V.append((x0 + sign * hf * thick, cy + ay * s * math.sin(th), cz + az * s * math.cos(th)))
            UV.append((j / segs, v0 + (v1 - v0) * k / (len(prof))))
    top = len(V)
    V.append((x0 + sign * thick * 1.03, cy, cz))
    UV.append((0.5, v1))
    pad = len(V)
    V.append((x0, cy, cz))
    UV.append((0.5, v0))
    for k in range(len(prof) - 1):
        for j in range(segs):
            j2 = (j + 1) % segs
            a, b = k * segs + j, k * segs + j2
            c, d = (k + 1) * segs + j2, (k + 1) * segs + j
            F.append((a, b, c, d) if sign > 0 else (a, d, c, b))
    last = (len(prof) - 1) * segs
    for j in range(segs):
        j2 = (j + 1) % segs
        F.append((last + j, last + j2, top) if sign > 0 else (last + j2, last + j, top))
        F.append((j2, j, pad) if sign > 0 else (j, j2, pad))
    return np.array(V, float), F, np.array(UV, float)


def mag_cleaner():
    Vi, Fi, UVi = mag_half(0.04, +1, 2.6, 4.0, 2.0, 0.5, 1.0)
    Vo, Fo, UVo = mag_half(-GLASS_T - 0.04, -1, 3.0, 4.6, 2.6, 0.0, 0.5)
    N = 128
    cv = P.Canvas(N, N, "#3A3D40")
    v = cv.v
    inner = v >= 0.5
    t = np.where(inner, (v - 0.5) * 2, v * 2)  # 0 rim on the glass .. 1 top
    cv.vgrad("#3A3D40", "#3A3D40")
    cv.blend(S(0.4, 1.0, t), "#4A4E52", 0.8)
    cv.blend((1 - S(0.0, 0.08, t)), "#202224", 0.9)  # felt / rubber pad edge
    # algae fringe on the inner half's rim: ragged green-brown band, taller in places, with darker tufts
    fr = P.fbm(N, N, 8, 3, 331)
    height = 0.14 + 0.26 * fr
    band = (1 - S(height - 0.05, height + 0.03, t)) * inner
    cv.blend(band, "#5E6B38", 0.9)
    cv.blend(band * S(0.55, 0.75, P.fbm(N, N, 20, 2, 332)), "#3F5A2A", 0.7)
    cv.blend(band * (1 - S(0.0, 0.05, t)), "#2E3A1E", 0.6)
    specks = (P.fbm(N, N, 40, 1, 333) > 0.8).astype(np.float32) * inner * (1 - S(0.25, 0.5, t))
    cv.blend(specks, "#6E7E40", 0.6)
    m = C.material("mag_cleaner", rough=0.45, image=tex("mag_cleaner", cv))
    return (Vi, Fi, UVi), (Vo, Fo, UVo), m


# ----------------------------------------------------------------------------
# 4. green haze on the back glass
# ----------------------------------------------------------------------------
def back_film():
    x0, x1, y0, y1, z = 0.3, 121.6, 3.2, 55.6, 0.06
    V = np.array([(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)])
    F = [(0, 3, 2, 1)]  # faces +z (into the tank)
    UV = np.array([(0, 0), (1, 0), (1, 1), (0, 1)], float)
    W, H = 512, 256
    cv = P.Canvas(W, H, "#5F7F3A", 0.0)
    f1 = P.fbm(H, W, 6, 4, 341, aspect=1.0)
    f2 = P.fbm(H, W, 18, 3, 342)
    yy = cv.v  # 0 = sand line, 1 = water line
    xx = cv.u
    a = 0.10 + 0.22 * S(0.35, 0.75, f1)
    a += 0.16 * (1 - S(0.0, 0.25, yy)) + 0.10 * S(0.88, 1.0, yy)  # heavier at the sand line and the water line
    a += 0.08 * ((1 - S(0.0, 0.08, xx)) + S(0.92, 1.0, xx))      # and in the corners
    # two arcs wiped clean by a magnet cleaner (lighter film)
    for cx, cy, rad in ((0.32, 0.55, 0.22), (0.66, 0.5, 0.2)):
        d = np.hypot((xx - cx) * 2.0, yy - cy)
        a -= 0.12 * (1 - S(0.0, 0.05, np.abs(d - rad))) * S(0.2, 0.4, yy)
    spots = (f2 > 0.74).astype(np.float32)  # green spot-algae colonies
    a = np.maximum(a, spots * 0.55)
    cv.a = np.clip(a, 0.0, 0.62).astype(np.float32)
    cv.blend(S(0.5, 0.8, f2), "#4E6E2E", 0.6)
    cv.blend(spots, "#3E5A26", 0.8)
    cv.blend(1 - S(0.0, 0.2, yy), "#6E7A3A", 0.5)  # browner at the sand line
    m = C.material("film_backglass", rough=0.6, image=tex("film_backglass", cv, alpha=True), image_alpha=True,
                   double=True)
    return V, F, UV, m


def build():
    C.reset_scene()
    rock = K.Rock()
    sand = Sand()
    Vr, Fr = rock_arrays()
    root = C.empty("Root")
    C.set_extras(root, units="cm", species="details", backlog="2026-10-13", static=True)
    objs = []
    # 3. coralline
    V, F, UV, COL, m = coralline(Vr, Fr)
    g = T.WGeo()
    g.add(V, [tuple(f) for f in F], UV, COL)
    objs.append(g.obj("CorallineLow", root, "body", [m], smooth=True,
                      extras={"site": [round(float(c), 1) for c in CORALLINE_CENTER], "species": "coralline_algae",
                              "alphaTest": 0.5, "layer": "overlay"}))
    # 2. yellow encrusting
    V, F, UV, COL, m, hit = encrusting(rock)
    g = T.WGeo()
    g.add(V, F, UV, COL)
    objs.append(g.obj("EncrustYellow", root, "body", [m], smooth=True,
                      extras={"site": [round(float(c), 1) for c in hit], "species": "encrusting_yellow"}))
    # 6. rust-red mushrooms
    V, F, UV, COL, m, sites = mushrooms(sand)
    g = T.WGeo()
    g.add(V, F, UV, COL)
    objs.append(g.obj("MushroomsRed", root, "body", [m], smooth=True,
                      extras={"sites": [[round(float(c), 1) for c in s] for s in sites],
                              "species": "mushroom_red_small"}))
    # 5. magnetic cleaner
    (Vi, Fi, UVi), (Vo, Fo, UVo), m = mag_cleaner()
    for name, (V, F, UV) in (("MagCleaner", (Vi, Fi, UVi)), ("MagCleanerOuter", (Vo, Fo, UVo))):
        g = T.WGeo()
        g.add(V, F, UV, (0, 0, 0, 1))
        o = g.obj(name, root, "equipment", [m], smooth=True,
                  extras={"site": [0.0, MAG_CENTER[0], MAG_CENTER[1]], "pane": "left"})
        C.recalc_normals(o)
        objs.append(o)
    # 4. back-glass film
    V, F, UV, m = back_film()
    g = T.WGeo()
    g.add(V, F, UV, (0, 0, 0, 1))
    objs.append(g.obj("FilmBackGlass", root, "backglass", [m], smooth=False, extras={"pane": "back"}))
    for o in objs:
        C.triangulate(o)
    path = os.path.join(C.MODELS, "reef_details.glb")
    C.export_glb(path, C.all_descendants(root))
    for o in objs:
        print(f"[details] {o.name}: {C.tri_count(o)} tris")
    print(f"[details] reef_details.glb: {sum(C.tri_count(o) for o in objs)} tris, "
          f"{os.path.getsize(path) / 1024:.0f} KB")
    return path


if __name__ == "__main__":
    build()
