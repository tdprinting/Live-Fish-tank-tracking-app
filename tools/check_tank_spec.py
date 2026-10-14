"""Automated checks from docs/specs/tank_design.md §6 that need the built assets (stdlib + numpy only):
  - every anchor (with a pos) within 2 cm of a surface: rock (rock_sdf.bin) or sand (rockscape.sand_height)
  - the back-glass black visible through the three openings in the front render (luminance < 8 % at the
    opening centres; render it with `preview.py --scene --view front`)
Non-manifold edges, rock y range and rock tri count are printed by the builders / validate_glb.py.

  python3 tools/check_tank_spec.py [docs/preview/scene.png]
"""
import json
import math
import os
import struct
import sys
import zlib

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SIM = os.path.join(ROOT, "app", "src", "main", "assets", "sim")
sys.path.insert(0, os.path.join(ROOT, "tools", "blender"))


def smoothstep(a, b, x):
    t = np.clip((np.asarray(x, float) - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# mirrors of tools/blender/common.py TANK / SAND_Y* and rockscape LANES / SPOIL / BURROW_MOUTH (rockscape needs bpy)
TANK = (121.9, 40.6, 45.7)
SAND_Y, SAND_Y_BACK = 3.5, 6.0
LANES = ((26, 31, 9, 9.1), (54, 34, 8, 7.8))
SPOIL = (60.0, 37.0)
BURROW_MOUTH = (62.0, 32.5)


def sand_height(x, z, d):
    """Mirror of rockscape.sand_height (kept in sync; rockscape needs bpy)."""
    S = smoothstep
    D = TANK[2]
    base = SAND_Y + (SAND_Y_BACK - SAND_Y) * (D - z) / D
    h = base + 2.5 * (1 - S(0.0, 10.0, max(d, 0)))
    for cx, cz, rx, rz in LANES:
        e = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
        h -= 0.8 * (1 - S(0.6, 1.0, e))
    e = ((x - SPOIL[0]) / 6.0) ** 2 + ((z - SPOIL[1]) / 7.5) ** 2
    h += 1.5 * (1 - S(0.2, 1.0, e))
    k = 1 - S(2.5, 5.0, math.hypot(x - BURROW_MOUTH[0], (z - BURROW_MOUTH[1]) * 1.3))
    return float(h * (1 - k) + (base - 0.4) * k)


def read_sdf():
    b = open(os.path.join(SIM, "rock_sdf.bin"), "rb").read()
    nx, ny, nz = struct.unpack_from("<3I", b, 4)
    ox, oy, oz, cell = struct.unpack_from("<4f", b, 16)
    d = np.frombuffer(b, "<f2", nx * ny * nz, 32).astype(float).reshape(nz, ny, nx)

    def q(x, y, z):
        ix = int(np.clip(round((x - ox) / cell), 0, nx - 1))
        iy = int(np.clip(round((y - oy) / cell), 0, ny - 1))
        iz = int(np.clip(round((z - oz) / cell), 0, nz - 1))
        return float(d[iz, iy, ix])
    return q


def read_png(path):
    data = open(path, "rb").read()
    pos, chunks = 8, []
    w = h = depth = ctype = None
    while pos < len(data):
        ln, typ = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif typ == b"IDAT":
            chunks.append(body)
        pos += 12 + ln
    raw = zlib.decompress(b"".join(chunks))
    ch = {2: 3, 6: 4}[ctype]
    bpp = ch * depth // 8
    stride = w * bpp
    img = np.zeros((h, stride), np.uint8)
    prev = np.zeros(stride, np.int32)
    i = 0
    for y in range(h):
        f = raw[i]
        line = np.frombuffer(raw[i + 1:i + 1 + stride], np.uint8).astype(np.int32)
        i += 1 + stride
        out = np.zeros(stride, np.int32)
        for x in range(stride):
            a = out[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            v = line[x]
            if f == 1:
                v += a
            elif f == 2:
                v += b
            elif f == 3:
                v += (a + b) // 2
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                v += a if pa <= pb and pa <= pc else (b if pb <= pc else c)
            out[x] = v & 255
        img[y] = out
        prev = out
    if depth == 16:
        return img.reshape(h, w, ch * 2)[..., ::2] / 255.0
    return img.reshape(h, w, ch) / 255.0


def project(p, cam, tgt, fov_deg, w, h):
    cam, tgt, p = (np.asarray(v, float) for v in (cam, tgt, p))
    f = tgt - cam
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1.0, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    d = p - cam
    x, y, z = d @ r, d @ u, d @ f
    t = math.tan(math.radians(fov_deg) / 2)
    return w / 2 + (x / z) / t * (h / 2), h / 2 - (y / z) / t * (h / 2)


def main():
    errors = []
    sdf = read_sdf()
    anchors = json.load(open(os.path.join(SIM, "anchors.json")))["anchors"]
    far = []
    for a in anchors:
        if "pos" not in a or a["type"] in ("HOST", "HOVER", "FEED", "FLOW", "STATION", "CAVE", "SLEEP", "ZONE"):
            continue
        x, y, z = a["pos"]
        d_rock = abs(sdf(x, y, z))
        d_sand = abs(y - sand_height(x, z, sdf(x, 4.0, z)))
        if min(d_rock, d_sand) > 2.0:
            far.append((a["name"], round(d_rock, 1), round(d_sand, 1)))
    print(f"anchors checked against rock/sand: {len(anchors)}; farther than 2 cm: {far or 'none'}")
    if far:
        errors.append(f"{len(far)} anchors farther than 2 cm from a surface")
    img_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs", "preview", "scene.png")
    if os.path.exists(img_path):
        img = read_png(img_path)
        h, w = img.shape[:2]
        lum = 0.2126 * img[..., 0] + 0.7152 * img[..., 1] + 0.0722 * img[..., 2]
        cam, tgt, fov = (61, 26, TANK[2] + 150), (61, 21, TANK[2] / 2), 32   # scene_preview.VIEWS["front"]
        for name, p in (("CAVE_BIG", (34, 12, 2)), ("central arch", (58, 14, 2)), ("right recess", (86, 13, 2))):
            px, py = project(p, cam, tgt, fov, w, h)
            ix, iy = int(px), int(py)
            patch = lum[max(iy - 3, 0):iy + 4, max(ix - 3, 0):ix + 4]
            v = float(np.median(patch))
            ok = v < 0.08
            print(f"opening {name}: pixel ({ix}, {iy}) luminance {v * 100:.1f} % {'OK' if ok else 'TOO BRIGHT'}")
            if not ok:
                errors.append(f"{name} opening not dark ({v * 100:.1f} %)")
    print("OK" if not errors else "FAIL: " + "; ".join(errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
