"""Equipment props (docs/specs/tank_design.md §3.2): equipment.glb, world coordinates (cm), every mesh closed.

Nodes (direct children of Root, extras.part = "equipment" unless noted):
  PowerheadLeft, PowerheadBack, PowerheadRight  (extras.dir = jet direction, extras.jet = nozzle point)
  Heater, HeaterLED ("light"), IntakeTube, Flipper (inside + outside halves, label texture),
  FrontMagnet, FrontMagnetBracket, HoseArc, CableLeft, FeedRing, HobFilter (extras.outflow), Skimmer,
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
    ringm = C.material("ring_clear", "#E6EEF0", rough=0.15, alpha=0.5, blend=True, double=True)
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
    slots = C.material("cartridge", "#D0D0D0", rough=0.6,
                       image=C.load_image(slot_tex(os.path.join(C.BUILD, "cartridge.png")), name="cartridge"))
    GTH = T.GT
    objs = []

    # --- PowerheadLeft: magnet plate on the left glass, ribbed body x 1.5-12.5 along +X, cable up and over the rim
    g = T.WGeo()
    box_add(g, (0.05, 42, 12), (1.5, 46, 16))
    box_add(g, (-GTH - 1.9, 42, 12), (-GTH - 0.02, 46, 16))  # outer magnet half
    ribbed(g, (1.5, 44, 14), (12.2, 44, 14), 3.2)
    V, F, uv = cylinder((12.2, 44, 14), (12.6, 44, 14), 2.6, 16)
    g.add(V, F, uv, mat=1)
    tube_path(g, [(4, 47, 14), (2.2, 52, 13.4), (1.2, 58, 12.6), (1.0, 61.0, 12.2), (-1.2, 62.6, 12), (-1.7, 58, 12)],
              0.35, 6, mat=2, n=4)
    objs.append(g.obj("PowerheadLeft", root, "equipment", [black, grille, cable],
                      extras={"dir": [1.0, -0.1, 0.1], "jet": [12.6, 44.0, 14.0]}))

    # --- PowerheadBack: round circulation pump on the back glass (85.5, 47), facing +Z tilted 15 deg down-left
    g = T.WGeo()
    s15 = math.sin(math.radians(15)) / math.sqrt(2)
    d = unit((-s15, -s15, math.cos(math.radians(15))))
    base = np.array([85.5, 47.0, 0.05])
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
    box_add(g, (83.5, 45, -GTH - 2.0), (87.5, 49, -GTH - 0.02))  # outer magnet
    tube_path(g, [(84.6, 50.5, 2.0), (84.1, 54, 0.6), (84, 60.6, 0.5), (84, 62.4, -1.6), (84, 58, -2.4)], 0.35, 6,
              mat=2, n=4)
    objs.append(g.obj("PowerheadBack", root, "equipment", [black, grille, cable],
                      extras={"dir": [-0.3, -0.3, 0.9], "jet": [round(float(v), 2) for v in face]}))

    # --- PowerheadRight: plate on the right glass, ribbed body x 111-120.5 along -X with a fat foam sleeve
    g = T.WGeo()
    L = T.TANK[0]
    box_add(g, (L - 1.5, 42, 8), (L - 0.05, 46, 12))
    box_add(g, (L + GTH + 0.02, 42, 8), (L + GTH + 1.9, 46, 12))
    ribbed(g, (120.4, 44, 10), (111.0, 44, 10), 3.2)
    V, F, uv = cylinder((111.2, 44, 10), (110.8, 44, 10), 2.6, 16)
    g.add(V, F, uv, mat=1)
    ribbed(g, (120.3, 44, 10), (114.3, 44, 10), 3.8, rings=6, depth=0.25, sides=12, mat=3)
    tube_path(g, [(117, 47.5, 10), (119.8, 52, 10.4), (120.9, 60.5, 10.6), (123.2, 62.6, 10.6), (123.7, 58, 10.6)],
              0.35, 6, mat=2, n=4)
    objs.append(g.obj("PowerheadRight", root, "equipment", [black, grille, cable, foam],
                      extras={"dir": [-1.0, -0.1, 0.2], "jet": [110.8, 44.0, 10.0]}))

    # --- Heater: vertical on the back glass at x 119.5, z 2.5, y 21-52; grey cap with a red LED; 2 suction cups
    g = T.WGeo()
    hx, hz = 119.5, 2.5
    V, F, uv = cylinder((hx, 21, hz), (hx, 49, hz), 1.4, 16)
    g.add(V, F, uv)
    V, F, uv = cylinder((hx, 49, hz), (hx, 52, hz), 1.45, 16)
    g.add(V, F, uv, mat=1)
    for y in (26.0, 46.0):
        V, F, uv = cylinder((hx, y, 0.05), (hx, y, 1.3), 1.3, 12, r1=0.6)
        g.add(V, F, uv, mat=2)
    tube_path(g, [(hx, 52, hz), (hx, 56, 1.6), (hx - 0.5, 60.6, 0.6), (hx - 0.5, 62.4, -1.6), (hx - 0.5, 58, -2.4)],
              0.3, 6, mat=0, n=4)
    objs.append(g.obj("Heater", root, "equipment", [black, cap, clear], smooth=True))
    g = T.WGeo()
    box_add(g, (hx - 0.1, 50.9, hz + 1.42), (hx + 0.1, 51.1, hz + 1.55))
    objs.append(g.obj("HeaterLED", root, "light", [led]))

    # --- IntakeTube: grey tube down the right end with a black strainer, elbow over the back rim
    g = T.WGeo()
    tube_path(g, [(116.5, 35.5, 5), (116.5, 50, 5), (116.5, 61.5, 5), (116.5, 63.0, 3.6), (116.5, 63.4, 0),
                  (116.5, 63.0, -3.6), (116.5, 59, -4.2)], 1.3, 10, mat=0, n=3)
    V, F, uv = cylinder((116.5, 28, 5), (116.5, 36, 5), 2.6, 16)
    g.add(V, F, uv, mat=1)
    objs.append(g.obj("IntakeTube", root, "equipment", [tube_grey, black], smooth=True))

    # --- Flipper: inner half on the front glass inner face + outer half with the label, 4 felt pads
    g = T.WGeo()
    D = T.TANK[2]
    box_add(g, (1, 13, 31.0), (7, 33, 32.85))
    box_add(g, (1, 13, D + GTH + 0.15), (7, 33, 36))
    for px in (1.6, 5.4):
        for py in (14, 31):
            box_add(g, (px, py, 32.85), (px + 1.0, py + 1.0, 33.0), mat=1)
            box_add(g, (px, py, D + GTH), (px + 1.0, py + 1.0, D + GTH + 0.15), mat=1)
    objs.append(g.obj("Flipper", root, "equipment", [label, grille]))

    # --- FrontMagnet: black cylinder on the sand facing the viewer + grey-green bracket to its left
    g = T.WGeo()
    V, F, uv = cylinder((72, 6.5, 27.5), (72, 6.5, 32.0), 4.2, 24)
    g.add(V, F, uv)
    V, F, uv = cylinder((72, 6.5, 31.95), (72, 6.5, 32.1), 3.4, 24)
    g.add(V, F, uv, mat=1)
    objs.append(g.obj("FrontMagnet", root, "equipment", [black, grille]))
    g = T.WGeo()
    V, F = oriented_box((67.6, 5.0, 31.0), (8.0, 2.0, 3.8), (1, 0.12, 0), (-0.12, 1, 0))
    g.add(V, F)
    V, F = oriented_box((63.9, 6.4, 31.0), (1.2, 4.0, 3.8), (1, 0.4, 0), (-0.4, 1, 0))
    g.add(V, F)
    objs.append(g.obj("FrontMagnetBracket", root, "equipment", [grey]))

    # --- HoseArc: clear tube through the four spec points, capped
    g = T.WGeo()
    tube_path(g, [(8, 36, 16), (13, 48, 14), (22, 54, 12), (30, 50, 10)], 0.8, 10, n=7)
    objs.append(g.obj("HoseArc", root, "equipment", [clear], smooth=True))

    # --- CableLeft: from the Flipper down the left glass along the sand, up the back-left corner and over the rim
    g = T.WGeo()
    tube_path(g, [(3.5, 13, 31.5), (1.6, 8.5, 29), (1.0, 5.4, 25), (2, 5.0, 20), (1.0, 6.8, 8), (0.8, 8, 1.2),
                  (0.6, 30, 0.6), (0.6, 60.6, 0.6), (0.6, 62.5, -1.6), (0.6, 58, -2.4)], 0.35, 6, n=4)
    objs.append(g.obj("CableLeft", root, "equipment", [cable], smooth=True))

    # --- FeedRing floating at the surface
    g = T.WGeo()
    V, F = torus((41, 56.2, 18), 4.5, 0.35, 24, 6)
    g.add(V, F)
    objs.append(g.obj("FeedRing", root, "equipment", [ringm], smooth=True))

    # --- HobFilter on the back rim: body + lid, two slotted cartridges, intake tube, outflow spout
    g = T.WGeo()
    box_add(g, (60, 58, -12), (84, 74, -1.0))
    box_add(g, (59.6, 74, -12.4), (84.4, 75, -0.6), mat=1)
    for cx in (66, 76):
        box_add(g, (cx - 3.5, 75, -7.75), (cx + 3.5, 89, -6.25), mat=2)
    tube_path(g, [(82, 40, 2), (82, 60, 2), (82, 62.6, 1.6), (82, 63.6, 0), (82, 63.4, -1.6)], 1.3, 10, n=3)
    box_add(g, (65, 57.5, -1.0), (79, 58.5, 2.0))
    objs.append(g.obj("HobFilter", root, "equipment", [hob, black, slots],
                      extras={"outflow": [72.0, 55.0, 1.0]}))

    # --- Skimmer right of the HOB: body, black pump block, two tall grey tubes
    g = T.WGeo()
    box_add(g, (86, 58, -10), (96, 72, -1.0))
    box_add(g, (87, 52, -9), (95, 58, -2), mat=1)
    for tx in (89, 93):
        V, F, uv = cylinder((tx, 62, -6), (tx, 92, -6), 1.6, 12)
        g.add(V, F, uv, mat=2)
    objs.append(g.obj("Skimmer", root, "equipment", [hob, black, tube_grey]))

    # --- AtoBox on the top rim (left) with its red LED
    g = T.WGeo()
    box_add(g, (16, 61.4, 28), (22, 63.4, 33))
    objs.append(g.obj("AtoBox", root, "equipment", [black]))
    g = T.WGeo()
    box_add(g, (18.85, 63.4, 29.85), (19.15, 63.55, 30.15))
    objs.append(g.obj("AtoLED", root, "light", [ato_led]))

    # --- Jar on the right rim (optional prop)
    g = T.WGeo()
    V, F, uv = cylinder((104, 61.45, 0), (104, 70.95, 0), 4.0, 20)
    g.add(V, F, uv)
    V, F, uv = cylinder((104, 61.6, 0), (104, 67.6, 0), 3.75, 20)
    g.add(V, F, uv, mat=1)
    objs.append(g.obj("Jar", root, "equipment", [clear, liquid]))

    # --- AI Prime HD x2: puck, heat-sink, gooseneck from a back-rim clamp, power cable; lens + LED dots emitter
    for side, x in (("L", 32.0), ("R", 91.0)):
        g = T.WGeo()
        pc = np.array([x, 101.0, 16.0])
        puck(g, pc)
        box_add(g, (x - 4, 102.8, 12), (x + 4, 103.8, 20))
        V, F, t, a = G.tube(G.bezier((x + 6, 64, -3), (x + 6, 88, -4), (x + 2, 100, 8), (x, 101, 12), 16), 0.9, 8,
                            cap_start=True, cap_end=True, up=(1, 0, 0))
        g.add(V, F, np.c_[a, t])
        box_add(g, (x + 4, 60, -5), (x + 8, 64, -2))
        tube_path(g, [(x, 103.2, 12), (x, 110, 5), (x + 2, 120, -5.9)], 0.3, 6, n=5)
        box_add(g, (x - 4, 99.1, 12), (x + 4, 99.2, 20), mat=1)  # white lens plate
        objs.append(g.obj(f"AiPrime{side}", root, "equipment", [puckm, cap]))
        g = T.WGeo()
        for i in range(4):
            for j in range(4):
                cx, cz = x - 2.25 + 1.5 * i, 16 - 2.25 + 1.5 * j
                box_add(g, (cx - 0.125, 98.95, cz - 0.125), (cx + 0.125, 99.1, cz + 0.125))
        objs.append(g.obj(f"AiPrime{side}_Lens", root, "emitter", [lens],
                          extras={"lightPos": [x, 99.0, 16.0], "coneDeg": 110}))

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
