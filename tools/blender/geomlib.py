"""Small numpy geometry builders shared by inverts / corals / equipment (Blender Z-up frame)."""
import math

import numpy as np


class Geo:
    """Accumulates vertices/faces/uvs/cols for one mesh."""

    def __init__(self):
        self.V, self.F, self.UV, self.COL, self.FM = [], [], [], [], []

    def add(self, V, F, UV=None, COL=None, mat=0):
        b = len(self.V)
        V = np.asarray(V, float)
        self.V += list(V)
        self.F += [tuple(i + b for i in f) for f in F]
        self.UV += list(UV) if UV is not None else [(0.5, 0.5)] * len(V)
        self.COL += list(COL) if COL is not None else [(0, 0, 0, 0)] * len(V)
        self.FM += [mat] * len(F)
        return b

    def arrays(self):
        return (np.array(self.V), self.F, np.array(self.UV, float), np.array(self.COL, float),
                np.array(self.FM, int))

    def tris(self):
        return sum(len(f) - 2 for f in self.F)


def frames(points, up=(0, 0, 1)):
    """Parallel-transport frames (T, N, B) along a polyline."""
    P = np.asarray(points, float)
    n = len(P)
    T = np.gradient(P, axis=0)
    T /= np.linalg.norm(T, axis=1, keepdims=True) + 1e-12
    N = np.zeros_like(P)
    u = np.array(up, float)
    if abs(np.dot(u, T[0])) > 0.95:
        u = np.array([1.0, 0, 0]) if abs(T[0][0]) < 0.9 else np.array([0, 1.0, 0])
    n0 = u - np.dot(u, T[0]) * T[0]
    N[0] = n0 / np.linalg.norm(n0)
    for i in range(1, n):
        v = N[i - 1] - np.dot(N[i - 1], T[i]) * T[i]
        ln = np.linalg.norm(v)
        N[i] = v / ln if ln > 1e-9 else N[i - 1]
    B = np.cross(T, N)
    return T, N, B


def tube(points, radii, sides=8, cap_start=False, cap_end=True, up=(0, 0, 1), squash=1.0, twist=0.0):
    """Tube along points. Returns V, F, t (0..1 along length per vertex), a (0..1 around per vertex)."""
    P = np.asarray(points, float)
    R = np.broadcast_to(np.asarray(radii, float), (len(P),))
    T, N, B = frames(P, up)
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(P, axis=0), axis=1))]
    tt = seg / max(seg[-1], 1e-9)
    V, tv, av = [], [], []
    for i in range(len(P)):
        for k in range(sides):
            ang = 2 * math.pi * k / sides + twist * tt[i]
            V.append(P[i] + R[i] * (math.cos(ang) * N[i] + squash * math.sin(ang) * B[i]))
            tv.append(tt[i])
            av.append(k / sides)
    F = []
    for i in range(len(P) - 1):
        for k in range(sides):
            a = i * sides + k
            b = i * sides + (k + 1) % sides
            F.append((a, b, b + sides, a + sides))
    if cap_end:
        c = len(V)
        V.append(P[-1] + T[-1] * R[-1] * 0.6)
        tv.append(1.0)
        av.append(0.0)
        base = (len(P) - 1) * sides
        for k in range(sides):
            F.append((base + k, base + (k + 1) % sides, c))
    if cap_start:
        c = len(V)
        V.append(P[0] - T[0] * R[0] * 0.6)
        tv.append(0.0)
        av.append(0.0)
        for k in range(sides):
            F.append(((k + 1) % sides, k, c))
    return np.array(V), F, np.array(tv), np.array(av)


def ellipsoid(center, radii, segs=12, rings=8, rot=None):
    V = []
    for i in range(rings + 1):
        th = math.pi * i / rings
        for k in range(segs):
            ph = 2 * math.pi * k / segs
            V.append((math.sin(th) * math.cos(ph), math.sin(th) * math.sin(ph), math.cos(th)))
    V = np.array(V) * np.asarray(radii, float)
    if rot is not None:
        V = V @ np.asarray(rot).T
    V = V + np.asarray(center, float)
    F = []
    for i in range(rings):
        for k in range(segs):
            a = i * segs + k
            b = i * segs + (k + 1) % segs
            F.append((a, a + segs, b + segs, b))
    return V, F


def cone(base, tip, r, sides=6):
    base = np.asarray(base, float)
    tip = np.asarray(tip, float)
    d = tip - base
    T, N, B = frames(np.array([base, tip]))
    V = [base + r * (math.cos(2 * math.pi * k / sides) * N[0] + math.sin(2 * math.pi * k / sides) * B[0])
         for k in range(sides)] + [tip]
    F = [(k, (k + 1) % sides, sides) for k in range(sides)]
    return np.array(V), F


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def bezier(p0, p1, p2, p3, n):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = (np.asarray(p, float) for p in (p0, p1, p2, p3))
    return (1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t ** 2 * p2 + t ** 3 * p3


def helix_shell(steep, turns, r0, r1, n=40, sides=10, direction=1.0, tube_ratio=0.95):
    """Gastropod shell: a tube swept along a logarithmic conical spiral so that whorls touch.
    z = steep * (r1 - R): steep ~1.6 for a turban shell, ~2.5 for a tall spire. The last whorl
    (aperture) sits at z=0 with spiral radius r1. Returns V, F, t (0 apex -> 1 aperture), a,
    aperture center, aperture tangent."""
    th_max = 2 * math.pi * turns
    th = np.linspace(0, th_max, n)
    b = math.log(r1 / max(r0, 1e-4)) / th_max
    R = r1 * np.exp(-b * (th_max - th))
    z = steep * (r1 - R)
    P = np.stack([R * np.cos(direction * th), R * np.sin(direction * th), z], -1)
    rho = tube_ratio * R + 0.004
    V, F, tv, av = tube(P, rho, sides, cap_start=True, cap_end=False)
    T = P[-1] - P[-2]
    return V, F, tv, av, P[-1], T / np.linalg.norm(T)
