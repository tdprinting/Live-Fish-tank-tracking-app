#!/usr/bin/env python3
"""Download-size pass for the committed GLBs (numpy + Pillow; idempotent). The web app downloads every model it
shows, so what ships is what a phone fetches: see docs/ASSETS.md §6 ("Download size").

    python3 tools/optimize_glb.py                    # every app/src/main/assets/models/*.glb
    python3 tools/optimize_glb.py path.glb [...]     # some (tools/blender/common.export_glb runs it on every export)

What it does, all read back by GlbLoader (Android) and its web twin unchanged in meaning:
  * embedded textures: opaque ones as JPEG (q 90, 4:4:4 chroma so thin fish stripes keep their colour), ones whose
    alpha carries something (fin sheets, the rock's hair-algae mask, the squid's fin) as WebP (lossy colour q 90,
    lossless alpha); an image is only replaced when that is at least 10 % smaller, so small PNG fin sheets stay PNG.
    No image edge over 2048 (MAX_EDGE).
  * NORMAL as normalized int8 (KHR_mesh_quantization, listed in extensionsUsed/Required: validate_glb.py allows it),
    TEXCOORD_0 as normalized uint16 where it stays inside 0..1 (core glTF), COLOR_0 as normalized uint8 on the
    room / store / care props (`part` store, room, care: room.frag / store.frag read it as a tint and baked AO).
    Organism COLOR_0 (spine coordinate, limb ids) stays uint16.
  * the rock (rockscape*.glb) keeps float normals and uint16 COLOR_0: tools/blender/rock_surface.py rewrites them in
    place.
  * unused buffer views are dropped and the BIN repacked.
"""
import glob
import io
import json
import os
import struct
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODELS = os.path.join(ROOT, "app", "src", "main", "assets", "models")
MAX_EDGE = 2048
JPEG_Q = 90
WEBP_Q = 90
MIN_GAIN = 0.9
BYTE_COLOR_PARTS = {"store", "room", "care"}
KEEP_ATTRS = ("rockscape",)  # rock_surface.py rewrites NORMAL / COLOR_0 in place (float / uint16)
QUANT = "KHR_mesh_quantization"

CT = {5126: ("f", 4), 5125: ("I", 4), 5123: ("H", 2), 5121: ("B", 1), 5122: ("h", 2), 5120: ("b", 1)}
NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}
NORM = {"B": 255.0, "H": 65535.0, "b": 127.0, "h": 32767.0}


def read_glb(path):
    d = open(path, "rb").read()
    jl = struct.unpack_from("<I", d, 12)[0]
    j = json.loads(d[20:20 + jl])
    off = 20 + jl
    b = d[off + 8:off + 8 + struct.unpack_from("<I", d, off)[0]] if off < len(d) else b""
    return j, bytes(b)


def write_glb(path, j, b):
    b = bytes(b) + b"\0" * ((4 - len(b) % 4) % 4)
    j["buffers"] = [{"byteLength": len(b)}]
    js = json.dumps(j, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(b)))
        f.write(struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(b), 0x004E4942) + b)


def read_acc(j, b, i):
    a = j["accessors"][i]
    bv = j["bufferViews"][a["bufferView"]]
    fmt, size = CT[a["componentType"]]
    n = NC[a["type"]]
    stride = bv.get("byteStride", size * n)
    base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    out = np.empty((a["count"], n), np.float64)
    for k in range(n):
        out[:, k] = np.ndarray((a["count"],), np.dtype("<" + fmt), b, base + k * size, (stride,))
    if a.get("normalized"):
        out = np.maximum(out / NORM[fmt], -1.0)
    return out


class Out:
    """The new BIN: views appended 4-byte aligned."""

    def __init__(self):
        self.b = bytearray()
        self.views = []

    def add(self, data, stride=None, target=None):
        while len(self.b) % 4:
            self.b += b"\0"
        v = {"buffer": 0, "byteOffset": len(self.b), "byteLength": len(data)}
        if stride:
            v["byteStride"] = stride
        if target:
            v["target"] = target
        self.b += data
        self.views.append(v)
        return len(self.views) - 1


def encode_image(data):
    """(bytes, mime) for an embedded image: JPEG when opaque, WebP when its alpha matters; the original when not
    [MIN_GAIN] smaller."""
    from PIL import Image
    im = Image.open(io.BytesIO(data))
    fmt = im.format
    im.load()
    w, h = im.size
    if max(w, h) > MAX_EDGE:
        k = MAX_EDGE / max(w, h)
        im = im.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
        fmt = None  # must re-encode
    alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
    if alpha:
        rgba = im.convert("RGBA")
        alpha = int(np.asarray(rgba)[..., 3].min()) < 255
    if fmt in ("JPEG", "WEBP"):
        return data, "image/jpeg" if fmt == "JPEG" else "image/webp"
    buf = io.BytesIO()
    if alpha:
        im.convert("RGBA").save(buf, "WEBP", quality=WEBP_Q, alpha_quality=100, method=6)
        mime = "image/webp"
    else:
        im.convert("RGB").save(buf, "JPEG", quality=JPEG_Q, subsampling=0, optimize=True)
        mime = "image/jpeg"
    new = buf.getvalue()
    if fmt is not None and len(new) > MIN_GAIN * len(data):
        return data, "image/png" if fmt == "PNG" else Image.MIME.get(fmt, "image/png")
    return new, mime


def optimize(path):
    j, b = read_glb(path)
    name = os.path.splitext(os.path.basename(path))[0]
    before = os.path.getsize(path)
    keep_attrs = name.startswith(KEEP_ATTRS)
    out = Out()
    accs = []
    acc_map = {}

    def copy_acc(i):
        """Accessor [i] copied unchanged (its data into its own view)."""
        if ("copy", i) in acc_map:
            return acc_map[("copy", i)]
        a = dict(j["accessors"][i])
        bv = j["bufferViews"][a["bufferView"]]
        fmt, size = CT[a["componentType"]]
        n = NC[a["type"]]
        stride = bv.get("byteStride", size * n)
        base = bv.get("byteOffset", 0) + a.pop("byteOffset", 0)
        if "byteStride" in bv:  # interleaved or padded (int8 normals: 4-byte stride): keep the stride
            data = b[base:base + (a["count"] - 1) * stride + size * n] if a["count"] else b""
            a["bufferView"] = out.add(data, stride, bv.get("target"))
        else:
            a["bufferView"] = out.add(b[base:base + a["count"] * size * n], None, bv.get("target"))
        accs.append(a)
        acc_map[("copy", i)] = len(accs) - 1
        return len(accs) - 1

    def new_acc(src, arr, ctype, normalized, stride=None):
        a = {"componentType": ctype, "count": len(arr), "type": src["type"]}
        if normalized:
            a["normalized"] = True
        for k in ("min", "max"):
            if k in src and src["componentType"] == 5126 and not normalized:
                a[k] = src[k]
        a["bufferView"] = out.add(arr.tobytes(), stride, 34962)
        accs.append(a)
        return len(accs) - 1

    used_quant = False
    node_part = {}
    for n in j.get("nodes", []):
        if "mesh" in n:
            node_part.setdefault(n["mesh"], set()).add((n.get("extras") or {}).get("part"))
    for mi, m in enumerate(j["meshes"]):
        byte_color = bool(node_part.get(mi)) and node_part[mi] <= BYTE_COLOR_PARTS
        for p in m["primitives"]:
            at = {}
            for k, ai in p["attributes"].items():
                src = j["accessors"][ai]
                if keep_attrs or src.get("sparse"):
                    at[k] = copy_acc(ai)
                elif k == "NORMAL" and src["componentType"] == 5126:
                    v = read_acc(j, b, ai)
                    ln = np.linalg.norm(v, axis=1, keepdims=True)
                    v = v / np.where(ln > 0, ln, 1)
                    q = np.zeros((len(v), 4), np.int8)
                    q[:, :3] = np.clip(np.round(v * 127), -127, 127)
                    at[k] = new_acc(src, q, 5120, True, 4)
                    used_quant = True
                elif k == "TEXCOORD_0" and src["componentType"] == 5126:
                    v = read_acc(j, b, ai)
                    if v.min() >= 0.0 and v.max() <= 1.0:
                        at[k] = new_acc(src, np.round(v * 65535).astype("<u2"), 5123, True)
                    else:
                        at[k] = copy_acc(ai)
                elif k == "COLOR_0" and byte_color and src["componentType"] in (5126, 5123):
                    v = read_acc(j, b, ai)
                    at[k] = new_acc(src, np.round(np.clip(v, 0, 1) * 255).astype(np.uint8), 5121, True)
                else:
                    at[k] = copy_acc(ai)
            p["attributes"] = at
            if "indices" in p:
                p["indices"] = copy_acc(p["indices"])
    for im in j.get("images", []):
        if "bufferView" not in im:
            continue
        bv = j["bufferViews"][im["bufferView"]]
        o = bv.get("byteOffset", 0)
        data, mime = encode_image(b[o:o + bv["byteLength"]])
        im["bufferView"] = out.add(data)
        im["mimeType"] = mime
    # the tang's unused alternate texture (read by nothing at runtime; its life stages live in textures/stages)
    for n in j.get("nodes", []):
        (n.get("extras") or {}).pop("altTextures", None)
    j["accessors"] = accs
    j["bufferViews"] = out.views
    if used_quant:
        for k in ("extensionsUsed", "extensionsRequired"):
            if QUANT not in j.setdefault(k, []):
                j[k].append(QUANT)
    write_glb(path, j, out.b)
    return before, os.path.getsize(path)


def main(argv):
    files = argv or sorted(glob.glob(os.path.join(MODELS, "*.glb")))
    tb = ta = 0
    for f in files:
        b0, b1 = optimize(f)
        tb += b0
        ta += b1
        if len(files) < 8 or b0 - b1 > 50000:
            print(f"[optimize_glb] {os.path.basename(f)}: {b0 / 1024:.0f} -> {b1 / 1024:.0f} KB")
    print(f"[optimize_glb] {len(files)} files: {tb / 1e6:.2f} -> {ta / 1e6:.2f} MB")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
