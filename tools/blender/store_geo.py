"""Geometry helpers shared by the fish store models (store_rooms.py, store_people.py, store_fronts.py).

World cm, y up, z toward the viewer (common.py). A [Group] is one runtime node: a world-space mesh with its own
material list; hex-colour "materials" paint vertex colours on the shared paint_vcol material (one draw call per node).
"""
import math

import numpy as np

import common as C
from common import bpy
from tank import WGeo, box


def ellipsoid(c, r, segs=12, rings=8):
    """UV ellipsoid at [c] with radii [r] (x, y, z)."""
    V, F = [], []
    for j in range(rings + 1):
        v = math.pi * j / rings
        for i in range(segs):
            u = 2 * math.pi * i / segs
            V.append((c[0] + r[0] * math.sin(v) * math.cos(u), c[1] + r[1] * math.cos(v), c[2] + r[2] * math.sin(v) * math.sin(u)))
    for j in range(rings):
        for i in range(segs):
            a, b = j * segs + i, j * segs + (i + 1) % segs
            F.append((a, a + segs, b + segs, b))
    return np.array(V), F


def cylinder(c, r, h, segs=16):
    """Upright closed cylinder: base centre [c], radius [r], height [h]."""
    V = []
    for y in (c[1], c[1] + h):
        for i in range(segs):
            u = 2 * math.pi * i / segs
            V.append((c[0] + r * math.cos(u), y, c[2] + r * math.sin(u)))
    V.append((c[0], c[1], c[2]))
    V.append((c[0], c[1] + h, c[2]))
    F = [(i, (i + 1) % segs, segs + (i + 1) % segs, segs + i) for i in range(segs)]
    F += [(2 * segs, (i + 1) % segs, i) for i in range(segs)]
    F += [(2 * segs + 1, segs + i, segs + (i + 1) % segs) for i in range(segs)]
    return np.array(V), F


class Group:
    """One runtime node: a WGeo with its own material list (name -> slot). With a [pivot] (world cm) the node sits at
    the pivot and its mesh is modelled around it (the renderer turns limbs and doors about their node's origin)."""

    def __init__(self, name, smooth=False, pivot=None, extras=None):
        self.name = name
        self.smooth = smooth
        self.pivot = pivot
        self.extras = extras
        self.g = WGeo()
        self.mats = []
        self.slots = {}

    def slot(self, m):
        if m.name not in self.slots:
            self.slots[m.name] = len(self.mats)
            self.mats.append(m)
        return self.slots[m.name]

    def box(self, lo, hi, m):
        self.add(box(lo, hi), m)

    def add(self, VF, m):
        """Adds a mesh with material [m], or painted in the hex colour [m] (vertex colour, one shared material: the
        small figures, fish and corals stay one draw call per node)."""
        V, F = VF
        if isinstance(m, str):
            self.g.add(V, F, col=C.lin(m) + (1.0,), mat=self.slot(mat("paint_vcol", "#FFFFFF", 0.6)))
        else:
            self.g.add(V, F, mat=self.slot(m))

    def build(self, root):
        if self.pivot is not None:
            self.g.V = [np.asarray(v, float) - np.asarray(self.pivot, float) for v in self.g.V]
        o = self.g.obj(self.name, root, "store", self.mats, smooth=self.smooth, extras=self.extras)
        if self.pivot is not None:
            o.location = C.w2b(self.pivot)
        return o

    def empty(self):
        return len(self.g.F) == 0


_mats = {}


def mat(name, color, rough=0.6, metal=0.0, emit=None, strength=1.0, alpha=1.0):
    if name not in _mats:
        _mats[name] = C.material(name, color, rough=rough, metal=metal, emissive=emit, emissive_strength=strength,
                                 alpha=alpha, blend=alpha < 1.0, double=alpha < 1.0)
    return _mats[name]


def fish(g, c, length, body, yaw=0.0):
    """A little fish facing +x (turned by [yaw] about y): an egg body and a tail fan."""
    cy, sy = math.cos(yaw), math.sin(yaw)
    V, F = ellipsoid((0, 0, 0), (length * 0.34, length * 0.2, length * 0.1), 10, 6)
    tail = np.array([(-length * 0.3, 0, 0), (-length * 0.52, length * 0.16, 0), (-length * 0.52, -length * 0.16, 0),
                     (-length * 0.3, 0, 0.02), (-length * 0.52, length * 0.16, 0.02), (-length * 0.52, -length * 0.16, 0.02)])
    TF = [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)]
    for VV, FF in ((V, F), (tail, TF)):
        R = np.c_[VV[:, 0] * cy - VV[:, 2] * sy, VV[:, 1], VV[:, 0] * sy + VV[:, 2] * cy] + np.asarray(c)
        g.add((R, FF), body)


def text(body, x, y, z, size, m, depth=1.2):
    """Blender text converted to a mesh, standing on the plane z (world), reading along +x."""
    bpy.ops.object.text_add(location=(x, -z, y), rotation=(math.radians(90), 0, 0))
    o = bpy.context.active_object
    o.data.body = body
    o.data.size = size
    o.data.extrude = depth / 2
    o.data.align_x = 'CENTER'
    o.data.align_y = 'CENTER'
    o.data.materials.append(m)
    bpy.ops.object.convert(target='MESH')
    o = bpy.context.active_object
    o.location.y -= depth / 2      # the back face on the wall plane, the letters toward the viewer
    return o


