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

Look pass (docs/research/11_look_review.md §6): the committed GLBs carry baseColorFactor tints, patched into the
material JSON after export (the textures are unchanged). Re-apply them after a regen, or bake them into the canvases:
  kenya / kenya_lod1  kenya_body, kenya_polyp  (1.0, 0.8, 0.86)   pink-mauve, not bleached lavender
  gsp_mat             gsp_mat_polyp            (1.0, 1.0, 0.7)    olive, not blue-grey
  macro_red           macro_red                (0.62, 0.88, 0.95) organ_pipe organpipe_skeleton too
  leather_pink        leather_pink             (0.7, 1.0, 0.65)
  toadstool_leather   toadstool_cap, toadstool_polyp (0.62, 0.6, 1.0)
  zoa                 zoa_polyp_orange, zoa_polyp_brown, paly_polyp_brown (0.62, 0.66, 1.0)
(details.py: reef_details mushroom_red_small (0.4, 0.7, 0.85), encrust_yellow (0.38, 0.33, 1.0) and no glow,
 coralline_crust (0.85, 1.0, 0.8).)
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


def tex(name, cv, alpha=False, jpeg=False):
    """Bake a canvas to an image the GLB embeds. jpeg=True (opaque, noisy textures such as pore fields) stores it as
    JPEG q 88, a third to a fifth of the PNG bytes (the APK assets are near their 45 MB cap)."""
    if jpeg and not alpha:
        from PIL import Image
        path = os.path.join(C.BUILD, name + ".jpg")
        os.makedirs(C.BUILD, exist_ok=True)
        Image.fromarray((np.clip(cv.rgb, 0, 1) * 255 + 0.5).astype(np.uint8)).save(path, quality=88)
        return C.load_image(path, name=name)
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
KENYA_SPRIGS_LOD1 = 650    # 2026-10-15: 1100 -> 650 keeps the Medium estimate under 142k with the remade leathers and the mushroom mound
KENYA_LOD1_TRIS = 4700    # kenya_lod1.glb body: the branches without twigs, decimated to about this
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
GSP_TENT_SEGS = 3       # ribbon segments per strand (2026-10-15: 4 -> 3 pays for the remade leathers)


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
    mat_cv = P.Canvas(128, 128, base_hex)
    mat_cv.multiply(0.75 + 0.45 * P.fbm(128, 128, 5, 4, 17))
    mat_cv.blend(S(0.6, 0.8, P.fbm(128, 128, 9, 2, 18)), "#6A4A3A", 0.5)
    d, cr = PoreLattice(0.5, 0.045, 19, jitter=0.3).field(mat_cv.u - 0.5, mat_cv.v - 0.5)
    mat_cv.blend((1 - S(0.15, 0.3, d)) * (cr > 0.3), "#2A1A1C", 0.6)          # retracted polyp holes in the mat
    mat_cv.blend((1 - S(0.0, 0.12, np.abs(d - 0.32))) * (cr > 0.3), "#6E8A3A", 0.35)  # green rims of the holes
    mat_m = C.material(name + "_mat", rough=0.8, image=tex(name + "_mat", mat_cv, jpeg=True))
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
                       emissive_strength=0.06, double=True)   # 0.3 read fluorescent lime; Tee's GSP is olive (research 11 §6)
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
        sc = rs.uniform(0.8, 1.2) * (0.75 + 0.25 * S(th_max, 0.4, th))
        ph_sway = (np.dot(p, flow) / 25.0 + rs.normal(0, 0.03)) % 1.0
        # flank polyps lean out and slightly down so their tentacles drape over the bare base (2026-10-15)
        inst.add(p + nn * 0.05, unit(nn + axis * (0.25 - 0.3 * S(0.9, th_max, th))), sc, phase=ph_sway)
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


def grid_faces(rows, segs, centre=True):
    """Faces of a polar grid: optional centre vertex 0, then `rows` rings of `segs` vertices (ring i at
    centre + i * segs), wrapping round."""
    o = 1 if centre else 0
    F = [(0, o + (k + 1) % segs, o + k) for k in range(segs)] if centre else []
    for i in range(rows - 1):
        b0, b1 = o + i * segs, o + (i + 1) * segs
        F += [(b0 + k, b0 + (k + 1) % segs, b1 + (k + 1) % segs, b1 + k) for k in range(segs)]
    return F


def orient_out(obj, inside_pt):
    """Recalculate normals, then flip the whole mesh if most faces point toward inside_pt (world cm): open surfaces
    (a plate on a stalk, a disc) can come out of recalc inside-out."""
    import bmesh
    C.recalc_normals(obj)
    me = obj.data
    c = np.array(C.w2b(inside_pt))
    s = sum(float(np.dot(np.array(p.normal), np.array(p.center) - c)) * p.area for p in me.polygons)
    if s < 0:
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.reverse_faces(bm, faces=bm.faces)
        bm.to_mesh(me)
        bm.free()
        me.update()


class PoreLattice:
    """Polyp pores on a jittered hex lattice over the square [-half, half]^2 (cm, planar texture space)."""

    def __init__(self, half, spacing, seed, jitter=0.18):
        self.half, self.sx, self.sy, self.jit = half, spacing, spacing * 0.866, jitter
        self.nr, self.nc = int(2 * half / self.sy) + 3, int(2 * half / spacing) + 3
        self.j = RS(seed).normal(0, 1, (self.nr, self.nc, 3)).astype(np.float32)

    def centre(self, row, col):
        j = self.j[np.clip(row + 1, 0, self.nr - 1), np.clip(col + 1, 0, self.nc - 1)]
        off = (row % 2) * 0.5
        return ((col + off + 0.5 + j[..., 0] * self.jit) * self.sx - self.half,
                (row + 0.5 + j[..., 1] * self.jit) * self.sy - self.half, 0.5 + 0.5 * np.tanh(j[..., 2]))

    def centres(self):
        """All pore centres: (N, 3) array of x, z, rand."""
        rr, cc = np.mgrid[-1:self.nr - 1, -1:self.nc - 1]
        x, z, r = self.centre(rr, cc)
        return np.c_[x.ravel(), z.ravel(), r.ravel()]

    def field(self, X, Z):
        """Distance to the nearest pore centre (in units of the spacing) and that pore's rand, at points X, Z."""
        best, rnd = np.full(X.shape, 9.0, np.float32), np.zeros(X.shape, np.float32)
        row0 = np.floor((Z + self.half) / self.sy).astype(int)
        for dr in (-1, 0, 1):
            row = row0 + dr
            col0 = np.floor((X + self.half) / self.sx - (row % 2) * 0.5).astype(int)
            for dc in (-1, 0, 1):
                cx, cz, r = self.centre(row, col0 + dc)
                d = np.hypot(X - cx, Z - cz) / self.sx
                closer = d < best
                best = np.where(closer, d, best)
                rnd = np.where(closer, r, rnd)
        return best, rnd


def profile_shell(profile, segs, at, base, q):
    """Closed-top surface of revolution with per-angle shaping: profile [(r, y)] from the top centre (r 0) outward
    and round to the underside; at(i, r, y, a) -> (r, y) reshapes each point. Returns V (world), local points,
    UV radius per vertex (arc length from the top centre, so a planar texture keeps its scale over the rim)."""
    arc = np.r_[0, np.cumsum([math.hypot(profile[i + 1][0] - profile[i][0], profile[i + 1][1] - profile[i][1])
                              for i in range(len(profile) - 1)])]
    L, S_ = [], []
    r0, y0 = at(0, profile[0][0], profile[0][1], 0.0)
    L.append(np.array([0.0, y0, 0.0]))
    S_.append((0.0, 0.0))
    for i in range(1, len(profile)):
        for k in range(segs):
            a = 2 * math.pi * k / segs
            r, y = at(i, profile[i][0], profile[i][1], a)
            L.append(np.array([r * math.cos(a), y, r * math.sin(a)]))
            S_.append((arc[i], a))
    V = np.array([q_rotate(q, p) for p in L]) + base
    return V, np.array(L), np.array(S_), grid_faces(len(profile) - 1, segs)


# Toadstool v2 (2026-10-15 remake; [P p04] close crop): a smooth, pale grey-cream, slightly translucent stalk ~5 cm
# tall that flares into a broad bell at the foot, and a thick golden-tan cap ~6.8 cm across with a lumpy top, a
# gently folded rolled margin and a brown underside, its top carpeted in short golden polyps with pale tips.
TOAD_POLYPS = 240
TOAD_SMAX = 5.6     # cm arc length from the cap centre to the end of the underside (planar cap texture half-width)


def toadstool(rock, A):
    """Sarcophyton toadstool leather [P p04] on the low front-left top of the R5 mound."""
    import geomlib as G
    hit, hn = new_anchor(rock, "coral_toadstool_front")
    rs = RS(151)
    axis = unit(UP * 0.9 + hn * 0.15 + np.array([0.1, 0, 0.05]))
    g = T.WGeo()
    H = 4.6
    side = unit(np.cross(axis, [0, 0, 1.0]))
    ys = np.linspace(-0.5, H, 9)
    pts = np.array([hit + axis * y + side * 0.25 * math.sin(math.pi * y / H) for y in ys])  # a slight S-bend
    rad = [2.7, 2.15, 1.7, 1.42, 1.3, 1.28, 1.36, 1.55, 1.8]                               # bell foot, waisted
    Vs, Fs, tv, av = G.tube(pts, rad, 14, cap_start=False, cap_end=False, up=(0, 0, 1))
    Vs = Vs + rs.normal(0, 0.025, Vs.shape)
    g.add(Vs, Fs, np.c_[av, tv], np.c_[np.full(len(Vs), 0.6), np.zeros(len(Vs)), tv * 0.25, np.ones(len(Vs))],
          mat=0)
    top = hit + axis * (H - 0.25)
    q = q_from_up(axis, rs.uniform(0, 6.28))
    ph = rs.uniform(0, 6.28, 6)
    prof = [(0.0, 1.62), (0.7, 1.6), (1.4, 1.56), (2.05, 1.5), (2.6, 1.4), (3.0, 1.22), (3.3, 0.98), (3.42, 0.7),
            (3.3, 0.42), (2.95, 0.2), (2.4, 0.02), (1.7, -0.1)]
    RIM = 7  # profile row of the outer rim

    def at(i, r, y, a):
        wob = 1 + 0.07 * math.sin(5 * a + ph[0]) + 0.035 * math.sin(9 * a + ph[1])
        w = math.exp(-((i - RIM) / 2.2) ** 2)                                  # folds strongest at the margin
        fold = 0.32 * w * math.sin(6 * a + ph[2]) + 0.12 * w * math.sin(11 * a + ph[3])
        if i <= 5:   # lumpy top
            x, z = r * math.cos(a), r * math.sin(a)
            y += 0.1 * math.sin(1.7 * x + ph[4]) * math.sin(1.9 * z + ph[5]) * (r / 3.0)
        return r * wob, y + fold
    segs = 32
    Vc, Lc, Sc, Fc = profile_shell(prof, segs, at, top, q)
    UVc = np.c_[0.5 + 0.5 * Sc[:, 0] / TOAD_SMAX * np.cos(Sc[:, 1]), 0.5 + 0.5 * Sc[:, 0] / TOAD_SMAX * np.sin(Sc[:, 1])]
    rows = np.r_[0, np.repeat(np.arange(1, len(prof)), segs)]
    colc = np.c_[np.where(rows >= RIM, 0.45, 0.3), np.zeros(len(Vc)), 0.15 + 0.25 * np.clip(rows / RIM, 0, 1),
                 np.ones(len(Vc))]
    g.add(Vc, Fc, UVc, colc, mat=1)
    arc_rim = float(np.r_[0, np.cumsum([math.hypot(prof[i + 1][0] - prof[i][0], prof[i + 1][1] - prof[i][1])
                                         for i in range(len(prof) - 1)])][RIM])
    # cap polyps: area-uniform over the lumpy top (profile rows 0..5), normals from the local surface
    inst = Instances(153)
    top_rows = np.where((rows >= 1) & (rows <= 6))[0]
    tri_top = [f for f in Fc if all(rows[i] <= 6 for i in f)]
    areas = np.array([np.linalg.norm(np.cross(Vc[f[1]] - Vc[f[0]], Vc[f[2]] - Vc[f[0]])) for f in tri_top])
    for fi in rs.choice(len(tri_top), TOAD_POLYPS, p=areas / areas.sum()):
        f = tri_top[fi]
        w = rs.dirichlet((1, 1, 1))
        p = sum(w[j] * Vc[f[j]] for j in range(3))
        nn = unit(np.cross(Vc[f[1]] - Vc[f[0]], Vc[f[2]] - Vc[f[0]]))
        if np.dot(nn, axis) < 0:
            nn = -nn
        inst.add(p + nn * 0.02, unit(nn + axis * 0.3), rs.uniform(0.8, 1.2), phase=rs.uniform())
    del top_rows
    # stalk: pale grey-cream, faint lengthwise striations, a creamier foot and a browner collar under the cap
    st = P.Canvas(64, 128, "#B6AEA4")
    st.blend(S(0.55, 0.75, P.fbm(128, 64, 6, 3, 154)), "#A29CAA", 0.5)
    st.multiply(0.94 + 0.08 * np.sin(st.u * 2 * math.pi * 9 + 3 * P.fbm(128, 64, 3, 2, 158)))
    st.blend(1 - S(0.0, 0.18, st.v), "#D8CFC0", 0.6)
    st.blend(S(0.82, 1.0, st.v), "#A89878", 0.55)
    m_stalk = C.material("toadstool_stalk", rough=0.5, image=tex("toadstool_stalk", st, jpeg=True))
    # cap: golden polyp carpet on top (dense pale-tipped dots), olive-brown rolled margin, grey-brown underside
    n = 256
    cc = P.Canvas(n, n, "#A88A30")
    X, Z = (cc.u - 0.5) * 2 * TOAD_SMAX, (cc.v - 0.5) * 2 * TOAD_SMAX
    sr = np.hypot(X, Z)
    cc.multiply(0.78 + 0.4 * P.fbm(n, n, 14, 3, 156))
    d, cr = PoreLattice(TOAD_SMAX, 0.22, 157, jitter=0.25).field(X, Z)
    cc.blend((1 - S(0.25, 0.42, d)) * (1 - S(arc_rim - 0.6, arc_rim, sr)), "#D9B848", 0.7)   # polyp bases
    cc.blend((1 - S(0.06, 0.16, d)) * (1 - S(arc_rim - 0.6, arc_rim, sr)) * (cr > 0.4), "#F0E6B8", 0.8)
    cc.blend(S(arc_rim - 0.7, arc_rim + 0.1, sr), "#7A6436", 0.85)          # margin
    cc.blend(S(arc_rim + 0.5, arc_rim + 1.2, sr), "#8C7E6A", 0.9)           # underside
    cc.blend(S(arc_rim + 1.6, TOAD_SMAX, sr), "#B4A894", 0.7)               # toward the stalk collar
    m_cap = C.material("toadstool_cap", rough=0.65, image=tex("toadstool_cap", cc, jpeg=True))
    pc = P.Canvas(32, 32, "#B08A2A")
    t = np.clip((pc.u + pc.v) / 2, 0, 1)
    pc.rgb = (hex2rgb("#7A5E22")[None, None] * (1 - S(0.15, 0.4, t))[..., None]
              + hex2rgb("#C9A23A")[None, None] * (S(0.15, 0.4, t) * (1 - S(0.86, 0.97, t)))[..., None]
              + hex2rgb("#EADFA8")[None, None] * S(0.86, 0.97, t)[..., None]).astype(np.float32)
    m_pol = C.material("toadstool_polyp", rough=0.6, image=tex("toadstool_polyp", pc), double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_toadstool_front", species="sarcophyton_sp")
    body = g.obj("Body", root, "body", [m_stalk, m_cap], smooth=True)
    orient_out(body, top + axis * 0.6)
    proto = proto_feather(7, 0.16, 0.06, 0.24, 64, 0.06, 1)   # short, broad golden tentacles: a fuzzy carpet
    pol = polyp_node("Polyps", root, proto, [m_pol], inst)
    print(f"[coral] toadstool: {g.tris()} body tris, {len(inst)} polyps")
    return root, [body, pol], {"Polyps": (len(inst), sum(len(f) - 2 for f in proto[1]))}


# Pink lobed leather v2 (2026-10-15 remake; [P p04] close crop): not a smooth dome but a thick, lobed plate ~9 x 6.5 cm
# held up on a short tan stalk. The top is gently domed and folded into broad meandering ridges (pale lavender-white
# furrows between them), packed with salmon-pink polyp pores that run over the rounded, rolled rim; the underside and
# stalk are smooth cream-tan. The pores are painted (planar UV; the rim is unrolled so they keep their size) and the
# low polyp nubs are instanced so the surface reads textured and opens / closes with the colony.
PINK_R = (4.4, 3.2)        # cm outline half-axes before lobing (old dome: 4.4 x 3.1)
PINK_H = 2.5               # cm top-centre height above the attachment
PINK_RIM = 0.55            # cm radius of the rolled rim
PINK_UVR = 6.6             # cm half-width of the planar pore texture (unrolled rim included)
PINK_POLYPS = 260


def pink_ridge(x, z, ph):
    """Lobe field: 0 along the narrow meandering furrows (zero lines of a warped two-wave pattern, lobes ~1.3 cm
    across) .. 1 on the lobe tops (scalars or arrays)."""
    wx = x + 0.55 * np.sin(0.9 * z + ph[0]) + 0.25 * np.sin(2.1 * z + ph[1])
    wz = z + 0.55 * np.sin(0.8 * x + ph[2]) + 0.25 * np.sin(1.9 * x + ph[3])
    n = np.sin(2.35 * (0.8 * wx + 0.6 * wz) + ph[4]) + 0.8 * np.sin(2.35 * (-0.45 * wx + 0.9 * wz) + ph[5])
    return np.minimum(1.0, np.abs(n) / 0.75) ** 0.7


def leather_pink(rock, A):
    """[P p04] pink-lavender lobed leather (Lobophytum-like) at the toadstool's left foot."""
    hit, hn = new_anchor(rock, "coral_leather_pink_front")
    rs = RS(161)
    axis = unit(UP * 0.75 + hn * 0.45)
    q = q_from_up(axis, rs.uniform(0, 6.28))
    ph = rs.uniform(0, 6.28, 9)
    rx, rz = PINK_R
    RIM = PINK_RIM

    def outline(a):  # lobed outline radius (rim outer edge)
        lobe = 1 + 0.09 * math.sin(3 * a + ph[6]) + 0.06 * math.sin(5 * a + ph[7]) + 0.03 * math.sin(8 * a + ph[8])
        return lobe / math.sqrt((math.cos(a) / rx) ** 2 + (math.sin(a) / rz) ** 2)

    def top_y(r, a, rt):  # top height at radius r along angle a (rt = top radius there)
        t = min(r / rt, 1.0)
        rid = float(pink_ridge(r * math.cos(a), r * math.sin(a), ph))
        fold = 0.32 * t ** 2 * math.sin(2 * a + ph[0]) + 0.12 * t ** 3 * math.sin(5 * a + ph[1])  # wavy plate
        relief = (0.12 * rid - 0.3 * (1 - rid) ** 2) * (1 - S(0.82, 1.0, t))  # rounded lobes, narrow furrows
        return PINK_H - 0.45 * t ** 2 + fold + relief

    def surf(ur, a):
        """Top + rolled rim + underside in the local frame, by unrolled radius ur (= planar texture radius on the
        top and the rim): 0 .. rt top, rt .. rt + RIM*pi rim, beyond that the underside fraction to the foot."""
        rt = outline(a) - RIM
        if ur <= rt:
            return np.array([ur * math.cos(a), top_y(ur, a, rt), ur * math.sin(a)])
        ye = top_y(rt, a, rt)
        s = min((ur - rt) / RIM, math.pi)
        r, y = rt + RIM * math.sin(s), ye - RIM + RIM * math.cos(s)
        return np.array([r * math.cos(a), y, r * math.sin(a)])

    segs = 40
    ts = [0.0, 0.11, 0.22, 0.33, 0.44, 0.55, 0.65, 0.74, 0.82, 0.89, 0.95, 1.0]
    psis = [45, 90, 135, 180]                                # rolled rim (deg round the rim, 0 = top edge)
    under = [(0.74, 0.4), (0.5, 0.12), (0.42, -0.1), (0.47, -0.6)]  # (r fraction, y fraction or cm) to the foot
    V, UV, COL, MAT = [], [], [], []

    def put(p, ur, a, mat, sway):
        V.append(q_rotate(q, p) + hit)
        UV.append((0.5 + 0.5 * ur / PINK_UVR * math.cos(a), 0.5 + 0.5 * ur / PINK_UVR * math.sin(a)))
        COL.append((0.35 if mat == 0 else 0.25, 0.0, sway, 1.0))
        MAT.append(mat)
    put(surf(0.0, 0.0), 0.0, 0.0, 0, 0.1)
    for t in ts[1:]:
        for k in range(segs):
            a = 2 * math.pi * k / segs
            rt = outline(a) - RIM
            put(surf(rt * t, a), rt * t, a, 0, 0.1 + 0.2 * t)
    for psi in psis:
        for k in range(segs):
            a = 2 * math.pi * k / segs
            ur = outline(a) - RIM + RIM * math.radians(psi)
            put(surf(ur, a), ur, a, 0 if psi <= 90 else 1, 0.3)
    for rf, yf in under:
        for k in range(segs):
            a = 2 * math.pi * k / segs
            rt = outline(a) - RIM
            y = yf * (top_y(rt, a, rt) - 2 * RIM) if yf >= 0 else yf
            put(np.array([rt * rf * math.cos(a), y, rt * rf * math.sin(a)]), rt * rf, a, 1, 0.0)
    nrings = len(ts) - 1 + len(psis) + len(under)
    g = T.WGeo()
    g.V, g.UV, g.COL = list(V), list(UV), list(COL)
    for f in grid_faces(nrings, segs):  # the smooth underside / stalk material where all corners are underside
        g.F.append(f)
        g.FM.append(1 if all(MAT[i] == 1 for i in f) else 0)
    # pores (planar texture space, cm): the texture paints every one; the ones on the crests carry a polyp nub
    lat = PoreLattice(PINK_UVR, 0.3, 163, jitter=0.1)
    inst = Instances(164)
    e = 2e-3
    cand = []
    for x, z, r in lat.centres():
        ur, a = math.hypot(x, z), math.atan2(z, x)
        rt = outline(a) - RIM
        if ur > rt * 0.97:
            continue
        rid = float(pink_ridge(x, z, ph)) if ur <= rt else 0.6
        if rid < 0.45 or r < 0.2:
            continue
        cand.append((ur, a, r))
    for i in np.sort(rs.choice(len(cand), min(PINK_POLYPS, len(cand)), replace=False)):
        ur, a, r = cand[i]
        p = surf(ur, a)
        du, da = surf(ur + e, a) - surf(max(ur - e, 0.0), a), surf(ur, a + e) - surf(ur, a - e)
        nl = unit(np.cross(da, du)) if ur > 0.05 else UP
        if np.dot(nl, p - np.array([0, 1.0, 0])) < 0:
            nl = -nl
        inst.add(q_rotate(q, p + nl * 0.01) + hit, unit(q_rotate(q, nl)), 0.8 + 0.4 * r,
                 phase=(0.05 * ur + rs.normal(0, 0.05)) % 1)
    # top texture: lavender-white skin packed with salmon-pink pores (rose mouths, pale tentacle-tip specks); the
    # narrow furrows between the lobes are paler with fewer pores; the unrolled rim keeps the pores
    n = 512
    cv = P.Canvas(n, n, "#D8C9D6")
    X, Z = (cv.u - 0.5) * 2 * PINK_UVR, (cv.v - 0.5) * 2 * PINK_UVR
    rid = pink_ridge(X, Z, ph).astype(np.float32)
    furrow = (1 - S(0.12, 0.4, rid)) * (1 - S(4.0, 5.0, np.hypot(X, Z)))
    cv.multiply(0.92 + 0.14 * P.fbm(n, n, 10, 3, 162))
    cv.blend(furrow, "#ECE6F2", 0.7)
    d, cr = lat.field(X, Z)
    size = 0.92 + 0.16 * cr
    keep = (cr > 0.05 + 0.9 * furrow).astype(np.float32)
    ring = (1 - S(0.4, 0.47, d / size)) * keep
    cv.blend(ring, "#D5838F", 0.92)
    cv.blend(ring * S(0.55, 0.9, cr), "#E09A86", 0.4)              # some pores salmon-orange
    cv.blend(ring * (1 - S(0.2, 0.36, d / size)), "#C06A7C", 0.5)  # deeper pink toward the mouth
    cv.blend((1 - S(0.08, 0.14, d / size)) * keep, "#8A4258", 0.8)  # mouths
    spark = (1 - S(0.02, 0.06, np.abs(d / size - 0.33))) * S(0.66, 0.76, P.fbm(n, n, 110, 1, 165)) * keep
    cv.blend(spark, "#F8F0F4", 0.75)                                 # retracted tentacle tips
    cv.blend(S(0.55, 0.85, P.fbm(n, n, 5, 2, 166)), "#C7A3C2", 0.15)  # broad lilac / cream blotches
    m_top = C.material("leather_pink", rough=0.6, image=tex("leather_pink", cv, jpeg=True))
    bc = P.Canvas(128, 128, "#BCA98F")
    rr = np.hypot(bc.u - 0.5, bc.v - 0.5) * 2 * PINK_UVR
    bc.blend(1 - S(1.5, 3.2, rr), "#8F7C66", 0.6)                   # darker foot
    bc.blend(S(3.4, 4.4, rr), "#CBB5B6", 0.6)                       # paler lip under the rim
    bc.multiply(0.9 + 0.18 * P.fbm(128, 128, 6, 3, 167))
    m_base = C.material("leather_pink_base", rough=0.55, image=tex("leather_pink_base", bc, jpeg=True))
    pc = P.Canvas(32, 32)
    pc.vgrad("#C27A8A", "#EBCFD6", 0.2, 0.95)
    m_pol = C.material("leather_pink_polyp", rough=0.55, image=tex("leather_pink_polyp", pc), double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_leather_pink_front", species="lobophytum_sp")
    body = g.obj("Body", root, "body", [m_top, m_base], smooth=True)
    orient_out(body, hit + axis * 1.0)
    proto = proto_bead(0.085, 0.11)  # low pink polyp nubs that fold down when the colony closes
    pol = polyp_node("Polyps", root, proto, [m_pol], inst)
    print(f"[coral] leather_pink: {g.tris()} body tris, {len(inst)} polyp nubs")
    return root, [body, pol], {"Polyps": (len(inst), sum(len(f) - 2 for f in proto[1]))}


# Finger leather v2 (2026-10-15 remake; [P p14] top left): not a ring of smooth tubes but a short, broad trunk that
# splits into stout lobes, each forking again into 2-3 blunt, knobbly fingers with rounded pale tips, the whole
# colony velvety tan with tiny polyps (painted speckle + an instanced polyp coat).
FINGER_POLYPS = 240


def finger_tube(pts, rads, sides, rs, knob=0.08):
    """Knobbly tube with a rounded tip: radius jitter per ring, then 3 shrinking rings and a point past the end."""
    import geomlib as G
    pts = np.asarray(pts, float)
    rads = np.asarray(rads, float) * (1 + rs.normal(0, knob, len(rads)))
    tng = unit(pts[-1] - pts[-2])
    r1 = rads[-1]
    tip = [pts[-1] + tng * r1 * f for f in (0.45, 0.8)]
    pts = np.vstack([pts, tip])
    rads = np.r_[rads, r1 * 0.88, r1 * 0.55]
    V, F, tv, av = G.tube(pts, rads, sides, cap_start=False, cap_end=True, up=(0, 0, 1))
    return V, F, tv, av, pts, rads


def finger_leather(rock, A):
    """[P p14] tan finger leather (Sinularia-like) on the arch's left top."""
    import geomlib as G
    hit, hn = new_anchor(rock, "coral_finger_leather")
    rs = RS(171)
    axis = unit(UP * 0.8 + hn * 0.4)
    t1 = unit(np.cross(axis, [0, 0, 1.0]))
    t2 = np.cross(axis, t1)
    g = T.WGeo()
    inst = Instances(172)
    trunk_top = hit + axis * 1.0
    V, F, tv, av = G.tube(np.array([hit - axis * 1.2, hit - axis * 0.2, hit + axis * 0.5, trunk_top]),
                          [2.0, 1.75, 1.5, 1.35], 12, cap_start=False, cap_end=True, up=(0, 0, 1))  # short, sunk foot
    g.add(V, F, np.c_[av, tv * 0.2], np.c_[np.full(len(V), 0.3), np.zeros(len(V)), np.zeros(len(V)), np.ones(len(V))])
    skins = []   # (V, sides, phase) for the polyp coat
    sides = 6
    nlobes = 5
    for k in range(nlobes):
        a = 2 * math.pi * k / nlobes + rs.uniform(-0.3, 0.3)
        out = t1 * math.cos(a) + t2 * math.sin(a)
        base = trunk_top + out * rs.uniform(0.5, 0.9) - axis * 0.35
        lean = rs.uniform(0.45, 0.9)
        L = rs.uniform(1.6, 2.2)
        d0 = unit(axis * 1.0 + out * lean)
        fork = base + d0 * L
        lobe = G.bezier(base, base + d0 * L * 0.4, fork - d0 * L * 0.25, fork, 4)
        r0 = rs.uniform(0.72, 0.85)
        V, F, tv, av = G.tube(lobe, np.linspace(r0, r0 * 0.9, 4), sides, cap_start=False, cap_end=True, up=tuple(axis))
        ph = k / nlobes
        g.add(V, F, np.c_[av, 0.2 + tv * 0.3], np.c_[np.full(len(V), 0.3), np.full(len(V), ph), tv * 0.35, np.ones(len(V))])
        skins.append((V[:-1], sides, ph, lobe, 0.0))
        nf = 2 if rs.uniform() < 0.55 else 3
        for j in range(nf):
            b = (j - (nf - 1) / 2) * 0.9 + rs.normal(0, 0.15)
            side = unit(np.cross(d0, axis) + 1e-6)
            fd = unit(d0 * 1.0 + side * b * 0.75 + axis * rs.uniform(0.1, 0.4))
            Lf = rs.uniform(2.2, 3.6) * (0.75 if j != nf // 2 else 1.0)
            p0 = fork - d0 * 0.25 + side * b * 0.35
            p3 = p0 + unit(fd + axis * 0.35 + rs.normal(0, 0.12, 3)) * Lf
            pts = G.bezier(p0, p0 + fd * Lf * 0.35, p3 - unit(axis + fd) * Lf * 0.3, p3, 6)
            rf = r0 * rs.uniform(0.62, 0.75)
            V, F, tv, av, pts2, rads = finger_tube(pts, np.linspace(rf, rf * 0.82, 6), sides, rs)
            g.add(V, F, np.c_[av, 0.5 + tv * 0.5], np.c_[np.full(len(V), 0.35), np.full(len(V), ph),
                                                         0.35 + 0.65 * tv, np.ones(len(V))])
            skins.append((V[:-1], sides, ph, pts2, 0.4))
    # polyp coat: on the skin of lobes and fingers, by surface area (fingers carry most)
    rings = []
    for Vs, sd, ph, pts, w0 in skins:
        nr = len(Vs) // sd
        for i in range(1, nr):
            for s_ in range(sd):
                rings.append((Vs[i * sd + s_], Vs[i * sd + s_] - pts[min(i, len(pts) - 1)], ph, w0))
    pick = rs.choice(len(rings), min(FINGER_POLYPS, len(rings)), replace=False)
    for i in np.sort(pick):
        v, nrm, ph, w0 = rings[i]
        nrm = unit(nrm + rs.normal(0, 0.15, 3))
        inst.add(v + nrm * 0.02, nrm, rs.uniform(0.8, 1.2), phase=(ph + rs.normal(0, 0.04)) % 1)
    # skin: warm tan, velvety speckle of tiny polyps, darker grooves at the base, paler rounded tips
    cv = P.Canvas(128, 256, "#A08560")
    cv.multiply(0.86 + 0.28 * P.fbm(256, 128, 8, 3, 173))
    sp = P.fbm(256, 128, 48, 1, 174)
    cv.blend(S(0.58, 0.72, sp), "#D2BC92", 0.7)
    cv.blend(1 - S(0.2, 0.36, sp), "#7E6440", 0.45)
    cv.blend(1 - S(0.0, 0.3, cv.v), "#8C7250", 0.5)
    cv.blend(S(0.88, 0.99, cv.v), "#D4C098", 0.7)
    m = C.material("finger_leather", rough=0.75, image=tex("finger_leather", cv, jpeg=True))
    pc = P.Canvas(32, 32)
    t = np.clip((pc.u + pc.v) / 2, 0, 1)
    pc.rgb = (hex2rgb("#8E7350")[None, None] * (1 - S(0.2, 0.7, t))[..., None]
              + hex2rgb("#C8B48C")[None, None] * S(0.2, 0.7, t)[..., None]).astype(np.float32)
    m_p = C.material("finger_leather_polyp", rough=0.6, image=tex("finger_leather_polyp", pc), double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_finger_leather", species="sinularia_sp")
    body = g.obj("Body", root, "body", [m], smooth=True)
    C.recalc_normals(body)
    proto = proto_feather(6, 0.1, 0.04, 0.18, 70, 0.05, 1)
    pol = polyp_node("Polyps", root, proto, [m_p], inst)
    print(f"[coral] finger_leather: {g.tris()} body tris, {len(inst)} polyps, {len(skins)} lobes + fingers")
    return root, [body, pol], {"Polyps": (len(inst), sum(len(f) - 2 for f in proto[1]))}


EAR_R = 6.5  # cm disc radius ([P p05]: a ~13 cm disc, folded like a taco)
EAR_BEADS = 400


def ear_local(t, a, ph, ys=1.0):
    """Elephant-ear disc in its local frame (+Y up): the back half curls up into a tall, slightly overhanging wall
    (the "taco" of [P p05]), the sides cup, the rim ruffles; the front rim band rolls down. ys squashes the height
    above the disc floor (keeps the wall under the water surface)."""
    R = EAR_R
    x, z = R * t * math.cos(a), R * t * 0.9 * math.sin(a)
    back = max(0.0, -z / R)
    y = R * (0.1 + 0.13 * (x / R) ** 2 + 0.95 * back ** 2.2) \
        + R * (0.055 * t ** 3 * math.sin(7 * a + ph) + 0.022 * t ** 2 * math.sin(13 * a + 2 * ph))
    y -= R * 0.07 * S(0.8, 1.0, t) * (1 - min(1.0, back * 2.5))       # the front band rolls down
    z += R * 0.32 * back ** 3                                          # the wall's top curls forward over the centre
    y0 = R * 0.1
    return np.array([x * (1 - 0.12 * back ** 2), y0 + (y - y0) * ys, z])


def proto_bead(h=0.24, r=0.13):
    """Bulbous discal tentacle ("bead"): a 4-sided pyramid (4 tris) with a light tip."""
    V = [(r, 0, 0), (0, 0, r), (-r, 0, 0), (0, 0, -r), (0, h, 0)]
    F = [(0, 4, 1), (1, 4, 2), (2, 4, 3), (3, 4, 0)]
    UV = [(0.5, 0.1)] * 4 + [(0.5, 0.95)]
    COL = [(0, 0, 0.2, 1)] * 4 + [(0, 0, 1, 1)]
    return np.array(V, float), F, np.array(UV, float), np.array(COL, float)


def proto_knob(h=0.3, r=0.12):
    """Bulb on a narrow foot (6 tris): foot point, a 3-vertex equator high up, a top point. Smooth-shaded it reads
    as a round bead; UV v and the hinge run foot (0) -> crown (1)."""
    V = [(0, 0, 0)] + [(r * math.cos(2 * math.pi * k / 3), 0.6 * h, r * math.sin(2 * math.pi * k / 3)) for k in range(3)]
    V.append((0, h, 0))
    F = [(0, 1 + (k + 1) % 3, 1 + k) for k in range(3)] + [(4, 1 + k, 1 + (k + 1) % 3) for k in range(3)]
    UV = [(0.5, 0.05)] + [(0.5, 0.6)] * 3 + [(0.5, 0.97)]
    COL = [(0, 0, 0.0, 1)] + [(0, 0, 0.6, 1)] * 3 + [(0, 0, 1, 1)]
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
    rings, segs = 12, 40
    # lift 0.5 cm, squash the wall until it stays 1.2 cm under the water surface, then drape: where the lumpy rock
    # top pokes through the disc, raise the disc over it (a smoothed, never-lower height field over the polar grid)
    lift = 0.5
    c = c + nrm * lift
    for ys in np.arange(1.0, 0.3, -0.05):
        oral = np.array([q_rotate(q, ear_local(i / rings, 2 * math.pi * k / segs, ph, ys)) for i in range(rings + 1)
                         for k in range(0, segs, 2)])
        if (oral[:, 1] + c[1]).max() < C.WATER_Y - 1.2:
            break
    D = np.zeros((rings + 1, segs))
    for i in range(rings + 1):
        for k in range(segs):
            p = q_rotate(q, ear_local(i / rings, 2 * math.pi * k / segs, ph, ys)) + c
            if inside_rock(rock, p, 0.12):
                h2, _ = rock.ray(p + nrm * 4.0, -nrm, 4.0)
                if h2 is not None:
                    D[i, k] = max(0.0, float(np.dot(h2 - p, nrm))) + 0.15
    D[0] = D[0].max()
    for _ in range(3):
        nb = (np.roll(D, 1, 1) + np.roll(D, -1, 1) + np.r_[D[:1], D[:-1]] + np.r_[D[1:], D[-1:]]) / 4
        D = np.maximum(D, 0.5 * D + 0.5 * nb)
    print(f"[coral] elephant_ear: wall height x{ys:.2f}, drape max {D.max():.2f} cm over {int((D > 0).sum())} grid points")

    def el(t, a, ph_):
        fi = min(max(t, 0.0), 1.0) * rings
        fk = (a % (2 * math.pi)) / (2 * math.pi) * segs
        i0, k0 = min(int(fi), rings - 1), int(fk) % segs
        u, w = fi - i0, fk - int(fk)
        d = (D[i0, k0] * (1 - u) * (1 - w) + D[i0 + 1, k0] * u * (1 - w) + D[i0, (k0 + 1) % segs] * (1 - u) * w
             + D[i0 + 1, (k0 + 1) % segs] * u * w)
        return ear_local(t, a, ph_, ys) + np.array([0.0, d, 0.0])
    V, UV, COL = [el(0, 0, ph)], [(0.5, 0.5)], [(0.3, 0, 0.1, 1)]
    for i in range(1, rings + 1):
        t = i / rings
        for k in range(segs):
            a = 2 * math.pi * k / segs
            V.append(el(t, a, ph))
            UV.append((0.5 + 0.5 * t * math.cos(a), 0.5 + 0.5 * t * math.sin(a)))
            COL.append((0.5 + 0.4 * t, 0, 0.1 + 0.6 * t ** 2, 1))
    F = grid_faces(rings, segs)
    b = len(V)  # underside skirt down to the foot
    for k in range(segs):
        a = 2 * math.pi * k / segs
        V.append(np.array([1.6 * math.cos(a), -0.3, 1.45 * math.sin(a)]))
        UV.append((0.5 + 0.05 * math.cos(a), 0.5 + 0.05 * math.sin(a)))
        COL.append((0, 0, 0, 1))
    outer = 1 + (rings - 1) * segs
    for k in range(segs):
        F.append((outer + k, outer + (k + 1) % segs, b + (k + 1) % segs, b + k))
    mb = len(V)  # raised mouth cone
    for k in range(8):
        a = 2 * math.pi * k / 8
        V.append(el(0.07, a, ph) + np.array([0, 0.05, 0]))
        UV.append((0.5 + 0.02 * math.cos(a), 0.5 + 0.02 * math.sin(a)))
        COL.append((0.3, 0, 0.1, 1))
    V.append(el(0.0, 0.0, ph) + np.array([0, 0.5, 0]))
    UV.append((0.5, 0.5))
    COL.append((0.3, 0, 0.1, 1))
    F += [(mb + k, mb + 8, mb + (k + 1) % 8) for k in range(8)]
    for k in range(segs):  # the foot stays on the rock
        V[b + k] = V[b + k] - np.array([0.0, lift, 0.0])
    g = T.WGeo()
    W = np.array([q_rotate(q, v) for v in V]) + c
    g.add(W, F, np.array(UV), np.array(COL))
    # beads: area-uniform over the oral disc inside the rim band (t 0.09 .. 0.78), the curled back wall included;
    # normals from the parametric surface, signed so they face the oral side
    e = 0.01

    def oral_n(t, a):
        du = el(min(t + e, 1), a, ph) - el(max(t - e, 0), a, ph)
        dv = el(t, a + e, ph) - el(t, a - e, ph)
        return unit(np.cross(dv, du))
    sgn = 1.0 if oral_n(0.3, 0.3)[1] > 0 else -1.0
    inst = Instances(182)
    cand, area = [], []
    for i in range(1, 200):
        t = 0.09 + 0.69 * (i + 0.5) / 200
        for k in range(120):
            a = 2 * math.pi * (k + 0.5 * (i % 2)) / 120
            cand.append((t, a))
            dt, da = 0.69 / 200, 2 * math.pi / 120
            area.append(np.linalg.norm(np.cross(el(t + dt, a, ph) - el(t, a, ph),
                                                el(t, a + da, ph) - el(t, a, ph))))
    area = np.array(area)
    for j in np.sort(rs.choice(len(cand), EAR_BEADS, replace=False, p=area / area.sum())):
        t, a = cand[j]
        a += rs.normal(0, 0.01)
        ln = oral_n(t, a) * sgn
        inst.add(q_rotate(q, el(t, a, ph)) + c, unit(q_rotate(q, ln)), rs.uniform(0.85, 1.25) * (1.15 - 0.35 * t))
    n = 512
    cv = P.Canvas(n, n, "#3A2630")
    du, dv = cv.u - 0.5, cv.v - 0.5
    rr, ang = np.hypot(du, dv) * 2, np.arctan2(dv, du)
    cv.multiply(0.8 + 0.35 * P.fbm(n, n, 12, 3, 183))
    cv.blend(1 - S(0.08, 0.3, rr), "#2A1C24", 0.6)                          # dark hollow round the mouth
    # rim band: blue-grey, radially striated, covered in fine brown-grey marginal bumps
    band = S(0.76, 0.84, rr)
    cv.blend(band, "#8790AA", 0.9)
    stri = 0.5 + 0.5 * np.sin(ang * 140 + 6 * P.fbm(n, n, 8, 2, 186))
    cv.blend(band * S(0.55, 0.9, stri), "#A8B2CA", 0.5)
    bumps = S(0.6, 0.72, P.fbm(n, n, 90, 1, 184))
    cv.blend(band * bumps, "#5A5068", 0.65)
    cv.blend(S(0.97, 1.0, rr), "#6E7690", 0.6)
    spots = S(0.7, 0.76, P.fbm(n, n, 18, 2, 185)) * S(0.55, 0.7, rr) * (1 - S(0.8, 0.86, rr))
    cv.blend(spots, "#C4D23E", 0.9)                                          # yellow-green patches by the band
    cv.blend(spots * S(0.5, 0.8, P.fbm(n, n, 40, 1, 187)), "#E0A43A", 0.5)
    cv.blend(1 - S(0.03, 0.05, rr), "#D88A3A", 0.9)                          # orange mouth lip
    cv.blend(1 - S(0.015, 0.03, rr), "#2A1A1E", 0.9)
    m = C.material("elephant_ear", rough=0.6, image=tex("elephant_ear", cv, jpeg=True), emissive="#C9D640",
                   emissive_strength=0.0, double=True)
    bc = P.Canvas(16, 64)
    bc.vgrad("#2E1E28", "#B898B4", 0.25, 1.0, 1.4)                           # plum foot -> lilac crown
    m_b = C.material("elephant_ear_bead", rough=0.45, image=tex("elephant_ear_bead", bc))
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_elephant_ear", species="amplexidiscus_fenestrafer")
    body = g.obj("Body", root, "body", [m], smooth=True)
    proto = proto_knob(0.32, 0.15)
    pol = polyp_node("Beads", root, proto, [m_b], inst)
    print(f"[coral] elephant_ear: {g.tris()} body tris, {len(inst)} beads")
    return root, [body, pol], {"Beads": (len(inst), len(proto[1]))}  # all triangles


# ----------------------------------------------------------------------------
# Zoanthids (C4) on a rubble rock on the sand
# ----------------------------------------------------------------------------
def rubble(center, radii, seed, segs=14, rings=8, rough=0.35):
    """Lumpy live-rock rubble: an ellipsoid with smooth low-frequency lumps (warped sines over the direction) plus a
    little fine noise, its bottom flattened into the sand."""
    import geomlib as G
    c = np.asarray(center, float)
    V, F = G.ellipsoid((0, 0, 0), (radii[0], radii[2], radii[1]), segs, rings)
    V = V[:, [0, 2, 1]] + c          # poles on the vertical axis (the top one smooth, the bottom one in the sand)
    F = [f[::-1] for f in F]
    rs = RS(seed)
    d = V - c
    nrm = unit(d / np.asarray(radii) ** 2)
    u = unit(d / np.asarray(radii))
    ph = rs.uniform(0, 6.28, 6)
    lump = (0.5 * np.sin(2.6 * u[:, 0] + 1.3 * u[:, 2] + ph[0]) * np.sin(2.2 * u[:, 1] + ph[1])
            + 0.35 * np.sin(4.1 * u[:, 2] - 2.0 * u[:, 0] + ph[2]) + 0.25 * np.sin(6.3 * u[:, 1] + 3.1 * u[:, 0] + ph[3]))
    noise = rs.normal(0, 1, len(V)) * 0.25
    V = V + nrm * ((lump + noise)[:, None] * rough * min(radii) * 0.25)
    V[:, 1] = np.maximum(V[:, 1], c[1] - radii[1] * 0.4)  # flat-ish bottom buried in the sand
    return V, F


def zoa(rock, A):
    pos, n = A["coral_zoa_front_center"]
    c = np.array([pos[0], pos[1] - 0.2, pos[2]])
    V, F = rubble(c, (3.2, 1.9, 2.2), 3, segs=24, rings=12)
    g = T.WGeo()
    dd = V - c  # spherical UV (the rubble texture tiles), so the sides do not streak
    g.add(V, F, np.c_[np.arctan2(dd[:, 2], dd[:, 0]) / (2 * math.pi) * 2 + 1.0,
                      np.arctan2(dd[:, 1], np.hypot(dd[:, 0], dd[:, 2])) / math.pi + 0.5], (0.2, 0, 0, 1))
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
    # polyp (2026-10-15 remake, [P p08]): a waisted column (6 sides, 2 segments) flaring into a trumpet, a concave
    # oral disc (12) with the mouth at the bottom of the cup, and 24 rim tentacles angled up and out; alternate
    # tentacles take the left / right tentacle colour (u 0.15 / 0.35) so the rim reads striped
    def proto(r=0.5, h=0.5):
        Vp, Fp, UVp, COLp = [], [], [], []
        for j, (y, rr) in enumerate(((0, r * 0.5), (h * 0.55, r * 0.42), (h, r * 0.66))):
            for s_ in range(6):
                a = 2 * math.pi * (s_ + 0.5 * j) / 6
                Vp.append((rr * math.cos(a), y, rr * math.sin(a)))
                UVp.append((0.1, 0.1 + 0.1 * j))
                COLp.append((0, 0, 0.05 * j, 1))
        for j in range(2):
            for s_ in range(6):
                b0, b1 = 6 * j, 6 * (j + 1)
                Fp.append((b0 + s_, b1 + s_, b0 + (s_ + 1) % 6))
                Fp.append((b0 + (s_ + 1) % 6, b1 + s_, b1 + (s_ + 1) % 6))
        b = len(Vp)
        Vp.append((0, h - 0.04, 0))
        UVp.append((0.75, 0.5))
        COLp.append((0, 0, 0.2, 1))
        for s_ in range(12):
            a = 2 * math.pi * s_ / 12
            Vp.append((r * 0.66 * math.cos(a), h + 0.07, r * 0.66 * math.sin(a)))
            UVp.append((0.75 + 0.24 * math.cos(a), 0.5 + 0.48 * math.sin(a)))
            COLp.append((0, 0, 0.3, 1))
        for s_ in range(12):
            Fp.append((b, b + 1 + (s_ + 1) % 12, b + 1 + s_))
        for s_ in range(24):
            a = 2 * math.pi * (s_ + 0.5) / 24
            dd = np.array([math.cos(a), 0, math.sin(a)])
            tg = np.array([-math.sin(a), 0, math.cos(a)])
            base = np.array([0, h + 0.06, 0]) + dd * r * 0.64
            tip = base + dd * r * 0.42 + UP * 0.17
            bb = len(Vp)
            u = 0.15 if s_ % 2 else 0.35
            Vp += [tuple(base - tg * 0.075), tuple(base + tg * 0.075), tuple(tip)]
            UVp += [(u, 0.6), (u, 0.6), (u, 0.9)]
            COLp += [(0, 0, 0.3, 1)] * 2 + [(0, 0, 1, 1)]
            Fp.append((bb, bb + 2, bb + 1))
        return np.array(Vp, float), Fp, np.array(UVp, float), np.array(COLp, float)

    def ptex(name, column, rim, disc, center, tip=None, mouth=None, glow=0.0, rim2=None, rays=None):
        """u < 0.5: column (v 0.1-0.3) and rim tentacles (v 0.6 base -> 0.9 tip; u < 0.25 / > 0.25 alternate
        tentacles, rim2 colours every other one); u >= 0.5: oral disc (rays: radial streaks between the rim and
        the centre ring)."""
        cv = P.Canvas(64, 64, column)
        cv.blend((cv.u < 0.5) * S(0.5, 0.7, cv.v), rim)
        if rim2:
            cv.blend((cv.u < 0.25) * S(0.5, 0.7, cv.v), rim2)
        if tip:  # pale tentacle tips (the brown rim reads striped cream-brown in [P p07] [P p08])
            cv.blend((cv.u < 0.5) * S(0.74, 0.88, cv.v), tip)
        rr = np.hypot((cv.u - 0.75) / 0.25, (cv.v - 0.5) / 0.5)
        cv.blend((cv.u >= 0.5) * 1.0, disc)
        if rays:
            ang = np.arctan2(cv.v - 0.5, (cv.u - 0.75) * 2)
            cv.blend((cv.u >= 0.5) * S(0.45, 0.9, np.cos(ang * 18)) * S(0.45, 0.6, rr) * (1 - S(0.85, 0.95, rr)), rays, 0.6)
        cv.blend((cv.u >= 0.5) * (1 - S(0.3, 0.5, rr)), center)
        if mouth:
            cv.blend((cv.u >= 0.5) * (1 - S(0.08, 0.16, rr)), mouth)
        cv.blend((cv.u >= 0.5) * S(0.85, 1.0, rr), rim)
        return C.material(name, rough=0.55, image=tex(name, cv), emissive=center, emissive_strength=glow)

    # rubble: porous grey-tan live rock with pink-purple coralline crust patches and dark pits
    rock_cv = P.Canvas(128, 128, "#A49880")
    rock_cv.multiply(0.75 + 0.4 * P.fbm(128, 128, 6, 4, 9))
    rock_cv.blend(S(0.58, 0.7, P.fbm(128, 128, 4, 3, 10)), "#A87894", 0.6)
    rock_cv.blend(S(0.66, 0.74, P.fbm(128, 128, 9, 2, 11)), "#8A6A9A", 0.4)
    f1, f2, _ = P.voronoi(128, 128, 60, 12)
    rock_cv.blend(1 - S(1.5, 3.0, f1), "#4A4038", 0.55)
    rm = C.material("zoa_rubble", rough=0.9, image=tex("zoa_rubble", rock_cv, jpeg=True))
    mA = ptex("zoa_polyp_orange", "#7A5A3A", "#E0792A", "#E0792A", "#5FBF5A", rim2="#C8642A", rays="#F09A4A")
    # [P p02] [P p07-p09]: brown rim tentacles with pale tips, a bright yellow oral ring, a green centre and a dark
    # mouth (was a cream-centred brown morph). No emissive: it is one flat colour and would tint the brown column green
    mB = ptex("zoa_polyp_brown", "#3E2A1C", "#5E3C22", "#D9C238", "#7CC23A", tip="#C8AE7E", mouth="#2A3418",
             rim2="#B89A6A", rays="#A88A2E")
    mC = ptex("paly_polyp_brown", "#5A4030", "#7A5A36", "#7A5A36", "#D8C8A0", rim2="#9A7A52")
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
    cv = P.Canvas(64, 64, "#6A5A3E")
    cv.multiply(0.85 + 0.3 * P.fbm(64, 64, 6, 3, 33))
    st = C.material("clove_stolon", rough=0.85, image=tex("clove_stolon", cv))
    # [2026-10-15] brown translucent stalk, cream-tan feathery tentacles with a darker rachis and pale pinnules
    fc = feather_canvas("#6E5A44", "#9A8468", "#A8917A", "#D2C4AE", "#7E6A54", pinnules=11, seed=3,
                        tip_white="#E8DECC")
    pm = C.material("clove_polyp", rough=0.6, image=tex("clove_polyp", fc, alpha=True), image_alpha=True,
                    double=True, clip=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_clove_kenya_front", species="clove_polyps")
    body = g.obj("Stolons", root, "body", [st], smooth=True)
    pr = proto_feather_quads(8, 0.5, 0.09, 0.8, 48, 0.13, 4, 0.55)   # tall stalk, tentacles cupped up then out
    pol = polyp_node("Polyps", root, pr, [pm], inst, extras={"alphaTest": 0.5})
    nt = sum(len(f) - 2 for f in pr[1])
    return root, [body, pol], {"Polyps": (len(inst), nt)}


# ----------------------------------------------------------------------------
# Mushroom (C5)
# ----------------------------------------------------------------------------
def shroom_disc(c, n, radius, seed, rings=7, segs=36):
    """Ruffled corallimorph disc (radius cm) on a short stalk, oriented to normal n (2026-10-15 remake): a low dome
    with a raised mouth cone, faint radial ribs, a margin that folds into 7-9 soft lobes and lifts on one side, and a
    stalk skirt that flares into the rock. UV: polar (centre 0.5, 0.5 -> rim radius 0.47); the skirt maps to the
    texture's outer ring (u radius 0.47 .. 0.5)."""
    rs = RS(seed)
    V, UV, COL, F = [], [], [], []
    ph = rs.uniform(0, 6.28, 4)
    nl = int(rs.integers(7, 10))

    def pt(t, a):
        r = radius * t * (1 + 0.06 * t ** 2 * math.sin(nl * a + ph[0]))
        ruffle = radius * (0.13 * t ** 2.5 * math.sin(nl * a + ph[0] + 0.6) + 0.045 * t ** 2 * math.sin(2 * nl * a + ph[1])
                           + 0.12 * t ** 2 * math.cos(a - ph[2]))                       # one side lifts
        rib = radius * 0.012 * t * math.sin(28 * a + ph[3])                               # faint radial ribs
        y = radius * (0.34 - 0.16 * t ** 2) + ruffle + rib
        return (r * math.cos(a), y, r * 0.92 * math.sin(a))
    V.append((0, 0.34 * radius + 0.1, 0))
    UV.append((0.5, 0.5))
    COL.append((0.3, 0, 0.25, 1))
    for i in range(1, rings + 1):
        t = (i / rings) ** 0.85
        for k in range(segs):
            a = 2 * math.pi * k / segs
            V.append(pt(t, a))
            UV.append((0.5 + 0.47 * t * math.cos(a), 0.5 + 0.47 * t * math.sin(a)))
            COL.append((0.4 + 0.5 * t, 0, 0.25 + 0.75 * t, 1))
    F += grid_faces(rings, segs)
    # mouth cone: lift the first ring into a small raised oral cone
    for k in range(segs):
        x, y, z = V[1 + k]
        V[1 + k] = (x, y + 0.06 * radius, z)
    b = len(V)
    for j, (rf, yf) in enumerate(((0.55, 0.12), (0.3, 0.0))):  # underside skirt -> stalk foot
        for k in range(segs):
            a = 2 * math.pi * k / segs
            V.append((rf * radius * math.cos(a), yf * radius, rf * 0.92 * radius * math.sin(a)))
            UV.append((0.5 + (0.485 + 0.012 * j) * math.cos(a), 0.5 + (0.485 + 0.012 * j) * math.sin(a)))
            COL.append((0.2, 0, 0.0, 1))
    outer = 1 + (rings - 1) * segs
    for k in range(segs):
        F.append((outer + k, outer + (k + 1) % segs, b + (k + 1) % segs, b + k))
        F.append((b + k, b + (k + 1) % segs, b + segs + (k + 1) % segs, b + segs + k))
    V = np.array(V, float)
    q = q_from_up(unit(n), rs.uniform(0, 6.28))
    V = np.array([q_rotate(q, v) for v in V]) + np.asarray(c, float)
    return V, F, np.array(UV), np.array(COL)


def shroom_tex(name, base, rim, plug, stripe, spot, under):
    """Polar mushroom texture: base colour with radial stripes and fine raised warts (paler, fluorescent-looking
    dots), a darker margin band with a pale edge, a radially striped mouth plug; the outer ring (r > 0.94) is the
    paler underside."""
    n = 256
    cv = P.Canvas(n, n, base)
    du, dv = cv.u - 0.5, cv.v - 0.5
    rr, ang = np.hypot(du, dv) / 0.47, np.arctan2(dv, du)
    cv.multiply(0.85 + 0.3 * P.fbm(n, n, 10, 3, sum(map(ord, name)) % 997))
    cv.blend(0.35 * (0.5 + 0.5 * np.cos(ang * 30 + 3 * P.fbm(n, n, 6, 2, 5))) * S(0.15, 0.6, rr), stripe)
    d, cr = PoreLattice(0.5, 0.034, 4, jitter=0.3).field(du, dv)
    warts = (1 - S(0.2, 0.32, d)) * (cr > 0.5) * S(0.18, 0.4, rr) * (1 - S(0.82, 0.95, rr))
    cv.blend(warts, spot, 0.8)
    cv.blend(S(0.72, 0.95, rr), rim, 0.85)
    cv.blend(S(0.93, 1.0, rr) * (1 - S(1.0, 1.02, rr)), spot, 0.35)
    cv.blend(1 - S(0.07, 0.15, rr), plug)
    cv.blend((1 - S(0.04, 0.15, rr)) * (0.5 + 0.5 * np.cos(ang * 12)), stripe, 0.35)
    cv.blend(1 - S(0.0, 0.035, rr), "#3A2A26", 0.8)
    cv.blend(S(1.0, 1.03, rr), under)
    return tex(name, cv, jpeg=True)


def mushroom(rock, A):
    pos, n = A["coral_shroom_center"]
    g = T.WGeo()
    V, F, UV, COL = shroom_disc(pos - UP * 0.1, (0, 1, 0), 1.75, 61)
    g.add(V, F, UV, COL, mat=0)
    pos2, n2 = A["coral_shroom_small"]
    V, F, UV, COL = shroom_disc(pos2 - unit(n2) * 0.1, n2, 1.25, 62, rings=6, segs=30)
    g.add(V, F, UV, COL, mat=1)
    m1 = C.material("mushroom", rough=0.5, image=shroom_tex("mushroom", "#4FAE58", "#2A7266", "#C8B48A", "#3E9C66",
                                                            "#9CF2B0", "#B8A9B8"), emissive="#5DBD5E",
                    emissive_strength=0.0, double=True)
    m2 = C.material("mushroom_small", rough=0.5, image=shroom_tex("mushroom_small", "#843646", "#561E30", "#B07A60",
                                                                  "#6E2838", "#C46A7A", "#A88A8E"), double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_shroom_center", species="mushroom", fluorescent=0.5)
    body = g.obj("Body", root, "body", [m1, m2], smooth=True, extras={"anchors": ["coral_shroom_center",
                                                                                    "coral_shroom_small"]})
    print(f"[coral] mushroom: {g.tris()} body tris")
    return root, [body], {}


# ----------------------------------------------------------------------------
# Hairy mushroom (Rhodactis sp.): the clownfish host. A cluster of flat-to-slightly-cupped discs 3-6 cm across,
# the oral surface densely covered in short branching ("hairy") tentacles: olive-green base, green tentacles
# with a lighter, fluorescent tip glow; no visible mouth under the hair (a faint one at most).
# ----------------------------------------------------------------------------
HAIRY_DISCS = 6           # discs in the clump (2026-10-15: packed edge to edge, [T84] / coral_hairy_mushroom.md)
HAIRY_SPACING = 0.3       # cm between tentacle tufts (jittered); sets the instance count (~850)


def hairy_disc_local(radius, cup, ph, t, a):
    """Point on a hairy-mushroom disc in its local frame (cm, base at the origin, oral surface up +Y).
    t: 0 centre -> 1 rim, a: angle. Cupped: the rim rises by `cup` x radius, with a gentle 5-6 lobe wave."""
    r = radius * t * (1 + 0.05 * t ** 2 * math.sin(6 * a + ph))
    wave = radius * (0.07 * t ** 3 * math.sin(5 * a + ph) + 0.03 * t ** 2 * math.sin(11 * a + 2 * ph))
    y = radius * (0.30 + cup * t ** 2) + wave
    return np.array([r * math.cos(a), y, r * 0.92 * math.sin(a)])


def hairy_disc(c, n, radius, cup, seed, twist, rings=5, segs=24):
    """Disc mesh (oral surface + a short stalk skirt) placed at c, oriented to normal n.
    Returns V, F, UV, COL and the (q, ph) needed to place tentacles on the same surface."""
    rs = RS(seed)
    ph = rs.uniform(0, 6.28)
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


def proto_hairy(h=0.17):
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
        sp = math.radians(68 if k % 2 else 56)  # splayed wide: neighbouring tufts knit into a velvet mat
        tip = base + (d * math.sin(sp) + UP * math.cos(sp)) * L
        bi = len(V)
        V += [tuple(base - tng * 0.075), tuple(base + tng * 0.075), tuple(tip)]
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
    radii = [2.8, 2.2, 1.9, 1.7, 1.5, 1.6][:HAIRY_DISCS]  # 3-6 cm discs
    offs = [(0, 0)] + [(math.cos(a) * rr, math.sin(a) * rr) for a, rr in
                       zip(np.linspace(0, 2 * math.pi, HAIRY_DISCS - 1, endpoint=False) + 0.4 + rs.normal(0, 0.15, HAIRY_DISCS - 1),
                           (4.0, 3.7, 3.6, 3.4, 3.5))]
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
    dc = P.Canvas(128, 128, "#3C4C2A")
    rr = np.hypot(dc.u - 0.5, dc.v - 0.5) * 2
    dc.blend(S(0.6, 1.0, rr), "#7A8A5C", 0.7)                     # paler bare margin
    dc.blend(1 - S(0.04, 0.12, rr), "#2A3420", 0.6)
    dc.multiply(0.85 + 0.3 * P.fbm(128, 128, 8, 3, 99))
    dc.blend(S(0.6, 0.75, P.fbm(128, 128, 30, 1, 101)) * (1 - S(0.85, 1.0, rr)), "#5E7444", 0.5)
    disc_m = C.material("mushroom_hairy", rough=0.85, image=tex("mushroom_hairy", dc, jpeg=True), emissive="#6FD45A",
                        emissive_strength=0.0, double=True)
    pc = P.Canvas(64, 64, "#4A6A2C")
    tt = pc.v
    pc.rgb = (hex2rgb("#3E4C2A")[None, None] * (1 - S(0.0, 0.5, tt))[..., None]
              + hex2rgb("#6E8A48")[None, None] * (S(0.0, 0.5, tt) * (1 - S(0.75, 0.97, tt)))[..., None]
              + hex2rgb("#A6DC78")[None, None] * S(0.75, 0.97, tt)[..., None])  # olive velvet, fluorescent tips
    pc.multiply(0.92 + 0.16 * P.fbm(64, 64, 6, 2, 100))
    pol_m = C.material("mushroom_hairy_polyp", rough=0.55, image=tex("mushroom_hairy_polyp", pc), emissive="#8CF060",
                       emissive_strength=0.04, double=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_shroom_hairy", species="rhodactis_sp", fluorescent=0.6,
                 hostFor="clown_orange,clown_darwin", discs=HAIRY_DISCS)
    body = g.obj("Body", root, "body", [disc_m], smooth=True)
    pol = polyp_node("Polyps", root, proto, [pol_m], inst)
    return root, [body, pol], {"Polyps": (len(inst), len(proto[1]) + sum(1 for f in proto[1] if len(f) == 4))}


# ----------------------------------------------------------------------------
# Hairy-mushroom mound (2026-10-15): Tee's night video shows the big lime-green mound at the lower right is a colony
# of green hairy mushrooms (Rhodactis) with purple oral centres, not green star polyps (the lawnmower blenny naps on
# top of it). Built on the old GSP mound's anchor and footprint: a low olive-brown tissue dome (~15 x 13 cm, ~3 cm
# high, a smooth top to rest on) tiled with ~12 overlapping lime discs, each fuzzy with short branched tentacles
# except for its bare purple centre. rhodactis_sp variantModels "mound" -> this file (the renderer matches the anchor).
# ----------------------------------------------------------------------------
MOUND_DISCS = 16
MOUND_SPACING = 0.56      # cm between tentacle tufts (~900 tufts, the old GSP mat's share of the budget)


def mushroom_mound(rock, A):
    pos, n = A["coral_gsp_lower_right"]
    rs = RS(211)
    hit, hn, _ = rock.nearest(pos)
    axis = unit(UP * 0.85 + unit(n) * 0.3)
    radii = (7.2, 3.0, 6.2)
    c = hit - axis * 0.6
    V, F, UV, surf, q = dome_mesh(c, radii, axis, 212, segs=20, rings=6, sink=0.3)
    g = T.WGeo()
    g.add(V, F, UV, (0.5, 0, 0, 1), mat=0)
    th_max = math.acos(-0.3)
    ga = math.pi * (3 - math.sqrt(5))
    inst = Instances(213)
    proto = proto_hairy()
    nd = 0
    for i in range(MOUND_DISCS):   # golden spiral, area-uniform over the cap; discs overlap like the real clump
        th = math.acos(1 - (i + 0.5) / MOUND_DISCS * (1 - math.cos(th_max * 0.9)))
        ph = (i * ga) % (2 * math.pi) + rs.normal(0, 0.1)
        p = surf(th, ph)
        e = 0.01
        nn = axis if th < 0.05 else unit(np.cross(surf(th, ph + e) - surf(th, ph - e), surf(th + e, ph) - surf(th - e, ph)))
        if np.dot(nn, p - c) < 0:
            nn = -nn
        if inside_rock(rock, p, 0.0):
            continue
        r = rs.uniform(2.5, 3.1) * (1.0 - 0.2 * th / th_max)
        nrm = unit(nn * 0.8 + axis * 0.2)
        cup = rs.uniform(0.02, 0.1)
        base = p - nrm * 0.5
        Vd, Fd, UVd, COLd, qd, phd = hairy_disc(base, nrm, r, cup, 220 + nd, rs.uniform(0, 6.28), rings=3, segs=18)
        g.add(Vd, Fd, UVd, COLd, mat=1)
        nd += 1
        sp = MOUND_SPACING
        for j in range(int(2 * r / (sp * 0.866)) + 1):
            for k in range(int(2 * r / sp) + 1):
                x = -r + k * sp + (j % 2) * sp * 0.5 + rs.normal(0, sp * 0.15)
                z = -r + j * sp * 0.866 + rs.normal(0, sp * 0.15)
                t = math.hypot(x, z / 0.92) / r
                if t > 0.95 or t < 0.24:   # bare purple oral centre
                    continue
                a = math.atan2(z / 0.92, x)
                lp = hairy_disc_local(r, cup, phd, t, a)
                du = hairy_disc_local(r, cup, phd, min(t + e, 1), a) - hairy_disc_local(r, cup, phd, t - e, a)
                dv = hairy_disc_local(r, cup, phd, t, a + e) - hairy_disc_local(r, cup, phd, t, a - e)
                ln = unit(np.cross(dv, du))
                if ln[1] < 0:
                    ln = -ln
                wn = unit(q_rotate(qd, unit(ln + UP * 0.3)))
                inst.add(q_rotate(qd, lp) + base + wn * 0.02, wn, (1.3 + 0.4 * (1 - t)) * rs.uniform(0.85, 1.15))
    # base tissue: olive-brown, darker toward the rock
    bc = P.Canvas(64, 64, "#4E6030")
    bc.multiply(0.8 + 0.35 * P.fbm(64, 64, 5, 3, 214))
    m_base = C.material("mushroom_mound_base", rough=0.85, image=tex("mushroom_mound_base", bc, jpeg=True))
    # discs: lime with a paler bare margin and a purple oral centre around a dark mouth
    dc = P.Canvas(128, 128, "#5E9A30")
    rr = np.hypot(dc.u - 0.5, dc.v - 0.5) * 2
    dc.multiply(0.85 + 0.3 * P.fbm(128, 128, 8, 3, 215))
    d_, cr_ = PoreLattice(0.5, 0.025, 217, jitter=0.35).field(dc.u - 0.5, dc.v - 0.5)
    dc.blend((1 - S(0.15, 0.3, d_)) * (cr_ > 0.35), "#A8E060", 0.7)          # fuzz between the instanced tufts
    dc.blend(S(0.8, 1.0, rr), "#9CC860", 0.7)
    dc.blend(1 - S(0.16, 0.3, rr), "#6E3C82", 0.95)
    dc.blend((1 - S(0.08, 0.2, rr)) * S(0.5, 0.7, P.fbm(128, 128, 20, 1, 216)), "#9A62AE", 0.5)
    dc.blend(1 - S(0.02, 0.05, rr), "#2A1830", 0.9)
    m_disc = C.material("mushroom_mound", rough=0.8, image=tex("mushroom_mound", dc, jpeg=True), emissive="#8CE050",
                        emissive_strength=0.0, double=True)
    pc = P.Canvas(64, 64)
    tt = pc.v
    pc.rgb = (hex2rgb("#4A6E26")[None, None] * (1 - S(0.0, 0.5, tt))[..., None]
              + hex2rgb("#8CC83C")[None, None] * (S(0.0, 0.5, tt) * (1 - S(0.75, 0.97, tt)))[..., None]
              + hex2rgb("#CFF07A")[None, None] * S(0.75, 0.97, tt)[..., None]).astype(np.float32)
    pol_m = C.material("mushroom_mound_polyp", rough=0.55, image=tex("mushroom_mound_polyp", pc), emissive="#6A9A40",
                       emissive_strength=0.15, double=True)   # lime but matte (research 11 §6)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_gsp_lower_right", species="rhodactis_sp", fluorescent=0.7, discs=nd,
                 form="hairy_mound")
    body = g.obj("Body", root, "body", [m_base, m_disc], smooth=True)
    C.recalc_normals(body)
    pol = polyp_node("Polyps", root, proto, [pol_m], inst)
    print(f"[coral] mushroom_hairy_mound: {nd} discs, {len(inst)} tufts, {g.tris()} body tris")
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
        # 2026-10-15 remake: fewer, fleshier Gracilaria stems (r 1.1 -> 0.5 mm) with irregular alternate side
        # branches that fork again, tapering to paler tips
        for br in range(int(round(size * 1.15))):
            d = unit(UP * 0.8 + bn * 0.6 + rs.normal(0, 0.45, 3))
            L = rs.uniform(0.45, 1.0) * size
            pts = []
            p = base + rs.normal(0, 0.45, 3)
            dd = d
            for k in range(7):
                pts.append(p.copy())
                dd = unit(dd + rs.normal(0, 0.2, 3) + UP * 0.05)
                p = p + dd * L / 6
            pts = np.array(pts)
            rad = np.linspace(0.11, 0.05, len(pts))
            V, F, tv, av = G.tube(pts, rad, 3, cap_start=False, cap_end=True, up=(0, 0, 1))
            ph = (tuft / 3 + br * 0.03) % 1
            g.add(V, F, np.c_[av, tv], np.c_[np.ones(len(V)), np.full(len(V), ph), tv, np.ones(len(V))])
            for s_ in range(3):  # side branches (alternate sides up the stem), some forking once more
                k0 = int(rs.integers(1, 5))
                side = unit(np.cross(unit(pts[k0 + 1] - pts[k0]), rs.normal(0, 1, 3)))
                q = [pts[k0]]
                dd2 = unit(unit(pts[k0 + 1] - pts[k0]) + side * 1.1)
                for k in range(3):
                    q.append(q[-1] + dd2 * L * 0.13)
                    dd2 = unit(dd2 + rs.normal(0, 0.25, 3) + UP * 0.1)
                q = np.array(q)
                r0 = rad[k0] * 0.75
                V, F, tv, av = G.tube(q, np.linspace(r0, 0.035, 4), 3, cap_start=False, cap_end=True, up=(0, 0, 1))
                w = (k0 / 6) + tv * 0.45
                g.add(V, F, np.c_[av, 0.4 + tv * 0.6], np.c_[np.ones(len(V)), np.full(len(V), ph), np.clip(w, 0, 1), np.ones(len(V))])
                if rs.uniform() < 0.5:
                    j0 = int(rs.integers(1, 3))
                    dd3 = unit(unit(q[j0 + 1] - q[j0]) + rs.normal(0, 0.9, 3))
                    q3 = np.array([q[j0], q[j0] + dd3 * L * 0.1, q[j0] + dd3 * L * 0.19])
                    V, F, tv, av = G.tube(q3, [r0 * 0.7, 0.04, 0.03], 3, cap_start=False, cap_end=True, up=(0, 0, 1))
                    w = (k0 / 6) + 0.2 + tv * 0.4
                    g.add(V, F, np.c_[av, 0.6 + tv * 0.4], np.c_[np.ones(len(V)), np.full(len(V), ph), np.clip(w, 0, 1), np.ones(len(V))])
    cv = P.Canvas(32, 128)
    cv.vgrad("#5E1830", "#B0304E", 0, 1, 0.8)                 # wine-red stems
    cv.multiply(0.85 + 0.3 * P.fbm(128, 32, 4, 3, 42))
    cv.blend(S(0.8, 1.0, cv.v), "#D8687E", 0.6)               # translucent pink tips
    m = C.material("macro_red", rough=0.6, image=tex("macro_red", cv, jpeg=True))
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_macro_back_left", species="red_macroalgae",
                 anchors=[t[0] for t in tufts])
    body = g.obj("Body", root, "body", [m], smooth=True)
    return root, [body], {}


# ----------------------------------------------------------------------------
# Organ pipe coral (Tubipora musica) replacing the aiptasia (C7)
# ----------------------------------------------------------------------------
def organ_pipe(rock, A):
    """Organ pipe (2026-10-15 remake): a low mound ~6 x 5 cm of packed, slightly splaying red tubes of uneven height
    tied by two irregular stolonal plates, the open colony a fuzzy cap of large pale grey-green feathery polyps
    that hides most of the tops; the red skeleton shows at the flanks and the base."""
    import geomlib as G
    pos, n = A["coral_organpipe_kenya_base"]
    rs = RS(51)
    base, bn, _ = rock.nearest(pos + UP * 0.5)
    g = T.WGeo()
    inst = Instances(52)
    t1 = np.array([1.0, 0, 0])
    t2 = np.array([0, 0, 1.0])
    pts = []   # packed hex grid of pipes (a solid block, not a fence), clipped to a lobed ellipse
    sp = 0.55
    for j in range(-6, 7):
        for i in range(-7, 8):
            a = (i + 0.5 * (j % 2)) * sp + rs.normal(0, 0.03)
            b = j * sp * 0.866 + rs.normal(0, 0.03)
            if (a / 3) ** 2 + (b / 2.5) ** 2 <= 1 + 0.12 * math.sin(5 * math.atan2(b, a)):
                pts.append((a, b))
    plate_h = (1.0, 2.1)
    for a, b in pts:
        foot = base + t1 * a + t2 * b
        h, hn, _ = rock.nearest(foot + UP * 2)
        foot = np.array([foot[0], h[1] - 0.25, foot[2]])
        dome = max(0.0, 1 - ((a / 3) ** 2 + (b / 2.5) ** 2))
        height = 1.1 + 2.5 * dome ** 0.8 + rs.uniform(-0.35, 0.35)
        lean = unit(UP + (t1 * a / 3 + t2 * b / 2.5) * 0.18 + rs.normal(0, 0.05, 3))   # pipes splay at the edge
        top = foot + lean * height
        r = rs.uniform(0.21, 0.25)
        V, F, tv, av = G.tube(np.array([foot, top]), [r * 1.05, r * 0.95], 5, cap_start=False, cap_end=False,
                              up=(1, 0, 0))
        g.add(V, F, np.c_[av, tv * 0.8], (0.3, 0, 0, 1))
        ring = V[-5:]
        c = top - lean * 0.06
        g.add(np.vstack([ring, [c]]), [(k, (k + 1) % 5, 5) for k in range(5)], np.full((6, 2), (0.9, 0.9)), (0.3, 0, 0, 1))
        inst.add(top, unit(lean + rs.normal(0, 0.08, 3)), rs.uniform(0.85, 1.2) * (0.85 + 0.25 * dome))
    for pi, py in enumerate(plate_h):  # stolonal plates: thin, wavy-edged, following the mound's outline
        segs = 18
        sh = 0.92 if pi else 1.0
        ring = []
        for k in range(segs):
            th = 2 * math.pi * k / segs
            rr = sh * (1 + 0.1 * math.sin(3 * th + pi) + rs.uniform(-0.05, 0.05))
            ring.append(base + UP * (py + 0.12 * math.sin(2 * th + pi) + rs.uniform(-0.05, 0.05))
                        + t1 * 3.15 * rr * math.cos(th) + t2 * 2.65 * rr * math.sin(th))
        V = np.vstack([ring, [base + UP * py]])
        F = [(segs, (k + 1) % segs, k) for k in range(segs)]
        V2 = V - UP * 0.18
        F2 = [(segs + 1 + k, segs + 1 + (k + 1) % segs, segs + 1 + segs) for k in range(segs)]
        Fs = [(k, (k + 1) % segs, segs + 1 + (k + 1) % segs, segs + 1 + k) for k in range(segs)]
        g.add(np.vstack([V, V2]), F + F2 + Fs, np.full((2 * segs + 2, 2), (0.5, 0.5)), (0.5, 0, 0, 1))
    # skeleton: deep red with lengthwise grain and darker pipe mouths
    cv = P.Canvas(64, 64, "#8A2424")
    cv.vgrad("#6A1A1C", "#A42C2C")
    cv.multiply(0.8 + 0.35 * P.fbm(64, 64, 8, 3, 2))
    cv.multiply(0.92 + 0.12 * np.sin(cv.u * 2 * math.pi * 10))
    cv.blend(S(0.85, 0.95, cv.u) * S(0.85, 0.95, cv.v), "#3A0C0E")
    sk = C.material("organpipe_skeleton", rough=0.8, image=tex("organpipe_skeleton", cv, jpeg=True))
    fc = feather_canvas("#8E9A78", "#A9B496", "#B4C0A0", "#E2E8D2", "#8A9878", pinnules=10, seed=5,
                        tip_white="#F0F2E6")
    pm = C.material("organpipe_polyp", rough=0.55, image=tex("organpipe_polyp", fc, alpha=True), image_alpha=True,
                    double=True, clip=True)
    root = C.empty("Root")
    C.set_extras(root, anchor="coral_organpipe_kenya_base", species="organ_pipe")
    body = g.obj("Skeleton", root, "body", [sk], smooth=False)
    pr = proto_feather_quads(8, 0.15, 0.13, 0.55, 58, 0.13, 4, 0.45)   # big feathery polyps knit into a fuzzy cap
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
    "zoa": zoa, "clove": clove, "mushroom": mushroom, "mushroom_hairy": mushroom_hairy,
    "mushroom_hairy_mound": mushroom_mound, "macro_red": macro_red,
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
