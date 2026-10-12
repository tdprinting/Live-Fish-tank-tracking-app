"""Per-species fish definitions (ASSETS.md §2.1-2.7): shape, fins, eye and textures."""
import math
import os

import numpy as np

import common as C
import fishlib as FL
import paintlib as P
from common import bpy, hex2rgb

S = P.smoothstep


# ----------------------------------------------------------------------------
# texture helpers in "L units" (body length) on the unrolled flank canvas
# ----------------------------------------------------------------------------
def half_perim(H, W):
    a, b = H, W
    return 0.5 * math.pi * (3 * (a + b) - np.sqrt((3 * a + b) * (a + 3 * b)))


class Flank:
    """Distance helpers on a body canvas: u in L, v converted to L with the local half perimeter."""

    def __init__(self, cv, prof):
        self.cv = cv
        self.prof = prof
        uu = cv.u[0]
        self.lv = half_perim(prof.H(uu), prof.W(uu)).astype(np.float32)[None, :].repeat(cv.h, 0)
        self.u, self.v = cv.u, cv.v

    def dist(self, cu, cv_):
        return np.sqrt((self.u - cu) ** 2 + ((self.v - cv_) * self.lv) ** 2)

    def disc(self, cu, cv_, r, soft=0.003):
        return 1 - S(r - soft, r + soft, self.dist(cu, cv_))

    def stroke(self, pts, widths, soft=0.004, samples=160):
        """Polyline brush with per-point half-widths (all in L units)."""
        pts = np.asarray(pts, float)
        seg = np.linspace(0, len(pts) - 1, samples)
        i = np.clip(seg.astype(int), 0, len(pts) - 2)
        f = seg - i
        su = pts[i, 0] * (1 - f) + pts[i + 1, 0] * f
        sv = pts[i, 1] * (1 - f) + pts[i + 1, 1] * f
        sw = np.asarray(widths, float)[i] * (1 - f) + np.asarray(widths, float)[i + 1] * f
        d = np.full(self.u.shape, 1e9, np.float32)
        for a_, b_, w_ in zip(su, sv, sw):
            d = np.minimum(d, np.sqrt((self.u - a_) ** 2 + ((self.v - b_) * self.lv) ** 2) - w_)
        return 1 - S(-soft, soft, d)

    def spots(self, rs, n, rmin, rmax, spacing, box, weight_fn=None, existing=None):
        pts = []
        tries = 0
        while len(pts) < n and tries < 20000:
            tries += 1
            u = rs.uniform(box[0], box[1])
            v = rs.uniform(box[2], box[3])
            if weight_fn is not None and rs.random() > float(np.ravel(weight_fn(u, v))[0]):
                continue
            lv = float(np.ravel(half_perim(self.prof.H(u), self.prof.W(u)))[0])
            ok = all((u - q[0]) ** 2 + ((v - q[1]) * lv) ** 2 >= spacing ** 2 for q in pts)
            if ok:
                pts.append((u, v, rs.uniform(rmin, rmax)))
        m = np.zeros_like(self.u)
        for (u, v, r) in pts:
            m = np.maximum(m, self.disc(u, v, r))
        return m, pts


def scales_overlay(cv, size_px=14, contrast=0.06, seed=1):
    sc = P.hex_scales(cv, size_px, seed)
    cv.multiply(1 - contrast * sc)


def mucus(cv, amount=0.03, seed=5):
    n = P.value_noise(cv.h, cv.w, 160, 80, seed)
    cv.multiply(1 + amount * (n - 0.5) * 2)


def dark_mouth(cv, fl, u1=0.035, v0=0.42, v1=0.52, color="#3A1A12", op=0.8):
    m = P.band(cv.u, -0.1, u1, 0.006) * P.band(cv.v, v0, v1, 0.01)
    cv.blend(m, color, op)


# ----------------------------------------------------------------------------
# species definitions
# ----------------------------------------------------------------------------
def clown_bars(cv, fl, narrow=1.0):
    """Ocellaris: three white bars with thin black edges. Returns (white, black) masks."""
    u, v = cv.u, cv.v
    lat = np.exp(-((v - 0.5) / 0.12) ** 2)  # lateral line emphasis
    # bar 1 (head): u 0.21-0.26, bowed forward in the middle
    bow = 0.018 * np.exp(-((v - 0.5) / 0.25) ** 2)
    c1 = 0.235 - bow
    w1 = 0.026 * narrow
    # bar 2: u 0.50-0.58 with a forward-pointing triangular bulge at the lateral line
    tri = 0.085 * np.clip(1 - np.abs(v - 0.52) / 0.3, 0, 1) ** 1.6
    e0 = 0.505 + (1 - narrow) * 0.01 - tri
    e1 = 0.575 - (1 - narrow) * 0.01 - 0.008 * lat
    # bar 3 (peduncle): u 0.86-0.91, slightly curved
    c3 = 0.885 + 0.006 * np.cos((v - 0.5) * 3)
    w3 = 0.024 * narrow
    d1 = np.abs(u - c1) - w1
    d2 = np.maximum(e0 - u, u - e1)
    d3 = np.abs(u - c3) - w3
    d = np.minimum(np.minimum(d1, d2), d3)
    white = 1 - S(-0.002, 0.002, d)
    black = (1 - S(0.013, 0.017, d)) * (1 - white)
    return white, black


def paint_clown_orange(cv, fl):
    cv.vgrad("#F6893B", "#E8601A", 0.0, 1.0, 0.8)
    # subtle darker saddle on the back and paler throat
    cv.blend(S(0.75, 1.0, cv.v), "#D9531A", 0.35)
    cv.blend((1 - S(0.0, 0.25, cv.v)) * (1 - S(0.0, 0.3, cv.u)), "#F8A060", 0.4)
    mucus(cv, 0.05, 3)
    scales_overlay(cv, 13, 0.07)
    white, black = clown_bars(cv, fl)
    cv.blend(black, "#141414")
    cv.blend(white, "#F5F5F5")
    # slight bluish-white sheen inside the bars
    cv.blend(white * P.value_noise(cv.h, cv.w, 60, 30, 9) * 0.5, "#E6EEF5", 0.4)
    dark_mouth(cv, fl, color="#B4441A")


def paint_clown_darwin(cv, fl):
    cv.vgrad("#1E1A18", "#101010", 0.0, 1.0)
    face = 1 - S(0.15, 0.22, cv.u)
    cv.blend(face, "#F26B1D")
    # orange throat extends slightly back under the head bar
    cv.blend((1 - S(0.2, 0.3, cv.u)) * (1 - S(0.15, 0.4, cv.v)), "#E8641C", 0.8)
    mucus(cv, 0.06, 4)
    scales_overlay(cv, 13, 0.09)
    white, black = clown_bars(cv, fl, narrow=0.8)
    cv.blend(black, "#0A0A0A")
    cv.blend(white, "#F5F5F5")
    dark_mouth(cv, fl, color="#B4441A")


def fins_clown(cv, darwin=False):
    base = "#F26B1D"
    m = "#141414"
    FL.paint_fin_slot(cv, 0, base=base, alpha=0.9, rays=24, ray_color="#D95A12", margin=m, margin_w=0.12,
                      spine_count=10)
    FL.paint_fin_slot(cv, 2, base=base, alpha=0.9, rays=14, ray_color="#D95A12", margin=m, margin_w=0.14)
    FL.paint_fin_slot(cv, 3, base=base, alpha=0.88, rays=18, ray_color="#D95A12", margin=m, margin_w=0.12)
    FL.paint_fin_slot(cv, 4, base="#F48A3A", alpha=0.55, rays=14, ray_color="#E8701F", margin=None)
    FL.paint_fin_slot(cv, 5, base=base, alpha=0.85, rays=6, ray_color="#D95A12",
                      margin=m if darwin else "#202020", margin_w=0.15)


def paint_tang(cv, fl, juvenile=True):
    u, v = cv.u, cv.v
    cv.vgrad("#3A6EE6", "#2457CF", 0.0, 1.0)
    mucus(cv, 0.05, 11)
    scales_overlay(cv, 9, 0.04)
    # --- black "palette" pattern (a "6" enclosing the blue palette behind the pectoral) ----------
    A = [(0.09, 0.64), (0.2, 0.62), (0.35, 0.58), (0.5, 0.56), (0.6, 0.57), (0.7, 0.62), (0.82, 0.66), (0.95, 0.6)]
    Aw = [0.018, 0.028, 0.035, 0.04, 0.045, 0.05, 0.05, 0.035]
    arm = fl.stroke(A, Aw)
    B = [(0.6, 0.58), (0.66, 0.72), (0.66, 0.86), (0.62, 0.97)]
    loop = fl.stroke(B, [0.045, 0.045, 0.04, 0.03])
    cen = np.interp(u, [p_[0] for p_ in A], [p_[1] for p_ in A])
    rear = S(0.6, 0.64, u) * (1 - S(0.92, 0.95, u)) * S(cen - 0.02, cen, v)
    dband = S(0.85, 0.88, v) * S(0.22, 0.28, u) * (1 - S(0.92, 0.95, u))
    black = np.maximum.reduce([arm, loop, rear, dband])
    # head above the eye: blue; black reaches the eye
    cv.blend(black, "#0B0B10")
    # tail: yellow from u 0.93
    tail = S(0.915, 0.94, u)
    if juvenile:
        belly = (1 - S(0.12, 0.3, v)) * S(0.25, 0.4, u) * (1 - S(0.72, 0.86, u))
        cv.blend(belly, "#F4D01A", 0.6)
    cv.blend(tail, "#F2C500")
    # light blue face sheen and throat
    cv.blend((1 - S(0.0, 0.12, u)) * (1 - S(0.3, 0.6, v)), "#5B8BEF", 0.4)
    dark_mouth(cv, fl, u1=0.025, v0=0.55, v1=0.62, color="#1A2C60")


def fins_tang(cv):
    def blue_to_yellow(T, Sx, rgb):
        m = S(0.55, 0.9, Sx)[..., None]
        return rgb * (1 - m) + hex2rgb("#E8C21A") * m
    FL.paint_fin_slot(cv, 0, base="#2050C8", alpha=0.92, rays=28, ray_color="#14307A", margin="#0A0A0A",
                      margin_w=0.12, spine_count=9,
                      base_fn=lambda T, Sx, rgb: rgb * (1 - (1 - S(0.0, 0.3, Sx))[..., None] * 0.9))
    FL.paint_fin_slot(cv, 2, base="#2050C8", alpha=0.92, rays=24, ray_color="#14307A", margin="#0A0A0A",
                      margin_w=0.12, base_fn=lambda T, Sx, rgb: rgb * (1 - (1 - S(0.0, 0.25, Sx))[..., None] * 0.8))

    def caudal(T, Sx, rgb):
        rgb[:] = hex2rgb("#F2C500")
        edge = np.maximum(1 - S(0.08, 0.16, T), S(0.84, 0.92, T))
        return rgb * (1 - edge[..., None]) + hex2rgb("#0A0A0A") * edge[..., None]
    FL.paint_fin_slot(cv, 3, base="#F2C500", alpha=0.95, rays=20, ray_color="#C99A00", base_fn=caudal)
    FL.paint_fin_slot(cv, 4, base="#2B5FD9", alpha=0.75, rays=14, ray_color="#1A3C8F", base_fn=blue_to_yellow)
    FL.paint_fin_slot(cv, 5, base="#2B5FD9", alpha=0.8, rays=5, ray_color="#1A3C8F", margin="#0A0A0A")


def paint_cardinal(cv, fl):
    u, v = cv.u, cv.v
    # band tilts: further back at the top
    slant = (v - 0.5) * -0.03  # band runs from the first dorsal (top) down to the pelvics (slightly forward)
    b0, b1 = 0.345 + slant, 0.455 + slant
    cv.vgrad("#F2E2DE", "#EAD6D2", 0.0, 1.0)  # silvery white rear
    cv.blend(P.value_noise(cv.h, cv.w, 40, 20, 23) * 0.5, "#E8D0CC", 0.5)
    head = 1 - S(b0 - 0.01, b0 + 0.005, u)
    hc = P.Canvas(cv.w, cv.h)
    hc.vgrad("#D6BC4A", "#A89A3C", 0.0, 1.0)  # yellow-olive to tan head
    cv.rgb = cv.rgb * (1 - head[..., None]) + hc.rgb * head[..., None]
    # yellow stronger on the snout and nape, white cheek
    cv.blend(head * (1 - S(0.05, 0.25, u)) * S(0.4, 0.7, v), "#D4BC48", 0.5)
    cheek = np.exp(-(((u - 0.20) / 0.09) ** 2 + ((v - 0.33) / 0.13) ** 2))
    cv.blend(cheek * head, "#E4DCC0", 0.5)
    mucus(cv, 0.04, 21)
    scales_overlay(cv, 12, 0.05)
    # black band with a dark scale mesh
    band = P.band(u, b0, b1, 0.004) * S(0.02, 0.08, v) * (1 - S(0.97, 1.0, v))
    net = P.hex_net(cv, 15, 1.6)
    bandc = (1 - net[..., None]) * hex2rgb("#5A5246") + net[..., None] * hex2rgb("#0E0C0A")
    cv.rgb = cv.rgb * (1 - band[..., None]) + bandc * band[..., None]
    # reddish-brown spots on the rear body
    rs = np.random.default_rng(7)
    m, _ = fl.spots(rs, 60, 0.012, 0.018, 0.033, (0.49, 0.97, 0.08, 0.97),
                    weight_fn=lambda uu, vv: 0.35 + 0.65 * S(0.1, 0.4, vv))
    # second flank pass with a different seed gives a different arrangement on the far side
    # (both flanks share UVs, so one set is used)
    cv.blend(m, "#9A3E2C")
    # dark edge of the lips
    dark_mouth(cv, fl, u1=0.03, v0=0.62, v1=0.7, color="#8C7A30", op=0.6)


def fins_cardinal(cv):
    grey = "#E4E2DE"
    FL.paint_fin_slot(cv, 0, base="#D8B840", alpha=0.85, rays=7, ray_color="#9A7A20", spine_count=7,
                      margin="#E0702A", margin_w=0.14)
    FL.paint_fin_slot(cv, 1, base=grey, alpha=0.5, rays=10, ray_color="#8A8580", margin="#E8E8E8",
                      margin_w=0.06, base_fn=lambda T, Sx, rgb: rgb * (1 - 0.4 * (1 - S(0.0, 0.4, Sx)))[..., None])
    FL.paint_fin_slot(cv, 2, base=grey, alpha=0.5, rays=10, ray_color="#8A8580", margin="#E8E8E8", margin_w=0.06)
    FL.paint_fin_slot(cv, 3, base="#B8B4B0", alpha=0.55, rays=18, ray_color="#6E6A66", margin="#DDDDDD",
                      margin_w=0.05)
    FL.paint_fin_slot(cv, 4, base=grey, alpha=0.35, rays=12, ray_color="#A8A49F")
    FL.paint_fin_slot(cv, 5, base="#E8A830", alpha=0.92, rays=6, ray_color="#C07A20", margin="#D0302A",
                      margin_w=0.14)


def paint_wrasse(cv, fl):
    u, v = cv.u, cv.v
    lv = fl.lv
    # above / below the stripe
    above = P.Canvas(cv.w, cv.h)
    t = S(0.25, 0.45, u)[..., None]
    above.rgb = hex2rgb("#D7E6F3") * (1 - t) + hex2rgb("#2E7FE0") * t
    below = P.Canvas(cv.w, cv.h)
    tb = S(0.4, 0.9, u)[..., None]
    below.rgb = hex2rgb("#EEF3F7") * (1 - tb) + hex2rgb("#A9C8EE") * tb
    upper = S(0.48, 0.55, v)[..., None]
    cv.rgb = below.rgb * (1 - upper) + above.rgb * upper
    # dorsal ridge deeper blue toward the tail
    cv.blend(S(0.8, 1.0, v) * S(0.4, 0.8, u), "#1F5FC0", 0.5)
    # yellow tint on the forehead
    cv.blend(P.band(u, 0.05, 0.2, 0.03) * S(0.7, 0.8, v), "#F1C40F", 0.25)
    mucus(cv, 0.04, 31)
    scales_overlay(cv, 10, 0.04)
    # black stripe: 0.05 L wide at the head -> 0.14 L at the peduncle, through the eye
    half_w = np.interp(u, [0.0, 0.15, 0.5, 0.85, 1.0], [0.018, 0.026, 0.045, 0.07, 0.075])
    cen = np.interp(u, [0.0, 0.07, 0.3, 1.0], [0.56, 0.58, 0.52, 0.5])
    d = np.abs(v - cen) * lv
    stripe = 1 - S(half_w - 0.004, half_w + 0.004, d)
    cv.blend(stripe, "#0F0F0F")
    dark_mouth(cv, fl, u1=0.02, v0=0.5, v1=0.56, color="#202020")


def fins_wrasse(cv):
    def dorsal(T, Sx, rgb):
        rgb[:] = hex2rgb("#2E7FE0")
        b = (1 - S(0.25, 0.4, Sx)) * S(0.05, 0.3, T)
        return rgb * (1 - b[..., None]) + hex2rgb("#0F0F0F") * b[..., None]
    FL.paint_fin_slot(cv, 0, base="#2E7FE0", alpha=0.8, rays=30, ray_color="#1A4F9F", base_fn=dorsal,
                      margin="#7FB8F0", margin_w=0.08)
    FL.paint_fin_slot(cv, 2, base="#2E7FE0", alpha=0.75, rays=22, ray_color="#1A4F9F", base_fn=dorsal,
                      margin="#7FB8F0", margin_w=0.08)

    def caudal(T, Sx, rgb):
        rgb[:] = hex2rgb("#2E7FE0")
        c = 1 - S(0.28, 0.36, np.abs(T - 0.5))
        return rgb * (1 - c[..., None]) + hex2rgb("#0F0F0F") * c[..., None]
    FL.paint_fin_slot(cv, 3, base="#2E7FE0", alpha=0.9, rays=14, ray_color="#1A4F9F", base_fn=caudal,
                      margin="#9FCBF5", margin_w=0.05)
    FL.paint_fin_slot(cv, 4, base="#E8EEF5", alpha=0.25, rays=10, ray_color="#C0C8D0", ray_alpha=0.4)
    FL.paint_fin_slot(cv, 5, base="#E8EEF5", alpha=0.35, rays=5, ray_color="#C0C8D0", ray_alpha=0.5)


def snowflake_mask(w, h, seed, rows=(0.32, 0.58, 0.84), aspect=1.0, density=1.0, scale_u=1.0):
    """Dendritic black blotches: warped, thresholded Voronoi cells arranged in rows."""
    rs = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u = (xx + 0.5) / w
    v = 1 - (yy + 0.5) / h
    warp_u = (P.fbm(h, w, 24, 4, seed + 1) - 0.5) * 0.02
    warp_v = (P.fbm(h, w, 24, 4, seed + 2) - 0.5) * 0.12
    uw, vw = u + warp_u, v + warp_v
    m = np.zeros((h, w), np.float32)
    centers = []
    for ri, rv in enumerate(rows):
        x = rs.uniform(0.0, 0.05) + 0.02 * ri
        while x < 1.02:
            sz_u = rs.uniform(0.022, 0.036) * scale_u
            sz_v = rs.uniform(0.13, 0.19) * aspect
            cv_ = rv + rs.uniform(-0.05, 0.05)
            d = np.sqrt(((uw - x) / sz_u) ** 2 + ((vw - cv_) / sz_v) ** 2)
            # star/dendritic edge
            ang = np.arctan2((vw - cv_) / sz_v, (uw - x) / sz_u)
            k = rs.integers(5, 8)
            edge = 1 + 0.35 * np.sin(ang * k + rs.uniform(0, 6.28)) + 0.2 * np.sin(ang * (k + 3) + 1.0)
            m = np.maximum(m, 1 - S(0.85, 1.0, d / edge))
            centers.append((x, cv_, sz_u, sz_v))
            x += rs.uniform(0.06, 0.085) * scale_u / density
    return m, centers


def paint_engineer_goby(cv, fl):
    """Juvenile engineer goby (ref_engineer_goby.jpg): black, yellow-cream wavy vertical bars joined by a broken
    wavy mid-flank stripe into a maze."""
    cv.vgrad("#0E0F0C", "#181A14", 0.0, 1.0)
    mucus(cv, 0.03, 41)
    rs = np.random.default_rng(42)
    m = np.zeros_like(cv.u)
    x = 0.05
    while x < 0.97:
        top = rs.uniform(0.85, 0.97)
        bot = rs.uniform(0.05, 0.2)
        n = 6
        pts = [(x + 0.012 * math.sin(k * 1.7 + x * 40) + rs.uniform(-0.004, 0.004), bot + (top - bot) * k / (n - 1))
               for k in range(n)]
        if rs.uniform() < 0.35:  # some bars break at the mid stripe
            pts = pts[:3] if rs.uniform() < 0.5 else pts[3:]
        m = np.maximum(m, fl.stroke(pts, [0.0075] * len(pts), soft=0.003))
        x += rs.uniform(0.045, 0.06)
    x = 0.03
    while x < 0.95:  # broken wavy mid-flank stripe joins the bars into a maze
        seg = rs.uniform(0.04, 0.1)
        xs = np.linspace(x, min(x + seg, 0.98), 6)
        pts = [(xx, 0.52 + 0.07 * math.sin(xx * 60 + 1.3)) for xx in xs]
        m = np.maximum(m, fl.stroke(pts, [0.0065] * len(pts), soft=0.003))
        x += seg + rs.uniform(0.02, 0.06)
    m = np.maximum(m, fl.stroke([(0.005, 0.6), (0.03, 0.8), (0.06, 0.92)], [0.004] * 3, soft=0.002))
    m = np.maximum(m, fl.stroke([(0.01, 0.35), (0.04, 0.3), (0.07, 0.4)], [0.0035] * 3, soft=0.002))
    cv.blend(np.clip(m, 0, 1), "#E4E070")
    dark_mouth(cv, fl, u1=0.03, v0=0.38, v1=0.44, color="#050505", op=0.7)


def fins_engineer_goby(cv):
    FL.paint_fin_slot(cv, 6, base="#141510", alpha=0.95, rays=0, margin="#B8B460", margin_w=0.06, edge_fade=0.1)
    FL.paint_fin_slot(cv, 4, base="#1A1A16", alpha=0.7, rays=8, ray_color="#0A0A08")


def paint_hawkfish(cv, fl):
    """Falco hawkfish (ref_hawkfish.jpg): white body densely spotted orange-red, densest on the back and head."""
    u, v = cv.u, cv.v
    cv.vgrad("#F4ECE6", "#FAF6F2", 0.0, 0.6)
    mucus(cv, 0.03, 51)
    scales_overlay(cv, 12, 0.05)
    rs = np.random.default_rng(52)
    dens = lambda uu, vv: 0.15 + 0.85 * S(0.25, 0.85, vv) * (1 - 0.5 * S(0.5, 0.95, uu))  # noqa: E731
    # irregular blotches that coalesce on the upper back
    blot = S(0.52, 0.6, P.fbm(cv.h, cv.w, 34, 3, 53) * (0.7 + 0.5 * S(0.45, 0.95, v)) * (1.05 - 0.35 * u))
    cv.blend(blot * S(0.3, 0.6, v), "#D8562C", 0.85)
    m, _ = fl.spots(rs, 120, 0.006, 0.011, 0.016, (0.02, 0.93, 0.18, 0.99), weight_fn=dens)
    cv.blend(m, "#E0602E")
    m3, _ = fl.spots(rs, 40, 0.004, 0.007, 0.012, (0.05, 0.9, 0.55, 0.99))
    cv.blend(m3, "#C23C1C")
    # red around the eye and a red-orange stripe from behind the eye toward the gill
    eu, ev = 0.10, 0.72
    cv.blend(fl.disc(eu, ev, 0.05, 0.01), "#D8401E", 0.7)
    cv.blend(fl.stroke([(eu + 0.02, ev - 0.04), (eu + 0.08, ev - 0.12), (eu + 0.12, ev - 0.2)], [0.012, 0.011, 0.007],
                       soft=0.004), "#E0481E", 1.0)
    m2, _ = fl.spots(rs, 26, 0.005, 0.008, 0.012, (0.0, 0.2, 0.4, 0.99))
    cv.blend(m2, "#D24A22", 0.95)
    dark_mouth(cv, fl, u1=0.03, v0=0.38, v1=0.46, color="#C0502E", op=0.5)


def fins_hawkfish(cv):
    def spotted(T, Sx, rgb, a, n=10, rows=4, color="#E0602E"):
        ph = np.abs(((T * n) % 1.0) - 0.5)
        rw = np.abs(((Sx * rows) % 1.0) - 0.5)
        m = (1 - S(0.15, 0.3, ph)) * (1 - S(0.12, 0.3, rw)) * (Sx < 0.9)
        return rgb * (1 - m[..., None]) + hex2rgb(color) * m[..., None], np.maximum(a, 0.9 * m)
    FL.paint_fin_slot(cv, 0, base="#F2E2D2", alpha=0.6, rays=16, ray_color="#C08060", spine_count=10,
                      spots=lambda T, Sx, rgb, a: spotted(T, Sx, rgb, a, 16, 4), margin="#F8EEE6", margin_w=0.05)
    FL.paint_fin_slot(cv, 2, base="#F2E2D2", alpha=0.55, rays=9, ray_color="#C08060",
                      spots=lambda T, Sx, rgb, a: spotted(T, Sx, rgb, a, 9, 3))

    def caudal_spots(T, Sx, rgb, a):
        rgb, a = spotted(T, Sx, rgb, a, 12, 5, "#E0602E")
        blue = (1 - S(0.12, 0.25, np.abs(((T * 12 + 0.5) % 1.0) - 0.5))) * \
            (1 - S(0.1, 0.25, np.abs(((Sx * 5 + 0.5) % 1.0) - 0.5))) * (Sx > 0.25) * (Sx < 0.9)
        return rgb * (1 - blue[..., None]) + hex2rgb("#5E9AD8") * blue[..., None], np.maximum(a, 0.85 * blue)
    FL.paint_fin_slot(cv, 3, base="#F4EEEA", alpha=0.55, rays=14, ray_color="#E0B0A0", spots=caudal_spots)
    FL.paint_fin_slot(cv, 4, base="#F4D8CC", alpha=0.7, rays=14, ray_color="#B85A40",
                      spots=lambda T, Sx, rgb, a: spotted(T, Sx, rgb, a, 14, 3, "#E0602E"))
    FL.paint_fin_slot(cv, 5, base="#F4E0D6", alpha=0.6, rays=5, ray_color="#C08060")


def paint_lawnmower(cv, fl):
    """Salarias fasciatus: mottled tan/brown/olive, faint dark bars, pale flecks, blue face spots, big lips."""
    u, v = cv.u, cv.v
    cv.vgrad("#9A9466", "#5E6B3F", 0.0, 1.0, 0.9)
    cv.blend(S(0.6, 1.0, v), "#6B5A3A", 0.45)
    mucus(cv, 0.06, 71)
    n1 = P.fbm(cv.h, cv.w, 34, 4, 72)
    n2 = P.fbm(cv.h, cv.w, 50, 3, 73)
    cv.blend(S(0.55, 0.62, n1), "#3B3A22", 0.75)
    cv.blend(S(0.6, 0.66, n2) * S(0.15, 0.5, v), "#B8B07A", 0.55)
    # faint dark bars (dusky bands), 8 along the body
    bars = np.zeros_like(u)
    for i, bc in enumerate(np.linspace(0.2, 0.9, 8)):
        bars = np.maximum(bars, fl.stroke([(bc, 0.92), (bc + 0.01, 0.55), (bc + 0.015, 0.25)],
                                          [0.022, 0.026, 0.016], soft=0.012))
    cv.blend(bars * (0.6 + 0.4 * n2), "#3A3420", 0.4)
    # thin dark wavy longitudinal lines on the rear body
    wav = np.abs(np.sin((v * 9 + 0.6 * np.sin(u * 40)) * np.pi))
    cv.blend((1 - S(0.0, 0.12, wav)) * S(0.45, 0.6, u) * P.band(v, 0.2, 0.8, 0.05), "#2E2A18", 0.35)
    rs = np.random.default_rng(74)
    m, _ = fl.spots(rs, 90, 0.003, 0.005, 0.015, (0.2, 0.97, 0.15, 0.95))
    cv.blend(m, "#E8E0C0", 0.6)
    # head: iridescent blue spots around the eye and cheek
    m2, _ = fl.spots(rs, 9, 0.004, 0.006, 0.015, (0.02, 0.16, 0.35, 0.85))
    cv.blend(m2, "#3A8FD8", 0.95)
    # big fleshy lips: pale band at the front of the snout with a dark gape line
    lips = (1 - S(0.025, 0.04, u)) * P.band(v, 0.28, 0.52, 0.03)
    cv.blend(lips, "#B7A57A", 0.8)
    dark_mouth(cv, fl, u1=0.035, v0=0.38, v1=0.42, color="#1E1A10", op=0.85)


def fins_lawnmower(cv):
    def mottled(T, Sx, rgb, a, seed=75):
        h, w = T.shape
        n = P.fbm(h, w, 30, 3, seed)
        m = S(0.5, 0.58, n) * (Sx < 0.92)
        return rgb * (1 - 0.8 * m[..., None]) + hex2rgb("#3B3A22") * 0.8 * m[..., None], np.maximum(a, 0.75 * m)
    FL.paint_fin_slot(cv, 0, base="#8A845A", alpha=0.5, rays=30, ray_color="#5A5434", spots=mottled,
                      margin="#C8B080", margin_w=0.06)
    FL.paint_fin_slot(cv, 2, base="#8A845A", alpha=0.5, rays=20, ray_color="#5A5434",
                      spots=lambda T, Sx, rgb, a: mottled(T, Sx, rgb, a, 76))
    FL.paint_fin_slot(cv, 3, base="#8A845A", alpha=0.55, rays=14, ray_color="#5A5434",
                      spots=lambda T, Sx, rgb, a: mottled(T, Sx, rgb, a, 77))
    FL.paint_fin_slot(cv, 4, base="#8A845A", alpha=0.55, rays=14, ray_color="#5A5434",
                      spots=lambda T, Sx, rgb, a: mottled(T, Sx, rgb, a, 78))
    FL.paint_fin_slot(cv, 5, base="#9A9466", alpha=0.6, rays=3, ray_color="#5A5434")


def paint_tailspot(cv, fl):
    """Ecsenius stigmatura: grey-tan/purplish-brown front fading to yellow-cream, black tail-base spot,
    with the blue + orange lines below the eye seen in docs/reference/blenny.jpg."""
    u, v = cv.u, cv.v
    front = P.Canvas(cv.w, cv.h)
    front.vgrad("#8E7B6C", "#6E5650", 0.0, 1.0)
    rear = P.Canvas(cv.w, cv.h)
    rear.vgrad("#F1E4B8", "#E3CC86", 0.0, 1.0)
    t = S(0.35, 0.78, u)[..., None]
    cv.rgb = front.rgb * (1 - t) + rear.rgb * t
    head = 1 - S(0.12, 0.26, u)
    cv.blend(head * S(0.4, 0.9, v), "#5A3A4A", 0.55)
    mucus(cv, 0.05, 81)
    lv = fl.lv
    blue = P.band(u, 0.015, 0.17, 0.008) * (1 - S(0.005, 0.008, np.abs(v - 0.58) * lv))
    orng = P.band(u, 0.015, 0.165, 0.008) * (1 - S(0.005, 0.008, np.abs(v - 0.50) * lv))
    cv.blend(orng, "#F26B1D")
    cv.blend(blue, "#2E6FD8")
    cv.blend(fl.disc(0.93, 0.5, 0.024, 0.004), "#121212")
    cv.blend(P.band(u, 0.175, 0.185, 0.004) * P.band(v, 0.3, 0.75, 0.05), "#4A3038", 0.5)
    dark_mouth(cv, fl, u1=0.02, v0=0.36, v1=0.44, color="#2A1A1A", op=0.6)


def fins_tailspot(cv):
    FL.paint_fin_slot(cv, 0, base="#C8B88A", alpha=0.45, rays=26, ray_color="#8A7A5A", margin="#E8A040",
                      margin_w=0.07)
    FL.paint_fin_slot(cv, 2, base="#D8C890", alpha=0.45, rays=20, ray_color="#8A7A5A")
    FL.paint_fin_slot(cv, 3, base="#E8D898", alpha=0.5, rays=12, ray_color="#A89860")
    FL.paint_fin_slot(cv, 4, base="#C8B88A", alpha=0.35, rays=12, ray_color="#8A7A5A")
    FL.paint_fin_slot(cv, 5, base="#C8B88A", alpha=0.45, rays=3, ray_color="#8A7A5A")


def paint_dottyback(cv, fl):
    """Dottyback (ref_dottyback.jpg): lavender-blue, broad black lateral stripe snout->tail through the eye,
    thin dark stripe along the dorsal base, pale blue-white lines between, white-blue belly."""
    u, v = cv.u, cv.v
    cv.vgrad("#DCE6FF", "#8E9EEA", 0.0, 0.75)
    mucus(cv, 0.03, 91)
    scales_overlay(cv, 10, 0.05)
    c = 0.62 - 0.04 * u
    hw = 0.085 - 0.03 * u
    cv.blend(1 - S(hw - 0.012, hw + 0.012, np.abs(v - c)), "#0C0C18", 0.95)
    cv.blend(1 - S(0.012, 0.03, np.abs(v - (c + hw + 0.05))), "#E4ECFF", 0.85)
    cv.blend(1 - S(0.015, 0.03, np.abs(v - 0.93)), "#141428", 0.9)
    cv.blend(1 - S(0.2, 0.42, v), "#EEF2FF", 0.7)
    dark_mouth(cv, fl, u1=0.02, v0=0.5, v1=0.56, color="#101020", op=0.7)


def fins_dottyback(cv):
    edge = "#6A90F0"
    FL.paint_fin_slot(cv, 0, base="#1A1C34", alpha=0.85, rays=30, ray_color="#101020", margin=edge, margin_w=0.12)
    FL.paint_fin_slot(cv, 2, base="#1A1C34", alpha=0.85, rays=20, ray_color="#101020", margin=edge, margin_w=0.12)

    def dark_center(T, Sx, rgb, a):
        m = 1 - S(0.18, 0.4, np.abs(T - 0.5))
        return rgb * (1 - m[..., None]) + hex2rgb("#0E0E1C") * m[..., None], a
    FL.paint_fin_slot(cv, 3, base="#7A8CE0", alpha=0.85, rays=16, ray_color="#4A5AB0", margin=edge, margin_w=0.06,
                      spots=dark_center)
    FL.paint_fin_slot(cv, 4, base="#A8B8F0", alpha=0.35, rays=12, ray_color="#7080C0")
    FL.paint_fin_slot(cv, 5, base="#C8D4FF", alpha=0.6, rays=4, ray_color="#8090D0")


# ----------------------------------------------------------------------------
SPECIES = {
    "clown_orange": dict(
        profile=[(0, 0.07, 0.05, -0.03), (0.03, 0.19, 0.10, -0.025), (0.09, 0.29, 0.135, -0.015),
                 (0.2, 0.37, 0.155, -0.005), (0.36, 0.41, 0.16, 0.0), (0.52, 0.39, 0.15, 0.0), (0.68, 0.30, 0.12, 0.0),
                 (0.8, 0.20, 0.085, 0.0), (0.9, 0.15, 0.06, 0.0), (1.0, 0.13, 0.04, 0.0)],
        body_tris=3600, ring=32, p_exp=0.85,
        dorsal=dict(u0=0.22, u1=0.80, h=[(0.22, 0.02), (0.26, 0.10), (0.33, 0.14), (0.45, 0.13), (0.50, 0.10),
                                         (0.52, 0.08), (0.56, 0.11), (0.66, 0.12), (0.75, 0.105), (0.80, 0.03)],
                    rake=18, n=26),
        anal=dict(u0=0.56, u1=0.82, h=[(0.56, 0.02), (0.60, 0.09), (0.70, 0.10), (0.78, 0.08), (0.82, 0.02)], rake=25),
        caudal=dict(length=0.2, span=0.26, shape="rounded"),
        pectoral=dict(u=0.27, length=0.18, width=0.12, zf=-0.12, shape="round"),
        pelvic=dict(u=0.33, length=0.10),
        eye=dict(u=0.11, zf=0.33, r=0.045, iris="#E8E2D2", ring="#F26B1D", outer="#E05A15"),
        paint=paint_clown_orange, fins=fins_clown, irid=0.12, rough=0.35, length_cm=7.0, scale_note="female 7.0, male 5.5",
    ),
    "tang": dict(
        profile=[(0, 0.08, 0.05, 0.0), (0.04, 0.22, 0.08, 0.005), (0.1, 0.34, 0.11, 0.005), (0.2, 0.46, 0.13, 0.0),
                 (0.35, 0.53, 0.14, 0.0), (0.5, 0.545, 0.14, 0.0), (0.64, 0.50, 0.13, 0.0), (0.75, 0.40, 0.11, 0.0),
                 (0.84, 0.25, 0.08, 0.0), (0.9, 0.14, 0.055, 0.0), (0.95, 0.11, 0.045, 0.0), (1.0, 0.10, 0.03, 0.0)],
        body_tris=4200, ring=36, p_exp=0.7,
        dorsal=dict(u0=0.17, u1=0.86, h=[(0.17, 0.03), (0.22, 0.09), (0.35, 0.11), (0.7, 0.11), (0.82, 0.09),
                                         (0.86, 0.02)], rake=15, n=30),
        anal=dict(u0=0.42, u1=0.86, h=[(0.42, 0.03), (0.47, 0.09), (0.7, 0.10), (0.82, 0.08), (0.86, 0.02)],
                  rake=18, n=22),
        caudal=dict(length=0.22, span=0.30, shape="lunate"),
        pectoral=dict(u=0.28, length=0.22, width=0.14, zf=-0.05, shape="pointed"),
        pelvic=dict(u=0.35, length=0.08),
        eye=dict(u=0.12, zf=0.32, r=0.05, iris="#1A1A24", ring="#3B6FE0", outer="#101018", ring_w=0.1),
        paint=paint_tang, fins=fins_tang, irid=0.35, rough=0.3, length_cm=6.5,
    ),
    "cardinal": dict(
        profile=[(0, 0.10, 0.04, 0.04), (0.04, 0.27, 0.075, 0.03), (0.12, 0.39, 0.10, 0.012), (0.27, 0.45, 0.115, 0.0),
                 (0.42, 0.45, 0.115, 0.0), (0.58, 0.38, 0.10, 0.0), (0.72, 0.26, 0.08, 0.0), (0.84, 0.16, 0.055, 0.0),
                 (0.93, 0.13, 0.04, 0.0), (1.0, 0.12, 0.03, 0.0)],
        body_tris=3400, ring=32, p_exp=0.7, belly_bias=0.05,
        dorsal=dict(u0=0.30, u1=0.42, h=[(0.30, 0.06), (0.32, 0.19), (0.36, 0.15), (0.40, 0.08), (0.42, 0.02)],
                    rake=22, n=10),
        dorsal2=dict(u0=0.52, u1=0.70, h=[(0.52, 0.05), (0.53, 0.46), (0.545, 0.18), (0.56, 0.14), (0.62, 0.13), (0.67, 0.08),
                                          (0.70, 0.02)], rake=55, n=16),
        anal=dict(u0=0.55, u1=0.72, h=[(0.55, 0.04), (0.58, 0.14), (0.64, 0.13), (0.70, 0.07), (0.72, 0.02)],
                  rake=38, n=12),
        caudal=dict(length=0.24, span=0.34, shape="forked"),
        pectoral=dict(u=0.27, length=0.16, width=0.08, zf=-0.05, shape="pointed"),
        pelvic=dict(u=0.36, length=0.16),
        eye=dict(u=0.11, zf=0.24, r=0.072, iris="#D8401E", ring="#E85A2A", outer="#C8A040", pupil=0.4, ring_w=0.14),
        paint=paint_cardinal, fins=fins_cardinal, irid=0.15, rough=0.35, length_cm=5.0,
    ),
    "wrasse": dict(
        profile=[(0, 0.03, 0.03, 0.0), (0.08, 0.12, 0.09, 0.0), (0.2, 0.19, 0.12, 0.0), (0.45, 0.21, 0.13, 0.0),
                 (0.7, 0.17, 0.10, 0.0), (0.88, 0.10, 0.06, 0.0), (1.0, 0.05, 0.02, 0.0)],
        body_tris=3000, ring=28, p_exp=1.0, belly_bias=0.05, back_pinch=0.1,
        dorsal=dict(u0=0.18, u1=0.88, h=[(0.18, 0.02), (0.22, 0.06), (0.5, 0.07), (0.85, 0.07), (0.88, 0.02)],
                    rake=30, n=26),
        anal=dict(u0=0.5, u1=0.88, h=[(0.5, 0.02), (0.54, 0.055), (0.84, 0.06), (0.88, 0.02)], rake=30, n=16),
        caudal=dict(length=0.15, span=0.2, shape="truncate"),
        pectoral=dict(u=0.24, length=0.12, width=0.07, zf=0.0, shape="round"),
        pelvic=dict(u=0.30, length=0.07),
        eye=dict(u=0.07, zf=0.25, r=0.032, iris="#7A8EA0", ring="#101010", outer="#101010", ring_w=0.1),
        paint=paint_wrasse, fins=fins_wrasse, irid=0.5, rough=0.3, length_cm=7.0,
    ),
    "engineer_goby": dict(
        # Pholidichthys leucotaenia (juvenile): elongate, compressed, blunt head, continuous dorsal-caudal-anal fin
        profile=[(0, 0.07, 0.05, 0.0), (0.015, 0.09, 0.062, 0.0), (0.06, 0.10, 0.065, 0.0), (0.3, 0.10, 0.06, 0.0),
                 (0.7, 0.095, 0.05, 0.0), (0.88, 0.07, 0.035, 0.0), (0.97, 0.035, 0.018, 0.0), (1.0, 0.012, 0.01, 0.0)],
        body_tris=5500, ring=16, p_exp=0.9, belly_bias=0.06, back_pinch=0.1,
        ribbon=dict(u_d=0.33, u_a=0.52, h_d=0.045, h_a=0.04),
        caudal=None,
        pectoral=dict(u=0.1, length=0.05, width=0.04, zf=-0.2, shape="round"),
        eye=dict(u=0.035, zf=0.4, r=0.014, iris="#2A2A20", ring="#B8B060", outer="#101010", ring_w=0.15),
        paint=paint_engineer_goby, fins=fins_engineer_goby, irid=0.05, rough=0.4, length_cm=20.0, tex=(2048, 256),
        gill=(0.08, 0.4, 0.03, 0.25),
    ),
    "hawkfish": dict(
        profile=[(0, 0.045, 0.035, -0.03), (0.06, 0.17, 0.09, -0.02), (0.16, 0.29, 0.13, -0.005), (0.3, 0.345, 0.14, 0.0),
                 (0.48, 0.34, 0.135, 0.0), (0.65, 0.27, 0.11, 0.0), (0.8, 0.17, 0.075, 0.0), (0.9, 0.13, 0.055, 0.0),
                 (1.0, 0.12, 0.04, 0.0)],
        body_tris=3400, ring=32, p_exp=0.8, belly_bias=0.06,
        dorsal=dict(u0=0.22, u1=0.82, h=[(0.22, 0.03), (0.26, 0.11), (0.4, 0.12), (0.52, 0.10), (0.56, 0.075),
                                         (0.60, 0.10), (0.75, 0.10), (0.82, 0.02)], rake=20, n=24, cirri=True),
        anal=dict(u0=0.58, u1=0.78, h=[(0.58, 0.03), (0.62, 0.09), (0.72, 0.09), (0.78, 0.02)], rake=30, n=12),
        caudal=dict(length=0.17, span=0.24, shape="truncate"),
        pectoral=dict(u=0.27, length=0.22, width=0.15, zf=-0.3, shape="round", fingers=True, n=12),
        pelvic=dict(u=0.33, length=0.12),
        eye=dict(u=0.10, zf=0.45, r=0.06, iris="#D8C8A0", ring="#8B3A24", outer="#B04A30", ring_w=0.14),
        paint=paint_hawkfish, fins=fins_hawkfish, irid=0.1, rough=0.4, length_cm=5.0,
    ),
    "lawnmower_blenny": dict(
        profile=[(0, 0.10, 0.09, -0.01), (0.04, 0.18, 0.13, 0.0), (0.12, 0.21, 0.15, 0.0), (0.35, 0.20, 0.14, 0.0),
                 (0.6, 0.16, 0.10, 0.0), (0.82, 0.10, 0.055, 0.0), (1.0, 0.05, 0.02, 0.0)],
        body_tris=3300, ring=28, p_exp=0.95, belly_bias=0.12,
        dorsal=dict(u0=0.10, u1=0.90, h=[(0.10, 0.03), (0.14, 0.10), (0.42, 0.09), (0.46, 0.065), (0.50, 0.10),
                                         (0.86, 0.10), (0.90, 0.02)], rake=22, n=32),
        anal=dict(u0=0.48, u1=0.90, h=[(0.48, 0.02), (0.52, 0.07), (0.86, 0.07), (0.90, 0.02)], rake=28, n=18),
        caudal=dict(length=0.14, span=0.2, shape="rounded"),
        pectoral=dict(u=0.17, length=0.17, width=0.16, zf=-0.2, shape="round"),
        pelvic=dict(u=0.10, length=0.06),
        eye=dict(u=0.065, zf=0.55, r=0.05, iris="#B89A50", ring="#3A2A10", outer="#4A4A2A", ring_w=0.1),
        cirri=dict(u=0.06, h=0.07, blades=4),
        paint=paint_lawnmower, fins=fins_lawnmower, irid=0.15, rough=0.4, length_cm=10.0,
    ),
    "tailspot_blenny": dict(
        profile=[(0, 0.08, 0.07, 0.0), (0.06, 0.16, 0.12, 0.01), (0.15, 0.17, 0.12, 0.0), (0.4, 0.165, 0.11, 0.0),
                 (0.65, 0.13, 0.08, 0.0), (0.85, 0.08, 0.045, 0.0), (1.0, 0.04, 0.02, 0.0)],
        body_tris=3000, ring=28, p_exp=0.95, belly_bias=0.1,
        dorsal=dict(u0=0.12, u1=0.92, h=[(0.12, 0.03), (0.16, 0.09), (0.45, 0.085), (0.5, 0.065), (0.55, 0.09),
                                         (0.88, 0.08), (0.92, 0.02)], rake=25, n=30),
        anal=dict(u0=0.5, u1=0.92, h=[(0.5, 0.02), (0.54, 0.07), (0.88, 0.065), (0.92, 0.02)], rake=30, n=18),
        caudal=dict(length=0.13, span=0.18, shape="rounded"),
        pectoral=dict(u=0.18, length=0.16, width=0.14, zf=-0.25, shape="round"),
        pelvic=dict(u=0.12, length=0.06),
        eye=dict(u=0.07, zf=0.55, r=0.055, iris="#C8A050", ring="#2A1A10", outer="#3A2420", ring_w=0.1),
        cirri=dict(u=0.065, h=0.035, blades=2),
        paint=paint_tailspot, fins=fins_tailspot, irid=0.2, rough=0.35, length_cm=5.5,
    ),
    "dottyback": dict(
        profile=[(0, 0.045, 0.035, -0.012), (0.05, 0.14, 0.08, -0.004), (0.14, 0.22, 0.105, 0.0), (0.3, 0.26, 0.115, 0.0),
                 (0.5, 0.265, 0.11, 0.0), (0.7, 0.22, 0.09, 0.0), (0.86, 0.15, 0.06, 0.0), (1.0, 0.11, 0.04, 0.0)],
        body_tris=3200, ring=28, p_exp=0.85, belly_bias=0.06,
        dorsal=dict(u0=0.20, u1=0.92, h=[(0.20, 0.02), (0.23, 0.06), (0.5, 0.07), (0.8, 0.085), (0.9, 0.07), (0.92, 0.02)],
                    rake=25, n=30),
        anal=dict(u0=0.46, u1=0.92, h=[(0.46, 0.02), (0.5, 0.06), (0.8, 0.08), (0.9, 0.065), (0.92, 0.02)], rake=28, n=20),
        caudal=dict(length=0.26, span=0.15, shape="rounded"),
        pectoral=dict(u=0.24, length=0.14, width=0.09, zf=-0.1, shape="round"),
        pelvic=dict(u=0.28, length=0.10),
        eye=dict(u=0.08, zf=0.3, r=0.045, iris="#20204A", ring="#5B63C8", outer="#202060", ring_w=0.12),
        paint=paint_dottyback, fins=fins_dottyback, irid=0.3, rough=0.32, length_cm=6.5,
    ),
}
SPECIES["clown_darwin"] = dict(SPECIES["clown_orange"], paint=paint_clown_darwin,
                               fins=lambda cv: fins_clown(cv, darwin=True),
                               eye=dict(u=0.11, zf=0.33, r=0.045, iris="#E8E2D2", ring="#F26B1D", outer="#E05A15"))

FISH_IDS = ["clown_orange", "clown_darwin", "tang", "cardinal", "wrasse", "engineer_goby", "hawkfish", "lawnmower_blenny",
            "tailspot_blenny", "dottyback"]


# ----------------------------------------------------------------------------
def build_geometry(sid):
    sp = SPECIES[sid]
    prof = FL.Profile(sp["profile"])
    cl = sp["caudal"]["length"] if sp.get("caudal") else 0.0
    fb = FL.FishBuilder(sid, prof, cl)
    V, F, UV, HH, UU, G = FL.build_body(prof, sp["body_tris"], ring=sp["ring"], p_exp=sp.get("p_exp", 0.85),
                                        belly_bias=sp.get("belly_bias", 0.12),
                                        back_pinch=sp.get("back_pinch", 0.25),
                                        gill=sp.get("gill", (0.215, 0.36, 0.06, 0.24)),
                                        head_flatten=sp.get("head_flatten"))
    COL = np.stack([np.zeros(len(V)), G, np.zeros(len(V)), -np.ones(len(V))], -1)
    if sid == "tang":  # caudal spine: small yellow wedge each side at u 0.92
        V, F, UV, COL = list(V), list(F), list(UV), list(COL)
        for side in (1, -1):
            u0 = 0.915
            H, W, zc = prof.H(u0), prof.W(u0), prof.Zc(u0)
            b = len(V)
            pts = [(u0 - 0.015, side * W * 0.9, zc + 0.01), (u0 + 0.02, side * W * 0.9, zc + 0.005),
                   (u0 + 0.01, side * (W * 0.9 + 0.012), zc + 0.004), (u0 - 0.015, side * W * 0.9, zc - 0.004)]
            V += pts
            UV += [(0.95, 0.55)] * 4
            COL += [(0, 0, 0, -1)] * 4
            F += [(b, b + 1, b + 2) if side > 0 else (b, b + 2, b + 1),
                  (b + 3, b + 2, b + 1) if side > 0 else (b + 3, b + 1, b + 2),
                  (b, b + 2, b + 3) if side > 0 else (b, b + 3, b + 2)]
        V, UV, COL = np.array(V), np.array(UV), np.array(COL)
    if "cirri" in sp:  # fleshy branched tentacles above the eyes, part of the body mesh
        c = sp["cirri"]
        V, F, UV, COL = list(V), list(F), list(UV), list(COL)
        u0 = c["u"]
        top = prof.top(u0)
        for side in (1, -1):
            basep = np.array([u0, side * prof.W(u0) * 0.35, top - 0.006])
            for k in range(c["blades"]):
                ang = math.radians(-30 + 60 * k / max(1, c["blades"] - 1))
                d = np.array([math.sin(ang) * 0.5, side * 0.25, 1.0])
                d = d / np.linalg.norm(d) * c["h"] * (1.0 - 0.25 * (k % 2))
                w = np.array([0.006, 0.0, 0.0])
                b = len(V)
                V += [basep - w, basep + w, basep + d * 0.6 + w * 0.5, basep + d * 0.6 - w * 0.5, basep + d]
                UV += [(u0 + 0.04, 0.55)] * 5
                COL += [(0, 0, 0, -1)] * 5
                F += [(b, b + 1, b + 2, b + 3), (b + 3, b + 2, b + 4)]
        V, UV, COL = np.array(V), np.array(UV), np.array(COL)
    fb.add("Body", V, F, UV, COL, "body", "body")

    fins = []
    for key, kind in (("dorsal", "dorsal"), ("dorsal2", "dorsal2"), ("anal", "anal")):
        if key in sp:
            d = sp[key]
            fv = FL.median_fin(prof, kind, d["u0"], d["u1"], d["h"], rake_deg=d.get("rake", 25),
                               n=d.get("n", 20), ns=d.get("ns", 6), cirri=d.get("cirri", False))
            name = {"dorsal": "FinDorsal", "dorsal2": "FinDorsal2", "anal": "FinAnal"}[key]
            fins.append((name, fv))
    if sp.get("caudal"):
        c = sp["caudal"]
        fins.append(("FinCaudal", FL.caudal_fin(prof, 0.985, c["length"], c["span"], c["shape"])))
    if "ribbon" in sp:
        r = sp["ribbon"]
        fins.append(("FinDorsal", FL.ribbon_fin(prof, r["u_d"], r["u_a"], r["h_d"], r["h_a"])))
    if "pectoral" in sp:
        p = sp["pectoral"]
        for side, nm in ((-1, "FinPectoralL"), (1, "FinPectoralR")):
            fins.append((nm, FL.paired_fin(prof, "pectoral", p["u"], side, p["length"], p["width"], zf=p["zf"],
                                           shape=p["shape"], fingers=p.get("fingers", False), n=p.get("n", 10))))
    if "pelvic" in sp:
        p = sp["pelvic"]
        for side, nm in ((-1, "FinPelvicL"), (1, "FinPelvicR")):
            fins.append((nm, FL.paired_fin(prof, "pelvic", p["u"], side, p["length"], p["length"] * 0.5,
                                           shape="pelvic", out_deg=25, n=6, ns=5)))
    for nm, (fv, ff, fuv, fc) in fins:
        fb.add(nm, fv, ff, fuv, fc, "fin", "fin")

    # eyes
    e = sp["eye"]
    H, W, zc = prof.H(e["u"]), prof.W(e["u"]), prof.Zc(e["u"])
    zf = e["zf"]
    pe = sp.get("p_exp", 0.85)
    ysurf = W * (math.sqrt(max(1 - zf * zf, 0))) ** pe * (1 - sp.get("belly_bias", 0.12) * zf)
    for side, nm in ((-1, "EyeL"), (1, "EyeR")):
        cen = np.array([e["u"], side * (ysurf - 0.45 * e["r"]), zc + zf * H])
        ev, ef, euv, ephi = FL.uv_sphere(cen, e["r"], side)
        fm = (ephi < math.radians(24)).astype(int)
        ecol = np.zeros((len(ev), 4))
        ecol[:, 3] = e["u"]
        fb.add(nm, ev, ef, euv, ecol, "eye", ["iris", "pupil"], face_mats=fm,
               extras={"pivot": (lambda c=cen: fb.gltf_point(c))})

    if False:  # (moray jaw, unused) hinged at u 0.085
        u = np.linspace(0.004, 0.085, 10)
        ring = 8
        Vj, Fj = [], []
        for ui in u:
            Hh, Ww, zz = prof.H(ui) * 0.9, prof.W(ui) * 1.04, prof.Zc(ui)
            for k in range(ring):
                a = math.pi + math.pi * k / (ring - 1)  # lower half: pi..2pi
                Vj.append((ui - 0.003, Ww * math.cos(a), zz - 0.15 * Hh + Hh * 0.85 * math.sin(a) * 1.05))
        for a in range(len(u) - 1):
            for k in range(ring - 1):
                p0 = a * ring + k
                Fj.append((p0, p0 + ring, p0 + ring + 1, p0 + 1))
        Vj = np.array(Vj)
        UVj = np.stack([Vj[:, 0], np.full(len(Vj), 0.3)], -1)
        Cj = np.zeros((len(Vj), 4))
        Cj[:, 3] = Vj[:, 0]
        hp = (0.085, 0.0, prof.Zc(0.085) - 0.1 * prof.H(0.085))
        hinge = (lambda: fb.gltf_point(hp))
        fb.add("JawLower", Vj, Fj, UVj, Cj, "jaw", "body", extras={"hinge": hinge})
    return sp, prof, fb


def paint_textures(sid, sp, prof, outdir):
    tw, th = sp.get("tex", FL.BODY_TEX)
    cv = P.Canvas(tw, th)
    fl = Flank(cv, prof)
    sp["paint"](cv, fl)
    body = os.path.join(outdir, f"{sid}_body.png")
    cv.save(body)
    body1 = os.path.join(outdir, f"{sid}_body_lod1.png")
    cv.downsample(2).save(body1)
    fc = P.Canvas(*FL.FIN_TEX, color="#808080", alpha=0.0)
    sp["fins"](fc)
    fin = os.path.join(outdir, f"{sid}_fin.png")
    fc.save(fin, alpha=True)
    fin1 = os.path.join(outdir, f"{sid}_fin_lod1.png")
    fc.downsample(2).save(fin1, alpha=True)
    e = sp["eye"]
    eye = FL.paint_eye(os.path.join(outdir, f"{sid}_eye.png"), iris=e["iris"], ring=e["ring"], outer=e["outer"],
                       ring_w=e.get("ring_w", 0.18), pupil=e.get("pupil", 0.36))
    extra = {}
    if sid == "tang":  # adult variant without the yellow belly (runtime texture swap)
        cv2 = P.Canvas(tw, th)
        paint_tang(cv2, Flank(cv2, prof), juvenile=False)
        extra["adult"] = cv2.save(os.path.join(C.TEXTURES, "tang_adult_albedo.png"))
    return dict(body=body, body1=body1, fin=fin, fin1=fin1, eye=eye, **extra)


def build_fish(sid, lod1=True, verbose=True):
    C.reset_scene()
    sp, prof, fb = build_geometry(sid)
    tex = paint_textures(sid, sp, prof, C.BUILD)
    imgs = {k: C.load_image(v, name=f"{sid}_{k}") for k, v in tex.items() if k != "adult"}
    mats = {
        "body": C.material(f"{sid}_body", rough=sp["rough"], image=imgs["body"]),
        "fin": C.material(f"{sid}_fin", rough=0.45, image=imgs["fin"], image_alpha=True, double=True),
        "iris": C.material(f"{sid}_iris", rough=0.08, image=imgs["eye"]),
        "pupil": C.material(f"{sid}_pupil", color="#030303", rough=0.05),
    }
    root_extras = dict(species=sid, iridescence=sp["irid"], roughness=sp["rough"], lengthCm=sp["length_cm"],
                       lod=0, unit="body length 1.0 = snout..caudal tip, facing +X",
                       bodyScale=(lambda: round(fb.S, 5)))
    if sid == "tang":
        root_extras["altTextures"] = {"adult": "textures/tang_adult_albedo.png"}
    root, objs = fb.build(mats, root_extras)
    for nm, o in objs.items():
        if o["part"] in ("body", "eye", "jaw"):
            C.recalc_normals(o)
    report = {nm: C.tri_count(o) for nm, o in objs.items()}
    out = C.export_glb(os.path.join(C.MODELS, f"{sid}.glb"), C.all_descendants(root))
    if lod1:
        for nm, o in objs.items():
            p = o["part"]
            if p == "body":
                C.decimate(o, 0.3)
            elif p == "fin":
                C.decimate(o, 0.4)
            elif p == "eye":
                C.decimate(o, 0.6)
        cap = 2000 if sid == "engineer_goby" else 1650
        over = sum(C.tri_count(o) for o in objs.values()) - cap
        if over > 0:  # trim the remainder from the body
            body = [o for o in objs.values() if o["part"] == "body"][0]
            bt = C.tri_count(body)
            C.decimate(body, max(0.3, (bt - over) / bt))
        mats["body"].node_tree.nodes["Image Texture"].image = imgs["body1"]
        mats["fin"].node_tree.nodes["Image Texture"].image = imgs["fin1"]
        root["lod"] = 1
        C.export_glb(os.path.join(C.MODELS, f"{sid}_lod1.glb"), C.all_descendants(root))
        report["lod1_total"] = sum(C.tri_count(o) for o in objs.values())
    if verbose:
        print(f"[fish] {sid}: " + ", ".join(f"{k}={v}" for k, v in report.items()))
    return out, report
