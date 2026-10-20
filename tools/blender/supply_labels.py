"""Printed packaging for the supply shelf (docs/ASSETS.md §5.1): one label atlas painted with PIL, no bpy needed.

Every prop on supplies.py's shelf is a look-alike of a real reef-keeping product (Ocean Nutrition Formula Two Flakes,
Hikari Bio-Pure Mysis, Red Sea Reef Foundation A/B/C, Seachem Matrix Carbon, API Reef Master, ...). The look is drawn
here from scratch: the brand's colour scheme, layout and typography feel, product names, fine print (guaranteed
analysis, directions, net weight), barcodes and little painted "photos" of fish and coral. No logo or artwork is
copied from the real packs; logos are approximated with plain shapes and system / app fonts.

  python3 tools/blender/supply_labels.py [out.png]     # paints the atlas alone (quick look, no Blender)

Each decal has a physical size (cm, as it sits on the prop) and is painted at PX_PER_CM, 2x supersampled, then packed
into one ATLAS x ATLAS image with a little edge-replicated padding so mipmaps do not bleed between decals. supplies.py
asks uv(name, u, v) for the atlas coordinates of a point on a decal (u across, v up).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
RES_FONT = os.path.join(REPO, "app", "src", "main", "res", "font")

ATLAS = 2048
PX_PER_CM = 44
SS = 2          # supersampling while painting
PAD = 6         # edge-replicated padding around each decal in the atlas (px)

# ---- fonts: system faces for the brand feel, the app's own fonts as the fallback ----
_SYS = "/usr/share/fonts"
FONTS = {
    "outfit": [os.path.join(RES_FONT, "outfit_semibold.ttf")],
    "inter": [os.path.join(RES_FONT, "inter_semibold.ttf")],
    "inter_reg": [os.path.join(RES_FONT, "inter_regular.ttf")],
    "inter_med": [os.path.join(RES_FONT, "inter_medium.ttf")],
    "bold": [_SYS + "/opentype/inter/Inter-Bold.otf", os.path.join(RES_FONT, "inter_semibold.ttf")],
    "black": [_SYS + "/opentype/inter/InterDisplay-Black.otf", _SYS + "/opentype/inter/Inter-Black.otf",
              os.path.join(RES_FONT, "outfit_semibold.ttf")],
    "black_it": [_SYS + "/opentype/inter/InterDisplay-BlackItalic.otf", _SYS + "/opentype/inter/Inter-BlackItalic.otf",
                 os.path.join(RES_FONT, "outfit_semibold.ttf")],
    "bold_it": [_SYS + "/opentype/inter/Inter-BoldItalic.otf", _SYS + "/truetype/liberation/LiberationSans-BoldItalic.ttf",
                os.path.join(RES_FONT, "inter_semibold.ttf")],
    "xbold": [_SYS + "/opentype/inter/InterDisplay-ExtraBold.otf", os.path.join(RES_FONT, "outfit_semibold.ttf")],
    "sans_b": [_SYS + "/truetype/liberation/LiberationSans-Bold.ttf", os.path.join(RES_FONT, "inter_semibold.ttf")],
    "sans_bi": [_SYS + "/truetype/liberation/LiberationSans-BoldItalic.ttf", os.path.join(RES_FONT, "inter_semibold.ttf")],
    "serif_b": [_SYS + "/truetype/liberation/LiberationSerif-Bold.ttf", _SYS + "/truetype/dejavu/DejaVuSerif-Bold.ttf",
                os.path.join(RES_FONT, "outfit_semibold.ttf")],
    "serif_bi": [_SYS + "/truetype/liberation/LiberationSerif-BoldItalic.ttf", _SYS + "/truetype/crosextra/Caladea-BoldItalic.ttf",
                 os.path.join(RES_FONT, "outfit_semibold.ttf")],
    "serif_i": [_SYS + "/truetype/liberation/LiberationSerif-Italic.ttf", os.path.join(RES_FONT, "inter_regular.ttf")],
    "cal_bi": [_SYS + "/truetype/crosextra/Caladea-BoldItalic.ttf", _SYS + "/truetype/liberation/LiberationSerif-BoldItalic.ttf",
               os.path.join(RES_FONT, "outfit_semibold.ttf")],
    "fine": [_SYS + "/opentype/inter/Inter-Medium.otf", os.path.join(RES_FONT, "inter_medium.ttf")],
}
_font_cache = {}


def font(style, px):
    key = (style, int(px))
    if key not in _font_cache:
        f = None
        for path in FONTS[style]:
            if os.path.exists(path):
                try:
                    f = ImageFont.truetype(path, max(int(px), 4))
                    break
                except OSError:
                    pass
        _font_cache[key] = f or ImageFont.load_default()
    return _font_cache[key]


def rgb(h):
    if isinstance(h, tuple):
        return h
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


# ---- a decal canvas: fractional coordinates (u across, v DOWN from the top), sizes in final pixels ----
class Canvas:
    def __init__(self, w, h, bg="#FFFFFF"):
        self.fw, self.fh = w, h
        self.w, self.h = w * SS, h * SS
        self.img = Image.new("RGB", (self.w, self.h), rgb(bg))
        self.d = ImageDraw.Draw(self.img)

    def X(self, u):
        return u * self.w

    def Y(self, v):
        return v * self.h

    def box(self, u0, v0, u1, v1):
        return (self.X(u0), self.Y(v0), self.X(u1), self.Y(v1))

    def rect(self, u0, v0, u1, v1, fill=None, r=0, outline=None, width=0):
        b = self.box(u0, v0, u1, v1)
        if r:
            self.d.rounded_rectangle(b, r * SS, fill=rgb(fill) if fill else None, outline=rgb(outline) if outline else None,
                                     width=int(width * SS))
        else:
            self.d.rectangle(b, fill=rgb(fill) if fill else None, outline=rgb(outline) if outline else None, width=int(width * SS))

    def ellipse(self, u0, v0, u1, v1, fill=None, outline=None, width=0):
        self.d.ellipse(self.box(u0, v0, u1, v1), fill=rgb(fill) if fill else None, outline=rgb(outline) if outline else None,
                       width=int(width * SS))

    def circle(self, u, v, rpx, fill=None, outline=None, width=0):
        x, y, r = self.X(u), self.Y(v), rpx * SS
        self.d.ellipse((x - r, y - r, x + r, y + r), fill=rgb(fill) if fill else None,
                       outline=rgb(outline) if outline else None, width=int(width * SS))

    def poly(self, pts, fill=None, outline=None, width=0):
        P = [(self.X(u), self.Y(v)) for u, v in pts]
        self.d.polygon(P, fill=rgb(fill) if fill else None, outline=rgb(outline) if outline else None,
                       width=int(width * SS) if outline else 1)

    def line(self, pts, fill, width):
        self.d.line([(self.X(u), self.Y(v)) for u, v in pts], fill=rgb(fill), width=max(1, int(width * SS)), joint="curve")

    def grad(self, u0, v0, u1, v1, c0, c1, horizontal=False):
        x0, y0, x1, y1 = [int(round(t)) for t in self.box(u0, v0, u1, v1)]
        n = (x1 - x0) if horizontal else (y1 - y0)
        for k in range(max(n, 1)):
            c = mix(c0, c1, k / max(n - 1, 1))
            if horizontal:
                self.d.line((x0 + k, y0, x0 + k, y1), fill=c)
            else:
                self.d.line((x0, y0 + k, x1, y0 + k), fill=c)

    def paste(self, layer, u, v, anchor="mm"):
        ax = {"l": 0.0, "m": 0.5, "r": 1.0}[anchor[0]]
        ay = {"t": 0.0, "m": 0.5, "b": 1.0}[anchor[1]]
        x, y = int(self.X(u) - ax * layer.width), int(self.Y(v) - ay * layer.height)
        self.img.paste(layer, (x, y), layer)

    def text(self, s, style, px, u, v, fill="#000000", anchor="mm", stroke=0, stroke_fill="#000000", sx=1.0, rot=0.0,
             max_w=None, max_h=None, spacing=0, align="center", shadow=None):
        """Text in its own layer: condensed by sx (and further to fit max_w, a fraction of the width), rotated rot
        degrees, optional outline (stroke px) and a drop shadow (dx, dy, colour)."""
        f = font(style, px * SS)
        if px < 10:
            spacing = max(spacing, 0)  # tight leading collapses small multi-line text
        st = int(round(stroke * SS))
        probe = ImageDraw.Draw(Image.new("L", (1, 1)))
        bb = probe.multiline_textbbox((0, 0), s, font=f, spacing=spacing * SS, align=align, stroke_width=st)
        tw, th = int(math.ceil(bb[2] - bb[0])), int(math.ceil(bb[3] - bb[1]))
        pad = 2 + st
        layer = Image.new("RGBA", (tw + 2 * pad, th + 2 * pad), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        if shadow:
            dx, dy, sc = shadow
            ld.multiline_text((pad - bb[0] + dx * SS, pad - bb[1] + dy * SS), s, font=f, fill=rgb(sc) + (255,),
                              spacing=spacing * SS, align=align, stroke_width=st, stroke_fill=rgb(sc) + (255,))
        ld.multiline_text((pad - bb[0], pad - bb[1]), s, font=f, fill=rgb(fill) + (255,), spacing=spacing * SS, align=align,
                          stroke_width=st, stroke_fill=rgb(stroke_fill) + (255,))
        w = layer.width * sx
        h = layer.height
        if abs(abs(rot) - 90) < 1e-3:
            # upright text turned on its side: the limits apply to the turned extents
            max_w, max_h = (None if max_h is None else max_h * self.h / self.w), (None if max_w is None else max_w * self.w / self.h)
        if max_w is not None and w > max_w * self.w:
            sx *= max_w * self.w / w
            w = layer.width * sx
        scale_y = 1.0
        if max_h is not None and h > max_h * self.h:
            scale_y = max_h * self.h / h
            sx *= scale_y
        layer = layer.resize((max(1, int(layer.width * sx)), max(1, int(layer.height * scale_y))), Image.LANCZOS)
        if rot:
            layer = layer.rotate(rot, expand=True, resample=Image.BICUBIC)
        self.paste(layer, u, v, anchor)
        return layer.width / self.w, layer.height / self.h

    def fine(self, lines, u0, v0, u1, px=7, fill="#333333", style="fine", gap=1.25, align="l"):
        """Fine print: lines of small text (each squeezed into the column u0..u1)."""
        v = v0
        for s in lines:
            if s:
                self.text(s, style, px, u0 if align == "l" else (u0 + u1) / 2, v, fill, anchor="lt" if align == "l" else "mt",
                          max_w=u1 - u0)
            v += px * gap / self.fh
        return v

    def blur_region(self, u0, v0, u1, v1, r):
        b = tuple(int(t) for t in self.box(u0, v0, u1, v1))
        self.img.paste(self.img.crop(b).filter(ImageFilter.GaussianBlur(r * SS)), b[:2])

    def done(self):
        return self.img.resize((self.fw, self.fh), Image.LANCZOS)


# ---- painted bits: fish, coral "photos", food, frozen cubes, barcodes, logos ----
def _layer(wpx, hpx):
    L = Image.new("RGBA", (int(wpx * SS), int(hpx * SS)), (0, 0, 0, 0))
    return L, ImageDraw.Draw(L)


def fish(c, kind, u, v, L, flip=False, rot=0.0):
    """A little painted fish L px long, centred at (u, v)."""
    lay, d = _layer(L * 1.3, L * 1.3)
    s = L * SS
    cx, cy = lay.width / 2, lay.height / 2

    def P(x, y):
        return (cx + x * s, cy + y * s)

    def body(col, hh=0.26, nose=0.5, tail=-0.5):
        d.ellipse((*P(-0.36, -hh), *P(0.42, hh)), fill=col)
        d.polygon([P(-0.3, 0), P(-0.56, -0.2), P(-0.5, 0), P(-0.56, 0.2)], fill=col)

    if kind == "yellow_tang":
        y = rgb("#FFD21E")
        d.polygon([P(-0.18, -0.3), P(0.15, -0.42), P(0.3, -0.18), P(-0.3, -0.12)], fill=y)
        d.polygon([P(-0.18, 0.3), P(0.12, 0.4), P(0.3, 0.18), P(-0.3, 0.12)], fill=y)
        d.ellipse((*P(-0.32, -0.32), *P(0.36, 0.32)), fill=y)
        d.polygon([P(0.3, -0.12), P(0.5, 0.0), P(0.3, 0.1)], fill=y)
        d.polygon([P(-0.28, 0), P(-0.52, -0.2), P(-0.46, 0), P(-0.52, 0.2)], fill=rgb("#F2C200"))
        d.ellipse((*P(-0.27, -0.02), *P(-0.22, 0.03)), fill=(255, 255, 255))
        d.ellipse((*P(0.22, -0.12), *P(0.3, -0.04)), fill=(20, 20, 20))
    elif kind == "blue_tang":
        b, k, yy = rgb("#2453D8"), rgb("#101530"), rgb("#FFD200")
        d.ellipse((*P(-0.38, -0.25), *P(0.42, 0.25)), fill=b)
        d.polygon([P(-0.32, 0), P(-0.58, -0.22), P(-0.5, 0), P(-0.58, 0.22)], fill=yy)
        d.polygon([P(-0.28, -0.04), P(-0.1, -0.2), P(0.25, -0.18), P(0.2, -0.1), P(-0.05, -0.06), P(0.1, 0.06), P(-0.25, 0.05)], fill=k)
        d.ellipse((*P(0.26, -0.11), *P(0.34, -0.03)), fill=(15, 15, 15))
    elif kind == "clown":
        o, w, k = rgb("#FF7417"), (255, 255, 255), rgb("#1A1A1A")
        d.ellipse((*P(-0.42, -0.2), *P(0.45, 0.2)), fill=o)
        d.ellipse((*P(-0.62, -0.18), *P(-0.36, 0.18)), fill=o, outline=k, width=int(0.02 * s))
        d.ellipse((*P(-0.1, -0.34), *P(0.2, -0.12)), fill=o, outline=k, width=int(0.02 * s))
        for x0, x1 in ((0.2, 0.3), (-0.06, 0.06), (-0.34, -0.26)):
            d.rectangle((*P(x0, -0.2), *P(x1, 0.2)), fill=w, outline=k, width=int(0.015 * s))
        d.ellipse((*P(-0.42, -0.2), *P(0.45, 0.2)), outline=o, width=int(0.03 * s))
        d.ellipse((*P(0.31, -0.08), *P(0.38, -0.01)), fill=k)
    elif kind == "angel":
        # an emperor-ish angelfish: blue body with yellow stripes, yellow tail
        b, yy = rgb("#1E3FA8"), rgb("#FFC21A")
        d.polygon([P(-0.2, -0.3), P(0.2, -0.36), P(0.3, -0.15), P(-0.3, -0.1)], fill=b)
        d.polygon([P(-0.2, 0.3), P(0.2, 0.34), P(0.3, 0.15), P(-0.3, 0.1)], fill=b)
        d.ellipse((*P(-0.34, -0.3), *P(0.42, 0.3)), fill=b)
        for k in range(6):
            yv = -0.24 + 0.09 * k
            d.line([P(-0.3, yv + 0.04), P(0.3, yv - 0.04)], fill=yy, width=max(1, int(0.03 * s)))
        d.polygon([P(-0.3, 0), P(-0.55, -0.18), P(-0.55, 0.18)], fill=yy)
        d.rectangle((*P(0.22, -0.12), *P(0.28, 0.1)), fill=rgb("#101530"))
        d.ellipse((*P(0.3, -0.09), *P(0.37, -0.02)), fill=(240, 240, 240))
    elif kind == "idol":
        w, k, yy = rgb("#F4F1E6"), rgb("#141414"), rgb("#FFD23A")
        d.polygon([P(-0.05, -0.28), P(-0.35, -0.6), P(0.15, -0.3)], fill=w)
        d.ellipse((*P(-0.3, -0.32), *P(0.36, 0.32)), fill=w)
        d.polygon([P(0.3, -0.06), P(0.52, 0.02), P(0.3, 0.1)], fill=yy)
        d.polygon([P(0.05, -0.32), P(0.22, -0.28), P(0.18, 0.3), P(0.0, 0.32)], fill=k)
        d.polygon([P(-0.2, -0.28), P(-0.08, -0.31), P(-0.12, 0.3), P(-0.24, 0.26)], fill=k)
        d.rectangle((*P(-0.3, -0.12), *P(-0.2, 0.1)), fill=yy)
        d.polygon([P(-0.28, 0), P(-0.5, -0.16), P(-0.5, 0.16)], fill=k)
    elif kind == "seahorse":
        yy, dk = rgb("#F2B23A"), rgb("#B87418")
        pts = [P(0.05, -0.38), P(0.12, -0.2), P(0.08, 0.0), P(0.0, 0.18), P(0.06, 0.32), P(0.16, 0.36), P(0.18, 0.28)]
        d.line(pts, fill=yy, width=int(0.13 * s), joint="curve")
        d.line(pts[:4], fill=yy, width=int(0.2 * s), joint="curve")
        d.ellipse((*P(-0.02, -0.48), *P(0.14, -0.33)), fill=yy)
        d.polygon([P(0.1, -0.43), P(0.32, -0.4), P(0.32, -0.36), P(0.1, -0.36)], fill=yy)
        d.ellipse((*P(0.06, -0.44), *P(0.1, -0.4)), fill=(30, 20, 10))
        for k in range(6):
            yv = -0.3 + 0.08 * k
            d.line([P(-0.02, yv), P(0.16, yv + 0.02)], fill=dk, width=max(1, int(0.012 * s)))
        d.polygon([P(-0.06, -0.12), P(-0.2, -0.04), P(-0.06, 0.02)], fill=rgb("#FFD27A"))
    elif kind == "dotty":
        p, yy = rgb("#9A2BD6"), rgb("#FFD21E")
        d.ellipse((*P(-0.42, -0.16), *P(0.44, 0.16)), fill=p)
        d.rectangle((*P(-0.42, -0.16), *P(-0.05, 0.16)), fill=yy)
        d.ellipse((*P(-0.42, -0.16), *P(0.44, 0.16)), outline=p, width=1)
        d.polygon([P(-0.38, 0), P(-0.58, -0.15), P(-0.58, 0.15)], fill=yy)
        d.ellipse((*P(0.3, -0.07), *P(0.36, -0.01)), fill=(20, 20, 20))
    if flip:
        lay = lay.transpose(Image.FLIP_LEFT_RIGHT)
    if rot:
        lay = lay.rotate(rot, expand=True, resample=Image.BICUBIC)
    c.paste(lay, u, v)


def reef(c, u0, v0, u1, v1, style, seed=1, blur=1.0):
    """A soft painted 'photo' of reef life filling the box (styles: purple, acro, zoa, gorgonian, lagoon, anemone,
    plants, rock)."""
    rng = np.random.default_rng(seed)
    b = [int(t) for t in c.box(u0, v0, u1, v1)]
    W, H = b[2] - b[0], b[3] - b[1]
    im = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(im)
    top, bot = {"purple": ("#0B1A4A", "#2A0F3A"), "acro": ("#0A2350", "#0B1530"), "zoa": ("#1A1030", "#0A0A18"),
                "gorgonian": ("#0A0A14", "#121A30"), "lagoon": ("#4FB7E8", "#1E5FA8"), "anemone": ("#141414", "#2A2018"),
                "plants": ("#1D5E3A", "#0E2E22"), "rock": ("#8FB9D8", "#3E6E9A")}[style]
    for y in range(H):
        d.line((0, y, W, y), fill=mix(top, bot, y / max(H - 1, 1)))
    S = min(W, H)
    if style == "purple":
        for _ in range(int(60 * W * H / S ** 2) + 30):
            x, y = rng.uniform(0, W), rng.uniform(H * 0.15, H * 1.05)
            r = rng.uniform(0.05, 0.16) * S
            col = [rgb("#6A2290"), rgb("#9B33B0"), rgb("#C04FBF"), rgb("#4A1C78"), rgb("#E070D0")][rng.integers(5)]
            d.ellipse((x - r * 1.5, y - r * 0.7, x + r * 1.5, y + r * 0.7), fill=col)
            d.ellipse((x - r * 1.1, y - r * 0.6, x + r * 1.0, y - r * 0.2), fill=mix(col, "#FFC8F0", 0.35))
        for _ in range(int(40 * W / S)):
            x, y = rng.uniform(0, W), rng.uniform(H * 0.2, H)
            d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=rgb("#7FE0FF"))
    elif style == "acro":
        for _ in range(int(14 * W / S) + 6):
            x0, y0 = rng.uniform(0, W), H * rng.uniform(0.75, 1.1)
            for _k in range(rng.integers(4, 8)):
                a = rng.uniform(-2.4, -0.7)
                ln = rng.uniform(0.15, 0.45) * S
                x1, y1 = x0 + math.cos(a) * ln, y0 + math.sin(a) * ln
                col = [rgb("#7FD14A"), rgb("#B9E04A"), rgb("#4FB04F"), rgb("#E2E86A")][rng.integers(4)]
                d.line((x0, y0, x1, y1), fill=col, width=int(rng.uniform(0.03, 0.06) * S))
                d.ellipse((x1 - 0.03 * S, y1 - 0.03 * S, x1 + 0.03 * S, y1 + 0.03 * S), fill=rgb("#DDF7A0"))
                for _j in range(5):
                    t = rng.uniform(0.2, 0.9)
                    px, py = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                    d.ellipse((px - 2, py - 2, px + 2, py + 2), fill=rgb("#9AF0FF"))
    elif style == "zoa":
        for _ in range(int(90 * W * H / S ** 2) + 40):
            x, y = rng.uniform(0, W), rng.uniform(0, H)
            r = rng.uniform(0.04, 0.08) * S
            ring = [rgb("#FF8A1E"), rgb("#B044D8"), rgb("#FFB03A"), rgb("#E0408A")][rng.integers(4)]
            d.ellipse((x - r, y - r, x + r, y + r), fill=ring)
            d.ellipse((x - r * 0.5, y - r * 0.5, x + r * 0.5, y + r * 0.5), fill=[rgb("#3AE07A"), rgb("#2A2A60"), rgb("#7AF0FF")][rng.integers(3)])
    elif style == "gorgonian":
        def branch(x, y, a, ln, w, depth):
            if depth == 0 or ln < 4:
                return
            x1, y1 = x + math.cos(a) * ln, y + math.sin(a) * ln
            d.line((x, y, x1, y1), fill=rgb("#D8263A"), width=max(1, int(w)))
            for _k in range(int(ln / 6)):
                t = rng.uniform(0, 1)
                px, py = x + (x1 - x) * t + rng.uniform(-w, w), y + (y1 - y) * t + rng.uniform(-w, w)
                d.ellipse((px - 2.5, py - 2.5, px + 2.5, py + 2.5), fill=rgb("#FFF4F0"))
            branch(x1, y1, a + rng.uniform(0.2, 0.6), ln * 0.75, w * 0.75, depth - 1)
            branch(x1, y1, a - rng.uniform(0.2, 0.6), ln * 0.75, w * 0.75, depth - 1)
        for k in range(3):
            branch(W * (0.2 + 0.3 * k), H * 1.02, -math.pi / 2 + rng.uniform(-0.3, 0.3), S * 0.3, S * 0.05, 5)
    elif style == "lagoon":
        d.rectangle((0, int(H * 0.78), W, H), fill=rgb("#E8D9A8"))
        for _ in range(int(10 * W / S) + 4):
            x, y, r = rng.uniform(0, W), H * rng.uniform(0.6, 0.85), rng.uniform(0.08, 0.2) * S
            d.ellipse((x - r * 1.4, y - r, x + r * 1.4, y + r), fill=[rgb("#8A6E9E"), rgb("#B07A92"), rgb("#7A8E6A")][rng.integers(3)])
        for _ in range(int(20 * W / S)):
            x, y = rng.uniform(0, W), H * rng.uniform(0.0, 0.5)
            d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=rgb("#BFE9FF"))
    elif style == "anemone":
        cx, cy = W * 0.5, H * 0.62
        for k in range(46):
            a = 2 * math.pi * k / 46 + rng.uniform(-0.05, 0.05)
            ln = rng.uniform(0.32, 0.5) * S
            col = mix("#E8B070", "#FFE2B0", rng.uniform(0, 1))
            pts = [(cx + math.cos(a) * ln * t + math.sin(t * 3) * 3, cy + math.sin(a) * ln * t) for t in np.linspace(0.1, 1, 6)]
            d.line(pts, fill=col, width=max(2, int(0.035 * S)))
        d.ellipse((cx - 0.12 * S, cy - 0.1 * S, cx + 0.12 * S, cy + 0.1 * S), fill=rgb("#8A5A2A"))
        d.ellipse((cx - 0.05 * S, cy - 0.04 * S, cx + 0.05 * S, cy + 0.04 * S), fill=rgb("#3A2412"))
    elif style == "plants":
        for _ in range(int(20 * W / S) + 6):
            x0 = rng.uniform(0, W)
            pts = [(x0 + math.sin(t * 3 + x0) * 0.05 * S, H - t * rng.uniform(0.5, 1.0) * H) for t in np.linspace(0, 1, 6)]
            d.line(pts, fill=[rgb("#4FB04F"), rgb("#2E8A3E"), rgb("#8AD05A")][rng.integers(3)], width=int(0.05 * S))
        d.rectangle((0, int(H * 0.88), W, H), fill=rgb("#B89A6A"))
    elif style == "rock":
        d.rectangle((0, int(H * 0.82), W, H), fill=rgb("#E6D6AE"))
        for _ in range(int(8 * W / S) + 3):
            x, y, r = rng.uniform(0, W), H * rng.uniform(0.45, 0.8), rng.uniform(0.12, 0.26) * S
            col = [rgb("#B48AB8"), rgb("#9E7AA6"), rgb("#C9A4C0"), rgb("#8E6E98")][rng.integers(4)]
            d.ellipse((x - r * 1.5, y - r, x + r * 1.5, y + r), fill=col)
            for _k in range(9):
                hx, hy, hr = x + rng.uniform(-r, r), y + rng.uniform(-r * 0.6, r * 0.6), rng.uniform(0.08, 0.2) * r
                d.ellipse((hx - hr, hy - hr, hx + hr, hy + hr), fill=mix(col, "#40304A", 0.5))
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur * SS * 0.7))
    c.img.paste(im, b[:2])


def pellets(c, u, v, rpx, col, n=120, seed=3, size=2.2, ring=None):
    """A round sample window of pellets (rpx radius, final px)."""
    rng = np.random.default_rng(seed)
    if ring:
        c.circle(u, v, rpx + 1.5, fill=ring)
    c.circle(u, v, rpx, fill=mix(col, "#000000", 0.25))
    for _ in range(n):
        a, r = rng.uniform(0, 2 * math.pi), rpx * math.sqrt(rng.uniform(0, 1)) * 0.92
        x, y = u + math.cos(a) * r / c.fw, v + math.sin(a) * r / c.fh
        s = size * rng.uniform(0.8, 1.2)
        c.circle(x, y, s, fill=mix(col, "#FFFFFF", rng.uniform(0, 0.25)))


def flakes(c, u0, v0, u1, v1, cols, n=60, seed=4):
    rng = np.random.default_rng(seed)
    for _ in range(n):
        u, v = rng.uniform(u0, u1), rng.uniform(v0, v1)
        r = rng.uniform(2.5, 5.5)
        pts = [(u + r * math.cos(a) / c.fw * rng.uniform(0.6, 1.3), v + r * math.sin(a) / c.fh * rng.uniform(0.6, 1.3))
               for a in np.linspace(0, 2 * math.pi, 6)[:-1] + rng.uniform(0, 1)]
        c.poly(pts, fill=cols[rng.integers(len(cols))])


def cubes(c, u0, v0, u1, v1, rows, cols, col, frost="#EEF6FF", seed=5, well=None):
    """Frozen food cubes in their blister wells."""
    rng = np.random.default_rng(seed)
    cw, ch = (u1 - u0) / cols, (v1 - v0) / rows
    for i in range(rows):
        for j in range(cols):
            a0, b0 = u0 + j * cw + cw * 0.06, v0 + i * ch + ch * 0.08
            a1, b1 = u0 + (j + 1) * cw - cw * 0.06, v0 + (i + 1) * ch - ch * 0.08
            if well:
                c.rect(a0 - cw * 0.03, b0 - ch * 0.04, a1 + cw * 0.03, b1 + ch * 0.04, fill=well, r=3)
            c.rect(a0, b0, a1, b1, fill=col, r=4)
            c.rect(a0 + (a1 - a0) * 0.08, b0 + (b1 - b0) * 0.08, a1 - (a1 - a0) * 0.35, b0 + (b1 - b0) * 0.3,
                   fill=mix(col, frost, 0.55), r=3)
            for _ in range(10):
                x, y = rng.uniform(a0, a1), rng.uniform(b0, b1)
                c.circle(x, y, rng.uniform(0.6, 1.3), fill=mix(col, frost, rng.uniform(0.3, 0.8)))


def barcode(c, u, v, wpx, hpx, digits="0 42055 30920 2", ink="#111111", bg="#FFFFFF", rot=0, seed=7):
    """An EAN/UPC-like barcode on a white patch (top-left at u, v)."""
    rng = np.random.default_rng(seed)
    lay = Image.new("RGBA", (int((wpx + 6) * SS), int((hpx + 6) * SS)), rgb(bg) + (255,))
    d = ImageDraw.Draw(lay)
    x = 3 * SS
    xe = (wpx + 3) * SS
    bh = (hpx - 4) * SS
    while x < xe:
        w = rng.choice([1, 1, 2, 3]) * 0.55 * SS
        if rng.uniform() < 0.55:
            d.rectangle((x, 3 * SS, x + w, 3 * SS + bh), fill=rgb(ink))
        x += w + 0.55 * SS
    f = font("fine", 4 * SS)
    d.text((lay.width / 2, lay.height - 1 * SS), digits, font=f, fill=rgb(ink), anchor="mb")
    if rot:
        lay = lay.rotate(rot, expand=True)
    c.paste(lay, u, v, "lt")


def burst(c, u, v, rpx, fill, points=14, inner=0.78, outline=None):
    pts = []
    for k in range(points * 2):
        a = math.pi * k / points
        r = rpx if k % 2 == 0 else rpx * inner
        pts.append((u + r * math.cos(a) / c.fw, v + r * math.sin(a) / c.fh))
    c.poly(pts, fill=fill, outline=outline, width=1.2 if outline else 0)


def wave_swoosh(c, u0, u1, v, amp, col, width):
    pts = [(u, v + amp * math.sin((u - u0) / (u1 - u0) * 2 * math.pi)) for u in np.linspace(u0, u1, 24)]
    c.line(pts, col, width)


# ---- logos (approximations drawn from plain shapes; nothing is copied) ----
def logo_ocean_nutrition(c, u, v, hpx):
    w = hpx * 3.6 / c.fw
    h = hpx / c.fh
    c.rect(u, v, u + w, v + h, fill="#FFFFFF", r=2)
    s = h * 0.78
    su = s * c.fh / c.fw
    c.poly([(u + 0.04 * w, v + 0.11 * h), (u + 0.04 * w + su, v + 0.11 * h), (u + 0.04 * w, v + 0.11 * h + s)], fill="#1D4FA8")
    c.poly([(u + 0.04 * w + su, v + 0.11 * h), (u + 0.04 * w + su, v + 0.11 * h + s), (u + 0.04 * w, v + 0.11 * h + s)], fill="#D8262E")
    c.text("OCEAN\nNUTRITION", "sans_b", hpx * 0.4, u + 0.08 * w + su, v + h / 2, "#1D3E8A", anchor="lm", spacing=-1,
           max_w=w * 0.68, align="left")


def logo_hikari(c, u, v, hpx, bg="#E3262E"):
    w = hpx * 2.4 / c.fw
    h = hpx / c.fh
    c.rect(u, v, u + w, v + h, fill=bg, r=hpx * 0.18)
    c.rect(u + 0.08 * w, v + 0.78 * h, u + 0.92 * w, v + 0.86 * h, fill="#FFFFFF")
    c.text("Hikari", "black_it", hpx * 0.62, u + w / 2, v + h * 0.44, "#FFFFFF", max_w=w * 0.86)


def logo_red_sea(c, u, v, hpx, col="#E1251B", anchor_right=False):
    w = hpx * 3.0 / c.fw
    h = hpx / c.fh
    if anchor_right:
        u -= w
    c.poly([(u, v + h * 0.95), (u + w * 0.09, v + h * 0.1), (u + w * 0.2, v + h * 0.1), (u + w * 0.11, v + h * 0.95)], fill=col)
    c.text("Red Sea", "serif_b", hpx * 0.86, u + w * 0.22, v + h * 0.52, col, anchor="lm", sx=0.78, max_w=w * 0.78)


def logo_seachem(c, u, v, rpx):
    pts = [(u + rpx * math.cos(a) / c.fw, v + rpx * math.sin(a) / c.fh) for a in np.linspace(0, 2 * math.pi, 7)[:-1] + math.pi / 6]
    c.poly(pts, fill="#151515")
    c.text("Seachem", "bold_it", rpx * 0.5, u, v, "#FFFFFF", max_w=rpx * 1.6 / c.fw)


def logo_api(c, u, v, hpx, col="#FFFFFF"):
    c.text("API", "black_it", hpx, u, v, col, sx=1.1)
    w = hpx * 1.6 / c.fw
    c.line([(u - w * 0.55, v - hpx * 0.45 / c.fh), (u - w * 0.1, v - hpx * 0.75 / c.fh), (u + w * 0.5, v - hpx * 0.5 / c.fh)], col, hpx * 0.12)


# ---- the products ----
def gen_label_text(kind):
    return {
        "analysis": ["GUARANTEED ANALYSIS", "Crude Protein (min) 50.3%", "Crude Fat (min) 15.7%", "Crude Fiber (max) 2.8%",
                     "Moisture (max) 8.0%", "Ash (max) 10.5%", "Phosphorus (min) 1.6%"],
        "ingredients": ["INGREDIENTS: Whole Herring, Whole Krill,", "Whole Mysis, Spirulina, Kelp, Squid,", "Fish Meal, Wheat Gluten,",
                        "Brewers Dried Yeast, Lecithin,", "Astaxanthin, Vitamin A, D3, E, C", "(Stabilized), Zinc, Iodine."],
        "directions": ["DIRECTIONS: Feed 1 - 3 times daily.", "Feed only what your fish eat in", "2 - 3 minutes. Remove uneaten food.",
                       "Store in a cool, dry place.", "Keep lid tightly closed."],
        "frozen": ["Free From Harmful Parasites,", "Unwanted Bacteria & Foul Odor.", "Keep frozen. Do not refreeze.",
                   "Thaw cube in tank water first."],
        "dosing": ["DOSING: 1 mL per 25 gal (100 L)", "raises the level as listed.", "Test before dosing. Dose daily", "in high-flow areas. Do not",
                   "mix with other supplements.", "Keep out of reach of children."],
    }[kind]


def label_formula(c, name, line2, base, dark, light, cap_note, fishes, food, seed):
    """Ocean Nutrition style jar wrap (u 0..1 spans the visible 220 degree arc; front panel in the middle)."""
    c.grad(0, 0, 1, 1, light, base)
    for k in range(5):
        wave_swoosh(c, 0.0, 1.0, 0.25 + 0.15 * k, 0.03, mix(light, "#FFFFFF", 0.25), 3)
    # white top strip with the line name
    c.rect(0, 0, 1, 0.11, fill="#FFFFFF")
    c.text("FORMULA  " + cap_note + "  FOODS", "sans_b", 9, 0.5, 0.055, dark, sx=0.9, max_w=0.5)
    # side panels: analysis + ingredients (left), directions + barcode (right)
    c.rect(0.015, 0.16, 0.2, 0.93, fill="#FFFFFF", r=4)
    c.fine(gen_label_text("analysis"), 0.025, 0.19, 0.19, px=8, fill="#1A1A1A")
    c.fine(gen_label_text("ingredients"), 0.025, 0.52, 0.19, px=7, fill="#333333")
    c.rect(0.8, 0.16, 0.985, 0.93, fill="#FFFFFF", r=4)
    c.fine(gen_label_text("directions"), 0.81, 0.19, 0.975, px=7, fill="#1A1A1A")
    barcode(c, 0.83, 0.58, 50, 26, digits="0 30172 10422 6")
    c.text("MADE IN USA", "sans_b", 7, 0.89, 0.88, dark, max_w=0.15)
    # brand + fish
    logo_ocean_nutrition(c, 0.27, 0.15, 26)
    for kind, u, v, L, fl in fishes:
        fish(c, kind, u, v, L, flip=fl)
    # the slanted name band with its yellow rule
    lay_u, lay_v = 0.5, 0.66
    band = [(0.19, 0.7), (0.81, 0.44), (0.81, 0.68), (0.19, 0.94)]
    c.poly([(u, v - 0.025) for u, v in band[:2]] + [(u, v + 0.025) for u, v in band[2:]], fill="#FFE11A")
    c.poly(band, fill=dark)
    c.text(name, "bold_it", 50, lay_u, lay_v - 0.02, "#FFFFFF", rot=22, max_w=0.6, stroke=1.2, stroke_fill=mix(dark, "#000000", 0.4))
    c.text(line2, "sans_bi", 11, 0.56, 0.84, "#FFFFFF", rot=22, max_w=0.36)
    c.text("Net Wt 70 g\n(2.5 oz)", "sans_b", 9, 0.89, 0.8, "#1A1A1A", max_w=0.16)
    food(c)


def d_flake(c):
    def food(c):
        flakes(c, 0.66, 0.82, 0.78, 0.97, ["#A6C24A", "#7FA83A", "#C9D86A", "#D88A3A"], n=30)
    label_formula(c, "Formula Two Flakes", "Premium Food for all Tropical Fish", "#2F9A3A", "#1A6A28", "#8ED05A", "FLAKE",
                  [("yellow_tang", 0.62, 0.3, 80, False), ("blue_tang", 0.42, 0.42, 66, False)], food, 1)


def d_pellet(c):
    def food(c):
        pellets(c, 0.71, 0.86, 16, "#C2452A", n=90, size=1.6, ring="#FFFFFF")
    label_formula(c, "Formula One Pellets", "Small Pellet Size • Slow Sinking", "#2A63C8", "#163C8A", "#6FB2F0", "PELLET",
                  [("angel", 0.6, 0.32, 70, False), ("idol", 0.4, 0.38, 62, True)], food, 2)
    c.rect(0.22, 0.93, 0.6, 0.99, fill="#FFE11A", r=3)
    c.text("Small Pellet Size (+/- 1.2mm)", "sans_b", 9, 0.41, 0.96, "#163C8A", max_w=0.36)


def hikari_pouch(c, stripe, stripe_text, title, title_col, outline, sub, fishes, food, net, top_note=None):
    c.grad(0, 0, 1, 1, "#FFFFFF", "#EEF4F2")
    c.rect(0, 0, 0.17, 1, fill=stripe)
    c.text(stripe_text, "black", 34, 0.085, 0.58, "#FFFFFF", rot=90, max_w=0.15, max_h=0.66)
    c.rect(0, 0, 1, 0.035, fill=mix(stripe, "#000000", 0.2))
    logo_hikari(c, 0.68, 0.06, 26)
    if top_note:
        c.text(top_note, "bold_it", 10, 0.42, 0.1, "#2A2A2A", max_w=0.44)
    c.text(title, "black", 58, 0.585, 0.29, title_col, stroke=2.5, stroke_fill=outline, max_w=0.78, max_h=0.26, spacing=-6,
           shadow=(2, 2, mix(outline, "#000000", 0.4)))
    c.text(sub, "bold", 10, 0.585, 0.45, "#2A2A2A", max_w=0.76)
    c.fine(["• A Perfect Balance of Vegetables & Proteins", "• Highly Palatable, Low Waste", "• Won't Cloud the Water"],
           0.21, 0.5, 0.94, px=7.5, fill="#333333")
    for kind, u, v, L, fl in fishes:
        fish(c, kind, u, v, L, flip=fl)
    food(c)
    c.text(net, "sans_b", 9, 0.95, 0.965, "#1A1A1A", anchor="rm", max_w=0.5)


def d_marine_s(c):
    reef(c, 0.17, 0.66, 1, 0.94, "lagoon", seed=5)

    def food(c):
        pellets(c, 0.36, 0.78, 22, "#B0703A", n=110, size=2.0, ring="#FFFFFF")
    hikari_pouch(c, "#14A5A2", "HIKARI MARINE", "MARINE\n-S-", "#18B3AE", "#0B4D55", "GREAT FOR SMALLER MARINE FISH",
                 [("clown", 0.74, 0.8, 70, False)], food, "NET WT. 1.76oz (50g)")
    c.text("Floats, then slowly sinks", "bold_it", 9, 0.585, 0.635, "#0B4D55", max_w=0.76)


def d_wafer(c):
    reef(c, 0.17, 0.62, 1, 0.94, "plants", seed=6)

    def food(c):
        for k, (u, v) in enumerate(((0.3, 0.86), (0.42, 0.89), (0.36, 0.8))):
            c.circle(u, v, 13, fill="#2F4A1A", outline="#5A7A2A", width=1.5)
            c.circle(u, v, 6, fill="#3E5E22")
    hikari_pouch(c, "#3E9A2A", "HIKARI", "MINI\nALGAE\nWAFERS", "#D9E021", "#1F5A10", "Ideal for Most Herbivores",
                 [("yellow_tang", 0.7, 0.76, 64, False)], food, "NET WT. 3.0oz (85g)", top_note="Sinking Wafers")


def bio_pure(c, base, stripe, title, title_col, outline, cube_col, fishes, cubes_n, net, photo):
    c.grad(0, 0, 1, 1, mix(base, "#FFFFFF", 0.55), base)
    c.rect(0, 0, 0.17, 1, fill=stripe)
    c.text("HIKARI BIO-PURE", "black", 30, 0.085, 0.55, "#FFFFFF", rot=90, max_w=0.15, max_h=0.8)
    logo_hikari(c, 0.68, 0.03, 24)
    c.rect(0.22, 0.04, 0.5, 0.1, fill=stripe, r=4)
    c.text("FROZEN FISH FOOD", "bold", 8, 0.36, 0.07, "#FFFFFF", max_w=0.26)
    c.text(title, "black", 50, 0.59, 0.21, title_col, stroke=2.5, stroke_fill=outline, max_w=0.78, max_h=0.21, spacing=-6,
           shadow=(2, 2, mix(outline, "#000000", 0.5)))
    c.fine(gen_label_text("frozen"), 0.22, 0.33, 0.66, px=7, fill="#1A1A2A")
    reef(c, 0.2, 0.45, 0.98, 0.62, photo, seed=11)
    for kind, u, v, L, fl in fishes:
        fish(c, kind, u, v, L, flip=fl)
    # the blister window
    c.rect(0.2, 0.63, 0.98, 0.9, fill=mix(base, "#FFFFFF", 0.75), r=6)
    cubes(c, 0.23, 0.65, 0.95, 0.88, 2, 4, cube_col, well=mix(base, "#FFFFFF", 0.5))
    burst(c, 0.27, 0.62, 16, "#FFE11A", points=12, outline="#B07A00")
    c.text("3 STEP\nSTERILIZED", "black", 5, 0.27, 0.62, "#3A2A00", max_w=0.09, spacing=-1)
    c.text(cubes_n, "sans_b", 8, 0.95, 0.93, "#FFFFFF", anchor="rm", max_w=0.4)
    c.text(net, "sans_b", 8, 0.22, 0.93, "#FFFFFF", anchor="lm", max_w=0.4)
    barcode(c, 0.76, 0.32, 34, 16, digits="0 42055 30920 2")


def d_mysis(c):
    bio_pure(c, "#8A5CC8", "#5A2E9A", "MYSIS\nSHRIMP", "#7B3FC4", "#FFFFFF", "#D9C6C2",
             [("seahorse", 0.84, 0.52, 56, False), ("clown", 0.42, 0.54, 44, False)], "32 Cubes", "Net Wt. 3.5oz (100g)", "purple")


def d_cyclops(c):
    bio_pure(c, "#2B4DB8", "#18287A", "CYCLOPS", "#FFFFFF", "#18287A", "#D2502A",
             [("seahorse", 0.86, 0.53, 52, True), ("dotty", 0.45, 0.54, 44, False)], "40 Mini Cubes", "Net Wt. 3.5oz (100g)", "gorgonian")


def d_brine(c):
    """San Francisco Bay Brand style frozen flat pack."""
    c.rect(0, 0, 1, 1, fill="#1E4DB7")
    c.rect(0.04, 0.03, 0.96, 0.97, fill="#0E1E4E")
    c.text("FROZEN", "sans_b", 10, 0.5, 0.07, "#FFFFFF", sx=1.2)
    c.text("Brine Shrimp", "serif_b", 40, 0.5, 0.15, "#FFD21E", max_w=0.88, shadow=(1.5, 1.5, "#4A2A00"))
    c.text("The Original", "serif_bi", 18, 0.62, 0.25, "#FFFFFF", max_w=0.6, sx=0.85)
    c.circle(0.18, 0.25, 12, fill="#FFFFFF", outline="#1E4DB7", width=2)
    c.text("SF\nBAY", "black", 6, 0.18, 0.25, "#1E4DB7", spacing=-1)
    reef(c, 0.04, 0.3, 0.96, 0.82, "plants", seed=8)
    c.rect(0.12, 0.33, 0.88, 0.62, fill="#5A4A3A", r=5)
    cubes(c, 0.14, 0.35, 0.86, 0.6, 2, 3, "#9A4A32", frost="#F6E6DA")
    c.text("Brine Shrimp (Artemia spp.)", "sans_b", 8, 0.5, 0.66, "#FFFFFF", max_w=0.8)
    fish(c, "clown", 0.72, 0.76, 48, flip=True)
    fish(c, "angel", 0.86, 0.7, 34, flip=True)
    burst(c, 0.2, 0.8, 26, "#FFE11A", points=14, outline="#C04000")
    c.text("FOR ALL\nFRESH &\nSALTWATER\nFISH", "black", 6.5, 0.2, 0.8, "#C0201A", spacing=-1, max_w=0.24)
    c.rect(0.04, 0.9, 0.96, 0.97, fill="#0E1E4E")
    c.text("Net Wt 3.5 OZ (100g) 30 cubes", "sans_b", 8, 0.5, 0.935, "#FFFFFF", max_w=0.88)


def d_krill(c):
    """BRS Krill Superba: a clear jar full of freeze-dried krill, white wrap label across the middle."""
    rng = np.random.default_rng(9)
    c.rect(0, 0, 1, 1, fill="#E9A47A")
    for _ in range(420):
        u, v = rng.uniform(-0.02, 1.02), rng.uniform(-0.02, 1.02)
        L = rng.uniform(10, 18)
        a = rng.uniform(0, 2 * math.pi)
        col = [rgb("#E86A3A"), rgb("#F2915A"), rgb("#D4572E"), rgb("#F7B48A"), rgb("#C8482A")][rng.integers(5)]
        pts = [(u + math.cos(a + 0.5 * t) * L * t / c.fw, v + math.sin(a + 0.5 * t) * L * t / c.fh) for t in np.linspace(0, 1, 5)]
        c.line(pts, col, rng.uniform(2.5, 4.5))
    for u in (0.3, 0.36, 0.7):
        c.rect(u, 0.02, u + 0.012, 0.98, fill="#FFF3EA")
    # wrap label over the lower middle
    c.rect(0, 0.3, 1, 0.94, fill="#FFFFFF")
    c.grad(0, 0.86, 1, 0.94, "#FFFFFF", "#9ED6F2")
    c.text("BULK REEF SUPPLY", "black", 14, 0.5, 0.37, "#1A73C8", max_w=0.5)
    wave_swoosh(c, 0.3, 0.7, 0.43, 0.012, "#5FB0E8", 2)
    c.text("KRILL", "black", 46, 0.5, 0.56, "#0E2E5E", max_w=0.5)
    c.text("SUPERBA", "black", 24, 0.5, 0.7, "#E8602A", max_w=0.5)
    c.text("Freeze Dried • Coral and Fish Food", "bold", 9, 0.5, 0.79, "#1A73C8", max_w=0.5)
    c.text("56 g", "sans_b", 8, 0.5, 0.9, "#0E2E5E")
    c.fine(["Nutritional Analysis", "Protein 63%", "Fat 13%", "Fiber 4%", "Moisture 6%"], 0.78, 0.36, 0.97, px=7, fill="#1A1A1A")
    c.fine(["Contains: Euphausia", "superba (Antarctic krill)", "Feed whole or crumbled."], 0.03, 0.36, 0.22, px=7, fill="#1A1A1A")
    barcode(c, 0.04, 0.62, 40, 20, digits="8 50001 26412 3")


def d_prawn(c):
    """A clear freezer bag of prawn chunks with a red header card (aquarium-store frozen meaty food)."""
    rng = np.random.default_rng(10)
    c.grad(0, 0, 1, 1, "#E8F2F8", "#C8DCE8")
    for _ in range(38):
        u, v = rng.uniform(0.04, 0.96), rng.uniform(0.42, 0.96)
        w, h = rng.uniform(0.12, 0.2), rng.uniform(0.06, 0.1)
        col = mix("#F08A70", "#F7C6B0", rng.uniform(0, 0.6))
        c.rect(u - w / 2, v - h / 2, u + w / 2, v + h / 2, fill=col, r=6)
        for k in range(3):
            x = u - w / 2 + w * (0.25 + 0.25 * k)
            c.line([(x, v - h / 2.2), (x + 0.01, v + h / 2.2)], "#D2604A", 1.4)
        c.rect(u - w / 2.4, v - h / 2.3, u, v - h / 4, fill=mix(col, "#FFFFFF", 0.5), r=3)
    for _ in range(80):
        c.circle(rng.uniform(0, 1), rng.uniform(0.35, 1), rng.uniform(0.6, 1.4), fill="#FFFFFF")
    c.rect(0.0, 0.0, 1.0, 0.4, fill="#D8202A")
    c.rect(0.0, 0.36, 1.0, 0.4, fill="#FFD21E")
    c.circle(0.5, 0.05, 6, fill="#E8F2F8")
    c.text("FRESH FROZEN", "black", 13, 0.5, 0.12, "#FFD21E", max_w=0.8)
    c.text("PRAWN", "black", 44, 0.5, 0.235, "#FFFFFF", max_w=0.9, shadow=(2, 2, "#7A0A10"))
    c.text("whole • shell-on • for scavengers", "bold", 8, 0.5, 0.33, "#FFFFFF", max_w=0.86)
    c.rect(0.58, 0.82, 0.96, 0.95, fill="#FFFFFF", r=3)
    c.text("NET WT 1 lb (454 g)", "sans_b", 7.5, 0.77, 0.885, "#D8202A", max_w=0.36)


def d_sea_veggies(c):
    """Two Little Fishies / Julian Sprung's Sea Veggies style: silver bag, green header, seaweed in the window."""
    c.grad(0, 0, 1, 1, "#F2F4F2", "#C8CCCA", horizontal=True)
    c.rect(0, 0.09, 1, 0.33, fill="#2F9A3E")
    c.text("Julian Sprung's", "serif_bi", 16, 0.5, 0.14, "#FFE24A", max_w=0.8, sx=0.95)
    c.text("SEA VEGGIES", "black", 30, 0.5, 0.22, "#FFFFFF", stroke=1.5, stroke_fill="#14501E", max_w=0.92, sx=0.95)
    c.text("GREEN SEAWEED", "sans_b", 9, 0.5, 0.295, "#FFE24A", max_w=0.6, sx=1.2)
    c.fine(["(Porphyra yezoensis)", "Tender green seaweed sheets that your fish will devour."], 0.08, 0.35, 0.92, px=6.5,
           fill="#1A1A1A", align="m")
    # window: dark green sheets
    c.rect(0.08, 0.44, 0.92, 0.74, fill="#1E3A1E", r=4)
    for k in range(5):
        c.rect(0.1 + 0.02 * k, 0.46 + 0.012 * k, 0.88 - 0.015 * k, 0.72, fill=mix("#203E22", "#3A6A30", k / 5), r=2)
    fish(c, "yellow_tang", 0.28, 0.82, 52)
    fish(c, "blue_tang", 0.7, 0.82, 52, flip=True)
    c.circle(0.5, 0.86, 14, fill="#1F6FB8")
    c.text("Two Little\nFishies", "bold", 5.5, 0.5, 0.86, "#FFFFFF", spacing=-1, max_w=0.12)
    c.text("NET WT 30g (1 oz)", "sans_b", 8, 0.5, 0.955, "#1A1A1A")
    c.rect(0, 0, 1, 0.05, fill="#BFC4C2")
    c.text("RESEALABLE POUCH", "sans_b", 5, 0.5, 0.025, "#4A4A4A")


def d_reef_roids(c):
    """Polyp Lab Reef-Roids style: matte black pouch with a silver base strip."""
    rng = np.random.default_rng(12)
    c.rect(0, 0, 1, 1, fill="#1A1A1C")
    for _ in range(900):
        c.circle(rng.uniform(0, 1), rng.uniform(0, 0.8), 0.6, fill="#2A2A2E")
    c.grad(0, 0.8, 1, 1, "#D8DCE0", "#A8AEB4")
    # the flask logo
    fl = [(0.44, 0.2), (0.44, 0.27), (0.38, 0.38), (0.62, 0.38), (0.56, 0.27), (0.56, 0.2)]
    c.line(fl + [fl[0]], "#FFFFFF", 2.2)
    c.rect(0.41, 0.33, 0.59, 0.38, fill="#FFFFFF")
    c.text("Polyp lab", "bold_it", 19, 0.5, 0.45, "#FFFFFF", max_w=0.7)
    c.text("ENGINEERED CORAL FOOD®", "sans_b", 7, 0.5, 0.52, "#B8BCC0", max_w=0.8)
    c.text("REEF-ROIDS", "black", 28, 0.5, 0.6, "#FFFFFF", max_w=0.9)
    c.text("150-200 micron • for all filter feeders", "fine", 7, 0.5, 0.67, "#C8CCD0", max_w=0.86)
    c.text("75g", "sans_b", 10, 0.85, 0.75, "#FFFFFF")
    c.text("NET WT", "sans_b", 6, 0.85, 0.71, "#B8BCC0")
    for k, col in enumerate(("#C8A040", "#D8B858", "#B8902A")):
        c.circle(0.3 + 0.2 * k, 0.9, 11, fill="#1A1A1C", outline=col, width=2)
        c.circle(0.3 + 0.2 * k, 0.9, 5, fill=col)


def d_phyto(c):
    """Reef Nutrition Phyto-Feast style bottle label."""
    c.grad(0, 0, 1, 1, "#163A6A", "#0E2240")
    c.rect(0.2, 0.0, 0.8, 0.12, fill="#D8202A")
    c.text("Reef Nutrition", "serif_bi", 14, 0.5, 0.06, "#FFE24A", max_w=0.56)
    c.text("Phyto\nFeast", "serif_b", 38, 0.46, 0.3, "#FFE24A", stroke=2, stroke_fill="#0A2A10", max_w=0.42, max_h=0.32,
           spacing=-8, shadow=(2, 2, "#000000"))
    c.text("Food for Your", "bold", 10, 0.5, 0.5, "#FFFFFF", max_w=0.5)
    c.fine(["• Corals • Clams • Scallops", "• Copepods • Rotifers", "Live Marine Microalgae Blend"], 0.3, 0.55, 0.7,
           px=7.5, fill="#FFFFFF", align="m")
    reef(c, 0.2, 0.72, 0.8, 1.0, "zoa", seed=13)
    c.rect(0.2, 0.72, 0.8, 0.74, fill="#2FB04A")
    c.rect(0.02, 0.1, 0.19, 0.95, fill="#FFFFFF", r=3)
    c.fine(["DIRECTIONS", "Shake well.", "Dose 1 drop", "per gallon", "daily.", "", "KEEP", "REFRIGERATED"], 0.03, 0.14, 0.18, px=7,
           fill="#1A1A1A")
    c.rect(0.81, 0.1, 0.98, 0.95, fill="#FFFFFF", r=3)
    c.fine(["CONTAINS", "Nannochloropsis", "Tetraselmis", "Isochrysis", "Pavlova", "Thalassiosira"], 0.82, 0.14, 0.97, px=6.5,
           fill="#1A1A1A")
    barcode(c, 0.82, 0.66, 26, 18, digits="8 59613 00106 3")
    c.text("6 fl oz", "sans_b", 9, 0.68, 0.52, "#FFE24A")


def d_pods(c):
    """AlgaeBarn 5280 Pods style: a clear mason jar of sea water (pods drifting) with a round sticker."""
    rng = np.random.default_rng(14)
    c.grad(0, 0, 1, 1, "#C9E6EE", "#9FCAD8")
    for _ in range(260):
        c.circle(rng.uniform(0, 1), rng.uniform(0.05, 1), rng.uniform(0.6, 1.4), fill=mix("#FFFFFF", "#D8C8A0", rng.uniform(0, 1)))
    for u in (0.32, 0.38, 0.72):
        c.rect(u, 0.04, u + 0.015, 0.96, fill="#EAF8FC")
    c.text("ALGAE BARN", "black", 14, 0.5, 0.12, "#B6DCE6", sx=1.1)
    c.circle(0.5, 0.55, 66, fill="#2A2420", outline="#C9A250", width=4)
    c.circle(0.5, 0.55, 58, fill="#C9A250")
    c.circle(0.5, 0.55, 54, fill="#2A2420")
    c.text("5280", "black", 34, 0.5, 0.44, "#FFFFFF", max_w=0.34)
    c.text("PODS", "black", 26, 0.5, 0.56, "#C9A250", max_w=0.34)
    c.text("LIVE COPEPODS", "sans_b", 7.5, 0.5, 0.645, "#FFFFFF", max_w=0.3)
    c.text("Tigriopus • Tisbe • Apocyclops", "fine", 5.5, 0.5, 0.69, "#D8D0B8", max_w=0.28)
    c.text("16 oz", "sans_b", 7, 0.5, 0.74, "#C9A250")


def foundation(c, letter, name, col, photo, seed):
    """Red Sea Reef Foundation style bottle wrap: black, coral photo on top, letter badge, Red Sea logo."""
    c.rect(0, 0, 1, 1, fill="#0C0C0E")
    reef(c, 0.2, 0.0, 0.8, 0.44, photo, seed=seed)
    c.rect(0.66, 0.02, 0.78, 0.15, fill=col)
    c.text(letter, "black", 24, 0.72, 0.085, "#FFFFFF")
    c.text("REEF FOUNDATION", "sans_b", 7, 0.23, 0.04, "#FFFFFF", anchor="lm", max_w=0.4)
    c.rect(0.25, 0.48, 0.75, 0.72, outline=col, width=2.5, r=3)
    c.text("FOUNDATION™", "sans_b", 8, 0.5, 0.52, "#FFFFFF", max_w=0.42)
    c.text(name, "black", 26, 0.5, 0.62, "#FFFFFF", max_w=0.46, max_h=0.15, sx=0.86)
    c.fine(["Biologically balanced supplement", "for optimal coral growth"], 0.25, 0.75, 0.75, px=6.5, fill="#C8C8C8", align="m")
    logo_red_sea(c, 0.5 - 0.09, 0.86, 22)
    # side panels: dosing text + barcode
    c.fine(gen_label_text("dosing"), 0.02, 0.08, 0.19, px=6.5, fill="#E0E0E0")
    c.fine(["Warning: Causes serious", "eye irritation. Wash hands", "after handling."], 0.02, 0.6, 0.19, px=6, fill="#C8C8C8")
    c.poly([(0.06, 0.78), (0.09, 0.73), (0.12, 0.78)], fill="#FFFFFF")
    c.text("!", "black", 7, 0.09, 0.765, "#000000")
    c.fine(["Dosage chart", "1 mL / 100 L:", "Ca +2 ppm", "KH +0.1 dKH", "Mg +1 ppm", "", "500 mL (16.9 fl oz)"], 0.81, 0.08, 0.98,
           px=6.5, fill="#E0E0E0")
    barcode(c, 0.82, 0.68, 30, 18, digits="7 290100 77303 4")


def d_found_a(c):
    foundation(c, "A", "CALCIUM+", "#1E9BE0", "purple", 21)


def d_found_b(c):
    foundation(c, "B", "KH/ALKALINITY", "#8E5BE0", "acro", 22)


def d_found_c(c):
    foundation(c, "C", "MAGNESIUM", "#3FB05A", "zoa", 23)


def d_coral_pro(c):
    """Red Sea Coral Pro Salt style bucket wrap: purple coral photo, navy band with the white name."""
    reef(c, 0, 0, 1, 0.58, "purple", seed=31)
    fish(c, "blue_tang", 0.62, 0.22, 60, flip=True)
    c.grad(0, 0.52, 1, 1, "#20225A", "#141638")
    c.rect(0.66, 0.08, 0.76, 0.34, fill="#1E9BE0")
    c.text("12KH", "black", 15, 0.71, 0.15, "#FFFFFF", max_w=0.09)
    c.fine(["IMPROVED", "FORMULA", "for SPS", "dominant"], 0.665, 0.21, 0.755, px=6, fill="#FFFFFF", align="m")
    c.rect(0.4, 0.56, 0.6, 0.6, fill="#FFFFFF")
    c.text("IMPROVED FORMULA", "black", 6, 0.5, 0.58, "#20225A", max_w=0.19)
    c.circle(0.31, 0.665, 13, fill="#20225A", outline="#FFFFFF", width=1.5)
    c.text("CORAL PRO SALT", "black", 30, 0.53, 0.665, "#FFFFFF", sx=0.78, max_w=0.38)
    c.text("THE LIVING REEF IN EVERY HARVESTED GRAIN", "sans_b", 6.5, 0.5, 0.715, "#C8CCF0", max_w=0.4)
    for k, (el, val) in enumerate((("Ca", "450"), ("Mg", "1340"), ("KH", "12.2"), ("Ca", "440"), ("Mg", "1320"), ("KH", "11.6"))):
        u = 0.34 + 0.055 * k + (0.02 if k >= 3 else 0)
        c.rect(u, 0.75, u + 0.046, 0.82, fill="#1E9BE0", r=2)
        c.text(el, "sans_b", 6, u + 0.023, 0.77, "#FFFFFF")
        c.text(val, "sans_b", 5, u + 0.023, 0.8, "#FFFFFF")
    c.text("MIXED REEF / SOFT", "sans_b", 5, 0.42, 0.735, "#FFFFFF")
    c.text("SPS DOMINANT", "sans_b", 5, 0.6, 0.735, "#FFFFFF")
    logo_red_sea(c, 0.45, 0.86, 24)
    c.text("175 US gal", "sans_b", 7, 0.72, 0.9, "#FFFFFF")
    c.fine(["MIXING: 1 cup (0.25 kg) per", "7.5 L / 2 US gal of RO water", "for 33 ppt. Mix for 1 hour.", "Use within 4 hours.",
            "", "Net weight 22 kg / 48.5 lb"], 0.03, 0.62, 0.22, px=6.5, fill="#E0E0F0")
    barcode(c, 0.82, 0.66, 44, 22, digits="7 290100 77360 7")


def d_instant_ocean(c):
    """Instant Ocean Sea Salt style 50 gallon bag: purple with the white name and an orange clownfish mark."""
    c.grad(0, 0, 1, 1, "#8A2E9E", "#5E1C78")
    c.text("Instant", "bold", 20, 0.38, 0.1, "#FFFFFF", max_w=0.4)
    c.text("Ocean", "bold", 20, 0.72, 0.1, "#FFFFFF", max_w=0.3)
    fish(c, "clown", 0.55, 0.1, 22)
    c.text("Instant Ocean", "black", 40, 0.5, 0.25, "#FFFFFF", max_w=0.94, sx=0.86)
    c.text("Sea Salt", "bold", 14, 0.78, 0.335, "#FFFFFF")
    c.text("Creates the Ideal Marine Aquarium", "serif_bi", 11, 0.5, 0.4, "#FF9A1E", max_w=0.9)
    reef(c, 0, 0.45, 1, 1, "purple", seed=41)
    fish(c, "clown", 0.38, 0.62, 60)
    fish(c, "clown", 0.62, 0.7, 46, flip=True)
    c.circle(0.17, 0.86, 30, fill="#FF8A1E", outline="#FFFFFF", width=2)
    c.text("50\nGALLONS", "black", 9, 0.17, 0.86, "#FFFFFF", spacing=-1, max_w=0.15)
    c.fine(["• Preferred by Experts", "• Dissolves Fast", "• Nitrate & Phosphate Free"], 0.62, 0.84, 0.98, px=7.5,
           fill="#FFFFFF")


def d_matrix(c):
    """Seachem Matrix Carbon style jar wrap: white fading to a blue bubble field, dark grey italic name."""
    rng = np.random.default_rng(15)
    c.rect(0, 0, 1, 1, fill="#FFFFFF")
    c.grad(0.45, 0.35, 1, 1, "#FFFFFF", "#7EC0E8")
    for _ in range(80):
        u, v, r = rng.uniform(0.5, 0.82), rng.uniform(0.4, 0.98), rng.uniform(2, 7)
        c.circle(u, v, r, outline="#FFFFFF", width=1.2, fill=mix("#9ED2F2", "#FFFFFF", rng.uniform(0, 0.5)))
    c.text("Matrix", "cal_bi", 60, 0.26, 0.1, "#26262A", anchor="lt", max_w=0.44, stroke=1, stroke_fill="#26262A")
    c.text("Carbon", "cal_bi", 60, 0.26, 0.33, "#26262A", anchor="lt", max_w=0.44, stroke=1, stroke_fill="#26262A")
    c.fine(["Carbone sphérique • Kugelkohle", "Carbón esférico • Carbone sferico"], 0.27, 0.58, 0.58, px=6.5,
           fill="#5A5A5E")
    c.text("High Efficiency\nSpherical Carbon", "bold_it", 9, 0.27, 0.66, "#2A7FC8", anchor="lt", align="left", max_w=0.3)
    for k in range(26):
        u, v = 0.33 + rng.uniform(-0.06, 0.06), 0.86 + rng.uniform(-0.07, 0.07)
        c.circle(u, v, rng.uniform(4, 6), fill=mix("#2A2A2E", "#5A5A60", rng.uniform(0, 1)))
    logo_seachem(c, 0.7, 0.85, 26)
    c.text("250 mL", "sans_b", 9, 0.47, 0.93, "#1A1A1A")
    c.rect(0.02, 0.06, 0.22, 0.95, fill="#F2F6F8", r=3)
    c.fine(["Matrix Carbon™ is a premium", "spherical activated carbon", "with very low ash and the", "lowest leachable phosphate.",
            "", "USE: 100 mL per 400 L", "(100 US gal). Rinse before", "use. Replace monthly."], 0.03, 0.1, 0.21, px=6.5, fill="#2A2A2E")
    c.rect(0.8, 0.06, 0.98, 0.95, fill="#F2F6F8", r=3)
    barcode(c, 0.81, 0.62, 30, 20, digits="0 00116 01090 0")
    c.fine(["Seachem Laboratories", "Madison, GA 30650", "www.seachem.com"], 0.81, 0.12, 0.97, px=6, fill="#2A2A2E")


def d_sock_tag(c):
    """BRS style felt filter sock tag."""
    c.rect(0, 0, 1, 1, fill="#FFFFFF")
    c.rect(0, 0, 1, 0.3, fill="#1A73C8")
    c.circle(0.5, 0.08, 5, fill="#FFFFFF")
    c.text("BULK REEF SUPPLY", "black", 12, 0.5, 0.2, "#FFFFFF", max_w=0.9)
    c.text("FELT\nFILTER SOCK", "black", 22, 0.5, 0.49, "#0E2E5E", spacing=-3, max_w=0.9, max_h=0.3)
    c.rect(0.08, 0.68, 0.92, 0.8, fill="#E8F2FA", r=3)
    c.text("4\" × 14\" • 200 micron", "sans_b", 10, 0.5, 0.74, "#1A73C8", max_w=0.82)
    c.text("with plastic ring", "fine", 8, 0.5, 0.85, "#2A2A2A")
    barcode(c, 0.3, 0.88, 50, 12, digits="8 50001 20590 5")


def d_scissors(c):
    """Aquascaping scissors on a hanging backing card."""
    c.grad(0, 0, 1, 1, "#1E2A2E", "#0E1416")
    c.rect(0, 0, 1, 0.14, fill="#2EAE9E")
    c.circle(0.5, 0.06, 6, fill="#0E1416")
    c.text("AQUASCAPING TOOLS", "black", 9, 0.5, 0.115, "#0E1416", max_w=0.9)
    c.text("CURVED\nSCISSORS", "black", 24, 0.5, 0.25, "#FFFFFF", spacing=-3, max_w=0.9, max_h=0.18)
    c.text("for algae & macro trimming", "bold_it", 8, 0.5, 0.36, "#7FE0D0", max_w=0.9)
    c.rect(0.08, 0.4, 0.92, 0.84, fill="#2A3A3E", r=6)
    c.rect(0.1, 0.86, 0.9, 0.95, fill="#2EAE9E", r=3)
    c.text("STAINLESS STEEL • 25 cm", "sans_b", 8, 0.5, 0.905, "#0E1416", max_w=0.78)


def d_aiptasia_box(c):
    """Red Sea Aiptasia-X style kit box front."""
    c.rect(0, 0, 1, 1, fill="#FFFFFF")
    reef(c, 0.0, 0.0, 1.0, 0.5, "anemone", seed=51)
    c.rect(0, 0.5, 1, 0.62, fill="#141414")
    c.text("AIPTASIA-X", "black", 26, 0.5, 0.56, "#FFFFFF", max_w=0.92)
    c.fine(["Aiptasia anemone eliminator", "Safe for corals & invertebrates", "Kit: 60 mL + syringe applicator"], 0.06, 0.66, 0.94,
           px=7, fill="#1A1A1A", align="m")
    c.fine(["Elimine les anemones", "Entfernt Glasrosen"], 0.06, 0.77, 0.94, px=6.5, fill="#5A5A5A", align="m")
    logo_red_sea(c, 0.5 - 0.27, 0.86, 26)


def d_aiptasia_bottle(c):
    c.rect(0, 0, 1, 1, fill="#FFFFFF")
    reef(c, 0.25, 0.0, 0.75, 0.42, "anemone", seed=52)
    c.text("AIPTASIA-X", "black", 16, 0.5, 0.56, "#141414", max_w=0.5)
    c.text("60 mL", "sans_b", 7, 0.5, 0.68, "#3A3A3A")
    logo_red_sea(c, 0.5 - 0.11, 0.76, 14)
    c.fine(["Shake well. Apply", "2-3 mL directly", "into the mouth."], 0.03, 0.15, 0.24, px=6, fill="#3A3A3A")


def d_joes_juice(c):
    """Blue Life Joe's Juice style bottle wrap: white with a blue band."""
    c.rect(0, 0, 1, 1, fill="#FFFFFF")
    c.rect(0, 0.0, 1, 0.3, fill="#1E4FB0")
    c.text("Blue Life", "bold_it", 12, 0.5, 0.15, "#FFFFFF", max_w=0.5)
    c.text("JOE'S", "black", 18, 0.5, 0.44, "#1E4FB0", max_w=0.5)
    c.text("JUICE", "black", 18, 0.5, 0.6, "#141414", max_w=0.5)
    c.text("Aiptasia eliminator", "bold", 7, 0.5, 0.74, "#2A2A2A", max_w=0.56)
    c.text("20 mL", "sans_b", 8, 0.5, 0.86, "#1E4FB0")


def d_kalk(c):
    """A plain tub of pickling lime / kalkwasser powder for the home-made paste."""
    c.rect(0, 0, 1, 1, fill="#F4F2EC")
    c.rect(0, 0.0, 1, 0.22, fill="#2E8A4A")
    c.text("KALKWASSER", "black", 14, 0.5, 0.11, "#FFFFFF", max_w=0.56)
    c.text("calcium hydroxide", "bold", 9, 0.5, 0.38, "#1A3A22", max_w=0.56)
    c.text("PICKLING LIME", "black", 12, 0.5, 0.52, "#2E8A4A", max_w=0.56)
    c.fine(["Mix to a thick paste.", "Into the pest's mouth", "with a blunt syringe."], 0.24, 0.64, 0.76, px=6.5, fill="#3A3A3A", align="m")


def d_laser_box(c):
    """Aiptasia laser pen box front: dark with a green beam."""
    c.rect(0, 0, 1, 1, fill="#141A22")
    c.poly([(0.04, 0.72), (0.96, 0.3), (0.96, 0.36), (0.04, 0.76)], fill="#3CD070")
    c.text("AIPTASIA", "black", 15, 0.5, 0.22, "#FFFFFF", max_w=0.9)
    c.text("LASER", "black", 22, 0.5, 0.5, "#3CD070", max_w=0.9)
    c.text("point • zap • gone", "bold", 7, 0.5, 0.88, "#C8D2DA", max_w=0.9)


def d_drip_kit(c):
    """A drip-acclimation kit cup wrap."""
    c.rect(0, 0, 1, 1, fill="#FFFFFF")
    c.rect(0, 0, 1, 0.24, fill="#1E8FD8")
    c.text("DRIP", "black", 24, 0.5, 0.42, "#1E8FD8", max_w=0.5)
    c.text("ACCLIMATION KIT", "black", 13, 0.5, 0.6, "#0E2E5E", max_w=0.56)
    c.text("airline • drip valve • cup", "fine", 8, 0.5, 0.73, "#2A2A2A", max_w=0.56)
    c.text("for fish, corals & inverts", "bold_it", 8, 0.5, 0.84, "#1E8FD8", max_w=0.56)
    for k in range(5):
        c.circle(0.3 + 0.1 * k, 0.12, 5, fill="#BFE7FF")


def d_flipper_box(c):
    """Flipper Edge style retail box front: black with blue accents."""
    c.rect(0, 0, 1, 1, fill="#121416")
    c.poly([(0, 0.62), (1, 0.48), (1, 0.56), (0, 0.7)], fill="#1E7FD8")
    c.circle(0.2, 0.12, 15, outline="#FFFFFF", width=3)
    c.poly([(0.16, 0.1), (0.24, 0.1), (0.2, 0.16)], fill="#FFFFFF")
    c.text("FLIPPER", "black_it", 26, 0.6, 0.11, "#FFFFFF", max_w=0.66, sx=1.1)
    c.text("Edge", "bold_it", 18, 0.75, 0.2, "#3FA0F0")
    c.text("STANDARD", "sans_b", 8, 0.5, 0.27, "#B8C0C8", sx=1.3)
    c.text("2-in-1 MAGNETIC\nAQUARIUM CLEANER", "black", 11, 0.5, 0.35, "#FFFFFF", spacing=-1, max_w=0.86)
    # product shot: the magnet
    c.rect(0.14, 0.72, 0.86, 0.9, fill="#2A2E32", r=10)
    c.rect(0.16, 0.73, 0.84, 0.79, fill="#3A4046", r=8)
    c.rect(0.82, 0.74, 0.9, 0.88, fill="#1E7FD8", r=3)
    c.text("FLIPPER", "black_it", 9, 0.5, 0.81, "#FFFFFF")
    c.fine(["Glass up to 1/2\" (12 mm)", "Scrubber + scraper in one"], 0.1, 0.92, 0.9, px=6.5, fill="#C8D0D8", align="m")


def d_flipper_top(c):
    c.grad(0, 0, 1, 1, "#3A3F44", "#1C1F22")
    c.circle(0.3, 0.5, 9, outline="#FFFFFF", width=2)
    c.text("FLIPPER", "black_it", 18, 0.58, 0.44, "#FFFFFF", max_w=0.42, sx=1.1)
    c.text("Edge", "bold_it", 10, 0.7, 0.74, "#3FA0F0")


def d_api_kit(c):
    """API Reef Master style clear tub front: bottles inside around the big label."""
    rng = np.random.default_rng(61)
    c.grad(0, 0, 1, 1, "#E8F0F4", "#C8D6DE")
    for k in range(9):
        u = 0.03 + 0.11 * k
        cap = ["#1E4DB7", "#D8202A", "#2FA04A", "#F2C21E", "#7A3AB8"][k % 5]
        c.rect(u, 0.18, u + 0.08, 0.9, fill="#FFFFFF", r=4)
        c.rect(u + 0.015, 0.08, u + 0.065, 0.2, fill=cap, r=3)
    for _ in range(30):
        c.circle(rng.uniform(0, 1), rng.uniform(0, 1), 1.2, fill="#FFFFFF")
    # the big label
    c.rect(0.18, 0.18, 0.82, 0.98, fill="#FFE11A")
    c.rect(0.18, 0.18, 0.82, 0.4, fill="#1E4DB7")
    logo_api(c, 0.5, 0.27, 20)
    c.rect(0.44, 0.33, 0.56, 0.38, fill="#FFFFFF", r=3)
    c.text("MARINE", "sans_b", 6, 0.5, 0.355, "#1E4DB7")
    c.text("REEF", "black", 36, 0.5, 0.48, "#0E2E6E", max_w=0.6)
    c.text("MASTER TEST KIT", "black", 18, 0.5, 0.595, "#0E2E6E", max_w=0.6)
    c.rect(0.4, 0.645, 0.8, 0.725, fill="#9A2E9E", r=8)
    c.text("TESTS CALCIUM, CARBONATE HARDNESS\nPHOSPHATE & NITRATE", "sans_b", 6, 0.6, 0.685, "#FFFFFF", max_w=0.38, spacing=-1)
    fish(c, "clown", 0.29, 0.69, 40)
    reef(c, 0.2, 0.76, 0.4, 0.88, "zoa", seed=62)
    c.rect(0.4, 0.75, 0.8, 0.83, fill="#1E4DB7", r=8)
    c.text("MESURE CALCIUM, DURETE CARBONATEE\nPHOSPHATES & NITRATES", "sans_b", 5.5, 0.6, 0.79, "#FFFFFF", max_w=0.38, spacing=-1)
    for k, t in enumerate(("Ca", "KH", "PO4", "NO3")):
        c.rect(0.22 + 0.15 * k, 0.89, 0.34 + 0.15 * k, 0.96, fill="#1E4DB7", r=3)
        c.text(t, "sans_b", 7, 0.28 + 0.15 * k, 0.925, "#FFFFFF")
    burst(c, 0.76, 0.27, 16, "#D8202A", points=10)
    c.text("OVER\n550\nTESTS", "black", 5, 0.76, 0.27, "#FFFFFF", spacing=-1)


def d_salifert(c):
    """Salifert Profi-Test style box front."""
    c.rect(0, 0, 1, 1, fill="#2A2A8E")
    c.text("SALIFERT", "black", 22, 0.5, 0.11, "#FFE11A", max_w=0.9, sx=1.1)
    c.rect(0, 0.2, 1, 0.82, fill="#4FA8E8")
    c.text("Carbonate Hard-\nness / Alkalinity", "bold", 12, 0.5, 0.31, "#FFE11A", stroke=0.8, stroke_fill="#2A2A8E", max_w=0.92,
           spacing=-1)
    reef(c, 0.0, 0.42, 0.66, 0.82, "gorgonian", seed=63)
    c.text("100-200\nTests", "bold", 9, 0.83, 0.68, "#FFE11A", stroke=0.6, stroke_fill="#2A2A8E")
    c.text("KH/Alk", "black", 18, 0.36, 0.91, "#FFE11A", max_w=0.62)
    c.text("Profi Test", "bold", 9, 0.83, 0.92, "#FFE11A", max_w=0.3)


def d_microscope(c):
    c.grad(0, 0, 1, 1, "#F4F6F8", "#D8DEE4")
    c.text("STUDENT MICROSCOPE", "black", 14, 0.5, 0.35, "#1E2A3A", max_w=0.86)
    c.text("40x – 1000x • LED • ages 8+", "bold", 9, 0.5, 0.72, "#1E7FD8", max_w=0.86)


def d_mallet(c):
    c.rect(0, 0, 1, 1, fill="#F2C21E")
    c.rect(0, 0, 0.18, 1, fill="#141414")
    c.rect(0.82, 0, 1, 1, fill="#141414")
    c.text("RUBBER MALLET", "black", 14, 0.5, 0.38, "#141414", max_w=0.6)
    c.text("16 oz • non-marring", "bold", 8, 0.5, 0.75, "#141414", max_w=0.6)


def d_liferock(c):
    """CaribSea LifeRock Shapes style carton side: kraft board, purple panel, rock photo."""
    rng = np.random.default_rng(71)
    c.rect(0, 0, 1, 1, fill="#C9A06A")
    for _ in range(300):
        c.circle(rng.uniform(0, 1), rng.uniform(0, 1), 0.8, fill=mix("#C9A06A", "#8A6A3A", rng.uniform(0, 0.4)))
    c.rect(0, 0, 0.26, 1, fill="#5A2E8E")
    c.text("CaribSea", "bold_it", 14, 0.13, 0.22, "#FFFFFF", max_w=0.22)
    wave_swoosh(c, 0.03, 0.23, 0.36, 0.03, "#7FD0F0", 2)
    c.text("SINCE 1971", "sans_b", 6, 0.13, 0.5, "#D8C8F0")
    c.text("20 lb\n9.07 kg", "sans_b", 8, 0.13, 0.78, "#FFFFFF", spacing=0)
    c.text("LifeRock", "black_it", 34, 0.48, 0.32, "#5A2E8E", max_w=0.4, shadow=(1.5, 1.5, "#FFFFFF"))
    c.text("SHAPES", "black", 14, 0.48, 0.58, "#1E8FA8", sx=1.4, max_w=0.36)
    c.text("arches • rings • tunnels", "bold", 8, 0.48, 0.74, "#2A1A0A", max_w=0.36)
    c.text("DRY LIVE ROCK • seeded with life", "fine", 7, 0.48, 0.86, "#2A1A0A", max_w=0.36)
    reef(c, 0.68, 0.08, 0.98, 0.92, "rock", seed=72)


def d_sign(c):
    c.rect(0, 0, 1, 1, fill="#E8B04A", r=26)
    c.rect(0.014, 0.055, 0.986, 0.945, fill="#0F4A57", r=20)
    c.text("Supplies", "outfit", 92, 0.5, 0.5, "#FFF4D6", max_w=0.8, max_h=0.74)
    for u in (0.045, 0.955):
        c.circle(u, 0.5, 6, fill="#FFE14D")


def d_backdrop(c):
    c.grad(0, 0, 1, 1, "#3F8F8C", "#1F5560")
    for k in range(5):
        v = 0.11 + k * 0.19
        wave_swoosh(c, 0, 1, v, 0.025, mix("#3F8F8C", "#1F5560", v * 0.9), 3)


# name -> (width cm, height cm, painter); a wrap decal's width is the arc length it covers (r * arc)
DECALS = {
    "flake": (2.66 * math.radians(220), 5.5, d_flake),
    "pellet": (2.2 * math.radians(220), 5.6, d_pellet),
    "floating_pellet": (5.2, 8.4, d_marine_s),
    "algae_wafer": (5.2, 8.4, d_wafer),
    "mysis": (5.6, 8.8, d_mysis),
    "brine": (5.4, 8.2, d_brine),
    "krill": (2.36 * math.radians(220), 6.4, d_krill),
    "prawn": (5.6, 8.0, d_prawn),
    "cyclops": (5.6, 8.8, d_cyclops),
    "nori": (5.4, 8.4, d_sea_veggies),
    "coral_food": (5.0, 8.0, d_reef_roids),
    "plankton": (1.94 * math.radians(220), 4.6, d_phyto),
    "pods": (2.26 * math.radians(220), 6.2, d_pods),
    "water_change": (3.8 * math.radians(220), 7.6, d_coral_pro),
    "water_small": (7.2, 8.4, d_instant_ocean),
    "dose_ca": (1.94 * math.radians(220), 5.8, d_found_a),
    "dose_alk": (1.94 * math.radians(220), 5.8, d_found_b),
    "dose_mg": (1.94 * math.radians(220), 5.8, d_found_c),
    "new_sock": (3.4, 4.4, d_sock_tag),
    "fresh_carbon": (2.66 * math.radians(220), 6.2, d_matrix),
    "trim_algae": (5.4, 9.0, d_scissors),
    "zap_pest": (3.8, 8.0, d_aiptasia_box),
    "zap_pest_bottle": (1.24 * math.radians(220), 2.6, d_aiptasia_bottle),
    "settle_in": (2.6 * math.radians(200), 3.6, d_drip_kit),
    "joes_juice": (1.29 * math.radians(220), 3.7, d_joes_juice),
    "kalk_paste": (2.04 * math.radians(220), 4.1, d_kalk),
    "pest_laser": (9.0, 2.6, d_laser_box),
    "glass_cleaner": (7.2, 10.6, d_flipper_box),
    "glass_cleaner_top": (6.0, 2.0, d_flipper_top),
    "test_kit": (10.0, 6.0, d_api_kit),
    "test_kit_salifert": (3.8, 4.6, d_salifert),
    "microscope": (8.0, 1.5, d_microscope),
    "knocker": (7.0, 1.0, d_mallet),
    "move_rocks": (13.6, 4.6, d_liferock),
    "sign": (12.0, 3.0, d_sign),
    "backdrop": (12.0, 3.0, d_backdrop),
}
# decals painted at a fixed pixel size instead of PX_PER_CM (the sign and the stretched backdrop)
FIXED_PX = {"sign": (512, 128), "backdrop": (512, 128)}


def decal_px(name):
    if name in FIXED_PX:
        return FIXED_PX[name]
    w, h, _ = DECALS[name]
    return int(round(w * PX_PER_CM)), int(round(h * PX_PER_CM))


def pack():
    """Shelf packing (tallest first) of every decal into the atlas: name -> (x, y, w, h) in pixels."""
    order = sorted(DECALS, key=lambda n: (-decal_px(n)[1], n))
    rects = {}
    x = y = row_h = 0
    for n in order:
        w, h = decal_px(n)
        W, H = w + 2 * PAD, h + 2 * PAD
        if x + W > ATLAS:
            x, y, row_h = 0, y + row_h, 0
        if y + H > ATLAS:
            raise ValueError(f"supply label atlas overflow at {n}")
        rects[n] = (x + PAD, y + PAD, w, h)
        x += W
        row_h = max(row_h, H)
    return rects


RECTS = pack()


def uv(name, u, v):
    """Blender UV (v up) of point (u across, v up) on decal `name`, 1 px inside its edge."""
    x0, y0, w, h = RECTS[name]
    px = x0 + 1 + u * (w - 2)
    py = y0 + 1 + (1 - v) * (h - 2)
    return px / ATLAS, 1 - py / ATLAS


def draw_atlas(path):
    atlas = np.zeros((ATLAS, ATLAS, 3), np.uint8)
    atlas[:] = (200, 200, 200)
    for n, (x, y, w, h) in RECTS.items():
        c = Canvas(w, h)
        DECALS[n][2](c)
        a = np.asarray(c.done().convert("RGB"))
        a = np.pad(a, ((PAD, PAD), (PAD, PAD), (0, 0)), mode="edge")
        atlas[y - PAD:y + h + PAD, x - PAD:x + w + PAD] = a
    img = Image.fromarray(atlas)
    if path.lower().endswith((".jpg", ".jpeg")):
        img.save(path, quality=88, subsampling=0, optimize=True)
    else:
        img.save(path, optimize=True)
    return path


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "build", "blender", "supply_labels.jpg")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    draw_atlas(out)
    used = max(y + h for x, y, w, h in RECTS.values())
    print(f"{out}: {len(RECTS)} decals, rows used to y={used}/{ATLAS}, {os.path.getsize(out) / 1024:.0f} KB")
