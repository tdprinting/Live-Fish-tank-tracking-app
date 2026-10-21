"""Minimal GLB reader for the body-profile bake: node world matrices, part tags and POSITION / COLOR_0 per primitive."""
import json
import struct

import numpy as np

_CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
_NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def _accessor(j, bin_, i):
    a = j["accessors"][i]
    bv = j["bufferViews"][a["bufferView"]]
    dt = np.dtype(_CT[a["componentType"]])
    nc = _NC[a["type"]]
    off = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = bv.get("byteStride", dt.itemsize * nc)
    raw = np.frombuffer(bin_, dtype=np.uint8, count=stride * (a["count"] - 1) + dt.itemsize * nc, offset=off)
    rows = np.lib.stride_tricks.as_strided(raw, shape=(a["count"], dt.itemsize * nc), strides=(stride, 1))
    out = np.ascontiguousarray(rows).view(dt).reshape(a["count"], nc).astype(np.float64)
    if a.get("normalized") and dt != np.float32:
        out /= float(np.iinfo(dt).max)
    return out


def _local(n):
    if "matrix" in n:
        return np.array(n["matrix"], dtype=np.float64).reshape(4, 4).T
    t = np.array(n.get("translation", [0, 0, 0]), dtype=np.float64)
    x, y, z, w = n.get("rotation", [0, 0, 0, 1])
    s = np.array(n.get("scale", [1, 1, 1]), dtype=np.float64)
    r = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])
    m = np.eye(4)
    m[:3, :3] = r * s
    m[:3, 3] = t
    return m


def read(path):
    """[(node name, extras, positions (n×3, world), colors (n×4) or None)] for every mesh node."""
    b = open(path, "rb").read()
    n0 = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + n0])
    off = 20 + n0
    n1 = struct.unpack("<I", b[off:off + 4])[0]
    bin_ = b[off + 8: off + 8 + n1]
    nodes = j["nodes"]
    out = []

    def walk(i, parent):
        n = nodes[i]
        w = parent @ _local(n)
        if "mesh" in n:
            mesh = j["meshes"][n["mesh"]]
            extras = dict(mesh.get("extras", {}))
            extras.update(n.get("extras", {}))
            for p in mesh["primitives"]:
                at = p["attributes"]
                pos = _accessor(j, bin_, at["POSITION"])
                pos = (np.c_[pos, np.ones(len(pos))] @ w.T)[:, :3]
                col = _accessor(j, bin_, at["COLOR_0"]) if "COLOR_0" in at else None
                out.append((n.get("name", ""), extras, pos, col))
        for c in n.get("children", []):
            walk(c, w)

    for r in j["scenes"][j.get("scene", 0)]["nodes"]:
        walk(r, np.eye(4))
    root = next((n.get("extras", {}) for n in nodes if n.get("name") == "Root"), {})
    return out, root
