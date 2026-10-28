"""Shared helpers for the ReefLive asset scripts (headless bpy).

Coordinate conventions (see docs/ASSETS.md):
  world (glTF, Y-up, cm)  <->  Blender (Z-up):  blender = (x, -z, y)
The glTF exporter (export_yup=True) converts Blender (x, y, z) -> glTF (x, z, -y).
"""
import os
import struct
import zlib

import numpy as np

try:
    import bpy
    import bmesh  # noqa: F401
except ImportError:  # paintlib-only use outside Blender
    bpy = None

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(REPO, "app", "src", "main", "assets")
MODELS = os.path.join(ASSETS, "models")
TEXTURES = os.path.join(ASSETS, "textures")
SIM = os.path.join(ASSETS, "sim")
PREVIEW = os.path.join(REPO, "docs", "preview")
BUILD = os.path.join(REPO, "build", "blender")  # scratch (git-ignored)

# Tank interior (world cm): the one source of truth for the asset scripts. Must match core TankDims.kt
# (owner's measurement 2026-10-13: 48 in long x 16 in tall x 18 in front-to-back, 60 US gal, open top).
TANK = (121.9, 40.6, 45.7)   # (x length, y height, z depth); back glass z = 0, front glass z = TANK[2]
WATER_Y = 38.1               # water line, ~2.5 cm under the top of the glass
SAND_Y = 3.5                 # rendered sand top at the front glass (sim plane: 3.0)
SAND_Y_BACK = 6.0            # sand top at the back glass
RIM_Y = TANK[1] + 0.4        # top of the black rim lip

for _d in (MODELS, TEXTURES, SIM, PREVIEW, BUILD):
    os.makedirs(_d, exist_ok=True)


# ----------------------------------------------------------------------------
# Colors
# ----------------------------------------------------------------------------
def hex2rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], dtype=np.float32)


def srgb_to_linear(c):
    c = np.asarray(c, dtype=np.float64)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def lin(h):
    """hex -> linear RGB tuple (for Blender material factors)."""
    return tuple(float(x) for x in srgb_to_linear(hex2rgb(h)))


def w2b(p):
    """world (x, y_up, z_front) -> blender (x, y, z_up)."""
    return (p[0], -p[2], p[1])


def b2w(p):
    return (p[0], p[2], -p[1])


# ----------------------------------------------------------------------------
# PNG writer (no Pillow in the bpy venv): adaptive per-row filters + zlib 9
# ----------------------------------------------------------------------------
def _paeth(a, b, c):
    p = a.astype(np.int16) + b - c
    pa = np.abs(p - a)
    pb = np.abs(p - b)
    pc = np.abs(p - c)
    return np.where((pa <= pb) & (pa <= pc), a, np.where(pb <= pc, b, c)).astype(np.uint8)


def write_png(path, arr):
    """arr: HxW, HxWx1..4 uint8 or float [0,1]. Row 0 = top of the image."""
    a = np.asarray(arr)
    if a.dtype != np.uint8:
        a = (np.clip(a, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)
    if a.ndim == 2:
        a = a[:, :, None]
    h, w, c = a.shape
    ctype = {1: 0, 2: 4, 3: 2, 4: 6}[c]
    flat = a.reshape(h, w * c)
    bpp = c
    prev = np.vstack([np.zeros((1, w * c), np.uint8), flat[:-1]])
    left = np.hstack([np.zeros((h, bpp), np.uint8), flat[:, :-bpp]])
    upleft = np.hstack([np.zeros((h, bpp), np.uint8), prev[:, :-bpp]])
    cands = [
        flat,
        (flat - left).astype(np.uint8),
        (flat - prev).astype(np.uint8),
        (flat - ((left.astype(np.uint16) + prev) // 2).astype(np.uint8)).astype(np.uint8),
        (flat - _paeth(left, prev, upleft)).astype(np.uint8),
    ]
    scores = np.stack([np.abs(cd.astype(np.int8).astype(np.int32)).sum(axis=1) for cd in cands])
    best = scores.argmin(axis=0)
    rows = np.empty((h, w * c + 1), np.uint8)
    rows[:, 0] = best
    for f in range(5):
        sel = best == f
        rows[sel, 1:] = cands[f][sel]
    raw = zlib.compress(rows.tobytes(), 9)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, ctype, 0, 0, 0))
    data += chunk(b"IDAT", raw) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    return path


def to_jpeg(src_png, dst, quality=90):
    """Re-encodes an RGB PNG as JPEG (4:4:4 chroma, so thin stripes keep their colour): the web app downloads every
    texture it shows (docs/ASSETS.md §6, download size). Needs Pillow."""
    from PIL import Image
    Image.open(src_png).convert("RGB").save(dst, "JPEG", quality=quality, subsampling=0, optimize=True)
    return dst


def read_png(path):
    """Minimal PNG reader for 8-bit non-interlaced images written by write_png/Blender."""
    with open(path, "rb") as f:
        data = f.read()
    pos = 8
    idat = b""
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        d = data[pos + 8:pos + 8 + ln]
        if t == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", d[:10])
        elif t == b"IDAT":
            idat += d
        pos += 12 + ln
    c = {0: 1, 4: 2, 2: 3, 6: 4}[ct]
    assert bd == 8
    raw = np.frombuffer(zlib.decompress(idat), np.uint8).reshape(h, w * c + 1)
    out = np.zeros((h, w * c), np.int32)
    for y in range(h):
        f = raw[y, 0]
        line = raw[y, 1:].astype(np.int32)
        up = out[y - 1] if y > 0 else np.zeros(w * c, np.int32)
        if f == 0:
            out[y] = line
        elif f == 2:
            out[y] = (line + up) & 255
        else:
            cur = np.zeros(w * c, np.int32)
            for x in range(w * c):
                a = cur[x - c] if x >= c else 0
                b = up[x]
                cc = up[x - c] if x >= c else 0
                if f == 1:
                    v = a
                elif f == 3:
                    v = (a + b) // 2
                else:
                    p = a + b - cc
                    pa, pb, pc = abs(p - a), abs(p - b), abs(p - cc)
                    v = a if (pa <= pb and pa <= pc) else (b if pb <= pc else cc)
                cur[x] = (line[x] + v) & 255
            out[y] = cur
    return out.astype(np.uint8).reshape(h, w, c)


# ----------------------------------------------------------------------------
# Blender helpers
# ----------------------------------------------------------------------------
def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    for blk in (bpy.data.meshes, bpy.data.materials, bpy.data.images, bpy.data.objects):
        for x in list(blk):
            blk.remove(x)


def link(obj, parent=None):
    bpy.context.scene.collection.objects.link(obj)
    if parent is not None:
        obj.parent = parent
    return obj


def empty(name, parent=None, loc=(0, 0, 0)):
    o = bpy.data.objects.new(name, None)
    o.location = loc
    return link(o, parent)


def mesh_object(name, verts, faces, uvs=None, cols=None, parent=None, smooth=True, part=None,
                materials=None, face_mats=None, extras=None):
    """Create a mesh object.
    verts: (N,3) blender coords; faces: list of index tuples;
    uvs: (N,2) per-vertex UV (blender convention, v up) or None;
    cols: (N,4) per-vertex RGBA data colors (attribute 'Col', linear float).
    """
    me = bpy.data.meshes.new(name)
    verts = np.asarray(verts, dtype=np.float64)
    me.from_pydata([tuple(v) for v in verts], [], [tuple(int(i) for i in f) for f in faces])
    me.validate(clean_customdata=False)
    if uvs is not None:
        uvs = np.asarray(uvs, dtype=np.float32)
        uvl = me.uv_layers.new(name="UVMap")
        vi = np.zeros(len(me.loops), np.int32)
        me.loops.foreach_get("vertex_index", vi)
        uvl.data.foreach_set("uv", uvs[vi].ravel())
    if cols is None:
        cols = np.zeros((len(verts), 4), np.float32)
    cols = np.asarray(cols, dtype=np.float32)
    ca = me.color_attributes.new("Col", 'FLOAT_COLOR', 'POINT')
    ca.data.foreach_set("color", cols.ravel())
    me.color_attributes.active_color = ca
    if materials:
        for m in materials:
            me.materials.append(m)
    if face_mats is not None:
        me.polygons.foreach_set("material_index", np.asarray(face_mats, np.int32))
    me.polygons.foreach_set("use_smooth", np.full(len(me.polygons), bool(smooth)))
    me.update()
    o = bpy.data.objects.new(name, me)
    link(o, parent)
    if part:
        set_extras(o, part=part)
    if extras:
        set_extras(o, **extras)
    return o


def set_extras(obj, **kw):
    for k, v in kw.items():
        obj[k] = v
        if obj.type == 'MESH':
            obj.data[k] = v


def get_cols(obj):
    me = obj.data
    ca = me.color_attributes["Col"]
    a = np.zeros(len(ca.data) * 4, np.float32)
    ca.data.foreach_get("color", a)
    return a.reshape(-1, 4)


def set_cols(obj, cols):
    ca = obj.data.color_attributes["Col"]
    ca.data.foreach_set("color", np.asarray(cols, np.float32).ravel())


def get_verts(obj):
    a = np.zeros(len(obj.data.vertices) * 3, np.float64)
    obj.data.vertices.foreach_get("co", a)
    return a.reshape(-1, 3)


def load_image(path, name=None, colorspace='sRGB'):
    img = bpy.data.images.load(path, check_existing=False)
    img.name = name or os.path.basename(path)
    img.colorspace_settings.name = colorspace
    img.pack()
    return img


def material(name, color="#808080", rough=0.5, metal=0.0, image=None, alpha=1.0, blend=False,
             double=False, emissive=None, emissive_strength=1.0, image_alpha=False, clip=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    if bsdf is None:
        bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
        out = nt.nodes.get("Material Output") or nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(bsdf.outputs[0], out.inputs[0])
    c = lin(color) if isinstance(color, str) else tuple(color)
    bsdf.inputs["Base Color"].default_value = (*c, 1.0)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    bsdf.inputs["Alpha"].default_value = alpha
    if image is not None:
        tex = nt.nodes.new("ShaderNodeTexImage")
        tex.image = image
        nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
        if image_alpha:
            nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    if emissive is not None:
        e = lin(emissive) if isinstance(emissive, str) else tuple(emissive)
        bsdf.inputs["Emission Color"].default_value = (*e, 1.0)
        bsdf.inputs["Emission Strength"].default_value = emissive_strength
    if blend or alpha < 1.0 or image_alpha:
        m.surface_render_method = 'BLENDED'
        try:
            m.blend_method = 'BLEND'
        except Exception:
            pass
    if clip:
        try:
            m.blend_method = 'CLIP'
        except Exception:
            pass
    m.use_backface_culling = not double
    return m


def tri_count(obj):
    me = obj.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh() if obj.modifiers else obj.data
    n = sum(len(p.vertices) - 2 for p in me.polygons)
    return n


def apply_modifiers(obj):
    bpy.context.view_layer.objects.active = obj
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    obj.select_set(True)
    for md in list(obj.modifiers):
        bpy.ops.object.modifier_apply(modifier=md.name)


def triangulate(obj):
    md = obj.modifiers.new("tri", 'TRIANGULATE')
    md.quad_method = 'BEAUTY'
    apply_modifiers(obj)


def decimate(obj, ratio):
    md = obj.modifiers.new("dec", 'DECIMATE')
    md.ratio = ratio
    md.use_collapse_triangulate = True
    apply_modifiers(obj)


def export_glb(path, objects=None):
    """Shared exporter (ASSETS.md header). Exports `objects` (and only them) or the whole scene."""
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    if objects is not None:
        for o in objects:
            o.select_set(True)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=path, export_format='GLB', use_selection=objects is not None,
        export_apply=True, export_yup=True, export_texcoords=True, export_normals=True,
        export_vertex_color='NAME', export_vertex_color_name='Col', export_all_vertex_colors=False,
        export_active_vertex_color_when_no_material=False,
        export_materials='EXPORT', export_image_format='AUTO', export_animations=False,
        export_skins=False, export_morph=False, export_extras=True, export_tangents=False,
        export_cameras=False, export_lights=False)
    optimize(path)
    return path


def optimize(path):
    """tools/optimize_glb.py on a fresh export (JPEG / WebP textures, quantized normals and UVs): what the web app
    downloads. Skipped with a warning where Pillow is missing (run tools/optimize_glb.py afterwards)."""
    import sys as _sys
    _sys.path.insert(0, os.path.join(REPO, "tools"))
    try:
        import optimize_glb
    except ImportError as e:  # noqa: F841
        print(f"[export] WARNING: {os.path.basename(path)} not optimized ({e}); run tools/optimize_glb.py")
        return path
    optimize_glb.optimize(path)
    return path


def all_descendants(root):
    out = [root]
    for c in root.children:
        out += all_descendants(c)
    return out


def scene_tris(objs):
    return sum(tri_count(o) for o in objs if o.type == 'MESH')


def recalc_normals(obj, inside=False):
    import bmesh as _bm
    bm = _bm.new()
    bm.from_mesh(obj.data)
    _bm.ops.recalc_face_normals(bm, faces=bm.faces)
    if inside:
        _bm.ops.reverse_faces(bm, faces=bm.faces)
    bm.to_mesh(obj.data)
    bm.free()
    obj.data.update()
