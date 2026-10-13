#!/usr/bin/env python3
"""
Preview sheets for the live tank's "Sea-glass Arcade" menu and its in-tank action pictures (UI_STYLE §10).

Re-draws, with the same tokens and proportions as `ui/live/LiveTheme.kt`, `LiveMenu.kt` and `ActionArt.kt`:

    <out>/layout-landscape-closed.png   phone landscape, menu rail only (the tank keeps the screen)
    <out>/layout-landscape-open.png     the same with the Feed card open and a feeding picture playing
    <out>/layout-portrait.png           phone portrait, dock + Water card, a dose picture playing
    <out>/action-art.png                one frame of every action tool (feed containers, dropper, water change, ...)

The tank is a flat stand-in for the GL view; glyphs come straight from ReefGlyphs.kt and food pictures from the
FoodArt port in glyph_preview.py. Usage: python3 tools/graphics/action_preview.py [--out DIR]
"""
import argparse
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import glyph_preview as gp  # noqa: E402

ROOT = gp.ROOT
FONT_DIR = os.path.join(ROOT, "app/src/main/res/font")

# LiveTheme tokens
NAVY = (0x1B, 0x13, 0x40)
ORANGE = (0xFF, 0xB1, 0x3D)
CYAN = (0x5F, 0xD7, 0xFF)
YELLOW = (0xFF, 0xE1, 0x4D)
PINK = (0xFF, 0x7A, 0xA8)
GREEN = (0x8E, 0xE6, 0x6B)
PURPLE = (0xB1, 0x8C, 0xFF)
WHITE = (0xF2, 0xF6, 0xFF)
RED = (0xFF, 0x5C, 0x6C)
PANEL = (0x0C, 0x58, 0x66)
CATS = [("Feed", "feedTin", ORANGE), ("Water", "droplet", CYAN), ("Light", "sun", YELLOW), ("Add life", "fish", PINK),
        ("Clean", "flipper", GREEN), ("Trouble", "whatIf", PURPLE), ("More", "sparkleTip", WHITE)]

S = 4  # drawing px per dp (saved at 2 px per dp)


def font(size_dp, bold=True):
    path = os.path.join(FONT_DIR, "outfit_semibold.ttf" if bold else "outfit_medium.ttf")
    try:
        return ImageFont.truetype(path, int(size_dp * S))
    except OSError:
        return gp.font(int(size_dp * S))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, t):
    return mix(c, (0, 0, 0), t)


def layer(img):
    return Image.new("RGBA", img.size, (0, 0, 0, 0))


def comp(img, lay):
    img.alpha_composite(lay)


# ---------------------------------------------------------------- tank stand-in

def tank(img, w, h):
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=mix((0x14, 0x6E, 0x9A), (0x05, 0x22, 0x3F), t) + (255,))
    lay = layer(img)
    ld = ImageDraw.Draw(lay)
    # light rays
    for k in range(6):
        x = w * (0.1 + 0.16 * k)
        ld.polygon([(x, 0), (x + w * 0.05, 0), (x + w * 0.12, h), (x + w * 0.02, h)], fill=(255, 255, 255, 14))
    # sand and rocks
    ld.rectangle([0, h * 0.86, w, h], fill=(0xD9, 0xC7, 0x9A, 255))
    for (cx, cy, rx, ry, c) in [(0.30, 0.80, 0.16, 0.14, (0x8C, 0x6A, 0x58)), (0.55, 0.74, 0.14, 0.2, (0x7A, 0x5C, 0x52)),
                                (0.75, 0.82, 0.12, 0.12, (0x96, 0x72, 0x5E)), (0.62, 0.62, 0.07, 0.09, (0x84, 0x64, 0x58))]:
        ld.ellipse([w * (cx - rx), h * (cy - ry), w * (cx + rx), h * (cy + ry)], fill=c + (255,))
    # corals
    for (cx, cy, c) in [(0.33, 0.66, (0xFF, 0x7A, 0x59)), (0.52, 0.55, (0xB1, 0x8C, 0xFF)), (0.78, 0.70, (0x8E, 0xE6, 0x6B))]:
        for k in range(5):
            a = math.radians(-150 + k * 30)
            ld.line([(w * cx, h * cy), (w * cx + math.cos(a) * h * 0.08, h * cy + math.sin(a) * h * 0.08)], fill=c + (255,), width=int(h * 0.018))
    # fish
    for (cx, cy, s, c) in [(0.42, 0.40, 1.0, (0xFF, 0x7A, 0x1A)), (0.66, 0.30, 1.3, (0x2B, 0x5F, 0xD9)), (0.80, 0.48, 0.8, (0xF4, 0xD0, 0x1A))]:
        r = h * 0.035 * s
        ld.ellipse([w * cx - r * 1.6, h * cy - r, w * cx + r * 1.6, h * cy + r], fill=c + (255,))
        ld.polygon([(w * cx + r * 1.4, h * cy), (w * cx + r * 2.5, h * cy - r * 0.9), (w * cx + r * 2.5, h * cy + r * 0.9)], fill=c + (255,))
        ld.ellipse([w * cx - r * 1.0, h * cy - r * 0.35, w * cx - r * 0.6, h * cy + r * 0.05], fill=(20, 20, 30, 255))
    # water surface
    ld.rectangle([0, h * 0.07, w, h * 0.075], fill=(255, 255, 255, 60))
    comp(img, lay)


# ---------------------------------------------------------------- live theme pieces

def glyph(img, name, x, y, size, tint=NAVY, accent=(255, 255, 255)):
    parts = GLYPHS.get(name)
    if parts is None:
        return
    k = size / 24
    cv = gp.Canvas(img, x, y, k, size, size, ss=2)
    gp.draw_glyph(cv, parts, tint=tint, accent=accent)
    cv.done()


def sea_glass(img, cx, cy, r, tone, pressed=0.0, selected=False):
    """LiveTheme.drawSeaGlass for a circle: coloured shadow, body, radial highlight, white rim, navy outline, gloss."""
    depth = 6 * S
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    ow = 3 * S
    body_y = cy + depth * pressed
    d.ellipse([cx - r, cy + depth - r, cx + r, cy + depth + r], fill=shade(tone, 0.45) + (255,), outline=NAVY + (255,), width=ow)
    for k in range(16):
        t = k / 15
        rr = r * (1 - 0.65 * t)
        d.ellipse([cx - rr - r * 0.18 * t, body_y - rr - r * 0.22 * t, cx + rr - r * 0.18 * t, body_y + rr - r * 0.22 * t], fill=mix(tone, (255, 255, 255), 0.42 * t) + (255,))
    d.ellipse([cx - r + ow * 1.2, body_y - r + ow * 1.2, cx + r - ow * 1.2, body_y + r - ow * 1.2], outline=(255, 255, 255, 130), width=int(ow * 0.5))
    d.ellipse([cx - r, body_y - r, cx + r, body_y + r], outline=NAVY + (255,), width=ow)
    d.ellipse([cx - r * 0.5, body_y - r * 0.78, cx + r * 0.1, body_y - r * 0.5], fill=(255, 255, 255, 160))
    if selected:
        d.ellipse([cx - r - 6 * S, body_y - r - 6 * S, cx + r + 6 * S, body_y + r + 6 * S], outline=(255, 255, 255, 220), width=3 * S)
    comp(img, lay)
    return body_y


def label_pill(img, cx, top, text, size=13):
    f = font(size)
    d = ImageDraw.Draw(img)
    tw = d.textlength(text, font=f)
    lay = layer(img)
    ld = ImageDraw.Draw(lay)
    h = (size + 4) * S
    ld.rounded_rectangle([cx - tw / 2 - 7 * S, top, cx + tw / 2 + 7 * S, top + h], radius=h / 2, fill=NAVY + (220,))
    comp(img, lay)
    ImageDraw.Draw(img).text((cx, top + h / 2), text, fill=(255, 255, 255, 255), font=f, anchor="mm")
    return h


def rail_button(img, cx, top, name, gname, tone, size=60, labels=True, selected=False, compact=False):
    r = size * S / 2
    by = sea_glass(img, cx, top + r, r, tone, selected=selected)
    g = size * 0.5 * S
    glyph(img, gname, cx - g / 2, by - g / 2, g, accent=PURPLE if tone == WHITE else (255, 255, 255))
    h = size * S + 6 * S
    if labels:
        h += 3 * S + label_pill(img, cx, top + h + 3 * S, name, 10.5 if compact else 13)
    return h


def panel(img, box, radius=28, alpha=0.74):
    x0, y0, x1, y1 = box
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle(box, radius=radius * S, fill=PANEL + (int(255 * alpha),))
    hi = layer(img)
    hd = ImageDraw.Draw(hi)
    for k in range(40):
        hd.line([(x0, y0 + k * S), (x1, y0 + k * S)], fill=(255, 255, 255, int(40 * (1 - k / 40))))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius=radius * S, fill=255)
    lay.alpha_composite(Image.composite(hi, Image.new("RGBA", img.size, (0, 0, 0, 0)), mask))
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle(box, radius=radius * S, outline=NAVY + (255,), width=3 * S)
    comp(img, lay)


def pill(img, x, y, text, tone=CYAN, h=44, leading=None, size=14, fg=NAVY):
    f = font(size)
    tw = ImageDraw.Draw(img).textlength(text, font=f)
    w = tw + 28 * S + (26 * S if leading else 0)
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle([x, y + 4 * S, x + w, y + h * S + 4 * S], radius=h * S / 2, fill=shade(tone, 0.45) + (255,), outline=NAVY + (255,), width=int(2.5 * S))
    d.rounded_rectangle([x, y, x + w, y + h * S], radius=h * S / 2, fill=tone + (255,), outline=NAVY + (255,), width=int(2.5 * S))
    d.rounded_rectangle([x + 12 * S, y + 4 * S, x + min(w * 0.42, 64 * S), y + 9 * S], radius=3 * S, fill=(255, 255, 255, 150))
    comp(img, lay)
    tx = x + 14 * S
    if leading:
        glyph(img, leading, tx, y + (h * S - 20 * S) / 2, 20 * S)
        tx += 26 * S
    ImageDraw.Draw(img).text((tx, y + h * S / 2), text, fill=fg + (255,), font=f, anchor="lm")
    return w


def glass_pill(img, x, y, w, h=48):
    """w in px, h in dp."""
    panel(img, (x, y, x + w, y + h * S), radius=h / 2)


def tile(img, x, y, w, h, tone, text, gname=None, food=None, sub=None):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    depth = 6 * S
    d.rounded_rectangle([x, y + depth, x + w, y + h], radius=22 * S, fill=shade(tone, 0.45) + (255,), outline=NAVY + (255,), width=3 * S)
    d.rounded_rectangle([x, y, x + w, y + h - depth], radius=22 * S, fill=tone + (255,), outline=NAVY + (255,), width=3 * S)
    d.rounded_rectangle([x + 14 * S, y + 5 * S, x + w * 0.5, y + 10 * S], radius=3 * S, fill=(255, 255, 255, 160))
    comp(img, lay)
    g = 32 * S
    if food is not None:
        fn = FOOD_FN[food]
        k = g / 48
        cv = gp.Canvas(img, x + (w - g) / 2, y + 10 * S, k, g, g, ss=2)
        fn(cv)
        cv.done()
    elif gname:
        glyph(img, gname, x + (w - g) / 2, y + 10 * S, g)
    f = font(12)
    ImageDraw.Draw(img).text((x + w / 2, y + 10 * S + g + 12 * S), text, fill=NAVY + (255,), font=f, anchor="mm")
    if sub:
        ImageDraw.Draw(img).text((x + w / 2, y + 10 * S + g + 27 * S), sub, fill=shade(NAVY, 0) + (200,), font=font(10, False), anchor="mm")


def close_dot(img, cx, cy):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    r = 13 * S
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=PANEL + (220,), outline=NAVY + (255,), width=3 * S)
    comp(img, lay)
    glyph(img, "close", cx - 7 * S, cy - 7 * S, 14 * S, tint=(255, 255, 255))


def hermie(img, cx, cy, size, mood="happy"):
    """Hermie, after Hermie.kt's drawHermie: spiral shell, red body, eye stalks, a waving claw."""
    k = size / 48
    lay = layer(img)
    d = ImageDraw.Draw(lay)

    def P(x, y):
        return (cx + (x - 24) * k, cy + (y - 24) * k)

    for i in range(3):
        x0, y0 = P(14 + i * 6, 36)
        x1, y1 = P(10 + i * 6, 44)
        d.line([(x0, y0), (x1, y1)], fill=(0xE2, 0x4B, 0x3C, 255), width=int(2.5 * k))
    sx, sy = P(28, 25)
    d.ellipse([sx - 13 * k, sy - 13 * k, sx + 13 * k, sy + 13 * k], fill=(0xF2, 0xB3, 0x5C, 255), outline=NAVY + (255,), width=int(2 * k))
    for rr in (9, 5):
        d.arc([sx - rr * k, sy - rr * k, sx + rr * k, sy + rr * k], 200, 520, fill=NAVY + (255,), width=int(1.6 * k))
    bx, by = P(16, 33)
    d.ellipse([bx - 9 * k, by - 7 * k, bx + 9 * k, by + 7 * k], fill=(0xFF, 0x6B, 0x5B, 255), outline=NAVY + (255,), width=int(2 * k))
    for ex in (12, 18):
        x0, y0 = P(ex, 28)
        x1, y1 = P(ex - 1, 18)
        d.line([(x0, y0), (x1, y1)], fill=NAVY + (255,), width=int(1.6 * k))
        d.ellipse([x1 - 3 * k, y1 - 3 * k, x1 + 3 * k, y1 + 3 * k], fill=(255, 255, 255, 255), outline=NAVY + (255,), width=int(1.2 * k))
        d.ellipse([x1 - 1.3 * k, y1 - 1.3 * k, x1 + 1.3 * k, y1 + 1.3 * k], fill=NAVY + (255,))
    clx, cly = P(6, 26)
    d.ellipse([clx - 5 * k, cly - 4 * k, clx + 5 * k, cly + 4 * k], fill=(0xFF, 0x6B, 0x5B, 255), outline=NAVY + (255,), width=int(2 * k))
    mx, my = P(15, 34)
    if mood == "worried":
        d.arc([mx - 3 * k, my, mx + 3 * k, my + 4 * k], 200, 340, fill=NAVY + (255,), width=int(1.5 * k))
    else:
        d.arc([mx - 3 * k, my - 3 * k, mx + 3 * k, my + 2 * k], 20, 160, fill=NAVY + (255,), width=int(1.5 * k))
    comp(img, lay)


def hermie_pill(img, x, y, text, maxw):
    f = font(13)
    words = text.split()
    lines, cur = [], ""
    for wd in words:
        t = (cur + " " + wd).strip()
        if ImageDraw.Draw(img).textlength(t, font=f) > maxw * S and cur:
            lines.append(cur)
            cur = wd
        else:
            cur = t
    lines.append(cur)
    lines = lines[:3]
    tw = max(ImageDraw.Draw(img).textlength(t, font=f) for t in lines)
    h = max(52, 10 + 17 * len(lines)) * S
    w = 4 * S + 48 * S + tw + 14 * S + 40 * S
    panel(img, (x, y, x + w, y + h), radius=26)
    hermie(img, x + 28 * S, y + h / 2, 44 * S)
    for i, t in enumerate(lines):
        ImageDraw.Draw(img).text((x + 56 * S, y + h / 2 + (i - (len(lines) - 1) / 2) * 17 * S), t, fill=(255, 255, 255, 255), font=f, anchor="lm")
    close_dot(img, x + w - 22 * S, y + h / 2)
    return w, h


def meter_pill(img, x, y, value, folded=False):
    tone = GREEN if value >= 70 else YELLOW if value >= 40 else RED
    w = (84 if folded else 190) * S
    h = 48 * S
    panel(img, (x, y, x + w, y + h), radius=24)
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    fx, fy, r = x + 22 * S, y + h / 2, 14 * S
    d.ellipse([fx - r, fy - r, fx + r, fy + r], fill=tone + (255,), outline=NAVY + (255,), width=int(2.2 * S))
    d.ellipse([fx - 6 * S, fy - 5 * S, fx - 3 * S, fy - 2 * S], fill=NAVY + (255,))
    d.ellipse([fx + 3 * S, fy - 5 * S, fx + 6 * S, fy - 2 * S], fill=NAVY + (255,))
    d.arc([fx - 7 * S, fy - 4 * S, fx + 7 * S, fy + 7 * S], 20, 160, fill=NAVY + (255,), width=int(2.2 * S))
    if not folded:
        bx, by = x + 44 * S, y + 28 * S
        d.rounded_rectangle([bx, by, bx + 96 * S, by + 10 * S], radius=5 * S, fill=NAVY + (140,))
        d.rounded_rectangle([bx, by, bx + 96 * S * value / 100, by + 10 * S], radius=5 * S, fill=tone + (255,))
        d.rounded_rectangle([bx, by, bx + 96 * S, by + 10 * S], radius=5 * S, outline=NAVY + (255,), width=2 * S)
    comp(img, lay)
    if not folded:
        ImageDraw.Draw(img).text((x + 44 * S, y + 15 * S), f"Tank happy {value}%", fill=(255, 255, 255, 255), font=font(13), anchor="lm")
    close_dot(img, x + w - 22 * S, y + h / 2)
    return w


def mode_toggle(img, x, y, sandbox=True):
    f = font(13)
    w1 = ImageDraw.Draw(img).textlength("Watch", font=f) + 54 * S
    w2 = ImageDraw.Draw(img).textlength("Sandbox", font=f) + 54 * S
    w = w1 + w2 + 6 * S
    glass_pill(img, x, y, w, 54)
    sx = x + 3 * S + (w1 if sandbox else 0)
    sw = w2 if sandbox else w1
    lay = layer(img)
    ImageDraw.Draw(lay).rounded_rectangle([sx, y + 3 * S, sx + sw, y + 51 * S], radius=24 * S, fill=(PINK if sandbox else CYAN) + (255,))
    comp(img, lay)
    for i, (t, g) in enumerate([("Watch", "eye"), ("Sandbox", "bucket")]):
        ox = x + 3 * S + (0 if i == 0 else w1)
        on = (i == 1) == sandbox
        col = NAVY if on else (255, 255, 255)
        glyph(img, g, ox + 14 * S, y + 17 * S, 20 * S, tint=col, accent=(255, 255, 255) if on else (0xCD, 0xEF, 0xF3))
        ImageDraw.Draw(img).text((ox + 40 * S, y + 27 * S), t, fill=col + (255,), font=f, anchor="lm")
    return w


def clock_pill(img, x, y, text="Day 3 · 14:25", phase="Day"):
    f = font(13)
    tw = ImageDraw.Draw(img).textlength(text, font=f)
    w = tw + 80 * S
    glass_pill(img, x, y, w, 40)
    lay = layer(img)
    ImageDraw.Draw(lay).ellipse([x + 12 * S, y + 11 * S, x + 30 * S, y + 29 * S], fill=YELLOW + (255,))
    comp(img, lay)
    ImageDraw.Draw(img).text((x + 36 * S, y + 20 * S), text, fill=(255, 255, 255, 255), font=f, anchor="lm")
    ImageDraw.Draw(img).text((x + 42 * S + tw, y + 20 * S), phase, fill=(0xCD, 0xEF, 0xF3, 255), font=font(11, False), anchor="lm")
    return w


def bottom_bar(img, W, H, maxw=560):
    w = min(W - 32 * S, maxw * S)
    x = (W - w) / 2
    y = H - 16 * S - 64 * S
    lay = layer(img)
    ImageDraw.Draw(lay).rounded_rectangle([x, y, x + w, y + 64 * S], radius=32 * S, fill=(0x0A, 0x1F, 0x3D, 225), outline=(255, 255, 255, 50), width=S)
    comp(img, lay)
    names = ["Home", "Log", "Live", "Tank", "More"]
    for i, n in enumerate(names):
        cx = x + w * (i + 0.5) / 5
        col = (0x5C, 0xE1, 0xE6) if n == "Live" else (0xA7, 0xC4, 0xD4)
        ImageDraw.Draw(img).ellipse([cx - 10 * S, y + 22 * S, cx + 10 * S, y + 42 * S], outline=col + (255,), width=2 * S)
    return x, w


def caption(img, cx, top, text):
    f = font(13)
    tw = ImageDraw.Draw(img).textlength(text, font=f)
    lay = layer(img)
    ImageDraw.Draw(lay).rounded_rectangle([cx - tw / 2 - 10 * S, top, cx + tw / 2 + 10 * S, top + 24 * S], radius=12 * S, fill=NAVY + (200,))
    comp(img, lay)
    ImageDraw.Draw(img).text((cx, top + 12 * S), text, fill=(255, 255, 255, 255), font=f, anchor="mm")


# ---------------------------------------------------------------- action tools (ActionArt.kt, simplified)

class Box:
    """A 48-unit box centred on (cx, cy), `u` px wide, rotated `deg` about its centre, like ActionArt.box."""

    def __init__(self, img, cx, cy, u, deg=0.0):
        self.img, self.cx, self.cy, self.k, self.a = img, cx, cy, u / 48, math.radians(deg)
        self.lay = layer(img)
        self.d = ImageDraw.Draw(self.lay)

    def p(self, x, y):
        x, y = x - 24, y - 24
        ca, sa = math.cos(self.a), math.sin(self.a)
        return (self.cx + (x * ca - y * sa) * self.k, self.cy + (x * sa + y * ca) * self.k)

    def w(self, v):
        return max(1, int(v * self.k))

    def poly(self, pts, fill=None, outline=NAVY, width=2.5):
        q = [self.p(*t) for t in pts]
        if fill is not None:
            self.d.polygon(q, fill=fill + (255,) if len(fill) == 3 else fill)
        if outline is not None:
            self.d.line(q + [q[0]], fill=outline + (255,), width=self.w(width), joint="curve")

    def rrect(self, x, y, w, h, r, fill=None, outline=NAVY, width=2.5):
        pts = []
        for (cx, cy, a0) in [(x + w - r, y + r, -90), (x + w - r, y + h - r, 0), (x + r, y + h - r, 90), (x + r, y + r, 180)]:
            for i in range(7):
                a = math.radians(a0 + i * 15)
                pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        self.poly(pts, fill, outline, width)

    def circle(self, x, y, r, fill=None, outline=NAVY, width=2.5):
        pts = [(x + r * math.cos(math.radians(i * 15)), y + r * math.sin(math.radians(i * 15))) for i in range(24)]
        self.poly(pts, fill, outline, width)

    def line(self, x0, y0, x1, y1, col=NAVY, width=2.5):
        self.d.line([self.p(x0, y0), self.p(x1, y1)], fill=col + (255,), width=self.w(width))

    def done(self):
        comp(self.img, self.lay)


def shaker(img, cx, cy, u, deg, food=ORANGE):
    b = Box(img, cx, cy, u, deg)
    b.rrect(14, 12, 20, 32, 6, fill=(0xE9, 0xF3, 0xFF))
    b.rrect(16, 26, 16, 16, 4, fill=food, outline=None)
    b.rrect(14, 30, 20, 7, 1, fill=PINK, outline=None)
    b.rrect(14, 12, 20, 32, 6)
    b.rrect(12, 4, 24, 9, 3, fill=ORANGE)
    for x in (18, 24, 30):
        b.circle(x, 6.5, 1.2, fill=NAVY, outline=None)
    b.done()


def particles(img, x, y, spread, n, cols, seed=1):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for i in range(n):
        h = math.sin(i * 12.9898 + seed * 78.233) * 43758.5453
        h -= math.floor(h)
        h2 = math.sin(i * 39.3468 + seed * 11.135) * 24634.6345
        h2 -= math.floor(h2)
        px, py = x + (h - 0.5) * spread, y + h2 * spread * 0.9
        r = 2.2 * S
        d.ellipse([px - r, py - r, px + r, py + r], fill=cols[i % len(cols)] + (255,))
    comp(img, lay)


def ripples(img, x, y, r, col=(255, 255, 255)):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for k, q in enumerate((0.35, 0.7, 1.0)):
        d.ellipse([x - r * q, y - r * q * 0.3, x + r * q, y + r * q * 0.3], outline=col + (int(200 * (1.1 - q)),), width=2 * S)
    comp(img, lay)


def swirl(img, x, y, r, col):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    d.ellipse([x - r, y - r * 0.32, x + r, y + r * 0.32], fill=col + (70,))
    for arm in range(3):
        pts = []
        for i in range(20):
            t = i / 19
            a = arm * 2.1 + t * 4.2
            pts.append((x + math.cos(a) * r * t, y + math.sin(a) * r * t * 0.32))
        d.line(pts, fill=col + (200,), width=3 * S, joint="curve")
    comp(img, lay)


def drop(img, x, y, r, col):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    d.ellipse([x - r, y - r, x + r, y + r], fill=col + (255,), outline=NAVY + (255,), width=S)
    d.polygon([(x - r * 0.8, y - r * 0.5), (x + r * 0.8, y - r * 0.5), (x, y - r * 2.2)], fill=col + (255,))
    comp(img, lay)


def bubbles(img, x, y, r, n=6):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for i in range(n):
        bx = x + math.sin(i * 2.3) * r * 0.5
        by = y - i * r * 0.28
        br = (2 + (i % 3)) * S
        d.ellipse([bx - br, by - br, bx + br, by + br], outline=(255, 255, 255, 200), width=S)
    comp(img, lay)


def sparks(img, x, y, r, col=YELLOW):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for i in range(7):
        a = i / 7 * 2 * math.pi
        x0, y0 = x + math.cos(a) * r * 0.35, y + math.sin(a) * r * 0.35
        x1, y1 = x + math.cos(a) * r * 0.7, y + math.sin(a) * r * 0.7
        mx, my = (x0 + x1) / 2 + math.cos(a + 1.5) * r * 0.08, (y0 + y1) / 2 + math.sin(a + 1.5) * r * 0.08
        d.line([(x0, y0), (mx, my), (x1, y1)], fill=col + (255,), width=3 * S)
    comp(img, lay)


def twinkles(img, x, y, r, col=(255, 255, 255)):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for i in range(5):
        a = i / 5 * 2 * math.pi + 0.4
        px, py = x + math.cos(a) * r * 0.8, y + math.sin(a) * r * 0.6
        s = (4 + 2 * (i % 2)) * S
        d.polygon([(px, py - s), (px + s * 0.25, py - s * 0.25), (px + s, py), (px + s * 0.25, py + s * 0.25), (px, py + s), (px - s * 0.25, py + s * 0.25), (px - s, py), (px - s * 0.25, py - s * 0.25)], fill=col + (230,))
    comp(img, lay)


def pulse(img, x, y, r, col=RED):
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for q in (0.5, 0.8, 1.1):
        d.ellipse([x - r * q, y - r * q, x + r * q, y + r * q], outline=col + (int(220 * (1.2 - q)),), width=3 * S)
    comp(img, lay)


def thermometer(img, cx, cy, u, level, col):
    b = Box(img, cx, cy, u)
    b.rrect(19, 4, 10, 32, 5, fill=(0xE9, 0xF3, 0xFF))
    b.circle(24, 38, 7, fill=col)
    b.rrect(22, 36 - 28 * level, 4, 28 * level + 2, 2, fill=col, outline=None)
    for k in range(4):
        b.line(29, 10 + k * 6, 32, 10 + k * 6, width=1.5)
    b.done()


def doc_cell(img, x, y, w, h, title):
    panel(img, (x, y, x + w, y + h), radius=22, alpha=0.5)
    ImageDraw.Draw(img).text((x + w / 2, y + h - 16 * S), title, fill=(255, 255, 255, 255), font=font(13), anchor="mm")


def art_sheet(out):
    cols, cw, ch = 4, 260, 230
    names = []
    rows = 5
    W, H = (cols * cw + 40) * S, (rows * ch + 90) * S
    img = Image.new("RGBA", (W, H), (0x06, 0x13, 0x27, 255))
    tank(img, W, H)
    lay = layer(img)
    ImageDraw.Draw(lay).rectangle([0, 0, W, H], fill=(0, 0, 0, 90))
    comp(img, lay)
    ImageDraw.Draw(img).text((20 * S, 30 * S), "In-tank action pictures · Sea-glass Arcade · one frame each (ActionArt.kt)", fill=(255, 255, 255, 255), font=font(20), anchor="lm")
    ImageDraw.Draw(img).text((20 * S, 58 * S), "Navy outline, candy fill, white highlight; the command goes to the sim when the picture reaches the water.", fill=(0xCD, 0xEF, 0xF3, 255), font=font(13, False), anchor="lm")

    def cell(i, title):
        x = (20 + (i % cols) * cw) * S
        y = (80 + (i // cols) * ch) * S
        doc_cell(img, x + 6 * S, y + 6 * S, (cw - 12) * S, (ch - 12) * S, title)
        return x + cw * S / 2, y + (ch - 30) * S / 2
    u = 90 * S
    surf = lambda cy: cy + 26 * S
    # 0 shaker
    cx, cy = cell(0, "Feed · shaker (flakes, pellets, wafer)")
    shaker(img, cx - 10 * S, cy - 30 * S, u * 0.9, 128)
    particles(img, cx + 10 * S, cy, 40 * S, 14, [(0xE8, 0x89, 0x3A), (0xC9, 0x4B, 0x3C), (0x6E, 0x9A, 0x3E), (0xD9, 0xB3, 0x6A)])
    ripples(img, cx + 10 * S, surf(cy) + 10 * S, 40 * S)
    # 1 frozen cube
    cx, cy = cell(1, "Feed · frozen cube (mysis, brine, krill)")
    b = Box(img, cx, cy - 10 * S, u * 0.7, 12)
    b.rrect(8, 8, 32, 32, 7, fill=(0xD7, 0xF1, 0xFF))
    for (x, y) in [(16, 18), (28, 22), (20, 30), (31, 32)]:
        b.circle(x, y, 3.2, fill=(0xF2, 0xA7, 0xB8), outline=None)
    b.rrect(12, 11, 10, 3, 1.5, fill=(255, 255, 255), outline=None)
    b.done()
    ripples(img, cx, cy + 34 * S, 46 * S)
    particles(img, cx, cy + 40 * S, 30 * S, 6, [(0xF2, 0xA7, 0xB8)], seed=3)
    # 2 nori clip
    cx, cy = cell(2, "Feed · nori clip on the glass")
    b = Box(img, cx, cy, u * 0.85)
    b.poly([(10, 14), (34, 10), (38, 38), (12, 40)], fill=(0x2F, 0x6B, 0x3A))
    b.rrect(18, 4, 14, 12, 3, fill=(0x1B, 0x22, 0x30))
    b.circle(25, 10, 2, fill=GREEN, outline=None)
    b.done()
    twinkles(img, cx, cy, 40 * S)
    # 3 pipette
    cx, cy = cell(3, "Feed · pipette (coral food, phyto, pods)")
    b = Box(img, cx, cy - 20 * S, u * 0.8, -8)
    b.rrect(19, 2, 10, 12, 5, fill=PINK)
    b.rrect(21, 13, 6, 26, 2, fill=(0xE9, 0xF3, 0xFF))
    b.poly([(21, 39), (27, 39), (24, 47)], fill=(0xE9, 0xF3, 0xFF))
    b.done()
    lay = layer(img)
    ImageDraw.Draw(lay).ellipse([cx - 36 * S, cy + 12 * S, cx + 36 * S, cy + 46 * S], fill=(0xD9, 0x8F, 0xB0, 110))
    comp(img, lay)
    # 4 dose
    cx, cy = cell(4, "Dose · dropper bottle + swirl")
    b = Box(img, cx - 12 * S, cy - 26 * S, u * 0.85, 160)
    b.rrect(12, 14, 24, 30, 6, fill=(0xE9, 0xF3, 0xFF))
    b.rrect(14, 26, 20, 16, 4, fill=PURPLE, outline=None)
    b.rrect(12, 14, 24, 30, 6)
    b.rrect(18, 5, 12, 10, 3, fill=PURPLE)
    b.poly([(22, 1), (26, 1), (25, 5), (23, 5)], fill=(0xE9, 0xF3, 0xFF))
    b.done()
    drop(img, cx + 4 * S, cy + 14 * S, 5 * S, PURPLE)
    swirl(img, cx + 4 * S, cy + 40 * S, 50 * S, PURPLE)
    # 5 water change
    cx, cy = cell(5, "Water change · siphon, bucket, jug")
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    d.rectangle([cx - 110 * S, cy - 50 * S, cx + 30 * S, cy - 40 * S], fill=(2, 16, 30, 120))
    d.line([(cx - 40 * S, cy + 30 * S), (cx - 40 * S, cy - 60 * S), (cx + 40 * S, cy - 60 * S), (cx + 60 * S, cy - 10 * S)], fill=(0x8E, 0xE6, 0x6B, 255), width=5 * S, joint="curve")
    for k in range(5):
        t = k / 4
        d.ellipse([cx - 42 * S + t * 80 * S, cy - 63 * S, cx - 36 * S + t * 80 * S, cy - 57 * S], fill=(0x9C, 0x7A, 0x4A, 255))
    comp(img, lay)
    b = Box(img, cx + 64 * S, cy + 14 * S, u * 0.8)
    b.poly([(8, 12), (40, 12), (36, 44), (12, 44)], fill=CYAN)
    b.poly([(10, 18), (38, 18), (35, 40), (13, 40)], fill=(0x9C, 0x7A, 0x4A), outline=None)
    b.line(8, 12, 40, 12)
    b.done()
    b = Box(img, cx - 70 * S, cy + 10 * S, u * 0.6, -30)
    b.rrect(10, 10, 26, 32, 6, fill=(0xE9, 0xF3, 0xFF))
    b.rrect(12, 20, 22, 20, 4, fill=CYAN, outline=None)
    b.rrect(10, 10, 26, 32, 6)
    b.done()
    bubbles(img, cx - 40 * S, cy + 40 * S, 40 * S)
    # 6 heater break
    cx, cy = cell(6, "Trouble · heater breaks")
    b = Box(img, cx - 10 * S, cy, u * 0.9, 8)
    b.rrect(18, 2, 12, 44, 6, fill=(0xE9, 0xF3, 0xFF))
    for k in range(6):
        b.line(20, 10 + k * 5, 28, 12 + k * 5, col=(0x8A, 0x8F, 0xA8), width=2)
    b.rrect(17, 0, 14, 6, 2, fill=NAVY)
    b.done()
    sparks(img, cx - 10 * S, cy - 40 * S, 70 * S)
    b = Box(img, cx + 50 * S, cy - 30 * S, 48 * S)
    b.circle(24, 24, 22, fill=(255, 255, 255))
    b.done()
    glyph(img, "plugOff", cx + 50 * S - 16 * S, cy - 30 * S - 16 * S, 32 * S, accent=RED)
    thermometer(img, cx + 54 * S, cy + 30 * S, 56 * S, 0.3, CYAN)
    # 7 heater stuck / heat wave
    cx, cy = cell(7, "Trouble · heater stuck on / heat wave")
    pulse(img, cx, cy, 70 * S, (0xFF, 0x6B, 0x3D))
    b = Box(img, cx - 20 * S, cy, u * 0.9)
    b.rrect(18, 2, 12, 44, 6, fill=(0xFF, 0xB0, 0x8F))
    for k in range(6):
        b.line(20, 10 + k * 5, 28, 12 + k * 5, col=(0xFF, 0x5C, 0x3D), width=2.5)
    b.done()
    thermometer(img, cx + 40 * S, cy, 70 * S, 0.9, (0xFF, 0x5C, 0x3D))
    # 8 lights
    cx, cy = cell(8, "Lights on / off (flicker + dim)")
    lay = layer(img)
    ImageDraw.Draw(lay).polygon([(cx - 70 * S, cy - 30 * S), (cx + 70 * S, cy - 30 * S), (cx + 110 * S, cy + 60 * S), (cx - 110 * S, cy + 60 * S)], fill=(0xFF, 0xF2, 0xA8, 90))
    comp(img, lay)
    b = Box(img, cx, cy - 40 * S, u * 1.6)
    b.rrect(2, 18, 44, 10, 4, fill=(0xE9, 0xF3, 0xFF))
    for k in range(6):
        b.circle(7 + k * 6.8, 26, 2.2, fill=YELLOW, outline=None)
    b.done()
    # 9 pump
    cx, cy = cell(9, "Pump on / off · flow lines")
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for k in range(3):
        y0 = cy - 14 * S + k * 14 * S
        d.arc([cx + 30 * S + k * 10 * S, y0 - 10 * S, cx + 90 * S + k * 10 * S, y0 + 10 * S], 200, 340, fill=CYAN + (220,), width=3 * S)
    comp(img, lay)
    b = Box(img, cx - 10 * S, cy, u * 0.8)
    b.circle(24, 24, 18, fill=(0xE9, 0xF3, 0xFF))
    b.circle(24, 24, 12, fill=CYAN)
    for k in range(3):
        a = math.radians(30 + k * 120)
        b.line(24, 24, 24 + math.cos(a) * 10, 24 + math.sin(a) * 10, width=3)
    b.done()
    bubbles(img, cx + 20 * S, cy + 20 * S, 40 * S)
    # 10 power cut
    cx, cy = cell(10, "Power cut · plug out, tank dims")
    lay = layer(img)
    ImageDraw.Draw(lay).rectangle([cx - 120 * S, cy - 90 * S, cx + 120 * S, cy + 80 * S], fill=(0, 0, 20, 110))
    comp(img, lay)
    b = Box(img, cx - 30 * S, cy, u * 0.7)
    b.rrect(4, 10, 22, 28, 5, fill=(0xE9, 0xF3, 0xFF))
    b.circle(12, 20, 2, fill=NAVY, outline=None)
    b.circle(18, 20, 2, fill=NAVY, outline=None)
    b.done()
    b = Box(img, cx + 30 * S, cy, u * 0.7)
    b.rrect(14, 12, 22, 24, 5, fill=ORANGE)
    b.line(14, 20, 6, 20, width=3)
    b.line(14, 28, 6, 28, width=3)
    b.line(36, 24, 46, 24, width=4)
    b.done()
    sparks(img, cx, cy, 60 * S)
    # 11 bag
    cx, cy = cell(11, "Add life · bag floats, opens, fish swims out")
    b = Box(img, cx - 10 * S, cy - 10 * S, u * 0.95)
    b.poly([(14, 10), (34, 10), (40, 40), (24, 46), (8, 40)], fill=(0xD7, 0xF1, 0xFF))
    b.poly([(10, 26), (38, 26), (40, 40), (24, 46), (8, 40)], fill=CYAN, outline=None)
    b.poly([(14, 10), (34, 10), (40, 40), (24, 46), (8, 40)], fill=None)
    b.circle(24, 8, 3, fill=PINK)
    b.done()
    glyph(img, "fish", cx + 26 * S, cy + 14 * S, 36 * S, tint=NAVY, accent=ORANGE)
    bubbles(img, cx + 40 * S, cy + 10 * S, 40 * S)
    ripples(img, cx - 10 * S, cy - 30 * S, 50 * S)
    # 12 net
    cx, cy = cell(12, "Take out · the net dips in")
    b = Box(img, cx, cy, u)
    b.line(30, 2, 26, 24, col=ORANGE, width=4)
    b.circle(22, 32, 12, fill=(0xE9, 0xF3, 0xFF))
    for k in range(5):
        b.line(12 + k * 5, 24, 12 + k * 5, 42, col=(0x8A, 0x8F, 0xA8), width=1)
    b.circle(22, 32, 12, fill=None)
    b.done()
    ripples(img, cx, cy - 20 * S, 50 * S)
    # 13 syringe vs pest
    cx, cy = cell(13, "Pest anemone appears / zapped")
    pulse(img, cx - 40 * S, cy + 20 * S, 40 * S)
    glyph(img, "pestAnemone", cx - 66 * S, cy - 6 * S, 52 * S, tint=(0xD9, 0xC7, 0x9A), accent=PINK)
    b = Box(img, cx + 40 * S, cy - 10 * S, u * 0.9, 25)
    b.rrect(19, 8, 10, 28, 2, fill=(0xE9, 0xF3, 0xFF))
    b.rrect(20, 20, 8, 15, 1, fill=CYAN, outline=None)
    b.line(24, 36, 24, 46, width=1.5)
    b.rrect(16, 2, 16, 6, 2, fill=CYAN)
    b.done()
    twinkles(img, cx + 20 * S, cy + 20 * S, 30 * S)
    # 14 maintain / snip
    cx, cy = cell(14, "Clean · new sock, carbon, trim algae")
    glyph(img, "sock", cx - 80 * S, cy - 20 * S, 52 * S, tint=NAVY, accent=(0x9C, 0x7A, 0x4A))
    glyph(img, "sock", cx - 20 * S, cy - 26 * S, 52 * S, tint=NAVY, accent=(255, 255, 255))
    twinkles(img, cx + 6 * S, cy, 34 * S)
    b = Box(img, cx + 64 * S, cy, u * 0.7)
    b.circle(16, 36, 6, fill=GREEN)
    b.circle(32, 36, 6, fill=GREEN)
    b.line(18, 31, 34, 4, width=3)
    b.line(30, 31, 14, 4, width=3)
    b.done()
    # 15 shells / rocks / hearts
    cx, cy = cell(15, "Shells, move rocks, settle in")
    glyph(img, "shell", cx - 96 * S, cy - 4 * S, 40 * S, accent=PINK)
    glyph(img, "rocks", cx - 36 * S, cy - 20 * S, 60 * S, accent=ORANGE)
    for k in range(3):
        glyph(img, "heart", cx + 40 * S + k * 18 * S, cy - 30 * S - k * 14 * S, 28 * S, tint=PINK, accent=(255, 255, 255))
    # 16 tap glass
    cx, cy = cell(16, "Knock on the glass")
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for q in (20, 40, 60):
        d.ellipse([cx - q * S, cy - q * S, cx + q * S, cy + q * S], outline=(255, 255, 255, int(220 - q * 2.5)), width=int(2.5 * S))
    comp(img, lay)
    glyph(img, "touch", cx - 4 * S, cy - 50 * S, 50 * S, tint=(255, 255, 255), accent=ORANGE)
    # 17 alert / sick
    cx, cy = cell(17, "A fish gets sick · alert")
    pulse(img, cx, cy, 70 * S)
    b = Box(img, cx, cy, u * 0.8)
    pts = [(24 + (14 + 3 * math.sin(i * 1.7)) * math.cos(i / 12 * 2 * math.pi), 24 + (14 + 3 * math.sin(i * 1.7)) * math.sin(i / 12 * 2 * math.pi)) for i in range(12)]
    b.poly(pts, fill=GREEN)
    b.circle(19, 21, 3, fill=(255, 255, 255), width=1.5)
    b.circle(29, 21, 3, fill=(255, 255, 255), width=1.5)
    b.line(19, 31, 29, 31, width=2)
    b.done()
    # 18 speed / reset
    cx, cy = cell(18, "Time speed · reset whoosh")
    lay = layer(img)
    d = ImageDraw.Draw(lay)
    for k in range(3):
        x = cx - 90 * S + k * 34 * S
        d.line([(x, cy - 60 * S), (x + 20 * S, cy - 44 * S), (x, cy - 28 * S)], fill=YELLOW + (255,), width=6 * S, joint="curve")
    d.arc([cx - 110 * S, cy - 20 * S, cx + 110 * S, cy + 90 * S], 200, 340, fill=CYAN + (220,), width=14 * S)
    d.arc([cx - 90 * S, cy - 4 * S, cx + 90 * S, cy + 90 * S], 200, 340, fill=(255, 255, 255, 180), width=5 * S)
    comp(img, lay)
    twinkles(img, cx + 40 * S, cy + 10 * S, 40 * S)
    # 19 hermie + meter
    cx, cy = cell(19, "Hermie reacts · Tank happy meter")
    hermie(img, cx - 60 * S, cy - 20 * S, 80 * S, "worried")
    meter_pill(img, cx - 40 * S, cy + 20 * S, 82)
    out_img = img.resize((W // 2, H // 2), Image.LANCZOS).convert("RGB")
    out_img.save(out)


# ---------------------------------------------------------------- layouts

def landscape(out, open_card):
    Wd, Hd = 800, 370
    W, H = Wd * S, Hd * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    tank(img, W, H)
    bar_x, bar_w = bottom_bar(img, W, H)
    top = 26  # status bar
    # rail: 6 bubbles (+ the tuck bubble when it fits), sized to the height like LiveMenu.Rail (UI_STYLE §10)
    room = Hd - top - 8 - 10
    with_tuck = room - 50 >= (44 + 6 + 4) * 6
    per = (room - 50 if with_tuck else room) / 6
    labels = per >= 60 + 6 + 30
    bubble = 60 if labels else max(44, min(60, per - 6 - 4))
    y = (top + 6) * S
    if with_tuck:
        sea_glass(img, 42 * S, y + 20 * S, 20 * S, WHITE)
        glyph(img, "chevronUp", 31 * S, y + 9 * S, 22 * S)
        y += 50 * S
    for i, (name, g, tone) in enumerate(CATS[:6]):
        y += 2 * S
        y += rail_button(img, 42 * S, y, name, g, tone, size=bubble, labels=labels, selected=(open_card and i == 0)) + 2 * S
    # More bubble bottom-right
    rail_button(img, W - (12 + 26) * S, H - (8 + 52 + 6 + 26) * S, "More", "sparkleTip", WHITE, size=52)
    # top-right HUD
    x = W - 12 * S
    mw = mode_toggle(img, 0, -1000 * S)  # measure
    cw = clock_pill(img, 0, -1000 * S)
    mode_toggle(img, x - mw, (top + 8) * S)
    clock_pill(img, x - mw - 8 * S - cw, (top + 15) * S)
    meter_w = 84 * S
    hw, hh = hermie_pill(img, 0, -1000 * S, "Shake shake! Flakes for everyone.", 200)
    hermie_pill(img, x - hw, (top + 72) * S, "Shake shake! Flakes for everyone.", 200)
    meter_pill(img, x - hw - 8 * S - meter_w, (top + 72) * S, 87, folded=True)
    if open_card:
        cx0 = (84 + 4) * S
        cwid = int(min(380, max(260, Wd * 0.35))) * S
        panel(img, (cx0, (top + 8) * S, cx0 + cwid, H - (88 + 8) * S))
        sea_glass(img, cx0 + 30 * S, (top + 34) * S, 18 * S, ORANGE)
        glyph(img, "feedTin", cx0 + 20 * S, (top + 24) * S, 20 * S)
        ImageDraw.Draw(img).text((cx0 + 58 * S, (top + 34) * S), "Feed the fish", fill=(255, 255, 255, 255), font=font(18), anchor="lm")
        close_dot(img, cx0 + cwid - 26 * S, (top + 34) * S)
        ImageDraw.Draw(img).text((cx0 + 14 * S, (top + 66) * S), "Tap a food to drop it in. Watch it go into the tank!", fill=(0xCD, 0xEF, 0xF3, 255), font=font(11, False), anchor="lm")
        ImageDraw.Draw(img).text((cx0 + 14 * S, (top + 86) * S), "Dry", fill=(255, 255, 255, 255), font=font(15), anchor="lm")
        foods = [("FLAKE", "Flakes"), ("PELLET", "Sinking pellets"), ("FLOATING_PELLET", "Floating pellets"), ("ALGAE_WAFER", "Algae wafer"),
                 ("MYSIS", "Mysis shrimp"), ("BRINE", "Brine shrimp")]
        cols = 3
        tw = (cwid - 28 * S - 16 * S) / cols
        for i, (k, lab) in enumerate(foods[:3]):
            tx = cx0 + 14 * S + (i % cols) * (tw + 8 * S)
            ty = (top + 98) * S + (i // cols) * 96 * S
            tile(img, tx, ty, tw, 88 * S, ORANGE, lab.split()[0] if len(lab) > 12 else lab, food=k)
        ImageDraw.Draw(img).text((cx0 + 14 * S, (top + 200) * S), "Frozen  (the card scrolls)", fill=(255, 255, 255, 255), font=font(15), anchor="lm")
        # the feeding picture in the tank
        fx, fy = W * 0.58, H * 0.2
        shaker(img, fx - 14 * S, fy - 34 * S, 80 * S, 128)
        particles(img, fx + 6 * S, fy + 6 * S, 40 * S, 14, [(0xE8, 0x89, 0x3A), (0xC9, 0x4B, 0x3C), (0x6E, 0x9A, 0x3E)])
        ripples(img, fx + 6 * S, H * 0.075 + 10 * S, 40 * S)
        caption(img, fx, fy + 60 * S, "Flakes")
    img.resize((W // 2, H // 2), Image.LANCZOS).convert("RGB").save(out)


def portrait(out):
    Wd, Hd = 393, 852
    W, H = Wd * S, Hd * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    tank(img, W, H)
    bottom_bar(img, W, H)
    top = 40
    mode_toggle(img, 12 * S, (top + 8) * S)
    cw = clock_pill(img, 0, -1000 * S)
    clock_pill(img, W - 12 * S - cw, (top + 15) * S)
    mw = meter_pill(img, 12 * S, (top + 70) * S, 64, folded=True)
    hermie_pill(img, 12 * S + mw + 8 * S, (top + 70) * S, "Drip drip. Shell builder makes coral skeletons strong.", 150)
    # the dose picture
    fx, fy = W * 0.62, H * 0.18
    b = Box(img, fx - 12 * S, fy - 26 * S, 76 * S, 160)
    b.rrect(12, 14, 24, 30, 6, fill=(0xE9, 0xF3, 0xFF))
    b.rrect(14, 26, 20, 16, 4, fill=PURPLE, outline=None)
    b.rrect(12, 14, 24, 30, 6)
    b.rrect(18, 5, 12, 10, 3, fill=PURPLE)
    b.done()
    drop(img, fx + 4 * S, fy + 16 * S, 5 * S, PURPLE)
    swirl(img, fx + 4 * S, fy + 44 * S, 48 * S, PURPLE)
    caption(img, fx, fy + 66 * S, "Shell builder")
    # dock
    dock_bottom = H - (88 + 16) * S
    per = (Wd - 24 - 12) / 7
    bubble = max(44, min(60, per - 8))
    dock_h = (bubble + 6 + 3 + 15 + 12 + 4) * S
    dock_top = dock_bottom - dock_h
    panel(img, (12 * S, dock_top, W - 12 * S, dock_bottom))
    for i, (name, g, tone) in enumerate(CATS):
        cx = 18 * S + (i + 0.5) * per * S
        rail_button(img, cx, dock_top + 6 * S, name, g, tone, size=bubble, labels=True, selected=(i == 1), compact=True)
    # Water card above the dock
    ch = int(Hd * 0.46) * S
    c0 = dock_top - 8 * S - ch
    panel(img, (12 * S, c0, W - 12 * S, dock_top - 8 * S))
    sea_glass(img, 40 * S, c0 + 28 * S, 18 * S, CYAN)
    glyph(img, "droplet", 30 * S, c0 + 18 * S, 20 * S)
    ImageDraw.Draw(img).text((68 * S, c0 + 28 * S), "Water", fill=(255, 255, 255, 255), font=font(18), anchor="lm")
    close_dot(img, W - 38 * S, c0 + 28 * S)
    # water strip
    panel(img, (24 * S, c0 + 54 * S, W - 24 * S, c0 + 100 * S), radius=22, alpha=0.6)
    for i, (lab, val, col) in enumerate([("Temp", "25.2°", GREEN), ("Sal", "1.025", GREEN), ("NO3", "18.0", YELLOW), ("PO4", "0.18", RED), ("Light", "45%", GREEN), ("Flow", "1.0x", GREEN)]):
        x = 24 * S + (i + 0.5) * (W - 48 * S) / 6
        ImageDraw.Draw(img).text((x, c0 + 66 * S), lab, fill=(0xCD, 0xEF, 0xF3, 255), font=font(10, False), anchor="mm")
        ImageDraw.Draw(img).text((x, c0 + 84 * S), val, fill=col + (255,), font=font(13), anchor="mm")
    ImageDraw.Draw(img).text((24 * S, c0 + 118 * S), "Water", fill=(255, 255, 255, 255), font=font(15), anchor="lm")
    tiles = [("Change water", "waveSparkle", "25 %"), ("Small change", "droplet", "10 %"), ("Warmer", "flame", None),
             ("Cooler", "snowflake", None), ("Saltier", "dropPlus", None), ("Less salty", "dropMinus", None)]
    cols = 3
    tw = (W - 48 * S - 16 * S) / cols
    for i, (lab, g, sub) in enumerate(tiles):
        tx = 24 * S + (i % cols) * (tw + 8 * S)
        ty = c0 + 132 * S + (i // cols) * 100 * S
        tile(img, tx, ty, tw, 92 * S, CYAN, lab, gname=g, sub=sub)
    pill(img, W / 2 - 40 * S, c0 + ch - 56 * S, "More", tone=CYAN)
    img.resize((W // 2, H // 2), Image.LANCZOS).convert("RGB").save(out)


FOOD_FN = {k: fn for (k, _, _, fn) in gp.FOODS}
GLYPHS = {}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "ui-graphics"))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    GLYPHS.update(dict(gp.load_glyphs(gp.GLYPHS_KT)))
    landscape(os.path.join(a.out, "layout-landscape-closed.png"), open_card=False)
    landscape(os.path.join(a.out, "layout-landscape-open.png"), open_card=True)
    portrait(os.path.join(a.out, "layout-portrait.png"))
    art_sheet(os.path.join(a.out, "action-art.png"))
    print(f"previews -> {a.out}")


if __name__ == "__main__":
    sys.exit(main())
