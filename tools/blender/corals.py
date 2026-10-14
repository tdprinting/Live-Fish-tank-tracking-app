"""Corals, macroalgae and polyp instance tables (ASSETS.md §4). All GLBs are in WORLD coordinates (cm).

Per GLB: Root (extras: anchor, species) -> Body nodes (part "body") and polyp prototype nodes (part "polyp").
A polyp node's mesh is ONE polyp in its local frame (cm, base at the origin, axis +Y) and carries
  extras.instances      flat float list, stride 10 per instance:
                        pos.xyz (world cm, Y up) | quat.xyzw (glTF frame, rotates local +Y to the surface normal)
                        | scale | phase (0..1, sway/pulse offset) | variant (0..1, tint jitter)
  extras.instanceStride 10
The runtime draws the prototype once per row (instanced VBO). Node transform is identity.

COLOR_0 for coral meshes (data, linear):
  body : r = thickness proxy (1 thin/translucent .. 0 thick), g = sway group phase (0..1),
         b = sway weight (0 at the attachment .. 1 at the free tips), a = 1
  polyp: r = 0, g = 0, b = tentacle hinge (0 at the polyp base .. 1 at the tentacle tips), a = 1
  card : b = sway weight (0 root .. 1 tip)
"""
import json
import math
import os

import numpy as np

import common as C
import paintlib as P
import tank as T
from common import bpy, hex2rgb

S = P.smoothstep
UP = np.array([0.0, 1.0, 0.0])
RS = np.random.default_rng


def unit(v):
    v = np.asarray(v, float)
    return v / (np.linalg.norm(v, axis=-1, keepdims=True) + 1e-12)


# ----------------------------------------------------------------------------
# rock queries (BVH of rockscape.glb, world coordinates)
# ----------------------------------------------------------------------------
class Rock:
    def __init__(self):
        from mathutils.bvhtree import BVHTree
        before = set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=os.path.join(C.MODELS, "rockscape.glb"))
        new = [o for o in bpy.data.objects if o not in before]
        verts, polys = [], []
        for o in new:
            if o.type != 'MESH':
                continue
            mw = o.matrix_world
            b = len(verts)
            verts += [tuple(mw @ v.co) for v in o.data.vertices]
            polys += [tuple(b + i for i in p.vertices) for p in o.data.polygons]
        self.bvh = BVHTree.FromPolygons(verts, polys)
        for o in new:
            bpy.data.objects.remove(o, do_unlink=True)

    def ray(self, p, d, dist):
        from mathutils import Vector
        loc, nrm, idx, t = self.bvh.ray_cast(Vector(C.w2b(p)), Vector(C.w2b(d)), dist)
        if loc is None:
            return None, None
        return np.array(C.b2w(loc)), np.array(C.b2w(nrm))

    def nearest(self, p):
        from mathutils import Vector
        loc, nrm, idx, t = self.bvh.find_nearest(Vector(C.w2b(p)))
        return np.array(C.b2w(loc)), np.array(C.b2w(nrm)), t

    def drape(self, anchor, n, R, a, b):
        """Radial projection of tangent-plane offsets (a, b) onto the rock around a centre of curvature
        anchor - n*R. Returns hit points and normals (None where the ray misses)."""
        n = unit(n)
        t1 = unit(np.cross(UP if abs(n[1]) < 0.95 else np.array([1.0, 0, 0]), n))
        t2 = np.cross(n, t1)
        c = np.asarray(anchor, float) - n * R
        d = unit(n * R + np.outer(a, t1) + np.outer(b, t2))
        P_, N_ = [], []
        for di in d:
            hit, nrm = self.ray(c + di * (R + 20), -di, R + 26)
            if hit is not None and np.dot(nrm, di) < 0:
                nrm = -nrm  # back-face hit: flip
            P_.append(hit)
            N_.append(nrm)
        return P_, N_, t1, t2


def anchors():
    import json
    import rockscape as R
    snapped = {a["name"]: a for a in json.load(open(os.path.join(C.SIM, "anchors.json")))["anchors"]}
    out = {}
    for name, typ, pos, extra, snap in R.ANCHORS:
        if not name.startswith("coral_") and typ != "GRAZE":
            continue
        s = snapped.get(name, {})
        out[name] = (np.array(s.get("pos", pos), float), unit(extra.get("normal", s.get("normal", [0, 1, 0]))))
    return out


# ----------------------------------------------------------------------------
# quaternions (x, y, z, w) and instance rows
# ----------------------------------------------------------------------------
def q_mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return np.array([aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
                     aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz])


def q_axis(axis, ang):
    axis = unit(axis)
    s = math.sin(ang / 2)
    return np.array([axis[0] * s, axis[1] * s, axis[2] * s, math.cos(ang / 2)])


def q_from_up(n, twist=0.0):
    n = unit(n)
    c = float(np.clip(np.dot(UP, n), -1, 1))
    ax = np.cross(UP, n)
    if np.linalg.norm(ax) < 1e-6:
        q = np.array([0, 0, 0, 1.0]) if c > 0 else q_axis([1, 0, 0], math.pi)
    else:
        q = q_axis(ax, math.acos(c))
    return q_mul(q, q_axis(UP, twist))


def q_rotate(q, v):
    x, y, z, w = q
    u = np.array([x, y, z])
    return 2 * np.dot(u, v) * u + (w * w - np.dot(u, u)) * np.asarray(v) + 2 * w * np.cross(u, v)


class Instances:
    def __init__(self, seed):
        self.rows = []
        self.rs = RS(seed)

    def add(self, pos, n, scale, phase=None, variant=None, twist=None):
        twist = self.rs.uniform(0, 2 * math.pi) if twist is None else twist
        phase = self.rs.uniform() if phase is None else phase
        variant = self.rs.uniform() if variant is None else variant
        q = q_from_up(n, twist)
        self.rows.append([*np.round(pos, 3), *np.round(q, 5), round(float(scale), 3), round(float(phase), 3),
                          round(float(variant), 3)])

    def flat(self):
        return [float(x) for r in self.rows for x in r]

    def __len__(self):
        return len(self.rows)


def polyp_node(name, root, proto, mats, inst, extras=None):
    V, F, UV, COL = proto
    g = T.WGeo()
    g.add(V, F, UV, COL)
    ex = {"instances": inst.flat(), "instanceStride": 10, "instanceCount": len(inst)}
    ex.update(extras or {})
    return g.obj(name, root, "polyp", mats, smooth=True, extras=ex)


def tex(name, cv, alpha=False):
    path = os.path.join(C.BUILD, name + ".png")
    cv.save(path, alpha=alpha)
    return C.load_image(path, name=name)


# ----------------------------------------------------------------------------
# polyp prototypes: (V, F, UV, COL) in local cm, base at origin, axis +Y
# ----------------------------------------------------------------------------
def proto_star(r=0.16, h=0.12, n=8):
    """Kenya tuft: 8-point star lifted h above the branch surface (8 tris)."""
    V = [(0, h + 0.02, 0)]
    UV = [(0.5, 0.5)]
    COL = [(0, 0, 0.3, 1)]
    for k in range(2 * n):
        a = math.pi * k / n
        rr = r if k % 2 == 0 else r * 0.3
        V.append((rr * math.cos(a), h + (0.03 if k % 2 == 0 else 0.01), rr * math.sin(a)))
        UV.append((0.5 + 0.5 * rr / r * math.cos(a), 0.5 + 0.5 * rr / r * math.sin(a)))
        COL.append((0, 0, 1.0 if k % 2 == 0 else 0.5, 1))
    F = [(0, 1 + (k + 1) % (2 * n), 1 + k) for k in range(2 * n)]
    # 16 tris for the star -> keep 8 big points only by merging: use alternating fan (8 tris)
    F = [(0, 1 + (2 * k + 2) % (2 * n), 1 + 2 * k) for k in range(n)]
    return np.array(V), F, np.array(UV), np.array(COL, float)


def proto_feather(n=8, stalk_h=0.15, stalk_r=0.05, tent=0.4, splay=55, w=0.035, sides=3, cup=0.0):
    """GSP-type polyp: n single-triangle tentacles on a stalk (sides >= 3: open frustum, sides*2 tris;
    sides == 1: a single upright quad, 2 tris)."""
    V, F, UV, COL = [], [], [], []
    if sides == 1:
        V += [(-stalk_r, 0, 0), (stalk_r, 0, 0), (stalk_r * 0.8, stalk_h, 0), (-stalk_r * 0.8, stalk_h, 0)]
        UV += [(0.1, 0.05), (0.1, 0.05), (0.1, 0.3), (0.1, 0.3)]
        COL += [(0, 0, 0, 1), (0, 0, 0, 1), (0, 0, 0.15, 1), (0, 0, 0.15, 1)]
        F.append((0, 1, 2, 3))
        sides = 0
    for k in range(sides):
        a = 2 * math.pi * k / sides
        V.append((stalk_r * math.cos(a), 0, stalk_r * math.sin(a)))
        UV.append((0.1, 0.05))
        COL.append((0, 0, 0, 1))
    for k in range(sides):
        a = 2 * math.pi * k / sides
        V.append((stalk_r * 0.8 * math.cos(a), stalk_h, stalk_r * 0.8 * math.sin(a)))
        UV.append((0.1, 0.3))
        COL.append((0, 0, 0.15, 1))
    for k in range(sides):
        F.append((k, sides + k, sides + (k + 1) % sides, (k + 1) % sides))
    sp = math.radians(splay)
    for k in range(n):
        a = 2 * math.pi * (k + 0.5) / n
        d = np.array([math.cos(a), 0, math.sin(a)])
        tng = np.array([-math.sin(a), 0, math.cos(a)])
        base = np.array([0, stalk_h, 0]) + d * stalk_r * 0.6
        tip = base + (d * math.sin(sp) + UP * math.cos(sp)) * tent - UP * cup * tent
        b = len(V)
        V += [tuple(base - tng * w), tuple(base + tng * w), tuple(tip)]
        UV += [(0.3, 0.4), (0.3, 0.4), (0.95, 0.95)]
        COL += [(0, 0, 0.2, 1), (0, 0, 0.2, 1), (0, 0, 1, 1)]
        F.append((b, b + 2, b + 1))
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def proto_feather_quads(n=8, stalk_h=0.3, stalk_r=0.08, tent=0.8, splay=60, w=0.09, sides=6, bend=0.25):
    """Clove / organ-pipe polyp: stalk tube + n alpha-feathered tentacle quads (2 segments each).
    UV: stalk in the left half (u < 0.5), tentacle feather in the right half (u 0.5..1, v base->tip)."""
    V, F, UV, COL = [], [], [], []
    for j, (y, r) in enumerate(((0, stalk_r), (stalk_h, stalk_r * 0.85))):
        for k in range(sides):
            a = 2 * math.pi * k / sides
            V.append((r * math.cos(a), y, r * math.sin(a)))
            UV.append((0.05 + 0.4 * k / sides, 0.1 + 0.6 * j))
            COL.append((0, 0, 0.1 * j, 1))
    for k in range(sides):
        F.append((k, sides + k, sides + (k + 1) % sides, (k + 1) % sides))
    sp = math.radians(splay)
    for k in range(n):
        a = 2 * math.pi * (k + 0.5) / n
        d = np.array([math.cos(a), 0, math.sin(a)])
        tng = np.array([-math.sin(a), 0, math.cos(a)])
        base = np.array([0, stalk_h, 0]) + d * stalk_r * 0.5
        b = len(V)
        for s, t in enumerate((0.0, 1.0)):
            ang = sp + bend * t
            p = base + (d * math.sin(ang) + UP * math.cos(ang)) * tent * t
            ww = w * (0.6 + 0.4 * math.sin(math.pi * min(t + 0.2, 1.0)))
            V += [tuple(p - tng * ww), tuple(p + tng * ww)]
            UV += [(0.5, t), (1.0, t)]
            COL += [(0, 0, t, 1)] * 2
        F += [(b, b + 2, b + 3, b + 1)]
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def feather_canvas(stalk_bottom, stalk_top, tent_base, tent_tip, rachis, w=128, h=128, pinnules=9, seed=1,
                   tip_white=None):
    """Left half: stalk gradient. Right half: feathery tentacle (alpha) with rachis and pinnules."""
    cv = P.Canvas(w, h, stalk_bottom, 1.0)
    cv.vgrad(stalk_bottom, stalk_top)
    u, v = cv.u, cv.v
    right = u >= 0.5
    x = (u - 0.5) * 2  # 0..1 across the tentacle
    t = v
    col = hex2rgb(tent_base)[None, None] * (1 - t[..., None]) + hex2rgb(tent_tip)[None, None] * t[..., None]
    cv.rgb = np.where(right[..., None], col, cv.rgb)
    if tip_white:
        cv.blend(right * S(0.82, 0.95, t), tip_white)
    across = np.abs(x - 0.5) * 2
    width = 0.25 + 0.75 * np.sin(np.pi * np.clip(t * 1.1, 0, 1))
    rach = 1 - S(0.08, 0.16, across)
    pin = (0.5 + 0.5 * np.cos(2 * np.pi * (t * pinnules + across * 0.8))) ** 3 * (across < width)
    a = np.clip(rach + pin * 0.95, 0, 1) * (t < 0.98)
    cv.blend(right * rach, rachis, 0.6)
    cv.a = np.where(right, a, 1.0).astype(np.float32)
    return cv


# ----------------------------------------------------------------------------
# Kenya tree (C1)
# ----------------------------------------------------------------------------
# v3 (2026-10-13) from Tee's own clip (reference/kenya_clip; docs/research/video/coral_kenya.md [H] section): a short,
# thick, pale cream stalk fans into 8 stout trunks just above the rock (3 near-upright in the middle, 5 in a ring at
# 50-72 deg from vertical); each forks 4 more times into slender branches that arch upward in the middle and arch out
# and sag at the tips round the lower ring; every outer branch carries short pinnate twigs and feathery polyp sprigs
# (the fuzz of the clip). Warm pinkish tan, paler cream-white tips, pale translucent stalk. Footprint: crown top
# 4 cm under the surface of the resized 16 in tank, crown 26 x 20 cm about coral_kenya_main. Body and Tufts carry extras.swayMode = 1 (tree sway in static.vert and
# polyp.vert: trunk still, tips lag, sprigs ride their branch, local ripple around a passing fish).
MAXD = 5                  # 0 stalk, 1 trunks, 2..5 forks; pinnate twig tubes hang off depth 3..5 (LOD0 only)
KENYA_TRUNKS_UP = 3
KENYA_TRUNKS_RING = 5
KENYA_SPRIGS = 2200       # LOD0 polyp sprigs, 6 tris each: Kenya (body + sprigs) keeps its old share of the High budget
KENYA_SPRIGS_LOD1 = 1100
KENYA_LOD1_TRIS = 5000    # kenya_lod1.glb body: the branches without twigs, decimated to about this
KENYA_TWIG_STEP = 0.95    # cm between pinnate twigs along a branch (alternate sides)


def proto_sprig(n=3, length=1.0, w=0.3, splay=22.0):
    """Kenya polyp sprig: n alpha-feathered pinnate quads fanned about +Y (2n tris). UV: the right (feather) half of
    feather_canvas, v base -> tip; COLOR_0.b = t along the feather (flutter hinge in polyp.vert)."""
    V, F, UV, COL = [], [], [], []
    sp = math.radians(splay)
    for k in range(n):
        a = 2 * math.pi * k / n
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        tng = np.array([-math.sin(a), 0.0, math.cos(a)])
        b = len(V)
        for t in (0.0, 1.0):
            p = (d * math.sin(sp) + UP * math.cos(sp)) * length * t
            ww = w * (0.55 + 0.45 * t)
            V += [tuple(p - tng * ww), tuple(p + tng * ww)]
            UV += [(0.5, t), (1.0, t)]
            COL += [(0, 0, t, 1)] * 2
        F.append((b, b + 2, b + 3, b + 1))
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def raw_anchor(name):
    import rockscape as R
    for nm, typ, pos, extra, snap in R.ANCHORS:
        if nm == name:
            return np.array(pos, float), unit(extra.get("normal", [0, 1, 0]))
    raise KeyError(name)


def kenya(rock, A):
    pos, n = A["coral_kenya_main"]
    rs = RS(7)  # seed picked for the clip's front silhouette: widths 26 / 22 / 16 / 10 cm at 35 / 55 / 75 / 90 % height
    branches = []  # (points, radii, depth, phase, terminal)
    X, Z = np.array([1.0, 0, 0]), np.array([0, 0, 1.0])
    top_y = [pos[1] + 2.4]  # stalk top (set once the stalk is grown)

    def grow(p0, d, length, r0, depth, phase):
        pts, rad = [], []
        dd = unit(d)
        horiz = math.sqrt(max(0.0, 1.0 - dd[1] ** 2))  # sin(angle from vertical) at the branch root
        # clip: near-upright branches keep curving up; branches leaving at > ~30 deg arch out, their tips sag
        lift = 0.06 + 0.4 * (1.0 - horiz) ** 2
        sag = 0.0 if depth < 2 else 0.85 * max(0.0, horiz - 0.45)
        wob = unit(rs.normal(0, 1, 3)) * (0.12 if depth < 3 else 0.28)
        k = {0: 4, 1: 5, 2: 4, 3: 3}.get(depth, 2)
        p = np.array(p0, float)
        for i in range(k + 1):
            t = i / k
            pts.append(p.copy())
            if depth == 0:  # flared foot, swelling where the trunks fan out
                rad.append(r0 * (1.3 - 0.3 * S(0.0, 0.45, t)) * (1.0 - 0.35 * S(0.55, 1.0, t)))
            else:
                rad.append(r0 * (1.0 - (0.3 if depth < 3 else 0.45) * t))
            dd = unit(dd + (wob + UP * (lift - 2.0 * sag * t)) / k)
            # tips droop to ~20 deg below horizontal at most, and nothing dips below the stalk's crown
            floor_y = -0.35 if depth < 2 or p[1] > top_y[0] + 1.5 else 0.0
            if dd[1] < floor_y:
                dd = unit(np.r_[dd[0], 0.0, dd[2]] * math.sqrt(1 - floor_y ** 2) / max(math.hypot(dd[0], dd[2]), 1e-6)
                          + UP * floor_y)
            p = p + dd * length / k
        idx = len(branches)
        branches.append([np.array(pts), np.array(rad), depth, phase, True])
        if depth >= MAXD:
            return
        if depth == 0:
            top_y[0] = pts[-1][1]
            specs = [(rs.uniform(0, 2 * math.pi), rs.uniform(14, 30), 1.15) for _ in range(KENYA_TRUNKS_UP)]
            a0 = rs.uniform(0, 2 * math.pi)
            specs += [(a0 + 2 * math.pi * c / KENYA_TRUNKS_RING + rs.uniform(-0.3, 0.3), rs.uniform(50, 72), 1.12)
                      for c in range(KENYA_TRUNKS_RING)]
            for c, (az, inc, lf) in enumerate(specs):
                inc = math.radians(inc)
                cd = unit(UP * math.cos(inc) + (X * math.cos(az) + Z * math.sin(az)) * math.sin(inc))
                j = len(pts) - 2 - (c % 2)  # trunks leave the upper stalk, so it fans out rather than ends
                grow(pts[j], cd, length * 2.2 * lf * rs.uniform(0.9, 1.1), rad[j] * 0.48, 1, c / len(specs))
            branches[idx][4] = False
            return
        t1 = unit(np.cross(dd, UP)) if abs(dd[1]) < 0.97 else X
        t2 = unit(np.cross(t1, dd))
        rot = rs.uniform(-1.0, 1.0)
        fork = unit(t1 * math.cos(rot) + t2 * math.sin(rot))
        a = math.radians(rs.uniform(24, 40))
        kids = [(len(pts) - 1, unit(dd * math.cos(a * 0.55) + fork * math.sin(a * 0.55)), 1.0)]
        if depth < MAXD - 1 or rs.uniform() < 0.6:
            kids.append((len(pts) - 1, unit(dd * math.cos(a) - fork * math.sin(a)), 0.85))
        if depth <= 2 or (depth == 3 and rs.uniform() < 0.3):
            a2 = math.radians(rs.uniform(32, 48))
            az = rs.uniform(0, 2 * math.pi)
            kids.append((int(rs.integers(len(pts) // 2, len(pts) - 1)),
                         unit(dd * math.cos(a2) + (t1 * math.cos(az) + t2 * math.sin(az)) * math.sin(a2)), 0.7))
        branches[idx][4] = False
        for j, cd, lf in kids:
            grow(pts[j], cd, length * lf * rs.uniform(0.62, 0.74), rad[j] * 0.72, depth + 1, phase)

    trunk_dir = unit(0.9 * UP + 0.1 * n)
    grow(pos - n * 0.6, trunk_dir, 2.4, 2.2, 0, 0.0)
    allp = np.vstack([b[0] for b in branches])
    lo, hi = allp.min(0), allp.max(0)
    # Tee's clip sets the 26 x 20 cm crown; the measured 16 in tank caps the height (~17 cm colony, crown 4 cm
    # under the surface)
    top = min(pos[1] + 17.0, C.WATER_Y - 4.0)
    sy = (top - pos[1]) / (hi[1] - pos[1])
    # the whole colony above the stalk (sagging lower ring included) fits the 26 x 20 cm crown footprint
    crown = allp[allp[:, 1] > pos[1] + 0.12 * (hi[1] - pos[1])]
    clo, chi = crown.min(0), crown.max(0)
    sx, sz = 26.0 / (chi[0] - clo[0]), 20.0 / (chi[2] - clo[2])
    cx, cz = (clo[0] + chi[0]) / 2, (clo[2] + chi[2]) / 2
    rsc = float(np.clip(((sx + sz) / 2) ** 0.6, 0.8, 1.6))
    print(f"[coral] kenya warp sx {sx:.2f} sy {sy:.2f} sz {sz:.2f}")
    for b in branches:
        pts, rad = b[0], b[1]
        h = np.clip((pts[:, 1] - pos[1]) / (hi[1] - pos[1]), 0, 1)
        w = S(0.04, 0.22, h)
        q = pts.copy()
        q[:, 1] = pos[1] + (pts[:, 1] - pos[1]) * sy
        q[:, 0] = pts[:, 0] + w * ((pos[0] + 1.0 + (pts[:, 0] - cx) * sx) - pts[:, 0])
        q[:, 2] = pts[:, 2] + w * ((pos[2] + (pts[:, 2] - cz) * sz) - pts[:, 2])
        b[0], b[1] = q, rad * (1 + (rsc - 1) * (1 - 0.5 * h))
    allp = np.vstack([b[0] for b in branches])
    _q = allp[allp[:, 1] > pos[1] + 2.0]
    _h = (_q[:, 1] - pos[1] - 2.0) / max(1.0, top - pos[1] - 2.0)
    _w = [np.ptp(_q[(_h > lo_) & (_h < lo_ + 0.1), 0]) if np.any((_h > lo_) & (_h < lo_ + 0.1)) else 0 for lo_ in (0.3, 0.5, 0.7, 0.85)]
    print("[coral] kenya silhouette widths@.35/.55/.75/.9", np.round(_w, 1), "top-x", round(float(_q[_h > 0.7, 0].mean()), 1))
    print("[coral] kenya bbox", np.round(allp.min(0), 1), np.round(allp.max(0), 1))
    print(f"[coral] kenya branches {len(branches)} (per depth {[sum(1 for b in branches if b[2] == d) for d in range(MAXD + 1)]})")
    import geomlib as G
    g1 = T.WGeo()   # branches (LOD1 body)
    g0 = T.WGeo()   # branches + pinnate twigs (LOD0 body)
    sides = {0: 12, 1: 8, 2: 6, 3: 5, 4: 4, 5: 3}
    cand = []       # sprig candidates: (pos, dir, scale, phase, lod1_ok)
    ntw = 0
    for pts, rad, depth, phase, terminal in branches:
        V, F, tv, av = G.tube(pts, rad, sides[depth], cap_start=depth == 0, cap_end=terminal, up=(0, 0, 1))
        prog = np.clip((depth + tv) / (MAXD + 1.0), 0, 1)        # 0 stalk foot -> 1 branch tip (texture v)
        thin = np.full(len(V), 1 - np.interp(rad.mean(), [0.15, 2.0], [0.1, 0.9]))  # thin branches: translucent
        col = np.c_[thin, np.full(len(V), phase), prog ** 1.3, np.ones(len(V))]
        g0.add(V, F, np.c_[av, prog], col)
        g1.add(V, F, np.c_[av, prog], col)
        seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))]
        L = seg[-1]

        def at(s):
            i = min(int(np.searchsorted(seg, s, side="right")) - 1, len(pts) - 2)
            f = (s - seg[i]) / max(seg[i + 1] - seg[i], 1e-6)
            return pts[i] * (1 - f) + pts[i + 1] * f, rad[i] * (1 - f) + rad[i + 1] * f, unit(pts[i + 1] - pts[i])

        if terminal:  # tip fluff: a cream-tipped cluster of sprigs round the branch end
            c, r, tng = at(L)
            for _ in range(3):
                cand.append((c, unit(tng + rs.normal(0, 0.45, 3)), rs.uniform(1.0, 1.35), phase, True))
        if depth >= 2:  # polyp sprigs straight on the branch skin
            for _ in range(int(L / (0.55 if depth >= 4 else (0.8 if depth == 3 else 1.4)))):
                c, r, tng = at(rs.uniform(0.15, 1.0) * L)
                o = unit(np.cross(tng, rs.normal(0, 1, 3)))
                if np.dot(o, UP) < -0.2:
                    o = -o
                cand.append((c + o * r * 0.6, unit(o + tng * 0.8), rs.uniform(0.9, 1.35), phase, True))
        if depth >= 3:  # pinnate twigs, alternate sides, about 55 deg off the branch, each tipped with a sprig
            s, side, rot0 = rs.uniform(0.2, 0.6), 1.0, rs.uniform(0, math.pi)
            k = 0
            while s < L - 0.25:
                c, r, tng = at(s)
                t1 = unit(np.cross(tng, UP)) if abs(tng[1]) < 0.97 else X
                t2 = unit(np.cross(t1, tng))
                ang = rot0 + (k // 2) * math.pi / 2
                o = side * unit(t1 * math.cos(ang) + t2 * math.sin(ang))
                dtw = unit(tng * math.cos(math.radians(55)) + o * math.sin(math.radians(55)) + UP * 0.15)
                ln = rs.uniform(0.5, 0.9)
                tp = np.array([c + o * r * 0.8, c + o * r * 0.8 + dtw * ln])
                V, F, tv, av = G.tube(tp, np.array([max(r * 0.45, 0.07), 0.05]), 3, cap_start=False, cap_end=False,
                                      up=(0, 0, 1))
                pr = np.clip(0.9 + 0.1 * tv, 0, 1)
                g0.add(V, F, np.c_[av, pr], np.c_[np.full(len(V), 0.9), np.full(len(V), phase), pr ** 1.3,
                                                     np.ones(len(V))])
                cand.append((tp[1] - dtw * 0.1, unit(dtw + UP * 0.2), rs.uniform(0.7, 0.95), phase, False))
                ntw += 1
                side, k, s = -side, k + 1, s + KENYA_TWIG_STEP * rs.uniform(0.8, 1.2)
    print(f"[coral] kenya twigs {ntw}, body tris LOD0 {g0.tris()} LOD1 (pre-decimate) {g1.tris()}, sprig candidates {len(cand)}")

    def sprigs(rows, seed):
        inst = Instances(seed)
        for c, d, sc, ph, _ in rows:
            inst.add(c, d, sc, phase=(ph + rs.uniform(0, 0.15)) % 1.0)
        return inst

    keep = np.sort(RS(5).choice(len(cand), min(KENYA_SPRIGS, len(cand)), replace=False))
    inst = sprigs([cand[i] for i in keep], 12)
    ok1 = [i for i in range(len(cand)) if cand[i][4]]
    keep1 = np.sort(RS(6).choice(len(ok1), min(KENYA_SPRIGS_LOD1, len(ok1)), replace=False))
    inst1 = sprigs([cand[ok1[i]] for i in keep1], 13)
    # body: pale translucent cream stalk -> warm pinkish tan trunks/branches with lilac-grey mottling -> cream tips
    cv = P.Canvas(64, 256)
    cv.vgrad("#C9B3A8", "#A27D70", 0.02, 0.4, 0.8)
    spk = P.fbm(256, 64, 8, 3, 7)
    cv.blend(S(0.25, 0.6, cv.v) * S(0.55, 0.8, spk) * 0.4, "#98808A")
    cv.blend(S(0.8, 0.97, cv.v), "#D8C4BC")
    cv.multiply(0.92 + 0.16 * spk)
    body_m = C.material("kenya_body", rough=0.7, image=tex("kenya_body", cv))
    fc = feather_canvas("#A27D70", "#AE8A7E", "#A6806F", "#DCC8C0", "#8E6E64", pinnules=8, seed=5,
                        tip_white="#EFE4DE")
    polyp_m = C.material("kenya_polyp", rough=0.6, image=tex("kenya_polyp", fc, alpha=True), image_alpha=True,
                         double=True, clip=True)
    proto = proto_sprig(3, 1.3, 0.4, 24.0)
    ntri = len(proto[1]) + sum(1 for f in proto[1] if len(f) == 4)
    ex = {"alphaTest": 0.5, "swayMode": 1}
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_kenya_main", species="kenya_tree", swayGroups=3, swayMode=1)
    body = g0.obj("Body", root, "body", [body_m], smooth=True, extras={"swayMode": 1})
    pol = polyp_node("Tufts", root, proto, [polyp_m], inst, extras=ex)
    # LOD1 (Medium / far): branches without the twig tubes, decimated; fewer sprigs, same materials
    root1 = C.empty("Root")
    C.set_extras(root1, anchor="coral_kenya_main", species="kenya_tree", swayGroups=3, swayMode=1, lod=1)
    body1 = g1.obj("Body", root1, "body", [body_m], smooth=True, extras={"swayMode": 1})
    C.decimate(body1, min(1.0, KENYA_LOD1_TRIS / max(g1.tris(), 1)))
    pol1 = polyp_node("Tufts", root1, proto, [polyp_m], inst1, extras=ex)
    lod1 = (root1, [body1, pol1], {"Tufts": (len(inst1), ntri)})
    return root, [body, pol], {"Tufts": (len(inst), ntri)}, lod1


# ----------------------------------------------------------------------------
# Green star polyps (G1 neon clump, G2 mat)
# ----------------------------------------------------------------------------
def gsp(rock, A, anchor, R, half, spacing, count, polyp_hex, tip_hex, seed, name, mat_hex="#5A3F6B", raw=False,
        tent=0.6, long=False, mound=0.0):
    """Encrusting GSP mat draped on the rock. long=True: long-tentacle polyps ([P p12-p14], proto_long_tentacle)
    on a brown-maroon base that rises into a low mound `mound` cm high at the centre (full-tank photo 2026-10-13)."""
    pos, n = raw_anchor(anchor) if raw else A[anchor]

    def lift(e):  # e = squared normalised radius (0 centre .. ~0.92 edge)
        return mound * max(0.0, 1 - e / 0.92) ** 0.7
    rs = RS(seed)
    # mat grid
    na, nb = int(2 * half[0] / 0.7) + 1, int(2 * half[1] / 0.7) + 1
    aa, bb = np.meshgrid(np.linspace(-half[0], half[0], na), np.linspace(-half[1], half[1], nb))
    edge = (aa / half[0]) ** 2 + (bb / half[1]) ** 2
    warp = 1 + 0.18 * np.sin(np.arctan2(bb, aa) * 3 + seed) + 0.1 * np.sin(np.arctan2(bb, aa) * 5)
    inside = edge < warp ** 2 * 0.92
    Pm, Nm, t1, t2 = rock.drape(pos, n, R, aa.ravel(), bb.ravel())
    V, COL, UV, idx = [], [], [], {}
    for k, (p, nn) in enumerate(zip(Pm, Nm)):
        if p is None or not inside.ravel()[k]:
            continue
        idx[k] = len(V)
        e = edge.ravel()[k] / (warp.ravel()[k] ** 2)
        V.append(p + nn * (0.12 + lift(e)))
        COL.append((0.6, 0, 0, 1))
        UV.append((aa.ravel()[k] / (2 * half[0]) + 0.5, bb.ravel()[k] / (2 * half[1]) + 0.5))
    F = []
    for j in range(nb - 1):
        for i in range(na - 1):
            q = [j * na + i, j * na + i + 1, (j + 1) * na + i + 1, (j + 1) * na + i]
            if all(x in idx for x in q):
                ps = [V[idx[x]] for x in q]
                if max(np.linalg.norm(ps[a] - ps[(a + 1) % 4]) for a in range(4)) < 2.0:
                    F.append(tuple(idx[x] for x in q))
    g = T.WGeo()
    g.add(np.array(V), F, np.array(UV), np.array(COL))
    # fix winding so the mat faces away from the rock
    # polyps: jittered hex grid of `spacing`, clipped to the warped ellipse, `count` max
    pts = []
    rows = int(2 * half[1] / (spacing * 0.866)) + 1
    for j in range(rows):
        b = -half[1] + j * spacing * 0.866
        off = (j % 2) * spacing * 0.5
        for i in range(int(2 * half[0] / spacing) + 1):
            a = -half[0] + off + i * spacing
            a2, b2 = a + rs.normal(0, spacing * 0.18), b + rs.normal(0, spacing * 0.18)
            th = math.atan2(b2, a2)
            w = 1 + 0.18 * math.sin(th * 3 + seed) + 0.1 * math.sin(th * 5)
            if (a2 / half[0]) ** 2 + (b2 / half[1]) ** 2 < w * w * 0.85:
                pts.append((a2, b2))
    pts = np.array(pts)
    if len(pts) > count:
        pts = pts[np.sort(rs.choice(len(pts), count, replace=False))]
    Pp, Np, _, _ = rock.drape(pos, n, R, pts[:, 0], pts[:, 1])
    inst = Instances(seed + 1)
    flow = unit(np.array([-0.6, -0.2, 0.7]))
    for (a, b), p, nn in zip(pts, Pp, Np):
        if p is None:
            continue
        nn = unit(nn + n * 0.4 + UP * 0.2)
        ph = (np.dot(p, flow) / 25.0 + rs.normal(0, 0.03)) % 1.0
        th = math.atan2(b, a)
        e = ((a / half[0]) ** 2 + (b / half[1]) ** 2) / (1 + 0.18 * math.sin(th * 3 + seed) + 0.1 * math.sin(th * 5)) ** 2
        inst.add(p + nn * (0.08 + lift(e)), nn, rs.uniform(0.8, 1.2) * (0.75 + 0.25 * min(1.0, 1.4 - e)), phase=ph)
    if long:
        mat_m, pol_m = gsp_long_materials(name, mat_hex)
    else:
        mat_cv = P.Canvas(64, 64, mat_hex)
        mat_cv.multiply(0.85 + 0.3 * P.fbm(64, 64, 4, 3, seed))
        mat_m = C.material(name + "_mat", rough=0.8, image=tex(name + "_mat", mat_cv))
        pc = P.Canvas(64, 64, polyp_hex)
        t = np.clip((pc.u + pc.v) / 2, 0, 1)
        k = S(0.12, 0.3, t)[..., None]
        pc.rgb = hex2rgb("#4B3A55")[None, None] * (1 - k) + \
            (hex2rgb(polyp_hex)[None, None] * (1 - S(0.5, 0.95, t))[..., None] + hex2rgb(tip_hex)[None, None] * S(0.5, 0.95, t)[..., None]) * k
        # emissive factor = fluorescence color (the runtime scales it by uBlueFraction)
        pol_m = C.material(name + "_polyp", rough=0.5, image=tex(name + "_polyp", pc), emissive=tip_hex, emissive_strength=0.12)
    root = C.empty("Root")
    C.set_extras(root, anchor=anchor, species="green_star_polyps", fluorescent=1)
    body = g.obj("Mat", root, "body", [mat_m], smooth=True)
    C.recalc_normals(body)
    # recalc may flip an open patch: make the average normal agree with the anchor normal
    me = body.data
    avg = np.zeros(3)
    for poly in me.polygons:
        avg += np.array(C.b2w(poly.normal))
    if np.dot(avg, n) < 0:
        import bmesh
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.reverse_faces(bm, faces=bm.faces)
        bm.to_mesh(me)
        bm.free()
    if long:
        proto = proto_long_tentacle(seed=seed + 2)
        pol = polyp_node("Polyps", root, proto, [pol_m], inst, extras={"longTentacle": 1})
        return root, [body, pol], {"Polyps": (len(inst), sum(len(f) - 2 for f in proto[1]))}
    # longer, slightly wider tentacles than v1 (0.42): the neon clump reads as a shaggy pom-pom in tank_left.jpg
    proto = proto_feather(8, 0.18, 0.06, tent, 60, 0.05, 1)
    pol = polyp_node("Polyps", root, proto, [pol_m], inst)
    return root, [body, pol], {"Polyps": (len(inst), len(proto[1]) + sum(1 for f in proto[1] if len(f) == 4))}


# ----------------------------------------------------------------------------
# Long-tentacle green star polyps (Tee's photos 2026-10-13, docs/research/video/tee_tank_photos.md [P p12],
# [P p13]): not a flat star mat but a dense dome (~9 cm) of long, thin, curling fluorescent-lime tentacles rising
# from short brownish stalks on a brown-maroon base. Polyp COLOR_0.r = 1 marks the long-tentacle mode in polyp.vert
# (tentacles writhe, retract by shortening into the stalk, sway more toward the tips).
# ----------------------------------------------------------------------------
GSP_LONG_TENT = 1.7     # cm tentacle length at scale 1 ([P p12]: ~1.5-2 cm curling tentacles)
GSP_TENTACLES = 6       # strands per polyp (the animal has 8; 6 ribbons keep the dome inside the triangle budget)
GSP_TENT_SEGS = 4       # ribbon segments per strand (enough for the curled tip)


def proto_long_tentacle(n=GSP_TENTACLES, stalk_h=0.35, stalk_r=0.08, tent=GSP_LONG_TENT, segs=GSP_TENT_SEGS,
                        w=0.07, seed=5):
    """Long-tentacle polyp: a 3-sided stalk (6 tris) and n curling ribbon tentacles (segs quads each).
    Each strand splays out, leans further out along its length and curls at the tip (t^3); the ribbon's width runs
    across the curl plane so the curl reads from the front. UV: stalk u < 0.5, tentacle u 0.55..0.95 with v base->tip.
    COLOR_0: r = 1 (long-tentacle flag), b = hinge (0.15 at the tentacle base -> 1 at the tip; stalk 0..0.12)."""
    rs = RS(seed)
    V, F, UV, COL = [], [], [], []
    for j, (y, r) in enumerate(((0.0, stalk_r), (stalk_h, stalk_r * 0.8))):
        for k in range(3):
            a = 2 * math.pi * k / 3
            V.append((r * math.cos(a), y, r * math.sin(a)))
            UV.append((0.1 + 0.3 * k / 3, 0.1 + 0.6 * j))
            COL.append((1, 0, 0.12 * j, 1))
    for k in range(3):
        F.append((k, 3 + k, 3 + (k + 1) % 3, (k + 1) % 3))
    sub = 4  # integration steps per ribbon segment
    for k in range(n):
        a = 2 * math.pi * (k + 0.5) / n + rs.uniform(-0.25, 0.25)
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        tng = np.array([-math.sin(a), 0.0, math.cos(a)])
        L = tent * rs.uniform(0.8, 1.15)
        th0 = math.radians(rs.uniform(15, 35))          # splay from the polyp axis at the base
        bend = math.radians(rs.uniform(20, 45))         # extra outward lean along the strand
        curl = math.radians(rs.uniform(110, 210)) * (1 if rs.uniform() < 0.75 else -1)  # hooked tip, mostly outward
        side = rs.uniform(-0.3, 0.3)
        p = np.array([0.0, stalk_h, 0.0]) + d * stalk_r * 0.5
        pts = [p.copy()]
        nsub = segs * sub
        for i in range(nsub):
            t = (i + 0.5) / nsub
            th = th0 + bend * t + curl * t ** 3
            p = p + unit(d * math.sin(th) + UP * math.cos(th) + tng * side * math.sin(math.pi * t)) * L / nsub
            pts.append(p.copy())
        b = len(V)
        for i in range(0, nsub + 1, sub):
            t = i / nsub
            ww = w * (1 - 0.5 * t)
            V += [tuple(pts[i] - tng * ww), tuple(pts[i] + tng * ww)]
            UV += [(0.55, t), (0.95, t)]
            COL += [(1, 0, 0.15 + 0.85 * t, 1)] * 2
        for s in range(segs):
            F.append((b + 2 * s, b + 2 * s + 1, b + 2 * s + 3, b + 2 * s + 2))
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def gsp_long_materials(name, base_hex="#4A2E36"):
    """Base (brown-maroon stolon tissue) and polyp (brown stalk, lime tentacles with paler tips) materials."""
    mat_cv = P.Canvas(64, 64, base_hex)
    mat_cv.multiply(0.8 + 0.4 * P.fbm(64, 64, 5, 3, 17))
    mat_cv.blend(S(0.6, 0.8, P.fbm(64, 64, 9, 2, 18)), "#6A4A3A", 0.5)
    mat_m = C.material(name + "_mat", rough=0.8, image=tex(name + "_mat", mat_cv))
    pc = P.Canvas(64, 64, "#5A3F38")
    st = pc.u < 0.5
    stalk = hex2rgb("#4E3434")[None, None] * (1 - pc.v[..., None]) + hex2rgb("#6E5A3A")[None, None] * pc.v[..., None]
    t = pc.v
    tent = (hex2rgb("#5E8A22")[None, None] * (1 - S(0.0, 0.25, t))[..., None]
            + hex2rgb("#A8E838")[None, None] * (S(0.0, 0.25, t) * (1 - S(0.7, 1.0, t)))[..., None]
            + hex2rgb("#D8FF7A")[None, None] * S(0.7, 1.0, t)[..., None])
    pc.rgb = np.where(st[..., None], stalk, tent).astype(np.float32)
    # the lime fluoresces under the blue-heavy light ([P p12]: tentacles glow against the dark rock)
    pol_m = C.material(name + "_polyp", rough=0.45, image=tex(name + "_polyp", pc), emissive="#B4FF3C",
                       emissive_strength=0.3, double=True)
    return mat_m, pol_m


def dome_mesh(c, radii, axis, seed, segs=22, rings=11, sink=0.35):
    """Lumpy dome: an ellipsoid (radii x, along-axis, z) cut at -sink x its height, around `axis`.
    Returns V, F, UV, surf(th, ph) -> world point, q (local +Y -> axis)."""
    rs = RS(seed)
    q = q_from_up(axis, rs.uniform(0, 6.28))
    ph1, ph2 = rs.uniform(0, 6.28, 2)
    rx, ry, rz = radii

    def local(th, ph):
        lump = 1 + 0.07 * math.sin(3 * ph + ph1) * math.sin(2 * th) + 0.05 * math.sin(5 * ph + ph2) * math.sin(th)
        return np.array([rx * math.sin(th) * math.cos(ph), ry * math.cos(th), rz * math.sin(th) * math.sin(ph)]) * lump

    def surf(th, ph):
        return q_rotate(q, local(th, ph)) + c

    th_max = math.acos(-sink)
    V, UV = [surf(0.0, 0.0)], [(0.5, 0.5)]
    for i in range(1, rings + 1):
        th = th_max * i / rings
        for k in range(segs):
            ph = 2 * math.pi * k / segs
            V.append(surf(th, ph))
            UV.append((0.5 + 0.5 * i / rings * math.cos(ph), 0.5 + 0.5 * i / rings * math.sin(ph)))
    F = [(0, 1 + (k + 1) % segs, 1 + k) for k in range(segs)]
    for i in range(rings - 1):
        b0, b1 = 1 + i * segs, 1 + (i + 1) * segs
        for k in range(segs):
            F.append((b0 + k, b0 + (k + 1) % segs, b1 + (k + 1) % segs, b1 + k))
    return np.array(V), F, np.array(UV), surf, q


def inside_rock(rock, p, margin=0.15):
    h, hn, d = rock.nearest(p)
    return h is not None and float(np.dot(p - h, hn)) < margin


def gsp_dome(rock, A, anchor, radii, count, seed, name):
    """[P p12/p13] upper-left colony: a dome of long-tentacle polyps on a brown-maroon base."""
    pos, n = A[anchor]
    rs = RS(seed)
    hit, hn, _ = rock.nearest(pos)
    axis = unit(UP * 0.8 + unit(n) * 0.35)
    c = hit + axis * radii[1] * 0.25
    V, F, UV, surf, q = dome_mesh(c, radii, axis, seed)
    g = T.WGeo()
    g.add(V, F, UV, (0.6, 0, 0, 1))
    inst = Instances(seed + 1)
    flow = unit(np.array([-0.6, -0.2, 0.7]))
    m = count * 3
    ga = math.pi * (3 - math.sqrt(5))
    th_max = math.acos(-0.3)
    for i in range(m):
        if len(inst) >= count:
            break
        cth = 1 - (i + 0.5) / m * (1 - math.cos(th_max))      # uniform over the cap area
        th, ph = math.acos(cth) + rs.normal(0, 0.03), (i * ga) % (2 * math.pi) + rs.normal(0, 0.05)
        p = surf(th, ph)
        e = 0.01
        nn = axis if th < 0.05 else unit(np.cross(surf(th, ph + e) - surf(th, ph - e), surf(th + e, ph) - surf(th - e, ph)))
        if np.dot(nn, p - c) < 0:
            nn = -nn
        if inside_rock(rock, p, 0.25):
            continue
        # the lower flanks carry shorter polyps ([P p13]: the dome edge thins out onto the rock)
        sc = rs.uniform(0.8, 1.2) * (0.7 + 0.3 * S(th_max, 0.4, th))
        ph_sway = (np.dot(p, flow) / 25.0 + rs.normal(0, 0.03)) % 1.0
        inst.add(p + nn * 0.05, unit(nn + axis * 0.25), sc, phase=ph_sway)
    mat_m, pol_m = gsp_long_materials(name)
    root = C.empty("Root")
    C.set_extras(root, anchor=anchor, species="green_star_polyps", fluorescent=1, form="long_tentacle_dome")
    body = g.obj("Mat", root, "body", [mat_m], smooth=True)
    C.recalc_normals(body)
    proto = proto_long_tentacle(seed=seed + 2)
    pol = polyp_node("Polyps", root, proto, [pol_m], inst, extras={"longTentacle": 1})
    print(f"[coral] {name}: dome at {np.round(c, 1)} axis {np.round(axis, 2)}, {len(inst)} polyps")
    return root, [body, pol], {"Polyps": (len(inst), sum(len(f) - 2 for f in proto[1]))}


# ----------------------------------------------------------------------------
# New corals from Tee's photos 2026-10-13 (tee_tank_photos.md). Their anchors live in rockscape.py ANCHORS and are
# snapped to the rock there (anchors.json, mirrored in core RockData DefaultScape). The raw points below are only a
# fallback for an anchors.json written before they were added (mode "nearest" = closest rock point, "top" = first
# rock hit straight down).
# ----------------------------------------------------------------------------
NEW_ANCHORS = {
    # [P p04] toadstool on the low front-left top of the R5 mound near the sand, the pink plate at its left foot
    "coral_toadstool_front": ((60.5, 9.3, 29.0), "nearest"),
    "coral_leather_pink_front": ((57.8, 9.0, 32.0), "nearest"),
    # [P p14] tan finger leather on the arch's left top (R6 back pier), up and left of the clowns' host
    "coral_finger_leather": ((76.0, 27.3, 12.0), "nearest"),
    # [P p05] big corallimorph on the front edge of the high back-left rock, ~3 cm under the surface
    "coral_elephant_ear": ((41.0, 45.0, 16.5), "top"),
}


def new_anchor(rock, name):
    path = os.path.join(C.SIM, "anchors.json")
    snapped = {a["name"]: a for a in json.load(open(path))["anchors"]} if os.path.exists(path) else {}
    if name in snapped:
        hit = np.array(snapped[name]["pos"], float)
        hn = np.array(snapped[name].get("normal", [0, 1, 0]), float)
    else:
        raw, mode = NEW_ANCHORS[name]
        raw = np.array(raw, float)
        if mode == "top":
            hit, hn = rock.ray(raw, np.array([0, -1.0, 0]), 80.0)
        else:
            hit, hn, _ = rock.nearest(raw)
    hn = unit(hn if hn[1] > -0.2 else hn + UP)
    print(f"[anchor] {name}: pos {np.round(hit, 2).tolist()} normal {np.round(hn, 2).tolist()}")
    return hit, hn


def revolve(profile, segs, wobble, axis, base, seed, uvf):
    """Surface of revolution: profile [(r, y)] from the underside centre round the rim to the top centre; the rim
    wobble(phi) scales r. uvf(i, r, phi) -> uv. Returns V, F, UV, q (local +Y -> axis)."""
    q = q_from_up(axis, RS(seed).uniform(0, 6.28))
    V, UV = [], []
    for i, (r, y) in enumerate(profile):
        for k in range(segs):
            ph = 2 * math.pi * k / segs
            rr = r * wobble(ph)
            V.append(q_rotate(q, np.array([rr * math.cos(ph), y, rr * 0.92 * math.sin(ph)])) + base)
            UV.append(uvf(i, r, ph))
    F = []
    for i in range(len(profile) - 1):
        b0, b1 = i * segs, (i + 1) * segs
        for k in range(segs):
            F.append((b0 + k, b0 + (k + 1) % segs, b1 + (k + 1) % segs, b1 + k))
    return np.array(V), F, np.array(UV), q


def toadstool(rock, A):
    """Sarcophyton toadstool leather [P p04]: pale cream-lavender stalk flaring at the foot and under the cap, a
    golden-tan cap ~6.5 cm across with an irregular rolled rim, covered in short extended polyps."""
    import geomlib as G
    hit, hn = new_anchor(rock, "coral_toadstool_front")
    rs = RS(151)
    axis = unit(UP * 0.9 + hn * 0.15 + np.array([0.1, 0, 0.05]))
    g = T.WGeo()
    H = 4.6
    pts = np.array([hit + axis * y for y in np.linspace(-0.4, H, 7)])
    Vs, Fs, tv, av = G.tube(pts, [2.0, 1.6, 1.3, 1.2, 1.2, 1.35, 1.7], 14, cap_start=False, cap_end=False,
                            up=(0, 0, 1))
    Vs = Vs + rs.normal(0, 0.04, Vs.shape)
    g.add(Vs, Fs, np.c_[av, tv], np.c_[np.full(len(Vs), 0.2), np.zeros(len(Vs)), tv * 0.3, np.ones(len(Vs))], mat=0)
    top = hit + axis * (H - 0.2)
    ph0 = rs.uniform(0, 6.28)

    def wob(ph):
        return 1 + 0.08 * math.sin(5 * ph + ph0) + 0.04 * math.sin(9 * ph + 2 * ph0)
    prof = [(1.5, -0.1), (2.5, 0.0), (3.1, 0.25), (3.3, 0.6), (3.05, 0.95), (2.3, 1.2), (1.2, 1.35), (0.05, 1.4)]
    RMAX = 3.3

    def uvf(i, r, ph):  # top: radial disc in 0..0.8; underside and rim: outer ring 0.8..1
        rr = r / RMAX * 0.8 if i >= 4 else 0.8 + 0.2 * (1 - i / 4)
        return (0.5 + 0.5 * rr * math.cos(ph), 0.5 + 0.5 * rr * math.sin(ph))
    Vc, Fc, UVc, q = revolve(prof, 28, wob, axis, top, 152, uvf)
    colc = [(0.5, 0, 0.2 + 0.4 * min(1, i / 4), 1) for i in range(len(prof)) for _ in range(28)]
    g.add(Vc, Fc, UVc, np.array(colc), mat=1)
    # cap polyps on the top surface (profile rows 4..7)
    top_r, top_y = [0.05, 1.2, 2.3, 3.05], [1.4, 1.35, 1.2, 0.95]
    inst = Instances(153)
    for _ in range(220):
        ph = rs.uniform(0, 2 * math.pi)
        r = 3.0 * math.sqrt(rs.uniform())
        y = float(np.interp(r, top_r, top_y))
        lp = np.array([r * wob(ph) * math.cos(ph), y + 0.02, r * wob(ph) * 0.92 * math.sin(ph)])
        nn = unit(q_rotate(q, unit(np.array([math.cos(ph) * r / RMAX * 0.6, 1.0, math.sin(ph) * r / RMAX * 0.6]))))
        inst.add(q_rotate(q, lp) + top, nn, rs.uniform(0.75, 1.2))
    st = P.Canvas(64, 64, "#CFC5BA")
    st.blend(S(0.55, 0.75, P.fbm(64, 64, 6, 3, 154)), "#B9AFB8", 0.6)
    st.blend(S(0.7, 0.85, P.fbm(64, 64, 10, 2, 155)), "#9C8C78", 0.4)
    st.blend(S(0.8, 1.0, st.v), "#B8A88E", 0.5)
    m_stalk = C.material("toadstool_stalk", rough=0.6, image=tex("toadstool_stalk", st))
    cc = P.Canvas(128, 128, "#A8862C")
    rr = np.hypot(cc.u - 0.5, cc.v - 0.5) * 2
    cc.multiply(0.8 + 0.35 * P.fbm(128, 128, 16, 3, 156))
    cc.blend(S(0.78, 0.84, rr), "#6F5A38", 0.9)
    m_cap = C.material("toadstool_cap", rough=0.7, image=tex("toadstool_cap", cc))
    pc = P.Canvas(64, 64, "#B08A2A")
    pc.vgrad("#8A6A22", "#E2C860", 0.3, 1.0)
    m_pol = C.material("toadstool_polyp", rough=0.6, image=tex("toadstool_polyp", pc), double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_toadstool_front", species="sarcophyton_sp")
    body = g.obj("Body", root, "body", [m_stalk, m_cap], smooth=True)
    C.recalc_normals(body)
    proto = proto_feather(8, 0.12, 0.05, 0.36, 62, 0.05, 1)
    pol = polyp_node("Polyps", root, proto, [m_pol], inst)
    return root, [body, pol], {"Polyps": (len(inst), sum(len(f) - 2 for f in proto[1]))}


def leather_pink(rock, A):
    """[P p04] pink-lavender lobed leather plate (Lobophytum-like) beside the toadstool: a thick folded plate with
    pink polyp pores (polyps retracted in the photo) on a cream-lavender skin. Texture only, no polyp instances."""
    hit, hn = new_anchor(rock, "coral_leather_pink_front")
    axis = unit(UP * 0.7 + hn * 0.5)
    c = hit + axis * 0.3
    radii = (4.4, 1.9, 3.1)
    V, F, _, _, q = dome_mesh(c, radii, axis, 161, 24, 9, sink=0.2)
    qi = np.array([-q[0], -q[1], -q[2], q[3]])
    loc = np.array([q_rotate(qi, v - c) for v in V])
    UV = np.c_[loc[:, 0] / (2.2 * radii[0]) + 0.5, loc[:, 2] / (2.2 * radii[2]) + 0.5]  # top-projected pores
    rad = np.hypot(loc[:, 0], loc[:, 2])
    g = T.WGeo()
    g.add(V, F, UV, np.c_[np.full(len(V), 0.4), np.zeros(len(V)), np.clip(rad / radii[0], 0, 1) * 0.3, np.ones(len(V))])
    cv = P.Canvas(256, 256, "#CDB8C6")
    cv.multiply(0.85 + 0.25 * P.fbm(256, 256, 8, 3, 162))
    u, v = cv.u * 34, cv.v * 34
    du = (u + (np.floor(v) % 2) * 0.5) % 1 - 0.5
    dv = v % 1 - 0.5
    rp = np.hypot(du, dv) + 0.12 * (P.fbm(256, 256, 30, 1, 163) - 0.5)
    cv.blend(1 - S(0.22, 0.32, rp), "#D07892", 0.9)
    cv.blend(1 - S(0.08, 0.15, rp), "#8E4060", 0.8)
    cv.blend(S(0.8, 0.95, np.hypot(cv.u - 0.5, cv.v - 0.5) * 2), "#D9CBB4", 0.8)
    m = C.material("leather_pink", rough=0.65, image=tex("leather_pink", cv))
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_leather_pink_front", species="lobophytum_sp")
    body = g.obj("Body", root, "body", [m], smooth=True)
    C.recalc_normals(body)
    return root, [body], {}


def finger_leather(rock, A):
    """[P p14] tan finger leather (Sinularia-like): thick blunt finger lobes from a short trunk, fuzzy with tiny
    extended polyps."""
    import geomlib as G
    hit, hn = new_anchor(rock, "coral_finger_leather")
    rs = RS(171)
    axis = unit(UP * 0.8 + hn * 0.4)
    t1 = unit(np.cross(axis, [0, 0, 1.0]))
    t2 = np.cross(axis, t1)
    g = T.WGeo()
    inst = Instances(172)
    trunk_top = hit + axis * 1.6
    V, F, tv, av = G.tube(np.array([hit - axis * 0.4, hit + axis * 0.8, trunk_top]), [2.2, 1.9, 1.7], 10,
                          cap_start=False, cap_end=True, up=(0, 0, 1))
    g.add(V, F, np.c_[av, tv * 0.2], np.c_[np.full(len(V), 0.3), np.zeros(len(V)), np.zeros(len(V)), np.ones(len(V))])
    nf, sides = 10, 6
    per = 18
    for k in range(nf):
        a = 2 * math.pi * k / nf + rs.uniform(-0.25, 0.25)
        out = t1 * math.cos(a) + t2 * math.sin(a)
        L = rs.uniform(3.2, 5.2) * (0.8 if k % 3 == 0 else 1.0)
        base = trunk_top + out * rs.uniform(0.6, 1.3) - axis * 0.3
        lean = rs.uniform(0.6, 1.2)
        pts = G.bezier(base, base + (axis * 0.6 + out * lean * 1.5) * L * 0.33,
                       base + (axis * 1.4 + out * lean * 1.3) * L * 0.45,
                       base + unit(axis * 1.2 + out * lean + rs.normal(0, 0.25, 3)) * L, 7)
        rads = np.linspace(0.62, 0.36, 7)
        V, F, tv, av = G.tube(pts, rads, sides, cap_start=False, cap_end=True, up=tuple(axis))
        g.add(V, F, np.c_[av, 0.2 + tv * 0.8], np.c_[np.full(len(V), 0.3), np.full(len(V), k / nf), tv, np.ones(len(V))])
        # polyp fuzz: tiny feathery polyps on the finger surface (ring vertices i*sides + s sit around pts[i])
        for _ in range(per):
            i = int(rs.integers(1, len(pts)))
            s = int(rs.integers(0, sides))
            vtx = V[i * sides + s]
            nrm = unit(vtx - pts[i])
            inst.add(vtx + nrm * 0.02, nrm, rs.uniform(0.8, 1.2), phase=(k / nf + rs.normal(0, 0.04)) % 1)
    cv = P.Canvas(64, 128, "#A8865A")
    cv.multiply(0.85 + 0.3 * P.fbm(128, 64, 10, 3, 173))
    cv.blend(S(0.6, 0.75, P.fbm(128, 64, 40, 1, 174)), "#D8C49A", 0.6)
    cv.blend(S(0.9, 1.0, cv.v), "#C4A878", 0.5)
    m = C.material("finger_leather", rough=0.7, image=tex("finger_leather", cv))
    pc = P.Canvas(32, 32, "#CDB88E")
    m_p = C.material("finger_leather_polyp", rough=0.6, image=tex("finger_leather_polyp", pc), double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_finger_leather", species="sinularia_sp")
    body = g.obj("Body", root, "body", [m], smooth=True)
    C.recalc_normals(body)
    proto = proto_feather(6, 0.1, 0.035, 0.24, 62, 0.04, 1)
    pol = polyp_node("Polyps", root, proto, [m_p], inst)
    return root, [body, pol], {"Polyps": (len(inst), len(proto[1]))}


EAR_R = 6.5  # cm disc radius ([P p05]: a ~13 cm disc, folded like a taco)


def ear_local(t, a, ph):
    """Elephant-ear disc in its local frame (+Y up): back half curled up, sides cupped, ruffled rim."""
    R = EAR_R
    x, z = R * t * math.cos(a), R * t * 0.9 * math.sin(a)
    y = R * (0.12 + 0.10 * (x / R) ** 2 + 0.38 * max(0.0, -z / R) ** 2) \
        + R * (0.05 * t ** 3 * math.sin(7 * a + ph) + 0.02 * t ** 2 * math.sin(13 * a))
    return np.array([x, y, z])


def proto_bead(h=0.24, r=0.13):
    """Bulbous discal tentacle ("bead"): a 4-sided pyramid (4 tris) with a light tip."""
    V = [(r, 0, 0), (0, 0, r), (-r, 0, 0), (0, 0, -r), (0, h, 0)]
    F = [(0, 4, 1), (1, 4, 2), (2, 4, 3), (3, 4, 0)]
    UV = [(0.5, 0.1)] * 4 + [(0.5, 0.95)]
    COL = [(0, 0, 0.2, 1)] * 4 + [(0, 0, 1, 1)]
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def elephant_ear(rock, A):
    """[P p05] big corallimorph (Amplexidiscus / large Rhodactis): a ~13 cm disc folded up at the back, the centre
    covered in dark purple-brown bulbous tentacles, a blue-grey rim band of fine bumps, a few yellow-green spots and
    a raised mouth with an orange lip."""
    hit, hn = new_anchor(rock, "coral_elephant_ear")
    rs = RS(181)
    nrm = unit(UP * 0.8 + hn * 0.3)
    ph = rs.uniform(0, 6.28)
    q = q_from_up(nrm, math.radians(rs.uniform(-20, 20)))
    c = hit - nrm * 0.2
    rings, segs = 8, 40
    V, UV, COL = [ear_local(0, 0, ph)], [(0.5, 0.5)], [(0.3, 0, 0.1, 1)]
    for i in range(1, rings + 1):
        t = i / rings
        for k in range(segs):
            a = 2 * math.pi * k / segs
            V.append(ear_local(t, a, ph))
            UV.append((0.5 + 0.5 * t * math.cos(a), 0.5 + 0.5 * t * math.sin(a)))
            COL.append((0.5 + 0.4 * t, 0, 0.1 + 0.6 * t ** 2, 1))
    F = [(0, 1 + (k + 1) % segs, 1 + k) for k in range(segs)]
    for i in range(rings - 1):
        b0, b1 = 1 + i * segs, 1 + (i + 1) * segs
        for k in range(segs):
            F.append((b0 + k, b0 + (k + 1) % segs, b1 + (k + 1) % segs, b1 + k))
    b = len(V)  # underside skirt down to the foot
    for k in range(segs):
        a = 2 * math.pi * k / segs
        V.append(np.array([1.4 * math.cos(a), -0.3, 1.3 * math.sin(a)]))
        UV.append((0.5 + 0.05 * math.cos(a), 0.5 + 0.05 * math.sin(a)))
        COL.append((0, 0, 0, 1))
    outer = 1 + (rings - 1) * segs
    for k in range(segs):
        F.append((outer + k, outer + (k + 1) % segs, b + (k + 1) % segs, b + k))
    mb = len(V)  # raised mouth cone
    for k in range(8):
        a = 2 * math.pi * k / 8
        V.append(ear_local(0.07, a, ph) + np.array([0, 0.05, 0]))
        UV.append((0.5 + 0.02 * math.cos(a), 0.5 + 0.02 * math.sin(a)))
        COL.append((0.3, 0, 0.1, 1))
    V.append(ear_local(0.0, 0.0, ph) + np.array([0, 0.45, 0]))
    UV.append((0.5, 0.5))
    COL.append((0.3, 0, 0.1, 1))
    F += [(mb + k, mb + 8, mb + (k + 1) % 8) for k in range(8)]
    g = T.WGeo()
    g.add(np.array([q_rotate(q, v) for v in V]) + c, F, np.array(UV), np.array(COL))
    inst = Instances(182)
    sp = 0.45
    for j in range(int(2 * EAR_R / (sp * 0.866)) + 1):
        for i in range(int(2 * EAR_R / sp) + 1):
            x = -EAR_R + i * sp + (j % 2) * sp * 0.5 + rs.normal(0, sp * 0.15)
            z = -EAR_R + j * sp * 0.866 + rs.normal(0, sp * 0.15)
            t = math.hypot(x, z / 0.9) / EAR_R
            if t > 0.8 or t < 0.09:
                continue
            a = math.atan2(z / 0.9, x)
            e = 0.01
            du = ear_local(min(t + e, 1), a, ph) - ear_local(max(t - e, 0), a, ph)
            dv = ear_local(t, a + e, ph) - ear_local(t, a - e, ph)
            ln = unit(np.cross(dv, du))
            if ln[1] < 0:
                ln = -ln
            inst.add(q_rotate(q, ear_local(t, a, ph)) + c, unit(q_rotate(q, ln)), rs.uniform(0.8, 1.3) * (1.1 - 0.3 * t))
    cv = P.Canvas(256, 256, "#3C2830")
    rr = np.hypot(cv.u - 0.5, cv.v - 0.5) * 2
    cv.multiply(0.85 + 0.3 * P.fbm(256, 256, 12, 3, 183))
    band = S(0.8, 0.88, rr)
    cv.blend(band, "#7C8294", 0.85)
    cv.blend(band * S(0.55, 0.7, P.fbm(256, 256, 70, 1, 184)), "#4E5368", 0.6)
    cv.blend(S(0.96, 1.0, rr), "#5E6478", 0.6)
    spots = S(0.74, 0.78, P.fbm(256, 256, 22, 2, 185)) * S(0.45, 0.65, rr) * (1 - S(0.85, 0.9, rr))
    cv.blend(spots, "#C9D640", 0.9)
    cv.blend(1 - S(0.03, 0.05, rr), "#D88A3A", 0.9)
    cv.blend(1 - S(0.015, 0.03, rr), "#2A1A1E", 0.9)
    m = C.material("elephant_ear", rough=0.6, image=tex("elephant_ear", cv), emissive="#C9D640",
                   emissive_strength=0.0, double=True)
    bc = P.Canvas(32, 64, "#4A303A")
    bc.vgrad("#3A2630", "#9A7C88", 0.3, 1.0)
    m_b = C.material("elephant_ear_bead", rough=0.5, image=tex("elephant_ear_bead", bc))
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_elephant_ear", species="amplexidiscus_fenestrafer")
    body = g.obj("Body", root, "body", [m], smooth=True)
    proto = proto_bead()
    pol = polyp_node("Beads", root, proto, [m_b], inst)
    return root, [body, pol], {"Beads": (len(inst), len(proto[1]))}


# ----------------------------------------------------------------------------
# Zoanthids (C4) on a rubble rock on the sand
# ----------------------------------------------------------------------------
def rubble(center, radii, seed, segs=14, rings=8, rough=0.35):
    import geomlib as G
    V, F = G.ellipsoid(center, radii, segs, rings)
    rs = RS(seed)
    c = np.asarray(center, float)
    d = V - c
    nrm = unit(d / np.asarray(radii) ** 2)
    noise = rs.normal(0, 1, len(V))
    V = V + nrm * (noise[:, None] * rough * min(radii) * 0.25)
    V[:, 1] = np.maximum(V[:, 1], c[1] - radii[1] * 0.4)  # flat-ish bottom buried in the sand
    return V, F


def zoa(rock, A):
    pos, n = A["coral_zoa_front_center"]
    c = np.array([pos[0], pos[1] - 0.2, pos[2]])
    V, F = rubble(c, (3.2, 1.9, 2.2), 3, segs=24, rings=12)
    g = T.WGeo()
    g.add(V, F, np.c_[(V[:, 0] - c[0]) / 6 + 0.5, (V[:, 2] - c[2]) / 6 + 0.5], (0.2, 0, 0, 1))
    rs = RS(21)
    instA, instB = Instances(22), Instances(23)
    k = 0
    tries = 0
    placed = []
    while k < 44 and tries < 6000:  # tank_right.jpg: the colony is a tight carpet of ~40 heads
        tries += 1
        th = rs.uniform(0, 2 * math.pi)
        ph = rs.uniform(0, 1.15)
        d = np.array([math.cos(th) * math.sin(ph) * 3.2, math.cos(ph) * 1.9, math.sin(th) * math.sin(ph) * 2.2])
        p = c + d
        # snap to the actual rubble surface (nearest vertex region)
        j = np.argmin(np.linalg.norm(V - p, axis=1))
        p = V[j]
        if p[1] < c[1] + 0.05 or any(np.linalg.norm(p - q) < 0.56 for q in placed):
            continue
        placed.append(p)
        nn = unit(p - c + UP * 0.8)
        # colony: orange/green-centred (A) on one side, brown/cream (B) on the other
        (instA if (p[0] - c[0]) + rs.normal(0, 0.8) < 0.6 else instB).add(p, nn, rs.uniform(0.85, 1.15))
        k += 1
    # polyp: column (8 sides) + oral disc (12) + 24 rim tentacles
    def proto(r=0.5, h=0.5):
        Vp, Fp, UVp, COLp = [], [], [], []
        for j, (y, rr) in enumerate(((0, r * 0.55), (h, r * 0.6))):
            for s in range(8):
                a = 2 * math.pi * s / 8
                Vp.append((rr * math.cos(a), y, rr * math.sin(a)))
                UVp.append((0.1, 0.1 + 0.2 * j))
                COLp.append((0, 0, 0.1 * j, 1))
        for s in range(8):
            Fp.append((s, 8 + s, 8 + (s + 1) % 8, (s + 1) % 8))
        b = len(Vp)
        Vp.append((0, h + 0.03, 0))
        UVp.append((0.75, 0.5))
        COLp.append((0, 0, 0.2, 1))
        for s in range(12):
            a = 2 * math.pi * s / 12
            Vp.append((r * 0.62 * math.cos(a), h + 0.06, r * 0.62 * math.sin(a)))
            UVp.append((0.75 + 0.24 * math.cos(a), 0.5 + 0.48 * math.sin(a)))
            COLp.append((0, 0, 0.3, 1))
        for s in range(12):
            Fp.append((b, b + 1 + (s + 1) % 12, b + 1 + s))
        for s in range(24):
            a = 2 * math.pi * (s + 0.5) / 24
            dd = np.array([math.cos(a), 0, math.sin(a)])
            tg = np.array([-math.sin(a), 0, math.cos(a)])
            base = np.array([0, h + 0.05, 0]) + dd * r * 0.6
            tip = base + dd * r * 0.45 + UP * 0.1
            bb = len(Vp)
            Vp += [tuple(base - tg * 0.07), tuple(base + tg * 0.07), tuple(tip)]
            UVp += [(0.3, 0.6), (0.3, 0.6), (0.3, 0.9)]
            COLp += [(0, 0, 0.3, 1)] * 2 + [(0, 0, 1, 1)]
            Fp.append((bb, bb + 2, bb + 1))
        return np.array(Vp, float), Fp, np.array(UVp, float), np.array(COLp, float)

    def ptex(name, column, rim, disc, center, tip=None, mouth=None, glow=0.0):
        """u < 0.5: column (v 0.1-0.3) and rim tentacles (v 0.6 base -> 0.9 tip); u >= 0.5: oral disc."""
        cv = P.Canvas(64, 64, column)
        cv.blend((cv.u < 0.5) * S(0.5, 0.7, cv.v), rim)
        if tip:  # pale tentacle tips (the brown rim reads striped cream-brown in [P p07] [P p08])
            cv.blend((cv.u < 0.5) * S(0.74, 0.88, cv.v), tip)
        rr = np.hypot((cv.u - 0.75) / 0.25, (cv.v - 0.5) / 0.5)
        cv.blend((cv.u >= 0.5) * 1.0, disc)
        cv.blend((cv.u >= 0.5) * (1 - S(0.3, 0.5, rr)), center)
        if mouth:
            cv.blend((cv.u >= 0.5) * (1 - S(0.08, 0.16, rr)), mouth)
        cv.blend((cv.u >= 0.5) * S(0.85, 1.0, rr), rim)
        return C.material(name, rough=0.55, image=tex(name, cv), emissive=center, emissive_strength=glow)

    rock_cv = P.Canvas(64, 64, "#A89A80")
    rock_cv.multiply(0.8 + 0.35 * P.fbm(64, 64, 6, 3, 9))
    rm = C.material("zoa_rubble", rough=0.9, image=tex("zoa_rubble", rock_cv))
    mA = ptex("zoa_polyp_orange", "#7A5A3A", "#E0792A", "#E0792A", "#5FBF5A")
    # [P p02] [P p07-p09]: brown rim tentacles with pale tips, a bright yellow oral ring, a green centre and a dark
    # mouth (was a cream-centred brown morph). No emissive: it is one flat colour and would tint the brown column green
    mB = ptex("zoa_polyp_brown", "#3E2A1C", "#5E3C22", "#D9C238", "#7CC23A", tip="#C8AE7E", mouth="#2A3418")
    mC = ptex("paly_polyp_brown", "#5A4030", "#7A5A36", "#7A5A36", "#D8C8A0")
    # paly colony (C4b): 20 brown polyps at R2's right foot + a 10-polyp orange patch at (17, 5, 30)
    instC, instD = Instances(24), Instances(25)
    for cen, cnt, inst_, rad in ((A["coral_paly_left_foot"][0], 20, instC, 2.2), (np.array([17.0, 5.0, 30.0]), 10, instD, 1.4)):
        placed, tries = [], 0
        while len(placed) < cnt and tries < 3000:
            tries += 1
            q = cen + np.array([rs.uniform(-rad, rad), rs.uniform(0, 2.0), rs.uniform(-rad, rad)])
            hit, hn, dist = rock.nearest(q)
            if dist > 1.5 or hn[1] < -0.2 or any(np.linalg.norm(hit - x) < 0.6 for x in placed):
                if hn is not None and hn[1] > 0.0 and dist <= 3.0 and q[1] < cen[1] + 0.5:
                    pass
                continue
            placed.append(hit)
            inst_.add(hit + hn * 0.05, unit(hn + UP * 0.6), rs.uniform(0.8, 1.1))
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_zoa_front_center", species="zoanthids")
    body = g.obj("Rubble", root, "body", [rm], smooth=True)
    C.recalc_normals(body)
    pr = proto()
    a = polyp_node("PolypsOrange", root, pr, [mA], instA)
    b = polyp_node("PolypsBrown", root, proto(0.5, 0.8), [mB], instB)  # [P p07]: taller stalks than the orange morph
    c_ = polyp_node("PalyBrown", root, pr, [mC], instC, extras={"anchor": "coral_paly_left_foot"})
    d_ = polyp_node("PalyOrange", root, pr, [mA], instD, extras={"anchor": "coral_paly_left_foot"})
    nt = len(pr[1]) + sum(1 for f in pr[1] if len(f) == 4)
    return root, [body, a, b, c_, d_], {"PolypsOrange": (len(instA), nt), "PolypsBrown": (len(instB), nt),
                                        "PalyBrown": (len(instC), nt), "PalyOrange": (len(instD), nt)}


# ----------------------------------------------------------------------------
# Clove polyps (C6): 3 tufts on stolons
# ----------------------------------------------------------------------------
def clove(rock, A):
    import geomlib as G
    pos, n = A["coral_clove_kenya_front"]
    rs = RS(31)
    g = T.WGeo()
    inst = Instances(32)
    t1 = unit(np.cross(UP, n))
    t2 = np.cross(n, t1)
    for tuft in range(3):
        cen = pos + t1 * (tuft - 1) * 2.6 + t2 * rs.uniform(-1.2, 1.2)
        hit, nn, _ = rock.nearest(cen)
        # stolon network: 5 short runners from the tuft centre, laid on the rock, a polyp at nearly every node
        # (spec: ~40 stalks per colony; the photo shows a dense, wispy patch)
        for s in range(5):
            ang = 2 * math.pi * s / 5 + rs.uniform(-0.3, 0.3)
            pts = []
            for k in range(6):
                q = hit + (t1 * math.cos(ang) + t2 * math.sin(ang)) * k * 0.45
                h, hn, _ = rock.nearest(q)
                pts.append(h + hn * 0.12)
            pts = np.array(pts)
            V, F, tv, av = G.tube(pts, 0.12, 4, cap_start=False, cap_end=True, up=(0, 1, 0))
            g.add(V, F, np.c_[av * 0.5, tv * 0.3], (0.8, tuft / 3, 0, 1))
            for k in range(1, 6):
                if rs.uniform() < 0.9:
                    h, hn, _ = rock.nearest(pts[k])
                    inst.add(h + hn * 0.1, unit(hn + n * 0.6 + UP * 0.3), rs.uniform(0.8, 1.2),
                             phase=(tuft / 3 + rs.normal(0, 0.05)) % 1)
        for _ in range(2):
            q = hit + t1 * rs.normal(0, 0.6) + t2 * rs.normal(0, 0.6)
            h, hn, _ = rock.nearest(q)
            inst.add(h + hn * 0.1, unit(hn + n * 0.6 + UP * 0.3), rs.uniform(0.9, 1.3), phase=tuft / 3)
    cv = P.Canvas(64, 64, "#6E6A3C")
    st = C.material("clove_stolon", rough=0.85, image=tex("clove_stolon", cv))
    fc = feather_canvas("#8A7A66", "#A8957F", "#B8A38E", "#CBBBA6", "#E0D6C6", seed=3)
    pm = C.material("clove_polyp", rough=0.6, image=tex("clove_polyp", fc, alpha=True), image_alpha=True,
                    double=True, clip=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_clove_kenya_front", species="clove_polyps")
    body = g.obj("Stolons", root, "body", [st], smooth=True)
    pr = proto_feather_quads(8, 0.35, 0.09, 0.8, 62, 0.11, 4, 0.35)
    pol = polyp_node("Polyps", root, pr, [pm], inst, extras={"alphaTest": 0.5})
    nt = sum(len(f) - 2 for f in pr[1])
    return root, [body, pol], {"Polyps": (len(inst), nt)}


# ----------------------------------------------------------------------------
# Mushroom (C5)
# ----------------------------------------------------------------------------
def shroom_disc(c, n, radius, seed):
    """Ruffled corallimorph disc (radius cm) on a short stalk, oriented to normal n."""
    rs = RS(seed)
    V, UV, COL, F = [], [], [], []
    rings, segs = 6, 32
    V.append((0, 0.36 * radius, 0))
    UV.append((0.5, 0.5))
    COL.append((0, 0, 0.3, 1))
    ph = rs.uniform(0, 6)
    for i in range(1, rings + 1):
        t = i / rings
        for k in range(segs):
            a = 2 * math.pi * k / segs
            r = radius * t
            ruffle = radius * (0.12 * t ** 3 * math.sin(8 * a + ph) + 0.04 * t ** 2 * math.sin(13 * a))
            y = radius * (0.36 - 0.18 * t ** 2) + ruffle
            V.append((r * math.cos(a), y, r * 0.9 * math.sin(a)))
            UV.append((0.5 + 0.5 * t * math.cos(a), 0.5 + 0.5 * t * math.sin(a)))
            COL.append((0.8 * t, 0, t, 1))
    for k in range(segs):
        F.append((0, 1 + (k + 1) % segs, 1 + k))
    for i in range(rings - 1):
        b0, b1 = 1 + i * segs, 1 + (i + 1) * segs
        for k in range(segs):
            F.append((b0 + k, b0 + (k + 1) % segs, b1 + (k + 1) % segs, b1 + k))
    b = len(V)
    for k in range(segs):
        a = 2 * math.pi * k / segs
        V.append((0.28 * radius * math.cos(a), 0.0, 0.25 * radius * math.sin(a)))
        UV.append((0.5 + 0.1 * math.cos(a), 0.5 + 0.1 * math.sin(a)))
        COL.append((0, 0, 0, 1))
    outer = 1 + (rings - 1) * segs
    for k in range(segs):
        F.append((outer + k, outer + (k + 1) % segs, b + (k + 1) % segs, b + k))
    V = np.array(V, float)
    q = q_from_up(unit(n), rs.uniform(0, 6.28))
    V = np.array([q_rotate(q, v) for v in V]) + np.asarray(c, float)
    return V, F, np.array(UV), np.array(COL)


def shroom_tex(name, base, rim, plug, stripe, spot):
    cv = P.Canvas(256, 256, base)
    rr = np.hypot(cv.u - 0.5, cv.v - 0.5) * 2
    cv.blend(S(0.7, 0.98, rr), rim)
    cv.blend(1 - S(0.06, 0.14, rr), plug)
    ang = np.arctan2(cv.v - 0.5, cv.u - 0.5)
    cv.blend(0.25 * (0.5 + 0.5 * np.cos(ang * 36)) * S(0.15, 0.6, rr), stripe)
    spots = (P.fbm(256, 256, 24, 2, 4) > 0.62) * S(0.2, 0.8, rr)
    cv.blend(spots * 0.5, spot)
    return tex(name, cv)


def mushroom(rock, A):
    pos, n = A["coral_shroom_center"]
    g = T.WGeo()
    V, F, UV, COL = shroom_disc(pos - UP * 0.1, (0, 1, 0), 1.75, 61)
    g.add(V, F, UV, COL, mat=0)
    pos2, n2 = A["coral_shroom_small"]
    V, F, UV, COL = shroom_disc(pos2 - unit(n2) * 0.1, n2, 1.25, 62)
    g.add(V, F, UV, COL, mat=1)
    m1 = C.material("mushroom", rough=0.5, image=shroom_tex("mushroom", "#5DBD5E", "#2D7F6F", "#C8B48A", "#3AA868",
                                                            "#8BF0A8"), emissive="#5DBD5E", emissive_strength=0.0,
                    double=True)
    m2 = C.material("mushroom_small", rough=0.5, image=shroom_tex("mushroom_small", "#8B3A4A", "#5E2434", "#B07A60",
                                                                  "#7A2E3E", "#B05A6A"), double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_shroom_center", species="mushroom", fluorescent=0.5)
    body = g.obj("Body", root, "body", [m1, m2], smooth=True, extras={"anchors": ["coral_shroom_center",
                                                                                    "coral_shroom_small"]})
    return root, [body], {}


# ----------------------------------------------------------------------------
# Hairy mushroom (Rhodactis sp.): the clownfish host. A cluster of flat-to-slightly-cupped discs 3-6 cm across,
# the oral surface densely covered in short branching ("hairy") tentacles: olive-green base, green tentacles
# with a lighter, fluorescent tip glow; no visible mouth under the hair (a faint one at most).
# ----------------------------------------------------------------------------
HAIRY_DISCS = 5           # discs in the clump
HAIRY_SPACING = 0.25      # cm between tentacle tufts (jittered); sets the instance count (~1100)


def hairy_disc_local(radius, cup, ph, t, a):
    """Point on a hairy-mushroom disc in its local frame (cm, base at the origin, oral surface up +Y).
    t: 0 centre -> 1 rim, a: angle. Cupped: the rim rises by `cup` x radius, with a gentle 5-6 lobe wave."""
    r = radius * t
    wave = radius * (0.05 * t ** 3 * math.sin(5 * a + ph) + 0.025 * t ** 2 * math.sin(11 * a + 2 * ph))
    y = radius * (0.30 + cup * t ** 2) + wave
    return np.array([r * math.cos(a), y, r * 0.92 * math.sin(a)])


def hairy_disc(c, n, radius, cup, seed, twist):
    """Disc mesh (oral surface + a short stalk skirt) placed at c, oriented to normal n.
    Returns V, F, UV, COL and the (q, ph) needed to place tentacles on the same surface."""
    rs = RS(seed)
    ph = rs.uniform(0, 6.28)
    rings, segs = 5, 24
    V, UV, COL, F = [hairy_disc_local(radius, cup, ph, 0.0, 0.0)], [(0.5, 0.5)], [(0.2, 0, 0.25, 1)], []
    for i in range(1, rings + 1):
        t = i / rings
        for k in range(segs):
            a = 2 * math.pi * k / segs
            V.append(hairy_disc_local(radius, cup, ph, t, a))
            UV.append((0.5 + 0.5 * t * math.cos(a), 0.5 + 0.5 * t * math.sin(a)))
            COL.append((0.8 * t, 0, 0.25 + 0.75 * t, 1))
    for k in range(segs):
        F.append((0, 1 + (k + 1) % segs, 1 + k))
    for i in range(rings - 1):
        b0, b1 = 1 + i * segs, 1 + (i + 1) * segs
        for k in range(segs):
            F.append((b0 + k, b0 + (k + 1) % segs, b1 + (k + 1) % segs, b1 + k))
    b = len(V)  # underside skirt down to the stalk foot
    for k in range(segs):
        a = 2 * math.pi * k / segs
        V.append(np.array([0.3 * radius * math.cos(a), 0.0, 0.27 * radius * math.sin(a)]))
        UV.append((0.5 + 0.08 * math.cos(a), 0.5 + 0.08 * math.sin(a)))
        COL.append((0, 0, 0, 1))
    outer = 1 + (rings - 1) * segs
    for k in range(segs):
        F.append((outer + k, outer + (k + 1) % segs, b + (k + 1) % segs, b + k))
    q = q_from_up(unit(n), twist)
    V = np.array([q_rotate(q, v) for v in V]) + np.asarray(c, float)
    return V, F, np.array(UV), np.array(COL), q, ph


def proto_hairy(h=0.24):
    """One branching tentacle tuft (10 tris): a short 3-sided stub (6 tris) splitting into 4 single-triangle
    branchlets that fork out and up. UV v runs base (0) -> tip (1) so the tips take the fluorescent colour."""
    V, F, UV, COL = [], [], [], []
    stub = h * 0.45
    for j, (y, r) in enumerate(((0.0, 0.06), (stub, 0.045))):
        for k in range(3):
            a = 2 * math.pi * k / 3
            V.append((r * math.cos(a), y, r * math.sin(a)))
            UV.append((0.2 + 0.3 * k / 3, 0.05 + 0.4 * j))
            COL.append((0, 0, 0.45 * j, 1))
    for k in range(3):
        F.append((k, 3 + k, 3 + (k + 1) % 3, (k + 1) % 3))
    for k in range(4):
        a = 2 * math.pi * (k + 0.25) / 4
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        tng = np.array([-math.sin(a), 0.0, math.cos(a)])
        base = np.array([0.0, stub, 0.0]) + d * 0.02
        L = h * (0.85 if k % 2 else 0.75)
        sp = math.radians(52 if k % 2 else 40)
        tip = base + (d * math.sin(sp) + UP * math.cos(sp)) * L
        bi = len(V)
        V += [tuple(base - tng * 0.055), tuple(base + tng * 0.055), tuple(tip)]
        UV += [(0.6, 0.45), (0.8, 0.45), (0.7, 0.98)]
        COL += [(0, 0, 0.45, 1), (0, 0, 0.45, 1), (0, 0, 1, 1)]
        F.append((bi, bi + 2, bi + 1))
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def mushroom_hairy(rock, A):
    """Rhodactis carpet on the low R10 rock under the right shelf (HOST_SHROOM, snapped to the rock by rockscape; the clowns'
    host spot, HOST_ARCH_CLOWNS hovers just above it)."""
    anchors_all = {a["name"]: a for a in json.load(open(os.path.join(C.SIM, "anchors.json")))["anchors"]}
    host = np.array(anchors_all["HOST_ARCH_CLOWNS"]["pos"], float)
    hit, hn = None, None
    if "HOST_SHROOM" in anchors_all:
        hit = np.array(anchors_all["HOST_SHROOM"]["pos"], float)
        hn = unit(np.array(anchors_all["HOST_SHROOM"].get("normal", [0, 1, 0]), float))
    for d in (() if hit is not None else ((0, -1, 0), (0, -0.6, -0.8), (0, 0, -1), (-0.6, -0.6, -0.5), (0.6, -0.6, -0.5))):
        p_, n_ = rock.ray(host, unit(np.array(d, float)), 30.0)
        if p_ is not None and (hit is None or np.linalg.norm(p_ - host) < np.linalg.norm(hit - host)):
            hit, hn = p_, n_
    if hit is None:  # fallback: the sand under the host point
        hit, hn = np.array([host[0], 4.0, host[2]]), np.array([0, 1.0, 0])
    hn = unit(hn + UP * 0.6)
    print(f"[coral] mushroom_hairy: clump at {np.round(hit, 1)} normal {np.round(hn, 2)}")
    rs = RS(97)
    # disc centres: one big disc in the middle and the rest around it, overlapping slightly like a clump
    t1 = unit(np.cross(hn, [0, 0, 1.0]) if abs(hn[2]) < 0.9 else np.cross(hn, [1.0, 0, 0]))
    t2 = np.cross(hn, t1)
    radii = [2.9, 2.2, 1.9, 1.6, 1.5][:HAIRY_DISCS]  # 3-6 cm discs
    offs = [(0, 0)] + [(math.cos(a) * rr, math.sin(a) * rr) for a, rr in
                       zip(np.linspace(0, 2 * math.pi, HAIRY_DISCS - 1, endpoint=False) + 0.4, (3.9, 3.6, 3.4, 3.6))]
    g = T.WGeo()
    inst = Instances(98)
    discs = []
    for k, (r, (oa, ob)) in enumerate(zip(radii, offs)):
        guess = hit + t1 * oa + t2 * ob + hn * 2.0
        p, nrm, _ = rock.nearest(guess)
        if p is None or np.linalg.norm(p - guess) > 5.0:
            p, nrm = hit + t1 * oa + t2 * ob, hn
        nrm = unit(unit(nrm) * 0.5 + hn * 0.5 + unit(np.array([oa, 0, ob]) + 1e-6) * 0.15 * (k > 0))
        cup = rs.uniform(0.04, 0.14)
        V, F, UV, COL, q, ph = hairy_disc(p - nrm * 0.15, nrm, r, cup, 300 + k, rs.uniform(0, 6.28))
        g.add(V, F, UV, COL)
        discs.append((p - nrm * 0.15, nrm, r, cup, q, ph))
    # tentacle tufts: jittered hex grid over each disc's oral surface, normals from the local surface
    proto = proto_hairy()
    for (c, nrm, r, cup, q, ph) in discs:
        sp = HAIRY_SPACING
        for j in range(int(2 * r / (sp * 0.866)) + 1):
            for i in range(int(2 * r / sp) + 1):
                x = -r + i * sp + (j % 2) * sp * 0.5 + rs.normal(0, sp * 0.15)
                z = -r + j * sp * 0.866 + rs.normal(0, sp * 0.15)
                t = math.hypot(x, z / 0.92) / r
                if t > 0.97:
                    continue
                a = math.atan2(z / 0.92, x)
                lp = hairy_disc_local(r, cup, ph, t, a)
                e = 0.01
                du = hairy_disc_local(r, cup, ph, min(t + e, 1), a) - hairy_disc_local(r, cup, ph, max(t - e, 0), a)
                dv = hairy_disc_local(r, cup, ph, t, a + e) - hairy_disc_local(r, cup, ph, t, a - e)
                ln = unit(np.cross(dv, du)) if t > 0.03 else UP
                if ln[1] < 0:
                    ln = -ln
                ln = unit(ln + UP * 0.3)
                wp = q_rotate(q, lp) + c
                wn = unit(q_rotate(q, ln))
                # shorter, denser fuzz toward the rim; a faint bare mouth spot at the centre
                scale = (0.75 + 0.35 * (1 - t)) * rs.uniform(0.85, 1.15) * (0.55 if t < 0.06 else 1.0)
                inst.add(wp + wn * 0.02, wn, scale)
    # textures: disc = dark olive with a slightly paler rim and a faint dark centre; tentacles olive -> green ->
    # light fluorescent green tips
    dc = P.Canvas(128, 128, "#2E4420")
    rr = np.hypot(dc.u - 0.5, dc.v - 0.5) * 2
    dc.blend(S(0.6, 1.0, rr), "#56743A", 0.7)
    dc.blend(1 - S(0.04, 0.12, rr), "#26341A", 0.6)
    dc.multiply(0.85 + 0.3 * P.fbm(128, 128, 8, 3, 99))
    disc_m = C.material("mushroom_hairy", rough=0.85, image=tex("mushroom_hairy", dc), emissive="#6FD45A",
                        emissive_strength=0.0, double=True)
    pc = P.Canvas(64, 64, "#4A6A2C")
    tt = pc.v
    pc.rgb = (hex2rgb("#34481E")[None, None] * (1 - S(0.0, 0.55, tt))[..., None]
              + hex2rgb("#4E8C2E")[None, None] * (S(0.0, 0.55, tt) * (1 - S(0.72, 0.97, tt)))[..., None]
              + hex2rgb("#9CEB6C")[None, None] * S(0.72, 0.97, tt)[..., None])
    pc.multiply(0.92 + 0.16 * P.fbm(64, 64, 6, 2, 100))
    pol_m = C.material("mushroom_hairy_polyp", rough=0.55, image=tex("mushroom_hairy_polyp", pc), emissive="#8CF060",
                       emissive_strength=0.1, double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_shroom_hairy", species="rhodactis_sp", fluorescent=0.6,
                 hostFor="clown_orange,clown_darwin", discs=HAIRY_DISCS)
    body = g.obj("Body", root, "body", [disc_m], smooth=True)
    pol = polyp_node("Polyps", root, proto, [pol_m], inst)
    return root, [body, pol], {"Polyps": (len(inst), len(proto[1]) + sum(1 for f in proto[1] if len(f) == 4))}


# ----------------------------------------------------------------------------
# Red Gracilaria-type macroalgae (M1): 3 tufts
# ----------------------------------------------------------------------------
def macro_red(rock, A):
    import geomlib as G
    rs = RS(41)
    g = T.WGeo()
    tufts = (("coral_macro_back_left", 10.0), ("coral_macro_back_left_2", 7.0), ("coral_macro_lane", 5.0),
             ("coral_macro_right_corner", 6.0))
    for tuft, (aname, size) in enumerate(tufts):
        pos, n = A[aname]
        if aname == "coral_macro_lane":  # on the sand
            base, bn = pos, UP
        else:
            base, bn, _ = rock.nearest(pos)
        for br in range(int(round(size * 1.9))):
            d = unit(UP * 0.8 + bn * 0.6 + rs.normal(0, 0.45, 3))
            L = rs.uniform(0.4, 1.0) * size
            pts = []
            p = base + rs.normal(0, 0.4, 3)
            dd = d
            for k in range(7):
                pts.append(p.copy())
                dd = unit(dd + rs.normal(0, 0.18, 3) + UP * 0.05)
                p = p + dd * L / 6
            pts = np.array(pts)
            rad = np.linspace(0.07, 0.04, len(pts))
            V, F, tv, av = G.tube(pts, rad, 3, cap_start=False, cap_end=True, up=(0, 0, 1))
            g.add(V, F, np.c_[av, tv], np.c_[np.ones(len(V)), np.full(len(V), (tuft / 3 + br * 0.03) % 1), tv, np.ones(len(V))])
            # 1-level side branches
            for s in range(2):
                k0 = rs.integers(2, 5)
                q = [pts[k0]]
                dd2 = unit(unit(pts[k0 + 1] - pts[k0]) + rs.normal(0, 0.8, 3))
                for k in range(3):
                    q.append(q[-1] + dd2 * L * 0.12)
                    dd2 = unit(dd2 + rs.normal(0, 0.2, 3))
                q = np.array(q)
                V, F, tv, av = G.tube(q, np.linspace(0.05, 0.03, 4), 3, cap_start=False, cap_end=True, up=(0, 0, 1))
                w = (k0 / 6) + tv * 0.4
                g.add(V, F, np.c_[av, 0.4 + tv * 0.6], np.c_[np.ones(len(V)), np.full(len(V), (tuft / 3) % 1), np.clip(w, 0, 1), np.ones(len(V))])
    cv = P.Canvas(16, 128)
    cv.vgrad("#7A1F3A", "#C23B5E", 0, 1, 0.8)
    cv.blend(S(0.85, 1.0, cv.v), "#D0506A", 0.5)
    m = C.material("macro_red", rough=0.6, image=tex("macro_red", cv))
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_macro_back_left", species="red_macroalgae",
                 anchors=[t[0] for t in tufts])
    body = g.obj("Body", root, "body", [m], smooth=True)
    return root, [body], {}


# ----------------------------------------------------------------------------
# Organ pipe coral (Tubipora musica) replacing the aiptasia (C7)
# ----------------------------------------------------------------------------
def organ_pipe(rock, A):
    import geomlib as G
    pos, n = A["coral_organpipe_kenya_base"]
    # sits on a ledge left of the Kenya base: grow upward from the nearest rock under the anchor
    rs = RS(51)
    base, bn, _ = rock.nearest(pos + UP * 0.5)
    g = T.WGeo()
    inst = Instances(52)
    t1 = np.array([1.0, 0, 0])
    t2 = np.array([0, 0, 1.0])
    # colony ~6 x 5 cm: ~46 parallel tubes (r 0.17 cm) of varying height, a dome profile, with two
    # horizontal "platforms" (the stolonal plates that tie the pipes together)
    pts = []
    tries = 0
    while len(pts) < 90 and tries < 8000:
        tries += 1
        a, b = rs.uniform(-3, 3), rs.uniform(-2.5, 2.5)
        if (a / 3) ** 2 + (b / 2.5) ** 2 > 1:
            continue
        if any((a - x) ** 2 + (b - y) ** 2 < 0.4 ** 2 for x, y in pts):
            continue
        pts.append((a, b))
    for a, b in pts:
        foot = base + t1 * a + t2 * b
        h, hn, _ = rock.nearest(foot + UP * 2)
        foot = np.array([foot[0], h[1] - 0.2, foot[2]])
        dome = 1 - ((a / 3) ** 2 + (b / 2.5) ** 2)
        height = 1.2 + 2.6 * dome + rs.uniform(-0.3, 0.3)
        lean = unit(UP + rs.normal(0, 0.06, 3))
        top = foot + lean * height
        line = np.array([foot, top])
        V, F, tv, av = G.tube(line, [0.2, 0.18], 5, cap_start=False, cap_end=False, up=(1, 0, 0))
        g.add(V, F, np.c_[av, tv * 0.8], (0.3, 0, 0, 1))
        # rim cap (open top shown as a darker ring)
        ring = V[-5:]
        c = top - lean * 0.05
        g.add(np.vstack([ring, [c]]), [(k, (k + 1) % 5, 5) for k in range(5)], np.full((6, 2), (0.9, 0.9)), (0.3, 0, 0, 1))
        inst.add(top, lean, rs.uniform(0.8, 1.15))
    for py in (1.0, 2.2):  # platforms: thin warped discs
        segs = 14
        ring = [base + UP * py + t1 * 3.1 * math.cos(2 * math.pi * k / segs) * (0.9 if py > 1.5 else 1)
                + t2 * 2.6 * math.sin(2 * math.pi * k / segs) * (0.9 if py > 1.5 else 1) for k in range(segs)]
        ring = [r + UP * 0.15 * math.sin(k * 1.7) for k, r in enumerate(ring)]
        V = np.vstack([ring, [base + UP * py]])
        F = [(segs, (k + 1) % segs, k) for k in range(segs)]
        V2 = V - UP * 0.15
        F2 = [(segs + 1 + k, segs + 1 + (k + 1) % segs, segs + 1 + segs) for k in range(segs)]
        Fs = [(k, (k + 1) % segs, segs + 1 + (k + 1) % segs, segs + 1 + k) for k in range(segs)]
        g.add(np.vstack([V, V2]), F + F2 + Fs, np.full((2 * segs + 2, 2), (0.5, 0.5)), (0.5, 0, 0, 1))
    cv = P.Canvas(64, 64, "#8A2424")
    cv.vgrad("#7A1F1F", "#9A2A2A")
    cv.multiply(0.85 + 0.3 * P.fbm(64, 64, 8, 3, 2))
    cv.blend(S(0.85, 0.95, cv.u) * S(0.85, 0.95, cv.v), "#4A1010")
    sk = C.material("organpipe_skeleton", rough=0.8, image=tex("organpipe_skeleton", cv))
    fc = feather_canvas("#B8C97A", "#C7A16A", "#B8C97A", "#C9CFA0", "#E8E0C8", seed=5, tip_white="#E8E0C8")
    pm = C.material("organpipe_polyp", rough=0.55, image=tex("organpipe_polyp", fc, alpha=True), image_alpha=True,
                    double=True, clip=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_organpipe_kenya_base", species="organ_pipe")
    body = g.obj("Skeleton", root, "body", [sk], smooth=False)
    pr = proto_feather_quads(8, 0.12, 0.12, 0.35, 70, 0.07, 4, 0.4)
    pol = polyp_node("Polyps", root, pr, [pm], inst, extras={"alphaTest": 0.5})
    nt = sum(len(f) - 2 for f in pr[1])
    return root, [body, pol], {"Polyps": (len(inst), nt)}


# ----------------------------------------------------------------------------
# hair-algae tuft cards at the GRAZE anchors
# ----------------------------------------------------------------------------
def algae_tufts(rock, A):
    rs = RS(61)
    g = T.WGeo()
    spots = []
    for name in sorted(k for k in A if k.startswith("GRAZE_")):
        pos, n = A[name]
        hit, nn, _ = rock.nearest(pos)
        spots.append((hit, nn))
    # crests: top surfaces found by vertical rays on a jittered 4 cm grid, Poisson-thinned to 5 cm
    for x in np.arange(3, 120, 4.0):
        for z in np.arange(2, C.TANK[2] - 1, 4.0):
            q = np.array([x + rs.uniform(-1.5, 1.5), C.WATER_Y + 5, z + rs.uniform(-1.5, 1.5)])
            hit, nn = rock.ray(q, np.array([0, -1.0, 0]), 60)
            if hit is None or nn[1] < 0.6 or hit[1] < 7 or hit[1] > C.WATER_Y - 1:
                continue
            if all(np.linalg.norm(hit - h) > 5.0 for h, _ in spots):
                spots.append((hit, nn))
    print(f"[coral] hair tufts: {len(spots)}")
    for hit, nn in spots:
        nn = unit(nn + UP * 0.3)
        for c in range(3):  # three crossed cards
            ang = math.pi * c / 3 + rs.uniform(0, 0.4)
            side = unit(np.cross(nn, [math.cos(ang), 0.3, math.sin(ang)]))
            w, h = rs.uniform(1.6, 2.4), rs.uniform(1.8, 2.8)
            p0 = hit - nn * 0.1
            V = [p0 - side * w / 2, p0 + side * w / 2, p0 + side * w / 2 + nn * h, p0 - side * w / 2 + nn * h]
            col = [(0, 0, 0, 1), (0, 0, 0, 1), (0, 0, 1, 1), (0, 0, 1, 1)]
            g.add(np.array(V), [(0, 1, 2, 3)], np.array([(0, 0), (1, 0), (1, 1), (0, 1)]), np.array(col))
    cv = P.Canvas(128, 128, "#3B5B2E", 0.0)
    rs2 = RS(62)
    a = np.zeros((128, 128))
    col = np.zeros((128, 128, 3))
    for f in range(60):
        x0 = rs2.uniform(0.15, 0.85)
        curve = rs2.uniform(-0.25, 0.25)
        length = rs2.uniform(0.5, 1.0)
        v = cv.v
        xs = x0 + curve * v ** 2 + 0.03 * np.sin(v * 9 + f)
        m = (1 - S(0.004, 0.012, np.abs(cv.u - xs))) * (v < length)
        a = np.maximum(a, m)
        shade = rs2.uniform(0.7, 1.2)
        col = np.where(m[..., None] > 0.5, hex2rgb("#3B5B2E") * shade + v[..., None] * 0.08, col)
    cv.rgb = np.where(a[..., None] > 0, col, hex2rgb("#3B5B2E")[None, None]).astype(np.float32)
    cv.a = a.astype(np.float32)
    m = C.material("algae_tuft", rough=0.7, image=tex("algae_tuft", cv, alpha=True), image_alpha=True, double=True,
                   clip=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="GRAZE_*", species="hair_algae", scaleBy="uAlgaeBloom")
    body = g.obj("Cards", root, "card", [m], smooth=False, extras={"alphaTest": 0.4})
    # AlgaeClumpLeft: loose 7 x 4 cm green hair-algae ball on the sand at the left foot
    pos, _ = A["coral_algae_clump_left"]
    g2 = T.WGeo()
    for c in range(16):
        ang = math.pi * c / 8 + rs.uniform(0, 0.3)
        tilt = rs.uniform(-0.5, 0.5)
        dirn = unit(np.array([math.cos(ang) * 0.5, 1.0, math.sin(ang) * 0.3 + tilt]))
        side = unit(np.cross(dirn, [math.sin(ang), 0.1, -math.cos(ang)]))
        w, h = rs.uniform(3.0, 4.2), rs.uniform(2.4, 3.6)
        p0 = pos + np.array([rs.uniform(-1.8, 1.8), -0.2, rs.uniform(-1.0, 1.0)])
        V = [p0 - side * w / 2, p0 + side * w / 2, p0 + side * w / 2 + dirn * h, p0 - side * w / 2 + dirn * h]
        g2.add(np.array(V), [(0, 1, 2, 3)], np.array([(0, 0), (1, 0), (1, 1), (0, 1)]),
               np.array([(0, 0, 0, 1), (0, 0, 0, 1), (0, 0, 1, 1), (0, 0, 1, 1)]))
    cv2 = P.Canvas(128, 128, "#7FA23A", 0.0)
    cv2.rgb = np.where(a[..., None] > 0, col * (hex2rgb("#7FA23A") / hex2rgb("#3B5B2E"))[None, None],
                       hex2rgb("#7FA23A")[None, None]).astype(np.float32)
    cv2.rgb = np.clip(cv2.rgb, 0, 1)
    cv2.a = a.astype(np.float32)
    m2 = C.material("algae_clump", rough=0.75, image=tex("algae_clump", cv2, alpha=True), image_alpha=True,
                    double=True, clip=True)
    clump = g2.obj("ClumpLeft", root, "card", [m2], smooth=False,
                   extras={"alphaTest": 0.4, "anchor": "coral_algae_clump_left"})
    long_ = long_tufts(rock, root)
    glass = glass_strands(root)
    return root, [body, clump, long_, glass], {}


# backlog 2026-10-13 (ASSETS.md §4.1): long dark hair algae streaming from the upper ledges and hair strands on the
# left pane. Both are bent alpha strips (`card`), so the card shader sways them with the flow and grows them
# root -> tip with uAlgaeBloom (COLOR_0.b), like the GRAZE tufts.
FLOW_JETS = [(np.array(pos, float), unit(np.array(ex["dir"], float))) for name, _t, pos, ex, _s in
             __import__("rockscape").ANCHORS if name.startswith("FLOW_PH_")]


def flow_dir(p):
    """Horizontal downstream direction at p from the three powerhead jets (distance-weighted)."""
    f = np.array([0.95, 0.0, 0.3]) * 0.05
    for jp, jd in FLOW_JETS:
        f = f + np.array([jd[0], 0.0, jd[2]]) * math.exp(-np.linalg.norm(p - jp) / 35.0)
    return unit(f)


def strand_canvas(base, dark, tip, seed, w=128, h=256, n=90, width0=0.014):
    """Long filament strip texture: n sinuous filaments from the root (v 0) to random lengths, alpha-masked."""
    cv = P.Canvas(w, h, base, 0.0)
    rs = RS(seed)
    a = np.zeros((h, w), np.float32)
    col = np.zeros((h, w, 3), np.float32)
    v, u = cv.v, cv.u
    for f in range(n):
        x0 = rs.uniform(0.08, 0.92)
        length = rs.uniform(0.55, 1.0)
        amp, freq, ph = rs.uniform(0.01, 0.04), rs.uniform(5, 11), rs.uniform(0, 6.28)
        xs = x0 + (0.5 - x0) * 0.25 * v + amp * np.sin(v * freq + ph)
        width = width0 * rs.uniform(0.7, 1.3) * (1 - 0.5 * v / length)
        m = (1 - S(width, width + 0.008, np.abs(u - xs))) * (v < length) * (1 - S(length - 0.05, length, v))
        shade = rs.uniform(0.75, 1.15)
        c = (hex2rgb(base) * (1 - v[..., None]) + hex2rgb(tip) * v[..., None]) * shade
        c = np.where(rs.random() < 0.3, hex2rgb(dark)[None, None] * np.ones_like(c), c)
        col = np.where(m[..., None] > a[..., None], c, col)
        a = np.maximum(a, m)
    cv.rgb = np.clip(np.where(a[..., None] > 0, col, hex2rgb(base)[None, None]), 0, 1).astype(np.float32)
    cv.a = a
    return cv


def strip(g, pts, side, widths, col_rg, tip_b=1.0):
    """Bent strip along pts (world), widths per point; UV u across, v along; COLOR_0.b = 0 root -> tip_b."""
    n = len(pts)
    V, UV, COL = [], [], []
    for k, (p, w) in enumerate(zip(pts, widths)):
        t = k / (n - 1)
        V += [p - side * w / 2, p + side * w / 2]
        UV += [(0, t), (1, t)]
        COL += [(col_rg[0], col_rg[1], t * tip_b, 1)] * 2
    F = [(2 * k, 2 * k + 1, 2 * k + 3, 2 * k + 2) for k in range(n - 1)]
    g.add(np.array(V), F, np.array(UV, float), np.array(COL, float))


def long_tufts(rock, root, count=16):
    rs = RS(161)
    # keep clear of the upper-left GSP, the two red macro tufts and the Sally crab's crevice
    avoid = [(np.array(p, float), r) for p, r in (((20, 27.6, 18.2), 7.0), ((36.6, 28.9, 16.0), 5.0),
                                                  ((42.8, 33.5, 13.5), 5.0), ((101.5, 22.0, 11.5), 3.0))]
    spots = []
    for x in np.arange(4, 119, 3.0):
        for z in np.arange(2, C.TANK[2] - 1, 3.0):
            q = np.array([x + rs.uniform(-1, 1), C.WATER_Y + 5, z + rs.uniform(-1, 1)])
            hit, nn = rock.ray(q, np.array([0, -1.0, 0]), 60)
            if hit is None or nn[1] < 0.55 or not (23.5 <= hit[1] <= 33.0) or hit[2] < 11.7:
                continue
            if any(np.linalg.norm(hit - p) < r for p, r in avoid):
                continue
            spots.append((hit, unit(nn)))
    order = rs.permutation(len(spots))
    chosen = []
    for i in order:
        hit, nn = spots[i]
        if all(np.linalg.norm(hit - h) > 5.0 for h, _ in chosen):
            chosen.append((hit, nn))
        if len(chosen) >= count:
            break
    print(f"[coral] long hair tufts: {len(chosen)} on the upper ledges at "
          f"{[tuple(int(round(c)) for c in h) for h, _ in chosen]}")
    g = T.WGeo()
    for ti, (hit, nn) in enumerate(chosen):
        f = flow_dir(hit)
        phase = (ti * 0.37) % 1
        for c in range(4):
            d0 = unit(nn * 0.45 + f + rs.normal(0, 0.18, 3))
            L = rs.uniform(5.0, 10.0)
            # shorten where the strand would run into rock, out of the water or through the glass
            h2, _ = rock.ray(hit + nn * 0.3, d0, L)
            if h2 is not None:
                L = max(3.0, np.linalg.norm(h2 - hit) * 0.9)
            p0 = hit - nn * 0.1 + rs.normal(0, 0.3, 3)
            pts = [p0 + d0 * L * t + f * L * 0.3 * t * t for t in np.linspace(0, 1, 5)]
            for _ in range(8):
                if all(0.6 < p[0] < C.TANK[0] - 0.6 and p[1] < C.WATER_Y - 1.5 and 0.6 < p[2] < C.TANK[2] - 0.6 for p in pts):
                    break
                L *= 0.8
                pts = [p0 + d0 * L * t + f * L * 0.3 * t * t for t in np.linspace(0, 1, 5)]
            s1 = unit(np.cross(d0, nn) + 1e-6)
            s2 = np.cross(d0, s1)
            ang = math.pi * c / 4 + rs.uniform(-0.2, 0.2)
            side = unit(s1 * math.cos(ang) + s2 * math.sin(ang))
            W = rs.uniform(1.6, 2.6)
            strip(g, pts, side, W * np.linspace(1.0, 0.6, 5), (0.5, phase))
    cv = strand_canvas("#4E4C24", "#363A1C", "#7A7440", 162)
    m = C.material("algae_long", rough=0.75, image=tex("algae_long", cv, alpha=True), image_alpha=True, double=True,
                   clip=True)
    return g.obj("LongTufts", root, "card", [m], smooth=False,
                 extras={"alphaTest": 0.4, "species": "hair_algae", "flowLean": True})


def glass_strands(root, count=16):
    """Hair strands on the inside of the left pane, low and toward the back, lying almost flat on the glass."""
    rs = RS(171)
    g = T.WGeo()
    roots = P.poisson(rs, count, 2.2, box=(4.5, 15.0, 5.0, 20.0))  # (y, z)
    for k, (y, z) in enumerate(roots):
        d = unit(np.array([0.22, rs.uniform(0.4, 1.0), rs.uniform(-0.8, 0.8)]))
        L = rs.uniform(3.0, 6.0)
        p0 = np.array([0.12, y, z])
        drift = unit(np.array([0.0, rs.uniform(-0.3, 0.3), rs.uniform(-1, 1)]))
        pts = [p0 + d * L * t + drift * L * 0.2 * t * t for t in np.linspace(0, 1, 4)]
        side = unit(np.cross(d, [1.0, 0, 0]))
        strip(g, pts, side, rs.uniform(1.0, 1.6) * np.linspace(1.0, 0.7, 4), (0.5, (k * 0.29) % 1), tip_b=0.8)
    cv = strand_canvas("#4F7A2E", "#35521F", "#7FA23A", 172, w=64, h=128, n=36, width0=0.02)
    m = C.material("algae_glass", rough=0.6, image=tex("algae_glass", cv, alpha=True), image_alpha=True, double=True,
                   clip=True)
    return g.obj("GlassStrands", root, "card", [m], smooth=False,
                 extras={"alphaTest": 0.4, "species": "hair_algae", "pane": "left"})


# ----------------------------------------------------------------------------
# Aiptasia x3 (1.5 cm brown pest anemones)
# ----------------------------------------------------------------------------
def aiptasia(rock, A):
    import geomlib as G
    rs = RS(71)
    g = T.WGeo()
    names = ("coral_aiptasia_1", "coral_aiptasia_2", "coral_aiptasia_3")
    for k, nm in enumerate(names):
        pos, _ = A[nm]
        hit, nn, _ = rock.nearest(pos)
        nn = unit(nn + UP * 0.5)
        top = hit + nn * 1.0
        V, F, tv, av = G.tube(np.array([hit - nn * 0.1, hit + nn * 0.5, top]), [0.18, 0.22, 0.3], 6,
                              cap_start=True, cap_end=True, up=(1, 0, 0))
        g.add(V, F, np.c_[av * 0.5, tv * 0.5], (0.3, k / 3, 0, 1))
        t1 = unit(np.cross(nn, [0.3, 0.1, 0.9]))
        t2 = np.cross(nn, t1)
        for j in range(14):
            ang = 2 * math.pi * j / 14
            dirn = unit(nn * 0.55 + (t1 * math.cos(ang) + t2 * math.sin(ang)))
            base = top + dirn * 0.18
            tip = base + dirn * rs.uniform(0.55, 0.75) + nn * 0.1
            V, F = G.cone(base, tip, 0.05, 3)
            g.add(V, F, np.c_[np.full(len(V), 0.75), np.r_[np.zeros(3), 1.0][:len(V)] if len(V) == 4 else np.full(len(V), 0.8)],
                  (0.3, k / 3, 1, 1))
    cv = P.Canvas(32, 64)
    cv.vgrad("#5E4630", "#9C8466", 0, 1, 0.8)
    m = C.material("aiptasia", rough=0.6, image=tex("aiptasia", cv), alpha=0.9, blend=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_aiptasia_1", species="aiptasia", anchors=list(names))
    body = g.obj("Body", root, "body", [m], smooth=True)
    return root, [body], {}


BUILDERS = {
    "kenya": kenya,
    # Tee's photos 2026-10-13 [P p12/p13]: the upper-left GSP is a ~9 cm dome of long curling lime tentacles
    "gsp_neon": lambda r, A: gsp_dome(r, A, "coral_gsp_upper_left", (3.0, 3.9, 2.8), 165, 71, "gsp_neon"),
    # full-tank photo 2026-10-13: the lower-right colony is the same long-tentacle form, a broad low mound
    "gsp_mat": lambda r, A: gsp(r, A, "coral_gsp_lower_right", 9.0, (7.0, 8.5), 0.88, 235, "#59D64A", "#7DFF4F", 81,
                                "gsp_mat", mat_hex="#4A3038", raw=True, long=True, mound=2.2),
    "toadstool_leather": toadstool, "leather_pink": leather_pink, "finger_leather": finger_leather,
    "elephant_ear": elephant_ear,
    "zoa": zoa, "clove": clove, "mushroom": mushroom, "mushroom_hairy": mushroom_hairy, "macro_red": macro_red,
    "organ_pipe": organ_pipe,
    "algae_tufts": algae_tufts, "aiptasia": aiptasia,
}


def build(ids=None):
    """Builds the coral GLBs. Returns {id: (mesh_tris, {node: (instances, tris_per_instance)})}."""
    C.reset_scene()
    rock = Rock()
    A = anchors()
    out = {}
    for cid in ids or BUILDERS:
        for o in list(bpy.data.objects):
            bpy.data.objects.remove(o, do_unlink=True)
        res = BUILDERS[cid](rock, A)
        root, objs, inst = res[:3]
        variants = [(cid, root, objs, inst)]
        if len(res) > 3:  # optional LOD1 (root, objs, inst) -> <cid>_lod1.glb
            variants.append((cid + "_lod1",) + tuple(res[3]))
        for vi, (name, root_, objs_, inst_) in enumerate(variants):
            if vi == 1:  # free the base names for the LOD1 nodes (Blender suffixes duplicates with .001)
                for o in variants[0][2] + [variants[0][1]]:
                    o.name = o.name + "_lod0"
                    if o.data is not None:
                        o.data.name = o.name
                for o in objs_ + [root_]:
                    o.name = o.name.split(".")[0]
                    if o.data is not None:  # mesh datablocks too (Body.001 -> Body) so glTF mesh names match
                        o.data.name = o.name
                # GlbLoader / validate_glb.py expect the scene root to be exactly `Root` (never `Root.001`)
                assert root_.name == "Root", f"{name}: LOD1 root exported as {root_.name!r}"
            for o in objs_:
                C.triangulate(o)
            path = os.path.join(C.MODELS, name + ".glb")
            C.export_glb(path, C.all_descendants(root_))
            tris = sum(C.tri_count(o) for o in objs_)
            itri = sum(nc * nt for nc, nt in inst_.values())
            print(f"[coral] {name}.glb: {tris} mesh tris, instances {dict((k, v[0]) for k, v in inst_.items())} "
                  f"(+{itri} instanced tris), {os.path.getsize(path) / 1024:.0f} KB")
            out[name] = (tris, inst_)
    return out


if __name__ == "__main__":
    import sys
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    build(args or None)
