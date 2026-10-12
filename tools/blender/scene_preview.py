"""Assembled-scene preview (ASSETS.md §8): Cycles CPU render of every committed GLB from the front camera
(61, 35, 140) -> (61, 28, 16) world cm, 1280x720 -> docs/preview/scene.png."""
import math
import os

import numpy as np

import common as C
import preview as PV
from common import bpy, w2b

# fish placements for the preview: (id, lengthCm, world pos, heading deg about +Y (0 = +X), pitch)
FISH_POSES = [
    ("clown_orange", 7.0, (86, 24, 22), 160, 0), ("clown_darwin", 5.5, (79, 30, 20), 20, -5),
    ("tang", 6.5, (100, 40, 16), 200, 0), ("cardinal", 5.0, (29, 16, 23), 200, 0),
    ("wrasse", 7.0, (64, 18, 21), 10, -8), ("hawkfish", 5.0, (49, 30, 21), 200, 0),
    ("lawnmower_blenny", 10.0, (27, 18, 25.5), 30, 5), ("tailspot_blenny", 5.5, (53, 14, 27.5), 70, 10),
    ("dottyback", 6.5, (93, 30, 12), 180, 0), ("eel", 35.0, None, 0, 0),
]
INVERT_POSES = [
    ("shrimp", 5.0, (88, 36, 16), 200), ("skunk_shrimp", 6.0, (66, 22, 18), 300),
    ("hermit_blue", 2.0, (40, 3.0, 29), 160), ("hermit_scarlet", 3.5, (96, 3.0, 29), 20),
    ("brittle_star", 15.0, (36, 3.1, 12), 0), ("nassarius", 1.5, (58, 2.7, 30), 90),
    ("astrea", 3.0, (11, 22, 30.8), 0), ("feather_duster", 1.0, (85, 2.0, 21), 0),
]


def place(objs, scale, pos, yaw_deg, pitch_deg=0.0):
    roots = [o for o in objs if o.parent is None]
    for r in roots:
        r.scale = (scale, scale, scale)
        r.rotation_mode = 'XYZ'
        r.rotation_euler = (0, math.radians(-pitch_deg), math.radians(yaw_deg))
        r.location = w2b(pos)


def eel_in_den(objs):
    """Bend the eel along the den path with the head out of the den (preview only)."""
    import mathutils
    L = 35.0
    roots = [o for o in objs if o.parent is None]
    for r in roots:
        r.scale = (L, L, L)
        r.rotation_euler = (0, 0, math.radians(-150))
        r.location = w2b((66.5, 6.0, 26.5))
    # curve modifier along a path that runs back into the den
    pts = [(66.5, 6.0, 26.5), (69, 5.6, 23), (73, 6, 19), (77, 7, 15), (80, 8.5, 11), (83, 9.5, 8), (86, 9.5, 5)]
    cu = bpy.data.curves.new("den", 'CURVE')
    cu.dimensions = '3D'
    sp = cu.splines.new('POLY')
    sp.points.add(len(pts) - 1)
    for i, p in enumerate(pts):
        sp.points[i].co = (*w2b(p), 1)
    co = bpy.data.objects.new("den", cu)
    C.link(co)
    for r in roots:
        r.location = (0, 0, 0)
        r.rotation_euler = (0, 0, math.radians(180))
        for ch in r.children:
            md = ch.modifiers.new("curve", 'CURVE')
            md.object = co
            md.deform_axis = 'POS_X'


def render_scene(samples=None, w=1280, h=720, out=None):
    C.reset_scene()
    PV.setup_cycles(w, h, samples or 64)
    s = bpy.context.scene
    s.cycles.max_bounces = 4
    s.cycles.transparent_max_bounces = 24
    PV.world((0.012, 0.03, 0.05), 1.0)
    M = C.MODELS
    for f in ("tank", "rockscape", "equipment", "kenya", "gsp_neon", "gsp_mat", "zoa", "clove", "mushroom",
              "macro_red", "organ_pipe", "algae_tufts"):
        p = os.path.join(M, f + ".glb")
        if os.path.exists(p):
            objs = PV.import_glb(p)
            for o in objs:
                if o.name.startswith("GlassFront") or o.name.startswith("Stand") or o.name.startswith("WaterSurface"):
                    o.hide_render = True
                if f == "tank" and o.name.startswith(("GlassLeft", "GlassRight")):
                    o.hide_render = True
            if f in ("gsp_neon", "gsp_mat", "zoa", "clove", "kenya", "organ_pipe"):
                instance_polyps(objs)
    for sid, L, pos, yaw, pitch in FISH_POSES:
        p = os.path.join(M, sid + ".glb")
        if not os.path.exists(p):
            continue
        objs = PV.import_glb(p)
        if sid == "eel":
            eel_in_den(objs)
        else:
            place(objs, L, pos, yaw, pitch)
    for sid, L, pos, yaw in INVERT_POSES:
        p = os.path.join(M, sid + ".glb")
        if os.path.exists(p):
            place(PV.import_glb(p), L, pos, yaw)
    # AI Prime HD fixtures: two spot lights 29 cm above the rim + soft fill
    for x in (30.5, 91.4):
        PV.add_light('SPOT', w2b((x, 88, 16.5)), 45000, rot=(0, 0, 0), color=(0.78, 0.86, 1.0), spot=120)
        PV.add_light('AREA', w2b((x, 70, 16.5)), 3000, size=14, color=(0.55, 0.7, 1.0))
    PV.add_light('AREA', w2b((61, 30, 90)), 5000, size=80, color=(0.7, 0.8, 1.0))
    for o in bpy.context.scene.objects:
        if o.type == 'LIGHT' and o.data.type in ('SPOT', 'AREA'):
            pass
    # lights point down (blender -Z is default for spot/area)
    PV.camera(w2b((61, 35, 140)), w2b((61, 28, 16)), lens=33)
    # underwater haze: a thin volume box filling the water
    haze(0.0035)
    out = out or os.path.join(C.PREVIEW, "scene.png")
    px = PV.render_to(out)
    print("[scene] wrote", out)
    return out


def haze(density):
    me = bpy.data.meshes.new("haze")
    import bmesh
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new("Haze", me)
    o.scale = (121.8, 32.9, 52.9)
    o.location = w2b((60.95, 3.0 + 26.45, 16.5))
    C.link(o)
    m = bpy.data.materials.new("haze")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.remove(nt.nodes["Principled BSDF"])
    vol = nt.nodes.new("ShaderNodeVolumeScatter")
    vol.inputs["Density"].default_value = density
    vol.inputs["Color"].default_value = (0.55, 0.8, 1.0, 1)
    nt.links.new(vol.outputs[0], nt.nodes["Material Output"].inputs["Volume"])
    me.materials.append(m)


def instance_polyps(objs):
    """Expand extras.instances (pos3 quat4 scale phase variant) into real copies for the preview."""
    import mathutils
    for o in objs:
        inst = o.get("instances")
        if inst is None or o.type != 'MESH':
            continue
        arr = np.array(list(inst), float).reshape(-1, int(o.get("instanceStride", 10)))
        me = o.data
        V0 = C.get_verts(o)
        F0 = [tuple(p.vertices) for p in me.polygons]
        allV, allF = [], []
        for k, row in enumerate(arr):
            pos, q, sc = row[0:3], row[3:7], row[7]
            # instance data is in glTF (y-up) coordinates -> blender
            qb = mathutils.Quaternion((q[3], q[0], -q[2], q[1]))
            R = np.array(qb.to_matrix())
            Vt = (V0 * sc) @ R.T + np.array(w2b(pos))
            allF += [tuple(i + len(allV) * 0 + k * len(V0) for i in f) for f in F0]
            allV.append(Vt)
        allV = np.vstack(allV)
        nm = bpy.data.meshes.new(o.name + "_inst")
        nm.from_pydata([tuple(v) for v in allV], [], allF)
        nm.update()
        if me.uv_layers:
            src = np.zeros(len(me.loops) * 2, np.float32)
            me.uv_layers[0].data.foreach_get("uv", src)
            uvl = nm.uv_layers.new(name="UVMap")
            uvl.data.foreach_set("uv", np.tile(src, len(arr)))
        for ca in me.color_attributes:  # the importer's materials read the color attribute: keep it white
            nca = nm.color_attributes.new(ca.name, ca.data_type, ca.domain)
            nca.data.foreach_set("color", np.ones(len(nca.data) * 4, np.float32))
        for m in me.materials:
            nm.materials.append(m)
        no = bpy.data.objects.new(o.name + "_inst", nm)
        C.link(no)
        o.hide_render = True
