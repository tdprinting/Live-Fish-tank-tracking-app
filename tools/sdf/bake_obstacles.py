#!/usr/bin/env python3
"""Static obstacle bake (SIMULATION §4.3, owner rule: no body passes through decor).

Reads the coral, decor and equipment GLBs in app/src/main/assets/models (world-space cm, as the renderer draws them)
and writes core/src/main/kotlin/com/reeflive/core/sim/StaticObstacleData.kt: per model, its root anchor and, per mesh
node, the node centre and a set of capsules fitted to the node's vertices. StaticObstacles parses that table once;
nothing is read at run time per tick.

Fit: the node's vertices (clipped to the water box) are binned on a voxel grid; each occupied voxel becomes one capsule
along the principal axis of its vertices (length from the 10th to the 90th percentile of the projections, radius the
90th percentile of the distance from that axis). Solid models are also filled: an empty voxel with mesh voxels of the
same node on all sides but below (corals are open where they sit on the rock) becomes a sphere, so a body cannot sit
inside a hollow shell. Polyp instance tables (tufts, GSP and zoa polyps, toadstool and leather polyps) are left out:
they are soft, retract on contact and are drawn over the body mesh.

Only numpy and the standard library. Run: python3 tools/sdf/bake_obstacles.py
"""
import glob
import json
import os
import struct

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MODELS = os.path.join(ROOT, "app", "src", "main", "assets", "models")
SPECIES = os.path.join(ROOT, "core", "src", "main", "resources", "species")
OUT = os.path.join(ROOT, "core", "src", "main", "kotlin", "com", "reeflive", "core", "sim", "StaticObstacleData.kt")

TANK = (121.9, 56.5, 33.0)   # water box x, y (surface + the feed ring), z

# model -> (voxel cm, fill interior, node names to keep or None = every non-polyp node)
CORALS = {
    "kenya": (1.5, False, None),
    "organ_pipe": (1.5, True, None),
    "zoa": (2.0, True, None),
    "gsp_mat": (2.0, True, None),
    "gsp_neon": (2.0, True, None),
    "mushroom": (2.0, True, None),
    "mushroom_hairy": (1.5, True, None),
    "clove": (2.0, True, None),
    "macro_red": (2.0, False, None),
    "toadstool_leather": (2.0, True, None),
    "leather_pink": (2.0, True, None),
    "finger_leather": (1.5, True, None),
    "elephant_ear": (2.0, True, None),
}
DECOR = {
    # the sand-front mushrooms (decor, not a sim colony) and the inner half of the magnetic glass cleaner
    "reef_details": (2.0, True, {"MushroomsRed", "MagCleaner"}),
    # every equipment node, clipped to the water: heater, HOB intake tube, powerheads, HOB / skimmer lips, magnet...
    "equipment": (2.0, True, None),
}
CORAL_SPECIES = ["capnella_sp", "briareum_sp", "zoanthus_sp", "clavularia_sp", "discosoma_sp", "rhodactis_sp", "gracilaria_sp",
                 "tubipora_musica", "sarcophyton_sp", "lobophytum_sp", "sinularia_sp", "amplexidiscus_fenestrafer"]


def load(path):
    b = open(path, "rb").read()
    jl, = struct.unpack_from("<I", b, 12)
    js = json.loads(b[20:20 + jl])
    off = 20 + jl
    bl, = struct.unpack_from("<I", b, off)
    return js, b[off + 8:off + 8 + bl]


def accessor(js, bin_, i):
    a = js["accessors"][i]
    bv = js["bufferViews"][a["bufferView"]]
    n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
    dt = np.dtype({5126: np.float32, 5125: np.uint32, 5123: np.uint16, 5121: np.uint8}[a["componentType"]])
    o = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = bv.get("byteStride")
    if stride and stride != n * dt.itemsize:
        raw = np.frombuffer(bin_, np.uint8, count=stride * a["count"], offset=o).reshape(a["count"], stride)
        return raw[:, :n * dt.itemsize].copy().view(dt).reshape(a["count"], n)
    return np.frombuffer(bin_, dt, count=a["count"] * n, offset=o).reshape(a["count"], n)


def node_matrix(n):
    if "matrix" in n:
        return np.array(n["matrix"], float).reshape(4, 4).T
    t = np.array(n.get("translation", [0, 0, 0]), float)
    x, y, z, w = n.get("rotation", [0, 0, 0, 1])
    s = np.array(n.get("scale", [1, 1, 1]), float)
    r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    m = np.eye(4)
    m[:3, :3] = r * s
    m[:3, 3] = t
    return m


def mesh_nodes(path):
    """(root anchor, [(node name, world vertices)]) for every non-polyp mesh node."""
    js, bin_ = load(path)
    out = []
    anchor = None

    def rec(ni, parent):
        nonlocal anchor
        n = js["nodes"][ni]
        m = parent @ node_matrix(n)
        ex = n.get("extras", {})
        if anchor is None and isinstance(ex.get("anchor"), str) and "mesh" not in n:
            anchor = ex["anchor"]
        if "mesh" in n and ex.get("part") not in ("polyp", "emitter", "light") and "instances" not in ex:
            v = np.vstack([accessor(js, bin_, p["attributes"]["POSITION"]) for p in js["meshes"][n["mesh"]]["primitives"]]).astype(float)
            out.append((n.get("name", "node"), (m[:3, :3] @ v.T).T + m[:3, 3]))
        for c in n.get("children", []):
            rec(c, m)

    for s in js["scenes"][js.get("scene", 0)]["nodes"]:
        rec(s, np.eye(4))
    return anchor, out


def fit(v, h, fill):
    """Capsules (ax ay az bx by bz r) for one node's vertices."""
    keep = (v[:, 0] >= 0) & (v[:, 0] <= TANK[0]) & (v[:, 1] <= TANK[1]) & (v[:, 2] >= 0) & (v[:, 2] <= TANK[2])
    v = v[keep]
    if len(v) == 0:
        return []
    key = np.floor(v / h).astype(int)
    caps = []
    cells = {}
    for k, p in zip(map(tuple, key), v):
        cells.setdefault(k, []).append(p)
    for k, pts in cells.items():
        p = np.array(pts)
        c = p.mean(0)
        if len(p) < 3:
            r = max(0.3, float(np.max(np.linalg.norm(p - c, axis=1))))
            caps.append((*c, *c, r))
            continue
        _, _, vt = np.linalg.svd(p - c, full_matrices=False)
        ax = vt[0]
        t = (p - c) @ ax
        perp = np.linalg.norm((p - c) - np.outer(t, ax), axis=1)
        t0, t1 = np.percentile(t, 10), np.percentile(t, 90)
        r = max(0.3, float(np.percentile(perp, 90)))
        caps.append((*(c + ax * t0), *(c + ax * t1), r))
    if fill:
        occ = set(cells)
        lo = key.min(0)
        hi = key.max(0)

        def hit(k, d):
            q = list(k)
            for _ in range(64):
                q[d[0]] += d[1]
                if q[d[0]] < lo[d[0]] or q[d[0]] > hi[d[0]]:
                    return False
                if tuple(q) in occ:
                    return True
            return False

        dirs = [(0, 1), (0, -1), (2, 1), (2, -1), (1, 1)]   # all but straight down (the colony sits on the rock there)
        for ix in range(lo[0], hi[0] + 1):
            for iy in range(lo[1], hi[1] + 1):
                for iz in range(lo[2], hi[2] + 1):
                    k = (ix, iy, iz)
                    if k in occ or not all(hit(k, d) for d in dirs):
                        continue
                    c = (np.array(k) + 0.5) * h
                    caps.append((*c, *c, h * 0.75))
    return caps


def fmt(x):
    s = "%.2f" % x
    return s.rstrip("0").rstrip(".") if "." in s else s


def main():
    lines = []
    stats = []
    for name, (h, fill, only) in list(CORALS.items()) + list(DECOR.items()):
        anchor, nodes = mesh_nodes(os.path.join(MODELS, name + ".glb"))
        lines.append("M %s %s" % (name, anchor or "-"))
        n = 0
        for node, v in nodes:
            if only is not None and node not in only:
                continue
            caps = fit(v, h, fill)
            if not caps:
                continue
            c = (v.min(0) + v.max(0)) / 2
            lines.append("N %s %s" % (node, " ".join(fmt(x) for x in c)))
            for cap in caps:
                lines.append("C " + " ".join(fmt(x) for x in cap))
            n += len(caps)
        stats.append("%s %d" % (name, n))
    # species -> candidate models (JSON model first, then variants), as the renderer's ModelRegistry
    sp = []
    for sid in CORAL_SPECIES:
        o = json.load(open(os.path.join(SPECIES, sid + ".json")))
        ms = [o["model"]] + list((o.get("variantModels") or {}).values())
        sp.append("S %s %s" % (sid, " ".join(os.path.basename(m)[:-4] for m in ms)))
    text = "\n".join(sp + lines)
    chunks, cur = [], []
    size = 0
    for ln in text.split("\n"):
        if size + len(ln) > 30000:
            chunks.append("\n".join(cur))
            cur, size = [], 0
        cur.append(ln)
        size += len(ln) + 1
    chunks.append("\n".join(cur))
    with open(OUT, "w") as f:
        f.write("package com.reeflive.core.sim\n\n")
        f.write("// GENERATED by tools/sdf/bake_obstacles.py from app/src/main/assets/models; do not edit by hand.\n")
        f.write("// %s\n\n" % ", ".join(stats))
        f.write("/**\n * Capsules fitted to the coral, decor and equipment models (world cm), parsed once by [StaticObstacles].\n")
        f.write(" * Lines: `S species model…` candidate models; `M model rootAnchor`; `N node cx cy cz` node centre;\n")
        f.write(" * `C ax ay az bx by bz r` one capsule of that node.\n */\n")
        f.write("internal object StaticObstacleData {\n    val CHUNKS: Array<String> = arrayOf(\n")
        for c in chunks:
            f.write('        """' + c + '""",\n')
        f.write("    )\n}\n")
    print(OUT)
    print(", ".join(stats))


if __name__ == "__main__":
    main()
