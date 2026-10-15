"""Crawler limb pose for previews and species cards (SIMULATION §19): a numpy mirror of the limb branch of
app/src/main/assets/shaders/fish.vert (starArm / crabLimb), so offline renders show the brittle star's arms and the
Sally Lightfoot's legs posed the way the app poses them. Inputs are the model's `extras.limbPivots` (glTF space) and
a limb pose in the core LimbRig layout (BrittleArms: 40 x vec4; CrabLegs / HermitLegs: 10 x vec4); the presets below
are typical frames.
"""
import math

import numpy as np

PI = math.pi


def _smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def _rot(v, a, ang):
    """Rodrigues rotation of rows of v about unit axis a (broadcast) by ang (per row)."""
    c, s = np.cos(ang)[:, None], np.sin(ang)[:, None]
    return v * c + np.cross(a, v) * s + a * np.sum(a * v, axis=1, keepdims=True) * (1 - c)


# ---------------------------------------------------------------- pose presets (core layouts)
STAR_N = 15       # steps per brittle star arm (core BrittleArms.SAMPLES)


def brittle_pack(psi, y, length=None):
    """BrittleArms.out from per-arm step directions psi (5 x 15, rad from the rest direction, + toward -Z), heights
    y (5 x 15, model units above the disc's ground plane) and arm lengths (5): 8 vec4 per arm, heights 1..8 in the x
    slots as 0.5 + 0.5 y, the plain slots n = 0.. holding psi 1..15, y 9..15, the length, 0."""
    psi, y = np.asarray(psi, float), np.asarray(y, float)
    length = np.ones(5) if length is None else np.asarray(length, float)
    out = np.zeros((5, 32))
    for k in range(5):
        plain = list(psi[k]) + list(y[k, 8:]) + [length[k], 0.0]
        for i in range(8):
            out[k, 4 * i] = 0.5 + 0.5 * np.clip(y[k, i], -0.9, 0.9)
        for n, v in enumerate(plain):
            out[k, 4 * (n // 3) + 1 + n % 3] = v
    return out.astype(np.float32).ravel()


def brittle_unpack(limbs):
    """Inverse of [brittle_pack]: (psi 5 x 15, y 5 x 15, length 5)."""
    L = np.asarray(limbs, float).reshape(5, 32)
    psi, y, length = np.zeros((5, STAR_N)), np.zeros((5, STAR_N)), np.zeros(5)
    for k in range(5):
        slot = lambda n: L[k, 4 * (n // 3) + 1 + n % 3]
        for j in range(STAR_N):
            psi[k, j] = slot(j)
        for j in range(8):
            y[k, j] = 2 * L[k, 4 * j] - 1
        for j in range(8, STAR_N):
            y[k, j] = slot(STAR_N + j - 8)
        length[k] = slot(22)
    return psi, y, length


def brittle_pose(kind="row"):
    """Typical BrittleArms frames: 'row' (arm 0 leading, its neighbours mid power stroke, the rear pair trailing; Astley
    2012), 'hide' (day: two tips out of the crevice trawling, the rest folded in S-curves), 'feed' (arm 0's tip
    looped round a food item), 'rest' (all out, lying in gentle S-curves)."""
    s = (np.arange(STAR_N) + 0.5) / STAR_N
    psi, y = np.zeros((5, STAR_N)), np.zeros((5, STAR_N))
    wave = lambda amp, ph: amp * s * np.sin(2 * PI * (1.3 * s - ph))
    for k in range(5):
        psi[k] = wave(0.12, 0.37 * k) + (0.25 if k % 2 == 0 else -0.25) * 2 * _smooth(0.3, 1.0, s)
    if kind == "row":
        psi[0] = wave(0.15, 0.1)                                  # lead arm probing ahead, tip a little raised
        y[0] = np.maximum(0, 0.08 * (s - 0.6))
        for k, sg in ((1, 1), (4, -1)):                           # rowing pair: swept back, bent like an S
            psi[k] = sg * (0.42 * (1 - 0.55 * s) - 0.5 * _smooth(0.4, 1.0, s)) + wave(0.1, 0.3)
        for k, sg in ((2, 1), (3, -1)):                           # rear pair: trailing, pushing
            psi[k] = sg * (0.14 * (1 - 0.55 * s) + 0.3 * s) + wave(0.08, 0.6)
    elif kind == "hide":
        for k in (2, 3, 4):                                       # drawn in: folded in S-curves under the rock
            psi[k] = (1 if k % 2 == 0 else -1) * 0.78 * 1.5 * np.sin(2 * PI * 1.6 * s)
        for k, sg in ((0, 1), (1, -1)):                           # tips out, sweeping
            psi[k] = sg * 0.3 * s + wave(0.22, 0.2 * k)
    elif kind == "feed":
        psi[0] = wave(0.06, 0.0) + 2.4 * _smooth(0.6, 1.0, s)        # tip looped round the item
        y[0] = 0.04 * _smooth(0.5, 0.9, s)
    return brittle_pack(psi, y)


def crab_pose(kind="walk"):
    """CrabLegs.out: leg k = (phase, signed extension, lift, tuck); claws 8/9 = (pick phase, stroke, reach, 0)."""
    L = np.zeros((10, 4))
    clock, side = 0.3, 1.0
    moving = 1.0 if kind == "walk" else 0.0
    for k in range(8):
        pair, right = k // 2, k % 2 == 1
        group = (pair + (1 if right else 0)) % 2
        ph = (clock + 0.5 * group + 0.06 * (3 - pair)) % 1.0
        leading = (right and side > 0) or ((not right) and side < 0)
        L[k] = (ph, (-1 if leading else 1) * 0.45 * moving, 0.38 * moving, 1.0 if kind == "tuck" else 0.0)
    stroke = 0.6 if kind in ("walk", "graze") else 0.0
    L[8] = (0.2, stroke, 0.25 + 0.65 * stroke, 0)
    L[9] = (0.7, stroke, 0.25 + 0.65 * stroke, 0)
    return L.astype(np.float32).ravel()


def hermit_pose(kind="walk"):
    """HermitLegs.out: legs (phase, stroke, lift, retract); chelae 4/5 (pick phase, pick, support, retract);
    6 shell (stride phase, roll, pitch, 0); 7 shield/eyes (scan phase, scan, bob, retract); 8 antennae, 9 antennules."""
    L = np.zeros((10, 4))
    clock = 0.15
    moving = 1.0 if kind == "walk" else 0.0
    r = 1.0 if kind == "retract" else 0.0
    sm = lambda a, b, x: float(_smooth(a, b, np.array(x)))
    for k in range(4):
        pair, right = k // 2, k % 2
        L[k] = ((clock + 0.5 * ((pair + right) % 2) + 0.08 * pair) % 1.0, 0.36 * moving, 0.9 * moving, sm(0, 0.6, r))
    pick = 1.0 if kind == "pick" else 0.0
    L[4] = (0.25, pick, moving, sm(0.2, 0.8, r))
    L[5] = (0.75, pick, moving, sm(0.1, 0.7, r))
    L[6] = (clock, 0.1 * moving, 0.0, 0.0)
    L[7] = (0.3, 1.0 - 0.6 * moving, 0.0, sm(0.45, 1.0, r))
    L[8] = (0.2, 0.5 + 0.5 * moving, 0.0, sm(0.45, 1.0, r))
    L[9] = (0.1, 0.0, 0.0, sm(0.45, 1.0, r))
    return L.astype(np.float32).ravel()


# ---------------------------------------------------------------- shader mirror (glTF model space)
def _star_nodes(psi, y, a0, seg):
    """Centreline nodes 0..STAR_N of one arm relative to its root (core BrittleArms.nodes)."""
    p = np.zeros((STAR_N + 1, 3))
    for j in range(1, STAR_N + 1):
        dy = y[j - 1] - (y[j - 2] if j > 1 else 0.0)
        hz = math.sqrt(max(seg * seg - dy * dy, 0.09 * seg * seg))
        a = a0 + psi[j - 1]
        p[j] = p[j - 1] + (math.cos(a) * hz, dy, -math.sin(a) * hz)
    return p


def pose_star(V, N, col, piv, limbs):
    """V, N: (n, 3) glTF positions / normals of arm vertices; col: (n, 4) COLOR_0. Returns posed V, N."""
    piv = np.asarray(piv, float).reshape(10, 4)
    psiA, yA, lenA = brittle_unpack(limbs)
    V, N = V.copy(), N.copy()
    kk = np.floor(col[:, 2] * 9 + 0.5).astype(int)
    for k in range(5):
        m = kk == k
        if not m.any():
            continue
        P = piv[k]
        a0 = math.atan2(-P[2], P[0])
        d0 = np.array([math.cos(a0), 0, -math.sin(a0)])
        l0 = np.array([-math.sin(a0), 0, -math.cos(a0)])
        off = V[m] - P[:3]
        along, lat, up = off @ d0, off @ l0, off[:, 1]
        lenK = float(np.clip(lenA[k], 0.1, 1.0))
        s = np.clip(col[m, 0], 0, 1)
        keep = (s < lenK + 0.001).astype(float)
        s = np.minimum(s, lenK)
        ax = along - s * P[3]
        nodes = _star_nodes(psiA[k], yA[k], a0, P[3] / STAR_N)
        ext = np.vstack([2 * nodes[0] - nodes[1], nodes, 2 * nodes[-1] - nodes[-2]])   # mirrored end nodes
        sN = s * STAR_N
        j = np.minimum(np.floor(sN), STAR_N - 1).astype(int)
        t = (sN - j)[:, None]
        p0, p1, p2, p3 = ext[j], ext[j + 1], ext[j + 2], ext[j + 3]
        c = P[:3] + 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t ** 2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3)
        T = 0.5 * ((-p0 + p2) + 2 * (2 * p0 - 5 * p1 + 4 * p2 - p3) * t + 3 * (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 2) + d0 * 1e-4
        T /= np.linalg.norm(T, axis=1, keepdims=True)
        Lv = np.stack([T[:, 2], 0 * T[:, 0], -T[:, 0]], -1) + l0 * 1e-4
        Lv /= np.linalg.norm(Lv, axis=1, keepdims=True)
        U = np.cross(T, Lv)
        V[m] = c + (T * ax[:, None] + Lv * lat[:, None] + U * up[:, None]) * keep[:, None]
        n = N[m]
        N[m] = T * (n @ d0)[:, None] + Lv * (n @ l0)[:, None] + U * n[:, 1:2]
    return V, N


def pose_crab(V, N, col, piv, limbs):
    piv = np.asarray(piv, float).reshape(10, 4)
    Lm = np.asarray(limbs, float).reshape(10, 4)
    V, N = V.copy(), N.copy()
    kk = np.floor(col[:, 2] * 9 + 0.5).astype(int)
    for k in range(10):
        m = kk == k
        if not m.any():
            continue
        P, A = piv[k], Lm[k]
        off = V[m] - P[:3]
        n = N[m]
        if k < 8:
            d = np.array([P[0] + 1e-4, 0, P[2]])
            d /= np.linalg.norm(d)
            ph = A[0]
            swing = ph < 0.5
            q, p = ph * 2, (ph - 0.5) * 2
            e = -math.cos(PI * q) if swing else 1 - 2 * p
            t = np.clip(col[m, 0], 0, 1)
            radial = off @ d
            off = off + d * (radial * (0.2 * A[1] * e * t - 0.3 * A[3] * t))[:, None]
            ax = np.array([-d[2], 0, d[0]])
            lift = np.full(len(t), A[2] * (1.0 if swing else 0.0) * math.sin(PI * q) - 0.12 * A[3])
            off, n = _rot(off, ax, lift), _rot(n, ax, lift)
            yaw = np.full(len(t), 0.08 * A[1] * math.sin(2 * PI * ph))
            yax = np.array([0, 1.0, 0])
            off, n = _rot(off, yax, yaw), _rot(n, yax, yaw)
        else:
            w = 0.5 - 0.5 * math.cos(2 * PI * A[0])
            down = np.full(len(off), A[1] * 0.5 * w + 0.15 * (A[2] - 0.5))
            off[:, 0] += 0.12 * (A[2] - 0.5) - 0.07 * A[1] * (1 - w)
            zax = np.array([0, 0, 1.0])
            off, n = _rot(off, zax, -down), _rot(n, zax, -down)
        V[m] = P[:3] + off
        N[m] = n
    return V, N


def pose_hermit(V, N, col, piv, limbs):
    """Mirror of fish.vert hermitLimb (motion mode 5)."""
    piv = np.asarray(piv, float).reshape(10, 4)
    Lm = np.asarray(limbs, float).reshape(10, 4)
    V, N = V.copy(), N.copy()
    kk = np.floor(col[:, 2] * 9 + 0.5).astype(int)
    yax, zax, xax = np.array([0, 1.0, 0]), np.array([0, 0, 1.0]), np.array([1.0, 0, 0])
    for k in range(10):
        m = kk == k
        if not m.any():
            continue
        P, A = piv[k], Lm[k]
        off = V[m] - P[:3]
        n = N[m]
        t = np.clip(col[m, 0], 0, 1)
        side = np.where(V[m][:, 2] >= 0, 1.0, -1.0)
        if k < 4:
            ph = A[0]
            sw = ph < 0.4
            q, p = ph / 0.4, (ph - 0.4) / 0.6
            e = -math.cos(PI * q) if sw else 1 - 2 * p
            sgn = 1.0 if P[2] >= 0 else -1.0
            yaw = np.full(len(t), A[1] * e * sgn)
            off, n = _rot(off, yax, yaw), _rot(n, yax, yaw)
            lift = A[2] * (1.0 if sw else 0.0) * math.sin(PI * q) * _smooth(0.25, 1.0, t)
            off[:, 1] += lift * 0.22 * P[3]
        elif k < 6:
            w = 0.5 - 0.5 * math.cos(2 * PI * A[0])
            sd = -1.0 if k == 4 else 1.0
            down = np.full(len(t), A[1] * (0.55 * w - 0.15) + 0.1 * A[2])
            off, n = _rot(off, zax, -down), _rot(n, zax, -down)
            off[:, 0] -= 0.25 * P[3] * A[1] * (1 - w) * t
            off[:, 2] -= sd * 0.1 * P[3] * A[1] * (1 - w) * t
        elif k == 6:
            roll = np.full(len(t), A[1] * math.sin(2 * PI * A[0]))
            pitch = np.full(len(t), A[2])
            off, n = _rot(off, xax, roll), _rot(n, xax, roll)
            off, n = _rot(off, zax, pitch), _rot(n, zax, pitch)
        elif k == 7:
            off[:, 1] += A[2] * 0.02 * P[3]
            eye = col[m, 1] > 0.5
            a = A[1] * 0.3 * np.sin(2 * PI * A[0] + side * 1.7)
            off[:, 2] += np.where(eye, t * a * P[3], 0)
            off[:, 0] -= np.where(eye, t * np.abs(a) * 0.3 * P[3], 0)
        elif k == 8:
            ph = 2 * PI * A[0] + side * 1.3
            h2 = t * t
            off[:, 1] += h2 * A[1] * 0.18 * P[3] * np.sin(ph - t * 3)
            off[:, 2] += h2 * A[1] * 0.14 * P[3] * np.cos(0.7 * ph - t * 2.5) * side
            off[:, 0] += h2 * A[1] * 0.08 * P[3] * np.sin(0.5 * ph)
        else:
            f = np.maximum(np.sin(2 * PI * A[0] + side * 0.9), 0) ** 8
            off[:, 1] -= t * t * A[1] * f * 0.3 * P[3]
        v = P[:3] + off
        if k != 6:
            c = piv[7, :3]
            s = 0.6 if k == 4 else 0.45 if k == 5 else 0.25 if k < 4 else 0.2
            v = v + ((c + (v - c) * s) - v) * A[3]
        V[m] = v
        N[m] = n
    return V, N


# ---------------------------------------------------------------- Blender glue
def pose_imported(objs, pose=None):
    """Poses the limb meshes of an imported crawler GLB in place (call before the COLOR_0 white-out).
    pose: preset name ('row', 'hide', 'feed', 'rest' for the star; 'walk', 'graze', 'tuck', 'rest' for the crab)."""
    meshes = [o for o in objs if o.type == 'MESH' and "limbPivots" in o.keys()]
    if not meshes:
        return False
    rig = None
    for o in objs:
        if "limbRig" in o.keys():
            rig = str(o["limbRig"])
    if rig is None:
        rig = "crab_legs" if any("claw" in o.keys() for o in objs) else "brittle_arms"
    star = rig == "brittle_arms"
    hermit = rig == "hermit_legs"
    limbs = brittle_pose(pose or "row") if star else hermit_pose(pose or "walk") if hermit else crab_pose(pose or "walk")
    fn = pose_star if star else pose_hermit if hermit else pose_crab
    for o in meshes:
        me = o.data
        piv = list(o["limbPivots"])
        nv = len(me.vertices)
        Vb = np.zeros(nv * 3)
        me.vertices.foreach_get("co", Vb)
        Vb = Vb.reshape(-1, 3)
        if len(me.color_attributes) == 0:
            continue
        ca = me.color_attributes[0]
        raw = np.zeros(len(ca.data) * 4, np.float32)
        ca.data.foreach_get("color", raw)
        raw = raw.reshape(-1, 4)
        if ca.domain == 'CORNER':
            vi = np.zeros(len(me.loops), np.int32)
            me.loops.foreach_get("vertex_index", vi)
            col = np.zeros((nv, 4), np.float32)
            col[vi] = raw
        else:
            col = raw
        if ca.data_type == 'BYTE_COLOR' or ca.data_type == 'FLOAT_COLOR':
            pass   # colors are linear floats in Blender either way; COLOR_0 values are data (0..1)
        Vg = np.stack([Vb[:, 0], Vb[:, 2], -Vb[:, 1]], -1)
        Ng = np.tile([0, 1.0, 0], (nv, 1))
        Vg, _ = fn(Vg, Ng, col, piv, limbs)
        Vb2 = np.stack([Vg[:, 0], -Vg[:, 2], Vg[:, 1]], -1)
        me.vertices.foreach_set("co", Vb2.ravel())
        if getattr(me, "has_custom_normals", False):
            try:
                me.normals_split_custom_set_from_vertices([(0.0, 0.0, 0.0)] * nv)
            except Exception:
                pass
        me.update()
    return True
