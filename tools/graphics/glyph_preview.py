#!/usr/bin/env python3
"""
Preview sheets for ReefLive's custom icon set and food pictures (UI_STYLE §5.1 / §5.2).

Reads the glyph tables straight out of `app/.../ui/components/ReefGlyphs.kt` (they are plain SVG path strings
tagged with a role) and re-draws the food illustrations of `ui/live/FoodArt.kt` with the same geometry, then
rasterises both with a small PIL path renderer (no SVG library needed). Writes:

    <out>/icon-set.png / .svg      every glyph on an Abyss card with its name
    <out>/food-pictures.png / .svg every FoodKind picture with its label

Usage: python3 tools/graphics/glyph_preview.py [--out DIR] [--scale N]
"""
import argparse
import math
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GLYPHS_KT = os.path.join(ROOT, "app/src/main/java/com/reeflive/app/ui/components/ReefGlyphs.kt")

# Abyss tokens (theme/Color.kt)
ABYSS1 = (0x06, 0x13, 0x27)
ABYSS2 = (0x0A, 0x1F, 0x3D)
FOAM = (0xE8, 0xF6, 0xFA)
MIST = (0xA7, 0xC4, 0xD4)
CORAL = (0xFF, 0x7A, 0x59)
CORAL_SOFT = (0xFF, 0xB0, 0x8F)
LAGOON_BRIGHT = (0x5C, 0xE1, 0xE6)

# FoodPalette (theme/Color.kt)
P = dict(
    flakeOrange="#E8893A", flakeRed="#C94B3C", flakeGreen="#6E9A3E", flakeTan="#D9B36A",
    mysisPink="#F2A7B8", mysisDeep="#D9748F", brineOrange="#F2A25A", brineDeep="#D8763A",
    krillRed="#E2604A", krillPale="#F7B9A8", pelletBrown="#8A5A2B", pelletLight="#B88A4A",
    noriGreen="#2F6B3A", noriDark="#1E4A2A", clipBlack="#1B2230", waferGreen="#35502A", waferSpeck="#A9C58A",
    podTan="#D8CBA8", prawnPink="#F0B7A4", prawnStripe="#D9684F", phyto="#6DBF5A", cyclopsRed="#FF5A36",
    powderPink="#D98FB0", powderOrange="#F2B35C", eye="#14161C", glint="#FFFFFF", water="#5CE1E6",
)


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgba(c, a=1.0):
    if isinstance(c, str):
        c = hex_rgb(c)
    return (c[0], c[1], c[2], int(round(255 * a)))


# ---------------------------------------------------------------- glyph table from Kotlin

def dots(spec):
    out = ""
    for s in spec.split(";"):
        cx, cy, r = [float(v) for v in s.split()]
        out += f"M{cx - r} {cy}a{r} {r} 0 1 0 {2 * r} 0a{r} {r} 0 1 0 {-2 * r} 0Z"
    return out


def spark(cx, cy, r):
    k = r * 0.28
    return (f"M{cx} {cy - r}L{cx + k} {cy - k}L{cx + r} {cy}L{cx + k} {cy + k}L{cx} {cy + r}"
            f"L{cx - k} {cy + k}L{cx - r} {cy}L{cx - k} {cy - k}Z")


def part(d, role, width=1.75):
    return (d, role, width)


ENV = dict(
    dots=dots, spark=spark,
    ink=lambda d, width=1.75: part(d, "INK", width),
    inkFill=lambda d: part(d, "INK_FILL"),
    accent=lambda d, width=1.75: part(d, "ACCENT", width),
    accentFill=lambda d: part(d, "ACCENT_FILL"),
    soft=lambda d: part(d, "SOFT"),
    accentSoft=lambda d: part(d, "ACCENT_SOFT"),
    g=lambda name, *parts: (name, list(parts)),
)


def load_glyphs(path):
    src = open(path, encoding="utf-8").read()
    body = src[src.index("object ReefGlyphs"):src.index("val all: List<ReefGlyph>")]
    glyphs = []
    for m in re.finditer(r"val (\w+) = g\(", body):
        i = m.end() - 1
        depth = 0
        j = i
        in_str = False
        while j < len(body):
            ch = body[j]
            if ch == '"' and body[j - 1] != "\\":
                in_str = not in_str
            elif not in_str:
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        break
            j += 1
        expr = body[i:j + 1]
        expr = re.sub(r"(\d)f\b", r"\1", expr)          # Kotlin float literals
        name, parts = eval("g" + expr, {"__builtins__": {}}, ENV)
        glyphs.append((name, parts))
    return glyphs


# ---------------------------------------------------------------- SVG path flattening

_TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def _arc_points(x1, y1, rx, ry, phi, fa, fs, x2, y2, steps=24):
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    phi = math.radians(phi)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p = cp * dx + sp * dy
    y1p = -sp * dx + cp * dy
    rx, ry = abs(rx), abs(ry)
    lam = (x1p ** 2) / (rx ** 2) + (y1p ** 2) / (ry ** 2)
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if fa == fs:
        coef = -coef
    cxp = coef * rx * y1p / ry
    cyp = -coef * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        d = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return d

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    pts = []
    for k in range(1, steps + 1):
        t = t1 + dt * k / steps
        x = cp * rx * math.cos(t) - sp * ry * math.sin(t) + cx
        y = sp * rx * math.cos(t) + cp * ry * math.sin(t) + cy
        pts.append((x, y))
    return pts


def _cubic(p0, p1, p2, p3, steps=16):
    out = []
    for k in range(1, steps + 1):
        t = k / steps
        mt = 1 - t
        x = mt ** 3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0]
        y = mt ** 3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]
        out.append((x, y))
    return out


def flatten(d):
    """SVG path data -> list of (points, closed) subpaths."""
    toks = _TOKEN.findall(d)
    i = 0
    cmd = None
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    last_ctrl = None
    subs = []
    pts = []

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    def flush(closed):
        nonlocal pts
        if len(pts) > 1:
            subs.append((pts, closed))
        pts = []

    while i < len(toks):
        if re.match(r"[A-Za-z]", toks[i]):
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                flush(True)
                cur = start
                pts = [cur]
                continue
        rel = cmd.islower()
        c = cmd.upper()
        ox, oy = cur if rel else (0.0, 0.0)
        if c == "M":
            x, y = num() + ox, num() + oy
            flush(False)
            cur = start = (x, y)
            pts = [cur]
            cmd = "l" if rel else "L"
        elif c == "L":
            cur = (num() + ox, num() + oy)
            pts.append(cur)
        elif c == "H":
            cur = (num() + ox, cur[1])
            pts.append(cur)
        elif c == "V":
            cur = (cur[0], num() + oy)
            pts.append(cur)
        elif c == "C":
            p1 = (num() + ox, num() + oy)
            p2 = (num() + ox, num() + oy)
            p3 = (num() + ox, num() + oy)
            pts.extend(_cubic(cur, p1, p2, p3))
            last_ctrl = p2
            cur = p3
        elif c == "S":
            p2 = (num() + ox, num() + oy)
            p3 = (num() + ox, num() + oy)
            p1 = (2 * cur[0] - last_ctrl[0], 2 * cur[1] - last_ctrl[1]) if last_ctrl else cur
            pts.extend(_cubic(cur, p1, p2, p3))
            last_ctrl = p2
            cur = p3
        elif c == "Q":
            q = (num() + ox, num() + oy)
            p3 = (num() + ox, num() + oy)
            p1 = (cur[0] + 2 / 3 * (q[0] - cur[0]), cur[1] + 2 / 3 * (q[1] - cur[1]))
            p2 = (p3[0] + 2 / 3 * (q[0] - p3[0]), p3[1] + 2 / 3 * (q[1] - p3[1]))
            pts.extend(_cubic(cur, p1, p2, p3))
            cur = p3
        elif c == "A":
            rx, ry, phi, fa, fs = num(), num(), num(), int(num()), int(num())
            x, y = num() + ox, num() + oy
            pts.extend(_arc_points(cur[0], cur[1], rx, ry, phi, fa, fs, x, y))
            cur = (x, y)
        else:
            raise ValueError("unsupported path command " + cmd)
        if c in "CSQ":
            pass
        if c not in "CS":
            last_ctrl = None
    flush(False)
    return subs


# ---------------------------------------------------------------- tiny renderer (supersampled)

class Canvas:
    """
    Draws in 'unit' space onto one cell of an RGBA image: the cell's top-left is (ox, oy) px in `img`, it is `cw` x `ch`
    px, `k` px per unit, supersampled by `ss`. Each primitive is painted on a small cell-sized layer and composited,
    so translucent parts blend properly; `done()` downsamples the cell onto the sheet.
    """

    def __init__(self, img, ox, oy, k, cw, ch, ss=4):
        self.img = img
        self.ox, self.oy, self.k, self.ss = ox, oy, k, ss
        self.cw, self.ch = int(cw), int(ch)
        self.cell = Image.new("RGBA", (self.cw * ss, self.ch * ss), (0, 0, 0, 0))
        self.transforms = []  # list of callables unit->unit

    def _map(self, p):
        for t in reversed(self.transforms):
            p = t(p)
        return (p[0] * self.k * self.ss, p[1] * self.k * self.ss)

    def _w(self, w):
        return max(1.0, w * self.k * self.ss)

    def _layer(self):
        return Image.new("RGBA", self.cell.size, (0, 0, 0, 0))

    def _blend(self, layer):
        self.cell.alpha_composite(layer)

    def done(self):
        small = self.cell.resize((self.cw, self.ch), Image.LANCZOS)
        self.img.alpha_composite(small, (int(self.ox), int(self.oy)))

    def fill_subpaths(self, subs, color):
        layer = self._layer()
        d = ImageDraw.Draw(layer)
        # even-odd is close enough for these shapes: draw each subpath, the ring glyphs are strokes anyway
        for pts, _ in subs:
            if len(pts) >= 3:
                d.polygon([self._map(p) for p in pts], fill=color)
        self._blend(layer)

    def stroke_subpaths(self, subs, color, width):
        layer = self._layer()
        d = ImageDraw.Draw(layer)
        w = self._w(width)
        r = w / 2
        for pts, closed in subs:
            mp = [self._map(p) for p in pts]
            if closed and len(mp) > 1:
                mp = mp + [mp[0]]
            if len(mp) == 1:
                x, y = mp[0]
                d.ellipse([x - r, y - r, x + r, y + r], fill=color)
                continue
            d.line(mp, fill=color, width=int(round(w)), joint="curve")
            for x, y in (mp[0], mp[-1]):
                d.ellipse([x - r, y - r, x + r, y + r], fill=color)
            for x, y in mp[1:-1]:
                d.ellipse([x - r, y - r, x + r, y + r], fill=color)
        self._blend(layer)

    # primitives mirroring DrawScope
    def path(self, d, color, stroke=None):
        subs = flatten(d)
        if stroke is None:
            self.fill_subpaths(subs, color)
        else:
            self.stroke_subpaths(subs, color, stroke)

    def circle(self, color, r, c, stroke=None):
        self.path(dots(f"{c[0]} {c[1]} {r}"), color, stroke)

    def line(self, color, a, b, w):
        self.stroke_subpaths([([a, b], False)], color, w)

    def oval(self, color, tl, size, stroke=None):
        cx, cy = tl[0] + size[0] / 2, tl[1] + size[1] / 2
        rx, ry = size[0] / 2, size[1] / 2
        d = f"M{cx - rx} {cy}a{rx} {ry} 0 1 0 {2 * rx} 0a{rx} {ry} 0 1 0 {-2 * rx} 0Z"
        self.path(d, color, stroke)

    def arc(self, color, start, sweep, tl, size, stroke):
        cx, cy = tl[0] + size[0] / 2, tl[1] + size[1] / 2
        rx, ry = size[0] / 2, size[1] / 2
        pts = []
        n = 32
        for k in range(n + 1):
            a = math.radians(start + sweep * k / n)
            pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
        self.stroke_subpaths([(pts, False)], color, stroke)

    def round_rect(self, color, tl, size, r):
        x, y, w, h = tl[0], tl[1], size[0], size[1]
        d = (f"M{x + r} {y}h{w - 2 * r}a{r} {r} 0 0 1 {r} {r}v{h - 2 * r}a{r} {r} 0 0 1 {-r} {r}h{-(w - 2 * r)}"
             f"a{r} {r} 0 0 1 {-r} {-r}v{-(h - 2 * r)}a{r} {r} 0 0 1 {r} {-r}Z")
        self.path(d, color)

    def rotated(self, deg, pivot):
        a = math.radians(deg)
        ca, sa = math.cos(a), math.sin(a)
        px, py = pivot

        def t(p):
            x, y = p[0] - px, p[1] - py
            return (px + x * ca - y * sa, py + x * sa + y * ca)
        return t

    def push(self, t):
        self.transforms.append(t)

    def pop(self):
        self.transforms.pop()


def draw_glyph(cv, parts, tint=FOAM, accent=CORAL_SOFT, mirror=False):
    if mirror:
        cv.push(lambda p: (24 - p[0], p[1]))
    for d, role, width in parts:
        if role in ("INK", "INK_FILL"):
            color = rgba(tint)
        elif role in ("ACCENT", "ACCENT_FILL"):
            color = rgba(accent)
        elif role == "SOFT":
            color = rgba(tint, 0.22)
        else:
            color = rgba(accent, 0.3)
        cv.path(d, color, stroke=width if role in ("INK", "ACCENT") else None)
    if mirror:
        cv.pop()


def glyph_svg(parts, tint="#E8F6FA", accent="#FFB08F"):
    out = []
    for d, role, width in parts:
        if role in ("INK", "ACCENT"):
            col = tint if role == "INK" else accent
            out.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>')
        else:
            col = tint if role in ("INK_FILL", "SOFT") else accent
            op = {"SOFT": 0.22, "ACCENT_SOFT": 0.3}.get(role, 1)
            out.append(f'<path d="{d}" fill="{col}" fill-opacity="{op}"/>')
    return "".join(out)


# ---------------------------------------------------------------- food pictures (port of FoodArt.kt)

def quad(a, b, c, d):
    return f"M{a[0]} {a[1]}L{b[0]} {b[1]}L{c[0]} {c[1]}L{d[0]} {d[1]}Z"


def food_flakes(cv):
    def flake(d, color):
        cv.path(d, rgba(color))
        cv.path(d, rgba("#000000", 0.2), stroke=1.2)
    flake(quad((9, 13), (22, 9), (24, 20), (13, 24)), P["flakeOrange"])
    flake(quad((26, 11), (39, 15), (35, 26), (24, 22)), P["flakeRed"])
    flake(quad((13, 28), (24, 26), (27, 36), (12, 39)), P["flakeGreen"])
    flake(quad((29, 29), (41, 28), (39, 40), (30, 38)), P["flakeTan"])
    cv.circle(rgba(P["flakeOrange"]), 1.4, (8, 33))
    cv.circle(rgba(P["flakeRed"]), 1.1, (42, 20))
    cv.circle(rgba(P["flakeTan"]), 1.2, (22, 43))


def shrimp_body(cv, color, deep, centre, radius, start, sweep, width):
    cv.arc(color, start, sweep, (centre[0] - radius, centre[1] - radius), (radius * 2, radius * 2), width)
    for k in range(1, 5):
        a = math.radians(start + sweep * k / 5)
        cx = centre[0] + math.cos(a) * radius
        cy = centre[1] + math.sin(a) * radius
        nx, ny = math.cos(a), math.sin(a)
        cv.line(deep, (cx - nx * width * 0.45, cy - ny * width * 0.45), (cx + nx * width * 0.45, cy + ny * width * 0.45), 1.1)


def food_mysis(cv):
    shrimp_body(cv, rgba(P["mysisPink"]), rgba(P["mysisDeep"]), (24, 25), 11, 200, 200, 7)
    cv.circle(rgba(P["mysisDeep"]), 4.6, (34, 21))
    cv.circle(rgba(P["eye"]), 1.3, (35.5, 19.5))
    cv.circle(rgba(P["glint"]), 0.45, (35.9, 19.1))
    cv.line(rgba(P["mysisDeep"]), (36, 17.5), (43, 9), 1.1)
    cv.line(rgba(P["mysisDeep"]), (37.5, 19), (45, 15), 1.1)
    cv.path("M14 28L7 24L9 31Z", rgba(P["mysisPink"]))
    for k in range(5):
        x = 17 + k * 3.6
        cv.line(rgba(P["mysisDeep"]), (x, 33), (x - 1.2, 37.5), 1.1)


def food_brine(cv):
    cv.line(rgba(P["brineOrange"]), (11, 25), (34, 22), 7)
    cv.line(rgba(P["brineDeep"]), (12, 23), (33, 20.5), 1.2)
    for k in range(8):
        x = 13 + k * 2.8
        cv.line(rgba(P["brineDeep"]), (x, 27.5), (x - 0.8, 32), 1)
    cv.line(rgba(P["brineOrange"]), (11, 25), (5, 20), 2.2)
    cv.line(rgba(P["brineOrange"]), (11, 25), (5, 30), 2.2)
    cv.line(rgba(P["brineDeep"]), (35, 21), (38.5, 17), 1.2)
    cv.line(rgba(P["brineDeep"]), (35, 23), (39, 26), 1.2)
    cv.circle(rgba(P["eye"]), 1.6, (39, 16.5))
    cv.circle(rgba(P["eye"]), 1.6, (39.5, 26.5))


def food_krill(cv):
    shrimp_body(cv, rgba(P["krillRed"], 0.9), rgba(P["krillPale"]), (23, 30), 13, 215, 150, 6.5)
    cv.circle(rgba(P["krillRed"]), 4.4, (35, 24))
    cv.circle(rgba(P["eye"]), 2.2, (36, 22.5))
    cv.circle(rgba(P["glint"]), 0.7, (36.8, 21.7))
    cv.line(rgba(P["krillPale"]), (37, 20), (45, 10), 1.1)
    cv.line(rgba(P["krillPale"]), (38.5, 22), (46, 19), 1.1)
    cv.path("M12 24L5 19L6 27Z", rgba(P["krillRed"]))
    for k in range(6):
        x = 14 + k * 3.4
        cv.line(rgba(P["krillPale"]), (x, 35.5), (x - 1, 40), 1.1)


def food_pellets(cv, dark, light, floating):
    if floating:
        cv.line(rgba(P["water"]), (4, 27), (44, 27), 1.6)
        for p in ((14, 25), (25, 25.5), (35, 24.5)):
            cv.circle(rgba(dark), 4.6, p)
            cv.circle(rgba(light), 2.2, (p[0] - 1.3, p[1] - 1.5))
            cv.arc(rgba(P["water"], 0.7), 20, 140, (p[0] - 7, p[1] - 3), (14, 10), 1.2)
    else:
        for p in ((15, 31), (25, 34), (34, 28), (20, 22), (30, 21)):
            cv.circle(rgba(dark), 5, p)
            cv.circle(rgba(light), 2.2, (p[0] - 1.5, p[1] - 1.6))


def food_nori(cv):
    sheet = "M11 9L39 9L39 31C34 30 32 36 33 40L11 40Z"
    cv.path(sheet, rgba(P["noriGreen"]))
    for k in range(5):
        x = 15 + k * 5
        cv.line(rgba(P["noriDark"]), (x, 12), (x + 1, 37), 1.1)
    cv.path(sheet, rgba(P["noriDark"]), stroke=1.2)
    cv.round_rect(rgba(P["clipBlack"]), (7, 5), (14, 8), 2.5)
    cv.circle(rgba(P["clipBlack"]), 3.5, (7, 9))
    cv.circle(rgba(P["water"], 0.6), 1.6, (7, 9), stroke=1)


def food_wafer(cv):
    cv.push(cv.rotated(-12, (24, 26)))
    cv.oval(rgba(P["waferGreen"]), (9, 15), (30, 22))
    cv.oval(rgba(P["noriDark"]), (9, 15), (30, 22), stroke=1.4)
    for p in ((16, 22), (23, 19), (30, 23), (19, 30), (27, 31), (34, 29), (14, 28), (24, 25)):
        cv.circle(rgba(P["waferSpeck"]), 1.1, p)
    cv.line(rgba(P["noriDark"]), (28, 17), (33, 21), 1)
    cv.pop()


def food_pods(cv):
    for x, y, deg in ((14, 16, 0), (30, 13, 30), (20, 33, -20), (34, 31, 10)):
        cv.push(cv.rotated(deg, (x, y)))
        cv.oval(rgba(P["podTan"]), (x - 2.2, y - 3.4), (4.4, 6.8))
        cv.line(rgba(P["podTan"]), (x - 1, y - 3.2), (x - 4, y - 7.5), 1)
        cv.line(rgba(P["podTan"]), (x + 1, y - 3.2), (x + 4, y - 7.5), 1)
        cv.line(rgba(P["podTan"]), (x, y + 3.2), (x, y + 6.5), 1)
        cv.circle(rgba(P["eye"]), 0.7, (x, y - 1.6))
        cv.pop()


def food_prawn(cv):
    chunk = "M10 30C12 16 22 10 36 14C42 18 40 32 32 38C24 42 12 40 10 30Z"
    cv.path(chunk, rgba(P["prawnPink"]))
    cv.path(chunk, rgba(P["prawnStripe"], 0.6), stroke=1.2)
    for k in range(3):
        x = 17 + k * 7
        cv.arc(rgba(P["prawnStripe"]), 200, 140, (x - 6, 14 + k * 1.5), (12, 22), 2)
    cv.circle(rgba(P["glint"], 0.5), 2.4, (18, 20))


def food_plankton(cv):
    for x, y, r in ((24, 24, 13), (16, 20, 8), (32, 19, 7), (20, 32, 7), (31, 31, 6)):
        cv.circle(rgba(P["phyto"], 0.28), r, (x, y))
    for p in ((18, 23), (27, 18), (30, 28), (21, 30), (25, 25)):
        cv.circle(rgba(P["phyto"]), 1.8, p)
    for p in ((14, 30), (34, 23), (23, 14)):
        cv.circle(rgba(P["phyto"]), 1, p)


POWDER = [(16, 22, 1.4), (20, 18, 1.1), (24, 24, 1.7), (28, 19, 1.2), (31, 25, 1.5), (18, 28, 1.2), (23, 31, 1.4),
          (29, 31, 1.1), (34, 29, 1.0), (14, 17, 0.9), (33, 16, 0.9), (21, 24, 0.9), (27, 27, 1.0), (36, 22, 1.1),
          (12, 26, 1.0), (25, 14, 1.0), (19, 34, 0.9), (31, 35, 0.9)]


def food_powder(cv):
    cv.circle(rgba(P["powderPink"], 0.18), 15, (24, 25))
    for i, (x, y, r) in enumerate(POWDER):
        cv.circle(rgba(P["powderOrange"] if i % 3 == 0 else P["powderPink"]), r, (x, y))


def food_cyclops(cv):
    for x, y, deg in ((15, 17, -20), (31, 14, 15), (21, 32, 25), (35, 31, -10)):
        cv.push(cv.rotated(deg, (x, y)))
        red = rgba(P["cyclopsRed"])
        cv.line(red, (x, y + 2), (x, y + 8), 1.3)
        cv.line(red, (x, y + 8), (x - 1.8, y + 10.5), 1)
        cv.line(red, (x, y + 8), (x + 1.8, y + 10.5), 1)
        cv.circle(red, 3, (x, y))
        cv.circle(rgba(P["cyclopsRed"], 0.7), 1.5, (x - 3.2, y + 3.5))
        cv.circle(rgba(P["cyclopsRed"], 0.7), 1.5, (x + 3.2, y + 3.5))
        cv.circle(rgba(P["eye"]), 0.8, (x, y - 1))
        cv.line(red, (x - 1.5, y - 2.5), (x - 4, y - 5.5), 0.9)
        cv.line(red, (x + 1.5, y - 2.5), (x + 4, y - 5.5), 0.9)
        cv.pop()


FOODS = [
    ("FLAKE", "Flakes", "Dry", food_flakes),
    ("MYSIS", "Mysis shrimp", "Frozen", food_mysis),
    ("PELLET", "Sinking pellets", "Dry", lambda cv: food_pellets(cv, P["pelletBrown"], P["pelletLight"], False)),
    ("NORI", "Nori sheet", "Greens", food_nori),
    ("PODS", "Live pods", "Tiny & live", food_pods),
    ("PRAWN", "Prawn chunk", "Frozen", food_prawn),
    ("PLANKTON", "Phytoplankton", "Tiny & live", food_plankton),
    ("BRINE", "Brine shrimp", "Frozen", food_brine),
    ("KRILL", "Krill", "Frozen", food_krill),
    ("FLOATING_PELLET", "Floating pellets", "Dry", lambda cv: food_pellets(cv, P["pelletLight"], P["flakeTan"], True)),
    ("ALGAE_WAFER", "Algae wafer", "Greens", food_wafer),
    ("CORAL_FOOD", "Coral food", "Tiny & live", food_powder),
    ("CYCLOPS", "Cyclops", "Tiny & live", food_cyclops),
]


# ---------------------------------------------------------------- sheets

def font(size):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def card(img, x, y, w, h, r=14):
    """Glass card: drawn on its own layer and composited, so the translucent fill really is translucent."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([x, y, x + w, y + h], radius=r, fill=(255, 255, 255, 20), outline=(255, 255, 255, 40))
    img.alpha_composite(layer)


def icon_sheet(glyphs, out_png, out_svg, scale):
    cols = 8
    cell, pad, top = 104 * scale, 12 * scale, 54 * scale
    rows = math.ceil(len(glyphs) / cols)
    W = pad + cols * (cell + pad)
    H = top + rows * (cell + 22 * scale + pad)
    img = Image.new("RGBA", (W, H), ABYSS1 + (255,))
    d = ImageDraw.Draw(img)
    d.text((pad, 14 * scale), "ReefLive icon set · Abyss · 24 dp glyphs, 1.75 dp stroke, coral accent", fill=FOAM, font=font(16 * scale))
    f = font(11 * scale)
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
           f'<rect width="100%" height="100%" fill="#061327"/>']
    for n, (name, parts) in enumerate(glyphs):
        cx = pad + (n % cols) * (cell + pad)
        cy = top + (n // cols) * (cell + 22 * scale + pad)
        d = ImageDraw.Draw(img)
        card(img, cx, cy, cell, cell)
        k = (cell * 0.62) / 24
        ox = cx + (cell - 24 * k) / 2
        oy = cy + (cell - 24 * k) / 2
        cv = Canvas(img, ox, oy, k, 24 * k, 24 * k)
        draw_glyph(cv, parts)
        cv.done()
        d = ImageDraw.Draw(img)
        d.text((cx + cell / 2, cy + cell + 4 * scale), name, fill=MIST, font=f, anchor="ma")
        svg.append(f'<rect x="{cx}" y="{cy}" width="{cell}" height="{cell}" rx="14" fill="#ffffff" fill-opacity="0.08" stroke="#ffffff" stroke-opacity="0.16"/>')
        svg.append(f'<g transform="translate({ox} {oy}) scale({k})">{glyph_svg(parts)}</g>')
        svg.append(f'<text x="{cx + cell / 2}" y="{cy + cell + 16 * scale}" font-family="sans-serif" font-size="{11 * scale}" fill="#A7C4D4" text-anchor="middle">{name}</text>')
    svg.append("</svg>")
    img.convert("RGB").save(out_png)
    open(out_svg, "w", encoding="utf-8").write("\n".join(svg))


def food_sheet(out_png, scale):
    cols = 5
    cell, pad, top = 128 * scale, 14 * scale, 54 * scale
    rows = math.ceil(len(FOODS) / cols)
    W = pad + cols * (cell + pad)
    H = top + rows * (cell + 40 * scale + pad)
    img = Image.new("RGBA", (W, H), ABYSS1 + (255,))
    d = ImageDraw.Draw(img)
    d.text((pad, 14 * scale), "ReefLive food pictures · 13 FoodKinds · FoodPalette (matches the tank's particle tints)", fill=FOAM, font=font(16 * scale))
    f1, f2 = font(12 * scale), font(10 * scale)
    for n, (kind, label, group, fn) in enumerate(FOODS):
        cx = pad + (n % cols) * (cell + pad)
        cy = top + (n // cols) * (cell + 40 * scale + pad)
        d = ImageDraw.Draw(img)
        card(img, cx, cy, cell, cell, r=18)
        k = (cell * 0.78) / 48
        ox = cx + (cell - 48 * k) / 2
        oy = cy + (cell - 48 * k) / 2
        cv = Canvas(img, ox, oy, k, 48 * k, 48 * k)
        fn(cv)
        cv.done()
        d = ImageDraw.Draw(img)
        d.text((cx + cell / 2, cy + cell + 5 * scale), label, fill=FOAM, font=f1, anchor="ma")
        d.text((cx + cell / 2, cy + cell + 21 * scale), f"{kind} · {group}", fill=MIST, font=f2, anchor="ma")
    img.convert("RGB").save(out_png)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "ui-graphics"))
    ap.add_argument("--scale", type=int, default=2)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    glyphs = load_glyphs(GLYPHS_KT)
    icon_sheet(glyphs, os.path.join(a.out, "icon-set.png"), os.path.join(a.out, "icon-set.svg"), a.scale)
    food_sheet(os.path.join(a.out, "food-pictures.png"), a.scale)
    print(f"{len(glyphs)} glyphs, {len(FOODS)} foods -> {a.out}")


if __name__ == "__main__":
    sys.exit(main())
