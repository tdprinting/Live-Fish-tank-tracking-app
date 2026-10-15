"""Invertebrates (ASSETS.md §2.8-2.18): shrimp, hermit crabs, Sally Lightfoot crab, brittle star, snails,
asterina, feather duster.

Mobile inverts are modeled at unit length facing +X (Blender Z up) like the fish; `extras.lengthCm` on Root
is the default scale. The feather duster is sessile and modeled in centimeters (origin at its base),
like the corals.
COLOR_0 use: A = 0 -> 1 along an appendage (antenna, leg, arm, radiole) or along the body spine;
B = appendage index / (count-1) (phase offsets for the runtime); R, G = 0.
Limb-rigged crawlers (brittle star, Sally Lightfoot; SIMULATION §19) instead carry R = t along the limb (0 root .. 1
tip), B = limb index / 9, A = 0.5 - x, and extras.limbPivots (10 x vec4, glTF space) for the renderer's limb pose.
"""
import math
import os

import numpy as np

import common as C
import brittle_geom as BG
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
        # strip 0: body bands along the spine (rostrum -> telson), set on the segments: white rostrum and front of the
        # carapace, red band behind the eyes, white mid-carapace band, red rear carapace, white abdominal segments 1-2,
        # the broad red band over the humped 3rd segment, white 4, red 5, white 6 (ADW/Wikipedia S. hispidus [M]: red and
        # white bands on carapace, abdomen and claws, "sometimes bordered in purple"; Tee's b_02/b_03 [H]). The white
        # is a translucent milky white, the red a deep scarlet.
        RED, WH, PUR = "#C21E1A", "#F3EEE8", "#6E2450"
        stops = [(0.10, WH), (0.21, RED), (0.27, WH), (0.41, RED), (0.53, WH), (0.66, RED), (0.73, WH), (0.82, RED),
                 (0.89, WH), (1.0, WH)]

        def body_bands(cv):
            bands(cv, stops)
            t0 = 0.0
            for t1, col in stops:  # thin purple rims on the red bands
                if col == RED:
                    cv.blend(P.band(cv.u, t0 - 0.006, t0 + 0.004, 0.003) + P.band(cv.u, t1 - 0.004, t1 + 0.006, 0.003), PUR, 0.6)
                t0 = t1
            for tb in (0.47, 0.55, 0.63, 0.71, 0.79, 0.87):  # abdominal segment joints
                cv.blend(P.band(cv.u, tb - 0.004, tb + 0.004, 0.003), "#000000", 0.18)
            cv.blend(P.value_noise(cv.h, cv.w, 60, 12, 9) * 0.5, "#FFFFFF", 0.25)
        at.paint_strip(0, body_bands)
        # strip 1: claws (merus, carpus, palm, fingers) banded
        at.paint_strip(1, lambda cv: bands(cv, [
            (0.12, WH), (0.25, RED), (0.37, WH), (0.5, RED), (0.62, WH),
            (0.75, RED), (0.85, WH), (0.95, RED), (1.0, WH)]))
        # strip 2: legs / antennae white with faint translucency
        at.paint_strip(2, lambda cv: cv.vgrad("#E8E4DC", "#F8F6F2"))
        # strip 3: tail fan white with a red band across the middle, white tips
        at.paint_strip(3, lambda cv: (cv.vgrad(WH, WH),
                                      cv.blend(P.band(cv.u, 0.42, 0.74, 0.03), RED)))
    else:  # skunk cleaner
        def body(cv):
            # Aquarium of the Pacific [M]: "pale amber body ... one white band down the carapace center separates two
            # red bands on each side"; the white stripe runs from the rostrum to the telson, the red bands are as wide
            # as the white one or wider, the flanks below are translucent amber-yellow
            v = cv.v  # flank height: 0 belly -> 1 dorsal
            cv.vgrad("#F4DDB0", "#EBB94A", 0.0, 0.66)
            cv.blend(S(0.64, 0.68, v), "#C4242A")
            cv.blend(S(0.66, 0.70, v) * (1 - S(0.70, 0.86, v)), "#D63A30", 0.4)
            cv.blend(S(0.86, 0.885, v), "#F6F5F0")
            cv.blend(P.band(cv.u, -0.1, 0.06, 0.02), "#F4F4F0", 0.6)  # pale rostrum
            for tb in (0.47, 0.55, 0.63, 0.71, 0.79, 0.87):  # abdominal segment joints
                cv.blend(P.band(cv.u, tb - 0.004, tb + 0.004, 0.003) * (1 - S(0.84, 0.88, v)), "#7A3A10", 0.25)
            n = P.fbm(cv.h, cv.w, 40, 3, 5)
            cv.multiply(0.94 + 0.12 * n)
        at.paint_strip(0, body)
        at.paint_strip(1, lambda cv: cv.vgrad("#F0C890", "#F6DDB0"))
        at.paint_strip(2, lambda cv: cv.vgrad("#F2F0EC", "#FAFAF8"))

        def tail(cv):
            # red tail fan with a white spot on each uropod/telson lobe near its tip and a pale rim (Aquarium of the
            # Pacific: "tail red with white spots" [M]); the fan's UV runs base -> tip only, so the spot is one short
            # band that each separate lobe shows as a spot
            cv.vgrad("#C62B2B", "#D13B2F")
            cv.blend(P.band(cv.u, 0.6, 0.74, 0.03), "#F4F4F0")
            cv.blend(S(0.93, 0.98, cv.u), "#F0E0D0", 0.7)
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
    # both: carapace ~0.4 of rostrum-telson, abdomen arched with its high point over the 3rd segment and bent down
    # behind it (the caridean "hump" is strong in Lysmata, milder in the straighter Stenopus) [M photos, research 17]
    if banded:
        keys = [(0.62, 0, 0.07), (0.5, 0, 0.05), (0.42, 0, 0.025), (0.2, 0, 0.025), (0.04, 0, 0.035),
                (-0.1, 0, 0.0), (-0.22, 0, -0.06), (-0.31, 0, -0.13), (-0.36, 0, -0.2)]
        rad = [(0.0, 0.004), (0.1, 0.02), (0.14, 0.06), (0.3, 0.105), (0.45, 0.105), (0.58, 0.1), (0.75, 0.078),
               (0.95, 0.05), (1.0, 0.042)]
        squash = 0.85
    else:
        keys = [(0.66, 0, 0.06), (0.52, 0, 0.04), (0.42, 0, 0.03), (0.22, 0, 0.04), (0.05, 0, 0.07),
                (-0.1, 0, 0.04), (-0.22, 0, -0.04), (-0.31, 0, -0.12), (-0.36, 0, -0.19)]
        rad = [(0.0, 0.004), (0.12, 0.016), (0.16, 0.055), (0.3, 0.098), (0.45, 0.1), (0.6, 0.092), (0.8, 0.07),
               (0.95, 0.048), (1.0, 0.04)]
        squash = 0.8
    gb = G.Geo()
    V, F, UV, COL, spine, R = shrimp_body(at, 0, keys, rad, n=48, sides=14, squash=squash)
    gb.add(V, F, UV, COL)
    # tail fan
    end = spine[-1]
    d = spine[-1] - spine[-3]
    for (Vf, Ff, k) in tail_fan(end, d, 0.13, lobes=5, spread=90 if banded else 110, w=0.04 if banded else 0.05):
        tl = np.linalg.norm(Vf - end, axis=1) / 0.13
        gb.add(Vf, Ff, at.uv(3, tl, np.full(len(Vf), 0.5)), [(0, 0, 0, 1.0)] * len(Vf))
    # spines (banded only): 60 small white cones on the carapace and the abdomen top
    if banded:
        rs = np.random.default_rng(3)
        for _ in range(50):
            i = rs.integers(6, 30)
            ang = rs.uniform(-1.3, 1.3)
            p = spine[i]
            r = R[i]
            nrm = np.array([0.0, math.sin(ang), math.cos(ang)])
            base = p + nrm * r * 0.95
            Vc, Fc = G.cone(base, base + nrm * 0.022 + np.array([-0.01, 0, 0]), 0.006, 4)
            gb.add(Vc, Fc, at.uv(2, np.full(len(Vc), 0.5)), [(0, 0, 0, i / 47)] * len(Vc))
    body = geo_obj("Body", gb, root, "shell", [mshell])

    # --- limb rig data (research 17) ------------------------------------------------------------
    # COLOR_0 on every limb vertex: R = limb class / 8, G = pair index / 8, B = hinge (0 base .. 1 tip),
    # A = spine coordinate u of the attachment point (set after normalisation, so the limb rides its body
    # station). Classes: 1 walking leg, 2 picking leg (small chelate P1-P2), 3 cheliped (Stenopus P3),
    # 4 antenna, 5 antennule flagellum, 6 pleopod, 7 third maxilliped. fish.vert animates each class.
    WALK, PICK, CLAW, ANT, ANTL, PLEO, MXP = 1, 2, 3, 4, 5, 6, 7
    bases = []  # (geo, first vertex, vertex count, base x) for the u fix-up below

    def limb(g, pts, radii, sides, cls, pair, uvstrip, base_x, cap_end=True, uscale=1.0):
        Vt, Ft, tv, av = G.tube(pts, radii, sides, cap_end=cap_end)
        col = np.stack([np.full(len(Vt), cls / 8), np.full(len(Vt), pair / 8), tv, np.zeros(len(Vt))], -1)
        g.add(Vt, Ft, at.uv(uvstrip, tv * uscale, av), col)
        bases.append((g, len(Vt), base_x))
        return Vt

    # --- pereiopods: P1-P2 slender pickers, P3 the big banded cheliped (Stenopus) or a walking leg
    # (Lysmata), P4-P5 walking legs (research 17: Stenopus has 3 chelate pairs, only P3 enlarged)
    gl = G.Geo()
    leg_x = [0.32, 0.22, 0.12, 0.03, -0.05]
    for li, x in enumerate(leg_x):
        if banded and li == 2:
            continue
        cls = PICK if li < 2 else WALK
        for side in (1, -1):
            base = np.array([x, side * 0.05, -0.05])
            if cls == PICK:
                # held forward and up under the head, chela tips near the mouth (white in Lysmata's front pair)
                L = 1.0 if li == 0 else 1.25
                pts = leg_path(base, base + [0.06 * L, side * 0.09, 0.0], base + [0.14 * L, side * 0.12, -0.06],
                               base + [0.2 * L, side * 0.1, -0.14], 7)
            else:
                pts = leg_path(base, base + [0.03, side * 0.16, 0.05], base + [-0.02, side * 0.24, -0.08],
                               base + [-0.06, side * 0.27, -0.2], 7)
            # Lysmata's front legs are white (the rocking signal, Caves 2019)
            limb(gl, pts, np.linspace(0.012, 0.005, 7), 5, cls, li, 2 if banded or cls == PICK else 1, x)
    # third maxillipeds: short leg-like palps in front of P1 (Lysmata picks with them; Stenopus grooms
    # its antennules with their brush, Bauer 1981)
    for side in (1, -1):
        base = np.array([0.4, side * 0.03, -0.04])
        pts = leg_path(base, base + [0.06, side * 0.05, -0.02], base + [0.12, side * 0.06, -0.05],
                       base + [0.17, side * 0.05, -0.06], 7)
        limb(gl, pts, np.linspace(0.009, 0.004, 7), 4, MXP, 0, 2 if banded else 1, 0.4)
    # pleopods: five pairs of swimmerets under abdominal segments 1-5
    for pi in range(5):
        k = 18 + pi * 5
        p0 = spine[k]
        x = p0[0]
        for side in (1, -1):
            base = p0 + np.array([0.0, side * 0.03, -R[k] * 0.8])
            pts = leg_path(base, base + [-0.01, side * 0.02, -0.04], base + [-0.03, side * 0.025, -0.07],
                           base + [-0.05, side * 0.02, -0.09], 4)
            limb(gl, pts, np.linspace(0.008, 0.003, 4), 4, PLEO, pi, 2 if banded else 1, x)
    legs = geo_obj("Legs", gl, root, "leg", [mshell], extras={"count": 18 if banded else 20})

    # --- chelipeds (banded: big raised claws on P3) --------------------------------------
    if banded:
        gc = G.Geo()
        for side in (1, -1):
            base = np.array([0.12, side * 0.06, -0.04])
            pts = smooth_path([base, base + [0.14, side * 0.2, 0.08], base + [0.36, side * 0.32, 0.22],
                               base + [0.56, side * 0.36, 0.34]], 14)
            Rr = np.linspace(0.03, 0.026, 14)
            tipd = pts[-1] - pts[-2]
            tipd /= np.linalg.norm(tipd)
            palm_end = pts[-1] + tipd * 0.07
            arm_len = np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1))
            full = arm_len + 0.2
            Vt, Ft, tv, av = G.tube(pts, Rr, 8, cap_end=False)
            col = np.stack([np.full(len(Vt), CLAW / 8), np.zeros(len(Vt)), tv * arm_len / full, np.zeros(len(Vt))], -1)
            gc.add(Vt, Ft, at.uv(1, tv * 0.8, av), col)
            n = len(Vt)
            # palm (thicker) and two fingers
            Vp, Fp, tp, ap = G.tube([pts[-1], palm_end], [0.032, 0.03], 8, cap_end=False)
            gc.add(Vp, Fp, at.uv(1, 0.8 + 0.1 * tp, ap), [(CLAW / 8, 0, (arm_len + 0.07 * t) / full, 0) for t in tp])
            n += len(Vp)
            up = np.array([0, 0, 1.0])
            for sgn in (1, -1):
                fp = smooth_path([palm_end + up * 0.012 * sgn, palm_end + tipd * 0.06 + up * 0.02 * sgn,
                                  palm_end + tipd * 0.13 + up * 0.004 * sgn], 6)
                Vf, Ff, tf, af = G.tube(fp, np.linspace(0.016, 0.003, 6), 6)
                gc.add(Vf, Ff, at.uv(1, 0.9 + 0.1 * tf, af), [(CLAW / 8, 0, min(1.0, (arm_len + 0.07 + 0.13 * t) / full), 0) for t in tf])
                n += len(Vf)
            # rows of short white spines along the arm and palm (S. hispidus "body and claws spined" [M]; Tee b_03 [H])
            rs = np.random.default_rng(11 if side > 0 else 12)
            for _ in range(20):
                tt = rs.uniform(0.1, 1.0)
                k = int(tt * 13)
                p = pts[k] * (1 - (tt * 13 - k)) + pts[min(k + 1, 13)] * (tt * 13 - k)
                tang = pts[min(k + 1, 13)] - pts[k]
                tang /= np.linalg.norm(tang)
                a = rs.uniform(0, 2 * math.pi)
                ref = np.array([0.0, 0.0, 1.0])
                e1 = np.cross(tang, ref)
                e1 /= np.linalg.norm(e1)
                e2 = np.cross(tang, e1)
                nrm = math.cos(a) * e1 + math.sin(a) * e2
                base = p + nrm * 0.026
                Vc, Fc = G.cone(base, base + nrm * 0.016 + tang * 0.008, 0.004, 3)
                gc.add(Vc, Fc, at.uv(2, np.full(len(Vc), 0.5)), [(CLAW / 8, 0, tt * arm_len / full, 0)] * len(Vc))
                n += len(Vc)
            bases.append((gc, n, 0.12))
        geo_obj("Claws", gc, root, "leg", [mshell], extras={"claw": True})

    # --- antennae: one long pair + the antennule flagella (Stenopus: 2 pairs, the antennules biramous so
    # four short whips; Lysmata: long white antennae and a forked antennule pair, research 17) ----------
    ga = G.Geo()
    head = spine[2] + np.array([0, 0, 0.02])
    if banded:
        ants = [(2.0, 1, ANT, 0), (2.0, -1, ANT, 0), (1.1, 1, ANTL, 0), (1.1, -1, ANTL, 0), (0.8, 1, ANTL, 1), (0.8, -1, ANTL, 1)]
    else:
        ants = [(1.6, 1, ANT, 0), (1.6, -1, ANT, 0), (0.9, 1, ANTL, 0), (0.9, -1, ANTL, 0), (0.5, 1, ANTL, 1), (0.5, -1, ANTL, 1)]
    for i, (L, side, cls, pair) in enumerate(ants):
        if cls == ANT:
            # swept up and back over the body
            ctrl = [head, head + [0.25, side * 0.08, 0.2], head + [0.2, side * 0.45, 0.45],
                    head + [-0.55, side * 0.6, 0.5]]
        elif pair == 0:
            ctrl = [head, head + [0.3, side * 0.12, 0.1], head + [0.55, side * 0.35, 0.15],
                    head + [0.6, side * 0.55, 0.05]]
        else:
            ctrl = [head, head + [0.15, side * 0.05, 0.05], head + [0.3, side * 0.12, 0.08],
                    head + [0.42, side * 0.2, 0.06]]
        pts = G.bezier(*ctrl, 16)
        # rescale to the requested length
        ln = np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1))
        pts = head + (pts - head) * (L / ln)
        limb(ga, pts, np.linspace(0.006, 0.0015, 16), 4, cls, pair, 2, head[0], cap_end=False)
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
    # limbs: A = spine coordinate of the attachment point (u = 0.5 - x in the normalised model)
    for o in objs:
        if o.name not in ("Legs", "Claws", "Antennae"):
            continue
        g = [b for b in bases if b[0] is {"Legs": gl, "Antennae": ga}.get(o.name, gc if banded else None)]
        cols = C.get_cols(o)
        i = 0
        for _, n, bx in g:
            cols[i:i + n, 3] = np.clip(0.5 - (bx + off) * sc, 0.0, 1.0)
            i += n
        assert i == len(cols), (o.name, i, len(cols))
        C.set_cols(o, cols)
    for o in (body, eyes):
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, f"{sid}.glb"), 4200)


# ----------------------------------------------------------------------------
# Hermit crabs (docs/research/hermit_crab_motion.md): blue-leg Clibanarius tricolor (≈ 2 cm, blue walking legs
# banded red-orange and yellow at the joints, orange antennae, near-equal chelae, usually in a Cerithium shell) and
# scarlet Paguristes cadenati (≈ 3 cm, bright red and hairless, yellow eyestalks with yellow-green corneas, deep red
# antennae, left chela slightly larger, Cerithium / Vasum shells). Only the chelipeds and two walking-leg pairs
# (P2-P3) show; the soft abdomen stays coiled in the shell. Unit: shell apex -> claw tips 1.0, faces +X, feet on z = 0.
# Limb rig (SIMULATION §19.5, core HermitLegs): COLOR_0 R = t along the limb, G = eye mask, B = limb / 9
# (legs k = 2 pair + (0 left, 1 right), 4/5 chelae, 6 shell, 7 shield + eyestalks, 8 antennae, 9 antennules);
# extras.limbPivots: legs/chelae = coxa, 6 = shell rock point on the ground, 7 = withdrawal point just inside the
# aperture (w = shield length), 8/9 = antenna / antennule base (w = length).
# ----------------------------------------------------------------------------
HERMIT_SHELLS = {   # helix_shell(steep, turns, r0, r1), nodules per whorl, nodule amplitude
    "blue": (8.5, 8.0, 0.02, 0.088, 9, 0.08),        # Cerithium: tall slender spire of ~8 whorls, beaded cords
    "scarlet": (4.6, 5.0, 0.016, 0.12, 7, 0.14),     # Vasum-like: heavier, knobbed shoulders
}


def _hermit_colors(blue):
    if blue:
        return dict(leg="#2F63C8", leg_dark="#1E3F8A", ring="#D2462A", ring2="#E8C040", tip="#F0E6C8",
                    claw="#2C3A52", claw_hi="#4A6890", claw_tip="#EADFC0", shield="#7A6A4A", shield_hi="#A8946A",
                    stalk="#C8803A", stalk_ring="#F2EDE0", cornea="#101418", ant="#E8762A", antl="#E89040")
    return dict(leg="#C81E1E", leg_dark="#8E1010", ring="#A01414", ring2="#D83A2A", tip="#F2E4D0",
                claw="#C42020", claw_hi="#E04030", claw_tip="#F4E8D8", shield="#B81C1C", shield_hi="#E04838",
                stalk="#F2C230", stalk_ring="#F6DA60", cornea="#4E6A2A", ant="#8E1212", antl="#B82020")


def _rot_to(src_a, src_b, dst_a, dst_b):
    """Rotation matrix taking the orthonormal pair (src_a, src_b) onto (dst_a, dst_b)."""
    def basis(a, b):
        a = a / np.linalg.norm(a)
        b = b - a * (a @ b)
        b = b / np.linalg.norm(b)
        return np.stack([a, b, np.cross(a, b)], -1)
    return basis(np.asarray(dst_a, float), np.asarray(dst_b, float)) @ basis(np.asarray(src_a, float), np.asarray(src_b, float)).T


def build_hermit(kind="blue"):
    C.reset_scene()
    blue = kind == "blue"
    sid = "hermit_blue" if blue else "hermit_scarlet"
    K = _hermit_colors(blue)
    at = Atlas(256, 256, 8)
    joints = (0.12, 0.45, 0.63, 0.82)          # coxa-basis, merus/carpus, carpus/propodus, propodus/dactyl

    def leg(cv):
        cv.vgrad(K["leg_dark"], K["leg"])
        for j in joints:
            cv.blend(1 - S(0.012, 0.03, np.abs(cv.u - j)), K["ring"])
            if blue:
                cv.blend(1 - S(0.004, 0.012, np.abs(cv.u - j - 0.032)), K["ring2"], 0.9)
        cv.blend(S(0.9, 0.99, cv.u), K["tip"], 0.85)
        if blue:   # sparse dark setae tufts along the propodus and dactyl
            cv.blend(S(0.55, 0.75, P.value_noise(cv.h, cv.w, 48, 6, 3)) * S(0.6, 0.9, cv.u), "#162238", 0.5)
    at.paint_strip(0, leg)

    def shield(cv):
        cv.vgrad(K["shield"], K["shield_hi"])
        cv.multiply(0.88 + 0.24 * P.value_noise(cv.h, cv.w, 24, 8, 5))
        if blue:
            cv.blend(S(0.6, 0.8, P.value_noise(cv.h, cv.w, 32, 10, 9)), "#3E5A8A", 0.35)
    at.paint_strip(1, shield)

    def shell(cv):
        rs = 11 if blue else 12
        if blue:
            cv.vgrad("#5E5246", "#B8AC98")
            n = P.fbm(cv.h, cv.w, 24, 4, rs)
            cv.blend(S(0.45, 0.6, n), "#3A3028", 0.65)
            cv.blend(S(0.62, 0.72, n), "#E4DCCC", 0.5)
            # spiral rows of beads, alternately dark and pale (Cerithium)
            beads = (0.5 + 0.5 * np.sin(2 * np.pi * cv.u * 8.0 * 9)) * (0.5 + 0.5 * np.sin(2 * np.pi * cv.v * 3))
            cv.blend(S(0.55, 0.8, beads), "#2E2620", 0.45)
            cv.blend(S(0.55, 0.8, (1 - beads)) * S(0.3, 0.7, n), "#EEE8DA", 0.35)
        else:
            cv.vgrad("#6A5038", "#C2A27A")
            n = P.fbm(cv.h, cv.w, 16, 4, rs)
            cv.blend(S(0.5, 0.62, n), "#4A3424", 0.6)
            cv.blend(S(0.4, 0.5, np.abs(np.sin(cv.u * 40))), "#8A6A4A", 0.3)
        cv.blend(S(0.55, 0.85, P.fbm(cv.h, cv.w, 8, 3, rs + 4)), "#4E5A3A", 0.25)   # coralline / film patches
        cv.blend(S(0.97, 1.0, cv.u), "#D8CCB8", 0.6)                                 # pale lip at the aperture
    at.paint_strip(2, shell)

    def claw(cv):
        cv.vgrad(K["claw"], K["claw_hi"])
        if not blue:   # small white tubercles on the scarlet's palms
            cv.blend(S(0.78, 0.84, P.value_noise(cv.h, cv.w, 96, 24, 21)), "#F0C8B8", 0.35)
        cv.blend(S(0.84, 0.96, cv.u), K["claw_tip"], 0.95)
    at.paint_strip(3, claw)
    at.paint_strip(4, lambda cv: (cv.vgrad(K["stalk"], K["stalk"]),
                                  cv.blend(1 - S(0.03, 0.07, np.abs(cv.u - 0.86)), K["stalk_ring"], 0.9)))
    at.paint_strip(5, lambda cv: (cv.vgrad(K["ant"], K["ant"]),
                                  cv.blend(1 - S(0.0, 0.5, np.abs(((cv.u * 30) % 1.0) - 0.5) * 2), K["antl"], 0.35)))
    at.paint_strip(6, lambda cv: cv.vgrad("#3A2A26", "#5A4036"))       # soft abdomen glimpsed in the aperture
    at.paint_strip(7, lambda cv: cv.vgrad(K["cornea"], K["cornea"]))
    tex = os.path.join(C.BUILD, f"{sid}_atlas.png")
    at.cv.save(tex)
    mat = C.material(f"{sid}_mat", rough=0.5, image=C.load_image(tex, name=sid))
    meye = C.material(f"{sid}_eye", color=K["cornea"], rough=0.08)

    def col(t, k, V, eye=0.0):
        V = np.asarray(V)
        return np.stack([np.asarray(t, float) * np.ones(len(V)), np.full(len(V), eye), np.full(len(V), k / 9.0),
                         np.zeros(len(V))], -1)

    piv = [None] * 10
    pw = [0.0] * 10
    # ---- shell: spire back and up ~25 deg, aperture facing down and forward, resting on the substrate ----
    steep, turns, r0, r1, knobs, kamp = HERMIT_SHELLS[kind]
    ns, sides = 80, 12
    Vs, Fs, ts, as_, apc, apt = G.helix_shell(steep, turns, r0, r1, n=ns, sides=sides)
    ring = Vs[:ns * sides].reshape(ns, sides, 3)
    cen = ring.mean(axis=1, keepdims=True)
    th = np.linspace(0, 2 * math.pi * turns, ns)[:, None]
    ang = (np.arange(sides) / sides)[None, :]
    nod = (0.5 + 0.5 * np.cos(th * knobs)) * (0.6 + 0.4 * np.cos(2 * math.pi * ang * 3)) if blue else \
        (0.5 + 0.5 * np.cos(th * knobs)) * np.exp(-((ang - 0.25) / 0.12) ** 2)
    ring = cen + (ring - cen) * (1 + kamp * nod[..., None] * np.linspace(0.3, 1.0, ns)[:, None, None])
    Vs[:ns * sides] = ring.reshape(-1, 3)
    axis = np.array([-math.cos(math.radians(25)), 0, math.sin(math.radians(25))])
    down = np.array([0.45, 0, -1.0])
    Rm = _rot_to(np.array([0, 0, 1.0]), apt, axis, down)
    Vs = Vs @ Rm.T
    apc = apc @ Rm.T
    T = apt @ Rm.T
    sh = np.array([-apc[0], -apc[1], 0.0]) + np.array([0, 0, -Vs[:, 2].min() + 0.012])
    Vs, apc = Vs + sh, apc + sh
    rho = 0.95 * r1
    gs = G.Geo()
    gs.add(Vs, Fs, at.uv(2, ts, as_), col(0, 6, Vs))
    Va, Fa = G.ellipsoid(apc - T * rho * 0.55, (rho * 0.8, rho * 0.8, rho * 0.8), 10, 6)
    gs.add(Va, Fa, at.uv(6, np.full(len(Va), 0.5)), col(0, 6, Va))
    piv[6] = np.array([Vs[:, 0].mean(), 0.0, 0.0])
    piv[7] = apc - T * rho * 0.25
    # ---- shield (hard anterior carapace) and the soft thorax bridging to the aperture ----
    SL = 0.2 if blue else 0.21
    pw[7] = SL
    sc = apc + T * 0.03 + np.array([0.13, 0, 0.06])
    gb = G.Geo()
    Vb, Fb = G.ellipsoid(sc, (SL * 0.5, SL * 0.4, SL * 0.24), 12, 7, rot=G.rot_y(math.radians(12)))
    Vb[:, 2] = np.where(Vb[:, 2] > sc[2], sc[2] + (Vb[:, 2] - sc[2]) * 0.8, Vb[:, 2])
    x0, y0, x1, y1 = at.rect(1)
    uvb = np.stack([(0.05 + 0.9 * np.clip((Vb[:, 0] - sc[0]) / SL + 0.5, 0, 1)) * (x1 - x0) / at.w,
                    1 - (y0 + np.clip((Vb[:, 1]) / (SL * 0.8) + 0.5, 0, 1) * (y1 - y0)) / at.h], -1)
    gb.add(Vb, Fb, uvb, col(0.2, 7, Vb))
    th_pts = smooth_path([apc - T * 0.02, apc + T * 0.03 + np.array([0.04, 0, 0.02]), sc - np.array([SL * 0.3, 0, 0.01])], 6)
    Vt, Ft, tv, av = G.tube(th_pts, np.linspace(rho * 0.75, SL * 0.3, 6), 10, cap_end=False)
    gb.add(Vt, Ft, at.uv(6, tv, av), col(0.0, 7, Vt))
    root = make_root("Root", {})
    body_o = geo_obj("Body", gb, root, "body", [mat])
    front = sc + np.array([SL * 0.45, 0, -0.005])
    # ---- eyestalks: long, yellow / orange, dark (scarlet: yellow-green) corneas ----
    ge = G.Geo()
    for sd in (1, -1):
        b = front + np.array([0.0, sd * 0.022, 0.02])
        tip = b + np.array([0.1, sd * 0.045, 0.14]) * (SL / 0.2)
        Vt, Ft, tv, av = G.tube([b, (b + tip) / 2 + [0.0, 0, 0.005], tip], [0.016, 0.0145, 0.0135], 7, cap_end=False)
        ge.add(Vt, Ft, at.uv(4, tv, av), col(tv, 7, Vt, 1.0), mat=0)
        Ve, Fe = G.ellipsoid(tip + (tip - b) * 0.06, (0.021, 0.021, 0.024), 8, 5)
        ge.add(Ve, Fe, at.uv(7, np.full(len(Ve), 0.5)), col(1.0, 7, Ve, 1.0), mat=1)
    geo_obj("Eyes", ge, root, "eye", [mat, meye])
    # ---- antennae (8): long flagella sweeping out; antennules (9): short, flicking ----
    ga = G.Geo()
    alen = 0.5 * (SL / 0.2)
    for sd in (1, -1):
        b = front + np.array([-0.005, sd * 0.045, -0.012])
        pts = G.bezier(b, b + [0.12, sd * 0.06, 0.08], b + [0.3, sd * 0.2, 0.12], b + [0.42, sd * 0.32, 0.04], 16) * 1.0
        pts = b + (pts - b) * (alen / 0.5)
        tt = np.linspace(0, 1, 16)
        Vt, Ft, tv, av = G.tube(pts, np.where(tt < 0.18, 0.009, 0.006 * (1 - tt) + 0.0015), 4, cap_end=False)
        ga.add(Vt, Ft, at.uv(5, tv, av), col(np.repeat(tt, 4), 8, Vt))
    piv[8] = front + np.array([-0.005, 0, -0.012]); pw[8] = alen
    for sd in (1, -1):
        b = front + np.array([0.005, sd * 0.012, 0.0])
        pts = smooth_path([b, b + [0.035, sd * 0.012, 0.05], b + [0.07, sd * 0.02, 0.085], b + [0.1, sd * 0.03, 0.07]], 7)
        tt = np.linspace(0, 1, 7)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.008, 0.004, 7), 4, cap_end=True)
        ga.add(Vt, Ft, at.uv(5, np.r_[np.repeat(tt, 4), 1.0], None), col(np.r_[np.repeat(tt, 4), 1.0], 9, Vt))
    piv[9] = front + np.array([0.005, 0, 0.0]); pw[9] = 0.12
    geo_obj("Antennae", ga, root, "antenna", [mat], extras={"count": 4})
    # ---- chelipeds (4 left, 5 right): merus forward-down, carpus raised, flattened palm before the face, fingers
    # pointing down to the substrate; the scarlet's left chela is a little larger ----
    gc = G.Geo()
    for sd, k in ((1, 4), (-1, 5)):
        big = (1.12 if sd > 0 else 1.0) if not blue else (1.04 if sd > 0 else 1.0)
        b = sc + np.array([SL * 0.32, sd * SL * 0.3, -SL * 0.22])
        p1 = b + np.array([0.07, sd * 0.03, -0.03]) * big          # merus: forward, out and down
        p2 = p1 + np.array([0.045, -sd * 0.005, 0.03]) * big       # carpus: raised
        pd = np.array([0.8, -sd * 0.28, -0.52]); pd /= np.linalg.norm(pd)
        pc = p2 + pd * 0.04 * big
        pts = smooth_path([b, p1, p2, pc], 8)
        tt = np.linspace(0, 0.6, 8)
        Vt, Ft, tv, av = G.tube(pts, np.r_[0.017, 0.018, 0.019, 0.02, 0.022, 0.023, 0.024, 0.024] * big, 7,
                                cap_start=True, cap_end=False)
        gc.add(Vt, Ft, at.uv(3, np.r_[np.repeat(tt, 7), 0.0], av), col(np.r_[np.repeat(tt, 7), 0.0], k, Vt))
        # palm (propodus): swollen oval, flattened side to side, tapering into the fingers
        Rz = _rot_to(np.array([1.0, 0, 0]), np.array([0, 0, 1.0]), pd, np.array([0, sd * 1.0, 0.0]))
        Vp, Fp = G.ellipsoid((0, 0, 0), (0.052 * big, 0.036 * big, 0.026 * big), 10, 6)
        Vp[:, 0] *= np.where(Vp[:, 0] > 0, 1.0, 0.8)
        Vp[:, 1:] *= np.where(Vp[:, 0] > 0, 1 - 0.35 * Vp[:, 0] / (0.052 * big), 1.0)[:, None]
        Vp = Vp @ Rz.T + pc
        gc.add(Vp, Fp, at.uv(3, np.clip(0.62 + 0.2 * (Vp - pc) @ pd / (0.052 * big), 0, 1)), col(0.75, k, Vp))
        for fy, fl in ((0.011, 0.05), (-0.011, 0.046)):   # dactyl above, fixed finger below, gaping a little
            fb = pc + pd * 0.036 * big + Rz[:, 1] * fy * big
            ft = pc + pd * (0.036 + fl) * big + Rz[:, 1] * fy * 0.15 * big
            Vf, Ff = G.cone(fb, ft, 0.011 * big, 6)
            gc.add(Vf, Ff, at.uv(3, np.r_[np.full(6, 0.82), 0.97]), col(np.r_[np.full(6, 0.9), 1.0], k, Vf))
        piv[k] = b; pw[k] = 0.2 * big
    geo_obj("Claws", gc, root, "leg", [mat], extras={"claw": True})
    # ---- walking legs P2 (pair 0) and P3 (pair 1): arched, knees high, long curved dactyls on the substrate ----
    gl = G.Geo()
    for pair, (adeg, L) in enumerate(((52.0, 0.44), (84.0, 0.42))):
        for sd in (1, -1):
            k = 2 * pair + (0 if sd > 0 else 1)
            a = math.radians(adeg)
            d = np.array([math.cos(a), sd * math.sin(a), 0.0])
            base = sc + np.array([SL * (0.12 - 0.22 * pair), sd * SL * 0.33, -SL * 0.2])
            L = L * (SL / 0.2)
            pts = smooth_path([base, base + d * 0.3 * L + [0, 0, 0.07], base + d * 0.5 * L + [0, 0, 0.075],
                               base + d * 0.78 * L + [0, 0, 0.0], base + d * 0.92 * L + [0.02, 0, -base[2] * 0.6],
                               base + d * 0.98 * L + [0.035, 0, -base[2] + 0.004]], 11)
            tt = np.linspace(0, 1, 11)
            R = 0.021 * (1 - 0.62 * tt) * np.where(tt < 0.45, 1.1, 1.0)
            Vt, Ft, tv, av = G.tube(pts, R, 6, squash=0.75)
            ts_ = np.r_[np.repeat(tt, 6), 1.0]
            gl.add(Vt, Ft, at.uv(0, ts_, av), col(ts_, k, Vt))
            piv[k] = base; pw[k] = L
    legs_o = geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 4})
    shell_o = geo_obj("Shell", gs, root, "shell", [mat])
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    # unit: shell apex .. claw tips = 1, centred in x/y, feet on z = 0
    span = np.vstack([C.get_verts(o) for o in objs if o.name in ("Shell", "Body", "Claws")])
    lo, hi = span.min(axis=0), span.max(axis=0)
    sc_ = 1.0 / (hi[0] - lo[0])
    off = np.array([-(lo[0] + hi[0]) / 2, 0.0, 0.0])
    for o in objs:
        V = (C.get_verts(o) + off) * sc_
        V[:, 2] = np.maximum(V[:, 2], 0.0)
        o.data.vertices.foreach_set("co", V.ravel())
        o.data.update()
    pivots = []
    for k in range(10):
        pv = (np.asarray(piv[k], float) + off) * sc_
        pivots += gltf_pt(pv) + [float(pw[k] * sc_)]
    for o in objs:
        C.set_extras(o, limbPivots=pivots)
    C.set_extras(root, species=sid, lengthCm=2.0 if blue else 3.5,
                 unit="shell apex to claw tips 1.0, crab faces +X and walks forward", iridescence=0.0,
                 roughness=0.5, gait="forward", limbRig="hermit_legs", limbPivots=pivots)
    for o in (shell_o, body_o):
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, f"{sid}.glb"), 4000)


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
# brittle star (black Ophiocoma erinaceus, research 17 §2): unit arm span, disc at origin, sits on z = 0.
# Limb rig (SIMULATION §19): straight rest arms; the renderer bends each arm from core LimbRig data.
# COLOR_0 on arms: R = t along the arm (0 at the disc junction .. 1 tip), B = arm index / 9, A = 0.5 - x.
# Root.extras / Arms.extras `limbPivots` = 10 x vec4 (glTF space): arm-root xyz, w = rest arm length.
# ----------------------------------------------------------------------------
# (arm geometry and dimensions: brittle_geom.py, pure numpy, shared with tools that rebuild the arms without Blender)
BRITTLE_DISC_R = BG.DISC_R


def gltf_pt(p):
    """Blender (x, y, z) -> glTF (x, z, -y)."""
    return [float(p[0]), float(p[2]), float(-p[1])]


def build_brittle_star():
    """Black brittle star, Ophiocoma erinaceus (the usual trade 'black brittle star', research 17 §2.1): small
    pentagonal disc (≈ 1/9 of the span) with coarse granules and paired radial shields; five robust arms 4 disc
    diameters long, flattened, of short segments (dorsal arm plates, dark joints, faint lighter bands every few
    segments that read more distally), tapering to a whip tip; long, stout arm spines that shorten toward the tip;
    uniformly charcoal black above, a little paler with pale plate marks and orange tube feet below."""
    C.reset_scene()
    at = Atlas(512, 256, 4)
    nseg = BG.SEGMENTS

    def arm(cv):
        # strip u = along the arm (0 disc -> 1 tip), v = dorsal-ness (1 dorsal midline .. 0 ventral midline)
        cv.vgrad("#2A282C", "#121214")
        ph = (cv.u * nseg) % 1.0
        dpl = np.minimum(ph, 1 - ph)
        # dorsal arm plates: slightly raised, faintly lighter middles, dark joints between segments
        cv.blend(S(0.18, 0.45, dpl) * S(0.45, 0.8, cv.v), "#28282C", 0.6)
        cv.blend(1 - S(0.0, 0.13, dpl), "#060607", 0.85)
        # faint bands every ~6 segments, stronger toward the tip (many trade animals show them)
        band6 = 1 - S(0.0, 0.35, np.abs(((cv.u * nseg / 6.0) % 1.0) - 0.5) * 2)
        cv.blend(band6 * S(0.45, 0.9, cv.v) * (0.18 + 0.32 * cv.u), "#4A4850")
        # ventral: pale marks on the lower arm plates and a row of orange tube feet each side
        cv.blend((1 - S(0.0, 0.25, dpl)) * (1 - S(0.12, 0.22, cv.v)), "#8E8A92", 0.55)
        tf = np.exp(-(((ph - 0.5) / 0.12) ** 2 + ((cv.v - 0.3) / 0.05) ** 2))
        cv.blend(np.clip(tf * 1.4, 0, 1), "#C85A22", 0.7)
        cv.blend(S(0.9, 1.0, cv.u), "#2E2A2A", 0.5)
    at.paint_strip(0, arm)

    def disc(cv):
        # strip u = angle around the disc (arm k at u = k/5), v = radius (0 rim -> 1 centre)
        cv.vgrad("#1A1A1C", "#121214")
        gr = P.value_noise(cv.h, cv.w, 160, 28, 31)
        cv.multiply(0.72 + 0.5 * S(0.5, 0.8, gr))                       # coarse granules
        a5 = (cv.u * 5) % 1.0
        for c in (0.0 + 0.07, 1.0 - 0.07):                               # paired radial shields at each arm base
            m = np.exp(-(((a5 - c) / 0.05) ** 2 + ((cv.v - 0.16) / 0.1) ** 2))
            cv.blend(S(0.35, 0.6, m), "#2C2C30", 0.85)
        cv.blend((1 - S(0.0, 0.12, np.abs(a5 - 0.5))) * (1 - S(0.0, 0.25, cv.v)), "#0A0A0B", 0.6)  # interradial notch
        cv.blend(1 - S(0.0, 0.08, 1 - cv.v), "#0E0E10", 0.5)            # mouth side darker centre
    at.paint_strip(1, disc)

    def spine(cv):
        cv.vgrad("#1C1C20", "#3A3A40")       # black spines with slightly paler tips
    at.paint_strip(2, spine)
    tex = os.path.join(C.BUILD, "brittle_star_atlas.png")
    at.cv.save(tex)
    mat = C.material("brittle_mat", rough=0.55, image=C.load_image(tex, name="brittle_star"))

    pivots = BG.pivots()
    root = make_root("Root", dict(species="brittle_star", lengthCm=15.0, unit="arm span 1.0, disc at origin",
                                  arms=5, roughness=0.55, limbRig="brittle_arms", limbPivots=pivots,
                                  discToSpan=round(2 * BRITTLE_DISC_R, 3)))
    # ---- disc: flat pentagon, scalloped between the arms, low dome ----
    gd = G.Geo()
    Vd, Fd = G.ellipsoid((0, 0, 0.016), (BRITTLE_DISC_R, BRITTLE_DISC_R, 0.016), 18, 6)
    ang = np.arctan2(Vd[:, 1], Vd[:, 0])
    Vd[:, :2] *= (1 + 0.10 * np.cos(5 * ang) - 0.03 * np.cos(10 * ang))[:, None]
    Vd[:, 2] = np.maximum(Vd[:, 2], 0.004)
    rad = np.linalg.norm(Vd[:, :2], axis=1) / (BRITTLE_DISC_R * 1.1)
    top = Vd[:, 2] >= 0.016
    vv = np.where(top, 1 - np.clip(rad, 0, 1), 0.0)
    xs = Vd[:, 0]
    gd.add(Vd, Fd, at.uv(1, (ang / (2 * math.pi)) % 1, vv), np.stack([0 * xs, 0 * xs, 0 * xs, np.clip(0.5 - xs, 0, 1)], -1))
    disc_o = geo_obj("Body", gd, root, "body", [mat])
    # ---- arms: straight rest pose (the renderer bends them along the sim's pose), flattened, tapering, a pair of
    # spines at every ring ----
    ga = BG.arms(at.uv)
    geo_obj("Arms", ga, root, "leg", [mat], extras={"count": 5, "limbPivots": pivots})
    objs = [o for o in C.all_descendants(root) if o.type == "MESH"]
    C.recalc_normals(disc_o)
    return finish(root, objs, os.path.join(C.MODELS, "brittle_star.glb"), 2000)


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


_L, _H, _D = C.TANK
ASTERINA_GLASS = [  # default world placements (cm) on the glass: pos3, normal3 (pointing into the tank)
    (5.0, 14.0, _D, 0, 0, -1), (9.0, 22.0, _D, 0, 0, -1), (47.0, 9.0, _D, 0, 0, -1),
    (0.0, 27.0, 16.0, 1, 0, 0), (_L, 18.0, 26.0, -1, 0, 0), (88.0, 6.0, _D, 0, 0, -1)]


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
    m, rings = 40, 3
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
    return finish(root, [o], os.path.join(C.MODELS, "asterina.glb"), 400)


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
# Sally Lightfoot crab (Percnon gibbesi, research 17 §3): extremely flat carapace a little wider than long (CW 39.9 /
# CL 38.2 mm), maroon with a green-yellow midline and paired radiating lines; four pairs of long, flattened walking
# legs, maroon with yellow-gold blotches and yellow joint rings, a row of spines on the leading edge of each merus;
# small, equal red-orange chelae with pale spoon tips; short eye stalks at the front corners.
# Unit: carapace width 1.0 (= lengthCm 5), facing +X (it walks sideways along +-Y), feet on z = 0.
# Limb rig (SIMULATION §19): COLOR_0 R = t along the limb, B = limb / 9 (legs k = 2 pair + (0 left, 1 right), chelae
# 8 left, 9 right); extras.limbPivots = 10 x vec4 (glTF space): coxa xyz, w = leg length.
# ----------------------------------------------------------------------------
SALLY_LEG_ANGLES = (62.0, 86.0, 110.0, 134.0)    # leg directions from forward (deg)
SALLY_LEG_LEN = (0.95, 1.12, 1.12, 0.98)         # carapace widths
SALLY_BODY_Z = 0.15                              # carapace mid-height


def _squircle(phi, n=2.6):
    return (np.abs(np.cos(phi)) ** n + np.abs(np.sin(phi)) ** n) ** (-1.0 / n)


def build_sally_lightfoot():
    C.reset_scene()
    at = Atlas(512, 1024, 4)
    joints = (0.14, 0.47, 0.62, 0.86)

    def leg(cv):
        # u along the leg (0 coxa .. 1 dactyl tip), v around (0.25 dorsal)
        cv.vgrad("#4C0C18", "#6A1622")
        u = cv.u
        rs = np.random.default_rng(5)
        for _ in range(34):              # yellow-gold blotches on each segment, mostly on top
            cu, cvv = rs.uniform(0.05, 0.95), rs.uniform(0.15, 0.85)
            r = rs.uniform(0.014, 0.03)
            cv.blend(1 - S(r * 0.55, r, np.hypot((u - cu) * 1.6, (cv.v - cvv) * 0.35)), "#D8B030", 0.9)
        for j in joints:                 # yellow rings at the joints
            cv.blend(1 - S(0.007, 0.018, np.abs(u - j)), "#E8C838")
        cv.blend(S(0.88, 1.0, u), "#2A0A10", 0.75)      # dark dactyl tips
        cv.blend(S(0.965, 1.0, u), "#C89030", 0.6)      # pale claw-like tip
    at.paint_strip(0, leg)

    def cara(cv):
        # u 0.25..0.75 holds the top view: px along the body (front = 1), py side to side
        px = np.clip((cv.u - 0.25) / 0.5, 0, 1)
        X, Y = (px - 0.5) * 2, (cv.v - 0.5) * 2
        r = np.hypot(X, Y)
        cv.vgrad("#561020", "#561020")
        cv.blend(np.clip(0.5 + 0.5 * X, 0, 1) * 0.35, "#7A2030")
        cv.blend(S(0.78, 1.0, r), "#38080F", 0.7)
        cv.blend((1 - S(0.03, 0.06, np.abs(Y))) * (1 - S(0.8, 0.95, np.abs(X))), "#B8D040")   # green-yellow midline
        ang = np.arctan2(np.abs(Y), X)                                                         # paired (mirrored) lines
        lines = (1 - S(0.03, 0.08, np.abs(((ang / np.pi * 5 + 0.5) % 1.0) - 0.5))) * S(0.25, 0.42, r) * (1 - S(0.8, 0.93, r))
        cv.blend(np.clip(lines, 0, 1) * 0.6, "#98B838")
        cv.blend((1 - S(0.0, 0.04, np.abs(X - 0.92))) * (1 - S(0.5, 0.7, np.abs(Y))), "#B8C048", 0.5)  # front rim
        cv.multiply(0.9 + 0.2 * P.value_noise(cv.h, cv.w, 64, 16, 7))
    at.paint_strip(1, cara)

    def claw(cv):
        cv.vgrad("#A82E18", "#D04A22")    # red-orange chelipeds
        cv.blend(S(0.55, 0.75, cv.u), "#E06A30", 0.7)
        cv.blend(S(0.84, 0.97, cv.u), "#F2D8B0", 0.9)   # pale spoon tips
    at.paint_strip(2, claw)

    def stalk(cv):
        cv.vgrad("#6A1A20", "#C8A050")
    at.paint_strip(3, stalk)
    tex = os.path.join(C.BUILD, "sally_lightfoot_atlas.png")
    at.cv.save(tex)
    mat = C.material("sally_lightfoot_mat", rough=0.45, image=C.load_image(tex, name="sally_lightfoot"))
    meye = C.material("sally_lightfoot_eye", color="#0A0A0A", rough=0.1)

    def col(t, k, V):
        V = np.asarray(V)
        return np.stack([np.asarray(t, float) * np.ones(len(V)), np.zeros(len(V)), np.full(len(V), k / 9.0),
                         np.clip(0.5 - V[:, 0], 0, 1)], -1)

    pivots = [0.0] * 40
    # ---- carapace: flat squircle, slightly wider than long, low dome ----
    gb = G.Geo()
    Vb, Fb = G.ellipsoid((0, 0, SALLY_BODY_Z), (0.48, 0.5, 0.055), 18, 7)
    phi = np.arctan2(Vb[:, 1] / 0.5, Vb[:, 0] / 0.48)
    Vb[:, :2] *= np.minimum(_squircle(phi), 1.25)[:, None]
    Vb[:, 0] = np.where(Vb[:, 0] > 0.35, 0.35 + (Vb[:, 0] - 0.35) * 0.6, Vb[:, 0])   # straight front edge
    Vb[:, 2] = SALLY_BODY_Z + (Vb[:, 2] - SALLY_BODY_Z) * np.where(Vb[:, 2] > SALLY_BODY_Z, 1.0, 0.6)
    x0, y0, x1, y1 = at.rect(1)
    uvb = np.stack([(0.25 + 0.5 * np.clip(Vb[:, 0] / 0.96 + 0.5, 0, 1)) * (x1 - x0) / at.w,
                    1 - (y0 + np.clip(Vb[:, 1] + 0.5, 0, 1) * (y1 - y0)) / at.h], -1)
    gb.add(Vb, Fb, uvb, col(0.0, 0, Vb))
    root = make_root("Root", {})
    body_o = geo_obj("Body", gb, root, "shell", [mat])
    # ---- walking legs ----
    gl = G.Geo()
    for pair, (adeg, L) in enumerate(zip(SALLY_LEG_ANGLES, SALLY_LEG_LEN)):
        for side in (1, -1):              # +1 = left (+Y), -1 = right
            k = 2 * pair + (0 if side > 0 else 1)
            a = math.radians(adeg)
            d = np.array([math.cos(a), side * math.sin(a), 0.0])
            fwd = np.array([-d[1], d[0], 0.0])           # leg's leading (anterior) edge, in the leg plane
            fwd = fwd if fwd[0] >= 0 else -fwd
            rr = 0.46 * min(_squircle(math.atan2(side * math.sin(a) / 0.5, math.cos(a) / 0.48)), 1.25)
            base = d * rr * 0.9 + np.array([0, 0, SALLY_BODY_Z - 0.02])
            pts = smooth_path([base, base + d * 0.22 * L + [0, 0, 0.09], base + d * 0.5 * L + [0, 0, 0.16],
                               base + d * 0.78 * L + [0, 0, 0.07], base + d * 1.0 * L + [0, 0, -base[2] + 0.004]], 9)
            tt = np.linspace(0, 1, 9)
            R = 0.046 * (1 - 0.5 * tt) * np.where(tt < 0.5, 1.15, 1.0)
            Vt, Ft, tv, av = G.tube(pts, R, 6, squash=0.4, up=fwd)
            ts = np.r_[np.repeat(tt, 6), 1.0]
            gl.add(Vt, Ft, at.uv(0, ts, av), col(ts, k, Vt))
            for j in range(3):            # spines on the leading edge of the merus
                i = 2 + j
                b = pts[i] + fwd * R[i] * 0.9
                tip = b + fwd * 0.035 + d * 0.02
                Vc, Fc = G.cone(b, tip, 0.008, 3)
                gl.add(Vc, Fc, at.uv(0, np.full(len(Vc), joints[2])), col(tt[i], k, Vc))
            pivots[4 * k:4 * k + 4] = gltf_pt(base) + [float(L)]
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 8, "limbPivots": pivots})
    # ---- chelipeds: small, equal; merus/carpus forward and in, palm, spoon-tipped fingers toward the mouth ----
    gc = G.Geo()
    for side, k in ((1, 8), (-1, 9)):
        b = np.array([0.36, side * 0.17, SALLY_BODY_Z - 0.03])
        pts = smooth_path([b, b + [0.1, side * 0.1, 0.0], b + [0.2, side * 0.06, -0.03], b + [0.25, side * 0.0, -0.05]], 5)
        tt = np.linspace(0, 0.55, 5)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.035, 0.03, 5), 6, cap_end=False, up=(0, 0, 1))
        gc.add(Vt, Ft, at.uv(2, np.repeat(tt, 6), av), col(np.repeat(tt, 6), k, Vt))
        pc = pts[-1] + np.array([0.03, -side * 0.025, -0.01])
        Vp, Fp = G.ellipsoid(pc, (0.05, 0.03, 0.028), 6, 4, rot=G.rot_z(-side * 0.5))
        gc.add(Vp, Fp, at.uv(2, np.full(len(Vp), 0.7)), col(0.75, k, Vp))
        for dz in (0.012, -0.012):        # fingers with spoon tips
            fb = pc + np.array([0.035, -side * 0.03, dz])
            ft = fb + np.array([0.045, -side * 0.035, -0.012])
            Vf, Ff = G.cone(fb, ft, 0.012, 4)
            gc.add(Vf, Ff, at.uv(2, np.r_[np.full(4, 0.85), 0.95]), col(np.r_[np.full(4, 0.9), 1.0], k, Vf))
        pivots[4 * k:4 * k + 4] = gltf_pt(b) + [0.3]
    geo_obj("Claws", gc, root, "leg", [mat], extras={"claw": True, "limbPivots": pivots})
    # ---- eye stalks at the front corners ----
    ge = G.Geo()
    for side in (1, -1):
        b = np.array([0.4, side * 0.3, SALLY_BODY_Z + 0.03])
        tip = b + np.array([0.06, side * 0.03, 0.05])
        Vt, Ft, tv, av = G.tube([b, tip], [0.024, 0.02], 6, cap_end=False)
        ge.add(Vt, Ft, at.uv(3, tv, av), col(0, 0, Vt), mat=0)
        Ve, Fe = G.ellipsoid(tip, (0.03, 0.028, 0.03), 6, 4)
        ge.add(Ve, Fe, None, col(0, 0, Ve), mat=1)
    geo_obj("Eyes", ge, root, "eye", [mat, meye], extras={"pivot": gltf_pt((0.43, 0.0, SALLY_BODY_Z + 0.05))})
    C.set_extras(root, species="sally_lightfoot", lengthCm=5.0,
                 unit="carapace width 1.0, crab faces +X and walks sideways (+-Y)",
                 iridescence=0.05, roughness=0.45, gait="sideways", grazes="hair_algae",
                 limbRig="crab_legs", limbPivots=pivots)
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
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
