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
MAXD = 6  # depth 6 = the short lobe beside each tip (HQ: one more generation of thin branchlets than v1's 5)
KENYA_TUFTS = 1500       # LOD0 polyp tufts (10 tris each); the photo's crown is a fuzz of polyps on every branchlet
KENYA_TUFTS_LOD1 = 700
KENYA_LOD1_RATIO = 0.42  # body decimation for kenya_lod1.glb


def raw_anchor(name):
    import rockscape as R
    for nm, typ, pos, extra, snap in R.ANCHORS:
        if nm == name:
            return np.array(pos, float), unit(extra.get("normal", [0, 1, 0]))
    raise KeyError(name)


def kenya(rock, A):
    pos, n = A["coral_kenya_main"]
    rs = RS(11)
    branches = []  # (points, radii, depth, phase)

    def grow(p0, d, length, r0, depth, phase):
        """Fleshy Capnella branching: short thick trunk, 5 primaries fanning out, lobed tips."""
        pts, rad = [], []
        bend = unit(rs.normal(0, 1, 3)) * (0.3 if depth < 4 else 0.5)
        k = 4 if depth <= 2 else (3 if depth < MAXD - 1 else 2)
        p = np.array(p0, float)
        dd = unit(d)
        for i in range(k + 1):
            t = i / k
            pts.append(p.copy())
            # fleshy: little taper on the thick limbs, thin branchlets taper to a swollen polyp-covered club
            club = 1.0 + (0.25 * math.sin(math.pi * t) if depth >= MAXD - 1 else 0.0)
            taper = 0.22 if depth < 4 else 0.4
            rad.append(r0 * (1 - taper * t) * club)
            dd = unit(dd + bend / k + UP * 0.08 / k)
            p = p + dd * length / k
        branches.append((np.array(pts), np.array(rad), depth, phase))
        if depth >= MAXD:
            return
        nchild = {0: 5, 1: 3, 2: 3, 3: 3, 4: 2}.get(depth, 1)
        if depth == MAXD - 1:
            nchild = 1 if rs.uniform() < 0.5 else 0  # a short lobe beside half of the tips
        for c in range(nchild):
            ang = (2 * math.pi * c / nchild) + rs.uniform(-0.35, 0.35)
            if depth == 0:
                spread = math.radians(rs.uniform(38, 62))
            else:
                spread = math.radians(rs.uniform(25, 45))
            t1 = unit(np.cross(dd, UP if abs(dd[1]) < 0.95 else np.array([1.0, 0, 0])))
            t2 = np.cross(dd, t1)
            cd = unit(dd * math.cos(spread) + (t1 * math.cos(ang) + t2 * math.sin(ang)) * math.sin(spread) + UP * 0.25)
            j = len(pts) - 1 if c == 0 or depth < 1 else rs.integers(len(pts) // 2, len(pts))
            lf = {0: 1.5, MAXD - 1: 0.35}.get(depth, rs.uniform(0.62, 0.78))
            grow(pts[j], cd, length * lf, rad[j] * (0.62 if depth == 0 else (0.82 if depth < 3 else 0.7)), depth + 1,
                 phase if depth > 0 else c / 5.0)

    trunk_dir = unit(0.85 * UP + 0.15 * n)
    grow(pos - n * 0.8, trunk_dir, 4.0, 2.0, 0, 0.0)
    allp = np.vstack([b[0] for b in branches])
    lo, hi = allp.min(0), allp.max(0)
    sy = (48.0 - pos[1]) / (hi[1] - pos[1])
    crown = allp[allp[:, 1] > pos[1] + 0.35 * (hi[1] - pos[1])]
    clo, chi = crown.min(0), crown.max(0)
    sx, sz = 26.0 / (chi[0] - clo[0]), 20.0 / (chi[2] - clo[2])
    cx, cz = (clo[0] + chi[0]) / 2, (clo[2] + chi[2]) / 2
    rsc = float(np.clip(((sx + sz) / 2) ** 0.6, 0.8, 1.6))
    for k, (pts, rad, depth, phase) in enumerate(branches):
        h = np.clip((pts[:, 1] - pos[1]) / (hi[1] - pos[1]), 0, 1)
        w = S(0.12, 0.45, h)
        q = pts.copy()
        q[:, 1] = pos[1] + (pts[:, 1] - pos[1]) * sy
        q[:, 0] = pts[:, 0] + w * ((73.0 + (pts[:, 0] - cx) * sx) - pts[:, 0])
        q[:, 2] = pts[:, 2] + w * ((17.0 + (pts[:, 2] - cz) * sz) - pts[:, 2])
        branches[k] = (q, rad * (1 + (rsc - 1) * (1 - 0.5 * h)), depth, phase)
    allp = np.vstack([b[0] for b in branches])
    print("[coral] kenya bbox", np.round(allp.min(0), 1), np.round(allp.max(0), 1))
    g = T.WGeo()
    inst = Instances(12)
    import geomlib as G
    sides = {0: 10, 1: 8, 2: 7, 3: 6, 4: 4, 5: 3, 6: 3}
    ymin = pos[1]
    ymax = max(b[0][:, 1].max() for b in branches)
    print(f"[coral] kenya branches {len(branches)} (per depth {[sum(1 for b in branches if b[2] == d) for d in range(MAXD + 1)]})")
    for pts, rad, depth, phase in branches:
        V, F, tv, av = G.tube(pts, rad, sides[depth], cap_start=depth == 0, cap_end=depth >= MAXD - 1, up=(0, 0, 1))
        hgt = np.clip((V[:, 1] - ymin) / (ymax - ymin), 0, 1)
        thin = np.full(len(V), 1 - np.interp(rad.mean(), [0.2, 2.0], [0.1, 0.9]))  # thin branches: translucent
        col = np.c_[thin, np.full(len(V), phase), np.clip((depth + tv) / (MAXD + 1.0), 0, 1) ** 1.3, np.ones(len(V))]
        g.add(V, F, np.c_[av, hgt], col)
        # polyp tufts, denser toward the outer branchlets (every tip carries several)
        if depth >= 1:
            m = {1: 1, 2: 2, 3: 3, 4: 4, 5: 3, 6: 2}[depth]
            for _ in range(m):
                t = rs.uniform(0.3, 1.0) ** 0.5
                i = min(int(t * (len(pts) - 1)), len(pts) - 2)
                f = t * (len(pts) - 1) - i
                c = pts[i] * (1 - f) + pts[i + 1] * f
                r = rad[i] * (1 - f) + rad[i + 1] * f
                tng = unit(pts[i + 1] - pts[i])
                o = unit(np.cross(tng, rs.normal(0, 1, 3)))
                if np.dot(o, UP) < -0.3:
                    o = -o
                inst.add(c + o * r * 0.7, o, rs.uniform(1.0, 1.5), phase=(phase + 0.1 * depth) % 1.0)
    print(f"[coral] kenya tufts generated {len(inst)}")
    if len(inst) > KENYA_TUFTS:
        keep = np.sort(RS(5).choice(len(inst), KENYA_TUFTS, replace=False))
        inst.rows = [inst.rows[i] for i in keep]
    inst1 = Instances(12)
    inst1.rows = [inst.rows[i] for i in np.sort(RS(6).choice(len(inst), min(KENYA_TUFTS_LOD1, len(inst)), replace=False))]
    cv = P.Canvas(64, 256)
    cv.vgrad("#C9A08E", "#E4C2BA", 0.0, 1.0, 0.8)  # tan-pink stalk -> branches #D8A9A0
    cv.blend(S(0.2, 0.6, cv.v) * (1 - S(0.75, 1.0, cv.v)), "#D8A9A0", 0.7)
    spk = P.fbm(256, 64, 8, 3, 7)
    cv.multiply(0.9 + 0.2 * spk)
    body_m = C.material("kenya_body", rough=0.75, image=tex("kenya_body", cv))
    pc = P.Canvas(64, 64, "#E9C2BC")
    rr = np.hypot(pc.u - 0.5, pc.v - 0.5)
    pc.blend(1 - S(0.05, 0.2, rr), "#C99A90")
    pc.blend(S(0.38, 0.5, rr), "#EBD3CC")
    polyp_m = C.material("kenya_polyp", rough=0.6, image=tex("kenya_polyp", pc))
    # tuft prototype: a short stalk quad + 8 splayed single-triangle tentacles (10 tris); seen in the mass they
    # read as the fuzzy polyp coat of tank_right.jpg rather than the flat 8-point stars of v1 (0.85 cm tentacles)
    proto = proto_feather(8, 0.25, 0.1, 0.85, 66, 0.1, 1)
    ntri = len(proto[1]) + sum(1 for f in proto[1] if len(f) == 4)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_kenya_main", species="kenya_tree", swayGroups=3)
    body = g.obj("Body", root, "body", [body_m], smooth=True)
    pol = polyp_node("Tufts", root, proto, [polyp_m], inst)
    # LOD1 (Medium / far): decimated body, fewer tufts, same materials
    root1 = C.empty("Root")
    C.set_extras(root1, anchor="coral_kenya_main", species="kenya_tree", swayGroups=3, lod=1)
    body1 = g.obj("Body", root1, "body", [body_m], smooth=True)
    C.decimate(body1, KENYA_LOD1_RATIO)
    pol1 = polyp_node("Tufts", root1, proto, [polyp_m], inst1)
    lod1 = (root1, [body1, pol1], {"Tufts": (len(inst1), ntri)})
    return root, [body, pol], {"Tufts": (len(inst), ntri)}, lod1


# ----------------------------------------------------------------------------
# Green star polyps (G1 neon clump, G2 mat)
# ----------------------------------------------------------------------------
def gsp(rock, A, anchor, R, half, spacing, count, polyp_hex, tip_hex, seed, name, mat_hex="#5A3F6B", raw=False,
        tent=0.6):
    pos, n = raw_anchor(anchor) if raw else A[anchor]
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
        V.append(p + nn * 0.12)
        e = edge.ravel()[k] / (warp.ravel()[k] ** 2)
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
        inst.add(p + nn * 0.08, nn, rs.uniform(0.8, 1.2), phase=ph)
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
    # longer, slightly wider tentacles than v1 (0.42): the neon clump reads as a shaggy pom-pom in tank_left.jpg
    proto = proto_feather(8, 0.18, 0.06, tent, 60, 0.05, 1)
    pol = polyp_node("Polyps", root, proto, [pol_m], inst)
    return root, [body, pol], {"Polyps": (len(inst), len(proto[1]) + sum(1 for f in proto[1] if len(f) == 4))}


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

    def ptex(name, column, rim, disc, center):
        cv = P.Canvas(64, 64, column)
        cv.blend((cv.u < 0.5) * S(0.5, 0.7, cv.v), rim)
        rr = np.hypot((cv.u - 0.75) / 0.25, (cv.v - 0.5) / 0.5)
        cv.blend((cv.u >= 0.5) * 1.0, disc)
        cv.blend((cv.u >= 0.5) * (1 - S(0.3, 0.5, rr)), center)
        cv.blend((cv.u >= 0.5) * S(0.85, 1.0, rr), rim)
        return C.material(name, rough=0.55, image=tex(name, cv), emissive=center, emissive_strength=0.0)

    rock_cv = P.Canvas(64, 64, "#A89A80")
    rock_cv.multiply(0.8 + 0.35 * P.fbm(64, 64, 6, 3, 9))
    rm = C.material("zoa_rubble", rough=0.9, image=tex("zoa_rubble", rock_cv))
    mA = ptex("zoa_polyp_orange", "#7A5A3A", "#E0792A", "#E0792A", "#5FBF5A")
    mB = ptex("zoa_polyp_brown", "#5A4030", "#6B4A2E", "#6B4A2E", "#E8DDC0")
    mC = ptex("paly_polyp_brown", "#5A4030", "#7A5A36", "#7A5A36", "#D8C8A0")
    # paly colony (C4b): 20 brown polyps at R2's right foot + a 10-polyp orange patch at (24, 5, 25)
    instC, instD = Instances(24), Instances(25)
    for cen, cnt, inst_, rad in ((A["coral_paly_left_foot"][0], 20, instC, 2.2), (np.array([24.0, 5.0, 25.0]), 10, instD, 1.4)):
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
    b = polyp_node("PolypsBrown", root, pr, [mB], instB)
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
        for z in np.arange(2, 32, 4.0):
            q = np.array([x + rs.uniform(-1.5, 1.5), 60.0, z + rs.uniform(-1.5, 1.5)])
            hit, nn = rock.ray(q, np.array([0, -1.0, 0]), 60)
            if hit is None or nn[1] < 0.6 or hit[1] < 9 or hit[1] > 55:
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
    return root, [body, clump], {}


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
    "gsp_neon": lambda r, A: gsp(r, A, "coral_gsp_upper_left", 6.0, (5.0, 4.0), 0.24, 1200, "#C4FF3C", "#C4FF3C", 71,
                                 "gsp_neon", tent=0.65),
    "gsp_mat": lambda r, A: gsp(r, A, "coral_gsp_lower_right", 9.0, (8.0, 9.5), 0.28, 2200, "#59D64A", "#7DFF4F", 81,
                                "gsp_mat", mat_hex="#4E3860", raw=True, tent=0.55),
    "zoa": zoa, "clove": clove, "mushroom": mushroom, "macro_red": macro_red, "organ_pipe": organ_pipe,
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
