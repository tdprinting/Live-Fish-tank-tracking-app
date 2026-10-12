"""Procedural texture painting in numpy (no Pillow available in the bpy venv).

Canvas convention: image row 0 = top. For fish bodies the canvas is the unrolled
flank: x = body u (0 snout, left edge -> 1 tail, right edge), y = flank height h
(1 = dorsal midline at the top row, 0.5 = lateral line, 0 = ventral midline at
the bottom row). Both flanks share (mirror) the same texture.
"""
import numpy as np

from common import hex2rgb, write_png


def rng(seed):
    return np.random.default_rng(seed)


class Canvas:
    def __init__(self, w, h, color="#808080", alpha=1.0):
        self.w, self.h = w, h
        self.rgb = np.empty((h, w, 3), np.float32)
        self.rgb[:] = hex2rgb(color) if isinstance(color, str) else color
        self.a = np.full((h, w), alpha, np.float32)
        # normalized coordinates of pixel centers
        self.u = ((np.arange(w) + 0.5) / w)[None, :].repeat(h, 0).astype(np.float32)
        self.v = (1.0 - (np.arange(h) + 0.5) / h)[:, None].repeat(w, 1).astype(np.float32)

    def blend(self, mask, color, opacity=1.0):
        m = np.clip(mask * opacity, 0, 1)[..., None]
        c = hex2rgb(color) if isinstance(color, str) else np.asarray(color, np.float32)
        if c.ndim == 3:
            self.rgb = self.rgb * (1 - m) + c * m
        else:
            self.rgb = self.rgb * (1 - m) + c[None, None, :] * m

    def multiply(self, factor):
        self.rgb *= np.asarray(factor, np.float32)[..., None] if np.ndim(factor) == 2 else factor

    def vgrad(self, c_bottom, c_top, v0=0.0, v1=1.0, gamma=1.0):
        t = np.clip((self.v - v0) / max(v1 - v0, 1e-6), 0, 1) ** gamma
        cb, ct = hex2rgb(c_bottom), hex2rgb(c_top)
        self.rgb = cb[None, None] * (1 - t[..., None]) + ct[None, None] * t[..., None]

    def rgba(self):
        return np.concatenate([self.rgb, self.a[..., None]], axis=2)

    def save(self, path, alpha=False):
        arr = self.rgba() if alpha else self.rgb
        return write_png(path, arr)

    def downsample(self, f=2):
        c = Canvas(self.w // f, self.h // f)
        c.rgb = self.rgb.reshape(self.h // f, f, self.w // f, f, 3).mean(axis=(1, 3))
        c.a = self.a.reshape(self.h // f, f, self.w // f, f).mean(axis=(1, 3))
        return c


# ----------------------------------------------------------------------------
# Masks
# ----------------------------------------------------------------------------
def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0 + 1e-9), 0, 1)
    return t * t * (3 - 2 * t)


def band(x, x0, x1, soft):
    """1 inside [x0, x1] with soft edges of width `soft`."""
    return smoothstep(x0 - soft, x0 + soft, x) * (1 - smoothstep(x1 - soft, x1 + soft, x))


def ellipse(cv, cu, cvv, ru, rv, soft=0.15):
    d = np.sqrt(((cv.u - cu) / ru) ** 2 + ((cv.v - cvv) / rv) ** 2)
    return 1 - smoothstep(1 - soft, 1 + soft, d)


def disc_px(cv, cu, cvv, r_u, aspect, soft_px=1.2):
    """Circle with radius r_u (in u units) corrected for the canvas aspect (v units = aspect*u)."""
    du = (cv.u - cu) * cv.w
    dv = (cv.v - cvv) * cv.h * aspect
    d = np.sqrt(du * du + dv * dv) - r_u * cv.w
    return 1 - smoothstep(-soft_px, soft_px, d)


def poisson(rs, n, rmin, box=(0, 1, 0, 1), tries=6000, existing=None):
    pts = [] if existing is None else list(existing)
    x0, x1, y0, y1 = box
    t = 0
    while len(pts) < n and t < tries:
        t += 1
        p = (rs.uniform(x0, x1), rs.uniform(y0, y1))
        if all((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 >= rmin * rmin for q in pts):
            pts.append(p)
    return pts


# ----------------------------------------------------------------------------
# Noise
# ----------------------------------------------------------------------------
def value_noise(h, w, cells_x, cells_y, seed, tile=True):
    """Tileable smooth value noise in [0,1] (bicubic-ish via smoothstep interpolation)."""
    r = rng(seed)
    g = r.random((cells_y + 1, cells_x + 1)).astype(np.float32)
    if tile:
        g[-1, :] = g[0, :]
        g[:, -1] = g[:, 0]
    y = (np.arange(h) + 0.5) / h * cells_y
    x = (np.arange(w) + 0.5) / w * cells_x
    yi = np.floor(y).astype(int)
    xi = np.floor(x).astype(int)
    fy = y - yi
    fx = x - xi
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[yi][:, xi]
    b = g[yi][:, xi + 1]
    c = g[yi + 1][:, xi]
    d = g[yi + 1][:, xi + 1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return (top + (bot - top) * fy[:, None]).astype(np.float32)


def fbm(h, w, base_cells, octaves, seed, gain=0.5, aspect=1.0):
    acc = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        cx = int(base_cells * 2 ** o)
        cy = max(1, int(round(cx * aspect * h / w)))
        acc += amp * value_noise(h, w, cx, cy, seed + 17 * o)
        tot += amp
        amp *= gain
    return acc / tot


def blur(img, sigma_px, wrap=True):
    """Gaussian blur via FFT (wraps around). img: HxW or HxWxC."""
    if sigma_px <= 0:
        return img
    h, w = img.shape[:2]
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    k = np.exp(-2 * (np.pi ** 2) * (sigma_px ** 2) * (fx * fx + fy * fy))
    if img.ndim == 2:
        return np.real(np.fft.ifft2(np.fft.fft2(img) * k)).astype(np.float32)
    return np.stack([blur(img[..., i], sigma_px) for i in range(img.shape[2])], axis=2)


def voronoi(h, w, n, seed, tile=True, aspect=1.0):
    """Returns (F1, F2, id) distance fields in pixel units for n random sites (tileable)."""
    r = rng(seed)
    pts = r.random((n, 2)) * [w, h]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    f1 = np.full((h, w), 1e9, np.float32)
    f2 = np.full((h, w), 1e9, np.float32)
    ids = np.zeros((h, w), np.int32)
    offs = [(0, 0)] if not tile else [(ox, oy) for ox in (-w, 0, w) for oy in (-h, 0, h)]
    for i, (px, py) in enumerate(pts):
        for ox, oy in offs:
            d = np.sqrt((xx - px - ox) ** 2 + ((yy - py - oy) * aspect) ** 2)
            closer = d < f1
            f2 = np.where(closer, f1, np.minimum(f2, d))
            ids = np.where(closer, i, ids)
            f1 = np.where(closer, d, f1)
    return f1, f2, ids


def hex_scales(cv, size_px, seed=0):
    """Scale pattern: overlapping arcs on a hex lattice, 0..1 (1 = scale edge)."""
    h, w = cv.h, cv.w
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    sx = size_px
    sy = size_px * 0.866
    row = np.floor(yy / sy)
    xo = xx + (row % 2) * sx * 0.5
    cx = (np.floor(xo / sx) + 0.5) * sx
    cy = (row + 0.5) * sy
    # distance to the cell's center, offset toward the tail so the edge reads as a scale overlap
    d = np.sqrt(((xo - cx + 0.25 * sx) / sx) ** 2 + ((yy - cy) / sy) ** 2)
    edge = smoothstep(0.42, 0.55, d)
    jitter = value_noise(h, w, max(2, w // int(sx * 3)), max(2, h // int(sx * 3)), seed)
    return edge * (0.7 + 0.3 * jitter)


def hex_net(cv, size_px, width_px=1.2):
    """A visible net of scale outlines (cardinal's black band)."""
    h, w = cv.h, cv.w
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    sx = size_px
    sy = size_px * 0.866
    row = np.floor(yy / sy)
    xo = xx + (row % 2) * sx * 0.5
    cx = (np.floor(xo / sx) + 0.5) * sx
    cy = (row + 0.5) * sy
    dx = np.abs(xo - cx)
    dy = np.abs(yy - cy)
    # diamond-ish (rhombic) cells like overlapping scales
    d = dx / (sx * 0.5) + dy / (sy * 0.9)
    return smoothstep(1.0 - width_px / sx * 2.5, 1.0, d)
