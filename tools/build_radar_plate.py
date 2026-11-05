"""Derive a clean radar plate from the authored radar arena.

The authored `radar_grid.glb` is shared with the first-person battle arena, so it keeps the sky dome,
the floating voxel set pieces and the battle boundary ring - none of which belong to the isometric
radar world (the ring reads as a false range ring and the blocks float over the compass rings).
It also enlarges the authored plain floor quad (theme-bound, no texture coordinates) far past the
authored grid, so the ground continues to the horizon at the Radar place's lowest camera angle and
widest zoom instead of ending in a visible edge.

This script writes `radar_plate.glb` with only the radar floor and its world grid, plus the matching
manifest `plate.json`, so both assets stay byte-for-byte derived from the same authored source.
"""
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/assets/Arena/Radar/radar_grid.glb"
OUT = ROOT / "app/src/main/assets/Arena/Radar/radar_plate.glb"
MANIFEST = ROOT / "app/src/main/assets/Arena/Radar/plate.json"


def read_glb(path):
    data = path.read_bytes()
    magic, version, length = struct.unpack_from("<III", data, 0)
    assert magic == 0x46546C67, "not a GLB"
    chunks = []
    offset = 12
    while offset < length:
        chunk_length, chunk_type = struct.unpack_from("<II", data, offset)
        offset += 8
        chunks.append((chunk_type, data[offset:offset + chunk_length]))
        offset += chunk_length
    return chunks


def write_glb(path, chunks):
    body = b""
    for chunk_type, payload in chunks:
        padded = payload + b" " * ((4 - len(payload) % 4) % 4)
        body += struct.pack("<II", len(padded), chunk_type) + padded
    header = struct.pack("<III", 0x46546C67, 2, 12 + len(body))
    path.write_bytes(header + body)


chunks = read_glb(SRC)
json_bytes = chunks[0][1].rstrip(b" \t\r\n\x00")
gltf = json.loads(json_bytes.decode("utf-8"))

nodes = gltf["nodes"]
root = nodes[0]
# Root keeps only its own mesh: the voxel set pieces and the authored sky dome are not radar.
root["children"] = []
# Drop the boundary primitive (third primitive of the floor mesh) and its material reference.
arena_mesh = gltf["meshes"][0]
arena_mesh["primitives"] = [
    primitive for primitive in arena_mesh["primitives"]
    if primitive.get("name") != "battle_boundary"
]
# Keep the JSON tidy: drop unreferenced nodes so the derived asset cannot regrow the props.
gltf["nodes"] = [root]
gltf["scenes"] = [{"nodes": [0]}]
gltf["images"] = [image for image in gltf.get("images", []) if "bufferView" in image]
gltf["samplers"] = gltf.get("samplers", [])

# The authored floor is a plain 400x400 quad (no texture coordinates) that the environment theme
# paints with the palette's floor colour. Enlarging that quad gives the Radar place a ground that
# continues past the grid for any zoom and any camera angle without a second material, so the floor
# edge can never be seen (and the skirt keeps the exact themed colour automatically).
FLOOR_HALF_EXTENT = 2000.0
floor_primitive = next(p for p in arena_mesh["primitives"] if p.get("name") == "radar_floor")
position_accessor_index = floor_primitive["attributes"]["POSITION"]
accessor = gltf["accessors"][position_accessor_index]
view = gltf["bufferViews"][accessor["bufferView"]]
bin_chunk = bytearray(chunks[1][1])
base_offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
stride = view.get("byteStride", 12)
for vertex in range(accessor["count"]):
    vertex_offset = base_offset + vertex * stride
    x, y, z = struct.unpack_from("<fff", bin_chunk, vertex_offset)
    struct.pack_into(
        "<fff",
        bin_chunk,
        vertex_offset,
        FLOOR_HALF_EXTENT if x > 0 else -FLOOR_HALF_EXTENT,
        y,
        FLOOR_HALF_EXTENT if z > 0 else -FLOOR_HALF_EXTENT,
    )
accessor["min"] = [-FLOOR_HALF_EXTENT, 0.0, -FLOOR_HALF_EXTENT]
accessor["max"] = [FLOOR_HALF_EXTENT, 0.0, FLOOR_HALF_EXTENT]
chunks[1] = (chunks[1][0], bytes(bin_chunk))
# Anything no longer referenced (meshes, materials, textures, accessors) is kept in place: stripping
# the indices would invalidate every bufferView in the shared binary chunk.

new_json = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
write_glb(OUT, [(0x4E4F534A, new_json)] + chunks[1:])

manifest = json.loads((ROOT / "app/src/main/assets/Arena/Radar/arena.json").read_text(encoding="utf-8"))
manifest["assetPath"] = "Arena/Radar/radar_plate.glb"
manifest["meshes"] = {"floor": "radar_floor", "grid": "world_grid"}
# The reader requires the camera collision radius to exceed the playable radius; the radar place
# never uses the arena's combat bounds, so shrink both around the authored plate.
manifest["playableRadius"] = 4.0
manifest["cameraCollisionRadius"] = 5.0
MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

print("wrote", OUT, OUT.stat().st_size, "bytes")
print("wrote", MANIFEST)
print("primitives:", [p.get("name") for p in arena_mesh["primitives"]])
print("root children:", root.get("children"))
