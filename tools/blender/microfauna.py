"""In-tank microfauna (research 10, 16): the larger cryptofauna an aquarist actually sees in the display, built
like the mobile inverts (unit length facing +X, Blender Z up, spline-skinned by fish.vert):

  micro_bristleworm  Eurythoe-type amphinomid fireworm: flattened, ~40 visible chaetigers, white notochaetal fans,
                     red dorsal branchiae tufts, caruncle on the first segments, 5 short prostomial antennae
  micro_amphipod     gammarid (Elasmopus/Gammaropsis): laterally compressed, curved, coxal plates 1-4 deep, A1/A2,
                     two subchelate gnathopods, five pereopods (3-4 forward, 5-7 back), pleopods, 3 uropod pairs
  micro_mysid        mysid: carapace, stalked black eyes, antennal scales, feathery thoracic exopods, 6 abdominal
                     somites, tail fan with the two statocysts in the inner uropods
  micro_isopod       sphaeromatid: domed oval, head + 7 pereonites + pleon + big pleotelson, flat uropods
  micro_copepod      harpacticoid (Tigriopus): red-orange, prosome 5 + urosome 5 somites, caudal rami with setae,
                     short antennules, median red naupliar eye
  micro_snail        micro-snail (Collonista-type trochoid): globose spiral-banded shell, foot, 2 cephalic tentacles
  micro_flatworm     Convolutriloba retrogemma: flat rust-red leaf, trilobed posterior, pale statocyst
  micro_tentacle     terebellid (spaghetti worm) feeding tentacle: one ribbon with a ciliary groove; the worm's body
                     stays buried, the runtime lays several of these over the sand

COLOR_0: A = spine coordinate u of the vertex (0 snout .. 1 tail; appendages carry the u of their base),
B = hinge (0 base .. 1 tip of an appendage, drives the leg / antenna sweep), R/G = 0.
"""
import math
import os

import numpy as np

import common as C
import geomlib as G
import paintlib as P
from inverts import Atlas, bands, smooth_path, make_root, finish, geo_obj

S = P.smoothstep


def ell_tube(Pts, W, H, sides=10, up=(0, 0, 1), cap_start=True, cap_end=True):
    """Tube with an elliptical section: half-width W (side, B axis) and half-height H (up, N axis) per station.
    Returns V, F, t (0..1 along), a (0..1 around, 0 = dorsal)."""
    Pts = np.asarray(Pts, float)
    W = np.broadcast_to(np.asarray(W, float), (len(Pts),))
    H = np.broadcast_to(np.asarray(H, float), (len(Pts),))
    T, N, B = G.frames(Pts, up)
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(Pts, axis=0), axis=1))]
    tt = seg / max(seg[-1], 1e-9)
    V, tv, av = [], [], []
    for i in range(len(Pts)):
        for k in range(sides):
            ang = 2 * math.pi * k / sides
            V.append(Pts[i] + H[i] * math.cos(ang) * N[i] + W[i] * math.sin(ang) * B[i])
            tv.append(tt[i]); av.append(k / sides)
    F = []
    for i in range(len(Pts) - 1):
        for k in range(sides):
            a = i * sides + k
            b = i * sides + (k + 1) % sides
            F.append((a, b, b + sides, a + sides))
    if cap_end:
        c = len(V); V.append(Pts[-1] + T[-1] * min(W[-1], H[-1]) * 0.6); tv.append(1.0); av.append(0.0)
        base = (len(Pts) - 1) * sides
        F += [(base + k, base + (k + 1) % sides, c) for k in range(sides)]
    if cap_start:
        c = len(V); V.append(Pts[0] - T[0] * min(W[0], H[0]) * 0.6); tv.append(0.0); av.append(0.0)
        F += [((k + 1) % sides, k, c) for k in range(sides)]
    return np.array(V), F, np.array(tv), np.array(av), T, N, B


def spine_u(V):
    """Spine coordinate from model x (snout +0.5 -> u 0)."""
    return np.clip(0.5 - np.asarray(V)[:, 0], 0.0, 1.0)


def col_u(V, hinge=0.0, u=None):
    n = len(V)
    uu = spine_u(V) if u is None else np.full(n, u, float)
    hh = np.broadcast_to(np.asarray(hinge, float), (n,))
    return np.stack([np.zeros(n), np.zeros(n), hh, uu], -1)


def appendage(g, pts, r0, r1, uv, u_base, sides=3, mat=0):
    """Thin tapered limb along pts: hinge runs 0 at the base -> 1 at the tip, u fixed at its base station."""
    n = len(pts)
    Vt, Ft, tv, av = G.tube(pts, np.linspace(r0, r1, n), sides, cap_end=True)
    col = np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)), tv, np.full(len(Vt), u_base)], -1)
    g.add(Vt, Ft, uv(tv, av), col, mat=mat)


def normalize_x(objs, x0, x1, sit=False):
    """Scale so [x0, x1] maps to [-0.5, 0.5] (snout at +0.5)."""
    sc = 1.0 / (x1 - x0)
    off = -(x0 + x1) / 2
    for o in objs:
        V = C.get_verts(o)
        V[:, 0] += off
        V *= sc
        o.data.vertices.foreach_set("co", V.ravel())
        o.data.update()


def save_atlas(at, name):
    tex = os.path.join(C.BUILD, f"{name}.png")
    os.makedirs(C.BUILD, exist_ok=True)
    at.cv.save(tex)
    return C.load_image(tex, name=name)


# ----------------------------------------------------------------------------
# bristleworm (Eurythoe complanata-type amphinomid)
# ----------------------------------------------------------------------------
def build_bristleworm():
    """Amphinomid fireworm as seen in reef tanks (research 16): long, slightly flattened, pink-salmon to orange-tan,
    segmental grooves, a paler mid-dorsal line, bushy red branchiae on every chaetiger from ~4 on, white calcareous
    notochaetae fanned out to the sides (they flare when touched), a lobed caruncle over the first 3-4 segments and
    five short prostomial antennae."""
    C.reset_scene()
    nseg = 42
    at = Atlas(512, 256, 4)

    def body(cv):  # u along body, v flank height (1 dorsal)
        cv.vgrad("#F0C2A4", "#D98466", 0.0, 1.0)
        ph = (cv.u * nseg) % 1.0
        groove = 1 - S(0.0, 0.14, np.minimum(ph, 1 - ph))
        cv.blend(groove, "#8E4434", 0.55)
        cv.blend((1 - S(0.0, 0.07, np.abs(cv.v - 1.0))) * S(0.06, 0.12, cv.u), "#F6D8C4", 0.55)  # mid-dorsal line
        cv.blend(1 - S(0.0, 0.08, cv.u), "#E8907A", 0.6)
        n = P.fbm(cv.h, cv.w, 48, 3, 11)
        cv.multiply(0.92 + 0.14 * n)
    at.paint_strip(0, body)
    at.paint_strip(1, lambda cv: cv.vgrad("#F8F4EC", "#FFFFFF"))         # chaetae: white, glassy
    at.paint_strip(2, lambda cv: cv.vgrad("#A8241C", "#D2483A"))         # branchiae: blood red
    at.paint_strip(3, lambda cv: (cv.vgrad("#C8603E", "#E0805C"),        # caruncle
                                  cv.blend(P.band(cv.u, 0.3, 0.7, 0.1) * 0.5, "#F0B090")))
    img = save_atlas(at, "micro_bristleworm")
    mat = C.material("bristle_mat", rough=0.45, image=img)
    root = make_root("Root", dict(species="micro_bristleworm", lengthCm=5.0, iridescence=0.15, roughness=0.45,
                                  unit="body length 1.0 = prostomium..pygidium, facing +X"))
    n = 90
    xs = np.linspace(0.5, -0.5, n)
    Pts = np.stack([xs, 0 * xs, 0 * xs], -1)
    t = np.linspace(0, 1, n)
    W = 0.034 * np.clip(S(0.0, 0.07, t) * 0.75 + 0.25, 0, 1) * (1 - 0.75 * S(0.82, 1.0, t))
    H = W * 0.62
    gb = G.Geo()
    V, F, tv, av, T, N, B = ell_tube(Pts, W, H, 10)
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2
    gb.add(V, F, at.uv(0, tv, h), col_u(V))
    # caruncle: lobed ridge on segments 1-4
    for k in range(4):
        x = 0.47 - k * 0.012
        Ve, Fe = G.ellipsoid((x, 0, 0.018), (0.008, 0.007, 0.006), 6, 4)
        gb.add(Ve, Fe, at.uv(3, np.full(len(Ve), 0.5)), col_u(Ve))
    body_o = geo_obj("Body", gb, root, "body", [mat])

    # chaetal fans (notopodia) and branchiae, one pair per chaetiger
    gl = G.Geo()
    gr = G.Geo()
    rs = np.random.default_rng(5)
    for s in range(nseg):
        ts = 0.05 + 0.9 * (s + 0.5) / nseg
        x = 0.5 - ts
        w = float(np.interp(ts, t, W))
        u = float(np.clip(0.5 - x, 0, 1))
        for side in (1, -1):
            base = np.array([x, side * w * 0.95, 0.0])
            L = 0.035 * (0.6 + 0.4 * S(0.0, 0.15, ts)) * (1 - 0.5 * S(0.8, 1.0, ts))
            for f in range(3):
                ang = math.radians(-35 + 35 * f + rs.uniform(-8, 8))
                d = np.array([-math.sin(math.radians(25)) + 0.3 * math.sin(ang), side * math.cos(ang), 0.25 * f - 0.1])
                d /= np.linalg.norm(d)
                Vc, Fc = G.cone(base, base + d * L, 0.0028, 3)
                col = np.stack([np.zeros(len(Vc)), np.zeros(len(Vc)), np.r_[np.zeros(3), 1.0], np.full(len(Vc), u)], -1)
                gl.add(Vc, Fc, at.uv(1, np.full(len(Vc), 0.5)), col)
            if ts > 0.1 and ts < 0.9:  # bushy red branchiae just dorsal of the notopodium
                bb = np.array([x, side * w * 0.65, w * 0.45])
                for f in range(2):
                    d = np.array([rs.uniform(-0.3, 0.3), side * 0.5, 1.0])
                    d /= np.linalg.norm(d)
                    Vc, Fc = G.cone(bb, bb + d * 0.016, 0.004, 4)
                    col = np.stack([np.zeros(len(Vc)), np.zeros(len(Vc)), np.r_[np.zeros(4), 1.0], np.full(len(Vc), u)], -1)
                    gr.add(Vc, Fc, at.uv(2, np.full(len(Vc), 0.5)), col)
    geo_obj("Chaetae", gl, root, "leg", [mat], extras={"count": nseg * 2})
    geo_obj("Branchiae", gr, root, "leg", [mat], extras={"gills": True})
    # prostomial antennae: 5 short cirri
    ga = G.Geo()
    for i, (dy, dz, L) in enumerate([(0, 0.006, 0.02), (0.008, 0.004, 0.016), (-0.008, 0.004, 0.016),
                                      (0.012, -0.002, 0.013), (-0.012, -0.002, 0.013)]):
        b0 = np.array([0.495, dy * 0.6, dz])
        pts = smooth_path([b0, b0 + [L * 0.6, dy * 0.6, 0.004], b0 + [L, dy * 1.4, 0.002]], 5)
        appendage(ga, pts, 0.0022, 0.0009, lambda tv_, av_: at.uv(0, np.zeros_like(tv_), av_), 0.0)
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 5})
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_bristleworm.glb"), 3600)


# ----------------------------------------------------------------------------
# gammarid amphipod
# ----------------------------------------------------------------------------
def build_amphipod():
    """Gammarid (Elasmopus / Gammaropsis, 6-15 mm in tanks): laterally compressed, dorsally arched body; head with a
    dark kidney eye, A1 about 0.45 BL and A2 0.35 BL; pereonites 1-7 with deep coxal plates on 1-4 that hide the leg
    bases; two subchelate gnathopods (2nd larger); pereopods 3-4 point forward-down, 5-7 back-down and longer; three
    pleonites with pleopods; urosome with three pairs of uropods and a small telson. Translucent olive-tan with
    brown dorsal saddles and a green-brown gut line."""
    C.reset_scene()
    at = Atlas(512, 256, 4)

    def body(cv):
        cv.vgrad("#C8B48C", "#8C7A52", 0.0, 1.0)
        cv.blend(S(0.6, 0.85, cv.v) * (0.5 + 0.5 * np.cos(cv.u * 2 * math.pi * 12) > 0.6), "#5E4E32", 0.55)
        cv.blend((1 - S(0.0, 0.08, np.abs(cv.v - 0.62))) * S(0.12, 0.2, cv.u) * (1 - S(0.7, 0.8, cv.u)), "#6E7A3A", 0.5)
        cv.multiply(0.9 + 0.15 * P.fbm(cv.h, cv.w, 32, 3, 21))
    at.paint_strip(0, body)
    at.paint_strip(1, lambda cv: (cv.vgrad("#B4A07A", "#A08C62"),           # coxal plates
                                  cv.blend(S(0.7, 0.95, cv.v), "#6A5A3C", 0.6)))
    at.paint_strip(2, lambda cv: cv.vgrad("#CDBE9C", "#D8CCAE"))           # limbs: paler, translucent look
    at.paint_strip(3, lambda cv: cv.vgrad("#3A1410", "#1A0806"))           # eye
    img = save_atlas(at, "micro_amphipod")
    mat = C.material("amphipod_mat", rough=0.35, image=img)
    meye = C.material("amphipod_eye", color="#20080A", rough=0.15)
    root = make_root("Root", dict(species="micro_amphipod", lengthCm=1.0, iridescence=0.05, roughness=0.35,
                                  unit="body length 1.0 = head..telson, facing +X"))
    # dorsal arch: spine from head (x=0.5) down-curving through the urosome
    keys = [(0.5, 0, 0.0), (0.35, 0, 0.035), (0.15, 0, 0.05), (-0.05, 0, 0.045), (-0.22, 0, 0.02),
            (-0.36, 0, -0.03), (-0.46, 0, -0.08), (-0.5, 0, -0.11)]
    Pts = smooth_path(keys, 60)
    t = np.linspace(0, 1, 60)
    Hh = np.interp(t, [0, 0.08, 0.3, 0.6, 0.8, 1.0], [0.035, 0.06, 0.075, 0.07, 0.045, 0.02])
    Ww = Hh * 0.42
    gb = G.Geo()
    V, F, tv, av, T, N, B = ell_tube(Pts, Ww, Hh, 12)
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2
    gb.add(V, F, at.uv(0, tv, h), col_u(V))
    # coxal plates: flat side plates hanging from the pereonites as a skirt (1-4 deep and overlapping, 5-7 shallow)
    for i, (ti, depth) in enumerate([(0.14, 0.055), (0.22, 0.06), (0.3, 0.065), (0.38, 0.07),
                                     (0.47, 0.035), (0.55, 0.03), (0.62, 0.025)]):
        k = int(ti * 59)
        top = Pts[k] - N[k] * Hh[k] * 0.2
        half_l = 0.048 if i < 4 else 0.04
        half_h = Hh[k] * 0.8 + depth
        m = 14
        for side in (1, -1):
            ring = []
            for j in range(m):
                a = 2 * math.pi * j / m
                # rounded-rectangle outline (superellipse), lower edge fuller
                ca, sa = math.cos(a), math.sin(a)
                x = half_l * np.sign(ca) * abs(ca) ** 0.6
                z = half_h * np.sign(sa) * abs(sa) ** 0.6
                ring.append(top + np.array([x, side * (Ww[k] * 0.92), 0]) + N[k] * (z - half_h * 0.5))
            ring = np.array(ring)
            c0 = ring.mean(axis=0)
            Vp = np.vstack([[c0], ring])
            Fp = [(0, j + 1, (j + 1) % m + 1) if side > 0 else (0, (j + 1) % m + 1, j + 1) for j in range(m)]
            hv = np.clip(0.5 + (Vp[:, 2] - top[2]) / (2 * half_h), 0, 1)
            gb.add(Vp, Fp, at.uv(1, np.full(len(Vp), 0.5), hv), col_u(Vp))
    body_o = geo_obj("Body", gb, root, "body", [mat])
    uvl = lambda tv_, av_: at.uv(2, tv_, av_)
    # legs: gnathopods 1-2, pereopods 3-7
    gl = G.Geo()
    legs = [  # (t station, forward lean, length, subchelate)
        (0.12, 0.6, 0.1, 1), (0.2, 0.5, 0.12, 2), (0.3, 0.4, 0.14, 0), (0.39, 0.3, 0.15, 0),
        (0.49, -0.45, 0.17, 0), (0.57, -0.6, 0.2, 0), (0.64, -0.75, 0.22, 0)]
    for (ti, lean, L, chela) in legs:
        k = int(ti * 59)
        base = Pts[k] - N[k] * Hh[k] * 0.9
        u = float(np.clip(0.5 - base[0], 0, 1))
        for side in (1, -1):
            b0 = base + np.array([0, side * Ww[k] * 0.8, -0.05])
            knee = b0 + np.array([lean * L * 0.25 + 0.02 * np.sign(lean), side * 0.035, -L * 0.5])
            foot = knee + np.array([lean * L * 0.55, side * 0.015, -L * 0.3])
            pts = smooth_path([b0, knee, foot], 7)
            appendage(gl, pts, 0.008, 0.003, uvl, u)
            if chela:  # enlarged propodus with the dactyl folded back on its palm
                pr = foot + np.array([0.008 * chela, 0, 0.004])
                Ve, Fe = G.ellipsoid(pr, (0.016 * chela, 0.006, 0.009 * chela), 6, 4)
                gl.add(Ve, Fe, uvl(np.full(len(Ve), 0.8), np.full(len(Ve), 0.5)), col_u(Ve, 1.0, u))
    # pleopods (3 pairs, short rami under the pleon) and uropods (3 pairs, styliform, pointing back)
    for ti in (0.7, 0.76, 0.82):
        k = int(ti * 59)
        b0 = Pts[k] - N[k] * Hh[k] * 0.9
        u = float(np.clip(0.5 - b0[0], 0, 1))
        for side in (1, -1):
            pts = smooth_path([b0 + [0, side * 0.01, 0], b0 + [-0.03, side * 0.015, -0.035], b0 + [-0.05, side * 0.012, -0.055]], 4)
            appendage(gl, pts, 0.006, 0.002, uvl, u)
    for j, ti in enumerate((0.88, 0.93, 0.97)):
        k = min(int(ti * 59), 58)
        b0 = Pts[k]
        d = Pts[k + 1] - Pts[k - 1]
        d /= np.linalg.norm(d)
        u = float(np.clip(0.5 - b0[0], 0, 1))
        for side in (1, -1):
            for ram in (0, 1):
                tip = b0 + d * (0.09 - 0.02 * j) + np.array([0, side * (0.02 + 0.012 * ram), -0.015 * ram])
                appendage(gl, smooth_path([b0, (b0 + tip) / 2 + [0, 0, 0.004], tip], 4), 0.005, 0.0015, uvl, u)
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 14})
    # antennae: A1 (longer, up-forward, with accessory flagellum) and A2 (forward-down, peduncle stout)
    ga = G.Geo()
    head = Pts[1] + np.array([0.0, 0, 0.01])
    for side in (1, -1):
        a1 = smooth_path([head + [0, side * 0.015, 0.02], head + [0.15, side * 0.05, 0.1],
                          head + [0.32, side * 0.12, 0.16], head + [0.45, side * 0.17, 0.15]], 12)
        appendage(ga, a1, 0.007, 0.0015, uvl, 0.0)
        a2 = smooth_path([head + [0, side * 0.02, -0.02], head + [0.12, side * 0.06, -0.02],
                          head + [0.26, side * 0.11, -0.06], head + [0.35, side * 0.14, -0.1]], 10)
        appendage(ga, a2, 0.008, 0.0015, uvl, 0.0)
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 4})
    ge = G.Geo()
    for side in (1, -1):
        Ve, Fe = G.ellipsoid(head + [-0.01, side * Ww[2] * 0.85, 0.005], (0.016, 0.006, 0.024), 8, 5)
        ge.add(Ve, Fe, None, col_u(Ve, 0.0, 0.03), mat=0)
    eyes = geo_obj("Eyes", ge, root, "eye", [meye])
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    allx = np.concatenate([C.get_verts(o)[:, 0] for o in (body_o,)])
    normalize_x(objs, allx.min(), allx.max())
    for o in objs:  # u from the normalized x for every part (appendages keep their base station)
        if o is body_o or o is eyes:
            cols = C.get_cols(o); V = C.get_verts(o)
            cols[:, 3] = spine_u(V) if o is body_o else 0.03
            C.set_cols(o, cols)
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_amphipod.glb"), 4000)


# ----------------------------------------------------------------------------
# mysid
# ----------------------------------------------------------------------------
def build_mysid():
    """Mysid (Mysidium / Americamysis type, 5-10 mm): slender, glassy pale with star-shaped brown-black
    chromatophores along the ventral midline; carapace over the thorax; big black eyes on short stalks; antennal
    scales and antennules forward; eight pairs of feathery thoracic exopods (the swimming legs); six abdominal
    somites; tail fan with the two statocysts showing as amber dots in the inner uropods."""
    C.reset_scene()
    at = Atlas(512, 256, 4)

    def body(cv):
        cv.vgrad("#D8CCB6", "#EAE2D2", 0.0, 1.0)
        for x in np.linspace(0.1, 0.9, 11):  # ventral chromatophores
            m = np.exp(-(((cv.u - x) / 0.012) ** 2 + ((cv.v - 0.05) / 0.06) ** 2))
            cv.blend(S(0.2, 0.6, m), "#3A2418", 0.8)
        cv.blend((1 - S(0.0, 0.05, np.abs(cv.v - 0.55))) * S(0.15, 0.25, cv.u) * (1 - S(0.55, 0.6, cv.u)), "#B89A6A", 0.45)
    at.paint_strip(0, body)
    at.paint_strip(1, lambda cv: cv.vgrad("#E8E0D0", "#F4EEE4"))
    at.paint_strip(2, lambda cv: (cv.vgrad("#EDE6D8", "#F4F0E6"),
                                  cv.blend(np.exp(-(((cv.u - 0.35) / 0.05) ** 2 + ((cv.v - 0.5) / 0.2) ** 2)) > 0.4, "#D08A30")))
    at.paint_strip(3, lambda cv: cv.vgrad("#050505", "#101010"))
    img = save_atlas(at, "micro_mysid")
    mat = C.material("mysid_mat", rough=0.25, image=img)
    meye = C.material("mysid_eye", color="#060606", rough=0.08)
    root = make_root("Root", dict(species="micro_mysid", lengthCm=0.8, iridescence=0.2, roughness=0.25,
                                  unit="body length 1.0 = rostrum..telson, facing +X"))
    keys = [(0.5, 0, 0.0), (0.3, 0, 0.012), (0.05, 0, 0.01), (-0.2, 0, 0.0), (-0.42, 0, -0.012)]
    Pts = smooth_path(keys, 50)
    t = np.linspace(0, 1, 50)
    Hh = np.interp(t, [0, 0.06, 0.2, 0.42, 0.5, 0.8, 1.0], [0.012, 0.04, 0.055, 0.052, 0.04, 0.03, 0.02])
    gb = G.Geo()
    V, F, tv, av, T, N, B = ell_tube(Pts, Hh * 0.85, Hh, 10)
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2
    gb.add(V, F, at.uv(0, tv, h), col_u(V))
    body_o = geo_obj("Body", gb, root, "body", [mat])
    uvl = lambda tv_, av_: at.uv(1, tv_, av_)
    # tail fan: telson + two pairs of uropods (inner with statocyst)
    gf = G.Geo()
    end = Pts[-1]
    for j, (ang, L, strip) in enumerate([(0, 0.13, 1), (22, 0.15, 2), (-22, 0.15, 2), (40, 0.16, 1), (-40, 0.16, 1)]):
        a = math.radians(ang)
        d = np.array([-math.cos(a), math.sin(a), 0.0])
        perp = np.array([-d[1], d[0], 0.0])
        Vf = [end] + [end + d * L * (0.15 + 0.85 * math.sin(math.pi * s / 6) ** 0.3) * (s / 6) + perp * 0.022 * math.sin(math.pi * s / 6)
                      for s in range(7)] + [end + d * L * (s / 6) - perp * 0.022 * math.sin(math.pi * s / 6) for s in range(6, -1, -1)]
        Vf = np.array(Vf)
        nf = len(Vf)
        Ff = [(0, i, i + 1) for i in range(1, nf - 1)]
        tl = np.linalg.norm(Vf - end, axis=1) / L
        gf.add(Vf, Ff, at.uv(strip, tl), col_u(Vf, 0.0, 1.0))
    geo_obj("TailFan", gf, root, "body", [mat])
    # thoracic exopods: 8 pairs of feathery swimming rami (the "leg" sweep animates them), endopods shorter
    gl = G.Geo()
    for i in range(8):
        ti = 0.1 + 0.32 * i / 7
        k = int(ti * 49)
        base = Pts[k] - N[k] * Hh[k] * 0.8
        u = float(np.clip(0.5 - base[0], 0, 1))
        for side in (1, -1):
            pts = smooth_path([base + [0, side * 0.02, 0], base + [-0.02, side * 0.07, -0.04], base + [-0.06, side * 0.1, -0.06]], 5)
            appendage(gl, pts, 0.004, 0.0015, uvl, u)
            for s in range(3):  # setae fringe
                p0 = pts[1 + s]
                Vc, Fc = G.cone(p0, p0 + [-0.025, side * 0.01, -0.01], 0.0012, 3)
                gl.add(Vc, Fc, uvl(np.full(len(Vc), 0.5), np.full(len(Vc), 0.5)), col_u(Vc, 0.5 + 0.2 * s, u))
    # pleopods: vestigial in females, a few small rami
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 16})
    # antennae: antennules (biramous) and antennae with a broad scale
    ga = G.Geo()
    head = Pts[0]
    for side in (1, -1):
        for (dz, L) in ((0.01, 0.35), (0.0, 0.28)):
            pts = smooth_path([head + [0, side * 0.01, dz], head + [L * 0.4, side * 0.05, dz + 0.03], head + [L, side * 0.1, dz + 0.02]], 8)
            appendage(ga, pts, 0.003, 0.0008, uvl, 0.0)
        b0 = head + [0.0, side * 0.018, -0.01]
        Vs = np.array([b0, b0 + [0.1, side * 0.025, 0.0], b0 + [0.12, side * 0.04, 0.0], b0 + [0.02, side * 0.035, 0.0]])
        ga.add(Vs, [(0, 1, 2), (0, 2, 3)], uvl(np.full(4, 0.5), np.full(4, 0.5)), col_u(Vs, np.array([0, 0.8, 1, 0.3]), 0.0))
        pts = smooth_path([b0, b0 + [0.12, side * 0.05, -0.01], b0 + [0.3, side * 0.09, -0.05]], 8)
        appendage(ga, pts, 0.002, 0.0006, uvl, 0.0)
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 6})
    ge = G.Geo()
    for side in (1, -1):
        b0 = Pts[3] + np.array([0, side * 0.02, 0.01])
        tip = b0 + np.array([0.025, side * 0.035, 0.008])
        Vt, Ft, _, _ = G.tube([b0, tip], [0.012, 0.012], 6, cap_end=False)
        ge.add(Vt, Ft, None, col_u(Vt, 0.0, 0.03), mat=0)
        Ve, Fe = G.ellipsoid(tip, (0.026, 0.026, 0.026), 8, 6)
        ge.add(Ve, Fe, None, col_u(Ve, 0.0, 0.03), mat=1)
    geo_obj("Eyes", ge, root, "eye", [mat, meye])
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    normalize_x(objs, end[0] - 0.16, 0.5)
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_mysid.glb"), 2600)


# ----------------------------------------------------------------------------
# isopod (sphaeromatid)
# ----------------------------------------------------------------------------
def build_isopod():
    """Sphaeromatid isopod (Dynamenella / Cymodoce type, 3-8 mm): domed oval in top view (L:W ~ 1.8), head with
    lateral eyes, seven pereonites as transverse bands, short pleon and a large rounded pleotelson, flat lateral
    uropods; mottled grey-brown with paler margins; seven pairs of short walking legs hidden under the edge."""
    C.reset_scene()
    at = Atlas(256, 128, 2)

    def body(cv):
        cv.vgrad("#9A9088", "#6E645C", 0.0, 1.0)
        seg = np.interp(cv.u, [0, 0.12, 0.16, 0.22, 0.28, 0.34, 0.4, 0.46, 0.52, 0.58, 0.64, 1.0],
                        [0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0])
        cv.blend(S(0.7, 1.0, seg) * S(0.2, 0.5, cv.v), "#4E4640", 0.5)
        cv.blend(S(0.55, 0.75, P.fbm(cv.h, cv.w, 20, 3, 33)), "#B4AA9E", 0.5)
        cv.blend(1 - S(0.05, 0.25, cv.v), "#B8AEA2", 0.6)
    at.paint_strip(0, body)
    at.paint_strip(1, lambda cv: cv.vgrad("#A89E92", "#B8AEA2"))
    img = save_atlas(at, "micro_isopod")
    mat = C.material("isopod_mat", rough=0.5, image=img)
    meye = C.material("isopod_eye", color="#0A0A0A", rough=0.2)
    root = make_root("Root", dict(species="micro_isopod", lengthCm=0.5, iridescence=0.0, roughness=0.5,
                                  unit="body length 1.0 = head..pleotelson, facing +X"))
    n = 40
    xs = np.linspace(0.5, -0.5, n)
    t = np.linspace(0, 1, n)
    W = 0.28 * np.sqrt(np.clip(1 - ((t - 0.45) / 0.56) ** 2, 0.02, 1))
    H = 0.6 * W * (1 - 0.3 * S(0.7, 1.0, t))
    Pts = np.stack([xs, 0 * xs, H * 0.1], -1)
    gb = G.Geo()
    V, F, tv, av, T, N, B = ell_tube(Pts, W, H, 14)
    V[:, 2] = np.maximum(V[:, 2], -0.02)  # flat belly
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2
    gb.add(V, F, at.uv(0, tv, h), col_u(V))
    # flat uropods either side of the pleotelson
    for side in (1, -1):
        Ve, Fe = G.ellipsoid((-0.38, side * 0.17, 0.0), (0.09, 0.05, 0.012), 8, 4)
        gb.add(Ve, Fe, at.uv(1, np.full(len(Ve), 0.5)), col_u(Ve))
    body_o = geo_obj("Body", gb, root, "body", [mat])
    gl = G.Geo()
    uvl = lambda tv_, av_: at.uv(1, tv_, av_)
    for i in range(7):
        x = 0.32 - i * 0.085
        u = float(0.5 - x)
        wi = float(np.interp(u, t, W))
        for side in (1, -1):
            b0 = np.array([x, side * wi * 0.7, -0.01])
            lean = 0.08 if i < 3 else -0.06
            pts = smooth_path([b0, b0 + [lean * 0.5, side * 0.06, -0.02], b0 + [lean, side * 0.1, -0.06]], 4)
            appendage(gl, pts, 0.014, 0.006, uvl, u)
    ga = G.Geo()
    for side in (1, -1):
        for (L, dz) in ((0.25, 0.03), (0.32, -0.0)):
            b0 = np.array([0.48, side * 0.05, dz])
            pts = smooth_path([b0, b0 + [L * 0.5, side * 0.08, 0.02], b0 + [L, side * 0.18, 0.0]], 6)
            appendage(ga, pts, 0.01, 0.003, uvl, 0.0)
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 14})
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 4})
    ge = G.Geo()
    for side in (1, -1):
        Ve, Fe = G.ellipsoid((0.42, side * 0.12, 0.06), (0.03, 0.02, 0.025), 6, 4)
        ge.add(Ve, Fe, None, col_u(Ve, 0.0, 0.06))
    geo_obj("Eyes", ge, root, "eye", [meye])
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_isopod.glb"), 2000)


# ----------------------------------------------------------------------------
# harpacticoid copepod (Tigriopus)
# ----------------------------------------------------------------------------
def build_copepod():
    """Harpacticoid (Tigriopus californicus, ~1 mm): tapering cylinder; cephalosome + 4 pedigerous somites (prosome)
    then a narrower 5-somite urosome; forked caudal rami with two long setae; short bent 9-segment antennules;
    bright red-orange (carotenoid) with a deeper red median naupliar eye; 4 pairs of swimming legs beneath."""
    C.reset_scene()
    at = Atlas(128, 64, 2)

    def body(cv):
        cv.vgrad("#C2401E", "#D9602E", 0.0, 1.0)
        b = np.interp(cv.u, [0, 0.22, 0.24, 0.32, 0.34, 0.42, 0.44, 0.5, 0.52, 0.6, 0.62, 0.7, 0.72, 0.8, 0.82, 0.9, 0.92, 1],
                      [0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0])
        cv.blend(b, "#8E2410", 0.6)
        cv.blend(np.exp(-(((cv.u - 0.05) / 0.03) ** 2 + ((cv.v - 1.0) / 0.2) ** 2)) > 0.4, "#6A0806", 0.9)
    at.paint_strip(0, body)
    at.paint_strip(1, lambda cv: cv.vgrad("#E07040", "#F09060"))
    img = save_atlas(at, "micro_copepod")
    mat = C.material("copepod_mat", rough=0.3, image=img)
    root = make_root("Root", dict(species="micro_copepod", lengthCm=0.1, iridescence=0.1, roughness=0.3,
                                  unit="body length 1.0 = cephalosome..caudal rami, facing +X"))
    n = 30
    xs = np.linspace(0.5, -0.42, n)
    t = np.linspace(0, 1, n)
    R = np.interp(t, [0, 0.06, 0.2, 0.5, 0.56, 0.95, 1.0], [0.04, 0.1, 0.11, 0.085, 0.055, 0.04, 0.03])
    Pts = np.stack([xs, 0 * xs, 0 * xs], -1)
    gb = G.Geo()
    V, F, tv, av, T, N, B = ell_tube(Pts, R * 0.95, R * 0.85, 8)
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2
    gb.add(V, F, at.uv(0, tv, h), col_u(V))
    body_o = geo_obj("Body", gb, root, "body", [mat])
    uvl = lambda tv_, av_: at.uv(1, tv_, av_)
    gl = G.Geo()
    for side in (1, -1):  # caudal rami + setae (part of the tail, sweep little)
        b0 = np.array([-0.42, side * 0.02, 0])
        appendage(gl, [b0, b0 + [-0.06, side * 0.02, 0]], 0.012, 0.008, uvl, 1.0)
        appendage(gl, smooth_path([b0 + [-0.06, side * 0.02, 0], b0 + [-0.2, side * 0.05, 0.0], b0 + [-0.3, side * 0.08, -0.01]], 4),
                  0.004, 0.001, uvl, 1.0)
    for i in range(4):  # swimming legs
        x = 0.25 - i * 0.1
        for side in (1, -1):
            b0 = np.array([x, side * 0.04, -0.07])
            appendage(gl, smooth_path([b0, b0 + [-0.04, side * 0.08, -0.04], b0 + [-0.08, side * 0.1, -0.05]], 4),
                      0.01, 0.004, uvl, float(0.5 - x))
    ga = G.Geo()
    for side in (1, -1):  # short antennules, bent back
        b0 = np.array([0.48, side * 0.04, 0.03])
        appendage(ga, smooth_path([b0, b0 + [0.08, side * 0.12, 0.02], b0 + [0.02, side * 0.25, 0.0], b0 + [-0.04, side * 0.3, -0.01]], 6),
                  0.012, 0.004, uvl, 0.0)
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 8})
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 2})
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    normalize_x(objs, -0.48 - 0.3 * 0.0, 0.5)
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_copepod.glb"), 900)


# ----------------------------------------------------------------------------
# micro-snail
# ----------------------------------------------------------------------------
def build_snail():
    """Micro-snail (Collonista / small trochoid, 2-4 mm): globose low-spired shell of ~3 whorls, tan-brown with
    darker spiral bands and axial flames, round aperture; pale grey foot with two slender cephalic tentacles and
    black eye spots at their bases."""
    C.reset_scene()
    at = Atlas(256, 128, 2)

    def shell(cv):
        cv.vgrad("#B49478", "#8A6A50")
        cv.blend(0.5 + 0.5 * np.sin(cv.v * math.pi * 10) > 0.75, "#5E4230", 0.6)
        cv.blend(0.5 + 0.5 * np.sin(cv.u * math.pi * 60 + 3 * cv.v) > 0.85, "#E0CCB4", 0.5)
    at.paint_strip(0, shell)
    at.paint_strip(1, lambda cv: cv.vgrad("#B8B2AA", "#D4CEC6"))
    img = save_atlas(at, "micro_snail")
    mat = C.material("msnail_mat", rough=0.35, image=img)
    meye = C.material("msnail_eye", color="#050505", rough=0.2)
    root = make_root("Root", dict(species="micro_snail", lengthCm=0.3, iridescence=0.2, roughness=0.35,
                                  unit="foot length 1.0, facing +X"))
    Vs, Fs, tv, av, ap, at_ = G.helix_shell(1.0, 3.0, 0.03, 0.26, n=48, sides=10)
    Vs = Vs @ G.rot_y(math.radians(20)).T + np.array([-0.05, 0, 0.26])
    gs = G.Geo()
    gs.add(Vs, Fs, at.uv(0, tv, av), col_u(Vs, 0.0, 0.5))
    shell_o = geo_obj("Shell", gs, root, "shell", [mat])
    gf = G.Geo()
    Ve, Fe = G.ellipsoid((0.0, 0, 0.04), (0.5, 0.2, 0.05), 12, 5)
    gf.add(Ve, Fe, at.uv(1, np.full(len(Ve), 0.5)), col_u(Ve, 0.0, 0.5))
    Ve, Fe = G.ellipsoid((0.38, 0, 0.09), (0.1, 0.1, 0.07), 8, 5)
    gf.add(Ve, Fe, at.uv(1, np.full(len(Ve), 0.5)), col_u(Ve, 0.0, 0.5))
    foot_o = geo_obj("Body", gf, root, "body", [mat])
    ga = G.Geo()
    for side in (1, -1):
        b0 = np.array([0.42, side * 0.06, 0.12])
        appendage(ga, smooth_path([b0, b0 + [0.15, side * 0.1, 0.06], b0 + [0.3, side * 0.18, 0.05]], 5), 0.018, 0.006,
                  lambda tv_, av_: at.uv(1, tv_, av_), 0.5)
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 2})
    ge = G.Geo()
    for side in (1, -1):
        Ve, Fe = G.ellipsoid((0.44, side * 0.09, 0.12), (0.018, 0.018, 0.018), 6, 4)
        ge.add(Ve, Fe, None, col_u(Ve, 0.0, 0.5))
    geo_obj("Eyes", ge, root, "eye", [meye])
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    for o in (shell_o, foot_o):
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_snail.glb"), 1400)


# ----------------------------------------------------------------------------
# red planaria (Convolutriloba retrogemma)
# ----------------------------------------------------------------------------
def build_flatworm():
    """Convolutriloba retrogemma (acoel, 2-5 mm): thin flat leaf, rounded front, posterior edge with three short
    lobes (two lateral + a median between notches); rust-red from symbiotic algae with a paler margin and a white
    statocyst spot at the front; glides on cilia with a rippling edge."""
    C.reset_scene()
    cv = P.Canvas(128, 128, "#A8402A")
    r = np.sqrt((cv.u - 0.5) ** 2 + (cv.v - 0.5) ** 2)
    cv.blend(S(0.32, 0.45, r), "#D07856", 0.6)
    cv.multiply(0.88 + 0.2 * P.fbm(128, 128, 24, 3, 77))
    cv.blend(np.exp(-(((cv.u - 0.85) / 0.03) ** 2 + ((cv.v - 0.5) / 0.03) ** 2)) > 0.5, "#F4EEE0")
    tex = os.path.join(C.BUILD, "micro_flatworm.png")
    cv.save(tex)
    mat = C.material("flatworm_mat", rough=0.4, image=C.load_image(tex, name="micro_flatworm"))
    root = make_root("Root", dict(species="micro_flatworm", lengthCm=0.35, iridescence=0.0, roughness=0.4,
                                  unit="body length 1.0, flat in XY, facing +X"))
    m = 48
    outline = []
    for k in range(m):
        a = 2 * math.pi * k / m
        ca, sa = math.cos(a), math.sin(a)
        rx = 0.5
        ry = 0.26 * (1 - 0.15 * ca)
        p = np.array([rx * ca, ry * sa])
        if ca < -0.55:  # trilobed posterior: two notches at +-~25 deg off the rear
            ang = math.atan2(sa, -ca)
            notch = math.exp(-((abs(ang) - 0.42) / 0.1) ** 2)
            p *= 1 - 0.28 * notch
        outline.append(p)
    outline = np.array(outline)
    V = [(0, 0, 0.03)] + [(x, y, 0.004) for x, y in outline] + [(0, 0, -0.005)]
    F = [(0, k + 1, (k + 1) % m + 1) for k in range(m)] + [(m + 1, (k + 1) % m + 1, k + 1) for k in range(m)]
    V = np.array(V)
    UV = np.stack([0.5 + V[:, 0], 0.5 + V[:, 1] * 1.6], -1)
    g = G.Geo()
    g.add(V, F, UV, col_u(V))
    o = geo_obj("Body", g, root, "body", [mat])
    C.recalc_normals(o)
    return finish(root, [o], os.path.join(C.MODELS, "micro_flatworm.glb"), 200)


# ----------------------------------------------------------------------------
# terebellid feeding tentacle
# ----------------------------------------------------------------------------
def build_tentacle():
    """One buccal tentacle of a terebellid (Loimia / Eupolymnia, the aquarium "spaghetti worm"): a soft, slightly
    flattened bluish-white ribbon with faint purple bands (research 16 §14.1), ~0.5-1 mm wide with a ventral ciliary groove (darker line) that carries
    particles to the mouth; tip a little wider. The runtime stretches it along the sand as it extends."""
    C.reset_scene()
    at = Atlas(256, 32, 1)

    def tent(cv):
        cv.vgrad("#D6DEE6", "#EEF2F6")                                        # bluish white
        cv.blend(1 - S(0.0, 0.12, np.abs(cv.v - 0.0)), "#A8B4C4", 0.6)        # ciliary groove
        cv.blend((0.5 + 0.5 * np.sin(cv.u * 2 * math.pi * 14) > 0.88).astype(float), "#8A6A9A", 0.45)  # purple bands
    at.paint_strip(0, tent)
    img = save_atlas(at, "micro_tentacle")
    mat = C.material("tentacle_mat", rough=0.3, image=img)
    root = make_root("Root", dict(species="micro_tentacle", lengthCm=8.0, iridescence=0.1, roughness=0.3,
                                  unit="tentacle length 1.0 = tip (+X)..base, facing +X"))
    xs = np.linspace(0.5, -0.5, 64)
    Pts = np.stack([xs, 0 * xs, 0 * xs], -1)
    t = np.linspace(0, 1, 64)
    R = 0.0045 * (1 + 0.5 * (1 - S(0.0, 0.05, t)))
    V, F, tv, av, T, N, B = ell_tube(Pts, R * 1.4, R, 5)
    g = G.Geo()
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2
    g.add(V, F, at.uv(0, tv, h), col_u(V))
    o = geo_obj("Body", g, root, "body", [mat])
    C.recalc_normals(o)
    return finish(root, [o], os.path.join(C.MODELS, "micro_tentacle.glb"), 800)



# ----------------------------------------------------------------------------
# caprellid skeleton shrimp
# ----------------------------------------------------------------------------
def build_caprellid():
    """Caprellid (research 17 §5): stick body nearly round in section, head fused with pereonite 1, long pereonites
    2-4, short 5-7 and a vestigial abdomen; A1 long, A2 shorter (setose sieves); gnathopod 1 small, gnathopod 2 large
    and raptorial on pereonite 2 held up mantis-like; oval gills on pereonites 3-4; pereopods 5-7 hooked for clinging
    at the rear. Takes the colour of its perch: here pink-red to match the red macroalga, with darker bands."""
    C.reset_scene()
    at = Atlas(256, 128, 2)

    def body(cv):
        cv.vgrad("#B8786C", "#D49A8C")
        b = np.interp(cv.u, [0, 0.12, 0.14, 0.34, 0.36, 0.56, 0.58, 0.74, 0.76, 0.84, 0.86, 1.0],
                      [0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0])
        cv.blend(b, "#6A3A32", 0.6)
        cv.multiply(0.9 + 0.15 * P.fbm(cv.h, cv.w, 24, 3, 61))
    at.paint_strip(0, body)
    at.paint_strip(1, lambda cv: cv.vgrad("#E6D6CC", "#F0E4DA"))
    img = save_atlas(at, "micro_caprellid")
    mat = C.material("caprellid_mat", rough=0.4, image=img)
    meye = C.material("caprellid_eye", color="#2B1A1A", rough=0.15)
    root = make_root("Root", dict(species="micro_caprellid", lengthCm=1.2, iridescence=0.0, roughness=0.4,
                                  unit="body length 1.0 = head..abdomen, facing +X (standing: +X up)"))
    xs = np.linspace(0.5, -0.5, 60)
    t = np.linspace(0, 1, 60)
    # segment joints slightly swollen
    joints = [0.12, 0.35, 0.57, 0.75, 0.85, 0.93]
    R = 0.022 + 0.008 * sum(np.exp(-((t - j) / 0.02) ** 2) for j in joints)
    R = R * (1 - 0.5 * S(0.93, 1.0, t)) + 0.012 * (1 - S(0.0, 0.08, t))
    Pts = np.stack([xs, 0 * xs, 0 * xs], -1)
    gb = G.Geo()
    V, F, tv, av, T, N, B = ell_tube(Pts, R, R, 8)
    gb.add(V, F, at.uv(0, tv, 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2), col_u(V))
    for side in (1, -1):  # gills on pereonites 3-4
        for x in (0.05, -0.12):
            Ve, Fe = G.ellipsoid((x, side * 0.045, -0.01), (0.035, 0.012, 0.022), 8, 4)
            gb.add(Ve, Fe, at.uv(1, np.full(len(Ve), 0.5)), col_u(Ve))
    body_o = geo_obj("Body", gb, root, "body", [mat])
    uvl = lambda tv_, av_: at.uv(1, tv_, av_)
    gl = G.Geo()
    for side in (1, -1):
        # gnathopod 1: small, folded under the head
        b0 = np.array([0.42, side * 0.02, -0.02])
        appendage(gl, smooth_path([b0, b0 + [0.04, side * 0.04, -0.05], b0 + [0.08, side * 0.03, -0.03]], 4), 0.008, 0.004, uvl, 0.08)
        # gnathopod 2: big raptorial, basis down then propodus folded up in front (praying)
        b0 = np.array([0.28, side * 0.025, -0.02])
        g2 = smooth_path([b0, b0 + [0.02, side * 0.08, -0.12], b0 + [0.1, side * 0.1, -0.14]], 6)
        appendage(gl, g2, 0.014, 0.01, uvl, 0.22)
        pr = g2[-1] + np.array([0.06, 0, 0.04])
        Ve, Fe = G.ellipsoid(pr, (0.07, 0.016, 0.025), 8, 4, rot=G.rot_y(math.radians(-35)))
        gl.add(Ve, Fe, at.uv(0, np.full(len(Ve), 0.3), np.full(len(Ve), 0.6)), col_u(Ve, 1.0, 0.22))   # propodus: body colour
        appendage(gl, smooth_path([pr + [0.06, 0, 0.04], pr + [0.0, 0, 0.0], pr + [-0.05, 0, -0.025]], 4), 0.006, 0.002, uvl, 0.22)
        # pereopods 5-7: hooked clinging legs at the rear, splayed to grip the perch
        for i, x in enumerate((-0.32, -0.38, -0.44)):
            b0 = np.array([x, side * 0.02, -0.02])
            pts = smooth_path([b0, b0 + [0.02 - 0.03 * i, side * 0.1, -0.08], b0 + [-0.04 - 0.03 * i, side * 0.12, -0.16],
                               b0 + [-0.02 - 0.03 * i, side * 0.1, -0.2]], 6)
            appendage(gl, pts, 0.01, 0.004, uvl, float(0.5 - x))
    ga = G.Geo()
    head = np.array([0.5, 0, 0.0])
    for side in (1, -1):
        appendage(ga, smooth_path([head + [0, side * 0.01, 0.01], head + [0.15, side * 0.04, 0.08], head + [0.38, side * 0.1, 0.12]], 10),
                  0.009, 0.002, uvl, 0.0)
        appendage(ga, smooth_path([head + [0, side * 0.012, -0.005], head + [0.1, side * 0.05, -0.0], head + [0.22, side * 0.08, 0.02]], 8),
                  0.007, 0.002, uvl, 0.0)
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 10})
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 4})
    ge = G.Geo()
    for side in (1, -1):
        Ve, Fe = G.ellipsoid((0.47, side * 0.018, 0.012), (0.012, 0.008, 0.012), 6, 4)
        ge.add(Ve, Fe, None, col_u(Ve, 0.0, 0.03))
    geo_obj("Eyes", ge, root, "eye", [meye])
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_caprellid.glb"), 2000)


# ----------------------------------------------------------------------------
# podocopid ostracod
# ----------------------------------------------------------------------------
def build_ostracod():
    """Podocopid ostracod (seed shrimp, 0.5-1.5 mm): smooth oval bivalved carapace, length:height ~2:1, translucent
    beige with a darker dorsal hinge line and a dark gut shadow; antennae and thoracic legs poke out of the ventral
    gape while it scurries."""
    C.reset_scene()
    at = Atlas(128, 64, 2)

    def shell(cv):
        cv.vgrad("#BCA878", "#D8C89A")
        cv.blend(1 - S(0.0, 0.06, np.abs(cv.v - 1.0)), "#6E5A3A", 0.7)
        cv.blend(np.exp(-(((cv.u - 0.5) / 0.22) ** 2 + ((cv.v - 0.55) / 0.2) ** 2)) * 0.5, "#5A4A2E")
    at.paint_strip(0, shell)
    at.paint_strip(1, lambda cv: cv.vgrad("#D8CCAA", "#E6DCC0"))
    img = save_atlas(at, "micro_ostracod")
    mat = C.material("ostracod_mat", rough=0.25, image=img)
    root = make_root("Root", dict(species="micro_ostracod", lengthCm=0.1, iridescence=0.25, roughness=0.25,
                                  unit="carapace length 1.0, hinge up, facing +X"))
    g = G.Geo()
    for side in (1, -1):  # two valves meeting at the hinge, gaping slightly below
        Ve, Fe = G.ellipsoid((0, side * 0.06, 0.0), (0.5, 0.17, 0.26), 14, 8)
        Ve[:, 1] = np.where(side * Ve[:, 1] < side * 0.06, side * 0.06 + 0.05 * (Ve[:, 1] - side * 0.06), Ve[:, 1])
        Ve[:, 2] += 0.02 * (Ve[:, 0] > 0)
        g.add(Ve, Fe, at.uv(0, np.clip(0.5 - Ve[:, 0], 0, 1), np.clip(0.5 + Ve[:, 2] * 1.9, 0, 1)), col_u(Ve, 0.0, 0.5))
    body_o = geo_obj("Body", g, root, "shell", [mat])
    gl = G.Geo()
    uvl = lambda tv_, av_: at.uv(1, tv_, av_)
    for side in (1, -1):
        b0 = np.array([0.35, side * 0.03, -0.12])
        appendage(gl, smooth_path([b0, b0 + [0.15, side * 0.06, 0.02], b0 + [0.3, side * 0.1, 0.0]], 5), 0.02, 0.006, uvl, 0.5)
        for x in (0.1, -0.1):
            b0 = np.array([x, side * 0.03, -0.2])
            appendage(gl, smooth_path([b0, b0 + [-0.03, side * 0.06, -0.08], b0 + [-0.08, side * 0.08, -0.12]], 4), 0.016, 0.006, uvl, 0.5)
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 6})
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_ostracod.glb"), 1000)


# ----------------------------------------------------------------------------
# Homotrema rubrum (red foram)
# ----------------------------------------------------------------------------
def build_foram():
    """Homotrema rubrum (research 17 §14.2): sessile red foram on shaded rock undersides, 2-5 mm (up to 8); most are
    hemispherical or globose with a knobbly surface pitted by pores; deep red wall (degraded symbiont chlorophyll),
    pale where weathered. Modelled as a squat knobbly dome sitting on z = 0 (unit = diameter)."""
    C.reset_scene()
    cv = P.Canvas(64, 64, "#B8202A")
    cv.multiply(0.8 + 0.3 * P.fbm(64, 64, 10, 3, 81))
    cv.blend(S(0.75, 0.85, P.value_noise(64, 64, 24, 24, 82)), "#5A0C12", 0.7)   # pores
    tex = os.path.join(C.BUILD, "micro_foram.png")
    cv.save(tex)
    mat = C.material("foram_mat", rough=0.7, image=C.load_image(tex, name="micro_foram"))
    root = make_root("Root", dict(species="micro_foram", lengthCm=0.4, iridescence=0.0, roughness=0.7,
                                  unit="diameter 1.0, sits on z = 0, top +Z"))
    Ve, Fe = G.ellipsoid((0, 0, 0), (0.5, 0.5, 0.38), 14, 7)
    ang = np.arctan2(Ve[:, 1], Ve[:, 0])
    Ve[:, :2] *= (1 + 0.07 * np.sin(5 * ang) * np.sin(3 * Ve[:, 2] * 8))[:, None]
    Ve[:, 2] = np.maximum(Ve[:, 2], 0.0)
    g = G.Geo()
    g.add(Ve, Fe, np.stack([0.5 + Ve[:, 0], 0.5 + Ve[:, 1]], -1), col_u(Ve, 0.0, 0.5))
    o = geo_obj("Body", g, root, "body", [mat])
    C.recalc_normals(o)
    return finish(root, [o], os.path.join(C.MODELS, "micro_foram.glb"), 300)


# ----------------------------------------------------------------------------
# calanoid copepod (Parvocalanus)
# ----------------------------------------------------------------------------
def build_calanoid():
    """Parvocalanus crassirostris (~0.3-0.5 mm, research 17 §2): glassy oval prosome (cephalosome + 4-5 pedigerous
    somites) with an orange lipid droplet, slender 3-4 somite urosome with setose caudal rami; antennules about one
    body length held out at right angles; median red eye."""
    C.reset_scene()
    at = Atlas(128, 64, 2)

    def body(cv):
        cv.vgrad("#C8C0A8", "#DCD6C4")
        cv.blend(np.exp(-(((cv.u - 0.32) / 0.06) ** 2 + ((cv.v - 0.6) / 0.25) ** 2)) > 0.5, "#E8902A")
        cv.blend(np.exp(-(((cv.u - 0.04) / 0.03) ** 2 + ((cv.v - 1.0) / 0.2) ** 2)) > 0.4, "#8A1410")
    at.paint_strip(0, body)
    at.paint_strip(1, lambda cv: cv.vgrad("#C8B89A", "#D8CCB0"))
    img = save_atlas(at, "micro_calanoid")
    mat = C.material("calanoid_mat", rough=0.2, image=img)
    root = make_root("Root", dict(species="micro_calanoid", lengthCm=0.04, iridescence=0.3, roughness=0.2,
                                  unit="body length 1.0 = head..caudal rami, facing +X"))
    xs = np.linspace(0.5, -0.45, 28)
    t = np.linspace(0, 1, 28)
    R = np.interp(t, [0, 0.08, 0.3, 0.55, 0.62, 1.0], [0.06, 0.15, 0.16, 0.12, 0.05, 0.035])
    Pts = np.stack([xs, 0 * xs, 0 * xs], -1)
    V, F, tv, av, T, N, B = ell_tube(Pts, R, R * 0.95, 8)
    g = G.Geo()
    g.add(V, F, at.uv(0, tv, 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2), col_u(V))
    body_o = geo_obj("Body", g, root, "body", [mat])
    uvl = lambda tv_, av_: at.uv(1, tv_, av_)
    ga = G.Geo()
    for side in (1, -1):  # antennules ~1 BL out at right angles, slightly swept back
        b0 = np.array([0.42, side * 0.08, 0.03])
        appendage(ga, smooth_path([b0, b0 + [-0.05, side * 0.45, 0.02], b0 + [-0.15, side * 0.95, -0.02]], 8), 0.014, 0.004, uvl, 0.05)
    gl = G.Geo()
    for side in (1, -1):
        b0 = np.array([-0.45, side * 0.02, 0])
        appendage(gl, smooth_path([b0, b0 + [-0.1, side * 0.04, 0], b0 + [-0.18, side * 0.08, 0]], 3), 0.008, 0.002, uvl, 1.0)
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 2})
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 2})
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "micro_calanoid.glb"), 600)

MICRO_BUILDERS = {
    "micro_bristleworm": build_bristleworm,
    "micro_amphipod": build_amphipod,
    "micro_mysid": build_mysid,
    "micro_isopod": build_isopod,
    "micro_copepod": build_copepod,
    "micro_snail": build_snail,
    "micro_flatworm": build_flatworm,
    "micro_tentacle": build_tentacle,
    "micro_caprellid": build_caprellid,
    "micro_ostracod": build_ostracod,
    "micro_foram": build_foram,
    "micro_calanoid": build_calanoid,
}

if __name__ == "__main__":
    import sys
    only = sys.argv[1:] or list(MICRO_BUILDERS)
    for k in only:
        MICRO_BUILDERS[k]()
