"""LOD1 for a committed organism GLB (headless bpy): <model>_lod1.glb, drawn by TankRenderer.drawOrganism beyond
RenderQuality.lod1DistanceCm (60 cm on High, 45 on Medium: the default camera sees every animal of Tee's tank from
farther than that).

    python3 tools/blender/lod1.py                 # every model in PLAN
    python3 tools/blender/lod1.py hermit_blue     # one

Only the geometry changes. The LOD0 file is imported (vertices merged across UV seams), every mesh node over
MIN_TRIS triangles is collapse-decimated toward the model's target, and each decimated node's new mesh is grafted back
into a copy of the LOD0 glTF, so node names, node / root extras (limbPivots, gait, ...), materials and textures stay
exactly as the generator wrote them; the root gets extras.lod = 1 (validate_glb.py).

COLOR_0 carries limb ids and flags for the crawler rigs (fish.vert: limb index / 9, eye flag, side, class). An edge
collapse only ever merges vertices of one connected piece, and every limb is its own piece, so those channels stay
exact; the build checks it anyway: a channel that is constant over every piece of the LOD0 node (an id) must keep
exactly its LOD0 values in the LOD1, or the build fails.
"""
import json
import os
import struct
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
from common import bpy  # noqa: E402

# model -> LOD1 triangle target (about 40 % of LOD0; validate_glb.py LOD1_BUDGET is the cap)
PLAN = {
    "hermit_blue": 1200, "hermit_scarlet": 1200, "shrimp": 1800, "skunk_shrimp": 1500, "sally_lightfoot": 2200,
    "brittle_star": 1000, "nassarius": 450, "astrea": 450,
}
MIN_TRIS = 120     # smaller nodes (eyes, feet, siphons) are kept as they are

CT = {5126: ("f", 4), 5125: ("I", 4), 5123: ("H", 2), 5121: ("B", 1), 5122: ("h", 2), 5120: ("b", 1)}
NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


def read_glb(path):
    d = open(path, "rb").read()
    jl = struct.unpack_from("<I", d, 12)[0]
    j = json.loads(d[20:20 + jl])
    off = 20 + jl
    b = d[off + 8:off + 8 + struct.unpack_from("<I", d, off)[0]] if off < len(d) else b""
    return j, bytearray(b)


def write_glb(path, j, b):
    while len(b) % 4:
        b += b"\0"
    j["buffers"] = [{"byteLength": len(b)}]
    js = json.dumps(j, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(b))
    out += struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(b), 0x004E4942) + bytes(b)
    with open(path, "wb") as f:
        f.write(out)


def accessor(j, b, i):
    a = j["accessors"][i]
    bv = j["bufferViews"][a["bufferView"]]
    fmt, size = CT[a["componentType"]]
    n = NC[a["type"]]
    stride = bv.get("byteStride", size * n)
    base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    dt = np.dtype("<" + fmt)
    out = np.empty((a["count"], n), np.float64)
    for k in range(n):
        out[:, k] = np.ndarray((a["count"],), dt, b, base + k * size, (stride,))
    if a.get("normalized"):
        out /= {"B": 255.0, "H": 65535.0, "b": 127.0, "h": 32767.0}[fmt]
    return out


def tris(j, mesh):
    return sum(j["accessors"][p["indices"]]["count"] // 3 for p in j["meshes"][mesh]["primitives"])


def graft(dst_j, dst_b, src_j, src_b, src_mesh):
    """Copies mesh [src_mesh] of the exported LOD (its accessors and data) into dst; returns the new mesh index."""
    mat_names = [m.get("name") for m in dst_j.get("materials", [])]
    prims = []
    for p in src_j["meshes"][src_mesh]["primitives"]:
        q = {"attributes": {}, "mode": p.get("mode", 4)}
        for key, ai in list(p["attributes"].items()) + [("__indices", p["indices"])]:
            a = dict(src_j["accessors"][ai])
            bv = src_j["bufferViews"][a.pop("bufferView")]
            o = bv.get("byteOffset", 0) + a.pop("byteOffset", 0)
            data = src_b[o:o + bv["byteLength"]]
            while len(dst_b) % 4:
                dst_b += b"\0"
            view = {"buffer": 0, "byteOffset": len(dst_b), "byteLength": len(data)}
            if "byteStride" in bv:
                view["byteStride"] = bv["byteStride"]
            if "target" in bv:
                view["target"] = bv["target"]
            dst_b += data
            dst_j["bufferViews"].append(view)
            a["bufferView"] = len(dst_j["bufferViews"]) - 1
            dst_j["accessors"].append(a)
            if key == "__indices":
                q["indices"] = len(dst_j["accessors"]) - 1
            else:
                q["attributes"][key] = len(dst_j["accessors"]) - 1
        if "material" in p:
            q["material"] = mat_names.index(src_j["materials"][p["material"]]["name"])
        prims.append(q)
    dst_j["meshes"].append({"name": src_j["meshes"][src_mesh].get("name", "lod1"), "primitives": prims})
    return len(dst_j["meshes"]) - 1


def compact(j, b):
    """Drops buffer views no accessor / image uses any more (the replaced LOD0 meshes) and repacks the BIN."""
    used_acc = set()
    for m in j["meshes"]:
        for p in m["primitives"]:
            used_acc |= set(p["attributes"].values())
            if "indices" in p:
                used_acc.add(p["indices"])
    used_meshes = {n["mesh"] for n in j["nodes"] if "mesh" in n}
    remap_mesh, meshes = {}, []
    for i, m in enumerate(j["meshes"]):
        if i in used_meshes:
            remap_mesh[i] = len(meshes)
            meshes.append(m)
    used_acc = set()
    for m in meshes:
        for p in m["primitives"]:
            used_acc |= set(p["attributes"].values())
            if "indices" in p:
                used_acc.add(p["indices"])
    remap_acc, accs = {}, []
    for i, a in enumerate(j["accessors"]):
        if i in used_acc:
            remap_acc[i] = len(accs)
            accs.append(a)
    used_views = {a["bufferView"] for a in accs} | {im["bufferView"] for im in j.get("images", []) if "bufferView" in im}
    remap_view, views, nb = {}, [], bytearray()
    for i, v in enumerate(j["bufferViews"]):
        if i not in used_views:
            continue
        while len(nb) % 4:
            nb += b"\0"
        o = v.get("byteOffset", 0)
        data = b[o:o + v["byteLength"]]
        v = dict(v, byteOffset=len(nb))
        nb += data
        remap_view[i] = len(views)
        views.append(v)
    for a in accs:
        a["bufferView"] = remap_view[a["bufferView"]]
    for im in j.get("images", []):
        if "bufferView" in im:
            im["bufferView"] = remap_view[im["bufferView"]]
    for m in meshes:
        for p in m["primitives"]:
            p["attributes"] = {k: remap_acc[v] for k, v in p["attributes"].items()}
            if "indices" in p:
                p["indices"] = remap_acc[p["indices"]]
    for n in j["nodes"]:
        if "mesh" in n:
            n["mesh"] = remap_mesh[n["mesh"]]
    j["meshes"], j["accessors"], j["bufferViews"] = meshes, accs, views
    return nb


def id_channels(j, b, mesh):
    """COLOR_0 channels that are constant over every connected piece of the LOD0 node (limb index, eye flag, side,
    class) -> their distinct values. Pieces are found with the vertices welded by position."""
    pos, col, idx, off = [], [], [], 0
    for p in j["meshes"][mesh]["primitives"]:
        if "COLOR_0" not in p["attributes"]:
            return {}
        P = accessor(j, b, p["attributes"]["POSITION"])
        pos.append(P)
        col.append(accessor(j, b, p["attributes"]["COLOR_0"]))
        idx.append(accessor(j, b, p["indices"]).astype(np.int64).ravel() + off)
        off += len(P)
    pos, col, idx = np.vstack(pos), np.vstack(col), np.concatenate(idx)
    _, weld = np.unique(np.round(pos, 5), axis=0, return_inverse=True)
    weld = weld.ravel()
    parent = np.arange(weld.max() + 1)

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for t in idx.reshape(-1, 3):
        a, c, d = (find(weld[v]) for v in t)
        parent[c] = a
        parent[d] = a
    comp = np.array([find(w) for w in weld])
    out = {}
    for c in range(col.shape[1]):
        ok = True
        for k in np.unique(comp):
            v = col[comp == k, c]
            if np.ptp(v) > 1e-4:
                ok = False
                break
        if ok:
            out[c] = np.unique(col[:, c])
    return out


def snap_ids(j, b, mesh, ids, label):
    """Blender's importer holds vertex colours as 8-bit sRGB, so the round trip moves every value a little: id channels
    ([id_channels] of the LOD0 node) are put back on their exact LOD0 values. A value that is not near one of them was
    blended by a collapse across two limbs: fail."""
    if not ids:
        return
    for p in j["meshes"][mesh]["primitives"]:
        ai = p["attributes"].get("COLOR_0")
        if ai is None:
            continue
        a = j["accessors"][ai]
        bv = j["bufferViews"][a["bufferView"]]
        assert a["componentType"] == 5123 and a["type"] == "VEC4" and "byteStride" not in bv
        base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        raw = np.ndarray((a["count"], 4), "<u2", b, base).astype(np.float64) / 65535.0
        for c, vals in ids.items():
            k = np.abs(raw[:, c, None] - vals[None, :]).argmin(axis=1)
            err = np.abs(raw[:, c] - vals[k]).max()
            if err > 0.01:
                raise SystemExit(f"{label}: COLOR_0[{c}] (an id channel) blended by the decimation ({err:.3f})")
            raw[:, c] = vals[k]
        np.ndarray((a["count"], 4), "<u2", b, base)[:] = np.round(raw * 65535.0).astype(np.uint16)


def halve_images(j, b):
    """The LOD1 is seen from beyond 45-60 cm: its embedded textures at half size, like the fish LOD1s."""
    import io
    from PIL import Image
    for im in j.get("images", []):
        bv = j["bufferViews"][im["bufferView"]]
        o = bv.get("byteOffset", 0)
        src = Image.open(io.BytesIO(bytes(b[o:o + bv["byteLength"]])))
        if min(src.size) < 64:
            continue
        fmt = src.format
        img = src.resize((src.width // 2, src.height // 2), Image.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, fmt, **({"quality": 88} if fmt in ("JPEG", "WEBP") else {"optimize": True}))
        data = buf.getvalue()
        while len(b) % 4:
            b += b"\0"
        j["bufferViews"].append({"buffer": 0, "byteOffset": len(b), "byteLength": len(data)})
        b += data
        im["bufferView"] = len(j["bufferViews"]) - 1


def build(name, target=None):
    src = os.path.join(C.MODELS, name + ".glb")
    out = os.path.join(C.MODELS, name + "_lod1.glb")
    target = target or PLAN[name]
    j, b = read_glb(src)
    nodes = {n["name"]: n for n in j["nodes"] if "mesh" in n}
    total = sum(tris(j, n["mesh"]) for n in nodes.values())
    small = sum(tris(j, n["mesh"]) for n in nodes.values() if tris(j, n["mesh"]) <= MIN_TRIS)
    ratio = min(1.0, max(0.05, (target - small) / max(total - small, 1)))

    C.reset_scene()
    bpy.ops.import_scene.gltf(filepath=src, merge_vertices=True)
    objs = {o.name: o for o in bpy.context.scene.objects if o.type == 'MESH'}
    for o in objs.values():
        me = o.data
        if me.color_attributes:
            me.color_attributes[0].name = "Col"
        if tris(j, nodes[o.name]["mesh"]) > MIN_TRIS:
            C.decimate(o, ratio)
    tmp = os.path.join(tempfile.mkdtemp(), name + "_lod1.glb")
    C.export_glb(tmp, list(objs.values()))
    sj, sb = read_glb(tmp)
    snodes = {n["name"]: n for n in sj["nodes"] if "mesh" in n}
    for nm, n in nodes.items():
        if tris(j, n["mesh"]) <= MIN_TRIS:
            continue
        if nm not in snodes:
            raise SystemExit(f"{name}: node {nm} lost in the LOD export")
        ids = id_channels(j, b, n["mesh"])
        n["mesh"] = graft(j, b, sj, sb, snodes[nm]["mesh"])
        snap_ids(j, b, n["mesh"], ids, f"{name}/{nm}")
    halve_images(j, b)
    b = compact(j, b)
    root = j["nodes"][j["scenes"][j.get("scene", 0)]["nodes"][0]]
    root.setdefault("extras", {})["lod"] = 1
    write_glb(out, j, b)
    C.optimize(out)
    n1 = sum(tris(j, n["mesh"]) for n in j["nodes"] if "mesh" in n)
    print(f"[lod1] {name}_lod1.glb: {total} -> {n1} tris (target {target}), {os.path.getsize(out) / 1024:.0f} KB")
    return out


if __name__ == "__main__":
    names = [a for a in sys.argv[1:] if not a.startswith("-")] or list(PLAN)
    for nm in names:
        build(nm)
