"""Build every ReefLive asset (ASSETS.md §8).

    /tmp/claude-0/bpyenv/bin/python tools/blender/build_all.py                 # everything
    /tmp/claude-0/bpyenv/bin/python tools/blender/build_all.py --only fish,corals
    /tmp/claude-0/bpyenv/bin/python tools/blender/build_all.py --preview       # + docs/preview renders

Order: fishlib self-test -> textures -> fish -> inverts -> rockscape (+anchors) -> rock SDF (+ rock_surface pass) -> tank
(needs the SDF for sand mounds) -> corals (need the rock) -> equipment -> details (rock + sand) -> supply shelf -> species cards
-> validate.
Prints per-asset triangle counts, fails (exit 1) if any asset is > 15 % over its budget, and prints the
rendered-triangle estimates at High (<= 220k) and Medium (<= 140k, LOD1 files + halved instances) (tools/validate_glb.py).
"""
import argparse
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(HERE, "..", "sdf"))

STEPS = ["selftest", "textures", "fish", "inverts", "rockscape", "sdf", "tank", "corals", "equipment", "details",
         "supplies", "care", "room", "cards"]


def run(step, args):
    if step == "selftest":
        import fishlib
        fishlib.self_test()
    elif step == "textures":
        import textures
        textures.build_all()
    elif step == "fish":
        import fish_species as FS
        for sid in FS.FISH_IDS:
            FS.build_fish(sid)
    elif step == "inverts":
        import inverts
        for k, fn in inverts.INVERT_BUILDERS.items():
            fn()
        import microfauna
        for k, fn in microfauna.MICRO_BUILDERS.items():
            fn()
        import cleanup_crew  # trochus, fighting conch, tuxedo urchin (docs/research/43_cleanup_crew.md)
        cleanup_crew.build_all()
        import emerald_crab  # emerald crab on the shared crab rig (research 43 §1)
        emerald_crab.build_emerald_crab()
        import squid  # sandbox bobtail squid (docs/research/cephalopod_squid.md)
        squid.build_all()
        import file_clam  # flame scallop (docs/research/42_peppermint_shrimp_flame_scallop.md)
        file_clam.build()
        import porcelain_crab  # porcelain crab (docs/research/45_clam_crab_star_cucumber.md)
        porcelain_crab.build_porcelain_crab()
        import maxima_clam  # maxima clam (docs/research/45_clam_crab_star_cucumber.md)
        maxima_clam.build()
        maxima_clam.use("crocea_clam")  # ultra crocea (docs/research/62_rare_inverts.md)
        maxima_clam.build()
        import mantis_shrimp  # peacock mantis shrimp (docs/research/62_rare_inverts.md)
        mantis_shrimp.build()
        import sea_cucumber  # tiger tail sea cucumber (docs/research/45_clam_crab_star_cucumber.md)
        sea_cucumber.build()
    elif step == "rockscape":
        import rockscape
        rockscape.build(bake=not args.nobake)
    elif step == "sdf":
        import bake_sdf
        bake_sdf.bake()
        # smoothed normals + cavity / ridge / AO vertex colours on the rock (needs the SDF just baked)
        import rock_surface
        rock_surface.run()
    elif step == "tank":
        import tank
        tank.build()
    elif step == "corals":
        import corals
        corals.build()
    elif step == "equipment":
        import equipment
        equipment.build()
    elif step == "details":
        import details
        details.build()
    elif step == "supplies":
        import supplies
        supplies.build()
    elif step == "care":
        import care_tools
        care_tools.build()
    elif step == "room":
        import stands  # My tank's stands and room (core custom/room/Decor.kt)
        for sid in stands.STANDS:
            stands.build(sid)
        import rooms
        for sc in rooms.SCENES:
            rooms.build(sc)
    elif step == "cards":
        import render_cards
        render_cards.build()


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None, help="comma list of steps: " + ",".join(STEPS))
    ap.add_argument("--skip", default="", help="comma list of steps to skip")
    ap.add_argument("--nobake", action="store_true", help="rockscape without Cycles bakes (fast blocking check)")
    ap.add_argument("--preview", action="store_true", help="also render docs/preview/fish.png, inverts.png, scene.png")
    args = ap.parse_args(argv)
    steps = args.only.split(",") if args.only else STEPS
    skip = set(filter(None, args.skip.split(",")))
    t0 = time.time()
    for st in steps:
        if st in skip:
            continue
        if st not in STEPS:
            raise SystemExit(f"unknown step {st}")
        t = time.time()
        print(f"=== {st}")
        run(st, args)
        print(f"=== {st} done in {time.time() - t:.0f}s")
    if args.preview:
        import preview
        import scene_preview
        preview.fish_sheet(preview.FISH_SHEET, os.path.join(preview.C.PREVIEW, "fish.png"), 2)
        preview.fish_sheet(preview.INVERT_SHEET, os.path.join(preview.C.PREVIEW, "inverts.png"), 3)
        scene_preview.render_scene(samples=64, w=1280, h=720)
    import validate_glb
    rc = validate_glb.main([])
    print(f"build_all finished in {time.time() - t0:.0f}s")
    return rc


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    sys.exit(main(argv))
