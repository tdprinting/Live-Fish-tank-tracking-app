"""Maxima clam: the small giant clam Tridacna maxima (Cardiidae: Tridacninae), docs/research/45_clam_crab_star_cucumber.md.

    /tmp/claude-0/bpyenv/bin/python tools/blender/maxima_clam.py      -> models/maxima_clam.glb

It sits hinge down with the gape facing the light. Same frame and rig as the flame scallop (fish.vert motion mode 7,
core ClamRig): the hinge line at x = -0.5 along z, the gape toward +x, the valves meeting in the y = 0 plane (glTF;
Blender z = 0), the right valve on +y. Unit length is the shell length (z, the longest side: what a seller quotes);
hinge to gape is 0.56. Shell 12 cm by default (extras.lengthCm).
Look (research 45 §1; Rosewater 1965, Knop 1996, Reef Builders photographs [M]): an elongate, rather thick grey-white
shell with 5 strong radial folds that make the gape a deep zig-zag (the two valves interlock), closely packed leaf-like
scutes standing up on the folds toward the upper margin, a wide byssal opening along the hinge. The mantle spreads
well past the shell rim when open: thick, velvety, electric blue to teal with a gold-brown mottle, a row of bright
blue-white hyaline "eye" spots along its edge, a fringed incurrent slit at one end and a raised excurrent siphon cone.

COLOR_0 (fish.vert clamPose):
  R = valve side (1 the +y valve and mantle lobe, 0 the other)
  G = class (0 shell, 0.4 draped mantle / siphon: fish.vert pulls it in and down into the shell as it closes)
  B = t across the mantle (0 at the midline .. 1 at the draped edge; 0 on the shell)
  A = 0
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

SID = "maxima_clam"
LENGTH_CM = 12.0
BUDGET = 3600
S = P.smoothstep

HINGE_X = -0.5
HEIGHT = 0.56              # hinge to gape crest (Tridacna maxima: height ~0.55-0.6 of length [M])
HALF_LEN = 0.5
INFL = 0.21                # one valve's inflation; the closed shell is ~0.42 L thick [M]
N_FOLD = 5                 # strong radial folds (plicae) per valve [M: 4-6 in T. maxima]
FOLD_AMP = 0.045           # zig-zag of the gape, in y
N_PHI = 50                 # around the margin (hinge end to hinge end)
N_T = 12                   # umbo -> margin
MANTLE_X0 = 0.015          # the mantle crest sits just above the gape, hiding the rim
SCUTE_TS = (0.42, 0.52, 0.62, 0.71, 0.8, 0.88, 0.95)   # where along each fold crest the scutes stand
SCUTE_W = 0.03
# mantle palette: base gradient, sheen, mottle, streaks, dark band, edge eye spots
MANTLE = dict(base=("#0C6FA8", "#1590B8"), sheen="#2AC4C9", mottle="#7A6A2A", streak="#1EC3F0", band="#06345A",
              eyes="#BFF2FF", siphon=("#2A6C8A", "#0B2E40"), siphon_rim="#7FD8F0")

# Other Tridacna built on the same shell and rig. The ultra crocea (boring clam, research 62 §1): a smaller (4 cm
# farmed, to 15 cm), stubbier, thicker shell with 6-7 low folds whose scutes are worn nearly flat (it lives ground
# into the rock); the "ultra" mantle is gold-yellow (~#E3B21E) with iridescent electric blue-green lines and spots.
LOOKS = {
    "crocea_clam": dict(SID="crocea_clam", LENGTH_CM=8.0, HEIGHT=0.62, INFL=0.24, N_FOLD=6, FOLD_AMP=0.032,
                        SCUTE_TS=(0.86, 0.94), SCUTE_W=0.018,
                        MANTLE=dict(base=("#D49A12", "#E8B820"), sheen="#F4D040", mottle="#8A5A10", streak="#1ED8E8",
                                    band="#6A3A08", eyes="#5AF0FF", siphon=("#C08A18", "#4A3008"), siphon_rim="#2AD8E8")),
}


def use(look):
    """Switches the module to another species' shell and mantle ([LOOKS])."""
    globals().update(LOOKS[look])


def to_b(p):
    """glTF (x, y, z) -> Blender (x, -z, y)."""
    p = np.asarray(p, float)
    return np.stack([p[..., 0], -p[..., 2], p[..., 1]], -1)


def margin(phi):
    """Valve margin in the commissure (x, z) plane at phi in [0, pi]: 0 and pi at the hinge ends, pi/2 the gape crest.
    Elongate, a flat-bottomed fan: the hinge line runs straight along x = -0.5 for the middle of the shell."""
    phi = np.asarray(phi, float)
    z = HALF_LEN * np.cos(phi) * (1 - 0.08 * np.sin(phi) ** 2)
    x = HINGE_X + HEIGHT * np.sin(phi) ** 0.8 * (1 + 0.06 * np.cos(phi))   # a little taller at the anterior (+z) end
    return x, z


def fold(phi, t):
    """The radial folds: the commissure zig-zags in y with them, growing from the umbo to the margin."""
    return FOLD_AMP * np.sin(phi * N_FOLD * 2 + 0.5 * math.pi) * S(0.15, 0.9, t)


def valve(at, upper):
    sgn = 1.0 if upper else -1.0
    ph = np.linspace(0.0, math.pi, N_PHI)
    tt = np.linspace(0.0, 1.0, N_T + 1)[1:]
    umbo = np.array([HINGE_X + 0.03, 0.0, 0.0])
    V, UV, COL = [umbo + [0, sgn * (INFL * 0.75), 0]], [at.uv(0, 0.0, 0.5)], [(1.0 if upper else 0.0, 0.0, 0.0, 0.0)]
    for t in tt:
        for j, a in enumerate(ph):
            mx, mz = margin(a)
            x = umbo[0] + (mx - umbo[0]) * t
            z = mz * t
            # inflation: a thick dome, fullest a third of the way out, thin at the margin; the hinge flank is steep
            h = INFL * (1 - t ** 2.4) ** 0.5 * math.sin(a) ** 0.35
            # the folds ride on the dome (crests out, grooves in) and set the zig-zag of the margin
            f = fold(a, t)
            y = sgn * h + f
            V.append((x, y, z))
            UV.append(at.uv(0, t, j / (N_PHI - 1)))
            COL.append((1.0 if upper else 0.0, 0.0, 0.0, 0.0))
    # the flat hinge underside closes the shell: margin ends meet along the hinge line (rows end at x ~ -0.5)
    F = []
    n = N_PHI
    for j in range(n - 1):
        F.append((0, 1 + j, 1 + j + 1) if upper else (0, 1 + j + 1, 1 + j))
    for i in range(N_T - 1):
        for j in range(n - 1):
            a, b, c, d = 1 + i * n + j, 1 + i * n + j + 1, 1 + (i + 1) * n + j + 1, 1 + (i + 1) * n + j
            F += [(a, d, c), (a, c, b)] if upper else [(a, b, c), (a, c, d)]
    # the outer rim: a short lip turning in to the commissure so a gaping valve shows a thickness, not a knife edge
    base = len(V)
    last = 1 + (N_T - 1) * n
    for j, a in enumerate(ph):
        x, y, z = V[last + j]
        V.append((x - 0.004, fold(a, 1.0) + sgn * 0.004, z * 0.985))
        UV.append(at.uv(0, 1.0, j / (n - 1))); COL.append((1.0 if upper else 0.0, 0.0, 0.0, 0.0))
    for j in range(n - 1):
        a, b, c, d = last + j, last + j + 1, base + j + 1, base + j
        F += [(a, d, c), (a, c, b)] if upper else [(a, b, c), (a, c, d)]
    return np.array(V), F, np.array(UV), np.array(COL)


def scutes(at, upper, rng):
    """Leaf-like scutes standing out on the fold crests toward the upper margin (growth-line shelves) [M]."""
    sgn = 1.0 if upper else -1.0
    g = G.Geo()
    umbo = np.array([HINGE_X + 0.03, 0.0, 0.0])
    for k in range(1, N_FOLD * 2):
        # crests where the fold pushes outward on this valve
        a = k * math.pi / (N_FOLD * 2)
        if sgn * math.cos(k * math.pi) < 0.5:
            continue
        for t in SCUTE_TS:
            mx, mz = margin(a)
            x = umbo[0] + (mx - umbo[0]) * t
            z = mz * t
            h = INFL * (1 - t ** 2.4) ** 0.5 * math.sin(a) ** 0.35 + 0.004
            y = sgn * h + fold(a, t)
            out = np.array([mx - umbo[0], 0.0, mz])
            out /= np.linalg.norm(out)
            side = np.cross(out, [0, 1.0, 0])
            w = SCUTE_W * (0.5 + 0.7 * t) * rng.uniform(0.8, 1.2)
            lift = sgn * 0.035 * t
            p0 = np.array([x, y, z])
            quad = [p0 - side * w, p0 + side * w, p0 + side * w * 0.8 + out * 0.03 + [0, lift, 0],
                    p0 - side * w * 0.8 + out * 0.03 + [0, lift, 0]]
            Vq = np.array(quad)
            Fq = [(0, 1, 2), (0, 2, 3)] if upper else [(0, 2, 1), (0, 3, 2)]
            uv = np.array([at.uv(0, 0.9, 0.2), at.uv(0, 0.9, 0.3), at.uv(0, 1.0, 0.3), at.uv(0, 1.0, 0.2)])
            g.add(Vq, Fq, uv, np.tile([1.0 if upper else 0.0, 0.0, 0.0, 0.0], (4, 1)))
    return g


def mantle(at, upper):
    """One mantle lobe over the gape: from the midline out past this valve's rim, draped down over it, following the
    zig-zag; t (COLOR_0.b) runs 0 at the midline to 1 at the hanging edge."""
    sgn = 1.0 if upper else -1.0
    ns, nr = 34, 7
    ss = np.linspace(-0.96, 0.96, ns)
    rr = np.linspace(0.0, 1.0, nr)
    V, UV, COL = [], [], []
    for i, r in enumerate(rr):
        for j, s in enumerate(ss):
            phi = math.acos(np.clip(s * 0.98, -1, 1))
            mx, mz = margin(phi)
            end = math.sqrt(max(0.0, 1 - s * s))
            # the lobe reaches ~0.1 L past the valve rim at mid-length when fully open, less at the ends
            half_w = 0.13 * end ** 0.6 + 0.02
            w = half_w * r
            # crest above the gape, a low dome, then draping down over the rim; a wavy, frilled edge
            x = mx + MANTLE_X0 + 0.04 * (1 - r ** 2) * end - 0.045 * S(0.55, 1.0, r) + 0.008 * math.sin(s * 23) * r
            y = sgn * w + fold(phi, 1.0) * (1 - r)
            z = mz * (1 + 0.04 * r)
            V.append((x, y, z))
            UV.append(at.uv(1, r, (j + 0.5) / ns))
            COL.append((1.0 if upper else 0.0, 0.4, r, 0.0))
    F = []
    for i in range(nr - 1):
        for j in range(ns - 1):
            a, b, c, d = i * ns + j, i * ns + j + 1, (i + 1) * ns + j + 1, (i + 1) * ns + j
            F += [(a, b, c), (a, c, d)] if upper else [(a, d, c), (a, c, b)]
    return np.array(V), F, np.array(UV), np.array(COL)


def siphon(at):
    """The excurrent siphon: a raised cone near the posterior (-z) end, open at the top (fish.vert class 0.4)."""
    s = -0.62
    phi = math.acos(s)
    mx, mz = margin(phi)
    base = np.array([mx + MANTLE_X0 + 0.035, 0.0, mz])
    pts = np.array([base + [0.0, 0, 0], base + [0.03, 0, 0.004], base + [0.055, 0, 0.01]])
    Vt, Ft, tv, av = G.tube(to_b(pts), np.array([0.05, 0.035, 0.03]), 10, cap_end=False)
    # tube() builds in Blender space; return as glTF for the shared conversion below
    Vg = np.stack([Vt[:, 0], Vt[:, 2], -Vt[:, 1]], -1)
    col = np.tile([0.5, 0.4, 0.0, 0.0], (len(Vt), 1))
    return Vg, Ft, at.uv(2, tv, av), col


def build():
    C.reset_scene()
    at = Atlas(512, 256, 3)
    rs = np.random.default_rng(45)

    def shell(cv):
        # u = umbo -> margin, v = along the shell. Grey-white, chalky; the grooves darker and greener (algae film),
        # concentric growth lines, the outer margin cream; a dark byssal patch at the hinge.
        u, v = cv.u, cv.v
        cv.vgrad("#D9D4C8", "#E2DDD0")
        groove = 0.5 + 0.5 * np.cos(v * math.pi * N_FOLD * 2 + 0.5 * math.pi)
        cv.blend((1 - groove) * S(0.2, 0.7, u), "#8E9580", 0.4)
        growth = 0.5 + 0.5 * np.sin(u * math.pi * 2 * 18)
        cv.blend(growth * S(0.35, 1.0, u), "#9C9482", 0.25)
        cv.blend(S(0.9, 1.0, u), "#F1EBDD", 0.6)
        cv.blend(P.value_noise(cv.h, cv.w, 24, 10, 3) * S(0.0, 0.3, 1 - u), "#7A7464", 0.25)
        cv.blend(P.ellipse(cv, 0.02, 0.5, 0.06, 0.18, 0.4), "#4C463C", 0.7)   # byssal opening
    at.paint_strip(0, shell)

    def mantle_tex(cv):
        # u = midline -> draped edge, v = along. Electric blue to teal with a gold-brown mottle and wavy streaks,
        # a darker band before the edge, then the bright blue-white hyaline spots along the edge.
        u, v = cv.u, cv.v
        m = MANTLE
        cv.vgrad(*m["base"])
        cv.blend(P.fbm(cv.h, cv.w, 6, 4, 11), m["sheen"], 0.45)
        mott = P.value_noise(cv.h, cv.w, 36, 7, 5)
        cv.blend(S(0.55, 0.8, mott) * S(0.0, 0.7, 1 - u), m["mottle"], 0.65)
        streak = 0.5 + 0.5 * np.sin(v * math.pi * 26 + 4 * np.sin(u * 7))
        cv.blend(S(0.7, 1.0, streak) * S(0.1, 0.6, u), m["streak"], 0.55 if SID != "maxima_clam" else 0.35)
        cv.blend(P.band(u, 0.78, 0.9, 0.04), m["band"], 0.6)
        for (cu, cvv) in P.poisson(rs, 46, 0.035, (0.86, 0.95, 0.02, 0.98)):
            cv.blend(P.ellipse(cv, cu, cvv, 0.018, 0.011, 0.3), m["eyes"])
        cv.blend(P.band(u, 0.0, 0.05, 0.03), "#3B3020", 0.5)   # the dark midline where the lobes meet
    at.paint_strip(1, mantle_tex)

    def siphon_tex(cv):
        cv.vgrad(*MANTLE["siphon"])
        cv.blend(P.band(cv.u, 0.0, 0.3, 0.1), MANTLE["siphon_rim"], 0.5)
    at.paint_strip(2, siphon_tex)
    tex = os.path.join(C.BUILD, f"{SID}_atlas.png")
    at.cv.save(tex)
    img = C.load_image(tex, name=SID)
    pre = SID.replace("_clam", "")
    mshell = C.material(f"{pre}_shell", rough=0.75, image=img)
    mflesh = C.material(f"{pre}_mantle", rough=0.28, image=img, double=True)

    root = make_root("Root", dict(species=SID, lengthCm=LENGTH_CM, iridescence=0.35, roughness=0.5,
                                  unit="length 1.0 = shell length along z; hinge (x -0.5) .. gape (x +0.06); valves meet at y = 0"))
    gs = G.Geo()
    for up in (True, False):
        V, F, UV, COL = valve(at, up)
        gs.add(to_b(V), F, UV, COL)
        g = scutes(at, up, rs)
        Vq, Fq, UVq, COLq, _ = g.arrays()
        gs.add(to_b(Vq), Fq, UVq, COLq)
    shell_o = geo_obj("Shell", gs, root, "body", [mshell])
    gm = G.Geo()
    for up in (True, False):
        V, F, UV, COL = mantle(at, up)
        gm.add(to_b(V), F, UV, COL)
    V, F, UV, COL = siphon(at)
    gm.add(to_b(V), F, UV, COL)
    mantle_o = geo_obj("Mantle", gm, root, "body", [mflesh])
    for o in (shell_o, mantle_o):
        C.recalc_normals(o)
    return finish(root, [shell_o, mantle_o], os.path.join(C.MODELS, f"{SID}.glb"), BUDGET)


if __name__ == "__main__":
    for a in sys.argv[1:]:
        if a in LOOKS:
            use(a)
    build()
