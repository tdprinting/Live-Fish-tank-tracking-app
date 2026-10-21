"""Fish store interiors for My tank's store visits (docs/ECONOMY.md §5 "Visiting a store"): store_room_<id>.glb.

Walking a store is a row of display tanks on stands along the back wall, then the checkout counter. The live display
tank the keeper is looking at is the real tank (tank.glb + the display sim) at the origin; this model is everything
around it, authored once per store in modules the renderer repeats along the aisle (render/StoreRoom.kt):

  Bay       one bay of the shop (BAY cm wide, centred on the tank): floor, back wall with a stripe and a poster,
            ceiling, a hanging tank light. Drawn for every bay, the counter's too.
  Stand     the cabinet under a display tank (the tank's own walnut top sits on it). Every tank bay.
  PropTank  a stocked display tank (top board, rock, corals, fish, glowing water, glass) for the bays the keeper is
            not standing at: only one display sim runs at a time.
  Counter   the checkout: desk, till, card reader, a jar of sand dollars, bagged fish, the CHECKOUT sign. One bay
            past the last tank.
  Sign      the store's name across the back wall above the first tank.
  EndL/EndR side walls closing the aisle (left of the first bay, right of the counter bay).

World cm, the tank frame (x along the tank, y up, z toward the viewer; tank interior x 0..121.9, y 0..40.6, z 0..45.7,
the floor at y = FLOOR). Material names carry the store shader's kinds (store.frag): floor*, lamp*, glass*, water*,
gold*, screen*, neon*; the rest are plain painted surfaces.

    python3 tools/blender/store_rooms.py [store ids...]   (bpy from PyPI; no Blender install needed)
"""
import math
import os
import sys

import numpy as np

import common as C
from common import bpy
from tank import WGeo, box

L, H, D = C.TANK
BAY = 180.0                      # render/StoreRoom.kt StoreRoomLayout.BAY
CX = L / 2
X0, X1 = CX - BAY / 2, CX + BAY / 2
FLOOR = -82.0                    # tank.py stand_geos: the floor under the stand
CEIL = 300.0
BACK = -70.0
FRONT = 520.0
STORES = ["shellys", "frag_shack", "big_blue", "deep_reef"]

THEMES = {
    # walls, stripe, trim, floor, stand, counter, counter top, poster, poster art, sign text, water, ceiling
    "shellys": dict(wall="#F2E4CB", stripe="#2BA7A0", trim="#7A5236", floor="#D8C7A8", stand="#C68A55",
                    counter="#2BA7A0", top="#F7F1E6", poster="#FF9E5E", art="#FFFFFF", sign="#1B6E6A",
                    water="#3AB4E8", ceil="#F6EFE2", name="SHELLY'S PET SHOP", sign_kind="paint"),
    "frag_shack": dict(wall="#2A2E37", stripe="#7A5CFF", trim="#14161A", floor="#8E9399", stand="#1C1E23",
                       counter="#3B3F48", top="#6E737B", poster="#3C2E78", art="#9DF2FF", sign="#B9A8FF",
                       water="#5E6BFF", ceil="#1E2128", name="FRAG SHACK", sign_kind="neon"),
    "big_blue": dict(wall="#EEF3F8", stripe="#1F6FD1", trim="#2B3A4F", floor="#C7CDD4", stand="#1F4F9A",
                     counter="#1F6FD1", top="#F4F7FA", poster="#0F4C9E", art="#FFD24A", sign="#5FD7FF",
                     water="#2E9BE6", ceil="#F2F5F8", name="BIG BLUE AQUATICS", sign_kind="neon"),
    "deep_reef": dict(wall="#17141C", stripe="#C9A646", trim="#0C0B0F", floor="#3A2A20", stand="#0F0E12",
                      counter="#15131A", top="#2A2420", poster="#221C28", art="#C9A646", sign="#E2C46A",
                      water="#2A7FD8", ceil="#100E14", name="DEEP REEF IMPORTS", sign_kind="gold"),
}


# ============================================================== geometry helpers (world cm)
def ellipsoid(c, r, segs=12, rings=8):
    """UV ellipsoid at [c] with radii [r] (x, y, z)."""
    V, F = [], []
    for j in range(rings + 1):
        v = math.pi * j / rings
        for i in range(segs):
            u = 2 * math.pi * i / segs
            V.append((c[0] + r[0] * math.sin(v) * math.cos(u), c[1] + r[1] * math.cos(v), c[2] + r[2] * math.sin(v) * math.sin(u)))
    for j in range(rings):
        for i in range(segs):
            a, b = j * segs + i, j * segs + (i + 1) % segs
            F.append((a, a + segs, b + segs, b))
    return np.array(V), F


def cylinder(c, r, h, segs=16):
    """Upright closed cylinder: base centre [c], radius [r], height [h]."""
    V = []
    for y in (c[1], c[1] + h):
        for i in range(segs):
            u = 2 * math.pi * i / segs
            V.append((c[0] + r * math.cos(u), y, c[2] + r * math.sin(u)))
    V.append((c[0], c[1], c[2]))
    V.append((c[0], c[1] + h, c[2]))
    F = [(i, (i + 1) % segs, segs + (i + 1) % segs, segs + i) for i in range(segs)]
    F += [(2 * segs, (i + 1) % segs, i) for i in range(segs)]
    F += [(2 * segs + 1, segs + i, segs + (i + 1) % segs) for i in range(segs)]
    return np.array(V), F


class Group:
    """One runtime node: a WGeo with its own material list (name -> slot)."""

    def __init__(self, name, smooth=False):
        self.name = name
        self.smooth = smooth
        self.g = WGeo()
        self.mats = []
        self.slots = {}

    def slot(self, m):
        if m.name not in self.slots:
            self.slots[m.name] = len(self.mats)
            self.mats.append(m)
        return self.slots[m.name]

    def box(self, lo, hi, m):
        self.add(box(lo, hi), m)

    def add(self, VF, m):
        """Adds a mesh with material [m], or painted in the hex colour [m] (vertex colour, one shared material: the
        small figures, fish and corals stay one draw call per node)."""
        V, F = VF
        if isinstance(m, str):
            self.g.add(V, F, col=C.lin(m) + (1.0,), mat=self.slot(mat("paint_vcol", "#FFFFFF", 0.6)))
        else:
            self.g.add(V, F, mat=self.slot(m))

    def build(self, root):
        return self.g.obj(self.name, root, "store", self.mats, smooth=self.smooth)


_mats = {}


def mat(name, color, rough=0.6, metal=0.0, emit=None, strength=1.0, alpha=1.0):
    if name not in _mats:
        _mats[name] = C.material(name, color, rough=rough, metal=metal, emissive=emit, emissive_strength=strength,
                                 alpha=alpha, blend=alpha < 1.0, double=alpha < 1.0)
    return _mats[name]


def fish(g, c, length, body, yaw=0.0):
    """A little fish facing +x (turned by [yaw] about y): an egg body and a tail fan."""
    cy, sy = math.cos(yaw), math.sin(yaw)
    V, F = ellipsoid((0, 0, 0), (length * 0.34, length * 0.2, length * 0.1), 10, 6)
    tail = np.array([(-length * 0.3, 0, 0), (-length * 0.52, length * 0.16, 0), (-length * 0.52, -length * 0.16, 0),
                     (-length * 0.3, 0, 0.02), (-length * 0.52, length * 0.16, 0.02), (-length * 0.52, -length * 0.16, 0.02)])
    TF = [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)]
    for VV, FF in ((V, F), (tail, TF)):
        R = np.c_[VV[:, 0] * cy - VV[:, 2] * sy, VV[:, 1], VV[:, 0] * sy + VV[:, 2] * cy] + np.asarray(c)
        g.add((R, FF), body)


def text(body, x, y, z, size, m, depth=1.2):
    """Blender text converted to a mesh, standing on the plane z (world), reading along +x."""
    bpy.ops.object.text_add(location=(x, -z, y), rotation=(math.radians(90), 0, 0))
    o = bpy.context.active_object
    o.data.body = body
    o.data.size = size
    o.data.extrude = depth / 2
    o.data.align_x = 'CENTER'
    o.data.align_y = 'CENTER'
    o.data.materials.append(m)
    bpy.ops.object.convert(target='MESH')
    o = bpy.context.active_object
    o.location.y -= depth / 2      # the back face on the wall plane, the letters toward the viewer
    return o


# ============================================================== modules
def bay(t):
    g = Group("Bay")
    g.box((X0, FLOOR - 1, BACK), (X1, FLOOR, FRONT), mat("floor_tile", t["floor"], 0.45))
    g.box((X0, FLOOR, BACK - 2), (X1, CEIL, BACK), mat("wall", t["wall"], 0.9))
    g.box((X0, 58, BACK), (X1, 72, BACK + 0.6), mat("stripe", t["stripe"], 0.5))
    g.box((X0, FLOOR, BACK), (X1, FLOOR + 10, BACK + 2), mat("trim", t["trim"], 0.5))
    g.box((X0, CEIL, BACK - 2), (X1, CEIL + 2, FRONT), mat("ceiling", t["ceil"], 0.9))
    # poster over the tank: a framed print of a big fish
    g.box((CX - 42, 104, BACK), (CX + 42, 160, BACK + 1), mat("trim", t["trim"], 0.5))
    g.box((CX - 38, 108, BACK + 1), (CX + 38, 156, BACK + 1.3), mat("poster", t["poster"], 0.7))
    fish(g, (CX, 132, BACK + 1.6), 46, t["art"])
    # ceiling panel light over the aisle in front of the stand
    g.box((CX - 40, CEIL - 1, 140), (CX + 40, CEIL, 200), mat("lamp_ceiling", "#FFFFFF", 0.2, emit="#FFF6E8", strength=3.0))
    return g


# ============================================================== display tanks (core StoreDisplays.DisplayKind order)
# name, interior L x H x D (cm, TankBuild inches to the mm), sump under the stand, black rim, all-in-one back chamber
KINDS = [
    dict(name="nano", L=40.6, H=45.7, D=40.6, sump=False, rim=True, aio=True),
    dict(name="rimless", L=91.4, H=40.6, D=45.7, sump=False, rim=False, aio=False),
    dict(name="long", L=121.9, H=40.6, D=45.7, sump=False, rim=True, aio=False),
    dict(name="frag", L=91.4, H=30.5, D=61.0, sump=True, rim=True, aio=False),
    dict(name="show", L=121.9, H=61.0, D=61.0, sump=True, rim=False, aio=False),
]
FRAG = 3
# core StoreDisplays.RACKS (x0, z0, x1, z1, top y) and plug pitch / height, in the frag tank's frame
RACKS = [(8.0, 6.0, 83.4, 24.0, 14.0), (8.0, 32.0, 83.4, 54.0, 7.0)]
PLUG_PITCH = 6.25
PLUG_TOP = 1.6
STAND_TOP = -7.6   # tank.py: the walnut top board is part of the tank shell; the stand body stops under it


def stand(t, k, kind):
    """The cabinet under a display tank of [kind]: doors, or an open front showing the sump on a sumped tank."""
    g = Group(f"Stand{k}")
    L, D = kind["L"], kind["D"]
    body = mat("stand", t["stand"], 0.55)
    trim = mat("trim", t["trim"], 0.5)
    seam = mat("seam", "#08080A", 0.8)
    x0, x1, z0, z1 = -6, L + 6, -6, D + 6
    if not kind["sump"]:
        g.box((x0, FLOOR, z0), (x1, STAND_TOP, z1), body)
        doors = 1 if L < 60 else (2 if L < 100 else 3)
        for d in range(1, doors):
            x = x0 + (x1 - x0) * d / doors
            g.box((x - 0.4, FLOOR + 10, z1), (x + 0.4, -12, z1 + 0.3), seam)
        g.box((x0, -12.4, z1), (x1, -12, z1 + 0.3), seam)
        knob = mat("gold_knob", "#C9A646", 0.3, metal=0.9)
        for d in range(doors):
            cx = x0 + (x1 - x0) * (d + 0.5) / doors
            g.box((cx - 1, -40, z1 + 0.3), (cx + 1, -33, z1 + 1.6), knob)
        g.box((x0 + 8, -24, z1 + 0.3), (x1 - 8, -16, z1 + 0.8), mat("label", "#F8F8F4", 0.6))
    else:
        # open frame: sides, back, bottom, top rail; the sump shows through the front
        for lo, hi in (((x0, FLOOR, z0), (x0 + 3, STAND_TOP, z1)), ((x1 - 3, FLOOR, z0), (x1, STAND_TOP, z1)),
                       ((x0, FLOOR, z0), (x1, STAND_TOP, z0 + 2)), ((x0, FLOOR, z0), (x1, FLOOR + 9, z1)),
                       ((x0, -16, z0), (x1, STAND_TOP, z1))):
            g.box(lo, hi, body)
        g.box((x0, FLOOR + 9, z0 + 2), (x1, FLOOR + 9.3, z1), mat("cabinet_dark", "#15161A", 0.9))
        # sump: a glass box of glowing water with baffles, a skimmer, the return pump and a filter sock
        sy0, sy1 = FLOOR + 10, FLOOR + 40
        sx0, sx1, sz0, sz1 = x0 + 10, x1 - 10, z0 + 8, z1 - 8
        g.box((sx0 + 0.6, sy0, sz0 + 0.6), (sx1 - 0.6, sy1 - 6, sz1 - 0.6), mat("water_sump", "#9FD8FF", 0.1, emit="#3FA6FF", strength=0.5, alpha=0.4))
        glass = mat("glass_prop", "#DDEFE4", 0.02, alpha=0.12)
        for lo, hi in (((sx0, sy0, sz1 - 0.6), (sx1, sy1, sz1)), ((sx0, sy0, sz0), (sx0 + 0.6, sy1, sz1)), ((sx1 - 0.6, sy0, sz0), (sx1, sy1, sz1))):
            g.box(lo, hi, glass)
        w = sx1 - sx0
        for f in (0.33, 0.62):
            bx = sx0 + w * f
            g.box((bx - 0.4, sy0, sz0), (bx + 0.4, sy1 - 3, sz1), glass)
        # skimmer in the first chamber: a clear body full of fine bubbles, a dark cup and pump at its foot
        kx, kz = sx0 + w * 0.16, (sz0 + sz1) / 2
        g.add(cylinder((kx, sy0, kz), 6.5, 42, 16), mat("glass_skimmer", "#E8F4FF", 0.05, alpha=0.22))
        g.add(cylinder((kx, sy0 + 2, kz), 5.6, 30, 12), mat("bubbles_skimmer", "#FFFFFF", 0.3, alpha=0.6))
        g.add(cylinder((kx, sy0 + 42, kz), 7.2, 6, 16), mat("skimmer_cup", "#20232A", 0.4))
        g.box((kx + 6, sy0, kz - 4), (kx + 13, sy0 + 7, kz + 4), mat("pump", "#1A1C20", 0.5))
        # filter sock hanging in the inlet chamber
        g.add(cylinder((sx0 + w * 0.42, sy1 - 22, kz), 4.5, 20, 12), mat("sock", "#F2F2EE", 0.95))
        # return pump in the last chamber, the return line up the back
        rx = sx0 + w * 0.82
        g.box((rx - 5, sy0, kz - 4), (rx + 5, sy0 + 9, kz + 4), mat("pump", "#1A1C20", 0.5))
        pvc = mat("pvc", "#E9ECEF", 0.5)
        g.add(cylinder((rx, sy0 + 9, z0 + 4), 1.4, -16 - (sy0 + 9), 10), pvc)
        g.add(cylinder((sx0 + w * 0.45, sy1 - 2, z0 + 4), 2.2, -16 - (sy1 - 2), 10), pvc)
        # a small heater and the ATO float
        g.add(cylinder((sx0 + w * 0.72, sy0 + 3, sz0 + 3), 1.4, 26, 8), mat("heater", "#3A3F46", 0.3))
        # a glowing controller box and a power strip on the cabinet side
        g.box((x0 + 3, -40, z0 + 14), (x0 + 3.8, -28, z0 + 26), mat("controller", "#22252B", 0.4))
        g.box((x0 + 3.8, -37, z0 + 16), (x0 + 4.0, -31, z0 + 24), mat("screen_ctrl", "#0A2A3A", 0.2, emit="#38C8FF", strength=1.2))
        g.box((x0 + 3, FLOOR + 9.3, z0 + 8), (x0 + 4, FLOOR + 12, z0 + 40), mat("powerstrip", "#E8E8E8", 0.6))
    g.box((x0 + 2, FLOOR, z1 - 2), (x1 - 2, FLOOR + 9, z1 + 0.4), trim)
    return g


def coral_blob(g, rng, c, r, col, name):
    g.add(ellipsoid(c, (r, r * 0.8, r), 10, 6), col)


def coral_discs(g, rng, c, r, col, name, n=7):
    """A clump of mushrooms or zoas: flat discs on short stalks."""
    m = col
    for _ in range(n):
        a, d = rng.uniform(0, 6.283), rng.uniform(0, r)
        p = (c[0] + d * math.cos(a), c[1] + rng.uniform(0, 1.5), c[2] + d * math.sin(a))
        dr = rng.uniform(0.35, 0.6) * r
        g.add(ellipsoid(p, (dr, dr * 0.25, dr), 8, 3), m)


def coral_tree(g, rng, c, h, col, name):
    """A Kenya-tree or finger leather: a trunk and a few fingers."""
    m = col
    g.add(cylinder(c, h * 0.12, h * 0.55, 8), m)
    for _ in range(5):
        a = rng.uniform(0, 6.283)
        tip = (c[0] + math.cos(a) * h * 0.3, c[1] + h * rng.uniform(0.7, 1.0), c[2] + math.sin(a) * h * 0.3)
        mid = ((c[0] + tip[0]) / 2, c[1] + h * 0.5, (c[2] + tip[2]) / 2)
        g.add(ellipsoid(mid, (h * 0.09, h * 0.28, h * 0.09), 8, 5), m)
        g.add(ellipsoid(tip, (h * 0.1, h * 0.1, h * 0.1), 8, 5), m)


CORAL_COLS = ("#FF6FA8", "#7DFF6A", "#B07CFF", "#FF9A3A", "#5BE0FF", "#FFE45A", "#3DFFB8", "#FF5252")
FISH_COLS = ("#FF7A1A", "#2C6BFF", "#FFD21A", "#FF7A1A", "#9B5CFF", "#FF3D7F", "#3DD9FF")


def scape(kind, k):
    """Rock lumps (centre, radii) of a prop tank's rockwork, in the shapes core StoreDisplays builds."""
    L, D = kind["L"], kind["D"]
    z = D * 0.42
    if kind["name"] == "nano":         # a bommie
        return [((L * 0.5, 3, z), (12, 16, 11)), ((L * 0.5, 16, z), (8, 8, 7)), ((L * 0.25, 3, z + 6), (5, 5, 5))]
    if kind["name"] == "rimless":      # an arch with a boulder each side
        return [((L * 0.38, 3, z), (6, 13, 7)), ((L * 0.62, 3, z), (6, 13, 7)), ((L * 0.5, 14, z), (16, 5, 7)),
                ((L * 0.14, 3, z), (9, 9, 8)), ((L * 0.86, 3, z), (9, 10, 8))]
    if kind["name"] == "long":         # two islands
        return [((L * 0.25, 3, z), (17, 18, 12)), ((L * 0.3, 18, z), (8, 8, 7)), ((L * 0.74, 3, z), (14, 15, 11)),
                ((L * 0.82, 3, z + 10), (6, 5, 5))]
    if kind["name"] == "show":         # pillars and open water
        return [((L * 0.18, 3, z), (9, 36, 9)), ((L * 0.52, 3, z * 0.8), (10, 42, 10)), ((L * 0.82, 3, z), (8, 30, 8)),
                ((L * 0.35, 3, z + 10), (10, 8, 8)), ((L * 0.68, 3, z + 10), (9, 7, 8))]
    return []


def racks(g, with_frags, rng=None, reef=None):
    """The frag tank's egg-crate racks on PVC legs, a plug in every hole (core StoreDisplays.plugs)."""
    crate = mat("eggcrate", "#F4F4F0", 0.6)
    pvc = mat("pvc", "#E9ECEF", 0.5)
    plug = mat("plug", "#D9CDB8", 0.85)
    for (x0, z0, x1, z1, top) in RACKS:
        g.box((x0, top - 1.2, z0), (x1, top, z1), crate)
        for x in (x0 + 2, x1 - 2):
            for z in (z0 + 2, z1 - 2):
                g.add(cylinder((x, 3.0, z), 1.0, top - 1.2 - 3.0, 8), pvc)
        z = z0 + PLUG_PITCH / 2
        while z < z1:
            x = x0 + PLUG_PITCH / 2
            while x < x1:
                g.add(cylinder((x, top - 0.4, z), 1.25, PLUG_TOP + 0.4, 8), plug)
                if with_frags and rng.random() < 0.45:
                    col = CORAL_COLS[int(rng.integers(len(CORAL_COLS)))]
                    p = (x, top + PLUG_TOP, z)
                    if rng.random() < 0.5:
                        coral_discs(reef, rng, p, 2.2, col, f"coral_{col[1:]}", n=3)
                    else:
                        coral_blob(reef, rng, (p[0], p[1] + 1.2, p[2]), 1.8, col, f"coral_{col[1:]}")
                x += PLUG_PITCH
            z += PLUG_PITCH


def prop_tank(t, k, kind, seed):
    """A stocked display tank of [kind] for the bays the keeper is not standing at (its fish in PropFish<k>)."""
    rng = np.random.default_rng(seed)
    g = Group(f"PropTank{k}")
    fg = Group(f"PropFish{k}", smooth=True)
    reef = Group(f"PropReef{k}", smooth=True)
    L, H, D = kind["L"], kind["H"], kind["D"]
    wy = H - 2.5
    g.box((-4, -7.6, -4), (L + 4, -3.6, D + 4), mat("tank_top", "#3B2518", 0.35))
    g.box((0.4, 0, 0.4), (L - 0.4, 3, D - 0.4), mat("sand", "#EDE3CF", 0.92))
    g.box((-1, -1, -1.2), (L + 1, H + 1, -0.6), mat("backing", "#070809", 0.9))
    if kind["aio"]:
        # the all-in-one's filter chamber behind a black false wall, its return nozzle and intake slots
        g.box((0.4, 3, 0.4), (L - 0.4, H - 1, 6), mat("aio_wall", "#0B0C0E", 0.6))
        for y in range(int(wy) - 8, int(wy) - 1, 2):
            g.box((L - 14, y, 6), (L - 3, y + 0.8, 6.2), mat("aio_slot", "#1E2024", 0.6))
        g.add(cylinder((L * 0.3, wy - 6, 6), 1.2, 3, 8), mat("nozzle", "#1A1C20", 0.5))
    rock = mat("rock", "#9C8A78", 0.95)
    lumps = scape(kind, k)
    for c, r in lumps:
        reef.add(ellipsoid(c, r, 12, 7), rock)
    if kind["name"] == "frag":
        racks(g, True, rng, reef)
    else:
        # corals on the rock tops and the sand
        for i, (c, r) in enumerate(lumps):
            top = (c[0] + rng.uniform(-r[0], r[0]) * 0.4, c[1] + r[1] * 0.85, c[2] + rng.uniform(-2, 2))
            col = CORAL_COLS[(i + k * 3) % len(CORAL_COLS)]
            nm = f"coral_{col[1:]}"
            grow = (coral_discs, coral_blob, coral_tree)[(i + k) % 3]
            grow(reef, rng, top, 3.5 if L < 60 else 5.0, col, nm)
        for i in range(2 if L < 60 else 4):
            col = CORAL_COLS[(i * 3 + k) % len(CORAL_COLS)]
            coral_discs(reef, rng, (L * (0.15 + 0.7 * rng.random()), 3.2, D * (0.65 + 0.2 * rng.random())), 3.0, col, f"coral_{col[1:]}", n=5)
        # a powerhead on the back glass
        g.add(cylinder((L * 0.85, wy - 9, 1.5), 3.2, 5, 12), mat("powerhead", "#1A1C20", 0.5))
    # a bubble trail from an airline or the return, rising to the surface
    bx = L * 0.12 if kind["name"] != "nano" else L * 0.3
    g.box((bx - 0.9, 4, D * 0.3 - 0.9), (bx + 0.9, wy, D * 0.3 + 0.9), mat("bubbles_tank", "#FFFFFF", 0.3, alpha=0.6))
    # fish: few and small in nanos and frag tanks, a school in the long tanks
    nfish = {"nano": 3, "rimless": 4, "long": 6, "frag": 2, "show": 8}[kind["name"]]
    for i in range(nfish):
        col = FISH_COLS[(i + k) % len(FISH_COLS)]
        ln = (4 + rng.uniform(0, 2)) * (1.6 if kind["name"] == "show" else 1.0)
        fish(fg, (L * (0.12 + 0.76 * rng.random()), 8 + rng.uniform(0, wy - 14), D * (0.25 + 0.5 * rng.random())), ln,
             col, yaw=rng.uniform(-0.4, 0.4) + (math.pi if i % 2 else 0))
    if kind["rim"]:
        g.box((-0.8, H, -0.8), (L + 0.8, H + 1.2, D + 0.8), mat("rim", "#141518", 0.45))
    else:
        # rimless: polished glass edges catch the light
        g.box((-0.7, H - 0.15, D - 0.1), (L + 0.7, H, D + 0.7), mat("glass_edge", "#CFF5E8", 0.05, alpha=0.5))
    g.box((0.3, 3, 0.3), (L - 0.3, wy, D - 0.3), mat("water_prop", "#9FD8FF", 0.1, emit="#3FA6FF", strength=0.35, alpha=0.32))
    glass = mat("glass_prop", "#DDEFE4", 0.02, alpha=0.12)
    gt = 0.6
    for lo, hi in (((-gt, -gt, D), (L + gt, H, D + gt)), ((-gt, -gt, 0 - gt), (-0.01, H, D)), ((L + 0.01, -gt, 0 - gt), (L + gt, H, D))):
        g.box(lo, hi, glass)
    return g, fg, reef


def lamp(t, k, kind):
    """The light over a display tank of [kind], hanging from the ceiling: its lens is tinted by the tank's light."""
    g = Group(f"Lamp{k}")
    L, H, D = kind["L"], kind["H"], kind["D"]
    house = mat("light_housing", "#202226", 0.4, metal=0.6)
    lens = mat("lamp_tank", "#FFFFFF", 0.2, emit="#EAF4FF", strength=4.0)
    cable = mat("cable", "#111111", 0.5)
    n = kind["name"]
    zc = D / 2
    if n in ("nano", "show"):
        # round LED pucks on pendants (one over a cube, three over the big tank)
        xs = [L / 2] if n == "nano" else [L * 0.2, L * 0.5, L * 0.8]
        y = H + (34 if n == "nano" else 40)
        for x in xs:
            g.add(cylinder((x, y, zc), 9 if n == "nano" else 11, 4, 20), house)
            g.add(cylinder((x, y - 0.4, zc), 7.6 if n == "nano" else 9.5, 0.4, 20), lens)
            g.box((x - 0.3, y + 4, zc - 0.3), (x + 0.3, CEIL, zc + 0.3), cable)
    elif n == "frag":
        # a low, wide fixture close over the shallow frag tank: rows of blue LED strips
        y = H + 24
        g.box((4, y, 6), (L - 4, y + 5, D - 6), house)
        for zz in np.linspace(12, D - 12, 5):
            g.box((8, y - 0.5, zz - 1.4), (L - 8, y, zz + 1.4), lens)
        for x in (12, L - 12):
            g.box((x - 0.3, y + 5, zc - 0.3), (x + 0.3, CEIL, zc + 0.3), cable)
    else:
        # a hanging LED bar the length of the tank
        y = H + 77
        g.box((4, y, 4), (L - 4, y + 7, D - 4), house)
        g.box((8, y - 0.6, 8), (L - 8, y, D - 8), lens)
        for x in (12, L - 12):
            g.box((x - 0.3, y + 7, zc - 0.3), (x + 0.3, CEIL, zc + 0.3), cable)
    return g


def frag_racks():
    """The live frag tank's racks and plugs (the frags are the display sim's corals)."""
    g = Group("Racks")
    racks(g, False)
    return g


def counter(t):
    g = Group("Counter")
    g.box((-10, FLOOR, 40), (132, 16, 96), mat("counter", t["counter"], 0.6))
    g.box((-14, 16, 36), (136, 20, 100), mat("counter_top", t["top"], 0.35))
    g.box((-10, -30, 96), (132, -22, 96.6), mat("stripe", t["stripe"], 0.5))
    g.box((-8, FLOOR, 94), (130, FLOOR + 8, 98), mat("trim", t["trim"], 0.5))
    dark = mat("till", "#1C1D22", 0.4, metal=0.3)
    g.box((70, 20, 50), (104, 36, 78), dark)
    g.box((72, 36, 58), (102, 54, 61), dark)
    g.box((74, 38, 61), (100, 52, 61.4), mat("screen_till", "#0A2A3A", 0.2, emit="#38C8FF", strength=1.5))
    g.box((40, 20, 70), (52, 30, 80), dark)
    g.box((42, 26, 80), (50, 29, 80.3), mat("screen_card", "#0A2A3A", 0.2, emit="#7CFFB2", strength=1.2))
    # a jar of sand dollars
    g.add(cylinder((20, 20, 66), 7.5, 16, 20), mat("glass_jar", "#DDEFE4", 0.05, alpha=0.25))
    gold = mat("coin", "#F2C443", 0.35)
    for k in range(9):
        a = k * 2.4
        g.add(cylinder((20 + 3.5 * math.cos(a), 20.5 + k * 1.1, 66 + 3.5 * math.sin(a)), 2.6, 0.5, 14), gold)
    # bagged fish waiting at the end of the counter
    bag = mat("glass_bag", "#E8F6FF", 0.05, alpha=0.3)
    for k, col in enumerate(("#FF7A1A", "#2C6BFF", "#FFD21A")):
        bx = 114 + (k % 2) * 10
        bz = 52 + k * 13
        g.add(ellipsoid((bx, 30, bz), (5, 10, 5), 12, 8), bag)
        g.box((bx - 1.2, 39.5, bz - 1.2), (bx + 1.2, 43, bz + 1.2), mat("bag_tie", "#E23B3B", 0.5))
        fish(g, (bx, 27, bz), 5, col, yaw=0.4 * k)
    # the sign hanging over the counter
    g.box((14, 150, 64), (108, 176, 67), mat("sign_board", t["stripe"], 0.5))
    for x in (24, 98):
        g.box((x - 0.3, 176, 65.2), (x + 0.3, CEIL, 65.8), mat("cable", "#111111", 0.5))
    # shelves of food and test kits on the back wall behind the counter
    shelf = mat("trim", t["trim"], 0.5)
    for y in (30, 62):
        g.box((-20, y, BACK), (142, y + 3, BACK + 30), shelf)
        for k in range(9):
            x = -12 + k * 17
            col = ("#E8463C", "#2F8FE0", "#F3C33B", "#45B36B", "#9A5CE6")[(k + int(y)) % 5]
            g.add(cylinder((x, y + 3, BACK + 15), 5, 13 if k % 3 else 17, 14), col)
    g.box((0, FLOOR, 100), (122, FLOOR + 0.4, 170), mat("rug", t["stripe"], 0.95))
    # a potted palm by the end wall
    g.add(cylinder((150, FLOOR, 20), 12, 34, 16), mat("pot", "#B5653E", 0.8))
    leaf = mat("leaf", "#3E9B4A", 0.7)
    for k in range(7):
        a = k * 2 * math.pi / 7
        g.add(ellipsoid((150 + 14 * math.cos(a), FLOOR + 62, 20 + 14 * math.sin(a)), (12 * abs(math.cos(a)) + 3, 4, 12 * abs(math.sin(a)) + 3), 10, 6), leaf)
    g.add(cylinder((150, FLOOR + 30, 20), 2.2, 34, 8), mat("trunk", "#7A5A3A", 0.9))
    return g


# ============================================================== people (render/StoreRoom.kt animates them)
# Each person is modelled standing at the origin, facing +z; the renderer places, turns and bobs them. The walker's
# legs are their own nodes, hip at the origin (WALK_HIP above the floor), so they can swing.
WALK_HIP = 84.0
SKINS = ("#F1C7A3", "#C68E63", "#8D5A3B", "#E8B48C")


def legs(g, hip, h, pants, shoe, spread=7.0):
    for s in (-1, 1):
        g.add(cylinder((s * spread / 2, h * 0.04, 0), h * 0.045, hip - h * 0.04, 10), pants)
        g.add(ellipsoid((s * spread / 2, h * 0.02, 2.5), (h * 0.04, h * 0.025, h * 0.075), 10, 5), shoe)


def leg_node(name, h, pants, shoe, side):
    """One walker leg, hip at the origin, hanging down to the floor at y = -hip."""
    g = Group(name, smooth=True)
    hip = WALK_HIP
    g.add(cylinder((side * 3.5, -hip + h * 0.04, 0), h * 0.045, hip - h * 0.04, 10), pants)
    g.add(ellipsoid((side * 3.5, -hip + h * 0.02, 2.5), (h * 0.04, h * 0.025, h * 0.075), 10, 5), shoe)
    return g


def person(g, h, shirt, pants, skin, hair, with_legs=True, apron=None, cap=None, head=True):
    """A friendly low-poly person [h] cm tall at the origin, facing +z (arms down, slightly forward)."""
    hip = h * 0.51
    sh = shirt
    pa = pants
    sk = skin
    hr = hair
    shoe = "#2A2A30"
    if with_legs:
        legs(g, hip, h, pa, shoe)
    g.add(ellipsoid((0, hip + h * 0.02, 0), (h * 0.1, h * 0.07, h * 0.07), 12, 6), pa)
    g.add(ellipsoid((0, hip + h * 0.16, 0), (h * 0.115, h * 0.17, h * 0.075), 14, 8), sh)
    if apron:
        g.add(ellipsoid((0, hip + h * 0.12, h * 0.035), (h * 0.1, h * 0.15, h * 0.05), 12, 6), apron)
    for s in (-1, 1):
        # sleeve from the shoulder, then the forearm and hand hanging just in front of the hip
        g.add(ellipsoid((s * h * 0.13, hip + h * 0.2, 0), (h * 0.032, h * 0.09, h * 0.032), 8, 6), sh)
        g.add(ellipsoid((s * h * 0.135, hip + h * 0.07, h * 0.015), (h * 0.026, h * 0.075, h * 0.026), 8, 5), sk)
    g.add(cylinder((0, hip + h * 0.31, 0), h * 0.035, h * 0.05, 8), sk)
    hy = hip + h * 0.41
    if not head:
        return hy
    g.add(ellipsoid((0, hy, 0), (h * 0.065, h * 0.075, h * 0.07), 14, 9), sk)
    g.add(ellipsoid((0, hy + h * 0.025, -h * 0.01), (h * 0.07, h * 0.06, h * 0.07), 14, 7), hr)
    eye = "#1A1420"
    for s in (-1, 1):
        g.add(ellipsoid((s * h * 0.024, hy + h * 0.005, h * 0.066), (h * 0.008, h * 0.01, h * 0.004), 6, 4), eye)
    g.add(ellipsoid((0, hy - h * 0.03, h * 0.064), (h * 0.02, h * 0.006, h * 0.004), 6, 3), "#B5523F")
    if cap:
        g.add(ellipsoid((0, hy + h * 0.05, 0), (h * 0.072, h * 0.035, h * 0.072), 14, 6), cap)
        g.add(ellipsoid((0, hy + h * 0.045, h * 0.07), (h * 0.05, h * 0.008, h * 0.04), 10, 4), cap)
    return hy


def clerk(t):
    """The shop's clerk behind the counter in the store's colours; the head is its own node (it looks around)."""
    g = Group("Clerk", smooth=True)
    h = 170.0
    hy = person(g, h, t["stripe"], "#2E3440", SKINS[1], "#2B1D14", with_legs=False, apron=t["counter"], head=False)
    # the head is its own node, modelled about its centre (StoreRoom.kt CLERK_HEAD_Y = hy)
    hg = Group("ClerkHead", smooth=True)
    sk = SKINS[1]
    hg.add(ellipsoid((0, 0, 0), (h * 0.065, h * 0.075, h * 0.07), 14, 9), sk)
    hg.add(ellipsoid((0, h * 0.025, -h * 0.01), (h * 0.07, h * 0.06, h * 0.07), 14, 7), "#2B1D14")
    eye = "#1A1420"
    for s in (-1, 1):
        hg.add(ellipsoid((s * h * 0.024, h * 0.005, h * 0.066), (h * 0.008, h * 0.01, h * 0.004), 6, 4), eye)
    hg.add(ellipsoid((0, -h * 0.03, h * 0.064), (h * 0.02, h * 0.006, h * 0.004), 6, 3), "#B5523F")
    hg.add(ellipsoid((0, h * 0.05, 0), (h * 0.072, h * 0.035, h * 0.072), 14, 6), t["stripe"])
    hg.add(ellipsoid((0, h * 0.045, h * 0.07), (h * 0.05, h * 0.008, h * 0.04), 10, 4), t["stripe"])
    return g, hg, hy


def shoppers():
    """A parent and a kid looking into a tank, and a walker crossing the aisle (legs swing)."""
    a = Group("Shopper0", smooth=True)
    person(a, 172.0, "#5B8DEF", "#3B4252", SKINS[0], "#6B4A2B")
    b = Group("Shopper1", smooth=True)
    person(b, 118.0, "#FF8FB1", "#4C566A", SKINS[3], "#E0B04A")
    w = Group("Walker", smooth=True)
    h = 165.0
    person(w, h, "#7BC67E", "#2E3440", SKINS[2], "#1C1410", with_legs=False)
    pa = "#2E3440"
    shoe = "#2A2A30"
    return [a, b, w, leg_node("WalkerLegL", h, pa, shoe, -1), leg_node("WalkerLegR", h, pa, shoe, 1)]


# ============================================================== store dressing (between the bays, by the back wall)
def dressing(t, sid):
    """Two props sets that alternate along the aisle at the bay boundaries (render/StoreRoom.kt): Decor0, Decor1.
    Modelled at the boundary right of the bay (x = X1), against the back wall behind the stands."""
    a, b = Group("Decor0"), Group("Decor1")
    x = X1
    trim = mat("trim", t["trim"], 0.5)
    if sid == "shellys":
        # a bubble wall (a tall clear tube of rising bubbles) and a fish-food endcap with a chalkboard
        a.add(cylinder((x, FLOOR, -40), 9, 150, 18), mat("glass_tube", "#E8F6FF", 0.05, alpha=0.25))
        a.add(cylinder((x, FLOOR + 4, -40), 7.5, 142, 14), mat("water_tube", "#9FD8FF", 0.1, emit="#5FC8FF", strength=0.6, alpha=0.35))
        a.box((x - 1.2, FLOOR + 6, -41.2), (x + 1.2, FLOOR + 146, -38.8), mat("bubbles_wall", "#FFFFFF", 0.3, alpha=0.7))
        a.add(cylinder((x, FLOOR, -40), 11, 8, 18), mat("tube_base", t["stripe"], 0.5))
        a.add(cylinder((x, FLOOR + 150, -40), 10, 5, 18), mat("tube_base", t["stripe"], 0.5))
        b.box((x - 22, FLOOR, -66), (x + 22, 40, -36), trim)
        for y in (FLOOR + 14, FLOOR + 50, FLOOR + 86):
            b.box((x - 21, y, -66), (x + 21, y + 1.5, -36), mat("shelf", "#F7F1E6", 0.6))
            for k in range(4):
                col = ("#E8463C", "#2F8FE0", "#F3C33B", "#45B36B")[(k + int(y)) % 4]
                b.box((x - 19 + k * 10, y + 1.5, -60), (x - 11 + k * 10, y + 16, -48), col)
        b.box((x - 24, 70, BACK), (x + 24, 110, BACK + 1.5), mat("chalk_frame", "#7A5236", 0.7))
        b.box((x - 21, 73, BACK + 1.5), (x + 21, 107, BACK + 1.8), mat("chalkboard", "#24332B", 0.9))
        txt = text("FISH OF\nTHE WEEK", x, 90, BACK + 1.8, 6, mat("chalk", "#F4F1E8", 0.9), depth=0.2)
        return [a, b], [txt]
    if sid == "frag_shack":
        # a surfboard leaning on the wall and stacked salt buckets with an RO/DI barrel
        a.add(ellipsoid((x, 40, -50), (14, 115, 3), 16, 10), mat("surfboard", "#FFB347", 0.4))
        a.box((x - 0.8, -60, -47.2), (x + 0.8, 140, -46.8), mat("stringer", "#5B3A1E", 0.6))
        for k in range(3):
            a.add(cylinder((x + 26, FLOOR + k * 31, -52), 13, 30, 16), mat("salt_bucket", "#F1F3F5", 0.6))
            a.add(cylinder((x + 26, FLOOR + k * 31 + 22, -52), 13.4, 6, 16), mat("bucket_band", "#1E7FD0", 0.5))
        b.add(cylinder((x, FLOOR, -48), 24, 90, 20), mat("rodi_barrel", "#2D63C8", 0.5))
        b.add(cylinder((x, FLOOR + 90, -48), 24.5, 3, 20), trim)
        # a wall shelf of glowing frags under a blue strip light
        b.box((x - 26, 92, -70), (x + 26, 94, -52), mat("shelf", "#3B3F48", 0.6))
        b.box((x - 26, 112, -70), (x + 26, 114, -52), mat("light_housing", "#202226", 0.4, metal=0.6))
        b.box((x - 24, 111.6, -66), (x + 24, 112, -56), mat("lamp_tank", "#FFFFFF", 0.2, emit="#8A7BFF", strength=3.0))
        for k in range(6):
            col = CORAL_COLS[k]
            b.add(cylinder((x - 20 + k * 8, 94, -61), 1.6, 3, 8), mat("plug", "#D9CDB8", 0.85))
            b.add(ellipsoid((x - 20 + k * 8, 98.5, -61), (2.4, 1.8, 2.4), 8, 5), mat(f"glow_{col[1:]}", col, 0.4, emit=col, strength=1.2))
        return [a, b], []
    if sid == "big_blue":
        # a wall of small tanks: a lit rack of 3 x 3 cubes, and an endcap with a big SALE sign
        # an open steel rack: back panel, side posts and a shelf under each row
        a.box((x - 26, FLOOR, -70), (x + 26, 120, -67), trim)
        for dx in (-26, 24):
            a.box((x + dx, FLOOR, -68), (x + dx + 2, 120, -40), trim)
        for r in range(3):
            a.box((x - 26, FLOOR + 20 + r * 40, -68), (x + 26, FLOOR + 22 + r * 40, -40), trim)
        for r in range(3):
            for c in range(3):
                cx, cy = x - 16 + c * 16, FLOOR + 22 + r * 40
                a.box((cx - 7, cy, -63), (cx + 7, cy + 14, -45), mat("water_cube", "#9FD8FF", 0.1, emit="#3FA6FF", strength=0.8, alpha=0.6))
                a.box((cx - 7.3, cy - 0.6, -45), (cx + 7.3, cy + 14.3, -44.4), mat("glass_cube", "#DDEFE4", 0.02, alpha=0.15))
                fish(a, (cx, cy + 7, -46.5), 4.5, FISH_COLS[(r * 3 + c) % len(FISH_COLS)], yaw=0.3 * (c - 1))
        b.box((x - 22, FLOOR, -66), (x + 22, 30, -36), mat("endcap", t["stripe"], 0.6))
        for y in (FLOOR + 20, FLOOR + 60):
            for k in range(4):
                col = ("#FFD24A", "#E8463C", "#45B36B", "#F7F7F7")[(k + int(y)) % 4]
                b.add(cylinder((x - 15 + k * 10, y, -50), 4, 14, 12), col)
        b.box((x - 18, 40, -38), (x + 18, 60, -37), mat("sale_board", "#E8463C", 0.5))
        txt = text("SALE", x, 50, -37, 10, mat("sale_text", "#FFFFFF", 0.4, emit="#FFFFFF", strength=1.0), depth=0.4)
        return [a, b], [txt]
    # deep_reef: velvet rope posts with gold tops, and a lit plinth with a giant clam shell
    gold = mat("gold_post", "#C9A646", 0.25, metal=0.9)
    for dx in (-22, 22):
        a.add(cylinder((x + dx, FLOOR, -20), 2.2, 92, 12), gold)
        a.add(ellipsoid((x + dx, FLOOR + 94, -20), (3.5, 3.5, 3.5), 10, 6), gold)
        a.add(cylinder((x + dx, FLOOR, -20), 9, 2, 16), gold)
    for k in range(9):
        tt = k / 8
        a.add(ellipsoid((x - 22 + 44 * tt, FLOOR + 86 - 10 * math.sin(math.pi * tt), -20), (3, 1.4, 1.4), 6, 4), mat("velvet", "#7A1630", 0.9))
    b.box((x - 18, FLOOR, -58), (x + 18, 20, -30), mat("plinth", "#1C1A20", 0.4))
    b.box((x - 19, 20, -59), (x + 19, 22, -29), gold)
    b.add(ellipsoid((x, 30, -44), (16, 9, 11), 16, 8), mat("clam_shell", "#EDE6D8", 0.5))
    b.add(ellipsoid((x, 34, -44), (13, 3, 9), 14, 6), mat("clam_mantle", "#3F7BFF", 0.35, emit="#2A5BFF", strength=0.4))
    b.box((x - 12, 0, -29), (x + 12, 6, -28.6), mat("gold_plaque", "#C9A646", 0.3, metal=0.9))
    return [a, b], []


def end_wall(t, name, x, sgn):
    g = Group(name)
    g.box((x - (2 if sgn < 0 else 0), FLOOR, BACK - 2), (x + (0 if sgn < 0 else 2), CEIL, FRONT), mat("wall", t["wall"], 0.9))
    face = x if sgn < 0 else x - 0.6
    g.box((face, 58, BACK), (face + 0.6, 72, FRONT), mat("stripe", t["stripe"], 0.5))
    tx = x if sgn < 0 else x - 2
    g.box((tx, FLOOR, BACK), (tx + 2, FLOOR + 10, FRONT), mat("trim", t["trim"], 0.5))
    return g


def sign_mat(t):
    kind = t["sign_kind"]
    if kind == "neon":
        return mat("neon_sign", t["sign"], 0.3, emit=t["sign"], strength=4.0)
    if kind == "gold":
        return mat("gold_sign", t["sign"], 0.25, metal=0.9)
    return mat("sign_paint", t["sign"], 0.5)


# ============================================================== build
def join_into(node, objs):
    """Joins text objects (made by [text]) into a module's node."""
    if not objs:
        return
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    node.select_set(True)
    bpy.context.view_layer.objects.active = node
    bpy.ops.object.join()


def build(sid):
    C.reset_scene()
    _mats.clear()
    t = THEMES[sid]
    root = C.empty("Root")
    C.set_extras(root, units="cm", bay=BAY, floorY=FLOOR, store=sid)
    groups = [bay(t), counter(t), end_wall(t, "EndL", X0, -1), end_wall(t, "EndR", X1, 1), frag_racks()]
    for k, kind in enumerate(KINDS):
        tank, fishes, reef = prop_tank(t, k, kind, sum(map(ord, sid)) + k)
        groups += [stand(t, k, kind), tank, reef, fishes, lamp(t, k, kind)]
    body, head, _ = clerk(t)
    groups += [body, head] + shoppers()
    decor, decor_text = dressing(t, sid)
    groups += decor
    objs = [g.build(root) for g in groups]
    # the store's name across the back wall over the first tank, and CHECKOUT on the counter sign
    sign = text(t["name"], CX, 190, BACK, 17 if len(t["name"]) > 14 else 20, sign_mat(t), depth=1.6)
    sign.name = "Sign"
    sign.parent = root
    co = text("CHECKOUT", CX, 163, 67, 13, mat("sign_text", "#FFFFFF", 0.4, emit="#FFFFFF", strength=1.2), depth=0.8)
    join_into(next(o for o in objs if o.name == "Counter"), [co])
    join_into(next(o for o in objs if o.name == "Decor1"), decor_text)
    objs = list(root.children)
    for o in objs:
        C.triangulate(o)
    path = os.path.join(C.MODELS, f"store_room_{sid}.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[store_rooms] {os.path.basename(path)}: {tris} tris, {os.path.getsize(path) / 1024:.0f} KB")
    return path


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    for sid in args or STORES:
        build(sid)
