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
  Center*   props centred on the tank (a rug, pictures above it)

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
SCENES = ["living"]


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


def side_table(r, node="LeftTable"):
    """A round-cornered wooden side table left of the stand; its top is the radio spot."""
    x, y, z = RADIO_SPOT
    w, d = 36.0, 34.0
    r.box(node, "wood_table", (x - w / 2, y - 3, z - d / 2), (x + w / 2, y, z + d / 2))
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = x + sx * (w / 2 - 3), z + sz * (d / 2 - 3)
            r.box(node, "wood_table", (cx - 1.6, FLOOR, cz - 1.6), (cx + 1.6, y - 3, cz + 1.6))
    r.box(node, "wood_table", (x - w / 2 + 2, FLOOR + 14, z - d / 2 + 2), (x + w / 2 - 2, FLOOR + 16, z + d / 2 - 2), ao=0.8)
    # a small stack of books on the lower shelf
    r.box(node, "book_teal", (x - 12, FLOOR + 16, z - 8), (x + 6, FLOOR + 19, z + 6), ao=0.85)
    r.box(node, "book_coral", (x - 11, FLOOR + 19, z - 7), (x + 4, FLOOR + 21.5, z + 5), ao=0.9)


def floor_lamp(r, x, z, node="LeftLamp"):
    """Arc-free tripod floor lamp with a warm drum shade; the shade glows and lights the room at night."""
    base_y = FLOOR
    V, F = cylinder((x, z), 14, 14, base_y, base_y + 2.5, 20)
    r.mesh(node, "metal_lamp", V, F, ao=0.8)
    V, F = cylinder((x, z), 1.2, 1.2, base_y + 2.5, base_y + 150, 8)
    r.mesh(node, "metal_lamp", V, F)
    V, F = cylinder((x, z), 22, 18, base_y + 140, base_y + 172, 24)
    r.mesh(node, "lamp_shade", V, F, ao_floor=False)
    r.extras[node] = dict(lampPos=[x, base_y + 155, z], lampColor=[1.0, 0.72, 0.42])


def plant(r, x, z, node, height=120.0, seed=3, pot="pot_terracotta"):
    """A potted plant: a tapered pot, soil, and a fan of big flattened leaves on stems."""
    rng = np.random.default_rng(seed)
    V, F = cylinder((x, z), 15, 19, FLOOR, FLOOR + 34, 18)
    r.mesh(node, pot, V, F, ao=0.95)
    V, F = cylinder((x, z), 17.5, 17.5, FLOOR + 31, FLOOR + 32.5, 18)
    r.mesh(node, "soil", V, F, ao=0.6, ao_floor=False)
    n = 11
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
        size = 16 + 9 * rng.random()
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        up = np.array([0.0, 1.0, 0.0])
        tilt = 0.5 + 0.4 * rng.random()
        fwd = out * math.cos(tilt) + up * math.sin(tilt)
        side = np.cross(up, out)
        nrm = np.cross(fwd, side)
        R = np.c_[fwd, nrm, side]
        Vl, Fl = ellipsoid((0, 0, 0), (size, 1.2, size * 0.55), segs=10, rings=6)
        Vl = Vl @ R.T + top + fwd * size * 0.8
        r.mesh(node, "leaf_green", Vl, Fl, ao=0.85 + 0.15 * rng.random(), ao_floor=False)


def picture(r, x0, x1, y0, y1, node, art, frame="frame_oak"):
    """A framed print on the back wall: frame, mat, and the art's colour blocks (abstract sea and sand)."""
    z = BACK
    r.box(node, frame, (x0, y0, z), (x1, y1, z + 2.5), ao_floor=False)
    r.box(node, "mat_white", (x0 + 2.5, y0 + 2.5, z + 2.5), (x1 - 2.5, y1 - 2.5, z + 2.7), ao_floor=False)
    ix0, ix1, iy0, iy1 = x0 + 7, x1 - 7, y0 + 7, y1 - 7
    bands = art
    hh = (iy1 - iy0) / len(bands)
    for k, mat in enumerate(bands):
        r.box(node, mat, (ix0, iy0 + k * hh, z + 2.7), (ix1, iy0 + (k + 1) * hh, z + 2.9), ao_floor=False)


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


def living(r):
    """Cozy living room: warm walls, oak floor, a rug, side table with the radio spot, floor lamp, plants, sofa."""
    shell(r)
    side_table(r)
    floor_lamp(r, -168.0, 6.0)   # clear of the record stand (music_corner.py, x -127.5 .. -58.5)
    plant(r, L + 120.0, 6.0, "RightPlant", height=125, seed=5)
    plant(r, CX - HALF + 40, BACK + 40, "CenterPlantCorner", height=95, seed=11, pot="pot_white")
    # rug in front of the stand
    r.box("CenterRug", "rug_wool", (CX - 110, FLOOR, 70), (CX + 110, FLOOR + 0.6, 230), ao=1.0, ao_floor=False)
    # two prints above the tank, low-contrast sea colours
    picture(r, CX - 78, CX - 8, 72, 132, "CenterArtL", ["art_sand", "art_sea", "art_sky"])
    picture(r, CX + 8, CX + 78, 72, 132, "CenterArtR", ["art_sea_deep", "art_sea", "art_foam"])
    sofa(r, CX - HALF + 2, CX - HALF + 92, 120, 330, "CenterSofa")


BUILDERS = dict(living=living)

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
