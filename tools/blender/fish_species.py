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
    FL.paint_fin_slot(cv, 0, base=base, alpha=0.9, rays=25, ray_color="#D95A12", margin=m, margin_w=0.12,
                      spine_count=11, incise=0.18)
    FL.paint_fin_slot(cv, 2, base=base, alpha=0.9, rays=14, ray_color="#D95A12", margin=m, margin_w=0.14)
    FL.paint_fin_slot(cv, 3, base=base, alpha=0.88, rays=18, ray_color="#D95A12", margin=m, margin_w=0.12)
    FL.paint_fin_slot(cv, 4, base="#F48A3A", alpha=0.55, rays=14, ray_color="#E8701F", margin=None)
    FL.paint_fin_slot(cv, 5, base=base, alpha=0.85, rays=6, ray_color="#D95A12",
                      margin=m if darwin else "#202020", margin_w=0.15)


def paint_tang(cv, fl, juvenile=True):
    """Paracanthurus hepatus: royal blue with the black "palette": a band from the eye along the upper flank that
    widens into a black field over the upper rear body and sweeps down onto the caudal peduncle; inside that
    field an enclosed blue oval (the palette) sits below the soft dorsal. The nape above the eye band stays
    blue. Tail yellow from the peduncle (caudal spine in the yellow)."""
    u, v = cv.u, cv.v
    cv.vgrad("#3A6EE6", "#2457CF", 0.0, 1.0)
    cv.blend(S(0.55, 1.0, v) * S(0.1, 0.5, u), "#1E4CC0", 0.35)  # slightly deeper blue on the back
    mucus(cv, 0.05, 11)
    scales_overlay(cv, 9, 0.04)
    # lower edge of the black: eye band at v ~0.57 sloping to v ~0.47 by mid-body, then curving down over the
    # rear flank so the peduncle is black above the lateral line
    lower = np.interp(u, [0.08, 0.2, 0.4, 0.58, 0.72, 0.84, 0.92], [0.585, 0.565, 0.52, 0.49, 0.45, 0.40, 0.42])
    upper_eye = np.interp(u, [0.08, 0.16, 0.24], [0.66, 0.70, 0.74])  # top edge of the eye band (front part)
    # front edge of the black field: from the eye band up and back to the dorsal-fin origin region
    front = 0.20 + 0.16 * np.clip((v - 0.62) / 0.38, 0, 1) ** 1.3
    field = S(front - 0.012, front + 0.012, u) * (1 - S(0.915, 0.94, u))
    eyeband = S(0.075, 0.095, u) * (1 - S(front - 0.012, front + 0.012, u)) * (1 - S(upper_eye - 0.012, upper_eye + 0.012, v))
    black = np.maximum(field, eyeband) * S(lower - 0.012, lower + 0.012, v)
    # the palette: an enclosed blue oval in the black field, tilted slightly (higher at the rear)
    lv = fl.lv
    pu, pv = 0.50, 0.73 + 0.08 * (u - 0.5)
    d = np.sqrt(((u - pu) / 0.135) ** 2 + (((v - pv) * lv) / 0.055) ** 2)
    hole = 1 - S(0.92, 1.05, d)
    black = black * (1 - hole)
    cv.blend(black, "#0B0B10")
    # tail: yellow from the caudal peduncle (u 0.92)
    tail = S(0.915, 0.94, u)
    if juvenile:
        belly = (1 - S(0.12, 0.3, v)) * S(0.25, 0.4, u) * (1 - S(0.72, 0.86, u))
        cv.blend(belly, "#F4D01A", 0.6)
    cv.blend(tail, "#F2C500")
    # light blue face sheen and throat
    cv.blend((1 - S(0.0, 0.12, u)) * (1 - S(0.3, 0.6, v)), "#5B8BEF", 0.4)
    dark_mouth(cv, fl, u1=0.025, v0=0.52, v1=0.58, color="#1A2C60")


def fins_tang(cv):
    def blue_to_yellow(T, Sx, rgb):
        m = S(0.55, 0.9, Sx)[..., None]
        return rgb * (1 - m) + hex2rgb("#E8C21A") * m
    FL.paint_fin_slot(cv, 0, base="#2050C8", alpha=0.92, rays=29, ray_color="#14307A", margin="#0A0A0A",
                      margin_w=0.12, spine_count=9, incise=0.10,
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


CARD_BAND = (0.385, 0.505)  # dark bar at the dorsal edge (u), behind the opercle under the first dorsal


def card_band_edges(v):
    """The bar leans back toward the belly a little (Tee's fish b_04 [H]; FishBase: 'a dark brown bar crosses the
    anterior body'); it is ~0.12 SL wide at the back, ~0.11 at the belly."""
    lean = (1.0 - v) * 0.035
    return CARD_BAND[0] + lean, CARD_BAND[1] + lean - 0.012 * (1.0 - v)


def paint_cardinal(cv, fl):
    """S. nematoptera after Tee's own fish (reference b_04, 2026-10-13 [H]) and ref_cardinal.jpg: lemon-yellow head
    and nape up to the bar, a pale silvery cheek behind the eye, a dark brown-black bar with a scaly net from the
    first dorsal down to the belly, and a pinkish-white rear body with round orange-brown spots that get smaller
    toward the peduncle (FishBase morphology: 'head yellow ... dark brown bar ... posterior body with purple
    [red-brown] spots')."""
    u, v = cv.u, cv.v
    b0, b1 = card_band_edges(v)
    # rear: pinkish white, a faint warm flush along the back
    cv.vgrad("#F4E9E6", "#EDDCD6", 0.0, 1.0)
    cv.blend(P.value_noise(cv.h, cv.w, 40, 20, 23) * 0.4, "#E9D3CD", 0.5)
    # head: lemon yellow, brighter on the nape/forehead, paler on the throat
    head = 1 - S(b0 - 0.012, b0 + 0.004, u)
    hc = P.Canvas(cv.w, cv.h)
    hc.vgrad("#EFE6A6", "#E2CB3A", 0.0, 0.85)
    cv.rgb = cv.rgb * (1 - head[..., None]) + hc.rgb * head[..., None]
    cv.blend(head * S(0.55, 0.9, v) * (1 - S(0.15, 0.38, u)), "#E6CE32", 0.6)
    # silvery-cream cheek and opercle behind the eye
    cheek = np.exp(-(((u - 0.24) / 0.08) ** 2 + ((v - 0.40) / 0.14) ** 2))
    cv.blend(cheek * head, "#EEE8CC", 0.55)
    # yellow wash carries a little way past the bar on the belly (Tee's fish)
    cv.blend((1 - S(b1 + 0.0, b1 + 0.06, u)) * S(b1 - 0.02, b1, u) * (1 - S(0.15, 0.35, v)), "#EBDD8A", 0.45)
    mucus(cv, 0.04, 21)
    scales_overlay(cv, 12, 0.05)
    # the bar: dark net of scale outlines over brown-black scale centres, soft edges
    band = S(b0 - 0.006, b0 + 0.006, u) * (1 - S(b1 - 0.006, b1 + 0.006, u)) * S(0.03, 0.09, v)
    net = P.hex_net(cv, 15, 1.7)
    bandc = (1 - net[..., None]) * hex2rgb("#4E4238") + net[..., None] * hex2rgb("#0D0A08")
    cv.rgb = cv.rgb * (1 - band[..., None]) + bandc * band[..., None]
    # orange-brown spots on the rear body, shrinking toward the peduncle, sparse low on the flank
    rs = np.random.default_rng(7)
    m = np.zeros_like(u)
    pts = []
    for (lo, hi, n, rmin, rmax, sp) in ((0.53, 0.75, 44, 0.012, 0.016, 0.038), (0.75, 0.97, 36, 0.009, 0.012, 0.03)):
        mm, pp = fl.spots(rs, n, rmin, rmax, sp, (lo, hi, 0.07, 0.96),
                          weight_fn=lambda uu, vv: 0.3 + 0.7 * S(0.12, 0.35, vv), existing=None)
        m = np.maximum(m, mm)
        pts += pp
    cv.blend(m, "#B65A38")
    cv.blend(m * S(0.75, 1.0, u), "#A04A30", 0.4)
    # dark lip line
    dark_mouth(cv, fl, u1=0.035, v0=0.5, v1=0.58, color="#8C7A30", op=0.5)


def fins_cardinal(cv):
    clear = "#E2E0DD"
    def dark_lead(w, col):
        def f(T, Sx, rgb):
            m = (1 - S(w - 0.04, w + 0.04, T))[..., None]
            return rgb * (1 - m) + hex2rgb(col) * m
        return f
    # first dorsal VII: yellow with the bar running up its front spines (FishBase: bar 'extends onto the first dorsal')
    FL.paint_fin_slot(cv, 0, base="#E6CC3A", alpha=0.9, rays=7, ray_color="#9A7A20", spine_count=7,
                      margin="#E07A2A", margin_w=0.08, incise=0.1, base_fn=dark_lead(0.38, "#3A2A1C"))
    # second dorsal I,9 and anal II,9-10: tall, translucent grey with a dusky leading edge and a white rim
    FL.paint_fin_slot(cv, 1, base=clear, alpha=0.45, rays=10, ray_color="#8A8580", margin="#F2F2F2",
                      margin_w=0.05, base_fn=dark_lead(0.1, "#5A5450"))
    FL.paint_fin_slot(cv, 2, base=clear, alpha=0.45, rays=10, ray_color="#8A8580", margin="#F2F2F2",
                      margin_w=0.05, base_fn=dark_lead(0.08, "#6A6460"))
    # forked caudal: translucent, faintly dusky
    FL.paint_fin_slot(cv, 3, base="#C8C3C0", alpha=0.5, rays=18, ray_color="#6E6A66", margin="#E6E6E6",
                      margin_w=0.04)
    FL.paint_fin_slot(cv, 4, base=clear, alpha=0.28, rays=12, ray_color="#B8B4AF")
    # long pelvics: yellow with an orange-red trailing rim and a dark leading spine (Tee's fish b_04, ref photo)
    FL.paint_fin_slot(cv, 5, base="#EDCB3A", alpha=0.95, rays=6, ray_color="#C8902A", margin="#D8522A",
                      margin_w=0.16, base_fn=dark_lead(0.14, "#5A3A1E"))


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
    # electric-blue borders hug the stripe on the rear body (strongest at the peduncle), the lower one fainter
    vs = (v - cen) * lv
    edge_up = (1 - S(0.003, 0.008, np.abs(vs - half_w - 0.006))) * S(0.3, 0.6, u)
    edge_dn = (1 - S(0.003, 0.007, np.abs(vs + half_w + 0.005))) * S(0.45, 0.75, u)
    cv.blend(edge_up, "#3E9BFF", 0.8)
    cv.blend(edge_dn, "#5AA8F5", 0.55)
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
    """Juvenile engineer goby (ref_engineer_goby.jpg): black, bright yellow-cream wavy vertical bars (slightly
    S-shaped, slanted) that join pairwise at the top or bottom and through a broken wavy longitudinal line into a
    maze; yellow lines on the head."""
    cv.vgrad("#0C0D0A", "#15170F", 0.0, 1.0)
    mucus(cv, 0.03, 41)
    rs = np.random.default_rng(43)
    m = np.zeros_like(cv.u)
    w = 0.0092  # bold bars ~1/4 of their spacing, as in the photo

    def line(pts, wd=w):
        return fl.stroke(pts, [wd] * len(pts), soft=0.003)
    xs = []
    x = 0.075
    while x < 0.965:
        xs.append(x)
        x += rs.uniform(0.038, 0.05)
    tops, bots = [], []
    for k, x0 in enumerate(xs):
        ph = rs.uniform(0, 6.28)
        slant = rs.uniform(0.006, 0.016)
        top, bot = rs.uniform(0.86, 0.95), rs.uniform(0.06, 0.16)
        pts = []
        for t in np.linspace(0, 1, 9):
            v = bot + (top - bot) * t
            pts.append((x0 + slant * (t - 0.5) + 0.009 * math.sin(math.pi * 1.6 * t + ph), v))
        r = rs.uniform()
        if r < 0.22:      # break at mid-flank
            m = np.maximum(m, line(pts[:4]))
            m = np.maximum(m, line(pts[5:]))
        elif r < 0.32:    # short bar (upper half only)
            m = np.maximum(m, line(pts[4:]))
        else:
            m = np.maximum(m, line(pts))
        tops.append(pts[-1])
        bots.append(pts[0])
    for k in range(len(xs) - 1):  # U / inverted-U joins between neighbours
        r = rs.uniform()
        if r < 0.3:
            a, b = tops[k], tops[k + 1]
            m = np.maximum(m, line([a, ((a[0] + b[0]) / 2, max(a[1], b[1]) + 0.025), b]))
        elif r < 0.55:
            a, b = bots[k], bots[k + 1]
            m = np.maximum(m, line([a, ((a[0] + b[0]) / 2, min(a[1], b[1]) - 0.02), b]))
    x = 0.42
    while x < 0.95:  # broken wavy longitudinal line on the rear half
        seg = rs.uniform(0.04, 0.09)
        xx = np.linspace(x, min(x + seg, 0.97), 6)
        m = np.maximum(m, line([(q, 0.62 + 0.05 * math.sin(q * 70 + 0.8)) for q in xx], 0.0075))
        x += seg + rs.uniform(0.03, 0.07)
    # head: lines over the snout, around the eye and on the cheek
    m = np.maximum(m, line([(0.004, 0.55), (0.02, 0.74), (0.045, 0.9)], 0.007))
    m = np.maximum(m, line([(0.012, 0.36), (0.035, 0.28), (0.06, 0.36), (0.07, 0.55)], 0.0068))
    m = np.maximum(m, line([(0.05, 0.68), (0.058, 0.8), (0.052, 0.92)], 0.0065))
    cv.blend(np.clip(m, 0, 1), "#E8E472")
    dark_mouth(cv, fl, u1=0.03, v0=0.38, v1=0.44, color="#050505", op=0.7)


def fins_engineer_goby(cv):
    def bars(T, Sx, rgb, a):
        # the body bars run up into the fin base as short yellow ticks (photo), fading toward the margin
        ph = np.abs(((T * 18 + 0.15 * np.sin(T * 40)) % 1.0) - 0.5)
        m = (1 - S(0.07, 0.13, ph)) * (1 - S(0.4, 0.75, Sx))
        return rgb * (1 - m[..., None]) + hex2rgb("#E8E472") * m[..., None], a
    FL.paint_fin_slot(cv, 6, base="#141510", alpha=0.95, rays=0, margin="#B8B460", margin_w=0.06, edge_fade=0.1,
                      spots=bars)
    FL.paint_fin_slot(cv, 5, base="#1A1A16", alpha=0.75, rays=4, ray_color="#0A0A08")
    FL.paint_fin_slot(cv, 4, base="#1A1A16", alpha=0.7, rays=8, ray_color="#0A0A08")


def paint_hawkfish(cv, fl):
    """Falco hawkfish (ref_hawkfish.jpg; Fishes of Australia: "whitish with reddish-brown saddles that taper and
    break up into orange to reddish-brown blotches toward the rear; a pair of reddish bars radiating from each
    eye"): white body, five red-brown saddles from the dorsal ridge that narrow downward and dissolve into spots
    below the lateral line, dense orange-red spotting on the head and back, two red bars behind the eye."""
    u, v = cv.u, cv.v
    cv.vgrad("#F4ECE6", "#FAF6F2", 0.0, 0.6)
    mucus(cv, 0.03, 51)
    scales_overlay(cv, 12, 0.06)
    rs = np.random.default_rng(52)
    lv = fl.lv
    brk = P.fbm(cv.h, cv.w, 30, 4, 53)
    # saddles: wide on the back, tapering toward the belly; their lower part breaks into blotches
    sad = np.zeros_like(u)
    for k, (cu, wd, depth) in enumerate([(0.24, 0.05, 0.42), (0.37, 0.055, 0.40), (0.50, 0.05, 0.40),
                                         (0.63, 0.045, 0.36), (0.78, 0.04, 0.32)]):
        lean = cu + 0.04 * (1 - v)  # saddles slant slightly backward toward the belly
        half = wd * (0.35 + 0.65 * S(1 - depth - 0.05, 1.0, v))  # narrower lower down
        m = 1 - S(half - 0.008, half + 0.008, np.abs(u - lean))
        reach = S(1 - depth - 0.12, 1 - depth + 0.06, v)  # fades out toward its lower end
        holes = S(0.6, 0.7, brk + 0.3 * (1 - S(0.5, 0.92, v)))  # broken edges / blotches below
        sad = np.maximum(sad, m * reach * (1 - holes))
    cv.blend(sad, "#C04428")
    cv.blend(sad * S(0.8, 1.0, v), "#9A3020", 0.4)
    # spots: dense on the back and head, orange-red, sparse and smaller below
    dens = lambda uu, vv: 0.12 + 0.88 * S(0.3, 0.85, vv) * (1 - 0.4 * S(0.6, 0.95, uu))  # noqa: E731
    m, _ = fl.spots(rs, 120, 0.005, 0.010, 0.015, (0.02, 0.95, 0.15, 0.99), weight_fn=dens)
    cv.blend(m, "#E0602E")
    m3, _ = fl.spots(rs, 40, 0.004, 0.007, 0.012, (0.05, 0.9, 0.55, 0.99))
    cv.blend(m3, "#C23C1C")
    # head: red ring around the eye and two red bars radiating back from it (upper to the nape/opercle top,
    # lower across the cheek to the opercle), white snout
    eu, ev = 0.10, 0.65  # eye centre: v = 0.5 + asin(zf)/pi
    cv.blend(fl.disc(eu, ev, 0.045, 0.008), "#D8401E", 0.75)
    cv.blend(fl.stroke([(eu + 0.03, ev - 0.03), (eu + 0.08, ev - 0.10), (eu + 0.13, ev - 0.18)],
                       [0.011, 0.010, 0.006], soft=0.004), "#D83A1A")
    cv.blend(fl.stroke([(eu + 0.035, ev + 0.01), (eu + 0.09, ev - 0.0), (eu + 0.15, ev - 0.03)],
                       [0.009, 0.008, 0.005], soft=0.004), "#D83A1A")
    m2, _ = fl.spots(rs, 22, 0.004, 0.007, 0.012, (0.02, 0.2, 0.45, 0.99))
    cv.blend(m2, "#D24A22", 0.9)
    cv.blend((1 - S(0.0, 0.05, u)) * (1 - S(0.7, 0.85, v)), "#FBF3EC", 0.6)  # pale snout
    dark_mouth(cv, fl, u1=0.03, v0=0.38, v1=0.46, color="#C0502E", op=0.5)


def fins_hawkfish(cv):
    def spotted(T, Sx, rgb, a, n=10, rows=4, color="#E0602E"):
        ph = np.abs(((T * n) % 1.0) - 0.5)
        rw = np.abs(((Sx * rows) % 1.0) - 0.5)
        m = (1 - S(0.15, 0.3, ph)) * (1 - S(0.12, 0.3, rw)) * (Sx < 0.9)
        return rgb * (1 - m[..., None]) + hex2rgb(color) * m[..., None], np.maximum(a, 0.9 * m)
    # dorsal X,12: deeply incised spiny part (free spine tips carry the cirri tufts), red-brown spots on the soft
    # part, membrane red-tinged at the base under the saddles
    FL.paint_fin_slot(cv, 0, base="#F2E2D2", alpha=0.6, rays=20, ray_color="#C06040", spine_count=10, incise=0.42,
                      spots=lambda T, Sx, rgb, a: spotted(T, Sx, rgb, a, 20, 4), margin="#F8EEE6", margin_w=0.05)
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

    # slot 7: the cirri tufts at the dorsal spine tips (opaque, white with red-orange bases)
    def cirri(T, Sx, rgb):
        m = (1 - S(0.2, 0.5, Sx))[..., None]
        return hex2rgb("#F6EEE8") * (1 - m) + hex2rgb("#D8502A") * m
    FL.paint_fin_slot(cv, 7, base="#F6EEE8", alpha=1.0, rays=0, base_fn=cirri, edge_fade=0.02)


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
    """S. fasciatus fins: translucent olive with rows of small dark-brown spots/oblique dashes (dorsal XII,18-20,
    notched between the spiny and soft parts; anal II,19-20); caudal and pectorals spotted in vertical rows."""
    def dashes(T, Sx, rgb, a, n=30, rows=4, slant=0.35, seed=75):
        h, w = T.shape
        jit = P.fbm(h, w, 20, 2, seed)
        ph = np.abs((((T + slant * Sx / n) * n + 0.4 * jit) % 1.0) - 0.5)
        rw = np.abs(((Sx * rows + 0.3 * jit) % 1.0) - 0.5)
        m = (1 - S(0.12, 0.26, ph)) * (1 - S(0.1, 0.26, rw)) * (Sx < 0.92)
        return rgb * (1 - 0.85 * m[..., None]) + hex2rgb("#3A3420") * 0.85 * m[..., None], np.maximum(a, 0.8 * m)
    FL.paint_fin_slot(cv, 0, base="#8A845A", alpha=0.42, rays=31, ray_color="#5A5434", spine_count=12, incise=0.12,
                      spots=lambda T, Sx, rgb, a: dashes(T, Sx, rgb, a, 31, 4), margin="#C8B080", margin_w=0.06)
    FL.paint_fin_slot(cv, 2, base="#8A845A", alpha=0.45, rays=21, ray_color="#5A5434",
                      spots=lambda T, Sx, rgb, a: dashes(T, Sx, rgb, a, 21, 3, seed=76))
    FL.paint_fin_slot(cv, 3, base="#8A845A", alpha=0.5, rays=14, ray_color="#5A5434",
                      spots=lambda T, Sx, rgb, a: dashes(T, Sx, rgb, a, 14, 5, 0.0, 77))
    FL.paint_fin_slot(cv, 4, base="#8A845A", alpha=0.5, rays=14, ray_color="#5A5434",
                      spots=lambda T, Sx, rgb, a: dashes(T, Sx, rgb, a, 14, 4, 0.0, 78))
    FL.paint_fin_slot(cv, 5, base="#9A9466", alpha=0.6, rays=3, ray_color="#5A5434")


def paint_tailspot(cv, fl):
    """Ecsenius stigmatura (FishBase: "grey to brown-orange, head grey-blue; yellow iris; a yellow line from below
    the eye to above the pectoral axil; black spot on the caudal-fin base") with the blue + orange lines under the
    eye seen in docs/reference/blenny.jpg: grey-blue / purplish-brown head, body warming to orange-tan and
    yellow-cream at the rear, a round black spot at the caudal base ringed paler."""
    u, v = cv.u, cv.v
    front = P.Canvas(cv.w, cv.h)
    front.vgrad("#8C8290", "#5E4C5A", 0.0, 1.0)
    mid = P.Canvas(cv.w, cv.h)
    mid.vgrad("#D8B88A", "#9A7058", 0.0, 1.0)
    rear = P.Canvas(cv.w, cv.h)
    rear.vgrad("#F2E2B0", "#E2C078", 0.0, 1.0)
    t1 = S(0.2, 0.5, u)[..., None]
    t2 = S(0.5, 0.85, u)[..., None]
    cv.rgb = (front.rgb * (1 - t1) + mid.rgb * t1) * (1 - t2) + rear.rgb * t2
    head = 1 - S(0.12, 0.26, u)
    cv.blend(head * S(0.55, 0.95, v), "#4E3A52", 0.5)  # dark purplish crown
    cv.blend(head * (1 - S(0.2, 0.45, v)), "#A8A8B8", 0.4)  # grey-blue throat
    mucus(cv, 0.05, 81)
    scales_overlay(cv, 9, 0.03)  # combtooth blennies are scaleless; this is only a faint skin texture
    lv = fl.lv
    # eye at v ~0.69; blue line just below it, yellow-orange line under that running back above the pectoral
    yl = np.interp(u, [0.015, 0.1, 0.2], [0.50, 0.50, 0.52])
    blue = P.band(u, 0.015, 0.16, 0.008) * (1 - S(0.004, 0.0075, np.abs(v - 0.585) * lv))
    orng = P.band(u, 0.015, 0.20, 0.012) * (1 - S(0.004, 0.0075, np.abs(v - yl) * lv))
    cv.blend(orng, "#F29A1D")
    cv.blend(blue, "#2E6FD8")
    # black tail-base spot with a faint pale ring
    cv.blend(fl.disc(0.94, 0.5, 0.032, 0.006), "#F6EAC0", 0.5)
    cv.blend(fl.disc(0.94, 0.5, 0.024, 0.004), "#101010")
    cv.blend(P.band(u, 0.175, 0.185, 0.004) * P.band(v, 0.3, 0.75, 0.05), "#4A3038", 0.4)
    dark_mouth(cv, fl, u1=0.02, v0=0.36, v1=0.44, color="#2A1A1A", op=0.6)


def fins_tailspot(cv):
    FL.paint_fin_slot(cv, 0, base="#C8B88A", alpha=0.45, rays=26, ray_color="#8A7A5A", margin="#E8A040",
                      margin_w=0.07)
    FL.paint_fin_slot(cv, 2, base="#D8C890", alpha=0.45, rays=20, ray_color="#8A7A5A")
    FL.paint_fin_slot(cv, 3, base="#E8D898", alpha=0.5, rays=12, ray_color="#A89860")
    FL.paint_fin_slot(cv, 4, base="#C8B88A", alpha=0.35, rays=12, ray_color="#8A7A5A")
    FL.paint_fin_slot(cv, 5, base="#C8B88A", alpha=0.45, rays=3, ray_color="#8A7A5A")


def paint_bicolor(cv, fl):
    """Ecsenius bicolor, bicolour phase (research 50 §1b; FishBase: "black anteriorly, yellow posteriorly"; hobby
    sources: blue-black to dark brown front, orange-yellow rear). The split falls a little behind the dorsal-fin
    origin and runs obliquely back toward the belly [DESIGN ESTIMATE from photos]; a pink-orange curved mark behind
    the eye on every colour form [OBSERVATION OzAnimals]; scaleless skin, slightly paler throat."""
    u, v = cv.u, cv.v
    front = P.Canvas(cv.w, cv.h)
    front.vgrad("#2A2238", "#1A1626", 0.0, 1.0)
    rear = P.Canvas(cv.w, cv.h)
    rear.vgrad("#F2A21E", "#E68A12", 0.0, 1.0)
    # oblique split: ~0.52 L at the back, ~0.42 L on the belly, with a soft orange-brown seam
    split = 0.42 + 0.10 * v
    t = S(split - 0.03, split + 0.05, u)[..., None]
    cv.rgb = front.rgb * (1 - t) + rear.rgb * t
    seam = P.band(u - split, -0.03, 0.05, 0.02)
    cv.blend(seam, "#7A3A20", 0.35)
    # a faint blue sheen over the dark forebody (males brighter and bluer; research 50 §1b)
    n1 = P.fbm(cv.h, cv.w, 30, 3, 91)
    cv.blend((1 - S(split - 0.12, split, u)) * S(0.35, 0.9, v) * S(0.45, 0.7, n1), "#34407A", 0.35)
    cv.blend((1 - S(0.08, 0.22, u)) * (1 - S(0.15, 0.4, v)), "#4A4058", 0.45)   # paler throat
    mucus(cv, 0.05, 92)
    scales_overlay(cv, 9, 0.025)   # scaleless; a faint skin texture only
    lv = fl.lv
    # the curved pink-orange mark from behind the eye down toward the opercle
    pts = [(0.135, 0.74), (0.15, 0.68), (0.155, 0.6), (0.15, 0.53)]
    cv.blend(fl.stroke(pts, [0.004, 0.005, 0.005, 0.003], soft=0.003), "#E86A7A", 0.85)
    # the yellow rear warms to orange at the peduncle
    cv.blend(S(0.8, 0.98, u), "#F08A10", 0.4)
    dark_mouth(cv, fl, u1=0.02, v0=0.36, v1=0.44, color="#120A12", op=0.6)


def fins_bicolor(cv):
    """Dorsal dark over the forebody, yellow-orange over the rear (the split carries up into the fin); anal the same;
    caudal and its rays orange-yellow; pectorals clear yellowish; pelvics dark."""
    def split(Tsplit, front, rear):
        def fn(T, Sx, rgb):
            m = S(Tsplit - 0.06, Tsplit + 0.06, T)[..., None]
            return hex2rgb(front) * (1 - m) + hex2rgb(rear) * m
        return fn
    FL.paint_fin_slot(cv, 0, base="#2A2238", alpha=0.6, rays=30, ray_color="#1A1420", margin="#4A5AA8", margin_w=0.06,
                      base_fn=split(0.42, "#2A2238", "#E8961C"))
    FL.paint_fin_slot(cv, 2, base="#E8961C", alpha=0.55, rays=20, ray_color="#B86A10",
                      base_fn=split(0.15, "#3A2A30", "#E8961C"))
    FL.paint_fin_slot(cv, 3, base="#F0A020", alpha=0.6, rays=13, ray_color="#C07410")
    FL.paint_fin_slot(cv, 4, base="#E8C060", alpha=0.35, rays=13, ray_color="#B8903A")
    FL.paint_fin_slot(cv, 5, base="#2A2238", alpha=0.5, rays=3, ray_color="#1A1420")


def paint_midas(cv, fl):
    """Ecsenius midas, the common golden-orange phase (research 50 §2b; FishBase "golden orange, black spot around the
    anus"; hobby: orange to salmon body with gold and yellow highlights, blue flashes on the fins and round the eye).
    The orange phase mimics and schools with lyretail anthias (Pseudanthias squamipinnis)."""
    u, v = cv.u, cv.v
    cv.vgrad("#F4B03A", "#E07A1C", 0.0, 1.0)
    cv.blend(S(0.55, 1.0, v), "#D86A1A", 0.35)          # deeper orange back
    cv.blend(1 - S(0.0, 0.35, v), "#F8D070", 0.45)      # gold belly
    n1 = P.fbm(cv.h, cv.w, 40, 3, 95)
    cv.blend(S(0.55, 0.7, n1) * P.band(v, 0.25, 0.85, 0.1), "#F8C850", 0.25)   # gold highlights
    mucus(cv, 0.05, 96)
    scales_overlay(cv, 9, 0.025)
    # a soft salmon cast on the head, the snout a touch darker
    cv.blend(1 - S(0.06, 0.2, u), "#E8844A", 0.35)
    # blue ring round the eye (u 0.075, v 0.67) drawn on the skin round the eyeball
    ring = fl.disc(0.075, 0.67, 0.046, 0.004) - fl.disc(0.075, 0.67, 0.04, 0.004)
    cv.blend(np.clip(ring, 0, 1), "#3A8AE8", 0.45)
    # black spot round the vent, just ahead of the anal fin origin, low on the flank
    cv.blend(fl.disc(0.47, 0.06, 0.018, 0.006), "#151010", 0.9)
    dark_mouth(cv, fl, u1=0.02, v0=0.36, v1=0.44, color="#3A1A10", op=0.55)


def fins_midas(cv):
    """Orange-yellow fins with thin blue margins on the dorsal, anal and the lyre tail's lobes (research 50 §2b)."""
    FL.paint_fin_slot(cv, 0, base="#F0A030", alpha=0.5, rays=30, ray_color="#C87818", margin="#3A8AE8", margin_w=0.07)
    FL.paint_fin_slot(cv, 2, base="#F0A030", alpha=0.5, rays=20, ray_color="#C87818", margin="#3A8AE8", margin_w=0.08)
    FL.paint_fin_slot(cv, 3, base="#F4B040", alpha=0.55, rays=14, ray_color="#C87818",
                      base_fn=lambda T, Sx, rgb: rgb * (1 - (np.abs(T - 0.5) > 0.42)[..., None] * 0.6)
                      + hex2rgb("#3A8AE8") * (np.abs(T - 0.5) > 0.42)[..., None] * 0.6)
    FL.paint_fin_slot(cv, 4, base="#F4C060", alpha=0.35, rays=13, ray_color="#C89838")
    FL.paint_fin_slot(cv, 5, base="#F0B040", alpha=0.45, rays=3, ray_color="#C87818")


def paint_dottyback(cv, fl):
    """Electric Indigo dottyback (P. fridmani x P. sankeyi, ORA: "three colours: light blue to turquoise top, a
    sharply defined black band down the body, fresh white underside, tail filament"; ref_dottyback.jpg): vivid
    periwinkle-to-turquoise back, a pale line above the broad black band that runs snout -> eye -> tail, a thin
    dark line along the dorsal base, white belly."""
    u, v = cv.u, cv.v
    cv.vgrad("#F2F5FF", "#6E86F0", 0.0, 0.72)
    cv.blend(S(0.78, 1.0, v), "#6AAEF0", 0.45)  # turquoise toward the dorsal ridge
    mucus(cv, 0.03, 91)
    scales_overlay(cv, 10, 0.05)
    c = 0.60 - 0.04 * u
    hw = 0.075 - 0.025 * u
    cv.blend(1 - S(0.015, 0.035, np.abs(v - (c + hw + 0.04))), "#DCE8FF", 0.8)
    cv.blend(1 - S(hw - 0.008, hw + 0.008, np.abs(v - c)), "#0A0A14")
    cv.blend(1 - S(0.012, 0.025, np.abs(v - 0.93)), "#141428", 0.9)
    cv.blend(1 - S(0.22, 0.42, v), "#F4F6FF", 0.75)
    dark_mouth(cv, fl, u1=0.02, v0=0.5, v1=0.56, color="#101020", op=0.7)


def fins_dottyback(cv):
    edge = "#6A90F0"
    # dorsal III,~26 / anal III,~14: translucent blue with a dark base continuing the dorsal line, blue edge
    dark_base = lambda T, Sx, rgb: rgb * (1 - (1 - S(0.0, 0.45, Sx))[..., None] * 0.75)  # noqa: E731
    FL.paint_fin_slot(cv, 0, base="#5A6ED8", alpha=0.75, rays=29, ray_color="#202850", margin=edge, margin_w=0.12,
                      base_fn=dark_base, spine_count=3)
    FL.paint_fin_slot(cv, 2, base="#5A6ED8", alpha=0.75, rays=17, ray_color="#202850", margin=edge, margin_w=0.12,
                      base_fn=dark_base, spine_count=3)

    def dark_center(T, Sx, rgb, a):  # the black band runs out along the middle rays to the filament
        m = (1 - S(0.08, 0.2, np.abs(T - 0.5))) * (0.55 + 0.45 * S(0.0, 0.6, Sx))
        return rgb * (1 - m[..., None]) + hex2rgb("#0A0A16") * m[..., None], np.maximum(a, 0.9 * m)
    FL.paint_fin_slot(cv, 3, base="#7A8CE0", alpha=0.8, rays=16, ray_color="#4A5AB0", margin=edge, margin_w=0.06,
                      spots=dark_center, edge_fade=0.03)
    FL.paint_fin_slot(cv, 4, base="#A8B8F0", alpha=0.35, rays=12, ray_color="#7080C0")
    FL.paint_fin_slot(cv, 5, base="#E0E6FF", alpha=0.6, rays=4, ray_color="#8090D0")


def paint_diamond_goby(cv, fl):
    """Diamond goby, Valenciennea puellaris, painted from Tee's photos (2026-10-13, p08 side view, p07): pale
    lavender-white body; two rows of orange dashes (a bold mid-side row and a finer row along the back), an orange line
    along the lower side from the cheek to the tail base; orange spots on the head; blue iridescent spots and short
    stripes on the cheek under the eye (Fishes of Australia 177 agrees: orange dashes, orange lower-side stripe, blue
    head markings)."""
    u, v = cv.u, cv.v
    cv.vgrad("#F0EAF3", "#D9CDE3", 0.0, 1.0)            # lavender-white, a little more lilac on the back
    cv.blend(1 - S(0.08, 0.3, v), "#F8F6F8", 0.6)      # bright belly
    mucus(cv, 0.03, 161)
    scales_overlay(cv, 9, 0.05)
    rs = np.random.default_rng(162)
    # mid-side row: bold orange dashes, ~0.03 L long, every ~0.065 L (p08)
    x = 0.2
    while x < 0.93:
        y0 = 0.57 + 0.01 * math.sin(x * 9)
        ln = rs.uniform(0.022, 0.034)
        cv.blend(fl.stroke([(x, y0), (x + ln, y0 - 0.003)], [0.009, 0.009], soft=0.0025), "#EE9030", 0.95)
        x += rs.uniform(0.055, 0.075)
    # upper row along the back: smaller, paler dashes
    x = 0.22
    while x < 0.9:
        y0 = 0.82 + 0.01 * math.sin(x * 13)
        cv.blend(fl.stroke([(x, y0), (x + rs.uniform(0.01, 0.018), y0)], [0.004, 0.004], soft=0.002), "#EEA040", 0.85)
        x += rs.uniform(0.05, 0.07)
    # the lower-side line: continuous orange from the cheek to the tail base
    low = [(0.02, 0.36), (0.1, 0.33), (0.2, 0.32), (0.5, 0.31), (0.8, 0.33), (0.95, 0.38)]
    cv.blend(fl.stroke(low, [0.009, 0.009, 0.0065, 0.006, 0.005, 0.0035], soft=0.0025), "#EA902E", 0.95)
    # head: orange spots over the top and behind the eye
    hs, _ = fl.spots(rs, 16, 0.0035, 0.006, 0.012, (0.03, 0.2, 0.55, 0.95))
    cv.blend(hs, "#EC9234", 0.9)
    # cheek: blue iridescent spots and short stripes under the eye, above the orange line
    cd, _ = fl.spots(rs, 12, 0.0045, 0.0075, 0.012, (0.03, 0.19, 0.38, 0.52))
    cv.blend(cd, "#7EB6F2", 0.9)
    for (a0, b0, a1, b1) in ((0.04, 0.44, 0.08, 0.46), (0.1, 0.45, 0.15, 0.44), (0.06, 0.4, 0.11, 0.41)):
        cv.blend(fl.stroke([(a0, b0), (a1, b1)], [0.003, 0.003], soft=0.002), "#6AA8F0", 0.85)
    dark_mouth(cv, fl, u1=0.025, v0=0.4, v1=0.46, color="#B88A80", op=0.5)


def fins_diamond_goby(cv):
    def orange_spots(T, Sx, rgb, a, n=8, rows=3):
        ph = np.abs(((T * n) % 1.0) - 0.5)
        rw = np.abs(((Sx * rows + 0.3) % 1.0) - 0.5)
        m = (1 - S(0.15, 0.3, ph)) * (1 - S(0.12, 0.28, rw)) * (Sx < 0.85)
        return rgb * (1 - m[..., None]) + hex2rgb("#E8862E") * m[..., None], np.maximum(a, 0.85 * m)
    # translucent fins with faint orange spotting (Tee's photos p08)
    FL.paint_fin_slot(cv, 0, base="#F0EEF4", alpha=0.35, rays=6, ray_color="#D0CCD8", spine_count=6,
                      spots=lambda T, Sx, rgb, a: orange_spots(T, Sx, rgb, a * 0.8, 6, 3))
    FL.paint_fin_slot(cv, 1, base="#F0EEF4", alpha=0.32, rays=12, ray_color="#D0CCD8",
                      spots=lambda T, Sx, rgb, a: orange_spots(T, Sx, rgb, a * 0.8, 10, 3), margin="#9CC4F0", margin_w=0.06)
    FL.paint_fin_slot(cv, 2, base="#F4F2F6", alpha=0.3, rays=12, ray_color="#D8D4DC", margin="#A8CCF0",
                      margin_w=0.06)
    FL.paint_fin_slot(cv, 3, base="#F0EEF2", alpha=0.35, rays=14, ray_color="#D0CCD4",
                      spots=lambda T, Sx, rgb, a: orange_spots(T, Sx, rgb, a * 0.8, 12, 4))
    FL.paint_fin_slot(cv, 4, base="#F2F2F2", alpha=0.3, rays=14, ray_color="#D8D8D8")
    FL.paint_fin_slot(cv, 5, base="#F4F4F4", alpha=0.4, rays=5, ray_color="#D8D8D8")


def _cells(cv, n, seed, region):
    """Voronoi cell-edge distance (px) over the canvas for n sites restricted to region(u, v) -> bool."""
    rs = np.random.default_rng(seed)
    pts = []
    while len(pts) < n:
        u, v = rs.random(), rs.random()
        if region(u, v):
            pts.append((u * cv.w, (1 - v) * cv.h))
    yy, xx = np.mgrid[0:cv.h, 0:cv.w].astype(np.float32)
    f1 = np.full((cv.h, cv.w), 1e9, np.float32)
    f2 = np.full((cv.h, cv.w), 1e9, np.float32)
    for (px, py) in pts:
        d = np.sqrt((xx - px) ** 2 + (yy - py) ** 2)
        closer = d < f1
        f2 = np.where(closer, f1, np.minimum(f2, d))
        f1 = np.where(closer, d, f1)
    return f2 - f1


def paint_leopard_wrasse(cv, fl):
    """Blue star leopard wrasse, Macropharyngodon bipartitus, female / initial phase, painted from Tee's photos
    (2026-10-13, p06 side view, p09 flank): orange-brown body densely covered in small pale-blue dots, a black blotch on
    the chest and lower belly behind the pectoral covered by a bright blue-white mesh, and a yellow-tan head with dark
    lines and a pale patch behind the eye (Seatizens: female "orange-red with white spots ... large black area over chest
    and abdomen with a bright iridescent blue network")."""
    u, v = cv.u, cv.v
    cv.vgrad("#B0702E", "#874A20", 0.0, 1.0)                           # orange-brown, darker on the back
    cv.blend(S(0.8, 1.0, v), "#7A4220", 0.4)
    head = 1 - S(0.17, 0.23, u)
    cv.blend(head, "#C4A55E", 0.95)                                     # yellow-tan head
    cv.blend(head * (1 - S(0.2, 0.45, v)), "#D8C48A", 0.6)               # paler throat
    mucus(cv, 0.04, 301)
    scales_overlay(cv, 9, 0.05)
    # dark lines on the head: a vermiculated mesh plus a line from the mouth through the eye and one along the jaw
    hm = _cells(cv, 45, 302, lambda uu, vv: uu < 0.24 and vv > 0.2)
    mesh = (1 - S(1.0, 2.6, hm)) * head
    cv.blend(mesh, "#3A3018", 0.45)
    cv.blend(fl.stroke([(0.01, 0.5), (0.06, 0.56), (0.12, 0.58), (0.18, 0.55)], [0.004, 0.005, 0.005, 0.004], soft=0.003), "#2E2414", 0.8)
    cv.blend(fl.stroke([(0.015, 0.4), (0.07, 0.38), (0.15, 0.4)], [0.0035, 0.004, 0.0035], soft=0.003), "#2E2414", 0.7)
    cv.blend(fl.disc(0.135, 0.68, 0.014, 0.006), "#EDE6CC", 0.85)       # pale patch behind the eye
    # black chest/belly blotch behind the pectoral with a ragged edge (p06): u 0.18-0.55, up to just below mid-side
    nz = P.value_noise(cv.h, cv.w, 40, 16, 303)
    top = 0.46 + 0.05 * np.sin(u * 23.0) + 0.08 * (nz - 0.5) - 0.2 * S(0.36, 0.55, u)
    field = (1 - S(top - 0.02, top + 0.02, v)) * S(0.17, 0.22, u) * (1 - S(0.5, 0.58, u))
    cv.blend(field, "#0C0E16", 0.97)
    # bright blue-white mesh over the blotch: pale lines around black islands
    net = _cells(cv, 70, 304, lambda uu, vv: 0.14 < uu < 0.6 and vv < 0.55)
    lines = (1 - S(3.0, 6.5, net)) * field
    cv.blend(lines, "#7FC4F8", 0.95)
    cv.blend(lines * (1 - S(0.3, 0.7, nz)), "#E8F4FF", 0.6)
    # dense small pale-blue dots over everything orange-brown (not the head or the blotch)
    rs = np.random.default_rng(305)
    dots, _ = fl.spots(rs, 420, 0.0035, 0.0055, 0.0105, (0.18, 0.98, 0.03, 0.99))
    body = (1 - field) * S(0.17, 0.22, u)
    cv.blend(dots * body, "#A8D4FA", 0.92)
    cv.blend(dots * body * (nz > 0.55), "#F2F8FF", 0.5)
    dark_mouth(cv, fl, u1=0.018, v0=0.44, v1=0.52, color="#6A4A20", op=0.6)


def fins_leopard_wrasse(cv):
    """Dorsal, anal and caudal orange-brown with pale-blue dots like the body (Tee's photos p06, p09); clear pectorals."""
    def blue_dots(T, Sx, rgb, a, n=10, rows=3, col="#BCDFFA"):
        ph = np.abs(((T * n + 0.5 * np.floor(Sx * rows)) % 1.0) - 0.5)
        rw = np.abs(((Sx * rows) % 1.0) - 0.5)
        m = (1 - S(0.12, 0.22, ph)) * (1 - S(0.12, 0.22, rw)) * (Sx < 0.9)
        return rgb * (1 - m[..., None]) + hex2rgb(col) * m[..., None], np.maximum(a, 0.9 * m)
    FL.paint_fin_slot(cv, 0, base="#9A5A26", alpha=0.8, rays=20, ray_color="#6A3A18", spine_count=9,
                      spots=lambda T, Sx, rgb, a: blue_dots(T, Sx, rgb, a, 22, 3), margin="#5A3418", margin_w=0.08)
    FL.paint_fin_slot(cv, 2, base="#94562A", alpha=0.8, rays=13, ray_color="#6A3A18",
                      spots=lambda T, Sx, rgb, a: blue_dots(T, Sx, rgb, a, 14, 2), margin="#3A2418", margin_w=0.1)
    FL.paint_fin_slot(cv, 3, base="#A2642C", alpha=0.7, rays=13, ray_color="#6A3A18",
                      spots=lambda T, Sx, rgb, a: blue_dots(T, Sx, rgb, a, 12, 4), margin="#5A3418", margin_w=0.07)
    FL.paint_fin_slot(cv, 4, base="#E8D8C0", alpha=0.18, rays=12, ray_color="#C8A880", ray_alpha=0.35)
    FL.paint_fin_slot(cv, 5, base="#C89A70", alpha=0.5, rays=5, ray_color="#8A6040")

# ---------------------------------------------------------------- Zebrasoma tangs (research 42)
def paint_yellow_tang(cv, fl):
    """Zebrasoma flavescens: lemon yellow all over, a touch paler on the belly and face; the caudal spine sits in a white
    sheath on the peduncle [S12 FishBase; S13]. Faint vertical scale texture (tangs have tiny ctenoid scales)."""
    u, v = cv.u, cv.v
    cv.vgrad("#F7D21C", "#F3C80E", 0.0, 1.0)
    cv.blend((1 - S(0.0, 0.3, v)) * S(0.1, 0.4, u), "#FADF52", 0.45)      # paler belly
    cv.blend(S(0.8, 1.0, v), "#E8B80A", 0.35)                            # deeper yellow along the back
    mucus(cv, 0.04, 401)
    scales_overlay(cv, 8, 0.035)
    cv.blend(P.band(v, 0.48, 0.52, 0.02) * S(0.2, 0.4, u) * (1 - S(0.8, 0.9, u)), "#FFF09A", 0.18)  # lateral line
    cv.blend(fl.disc(0.885, 0.5, 0.022, 0.006), "#F6F4EC", 0.95)        # white spine sheath
    dark_mouth(cv, fl, u1=0.02, v0=0.46, v1=0.54, color="#B08A10", op=0.5)


def fins_yellow_tang(cv):
    def edge_pale(T, Sx, rgb):
        m = S(0.75, 1.0, Sx)[..., None]
        return rgb * (1 - 0.3 * m) + hex2rgb("#FFF2A0") * 0.3 * m
    FL.paint_fin_slot(cv, 0, base="#F4CC14", alpha=0.9, rays=30, ray_color="#D8A808", spine_count=5, incise=0.05, base_fn=edge_pale)
    FL.paint_fin_slot(cv, 2, base="#F4CC14", alpha=0.9, rays=24, ray_color="#D8A808", base_fn=edge_pale)
    FL.paint_fin_slot(cv, 3, base="#F2C810", alpha=0.85, rays=18, ray_color="#D4A40A")
    FL.paint_fin_slot(cv, 4, base="#F8E070", alpha=0.45, rays=14, ray_color="#E0C040", ray_alpha=0.6)
    FL.paint_fin_slot(cv, 5, base="#F4CC14", alpha=0.85, rays=5, ray_color="#D8A808")


def paint_purple_tang(cv, fl):
    """Zebrasoma xanthurum: deep blue-purple body with fine dark horizontal lines on the flank and small black spots on
    the head and chest; the tail (from the peduncle) bright yellow [trade descriptions S21 S24; confidence L]."""
    u, v = cv.u, cv.v
    cv.vgrad("#3E3492", "#33297E", 0.0, 1.0)
    cv.blend(S(0.75, 1.0, v), "#271E62", 0.5)
    mucus(cv, 0.05, 411)
    scales_overlay(cv, 8, 0.04)
    # thin longitudinal lines over the mid and rear flank
    lv = fl.lv
    ph = np.abs((((v - 0.5) * lv / 0.011) % 1.0) - 0.5)
    lines = (1 - S(0.12, 0.25, ph)) * S(0.28, 0.4, u) * (1 - S(0.86, 0.9, u)) * S(0.1, 0.2, v) * (1 - S(0.85, 0.95, v))
    cv.blend(lines, "#1C1650", 0.55)
    # black dots on the head and chest
    rs = np.random.default_rng(412)
    dots, _ = fl.spots(rs, 140, 0.0028, 0.0042, 0.0085, (0.08, 0.32, 0.3, 0.92))
    cv.blend(dots * (1 - S(0.24, 0.34, u)), "#08061A", 0.9)
    cv.blend(S(0.965, 0.985, u), "#F4D21A")                               # yellow from the caudal base
    cv.blend(fl.disc(0.885, 0.5, 0.02, 0.006), "#18123E", 0.9)          # dark spine sheath
    dark_mouth(cv, fl, u1=0.02, v0=0.46, v1=0.54, color="#140E30", op=0.6)


def fins_purple_tang(cv):
    FL.paint_fin_slot(cv, 0, base="#2E2478", alpha=0.92, rays=30, ray_color="#1A1450", margin="#5868D8", margin_w=0.06,
                      spine_count=5, incise=0.05)
    FL.paint_fin_slot(cv, 2, base="#2E2478", alpha=0.92, rays=24, ray_color="#1A1450", margin="#5868D8", margin_w=0.06)

    def caudal(T, Sx, rgb):
        rgb[:] = hex2rgb("#F4D21A")
        return rgb
    FL.paint_fin_slot(cv, 3, base="#F4D21A", alpha=0.95, rays=18, ray_color="#D8B000", base_fn=caudal)
    FL.paint_fin_slot(cv, 4, base="#3A3090", alpha=0.6, rays=14, ray_color="#241C60", tip_color="#F2CC20", tip_from=0.55)
    FL.paint_fin_slot(cv, 5, base="#2E2478", alpha=0.85, rays=5, ray_color="#1A1450")


# ---------------------------------------------------------------- red fairy wrasse (research 42)
def paint_fairy_female(cv, fl):
    """Cirrhilabrus rubripinnis initial phase (female): subdued, yellowish-orange back fading to pink-red flanks and a
    white belly [S4 S5: females 'more subdued', 'more yellow'; L for the exact shades]."""
    u, v = cv.u, cv.v
    cv.vgrad("#F4D8D4", "#E8907A", 0.0, 0.7)
    cv.blend(S(0.55, 0.95, v), "#E0A04A", 0.7)                          # yellow-orange back
    cv.blend((1 - S(0.0, 0.32, v)), "#FBF2EE", 0.7)                      # white belly
    cv.blend(P.band(v, 0.35, 0.6, 0.06) * S(0.15, 0.4, u), "#E8667A", 0.35)  # pink mid-flank
    mucus(cv, 0.03, 421)
    scales_overlay(cv, 10, 0.05)
    dark_mouth(cv, fl, u1=0.02, v0=0.46, v1=0.54, color="#B05040", op=0.5)


def paint_fairy_male(cv, fl):
    """Terminal male: deep scarlet red, darker over the back, a pinker belly; a thin pale-blue line from the snout under
    the eye [S1 FishBase: red fins and body; L for the face line]."""
    u, v = cv.u, cv.v
    cv.vgrad("#F08C9A", "#D8283C", 0.0, 0.6)
    cv.blend(S(0.6, 1.0, v), "#9E1426", 0.6)
    cv.blend((1 - S(0.0, 0.25, v)) * S(0.1, 0.3, u), "#F6B8C0", 0.6)
    mucus(cv, 0.03, 431)
    scales_overlay(cv, 10, 0.06)
    cv.blend(fl.stroke([(0.0, 0.47), (0.05, 0.5), (0.12, 0.52), (0.2, 0.5)], [0.0025, 0.003, 0.003, 0.002], soft=0.002),
             "#9CD8F8", 0.7)
    dark_mouth(cv, fl, u1=0.02, v0=0.46, v1=0.54, color="#701020", op=0.6)


def _fairy_fins(cv, male):
    """Male fins: deep red with a pale-blue margin, long red pelvic filaments that he spreads when he flashes. Female:
    translucent yellowish, the GLB's long pelvic rays cut back to a short fin (alpha)."""
    if male:
        FL.paint_fin_slot(cv, 0, base="#C8182E", alpha=0.9, rays=20, ray_color="#8A0E1E", spine_count=11,
                          margin="#9CD0F4", margin_w=0.07)
        FL.paint_fin_slot(cv, 2, base="#C8182E", alpha=0.9, rays=12, ray_color="#8A0E1E", margin="#9CD0F4", margin_w=0.08)
        FL.paint_fin_slot(cv, 3, base="#D02838", alpha=0.85, rays=13, ray_color="#8A1420", margin="#9CD0F4", margin_w=0.05)
        FL.paint_fin_slot(cv, 4, base="#F4C0C8", alpha=0.25, rays=12, ray_color="#E09098", ray_alpha=0.4)
        FL.paint_fin_slot(cv, 5, base="#D0182C", alpha=0.95, rays=5, ray_color="#8A0E1E", edge_fade=0.02)
    else:
        FL.paint_fin_slot(cv, 0, base="#F0C890", alpha=0.6, rays=20, ray_color="#D89860", spine_count=11,
                          alpha_fn=lambda T, Sx, a: a * (1 - S(0.62, 0.7, Sx)))
        FL.paint_fin_slot(cv, 2, base="#F2D0A8", alpha=0.55, rays=12, ray_color="#D8A070",
                          alpha_fn=lambda T, Sx, a: a * (1 - S(0.7, 0.78, Sx)))
        FL.paint_fin_slot(cv, 3, base="#F0B880", alpha=0.6, rays=13, ray_color="#D08850")
        FL.paint_fin_slot(cv, 4, base="#F8E8E0", alpha=0.2, rays=12, ray_color="#E8C8B8", ray_alpha=0.35)
        FL.paint_fin_slot(cv, 5, base="#F6E0D0", alpha=0.5, rays=5, ray_color="#E0B8A0",
                          alpha_fn=lambda T, Sx, a: a * (1 - S(0.3, 0.36, Sx)))


def fins_fairy_male(cv):
    _fairy_fins(cv, True)


def fins_fairy_female(cv):
    _fairy_fins(cv, False)

# ----------------------------------------------------------------------------

def paint_firefish(cv, fl):
    """Firefish goby, Nemateleotris magnifica (research 41 §1.1): a slender white-to-cream body with a lemon-yellow head
    and snout, grading from about two thirds of the way back through salmon-orange to fiery red-orange at the tail base
    (the red rear half is darker toward the back), a pale lilac sheen on the belly."""
    u, v = cv.u, cv.v
    cv.vgrad("#F6F2EE", "#F2EEE6", 0.0, 1.0)
    cv.blend(1 - S(0.06, 0.2, u), "#F2D650", 0.95)            # yellow head and snout
    cv.blend(S(0.14, 0.22, u) * (1 - S(0.2, 0.3, u)) * S(0.5, 0.9, v), "#F4E6A0", 0.5)   # yellow nape fading back
    cv.blend(S(0.55, 0.75, u), "#F28C52", 0.9)                # salmon orange from two thirds back
    cv.blend(S(0.72, 0.92, u), "#D8381E", 0.95)               # fiery red rear
    cv.blend(S(0.9, 1.0, u), "#9A1A12", 0.6)                  # darker at the tail base
    cv.blend(1 - S(0.15, 0.35, v), "#EFE6F2", 0.35 * (1 - S(0.5, 0.8, u)))  # lilac-white belly
    mucus(cv, 0.035, 411)
    scales_overlay(cv, 8, 0.03)
    dark_mouth(cv, fl, u1=0.02, v0=0.46, v1=0.52, color="#B07040", op=0.5)


def fins_firefish(cv):
    # first dorsal: the tall flag (spines 1-2 very long), white-pink with a red-orange upper half
    def flag(T, Sx, rgb, a):
        m = S(0.45, 0.8, Sx)
        return rgb * (1 - m[..., None]) + hex2rgb("#E8502A") * m[..., None], np.maximum(a, 0.85 * m)
    FL.paint_fin_slot(cv, 0, base="#F4E8E8", alpha=0.7, rays=6, ray_color="#E0B0A0", spine_count=6, spots=flag)
    # second dorsal and anal: red-orange with a dark red-black margin
    FL.paint_fin_slot(cv, 1, base="#E04A28", alpha=0.8, rays=28, ray_color="#A02814", margin="#3A0A0A", margin_w=0.1)
    FL.paint_fin_slot(cv, 2, base="#E04A28", alpha=0.8, rays=27, ray_color="#A02814", margin="#3A0A0A", margin_w=0.1)
    # caudal: deep red with black upper and lower edges
    def edges(T, Sx, rgb, a):
        m = 1 - S(0.08, 0.18, np.minimum(T, 1 - T))
        return rgb * (1 - m[..., None]) + hex2rgb("#1A0606") * m[..., None], np.maximum(a, 0.9 * m)
    FL.paint_fin_slot(cv, 3, base="#C82A18", alpha=0.85, rays=17, ray_color="#801408", spots=edges)
    FL.paint_fin_slot(cv, 4, base="#F4EEE8", alpha=0.3, rays=19, ray_color="#D8C8B8")
    FL.paint_fin_slot(cv, 5, base="#F4F0EA", alpha=0.45, rays=5, ray_color="#D8C8B8")


def paint_chromis(cv, fl):
    """Blue-green chromis, Chromis viridis (research 41 §2.1): iridescent apple-green to pale blue, bluer and brighter
    on the back, a silvery-green belly; the colour is structural, so most of it comes from the high iridescence."""
    u, v = cv.u, cv.v
    cv.vgrad("#D4F0DC", "#62CC98", 0.0, 0.85)
    cv.blend(S(0.72, 1.0, v), "#4AB0C8", 0.55)                # bluer along the back
    cv.blend(1 - S(0.1, 0.3, v), "#E8F6F0", 0.6)              # silvery belly
    cv.blend(S(0.8, 1.0, u), "#70C8C0", 0.4)
    mucus(cv, 0.03, 421)
    scales_overlay(cv, 12, 0.08)
    dark_mouth(cv, fl, u1=0.02, v0=0.5, v1=0.56, color="#3A6A60", op=0.5)


def fins_chromis(cv):
    FL.paint_fin_slot(cv, 0, base="#7AD0C0", alpha=0.45, rays=22, ray_color="#5AA8A0", spine_count=12,
                      margin="#A8E8F0", margin_w=0.06)
    FL.paint_fin_slot(cv, 2, base="#7AD0C0", alpha=0.45, rays=12, ray_color="#5AA8A0", spine_count=2,
                      margin="#A8E8F0", margin_w=0.06)
    FL.paint_fin_slot(cv, 3, base="#86D8C8", alpha=0.5, rays=17, ray_color="#5AA8A0", margin="#B8F0F0", margin_w=0.05)
    FL.paint_fin_slot(cv, 4, base="#D0F0EA", alpha=0.25, rays=18, ray_color="#A0D0C8")
    FL.paint_fin_slot(cv, 5, base="#C8F0E8", alpha=0.45, rays=6, ray_color="#90C8C0")
def paint_royal_gramma(cv, fl):
    """Royal gramma, Gramma loreto (research 50 §1): the front half of the fish electric magenta-violet, the rear half
    golden yellow, meeting in a short dappled zone a little behind mid-body (further forward on the belly); a thin
    black line runs from the snout through the eye and a little past it; a faint yellow wash under the eye."""
    u, v = cv.u, cv.v
    edge = 0.5 - 0.06 * (1 - v)                            # the colour boundary leans forward toward the belly
    cv.vgrad("#B03AC8", "#8A1EB0", 0.0, 1.0)               # violet, deeper along the back
    cv.blend(1 - S(0.0, 0.3, v), "#C860D8", 0.35)          # paler lilac throat
    mix = S(edge - 0.06, edge + 0.06, u)
    yc = P.Canvas(cv.w, cv.h)
    yc.vgrad("#F4C21C", "#F0A80E", 0.0, 1.0)
    # dapple: violet scale centres reach a little into the yellow and yellow ones into the violet
    n = P.value_noise(cv.h, cv.w, 70, 30, 501)
    m = np.clip(mix + (n - 0.5) * 0.5 * (1 - np.abs(2 * mix - 1)), 0, 1)
    cv.rgb = cv.rgb * (1 - m[..., None]) + yc.rgb * m[..., None]
    mucus(cv, 0.035, 502)
    scales_overlay(cv, 10, 0.06)
    # snout-eye line: a thin black stroke from the upper lip through the eye to just past it, rising a little
    cv.blend(fl.stroke([(0.0, 0.5), (0.05, 0.6), (0.1, 0.68), (0.16, 0.72)], [0.003, 0.004, 0.004, 0.0015], soft=0.002),
             "#140818", 0.95)
    cv.blend(fl.stroke([(0.04, 0.52), (0.14, 0.58)], [0.004, 0.002], soft=0.004), "#E8C850", 0.35)
    dark_mouth(cv, fl, u1=0.02, v0=0.46, v1=0.52, color="#40104A", op=0.6)


def fins_royal_gramma(cv):
    # dorsal XII,19-20: violet in front, yellow behind (like the body), the black spot at the front between spines 1-5
    def dorsal_fn(T, Sx, rgb, a):
        y = S(0.38, 0.5, T)
        rgb = rgb * (1 - y[..., None]) + hex2rgb("#F2B418") * y[..., None]
        spot = np.exp(-(((T - 0.12) / 0.07) ** 2 + ((Sx - 0.45) / 0.35) ** 2))
        m = np.clip(spot * 1.6, 0, 1)
        return rgb * (1 - m[..., None]) + hex2rgb("#08040A") * m[..., None], np.maximum(a, 0.95 * m)
    FL.paint_fin_slot(cv, 0, base="#9A2AC0", alpha=0.85, rays=31, ray_color="#6A1890", spine_count=12, spots=dorsal_fn,
                      margin="#D070E8", margin_w=0.05)
    # anal III,10: violet, a little paler toward the rim
    FL.paint_fin_slot(cv, 2, base="#9A2AC0", alpha=0.8, rays=13, ray_color="#6A1890", spine_count=3, margin="#C860E0",
                      margin_w=0.08)
    FL.paint_fin_slot(cv, 3, base="#F4BC1A", alpha=0.85, rays=17, ray_color="#C88A10", margin="#F8D860", margin_w=0.04)
    FL.paint_fin_slot(cv, 4, base="#E8C0F0", alpha=0.3, rays=16, ray_color="#B080C0")
    # long violet pelvics (the male's reach the anal fin)
    FL.paint_fin_slot(cv, 5, base="#A030C8", alpha=0.9, rays=5, ray_color="#701C98", margin="#D890F0", margin_w=0.1)


def paint_banggai(cv, fl):
    """Banggai cardinalfish, Pterapogon kauderni (research 50 §2): silvery body with a pewter cast, three bold black
    bars (through the eye, from the first dorsal to the pelvic base, from the second dorsal to the anal fin), the rear
    body and peduncle sprinkled with white pearl spots, a faint dark wash between the bars."""
    u, v = cv.u, cv.v
    cv.vgrad("#E8ECEE", "#A8B0B4", 0.0, 1.0)               # silver, pewter on the back
    cv.blend(P.value_noise(cv.h, cv.w, 50, 24, 511) * 0.5, "#D0D6DA", 0.4)
    cv.blend(S(0.25, 0.75, u) * S(0.35, 1.0, v), "#9AA0A6", 0.25)
    mucus(cv, 0.03, 512)
    scales_overlay(cv, 13, 0.07)

    def bar(c0, c1, lean, soft=0.006, vlo=0.0):
        b0 = c0 + lean * (1 - v)
        b1 = c1 + lean * (1 - v)
        return S(b0 - soft, b0 + soft, u) * (1 - S(b1 - soft, b1 + soft, u)) * S(vlo, vlo + 0.05, v)
    cv.blend(bar(0.1, 0.17, -0.02), "#0C0C0E")           # the eye bar, nape to throat
    cv.blend(bar(0.3, 0.38, 0.05), "#0C0C0E")            # first dorsal to the pelvics
    cv.blend(bar(0.56, 0.65, 0.04, vlo=0.04), "#0E0E10")  # second dorsal to the anal fin
    cv.blend(S(0.95, 1.0, u) * (S(0.8, 0.95, v) + (1 - S(0.05, 0.2, v))), "#1A1A1C", 0.5)  # dark edges at the tail base
    rs = np.random.default_rng(513)
    m, _ = fl.spots(rs, 46, 0.006, 0.011, 0.025, (0.66, 0.99, 0.08, 0.94))
    m2, _ = fl.spots(rs, 10, 0.005, 0.008, 0.025, (0.4, 0.55, 0.08, 0.5))
    cv.blend(np.maximum(m, m2), "#FAFAF6")
    dark_mouth(cv, fl, u1=0.03, v0=0.48, v1=0.56, color="#202024", op=0.6)


def fins_banggai(cv):
    pearls = lambda n, rows: (lambda T, Sx, rgb, a: _pearl_spots(T, Sx, rgb, a, n, rows))  # noqa: E731
    # tall black first dorsal VIII
    FL.paint_fin_slot(cv, 0, base="#101012", alpha=0.95, rays=8, ray_color="#000000", spine_count=8, incise=0.12)
    # second dorsal I,14 and anal II,13: long front rays, black leading half with white pearls, clear rear
    def black_lead(T, Sx, rgb):
        m = (1 - S(0.25, 0.45, T))[..., None]
        return rgb * (1 - m) + hex2rgb("#141416") * m
    FL.paint_fin_slot(cv, 1, base="#C8CCD0", alpha=0.6, rays=15, ray_color="#6A6E72", base_fn=black_lead, spots=pearls(9, 4))
    FL.paint_fin_slot(cv, 2, base="#C8CCD0", alpha=0.6, rays=14, ray_color="#6A6E72", base_fn=black_lead, spots=pearls(9, 4))

    def lobes(T, Sx, rgb, a):  # caudal: black leading margins on both lobes, clear between
        m = 1 - S(0.06, 0.16, np.minimum(T, 1 - T))
        return rgb * (1 - m[..., None]) + hex2rgb("#101012") * m[..., None], np.maximum(a, 0.9 * m)
    FL.paint_fin_slot(cv, 3, base="#C0C4C8", alpha=0.45, rays=17, ray_color="#707478", spots=lobes)
    FL.paint_fin_slot(cv, 4, base="#E0E4E8", alpha=0.25, rays=14, ray_color="#A0A4A8")
    # long black pelvics with white pearls
    FL.paint_fin_slot(cv, 5, base="#121214", alpha=0.95, rays=6, ray_color="#000000", spots=pearls(5, 4))


def _pearl_spots(T, Sx, rgb, a, n, rows):
    ph = np.abs(((T * n + 0.5 * np.floor(Sx * rows)) % 1.0) - 0.5)
    rw = np.abs(((Sx * rows) % 1.0) - 0.5)
    m = (1 - S(0.12, 0.22, np.sqrt(ph ** 2 + rw ** 2))) * (Sx > 0.12) * (Sx < 0.92)
    return rgb * (1 - m[..., None]) + hex2rgb("#F8F8F4") * m[..., None], np.maximum(a, 0.95 * m)


def paint_watchman(cv, fl):
    """Yellow watchman goby, Cryptocentrus cinctus (research 50 §3), yellow morph: lemon yellow all over, deeper on
    the back, with four or five faint darker saddles; small pale-blue spots edged darker on the head, cheeks and
    front of the body; a pale throat."""
    u, v = cv.u, cv.v
    cv.vgrad("#F8E890", "#EEC81C", 0.0, 0.9)
    cv.blend(S(0.8, 1.0, v), "#D8A814", 0.4)
    for c in (0.3, 0.45, 0.6, 0.75, 0.9):                  # faint saddles
        cv.blend(np.exp(-((u - c) / 0.035) ** 2) * S(0.35, 0.95, v), "#C89A18", 0.3)
    cv.blend(1 - S(0.05, 0.3, v), "#F8F0C8", 0.5)
    mucus(cv, 0.035, 521)
    scales_overlay(cv, 8, 0.04)
    rs = np.random.default_rng(522)
    ring, _ = fl.spots(rs, 34, 0.0055, 0.0085, 0.016, (0.02, 0.32, 0.3, 0.95),
                       weight_fn=lambda uu, vv: 1.0 - 0.6 * S(0.18, 0.32, uu))
    core, _ = fl.spots(np.random.default_rng(522), 34, 0.0038, 0.006, 0.016, (0.02, 0.32, 0.3, 0.95),
                       weight_fn=lambda uu, vv: 1.0 - 0.6 * S(0.18, 0.32, uu))
    cv.blend(ring, "#3A6A8A", 0.7)
    cv.blend(core, "#8CD0F4", 0.95)
    dark_mouth(cv, fl, u1=0.04, v0=0.42, v1=0.5, color="#8A6010", op=0.6)


def fins_watchman(cv):
    def blue_spots(T, Sx, rgb, a, n=7, rows=3):
        ph = np.abs(((T * n) % 1.0) - 0.5)
        rw = np.abs(((Sx * rows + 0.4) % 1.0) - 0.5)
        m = (1 - S(0.13, 0.26, ph)) * (1 - S(0.12, 0.26, rw)) * (Sx < 0.88)
        return rgb * (1 - m[..., None]) + hex2rgb("#7CC4F0") * m[..., None], np.maximum(a, 0.9 * m)
    FL.paint_fin_slot(cv, 0, base="#F2D040", alpha=0.75, rays=6, ray_color="#C8A020", spine_count=6,
                      spots=lambda T, Sx, rgb, a: blue_spots(T, Sx, rgb, a, 5, 3), margin="#8CC8F0", margin_w=0.06)
    FL.paint_fin_slot(cv, 1, base="#F2D040", alpha=0.75, rays=11, ray_color="#C8A020",
                      spots=lambda T, Sx, rgb, a: blue_spots(T, Sx, rgb, a, 9, 3), margin="#8CC8F0", margin_w=0.06)
    FL.paint_fin_slot(cv, 2, base="#F0C830", alpha=0.75, rays=10, ray_color="#C8A020", margin="#5AA0E0", margin_w=0.12)
    FL.paint_fin_slot(cv, 3, base="#F0CC30", alpha=0.8, rays=17, ray_color="#C8A020",
                      spots=lambda T, Sx, rgb, a: blue_spots(T, Sx, rgb, a, 8, 4))
    FL.paint_fin_slot(cv, 4, base="#F6E080", alpha=0.45, rays=18, ray_color="#D8B840")
    FL.paint_fin_slot(cv, 5, base="#F6E8A0", alpha=0.6, rays=5, ray_color="#D8B840")


def _maze(cv, freq, seed, warp=1.6, width=0.22, aspect=1.0):
    """Wavy, branching worm lines (mandarin): domain-warped stripes broken by a second noise field. Returns
    (line, edge) masks: the orange line core and the dark outline around it."""
    u, v = cv.u, cv.v
    n1 = P.value_noise(cv.h, cv.w, 14, 6, seed)
    n2 = P.value_noise(cv.h, cv.w, 30, 12, seed + 1)
    n3 = P.value_noise(cv.h, cv.w, 22, 9, seed + 2)
    ph = (v * freq + u * freq * 0.35 * aspect + warp * (n1 - 0.5) * 4 + 0.9 * (n2 - 0.5)) % 1.0
    d = np.abs(ph - 0.5)                                    # 0 on a line centre
    w = width * (0.75 + 0.5 * n3)
    line = 1 - S(w * 0.5 - 0.03, w * 0.5 + 0.03, d)
    edge = (1 - S(w * 0.5 + 0.02, w * 0.5 + 0.09, d)) * (1 - line)
    gaps = S(0.28, 0.36, n2)                               # break the stripes into worm-like pieces
    return line * gaps, edge * gaps


def paint_mandarin(cv, fl):
    """Mandarin dragonet, Synchiropus splendidus: vivid blue body covered in wavy, maze-like orange lines outlined in
    deep navy; the broad head greener (green-blue with finer orange lines); belly a little paler blue-green."""
    u, v = cv.u, cv.v
    cv.vgrad("#2A7AE0", "#1848C0", 0.0, 1.0)                # vivid blue, deeper on the back
    cv.blend(1 - S(0.05, 0.35, v), "#2AA0C8", 0.45)        # blue-green belly
    head = 1 - S(0.14, 0.24, u)
    cv.blend(head, "#2AAE98", 0.8)                         # green-blue head
    mucus(cv, 0.012, 601)                                  # scaleless, slimy skin: no scale texture
    line, edge = _maze(cv, 8.5, 602, width=0.32)
    hline, hedge = _maze(cv, 14, 605, warp=1.2, width=0.28)
    line = line * (1 - head) + hline * head
    edge = edge * (1 - head) + hedge * head
    cv.blend(edge, "#0C1A60", 0.85)
    cv.blend(line * (1 - head), "#F07818", 0.97)
    cv.blend(line * head, "#F08C20", 0.95)
    cv.blend(line * (1 - S(0.2, 0.5, v)), "#F8A030", 0.3)  # warmer on the lower side
    dark_mouth(cv, fl, u1=0.02, v0=0.42, v1=0.52, color="#10306A", op=0.6)


def fins_mandarin(cv):
    def wavy(col, n, ang=0.6, w=0.22):
        def fn(T, Sx, rgb, a):
            ph = (T * n + Sx * n * ang + 0.25 * np.sin(Sx * 9 + T * 5)) % 1.0
            m = 1 - S(w * 0.5, w * 0.5 + 0.08, np.abs(ph - 0.5))
            return rgb * (1 - m[..., None]) + hex2rgb(col) * m[..., None], a
        return fn
    # male's tall first dorsal (long first ray): dark navy with orange-red wavy bands, blue rim
    FL.paint_fin_slot(cv, 0, base="#14246A", alpha=0.92, rays=4, ray_color="#0A1240", spine_count=4,
                      spots=wavy("#E86020", 5, 1.2), margin="#3A8AF0", margin_w=0.06)
    # second dorsal, anal, caudal: orange-red with blue wavy lines, dark blue margin
    FL.paint_fin_slot(cv, 1, base="#E86A20", alpha=0.85, rays=9, ray_color="#B04010",
                      spots=wavy("#2850C8", 7, 0.8), margin="#1A2A90", margin_w=0.1)
    FL.paint_fin_slot(cv, 2, base="#E06020", alpha=0.85, rays=8, ray_color="#A83A10",
                      spots=wavy("#2A58D0", 6, -0.9), margin="#1A2A90", margin_w=0.14)
    FL.paint_fin_slot(cv, 3, base="#E87024", alpha=0.85, rays=10, ray_color="#B04412",
                      spots=wavy("#2A5CD0", 6, 0.3), margin="#1A2A90", margin_w=0.08)
    # big fan-like pectorals, nearly transparent
    FL.paint_fin_slot(cv, 4, base="#F0E4D0", alpha=0.14, rays=20, ray_color="#C8B090", ray_alpha=0.3)
    # large pelvics it walks on: orange with blue lines, fairly opaque
    FL.paint_fin_slot(cv, 5, base="#E87020", alpha=0.88, rays=6, ray_color="#A84010",
                      spots=wavy("#2A58C8", 4, 0.6), margin="#1A2A90", margin_w=0.1)


def paint_sixline(cv, fl):
    """Six line wrasse, Pseudocheilinus hexataenia: bluish-purple to lavender body with six bright orange horizontal
    stripes along the upper two thirds of the flank, thin blue-violet lines between them; the stripes reach the head,
    two running through the eye; a black, blue-edged eyespot on the upper caudal peduncle; lower side and belly paler
    violet-blue with faint greenish dots on the lower head."""
    u, v = cv.u, cv.v
    lv = fl.lv
    cv.vgrad("#8A86D8", "#6A4AB8", 0.0, 1.0)                # lavender belly -> blue-purple back
    cv.blend(1 - S(0.05, 0.3, v), "#A8B0E8", 0.5)
    mucus(cv, 0.01, 611)
    scales_overlay(cv, 9, 0.04)
    # stripes: centres in v, converging slightly toward the peduncle; the 3rd and 4th pass through the eye (v ~0.6)
    taper = np.interp(u, [0.0, 0.15, 0.6, 0.95, 1.0], [0.75, 1.0, 1.0, 0.7, 0.6])
    start = S(0.0, 0.03, u) * (1 - S(0.93, 0.97, u))
    for k, c in enumerate((0.38, 0.47, 0.56, 0.65, 0.75, 0.86)):
        cen = 0.6 + (c - 0.6) * taper
        hw = np.interp(u, [0.0, 0.2, 0.7, 1.0], [0.006, 0.0085, 0.0075, 0.005])
        d = np.abs(v - cen) * lv
        m = (1 - S(hw - 0.002, hw + 0.002, d)) * start
        if k == 0:
            m = m * S(0.1, 0.2, u)                         # the lowest stripe starts behind the gill
        cv.blend(m, "#F28A1C", 0.97)
    # thin blue-violet lines between the stripes
    for c in (0.425, 0.515, 0.605, 0.7, 0.805):
        cen = 0.6 + (c - 0.6) * taper
        d = np.abs(v - cen) * lv
        cv.blend((1 - S(0.0015, 0.003, d)) * start * S(0.08, 0.18, u), "#6A7AF0", 0.6)
    # lower head: small greenish-blue dots; snout a little orange
    rs = np.random.default_rng(612)
    dots, _ = fl.spots(rs, 18, 0.0025, 0.004, 0.01, (0.03, 0.2, 0.2, 0.45))
    cv.blend(dots, "#7AD8C8", 0.8)
    # black eyespot edged in blue on the upper caudal peduncle
    cv.blend(fl.disc(0.905, 0.8, 0.026, 0.004), "#5AA0F8", 0.95)
    cv.blend(fl.disc(0.905, 0.8, 0.019, 0.003), "#080810", 0.98)
    dark_mouth(cv, fl, u1=0.02, v0=0.48, v1=0.54, color="#5A2050", op=0.6)


def fins_sixline(cv):
    # continuous dorsal: orange-violet, a violet base and an orange band toward the rim
    def dorsal_fn(T, Sx, rgb):
        m = S(0.3, 0.65, Sx)[..., None]
        return rgb * (1 - m) + hex2rgb("#E07A3A") * m
    FL.paint_fin_slot(cv, 0, base="#7A4AB8", alpha=0.8, rays=23, ray_color="#5A3090", spine_count=11, base_fn=dorsal_fn,
                      margin="#B0A0F0", margin_w=0.06)
    FL.paint_fin_slot(cv, 2, base="#7A54C0", alpha=0.75, rays=12, ray_color="#5A3090", spine_count=3, base_fn=dorsal_fn,
                      margin="#B0A0F0", margin_w=0.07)

    def tail_fn(T, Sx, rgb):                               # green-tinted tail, violet at the base
        m = S(0.1, 0.45, Sx)[..., None]
        return rgb * (1 - m) + hex2rgb("#7AB07A") * m
    FL.paint_fin_slot(cv, 3, base="#6A5AB8", alpha=0.72, rays=14, ray_color="#4A6A60", base_fn=tail_fn,
                      margin="#A8D0A0", margin_w=0.05)
    FL.paint_fin_slot(cv, 4, base="#E8E4F4", alpha=0.18, rays=13, ray_color="#C0B8D8", ray_alpha=0.35)
    FL.paint_fin_slot(cv, 5, base="#B8A8E8", alpha=0.45, rays=5, ray_color="#8A78C0")


def paint_clown_goby(cv, fl):
    """Yellow clown goby, Gobiodon okinawae: solid bright lemon yellow all over, a touch deeper on the back and paler
    on the throat; scaleless, mucus-covered skin, so only a faint texture."""
    u, v = cv.u, cv.v
    cv.vgrad("#FAEE5A", "#F0D418", 0.0, 1.0)
    cv.blend(1 - S(0.05, 0.3, v), "#FCF4A0", 0.45)        # pale throat and belly
    cv.blend(S(0.85, 1.0, v), "#E8C410", 0.35)
    mucus(cv, 0.008, 621)
    dark_mouth(cv, fl, u1=0.025, v0=0.42, v1=0.52, color="#B08A10", op=0.5)


def fins_clown_goby(cv):
    FL.paint_fin_slot(cv, 0, base="#F4E030", alpha=0.62, rays=6, ray_color="#D8BC18", spine_count=6,
                      margin="#F8EE80", margin_w=0.06)
    FL.paint_fin_slot(cv, 1, base="#F4E030", alpha=0.62, rays=10, ray_color="#D8BC18", margin="#F8EE80", margin_w=0.06)
    FL.paint_fin_slot(cv, 2, base="#F2DC2C", alpha=0.62, rays=9, ray_color="#D8BC18", margin="#F8EE80", margin_w=0.06)
    FL.paint_fin_slot(cv, 3, base="#F4E030", alpha=0.65, rays=15, ray_color="#D8BC18", margin="#F8EE80", margin_w=0.05)
    FL.paint_fin_slot(cv, 4, base="#F6EA70", alpha=0.45, rays=16, ray_color="#E0C830")
    FL.paint_fin_slot(cv, 5, base="#F6E860", alpha=0.6, rays=6, ray_color="#E0C830")

# ---------------------------------------------------------------- rare and expensive fish (research 60)
def paint_gem_tang(cv, fl):
    """Zebrasoma gemmatum: dark chocolate-brown all over, covered with small round to oval white spots on the head,
    body and the dorsal and anal fins, a bright yellow tail [Wikipedia; research 60 §1]. Spots a little bigger and
    sparser on the mid flank than on the head [photo read, L]."""
    u, v = cv.u, cv.v
    cv.vgrad("#33251C", "#281C15", 0.0, 1.0)
    cv.blend(S(0.78, 1.0, v), "#21170F", 0.45)                          # darker along the back
    cv.blend((1 - S(0.0, 0.25, v)) * S(0.15, 0.5, u), "#433226", 0.35)   # a touch warmer on the belly
    mucus(cv, 0.05, 601)
    scales_overlay(cv, 8, 0.04)
    rs = np.random.default_rng(602)
    head, pts = fl.spots(rs, 120, 0.0026, 0.0038, 0.0085, (0.04, 0.3, 0.08, 0.95))
    body, _ = fl.spots(rs, 140, 0.0042, 0.0068, 0.015, (0.26, 0.9, 0.05, 0.96), existing=pts)
    cv.blend(head * (1 - S(0.27, 0.31, u)), "#F4F0E6", 0.92)
    cv.blend(body * S(0.27, 0.31, u) * (1 - S(0.86, 0.9, u)), "#F2EEE2", 0.9)
    cv.blend(S(0.965, 0.985, u), "#F2C230")                               # yellow from the caudal base
    cv.blend(fl.disc(0.885, 0.5, 0.02, 0.006), "#1A120C", 0.9)          # dark spine sheath
    dark_mouth(cv, fl, u1=0.02, v0=0.46, v1=0.54, color="#140C08", op=0.6)


def fins_gem_tang(cv):
    def pearls(T, Sx, rgb, a, n, rows):
        ph = np.abs(((T * n + 0.5 * np.floor(Sx * rows)) % 1.0) - 0.5)
        rw = np.abs(((Sx * rows) % 1.0) - 0.5)
        m = (1 - S(0.1, 0.2, ph)) * (1 - S(0.1, 0.2, rw)) * (Sx < 0.88) * (Sx > 0.06)
        return rgb * (1 - m[..., None]) + hex2rgb("#F2EEE2") * m[..., None], np.maximum(a, 0.95 * m)
    FL.paint_fin_slot(cv, 0, base="#2A1E16", alpha=0.92, rays=30, ray_color="#1A120C", spine_count=5, incise=0.05,
                      spots=lambda T, Sx, rgb, a: pearls(T, Sx, rgb, a, 34, 4))
    FL.paint_fin_slot(cv, 2, base="#2A1E16", alpha=0.92, rays=24, ray_color="#1A120C",
                      spots=lambda T, Sx, rgb, a: pearls(T, Sx, rgb, a, 26, 3))

    def caudal(T, Sx, rgb):
        rgb[:] = hex2rgb("#F2C230")
        return rgb
    FL.paint_fin_slot(cv, 3, base="#F2C230", alpha=0.95, rays=18, ray_color="#D49E10", base_fn=caudal)
    FL.paint_fin_slot(cv, 4, base="#3A2A20", alpha=0.6, rays=14, ray_color="#22180F")
    FL.paint_fin_slot(cv, 5, base="#2A1E16", alpha=0.85, rays=5, ray_color="#1A120C")


def paint_wrought_iron(cv, fl):
    """Chaetodon daedalma: metallic charcoal black; every scale has a pale (silvery to straw) centre inside a broad
    black rim, so the flank reads as a black lattice, a wrought-iron grille [Wikipedia; research 60 §3]. The head and
    snout are plain dark, the lattice fades out toward the peduncle [photo read, L]."""
    u, v = cv.u, cv.v
    cv.vgrad("#1E1E22", "#18181B", 0.0, 1.0)
    mucus(cv, 0.04, 611)
    sc = P.hex_scales(cv, 17, 612)
    centre = (1 - S(0.0, 0.35, sc)) * S(0.2, 0.3, u) * (1 - S(0.86, 0.93, u)) * S(0.06, 0.16, v) * (1 - S(0.88, 0.97, v))
    cv.blend(centre, "#9C9880", 0.6)
    cv.blend(centre * S(0.45, 0.75, u), "#C4B578", 0.25)                  # straw tint over the rear flank [L]
    cv.blend(1 - S(0.16, 0.26, u), "#141416", 0.5)                       # plain dark head
    dark_mouth(cv, fl, u1=0.02, v0=0.45, v1=0.55, color="#0A0A0C", op=0.6)


def fins_wrought_iron(cv):
    FL.paint_fin_slot(cv, 0, base="#1C1C20", alpha=0.95, rays=26, ray_color="#101012", spine_count=12, incise=0.04,
                      margin="#E8C63A", margin_w=0.12)
    FL.paint_fin_slot(cv, 2, base="#1C1C20", alpha=0.95, rays=20, ray_color="#101012", spine_count=3, margin="#E8C63A", margin_w=0.12)
    FL.paint_fin_slot(cv, 3, base="#232328", alpha=0.85, rays=16, ray_color="#141418", margin="#E8C63A", margin_w=0.1)
    FL.paint_fin_slot(cv, 4, base="#D8D4C4", alpha=0.25, rays=13, ray_color="#9A968A", ray_alpha=0.45)
    FL.paint_fin_slot(cv, 5, base="#1C1C20", alpha=0.85, rays=5, ray_color="#101012")


SPECIES = {
    "clown_orange": dict(
        # A. ocellaris: oval, depth ~0.44 SL at the spiny dorsal, caudal peduncle ~0.19 SL deep
        profile=[(0, 0.08, 0.05, -0.03), (0.03, 0.20, 0.10, -0.022), (0.09, 0.30, 0.135, -0.012),
                 (0.2, 0.39, 0.155, -0.004), (0.36, 0.44, 0.16, 0.0), (0.52, 0.42, 0.15, 0.0), (0.68, 0.34, 0.125, 0.0),
                 (0.8, 0.26, 0.095, 0.0), (0.9, 0.21, 0.07, 0.0), (1.0, 0.19, 0.05, 0.0)],
        body_tris=3600, ring=32, p_exp=0.85,
        dorsal=dict(u0=0.22, u1=0.80, h=[(0.22, 0.02), (0.26, 0.10), (0.33, 0.14), (0.45, 0.13), (0.50, 0.10),
                                         (0.52, 0.08), (0.56, 0.11), (0.66, 0.12), (0.75, 0.105), (0.80, 0.03)],
                    rake=18, n=26),
        anal=dict(u0=0.56, u1=0.82, h=[(0.56, 0.02), (0.60, 0.09), (0.70, 0.10), (0.78, 0.08), (0.82, 0.02)], rake=25),
        caudal=dict(length=0.2, span=0.26, shape="rounded"),
        pectoral=dict(u=0.27, length=0.18, width=0.12, zf=-0.12, shape="round"),
        pelvic=dict(u=0.33, length=0.10),
        eye=dict(u=0.11, zf=0.33, r=0.048, iris="#F08A30", ring="#3A2414", outer="#E8641C", ring_w=0.07,
                 pupil=0.4),
        paint=paint_clown_orange, fins=fins_clown, irid=0.12, rough=0.35, length_cm=7.0, scale_note="female 7.0, male 5.5",
    ),
    "tang": dict(
        # P. hepatus: oval, compressed, depth ~0.5 SL, small pointed snout below a convex forehead
        profile=[(0, 0.06, 0.04, -0.004), (0.04, 0.17, 0.07, 0.004), (0.1, 0.30, 0.10, 0.008), (0.2, 0.43, 0.125, 0.0),
                 (0.35, 0.50, 0.135, 0.0), (0.5, 0.51, 0.135, 0.0), (0.64, 0.47, 0.125, 0.0), (0.75, 0.38, 0.105, 0.0),
                 (0.84, 0.25, 0.08, 0.0), (0.9, 0.15, 0.055, 0.0), (0.95, 0.12, 0.045, 0.0), (1.0, 0.11, 0.03, 0.0)],
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
        # S. nematoptera (FishBase morphology): deep and compressed, body depth 1.8-2.0 in SL (0.50-0.56 SL), a
        # straight dorsal head profile rising to the first dorsal, a big head (opercle ~0.33 SL), terminal oblique
        # mouth, long slender peduncle; D VII + I,9 with the second dorsal's front rays prolonged, A II,9-10.
        # Measured on Tee's fish (b_04) and ref_cardinal.jpg: depth 0.50-0.54 at the first dorsal, eye at 0.15 SL.
        # (u, depth, width, centre): top contour a straight line from the snout to the first dorsal, belly rounder
        profile=[(0, 0.06, 0.03, 0.0), (0.04, 0.13, 0.06, -0.005), (0.10, 0.22, 0.09, -0.015), (0.18, 0.32, 0.115, -0.02),
                 (0.30, 0.45, 0.138, -0.015), (0.42, 0.535, 0.15, 0.002), (0.54, 0.50, 0.14, 0.0), (0.66, 0.38, 0.118, 0.0),
                 (0.78, 0.245, 0.088, 0.0), (0.88, 0.17, 0.062, 0.0), (0.95, 0.152, 0.048, 0.0), (1.0, 0.155, 0.038, 0.0)],
        body_tris=3400, ring=32, p_exp=0.7, belly_bias=0.05, gill=(0.31, 0.4, 0.06, 0.26),
        dorsal=dict(u0=0.385, u1=0.485, h=[(0.385, 0.08), (0.40, 0.27), (0.43, 0.23), (0.46, 0.13), (0.485, 0.03)],
                    rake=28, n=10),
        dorsal2=dict(u0=0.56, u1=0.72, h=[(0.56, 0.06), (0.57, 0.36), (0.59, 0.28), (0.63, 0.20), (0.68, 0.12),
                                          (0.72, 0.03)], rake=50, n=16),
        anal=dict(u0=0.58, u1=0.74, h=[(0.58, 0.05), (0.59, 0.25), (0.63, 0.20), (0.68, 0.13), (0.74, 0.03)],
                  rake=42, n=12),
        caudal=dict(length=0.30, span=0.42, shape="forked"),
        pectoral=dict(u=0.33, length=0.17, width=0.08, zf=-0.05, shape="pointed"),
        pelvic=dict(u=0.35, length=0.30, base=0.09),
        eye=dict(u=0.15, zf=0.3, r=0.072, iris="#D8401E", ring="#E85A2A", outer="#D8C060", pupil=0.42, ring_w=0.14),
        paint=paint_cardinal, fins=fins_cardinal, irid=0.15, rough=0.35, length_cm=7.0,
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
        # Pholidichthys leucotaenia (juvenile): eel-like, strongly compressed (width ~0.45 x depth), blunt head,
        # gradual taper to a pointed tail, continuous dorsal-caudal-anal fin, slightly wavy resting pose
        # tapered: rounded blunt snout, deepest just behind the head (~0.3), long steady taper and lateral
        # compression over the rear 60 % to a narrow tail (ref_engineer_goby.jpg)
        profile=[(0, 0.03, 0.026, -0.004), (0.012, 0.058, 0.04, -0.002), (0.04, 0.08, 0.05, 0.0),
                 (0.1, 0.092, 0.052, 0.0), (0.3, 0.1, 0.05, 0.0), (0.45, 0.094, 0.043, 0.0),
                 (0.62, 0.08, 0.034, 0.0), (0.78, 0.06, 0.024, 0.0), (0.9, 0.038, 0.015, 0.0),
                 (0.97, 0.018, 0.008, 0.0), (1.0, 0.005, 0.004, 0.0)],
        body_tris=5500, ring=16, p_exp=1.0, belly_bias=0.04, back_pinch=0.22,
        ribbon=dict(u_d=0.28, u_a=0.5, h_d=0.042, h_a=0.036, taper=0.35, tip_len=1.6),
        caudal=None, rest_wave=(0.022, 1.35),
        pectoral=dict(u=0.1, length=0.07, width=0.05, zf=-0.2, shape="round"),
        pelvic=dict(u=0.08, length=0.03),
        eye=dict(u=0.035, zf=0.4, r=0.014, iris="#2A2A20", ring="#B8B060", outer="#101010", ring_w=0.15),
        paint=paint_engineer_goby, fins=fins_engineer_goby, irid=0.05, rough=0.4, length_cm=20.0, tex=(2048, 256),
        gill=(0.08, 0.4, 0.03, 0.25),
    ),
    "hawkfish": dict(
        profile=[(0, 0.045, 0.035, -0.03), (0.06, 0.17, 0.09, -0.02), (0.16, 0.29, 0.13, -0.005), (0.3, 0.345, 0.14, 0.0),
                 (0.48, 0.34, 0.135, 0.0), (0.65, 0.28, 0.11, 0.0), (0.8, 0.195, 0.08, 0.0), (0.9, 0.155, 0.06, 0.0),
                 (1.0, 0.145, 0.045, 0.0)],
        body_tris=3400, ring=32, p_exp=0.8, belly_bias=0.06,
        dorsal=dict(u0=0.22, u1=0.82, h=[(0.22, 0.03), (0.26, 0.11), (0.4, 0.12), (0.52, 0.10), (0.56, 0.075),
                                         (0.60, 0.10), (0.75, 0.10), (0.82, 0.02)], rake=20, n=24, cirri=True,
                    cirri_len=0.032, cirri_ts=[(k + 0.5) / 20 for k in range(10)]),
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
        eye=dict(u=0.07, zf=0.55, r=0.055, iris="#E0C040", ring="#2A1A10", outer="#3A2420", ring_w=0.1),
        cirri=dict(u=0.065, h=0.035, blades=2),
        paint=paint_tailspot, fins=fins_tailspot, irid=0.2, rough=0.35, length_cm=5.5,
    ),
    "bicolor_blenny": dict(
        # Ecsenius bicolor (research 50 §1a): elongate combtooth blenny, deeper than the tailspot (depth ~0.2 L, a
        # design estimate between the male 0.19 and female 0.23), blunt rounded head with high eyes, one long
        # continuous dorsal notched over the spines, rounded caudal, small supraorbital cirri [DESIGN ESTIMATE]
        profile=[(0, 0.09, 0.075, 0.0), (0.06, 0.18, 0.13, 0.01), (0.16, 0.20, 0.13, 0.0), (0.4, 0.19, 0.12, 0.0),
                 (0.65, 0.15, 0.09, 0.0), (0.85, 0.095, 0.05, 0.0), (1.0, 0.05, 0.022, 0.0)],
        body_tris=3000, ring=28, p_exp=0.95, belly_bias=0.1,
        dorsal=dict(u0=0.13, u1=0.91, h=[(0.13, 0.03), (0.17, 0.10), (0.42, 0.09), (0.47, 0.07), (0.52, 0.10),
                                         (0.87, 0.09), (0.91, 0.02)], rake=25, n=30),
        anal=dict(u0=0.5, u1=0.91, h=[(0.5, 0.02), (0.54, 0.075), (0.87, 0.07), (0.91, 0.02)], rake=30, n=18),
        caudal=dict(length=0.14, span=0.2, shape="rounded"),
        pectoral=dict(u=0.18, length=0.17, width=0.14, zf=-0.25, shape="round"),
        pelvic=dict(u=0.12, length=0.065),
        eye=dict(u=0.07, zf=0.55, r=0.055, iris="#D88A30", ring="#1A1410", outer="#2A2030", ring_w=0.1),
        cirri=dict(u=0.065, h=0.03, blades=2),
        paint=paint_bicolor, fins=fins_bicolor, irid=0.2, rough=0.35, length_cm=9.0,
    ),
    "midas_blenny": dict(
        # Ecsenius midas (research 50 §2a): the largest, slimmest Ecsenius (13 cm), built for swimming in the water
        # column: a long even body (depth ~0.18 L), blunt head, long dorsal, and a lunate caudal whose outer rays
        # run out into lyre lobes in adults like the anthias it schools with [DESIGN ESTIMATE from photos]
        profile=[(0, 0.08, 0.07, 0.0), (0.06, 0.16, 0.115, 0.01), (0.16, 0.18, 0.12, 0.0), (0.4, 0.175, 0.11, 0.0),
                 (0.65, 0.14, 0.085, 0.0), (0.85, 0.09, 0.048, 0.0), (1.0, 0.06, 0.022, 0.0)],
        body_tris=3000, ring=28, p_exp=0.92, belly_bias=0.08,
        dorsal=dict(u0=0.13, u1=0.92, h=[(0.13, 0.03), (0.17, 0.10), (0.44, 0.095), (0.49, 0.075), (0.54, 0.105),
                                         (0.88, 0.10), (0.92, 0.02)], rake=25, n=30),
        anal=dict(u0=0.5, u1=0.92, h=[(0.5, 0.02), (0.54, 0.08), (0.88, 0.075), (0.92, 0.02)], rake=30, n=18),
        caudal=dict(length=0.2, span=0.26, shape="lyre"),
        pectoral=dict(u=0.18, length=0.16, width=0.13, zf=-0.2, shape="round"),
        pelvic=dict(u=0.12, length=0.06),
        eye=dict(u=0.075, zf=0.52, r=0.052, iris="#E0A030", ring="#2A6ED8", outer="#E8902A", ring_w=0.12),
        cirri=dict(u=0.07, h=0.028, blades=2),
        paint=paint_midas, fins=fins_midas, irid=0.3, rough=0.32, length_cm=11.0,
    ),
    "dottyback": dict(
        profile=[(0, 0.045, 0.035, -0.012), (0.05, 0.14, 0.08, -0.004), (0.14, 0.22, 0.105, 0.0), (0.3, 0.26, 0.115, 0.0),
                 (0.5, 0.265, 0.11, 0.0), (0.7, 0.22, 0.09, 0.0), (0.86, 0.15, 0.06, 0.0), (1.0, 0.11, 0.04, 0.0)],
        body_tris=3200, ring=28, p_exp=0.85, belly_bias=0.06,
        dorsal=dict(u0=0.20, u1=0.92, h=[(0.20, 0.02), (0.23, 0.06), (0.5, 0.07), (0.8, 0.085), (0.9, 0.07), (0.92, 0.02)],
                    rake=25, n=30),
        anal=dict(u0=0.46, u1=0.92, h=[(0.46, 0.02), (0.5, 0.06), (0.8, 0.08), (0.9, 0.065), (0.92, 0.02)], rake=28, n=20),
        caudal=dict(length=0.28, span=0.16, shape="lanceolate"),
        pectoral=dict(u=0.24, length=0.14, width=0.09, zf=-0.1, shape="round"),
        pelvic=dict(u=0.28, length=0.10),
        eye=dict(u=0.08, zf=0.3, r=0.045, iris="#20204A", ring="#5B63C8", outer="#202060", ring_w=0.12),
        paint=paint_dottyback, fins=fins_dottyback, irid=0.15, rough=0.55, length_cm=6.5,  # matte indigo, not glossy plastic (research 11 §7)
    ),
    "diamond_goby": dict(
        # Valenciennea puellaris (research 16): elongate sleeper goby, depth ~0.2 L, slightly compressed, blunt
        # rounded head with high-set eyes and a wide mouth, two separate dorsal fins, long anal, rounded-lanceolate tail
        profile=[(0, 0.05, 0.05, -0.02), (0.04, 0.13, 0.10, -0.01), (0.1, 0.18, 0.125, 0.0), (0.22, 0.2, 0.13, 0.0),
                 (0.4, 0.195, 0.12, 0.0), (0.58, 0.17, 0.1, 0.0), (0.74, 0.13, 0.075, 0.0), (0.86, 0.095, 0.05, 0.0),
                 (1.0, 0.075, 0.03, 0.0)],
        body_tris=3200, ring=28, p_exp=0.95, belly_bias=0.08,
        dorsal=dict(u0=0.25, u1=0.4, h=[(0.25, 0.03), (0.27, 0.13), (0.31, 0.14), (0.36, 0.1), (0.4, 0.02)],
                    rake=28, n=7),
        dorsal2=dict(u0=0.46, u1=0.82, h=[(0.46, 0.03), (0.49, 0.1), (0.7, 0.1), (0.8, 0.08), (0.82, 0.02)],
                     rake=24, n=14),
        anal=dict(u0=0.5, u1=0.82, h=[(0.5, 0.03), (0.53, 0.085), (0.78, 0.08), (0.82, 0.02)], rake=26, n=13),
        caudal=dict(length=0.2, span=0.17, shape="rounded"),
        pectoral=dict(u=0.24, length=0.15, width=0.1, zf=-0.15, shape="round"),
        pelvic=dict(u=0.25, length=0.1),
        eye=dict(u=0.075, zf=0.42, r=0.034, iris="#C8D0D8", ring="#E87A2A", outer="#5AA8EC", ring_w=0.14),
        # wide oblique mouth whose corner reaches back under the front of the eye; opens ~35° to take sand (research 16)
        mouth=dict(u=0.075, seam=-0.22, gape_deg=35),
        paint=paint_diamond_goby, fins=fins_diamond_goby, irid=0.3, rough=0.32, length_cm=12.0,
    ),
    "leopard_wrasse": dict(
        # Macropharyngodon bipartitus (research video fish_leopard_wrasse): short, deep, compressed labrid (depth ~0.38 L at
        # the pectoral base, owner clip b81b87e7), steep head with a small pointed terminal mouth, one long continuous
        # dorsal (IX,11), anal III,11, rounded-truncate caudal; low-aspect rowing pectorals
        profile=[(0, 0.04, 0.03, -0.01), (0.04, 0.15, 0.07, -0.004), (0.1, 0.27, 0.105, 0.0), (0.2, 0.36, 0.13, 0.0),
                 (0.32, 0.385, 0.135, 0.0), (0.48, 0.37, 0.125, 0.0), (0.64, 0.3, 0.1, 0.0), (0.78, 0.2, 0.07, 0.0),
                 (0.9, 0.15, 0.05, 0.0), (1.0, 0.14, 0.035, 0.0)],
        body_tris=3400, ring=30, p_exp=0.8, belly_bias=0.08, back_pinch=0.2,
        dorsal=dict(u0=0.2, u1=0.86, h=[(0.2, 0.02), (0.24, 0.07), (0.45, 0.075), (0.7, 0.09), (0.83, 0.08), (0.86, 0.02)],
                    rake=24, n=30),
        anal=dict(u0=0.52, u1=0.86, h=[(0.52, 0.02), (0.56, 0.07), (0.78, 0.085), (0.84, 0.06), (0.86, 0.02)], rake=28, n=16),
        caudal=dict(length=0.2, span=0.24, shape="rounded"),
        pectoral=dict(u=0.25, length=0.15, width=0.1, zf=-0.05, shape="round"),
        pelvic=dict(u=0.3, length=0.08),
        eye=dict(u=0.085, zf=0.3, r=0.04, iris="#D07A30", ring="#3A3A18", outer="#A8B040", ring_w=0.16, pupil=0.4),
        paint=paint_leopard_wrasse, fins=fins_leopard_wrasse, irid=0.25, rough=0.33, length_cm=8.5,
    ),
    "yellow_tang": dict(
        # Zebrasoma flavescens: a deep, compressed disc (body depth ~0.55 SL; with the tall dorsal and anal the fish
        # reads as round), a long protruding snout under a concave forehead, small terminal mouth, eye high and well
        # back from the snout; caudal spine in a white sheath on the peduncle [FishBase S12]
        profile=[(0, 0.04, 0.03, -0.07), (0.05, 0.065, 0.04, -0.065), (0.1, 0.11, 0.06, -0.055), (0.16, 0.22, 0.09, -0.035),
                 (0.23, 0.38, 0.115, -0.012), (0.31, 0.52, 0.13, 0.0), (0.42, 0.58, 0.135, 0.0), (0.54, 0.55, 0.13, 0.0), (0.66, 0.46, 0.115, 0.0),
                 (0.77, 0.33, 0.09, 0.0), (0.86, 0.19, 0.065, 0.0), (0.92, 0.14, 0.05, 0.0), (1.0, 0.13, 0.035, 0.0)],
        body_tris=4200, ring=36, p_exp=0.7, belly_bias=0.04, back_pinch=0.18,
        dorsal=dict(u0=0.2, u1=0.86, h=[(0.2, 0.04), (0.26, 0.15), (0.38, 0.21), (0.55, 0.22), (0.72, 0.19), (0.82, 0.12),
                                        (0.86, 0.03)], rake=20, n=32),
        anal=dict(u0=0.4, u1=0.86, h=[(0.4, 0.04), (0.46, 0.15), (0.6, 0.19), (0.74, 0.16), (0.82, 0.1), (0.86, 0.03)],
                  rake=22, n=26),
        caudal=dict(length=0.2, span=0.3, shape="truncate"),
        pectoral=dict(u=0.3, length=0.2, width=0.12, zf=-0.05, shape="pointed"),
        pelvic=dict(u=0.33, length=0.1),
        eye=dict(u=0.2, zf=0.4, r=0.043, iris="#1A1A14", ring="#E8C020", outer="#0E0E0A", ring_w=0.1),
        spine=dict(u=0.885, uv=(0.885, 0.5), size=1.2),
        paint=paint_yellow_tang, fins=fins_yellow_tang, irid=0.08, rough=0.32, length_cm=18.0,
    ),
    "fairy_wrasse": dict(
        # Cirrhilabrus rubripinnis: slender, compressed labrid (depth ~0.28 SL), pointed snout, big eye with the
        # characteristic double pupil look of Cirrhilabrus; long continuous dorsal XI,9, anal III,9, rounded caudal.
        # Built with the male's long pelvic filaments; the female fin sheet (the GLB's own) cuts them back by alpha.
        profile=[(0, 0.03, 0.025, -0.006), (0.04, 0.11, 0.06, -0.003), (0.1, 0.2, 0.09, 0.0), (0.2, 0.26, 0.11, 0.0),
                 (0.35, 0.28, 0.115, 0.0), (0.52, 0.26, 0.105, 0.0), (0.68, 0.21, 0.085, 0.0), (0.82, 0.15, 0.06, 0.0),
                 (0.92, 0.12, 0.045, 0.0), (1.0, 0.11, 0.03, 0.0)],
        body_tris=3200, ring=30, p_exp=0.85, belly_bias=0.06, back_pinch=0.15,
        dorsal=dict(u0=0.2, u1=0.86, h=[(0.2, 0.02), (0.24, 0.08), (0.5, 0.09), (0.74, 0.12), (0.83, 0.11), (0.86, 0.02)],
                    rake=26, n=30),
        anal=dict(u0=0.52, u1=0.86, h=[(0.52, 0.02), (0.56, 0.08), (0.76, 0.1), (0.84, 0.08), (0.86, 0.02)], rake=28, n=16),
        caudal=dict(length=0.2, span=0.22, shape="rounded"),
        pectoral=dict(u=0.26, length=0.14, width=0.09, zf=-0.05, shape="round"),
        pelvic=dict(u=0.28, length=0.3, base=0.04),
        eye=dict(u=0.085, zf=0.28, r=0.046, iris="#C82838", ring="#1A1A1A", outer="#E86070", ring_w=0.14, pupil=0.44),
        paint=paint_fairy_female, fins=fins_fairy_female, irid=0.3, rough=0.3, length_cm=8.0,
    ),
    "firefish": dict(
        # Nemateleotris magnifica (research 41 §1.1): slender, compressed dartfish, depth ~0.17-0.2 SL, blunt head with a
        # small oblique upturned mouth and large high eyes; first dorsal VI with spines 1-2 prolonged into the tall flag
        # (~0.45 SL), long second dorsal I,28-32 and anal I,27-30, rounded-truncate tail
        profile=[(0, 0.05, 0.04, 0.01), (0.04, 0.13, 0.085, 0.0), (0.12, 0.18, 0.105, 0.0), (0.28, 0.19, 0.1, 0.0),
                 (0.5, 0.175, 0.085, 0.0), (0.7, 0.145, 0.065, 0.0), (0.86, 0.11, 0.045, 0.0), (1.0, 0.095, 0.03, 0.0)],
        body_tris=3000, ring=28, p_exp=0.95, belly_bias=0.04,
        dorsal=dict(u0=0.22, u1=0.4, h=[(0.22, 0.05), (0.24, 0.44), (0.27, 0.46), (0.31, 0.3), (0.36, 0.12), (0.4, 0.02)],
                    rake=18, n=6),
        dorsal2=dict(u0=0.44, u1=0.86, h=[(0.44, 0.03), (0.47, 0.09), (0.7, 0.1), (0.83, 0.09), (0.86, 0.02)],
                     rake=26, n=28),
        anal=dict(u0=0.46, u1=0.86, h=[(0.46, 0.03), (0.49, 0.085), (0.7, 0.095), (0.83, 0.085), (0.86, 0.02)],
                  rake=28, n=27),
        caudal=dict(length=0.2, span=0.2, shape="rounded"),
        pectoral=dict(u=0.22, length=0.14, width=0.09, zf=-0.1, shape="round"),
        pelvic=dict(u=0.22, length=0.12),
        eye=dict(u=0.075, zf=0.38, r=0.05, iris="#E8C850", ring="#2A1A10", outer="#E8A040", ring_w=0.12, pupil=0.45),
        paint=paint_firefish, fins=fins_firefish, irid=0.15, rough=0.33, length_cm=7.0,
    ),
    "chromis": dict(
        # Chromis viridis (research 41 §2.1): deep, compressed damsel, depth ~0.46-0.5 SL, small terminal mouth, one
        # continuous dorsal XII,9-11, anal II,9-11, deeply forked caudal with pointed lobes
        profile=[(0, 0.05, 0.035, -0.01), (0.04, 0.17, 0.08, -0.005), (0.1, 0.31, 0.11, 0.0), (0.22, 0.43, 0.13, 0.0),
                 (0.36, 0.47, 0.135, 0.0), (0.52, 0.44, 0.125, 0.0), (0.68, 0.33, 0.1, 0.0), (0.82, 0.2, 0.07, 0.0),
                 (0.92, 0.14, 0.05, 0.0), (1.0, 0.13, 0.035, 0.0)],
        body_tris=3400, ring=32, p_exp=0.75, belly_bias=0.04,
        dorsal=dict(u0=0.22, u1=0.84, h=[(0.22, 0.03), (0.26, 0.12), (0.4, 0.13), (0.56, 0.1), (0.62, 0.13),
                                         (0.76, 0.14), (0.84, 0.02)], rake=22, n=22),
        anal=dict(u0=0.56, u1=0.82, h=[(0.56, 0.03), (0.6, 0.13), (0.72, 0.14), (0.8, 0.08), (0.82, 0.02)], rake=30, n=12),
        caudal=dict(length=0.3, span=0.38, shape="forked"),
        pectoral=dict(u=0.28, length=0.2, width=0.11, zf=-0.05, shape="pointed"),
        pelvic=dict(u=0.3, length=0.13),
        eye=dict(u=0.1, zf=0.3, r=0.058, iris="#C8E0D8", ring="#1A3A38", outer="#5AA8A0", ring_w=0.12, pupil=0.45),
        paint=paint_chromis, fins=fins_chromis, irid=0.6, rough=0.25, length_cm=7.5,
    ),
    "royal_gramma": dict(
        # Gramma loreto (research 50 §1): small, moderately elongate, compressed basslet, depth ~0.3 SL [L], rounded head
        # with a large eye and an oblique terminal mouth; one long dorsal XII,19-20 that rises toward the back, anal
        # III,10, slightly concave caudal (fishprofiles: "Concave"); long pelvics reaching the anal fin in males
        profile=[(0, 0.05, 0.035, -0.01), (0.04, 0.15, 0.08, -0.005), (0.12, 0.25, 0.11, 0.0), (0.26, 0.3, 0.12, 0.0),
                 (0.42, 0.3, 0.115, 0.0), (0.6, 0.26, 0.1, 0.0), (0.76, 0.19, 0.075, 0.0), (0.9, 0.14, 0.05, 0.0),
                 (1.0, 0.13, 0.035, 0.0)],
        body_tris=3200, ring=28, p_exp=0.82, belly_bias=0.06,
        dorsal=dict(u0=0.22, u1=0.86, h=[(0.22, 0.02), (0.25, 0.07), (0.42, 0.075), (0.62, 0.1), (0.78, 0.13),
                                         (0.84, 0.1), (0.86, 0.02)], rake=26, n=31),
        anal=dict(u0=0.56, u1=0.86, h=[(0.56, 0.02), (0.6, 0.08), (0.76, 0.12), (0.84, 0.09), (0.86, 0.02)], rake=30, n=13),
        caudal=dict(length=0.24, span=0.3, shape="forked"),
        pectoral=dict(u=0.27, length=0.16, width=0.09, zf=-0.05, shape="round"),
        pelvic=dict(u=0.28, length=0.3, base=0.06),
        eye=dict(u=0.1, zf=0.3, r=0.055, iris="#E8C050", ring="#2A1030", outer="#9A3AB0", ring_w=0.12, pupil=0.45),
        paint=paint_royal_gramma, fins=fins_royal_gramma, irid=0.2, rough=0.32, length_cm=6.5,
    ),
    "banggai": dict(
        # Pterapogon kauderni (research 50 §2): deep, compressed apogonid, depth ~0.5 SL [L, like the pajama cardinal],
        # big head and eye, tall tasseled first dorsal VIII, second dorsal I,14 and anal II,13 with the front rays long
        # and streaming, deeply forked caudal with long pointed lobes, long pelvics
        profile=[(0, 0.06, 0.03, 0.0), (0.04, 0.14, 0.06, -0.005), (0.1, 0.24, 0.09, -0.01), (0.18, 0.34, 0.11, -0.015),
                 (0.3, 0.46, 0.13, -0.01), (0.42, 0.52, 0.14, 0.0), (0.54, 0.48, 0.13, 0.0), (0.66, 0.36, 0.11, 0.0),
                 (0.78, 0.23, 0.085, 0.0), (0.88, 0.16, 0.06, 0.0), (1.0, 0.15, 0.038, 0.0)],
        body_tris=3400, ring=32, p_exp=0.7, belly_bias=0.05, gill=(0.31, 0.4, 0.06, 0.26),
        dorsal=dict(u0=0.33, u1=0.47, h=[(0.33, 0.1), (0.35, 0.42), (0.38, 0.4), (0.42, 0.24), (0.47, 0.03)],
                    rake=32, n=8),
        dorsal2=dict(u0=0.54, u1=0.74, h=[(0.54, 0.08), (0.555, 0.62), (0.58, 0.46), (0.63, 0.28), (0.69, 0.16),
                                          (0.74, 0.03)], rake=58, n=15),
        anal=dict(u0=0.56, u1=0.76, h=[(0.56, 0.07), (0.575, 0.55), (0.6, 0.4), (0.66, 0.22), (0.76, 0.03)],
                  rake=52, n=14),
        caudal=dict(length=0.33, span=0.5, shape="forked"),
        pectoral=dict(u=0.33, length=0.17, width=0.08, zf=-0.05, shape="pointed"),
        pelvic=dict(u=0.34, length=0.36, base=0.09),
        eye=dict(u=0.14, zf=0.3, r=0.075, iris="#E8D070", ring="#101010", outer="#B0A060", pupil=0.45, ring_w=0.12),
        paint=paint_banggai, fins=fins_banggai, irid=0.3, rough=0.3, length_cm=6.5,
    ),
    "watchman_goby": dict(
        # Cryptocentrus cinctus (research 50 §3): torpedo-shaped shrimp goby, depth ~0.2 L [L], a big blunt head with
        # high bulging eyes and a large downturned mouth reaching back under the eye, two dorsals (VI + I,10), anal
        # I,9, rounded tail, fused cup-shaped pelvics
        profile=[(0, 0.06, 0.06, -0.02), (0.04, 0.15, 0.12, -0.01), (0.1, 0.2, 0.15, 0.0), (0.2, 0.21, 0.145, 0.0),
                 (0.36, 0.2, 0.13, 0.0), (0.54, 0.175, 0.105, 0.0), (0.72, 0.14, 0.08, 0.0), (0.86, 0.105, 0.055, 0.0),
                 (1.0, 0.085, 0.035, 0.0)],
        body_tris=3200, ring=28, p_exp=0.95, belly_bias=0.1,
        dorsal=dict(u0=0.27, u1=0.42, h=[(0.27, 0.03), (0.29, 0.16), (0.31, 0.2), (0.35, 0.14), (0.42, 0.02)],
                    rake=26, n=6),
        dorsal2=dict(u0=0.46, u1=0.84, h=[(0.46, 0.03), (0.49, 0.11), (0.72, 0.12), (0.82, 0.1), (0.84, 0.02)],
                     rake=24, n=11),
        anal=dict(u0=0.52, u1=0.84, h=[(0.52, 0.03), (0.55, 0.09), (0.78, 0.09), (0.84, 0.02)], rake=26, n=10),
        caudal=dict(length=0.24, span=0.2, shape="rounded"),
        pectoral=dict(u=0.25, length=0.17, width=0.11, zf=-0.15, shape="round"),
        pelvic=dict(u=0.24, length=0.12),
        eye=dict(u=0.08, zf=0.48, r=0.04, iris="#E8D060", ring="#2A2A30", outer="#5A90C8", ring_w=0.14),
        mouth=dict(u=0.085, seam=-0.25, gape_deg=30),
        paint=paint_watchman, fins=fins_watchman, irid=0.15, rough=0.32, length_cm=8.0,
    ),
    "mandarin": dict(
        # Synchiropus splendidus: elongate, goby-like callionymid, roughly round in section, a broad flat head wider
        # than deep with big high-set eyes, small protrusible terminal mouth; the male's first dorsal (IV) tall and
        # pointed with a long first ray, second dorsal and anal long and low, rounded caudal; huge fan pectorals and
        # large pelvics it "walks" on
        profile=[(0, 0.05, 0.06, -0.015), (0.04, 0.13, 0.16, -0.01), (0.1, 0.19, 0.22, 0.0), (0.2, 0.22, 0.22, 0.0),
                 (0.34, 0.225, 0.19, 0.0), (0.5, 0.2, 0.15, 0.0), (0.68, 0.155, 0.105, 0.0), (0.84, 0.115, 0.07, 0.0),
                 (1.0, 0.095, 0.045, 0.0)],
        body_tris=3200, ring=28, p_exp=0.95, belly_bias=0.16, back_pinch=0.12, head_flatten=0.12,
        dorsal=dict(u0=0.3, u1=0.43, h=[(0.3, 0.05), (0.315, 0.4), (0.34, 0.27), (0.38, 0.15), (0.43, 0.03)],
                    rake=22, n=8),
        dorsal2=dict(u0=0.5, u1=0.86, h=[(0.5, 0.03), (0.53, 0.11), (0.7, 0.12), (0.82, 0.11), (0.86, 0.02)],
                     rake=26, n=12),
        anal=dict(u0=0.56, u1=0.86, h=[(0.56, 0.03), (0.59, 0.09), (0.8, 0.1), (0.86, 0.02)], rake=28, n=10),
        caudal=dict(length=0.24, span=0.26, shape="rounded"),
        pectoral=dict(u=0.24, length=0.24, width=0.2, zf=0.05, shape="round"),
        pelvic=dict(u=0.19, length=0.17, base=0.07),
        eye=dict(u=0.1, zf=0.55, r=0.052, iris="#D83020", ring="#7A1010", outer="#C82818", ring_w=0.12, pupil=0.42),
        paint=paint_mandarin, fins=fins_mandarin, irid=0.2, rough=0.3, length_cm=7.0,
    ),
    "sixline_wrasse": dict(
        # Pseudocheilinus hexataenia: small, moderately elongate, compressed labrid, depth ~0.3 SL, pointed head with a
        # small terminal mouth; one continuous dorsal (IX,11-12), anal III,9, rounded-truncate caudal
        profile=[(0, 0.04, 0.03, -0.006), (0.05, 0.15, 0.075, -0.002), (0.14, 0.25, 0.105, 0.0), (0.3, 0.3, 0.12, 0.0),
                 (0.48, 0.29, 0.11, 0.0), (0.66, 0.235, 0.09, 0.0), (0.82, 0.165, 0.065, 0.0), (0.92, 0.14, 0.05, 0.0),
                 (1.0, 0.135, 0.035, 0.0)],
        body_tris=3200, ring=28, p_exp=0.85, belly_bias=0.06, back_pinch=0.15,
        dorsal=dict(u0=0.2, u1=0.87, h=[(0.2, 0.02), (0.24, 0.06), (0.5, 0.07), (0.78, 0.085), (0.85, 0.07), (0.87, 0.02)],
                    rake=26, n=28),
        anal=dict(u0=0.56, u1=0.87, h=[(0.56, 0.02), (0.6, 0.065), (0.8, 0.08), (0.85, 0.06), (0.87, 0.02)], rake=28, n=14),
        caudal=dict(length=0.19, span=0.24, shape="rounded"),
        pectoral=dict(u=0.25, length=0.13, width=0.09, zf=-0.05, shape="round"),
        pelvic=dict(u=0.29, length=0.08),
        eye=dict(u=0.085, zf=0.32, r=0.04, iris="#E04A20", ring="#8A2010", outer="#C83A1C", ring_w=0.12, pupil=0.42),
        paint=paint_sixline, fins=fins_sixline, irid=0.3, rough=0.3, length_cm=7.5,
    ),
    "clown_goby": dict(
        # Gobiodon okinawae: tiny, deep, strongly compressed boxy coral goby, depth ~0.4 SL, a blunt rounded head with
        # small eyes, two dorsals close together (VI + I,10), anal I,9, rounded caudal, pelvics fused into a disc
        profile=[(0, 0.1, 0.07, -0.025), (0.04, 0.25, 0.12, -0.012), (0.11, 0.38, 0.16, -0.004), (0.24, 0.44, 0.17, 0.0),
                 (0.42, 0.44, 0.15, 0.0), (0.6, 0.37, 0.12, 0.0), (0.76, 0.26, 0.085, 0.0), (0.88, 0.2, 0.06, 0.0),
                 (1.0, 0.18, 0.04, 0.0)],
        body_tris=3200, ring=30, p_exp=0.72, belly_bias=0.08, back_pinch=0.15, gill=(0.24, 0.38, 0.06, 0.26),
        dorsal=dict(u0=0.33, u1=0.47, h=[(0.33, 0.03), (0.35, 0.12), (0.39, 0.13), (0.44, 0.1), (0.47, 0.03)],
                    rake=26, n=7),
        dorsal2=dict(u0=0.49, u1=0.83, h=[(0.49, 0.03), (0.52, 0.11), (0.72, 0.12), (0.8, 0.1), (0.83, 0.02)],
                     rake=26, n=12),
        anal=dict(u0=0.56, u1=0.83, h=[(0.56, 0.03), (0.59, 0.1), (0.77, 0.1), (0.83, 0.02)], rake=28, n=10),
        caudal=dict(length=0.22, span=0.3, shape="rounded"),
        pectoral=dict(u=0.27, length=0.15, width=0.12, zf=-0.1, shape="round"),
        pelvic=dict(u=0.27, length=0.08, base=0.07),
        eye=dict(u=0.1, zf=0.42, r=0.034, iris="#3A3010", ring="#141008", outer="#5A4A18", ring_w=0.12, pupil=0.5),
        paint=paint_clown_goby, fins=fins_clown_goby, irid=0.15, rough=0.28, length_cm=3.5,
    ),
}
SPECIES["clown_darwin"] = dict(SPECIES["clown_orange"], paint=paint_clown_darwin,
                               fins=lambda cv: fins_clown(cv, darwin=True),
                               eye=dict(u=0.11, zf=0.33, r=0.048, iris="#E07A28", ring="#1A1410", outer="#D85A18",
                                        ring_w=0.07, pupil=0.4))

# Z. xanthurum: the same Zebrasoma build as the yellow tang, a little longer in the snout and deeper in the fins
SPECIES["purple_tang"] = dict(SPECIES["yellow_tang"], paint=paint_purple_tang, fins=fins_purple_tang, irid=0.2,
                              eye=dict(u=0.2, zf=0.4, r=0.043, iris="#14102A", ring="#3A3080", outer="#0A0818", ring_w=0.1),
                              length_cm=20.0)

# research 60: the gem tang is a Zebrasoma built like the yellow tang (depth 1.6-1.9 in SL, Wikipedia)
SPECIES["gem_tang"] = dict(SPECIES["yellow_tang"], paint=paint_gem_tang, fins=fins_gem_tang, irid=0.12,
                           eye=dict(u=0.2, zf=0.4, r=0.043, iris="#120C08", ring="#3A2A1C", outer="#0A0806", ring_w=0.1),
                           length_cm=18.0)

# research 60: Chaetodon daedalma, a classic butterflyfish disc: body depth ~0.6 SL, a short pointed snout with a
# small terminal mouth, one continuous dorsal (XII-XIII spines) and anal (III spines) whose soft parts round off
# behind, a truncate caudal [Chaetodontidae morphology; L for the exact contour]
SPECIES["wrought_iron_butterfly"] = dict(
    profile=[(0, 0.04, 0.025, -0.04), (0.05, 0.1, 0.045, -0.035), (0.1, 0.2, 0.07, -0.025), (0.17, 0.36, 0.1, -0.012),
             (0.26, 0.52, 0.12, 0.0), (0.36, 0.6, 0.128, 0.0), (0.48, 0.58, 0.122, 0.0), (0.6, 0.5, 0.11, 0.0),
             (0.7, 0.38, 0.09, 0.0), (0.8, 0.24, 0.068, 0.0), (0.88, 0.16, 0.05, 0.0), (1.0, 0.15, 0.035, 0.0)],
    body_tris=4200, ring=36, p_exp=0.7, belly_bias=0.03, back_pinch=0.15,
    dorsal=dict(u0=0.24, u1=0.84, h=[(0.24, 0.04), (0.3, 0.12), (0.42, 0.15), (0.6, 0.16), (0.72, 0.15), (0.8, 0.09),
                                    (0.84, 0.02)], rake=30, n=26),
    anal=dict(u0=0.46, u1=0.84, h=[(0.46, 0.04), (0.52, 0.12), (0.64, 0.15), (0.75, 0.12), (0.82, 0.06), (0.84, 0.02)],
              rake=30, n=20),
    caudal=dict(length=0.18, span=0.28, shape="truncate"),
    pectoral=dict(u=0.3, length=0.17, width=0.1, zf=-0.1, shape="pointed"),
    pelvic=dict(u=0.31, length=0.12),
    eye=dict(u=0.15, zf=0.3, r=0.05, iris="#141416", ring="#6A6658", outer="#0C0C0E", ring_w=0.12),
    paint=paint_wrought_iron, fins=fins_wrought_iron, irid=0.3, rough=0.3, length_cm=13.0,
)

FISH_IDS = ["clown_orange", "clown_darwin", "tang", "cardinal", "wrasse", "engineer_goby", "hawkfish", "lawnmower_blenny",
            "tailspot_blenny", "dottyback", "diamond_goby", "leopard_wrasse", "bicolor_blenny", "midas_blenny", "firefish", "chromis",
            "yellow_tang", "purple_tang", "fairy_wrasse",
            "royal_gramma", "banggai", "watchman_goby", "mandarin", "sixline_wrasse", "clown_goby", "gem_tang", "wrought_iron_butterfly"]


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
    jaw_extras = {}
    if "mouth" in sp:  # an opening mouth: split lips, a lower jaw on a hinge and a dark mouth lining (fish.vert uJaw)
        mo = sp["mouth"]
        V, F, UV, G, JAW, INS, hinge = FL.add_mouth(V, F, UV, G, prof, sp["ring"], mouth_u=mo["u"], seam_s=mo["seam"])
        COL = np.stack([INS, G, JAW, -np.ones(len(V))], -1)
        # hinge (model x, y) and the widest gape (rad) for the shader
        jaw_extras["jaw"] = (lambda h=hinge, a=mo["gape_deg"]: [fb.gltf_point(h)[0], fb.gltf_point(h)[1],
                                                                round(math.radians(a), 4), 1.0])
    else:
        COL = np.stack([np.zeros(len(V)), G, np.zeros(len(V)), -np.ones(len(V))], -1)
    spine = sp.get("spine", dict(u=0.915, uv=(0.95, 0.55), size=1.0) if sid == "tang" else None)
    if spine:  # caudal spine (surgeonfish scalpel): a small wedge each side of the peduncle, coloured from the body at uv
        V, F, UV, COL = list(V), list(F), list(UV), list(COL)
        k = spine["size"]
        for side in (1, -1):
            u0 = spine["u"]
            H, W, zc = prof.H(u0), prof.W(u0), prof.Zc(u0)
            b = len(V)
            pts = [(u0 - 0.015 * k, side * W * 0.9, zc + 0.01 * k), (u0 + 0.02 * k, side * W * 0.9, zc + 0.005 * k),
                   (u0 + 0.01 * k, side * (W * 0.9 + 0.012 * k), zc + 0.004 * k), (u0 - 0.015 * k, side * W * 0.9, zc - 0.004 * k)]
            V += pts
            UV += [spine["uv"]] * 4
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
    fb.add("Body", V, F, UV, COL, "body", "body", extras=jaw_extras)

    fins = []
    for key, kind in (("dorsal", "dorsal"), ("dorsal2", "dorsal2"), ("anal", "anal")):
        if key in sp:
            d = sp[key]
            fv = FL.median_fin(prof, kind, d["u0"], d["u1"], d["h"], rake_deg=d.get("rake", 25),
                               n=d.get("n", 20), ns=d.get("ns", 6), cirri=d.get("cirri", False),
                               cirri_len=d.get("cirri_len", 0.02), cirri_ts=d.get("cirri_ts"))
            name = {"dorsal": "FinDorsal", "dorsal2": "FinDorsal2", "anal": "FinAnal"}[key]
            fins.append((name, fv))
    if sp.get("caudal"):
        c = sp["caudal"]
        fins.append(("FinCaudal", FL.caudal_fin(prof, 0.985, c["length"], c["span"], c["shape"])))
    if "ribbon" in sp:
        r = sp["ribbon"]
        fins.append(("FinDorsal", FL.ribbon_fin(prof, r["u_d"], r["u_a"], r["h_d"], r["h_a"],
                                                taper=r.get("taper", 0.0), tip_len=r.get("tip_len", 0.9))))
    if "pectoral" in sp:
        p = sp["pectoral"]
        for side, nm in ((-1, "FinPectoralL"), (1, "FinPectoralR")):
            fins.append((nm, FL.paired_fin(prof, "pectoral", p["u"], side, p["length"], p["width"], zf=p["zf"],
                                           shape=p["shape"], fingers=p.get("fingers", False), n=p.get("n", 10))))
    if "pelvic" in sp:
        p = sp["pelvic"]
        for side, nm in ((-1, "FinPelvicL"), (1, "FinPelvicR")):
            fins.append((nm, FL.paired_fin(prof, "pelvic", p["u"], side, p["length"], p["length"] * 0.5,
                                           shape="pelvic", out_deg=25, n=6, ns=5, pelvic_base=p.get("base", 0.045))))
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

    if sp.get("rest_wave"):  # gentle lateral S in the rest pose (head kept straight)
        amp, freq = sp["rest_wave"]
        for k, prt in enumerate(fb.parts):
            V = np.array(prt[1], float).copy()
            u = V[:, 0]
            V[:, 1] += amp * np.sin(2 * math.pi * freq * u - 0.6) * P.smoothstep(0.06, 0.35, u)
            fb.parts[k] = (prt[0], V) + tuple(prt[2:])
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
    # (the tang's adult colours, no yellow belly, are a life-stage form: textures/stages, life_stages.py)
    return dict(body=body, body1=body1, fin=fin, fin1=fin1, eye=eye)


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
