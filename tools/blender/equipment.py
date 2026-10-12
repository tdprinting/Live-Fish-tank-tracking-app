"""Equipment props (ASSETS.md §5): equipment.glb, world coordinates (cm).

Nodes (each a direct child of Root, extras.part = "equipment" unless noted):
  PowerheadA, PowerheadB        (extras.dir = jet direction, extras.jet = nozzle position)
  FrontPump, FrontPumpBracket
  Heater, HeaterLED             (HeaterLED part "light": fixed emissive red)
  Flipper                       (label texture)
  HoseArc, FeedRing             (clear material)
  HobBody                       (extras.outflow = HOB_OUTFLOW point)
  AiPrimeL, AiPrimeR            (puck + gooseneck) and AiPrimeL_Lens/AiPrimeR_Lens (part "emitter": the runtime
                                 sets their emissive to the current light color)
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
}


def flipper_label(path):
    """64x128 label: black body, white 'FLIPPER' running bottom-to-top (as printed on the cleaner)."""
    w, h = 64, 128
    img = np.full((h, w, 3), 0.06)
    text = "FLIPPER"
    s = 2  # pixel scale
    gw = 6 * s
    y0 = (h - len(text) * gw) // 2
    for k, ch in enumerate(text):
        g = FONT[ch]
        for r in range(7):
            for c in range(5):
                if g[r][c] == "1":
                    # rotate 90 deg: text reads upward
                    px = 24 + r * s
                    py = h - (y0 + k * gw + c * s) - s
                    img[py:py + s, px:px + s] = 0.95
    # thin frame + rounded logo dot
    img[6:9, 8:56] = 0.6
    img = np.clip(img, 0, 1)
    C.write_png(path, (img * 255).astype(np.uint8))
    return path


# ----------------------------------------------------------------------------
def powerhead(name, root, mount, normal, jet_dir, mats, length=5.0, r=3.0):
    """Magnet block on the glass (normal = into the water), pump body pointing along jet_dir."""
    n = unit(normal)
    d = unit(jet_dir)
    g = T.WGeo()
    mount = np.asarray(mount, float)
    up = np.array([0, 1.0, 0]) if abs(n[1]) < 0.9 else np.array([1.0, 0, 0])
    V, F = oriented_box(mount + n * 1.0, (4, 4, 2), np.cross(up, n), up)
    g.add(V, F, mat=0)
    # stalk from the block to the body
    a = mount + n * 2.0
    b = a + n * 1.5 + d * 0.5
    V, F, uv = cylinder(a, b, 1.0, 8)
    g.add(V, F, uv, mat=0)
    # body: cylinder along d, rear end near the stalk
    c0 = b - d * length * 0.45
    c1 = b + d * length * 0.55
    V, F, uv = cylinder(c0, c1, r, 20)
    g.add(V, F, uv, mat=0)
    # front grille (slightly proud, dark grey) and the intake ring
    V, F, uv = disc(c1 + d * 0.05, d, r * 0.82, 20)
    g.add(V, F, uv, mat=1)
    V, F, uv = cylinder(c1 - d * 0.6, c1 + d * 0.3, r * 1.04, 20, caps=False)
    g.add(V, F, uv, mat=0)
    o = g.obj(name, root, "equipment", mats, smooth=False,
              extras={"dir": [round(float(x), 3) for x in d], "jet": [round(float(x), 2) for x in c1]})
    return o


def build():
    C.reset_scene()
    root = C.empty("Root")
    C.set_extras(root, units="cm")
    black = C.material("black_plastic", "#111214", rough=0.5)
    grille = C.material("grille", "#2A2C2F", rough=0.6)
    grey = C.material("bracket_grey", "#9FA8AD", rough=0.6)
    clear = C.material("clear", "#E8F2F4", rough=0.1, alpha=0.35, blend=True, double=True)
    hob = C.material("hob", "#2A2D30", rough=0.55)
    cap = C.material("cap_grey", "#6B7074", rough=0.4)
    led = C.material("led_red", "#FF2A1A", rough=0.3, emissive="#FF2A1A", emissive_strength=4.0)
    lens = C.material("ai_lens", "#DDE8FF", rough=0.2, emissive="#DDE8FF", emissive_strength=3.0)
    label_png = flipper_label(os.path.join(C.BUILD, "flipper_label.png"))
    label = C.material("flipper_label", "#FFFFFF", rough=0.45, image=C.load_image(label_png, name="flipper_label"))
    objs = []

    # PH-A: back glass upper centre-right (SIMULATION §5.3)
    objs.append(powerhead("PowerheadA", root, (84, 48, 0.0), (0, 0, 1), (-0.6, -0.35, 0.72), [black, grille]))
    # PH-B: left glass upper
    objs.append(powerhead("PowerheadB", root, (0.0, 42, 10), (1, 0, 0), (0.95, -0.2, 0.25), [black, grille]))

    # front pump lying on the sand against the front glass, face toward the viewer, + light-grey flat bracket
    g = T.WGeo()
    c = np.array([78.5, 3.0 + 2.6, 30.0])
    V, F, uv = cylinder(c - (0, 0, 2.8), c + (0, 0, 2.8), 2.5, 20)
    g.add(V, F, uv, mat=0)
    V, F, uv = disc(c + (0, 0, 2.85), (0, 0, 1), 2.0, 20)
    g.add(V, F, uv, mat=1)
    objs.append(g.obj("FrontPump", root, "equipment", [black, grille]))
    g = T.WGeo()
    V, F = oriented_box((73.4, 4.4, 31.8), (5.0, 3.0, 0.5), (1, 0, 0.1), (0, 1, -0.3))
    g.add(V, F)
    V, F = oriented_box((75.6, 3.4, 31.0), (1.2, 0.6, 2.6), (1, 0, 0), (0, 1, 0))
    g.add(V, F)
    objs.append(g.obj("FrontPumpBracket", root, "equipment", [grey]))

    # heater: vertical on the right glass, suction cups as small discs
    g = T.WGeo()
    hx, hz = L - 2.0, 8.5
    V, F, uv = cylinder((hx, 20.0, hz), (hx, 50.0, hz), 1.5, 16)
    g.add(V, F, uv, mat=0)
    V, F, uv = cylinder((hx, 50.0, hz), (hx, 56.5, hz), 1.7, 16)
    g.add(V, F, uv, mat=1)
    V, F, uv = cylinder((hx, 56.5, hz), (hx, 64.0, hz), 0.5, 8)  # cable
    g.add(V, F, uv, mat=0)
    for y in (24.0, 44.0):
        V, F, uv = cylinder((hx + 0.6, y, hz), (L - 0.05, y, hz), 1.2, 10, r1=1.6)
        g.add(V, F, uv, mat=2)
    objs.append(g.obj("Heater", root, "equipment", [black, cap, clear], smooth=True))
    g = T.WGeo()
    V, F = T.box((hx - 1.0, 53.0, hz + 1.55), (hx - 0.4, 53.6, hz + 1.85))
    g.add(V, F)
    objs.append(g.obj("HeaterLED", root, "light", [led]))

    # Flipper magnet cleaner, outside half on the front glass (label faces the viewer)
    g = T.WGeo()
    lo, hi = np.array([2.5, 15.0, D + 0.6]), np.array([6.5, 24.0, D + 2.6])
    V, F = T.box(lo, hi)
    uv = T.box_uv(V, lo, hi)
    g.add(V, F, uv)
    objs.append(g.obj("Flipper", root, "equipment", [label]))

    # clear hose arcing from the top-left corner down behind R2 into the R1 cave
    sdf = T.read_sdf()
    pts = G.bezier((3.7, 64.0, 16.5), (5.0, 50.0, 18.0), (14.0, 34.0, 16.0), (28.0, 27.0, 12.0), 40)
    if sdf is not None:
        dd = sdf(pts[:, 0], pts[:, 1], pts[:, 2])
        k = np.argmax(dd < 1.0) if (dd < 1.0).any() else len(pts)
        pts = pts[:max(k, 8)]
    V, F, t, a = G.tube(pts, 0.6, 10, cap_start=False, cap_end=False, up=(0, 1, 0))
    g = T.WGeo()
    g.add(V, F, np.c_[a, t])
    objs.append(g.obj("HoseArc", root, "equipment", [clear], smooth=True))

    # feeding ring floating at the surface
    g = T.WGeo()
    V, F = torus((41.4, 56.0, 19.8), 4.0, 0.3, 28, 6)
    g.add(V, F)
    objs.append(g.obj("FeedRing", root, "equipment", [clear], smooth=True))

    # HOB filter hanging on the back rim (outside the back glass) with its outflow lip and intake tube
    g = T.WGeo()
    V, F = T.box((62.5, 48.0, -11.0), (78.5, 62.0, -1.0))
    g.add(V, F)
    V, F = T.box((64.0, 57.0, -1.2), (77.0, 58.4, 2.2))  # spillway lip over the glass
    g.add(V, F)
    V, F = T.box((63.0, 62.0, -10.5), (78.0, 63.0, -1.5))  # lid
    g.add(V, F, mat=1)
    V, F, uv = cylinder((76.0, 61.0, -1.0), (76.0, 61.0, 1.6), 0.9, 10, caps=False)
    g.add(V, F, uv)
    V, F, uv = cylinder((76.0, 61.0, 1.6), (76.0, 26.0, 1.6), 0.9, 10)
    g.add(V, F, uv)
    objs.append(g.obj("HobBody", root, "equipment", [hob, cap], extras={"outflow": [71.0, 55.0, 1.0]}))

    # AI Prime HD x2: puck 12 x 12 x 3.5 at 29 cm above the rim, gooseneck from the rear rim
    for side, x in (("L", 30.5), ("R", 91.4)):
        g = T.WGeo()
        pc = np.array([x, 90.0, 16.5])
        # rounded-square puck approximated by a 24-sided superellipse prism
        n = 24
        ring = []
        for k in range(n):
            a = 2 * math.pi * k / n
            ca, sa = math.cos(a), math.sin(a)
            r = 6.0 / (abs(ca) ** 4 + abs(sa) ** 4) ** 0.25
            ring.append((pc[0] + r * ca, pc[2] + r * sa))
        ring = np.array(ring)
        V = np.concatenate([np.c_[ring[:, 0], np.full(n, pc[1] - 1.75), ring[:, 1]],
                            np.c_[ring[:, 0], np.full(n, pc[1] + 1.75), ring[:, 1]],
                            [[pc[0], pc[1] + 1.75, pc[2]]]])
        F = [(k, (k + 1) % n, n + (k + 1) % n, n + k) for k in range(n)]
        F = [f[::-1] for f in F]
        F += [(2 * n, n + (k + 1) % n, n + k) for k in range(n)]
        # bottom ring (black) around the lens
        Vb = np.c_[pc[0] + (ring[:, 0] - pc[0]) * 0.72, np.full(n, pc[1] - 1.75), pc[2] + (ring[:, 1] - pc[2]) * 0.72]
        b0 = len(V)
        V = np.concatenate([V, Vb])
        F += [(k, b0 + k, b0 + (k + 1) % n, (k + 1) % n) for k in range(n)]
        g.add(V, F)
        # gooseneck: from a clamp on the rear rim, up and over to the puck's back
        neck = G.bezier((x, 61.0, -1.5), (x, 82.0, -3.0), (x, 92.0, 2.0), (x, 90.5, 10.6), 24)
        Vn, Fn, t, a = G.tube(neck, 0.9, 10, cap_start=True, cap_end=False, up=(1, 0, 0))
        g.add(Vn, Fn, np.c_[a, t])
        V, F = T.box((x - 2.0, 58.0, -3.5), (x + 2.0, 62.5, 0.3))  # clamp
        g.add(V, F)
        o = g.obj(f"AiPrime{side}", root, "equipment", [black])
        objs.append(o)
        g = T.WGeo()
        Vl = np.c_[pc[0] + (ring[:, 0] - pc[0]) * 0.72, np.full(n, pc[1] - 1.78), pc[2] + (ring[:, 1] - pc[2]) * 0.72]
        V = np.concatenate([[[pc[0], pc[1] - 1.78, pc[2]]], Vl])
        F = [(0, 1 + k, 1 + (k + 1) % n) for k in range(n)]
        g.add(V, F, np.c_[0.5 + (V[:, 0] - pc[0]) / 9, 0.5 + (V[:, 2] - pc[2]) / 9])
        objs.append(g.obj(f"AiPrime{side}_Lens", root, "emitter", [lens],
                          extras={"lightPos": [x, 88.2, 16.5], "coneDeg": 120}))

    for o in objs:
        if not o.name.endswith("_Lens"):  # the lens is an open disc authored facing down
            C.recalc_normals(o)
        C.triangulate(o)
    path = os.path.join(C.MODELS, "equipment.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[equip] equipment.glb: {tris} tris, {os.path.getsize(path) / 1024:.0f} KB")
    return tris


if __name__ == "__main__":
    build()
