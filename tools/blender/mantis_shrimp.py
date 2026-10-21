"""Peacock mantis shrimp, Odontodactylus scyllarus (docs/research/62_rare_inverts.md §2), on the shrimp limb rig
(core ShrimpLimbs, fish.vert mode 2) like the shrimp pack, with a stomatopod's own build:

  * a long, low, dorsoventrally flattened body: a short shield-like carapace over the head (leopard spotted), three
    exposed thoracic segments carrying the walking legs, a broad six-segment abdomen and a spiny telson with
    uropods; mostly green, the carapace spotted dark, thin pale rims on the segment edges (Wikipedia [M]);
  * the smasher's raptorial appendages (second maxillipeds) folded under the head like a jackknife, the swollen red
    dactyl heel (the club) at the front; class CLAW, so the sim's strike tip offset (uLimb[13/14]) unfolds them
    forward and the meral spread display (S.z) swings them up and out;
  * three small maxillipeds (3-5) under the mouth (class PICK pairs 0-1 and MXP), three pairs of slender orange
    walking legs (pereiopods 6-8, class WALK pairs 2-4) and five pairs of orange swimmerets with gills (PLEO);
  * stalked, oblong compound eyes split by a dark midband, triple antennule whips held forward and a pair of
    orange-pink paddle antennal scales out to the sides.

Unit: rostral plate .. telson tip = 1.0, facing +X, Blender Z up, the spine at z = 0, feet at z = -MANTIS_STANCE.
COLOR_0 as build_shrimp: R = class / 8, G = pair / 8, B = hinge, A = spine u of the attachment point.
Prints the rest tips for core ShrimpLimbs MANTIS_* (glTF space, left = Blender +Y).

    /tmp/claude-0/bpyenv/bin/python tools/blender/mantis_shrimp.py
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import common as C          # noqa: E402
import geomlib as G         # noqa: E402
import paintlib as P        # noqa: E402
from inverts import Atlas, finish, geo_obj, leg_path, make_root, shrimp_body, smooth_path, tail_fan   # noqa: E402

S = P.smoothstep
SID = "mantis_shrimp"
LENGTH_CM = 12.0          # a typical trade animal (3-18 cm, research 60 §11)
STANCE = 0.1              # spine height over the ground, body lengths (core shrimpStance for the mantis)
WALK, PICK, CLAW, ANT, ANTL, PLEO, MXP = 1, 2, 3, 4, 5, 6, 7
CLAW_STRIP = 4
GREEN, GREEN_D, BELLY = "#2E9A4C", "#1F6E36", "#B9CC7A"
ORANGE, RED = "#F28A1E", "#E0402E"
# segment edges along the spine (u): carapace rear, three thoracic, six abdominal
SEGS = (0.27, 0.32, 0.37, 0.42, 0.5, 0.58, 0.66, 0.74, 0.82, 0.9)


def paint(at):
    def body(cv):
        u, v = cv.u, cv.v
        cv.vgrad(BELLY, GREEN, 0.0, 0.55)
        cv.blend(S(0.7, 1.0, v), GREEN_D, 0.45)
        # leopard spots on the carapace: dark rings round olive centres, small dark dots between (WP: "leopard-like
        # spots on the front of the shell")
        rs = np.random.default_rng(71)
        for (cu, cvv) in P.poisson(rs, 26, 0.035, (0.03, 0.26, 0.35, 1.0)):
            rr = rs.uniform(0.008, 0.013)
            cv.blend(P.ellipse(cv, cu, cvv, rr * 1.35, rr * 1.35 * 3.5, 0.2), "#16301C", 0.9)
            cv.blend(P.ellipse(cv, cu, cvv, rr, rr * 3.5, 0.2), "#5E8A3A", 0.9)
        for (cu, cvv) in P.poisson(rs, 40, 0.02, (0.02, 0.27, 0.3, 1.0)):
            cv.blend(P.ellipse(cv, cu, cvv, 0.003, 0.0105, 0.3), "#16301C", 0.7)
        # segment joints: a dark groove with a thin pale rim behind it [I]
        for tb in SEGS:
            cv.blend(P.band(u, tb - 0.004, tb + 0.002, 0.002), "#123A1E", 0.7)
            cv.blend(P.band(u, tb + 0.003, tb + 0.008, 0.002) * S(0.4, 0.7, v), "#D8E890", 0.55)
        # the flanks of the abdomen warm toward the legs, the telson spines darker
        cv.blend(S(0.42, 0.5, u) * (1 - S(0.0, 0.25, v)), "#C8A040", 0.35)
        cv.blend(S(0.9, 0.96, u) * S(0.6, 0.9, v), "#123A1E", 0.4)
        cv.blend(P.band(u, -0.1, 0.03, 0.01), "#6E9A50", 0.6)   # rostral plate
        cv.multiply(0.93 + 0.14 * P.fbm(cv.h, cv.w, 40, 3, 72))
    at.paint_strip(0, body)
    # walking legs and swimmerets orange, darker joints (WP: "orange legs")
    at.paint_strip(1, lambda cv: (cv.vgrad("#E07A18", ORANGE), [cv.blend(P.band(cv.u, t0, t0 + 0.03, 0.01), "#A04A10", 0.6)
                                                               for t0 in (0.3, 0.6, 0.85)]))
    # antennules, antennae, small maxillipeds and the antennal scales: pale orange-pink, the scale fringe bright
    at.paint_strip(2, lambda cv: (cv.vgrad("#E89A70", "#F4C0A0"), cv.blend(S(0.8, 0.95, cv.u), "#FF7A50", 0.6)))

    def tail(cv):
        # telson and uropods: green at the base, a blue band, orange-red tips (blue-edged tail [I])
        cv.vgrad(GREEN, "#3AA85A")
        cv.blend(S(0.45, 0.6, cv.u), "#2A6FD0", 0.85)
        cv.blend(S(0.82, 0.92, cv.u), "#E85A2A", 0.8)
    at.paint_strip(3, tail)

    def claw(cv):
        # raptorial: merus cream-green, propodus pink-orange, the dactyl heel (club) red (research 60 §11)
        u = cv.u
        cv.vgrad("#C8D8A0", "#E2E8C0")
        cv.blend(S(0.28, 0.34, u), "#F08A60")
        cv.blend(S(0.8, 0.86, u), RED)
        cv.blend(S(0.88, 0.97, u) * S(0.4, 1.0, cv.v), "#F2B0A0", 0.4)   # a pale sheen on the heel
        cv.multiply(0.95 + 0.08 * P.fbm(cv.h, cv.w, 30, 3, 73))
    at.paint_strip(CLAW_STRIP, claw)


def build():
    C.reset_scene()
    at = Atlas(512, 320, 5)
    paint(at)
    tex = os.path.join(C.BUILD, f"{SID}_atlas.png")
    at.cv.save(tex)
    img = C.load_image(tex, name=SID)
    mshell = C.material(f"{SID}_shell", rough=0.35, image=img)
    meye = C.material(f"{SID}_eye", color="#4E6E3A", rough=0.1)
    mband = C.material(f"{SID}_eyeband", color="#1A2418", rough=0.2)
    root = make_root("Root", dict(species=SID, lengthCm=LENGTH_CM, unit="body length 1.0 = rostral plate..telson, facing +X",
                                  iridescence=0.15, roughness=0.35))

    # --- body: long, low and flat (squash > 1 widens it) ------------------------------------------------------
    keys = [(0.6, 0, 0.004), (0.5, 0, 0.01), (0.35, 0, 0.014), (0.2, 0, 0.012), (0.0, 0, 0.012), (-0.2, 0, 0.01),
            (-0.38, 0, 0.0), (-0.5, 0, -0.012)]
    rad = [(0.0, 0.012), (0.04, 0.032), (0.1, 0.056), (0.25, 0.066), (0.29, 0.05), (0.4, 0.058), (0.47, 0.068),
           (0.56, 0.074), (0.8, 0.076), (0.92, 0.066), (1.0, 0.05)]
    gb = G.Geo()
    V, F, UV, COL, spine, R = shrimp_body(at, 0, keys, rad, n=56, sides=14, squash=1.3)
    # each segment swells a little between its joints, pinched at the grooves
    tv = COL[:, 3]
    groove = sum(P.band(tv, tb - 0.012, tb + 0.012, 0.01) for tb in SEGS)
    on = np.stack([np.interp(tv * (len(spine) - 1), np.arange(len(spine)), spine[:, i]) for i in range(3)], -1)
    V = on + (V - on) * (1.0 - 0.09 * np.clip(groove, 0, 1))[:, None]
    gb.add(V, F, UV, COL)
    end = spine[-1]
    d = spine[-1] - spine[-3]
    for (Vf, Ff, k) in tail_fan(end, d, 0.13, lobes=5, spread=130, w=0.065, droop=0.0):
        tl = np.linalg.norm(Vf - end, axis=1) / 0.13
        gb.add(Vf, Ff, at.uv(3, tl, np.full(len(Vf), 0.5)), [(0, 0, 0, 1.0)] * len(Vf))
    # telson spines: a row of short teeth round the rear edge, and the dorsal carinae of the 6th segment
    for a in np.linspace(-0.9, 0.9, 6):
        dd = np.array([math.cos(a), math.sin(a), 0.0])
        Vc, Fc = G.cone(end + dd * 0.07 + [0, 0, 0.004], end + dd * 0.105 + [0, 0, 0.004], 0.006, 4)
        gb.add(Vc, Fc, at.uv(0, np.full(len(Vc), 0.97), np.full(len(Vc), 0.9)), [(0, 0, 0, 1.0)] * len(Vc))
    body = geo_obj("Body", gb, root, "shell", [mshell])

    bases = []

    def limb(g, pts, radii, sides, cls, pair, strip, base_x, cap_end=True, h0=0.0, h1=1.0):
        Vt, Ft, tv, av = G.tube(pts, radii, sides, cap_end=cap_end)
        hh = h0 + (h1 - h0) * tv
        col = np.stack([np.full(len(Vt), cls / 8), np.full(len(Vt), pair / 8), hh, np.zeros(len(Vt))], -1)
        g.add(Vt, Ft, at.uv(strip, hh, av), col)
        bases.append((g, len(Vt), base_x))

    def blob(g, center, radii, cls, pair, strip, base_x, h, rot=None, segs=10, rings=6):
        Ve, Fe = G.ellipsoid(center, radii, segs, rings, rot=rot)
        g.add(Ve, Fe, at.uv(strip, np.full(len(Ve), h)), [(cls / 8, pair / 8, h, 0)] * len(Ve))
        bases.append((g, len(Ve), base_x))

    # --- legs: walking legs on the three free thoracic segments, maxillipeds under the head, swimmerets ---------
    gl = G.Geo()
    foot_z = -STANCE * 1.23
    for pr, x in ((2, 0.27), (3, 0.215), (4, 0.16)):
        for side in (1, -1):
            base = np.array([x, side * 0.06, -0.035])
            pts = leg_path(base, base + [0.015, side * 0.11, -0.01], base + [-0.01, side * 0.15, -0.06],
                           np.array([x - 0.035, side * 0.165, foot_z]), 7)
            limb(gl, pts, np.linspace(0.011, 0.004, 7), 5, WALK, pr, 1, x)
    for pr, x in ((0, 0.4), (1, 0.36)):
        for side in (1, -1):
            # maxillipeds 3-4: small subchelate grabbers folded under the mouth [M]
            base = np.array([x, side * 0.035, -0.045])
            pts = leg_path(base, base + [0.03, side * 0.05, -0.03], base + [0.07, side * 0.05, -0.055],
                           base + [0.1, side * 0.035, -0.06], 6)
            limb(gl, pts, np.linspace(0.009, 0.004, 6), 4, PICK, pr, 2, x)
    for side in (1, -1):
        base = np.array([0.32, side * 0.035, -0.045])   # maxilliped 5
        pts = leg_path(base, base + [0.03, side * 0.05, -0.025], base + [0.06, side * 0.05, -0.045], base + [0.085, side * 0.035, -0.05], 5)
        limb(gl, pts, np.linspace(0.008, 0.004, 5), 4, MXP, 0, 2, 0.32)
    for pi in range(5):
        k = 30 + pi * 4
        p0 = spine[k]
        for side in (1, -1):
            base = p0 + np.array([0.0, side * 0.04, -R[k] * 0.8])
            pts = leg_path(base, base + [-0.015, side * 0.035, -0.012], base + [-0.035, side * 0.045, -0.02],
                           base + [-0.06, side * 0.04, -0.022], 4)
            limb(gl, pts, np.linspace(0.012, 0.004, 4), 4, PLEO, pi, 1, p0[0])
            # the gill tuft on the swimmeret (stomatopods breathe through pleopod gills)
            blob(gl, base + [-0.02, side * 0.03, -0.022], (0.016, 0.01, 0.008), PLEO, pi, 2, p0[0], 0.5, segs=6, rings=4)
    legs = geo_obj("Legs", gl, root, "leg", [mshell], extras={"count": 22})

    # --- raptorial appendages (the clubs) -----------------------------------------------------------------------
    gc = G.Geo()
    for side in (1, -1):
        n0 = len(gc.V)
        base = np.array([0.42, side * 0.045, -0.03])
        knee = np.array([0.405, side * 0.08, -0.085])
        # merus: a thick segment down from under the carapace to the knee
        limb(gc, smooth_path([base, base + [0.0, side * 0.02, -0.03], knee], 6), np.linspace(0.02, 0.026, 6), 7, CLAW, 0,
             CLAW_STRIP, 0.42, cap_end=False, h0=0.0, h1=0.3)
        bases.pop()
        # carpus + propodus: forward along the underside of the head to the heel
        heel = np.array([0.52, side * 0.072, -0.062])
        limb(gc, smooth_path([knee, knee + [0.05, -side * 0.004, 0.012], heel], 7), np.linspace(0.022, 0.018, 7), 7, CLAW, 0,
             CLAW_STRIP, 0.42, cap_end=False, h0=0.3, h1=0.85)
        bases.pop()
        # the club: the swollen dactyl heel, round and blunt, facing forward
        blob(gc, heel + [0.014, 0, -0.004], (0.034, 0.028, 0.03), CLAW, 0, CLAW_STRIP, 0.42, 1.0)
        bases.pop()
        # the dactyl folded back under the propodus
        limb(gc, smooth_path([heel + [0.0, 0, -0.02], heel + [-0.04, 0, -0.03], heel + [-0.08, 0, -0.026]], 5),
             np.linspace(0.011, 0.004, 5), 5, CLAW, 0, CLAW_STRIP, 0.42, h0=0.95, h1=0.9)
        bases.pop()
        bases.append((gc, len(gc.V) - n0, 0.42))
    geo_obj("Claws", gc, root, "leg", [mshell], extras={"claw": True})

    # --- antennules (three whips each), antennae with their paddle scales ----------------------------------------
    ga = G.Geo()
    hx = 0.6
    for side in (1, -1):
        ped = np.array([hx, side * 0.018, 0.012])
        for j, (ln, sp, lift) in enumerate(((0.42, 0.06, 0.12), (0.34, 0.12, 0.08), (0.28, 0.18, 0.04))):
            ctrl = [ped, ped + [0.08, side * 0.01, 0.03], ped + [0.2, side * sp * 0.6, lift],
                    ped + [0.3, side * sp, lift * 0.8]]
            pts = G.bezier(*ctrl, 12)
            L = np.sum(np.linalg.norm(np.diff(pts, axis=0), axis=1))
            pts = ped + (pts - ped) * (ln / L)
            limb(ga, pts, np.linspace(0.006, 0.0015, 12), 4, ANTL, min(j, 1), 2, hx, cap_end=False)
        ab = np.array([hx - 0.02, side * 0.04, 0.0])
        ctrl = [ab, ab + [0.08, side * 0.08, 0.02], ab + [0.18, side * 0.2, 0.05], ab + [0.2, side * 0.32, 0.04]]
        pts = G.bezier(*ctrl, 12)
        limb(ga, pts, np.linspace(0.005, 0.0015, 12), 4, ANT, 0, 2, hx, cap_end=False)
        # the antennal scale: a flat oval paddle held out to the side, fringed
        dd = np.array([0.55, side * 0.8, 0.1]); dd /= np.linalg.norm(dd)
        ww = np.cross([0, 0, 1.0], dd); ww /= np.linalg.norm(ww)
        nz = np.cross(dd, ww)
        c0 = ab + dd * 0.07
        Ve, Fe = G.ellipsoid(c0, (0.055, 0.026, 0.005), 12, 6, rot=np.stack([dd, ww, nz], -1))
        hl = np.clip(0.1 + 0.9 * ((Ve - ab) @ dd) / 0.13, 0.0, 1.0)
        ga.add(Ve, Fe, at.uv(2, hl, np.full(len(Ve), 0.5)), np.stack([np.full(len(Ve), ANT / 8), np.zeros(len(Ve)), 0.15 * hl, np.zeros(len(Ve))], -1))
        bases.append((ga, len(Ve), hx))
    geo_obj("Antennae", ga, root, "antenna", [mshell], extras={"count": 8})

    # --- eyes: oblong, on mobile stalks, split by the midband -----------------------------------------------------
    ge = G.Geo()
    for side in (1, -1):
        b0 = np.array([hx - 0.005, side * 0.022, 0.02])
        tip = b0 + np.array([0.035, side * 0.03, 0.045])
        Vt, Ft, tv, av = G.tube([b0, tip], [0.011, 0.01], 6, cap_end=False)
        ge.add(Vt, Ft, at.uv(0, np.full(len(Vt), 0.02), np.full(len(Vt), 0.8)), [(0, 0, 0, 0.0)] * len(Vt), mat=0)
        ec = tip + [0.004, side * 0.004, 0.022]
        Ve, Fe = G.ellipsoid(ec, (0.026, 0.023, 0.038), 10, 8)
        ge.add(Ve, Fe, None, [(0, 0, 0, 0.0)] * len(Ve), mat=1)
        Vb, Fb = G.ellipsoid(ec, (0.0275, 0.0245, 0.007), 10, 4)
        ge.add(Vb, Fb, None, [(0, 0, 0, 0.0)] * len(Vb), mat=2)
    eyes = geo_obj("Eyes", ge, root, "eye", [mshell, meye, mband])

    objs = [o for o in C.all_descendants(root) if o.type == 'MESH']
    xs = np.r_[spine[:, 0], end[0] - 0.13]
    x0, x1 = xs.min(), xs.max()
    sc = 1.0 / (x1 - x0)
    off = -(x0 + x1) / 2
    for o in objs:
        Vv = C.get_verts(o)
        Vv[:, 0] += off
        Vv *= sc
        o.data.vertices.foreach_set("co", Vv.ravel())
        o.data.update()
    for o in objs:
        if o.name not in ("Legs", "Claws", "Antennae"):
            continue
        g = [b for b in bases if b[0] is {"Legs": gl, "Antennae": ga, "Claws": gc}[o.name]]
        cols = C.get_cols(o)
        i = 0
        for _, n, bx in g:
            cols[i:i + n, 3] = np.clip(0.5 - (bx + off) * sc, 0.0, 1.0)
            i += n
        assert i == len(cols), (o.name, i, len(cols))
        C.set_cols(o, cols)
    for o in (body, eyes):
        C.recalc_normals(o)
    report(objs, sc)
    return finish(root, objs, os.path.join(C.MODELS, f"{SID}.glb"), 5200)


def report(objs, sc):
    """Rest tips (glTF x, y, z of the left = Blender +Y limb) for core ShrimpLimbs MANTIS_*."""
    def tip(o, cls, pr):
        V, Cc = C.get_verts(o), C.get_cols(o)
        sel = (np.abs(Cc[:, 0] - cls / 8) < 0.02) & (np.abs(Cc[:, 1] - pr / 8) < 0.02) & (V[:, 1] > 0)
        h = Cc[sel, 2]
        p = V[sel][h > h.max() - 0.02].mean(0)
        return [round(float(p[0]), 4), round(float(p[2]), 4), round(float(-p[1]), 4)]
    legs = next(o for o in objs if o.name == "Legs")
    claws = next(o for o in objs if o.name == "Claws")
    out = []
    for pr in (2, 3, 4):
        out += tip(legs, WALK, pr)
    print("[mantis] MANTIS_LEGS = doubleArrayOf(" + ", ".join(map(str, out)) + ")")
    out = []
    for pr in (0, 1):
        out += tip(legs, PICK, pr)
    print("[mantis] MANTIS_PICKS = doubleArrayOf(" + ", ".join(map(str, out)) + ")")
    print("[mantis] club rest tip", tip(claws, CLAW, 0))
    V, Cc = C.get_verts(legs), C.get_cols(legs)
    print(f"[mantis] foot z {V[:, 2].min():.4f}, scale {sc:.4f}")


if __name__ == "__main__":
    build()
