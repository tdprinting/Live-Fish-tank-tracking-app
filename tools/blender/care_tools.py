"""Care props for "Fix my tank" (ASSETS.md §5.2): care_tools.glb, cm, every mesh closed.

The renderer's CareScene (app render/CareScene.kt) moves each node on its own, so every prop is authored around its own
pivot at the origin (Y up, world-like axes; Blender gets them through tank.to_b). Only Hose is authored in world
space (it hangs from the tank into the bucket; CareLayout.HOSE_ANCHOR is its pop-in point).

Nodes (extras.part = "care"):
  Cup, CupLid, CupMuck    skimmer collection cup (sits on the hang-on skimmer's lid, equipment.py SKIMMER_CUP), clear
                          cup with its riser neck; CupMuck is a unit-tall plug of brown skimmate the renderer scales
                          to the cup's fill
  SockRing, SockBag       4 cm felt filter sock: the white collar rests on the HOB filter lid, the felt bag hangs inside
                          (the renderer tints the felt from clean white to muck brown with the sock's age)
  CarbonBag               mesh media bag of black carbon with a white drawstring
  Bottle, BottleCap, BottleBand   500 ml dosing bottle (cap pivot at its base; the renderer tints cap and band per dose)
  Bucket, BucketWater     2-gallon bucket with a wire handle; BucketWater is a unit-tall plug scaled to the level
  Jug, JugCap, JugWater   1-gallon RO/DI jug with a handle
  Hose                    siphon: a clear gravel-vac tube in the front-right of the tank, the hose over the right rim
                          and down into the bucket
  Stream                  unit-long pour stream along -Y (scaled per pour, tinted per liquid)
"""
import math
import os

import numpy as np

import common as C
import geomlib as G
import tank as T

# Where the props rest / work (world cm). Mirror these in app render/CareScene.kt CareLayout.
BUCKET_POS = (133.0, 6.0, 35.0)
HOSE_PATH = [(114.0, 17.0, 38.0), (114.0, 29.0, 38.0)]          # gravel-vac tube (radius VAC_R)
VAC_R = 2.0


def lathe(g, prof, n=28, mat=0, at=(0, 0, 0)):
    """Closed surface of revolution about Y through the (r, y) profile (r = 0 ends close as poles)."""
    cx, cy, cz = at
    rows = []
    V = []
    for r, y in prof:
        if r <= 1e-6:
            rows.append([len(V)])
            V.append((cx, cy + y, cz))
        else:
            row = []
            for k in range(n):
                a = 2 * math.pi * k / n
                row.append(len(V))
                V.append((cx + r * math.cos(a), cy + y, cz + r * math.sin(a)))
            rows.append(row)
    F = []
    for a, b in zip(rows, rows[1:]):
        if len(a) == 1 and len(b) == 1:
            continue
        if len(a) == 1:
            F += [(a[0], b[(k + 1) % n], b[k]) for k in range(n)]
        elif len(b) == 1:
            F += [(a[k], a[(k + 1) % n], b[0]) for k in range(n)]
        else:
            F += [(a[k], a[(k + 1) % n], b[(k + 1) % n], b[k]) for k in range(n)]
    V = np.array(V, float)
    uv = np.c_[np.arctan2(V[:, 2] - cz, V[:, 0] - cx) / (2 * math.pi) + 0.5, (V[:, 1] - cy) / max(1e-6, np.ptp(V[:, 1]))]
    g.add(V, F, uv, mat=mat)


def tube(g, pts, r, sides=12, mat=0):
    V, F, t, a = G.tube(np.asarray(pts, float), r, sides, cap_start=True, cap_end=True, up=(0, 1, 0))
    g.add(V, F, np.c_[a, t], mat=mat)


def torus(g, center, R, r, n=28, m=8, mat=0, arc=1.0, axis="y"):
    """Torus (or a closed arc of one) around Y, or around Z with axis="z" (an upright handle)."""
    pts = []
    steps = n if arc >= 1.0 else n + 1
    for i in range(steps):
        a = 2 * math.pi * arc * i / n
        if axis == "y":
            pts.append((center[0] + R * math.cos(a), center[1], center[2] + R * math.sin(a)))
        else:
            pts.append((center[0] + R * math.cos(a), center[1] + R * math.sin(a), center[2]))
    if arc >= 1.0:
        V, F = [], []
        P = np.array(pts)
        Tn = np.roll(P, -1, axis=0) - np.roll(P, 1, axis=0)
        for i, p in enumerate(P):
            t = Tn[i] / np.linalg.norm(Tn[i])
            u = (p - np.asarray(center)) / np.linalg.norm(p - np.asarray(center))
            w = np.cross(t, u)
            for j in range(m):
                b = 2 * math.pi * j / m
                V.append(p + r * (math.cos(b) * u + math.sin(b) * w))
        for i in range(n):
            for j in range(m):
                a0 = i * m + j
                a1 = ((i + 1) % n) * m + j
                b0 = i * m + (j + 1) % m
                b1 = ((i + 1) % n) * m + (j + 1) % m
                F.append((a0, a1, b1, b0))
        g.add(np.array(V), F, mat=mat)
    else:
        tube(g, pts, r, m, mat)


def build():
    C.reset_scene()
    root = C.empty("Root")
    C.set_extras(root, units="cm", spec="ASSETS.md §5.2 care props")
    clear = C.material("cup_clear", "#E6EEF0", rough=0.12, alpha=0.32, blend=True, double=True)
    black = C.material("lid_black", "#141516", rough=0.45)
    muck = C.material("skimmate", "#4A3417", rough=0.35, alpha=0.93, blend=True)
    ring_white = C.material("sock_ring", "#F1F1EC", rough=0.4)
    felt = C.material("felt", "#F3F1EA", rough=0.97)
    carbon = C.material("carbon_bag", "#1D1E20", rough=0.9)
    tie = C.material("drawstring", "#E9E6DC", rough=0.8)
    bottle = C.material("bottle_white", "#F5F5F2", rough=0.35)
    cap = C.material("bottle_cap", "#FFFFFF", rough=0.4)
    band = C.material("bottle_band", "#FFFFFF", rough=0.5)
    bucket = C.material("bucket_white", "#F2F2EE", rough=0.5)
    handle = C.material("handle_wire", "#9DA3A8", rough=0.3, metal=0.6)
    water = C.material("bucket_water", "#9FD3E6", rough=0.05, alpha=0.55, blend=True)
    jug = C.material("jug_clear", "#CFE4F2", rough=0.15, alpha=0.4, blend=True, double=True)
    jug_cap = C.material("jug_cap", "#2F6FC2", rough=0.4)
    hose = C.material("hose_clear", "#D9EBE6", rough=0.1, alpha=0.45, blend=True, double=True)
    stream = C.material("stream", "#FFFFFF", rough=0.05, alpha=0.75, blend=True)
    objs = []

    def obj(g, name, mats):
        o = g.obj(name, root, "care", mats, smooth=True)
        objs.append(o)
        return o

    # --- skimmer cup: clear cup (r 3.4, 8 cm) with the riser neck inside, black lid, the skimmate plug
    g = T.WGeo()
    lathe(g, [(0, 0), (3.3, 0), (3.4, 0.3), (3.4, 7.7), (3.3, 8.0), (0, 8.0)])
    lathe(g, [(0, 0.05), (1.7, 0.05), (1.5, 3.0), (1.05, 5.4), (0.9, 5.6), (0, 5.6)], n=20)
    obj(g, "Cup", [clear])
    g = T.WGeo()
    lathe(g, [(0, 8.0), (3.65, 8.0), (3.7, 8.6), (3.5, 8.9), (1.0, 8.9), (0.9, 9.6), (0, 9.7)])
    obj(g, "CupLid", [black])
    g = T.WGeo()
    lathe(g, [(0, 0), (3.15, 0), (3.15, 1.0), (0, 1.0)])
    obj(g, "CupMuck", [muck])

    # --- filter sock: collar ring on top (y 0..0.6) and the felt bag hanging 12 cm under it
    g = T.WGeo()
    lathe(g, [(0, -0.4), (1.9, -0.4), (2.25, -0.15), (2.6, 0.2), (2.6, 0.6), (2.1, 0.6), (0, 0.6)])
    obj(g, "SockRing", [ring_white])
    g = T.WGeo()
    prof = [(0, -12.4)]
    for i in range(1, 7):
        a = i / 6 * math.pi / 2
        prof.append((1.85 * math.sin(a), -12.4 + 1.85 * (1 - math.cos(a))))
    prof += [(1.95, -6.0), (2.1, -1.0), (2.15, -0.35), (0, -0.35)]
    lathe(g, prof, n=24)
    obj(g, "SockBag", [felt])

    # --- carbon bag: a lumpy mesh pillow of carbon (pivot at its tied top) and the drawstring knot
    g = T.WGeo()
    prof = [(0, -8.0)]
    for i in range(1, 9):
        y = -8.0 + 7.4 * i / 9
        prof.append((2.2 * math.sin(math.pi * i / 9) ** 0.6 + 0.1 * math.sin(i * 2.3), y))
    prof += [(0.55, -0.4), (0, -0.2)]
    lathe(g, prof, n=20)
    lathe(g, [(0, -0.6), (0.7, -0.6), (0.75, 0.1), (0.4, 0.6), (0, 0.6)], n=12, mat=1)
    obj(g, "CarbonBag", [carbon, tie])

    # --- dosing bottle: body + shoulder + neck (y 0..15.4), screw cap (pivot at its base), label band
    g = T.WGeo()
    lathe(g, [(0, 0), (3.0, 0), (3.2, 0.4), (3.2, 11.2), (2.9, 12.4), (2.1, 13.5), (1.25, 14.2), (1.25, 15.4), (0, 15.4)])
    obj(g, "Bottle", [bottle])
    g = T.WGeo()
    lathe(g, [(0, 0), (1.45, 0), (1.5, 0.2), (1.5, 1.7), (1.3, 1.9), (0, 1.9)], n=20)
    obj(g, "BottleCap", [cap])
    g = T.WGeo()
    lathe(g, [(0, 3.6), (3.26, 3.6), (3.26, 10.0), (0, 10.0)])
    obj(g, "BottleBand", [band])

    # --- bucket: open shell (outer r 8.5 -> 9.8 over 20 cm, 0.4 cm wall), rolled lip, wire handle on two lugs
    g = T.WGeo()
    lathe(g, [(0, 0), (8.5, 0), (9.8, 20.0), (10.2, 20.2), (10.2, 20.8), (9.6, 20.9), (9.4, 20.4),
              (8.15, 0.6), (0, 0.6)], n=36)
    for s in (-1, 1):
        lathe(g, [(0, 0), (0.9, 0), (0.9, 0.6), (0, 0.6)], n=10, at=(s * 9.6, 18.0, 0))   # handle lugs
    obj(g, "Bucket", [bucket])
    g = T.WGeo()
    pts = [(-10.15, 18.5, 0)]
    for i in range(13):
        a = math.pi * i / 12
        pts.append((-10.2 * math.cos(a), 21.0 + 7.0 * math.sin(a), 0.0))
    pts.append((10.15, 18.5, 0))
    tube(g, pts, 0.18, 6)
    tube(g, [(-3.0, 28.0, 0), (3.0, 28.0, 0)], 0.7, 10, mat=1)   # grip
    o = obj(g, "BucketHandle", [handle, black])
    g = T.WGeo()
    lathe(g, [(0, 0), (8.0, 0), (8.0, 1.0), (0, 1.0)], n=32)
    obj(g, "BucketWater", [water])

    # --- RO/DI jug: rounded body (r 6.4, y 0..20), shoulder to a neck, blue cap, handle on the shoulder
    g = T.WGeo()
    lathe(g, [(0, 0), (6.0, 0), (6.4, 0.6), (6.4, 17.0), (5.6, 19.6), (3.4, 21.6), (1.7, 22.4), (1.7, 24.0), (0, 24.0)], n=32)
    torus(g, (-5.4, 17.0, 0), 3.4, 0.55, n=14, m=8, arc=0.5, axis="z")
    obj(g, "Jug", [jug])
    g = T.WGeo()
    lathe(g, [(0, 0), (1.95, 0), (2.0, 1.6), (0, 1.6)], n=20)
    obj(g, "JugCap", [jug_cap])
    g = T.WGeo()
    lathe(g, [(0, 0), (5.9, 0), (5.9, 1.0), (0, 1.0)], n=28)
    obj(g, "JugWater", [water])

    # --- siphon (world space): gravel-vac tube in the water, hose up over the right rim into the bucket
    g = T.WGeo()
    tube(g, HOSE_PATH, VAC_R, 18)
    bx, by, bz = BUCKET_POS
    rim = C.RIM_Y
    top = HOSE_PATH[-1]
    path = [top, (top[0] + 0.5, 33.0, 38.0), (top[0] + 2.0, rim + 2.6, 38.2), (118.0, rim + 4.6, 38.0),
            (123.0, rim + 4.2, 37.6), (126.5, rim + 1.0, 37.0), (129.0, by + 18.0, 36.0), (bx - 2.0, by + 9.0, bz)]
    pts = G.bezier(path[0], path[1], path[2], path[3], 8).tolist() + G.bezier(path[3], path[4], path[5], path[6], 8).tolist()[1:] + [path[7]]
    tube(g, pts, 0.7, 10)
    obj(g, "Hose", [hose])

    # --- pour stream: unit length along -Y
    g = T.WGeo()
    lathe(g, [(0, -1.0), (0.32, -1.0), (0.3, 0.0), (0, 0.0)], n=10)
    obj(g, "Stream", [stream])

    bad = {}
    for o in objs:
        C.recalc_normals(o)
        nm = T.non_manifold(o)
        if nm:
            bad[o.name] = nm
        C.triangulate(o)
    print("[care] non-manifold edges:", bad or 0)
    path = os.path.join(C.MODELS, "care_tools.glb")
    C.export_glb(path, C.all_descendants(root))
    tris = sum(C.tri_count(o) for o in objs)
    print(f"[care] care_tools.glb: {tris} tris, {os.path.getsize(path) / 1024:.0f} KB")
    return tris


if __name__ == "__main__":
    build()
