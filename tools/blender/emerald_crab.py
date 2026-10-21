"""Emerald crab, Mithraculus sculptus (research 43 §1): a small majid crab built procedurally for the limb-rigged
crawler path (fish.vert crabLimb, core CrabLegs / CrawlerSeat), like the Sally Lightfoot and the arrow crab.

  * carapace: about as wide as long, rounded in front of the rear legs and narrowing to the orbits, flat-domed, shiny
    emerald green, finely sculpted (an H-shaped gastric / cardiac groove, low tubercles, four low teeth on each
    front-side margin) with a short notched rostrum between the eyes;
  * eight walking legs (pereiopods 2-5), stout and a little longer than the carapace, paler olive green and hairy
    (rows of setae along the last segments), dark amber hooked dactyls; held in a low crab stance;
  * two equal chelipeds with heavy green palms and spoon-shaped fingers tipped white (they scoop bubble algae);
  * short eyestalks sunk in the orbits, dark red-brown eyes.

Unit: carapace length (rostrum to the back of the carapace) 1.0, facing +X, Blender Z up, standing on z = 0.
COLOR_0: R = t along the limb (0 coxa .. 1 dactyl tip), B = limb index / 9, A = 0.5 - x (body/eyes: R = 0).
Limbs k = 2 * pair + side (even = left = Blender +Y = glTF -Z), pairs front to back; 8 left / 9 right chela.
extras.limbPivots = 10 x vec4 (glTF space): coxa xyz, w = limb length along its centreline.
Prints CrabModel.EMERALD for core/sim/CrawlerSeat.kt.

    /tmp/claude-0/bpyenv/bin/python tools/blender/emerald_crab.py
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

REAR, FRONT = -0.5, 0.5
BODY_Z = 0.17            # carapace underside over the ground at rest: a low crab stance
DOME = 0.13               # dome height over the rim at the middle
# walking legs: coxa x on the carapace side, yaw from +X (deg), total length (carapace lengths)
LEGS = ((0.16, 52.0, 0.9), (0.0, 78.0, 1.0), (-0.15, 104.0, 0.97), (-0.29, 130.0, 0.85))
KNEE_T = 0.36            # merus-carpus joint along the leg (the high point)
KNEE_RISE = 0.26          # knee height over the coxa, per unit leg length
GREEN, GREEN_D, GREEN_L = "#2A6A30", "#163E1E", "#3E8A44"


def half_width(x):
    """Carapace half-width at x: broad and rounded over the legs, narrowing to the orbits (about as wide as long)."""
    t = (x - REAR) / (FRONT - REAR)
    if t < 0.42:
        return 0.56 * math.sqrt(max(0.0, 1 - ((0.42 - t) / 0.44) ** 2))
    return 0.56 - 0.34 * ((t - 0.42) / 0.58) ** 1.6


def dome(x, y):
    """Height of the carapace top over the rim at (x, y), with the grooves and tubercles of its sculpture."""
    w = max(half_width(x), 1e-3)
    r = min(1.0, abs(y) / w)
    t = (x - REAR) / (FRONT - REAR)
    h = DOME * (1 - r * r) ** 0.6 * (0.55 + 0.45 * math.sin(math.pi * min(1.0, 0.15 + 0.85 * t)))
    # H-shaped groove: two lengthwise branches either side of the midline, joined across behind the stomach region
    g = math.exp(-((abs(y) - 0.17) / 0.03) ** 2) * S(0.15, 0.3, t) * (1 - S(0.7, 0.85, t))
    g += math.exp(-((t - 0.45) / 0.03) ** 2) * (1 - S(0.15, 0.2, abs(y)))
    # low tubercles over the dome
    tb = (math.sin(x * 37.0 + 1.3) * math.sin(y * 41.0 + 0.4)) ** 8
    return max(0.0, h - 0.025 * g + 0.012 * tb * (1 - r))


def paint(at):
    def shell(cv):   # u along the body (0 rear .. 1 rostrum), v round (0 belly .. 1 dorsal midline)
        cv.vgrad("#24502A", GREEN)
        n = P.fbm(cv.h, cv.w, 32, 4, 11)
        cv.blend(S(0.55, 0.8, n), GREEN_L, 0.55)            # glossy lighter mottling over the dome
        cv.blend(S(0.6, 0.75, P.fbm(cv.h, cv.w, 90, 2, 3)) * S(0.4, 0.9, cv.v), "#7CC878", 0.35)   # tubercle highlights
        # the grooves read darker
        cv.blend((1 - S(0.0, 0.03, np.abs(cv.v - 0.82))) * S(0.15, 0.3, cv.u) * (1 - S(0.7, 0.85, cv.u)), GREEN_D, 0.7)
        cv.blend((1 - S(0.0, 0.025, np.abs(cv.u - 0.45))) * S(0.85, 0.95, cv.v), GREEN_D, 0.6)
        cv.blend(1 - S(0.0, 0.3, cv.v), "#6E8A4E", 0.7)      # paler olive underside
        cv.blend(S(0.92, 1.0, cv.u), "#24502A", 0.6)          # rostrum
        cv.multiply(0.94 + 0.1 * n)

    def leg(cv):     # u along the leg (0 coxa .. 1 tip)
        cv.vgrad("#4A6E34", "#6E8A48")
        for j in (0.1, KNEE_T, 0.64, 0.86):
            cv.blend(1 - S(0.0, 0.02, np.abs(cv.u - j)), "#33502A", 0.7)
        cv.blend(S(0.5, 0.9, cv.u) * S(0.5, 0.8, P.fbm(cv.h, cv.w, 60, 2, 7)), "#8A8A58", 0.5)   # matted setae, fouled
        cv.blend(S(0.88, 0.97, cv.u), "#7A4A1C", 0.85)       # amber dactyls
        cv.blend(S(0.97, 1.0, cv.u), "#2A1A0C", 0.9)
        cv.multiply(0.92 + 0.12 * P.fbm(cv.h, cv.w, 24, 2, 5))

    def claw(cv):
        cv.vgrad("#2A6A34", "#3A8A42")
        cv.blend(S(0.55, 0.75, P.fbm(cv.h, cv.w, 40, 3, 9)), GREEN_L, 0.45)
        cv.blend(S(0.78, 0.9, cv.u), "#C8D8B0", 0.8)
        cv.blend(S(0.9, 0.98, cv.u), "#F2EEDC")                # white spoon tips

    def eye(cv):
        cv.vgrad("#3A6A34", "#4A7A40")
        cv.blend(S(0.45, 0.55, cv.u), "#3A1410")
        cv.blend(S(0.7, 0.9, cv.u) * (1 - S(0.0, 0.2, np.abs(cv.v - 0.7))), "#8A4A3A", 0.5)
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
    hip = np.array([x0, side * w * 0.9, BODY_Z + 0.04])
    knee = hip + d * (0.34 * L) + np.array([0, 0, KNEE_RISE * L])
    foot = np.array([hip[0], hip[1], 0.0]) + d * (0.74 * L)
    ankle = knee + (foot - knee) * 0.55 + np.array([0, 0, 0.05 * L]) + d * 0.05 * L
    pts = np.vstack([G.bezier(hip, hip + (knee - hip) * 0.4, knee - d * 0.05 * L, knee, 8)[:-1],
                     G.bezier(knee, knee + d * 0.12 * L, ankle, foot, 12)])
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))]
    return pts, seg[-1]


def chela_line(side):
    base = np.array([FRONT - 0.18, side * 0.2, BODY_Z + 0.05])
    elbow = base + np.array([0.12, side * 0.1, 0.04])
    wrist = elbow + np.array([0.14, -side * 0.05, -0.02])
    tip = wrist + np.array([0.13, -side * 0.06, -0.06])
    return G.bezier(base, elbow, wrist, tip, 14)


def build_emerald_crab():
    C.reset_scene()
    at = Atlas(512, 256, 4)
    paint(at)
    path = os.path.join(C.BUILD, "emerald_crab.png")
    at.cv.save(path)
    img = C.load_image(path, name="emerald_crab")
    mat = C.material("EmeraldCrabMat", image=img, rough=0.4)     # shiny carapace
    root = make_root("Root", {})

    # ---- carapace: lofted rings, the top raised by the dome and its sculpture ----
    gb = G.Geo()
    xs = np.linspace(REAR, FRONT, 24)
    sides = 22
    rings = []
    for x in xs:
        w = half_width(x)
        ring = []
        for j in range(sides):
            a = 2 * math.pi * j / sides
            s_, c_ = math.sin(a), math.cos(a)
            y = w * c_
            # four low teeth on each front-side margin
            t = (x - REAR) / (FRONT - REAR)
            if 0.45 < t < 0.92 and abs(c_) > 0.85:
                y *= 1 + 0.035 * max(0.0, math.sin((t - 0.45) / 0.47 * 4 * math.pi)) ** 2
            if s_ > 0:
                z = BODY_Z + 0.06 + s_ ** 0.35 * dome(x, y)
            else:
                z = BODY_Z + 0.06 + 0.06 * s_
            ring.append((x, y, z))
        rings.append(ring)
    V = np.array(rings).reshape(-1, 3)
    F = []
    for i in range(len(xs) - 1):
        for j in range(sides):
            a = i * sides + j
            b = i * sides + (j + 1) % sides
            F.append((a, b, b + sides, a + sides))
    c0 = len(V); V = np.vstack([V, [[REAR - 0.005, 0, BODY_Z + 0.07]]])
    F += [((j + 1) % sides, j, c0) for j in range(sides)]
    c1 = len(V); V = np.vstack([V, [[FRONT + 0.005, 0, BODY_Z + 0.08]]])
    base = (len(xs) - 1) * sides
    F += [(base + j, base + (j + 1) % sides, c1) for j in range(sides)]
    u = np.clip((V[:, 0] - REAR) / (FRONT - REAR + 0.08), 0, 1)
    ang = np.arctan2(V[:, 2] - (BODY_Z + 0.06), np.abs(V[:, 1]) + 1e-6)
    vv = np.clip(0.5 + ang / math.pi, 0, 1)
    gb.add(V, F, at.uv(0, u, vv), cols(V, 0.0, 0))
    # the notched rostrum: two short blunt horns between the eyes
    for sd in (1, -1):
        b0 = np.array([FRONT - 0.04, sd * 0.05, BODY_Z + 0.1])
        Vt, Ft, tv, av = G.tube([b0, b0 + np.array([0.08, sd * 0.02, -0.01])], [0.035, 0.012], 6, cap_end=True)
        gb.add(Vt, Ft, at.uv(0, 0.94 + 0.05 * tv, av), cols(Vt, 0.0, 0))
    body_o = geo_obj("Body", gb, root, "shell", [mat])
    C.recalc_normals(body_o)

    # ---- walking legs, hairy toward the tips ----
    gl = G.Geo()
    lines, lens = [], []
    for k in range(8):
        pts, L = leg_line(k)
        lines.append(pts)
        lens.append(L)
        t = np.linspace(0, 1, len(pts))
        r = 0.06 * (1 - 0.55 * t) * (1 + 0.2 * np.exp(-((t - KNEE_T) / 0.05) ** 2)) + 0.01
        Vt, Ft, tv, av = G.tube(pts, r, 6, cap_start=True, cap_end=True, squash=0.75)
        gl.add(Vt, Ft, at.uv(1, tv, av), cols(Vt, tv, k))
        # setae: short tufts along the top and front of the propodus and dactyl
        seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))]
        side = 1.0 if k % 2 == 0 else -1.0
        for q in np.linspace(0.5, 0.9, 6):
            p = np.array([np.interp(q * seg[-1], seg, pts[:, i]) for i in range(3)])
            rr = np.interp(q, t, r)
            for up_, out in ((1.0, 0.3), (0.4, 1.0)):
                n_ = np.array([0.0, side * out, up_]); n_ /= np.linalg.norm(n_)
                b0 = p + n_ * rr * 0.8
                Vc, Fc = G.cone(b0, b0 + n_ * 0.05 + np.array([0.012, 0, 0]), 0.008, 3)
                gl.add(Vc, Fc, at.uv(1, np.full(len(Vc), q), np.full(len(Vc), 0.5)), cols(Vc, q, k))
    # ---- chelipeds: heavy palms, spoon-shaped fingers ----
    gc = G.Geo()
    for i, side in enumerate((1.0, -1.0)):
        k = 8 + i
        pts = chela_line(side)
        L = float(np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1)))
        lines.append(pts)
        lens.append(L)
        t = np.linspace(0, 1, len(pts))
        r = 0.045 * (1 - 0.2 * t) + 0.04 * np.exp(-((t - 0.78) / 0.16) ** 2)      # the palm swells before the fingers
        Vt, Ft, tv, av = G.tube(pts, r, 8, cap_start=True, cap_end=False, squash=0.8)
        gc.add(Vt, Ft, at.uv(2, tv * 0.8, av), cols(Vt, tv * 0.8, k))
        d = pts[-1] - pts[-2]
        d /= np.linalg.norm(d)
        n = np.cross(d, [0, 0, 1.0]); n /= np.linalg.norm(n)
        up = np.cross(n, d)
        for f in (1, -1):
            b0 = pts[-1] + up * f * 0.012
            # a spoon: narrow at the base, broad and flattened toward the tip, the tips meeting
            path_ = [b0, b0 + d * 0.04 + up * f * 0.014, b0 + d * 0.08 + up * f * 0.008, b0 + d * 0.105 - up * f * 0.004]
            Vf, Ff, tf, af = G.tube(path_, [0.022, 0.028, 0.034, 0.012], 6, cap_end=True, squash=0.45, up=tuple(up))
            gc.add(Vf, Ff, at.uv(2, 0.8 + 0.2 * tf, af), cols(Vf, 0.8 + 0.2 * tf, k))
    pivots = [0.0] * 40
    for k, Cl in enumerate(lines):
        pivots[4 * k:4 * k + 4] = gltf_pt(Cl[0]) + [lens[k]]
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 8, "limbPivots": pivots})
    geo_obj("Claws", gc, root, "leg", [mat], extras={"claw": True, "limbPivots": pivots})

    # ---- eyes on short stalks in the orbits ----
    ge = G.Geo()
    eyes = []
    for side in (1.0, -1.0):
        b0 = np.array([FRONT - 0.09, side * 0.13, BODY_Z + 0.1])
        tip = b0 + np.array([0.04, side * 0.04, 0.03])
        Vt, Ft, tv, av = G.tube([b0, tip], [0.028, 0.024], 6, cap_end=False)
        ge.add(Vt, Ft, at.uv(3, tv * 0.45, av), cols(Vt, 0.0, 0))
        Ve, Fe = G.ellipsoid(tip + np.array([0.01, side * 0.01, 0.006]), (0.036, 0.032, 0.032), 10, 6)
        ge.add(Ve, Fe, at.uv(3, np.full(len(Ve), 0.75), np.full(len(Ve), 0.5)), cols(Ve, 0.0, 0))
        eyes.append(tip)
    geo_obj("Eyes", ge, root, "eye", [mat], extras={"pivot": gltf_pt(np.mean(eyes, 0))})

    C.set_extras(root, species="mithraculus_sculptus", lengthCm=3.0,
                 unit="carapace length (rostrum to back) 1.0, crab faces +X, walks forward and to the sides",
                 iridescence=0.03, roughness=0.4, gait="any", limbRig="crab_legs", grazes="bubble_algae", limbPivots=pivots)
    print_model(lines, lens)
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    return finish(root, objs, os.path.join(C.MODELS, "emerald_crab.glb"), 5200)


def print_model(lines, lens):
    """CrabModel.EMERALD (glTF space): coxae, rest foot tips, knees (t 0.5), propodi (t 0.75), leg lengths, the chelae
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
    for x in np.linspace(REAR + 0.08, FRONT - 0.1, 5):
        for f in (-0.6, 0.0, 0.6):
            body += g((x, f * half_width(x), BODY_Z + 0.06 - 0.06 * math.sqrt(max(0.0, 1 - f * f))))
    for x in np.linspace(REAR + 0.1, FRONT - 0.15, 3):
        for sd in (1, -1):
            body += g((x, sd * half_width(x), BODY_Z + 0.06))
    body += g((FRONT + 0.04, 0.0, BODY_Z + 0.09))
    body += g((0.0, 0.0, BODY_Z + 0.06 + dome(0.0, 0.0)))
    print("[emerald] CrabModel.EMERALD")
    for name, arr in (("hip", hip), ("tip", tip), ("knee", knee), ("mid", mid), ("legW", [round(x, 3) for x in lens[:8]]),
                      ("clawPivot", cp), ("clawTip", ct), ("clawLow", cl), ("body", body)):
        print(f"  {name} = doubleArrayOf({', '.join(str(x) for x in arr)}),")
    print(f"  body points {len(body) // 3}")


if __name__ == "__main__":
    build_emerald_crab()
