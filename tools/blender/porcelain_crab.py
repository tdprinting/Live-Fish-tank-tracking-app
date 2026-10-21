"""Anemone porcelain crab, Neopetrolisthes maculatus (Porcellanidae; research 45 §2): a small, flat crab built for the
limb-rigged crawler path (fish.vert crabLimb, core CrabLegs / CrawlerSeat), like the emerald crab.

  * carapace: nearly round and flat, smooth and glossy, white to cream peppered with small maroon-red spots;
  * three pairs of walking legs; the reduced fourth pair (pereiopod 5) is a short stub tucked under the back of the
    carapace (a porcelain crab shows only six walking legs; the rig still plants the stub, out of sight);
  * two huge, flattened chelipeds held forward in a V, the palms broad and plate-like, spotted like the carapace;
  * long, thin antennae swept back along the sides; small eyes on short stalks;
  * the feather fans: the third maxillipeds, a short stalk and a semicircular comb of long setae each, swept down
    through the water and back to the mouth, left and right in turn (COLOR_0.g = 1 marks them; fish.vert poses them
    from the chela entries' w = fanning 0..1 and x = sweep phase).

Unit: carapace length 1.0, facing +X, Blender Z up, standing on z = 0.
COLOR_0: R = t along the limb (0 coxa .. 1 tip), G = 1 on the fans, B = limb index / 9, A = 0.5 - x.
Limbs k = 2 * pair + side (even = left = Blender +Y = glTF -Z); 8 left / 9 right chela (the fans share 8 / 9).
extras.limbPivots = 10 x vec4 (glTF space): coxa xyz, w = limb length along its centreline.
Prints CrabModel.PORCELAIN for core/sim/CrawlerSeat.kt.

    /tmp/claude-0/bpyenv/bin/python tools/blender/porcelain_crab.py
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
BODY_Z = 0.12            # a flat crab, low over the ground
DOME = 0.07
# walking legs: coxa x on the carapace side, yaw from +X (deg), total length (carapace lengths). The fourth pair is
# the reduced fifth pereiopod: tiny, tucked under the back of the carapace.
LEGS = ((0.12, 60.0, 0.8), (-0.08, 92.0, 0.85), (-0.27, 122.0, 0.78), (-0.38, 165.0, 0.28))
KNEE_T = 0.38
KNEE_RISE = 0.22
CREAM, SPOT = "#EFE6D6", "#8A1A22"


def half_width(x):
    """Nearly round: a little broader behind the middle, a straight short front between the eyes."""
    t = (x - REAR) / (FRONT - REAR)
    r = math.sqrt(max(0.0, 1 - ((t - 0.45) / 0.56) ** 2))
    return 0.5 * r * (1 - 0.25 * S(0.8, 1.0, t))


def dome(x, y):
    w = max(half_width(x), 1e-3)
    r = min(1.0, abs(y) / w)
    t = (x - REAR) / (FRONT - REAR)
    return DOME * (1 - r * r) ** 0.7 * (0.7 + 0.3 * math.sin(math.pi * min(1.0, 0.1 + 0.9 * t)))


def spots(cv, seed, n, r, box=(0.0, 1.0, 0.0, 1.0), color=SPOT, aspect=2.5):
    rs = np.random.default_rng(seed)
    for (cu, cvv) in P.poisson(rs, n, 2.3 * r, box):
        rr = r * rs.uniform(0.6, 1.3)
        cv.blend(P.ellipse(cv, cu, cvv, rr * aspect, rr, 0.25), color, 0.9)


def paint(at):
    def shell(cv):   # u along the body (0 rear .. 1 front), v round (0 belly .. 1 dorsal midline)
        cv.vgrad("#D9CDBA", CREAM)
        spots(cv, 3, 90, 0.03, (0.0, 1.0, 0.35, 1.0), aspect=0.7)
        cv.blend(1 - S(0.0, 0.3, cv.v), "#E8DFD0", 0.8)       # plain pale underside
    def leg(cv):     # u along the leg
        cv.vgrad("#E6DCCA", CREAM)
        spots(cv, 5, 40, 0.07, (0.05, 0.85, 0.0, 1.0), aspect=0.2)
        for j in (0.12, KNEE_T, 0.66, 0.86):
            cv.blend(1 - S(0.0, 0.015, np.abs(cv.u - j)), "#B9A88E", 0.6)
        cv.blend(S(0.9, 1.0, cv.u), "#9A5A2A", 0.8)          # amber dactyl tips
    def claw(cv):
        cv.vgrad("#E2D6C2", CREAM)
        spots(cv, 7, 70, 0.045, aspect=0.45)
        cv.blend(S(0.88, 1.0, cv.u), "#C9A07A", 0.6)          # paler worn finger tips
    def fan(cv):     # u along the fan (stalk .. setae tips)
        cv.vgrad("#C8B8D8", "#F3EEF8")
        cv.blend(S(0.0, 0.3, 1 - cv.u), "#9C7AB2", 0.5)       # a mauve stalk, pale feathery setae
        cv.blend(P.band(cv.u, 0.3, 0.32, 0.02), "#6E4A86", 0.4)
    at.paint_strip(0, shell)
    at.paint_strip(1, leg)
    at.paint_strip(2, claw)
    at.paint_strip(3, fan)


def cols(V, t, k, g=0.0):
    n = len(V)
    return np.stack([np.clip(np.broadcast_to(np.asarray(t, float), n), 0, 1), np.full(n, g),
                     np.broadcast_to(np.asarray(k, float) / 9.0, n), np.clip(0.5 - V[:, 0], 0, 1)], -1)


def leg_line(k):
    pair, side = k // 2, (1.0 if k % 2 == 0 else -1.0)
    x0, yaw, L = LEGS[pair]
    w = half_width(x0)
    a = math.radians(yaw)
    d = np.array([math.cos(a), side * math.sin(a), 0.0])
    hip = np.array([x0, side * w * 0.92, BODY_Z + 0.03])
    knee = hip + d * (0.36 * L) + np.array([0, 0, KNEE_RISE * L])
    foot = np.array([hip[0], hip[1], 0.0]) + d * (0.78 * L)
    ankle = knee + (foot - knee) * 0.55 + np.array([0, 0, 0.04 * L]) + d * 0.05 * L
    pts = np.vstack([G.bezier(hip, hip + (knee - hip) * 0.4, knee - d * 0.05 * L, knee, 8)[:-1],
                     G.bezier(knee, knee + d * 0.12 * L, ankle, foot, 12)])
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))]
    return pts, seg[-1]


def chela_line(side):
    """Merus out to the side, carpus forward, the big flat palm forward and in: the claws held in a V in front."""
    base = np.array([FRONT - 0.2, side * 0.3, BODY_Z + 0.04])
    elbow = base + np.array([0.18, side * 0.22, 0.03])
    wrist = elbow + np.array([0.3, side * 0.06, 0.0])
    tip = wrist + np.array([0.42, -side * 0.14, -0.04])
    return G.bezier(base, elbow, wrist, tip, 18)


def fan_root(side, pivots_k):
    """The fan's root (glTF): fish.vert takes it from the chela pivot as (P.x, P.y, 0.35 P.z)."""
    px, py, pz = pivots_k[:3]
    g = np.array([px, py, 0.35 * pz])
    return np.array([g[0], -g[2], g[1]])       # back to Blender (x, y, z)


def build_porcelain_crab():
    C.reset_scene()
    at = Atlas(512, 512, 4)
    paint(at)
    path = os.path.join(C.BUILD, "porcelain_crab.png")
    at.cv.save(path)
    img = C.load_image(path, name="porcelain_crab")
    mat = C.material("PorcelainCrabMat", image=img, rough=0.3)     # glossy, "porcelain"
    mfan = C.material("PorcelainFanMat", image=img, rough=0.6, double=True)
    root = make_root("Root", {})

    # ---- carapace: lofted rings, a low smooth dome ----
    gb = G.Geo()
    xs = np.linspace(REAR, FRONT, 22)
    sides = 22
    rings = []
    for x in xs:
        w = half_width(x)
        ring = []
        for j in range(sides):
            a = 2 * math.pi * j / sides
            s_, c_ = math.sin(a), math.cos(a)
            y = w * c_
            z = BODY_Z + 0.04 + (s_ ** 0.35 * dome(x, y) if s_ > 0 else 0.045 * s_)
            ring.append((x, y, z))
        rings.append(ring)
    V = np.array(rings).reshape(-1, 3)
    F = []
    for i in range(len(xs) - 1):
        for j in range(sides):
            a = i * sides + j
            b = i * sides + (j + 1) % sides
            F.append((a, b, b + sides, a + sides))
    c0 = len(V); V = np.vstack([V, [[REAR - 0.004, 0, BODY_Z + 0.05]]])
    F += [((j + 1) % sides, j, c0) for j in range(sides)]
    c1 = len(V); V = np.vstack([V, [[FRONT + 0.004, 0, BODY_Z + 0.06]]])
    base = (len(xs) - 1) * sides
    F += [(base + j, base + (j + 1) % sides, c1) for j in range(sides)]
    u = np.clip((V[:, 0] - REAR) / (FRONT - REAR + 0.05), 0, 1)
    ang = np.arctan2(V[:, 2] - (BODY_Z + 0.04), np.abs(V[:, 1]) + 1e-6)
    vv = np.clip(0.5 + ang / math.pi, 0, 1)
    gb.add(V, F, at.uv(0, u, vv), cols(V, 0.0, 0))
    # the long thin antennae, swept back along the sides at body height (clear of the rock ahead)
    for sd in (1, -1):
        a0 = np.array([FRONT - 0.03, sd * 0.1, BODY_Z + 0.08])
        pts = G.bezier(a0, a0 + np.array([0.12, sd * 0.2, 0.03]), a0 + np.array([-0.1, sd * 0.62, 0.05]), a0 + np.array([-0.7, sd * 0.78, 0.02]), 10)
        Vt, Ft, tv, av = G.tube(pts, np.linspace(0.014, 0.004, len(pts)), 3, cap_end=True)
        gb.add(Vt, Ft, at.uv(1, 0.2 + 0.6 * tv, av), cols(Vt, 0.0, 0))
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
        r0 = 0.05 if k < 6 else 0.02
        r = r0 * (1 - 0.5 * t) * (1 + 0.15 * np.exp(-((t - KNEE_T) / 0.05) ** 2)) + 0.008
        Vt, Ft, tv, av = G.tube(pts, r, 6, cap_start=True, cap_end=True, squash=0.7)
        gl.add(Vt, Ft, at.uv(1, tv, av), cols(Vt, tv, k))
    # ---- chelipeds: thin arm, then the broad, flat, plate-like palm and short fingers ----
    gc = G.Geo()
    for i, side in enumerate((1.0, -1.0)):
        k = 8 + i
        pts = chela_line(side)
        L = float(np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1)))
        lines.append(pts)
        lens.append(L)
        t = np.linspace(0, 1, len(pts))
        r = 0.04 + 0.13 * S(0.4, 0.62, t) * (1 - 0.35 * S(0.8, 1.0, t))
        Vt, Ft, tv, av = G.tube(pts, r, 8, cap_start=True, cap_end=False, squash=0.28)
        gc.add(Vt, Ft, at.uv(2, tv * 0.85, av), cols(Vt, tv * 0.85, k))
        d = pts[-1] - pts[-2]
        d /= np.linalg.norm(d)
        n = np.cross(d, [0, 0, 1.0]); n /= np.linalg.norm(n)
        for f, w in ((1, 0.06), (-1, 0.07)):
            b0 = pts[-1] + n * f * 0.06
            path_ = [b0, b0 + d * 0.08 - n * f * 0.01, b0 + d * 0.15 - n * f * 0.035]
            Vf, Ff, tf, af = G.tube(path_, [w, w * 0.8, 0.012], 6, cap_end=True, squash=0.35)
            gc.add(Vf, Ff, at.uv(2, 0.85 + 0.15 * tf, af), cols(Vf, 0.85 + 0.15 * tf, k))
    pivots = [0.0] * 40
    for k, Cl in enumerate(lines):
        pivots[4 * k:4 * k + 4] = gltf_pt(Cl[0]) + [lens[k]]

    # ---- the feather fans (third maxillipeds), drawn open at the top of their sweep: a short stalk up and forward
    # from the mouth, then a semicircle of long setae ----
    gf = G.Geo()
    for i, side in enumerate((1.0, -1.0)):
        k = 8 + i
        r0 = fan_root(side, pivots[4 * k:4 * k + 4])
        up = np.array([0.55, side * 0.15, 0.82]); up /= np.linalg.norm(up)
        stalk_tip = r0 + up * 0.2
        Vt, Ft, tv, av = G.tube([r0, r0 + up * 0.1, stalk_tip], [0.022, 0.018, 0.014], 4, cap_end=False)
        gf.add(Vt, Ft, at.uv(3, 0.3 * tv, av), cols(Vt, 0.3 * tv, k, 1.0))
        lat = np.cross(up, [0, 0, 1.0]); lat /= np.linalg.norm(lat)
        fwd = np.cross(lat, up)
        for j in range(9):
            a = math.radians(-80 + 160 * j / 8)
            dirn = up * math.cos(a) + lat * math.sin(a) * 0.9 + fwd * 0.25
            dirn /= np.linalg.norm(dirn)
            Ls = 0.3 * (0.75 + 0.25 * math.cos(a))
            tip = stalk_tip + dirn * Ls + np.array([0, 0, -0.04 * abs(math.sin(a))])
            Vs, Fs, ts, as_ = G.tube([stalk_tip, stalk_tip + dirn * Ls * 0.5, tip], [0.007, 0.005, 0.002], 3, cap_end=True)
            tt = 0.3 + 0.7 * ts
            gf.add(Vs, Fs, at.uv(3, tt, as_), cols(Vs, tt, k, 1.0))
    geo_obj("Legs", gl, root, "leg", [mat], extras={"count": 8, "limbPivots": pivots})
    geo_obj("Claws", gc, root, "leg", [mat], extras={"claw": True, "limbPivots": pivots})
    geo_obj("Fans", gf, root, "leg", [mfan], extras={"fans": True, "limbPivots": pivots})

    # ---- eyes on short stalks ----
    ge = G.Geo()
    eyes = []
    for side in (1.0, -1.0):
        b0 = np.array([FRONT - 0.07, side * 0.12, BODY_Z + 0.07])
        tip = b0 + np.array([0.05, side * 0.05, 0.03])
        Vt, Ft, tv, av = G.tube([b0, tip], [0.024, 0.02], 6, cap_end=False)
        ge.add(Vt, Ft, at.uv(0, 0.95 + 0.0 * tv, av), cols(Vt, 0.0, 0))
        Ve, Fe = G.ellipsoid(tip + np.array([0.012, side * 0.01, 0.004]), (0.03, 0.028, 0.028), 10, 6)
        ge.add(Ve, Fe, at.uv(1, np.full(len(Ve), 0.97), np.full(len(Ve), 0.5)), cols(Ve, 0.0, 0))
        eyes.append(tip)
    geo_obj("Eyes", ge, root, "eye", [mat], extras={"pivot": gltf_pt(np.mean(eyes, 0))})

    C.set_extras(root, species="neopetrolisthes_maculatus", lengthCm=1.8,
                 unit="carapace length 1.0, crab faces +X, walks forward and to the sides",
                 iridescence=0.0, roughness=0.3, gait="any", limbRig="crab_legs", fans=True, limbPivots=pivots)
    print_model(lines, lens)
    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    return finish(root, objs, os.path.join(C.MODELS, "porcelain_crab.glb"), 5200)


def print_model(lines, lens):
    """CrabModel.PORCELAIN (glTF space), the same layout as the emerald crab's."""
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
            body += g((x, f * half_width(x), BODY_Z + 0.04 - 0.045 * math.sqrt(max(0.0, 1 - f * f))))
    for x in np.linspace(REAR + 0.1, FRONT - 0.15, 3):
        for sd in (1, -1):
            body += g((x, sd * half_width(x), BODY_Z + 0.04))
    body += g((FRONT + 0.03, 0.0, BODY_Z + 0.06))
    body += g((0.0, 0.0, BODY_Z + 0.04 + dome(0.0, 0.0)))
    print("[porcelain] CrabModel.PORCELAIN")
    for name, arr in (("hip", hip), ("tip", tip), ("knee", knee), ("mid", mid), ("legW", [round(x, 3) for x in lens[:8]]),
                      ("clawPivot", cp), ("clawTip", ct), ("clawLow", cl), ("body", body)):
        print(f"  {name} = doubleArrayOf({', '.join(str(x) for x in arr)}),")
    print(f"  body points {len(body) // 3}")


if __name__ == "__main__":
    build_porcelain_crab()
