"""Blue tang eggs, larvae and young (core sim/Brood.kt BroodSpecs.TANG, docs/research/41_blue_tang_breeding.md), built
like the clownfish young in brood.py (unit length facing +X, Blender Z up, spline-skinned by fish.vert):

  brood_tang_egg        a floating pelagic egg (~0.8 mm, research 41 [S]): a clear sphere with one golden oil droplet
                        and the pale curled embryo
  brood_tang_larva      first weeks (~2-10 mm): see-through, a big head with huge dark eyes, a silvery gut ball and a
                        continuous fin fold, a few melanophores
  brood_tang_postlarva  the acronurus (~1-2.5 cm, from day ~30): a tall, flat, see-through silver disc with a bright
                        silver belly, very long second dorsal and pelvic spines (acanthurid larvae, research 41 §3),
                        big eyes and a small pointed snout
  brood_tang_fry        a little blue tang (~2.5-5 cm, after the first blue at day ~50): royal blue with the black
                        "palette" mark, yellow tail, rounder head and bigger eye than the adult

Parts: "body" opaque, "fin" see-through (drawn blended), "eye". COLOR_0.a = spine u (0 snout .. 1 tail), .b = hinge.
"""
import math
import os

import numpy as np

import common as C
import geomlib as G
import paintlib as P
from inverts import Atlas, make_root, finish, geo_obj
from microfauna import col_u, normalize_x, save_atlas
from brood import body_tube, fin_sheet, caudal, pectorals, eyes

S = P.smoothstep

# per-model triangle budgets (tools/validate_glb.py BUDGET, brood_*)
BUDGETS = {"brood_tang_egg": 600, "brood_tang_larva": 1000, "brood_tang_postlarva": 1500, "brood_tang_fry": 1800}


def build_egg():
    """One pelagic egg: clear shell (part fin, blended), a golden oil droplet that floats it, and the embryo."""
    C.reset_scene()
    shell = C.material("tang_egg_shell", color="#E8F2F4", rough=0.05, alpha=0.25, double=True)
    oil = C.material("tang_egg_oil", color="#E8B840", rough=0.15)
    emb = C.material("tang_egg_embryo", color="#E9DCCB", rough=0.4)
    root = make_root("Root", dict(species="brood_tang_egg", lengthCm=0.08, iridescence=0.3, roughness=0.05,
                                  unit="egg diameter 1.0, centred on the origin"))
    gs = G.Geo()
    V, F = G.ellipsoid((0, 0, 0), (0.5, 0.5, 0.5), segs=12, rings=8)
    gs.add(V, F, None, col_u(V, u=0.5))
    shell_o = geo_obj("Shell", gs, root, "fin", [shell])
    gb = G.Geo()
    V, F = G.ellipsoid((0.0, 0.0, 0.3), (0.12, 0.12, 0.12), segs=8, rings=5)   # the oil droplet rides at the top
    gb.add(V, F, None, col_u(V, u=0.5))
    # the embryo: a curled tube wrapped round the yolk
    pts = [(0.32 * math.cos(a), 0.32 * math.sin(a) * 0.4, -0.1 + 0.15 * math.sin(a)) for a in np.linspace(-2.2, 2.0, 7)]
    for i in range(len(pts) - 1):
        a, b = np.array(pts[i]), np.array(pts[i + 1])
        r = 0.075 * (1 - i / len(pts)) + 0.02
        V, F = G.ellipsoid(tuple((a + b) / 2), (max(r, np.linalg.norm(b - a) / 2), r, r), segs=6, rings=4)
        gb.add(V, F, None, col_u(V, u=0.5), mat=1)
    body_o = geo_obj("Body", gb, root, "body", [oil, emb])
    objs = [shell_o, body_o]
    for o in objs:
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "brood_tang_egg.glb"), BUDGETS["brood_tang_egg"])


def build_larva():
    """Pre-flexion larva: big head and eyes (acanthurid larvae are large-headed), a short see-through body tapering
    to the tail, a silvery gut ball, a continuous clear fin fold and a few melanophores along the gut."""
    C.reset_scene()
    at = Atlas(128, 64, 2)

    def flesh(cv):
        cv.blend(np.ones_like(cv.u), "#DCE4E6")
        cv.a[:] = 0.2
        noto = np.exp(-((cv.v - 0.55) / 0.04) ** 2) * S(0.2, 0.3, cv.u)
        cv.blend(noto, "#EEF2F2", 0.5); cv.a += 0.12 * noto
        rs = np.random.default_rng(41)
        for k in range(7):
            uc = 0.22 + k * 0.05 + rs.uniform(-0.01, 0.01)
            d = np.sqrt(((cv.u - uc) / 0.014) ** 2 + ((cv.v - 0.1) / 0.06) ** 2)
            cv.blend(d < 1, "#1A1612"); cv.a = np.maximum(cv.a, (d < 1) * 0.9)
    at.paint_strip(0, flesh)

    def fold(cv):
        cv.blend(np.ones_like(cv.u), "#EEF4F4")
        cv.a[:] = 0.1 + 0.12 * cv.v
    at.paint_strip(1, fold)
    img = save_atlas(at, "brood_tang_larva")
    clear = C.material("tang_larva_clear", rough=0.15, image=img, image_alpha=True, double=True)
    root = make_root("Root", dict(species="brood_tang_larva", lengthCm=0.3, iridescence=0.35, roughness=0.15,
                                  unit="body length 1.0 snout..tail fold, facing +X"))
    gb = G.Geo()
    V, F, UV = body_tube(at, 0, 24, [(0, 0.07), (0.1, 0.12), (0.28, 0.11), (0.5, 0.06), (0.8, 0.03), (1.0, 0.012)],
                         [(0, 0.06), (0.12, 0.085), (0.3, 0.06), (0.55, 0.03), (0.85, 0.014), (1.0, 0.006)], sides=10)
    gb.add(V, F, UV, col_u(V))
    fin_sheet(gb, at, 1, lambda x: 0.04 + 0.04 * (x + 0.5), lambda x: 0.04 + 0.04 * (x + 0.5) + 0.07 * np.sin(np.pi * np.clip((0.15 - x) / 0.65, 0, 1)) + 0.02,
              x_from=0.15, x_to=-0.5)
    fin_sheet(gb, at, 1, lambda x: -0.04 - 0.04 * (x + 0.5), lambda x: -0.04 - 0.04 * (x + 0.5) - 0.06 * np.sin(np.pi * np.clip((0.0 - x) / 0.5, 0, 1)) - 0.015,
              x_from=0.0, x_to=-0.5)
    fold_o = geo_obj("Fold", gb, root, "fin", [clear])
    gut = C.material("tang_larva_gut", color="#C9CFD2", rough=0.2)
    gg = G.Geo()
    V, F = G.ellipsoid((0.2, 0, -0.06), (0.09, 0.055, 0.065), segs=8, rings=5)   # silvery gut ball
    gg.add(V, F, None, col_u(V))
    gut_o = geo_obj("Gut", gg, root, "body", [gut])
    eye_o = eyes(root, 0.37, 0.03, 0.075, "#7A8C96", pupil=0.32)
    objs = [fold_o, gut_o, eye_o]
    for o in objs:
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "brood_tang_larva.glb"), BUDGETS["brood_tang_larva"])


# tang disc: half-depth along the tube (t 0 snout .. 1 caudal base): small pointed snout, very deep oval body, narrow peduncle
DISC = [(0, 0.05), (0.06, 0.13), (0.18, 0.26), (0.38, 0.31), (0.6, 0.27), (0.8, 0.14), (0.92, 0.07), (1.0, 0.06)]
TAIL_X = -0.3


def tang_body(name, length_cm, paint, fin_paint, fin_alpha, body_alpha, spines, eye_iris, budget, rough=0.3, tail_paint=None):
    C.reset_scene()
    at = Atlas(256, 128, 3)
    at.paint_strip(0, paint)

    def fins(cv, fp=fin_paint):
        fp(cv)
        cv.a[:] = fin_alpha + (1 - fin_alpha) * 0.5 * cv.v
    at.paint_strip(1, fins)
    at.paint_strip(2, lambda cv: fins(cv, tail_paint or fin_paint))
    img = save_atlas(at, name)
    body_mat = C.material(f"{name}_body", rough=rough, image=img, image_alpha=body_alpha < 1.0, double=body_alpha < 1.0)
    fin_mat = C.material(f"{name}_fin", rough=0.25, image=img, image_alpha=True, double=True)
    root = make_root("Root", dict(species=name, lengthCm=length_cm, iridescence=0.35 if body_alpha < 1 else 0.2, roughness=rough,
                                  unit="body length 1.0 snout..caudal tip, facing +X"))
    gb = G.Geo()
    V, F, UV = body_tube(at, 0, 28, DISC, [(0, 0.03), (0.15, 0.065), (0.4, 0.07), (0.75, 0.04), (1.0, 0.025)],
                         sides=12, x0=0.5, x1=TAIL_X)
    gb.add(V, F, UV, col_u(V))
    body_o = geo_obj("Body", gb, root, "fin" if body_alpha < 1 else "body", [body_mat])

    def half_depth(x):
        t = (0.5 - x) / (0.5 - TAIL_X)
        return np.interp(t, *zip(*DISC)) * 0.96
    gf = G.Geo()
    top = lambda x: half_depth(x)
    bot = lambda x: -half_depth(x)
    # long continuous dorsal and anal fins of a surgeonfish, highest toward the back
    fin_sheet(gf, at, 1, top, lambda x: top(x) + np.interp(x, [TAIL_X + 0.01, -0.15, 0.1, 0.3], [0.03, 0.09, 0.06, 0.0]) + 0.004,
              n=20, x_from=0.3, x_to=TAIL_X + 0.01)
    fin_sheet(gf, at, 1, bot, lambda x: bot(x) - np.interp(x, [TAIL_X + 0.01, -0.15, 0.05, 0.12], [0.03, 0.08, 0.05, 0.0]) - 0.004,
              n=16, x_from=0.12, x_to=TAIL_X + 0.01)
    caudal(gf, at, 2, TAIL_X + 0.01, 0.07, 0.2, fork=0.05)
    pectorals(gf, at, 1, 0.2, -0.04, 0.12, 0.1)
    if spines:
        # acronurus: a very long second dorsal spine and the pelvic spines, thin and stiff (part fin, hinge 0: no flex)
        for (x0, z0, dx, dz) in ((0.24, 0.29, -0.12, 0.42), (0.18, -0.27, -0.05, -0.36)):
            V = np.array([(x0, 0, z0), (x0 - 0.025, 0, z0), (x0 + dx, 0, z0 + dz)], float)
            UV = at.uv(1, np.array([0, 0, 1.0]), np.array([0.5, 0.5, 0.5]))
            COL = [(0, 0, 0, 0.5 - x0)] * 3
            gf.add(V, [(0, 1, 2)], UV, np.array(COL))
            V2 = V.copy(); V2[:, 1] += 0.004
            gf.add(V2, [(0, 2, 1)], UV, np.array(COL))
    fin_o = geo_obj("Fins", gf, root, "fin", [fin_mat])
    eye_o = eyes(root, 0.36, 0.05, 0.06, eye_iris, y=0.06)
    objs = [body_o, fin_o, eye_o]
    normalize_x(objs, -0.5, 0.5)
    for o in objs:
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, f"{name}.glb"), budget)


def build_postlarva():
    def paint(cv):
        # see-through silver disc, a mirror-bright belly sac and a hint of scales coming
        cv.blend(np.ones_like(cv.u), "#D6E0E6")
        cv.a[:] = 0.35
        belly = np.exp(-((cv.u - 0.35) / 0.16) ** 2) * S(0.55, 0.2, cv.v)
        cv.blend(belly, "#F4F8FA"); cv.a = np.maximum(cv.a, 0.9 * belly)
        cv.blend(S(0.0, 0.25, cv.u) * S(0.7, 0.95, cv.v), "#9FB2C2", 0.4)
    def fins(cv):
        cv.blend(np.ones_like(cv.u), "#E6EEF2")
    return tang_body("brood_tang_postlarva", 1.8, paint, fins, fin_alpha=0.15, body_alpha=0.5, spines=True,
                     eye_iris="#B8C4CC", budget=BUDGETS["brood_tang_postlarva"], rough=0.15)


def build_fry():
    def paint(cv):
        # royal blue, a black "palette" from the eye sweeping back along the upper flank with a blue window in it,
        # a black band under the dorsal fin base, yellow on the tail stalk (Paracanthurus juvenile)
        cv.vgrad("#2C62C8", "#1F4FB8", 0.0, 1.0)
        pal = P.band(cv.u, 0.22, 0.9, 0.02) * P.band(cv.v + 0.25 * (cv.u - 0.22), 0.55, 0.92, 0.03)
        cv.blend(pal, "#0B0E1A")
        win = np.exp(-(((cv.u - 0.5) / 0.14) ** 2 + ((cv.v - 0.7) / 0.06) ** 2))
        cv.blend(win * pal, "#2C62C8", 0.95)
        cv.blend(S(0.84, 0.92, cv.u) * P.band(cv.v, 0.3, 0.75, 0.05), "#F2C520")
    def fins(cv):
        cv.blend(np.ones_like(cv.u), "#123070")
        cv.blend(S(0.7, 0.95, cv.v), "#0A0D18", 0.8)
    def tail(cv):
        # yellow tail with black upper and lower edges (the adult's yellow caudal shows from the first blue)
        cv.blend(np.ones_like(cv.u), "#F2C520")
        edge = 1 - P.band(cv.v, 0.18, 0.82, 0.04)
        cv.blend(edge * S(0.3, 0.8, cv.u), "#0A0D18", 0.9)
    path = tang_body("brood_tang_fry", 3.5, paint, fins, fin_alpha=0.85, body_alpha=1.0, spines=False,
                     eye_iris="#3A3A40", budget=BUDGETS["brood_tang_fry"], tail_paint=tail)
    return path


BROOD_BUILDERS = {
    "brood_tang_egg": build_egg,
    "brood_tang_larva": build_larva,
    "brood_tang_postlarva": build_postlarva,
    "brood_tang_fry": build_fry,
}

if __name__ == "__main__":
    import sys
    only = sys.argv[1:] or list(BROOD_BUILDERS)
    for k in only:
        BROOD_BUILDERS[k]()
