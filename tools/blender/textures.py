"""Shared environment textures (ASSETS.md §6): caustics, water normal maps, iridescence LUT, room env, sand.
Pure numpy (runs inside or outside Blender)."""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import paintlib as P  # noqa: E402

S = P.smoothstep


def caustic(size=512, seed=7):
    """Tileable caustic network: Voronoi edge (F2-F1) intensity, 3 octaves, gamma 2.2. Grayscale."""
    acc = np.zeros((size, size), np.float32)
    for o, (n, wgt, width) in enumerate(((14, 0.55, 0.05), (28, 0.3, 0.045), (56, 0.15, 0.04))):
        f1, f2, _ = P.voronoi(size, size, n, seed + o)
        cell = size / math.sqrt(n)
        e = (f2 - f1) / cell
        acc += wgt * np.exp(-e / width)
    acc = acc / acc.max()
    acc = np.clip(acc, 0, 1) ** 2.2
    acc = acc / acc.max()
    return acc


def height_to_normal(hm, strength):
    gy, gx = np.gradient(hm)
    # wrap-aware gradients
    gx = (np.roll(hm, -1, 1) - np.roll(hm, 1, 1)) * 0.5
    gy = (np.roll(hm, -1, 0) - np.roll(hm, 1, 0)) * 0.5
    n = np.stack([-gx * strength, gy * strength, np.ones_like(hm)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    return n * 0.5 + 0.5


def water_normal(size=512, seed=11, cells=4, octaves=5, strength=6.0):
    hm = P.fbm(size, size, cells, octaves, seed, gain=0.55)
    # sharpen crests a bit (wave-like)
    hm = 1 - np.abs(hm - 0.5) * 2
    hm = hm ** 1.5
    return height_to_normal(hm * size / 64.0, strength / (size / 64.0))


def irid_lut(n=64):
    """thin-film-like ramp over NdotV (0 grazing -> 1 facing): violet -> blue -> cyan -> faint green."""
    stops = [(0.0, "#7A4DFF"), (0.25, "#3E6BFF"), (0.5, "#2FB8FF"), (0.75, "#4FE3D0"), (1.0, "#204060")]
    x = np.linspace(0, 1, n)
    out = np.zeros((1, n, 3), np.float32)
    for i in range(3):
        out[0, :, i] = np.interp(x, [s[0] for s in stops], [C.hex2rgb(s[1])[i] for s in stops])
    return out


def room_env(w=256, h=128):
    """Equirect 'room' for glass reflections: ceiling light, pinkish-lavender walls (photo), warm lamp spot,
    dark stand/floor. v=0 top (zenith)."""
    cv = P.Canvas(w, h)
    lat = 1 - cv.v  # 0 = zenith (top row) -> 1 = nadir
    cv.rgb[:] = 0
    ceiling = C.hex2rgb("#D8D0D8")
    wall = C.hex2rgb("#B8A8C8")
    floor = C.hex2rgb("#2A2018")
    t1 = S(0.25, 0.45, lat)[..., None]
    t2 = S(0.55, 0.62, lat)[..., None]
    cv.rgb = ceiling * (1 - t1) + wall * t1
    cv.rgb = cv.rgb * (1 - t2) + floor * t2
    # two light fixtures (AI Primes) seen from inside: bright blue-white blobs near the zenith
    for x in (0.25, 0.75):
        d = np.sqrt(((cv.u - x) * 2) ** 2 + ((lat - 0.12) * 1.0) ** 2)
        cv.blend(np.exp(-(d / 0.05) ** 2), "#E8F0FF", 1.0)
    # warm lamp / window on one side
    d = np.sqrt(((cv.u - 0.92) * 2) ** 2 + ((lat - 0.42) * 1.5) ** 2)
    cv.blend(np.exp(-(d / 0.08) ** 2), "#FFD8A0", 0.8)
    n = P.fbm(h, w, 8, 3, 3)
    cv.multiply(0.92 + 0.16 * n)
    return cv.rgb


def sand(size=1024, seed=21):
    """Tileable aragonite sand: grains + darker specks + detritus flecks."""
    h = w = size
    rs = np.random.default_rng(seed)
    base = P.fbm(h, w, 16, 3, seed)
    grain = rs.random((h, w)).astype(np.float32)
    grain = P.blur(grain, 0.8)
    grain = (grain - grain.min()) / (grain.max() - grain.min())
    # grain cells (each ~3 px = 0.9 mm at 30 cm per tile)
    f1, f2, ids = P.voronoi(h // 4, w // 4, 2600, seed + 1)
    cellv = rs.random(2600).astype(np.float32)[ids]
    cellv = P.blur(np.kron(cellv, np.ones((4, 4), np.float32)), 1.3)
    edge = P.blur(np.kron(S(0.0, 1.2, f2 - f1), np.ones((4, 4), np.float32)), 1.3)
    c1 = C.hex2rgb("#EDE7D9")  # docs/specs/tank_design.md §1.6
    c2 = C.hex2rgb("#D5CBB4")
    t = np.clip(0.35 * base + 0.45 * cellv + 0.2 * grain, 0, 1)[..., None]
    rgb = c1 * (1 - t) + c2 * t
    rgb *= (0.82 + 0.18 * edge)[..., None]
    specks = (rs.random((h, w)) > 0.996).astype(np.float32)
    specks = np.clip(P.blur(specks, 1.0) * 6, 0, 1)  # ~2 % dark specks
    rgb = rgb * (1 - specks[..., None]) + C.hex2rgb("#9A8E78") * specks[..., None]
    # coral rubble pebbles 3-8 mm (~3.4 px / mm), ~0.6 % coverage, tile-wrapped
    yy, xx = np.mgrid[0:h, 0:w]
    peb = np.zeros((h, w), np.float32)
    for _ in range(26):
        cx, cy, r = rs.uniform(0, w), rs.uniform(0, h), rs.uniform(5, 13)
        dx = (xx - cx + w / 2) % w - w / 2
        dy = (yy - cy + h / 2) % h - h / 2
        e = np.sqrt((dx / r) ** 2 + (dy / (r * rs.uniform(0.6, 1.0))) ** 2)
        peb = np.maximum(peb, 1 - S(0.8, 1.0, e))
    rgb = rgb * (1 - peb[..., None]) + C.hex2rgb("#F2EEE4") * (0.9 + 0.1 * grain[..., None]) * peb[..., None]
    det = S(0.82, 0.84, P.fbm(h, w, 90, 2, seed + 5))
    rgb = rgb * (1 - 0.6 * det[..., None]) + C.hex2rgb("#7A6448") * 0.6 * det[..., None]
    # height for the normal map: grain cells + sharp cell edges + fine grain, over soft 2 cm ripples / hoof-prints
    ripple = P.fbm(h, w, 12, 2, seed + 9)
    hgt = 0.5 * cellv + 0.5 * edge + 0.2 * grain + 0.6 * ripple
    return rgb, hgt


def quantize(rgb, step=4, seed=0):
    """Reduce PNG entropy: round 8-bit values to multiples of `step` with tiny ordered dither."""
    a = np.clip(rgb, 0, 1) * 255.0
    rs = np.random.default_rng(seed)
    a = np.round(a / step + (rs.random(a.shape[:2])[..., None] - 0.5) * 0.5) * step
    return np.clip(a, 0, 255).astype(np.uint8)


def build_all(verbose=True):
    out = {}
    out["caustic_512.png"] = C.write_png(os.path.join(C.TEXTURES, "caustic_512.png"), caustic())
    out["water_n1.png"] = C.write_png(os.path.join(C.TEXTURES, "water_n1.png"), water_normal(512, 11, 4))
    out["water_n2.png"] = C.write_png(os.path.join(C.TEXTURES, "water_n2.png"), water_normal(512, 23, 7, 4, 4.0))
    out["irid_lut.png"] = C.write_png(os.path.join(C.TEXTURES, "irid_lut.png"), irid_lut())
    out["room_env.png"] = C.write_png(os.path.join(C.TEXTURES, "room_env.png"), room_env())
    rgb, hgt = sand()
    out["sand_albedo.png"] = C.write_png(os.path.join(C.BUILD, "sand_albedo.png"), quantize(rgb, 3))
    # full 1024 (was 512): one texel = 0.3 mm, so single grains survive; strength 4 (was 2.5)
    n = height_to_normal(P.blur(hgt, 0.6), 4.0)
    out["sand_normal.png"] = C.write_png(os.path.join(C.TEXTURES, "sand_normal.png"), quantize(n, 4))
    if verbose:
        for k, v in out.items():
            print(f"[tex] {k}: {os.path.getsize(v)} bytes")
    return out


if __name__ == "__main__":
    build_all()
