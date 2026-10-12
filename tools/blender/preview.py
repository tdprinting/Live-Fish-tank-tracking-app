"""Preview renders (Cycles CPU; EEVEE needs a GPU context that the headless bpy module lacks).

  python tools/blender/preview.py --fish all          -> docs/preview/fish.png (contact sheet)
  python tools/blender/preview.py --fish tang         -> build/blender/preview_tang.png
  python tools/blender/preview.py --scene [--view all] [--size 1600x1200] [--samples 64]
                                                      -> docs/preview/scene.png (+ scene_<view>.png)
"""
import argparse
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
from common import bpy  # noqa: E402

FISH_SHEET = ["clown_orange", "clown_darwin", "tang", "cardinal", "wrasse", "hawkfish", "lawnmower_blenny",
              "tailspot_blenny", "dottyback", "engineer_goby"]
INVERT_SHEET = ["shrimp", "skunk_shrimp", "hermit_blue", "hermit_scarlet", "sally_lightfoot", "brittle_star", "feather_duster",
                "nassarius", "astrea", "asterina"]


def setup_cycles(w, h, samples=48):
    s = bpy.context.scene
    s.render.engine = 'CYCLES'
    s.cycles.device = 'CPU'
    s.cycles.samples = samples
    s.cycles.use_denoising = False
    s.cycles.max_bounces = 4
    s.cycles.transparent_max_bounces = 16
    s.render.resolution_x = w
    s.render.resolution_y = h
    s.render.resolution_percentage = 100
    s.render.image_settings.file_format = 'PNG'
    s.view_settings.view_transform = 'Standard'
    s.render.threads_mode = 'FIXED'
    s.render.threads = os.cpu_count() or 4


def world(color=(0.05, 0.08, 0.1), strength=1.0):
    w = bpy.data.worlds.new("W")
    w.use_nodes = True
    bg = w.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (*color, 1)
    bg.inputs[1].default_value = strength
    bpy.context.scene.world = w


def add_light(kind, loc, energy, rot=None, size=1.0, color=(1, 1, 1), spot=None):
    ld = bpy.data.lights.new(kind, kind)
    ld.energy = energy
    ld.color = color
    if kind == 'AREA':
        ld.size = size
    if kind == 'SUN':
        ld.angle = math.radians(size)
    if kind == 'SPOT' and spot:
        ld.spot_size = math.radians(spot)
        ld.spot_blend = 0.6
    o = bpy.data.objects.new(kind, ld)
    o.location = loc
    if rot:
        o.rotation_euler = rot
    C.link(o)
    return o


def look_at(obj, target):
    from mathutils import Vector
    d = Vector(target) - obj.location
    obj.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()


def camera(loc, target, lens=50, ortho=None):
    cd = bpy.data.cameras.new("Cam")
    cd.lens = lens
    cd.clip_start = 0.01
    cd.clip_end = 1000
    if ortho:
        cd.type = 'ORTHO'
        cd.ortho_scale = ortho
    o = bpy.data.objects.new("Cam", cd)
    o.location = loc
    C.link(o)
    look_at(o, target)
    bpy.context.scene.camera = o
    return o


def import_glb(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.data.objects if o not in before]
    # COLOR_0 is data (not color): stop the importer from multiplying it into base color
    # -> set it to white so the multiply is an identity
    for o in new:
        if o.type == 'MESH':
            for ca in o.data.color_attributes:
                ca.data.foreach_set("color", np.ones(len(ca.data) * 4, np.float32))
    return new


def render_to(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    img = bpy.data.images.load(path, check_existing=False)
    w, h = img.size
    px = np.array(img.pixels[:], np.float32).reshape(h, w, 4)[::-1]
    bpy.data.images.remove(img)
    return px


def render_fish(sid, w=640, h=380, samples=48):
    C.reset_scene()
    setup_cycles(w, h, samples)
    world((0.16, 0.2, 0.24), 1.0)
    objs = import_glb(os.path.join(C.MODELS, f"{sid}.glb"))
    # fish are unit length; eel is long and thin -> view it coiled? keep straight, wider framing
    add_light('SUN', (0, 0, 5), 3.5, rot=(math.radians(-25), math.radians(10), 0), size=8)
    add_light('AREA', (0.3, -1.2, 0.6), 30, size=1.5, color=(0.9, 0.95, 1.0))
    add_light('AREA', (-0.8, 0.8, 0.3), 12, size=1.0, color=(0.8, 0.9, 1.0))
    from mathutils import Vector
    pts = [o.matrix_world @ Vector(c) for o in objs if o.type == 'MESH' for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    ctr = (lo + hi) / 2
    ext = hi - lo
    top_view = sid in ("brittle_star", "asterina")
    elev = 0.55 if sid in ("hermit_blue", "hermit_scarlet", "sally_lightfoot", "astrea", "nassarius", "feather_duster",
                            "shrimp", "skunk_shrimp") else 0.16
    if top_view:
        elev = 1.6
    yaw = math.radians(-18)
    d = max(ext.x, ext.y, ext.z) * 3 + 1
    loc = ctr + Vector((d * math.sin(yaw) * math.cos(elev), -d * math.cos(yaw) * math.cos(elev), d * math.sin(elev)))
    span = max(ext.x, ext.y * (w / h) if top_view else 0, ext.z * (w / h) * 1.1 if not top_view else 0) * 1.15
    camera(tuple(loc), tuple(ctr), lens=50, ortho=span)
    return render_to(os.path.join(C.BUILD, f"preview_{sid}.png"))


def compose(cells, cols, out, pad=6, bg=(0.06, 0.07, 0.08)):
    h, w = cells[0].shape[:2]
    rows = (len(cells) + cols - 1) // cols
    sheet = np.zeros((rows * h + (rows + 1) * pad, cols * w + (cols + 1) * pad, 3), np.float32)
    sheet[:] = bg
    for i, c in enumerate(cells):
        r, k = divmod(i, cols)
        y = pad + r * (h + pad)
        x = pad + k * (w + pad)
        sheet[y:y + h, x:x + w] = c[..., :3]
    C.write_png(out, sheet)
    return out


def fish_sheet(ids, out, cols=2):
    cells = [render_fish(sid) for sid in ids]
    return compose(cells, cols, out)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--fish", default=None)
    ap.add_argument("--scene", action="store_true")
    ap.add_argument("--samples", type=int, default=None)
    ap.add_argument("--view", default="front", help="front|left34|right34|top|left_end|right_end|all")
    ap.add_argument("--size", default="1600x1200")
    a = ap.parse_args(argv)
    if a.fish:
        if a.fish == "all":
            print(fish_sheet(FISH_SHEET, os.path.join(C.PREVIEW, "fish.png"), 2))
            print(fish_sheet(INVERT_SHEET, os.path.join(C.PREVIEW, "inverts.png"), 3))
        else:
            for sid in a.fish.split(","):
                render_fish(sid)
                print(os.path.join(C.BUILD, f"preview_{sid}.png"))
    if a.scene:
        import scene_preview
        w, h = (int(v) for v in a.size.split("x"))
        views = list(scene_preview.VIEWS) if a.view == "all" else a.view.split(",")
        for v in views:
            scene_preview.render_scene(samples=a.samples, w=w, h=h, view=v)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    main(argv)
