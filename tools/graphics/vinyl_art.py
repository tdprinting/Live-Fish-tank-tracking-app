#!/usr/bin/env python3
"""
Record sleeves for the tank radio: one vinyl record per RadioStation song, a black grooved disc with a big round
label painted with that song's little scene (palm tree, turtle, plane, ...). Drawn from shapes, no downloads.

    app/src/main/res/drawable-nodpi/vinyl_<name>.webp   (256 px, transparent corners; the web build copies them)

  python3 tools/graphics/vinyl_art.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
S = 1024          # drawn 4x, then scaled down for smooth edges
SIZE = 256
C = S / 2
LABEL = 0.30 * S  # label radius


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def L(x, y):
    """Label coordinates (-1..1 across the label) to pixels."""
    return (C + x * LABEL, C + y * LABEL)


def poly(d, pts, fill):
    d.polygon([L(x, y) for x, y in pts], fill=fill)


def disc(d, x, y, r, fill):
    cx, cy = L(x, y)
    d.ellipse([cx - r * LABEL, cy - r * LABEL, cx + r * LABEL, cy + r * LABEL], fill=fill)


def rect(d, x0, y0, x1, y1, fill):
    d.rectangle([*L(x0, y0), *L(x1, y1)], fill=fill)


def line(d, pts, fill, w):
    d.line([L(x, y) for x, y in pts], fill=fill, width=int(w * LABEL), joint="curve")


def sky(img, top, bottom, horizon=None, sea=None, sea_deep=None):
    """Label background: a sky gradient, and the sea below [horizon] (label y, -1..1)."""
    g = Image.new("RGB", (S, S))
    gd = ImageDraw.Draw(g)
    for py in range(int(C - LABEL), int(C + LABEL) + 1):
        y = (py - C) / LABEL
        if horizon is not None and y > horizon:
            col = lerp(sea, sea_deep, (y - horizon) / (1 - horizon + 1e-6))
        else:
            col = lerp(top, bottom, (y + 1) / ((horizon if horizon is not None else 1) + 1))
        gd.line([(0, py), (S, py)], fill=col)
    img.paste(g, (0, 0))


def waves(d, y, col, n=5, amp=0.04, w=0.035):
    pts = [(x / 40 * 2 - 1, y + amp * math.sin(x / 40 * n * math.pi * 2)) for x in range(41)]
    line(d, pts, col, w)


def palm(d, x, y, h, trunk=(120, 80, 40), leaf=(40, 150, 70)):
    pts = [(x + 0.08 * math.sin(t / 10 * 1.2), y - h * t / 10) for t in range(11)]
    line(d, pts, trunk, 0.06)
    tx, ty = pts[-1]
    for a in (-160, -120, -60, -20, 200, 90):
        r = math.radians(a)
        line(d, [(tx, ty), (tx + 0.3 * math.cos(r), ty + 0.3 * math.sin(r) * 0.6 + 0.08)], leaf, 0.07)


def sun(d, x, y, r, col):
    disc(d, x, y, r, col)


def fish(d, x, y, s, body, stripe=None, flip=False):
    k = -1 if flip else 1
    disc(d, x, y, 0.0, body)
    cx, cy = L(x, y)
    d.ellipse([cx - s * LABEL, cy - s * 0.6 * LABEL, cx + s * LABEL, cy + s * 0.6 * LABEL], fill=body)
    poly(d, [(x - k * s * 0.9, y), (x - k * s * 1.5, y - s * 0.5), (x - k * s * 1.5, y + s * 0.5)], body)
    if stripe:
        for dx in (-0.35, 0.25):
            rect(d, x + k * dx * s - 0.12 * s, y - 0.55 * s, x + k * dx * s + 0.12 * s, y + 0.55 * s, stripe)
    disc(d, x + k * s * 0.55, y - s * 0.1, s * 0.1, (20, 20, 30))


def star5(d, x, y, r, col, inner=0.45, rot=-90):
    pts = []
    for i in range(10):
        a = math.radians(rot + i * 36)
        rr = r if i % 2 == 0 else r * inner
        pts.append((x + rr * math.cos(a), y + rr * math.sin(a)))
    poly(d, pts, col)


# ------------------------------------------------------------------ one scene per song
def coral_morning(img, d):
    sky(img, (255, 190, 140), (255, 230, 170), 0.15, (40, 150, 200), (20, 70, 140))
    sun(d, 0.0, 0.1, 0.32, (255, 245, 200))
    for x, h, col in ((-0.45, 0.55, (255, 110, 140)), (0.0, 0.7, (255, 150, 90)), (0.45, 0.5, (230, 90, 170))):
        line(d, [(x, 0.95), (x, 0.95 - h)], col, 0.08)
        line(d, [(x, 0.95 - h * 0.5), (x - 0.15, 0.95 - h * 0.8)], col, 0.07)
        line(d, [(x, 0.95 - h * 0.6), (x + 0.15, 0.95 - h * 0.9)], col, 0.07)


def aloha_lagoon(img, d):
    sky(img, (120, 210, 255), (200, 240, 255), 0.25, (40, 200, 200), (20, 120, 160))
    sun(d, 0.45, -0.45, 0.18, (255, 230, 90))
    disc(d, -0.2, 0.75, 0.55, (240, 215, 150))
    palm(d, -0.25, 0.45, 0.85)


def tide_pool(img, d):
    sky(img, (70, 160, 210), (40, 120, 180))
    for y in (-0.45, -0.1, 0.25):
        waves(d, y, (200, 240, 255), n=3, amp=0.06)
    rect(d, -1, 0.5, 1, 1, (210, 180, 130))
    star5(d, 0.0, 0.62, 0.3, (255, 120, 80))


def clownfish_waltz(img, d):
    sky(img, (60, 170, 220), (20, 90, 160))
    fish(d, 0.05, 0.0, 0.45, (255, 130, 30), (255, 255, 255))
    for x, y, r in ((-0.55, -0.5, 0.06), (-0.45, -0.7, 0.04), (0.6, 0.55, 0.05)):
        disc(d, x, y, r, (220, 245, 255))


def hammock_ukulele(img, d):
    sky(img, (255, 210, 120), (255, 160, 110))
    disc(d, 0.0, 0.3, 0.38, (190, 120, 60))
    disc(d, 0.0, -0.05, 0.3, (190, 120, 60))
    disc(d, 0.0, 0.2, 0.1, (70, 40, 20))
    rect(d, -0.06, -0.85, 0.06, 0.0, (120, 70, 30))
    for k in (-0.03, 0.0, 0.03):
        line(d, [(k, -0.8), (k, 0.5)], (250, 240, 220), 0.008)


def moonlit_drift(img, d):
    sky(img, (20, 30, 80), (50, 60, 130), 0.4, (20, 40, 90), (10, 20, 50))
    disc(d, 0.2, -0.35, 0.3, (250, 240, 200))
    disc(d, 0.33, -0.43, 0.27, lerp((20, 30, 80), (50, 60, 130), 0.3))
    for x, y in ((-0.5, -0.5), (-0.3, -0.1), (-0.65, 0.1), (0.6, 0.1), (0.0, -0.75)):
        star5(d, x, y, 0.06, (255, 250, 210))
    waves(d, 0.55, (90, 110, 180), n=4, amp=0.03)


def window_seat(img, d):
    sky(img, (110, 190, 255), (220, 240, 255), 0.45, (30, 150, 210), (20, 90, 160))
    for x, y in ((-0.5, -0.1), (0.5, 0.15)):
        for dx, r in ((-0.12, 0.12), (0.0, 0.16), (0.14, 0.11)):
            disc(d, x + dx, y, r, (255, 255, 255))
    # the plane
    poly(d, [(-0.6, -0.42), (0.55, -0.42), (0.7, -0.36), (0.55, -0.3), (-0.6, -0.3)], (245, 245, 250))
    poly(d, [(-0.05, -0.36), (0.15, -0.36), (-0.15, -0.0), (-0.3, -0.0)], (210, 215, 230))
    poly(d, [(-0.6, -0.42), (-0.45, -0.42), (-0.6, -0.62), (-0.7, -0.62)], (230, 80, 80))
    for x in (-0.3, -0.15, 0.0, 0.15, 0.3):
        disc(d, x, -0.37, 0.025, (60, 120, 200))
    disc(d, 0.0, 0.75, 0.25, (240, 215, 150))
    palm(d, 0.0, 0.6, 0.3)


def five_oclock_dock(img, d):
    sky(img, (255, 120, 90), (255, 200, 110), 0.3, (60, 90, 160), (30, 50, 110))
    sun(d, 0.0, 0.3, 0.32, (255, 230, 120))
    rect(d, -1, 0.5, 1, 0.62, (140, 90, 50))
    for x in (-0.7, -0.2, 0.3, 0.8):
        rect(d, x - 0.04, 0.5, x + 0.04, 0.95, (110, 70, 40))
    # a clock that says five
    disc(d, -0.38, -0.38, 0.26, (255, 250, 235))
    line(d, [(-0.38, -0.38), (-0.38, -0.58)], (60, 40, 30), 0.04)
    line(d, [(-0.38, -0.38), (-0.29, -0.22)], (60, 40, 30), 0.05)


def sandcastle_sunshine(img, d):
    sky(img, (110, 200, 255), (190, 235, 255), 0.25, (40, 180, 220), (30, 140, 200))
    sun(d, -0.5, -0.5, 0.2, (255, 225, 70))
    rect(d, -1, 0.45, 1, 1, (245, 210, 140))
    rect(d, -0.45, 0.0, 0.45, 0.55, (225, 180, 110))
    for x in (-0.45, 0.15):
        rect(d, x, -0.3, x + 0.3, 0.05, (225, 180, 110))
        for k in range(3):
            rect(d, x + k * 0.11, -0.4, x + k * 0.11 + 0.07, -0.3, (225, 180, 110))
    rect(d, -0.08, 0.3, 0.08, 0.55, (150, 110, 60))
    line(d, [(0.3, -0.4), (0.3, -0.7)], (90, 60, 40), 0.02)
    poly(d, [(0.3, -0.7), (0.5, -0.63), (0.3, -0.56)], (255, 80, 90))


def turtle_glide(img, d):
    sky(img, (40, 170, 200), (20, 90, 150))
    for dx, dy, a in ((-0.42, -0.3, -0.5), (0.42, -0.3, 0.5), (-0.36, 0.38, 0.4), (0.36, 0.38, -0.4)):
        cx, cy = L(dx, dy)
        w, h = 0.32 * LABEL, 0.14 * LABEL
        leaf = Image.new("RGBA", (int(w * 2), int(h * 2)))
        ImageDraw.Draw(leaf).ellipse([0, 0, w * 2, h * 2], fill=(110, 170, 90))
        leaf = leaf.rotate(math.degrees(a), expand=True)
        img.paste(leaf, (int(cx - leaf.width / 2), int(cy - leaf.height / 2)), leaf)
    disc(d, 0.0, -0.55, 0.15, (120, 180, 100))
    disc(d, 0.0, 0.05, 0.45, (90, 120, 60))
    for x, y in ((0, 0.05), (-0.22, -0.12), (0.22, -0.12), (-0.22, 0.22), (0.22, 0.22)):
        disc(d, x, y, 0.13, (150, 170, 80))
    disc(d, -0.06, -0.6, 0.03, (20, 20, 20))
    disc(d, 0.06, -0.6, 0.03, (20, 20, 20))


def sea_breeze_bossa(img, d):
    sky(img, (150, 220, 255), (230, 245, 255), 0.35, (40, 150, 200), (20, 90, 150))
    poly(d, [(0.05, -0.75), (0.05, 0.25), (0.6, 0.25)], (255, 255, 255))
    poly(d, [(-0.02, -0.55), (-0.02, 0.25), (-0.45, 0.25)], (255, 200, 90))
    line(d, [(0.02, -0.8), (0.02, 0.35)], (110, 70, 40), 0.03)
    poly(d, [(-0.55, 0.32), (0.65, 0.32), (0.45, 0.5), (-0.4, 0.5)], (200, 60, 60))
    waves(d, 0.65, (200, 240, 255), n=4, amp=0.03)


def starfish_lullaby(img, d):
    sky(img, (70, 50, 140), (140, 90, 190))
    star5(d, 0.0, 0.1, 0.55, (255, 140, 170), inner=0.4)
    for x, y in ((-0.6, -0.55), (0.55, -0.6), (0.65, 0.5), (-0.6, 0.55)):
        star5(d, x, y, 0.07, (255, 245, 200))
    disc(d, -0.12, 0.0, 0.04, (60, 30, 60))
    disc(d, 0.12, 0.0, 0.04, (60, 30, 60))


def tiki_twilight(img, d):
    sky(img, (90, 50, 140), (255, 140, 90), 0.5, (60, 50, 110), (30, 30, 70))
    for x in (-0.45, 0.45):
        rect(d, x - 0.04, -0.2, x + 0.04, 0.85, (120, 80, 45))
        rect(d, x - 0.1, -0.35, x + 0.1, -0.2, (150, 100, 55))
        poly(d, [(x - 0.1, -0.35), (x, -0.75), (x + 0.1, -0.35)], (255, 160, 40))
        poly(d, [(x - 0.05, -0.35), (x, -0.58), (x + 0.05, -0.35)], (255, 235, 120))
    # a tiki face in the middle
    rect(d, -0.18, -0.25, 0.18, 0.6, (160, 110, 60))
    rect(d, -0.12, -0.1, -0.04, -0.02, (40, 25, 15))
    rect(d, 0.04, -0.1, 0.12, -0.02, (40, 25, 15))
    rect(d, -0.1, 0.2, 0.1, 0.3, (40, 25, 15))


def kelp_forest_sway(img, d):
    sky(img, (40, 140, 120), (10, 60, 70))
    for x, ph, col in ((-0.6, 0.0, (90, 160, 60)), (-0.2, 1.0, (120, 180, 70)), (0.2, 2.0, (80, 150, 50)), (0.6, 3.0, (110, 170, 60))):
        pts = [(x + 0.08 * math.sin(t / 6 + ph), 1 - t / 10 * 1.9) for t in range(11)]
        line(d, pts, col, 0.07)
        for t in range(2, 10, 2):
            px, py = pts[t]
            disc(d, px + 0.07, py, 0.06, col)
    fish(d, 0.0, -0.45, 0.14, (255, 210, 80))


def postcard_reef(img, d):
    sky(img, (250, 240, 220), (240, 225, 200))
    rect(d, -0.6, -0.45, 0.6, 0.45, (60, 170, 210))
    rect(d, -0.6, 0.15, 0.6, 0.45, (40, 120, 180))
    fish(d, -0.15, -0.1, 0.18, (255, 150, 40), (255, 255, 255))
    fish(d, 0.3, 0.15, 0.12, (90, 110, 255), flip=True)
    rect(d, 0.35, -0.42, 0.55, -0.2, (230, 80, 90))  # the stamp
    for y in (0.6, 0.72):
        line(d, [(-0.4, y), (0.4, y)], (150, 130, 110), 0.02)


def dolphin_dance(img, d):
    sky(img, (110, 200, 255), (200, 240, 255), 0.35, (30, 140, 210), (20, 80, 150))
    grey, belly = (110, 135, 170), (200, 215, 230)
    # a leap: the body follows an arch, thickest in the middle
    path = [(-0.62 + 1.2 * t, 0.42 - 0.95 * math.sin(math.pi * t) + 0.25 * t) for t in (i / 90 for i in range(91))]
    for i, (x, y) in enumerate(path):
        t = i / 90
        disc(d, x, y, 0.035 + 0.13 * math.sin(math.pi * min(1, t * 1.15)), grey)
    poly(d, [(-0.05, -0.5), (0.12, -0.5), (-0.1, -0.72)], grey)                  # dorsal fin
    tx, ty = path[0]
    poly(d, [(tx, ty), (tx - 0.2, ty + 0.05), (tx - 0.12, ty - 0.08)], grey)   # tail flukes
    poly(d, [(tx, ty), (tx + 0.02, ty + 0.2), (tx - 0.1, ty + 0.14)], grey)
    hx, hy = path[-1]
    poly(d, [(hx - 0.04, hy - 0.04), (hx + 0.16, hy + 0.08), (hx - 0.02, hy + 0.06)], grey)  # beak
    disc(d, hx - 0.06, hy - 0.06, 0.028, (20, 20, 30))
    for x in (-0.7, 0.72):
        disc(d, x, 0.5, 0.06, (230, 250, 255))
        disc(d, x + 0.08, 0.44, 0.04, (230, 250, 255))


ART = {
    "coral_morning": coral_morning, "aloha_lagoon": aloha_lagoon, "tide_pool": tide_pool,
    "clownfish_waltz": clownfish_waltz, "hammock_ukulele": hammock_ukulele, "moonlit_drift": moonlit_drift,
    "window_seat": window_seat, "five_oclock_dock": five_oclock_dock, "sandcastle_sunshine": sandcastle_sunshine,
    "turtle_glide": turtle_glide, "sea_breeze_bossa": sea_breeze_bossa, "starfish_lullaby": starfish_lullaby,
    "tiki_twilight": tiki_twilight, "kelp_forest_sway": kelp_forest_sway, "postcard_reef": postcard_reef,
    "dolphin_dance": dolphin_dance,
}


def record(scene):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    R = 0.49 * S
    d.ellipse([C - R, C - R, C + R, C + R], fill=(18, 18, 22))
    # grooves
    r = R - 8
    i = 0
    while r > LABEL + 10:
        shade = 30 + (i % 3) * 6
        d.ellipse([C - r, C - r, C + r, C + r], outline=(shade, shade, shade + 4), width=2)
        r -= 9
        i += 1
    # a soft sheen across the grooves
    sheen = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sheen)
    for a0 in (-60, 120):
        sd.pieslice([C - R, C - R, C + R, C + R], a0, a0 + 35, fill=(255, 255, 255, 34))
    sheen = sheen.filter(ImageFilter.GaussianBlur(14))
    img.alpha_composite(sheen)
    # the label: paint the scene on its own layer, then cut it to a circle
    lab = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    scene(lab, ImageDraw.Draw(lab))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([C - LABEL, C - LABEL, C + LABEL, C + LABEL], fill=255)
    img.paste(lab, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.ellipse([C - LABEL, C - LABEL, C + LABEL, C + LABEL], outline=(250, 245, 230), width=10)
    hole = 0.018 * S
    d.ellipse([C - hole * 1.6, C - hole * 1.6, C + hole * 1.6, C + hole * 1.6], fill=(250, 245, 230))
    d.ellipse([C - hole, C - hole, C + hole, C + hole], fill=(18, 18, 22))
    # the disc's outer edge clean against the transparent corners
    edge = Image.new("L", (S, S), 0)
    ImageDraw.Draw(edge).ellipse([C - R, C - R, C + R, C + R], fill=255)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (S, S), 0), edge))
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    for name, fn in ART.items():
        out = os.path.join(OUT, f"vinyl_{name}.webp")
        record(fn).save(out, "WEBP", quality=86, method=6)
        print(f"vinyl_{name}.webp  {os.path.getsize(out) / 1024:.1f} KB")


if __name__ == "__main__":
    main()
