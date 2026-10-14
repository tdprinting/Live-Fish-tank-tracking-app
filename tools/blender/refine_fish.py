"""Build the fish assets and an editable Blender motion study.

blender -b --python tools/blender/refine_fish.py -- --out /path/to/output
The GLBs retain the app's vertex-mask animation contract. The .blend contains
editable cyclic shape-key studies; these are previews, not app animation clips.
"""
import argparse
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
import common as C
import fishlib as FL
import fish_species as FS
import preview as PR
from common import bpy


def animate(obj, sid):
    """Model-space equivalent of the fish shader's neutral swimming pose.

    No simulation path, feeding, disease or escape input is baked into this study.
    All phases are integer cycles over two seconds for a seamless loop.
    """
    v = C.get_verts(obj)
    ca = obj.data.color_attributes.get('Col') or obj.data.color_attributes.active_color
    if not ca:
        raise ValueError(f'{obj.name}: missing animation masks')
    raw = np.empty(len(ca.data) * 4, dtype=np.float32)
    ca.data.foreach_get('color', raw)
    raw = raw.reshape(-1, 4)
    if ca.domain == 'CORNER':
        col = np.zeros((len(v), 4))
        for loop in obj.data.loops:
            col[loop.vertex_index] = raw[loop.index]
    else:
        col = raw
    u, hinge = col[:, 3], col[:, 2]
    part = obj.get('part', '')
    pec = 'Pectoral' in obj.name
    fin = part == 'fin'
    eel = sid == 'engineer_goby'
    perch = sid in ('hawkfish', 'lawnmower_blenny', 'tailspot_blenny', 'diamond_goby')
    amp = 0.055 if eel else 0.006 if perch else 0.010 if sid == 'cardinal' else 0.025
    pec_amp = 0.12 if perch else 0.5
    wave = 1.65 if eel else 0.9
    obj.shape_key_add(name='Basis')
    for k in range(32):
        phase = k / 32
        p = v.copy()
        if fin:
            if pec:
                ph = 2 * math.pi * phase * 4 - hinge * 0.65
                p[:, 0] += 0.065 * pec_amp * hinge * np.sin(ph)
                p[:, 2] += 0.022 * pec_amp * hinge * np.cos(ph)
                p[:, 1] *= 1 - 0.32 * (0.5 + 0.5 * np.cos(ph)) * pec_amp * hinge
            flutter = 0.007 * np.sin(2 * math.pi * phase * 2 - u * 6 - hinge * 1.8)
            flutter *= hinge ** 2 * min(1, amp * 8 + pec_amp * 0.25)
            p[:, 1] -= (np.sign(-p[:, 1]) if pec else 1) * flutter
        uw = np.clip(0.5 - p[:, 0], 0, 1) if fin else u
        onset = 0.06 if eel else 0.18
        t = np.maximum(0, (uw - onset) / (1 - onset))
        env = t if eel else t * t
        p[:, 1] -= amp * env * np.sin(2 * math.pi * (uw * wave - phase * 2))
        key = obj.shape_key_add(name=f'Swim_{k:02d}')
        key.data.foreach_set('co', p.ravel())
        # Linear crossfade between adjacent samples, including last -> first.
        center = 1 + k * 2
        for frame, value in ((center - 2, 0), (center, 1), (center + 2, 0)):
            key.value = value
            key.keyframe_insert('value', frame=frame)
        if k == 0:
            for frame, value in ((63, 0), (65, 1)):
                key.value = value
                key.keyframe_insert('value', frame=frame)
        key.value = 0
    # Blender 5 actions use channel bags instead of legacy action.fcurves.
    action = obj.data.shape_keys.animation_data.action
    for layer in action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for fc in bag.fcurves:
                    for point in fc.keyframe_points:
                        point.interpolation = 'LINEAR'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', default=C.BUILD)
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    FL.self_test()
    if not args.skip_build:
        for sid in FS.FISH_IDS:
            FS.build_fish(sid)
    C.reset_scene()
    for i, sid in enumerate(FS.FISH_IDS):
        before = set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=os.path.join(C.MODELS, sid + '.glb'))
        new = [o for o in bpy.data.objects if o not in before]
        for obj in new:
            if obj.type == 'MESH':
                # Preserve Col (rig data) but bypass its color multiplication.
                for mat in obj.data.materials:
                    bs = mat.node_tree.nodes.get('Principled BSDF')
                    tex = next((n for n in mat.node_tree.nodes if n.type == 'TEX_IMAGE'), None)
                    if bs and tex:
                        mat.node_tree.links.new(tex.outputs['Color'], bs.inputs['Base Color'])
                        if obj.get('part') == 'fin':
                            mat.node_tree.links.new(tex.outputs['Alpha'], bs.inputs['Alpha'])
                    elif bs:
                        for link in list(bs.inputs['Base Color'].links):
                            mat.node_tree.links.remove(link)
                        if 'pupil' in mat.name:
                            bs.inputs['Base Color'].default_value = (*C.lin('#030303'), 1)
                animate(obj, sid)
            if obj.parent is None:
                obj.location += __import__('mathutils').Vector(((i % 3 - 1) * 1.3, 0, (1.5 - i // 3) * 0.85))
        curve = bpy.data.curves.new(sid + '_label', 'FONT')
        curve.body = sid.replace('_', ' ').title()
        curve.size = 0.065
        curve.extrude = 0.0002
        label = bpy.data.objects.new(curve.name, curve)
        C.link(label)
        label.location = ((i % 3 - 1) * 1.3 - 0.5, -0.1, (1.5 - i // 3) * 0.85 - 0.36)
        label.rotation_euler = (math.pi / 2, 0, 0)
        curve.materials.append(C.material('Label', color='#B7D8E0', emissive='#B7D8E0', emissive_strength=0.3))
    PR.setup_cycles(1440, 1280, 16)
    bpy.context.scene.render.threads = min(8, os.cpu_count() or 4)
    bpy.context.scene.cycles.use_denoising = True
    PR.world((0.025, 0.045, 0.060), 0.5)
    PR.add_light('AREA', (0, -3, 4), 220, size=5)
    PR.add_light('AREA', (-3, -1, 0), 100, rot=(math.pi / 2, 0, -0.5), size=4)
    PR.camera((0, -8, 0.45), (0, 0, 0), ortho=4.1)
    scene = bpy.context.scene
    scene.render.fps = 32
    scene.frame_start, scene.frame_end = 1, 64
    scene.frame_set(1)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, 'Fish-motion-study.blend'))
    PR.render_to(os.path.join(out, 'Fish-models.png'))
    print('[refine] Saved editable motion study and overview to', out)


if __name__ == '__main__':
    main()
