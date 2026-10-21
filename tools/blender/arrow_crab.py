"""Yellowline arrow crab, Stenorhynchus seticornis (research 42): a spider crab (Inachidae) built procedurally for the
limb-rigged crawler path (fish.vert crabLimb, core CrabLegs / CrawlerSeat, the Sally Lightfoot's rig).

  * carapace: a small pear / triangle seen from above, widest over the rear legs, domed, golden-tan with fine dark
    brown and pale blue-white lines running from the rostrum back over the body;
  * rostrum: the long, thin, slightly upturned "arrow" spike, about as long as the rest of the carapace, edged with
    tiny spines;
  * eight walking legs (pereiopods 2-5), very long and thin (about twice the carapace length), the merus raised so the
    knees stand well above the body and the body rides high over the rock; orange-tan with dark red-brown joint bands;
  * two slender chelipeds held forward and down beside the rostrum, ending in small violet-blue tipped claws;
  * short eyestalks with dark eyes at the rostrum's base.

Unit: carapace length (rostrum tip to the back of the carapace) 1.0, facing +X, Blender Z up, standing on z = 0.
COLOR_0: R = t along the limb (0 coxa .. 1 dactyl tip), B = limb index / 9, A = 0.5 - x (body/eyes: R = 0).
Limbs k = 2 * pair + side (even = left = Blender +Y = glTF -Z), pairs front to back; 8 left / 9 right chela.
extras.limbPivots = 10 x vec4 (glTF space): coxa xyz, w = limb length along its centreline.
Prints CrabModel.ARROW for core/sim/CrawlerSeat.kt.

    /tmp/claude-0/bpyenv/bin/python tools/blender/arrow_crab.py
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
from inverts import Atlas, finish, geo_obj, gltf_pt, make_root   # noqa: E402

S = P.smoothstep

# carapace (without the rostrum) from x = REAR to x = FRONT; the rostrum runs on to x = TIP
REAR, FRONT, TIP = -0.38, 0.16, 0.62
BODY_Z = 0.46            # carapace underside over the ground at rest (it stands tall on its legs)
# walking legs: coxa x on the carapace side, yaw from +X (deg), total length (carapace lengths)
LEGS = ((0.08, 58.0, 1.38), (-0.02, 82.0, 1.58), (-0.13, 108.0, 1.50), (-0.24, 134.0, 1.36))
KNEE_T = 0.36            # merus-carpus joint along the leg (the high point)
KNEE_RISE = 0.30         # knee height over the coxa, per unit leg length
CHELA_L = 1.0


def half_width(x):
    """Carapace half-width at x: rounded at the back, widest over the rear legs, tapering to a point at the eyes."""
    t = (x - REAR) / (FRONT - REAR)
    if t < 0.3:
        return 0.24 * math.sqrt(max(0.0, 1 - ((0.3 - t) / 0.32) ** 2))
    return 0.24 * (1 - 0.78 * ((t - 0.3) / 0.7) ** 1.1)


def half_height(x):
    t = (x - REAR) / (FRONT - REAR)
    return 0.16 * math.sin(math.pi * min(1.0, t * 0.75 + 0.06)) ** 0.7 * (1 - 0.4 * S(0.6, 1.0, t))


def paint(at):
    def shell(cv):   # u along the body (0 rear .. 1 rostrum tip), v round (0 belly .. 1 dorsal midline)
        cv.vgrad("#E8C68A", "#D69A48")
        # the fine longitudinal lines: dark brown, each flanked by a pale blue-white one
        for k, vv in enumerate((0.98, 0.86, 0.74, 0.62, 0.5, 0.38)):
            line = 1 - S(0.0, 0.018, np.abs(cv.v - vv))
            cv.blend(line * S(0.0, 0.1, cv.u), "#2A160C", 1.0)
            pale = 1 - S(0.0, 0.01, np.abs(cv.v - (vv - 0.045)))
            cv.blend(pale * S(0.0, 0.1, cv.u) * (1 - S(0.75, 0.85, cv.u)), "#DDE6F0", 0.7)
        # the rostrum: darker, reddish brown toward the tip
        cv.blend(S(0.72, 0.95, cv.u), "#8A4A26", 0.7)
        cv.blend(1 - S(0.0, 0.25, cv.v), "#F1DDB4", 0.6)        # pale belly
        n = P.fbm(cv.h, cv.w, 40, 3, 21)
        cv.multiply(0.93 + 0.12 * n)

    def leg(cv):     # u along the leg (0 coxa .. 1 tip)
        cv.vgrad("#E09A50", "#F0B868")
        for j in (0.08, KNEE_T, 0.62, 0.84):
            cv.blend(1 - S(0.0, 0.022, np.abs(cv.u - j)), "#6E2414", 0.9)
        cv.blend(S(0.9, 1.0, cv.u), "#5A3420", 0.7)              # dark dactyl tips
        cv.multiply(0.92 + 0.1 * P.fbm(cv.h, cv.w, 24, 2, 5))

    def claw(cv):
        cv.vgrad("#C8642E", "#E08848")
        for j in (0.12, 0.45, 0.72):
            cv.blend(1 - S(0.0, 0.02, np.abs(cv.u - j)), "#6E2414", 0.85)
        cv.blend(S(0.8, 0.9, cv.u), "#6A58C8")                    # violet-blue fingers
        cv.blend(S(0.96, 1.0, cv.u), "#2A2060", 0.8)

    def eye(cv):
        cv.vgrad("#C88A48", "#D9A060")
        cv.blend(S(0.45, 0.55, cv.u), "#1A140E")
        cv.blend(S(0.7, 0.9, cv.u) * (1 - S(0.0, 0.2, np.abs(cv.v - 0.7))), "#5A4A30", 0.5)
    at.paint_strip(0, shell)
    at.paint_strip(1, leg)
    at.paint_strip(2, claw)
    at.paint_strip(3, eye)


def cols(V, t, k):
    n = len(V)
    return np.stack([np.clip(np.broadcast_to(np.asarray(t, float), n), 0, 1), np.zeros(n),
                     np.broadcast_to(np.asarray(k, float) / 9.0, n), np.clip(0.5 - V[:, 0], 0, 1)], -1)


def leg_line(k):
    """Centreline of walking leg k (coxa to dactyl tip), Blender frame."""
    pair, side = k // 2, (1.0 if k % 2 == 0 else -1.0)
    x0, yaw, L = LEGS[pair]
    w = half_width(x0)
    a = math.radians(yaw)
    d = np.array([math.cos(a), side * math.sin(a), 0.0])
    hip = np.array([x0, side * w * 0.92, BODY_Z + 0.05])
    # merus up and out to the knee, then the carpus-propodus-dactyl down to the ground in a gentle curve
    knee = hip + d * (0.30 * L) + np.array([0, 0, KNEE_RISE * L])
    reach = 0.80 * L
    foot = np.array([hip[0], hip[1], 0.0]) + d * reach
    ankle = knee + (foot - knee) * 0.55 + np.array([0, 0, 0.06 * L]) + d * 0.04 * L
    pts = np.vstack([G.bezier(hip, hip + (knee - hip) * 0.4, knee - d * 0.05 * L, knee, 10)[:-1],
                     G.bezier(knee, knee + d * 0.12 * L, ankle, foot, 16)])
    # rescale along the path so the centreline is exactly L long (the knee stays where KNEE_T says)
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))]
    return pts, seg[-1]


def chela_line(side):
    base = np.array([FRONT - 0.02, side * 0.07, BODY_Z + 0.07])
    elbow = base + np.array([0.28, side * 0.16, 0.1])
    wrist = elbow + np.array([0.36, side * 0.02, -0.28])
    tip = wrist + np.array([0.2, -side * 0.05, -0.2])
    return G.bezier(base, elbow, wrist, tip, 16)


def build_arrow_crab():
    C.reset_scene()
    at = Atlas(512, 256, 4)
    paint(at)
    path = os.path.join(C.BUILD, "arrow_crab.png")
    at.cv.save(path)
    img = C.load_image(path, name="arrow_crab")
    mat = C.material("ArrowCrabMat", image=img, rough=0.42)
    root = make_root("Root", {})

    # ---- carapace + rostrum: lofted rings ----
    gb = G.Geo()
    xs = np.r_[np.linspace(REAR, FRONT, 22), np.linspace(FRONT, TIP, 14)[1:]]
    sides = 16
    rings = []
    for x in xs:
        if x <= FRONT:
            w, h = half_width(x), half_height(x)
            zc = BODY_Z + 0.03 + 0.55 * h
        else:
            r = (x - FRONT) / (TIP - FRONT)
            w = 0.045 * (1 - r) ** 0.85 + 0.004
            h = w * 0.85
            zc = BODY_Z + 0.03 + 0.55 * half_height(FRONT) + 0.10 * r * r     # gently upturned
        ring = []
        for j in range(sides):
            a = 2 * math.pi * j / sides
            s_, c_ = math.sin(a), math.cos(a)
            # flatter belly than back
            zz = h * (s_ if s_ > 0 else 0.45 * s_)
            ring.append((x, w * c_, zc + zz))
        rings.append(ring)
    V = np.array(rings).reshape(-1, 3)
    F = []
    for i in range(len(xs) - 1):
        for j in range(sides):
            a = i * sides + j
            b = i * sides + (j + 1) % sides
            F.append((a, b, b + sides, a + sides))
    # caps
    c0 = len(V); V = np.vstack([V, [[REAR - 0.01, 0, BODY_Z + 0.03 + 0.55 * half_height(REAR)]]])
    F += [((j + 1) % sides, j, c0) for j in range(sides)]
    c1 = len(V); V = np.vstack([V, [[TIP + 0.02, 0, rings[-1][0][2]]]])
    base = (len(xs) - 1) * sides
    F += [(base + j, base + (j + 1) % sides, c1) for j in range(sides)]
    u = np.clip((V[:, 0] - REAR) / (TIP - REAR), 0, 1)
    ang = np.arctan2(V[:, 2] - V[:, 2].mean(), np.abs(V[:, 1]) + 1e-6)
    vv = np.clip(0.5 + ang / math.pi, 0, 1)
    gb.add(V, F, at.uv(0, u, vv), cols(V, 0.0, 0))
    # tiny spines along the rostrum and a few on the carapace (setae-like)
    for i, x in enumerate(np.linspace(FRONT + 0.04, TIP - 0.05, 7)):
        r = (x - FRONT) / (TIP - FRONT)
        zc = BODY_Z + 0.03 + 0.55 * half_height(FRONT) + 0.10 * r * r
        w = 0.045 * (1 - r) ** 0.85 + 0.004
        for sd in (1, -1):
            b0 = np.array([x, sd * w * 0.8, zc + w * 0.3])
            Vc, Fc = G.cone(b0, b0 + np.array([0.02, sd * 0.025, 0.012]), 0.004, 3)
            gb.add(Vc, Fc, at.uv(0, np.full(len(Vc), min(0.99, (x - REAR) / (TIP - REAR))), np.full(len(Vc), 0.5)), cols(Vc, 0.0, 0))
    for x in (-0.25, -0.1, 0.05):
        b0 = np.array([x, 0.0, BODY_Z + 0.03 + 1.53 * half_height(x)])
        Vc, Fc = G.cone(b0, b0 + np.array([0.01, 0, 0.04]), 0.008, 4)
        gb.add(Vc, Fc, at.uv(0, np.full(len(Vc), (x - REAR) / (TIP - REAR)), np.full(len(Vc), 0.98)), cols(Vc, 0.0, 0))
    body_o = geo_obj("Body", gb, root, "shell", [mat])
    C.recalc_normals(body_o)

    # ---- walking legs ----
    gl = G.Geo()
    lines, lens = [], []
    for k in range(8):
        pts, L = leg_line(k)
        lines.append(pts)
        lens.append(L)
        t = np.linspace(0, 1, len(pts))
        r = 0.018 * (1 - 0.5 * t) * (1 + 0.3 * np.exp(-((t - KNEE_T) / 0.03) ** 2)) + 0.003
        Vt, Ft, tv, av = G.tube(pts, r, 6, cap_start=True, cap_end=True)
        gl.add(Vt, Ft, at.uv(1, tv, av), cols(Vt, tv, k))
    # ---- chelipeds with small claws ----
    gc = G.Geo()
    for i, side in enumerate((1.0, -1.0)):
        k = 8 + i
        pts = chela_line(side)
        L = float(np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1)))
        lines.append(pts)
        lens.append(L)
        t = np.linspace(0, 1, len(pts))
        r = 0.015 * (1 - 0.3 * t) + 0.011 * np.exp(-((t - 0.78) / 0.07) ** 2)      # the palm swells before the fingers
        Vt, Ft, tv, av = G.tube(pts, r, 6, cap_start=True, cap_end=False)
        gc.add(Vt, Ft, at.uv(2, tv * 0.85, av), cols(Vt, tv * 0.85, k))
        # two fingers
        d = pts[-1] - pts[-2]
        d /= np.linalg.norm(d)
        n = np.cross(d, [0, 0, 1.0]); n /= np.linalg.norm(n)
        for f in (1, -1):
            b0 = pts[-1]
            Vf, Ff, tf, af = G.tube([b0, b0 + d * 0.06 + n * f * 0.012, b0 + d * 0.11 + n * f * 0.004], [0.011, 0.007, 0.002], 5,
                                    cap_end=True)
            gc.add(Vf, Ff, at.uv(2, 0.85 + 0.15 * tf, af), cols(Vf, 0.85 + 0.15 * tf, k))
    pivots = [0.0] * 40
    for k, Cl in enumerate(lines):
        pivots[4 * k:4 * k + 4] = gltf_pt(Cl[0]) + [lens[k]]
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 8, "limbPivots": pivots})
    geo_obj("Claws", gc, root, "leg", [mat], extras={"claw": True, "limbPivots": pivots})

    # ---- eyes on short stalks at the rostrum base ----
    ge = G.Geo()
    eyes = []
    for side in (1.0, -1.0):
        b0 = np.array([FRONT - 0.03, side * 0.035, BODY_Z + 0.03 + 0.55 * half_height(FRONT) + 0.03])
        tip = b0 + np.array([0.02, side * 0.05, 0.035])
        Vt, Ft, tv, av = G.tube([b0, tip], [0.016, 0.014], 6, cap_end=False)
        ge.add(Vt, Ft, at.uv(3, tv * 0.45, av), cols(Vt, 0.0, 0))
        Ve, Fe = G.ellipsoid(tip + np.array([0.008, side * 0.008, 0.004]), (0.024, 0.022, 0.022), 10, 6)
        ge.add(Ve, Fe, at.uv(3, np.full(len(Ve), 0.75), np.full(len(Ve), 0.5)), cols(Ve, 0.0, 0))
        eyes.append(tip)
    geo_obj("Eyes", ge, root, "eye", [mat], extras={"pivot": gltf_pt(np.mean(eyes, 0))})

    C.set_extras(root, species="stenorhynchus_seticornis", lengthCm=4.5,
                 unit="carapace length (rostrum tip to back) 1.0, crab faces +X, walks forward and to the sides",
                 iridescence=0.04, roughness=0.42, gait="any", limbRig="crab_legs", limbPivots=pivots)
    print_model(lines, lens, V)
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    return finish(root, objs, os.path.join(C.MODELS, "arrow_crab.glb"), 6000)


def print_model(lines, lens, BV):
    """CrabModel.ARROW (glTF space): coxae, rest foot tips, knees (t 0.5), propodi (t 0.75), leg lengths, the chelae
    (pivot, tip, lowest point) and the carapace contact points (underside, rim, top)."""
    def g(p):
        return [round(x, 4) for x in gltf_pt(p)]

    def at(Cl, q):
        s = np.r_[0.0, np.cumsum(np.linalg.norm(np.diff(Cl, axis=0), axis=1))]
        return np.array([np.interp(q * s[-1], s, Cl[:, i]) for i in range(3)])
    hip, tip, knee, mid = [], [], [], []
    for k in range(8):
        Cl = lines[k]
        hip += g(Cl[0]); tip += g(Cl[-1]); knee += g(at(Cl, 0.5)); mid += g(at(Cl, 0.75))
    cp, ct, cl = [], [], []
    for k in (8, 9):
        Cl = lines[k]
        cp += g(Cl[0]); ct += g(Cl[-1]); cl += g(Cl[np.argmin(Cl[:, 2])])
    body = []
    for x in np.linspace(REAR + 0.04, FRONT - 0.02, 5):
        for f in (-0.6, 0.0, 0.6):
            y = f * half_width(x)
            zc = BODY_Z + 0.03 + 0.55 * half_height(x)
            body += g((x, y, zc - 0.45 * half_height(x) * math.sqrt(max(0.0, 1 - f * f))))
    for x in np.linspace(REAR + 0.05, FRONT - 0.03, 3):
        for sd in (1, -1):
            body += g((x, sd * half_width(x), BODY_Z + 0.03 + 0.55 * half_height(x)))
    body += g((TIP, 0.0, BODY_Z + 0.03 + 0.55 * half_height(FRONT) + 0.10))
    body += g((-0.1, 0.0, BODY_Z + 0.03 + 1.55 * half_height(-0.1)))
    print("[arrow] CrabModel.ARROW")
    for name, arr in (("hip", hip), ("tip", tip), ("knee", knee), ("mid", mid), ("legW", [round(x, 3) for x in lens[:8]]),
                      ("clawPivot", cp), ("clawTip", ct), ("clawLow", cl), ("body", body)):
        print(f"  {name} = doubleArrayOf({', '.join(str(x) for x in arr)}),")
    print(f"  body points {len(body) // 3}")


if __name__ == "__main__":
    build_arrow_crab()
