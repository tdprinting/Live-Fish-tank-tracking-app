"""The room My tank stands in (core custom/room/Decor.kt, docs/ECONOMY.md §10): room_<scene>.glb.

The tank on its stand sits against the back wall of a room; the room is drawn softly lit and a little out of focus
(room.frag) so it frames the tank without pulling the eye from it. One file per scene. Nodes, by where they move when
the tank is not Tee's 4 ft one (render/HomeRoom.kt):

  Shell     floor, walls, ceiling, baseboards: centred on the tank (shifted by half the length difference)
  Left*     props beside the left end of the stand (fixed: every tank's left glass is at x = 0); LeftTable holds the
            spot for the radio on its top (RADIO_SPOT, HomeRoomLayout.RADIO); the record stand (music_corner.py) stands
            on the floor left of it, against the back wall
  Right*    props beyond the right end (shift with the right glass). The supply shelf hangs right of the tank
            (x 134..236 on Tee's tank, y above -6): nothing tall stands there.
  Center*   props centred on the tank (pictures above it); these and the Shell turn with the room when the tank in
            view stands against another wall (RoomTanks: the room's side wall or front wall comes round behind it)
  Tank*     props that stay with the tank whichever wall it is on (the rug in front of the stand)

World cm, the tank frame (x along the tank from its left glass, y up from the glass bottom, z toward the viewer from
the back glass); the floor at FLOOR. The shell faces inward only (back faces culled: walls never hide the room when
the camera swings out past them). Material names carry room.frag's kinds (HomeRoomScene.kindOf):

  floor*   the floor (planks / tiles / carpet drawn procedurally; the material colour is its base)
  wall*    the walls (paint or a wallpaper pattern)
  ceiling* the ceiling
  lamp*    a lamp shade or bulb: glows, and lights the room at night (its position is the node's lampPos extra)
  rug*     a rug with a soft woven border
  leaf*    plant leaves (two-sided, a little translucent)
  the rest keeps its own colour (wood, fabric, pots, frames); COLOR_0.r is baked occlusion

    python3 tools/blender/rooms.py [scenes...]   (bpy from PyPI; no Blender install needed)
"""
import math
import os
import sys

import numpy as np

import common as C
from geomlib import ellipsoid
from tank import WGeo, box

L, H, D = C.TANK
CX = L / 2
FLOOR = -82.0
CEIL = 200.0
BACK = -45.0
FRONT = 460.0
HALF = 360.0                       # walls at CX ± HALF
RADIO_SPOT = (-32.0, -24.0, 22.0)  # top centre of the side table (render/HomeRoom.kt HomeRoomLayout.RADIO)
SCENES = ["living", "sunny", "beach", "cabin", "blossom", "city", "space"]   # core room/Decor.kt SCENES


class Room:
    """Node name -> (material name -> WGeo)."""

    def __init__(self):
        self.nodes = {}
        self.extras = {}

    def g(self, node, mat):
        return self.nodes.setdefault(node, {}).setdefault(mat, WGeo())

    def box(self, node, mat, lo, hi, ao=1.0, ao_floor=True):
        V, F = box(lo, hi)
        self._add(node, mat, V, F, ao, ao_floor)

    def mesh(self, node, mat, V, F, ao=1.0, ao_floor=True):
        self._add(node, mat, np.asarray(V, float), F, ao, ao_floor)

    def _add(self, node, mat, V, F, ao, ao_floor):
        a = np.full(len(V), ao, float)
        if ao_floor:
            a *= 0.7 + 0.3 * np.clip((V[:, 1] - FLOOR) / 25.0, 0.0, 1.0)
        self.g(node, mat).add(V, F, col=np.c_[a, np.zeros(len(V)), np.zeros(len(V)), np.ones(len(V))])

    def quad(self, node, mat, p0, p1, p2, p3, ao=(1, 1, 1, 1)):
        """One inward-facing quad (corners counter-clockwise seen from inside the room)."""
        V = np.array([p0, p1, p2, p3], float)
        self.g(node, mat).add(V, [(0, 1, 2, 3)], col=np.c_[ao, np.zeros(4), np.zeros(4), np.ones(4)])


def grid_quad(r, node, mat, origin, u, v, nu, nv, ao_fn=None):
    """An inward quad split nu x nv (so the per-vertex occlusion can darken its corners and edges)."""
    o, u, v = np.asarray(origin, float), np.asarray(u, float), np.asarray(v, float)
    V, F, A = [], [], []
    for j in range(nv + 1):
        for i in range(nu + 1):
            p = o + u * (i / nu) + v * (j / nv)
            V.append(p)
            A.append(ao_fn(p) if ao_fn else 1.0)
    for j in range(nv):
        for i in range(nu):
            a = j * (nu + 1) + i
            F.append((a, a + 1, a + nu + 2, a + nu + 1))
    V = np.array(V)
    A = np.array(A)
    r.g(node, mat).add(V, F, col=np.c_[A, np.zeros(len(V)), np.zeros(len(V)), np.ones(len(V))])


def corner_ao(p):
    """Soft darkening into the room's corners and along the floor and ceiling lines."""
    x0, x1 = CX - HALF, CX + HALF
    d = min(p[0] - x0, x1 - p[0], p[2] - BACK, FRONT - p[2])
    e = min(p[1] - FLOOR, CEIL - p[1])
    return float(0.62 + 0.38 * min(1.0, d / 90.0) ** 0.6 * (0.75 + 0.25 * min(1.0, e / 60.0)))


def shell(r, wall="wall_paint", floor="floor_oak", ceiling="ceiling", base="trim_baseboard"):
    x0, x1 = CX - HALF, CX + HALF
    # floor (normal up), ceiling (down), walls facing in
    grid_quad(r, "Shell", floor, (x0, FLOOR, FRONT), (x1 - x0, 0, 0), (0, 0, BACK - FRONT), 12, 8, corner_ao)
    grid_quad(r, "Shell", ceiling, (x0, CEIL, BACK), (x1 - x0, 0, 0), (0, 0, FRONT - BACK), 6, 4, corner_ao)
    grid_quad(r, "Shell", wall, (x0, FLOOR, BACK), (x1 - x0, 0, 0), (0, CEIL - FLOOR, 0), 14, 6, corner_ao)
    grid_quad(r, "Shell", wall, (x0, FLOOR, FRONT), (0, 0, BACK - FRONT), (0, CEIL - FLOOR, 0), 8, 6, corner_ao)
    grid_quad(r, "Shell", wall, (x1, FLOOR, BACK), (0, 0, FRONT - BACK), (0, CEIL - FLOOR, 0), 8, 6, corner_ao)
    grid_quad(r, "Shell", wall, (x1, FLOOR, FRONT), (x0 - x1, 0, 0), (0, CEIL - FLOOR, 0), 14, 6, corner_ao)
    # baseboards: 12 cm tall, 1.5 cm proud
    t, h = 1.5, 12.0
    r.box("Shell", base, (x0, FLOOR, BACK), (x1, FLOOR + h, BACK + t), ao=0.95)
    r.box("Shell", base, (x0, FLOOR, FRONT - t), (x1, FLOOR + h, FRONT), ao=0.95)
    r.box("Shell", base, (x0, FLOOR, BACK), (x0 + t, FLOOR + h, FRONT), ao=0.95)
    r.box("Shell", base, (x1 - t, FLOOR, BACK), (x1, FLOOR + h, FRONT), ao=0.95)


def cylinder(c, r0, r1, y0, y1, n=16):
    V, F = [], []
    for i in range(n):
        a = 2 * math.pi * i / n
        V.append((c[0] + r0 * math.cos(a), y0, c[1] + r0 * math.sin(a)))
        V.append((c[0] + r1 * math.cos(a), y1, c[1] + r1 * math.sin(a)))
    for i in range(n):
        j = (i + 1) % n
        F.append((2 * i, 2 * j, 2 * j + 1, 2 * i + 1))
    F.append(tuple(2 * i for i in range(n)))
    F.append(tuple(2 * i + 1 for i in range(n))[::-1])
    return V, F


def side_table(r, node="LeftTable", wood="wood_table"):
    """A round-cornered side table left of the stand; its top is the radio spot (every scene has one)."""
    x, y, z = RADIO_SPOT
    w, d = 36.0, 34.0
    r.box(node, wood, (x - w / 2, y - 3, z - d / 2), (x + w / 2, y, z + d / 2))
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = x + sx * (w / 2 - 3), z + sz * (d / 2 - 3)
            r.box(node, wood, (cx - 1.6, FLOOR, cz - 1.6), (cx + 1.6, y - 3, cz + 1.6))
    r.box(node, wood, (x - w / 2 + 2, FLOOR + 14, z - d / 2 + 2), (x + w / 2 - 2, FLOOR + 16, z + d / 2 - 2), ao=0.8)
    # a small stack of books on the lower shelf
    r.box(node, "book_teal", (x - 12, FLOOR + 16, z - 8), (x + 6, FLOOR + 19, z + 6), ao=0.85)
    r.box(node, "book_coral", (x - 11, FLOOR + 19, z - 7), (x + 4, FLOOR + 21.5, z + 5), ao=0.9)


def floor_lamp(r, x, z, node="LeftLamp", shade="lamp_shade", pole="metal_lamp", color=(1.0, 0.72, 0.42)):
    """Arc-free tripod floor lamp with a warm drum shade; the shade glows and lights the room at night."""
    base_y = FLOOR
    V, F = cylinder((x, z), 14, 14, base_y, base_y + 2.5, 20)
    r.mesh(node, pole, V, F, ao=0.8)
    V, F = cylinder((x, z), 1.2, 1.2, base_y + 2.5, base_y + 150, 8)
    r.mesh(node, pole, V, F)
    V, F = cylinder((x, z), 22, 18, base_y + 140, base_y + 172, 24)
    r.mesh(node, shade, V, F, ao_floor=False)
    r.extras[node] = dict(lampPos=[x, base_y + 155, z], lampColor=list(color))


def plant(r, x, z, node, height=120.0, seed=3, pot="pot_terracotta", leaf="leaf_green", n=11, size=16.0):
    """A potted plant: a tapered pot, soil, and a fan of big flattened leaves on stems."""
    rng = np.random.default_rng(seed)
    V, F = cylinder((x, z), 15, 19, FLOOR, FLOOR + 34, 18)
    r.mesh(node, pot, V, F, ao=0.95)
    V, F = cylinder((x, z), 17.5, 17.5, FLOOR + 31, FLOOR + 32.5, 18)
    r.mesh(node, "soil", V, F, ao=0.6, ao_floor=False)
    big = size
    for k in range(n):
        a = 2 * math.pi * k / n + rng.uniform(-0.3, 0.3)
        t = 0.45 + 0.55 * rng.random()
        top = np.array([x + math.cos(a) * 30 * t, FLOOR + 32 + height * (0.55 + 0.45 * t), z + math.sin(a) * 24 * t])
        base = np.array([x, FLOOR + 30, z])
        # stem
        d = top - base
        ln = np.linalg.norm(d)
        from tank import obox
        Vs, Fs = obox((base + top) / 2, (ln, 0.9, 0.9), d, (0, 0, 1) if abs(d[2]) < 0.9 * ln else (1, 0, 0))
        r.mesh(node, "leaf_stem", Vs, Fs, ao=0.8, ao_floor=False)
        # leaf: a flattened ellipsoid tipped outward at the stem's end
        size = big + 9 * rng.random()
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        up = np.array([0.0, 1.0, 0.0])
        tilt = 0.5 + 0.4 * rng.random()
        fwd = out * math.cos(tilt) + up * math.sin(tilt)
        side = np.cross(up, out)
        nrm = np.cross(fwd, side)
        R = np.c_[fwd, nrm, side]
        Vl, Fl = ellipsoid((0, 0, 0), (size, 1.2, size * 0.55), segs=10, rings=6)
        Vl = Vl @ R.T + top + fwd * size * 0.8
        r.mesh(node, leaf, Vl, Fl, ao=0.85 + 0.15 * rng.random(), ao_floor=False)


def wall_box(r, node, mat, u0, u1, y0, y1, d0, d1, wall="back"):
    """A box flat on a wall: [u0, u1] along it, [d0, d1] out from it into the room."""
    x0, x1 = CX - HALF, CX + HALF
    if wall == "back":
        lo, hi = (u0, y0, BACK + d0), (u1, y1, BACK + d1)
    elif wall == "front":
        lo, hi = (u0, y0, FRONT - d1), (u1, y1, FRONT - d0)
    elif wall == "right":
        lo, hi = (x1 - d1, y0, u0), (x1 - d0, y1, u1)
    else:
        lo, hi = (x0 + d0, y0, u0), (x0 + d1, y1, u1)
    r.box(node, mat, lo, hi, ao_floor=False)


def picture(r, x0, x1, y0, y1, node, art, frame="frame_oak", wall="back"):
    """A framed print on a wall ([x0, x1] along it): frame, mat, and the art's colour blocks (abstract sea and sand)."""
    wall_box(r, node, frame, x0, x1, y0, y1, 0, 2.5, wall)
    wall_box(r, node, "mat_white", x0 + 2.5, x1 - 2.5, y0 + 2.5, y1 - 2.5, 2.5, 2.7, wall)
    ix0, ix1, iy0, iy1 = x0 + 7, x1 - 7, y0 + 7, y1 - 7
    bands = art
    hh = (iy1 - iy0) / len(bands)
    for k, mat in enumerate(bands):
        wall_box(r, node, mat, ix0, ix1, iy0 + k * hh, iy0 + (k + 1) * hh, 2.7, 2.9, wall)


def other_walls(r, arts, frame="frame_oak"):
    """Prints on the front and side walls, centred where a tank on that wall would stand (RoomTanks: the room turns
    round so each of the keeper's tanks has a wall of its own)."""
    zmid = (BACK + FRONT) / 2
    for wall, u, node in (("front", CX, "CenterArtF"), ("right", zmid, "CenterArtRW"), ("left", zmid, "CenterArtLW")):
        a, b = arts
        picture(r, u - 78, u - 8, 72, 132, node, a, frame, wall)
        picture(r, u + 8, u + 78, 72, 132, node, b, frame, wall)


def sofa(r, x0, x1, z0, z1, node, fabric="fabric_sofa", legs="wood_dark"):
    """A low sofa against a side wall, seat facing +x or -x (the long side along z)."""
    face = 1 if x0 < CX else -1
    back_x = (x0, x0 + 18) if face > 0 else (x1 - 18, x1)
    for zz in (z0 + 3, z1 - 7):
        for xx in (x0 + 4, x1 - 8):
            r.box(node, legs, (xx, FLOOR, zz), (xx + 4, FLOOR + 10, zz + 4), ao=0.6)
    r.box(node, fabric, (x0, FLOOR + 10, z0), (x1, FLOOR + 40, z1), ao=0.9)
    r.box(node, fabric, (back_x[0], FLOOR + 40, z0), (back_x[1], FLOOR + 82, z1))
    r.box(node, fabric, (x0, FLOOR + 40, z0), (x1, FLOOR + 62, z0 + 16))
    r.box(node, fabric, (x0, FLOOR + 40, z1 - 16), (x1, FLOOR + 62, z1))
    # seat cushions and two throw pillows
    cx0, cx1 = (x0 + 18, x1 - 2) if face > 0 else (x0 + 2, x1 - 18)
    n = 3
    w = (z1 - z0 - 32) / n
    for k in range(n):
        r.box(node, "fabric_cushion", (cx0, FLOOR + 40, z0 + 16 + k * w + 0.5), (cx1, FLOOR + 50, z0 + 16 + (k + 1) * w - 0.5), ao=0.95)
    px = back_x[1] + 2 if face > 0 else back_x[0] - 14
    r.box(node, "fabric_pillow_teal", (px, FLOOR + 50, z0 + 22), (px + 12, FLOOR + 85, z0 + 60))
    r.box(node, "fabric_pillow_sand", (px, FLOOR + 50, z1 - 60), (px + 12, FLOOR + 83, z1 - 22))


def window(r, x0, x1, y0, y1, view, node="CenterWindow", frame="frame_white", nx=2, ny=1, bar=3.0, sill=True):
    """A window in the back wall onto room.frag's outside [view] (material view_<view>), with a frame, glazing bars
    ([nx] x [ny] panes) and a sill."""
    z = BACK + 0.4
    r.quad(node, f"view_{view}", (x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z))
    f = 6.0
    r.box(node, frame, (x0 - f, y0 - f, BACK), (x1 + f, y0, BACK + 4), ao_floor=False)
    r.box(node, frame, (x0 - f, y1, BACK), (x1 + f, y1 + f, BACK + 4), ao_floor=False)
    r.box(node, frame, (x0 - f, y0, BACK), (x0, y1, BACK + 4), ao_floor=False)
    r.box(node, frame, (x1, y0, BACK), (x1 + f, y1, BACK + 4), ao_floor=False)
    for i in range(1, nx):
        x = x0 + (x1 - x0) * i / nx
        r.box(node, frame, (x - bar / 2, y0, BACK), (x + bar / 2, y1, BACK + 2.5), ao_floor=False)
    for j in range(1, ny):
        y = y0 + (y1 - y0) * j / ny
        r.box(node, frame, (x0, y - bar / 2, BACK), (x1, y + bar / 2, BACK + 2.5), ao_floor=False)
    if sill:
        r.box(node, frame, (x0 - f - 4, y0 - f - 3, BACK), (x1 + f + 4, y0 - f, BACK + 10), ao_floor=False)


def porthole(r, cx, cy, rad, view, node, ring="steel_ring", n=32):
    """A round window: the view disc and a thick bolted ring round it."""
    z = BACK + 0.4
    V = [(cx + rad * math.cos(2 * math.pi * k / n), cy + rad * math.sin(2 * math.pi * k / n), z) for k in range(n)]
    r.mesh(node, f"view_{view}", V, [tuple(range(n))], ao_floor=False)
    w, d = 9.0, 5.0
    V, F = [], []
    for k in range(n):
        a = 2 * math.pi * k / n
        c, s_ = math.cos(a), math.sin(a)
        V += [(cx + rad * c, cy + rad * s_, BACK), (cx + rad * c, cy + rad * s_, BACK + d),
              (cx + (rad + w) * c, cy + (rad + w) * s_, BACK + d), (cx + (rad + w) * c, cy + (rad + w) * s_, BACK)]
    for k in range(n):
        a, b = 4 * k, 4 * ((k + 1) % n)
        F += [(a, a + 1, b + 1, b), (a + 1, a + 2, b + 2, b + 1), (a + 2, a + 3, b + 3, b + 2)]
    r.mesh(node, ring, V, F, ao_floor=False)
    for k in range(8):
        a = 2 * math.pi * (k + 0.5) / 8
        bx, by = cx + (rad + w / 2) * math.cos(a), cy + (rad + w / 2) * math.sin(a)
        r.box(node, ring, (bx - 1.2, by - 1.2, BACK + d), (bx + 1.2, by + 1.2, BACK + d + 1.0), ao=0.8, ao_floor=False)


def curtain(r, x0, x1, y0, y1, node, fabric="fabric_curtain", folds=5, depth=3.0):
    """A gathered curtain hanging against the back wall: one sheet waved in [folds] soft folds."""
    nu, nv = 6 * folds, 4
    V, F, A = [], [], []
    for j in range(nv + 1):
        y = y0 + (y1 - y0) * j / nv
        for i in range(nu + 1):
            t = i / nu
            ph = math.sin(t * folds * 2 * math.pi)
            V.append((x0 + (x1 - x0) * t, y, BACK + 5 + depth * (1 + ph)))
            A.append(0.82 + 0.18 * (0.5 + 0.5 * ph))
    for j in range(nv):
        for i in range(nu):
            a = j * (nu + 1) + i
            F.append((a, a + 1, a + nu + 2, a + nu + 1))
    V = np.array(V)
    r.g(node, fabric).add(V, F, col=np.c_[np.array(A), np.zeros(len(V)), np.zeros(len(V)), np.ones(len(V))])


def rug(r, mat="rug_wool"):
    """The rug in front of the stand (room.frag draws its border for exactly this size)."""
    r.box("TankRug", mat, (CX - 110, FLOOR, 70), (CX + 110, FLOOR + 0.6, 230), ao=1.0, ao_floor=False)


def fireplace(r, x0, x1, node="LeftFire"):
    """A stone fireplace on the back wall with a fire that flickers (room.frag kind fire) and lights the room."""
    h, d = 105.0, 28.0
    r.box(node, "stone_hearth", (x0 - 10, FLOOR, BACK), (x1 + 10, FLOOR + 8, BACK + d + 14), ao=0.9)
    r.box(node, "stone_fire", (x0, FLOOR + 8, BACK), (x0 + 22, FLOOR + h, BACK + d))
    r.box(node, "stone_fire", (x1 - 22, FLOOR + 8, BACK), (x1, FLOOR + h, BACK + d))
    r.box(node, "stone_fire", (x0, FLOOR + h - 28, BACK), (x1, FLOOR + h, BACK + d))
    r.box(node, "wood_dark", (x0 - 8, FLOOR + h, BACK), (x1 + 8, FLOOR + h + 6, BACK + d + 6), ao_floor=False)
    r.box(node, "dark_soot", (x0 + 22, FLOOR + 8, BACK), (x1 - 22, FLOOR + h - 28, BACK + 2), ao=0.5)
    cx = (x0 + x1) / 2
    for k, dx in enumerate((-12, 0, 12)):
        V, F = cylinder((cx + dx, BACK + 14), 4, 4, FLOOR + 9, FLOOR + 13, 8)
        r.mesh(node, "wood_log", V, F, ao=0.7)
    # flames: a few crossed cards
    for k in range(3):
        fx = cx - 10 + k * 10
        r.quad(node, "fire", (fx - 7, FLOOR + 12, BACK + 14), (fx + 7, FLOOR + 12, BACK + 14),
               (fx + 3, FLOOR + 36 + 6 * (k % 2), BACK + 14), (fx - 3, FLOOR + 36 + 6 * (k % 2), BACK + 14))
    r.extras[node] = dict(lampPos=[cx, FLOOR + 30, BACK + 30], lampColor=[1.0, 0.55, 0.22])


def surfboard(r, x, node="RightSurf", mat="board_surf"):
    """A surfboard leaning on the back wall."""
    V, F = ellipsoid((0, 0, 0), (26, 3, 105), segs=14, rings=10)
    lean = math.radians(-12)
    R = np.array([[1, 0, 0], [0, math.cos(lean), -math.sin(lean)], [0, math.sin(lean), math.cos(lean)]])
    # long axis z -> y, then lean the top against the wall
    to_up = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]])
    V = (V @ to_up.T) @ R.T + np.array([x, FLOOR + 104, BACK + 28])
    r.mesh(node, mat, V, F, ao=0.95)
    V, F = ellipsoid((0, 0, 0), (2.5, 3.3, 90), segs=6, rings=8)
    V = (V @ to_up.T) @ R.T + np.array([x, FLOOR + 104, BACK + 28])
    r.mesh(node, "board_stripe", V, F, ao=0.95)


def light_strip(r, x0, x1, y, node):
    """A glowing strip light along the back wall (space station)."""
    r.box(node, "steel_ring", (x0 - 3, y - 4, BACK), (x1 + 3, y + 4, BACK + 3), ao_floor=False)
    r.box(node, "lamp_strip", (x0, y - 2, BACK + 3), (x1, y + 2, BACK + 4), ao_floor=False)


def sunny(r):
    """Sunny window: a big bright window onto a garden, gauzy curtains, plants either side."""
    shell(r)
    other_walls(r, (["art_sand", "art_sea", "art_sky"], ["art_sea_deep", "art_sea", "art_foam"]), frame="frame_white")
    side_table(r)
    floor_lamp(r, -168.0, 6.0)
    window(r, CX - 105, CX + 105, 0, 150, "garden", nx=3, ny=2)
    r.box("CenterWindow", "metal_lamp", (CX - 160, 166, BACK + 2), (CX + 160, 168.5, BACK + 6), ao_floor=False)
    curtain(r, CX - 152, CX - 108, -40, 165, "CenterCurtainL")
    curtain(r, CX + 108, CX + 152, -40, 165, "CenterCurtainR")
    plant(r, L + 120.0, 6.0, "RightPlant", height=125, seed=5)
    plant(r, CX - HALF + 40, BACK + 40, "CenterPlantCorner", height=95, seed=11, pot="pot_white")
    rug(r)
    sofa(r, CX - HALF + 2, CX - HALF + 92, 275, 445, "CenterSofa", fabric="fabric_sun")


def beach(r):
    """Beach house: white boards, a wide window onto the sea, a surfboard and a palm."""
    shell(r)
    other_walls(r, (["art_sand", "art_sea", "art_sky"], ["art_sea_deep", "art_sea", "art_foam"]), frame="frame_white")
    side_table(r, wood="wood_drift")
    floor_lamp(r, -168.0, 6.0, shade="lamp_rattan")
    window(r, CX - 140, CX + 140, -10, 150, "beach", nx=4, ny=1, frame="frame_white")
    surfboard(r, L + 150.0)
    plant(r, CX - HALF + 40, BACK + 40, "CenterPlantCorner", height=150, seed=21, pot="pot_white", leaf="leaf_palm", n=9, size=22)
    rug(r, "rug_stripe")
    sofa(r, CX - HALF + 2, CX - HALF + 92, 275, 445, "CenterSofa", fabric="fabric_linen")


def cabin(r):
    """Snowy cabin: log walls, a stone fireplace, a window onto snowy pines, a plaid armchair."""
    shell(r, base="wood_dark")
    other_walls(r, (["art_sand", "art_sea", "art_sky"], ["art_sea_deep", "art_sea", "art_foam"]), frame="wood_frame")
    side_table(r, wood="wood_dark")
    fireplace(r, -250.0, -150.0)
    window(r, CX - 80, CX + 80, 20, 140, "snow", nx=2, ny=2, frame="wood_frame")
    plant(r, L + 120.0, 6.0, "RightPlant", height=90, seed=8, pot="pot_terracotta", leaf="leaf_pine", n=13, size=12)
    rug(r, "rug_plaid")
    sofa(r, CX + HALF - 92, CX + HALF - 2, 300, 440, "CenterChair", fabric="fabric_plaid")


def blossom(r):
    """Blossom garden: paper-screen window onto cherry trees, tatami floor, a little bonsai."""
    shell(r)
    other_walls(r, (["art_sand", "art_sea", "art_sky"], ["art_sea_deep", "art_sea", "art_foam"]), frame="wood_light")
    side_table(r, wood="wood_light")
    floor_lamp(r, -168.0, 6.0, shade="lamp_paper", pole="wood_dark")
    window(r, CX - 130, CX + 130, -30, 160, "blossom", nx=6, ny=4, frame="wood_light", bar=2.0)
    for x0, x1 in ((CX - 200, CX - 140), (CX + 140, CX + 200)):
        r.box("CenterScreens", "paper_screen", (x0, -60, BACK), (x1, 160, BACK + 1.5), ao_floor=False)
        for k in range(1, 4):
            r.box("CenterScreens", "wood_light", (x0 + (x1 - x0) * k / 4 - 1, -60, BACK + 1.5), (x0 + (x1 - x0) * k / 4 + 1, 160, BACK + 2.5), ao_floor=False)
    plant(r, L + 120.0, 6.0, "RightPlant", height=55, seed=31, pot="pot_dark", leaf="leaf_bonsai", n=14, size=10)
    rug(r, "rug_straw")


def city(r):
    """City loft: brick walls, a tall steel window over the city, a leather sofa, a tall plant."""
    shell(r, base="dark_steel")
    other_walls(r, (["art_sand", "art_sea", "art_sky"], ["art_sea_deep", "art_sea", "art_foam"]), frame="dark_steel")
    side_table(r, wood="wood_dark")
    floor_lamp(r, -168.0, 6.0, shade="lamp_black", color=(1.0, 0.78, 0.5))
    window(r, CX - 140, CX + 140, -55, 185, "city", nx=4, ny=3, frame="dark_steel", sill=False)
    plant(r, L + 120.0, 6.0, "RightPlant", height=150, seed=13, pot="pot_dark")
    rug(r, "rug_grey")
    sofa(r, CX - HALF + 2, CX - HALF + 92, 275, 445, "CenterSofa", fabric="fabric_leather", legs="dark_steel")


def space(r):
    """Space station: metal panels, round windows onto the Earth, glowing strip lights."""
    shell(r, base="dark_steel")
    other_walls(r, (["art_sand", "art_sea", "art_sky"], ["art_sea_deep", "art_sea", "art_foam"]), frame="steel_ring")
    side_table(r, wood="steel_crate")
    porthole(r, CX - 120, 95, 42, "space", "CenterPortL")
    porthole(r, CX + 120, 95, 42, "space", "CenterPortR")
    porthole(r, CX, 140, 30, "space", "CenterPortC")
    light_strip(r, CX - 250, CX - 175, 180, "CenterStripL")
    light_strip(r, CX + 175, CX + 250, 180, "CenterStripR")
    r.extras["CenterStripL"] = dict(lampPos=[CX - 212, 170, BACK + 20], lampColor=[0.6, 0.8, 1.0])
    plant(r, L + 120.0, 6.0, "RightPlant", height=80, seed=17, pot="pot_white", leaf="leaf_green")
    rug(r, "rug_grey")


def living(r):
    """Cozy living room: warm walls, oak floor, a rug, side table with the radio spot, floor lamp, plants, sofa."""
    shell(r)
    other_walls(r, (["art_sand", "art_sea", "art_sky"], ["art_sea_deep", "art_sea", "art_foam"]), frame="frame_oak")
    side_table(r)
    floor_lamp(r, -168.0, 6.0)   # clear of the record stand (music_corner.py, x -127.5 .. -58.5)
    plant(r, L + 120.0, 6.0, "RightPlant", height=125, seed=5)
    plant(r, CX - HALF + 40, BACK + 40, "CenterPlantCorner", height=95, seed=11, pot="pot_white")
    rug(r)
    # two prints above the tank, low-contrast sea colours
    picture(r, CX - 78, CX - 8, 72, 132, "CenterArtL", ["art_sand", "art_sea", "art_sky"])
    picture(r, CX + 8, CX + 78, 72, 132, "CenterArtR", ["art_sea_deep", "art_sea", "art_foam"])
    sofa(r, CX - HALF + 2, CX - HALF + 92, 275, 445, "CenterSofa")


BUILDERS = dict(living=living, sunny=sunny, beach=beach, cabin=cabin, blossom=blossom, city=city, space=space)

COLORS = {
    "floor_oak": ("#A7774E", 0.55), "wall_paint": ("#E6DCCB", 0.9), "ceiling": ("#F3EEE6", 0.95),
    "trim_baseboard": ("#F4F1EA", 0.5), "wood_table": ("#7A5236", 0.45), "wood_dark": ("#3B2518", 0.5),
    "book_teal": ("#2E7C7A", 0.7), "book_coral": ("#D9765A", 0.7), "metal_lamp": ("#3A3530", 0.35),
    "lamp_shade": ("#F3E3C4", 0.8), "pot_terracotta": ("#B8643F", 0.8), "pot_white": ("#ECE8E0", 0.6),
    "soil": ("#3A2A1E", 1.0), "leaf_stem": ("#4E7A3A", 0.7), "leaf_green": ("#3F7B3E", 0.6),
    "rug_wool": ("#CDBBA0", 1.0), "frame_oak": ("#9C7148", 0.5), "mat_white": ("#F5F2EA", 0.9),
    "art_sand": ("#D9C29A", 0.9), "art_sea": ("#5E9FB0", 0.9), "art_sky": ("#BFD8DE", 0.9),
    "art_sea_deep": ("#2F6A86", 0.9), "art_foam": ("#E8F0EE", 0.9),
    "fabric_sofa": ("#8C9A93", 0.95), "fabric_cushion": ("#97A49D", 0.95),
    "fabric_pillow_teal": ("#3E8A86", 0.95), "fabric_pillow_sand": ("#D8C6A2", 0.95),
    # windows (the view itself is drawn by room.frag; the colour only shows in previews)
    "view_garden": ("#9FD0EE", 1.0), "view_beach": ("#7CC4E0", 1.0), "view_snow": ("#C9D6E2", 1.0),
    "view_blossom": ("#F2C4D2", 1.0), "view_city": ("#4A5C7E", 1.0), "view_space": ("#0B1430", 1.0),
    "frame_white": ("#F2F0EA", 0.5), "wood_frame": ("#5A3A24", 0.6), "wood_light": ("#C8A277", 0.55),
    "dark_steel": ("#26282B", 0.4), "steel_ring": ("#9AA3AC", 0.3), "steel_crate": ("#7E8891", 0.35),
    "fabric_curtain": ("#F4EFE4", 0.95), "fabric_sun": ("#D9B26A", 0.95), "fabric_linen": ("#E2DACB", 0.95),
    "fabric_plaid": ("#9C3B32", 0.95), "fabric_leather": ("#6B3F26", 0.5),
    "wood_drift": ("#B9AA92", 0.8), "wood_log": ("#6E4A2E", 0.85), "stone_fire": ("#8E8A84", 0.9),
    "stone_hearth": ("#6F6B66", 0.85), "dark_soot": ("#141210", 1.0), "fire": ("#FF8A2A", 1.0),
    "board_surf": ("#F2E6C9", 0.3), "board_stripe": ("#2F8FB0", 0.3), "lamp_rattan": ("#C9A26B", 0.9),
    "lamp_paper": ("#F6EEDC", 0.9), "lamp_black": ("#2A2B2E", 0.5), "lamp_strip": ("#BFE6FF", 0.5),
    "paper_screen": ("#F3EDDF", 0.95), "pot_dark": ("#3A3532", 0.6),
    "leaf_palm": ("#4F8A3C", 0.6), "leaf_pine": ("#2F5A3A", 0.7), "leaf_bonsai": ("#4C7A34", 0.7),
    "rug_stripe": ("#BFD3D6", 1.0), "rug_plaid": ("#8C3A30", 1.0), "rug_straw": ("#CDBB8C", 1.0),
    "rug_grey": ("#7D8186", 1.0),
}


def build(scene):
    C.reset_scene()
    root = C.empty("Root")
    C.set_extras(root, units="cm", floor=FLOOR, ceiling=CEIL, back=BACK, front=FRONT, half=HALF, centre=CX,
                 radioSpot=list(RADIO_SPOT), scene=scene)
    r = Room()
    BUILDERS[scene](r)
    mats = {}
    tris = 0
    for node, by_mat in r.nodes.items():
        names = sorted(by_mat)
        for n in names:
            if n not in mats:
                col, rough = COLORS[n]
                mats[n] = C.material(n, col, rough=rough, double=n.startswith("leaf"))
        g = WGeo()
        for k, n in enumerate(names):
            sg = by_mat[n]
            g.add(np.array(sg.V), sg.F, col=np.array(sg.COL), mat=k)
        g.obj(node, root, "room", [mats[n] for n in names], extras=r.extras.get(node))
        tris += g.tris()
    out = C.export_glb(os.path.join(C.MODELS, f"room_{scene}.glb"))
    print(f"room_{scene}.glb  ~{tris * 2} tris  {os.path.getsize(out) // 1024} KB")


if __name__ == "__main__":
    for s in [a for a in sys.argv[1:] if not a.startswith("-")] or SCENES:
        build(s)
