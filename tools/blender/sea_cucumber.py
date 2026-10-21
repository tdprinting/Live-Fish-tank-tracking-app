"""Tiger tail sea cucumber, Holothuria hilla (Holothuriidae; research 45 §4), for the spine-skinned body path (fish.vert
mode 10): the body follows the sim's trail spline like an eel's, so it lies along the path it crawled over the sand.

  * body: a long, soft cylinder with a flat creeping sole (the trivium), thickest a fifth of the way back, tapering to a
    rounded tail with the anus at the tip; relaxed length 25 cm, about 3 cm across (research 45 §4);
  * colour: tan to orange-brown, darker chocolate cross-bands and blotches that close up into the "tiger" stripes toward
    the tail, a paler grey-tan sole dotted with tube feet;
  * papillae: rows of soft conical warts over the back and a fringe along each flank, cream with white tips;
  * oral tentacles: a ring of 20 short peltate (mop-headed) tentacles round the mouth at the front, which it presses on
    the sand and pushes into its mouth one at a time (fish.vert poses them from uPick = out and uSwim.w = phase).

Unit: body length 1.0 along X, head (mouth) toward +X at x = +0.5, Blender Z up, the sole on z = 0.
COLOR_0: body and papillae R = 0, G = 0, B = 0, A = u = 0.5 - x (spine coordinate, the papillae carry their base's u).
Tentacles (part "antenna"): R = t along the tentacle (0 base .. 1 tip), G = tentacle k / 20 (feeding order), B = 0,
A = u of the oral ring.

    /tmp/claude-0/bpyenv/bin/python tools/blender/sea_cucumber.py
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import common as C          # noqa: E402
import geomlib as G         # noqa: E402
import paintlib as P        # noqa: E402
from inverts import Atlas, finish, geo_obj, make_root   # noqa: E402

S = P.smoothstep

HEAD_X, TAIL_X = 0.47, -0.5
RINGS, SIDES = 56, 20
N_TENT = 20
TAN, BAND, SOLE, PAP = "#B9773A", "#4A2814", "#9C8A72", "#D8A066"


def radius(t):
    """Half-width along the body (t 0 head .. 1 tail)."""
    r = 0.034 + 0.028 * S(0.0, 0.2, t) - 0.006 * S(0.45, 0.75, t)
    r *= 1.0 - 0.5 * S(0.72, 1.0, t)
    # rounded ends
    head = math.sqrt(max(0.0, min(1.0, t / 0.035)))
    tail = math.sqrt(max(0.0, min(1.0, (1.0 - t) / 0.03)))
    return max(0.004, r * (0.55 + 0.45 * head) * (0.35 + 0.65 * tail))


def ring_point(x, r, th):
    """th: 0 = bottom centre (the seam, on the sole), increasing round the right flank, over the back, down the left."""
    s, c = math.sin(th), -math.cos(th)          # c = +1 on top, -1 under
    h = 0.82 if c > 0 else 0.5                  # high arched back, flat sole
    zc = 0.52 * r
    return np.array([x, -s * r * 1.05, zc + c * r * h])


def build():
    C.reset_scene()
    at = Atlas(512, 256, 4)
    rs = P.rng(45)

    def body_strip(cv):
        # u = t along (head .. tail), v = a around (0 sole centre .. 0.5 back .. 1 sole centre)
        t, a = cv.u, cv.v
        cv.rgb[:] = P.hex2rgb(TAN) if hasattr(P, "hex2rgb") else C.hex2rgb(TAN)
        n1 = P.fbm(cv.h, cv.w, 6, 4, 11, aspect=0.25)
        cv.multiply(0.85 + 0.3 * n1)
        back = 1 - np.abs(a - 0.5) * 2                     # 1 on the back, 0 under
        # tiger bands: cross-bands with ragged edges, wider and closer toward the tail
        wob = 0.03 * (P.fbm(cv.h, cv.w, 10, 3, 12, aspect=0.25) - 0.5)
        freq = 5 + 6 * t
        bands_ = 0.5 + 0.5 * np.sin(2 * math.pi * (freq * t + wob * 14))
        brk = P.fbm(cv.h, cv.w, 8, 3, 14, aspect=0.5)            # breaks the rings into saddles and blotches
        m = S(0.5, 0.85, bands_) * S(0.35, 0.6, brk + 0.25 * t) * (0.35 + 0.65 * S(0.25, 0.8, t)) * S(0.15, 0.45, back)
        cv.blend(m, BAND, 0.85)
        # blotches on the front half
        bl = S(0.62, 0.75, P.fbm(cv.h, cv.w, 14, 3, 13, aspect=0.25)) * (1 - S(0.4, 0.7, t)) * S(0.2, 0.5, back)
        cv.blend(bl, BAND, 0.6)
        # paler sole with rows of tube feet
        sole = 1 - S(0.12, 0.22, back)
        cv.blend(sole, SOLE, 1.0)
        feet = 0.5 + 0.5 * np.sin(2 * math.pi * 160 * t) * np.sin(2 * math.pi * 14 * a)
        cv.blend(S(0.8, 0.95, feet) * sole, "#D9CDB8", 0.7)
        # head and anus darker
        cv.blend(1 - S(0.0, 0.04, t), "#3A2A20", 0.7)
        cv.blend(S(0.975, 1.0, t), "#2A1C14", 0.8)

    def papilla_strip(cv):
        cv.vgrad(PAP, PAP)
        cv.blend(S(0.6, 0.95, cv.u), "#FFF6E0", 0.6)          # pale tips
        cv.blend(1 - S(0.0, 0.3, cv.u), TAN, 0.7)            # base takes the body colour

    def tentacle_strip(cv):
        cv.vgrad("#6B5A48", "#7E6B57")
        cv.blend(S(0.6, 0.85, cv.u), "#C9B9A0", 0.8)          # pale mop heads
        cv.multiply(0.9 + 0.1 * np.sin(2 * math.pi * 12 * cv.v))

    at.paint_strip(0, body_strip)
    at.paint_strip(1, papilla_strip)
    at.paint_strip(2, tentacle_strip)
    tex = os.path.join(C.BUILD, "sea_cucumber_atlas.png")
    at.cv.save(tex)
    mat = C.material("sea_cucumber_mat", rough=0.55, image=C.load_image(tex, name="sea_cucumber"))
    root = make_root("Root", dict(species="tiger_tail_cucumber", lengthCm=25.0,
                                  unit="body length 1.0, mouth toward +X, sole on z = 0",
                                  roughness=0.55, note="spine-skinned soft body (fish.vert mode 10)"))

    # ---- body ----
    V, UV, COL, F = [], [], [], []
    ts = np.linspace(0, 1, RINGS)
    for i, t in enumerate(ts):
        x = HEAD_X + (TAIL_X - HEAD_X) * t
        r = radius(t)
        for k in range(SIDES + 1):
            th = 2 * math.pi * k / SIDES
            V.append(ring_point(x, r, th))
            UV.append(at.uv(0, np.array([t]), np.array([k / SIDES]))[0])
            COL.append((0, 0, 0, 0.5 - x))
    W = SIDES + 1
    for i in range(RINGS - 1):
        for k in range(SIDES):
            a, b = i * W + k, i * W + k + 1
            F.append((a, a + W, b + W, b))
    # end caps: the mouth (a shallow pit) and the anus
    for end, t in ((0, 0.0), (RINGS - 1, 1.0)):
        x = HEAD_X + (TAIL_X - HEAD_X) * t
        r = radius(t)
        c = len(V)
        inset = -0.01 if end == 0 else 0.004
        V.append(np.array([x + (inset if end == 0 else -inset), 0.0, 0.52 * r]))
        UV.append(at.uv(0, np.array([t]), np.array([0.5]))[0])
        COL.append((0, 0, 0, 0.5 - x))
        base = end * W
        for k in range(SIDES):
            a, b = base + k, base + k + 1
            F.append((b, a, c) if end == 0 else (a, b, c))
    g = G.Geo()
    g.add(np.array(V), F, np.array(UV), COL)
    body_o = geo_obj("Body", g, root, "body", [mat])

    # ---- papillae: soft cones over the back and a fringe down each flank ----
    gp = G.Geo()
    pts = P.poisson(rs, 120, 0.034, box=(0.05, 0.95, 0.0, 1.0))
    fringe = [(t, 0.24) for t in np.linspace(0.06, 0.92, 18)] + [(t, 0.76) for t in np.linspace(0.08, 0.94, 18)]
    for (t, a) in [(p[0], 0.3 + 0.4 * p[1]) for p in pts] + fringe:
        x = HEAD_X + (TAIL_X - HEAD_X) * t
        r = radius(t)
        th = 2 * math.pi * a
        base = ring_point(x, r * 0.97, th)
        centre = np.array([x, 0.0, 0.52 * r])
        nrm = base - centre
        nrm[0] = 0.0
        nrm /= np.linalg.norm(nrm) + 1e-9
        h = (0.006 + 0.004 * rs.uniform()) * (0.6 + 0.4 * r / 0.062)
        Vc, Fc = G.cone(base, base + nrm * h + np.array([-0.003, 0, 0]), 0.006 * (0.7 + 0.3 * r / 0.062), sides=5)
        nV = len(Vc)
        tt = np.array([1.0 if j == nV - 1 else 0.0 for j in range(nV)])
        gp.add(Vc, Fc, at.uv(1, tt, np.full(nV, 0.5)), [(0, 0, 0, 0.5 - x)] * nV)
    pap_o = geo_obj("Papillae", gp, root, "body", [mat])

    # ---- oral tentacles: a ring of short stalks with flat mop heads, splayed forward and down ----
    gt = G.Geo()
    r0 = radius(0.03)
    xr = HEAD_X - 0.006
    u_ring = 0.5 - xr
    for k in range(N_TENT):
        th = 2 * math.pi * (k + 0.5) / N_TENT
        s, c = math.sin(th), -math.cos(th)
        root_p = np.array([xr, -s * r0 * 0.75, 0.52 * radius(0.0) + c * r0 * 0.55 + 0.004])
        out = np.array([0.0, -s, c * 0.7 - 0.25])
        out /= np.linalg.norm(out)
        fwd = np.array([1.0, 0.0, -0.35])
        d = 0.65 * fwd / np.linalg.norm(fwd) + 0.55 * out
        d /= np.linalg.norm(d)
        ln = 0.015 + 0.002 * math.cos(3 * th)
        tip = root_p + d * ln
        pts_ = G.bezier(root_p, root_p + d * ln * 0.35, tip - d * ln * 0.3 + np.array([0, 0, 0.002]), tip, 4)
        Vt, Ft, tv, av = G.tube(pts_, np.linspace(0.0042, 0.0034, 4), 4, cap_end=False)
        order = ((k * 7) % N_TENT) / N_TENT          # feeding order round the ring (not neighbour to neighbour)
        gt.add(Vt, Ft, at.uv(2, tv * 0.7, av), [(float(v), order, 0, u_ring) for v in tv])
        # peltate head: a flattened disc at the tip, facing along the tentacle
        Ve, Fe = G.ellipsoid(tip + d * 0.003, (0.0085, 0.0085, 0.003), 6, 3,
                             rot=_basis_to(d))
        gt.add(Ve, Fe, at.uv(2, np.full(len(Ve), 0.9), np.linspace(0, 1, len(Ve))), [(1.0, order, 0, u_ring)] * len(Ve))
    tent_o = geo_obj("Tentacles", gt, root, "antenna", [mat])

    objs = [body_o, pap_o, tent_o]
    for o in objs:
        C.recalc_normals(o)
    return finish(root, objs, os.path.join(C.MODELS, "tiger_tail_cucumber.glb"), 5200)


def _basis_to(d):
    """Rotation matrix taking local Z to direction d (ellipsoid's thin axis along the tentacle)."""
    z = d / np.linalg.norm(d)
    x = np.cross([0, 0, 1.0], z)
    if np.linalg.norm(x) < 1e-6:
        x = np.array([1.0, 0, 0])
    x /= np.linalg.norm(x)
    y = np.cross(z, x)
    return np.stack([x, y, z], 1)


if __name__ == "__main__":
    build()
