"""Rockscape (docs/specs/tank_design.md §2, §4; ASSETS.md §3): metaball blocking -> voxel remesh -> numpy displacement + pits ->
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
SAND_Y = 3.5  # front-glass sand height; back glass 6.0 (docs/specs/tank_design.md §1.6)
WATER_Y = 56.0
ROCK_TRIS = 35000  # spec cap 45k; kept lower for the 140k on-screen budget


def sand_base(z):
    """Base sand slope: 3.5 cm at the front glass (z 33) -> 6.0 cm at the back glass (z 0)."""
    return 3.5 + 2.5 * (33.0 - np.asarray(z, float)) / 33.0


def sand_height(x, z, d_rock):
    """Sand surface (spec §1.6 without the fBm): slope + mounds against rock feet + lanes + goby spoil.
    d_rock = horizontal distance to the nearest rock footprint edge (cm)."""
    x, z, d_rock = np.asarray(x, float), np.asarray(z, float), np.asarray(d_rock, float)
    h = sand_base(z) + 2.5 * (1 - S(0.0, 10.0, np.maximum(d_rock, 0)))
    for cx, cz, rx, rz in ((36, 24, 9, 7), (58, 26, 8, 6)):
        e = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
        h -= 0.8 * (1 - S(0.6, 1.0, e))
    e = ((x - 64) / 6.0) ** 2 + ((z - 28.5) / 7.5) ** 2  # burrow spoil fan, elongated toward +z
    h += 1.5 * (1 - S(0.2, 1.0, e))
    # keep the burrow mouth open: no rock-foot mound inside the entrance under the R5 lip (66, 25)
    k = 1 - S(2.5, 5.0, np.hypot(x - 66.0, (z - 25.0) * 1.3))
    h = h * (1 - k) + (sand_base(z) - 0.4) * k
    return h


# ----------------------------------------------------------------------------
# Blocking (docs/specs/tank_design.md §2): rock id -> (color zone, [(center, radii, negative), ...]).
# Radii are (x, y, z) world cm.
# ----------------------------------------------------------------------------
ROCKS = {
    # R0 left foot rock (T1), knobby, pressed against the pillar's left foot
    "R0": ("Z1", [((10, 6, 20), (4, 5, 5), False), ((11.5, 9, 21), (2.5, 2.5, 2.5), False)]),
    # R2 left pillar (T2): free-standing, tapers to r 4 at y 26, crown knob overhanging left (leans ~4 deg left)
    "R2": ("Z1", [((18, 5, 20), (6.5, 5, 6.5), False), ((17.7, 12, 20), (5.6, 6, 5.6), False),
                  ((17.3, 19, 20), (4.9, 6, 4.9), False), ((16.6, 25, 19.8), (4.2, 5, 4.2), False),
                  ((15, 31, 19), (5, 4, 4), False)]),
    # R1a / R1b left back stack (T2-T4) leaning on the back glass; R1b breaks the surface at x 28-34
    "R1": ("Z2", [((22, 15, 8), (11, 13, 8), False), ((19, 29, 7), (7, 6, 6), False),
                  ((26, 42, 8), (12, 11, 8), False), ((36, 46, 7), (10, 9, 7), False),
                  ((31, 54.0, 7.5), (4, 2.0, 4), False)]),
    # R4 shelf across the top-back (T4-T5), open beneath (x 44-64) through to the black back
    "R4": ("Z3", [((44, 46, 7), (10, 7, 7), False), ((54, 45, 6), (10, 6, 6), False), ((60, 42, 5), (7, 6, 5), False),
                  ((54, 40, 9), (4.5, 4, 3), False)]),
    # R3 centre pillar (T4-T5): pale stub, flat-ish top leaning right
    "R3": ("Z1b", [((49, 5, 22), (5.8, 5, 5.2), False), ((49.2, 12, 22), (4.8, 6, 4.6), False),
                   ((49.5, 19, 22), (4.6, 5, 4.4), False), ((50, 26, 22), (5, 3.5, 4), False)]),
    # R5 Kenya mound (T6) with the flat front lip over the goby burrow
    "R5": ("Z4", [((71, 10, 18), (9, 8, 8), False), ((74, 18, 15), (7, 6, 6), False), ((70, 20, 21), (6, 2.5, 5), False),
                  ((67.5, 9, 25), (5, 1.3, 2.5), False), ((73, 6, 22), (5, 4, 4), False)]),
    # R6 arch: back pier, low ledge + tall roof slab (caps the surface at x 94-98), right column
    "R6": ("Z5", [((78, 20, 4), (4, 18, 4), False), ((84, 41, 6), (8, 3, 6), False), ((96, 47, 6), (7, 8, 6), False),
                  ((96, 55, 6), (2.5, 2, 3), False), ((95, 20, 6), (6, 17, 6), False)]),
    # R9 right back stack (T9)
    "R9": ("Z6", [((105, 30, 6), (6, 14, 6), False), ((104, 46, 5), (5, 5, 5), False)]),
    # R7 GSP mound (T9): low wide dome, top 25 at (106, 25, 22)
    "R7": ("Z7", [((107, 8, 24), (9, 8.1, 9), False), ((106, 17, 22), (7, 8, 7), False)]),
    # R8 far-right corner rock (T10) + the pink coralline knob on R7's back-top
    "R8": ("Z8", [((115, 6, 25), (7, 5, 7), False), ((109, 27, 14), (4, 3, 4), False)]),
}
# carved openings (negative ellipsoids): black background visible through each from the front camera
CARVES = [
    ((34, 18, 6), (10, 11, 9)), ((42, 20, 5), (8, 10, 8)), ((30, 12, 10), (6, 7, 6)),   # CAVE_BIG
    ((54, 20, 7), (9, 14, 9)), ((60, 22, 4), (5, 12, 5)),                                 # under the R4 shelf
    ((86, 22, 6), (5, 16, 8)), ((86, 12, 10), (4, 6, 6)),                                 # ARCH
    ((102, 41, 5), (1.2, 5, 7)),                                                          # slit R9 / R6s
]
# tunnels / holes: chains of negative balls (centre, radius)
TUNNELS = {
    "goby_burrow": [((66, 4, 25), 3.5), ((68, 4.5, 22.5), 3.5), ((70, 5, 20), 3.5), ((72.5, 5.5, 17.5), 3.5),
                    ((75, 6, 15), 3.5), ((77, 6.5, 12.5), 3.5), ((79, 7, 10), 3.5), ((81, 7.5, 7.5), 3.3),
                    ((83, 8, 5), 3.2)],
    "blenny_hole": [((22 - 0.6 * k, 18 - 0.1 * k, 25 - 0.8 * k), 1.3) for k in (0, 1.5, 3, 4.5, 6)],
    "blenny_hole_2": [((94, 30 - 0.2 * k, 11 - k), 1.2) for k in (0, 1.25, 2.5, 3.75, 5)],
}
DEN_PATH = [[66, 4, 25], [70, 5, 20], [75, 6, 15], [79, 7, 10], [83, 8, 5]]

# ----------------------------------------------------------------------------
# anchors (spec §4). snap: "surface" = nearest rock point within 2.5 cm (+ normal); "sand" = y on the sand;
# None = free-water / glass point kept as given.
# ----------------------------------------------------------------------------
ANCHORS = [
    ("HOST_ARCH_CLOWNS", "HOST", (88, 24, 20), dict(radius=9, species="clown_orange,clown_darwin"), None),
    ("HOST_GSP_RIGHT", "HOST", (105, 20, 24), dict(radius=6), None),
    ("BURROW_GOBY", "CAVE_EEL", (66, 4, 25), dict(dir=[0.55, 0.1, -0.83], species="engineer_goby"), "sand"),
    ("SAND_GOBY_DIG", "SAND_ZONE", (64, 4, 28), dict(radius=6, species="engineer_goby"), "sand"),
    ("CAVE_BIG", "CAVE", (36, 14, 6), {}, None),
    ("CAVE_SHELF", "CAVE", (54, 16, 6), {}, None),
    ("CAVE_DOTTYBACK", "CAVE", (84, 12, 3), dict(species="dottyback"), None),
    ("HOLE_BLENNY", "HOLE", (22, 18, 25), dict(dir=[0.6, 0.1, 0.8], depth=6, radius=1.3, species="lawnmower_blenny"), None),
    ("HOLE_BLENNY_2", "HOLE", (94, 30, 11), dict(dir=[0, 0.2, 1], depth=5, radius=1.2, species="tailspot_blenny"), None),
    ("PERCH_R3_TOP", "PERCH", (50, 29, 22), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_R2_CROWN", "PERCH", (15, 35, 19), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_R5_LIP", "PERCH", (70, 24, 21), dict(normal=[0, 1, 0.3]), "surface"),
    ("PERCH_SHELF", "PERCH", (48, 52, 10), dict(normal=[0, 1, 0.2]), "surface"),
    ("PERCH_R9", "PERCH", (104, 51, 6), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_CORALLINE_LOW", "PERCH", (52, 10, 9), dict(normal=[0, 1, 0.6]), "surface"),
    ("STATION_CLEANER", "STATION", (40, 14, 20), dict(species="wrasse,skunk_shrimp"), None),
    ("SHRIMP_LEDGE_L", "LEDGE", (40, 36, 13), dict(normal=[0, -1, 0.3]), "surface"),
    ("SHRIMP_LEDGE_R", "LEDGE", (95, 38, 11), dict(normal=[0, -1, 0.3]), "surface"),
    ("HOVER_CARDINAL", "HOVER", (26, 20, 15), dict(radius=6, species="cardinal"), None),
    ("SLEEP_TANG", "SLEEP", (38, 12, 4), dict(dir=[-0.7, 0.2, -0.7]), None),
    ("SLEEP_WRASSE", "SLEEP", (47, 4, 18), dict(dir=[0, 0, -1]), "sand"),
    ("SURFACE_FEED", "FEED", (41, 55, 18), {}, None),
    ("NORI_CLIP", "FEED", (2, 35, 20), {}, None),
    ("HOB_OUTFLOW", "FLOW", (72, 55, 1), dict(dir=[0, -0.6, 0.8]), None),
    ("FLOW_PH_LEFT", "FLOW", (12, 44, 14), dict(dir=[1, -0.1, 0.1]), None),
    ("FLOW_PH_BACK", "FLOW", (85, 47, 4), dict(dir=[-0.3, -0.3, 0.9]), None),
    ("FLOW_PH_RIGHT", "FLOW", (111, 44, 10), dict(dir=[-1, -0.1, 0.2]), None),
    ("GRAZE_1", "GRAZE", (18, 30, 25), {}, "surface"), ("GRAZE_2", "GRAZE", (30, 46, 14), {}, "surface"),
    ("GRAZE_3", "GRAZE", (44, 20, 27), {}, "surface"), ("GRAZE_4", "GRAZE", (50, 52, 10), {}, "surface"),
    ("GRAZE_5", "GRAZE", (72, 16, 26), {}, "surface"), ("GRAZE_6", "GRAZE", (96, 46, 10), {}, "surface"),
    ("GRAZE_7", "GRAZE", (104, 38, 8), {}, "surface"), ("GRAZE_8", "GRAZE", (60, 5, 30), {}, "sand"),
    ("BRITTLE_HIDE", "HIDE", (36, 6, 8), dict(species="brittle_star"), "sand"),
    # Sally Lightfoot crab: rock crevice home in the slit between R9 and the R6 roof slab; it scuttles sideways
    # over the rock and grazes hair algae (GRAZE_*)
    ("CREVICE_SALLY", "CAVE", (101.5, 40, 9), dict(species="sally_lightfoot", dir=[0, 0, 1], grazes="GRAZE_*"),
     "surface"),
    ("DUSTER_BASE", "SESSILE", (86, 5, 21), dict(species="feather_duster"), "sand"),
    ("SAND_FRONT", "ZONE", (60, 4, 29), dict(box=[8, 25, 115, 32], species="nassarius,hermit"), "sand"),
    ("coral_kenya_main", "CORAL", (73, 24, 18), dict(normal=[0, 1, 0.2]), "surface"),
    ("coral_gsp_upper_left", "CORAL", (20, 40, 14), dict(normal=[0.3, 0.5, 0.8]), "surface"),
    ("coral_gsp_lower_right", "CORAL", (107, 16, 24), dict(normal=[0, 0.7, 0.7]), "surface"),
    ("coral_zoa_front_center", "CORAL", (66, 4, 28), dict(normal=[0, 1, 0]), "sand"),
    ("coral_paly_left_foot", "CORAL", (21, 4, 26), dict(normal=[0.2, 1, 0.3]), "sand"),
    ("coral_clove_kenya_front", "CORAL", (75, 13, 25), dict(normal=[0.3, 0.6, 0.75]), "surface"),
    ("coral_organpipe_kenya_base", "CORAL", (76, 22, 20), dict(normal=[0.2, 0.9, 0.3]), "surface"),
    ("coral_shroom_center", "CORAL", (57, 4, 27), dict(normal=[0, 1, 0]), "sand"),
    ("coral_shroom_small", "CORAL", (112, 28, 12), dict(normal=[0.1, 0.9, 0.4]), "surface"),
    ("coral_macro_back_left", "CORAL", (36, 42, 12), dict(normal=[0.2, 0.5, 0.8]), "surface"),
    ("coral_macro_back_left_2", "CORAL", (42, 48, 10), dict(normal=[0, 0.7, 0.7]), "surface"),
    ("coral_macro_lane", "CORAL", (38, 4, 28), dict(normal=[0, 1, 0]), "sand"),
    ("coral_macro_right_corner", "CORAL", (118, 7, 27), dict(normal=[0.2, 0.8, 0.5]), "surface"),
    ("coral_algae_clump_left", "CORAL", (6, 4, 24), dict(normal=[0, 1, 0]), "sand"),
    ("coral_aiptasia_1", "CORAL", (68, 25, 16), dict(normal=[0, 1, 0]), "surface"),
    ("coral_aiptasia_2", "CORAL", (31, 30, 13), dict(normal=[0, 1, 0]), "surface"),
    ("coral_aiptasia_3", "CORAL", (92, 36, 9), dict(normal=[0, 1, 0]), "surface"),
]
# compatibility names for the simulation track (anchors written before the spec v2 layout)
ALIASES = [("CAVE_EEL", "BURROW_GOBY", None), ("HOST_KENYA", "HOST_ARCH_CLOWNS", None),
           ("CAVE_SMALL", "CAVE_SHELF", None), ("HOLE_LAWNMOWER", "HOLE_BLENNY", None),
           ("HOLE_TAILSPOT", "HOLE_BLENNY_2", None), ("PERCH_1", "PERCH_R2_CROWN", None),
           ("PERCH_2", "PERCH_R3_TOP", None), ("PERCH_3", "PERCH_R9", None), ("PERCH_4", "PERCH_R5_LIP", None),
           ("PERCH_5", "PERCH_SHELF", None), ("SHRIMP_LEDGE", "SHRIMP_LEDGE_L", None),
           ("STATION_SKUNK", "STATION_CLEANER", None), ("STATION_SHRIMP", "STATION_CLEANER", None),
           ("BRITTLE_HOME", "BRITTLE_HIDE", "CAVE"), ("DUSTER_TUBE", "DUSTER_BASE", "TUBE")]

# color zones (spec §2.1): fractions (hair algae, brown film, bare, coralline) and the coralline pair
ZONES = {
    "Z1": (0.65, 0.15, 0.15, 0.05), "Z1b": (0.45, 0.15, 0.35, 0.05), "Z2": (0.55, 0.10, 0.10, 0.25),
    "Z3": (0.35, 0.15, 0.40, 0.10), "Z4": (0.50, 0.20, 0.20, 0.10), "Z5": (0.50, 0.15, 0.20, 0.15),
    "Z6": (0.60, 0.15, 0.20, 0.05), "Z7": (0.30, 0.20, 0.10, 0.40), "Z8": (0.10, 0.20, 0.00, 0.70),
}


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
    for rock, (zone, els) in ROCKS.items():
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
    for c, r in CARVES:
        add(c, r, True, 2.0)
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
    bmesh.ops.holes_fill(bm, edges=bm.edges[:], sides=0)  # close the cut: every rock is a closed solid
    bmesh.ops.triangulate(bm, faces=[f for f in bm.faces if len(f.verts) > 4])
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
    f1, f2 = N.worley3(pw, 5.0, seed)
    lumps = (1 - np.clip(f1 / 5.0, 0, 1)) - 0.45
    # cauliflower knobs at 2.5 cm on all rock (stronger on R2/R3 pillars)
    k1, _ = N.worley3(pw, 2.6, seed + 7)
    knobs = (1 - np.clip(k1 / 2.6, 0, 1)) ** 1.5 - 0.3
    pil = (S(9, 12, pw[:, 0]) * (1 - S(24, 27, pw[:, 0])) + S(42, 44, pw[:, 0]) * (1 - S(56, 58, pw[:, 0]))) \
        * S(13, 16, pw[:, 2])
    # pits: fBm 1.5 cm
    fb = N.fbm3(pw, 1.5, 4, seed + 3) - 0.5
    # crackle / pores (F2-F1 at 2 cm)
    c1, c2 = N.worley3(pw, 2.0, seed + 11)
    crack = 1 - S(0.0, 0.25, (c2 - c1) / 2.0)
    strata = (N.fbm3(pw * np.array([1.0, 4.0, 1.0]), 6.0, 3, seed + 13) - 0.5)
    d = 1.2 * 2 * lumps + (0.4 + 0.6 * pil) * 0.9 * knobs + 0.4 * fb * 2 - 0.3 * crack + 0.8 * strata
    # flatten displacement where the rock touches the back glass (keeps the back wall flush)
    d *= S(0.5, 2.0, pw[:, 2])
    Vb = Vb + Nb * d[:, None]
    print(f"[rock] displace {len(Vb)} verts in {time.time() - t:.1f}s")
    obj.data.vertices.foreach_set("co", Vb.ravel())
    obj.data.update()


def pits(obj, count=180, seed=9):
    """Pores: spherical dents pushed in along -normal (the boolean-hole look without booleans)."""
    Vb = C.get_verts(obj)
    Nb = vertex_normals(obj)
    pw = np.stack([Vb[:, 0], Vb[:, 2], -Vb[:, 1]], -1)
    nw = np.stack([Nb[:, 0], Nb[:, 2], -Nb[:, 1]], -1)
    rs = np.random.default_rng(seed)
    w = 0.3 + np.clip(nw[:, 1], 0, 1) + np.clip(nw[:, 2], 0, 1) * 0.8
    w *= pw[:, 1] > 6.0
    w = w / w.sum()
    idx = rs.choice(len(Vb), count, p=w)
    from mathutils.kdtree import KDTree
    kd = KDTree(len(Vb))
    for i, v in enumerate(Vb):
        kd.insert(v, i)
    kd.balance()
    for i in idx:
        r = rs.uniform(0.5, 1.4)
        depth = r * rs.uniform(0.8, 1.6)
        for (co, j, dist) in kd.find_range(Vb[i], r):
            prof = math.sqrt(max(r * r - dist * dist, 0)) / r
            Vb[j] = Vb[j] - Nb[i] * depth * prof
    obj.data.vertices.foreach_set("co", Vb.ravel())
    obj.data.update()


def build_high(resolution=0.7, voxel=0.6):
    t = time.time()
    o = metaball_mesh(resolution)
    voxel_remesh(o, voxel)
    displace(o)
    # only the two 1 cm caps break the surface: compress anything above y 56 into 56..57
    Vb = C.get_verts(o)
    y = Vb[:, 2]
    hi = y > 56.0
    Vb[hi, 2] = 56.0 + 1.0 * np.tanh(y[hi] - 56.0)
    o.data.vertices.foreach_set("co", Vb.ravel())
    o.data.update()
    pits(o)
    clip_below(o, 1.5)  # rock feet sink 1.5+ cm into the sand (sand 3.5-6 cm); nothing below y 1.5
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
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(low.data)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.01)  # decimation leaves near-coincident vertices
    bmesh.ops.dissolve_degenerate(bm, dist=0.002, edges=bm.edges)
    bmesh.ops.holes_fill(bm, edges=bm.edges[:], sides=0)
    bm.faces.index_update()
    # drop isolated slivers (faces whose every edge is a boundary edge) left at the y 1.5 cut
    lone = [f for f in bm.faces if all(len(e.link_faces) == 1 for e in f.edges)]
    bmesh.ops.delete(bm, geom=lone, context='FACES')
    # and tiny disconnected shells (e.g. 2-face pillows at the cut)
    seen, small = set(), []
    for f0 in bm.faces:
        if f0.index in seen:
            continue
        comp, stack = [], [f0]
        seen.add(f0.index)
        while stack:
            f = stack.pop()
            comp.append(f)
            for e in f.edges:
                for g in e.link_faces:
                    if g.index not in seen:
                        seen.add(g.index)
                        stack.append(g)
        if len(comp) < 20:
            small += comp
    bmesh.ops.delete(bm, geom=small, context='FACES')
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context='VERTS')
    bmesh.ops.triangulate(bm, faces=bm.faces[:])
    bm.to_mesh(low.data)
    bm.free()
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
    hx = lambda h: C.hex2rgb(h)[None, :]  # noqa: E731
    x, y, z = pv[:, 0], pv[:, 1], pv[:, 2]
    fine = N.fbm3(pv, 0.8, 3, 102)
    # zone of each texel = the rock element it is deepest inside (normalised ellipsoid distance)
    names, cents, radii = [], [], []
    for rock, (zone, els) in ROCKS.items():
        for c, r, neg in els:
            if not neg:
                names.append(zone)
                cents.append(c)
                radii.append(r)
    cents, radii = np.array(cents, float), np.array(radii, float)
    best = np.full(len(pv), 1e9)
    zid = np.zeros(len(pv), int)
    for k in range(len(cents)):
        dk = np.linalg.norm((pv - cents[k]) / radii[k], axis=1)
        m = dk < best
        best[m] = dk[m]
        zid[m] = k
    zone = np.array(names)[zid]
    frac = np.array([ZONES[zz] for zz in zone])  # (n, 4)
    # Z2: coralline concentrated at x 28-44, y 34-50 (the red/pink haze), less elsewhere on the stack
    hot = S(26, 30, x) * (1 - S(43, 46, x)) * S(32, 36, y) * (1 - S(49, 52, y)) * (zone == "Z2")
    frac[:, 3] = np.where(zone == "Z2", 0.12 + 0.48 * hot, frac[:, 3])
    # Z3: bare pale crest x 36-46 on the shelf top, pink boulder x 50-58 / y 36-44
    crest = S(35, 37, x) * (1 - S(45, 47, x)) * S(46, 50, y)
    frac[:, 2] = np.where(zone == "Z3", 0.2 + 0.7 * crest, frac[:, 2])
    pinkb = S(49, 51, x) * (1 - S(57, 59, x)) * S(35, 37, y) * (1 - S(43, 45, y))
    frac[:, 3] = np.where(zone == "Z3", 0.04 + 0.8 * pinkb, frac[:, 3])
    # R6c left face coralline (x 89-91, y 18-30); R6s pale crest
    r6c = S(88, 89.5, x) * (1 - S(91, 92.5, x)) * S(17, 19, y) * (1 - S(29, 31, y)) * (zone == "Z5")
    frac[:, 3] = np.maximum(frac[:, 3], 0.9 * r6c)
    r6crest = S(50, 53, y) * (zone == "Z5") * S(89, 91, x)
    frac[:, 2] = np.maximum(frac[:, 2], 0.7 * r6crest)
    # Z7 is mostly under the GSP mat: only the exposed rim reads; Z1b upper half pale
    frac /= frac.sum(1, keepdims=True)
    # one blotchy field, rank-transformed to uniform -> exact zone coverage with patchy shapes
    f = N.fbm3(pv, 3.5, 4, 103) + 0.25 * N.fbm3(pv, 1.2, 2, 113)
    uq = np.empty(len(f))
    uq[np.argsort(f)] = np.linspace(0, 1, len(f))
    cum = np.cumsum(frac, 1)
    soft = 0.1
    w_alg = 1 - S(cum[:, 0] - soft, cum[:, 0] + soft, uq)
    w_brn = S(cum[:, 0] - soft, cum[:, 0] + soft, uq) * (1 - S(cum[:, 1] - soft, cum[:, 1] + soft, uq))
    w_bare = S(cum[:, 1] - soft, cum[:, 1] + soft, uq) * (1 - S(cum[:, 2] - soft, cum[:, 2] + soft, uq))
    w_cor = S(cum[:, 2] - soft, cum[:, 2] + soft, uq)
    # bare rock faces upward (crests); algae hangs everywhere; coralline prefers front / shaded faces
    shade = np.clip((1 - aov) * 1.4, 0, 1)
    alg = hx("#5C6B2E") * (1 - shade[:, None]) + hx("#3E4A22") * shade[:, None]
    alg = alg * (0.85 + 0.3 * fine[:, None])
    brn = hx("#6E5B3A") * (0.9 + 0.2 * fine[:, None])
    which = N.value3(pv, 3.0, 106) > 0.8  # mostly tan, a little bleached white
    bare = np.where(which[:, None], hx("#ECE6D6"), hx("#D9CBB0")) * (0.92 + 0.16 * fine[:, None])
    bare = bare * 0.8 + hx("#8C8460") * 0.2  # film-dulled
    pk = N.value3(pv, 4.0, 107) > 0.4
    cor = np.where(pk[:, None], hx("#C96F8F"), hx("#7A4F9A"))
    cor = np.where((zone == "Z8")[:, None], np.where(pk[:, None], hx("#B5546F"), hx("#5E2E45")), cor)
    cor = np.where((r6c > 0.5)[:, None], hx("#C27090"), cor)
    col = alg * w_alg[:, None] + brn * w_brn[:, None] + bare * w_bare[:, None] + cor * w_cor[:, None]
    # matted hair algae softens everything: a thin olive film over all but the coralline
    film = (0.22 + 0.15 * N.fbm3(pv, 1.5, 2, 114)) * (1 - 0.7 * w_cor)
    col = col * (1 - film[:, None]) + hx("#4E5A2A") * film[:, None]
    # Z8 base maroon under everything
    col = np.where((zone == "Z8")[:, None] & (w_bare[:, None] > 0.5), hx("#5E2E45"), col)
    # cave interiors (z < 8, occluded): dark brown film with shaded purple coralline
    cave = (1 - S(5, 9, z)) * S(0.25, 0.6, 1 - aov)
    cv_col = np.where((uq > 0.75)[:, None], hx("#5A3F6B"), np.where((uq < 0.15)[:, None], hx("#3E4A22"), hx("#3A2F22")))
    col = col * (1 - cave[:, None]) + cv_col * cave[:, None]
    # AO-driven crevice darkening toward #2B2A1F
    cr = np.clip((1 - aov) * 1.1 - 0.15, 0, 1)[:, None] * 0.8
    col = col * (1 - cr) + hx("#2B2A1F") * cr
    coral_m = w_cor
    # hair algae mask -> alpha (runtime scales by uAlgaeBloom): algae patches, stronger on up / front faces
    hair = w_alg * (0.4 + 0.6 * S(-0.3, 0.6, np.maximum(nv[:, 1], nv[:, 2])))
    col *= (0.78 + 0.22 * aov)[:, None]  # runtime multiplies the AO map again: keep the baked share small
    col *= (0.93 + 0.14 * fine)[:, None]
    rgb[valid] = np.clip(col, 0, 1)
    alpha[valid] = np.clip(hair, 0, 1)
    rough = np.full(n, 0.85, np.float32)
    rough[valid] = 0.85 + 0.06 * (N.fbm3(pv, 2.0, 2, 109) - 0.5) * 2 - 0.15 * coral_m
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
        if snap == "sand":
            loc, nrm, idx, dist = bvh.find_nearest(w2b((pos[0], sand_base(pos[2]), pos[2])))
            dd = dist if loc is not None else 99.0
            p[1] = round(float(sand_height(pos[0], pos[2], dd)), 2)
            report.append((name, "sand"))
        if snap == "surface":
            loc, nrm, idx, dist = bvh.find_nearest(w2b(pos))
            if loc is not None and dist > 2.5:
                print(f"[rock] WARNING anchor {name} is {dist:.1f} cm from rock")
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
    out.insert(4, {"name": "den_path", "type": "PATH", "points": DEN_PATH})
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
    low = make_low(high, ROCK_TRIS)
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
    import tank as TK
    yv = C.get_verts(low)[:, 2]
    print(f"[rock] tris {tris}, non-manifold edges {TK.non_manifold(low)}, y range {yv.min():.2f}..{yv.max():.2f}")
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
