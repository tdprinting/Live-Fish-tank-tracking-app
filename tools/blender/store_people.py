"""The people and pets of the fish stores (core StoreLife.kt: who they are and what they do).

Each person is six nodes the renderer (render/StoreRoom.kt) poses every frame, all modelled standing at the origin
facing +z in figure cm (feet on y = 0), each about its own joint (Group pivot = the node's translation):

  P<slot>_Torso   hips, body, clothes, neck          pivot: the middle of the hips
  P<slot>_Head    head, hair, face, hats, glasses    pivot: the top of the neck
  P<slot>_ArmL/R  sleeve, forearm, hand              pivot: the shoulder; extras.grip = the hand (from the pivot)
  P<slot>_LegL/R  trouser leg, shoe                  pivot: the hip joint

Props (`Prop_<NAME>`, core Prop) are modelled with their grip at the origin; pets are `Pet_Body`, `Pet_Tail` (pivot
at the tail root) and `Pet_Sleep` (curled up). Everything is vertex painted (one draw call a node) and smooth shaded:
rounded, simple, friendly people (Tee 2026-10-21: "less blocky and chunky", "smoother graphics").
"""
import math

import numpy as np

from store_geo import Group, cylinder, ellipsoid as _ellipsoid

# Ellipsoid resolution (2026-10 perf pass: the people of a store were 40-60k triangles, validate_glb.py
# STORE_ROOM_BUDGET): the mid-size parts (eyes, ears, cheeks, nose, hands, sleeves, shoes: 10-16 segments) halved but
# never under 8 segments x 5 rings. The skull and the hair / hat shells over it (18+ segments) fit a few millimetres
# apart: they all drop to the same 0.7, so their facets stay alike and the skin does not poke through the hair. The
# 6 x 4 highlights are untouched.
DETAIL = 0.5
DETAIL_SHELL = 0.7


def ellipsoid(c, r, segs=12, rings=8):
    if segs >= 18:
        segs, rings = round(segs * DETAIL_SHELL), round(rings * DETAIL_SHELL)
    elif segs >= 10:
        segs, rings = max(8, round(segs * DETAIL)), max(min(rings, 5), round(rings * DETAIL))
    return _ellipsoid(c, r, segs, rings)

SKINS = ("#F6D2B5", "#E8B48C", "#C68E63", "#A56E46", "#7A4E33", "#F1C7A3")
SHOE = "#2A2A30"


def loft(pts, rx, rz, segs=16):
    """A smooth closed tube through centres [pts] with elliptical rings (radii [rx], [rz]); ends close on poles."""
    pts = np.asarray(pts, float)
    V = [tuple(pts[0])]
    n = len(pts)
    for i in range(n):
        t = pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)]
        t /= np.linalg.norm(t)
        a = np.array([1.0, 0.0, 0.0]) if abs(t[0]) < 0.9 else np.array([0.0, 0.0, 1.0])
        u = a - np.dot(a, t) * t
        u /= np.linalg.norm(u)
        w = np.cross(t, u)
        for k in range(segs):
            th = 2 * math.pi * k / segs
            V.append(tuple(pts[i] + u * rx[i] * math.cos(th) + w * rz[i] * math.sin(th)))
    V.append(tuple(pts[-1]))
    F = [(0, 1 + (k + 1) % segs, 1 + k) for k in range(segs)]
    for i in range(n - 1):
        b0, b1 = 1 + i * segs, 1 + (i + 1) * segs
        F += [(b0 + k, b0 + (k + 1) % segs, b1 + (k + 1) % segs, b1 + k) for k in range(segs)]
    last = len(V) - 1
    b = 1 + (n - 1) * segs
    F += [(last, b + k, b + (k + 1) % segs) for k in range(segs)]
    return np.array(V), F


def tilted(VF, c, ax, ang):
    """[VF] turned by [ang] about axis 'x' / 'y' / 'z' through [c]."""
    V, F = VF
    V = np.asarray(V, float) - c
    ca, sa = math.cos(ang), math.sin(ang)
    if ax == 'x':
        V = np.c_[V[:, 0], V[:, 1] * ca - V[:, 2] * sa, V[:, 1] * sa + V[:, 2] * ca]
    elif ax == 'y':
        V = np.c_[V[:, 0] * ca + V[:, 2] * sa, V[:, 1], -V[:, 0] * sa + V[:, 2] * ca]
    else:
        V = np.c_[V[:, 0] * ca - V[:, 1] * sa, V[:, 0] * sa + V[:, 1] * ca, V[:, 2]]
    return V + c, F


class Body:
    """Proportions of a person [h] cm tall. A kid has a bigger head for the body; [girth] widens the frame."""

    def __init__(self, h, kid=False, girth=1.0):
        self.h = h
        self.kid = kid
        self.hr = h * (0.09 if kid else 0.07)            # head radius
        self.neck_top = h - 2.02 * self.hr                 # where the head sits
        self.sh = self.neck_top - h * 0.028                # shoulder line
        self.hip = self.sh * (0.56 if kid else 0.6)
        self.knee = self.hip * 0.52
        self.w = self.sh * (0.17 if kid else 0.152) * girth   # half shoulder width
        self.d = self.w * 0.62 * (1.0 + (girth - 1.0) * 1.4)   # body depth
        self.legx = self.w * 0.42
        self.arm_r = self.w * 0.24
        self.shoulder = (self.w * 0.9, self.sh - self.arm_r * 0.5, 0.0)


def look(**kw):
    """One person's look (see CASTS): height, skin, hair style and colours of each piece of clothing."""
    d = dict(h=170, kid=False, girth=1.0, skin=SKINS[1], hair="#3A2618", style="short", shirt="#5B8DEF", sleeves="short",
             pants="#3B4252", legs="trousers", shoes=SHOE, extras={})
    d.update(kw)
    return d


# ============================================================== parts
def torso(slot, L, b):
    g = Group(f"P{slot}_Torso", smooth=True, pivot=(0, b.hip, 0))
    w, d, sh, hip, h = b.w, b.d, b.sh, b.hip, b.h
    ex = L["extras"]
    pants = L["pants"]
    # pelvis in the trousers (or skirt), the shirt from the waist up, a rounded shoulder line
    g.add(loft([(0, hip - w * 0.3, 0), (0, hip, 0), (0, hip + (sh - hip) * 0.28, 0)], [w * 0.55, w * 0.8, w * 0.76],
               [d * 0.6, d * 0.86, d * 0.82]), pants)
    chest = d * (1.0 if not L["kid"] else 0.92)
    g.add(loft([(0, hip + (sh - hip) * 0.18, 0), (0, hip + (sh - hip) * 0.45, d * 0.02), (0, hip + (sh - hip) * 0.78, d * 0.05),
                (0, sh - w * 0.05, 0), (0, sh + w * 0.2, -d * 0.05)],
               [w * 0.78, w * 0.8, w * 0.9, w * 0.88, w * 0.42], [d * 0.84, d * 0.86, chest * 0.96, d * 0.8, d * 0.42], 20), L["shirt"])
    # a belt line
    g.add(loft([(0, hip + (sh - hip) * 0.16, 0), (0, hip + (sh - hip) * 0.21, 0)], [w * 0.8, w * 0.8], [d * 0.87, d * 0.87], 20),
          ex.get("belt", "#2B2420"))
    if L["legs"] == "skirt":
        g.add(loft([(0, hip + 4, 0), (0, hip - (hip - b.knee) * 0.6, 0), (0, b.knee + 2, 0)], [w * 0.8, w * 1.0, w * 1.08],
                   [d * 0.88, d * 1.0, d * 1.1], 20), pants)
    # the neck
    g.add(loft([(0, sh - 2, 0), (0, b.neck_top + 1, 0)], [b.hr * 0.38, b.hr * 0.34], [b.hr * 0.38, b.hr * 0.34], 12), L["skin"])
    front = d * 0.86
    if "collar" in ex:
        for s in (-1, 1):
            g.add(ellipsoid((s * b.hr * 0.42, sh - 1, front * 0.72), (b.hr * 0.36, 1.6, b.hr * 0.22), 10, 4), ex["collar"])
    if "apron" in ex:
        g.add(loft([(0, b.knee + 6, front * 0.9), (0, hip + (sh - hip) * 0.4, front * 0.98), (0, hip + (sh - hip) * 0.82, front * 0.95)],
                   [w * 0.66, w * 0.7, w * 0.5], [1.0, 1.1, 1.0], 16), ex["apron"])
        g.add(ellipsoid((w * 0.28, hip + 4, front * 1.08), (w * 0.22, w * 0.14, 0.8), 10, 4), ex["apron"])
    for key in ("cardigan", "blazer", "vest", "hoodie"):
        if key not in ex:
            continue
        col = ex[key]
        bottom = hip - (w * 0.4 if key == "blazer" else w * 0.15)
        for s in (-1, 1):
            # an open jacket: two front panels over the shirt and a back
            g.add(loft([(s * w * 0.5, bottom, d * 0.1), (s * w * 0.52, hip + (sh - hip) * 0.5, d * 0.14), (s * w * 0.48, sh - 2, d * 0.04)],
                       [w * 0.36, w * 0.4, w * 0.38], [d * 0.84, d * 0.92, d * 0.84], 16), col)
            if key == "blazer":
                g.add(tilted(ellipsoid((s * w * 0.2, sh - (sh - hip) * 0.22, front * 0.96), (w * 0.12, (sh - hip) * 0.22, 0.7), 10, 4),
                             (s * w * 0.2, sh, front), 'z', s * 0.35), ex.get("lapel", col))
        g.add(loft([(0, bottom, -d * 0.2), (0, sh - 3, -d * 0.24)], [w * 0.82, w * 0.86], [d * 0.7, d * 0.62], 16), col)
        if key == "hoodie":
            g.add(ellipsoid((0, sh + 1, -d * 0.72), (w * 0.62, w * 0.3, d * 0.4), 14, 8), col)
    if "tie" in ex:
        g.add(loft([(0, sh - 3, front * 1.01), (0, hip + (sh - hip) * 0.35, front * 1.04)], [1.2, 2.0], [0.4, 0.5], 8), ex["tie"])
    if "bowtie" in ex:
        for s in (-1, 1):
            g.add(ellipsoid((s * 2.4, sh - 1.5, front * 0.98), (2.4, 1.4, 0.8), 8, 4), ex["bowtie"])
    if "lanyard" in ex:
        for s in (-1, 1):
            g.add(loft([(s * b.hr * 0.4, sh, front * 0.6), (s * 2.5, hip + (sh - hip) * 0.55, front * 1.02)], [0.5, 0.5], [0.3, 0.3], 6), ex["lanyard"])
        g.box((-3.2, hip + (sh - hip) * 0.38, front * 1.02), (3.2, hip + (sh - hip) * 0.55, front * 1.06), "#F7F7F2")
    if "nametag" in ex:
        g.box((w * 0.32, hip + (sh - hip) * 0.62, front * 0.99), (w * 0.62, hip + (sh - hip) * 0.72, front * 1.03), ex["nametag"])
    if "backpack" in ex:
        g.add(ellipsoid((0, hip + (sh - hip) * 0.55, -d * 1.05), (w * 0.62, (sh - hip) * 0.4, d * 0.45), 14, 8), ex["backpack"])
        for s in (-1, 1):
            g.add(loft([(s * w * 0.42, sh, -d * 0.4), (s * w * 0.46, sh - 1, d * 0.5), (s * w * 0.5, hip + (sh - hip) * 0.4, d * 0.86)],
                       [1.0, 1.0, 1.0], [0.6, 0.6, 0.6], 6), ex["backpack"])
    if "print" in ex:
        # a little logo or picture on the shirt front
        g.add(ellipsoid((0, hip + (sh - hip) * 0.62, front * 0.99), (w * 0.26, w * 0.2, 0.6), 12, 6), ex["print"])
    if "sharpie" in ex:
        pass   # behind the ear: on the head
    return g


def arm(slot, L, b, side):
    """One arm hanging from the shoulder, slightly bent, the hand a soft mitten with a thumb."""
    sx, sy, sz = b.shoulder
    piv = (side * sx, sy, sz)
    r = b.arm_r
    upper = (b.sh - b.hip) * 0.62
    fore = upper * 0.95
    elbow = (side * (sx + r * 0.25), sy - upper, sz - r * 0.15)
    wrist = (side * (sx + r * 0.35), elbow[1] - fore, sz + r * 0.6)
    hand = (wrist[0], wrist[1] - r * 0.95, wrist[2] + r * 0.1)
    g = Group(f"P{slot}_Arm{'L' if side < 0 else 'R'}", smooth=True, pivot=piv,
              extras=dict(grip=[hand[0] - piv[0], hand[1] - piv[1], hand[2] - piv[2]]))
    ex = L["extras"]
    sleeve = ex.get("cardigan") or ex.get("blazer") or ex.get("hoodie") or L["shirt"]
    long_sleeves = L["sleeves"] == "long" or any(k in ex for k in ("cardigan", "blazer", "hoodie"))
    # the shoulder ball
    g.add(ellipsoid((piv[0] - side * r * 0.1, sy + r * 0.2, sz), (r * 1.12, r * 1.05, r * 1.1), 14, 10), sleeve)
    if long_sleeves:
        g.add(loft([(piv[0], sy, sz), (elbow[0], elbow[1] + r * 0.2, elbow[2]), wrist], [r * 1.02, r * 0.86, r * 0.74],
                   [r * 1.02, r * 0.86, r * 0.74], 14), sleeve)
        g.add(loft([(wrist[0], wrist[1] + 2.5, wrist[2]), (wrist[0], wrist[1] - 0.3, wrist[2])], [r * 0.8, r * 0.8], [r * 0.8, r * 0.8], 12),
              ex.get("cuff", sleeve))
    else:
        mid = ((piv[0] + elbow[0]) / 2, (sy + elbow[1]) / 2 - upper * 0.1, (sz + elbow[2]) / 2)
        g.add(loft([(piv[0], sy, sz), mid], [r * 1.06, r * 1.0], [r * 1.06, r * 1.0], 14), sleeve)
        g.add(loft([mid, (elbow[0], elbow[1], elbow[2]), wrist], [r * 0.82, r * 0.74, r * 0.6], [r * 0.82, r * 0.74, r * 0.6], 12), L["skin"])
    # the hand: a soft mitten and a thumb toward the body's front
    g.add(ellipsoid(hand, (r * 0.62, r * 1.0, r * 0.82), 12, 8), L["skin"])
    g.add(ellipsoid((hand[0] - side * r * 0.42, hand[1] + r * 0.2, hand[2] + r * 0.45), (r * 0.26, r * 0.48, r * 0.26), 8, 6), L["skin"])
    if "watch" in ex and side < 0:
        g.add(loft([(wrist[0], wrist[1] + 1.4, wrist[2]), (wrist[0], wrist[1], wrist[2])], [r * 0.7, r * 0.7], [r * 0.7, r * 0.7], 10), ex["watch"])
    return g


def leg(slot, L, b, side):
    x = side * b.legx
    piv = (x, b.hip, 0.0)
    g = Group(f"P{slot}_Leg{'L' if side < 0 else 'R'}", smooth=True, pivot=piv)
    h, hip, knee = b.h, b.hip, b.knee
    r = b.w * 0.36 * (1.0 + (L["girth"] - 1.0) * 0.8)
    ankle = h * 0.045
    pants = L["pants"]
    style = L["legs"]
    if style == "skirt":
        # bare legs under the skirt
        g.add(loft([(x, knee + 4, 0), (x, knee, r * 0.05), (x, knee * 0.5, -r * 0.05), (x, ankle, 0)], [r * 0.6, r * 0.62, r * 0.6, r * 0.42],
                   [r * 0.6, r * 0.62, r * 0.64, r * 0.42], 12), L["skin"])
    elif style == "shorts":
        g.add(loft([(x, hip + r * 0.3, 0), (x, hip * 0.86, 0), (x, knee + r * 1.0, r * 0.05)], [r * 0.6, r * 1.08, r * 1.0],
                   [r * 0.6, r * 1.08, r * 1.0], 14), pants)
        g.add(loft([(x, knee + r * 1.4, r * 0.05), (x, knee, r * 0.1), (x, knee * 0.5, -r * 0.05), (x, ankle, 0)],
                   [r * 0.7, r * 0.66, r * 0.68, r * 0.45], [r * 0.7, r * 0.66, r * 0.7, r * 0.45], 12), L["skin"])
    else:
        g.add(loft([(x, hip + r * 0.4, 0), (x, hip * 0.82, 0), (x, knee, r * 0.08), (x, knee * 0.52, -r * 0.04), (x, ankle + 1, 0)],
                   [r * 0.55, r * 1.0, r * 0.74, r * 0.76, r * 0.6], [r * 0.55, r * 1.0, r * 0.74, r * 0.8, r * 0.6], 14), pants)
    if "socks" in L["extras"] and style != "trousers":
        g.add(loft([(x, ankle + 5, 0), (x, ankle, 0)], [r * 0.48, r * 0.48], [r * 0.48, r * 0.48], 10), L["extras"]["socks"])
    shoes = L["shoes"]
    if shoes == "flipflops":
        g.add(ellipsoid((x, h * 0.008, r * 0.8), (r * 0.62, h * 0.008, r * 1.5), 14, 6), "#F2A541")
        g.add(ellipsoid((x, h * 0.024, r * 0.62), (r * 0.5, h * 0.018, r * 1.1), 12, 6), L["skin"])
    else:
        g.add(ellipsoid((x, h * 0.024, r * 0.75), (r * 0.64, h * 0.026, r * 1.45), 16, 8), shoes)
        g.add(ellipsoid((x, h * 0.012, r * 0.8), (r * 0.68, h * 0.012, r * 1.5), 16, 4), L["extras"].get("sole", "#EDEDED" if shoes != SHOE else "#151518"))
    return g


def head(slot, L, b):
    """A round head on the neck top: hair by [L.style], a friendly face, hats and glasses."""
    piv = (0.0, b.neck_top, 0.0)
    g = Group(f"P{slot}_Head", smooth=True, pivot=piv)
    r = b.hr
    x, y, z = 0.0, b.neck_top + r * 0.95, 0.0
    skin, hair, ex = L["skin"], L["hair"], L["extras"]
    # the head, a touch wider at the cheeks, and the jaw
    g.add(ellipsoid((x, y, z), (r * 0.9, r, r * 0.95), 22, 16), skin)
    g.add(ellipsoid((x, y - r * 0.38, z + r * 0.12), (r * 0.74, r * 0.55, r * 0.78), 18, 10), skin)
    # ears
    for s in (-1, 1):
        g.add(ellipsoid((x + s * r * 0.9, y - r * 0.05, z - r * 0.02), (r * 0.13, r * 0.22, r * 0.15), 10, 6), skin)
    # the eyes: whites, dark irises and a glint; brows in the hair colour
    for s in (-1, 1):
        ex_, ey = x + s * r * 0.33, y + r * 0.02
        g.add(ellipsoid((ex_, ey, z + r * 0.86), (r * 0.16, r * 0.18, r * 0.08), 12, 8), "#FFFFFF")
        g.add(ellipsoid((ex_ + s * r * 0.01, ey - r * 0.02, z + r * 0.92), (r * 0.1, r * 0.12, r * 0.05), 10, 6), ex.get("eyes", "#2A1A12"))
        g.add(ellipsoid((ex_ - s * r * 0.03, ey + r * 0.04, z + r * 0.955), (r * 0.03, r * 0.03, r * 0.015), 6, 4), "#FFFFFF")
        brow = ex.get("brows", hair if L["style"] != "bald" else "#9A9A9A")
        g.add(tilted(ellipsoid((ex_, ey + r * 0.26, z + r * 0.86), (r * 0.17, r * 0.045, r * 0.05), 10, 4), (ex_, ey + r * 0.26, z), 'z', -s * 0.12), brow)
        g.add(ellipsoid((x + s * r * 0.56, y - r * 0.3, z + r * 0.72), (r * 0.17, r * 0.1, r * 0.06), 10, 6), "#F2A08F")
    # nose and smile
    g.add(ellipsoid((x, y - r * 0.16, z + r * 0.95), (r * 0.11, r * 0.13, r * 0.1), 10, 6), skin)
    mouth = []
    for k in range(7):
        t = (k / 6 - 0.5) * 1.6
        mouth.append((x + math.sin(t) * r * 0.22, y - r * 0.47 + (1 - math.cos(t)) * r * 0.12, z + r * 0.86 + math.cos(t) * r * 0.04))
    g.add(loft(mouth, [r * 0.04] * 7, [r * 0.035] * 7, 6), ex.get("lips", "#A8473A"))
    _hair(g, L["style"], (x, y, z), r, hair, ex)
    if "beard" in ex:
        g.add(ellipsoid((x, y - r * 0.62, z + r * 0.4), (r * 0.64, r * 0.36, r * 0.56), 16, 10), ex["beard"])
        g.add(ellipsoid((x, y - r * 0.47, z + r * 0.9), (r * 0.12, r * 0.08, r * 0.05), 8, 4), ex.get("lips", "#A8473A"))
    if "moustache" in ex:
        for s in (-1, 1):
            g.add(tilted(ellipsoid((x + s * r * 0.16, y - r * 0.33, z + r * 0.93), (r * 0.17, r * 0.06, r * 0.05), 10, 4), (x, y - r * 0.33, z), 'z', s * 0.25), ex["moustache"])
    if "glasses" in ex:
        frame = ex["glasses"]
        for s in (-1, 1):
            ring = [(x + s * r * 0.33 + math.cos(a) * r * 0.22, y + r * 0.02 + math.sin(a) * r * 0.19, z + r * 1.0) for a in np.linspace(0, 2 * math.pi, 13)]
            g.add(loft(ring, [r * 0.03] * 13, [r * 0.03] * 13, 6), frame)
            g.add(loft([(x + s * r * 0.55, y + r * 0.04, z + r * 0.95), (x + s * r * 0.88, y + r * 0.02, z + r * 0.1)], [r * 0.025] * 2, [r * 0.025] * 2, 6), frame)
        g.add(loft([(x - r * 0.11, y + r * 0.04, z + r * 1.0), (x + r * 0.11, y + r * 0.04, z + r * 1.0)], [r * 0.025] * 2, [r * 0.025] * 2, 6), frame)
    if "earrings" in ex:
        for s in (-1, 1):
            g.add(ellipsoid((x + s * r * 0.92, y - r * 0.3, z), (r * 0.06, r * 0.06, r * 0.06), 6, 4), ex["earrings"])
    if "cap" in ex:
        g.add(ellipsoid((x, y + r * 0.42, z - r * 0.04), (r * 1.0, r * 0.66, r * 1.0), 20, 10), ex["cap"])
        g.add(ellipsoid((x, y + r * 0.36, z + r * 0.86), (r * 0.72, r * 0.08, r * 0.58), 16, 6), ex["cap"])
        g.add(ellipsoid((x, y + r * 1.04, z), (r * 0.1, r * 0.06, r * 0.1), 8, 4), ex["cap"])
    if "visor" in ex:
        band = [(x + math.sin(a) * r * 0.97, y + r * 0.4, z + math.cos(a) * r * 0.99) for a in np.linspace(0, 2 * math.pi, 21)]
        g.add(loft(band, [r * 0.07] * 21, [r * 0.12] * 21, 6), ex["visor"])
        g.add(ellipsoid((x, y + r * 0.38, z + r * 0.92), (r * 0.7, r * 0.06, r * 0.5), 16, 6), ex["visor"])
    if "beanie" in ex:
        g.add(ellipsoid((x, y + r * 0.38, z - r * 0.04), (r * 1.0, r * 0.78, r * 1.0), 20, 10), ex["beanie"])
        g.add(loft([(x, y + r * 0.1, z), (x, y + r * 0.3, z)], [r * 1.0, r * 1.0], [r * 1.0, r * 1.0], 20), ex.get("beanie_band", ex["beanie"]))
    if "sunhat" in ex:
        g.add(ellipsoid((x, y + r * 0.5, z), (r * 0.92, r * 0.62, r * 0.92), 20, 10), ex["sunhat"])
        g.add(ellipsoid((x, y + r * 0.3, z), (r * 1.75, r * 0.06, r * 1.75), 24, 6), ex["sunhat"])
        g.add(loft([(x, y + r * 0.36, z), (x, y + r * 0.46, z)], [r * 0.94, r * 0.92], [r * 0.94, r * 0.92], 20), ex.get("ribbon", "#222222"))
    if "headlamp" in ex:
        g.add(loft([(x, y + r * 0.3, z), (x, y + r * 0.42, z)], [r * 0.98, r * 0.98], [r * 1.0, r * 1.0], 20), "#202226")
        g.add(ellipsoid((x, y + r * 0.38, z + r * 1.0), (r * 0.2, r * 0.16, r * 0.12), 10, 6), ex["headlamp"])
    if "hairclip" in ex:
        g.add(ellipsoid((x + r * 0.55, y + r * 0.6, z + r * 0.45), (r * 0.18, r * 0.1, r * 0.08), 8, 4), ex["hairclip"])
    if "sharpie" in ex:
        g.add(tilted(cylinder((x + r * 0.95, y + r * 0.0, z - r * 0.2), r * 0.06, r * 0.7, 8), (x + r * 0.95, y, z), 'x', 1.2), ex["sharpie"])
    return g


def _hair(g, style, c, r, col, ex):
    x, y, z = c
    if style == "bald":
        return
    if style == "ring":
        # bald on top, a ring of hair round the back and sides
        g.add(ellipsoid((x, y - r * 0.05, z - r * 0.25), (r * 0.95, r * 0.42, r * 0.82), 18, 10), col)
        return
    if style == "buzz":
        g.add(ellipsoid((x, y + r * 0.18, z - r * 0.06), (r * 0.94, r * 0.86, r * 0.98), 20, 12), col)
        return
    if style == "curly":
        g.add(ellipsoid((x, y + r * 0.25, z - r * 0.1), (r * 1.0, r * 0.88, r * 1.0), 18, 12), col)
        for k in range(16):
            a = k * 2.399
            u = 0.25 + 0.75 * (k / 15)
            p = (x + math.cos(a) * r * 0.86 * u, y + r * (0.95 - 0.55 * u), z - r * 0.18 + math.sin(a) * r * 0.86 * u)
            g.add(ellipsoid(p, (r * 0.34, r * 0.32, r * 0.34), 8, 6), col)
        return
    # a soft cap of hair over the top and back, a side part at the front
    g.add(ellipsoid((x, y + r * 0.26, z - r * 0.12), (r * 0.98, r * 0.84, r * 0.98), 20, 12), col)
    g.add(tilted(ellipsoid((x + r * 0.2, y + r * 0.62, z + r * 0.5), (r * 0.62, r * 0.26, r * 0.42), 14, 8), (x, y + r * 0.6, z + r * 0.5), 'z', -0.25), col)
    if style == "spiky":
        for k in range(7):
            a = -1.2 + k * 0.4
            g.add(tilted(ellipsoid((x + math.sin(a) * r * 0.5, y + r * 0.95, z + math.cos(a) * r * 0.2), (r * 0.16, r * 0.34, r * 0.16), 8, 6),
                         (x, y + r * 0.8, z), 'z', -a * 0.4), col)
    elif style == "long":
        g.add(ellipsoid((x, y - r * 0.4, z - r * 0.42), (r * 1.0, r * 1.15, r * 0.62), 18, 10), col)
        for s in (-1, 1):
            g.add(ellipsoid((x + s * r * 0.82, y - r * 0.35, z + r * 0.05), (r * 0.26, r * 0.9, r * 0.45), 10, 8), col)
    elif style == "bob":
        g.add(ellipsoid((x, y - r * 0.1, z - r * 0.2), (r * 1.06, r * 0.82, r * 0.92), 20, 12), col)
        g.add(ellipsoid((x, y + r * 0.55, z + r * 0.62), (r * 0.8, r * 0.22, r * 0.36), 14, 6), col)
    elif style == "bun":
        g.add(ellipsoid((x, y + r * 0.8, z - r * 0.62), (r * 0.42, r * 0.4, r * 0.42), 12, 8), col)
    elif style == "ponytail":
        g.add(ellipsoid((x, y + r * 0.35, z - r * 0.98), (r * 0.18, r * 0.16, r * 0.14), 8, 6), ex.get("hairtie", "#E04A6A"))
        g.add(loft([(x, y + r * 0.3, z - r * 1.0), (x, y - r * 0.2, z - r * 1.18), (x, y - r * 0.9, z - r * 1.0)],
                   [r * 0.24, r * 0.26, r * 0.12], [r * 0.24, r * 0.26, r * 0.12], 10), col)
    elif style == "pigtails":
        for s in (-1, 1):
            g.add(ellipsoid((x + s * r * 0.95, y + r * 0.1, z - r * 0.3), (r * 0.16, r * 0.14, r * 0.14), 8, 6), ex.get("hairtie", "#E04A6A"))
            g.add(loft([(x + s * r * 1.0, y + r * 0.05, z - r * 0.3), (x + s * r * 1.25, y - r * 0.4, z - r * 0.3), (x + s * r * 1.15, y - r * 0.85, z - r * 0.2)],
                       [r * 0.24, r * 0.22, r * 0.1], [r * 0.24, r * 0.22, r * 0.1], 10), col)


def person(slot, L):
    """The six nodes of person [slot] dressed as [L] (see [look])."""
    b = Body(L["h"], L["kid"], L["girth"])
    return [torso(slot, L, b), head(slot, L, b), arm(slot, L, b, -1), arm(slot, L, b, 1), leg(slot, L, b, -1), leg(slot, L, b, 1)]


# ============================================================== who (core StoreLife.kt CASTS, slot order)
CASTS = {
    "shellys": [
        # Shelly, the owner: grey bun, glasses, a purple cardigan over a teal shop polo
        look(h=163, skin=SKINS[0], hair="#C9C4BE", style="bun", shirt="#2BA7A0", sleeves="short", pants="#4A4F63", girth=1.1,
             extras=dict(cardigan="#8E5BB8", glasses="#5A3A7A", earrings="#E2C46A", nametag="#FFFFFF", lips="#B84A5A")),
        # Max, the after-school helper: teal polo and visor, apron
        look(h=172, skin=SKINS[3], hair="#1C1410", style="short", shirt="#2BA7A0", pants="#2F3542", shoes="#E8E8E8", sole="#FFFFFF",
             extras=dict(visor="#1B6E6A", apron="#1B6E6A", collar="#2BA7A0", nametag="#FFFFFF")),
        # Grandpa Joe: plaid-ish red shirt, vest, a cap, white moustache
        look(h=168, skin=SKINS[5], hair="#E8E6E0", style="ring", shirt="#B8403A", sleeves="long", pants="#6B5A45", girth=1.15,
             extras=dict(vest="#4C5A3A", cap="#2E4A6E", moustache="#EDEBE6", brows="#EDEBE6", glasses="#3A3A3A", watch="#C9A646")),
        # Ana: yellow cardigan, jeans, ponytail
        look(h=166, skin=SKINS[2], hair="#3A2214", style="ponytail", shirt="#FFFFFF", pants="#3D5A8A", shoes="#C0563E",
             extras=dict(cardigan="#F2C14E", earrings="#F2F2F2", lips="#B5523F", hairtie="#F2C14E")),
        # Lily, six: pink dress, pigtails
        look(h=112, kid=True, skin=SKINS[2], hair="#3A2214", style="pigtails", shirt="#FF8FB1", pants="#FF8FB1", legs="skirt",
             shoes="#FFD84A", extras=dict(print="#FFFFFF", hairtie="#FFD84A", socks="#FFFFFF")),
    ],
    "frag_shack": [
        # Kai: beanie, beard, open aloha shirt, board shorts, flip-flops
        look(h=180, skin=SKINS[3], hair="#2A1A10", style="long", shirt="#2F9CCB", pants="#F28C28", legs="shorts", shoes="flipflops",
             extras=dict(beanie="#3A3F48", beanie_band="#2A2E36", beard="#2A1A10", print="#F7E26B", sharpie="#111111", watch="#202226")),
        # Jess: glasses, ponytail, black tee, headlamp, cargo shorts
        look(h=165, skin=SKINS[0], hair="#B5562A", style="ponytail", shirt="#1E1F24", pants="#6E6A55", legs="shorts", shoes="#3A3F48",
             extras=dict(glasses="#1A1A1A", headlamp="#BFE9FF", print="#7A5CFF", socks="#7A5CFF", hairtie="#7A5CFF")),
        # Mia the zoa collector: purple hoodie, bob, backpack
        look(h=160, skin=SKINS[4], hair="#141010", style="bob", shirt="#7A5CFF", sleeves="long", pants="#2E3440",
             extras=dict(hoodie="#8E6BFF", backpack="#3DD9B8", earrings="#3DD9B8", hairclip="#FF6FA8", lips="#8A3A4A")),
        # Sam: green tee, cap backwards-ish, jeans
        look(h=176, skin=SKINS[1], hair="#5A3A22", style="short", shirt="#5BAA5A", pants="#3D5A8A",
             extras=dict(cap="#C0563E", print="#FFFFFF", watch="#202226")),
        # Theo, eight: dino tee, shorts, curly hair
        look(h=126, kid=True, skin=SKINS[4], hair="#141010", style="curly", shirt="#FF9A3A", pants="#3B4252", legs="shorts",
             shoes="#2C6BFF", extras=dict(print="#3E9B4A", socks="#FFFFFF")),
        # Ben, Theo's dad: flannel, glasses
        look(h=183, skin=SKINS[4], hair="#141010", style="buzz", shirt="#A33A3A", sleeves="long", pants="#5A5145", girth=1.1,
             extras=dict(glasses="#2A2A2A", beard="#141010", watch="#C9C9C9")),
    ],
    "big_blue": [
        # Marcus, the manager: blue polo, lanyard, khakis
        look(h=182, skin=SKINS[4], hair="#141010", style="buzz", shirt="#1F6FD1", pants="#C8B48A", shoes="#3A2A20", girth=1.15,
             extras=dict(collar="#1F6FD1", lanyard="#0F4C9E", beard="#141010", watch="#C9C9C9")),
        # Priya, the fish room aquarist: blue polo, black apron, long braid
        look(h=162, skin=SKINS[3], hair="#141010", style="long", shirt="#1F6FD1", pants="#2E3440",
             extras=dict(apron="#1C1E23", collar="#1F6FD1", nametag="#FFFFFF", earrings="#E2C46A")),
        # Leo, the stocker: grey hoodie under a blue vest, cap
        look(h=178, skin=SKINS[1], hair="#6B4A2B", style="short", shirt="#8C939C", sleeves="long", pants="#2E3440", shoes="#C9C9C9",
             extras=dict(vest="#1F6FD1", cap="#1F6FD1")),
        # Dave, reef club: club tee with a coral print, cargo shorts, socks with sandals energy
        look(h=175, skin=SKINS[5], hair="#8A6A4A", style="ring", shirt="#FF7A5A", pants="#7A7460", legs="shorts", girth=1.3,
             extras=dict(print="#FFD24A", beard="#8A6A4A", glasses="#2A2A2A", socks="#FFFFFF", watch="#202226")),
        # Rosa: floral blouse, grey bob, sun hat
        look(h=158, skin=SKINS[1], hair="#B9B4AE", style="bob", shirt="#E86A8A", sleeves="long", pants="#3B4252", girth=1.1,
             extras=dict(sunhat="#F4E3C1", ribbon="#E86A8A", glasses="#B05A7A", earrings="#E2C46A")),
        # Jamal: orange hoodie, spiky hair
        look(h=174, skin=SKINS[4], hair="#141010", style="spiky", shirt="#FFFFFF", pants="#2E3440", shoes="#E8463C", sole="#FFFFFF",
             extras=dict(hoodie="#F28C28", backpack="#2E3440")),
        # Zoe, seven: yellow raincoat-ish top, pigtails
        look(h=118, kid=True, skin=SKINS[0], hair="#E0B04A", style="pigtails", shirt="#FFD24A", sleeves="long", pants="#5B8DEF",
             shoes="#E8463C", extras=dict(hairtie="#5B8DEF", print="#FF7A1A")),
        # Mei, Zoe's mum: green jacket, long hair
        look(h=164, skin=SKINS[0], hair="#141010", style="long", shirt="#FFFFFF", pants="#2E3440", shoes="#8A5A3A",
             extras=dict(blazer="#4C8A6A", earrings="#F2F2F2", watch="#C9A646")),
    ],
    "deep_reef": [
        # Celeste, the curator: black blazer, sleek bob, gold earrings
        look(h=170, skin=SKINS[0], hair="#141010", style="bob", shirt="#F4F1EA", sleeves="long", pants="#141418", shoes="#141418",
             extras=dict(blazer="#141418", lapel="#22222A", earrings="#E2C46A", lips="#A0283A", nametag="#C9A646")),
        # Victor, the greeter: a dark suit and a bow tie
        look(h=184, skin=SKINS[3], hair="#3A3A3A", style="short", shirt="#F4F1EA", sleeves="long", pants="#1C1C24", shoes="#141418",
             extras=dict(blazer="#1C1C24", lapel="#2A2A34", bowtie="#7A1630", moustache="#3A3A3A", watch="#C9A646")),
        # Ms. Okafor: an emerald dress, a gold necklace look, a wide hat
        look(h=172, skin=SKINS[4], hair="#141010", style="bun", shirt="#0F6E5A", sleeves="long", pants="#0F6E5A", legs="skirt",
             shoes="#C9A646", extras=dict(sunhat="#141418", ribbon="#C9A646", earrings="#E2C46A", print="#E2C46A", lips="#8A2A3A")),
        # Julian, designer: grey blazer, glasses, tablet
        look(h=178, skin=SKINS[1], hair="#7A5A3A", style="short", shirt="#2E3440", sleeves="long", pants="#3A3F48", shoes="#5A3A22",
             extras=dict(blazer="#8C939C", glasses="#141418", beard="#7A5A3A")),
        # Nadia: red dress, long hair
        look(h=166, skin=SKINS[2], hair="#2A1A12", style="long", shirt="#B8243A", sleeves="short", pants="#B8243A", legs="skirt",
             shoes="#141418", extras=dict(earrings="#E2C46A", lips="#8A1A2A")),
        # Omar: navy sweater, neat beard
        look(h=180, skin=SKINS[2], hair="#141010", style="short", shirt="#22325A", sleeves="long", pants="#C8C2B4", shoes="#3A2A20",
             extras=dict(beard="#141010", watch="#C9C9C9", collar="#F4F1EA")),
    ],
}


# ============================================================== what they hold (core Prop; grip at the origin)
def props():
    out = []

    def p(name):
        g = Group(f"Prop_{name}", smooth=True)
        out.append(g)
        return g

    g = p("NET")
    g.add(cylinder((0, -50, 0), 0.7, 54, 8), "#2B3A4F")
    ring = [(math.cos(a) * 8, -56, math.sin(a) * 6) for a in np.linspace(0, 2 * math.pi, 17)]
    g.add(loft(ring, [0.5] * 17, [0.5] * 17, 6), "#2B3A4F")
    g.add(ellipsoid((0, -60, 0), (7.6, 6, 5.6), 12, 8), "#4FAF6A")
    g = p("CUP")
    g.add(cylinder((0, -4, 2), 3.6, 9, 14), "#F4F1EA")
    g.add(cylinder((0, 4.6, 2), 3.7, 1.2, 14), "#7A4A2A")
    g = p("PHONE")
    g.box((-3.6, -3, 0.5), (3.6, 11, 1.4), "#1C1D22")
    g.box((-3.2, -2.5, 0.4), (3.2, 10.5, 0.5), "#5FC8FF")
    g = p("BAG")
    g.add(ellipsoid((0, -14, 2), (6.5, 11, 6.5), 14, 10), "#DDF2FF")
    g.add(cylinder((0, -3, 2), 1.4, 3, 8), "#E23B3B")
    g.add(ellipsoid((0, -15, 2), (3, 1.6, 0.8), 8, 5), "#FF7A1A")
    g = p("JUG")
    g.add(cylinder((0, -38, 3), 13, 36, 16), "#5FA8E8")
    g.add(cylinder((0, -2, 3), 3, 2.5, 10), "#1F4F9A")
    g = p("BUCKET")
    g.add(cylinder((0, -30, 2), 13, 24, 18), "#F4F4F0")
    g.add(cylinder((0, -10, 2), 13.4, 3, 18), "#1F6FD1")
    arc = [(math.cos(a) * 13, -6 + math.sin(a) * 6, 2) for a in np.linspace(0, math.pi, 9)]
    g.add(loft(arc, [0.4] * 9, [0.4] * 9, 6), "#888C92")
    g = p("BROOM")
    g.add(cylinder((0, -92, 0), 1.1, 102, 8), "#C9A06A")
    g.box((-14, -104, -4), (14, -92, 4), "#3A6EA8")
    g.box((-15, -108, -3.5), (15, -104, 3.5), "#E8D5A0")
    g = p("TABLET")
    g.box((-11, -1, 2), (11, 15, 3), "#1C1D22")
    g.box((-10, 0, 1.9), (10, 14, 2), "#8FD8FF")
    g = p("FOOD")
    g.add(cylinder((0, -2, 2), 2.8, 10, 12), "#E8463C")
    g.add(cylinder((0, 8, 2), 2.9, 1.5, 12), "#F3C33B")
    g = p("FRAG")
    g.add(cylinder((0, -2, 3), 1.4, 3, 8), "#D9CDB8")
    for k in range(4):
        a = k * 1.6
        g.add(ellipsoid((math.cos(a) * 1.2, 2.2, 3 + math.sin(a) * 1.2), (1.4, 0.5, 1.4), 8, 4), "#7DFF6A")
    g = p("BOX")
    g.box((-18, -14, -4), (18, 12, 26), "#C49A6C")
    g.box((-18.2, -2, -4.2), (18.2, 2, 26.2), "#D8B888")
    g = p("VIAL")
    g.add(cylinder((0, -3, 2), 1.1, 9, 10), "#DDEFFF")
    g.add(cylinder((0, -2.8, 2), 1.0, 5, 10), "#6AC3FF")
    g.add(cylinder((0, 6, 2), 1.2, 1.2, 10), "#E8463C")
    return out


# ============================================================== pets (core StorePet)
def pet(kind):
    """The shop pet: standing body (Pet_Body, a walker the renderer bobs), its tail (Pet_Tail, swishes about its
    root) and curled up asleep (Pet_Sleep). Facing +z, feet on y = 0."""
    body = Group("Pet_Body", smooth=True)
    sleep = Group("Pet_Sleep", smooth=True)
    if kind == "cat":
        fur, belly, dark = "#E8A04A", "#FFF1DC", "#B86A22"
        body.add(ellipsoid((0, 16, 0), (8, 8.5, 15), 16, 10), fur)
        body.add(ellipsoid((0, 13, 3), (6, 5, 11), 12, 8), belly)
        for k in range(3):
            body.add(ellipsoid((0, 23.6, -6 + k * 6), (6.5, 1.2, 1.4), 10, 4), dark)
        for sx in (-1, 1):
            for sz in (-1, 1):
                body.add(loft([(sx * 4.4, 12, sz * 9), (sx * 4.6, 0.9, sz * 9.6)], [2.1, 1.7], [2.1, 1.7], 10), fur)
                body.add(ellipsoid((sx * 4.6, 0.9, sz * 9.6 + 1), (2, 1, 2.4), 8, 4), belly)
        hx, hy, hz = 0, 25, 15
        body.add(ellipsoid((hx, hy, hz), (6.8, 6, 6), 16, 10), fur)
        body.add(ellipsoid((hx, hy - 1.8, hz + 4.2), (3.4, 2.4, 2.2), 10, 6), belly)
        for s in (-1, 1):
            body.add(tilted(ellipsoid((hx + s * 4, hy + 5.4, hz - 0.5), (2, 3.2, 1.2), 8, 6), (hx + s * 4, hy + 4, hz), 'z', -s * 0.3), fur)
            body.add(ellipsoid((hx + s * 2.4, hy + 0.8, hz + 5.2), (1.2, 1.4, 0.6), 8, 6), "#4FBF6A")
            body.add(ellipsoid((hx + s * 2.4, hy + 0.8, hz + 5.6), (0.4, 1.1, 0.3), 6, 4), "#141010")
        body.add(ellipsoid((hx, hy - 1.2, hz + 6.3), (0.8, 0.6, 0.5), 6, 4), "#E87A8A")
        tail = Group("Pet_Tail", smooth=True, pivot=(0, 18, -14))
        tail.add(loft([(0, 18, -14), (0, 24, -22), (0, 34, -24), (2, 40, -20)], [1.6, 1.5, 1.4, 1.1], [1.6, 1.5, 1.4, 1.1], 10), fur)
        # asleep: a loaf with the tail round it
        sleep.add(ellipsoid((0, 7, 0), (11, 7, 13), 16, 10), fur)
        sleep.add(ellipsoid((0, 9, 9), (6.4, 5.4, 5.6), 14, 8), fur)
        for s in (-1, 1):
            sleep.add(ellipsoid((s * 3.8, 13.6, 8.4), (1.8, 2.6, 1.0), 8, 6), fur)
            sleep.add(ellipsoid((s * 2.3, 9.6, 14.2), (1.2, 0.25, 0.4), 6, 4), "#3A2414")
        arc = [(math.sin(a) * 12, 2.5, math.cos(a) * 13) for a in np.linspace(1.0, 4.2, 9)]
        sleep.add(loft(arc, [1.8] * 9, [1.8] * 9, 10), fur)
        for k in range(3):
            sleep.add(ellipsoid((0, 13.4, -6 + k * 5), (7, 1.2, 1.4), 10, 4), dark)
    else:
        # a corgi
        fur, white, dark = "#D9893A", "#FFF6EA", "#3A2414"
        body.add(ellipsoid((0, 17, 0), (9.5, 8.5, 20), 16, 10), fur)
        body.add(ellipsoid((0, 14, 4), (7.5, 6.5, 15), 14, 8), white)
        for sx in (-1, 1):
            for sz in (-1, 1):
                body.add(loft([(sx * 5.4, 11, sz * 13), (sx * 5.6, 1.2, sz * 13.4)], [2.6, 2.3], [2.6, 2.3], 10), fur if sz < 0 else white)
                body.add(ellipsoid((sx * 5.6, 1.2, sz * 13.4 + 1), (2.6, 1.2, 3), 8, 4), white)
        hx, hy, hz = 0, 26, 21
        body.add(ellipsoid((hx, hy, hz), (7.6, 7, 7.4), 16, 10), fur)
        body.add(ellipsoid((hx, hy - 3, hz + 5.8), (4, 3.2, 4.4), 12, 8), white)
        body.add(ellipsoid((hx, hy - 1.6, hz + 10), (1.4, 1.1, 1.0), 8, 6), dark)
        body.add(ellipsoid((hx, hy - 4.8, hz + 8.2), (2.2, 1.2, 0.8), 8, 4), "#E87A8A")
        for s in (-1, 1):
            body.add(tilted(ellipsoid((hx + s * 4.6, hy + 7.4, hz - 1), (2.6, 5, 1.2), 10, 6), (hx + s * 4.6, hy + 5, hz), 'z', -s * 0.32), fur)
            body.add(ellipsoid((hx + s * 2.8, hy + 1.4, hz + 6.2), (1.0, 1.1, 0.6), 8, 6), dark)
        body.add(loft([(0, hy - 6, hz - 3.5), (0, hy - 6.5, hz + 2)], [6.4, 5.8], [5.2, 4.8], 14), "#E23B3B")
        tail = Group("Pet_Tail", smooth=True, pivot=(0, 19, -19))
        tail.add(ellipsoid((0, 21, -21), (3.4, 4, 3.4), 10, 8), fur)
        sleep.add(ellipsoid((0, 8, 0), (13, 8, 18), 16, 10), fur)
        sleep.add(ellipsoid((0, 9, 18), (7, 6.4, 7), 14, 8), fur)
        sleep.add(ellipsoid((0, 6.5, 23), (4, 3, 4), 12, 6), white)
        sleep.add(ellipsoid((0, 7.4, 27), (1.2, 1.0, 0.9), 8, 6), dark)
        for s in (-1, 1):
            sleep.add(ellipsoid((s * 4.4, 15.4, 16.5), (2.4, 4.4, 1.1), 8, 6), fur)
            sleep.add(ellipsoid((s * 2.6, 10.6, 23.6), (1.0, 0.25, 0.4), 6, 4), dark)
            sleep.add(ellipsoid((s * 4, 2.4, 20), (2.4, 2, 4.8), 8, 6), white)
    return [body, tail, sleep]
