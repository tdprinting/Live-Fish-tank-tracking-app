"""The four fish stores of My tank's economy (docs/ECONOMY.md §8): small, warm, low-poly shop-front dioramas.

  Shelly's Pet Shop   friendly mall pet shop, striped awning, three lit tanks in the window, a shell sign
  Frag Shack          a reefer's garage, door rolled up, frag tanks under purple-blue reef lights, a surfboard
  Big Blue Aquatics   the big blue box store, a wall of tanks behind glass, a giant tang mural, neon sign
  Deep Reef Imports   dark glass and gold, one big lit display tank with a rare fish, gold lettering

Each store is exported to app/src/main/assets/models/store_<id>.glb (metres, Y-up) and rendered to the storefront
picture the store screens show: app/src/main/res/drawable-nodpi/store_<id>.webp (the web build copies it too).

    python3 tools/blender/stores.py [store ids...]   (bpy from PyPI; no Blender install needed)
"""
import math
import os
import sys

import common as C
import preview as PV
from common import bpy

OUT = os.path.join(C.REPO, "app", "src", "main", "res", "drawable-nodpi")
STORES = ["shellys", "frag_shack", "big_blue", "deep_reef"]


# ============================================================== building blocks
_mats = {}


def mat(color, rough=0.6, metal=0.0, emit=None, strength=1.0, alpha=1.0):
    key = (color, rough, metal, emit, strength, alpha)
    if key not in _mats:
        _mats[key] = C.material(f"m{len(_mats)}", color, rough=rough, metal=metal, emissive=emit,
                                emissive_strength=strength, alpha=alpha, blend=alpha < 1.0)
    return _mats[key]


def _finish(o, m, bevel=0.0):
    o.data.materials.clear()
    o.data.materials.append(m)
    if bevel > 0:
        md = o.modifiers.new("bev", 'BEVEL')
        md.width = bevel
        md.segments = 2
        md.limit_method = 'ANGLE'
    return o


def box(size, loc, m, bevel=0.0, rot=(0, 0, 0)):
    """Box of full [size] (x, y, z) centred at [loc] (Blender Z-up)."""
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return _finish(o, m, bevel)


def hole(wall, size, loc):
    """Cuts a box-shaped opening of [size] at [loc] through [wall] (a window or a doorway to see inside)."""
    cutter = box(size, loc, mat("#000000"))
    md = wall.modifiers.new("cut", 'BOOLEAN')
    md.operation = 'DIFFERENCE'
    md.object = cutter
    md.solver = 'EXACT'
    C.apply_modifiers(wall)
    bpy.data.objects.remove(cutter, do_unlink=True)
    return wall


def interior(width, depth, height, y0, color="#2A2238", light=(1.0, 0.85, 0.65), energy=120):
    """A lit room behind the front wall: floor, back wall and a warm lamp, seen through the openings."""
    box((width, 0.1, height), (0, y0 + depth, height / 2), mat(color, 0.9))
    box((width, depth, 0.05), (0, y0 + depth / 2, 0.08), mat("#6B5A4A", 0.8))
    PV.add_light('POINT', (0, y0 + depth * 0.4, height * 0.8), energy, size=0.5, color=light)


def cyl(r, depth, loc, m, rot=(0, 0, 0), verts=24, r2=None):
    if r2 is None:
        bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=depth, location=loc, rotation=rot, vertices=verts)
    else:
        bpy.ops.mesh.primitive_cone_add(radius1=r, radius2=r2, depth=depth, location=loc, rotation=rot, vertices=verts)
    o = bpy.context.active_object
    bpy.ops.object.shade_smooth()
    return _finish(o, m)


def ball(r, loc, m, scale=(1, 1, 1), segs=16):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=loc, segments=segs, ring_count=max(6, segs // 2))
    o = bpy.context.active_object
    o.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    bpy.ops.object.shade_smooth()
    return _finish(o, m)


def text(body, loc, size, m, depth=0.02, rot=(math.radians(90), 0, 0), align='CENTER', font_bold=True):
    bpy.ops.object.text_add(location=loc, rotation=rot)
    o = bpy.context.active_object
    o.data.body = body
    o.data.size = size
    o.data.extrude = depth
    o.data.align_x = align
    o.data.align_y = 'CENTER'
    o.data.bevel_depth = depth * 0.25
    o.data.materials.append(m)
    bpy.ops.object.convert(target='MESH')
    return bpy.context.active_object


def fish(loc, length, body, fin=None, yaw=0.0):
    """A little cartoon fish facing +x (rotated by [yaw]): an egg body, a tail fan and two eyes."""
    fin = fin or body
    parts = [ball(length * 0.32, loc, mat(body, 0.35), scale=(1.0, 0.42, 0.62))]
    tx = loc[0] - length * 0.42 * math.cos(yaw)
    ty = loc[1] - length * 0.42 * math.sin(yaw)
    parts.append(cyl(length * 0.2, length * 0.22, (tx, ty, loc[2]), mat(fin, 0.4), rot=(0, math.radians(90), yaw), verts=3, r2=0.0))
    for s in (-1, 1):
        ex = loc[0] + length * 0.18 * math.cos(yaw) - s * length * 0.1 * math.sin(yaw)
        ey = loc[1] + length * 0.18 * math.sin(yaw) + s * length * 0.1 * math.cos(yaw)
        parts.append(ball(length * 0.045, (ex, ey, loc[2] + length * 0.05), mat("#14102E", 0.2), segs=8))
    return parts


def tank(center, size, water="#2A9FD6", glow=1.1, fish_cols=("#FF8A2A",), rock="#8A7B6E", coral=None, n_fish=3):
    """A lit aquarium: glass box, glowing water, a rock mound, a few corals and fish."""
    x, y, z = center
    w, d, h = size
    box((w, d, h), center, mat("#DDF4FF", 0.05, alpha=0.18))
    box((w * 0.94, d * 0.6, h * 0.86), (x, y + d * 0.15, z - h * 0.04), mat(water, 0.2, emit=water, strength=glow, alpha=0.8))
    box((w * 1.02, d * 1.04, h * 0.06), (x, y, z + h * 0.5), mat("#1B1340", 0.5))
    box((w * 0.94, d * 0.9, h * 0.08), (x, y, z - h * 0.45), mat("#EAD9B5", 0.9))
    ball(min(w, h) * 0.22, (x - w * 0.18, y, z - h * 0.32), mat(rock, 0.9), scale=(1.4, 0.8, 0.8))
    for i, c in enumerate(coral or ("#7CFF6B", "#FF6FAE")):
        cx = x + w * (0.08 + 0.18 * i)
        cyl(h * 0.05, h * 0.22, (cx, y, z - h * 0.3), mat(c, 0.5, emit=c, strength=0.6), verts=8, r2=h * 0.08)
    for i in range(n_fish):
        fx = x + w * (-0.3 + 0.3 * i)
        fz = z + h * (0.05 + 0.12 * ((i * 7) % 3 - 1))
        fish((fx, y - d * 0.46, fz), min(w, h) * 0.3, fish_cols[i % len(fish_cols)], yaw=math.radians(180 if i % 2 else 0))


def awning(x0, x1, y, z, depth, cols, n=10):
    """A striped shop awning sloping out from the wall at height [z], with a scalloped edge."""
    w = (x1 - x0) / n
    for i in range(n):
        cx = x0 + w * (i + 0.5)
        box((w, depth, 0.03), (cx, y - depth / 2, z), mat(cols[i % 2], 0.7), rot=(math.radians(-22), 0, 0))
        ball(w * 0.5, (cx, y - depth * 0.93, z - depth * 0.37 - 0.02), mat(cols[i % 2], 0.7), scale=(1, 0.15, 0.6), segs=12)


def plant(loc, pot="#C8693E", leaf="#3FA34D", s=1.0):
    x, y, z = loc
    cyl(0.16 * s, 0.3 * s, (x, y, z + 0.15 * s), mat(pot, 0.8), r2=0.12 * s)
    for i in range(5):
        a = i * 2 * math.pi / 5
        ball(0.12 * s, (x + math.cos(a) * 0.08 * s, y + math.sin(a) * 0.08 * s, z + 0.42 * s + 0.05 * (i % 2)), mat(leaf, 0.7), scale=(1, 1, 1.4))


def ground(width, sidewalk="#B9B4AA", street="#3B3F48"):
    box((width + 4, 2.4, 0.12), (0, -1.2, 0.0), mat(sidewalk, 0.95))
    box((width + 4, 4.0, 0.08), (0, -4.4, -0.03), mat(street, 0.95))
    for i in range(-3, 4):
        box((0.9, 0.1, 0.01), (i * 2.0, -4.6, 0.02), mat("#F2E6A6", 0.8))
    box((width + 4, 0.18, 0.18), (0, -2.45, 0.03), mat("#9A958C", 0.9))
    box((width + 4, 6, 0.1), (0, 3.0, -0.02), mat("#4F6B47", 1.0))


# ============================================================== the stores
def shellys():
    W = 6.0
    ground(W)
    wall, trim = mat("#F6E7C8", 0.85), mat("#FF7AA8", 0.6)
    front = box((W, 0.25, 3.6), (0, 0.12, 1.8), wall)
    hole(front, (3.4, 1.0, 1.5), (-1.0, 0.1, 1.55))
    box((W, 0.2, 3.6), (0, 3.0, 1.8), wall)
    for x in (-W / 2 + 0.1, W / 2 - 0.1):
        box((0.2, 3.0, 3.6), (x, 1.5, 1.8), wall)
    box((W + 0.2, 3.2, 0.25), (0, 1.5, 3.7), trim, bevel=0.03)
    interior(W - 0.4, 2.6, 3.4, 0.3, color="#F2D8E4", energy=260)
    # big window with three lit tanks on a stand
    box((3.6, 0.08, 0.12), (-1.0, -0.02, 0.76), mat("#1B1340", 0.5))
    box((3.6, 0.08, 0.12), (-1.0, -0.02, 2.34), mat("#1B1340", 0.5))
    box((3.4, 0.03, 1.5), (-1.0, 0.0, 1.55), mat("#BFE9FF", 0.05, alpha=0.12))
    box((3.3, 0.6, 0.5), (-1.0, 0.6, 0.95), mat("#7A4E2D", 0.7))
    for i, (cols, water) in enumerate([(("#FF8A2A", "#FFFFFF"), "#33B5E5"), (("#2B6CFF", "#FFE14D"), "#2FA7D9"), (("#FF5C6C",), "#44C7E8")]):
        tank((-2.1 + i * 1.1, 0.6, 1.55), (0.95, 0.45, 0.65), water=water, fish_cols=cols, n_fish=2)
    # door with a round porthole window
    box((1.2, 0.12, 2.3), (1.9, -0.02, 1.2), mat("#2FA7D9", 0.5), bevel=0.02)
    cyl(0.28, 0.06, (1.9, -0.1, 1.65), mat("#BFE9FF", 0.05, emit="#9FE0FF", strength=0.6), rot=(math.radians(90), 0, 0))
    cyl(0.06, 0.08, (2.3, -0.12, 1.15), mat("#FFE14D", 0.3, metal=0.8), rot=(math.radians(90), 0, 0))
    awning(-W / 2, W / 2, -0.05, 2.75, 1.0, ("#FF7AA8", "#FFFFFF"), n=12)
    # the shell sign: a pink scallop fan above the name
    for i in range(7):
        a = math.radians(-60 + i * 20)
        cyl(0.07, 0.75, (0.35 * math.sin(a), -0.12, 3.95 + 0.35 * math.cos(a)), mat("#FFB3CF", 0.5), rot=(0, a, 0), verts=10, r2=0.13)
    text("SHELLY'S PET SHOP", (0, -0.14, 3.3), 0.36, mat("#1B1340", 0.4))
    plant((-2.7, -0.4, 0.06)); plant((2.75, -0.4, 0.06), pot="#5FD7FF")
    # bench and a fish bowl on it
    box((1.2, 0.4, 0.08), (-0.2, -1.6, 0.48), mat("#7A4E2D", 0.7))
    for sx in (-0.75, 0.35):
        box((0.08, 0.35, 0.44), (sx, -1.6, 0.24), mat("#1B1340", 0.5))
    ball(0.22, (0.15, -1.6, 0.72), mat("#BFE9FF", 0.05, alpha=0.3))
    fish((0.15, -1.6, 0.72), 0.16, "#FF8A2A")
    return W


def frag_shack():
    W = 6.0
    ground(W, sidewalk="#A9A39A")
    tin = mat("#9BA6AE", 0.45, metal=0.6)
    front = box((W, 0.2, 3.2), (0, 0.1, 1.6), tin)
    hole(front, (4.4, 1.0, 2.6), (0, 0.1, 1.3))
    box((W, 0.2, 3.2), (0, 3.2, 1.6), tin)
    for x in (-W / 2 + 0.1, W / 2 - 0.1):
        box((0.2, 3.2, 3.2), (x, 1.6, 1.6), tin)
    interior(W - 0.4, 2.8, 3.0, 0.25, color="#1C1830", light=(0.7, 0.6, 1.0), energy=60)
    for i in range(int(W / 0.25)):
        x = -W / 2 + 0.125 + i * 0.25
        if abs(x) > 2.2:
            box((0.05, 0.04, 3.2), (x, -0.02, 1.6), mat("#B7C1C8", 0.4, metal=0.6))
        else:
            box((0.05, 0.04, 0.6), (x, -0.02, 2.9), mat("#B7C1C8", 0.4, metal=0.6))
    # roof edge and the open garage door rolled up at the top
    box((W + 0.3, 3.5, 0.18), (0, 1.6, 3.25), mat("#6E7B85", 0.6, metal=0.5))
    cyl(0.22, 4.5, (0, -0.15, 2.72), mat("#C9D1D6", 0.4, metal=0.7), rot=(0, math.radians(90), 0))
    # inside: three shelves of shallow frag tanks under purple-blue reef lights
    corals = ["#7CFF6B", "#FF6FAE", "#FFB13D", "#5FD7FF", "#B18CFF", "#E9FF5C"]
    for row, zz in enumerate((0.55, 1.25, 1.95)):
        box((4.0, 0.9, 0.06), (0, 0.6, zz - 0.2), mat("#3A3F48", 0.7))
        box((3.9, 0.8, 0.22), (0, 0.6, zz - 0.06), mat("#3B4BFF", 0.3, emit="#4A3BFF", strength=2.5, alpha=0.6))
        for k in range(10):
            c = corals[(k + row * 3) % len(corals)]
            ball(0.07, (-1.75 + k * 0.39, 0.45, zz + 0.07), mat(c, 0.4, emit=c, strength=1.8), scale=(1, 1, 0.8 + 0.4 * (k % 2)), segs=10)
        box((3.9, 0.2, 0.05), (0, 0.6, zz + 0.38), mat("#B18CFF", 0.3, emit="#9D6BFF", strength=8))
    # hand-painted sign board on two posts
    box((3.4, 0.08, 0.7), (0, -0.12, 3.75), mat("#E8C27A", 0.9), bevel=0.02)
    for x in (-1.4, 1.4):
        box((0.08, 0.08, 0.5), (x, -0.1, 3.3), mat("#7A4E2D", 0.8))
    text("FRAG SHACK", (0, -0.18, 3.75), 0.42, mat("#0E3B5A", 0.6))
    # a surfboard leaning by the door and a cooler
    box((0.5, 0.08, 2.0), (2.6, -0.45, 1.05), mat("#5FD7FF", 0.3), bevel=0.2, rot=(math.radians(-10), math.radians(8), 0))
    box((0.06, 0.09, 1.9), (2.6, -0.5, 1.05), mat("#FF7AA8", 0.3), rot=(math.radians(-10), math.radians(8), 0))
    box((0.7, 0.45, 0.45), (-2.5, -0.6, 0.29), mat("#FF5C6C", 0.5), bevel=0.04)
    box((0.72, 0.47, 0.08), (-2.5, -0.6, 0.53), mat("#FFFFFF", 0.5), bevel=0.02)
    plant((2.4, -1.9, 0.06), pot="#E8C27A", leaf="#2E8B57", s=0.8)
    return W


def big_blue():
    W = 8.0
    ground(W)
    blue = mat("#1F6FD1", 0.55)
    front = box((W, 0.25, 4.4), (0, 0.12, 2.2), blue)
    hole(front, (5.0, 1.0, 2.4), (-0.9, 0.1, 1.55))
    hole(front, (1.5, 1.0, 2.3), (2.6, 0.1, 1.2))
    box((W, 0.2, 4.4), (0, 3.6, 2.2), blue)
    for x in (-W / 2 + 0.1, W / 2 - 0.1):
        box((0.2, 3.6, 4.4), (x, 1.8, 2.2), blue)
    box((W + 0.2, 3.8, 0.3), (0, 1.8, 4.5), mat("#0E3B5A", 0.5))
    interior(W - 0.4, 3.2, 4.2, 0.3, color="#16324F", light=(0.8, 0.9, 1.0), energy=300)
    # glass front showing a wall of tanks
    box((5.0, 0.03, 2.4), (-0.9, 0.0, 1.55), mat("#BFE9FF", 0.05, alpha=0.12))
    waters = ["#2FA7D9", "#33B5E5", "#3A8DFF", "#44C7E8"]
    fishes = [("#FF8A2A",), ("#2B6CFF", "#FFE14D"), ("#FF5C6C",), ("#FFE14D",)]
    for r in range(3):
        for c in range(4):
            i = r * 4 + c
            tank((-2.75 + c * 1.22, 0.7, 0.75 + r * 0.78), (1.1, 0.45, 0.68), water=waters[i % 4], fish_cols=fishes[i % 4], n_fish=1,
                 coral=(("#7CFF6B",) if i % 2 else ("#FF6FAE",)))
    # sliding doors
    box((0.75, 0.05, 2.3), (2.2, -0.08, 1.25), mat("#BFE9FF", 0.05, alpha=0.3))
    box((0.75, 0.05, 2.3), (3.0, -0.08, 1.25), mat("#BFE9FF", 0.05, alpha=0.3))
    # neon sign and a giant blue tang mural above
    text("BIG BLUE AQUATICS", (-0.4, -0.1, 3.65), 0.48, mat("#5FD7FF", 0.3, emit="#5FD7FF", strength=6))
    tang = fish((3.05, -0.1, 3.0), 1.5, "#2B4BFF", fin="#FFE14D")
    for o in tang:
        o.scale.y = 0.2
    # parking lines and a sign post
    for i in range(4):
        box((0.08, 1.6, 0.01), (-3 + i * 2, -3.2, 0.02), mat("#F2F6FF", 0.8))
    cyl(0.06, 2.2, (-3.6, -1.9, 1.1), mat("#9AA5AE", 0.4, metal=0.7))
    ball(0.45, (-3.6, -1.9, 2.4), mat("#5FD7FF", 0.3, emit="#5FD7FF", strength=1.5), scale=(1, 0.2, 1))
    plant((-3.6, -0.5, 0.06), pot="#0E3B5A"); plant((3.7, -0.5, 0.06), pot="#0E3B5A")
    return W


def deep_reef():
    W = 6.0
    ground(W, sidewalk="#8F8A84", street="#2A2D34")
    dark, gold = mat("#151A2E", 0.4), mat("#E2B64B", 0.25, metal=0.9)
    front = box((W, 0.25, 4.0), (0, 0.12, 2.0), dark)
    hole(front, (3.1, 1.0, 1.9), (-0.4, 0.1, 1.75))
    box((W, 0.2, 4.0), (0, 3.2, 2.0), dark)
    for x in (-W / 2 + 0.1, W / 2 - 0.1):
        box((0.2, 3.2, 4.0), (x, 1.6, 2.0), dark)
    interior(W - 0.4, 2.8, 3.8, 0.3, color="#0C0F1E", light=(0.6, 0.5, 1.0), energy=40)
    box((W + 0.2, 3.4, 0.2), (0, 1.6, 4.05), gold)
    for x in (-W / 2 + 0.2, W / 2 - 0.2):
        box((0.3, 0.3, 4.0), (x, -0.05, 2.0), mat("#202744", 0.4), bevel=0.04)
        box((0.36, 0.36, 0.15), (x, -0.05, 3.9), gold)
        box((0.36, 0.36, 0.15), (x, -0.05, 0.1), gold)
    # one big display tank glowing in a gold frame, with a rare leopard wrasse and a bubble tip anemone
    for zz in (0.75, 2.75):
        box((3.4, 0.14, 0.12), (-0.4, -0.02, zz), gold)
    for xx in (-2.05, 1.25):
        box((0.12, 0.14, 2.1), (xx, -0.02, 1.75), gold)
    tank((-0.4, 0.35, 1.75), (3.1, 0.6, 1.9), water="#3B2FD9", glow=1.3, fish_cols=("#3FBF7F",), coral=("#B18CFF", "#FF6FAE", "#7CFF6B"), n_fish=0)
    fish((-0.6, 0.1, 1.95), 0.95, "#3FBF7F", fin="#FF7AA8")
    for i in range(10):
        a = i * 2 * math.pi / 10
        cyl(0.025, 0.25, (0.6 + 0.12 * math.cos(a), 0.35 + 0.12 * math.sin(a), 1.05), mat("#FF6F7A", 0.4, emit="#FF6F7A", strength=0.8), verts=6)
        ball(0.04, (0.6 + 0.12 * math.cos(a), 0.35 + 0.12 * math.sin(a), 1.18), mat("#7CFF6B", 0.4, emit="#7CFF6B", strength=1.2), segs=8)
    # dark glass door with gold handle, gold lettering, a dark awning
    box((1.1, 0.1, 2.4), (2.1, -0.02, 1.2), mat("#0B0E1C", 0.15, metal=0.3))
    box((0.06, 0.1, 0.8), (1.7, -0.1, 1.25), gold)
    box((W - 0.6, 1.0, 0.06), (0, -0.45, 3.3), mat("#202744", 0.5), rot=(math.radians(-14), 0, 0))
    text("DEEP REEF IMPORTS", (0, -0.12, 3.68), 0.34, gold)
    for x in (-2.55, 2.75):
        cyl(0.2, 0.45, (x, -0.55, 0.28), gold, r2=0.16)
        for i in range(5):
            a = i * 2 * math.pi / 5
            box((0.05, 0.5, 0.02), (x + 0.15 * math.cos(a), -0.55 + 0.15 * math.sin(a), 0.75), mat("#2E8B57", 0.6), rot=(math.radians(50), 0, a))
    return W


BUILDERS = {"shellys": shellys, "frag_shack": frag_shack, "big_blue": big_blue, "deep_reef": deep_reef}
SKY = {"shellys": (0.55, 0.72, 0.9), "frag_shack": (0.95, 0.62, 0.45), "big_blue": (0.5, 0.7, 0.95), "deep_reef": (0.18, 0.16, 0.38)}


def build(sid, render=True, samples=40):
    C.reset_scene()
    _mats.clear()
    W = BUILDERS[sid]()
    objs = [o for o in bpy.context.scene.objects if o.type == 'MESH']
    for o in objs:
        if o.modifiers:
            C.apply_modifiers(o)
    C.export_glb(os.path.join(C.MODELS, f"store_{sid}.glb"), objs)
    if not render:
        return
    PV.setup_cycles(768, 512, samples)
    s = bpy.context.scene
    s.render.image_settings.file_format = 'WEBP'
    s.render.image_settings.quality = 82
    s.cycles.use_denoising = True
    s.view_settings.view_transform = 'Standard'
    PV.world(SKY[sid], 0.9 if sid != "deep_reef" else 0.5)
    sun = 3.5 if sid != "deep_reef" else 0.6
    PV.add_light('SUN', (0, 0, 10), sun, rot=(math.radians(50), math.radians(-25), math.radians(-30)), size=4,
                 color=(1.0, 0.92, 0.8) if sid != "frag_shack" else (1.0, 0.75, 0.55))
    PV.add_light('AREA', (-4, -7, 5), 900, size=6, color=(0.75, 0.85, 1.0))
    PV.camera((W * 0.42, -W * 1.3 - 3.0, 2.6), (0, 0.4, 1.9), lens=38)
    path = os.path.join(OUT, f"store_{sid}.webp")
    PV.render_to(path)
    print(f"[stores] {sid}: {os.path.getsize(path) / 1024:.0f} KB picture, "
          f"{os.path.getsize(os.path.join(C.MODELS, f'store_{sid}.glb')) / 1024:.0f} KB glb")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    for sid in args or STORES:
        build(sid, render="--no-render" not in sys.argv)
