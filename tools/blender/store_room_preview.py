"""Preview renders of the fish store rooms (store_rooms.py), laid out the way render/StoreRoom.kt draws them: an entry
bay, one bay per display tank of the given kinds (the tank the keeper stands at is a prop tank here), the counter bay
and a spare bay, people where the renderer stands them.

    python3 tools/blender/store_room_preview.py <store id> <out.png> [kinds, e.g. 2,3,4] [aisle|close|counter]
"""
import math
import os
import sys

import common as C
import preview as PV
from common import bpy

import store_rooms as SR

CX = SR.CX


def place(objs, name, x, z=0.0, y=0.0, yaw=0.0, base=None):
    """A linked copy of node [name] moved by (x, y, z) world cm and turned [yaw] about world y."""
    src = objs.get(name)
    if src is None:
        return None
    o = src.copy()
    bpy.context.scene.collection.objects.link(o)
    o.parent = None
    o.hide_render = False
    o.matrix_world = src.matrix_world.copy()
    o.rotation_mode = 'XYZ'
    o.rotation_euler.z += yaw
    o.location.x += x
    o.location.y -= z
    o.location.z += y
    return o


def person(objs, slot, x, z, yaw):
    """Person [slot] standing at world (x, floor, z) turned [yaw]: every part keeps its joint offset from the feet."""
    for part in ("Torso", "Head", "ArmL", "ArmR", "LegL", "LegR"):
        src = objs.get(f"P{slot}_{part}")
        if src is None:
            return
        lx, ly, lz = src.matrix_world.translation
        c, s = math.cos(yaw), math.sin(yaw)
        o = place(objs, src.name, 0.0, yaw=yaw)
        o.location = (x + lx * c - ly * s, -z + lx * s + ly * c, SR.FLOOR + lz)


def main(argv):
    sid = argv[0]
    out = argv[1]
    kinds = [int(k) for k in (argv[2] if len(argv) > 2 else "2,3,4").split(",")]
    view = argv[3] if len(argv) > 3 else "aisle"
    at = 0
    SR.configure(sid)
    BAY = SR.BAY
    C.reset_scene()
    PV.setup_cycles(960, 540, 40)
    PV.world((0.02, 0.02, 0.025), 0.3)
    path = os.path.join(C.MODELS, f"store_room_{sid}.glb")
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    objs = {o.name: o for o in bpy.data.objects if o not in before}
    for o in objs.values():
        if o.type == 'MESH':
            o.hide_render = True
    # COLOR_0 only paints the paint_vcol material (store.frag); elsewhere the importer must not multiply it in
    for m in bpy.data.materials:
        if m.node_tree is None or m.name.startswith("paint_vcol"):
            continue
        for n in list(m.node_tree.nodes):
            if n.type in ('VERTEX_COLOR', 'ATTRIBUTE'):
                m.node_tree.nodes.remove(n)
    L0 = SR.KINDS[kinds[at]]["L"]
    for bay in range(-1, len(kinds) + 2):
        bx = L0 / 2 - CX + (bay - at) * BAY
        place(objs, "Bay", bx)
        place(objs, "FrontDoor" if bay == 0 else "FrontBay", bx)
        place(objs, "Roof", bx)
        if bay == -1:
            place(objs, "EndL", bx)
            place(objs, "Entry", bx)
            place(objs, "Neighbor", bx - BAY)
        if bay == len(kinds) + 1:
            place(objs, "EndR", bx)
            place(objs, "Back", bx)
            place(objs, "Neighbor", bx + BAY)
        if bay == 0:
            for n in ("FrontSign", "Street", "Sky", "DoorL", "DoorR", "DoorUp"):
                place(objs, n, bx)
        if bay == len(kinds):
            place(objs, "Counter", bx)
            person(objs, 0, bx + CX, 15.0, 0.2)
        if bay == 0:
            place(objs, "Sign", bx)
        place(objs, f"Decor{(bay % 2 + 2) % 2}", bx)
        if 0 <= bay < len(kinds):
            k = kinds[bay]
            L = SR.KINDS[k]["L"]
            tx = L0 / 2 + (bay - at) * BAY - L / 2
            for n in (f"Stand{k}", f"PropTank{k}", f"PropReef{k}", f"PropFish{k}", f"Lamp{k}"):
                place(objs, n, tx)
            # light under each lamp (Cycles stand-in for store.frag's bay lights)
            H = SR.KINDS[k]["H"]
            PV.add_light('AREA', (tx + L / 2, -SR.KINDS[k]["D"] / 2, H + 30), 2.5e6, size=80, color=(0.85, 0.9, 1.0))
    # the rest of the cast along the aisle in front of the tanks
    n = sum(1 for o in objs if o.endswith("_Torso"))
    for slot in range(1, n):
        x = L0 / 2 + (slot - 1.5) * BAY * 0.55
        person(objs, slot, x, 110 + (slot % 2) * 40, math.pi + 0.5 * (slot % 3 - 1))
    if "Pet_Body" in objs:
        place(objs, "Pet_Body", L0 / 2 + 40, z=170, y=SR.FLOOR, yaw=0.8)
    if view == "outside":
        sun = PV.add_light('SUN', (0, 0, 1000), 4.0, color=(1.0, 0.95, 0.85))
        sun.rotation_euler = (math.radians(50), 0, math.radians(30))
    for bay in range(-1, len(kinds) + 2):
        bx = L0 / 2 + (bay - at) * BAY
        PV.add_light('AREA', (bx, -170, SR.CEIL - 5), 2.0e6, size=90, color=(1.0, 0.96, 0.9))
    if view == "close":
        PV.camera((L0 / 2, -175, 30), (L0 / 2, -20, 0), lens=30)
    elif view == "counter":
        cx = L0 / 2 + len(kinds) * BAY
        PV.camera((cx - 60, -330, 70), (cx, 0, 0), lens=26)
    elif view == "outside":
        PV.camera((L0 / 2, -(SR.FRONT + 360), 80), (L0 / 2, -SR.FRONT, 70), lens=18)
    else:
        PV.camera((L0 / 2 + 60, -420, 95), (L0 / 2 + 90, 0, -5), lens=18)
    bpy.context.scene.camera.data.clip_end = 5000
    PV.render_to(out)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1:])
