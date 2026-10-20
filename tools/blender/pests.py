"""Aiptasia, Berghia and the keeper's aiptasia tools (docs/research/aiptasia_control.md).

    /tmp/claude-0/bpyenv/bin/python tools/blender/pests.py            # all three GLBs
    /tmp/claude-0/bpyenv/bin/python tools/blender/pests.py aiptasia   # one of aiptasia, berghia, pest_tools

models/aiptasia.glb   ONE Exaiptasia polyp (cm, Y up, pedal disc at the origin, column along +Y). The renderer
                      (AiptasiaDraw) draws it once per AIPTASIA coral at its rock spot, scaled by the polyp's size and
                      pulled toward the pedal disc as it retracts. Node "Body" (part body -> soft-coral shader):
                      COLOR_0 r thickness, g sway phase, b sway weight (0 column .. 1 tentacle tips).
                      Mature polyp: column 2.4 cm, oral disc ~1 cm, two whorls of long tapering tentacles (Wikipedia:
                      column to 2.5 cm, disc to 1 cm, up to ~96 tentacles; 28 here for the triangle budget), translucent
                      brown from zooxanthellae with paler stripes and pale tips.
models/berghia.glb    Berghia stephanieae aeolid nudibranch, built like the micro critters (unit length facing +X,
                      Blender Z up, spline-skinned by fish.vert): translucent pale body, long oral tentacles and
                      rhinophores, rows of cerata with brown digestive-gland cores (the eaten aiptasia's algae) and
                      white cnidosac tips. COLOR_0 a = spine u (0 head .. 1 tail), b = hinge.
models/pest_tools.glb Props for the in-tank treatment animation (PestScene.kt), each authored with its working tip at
                      the origin and its body along +Y: Syringe + SyringePlunger + SyringeFill (unit-tall plug the
                      runtime scales and tints per product), Laser + LaserBeam (unit-long beam along -Y from the tip).
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import geomlib as G  # noqa: E402
import paintlib as P  # noqa: E402
import tank as T  # noqa: E402
from inverts import Atlas, smooth_path, make_root, finish, geo_obj  # noqa: E402
from microfauna import ell_tube, col_u, appendage, normalize_x, save_atlas  # noqa: E402

S = P.smoothstep
RS = np.random.RandomState


# ----------------------------------------------------------------------------------------------------------------
# aiptasia polyp
# ----------------------------------------------------------------------------------------------------------------
COLUMN_H = 2.4
DISC_R = 0.55
N_INNER, N_OUTER = 12, 16


def aiptasia_texture():
    """u 0..0.5 column (v up the column), u 0.5..1 tentacles (v base -> tip)."""
    cv = P.Canvas(128, 128, "#7A5A3A")
    col = cv.u < 0.5
    # column: translucent tan-brown, darker toward the foot, 12 paler mesentery stripes
    cv.vgrad("#5A4028", "#94744E", 0, 1, 1.0)
    stripes = (0.5 + 0.5 * np.cos(cv.u * 2 * math.pi * 24)) > 0.82
    cv.blend(col & stripes, "#C2A884", 0.45)
    # tentacles: brown with fine pale flecks, going cream toward the tips
    tent = ~col
    cv.blend(tent, "#86663F", 1.0)
    cv.blend(tent & (P.fbm(128, 128, 32, 3, 11) > 0.62), "#B49464", 0.5)
    tip = np.clip((cv.v - 0.62) / 0.38, 0, 1) * tent
    cv.blend(tip, "#EEE4CC", 0.85)
    cv.multiply(0.92 + 0.12 * P.fbm(128, 128, 16, 3, 5))
    path = os.path.join(C.BUILD, "aiptasia.png")
    os.makedirs(C.BUILD, exist_ok=True)
    cv.save(path)
    return C.load_image(path, name="aiptasia")


def build_aiptasia():
    C.reset_scene()
    rs = RS(31)
    g = T.WGeo()
    # column: from just inside the rock (y -0.25) up to the oral disc, a slight waist and a flared collar
    ys = np.linspace(-0.25, COLUMN_H, 9)
    rad = 0.42 - 0.08 * np.sin(np.clip(ys / COLUMN_H, 0, 1) * math.pi) + 0.1 * S(0.8, 1.0, ys / COLUMN_H)
    pts = np.stack([np.zeros_like(ys), ys, np.zeros_like(ys)], -1)
    V, F, tv, av = G.tube(pts, rad, 10, cap_start=True, cap_end=False, up=(1, 0, 0))
    UV = np.c_[av * 0.5, tv]
    COL = np.stack([np.full(len(V), 0.8), np.zeros(len(V)), 0.15 * tv, np.ones(len(V))], -1)
    g.add(V, F, UV, COL)
    # oral disc: a shallow cone up to the hypostome with the mouth
    top = COLUMN_H
    ring = 14
    V = [(0, top + 0.12, 0)] + [(DISC_R * math.cos(2 * math.pi * k / ring), top, DISC_R * math.sin(2 * math.pi * k / ring)) for k in range(ring)]
    F = [(0, (k + 1) % ring + 1, k + 1) for k in range(ring)]
    UV = [(0.02, 0.98)] + [(0.1 + 0.3 * k / ring, 0.92) for k in range(ring)]
    g.add(V, F, UV, (0.8, 0.0, 0.2, 1))
    # tentacles: an inner whorl (shorter, more upright) and an outer whorl (long, arching out and over)
    for whorl, (n, r0, lo, hi, rise) in enumerate(((N_INNER, 0.28, 1.3, 1.7, 0.75), (N_OUTER, 0.5, 1.9, 2.5, 0.45))):
        for k in range(n):
            ang = 2 * math.pi * (k + 0.5 * whorl) / n + rs.uniform(-0.08, 0.08)
            d = np.array([math.cos(ang), 0, math.sin(ang)])
            L = rs.uniform(lo, hi)
            base = np.array([0, top + 0.02, 0]) + d * r0
            up = rise + rs.uniform(-0.1, 0.1)
            keys = [base,
                    base + d * L * 0.25 + np.array([0, L * 0.3 * up + 0.15, 0]),
                    base + d * L * 0.55 + np.array([0, L * 0.38 * up, 0]),
                    base + d * L * 0.85 + np.array([0, L * (0.3 * up - 0.12), 0])]
            P_ = smooth_path(keys, 6)
            Vt, Ft, tvt, avt = G.tube(P_, np.linspace(0.055, 0.012, 6), 4, cap_end=True, up=(0, 1, 0))
            UVt = np.c_[0.5 + avt * 0.5, tvt]
            ph = rs.uniform(0, 1)
            COLt = np.stack([np.full(len(Vt), 0.3), np.full(len(Vt), ph), 0.2 + 0.8 * tvt, np.ones(len(Vt))], -1)
            g.add(Vt, Ft, UVt, COLt)
    m = C.material("aiptasia", rough=0.55, image=aiptasia_texture())
    root = C.empty("Root")
    C.set_extras(root, species="aiptasia", units="cm", polyp=True, columnCm=COLUMN_H,
                 spec="tools/blender/pests.py; one polyp, pedal disc at the origin")
    body = g.obj("Body", root, "body", [m], smooth=True)
    C.recalc_normals(body)
    C.triangulate(body)
    path = os.path.join(C.MODELS, "aiptasia.glb")
    C.export_glb(path, C.all_descendants(root))
    print(f"[pests] aiptasia.glb: {C.tri_count(body)} tris")


# ----------------------------------------------------------------------------------------------------------------
# Berghia stephanieae
# ----------------------------------------------------------------------------------------------------------------
def build_berghia():
    """Aeolid, ~2 cm: slender translucent white foot tapering to a pointed tail, two long oral tentacles and two
    rhinophores up front, ~44 cerata in rows down the back (longest mid-body), each a translucent sleeve around a
    brown digestive-gland core with an opaque white cnidosac tip."""
    C.reset_scene()
    at = Atlas(256, 128, 2)

    def body(cv):
        cv.blend(np.ones_like(cv.u), "#E8E2D6")
        cv.blend(cv.u, "#D6CCBC")
        cv.multiply(0.94 + 0.1 * P.fbm(cv.h, cv.w, 20, 3, 9))
        # a faint pale-blue sheen along the back
        cv.blend(np.abs(cv.v - 0.5) < 0.15, "#DDE6EA", 0.35)

    def ceras(cv):
        # base -> tip along u: translucent pale sleeve, brown core, white cnidosac tip with a blue-white glint
        cv.blend(np.ones_like(cv.u), "#B88A5A")
        cv.blend(cv.u, "#8A5A30")
        cv.blend(np.abs(cv.v - 0.5) > 0.32, "#E4DCCC", 0.6)
        cv.blend(cv.u > 0.78, "#F6F4EE", 1.0)
        cv.blend((cv.u > 0.72) & (cv.u < 0.8), "#CFE0EA", 0.6)
    at.paint_strip(0, body)
    at.paint_strip(1, ceras)
    img = save_atlas(at, "berghia")
    mat = C.material("berghia_mat", rough=0.35, image=img)
    root = make_root("Root", dict(species="berghia", lengthCm=2.0, iridescence=0.15, roughness=0.35,
                                  unit="body length 1.0, facing +X, Z up"))
    rs = RS(5)
    # foot / body: x 0.5 head .. -0.5 tail, low and flat on the surface
    keys = [(0.5, 0, 0.05), (0.38, 0, 0.07), (0.1, 0, 0.075), (-0.2, 0, 0.065), (-0.42, 0, 0.035), (-0.5, 0, 0.02)]
    Pb = smooth_path(keys, 14)
    t = np.linspace(0, 1, 14)
    W = 0.075 * (1 - 0.35 * t) * np.sqrt(np.clip(1 - ((t - 0.35) / 0.68) ** 2, 0.08, 1)) + 0.012
    H = 0.06 * (1 - 0.5 * t) + 0.01
    Vb, Fb, tvb, avb, *_ = ell_tube(Pb, W, H, sides=8)
    gb = G.Geo()
    gb.add(Vb, Fb, at.uv(0, tvb, avb), col_u(Vb))
    objs = [geo_obj("Body", gb, root, "body", [mat])]
    # cerata: pairs of rows down both sides of the back from behind the rhinophores to near the tail
    gc = G.Geo()
    n = 22
    for i in range(n):
        x = 0.3 - 0.72 * i / (n - 1)
        u = 0.5 - x
        k = math.sin(math.pi * (i + 1) / (n + 1))
        z_back = np.interp(x, Pb[::-1, 0], Pb[::-1, 2]) + 0.04
        for side in (1, -1):
            for row in (0, 1):
                if row == 1 and i % 2:
                    continue
                y0 = side * (0.025 + 0.035 * row)
                L = (0.1 + 0.09 * k) * (0.85 if row else 1.0) * rs.uniform(0.9, 1.1)
                tilt = np.array([-0.45 + rs.uniform(-0.1, 0.1), side * (0.55 + 0.25 * row), 0.75])
                tilt /= np.linalg.norm(tilt)
                b = np.array([x, y0, z_back])
                pts = smooth_path([b, b + tilt * L * 0.5 + np.array([0, 0, 0.01]), b + tilt * L], 4)
                Vc, Fc, tvc, avc = G.tube(pts, np.array([0.016, 0.017, 0.012, 0.004]) * (0.8 + 0.4 * k), 4, cap_end=True)
                gc.add(Vc, Fc, at.uv(1, tvc, avc), np.stack([np.zeros(len(Vc)), np.zeros(len(Vc)), 0.0 * tvc, np.full(len(Vc), u)], -1))
    objs.append(geo_obj("Cerata", gc, root, "body", [mat]))
    # oral tentacles (long, swept sideways) and rhinophores (upright)
    ga = G.Geo()
    for side in (1, -1):
        b0 = np.array([0.47, side * 0.03, 0.05])
        appendage(ga, smooth_path([b0, b0 + [0.08, side * 0.07, 0.0], b0 + [0.14, side * 0.16, -0.02]], 5), 0.012, 0.004,
                  lambda tv_, av_: at.uv(0, tv_ * 0.2, av_), 0.0, sides=4)
        b1 = np.array([0.4, side * 0.025, 0.1])
        appendage(ga, smooth_path([b1, b1 + [0.03, side * 0.02, 0.07], b1 + [0.05, side * 0.03, 0.13]], 4), 0.011, 0.005,
                  lambda tv_, av_: at.uv(1, 0.3 + tv_ * 0.4, av_), 0.06, sides=4)
    objs.append(geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 4}))
    normalize_x(objs, -0.5, 0.64)
    for o in objs[:2]:
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "berghia.glb"), 2400)


# ----------------------------------------------------------------------------------------------------------------
# treatment props
# ----------------------------------------------------------------------------------------------------------------
def lathe(g, prof, n=16, y0=0.0):
    """Closed surface of revolution about +Y (r, y) profile; r 0 ends close as poles."""
    rows, V = [], []
    for r, y in prof:
        if r <= 1e-6:
            rows.append([len(V)]); V.append((0, y0 + y, 0))
        else:
            row = []
            for k in range(n):
                a = 2 * math.pi * k / n
                row.append(len(V)); V.append((r * math.cos(a), y0 + y, r * math.sin(a)))
            rows.append(row)
    F = []
    for a, b in zip(rows, rows[1:]):
        if len(a) == 1 and len(b) == 1:
            continue
        if len(a) == 1:
            F += [(a[0], b[(k + 1) % n], b[k]) for k in range(n)]
        elif len(b) == 1:
            F += [(a[k], a[(k + 1) % n], b[0]) for k in range(n)]
        else:
            F += [(a[k], a[(k + 1) % n], b[(k + 1) % n], b[k]) for k in range(n)]
    g.add(V, F)


def build_tools():
    C.reset_scene()
    root = C.empty("Root")
    C.set_extras(root, units="cm", spec="tools/blender/pests.py treatment props; tip at origin, body along +Y")
    clear = C.material("syringe_clear", "#E8F2F6", rough=0.1, alpha=0.4, blend=True, double=True)
    white = C.material("syringe_white", "#F4F4F2", rough=0.4)
    tip_m = C.material("applicator_tip", "#F0EEE8", rough=0.35)
    fill = C.material("syringe_fill", "#FFFFFF", rough=0.2)
    rubber = C.material("plunger_rubber", "#2A2A2C", rough=0.6)
    alu = C.material("laser_body", "#202326", rough=0.3, metal=0.7)
    button = C.material("laser_button", "#3CD070", rough=0.3, emissive="#3CD070", emissive_strength=0.6)
    lens = C.material("laser_lens", "#9FE8FF", rough=0.05, emissive="#9FE8FF", emissive_strength=0.4)
    beam = C.material("laser_beam", "#FFFFFF", rough=0.0, alpha=0.8, blend=True, emissive="#FFFFFF", emissive_strength=2.0)
    objs = []

    def obj(g, name, mats):
        o = g.obj(name, root, "care", mats, smooth=True)
        objs.append(o)
        return o

    # syringe: a 3 cm bent-free applicator tube (Aiptasia-X lock-on tip), a 6 cm clear barrel with finger flange
    g = T.WGeo(); lathe(g, [(0, 0), (0.09, 0.0), (0.12, 2.6), (0.32, 3.0), (0, 3.0)], n=8); obj(g, "SyringeTip", [tip_m])
    g = T.WGeo(); lathe(g, [(0, 3.0), (0.58, 3.0), (0.6, 3.2), (0.6, 9.0), (0, 9.0)], n=16); obj(g, "Syringe", [clear])
    g = T.WGeo()
    lathe(g, [(0, 9.0), (0.62, 9.0), (0.62, 9.2), (0, 9.2)], n=16)
    for s in (1, -1):
        V, F = T.box((-0.4, 9.0, -0.15) if s > 0 else (-0.4, 9.0, -0.15), (0.4, 9.2, 0.15))
        V = V + np.array([s * 1.0, 0, 0]); V[:, 0] *= 1.0
        g.add(V, F)
    obj(g, "SyringeFlange", [white])
    # plunger: rubber seal at its base (y 0), rod up to the thumb pad; the runtime slides it down the barrel
    g = T.WGeo()
    lathe(g, [(0, 0), (0.55, 0), (0.55, 0.45), (0.2, 0.5), (0.2, 7.0), (0.75, 7.0), (0.75, 7.25), (0, 7.25)], n=12)
    obj(g, "SyringePlunger", [rubber])
    # fill: a unit-tall plug the runtime scales to the dose left and tints per product
    g = T.WGeo(); lathe(g, [(0, 0), (0.54, 0), (0.54, 1.0), (0, 1.0)], n=14); obj(g, "SyringeFill", [fill])
    # laser: a 13 cm pen, lens at the tip, a glowing button
    g = T.WGeo()
    lathe(g, [(0, 0.0), (0.3, 0.0), (0.45, 0.6), (0.62, 1.2), (0.62, 12.6), (0.5, 13.0), (0, 13.0)], n=16)
    obj(g, "Laser", [alu])
    g = T.WGeo(); V, F = T.box((-0.18, 7.0, 0.5), (0.18, 7.8, 0.7)); g.add(V, F); obj(g, "LaserButton", [button])
    g = T.WGeo(); lathe(g, [(0, -0.02), (0.22, -0.02), (0.22, 0.02), (0, 0.02)], n=10); obj(g, "LaserLens", [lens])
    # beam: unit length along -Y from the lens
    g = T.WGeo(); lathe(g, [(0, 0), (0.05, 0), (0.05, -1.0), (0, -1.0)], n=6); obj(g, "LaserBeam", [beam])
    for o in objs:
        C.recalc_normals(o)
        C.triangulate(o)
    path = os.path.join(C.MODELS, "pest_tools.glb")
    C.export_glb(path, C.all_descendants(root))
    print(f"[pests] pest_tools.glb: {sum(C.tri_count(o) for o in objs)} tris")


BUILDERS = {"aiptasia": build_aiptasia, "berghia": build_berghia, "pest_tools": build_tools}

if __name__ == "__main__":
    for k in sys.argv[1:] or list(BUILDERS):
        BUILDERS[k]()
