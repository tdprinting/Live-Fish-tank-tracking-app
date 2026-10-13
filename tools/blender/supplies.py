"""Supply shelf (docs/ASSETS.md §5.1): supply_shelf.glb, world coordinates (cm, Y up), beside the tank.

A cartoon-but-tasteful driftwood rack with sea-glass trim standing to the right of the tank, three rows of
supplies the live screen's "Supplies" mode looks through (app/.../render/SupplyShelf.kt, ui/live/SupplyShelf.kt):

  row 0 FEED  (top):    one prop per FoodKind (shakers, frozen packs, nori, little bottles)
  row 1 CARE  (middle): bucket, jug, three dropper bottles, filter sock, carbon bag, scissors, syringe, drip cup
  row 2 TOOLS (bottom): Flipper glass cleaner, test kit, microscope, knocker, rock pile

Nodes (direct children of Root, extras.part = "equipment"):
  ShelfFrame, ShelfBack, ShelfTrim     rack, back panel, row lips + crest (extras.row on the lips)
  Supply_<id>                          one node per prop, extras {item: id, row: 0..2}; the runtime picks, lifts
                                       and highlights each one as a whole (every material of the prop is a
                                       primitive of this one node)

Layout constants are mirrored in SupplyShelf.kt (X0..X1, ROW_Y, Z_PROP); keep them in sync. Props stand on their
row board at z = Z_PROP, centred at the x the script prints. Only flat colours (no textures): the runtime adds a
soft self-glow so the shelf reads at night, and lifts / brightens a picked prop.
"""
import math
import os

import numpy as np

import common as C
import geomlib as G
import tank as T
from common import bpy

# ---- layout (mirrored in app/src/main/java/com/reeflive/app/render/SupplyShelf.kt) ----
X0, X1 = 134.0, 236.0          # outer x of the rack
POST = 2.4                     # upright thickness
Z0, Z1 = 2.0, 24.0             # back / front of the rack
Z_PROP = 13.0                  # prop centre line
BOARD = 2.0                    # board thickness
ROW_Y = (36.4, 17.4, -3.6)     # board tops: FEED, CARE, TOOLS
TOP_Y = 52.4                   # underside of the top board
FOOT_Y = -7.6                  # the tank stand's underside (StandTop), so the rack stands at the same level

FEED = ["flake", "pellet", "floating_pellet", "algae_wafer", "mysis", "brine", "krill", "prawn", "cyclops",
        "nori", "coral_food", "plankton", "pods"]
CARE = ["water_change", "water_small", "dose_alk", "dose_ca", "dose_mg", "new_sock", "fresh_carbon",
        "trim_algae", "zap_pest", "settle_in"]
TOOLS = ["glass_cleaner", "test_kit", "microscope", "knocker", "move_rocks"]
ROWS = [FEED, CARE, TOOLS]
TONES = ["#FFB13D", "#8EE66B", "#5FD7FF"]   # LiveTheme Candy: feed orange, care green, tools cyan

_mats = {}


def mat(hexc, rough=0.55):
    key = (hexc, rough)
    if key not in _mats:
        _mats[key] = C.material("shelf_" + hexc.strip("#").lower() + ("" if rough == 0.55 else f"_{int(rough * 100)}"),
                                hexc, rough=rough)
    return _mats[key]


class Prop:
    """World-space geometry of one prop with per-face material colours."""

    def __init__(self):
        self.g = T.WGeo()
        self.cols = []

    def _slot(self, hexc, rough=0.55):
        m = mat(hexc, rough)
        if m not in self.cols:
            self.cols.append(m)
        return self.cols.index(m)

    def add(self, V, F, hexc, rough=0.55):
        self.g.add(np.asarray(V, float), F, col=(1, 1, 1, 1), mat=self._slot(hexc, rough))

    def obj(self, name, parent, extras):
        return self.g.obj(name, parent, "equipment", self.cols, smooth=True, extras=extras)


# ---- primitive builders (world frame, Y up) ----
def cyl(p0, p1, r, sides=14, r1=None):
    pts = np.array([p0, p1], float)
    V, F, _, _ = G.tube(pts, [r, r if r1 is None else r1], sides, cap_start=True, cap_end=True, up=(1, 0, 0))
    V[2 * sides] = pts[1]
    V[2 * sides + 1] = pts[0]
    return V, F


def box(c, size):
    c = np.asarray(c, float)
    s = np.asarray(size, float) / 2
    return T.box(c - s, c + s)


def obox(c, size, xdir, ydir):
    return T.obox(c, size, xdir, ydir)


def ring(c, R, r, n=16, m=6, rot=None):
    """Torus around +Y (horizontal ring) at c; rot (3x3) turns it."""
    V, F = [], []
    for i in range(n):
        a = 2 * math.pi * i / n
        for j in range(m):
            b = 2 * math.pi * j / m
            V.append(((R + r * math.cos(b)) * math.cos(a), r * math.sin(b), (R + r * math.cos(b)) * math.sin(a)))
    for i in range(n):
        for j in range(m):
            a0 = i * m + j
            a1 = ((i + 1) % n) * m + j
            b0 = i * m + (j + 1) % m
            b1 = ((i + 1) % n) * m + (j + 1) % m
            F.append((a0, b0, b1, a1))
    V = np.array(V)
    if rot is not None:
        V = V @ np.asarray(rot).T
    return V + np.asarray(c, float), F


def blob(c, radii, segs=10, rings=6, rot=None):
    return G.ellipsoid(np.asarray(c, float), radii, segs, rings, rot)


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


# ---- props: each builds at base centre (x, y, z) ----
def shaker(p, x, y, z, label, cap, h=7.0, r=2.2):
    p.add(*cyl((x, y, z), (x, y + h, z), r), "#F4F1E6", 0.3)
    p.add(*cyl((x, y + h * 0.28, z), (x, y + h * 0.72, z), r + 0.08), label)
    p.add(*cyl((x, y + h, z), (x, y + h + 1.3, z), r + 0.15), cap, 0.4)
    # shaker holes: three little dots on the cap
    for k in range(3):
        a = 2 * math.pi * k / 3
        p.add(*blob((x + 0.9 * math.cos(a), y + h + 1.32, z + 0.9 * math.sin(a)), (0.35, 0.12, 0.35), 6, 3), "#1B1340")


def frozen_pack(p, x, y, z, colour, cubes="#FFFFFF"):
    # a blister pack leaning back on the back stop, cube bumps on the front
    tilt = rx(-0.18)
    c = np.array([x, y + 4.2, z])
    V, F = obox((0, 0, 0), (5.4, 8.2, 0.9), (1, 0, 0), (0, 1, 0))
    p.add(V @ tilt.T + c, F, colour)
    for i in range(2):
        for j in range(3):
            V, F = obox((-1.25 + 2.5 * i, -2.3 + 2.3 * j, 0.6), (1.8, 1.8, 0.8), (1, 0, 0), (0, 1, 0))
            p.add(V @ tilt.T + c, F, cubes, 0.25)
    V, F = obox((0, 3.55, 0.5), (5.0, 0.6, 0.3), (1, 0, 0), (0, 1, 0))
    p.add(V @ tilt.T + c, F, "#1B1340")


def bottle(p, x, y, z, liquid, cap="#F2F6FF", h=5.2, r=1.7):
    p.add(*cyl((x, y, z), (x, y + h, z), r, 12), liquid, 0.25)
    p.add(*cyl((x, y + h, z), (x, y + h + 1.0, z), r, 12, r1=0.7), liquid, 0.25)
    p.add(*cyl((x, y + h * 0.3, z), (x, y + h * 0.7, z), r + 0.06, 12), "#F2F6FF")
    p.add(*cyl((x, y + h + 1.0, z), (x, y + h + 2.2, z), 0.75, 10), cap, 0.4)
    p.add(*cyl((x, y + h + 2.2, z), (x, y + h + 3.4, z), 0.55, 8, r1=0.15), cap, 0.4)


def build_prop(item, x, y, z):
    p = Prop()
    if item == "flake":
        shaker(p, x, y, z, "#FFB13D", "#FF7AA8")
    elif item == "pellet":
        shaker(p, x, y, z, "#E0792A", "#2E9E96")
    elif item == "floating_pellet":
        shaker(p, x, y, z, "#FFE14D", "#5FD7FF")
    elif item == "algae_wafer":
        shaker(p, x, y, z, "#6BBF59", "#2F6B3A", h=6.0, r=2.4)
        p.add(*cyl((x, y + 3.0, z + 2.45), (x, y + 3.0, z + 2.75), 1.3, 12), "#3F7F35")
    elif item == "mysis":
        frozen_pack(p, x, y, z, "#BFE7FF")
    elif item == "brine":
        frozen_pack(p, x, y, z, "#FFB3C7", "#FFE3EC")
    elif item == "krill":
        frozen_pack(p, x, y, z, "#FF8A65", "#FFD7C8")
    elif item == "prawn":
        frozen_pack(p, x, y, z, "#FFCC80", "#FFF1DD")
    elif item == "cyclops":
        frozen_pack(p, x, y, z, "#E64A4A", "#FF9C9C")
    elif item == "nori":
        # a bag of nori sheets with the green veggie clip on top
        tilt = rx(-0.15)
        c = np.array([x, y + 4.0, z])
        V, F = obox((0, 0, 0), (5.2, 8.0, 1.6), (1, 0, 0), (0, 1, 0))
        p.add(V @ tilt.T + c, F, "#24402A")
        V, F = obox((0, 0.3, 0.85), (3.6, 4.6, 0.2), (1, 0, 0), (0, 1, 0))
        p.add(V @ tilt.T + c, F, "#3E6B3B")
        V, F = obox((0, 4.3, 0), (2.4, 1.6, 2.4), (1, 0, 0), (0, 1, 0))
        p.add(V @ tilt.T + c, F, "#8EE66B", 0.35)
    elif item == "coral_food":
        bottle(p, x, y, z, "#F7A8C8", "#B18CFF")
    elif item == "plankton":
        bottle(p, x, y, z, "#7CCB5A", "#2E9E96")
    elif item == "pods":
        bottle(p, x, y, z, "#E8D2A6", "#FFB13D")
        for k in range(3):
            p.add(*blob((x - 0.7 + 0.7 * k, y + 1.4 + 0.5 * (k % 2), z + 1.72), (0.3, 0.18, 0.12), 6, 3), "#8A6A3A")
    elif item == "water_change":
        # bucket with a handle and a siphon hose curling over the rim
        p.add(*cyl((x, y, z), (x, y + 6.6, z), 3.0, 18, r1=3.6), "#F2F6FF", 0.4)
        p.add(*ring((x, y + 6.6, z), 3.6, 0.28, 18, 6), "#5FD7FF", 0.35)
        p.add(*cyl((x, y + 5.7, z), (x, y + 6.1, z), 3.45, 18), "#5FD7FF", 0.15)
        V, F = ring((0, 0, 0), 3.5, 0.16, 16, 5, rot=rx(math.pi / 2))
        p.add(V + (x, y + 6.6, z), F, "#1B1340")
        pts = [(x + 2.2, y + 4.0, z - 1.0), (x + 2.6, y + 8.2, z - 0.6), (x + 4.2, y + 8.8, z), (x + 4.6, y + 5.0, z + 0.4),
               (x + 4.3, y + 0.8, z + 1.0)]
        V, F, _, _ = G.tube(np.array(pts), 0.32, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V, F, "#BFE7FF", 0.3)
    elif item == "water_small":
        # a jug of fresh salt water with a handle
        p.add(*obox((x, y + 3.6, z), (4.6, 7.2, 4.0), (1, 0, 0), (0, 1, 0)), "#BFE7FF", 0.25)
        p.add(*cyl((x - 0.8, y + 7.2, z), (x - 0.8, y + 8.4, z), 1.0, 12), "#5FD7FF", 0.35)
        p.add(*ring((x + 2.3, y + 4.8, z), 1.6, 0.4, 12, 6, rot=rx(math.pi / 2)), "#BFE7FF", 0.25)
        p.add(*obox((x, y + 3.2, z + 2.05), (3.6, 2.4, 0.1), (1, 0, 0), (0, 1, 0)), "#F2F6FF")
    elif item == "dose_alk":
        bottle(p, x, y, z, "#B18CFF", h=5.8, r=1.9)
    elif item == "dose_ca":
        bottle(p, x, y, z, "#F2F6FF", "#5FD7FF", h=5.8, r=1.9)
    elif item == "dose_mg":
        bottle(p, x, y, z, "#8EE66B", h=5.8, r=1.9)
    elif item == "new_sock":
        # a clean white filter sock hanging from its plastic ring on a little stand
        p.add(*cyl((x, y, z), (x, y + 0.6, z), 2.6, 14), "#2E9E96")
        p.add(*cyl((x - 2.0, y + 0.6, z), (x - 2.0, y + 9.6, z), 0.25, 6), "#2E9E96")
        pts = [(x, y + 8.8, z), (x, y + 6.0, z), (x, y + 3.0, z), (x, y + 1.4, z)]
        V, F, _, _ = G.tube(np.array(pts), [2.1, 2.0, 1.7, 1.2], 14, cap_start=False, cap_end=True, up=(1, 0, 0))
        p.add(V, F, "#FBFBF6", 0.9)
        p.add(*ring((x, y + 9.0, z), 2.2, 0.45, 18, 6), "#FFE14D", 0.35)
    elif item == "fresh_carbon":
        # a mesh bag of carbon, tied at the top
        p.add(*blob((x, y + 2.6, z), (2.9, 2.6, 2.4), 12, 7), "#33363B", 0.9)
        p.add(*cyl((x, y + 4.9, z), (x, y + 6.4, z), 0.6, 8, r1=0.9), "#33363B", 0.9)
        p.add(*ring((x, y + 5.2, z), 0.75, 0.22, 10, 5), "#FF7AA8", 0.35)
        for k in range(5):
            a = 2 * math.pi * k / 5
            p.add(*blob((x + 2.0 * math.cos(a), y + 2.6 + 0.8 * math.sin(2 * a), z + 1.9 * math.sin(a)), (0.4, 0.35, 0.4), 6, 3), "#4A4E55")
    elif item == "trim_algae":
        # scissors standing in a pot
        p.add(*cyl((x, y, z), (x, y + 4.0, z), 2.0, 14, r1=2.4), "#2E9E96", 0.4)
        for s in (-1, 1):
            V, F = obox((0, 0, 0), (0.5, 6.4, 0.25), (1, 0, 0), (0, 1, 0))
            p.add(V @ rz(0.12 * s).T + (x + 0.35 * s, y + 4.6, z), F, "#C9D4DC", 0.2)
            p.add(*ring((x + 1.0 * s, y + 8.6, z), 1.0, 0.32, 12, 6, rot=rx(math.pi / 2)), "#FFB13D", 0.35)
        p.add(*blob((x, y + 7.6, z + 0.2), (0.35, 0.35, 0.35), 6, 3), "#1B1340")
    elif item == "zap_pest":
        # a syringe of pest killer on a little stand
        p.add(*cyl((x, y, z), (x, y + 0.6, z), 2.2, 12), "#B18CFF")
        p.add(*cyl((x, y + 0.6, z), (x, y + 1.2, z), 0.12, 6), "#9AA4AC")
        p.add(*cyl((x, y + 1.2, z), (x, y + 2.2, z), 0.4, 8), "#9AA4AC")
        p.add(*cyl((x, y + 2.2, z), (x, y + 8.2, z), 1.0, 12), "#5FD7FF", 0.2)
        p.add(*cyl((x, y + 8.2, z), (x, y + 8.6, z), 1.9, 12), "#F2F6FF")
        p.add(*cyl((x, y + 8.6, z), (x, y + 10.4, z), 0.3, 6), "#F2F6FF")
        p.add(*cyl((x, y + 10.4, z), (x, y + 10.8, z), 1.3, 12), "#FF7AA8", 0.35)
    elif item == "settle_in":
        # an acclimation drip cup with an airline loop and a happy heart
        p.add(*cyl((x, y, z), (x, y + 5.0, z), 2.2, 14, r1=2.8), "#F2F6FF", 0.3)
        p.add(*cyl((x, y + 4.0, z), (x, y + 4.4, z), 2.7, 14), "#5FD7FF", 0.15)
        pts = [(x + 2.0, y + 4.8, z), (x + 3.0, y + 7.6, z), (x + 1.0, y + 9.2, z - 0.4), (x - 1.6, y + 8.0, z - 0.6)]
        V, F, _, _ = G.tube(np.array(pts), 0.2, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V, F, "#8EE66B", 0.3)
        hy = y + 7.4
        p.add(*blob((x - 0.55, hy, z + 0.6), (0.75, 0.75, 0.55), 8, 5), "#FF5C6C", 0.35)
        p.add(*blob((x + 0.55, hy, z + 0.6), (0.75, 0.75, 0.55), 8, 5), "#FF5C6C", 0.35)
        V, F = G.cone((x, hy + 0.1, z + 0.6), (x, hy - 1.5, z + 0.6), 1.15, 8)
        p.add(V, F, "#FF5C6C", 0.35)
    elif item == "glass_cleaner":
        # the Flipper: the two magnet halves standing together, green handle, black pads
        for k, dz in enumerate((-1.6, 1.6)):
            p.add(*obox((x, y + 7.2, z + dz), (5.6, 14.0, 2.6), (1, 0, 0), (0, 1, 0)), "#8EE66B" if k else "#2E9E96", 0.4)
            p.add(*obox((x, y + 7.2, z + dz * 0.42), (5.0, 13.2, 0.4), (1, 0, 0), (0, 1, 0)), "#1B1340", 0.8)
        p.add(*obox((x, y + 12.2, z + 3.0), (3.2, 1.6, 0.4), (1, 0, 0), (0, 1, 0)), "#F2F6FF")
        p.add(*obox((x, y + 0.4, z), (8.0, 0.8, 7.0), (1, 0, 0), (0, 1, 0)), "#5FD7FF", 0.4)
    elif item == "test_kit":
        # a test kit case with a colour card and four vials
        p.add(*obox((x, y + 2.5, z), (13.0, 5.0, 8.0), (1, 0, 0), (0, 1, 0)), "#F2F6FF", 0.4)
        V, F = obox((0, 0, 0), (13.0, 0.8, 8.0), (1, 0, 0), (0, 1, 0))
        p.add(V @ rx(-1.2).T + (x, y + 8.3, z - 4.2), F, "#5FD7FF", 0.4)
        V, F = obox((0, 0, 0), (9.0, 0.2, 4.0), (1, 0, 0), (0, 1, 0))
        p.add(V @ rx(-1.2).T + (x, y + 8.5, z - 3.6), F, "#FFE14D")
        for k, capc in enumerate(("#B18CFF", "#FF5C6C", "#8EE66B", "#FFB13D")):
            vx = x - 4.2 + 2.8 * k
            p.add(*cyl((vx, y + 5.0, z + 1.2), (vx, y + 9.4, z + 1.2), 0.8, 10), "#DDF3F7", 0.15)
            p.add(*cyl((vx, y + 9.4, z + 1.2), (vx, y + 10.4, z + 1.2), 0.9, 10), capc, 0.35)
    elif item == "microscope":
        # a chunky kid's microscope
        p.add(*obox((x, y + 0.8, z), (9.0, 1.6, 7.0), (1, 0, 0), (0, 1, 0)), "#2E9E96", 0.4)
        p.add(*obox((x, y + 6.0, z - 2.4), (2.2, 9.0, 2.2), (1, 0, 0), (0, 1, 0)), "#F2F6FF", 0.4)
        p.add(*obox((x, y + 5.2, z + 0.6), (6.0, 0.7, 4.6), (1, 0, 0), (0, 1, 0)), "#1B1340", 0.6)
        p.add(*cyl((x, y + 7.2, z + 0.8), (x, y + 13.6, z - 1.0), 1.5, 14), "#F2F6FF", 0.35)
        p.add(*cyl((x, y + 13.6, z - 1.0), (x, y + 15.0, z - 1.4), 1.0, 12), "#1B1340", 0.5)
        p.add(*cyl((x, y + 6.4, z + 1.2), (x, y + 7.4, z + 0.9), 1.1, 12, r1=1.5), "#5FD7FF", 0.3)
        p.add(*cyl((x - 1.6, y + 9.0, z - 2.4), (x + 1.6, y + 9.0, z - 2.4), 1.2, 12), "#FFB13D", 0.35)
    elif item == "knocker":
        # a soft rubber knocker for the glass, leaning on a stand
        p.add(*obox((x, y + 0.4, z), (8.0, 0.8, 6.0), (1, 0, 0), (0, 1, 0)), "#2E9E96", 0.4)
        hdir = np.array([0.35, 1.0, 0.0])
        hdir /= np.linalg.norm(hdir)
        b0 = np.array([x - 2.4, y + 0.8, z])
        b1 = b0 + hdir * 11.0
        p.add(*cyl(b0, b1, 0.55, 10), "#E8D2A6", 0.6)
        side = np.array([1.0, -0.35, 0.0])
        side /= np.linalg.norm(side)
        p.add(*cyl(b1 - side * 2.6, b1 + side * 2.6, 1.7, 14), "#FF7AA8", 0.5)
        p.add(*cyl(b1 + side * 2.6, b1 + side * 3.1, 1.3, 14), "#F2F6FF", 0.5)
    elif item == "move_rocks":
        # a tray of live rock with a pair of rock tongs
        p.add(*obox((x, y + 0.6, z), (13.0, 1.2, 8.0), (1, 0, 0), (0, 1, 0)), "#2E9E96", 0.4)
        p.add(*blob((x - 2.8, y + 3.2, z + 0.4), (3.4, 2.4, 3.0), 10, 6, rot=rz(0.3)), "#CFC2A4", 0.9)
        p.add(*blob((x + 2.6, y + 2.6, z - 0.6), (2.8, 1.9, 2.6), 10, 6, rot=rz(-0.4)), "#B07592", 0.9)
        p.add(*blob((x + 0.2, y + 5.2, z + 0.2), (2.2, 1.6, 2.0), 10, 6, rot=rz(0.8)), "#8A8450", 0.9)
        for s in (-1, 1):
            p.add(*cyl((x + 4.6, y + 1.2, z + 2.0 + 0.5 * s), (x - 1.0, y + 9.0, z + 1.2 + 0.2 * s), 0.25, 6), "#9AA4AC", 0.2)
        p.add(*blob((x + 4.6, y + 1.3, z + 2.0), (0.6, 0.6, 0.9), 6, 3), "#FFB13D")
    else:
        raise ValueError(item)
    return p


def rack(parent):
    wood, board, back = "#D8C3A0", "#E8D8B8", "#2F7F7B"
    frame = Prop()
    xs = X0, X1 - POST
    for xx in xs:
        frame.add(*T.box((xx, FOOT_Y, Z0), (xx + POST, TOP_Y + BOARD + 1.0, Z1)), wood, 0.7)
    for y in ROW_Y:
        frame.add(*T.box((X0 + POST, y - BOARD, Z0), (X1 - POST, y, Z1 - 0.2)), board, 0.7)
    frame.add(*T.box((X0 - 1.0, TOP_Y, Z0 - 1.0), (X1 + 1.0, TOP_Y + BOARD, Z1 + 1.0)), wood, 0.7)
    frame.add(*T.box((X0 + POST, FOOT_Y, Z1 - 3.0), (X1 - POST, ROW_Y[2] - BOARD, Z1 - 1.0)), wood, 0.7)
    o = frame.obj("ShelfFrame", parent, {})
    bp = Prop()
    bp.add(*T.box((X0 + POST, ROW_Y[2] - BOARD, Z0), (X1 - POST, TOP_Y, Z0 + 1.0)), back, 0.8)
    # sea-glass pebbles scattered on the back panel (cartoon touch)
    rng = np.random.default_rng(7)
    for k in range(18):
        px = rng.uniform(X0 + 6, X1 - 6)
        row = k % 3
        py = ROW_Y[row] + rng.uniform(11.0, 14.5)
        col = ["#7FD1C7", "#A8E6CF", "#5FB7D4", "#CDEBF0"][k % 4]
        bp.add(*blob((px, py, Z0 + 1.0), (rng.uniform(0.8, 1.6), rng.uniform(0.6, 1.1), 0.3), 8, 4), col, 0.2)
    ob = bp.obj("ShelfBack", parent, {})
    trims = []
    for r, y in enumerate(ROW_Y):
        t = Prop()
        # rounded row lip along the front edge in the row's candy tone
        V, F, _, _ = G.tube(np.array([(X0 + POST, y + 0.3, Z1 - 0.2), (X1 - POST, y + 0.3, Z1 - 0.2)]), 0.85, 10,
                            cap_start=True, cap_end=True, up=(0, 1, 0))
        t.add(V, F, TONES[r], 0.35)
        trims.append(t.obj(f"ShelfLip{r}", parent, {"row": r}))
    crest = Prop()
    # scalloped crest over the top: a row of half-pebbles in sea-glass colours
    n = 14
    for k in range(n):
        cx = X0 + 4 + (X1 - X0 - 8) * k / (n - 1)
        col = ["#7FD1C7", "#5FD7FF", "#A8E6CF"][k % 3]
        crest.add(*blob((cx, TOP_Y + BOARD + 0.4, Z1 - 1.0), (3.4, 2.4, 1.1), 10, 5), col, 0.25)
    oc = crest.obj("ShelfCrest", parent, {})
    return [o, ob, oc] + trims


def prop_xs(n):
    lo, hi = X0 + POST, X1 - POST
    pitch = (hi - lo) / n
    return [lo + pitch * (i + 0.5) for i in range(n)]


def build():
    C.reset_scene()
    _mats.clear()
    root = C.empty("Root")
    C.set_extras(root, units="cm", spec="docs/ASSETS.md §5.1", shelf=True)
    objs = rack(root)
    layout = {}
    for r, items in enumerate(ROWS):
        for item, x in zip(items, prop_xs(len(items))):
            p = build_prop(item, x, ROW_Y[r], Z_PROP)
            objs.append(p.obj("Supply_" + item, root, {"item": item, "row": r}))
            layout[item] = (round(x, 2), ROW_Y[r])
    for o in objs:
        C.recalc_normals(o)
        C.triangulate(o)
    path = os.path.join(C.MODELS, "supply_shelf.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[supplies] supply_shelf.glb: {tris} tris, {len(layout)} props, {os.path.getsize(path) / 1024:.0f} KB")
    for item, (x, y) in layout.items():
        print(f"  {item:16s} x {x:7.2f}  board {y:6.1f}")
    return tris


if __name__ == "__main__":
    build()
