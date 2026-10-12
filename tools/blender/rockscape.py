"""Rockscape (ASSETS.md §3): metaball blocking -> voxel remesh -> numpy displacement + pits ->
decimate (40k) -> Smart UV -> Cycles bakes (normal, AO, position) -> numpy albedo/roughness ->
rockscape.glb + textures + anchors.json.

All coordinates in this file are WORLD cm (x right, y up, z toward the front glass; back glass z=0),
converted to Blender with common.w2b.
"""
import json
import math
import os
import time

import numpy as np

import common as C
import noise3d as N
import paintlib as P
from common import bpy, w2b

S = P.smoothstep
TANK = (121.9, 61.0, 33.0)
SAND_Y = 3.0
WATER_Y = 56.0

# ----------------------------------------------------------------------------
# Blocking: (name, center, radii, negative) per element, grouped by rock (§3.1 table)
# ----------------------------------------------------------------------------
ROCKS = {
    # R1 left back-wall stack (x 11-56, top ~52, z 0-15) with the big cavern under its right overhang
    "R1": [((19, 10, 7), (10, 10, 7), False), ((21, 22, 7), (10, 9, 7), False), ((24, 33, 7), (11, 8, 7), False),
           ((27, 44, 6.5), (11, 7, 6), False), ((20, 50, 5), (7, 4, 4.5), False),
           ((40, 38, 7), (13, 7, 7), False), ((48, 46, 6), (8, 6, 6), False), ((36, 47, 5.5), (7, 5, 5), False),
           ((12, 30, 5), (4, 12, 4.5), False),
           # cavern carve (opening x 30-55, y 6-30, back to z ~2)
           ((42, 15, 10), (10.5, 12, 9.5), True), ((35, 22, 9), (5, 7, 8), True)],
    # R2 left pillar (x 12-26, top 33, z 15-25), narrowing upward, blenny hole on the front-right face
    "R2": [((19, 7, 20), (7.5, 7, 5.5), False), ((19.5, 16, 20), (6.5, 7, 5.2), False),
           ((19, 24, 20), (5.5, 6, 4.6), False), ((18.5, 30, 20), (4.2, 3.5, 3.8), False)],
    # R3 center pillar (x 44-55, top 29, z 15-26), flattened top
    "R3": [((49, 7, 21), (6.5, 7, 5.5), False), ((49.5, 16, 21), (5.6, 7, 5), False),
           ((49, 24, 21), (5, 4.5, 4.4), False)],
    # R4 center arch / back shelf (x 55-71, top 33, z 0-11) with the opening (x 57-68, y 5-24)
    "R4": [((57, 12, 5), (4, 11, 5), False), ((69, 12, 5), (4, 11, 5), False), ((63, 28, 5), (10, 6, 5.5), False),
           ((63, 12, 6), (4.6, 9.5, 9), True),
           # back-glass rubble band behind the Kenya rock (tank_full.jpg: rock reaches the waterline across the back)
           ((60, 39, 3.2), (8, 6, 3.2), False), ((72, 45, 3.5), (7, 6.5, 3.5), False), ((55, 31, 3.2), (4, 7, 3.2), False),
           ((66, 50, 3.0), (5, 3.5, 3.0), False)],
    # R5 Kenya rock (x 65-80, top 38, z 8-23) + flat overhang slab over the eel den at sand level
    "R5": [((72, 9, 15), (9, 8.5, 8), False), ((73, 21, 15), (7.5, 9, 6.5), False), ((72, 31, 14), (5.5, 6, 5.5), False),
           ((69, 8.5, 22), (6.5, 2.6, 4.2), False), ((78, 12, 19), (4, 6, 4), False)],
    # R6 right arch (x 76-104, top 54, z 0-13) with the arch opening (x 85-95, y 24-38) and the shrimp shelf
    "R6": [((81, 21, 6), (5.5, 21, 6), False), ((99, 21, 6), (6, 21, 6), False), ((90, 46, 6), (15, 7.5, 6), False),
           ((92, 52, 5), (8, 3.5, 5), False), ((88, 42, 12), (10, 3.2, 5), False), ((90, 10, 6), (8, 10, 6), False),
           ((90, 31, 7), (5, 7, 9), True)],
    # R7 GSP mound (x 95-113, top 24, z 15-30)
    "R7": [((104, 8, 22), (9.5, 9, 7.5), False), ((103, 16, 21), (7, 7.5, 6), False), ((109, 6, 26), (4, 4, 4), False)],
    # R8 far-right rubble (x 112-119.5, top 21, z 16-30)
    "R8": [((116, 7, 23), (4.5, 7, 6), False), ((117, 15, 21), (3.5, 5.5, 4), False), ((114, 4, 28), (3, 2.5, 3), False)],
}
# tunnels / holes: chains of negative balls (center, radius)
TUNNELS = {
    "eel_den": [((69, 5.5, 23), 4.2), ((71, 5.8, 21), 4.3), ((73, 6, 19), 4.5), ((75, 6.5, 17), 4.5), ((77, 7, 15), 4.5),
                ((78.5, 7.8, 13), 4.3), ((80, 8.5, 11), 4.2), ((83, 9.5, 8), 4.0), ((86, 9.5, 5), 3.8)],
    "blenny_hole": [((25.0, 18, 24.0), 2.1), ((23.4, 17.5, 22.0), 2.0), ((21.8, 17, 20.0), 1.9)],
    "tailspot_hole": [((51.5, 14, 26.0), 1.2), ((51, 14, 24.0), 1.1), ((50.5, 14, 22.0), 1.0)],
    "crevice_tang": [((36, 25, 7), 2.4), ((33, 26, 5), 2.2)],
}

# ----------------------------------------------------------------------------
# anchors (§3.4). snap: "surface" = move to the nearest rock point (+ normal from the surface);
# None = free-water / sand / glass point kept as given.
# ----------------------------------------------------------------------------
ANCHORS = [
    ("HOST_KENYA", "HOST", (72, 40, 15), dict(radius=10), None),
    ("HOST_GSP_RIGHT", "HOST", (104, 14, 24), dict(radius=8), "surface"),
    ("CAVE_EEL", "CAVE_EEL", (69, 5, 23), dict(dir=[0.5, 0.1, -0.85]), None),
    ("CAVE_SMALL", "CAVE", (63, 12, 8), {}, None),
    ("CAVE_BIG", "CAVE", (42, 18, 6), {}, None),
    ("HOLE_BLENNY", "HOLE", (25, 18, 24), dict(dir=[0.6, 0.2, 0.78], depth=6, radius=1.8, species="lawnmower_blenny"), None),
    ("HOLE_TAILSPOT", "HOLE", (51.5, 14, 26), dict(dir=[0.15, 0.0, 1.0], depth=5, radius=1.1, species="tailspot_blenny"), None),
    ("PERCH_1", "PERCH", (19, 33, 20), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_2", "PERCH", (49, 29, 21), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_3", "PERCH", (92, 54, 8), dict(normal=[0.2, 1, 0.3]), "surface"),
    ("PERCH_4", "PERCH", (70, 38, 20), dict(normal=[0, 0.8, 0.6]), "surface"),
    ("PERCH_5", "PERCH", (104, 25, 20), dict(normal=[0, 1, 0.2]), "surface"),
    ("STATION_CLEANER", "STATION", (62, 20, 15), dict(species="wrasse"), None),
    ("STATION_SKUNK", "STATION", (66, 22, 16), dict(species="skunk_shrimp"), "surface"),
    ("SHRIMP_LEDGE", "LEDGE", (88, 38, 15), dict(normal=[0, -1, 0.3]), "surface"),
    ("CAVE_DOTTYBACK", "CAVE", (90, 31, 9), dict(species="dottyback"), None),
    ("SLEEP_TANG", "SLEEP", (36, 25, 7), dict(dir=[-0.7, 0.2, -0.7]), None),
    ("SURFACE_FEED", "FEED", (41, 55, 20), {}, None),
    ("NORI_CLIP", "FEED", (2, 35, 18), {}, None),
    ("HOB_OUTFLOW", "FLOW", (71, 55, 1), {}, None),
    ("GRAZE_1", "GRAZE", (12, 20, 30), {}, "surface"), ("GRAZE_2", "GRAZE", (30, 40, 12), {}, "surface"),
    ("GRAZE_3", "GRAZE", (44, 8, 27), {}, "surface"), ("GRAZE_4", "GRAZE", (52, 30, 9), {}, "surface"),
    ("GRAZE_5", "GRAZE", (80, 20, 25), {}, "surface"), ("GRAZE_6", "GRAZE", (96, 45, 6), {}, "surface"),
    ("GRAZE_7", "GRAZE", (110, 12, 28), {}, "surface"), ("GRAZE_8", "GRAZE", (60, 4, 30), {}, "surface"),
    ("BRITTLE_HIDE", "HIDE", (35, 4, 10), dict(species="brittle_star"), None),
    ("DUSTER_BASE", "SESSILE", (85, 3, 21), dict(species="feather_duster"), None),
    ("SAND_FRONT", "ZONE", (60, 3, 29), dict(box=[8, 25, 115, 32], species="nassarius,hermit"), None),
    ("coral_kenya_main", "CORAL", (72, 38, 15), dict(normal=[0, 1, 0.2]), "surface"),
    ("coral_gsp_upper_left", "CORAL", (21, 42, 12), dict(normal=[0.3, 0.6, 0.75]), "surface"),
    ("coral_gsp_lower_right", "CORAL", (105, 12, 22), dict(normal=[0, 0.7, 0.7]), "surface"),
    ("coral_zoa_front_center", "CORAL", (71, 4, 28), dict(normal=[0, 1, 0]), None),
    ("coral_clove_kenya_base", "CORAL", (77, 18, 22), dict(normal=[0.6, 0.5, 0.6]), "surface"),
    ("coral_shroom_front_left", "CORAL", (53, 3.2, 28), dict(normal=[0, 1, 0]), None),
    ("coral_macro_back_left", "CORAL", (47, 36, 10), dict(normal=[0.2, 0.5, 0.8]), "surface"),
    ("coral_organpipe_kenya_base", "CORAL", (68, 37, 14), dict(normal=[-0.3, 0.9, 0.3]), "surface"),
]
ALIASES = [("HOLE_LAWNMOWER", "HOLE_BLENNY", None), ("STATION_SHRIMP", "STATION_SKUNK", None),
           ("DUSTER_TUBE", "DUSTER_BASE", "TUBE"), ("BRITTLE_HOME", "BRITTLE_HIDE", "CAVE")]
DEN_PATH = [[69, 5, 23], [73, 6, 19], [77, 7, 15], [80, 8.5, 11], [83, 9.5, 8], [86, 9.5, 5]]


def rock_region_masks(p):
    """Per-point weights for regional coloring (p: (N,3) world)."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    r1_right = S(36, 42, x) * (1 - S(55, 58, x)) * S(26, 32, y) * (z < 18)
    r6 = S(76, 80, x) * (1 - S(104, 108, x)) * (z < 16)
    r8 = S(110, 113, x)
    r3_top = S(42, 44, x) * (1 - S(55, 57, x)) * S(16, 22, y) * (z > 13)
    r5_front = S(63, 65, x) * (1 - S(80, 82, x)) * S(16, 20, z) * S(4, 8, y)
    return r1_right, r6, r8, r3_top, r5_front


# ----------------------------------------------------------------------------
def metaball_mesh(resolution=0.7):
    mb = bpy.data.metaballs.new("RockMB")
    mb.resolution = resolution
    mb.render_resolution = resolution
    mb.threshold = 0.6
    k = 1.0 / 1.13

    import mathutils

    def add(center, radii, neg, stiff=2.0, yaw=0.0, tilt=0.0):
        e = mb.elements.new(type='ELLIPSOID')
        e.co = w2b(center)
        e.radius = 2.0
        e.size_x, e.size_y, e.size_z = radii[0] * k, radii[2] * k, radii[1] * k
        e.stiffness = stiff
        e.use_negative = neg
        if yaw or tilt:
            e.rotation = (mathutils.Euler((tilt, 0, yaw), 'XYZ')).to_quaternion()

    rs = np.random.default_rng(1234)
    for rock, els in ROCKS.items():
        for c, r, neg in els:
            add(c, r, neg)
            if neg:
                continue
            # break the smooth metaball silhouette: flattened sub-rocks ("plates") stuck on the surface
            for _ in range(5):
                a = rs.uniform(0, 2 * math.pi)
                el = rs.uniform(-0.5, 0.9)
                d = np.array([math.cos(a) * math.cos(el), math.sin(el), math.sin(a) * math.cos(el)])
                if c[2] - r[2] < 1.0 and d[2] < 0:  # keep the back face flush with the glass
                    d[2] = -d[2]
                cc = np.array(c) + d * np.array(r) * rs.uniform(0.6, 0.85)
                m = min(r) * rs.uniform(0.35, 0.6)
                rr = (m * rs.uniform(1.0, 1.6), m * rs.uniform(0.45, 0.7), m * rs.uniform(0.9, 1.3))
                add(tuple(cc), rr, False, 2.0, yaw=rs.uniform(0, math.pi), tilt=rs.uniform(-0.3, 0.3))
            # small crevices
            for _ in range(2):
                a = rs.uniform(0, 2 * math.pi)
                d = np.array([math.cos(a), rs.uniform(-0.3, 0.3), abs(math.sin(a))])
                cc = np.array(c) + d * np.array(r) * 0.95
                m = min(r) * rs.uniform(0.18, 0.3)
                add(tuple(cc), (m * 1.6, m * 0.5, m), True, 2.0, yaw=rs.uniform(0, math.pi))
    for name, chain in TUNNELS.items():
        for c, r in chain:
            add(c, (r, r, r), True, 2.0)
    o = bpy.data.objects.new("RockMB", mb)
    C.link(o)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(o.evaluated_get(dg))
    bpy.data.objects.remove(o)
    ro = bpy.data.objects.new("RockHigh", me)
    C.link(ro)
    return ro


def clip_below(obj, y_world=0.4):
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    geom = bm.verts[:] + bm.edges[:] + bm.faces[:]
    # world y = blender z
    res = bmesh.ops.bisect_plane(bm, geom=geom, plane_co=(0, 0, y_world), plane_no=(0, 0, 1), clear_inner=True)
    bm.to_mesh(obj.data)
    bm.free()
    obj.data.update()


def voxel_remesh(obj, size):
    md = obj.modifiers.new("remesh", 'REMESH')
    md.mode = 'VOXEL'
    md.voxel_size = size
    md.adaptivity = 0.0
    C.apply_modifiers(obj)


def vertex_normals(obj):
    me = obj.data
    n = np.zeros(len(me.vertices) * 3)
    me.vertices.foreach_get("normal", n)
    return n.reshape(-1, 3)


def displace(obj, seed=5):
    Vb = C.get_verts(obj)
    Nb = vertex_normals(obj)
    pw = np.stack([Vb[:, 0], Vb[:, 2], -Vb[:, 1]], -1)  # world
    t = time.time()
    # lumps: Voronoi F1 (6 cm cells) -> bumps 1.5 cm
    f1, f2 = N.worley3(pw, 6.0, seed)
    lumps = (1 - np.clip(f1 / 6.0, 0, 1)) - 0.45
    # cauliflower knobs at 2.5 cm on all rock (stronger on R2/R3 pillars)
    k1, _ = N.worley3(pw, 2.6, seed + 7)
    knobs = (1 - np.clip(k1 / 2.6, 0, 1)) ** 1.5 - 0.3
    pil = (S(10, 13, pw[:, 0]) * (1 - S(26, 29, pw[:, 0])) + S(42, 44, pw[:, 0]) * (1 - S(55, 57, pw[:, 0]))) \
        * S(12, 15, pw[:, 2])
    # pits: fBm 1.5 cm
    fb = N.fbm3(pw, 1.5, 4, seed + 3) - 0.5
    # crackle / pores (F2-F1 at 2 cm)
    c1, c2 = N.worley3(pw, 2.0, seed + 11)
    crack = 1 - S(0.0, 0.25, (c2 - c1) / 2.0)
    strata = (N.fbm3(pw * np.array([1.0, 4.0, 1.0]), 6.0, 3, seed + 13) - 0.5)
    d = 1.9 * lumps + (0.55 + 0.45 * pil) * 1.0 * knobs + 0.5 * fb * 2 - 0.35 * crack + 1.2 * strata
    # flatten displacement where the rock touches the back glass (keeps the back wall flush)
    d *= S(0.5, 2.0, pw[:, 2])
    Vb = Vb + Nb * d[:, None]
    print(f"[rock] displace {len(Vb)} verts in {time.time() - t:.1f}s")
    obj.data.vertices.foreach_set("co", Vb.ravel())
    obj.data.update()


def pits(obj, count=150, seed=9):
    """Pores: spherical dents pushed in along -normal (the boolean-hole look without booleans)."""
    Vb = C.get_verts(obj)
    Nb = vertex_normals(obj)
    pw = np.stack([Vb[:, 0], Vb[:, 2], -Vb[:, 1]], -1)
    nw = np.stack([Nb[:, 0], Nb[:, 2], -Nb[:, 1]], -1)
    rs = np.random.default_rng(seed)
    w = 0.3 + np.clip(nw[:, 1], 0, 1) + np.clip(nw[:, 2], 0, 1) * 0.8
    w *= pw[:, 1] > 4.0
    w = w / w.sum()
    idx = rs.choice(len(Vb), count, p=w)
    from mathutils.kdtree import KDTree
    kd = KDTree(len(Vb))
    for i, v in enumerate(Vb):
        kd.insert(v, i)
    kd.balance()
    for i in idx:
        r = rs.uniform(0.6, 1.5)
        depth = r * rs.uniform(0.8, 1.6)
        for (co, j, dist) in kd.find_range(Vb[i], r):
            prof = math.sqrt(max(r * r - dist * dist, 0)) / r
            Vb[j] = Vb[j] - Nb[i] * depth * prof
    obj.data.vertices.foreach_set("co", Vb.ravel())
    obj.data.update()


def build_high(resolution=0.7, voxel=0.55):
    t = time.time()
    o = metaball_mesh(resolution)
    voxel_remesh(o, voxel)
    displace(o)
    pits(o)
    clip_below(o, 0.4)
    print(f"[rock] high: {len(o.data.polygons)} faces in {time.time() - t:.1f}s")
    return o


def make_low(high, target=40000):
    me = high.data.copy()
    low = bpy.data.objects.new("Rock", me)
    C.link(low)
    tris = sum(len(p.vertices) - 2 for p in me.polygons)
    md = low.modifiers.new("dec", 'DECIMATE')
    md.ratio = min(1.0, target / tris * 0.98)
    md.use_collapse_triangulate = True
    C.apply_modifiers(low)
    C.triangulate(low)
    me = low.data
    me.polygons.foreach_set("use_smooth", np.ones(len(me.polygons), bool))
    me.update()
    return low


def smart_uv(obj):
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.002)
    bpy.ops.object.mode_set(mode='OBJECT')


# ----------------------------------------------------------------------------
# baking
# ----------------------------------------------------------------------------
def _bake_image(name, size, float_buf=False, noncolor=True):
    img = bpy.data.images.new(name, size, size, alpha=False, float_buffer=float_buf)
    if noncolor:
        img.colorspace_settings.name = 'Non-Color'
    return img


def _bake_material(obj, img, emit_from=None):
    m = bpy.data.materials.new("bake_" + img.name)
    m.use_nodes = True
    nt = m.node_tree
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = img
    nt.nodes.active = tex
    if emit_from is not None:
        geo = nt.nodes.new("ShaderNodeNewGeometry")
        em = nt.nodes.new("ShaderNodeEmission")
        nt.links.new(geo.outputs[emit_from], em.inputs[0])
        out = nt.nodes.get("Material Output")
        nt.links.new(em.outputs[0], out.inputs[0])
    obj.data.materials.clear()
    obj.data.materials.append(m)
    return m


def img_to_np(img):
    w, h = img.size
    a = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(a)
    return a.reshape(h, w, 4)[::-1]  # row 0 = top


def bake_maps(high, low, size_albedo=2048, size_maps=1024, ao_samples=24):
    s = bpy.context.scene
    s.render.engine = 'CYCLES'
    s.cycles.device = 'CPU'
    s.render.threads_mode = 'FIXED'
    s.render.threads = os.cpu_count() or 4
    s.render.bake.margin = 8
    out = {}
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    bpy.context.view_layer.objects.active = low
    low.select_set(True)
    high.hide_render = True
    # position + world normal (emit bakes) at albedo resolution
    s.cycles.samples = 1
    for key, sock in (("pos", "Position"), ("nrm", "Normal")):
        img = _bake_image(f"rock_{key}", size_albedo, float_buf=True)
        _bake_material(low, img, emit_from=sock)
        t = time.time()
        bpy.ops.object.bake(type='EMIT', margin=8)
        out[key] = img_to_np(img)[..., :3].copy()
        print(f"[rock] bake {key} {time.time() - t:.1f}s")
    # AO
    s.cycles.samples = ao_samples
    img = _bake_image("rock_ao", size_maps)
    _bake_material(low, img)
    t = time.time()
    bpy.ops.object.bake(type='AO', margin=8)
    out["ao"] = img_to_np(img)[..., 0].copy()
    print(f"[rock] bake ao {time.time() - t:.1f}s")
    # tangent-space normal from the high mesh
    high.hide_render = False
    s.cycles.samples = 1
    img = _bake_image("rock_normal", size_maps)
    _bake_material(low, img)
    high.select_set(True)
    low.select_set(True)
    bpy.context.view_layer.objects.active = low
    t = time.time()
    bpy.ops.object.bake(type='NORMAL', normal_space='TANGENT', use_selected_to_active=True, cage_extrusion=0.6,
                        max_ray_distance=1.2, margin=8)
    out["normal"] = img_to_np(img)[..., :3].copy()
    print(f"[rock] bake normal {time.time() - t:.1f}s")
    high.select_set(False)
    return out


def blender_to_world_arr(p):
    return np.stack([p[..., 0], p[..., 2], -p[..., 1]], -1)


def paint_albedo(maps, size):
    pos = blender_to_world_arr(maps["pos"].reshape(-1, 3))
    nrm = blender_to_world_arr(maps["nrm"].reshape(-1, 3))
    valid = np.linalg.norm(nrm, axis=1) > 0.5
    n = len(pos)
    ao = maps["ao"]
    # upsample AO to the albedo size
    f = size // ao.shape[0]
    ao_up = np.kron(ao, np.ones((f, f), np.float32)).reshape(-1)
    rgb = np.zeros((n, 3), np.float32)
    alpha = np.zeros(n, np.float32)
    pv = pos[valid]
    nv = nrm[valid]
    aov = ao_up[valid]
    hx = lambda h: C.hex2rgb(h)[None, :]
    base_noise = N.fbm3(pv, 8.0, 4, 101)
    fine = N.fbm3(pv, 0.8, 3, 102)
    tan = hx("#D8CBB0") * (0.88 + 0.24 * base_noise[:, None])
    crev = hx("#8A7C64")
    col = tan * (1 - (1 - aov[:, None]) * 0.0)
    cmix = np.clip((1 - aov) * 1.2, 0, 1)[:, None]
    col = col * (1 - cmix) + crev * cmix
    up = np.clip(nv[:, 1], 0, 1)
    # green-brown film on upward/front faces (this tank's rock reads green-brown)
    film_n = N.fbm3(pv, 4.0, 4, 103)
    film = np.clip(S(0.35, 0.6, film_n) * (0.35 + 0.65 * S(-0.2, 0.6, nv[:, 1])), 0, 1) * 0.6
    col = col * (1 - film[:, None]) + (hx("#6E7442") * (0.85 + 0.3 * fine[:, None])) * film[:, None]
    # darker olive in lower crevices
    col = col * (1 - 0.35 * (1 - aov[:, None]) * film[:, None])
    r1_right, r6, r8, r3_top, r5_front = rock_region_masks(pv)
    # fresh pale rock: R3 upper half, R5 front
    fresh = np.clip(np.maximum(r3_top, r5_front) * S(0.3, 0.7, N.fbm3(pv, 3.0, 3, 104)), 0, 1)[:, None] * 0.7
    col = col * (1 - fresh) + hx("#E8E2D4") * fresh
    # coralline patches (pink / purple), mostly on R1 right half, R6, R8
    cor_n = N.fbm3(pv, 3.0, 4, 105)
    region = np.clip(0.25 + 0.75 * np.maximum.reduce([r1_right, r6 * 0.8, r8]), 0, 1)
    coral_m = S(0.68, 0.74, cor_n * (0.8 + 0.35 * region)) * (0.5 + 0.5 * S(-0.3, 0.3, nv[:, 2]))
    which = N.value3(pv, 6.0, 106) > 0.35
    ccol = np.where(which[:, None], hx("#D46A8A"), hx("#7F5CB5"))
    ccol = np.where((r8 > 0.5)[:, None], np.where(which[:, None], hx("#B5294F"), hx("#7F5CB5")), ccol)
    col = col * (1 - coral_m[:, None] * 0.9) + ccol * coral_m[:, None] * 0.9
    # hair algae mask -> alpha (shader scales by uAlgaeBloom); tufts mainly on up-facing upper rock
    hair = S(0.55, 0.7, N.fbm3(pv, 5.0, 4, 107)) * S(0.0, 0.7, nv[:, 1]) * S(8, 20, pv[:, 1])
    hair = np.maximum(hair, 0.6 * S(0.6, 0.75, N.fbm3(pv, 2.5, 3, 108)) * S(-0.2, 0.4, nv[:, 2]))
    # baked-in present-day algae film (the photo shows quite a bit of hair algae on the upper rock)
    col = col * (1 - 0.35 * hair[:, None]) + hx("#3B5B2E") * 0.35 * hair[:, None]
    col *= (0.78 + 0.22 * aov)[:, None]  # runtime multiplies the AO map again: keep the baked share small
    col *= (0.93 + 0.14 * fine)[:, None]
    rgb[valid] = np.clip(col, 0, 1)
    alpha[valid] = np.clip(hair, 0, 1)
    rough = np.full(n, 0.85, np.float32)
    rough[valid] = 0.85 + 0.1 * (N.fbm3(pv, 2.0, 2, 109) - 0.5) * 2 - 0.1 * coral_m
    return rgb.reshape(size, size, 3), alpha.reshape(size, size), rough.reshape(size, size), valid.reshape(size, size)


def dilate(img, valid, iters=6):
    """Fill invalid texels with neighbor averages (padding beyond the bake margin)."""
    img = img.copy()
    v = valid.astype(np.float32)
    for _ in range(iters):
        acc = np.zeros_like(img)
        cnt = np.zeros(v.shape, np.float32)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            acc += np.roll(np.roll(img * v[..., None] if img.ndim == 3 else img * v, dy, 0), dx, 1)
            cnt += np.roll(np.roll(v, dy, 0), dx, 1)
        fill = (v == 0) & (cnt > 0)
        if img.ndim == 3:
            img[fill] = acc[fill] / cnt[fill][:, None]
        else:
            img[fill] = acc[fill] / cnt[fill]
        v = np.maximum(v, fill.astype(np.float32))
    return img


# ----------------------------------------------------------------------------
def snap_anchors(low):
    from mathutils.bvhtree import BVHTree
    dg = bpy.context.evaluated_depsgraph_get()
    bvh = BVHTree.FromObject(low, dg)
    out = []
    report = []
    for name, typ, pos, extra, snap in ANCHORS:
        a = {"name": name, "type": typ}
        p = list(pos)
        if snap == "surface":
            loc, nrm, idx, dist = bvh.find_nearest(w2b(pos))
            if loc is not None:
                pw = C.b2w(loc)
                nw = C.b2w(nrm)
                p = [round(v, 2) for v in pw]
                extra = dict(extra)
                if "normal" in extra or typ in ("PERCH", "CORAL", "LEDGE", "GRAZE"):
                    extra["normal"] = [round(v, 3) for v in nw]
                report.append((name, round(dist, 2)))
        a["pos"] = [round(float(v), 2) for v in p]
        for k, v in extra.items():
            a[k] = v
        out.append(a)
    out.insert(3, {"name": "den_path", "type": "PATH", "points": DEN_PATH})
    # aliases used by the simulation track (core DefaultScape) so it never falls back to its analytic defaults
    by = {a["name"]: a for a in out}
    for alias, src, typ in ALIASES:
        if src in by:
            b = dict(by[src])
            b["name"] = alias
            b["type"] = typ or b["type"]
            b["aliasOf"] = src
            out.append(b)
    return out, report


def write_anchors(anchors):
    path = os.path.join(C.SIM, "anchors.json")
    with open(path, "w") as f:
        f.write('{"anchors": [\n')
        f.write(",\n".join("  " + json.dumps(a) for a in anchors))
        f.write("\n]}\n")
    return path


def build(bake=True, albedo_size=2048, verbose=True):
    C.reset_scene()
    t0 = time.time()
    high = build_high()
    low = make_low(high, 40000)
    smart_uv(low)
    tris = sum(len(p.vertices) - 2 for p in low.data.polygons)
    print(f"[rock] low: {tris} tris")
    if bake:
        maps = bake_maps(high, low, albedo_size, 1024)
        rgb, alpha, rough, valid = paint_albedo(maps, albedo_size)
        rgb = dilate(rgb, valid, 8)
        alpha = dilate(alpha, valid, 8)
        from textures import quantize
        albedo = np.concatenate([quantize(rgb, 6, 1), (np.clip(alpha, 0, 1) * 255).astype(np.uint8)[..., None]], -1)
        apath = os.path.join(C.BUILD, "rock_albedo.png")
        C.write_png(apath, albedo)
        nmap = maps["normal"]
        C.write_png(os.path.join(C.TEXTURES, "rock_normal.png"), quantize(nmap, 3, 2))
        f = albedo_size // 1024
        rough_s = rough.reshape(1024, f, 1024, f).mean(axis=(1, 3))
        ra = np.stack([rough_s, maps["ao"], np.zeros_like(rough_s)], -1)
        C.write_png(os.path.join(C.TEXTURES, "rock_rough_ao.png"), quantize(ra, 2, 3))
        img = C.load_image(apath, name="rock_albedo")
        mat = C.material("rock", rough=0.85, image=img)
    else:
        mat = C.material("rock", color="#B8AA90", rough=0.85)
    low.data.materials.clear()
    low.data.materials.append(mat)
    nv = len(low.data.vertices)
    cols = np.zeros((nv, 4), np.float32)
    cols[:, 3] = 1.0
    ca = low.data.color_attributes.new("Col", 'FLOAT_COLOR', 'POINT')
    ca.data.foreach_set("color", cols.ravel())
    C.set_extras(low, part="rock", anchor="rockscape")
    anchors, rep = snap_anchors(low)
    write_anchors(anchors)
    if verbose:
        print("[rock] anchor snap distances (cm):", rep)
    high.hide_set(True)
    C.export_glb(os.path.join(C.MODELS, "rockscape.glb"), [low])
    print(f"[rock] done in {time.time() - t0:.0f}s")
    return low, high


if __name__ == "__main__":
    import sys
    build(bake="--nobake" not in sys.argv)
