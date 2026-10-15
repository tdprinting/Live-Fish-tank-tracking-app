"""Limb rigging for an imported (scanned / generated) one-piece crab mesh: split it into body and limbs and give every
limb vertex its limb index and its parameter t along the limb (0 at the coxa .. 1 at the tip), the data the
renderer's limb rig reads (SIMULATION §19, ASSETS.md limb-rig conventions). Pure numpy: the builder that calls it
imports the mesh with Blender and rebuilds the parts from these arrays.

How a limb is found: the shortest path over the welded surface from its tip back to the middle of the carapace runs
along the limb; cut where it enters the carapace outline (walking legs) or at a given base point (chelae), it is the
limb's first centreline. Vertices near it are the limb; their centroids along it give the real centreline, and the
vertices are assigned again against that, each to the nearest limb within reach, and t is the arc length of its
projection over the centreline's length.
"""
import heapq

import numpy as np


def weld(V, digits=5):
    """Merge coincident vertices (UV seams). Returns (unique positions, index of each vertex's unique position)."""
    U, inv = np.unique(np.round(V, digits), axis=0, return_inverse=True)
    return U, inv.ravel()


def geodesic(U, F, start):
    """Dijkstra over the mesh edges from vertex [start]: (distance, predecessor) per vertex."""
    n = len(U)
    adj = [[] for _ in range(n)]
    for t in F:
        for a, b in ((t[0], t[1]), (t[1], t[2]), (t[2], t[0])):
            adj[a].append(b)
            adj[b].append(a)
    D = np.full(n, np.inf)
    prev = -np.ones(n, int)
    D[start] = 0.0
    h = [(0.0, start)]
    while h:
        d, a = heapq.heappop(h)
        if d > D[a]:
            continue
        for b in adj[a]:
            nd = d + float(np.linalg.norm(U[a] - U[b]))
            if nd < D[b]:
                D[b] = nd
                prev[b] = a
                heapq.heappush(h, (nd, b))
    return D, prev


def _project(P, C):
    """Nearest point of every row of P on polyline C: (distance, arc length along C)."""
    a, b = C[:-1], C[1:]
    ab = b - a
    seg = np.linalg.norm(ab, axis=1)
    s0 = np.r_[0.0, np.cumsum(seg)][:-1]
    q = np.clip(np.einsum('nsk,sk->ns', P[:, None, :] - a[None], ab) / np.maximum(seg ** 2, 1e-12), 0.0, 1.0)
    X = a[None] + q[..., None] * ab[None]
    d = np.linalg.norm(P[:, None, :] - X, axis=2)
    j = np.argmin(d, axis=1)
    r = np.arange(len(P))
    return d[r, j], s0[j] + q[r, j] * seg[j]


def _resample(C, n):
    s = np.r_[0.0, np.cumsum(np.linalg.norm(np.diff(C, axis=0), axis=1))]
    q = np.linspace(0.0, s[-1], n)
    return np.stack([np.interp(q, s, C[:, i]) for i in range(3)], -1)


def _smooth(C, passes=3):
    C = C.copy()
    for _ in range(passes):
        C[1:-1] = 0.25 * C[:-2] + 0.5 * C[1:-1] + 0.25 * C[2:]
    return C


def rig_limbs(U, F, centre, tips, bases, inside, reach, body=None):
    """Segment the welded mesh (U, F) into limbs.

    centre: a point on top of the carapace the paths run back to; tips: per limb, the (x, y) of its tip;
    bases: per limb None (cut where the path enters the carapace, `inside(points) -> bool`) or the (x, y) where the
    limb meets the body; reach: per limb, how far from its centreline a vertex can be (the limb's half-width plus a
    margin); body: `body(points) -> bool`, where the carapace can be (vertices elsewhere are always a limb's, the
    nearest one's: the dactyl ends past the centreline's reach). Returns (label per vertex: limb index or -1 for the body, t per vertex, centrelines from coxa to tip).
    """
    start = int(np.argmin(np.linalg.norm(U - np.asarray(centre), axis=1)))
    D, prev = geodesic(U, F, start)
    lines = []
    for tip, base in zip(tips, bases):
        # the tip: the lowest vertex near the given point (the dactyl's end touches the ground)
        near = np.linalg.norm(U[:, :2] - np.asarray(tip), axis=1)
        i = int(np.argmin(near + 0.5 * U[:, 2]))
        p = [i]
        while prev[p[-1]] >= 0:
            p.append(prev[p[-1]])
        P = U[p]                                    # tip -> centre
        if base is None:
            k = int(np.argmax(inside(P)))
        else:
            k = int(np.argmin(np.linalg.norm(P[:, :2] - np.asarray(base), axis=1)))
        lines.append(_resample(P[:k + 1][::-1], 24))
    # refine: centroids of each limb's vertices along its path are the real centreline
    for it in range(2):
        label, _, s = _assign(U, lines, reach, D)
        new = []
        for k, C in enumerate(lines):
            m = label == k
            L = np.r_[0.0, np.cumsum(np.linalg.norm(np.diff(C, axis=0), axis=1))][-1]
            bins = np.linspace(0, L, 13)
            pts = [C[0]]
            for b0, b1 in zip(bins[:-1], bins[1:]):
                w = m & (s >= b0) & (s < b1)
                if w.sum() >= 3:
                    pts.append(U[w].mean(0))
            pts.append(C[-1])
            new.append(_resample(_smooth(np.array(pts)), 24))
        lines = new
    label, d, s = _assign(U, lines, reach, D)
    if body is not None:
        lost = (label < 0) & ~body(U)
        if lost.any():
            dk = np.stack([_project(U[lost], C)[0] for C in lines], -1)
            label[lost] = np.argmin(dk, axis=1)
    label = _vote(label, F, len(U))
    t = np.zeros(len(U))
    for k, C in enumerate(lines):
        L = np.r_[0.0, np.cumsum(np.linalg.norm(np.diff(C, axis=0), axis=1))][-1]
        m = label == k
        _, sk = _project(U[m], C)
        t[m] = np.clip(sk / L, 0.0, 1.0)
    return label, t, lines


def _vote(label, F, n, passes=2):
    """A vertex most of whose mesh neighbours belong to one other part joins it (stray patches where limbs touch)."""
    nb = [[] for _ in range(n)]
    for t in F:
        for a, b in ((t[0], t[1]), (t[1], t[2]), (t[2], t[0])):
            nb[a].append(b)
            nb[b].append(a)
    for _ in range(passes):
        new = label.copy()
        for i in range(n):
            if not nb[i]:
                continue
            v, c = np.unique(label[nb[i]], return_counts=True)
            j = int(np.argmax(c))
            if v[j] != label[i] and c[j] * 2 > len(nb[i]):
                new[i] = v[j]
        label = new
    return label


def _assign(U, lines, reach, D):
    n = len(U)
    best = np.full(n, np.inf)
    label = -np.ones(n, int)
    arc = np.zeros(n)
    for k, C in enumerate(lines):
        d, s = _project(U, C)
        # past the coxa (a projection onto the first point is the body behind it), and within reach
        ok = (d < reach[k]) & (s > 1e-6) & (d < best)
        best[ok] = d[ok]
        label[ok] = k
        arc[ok] = s[ok]
    return label, best, arc
