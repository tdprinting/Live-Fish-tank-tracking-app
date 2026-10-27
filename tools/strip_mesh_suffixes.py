#!/usr/bin/env python3
"""Strip Blender `.NNN` duplicate suffixes from glTF MESH names in .glb files (stdlib only).

Only `meshes[*].name` in the JSON chunk is changed. Node names, the BIN chunk and everything else are kept
byte-identical. The JSON chunk is re-serialised compactly and padded with spaces to a 4-byte boundary (GLB spec).
A file is skipped, and reported, if stripping would make two mesh names in it equal.

Usage: python3 tools/strip_mesh_suffixes.py [--dry-run] [files...]
       (default: every app/src/main/assets/models/*.glb)
"""
import glob
import json
import os
import re
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODELS = os.path.join(ROOT, "app", "src", "main", "assets", "models")
SUFFIX = re.compile(r"\.\d{3}$")
MAGIC, VERSION, JSON_TYPE = 0x46546C67, 2, 0x4E4F534A


def strip_file(path, dry_run):
    with open(path, "rb") as f:
        data = f.read()
    magic, version, length = struct.unpack("<III", data[:12])
    if magic != MAGIC or version != VERSION or length != len(data):
        return "error: not a GLB 2.0 file of the stated length"
    clen, ctype = struct.unpack("<II", data[12:20])
    if ctype != JSON_TYPE:
        return "error: first chunk is not JSON"
    json_end = 20 + clen
    tail = data[json_end:]  # BIN chunk (and any padding), copied verbatim
    doc = json.loads(data[20:json_end].decode("utf-8"))

    meshes = doc.get("meshes", [])
    old = [m.get("name", "") for m in meshes]
    new = [SUFFIX.sub("", n) for n in old]
    if new == old:
        return "unchanged"
    dupes = sorted({n for n in new if new.count(n) > 1})
    if dupes:
        return f"skipped: stripping would duplicate mesh names {dupes}"
    for m, n in zip(meshes, new):
        if "name" in m:
            m["name"] = n

    body = json.dumps(doc, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    body += b" " * (-len(body) % 4)  # pad JSON chunk with spaces to 4-byte alignment
    out = struct.pack("<III", MAGIC, VERSION, 12 + 8 + len(body) + len(tail))
    out += struct.pack("<II", len(body), JSON_TYPE) + body + tail

    changes = ", ".join(f"{o} -> {n}" for o, n in zip(old, new) if o != n)
    if not dry_run:
        tmp = path + ".tmp"
        with open(tmp, "wb") as f:
            f.write(out)
        os.replace(tmp, path)
    return f"stripped: {changes}"


def main(argv):
    dry_run = "--dry-run" in argv
    files = [a for a in argv if a != "--dry-run"] or sorted(glob.glob(os.path.join(MODELS, "*.glb")))
    failed = False
    for path in files:
        result = strip_file(path, dry_run)
        if result.startswith("error"):
            failed = True
        if result != "unchanged":
            print(f"{os.path.basename(path)}: {result}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
