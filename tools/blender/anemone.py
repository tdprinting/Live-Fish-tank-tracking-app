"""Rainbow bubble-tip anemone (Entacmaea quadricolor, RBTA; research 40). Builds models/bubble_tip_anemone.glb.

    /tmp/claude-0/bpyenv/bin/python tools/blender/anemone.py            # GLB
    /tmp/claude-0/bpyenv/bin/python tools/blender/anemone.py --preview  # + Cycles renders of both tentacle states

Centimetres, Y up, origin at the centre of the oral disc; the column hangs below (-Y) into the crevice it sits in.
Root extras.lengthCm = tentacle span the model is built at (the runtime scales by organism length / lengthCm).

Two polyp nodes, both drawn by polyp.vert in anemone mode (uSwayMode 2):
  Disc      : oral disc + column, one instance. COLOR_0 r = fluorescence mask, g = 1 (disc flag), b = 0.
  Tentacles : ONE tentacle prototype (axis +Y, base at the origin, unit length) instanced over the disc
              (extras.instances, stride 10 like the corals: pos | quat | scale = length cm | phase | variant).
              COLOR_0 r = 0, g = 0, b = hinge (0 base .. 1 tip). The shader rebuilds the radius profile from the hinge
              (stringy <-> bubbled, `tentacle_profile` below is the same formula), so the baked radius is only what
              tools without the shader see.
"""
import argparse
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import paintlib as P  # noqa: E402
import tank as T  # noqa: E402
from common import bpy  # noqa: E402

S = P.smoothstep
OUT = os.path.join(C.MODELS, "bubble_tip_anemone.glb")
DISC_R = 4.2          # oral disc radius, cm
SPAN = 18.0           # tentacle span the model is built at (cm)
N_TENT = 72           # adult count is an assumption (research 40 §1.2: ~20 young, rising with age)
COL_DEPTH = 5.5       # column below the disc, cm (hidden in the crevice)
RINGS = (0.0, 0.05, 0.12, 0.22, 0.34, 0.46, 0.57, 0.67, 0.76, 0.84, 0.9, 0.945, 0.975, 1.0)
SIDES = 8


# ----------------------------------------------------------------------------------------------------------------
# tentacle shape (mirrored in polyp.vert anemoneProfile)
# ----------------------------------------------------------------------------------------------------------------
def tentacle_profile(h, bubble, bp):
    """Radius (fraction of the stringy length) and length factor at hinge h for bubble 0..1, bulb centre bp."""
    h = np.asarray(h, float)
    lf = 1.0 - 0.38 * bubble
    rs = 0.085 * (1.0 - 0.5 * h)
    bulb = np.exp(-((h - bp) / 0.22) ** 2)
    rb = 0.075 * (1.0 - 0.3 * h) + 0.09 * bulb
    nip = S(bp + 0.14, bp + 0.3, h)
    rb = rb * (1 - nip) + 0.05 * nip
    r = rs + (rb - rs) * bubble
    cap = np.sqrt(np.clip(1.0 - ((h - 0.93) / 0.07) ** 2, 0.0, 1.0))
    r = np.where(h > 0.93, r * cap, r)
    return r, lf


def tentacle_proto(bubble=0.45, bp=0.65, length=1.0):
    """Unit tentacle: rings x SIDES + tip vertex. Returns V, F, UV, COL (Y up)."""
    V, UV, COL = [], [], []
    for h in RINGS[:-1]:
        r, lf = tentacle_profile(h, bubble, bp)
        for s in range(SIDES + 1):
            a = 2 * math.pi * s / SIDES
            V.append((math.cos(a) * r * length, h * lf * length, math.sin(a) * r * length))
            UV.append((h, s / SIDES))
            COL.append((0.0, 0.0, h, 1.0))
    tip = len(V)
    _, lf = tentacle_profile(1.0, bubble, bp)
    V.append((0.0, lf * length, 0.0)); UV.append((1.0, 0.5)); COL.append((0.0, 0.0, 1.0, 1.0))
    F = []
    n = len(RINGS) - 1
    for k in range(n - 1):
        for s in range(SIDES):
            a = k * (SIDES + 1) + s
            b = a + SIDES + 1
            F.append((a, b, b + 1, a + 1))
    last = (n - 1) * (SIDES + 1)
    for s in range(SIDES):
        F.append((last + s, tip, last + s + 1))
    return np.array(V), F, np.array(UV), np.array(COL)


# ----------------------------------------------------------------------------------------------------------------
# atlas: strip 0 tentacle (x = base->tip, y = around), strip 1 oral disc (x = centre->rim, y = angle), strip 2 column
# ----------------------------------------------------------------------------------------------------------------
STRIPS = 4


def strip_uv(k, x, y):
    x = np.clip(np.asarray(x, float), 0.004, 0.996)
    y = np.clip(np.asarray(y, float), 0.01, 0.99)
    return np.stack([x, 1.0 - (k + 1 - y) / STRIPS], -1)


def paint_atlas(w=512, h=512, tip_pink=0.0):
    cv = P.Canvas(w, h, "#808080")
    sh = h // STRIPS

    def strip(k):
        return P.Canvas(w, sh)

    # --- tentacle: neon green base, orange body with red speckle and fine lengthwise stripes, yellow-orange tip
    t = strip(0)
    x, y = t.u, t.v
    t.rgb[:] = C.hex2rgb("#FF7418")
    t.blend(S(0.45, 0.95, x), "#FF9A2A", 0.9)
    t.blend(S(0.8, 1.0, x), "#FFC63A", 0.9)
    stripes = 0.5 + 0.5 * np.sin(y * 2 * math.pi * 7 + 0.6 * np.sin(x * 9))
    noise = P.fbm(sh, w, 48, 3, 41, aspect=w / sh)
    speck = S(0.7, 0.77, P.fbm(sh, w, 140, 2, 42, aspect=w / sh) * 0.7 + 0.3 * stripes)
    body = S(0.08, 0.2, x) * (1 - S(0.7, 0.92, x))
    t.blend(speck * body, "#8E1C12", 0.85)
    t.blend(S(0.55, 0.9, stripes) * body * (0.4 + 0.6 * noise), "#B32A14", 0.35)
    t.blend(1 - S(0.1, 0.3, x), "#3EE07C", 1.0)
    t.blend(S(0.12, 0.2, x) * (1 - S(0.2, 0.3, x)), "#B8C23A", 0.5)
    t.multiply(0.9 + 0.2 * noise)
    # preview only: the bubbled tip tint polyp.frag adds at runtime
    t.blend(S(0.86, 0.97, x), "#FF4FB8", tip_pink)
    cv.rgb[0:sh] = t.rgb

    # --- oral disc (polar): pale lavender mouth, lips, teal-green disc with fine radial lines, neon green rim ring
    d = strip(1)
    r, a = d.u, d.v
    d.rgb[:] = C.hex2rgb("#2BBF73")
    d.blend(S(0.15, 0.6, r) * (1 - S(0.6, 0.85, r)), "#2E9FA0", 0.55)
    lines = 0.5 + 0.5 * np.cos(a * 2 * math.pi * 64)
    d.blend(S(0.55, 0.95, lines) * S(0.14, 0.3, r) * (1 - S(0.86, 0.94, r)), "#0E5A3C", 0.6)
    d.blend(S(0.8, 0.93, r), "#55FF6E", 0.95)
    d.blend(1 - S(0.1, 0.16, r), "#CBBEEA", 1.0)
    d.blend((1 - S(0.03, 0.06, r)), "#7A5E9E", 0.7)
    d.blend(S(0.1, 0.13, r) * (1 - S(0.13, 0.17, r)), "#E9E2F8", 0.6)
    d.multiply(0.92 + 0.16 * P.fbm(sh, w, 30, 3, 43, aspect=w / sh))
    cv.rgb[sh:2 * sh] = d.rgb

    # --- column: rusty orange-brown with paler verrucae (adhesive bumps) toward the top
    c = strip(2)
    c.rgb[:] = C.hex2rgb("#A2462A")
    c.blend(S(0.0, 0.4, c.u), "#C0603A", 0.6)
    bumps = S(0.68, 0.78, P.fbm(sh, w, 90, 2, 44, aspect=w / sh))
    c.blend(bumps * (1 - S(0.4, 0.8, c.u)), "#E2A27A", 0.7)
    c.blend(S(0.85, 1.0, c.u), "#5E2A1A", 0.6)
    cv.rgb[2 * sh:3 * sh] = c.rgb
    cv.rgb[3 * sh:] = C.hex2rgb("#3EE07C")
    return cv


def tentacle_frame(rs):
    """Instance rows on the disc: (pos, up direction, length, phase, variant)."""
    rows = []
    golden = math.pi * (3 - math.sqrt(5))
    for i in range(N_TENT):
        f = (i + 0.5) / N_TENT
        rn = math.sqrt(0.16 + 0.84 * f)                     # 0.4 .. 1 of the disc radius, even area density
        ang = i * golden + rs.uniform(-0.08, 0.08)
        rr = (0.38 + 0.6 * (rn - 0.4) / 0.6) * DISC_R
        y = disc_height(rr / DISC_R)
        pos = np.array([math.cos(ang) * rr, y, math.sin(ang) * rr])
        tilt = math.radians(14 + 58 * (rr / DISC_R - 0.38) / 0.6 + rs.uniform(-9, 9))
        out = np.array([math.cos(ang), 0.0, math.sin(ang)])
        up = np.array([0.0, 1.0, 0.0]) * math.cos(tilt) + out * math.sin(tilt)
        length = (SPAN / 2 - DISC_R) * (0.62 + 0.42 * (rr / DISC_R - 0.38) / 0.6) * rs.uniform(0.86, 1.12) + 0.6
        rows.append((pos, up, length, rs.uniform(), rs.uniform()))
    return rows


def disc_height(rn):
    """Oral disc surface height (cm) at radius fraction rn: raised lips, a shallow trough, rim at 0."""
    return 0.35 * math.exp(-(rn / 0.1) ** 2) - 0.45 * math.exp(-((rn - 0.35) / 0.25) ** 2) + 0.05 * rn


def q_from_up(n):
    up = np.array([0.0, 1.0, 0.0])
    n = n / np.linalg.norm(n)
    ax = np.cross(up, n)
    s = np.linalg.norm(ax)
    if s < 1e-8:
        return np.array([0, 0, 0, 1.0])
    ang = math.atan2(s, float(np.dot(up, n)))
    ax = ax / s
    return np.array([*(ax * math.sin(ang / 2)), math.cos(ang / 2)])


def disc_mesh():
    """Oral disc + column (Y up). Returns WGeo parts lists."""
    seg = 40
    rad = [0.0, 0.04, 0.08, 0.12, 0.16, 0.24, 0.34, 0.46, 0.58, 0.7, 0.8, 0.88, 0.94, 1.0]
    V, UV, COL, F = [], [], [], []
    for rn in rad:
        for s in range(seg + 1):
            a = 2 * math.pi * s / seg
            V.append((math.cos(a) * rn * DISC_R, disc_height(rn), math.sin(a) * rn * DISC_R))
            UV.append(strip_uv(1, rn, s / seg))
            glow = float(S(0.14, 0.3, rn))
            COL.append((glow, 1.0, 0.0, 1.0))
    for k in range(len(rad) - 1):
        for s in range(seg):
            a = k * (seg + 1) + s
            b = a + seg + 1
            F.append((a, a + 1, b + 1, b))
    base = len(V)
    # column: from under the rim (lip rolled under) down into the crevice, tapering to the foot
    col_rows = [(0.99, -0.15), (0.95, -0.6), (0.84, -1.6), (0.74, -2.8), (0.66, -4.0), (0.6, COL_DEPTH * -1.0), (0.3, -COL_DEPTH - 0.4)]
    for j, (rf, y) in enumerate(col_rows):
        for s in range(seg + 1):
            a = 2 * math.pi * s / seg
            wob = 1 + 0.04 * math.sin(a * 5 + j)
            V.append((math.cos(a) * rf * DISC_R * wob, y, math.sin(a) * rf * DISC_R * wob))
            UV.append(strip_uv(2, j / (len(col_rows) - 1), s / seg))
            COL.append((0.0, 1.0, 0.0, 1.0))
    for k in range(len(col_rows) - 1):
        for s in range(seg):
            a = base + k * (seg + 1) + s
            b = a + seg + 1
            F.append((a, b, b + 1, a + 1))
    return np.array(V), F, np.array(UV), np.array(COL)


# ----------------------------------------------------------------------------------------------------------------
def build(preview_states=None):
    C.reset_scene()
    atlas = paint_atlas()
    os.makedirs(C.BUILD, exist_ok=True)
    from PIL import Image
    tex_path = os.path.join(C.BUILD, "bubble_tip_anemone.jpg")
    Image.fromarray((np.clip(atlas.rgb, 0, 1) * 255 + 0.5).astype(np.uint8)).save(tex_path, quality=88)
    img = C.load_image(tex_path, name="bubble_tip_anemone")
    m_disc = C.material("anemone_disc", rough=0.32, image=img, emissive="#2CFF62", emissive_strength=0.55, double=True)
    m_tent = C.material("anemone_tentacle", rough=0.28, image=img, emissive="#2CFF62", emissive_strength=0.45, double=True)
    root = C.empty("Root")
    C.set_extras(root, species="entacmaea_quadricolor", lengthCm=SPAN, discRadiusCm=DISC_R,
                 unit="centimeters, oral disc centre at origin, column down -Y", roughness=0.3)

    V, F, UV, COL = disc_mesh()
    g = T.WGeo(); g.add(V, F, UV, COL)
    disc_o = g.obj("Disc", root, "polyp", [m_disc], smooth=True)

    rs = np.random.default_rng(7)
    rows = tentacle_frame(rs)
    pV, pF, pUV, pCOL = tentacle_proto()
    tuv = strip_uv(0, pUV[:, 0], pUV[:, 1])
    inst = []
    for pos, up, length, ph, var in rows:
        q = q_from_up(up)
        inst.append([*np.round(pos, 3), *np.round(q, 5), round(length, 3), round(ph, 3), round(var, 3)])
    g2 = T.WGeo(); g2.add(pV, pF, tuv, pCOL)
    ex = {"instances": [float(x) for r in inst for x in r], "instanceStride": 10, "instanceCount": len(inst)}
    tent_o = g2.obj("Tentacles", root, "polyp", [m_tent], smooth=True, extras=ex)
    C.export_glb(OUT, C.all_descendants(root))
    tris = C.tri_count(disc_o) + C.tri_count(tent_o) * len(inst)
    print(f"[anemone] {os.path.basename(OUT)}: {tris} rendered tris ({len(inst)} tentacles)")
    return rows, (m_disc, m_tent, img)


# ----------------------------------------------------------------------------------------------------------------
# previews: explicit geometry per state (what the shader does at runtime), Cycles, actinic + white light
# ----------------------------------------------------------------------------------------------------------------
def preview(outdir, size=(900, 900), samples=48):
    import preview as PV
    rows, (m_disc, m_tent, img) = build()
    states = [("stringy", 0.0, 1.0), ("bubbled", 1.0, 1.0), ("deflated", 0.6, 0.25)]
    for name, bubble, ext in states:
        C.reset_scene()
        from PIL import Image
        pth = os.path.join(C.BUILD, f"anemone_preview_{name}.png")
        Image.fromarray((np.clip(paint_atlas(tip_pink=0.85 * bubble).rgb, 0, 1) * 255 + 0.5).astype(np.uint8)).save(pth)
        img2 = C.load_image(pth, name="atlas")
        md = C.material("disc", rough=0.32, image=img2, emissive="#2CFF62", emissive_strength=0.15, double=True)
        mt = C.material("tent", rough=0.28, image=img2, double=True)
        V, F, UV, COL = disc_mesh()
        k = 0.45 + 0.55 * ext
        V = V * np.array([k, 1.0, k])
        g = T.WGeo(); g.add(V, F, UV, COL); g.obj("Disc", None, None, [md], smooth=True)
        g2 = T.WGeo()
        for pos, up, length, ph, var in rows:
            bp = 0.5 + 0.25 * var
            pV = []
            pUV = []
            for h in RINGS[:-1]:
                r, lf = tentacle_profile(h, bubble, bp)
                for s in range(SIDES + 1):
                    a = 2 * math.pi * s / SIDES
                    pV.append((math.cos(a) * r, h * lf, math.sin(a) * r))
                    pUV.append((h, s / SIDES))
            _, lf = tentacle_profile(1.0, bubble, bp)
            pV.append((0, lf, 0)); pUV.append((1.0, 0.5))
            _, pF, _, _ = tentacle_proto()
            pV = np.array(pV) * length * (0.35 + 0.65 * ext)
            # gentle droop of long stringy tentacles (gravity / flow), more toward the tip
            hh = np.clip(pV[:, 1] / max(length, 1e-6), 0, 1)
            pV[:, 0] += 0.25 * length * (1 - bubble) * hh ** 2 * math.sin(ph * 6.28)
            q = q_from_up(up)
            from corals import q_rotate
            W = np.array([q_rotate(q, v) for v in pV]) + pos * np.array([k, 1, k])
            # tips sag outward / down when stringy
            out = np.array([pos[0], 0, pos[2]]); out = out / (np.linalg.norm(out) + 1e-9)
            W += (out * 0.35 - np.array([0, 0.5, 0])) * (length * (1 - bubble) * 0.5) * (hh ** 2)[:, None]
            uv = strip_uv(0, np.array(pUV)[:, 0], np.array(pUV)[:, 1])
            g2.add(W, pF, uv)
        g2.obj("Tentacles", None, None, [mt], smooth=True)
        # tip tint for the bubbled form (polyp.frag does the same from the hinge)
        PV.setup_cycles(*size, samples=samples)
        sc = bpy.context.scene
        sc.world = bpy.data.worlds.new("w"); sc.world.use_nodes = True
        sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0.004, 0.006, 0.03, 1)
        def lamp(nm, loc, col, e, sz=8.0):
            ld = bpy.data.lights.new(nm, 'AREA'); ld.energy = e; ld.color = col; ld.size = sz
            ob = bpy.data.objects.new(nm, ld); ob.location = loc; sc.collection.objects.link(ob)
            ob.rotation_euler = (0, 0, 0)
            return ob
        lamp("white", (0, 0, 40), (1.0, 0.97, 0.95), 5000)
        sc.view_settings.view_transform = "Standard"
        lamp("blue", (6, -6, 38), (0.35, 0.45, 1.0), 5000)
        cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam)
        cam.location = (0, -15, 24); cam.rotation_euler = (math.radians(32), 0, 0)
        cam.data.lens = 50
        sc.camera = cam
        sc.render.filepath = os.path.join(outdir, f"anemone_{name}.png")
        bpy.ops.render.render(write_still=True)
        print("[anemone] wrote", sc.render.filepath)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    ap.add_argument("--outdir", default=os.path.join(C.BUILD, "anemone_preview"))
    a = ap.parse_args()
    if a.preview:
        os.makedirs(a.outdir, exist_ok=True)
        preview(a.outdir)
    else:
        build()
