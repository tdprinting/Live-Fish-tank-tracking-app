#!/usr/bin/env python3
"""ReefLive GLB validator (stdlib only: json + struct).

Checks every app/src/main/assets/models/*.glb against the asset contract (docs/ASSETS.md §0):
  * GLB magic/version/chunks, JSON parses, only supported features (no skins/animations/
    required extensions, no Draco), triangle-list primitives
  * every mesh primitive has POSITION, NORMAL, TEXCOORD_0, COLOR_0 (VEC4)
  * every mesh node has extras.part from the allowed set
  * per-asset triangle budgets (+15% tolerance like build_all)
  * fish: COLOR_0.a runs 0 at the snout (max x) -> 1 at the tail (min x), COLOR_0.b in [0,1] on fins,
    unit length (x extent ~1.0)
  * instance tables (extras.instances) are multiples of 10 floats
  * on-screen triangle estimates: High <= 220k (LOD0 + instances), Medium <= 140k (*_lod1.glb + halved instances)
Also checks assets/sim/anchors.json names and rock_sdf.bin header/size.
Usage: python3 tools/validate_glb.py [files...]   (exit code 1 on failure)
"""
import glob
import json
import math
import os
import re
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODELS = os.path.join(ROOT, "app", "src", "main", "assets", "models")
SIM = os.path.join(ROOT, "app", "src", "main", "assets", "sim")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")

PARTS = {"body", "fin", "eye", "jaw", "polyp", "antenna", "shell", "leg", "glass", "backglass", "water",
         "sand", "rim", "rock", "equipment", "light", "emitter", "stand", "tube", "card",
         # models drawn by their own scene class with its own program, outside the tank's material switch
         # (GlbLoader keeps the tag, Material.forPart maps it to GENERIC; the scene picks the program):
         "store",  # store_rooms.py / store_people.py / store_fronts.py -> StoreRoomScene (store.frag)
         "room",   # rooms.py, stands.py, music_corner.py -> HomeRoomScene / MusicCornerScene (room.frag)
         "care"}   # care_tools.py, pests.py (pest_tools) -> CareScene (generic program, posed per act)

# required extensions GlbLoader handles: normalized integer NORMAL / TEXCOORD_0 / COLOR_0 (tools/optimize_glb.py)
SUPPORTED_REQUIRED = {"KHR_mesh_quantization"}
# embedded image types both decoders read (Android BitmapFactory; web android/graphics/Graphics.kt sniffs the bytes)
IMAGE_TYPES = {"image/png", "image/jpeg", "image/webp"}

COMP = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2), 5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}

# triangle budgets for LOD0 files: (total max, body max) ; None = not checked
FISH = ["clown_orange", "clown_darwin", "tang", "cardinal", "wrasse", "engineer_goby", "hawkfish",
        "lawnmower_blenny", "tailspot_blenny", "dottyback", "diamond_goby", "leopard_wrasse",
        "bicolor_blenny", "midas_blenny", "firefish", "chromis", "yellow_tang", "purple_tang", "fairy_wrasse", "royal_gramma", "banggai", "watchman_goby",
        "mandarin", "sixline_wrasse", "clown_goby", "gem_tang", "wrought_iron_butterfly",
        "bobtail_squid"]  # the squid is unit length facing +X too (arm tips = "snout"; tools/blender/squid.py)
BUDGET = {
    "clown_orange": 5000, "clown_darwin": 5000, "tang": 5800, "cardinal": 5000, "wrasse": 4000,
    "engineer_goby": 6200, "hawkfish": 5200, "lawnmower_blenny": 4600, "tailspot_blenny": 4400, "dottyback": 4400,
    "diamond_goby": 4800, "leopard_wrasse": 4800, "bicolor_blenny": 4400, "midas_blenny": 4400, "royal_gramma": 4800, "banggai": 4800, "watchman_goby": 4800, "gem_tang": 6000, "wrought_iron_butterfly": 6000, "firefish": 4800, "chromis": 4800, "mandarin": 4800, "sixline_wrasse": 4800, "clown_goby": 4800, "bobtail_squid": 4600,
    "yellow_tang": 6000, "purple_tang": 6000, "fairy_wrasse": 4800,
    "shrimp": 4200, "skunk_shrimp": 4200, "peppermint_shrimp": 4200, "fire_shrimp": 4200, "sexy_shrimp": 4200, "harlequin_shrimp": 4200, "pistol_shrimp": 4200, "mantis_shrimp": 5200, "flame_scallop": 3200, "maxima_clam": 3600, "crocea_clam": 3600, "hermit_blue": 2600, "hermit_scarlet": 2600, "sally_lightfoot": 5400, "arrow_crab": 6000, "emerald_crab": 5200, "tiger_tail_cucumber": 5200, "porcelain_crab": 5200, "brittle_star": 2000, "serpent_star": 2000,
    "nassarius": 1300, "astrea": 1100, "asterina": 400, "feather_duster": 1300,
    "trochus": 2800, "fighting_conch": 3800, "tuxedo_urchin": 5000,
    # in-tank microfauna (tools/blender/microfauna.py); the renderer culls them below a few pixels on screen
    "micro_bristleworm": 3600, "micro_amphipod": 4000, "micro_mysid": 2600, "micro_isopod": 2000, "micro_copepod": 900,
    "micro_snail": 1400, "micro_flatworm": 200, "micro_tentacle": 800, "micro_caprellid": 2000, "micro_ostracod": 700,
    "micro_foram": 600, "micro_calanoid": 600,
    # clownfish breeding (tools/blender/brood.py): one clutch, pooled larvae/fry (core BroodField MAX_*)
    "brood_clown_eggs_fresh": 10000, "brood_clown_eggs_dark": 10000, "brood_clown_eggs_eyed": 10000,
    "brood_clown_larva": 1000, "brood_clown_postlarva": 1000, "brood_clown_fry": 1000,
    "brood_tang_egg": 600, "brood_tang_larva": 1000, "brood_tang_postlarva": 1500, "brood_tang_fry": 1800,
    "tank": 11000, "rockscape": 62000, "equipment": 9000,
    # pests (tools/blender/pests.py): one aiptasia polyp drawn once per polyp (AiptasiaDraw caps the count), the
    # Berghia nudibranch (drawn like the micro critters), the syringe / laser props of the treatment animation
    "aiptasia": 1500, "berghia": 2400, "pest_tools": 900,
    "kenya": 14000, "gsp_neon": 1500, "gsp_mat": 2500, "zoa": 1500, "clove": 3000, "mushroom": 1100,
    "mushroom_hairy": 1500, "mushroom_hairy_mound": 3500,
    # Tee's photos 2026-10-13 (docs/research/video/tee_tank_photos.md): leathers and the big corallimorph; raised in
    # the 2026-10-15 coral remake (lobed / ridged leathers, taco-folded ear), paid for by fewer GSP ribbon segments
    "toadstool_leather": 1000, "leather_pink": 1500, "finger_leather": 1500, "elephant_ear": 1100,
    "macro_red": 6500, "organ_pipe": 2500, "algae_tufts": 1100,
    # stony corals (tools/blender/stony_corals.py; sandbox / My tank only): skeleton + mantle bodies, tentacles instanced
    "hammer": 1500, "torch": 1500, "frogspawn": 1600, "duncan": 2000,
    "acan": 1000, "candy_cane": 2000, "open_brain": 1400, "bubble": 1000,
    "gold_torch": 1500, "scolymia": 1800, "bounce_mushroom": 1200,
    "montipora_cap": 2000, "birdsnest": 3200, "acropora": 1400, "xenia": 700,
    "reef_details": 2000,
    # the supply shelf beside the tank (tools/blender/supplies.py; 28 labelled props + rack + sign; off the default view)
    "supply_shelf": 18000,
    # props for the "Fix tank" upkeep shows (care_tools.py), drawn only while a care act runs (CareScene)
    "care_tools": 5000,
    # the record stand left of the tank at home (music_corner.py; MusicCornerScene)
    "music_corner": 4000,
}
# the room at home round the tank (rooms.py, stands.py; HomeRoomScene): one room_* and ONE node of one stand_* (the
# build nearest the tank's size; a stand file holds one node per build) are drawn at a time
PREFIX_BUDGET = {"room_": 6000, "stand_": 2400}
STAND_NODE_BUDGET = 700
# fish-store visits (store_rooms.py): their own scene. A store room replaces the room at home round the live display
# tank, so a visit draws one store room + the tank scene (STORE_MAX below), never the home room as well. Set in the
# 2026-10 perf pass, when the four rooms went from 94-120k to 72-88k triangles (people and signs at lower
# resolution); Big Blue, the biggest store, sits inside the usual +15 %.
STORE_ROOM_BUDGET = 80000
# LOD1 caps: fish default 1700; the engineer goby (anguilliform, 96 stations) 2100; scene assets with a Medium LOD
LOD1_BUDGET = {"engineer_goby": 2100, "rockscape": 31000, "kenya": 6500,
               # crawlers of Tee's tank (tools/blender/lod1.py), drawn like the fish LOD1 beyond lod1DistanceCm
               "hermit_blue": 1300, "hermit_scarlet": 1300, "shrimp": 1900, "skunk_shrimp": 1600,
               "sally_lightfoot": 2300, "brittle_star": 1100, "nassarius": 500, "astrea": 500}
# scene assets whose _lod1.glb the renderer draws on Medium and Low (TankRenderer.sceneModel)
MEDIUM_LOD1 = {"rockscape", "kenya"}
# on-screen triangle ceilings (docs/ASSETS.md §8) for the default view of Tee's tank (DEFAULT_* below): High = LOD0
# scene + all polyp instances + fish at LOD1 (default camera > 60 cm) + the room at home; Medium = MEDIUM_LOD1 swapped
# in, the other polyp instance tables halved (RenderQuality.polypDensity), fish at LOD1
HIGH_MAX = 222000
MEDIUM_MAX = 142000
# a fish-store visit at High: the tank scene (without the home room, ~206k) + the largest store room. The visit
# frames the aisle, so the display tank fills less of the screen than at home; 300k keeps it within ~1.35x HIGH_MAX.
STORE_MAX = 300000
# Tee's tank, model -> count on screen: core sim/Presets.kt DefaultStock (fish, inverts) and TankRenderer
# SCENE_MODELS / DEMO_CORALS + the leathers and hairy mushrooms of DefaultStock.corals. Sandbox / My tank / store
# species are not in the default view; their own per-asset budgets cover them. check_default_stock() fails when
# Presets.kt gains or loses a seed, so these lists cannot silently go stale.
DEFAULT_FISH = {"clown_orange": 2, "tang": 1, "cardinal": 1, "wrasse": 1, "engineer_goby": 1, "hawkfish": 1,
                "lawnmower_blenny": 1, "tailspot_blenny": 1, "dottyback": 1, "diamond_goby": 1, "leopard_wrasse": 1}
DEFAULT_INVERTS = {"shrimp": 1, "skunk_shrimp": 1, "hermit_blue": 3, "brittle_star": 1, "feather_duster": 1,
                   "nassarius": 3, "astrea": 3, "asterina": 2, "sally_lightfoot": 1}
DEFAULT_CORALS = {"kenya": 1, "gsp_neon": 1, "zoa": 1, "clove": 1, "mushroom": 1, "mushroom_hairy": 1,
                  "macro_red": 1, "organ_pipe": 1, "toadstool_leather": 1, "leather_pink": 1, "finger_leather": 1,
                  "elephant_ear": 1, "mushroom_hairy_mound": 1}
DEFAULT_SCENE = {"tank": 1, "rockscape": 1, "equipment": 1, "algae_tufts": 1, "reef_details": 1}
PRESETS = os.path.join(ROOT, "core", "src", "main", "kotlin", "com", "reeflive", "core", "sim", "Presets.kt")
# in-tank microfauna (MicroCritters): drawn nearest-first until this per-frame triangle budget is spent, so they add
# at most this much on screen whatever the population (app render/MicroCritterVisuals.kt MICRO_TRIS_*)
MICRO_TRIS_HIGH = 12000
MICRO_TRIS_MEDIUM = 2000
BODY_BUDGET = {"engineer_goby": 5600}
# body COLOR_0.a at its front end: the squid body starts at the arm crown (its arms, part "leg", are the snout)
SNOUT_A = {"bobtail_squid": 0.3}
# docs/specs/tank_design.md §0: every tank / equipment / rock mesh is closed (WaterSurface is the two-sided quad)
CLOSED = {"tank", "equipment", "rockscape"}
OPEN_OK = {"WaterSurface"}


def open_edges(g, mi, m):
    """Edges not shared by exactly two triangles, after welding positions (glTF splits vertices at seams)."""
    edges = {}
    for p in g.json["meshes"][mi]["primitives"]:
        pos = g.accessor(p["attributes"]["POSITION"])
        key = [tuple(round(c, 3) for c in xf(m, P_)) for P_ in pos]
        idx = [i[0] for i in g.accessor(p["indices"])] if "indices" in p else list(range(len(pos)))
        for t in range(0, len(idx), 3):
            a, b, c = (key[i] for i in idx[t:t + 3])
            for e in ((a, b), (b, c), (c, a)):
                if e[0] == e[1]:
                    continue
                k = (min(e), max(e))
                edges[k] = edges.get(k, 0) + 1
    return sum(1 for v in edges.values() if v != 2)


class Glb:
    def __init__(self, path):
        self.path = path
        with open(path, "rb") as f:
            data = f.read()
        magic, ver, length = struct.unpack("<III", data[:12])
        if magic != 0x46546C67:
            raise ValueError("bad GLB magic")
        if ver != 2:
            raise ValueError("GLB version %d" % ver)
        if length != len(data):
            raise ValueError("length mismatch")
        clen, ctype = struct.unpack("<II", data[12:20])
        if ctype != 0x4E4F534A:
            raise ValueError("first chunk not JSON")
        self.json = json.loads(data[20:20 + clen].decode("utf-8"))
        off = 20 + clen
        self.bin = b""
        if off < len(data):
            blen, btype = struct.unpack("<II", data[off:off + 8])
            if btype != 0x004E4942:
                raise ValueError("second chunk not BIN")
            self.bin = data[off + 8:off + 8 + blen]

    def accessor(self, idx):
        a = self.json["accessors"][idx]
        bv = self.json["bufferViews"][a["bufferView"]]
        fmt, size = COMP[a["componentType"]]
        n = NCOMP[a["type"]]
        stride = bv.get("byteStride", size * n)
        base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        out = []
        for i in range(a["count"]):
            vals = struct.unpack_from("<" + fmt * n, self.bin, base + i * stride)
            if a.get("normalized"):
                mx = {"B": 255.0, "H": 65535.0, "b": 127.0, "h": 32767.0}[fmt]
                vals = tuple(v / mx for v in vals)
            out.append(vals)
        return out

    def mesh_nodes(self):
        for i, n in enumerate(self.json.get("nodes", [])):
            if "mesh" in n:
                yield i, n

    def tri_count(self, mesh_idx):
        t = 0
        for p in self.json["meshes"][mesh_idx]["primitives"]:
            if "indices" in p:
                t += self.json["accessors"][p["indices"]]["count"] // 3
            else:
                t += self.json["accessors"][p["attributes"]["POSITION"]]["count"] // 3
        return t


def world_matrices(g):
    """node index -> 4x4 world matrix (row-major lists); handles TRS only."""
    nodes = g.json.get("nodes", [])
    parent = {}
    for i, n in enumerate(nodes):
        for c in n.get("children", []):
            parent[c] = i

    def local(n):
        if "matrix" in n:
            m = n["matrix"]
            return [[m[c * 4 + r] for c in range(4)] for r in range(4)]
        t = n.get("translation", [0, 0, 0])
        q = n.get("rotation", [0, 0, 0, 1])
        s = n.get("scale", [1, 1, 1])
        x, y, z, w = q
        R = [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
             [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
             [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]
        return [[R[r][0] * s[0], R[r][1] * s[1], R[r][2] * s[2], t[r]] for r in range(3)] + [[0, 0, 0, 1]]

    def mul(a, b):
        return [[sum(a[r][k] * b[k][c] for k in range(4)) for c in range(4)] for r in range(4)]

    cache = {}

    def wm(i):
        if i in cache:
            return cache[i]
        m = local(nodes[i])
        if i in parent:
            m = mul(wm(parent[i]), m)
        cache[i] = m
        return m
    return {i: wm(i) for i in range(len(nodes))}


def xf(m, p):
    return tuple(m[r][0] * p[0] + m[r][1] * p[1] + m[r][2] * p[2] + m[r][3] for r in range(3))


def check_glb(path, errors, warnings, report):
    name = os.path.splitext(os.path.basename(path))[0]
    base = name[:-5] if name.endswith("_lod1") else name
    try:
        g = Glb(path)
    except Exception as e:  # noqa: BLE001
        errors.append(f"{name}: {e}")
        return
    j = g.json
    for k in ("skins", "animations"):
        if j.get(k):
            errors.append(f"{name}: contains {k}")
    unsupported = [e for e in j.get("extensionsRequired", []) if e not in SUPPORTED_REQUIRED]
    if unsupported:
        errors.append(f"{name}: extensionsRequired {unsupported}")
    for im in j.get("images", []):
        if im.get("mimeType") not in IMAGE_TYPES:
            errors.append(f"{name}: image mimeType {im.get('mimeType')!r} (GlbLoader / the web decode {sorted(IMAGE_TYPES)})")
    # GlbLoader reads rootExtras from the single scene root; generators name it `Root` (the rockscape's root is its
    # `Rock` mesh). A Blender duplicate suffix (`Root.001`) means a LOD1 export picked up the wrong name.
    scenes = j.get("scenes") or [{}]
    roots = scenes[j.get("scene", 0)].get("nodes", [])
    if len(roots) != 1:
        errors.append(f"{name}: {len(roots)} scene roots (expected 1)")
    else:
        rname = j["nodes"][roots[0]].get("name")
        if rname != ("Rock" if base == "rockscape" else "Root"):
            errors.append(f"{name}: scene root is named {rname!r}")
    if name.endswith("_lod1") and base not in FISH and j["nodes"][roots[0]].get("extras", {}).get("lod") != 1:
        errors.append(f"{name}: root extras.lod != 1")
    total = 0
    body_tris = 0
    wms = world_matrices(g)
    xs_a = []
    fin_b_bad = 0
    for ni, n in g.mesh_nodes():
        ex = n.get("extras", {})
        part = ex.get("part")
        if part not in PARTS:
            errors.append(f"{name}: node '{n.get('name')}' has extras.part={part!r}")
        mi = n["mesh"]
        tris = g.tri_count(mi)
        total += tris
        if ex.get("hidden"):
            report.setdefault("hidden", {})[name] = report.get("hidden", {}).get(name, 0) + tris
        if base in CLOSED and n.get("name") not in OPEN_OK:
            ne = open_edges(g, mi, wms[ni])
            if ne:
                errors.append(f"{name}: node '{n.get('name')}' has {ne} non-manifold edges")
        if part == "body":
            body_tris += tris
        for p in j["meshes"][mi]["primitives"]:
            if p.get("mode", 4) != 4:
                errors.append(f"{name}: primitive mode {p.get('mode')} (triangles only)")
            at = p["attributes"]
            for req in ("POSITION", "NORMAL", "TEXCOORD_0", "COLOR_0"):
                if req not in at:
                    errors.append(f"{name}: node '{n.get('name')}' missing {req}")
            for k, ai in at.items():  # glTF: vertex attribute elements 4-byte aligned (int8 normals padded)
                a = j["accessors"][ai]
                fmt, size = COMP[a["componentType"]]
                bv = j["bufferViews"][a["bufferView"]]
                if bv.get("byteStride", size * NCOMP[a["type"]]) % 4:
                    errors.append(f"{name}: node '{n.get('name')}' {k} stride not a multiple of 4")
            if "COLOR_0" in at and j["accessors"][at["COLOR_0"]]["type"] != "VEC4":
                errors.append(f"{name}: COLOR_0 is not VEC4")
            if base in FISH and "COLOR_0" in at and part in ("body", "fin"):
                pos = g.accessor(at["POSITION"])
                col = g.accessor(at["COLOR_0"])
                m = wms[ni]
                if part == "body":
                    for P_, c in zip(pos, col):
                        xs_a.append((xf(m, P_)[0], c[3]))
                else:
                    fin_b_bad += sum(1 for c in col if not (-1e-3 <= c[2] <= 1.001))
        inst = ex.get("instances")
        if inst is not None:
            stride = ex.get("instanceStride", 10)
            if len(inst) % stride:
                errors.append(f"{name}: instances length {len(inst)} not a multiple of {stride}")
            else:
                cnt = len(inst) // stride
                report.setdefault("instances", {})[f"{name}/{n.get('name')}"] = (cnt, tris, cnt * tris)
    if base in FISH and xs_a:
        xs_a.sort()
        xmin, a_tail = xs_a[0]
        xmax, a_snout = xs_a[-1]
        if not (a_snout < SNOUT_A.get(base, 0.08) and a_tail > 0.75):
            errors.append(f"{name}: COLOR_0.a not 0 (snout,+x) -> 1 (tail,-x): snout {a_snout:.3f} tail {a_tail:.3f}")
        # monotonic in bins
        bins = {}
        for x, a in xs_a:
            bins.setdefault(round(x * 10), []).append(a)
        means = [sum(v) / len(v) for k, v in sorted(bins.items())]
        if any(means[i] < means[i + 1] - 0.02 for i in range(len(means) - 1)):
            errors.append(f"{name}: COLOR_0.a is not monotonic along -X")
        if fin_b_bad:
            errors.append(f"{name}: {fin_b_bad} fin vertices with COLOR_0.b outside [0,1]")
        # whole fish extent (all mesh nodes)
    if base in FISH:
        lo, hi = 1e9, -1e9
        for ni, n in g.mesh_nodes():
            for p in j["meshes"][n["mesh"]]["primitives"]:
                a = j["accessors"][p["attributes"]["POSITION"]]
                # bounds via accessor min/max (nodes are untransformed children of Root)
                lo = min(lo, a["min"][0] + wms[ni][0][3])
                hi = max(hi, a["max"][0] + wms[ni][0][3])
        if abs((hi - lo) - 1.0) > 0.03 or hi < 0.45:
            errors.append(f"{name}: fish x extent {lo:.3f}..{hi:.3f} (expected unit length facing +X)")
    budget = BUDGET.get(base)
    if budget is None and base.startswith("store_room_"):
        budget = STORE_ROOM_BUDGET
    for prefix, b in PREFIX_BUDGET.items():
        if budget is None and base.startswith(prefix):
            budget = b
    if base.startswith("stand_"):
        for ni, n in g.mesh_nodes():
            t = g.tri_count(n["mesh"])
            if t > STAND_NODE_BUDGET:
                errors.append(f"{name}: stand node '{n.get('name')}' {t} tris > {STAND_NODE_BUDGET}")
            report.setdefault("stand_nodes", []).append(t)
    if name.endswith("_lod1"):
        cap = LOD1_BUDGET.get(base, 1700)
        if (base in FISH or base in LOD1_BUDGET) and total > cap:
            errors.append(f"{name}: LOD1 {total} tris > {cap}")
    elif budget is not None and total > budget * 1.15:
        errors.append(f"{name}: {total} tris > budget {budget} (+15%)")
    if base in FISH and not name.endswith("_lod1"):
        bb = BODY_BUDGET.get(base, 4500)
        if body_tris > bb:
            errors.append(f"{name}: body {body_tris} tris > {bb}")
    report.setdefault("files", []).append((name, os.path.getsize(path), total, body_tris))


ANCHOR_NAMES = [  # docs/specs/tank_design.md §4 + species anchors + compatibility aliases
    "HOST_ARCH_CLOWNS", "HOST_GSP_RIGHT", "BURROW_GOBY", "den_path", "CAVE_BIG", "CAVE_SHELF", "CAVE_DOTTYBACK",
    "HOLE_BLENNY", "HOLE_BLENNY_2", "PERCH_R3_TOP", "PERCH_R2_CROWN", "PERCH_R5_LIP", "PERCH_SHELF", "PERCH_R9",
    "PERCH_CORALLINE_LOW", "STATION_CLEANER", "SHRIMP_LEDGE_L", "SHRIMP_LEDGE_R", "HOVER_CARDINAL", "SLEEP_TANG",
    "SLEEP_WRASSE", "SURFACE_FEED", "NORI_CLIP", "HOB_OUTFLOW", "FLOW_PH_LEFT", "FLOW_PH_BACK", "FLOW_PH_RIGHT"] + [
    f"GRAZE_{i}" for i in range(1, 9)] + [
    "coral_kenya_main", "coral_gsp_upper_left", "coral_gsp_lower_right", "coral_zoa_front_center",
    "coral_paly_left_foot", "coral_clove_kenya_front", "coral_organpipe_kenya_base", "coral_shroom_center",
    "coral_macro_back_left", "coral_macro_right_corner",
    "SAND_GOBY_DIG", "BRITTLE_HIDE", "DUSTER_BASE", "SAND_FRONT", "CREVICE_SALLY",
    "CAVE_EEL", "HOST_KENYA", "CAVE_SMALL", "HOLE_LAWNMOWER", "HOLE_TAILSPOT", "PERCH_1", "PERCH_2", "PERCH_3",
    "PERCH_4", "PERCH_5", "SHRIMP_LEDGE", "STATION_SKUNK", "STATION_SHRIMP", "BRITTLE_HOME", "DUSTER_TUBE"]


def check_sim(errors, report):
    ap = os.path.join(SIM, "anchors.json")
    if not os.path.exists(ap):
        errors.append("anchors.json missing")
    else:
        a = json.load(open(ap))
        names = {x["name"] for x in a["anchors"]}
        for n in ANCHOR_NAMES:
            if n not in names:
                errors.append(f"anchors.json: missing {n}")
        report["anchors"] = len(names)
    sp = os.path.join(SIM, "rock_sdf.bin")
    if not os.path.exists(sp):
        errors.append("rock_sdf.bin missing")
    else:
        with open(sp, "rb") as f:
            d = f.read()
        if d[:4] != b"RSDF":
            errors.append("rock_sdf.bin: bad magic")
        else:
            nx, ny, nz = struct.unpack("<III", d[4:16])
            ox, oy, oz, cell = struct.unpack("<ffff", d[16:32])
            n = nx * ny * nz
            exp = 32 + n * 2 + n
            if len(d) != exp:
                errors.append(f"rock_sdf.bin: size {len(d)} != {exp}")
            report["sdf"] = (nx, ny, nz, cell, len(d))


def estimates(report):
    """Triangles on screen in the default full-tank view of Tee's tank (DEFAULT_*), and on a fish-store visit.
    High: every DEFAULT_SCENE / DEFAULT_CORALS / DEFAULT_INVERTS model at LOD0 times its count (hidden nodes left
    out), every polyp instance table in full, fish at LOD1 (the default camera is > 60 cm from every fish, ARCHITECTURE
    §LOD; `worst` is the all-LOD0 case), the micro critters' draw budget and the room at home (the largest room, the
    largest stand node and the music corner). Medium: the same with MEDIUM_LOD1 assets swapped for their _lod1.glb
    (their own instance tables) and the other polyp instance tables halved. Store: High without the home room, plus the
    largest store room."""
    files = {name: tris for name, size, tris, body in report.get("files", [])}
    hidden = report.get("hidden", {})
    inst = {}
    for key, (cnt, tris, tot) in report.get("instances", {}).items():
        inst.setdefault(key.split("/")[0], []).append((cnt, tris))
    rooms = max([t for n, t in files.items() if n.startswith("room_")] or [0])
    stand = max(report.get("stand_nodes") or [0])
    home = rooms + stand + files.get("music_corner", 0)
    store = max([t for n, t in files.items() if n.startswith("store_room_")] or [0])
    out = {}
    for level in ("high", "medium"):
        meshes = instances = fish1 = fish0 = 0
        for name, count in {**DEFAULT_SCENE, **DEFAULT_CORALS}.items():
            use = name + "_lod1" if level == "medium" and name in MEDIUM_LOD1 and name + "_lod1" in files else name
            if use not in files:
                continue  # reported by check_default_stock
            meshes += (files[use] - hidden.get(use, 0)) * count
            # the prototype mesh is counted once in `meshes`; the table draws it `cnt` times
            extra = sum((cnt - 1) * t for cnt, t in inst.get(use, [])) * count
            if level == "medium" and use == name:
                extra //= 2
            instances += extra
        # fish and the mobile inverts: LOD1 where shipped (drawOrganism switches beyond lod1DistanceCm)
        for name, count in {**DEFAULT_FISH, **DEFAULT_INVERTS}.items():
            fish0 += files.get(name, 0) * count
            fish1 += files.get(name + "_lod1", files.get(name, 0)) * count
        meshes += MICRO_TRIS_HIGH if level == "high" else MICRO_TRIS_MEDIUM
        tank = meshes + instances + fish1
        out[level] = {"meshes": meshes, "instances": instances, "fish": fish1, "fish_lod0": fish0, "home": home,
                      "total": tank + home, "worst": meshes + instances + fish0 + home, "store": tank + store,
                      "store_room": store}
    return out


def check_default_stock(errors):
    """DEFAULT_FISH / DEFAULT_INVERTS / DEFAULT_CORALS hold as many animals as Presets.kt DefaultStock seeds, and every
    default model exists."""
    for group in (DEFAULT_SCENE, DEFAULT_FISH, DEFAULT_INVERTS, DEFAULT_CORALS):
        for name in group:
            if not os.path.exists(os.path.join(MODELS, name + ".glb")):
                errors.append(f"default tank model {name}.glb missing")
    try:
        src = open(PRESETS, encoding="utf-8").read()
    except OSError:
        return
    for key, group in (("fish", DEFAULT_FISH), ("inverts", DEFAULT_INVERTS), ("corals", DEFAULT_CORALS)):
        m = re.search(r"val %s = listOf\((.*?)\n    \)" % key, src, re.S)
        if not m:
            errors.append(f"Presets.kt: DefaultStock.{key} not found")
            continue
        seeds = len(re.findall(r"^\s*LivestockSeed\(", m.group(1), re.M))
        if seeds != sum(group.values()):
            errors.append(f"Presets.kt DefaultStock.{key} has {seeds} seeds, DEFAULT_{key.upper()} counts "
                          f"{sum(group.values())}: update the default-view lists in validate_glb.py")


def main(argv):
    files = argv or sorted(glob.glob(os.path.join(MODELS, "*.glb")))
    errors, warnings, report = [], [], {}
    for f in files:
        check_glb(f, errors, warnings, report)
    if not argv:
        check_sim(errors, report)
    tot_bytes = 0
    print(f"{'asset':28s} {'bytes':>10s} {'tris':>7s} {'body':>6s}")
    for name, size, tris, body in report.get("files", []):
        tot_bytes += size
        print(f"{name:28s} {size:10d} {tris:7d} {body:6d}")
    for k, (cnt, tris, tot) in sorted(report.get("instances", {}).items()):
        print(f"  instances {k}: {cnt} x {tris} tris = {tot}")
    if "anchors" in report:
        print(f"anchors: {report['anchors']}")
    if "sdf" in report:
        print("rock_sdf.bin: %dx%dx%d cell %.2f cm, %d bytes" % report["sdf"])
    print(f"GLB total: {tot_bytes / 1e6:.2f} MB in {len(report.get('files', []))} files")
    if not argv:
        check_default_stock(errors)
        ests = estimates(report)
        for level, cap in (("high", HIGH_MAX), ("medium", MEDIUM_MAX)):
            est = ests[level]
            print(f"rendered-triangle estimate at {level.capitalize()}, default view: {est['total']} "
                  f"(meshes {est['meshes']}, polyp instances {est['instances']}, fish + inverts LOD1 {est['fish']}, "
                  f"room at home {est['home']}); all animals at LOD0: {est['worst']}")
            if est["total"] > cap:
                errors.append(f"{level.capitalize()} triangle estimate {est['total']} > {cap}")
        est = ests["high"]
        print(f"rendered-triangle estimate on a fish-store visit at High: {est['store']} "
              f"(tank scene {est['store'] - est['store_room']} + largest store room {est['store_room']})")
        if est["store"] > STORE_MAX:
            errors.append(f"store visit triangle estimate {est['store']} > {STORE_MAX}")
        size = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(ASSETS) for f in fs)
        print(f"app/src/main/assets total: {size / 1e6:.2f} MB")
        if size > 45e6:
            errors.append(f"assets {size / 1e6:.1f} MB > 45 MB")
    for w in warnings:
        print("WARN", w)
    for e in errors:
        print("FAIL", e)
    print("OK" if not errors else f"{len(errors)} error(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
