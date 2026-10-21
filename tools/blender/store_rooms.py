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

    def __init__(self, name):
        self.name = name
        self.g = WGeo()
        self.mats = []
        self.slots = {}

    def slot(self, m):
        if m.name not in self.slots:
            self.slots[m.name] = len(self.mats)
            self.mats.append(m)
        return self.slots[m.name]

    def box(self, lo, hi, m):
        V, F = box(lo, hi)
        self.g.add(V, F, mat=self.slot(m))

    def add(self, VF, m):
        V, F = VF
        self.g.add(V, F, mat=self.slot(m))

    def build(self, root):
        return self.g.obj(self.name, root, "store", self.mats)


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
        g.add((R, FF), mat(f"fish_{body[1:]}", body, 0.35))


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
    g.box((CX - 42, 84, BACK), (CX + 42, 150, BACK + 1), mat("trim", t["trim"], 0.5))
    g.box((CX - 38, 88, BACK + 1), (CX + 38, 146, BACK + 1.3), mat("poster", t["poster"], 0.7))
    fish(g, (CX, 117, BACK + 1.6), 52, t["art"])
    # hanging tank light: housing, glowing lens, two cables to the ceiling
    g.box((CX - 56, 118, 4), (CX + 56, 125, 42), mat("light_housing", "#202226", 0.4, metal=0.6))
    g.box((CX - 52, 117.4, 8), (CX + 52, 118, 38), mat("lamp_tank", "#FFFFFF", 0.2, emit="#EAF4FF", strength=4.0))
    for x in (CX - 46, CX + 46):
        g.box((x - 0.3, 125, 22.7), (x + 0.3, CEIL, 23.3), mat("cable", "#111111", 0.5))
    # ceiling panel light over the aisle in front of the stand
    g.box((CX - 40, CEIL - 1, 140), (CX + 40, CEIL, 200), mat("lamp_ceiling", "#FFFFFF", 0.2, emit="#FFF6E8", strength=3.0))
    return g


def stand(t):
    g = Group("Stand")
    body = mat("stand", t["stand"], 0.55)
    g.box((-6, FLOOR, -6), (L + 6, -7.6, D + 6), body)
    seam = mat("seam", "#08080A", 0.8)
    for x in (L / 3, 2 * L / 3):
        g.box((x - 0.4, FLOOR + 10, D + 6), (x + 0.4, -12, D + 6.3), seam)
    g.box((-6, -12.4, D + 6), (L + 6, -12, D + 6.3), seam)
    g.box((-4, FLOOR, D + 4), (L + 4, FLOOR + 9, D + 6.4), mat("trim", t["trim"], 0.5))
    knob = mat("gold_knob", "#C9A646", 0.3, metal=0.9)
    for x in (L / 3 - 6, L / 3 + 6, 2 * L / 3 - 6, 2 * L / 3 + 6):
        g.box((x - 1, -40, D + 6.3), (x + 1, -33, D + 7.6), knob)
    # the price-card rail along the top of the doors
    g.box((8, -24, D + 6.3), (L - 8, -16, D + 6.8), mat("label", "#F8F8F4", 0.6))
    return g


def prop_tank(t, seed):
    """A stocked display tank like the live one, for the bays the keeper is not standing at."""
    rng = np.random.default_rng(seed)
    g = Group("PropTank")
    g.box((-4, -7.6, -4), (L + 4, -3.6, D + 4), mat("tank_top", "#3B2518", 0.35))
    g.box((0.4, 0, 0.4), (L - 0.4, 3, D - 0.4), mat("sand", "#EDE3CF", 0.92))
    g.box((-1, -1, -1.2), (L + 1, H + 1, -0.6), mat("backing", "#070809", 0.9))
    rock = mat("rock", "#9C8A78", 0.95)
    for x, w, h in ((24, 22, 18), (62, 30, 24), (98, 20, 15)):
        g.add(ellipsoid((x, 3, 18 + rng.uniform(-3, 3)), (w / 2, h, 12), 12, 7), rock)
    for k, col in enumerate(("#FF6FA8", "#7DFF6A", "#B07CFF", "#FF9A3A", "#5BE0FF", "#FFE45A")):
        x = 14 + k * 19 + rng.uniform(-4, 4)
        g.add(ellipsoid((x, 10 + rng.uniform(0, 14), 26 + rng.uniform(-4, 6)), (4, 3.2, 4), 10, 6), mat(f"coral_{k}", col, 0.5))
    for k, col in enumerate(("#FF7A1A", "#2C6BFF", "#FFD21A", "#FF7A1A", "#9B5CFF")):
        fish(g, (12 + k * 24 + rng.uniform(-5, 5), 18 + rng.uniform(0, 14), 14 + rng.uniform(0, 20)), 7 + rng.uniform(0, 3),
             col, yaw=rng.uniform(-0.5, 0.5) + (math.pi if k % 2 else 0))
    rim = mat("rim", "#141518", 0.45)
    g.box((-0.8, H, -0.8), (L + 0.8, H + 1.2, D + 0.8), rim)
    # glowing water, then the glass panes (both blended)
    g.box((0.3, 3, 0.3), (L - 0.3, C.WATER_Y, D - 0.3), mat("water_prop", t["water"], 0.1, emit=t["water"], strength=0.35, alpha=0.32))
    glass = mat("glass_prop", "#DDEFE4", 0.02, alpha=0.12)
    gt = 0.6
    for lo, hi in (((-gt, -gt, D), (L + gt, H, D + gt)), ((-gt, -gt, 0 - gt), (-0.01, H, D)), ((L + 0.01, -gt, 0 - gt), (L + gt, H, D))):
        g.box(lo, hi, glass)
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
            g.add(cylinder((x, y + 3, BACK + 15), 5, 13 if k % 3 else 17, 14), mat(f"jar_{col[1:]}", col, 0.5))
    g.box((0, FLOOR, 100), (122, FLOOR + 0.4, 170), mat("rug", t["stripe"], 0.95))
    # a potted palm by the end wall
    g.add(cylinder((150, FLOOR, 20), 12, 34, 16), mat("pot", "#B5653E", 0.8))
    leaf = mat("leaf", "#3E9B4A", 0.7)
    for k in range(7):
        a = k * 2 * math.pi / 7
        g.add(ellipsoid((150 + 14 * math.cos(a), FLOOR + 62, 20 + 14 * math.sin(a)), (12 * abs(math.cos(a)) + 3, 4, 12 * abs(math.sin(a)) + 3), 10, 6), leaf)
    g.add(cylinder((150, FLOOR + 30, 20), 2.2, 34, 8), mat("trunk", "#7A5A3A", 0.9))
    return g


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
def build(sid):
    C.reset_scene()
    _mats.clear()
    t = THEMES[sid]
    root = C.empty("Root")
    C.set_extras(root, units="cm", bay=BAY, floorY=FLOOR, store=sid)
    objs = []
    for g in (bay(t), stand(t), prop_tank(t, sum(map(ord, sid))), counter(t),
              end_wall(t, "EndL", X0, -1), end_wall(t, "EndR", X1, 1)):
        objs.append(g.build(root))
    # the store's name across the back wall over the first tank, and CHECKOUT on the counter sign
    sign = text(t["name"], CX, 178, BACK, 17 if len(t["name"]) > 14 else 20, sign_mat(t), depth=1.6)
    sign.name = "Sign"
    sign.parent = root
    co = text("CHECKOUT", CX, 163, 67, 13, mat("sign_text", "#FFFFFF", 0.4, emit="#FFFFFF", strength=1.2), depth=0.8)
    # join CHECKOUT into the counter node
    cnode = next(o for o in objs if o.name == "Counter")
    bpy.ops.object.select_all(action='DESELECT')
    co.select_set(True)
    cnode.select_set(True)
    bpy.context.view_layer.objects.active = cnode
    bpy.ops.object.join()
    objs.append(sign)
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
