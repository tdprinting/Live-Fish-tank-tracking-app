"""Preview sheet of the clownfish eggs, larvae and fry (tools/blender/brood.py):
  python tools/blender/brood_preview.py [outfile]  -> build/blender/brood_sheet.png
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import preview as PV  # noqa: E402

IDS = ["brood_clown_eggs_fresh", "brood_clown_eggs_dark", "brood_clown_eggs_eyed",
       "brood_clown_larva", "brood_clown_postlarva", "brood_clown_fry"]


def main(argv):
    out = argv[0] if argv else os.path.join(C.BUILD, "brood_sheet.png")
    cells = [PV.render_fish(i, w=640, h=420, samples=int(os.environ.get("SAMPLES", "48"))) for i in IDS]
    PV.compose(cells, 3, out)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1:])
