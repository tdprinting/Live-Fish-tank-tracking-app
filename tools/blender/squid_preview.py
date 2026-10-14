"""Bobtail squid previews (Cycles CPU): a model sheet with the app's motion-mode-4 poses and a sandbox scene close-up.

    /tmp/claude-0/bpyenv/bin/python tools/blender/squid_preview.py [--out DIR] [--samples N] [--no-scene]

The pose is a numpy mirror of squidPose() in app/src/main/assets/shaders/fish.vert (core SquidRig layout:
uLimb[0] = fin phase, fin stroke, mantle contraction, fin wrap; uLimb[1] = arm phase, spread, dorsal raise,
tentacle), applied in Blender's frame (x forward, y side, z up = glTF y).
"""
import argparse
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common as C  # noqa: E402
import preview as PV  # noqa: E402
from common import bpy, w2b  # noqa: E402

GLB = os.path.join(C.MODELS, "bobtail_squid.glb")
DEFAULT_OUT = "/mnt/project-files/reeftank/previews/squid"

POSES = {
    #          fin phase, stroke, mantle, wrap | arm phase, spread, raise, tentacle
    "rest": ((0.0, 0.3, 0.08, 0.0), (0.0, 0.3, 0.0, 0.0)),
    "hover": ((0.15, 0.8, 0.1, 0.0), (0.3, 0.65, 0.0, 0.0)),
    "strike": ((0.4, 0.5, 0.1, 0.0), (0.1, 0.55, 0.7, 1.0)),
    "jet": ((0.0, 0.1, 0.9, 1.0), (0.0, 0.0, 0.0, 0.0)),
}


def _smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def pose_squid(objs, pose):
    """Poses the imported squid meshes in place (unit model, before any scaling) and whitens COLOR_0."""
    Fn, Ar = (np.asarray(p, float) for p in POSES[pose]) if isinstance(pose, str) else pose
    for o in objs:
        if o.type != 'MESH':
            continue
        me = o.data
        ca = me.color_attributes[0]
        raw = np.zeros(len(ca.data) * 4, np.float32)
        ca.data.foreach_get("color", raw)
        raw = raw.reshape(-1, 4)
        if ca.domain == 'CORNER':
            vi = np.zeros(len(me.loops), np.int32)
            me.loops.foreach_get("vertex_index", vi)
            col = np.zeros((len(me.vertices), 4), np.float32)
            col[vi] = raw
        else:
            col = raw
        V = np.zeros(len(me.vertices) * 3)
        me.vertices.foreach_get("co", V)
        V = V.reshape(-1, 3)
        part = o.get("part", "body")
        u = col[:, 3]
        h = col[:, 2]
        lx = V[:, 0] - (0.5 - u)   # offset from the station, as fish.vert
        if part == "body":
            m = 1 - 0.14 * Fn[2] * _smooth(0.36, 0.5, u)
            V[:, 1] *= m
            V[:, 2] *= m
        elif part == "fin":
            side = np.where(V[:, 1] >= 0, 1.0, -1.0)   # Blender +y = glTF -z
            V[:, 2] += 0.07 * Fn[1] * h * np.sin(2 * math.pi * Fn[0] + lx * 18.0) * (1 - Fn[3])
            V[:, 1] -= side * h * 0.1 * Fn[3]
            V[:, 2] -= h * 0.03 * Fn[3]
        elif part == "leg":
            k = np.floor(col[:, 1] * 8 + 0.5)
            rl = np.sqrt(V[:, 1] ** 2 + V[:, 2] ** 2)
            rd = np.where(rl[:, None] > 1e-4, V[:, 1:3] / np.maximum(rl, 1e-4)[:, None], 0.0)
            arm = col[:, 0] < 0.19
            dors = (k <= 1.5) & arm
            A = np.where(arm, 1.0, 0.0)
            T = 1 - A
            V[:, 1:3] += A[:, None] * rd * (h * (Ar[1] - 0.3) * 0.14)[:, None]
            V[:, 2] += dors * Ar[2] * h * 0.14
            V[:, 0] -= dors * Ar[2] * h * 0.04
            V[:, 2] += A * 0.012 * h * h * np.sin(2 * math.pi * Ar[0] + k * 0.8 - h * 3)
            V[:, 1] -= A * 0.01 * h * h * np.cos(2 * math.pi * Ar[0] + k * 1.1 - h * 2.5)
            V[:, 0] += T * Ar[3] * 0.55 * h
            V[:, 1:3] *= (1 - T * 0.6 * Ar[3] * h)[:, None]
            V[:, 2] += T * 0.008 * h * np.sin(2 * math.pi * Ar[0] + k * 2) * (1 - Ar[3])
        me.vertices.foreach_set("co", V.ravel())
        me.update()
        for c in me.color_attributes:
            c.data.foreach_set("color", np.ones(len(c.data) * 4, np.float32))


def import_squid(pose="rest"):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=GLB)
    new = [o for o in bpy.data.objects if o not in before]
    pose_squid(new, pose)
    return new


def render_view(pose, view, w=720, h=440, samples=48):
    C.reset_scene()
    PV.setup_cycles(w, h, samples)
    PV.world((0.16, 0.2, 0.24), 1.0)
    objs = import_squid(pose)
    PV.add_light('SUN', (0, 0, 5), 3.5, rot=(math.radians(-25), math.radians(10), 0), size=8)
    PV.add_light('AREA', (0.3, -1.2, 0.6), 30, size=1.5, color=(0.9, 0.95, 1.0))
    PV.add_light('AREA', (-0.8, 0.8, 0.3), 12, size=1.0, color=(0.8, 0.9, 1.0))
    ctr = (0.08 if pose == "strike" else 0.0, 0.0, 0.0)
    if view == "side":
        loc, span = (0.0, -4.0, 0.15), 1.25
    elif view == "top":
        loc, span = (0.0, -0.05, 4.0), 1.25
    elif view == "front34":
        loc, span = (2.6, -2.6, 1.3), 1.15
    else:  # below: the ventral side (light organ, funnel, suckers)
        loc, span = (0.9, -1.6, -3.2), 1.2
    if pose == "strike":
        span *= 1.3
    PV.camera(loc, ctr, lens=50, ortho=span)
    out = os.path.join(C.BUILD, f"squid_{pose}_{view}.png")
    return PV.render_to(out)


def model_sheet(out_dir, samples=48):
    cells = [render_view("rest", "side", samples=samples), render_view("hover", "front34", samples=samples),
             render_view("rest", "top", samples=samples), render_view("strike", "side", samples=samples),
             render_view("jet", "front34", samples=samples), render_view("rest", "below", samples=samples)]
    path = os.path.join(out_dir, "model_sheet.png")
    PV.compose(cells, 2, path)
    for name, c in zip(("side", "hover_34", "top", "strike_side", "jet_34", "ventral"), cells):
        C.write_png(os.path.join(out_dir, f"model_{name}.png"), c[..., :3])
    return path


def sand_y(x, z):
    """World y of the sand surface at (x, z) by a ray cast down onto the tank's sand mesh."""
    from mathutils import Vector
    dg = bpy.context.evaluated_depsgraph_get()
    hit, loc, *_ = bpy.context.scene.ray_cast(dg, Vector(w2b((x, 60.0, z))), Vector((0, 0, -1)))
    # skip anything above the sand (water surface, equipment): walk down until a sand/rock hit
    while hit and loc.z > 12.0:
        hit, loc, *_ = bpy.context.scene.ray_cast(dg, loc + Vector((0, 0, -0.01)), Vector((0, 0, -1)))
    return float(loc.z) if hit else 4.0


def scene(out_dir, samples=40, w=1120, h=700):
    import scene_preview as SP
    SP.build_scene(samples, w, h, "front")
    L = 6.0
    # hovering at night height over the open sand in front of the rock, arms flared, fins beating
    hover = import_squid("hover")
    SP.place(hover, L, (55.0, 12.5, 26.0), 200, 4)
    # a second animal buried by day: mantle under the sand, eyes and the top of the head out (posture 5); the front
    # sand sits near y 5 (anchors SAND_FRONT); clear of the corals on the open strip by the glass
    bx, bz = 43.0, 28.5
    y = sand_y(bx, bz)
    y = y if 3.5 < y < 8.0 else 5.0   # front sand mounds run ~5-7 cm
    buried = import_squid("rest")
    SP.place(buried, L, (bx, y - 0.3, bz), 150, 0)
    # one mid-strike over the sand, tentacles out
    strike = import_squid("strike")
    SP.place(strike, L, (63.0, 9.0, 29.0), 195, -8)
    cam = PV.camera(w2b((55.0, 30.0, 112.0)), w2b((55.0, 8.0, 27.0)))
    cam.data.sensor_fit = 'VERTICAL'
    cam.data.angle_y = math.radians(16)
    cam.data.clip_end = 2000
    path = os.path.join(out_dir, "scene_sandbox.png")
    PV.render_to(path)
    print("[squid] buried squid at sand y", y)
    return path


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--samples", type=int, default=48)
    ap.add_argument("--no-scene", action="store_true")
    ap.add_argument("--no-model", action="store_true")
    a = ap.parse_args(argv)
    os.makedirs(a.out, exist_ok=True)
    if not a.no_model:
        print(model_sheet(a.out, a.samples))
    if not a.no_scene:
        print(scene(a.out, a.samples))


if __name__ == "__main__":
    main(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:])
