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
TANK = C.TANK            # (121.9, 40.6, 45.7): 48 x 16 x 18 in, open top (common.py / core TankDims.kt)
SAND_Y = C.SAND_Y        # front-glass sand height; back glass C.SAND_Y_BACK (docs/specs/tank_design.md §1.6)
WATER_Y = C.WATER_Y      # 38.1
ROCK_TRIS = 60000       # LOD0 (High); docs/ASSETS.md §3 "HQ" budget 62k
ROCK_TRIS_LOD1 = 30000  # rockscape_lod1.glb (Medium / far camera), decimated from the UV'd LOD0 so the maps still fit


def sand_base(z):
    """Base sand slope: 3.5 cm at the front glass (z = TANK[2]) -> 6.0 cm at the back glass (z 0)."""
    D = TANK[2]
    return C.SAND_Y + (C.SAND_Y_BACK - C.SAND_Y) * (D - np.asarray(z, float)) / D


def sand_height(x, z, d_rock):
    """Sand surface (spec §1.6 without the fBm): slope + mounds against rock feet + lanes + goby spoil.
    d_rock = horizontal distance to the nearest rock footprint edge (cm). Mirrored in tools/check_tank_spec.py."""
    x, z, d_rock = np.asarray(x, float), np.asarray(z, float), np.asarray(d_rock, float)
    h = sand_base(z) + 2.5 * (1 - S(0.0, 10.0, np.maximum(d_rock, 0)))
    for cx, cz, rx, rz in LANES:
        e = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
        h -= 0.8 * (1 - S(0.6, 1.0, e))
    e = ((x - SPOIL[0]) / 6.0) ** 2 + ((z - SPOIL[1]) / 7.5) ** 2  # burrow spoil fan, elongated toward +z
    h += 1.5 * (1 - S(0.2, 1.0, e))
    # keep the burrow mouth open: no rock-foot mound inside the entrance under the R5 lip
    k = 1 - S(2.5, 5.0, np.hypot(x - BURROW_MOUTH[0], (z - BURROW_MOUTH[1]) * 1.3))
    h = h * (1 - k) + (sand_base(z) - 0.4) * k
    return h


# ----------------------------------------------------------------------------
# Blocking (docs/specs/tank_design.md §2), laid out from the owner's full-front photo (2026-10-13): low, wide
# rockwork filling the back ~2/3 of the 45.7 cm depth with an open sand strip along the front glass; the stack
# rises toward the left (one cap touching the surface at x 28-34), a central arch / cave under the shelf (x 56-64)
# with the Kenya tree on the mound in front of it, a long right shelf (x 76-103) at y 24-29 over a dark recess,
# the clowns' hairy-mushroom carpet on a low purple-coralline rock in front of that recess, just under the shelf
# (x 75-89; [P p14], [P full], night clip T84), and the big lime GSP mound front right (x 97-117).
# rock id -> (color zone, [(center, radii, negative), ...]). Radii are (x, y, z) world cm.
# ----------------------------------------------------------------------------
LANES = ((26, 31, 9, 9.1), (54, 34, 8, 7.8))   # sand lanes (x, z, rx, rz): R2|R3 and R3|R5 gaps
SPOIL = (60.0, 37.0)                           # goby spoil fan centre (x, z)
BURROW_MOUTH = (62.0, 32.5)                    # goby burrow entrance under the R5 lip (x, z)
ROCKS = {
    # R0 left foot rock (T1), knobby, pressed against the pillar's left foot
    "R0": ("Z1", [((4.5, 5.2, 26.0), (4, 3.3, 6.5), False), ((6.0, 7.1, 27.3), (2.5, 1.7, 3.2), False)]),
    # R2 left pillar (T2): free-standing, tapers upward, crown knob overhanging left (top ~24)
    "R2": ("Z1", [((10, 4.5, 26.0), (6.5, 3.3, 8.5), False), ((9.7, 9.1, 26.0), (5.6, 4.0, 7.3), False),
                  ((9.3, 13.7, 26.0), (4.9, 4.0, 6.4), False), ((8.6, 17.7, 25.7), (4.2, 3.3, 5.5), False),
                  ((7, 21.7, 24.7), (5, 2.6, 5.2), False)]),
    # R1a / R1b left back stack (T2-T4) leaning on the back glass; R1b breaks the surface at x 28-34
    "R1": ("Z2", [((22, 11.1, 10.4), (11, 8.6, 10.4), False), ((19, 20.3, 9.1), (7, 4.0, 7.8), False),
                  ((26, 28.9, 10.4), (12, 7.3, 10.4), False), ((36, 31.6, 9.1), (10, 5.9, 9.1), False),
                  ((31, 36.8, 9.8), (4, 1.3, 5.2), False)]),
    # R4 shelf across the top-back (T4-T5), open beneath (x 47-67) through to the black back: the central arch
    "R4": ("Z3", [((44, 31.6, 9.1), (10, 4.6, 9.1), False), ((54, 30.9, 7.8), (10, 4.0, 7.8), False),
                  ((61, 28.9, 6.5), (7, 4.0, 6.5), False), ((54, 27.6, 11.7), (4.5, 2.6, 3.9), False)]),
    # R3 centre-left pillar (T4-T5): pale stub, flat-ish top leaning right (top ~21)
    "R3": ("Z1b", [((42, 4.5, 28.6), (5.8, 3.3, 6.8), False), ((42.2, 9.1, 28.6), (4.8, 4.0, 6.0), False),
                   ((42.5, 13.7, 28.6), (4.6, 3.3, 5.7), False), ((43, 18.4, 28.6), (5, 2.3, 5.2), False)]),
    # R5 Kenya mound (T6) in front of the arch, with the flat front lip over the goby burrow
    "R5": ("Z4", [((67, 7.8, 23.4), (9, 5.3, 10.4), False), ((70, 13.1, 19.5), (7, 4.0, 7.8), False),
                  ((66, 14.4, 27.3), (6, 1.7, 6.5), False), ((63.5, 7.8, 32.0), (5, 1.8, 3.2), False),
                  ((71, 4.6, 27.0), (3.5, 2.6, 3.8), False)]),
    # R6 right arch: back pier, the long right shelf (ledge + roof slab, y 24-29) over a dark recess, right column
    "R6": ("Z5", [((78, 14.4, 5.2), (4, 11.9, 5.2), False), ((84, 25.0, 11.0), (9, 2.2, 9.0), False),
                  ((95, 26.0, 8.0), (8, 3.0, 8.0), False), ((95, 13.0, 7.8), (6, 10.5, 7.8), False)]),
    # R9 right back stack (T9), lower than the left: top ~28
    "R9": ("Z6", [((106, 15.0, 8.0), (6.5, 9.0, 8.0), False), ((104, 25.0, 6.5), (5, 3.0, 6.0), False)]),
    # R7 GSP mound (T9): low wide dome front right under the big lime GSP mat ([P full] "big lime mound at lower right")
    "R7": ("Z7", [((107, 5.5, 33.0), (10, 5.0, 9.0), False), ((105, 10.0, 31.0), (7.5, 4.5, 7.5), False)]),
    # R8 far-right corner rock (T10) + the pink coralline knob on R9's front
    "R8": ("Z8", [((116, 5.0, 34.0), (6, 3.3, 8.0), False), ((108, 17.0, 15.0), (3.5, 2.5, 3.5), False)]),
    # R10 the clowns' rock: low purple-coralline rock in front of the right recess, under the shelf's front edge, top
    # ~11.5 at (81.5, 11.5, 21); the hairy-mushroom carpet sits on it ([P p14], [P full], night clip T84)
    "R10": ("Z8", [((82, 5.5, 22.0), (7, 4.0, 6.0), False), ((81.5, 9.0, 21.0), (5, 2.5, 4.5), False)]),
}
# carved openings (negative ellipsoids): black background visible through each from the front camera
CARVES = [
    ((34, 13.1, 7.8), (10, 7.3, 11.7)), ((42, 14.4, 6.5), (8, 6.6, 10.4)), ((30, 9.1, 13.0), (6, 4.6, 7.8)),  # CAVE_BIG
    ((56, 14.4, 9.1), (9, 9.2, 11.7)), ((62, 15.7, 5.2), (5, 7.9, 6.5)),                                 # central arch
    ((86, 13.0, 8.0), (5, 9.0, 10.4)), ((86, 9.1, 13.0), (4, 4.0, 7.8)),                                 # right recess
    ((101.5, 22.0, 7.0), (1.2, 3.3, 9.1)),                                                               # slit R9 / R6
    ((106, 7.5, 13.5), (3.8, 3.2, 4.5)), ((106, 7.0, 9.5), (2.8, 2.5, 3.0)),                             # dottyback cave (R9 foot)
]
# tunnels / holes: chains of negative balls (centre, radius)
TUNNELS = {
    "goby_burrow": [((62, 3.8, 32.5), 3.5), ((64, 4.2, 29.2), 3.5), ((66, 4.5, 26.0), 3.5), ((68.5, 4.8, 22.8), 3.5),
                    ((71, 5.2, 19.5), 3.5), ((73, 5.5, 16.2), 3.3), ((74.5, 5.8, 14.0), 3.0)],  # dead end inside R5
    # lawnmower blenny's hole in the front face of the R2 left pillar, ~59 cm from the tailspot's
    "blenny_hole": [((10 - 0.1 * k, 13 - 0.1 * k, 32.8 - 1.0 * k), 1.3) for k in (0, 1.5, 3, 4.5, 6)],
    # tailspot hole in the front of the R4 shelf's right end above the central arch: >= 50 cm from the lawnmower's
    # hole, ~28 cm from the clowns' host (outside their 22 cm defence radius), ~50 cm from the dottyback cave
    "blenny_hole_2": [((62.5, 29.5 - 0.05 * k, 13.3 - 1.0 * k), 1.5) for k in (-3, -2, -1, 0, 1.5, 3, 4.5, 6)],
}
DEN_PATH = [[62, 3.8, 32.5], [66, 4.5, 26], [71, 5.2, 19.5], [74.5, 5.8, 14]]  # a dead-end burrow ~22 cm long

# ----------------------------------------------------------------------------
# anchors (spec §4). snap: "surface" = nearest rock point within 2.5 cm (+ normal); "top" = first rock hit straight
# down at (x, z) (+ normal); "sand" = y on the sand;
# None = free-water / glass point kept as given.
# ----------------------------------------------------------------------------
ANCHORS = [
    ("HOST_ARCH_CLOWNS", "HOST", (82, 16, 21), dict(radius=9, species="clown_orange,clown_darwin"), None),
    ("HOST_GSP_RIGHT", "HOST", (107, 14, 32), dict(radius=7), None),
    ("BURROW_GOBY", "CAVE_EEL", (62, 4, 32.5), dict(dir=[0.55, 0.1, -0.83], species="engineer_goby"), "sand"),
    ("SAND_GOBY_DIG", "SAND_ZONE", (60, 4, 37), dict(radius=6, species="engineer_goby"), "sand"),
    ("CAVE_BIG", "CAVE", (36, 10.4, 7.8), {}, None),
    ("CAVE_SHELF", "CAVE", (56, 11.8, 7.8), {}, None),
    ("CAVE_DOTTYBACK", "CAVE", (106, 7.5, 13.0), dict(species="dottyback"), None),
    ("HOLE_BLENNY", "HOLE", (10, 13, 32.3), dict(dir=[0.1, 0.1, 0.99], depth=6, radius=1.3, species="lawnmower_blenny"), None),
    ("HOLE_BLENNY_2", "HOLE", (62.5, 29.5, 13.3), dict(dir=[0, 0.05, 1], depth=6, radius=1.2, species="tailspot_blenny"), None),
    # the clowns' hairy-mushroom carpet: top of the R10 rock under the shelf (corals.mushroom_hairy is built here)
    ("HOST_SHROOM", "HOST", (81.5, 13.6, 21), dict(normal=[0, 1, 0.3], radius=6, species="clown_orange,clown_darwin"),
     "surface"),
    ("PERCH_R3_TOP", "PERCH", (43, 20.5, 28.6), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_R2_CROWN", "PERCH", (7, 24.3, 24.7), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_R5_LIP", "PERCH", (66, 16, 27.3), dict(normal=[0, 1, 0.3]), "surface"),
    ("PERCH_SHELF", "PERCH", (48, 35.5, 13.0), dict(normal=[0, 1, 0.2]), "surface"),
    ("PERCH_R9", "PERCH", (104, 28, 7), dict(normal=[0, 1, 0]), "surface"),
    ("PERCH_CORALLINE_LOW", "PERCH", (57, 8.5, 20.5), dict(normal=[0, 1, 0.6]), "surface"),
    ("STATION_CLEANER", "STATION", (38, 10, 14), dict(species="wrasse"), None),
    # the skunk cleaner's own station, on the front face of the R9 top at the right: the left cave mouth by the
    # wrasse station is the banded shrimp's ledge (SHRIMP_LEDGE_L, owner photos b_02/b_03) and the two shrimp keep
    # >= 20 cm apart (06 §2.14); this face is off the tang's front lane and outside the clowns' 22 cm host halo
    ("STATION_SKUNK", "STATION", (106, 28, 16), dict(species="skunk_shrimp", normal=[0.28, 0.35, 0.9]), None),
    ("SHRIMP_LEDGE_L", "LEDGE", (40, 24.9, 16.9), dict(normal=[0, -1, 0.3]), "surface"),
    ("SHRIMP_LEDGE_R", "LEDGE", (95, 22.5, 12), dict(normal=[0, -1, 0.3]), "surface"),
    ("HOVER_CARDINAL", "HOVER", (26, 14, 23), dict(radius=6, species="cardinal"), None),
    ("SLEEP_TANG", "SLEEP", (38, 9.1, 5.2), dict(dir=[-0.7, 0.2, -0.7]), None),
    ("SLEEP_WRASSE", "SLEEP", (47, 4, 23.4), dict(dir=[0, 0, -1]), "sand"),
    ("SURFACE_FEED", "FEED", (41, WATER_Y - 0.6, 23.4), {}, None),
    ("NORI_CLIP", "FEED", (2, 32.0, 39.0), {}, None),
    ("HOB_OUTFLOW", "FLOW", (73, WATER_Y - 0.7, 1.3), dict(dir=[0, -0.4, 0.92]), None),
    ("FLOW_PH_LEFT", "FLOW", (12.6, 28, 18), dict(dir=[1, -0.1, 0.1]), None),
    ("FLOW_PH_BACK", "FLOW", (76, 31, 4.5), dict(dir=[-0.3, -0.3, 0.9]), None),
    ("FLOW_PH_RIGHT", "FLOW", (110.8, 28, 13), dict(dir=[-1, -0.1, 0.2]), None),
    ("GRAZE_1", "GRAZE", (12, 15.6, 32), {}, "top"), ("GRAZE_2", "GRAZE", (30, 31.6, 18.2), {}, "surface"),
    ("GRAZE_3", "GRAZE", (40, 14, 34), {}, "surface"), ("GRAZE_4", "GRAZE", (50, 35.5, 13.0), {}, "surface"),
    ("GRAZE_5", "GRAZE", (68, 11, 31), {}, "surface"), ("GRAZE_6", "GRAZE", (96, 29, 10), {}, "surface"),
    ("GRAZE_7", "GRAZE", (104, 27, 9), {}, "surface"), ("GRAZE_8", "GRAZE", (60, 5, 39.0), {}, "sand"),
    ("BRITTLE_HIDE", "HIDE", (36, 6, 10.4), dict(species="brittle_star"), "sand"),
    # Sally Lightfoot crab: rock crevice home in the slit between R9 and the R6 roof slab; it scuttles sideways
    # over the rock and grazes hair algae (GRAZE_*)
    ("CREVICE_SALLY", "CAVE", (101.5, 22, 12), dict(species="sally_lightfoot", dir=[0, 0, 1], grazes="GRAZE_*"),
     "surface"),
    ("DUSTER_BASE", "SESSILE", (86, 5, 27.3), dict(species="feather_duster"), "sand"),
    ("SAND_FRONT", "ZONE", (60, 4, 40), dict(box=[8, 36, 115, 44], species="nassarius,hermit"), "sand"),
    ("coral_kenya_main", "CORAL", (69, 17, 22), dict(normal=[0, 1, 0.2]), "surface"),
    ("coral_gsp_upper_left", "CORAL", (20, 27.6, 18.2), dict(normal=[0.3, 0.5, 0.8]), "surface"),
    ("coral_gsp_lower_right", "CORAL", (107, 14, 32), dict(normal=[0, 1, 0.2]), "surface"),
    ("coral_zoa_front_center", "CORAL", (63, 4, 38), dict(normal=[0, 1, 0]), "sand"),
    ("coral_paly_left_foot", "CORAL", (14, 4, 34.5), dict(normal=[0.2, 1, 0.3]), "sand"),
    ("coral_clove_kenya_front", "CORAL", (71, 9.8, 31), dict(normal=[0.3, 0.6, 0.75]), "surface"),
    ("coral_organpipe_kenya_base", "CORAL", (72, 15, 25), dict(normal=[0.2, 0.9, 0.3]), "surface"),
    ("coral_shroom_center", "CORAL", (55, 4, 36), dict(normal=[0, 1, 0]), "sand"),
    ("coral_shroom_front_left", "CORAL", (50, 4, 38), dict(normal=[0, 1, 0]), "sand"),
    ("coral_shroom_small", "CORAL", (110, 18, 14), dict(normal=[0.1, 0.9, 0.4]), "surface"),
    ("coral_macro_back_left", "CORAL", (36, 30, 21), dict(normal=[0.2, 0.5, 0.8]), "surface"),
    ("coral_macro_back_left_2", "CORAL", (42, 32, 19.5), dict(normal=[0, 0.7, 0.7]), "surface"),
    ("coral_macro_lane", "CORAL", (31, 4, 36.4), dict(normal=[0, 1, 0]), "sand"),
    ("coral_macro_right_corner", "CORAL", (118, 5.8, 35.1), dict(normal=[0.2, 0.8, 0.5]), "surface"),
    ("coral_algae_clump_left", "CORAL", (6, 4, 35), dict(normal=[0, 1, 0]), "sand"),
    ("coral_aiptasia_1", "CORAL", (64, 17, 21), dict(normal=[0, 1, 0]), "surface"),
    ("coral_aiptasia_2", "CORAL", (31, 22, 13), dict(normal=[0, 1, 0]), "surface"),
    ("coral_aiptasia_3", "CORAL", (92, 27, 13), dict(normal=[0, 1, 0]), "surface"),
    # corals from Tee's photos (tee_tank_photos.md; built by corals.py from these snapped anchors)
    ("coral_toadstool_front", "CORAL", (60.5, 9.3, 29), dict(normal=[0, 1, 0.2]), "surface"),   # [P p04] R5 front-left foot
    ("coral_leather_pink_front", "CORAL", (57.8, 9.0, 32), dict(normal=[0, 1, 0.3]), "surface"),  # [P p04] its left foot
    ("coral_finger_leather", "CORAL", (76, 27.3, 12), dict(normal=[0, 1, 0.1]), "surface"),    # [P p14] arch's left top
    ("coral_elephant_ear", "CORAL", (41, 35.0, 16.5), dict(normal=[0, 1, 0.5]), "top"),        # [P p05] under the surface
]
# compatibility names for the simulation track (anchors written before the spec v2 layout)
ALIASES = [("CAVE_EEL", "BURROW_GOBY", None), ("HOST_KENYA", "HOST_ARCH_CLOWNS", None),
           ("CAVE_SMALL", "CAVE_SHELF", None), ("HOLE_LAWNMOWER", "HOLE_BLENNY", None),
           ("HOLE_TAILSPOT", "HOLE_BLENNY_2", None), ("PERCH_1", "PERCH_R2_CROWN", None),
           ("PERCH_2", "PERCH_R3_TOP", None), ("PERCH_3", "PERCH_R9", None), ("PERCH_4", "PERCH_R5_LIP", None),
           ("PERCH_5", "PERCH_SHELF", None), ("SHRIMP_LEDGE", "SHRIMP_LEDGE_L", None),
           ("STATION_SHRIMP", "STATION_SKUNK", None),
           ("BRITTLE_HOME", "BRITTLE_HIDE", "CAVE"), ("DUSTER_TUBE", "DUSTER_BASE", "TUBE"),
           ("coral_shroom_hairy", "HOST_SHROOM", "CORAL"), ("coral_clove_kenya_base", "coral_clove_kenya_front", None),
           ("coral_aiptasia_kenya_base", "coral_aiptasia_1", None),
           ("CREVICE_CRAB", "CREVICE_SALLY", None)]

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
            # break the smooth metaball silhouette: flattened sub-rocks ("plates") stuck on the surface, plus two
            # wide flat ledges per element (the photo's live rock is stacked slabs with overhangs, not blobs)
            for pk in range(7):
                a = rs.uniform(0, 2 * math.pi)
                el = rs.uniform(-0.5, 0.9)
                d = np.array([math.cos(a) * math.cos(el), math.sin(el), math.sin(a) * math.cos(el)])
                if c[2] - r[2] < 1.0 and d[2] < 0:  # keep the back face flush with the glass
                    d[2] = -d[2]
                cc = np.array(c) + d * np.array(r) * rs.uniform(0.6, 0.85)
                m = min(r) * rs.uniform(0.35, 0.6)
                if pk < 2:  # ledge: wide, thin, nearly horizontal
                    rr = (m * rs.uniform(1.4, 2.0), m * rs.uniform(0.28, 0.4), m * rs.uniform(1.1, 1.5))
                    tilt = rs.uniform(-0.12, 0.12)
                else:
                    rr = (m * rs.uniform(1.0, 1.6), m * rs.uniform(0.45, 0.7), m * rs.uniform(0.9, 1.3))
                    tilt = rs.uniform(-0.3, 0.3)
                add(tuple(cc), rr, False, 2.0, yaw=rs.uniform(0, math.pi), tilt=tilt)
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
        # the goby burrow runs under the dense R5 mound: a stiffer negative so it stays open (the sim needs a clear
        # bore around the mouth; soft negatives barely dent the overlapping positives there); the blenny holes likewise
        for c, r in chain:
            add(c, (r, r, r), True, 5.0)
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
    pil = (S(1, 4, pw[:, 0]) * (1 - S(16, 19, pw[:, 0])) + S(35, 37, pw[:, 0]) * (1 - S(49, 51, pw[:, 0]))) \
        * S(17, 21, pw[:, 2])
    # pits: fBm 1.5 cm, plus fine 0.6 cm grain (the porous skeletal texture of live rock)
    fb = N.fbm3(pw, 1.5, 4, seed + 3) - 0.5
    grain = N.fbm3(pw, 0.6, 2, seed + 17) - 0.5
    # crackle / pores (F2-F1 at 2 cm) and a finer 1 cm pore network
    c1, c2 = N.worley3(pw, 2.0, seed + 11)
    crack = 1 - S(0.0, 0.25, (c2 - c1) / 2.0)
    p1, p2 = N.worley3(pw, 1.0, seed + 19)
    fine_pores = (1 - S(0.0, 0.2, (p2 - p1) / 1.0)) * (N.value3(pw, 4.0, seed + 23) > 0.45)
    # horizontal strata / ledges: flattened fBm bands plus a sharp step every ~7 cm
    strata = (N.fbm3(pw * np.array([1.0, 4.0, 1.0]), 6.0, 3, seed + 13) - 0.5)
    ledge = S(0.55, 0.7, N.value3(pw * np.array([0.25, 1.0, 0.25]), 7.0, seed + 29)) * 0.8
    d = 1.2 * 2 * lumps + (0.4 + 0.6 * pil) * 0.9 * knobs + 0.4 * fb * 2 + 0.3 * grain - 0.3 * crack \
        - 0.35 * fine_pores + 0.8 * strata + ledge
    # flatten displacement where the rock touches the back glass (keeps the back wall flush)
    d *= S(0.5, 2.0, pw[:, 2])
    Vb = Vb + Nb * d[:, None]
    print(f"[rock] displace {len(Vb)} verts in {time.time() - t:.1f}s")
    obj.data.vertices.foreach_set("co", Vb.ravel())
    obj.data.update()


def pits(obj, count=560, big=40, seed=9):
    """Pores: spherical dents pushed in along -normal (the boolean-hole look without booleans).
    `count` small pores r 0.35-1.1 cm (the sponge-like holes all over live rock) + `big` holes r 1.4-2.3 cm."""
    Vb = C.get_verts(obj)
    Nb = vertex_normals(obj)
    pw = np.stack([Vb[:, 0], Vb[:, 2], -Vb[:, 1]], -1)
    nw = np.stack([Nb[:, 0], Nb[:, 2], -Nb[:, 1]], -1)
    rs = np.random.default_rng(seed)
    w = 0.3 + np.clip(nw[:, 1], 0, 1) + np.clip(nw[:, 2], 0, 1) * 0.8
    w *= pw[:, 1] > 6.0
    w = w / w.sum()
    idx = rs.choice(len(Vb), count + big, p=w)
    from mathutils.kdtree import KDTree
    kd = KDTree(len(Vb))
    for i, v in enumerate(Vb):
        kd.insert(v, i)
    kd.balance()
    for k, i in enumerate(idx):
        if k < big:
            r = rs.uniform(1.4, 2.3)
            depth = r * rs.uniform(0.7, 1.1)
        else:
            r = rs.uniform(0.35, 1.1)
            depth = r * rs.uniform(0.9, 1.6)
        for (co, j, dist) in kd.find_range(Vb[i], r):
            prof = math.sqrt(max(r * r - dist * dist, 0)) / r
            Vb[j] = Vb[j] - Nb[i] * depth * prof
    obj.data.vertices.foreach_set("co", Vb.ravel())
    obj.data.update()


def build_high(resolution=0.6, voxel=0.5):
    t = time.time()
    o = metaball_mesh(resolution)
    voxel_remesh(o, voxel)
    displace(o)
    # only the R1b cap breaks the surface: compress anything above the water line into WATER_Y..WATER_Y + 1
    Vb = C.get_verts(o)
    y = Vb[:, 2]
    hi = y > WATER_Y
    Vb[hi, 2] = WATER_Y + 1.0 * np.tanh(y[hi] - WATER_Y)
    # nothing through the glass: soft-squash x into 0.4..L-0.4 and z (blender -y) into 0.4..D-0.4
    XH, ZH = TANK[0] - 0.4, TANK[2] - 0.4
    x = Vb[:, 0]
    Vb[x > XH, 0] = XH + 0.3 * np.tanh((x[x > XH] - XH) / 0.3)
    Vb[x < 0.4, 0] = 0.4 - 0.3 * np.tanh((0.4 - x[x < 0.4]) / 0.3)
    zf = -Vb[:, 1]  # world z
    far = zf > ZH
    Vb[far, 1] = -(ZH + 0.3 * np.tanh((zf[far] - ZH) / 0.3))
    o.data.vertices.foreach_set("co", Vb.ravel())
    o.data.update()
    pits(o)
    # pits move neighbours along -n of the pit centre: undersides can push vertices up, so re-cap at WATER_Y + 1
    Vb = C.get_verts(o)
    y = Vb[:, 2]
    Vb[y > WATER_Y + 1.0, 2] = WATER_Y + 1.0
    o.data.vertices.foreach_set("co", Vb.ravel())
    o.data.update()
    clip_below(o, 1.5)  # rock feet sink 1.5+ cm into the sand (sand 3.5-6 cm); nothing below y 1.5
    print(f"[rock] high: {len(o.data.polygons)} faces in {time.time() - t:.1f}s")
    return o


def make_low(high, target=40000, name="Rock"):
    me = high.data.copy()
    low = bpy.data.objects.new(name, me)
    C.link(low)
    tris = sum(len(p.vertices) - 2 for p in me.polygons)
    md = low.modifiers.new("dec", 'DECIMATE')
    md.ratio = min(1.0, target / tris * 0.98)
    md.use_collapse_triangulate = True
    C.apply_modifiers(low)
    C.triangulate(low)
    clean_closed(low)
    return low


def clean_closed(low):
    """Weld / dissolve / hole-fill after decimation so the rock stays one closed shell (validator: 0 open edges)."""
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


def make_lod1(low, target):
    """LOD1 from the UV-mapped LOD0 (collapse decimation keeps the UV layout, so the baked maps still apply)."""
    me = low.data.copy()
    lod = bpy.data.objects.new("Rock", me)
    C.link(lod)
    tris = sum(len(p.vertices) - 2 for p in me.polygons)
    md = lod.modifiers.new("dec", 'DECIMATE')
    md.ratio = min(1.0, target / tris * 0.98)
    md.use_collapse_triangulate = True
    C.apply_modifiers(lod)
    clean_closed(lod)
    return lod


def smart_uv(obj):
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(80), island_margin=0.001)  # fewer, larger islands
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
    # tangent-space normal from the high mesh (albedo resolution: the pores live here, not in the 60k mesh)
    high.hide_render = False
    s.cycles.samples = 1
    img = _bake_image("rock_normal", size_albedo)
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
    # Z2: coralline concentrated at x 28-44, y 23-33 (the red/pink haze), less elsewhere on the stack
    hot = S(26, 30, x) * (1 - S(43, 46, x)) * S(21, 25, y) * (1 - S(33.5, 35.5, y)) * (zone == "Z2")
    frac[:, 3] = np.where(zone == "Z2", 0.12 + 0.48 * hot, frac[:, 3])
    # Z3: bare pale crest x 36-46 on the shelf top, pink boulder x 50-58 / y 25-30
    crest = S(35, 37, x) * (1 - S(45, 47, x)) * S(31.5, 34.5, y)
    frac[:, 2] = np.where(zone == "Z3", 0.2 + 0.7 * crest, frac[:, 2])
    pinkb = S(49, 51, x) * (1 - S(57, 59, x)) * S(24.3, 25.6, y) * (1 - S(29.6, 31, y))
    frac[:, 3] = np.where(zone == "Z3", 0.04 + 0.8 * pinkb, frac[:, 3])
    # R6c left face coralline (x 89-91, y 13-21); R6s pale crest
    r6c = S(88, 89.5, x) * (1 - S(91, 92.5, x)) * S(12.4, 13.7, y) * (1 - S(20.3, 21.7, y)) * (zone == "Z5")
    frac[:, 3] = np.maximum(frac[:, 3], 0.9 * r6c)
    r6crest = S(27, 29, y) * (zone == "Z5") * S(89, 91, x)
    frac[:, 2] = np.maximum(frac[:, 2], 0.7 * r6crest)
    # Z7 is mostly under the GSP mat: only the exposed rim reads; Z1b upper half pale
    frac /= frac.sum(1, keepdims=True)
    # one blotchy field, rank-transformed to uniform -> exact zone coverage with patchy shapes
    f = N.fbm3(pv, 2.4, 4, 103) + 0.35 * N.fbm3(pv, 0.9, 2, 113)  # 2-3 cm patches with ragged 1 cm edges
    uq = np.empty(len(f))
    uq[np.argsort(f)] = np.linspace(0, 1, len(f))
    cum = np.cumsum(frac, 1)
    soft = 0.1
    w_alg = 1 - S(cum[:, 0] - soft, cum[:, 0] + soft, uq)
    w_brn = S(cum[:, 0] - soft, cum[:, 0] + soft, uq) * (1 - S(cum[:, 1] - soft, cum[:, 1] + soft, uq))
    w_bare = S(cum[:, 1] - soft, cum[:, 1] + soft, uq) * (1 - S(cum[:, 2] - soft, cum[:, 2] + soft, uq))
    w_cor = S(cum[:, 2] - soft, cum[:, 2] + soft, uq)
    # Palette matched to the photos under the blue-heavy AI Prime light (tank_full / tank_left / tank_right):
    # the turf algae reads khaki-olive to brown, not green; bare rock is tan / cream with pale crests; coralline is
    # a muted mauve-purple with a few pink patches; every pore is a dark dot.
    shade = np.clip((1 - aov) * 1.4, 0, 1)
    turf_var = N.value3(pv, 2.2, 116)  # olive vs brown turf patches
    alg_lit = hx("#8A8450") * (1 - turf_var[:, None]) + hx("#7A6A40") * turf_var[:, None]
    alg = alg_lit * (1 - shade[:, None]) + hx("#4E4A2A") * shade[:, None]
    alg = alg * (0.85 + 0.3 * fine[:, None])
    brn = hx("#8E7850") * (0.9 + 0.2 * fine[:, None])
    which = N.value3(pv, 3.0, 106) > 0.8  # mostly tan, a little bleached white
    bare = np.where(which[:, None], hx("#E8E1CD"), hx("#CFC2A4")) * (0.92 + 0.16 * fine[:, None])
    bare = bare * 0.88 + hx("#A89E74") * 0.12  # film-dulled
    pk = N.value3(pv, 2.5, 107) > 0.55
    cor = np.where(pk[:, None], hx("#B07592"), hx("#6E4E80"))
    cor = np.where((zone == "Z8")[:, None], np.where(pk[:, None], hx("#A8506A"), hx("#5E2E45")), cor)
    cor = np.where((r6c > 0.5)[:, None], hx("#C27090"), cor)
    col = alg * w_alg[:, None] + brn * w_brn[:, None] + bare * w_bare[:, None] + cor * w_cor[:, None]
    # matted turf softens everything: a thin khaki film over all but the coralline
    film = (0.14 + 0.12 * N.fbm3(pv, 1.5, 2, 114)) * (1 - 0.7 * w_cor)
    col = col * (1 - film[:, None]) + hx("#6E6A3C") * film[:, None]
    # pale encrusting specks (sponge / tube-worm dots) 1.5 %
    speck = S(0.78, 0.84, N.value3(pv, 0.45, 118)) * (1 - w_cor)
    col = col * (1 - 0.6 * speck[:, None]) + hx("#E9E4D4") * 0.6 * speck[:, None]
    # Z8 base maroon under everything
    col = np.where((zone == "Z8")[:, None] & (w_bare[:, None] > 0.5), hx("#5E2E45"), col)
    # cave interiors (z < 10, occluded): dark brown film with shaded purple coralline
    cave = (1 - S(6.5, 11.7, z)) * S(0.25, 0.6, 1 - aov)
    cv_col = np.where((uq > 0.75)[:, None], hx("#5A3F6B"), np.where((uq < 0.15)[:, None], hx("#3E3A20"), hx("#3A2F22")))
    col = col * (1 - cave[:, None]) + cv_col * cave[:, None]
    # pores: the same 1 cm Worley network as the displacement, dark inside each cell wall; plus the 2 cm crackle
    p1, p2 = N.worley3(pv, 1.0, 5 + 19)
    pores = (1 - S(0.0, 0.2, (p2 - p1) / 1.0)) * (N.value3(pv, 4.0, 5 + 23) > 0.45)
    c1, c2 = N.worley3(pv, 2.0, 5 + 11)
    pores = np.maximum(pores, 0.6 * (1 - S(0.0, 0.22, (c2 - c1) / 2.0)))
    col = col * (1 - 0.45 * pores[:, None]) + hx("#2A2819") * 0.45 * pores[:, None]
    # AO-driven crevice darkening toward #2B2A1F
    cr = np.clip((1 - aov) * 1.1 - 0.15, 0, 1)[:, None] * 0.65
    col = col * (1 - cr) + hx("#2B2A1F") * cr
    coral_m = w_cor
    # hair algae mask -> alpha (runtime scales by uAlgaeBloom): algae patches, stronger on up / front faces
    hair = w_alg * (0.4 + 0.6 * S(-0.3, 0.6, np.maximum(nv[:, 1], nv[:, 2])))
    col *= (0.84 + 0.16 * aov)[:, None]  # runtime multiplies the AO map again: keep the baked share small
    col *= (0.93 + 0.14 * fine)[:, None]
    rgb[valid] = np.clip(col, 0, 1)
    alpha[valid] = np.clip(hair, 0, 1)
    rough = np.full(n, 0.85, np.float32)
    rough[valid] = 0.85 + 0.06 * (N.fbm3(pv, 2.0, 2, 109) - 0.5) * 2 - 0.15 * coral_m - 0.1 * speck + 0.05 * pores
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
        if snap == "top":  # first rock hit straight down from above the water at (x, z)
            loc, nrm, idx, dist = bvh.ray_cast(w2b((pos[0], WATER_Y + 3.0, pos[2])), w2b((0.0, -1.0, 0.0)))
            if loc is not None:
                p = [round(v, 2) for v in C.b2w(loc)]
                extra = dict(extra)
                extra["normal"] = [round(v, 3) for v in C.b2w(nrm)]
                report.append((name, "top"))
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
        albedo = np.concatenate([quantize(rgb, 8, 1), (quantize(alpha[..., None], 8, 2))], -1)
        apath = os.path.join(C.BUILD, "rock_albedo.png")
        C.write_png(apath, albedo)
        nmap = maps["normal"]
        C.write_png(os.path.join(C.TEXTURES, "rock_normal.png"), quantize(nmap, 6, 2))  # step 6: the 2048 map with pore detail compresses poorly
        f = albedo_size // 1024
        rough_s = rough.reshape(1024, f, 1024, f).mean(axis=(1, 3))
        ra = np.stack([rough_s, maps["ao"], np.zeros_like(rough_s)], -1)
        C.write_png(os.path.join(C.TEXTURES, "rock_rough_ao.png"), quantize(ra, 2, 3))
        img = C.load_image(apath, name="rock_albedo")
        mat = C.material("rock", rough=0.85, image=img)
        # LOD1 carries a half-resolution copy of the albedo (self-contained GLB, like the fish LOD1s)
        a1 = quantize(albedo.reshape(albedo_size // 2, 2, albedo_size // 2, 2, 4).mean(axis=(1, 3)) / 255.0, 12, 3)
        apath1 = os.path.join(C.BUILD, "rock_albedo_lod1.png")
        C.write_png(apath1, a1)
        mat1 = C.material("rock", rough=0.85, image=C.load_image(apath1, name="rock_albedo"))
    else:
        mat = C.material("rock", color="#B8AA90", rough=0.85)
        mat1 = mat
    import tank as TK
    high.hide_set(True)
    lod1 = make_lod1(low, ROCK_TRIS_LOD1)
    for o, m, lod in ((low, mat, 0), (lod1, mat1, 1)):
        o.data.materials.clear()
        o.data.materials.append(m)
        nv = len(o.data.vertices)
        cols = np.zeros((nv, 4), np.float32)
        cols[:, 3] = 1.0
        ca = o.data.color_attributes.new("Col", 'FLOAT_COLOR', 'POINT')
        ca.data.foreach_set("color", cols.ravel())
        C.set_extras(o, part="rock", anchor="rockscape", lod=lod)
        t_ = sum(len(p.vertices) - 2 for p in o.data.polygons)
        yv = C.get_verts(o)[:, 2]
        print(f"[rock] lod{lod}: tris {t_}, non-manifold edges {TK.non_manifold(o)}, y range {yv.min():.2f}..{yv.max():.2f}")
    anchors, rep = snap_anchors(low)
    write_anchors(anchors)
    if verbose:
        print("[rock] anchor snap distances (cm):", rep)
    # both files carry a single node named "Rock" (the runtime swaps files, not nodes)
    lod1.name = "Rock_lod1"
    low.name = "Rock"
    C.export_glb(os.path.join(C.MODELS, "rockscape.glb"), [low])
    low.name = "Rock_lod0"
    lod1.name = "Rock"
    C.export_glb(os.path.join(C.MODELS, "rockscape_lod1.glb"), [lod1])
    lod1.name = "Rock_lod1"
    low.name = "Rock"
    print(f"[rock] done in {time.time() - t0:.0f}s")
    return low, high


if __name__ == "__main__":
    import sys
    build(bake="--nobake" not in sys.argv)
