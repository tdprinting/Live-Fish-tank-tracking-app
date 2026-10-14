"""Species cards (ASSETS.md §7): 512x512 transparent WebP renders of every livestock GLB, 3/4 view, soft
3-point light -> app/src/main/res/drawable-nodpi/species_<speciesId>.webp

<speciesId> is the species database id (core/src/main/resources/species/<id>.json), so the app can resolve
`R.drawable.species_<id>` straight from a livestock entry.
"""
import math
import os
import sys

import common as C
import preview as PV
import scene_preview as SP
from common import bpy

OUT = os.path.join(C.REPO, "app", "src", "main", "res", "drawable-nodpi")

# species id -> GLB id
CARDS = {
    "amphiprion_ocellaris": "clown_orange",
    "amphiprion_ocellaris_darwin": "clown_darwin",
    "paracanthurus_hepatus": "tang",
    "sphaeramia_nematoptera": "cardinal",
    "labroides_dimidiatus": "wrasse",
    "pholidichthys_leucotaenia": "engineer_goby",
    "cirrhitichthys_falco": "hawkfish",
    "salarias_fasciatus": "lawnmower_blenny",
    "ecsenius_stigmatura": "tailspot_blenny",
    "pseudochromis_indigo": "dottyback",
    "valenciennea_puellaris": "diamond_goby",
    "macropharyngodon_bipartitus": "leopard_wrasse",
    "euprymna_berryi": "bobtail_squid",
    "stenopus_hispidus": "shrimp",
    "lysmata_amboinensis": "skunk_shrimp",
    "clibanarius_tricolor": "hermit_blue",
    "paguristes_cadenati": "hermit_scarlet",
    "percnon_gibbesi": "sally_lightfoot",
    "ophiocoma_sp": "brittle_star",
    "sabellastarte_sp": "feather_duster",
    "nassarius_sp": "nassarius",
    "lithopoma_tectum": "astrea",
    "asterina_sp": "asterina",
    "capnella_sp": "kenya",
    "briareum_sp": "gsp_mat",
    "clavularia_sp": "clove",
    "discosoma_sp": "mushroom",
    "rhodactis_sp": "mushroom_hairy",
    "gracilaria_sp": "macro_red",
    "tubipora_musica": "organ_pipe",
    "zoanthus_sp": "zoa",
}
TOP = {"brittle_star", "asterina", "mushroom", "mushroom_hairy", "zoa"}
HIGH = {"hermit_blue", "hermit_scarlet", "sally_lightfoot", "astrea", "nassarius", "shrimp", "skunk_shrimp", "gsp_mat",
        "organ_pipe", "clove"}


def render_card(species, glb, size=512, samples=32):
    from mathutils import Vector
    C.reset_scene()
    PV.setup_cycles(size, size, samples)
    s = bpy.context.scene
    s.render.film_transparent = True
    s.render.image_settings.file_format = 'WEBP'
    s.render.image_settings.color_mode = 'RGBA'
    s.render.image_settings.quality = 85
    PV.world((0.35, 0.42, 0.5), 1.0)
    objs = PV.import_glb(os.path.join(C.MODELS, glb + ".glb"))
    SP.instance_polyps(objs)
    meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH' and not o.hide_render]
    pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    ctr = (lo + hi) / 2
    ext = hi - lo
    big = max(ext.x, ext.y, ext.z)
    # key / fill / rim, scaled to the subject
    PV.add_light('SUN', (0, 0, 10), 3.2, rot=(math.radians(-30), math.radians(15), 0), size=10)
    PV.add_light('AREA', tuple(ctr + Vector((-1.2, -1.5, 1.0)) * big), 60 * big * big, size=big * 1.5,
                 color=(0.85, 0.92, 1.0))
    PV.add_light('AREA', tuple(ctr + Vector((1.0, 1.4, 0.8)) * big), 40 * big * big, size=big, color=(0.7, 0.85, 1.0))
    yaw = math.radians(-28)
    elev = 1.45 if glb in TOP else (0.6 if glb in HIGH else 0.28)
    d = big * 4 + 1
    loc = ctr + Vector((d * math.sin(yaw) * math.cos(elev), -d * math.cos(yaw) * math.cos(elev), d * math.sin(elev)))
    PV.camera(tuple(loc), tuple(ctr), lens=50, ortho=big * 1.12)
    path = os.path.join(OUT, f"species_{species}.webp")
    s.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return path


def build(ids=None):
    os.makedirs(OUT, exist_ok=True)
    out = []
    for sp, glb in CARDS.items():
        if ids and sp not in ids and glb not in ids:
            continue
        if not os.path.exists(os.path.join(C.MODELS, glb + ".glb")):
            print(f"[cards] skip {sp}: no {glb}.glb")
            continue
        p = render_card(sp, glb)
        print(f"[cards] {os.path.basename(p)}: {os.path.getsize(p) / 1024:.0f} KB")
        out.append(p)
    return out


if __name__ == "__main__":
    build([a for a in sys.argv[1:]] or None)
