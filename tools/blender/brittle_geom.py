"""Black brittle star arm geometry (pure numpy, Blender Z-up model space; no bpy): five flattened, tapering arms in
their straight rest pose with a pair of stout spines at every ring. Shared by inverts.build_brittle_star and anything
that rebuilds the arms outside Blender. The renderer bends the arms along the sim's per-step pose (fish.vert starArm,
core BrittleArms), so the arms carry enough rings to follow a tight curl smoothly.

COLOR_0 (limb-rigged crawlers): R = t along the arm (0 root .. 1 tip), B = arm index / 9, A = 0.5 - x.
"""
import math

import numpy as np

import geomlib as G

ROOT_R = 0.05       # disc-arm junction radius (fraction of span); core BrittleArms.ROOT_R
ARM_LEN = 0.45      # junction -> tip; core BrittleArms.ARM_LEN
DISC_R = 0.056      # disc radius: disc 25 mm, arms 100 mm (4 disc diameters), span 225 mm; core BrittleArms.DISC_R
ARM_Z = 0.0085      # arm centreline height above the substrate; core BrittleArms.ARM_Y
RINGS = 27          # cross-section rings along each arm (≈ 1.8 per sim step of 1/15 arm)
SIDES = 5
SQUASH = 0.62       # arm cross-section height / width (flattened arms)
SEGMENTS = 52       # visible arm segments painted on the atlas (a real 10 cm arm has ~80-100)


def arm_radius(tt):
    """Half-width of the arm at t (0 root .. 1 tip), fraction of span: O. erinaceus arms are robust, ~0.011 at the
    base, tapering to a fine whip tip, with a faint bead at each painted segment."""
    tt = np.asarray(tt, float)
    return 0.0105 * (1 - 0.82 * tt ** 1.25) * (1 + 0.06 * np.cos(tt * SEGMENTS * math.pi / 2) ** 2)


def arms(uv, girth=1.0, spine_len=(0.012, 0.018), spine_side=0.88, spine_fwd=0.35, spine_every=1, radius=None):
    """The five arms with their spines as one geomlib.Geo. uv(strip, t, around) maps atlas strips: 0 arm (t along,
    around = dorsal-ness 1 dorsal .. 0 ventral), 2 spines (around 0 base .. 1 tip). The keyword arguments reshape the
    arms for other ophiuroids on the same rig (the serpent star: thicker, smooth arms, short spines laid along them)."""
    ga = G.Geo()
    t = np.linspace(0, 1, RINGS)
    for i in range(5):
        a = 2 * math.pi * i / 5
        d = np.array([math.cos(a), math.sin(a), 0.0])
        perp = np.array([-math.sin(a), math.cos(a), 0.0])
        r0 = ROOT_R - 0.012                 # start a little inside the disc so the joint is hidden
        L = ARM_LEN + (ROOT_R - r0)
        pts = (r0 + L * t)[:, None] * d + np.array([0, 0, ARM_Z])
        tt = np.clip((np.linalg.norm(pts[:, :2], axis=1) - ROOT_R) / ARM_LEN, 0, 1)
        R = (radius or arm_radius)(tt) * girth
        Vt, Ft, tv, av = G.tube(pts, R, SIDES, squash=SQUASH, up=perp)
        dors = 1 - np.abs(((np.asarray(av) - 0.25 + 0.5) % 1.0) - 0.5) * 2      # 1 dorsal .. 0 ventral
        tvv = np.r_[np.repeat(tt, SIDES), 1.0]          # t of each ring's station (+ the tip cap)
        Vt = np.asarray(Vt)
        ga.add(Vt, Ft, uv(0, tvv, dors), np.stack([tvv, 0 * tvv, np.full(len(Vt), i / 9), np.clip(0.5 - Vt[:, 0], 0, 1)], -1))
        # arm spines: a stout pair at every ring, about 1.4 arm-widths long near the disc, shorter distally, held out
        # to the sides and a little forward and up (O. erinaceus: long, robust spines on every arm segment)
        for kk in range(2, RINGS - 1, spine_every):
            for sgn in (1, -1):
                Ls = spine_len[0] + spine_len[1] * (1 - tt[kk])
                b = pts[kk] + perp * sgn * R[kk] * 0.8
                tip = b + perp * sgn * Ls * spine_side + d * Ls * spine_fwd + np.array([0, 0, Ls * 0.25])
                Vc, Fc = G.cone(b, tip, max(0.0018, R[kk] * 0.2), 3)
                ga.add(Vc, Fc, uv(2, np.full(len(Vc), 0.5), np.r_[np.zeros(3), 1.0]),
                       [(tt[kk], 0, i / 9, float(np.clip(0.5 - v[0], 0, 1))) for v in Vc])
    return ga


def pivots():
    """extras.limbPivots: 10 x vec4 in glTF space (x, z, -y): arm k's root and its rest length; 5..9 unused."""
    out = []
    for k in range(10):
        if k < 5:
            a = 2 * math.pi * k / 5
            out += [ROOT_R * math.cos(a), ARM_Z, -ROOT_R * math.sin(a), ARM_LEN]
        else:
            out += [0.0, 0.0, 0.0, 0.0]
    return [float(v) for v in out]
