"""Cleanup crew pack (docs/research/43_cleanup_crew.md): trochus snail, fighting conch, tuxedo urchin.

    /tmp/claude-0/bpyenv/bin/python tools/blender/cleanup_crew.py [trochus|conch|urchin ...]

All three are rigid bodies placed at the organism (the body centre: the sim keeps it half a body height off the
surface) and posed by fish.vert from the sim's LimbRig entries, like the flame scallop: motion mode 8 for the two
snails (core SnailRig), mode 9 for the urchin (core UrchinRig). Unit length along +X (Blender Z up), the body's
vertical middle at z = 0, the sole or test underside at z = -depthRatio / 2 (printed by each builder).

COLOR_0 (read by fish.vert): R = class, B = t along an appendage (0 root .. 1 tip), A = a per-appendage phase seed.
  snails:  R 0 shell, 0.25 foot, 0.5 head / snout, 0.75 tentacle, 1 eye (stalk tip), 0.4 operculum (conch)
  urchin:  R 0 test, 0.25 spine, 0.5 tube foot, 0.75 debris (B = piece index / 7), 1 Aristotle's lantern teeth
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import geomlib as G  # noqa: E402
import paintlib as P  # noqa: E402
from inverts import Atlas, finish, geo_obj, make_root  # noqa: E402

S = P.smoothstep

CLS_SHELL, CLS_FOOT, CLS_HEAD, CLS_TENT, CLS_EYE, CLS_OPER = 0.0, 0.25, 0.5, 0.75, 1.0, 0.4
CLS_TEST, CLS_SPINE, CLS_TUBE, CLS_DEBRIS, CLS_TEETH = 0.0, 0.25, 0.5, 0.75, 1.0


def cols(n, cls, t=0.0, seed=0.0):
    t = np.broadcast_to(np.asarray(t, float), (n,))
    return np.stack([np.full(n, cls), np.zeros(n), t, np.full(n, seed)], -1)


def _recentre(objs, sole_z):
    """Shift every mesh so the sole / underside at [sole_z] lands at -depth / 2 and the top at +depth / 2."""
    allv = np.vstack([C.get_verts(o) for o in objs])
    top = allv[:, 2].max()
    depth = top - sole_z
    dz = -sole_z - depth / 2
    for o in objs:
        V = C.get_verts(o)
        V[:, 2] += dz
        o.data.vertices.foreach_set("co", V.ravel())
        o.data.update()
    return depth, dz


def _tentacle(g, uv, base, tip_dir, length, r0, r1, n=7, sides=5, cls=CLS_TENT, seed=0.0, bend=(0, 0, 0)):
    base = np.asarray(base, float)
    d = np.asarray(tip_dir, float)
    d = d / np.linalg.norm(d)
    b = np.asarray(bend, float)
    pts = G.bezier(base, base + d * length * 0.35, base + d * length * 0.7 + b * 0.5, base + d * length + b, n)
    V, F, tv, av = G.tube(pts, np.linspace(r0, r1, n), sides, cap_end=True)
    g.add(V, F, uv(tv, av), cols(len(V), cls, tv, seed))
    return pts[-1]


# ----------------------------------------------------------------------------
# Trochus snail (Trochus / Tectus as sold: "banded trochus", research 43 §4)
# ----------------------------------------------------------------------------
TROCHUS_H = 0.92        # shell height / base diameter (flat-sided cone, Tectus fenestratus / Trochus histrio)
TROCHUS_WHORLS = 6.5
TROCHUS_TILT = math.radians(14)   # the crawling snail carries its apex tipped back


def build_trochus():
    """Banded trochus: a straight-sided, sharply pointed cone (height about its width) of six flat whorls with an
    angular keel at the periphery and a flat base; fine beaded spiral cords; cream to pinkish ground with oblique
    red-brown flames, the apex worn to pearl. The animal: a long foot fringed with epipodial tentacles (a trochid
    hallmark), a broad snout that rasps the glass, a pair of long banded cephalic tentacles with eyes on short
    stalks at their outer bases."""
    C.reset_scene()
    at = Atlas(512, 256, 4)
    rs = np.random.default_rng(43)

    def shell(cv):
        # u = up the shell (0 keel .. 1 apex), v = around (0..1)
        cv.vgrad("#E6D6C4", "#EFE2D2")
        # oblique axial flames: they lean with the coil and break into blotches on each whorl
        whorl = (cv.u * TROCHUS_WHORLS - cv.v) % 1.0
        flame = 0.5 + 0.5 * np.sin(2 * math.pi * (cv.v * 11 + cv.u * 3.2))
        n = P.fbm(cv.h, cv.w, 10, 3, 431)
        m = S(0.55, 0.75, flame * (0.7 + 0.5 * n)) * S(0.08, 0.22, whorl) * (1 - S(0.85, 0.97, whorl))
        cv.blend(m, "#8A3E2E", 0.85)
        cv.blend(m * S(0.5, 0.8, n), "#5E4A62", 0.45)        # some flames grey-purple
        # beaded spiral cords: three per whorl, beads alternating dark / light
        for c0 in (0.3, 0.55, 0.8):
            cord = np.exp(-(((whorl - c0) / 0.05) ** 2))
            bead = 0.5 + 0.5 * np.cos(2 * math.pi * (cv.v * 34 + c0 * 7))
            cv.blend(cord * S(0.6, 0.9, bead), "#F7EEE2", 0.55)
            cv.blend(cord * (1 - S(0.1, 0.4, bead)), "#6E3426", 0.45)
        # suture: a thin dark groove line
        cv.blend(np.exp(-((whorl / 0.035) ** 2)) + np.exp(-(((1 - whorl) / 0.035) ** 2)), "#5A3A2C", 0.6)
        # apex worn to the pearly nacre; a light algae film low on the body whorl
        cv.blend(S(0.8, 0.97, cv.u), "#D9CFD8", 0.8)
        cv.blend(S(0.55, 0.75, P.fbm(cv.h, cv.w, 14, 3, 432)) * (1 - S(0.2, 0.45, cv.u)), "#7C8456", 0.25)
    at.paint_strip(0, shell)

    def base(cv):
        # u = radius (0 centre .. 1 keel), v = around: cream base with spiral rows of red dots, pearly umbilical area
        cv.vgrad("#E8DCCB", "#E8DCCB")
        rows = 0.5 + 0.5 * np.cos(2 * math.pi * cv.u * 9)
        dots = 0.5 + 0.5 * np.cos(2 * math.pi * cv.v * 40)
        cv.blend(S(0.7, 0.95, rows) * S(0.55, 0.9, dots) * S(0.15, 0.3, cv.u), "#93473A", 0.7)
        cv.blend(1 - S(0.1, 0.28, cv.u), "#E9E6EE", 0.9)
    at.paint_strip(1, base)

    def body(cv):
        # foot and head: cream-grey with dark brown mottling and a pale sole; u = along, v = up the side
        cv.vgrad("#D8D2C2", "#8A8274")
        n = P.fbm(cv.h, cv.w, 16, 4, 433)
        cv.blend(S(0.5, 0.68, n) * S(0.25, 0.6, cv.v), "#3E3226", 0.75)
        cv.blend(S(0.62, 0.72, P.fbm(cv.h, cv.w, 30, 2, 434)) * S(0.4, 0.8, cv.v), "#E8E0CC", 0.5)
    at.paint_strip(2, body)

    def tent(cv):
        # cephalic and epipodial tentacles: banded dark brown and cream, pale tips
        cv.vgrad("#C8BCA8", "#C8BCA8")
        cv.blend(S(0.45, 0.6, 0.5 + 0.5 * np.sin(2 * math.pi * cv.u * 7)), "#4A3626", 0.8)
        cv.blend(S(0.85, 1.0, cv.u), "#EDE6D8", 0.8)
    at.paint_strip(3, tent)
    tex = os.path.join(C.BUILD, "trochus_atlas.png")
    at.cv.save(tex)
    mat = C.material("trochus_mat", rough=0.4, image=C.load_image(tex, name="trochus"))
    root = make_root("Root", {})

    # ---- shell: a coiled cone. Each vertex sits at height s (0 keel .. 1 apex) and angle th; its place within the
    # whorl w = frac(s * whorls - th / 2pi) puts the suture groove at w = 0 and a slight convexity mid-whorl ----
    rings, sides = 24, 32
    R0 = 0.5
    zb = 0.13                                   # keel height above the sole
    V, UV, Cc = [], [], []
    for i in range(rings + 1):
        s = (i / rings) ** 0.92
        for k in range(sides):
            th = 2 * math.pi * k / sides
            w = (s * TROCHUS_WHORLS - th / (2 * math.pi)) % 1.0
            r = R0 * (1 - s) ** 1.0
            r *= 1 + 0.035 * math.sin(math.pi * w) - 0.05 * math.exp(-(w / 0.07) ** 2) - 0.05 * math.exp(-((1 - w) / 0.07) ** 2)
            # the periphery keel: a sharp angular rim at the base
            if i == 0:
                r *= 1.03
            V.append((r * math.cos(th), r * math.sin(th), zb + TROCHUS_H * s))
            UV.append((s, k / sides))
    apex = len(V)
    V.append((0, 0, zb + TROCHUS_H + 0.01))
    UV.append((1.0, 0.5))
    F = []
    for i in range(rings):
        for k in range(sides):
            a = i * sides + k
            b = i * sides + (k + 1) % sides
            F.append((a, b, b + sides, a + sides))
    top0 = rings * sides
    for k in range(sides):
        F.append((top0 + k, top0 + (k + 1) % sides, apex))
    Va = np.array(V)
    UVa = np.array(UV)
    gs = G.Geo()
    gs.add(Va, F, at.uv(0, UVa[:, 0], UVa[:, 1]), cols(len(Va), CLS_SHELL))
    # flat, slightly convex base out to the keel
    bv, bf, buv = [], [], []
    brings = 5
    for i in range(1, brings + 1):
        rr = i / brings
        for k in range(sides):
            th = 2 * math.pi * k / sides
            r = R0 * 1.03 * rr
            bv.append((r * math.cos(th), r * math.sin(th), zb - 0.025 * (1 - rr ** 2)))
            buv.append((rr, k / sides))
    bc = len(bv)
    bv.append((0, 0, zb - 0.03))
    buv.append((0.0, 0.5))
    for i in range(brings - 1):
        for k in range(sides):
            a = i * sides + k
            b = i * sides + (k + 1) % sides
            bf.append((b, a, a + sides, b + sides))
    for k in range(sides):
        bf.append(((k + 1) % sides, k, bc))
    bva = np.array(bv)
    buva = np.array(buv)
    gs.add(bva, bf, at.uv(1, buva[:, 0], buva[:, 1]), cols(len(bva), CLS_SHELL))
    shell_o = geo_obj("Shell", gs, root, "shell", [mat])

    # ---- foot: a long oval sole emerging under the shell, longer in front; a fringe of epipodial tentacles ----
    gf = G.Geo()
    fl, fw, fh = 0.62, 0.3, 0.09
    Vf, Ff = G.ellipsoid((0.1, 0, 0.045), (fl, fw, fh * 0.5), 14, 5)
    Vf[:, 2] = np.maximum(Vf[:, 2], 0.0)                 # flat sole on z = 0
    tf = np.clip((Vf[:, 0] + fl - 0.1) / (2 * fl), 0, 1)
    vf = np.clip(Vf[:, 2] / fh, 0, 1)
    gf.add(Vf, Ff, at.uv(2, tf, vf), cols(len(Vf), CLS_FOOT, tf))
    # head: a broad snout (muzzle) forward of the shell, and the neck up into the aperture
    hp = G.bezier((0.28, 0, 0.1), (0.4, 0, 0.12), (0.52, 0, 0.1), (0.62, 0, 0.06), 6)
    Vh, Fh, th_, ah = G.tube(hp, np.linspace(0.11, 0.07, 6), 8, cap_end=True, squash=0.6)
    gf.add(Vh, Fh, at.uv(2, 0.6 + 0.4 * th_, 0.5 + 0.3 * np.cos(2 * math.pi * ah)), cols(len(Vh), CLS_HEAD, th_))
    foot_o = geo_obj("Foot", gf, root, "body", [mat])

    gt = G.Geo()
    tuv = lambda t, a: at.uv(3, t, a)  # noqa: E731
    eyes = []
    for side in (1, -1):
        # cephalic tentacles: long, tapering, held forward and out
        b = np.array([0.5, side * 0.07, 0.15])
        tip = _tentacle(gt, tuv, b, (0.75, side * 0.55, 0.25), 0.42, 0.022, 0.006, n=9, seed=0.1 if side > 0 else 0.6,
                        bend=(0.0, side * 0.04, -0.05))
        eyes.append(b + np.array([-0.01, side * 0.05, 0.02]))
        # epipodial tentacles: three each side along the foot rim, splayed out and back
        for j, x in enumerate((0.15, -0.12, -0.38)):
            eb = np.array([x, side * (fw * 0.92), 0.05])
            _tentacle(gt, tuv, eb, (-0.35, side * 1.0, 0.15), 0.16 + 0.03 * j, 0.012, 0.004, n=5, sides=4,
                      seed=0.2 + 0.13 * j + (0.4 if side < 0 else 0.0))
    tent_o = geo_obj("Tentacles", gt, root, "antenna", [mat])
    ge = G.Geo()
    for i, e in enumerate(eyes):
        # short eye stalk with a black eye
        pts = G.bezier(e, e + [0.0, 0, 0.02], e + [0.01, 0, 0.035], e + [0.015, 0, 0.045], 4)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.016, 0.012, 4), 5)
        ge.add(Vt, Ft, at.uv(3, 0.1 + 0.2 * tv, av), cols(len(Vt), CLS_EYE, tv, 0.3 * i))
        Ve, Fe = G.ellipsoid(pts[-1] + [0.003, 0, 0.004], (0.014, 0.014, 0.014), 8, 5)
        ge.add(Ve, Fe, at.uv(3, np.full(len(Ve), 0.5), np.full(len(Ve), 0.02)), cols(len(Ve), CLS_EYE, 1.0, 0.3 * i))
    eye_o = geo_obj("Eyes", ge, root, "eye", [mat])

    objs = [shell_o, foot_o, tent_o, eye_o]
    # tip the shell back (apex toward -X) about the keel's rear edge, the way it rides on the crawling foot
    Vsh = C.get_verts(shell_o)
    pivot = np.array([-0.1, 0, zb])
    Vsh = (Vsh - pivot) @ G.rot_y(-TROCHUS_TILT).T + pivot
    shell_o.data.vertices.foreach_set("co", Vsh.ravel())
    shell_o.data.update()
    depth, dz = _recentre(objs, 0.0)
    for o in objs:
        C.recalc_normals(o)
    aperture = [0.18, 0.0, round(float(zb + dz), 4)]
    C.set_extras(root, species="trochus", lengthCm=2.5, unit="shell base diameter 1.0, snout +X, apex +Z",
                 roughness=0.4, motionMode=8, depthRatio=round(depth, 3), aperture=aperture,
                 note="retracted parts gather at extras.aperture (model space) and the shell stays")
    print(f"[trochus] depthRatio {depth:.3f}, aperture {aperture}")
    return finish(root, objs, os.path.join(C.MODELS, "trochus.glb"), 2400)


# ----------------------------------------------------------------------------
# Florida fighting conch (Strombus alatus, research 43 §3)
# ----------------------------------------------------------------------------
def build_conch():
    """Florida fighting conch: a heavy spindle shell, a short stepped spire with a ring of blunt knobs on the
    shoulder, a big body whorl tapering to the siphonal canal in front; the outer lip flares into a thick wing
    toward the back, with the stromboid notch near the front where the right eye peers out. Mottled orange-brown with
    cream zigzag bands; glossy orange-salmon aperture. The animal: a grey-brown mottled proboscis that sweeps the
    sand, two long eyestalks with big ringed eyes, and a narrow muscular foot ending in the sickle-shaped, serrated
    operculum it digs in to leap."""
    C.reset_scene()
    at = Atlas(512, 256, 4)

    def shell(cv):
        # u = along the shell axis (0 apex .. 1 canal), v = around (0 dorsal midline ... 0.5 ventral)
        cv.vgrad("#B8692E", "#C77A3C")
        n = P.fbm(cv.h, cv.w, 12, 4, 351)
        cv.blend(S(0.45, 0.7, n), "#8A4A22", 0.6)
        # cream zigzag spiral bands across the body whorl
        zz = np.abs(((cv.v * 18) % 2.0) - 1.0)
        for c0 in (0.42, 0.62, 0.8):
            band = np.exp(-(((cv.u - c0 - 0.025 * zz) / 0.03) ** 2))
            cv.blend(band, "#EAD8B6", 0.75)
        # growth lines and a little algae film on the spire
        cv.multiply(0.9 + 0.1 * (0.5 + 0.5 * np.sin(cv.u * 2 * math.pi * 40)))
        cv.blend(S(0.55, 0.75, P.fbm(cv.h, cv.w, 16, 3, 352)) * (1 - S(0.2, 0.35, cv.u)), "#6E6A3E", 0.35)
        cv.blend(S(0.0, 0.08, 0.08 - cv.u), "#E8D9C8", 0.6)   # worn apex
    at.paint_strip(0, shell)

    def lip(cv):
        # aperture and inner lip: glossy salmon-orange shading to cream at the rim; u = across, v = along
        cv.vgrad("#E89A6A", "#F3C49A")
        cv.blend(S(0.7, 1.0, cv.u), "#F6E6CF", 0.8)
        cv.blend(S(0.0, 0.25, 0.25 - cv.u), "#C5683E", 0.6)
    at.paint_strip(1, lip)

    def body(cv):
        # proboscis, foot and stalks: grey-brown with dark mottling and pale flecks
        cv.vgrad("#7A6858", "#9C8A76")
        cv.blend(S(0.5, 0.7, P.fbm(cv.h, cv.w, 18, 4, 353)), "#3C2E24", 0.7)
        cv.blend(S(0.68, 0.76, P.fbm(cv.h, cv.w, 34, 2, 354)), "#D8C8A8", 0.5)
    at.paint_strip(2, body)

    def eye_oper(cv):
        # left half: the eye (u 0..0.5 = radial from pupil centre): black pupil, yellow-orange iris, dark ring, pale
        # rim; right half: the operculum (u 0.5..1 = along the sickle): horn brown, growth ridges, darker serrated edge
        e = np.clip(cv.u / 0.5, 0, 1)
        col = np.zeros_like(cv.rgb)
        col[:] = P.hex2rgb("#141012") if hasattr(P, "hex2rgb") else C.hex2rgb("#141012")
        cv.rgb[:] = C.hex2rgb("#141012")
        cv.blend((S(0.25, 0.3, e)) * (cv.u < 0.5), "#D8A428")
        cv.blend((S(0.55, 0.6, e)) * (cv.u < 0.5), "#3A2A14")
        cv.blend((S(0.72, 0.8, e)) * (cv.u < 0.5), "#8C7A62")
        o = np.clip((cv.u - 0.5) / 0.5, 0, 1)
        m = (cv.u >= 0.5).astype(np.float32)
        cv.blend(m, "#6A4420")
        cv.blend(m * S(0.5, 0.9, 0.5 + 0.5 * np.sin(2 * math.pi * o * 14)), "#8A5A2C", 0.6)
        cv.blend(m * S(0.75, 0.95, cv.v), "#2E1C0C", 0.8)
    at.paint_strip(3, eye_oper)
    tex = os.path.join(C.BUILD, "conch_atlas.png")
    at.cv.save(tex)
    mat = C.material("conch_mat", rough=0.35, image=C.load_image(tex, name="conch"))
    root = make_root("Root", {})

    # ---- shell body: a spindle swept along X (apex -0.5 .. canal +0.5), the aperture side (-Z, underneath) flattened
    # where it rests on the sand. Radius profile: pointed stepped spire, shoulder knobs at x = -0.15, body whorl, canal.
    rings, sides = 44, 30
    xs = np.linspace(-0.5, 0.5, rings)

    def prof(x):
        if x < -0.17:   # spire: stepped cone (whorl shoulders every ~0.07)
            t = (x + 0.5) / 0.33
            step = 0.012 * np.sin(2 * math.pi * t * 4.5) ** 2
            return 0.012 + 0.2 * t ** 1.05 + step
        if x < -0.08:   # shoulder
            t = (x + 0.17) / 0.09
            return 0.212 + 0.07 * math.sin(math.pi * t * 0.5)
        t = (x + 0.08) / 0.58
        return 0.282 * (1 - t) ** 1.25 + 0.022 * t
    V, UV = [], []
    for i, x in enumerate(xs):
        r0 = prof(x)
        for k in range(sides):
            a = 2 * math.pi * k / sides            # 0 = dorsal (+Z), pi = ventral (-Z)
            r = r0
            # blunt shoulder knobs (about 9 around the last whorl, strongest dorsally)
            if -0.19 < x < -0.06:
                kn = max(0.0, math.cos(9 * a)) ** 4 * math.exp(-((x + 0.12) / 0.035) ** 2)
                r += 0.06 * kn * (0.4 + 0.6 * math.cos(a))
            y = r * math.sin(a)
            z = r * math.cos(a)
            if z < 0:
                z *= 0.62                           # flattened underside / aperture face
            V.append((x, y, z))
            UV.append(((x + 0.5), (a / (2 * math.pi))))
    F = []
    for i in range(rings - 1):
        for k in range(sides):
            a_ = i * sides + k
            b_ = i * sides + (k + 1) % sides
            F.append((a_, a_ + sides, b_ + sides, b_))
    tipA = len(V); V.append((-0.505, 0, 0)); UV.append((0.0, 0.5))
    tipB = len(V); V.append((0.52, 0, -0.01)); UV.append((1.0, 0.5))
    for k in range(sides):
        F.append(((k + 1) % sides, k, tipA))
        base = (rings - 1) * sides
        F.append((base + k, base + (k + 1) % sides, tipB))
    Va = np.array(V); UVa = np.array(UV)
    gs = G.Geo()
    gs.add(Va, F, at.uv(0, UVa[:, 0], UVa[:, 1]), cols(len(Va), CLS_SHELL))
    # ---- flared outer lip (the "wing"): a thick curved plate along the right side (+Y is left in Blender, the
    # dextral lip is on the animal's right: -Y), widest toward the back, from x = -0.2 to +0.36, curling up; the
    # stromboid notch is the dip at x ~ +0.3 ----
    nl, nw = 18, 6
    lv, luv = [], []
    for i in range(nl):
        t = i / (nl - 1)
        x = -0.22 + 0.6 * t
        notch = math.exp(-((x - 0.3) / 0.03) ** 2)
        width = (0.11 * math.sin(math.pi * min(1.0, t * 1.1)) ** 0.8 + 0.015) * (1 - 0.7 * notch)
        rr = prof(x) * 0.95
        for j in range(nw):
            u = j / (nw - 1)
            # out from the body side at mid-height, curling up at the rim; posterior end lifts into the wing
            ang = -math.pi / 2 - 0.45 + 0.35 * u          # out from just under the side, rising a little at the rim
            rad = rr + width * u
            y = rad * math.sin(ang) * 1.0
            z = rad * math.cos(ang) * 0.62 + 0.04 * u * u * (1 - t) ** 1.5
            lv.append((x, y, z))
            luv.append((u, t))
    lf = []
    for i in range(nl - 1):
        for j in range(nw - 1):
            a_ = i * nw + j
            lf.append((a_, a_ + 1, a_ + nw + 1, a_ + nw))
    lv = np.array(lv); luv = np.array(luv)
    # give the plate thickness (the lip of an adult is thick): offset a copy inward along its normal-ish
    thick = 0.03
    lv2 = lv.copy(); lv2[:, 2] -= thick * 0.6; lv2[:, 1] *= 0.94
    b0 = len(lv)
    LV = np.vstack([lv, lv2])
    LF = lf + [(c + b0, b + b0, a + b0) for (a, b, c, _d) in lf] + [(d + b0, c + b0, a + b0) for (a, b, c, d) in lf]
    # rim strip joining the two sheets along the outer edge
    for i in range(nl - 1):
        a_ = i * nw + nw - 1
        LF.append((a_, a_ + b0, a_ + nw + b0, a_ + nw))
    # top sheet: the shell's own outer colour; underside and rim: the glossy aperture lining
    UVtop = at.uv(0, 0.5 + 0.35 * luv[:, 1], 0.75 - 0.2 * luv[:, 0])
    UVbot = at.uv(1, luv[:, 0], luv[:, 1])
    gs.add(LV, [tuple(f[:4]) if len(f) == 4 else f for f in LF], np.vstack([UVtop, UVbot]), cols(len(LV), CLS_SHELL))
    shell_o = geo_obj("Shell", gs, root, "shell", [mat])

    # ---- the animal, all out of the front of the aperture (underneath, x > 0.2) ----
    gb = G.Geo()
    # proboscis: long, extensible, reaching forward and down to the sand
    pp = G.bezier((0.3, 0.0, -0.1), (0.42, 0.0, -0.1), (0.55, 0.0, -0.13), (0.66, 0.0, -0.17), 9)
    Vp, Fp, tv, av = G.tube(pp, np.linspace(0.03, 0.02, 9), 7, cap_end=True, squash=0.8)
    gb.add(Vp, Fp, at.uv(2, tv, av), cols(len(Vp), CLS_HEAD, tv))
    # foot: narrow, muscular, from under the aperture forward and down, ending at the operculum
    fp = G.bezier((0.18, 0.04, -0.12), (0.3, 0.06, -0.17), (0.42, 0.07, -0.19), (0.52, 0.08, -0.2), 9)
    Vf, Ff, tvf, avf = G.tube(fp, np.linspace(0.045, 0.026, 9), 7, cap_end=True, squash=0.7)
    gb.add(Vf, Ff, at.uv(2, 0.2 + 0.6 * tvf, avf), cols(len(Vf), CLS_FOOT, tvf))
    body_o = geo_obj("Body", gb, root, "body", [mat])
    # operculum: a curved, serrated sickle blade on the foot tip, edge down to dig in
    go = G.Geo()
    no = 10
    ov, ouv = [], []
    base = fp[-1]
    for i in range(no):
        t = i / (no - 1)
        ang = math.radians(-20 + 120 * t)
        r = 0.09
        cx = base[0] + r * math.sin(ang) * 0.9 - 0.01
        cz = base[2] - r * (1 - math.cos(ang)) * 0.6
        w = 0.028 * math.sin(math.pi * (0.15 + 0.85 * t)) + 0.004
        serr = 0.006 * (i % 2)
        for side in (-1, 1):
            ov.append((cx, base[1] + side * 0.006, cz))
            ouv.append((0.5 + 0.5 * t, 0.2))
        ov.append((cx - 0.004, base[1], cz - w - serr))
        ouv.append((0.5 + 0.5 * t, 0.95))
    of = []
    for i in range(no - 1):
        a_ = i * 3
        of += [(a_, a_ + 3, a_ + 5, a_ + 2), (a_ + 1, a_ + 2, a_ + 5, a_ + 4), (a_, a_ + 1, a_ + 4, a_ + 3)]
    ov = np.array(ov); ouv = np.array(ouv)
    tvo = np.repeat(np.linspace(0, 1, no), 3)
    go.add(ov, of, at.uv(3, ouv[:, 0], ouv[:, 1]), cols(len(ov), CLS_OPER, tvo))
    oper_o = geo_obj("Operculum", go, root, "body", [mat])
    # eyestalks: long, from either side of the proboscis base, the right one through the stromboid notch; the eye at
    # the tip is big (strombids have large camera eyes)
    ge = G.Geo()
    gt = G.Geo()
    for i, side in enumerate((1, -1)):
        b = np.array([0.3, side * 0.06, -0.06])
        d = np.array([0.6, side * 0.45, 0.35])
        tip = _tentacle(gt, lambda t, a: at.uv(2, t, a), b, d, 0.27, 0.014, 0.01, n=7, sides=5, seed=0.25 + 0.5 * i,
                        bend=(0.0, 0.0, 0.03))
        Ve, Fe = G.ellipsoid(tip, (0.03, 0.03, 0.03), 9, 5)
        # map the eye texture radially so the pupil faces outward along the stalk direction
        dd = d / np.linalg.norm(d)
        rel = (Ve - tip) / 0.03
        rad = np.arccos(np.clip(rel @ dd, -1, 1)) / math.pi          # 0 facing out .. 1 behind
        ge.add(Ve, Fe, at.uv(3, 0.5 * rad, np.full(len(Ve), 0.5)), cols(len(Ve), CLS_EYE, 1.0, 0.25 + 0.5 * i))
        # a short tentacle near each eye (strombids carry one on each stalk)
        _tentacle(gt, lambda t, a: at.uv(2, t, a), tip - dd * 0.03, (0.3, side * 0.8, 0.4), 0.06, 0.008, 0.003, n=4,
                  sides=4, seed=0.4 + 0.5 * i)
    stalk_o = geo_obj("Stalks", gt, root, "antenna", [mat])
    eye_o = geo_obj("Eyes", ge, root, "eye", [mat])
    objs = [shell_o, body_o, oper_o, stalk_o, eye_o]
    sole = min(C.get_verts(o)[:, 2].min() for o in objs)
    depth, dz = _recentre(objs, sole)
    for o in objs:
        C.recalc_normals(o)
    oper_tip = [round(float(v), 4) for v in (np.array(ov[-1]) + [0, 0, dz])]
    aperture = [0.24, 0.0, round(float(-0.1 + dz), 4)]
    C.set_extras(root, species="fighting_conch", lengthCm=7.0, unit="shell length 1.0 (apex -X, canal +X), aperture down",
                 roughness=0.35, motionMode=8, depthRatio=round(depth, 3), aperture=aperture, operculumTip=oper_tip,
                 note="leap: the shell vaults over extras.operculumTip (fish.vert snailPose)")
    print(f"[conch] depthRatio {depth:.3f}, aperture {aperture}, operculum tip {oper_tip}")
    return finish(root, objs, os.path.join(C.MODELS, "fighting_conch.glb"), 3600)


# ----------------------------------------------------------------------------
# Tuxedo urchin (Mespilia globulus, research 43 §2)
# ----------------------------------------------------------------------------
URCHIN_H = 0.66      # test height / diameter (globular, a little flattened)
URCHIN_SPINE_TRACTS = 10
URCHIN_DEBRIS = 7


def build_urchin():
    """Tuxedo urchin: a near-globular test with ten broad bare bands of deep blue to purple-black skin running pole
    to pole, alternating with ten narrow tracts of short, dense spines (red-brown with orange tips); long translucent
    tube feet with dark suckers reach out of the spine tracts, and those on top hold bits of shell, rubble and algae
    over the test (the covering reaction). The mouth with Aristotle's lantern underneath."""
    C.reset_scene()
    at = Atlas(512, 256, 4)
    rs = np.random.default_rng(91)

    def test(cv):
        # u = latitude (0 oral pole .. 1 aboral pole), v = around: bare bands where v*10 is near the band centres
        ph = (cv.v * URCHIN_SPINE_TRACTS) % 1.0
        bare = S(0.12, 0.2, np.abs(ph - 0.5))      # 0 in a spine tract, 1 in a bare band
        cv.vgrad("#2C1A20", "#2C1A20")
        cv.blend(1 - bare, "#5A2A22")               # spine tracts: brown-red skin under the spines
        n = P.fbm(cv.h, cv.w, 20, 3, 911)
        cv.blend(bare, "#1C2C5E")                   # deep blue bare skin
        cv.blend(bare * S(0.4, 0.8, n), "#2E3C7A", 0.6)
        cv.blend(bare * S(0.3, 0.0, np.abs(ph - 0.5) - 0.32), "#141A36", 0.5)   # darker band middles
        # tiny tubercle bumps / pore rows read as fine pale dots along the tract edges
        dots = 0.5 + 0.5 * np.cos(2 * math.pi * cv.u * 60)
        edge = np.exp(-(((np.abs(ph - 0.5) - 0.24) / 0.03) ** 2))
        cv.blend(edge * S(0.7, 0.95, dots), "#A8A0B8", 0.4)
        cv.blend(S(0.92, 1.0, cv.u), "#3A2C3A", 0.8)     # periproct
        cv.blend(1 - S(0.0, 0.1, cv.u), "#6A5A4C", 0.8)  # peristome
    at.paint_strip(0, test)

    def spine(cv):
        # u = along the spine (0 base .. 1 tip): dark red-brown base, orange-red shaft, pale tip
        cv.vgrad("#B04A22", "#B04A22")
        cv.blend(1 - S(0.0, 0.35, cv.u), "#4A1C14")
        cv.blend(S(0.8, 1.0, cv.u), "#E0743A", 0.8)
    at.paint_strip(1, spine)

    def tube(cv):
        # tube feet: translucent grey-pink stems, dark disc suckers
        cv.vgrad("#8E7478", "#A88E90")
        cv.blend(S(0.88, 0.96, cv.u), "#2A1A1C", 0.9)
    at.paint_strip(2, tube)

    def debris(cv):
        # four kinds across u: bleached shell bits, coralline rubble (pink-purple), green algae scrap, grey rubble
        q = (cv.u * 4).astype(int)
        n = P.fbm(cv.h, cv.w, 24, 3, 912)
        for i, (c1, c2) in enumerate((("#E8E0D0", "#B8A890"), ("#B86A92", "#7E4A6E"), ("#4E7A36", "#2E5422"),
                                      ("#9C968A", "#6A665E"))):
            m = (q == i).astype(np.float32)
            cv.blend(m, c1)
            cv.blend(m * S(0.45, 0.7, n), c2, 0.7)
    at.paint_strip(3, debris)
    tex = os.path.join(C.BUILD, "urchin_atlas.png")
    at.cv.save(tex)
    mat = C.material("urchin_mat", rough=0.55, image=C.load_image(tex, name="tuxedo_urchin"))
    root = make_root("Root", {})
    R = 0.5
    Hh = URCHIN_H / 2

    def test_pt(lat, lon, inflate=0.0):
        """lat 0 oral pole .. 1 aboral pole; returns point and outward normal on the oblate test."""
        th = math.pi * (1 - lat)
        x = (R + inflate) * math.sin(th) * math.cos(lon)
        y = (R + inflate) * math.sin(th) * math.sin(lon)
        z = (Hh + inflate) * math.cos(th)
        # the oral face is flatter than the aboral dome
        if z < 0:
            z *= 0.8
        nrm = np.array([x / (R * R), y / (R * R), z / (Hh * Hh)])
        return np.array([x, y, z]), nrm / np.linalg.norm(nrm)

    # ---- test ----
    gt = G.Geo()
    rings, sides = 16, 40
    V, UV = [], []
    for i in range(rings + 1):
        lat = i / rings
        for k in range(sides):
            lon = 2 * math.pi * k / sides
            p, _ = test_pt(lat, lon)
            V.append(p)
            UV.append((lat, k / sides))
    F = []
    for i in range(rings):
        for k in range(sides):
            a_ = i * sides + k
            b_ = i * sides + (k + 1) % sides
            F.append((a_, b_, b_ + sides, a_ + sides))
    Va = np.array(V); UVa = np.array(UV)
    gt.add(Va, F, at.uv(0, UVa[:, 0], UVa[:, 1]), cols(len(Va), CLS_TEST))
    # Aristotle's lantern: five white teeth meeting at the mouth
    for k in range(5):
        a = 2 * math.pi * k / 5
        b0 = np.array([0.03 * math.cos(a), 0.03 * math.sin(a), -Hh * 0.8 - 0.005])
        Vc, Fc = G.cone(b0, np.array([0.0, 0.0, -Hh * 0.8 - 0.03]), 0.012, 4)
        gt.add(Vc, Fc, at.uv(3, np.full(len(Vc), 0.1), np.full(len(Vc), 0.5)), cols(len(Vc), CLS_TEETH))
    test_o = geo_obj("Test", gt, root, "body", [mat])

    # ---- spines: dense, short, in the ten tracts (two staggered columns per tract), leaning a little toward the
    # aboral pole; shorter near the poles ----
    gsp = G.Geo()
    tract_w = 2 * math.pi / URCHIN_SPINE_TRACTS
    nlat = 14
    for tr in range(URCHIN_SPINE_TRACTS):
        c0 = tract_w * tr          # tract centre (bare bands sit between, at half steps)
        for i in range(nlat):
            lat = 0.1 + 0.8 * (i + 0.5) / nlat
            for col in (-1, 0, 1):
                lon = c0 + col * tract_w * 0.12 + (0.025 if i % 2 else -0.025) + rs.normal(0, 0.008)
                p, nrm = test_pt(lat, lon)
                ln = (0.045 + 0.02 * math.sin(math.pi * lat)) * (0.85 + 0.3 * rs.random())
                lean = np.array([0, 0, 0.25])
                d = nrm + lean
                d /= np.linalg.norm(d)
                Vc, Fc = G.cone(p - nrm * 0.004, p + d * ln, 0.008, 3)
                tv = np.r_[np.zeros(3), 1.0]
                gsp.add(Vc, Fc, at.uv(1, tv, np.full(len(Vc), 0.5)), cols(len(Vc), CLS_SPINE, tv, (tr * 0.1 + i * 0.037) % 1.0))
    spine_o = geo_obj("Spines", gsp, root, "leg", [mat])

    # ---- tube feet: out of the five ambulacra (every other spine tract), longer below and around the ambitus,
    # the aboral ones reaching up to hold the debris ----
    gtb = G.Geo()
    feet = []
    for amb in range(5):
        c0 = 2 * tract_w * amb
        for j, lat in enumerate((0.2, 0.42, 0.62, 0.82)):
            for col in (-1, 1):
                lon = c0 + col * tract_w * 0.2
                p, nrm = test_pt(lat, lon)
                ln = 0.2 if lat < 0.6 else 0.15
                tip_dir = nrm + np.array([0, 0, -0.3 if lat < 0.45 else 0.15])
                tip = _tentacle(gtb, lambda t, a: at.uv(2, t, a), p, tip_dir, ln, 0.006, 0.005, n=5, sides=3,
                                cls=CLS_TUBE, seed=(amb * 0.19 + j * 0.07 + (0.5 if col > 0 else 0)) % 1.0)
                feet.append((lat, p, tip))
                # sucker disc at the tip
                Vd, Fd = G.ellipsoid(tip, (0.008, 0.008, 0.005), 5, 2)
                gtb.add(Vd, Fd, at.uv(2, np.full(len(Vd), 0.95), np.full(len(Vd), 0.5)),
                        cols(len(Vd), CLS_TUBE, 1.0, (amb * 0.19 + j * 0.07 + (0.5 if col > 0 else 0)) % 1.0))
    tube_o = geo_obj("TubeFeet", gtb, root, "antenna", [mat])

    # ---- debris: seven pieces held over the aboral half (B = piece index / 7: the sim shows the first n) ----
    gd = G.Geo()
    spots = [(0.86, 0.3), (0.74, 1.6), (0.7, 3.0), (0.78, 4.3), (0.62, 5.4), (0.95, 2.2), (0.6, 0.9)]
    kinds = [0, 1, 2, 0, 3, 1, 3]
    for i, ((lat, lon), kd) in enumerate(zip(spots, kinds)):
        p, nrm = test_pt(lat, lon, inflate=0.1)
        if kd == 0:      # a curved shell fragment
            Vb, Fb = G.ellipsoid(p, (0.09, 0.06, 0.018), 8, 4)
        elif kd == 2:    # a flat algae scrap
            Vb, Fb = G.ellipsoid(p, (0.08, 0.05, 0.008), 7, 3)
        else:            # a rubble chunk
            Vb, Fb = G.ellipsoid(p, (0.05, 0.045, 0.04), 6, 4)
            Vb = Vb + rs.normal(0, 0.008, Vb.shape)
        # lay the piece on the test (its flat face along the surface), and tip it a little
        zax = np.array([0, 0, 1.0])
        ax = np.cross(zax, nrm)
        if np.linalg.norm(ax) > 1e-6:
            ang = math.acos(np.clip(zax @ nrm, -1, 1))
            ax /= np.linalg.norm(ax)
            Kx = np.array([[0, -ax[2], ax[1]], [ax[2], 0, -ax[0]], [-ax[1], ax[0], 0]])
            Rm = np.eye(3) + math.sin(ang) * Kx + (1 - math.cos(ang)) * Kx @ Kx
            Vb = (Vb - p) @ Rm.T + p
        u0 = (kd + 0.5) / 4
        gd.add(Vb, Fb, at.uv(3, np.full(len(Vb), u0) + rs.normal(0, 0.05, len(Vb)), rs.random(len(Vb))),
               cols(len(Vb), CLS_DEBRIS, i / URCHIN_DEBRIS, i / URCHIN_DEBRIS))
    debris_o = geo_obj("Debris", gd, root, "body", [mat])

    objs = [test_o, spine_o, tube_o, debris_o]
    # the urchin stands on its oral tube feet and spines: the sole is the spine tips' lowest reach under the test
    sole = -Hh * 0.8 - 0.06
    depth, dz = _recentre([test_o, spine_o, tube_o], sole)
    V = C.get_verts(debris_o); V[:, 2] += dz
    debris_o.data.vertices.foreach_set("co", V.ravel()); debris_o.data.update()
    for o in (test_o,):
        C.recalc_normals(o)
    centre = [0.0, round(float(dz), 4), 0.0]
    C.set_extras(root, species="tuxedo_urchin", lengthCm=5.0, unit="test diameter 1.0 (spines excluded), mouth down",
                 roughness=0.55, motionMode=9, depthRatio=round(depth, 3), testCentre=centre,
                 debris=URCHIN_DEBRIS, note="hidden debris pieces collapse to extras.testCentre (glTF space)")
    print(f"[urchin] depthRatio {depth:.3f}, test centre {centre}")
    return finish(root, objs, os.path.join(C.MODELS, "tuxedo_urchin.glb"), 4200)


BUILDERS = {"trochus": build_trochus, "conch": build_conch, "urchin": build_urchin}

if __name__ == "__main__":
    want = [a for a in sys.argv[1:] if a in BUILDERS] or list(BUILDERS)
    for k in want:
        BUILDERS[k]()
