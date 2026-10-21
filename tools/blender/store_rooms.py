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
import bmesh  # noqa: E402 (after bpy)
import store_fronts as SF
import store_people as SP
from store_geo import Group, cylinder, ellipsoid, fish, mat, text, _mats
from store_people import loft

L, H, D = C.TANK
CX = L / 2
FLOOR = -82.0                    # tank.py stand_geos: the floor under the stand
BACK = -70.0
FRONT = 540.0                    # the front wall's inner face (store_fronts.py); the aisle camera stays inside it
STORES = ["shellys", "frag_shack", "big_blue", "deep_reef"]

# Each store's size and quality (docs/research/fish_stores.md): bay pitch (cm, the renderer reads it from the root's
# extras), ceiling height (world y), and its tier, which picks the floor, ceiling and wall finish: a small-town pet shop,
# a garage frag shop, a big-box aquarium store, a gallery of rare livestock.
CONFIG = {
    "shellys": dict(bay=165.0, ceil=262.0, tier=0),
    "frag_shack": dict(bay=175.0, ceil=300.0, tier=1),
    "big_blue": dict(bay=190.0, ceil=400.0, tier=2),
    "deep_reef": dict(bay=240.0, ceil=340.0, tier=3),
}
# set per store by configure()
BAY = 180.0
X0, X1 = CX - BAY / 2, CX + BAY / 2
CEIL = 300.0


def configure(sid):
    global BAY, X0, X1, CEIL
    cfg = CONFIG[sid]
    BAY, CEIL = cfg["bay"], cfg["ceil"]
    X0, X1 = CX - BAY / 2, CX + BAY / 2


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
# ============================================================== modules
def bay(t, sid):
    """One bay of the shop in the store's own finish: floor, back wall, ceiling and its light, a poster over the tank."""
    g = Group("Bay")
    tier = CONFIG[sid]["tier"]
    floor = ("floor_tile", "floor_concrete", "floor_tile_big", "floor_marble")[tier]
    g.box((X0, FLOOR - 1, BACK), (X1, FLOOR, FRONT), mat(floor, t["floor"], 0.45))
    g.box((X0, FLOOR, BACK - 2), (X1, CEIL, BACK), mat("wall", t["wall"], 0.9))
    g.box((X0, FLOOR, BACK), (X1, FLOOR + 10, BACK + 2), mat("trim", t["trim"], 0.5))
    g.box((X0, CEIL, BACK - 2), (X1, CEIL + 2, FRONT), mat("ceiling", t["ceil"], 0.9))
    trim = mat("trim", t["trim"], 0.5)
    lamp = mat("lamp_ceiling", "#FFFFFF", 0.2, emit="#FFF6E8", strength=3.0)
    if tier == 0:
        # a strip-mall pet shop: a painted stripe, a wainscot, a drop ceiling with a troffer over the aisle
        g.box((X0, 58, BACK), (X1, 72, BACK + 0.6), mat("stripe", t["stripe"], 0.5))
        g.box((X0, FLOOR + 10, BACK), (X1, 0, BACK + 0.8), mat("wainscot", "#E2CFAE", 0.8))
        grid = mat("ceiling_grid", "#E9E4DA", 0.6)
        for x in np.arange(X0, X1 + 0.1, 60.0):
            g.box((x - 0.8, CEIL - 1.2, BACK), (x + 0.8, CEIL, FRONT), grid)
        for z in np.arange(BACK, FRONT + 0.1, 60.0):
            g.box((X0, CEIL - 1.2, z - 0.8), (X1, CEIL, z + 0.8), grid)
        g.box((CX - 40, CEIL - 1.6, 140), (CX + 40, CEIL - 1.2, 200), lamp)
    elif tier == 1:
        # a garage: plywood panels on the wall, open rafters, a shop light and a string of bulbs over the aisle
        g.box((X0, 58, BACK), (X1, 64, BACK + 0.6), mat("stripe", t["stripe"], 0.5))
        for x in np.arange(X0 + 60, X1, 120.0):
            g.box((x - 0.5, FLOOR + 10, BACK), (x + 0.5, CEIL, BACK + 0.5), mat("seam", "#08080A", 0.8))
        wood = mat("rafter", "#5A4632", 0.85)
        for x in np.arange(X0 + 15, X1, 45.0):
            g.box((x - 3, CEIL - 22, BACK), (x + 3, CEIL, FRONT), wood)
        g.box((CX - 60, CEIL - 34, 165), (CX + 60, CEIL - 30, 175), mat("light_housing", "#202226", 0.4, metal=0.6))
        g.box((CX - 58, CEIL - 34.4, 167), (CX + 58, CEIL - 34, 173), lamp)
        for x in (CX - 50, CX + 50):
            g.box((x - 0.3, CEIL - 30, 169.7), (x + 0.3, CEIL - 22, 170.3), mat("cable", "#111111", 0.5))
        bulb = mat("lamp_bulb", "#FFE2A8", 0.3, emit="#FFC870", strength=3.0)
        cord = mat("cable", "#111111", 0.5)
        n = 7
        for k in range(n):
            u0, u1 = k / n, (k + 1) / n
            x0, x1 = X0 + BAY * u0, X0 + BAY * u1
            y0 = CEIL - 26 - 22 * math.sin(math.pi * u0)
            y1 = CEIL - 26 - 22 * math.sin(math.pi * u1)
            g.add(_rod((x0, y0, 250), (x1, y1, 250), 0.25), cord)
            g.add(ellipsoid((x0 + (x1 - x0) / 2, (y0 + y1) / 2 - 3, 250), (1.6, 2.2, 1.6), 8, 6), bulb)
    elif tier == 2:
        # a big box: a clean blue band, an open joist ceiling with a duct and high-bay lamps
        g.box((X0, 58, BACK), (X1, 74, BACK + 0.6), mat("stripe", t["stripe"], 0.5))
        g.box((X0, 200, BACK), (X1, 204, BACK + 0.6), mat("stripe", t["stripe"], 0.5))
        steel = mat("joist", "#7E858E", 0.5, metal=0.0)
        for x in np.arange(X0 + 20, X1, 95.0):
            g.box((x - 1.5, CEIL - 30, BACK), (x + 1.5, CEIL, FRONT), steel)
        g.add(_duct((X0, CEIL - 60, 40), (X1, CEIL - 60, 40), 22), mat("duct", "#B9C0C8", 0.35))
        g.add(cylinder((CX, CEIL - 70, 170), 26, 18, 24), mat("light_housing", "#202226", 0.4, metal=0.6))
        g.add(cylinder((CX, CEIL - 70.4, 170), 22, 0.4, 24), lamp)
        g.box((CX - 0.4, CEIL - 52, 169.6), (CX + 0.4, CEIL, 170.4), mat("cable", "#111111", 0.5))
    else:
        # a gallery: dark wood panels with brass pinstripes, a crown moulding, a coffered ceiling with spots
        gold = mat("gold_trim", "#C9A646", 0.25, metal=0.9)
        g.box((X0, 58, BACK), (X1, 60, BACK + 0.8), gold)
        for x in np.arange(X0 + 40, X1, 80.0):
            g.box((x - 0.6, 60, BACK), (x + 0.6, CEIL - 20, BACK + 0.8), gold)
        g.box((X0, CEIL - 20, BACK), (X1, CEIL, BACK + 8), mat("crown", "#2A2420", 0.5))
        g.box((X0, CEIL - 21, BACK + 8), (X1, CEIL - 20, BACK + 9), gold)
        beam = mat("coffer", "#1A1720", 0.6)
        for x in np.arange(X0, X1 + 0.1, BAY / 2):
            g.box((x - 5, CEIL - 18, BACK), (x + 5, CEIL, FRONT), beam)
        for z in np.arange(BACK, FRONT + 0.1, 120.0):
            g.box((X0, CEIL - 18, z - 5), (X1, CEIL, z + 5), beam)
        for x in (CX - BAY / 4, CX + BAY / 4):
            for z in (150.0, 190.0):
                g.add(cylinder((x, CEIL - 3, z), 6, 3, 14), mat("light_housing", "#202226", 0.4, metal=0.6))
                g.add(cylinder((x, CEIL - 3.2, z), 4.4, 0.2, 14), lamp)
    # poster over the tank: a framed print of a big fish
    g.box((CX - 42, 104, BACK), (CX + 42, 160, BACK + 1), trim)
    g.box((CX - 38, 108, BACK + 1), (CX + 38, 156, BACK + 1.3), mat("poster", t["poster"], 0.7))
    fish(g, (CX, 132, BACK + 1.6), 46, t["art"])
    return g


def _rod(a, b, r):
    """A thin round rod from [a] to [b]."""
    return loft([a, b], [r, r], [r, r], 6)


def _duct(a, b, r):
    return loft([a, b], [r, r], [r, r], 18)


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


# ============================================================== the entry bay and the back of the shop
def entry(t, sid):
    """The bay left of the first tank, just inside the door: what a store shows you first (docs/research/fish_stores.md:
    the community board, the dry goods, a bench; a big store's carts, a gallery's lounge)."""
    g = Group("Entry")
    trim = mat("trim", t["trim"], 0.5)
    cork = mat("cork", "#B98A5A", 0.9)
    def board(x, y, w, h, z=BACK):
        g.box((x - w / 2 - 3, y - h / 2 - 3, z), (x + w / 2 + 3, y + h / 2 + 3, z + 1.5), trim)
        g.box((x - w / 2, y - h / 2, z + 1.5), (x + w / 2, y + h / 2, z + 1.8), cork)
        for k in range(7):
            col = ("#FFFFFF", "#FFE45A", "#9DF2FF", "#FF9EC2", "#B7F59A")[k % 5]
            fx = x - w / 2 + 10 + (k * 37) % (w - 20)
            fy = y - h / 2 + 10 + (k * 23) % (h - 24)
            g.box((fx - 6, fy - 8, z + 1.8), (fx + 6, fy + 8, z + 2.0), mat(f"flyer_{col[1:]}", col, 0.8))
            g.add(ellipsoid((fx, fy + 7, z + 2.2), (0.8, 0.8, 0.5), 6, 4), mat("pin", "#E23B3B", 0.4))
    def shelf_unit(x, w, rows, z0=BACK, depth=36, top=150):
        g.box((x - w / 2, FLOOR, z0), (x + w / 2, top, z0 + 3), trim)
        for dx in (-w / 2, w / 2 - 2):
            g.box((x + dx, FLOOR, z0), (x + dx + 2, top, z0 + depth), trim)
        for r in range(rows):
            y = FLOOR + 12 + r * (top - FLOOR - 20) / rows
            g.box((x - w / 2, y, z0), (x + w / 2, y + 2, z0 + depth), mat("shelf", "#F7F1E6", 0.6))
            for k in range(int(w // 12)):
                col = ("#E8463C", "#2F8FE0", "#F3C33B", "#45B36B", "#9A5CE6", "#F7F7F7")[(k * 3 + r) % 6]
                bx = x - w / 2 + 6 + k * 12
                if (k + r) % 3 == 0:
                    g.add(cylinder((bx, y + 2, z0 + depth / 2), 4.4, 14, 12), mat(f"pack_{col[1:]}", col, 0.6))
                else:
                    g.box((bx - 4.5, y + 2, z0 + 8), (bx + 4.5, y + 20, z0 + depth - 6), mat(f"pack_{col[1:]}", col, 0.6))
    if sid == "shellys":
        board(CX - 30, 120, 90, 60)
        shelf_unit(CX + 45, 70, 4)
        # a gumball machine and a bin of dog toys by the door
        g.add(cylinder((X0 + 30, FLOOR, 360), 9, 70, 16), mat("gumball_stand", "#E23B3B", 0.4))
        g.add(ellipsoid((X0 + 30, FLOOR + 84, 360), (14, 14, 14), 16, 10), mat("glass_gumball", "#F4FBFF", 0.04, alpha=0.3))
        for k in range(14):
            a = k * 2.4
            col = CORAL_COLS[k % len(CORAL_COLS)]
            g.add(ellipsoid((X0 + 30 + 8 * math.cos(a) * (k % 3) / 2, FLOOR + 76 + (k // 4) * 4, 360 + 8 * math.sin(a) * (k % 3) / 2), (2, 2, 2), 6, 4), col)
        g.add(cylinder((X0 + 75, FLOOR, 400), 22, 40, 18), mat("toy_bin", "#2BA7A0", 0.6))
        for k in range(5):
            g.add(ellipsoid((X0 + 65 + k * 5, FLOOR + 42, 395 + (k % 2) * 8), (4, 4, 4), 8, 6), ("#FFE45A", "#E8463C", "#5BE0FF")[k % 3])
    elif sid == "frag_shack":
        # a whiteboard of today's frags, a sofa made of a pallet and cushions, a mini fridge of frozen food
        g.box((CX - 50, 90, BACK), (CX + 30, 150, BACK + 1.4), mat("whiteboard", "#F7F8F8", 0.3))
        txt = text("FRAGS\nTODAY", CX - 10, 120, BACK + 1.4, 9, mat("marker", "#2F55D4", 0.6), depth=0.2)
        g.box((CX + 40, FLOOR, BACK), (CX + 80, FLOOR + 80, BACK + 40), mat("fridge", "#E9ECEF", 0.4))
        g.box((CX + 42, FLOOR + 50, BACK + 40), (CX + 78, FLOOR + 76, BACK + 40.4), mat("glass_fridge", "#DDEFFF", 0.05, alpha=0.3))
        g.box((X0 + 20, FLOOR, 40), (X0 + 150, FLOOR + 22, 100), mat("pallet", "#B38B5A", 0.9))
        g.box((X0 + 24, FLOOR + 22, 44), (X0 + 146, FLOOR + 36, 96), mat("cushion", "#3DD9B8", 0.95))
        g.box((X0 + 24, FLOOR + 36, 40), (X0 + 146, FLOOR + 70, 54), mat("cushion", "#3DD9B8", 0.95))
        return g, [txt]
    elif sid == "big_blue":
        # carts by the door, a gondola of dry goods, the free water testing station
        for k in range(3):
            _cart(g, X0 + 40 + k * 9, 330 + k * 6)
        shelf_unit(CX - 10, 120, 5, top=170)
        g.box((CX + 60, FLOOR, BACK), (CX + 92, 30, BACK + 40), mat("counter", t["counter"], 0.6))
        g.box((CX + 58, 30, BACK - 2), (CX + 94, 33, BACK + 42), mat("counter_top", t["top"], 0.35))
        for k in range(5):
            g.add(cylinder((CX + 64 + k * 6, 33, BACK + 24), 1.2, 7, 8), mat("glass_vial", "#DDEFFF", 0.05, alpha=0.35))
        g.box((CX + 60, 110, BACK), (CX + 92, 134, BACK + 1.2), mat("sale_board", "#E8463C", 0.5))
        txt = text("FREE\nWATER TEST", CX + 76, 122, BACK + 1.2, 4.5, mat("sign_text", "#FFFFFF", 0.4, emit="#FFFFFF", strength=1.0), depth=0.3)
        return g, [txt]
    else:
        # a lounge: two leather chairs, a low table with a coffee book, a tall vase of orchids
        leather = mat("leather", "#5A2E1E", 0.45)
        for dx in (-40, 40):
            x = CX + dx
            g.box((x - 24, FLOOR, 60), (x + 24, FLOOR + 40, 108), leather)
            g.box((x - 24, FLOOR + 40, 60), (x + 24, FLOOR + 92, 72), leather)
            for s in (-1, 1):
                g.box((x + s * 24 - (6 if s > 0 else 0), FLOOR + 40, 60), (x + s * 24 + (0 if s > 0 else 6), FLOOR + 60, 108), leather)
        g.box((CX - 22, FLOOR, 128), (CX + 22, FLOOR + 36, 158), mat("crown", "#2A2420", 0.5))
        g.box((CX - 23, FLOOR + 36, 127), (CX + 23, FLOOR + 38, 159), mat("gold_trim", "#C9A646", 0.25, metal=0.9))
        g.box((CX - 10, FLOOR + 38, 136), (CX + 10, FLOOR + 41, 150), mat("book", "#1F6FD1", 0.6))
        g.add(cylinder((X0 + 34, FLOOR, 10), 10, 60, 16), mat("vase", "#F4F1EA", 0.2))
        for k in range(5):
            a = k * 1.25
            g.add(_rod((X0 + 34, FLOOR + 60, 10), (X0 + 34 + 12 * math.cos(a), FLOOR + 120 + k * 6, 10 + 12 * math.sin(a)), 0.5), mat("stem", "#3E6B3A", 0.7))
            g.add(ellipsoid((X0 + 34 + 12 * math.cos(a), FLOOR + 122 + k * 6, 10 + 12 * math.sin(a)), (4, 3, 4), 8, 6), mat("orchid", "#F4E8F4", 0.6))
        # a framed print of a coelacanth over the chairs and a little brass plaque
        g.box((CX - 50, 100, BACK), (CX + 50, 170, BACK + 2), mat("gold_trim", "#C9A646", 0.25, metal=0.9))
        g.box((CX - 46, 104, BACK + 2), (CX + 46, 166, BACK + 2.3), mat("poster", t["poster"], 0.7))
        fish(g, (CX, 135, BACK + 2.6), 70, "#3A5A8A")
    return g, []


def _cart(g, x, z):
    """A shopping cart parked by the door, facing into the store."""
    wire = mat("cart", "#B9C0C8", 0.35)
    g.box((x - 25, FLOOR + 50, z - 40), (x + 25, FLOOR + 52, z + 40), wire)
    for dx in (-25, 23):
        g.box((x + dx, FLOOR + 50, z - 40), (x + dx + 2, FLOOR + 90, z + 40), wire)
    g.box((x - 25, FLOOR + 50, z + 38), (x + 25, FLOOR + 90, z + 40), wire)
    g.box((x - 25, FLOOR + 50, z - 40), (x + 25, FLOOR + 90, z - 38), wire)
    g.box((x - 25, FLOOR + 96, z - 50), (x + 25, FLOOR + 100, z - 46), mat("cart_handle", "#E8463C", 0.5))
    for dx in (-20, 20):
        for dz in (-34, 34):
            g.add(cylinder((x + dx, FLOOR, z + dz), 4, 8, 10), mat("out_tyre", "#1A1A1C", 0.8))
            g.box((x + dx - 1, FLOOR + 8, z + dz - 1), (x + dx + 1, FLOOR + 50, z + dz + 1), wire)


def back(t, sid):
    """The bay right of the counter: the door to the back, and what each store keeps there (docs/research/fish_stores.md:
    the fish room and quarantine racks, the RO/DI station, a gallery's espresso bar)."""
    g = Group("Back")
    trim = mat("trim", t["trim"], 0.5)
    dx = CX - 30
    g.box((dx - 50, FLOOR, BACK), (dx + 50, 134, BACK + 3), trim)
    g.box((dx - 45, FLOOR, BACK + 3), (dx + 45, 128, BACK + 4.5), mat("door_back", t["stand"], 0.55))
    g.box((dx + 30, 20, BACK + 4.5), (dx + 34, 34, BACK + 7), mat("gold_knob", "#C9A646", 0.3, metal=0.9))
    sign = mat("sign_board", t["stripe"], 0.5)
    g.box((dx - 34, 96, BACK + 4.5), (dx + 34, 116, BACK + 5), sign)
    words = {"shellys": "STAFF ONLY", "frag_shack": "FRAG LAB", "big_blue": "FISH ROOM", "deep_reef": "PRIVATE"}[sid]
    txt = text(words, dx, 106, BACK + 5, 7, mat("sign_text", "#FFFFFF", 0.4, emit="#FFFFFF", strength=1.0), depth=0.3)
    x = CX + 60
    if sid == "shellys":
        # a bird cage on a stand and bags of dog food
        g.add(cylinder((x, FLOOR, 0), 2, 110, 8), trim)
        cage = mat("cage", "#D8D2C0", 0.4, metal=0.0)
        for k in range(12):
            a = k * math.pi / 6
            g.add(_rod((x + 18 * math.cos(a), 28, 18 * math.sin(a)), (x + 14 * math.cos(a), 74, 14 * math.sin(a)), 0.4), cage)
        g.add(cylinder((x, 26, 0), 19, 2, 18), cage)
        g.add(ellipsoid((x, 76, 0), (15, 8, 15), 14, 6), cage)
        g.add(ellipsoid((x + 2, 46, 0), (3.5, 5, 3.5), 10, 6), "#5BC85A")
        g.add(ellipsoid((x + 2, 52, 1.6), (2.8, 2.6, 2.6), 10, 6), "#F3C33B")
        for k in range(3):
            g.box((x - 50 + k * 6, FLOOR + k * 22, 60), (x - 14 + k * 6, FLOOR + 22 + k * 22, 86), mat("food_sack", ("#C0563E", "#2F8FE0", "#45B36B")[k], 0.8))
    elif sid == "frag_shack":
        # the RO/DI filters on the wall with their blue canisters and the barrel under them
        for k in range(4):
            g.add(cylinder((x - 20 + k * 14, 50, BACK + 10), 5, 32, 14), mat("rodi_canister", "#DDEFFF" if k < 2 else "#5FA8E8", 0.2))
        g.box((x - 30, 86, BACK + 2), (x + 34, 90, BACK + 16), trim)
        g.add(cylinder((x, FLOOR, -30), 26, 100, 20), mat("rodi_barrel", "#2D63C8", 0.5))
    elif sid == "big_blue":
        # the quarantine rack through the fish room window: rows of little tanks glowing blue
        g.box((x - 46, 20, BACK), (x + 46, 120, BACK + 2), mat("light_housing", "#202226", 0.4, metal=0.6))
        for r in range(3):
            for c in range(4):
                cx, cy = x - 33 + c * 22, 26 + r * 32
                g.box((cx - 9, cy, BACK + 2), (cx + 9, cy + 24, BACK + 2.6), mat("water_qt", "#9FD8FF", 0.1, emit="#3FA6FF", strength=0.9, alpha=0.7))
        g.box((x - 48, 18, BACK + 2.6), (x + 48, 122, BACK + 3.2), mat("glass_window", "#DDEFE4", 0.02, alpha=0.15))
        g.box((x - 40, FLOOR, 10), (x + 40, FLOOR + 14, 70), mat("pallet", "#B38B5A", 0.9))
        for k in range(6):
            g.box((x - 36 + (k % 3) * 25, FLOOR + 14 + (k // 3) * 22, 14), (x - 14 + (k % 3) * 25, FLOOR + 36 + (k // 3) * 22, 66), mat("salt_box", "#F1F3F5", 0.6))
    else:
        # the espresso bar: a marble counter, a brass machine, little cups
        g.box((x - 40, FLOOR, BACK), (x + 50, 18, BACK + 50), mat("crown", "#2A2420", 0.5))
        g.box((x - 42, 18, BACK - 2), (x + 52, 21, BACK + 52), mat("floor_marble_top", "#E8E2D8", 0.25))
        g.box((x - 10, 21, BACK + 10), (x + 22, 52, BACK + 36), mat("gold_espresso", "#C9A646", 0.25, metal=0.9))
        g.add(cylinder((x + 6, 52, BACK + 23), 6, 6, 14), mat("gold_espresso", "#C9A646", 0.25, metal=0.9))
        for k in range(4):
            g.add(cylinder((x - 32 + k * 8, 21, BACK + 40), 2.6, 5, 12), mat("cup", "#F4F1EA", 0.3))
    return g, [txt]


def end_wall(t, name, x, sgn):
    g = Group(name)
    g.box((x - (2 if sgn < 0 else 0), FLOOR, BACK - 2), (x + (0 if sgn < 0 else 2), CEIL + 70, FRONT + SF.WALL), mat("wall", t["wall"], 0.9))
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


# ============================================================== baked shading
def bake_ao(sets, samples=24):
    """Bakes ambient occlusion into the vertex colour of each set of objects on its own (a person's six parts, the pet,
    a prop), so armpits, under the chin, between the legs and the folds of clothes are softly shaded like the Cycles
    previews, at no runtime cost (store.frag multiplies COLOR_0 in, kind 9)."""
    scn = bpy.context.scene
    scn.render.engine = 'CYCLES'
    scn.cycles.device = 'CPU'
    scn.cycles.samples = samples
    scn.render.bake.target = 'VERTEX_COLORS'
    if scn.world is None:
        scn.world = bpy.data.worlds.new("bake")
    scn.world.light_settings.distance = 14.0      # cm: creases and folds, not the whole figure
    meshes = [o for o in bpy.data.objects if o.type == 'MESH']
    for objs in sets:
        objs = [o for o in objs if o is not None and "Col" in o.data.color_attributes]
        if not objs:
            continue
        for o in meshes:
            o.hide_render = o not in objs
        for o in objs:
            me = o.data
            # the builders don't care about winding (store.frag lights both sides); the bake's rays need outward faces
            bm = bmesh.new()
            bm.from_mesh(me)
            bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
            bm.to_mesh(me)
            bm.free()
            col = me.color_attributes["Col"]
            ao = me.color_attributes.new("AO", 'FLOAT_COLOR', 'POINT')
            me.color_attributes.active_color = ao
            bpy.ops.object.select_all(action='DESELECT')
            o.select_set(True)
            bpy.context.view_layer.objects.active = o
            bpy.ops.object.bake(type='AO')
            if os.environ.get("AO_DEBUG"):
                v = [a.color[0] for a in ao.data]
                print("[ao]", o.name, round(min(v), 3), round(sorted(v)[len(v) // 2], 3), round(max(v), 3))
            # normalise by the part's own bright end (layered clothes shells occlude each other), then soften with a
            # 0.55 floor so creases darken but nothing turns black
            v = sorted(a.color[0] for a in ao.data)
            top = max(v[int(len(v) * 0.9)], 0.05)
            for c, a in zip(col.data, ao.data):
                k = 0.55 + 0.45 * min(1.0, a.color[0] / top)
                r, g, b, al = c.color
                c.color = (r * k, g * k, b * k, al)
            me.color_attributes.remove(me.color_attributes["AO"])
            me.color_attributes.active_color = me.color_attributes["Col"]
    for o in meshes:
        o.hide_render = False


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
    configure(sid)
    t = THEMES[sid]
    st = SF.STYLES[sid]
    root = C.empty("Root")
    C.set_extras(root, units="cm", bay=BAY, floorY=FLOOR, ceil=CEIL, front=FRONT, wall=SF.WALL, store=sid,
                 door=st["door"], doorW=st["door_w"], doorH=st["door_h"])
    groups = [bay(t, sid), counter(t), end_wall(t, "EndL", X0, -1), end_wall(t, "EndR", X1, 1), frag_racks()]
    for k, kind in enumerate(KINDS):
        tank, fishes, reef = prop_tank(t, k, kind, sum(map(ord, sid)) + k)
        groups += [stand(t, k, kind), tank, reef, fishes, lamp(t, k, kind)]
    decor, decor_text = dressing(t, sid)
    groups += decor
    ent, ent_text = entry(t, sid)
    bk, bk_text = back(t, sid)
    groups += [ent, bk]
    # the people (core StoreLife cast, slot order), what they carry, the shop pet
    for slot, look in enumerate(SP.CASTS[sid]):
        groups += SP.person(slot, look)
    groups += SP.props()
    pet = {"shellys": "cat", "frag_shack": "dog"}.get(sid)
    if pet:
        groups += SP.pet(pet)
    # outside: the front wall bay by bay, the door, the street and the neighbours
    f = SF.Front(X0, X1, CX, FLOOR, CEIL, FRONT)
    sign, sign_text = SF.front_sign(f, sid, t["name"])
    groups += [SF.front_bay(f, sid, "FrontBay"), SF.front_bay(f, sid, "FrontDoor"), sign, SF.street(f, sid),
               SF.neighbor(f, sid), SF.sky(f), SF.roof(f)] + SF.doors(f, sid)
    objs = [g.build(root) for g in groups if not g.empty() or g.name == "FrontSign"]
    # the store's name across the back wall over the first tank, and CHECKOUT on the counter sign
    sign = text(t["name"], CX, 190, BACK, 17 if len(t["name"]) > 14 else 20, sign_mat(t), depth=1.6)
    sign.name = "Sign"
    sign.parent = root
    co = text("CHECKOUT", CX, 163, 67, 13, mat("sign_text", "#FFFFFF", 0.4, emit="#FFFFFF", strength=1.2), depth=0.8)
    by = {o.name: o for o in objs}
    sets = [[by.get(f"P{slot}_{p}") for p in ("Torso", "Head", "ArmL", "ArmR", "LegL", "LegR")] for slot in range(len(SP.CASTS[sid]))]
    sets += [[by.get("Pet_Body"), by.get("Pet_Tail")], [by.get("Pet_Sleep")]]
    sets += [[o] for n, o in by.items() if n.startswith("Prop_")]
    if "--no-bake" not in sys.argv:
        bake_ao(sets)
    join_into(by["Counter"], [co])
    join_into(by["Decor1"], decor_text)
    join_into(by["Entry"], ent_text)
    join_into(by["Back"], bk_text)
    join_into(by["FrontSign"], sign_text)
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
