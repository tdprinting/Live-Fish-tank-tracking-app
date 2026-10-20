"""Equipment props (docs/specs/tank_design.md §3.2): equipment.glb, world coordinates (cm), every mesh closed.

Nodes (direct children of Root, extras.part = "equipment" unless noted):
  PowerheadLeft, PowerheadBack, PowerheadRight  (extras.dir = jet direction, extras.jet = nozzle point)
  Heater, HeaterLED ("light"), IntakeTube, Flipper (inside + outside halves, label texture),
  FrontMagnet, FrontMagnetBracket, HoseArc, CableLeft, HobFilter, ReturnPipe, ReturnNozzle (extras.outflow), Skimmer
  (its collection cup is care_tools.glb, seated at SKIMMER_CUP),
  AtoBox, AtoLED ("light"), Jar, AiPrimeL/R (puck, heat-sink, gooseneck, clamp, power cable, lens plate) and
  AiPrimeL_Lens/AiPrimeR_Lens (part "emitter": 4 x 4 LED dots whose emissive the runtime sets to the light color).
"""
import math
import os

import numpy as np

import common as C
import geomlib as G
import paintlib as P
import tank as T
from common import bpy

L, H, D = T.TANK
# Care props (care_tools.py; mirrored in app render/CareScene.kt CareLayout): the skimmer's collection cup base on
# the skimmer lid, and the filter sock's collar on the HOB filter lid (x, y, z world cm).
SKIMMER_CUP = (63.0, C.RIM_Y + 10.2, -5.2)
HOB_SOCK = (108.5, C.RIM_Y + 5.7, -3.7)
# AI Prime puck centre z (cm from the back glass): a little behind the centre line, arms rising from behind the tank
LIGHT_Z = 20.0


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def cylinder(p0, p1, r, sides=16, caps=True, r1=None):
    """Closed cylinder along p0->p1 (world)."""
    pts = np.array([p0, p1], float)
    V, F, t, a = G.tube(pts, [r, r if r1 is None else r1], sides, cap_start=caps, cap_end=caps, up=(0, 1, 0))
    # flat caps: pull the cap centres back onto the end planes
    if caps:
        V[2 * sides] = pts[1]
        V[2 * sides + 1] = pts[0]
    uv = np.c_[a, t]
    return V, F, uv


def disc(center, normal, r, sides=16):
    n = unit(normal)
    up = np.array([0, 1.0, 0]) if abs(n[1]) < 0.9 else np.array([1.0, 0, 0])
    u = unit(np.cross(up, n))
    v = np.cross(n, u)
    c = np.asarray(center, float)
    V = [c] + [c + r * (math.cos(2 * math.pi * k / sides) * u + math.sin(2 * math.pi * k / sides) * v) for k in range(sides)]
    F = [(0, 1 + k, 1 + (k + 1) % sides) for k in range(sides)]
    uv = [(0.5, 0.5)] + [(0.5 + 0.5 * math.cos(2 * math.pi * k / sides), 0.5 + 0.5 * math.sin(2 * math.pi * k / sides))
                         for k in range(sides)]
    return np.array(V), F, np.array(uv)


def torus(center, R, r, n=24, m=8):
    V, F = [], []
    for i in range(n):
        a = 2 * math.pi * i / n
        for j in range(m):
            b = 2 * math.pi * j / m
            V.append((center[0] + (R + r * math.cos(b)) * math.cos(a), center[1] + r * math.sin(b),
                      center[2] + (R + r * math.cos(b)) * math.sin(a)))
    for i in range(n):
        for j in range(m):
            a0 = i * m + j
            a1 = ((i + 1) % n) * m + j
            b0 = i * m + (j + 1) % m
            b1 = ((i + 1) % n) * m + (j + 1) % m
            F.append((a0, b0, b1, a1))
    return np.array(V), F


def oriented_box(center, size, xdir, ydir):
    """Box centred at `center` with local axes x=xdir, y=ydir (z = x cross y)."""
    x = unit(xdir)
    y = unit(np.asarray(ydir, float) - np.dot(ydir, x) * x)
    z = np.cross(x, y)
    V, F = T.box(-np.asarray(size) / 2, np.asarray(size) / 2)
    Vw = np.asarray(center, float) + V[:, :1] * x + V[:, 1:2] * y + V[:, 2:3] * z
    return Vw, F


# ----------------------------------------------------------------------------
FONT = {  # 5x7 bitmap glyphs for the Flipper label
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
}


def flipper_label(path):
    """48x160 label for the 6 x 20 cm face: black, white "FLIPPER" and "FLOAT" rotated 90 deg, reading
    top-to-bottom."""
    img = np.full((48, 160, 3), 0.06)

    def text(t, x0, y0, sc):
        for k, ch in enumerate(t):
            for r in range(7):
                for c in range(5):
                    if FONT[ch][r][c] == "1":
                        x = x0 + k * 6 * sc + c * sc
                        y = y0 + r * sc
                        img[y:y + sc, x:x + sc] = 0.95
    text("FLIPPER", 14, 4, 3)
    text("FLOAT", 46, 31, 2)
    img = np.rot90(img, k=-1)  # clockwise: reads top-to-bottom on the upright cleaner
    for r0 in (4, 150):  # blue brand stripes at both ends
        img[r0:r0 + 4, 6:42] = (0.12, 0.39, 0.84)
    C.write_png(path, (np.clip(img, 0, 1) * 255).astype(np.uint8))
    return path


def slot_tex(path):
    """Filter-cartridge face: dark frame with light slotted media."""
    img = np.full((128, 64, 3), 0.16)
    for r in range(6):
        for c in range(2):
            img[10 + r * 19:10 + r * 19 + 13, 8 + c * 26:8 + c * 26 + 22] = 0.82
    C.write_png(path, (img * 255).astype(np.uint8))
    return path


def tube_path(g, pts, r, sides=8, mat=0, n=None):
    """Closed (capped) tube through the given points (Catmull-Rom resampled)."""
    P_ = np.asarray(pts, float)
    if n:
        Q = [P_[0] - (P_[1] - P_[0])] + list(P_) + [P_[-1] + (P_[-1] - P_[-2])]
        out = []
        for i in range(1, len(Q) - 2):
            p0, p1, p2, p3 = (np.asarray(q) for q in Q[i - 1:i + 3])
            for t in np.linspace(0, 1, n, endpoint=False):
                out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                                  + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
        out.append(P_[-1])
        P_ = np.array(out)
    V, F, t, a = G.tube(P_, r, sides, cap_start=True, cap_end=True, up=(0, 1, 0))
    g.add(V, F, np.c_[a, t], mat=mat)


def ribbed(g, p0, p1, r, rings=12, depth=0.15, sides=12, mat=0):
    """Ribbed cylinder body (12 rings 1.5 mm deep), capped."""
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    n = rings * 2 + 1
    pts = [p0 + (p1 - p0) * k / (n - 1) for k in range(n)]
    rad = [r - depth * (k % 2) for k in range(n)]
    V, F, t, a = G.tube(np.array(pts), rad, sides, cap_start=True, cap_end=True, up=(0, 1, 0))
    g.add(V, F, np.c_[a, t], mat=mat)


def box_add(g, lo, hi, mat=0):
    V, F = T.box(lo, hi)
    g.add(V, F, T.box_uv(V, lo, hi), mat=mat)


def slab(g, x0, x1, y0, y1, z0, z1, r=1.0, bevel=0.2, face="+z", mat=1, face_mat=None, uv_face=None):
    """Rounded-corner slab (corner radius r in x/y, chamfer `bevel` on both end faces) along z, closed. The end cap on
    the `face` side ("+z" or "-z") gets `face_mat` with planar UV over x0..x1 / y0..y1 (the label side)."""
    r = min(r, (x1 - x0) / 2 - 1e-3, (y1 - y0) / 2 - 1e-3)
    pts = []
    for cx, cy, a0 in ((x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180), (x1 - r, y0 + r, 270)):
        for k in range(5):
            a = math.radians(a0 + 90 * k / 4)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    pts = np.array(pts)
    n = len(pts)
    cen = pts.mean(axis=0)

    def ring(z, inset):
        p = cen + (pts - cen) * np.maximum(0.0, 1 - inset / np.maximum(np.linalg.norm(pts - cen, axis=1), 1e-6))[:, None]
        return np.c_[p[:, 0], p[:, 1], np.full(n, z)]

    rings = [ring(z0, bevel), ring(z0 + bevel, 0), ring(z1 - bevel, 0), ring(z1, bevel)]
    V = np.concatenate(rings)
    F = []
    for j in range(3):
        for i in range(n):
            a, b = j * n + i, j * n + (i + 1) % n
            F.append((a, b, b + n, a + n))
    top = tuple(range(3 * n, 4 * n))
    bot = tuple(range(n - 1, -1, -1))
    uv = np.c_[(V[:, 0] - x0) / (x1 - x0), (V[:, 1] - y0) / (y1 - y0)]
    g.add(V, F + [top, bot], uv, mat=mat)
    if face_mat is not None:  # the two cap faces are the last two entries of FM
        g.FM[-2 if face == "+z" else -1] = face_mat


def puck(g, pc, size=12.2, h=3.6, n=24, mat=0):
    """Rounded-square prism (superellipse) centred at pc, closed."""
    ring = []
    for k in range(n):
        a = 2 * math.pi * k / n
        ca, sa = math.cos(a), math.sin(a)
        r = size / 2 / (abs(ca) ** 4 + abs(sa) ** 4) ** 0.25
        ring.append((pc[0] + r * ca, pc[2] + r * sa))
    ring = np.array(ring)
    V = np.concatenate([np.c_[ring[:, 0], np.full(n, pc[1] - h / 2), ring[:, 1]],
                        np.c_[ring[:, 0], np.full(n, pc[1] + h / 2), ring[:, 1]],
                        [[pc[0], pc[1] + h / 2, pc[2]], [pc[0], pc[1] - h / 2, pc[2]]]])
    F = [(k, (k + 1) % n, n + (k + 1) % n, n + k) for k in range(n)]
    F += [(2 * n, n + (k + 1) % n, n + k) for k in range(n)]
    F += [(2 * n + 1, k, (k + 1) % n) for k in range(n)]
    g.add(V, F, mat=mat)


def build():
    C.reset_scene()
    root = C.empty("Root")
    C.set_extras(root, units="cm", spec="docs/specs/tank_design.md §3.2")
    black = C.material("black_plastic", "#111214", rough=0.5)
    grille = C.material("grille", "#2A2C2F", rough=0.6)
    grey = C.material("bracket_grey", "#9CA59A", rough=0.6)
    tube_grey = C.material("tube_grey", "#8A8F94", rough=0.4)
    clear = C.material("clear", "#DDE8E6", rough=0.15, alpha=0.4, blend=True, double=True)
    nozzle_white = C.material("nozzle_white", "#EEEEE8", rough=0.4)
    hob = C.material("hob", "#2A2C30", rough=0.55)
    cap = C.material("cap_grey", "#8C8F93", rough=0.4)
    cable = C.material("cable", "#1A1A1A", rough=0.5)
    puckm = C.material("puck_black", "#141414", rough=0.45)
    foam = C.material("foam", "#151617", rough=0.95)
    liquid = C.material("jar_liquid", "#4D4A2E", rough=0.2, alpha=0.85, blend=True)
    led = C.material("led_red", "#FF2A1A", rough=0.3, emissive="#FF2A1A", emissive_strength=4.0)
    ato_led = C.material("led_ato", "#FF2020", rough=0.3, emissive="#FF2020", emissive_strength=4.0)
    lens = C.material("ai_lens", "#DDE8FF", rough=0.2, emissive="#DDE8FF", emissive_strength=3.0)
    label = C.material("flipper_label", "#FFFFFF", rough=0.45,
                       image=C.load_image(flipper_label(os.path.join(C.BUILD, "flipper_label.png")), name="flipper_label"))
    blue = C.material("flipper_blue", "#1E63D6", rough=0.45)
    felt = C.material("flipper_felt", "#23262A", rough=0.95)
    slots = C.material("cartridge", "#D0D0D0", rough=0.6,
                       image=C.load_image(slot_tex(os.path.join(C.BUILD, "cartridge.png")), name="cartridge"))
    GTH = T.GT
    objs = []

    # Layout from the owner's full-front photo (2026-10-13): AI Prime pucks on goosenecks clamped to the back rim,
    # hang-on-back filter back right (its intake down the right end), skimmer back middle, round powerhead on the back
    # glass upper middle, heater in the back-right corner. Heights are relative to the rim (C.RIM_Y) and water line.
    RIM_Y, WY = C.RIM_Y, C.WATER_Y
    D = T.TANK[2]
    # --- PowerheadLeft: magnet plate on the left glass, ribbed body x 1.5-12.5 along +X, cable up and over the rim
    g = T.WGeo()
    box_add(g, (0.05, 26, 16), (1.5, 30, 20))
    box_add(g, (-GTH - 1.9, 26, 16), (-GTH - 0.02, 30, 20))  # outer magnet half
    ribbed(g, (1.5, 28, 18), (12.2, 28, 18), 3.2)
    V, F, uv = cylinder((12.2, 28, 18), (12.6, 28, 18), 2.6, 16)
    g.add(V, F, uv, mat=1)
    tube_path(g, [(4, 31, 18), (2.2, 34, 17.4), (1.2, RIM_Y - 3.4, 16.6), (1.0, RIM_Y - 0.4, 16.2),
                  (-1.2, RIM_Y + 1.2, 16), (-1.7, RIM_Y - 3.4, 16)], 0.35, 6, mat=2, n=4)
    objs.append(g.obj("PowerheadLeft", root, "equipment", [black, grille, cable],
                      extras={"dir": [1.0, -0.1, 0.1], "jet": [12.6, 28.0, 18.0]}))

    # --- PowerheadBack: round circulation pump on the back glass (77, 32), facing +Z tilted 15 deg down-left
    g = T.WGeo()
    s15 = math.sin(math.radians(15)) / math.sqrt(2)
    d = unit((-s15, -s15, math.cos(math.radians(15))))
    base = np.array([77.0, 32.0, 0.05])
    V, F, uv = cylinder(base, base + d * 4.5, 4.0, 24)
    g.add(V, F, uv)
    face = base + d * 4.5
    V, F, uv = cylinder(face - d * 0.05, face + d * 0.25, 3.3, 24)
    g.add(V, F, uv, mat=1)
    side = unit(np.cross(d, (0, 1, 0)))
    up2 = np.cross(side, d)
    for k in range(12):  # 12 radial slots: raised bars between them
        a = 2 * math.pi * k / 12
        rdir = math.cos(a) * side + math.sin(a) * up2
        V, F = oriented_box(face + d * 0.3 + rdir * 1.9, (2.6, 0.35, 0.2), rdir, np.cross(d, rdir))
        g.add(V, F)
    box_add(g, (75.0, 30, -GTH - 2.0), (79.0, 34, -GTH - 0.02))  # outer magnet
    tube_path(g, [(76.1, 35.5, 2.0), (76.3, 37.0, 0.6), (76.5, RIM_Y - 0.8, 0.5), (76.5, RIM_Y + 1.0, -1.6),
                  (76.5, RIM_Y - 3.4, -2.4)], 0.35, 6, mat=2, n=4)
    objs.append(g.obj("PowerheadBack", root, "equipment", [black, grille, cable],
                      extras={"dir": [-0.3, -0.3, 0.9], "jet": [round(float(v), 2) for v in face]}))

    # --- PowerheadRight: plate on the right glass, ribbed body x 111-120.5 along -X with a fat foam sleeve
    g = T.WGeo()
    L = T.TANK[0]
    box_add(g, (L - 1.5, 26, 11), (L - 0.05, 30, 15))
    box_add(g, (L + GTH + 0.02, 26, 11), (L + GTH + 1.9, 30, 15))
    ribbed(g, (120.4, 28, 13), (111.0, 28, 13), 3.2)
    V, F, uv = cylinder((111.2, 28, 13), (110.8, 28, 13), 2.6, 16)
    g.add(V, F, uv, mat=1)
    ribbed(g, (120.3, 28, 13), (114.3, 28, 13), 3.8, rings=6, depth=0.25, sides=12, mat=3)
    tube_path(g, [(117, 31.5, 13), (119.8, 34.5, 13.4), (120.9, RIM_Y - 0.9, 13.6), (123.2, RIM_Y + 1.2, 13.6),
                  (123.7, RIM_Y - 3.4, 13.6)], 0.35, 6, mat=2, n=4)
    objs.append(g.obj("PowerheadRight", root, "equipment", [black, grille, cable, foam],
                      extras={"dir": [-1.0, -0.1, 0.2], "jet": [110.8, 28.0, 13.0]}))

    # --- Heater: vertical on the back glass at x 119.5, z 2.5, y 8-34; grey cap with a red LED; 2 suction cups
    g = T.WGeo()
    hx, hz = 119.5, 2.5
    V, F, uv = cylinder((hx, 8, hz), (hx, 31, hz), 1.4, 16)
    g.add(V, F, uv)
    V, F, uv = cylinder((hx, 31, hz), (hx, 34, hz), 1.45, 16)
    g.add(V, F, uv, mat=1)
    for y in (12.0, 27.0):
        V, F, uv = cylinder((hx, y, 0.05), (hx, y, 1.3), 1.3, 12, r1=0.6)
        g.add(V, F, uv, mat=2)
    tube_path(g, [(hx, 34, hz), (hx, 36.5, 1.6), (hx + 0.3, RIM_Y - 0.8, 0.6), (hx + 0.3, RIM_Y + 1.0, -1.6),
                  (hx + 0.3, RIM_Y - 3.4, -2.4)], 0.3, 6, mat=0, n=4)
    objs.append(g.obj("Heater", root, "equipment", [black, cap, clear], smooth=True))
    g = T.WGeo()
    box_add(g, (hx - 0.1, 32.9, hz + 1.42), (hx + 0.1, 33.1, hz + 1.55))
    objs.append(g.obj("HeaterLED", root, "light", [led]))

    # --- IntakeTube: the HOB's grey intake down the right end with a black strainer, elbow over the back rim into
    # the HobFilter body
    g = T.WGeo()
    ix = 114.5
    tube_path(g, [(ix, 22.5, 5), (ix, 30, 5), (ix, RIM_Y + 0.1, 5), (ix, RIM_Y + 1.6, 3.6), (ix, RIM_Y + 2.0, 0),
                  (ix, RIM_Y + 1.6, -3.6), (ix, RIM_Y - 2.4, -4.2)], 1.3, 10, mat=0, n=3)
    V, F, uv = cylinder((ix, 15, 5), (ix, 23, 5), 2.6, 16)
    g.add(V, F, uv, mat=1)
    objs.append(g.obj("IntakeTube", root, "equipment", [tube_grey, black], smooth=True))

    # --- Flipper Float: a magnetic scraper in two halves. The outer half (label side, a soft-edged shell with a pull
    # tab) sits flush on the outside of the pane; the inner half is a rounded body with a continuous felt pad that
    # touches the glass, a blue scrubber rail and a grip bar on its water side. Centre (5, 23) never changes (the
    # renderer slides it from there); the outer half is a separate shell on the far face of the glass.
    g = T.WGeo()
    FX0, FX1 = 2.0, 8.0
    FCX = (FX0 + FX1) / 2
    slab(g, FX0, FX1, 13, 33, D + GTH + 0.1, D + GTH + 1.4, r=1.1, bevel=0.22, face="+z", mat=1, face_mat=0)
    slab(g, FCX - 1.5, FCX + 1.5, 33.0, 34.4, D + GTH + 0.1, D + GTH + 0.8, r=0.5, bevel=0.15, mat=2)  # pull tab
    slab(g, FX0, FX1, 13, 33, D - 1.5, D - 0.2, r=1.1, bevel=0.22, face="-z", mat=1, face_mat=1)
    slab(g, FX0 + 0.55, FX1 - 0.55, 13.55, 32.45, D - 0.2, D, r=0.7, bevel=0.05, mat=3)  # felt pad on the glass
    slab(g, FCX - 0.8, FCX + 0.8, 15, 31, D - 1.95, D - 1.5, r=0.7, bevel=0.15, mat=2)  # grip bar on the water side
    slab(g, FX0 + 0.3, FX0 + 0.8, 14, 32, D - 1.6, D - 1.45, r=0.2, bevel=0.05, mat=2)  # scrubber rails
    slab(g, FX1 - 0.8, FX1 - 0.3, 14, 32, D - 1.6, D - 1.45, r=0.2, bevel=0.05, mat=2)
    objs.append(g.obj("Flipper", root, "equipment", [label, grille, blue, felt], smooth=True))

    # --- FrontMagnet: black cylinder on the sand facing the viewer + grey-green bracket to its left
    g = T.WGeo()
    MX = 70.0
    V, F, uv = cylinder((MX, 6.5, D - 5.5), (MX, 6.5, D - 1.0), 4.2, 24)
    g.add(V, F, uv)
    V, F, uv = cylinder((MX, 6.5, D - 1.05), (MX, 6.5, D - 0.9), 3.4, 24)
    g.add(V, F, uv, mat=1)
    objs.append(g.obj("FrontMagnet", root, "equipment", [black, grille]))
    g = T.WGeo()
    V, F = oriented_box((MX - 4.4, 5.0, D - 2.0), (8.0, 2.0, 3.8), (1, 0.12, 0), (-0.12, 1, 0))
    g.add(V, F)
    V, F = oriented_box((MX - 8.1, 6.4, D - 2.0), (1.2, 4.0, 3.8), (1, 0.4, 0), (-0.4, 1, 0))
    g.add(V, F)
    objs.append(g.obj("FrontMagnetBracket", root, "equipment", [grey]))

    # --- HoseArc: the left powerhead's clear air/feed line. It leaves a hose barb on the top of the powerhead, rises in
    # a gentle curve and runs along the back glass (held by a small suction clip), then crosses the back rim and drops
    # outside the tank like the other cables, so it touches nothing but the powerhead, the clip and the rim.
    g = T.WGeo()
    V, F, uv = cylinder((9.0, 30.9, 18.0), (9.0, 31.9, 18.0), 0.55, 10)  # hose barb on the powerhead body
    g.add(V, F, uv, mat=1)
    tube_path(g, [(9.0, 31.4, 18.0), (9.0, 33.4, 17.6), (8.2, 35.4, 14.0), (7.0, 36.6, 9.0), (6.0, 37.0, 4.0),
                  (5.6, 37.4, 1.3), (5.5, RIM_Y - 0.9, 0.7), (5.5, RIM_Y + 1.1, -1.5), (5.5, RIM_Y - 3.4, -2.4)],
              0.5, 10, n=6)
    box_add(g, (4.6, 36.2, 0.05), (6.4, 38.4, 1.25), mat=1)  # suction clip on the back glass
    objs.append(g.obj("HoseArc", root, "equipment", [clear, black], smooth=True))

    # --- CableLeft: the powerhead's controller lead, down the left glass along the sand, up the back-left corner and
    # over the rim (it starts at the powerhead's magnet plate, not at the Flipper, which slides free of it)
    g = T.WGeo()
    tube_path(g, [(1.0, 26.0, 19.6), (0.9, 18.0, 19.6), (0.8, 10.0, 19.0), (0.7, 7.8, 14.0), (0.8, 8.0, 8.0),
                  (0.8, 8, 1.2), (0.6, 22, 0.6), (0.6, RIM_Y - 0.8, 0.6), (0.6, RIM_Y + 1.1, -1.6),
                  (0.6, RIM_Y - 3.4, -2.4)], 0.35, 6, n=4)
    objs.append(g.obj("CableLeft", root, "equipment", [cable], smooth=True))

    # Back rim (tank.py rim_geos): skirt outer face z -1.035 from RIM_Y - 3.8 to the lip top RIM_Y; the lip runs
    # inward to z 1.765 with its underside at RIM_Y - 0.5; below the skirt the back glass outer face is at z -0.635.
    LIP_UND = RIM_Y - 0.5
    SKIRT_Z, GLASS_Z = -GTH - 0.4, -GTH

    def bumper(g, x, y, mat):
        """Rubber leveler pad bridging the skirt-to-glass step so a hang-on body touches the glass."""
        V, F, uv = cylinder((x, y, SKIRT_Z - 0.02), (x, y, GLASS_Z), 0.8, 10)
        g.add(V, F, uv, mat=mat)

    # --- HobFilter hung on the back rim at the right end: body flush against the skirt (rubber levelers on the glass
    # below it), lid, two slotted cartridges; its intake is IntakeTube (x 114.5). The water returns through
    # ReturnNozzle at the back middle (video T97: a white ring nozzle on a clear pipe clipped to the rim; the photo's
    # pipes behind the rim at x ~73-77), where Tee hand-feeds.
    g = T.WGeo()
    HX0, HX1 = 98.5, 118.4
    RX = 73.0                                                               # return outlet x (core HOB_OUTFLOW)
    box_add(g, (HX0, RIM_Y - 13.4, -11), (HX1, RIM_Y + 4.6, SKIRT_Z))
    box_add(g, (HX0 - 0.4, RIM_Y + 4.6, -11.4), (HX1 + 0.4, RIM_Y + 5.6, SKIRT_Z))
    for cx in (104.5, 112.5):
        box_add(g, (cx - 3.5, RIM_Y + 5.6, -7.75), (cx + 3.5, RIM_Y + 19.6, -6.25), mat=2)
    for bx in (HX0 + 2.5, HX1 - 2.5):
        bumper(g, bx, RIM_Y - 10.4, 1)
    # the filter sock's opening in the lid (the sock's white collar, care_tools.glb SockRing, rests on it)
    V, F, uv = cylinder((HOB_SOCK[0], RIM_Y + 5.6, HOB_SOCK[2]), (HOB_SOCK[0], RIM_Y + 5.7, HOB_SOCK[2]), 2.3, 20)
    g.add(V, F, uv, mat=1)
    outflow = [RX, round(WY - 0.7, 2), 2.3]
    objs.append(g.obj("HobFilter", root, "equipment", [hob, black, slots]))

    # --- ReturnPipe + ReturnNozzle: clear pipe from the HobFilter along the back of the rim to x RX, over the lip and
    # down into the water, ending in a white ring nozzle aimed forward and a little down (0, -0.4, 0.92) 0.7 cm under the surface
    g = T.WGeo()
    tube_path(g, [(HX0 + 0.5, RIM_Y - 6.0, -6.0), (HX0 - 3.0, RIM_Y + 1.8, -2.6), (RX + 6.0, RIM_Y + 2.0, -2.2),
                  (RX + 1.0, RIM_Y + 2.0, -1.2), (RX, RIM_Y + 1.9, 0.6), (RX, RIM_Y + 0.6, 2.3), (RX, WY - 0.2, 2.3)],
              0.7, 10, n=4)
    box_add(g, (RX - 1.4, RIM_Y, -1.6), (RX + 1.4, RIM_Y + 0.5, 2.4))         # rim clip
    objs.append(g.obj("ReturnPipe", root, "equipment", [clear], smooth=True))
    g = T.WGeo()
    nd = np.array([0.0, -0.4, 0.92])
    n0 = np.array([RX, WY - 0.3, 2.3])
    V, F, uv = cylinder(tuple(n0), tuple(n0 + nd * 1.6), 1.0, 14, r1=1.15)
    g.add(V, F, uv)
    objs.append(g.obj("ReturnNozzle", root, "equipment", [nozzle_white], smooth=True,
                      extras={"outflow": outflow, "dir": nd.tolist()}))

    # --- Skimmer at the back middle, hung on the back rim: reaction body flush against the skirt with levelers on the
    # glass, pump block bolted under it, lid with two tall grey riser tubes, hanger saddle over the lip with a short
    # intake leg dropping into the water (clear of the R4 shelf top)
    g = T.WGeo()
    KX = 58.0
    box_add(g, (KX, RIM_Y - 15.4, -9), (KX + 10, RIM_Y + 8.6, SKIRT_Z))
    box_add(g, (KX - 0.4, RIM_Y + 8.6, -9.4), (KX + 10.4, RIM_Y + 9.6, SKIRT_Z))
    box_add(g, (KX + 1, RIM_Y - 21.4, -8), (KX + 9, RIM_Y - 15.4, -2.2), mat=1)
    box_add(g, (KX + 1.5, RIM_Y, SKIRT_Z), (KX + 8.5, RIM_Y + 0.8, 2.6), mat=1)    # hanger saddle on the lip
    box_add(g, (KX + 1.6, RIM_Y - 4.4, 2.0), (KX + 8.4, RIM_Y, 2.6), mat=1)        # saddle front leg inside the glass
    V, F, uv = cylinder((KX + 5, RIM_Y - 6.0, 3.4), (KX + 5, RIM_Y - 3.4, 3.4), 1.0, 10)
    g.add(V, F, uv, mat=1)                                                     # intake leg into the water
    for bx in (KX + 2, KX + 8):
        bumper(g, bx, RIM_Y - 12.4, 1)
    # the collection cup's seat on the lid: a low black collar the clear cup (care_tools.glb Cup, drawn and lifted
    # off by the renderer's CareScene at SKIMMER_CUP) stands in, and the grey air-line spigot at the back
    cx, cy, cz = SKIMMER_CUP
    V, F, uv = cylinder((cx, RIM_Y + 9.6, cz), (cx, cy, cz), 3.75, 24)
    g.add(V, F, uv, mat=1)
    V, F, uv = cylinder((KX + 8.6, RIM_Y + 9.6, -8.2), (KX + 8.6, RIM_Y + 12.6, -8.2), 0.6, 10)
    g.add(V, F, uv, mat=2)
    objs.append(g.obj("Skimmer", root, "equipment", [hob, black, tube_grey]))

    # --- AtoBox on the front rim (left) with its red LED
    g = T.WGeo()
    box_add(g, (16, RIM_Y, D - 5), (22, RIM_Y + 2.0, D))
    objs.append(g.obj("AtoBox", root, "equipment", [black]))
    g = T.WGeo()
    box_add(g, (18.85, RIM_Y + 2.0, D - 3.15), (19.15, RIM_Y + 2.15, D - 2.85))
    objs.append(g.obj("AtoLED", root, "light", [ato_led]))

    # --- Jar on the back rim (optional prop), between the AiPrimeL clamp and the skimmer
    g = T.WGeo()
    V, F, uv = cylinder((48.0, RIM_Y + 0.05, 0), (48.0, RIM_Y + 9.55, 0), 4.0, 20)
    g.add(V, F, uv)
    V, F, uv = cylinder((48.0, RIM_Y + 0.2, 0), (48.0, RIM_Y + 6.2, 0), 3.75, 20)
    g.add(V, F, uv, mat=1)
    objs.append(g.obj("Jar", root, "equipment", [clear, liquid]))

    # --- AI Prime HD x2: puck (lens at LIGHT_Y, LightingState.LIGHT_POS), heat-sink, gooseneck rising from behind the
    # tank out of a C-clamp gripping the back rim (top plate on the lip, back leg down the skirt with a thumb screw,
    # front jaw under the lip with a thumb knob), power cable running down the arm and behind the tank; lens + LED
    # dots emitter. Pucks ~30 cm over the open top (photo: 25-35 cm), a little behind the tank's centre line.
    PY = RIM_Y + 30.6   # puck centre; bottom face PY - 1.8, lens plate PY - 1.9..PY - 1.8, LED dots from PY - 2.05
    PZ = LIGHT_Z
    for side, x, cx in (("L", 32.0, 38.0), ("R", 88.0, 82.0)):
        g = T.WGeo()
        pc = np.array([x, PY, PZ])
        puck(g, pc)
        box_add(g, (x - 4, PY + 1.8, PZ - 4), (x + 4, PY + 2.8, PZ + 4))      # heat-sink
        box_add(g, (x - 1.3, PY - 1.0, PZ - 7.0), (x + 1.3, PY + 1.0, PZ - 5.4))  # arm socket on the puck's rear edge
        V, F, t, a = G.tube(G.bezier((cx, RIM_Y + 0.9, 0.2), (cx, RIM_Y + 22.6, 0.2), (x, PY, 1.0), (x, PY, PZ - 6.6), 16),
                            0.8, 8, cap_start=True, cap_end=True, up=(1, 0, 0))
        g.add(V, F, np.c_[a, t])
        V, F, uv = cylinder((cx, RIM_Y + 0.9, 0.2), (cx, RIM_Y + 2.6, 0.2), 1.2, 12)
        g.add(V, F, uv)                                                        # arm base collar
        # C-clamp around the back rim, touching the lip top, skirt and lip underside
        box_add(g, (cx - 1.9, RIM_Y, -1.9), (cx + 1.9, RIM_Y + 0.9, 2.6))      # top plate on the lip
        box_add(g, (cx - 1.85, RIM_Y - 4.4, -1.9), (cx + 1.85, RIM_Y, SKIRT_Z))  # back leg on the skirt
        box_add(g, (cx - 1.85, RIM_Y - 1.8, 2.0), (cx + 1.85, RIM_Y, 2.6))     # front leg inside the tank
        box_add(g, (cx - 1.8, RIM_Y - 1.8, 0.2), (cx + 1.8, RIM_Y - 1.2, 2.0))  # jaw foot under the lip
        V, F, uv = cylinder((cx, RIM_Y - 1.2, 1.1), (cx, LIP_UND, 1.1), 0.3, 8)
        g.add(V, F, uv, mat=1)                                                 # jaw screw pressing the lip
        V, F, uv = cylinder((cx, RIM_Y - 2.8, 1.1), (cx, RIM_Y - 1.8, 1.1), 0.9, 12)
        g.add(V, F, uv, mat=1)                                                 # jaw thumb knob
        V, F, uv = cylinder((cx, RIM_Y - 3.2, -1.9), (cx, RIM_Y - 3.2, -3.0), 0.3, 8)
        g.add(V, F, uv, mat=1)                                                 # back thumb screw
        V, F, uv = cylinder((cx, RIM_Y - 3.2, -3.0), (cx, RIM_Y - 3.2, -3.7), 0.9, 12)
        g.add(V, F, uv, mat=1)                                                 # back thumb knob
        # power cable: out of the puck's rear, along the arm, behind the clamp and down the back of the tank
        tube_path(g, [(x + 1.6, PY + 1.0, PZ - 5.8), (x + 1.9, PY + 0.6, PZ - 11), (cx + 1.6, RIM_Y + 20.6, 1.4),
                      (cx + 2.3, RIM_Y + 8.6, 0.4), (cx + 2.3, RIM_Y + 1.8, -1.8), (cx + 2.3, RIM_Y - 3.4, -2.6),
                      (cx + 2.3, RIM_Y - 15.4, -2.6)], 0.3, 6, mat=2, n=4)
        box_add(g, (x - 4, PY - 1.9, PZ - 4), (x + 4, PY - 1.8, PZ + 4), mat=1)  # white lens plate
        objs.append(g.obj(f"AiPrime{side}", root, "equipment", [puckm, cap, cable]))
        g = T.WGeo()
        for i in range(4):
            for j in range(4):
                lx, lz = x - 2.25 + 1.5 * i, PZ - 2.25 + 1.5 * j
                box_add(g, (lx - 0.125, PY - 2.05, lz - 0.125), (lx + 0.125, PY - 1.9, lz + 0.125))
        objs.append(g.obj(f"AiPrime{side}_Lens", root, "emitter", [lens],
                          extras={"lightPos": [x, round(PY - 2.0, 2), PZ], "coneDeg": 110}))

    bad = {}
    for o in objs:
        C.recalc_normals(o)
        nm = T.non_manifold(o)
        if nm:
            bad[o.name] = nm
        C.triangulate(o)
    print("[equip] non-manifold edges:", bad or 0)
    path = os.path.join(C.MODELS, "equipment.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[equip] equipment.glb: {tris} tris, {os.path.getsize(path) / 1024:.0f} KB")
    return tris


if __name__ == "__main__":
    build()
