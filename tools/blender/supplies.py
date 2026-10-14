"""Supply shelf (docs/ASSETS.md §5.1): supply_shelf.glb, world coordinates (cm, Y up), beside the tank.

A warm driftwood rack with sea-glass edging standing to the right of the tank, three rows of supplies the live
screen's "Supplies" mode looks through (app/.../render/SupplyShelf.kt, ui/live/SupplyShelf.kt). Every prop is a
look-alike of a real reef-keeping product, in its real container shape with its packaging printed on it
(supply_labels.py paints the labels):

  row 0 FEED  (top):    Ocean Nutrition Formula Two Flakes jar, Formula One Pellets jar, Hikari Marine S pouch
                        (floats, then sinks), Hikari Mini Algae Wafers pouch, Hikari Bio-Pure Mysis blister card,
                        San Francisco Bay Brand Brine Shrimp flat pack, BRS Krill Superba jar, a frozen prawn bag,
                        Hikari Bio-Pure Cyclops card, Two Little Fishies Sea Veggies bag, Polyp Lab Reef-Roids pouch,
                        Reef Nutrition Phyto-Feast bottle, AlgaeBarn 5280 Pods mason jar
  row 1 CARE  (middle): Red Sea Coral Pro Salt bucket (big change), Instant Ocean Sea Salt bag (small change),
                        Red Sea Reef Foundation B / A / C bottles (alk / calcium / magnesium), BRS felt filter sock
                        with its tag, Seachem Matrix Carbon jar, curved scissors on a backing card, Red Sea
                        Aiptasia-X kit (box, bottle, syringe), a drip-acclimation cup with airline and valve
  row 2 TOOLS (bottom): Flipper Edge magnet and its box, API Reef Master test kit tub + Salifert KH box and a vial,
                        student microscope, rubber mallet, CaribSea LifeRock carton with rock and tongs

Labels come from one 2048² atlas (supply_labels.py, JPEG) UV-mapped onto wrap bands, pouch fronts, box faces and
cards; it also holds the lit "Supplies" sign and the soft backdrop of the back panel. The runtime names every prop on
screen too (Compose name tags under each prop), so the print is flavour, not the only cue.

Nodes (direct children of Root, extras.part = "equipment"):
  ShelfFrame, ShelfBack, ShelfCrest    driftwood rack, back panel (backdrop print), sea-glass crest over the top
  ShelfLip0..2                         sea-glass row edging (extras.row), tinted by row (feed amber, care green, tools blue)
  ShelfSign                            the "Supplies" sign and the warm puck lights under the boards; drawn lit
                                       (SupplyShelfLayout.SIGN_NODE)
  Supply_<id>                          one node per prop, extras {item: id, row: 0..2}; the runtime picks, lifts
                                       and highlights each one as a whole (every material of the prop is a
                                       primitive of this one node)

Layout constants are mirrored in SupplyShelf.kt (X0..X1, SLOT_X0..SLOT_X1, ROW_Y, Z_PROP, ROW_COUNT); keep them in
sync. Props stand on their row board at z = Z_PROP, centred in equal slots (prop_xs) and no wider than a slot. The
runtime adds a soft self-glow so the shelf reads at night (less on printed labels, more on the sign), and lifts /
brightens a picked prop.

  python3 tools/blender/supplies.py [--preview]      # builds the GLB (+ docs/preview/supply_shelf_v3.png)
"""
import math
import os
import sys

import numpy as np

import common as C
import geomlib as G
import supply_labels as SL
import tank as T
from common import bpy

# ---- layout (mirrored in app/src/main/java/com/reeflive/app/render/SupplyShelf.kt) ----
X0, X1 = 134.0, 236.0          # outer x of the rack
POST = 2.4                     # upright thickness (slots start inside it: SLOT_X0 = X0 + POST)
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
# props are modelled near real proportions, then scaled per row (and per item) to fill their slots: bigger print reads
# better at phone size. CLEAR = free height over each board (a picked prop must fit, lifted).
ROW_SCALE = (1.15, 1.05, 1.0)
SCALE = {"glass_cleaner": 1.12, "test_kit": 1.15, "move_rocks": 1.1, "microscope": 0.95, "water_change": 1.04}
CLEAR = (TOP_Y - ROW_Y[0], ROW_Y[0] - BOARD - ROW_Y[1], ROW_Y[1] - BOARD - ROW_Y[2])
SEA_GLASS = ["#F2C17A", "#9EDFA6", "#8CD3EA"]   # row edging: amber (feed), green (care), blue (tools)

_mats = {}
_label_mat = []


def mat(hexc, rough=0.55, metal=0.0):
    key = (hexc, rough, metal)
    if key not in _mats:
        name = "shelf_" + hexc.strip("#").lower() + ("" if rough == 0.55 else f"_{int(rough * 100)}") + ("_m" if metal else "")
        _mats[key] = C.material(name, hexc, rough=rough, metal=metal)
    return _mats[key]


def label_mat():
    return _label_mat[0]


I3 = np.eye(3)


class Prop:
    """World-space geometry of one prop with per-face materials (colours and the shared label atlas). `frame(R, o)`
    sets a rotation + offset applied to everything added until it is reset (local building of tilted parts)."""

    def __init__(self):
        self.g = T.WGeo()
        self.cols = []
        self.R, self.o = I3, np.zeros(3)

    def frame(self, R=None, o=(0, 0, 0)):
        self.R = I3 if R is None else np.asarray(R, float)
        self.o = np.asarray(o, float)

    def _xf(self, V):
        return np.asarray(V, float) @ self.R.T + self.o

    def _slot(self, m):
        if m not in self.cols:
            self.cols.append(m)
        return self.cols.index(m)

    def add(self, V, F, hexc, rough=0.55, metal=0.0):
        self.g.add(self._xf(V), F, col=(1, 1, 1, 1), mat=self._slot(mat(hexc, rough, metal)))

    def add_uv(self, V, F, UV):
        self.g.add(self._xf(V), F, UV=UV, col=(1, 1, 1, 1), mat=self._slot(label_mat()))

    def add_mixed(self, V, F, UV, face_mats):
        """One shared-vertex mesh whose faces use different materials: face_mats[i] is "label" or (hex, rough)."""
        b = len(self.g.F)
        self.g.add(self._xf(V), F, UV=UV, col=(1, 1, 1, 1), mat=0)
        for i, fm in enumerate(face_mats):
            self.g.FM[b + i] = self._slot(label_mat() if fm == "label" else mat(*fm))

    def obj(self, name, parent, extras):
        return self.g.obj(name, parent, "equipment", self.cols, smooth=True, extras=extras)


# ---- primitive builders (world frame, Y up) ----
def cyl(p0, p1, r, sides=14, r1=None):
    pts = np.array([p0, p1], float)
    V, F, _, _ = G.tube(pts, [r, r if r1 is None else r1], sides, cap_start=True, cap_end=True, up=(1, 0, 0))
    V[2 * sides] = pts[1]
    V[2 * sides + 1] = pts[0]
    return V, F


def lathe(prof, c, sides=18):
    """Closed solid of revolution about +Y at c: prof is [(r, y)] from the bottom up (both ends capped)."""
    c = np.asarray(c, float)
    n = len(prof)
    V = []
    for r, y in prof:
        for k in range(sides):
            a = 2 * math.pi * k / sides
            V.append((c[0] + r * math.sin(a), c[1] + y, c[2] + r * math.cos(a)))
    F = []
    for i in range(n - 1):
        for k in range(sides):
            a0, a1 = i * sides + k, i * sides + (k + 1) % sides
            F.append((a0, a1, a1 + sides, a0 + sides))
    V.append((c[0], c[1] + prof[0][1], c[2]))
    V.append((c[0], c[1] + prof[-1][1], c[2]))
    bot, top = len(V) - 2, len(V) - 1
    for k in range(sides):
        F.append(((k + 1) % sides, k, bot))
        F.append(((n - 1) * sides + k, (n - 1) * sides + (k + 1) % sides, top))
    return np.array(V), F


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


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


# local face frames of a box: normal, u (across the print), v (up the print)
FACES = {"+z": ((0, 0, 1), (1, 0, 0), (0, 1, 0)), "-z": ((0, 0, -1), (-1, 0, 0), (0, 1, 0)),
         "+x": ((1, 0, 0), (0, 0, -1), (0, 1, 0)), "-x": ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
         "+y": ((0, 1, 0), (1, 0, 0), (0, 0, -1)), "-y": ((0, -1, 0), (1, 0, 0), (0, 0, 1))}


def fbox(p, c, size, hexc, rough=0.55, decals=None, metal=0.0):
    """A flat-shaded box (own vertices per face) centred at c (in the prop's current frame); decals maps a face
    ("+z" front, "+y" top, ...) to a label name printed over that whole face."""
    c = np.asarray(c, float)
    h = np.asarray(size, float) / 2
    for f, (n, u, v) in FACES.items():
        n, u, v = (np.asarray(t, float) for t in (n, u, v))
        hu, hv = abs(np.dot(u, h)), abs(np.dot(v, h))
        fc = c + n * abs(np.dot(n, h))
        V = [fc - u * hu - v * hv, fc + u * hu - v * hv, fc + u * hu + v * hv, fc - u * hu + v * hv]
        if decals and f in decals:
            nm = decals[f]
            p.add_uv(V, [(0, 1, 2, 3)], [SL.uv(nm, 0, 0), SL.uv(nm, 1, 0), SL.uv(nm, 1, 1), SL.uv(nm, 0, 1)])
        else:
            p.add(V, [(0, 1, 2, 3)], hexc, rough, metal)


def wrap(p, name, x, y0, y1, z, r0, r1=None, arc=220.0, sides=16):
    """A printed label band around the front of a round body (facing +z): arc degrees, radius r0 at y0 .. r1 at y1."""
    r1 = r0 if r1 is None else r1
    arc = math.radians(arc)
    V, UV, F = [], [], []
    for i in range(sides + 1):
        t = i / sides
        a = -arc / 2 + arc * t
        for yy, rr, v in ((y0, r0, 0.0), (y1, r1, 1.0)):
            V.append((x + rr * math.sin(a), yy, z + rr * math.cos(a)))
            UV.append(SL.uv(name, t, v))
    for i in range(sides):
        F.append((2 * i, 2 * i + 2, 2 * i + 3, 2 * i + 1))
    p.add_uv(V, F, UV)


def plate(p, name, c, w, h, xdir=(1, 0, 0), ydir=(0, 1, 0)):
    """A flat printed label quad centred at c (facing xdir × ydir)."""
    c = np.asarray(c, float)
    xd = np.asarray(xdir, float); xd /= np.linalg.norm(xd)
    yd = np.asarray(ydir, float); yd /= np.linalg.norm(yd)
    V = [c - xd * w / 2 - yd * h / 2, c + xd * w / 2 - yd * h / 2, c + xd * w / 2 + yd * h / 2, c - xd * w / 2 + yd * h / 2]
    p.add_uv(V, [(0, 1, 2, 3)], [SL.uv(name, 0, 0), SL.uv(name, 1, 0), SL.uv(name, 1, 1), SL.uv(name, 0, 1)])


def pouch(p, name, x, y, z, w, h, d, back, seal, nu=8, nv=6, flat=0.85, seal_h=0.5):
    """A stand-up pouch / bag: the printed front and plain back bulge to depth d at the bottom gusset and pinch to a
    flat seal at the top (one closed mesh). Built facing +z with its base centre at (x, y, z)."""
    V, UV, F, FM = [], [], [], []

    def bulge(u, v):
        return (math.sin(math.pi * u) ** 0.55) * (1 - flat * v ** 1.6) * d / 2

    front = {}
    for j in range(nv + 1):
        for i in range(nu + 1):
            u, v = i / nu, j / nv
            front[i, j] = len(V)
            V.append((x + (u - 0.5) * w, y + v * h, z + bulge(u, v)))
            UV.append(SL.uv(name, u, v))
    backi = {}
    for j in range(nv + 1):
        for i in range(nu + 1):
            if i in (0, nu):
                backi[i, j] = front[i, j]
                continue
            u, v = i / nu, j / nv
            backi[i, j] = len(V)
            V.append((x + (u - 0.5) * w, y + v * h, z - bulge(u, v)))
            UV.append((0.0, 0.0))
    for j in range(nv):
        for i in range(nu):
            F.append((front[i, j], front[i + 1, j], front[i + 1, j + 1], front[i, j + 1])); FM.append("label")
            F.append((backi[i + 1, j], backi[i, j], backi[i, j + 1], backi[i + 1, j + 1])); FM.append((back, 0.5))
    for i in range(nu):  # flat bottom gusset and the pinched top
        F.append((front[i + 1, 0], front[i, 0], backi[i, 0], backi[i + 1, 0])); FM.append((back, 0.5))
        F.append((front[i, nv], front[i + 1, nv], backi[i + 1, nv], backi[i, nv])); FM.append((back, 0.5))
    p.add_mixed(V, F, UV, FM)
    # crimped heat seal along the top
    fbox(p, (x, y + h + seal_h / 2 - 0.05, z), (w, seal_h, max(0.2, d * (1 - flat) * 0.6)), seal, 0.5)


def card(p, name, x, y, z, w, h, t, edge, tilt=0.14):
    """A printed card (blister / flat pack / backing card) leaning back by `tilt` on its bottom edge. Leaves the prop
    frame set to the card (local origin = card base centre, +z out of the print) for things mounted on it."""
    p.frame(rx(-tilt), (x, y, z))
    fbox(p, (0, h / 2, 0), (w, h, t), edge, 0.45, decals={"+z": name})


def blister_cubes(p, u0, u1, v0, v1, w, h, t, rows, cols, col):
    """Frozen cubes standing proud of a card's blister window (card frame, window in print fractions, v from top)."""
    cw, ch = (u1 - u0) * w / cols, (v1 - v0) * h / rows
    for i in range(rows):
        for j in range(cols):
            cx = -w / 2 + (u0 + (j + 0.5) * (u1 - u0) / cols) * w
            cy = h * (1 - (v0 + (i + 0.5) * (v1 - v0) / rows))
            fbox(p, (cx, cy, t / 2 + 0.22), (cw * 0.82, ch * 0.8, 0.44), col, 0.25)


def ribs(p, x, y0, y1, z, r, col, n=2, rough=0.4, sides=14):
    """Grip rings around a cap (thin triangular-section tori)."""
    for k in range(n):
        p.add(*ring((x, y0 + (y1 - y0) * (k + 0.5) / n, z), r, 0.07, sides, 3), col, rough)


def screw_jar(p, name, x, y, z, r, h, body, cap, cap_h, band=(0.05, 0.95), arc=220.0, cap_r=None, rough=0.25):
    """A round jar with a wrap label (band = fraction of the body height) and a ribbed screw cap."""
    cr = cap_r or r + 0.1
    p.add(*lathe([(r * 0.94, 0), (r, 0.25), (r, h - 0.2), (r * 0.9, h), (r * 0.9, h + 0.15)], (x, y, z), 18), body, rough)
    lb0, lb1 = y + h * band[0], y + h * band[1]
    wrap(p, name, x, lb0, lb1, z, r + 0.04, arc=arc, sides=16)
    p.add(*lathe([(cr, 0), (cr, cap_h - 0.15), (cr * 0.94, cap_h), (0.01, cap_h)], (x, y + h + 0.05, z), 18), cap, 0.4)
    ribs(p, x, y + h + 0.2, y + h + cap_h - 0.25, z, cr, cap, n=2, sides=18)


def bottle(p, name, x, y, z, r, h, body, cap, cap_h=1.3, neck_r=None, band=(0.08, 0.88), rough=0.3, cap_rough=0.4):
    """A supplement bottle: rounded shoulder, neck and ribbed cap, wrap label."""
    nr = neck_r or r * 0.48
    prof = [(r * 0.94, 0), (r, 0.2), (r, h), (r * 0.92, h + 0.45), (r * 0.7, h + 0.85), (nr, h + 1.1), (nr, h + 1.4)]
    p.add(*lathe(prof, (x, y, z), 18), body, rough)
    wrap(p, name, x, y + h * band[0], y + h * band[1], z, r + 0.04, arc=220, sides=16)
    cy = y + h + 1.35
    p.add(*lathe([(nr * 1.25, 0), (nr * 1.25, cap_h - 0.1), (nr * 1.15, cap_h), (0.01, cap_h)], (x, cy, z), 16), cap, cap_rough)
    ribs(p, x, cy + 0.15, cy + cap_h - 0.2, z, nr * 1.25, cap, n=2, sides=16)


# ---- the products: each builds at its base centre (x, y, z) ----
def build_prop(item, x, y, z):
    p = Prop()
    if item == "flake":
        # Ocean Nutrition Formula Two Flakes: clear jar wrapped in the green label, ribbed green cap
        screw_jar(p, "flake", x, y, z, 2.62, 6.0, "#DDEFD8", "#1F8A3A", 1.7, band=(0.05, 0.97), cap_r=2.74)
    elif item == "pellet":
        # Formula One Pellets: narrower jar, blue label, blue cap
        screw_jar(p, "pellet", x, y, z, 2.16, 6.1, "#D8E6F4", "#1E5FC0", 1.8, band=(0.05, 0.97), cap_r=2.26)
    elif item == "floating_pellet":
        # Hikari Marine S: white stand-up pouch with the teal stripe
        pouch(p, "floating_pellet", x, y, z, 5.2, 8.4, 2.2, "#F2F6F4", "#E6ECEA")
    elif item == "algae_wafer":
        # Hikari Mini Algae Wafers pouch
        pouch(p, "algae_wafer", x, y, z, 5.2, 8.4, 2.2, "#F2F6F4", "#3E9A2A")
    elif item in ("mysis", "cyclops"):
        # Hikari Bio-Pure blister card with the frozen cubes standing proud of the window
        card(p, item, x, y, z, 5.6, 8.8, 0.22, "#5A2E9A" if item == "mysis" else "#18287A")
        blister_cubes(p, 0.23, 0.95, 0.65, 0.88, 5.6, 8.8, 0.22, 2, 4, "#E2D4D0" if item == "mysis" else "#D8603A")
        p.frame()
    elif item == "brine":
        # San Francisco Bay Brand frozen flat pack: cubes in the middle window
        card(p, "brine", x, y, z, 5.4, 8.2, 0.22, "#1E4DB7")
        blister_cubes(p, 0.14, 0.86, 0.35, 0.6, 5.4, 8.2, 0.22, 2, 3, "#A85A40")
        p.frame()
    elif item == "krill":
        # BRS Krill Superba: a clear jar packed with freeze-dried krill (printed), white lid
        p.add(*lathe([(2.2, 0), (2.32, 0.3), (2.32, 6.2), (2.0, 6.5), (2.0, 6.65)], (x, y, z), 18), "#E58E62", 0.2)
        wrap(p, "krill", x, y + 0.1, y + 6.5, z, 2.36, arc=220, sides=16)
        p.add(*lathe([(2.16, 0), (2.16, 1.15), (2.06, 1.3), (0.01, 1.3)], (x, y + 6.6, z), 18), "#F6F6F2", 0.35)
        ribs(p, x, y + 6.75, y + 7.7, z, 2.16, "#F6F6F2", n=2, sides=18)
    elif item == "prawn":
        # a clear freezer bag of prawn chunks with a red header card
        pouch(p, "prawn", x, y, z, 5.6, 8.0, 1.6, "#D8E6EE", "#C8202A", flat=0.7)
        p.add(*cyl((x, y + 8.25, z - 0.2), (x, y + 8.25, z + 0.2), 0.32, 10), "#F2F2EE", 0.4)
    elif item == "nori":
        # Two Little Fishies / Julian Sprung's Sea Veggies: resealable bag, a green sheet poking out of the top
        pouch(p, "nori", x, y, z, 5.4, 8.4, 1.8, "#C8CCCA", "#BFC4C2")
        p.frame(rz(0.12), (x - 0.4, y + 8.6, z - 0.05))
        fbox(p, (0, 0.6, 0), (3.6, 1.6, 0.08), "#1F3A20", 0.75)
        p.frame()
    elif item == "coral_food":
        # Polyp Lab Reef-Roids: matte black pouch, silver base
        pouch(p, "coral_food", x, y, z, 5.0, 8.0, 2.0, "#1A1A1C", "#1A1A1C")
    elif item == "plankton":
        # Reef Nutrition Phyto-Feast: dark green culture in a bottle, orange cap
        bottle(p, "plankton", x, y, z, 1.92, 6.0, "#1F3A26", "#F26A1E", cap_h=1.4, neck_r=0.9, band=(0.1, 0.87), rough=0.2)
    elif item == "pods":
        # AlgaeBarn 5280 Pods: a glass mason jar of sea water with a gold lid
        p.add(*lathe([(2.05, 0), (2.22, 0.3), (2.22, 5.9), (2.0, 6.3), (1.86, 6.4), (1.86, 6.9)], (x, y, z), 18), "#B8DCE6", 0.12)
        wrap(p, "pods", x, y + 0.15, y + 6.0, z, 2.26, arc=220, sides=16)
        p.add(*lathe([(1.98, 0), (1.98, 0.75), (1.9, 0.85), (0.01, 0.85)], (x, y + 6.4, z), 18), "#C9A250", 0.3, 0.6)
        ribs(p, x, y + 6.5, y + 7.1, z, 1.98, "#B8903A", n=2, sides=18)
    elif item == "water_change":
        # Red Sea Coral Pro Salt bucket: navy, printed all round, lid and handle
        p.add(*lathe([(3.4, 0), (3.52, 0.25), (3.98, 8.0), (4.08, 8.15)], (x, y, z), 22), "#1A1E48", 0.4)
        wrap(p, "water_change", x, y + 0.3, y + 7.9, z, 3.56, 4.0, arc=220, sides=18)
        p.add(*lathe([(4.18, 0), (4.22, 0.75), (4.0, 0.95), (0.01, 0.95)], (x, y + 8.05, z), 22), "#121430", 0.4)
        p.add(*ring((x, y + 8.35, z), 4.2, 0.12, 22, 4), "#0A0C20", 0.4)
        pts = [(x + 4.1 * math.cos(a), y + 7.6 + 2.6 * math.sin(a), z) for a in np.linspace(0.05, math.pi - 0.05, 11)]
        V, F, _, _ = G.tube(np.array(pts), 0.14, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V, F, "#20243A", 0.4)
        p.add(*cyl((x - 1.3, y + 10.2, z), (x + 1.3, y + 10.2, z), 0.36, 10), "#121430", 0.5)
    elif item == "water_small":
        # Instant Ocean Sea Salt 50 gal bag
        pouch(p, "water_small", x, y, z, 7.2, 8.4, 3.0, "#6E2488", "#6E2488", flat=0.8, nu=10)
    elif item in ("dose_alk", "dose_ca", "dose_mg"):
        # Red Sea Reef Foundation B / A / C: white bottle, black wrap label, white cap
        bottle(p, item, x, y, z, 1.9, 6.6, "#F4F5F6", "#F4F5F6", cap_h=1.4, neck_r=0.92, band=(0.07, 0.95), rough=0.35)
    elif item == "new_sock":
        # BRS felt filter sock on a little stand, its tag hanging in front
        fbox(p, (x, y + 0.5, z), (6.6, 1.0, 4.4), "#2E3A40", 0.6)
        p.add(*cyl((x - 2.9, y + 1.0, z - 1.4), (x - 2.9, y + 10.4, z - 1.4), 0.25, 6), "#2E3A40")
        p.add(*cyl((x - 2.9, y + 10.2, z - 1.4), (x - 0.4, y + 10.2, z - 1.4), 0.2, 6), "#2E3A40")
        pts = [(x, y + 9.4, z), (x, y + 6.6, z), (x, y + 3.6, z), (x, y + 2.0, z)]
        V, F, _, _ = G.tube(np.array(pts), [2.1, 2.05, 1.8, 1.15], 14, cap_start=False, cap_end=True, up=(1, 0, 0))
        p.add(V, F, "#FBFBF6", 0.95)
        p.add(*lathe([(2.05, 0), (2.5, 0.05), (2.5, 0.6), (2.05, 0.65)], (x, y + 9.3, z), 18), "#F0F0EE", 0.35)
        p.frame(rx(-0.06), (x + 0.6, y + 6.7, z + 2.35))
        fbox(p, (0, 0, 0), (3.4, 4.4, 0.05), "#FFFFFF", 0.5, decals={"+z": "new_sock"})
        p.frame()
        p.add(*cyl((x + 0.6, y + 8.7, z + 2.3), (x + 0.3, y + 9.4, z + 2.1), 0.06, 4), "#D8D8D8", 0.4)
    elif item == "fresh_carbon":
        # Seachem Matrix Carbon: white jar, white lid
        screw_jar(p, "fresh_carbon", x, y, z, 2.62, 6.8, "#F6F7F8", "#F2F3F4", 1.6, band=(0.04, 0.95), cap_r=2.74, rough=0.35)
    elif item == "trim_algae":
        # curved aquascaping scissors mounted on a hanging backing card
        card(p, "trim_algae", x, y, z, 5.4, 9.0, 0.2, "#1E2A2E", tilt=0.12)
        for s in (-1, 1):
            # blades (curved, meeting at the tips) and the finger loops below the pivot
            pts = np.array([(0.1 * s, 3.7, 0.25), (0.24 * s, 4.6, 0.25), (0.2 * s, 5.5, 0.25), (0.02 * s, 6.1, 0.25)])
            V, F, _, _ = G.tube(pts, [0.2, 0.16, 0.1, 0.04], 6, cap_start=True, cap_end=True, up=(0, 0, 1), squash=0.35)
            p.add(V, F, "#C9D4DC", 0.15, 0.8)
            pts = np.array([(0.1 * s, 3.7, 0.26), (0.45 * s, 3.0, 0.26), (0.75 * s, 2.5, 0.26)])
            V, F, _, _ = G.tube(pts, 0.12, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
            p.add(V, F, "#C9D4DC", 0.15, 0.8)
            p.add(*ring((0.95 * s, 2.05, 0.28), 0.55, 0.14, 12, 5, rot=rx(math.pi / 2)), "#2EAE9E", 0.35)
        p.add(*cyl((0, 3.7, 0.12), (0, 3.7, 0.5), 0.2, 8), "#8A949C", 0.2, 0.8)
        p.frame()
    elif item == "zap_pest":
        # Red Sea Aiptasia-X kit: the box, the applicator bottle and the syringe
        fbox(p, (x - 2.3, y + 4.0, z - 0.6), (3.8, 8.0, 2.4), "#F6F6F6", 0.5, decals={"+z": "zap_pest"})
        bottle(p, "zap_pest_bottle", x + 2.0, y, z + 0.4, 1.2, 2.9, "#F6F6F6", "#F6F6F6", cap_h=0.8, neck_r=0.5,
               band=(0.03, 0.95))
        p.add(*cyl((x + 2.0, y + 5.0, z + 0.4), (x + 2.0, y + 5.9, z + 0.4), 0.18, 8, r1=0.06), "#F6F6F6", 0.4)
        sy = y + 0.55
        p.add(*cyl((x - 3.8, sy, z + 2.6), (x + 0.6, sy, z + 2.6), 0.42, 12), "#E4F2F8", 0.15)
        p.add(*cyl((x - 3.6, sy, z + 2.6), (x - 1.4, sy, z + 2.6), 0.38, 12), "#E8C890", 0.3)
        p.add(*cyl((x - 4.4, sy, z + 2.6), (x - 3.8, sy, z + 2.6), 0.12, 6), "#F2F2F2", 0.4)
        p.add(*cyl((x - 4.6, sy, z + 2.6), (x - 4.4, sy, z + 2.6), 0.55, 10), "#F2F2F2", 0.4)
        p.add(*obox_((x + 0.75, sy, z + 2.6), (0.3, 1.5, 0.5)), "#F2F2F2", 0.4)
        p.add(*cyl((x + 0.6, sy, z + 2.6), (x + 1.9, sy, z + 2.6), 0.12, 6, r1=0.05), "#3FB06A", 0.4)
    elif item == "settle_in":
        # a drip-acclimation cup with its airline loop and drip valve
        p.add(*lathe([(2.2, 0), (2.3, 0.2), (2.9, 5.0), (2.98, 5.15)], (x, y, z), 18), "#F2F6FF", 0.3)
        wrap(p, "settle_in", x, y + 0.7, y + 4.3, z, 2.43, 2.79, arc=200, sides=16)
        p.add(*cyl((x, y + 4.5, z), (x, y + 4.62, z), 2.82, 18), "#5FB8E8", 0.15)
        pts = [(x + 2.2, y + 5.0, z - 0.6), (x + 3.3, y + 7.6, z - 0.6), (x + 1.2, y + 9.4, z - 1.0), (x - 1.4, y + 8.6, z - 1.2),
               (x - 2.2, y + 5.4, z - 1.0)]
        V, F, _, _ = G.tube(np.array(pts), 0.17, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V, F, "#DCEFF6", 0.25)
        p.add(*cyl((x + 3.1, y + 7.0, z - 0.6), (x + 3.25, y + 8.1, z - 0.6), 0.42, 10), "#2E9E96", 0.4)
        p.add(*cyl((x + 2.75, y + 7.55, z - 0.6), (x + 3.6, y + 7.55, z - 0.6), 0.15, 6), "#1B6A66", 0.4)
    elif item == "glass_cleaner":
        # Flipper Edge: its retail box and the magnet itself (handle on top of the scrubber half, blue blade end)
        fbox(p, (x - 4.0, y + 5.3, z - 1.6), (7.2, 10.6, 2.6), "#121416", 0.5, decals={"+z": "glass_cleaner"})
        p.frame(ry(-0.32), (x + 4.3, y, z + 1.2))
        fbox(p, (0, 0.6, 0), (6.6, 1.2, 3.2), "#4A5056", 0.5)
        fbox(p, (0, 1.3, 0), (6.8, 0.2, 3.4), "#2A6A3A", 0.9)
        fbox(p, (0, 2.3, 0), (7.0, 1.8, 3.4), "#24282C", 0.45)
        fbox(p, (0.1, 3.45, 0), (6.0, 0.5, 2.4), "#2E3338", 0.4, decals={"+y": "glass_cleaner_top"})
        fbox(p, (3.65, 2.1, 0), (0.6, 2.2, 3.6), "#1E7FD8", 0.4)
        fbox(p, (-3.6, 2.1, 0), (0.3, 1.6, 2.8), "#1E7FD8", 0.4)
        p.frame()
    elif item == "test_kit":
        # API Reef Master: clear tub (contents printed), blue lid with a handle; a Salifert KH box and a vial
        fbox(p, (x - 3.4, y + 3.0, z - 0.6), (10.0, 6.0, 5.4), "#DDE8EE", 0.2, decals={"+z": "test_kit"})
        fbox(p, (x - 3.4, y + 6.4, z - 0.6), (10.4, 0.8, 5.8), "#1E4DB7", 0.35)
        fbox(p, (x - 3.4, y + 6.85, z - 0.6), (9.6, 0.1, 5.0), "#1A44A8", 0.35)
        pts = [(x - 5.4, y + 6.8, z - 0.6), (x - 4.6, y + 8.0, z - 0.6), (x - 2.2, y + 8.0, z - 0.6), (x - 1.4, y + 6.8, z - 0.6)]
        V, F, _, _ = G.tube(np.array(pts), 0.22, 6, cap_start=True, cap_end=True, up=(0, 0, 1), squash=0.6)
        p.add(V, F, "#E8F0F4", 0.25)
        p.frame(ry(-0.25), (x + 5.4, y, z + 0.6))
        fbox(p, (0, 2.3, 0), (3.8, 4.6, 1.8), "#2A2A8E", 0.5, decals={"+z": "test_kit_salifert"})
        p.frame()
        p.add(*cyl((x + 2.4, y, z + 2.8), (x + 2.4, y + 3.2, z + 2.8), 0.55, 12), "#E4F2F8", 0.12)
        p.add(*cyl((x + 2.4, y + 0.1, z + 2.8), (x + 2.4, y + 1.9, z + 2.8), 0.5, 12), "#9A5ACC", 0.2)
        p.add(*cyl((x + 2.4, y + 3.2, z + 2.8), (x + 2.4, y + 3.8, z + 2.8), 0.62, 12), "#2A2A8E", 0.35)
    elif item == "microscope":
        # a student microscope: grey base, white arm, black stage and eyepiece, focus knobs; label on the base
        fbox(p, (x, y + 0.9, z), (10.0, 1.8, 7.6), "#E8ECF0", 0.4, decals={"+z": "microscope"})
        V, F, _, _ = G.tube(np.array([(x, y + 1.8, z - 2.6), (x, y + 6.0, z - 3.0), (x, y + 10.0, z - 2.4), (x, y + 12.2, z - 1.0)]),
                            1.1, 10, cap_start=True, cap_end=True, up=(1, 0, 0), squash=0.8)
        p.add(V, F, "#F4F6F8", 0.35)
        fbox(p, (x, y + 5.4, z + 0.6), (6.4, 0.7, 5.0), "#1E2226", 0.5)
        for s in (-1, 1):
            fbox(p, (x + 1.8 * s, y + 5.85, z + 1.0), (0.4, 0.2, 2.6), "#C9D4DC", 0.2, metal=0.8)
        fbox(p, (x, y + 5.82, z + 0.8), (2.6, 0.06, 1.2), "#BFE7FF", 0.1)
        p.add(*cyl((x, y + 7.4, z + 0.6), (x, y + 13.4, z - 1.0), 1.5, 14), "#F4F6F8", 0.35)
        p.add(*cyl((x, y + 13.4, z - 1.0), (x, y + 15.0, z - 1.4), 1.0, 12), "#1E2226", 0.5)
        p.add(*cyl((x, y + 6.4, z + 1.0), (x, y + 7.6, z + 0.7), 1.0, 12, r1=1.4), "#1E2226", 0.4)
        p.add(*cyl((x, y + 3.6, z + 0.9), (x, y + 4.6, z + 0.9), 0.9, 12), "#FFF6C8", 0.3)
        p.add(*cyl((x - 1.9, y + 9.0, z - 2.6), (x + 1.9, y + 9.0, z - 2.6), 1.3, 14), "#1E7FD8", 0.35)
    elif item == "knocker":
        # a rubber mallet (black head, white face caps, yellow-and-black grip) leaning on its stand
        fbox(p, (x, y + 0.6, z), (9.0, 1.2, 6.0), "#141414", 0.5, decals={"+z": "knocker"})
        hdir = np.array([0.35, 1.0, 0.0])
        hdir /= np.linalg.norm(hdir)
        b0 = np.array([x - 2.4, y + 1.2, z])
        b1 = b0 + hdir * 11.0
        p.add(*cyl(b0, b1, 0.5, 10), "#F2C21E", 0.5)
        p.add(*cyl(b0, b0 + hdir * 3.6, 0.68, 10), "#141414", 0.8)
        side = np.array([1.0, -0.35, 0.0])
        side /= np.linalg.norm(side)
        p.add(*cyl(b1 - side * 2.6, b1 + side * 2.6, 1.8, 16), "#202224", 0.7)
        p.add(*cyl(b1 + side * 2.6, b1 + side * 2.9, 1.7, 16, r1=1.5), "#F2F2F2", 0.6)
        p.add(*cyl(b1 - side * 2.9, b1 - side * 2.6, 1.5, 16, r1=1.7), "#F2F2F2", 0.6)
    elif item == "move_rocks":
        # CaribSea LifeRock: an open carton of dry rock, flaps up, rock tongs leaning in
        k, kd = "#C9A06A", "#A8804E"
        fbox(p, (x, y + 2.5, z + 3.85), (14.0, 5.0, 0.3), k, 0.85, decals={"+z": "move_rocks"})
        fbox(p, (x, y + 2.5, z - 3.85), (14.0, 5.0, 0.3), kd, 0.85)
        for s in (-1, 1):
            fbox(p, (x + 6.85 * s, y + 2.5, z), (0.3, 5.0, 7.4), kd, 0.85)
        fbox(p, (x, y + 0.15, z), (13.4, 0.3, 7.4), kd, 0.85)
        for s in (-1, 1):
            p.frame(rx(0.55 * s), (x, y + 5.0, z + 3.85 * s))
            fbox(p, (0, 1.5, 0.15 * s), (13.6, 3.0, 0.25), k if s > 0 else kd, 0.85)
            p.frame()
        rng = np.random.default_rng(5)
        for i, (dx, dy, dz, r) in enumerate(((-3.6, 4.6, 0.4, 2.6), (0.4, 5.2, -0.6, 2.9), (3.8, 4.4, 0.8, 2.4), (-1.4, 4.2, 2.0, 1.8))):
            col = ["#B48AB8", "#9E7AA6", "#C9A4C0", "#A888B0"][i]
            p.add(*blob((x + dx, y + dy, z + dz), (r * 1.15, r * 0.8, r), 10, 6, rot=rz(rng.uniform(-0.5, 0.5))), col, 0.95)
            for _ in range(3):
                a = rng.uniform(-1.2, 1.2)
                p.add(*blob((x + dx + r * 0.9 * math.sin(a), y + dy + rng.uniform(-0.3, 0.6), z + dz + r * 0.85 * math.cos(a)),
                            (0.45, 0.4, 0.2), 6, 3, rot=ry(a)), "#5A3E62", 0.9)
        p.add(*blob((x + 1.0, y + 7.2, z), (0.7, 0.5, 0.6), 8, 4), "#E86A8A", 0.8)
        for s in (-1, 1):
            p.add(*cyl((x + 6.3, y + 1.2, z + 3.2 + 0.4 * s), (x + 1.6, y + 9.6, z + 1.4 + 0.2 * s), 0.22, 6), "#9AA4AC", 0.2, 0.8)
        p.add(*blob((x + 6.3, y + 1.3, z + 3.2), (0.6, 0.6, 0.9), 6, 3), "#1E7FD8", 0.4)
    else:
        raise ValueError(item)
    return p


def obox_(c, size):
    return T.obox(c, size, (1, 0, 0), (0, 1, 0))


# ---- the rack ----
def driftwood(p, x, z, y0, y1, r, seed, col):
    """A gently crooked driftwood upright from y0 to y1."""
    rng = np.random.default_rng(seed)
    n = 7
    pts = [(x + rng.uniform(-0.35, 0.35), y0 + (y1 - y0) * k / (n - 1), z + rng.uniform(-0.35, 0.35)) for k in range(n)]
    radii = [r * rng.uniform(0.88, 1.12) for _ in range(n)]
    V, F, _, _ = G.tube(np.array(pts), radii, 9, cap_start=True, cap_end=True, up=(1, 0, 0))
    p.add(V, F, col, 0.8)


def rack(parent):
    wood, wood_dark, board = "#C49A6C", "#9C7450", "#DDBE93"
    frame = Prop()
    # crooked driftwood uprights, front and back on each side, with a cross brace between them
    for k, xx in enumerate((X0 + POST / 2, X1 - POST / 2)):
        for j, zz in enumerate((Z0 + 1.4, Z1 - 1.4)):
            driftwood(frame, xx, zz, FOOT_Y, TOP_Y + BOARD + 0.6, 1.25, 3 + 2 * k + j, wood if j else wood_dark)
        for y in (ROW_Y[2] - BOARD - 1.5, ROW_Y[1] - BOARD - 1.5, ROW_Y[0] - BOARD - 1.5):
            frame.add(*cyl((xx, y, Z0 + 1.4), (xx, y, Z1 - 1.4), 0.7, 8), wood_dark, 0.8)
    # boards: warm planks with a darker grain stripe on the front edge
    for y in ROW_Y:
        frame.add(*T.box((X0 + 0.4, y - BOARD, Z0), (X1 - 0.4, y, Z1 - 0.2)), board, 0.75)
        for g in range(3):
            gy = y - BOARD + 0.35 + 0.5 * g
            frame.add(*T.box((X0 + 0.6 + 7 * g, gy, Z1 - 0.21), (X1 - 0.6 - 5 * g, gy + 0.12, Z1 - 0.15)), wood_dark, 0.8)
    frame.add(*T.box((X0 - 1.0, TOP_Y, Z0 - 1.0), (X1 + 1.0, TOP_Y + BOARD, Z1 + 1.0)), wood, 0.75)
    frame.add(*T.box((X0 + POST, FOOT_Y, Z1 - 3.0), (X1 - POST, ROW_Y[2] - BOARD, Z1 - 1.0)), wood_dark, 0.8)
    o = frame.obj("ShelfFrame", parent, {})

    # back panel: the soft lagoon backdrop print, a few sea-glass pebbles
    bp = Prop()
    bx0, bx1, by0, by1 = X0 + 0.4, X1 - 0.4, ROW_Y[2] - BOARD, TOP_Y
    V = [(bx0, by0, Z0 + 0.6), (bx1, by0, Z0 + 0.6), (bx1, by1, Z0 + 0.6), (bx0, by1, Z0 + 0.6)]
    bp.add_uv(V, [(0, 1, 2, 3)], [SL.uv("backdrop", 0, 0), SL.uv("backdrop", 1, 0), SL.uv("backdrop", 1, 1), SL.uv("backdrop", 0, 1)])
    bp.add(*T.box((bx0, by0, Z0 - 0.4), (bx1, by1, Z0 + 0.5)), "#1F5560", 0.8)
    rng = np.random.default_rng(7)
    for k in range(9):
        px = rng.uniform(X0 + 6, X1 - 6)
        row = k % 3
        py = ROW_Y[row] + rng.uniform(12.0, 14.5)
        col = ["#7FD1C7", "#A8E6CF", "#5FB7D4", "#CDEBF0"][k % 4]
        bp.add(*blob((px, py, Z0 + 0.8), (rng.uniform(0.8, 1.4), rng.uniform(0.6, 1.0), 0.3), 8, 4), col, 0.2)
    ob = bp.obj("ShelfBack", parent, {})

    # sea-glass edging along every board front, tinted by row
    trims = []
    for r, y in enumerate(ROW_Y):
        t = Prop()
        V, F, _, _ = G.tube(np.array([(X0 + 0.6, y + 0.3, Z1 - 0.2), (X1 - 0.6, y + 0.3, Z1 - 0.2)]), 0.75, 8,
                            cap_start=True, cap_end=True, up=(0, 1, 0))
        t.add(V, F, SEA_GLASS[r], 0.15)
        # rounded sea-glass beads along it
        for k in range(12):
            bx = X0 + 4 + (X1 - X0 - 8) * k / 11
            t.add(*blob((bx, y + 0.3, Z1 + 0.5), (1.1, 0.9, 0.5), 7, 4), ["#BFEDE2", "#9FD9E8", "#D9F2C9"][(k + r) % 3], 0.15)
        trims.append(t.obj(f"ShelfLip{r}", parent, {"row": r}))

    # scalloped crest over the top: half-pebbles in sea-glass colours
    crest = Prop()
    n = 14
    for k in range(n):
        cx = X0 + 4 + (X1 - X0 - 8) * k / (n - 1)
        if abs(cx - (X0 + X1) / 2) < 17:
            continue  # the sign stands there
        col = ["#7FD1C7", "#5FD7FF", "#A8E6CF"][k % 3]
        crest.add(*blob((cx, TOP_Y + BOARD + 0.4, Z1 - 1.0), (3.4, 2.4, 1.1), 10, 5), col, 0.25)
    oc = crest.obj("ShelfCrest", parent, {})

    # the lit "Supplies" sign on the crest, and warm puck lights under each board (drawn lit: SIGN_NODE)
    sign = Prop()
    mx = (X0 + X1) / 2
    sy = TOP_Y + BOARD + 4.6
    fbox(sign, (mx, sy, Z1 - 1.6), (31.0, 8.2, 1.0), "#E8B04A", 0.4)
    plate(sign, "sign", (mx, sy, Z1 - 1.08), 30.0, 7.5)
    for s in (-1, 1):
        sign.add(*cyl((mx + 12 * s, TOP_Y + BOARD, Z1 - 1.6), (mx + 12 * s, sy - 4.1, Z1 - 1.6), 0.3, 6), "#9C7450", 0.8)
    for y in (TOP_Y, ROW_Y[0] - BOARD, ROW_Y[1] - BOARD):
        for k in range(4):
            px = X0 + 14 + (X1 - X0 - 28) * k / 3
            sign.add(*cyl((px, y, Z1 - 5.0), (px, y - 0.35, Z1 - 5.0), 1.3, 12), "#FFF2C8", 0.3)
    os_ = sign.obj("ShelfSign", parent, {})
    return [o, ob, oc, os_] + trims


def prop_xs(n):
    lo, hi = X0 + POST, X1 - POST
    pitch = (hi - lo) / n
    return [lo + pitch * (i + 0.5) for i in range(n)]


def build():
    C.reset_scene()
    _mats.clear()
    _label_mat.clear()
    atlas = SL.draw_atlas(os.path.join(C.BUILD, "supply_labels.jpg"))
    img = C.load_image(atlas, "supply_labels")
    _label_mat.append(C.material("shelf_labels", "#FFFFFF", rough=0.5, image=img, double=True))
    root = C.empty("Root")
    C.set_extras(root, units="cm", spec="docs/ASSETS.md §5.1", shelf=True)
    objs = rack(root)
    layout = {}
    for r, items in enumerate(ROWS):
        pitch = (X1 - X0 - 2 * POST) / len(items)
        for item, x in zip(items, prop_xs(len(items))):
            p = build_prop(item, x, ROW_Y[r], Z_PROP)
            k = SCALE.get(item, ROW_SCALE[r])
            if k != 1.0:
                base = np.array([x, ROW_Y[r], Z_PROP])
                p.g.V = list((np.array(p.g.V) - base) * k + base)
            o = p.obj("Supply_" + item, root, {"item": item, "row": r})
            objs.append(o)
            V = np.array(p.g.V)
            lo, hi = V[:, 0].min(), V[:, 0].max()
            layout[item] = (round(x, 2), ROW_Y[r], round(hi - lo, 2), round((lo + hi) / 2 - x, 2), round(V[:, 1].max() - ROW_Y[r], 1))
            if hi - lo > pitch:
                raise ValueError(f"{item} is {hi - lo:.2f} cm wide, its slot only {pitch:.2f}")
            # a picked prop rises LIFT_CM and grows 10 % (SupplyShelf.kt): it must still clear the board above
            top = (V[:, 1].max() - ROW_Y[r]) * 1.1 + 2.6
            if top > CLEAR[r]:
                raise ValueError(f"{item} would hit the board above when picked ({top:.1f} > {CLEAR[r]:.1f} cm)")
    for o in objs:
        C.recalc_normals(o)
        C.triangulate(o)
    path = os.path.join(C.MODELS, "supply_shelf.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[supplies] supply_shelf.glb: {tris} tris, {len(layout)} props, {os.path.getsize(path) / 1024:.0f} KB")
    for o in objs:
        print(f"  {o.name:22s} {C.tri_count(o):5d} tris")
    for item, (x, y, w, off, h) in layout.items():
        print(f"  {item:16s} x {x:7.2f}  board {y:6.1f}  width {w:5.2f}  centre off {off:+5.2f}  height {h:4.1f}")
    return tris


def render_preview(out=None, w=1600, h=1000, samples=48, view="all"):
    """Cycles render of the exported shelf (docs/preview/supply_shelf_v3.png): front view of the whole rack, or a
    close-up of one row (view = feed / care / tools)."""
    import preview as PV
    C.reset_scene()
    PV.setup_cycles(w, h, samples)
    PV.world((0.33, 0.38, 0.44), 0.9)
    objs = PV.import_glb(os.path.join(C.MODELS, "supply_shelf.glb"))
    for o in objs:
        if o.type == 'MESH':
            for m in o.data.materials:
                if m and m.node_tree and "labels" in m.name:
                    bsdf = m.node_tree.nodes.get("Principled BSDF")
                    if bsdf:
                        bsdf.inputs["Roughness"].default_value = 0.45
    cx = (X0 + X1) / 2
    if view == "all":
        tgt, dist, lens, rise = (cx, 28.5, 13.0), 178.0, 50, 5.0
    else:
        r = {"feed": 0, "care": 1, "tools": 2}[view]
        tgt, dist, lens, rise = (cx, ROW_Y[r] + 6.0, 13.0), 96.0, 50, 3.0
    eye = (tgt[0], tgt[1] + rise, tgt[2] + dist)
    PV.camera(C.w2b(eye), C.w2b(tgt), lens=lens)
    PV.add_light('AREA', C.w2b((cx - 60, 90, 140)), 150000, size=120, color=(1.0, 0.97, 0.92))
    PV.add_light('AREA', C.w2b((cx + 90, 40, 120)), 50000, size=80, color=(0.9, 0.95, 1.0))
    PV.add_light('AREA', C.w2b((cx, 20, 30)), 14000, size=60, color=(1.0, 0.95, 0.88))
    for o in bpy.context.scene.objects:
        if o.type == 'LIGHT':
            PV.look_at(o, C.w2b((cx, 20, 13)))
    out = out or os.path.join(C.PREVIEW, "supply_shelf_v3.png")
    PV.render_to(out)
    if os.path.dirname(os.path.abspath(out)) == os.path.abspath(C.PREVIEW):
        # keep the committed preview small: 1200 px wide, 256 colours
        from PIL import Image
        im = Image.open(out).convert("RGB")
        im = im.resize((1200, round(1200 * im.height / im.width)), Image.LANCZOS)
        im.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG).save(out, optimize=True)
    return out


if __name__ == "__main__":
    build()
    if "--preview" in sys.argv:
        print(render_preview())
        for v in ("feed", "care", "tools"):
            print(render_preview(os.path.join(C.BUILD, f"supply_shelf_{v}.png"), 1600, 900, 32, v))
