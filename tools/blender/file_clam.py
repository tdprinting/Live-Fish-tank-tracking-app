"""Flame scallop: the rough fileclam Ctenoides scaber (Limidae), docs/research/42_peppermint_shrimp_flame_scallop.md.

    /tmp/claude-0/bpyenv/bin/python tools/blender/file_clam.py      -> models/flame_scallop.glb

Unit length along X like the fish: the hinge (umbones, auricles) at x = -0.5, the gape (the open ventral margin) at
x = +0.5; the valves meet in the y = 0 plane (glTF; Blender z = 0), the right valve on top (+Y), the left below.
Shell 6 cm by default (extras.lengthCm); the two closed valves are 0.38 L thick (species sheet depthRatio).
Look (research 42 §2.2; SeaLifeBase, Reef Builders 2014, Wikipedia photographs [M]): an obliquely oval, rather flat
shell, white to cream under a thin tan-brown periostracum, with many fine radial ribs carrying rows of small scales
(the "rough" file); a fleshy red-orange mantle lip lining each valve margin, and a fringe of long, slender red
tentacles, shorter ones near the hinge, pale at the tips. No flashing strip: that is its cousin C. ales.

COLOR_0 (fish.vert motion mode 7, core ClamRig):
  R = valve side (1 upper / right valve and the upper mantle lobe and tentacles, 0 lower)
  G = class (0 shell, 0.5 mantle, 1 tentacle)
  B = t along a tentacle (0 root .. 1 tip; 0 elsewhere)
  A = tentacle phase seed (0..1; 0 elsewhere)
fish.vert opens the valves about the hinge line (x = -0.5, y = 0, along Z) by the sim's gape, pushes the mantle out,
and extends, writhes or streams the tentacles; everything else is rigid.
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import geomlib as G  # noqa: E402
import paintlib as P  # noqa: E402
from inverts import Atlas, geo_obj, make_root, finish  # noqa: E402

SID = "flame_scallop"
LENGTH_CM = 6.0
BUDGET = 3200
HALF_THICK = 0.17          # one valve's inflation (with the gaping lips about 0.38 L both)
HINGE_X = -0.5
N_THETA = 30               # around the margin
N_T = 9                    # umbo -> margin
N_TENT = 28                # per lobe (upper and lower): 56 tentacles
S = P.smoothstep


UMBO = np.array([HINGE_X + 0.07, 0.0])     # the beak sits just inside the hinge end of the oval
CX, AX, BZ = 0.02, 0.48, 0.37               # the oval: centre x, half length, half width (glTF z)
GAPE_SPREAD = math.radians(115)             # the mantle and tentacles fringe the margin within this of +X


def outline(phi):
    """Margin of the valve in the commissure plane at ellipse angle phi (0 = straight at the gape, pi = the hinge).
    Obliquely oval: the +z (anterior) side bulges a little more (Lima-like obliquity); flattened at the hinge line."""
    phi = np.asarray(phi, float)
    x = CX + AX * np.cos(phi)
    z = BZ * np.sin(phi) * (1 + 0.08 * np.cos(phi) * np.sign(np.sin(phi)))
    x = np.maximum(x, HINGE_X + 0.02 * (1 - np.abs(np.sin(phi))))  # straight hinge line
    return x, z


def valve(at, upper):
    """One valve: a dome over the oval, rows from the umbo out to the margin, closed all the way round.
    Returns V, F, UV, COL (Blender frame, z up)."""
    sgn = 1.0 if upper else -1.0
    n = N_THETA
    ph = np.linspace(0, 2 * math.pi, n, endpoint=False)
    tt = np.linspace(0.06, 1.0, N_T)
    V, UV, COL = [], [], []
    col = (1.0 if upper else 0.0, 0.0, 0.0, 0.0)
    V.append((UMBO[0], 0.0, sgn * (HALF_THICK + 0.004)))   # the beak
    UV.append(at.uv(0, 0.0, 0.5)); COL.append(col)
    for t in tt:
        for j, a in enumerate(ph):
            mx, mz = outline(a)
            x = UMBO[0] + (mx - UMBO[0]) * t
            z = mz * t
            # inflation: a low dome from the beak falling to the thin margin; radial ribs
            h = HALF_THICK * (1 - t ** 2.2) ** 0.55
            rib = 0.006 * abs(math.sin(a * 24)) * S(0.1, 0.4, t)
            V.append((x, -z, sgn * (h + rib + 0.004)))
            UV.append(at.uv(0, t, (j + 0.5) / n)); COL.append(col)
    F = []
    for j in range(n):
        k = (j + 1) % n
        F.append((0, 1 + k, 1 + j) if upper else (0, 1 + j, 1 + k))
    for i in range(N_T - 1):
        for j in range(n):
            k = (j + 1) % n
            a, b, c, d = 1 + i * n + j, 1 + i * n + k, 1 + (i + 1) * n + k, 1 + (i + 1) * n + j
            F += [(a, d, c), (a, c, b)] if upper else [(a, b, c), (a, c, d)]
    return np.array(V), F, np.array(UV), np.array(COL)


def mantle(at, upper):
    """The fleshy mantle lip just inside the valve margin along the gape, bulging out past it (a band)."""
    sgn = 1.0 if upper else -1.0
    th = np.linspace(-GAPE_SPREAD, GAPE_SPREAD, N_THETA)
    rows = [(0.90, 0.020), (0.99, 0.012), (1.035, 0.004)]  # (fraction of the margin radius, height)
    V, UV, COL = [], [], []
    for k, (f, hgt) in enumerate(rows):
        for j, a in enumerate(th):
            mx, mz = outline(a)
            V.append((UMBO[0] + (mx - UMBO[0]) * f, -mz * f, sgn * hgt))
            UV.append(at.uv(1, k / (len(rows) - 1), (j + 0.5) / N_THETA))
            COL.append((1.0 if upper else 0.0, 0.5, 0.0, 0.0))
    F = []
    n = N_THETA
    for i in range(len(rows) - 1):
        for j in range(n - 1):
            a, b, c, d = i * n + j, i * n + j + 1, (i + 1) * n + j + 1, (i + 1) * n + j
            F += [(a, b, c), (a, c, d)] if upper else [(a, d, c), (a, c, b)]
    return np.array(V), F, np.array(UV), np.array(COL)


def tentacles(at, upper, rng):
    """A row of long slender tentacles from the mantle edge, radiating outward in the commissure plane and curling a
    little; shortest toward the hinge ends. Each carries its root-to-tip t and a phase seed for the writhing."""
    sgn = 1.0 if upper else -1.0
    g = G.Geo()
    th = np.linspace(-GAPE_SPREAD * 0.95, GAPE_SPREAD * 0.95, N_TENT) + (0.5 if upper else 0.0) * (2 * GAPE_SPREAD * 0.95 / N_TENT)
    ux = UMBO[0]
    for i, a in enumerate(th):
        mx, mz = outline(a)
        root = np.array([ux + (mx - ux) * 1.02, -mz * 1.02, sgn * 0.008])
        out = np.array([mx - ux, -mz, 0.0])
        out /= np.linalg.norm(out)
        side = np.cross(out, [0, 0, 1.0])
        # long at the gape, short near the hinge ends (research 42 §2.2: a dense fringe of long tentacles) [E lengths]
        L = (0.12 + 0.24 * math.cos(a * 0.7) ** 2) * rng.uniform(0.7, 1.15)
        curl = rng.uniform(-1.4, 1.4)
        lift = sgn * rng.uniform(0.02, 0.12)
        pts = []
        for k in range(6):
            s = k / 5
            bend = curl * s * s
            p = root + out * (L * s) + side * (L * 0.45 * bend) + np.array([0, 0, lift * s * L * 2.5])
            pts.append(p)
        Vt, Ft, tv, av = G.tube(np.array(pts), np.linspace(0.011, 0.0035, 6), 3, cap_end=True)
        seed = rng.uniform(0, 1)
        g.add(Vt, Ft, at.uv(2, tv, av), np.stack([np.full(len(Vt), 1.0 if upper else 0.0), np.full(len(Vt), 1.0), tv, np.full(len(Vt), seed)], -1))
    return g


def build():
    C.reset_scene()
    at = Atlas(512, 256, 4)

    def shell(cv):
        # u = umbo -> margin, v = around. Cream-white shell under a tan periostracum that wears off toward the umbo,
        # radial ribs (light ridges, darker grooves) and concentric growth lines; brown scale rows near the margin.
        u, v = cv.u, cv.v
        cv.vgrad("#EDE6DA", "#E6DCCB")
        cv.blend(S(0.35, 0.95, u), "#B08A62", 0.55)
        ribs = 0.5 + 0.5 * np.sin(v * math.pi * 2 * 22)
        cv.blend(ribs * S(0.1, 0.4, u), "#FFFFFF", 0.25)
        cv.blend((1 - ribs) * S(0.15, 0.5, u), "#6E5038", 0.25)
        growth = 0.5 + 0.5 * np.sin(u * math.pi * 2 * 14)
        cv.blend(growth * S(0.3, 1.0, u), "#5A3E28", 0.18)
        cv.blend(P.value_noise(cv.h, cv.w, 30, 8, 3) * S(0.5, 1.0, u), "#4A3220", 0.2)
    at.paint_strip(0, shell)

    def mantle_tex(cv):
        # deep red-orange flesh, darker at the inner edge, a paler glistening lip
        cv.vgrad("#B81E14", "#D93A1E")
        cv.blend(S(0.0, 0.4, cv.u), "#E8502A", 0.6)
        cv.blend(P.value_noise(cv.h, cv.w, 50, 10, 7), "#7A0E0A", 0.25)
    at.paint_strip(1, mantle_tex)

    def tentacle_tex(cv):
        cv.vgrad("#C8261A", "#D63A22")
        cv.blend(S(0.75, 1.0, cv.u), "#F2A07A", 0.7)   # pale tips
        cv.blend(P.band(cv.u, 0.0, 0.06, 0.02), "#8E140E", 0.6)
    at.paint_strip(2, tentacle_tex)
    at.paint_strip(3, lambda cv: cv.vgrad("#C8261A", "#C8261A"))
    tex = os.path.join(C.BUILD, "flame_scallop_atlas.png")
    at.cv.save(tex)
    img = C.load_image(tex, name="flame_scallop")
    mshell = C.material("scallop_shell", rough=0.55, image=img)
    mflesh = C.material("scallop_mantle", rough=0.3, image=img, double=True)

    root = make_root("Root", dict(species=SID, lengthCm=LENGTH_CM, iridescence=0.0, roughness=0.5,
                                  unit="length 1.0 = hinge (x -0.5) .. gape (x +0.5); valves meet at y = 0"))
    gs = G.Geo()
    for up in (True, False):
        V, F, UV, COL = valve(at, up)
        gs.add(V, F, UV, COL)
    shell_o = geo_obj("Shell", gs, root, "body", [mshell])
    gm = G.Geo()
    for up in (True, False):
        V, F, UV, COL = mantle(at, up)
        gm.add(V, F, UV, COL)
    mantle_o = geo_obj("Mantle", gm, root, "body", [mflesh])
    rng = np.random.default_rng(41)
    gt = G.Geo()
    for up in (True, False):
        g = tentacles(at, up, rng)
        V, F, UV, COL, FM = g.arrays()
        gt.add(V, F, UV, COL)
    tent_o = geo_obj("Tentacles", gt, root, "leg", [mflesh], extras={"count": 2 * N_TENT})
    for o in (shell_o, mantle_o):
        C.recalc_normals(o)
    objs = [shell_o, mantle_o, tent_o]
    return finish(root, objs, os.path.join(C.MODELS, f"{SID}.glb"), BUDGET)


if __name__ == "__main__":
    build()
