"""Stony corals (docs/research/stony_corals.md): hammer, torch, frogspawn, duncan, acan, candy cane, open brain, bubble.

These corals are not in Tee's tank, so they have no anchor on its rock: every GLB is built with the colony base at
STUDIO (core StonyCorals.STUDIO) and the app draws each colony as the model moved by its offset (a copy). Same GLB
layout as corals.py (Root -> body parts + instanced polyp nodes), plus:

  body COLOR_0.a  softness: 0 skeleton (static.vert neither shrinks nor sways it, softcoral.frag shows it as bare
                  white limestone where the tissue has died), 0.35 fleshy mantle on the skeleton, 1 soft tissue
  polyp extras.polypMode = 1  night tentacles (Euphyllia sweepers, duncan feeding tentacles): polyp.vert grows them
                  out with the colony's CoralPose.tentacles after dark
  polyp extras.polypMode = 2  Euphyllia day tentacles: each strand bends as a whole and shortens as it retracts
  polyp extras.polypMode = 3  inflating tissue (bubble vesicles, the open brain mantle): swells by day, deflates as
                  the night tentacles come out

  python tools/blender/stony_corals.py [hammer torch frogspawn duncan]
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import corals as K  # noqa: E402
import geomlib as G  # noqa: E402
import paintlib as P  # noqa: E402
import tank as T  # noqa: E402
from common import bpy  # noqa: E402,F401

S = P.smoothstep
UP = np.array([0.0, 1.0, 0.0])
RS = np.random.default_rng
unit = K.unit

STUDIO = np.array([60.0, 10.0, 20.0])   # core StonyCorals.STUDIO: colony base
SKELETON = 0.0
MANTLE = 0.35


# ----------------------------------------------------------------------------
# shared pieces
# ----------------------------------------------------------------------------
def ball(c, r, seg=6, rings=4, v0=0.9, hinge=1.0, flag=1.0):
    """Small UV sphere (tentacle tips): V, F, UV, COL lists."""
    V, F, UV, COL = [], [], [], []
    for i in range(rings + 1):
        th = math.pi * i / rings
        for k in range(seg):
            ph = 2 * math.pi * k / seg
            V.append((c[0] + r[0] * math.sin(th) * math.cos(ph), c[1] + r[1] * math.cos(th), c[2] + r[2] * math.sin(th) * math.sin(ph)))
            UV.append((k / seg, v0 + (1 - v0) * (1 - i / rings)))
            COL.append((flag, 0, hinge, 1))
    for i in range(rings):
        for k in range(seg):
            a, b = i * seg + k, i * seg + (k + 1) % seg
            F.append((a, b, b + seg))
            F.append((a, b + seg, a + seg))
    return V, F, UV, COL


def merge(*parts):
    V, F, UV, COL = [], [], [], []
    for pv, pf, puv, pc in parts:
        b = len(V)
        V += list(map(tuple, pv))
        F += [tuple(i + b for i in f) for f in pf]
        UV += list(map(tuple, puv))
        COL += list(map(tuple, pc))
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def strand(length, r0, r1, sides=5, rings=4, flag=1.0, curve=0.0, v1=0.9):
    """A tapering tentacle along +Y (local cm, base at the origin): COLOR_0.r = flag (1: polyp.vert long-tentacle
    mode), b = hinge 0 base .. 1 tip; UV v runs 0.05 at the base to v1 at the tip (u around)."""
    V, F, UV, COL = [], [], [], []
    for i in range(rings + 1):
        h = i / rings
        r = r0 + (r1 - r0) * h
        x = curve * h * h * length
        for k in range(sides):
            a = 2 * math.pi * k / sides
            V.append((x + r * math.cos(a), length * h, r * math.sin(a)))
            UV.append((k / sides, 0.05 + (v1 - 0.05) * h))
            COL.append((flag, 0, h, 1))
    for i in range(rings):
        for k in range(sides):
            a, b = i * sides + k, i * sides + (k + 1) % sides
            F.append((a, b, b + sides, a + sides))
    tip = (curve * length, length, 0.0)
    return (V, F, UV, COL), tip


def tentacle_canvas(body, mid, tip, seed, stripe=None, w=32, h=128):
    """u around the strand, v base (0) -> tip (1): body colour, a lighter band, the tip colour from v 0.85."""
    cv = P.Canvas(w, h, body)
    cv.multiply(0.88 + 0.24 * P.fbm(h, w, 3, 3, seed))
    cv.blend(S(0.3, 0.75, cv.v), mid, 0.8)
    if stripe:
        cv.blend(S(0.55, 0.9, np.sin(cv.v * 70.0)) * S(0.15, 0.4, cv.v) * (1 - S(0.75, 0.85, cv.v)), stripe, 0.35)
    cv.blend(S(0.84, 0.9, cv.v), tip)
    return cv


def frag_base(g, seed, radii=(2.4, 1.1, 2.0), mat=0):
    """The bit of rubble the frag grew on, its foot at STUDIO (rigid)."""
    c = STUDIO + np.array([0.0, radii[1] * 0.35, 0.0])
    V, F = K.rubble(c, radii, seed, segs=16, rings=8)
    d = V - c
    g.add(V, F, np.c_[np.arctan2(d[:, 2], d[:, 0]) / (2 * math.pi) * 2 + 1.0,
                      np.arctan2(d[:, 1], np.hypot(d[:, 0], d[:, 2])) / math.pi + 0.5], (0.1, 0, 0, SKELETON), mat=mat)
    return float(V[:, 1].max()) - 0.25


def rubble_material(name):
    cv = P.Canvas(64, 64, "#A69A86")
    cv.multiply(0.75 + 0.4 * P.fbm(64, 64, 6, 4, 9))
    cv.blend(S(0.55, 0.7, P.fbm(64, 64, 4, 3, 10)), "#A87894", 0.6)
    f1, _, _ = P.voronoi(64, 64, 30, 12)
    cv.blend(1 - S(1.5, 3.0, f1), "#4A4038", 0.5)
    return C.material(name, rough=0.9, image=K.tex(name, cv, jpeg=True))


def corallite(g, pts, r0, r1, rim, seed, sides=14, ribs=7, mat=0, tissue_from=0.65):
    """A ribbed skeleton tube along pts (r0 at the foot, r1 near the top) that flares into an open corallite cup of
    radius rim, its septa (ribs) running up the outside. UV: u around, v 0 foot .. 1 rim; COLOR_0.a = skeleton."""
    pts = np.asarray(pts, float)
    n = len(pts)
    R = np.linspace(r0, r1, n)
    R[-1] = rim
    R[-2] = 0.5 * (R[-3] + rim)
    Tn, N, B = G.frames(pts, (1, 0, 0))
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))]
    tt = seg / max(seg[-1], 1e-9)
    V, UV = [], []
    for i in range(n):
        for k in range(sides):
            a = 2 * math.pi * k / sides
            rib = 1 + 0.09 * math.cos(ribs * a)
            V.append(pts[i] + R[i] * rib * (math.cos(a) * N[i] + math.sin(a) * B[i]))
            UV.append((k / sides, tt[i] * 0.9))
    # inner wall of the cup, down to the mouth of the corallite
    top, nrm = pts[-1], Tn[-1]
    for depth, rr in ((0.25, 0.7), (0.55, 0.35)):
        for k in range(sides):
            a = 2 * math.pi * k / sides
            V.append(top - nrm * depth * rim + rr * rim * (math.cos(a) * N[-1] + math.sin(a) * B[-1]))
            UV.append((k / sides, 0.95))
    F = []
    rows = n + 2
    for i in range(rows - 1):
        for k in range(sides):
            a, b = i * sides + k, i * sides + (k + 1) % sides
            F.append((a, b, b + sides, a + sides))
    g.add(np.array(V), F, np.array(UV), (0.3, 0, 0, SKELETON), mat=mat)


def skeleton_canvas(name, bare, tissue, seed, tissue_from=0.6):
    """Corallite texture: pale ribbed limestone below, living tissue (colour) over the top part, v 0 foot -> 1 rim."""
    cv = P.Canvas(64, 128, bare)
    cv.multiply(0.86 + 0.2 * P.fbm(128, 64, 5, 3, seed))
    cv.blend(S(0.55, 0.95, np.cos(cv.u * 2 * math.pi * 7)) * 0.8, "#8E877A", 0.35)   # shadow between the ribs
    cv.blend(S(tissue_from - 0.1, tissue_from + 0.08, cv.v), tissue)
    cv.multiply(0.92 + 0.12 * S(tissue_from, 1.0, cv.v))
    return C.material(name, rough=0.7, image=K.tex(name, cv, jpeg=True))


def dome_cap(g, c, n, rx, rz, h, seed, rings=4, segs=14, mat=0, soft=MANTLE):
    """Fleshy mantle cap over a corallite mouth: an ellipse (rx, rz) domed h high about normal n."""
    t1 = unit(np.cross(n, [0.0, 0.0, 1.0]) if abs(n[2]) < 0.9 else np.cross(n, [1.0, 0.0, 0.0]))
    t2 = np.cross(n, t1)
    V, UV = [c + n * h], [(0.5, 0.5)]
    rs = RS(seed)
    wob = rs.uniform(0, 6.28, 3)
    for i in range(1, rings + 1):
        t = i / rings
        for k in range(segs):
            a = 2 * math.pi * k / segs
            w = 1 + 0.08 * math.sin(3 * a + wob[0]) + 0.05 * math.sin(5 * a + wob[1])
            p = c + (t1 * math.cos(a) * rx + t2 * math.sin(a) * rz) * t * w + n * h * (1 - t * t)
            V.append(p)
            UV.append((0.5 + 0.5 * t * math.cos(a), 0.5 + 0.5 * t * math.sin(a)))
    F = [(0, 1 + (k + 1) % segs, 1 + k) for k in range(segs)]
    for i in range(rings - 1):
        for k in range(segs):
            a, b = 1 + i * segs + k, 1 + i * segs + (k + 1) % segs
            F.append((a, b, b + segs, a + segs))
    g.add(np.array(V), F, np.array(UV), (0.6, 0, 0.2, soft), mat=mat)
    return t1, t2


def head_points(c, n, rx, rz, h, count, seed, edge_tilt=0.9):
    """Tentacle bases spread over a head (sunflower over the ellipse), with directions fanning out at the rim."""
    t1 = unit(np.cross(n, [0.0, 0.0, 1.0]) if abs(n[2]) < 0.9 else np.cross(n, [1.0, 0.0, 0.0]))
    t2 = np.cross(n, t1)
    rs = RS(seed)
    out = []
    ga = math.pi * (3 - math.sqrt(5))
    for i in range(count):
        t = math.sqrt((i + 0.5) / count)
        a = i * ga + rs.normal(0, 0.15)
        p = c + (t1 * math.cos(a) * rx + t2 * math.sin(a) * rz) * t + n * h * (1 - t * t) * 0.95
        d = unit(n + (t1 * math.cos(a) + t2 * math.sin(a)) * t * edge_tilt)
        out.append((p, d, t, a))
    return out


def euphyllia(cfg):
    """A branching Euphyllia frag: a few corallites rising from rubble, each head carpeted in day tentacles
    (long-tentacle mode: they writhe, sway and shorten as they retract) and ringed by sweepers (night tentacles)."""
    rs = RS(cfg["seed"])
    g = T.WGeo()
    top = frag_base(g, cfg["seed"], mat=0)
    heads = []
    for hi, (dx, dz, hgt, lean) in enumerate(cfg["heads"]):
        foot = STUDIO + np.array([dx * 0.35, top - STUDIO[1], dz * 0.35])
        end = STUDIO + np.array([dx, top - STUDIO[1] + hgt, dz])
        mid = (foot + end) / 2 + np.array([lean * 0.4, 0.0, -lean * 0.3])
        pts = [foot + (mid - foot) * t for t in (0.0, 0.5)] + [mid + (end - mid) * t for t in (0.5, 0.85, 1.0)]
        pts = np.array(pts)
        corallite(g, pts, cfg["stalk"] * 1.15, cfg["stalk"], cfg["rim"], cfg["seed"] + hi, mat=1)
        n = unit(pts[-1] - pts[-2] + UP * 0.6)
        heads.append((pts[-1], n))
    for hi, (c, n) in enumerate(heads):
        dome_cap(g, c - n * 0.15, n, cfg["rim"] * 0.95, cfg["rim"] * 0.85 * cfg.get("oval", 1.0), cfg["rim"] * 0.35, cfg["seed"] + 40 + hi, mat=2)
    mats = [rubble_material(cfg["name"] + "_rubble"),
            skeleton_canvas(cfg["name"] + "_skeleton", "#E4E0D6", cfg["tissue"], cfg["seed"] + 5, cfg.get("tissue_from", 0.62)),
            C.material(cfg["name"] + "_mantle", color=cfg["mantle"], rough=0.55)]
    # day tentacles
    inst = K.Instances(cfg["seed"] + 100)
    sweep = K.Instances(cfg["seed"] + 200)
    for hi, (c, n) in enumerate(heads):
        for p, d, t, a in head_points(c, n, cfg["rim"] * 0.9, cfg["rim"] * 0.8 * cfg.get("oval", 1.0), cfg["rim"] * 0.3,
                                      cfg["per_head"], cfg["seed"] + 300 + hi):
            # no two tentacles point quite the same way (the mass reads soft, not combed)
            d = unit(d + rs.normal(0, 0.22, 3) + (d - n) * 0.6)
            inst.add(p, d, rs.uniform(0.8, 1.1) * (1.0 - 0.2 * t), phase=(hi * 0.31 + rs.normal(0, 0.08)) % 1)
        # sweepers: from the head rim, leaning well out over the neighbours
        for k in range(cfg["sweepers"]):
            a = 2 * math.pi * (k + rs.uniform(-0.3, 0.3)) / cfg["sweepers"]
            t1 = unit(np.cross(n, [0.0, 0.0, 1.0]))
            t2 = np.cross(n, t1)
            out = t1 * math.cos(a) + t2 * math.sin(a)
            sweep.add(c + out * cfg["rim"] * 0.85, unit(out * 1.4 + n * 0.5), rs.uniform(0.8, 1.15))
    proto = cfg["proto"]()
    tcv = tentacle_canvas(cfg["t_body"], cfg["t_mid"], cfg["t_tip"], cfg["seed"] + 7, stripe=cfg.get("t_stripe"))
    tm = C.material(cfg["name"] + "_tentacle", rough=0.45, image=K.tex(cfg["name"] + "_tentacle", tcv),
                    emissive=cfg["glow"], emissive_strength=1.0)
    (sv, sf, suv, sc), stip = strand(cfg["sweeper_len"], 0.07, 0.035, sides=4, rings=5, flag=0.0, curve=0.15)
    bv, bf, buv, bc = ball(stip, (0.08, 0.1, 0.08), 5, 3)
    sproto = merge((sv, sf, suv, sc), (bv, bf, buv, bc))
    scv = tentacle_canvas("#B8A890", "#D2C6B2", "#F2EEE4", cfg["seed"] + 8)
    sm = C.material(cfg["name"] + "_sweeper", rough=0.5, image=K.tex(cfg["name"] + "_sweeper", scv))
    root = C.empty("Root")
    C.set_extras(root, species=cfg["species"], stony=1)
    body = g.obj("Body", root, "body", mats, smooth=True)
    C.recalc_normals(body)
    pol = K.polyp_node("Polyps", root, proto, [tm], inst, extras={"polypMode": 2})
    swp = K.polyp_node("Sweepers", root, sproto, [sm], sweep, extras={"polypMode": 1})
    nt = len(proto[1]) * 2
    return root, [body, pol, swp], {"Polyps": (len(inst), nt), "Sweepers": (len(sweep), len(sproto[1]) * 2)}


# ----------------------------------------------------------------------------
# tentacle prototypes
# ----------------------------------------------------------------------------
def proto_hammer():
    """Hammer: a short thick tentacle ending in a crescent crossbar (the hammer / anchor tip)."""
    s, tip = strand(2.3, 0.17, 0.13, sides=5, rings=3, curve=0.06)
    # crossbar: an ellipsoid across the tip, its ends drooping a little (anchor shape)
    bv, bf, buv, bc = ball(tip, (0.42, 0.15, 0.17), 6, 3, v0=0.86)
    bv = [(x, y - 0.3 * ((x - tip[0]) / 0.42) ** 2, z) for x, y, z in bv]
    return merge(s, (bv, bf, buv, bc))


def proto_torch():
    """Torch: a long, slim, gently curving tentacle with a round tip."""
    s, tip = strand(4.6, 0.14, 0.08, sides=5, rings=5, curve=0.12)
    return merge(s, ball(tip, (0.17, 0.18, 0.17), 5, 3))


def proto_frogspawn():
    """Frogspawn: a tentacle that splits near its end into short knobby branches, each with a bead tip."""
    s, tip = strand(2.0, 0.15, 0.11, sides=5, rings=3)
    parts = [s, ball(tip, (0.14, 0.14, 0.14), 3, 2, v0=0.86)]
    for k in range(3):
        a = 2 * math.pi * k / 3 + 0.4
        d = np.array([math.cos(a), 1.1, math.sin(a)])
        d /= np.linalg.norm(d)
        e = np.array(tip) + d * 0.42
        sv, sf, suv, sc = strand(0.42, 0.075, 0.065, sides=3, rings=1, v1=0.88)[0]
        # rotate the little strand from +Y onto d and move it to the tip
        q = K.q_from_up(d)
        sv = [tuple(np.array(tip) + K.q_rotate(q, np.array(v))) for v in sv]
        sc = [(1.0, 0, 0.85 + 0.15 * c[2], 1) for c in sc]
        parts += [(sv, sf, suv, sc), ball(tuple(e), (0.13, 0.13, 0.13), 3, 2, v0=0.88)]
    return merge(*parts)


# ----------------------------------------------------------------------------
# Euphyllia (stony_corals.md §1-3)
# ----------------------------------------------------------------------------
EUPHYLLIA = {
    # [retail]: branching hammer frags of 2-4 heads; teal-green tentacles with fluorescent lime crossbar tips
    "hammer": dict(name="hammer", species="fimbriaphyllia_ancora", seed=501, proto=proto_hammer,
                   heads=[(-2.6, 0.6, 4.4, 0.6), (2.3, -0.9, 3.8, -0.6), (0.2, 2.4, 3.0, 0.2)],
                   stalk=0.6, rim=1.7, oval=1.25, per_head=44, sweepers=4, sweeper_len=7.0,
                   tissue="#6E7A56", mantle="#4E7E6A", t_body="#3E8C78", t_mid="#5FB39A", t_tip="#B8F25A",
                   t_stripe="#2E6A5A", glow="#2F7A1E"),
    # torch: long flowing green-brown tentacles with glowing yellow-green tips; the hardest stinger
    "torch": dict(name="torch", species="euphyllia_glabrescens", seed=511, proto=proto_torch,
                  heads=[(-2.3, 0.4, 4.0, 0.5), (2.0, -0.7, 3.2, -0.5), (0.0, 2.1, 2.6, 0.0)],
                  stalk=0.55, rim=1.45, per_head=40, sweepers=5, sweeper_len=10.0,
                  tissue="#6E6A44", mantle="#5E6038", t_body="#5A6438", t_mid="#76844A", t_tip="#D8F070",
                  glow="#2E5A0A"),
    # frogspawn: green tentacles splitting into pale knobby tips with a pink cast
    "frogspawn": dict(name="frogspawn", species="fimbriaphyllia_divisa", seed=521, proto=proto_frogspawn,
                      heads=[(-2.5, 0.7, 4.1, 0.5), (2.3, -0.6, 3.5, -0.5), (0.0, 2.4, 2.8, 0.1)],
                      stalk=0.6, rim=1.65, oval=1.15, per_head=40, sweepers=4, sweeper_len=8.0,
                      tissue="#6A7A58", mantle="#5E7E5E", t_body="#5E8A5C", t_mid="#86A876", t_tip="#E8B8CC",
                      t_stripe="#4A6E48", glow="#2A6A20"),
}


# ----------------------------------------------------------------------------
# Duncan (stony_corals.md §4)
# ----------------------------------------------------------------------------
def proto_duncan_disc(r=0.9):
    """Duncan head: a flat oral disc (green, fluorescent centre ring) ringed by 18 short tentacles. Ordinary polyp
    mode: the tentacles fold in and the disc shrinks as it closes."""
    V, F, UV, COL = [(0, 0.12, 0)], [], [(0.75, 0.5)], [(0, 0, 0.2, 1)]
    segs = 24
    for ring, (rr, y) in enumerate(((0.45, 0.07), (1.0, 0.03))):
        for k in range(segs):
            a = 2 * math.pi * k / segs
            V.append((rr * r * math.cos(a), y, rr * r * math.sin(a)))
            UV.append((0.75 + 0.24 * rr * math.cos(a), 0.5 + 0.48 * rr * math.sin(a)))
            COL.append((0, 0, 0.25 + 0.1 * ring, 1))
    for k in range(segs):
        F.append((0, 1 + (k + 1) % segs, 1 + k))
        a, b = 1 + k, 1 + (k + 1) % segs
        F.append((a, b, b + segs, a + segs))
    # under-side skirt down into the corallite
    b0 = len(V)
    for k in range(segs):
        a = 2 * math.pi * k / segs
        V.append((0.7 * r * math.cos(a), -0.25, 0.7 * r * math.sin(a)))
        UV.append((0.2, 0.2))
        COL.append((0, 0, 0.0, 1))
    for k in range(segs):
        a, b = 1 + segs + k, 1 + segs + (k + 1) % segs
        F.append((b, a, b0 + k, ))
        F.append((b, b0 + k, b0 + (k + 1) % segs))
    parts = [(V, F, UV, COL)]
    for k in range(segs):
        a = 2 * math.pi * (k + 0.5) / segs
        d = np.array([math.cos(a) * 0.9, 0.45 + 0.25 * (k % 2), math.sin(a) * 0.9])
        d /= np.linalg.norm(d)
        (sv, sf, suv, sc), tip = strand(0.75 + 0.2 * (k % 2), 0.05, 0.022, sides=3, rings=2, flag=0.0, v1=0.45)
        q = K.q_from_up(d)
        base = np.array([math.cos(a) * r * 0.95, 0.05, math.sin(a) * r * 0.95])
        sv = [tuple(base + K.q_rotate(q, np.array(v))) for v in sv]
        sc = [(0, 0, 0.35 + 0.65 * c[2], 1) for c in sc]
        suv = [(0.1 + 0.3 * u, 0.55 + v * 0.8) for u, v in suv]
        parts.append((sv, sf, suv, sc))
    return merge(*parts)


def duncan():
    rs = RS(531)
    g = T.WGeo()
    top = frag_base(g, 531, radii=(2.2, 1.0, 1.9))
    heads = []
    # [retail]: 1 in frags with 3-5 heads; ours is a small colony of 7 tubes, new heads budding off the older ones
    spec = [(-0.9, 0.4, 3.2, 0.5), (0.8, -0.5, 3.6, -0.4), (0.1, 0.9, 2.6, 0.1), (-0.2, -1.0, 2.4, 0.3),
            (1.3, 0.6, 2.0, -0.2), (-1.4, -0.4, 1.8, 0.4)]
    for i, (dx, dz, h, lean) in enumerate(spec):
        foot = STUDIO + np.array([dx * 0.4, top - STUDIO[1], dz * 0.4])
        end = STUDIO + np.array([dx * 1.3 + lean, top - STUDIO[1] + h, dz * 1.3])
        pts = np.array([foot + (end - foot) * t + np.array([math.sin(t * 3 + i) * 0.15, 0, 0]) for t in (0, 0.35, 0.7, 0.9, 1.0)])
        corallite(g, pts, 0.42, 0.4, 0.62, 540 + i, sides=12, ribs=6, mat=1)
        heads.append((pts[-1], unit(pts[-1] - pts[-2] + UP * 0.8)))
    # one small daughter bud on the side of the tallest tube (how the colony grows new heads)
    c0, n0 = heads[1]
    bud_foot = c0 - n0 * 1.6 + np.array([0.3, 0, 0.2])
    bud_end = bud_foot + unit(np.array([0.8, 0.9, 0.3])) * 1.0
    corallite(g, np.array([bud_foot, bud_foot + (bud_end - bud_foot) * 0.5, bud_end * 0.9 + bud_foot * 0.1, bud_end]),
              0.3, 0.3, 0.45, 549, sides=10, ribs=5, mat=1)
    heads.append((bud_end, unit(bud_end - bud_foot)))
    mats = [rubble_material("duncan_rubble"), skeleton_canvas("duncan_skeleton", "#DCD6C8", "#6A5E44", 547, 0.6)]
    inst = K.Instances(551)
    night = K.Instances(552)
    for i, (c, n) in enumerate(heads):
        sc = 0.75 if i == len(heads) - 1 else rs.uniform(0.95, 1.1)
        inst.add(c + n * 0.05, n, sc, phase=rs.uniform())
        # night feeding tentacles: 8 longer ones from under the disc rim
        t1 = unit(np.cross(n, [0.0, 0.0, 1.0]))
        t2 = np.cross(n, t1)
        for k in range(8):
            a = 2 * math.pi * (k + rs.uniform(-0.2, 0.2)) / 8
            out = t1 * math.cos(a) + t2 * math.sin(a)
            night.add(c + out * 0.75 * sc + n * 0.05, unit(out * 1.1 + n * 0.7), sc * rs.uniform(0.85, 1.1))
    # disc texture: u >= 0.5 oral disc (green ring, dark mouth); u < 0.5 tentacles (v 0.55 base .. 1 tip)
    cv = P.Canvas(64, 64, "#5E7A5A")
    rr = np.hypot((cv.u - 0.75) / 0.25, (cv.v - 0.5) / 0.5)
    cv.blend((cv.u >= 0.5) * 1.0, "#3E6A4A")
    cv.blend((cv.u >= 0.5) * S(0.25, 0.4, rr) * (1 - S(0.55, 0.7, rr)), "#7CE89A")
    cv.blend((cv.u >= 0.5) * (1 - S(0.08, 0.16, rr)), "#2A3424")
    cv.blend((cv.u >= 0.5) * S(0.8, 0.95, rr), "#6E8A62")
    cv.blend((cv.u < 0.5) * (1 - S(0.3, 0.45, cv.v)), "#4A4232")       # skirt: brown tissue down into the tube
    cv.blend((cv.u < 0.5) * S(0.55, 1.0, cv.v), "#8FB27E", 0.7)
    cv.blend((cv.u < 0.5) * S(0.85, 0.98, cv.v), "#D8E8B8")
    dm = C.material("duncan_polyp", rough=0.5, image=K.tex("duncan_polyp", cv), emissive="#1E7A3A", emissive_strength=1.0)
    (sv, sf, suv, sc_), stip = strand(1.6, 0.05, 0.025, sides=4, rings=4, flag=0.0, curve=0.2)
    nproto = merge((sv, sf, suv, sc_), ball(stip, (0.05, 0.06, 0.05), 4, 2))
    ncv = tentacle_canvas("#7A8A6A", "#A8B898", "#E8EED8", 553)
    nm = C.material("duncan_night_tentacle", rough=0.5, image=K.tex("duncan_night_tentacle", ncv))
    proto = proto_duncan_disc()
    root = C.empty("Root")
    C.set_extras(root, species="duncanopsammia_axifuga", stony=1)
    body = g.obj("Body", root, "body", mats, smooth=True)
    C.recalc_normals(body)
    pol = K.polyp_node("Polyps", root, proto, [dm], inst)
    nig = K.polyp_node("NightTentacles", root, nproto, [nm], night, extras={"polypMode": 1})
    return root, [body, pol, nig], {"Polyps": (len(inst), len(proto[1]) * 2), "NightTentacles": (len(night), len(nproto[1]) * 2)}


# ----------------------------------------------------------------------------
# pack 2 shared pieces
# ----------------------------------------------------------------------------
def cushion(rx, rz, h, rings=6, segs=24, lobes=None, waist=0.0, rim_v=0.98, flag=0.0):
    """A fleshy cushion along +Y (local cm, base at the origin): an ellipse (rx, rz) swollen h high, its outline
    optionally pinched at the waist (meandroid brains) and wavy (lobes = (count, depth)). UV: polar (u 0.5 + r cos,
    v 0.5 + r sin, r = 0.5 at the rim); COLOR_0.b = hinge (0 centre .. 1 rim, so the ordinary mode folds the rim)."""
    V, UV, COL = [(0.0, h, 0.0)], [(0.5, 0.5)], [(flag, 0, 0.0, 1)]
    for i in range(1, rings + 1):
        t = i / rings
        for k in range(segs):
            a = 2 * math.pi * k / segs
            w = 1.0 - waist * math.sin(a) ** 2 * (1 - abs(math.cos(a)))   # pinched across z at the middle
            if lobes:
                w *= 1 + lobes[1] * math.sin(lobes[0] * a)
            x, z = rx * w * t * math.cos(a), rz * w * t * math.sin(a)
            y = h * (1 - t ** 2.4) + 0.08 * h * math.sin(t * math.pi)
            V.append((x, y, z))
            UV.append((0.5 + 0.5 * rim_v * t * math.cos(a), 0.5 + 0.5 * rim_v * t * math.sin(a)))
            COL.append((flag, 0, t, 1))
    # under-lip tucked back toward the base so the edge reads thick
    for k in range(segs):
        a = 2 * math.pi * k / segs
        x, y, z = V[1 + (rings - 1) * segs + k]
        V.append((x * 0.9, -0.06 * h, z * 0.9))
        UV.append((0.5 + 0.5 * math.cos(a), 0.5 + 0.5 * math.sin(a)))
        COL.append((flag, 0, 0.9, 1))
    F = [(0, 1 + (k + 1) % segs, 1 + k) for k in range(segs)]
    for i in range(rings):
        for k in range(segs):
            a, b = 1 + i * segs + k, 1 + i * segs + (k + 1) % segs
            F.append((a, b, b + segs, a + segs))
    return V, F, UV, COL


def night_ring(inst, c, n, r, count, rs, scale=1.0, lift=0.6):
    """Night feeding tentacles from around a polyp mouth (polypMode 1)."""
    t1 = unit(np.cross(n, [0.0, 0.0, 1.0]) if abs(n[2]) < 0.9 else np.cross(n, [1.0, 0.0, 0.0]))
    t2 = np.cross(n, t1)
    for k in range(count):
        a = 2 * math.pi * (k + rs.uniform(-0.25, 0.25)) / count
        out = t1 * math.cos(a) + t2 * math.sin(a)
        inst.add(c + out * r, unit(out * 1.1 + n * lift), scale * rs.uniform(0.85, 1.1))


def night_proto(length, r0=0.045, r1=0.02, curve=0.2):
    (sv, sf, suv, sc), tip = strand(length, r0, r1, sides=4, rings=4, flag=0.0, curve=curve)
    return merge((sv, sf, suv, sc), ball(tip, (r0 * 1.1, r0 * 1.2, r0 * 1.1), 4, 2))


def mound(g, top, rx, rz, h, seed, mat=1):
    """Low encrusting skeleton dome over the rubble (rigid), UV polar like a corallite rim."""
    V, F, UV, COL = cushion(rx, rz, h, rings=5, segs=20)
    rs = RS(seed)
    V = np.array(V) + np.array([STUDIO[0], top - 0.1, STUDIO[2]])
    V[:, 1] += rs.normal(0, 0.04, len(V))
    g.add(V, F, np.array(UV) * np.array([1.0, 0.9]), (0.3, 0, 0, SKELETON), mat=mat)


# ----------------------------------------------------------------------------
# Acan (stony_corals.md, pack 2): an encrusting carpet of plump two-tone polyps
# ----------------------------------------------------------------------------
def acan():
    rs = RS(601)
    g = T.WGeo()
    top = frag_base(g, 601, radii=(2.6, 0.9, 2.2))
    mound(g, top, 2.3, 2.0, 0.8, 602)
    mats = [rubble_material("acan_rubble"), skeleton_canvas("acan_skeleton", "#D8D0C2", "#5A2A2C", 603, 0.3)]
    inst = K.Instances(604)
    night = K.Instances(605)
    # [retail]: frags of ~3 cm with 4+ polyps; ours is a 6 cm colony of 11, packed so neighbours just touch
    centres = [(0.0, 0.0)] + [(1.45 * math.cos(a), 1.3 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 7)[:-1]] \
        + [(2.35 * math.cos(a + 0.5), 2.0 * math.sin(a + 0.5)) for a in np.linspace(0, 2 * math.pi, 5)[:-1]]
    for i, (dx, dz) in enumerate(centres):
        t = math.hypot(dx / 2.9, dz / 2.5)
        y = top - 0.1 + 0.9 * (1 - min(t, 1) ** 2.4)
        c = STUDIO * np.array([1, 0, 1]) + np.array([dx, y, dz])
        n = unit(np.array([dx * 0.35, 1.0, dz * 0.35]))
        sc = rs.uniform(0.9, 1.08) * (0.85 if i >= 7 else 1.0)
        inst.add(c, n, sc, phase=rs.uniform())
        night_ring(night, c + n * 0.25, n, 0.25 * sc, 6, rs, sc)
    # polyp: puffy disc, red outer mantle, green ring, orange eye (the 'rainbow acan' look); u/v polar
    V, F, UV, COL = cushion(0.8, 0.76, 0.6, rings=6, segs=20, lobes=(6, 0.04))
    cv = P.Canvas(64, 64, "#A8282E")
    r = np.hypot(cv.u - 0.5, cv.v - 0.5) * 2
    cv.multiply(0.85 + 0.25 * P.fbm(64, 64, 6, 3, 606))
    cv.blend(1 - S(0.36, 0.46, r), "#3FA65A")
    cv.blend(1 - S(0.16, 0.24, r), "#E86A2E")
    cv.blend(1 - S(0.05, 0.1, r), "#2A1A14")
    cv.blend(S(0.88, 0.98, r), "#6A1A20")
    # radial septal lines through the mantle
    ang = np.arctan2(cv.v - 0.5, cv.u - 0.5)
    cv.blend(S(0.7, 1.0, np.cos(ang * 18)) * S(0.55, 0.7, r) * (1 - S(0.9, 0.97, r)), "#C85048", 0.45)
    pm = C.material("acan_polyp", rough=0.4, image=K.tex("acan_polyp", cv), emissive="#2E7A22", emissive_strength=0.4)
    nproto = night_proto(0.9)
    ncv = tentacle_canvas("#B86A5A", "#D8A890", "#F2E6D8", 607)
    nm = C.material("acan_night_tentacle", rough=0.5, image=K.tex("acan_night_tentacle", ncv))
    root = C.empty("Root")
    C.set_extras(root, species="micromussa_lordhowensis", stony=1)
    body = g.obj("Body", root, "body", mats, smooth=True)
    C.recalc_normals(body)
    proto = (V, F, UV, COL)
    pol = K.polyp_node("Polyps", root, proto, [pm], inst)
    nig = K.polyp_node("NightTentacles", root, nproto, [nm], night, extras={"polypMode": 1})
    return root, [body, pol, nig], {"Polyps": (len(inst), len(F) * 2), "NightTentacles": (len(night), len(nproto[1]) * 2)}


# ----------------------------------------------------------------------------
# Candy cane (pack 2): a bunch of branching tubes, each with a striped round head
# ----------------------------------------------------------------------------
def candy_cane():
    rs = RS(611)
    g = T.WGeo()
    top = frag_base(g, 611, radii=(2.4, 1.0, 2.1))
    heads = []
    # [BRS]: 1-2 in frags of 1-3 heads; ours a 10-head clump, tubes forking off each other (phaceloid)
    for i in range(10):
        a = i * 2.4 + rs.uniform(-0.2, 0.2)
        rr = 0.5 + 1.6 * math.sqrt((i + 0.5) / 10)
        dx, dz = rr * math.cos(a), rr * math.sin(a)
        h = rs.uniform(2.4, 3.6) - 0.35 * rr
        foot = STUDIO + np.array([dx * 0.35, top - STUDIO[1], dz * 0.35])
        end = STUDIO + np.array([dx * 1.15, top - STUDIO[1] + h, dz * 1.15])
        pts = np.array([foot + (end - foot) * t + np.array([math.sin(t * 2.5 + i) * 0.12, 0, math.cos(t * 2 + i) * 0.1])
                        for t in (0, 0.35, 0.7, 0.9, 1.0)])
        corallite(g, pts, 0.36, 0.34, 0.58, 620 + i, sides=10, ribs=5, mat=1)
        heads.append((pts[-1], unit(pts[-1] - pts[-2] + UP * 0.9)))
    mats = [rubble_material("candy_cane_rubble"), skeleton_canvas("candy_cane_skeleton", "#E2DCCF", "#4E5E3A", 631, 0.72)]
    inst = K.Instances(632)
    night = K.Instances(633)
    for c, n in heads:
        sc = rs.uniform(0.92, 1.08)
        inst.add(c - n * 0.05, n, sc, phase=rs.uniform())
        night_ring(night, c + n * 0.3, n, 0.2 * sc, 6, rs, sc, lift=0.9)
    # head: a domed cup of fleshy tissue, green with cream radial stripes (the candy cane), a dark mouth
    V, F, UV, COL = cushion(0.62, 0.6, 0.48, rings=5, segs=18)
    cv = P.Canvas(64, 64, "#4E8A46")
    r = np.hypot(cv.u - 0.5, cv.v - 0.5) * 2
    ang = np.arctan2(cv.v - 0.5, cv.u - 0.5)
    cv.multiply(0.88 + 0.2 * P.fbm(64, 64, 6, 3, 634))
    cv.blend(S(0.35, 0.95, np.cos(ang * 12)) * S(0.18, 0.35, r), "#E8EACC", 0.85)
    cv.blend(1 - S(0.12, 0.22, r), "#7CD86A")
    cv.blend(1 - S(0.05, 0.1, r), "#22301C")
    cv.blend(S(0.9, 0.99, r), "#3A5A30")
    pm = C.material("candy_cane_polyp", rough=0.45, image=K.tex("candy_cane_polyp", cv), emissive="#3A9A2A", emissive_strength=1.0)
    nproto = night_proto(1.0, 0.04, 0.018)
    ncv = tentacle_canvas("#8A9A70", "#B8C8A0", "#F0F2E0", 635)
    nm = C.material("candy_cane_night_tentacle", rough=0.5, image=K.tex("candy_cane_night_tentacle", ncv))
    root = C.empty("Root")
    C.set_extras(root, species="caulastrea_furcata", stony=1)
    body = g.obj("Body", root, "body", mats, smooth=True)
    C.recalc_normals(body)
    pol = K.polyp_node("Polyps", root, (V, F, UV, COL), [pm], inst)
    nig = K.polyp_node("NightTentacles", root, nproto, [nm], night, extras={"polypMode": 1})
    return root, [body, pol, nig], {"Polyps": (len(inst), len(F) * 2), "NightTentacles": (len(night), len(nproto[1]) * 2)}


# ----------------------------------------------------------------------------
# Open brain (pack 2): a free-living fleshy brain on the sand, swollen by day
# ----------------------------------------------------------------------------
def open_brain():
    rs = RS(641)
    g = T.WGeo()
    # the cone-shaped skeleton foot sitting in the sand (Trachyphyllia is free-living, no rubble)
    pts = np.array([STUDIO + np.array([0.0, y, 0.0]) for y in (-0.2, 0.4, 1.0, 1.5, 1.7)])
    V, F = [], []
    segs = 20
    for i, p in enumerate(pts):
        rr = (1.0, 1.9, 2.8, 3.4, 3.55)[i]
        for k in range(segs):
            a = 2 * math.pi * k / segs
            w = 1 - 0.18 * math.sin(a) ** 2
            V.append(p + np.array([rr * 1.25 * w * math.cos(a), 0.0, rr * 0.9 * w * math.sin(a)]) * (1 + 0.05 * math.cos(16 * a)))
    for i in range(len(pts) - 1):
        for k in range(segs):
            a, b = i * segs + k, i * segs + (k + 1) % segs
            F.append((a, b, b + segs, a + segs))
    UV = [((k % segs) / segs, (k // segs) / len(pts) * 0.6) for k in range(len(V))]
    g.add(np.array(V), F, np.array(UV), (0.3, 0, 0, SKELETON), mat=0)
    mats = [skeleton_canvas("open_brain_skeleton", "#E6E0D4", "#7A3A3A", 642, 0.55)]
    # mantle: one inflating instance, an 11 x 8 cm cushion pinched at the waist (the figure-8 / meandroid form)
    mantle = K.Instances(643)
    mantle.add(STUDIO + np.array([0.0, 1.55, 0.0]), UP, 1.0, phase=0.3, variant=0.5, twist=0.0)
    V, F, UV, COL = cushion(4.6, 3.4, 2.6, rings=10, segs=44, lobes=(9, 0.06), waist=0.3)
    # the central valley (the mouth groove) dips into the cushion along x
    V = [(x, y - 0.9 * math.exp(-(z / 0.8) ** 2) * max(0.0, 1 - (x / 3.9) ** 2) * min(1.0, y / 0.8)
          + 0.12 * y * math.sin(math.atan2(z, x) * 23), z) for x, y, z in V]
    cv = P.Canvas(128, 128, "#A6303E")
    r = np.hypot(cv.u - 0.5, cv.v - 0.5) * 2
    ang = np.arctan2(cv.v - 0.5, cv.u - 0.5)
    cv.multiply(0.85 + 0.25 * P.fbm(128, 128, 8, 3, 644))
    # radial septal ridges under the tissue, lighter between dark furrows
    cv.blend(S(0.6, 1.0, np.cos(ang * 46)) * S(0.25, 0.4, r), "#D85A66", 0.5)
    cv.blend(S(0.8, 0.9, r) * (1 - S(0.93, 0.97, r)), "#3E9A6A", 0.7)        # green band toward the rim
    cv.blend(S(0.95, 0.99, r), "#9AF0B0")                                        # glowing rim
    cv.blend((1 - S(0.03, 0.08, np.abs(cv.v - 0.5))) * (1 - S(0.4, 0.5, r)), "#4A1820", 0.8)  # mouth groove
    mm = C.material("open_brain_mantle", rough=0.35, image=K.tex("open_brain_mantle", cv), emissive="#1A6A30", emissive_strength=0.3)
    night = K.Instances(645)
    # night tentacles: short ones along the mouth groove and a ring inside the rim
    for k in range(10):
        x = -3.4 + 6.8 * k / 9
        c = STUDIO + np.array([x, 1.55 + 1.7 * (1 - (x / 4.6) ** 2), rs.normal(0, 0.15)])
        night.add(c, unit(np.array([rs.normal(0, 0.3), 1.0, rs.normal(0, 0.3)])), rs.uniform(0.8, 1.0))
    for k in range(22):
        a = 2 * math.pi * (k + rs.uniform(-0.2, 0.2)) / 22
        w = 1.0 - 0.32 * (math.sin(a) ** 2) * (1 - abs(math.cos(a)))
        c = STUDIO + np.array([3.7 * w * math.cos(a), 1.55 + 1.2, 2.7 * w * math.sin(a)])
        night.add(c, unit(np.array([math.cos(a), 1.2, math.sin(a)])), rs.uniform(0.85, 1.1))
    nproto = night_proto(1.2, 0.06, 0.025)
    ncv = tentacle_canvas("#B87A70", "#D8B0A0", "#F2ECE0", 646)
    nm = C.material("open_brain_night_tentacle", rough=0.5, image=K.tex("open_brain_night_tentacle", ncv))
    root = C.empty("Root")
    C.set_extras(root, species="trachyphyllia_geoffroyi", stony=1)
    body = g.obj("Body", root, "body", mats, smooth=True)
    C.recalc_normals(body)
    man = K.polyp_node("Mantle", root, (V, F, UV, COL), [mm], mantle, extras={"polypMode": 3})
    nig = K.polyp_node("NightTentacles", root, nproto, [nm], night, extras={"polypMode": 1})
    return root, [body, man, nig], {"Mantle": (1, len(F) * 2), "NightTentacles": (len(night), len(nproto[1]) * 2)}


# ----------------------------------------------------------------------------
# Bubble coral (pack 2): grape-like vesicles over a thin-walled skeleton
# ----------------------------------------------------------------------------
def bubble():
    rs = RS(651)
    g = T.WGeo()
    top = frag_base(g, 651, radii=(2.8, 1.1, 2.4))
    # skeleton: a few thin, tall septal walls in meandering valleys (mostly hidden under the bubbles by day)
    for w in range(5):
        z = -2.0 + w * 1.0
        xs = np.linspace(-2.4, 2.4, 7)
        lo = np.array([STUDIO + np.array([x, top - STUDIO[1], z + 0.25 * math.sin(x * 1.3 + w)]) for x in xs])
        hgt = 1.9 * np.sqrt(np.clip(1 - (xs / 2.7) ** 2 - (z / 2.8) ** 2, 0.05, 1))
        V, F = [], []
        for i, p in enumerate(lo):
            for k, (dy, dz) in enumerate(((0.0, -0.06), (hgt[i], -0.03), (hgt[i], 0.03), (0.0, 0.06))):
                V.append(p + np.array([0.0, dy, dz]))
        for i in range(len(lo) - 1):
            for k in range(4):
                a, b = i * 4 + k, i * 4 + (k + 1) % 4
                F.append((a, b, b + 4, a + 4))
        UV = [(k % 4 / 4, (V[k][1] - top) / 3.0 * 0.6 + 0.35) for k in range(len(V))]
        g.add(np.array(V), F, np.array(UV), (0.3, 0, 0, SKELETON), mat=1)
    mats = [rubble_material("bubble_rubble"), skeleton_canvas("bubble_skeleton", "#ECE8DE", "#C8CCB0", 652, 0.5)]
    # bubbles over a dome: sunflower-packed, bigger in the middle
    inst = K.Instances(653)
    cnt = 40
    for i in range(cnt):
        t = math.sqrt((i + 0.5) / cnt)
        a = i * math.pi * (3 - math.sqrt(5))
        x, z = 3.0 * t * math.cos(a), 2.6 * t * math.sin(a)
        y = top + 0.2 + 2.1 * math.sqrt(max(0.0, 1 - t * t)) - 0.6 * t * t
        c = STUDIO * np.array([1, 0, 1]) + np.array([x, y, z])
        n = unit(np.array([x * 0.5, 1.0, z * 0.5]))
        inst.add(c - n * 0.35, n, rs.uniform(0.85, 1.15) * (1.15 - 0.35 * t), phase=rs.uniform())
    V, F, UV, COL = ball((0.0, 0.55, 0.0), (0.64, 0.62, 0.64), 12, 7, v0=0.0, hinge=0.0, flag=0.0)
    # vesicle: pale cream-green, a darker web where they touch, a lighter crown (light passing through water-filled tissue)
    cv = P.Canvas(32, 64, "#C8D4B4")
    cv.multiply(0.9 + 0.15 * P.fbm(64, 32, 4, 3, 654))
    cv.blend(1 - S(0.15, 0.35, cv.v), "#8A9A78", 0.8)
    cv.blend(S(0.7, 0.95, cv.v), "#EEF4DE", 0.8)
    cv.blend(S(0.6, 1.0, np.cos(cv.u * 2 * math.pi * 9)) * (1 - S(0.4, 0.6, cv.v)), "#A8B898", 0.35)
    bm = C.material("bubble_vesicle", rough=0.2, image=K.tex("bubble_vesicle", cv), emissive="#4A7A3A", emissive_strength=0.6)
    night = K.Instances(655)
    # night: long tapering sweepers between the shrunken bubbles
    for i in range(18):
        t = math.sqrt((i + 0.5) / 18)
        a = i * 2.4 + 0.7
        c = STUDIO * np.array([1, 0, 1]) + np.array([2.4 * t * math.cos(a), top + 1.6 * math.sqrt(max(0.05, 1 - t * t)), 2.0 * t * math.sin(a)])
        night.add(c, unit(np.array([math.cos(a) * t * 1.3, 0.9, math.sin(a) * t * 1.3])), rs.uniform(0.85, 1.15))
    nproto = night_proto(3.2, 0.06, 0.02, curve=0.25)
    ncv = tentacle_canvas("#A8B090", "#C8D0B0", "#F4F6E8", 656)
    nm = C.material("bubble_night_tentacle", rough=0.5, image=K.tex("bubble_night_tentacle", ncv))
    root = C.empty("Root")
    C.set_extras(root, species="plerogyra_sinuosa", stony=1)
    body = g.obj("Body", root, "body", mats, smooth=True)
    C.recalc_normals(body)
    pol = K.polyp_node("Bubbles", root, (V, F, UV, COL), [bm], inst, extras={"polypMode": 3})
    nig = K.polyp_node("NightTentacles", root, nproto, [nm], night, extras={"polypMode": 1})
    return root, [body, pol, nig], {"Bubbles": (len(inst), len(F) * 2), "NightTentacles": (len(night), len(nproto[1]) * 2)}


BUILDERS = {
    "hammer": lambda: euphyllia(EUPHYLLIA["hammer"]),
    "torch": lambda: euphyllia(EUPHYLLIA["torch"]),
    "frogspawn": lambda: euphyllia(EUPHYLLIA["frogspawn"]),
    "duncan": duncan,
    "acan": acan,
    "candy_cane": candy_cane,
    "open_brain": open_brain,
    "bubble": bubble,
}


def build(ids=None):
    C.reset_scene()
    out = {}
    for cid in ids or BUILDERS:
        for o in list(bpy.data.objects):
            bpy.data.objects.remove(o, do_unlink=True)
        for m in list(bpy.data.meshes):
            bpy.data.meshes.remove(m)
        root, objs, inst = BUILDERS[cid]()
        for o in objs:
            C.triangulate(o)
        path = os.path.join(C.MODELS, cid + ".glb")
        C.export_glb(path, C.all_descendants(root))
        tris = sum(C.tri_count(o) for o in objs)
        print(f"[stony] {cid}.glb: {tris} mesh tris, instances {dict((k, v[0]) for k, v in inst.items())}, "
              f"{os.path.getsize(path) / 1024:.0f} KB")
        out[cid] = (tris, inst)
    return out


if __name__ == "__main__":
    build([a for a in sys.argv[1:] if not a.startswith("-")] or None)
