"""Procedural fish construction (ASSETS.md §1).

Fish are built in a "spec frame": x_s = body coordinate u (0 snout -> 1 end of the
body loft; the caudal fin extends beyond 1), z_s up, y_s lateral, body length 1.
At the end everything is mapped to the export frame:
  * whole fish (snout -> caudal tip) has length 1.0, centered on the origin,
  * facing +X (snout at x = +0.5, caudal tip at x = -0.5), Blender Z up = world +Y,
  * COLOR_0.a = spine coordinate 0 at the snout -> 1 at the tail tip (= 0.5 - x),
    fins attached at a point (pectorals, pelvics) carry the u of their base,
  * COLOR_0.g = opercular (gill) mask, COLOR_0.b = fin hinge distance (0 base -> 1 tip).
The transform is a 180 deg rotation about Z (x -> 0.5 - S x, y -> -S y), so face
winding is preserved.
"""
import math

import numpy as np

import common as C
import paintlib as P
from common import bpy

FIN_SLOTS = {"dorsal": 0, "dorsal2": 1, "anal": 2, "caudal": 3, "pectoral": 4, "pelvic": 5,
             "ribbon": 6, "spare": 7}
SLOT_W, SLOT_H = 256, 64
FIN_TEX = (512, 256)
BODY_TEX = (1024, 512)


# ----------------------------------------------------------------------------
# Interpolation
# ----------------------------------------------------------------------------
def pchip(xs, ys, x):
    """Monotone cubic (Fritsch-Carlson) interpolation; smooth like Catmull-Rom without overshoot."""
    xs = np.asarray(xs, float)
    ys = np.asarray(ys, float)
    x = np.asarray(x, float)
    h = np.diff(xs)
    d = np.diff(ys) / h
    m = np.zeros_like(ys)
    m[0], m[-1] = d[0], d[-1]
    for k in range(1, len(xs) - 1):
        if d[k - 1] * d[k] <= 0:
            m[k] = 0
        else:
            w1 = 2 * h[k] + h[k - 1]
            w2 = h[k] + 2 * h[k - 1]
            m[k] = (w1 + w2) / (w1 / d[k - 1] + w2 / d[k])
    xc = np.clip(x, xs[0], xs[-1])
    i = np.clip(np.searchsorted(xs, xc) - 1, 0, len(xs) - 2)
    t = (xc - xs[i]) / h[i]
    h00 = 2 * t ** 3 - 3 * t ** 2 + 1
    h10 = t ** 3 - 2 * t ** 2 + t
    h01 = -2 * t ** 3 + 3 * t ** 2
    h11 = t ** 3 - t ** 2
    return h00 * ys[i] + h10 * h[i] * m[i] + h01 * ys[i + 1] + h11 * h[i] * m[i + 1]


class Profile:
    def __init__(self, table):
        t = np.array(table, float)
        self.u = t[:, 0]
        self.hh = t[:, 1] / 2  # half height
        self.hw = t[:, 2] / 2  # half width
        self.yo = t[:, 3]

    def H(self, u):
        return pchip(self.u, self.hh, u)

    def W(self, u):
        return pchip(self.u, self.hw, u)

    def Zc(self, u):
        return pchip(self.u, self.yo, u)

    def top(self, u):
        return self.Zc(u) + self.H(u)

    def bottom(self, u):
        return self.Zc(u) - self.H(u)


# ----------------------------------------------------------------------------
# Body loft
# ----------------------------------------------------------------------------
def flank_h(theta):
    """ring angle (0 = belly, going up the +y flank) -> texture v (0 belly, 0.5 lateral line, 1 back)."""
    th = np.mod(theta, 2 * np.pi)
    return np.where(th <= np.pi, th / np.pi, (2 * np.pi - th) / np.pi)


def build_body(prof, tris, ring=32, p_exp=0.85, belly_bias=0.12, back_pinch=0.25, stations=None,
               gill=(0.215, 0.36, 0.06, 0.24), head_flatten=None):
    """Returns verts (spec frame), faces, uv, h (flank height), u per vertex, gill mask."""
    if stations is None:
        stations = max(8, int(round((tris - 2 * ring) / (2 * ring))) + 1)
    i = np.arange(stations) / (stations - 1)
    us = 0.65 * i + 0.35 * (0.5 - 0.5 * np.cos(np.pi * i))
    k = np.arange(ring)
    theta = 2 * np.pi * k / ring  # 0 = belly
    s, c = np.sin(theta - np.pi / 2), np.cos(theta - np.pi / 2)  # s=-1 belly, s=+1 back; c>0 -> +y flank
    V, UV, HH, UU = [], [], [], []
    for u in us:
        H, W, zc = prof.H(u), prof.W(u), prof.Zc(u)
        # flatter flanks (superellipse), wider below the lateral line, pinched dorsal ridge
        ylat = np.sign(c) * np.abs(c) ** p_exp
        wfac = (1 - belly_bias * s) * (1 - back_pinch * np.clip(s, 0, 1) ** 3)
        hf = 1.0
        if head_flatten is not None:
            hf = 1 - head_flatten * (1 - P.smoothstep(0.0, 0.08, u))
        y = W * ylat * wfac
        z = zc + H * s * hf
        for kk in range(ring):
            V.append((u, y[kk], z[kk]))
        hv = flank_h(theta)
        UV += [(u, hv[kk]) for kk in range(ring)]
        HH += list(hv)
        UU += [u] * ring
    n = len(V)
    snout = (us[0] - 0.012, 0.0, prof.Zc(0.0))
    tail = (1.004, 0.0, prof.Zc(1.0))
    V += [snout, tail]
    UV += [(0.0, 0.5), (1.0, 0.5)]
    HH += [0.5, 0.5]
    UU += [0.0, 1.0]
    F = []
    for a in range(stations - 1):
        for kk in range(ring):
            p0 = a * ring + kk
            p1 = a * ring + (kk + 1) % ring
            q0 = p0 + ring
            q1 = p1 + ring
            F.append((p0, p1, q1, q0))
    for kk in range(ring):
        F.append((n, (kk + 1) % ring, kk))
        b = (stations - 1) * ring
        F.append((n + 1, b + kk, b + (kk + 1) % ring))
    V = np.array(V)
    UU = np.array(UU)
    HH = np.array(HH)
    gu, gh, gru, grh = gill
    d = np.sqrt(((UU - gu) / gru) ** 2 + ((HH - gh) / grh) ** 2)
    G = 1 - P.smoothstep(0.6, 1.0, d)
    return V, F, np.array(UV), HH, UU, G


# ----------------------------------------------------------------------------
# Fins
# ----------------------------------------------------------------------------
def slot_uv(slot, t, s):
    col, row = slot % 2, slot // 2
    t = 0.01 + 0.98 * np.asarray(t)
    s = 0.03 + 0.94 * np.asarray(s)
    u = (col + t) / 2.0
    v = 1.0 - (row + 1 - s) / 4.0
    return np.stack([u, v], -1)


def fin_grid(base, tip, ns, slot, ts=None, A=None, bend=None, skip=()):
    """Grid between base[j] and tip[j] polylines (n columns x ns rows).
    bend: optional (n,3) offset vector added with weight s*(1-s)*4 (cupping)."""
    base = np.asarray(base, float)
    tip = np.asarray(tip, float)
    n = len(base)
    if ts is None:
        ts = np.linspace(0, 1, n)
    V, UV, COL = [], [], []
    for j in range(n):
        for i in range(ns):
            s = i / (ns - 1)
            p = base[j] + s * (tip[j] - base[j])
            if bend is not None:
                p = p + bend[j] * 4 * s * (1 - s)
            V.append(p)
            UV.append(slot_uv(slot, ts[j], s))
            COL.append((0.0, 0.0, s, A[j] if A is not None else -1.0))
    F = []
    for j in range(n - 1):
        if j in skip:
            continue
        for i in range(ns - 1):
            a = j * ns + i
            F.append((a, a + 1, a + ns + 1, a + ns))
    return np.array(V), F, np.array(UV), np.array(COL)


def median_fin(prof, kind, u0, u1, heights, rake_deg=25.0, n=20, ns=6, slot=None, inset=0.12,
               cirri=False):
    """Dorsal/anal fin along the top/bottom contour. heights: list of (u, h)."""
    us = np.linspace(u0, u1, n)
    hs = np.maximum(pchip([h[0] for h in heights], [h[1] for h in heights], us), 0.003)
    sign = 1.0 if kind.startswith("dorsal") else -1.0
    zc = prof.Zc(us)
    H = prof.H(us)
    base = np.stack([us, np.zeros(n), zc + sign * (1 - inset) * H], -1)
    r = math.radians(rake_deg)
    # rake: rays lean backward; near the end of the fin the lean increases (soft rays fold back)
    lean = r + np.linspace(0, 0.35, n)
    tip = base + np.stack([hs * np.sin(lean) + inset * H * 0.5, np.zeros(n),
                           sign * (hs * np.cos(lean) + inset * H)], -1)
    ts = np.linspace(0, 1, n)
    slot = FIN_SLOTS[kind] if slot is None else slot
    V, F, UV, COL = fin_grid(base, tip, ns, slot, ts=ts, A=us)
    if cirri:
        # tiny tufts (3 tapered triangles) at each spine tip of the spiny part
        extra_v, extra_f, extra_uv, extra_c = [], [], [], []
        nb = len(V)
        for j in range(0, n // 2 + 1, max(1, n // 20)):
            tp = tip[j]
            for kk in range(3):
                ang = math.radians(-35 + 35 * kk) + lean[j]
                d = np.array([math.sin(ang), 0.0, sign * math.cos(ang)]) * 0.02
                side = np.array([0.0, 0.004 * (kk - 1), 0.0])
                w = np.array([0.003, 0.0, 0.0])
                vi = nb + len(extra_v)
                extra_v += [tp - w + side, tp + w + side, tp + d + side]
                extra_f.append((vi, vi + 1, vi + 2))
                uvt = slot_uv(slot, ts[j], 0.999)
                extra_uv += [uvt, uvt, uvt]
                extra_c += [(0, 0, 1.0, us[j])] * 3
        V = np.vstack([V, extra_v])
        F = F + extra_f
        UV = np.vstack([UV, extra_uv])
        COL = np.vstack([COL, extra_c])
    return V, F, UV, COL


def caudal_fin(prof, ub, length, span, shape="rounded", n=21, ns=6, slot=None):
    t = np.linspace(-1, 1, n)
    zc0 = prof.Zc(ub)
    H0 = prof.H(ub)
    at = np.abs(t)
    if shape == "rounded":
        ext = 0.62 + 0.38 * np.sqrt(np.clip(1 - t * t, 0, 1))
        zt = np.sin(t * np.pi / 2) * span / 2
    elif shape == "truncate":
        ext = 0.86 + 0.14 * np.sqrt(np.clip(1 - t * t, 0, 1))
        zt = t * span / 2
    elif shape == "forked":
        ext = 0.62 + 0.38 * at ** 1.4
        zt = t * span / 2
    elif shape == "lunate":
        ext = 0.42 + 0.58 * at ** 1.8
        zt = np.sign(t) * at ** 0.9 * span / 2
    else:
        raise ValueError(shape)
    base = np.stack([np.full(n, ub - 0.025), np.zeros(n), zc0 + t * 0.92 * H0], -1)
    tip = np.stack([ub + length * ext, np.zeros(n), zc0 + zt], -1)
    slot = FIN_SLOTS["caudal"] if slot is None else slot
    return fin_grid(base, tip, ns, slot, ts=(t + 1) / 2, A=None)


def paired_fin(prof, kind, u, side, length, width, zf=-0.15, out_deg=35, down_deg=15, shape="round",
               n=10, ns=6, fingers=False, slot=None, attach_w=0.85):
    """Pectoral (fan from a short vertical base) or pelvic (from a short longitudinal base)."""
    H, W, zc = prof.H(u), prof.W(u), prof.Zc(u)
    t = np.linspace(0, 1, n)
    if shape == "round":
        ext = 0.55 + 0.45 * np.sqrt(np.clip(1 - (2 * t - 1) ** 2, 0, 1))
    elif shape == "pointed":  # upper rays longest
        ext = 0.35 + 0.65 * np.sin(np.clip(t, 0, 1) * np.pi * 0.62) ** 1.2
    elif shape == "paddle":
        ext = 0.7 + 0.3 * np.sqrt(np.clip(1 - (2 * t - 1) ** 2, 0, 1))
    elif shape == "pelvic":  # leading spine longest
        ext = 1.0 - 0.55 * t
    else:
        raise ValueError(shape)
    skip = set()
    if fingers:  # hawkfish: lower 30% split into 3 free-ray lobes, longer and separated by gaps
        for j in range(n):
            if t[j] < 0.3:
                ext[j] *= 1.12
        lob = [j for j in range(n) if t[j] < 0.3]
        if len(lob) >= 4:
            skip = {lob[0], lob[2]} if len(lob) < 6 else {lob[1], lob[3]}
    oa = math.radians(out_deg)
    da = math.radians(down_deg)
    if kind == "pectoral":
        zb = zc + zf * H + (t - 0.5) * width * 0.45
        base = np.stack([np.full(n, u), np.full(n, side * W * attach_w), zb], -1)
        L = length * ext
        tip = np.stack([u + L * math.cos(oa) * math.cos(da),
                        side * (W * attach_w + L * math.sin(oa)),
                        zc + zf * H + (t - 0.5) * width - L * math.sin(da)], -1)
    else:  # pelvic
        ub = u + t * 0.045
        base = np.stack([ub, np.full(n, side * W * 0.3), prof.Zc(ub) - prof.H(ub) * 0.88], -1)
        L = length * ext
        tip = np.stack([ub + L * 0.62, side * (W * 0.3 + L * math.sin(oa) * 0.6),
                        base[:, 2] - L * 0.75], -1)
    slot = FIN_SLOTS[kind] if slot is None else slot
    A = np.full(n, u)
    bend = np.tile(np.array([0, side * 0.012 * length / 0.2, 0]), (n, 1))
    V, F, UV, COL = fin_grid(base, tip, ns, slot, ts=t, A=A, bend=bend, skip=skip)
    return V, F, UV, COL


def ribbon_fin(prof, u_dorsal0, u_anal0, h_dorsal, h_anal, n=90, ns=3, slot=None, taper=0.0, tip_len=0.9):
    """Eel-like: one continuous strip from the dorsal start, around the tail tip, forward to the anal start.
    taper > 0 lowers the fin toward the tail and tip_len (x h_dorsal) sets the point at the tail tip, so the
    outline converges to a pointed tail."""
    # path parameter: top contour u_dorsal0 -> 1, then bottom 1 -> u_anal0
    nt = int(n * (1 - u_dorsal0) / ((1 - u_dorsal0) + (1 - u_anal0)))
    nb = n - nt
    ut = np.linspace(u_dorsal0, 0.995, nt)
    ub = np.linspace(0.995, u_anal0, nb)
    top = np.stack([ut, np.zeros(nt), prof.Zc(ut) + prof.H(ut) * 0.8], -1)
    bot = np.stack([ub, np.zeros(nb), prof.Zc(ub) - prof.H(ub) * 0.8], -1)
    tipc = np.array([[1.004, 0, prof.Zc(1.0)]])
    base = np.vstack([top, tipc, bot])
    # heights ramp in at the start
    ht = h_dorsal * P.smoothstep(u_dorsal0, u_dorsal0 + 0.08, ut)
    hb = h_anal * P.smoothstep(u_anal0, u_anal0 + 0.08, ub)
    ht = ht * (1 - taper * P.smoothstep(0.7, 0.995, ut))
    hb = hb * (1 - taper * P.smoothstep(0.7, 0.995, ub))
    hs = np.concatenate([ht, [h_dorsal * tip_len], hb])
    # outward normal in the x-z plane
    tang = np.gradient(base, axis=0)
    nrm = np.stack([tang[:, 2], np.zeros(len(base)), -tang[:, 0]], -1)
    nrm /= np.linalg.norm(nrm, axis=1, keepdims=True) + 1e-9
    # make sure the top part points up
    if nrm[0, 2] < 0:
        nrm = -nrm
    tip = base + nrm * hs[:, None]
    ts = np.linspace(0, 1, len(base))
    us = base[:, 0]
    slot = FIN_SLOTS["ribbon"] if slot is None else slot
    return fin_grid(base, tip, ns, slot, ts=ts, A=us)


def uv_sphere(center, r, axis_sign, segs=10, rings=6):
    """Sphere with its pole on the lateral axis (spec y). Returns V, F, UV(planar side), polar angle per face."""
    V = [center + np.array([0, axis_sign * r, 0])]
    phis = np.linspace(0, np.pi, rings + 1)[1:-1]
    for ph in phis:
        for k in range(segs):
            lam = 2 * np.pi * k / segs
            V.append(center + np.array([r * math.sin(ph) * math.cos(lam), axis_sign * r * math.cos(ph),
                                        r * math.sin(ph) * math.sin(lam)]))
    V.append(center - np.array([0, axis_sign * r, 0]))
    V = np.array(V)
    F, FPHI = [], []
    for k in range(segs):
        F.append((0, 1 + (k + 1) % segs, 1 + k) if axis_sign > 0 else (0, 1 + k, 1 + (k + 1) % segs))
        FPHI.append(phis[0] / 2)
    for a in range(len(phis) - 1):
        for k in range(segs):
            p0 = 1 + a * segs + k
            p1 = 1 + a * segs + (k + 1) % segs
            if axis_sign > 0:
                F.append((p0, p1, p1 + segs, p0 + segs))
            else:
                F.append((p0, p0 + segs, p1 + segs, p1))
            FPHI.append((phis[a] + phis[a + 1]) / 2)
    last = len(V) - 1
    b = 1 + (len(phis) - 1) * segs
    for k in range(segs):
        F.append((last, b + k, b + (k + 1) % segs) if axis_sign > 0 else (last, b + (k + 1) % segs, b + k))
        FPHI.append(np.pi)
    d = V - center
    UV = np.stack([0.5 + 0.5 * d[:, 0] / r, 0.5 + 0.5 * d[:, 2] / r], -1)
    return V, F, UV, np.array(FPHI)


# ----------------------------------------------------------------------------
# Painting helpers for fins and eyes
# ----------------------------------------------------------------------------
def paint_fin_slot(cv, slot, base="#C9C6C2", alpha=0.55, rays=12, ray_color=None, ray_alpha=0.85,
                   margin=None, margin_w=0.08, tip_color=None, tip_from=0.5, spots=None, base_fn=None,
                   edge_fade=0.06, spine_count=0, alpha_fn=None):
    """Paint one 256x64 slot. Pixel space t (0..1 across), s (0 base -> 1 tip)."""
    col, row = slot % 2, slot // 2
    x0, y0 = col * SLOT_W, row * SLOT_H
    t = (np.arange(SLOT_W) + 0.5) / SLOT_W
    s = 1 - (np.arange(SLOT_H) + 0.5) / SLOT_H
    T, S = np.meshgrid(t, s)
    T = (T - 0.01) / 0.98
    S = (S - 0.03) / 0.94
    rgb = np.empty((SLOT_H, SLOT_W, 3), np.float32)
    rgb[:] = C.hex2rgb(base)
    if base_fn is not None:
        rgb = base_fn(T, S, rgb)
    a = np.full((SLOT_H, SLOT_W), alpha, np.float32)
    if tip_color is not None:
        m = P.smoothstep(tip_from, 1.0, S)[..., None]
        rgb = rgb * (1 - m) + C.hex2rgb(tip_color) * m
    if rays:
        rc = C.hex2rgb(ray_color) if ray_color else rgb * 0.8
        ph = np.abs(((T * rays) % 1.0) - 0.5)
        line = 1 - P.smoothstep(0.06, 0.16, ph)
        if spine_count:
            # thicker leading spines
            sp = (T * rays) < spine_count
            line = np.where(sp, 1 - P.smoothstep(0.1, 0.24, ph), line)
        rgb = rgb * (1 - 0.6 * line[..., None]) + (rc if np.ndim(rc) == 1 else rc) * 0.6 * line[..., None]
        a = np.maximum(a, ray_alpha * line)
    if spots is not None:
        rgb, a = spots(T, S, rgb, a)
    if margin is not None:
        m = P.smoothstep(1 - margin_w - 0.03, 1 - margin_w, S)[..., None]
        rgb = rgb * (1 - m) + C.hex2rgb(margin) * m
        a = np.maximum(a, 0.85 * m[..., 0])
    if alpha_fn is not None:
        a = alpha_fn(T, S, a)
    a = a * (1 - P.smoothstep(1 - edge_fade, 1.0, S))
    cv.rgb[y0:y0 + SLOT_H, x0:x0 + SLOT_W] = rgb
    cv.a[y0:y0 + SLOT_H, x0:x0 + SLOT_W] = a


def paint_eye(path, iris="#F5F5F5", ring="#E0663A", outer="#202020", ring_w=0.18, pupil=0.36, size=64):
    cv = P.Canvas(size, size, outer)
    d = np.sqrt((cv.u - 0.5) ** 2 + (cv.v - 0.5) ** 2) * 2
    cv.blend(1 - P.smoothstep(0.78, 0.84, d), ring)
    cv.blend(1 - P.smoothstep(0.78 - ring_w - 0.03, 0.78 - ring_w, d), iris)
    # iris radial streaks
    ang = np.arctan2(cv.v - 0.5, cv.u - 0.5)
    streak = 0.5 + 0.5 * np.sin(ang * 23)
    cv.multiply(1 - 0.12 * streak * (d < 0.78))
    cv.blend(1 - P.smoothstep(pupil - 0.03, pupil + 0.02, d), "#050505")
    # specular catchlight
    cv.blend(1 - P.smoothstep(0.06, 0.1, np.sqrt((cv.u - 0.38) ** 2 + (cv.v - 0.62) ** 2)), "#FFFFFF", 0.85)
    return cv.save(path)


# ----------------------------------------------------------------------------
# Assembly
# ----------------------------------------------------------------------------
class FishBuilder:
    """Collects parts in the spec frame, then creates Blender objects in the export frame."""

    def __init__(self, sid, prof, caudal_len):
        self.sid = sid
        self.prof = prof
        self.S = 1.0 / (1.0 + caudal_len)
        self.x0 = 0.0
        self.parts = []  # (name, V, F, UV, COL, part, matkey, face_mats, extras)

    def to_export(self, V):
        V = np.asarray(V, float)
        return np.stack([0.5 - self.S * (V[:, 0] - self.x0), -self.S * V[:, 1], self.S * V[:, 2]], -1)

    def fit(self):
        """Exact unit length: snout (min spec x over all parts) -> +0.5, tail tip (max) -> -0.5."""
        xs = np.concatenate([np.asarray(p[1], float)[:, 0] for p in self.parts])
        self.x0 = float(xs.min())
        self.S = 1.0 / float(xs.max() - xs.min())

    def add(self, name, V, F, UV, COL, part, mat, face_mats=None, extras=None):
        self.parts.append((name, V, F, UV, COL, part, mat, face_mats, extras or {}))

    def build(self, mats, root_extras):
        """Extras values may be callables (evaluated after fit(), e.g. lambda: fb.gltf_point(p))."""
        self.fit()
        ev = lambda d: {k: (v() if callable(v) else v) for k, v in d.items()}  # noqa: E731
        root = C.empty("Root")
        C.set_extras(root, **ev(root_extras))
        objs = {}
        for name, V, F, UV, COL, part, mat, fm, ex in self.parts:
            Ve = self.to_export(V)
            COL = np.array(COL, float).copy()
            A = np.clip(0.5 - Ve[:, 0], 0, 1)
            neg = COL[:, 3] < 0
            COL[neg, 3] = A[neg]
            COL[~neg, 3] = np.clip((COL[~neg, 3] - self.x0) * self.S, 0, 1)
            ml = [mats[m] for m in (mat if isinstance(mat, (list, tuple)) else [mat])]
            o = C.mesh_object(name, Ve, F, uvs=UV, cols=COL, parent=root, part=part, materials=ml,
                              face_mats=fm, smooth=True)
            for k, v in ev(ex).items():
                if isinstance(v, np.ndarray):
                    v = [float(x) for x in v]
                C.set_extras(o, **{k: v})
            objs[name] = o
        return root, objs

    def gltf_point(self, p):
        """spec-frame point -> glTF coordinates (x, y_up, z) of the exported unit fish."""
        b = self.to_export(np.array([p]))[0]
        return [round(float(b[0]), 5), round(float(b[2]), 5), round(float(-b[1]), 5)]


# ----------------------------------------------------------------------------
# Self-test (build_all step 1): geometry invariants that do not need bpy
# ----------------------------------------------------------------------------
def self_test():
    xs = np.array([0.0, 0.2, 0.5, 1.0])
    ys = np.array([0.0, 0.4, 0.5, 0.1])
    x = np.linspace(0, 1, 101)
    y = pchip(xs, ys, x)
    assert np.allclose(pchip(xs, ys, xs), ys), "pchip must interpolate its knots"
    assert y.max() <= ys.max() + 1e-9 and y.min() >= ys.min() - 1e-9, "pchip must not overshoot"
    prof = Profile([(0, 0.05, 0.03, 0), (0.3, 0.4, 0.15, 0.02), (0.8, 0.15, 0.06, 0), (1, 0.08, 0.03, 0)])
    V, F, UV, HH, UU, G = build_body(prof, 3000)
    V = np.asarray(V, float)
    assert np.all(np.isfinite(V)) and len(UV) == len(V)
    # closed, outward-wound surface: positive signed volume
    vol = 0.0
    for f in F:
        a = V[f[0]]
        for k in range(1, len(f) - 1):
            vol += np.dot(a, np.cross(V[f[k]], V[f[k + 1]])) / 6.0
    assert vol > 0, f"body winding inverted (signed volume {vol})"
    uv = np.asarray(UV, float)
    assert uv[:, 1].min() >= -1e-6 and uv[:, 1].max() <= 1 + 1e-6, "flank v must be in [0, 1]"
    tris = sum(len(f) - 2 for f in F)
    print(f"[fishlib] self-test ok: {len(V)} verts, {tris} tris, volume {vol:.4f}")
    return True


if __name__ == "__main__":
    self_test()
