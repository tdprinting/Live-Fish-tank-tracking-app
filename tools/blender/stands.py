"""Tank stands for My tank's room (core custom/room/Decor.kt, docs/ECONOMY.md §10): stand_<id>.glb.

One file per stand model; each holds one node per tank build (TankBuilds ids: nano_cube_20, rimless_40,
long_reef_60, display_120), built to that build's footprint so doors and shelves keep their proportions instead of
being stretched. The renderer (render/HomeRoom.kt) draws the node matching the tank on screen.

World cm, the tank frame (x along the tank from its left glass, y up from the glass bottom, z toward the viewer from
the back glass). The stand top is at y = TOP (the tank sits on it), the floor at FLOOR. Material names carry the room
shader's kinds (room.frag, HomeRoomScene.kindOf):

  paint*   the stand's main colour (tinted by the keeper's colour pick; the material's own colour is a stand-in)
  trim*    its second colour (top slab, frame, shelves)
  metal*   handles and hinges: satin steel, or gold with the Gold handles extra
  dark*    insides, shadow gaps, the toe kick
  glow*    the Glow strip extra's LEDs (drawn only when it is on)
  window*  the reef pro's sump window (dark glass with the sump's light behind it)
  anything else keeps its own colour (a salt bucket, a test kit)

A "wood" or "plank" in the name gives the surface a procedural grain, "gloss" a polished finish (room.frag).
COLOR_0.r is a baked occlusion term (dark in recesses and near the floor), COLOR_0.g marks door faces that a tap
could open later (unused yet).

    python3 tools/blender/stands.py [ids...]   (bpy from PyPI; no Blender install needed)
"""
import math
import os
import sys

import numpy as np

import common as C
from tank import WGeo, box

TOP = -3.6
SLAB = 4.0          # top slab thickness (tank.py StandTop: -7.6 .. -3.6)
FLOOR = -82.0
# core TankBuilds: id -> (length, depth) of the glass box (cm, TankBuild.cm rounding)
BUILDS = {
    "nano_cube_20": (40.6, 40.6),
    "rimless_40": (91.4, 45.7),
    "long_reef_60": (121.9, 45.7),
    "display_120": (121.9, 61.0),
}
STANDS = ["classic", "shaker", "iron", "modern", "rustic", "reef_pro"]


class Stand:
    """Material name -> WGeo, with an occlusion value per piece."""

    def __init__(self):
        self.geo = {}
        self.n = 0

    def add(self, mat, lo, hi, ao=1.0, door=0.0):
        g = self.geo.setdefault(mat, WGeo())
        # a unique tiny inset per piece: touching pieces never share a face (no z-fighting)
        e = 0.001 + 0.0007 * (self.n % 7)
        self.n += 1
        lo = np.add(lo, e)
        hi = np.subtract(hi, e)
        V, F = box(lo, hi)
        # occlusion: darker toward the floor and in the piece's own lower edge
        floor = np.clip((V[:, 1] - FLOOR) / 18.0, 0.0, 1.0)
        a = ao * (0.72 + 0.28 * floor)
        col = np.c_[a, np.full(len(V), door), np.zeros(len(V)), np.ones(len(V))]
        g.add(V, F, col=col)

    def addv(self, mat, V, F, ao=1.0):
        g = self.geo.setdefault(mat, WGeo())
        V = np.asarray(V, float)
        floor = np.clip((V[:, 1] - FLOOR) / 18.0, 0.0, 1.0)
        a = ao * (0.72 + 0.28 * floor)
        g.add(V, F, col=np.c_[a, np.zeros(len(V)), np.zeros(len(V)), np.ones(len(V))])


def cylinder(c, r, y0, y1, n=14):
    V, F = [], []
    for i in range(n):
        a = 2 * math.pi * i / n
        V.append((c[0] + r * math.cos(a), y0, c[1] + r * math.sin(a)))
        V.append((c[0] + r * math.cos(a), y1, c[1] + r * math.sin(a)))
    for i in range(n):
        j = (i + 1) % n
        F.append((2 * i, 2 * j, 2 * j + 1, 2 * i + 1))
    F.append(tuple(2 * i for i in range(n)))
    F.append(tuple(2 * i + 1 for i in range(n))[::-1])
    return V, F


def doors_for(L):
    """How many doors across a stand of length L (cm)."""
    return 1 if L < 60 else 2 if L < 100 else 3


def top_slab(s, L, D, mat, over=2.0, t=SLAB):
    s.add(mat, (-2 - over, TOP - t, -2 - over), (L + 2 + over, TOP, D + 2 + over))


def carcass(s, L, D, mat, kick=0.0, kick_in=0.0):
    """Sides, back, floor of a cabinet from y FLOOR+kick up to under the slab; a recessed dark toe kick below."""
    y0, y1 = FLOOR + kick, TOP - SLAB
    s.add(mat, (-2, y0, -2), (0, y1, D + 2))
    s.add(mat, (L, y0, -2), (L + 2, y1, D + 2))
    s.add("dark_inside", (0, y0, -2), (L, y1, 0))
    s.add("dark_inside", (0, y0, 0), (L, y0 + 2, D + 1), ao=0.6)
    if kick > 0:
        s.add("dark_kick", (-2 + kick_in * 0.3, FLOOR, -2 + kick_in * 0.3), (L + 2 - kick_in * 0.3, y0, D + 2 - kick_in), ao=0.55)


def handle_bar(s, x, y0, y1, z, mat="metal_handle"):
    s.add(mat, (x - 0.6, y0, z), (x + 0.6, y1, z + 2.2))


def knob(s, x, y, z, mat="metal_knob"):
    s.add(mat, (x - 1.0, y - 1.0, z), (x + 1.0, y + 1.0, z + 1.8))


# ---------------------------------------------------------------- the six stands

def classic(L, D):
    """Tee's own stand (tank.py stand_geos): white, an X-lattice door on the left, open bays with shelves."""
    s = Stand()
    top_slab(s, L, D, "trim_wood_top")
    y0, y1 = FLOOR + 3, TOP - SLAB
    s.add("paint", (-2, FLOOR + 1, -2), (0, y1, D + 2))
    s.add("paint", (L, FLOOR + 1, -2), (L + 2, y1, D + 2))
    s.add("dark_inside", (0, y0, -2), (L, y1, -0.5), ao=0.8)
    s.add("paint", (0, FLOOR + 1, -0.5), (L, y0, D + 2))           # bottom board
    s.add("paint", (-2, y1 - 2, -0.5), (L + 2, y1, D + 2))          # under the top
    s.add("paint", (-2, y1 - 5, D), (L + 2, y1, D + 2))            # top face rail
    s.add("paint", (-2, FLOOR + 1, D), (L + 2, FLOOR + 6, D + 2))  # bottom face rail
    # feet
    for fx in (-2, L - 2):
        for fz in (-2, D - 2):
            s.add("paint", (fx, FLOOR, fz), (fx + 4, FLOOR + 1, fz + 4), ao=0.7)
    door_w = min(28.0, L * 0.6) if L >= 60 else L
    # the door: frame, dark mesh behind an X lattice
    dx0, dx1 = 0.0, door_w
    s.add("paint", (dx0 - 2, FLOOR + 6, D), (dx0 + 1, y1 - 5, D + 2))
    s.add("paint", (dx1 - 1, FLOOR + 6, D), (dx1 + 2, y1 - 5, D + 2))
    s.add("dark_mesh", (dx0 + 1, FLOOR + 6, D + 0.4), (dx1 - 1, y1 - 5, D + 0.6), ao=0.7)
    (ax, ay), (bx, by) = (dx0 + 1, FLOOR + 6), (dx1 - 1, y1 - 5)
    from tank import obox
    for p, q in (((ax, ay), (bx, by)), ((ax, by), (bx, ay))):
        c = ((p[0] + q[0]) / 2, (p[1] + q[1]) / 2, D + 1.2)
        d = (q[0] - p[0], q[1] - p[1], 0)
        ln = math.hypot(d[0], d[1])
        V, F = obox(c, (ln, 3.0, 1.2), d, (-d[1], d[0], 0))
        s.addv("paint", V, F)
    knob(s, dx1 - 4, (FLOOR + y1) / 2, D + 2)
    if L >= 60:
        # open bays: dividers and a shelf in each, a storage bin on the floor of the first and the shelf of the last
        bays = 2 if L >= 100 else 1
        bw = (L - dx1 - 2) / bays
        for b in range(bays):
            x0 = dx1 + 2 + b * bw
            s.add("paint", (x0 - 2, FLOOR + 1, -0.5), (x0, y1, D + 2))
            s.add("paint", (x0, -46, -0.5), (x0 + bw, -44, D + 1))
            if b == 0:
                s.add("bin_clear", (x0 + 4, FLOOR + 3, 4), (x0 + bw - 4, FLOOR + 23, D - 4), ao=0.85)
            if b == bays - 1:
                s.add("bin_clear", (x0 + 4, -44, 4), (x0 + bw - 4, -24, D - 4), ao=0.9)
    return s


def shaker(L, D):
    s = Stand()
    top_slab(s, L, D, "trim_wood_top", over=2.5)
    carcass(s, L, D, "paint_wood", kick=8, kick_in=4)
    y0, y1 = FLOOR + 8, TOP - SLAB
    s.add("paint_wood", (-2, y0, D), (L + 2, y0 + 3, D + 2))
    s.add("paint_wood", (-2, y1 - 3, D), (L + 2, y1, D + 2))
    n = doors_for(L)
    w = (L + 4) / n
    for i in range(n):
        x0, x1 = -2 + i * w, -2 + (i + 1) * w
        dy0, dy1 = y0 + 3, y1 - 3
        # rails and stiles stand proud of the recessed panel
        s.add("paint_wood", (x0, dy0, D + 0.6), (x0 + 6, dy1, D + 2.6), door=1)
        s.add("paint_wood", (x1 - 6, dy0, D + 0.6), (x1, dy1, D + 2.6), door=1)
        s.add("paint_wood", (x0 + 6, dy0, D + 0.6), (x1 - 6, dy0 + 6, D + 2.6), door=1)
        s.add("paint_wood", (x0 + 6, dy1 - 6, D + 0.6), (x1 - 6, dy1, D + 2.6), door=1)
        s.add("paint_wood", (x0 + 6, dy0 + 6, D + 0.4), (x1 - 6, dy1 - 6, D + 1.4), ao=0.9, door=1)
        s.add("dark_gap", (x1 - 0.25, dy0, D + 0.5), (x1 + 0.25, dy1, D + 2.2), ao=0.5)
        hx = x1 - 3 if i % 2 == 0 else x0 + 3
        if n == 3 and i == 1:
            hx = x0 + 3
        knob(s, hx, dy1 - 10, D + 2.6)
    return s


def iron(L, D):
    s = Stand()
    top_slab(s, L, D, "trim_wood_top", over=1.0, t=3.5)
    post = 3.8
    xs = [-2, L + 2 - post] + ([L / 2 - post / 2] if L >= 100 else [])
    for x in xs:
        for z in (-2, D + 2 - post):
            s.add("paint_metal", (x, FLOOR, z), (x + post, TOP - 3.5, z + post))
    for y in (FLOOR + 6, -44, TOP - 3.5 - post):
        s.add("paint_metal", (-2, y, -2), (L + 2, y + post, -2 + post))
        s.add("paint_metal", (-2, y, D + 2 - post), (L + 2, y + post, D + 2))
        for x in xs:
            s.add("paint_metal", (x, y, -2 + post), (x + post, y + post, D + 2 - post))
    # two wooden shelves on the lower rails
    s.add("trim_wood_shelf", (-1, FLOOR + 6 + post, -1), (L + 1, FLOOR + 6 + post + 2.5, D + 1))
    s.add("trim_wood_shelf", (-1, -44 + post, -1), (L + 1, -44 + post + 2.5, D + 1))
    # leveling feet
    for x in xs:
        for z in (-2, D + 2 - post):
            s.add("metal_feet", (x + 0.6, FLOOR - 0.01, z + 0.6), (x + post - 0.6, FLOOR + 0.8, z + post - 0.6), ao=0.6)
    # a salt bucket and a test kit box on the shelves (open frame: something to see)
    by = FLOOR + 6 + post + 2.5
    bx = min(L * 0.3, 30)
    V, F = cylinder((bx, D * 0.5), min(13, L * 0.2), by, by + 31)
    s.addv("bucket_white", V, F, ao=0.95)
    V, F = cylinder((bx, D * 0.5), min(13.6, L * 0.2 + 0.6), by + 31, by + 34)
    s.addv("bucket_lid_blue", V, F)
    ky = -44 + post + 2.5
    s.add("kit_box_orange", (L * 0.62, ky, D * 0.3), (L * 0.62 + 18, ky + 9, D * 0.3 + 12), ao=0.9)
    s.add("kit_box_white", (L * 0.62 + 2, ky + 9, D * 0.32), (L * 0.62 + 15, ky + 14, D * 0.32 + 9), ao=0.95)
    return s


def modern(L, D):
    s = Stand()
    # thin top in the paint colour, a metal edge line (gold with the extra)
    s.add("paint_gloss", (-2, TOP - 2.5, -2), (L + 2, TOP, D + 2))
    s.add("metal_edge", (-2.1, TOP - 2.9, D + 1.9), (L + 2.1, TOP - 2.5, D + 2.15))
    y1 = TOP - 2.9
    kick = 9
    s.add("paint_gloss", (-2, FLOOR + kick, -2), (0, y1, D + 2))
    s.add("paint_gloss", (L, FLOOR + kick, -2), (L + 2, y1, D + 2))
    s.add("dark_inside", (0, FLOOR + kick, -2), (L, y1, 0))
    s.add("trim_kick", (-1, FLOOR, -1), (L + 1, FLOOR + kick, D - 4), ao=0.5)
    n = doors_for(L)
    w = (L + 4) / n
    for i in range(n):
        x0, x1 = -2 + i * w, -2 + (i + 1) * w
        s.add("paint_gloss", (x0 + 0.2, FLOOR + kick, D), (x1 - 0.2, y1 - 3.2, D + 2), door=1)
        s.add("dark_gap", (x1 - 0.2, FLOOR + kick, D + 0.2), (x1 + 0.2, y1, D + 1.6), ao=0.45)
    # the finger groove along the top of the doors
    s.add("dark_groove", (-2, y1 - 3.2, D - 1), (L + 2, y1, D + 1.4), ao=0.5)
    return s


def rustic(L, D):
    s = Stand()
    # a thick plank top, plank sides, plank doors with Z braces and black strap hinges
    rng = np.random.default_rng(7)
    t = 5.0
    for k, z0 in enumerate(np.arange(-3.5, D + 3.5, 11.0)):
        z1 = min(z0 + 11.0, D + 3.5)
        s.add("paint_plank_top", (-3.5, TOP - t, z0 + 0.15), (L + 3.5, TOP - rng.uniform(0, 0.25), z1 - 0.15), ao=1.0 - 0.04 * rng.random())
    legs = 6.5
    for x in (-3, L + 3 - legs):
        for z in (-3, D + 3 - legs):
            s.add("paint_plank", (x, FLOOR, z), (x + legs, TOP - t, z + legs))
    s.add("dark_inside", (0, FLOOR + 6, -2), (L, TOP - t, 0))
    s.add("paint_plank", (-3 + legs, FLOOR + 6, -2.5), (L + 3 - legs, FLOOR + 10, D + 2.5))
    s.add("paint_plank", (-3 + legs, TOP - t - 5, D + 0.5), (L + 3 - legs, TOP - t, D + 2.5))
    for x in (-2, L):
        s.add("paint_plank", (x, FLOOR + 6, -2), (x + 2, TOP - t, D + 2))
    n = doors_for(L)
    x_lo, x_hi = -3 + legs, L + 3 - legs
    w = (x_hi - x_lo) / n
    y0, y1 = FLOOR + 10, TOP - t - 5
    from tank import obox
    for i in range(n):
        x0, x1 = x_lo + i * w, x_lo + (i + 1) * w
        planks = max(2, int(round((x1 - x0) / 10.0)))
        pw = (x1 - x0) / planks
        for p in range(planks):
            dz = rng.uniform(-0.15, 0.15)
            s.add("paint_plank", (x0 + p * pw + 0.2, y0 + 0.3, D + 0.5 + dz), (x0 + (p + 1) * pw - 0.2, y1 - 0.3, D + 2.2 + dz),
                  ao=1.0 - 0.08 * rng.random(), door=1)
        # Z brace: two rails and a diagonal, a step proud of the planks
        s.add("paint_plank", (x0 + 1, y0 + 5, D + 2.2), (x1 - 1, y0 + 10, D + 3.4), door=1)
        s.add("paint_plank", (x0 + 1, y1 - 10, D + 2.2), (x1 - 1, y1 - 5, D + 3.4), door=1)
        (ax, ay), (bx, by) = (x0 + 3, y0 + 10), (x1 - 3, y1 - 10)
        if i % 2:
            ax, bx = bx, ax
        c = ((ax + bx) / 2, (ay + by) / 2, D + 2.8)
        d = (bx - ax, by - ay, 0)
        V, F = obox(c, (math.hypot(d[0], d[1]), 4.5, 1.1), d, (-d[1], d[0], 0))
        s.addv("paint_plank", V, F)
        # strap hinges on the outer edge, a ring pull near the middle
        hx = x0 if i % 2 == 0 else x1
        sgn = 1 if i % 2 == 0 else -1
        for hy in (y0 + 7.5, y1 - 7.5):
            s.add("trim_iron", (min(hx, hx + sgn * 16), hy - 1.4, D + 3.4), (max(hx, hx + sgn * 16), hy + 1.4, D + 3.8))
        knob(s, x1 - 4 if i % 2 == 0 else x0 + 4, (y0 + y1) / 2, D + 2.2, mat="trim_iron_pull")
    return s


def reef_pro(L, D):
    s = Stand()
    s.add("paint_gloss", (-2, TOP - 3, -2), (L + 2, TOP, D + 2))
    # a glowing line under the top edge (always on: the pro cabinet's own lighting)
    s.add("glow_pro", (-1.5, TOP - 3.5, D + 1.6), (L + 1.5, TOP - 3.0, D + 2.0))
    plinth = 7
    s.add("trim_plinth", (-1, FLOOR, -1), (L + 1, FLOOR + plinth, D + 1.5), ao=0.6)
    y0, y1 = FLOOR + plinth, TOP - 3.5
    s.add("paint_gloss", (-2, y0, -2), (0, y1, D + 2))
    s.add("paint_gloss", (L, y0, -2), (L + 2, y1, D + 2))
    s.add("dark_inside", (0, y0, -2), (L, y1, 0))
    # the sump behind the window: a glass box with water, a skimmer and a return pump lit blue
    if L >= 60:
        n = 3
        w = (L + 4) / n
        wx0, wx1 = -2 + w, -2 + 2 * w
    else:
        n = 1
        w = L + 4
        wx0, wx1 = 4, L - 4
    for i in range(n):
        x0, x1 = -2 + i * w, -2 + (i + 1) * w
        if L >= 60 and i == 1:
            continue
        s.add("paint_gloss", (x0 + 0.2, y0, D), (x1 - 0.2, y1, D + 2), door=1)
        hx = x1 - 4 if i == 0 else x0 + 4
        handle_bar(s, hx, y1 - 30, y1 - 6, D + 2)
    if L >= 60:
        # the window door: frame in the paint colour, tinted glass, the sump visible inside
        s.add("paint_gloss", (wx0 + 0.2, y0, D), (wx0 + 4, y1, D + 2), door=1)
        s.add("paint_gloss", (wx1 - 4, y0, D), (wx1 - 0.2, y1, D + 2), door=1)
        s.add("paint_gloss", (wx0 + 4, y0, D), (wx1 - 4, y0 + 5, D + 2), door=1)
        s.add("paint_gloss", (wx0 + 4, y1 - 5, D), (wx1 - 4, y1, D + 2), door=1)
        s.add("window_sump", (wx0 + 4, y0 + 5, D + 1.0), (wx1 - 4, y1 - 5, D + 1.4), door=1)
        cx = (wx0 + wx1) / 2
        s.add("sump_glass", (cx - 16, y0 + 2, 6), (cx + 16, y0 + 32, D - 6), ao=0.8)
        V, F = cylinder((cx - 6, D * 0.45), 5, y0 + 2, y0 + 48)
        s.addv("sump_skimmer", V, F, ao=0.85)
        s.add("sump_pump", (cx + 6, y0 + 2, D * 0.4), (cx + 13, y0 + 9, D * 0.4 + 7), ao=0.8)
        handle_bar(s, wx1 - 7, y1 - 30, y1 - 6, D + 2)
    else:
        s.add("window_sump", (wx0, y0 + 30, D + 2.0), (wx1, y0 + 46, D + 2.2))
    return s


BUILDERS = dict(classic=classic, shaker=shaker, iron=iron, modern=modern, rustic=rustic, reef_pro=reef_pro)

# stand-in colours for the preview (the app tints paint and trim with the keeper's pick)
MATS = {
    "paint": ("#F1EDE6", 0.6), "paint_wood": ("#B98A5A", 0.55), "paint_metal": ("#1E1F22", 0.45),
    "paint_gloss": ("#F4F4F2", 0.12), "paint_plank": ("#9C8A74", 0.8), "paint_plank_top": ("#9C8A74", 0.75),
    "trim_wood_top": ("#3B2518", 0.35), "trim_wood_shelf": ("#B98A5A", 0.5), "trim_kick": ("#2A2B2E", 0.5),
    "trim_iron": ("#2B2622", 0.5), "trim_iron_pull": ("#2B2622", 0.4), "trim_plinth": ("#8A9096", 0.4),
    "metal_handle": ("#C9CCCF", 0.3), "metal_knob": ("#C9CCCF", 0.3), "metal_edge": ("#C9CCCF", 0.25),
    "metal_feet": ("#9A9DA0", 0.4),
    "dark_inside": ("#1A1A1C", 0.9), "dark_kick": ("#141416", 0.9), "dark_gap": ("#0C0C0D", 0.9),
    "dark_groove": ("#0E0E0F", 0.9), "dark_mesh": ("#2A2A2C", 0.7),
    "glow_strip": ("#7FD3FF", 0.5), "glow_pro": ("#9FE8FF", 0.5),
    "window_sump": ("#0B1C2A", 0.05),
    "bin_clear": ("#D9DEE2", 0.3), "bucket_white": ("#EEEEEA", 0.6), "bucket_lid_blue": ("#2F6FB3", 0.5),
    "kit_box_orange": ("#E8782E", 0.6), "kit_box_white": ("#F2F2EE", 0.6),
    "sump_glass": ("#5FB8D9", 0.1), "sump_skimmer": ("#2A2C30", 0.4), "sump_pump": ("#1C1D20", 0.5),
}


def glow_strip(s, L, D):
    """The Glow strip extra: LEDs under the front of the stand, aimed at the floor."""
    s.add("glow_strip", (-1, FLOOR + 0.3, D + 0.8), (L + 1, FLOOR + 1.0, D + 1.6))


def build(sid):
    C.reset_scene()
    root = C.empty("Root")
    C.set_extras(root, units="cm", top=TOP, floor=FLOOR, builds=list(BUILDS))
    mats = {}
    objs = [root]
    tris = {}
    for bid, (L, D) in BUILDS.items():
        s = BUILDERS[sid](L, D)
        glow_strip(s, L, D)
        names = sorted(s.geo)
        for name in names:
            if name not in mats:
                col, rough = MATS[name]
                mats[name] = C.material(name, col, rough=rough, metal=1.0 if name.startswith("metal") else 0.0,
                                        alpha=0.35 if name in ("bin_clear", "sump_glass") else 1.0)
        g = WGeo()
        for name in names:
            sg = s.geo[name]
            k = names.index(name)
            g.add(np.array(sg.V), sg.F, col=np.array(sg.COL), mat=k)
        o = g.obj(bid, root, "room", [mats[n] for n in names], extras=dict(length=L, depth=D))
        objs.append(o)
        tris[bid] = g.tris() * 2
    out = C.export_glb(os.path.join(C.MODELS, f"stand_{sid}.glb"))
    print(f"stand_{sid}.glb  tris per build {tris}  {os.path.getsize(out) // 1024} KB")
    return out


if __name__ == "__main__":
    ids = [a for a in sys.argv[1:] if not a.startswith("-")] or STANDS
    for sid in ids:
        build(sid)
