"""Invertebrates (ASSETS.md §2.8-2.18): shrimp, hermit crabs, Sally Lightfoot crab, brittle star, snails,
asterina, feather duster.

Mobile inverts are modeled at unit length facing +X (Blender Z up) like the fish; `extras.lengthCm` on Root
is the default scale. The feather duster is sessile and modeled in centimeters (origin at its base),
like the corals.
COLOR_0 use: A = 0 -> 1 along an appendage (antenna, leg, arm, radiole) or along the body spine;
B = appendage index / (count-1) (phase offsets for the runtime); R, G = 0.
"""
import math
import os

import numpy as np

import common as C
import geomlib as G
import paintlib as P
from common import bpy, hex2rgb

S = P.smoothstep
ATLAS = (512, 256)


# ----------------------------------------------------------------------------
# atlas helpers: the atlas is split into horizontal strips; each strip holds a banding pattern
# painted along x. UV of a vertex = (x0 + t*(x1-x0), strip center +- around offset)
# ----------------------------------------------------------------------------
class Atlas:
    def __init__(self, w=512, h=256, strips=4, color="#F4F2EE"):
        self.cv = P.Canvas(w, h, color)
        self.strips = strips
        self.w, self.h = w, h

    def rect(self, k):
        sh = self.h // self.strips
        return 0, k * sh, self.w, (k + 1) * sh

    def uv(self, k, t, a=None):
        """t along (0..1) -> u, a around (0..1) -> v within strip k (Blender v-up convention)."""
        x0, y0, x1, y1 = self.rect(k)
        t = np.clip(np.asarray(t, float), 0.002, 0.998)
        a = np.full_like(t, 0.5) if a is None else np.clip(np.asarray(a, float), 0.02, 0.98)
        u = (x0 + t * (x1 - x0)) / self.w
        ypix = y0 + (1 - a) * (y1 - y0)
        v = 1 - ypix / self.h
        return np.stack([u, v], -1)

    def paint_strip(self, k, fn):
        x0, y0, x1, y1 = self.rect(k)
        sub = P.Canvas(x1 - x0, y1 - y0)
        fn(sub)
        self.cv.rgb[y0:y1, x0:x1] = sub.rgb
        self.cv.a[y0:y1, x0:x1] = sub.a


def bands(cv, stops, soft=0.006):
    """stops: list of (t_end, color) covering 0..1 along x."""
    t0 = 0.0
    for t1, col in stops:
        m = P.band(cv.u, t0, t1, soft)
        cv.blend(m, col)
        t0 = t1


def smooth_path(keys, n):
    keys = np.asarray(keys, float)
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(keys, axis=0), axis=1))]
    s = np.linspace(0, seg[-1], n)
    from fishlib import pchip
    return np.stack([pchip(seg, keys[:, i], s) for i in range(keys.shape[1])], -1)


def make_root(name, extras):
    root = C.empty("Root")
    C.set_extras(root, **extras)
    return root


def finish(root, objs, path, budget=None):
    out = C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[invert] {os.path.basename(path)}: {tris} tris" + (f" (budget {budget})" if budget else ""))
    return tris


def geo_obj(name, g, root, part, mats, extras=None, smooth=True):
    V, F, UV, COL, FM = g.arrays()
    o = C.mesh_object(name, V, F, uvs=UV, cols=COL, parent=root, part=part, materials=mats, face_mats=FM,
                      smooth=smooth, extras=extras)
    return o


# ----------------------------------------------------------------------------
# shrimp (Stenopus hispidus / Lysmata amboinensis)
# ----------------------------------------------------------------------------
def shrimp_body(at, strip, keys, radii_keys, n=44, sides=12, squash=0.8):
    P_ = smooth_path(keys, n)
    rk = np.asarray(radii_keys, float)
    tt = np.linspace(0, 1, n)
    R = np.interp(tt, rk[:, 0], rk[:, 1])
    V, F, tv, av = G.tube(P_, R, sides, cap_start=True, cap_end=True, up=(0, 0, 1), squash=squash)
    # around coordinate -> flank height (0 belly, 1 dorsal), mirrored like the fish
    # frames: N = up projected, so angle 0 = dorsal
    h = 1 - np.abs(((av + 0.5) % 1.0) - 0.5) * 2  # 1 at a=0 (dorsal) -> 0 at a=0.5 (belly)
    UV = at.uv(strip, tv, h)
    COL = np.stack([np.zeros(len(V)), np.zeros(len(V)), np.zeros(len(V)), tv], -1)
    return V, F, UV, COL, P_, R


def tail_fan(end, direction, length, lobes=5, spread=80, w=0.045, segs=6, droop=-0.2):
    """Flat fan of lobes at the telson, in the body plane (X/Y), slight droop."""
    out = []
    d = np.asarray(direction, float)
    d /= np.linalg.norm(d)
    side = np.array([0.0, 1.0, 0.0])
    up = np.cross(d, side)
    for i in range(lobes):
        a = math.radians(-spread / 2 + spread * i / (lobes - 1))
        L = length * (1.0 if i == lobes // 2 else 0.92)
        axis = math.cos(a) * d + math.sin(a) * side + droop * up * 0.3
        axis /= np.linalg.norm(axis)
        perp = np.cross(up, axis)
        V2 = [end]
        for k in range(segs + 1):
            ang = -math.pi / 2 + math.pi * k / segs
            V2.append(end + axis * L * math.cos(ang) + perp * w * math.sin(ang))
        V2 = np.array(V2)
        F = [(0, k, k + 1) for k in range(1, segs + 1)]
        out.append((V2, F, i / (lobes - 1)))
    return out


def leg_path(base, knee, ankle, foot, n=8):
    return smooth_path([base, knee, ankle, foot], n)


def build_shrimp(kind="banded"):
    C.reset_scene()
    banded = kind == "banded"
    at = Atlas(512, 256, 4)
    if banded:
        # strip 0: body bands along spine (rostrum -> telson)
        at.paint_strip(0, lambda cv: bands(cv, [
            (0.08, "#F4F2EE"), (0.2, "#C8281E"), (0.28, "#F4F2EE"), (0.42, "#C8281E"), (0.5, "#F4F2EE"),
            (0.58, "#C8281E"), (0.64, "#F4F2EE"), (0.73, "#C8281E"), (0.79, "#F4F2EE"), (0.86, "#C8281E"),
            (1.0, "#F4F2EE")]))
        # strip 1: claws 4 bands each
        at.paint_strip(1, lambda cv: bands(cv, [
            (0.12, "#F4F2EE"), (0.25, "#C8281E"), (0.37, "#F4F2EE"), (0.5, "#C8281E"), (0.62, "#F4F2EE"),
            (0.75, "#C8281E"), (0.85, "#F4F2EE"), (0.95, "#C8281E"), (1.0, "#F4F2EE")]))
        # strip 2: legs / antennae white with faint translucency
        at.paint_strip(2, lambda cv: cv.vgrad("#E8E4DC", "#F8F6F2"))
        # strip 3: tail fan white with a red band in the middle
        at.paint_strip(3, lambda cv: (cv.vgrad("#F4F2EE", "#F4F2EE"),
                                      cv.blend(P.band(cv.u, 0.45, 0.75, 0.03), "#C8281E")))
    else:  # skunk cleaner
        def body(cv):
            v = cv.v  # flank height: 0 belly -> 1 dorsal
            cv.vgrad("#F2D9A8", "#E8B848", 0.0, 0.7)
            cv.blend(S(0.68, 0.72, v), "#C62B2B")
            cv.blend(S(0.9, 0.93, v), "#F4F4F0")
            cv.blend(P.band(cv.u, -0.1, 0.06, 0.02), "#F4F4F0", 0.6)  # pale rostrum
            n = P.fbm(cv.h, cv.w, 40, 3, 5)
            cv.multiply(0.94 + 0.12 * n)
        at.paint_strip(0, body)
        at.paint_strip(1, lambda cv: cv.vgrad("#F0C890", "#F6DDB0"))
        at.paint_strip(2, lambda cv: cv.vgrad("#F2F0EC", "#FAFAF8"))

        def tail(cv):
            cv.vgrad("#C62B2B", "#D13B2F")
            for x in (0.3, 0.55, 0.8):
                cv.blend(np.exp(-(((cv.u - x) / 0.04) ** 2 + ((cv.v - 0.5) / 0.15) ** 2) * 3) > 0.5, "#F4F4F0")
            cv.blend(S(0.88, 0.95, cv.u), "#F4F4F0")
        at.paint_strip(3, tail)
    tex = os.path.join(C.BUILD, f"{kind}_shrimp_atlas.png")
    at.cv.save(tex)
    img = C.load_image(tex, name=f"{kind}_shrimp")
    mshell = C.material(f"{kind}_shell", rough=0.5 if banded else 0.3, image=img)
    meye = C.material(f"{kind}_eye", color="#050505", rough=0.1)

    sid = "shrimp" if banded else "skunk_shrimp"
    root = make_root("Root", dict(species=sid, lengthCm=5.0 if banded else 6.0,
                                  unit="body length 1.0 = rostrum..telson, facing +X", iridescence=0.0,
                                  roughness=0.5 if banded else 0.3))
    # --- body spine (x forward, z up) ------------------------------------------
    if banded:
        keys = [(0.62, 0, 0.07), (0.5, 0, 0.05), (0.42, 0, 0.02), (0.2, 0, 0.02), (0.02, 0, 0.0),
                (-0.12, 0, -0.03), (-0.24, 0, -0.08), (-0.32, 0, -0.15), (-0.36, 0, -0.22)]
        rad = [(0.0, 0.004), (0.1, 0.02), (0.14, 0.06), (0.3, 0.11), (0.45, 0.105), (0.55, 0.095), (0.75, 0.075),
               (0.95, 0.05), (1.0, 0.04)]
        squash = 0.85
    else:
        keys = [(0.66, 0, 0.06), (0.52, 0, 0.04), (0.42, 0, 0.02), (0.2, 0, 0.03), (0.0, 0, 0.04),
                (-0.15, 0, 0.0), (-0.26, 0, -0.06), (-0.34, 0, -0.12), (-0.38, 0, -0.17)]
        rad = [(0.0, 0.004), (0.12, 0.016), (0.16, 0.05), (0.3, 0.09), (0.45, 0.092), (0.6, 0.085), (0.8, 0.065),
               (0.95, 0.045), (1.0, 0.036)]
        squash = 0.78
    gb = G.Geo()
    V, F, UV, COL, spine, R = shrimp_body(at, 0, keys, rad, n=48, sides=14, squash=squash)
    gb.add(V, F, UV, COL)
    # tail fan
    end = spine[-1]
    d = spine[-1] - spine[-3]
    for (Vf, Ff, k) in tail_fan(end, d, 0.13, lobes=5, spread=90, w=0.04):
        tl = np.linalg.norm(Vf - end, axis=1) / 0.13
        gb.add(Vf, Ff, at.uv(3, tl, np.full(len(Vf), 0.5)), [(0, 0, 0, 1.0)] * len(Vf))
    # spines (banded only): 60 small white cones on the carapace and the abdomen top
    if banded:
        rs = np.random.default_rng(3)
        for _ in range(60):
            i = rs.integers(6, 30)
            ang = rs.uniform(-1.3, 1.3)
            p = spine[i]
            r = R[i]
            nrm = np.array([0.0, math.sin(ang), math.cos(ang)])
            base = p + nrm * r * 0.95
            Vc, Fc = G.cone(base, base + nrm * 0.022 + np.array([-0.01, 0, 0]), 0.006, 4)
            gb.add(Vc, Fc, at.uv(2, np.full(len(Vc), 0.5)), [(0, 0, 0, i / 47)] * len(Vc))
    body = geo_obj("Body", gb, root, "shell", [mshell])

    # --- walking legs (4 pairs; banded: pair 3 = chelipeds built separately) --------------------
    gl = G.Geo()
    leg_x = [0.32, 0.22, 0.12, 0.03, -0.05]
    idx = 0
    for li, x in enumerate(leg_x):
        if banded and li == 2:
            continue
        for side in (1, -1):
            base = np.array([x, side * 0.05, -0.05])
            pts = leg_path(base, base + [0.03, side * 0.16, 0.05], base + [-0.02, side * 0.24, -0.08],
                           base + [-0.06, side * 0.27, -0.2], 9)
            Vt, Ft, tv, av = G.tube(pts, np.linspace(0.012, 0.005, 9), 5)
            col = np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)), np.full(len(Vt), idx / 9), tv], -1)
            gl.add(Vt, Ft, at.uv(2 if banded else 1, tv, av), col)
            idx += 1
    # skunk: small chelae on the first two pairs (white tips)
    legs = geo_obj("Legs", gl, root, "leg", [mshell], extras={"count": idx})

    # --- chelipeds (banded: big raised claws) --------------------------------------
    if banded:
        gc = G.Geo()
        for side in (1, -1):
            base = np.array([0.24, side * 0.06, -0.04])
            pts = smooth_path([base, base + [0.1, side * 0.2, 0.08], base + [0.28, side * 0.32, 0.22],
                               base + [0.46, side * 0.36, 0.34]], 14)
            Rr = np.linspace(0.03, 0.026, 14)
            Vt, Ft, tv, av = G.tube(pts, Rr, 8, cap_end=False)
            col = np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)), np.full(len(Vt), 0.5 + 0.5 * side), tv * 0.8], -1)
            gc.add(Vt, Ft, at.uv(1, tv * 0.8, av), col)
            tipd = pts[-1] - pts[-2]
            tipd /= np.linalg.norm(tipd)
            # palm (thicker) and two fingers
            palm_end = pts[-1] + tipd * 0.07
            Vp, Fp, tp, ap = G.tube([pts[-1], palm_end], [0.032, 0.03], 8, cap_end=False)
            gc.add(Vp, Fp, at.uv(1, 0.8 + 0.1 * tp, ap), [(0, 0, 0.5 + 0.5 * side, 0.85)] * len(Vp))
            up = np.array([0, 0, 1.0])
            for sgn in (1, -1):
                fp = smooth_path([palm_end + up * 0.012 * sgn, palm_end + tipd * 0.06 + up * 0.02 * sgn,
                                  palm_end + tipd * 0.13 + up * 0.004 * sgn], 6)
                Vf, Ff, tf, af = G.tube(fp, np.linspace(0.016, 0.003, 6), 6)
                gc.add(Vf, Ff, at.uv(1, 0.9 + 0.1 * tf, af), [(0, 0, 0.5 + 0.5 * side, 1.0)] * len(Vf))
        geo_obj("Claws", gc, root, "leg", [mshell], extras={"claw": True})

    # --- antennae ---------------------------------------------------------------------
    ga = G.Geo()
    head = spine[2] + np.array([0, 0, 0.02])
    ants = [(1.6, 1), (1.6, -1), (0.9, 1), (0.9, -1)]
    if not banded:
        ants += [(0.5, 1), (0.5, -1)]
    for i, (L, side) in enumerate(ants):
        if L > 1.0:
            ctrl = [head, head + [0.25, side * 0.08, 0.2], head + [0.2, side * 0.45, 0.45],
                    head + [-0.55, side * 0.6, 0.5]]
        elif L > 0.7:
            ctrl = [head, head + [0.3, side * 0.12, 0.1], head + [0.55, side * 0.35, 0.15],
                    head + [0.6, side * 0.55, 0.05]]
        else:
            ctrl = [head, head + [0.15, side * 0.05, 0.05], head + [0.3, side * 0.12, 0.08],
                    head + [0.42, side * 0.2, 0.06]]
        pts = G.bezier(*ctrl, 24)
        # rescale to the requested length
        ln = np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1))
        pts = head + (pts - head) * (L / ln)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.006, 0.0015, 24), 4, cap_end=False)
        col = np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)), np.full(len(Vt), i / (len(ants) - 1)), tv], -1)
        ga.add(Vt, Ft, at.uv(2, tv, av), col)
    geo_obj("Antennae", ga, root, "antenna", [mshell], extras={"count": len(ants)})

    # --- eyes on stalks ---------------------------------------------------------------------
    ge = G.Geo()
    for side in (1, -1):
        b0 = spine[3] + np.array([0.0, side * 0.025, 0.035])
        tip = b0 + np.array([0.03, side * 0.045, 0.03])
        Vt, Ft, tv, av = G.tube([b0, tip], [0.01, 0.009], 6, cap_end=False)
        ge.add(Vt, Ft, None, [(0, 0, 0, 0.1)] * len(Vt), mat=0)
        Ve, Fe = G.ellipsoid(tip, (0.022, 0.022, 0.022), 8, 6)
        ge.add(Ve, Fe, None, [(0, 0, 0, 0.1)] * len(Ve), mat=1)
    eyes = geo_obj("Eyes", ge, root, "eye", [mshell, meye])
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    # normalize: rostrum tip .. telson = 1.0, centered, facing +X
    xs = np.r_[spine[:, 0], end[0] - 0.13]
    x0, x1 = xs.min(), xs.max()
    sc = 1.0 / (x1 - x0)
    off = -(x0 + x1) / 2
    for o in objs:
        Vv = C.get_verts(o)
        Vv[:, 0] += off
        Vv *= sc
        o.data.vertices.foreach_set("co", Vv.ravel())
        o.data.update()
    for o in (body, eyes):
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, f"{sid}.glb"), 4200)


# ----------------------------------------------------------------------------
# hermit crabs
# ----------------------------------------------------------------------------
def build_hermit(kind="blue"):
    C.reset_scene()
    blue = kind == "blue"
    sid = "hermit_blue" if blue else "hermit_scarlet"
    at = Atlas(256, 256, 4)
    leg_bands = ([(0.22, "#3A6FD0"), (0.27, "#C8282C"), (0.5, "#3A6FD0"), (0.55, "#C8282C"), (0.8, "#3A6FD0"),
                  (0.85, "#C8282C"), (1.0, "#2E5CB8")] if blue else
                 [(0.25, "#D32F2F"), (0.3, "#B71C1C"), (0.55, "#D32F2F"), (0.6, "#B71C1C"), (0.85, "#D32F2F"),
                  (1.0, "#E8E0D0")])
    at.paint_strip(0, lambda cv: bands(cv, leg_bands, 0.01))
    at.paint_strip(1, lambda cv: cv.vgrad("#C88060", "#E0A07A") if blue else cv.vgrad("#E8B828", "#F2C230"))

    def shell(cv):
        rs = 11 if blue else 12
        cv.vgrad("#6E5E4E", "#9A8A74") if blue else cv.vgrad("#8A6A52", "#B89A7A")
        n = P.fbm(cv.h, cv.w, 16, 4, rs)
        cv.blend(S(0.5, 0.6, n), "#4A3E32", 0.6)
        stripes = 0.5 + 0.5 * np.sin(cv.v * 60)
        cv.multiply(0.9 + 0.1 * stripes)
    at.paint_strip(2, shell)
    at.paint_strip(3, lambda cv: cv.vgrad("#2E5CB8", "#4A80E0") if blue else cv.vgrad("#E8B828", "#F2C230"))
    tex = os.path.join(C.BUILD, f"{sid}_atlas.png")
    at.cv.save(tex)
    img = C.load_image(tex, name=sid)
    mat = C.material(f"{sid}_mat", rough=0.5, image=img)
    meye = C.material(f"{sid}_eye", color="#0A0A0A", rough=0.1)
    root = make_root("Root", dict(species=sid, lengthCm=2.0 if blue else 3.5,
                                  unit="shell length 1.0, crab faces +X", iridescence=0.0, roughness=0.5))
    # shell: conical helix lying on its side, apex toward -X/up, aperture at the front-bottom
    Vs, Fs, ts, as_, apc, apt = G.helix_shell(1.7, 3.6, 0.01, 0.2, n=56, sides=12)
    Rm = G.rot_y(math.radians(-100))
    Vs = Vs @ Rm.T + np.array([0.3, 0.0, 0.15])
    apc = apc @ Rm.T + np.array([0.3, 0.0, 0.15])
    gs = G.Geo()
    gs.add(Vs, Fs, at.uv(2, ts, as_), np.stack([np.zeros(len(Vs))] * 3 + [ts], -1))
    shell_o = geo_obj("Shell", gs, root, "shell", [mat])
    ap = apc + np.array([0.02, 0, -0.05])  # aperture center
    # body (cephalothorax) peeking out of the aperture
    gb = G.Geo()
    Vb, Fb = G.ellipsoid(ap + np.array([0.12, 0, -0.02]), (0.11, 0.09, 0.07), 10, 6)
    gb.add(Vb, Fb, at.uv(1, np.full(len(Vb), 0.5)), [(0, 0, 0, 0)] * len(Vb))
    body_o = geo_obj("Body", gb, root, "body", [mat])
    front = ap + np.array([0.2, 0, -0.02])
    # legs: 2 pairs of walking legs + 2 claws
    gl = G.Geo()
    k = 0
    for i, x in enumerate((0.0, -0.06)):
        for side in (1, -1):
            b = ap + np.array([0.14 + x, side * 0.07, -0.06])
            pts = smooth_path([b, b + [0.08, side * 0.14, 0.06], b + [0.18, side * 0.22, -0.04],
                               b + [0.24, side * 0.26, -0.16]], 9)
            Vt, Ft, tv, av = G.tube(pts, np.linspace(0.028, 0.012, 9), 6)
            gl.add(Vt, Ft, at.uv(0, tv, av), np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)),
                                                        np.full(len(Vt), k / 3), tv], -1))
            k += 1
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": k})
    gc = G.Geo()
    for side, big in ((1, 1.0 if blue else 1.25), (-1, 0.85)):
        b = front + np.array([0.0, side * 0.05, -0.02])
        pts = smooth_path([b, b + [0.08, side * 0.06, 0.0], b + [0.14, side * 0.05, -0.04]], 6)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.035, 0.03, 6) * big, 7, cap_end=False)
        gc.add(Vt, Ft, at.uv(0, tv * 0.6, av), [(0, 0, 0.5 + 0.5 * side, 0.5)] * len(Vt))
        Vc, Fc = G.ellipsoid(pts[-1] + np.array([0.05, 0, -0.01]) * big, (0.07 * big, 0.045 * big, 0.035 * big), 8, 6)
        gc.add(Vc, Fc, at.uv(0, np.full(len(Vc), 0.9)), [(0, 0, 0.5 + 0.5 * side, 1.0)] * len(Vc))
    geo_obj("Claws", gc, root, "leg", [mat], extras={"claw": True})
    # eyestalks + antennae
    ge = G.Geo()
    for side in (1, -1):
        b = front + np.array([0.0, side * 0.03, 0.04])
        tip = b + np.array([0.04, side * 0.03, 0.12])
        Vt, Ft, tv, av = G.tube([b, tip], [0.015, 0.013], 6, cap_end=False)
        ge.add(Vt, Ft, at.uv(3, tv, av), [(0, 0, 0, 0)] * len(Vt), mat=0)
        Ve, Fe = G.ellipsoid(tip, (0.022, 0.022, 0.022), 8, 5)
        ge.add(Ve, Fe, None, [(0, 0, 0, 0)] * len(Ve), mat=1)
    geo_obj("Eyes", ge, root, "eye", [mat, meye])
    ga = G.Geo()
    for i, side in enumerate((1, -1)):
        b = front + np.array([0.02, side * 0.02, 0.02])
        pts = G.bezier(b, b + [0.15, side * 0.05, 0.12], b + [0.3, side * 0.15, 0.15], b + [0.4, side * 0.25, 0.1], 14)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.008, 0.002, 14), 4, cap_end=False)
        ga.add(Vt, Ft, at.uv(0, tv, av), np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)), np.full(len(Vt), i), tv], -1))
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 2})
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    _normalize_unit(objs, along="x")
    for o in (shell_o, body_o):
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, f"{sid}.glb"), 2500)


def _normalize_unit(objs, along="x", sit=True):
    allv = np.vstack([C.get_verts(o) for o in objs])
    lo, hi = allv.min(axis=0), allv.max(axis=0)
    sc = 1.0 / (hi[0] - lo[0])
    off = np.array([-(lo[0] + hi[0]) / 2, -(lo[1] + hi[1]) / 2, -lo[2] if sit else -(lo[2] + hi[2]) / 2])
    for o in objs:
        V = (C.get_verts(o) + off) * sc
        o.data.vertices.foreach_set("co", V.ravel())
        o.data.update()


# ----------------------------------------------------------------------------
# brittle star (black Ophiocoma): unit arm span, disc at origin, sits on z=0
# ----------------------------------------------------------------------------
def build_brittle_star():
    """Black brittle star (Ophiocoma sp.): pentagonal, granular disc with paired radial shields; five long arms
    (span ~6x the disc) made of many short segments (dorsal arm plates with dark joints, a faint lighter band
    every few segments) that taper to a whip tip; a row of stout, blunt arm spines on each side of every
    other segment, about one arm-width long near the disc and shortening toward the tip."""
    C.reset_scene()
    at = Atlas(256, 128, 2)
    nseg = 44

    def arm(cv):
        # strip u = along the arm (0 disc -> 1 tip), v = around (0.5 = dorsal midline)
        cv.vgrad("#1A1A1C", "#202022")
        ph = (cv.u * nseg) % 1.0
        joint = 1 - S(0.0, 0.12, np.minimum(ph, 1 - ph))   # dark seam between segments
        cv.blend(S(0.25, 0.5, np.minimum(ph, 1 - ph)) * (1 - S(0.15, 0.45, np.abs(cv.v - 0.5))), "#3A3A3E", 0.55)
        cv.blend(joint, "#08080A", 0.8)
        cv.blend((1 - S(0.0, 0.3, np.abs(((cv.u * nseg / 6.0) % 1.0) - 0.5))) * 0.35, "#4A4A4E")
        cv.blend(S(0.88, 1.0, cv.u), "#2E2E30", 0.5)
    at.paint_strip(0, arm)

    def disc(cv):
        # strip u = angle around the disc, v = radius (0 rim -> 1 centre); granules + paired radial shields
        cv.vgrad("#1E1E20", "#141416")
        gr = P.value_noise(cv.h, cv.w, 96, 24, 31)
        cv.multiply(0.8 + 0.4 * S(0.45, 0.75, gr))
        a5 = (cv.u * 5) % 1.0
        for c in (0.42, 0.58):  # two radial shields per arm base, near the rim
            m = np.exp(-(((a5 - c) / 0.07) ** 2 + ((cv.v - 0.22) / 0.14) ** 2))
            cv.blend(S(0.4, 0.6, m), "#2E2E32", 0.8)
    at.paint_strip(1, disc)
    tex = os.path.join(C.BUILD, "brittle_star_atlas.png")
    at.cv.save(tex)
    mat = C.material("brittle_mat", rough=0.55, image=C.load_image(tex, name="brittle_star"))
    root = make_root("Root", dict(species="brittle_star", lengthCm=15.0, unit="arm span 1.0, disc at origin",
                                  arms=5, roughness=0.55))
    gd = G.Geo()
    Vd, Fd = G.ellipsoid((0, 0, 0.022), (0.075, 0.075, 0.024), 15, 6)
    ang = np.arctan2(Vd[:, 1], Vd[:, 0])
    # pentagonal disc, slightly notched between the arms
    Vd[:, :2] *= (1 + 0.10 * np.cos(5 * ang) - 0.04 * np.cos(10 * ang))[:, None]
    rad = np.linalg.norm(Vd[:, :2], axis=1) / 0.085
    top = Vd[:, 2] >= 0.022
    vv = np.where(top, 1 - np.clip(rad, 0, 1), 0.0)
    gd.add(Vd, Fd, at.uv(1, (ang / (2 * math.pi)) % 1, vv), [(0, 0, 0, 0)] * len(Vd))
    disc_o = geo_obj("Body", gd, root, "body", [mat])
    ga = G.Geo()
    for i in range(5):
        a = 2 * math.pi * i / 5
        d = np.array([math.cos(a), math.sin(a), 0])
        perp = np.array([-math.sin(a), math.cos(a), 0])
        n = 22
        t = np.linspace(0, 1, n)
        curl = 0.12 * np.sin(t * math.pi * (1.2 + 0.2 * i)) * (1 if i % 2 else -1)
        pts = (0.065 + 0.435 * t)[:, None] * d + (curl * t)[:, None] * perp + np.c_[0 * t, 0 * t, 0.02 - 0.013 * t]
        R = 0.019 * (1 - 0.86 * t ** 0.9)
        Vt, Ft, tv, av = G.tube(pts, R, 5, squash=0.6)
        ga.add(Vt, Ft, at.uv(0, tv, av), np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)),
                                                    np.full(len(Vt), i / 4), tv], -1))
        # stout arm spines: one per side at every other station, about an arm-width long, angled forward/up
        tang = np.gradient(pts, axis=0)
        tang /= np.linalg.norm(tang, axis=1, keepdims=True)
        for k in range(1, n - 2):
            nrm = np.cross([0, 0, 1.0], tang[k])
            for sgn in (1, -1):
                L = 0.012 + 0.016 * (1 - t[k])
                b = pts[k] + nrm * sgn * R[k] * 0.85
                tipp = b + nrm * sgn * L * 0.85 + tang[k] * L * 0.45 + np.array([0, 0, L * 0.2])
                Vc, Fc = G.cone(b, tipp, max(0.0022, R[k] * 0.16), 3)
                ga.add(Vc, Fc, at.uv(0, np.full(len(Vc), t[k])), [(0, 0, i / 4, t[k])] * len(Vc))
    geo_obj("Arms", ga, root, "leg", [mat], extras={"count": 5})
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    C.recalc_normals(disc_o)
    return finish(root, objs, os.path.join(C.MODELS, "brittle_star.glb"), 2500)


# ----------------------------------------------------------------------------
# snails and asterina
# ----------------------------------------------------------------------------
def build_nassarius():
    C.reset_scene()
    at = Atlas(256, 128, 2)

    def shell(cv):
        cv.vgrad("#8D7A62", "#C9B79A")
        cv.blend(S(0.4, 0.5, 0.5 + 0.5 * np.sin(cv.u * 2 * math.pi * 9)), "#3D2F22", 0.45)
        rib = 0.5 + 0.5 * np.sin(cv.v * 2 * math.pi * 10)
        cv.multiply(0.88 + 0.12 * rib)
    at.paint_strip(0, shell)
    at.paint_strip(1, lambda cv: cv.vgrad("#B8A890", "#D8CCB8"))
    tex = os.path.join(C.BUILD, "nassarius_atlas.png")
    at.cv.save(tex)
    mat = C.material("nassarius_mat", rough=0.45, image=C.load_image(tex, name="nassarius"))
    root = make_root("Root", dict(species="nassarius", lengthCm=1.5, unit="shell length 1.0, siphon toward +X",
                                  roughness=0.45, note="mostly buried: the runtime sinks it below the sand"))
    Vs, Fs, ts, as_, apc, apt = G.helix_shell(2.6, 4.5, 0.008, 0.17, n=44, sides=9, tube_ratio=0.9)
    Rm = G.rot_y(math.radians(-80))
    Vs = Vs @ Rm.T
    apc = apc @ Rm.T
    gs = G.Geo()
    gs.add(Vs, Fs, at.uv(0, ts, as_), [(0, 0, 0, 0)] * len(Vs))
    shell_o = geo_obj("Shell", gs, root, "shell", [mat])
    lo, hi = Vs.min(axis=0), Vs.max(axis=0)
    front = apc + np.array([0.05, 0, 0.0])
    gf = G.Geo()
    Vf, Ff = G.ellipsoid(np.array([(lo[0] + hi[0]) / 2 + 0.12, 0, lo[2] + 0.04]), (0.3, 0.14, 0.04), 10, 5)
    gf.add(Vf, Ff, at.uv(1, np.full(len(Vf), 0.5)), [(0, 0, 0, 0)] * len(Vf))
    foot_o = geo_obj("Foot", gf, root, "body", [mat])
    gsi = G.Geo()
    pts = G.bezier(front, front + [0.1, 0, 0.06], front + [0.18, 0, 0.15], front + [0.22, 0, 0.3], 10)
    Vt, Ft, tv, av = G.tube(pts, np.linspace(0.04, 0.03, 10), 6)
    gsi.add(Vt, Ft, at.uv(1, tv, av), np.stack([np.zeros(len(Vt))] * 3 + [tv], -1))
    geo_obj("Siphon", gsi, root, "antenna", [mat])
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    _normalize_unit(objs)
    for o in (shell_o, foot_o):
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "nassarius.glb"), 1000)


def build_astrea():
    C.reset_scene()
    at = Atlas(256, 128, 2)

    def shell(cv):
        # Lithopoma tectum: grey-brown / olive conical shell with knobby axial ribs, usually filmed with algae
        # and spotted with pink coralline; paler worn knobs (strip u = up the spire, v = around)
        cv.vgrad("#7E7660", "#A49A80")
        n = P.fbm(cv.h, cv.w, 12, 4, 41)
        cv.blend(S(0.5, 0.62, n), "#5E5C3A", 0.6)  # olive algae film
        n2 = P.fbm(cv.h, cv.w, 18, 4, 42)
        cv.blend(S(0.64, 0.7, n2), "#B87890", 0.75)  # pink coralline patches
        cv.blend(S(0.7, 0.74, P.fbm(cv.h, cv.w, 40, 2, 43)), "#E6E0D2", 0.6)  # pale worn specks
        rib = 0.5 + 0.5 * np.cos(cv.v * 2 * math.pi * 7 + cv.u * 2 * math.pi * 0.7)
        cv.blend(S(0.75, 0.95, rib), "#CFC6AE", 0.35)  # knob crests
        cv.multiply(0.9 + 0.1 * (0.5 + 0.5 * np.sin(cv.u * 2 * math.pi * 9)))  # growth lines
    at.paint_strip(0, shell)
    at.paint_strip(1, lambda cv: cv.vgrad("#7A6A50", "#A89878"))
    tex = os.path.join(C.BUILD, "astrea_atlas.png")
    at.cv.save(tex)
    mat = C.material("astrea_mat", rough=0.55, image=C.load_image(tex, name="astrea"))
    root = make_root("Root", dict(species="astrea", lengthCm=3.0, unit="shell base diameter 1.0, apex +Z",
                                  roughness=0.55))
    # conical shell: stacked rings with a 7-point star outline that twists up the spire
    rings, sides = 14, 28
    V, UV = [], []
    for i in range(rings):
        t = i / (rings - 1)
        z = 0.7 * t ** 0.9
        r = 0.5 * (1 - t) ** 1.05 + 0.01
        for k in range(sides):
            a = 2 * math.pi * k / sides
            spike = 1 + 0.18 * max(0.0, math.cos(7 * (a + 1.4 * t))) ** 3 * (1 - t * 0.6)
            V.append((r * spike * math.cos(a), r * spike * math.sin(a), z + 0.05))
            UV.append((t, k / sides))
    V.append((0, 0, 0.77))
    UV.append((1.0, 0.5))
    F = []
    for i in range(rings - 1):
        for k in range(sides):
            a = i * sides + k
            b = i * sides + (k + 1) % sides
            F.append((a, b, b + sides, a + sides))
    top = len(V) - 1
    base0 = (rings - 1) * sides
    for k in range(sides):
        F.append((base0 + k, base0 + (k + 1) % sides, top))
    # flat base
    bc = len(V)
    V.append((0, 0, 0.05))
    UV.append((0.0, 0.5))
    for k in range(sides):
        F.append(((k + 1) % sides, k, bc))
    V = np.array(V)
    UVa = np.array(UV)
    gs = G.Geo()
    gs.add(V, F, at.uv(0, UVa[:, 0], UVa[:, 1]), [(0, 0, 0, 0)] * len(V))
    shell_o = geo_obj("Shell", gs, root, "shell", [mat])
    gf = G.Geo()
    Vf, Ff = G.ellipsoid((0.05, 0, 0.03), (0.42, 0.3, 0.03), 12, 4)
    gf.add(Vf, Ff, at.uv(1, np.full(len(Vf), 0.5)), [(0, 0, 0, 0)] * len(Vf))
    foot_o = geo_obj("Foot", gf, root, "body", [mat])
    ga = G.Geo()
    for i, side in enumerate((1, -1)):
        b = np.array([0.4, side * 0.08, 0.05])
        pts = G.bezier(b, b + [0.08, side * 0.03, 0.04], b + [0.14, side * 0.08, 0.06], b + [0.18, side * 0.12, 0.05], 6)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.015, 0.004, 6), 4)
        ga.add(Vt, Ft, at.uv(1, tv, av), np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)), np.full(len(Vt), i), tv], -1))
    geo_obj("Tentacles", ga, root, "antenna", [mat])
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    for o in (shell_o, foot_o):
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "astrea.glb"), 1200)


ASTERINA_GLASS = [  # default world placements (cm) on the glass: pos3, normal3 (pointing into the tank)
    (5.0, 14.0, 33.0, 0, 0, -1), (9.0, 22.0, 33.0, 0, 0, -1), (47.0, 9.0, 33.0, 0, 0, -1),
    (0.0, 30.0, 12.0, 1, 0, 0), (121.9, 18.0, 20.0, -1, 0, 0), (88.0, 6.0, 33.0, 0, 0, -1)]


def build_asterina():
    """Asterina-type star (Aquilonastra, research 16 §14.4): small (5-15 mm), flat-bottomed and gently domed, short
    broad arms (R:r ~ 1.8) fringed with spinelets; the aquarium form is fissiparous, so most stars show 6-7 arms of
    unequal length (one side regenerating after a split). Mottled cream to olive-brown with orange flecks and a paler
    madreporite area; the top is granular with tiny spinelets and papulae."""
    C.reset_scene()
    cv = P.Canvas(128, 128, "#C9A88A")
    n = P.fbm(128, 128, 8, 4, 51)
    cv.blend(S(0.48, 0.62, n), "#7E6E4E", 0.75)
    cv.blend(S(0.66, 0.74, P.fbm(128, 128, 22, 2, 52)), "#EADCC6", 0.75)
    cv.blend(S(0.7, 0.78, P.fbm(128, 128, 26, 2, 53)), "#D8743A", 0.8)
    gran = P.value_noise(128, 128, 64, 64, 54)
    cv.multiply(0.86 + 0.22 * S(0.4, 0.8, gran))                       # granules / spinelets
    rr = np.sqrt((cv.u - 0.5) ** 2 + (cv.v - 0.5) ** 2)
    cv.blend(1 - S(0.0, 0.12, rr), "#DCC2A2", 0.45)
    cv.blend(S(0.8, 0.95, rr / 0.5), "#E6D6C0", 0.5)                    # pale spinelet fringe at the margin
    tex = os.path.join(C.BUILD, "asterina.png")
    cv.save(tex)
    mat = C.material("asterina_mat", rough=0.65, image=C.load_image(tex, name="asterina"))
    root = make_root("Root", dict(species="asterina", lengthCm=1.2, unit="diameter 1.0, flat in XY, top +Z",
                                  instanceStride=6, instances=[float(x) for p in ASTERINA_GLASS for x in p],
                                  instanceLayout="world pos3 (cm) + surface normal3; runtime scales by lengthCm"))
    arms = [1.0, 0.97, 1.0, 0.62, 0.5, 0.95]          # fissiparous: two short regenerating arms on one side
    na = len(arms)
    m, rings = 54, 4
    rs = np.random.default_rng(9)
    jitter = rs.uniform(-0.06, 0.06, na)

    def radius(a):
        # star outline: disc radius r plus a rounded lobe per arm, blended by the nearest arm's angle
        r = 0.2
        best = 0.0
        for i in range(na):
            ai = 2 * math.pi * i / na + jitter[i]
            d = math.atan2(math.sin(a - ai), math.cos(a - ai))
            best = max(best, arms[i] * math.exp(-(d / 0.36) ** 2))
        return r + 0.3 * best

    V = [(0.0, 0.0, 0.11)]
    UV = [(0.5, 0.5)]
    for j in range(1, rings + 1):
        f = j / rings
        for k in range(m):
            a = 2 * math.pi * k / m
            R = radius(a) * f
            z = 0.11 * (1 - f ** 1.6) + 0.012
            V.append((R * math.cos(a), R * math.sin(a), z))
            UV.append((0.5 + R * math.cos(a), 0.5 + R * math.sin(a)))
    F = [(0, k + 1, (k + 1) % m + 1) for k in range(m)]
    for j in range(rings - 1):
        b0 = 1 + j * m
        b1 = 1 + (j + 1) * m
        for k in range(m):
            F.append((b0 + k, b1 + k, b1 + (k + 1) % m, b0 + (k + 1) % m))
    # flat oral side
    rim = 1 + (rings - 1) * m
    c = len(V)
    V.append((0, 0, 0.0))
    UV.append((0.5, 0.5))
    for k in range(m):
        a = 2 * math.pi * k / m
        R = radius(a)
        V.append((R * math.cos(a) * 1.0, R * math.sin(a), 0.0))
        UV.append((0.5 + R * math.cos(a), 0.5 + R * math.sin(a)))
    for k in range(m):
        F.append((rim + k, rim + (k + 1) % m, c + 1 + (k + 1) % m, c + 1 + k))
        F.append((c, c + 1 + (k + 1) % m, c + 1 + k))
    V = np.array(V)
    V *= 0.5 / np.abs(V[:, :2]).max()
    g = G.Geo()
    g.add(V, F, np.array(UV), [(0, 0, 0, 0)] * len(V))
    o = geo_obj("Body", g, root, "body", [mat])
    C.recalc_normals(o)
    return finish(root, [o], os.path.join(C.MODELS, "asterina.glb"), 560)


# ----------------------------------------------------------------------------
# feather duster (sessile, centimeters, origin at the tube base on the sand, tube axis +Z)
# ----------------------------------------------------------------------------
def build_feather_duster():
    C.reset_scene()
    at = Atlas(256, 256, 2)

    def tube_tex(cv):
        cv.vgrad("#6A5A48", "#8A7A62")
        n = P.fbm(cv.h, cv.w, 30, 4, 61)
        cv.multiply(0.8 + 0.4 * n)
        grains = S(0.7, 0.75, P.fbm(cv.h, cv.w, 120, 1, 62))
        cv.blend(grains, "#D8D0C0", 0.5)
    at.paint_strip(0, tube_tex)

    def radiole(cv):
        # x along the radiole (0 base -> 1 tip), y across: feather with central rachis, banded brown/white
        cv.rgb[:] = hex2rgb("#A4532D")
        bandm = S(0.45, 0.55, 0.5 + 0.5 * np.sin(cv.u * 2 * math.pi * 6))
        cv.blend(bandm, "#F4F0E8", 0.85)
        cv.blend(1 - S(0.0, 0.25, cv.u), "#8B3A24", 0.6)
        across = np.abs(cv.v - 0.5) * 2
        barbs = 0.5 + 0.5 * np.sin((cv.u * 90 + across * 4) * math.pi)
        cv.a = (1 - S(0.06, 0.1, across)) * 1.0 + S(0.06, 0.1, across) * (0.85 * S(0.3, 0.7, barbs))
        cv.a *= 1 - S(0.92, 1.0, across)
        cv.a *= 1 - S(0.94, 1.0, cv.u) * 0.7
    at.paint_strip(1, radiole)
    tex = os.path.join(C.BUILD, "feather_duster_atlas.png")
    at.cv.save(tex, alpha=True)
    img = C.load_image(tex, name="feather_duster")
    mtube = C.material("duster_tube", rough=0.85, image=img)
    mcrown = C.material("duster_crown", rough=0.6, image=img, image_alpha=True, double=True)
    root = make_root("Root", dict(species="feather_duster", lengthCm=1.0, unit="centimeters, tube base at origin",
                                  crownCenter=[0, 13.2, 0], roughness=0.6))
    # tube: 13 cm, slightly curved, r 0.55 cm
    path = G.bezier((0, 0, -0.5), (0.5, 0, 4), (-0.3, 0.4, 9), (0.4, 0.2, 13), 14)
    Vt, Ft, tv, av = G.tube(path, np.linspace(0.6, 0.5, 14), 10, cap_end=False, cap_start=True)
    gt = G.Geo()
    gt.add(Vt, Ft, at.uv(0, tv, av), [(0, 0, 0, 0)] * len(Vt))
    tube_o = geo_obj("Tube", gt, root, "tube", [mtube])
    # crown: two semicircular lobes of 22 radioles each, opening to a funnel 7 cm across
    top = path[-1]
    gc = G.Geo()
    n_rad = 44
    for i in range(n_rad):
        lobe = i // (n_rad // 2)
        a = math.pi * (i % (n_rad // 2)) / (n_rad // 2 - 1) + lobe * math.pi
        out = np.array([math.cos(a), math.sin(a), 0.0])
        L = 4.4 + 0.3 * math.sin(i * 1.7)
        ctrl = [top + out * 0.3, top + out * 1.0 + [0, 0, 1.0], top + out * 2.8 + [0, 0, 2.2],
                top + out * 4.2 + [0, 0, 2.4]]
        pts = G.bezier(*ctrl, 10)
        ln = np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1))
        pts = pts[0] + (pts - pts[0]) * (L / ln)
        side = np.cross(out, [0, 0, 1.0])
        w = 0.32
        Vr = np.vstack([pts - side * w, pts + side * w])
        t = np.linspace(0, 1, 10)
        F = [(k, k + 1, 10 + k + 1, 10 + k) for k in range(9)]
        uv = np.vstack([at.uv(1, t, np.zeros(10)), at.uv(1, t, np.ones(10))])
        col = np.vstack([np.stack([np.zeros(10), np.zeros(10), np.full(10, i / (n_rad - 1)), t], -1)] * 2)
        gc.add(Vr, F, uv, col)
    crown_o = geo_obj("Crown", gc, root, "polyp", [mcrown], extras={"radioles": n_rad})
    C.recalc_normals(tube_o)
    return finish(root, [tube_o, crown_o], os.path.join(C.MODELS, "feather_duster.glb"), 1200)


# ----------------------------------------------------------------------------
# Sally Lightfoot crab (Percnon gibbesi): flat, nearly round maroon carapace with a green-yellow mid stripe and
# radiating lines; long flattened banded legs with yellow-gold spots and joint rings; small claws.
# Unit: carapace width 1.0 (= lengthCm 5), facing +X (it walks sideways along +-Y), sits on z = 0.
# ----------------------------------------------------------------------------
def build_sally_lightfoot():
    C.reset_scene()
    at = Atlas(512, 512, 2)

    def leg(cv):
        cv.vgrad("#4A0E18", "#6E1A24")  # across the leg (flattened top/bottom)
        u = cv.u
        for j in (0.34, 0.67, 0.93):     # gold rings at the joints
            cv.blend(1 - S(0.008, 0.02, np.abs(u - j)), "#E0B030")
        rs = np.random.default_rng(5)
        for _ in range(26):              # yellow-gold spots on each segment
            cu, cvv = rs.uniform(0.04, 0.96), rs.uniform(0.2, 0.8)
            r = rs.uniform(0.012, 0.022)
            cv.blend(1 - S(r * 0.6, r, np.hypot((u - cu) * 2.0, cv.v - cvv) * 0.5), "#E8C040")
        cv.blend(S(0.95, 1.0, u), "#2A0A0E")  # dark tips

    def cara(cv):
        # square region u 0.25..0.75 holds the top view: px across the body (x, front = 1), py side to side
        px = np.clip((cv.u - 0.25) / 0.5, 0, 1)
        py = cv.v
        X, Y = (px - 0.5) * 2, (py - 0.5) * 2
        r = np.hypot(X, Y)
        cv.vgrad("#5A1220", "#5A1220")
        cv.blend(np.clip(0.5 + 0.5 * X, 0, 1) * 0.4, "#7E2230")
        cv.blend(S(0.75, 1.0, r), "#3A0A12", 0.7)
        cv.blend((1 - S(0.025, 0.05, np.abs(Y))) * (1 - S(0.85, 0.95, np.abs(X))), "#B8D040")  # mid stripe
        ang = np.arctan2(Y, X)
        lines = (1 - S(0.05, 0.12, np.abs(((ang / (2 * np.pi) * 14) % 1.0) - 0.5))) \
            * S(0.25, 0.4, r) * (1 - S(0.85, 0.95, r))
        cv.blend(np.clip(lines, 0, 1) * 0.85, "#A8C840")                  # radiating lines
        cv.blend((1 - S(0.02, 0.05, np.abs(r - 0.55))) * 0.3, "#C0D050")   # faint ring
    at.paint_strip(0, leg)
    at.paint_strip(1, cara)
    tex = os.path.join(C.BUILD, "sally_lightfoot_atlas.png")
    at.cv.save(tex)
    mat = C.material("sally_lightfoot_mat", rough=0.45, image=C.load_image(tex, name="sally_lightfoot"))
    meye = C.material("sally_lightfoot_eye", color="#0A0A0A", rough=0.1)
    root = make_root("Root", dict(species="sally_lightfoot", lengthCm=5.0,
                                  unit="carapace width 1.0, crab faces +X and walks sideways (+-Y)",
                                  iridescence=0.05, roughness=0.45, gait="sideways", grazes="hair_algae"))
    # carapace: flattened, nearly round dome
    gb = G.Geo()
    Vb, Fb = G.ellipsoid((0, 0, 0.17), (0.46, 0.5, 0.1), 18, 8)
    Vb[:, 2] = np.maximum(Vb[:, 2], 0.11)
    x0, y0, x1, y1 = at.rect(1)
    uvb = np.stack([(0.25 + 0.5 * (Vb[:, 0] / 0.92 + 0.5)) * (x1 - x0) / at.w,
                    1 - (y0 + (Vb[:, 1] / 1.0 + 0.5) * (y1 - y0)) / at.h], -1)
    gb.add(Vb, Fb, uvb, [(0, 0, 0, 0)] * len(Vb))
    body_o = geo_obj("Body", gb, root, "shell", [mat])
    # 4 pairs of long, flattened walking legs
    gl = G.Geo()
    k = 0
    for i, (bx, sweep) in enumerate(((0.24, 0.55), (0.08, 0.2), (-0.1, -0.2), (-0.26, -0.55))):
        for side in (1, -1):
            b = np.array([bx, side * 0.4, 0.15])
            out = np.array([sweep, side * 1.0, 0.0])
            out /= np.linalg.norm(out)
            L = 1.05 if i in (1, 2) else 0.92
            pts = smooth_path([b, b + out * 0.42 * L + [0, 0, 0.26], b + out * 0.85 * L + [0, 0, 0.12],
                               b + out * 1.1 * L + [0.0, 0, -0.14]], 9)
            Vt, Ft, tv, av = G.tube(pts, np.linspace(0.08, 0.035, 9), 6, squash=0.5, up=(0, 0, 1))
            gl.add(Vt, Ft, at.uv(0, tv, av), np.stack([np.zeros(len(Vt)), np.zeros(len(Vt)),
                                                        np.full(len(Vt), k / 7), tv], -1))
            k += 1
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": k})
    # small claws at the front
    gc = G.Geo()
    for side in (1, -1):
        b = np.array([0.36, side * 0.2, 0.14])
        pts = smooth_path([b, b + [0.12, side * 0.1, 0.04], b + [0.2, side * 0.04, 0.02]], 5)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.04, 0.035, 5), 6, cap_end=False)
        gc.add(Vt, Ft, at.uv(0, tv * 0.5, av), [(0, 0, 0.5 + 0.5 * side, 0.5)] * len(Vt))
        Vc, Fc = G.ellipsoid(pts[-1] + np.array([0.06, -side * 0.01, 0.0]), (0.07, 0.04, 0.03), 8, 5)
        gc.add(Vc, Fc, at.uv(0, np.full(len(Vc), 0.5)), [(0, 0, 0.5 + 0.5 * side, 1.0)] * len(Vc))
    geo_obj("Claws", gc, root, "leg", [mat], extras={"claw": True})
    ge = G.Geo()
    for side in (1, -1):
        b = np.array([0.4, side * 0.13, 0.2])
        tip = b + np.array([0.05, side * 0.02, 0.06])
        Vt, Ft, tv, av = G.tube([b, tip], [0.025, 0.022], 6, cap_end=False)
        ge.add(Vt, Ft, at.uv(0, tv * 0.3, av), [(0, 0, 0, 0)] * len(Vt), mat=0)
        Ve, Fe = G.ellipsoid(tip, (0.03, 0.03, 0.03), 8, 5)
        ge.add(Ve, Fe, None, [(0, 0, 0, 0)] * len(Ve), mat=1)
    geo_obj("Eyes", ge, root, "eye", [mat, meye])
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    # unit = carapace width (x extent of the body 0.92 -> 1.0); feet on z = 0
    allv = np.vstack([C.get_verts(o) for o in objs])
    zmin = allv[:, 2].min()
    for o in objs:
        V = C.get_verts(o)
        V[:, 2] -= zmin
        V /= 0.92
        o.data.vertices.foreach_set("co", V.ravel())
        o.data.update()
    C.recalc_normals(body_o)
    return finish(root, objs, os.path.join(C.MODELS, "sally_lightfoot.glb"), 1600)


INVERT_BUILDERS = {
    "shrimp": lambda: build_shrimp("banded"),
    "skunk_shrimp": lambda: build_shrimp("skunk"),
    "hermit_blue": lambda: build_hermit("blue"),
    "hermit_scarlet": lambda: build_hermit("scarlet"),
    "brittle_star": build_brittle_star,
    "nassarius": build_nassarius,
    "astrea": build_astrea,
    "asterina": build_asterina,
    "feather_duster": build_feather_duster,
    "sally_lightfoot": build_sally_lightfoot,
}
