"""Generate the tiny unlit GLB used by World Radar battles."""

from __future__ import annotations

import json
import math
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/Arena/Radar/radar_grid.glb"


def quad(vertices, indices, a, b, c, d):
    start = len(vertices)
    vertices.extend((a, b, c, d))
    indices.extend((start, start + 1, start + 2, start, start + 2, start + 3))


def rectangle(vertices, indices, x0, z0, x1, z1, y):
    quad(vertices, indices, (x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1))


def color_from_srgb(hex_color: str, alpha: float = 1.0):
    channels = [int(hex_color[index:index + 2], 16) / 255.0 for index in (1, 3, 5)]
    linear = [
        channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4
        for channel in channels
    ]
    return [*linear, alpha]


def build_geometry():
    groups = []
    # The camera far plane is 140 units; this margin keeps the grid visible to every edge.
    extent = 200.0
    grid_step = 2

    floor_vertices, floor_indices = [], []
    rectangle(floor_vertices, floor_indices, -extent, -extent, extent, extent, 0.0)
    groups.append(("radar_floor", floor_vertices, floor_indices, 0))

    grid_vertices, grid_indices = [], []
    thickness = 0.025
    for coordinate in range(-int(extent), int(extent) + 1, grid_step):
        rectangle(grid_vertices, grid_indices, coordinate - thickness, -extent, coordinate + thickness, extent, 0.018)
        rectangle(grid_vertices, grid_indices, -extent, coordinate - thickness, extent, coordinate + thickness, 0.018)
    groups.append(("world_grid", grid_vertices, grid_indices, 1))

    ring_vertices, ring_indices = [], []
    segments = 96
    inner, outer = 7.92, 8.08
    for index in range(segments):
        a0 = math.tau * index / segments
        a1 = math.tau * (index + 1) / segments
        quad(
            ring_vertices,
            ring_indices,
            (math.cos(a0) * inner, 0.032, math.sin(a0) * inner),
            (math.cos(a0) * outer, 0.032, math.sin(a0) * outer),
            (math.cos(a1) * outer, 0.032, math.sin(a1) * outer),
            (math.cos(a1) * inner, 0.032, math.sin(a1) * inner),
        )
    groups.append(("battle_boundary", ring_vertices, ring_indices, 2))
    return groups


def align4(data: bytearray):
    while len(data) % 4:
        data.append(0)


def generate():
    binary = bytearray()
    buffer_views = []
    accessors = []
    primitives = []

    for name, vertices, indices, material in build_geometry():
        align4(binary)
        vertex_offset = len(binary)
        for vertex in vertices:
            binary.extend(struct.pack("<3f", *vertex))
        buffer_views.append({"buffer": 0, "byteOffset": vertex_offset, "byteLength": len(vertices) * 12, "target": 34962})
        xs, ys, zs = zip(*vertices)
        position_accessor = len(accessors)
        accessors.append({
            "bufferView": len(buffer_views) - 1,
            "componentType": 5126,
            "count": len(vertices),
            "type": "VEC3",
            "min": [min(xs), min(ys), min(zs)],
            "max": [max(xs), max(ys), max(zs)],
        })

        align4(binary)
        index_offset = len(binary)
        for value in indices:
            binary.extend(struct.pack("<H", value))
        buffer_views.append({"buffer": 0, "byteOffset": index_offset, "byteLength": len(indices) * 2, "target": 34963})
        index_accessor = len(accessors)
        accessors.append({
            "bufferView": len(buffer_views) - 1,
            "componentType": 5123,
            "count": len(indices),
            "type": "SCALAR",
            "min": [min(indices)],
            "max": [max(indices)],
        })
        primitives.append({
            "name": name,
            "attributes": {"POSITION": position_accessor},
            "indices": index_accessor,
            "material": material,
            "mode": 4,
        })

    align4(binary)
    materials = [
        ("radar_floor", color_from_srgb("#0A0812"), "OPAQUE"),
        ("world_grid", color_from_srgb("#3C3260", 0.72), "BLEND"),
        ("battle_boundary", color_from_srgb("#2DE1FC", 0.65), "BLEND"),
    ]
    document = {
        "asset": {"version": "2.0", "generator": "VBHelper radar arena generator"},
        "extensionsUsed": ["KHR_materials_unlit"],
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": "WorldRadarArena", "mesh": 0}],
        "meshes": [{"name": "WorldRadarArena", "primitives": primitives}],
        "materials": [
            {
                "name": name,
                "doubleSided": True,
                "alphaMode": alpha,
                "extensions": {"KHR_materials_unlit": {}},
                "pbrMetallicRoughness": {
                    "baseColorFactor": color,
                    "metallicFactor": 0.0,
                    "roughnessFactor": 1.0,
                },
            }
            for name, color, alpha in materials
        ],
        "bufferViews": buffer_views,
        "accessors": accessors,
        "buffers": [{"byteLength": len(binary)}],
    }
    json_chunk = json.dumps(document, separators=(",", ":")).encode("utf-8")
    while len(json_chunk) % 4:
        json_chunk += b" "
    total_length = 12 + 8 + len(json_chunk) + 8 + len(binary)
    glb = bytearray(struct.pack("<III", 0x46546C67, 2, total_length))
    glb.extend(struct.pack("<II", len(json_chunk), 0x4E4F534A))
    glb.extend(json_chunk)
    glb.extend(struct.pack("<II", len(binary), 0x004E4942))
    glb.extend(binary)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(glb)
    print(f"Generated {OUTPUT} ({len(glb)} bytes)")


if __name__ == "__main__":
    generate()
