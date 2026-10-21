"""Contact sheet of the stands (tools/blender/stands.py) in their first colours, under a stand-in tank.

    python3 tools/blender/stand_preview.py OUT.png [build_id]
"""
import math
import os
import sys

import numpy as np

import common as C
from common import bpy
import stands as S

# first colour of each stand (core Decor.STANDS): paint, trim
COLORS = {
    "classic": ("#F1EDE6", "#3B2518"), "shaker": ("#B98A5A", "#8B6240"), "iron": ("#1E1F22", "#B98A5A"),
    "modern": ("#F4F4F2", "#2A2B2E"), "rustic": ("#9C8A74", "#2B2622"), "reef_pro": ("#3A3D42", "#8A9096"),
}


def render_one(sid, bid, out, extras=()):
    C.reset_scene()
    bpy.ops.import_scene.gltf(filepath=os.path.join(C.MODELS, f"stand_{sid}.glb"))
    paint, trim = COLORS[sid]
    for o in list(bpy.data.objects):
        if o.type == 'MESH' and o.name.split(".")[0] != bid:
            bpy.data.objects.remove(o)
    for m in bpy.data.materials:
        b = m.node_tree.nodes.get("Principled BSDF") if m.use_nodes else None
        if b is None:
            continue
        # COLOR_0 is the app's occlusion data, not a colour: keep it out of the preview's base colour
        for n in list(m.node_tree.nodes):
            if n.type in ('VERTEX_COLOR', 'ATTRIBUTE') or n.bl_idname == 'ShaderNodeVertexColor':
                m.node_tree.nodes.remove(n)
        for l in list(m.node_tree.links):
            if l.to_socket == b.inputs["Base Color"]:
                m.node_tree.links.remove(l)
        if m.name.startswith("paint"):
            b.inputs["Base Color"].default_value = (*C.lin(paint), 1)
        elif m.name.startswith("trim"):
            b.inputs["Base Color"].default_value = (*C.lin(trim), 1)
        elif m.name.startswith("glow"):
            on = m.name == "glow_pro" or "glow" in extras
            b.inputs["Emission Color"].default_value = (0.4, 0.8, 1.0, 1)
            b.inputs["Emission Strength"].default_value = 8.0 if on else 0.0
        elif m.name.startswith("metal") and "brass" in extras:
            b.inputs["Base Color"].default_value = (*C.lin("#D4A84B"), 1)
    L, D = S.BUILDS[bid]
    # tank stand-in, floor, wall (Blender coords: x, -z, y)
    def cube(name, lo, hi, col, alpha=1.0, rough=0.5):
        bpy.ops.mesh.primitive_cube_add()
        o = bpy.context.object
        o.name = name
        c = [(lo[0] + hi[0]) / 2, -(lo[2] + hi[2]) / 2, (lo[1] + hi[1]) / 2]
        o.location = c
        o.scale = [(hi[0] - lo[0]) / 2, (hi[2] - lo[2]) / 2, (hi[1] - lo[1]) / 2]
        o.data.materials.append(C.material(name + "_m", col, rough=rough, alpha=alpha))
    cube("tank", (0, -3.6, 0), (L, 40, D), "#0E3A4A", rough=0.1)
    cube("floor", (-300, -84, -60), (400, -82, 300), "#8A6A4D", rough=0.6)
    cube("wall", (-300, -82, -50), (400, 200, -45), "#E8DFD0", rough=0.9)
    sc = bpy.context.scene
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cx = L / 2
    eye = np.array([cx + 70, 5, D + 190.0])
    at = np.array([cx, -38, D / 2])
    cam.location = (eye[0], -eye[2], eye[1])
    d = at - eye
    yaw = math.atan2(d[0], -d[2])
    pitch = math.atan2(d[1], math.hypot(d[0], d[2]))
    cam.rotation_euler = (math.pi / 2 + pitch, 0, -yaw)
    cam.data.lens = 35
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", 'AREA'))
    sun.data.energy = 1.2e6
    sun.data.size = 150
    sun.location = (cx - 120, -(D + 150), 180)
    sun.rotation_euler = (math.radians(50), 0, math.radians(-35))
    sc.collection.objects.link(sun)
    w = bpy.data.worlds.new("w")
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs[0].default_value = (0.5, 0.48, 0.45, 1)
    w.node_tree.nodes["Background"].inputs[1].default_value = 0.6
    sc.world = w
    sc.render.engine = 'CYCLES'
    sc.cycles.samples = 24
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = 420, 360
    sc.render.filepath = out
    sc.view_settings.view_transform = 'Standard'
    bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    out = sys.argv[1]
    bid = sys.argv[2] if len(sys.argv) > 2 else "long_reef_60"
    extras = tuple(sys.argv[3].split(",")) if len(sys.argv) > 3 else ()
    tiles = []
    for sid in S.STANDS:
        p = os.path.join(C.BUILD, f"stand_prev_{sid}_{bid}.png")
        os.makedirs(C.BUILD, exist_ok=True)
        render_one(sid, bid, p, extras)
        tiles.append(C.read_png(p)[..., :3])
    h, w = tiles[0].shape[:2]
    sheet = np.zeros((h * 2, w * 3, 3), np.uint8)
    for i, t in enumerate(tiles):
        sheet[(i // 3) * h:(i // 3 + 1) * h, (i % 3) * w:(i % 3 + 1) * w] = t
    C.write_png(out, sheet)
    print("wrote", out)
