"""Supply shelf (docs/ASSETS.md §5.1): supply_shelf.glb, world coordinates (cm, Y up), beside the tank.

A warm driftwood rack with sea-glass edging standing to the right of the tank, three rows of supplies the live
screen's "Supplies" mode looks through (app/.../render/SupplyShelf.kt, ui/live/SupplyShelf.kt). Each prop is built to
read as its real-world product at shelf distance (its own silhouette and colours) and carries a printed label:

  row 0 FEED  (top):    flake tub, sinking-pellet jar, floating-pellet jar, algae-wafer pouch, frozen blister cards
                        (mysis, brine, cyclops), krill canister, prawn tray, nori on its veggie clip, coral-food
                        dropper bottle, phytoplankton squeeze bottle, live-pods jar
  row 1 CARE  (middle): bucket with a wire handle, jug, three dosing bottles (alk / calcium / magnesium), filter sock,
                        carbon bag, scissors in a pot, pest-zap syringe, acclimation drip cup
  row 2 TOOLS (bottom): Flipper magnet, test kit, microscope, glass knocker, tray of rock with tongs

Labels are drawn procedurally with PIL into one atlas (supply_labels, 1024², the app's Outfit font) and UV-mapped onto
label bands / quads; the atlas also holds the lit "Supplies" sign and the soft backdrop of the back panel. The runtime
names every prop on screen too (Compose name tags under each prop), so the print is flavour, not the only cue.

Nodes (direct children of Root, extras.part = "equipment"):
  ShelfFrame, ShelfBack, ShelfCrest    driftwood rack, back panel (backdrop print), sea-glass crest over the top
  ShelfLip0..2                         sea-glass row edging (extras.row), tinted by row (feed amber, care green, tools blue)
  ShelfSign                            the "Supplies" sign and the warm puck lights under the boards; drawn lit
                                       (SupplyShelfLayout.SIGN_NODE)
  Supply_<id>                          one node per prop, extras {item: id, row: 0..2}; the runtime picks, lifts
                                       and highlights each one as a whole (every material of the prop is a
                                       primitive of this one node)

Layout constants are mirrored in SupplyShelf.kt (X0..X1, SLOT_X0..SLOT_X1, ROW_Y, Z_PROP, ROW_COUNT); keep them in
sync. Props stand on their row board at z = Z_PROP, centred in equal slots (prop_xs). The runtime adds a soft
self-glow so the shelf reads at night (less on printed labels, more on the sign), and lifts / brightens a picked prop.
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
SEA_GLASS = ["#F2C17A", "#9EDFA6", "#8CD3EA"]   # row edging: amber (feed), green (care), blue (tools)

# ---- printed labels: (title, sub line, background, ink, accent, motif) ----
LABELS = {
    "flake": ("FLAKES", "tropical fish food", "#FF9A2E", "#FFFFFF", "#D63D2E", "flakes"),
    "pellet": ("SINKING\nPELLETS", "marine formula", "#7A4A2A", "#FFF4E0", "#E0A060", "pellets"),
    "floating_pellet": ("FLOATING\nPELLETS", "top feeder", "#FFD84A", "#3A2A10", "#2E9E96", "pellets"),
    "algae_wafer": ("ALGAE\nWAFERS", "for grazers", "#3E9A4A", "#FFFFFF", "#B6F07A", "wafer"),
    "mysis": ("MYSIS", "frozen shrimp", "#BFE7FF", "#12355A", "#5FA8FF", "shrimp"),
    "brine": ("BRINE\nSHRIMP", "frozen", "#FF9FBF", "#5A1030", "#FFFFFF", "shrimp"),
    "krill": ("KRILL", "freeze-dried", "#E8502E", "#FFFFFF", "#FFD27A", "shrimp"),
    "prawn": ("PRAWN", "chunks", "#FFFFFF", "#C2402A", "#FF8A65", "shrimp"),
    "cyclops": ("CYCLOPS", "frozen", "#D93A3A", "#FFFFFF", "#FF9C9C", "pellets"),
    "nori": ("NORI", "seaweed", "#1F4A2A", "#B6F07A", "#8EE66B", "leaf"),
    "coral_food": ("CORAL\nFOOD", "reef booster", "#F7A8C8", "#5A1040", "#B18CFF", "drop"),
    "plankton": ("PHYTO", "live plankton", "#5DBB4A", "#FFFFFF", "#D8FF9A", "bubbles"),
    "pods": ("LIVE\nPODS", "copepods", "#E8D2A6", "#4A3418", "#8A6A3A", "pellets"),
    "water_change": ("WATER\nCHANGE", "25 %", "#2E8FD8", "#FFFFFF", "#BFE7FF", "drop"),
    "water_small": ("SALT\nWATER", "10 %", "#BFE7FF", "#0E3A66", "#5FD7FF", "drop"),
    "dose_alk": ("ALK", "alkalinity +", "#8A5CE6", "#FFFFFF", "#D8C8FF", "drop"),
    "dose_ca": ("CALCIUM", "Ca +", "#2F7FD8", "#FFFFFF", "#BFE7FF", "drop"),
    "dose_mg": ("MAGNESIUM", "Mg +", "#3FA65A", "#FFFFFF", "#C8F5B0", "drop"),
    "new_sock": ("FILTER\nSOCK", "fresh & clean", "#FFFFFF", "#1B6A66", "#FFE14D", "bubbles"),
    "fresh_carbon": ("CARBON", "activated", "#22252A", "#FFFFFF", "#FF7AA8", "pellets"),
    "trim_algae": ("TRIM", "algae", "#2E9E96", "#FFFFFF", "#FFB13D", "leaf"),
    "zap_pest": ("PEST\nZAP", "aiptasia", "#B18CFF", "#2A1050", "#FF5C6C", "drop"),
    "settle_in": ("DRIP", "acclimate", "#FFFFFF", "#2E7F3A", "#FF5C6C", "bubbles"),
    "glass_cleaner": ("FLIPPER", "glass cleaner", "#2E9E5A", "#FFFFFF", "#B6F07A", "bubbles"),
    "test_kit": ("TEST KIT", "reef water", "#FFFFFF", "#12355A", "#5FD7FF", "swatches"),
    "microscope": ("MICROSCOPE", "look closer", "#1B6A80", "#FFFFFF", "#FFB13D", "bubbles"),
    "knocker": ("KNOCK", "tap tap!", "#FF7AA8", "#FFFFFF", "#FFE14D", "none"),
    "move_rocks": ("LIVE ROCK", "rearrange", "#B07A5A", "#FFFFFF", "#F2D6B0", "pellets"),
}
ATLAS = 1024
CELL_W, CELL_H = 256, 128
SIGN_CELL = 28        # two cells wide
BACKDROP_CELL = 30    # two cells wide
FONT = os.path.join(C.REPO, "app", "src", "main", "res", "font", "outfit_semibold.ttf")
FONT_SUB = os.path.join(C.REPO, "app", "src", "main", "res", "font", "inter_semibold.ttf")

_mats = {}
_label_mat = []


def mat(hexc, rough=0.55):
    key = (hexc, rough)
    if key not in _mats:
        _mats[key] = C.material("shelf_" + hexc.strip("#").lower() + ("" if rough == 0.55 else f"_{int(rough * 100)}"),
                                hexc, rough=rough)
    return _mats[key]


def label_mat():
    return _label_mat[0]


# ---- the label atlas (PIL) ----
def _rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _font(path, size):
    from PIL import ImageFont
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def _fit(draw, text, path, size, max_w, max_h):
    while size > 10:
        f = _font(path, size)
        b = draw.multiline_textbbox((0, 0), text, font=f, spacing=0, align="center")
        if b[2] - b[0] <= max_w and b[3] - b[1] <= max_h:
            return f, b
        size -= 2
    f = _font(path, size)
    return f, draw.multiline_textbbox((0, 0), text, font=f, spacing=0, align="center")


def _motif(d, kind, x0, y0, w, h, col, rng):
    if kind == "flakes":
        for _ in range(9):
            cx, cy = x0 + rng.uniform(0, w), y0 + rng.uniform(0, h)
            r = rng.uniform(3, 6)
            pts = [(cx + r * math.cos(a + rng.uniform(-.4, .4)), cy + r * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 5)[:-1]]
            d.polygon(pts, fill=col)
    elif kind == "pellets":
        for _ in range(10):
            cx, cy = x0 + rng.uniform(0, w), y0 + rng.uniform(0, h)
            d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=col)
    elif kind == "wafer":
        for k in range(3):
            cx = x0 + w * (0.2 + 0.3 * k)
            d.ellipse((cx - 9, y0 + h / 2 - 9, cx + 9, y0 + h / 2 + 9), fill=col)
    elif kind == "shrimp":
        for k in range(2):
            cx = x0 + w * (0.3 + 0.4 * k)
            d.arc((cx - 12, y0 + 2, cx + 12, y0 + h - 2), 200, 340 + 20, fill=col, width=5)
    elif kind == "leaf":
        for k in range(3):
            cx = x0 + w * (0.2 + 0.3 * k)
            d.ellipse((cx - 5, y0 + 2, cx + 5, y0 + h - 2), fill=col)
    elif kind == "drop":
        for k in range(3):
            cx = x0 + w * (0.2 + 0.3 * k)
            cy = y0 + h * 0.6
            d.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=col)
            d.polygon([(cx - 5, cy - 3), (cx + 5, cy - 3), (cx, cy - 14)], fill=col)
    elif kind == "bubbles":
        for _ in range(7):
            cx, cy, r = x0 + rng.uniform(0, w), y0 + rng.uniform(0, h), rng.uniform(3, 7)
            d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=col, width=2)
    elif kind == "swatches":
        cols = ["#8A5CE6", "#FF5C6C", "#8EE66B", "#FFB13D", "#5FD7FF"]
        for k, c in enumerate(cols):
            xx = x0 + w * 0.1 + k * (w * 0.8 / len(cols))
            d.rectangle((xx, y0 + 3, xx + w * 0.8 / len(cols) - 3, y0 + h - 3), fill=c)


def draw_atlas(path):
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", (ATLAS, ATLAS), (255, 255, 255, 255))
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(11)
    items = FEED + CARE + TOOLS
    for k, item in enumerate(items):
        title, sub, bg, ink, accent, motif = LABELS[item]
        x0, y0 = (k % 4) * CELL_W, (k // 4) * CELL_H
        d.rectangle((x0, y0, x0 + CELL_W - 1, y0 + CELL_H - 1), fill=_rgb(bg))
        # accent bands top and bottom, a motif strip along the bottom
        d.rectangle((x0, y0, x0 + CELL_W - 1, y0 + 9), fill=_rgb(accent))
        d.rectangle((x0, y0 + CELL_H - 30, x0 + CELL_W - 1, y0 + CELL_H - 1), fill=_rgb(accent))
        _motif(d, motif, x0 + 6, y0 + CELL_H - 28, CELL_W - 12, 26, _rgb(bg), rng)
        f, b = _fit(d, title, FONT, 52, CELL_W - 20, 66)
        tx = x0 + (CELL_W - (b[2] - b[0])) / 2 - b[0]
        ty = y0 + 12 + (68 - (b[3] - b[1])) / 2 - b[1]
        d.multiline_text((tx, ty), title, font=f, fill=_rgb(ink), spacing=0, align="center")
        fs, bs = _fit(d, sub, FONT_SUB, 18, CELL_W - 30, 20)
        sx = x0 + (CELL_W - (bs[2] - bs[0])) / 2 - bs[0]
        # sub line on a small pill over the motif strip, in the ink colour on the background
        pw = bs[2] - bs[0] + 16
        d.rounded_rectangle((x0 + (CELL_W - pw) / 2, y0 + CELL_H - 27, x0 + (CELL_W + pw) / 2, y0 + CELL_H - 5), 10, fill=_rgb(bg))
        d.text((sx, y0 + CELL_H - 26 - bs[1] + 1), sub, font=fs, fill=_rgb(ink))
    # the sign: warm white letters on deep sea teal with a gold rim
    x0, y0 = (SIGN_CELL % 4) * CELL_W, (SIGN_CELL // 4) * CELL_H
    d.rounded_rectangle((x0, y0, x0 + 2 * CELL_W - 1, y0 + CELL_H - 1), 26, fill=_rgb("#E8B04A"))
    d.rounded_rectangle((x0 + 7, y0 + 7, x0 + 2 * CELL_W - 8, y0 + CELL_H - 8), 20, fill=_rgb("#0F4A57"))
    f, b = _fit(d, "Supplies", FONT, 92, 2 * CELL_W - 70, 92)
    d.text((x0 + (2 * CELL_W - (b[2] - b[0])) / 2 - b[0], y0 + (CELL_H - (b[3] - b[1])) / 2 - b[1]), "Supplies",
           font=f, fill=_rgb("#FFF4D6"))
    for sx in (x0 + 22, x0 + 2 * CELL_W - 22):
        d.ellipse((sx - 6, y0 + CELL_H / 2 - 6, sx + 6, y0 + CELL_H / 2 + 6), fill=_rgb("#FFE14D"))
    # backdrop: a soft lagoon gradient with faint wave lines (stretched over the back panel)
    x0, y0 = (BACKDROP_CELL % 4) * CELL_W, (BACKDROP_CELL // 4) * CELL_H
    top, bot = np.array(_rgb("#3F8F8C"), float), np.array(_rgb("#1F5560"), float)
    for yy in range(CELL_H):
        t = yy / (CELL_H - 1)
        c = tuple(int(v) for v in top * (1 - t) + bot * t)
        d.line((x0, y0 + yy, x0 + 2 * CELL_W - 1, y0 + yy), fill=c)
    for k in range(5):
        yy = y0 + 14 + k * 24
        pts = [(x0 + xx, yy + 3 * math.sin(xx / 22 + k)) for xx in range(0, 2 * CELL_W, 6)]
        d.line(pts, fill=tuple(int(v) for v in (top * (1 - (yy - y0) / CELL_H) + bot * (yy - y0) / CELL_H) * 1.08), width=3)
    img.convert("RGB").save(path)
    return path


def cell_uv(cell, u, v, span=1):
    """Blender UV (v up) of point (u, v) in atlas cell `cell` (u across, v up), `span` cells wide; 3 px inset."""
    x0, y0 = (cell % 4) * CELL_W + 3, (cell // 4) * CELL_H + 3
    w, h = span * CELL_W - 6, CELL_H - 6
    px, py = x0 + u * w, y0 + (1 - v) * h
    return px / ATLAS, 1 - py / ATLAS


class Prop:
    """World-space geometry of one prop with per-face material colours (and the shared label atlas)."""

    def __init__(self):
        self.g = T.WGeo()
        self.cols = []

    def _slot(self, m):
        if m not in self.cols:
            self.cols.append(m)
        return self.cols.index(m)

    def add(self, V, F, hexc, rough=0.55):
        self.g.add(np.asarray(V, float), F, col=(1, 1, 1, 1), mat=self._slot(mat(hexc, rough)))

    def add_uv(self, V, F, UV):
        self.g.add(np.asarray(V, float), F, UV=UV, col=(1, 1, 1, 1), mat=self._slot(label_mat()))

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


def wrap(p, cell, x, y0, y1, z, r0, r1=None, arc=200.0, sides=12, span=1):
    """A printed label band on the front of a round body (facing +z): arc degrees, radius r0 at y0 .. r1 at y1."""
    r1 = r0 if r1 is None else r1
    arc = math.radians(arc)
    V, UV, F = [], [], []
    for i in range(sides + 1):
        t = i / sides
        a = -arc / 2 + arc * t
        for yy, rr, v in ((y0, r0, 0.0), (y1, r1, 1.0)):
            V.append((x + rr * math.sin(a), yy, z + rr * math.cos(a)))
            UV.append(cell_uv(cell, t, v, span))
    for i in range(sides):
        F.append((2 * i, 2 * i + 2, 2 * i + 3, 2 * i + 1))
    p.add_uv(V, F, UV)


def plate(p, cell, c, w, h, xdir=(1, 0, 0), ydir=(0, 1, 0), span=1):
    """A flat printed label quad centred at c (facing xdir × ydir)."""
    c = np.asarray(c, float)
    xd = np.asarray(xdir, float); xd /= np.linalg.norm(xd)
    yd = np.asarray(ydir, float); yd /= np.linalg.norm(yd)
    V = [c - xd * w / 2 - yd * h / 2, c + xd * w / 2 - yd * h / 2, c + xd * w / 2 + yd * h / 2, c - xd * w / 2 + yd * h / 2]
    UV = [cell_uv(cell, 0, 0, span), cell_uv(cell, 1, 0, span), cell_uv(cell, 1, 1, span), cell_uv(cell, 0, 1, span)]
    p.add_uv(V, [(0, 1, 2, 3)], UV)


# ---- product builders: each builds at base centre (x, y, z) ----
def tub(p, cell, x, y, z, r, h, body, lid, lid_h=1.3, band=(0.12, 0.86), lid_r=None, sides=18):
    """A screw-top food tub / jar with a wrap-around printed label."""
    lr = lid_r or r + 0.15
    p.add(*cyl((x, y, z), (x, y + h, z), r, sides), body, 0.3)
    p.add(*cyl((x, y + h, z), (x, y + h + lid_h, z), lr, sides), lid, 0.4)
    p.add(*ring((x, y + h + lid_h * 0.35, z), lr, 0.09, sides, 4), lid, 0.4)
    p.add(*ring((x, y + h + lid_h * 0.75, z), lr, 0.09, sides, 4), lid, 0.4)
    p.add(*cyl((x, y + h * band[0], z), (x, y + h * band[1], z), r + 0.03, sides), LABELS_BG(cell), 0.5)
    wrap(p, cell, x, y + h * band[0], y + h * band[1], z, r + 0.07, arc=150)


def LABELS_BG(cell):
    return LABELS[(FEED + CARE + TOOLS)[cell]][2]


def frozen(p, cell, x, y, z, card, cubes):
    """A frozen-food blister card leaning back: printed top half, 2 × 3 frosty cubes below."""
    tilt = rx(-0.16)
    c = np.array([x, y + 4.8, z])
    V, F = obox((0, 0, 0), (5.8, 9.4, 0.5), (1, 0, 0), (0, 1, 0))
    p.add(V @ tilt.T + c, F, card)
    V = [np.array(v) @ tilt.T + c for v in ((-2.75, 0.9, 0.27), (2.75, 0.9, 0.27), (2.75, 4.45, 0.27), (-2.75, 4.45, 0.27))]
    p.add_uv(V, [(0, 1, 2, 3)], [cell_uv(cell, 0, 0), cell_uv(cell, 1, 0), cell_uv(cell, 1, 1), cell_uv(cell, 0, 1)])
    for i in range(2):
        for j in range(3):
            V, F = obox((-1.3 + 2.6 * i, -3.6 + 1.5 * j, 0.55), (2.1, 1.25, 0.7), (1, 0, 0), (0, 1, 0))
            p.add(V @ tilt.T + c, F, cubes, 0.2)
    # hang hole tab
    V, F = obox((0, 4.95, 0), (1.6, 0.5, 0.55), (1, 0, 0), (0, 1, 0))
    p.add(V @ tilt.T + c, F, "#F2F6FF")


def dose_bottle(p, cell, x, y, z, cap, h=6.6, r=2.0, body="#F4F6F8"):
    """A reef dosing bottle: white body, shoulder, neck, ribbed colour cap, wrap label."""
    p.add(*cyl((x, y, z), (x, y + h, z), r, 16), body, 0.35)
    p.add(*cyl((x, y + h, z), (x, y + h + 1.1, z), r, 16, r1=r * 0.45), body, 0.35)
    p.add(*cyl((x, y + h + 1.1, z), (x, y + h + 1.5, z), r * 0.42, 12), body, 0.35)
    p.add(*cyl((x, y + h + 1.5, z), (x, y + h + 3.0, z), r * 0.6, 14), cap, 0.4)
    for k in range(3):
        p.add(*ring((x, y + h + 1.8 + 0.45 * k, z), r * 0.6, 0.07, 14, 4), cap, 0.4)
    p.add(*cyl((x, y + h * 0.12, z), (x, y + h * 0.82, z), r + 0.03, 16), LABELS_BG(cell), 0.5)
    wrap(p, cell, x, y + h * 0.12, y + h * 0.82, z, r + 0.07, arc=150)


def build_prop(item, x, y, z, cell):
    p = Prop()
    if item == "flake":
        # wide, short tub with a red flip-sprinkle lid
        tub(p, cell, x, y, z, 2.8, 5.6, "#FFF4E0", "#E0402E", lid_h=1.5, lid_r=2.95)
        p.add(*obox((x, y + 7.15, z + 1.2), (2.8, 0.25, 1.4), (1, 0, 0), (0, 1, 0)), "#B02A1E", 0.4)
    elif item == "pellet":
        # tall narrow jar of brown pellets, teal cap, pellets showing above the label
        tub(p, cell, x, y, z, 2.0, 8.6, "#DDEEF0", "#2E9E96", band=(0.08, 0.72), lid_r=2.15)
        for k in range(6):
            a = k * 1.1
            p.add(*blob((x + 1.4 * math.cos(a), y + 6.6 + 0.6 * (k % 3), z + 1.4 * math.sin(a) + 0.2), (0.45, 0.45, 0.45), 6, 3), "#7A4A2A", 0.7)
    elif item == "floating_pellet":
        # squat yellow jar with a fat blue lid
        tub(p, cell, x, y, z, 2.7, 4.8, "#FFF7C2", "#3E8FD8", lid_h=1.9, lid_r=2.9)
    elif item == "algae_wafer":
        # a stand-up pouch with a tear strip and a round window of wafers
        p.add(*obox((x, y + 4.3, z), (5.8, 8.6, 1.8), (1, 0, 0), (0, 1, 0)), "#3E9A4A", 0.5)
        p.add(*obox((x, y + 8.85, z), (5.8, 0.6, 0.6), (1, 0, 0), (0, 1, 0)), "#2C6E35", 0.5)
        p.add(*obox((x, y + 0.5, z), (5.4, 1.0, 2.6), (1, 0, 0), (0, 1, 0)), "#2C6E35", 0.5)
        plate(p, cell, (x, y + 5.3, z + 0.93), 5.5, 2.75)
        for k in range(2):
            p.add(*cyl((x - 1.3 + 2.6 * k, y + 2.2, z + 0.9), (x - 1.3 + 2.6 * k, y + 2.2, z + 1.05), 1.0, 12), "#2F5A20", 0.8)
    elif item == "mysis":
        frozen(p, cell, x, y, z, "#E6F5FF", "#CFEBFF")
    elif item == "brine":
        frozen(p, cell, x, y, z, "#FFE3EC", "#FFC2D4")
    elif item == "krill":
        # freeze-dried krill canister: tall, red lid, krill peeking over the rim
        tub(p, cell, x, y, z, 2.3, 7.6, "#FFF0E6", "#C0301E", lid_h=1.2)
    elif item == "prawn":
        # a foam tray pack leaning back: a big pink prawn under the film, the printed label across the top
        tilt = rx(-0.2)
        c = np.array([x, y + 4.6, z - 0.4])
        V, F = obox((0, 0, 0), (6.2, 9.0, 1.2), (1, 0, 0), (0, 1, 0))
        p.add(V @ tilt.T + c, F, "#F7F7F2", 0.6)
        V = [np.array(v) @ tilt.T + c for v in ((-2.9, 1.6, 0.62), (2.9, 1.6, 0.62), (2.9, 4.3, 0.62), (-2.9, 4.3, 0.62))]
        p.add_uv(V, [(0, 1, 2, 3)], [cell_uv(cell, 0, 0), cell_uv(cell, 1, 0), cell_uv(cell, 1, 1), cell_uv(cell, 0, 1)])
        pts = np.array([(-2.0, -2.6, 0.9), (-1.6, -0.6, 1.1), (-0.2, 0.6, 1.2), (1.4, 0.2, 1.1), (2.0, -1.4, 0.9)])
        V, F, _, _ = G.tube(pts, [0.5, 0.95, 1.1, 0.9, 0.55], 10, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V @ tilt.T + c, F, "#FF8A65", 0.45)
        for k in range(3):
            q = pts[1 + k]
            V, F = ring((0, 0, 0), 0.95 - 0.08 * k, 0.1, 10, 4, rot=rx(math.pi / 2))
            p.add(V @ tilt.T + c + q @ tilt.T, F, "#FFC2AE", 0.45)
        V, F = G.cone(pts[0], pts[0] + (-0.9, -0.9, 0), 0.9, 6)
        p.add(V @ tilt.T + c, F, "#E8603E", 0.45)
    elif item == "cyclops":
        frozen(p, cell, x, y, z, "#FFE0E0", "#FF8C8C")
    elif item == "nori":
        # a nori sheet held by the green veggie clip on a suction-cup stalk; label on the foot
        p.add(*obox((x, y + 0.5, z), (5.6, 1.0, 3.6), (1, 0, 0), (0, 1, 0)), "#24402A", 0.6)
        p.add(*cyl((x, y + 1.0, z - 1.0), (x, y + 9.4, z - 1.0), 0.3, 8), "#2E7F3A", 0.4)
        for k, (dx, col) in enumerate(((0.35, "#16301C"), (0.0, "#1F4A2A"))):
            V, F = obox((x + dx, y + 5.4 - 0.3 * k, z - 0.4 + 0.25 * k), (5.2, 6.4, 0.12), (1, 0.04 * (k - 0.5), 0), (0, 1, 0))
            p.add(V, F, col, 0.7)
        plate(p, cell, (x, y + 5.0, z + 0.1), 5.0, 2.5)
        p.add(*obox((x, y + 9.3, z - 0.1), (2.4, 1.8, 1.6), (1, 0, 0), (0, 1, 0)), "#8EE66B", 0.35)
        p.add(*cyl((x, y + 9.3, z - 1.0), (x, y + 9.3, z - 1.9), 1.1, 12, r1=1.6), "#C9F5D0", 0.3)
    elif item == "coral_food":
        # a dropper bottle of pink coral food
        p.add(*cyl((x, y, z), (x, y + 6.2, z), 1.9, 16), "#F7A8C8", 0.25)
        p.add(*cyl((x, y + 6.2, z), (x, y + 7.2, z), 1.9, 16, r1=0.8), "#F7A8C8", 0.25)
        p.add(*cyl((x, y + 7.2, z), (x, y + 8.6, z), 0.85, 12), "#B18CFF", 0.4)
        p.add(*cyl((x, y + 8.6, z), (x, y + 10.4, z), 0.55, 10, r1=0.12), "#B18CFF", 0.4)
        p.add(*cyl((x, y + 0.7, z), (x, y + 5.5, z), 1.93, 16), LABELS_BG(cell), 0.5)
        wrap(p, cell, x, y + 0.7, y + 5.5, z, 1.97, arc=150)
    elif item == "plankton":
        # a green squeeze bottle with a domed shoulder and flip cap
        p.add(*cyl((x, y, z), (x, y + 7.0, z), 2.1, 16, r1=2.3), "#7CCB5A", 0.3)
        p.add(*blob((x, y + 7.0, z), (2.3, 1.3, 2.3), 16, 6), "#7CCB5A", 0.3)
        p.add(*cyl((x, y + 8.0, z), (x, y + 9.6, z), 0.9, 12), "#F2F6FF", 0.4)
        p.add(*obox((x + 0.5, y + 9.8, z), (1.4, 0.4, 1.2), (1, 0, 0), (0, 1, 0)), "#F2F6FF", 0.4)
        p.add(*cyl((x, y + 0.9, z), (x, y + 6.1, z), 2.15, 16, r1=2.29), LABELS_BG(cell), 0.5)
        wrap(p, cell, x, y + 0.9, y + 6.1, z, 2.19, 2.33, arc=150)
    elif item == "pods":
        # a little jar of live pods: cloudy water with critters above the label, orange lid
        tub(p, cell, x, y, z, 2.2, 6.4, "#E8E0C8", "#FFB13D", band=(0.06, 0.6))
        for k in range(5):
            p.add(*blob((x - 1.2 + 0.6 * k, y + 4.6 + 0.6 * (k % 2), z + 2.05), (0.32, 0.18, 0.12), 6, 3), "#8A6A3A")
    elif item == "water_change":
        # a white bucket with a wire handle, blue band label, water inside, siphon hose over the rim
        p.add(*cyl((x, y, z), (x, y + 7.2, z), 3.0, 20, r1=3.6), "#EEF3F7", 0.4)
        p.add(*ring((x, y + 7.2, z), 3.6, 0.25, 20, 6), "#DCE4EA", 0.35)
        p.add(*cyl((x, y + 6.3, z), (x, y + 6.45, z), 3.45, 20), "#4FB8F0", 0.1)
        pts = [(x + 3.55 * math.cos(a), y + 7.0 + 3.6 * math.sin(a) * 0.95, z) for a in np.linspace(0, math.pi, 11)]
        V, F, _, _ = G.tube(np.array(pts), 0.14, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V, F, "#8A949C", 0.25)
        p.add(*cyl((x - 1.2, y + 10.42, z), (x + 1.2, y + 10.42, z), 0.42, 10), "#1B1340", 0.6)
        wrap(p, cell, x, y + 1.6, y + 5.6, z, 3.15, 3.5, arc=150)
        pts = [(x + 2.0, y + 4.0, z - 1.6), (x + 2.4, y + 8.6, z - 1.4), (x + 3.4, y + 9.0, z - 1.6), (x + 3.9, y + 6.0, z - 2.2),
               (x + 3.7, y + 0.8, z - 2.6)]
        V, F, _, _ = G.tube(np.array(pts), 0.3, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V, F, "#BFE7FF", 0.3)
    elif item == "water_small":
        # a jug of fresh salt water: body, blue cap, side handle, front label
        p.add(*obox((x - 0.4, y + 3.8, z), (4.8, 7.6, 4.0), (1, 0, 0), (0, 1, 0)), "#D8F0FF", 0.25)
        p.add(*cyl((x - 0.4, y + 7.6, z), (x - 1.0, y + 8.8, z), 2.0, 14, r1=0.9), "#D8F0FF", 0.25)
        p.add(*cyl((x - 1.0, y + 8.8, z), (x - 1.0, y + 9.9, z), 1.0, 12), "#2E8FD8", 0.35)
        p.add(*ring((x + 2.2, y + 5.4, z), 1.5, 0.42, 12, 6, rot=rx(math.pi / 2)), "#D8F0FF", 0.25)
        plate(p, cell, (x - 0.4, y + 3.6, z + 2.04), 4.4, 3.3)
    elif item == "dose_alk":
        dose_bottle(p, cell, x, y, z, "#8A5CE6")
    elif item == "dose_ca":
        dose_bottle(p, cell, x, y, z, "#2F7FD8")
    elif item == "dose_mg":
        dose_bottle(p, cell, x, y, z, "#3FA65A")
    elif item == "new_sock":
        # a clean white filter sock hanging from its yellow ring on a little stand; label on the base
        p.add(*obox((x, y + 0.5, z), (6.2, 1.0, 4.4), (1, 0, 0), (0, 1, 0)), "#1B6A66", 0.5)
        p.add(*cyl((x - 2.6, y + 1.0, z - 1.4), (x - 2.6, y + 10.2, z - 1.4), 0.25, 6), "#1B6A66")
        p.add(*cyl((x - 2.6, y + 10.0, z - 1.4), (x, y + 10.0, z - 1.4), 0.2, 6), "#1B6A66")
        pts = [(x, y + 9.4, z), (x, y + 6.6, z), (x, y + 3.6, z), (x, y + 2.0, z)]
        V, F, _, _ = G.tube(np.array(pts), [2.1, 2.0, 1.7, 1.1], 14, cap_start=False, cap_end=True, up=(1, 0, 0))
        p.add(V, F, "#FBFBF6", 0.9)
        p.add(*ring((x, y + 9.6, z), 2.2, 0.45, 18, 6), "#FFE14D", 0.35)
        plate(p, cell, (x, y + 5.4, z + 2.15), 4.6, 2.3)
    elif item == "fresh_carbon":
        # a black mesh bag of carbon, pink tie, a printed tag hanging in front
        p.add(*blob((x, y + 2.8, z - 0.4), (3.1, 2.8, 2.4), 12, 7), "#33363B", 0.9)
        p.add(*cyl((x, y + 5.2, z - 0.4), (x, y + 6.8, z - 0.4), 0.6, 8, r1=0.95), "#33363B", 0.9)
        p.add(*ring((x, y + 5.5, z - 0.4), 0.78, 0.22, 10, 5), "#FF7AA8", 0.35)
        for k in range(6):
            a = 2 * math.pi * k / 6
            p.add(*blob((x + 2.2 * math.cos(a), y + 2.8 + 0.9 * math.sin(2 * a), z - 0.4 + 2.0 * math.sin(a)), (0.4, 0.35, 0.4), 6, 3), "#4A4E55")
        tag = rx(-0.12)
        c = np.array([x, y + 2.4, z + 2.4])
        V, F = obox((0, 0, -0.06), (5.8, 3.0, 0.1), (1, 0, 0), (0, 1, 0))
        p.add(V @ tag.T + c, F, "#22252A")
        V = [np.array(v) @ tag.T + c for v in ((-2.8, -1.4, 0.01), (2.8, -1.4, 0.01), (2.8, 1.4, 0.01), (-2.8, 1.4, 0.01))]
        p.add_uv(V, [(0, 1, 2, 3)], [cell_uv(cell, 0, 0), cell_uv(cell, 1, 0), cell_uv(cell, 1, 1), cell_uv(cell, 0, 1)])
    elif item == "trim_algae":
        # a pair of scissors standing blades-down in a teal pot
        p.add(*cyl((x, y, z), (x, y + 4.2, z), 2.2, 16, r1=2.6), "#2E9E96", 0.4)
        p.add(*ring((x, y + 4.2, z), 2.6, 0.18, 16, 4), "#1B6A66", 0.4)
        wrap(p, cell, x, y + 0.6, y + 3.7, z, 2.3, 2.56, arc=150)
        for s in (-1, 1):
            V, F = obox((0, 0, 0), (0.55, 6.0, 0.22), (1, 0, 0), (0, 1, 0))
            p.add(V @ rz(0.22 * s).T + (x + 0.65 * s, y + 5.4, z + 0.2 * s), F, "#C9D4DC", 0.15)
            V, F = obox((0, 0, 0), (0.5, 2.4, 0.3), (1, 0, 0), (0, 1, 0))
            p.add(V @ rz(-0.35 * s).T + (x - 0.5 * s, y + 9.2, z), F, "#FFB13D", 0.35)
            p.add(*ring((x - 1.35 * s, y + 10.9, z), 1.05, 0.34, 14, 6, rot=rx(math.pi / 2)), "#FFB13D", 0.35)
        p.add(*blob((x, y + 8.0, z + 0.3), (0.38, 0.38, 0.38), 8, 4), "#1B1340")
    elif item == "zap_pest":
        # a big syringe of pest killer standing on a round foot
        p.add(*cyl((x, y, z), (x, y + 0.6, z), 2.4, 14), "#B18CFF", 0.4)
        p.add(*cyl((x, y + 0.6, z), (x, y + 2.0, z), 0.08, 6), "#9AA4AC", 0.2)
        V, F = G.cone((x, y + 2.8, z), (x, y + 2.0, z), 0.45, 8)
        p.add(V, F, "#E8ECF0", 0.3)
        p.add(*cyl((x, y + 2.8, z), (x, y + 9.2, z), 1.05, 14), "#DDF3FF", 0.15)
        p.add(*cyl((x, y + 2.9, z), (x, y + 5.6, z), 0.96, 14), "#B18CFF", 0.3)
        wrap(p, cell, x, y + 5.7, y + 8.9, z, 1.1, arc=160)
        p.add(*obox((x, y + 9.35, z), (4.2, 0.3, 1.4), (1, 0, 0), (0, 1, 0)), "#F2F6FF", 0.4)
        p.add(*obox((x, y + 10.6, z), (0.5, 2.6, 0.5), (1, 0, 0), (0, 1, 0)), "#F2F6FF", 0.4)
        p.add(*cyl((x, y + 11.9, z), (x, y + 12.3, z), 1.4, 14), "#FF5C6C", 0.35)
    elif item == "settle_in":
        # an acclimation drip cup with an airline loop, a valve and a heart
        p.add(*cyl((x, y, z), (x, y + 5.6, z), 2.3, 16, r1=2.9), "#F2F6FF", 0.3)
        p.add(*cyl((x, y + 4.6, z), (x, y + 4.75, z), 2.78, 16), "#5FD7FF", 0.15)
        wrap(p, cell, x, y + 0.7, y + 4.2, z, 2.42, 2.82, arc=150)
        pts = [(x + 2.0, y + 5.4, z - 0.4), (x + 3.2, y + 8.0, z - 0.4), (x + 1.0, y + 9.6, z - 0.8), (x - 1.6, y + 8.4, z - 1.0)]
        V, F, _, _ = G.tube(np.array(pts), 0.2, 6, cap_start=True, cap_end=True, up=(0, 0, 1))
        p.add(V, F, "#8EE66B", 0.3)
        p.add(*blob((x + 3.0, y + 7.6, z - 0.4), (0.5, 0.5, 0.5), 8, 4), "#2E9E96", 0.4)
        hy = y + 7.8
        p.add(*blob((x - 0.55, hy, z + 0.6), (0.75, 0.75, 0.55), 8, 5), "#FF5C6C", 0.35)
        p.add(*blob((x + 0.55, hy, z + 0.6), (0.75, 0.75, 0.55), 8, 5), "#FF5C6C", 0.35)
        V, F = G.cone((x, hy + 0.1, z + 0.6), (x, hy - 1.5, z + 0.6), 1.15, 8)
        p.add(V, F, "#FF5C6C", 0.35)
    elif item == "glass_cleaner":
        # the Flipper: two magnet halves standing together on a little stand, label on the front half
        for k, dz in enumerate((-1.6, 1.6)):
            V, F, _, _ = G.tube(np.array([(x, y + 1.2, z + dz), (x, y + 13.2, z + dz)]), 2.9, 12, cap_start=True, cap_end=True,
                                up=(1, 0, 0), squash=0.42)
            p.add(V, F, "#2E9E5A" if k else "#1B6A40", 0.4)
            p.add(*obox((x, y + 7.2, z + dz * 0.45), (5.0, 11.6, 0.4), (1, 0, 0), (0, 1, 0)), "#1B1340", 0.8)
        plate(p, cell, (x, y + 9.0, z + 1.6 + 1.24), 5.0, 2.5)
        p.add(*obox((x, y + 3.6, z + 2.9), (4.4, 0.3, 0.25), (1, 0, 0), (0, 1, 0)), "#C9D4DC", 0.2)
        p.add(*obox((x, y + 0.6, z), (9.0, 1.2, 8.0), (1, 0, 0), (0, 1, 0)), "#5FD7FF", 0.4)
    elif item == "test_kit":
        # a test kit case with its lid open, a colour card, four vials; label on the case front
        p.add(*obox((x, y + 2.6, z), (14.0, 5.2, 8.0), (1, 0, 0), (0, 1, 0)), "#F2F6FF", 0.4)
        plate(p, cell, (x, y + 2.6, z + 4.03), 8.6, 4.3)
        for s in (-1, 1):
            p.add(*obox((x + 5.8 * s, y + 2.6, z + 4.03), (1.6, 4.6, 0.06), (1, 0, 0), (0, 1, 0)), "#5FD7FF", 0.4)
        V, F = obox((0, 0, 0), (14.0, 0.8, 8.0), (1, 0, 0), (0, 1, 0))
        p.add(V @ rx(-1.2).T + (x, y + 8.4, z - 4.2), F, "#5FD7FF", 0.4)
        V, F = obox((0, 0, 0), (10.0, 0.2, 4.4), (1, 0, 0), (0, 1, 0))
        p.add(V @ rx(-1.2).T + (x, y + 8.6, z - 3.6), F, "#FFE14D")
        for k, capc in enumerate(("#8A5CE6", "#FF5C6C", "#3FA65A", "#FFB13D")):
            vx = x - 4.2 + 2.8 * k
            p.add(*cyl((vx, y + 5.2, z + 1.0), (vx, y + 9.6, z + 1.0), 0.8, 10), "#DDF3F7", 0.15)
            p.add(*cyl((vx, y + 5.3, z + 1.0), (vx, y + 7.4, z + 1.0), 0.72, 10), capc, 0.2)
            p.add(*cyl((vx, y + 9.6, z + 1.0), (vx, y + 10.6, z + 1.0), 0.9, 10), capc, 0.35)
    elif item == "microscope":
        # a chunky kid's microscope: base, arm, stage with clips, tube, eyepiece, focus knob; label on the base
        p.add(*obox((x, y + 0.9, z), (10.0, 1.8, 7.6), (1, 0, 0), (0, 1, 0)), "#1B6A80", 0.4)
        plate(p, cell, (x, y + 0.9, z + 3.83), 7.6, 1.6)
        V, F, _, _ = G.tube(np.array([(x, y + 1.8, z - 2.6), (x, y + 6.0, z - 3.0), (x, y + 10.0, z - 2.4), (x, y + 12.2, z - 1.0)]),
                            1.1, 10, cap_start=True, cap_end=True, up=(1, 0, 0), squash=0.8)
        p.add(V, F, "#F2F6FF", 0.4)
        p.add(*obox((x, y + 5.4, z + 0.6), (6.4, 0.7, 5.0), (1, 0, 0), (0, 1, 0)), "#1B1340", 0.6)
        for s in (-1, 1):
            p.add(*obox((x + 1.8 * s, y + 5.85, z + 1.0), (0.4, 0.2, 2.6), (1, 0, 0), (0, 1, 0)), "#C9D4DC", 0.2)
        p.add(*obox((x, y + 5.82, z + 0.8), (2.6, 0.06, 1.2), (1, 0, 0), (0, 1, 0)), "#BFE7FF", 0.1)
        p.add(*cyl((x, y + 7.4, z + 0.6), (x, y + 13.4, z - 1.0), 1.5, 14), "#F2F6FF", 0.35)
        p.add(*cyl((x, y + 13.4, z - 1.0), (x, y + 15.0, z - 1.4), 1.0, 12), "#1B1340", 0.5)
        p.add(*cyl((x, y + 6.4, z + 1.0), (x, y + 7.6, z + 0.7), 1.0, 12, r1=1.4), "#5FD7FF", 0.3)
        p.add(*cyl((x - 1.8, y + 9.0, z - 2.6), (x + 1.8, y + 9.0, z - 2.6), 1.3, 14), "#FFB13D", 0.35)
    elif item == "knocker":
        # a soft rubber mallet for knocking on the glass, leaning on a stand; label on the stand front
        p.add(*obox((x, y + 0.6, z), (9.0, 1.2, 6.0), (1, 0, 0), (0, 1, 0)), "#2E9E96", 0.4)
        plate(p, cell, (x, y + 0.6, z + 3.03), 6.4, 1.1)
        hdir = np.array([0.35, 1.0, 0.0])
        hdir /= np.linalg.norm(hdir)
        b0 = np.array([x - 2.4, y + 1.2, z])
        b1 = b0 + hdir * 11.0
        p.add(*cyl(b0, b1, 0.55, 10), "#E8D2A6", 0.6)
        p.add(*cyl(b0, b0 + hdir * 3.0, 0.7, 10), "#1B1340", 0.7)
        side = np.array([1.0, -0.35, 0.0])
        side /= np.linalg.norm(side)
        p.add(*cyl(b1 - side * 2.6, b1 + side * 2.6, 1.8, 16), "#FF7AA8", 0.5)
        p.add(*cyl(b1 + side * 2.6, b1 + side * 3.1, 1.4, 16), "#F2F6FF", 0.5)
        p.add(*cyl(b1 - side * 3.1, b1 - side * 2.6, 1.4, 16, r1=1.8), "#F2F6FF", 0.5)
    elif item == "move_rocks":
        # a tray of live rock with a pair of rock tongs; label on the tray front
        p.add(*obox((x, y + 0.8, z), (14.0, 1.6, 8.0), (1, 0, 0), (0, 1, 0)), "#7A5236", 0.5)
        plate(p, cell, (x, y + 0.8, z + 4.03), 7.0, 1.5)
        p.add(*blob((x - 3.0, y + 3.4, z + 0.4), (3.4, 2.4, 3.0), 10, 6, rot=rz(0.3)), "#CFC2A4", 0.9)
        p.add(*blob((x + 2.6, y + 2.8, z - 0.6), (2.8, 1.9, 2.6), 10, 6, rot=rz(-0.4)), "#B07592", 0.9)
        p.add(*blob((x + 0.2, y + 5.4, z + 0.2), (2.2, 1.6, 2.0), 10, 6, rot=rz(0.8)), "#8A8450", 0.9)
        p.add(*blob((x - 4.2, y + 5.0, z + 1.0), (0.8, 1.2, 0.8), 8, 4), "#FF7A6A", 0.6)
        for s in (-1, 1):
            p.add(*cyl((x + 5.0, y + 1.4, z + 2.6 + 0.5 * s), (x - 0.6, y + 9.2, z + 1.4 + 0.2 * s), 0.25, 6), "#9AA4AC", 0.2)
        p.add(*blob((x + 5.0, y + 1.5, z + 2.6), (0.6, 0.6, 0.9), 6, 3), "#FFB13D")
    else:
        raise ValueError(item)
    return p


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
    bp.add_uv(V, [(0, 1, 2, 3)], [cell_uv(BACKDROP_CELL, 0, 0, 2), cell_uv(BACKDROP_CELL, 1, 0, 2),
                                   cell_uv(BACKDROP_CELL, 1, 1, 2), cell_uv(BACKDROP_CELL, 0, 1, 2)])
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
        V, F, _, _ = G.tube(np.array([(X0 + 0.6, y + 0.3, Z1 - 0.2), (X1 - 0.6, y + 0.3, Z1 - 0.2)]), 0.75, 10,
                            cap_start=True, cap_end=True, up=(0, 1, 0))
        t.add(V, F, SEA_GLASS[r], 0.15)
        # rounded sea-glass beads along it
        for k in range(12):
            bx = X0 + 4 + (X1 - X0 - 8) * k / 11
            t.add(*blob((bx, y + 0.3, Z1 + 0.5), (1.1, 0.9, 0.5), 8, 4), ["#BFEDE2", "#9FD9E8", "#D9F2C9"][(k + r) % 3], 0.15)
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
    sign.add(*obox((mx, sy, Z1 - 1.6), (31.0, 8.2, 1.0), (1, 0, 0), (0, 1, 0)), "#E8B04A", 0.4)
    plate(sign, SIGN_CELL, (mx, sy, Z1 - 1.08), 30.0, 7.5, span=2)
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
    atlas = draw_atlas(os.path.join(C.BUILD, "supply_labels.png"))
    img = C.load_image(atlas, "supply_labels")
    _label_mat.append(C.material("shelf_labels", "#FFFFFF", rough=0.6, image=img, double=True))
    root = C.empty("Root")
    C.set_extras(root, units="cm", spec="docs/ASSETS.md §5.1", shelf=True)
    objs = rack(root)
    layout = {}
    cell = 0
    for r, items in enumerate(ROWS):
        for item, x in zip(items, prop_xs(len(items))):
            p = build_prop(item, x, ROW_Y[r], Z_PROP, cell)
            objs.append(p.obj("Supply_" + item, root, {"item": item, "row": r}))
            layout[item] = (round(x, 2), ROW_Y[r])
            cell += 1
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
