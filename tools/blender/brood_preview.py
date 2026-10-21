"""Preview sheet of the clownfish eggs, larvae and fry (tools/blender/brood.py), or the blue tang ones (brood_tang.py):
  python tools/blender/brood_preview.py [outfile] [tang]  -> build/blender/brood_sheet.png
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import preview as PV  # noqa: E402

IDS = ["brood_clown_eggs_fresh", "brood_clown_eggs_dark", "brood_clown_eggs_eyed",
       "brood_clown_larva", "brood_clown_postlarva", "brood_clown_fry"]
TANG_IDS = ["brood_tang_egg", "brood_tang_larva", "brood_tang_postlarva", "brood_tang_fry"]


def main(argv):
    out = argv[0] if argv else os.path.join(C.BUILD, "brood_sheet.png")
    ids = TANG_IDS if "tang" in argv[1:] else IDS
    cells = [PV.render_fish(i, w=640, h=420, samples=int(os.environ.get("SAMPLES", "48"))) for i in ids]
    PV.compose(cells, 2 if ids is TANG_IDS else 3, out)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1:])
