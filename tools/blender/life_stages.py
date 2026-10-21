"""Life-stage body textures (docs/research/life_stages.md; app LifeStageVisuals.kt).

Paints every juvenile / subadult / adult / sex form of each fish on the model's own unrolled body UV layout (the same
Flank as fish_species.py), so the runtime can cross-fade them as a fish grows or changes sex, and writes
`textures/stages/manifest.json` (model -> form -> texture, plus `base`: the form the GLB's embedded body texture already
shows, which is not written again and is drawn at full resolution). The GLBs themselves are unchanged.

  /tmp/claude-0/bpyenv/bin/python tools/blender/life_stages.py              -> textures + manifest
  /tmp/claude-0/bpyenv/bin/python tools/blender/life_stages.py --preview DIR -> per-species renders of every form

Claims tagged in the painters: [M] measured / published (life_stages.md cites them), [L] estimate, [P pNN] Tee's photo.
"""
import argparse
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import fish_species as FS  # noqa: E402
import fishlib as FL  # noqa: E402
import paintlib as P  # noqa: E402
from common import hex2rgb  # noqa: E402

S = P.smoothstep
OUT = os.path.join(C.TEXTURES, "stages")
# stage textures are half the GLB body resolution: juveniles are small on screen, and the APK stays lean
DOWNSAMPLE = 2


def _mask_hue(cv, fn):
    r, g, b = cv.rgb[..., 0], cv.rgb[..., 1], cv.rgb[..., 2]
    return np.clip(fn(r, g, b), 0, 1).astype(np.float32)


def _lighten(cv, amount, color="#FFFFFF"):
    cv.blend(np.ones_like(cv.u), color, amount)


# ---------------------------------------------------------------- clownfish
def clown_orange_juvenile(cv, fl):
    """Juvenile ocellaris: the three white bars are formed by ~15 days after hatching, head first [M Salis 2018],
    but the black edging is thin and the orange paler until the fish grows [L]."""
    cv.vgrad("#F7A057", "#EE7A2E", 0.0, 1.0, 0.8)
    cv.blend((1 - S(0.0, 0.25, cv.v)) * (1 - S(0.0, 0.3, cv.u)), "#F9B880", 0.4)
    FS.mucus(cv, 0.05, 3)
    FS.scales_overlay(cv, 13, 0.05)
    white, black = FS.clown_bars(cv, fl, narrow=0.92)
    cv.blend(black, "#2A1A12", 0.35)
    cv.blend(white, "#F7F7F5")
    FS.dark_mouth(cv, fl, color="#C0561E")


def clown_darwin_juvenile(cv, fl):
    """Black (Darwin) ocellaris start orange-brown and fill in black over ~12-18 months, by about 5 cm; some keep an
    orange face [L: breeder / retailer accounts, ORA; life_stages.md §1]."""
    cv.vgrad("#8C4A1E", "#6A3416", 0.0, 1.0)
    cv.blend(P.value_noise(cv.h, cv.w, 30, 12, 77), "#3A2216", 0.45)   # patchy darkening coming in
    face = 1 - S(0.15, 0.24, cv.u)
    cv.blend(face, "#F07020")
    FS.mucus(cv, 0.06, 4)
    FS.scales_overlay(cv, 13, 0.08)
    white, black = FS.clown_bars(cv, fl, narrow=0.85)
    cv.blend(black, "#1A1210", 0.7)
    cv.blend(white, "#F5F5F5")
    FS.dark_mouth(cv, fl, color="#B4441A")


# ---------------------------------------------------------------- blue tang
def _tang_blue(cv, base_mix, color):
    blue = _mask_hue(cv, lambda r, g, b: (b - np.maximum(r, g) - 0.15) * 4)
    cv.blend(blue, color, base_mix)


def tang_juvenile(cv, fl):
    """Pacific-form juvenile: the adult palette pattern with no yellow belly (Tee's fish, [P p09, p10]); young fish are
    a paler, brighter blue [M Reef Builders 2011 on the Indian Ocean form; L for the Pacific form]."""
    FS.paint_tang(cv, fl, juvenile=False)
    _tang_blue(cv, 0.45, "#5C9CFA")


def tang_adult(cv, fl):
    """Adult: deeper royal blue, palette pattern unchanged, yellow tail [L]."""
    FS.paint_tang(cv, fl, juvenile=False)
    _tang_blue(cv, 0.35, "#1A3EA6")


# ---------------------------------------------------------------- pajama cardinal
def cardinal_juvenile(cv, fl):
    """Juveniles: lighter body and smaller spots, the spots dark/black before they turn red-orange [M FishBase via
    research 02 §3A; L seller account for black spots]."""
    FS.paint_cardinal(cv, fl)
    spots = _mask_hue(cv, lambda r, g, b: ((r - g) - 0.18) * 6) * S(0.47, 0.5, cv.u)
    cv.blend(spots, "#2A2622", 0.9)
    head = 1 - S(0.3, 0.36, cv.u)
    cv.blend(head, "#E8DCA0", 0.25)   # paler head


# ---------------------------------------------------------------- cleaner wrasse
def wrasse_juvenile(cv, fl):
    """Juvenile Labroides dimidiatus: black with a neon blue stripe along the upper side, snout to tail
    [M Fishes of Australia; Reef Life Survey]."""
    u, v = cv.u, cv.v
    cv.vgrad("#141826", "#0B0C12", 0.0, 1.0)
    FS.mucus(cv, 0.03, 31)
    FS.scales_overlay(cv, 10, 0.04)
    cen = np.interp(u, [0.0, 0.1, 0.5, 1.0], [0.70, 0.80, 0.84, 0.80])
    d = np.abs(v - cen) * fl.lv
    hw = np.interp(u, [0.0, 0.2, 0.8, 1.0], [0.006, 0.011, 0.012, 0.009])
    stripe = 1 - S(hw - 0.003, hw + 0.003, d)
    glow = 1 - S(hw, hw + 0.012, d)
    cv.blend(glow, "#1E4FA0", 0.6)
    cv.blend(stripe, "#3C9CFF")
    cv.blend(stripe * (1 - S(0.0, hw * 0.6, d)), "#9FD8FF", 0.6)
    cv.blend(1 - S(0.08, 0.22, v), "#232A40", 0.5)   # dusky belly
    FS.dark_mouth(cv, fl, u1=0.02, v0=0.5, v1=0.56, color="#202020")


# ---------------------------------------------------------------- engineer goby
def goby_juvenile(cv, fl):
    """Striped juvenile: black with a pale stripe from the eye to the tail and a pale dorsal stripe, mimicking the
    venomous striped eel catfish Plotosus lineatus [M Australian Museum; FishBase; Wikipedia]."""
    u, v = cv.u, cv.v
    cv.vgrad("#0E0E0C", "#080807", 0.0, 1.0)
    FS.mucus(cv, 0.03, 41)
    mid = FS.Flank.stroke(fl, [(0.03, 0.56), (0.08, 0.52), (0.3, 0.5), (0.7, 0.5), (0.97, 0.52)],
                          [0.006, 0.0085, 0.009, 0.008, 0.004], soft=0.0025)
    top = FS.Flank.stroke(fl, [(0.02, 0.8), (0.08, 0.9), (0.3, 0.92), (0.7, 0.92), (0.96, 0.9)],
                          [0.004, 0.006, 0.0065, 0.006, 0.003], soft=0.0025)
    low = FS.Flank.stroke(fl, [(0.05, 0.22), (0.3, 0.2), (0.7, 0.2), (0.95, 0.24)],
                          [0.003, 0.004, 0.004, 0.002], soft=0.0025)
    cv.blend(mid, "#F2ECCB")
    cv.blend(top, "#EFE6BE", 0.95)
    cv.blend(low, "#D8D0A8", 0.35)   # a faint lower line, as in the catfish model
    FS.dark_mouth(cv, fl, u1=0.03, v0=0.38, v1=0.44, color="#050505", op=0.7)


def goby_subadult(cv, fl, seed=51):
    """Subadult (Tee's fish, [P p02]): the stripes have broken up; dark chocolate-black saddles above and below the
    lateral line, often joined across it, split by narrow irregular cream-yellow bands; the head stays dark with one
    cream band behind the eye. FishBase/Monterey: at 49-108 mm "their dark stripes break into rows of dark spots" [M]."""
    u, v = cv.u, cv.v
    rs = np.random.default_rng(seed)
    cv.vgrad("#E2D29C", "#D8C88E", 0.0, 1.0)
    dark = np.zeros_like(u)
    x = 0.105
    k = 0
    while x < 0.97:
        w = rs.uniform(0.09, 0.14)     # broad blocks, about eight along the body [P p02]
        gap = rs.uniform(0.018, 0.03)
        c = x + w / 2
        wob = 0.006 * np.sin(v * 9 + k * 1.7)
        # upper saddle and lower blotch, slightly offset, rounded ends; mostly merged across the lateral line [P p02]
        merged = rs.uniform() < 0.65
        split_hi = 0.5 + rs.uniform(-0.03, 0.06)
        split_lo = 0.5 - rs.uniform(0.0, 0.06) if not merged else split_hi
        for (v0, v1, du) in ((split_hi + 0.008, 1.05, 0.0), (-0.05, split_lo - 0.008, rs.uniform(-0.03, 0.03))):
            if v1 <= v0:
                continue
            m = P.band(u, c - w / 2 + du + wob, c + w / 2 + du + wob, 0.004) * P.band(v, v0, v1, 0.02)
            dark = np.maximum(dark, m)
        if merged:
            dark = np.maximum(dark, P.band(u, c - w / 2 + wob, c + w / 2 + wob, 0.004))
        x += w + gap
        k += 1
    # dark head with a cream band just behind the eye
    head = 1 - S(0.055, 0.065, u)
    band = P.band(u, 0.068, 0.085, 0.004) * P.band(v, 0.12, 0.92, 0.04)
    dark = np.maximum(dark, head)
    dark = np.maximum(dark, (1 - S(0.08, 0.1, u)) * (1 - band))
    n = P.value_noise(cv.h, cv.w, 90, 10, seed + 3)
    dark *= S(0.25, 0.45, n + 0.35 + head)
    cv.blend(dark, "#241913")
    cv.blend(dark * P.value_noise(cv.h, cv.w, 40, 8, seed + 4), "#3A2A1C", 0.5)
    cv.blend(head * P.value_noise(cv.h, cv.w, 160, 14, seed + 5) * S(0.6, 0.8, P.value_noise(cv.h, cv.w, 60, 8, seed + 7)),
             "#BFAE7A", 0.35)   # sparse cream flecks on the face
    FS.mucus(cv, 0.03, seed + 6)
    FS.dark_mouth(cv, fl, u1=0.03, v0=0.38, v1=0.44, color="#1A120C", op=0.7)


def goby_fins_juvenile(cv):
    """Juvenile fins: plain black, no yellow ticks (the ticks come with the broken subadult bars) [M photos, FishBase]."""
    FL.paint_fin_slot(cv, 6, base="#0C0C0A", alpha=0.95, rays=0, margin="#D8D0A8", margin_w=0.05, edge_fade=0.1)
    FL.paint_fin_slot(cv, 5, base="#121210", alpha=0.75, rays=4, ray_color="#060605")
    FL.paint_fin_slot(cv, 4, base="#121210", alpha=0.7, rays=8, ray_color="#060605")


def goby_fins_adult(cv):
    """Adult fins: mottled dark brown like the body, pale margin, no bright ticks [L, Monterey Bay Aquarium photos]."""
    def mottle(T, Sx, rgb, a):
        ph = np.abs(((T * 11 + 0.3 * np.sin(T * 23 + Sx * 5)) % 1.0) - 0.5)
        m = (1 - S(0.12, 0.2, ph)) * (1 - S(0.3, 0.8, Sx)) * 0.55
        return rgb * (1 - m[..., None]) + hex2rgb("#B8A472") * m[..., None], a
    FL.paint_fin_slot(cv, 6, base="#33271C", alpha=0.95, rays=0, margin="#B8A878", margin_w=0.05, edge_fade=0.1, spots=mottle)
    FL.paint_fin_slot(cv, 5, base="#2E241A", alpha=0.75, rays=4, ray_color="#1A140E")
    FL.paint_fin_slot(cv, 4, base="#2E241A", alpha=0.7, rays=8, ray_color="#1A140E")


def goby_adult(cv, fl):
    """Adult: eel-like, mottled brown with irregular whitish / yellowish spots and bars, lower contrast than the
    subadult [M Monterey Bay Aquarium; FishBase "barred or spotted"]."""
    u, v = cv.u, cv.v
    cv.vgrad("#4A3828", "#3A2C20", 0.0, 1.0)
    n1 = P.value_noise(cv.h, cv.w, 140, 14, 61)
    n2 = P.value_noise(cv.h, cv.w, 60, 6, 62)
    pale = S(0.58, 0.72, 0.6 * n1 + 0.4 * n2)
    cv.blend(pale, "#C8B47C", 0.85)
    bars = S(0.75, 0.95, np.abs(np.sin(u * math.pi * 26 + 2.0 * n2)))
    cv.blend(bars * S(0.5, 0.65, n1 + 0.2), "#D6C48C", 0.5)
    cv.blend(1 - S(0.05, 0.3, v), "#6A5638", 0.4)
    FS.mucus(cv, 0.04, 63)
    FS.dark_mouth(cv, fl, u1=0.03, v0=0.38, v1=0.44, color="#1A120C", op=0.6)


# ---------------------------------------------------------------- the small ones ([L] differences)
def hawk_juvenile(cv, fl):
    """Juvenile falco: paler, spots smaller and less crowded toward the back [L]."""
    FS.paint_hawkfish(cv, fl)
    red = _mask_hue(cv, lambda r, g, b: ((r - b) - 0.25) * 4)
    cv.blend(red, "#F6E6DC", 0.4)


def lawnmower_juvenile(cv, fl):
    """Juvenile Salarias: paler, more translucent olive with the dark bars clearer [L; no source describes juvenile
    colour (life_stages.md §7)]."""
    FS.paint_lawnmower(cv, fl)
    _lighten(cv, 0.22, "#C8C8A8")
    bars = S(0.7, 0.9, np.abs(np.sin(cv.u * math.pi * 7.5 + 0.4)))
    cv.blend(bars * P.band(cv.v, 0.15, 0.95, 0.05) * S(0.12, 0.2, cv.u), "#4A4A2E", 0.35)


def tailspot_juvenile(cv, fl):
    """Juvenile tailspot: same pattern, paler [L]."""
    FS.paint_tailspot(cv, fl)
    _lighten(cv, 0.15)


def bicolor_juvenile(cv, fl):
    """Juvenile bicolor blenny: no source describes it (research 50 §1b, [UNKNOWN]); painted as the bicolour phase,
    the dark front browner and the rear a paler yellow [L]."""
    FS.paint_bicolor(cv, fl)
    dark = _mask_hue(cv, lambda r, g, b: (0.3 - (r + g + b) / 3) * 5)
    cv.blend(dark, "#4A3A3A", 0.35)
    _lighten(cv, 0.1, "#FFF0B0")


def midas_juvenile(cv, fl):
    """Juvenile midas blenny: paler lemon-yellow before the adult's deep gold (research 50 §2b gives no juvenile
    colour) [L]."""
    FS.paint_midas(cv, fl)
    _lighten(cv, 0.18, "#FFF2A0")


def dottyback_juvenile(cv, fl):
    """Juvenile Electric Indigo: lavender with the black band less sharply defined [L]."""
    FS.paint_dottyback(cv, fl)
    dark = _mask_hue(cv, lambda r, g, b: (0.25 - (r + g + b) / 3) * 6)
    cv.blend(dark, "#4A4E8C", 0.35)
    _lighten(cv, 0.08, "#C8D0FF")


def diamond_juvenile(cv, fl):
    """Juvenile diamond goby: pearly body, orange spots fainter; the long third dorsal spine (< 40 mm SL [M FishBase])
    is a fin shape, not drawn here [L]."""
    FS.paint_diamond_goby(cv, fl)
    orange = _mask_hue(cv, lambda r, g, b: ((r - b) - 0.3) * 4)
    cv.blend(orange, "#F4E6DA", 0.35)


# ---------------------------------------------------------------- blue star leopard wrasse
# the initial-phase female is the GLB's own photo-based painter (fish_species.paint_leopard_wrasse, Tee's fish [P p06])
def leopard_juvenile(cv, fl):
    """Juvenile M. bipartitus: white with orange mottling (the black spots are on the dorsal and anal fins)
    [M seatizens.sc after Lieske & Myers]."""
    cv.vgrad("#F4F0EA", "#ECE4DA", 0.0, 1.0)
    n = P.value_noise(cv.h, cv.w, 70, 18, 201)
    cv.blend(S(0.48, 0.62, n), "#E07A34", 0.85)
    FS.mucus(cv, 0.03, 202)


def leopard_male(cv, fl):
    """Terminal male: dull orange-red, dark-edged green diagonal bands on the head, a light yellow-orange area under
    the soft dorsal onto the peduncle, blue-edged dark red blotches forming a band on the rear flank [M seatizens.sc]."""
    u, v = cv.u, cv.v
    cv.vgrad("#A85034", "#B8603C", 0.0, 1.0)
    for k in range(4):
        c0 = 0.03 + 0.05 * k
        b = FS.Flank.stroke(fl, [(c0, 0.2), (c0 + 0.06, 0.85)], [0.007, 0.007], soft=0.003)
        e = FS.Flank.stroke(fl, [(c0, 0.2), (c0 + 0.06, 0.85)], [0.011, 0.011], soft=0.003)
        cv.blend(e * (1 - S(0.24, 0.28, u)), "#1A2418")
        cv.blend(b * (1 - S(0.24, 0.28, u)), "#4CAA5A")
    cv.blend(S(0.58, 0.7, u) * S(0.6, 0.75, v), "#F0C060", 0.8)
    rs = np.random.default_rng(205)
    m, pts = fl.spots(rs, 14, 0.012, 0.02, 0.035, (0.5, 0.92, 0.3, 0.6))
    ring = np.zeros_like(u)
    for (pu, pv, r) in pts:
        ring = np.maximum(ring, fl.disc(pu, pv, r + 0.004) - fl.disc(pu, pv, r))
    cv.blend(ring, "#3A8EF0")
    cv.blend(m, "#6A1A18")
    FS.mucus(cv, 0.03, 206)


# ---------------------------------------------------------------- Zebrasoma night colours, fairy wrasse (research 42)
def yellow_tang_night(cv, fl):
    """Night: the yellow dulls to a brownish grey-yellow and a broad white band shows along the mid-flank [M ADW S18:
    'darker/grayer yellow with a white lateral stripe'; 'brown' in another account]."""
    FS.paint_yellow_tang(cv, fl)
    u, v = cv.u, cv.v
    cv.blend(np.ones_like(u), "#8A7238", 0.6)
    band = P.band(v, 0.4, 0.58, 0.04) * S(0.22, 0.32, u) * (1 - S(0.8, 0.88, u))
    cv.blend(band, "#D8D4C4", 0.55)
    cv.blend(fl.disc(0.885, 0.5, 0.022, 0.006), "#F6F4EC", 0.9)


def purple_tang_night(cv, fl):
    """Night: darker and duller, with a faint pale mid-flank band like other Zebrasoma [L]."""
    FS.paint_purple_tang(cv, fl)
    u, v = cv.u, cv.v
    body = 1 - S(0.96, 0.98, u)
    cv.blend(body, "#1A1440", 0.45)
    cv.blend(P.band(v, 0.42, 0.56, 0.04) * S(0.25, 0.35, u) * (1 - S(0.78, 0.86, u)), "#8A80B0", 0.35)


def fairy_juvenile(cv, fl):
    """Juvenile Cirrhilabrus: plain pale orange-pink with a white belly and a dark spot on the upper peduncle [L: common
    to juvenile fairy wrasses]."""
    FS.paint_fairy_female(cv, fl)
    cv.blend(np.ones_like(cv.u), "#F2C4A8", 0.35)
    cv.blend(fl.disc(0.88, 0.7, 0.02, 0.006), "#3A2020", 0.7)


# model -> (species id, {form: painter}); the adult forms reuse the GLB's own painter
FORMS = {
    "clown_orange": ("amphiprion_ocellaris", {"juvenile": clown_orange_juvenile, "adult": FS.paint_clown_orange}),
    "clown_darwin": ("amphiprion_ocellaris", {"juvenile": clown_darwin_juvenile, "adult": FS.paint_clown_darwin}),
    "tang": ("paracanthurus_hepatus", {"juvenile": tang_juvenile, "adult": tang_adult}),
    "cardinal": ("sphaeramia_nematoptera", {"juvenile": cardinal_juvenile, "adult": FS.paint_cardinal}),
    "wrasse": ("labroides_dimidiatus", {"juvenile": wrasse_juvenile, "adult": FS.paint_wrasse}),
    "engineer_goby": ("pholidichthys_leucotaenia", {"juvenile": goby_juvenile, "subadult": goby_subadult, "adult": goby_adult}),
    "hawkfish": ("cirrhitichthys_falco", {"juvenile": hawk_juvenile, "adult": FS.paint_hawkfish}),
    "lawnmower_blenny": ("salarias_fasciatus", {"juvenile": lawnmower_juvenile, "adult": FS.paint_lawnmower}),
    "tailspot_blenny": ("ecsenius_stigmatura", {"juvenile": tailspot_juvenile, "adult": FS.paint_tailspot}),
    "bicolor_blenny": ("ecsenius_bicolor", {"juvenile": bicolor_juvenile, "adult": FS.paint_bicolor}),
    "midas_blenny": ("ecsenius_midas", {"juvenile": midas_juvenile, "adult": FS.paint_midas}),
    "dottyback": ("pseudochromis_indigo", {"juvenile": dottyback_juvenile, "adult": FS.paint_dottyback}),
    "diamond_goby": ("valenciennea_puellaris", {"juvenile": diamond_juvenile, "adult": FS.paint_diamond_goby}),
    "leopard_wrasse": ("macropharyngodon_bipartitus", {"juvenile": leopard_juvenile, "female": FS.paint_leopard_wrasse, "male": leopard_male}),
    "yellow_tang": ("zebrasoma_flavescens", {"adult": FS.paint_yellow_tang, "night": yellow_tang_night}),
    "purple_tang": ("zebrasoma_xanthurum", {"adult": FS.paint_purple_tang, "night": purple_tang_night}),
    "fairy_wrasse": ("cirrhilabrus_rubripinnis", {"juvenile": fairy_juvenile, "female": FS.paint_fairy_female, "male": FS.paint_fairy_male}),
}
# model -> {form: fin-sheet painter}; the form whose painter is the GLB's own fin painter is the fin `base`
FIN_FORMS = {
    "engineer_goby": {"juvenile": goby_fins_juvenile, "subadult": FS.fins_engineer_goby, "adult": goby_fins_adult},
    # the GLB geometry has the male's long fins, its own fin sheet cuts them back (female); the male sheet shows them
    "fairy_wrasse": {"juvenile": FS.fins_fairy_female, "female": FS.fins_fairy_female, "male": FS.fins_fairy_male},
}
# models whose GLB is not built yet: only flat previews are painted, on the cleaner wrasse's profile (none at present)
PENDING_MODELS = {}


def species_sheet(sid):
    p = os.path.join(C.REPO, "core", "src", "main", "resources", "species", sid + ".json")
    return json.load(open(p)) if os.path.exists(p) else None


def paint(model, painter, prof_model=None):
    sp = FS.SPECIES[prof_model or model]
    prof = FL.Profile(sp["profile"])
    tw, th = sp.get("tex", FL.BODY_TEX)
    cv = P.Canvas(tw, th)
    painter(cv, FS.Flank(cv, prof))
    return cv


def paint_fins(painter):
    fc = P.Canvas(*FL.FIN_TEX, color="#808080", alpha=0.0)
    painter(fc)
    return fc


def build(models=None):
    os.makedirs(OUT, exist_ok=True)
    manifest = {"note": "Generated by tools/blender/life_stages.py (docs/research/life_stages.md). model -> form -> body texture.",
                "models": {}, "pending": {}}
    old = os.path.join(OUT, "manifest.json")
    if models and os.path.exists(old):
        manifest["models"] = json.load(open(old)).get("models", {})   # rebuilding a subset keeps the others
    todo = dict(FORMS)
    for m, entry in PENDING_MODELS.items():
        if m in FS.SPECIES and os.path.exists(os.path.join(C.MODELS, m + ".glb")):
            todo[m] = entry
        else:
            manifest["pending"][m] = {"species": entry[0], "forms": sorted(entry[1]), "todo": "model not built yet"}
    for model, (sid, forms) in todo.items():
        if models and model not in models:
            continue
        out = {}
        base = None
        for form, painter in forms.items():
            if painter is FS.SPECIES.get(model, {}).get("paint"):
                base = form          # the GLB's own embedded body texture: drawn as is, at full resolution
                continue
            cv = paint(model, painter).downsample(DOWNSAMPLE)
            path = os.path.join(OUT, f"{model}_{form}.png")
            cv.save(path)
            out[form] = f"textures/stages/{model}_{form}.png"
            print(f"[stages] {model}/{form}: {os.path.getsize(path) // 1024} KB")
        stale = os.path.join(OUT, f"{model}_{base}.png") if base else None
        if stale and os.path.exists(stale):
            os.remove(stale)
        entry = {"species": sid, "base": base, "forms": out}
        if model in FIN_FORMS:
            fout, fbase = {}, None
            for form, painter in FIN_FORMS[model].items():
                if painter is FS.SPECIES.get(model, {}).get("fins"):
                    fbase = form
                    continue
                fc = paint_fins(painter).downsample(DOWNSAMPLE)
                path = os.path.join(OUT, f"{model}_fin_{form}.png")
                fc.save(path, alpha=True)
                fout[form] = f"textures/stages/{model}_fin_{form}.png"
                print(f"[stages] {model}/fin {form}: {os.path.getsize(path) // 1024} KB")
            entry["fins"] = {"base": fbase, "forms": fout}
        manifest["models"][f"models/{model}.glb"] = entry
    with open(os.path.join(OUT, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    return manifest


# ---------------------------------------------------------------- previews
def stage_shapes(sid):
    """form -> (depthScale, widthScale) from the sheet's lifeStages (first stage with that form)."""
    sh = species_sheet(sid)
    out = {}
    if sh and "lifeStages" in sh:
        for st in sh["lifeStages"]["stages"]:
            out.setdefault(st.get("form", st["id"]), (st.get("depthScale", 1.0), st.get("widthScale", 1.0)))
    return out


def render_form(model, form, tex_path, shape, w=560, h=300, fin_path=None):
    import preview as PV
    from mathutils import Vector
    C.reset_scene()
    PV.setup_cycles(w, h, 40)
    PV.world((0.16, 0.2, 0.24), 1.0)
    objs = PV.import_glb(os.path.join(C.MODELS, f"{model}.glb"))
    img = C.load_image(tex_path, name=f"stage_{model}_{form}") if tex_path else None   # None: the GLB's own texture
    fimg = C.load_image(fin_path, name=f"stage_{model}_fin_{form}") if fin_path else None
    for o in objs:
        if o.type != 'MESH':
            continue
        for slot in o.material_slots:
            mat = slot.material
            if not (mat and mat.node_tree):
                continue
            swap = img if mat.name.startswith(f"{model}_body") else fimg if mat.name.startswith(f"{model}_fin") else None
            if swap is None:
                continue
            for nd in mat.node_tree.nodes:
                if nd.type == 'TEX_IMAGE':
                    nd.image = swap
    # body proportions as the shader applies them: glTF Y (depth) -> Blender Z, glTF Z (width) -> Blender -Y
    root = [o for o in objs if o.parent is None]
    for r in root:
        r.scale = (r.scale[0], r.scale[1] * shape[1], r.scale[2] * shape[0])
    PV.add_light('SUN', (0, 0, 5), 3.5, rot=(math.radians(-25), math.radians(10), 0), size=8)
    PV.add_light('AREA', (0.3, -1.2, 0.6), 30, size=1.5, color=(0.9, 0.95, 1.0))
    PV.add_light('AREA', (-0.8, 0.8, 0.3), 12, size=1.0, color=(0.8, 0.9, 1.0))
    import bpy
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for o in objs if o.type == 'MESH' for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    ctr = (lo + hi) / 2
    ext = hi - lo
    yaw, elev = math.radians(-12), 0.12
    d = max(ext.x, ext.y, ext.z) * 3 + 1
    loc = ctr + Vector((d * math.sin(yaw) * math.cos(elev), -d * math.cos(yaw) * math.cos(elev), d * math.sin(elev)))
    span = max(ext.x, ext.z * (w / h) * 1.1) * 1.12
    PV.camera(tuple(loc), tuple(ctr), lens=50, ortho=span)
    return PV.render_to(os.path.join(C.BUILD, f"stage_{model}_{form}.png"))


def previews(outdir, models=None):
    os.makedirs(outdir, exist_ok=True)
    index = {}
    for model, (sid, forms) in FORMS.items():
        if models and model not in models:
            continue
        shapes = stage_shapes(sid)
        cells = []
        for form in forms:
            tex = os.path.join(OUT, f"{model}_{form}.png")
            fin = os.path.join(OUT, f"{model}_fin_{form}.png")
            px = render_form(model, form, tex if os.path.exists(tex) else None, shapes.get(form, (1.0, 1.0)),
                             fin_path=fin if os.path.exists(fin) else None)
            p = os.path.join(outdir, f"_cell_{model}_{form}.png")
            C.write_png(p, px[..., :3])
            cells.append(p)
        index[model] = {"species": sid, "forms": list(forms), "cells": cells}
    # pending species: flat unrolled swatches of every form on a stand-in profile
    for model, (sid, forms) in PENDING_MODELS.items():
        cells = []
        for form, painter in forms.items():
            cv = paint(model, painter, prof_model=model if model in FS.SPECIES else "wrasse")
            p = os.path.join(outdir, f"_cell_{model}_{form}.png")
            cv.save(p)
            cells.append(p)
        index[model] = {"species": sid, "forms": list(forms), "cells": cells, "flat": True}
    with open(os.path.join(outdir, "_index.json"), "w") as f:
        json.dump(index, f, indent=1)
    return index


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", default=None, help="directory for per-form renders")
    ap.add_argument("--models", default=None, help="comma list of models (default all)")
    ap.add_argument("--no-build", action="store_true")
    a = ap.parse_args(argv)
    ms = a.models.split(",") if a.models else None
    if not a.no_build:
        build(ms)
    if a.preview:
        previews(a.preview, ms)
