"""Clownfish eggs, larvae and fry (core sim/Brood.kt, docs/research/28_clownfish_breeding.md), built like the
microfauna (unit length facing +X, Blender Z up, spline-skinned by fish.vert):

  brood_clown_eggs_fresh   a clutch on the rock, laid: ~170 capsule eggs (2.5 x 1 mm, research 28 §1) glued by one
  brood_clown_eggs_dark    pole, packed and leaning one way; bright orange yolk on days 1-2, turning dark as the
  brood_clown_eggs_eyed    embryo pigments (days 3-5), then large silver eyes show through the capsule before hatching
  brood_clown_larva        newly hatched larva (~4.5 mm): see-through body and continuous fin fold, big dark eyes with a
                           silver sheen, yolk/gut ball behind the head, a row of melanophores along the belly
  brood_clown_postlarva    metamorphosing (~9 mm): body filling in orange-brown, first white head bar, fins forming
  brood_clown_fry          fry (~1.2-2.5 cm): orange with the head and mid bars, a faint tail bar, black-edged fins,
                           big eyes and a rounder head than the adult

Parts: "body" opaque, "fin" see-through (drawn blended), "eye". COLOR_0.a = spine u (0 snout .. 1 tail), .b = hinge.
"""
import math
import os

import numpy as np

import common as C
import geomlib as G
import paintlib as P
from inverts import Atlas, make_root, finish, geo_obj
from microfauna import ell_tube, col_u, normalize_x, save_atlas

S = P.smoothstep

# per-model triangle budgets (tools/validate_glb.py BUDGET, brood_*)
BUDGETS = {"brood_clown_eggs": 10000, "brood_clown_larva": 1000, "brood_clown_postlarva": 1500, "brood_clown_fry": 1800}


# ----------------------------------------------------------------------------
# egg clutch
# ----------------------------------------------------------------------------
EGG_STAGES = {
    # capsule shell, yolk / embryo inside, eyes (None = not yet visible)
    "fresh": ("#F7B98A", "#F0671C", None),
    "dark": ("#C9A27F", "#4A2A1A", None),
    "eyed": ("#B9B2A8", "#3A2C24", "#D9DEE2"),
}


def build_eggs(stage):
    """A flat clutch about 3.5 cm long (unit length 1 along X, 0.7 wide), eggs standing on their attachment pole and
    leaning toward -X, densely packed (Poisson disc). Each egg: 6 x 4 capsule, painted from a small atlas: the
    see-through capsule end, the yolk, and on 'eyed' the paired silver eyes near the free end."""
    C.reset_scene()
    shell, yolk, eye = EGG_STAGES[stage]
    at = Atlas(64, 32, 1)

    def egg(cv):  # u: 0 attached pole .. 1 free end, v around
        cv.blend(np.ones_like(cv.u), yolk)
        cv.blend(S(0.78, 0.95, cv.u), shell, 0.85)  # clear tip of the capsule
        cv.blend(S(0.0, 0.08, 0.08 - cv.u), shell, 0.8)
        if eye is not None:
            for vc in (0.25, 0.75):
                d = np.sqrt(((cv.u - 0.66) / 0.1) ** 2 + ((cv.v - vc) / 0.12) ** 2)
                cv.blend(d < 1.0, "#101010")
                cv.blend((d < 0.55), eye, 0.9)
        if stage == "dark":
            cv.blend(P.fbm(cv.h, cv.w, 6, 2, 3) > 0.55, "#2A1810", 0.6)
    at.paint_strip(0, egg)
    img = save_atlas(at, f"brood_clown_eggs_{stage}")
    mat = C.material(f"eggs_{stage}_mat", rough=0.2, image=img)
    root = make_root("Root", dict(species=f"brood_clown_eggs_{stage}", lengthCm=3.0, iridescence=0.15, roughness=0.2,
                                  unit="clutch length 1.0 on the rock (Blender Z = rock normal), facing +X"))
    rs = np.random.default_rng(28)
    # hex-packed rows, eggs touching side by side and overlapping like shingles along the lean (research 28 §1)
    pts = []
    for j, y in enumerate(np.arange(-0.3, 0.3001, 0.036)):
        for x in np.arange(-0.48 + (j % 2) * 0.025, 0.48, 0.05):
            pts.append((x + rs.uniform(-0.006, 0.006), y + rs.uniform(-0.005, 0.005)))
    g = G.Geo()
    n = 0
    lean = G.rot_y(-0.95)  # every egg leans the same way (the male fans them flat)
    for x, y in pts:
        if (x / 0.47) ** 2 + (y / 0.3) ** 2 > 1.0:
            continue
        L = 0.075 * rs.uniform(0.92, 1.08)
        r = 0.019 * rs.uniform(0.95, 1.05)
        V, F = G.ellipsoid((0, 0, 0), (r, r, L / 2), segs=5, rings=3)
        u_along = (V[:, 2] / L + 0.5)  # 0 base .. 1 tip
        ang = (np.arctan2(V[:, 1], V[:, 0]) / (2 * math.pi)) % 1.0
        V = V + np.array([0, 0, L / 2])
        V = V @ (lean @ G.rot_z(rs.uniform(-0.2, 0.2))).T
        V = V + np.array([x, y, 0.004])
        g.add(V, F, at.uv(0, u_along, ang), col_u(V))
        n += 1
    o = geo_obj("Body", g, root, "body", [mat])
    C.recalc_normals(o)
    print(f"[brood] eggs_{stage}: {n} eggs")
    return finish(root, [o], os.path.join(C.MODELS, f"brood_clown_eggs_{stage}.glb"), BUDGETS["brood_clown_eggs"])


# ----------------------------------------------------------------------------
# fish-shaped young: shared body / fin / eye builders
# ----------------------------------------------------------------------------
def body_tube(at, strip, n, depth_keys, width_keys, sides=12, x0=0.5, x1=-0.5):
    xs = np.linspace(x0, x1, n)
    t = np.linspace(0, 1, n)
    H = np.interp(t, *zip(*depth_keys))
    W = np.interp(t, *zip(*width_keys))
    Pts = np.stack([xs, 0 * xs, 0 * xs], -1)
    V, F, tv, av, T, N, B = ell_tube(Pts, W, H, sides)
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2
    return V, F, at.uv(strip, tv, h)


def fin_sheet(g, at, strip, base, tip_fn, n=14, x_from=0.4, x_to=-0.45, hinge_scale=1.0, u_fn=None):
    """A median fin: a strip between base(x) on the body outline and tip_fn(x) further out (both (y, z) in the
    sagittal plane, y = 0). COLOR_0.b = hinge 0 at the base .. 1 at the edge."""
    xs = np.linspace(x_from, x_to, n)
    V, UV, COL, F = [], [], [], []
    for i, x in enumerate(xs):
        zb = base(x)
        zt = tip_fn(x)
        u = 0.5 - x if u_fn is None else u_fn(x)
        for k, (z, hh) in enumerate(((zb, 0.0), (zt, 1.0))):
            V.append((x, 0.0, z))
            UV.append(at.uv(strip, np.array([i / (n - 1)]), np.array([hh]))[0])
            COL.append((0, 0, hh * hinge_scale, float(np.clip(u, 0, 1))))
    for i in range(n - 1):
        a = i * 2
        F.append((a, a + 2, a + 3, a + 1))
    g.add(np.array(V), F, np.array(UV), np.array(COL))


def caudal(g, at, strip, x_base, half_h, length, fork=0.0, n=9):
    """Caudal fin fan: base on the peduncle, outer edge an arc (fork < 0 rounded, > 0 forked)."""
    V = [(x_base, 0, half_h * 0.7), (x_base, 0, -half_h * 0.7)]
    for k in range(n):
        a = -1 + 2 * k / (n - 1)
        reach = length * (1 + fork * (abs(a) - 0.5))
        V.append((x_base - reach * math.cos(0.5 * a), 0, half_h * (0.7 + 0.9 * a) if a > 0 else half_h * (-0.7 + 0.9 * a) if a < 0 else 0.0))
    V = np.array(V, float)
    V[2:, 2] = np.linspace(-half_h * 1.6, half_h * 1.6, n)
    hinge = np.r_[0, 0, np.ones(n)]
    UV = at.uv(strip, hinge, np.clip(0.5 + V[:, 2] / (half_h * 3.4), 0, 1))
    COL = np.stack([np.zeros(n + 2), np.zeros(n + 2), hinge, np.clip(0.5 - V[:, 0], 0, 1)], -1)
    F = [(1, 2 + k + 1, 2 + k) for k in range(n // 2)] + [(0, 1, 2 + n // 2)] + [(0, 2 + k + 1, 2 + k) for k in range(n // 2, n - 1)]
    g.add(V, F, UV, COL)


def pectorals(g, at, strip, x, z, span, chord):
    for side in (1, -1):
        b = np.array([x, side * 0.05, z])
        V = [b, b + [0, 0, chord * 0.5], b + [-chord, side * span, chord * 0.2], b + [-chord * 1.1, side * span * 0.8, -chord * 0.3]]
        UV = at.uv(strip, np.array([0, 0, 1, 1.0]), np.array([0.5, 1, 1, 0.0]))
        u = 0.5 - x
        COL = [(0, 0, 0, u), (0, 0, 0, u), (0, 0, 1, u), (0, 0, 1, u)]
        g.add(np.array(V, float), [(0, 1, 2), (0, 2, 3)], UV, COL)


def eyes(root, x, z, r, iris, segs=10, pupil=0.2, y=None):
    """Paired eyes as part 'eye' (pivot at the eye centre): black pupil with a coloured iris ring."""
    cv = P.Canvas(32, 32, "#0A0A0C")
    rr = np.sqrt((cv.u - 0.5) ** 2 + (cv.v - 0.5) ** 2)
    cv.blend(S(pupil - 0.04, pupil + 0.02, rr), iris)
    cv.blend(S(0.42, 0.48, rr), "#20201E")
    tex = os.path.join(C.BUILD, f"eye_{iris[1:]}.png")
    cv.save(tex)
    mat = C.material(f"eye_{iris[1:]}_mat", rough=0.1, image=C.load_image(tex, name=f"eye_{iris[1:]}"))
    g = G.Geo()
    for side in (1, -1):
        yy = r * 0.75 if y is None else y
        V, F = G.ellipsoid((x, side * yy, z), (r, r * 0.55, r), segs=segs, rings=6)
        d = V - np.array([x, side * yy, z])
        UV = np.stack([0.5 + 0.5 * d[:, 0] / r, 0.5 + 0.5 * d[:, 2] / r], -1)
        g.add(V, F, UV, col_u(V, u=0.5 - x))
    return geo_obj("Eyes", g, root, "eye", [mat], extras={"pivot": [x, 0.0, z]})


# ----------------------------------------------------------------------------
# newly hatched larva
# ----------------------------------------------------------------------------
def build_larva():
    """Hatchling ~4.5 mm (Raheem 2021 5.0 mm, Yasir & Qin 3.6 mm): elongate, almost transparent, a continuous
    median fin fold from behind the head round the tail to the yolk, eyes about a sixth of body length, an orange
    yolk / gut ball under the front third, a dotted line of black melanophores along the belly and gut."""
    C.reset_scene()
    at = Atlas(128, 64, 2)

    def flesh(cv):  # see-through body: faint pinkish tissue, notochord line, melanophores (alpha carries them)
        cv.blend(np.ones_like(cv.u), "#E8D8C8")
        cv.a[:] = 0.22
        noto = np.exp(-((cv.v - 0.55) / 0.04) ** 2) * S(0.12, 0.2, cv.u)
        cv.blend(noto, "#F2E6D8", 0.5); cv.a += 0.15 * noto
        rs = np.random.default_rng(5)
        for k in range(16):  # ventral melanophore row
            uc = 0.18 + k * 0.045 + rs.uniform(-0.01, 0.01)
            d = np.sqrt(((cv.u - uc) / 0.012) ** 2 + ((cv.v - 0.06) / 0.05) ** 2)
            cv.blend(d < 1, "#1A1410"); cv.a = np.maximum(cv.a, (d < 1) * 0.95)
        for uc, vc in ((0.08, 0.95), (0.13, 0.9)):  # head spots
            d = np.sqrt(((cv.u - uc) / 0.015) ** 2 + ((cv.v - vc) / 0.06) ** 2)
            cv.blend(d < 1, "#1A1410"); cv.a = np.maximum(cv.a, (d < 1) * 0.9)
    at.paint_strip(0, flesh)

    def fold(cv):  # fin fold: clear membrane, slightly milky at the edge
        cv.blend(np.ones_like(cv.u), "#EEF2F2")
        cv.a[:] = 0.12 + 0.12 * cv.v
    at.paint_strip(1, fold)
    tex = os.path.join(C.BUILD, "brood_clown_larva.png")
    os.makedirs(C.BUILD, exist_ok=True)
    at.cv.save(tex, alpha=True)
    img = C.load_image(tex, name="brood_clown_larva")
    clear = C.material("larva_clear", rough=0.15, image=img, image_alpha=True, double=True)
    root = make_root("Root", dict(species="brood_clown_larva", lengthCm=0.45, iridescence=0.2, roughness=0.15,
                                  unit="body length 1.0 snout..tail fold, facing +X"))
    gb = G.Geo()
    V, F, UV = body_tube(at, 0, 26, [(0, 0.05), (0.08, 0.085), (0.2, 0.08), (0.45, 0.05), (0.8, 0.025), (1.0, 0.01)],
                         [(0, 0.045), (0.1, 0.07), (0.25, 0.055), (0.5, 0.03), (0.8, 0.015), (1.0, 0.006)], sides=10)
    gb.add(V, F, UV, col_u(V))
    # continuous fin fold: dorsal from 30 % back, round the tail, ventral up to the vent
    fin_sheet(gb, at, 1, lambda x: 0.03 + 0.03 * (x + 0.5), lambda x: 0.03 + 0.03 * (x + 0.5) + 0.06 * np.sin(np.pi * np.clip((0.2 - x) / 0.7, 0, 1)) + 0.02,
              x_from=0.2, x_to=-0.5)
    fin_sheet(gb, at, 1, lambda x: -0.03 - 0.03 * (x + 0.5), lambda x: -0.03 - 0.03 * (x + 0.5) - 0.06 * np.sin(np.pi * np.clip((0.05 - x) / 0.55, 0, 1)) - 0.015,
              x_from=0.05, x_to=-0.5)
    fold_o = geo_obj("Fold", gb, root, "fin", [clear])
    # yolk / gut ball (opaque, orange): the larva's food for its first day
    yolk_mat = C.material("larva_yolk", color="#E8873A", rough=0.35)
    gy = G.Geo()
    V, F = G.ellipsoid((0.3, 0, -0.06), (0.065, 0.045, 0.05), segs=8, rings=5)
    gy.add(V, F, None, col_u(V))
    V, F = G.ellipsoid((0.19, 0, -0.055), (0.06, 0.028, 0.03), segs=8, rings=4)  # coiled gut, darker
    gy.add(V, F, None, col_u(V), mat=1)
    gut_mat = C.material("larva_gut", color="#8A5A3A", rough=0.4)
    yolk_o = geo_obj("Yolk", gy, root, "body", [yolk_mat, gut_mat])
    eye_o = eyes(root, 0.4, 0.02, 0.062, "#6E6A58", pupil=0.3)
    objs = [fold_o, yolk_o, eye_o]
    for o in objs:
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "brood_clown_larva.glb"), BUDGETS["brood_clown_larva"])


# ----------------------------------------------------------------------------
# post-larva / fry
# ----------------------------------------------------------------------------
def paint_clown(cv, bars, base="#E8701E", belly="#F3A060", darken=0.0):
    """Clown flank: orange with a lighter belly; white bars at (u_centre, half_width, strength) with thin black edges."""
    cv.vgrad(belly, base, 0.0, 0.6)
    if darken > 0:
        cv.blend(np.ones_like(cv.u), "#5A3A28", darken)
    for uc, hw, k in bars:
        # bars bow forward a little at mid-flank like the adult's
        uu = cv.u - 0.03 * np.sin(np.pi * cv.v)
        m = P.band(uu, uc - hw, uc + hw, 0.006)
        edge = P.band(uu, uc - hw - 0.012, uc + hw + 0.012, 0.004) - m
        cv.blend(edge, "#141010", 0.8 * k)
        cv.blend(m, "#FAFAF5", k)


# clown body half-depth along the tube (t 0 snout .. 1 caudal base): blunt head, deep oval body, short peduncle
DEPTH = [(0, 0.06), (0.04, 0.11), (0.12, 0.16), (0.3, 0.195), (0.55, 0.185), (0.75, 0.12), (0.88, 0.085), (1.0, 0.078)]
TAIL_X = -0.28


def build_young(name, length_cm, bars, darken, fin_alpha, dorsal_split, budget):
    C.reset_scene()
    at = Atlas(256, 128, 2)
    at.paint_strip(0, lambda cv: paint_clown(cv, bars, darken=darken))

    def fins(cv):
        cv.blend(np.ones_like(cv.u), "#EC8A3A")
        edge = S(0.75, 0.92, cv.v)
        cv.blend(edge, "#151212", 0.85 * (1 - darken))  # black margin comes in with metamorphosis
        cv.blend(S(0.92, 1.0, cv.v), "#FFFFFF", 0.4)
        cv.a[:] = fin_alpha + (1 - fin_alpha) * 0.6 * cv.v
    at.paint_strip(1, fins)
    tex = os.path.join(C.BUILD, f"{name}.png")
    os.makedirs(C.BUILD, exist_ok=True)
    at.cv.save(tex, alpha=True)
    img = C.load_image(tex, name=name)
    body_mat = C.material(f"{name}_body", rough=0.35, image=img)
    fin_mat = C.material(f"{name}_fin", rough=0.3, image=img, image_alpha=True, double=True)
    root = make_root("Root", dict(species=name, lengthCm=length_cm, iridescence=0.15, roughness=0.35,
                                  unit="body length 1.0 snout..caudal tip, facing +X"))
    # deep, rounded head and a big eye (juveniles), caudal peduncle; tube to x = -0.32, caudal fin beyond
    gb = G.Geo()
    V, F, UV = body_tube(at, 0, 28, DEPTH, [(0, 0.04), (0.15, 0.085), (0.4, 0.085), (0.75, 0.05), (1.0, 0.03)],
                         sides=12, x0=0.5, x1=TAIL_X)
    # spine u for the tube runs over 0..0.82 of the length (the caudal fin is the rest)
    gb.add(V, F, UV, col_u(V))
    body_o = geo_obj("Body", gb, root, "body", [body_mat])
    gf = G.Geo()
    def half_depth(x):
        t = (0.5 - x) / (0.5 - TAIL_X)
        return np.interp(t, *zip(*DEPTH)) * 0.96   # a hair inside the outline so the base is hidden
    top = lambda x: half_depth(x)
    bot = lambda x: -half_depth(x)
    # dorsal: lower spiny part in front with a notch, taller soft part behind (Fautin & Allen 1992: X-XI spines)
    def dorsal_h(x):
        spiny = 0.055 * S(0.24, 0.18, x) * (1.0 - 0.45 * S(dorsal_split + 0.06, dorsal_split, x))
        soft = np.interp(x, [TAIL_X + 0.01, -0.2, -0.1, dorsal_split - 0.02, dorsal_split + 0.02], [0.03, 0.1, 0.11, 0.07, 0.0])
        return np.maximum(spiny, soft) + 0.004
    fin_sheet(gf, at, 1, top, lambda x: top(x) + dorsal_h(x), n=20, x_from=0.24, x_to=TAIL_X + 0.01)
    fin_sheet(gf, at, 1, bot, lambda x: bot(x) - np.interp(x, [TAIL_X + 0.01, -0.2, -0.12, -0.02], [0.03, 0.09, 0.095, 0.0]) - 0.004,
              x_from=-0.02, x_to=TAIL_X + 0.01)
    caudal(gf, at, 1, TAIL_X + 0.01, 0.075, 0.22, fork=-0.12)   # rounded tail (negative fork = convex)
    pectorals(gf, at, 1, 0.22, -0.02, 0.1, 0.09)
    fin_o = geo_obj("Fins", gf, root, "fin", [fin_mat])
    eye_o = eyes(root, 0.36, 0.045, 0.052, "#E09040" if darken < 0.3 else "#8C9496", y=0.068)
    objs = [body_o, fin_o, eye_o]
    normalize_x(objs, -0.5, 0.5)
    for o in objs:
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, f"{name}.glb"), budget)


def build_postlarva():
    """Metamorphosing (~9 mm, ~day 8-12): body turning opaque orange-brown, only the head bar (research 28 §3: head
    bar first), fins still clear, eyes still big and silvery."""
    return build_young("brood_clown_postlarva", 0.9, [(0.27, 0.035, 0.85)], darken=0.45, fin_alpha=0.15,
                       dorsal_split=0.05, budget=BUDGETS["brood_clown_postlarva"])


def build_fry():
    """Fry ~1.2-2.5 cm: orange, head and mid bars, a faint tail bar still forming, black fin margins starting."""
    return build_young("brood_clown_fry", 1.5, [(0.27, 0.04, 1.0), (0.53, 0.045, 0.95), (0.87, 0.02, 0.5)], darken=0.05,
                       fin_alpha=0.35, dorsal_split=0.05, budget=BUDGETS["brood_clown_fry"])


BROOD_BUILDERS = {
    "brood_clown_eggs_fresh": lambda: build_eggs("fresh"),
    "brood_clown_eggs_dark": lambda: build_eggs("dark"),
    "brood_clown_eggs_eyed": lambda: build_eggs("eyed"),
    "brood_clown_larva": build_larva,
    "brood_clown_postlarva": build_postlarva,
    "brood_clown_fry": build_fry,
}

if __name__ == "__main__":
    import sys
    only = sys.argv[1:] or list(BROOD_BUILDERS)
    for k in only:
        BROOD_BUILDERS[k]()
