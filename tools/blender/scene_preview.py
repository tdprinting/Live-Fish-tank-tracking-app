"""Assembled-scene preview (docs/specs/tank_design.md §5, §6): Cycles CPU render of every committed GLB with the
AI Prime lighting look, from the spec's verification cameras (tank interior common.TANK = 121.9 x 40.6 x 45.7 cm):
  front      (61, 26, 196) -> (61, 21, 23), FOV 32   (matches the owner's tank_full_2026-10-13.jpg framing)
  left34     (-40, 62, 165) -> (50, 17, 23), FOV 40
  right34    (170, 62, 165) -> (75, 17, 23), FOV 40
  top        (61, 170, 23) -> (61, 0, 23),  FOV 40
  left_end   (-150, 25, 23) -> (0, 20, 23), FOV 35
  right_end  (272, 25, 23) -> (122, 20, 23), FOV 35
`render_scene(view="front")` -> docs/preview/scene.png by default."""
import math
import os

import numpy as np

import common as C
import preview as PV
from common import bpy, w2b

L_, H_, D_ = C.TANK
ZC = D_ / 2
VIEWS = {
    "front": ((61, 26, D_ + 150), (61, 21, ZC), 32),
    "left34": ((-40, 62, D_ + 120), (50, 17, ZC), 40),
    "right34": ((170, 62, D_ + 120), (75, 17, ZC), 40),
    "top": ((61, 170, ZC + 0.01), (61, 0, ZC), 40),
    "left_end": ((-150, 25, ZC), (0, 20, ZC), 35),
    "right_end": ((272, 25, ZC), (122, 20, ZC), 35),
    # close-ups for the 2026-10-13 backlog details (ASSETS.md §4.1)
    "clowns": ((84, 20, D_ + 62), (82, 13, 21), 26),
    "left_glass": ((40, 22, D_ + 52), (2, 16, 18), 28),
    "upper": ((50, 40, D_ + 62), (45, 30, 16), 34),
}
# fish placements for the preview: (id, lengthCm, world pos, heading deg about +Y (0 = +X), pitch)
FISH_POSES = [
    ("clown_orange", 7.0, (79.5, 15.5, 23), 160, 0), ("clown_darwin", 5.5, (84.5, 14.5, 22.5), 20, -5),
    ("tang", 6.5, (78, 21, 34), 200, 0), ("cardinal", 5.0, (36, 11, 35), 200, 0),
    ("wrasse", 7.0, (84, 9, 37), 10, -8), ("hawkfish", 5.0, (43, 21.3, 28.6), 200, 0),
    ("lawnmower_blenny", 10.0, (15.5, 13.6, 34.5), -40, 5), ("tailspot_blenny", 5.5, (62.5, 29.9, 14.1), -85, 10),
    ("dottyback", 6.5, (84, 10, 6), 160, 0), ("engineer_goby", 20.0, None, 0, 0),
]
INVERT_POSES = [
    ("shrimp", 5.0, (95, 22.3, 13.5), 200), ("skunk_shrimp", 6.0, (40, 24.2, 19.5), 300),
    ("hermit_blue", 2.0, (30, 4.1, 38), 160), ("hermit_scarlet", 3.5, (95, 4.0, 40), 20),
    ("brittle_star", 15.0, (36, 6.0, 10.4), 0), ("nassarius", 1.5, (56, 4.6, 40), 90),
    ("astrea", 3.0, (12, 20, D_ - 0.7), 0), ("feather_duster", 1.0, (86, 5.3, 27.3), 0),
    ("sally_lightfoot", 5.0, (100.5, 27.5, 12.5), 250),
]
CORALS = ("kenya", "gsp_neon", "mushroom_hairy_mound", "zoa", "clove", "mushroom", "mushroom_hairy", "macro_red", "organ_pipe",
          "algae_tufts", "aiptasia", "reef_details")
POLYP_FILES = ("gsp_neon", "mushroom_hairy_mound", "zoa", "clove", "kenya", "organ_pipe", "mushroom_hairy")


def place(objs, scale, pos, yaw_deg, pitch_deg=0.0):
    roots = [o for o in objs if o.parent is None]
    for r in roots:
        r.scale = (scale, scale, scale)
        r.rotation_mode = 'XYZ'
        r.rotation_euler = (0, math.radians(-pitch_deg), math.radians(yaw_deg))
        r.location = w2b(pos)


def goby_in_burrow(objs):
    """Bend the engineer goby along the burrow path with its head out of the entrance (preview only)."""
    L = 20.0
    roots = [o for o in objs if o.parent is None]
    pts = [(60.5, 6.0, 37.0), (62, 5.0, 32.5), (64, 4.8, 29.2), (66, 5.0, 26.0), (68.5, 5.5, 22.8), (71, 6, 19.5),
           (75, 6.5, 13), (79, 7, 6.5)]
    cu = bpy.data.curves.new("den", 'CURVE')
    cu.dimensions = '3D'
    sp = cu.splines.new('POLY')
    sp.points.add(len(pts) - 1)
    for i, p in enumerate(pts):
        sp.points[i].co = (*w2b(p), 1)
    co = bpy.data.objects.new("den", cu)
    C.link(co)
    for r in roots:
        r.scale = (L, L, L)
        r.location = (0, 0, 0)
        r.rotation_euler = (0, 0, math.radians(180))
        for ch in r.children:
            md = ch.modifiers.new("curve", 'CURVE')
            md.object = co
            md.deform_axis = 'POS_X'


def principled(name, color, rough, transmission=0.0, ior=1.45, alpha=1.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["IOR"].default_value = ior
    b.inputs["Transmission Weight"].default_value = transmission
    b.inputs["Alpha"].default_value = alpha
    return m


def water_volume(density_abs=0.006, density_scatter=0.0008):
    """Water: absorption (greenish #2E8A78) + 1 % scatter for light shafts, box over the interior up to the water line."""
    me = bpy.data.meshes.new("water_vol")
    import bmesh
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new("WaterVol", me)
    o.scale = (L_ - 0.1, D_ - 0.1, C.WATER_Y - 0.1)
    o.location = w2b((L_ / 2, C.WATER_Y / 2, D_ / 2))
    C.link(o)
    m = bpy.data.materials.new("water_vol")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.remove(nt.nodes["Principled BSDF"])
    ab = nt.nodes.new("ShaderNodeVolumeAbsorption")
    ab.inputs["Density"].default_value = density_abs
    ab.inputs["Color"].default_value = (*C.lin("#2E8A78"), 1)
    sc = nt.nodes.new("ShaderNodeVolumeScatter")
    sc.inputs["Density"].default_value = density_scatter
    sc.inputs["Color"].default_value = (0.75, 0.9, 1.0, 1)
    add = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(ab.outputs[0], add.inputs[0])
    nt.links.new(sc.outputs[0], add.inputs[1])
    nt.links.new(add.outputs[0], nt.nodes["Material Output"].inputs["Volume"])
    me.materials.append(m)


def caustic_spot(loc, energy, color, cone=110, blend=0.4, caustics=True):
    """Spot light pointing -Y (world) with a projected caustic pattern (Voronoi edge network on the ray
    direction: ~9 cm cells at the sand)."""
    ld = bpy.data.lights.new("spot", 'SPOT')
    ld.energy = energy
    ld.color = color
    ld.spot_size = math.radians(cone)
    ld.spot_blend = blend
    ld.shadow_soft_size = 3.0
    if caustics:
        try:
            ld.use_nodes = True
            nt = ld.node_tree
            em = nt.nodes["Emission"]
            tc = nt.nodes.new("ShaderNodeTexCoord")
            mp = nt.nodes.new("ShaderNodeMapping")
            mp.inputs["Scale"].default_value = (11.0, 11.0, 11.0)
            nt.links.new(tc.outputs["Normal"], mp.inputs["Vector"])
            vo = nt.nodes.new("ShaderNodeTexVoronoi")
            vo.feature = 'DISTANCE_TO_EDGE'
            nt.links.new(mp.outputs[0], vo.inputs["Vector"])
            mr = nt.nodes.new("ShaderNodeMapRange")
            mr.inputs["From Min"].default_value = 0.0
            mr.inputs["From Max"].default_value = 0.09
            mr.inputs["To Min"].default_value = 1.6
            mr.inputs["To Max"].default_value = 0.76
            nt.links.new(vo.outputs["Distance"], mr.inputs["Value"])
            nt.links.new(mr.outputs[0], em.inputs["Strength"])
        except Exception as e:  # noqa: BLE001
            print("[scene] caustic gobo unavailable:", e)
    o = bpy.data.objects.new("spot", ld)
    o.location = w2b(loc)
    C.link(o)
    return o


def setup_materials(objs):
    glass = principled("pv_glass", C.lin("#DDEFE4"), 0.02, transmission=1.0, ior=1.52)
    surf = principled("pv_water_surface", (1, 1, 1), 0.04, transmission=1.0, ior=1.333)
    for o in objs:
        if o.type != 'MESH':
            continue
        n = o.name
        if n.startswith("Glass") and not n.startswith("GlassFilm"):
            o.data.materials.clear()
            o.data.materials.append(glass)
            o.visible_shadow = False
        elif n.startswith("WaterSurface"):
            o.data.materials.clear()
            o.data.materials.append(surf)
            o.visible_shadow = False
        elif n.startswith("WaterVolume"):
            o.hide_render = True
        elif n.startswith(("Seams", "GlassFilm")):
            o.visible_shadow = False


def build_scene(samples, w, h, view):
    C.reset_scene()
    PV.setup_cycles(w, h, samples)
    bpy.context.scene.cycles.use_denoising = True
    s = bpy.context.scene
    s.cycles.max_bounces = 6
    s.cycles.transmission_bounces = 8
    s.cycles.transparent_max_bounces = 24
    s.cycles.volume_bounces = 0
    s.cycles.caustics_refractive = False
    s.cycles.caustics_reflective = False
    s.view_settings.view_transform = 'AgX'
    s.view_settings.look = 'AgX - Medium High Contrast'
    s.view_settings.exposure = 0.3
    PV.world((0.02, 0.018, 0.022), 1.0)
    M = C.MODELS
    tank = PV.import_glb(os.path.join(M, "tank.glb"))
    setup_materials(tank)
    for f in ("rockscape", "equipment") + CORALS:
        p = os.path.join(M, f + ".glb")
        if os.path.exists(p):
            objs = PV.import_glb(p)
            if f in POLYP_FILES:
                instance_polyps(objs)
            if f == "equipment":
                for o in objs:
                    if o.type == 'MESH' and o.name.startswith(("HoseArc", "Jar")):
                        o.visible_shadow = False
    for sid, L, pos, yaw, pitch in FISH_POSES:
        p = os.path.join(M, sid + ".glb")
        if not os.path.exists(p):
            continue
        objs = PV.import_glb(p)
        if sid == "engineer_goby":
            goby_in_burrow(objs)
        else:
            place(objs, L, pos, yaw, pitch)
    for sid, L, pos, yaw in INVERT_POSES:
        p = os.path.join(M, sid + ".glb")
        if os.path.exists(p):
            place(PV.import_glb(p), L, pos, yaw)
    # AI Prime HD x2 (spec §5): blue-heavy 16k look = blue #8FA9FF (85 %) + warm white #FFF1DC (15 %) per puck,
    # cone 110 deg, blend 0.4; projected caustics; faint violet wall-bounce fill; warm room light for the stand/wall
    import equipment as EQ
    lights = [(32.0, C.RIM_Y + 28.4, EQ.LIGHT_Z), (88.0, C.RIM_Y + 28.4, EQ.LIGHT_Z)]  # AiPrimeL/R_Lens lightPos
    for p in lights:
        caustic_spot(p, 300000 * 0.6, C.lin("#8FA9FF"))
        caustic_spot(p, 53000 * 0.6, C.lin("#FFF1DC"), caustics=False)
    # the pucks' lens optics are collimated: two narrow high spots stand in for that so the sand (80 % luminance in
    # the photo) is not starved by inverse-square falloff from the puck height
    for x, _, z in lights:
        caustic_spot((x, 400, z), 2600000, C.lin("#A9BCFF"), cone=24, blend=0.5)
    PV.add_light('AREA', w2b((61, 120, 70)), 60000, size=160, color=C.lin("#6A5BD6"))
    PV.add_light('AREA', w2b((61, 130, 190)), 160000, size=220, color=C.lin("#FFE2C4"))
    water_volume()
    loc, tgt, fov = VIEWS[view]
    cam = PV.camera(w2b(loc), w2b(tgt))
    cam.data.sensor_fit = 'VERTICAL'
    cam.data.angle_y = math.radians(fov)
    cam.data.clip_end = 2000
    return cam


def render_scene(samples=None, w=1600, h=1200, out=None, view="front"):
    build_scene(samples or 64, w, h, view)
    out = out or os.path.join(C.PREVIEW, "scene.png" if view == "front" else f"scene_{view}.png")
    PV.render_to(out)
    print("[scene] wrote", out)
    return out


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
            allF += [tuple(i + k * len(V0) for i in f) for f in F0]
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


if __name__ == "__main__":
    import sys
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    view = args[0] if args else "front"
    render_scene(int(args[1]) if len(args) > 1 else 64, int(args[2]) if len(args) > 2 else 1600,
                 int(args[3]) if len(args) > 3 else 1200, view=view)
