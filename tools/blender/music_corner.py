"""The music corner beside the tank (render/MusicCorner.kt): music_corner.glb.

A tiered wooden record stand on the floor left of the side table, holding all 16 radio records (core RadioStation)
face out, four to a row, and the little radio on the side table (rooms.py RADIO_SPOT) with a tuning knob, a volume
knob and a lit dial. The renderer turns the knobs, slides the dial's needle and spins or pulls out a record, so those
parts are authored round their own pivot and placed by MusicCornerLayout (keep the numbers below in step with it):

  Stand        the stand (world cm, static)
  Radio        the radio's case, speaker cloth, dial face, antenna (world cm, static)
  Disc00..15   one record each, in its own plane (centre at the origin, facing +z, radius DISC_R); the label is the
               song's sleeve art (vinyl_*.webp, tools/graphics/vinyl_art.py) from one 4 x 4 atlas
  KnobTune     the tuning knob, KnobVolume the volume knob: round their axis (origin, facing +z), the mark at +y
  Needle       the dial needle (origin at its centre)

World cm, the tank frame (x along the tank from its left glass, y up from the glass bottom, z toward the viewer from
the back glass). Material names carry room.frag's kinds (HomeRoomScene.kindOf): "wood" ones get its grain.

    python3 tools/blender/music_corner.py   (bpy from PyPI; no Blender install needed)
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import common as C  # noqa: E402
from tank import WGeo, box  # noqa: E402

FLOOR = -82.0
# the record stand (MusicCornerLayout): rows from the bottom; each row's rail top (y) and front (z)
DISC_R = 7.5
COLS = 4
ROWS = 4
COL_X0 = -117.75
COL_PITCH = 16.5
ROW_Y0 = -70.0
ROW_DY = 18.0
ROW_Z0 = -14.0
ROW_DZ = -6.0
STAND_X = (-127.5, -58.5)
# the radio on the side table (rooms.py RADIO_SPOT = (-32, -24, 22))
RADIO_LO = (-48.0, -24.0, 14.0)
RADIO_HI = (-16.0, -4.0, 28.0)
TUNE_R = 2.3
VOLUME_R = 2.8
SONGS = [
    "coral_morning", "aloha_lagoon", "tide_pool", "clownfish_waltz", "hammock_ukulele", "moonlit_drift",
    "window_seat", "five_oclock_dock", "sandcastle_sunshine", "turtle_glide", "sea_breeze_bossa", "starfish_lullaby",
    "tiki_twilight", "kelp_forest_sway", "postcard_reef", "dolphin_dance",
]
COLORS = {
    "wood_record": ("#8A5B3B", 0.5), "wood_radio": ("#6A3E25", 0.35), "fabric_speaker": ("#8E7B5A", 0.95),
    "dial_face": ("#F1E2B6", 0.6), "knob_cream": ("#EFE5D0", 0.35), "ink_mark": ("#2A2522", 0.6),
    "chrome_rod": ("#C9C6C0", 0.25), "vinyl_black": ("#121212", 0.28), "needle_red": ("#C8372D", 0.5),
    "felt_rail": ("#2F3A34", 0.95),
}


def occ(V, ao=1.0):
    a = np.full(len(V), ao, float) * (0.72 + 0.28 * np.clip((np.asarray(V)[:, 1] - FLOOR) / 25.0, 0.0, 1.0))
    return np.c_[a, np.zeros(len(V)), np.zeros(len(V)), np.ones(len(V))]


class Parts:
    """Node name -> material name -> WGeo."""

    def __init__(self):
        self.nodes = {}

    def g(self, node, mat):
        return self.nodes.setdefault(node, {}).setdefault(mat, WGeo())

    def box(self, node, mat, lo, hi, ao=1.0, floor_ao=True):
        V, F = box(lo, hi)
        self.g(node, mat).add(V, F, col=occ(V, ao) if floor_ao else (ao, 0, 0, 1))

    def mesh(self, node, mat, V, F, UV=None, ao=1.0):
        V = np.asarray(V, float)
        self.g(node, mat).add(V, F, UV, col=(ao, 0, 0, 1))


def disc(cx, cy, z, r, n, facing=1.0):
    """A flat n-gon (centre first) facing +z (or -z)."""
    V = [(cx, cy, z)] + [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n), z) for i in range(n)]
    F = [(0, 1 + i, 1 + (i + 1) % n) if facing > 0 else (0, 1 + (i + 1) % n, 1 + i) for i in range(n)]
    return np.array(V), F


def cyl_z(r, z0, z1, n, cx=0.0, cy=0.0):
    """A closed cylinder along z (side and front cap)."""
    V, F = [], []
    for i in range(n):
        a = 2 * math.pi * i / n
        V.append((cx + r * math.cos(a), cy + r * math.sin(a), z0))
        V.append((cx + r * math.cos(a), cy + r * math.sin(a), z1))
    for i in range(n):
        j = (i + 1) % n
        F.append((2 * i, 2 * j, 2 * j + 1, 2 * i + 1))
    F.append(tuple(2 * i + 1 for i in range(n)))
    return np.array(V), F


def stand(p):
    x0, x1 = STAND_X
    zb = ROW_Z0 + ROW_DZ * (ROWS - 1) - 6.0     # back of the top row's shelf
    top = ROW_Y0 + ROW_DY * (ROWS - 1) + 3.0
    # two stepped side cheeks (a box per step) and a back board
    for sx in (x0, x1 - 2.2):
        for r in range(ROWS):
            y = ROW_Y0 + ROW_DY * r
            zf = ROW_Z0 + ROW_DZ * r + 1.0
            p.box("Stand", "wood_record", (sx, FLOOR if r == 0 else y - ROW_DY + 1.5, zb), (sx + 2.2, y + 1.5, zf), ao=0.95)
    p.box("Stand", "wood_record", (x0, FLOOR + 2.0, zb - 1.6), (x1, top + 6.0, zb), ao=0.85)
    for r in range(ROWS):
        y = ROW_Y0 + ROW_DY * r
        zf = ROW_Z0 + ROW_DZ * r
        # the shelf board, a front lip that holds the records and a felt strip in the groove
        p.box("Stand", "wood_record", (x0 + 2.2, y - 1.5, zb), (x1 - 2.2, y, zf), ao=0.9)
        p.box("Stand", "wood_record", (x0 + 2.2, y, zf - 1.0), (x1 - 2.2, y + 2.2, zf), ao=1.0)
        p.box("Stand", "felt_rail", (x0 + 2.2, y, zf - 3.2), (x1 - 2.2, y + 0.25, zf - 1.0), ao=0.8, floor_ao=False)
    # a plinth
    p.box("Stand", "wood_record", (x0, FLOOR, zb), (x1, FLOOR + 3.0, ROW_Z0 + 1.0), ao=0.75)


def radio(p):
    (x0, y0, z0), (x1, y1, z1) = RADIO_LO, RADIO_HI
    p.box("Radio", "wood_radio", (x0 + 1.5, y0, z0 + 1.0), (x1 - 1.5, y0 + 0.8, z1 - 1.0), ao=0.6, floor_ao=False)    # foot
    p.box("Radio", "wood_radio", (x0, y0 + 0.8, z0), (x1, y1 - 1.2, z1), floor_ao=False)
    p.box("Radio", "wood_radio", (x0 + 0.8, y1 - 1.2, z0 + 0.6), (x1 - 0.8, y1, z1 - 0.6), floor_ao=False)            # rounded-ish top
    # speaker cloth with a few slats over it
    p.box("Radio", "fabric_speaker", (-46.0, -21.5, z1), (-31.0, -6.5, z1 + 0.05), ao=0.9, floor_ao=False)
    for k in range(4):
        y = -18.5 + k * 3.6
        p.box("Radio", "wood_radio", (-46.0, y, z1), (-31.0, y + 0.7, z1 + 0.45), floor_ao=False)
    # dial window (lit when the radio is on: room.frag gets its emissive from MusicCornerScene)
    p.box("Radio", "dial_face", (-29.5, -10.5, z1), (-18.0, -6.5, z1 + 0.05), floor_ao=False)
    for k in range(9):
        x = -28.8 + k * (10.1 / 8)
        p.box("Radio", "ink_mark", (x - 0.08, -7.4, z1 + 0.05), (x + 0.08, -6.9, z1 + 0.07), floor_ao=False)
    # a telescopic antenna from the back right corner, leaning out
    base = np.array([x1 - 2.5, y1, z0 + 2.0])
    tip = base + np.array([9.0, 26.0, -4.0])
    from tank import obox
    d = tip - base
    ln = float(np.linalg.norm(d))
    for k, w in enumerate((0.45, 0.32, 0.22)):
        a = base + d * (k / 3)
        b = base + d * ((k + 1) / 3)
        V, F = obox((a + b) / 2, (ln / 3, w, w), b - a, (0, 0, 1))
        p.mesh("Radio", "chrome_rod", V, F)
    V, F = cyl_z(0.6, 0.0, 0.0, 8)
    p.mesh("Radio", "chrome_rod", V + tip - np.array([0, 0, 0.3]), F)


def knob(p, node, r):
    V, F = cyl_z(r, 0.0, 1.6, 28)
    p.mesh(node, "knob_cream", V, F)
    # grip ridges round the rim and a dark mark pointing up (+y)
    for k in range(14):
        a = 2 * math.pi * k / 14
        from tank import obox
        Vr, Fr = obox((r * math.cos(a), r * math.sin(a), 0.8), (0.35, 0.35, 1.5), (0, 0, 1), (math.cos(a), math.sin(a), 0))
        p.mesh(node, "knob_cream", Vr, Fr, ao=0.85)
    p.mesh(node, "ink_mark", *box((-0.22, r * 0.25, 1.6), (0.22, r * 0.92, 1.68)))


def records(p):
    for i, song in enumerate(SONGS):
        node = f"Disc{i:02d}"
        # the label art: the whole sleeve picture (a record drawn to its edge) on the front face
        cu, cv = (i % 4) / 4, 1 - (i // 4 + 1) / 4
        n = 40
        V, F = disc(0, 0, 0.12, DISC_R, n)
        UV = np.c_[cu + (V[:, 0] / DISC_R * 0.5 * 0.98 + 0.5) / 4, cv + (V[:, 1] / DISC_R * 0.5 * 0.98 + 0.5) / 4]
        p.mesh(node, "vinyl_art", V, F, UV)
        Vb, Fb = disc(0, 0, -0.12, DISC_R, n, facing=-1)
        p.mesh(node, "vinyl_black", Vb, Fb)
        Vs, Fs = cyl_z(DISC_R, -0.12, 0.12, n)
        p.mesh(node, "vinyl_black", Vs, Fs[:-1])


def atlas(path):
    src = os.path.join(C.REPO, "app", "src", "main", "res", "drawable-nodpi")
    im = Image.new("RGB", (1024, 1024), (18, 18, 18))
    for i, s in enumerate(SONGS):
        t = Image.open(os.path.join(src, f"vinyl_{s}.webp")).convert("RGBA").resize((256, 256), Image.LANCZOS)
        bg = Image.new("RGBA", (256, 256), (18, 18, 18, 255))
        bg.alpha_composite(t)
        im.paste(bg.convert("RGB"), ((i % 4) * 256, (i // 4) * 256))
    im.save(path, quality=86)
    return path


def build():
    C.reset_scene()
    root = C.empty("Root")
    C.set_extras(root, units="cm")
    p = Parts()
    stand(p)
    radio(p)
    knob(p, "KnobTune", TUNE_R)
    knob(p, "KnobVolume", VOLUME_R)
    p.box("Needle", "needle_red", (-0.12, -1.7, 0.0), (0.12, 1.7, 0.08), floor_ao=False)
    records(p)
    os.makedirs(C.BUILD, exist_ok=True)
    img = C.load_image(atlas(os.path.join(C.BUILD, "vinyl_atlas.jpg")), "vinyl_atlas")
    mats = {}
    tris = 0
    for node, by_mat in p.nodes.items():
        names = sorted(by_mat)
        for n in names:
            if n not in mats:
                if n == "vinyl_art":
                    mats[n] = C.material(n, "#FFFFFF", rough=0.3, image=img)
                else:
                    col, rough = COLORS[n]
                    mats[n] = C.material(n, col, rough=rough)
        g = WGeo()
        for k, n in enumerate(names):
            sg = by_mat[n]
            g.add(np.array(sg.V), sg.F, np.array(sg.UV), col=np.array(sg.COL), mat=k)
        g.obj(node, root, "room", [mats[n] for n in names])
        tris += g.tris()
    out = C.export_glb(os.path.join(C.MODELS, "music_corner.glb"))
    print(f"music_corner.glb  ~{tris} tris  {os.path.getsize(out) // 1024} KB")


if __name__ == "__main__":
    build()
