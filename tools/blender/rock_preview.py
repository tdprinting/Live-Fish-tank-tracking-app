"""Rock-only preview renders (before/after rockwork changes).

  python tools/blender/rock_preview.py --out DIR [--tag after] [--glb models/rockscape.glb] [--anchors sim/anchors.json]

Writes <tag>_front.png (whole rockscape, front camera), <tag>_left.png / <tag>_right.png (three-quarter
close-ups of the left stack and the right arch) and one close-up per hole/cave listed in the anchors file's
"holes" array (<tag>_hole_<NAME>.png). Rock only (no corals, water or glass), so holes and pores read clearly.
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import preview as PV  # noqa: E402
from common import bpy, w2b  # noqa: E402


def scene(glb):
    C.reset_scene()
    PV.import_glb(glb)
    PV.world((0.03, 0.05, 0.07), 1.0)
    # key light from above the front glass, a cool fill from the left, rim from behind-right
    PV.add_light('SUN', (0, 0, 100), 3.2, rot=(math.radians(35), 0, math.radians(8)), size=4)
    PV.add_light('AREA', w2b((-30, 40, 60)), 60000, size=60, color=(0.8, 0.9, 1.0))
    for o in bpy.context.scene.objects:
        if o.type == 'AREA' or (o.type == 'LIGHT' and o.data.type == 'AREA'):
            PV.look_at(o, w2b((60, 20, 15)))


def shot(out, loc, target, lens=35, w=1600, h=900, samples=48, ortho=None):
    PV.setup_cycles(w, h, samples)
    PV.camera(w2b(loc), w2b(target), lens=lens, ortho=ortho)
    PV.render_to(out)
    print("[rock_preview]", out)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--tag", default="after")
    ap.add_argument("--glb", default=os.path.join(C.MODELS, "rockscape.glb"))
    ap.add_argument("--anchors", default=os.path.join(C.SIM, "anchors.json"))
    ap.add_argument("--samples", type=int, default=48)
    ap.add_argument("--holes", default=None, help="comma list of hole names to close up (default: blenny holes + homes)")
    a = ap.parse_args(argv)
    os.makedirs(a.out, exist_ok=True)
    scene(a.glb)
    t = a.tag
    shot(os.path.join(a.out, f"{t}_front.png"), (61, 30, 140), (61, 28, 10), lens=38, samples=a.samples)
    shot(os.path.join(a.out, f"{t}_left.png"), (48, 30, 70), (26, 26, 10), lens=35, samples=a.samples)
    shot(os.path.join(a.out, f"{t}_right.png"), (78, 28, 72), (100, 26, 12), lens=35, samples=a.samples)
    holes = json.load(open(a.anchors)).get("holes", [])
    want = a.holes.split(",") if a.holes else None
    for hdesc in holes:
        if want is not None and hdesc["name"] not in want:
            continue
        if want is None and hdesc["kind"] not in ("HOLE", "CAVE", "CREVICE", "OVERHANG", "LEDGE"):
            continue
        m, ax = hdesc["mouth"], hdesc["axis"]
        back = 14.0 + 2.0 * hdesc["radius"]
        cam = (m[0] + ax[0] * back + 3.0, m[1] + ax[1] * back + 4.0, min(m[2] + ax[2] * back + 2.0, 75.0))
        shot(os.path.join(a.out, f"{t}_hole_{hdesc['name']}.png"), cam, m, lens=50, w=900, h=700, samples=a.samples)


if __name__ == "__main__":
    main(sys.argv[1:] if "--" not in sys.argv else sys.argv[sys.argv.index("--") + 1:])
