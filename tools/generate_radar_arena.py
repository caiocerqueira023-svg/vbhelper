"""Generate the unlit GLB used by World Radar battles."""

from __future__ import annotations

import copy
import io
import json
import math
import struct
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/Arena/Radar/radar_grid.glb"
DOME_SOURCE = ROOT / "app/src/main/assets/Arena/Colosseum/Colosseum.glb"
VOXEL_CLUSTERS = (
    ((-11.8, 1.4, -18.5), -0.38, (
        (-.75, -.20, -.28, 2.35, 2.35, 2.35, False),
        (1.05, 1.25, .42, 1.55, 1.55, 1.55, True),
        (-1.85, 1.55, .20, 1.05, 1.05, 1.05, False),
        (.20, 2.55, -.62, .82, 1.35, .82, True),
    )),
    ((3.8, 3.0, -23.2), 0.72, (
        (.10, -.40, .20, 2.70, 2.70, 2.70, True),
        (-1.85, 1.35, -.35, 1.35, 1.35, 1.35, False),
        (1.65, 1.70, .60, 1.05, 1.70, 1.05, False),
    )),
    ((16.4, 1.2, -14.8), 1.18, (
        (-.35, -.10, .05, 2.25, 2.25, 2.25, False),
        (1.45, 1.20, -.55, 1.50, 1.50, 1.50, True),
        (-1.65, 1.65, .50, 1.15, 1.15, 1.15, False),
        (.25, 2.55, .78, .78, .78, .78, True),
    )),
    ((22.0, 2.5, -2.5), 1.78, (
        (.30, -.25, .15, 2.85, 2.85, 2.85, False),
        (-1.90, 1.55, -.65, 1.35, 1.85, 1.35, True),
        (1.90, 1.90, .48, 1.10, 1.10, 1.10, False),
    )),
    ((19.5, 1.4, 15.3), 2.32, (
        (-.45, -.15, -.25, 2.40, 2.40, 2.40, True),
        (1.45, 1.20, .45, 1.65, 1.65, 1.65, False),
        (-1.75, 1.55, .60, 1.15, 1.15, 1.15, False),
        (.45, 2.50, -.70, .88, 1.45, .88, True),
    )),
    ((2.3, 4.1, 21.4), 2.92, (
        (.20, -.55, .10, 2.95, 2.95, 2.95, False),
        (-2.05, 1.30, .55, 1.45, 1.45, 1.45, True),
        (1.95, 1.85, -.50, 1.10, 1.70, 1.10, False),
    )),
    ((-15.7, 1.0, 16.9), 3.42, (
        (-.25, .00, .10, 2.35, 2.35, 2.35, False),
        (1.55, 1.30, -.45, 1.55, 1.55, 1.55, True),
        (-1.75, 1.65, .35, 1.20, 1.20, 1.20, False),
        (.35, 2.70, .72, .82, .82, .82, True),
    )),
    ((-22.8, 2.8, 4.2), 4.18, (
        (.35, -.35, -.20, 2.75, 2.75, 2.75, True),
        (-1.95, 1.45, .55, 1.40, 1.85, 1.40, False),
        (1.85, 1.85, -.45, 1.05, 1.05, 1.05, False),
    )),
    ((-19.1, 4.0, -13.0), 4.82, (
        (-.20, -.50, .15, 3.05, 3.05, 3.05, False),
        (2.05, 1.40, -.55, 1.45, 1.45, 1.45, True),
        (-1.95, 1.95, .50, 1.10, 1.70, 1.10, False),
    )),
)


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


def png_chunk(kind: bytes, payload: bytes):
    return (struct.pack(">I", len(payload)) + kind + payload +
            struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))


def voxel_texture(lit: bool):
    """Small baked panel texture matching the Digifarm's floating voxels."""
    size = 128
    base = (35, 23, 59) if lit else (14, 10, 26)
    grid = (111, 78, 177) if lit else (69, 47, 113)
    edge = (167, 139, 250) if lit else (139, 92, 246)
    rows = bytearray()
    for y in range(size):
        rows.append(0)  # PNG filter: none
        for x in range(size):
            color = base
            if any(abs(x - p) <= 1 for p in (32, 64, 96)) or any(
                abs(y - p) <= 1 for p in (32, 64, 96)
            ):
                color = grid
            if 13 <= x <= 114 and 13 <= y <= 114 and (
                x in (13, 14, 113, 114) or y in (13, 14, 113, 114)
            ):
                color = grid
            if 1 <= x <= size - 2 and 1 <= y <= size - 2 and (
                x < 10 or x >= size - 10 or y < 10 or y >= size - 10
            ):
                color = edge
            rows.extend(color)

    header = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header) +
            png_chunk(b"IDAT", zlib.compress(bytes(rows), 9)) +
            png_chunk(b"IEND", b""))


def add_cube_mesh(binary, buffer_views, accessors, name, material):
    faces = (
        ((-.5, -.5, .5), (.5, -.5, .5), (.5, .5, .5), (-.5, .5, .5)),
        ((.5, -.5, -.5), (-.5, -.5, -.5), (-.5, .5, -.5), (.5, .5, -.5)),
        ((.5, -.5, .5), (.5, -.5, -.5), (.5, .5, -.5), (.5, .5, .5)),
        ((-.5, -.5, -.5), (-.5, -.5, .5), (-.5, .5, .5), (-.5, .5, -.5)),
        ((-.5, .5, .5), (.5, .5, .5), (.5, .5, -.5), (-.5, .5, -.5)),
        ((-.5, -.5, -.5), (.5, -.5, -.5), (.5, -.5, .5), (-.5, -.5, .5)),
    )
    vertices, uv, indices = [], [], []
    for face in faces:
        start = len(vertices)
        vertices.extend(face)
        uv.extend(((0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0)))
        indices.extend((start, start + 1, start + 2, start, start + 2, start + 3))

    align4(binary)
    position_offset = len(binary)
    for vertex in vertices:
        binary.extend(struct.pack("<3f", *vertex))
    position_view = len(buffer_views)
    buffer_views.append({"buffer": 0, "byteOffset": position_offset,
                         "byteLength": len(vertices) * 12, "target": 34962})
    position_accessor = len(accessors)
    accessors.append({
        "bufferView": position_view, "componentType": 5126,
        "count": len(vertices), "type": "VEC3",
        "min": [-.5, -.5, -.5], "max": [.5, .5, .5],
    })

    align4(binary)
    uv_offset = len(binary)
    for texcoord in uv:
        binary.extend(struct.pack("<2f", *texcoord))
    uv_view = len(buffer_views)
    buffer_views.append({"buffer": 0, "byteOffset": uv_offset,
                         "byteLength": len(uv) * 8, "target": 34962})
    uv_accessor = len(accessors)
    accessors.append({
        "bufferView": uv_view, "componentType": 5126,
        "count": len(uv), "type": "VEC2", "min": [0.0, 0.0], "max": [1.0, 1.0],
    })

    align4(binary)
    index_offset = len(binary)
    for value in indices:
        binary.extend(struct.pack("<H", value))
    index_view = len(buffer_views)
    buffer_views.append({"buffer": 0, "byteOffset": index_offset,
                         "byteLength": len(indices) * 2, "target": 34963})
    index_accessor = len(accessors)
    accessors.append({
        "bufferView": index_view, "componentType": 5123,
        "count": len(indices), "type": "SCALAR", "min": [0], "max": [23],
    })
    return {
        "name": name,
        "primitives": [{
            "name": name, "attributes": {"POSITION": position_accessor, "TEXCOORD_0": uv_accessor},
            "indices": index_accessor, "material": material, "mode": 4,
        }],
    }


def read_glb(path: Path):
    raw = path.read_bytes()
    magic, version, total_length = struct.unpack_from("<III", raw, 0)
    if magic != 0x46546C67 or version != 2 or total_length != len(raw):
        raise ValueError(f"Invalid GLB: {path}")
    json_length, json_type = struct.unpack_from("<II", raw, 12)
    if json_type != 0x4E4F534A:
        raise ValueError(f"Missing JSON chunk: {path}")
    document = json.loads(raw[20:20 + json_length].decode("utf-8").rstrip(" \0"))
    binary_header = 20 + json_length
    binary_length, binary_type = struct.unpack_from("<II", raw, binary_header)
    if binary_type != 0x004E4942:
        raise ValueError(f"Missing binary chunk: {path}")
    binary_start = binary_header + 8
    return document, raw[binary_start:binary_start + binary_length]


def smoothstep(edge_start, edge_end, value):
    amount = max(0.0, min(1.0, (value - edge_start) / (edge_end - edge_start)))
    return amount * amount * (3.0 - 2.0 * amount)


def adapt_radar_dome_texture(source_png):
    """Bring the training dome into the Radar palette and soften its horizon."""
    with Image.open(io.BytesIO(source_png)) as source:
        grayscale = ImageOps.autocontrast(ImageOps.grayscale(source), cutoff=1)

    purple_mapped = ImageOps.colorize(
        grayscale,
        black=(14, 10, 26),
        mid=(69, 47, 113),
        white=(139, 92, 246),
    ).convert("RGBA")
    cyan_tint = ImageOps.colorize(
        grayscale,
        black=(14, 10, 26),
        mid=(45, 95, 118),
        white=(45, 225, 252),
    ).convert("RGBA")
    base_cyan_mask = grayscale.point(
        lambda value: round(255 * 0.04 * (value / 255) ** 1.35)
    )
    glow_cyan_mask = grayscale.point(
        lambda value: round(255 * 0.30 * (value / 255) ** 1.15)
    )
    mapped = Image.composite(cyan_tint, purple_mapped, base_cyan_mask)
    emissive_palette = Image.composite(cyan_tint, purple_mapped, glow_cyan_mask)
    alpha_mask = Image.new("L", mapped.size)
    emission_mask = Image.new("L", mapped.size)
    alpha_draw = ImageDraw.Draw(alpha_mask)
    emission_draw = ImageDraw.Draw(emission_mask)
    width, height = mapped.size
    for y in range(height):
        texture_v = (y + 0.5) / height
        distance_from_horizon = abs(texture_v - 0.5)
        sky_amount = smoothstep(0.0, 0.30, distance_from_horizon)
        emission_amount = smoothstep(0.0, 0.40, distance_from_horizon)
        alpha_draw.line((0, y, width, y), fill=round(255 * (0.58 + 0.42 * sky_amount)))
        emission_amount = 0.58 + 0.42 * emission_amount
        emission_draw.line((0, y, width, y), fill=round(255 * emission_amount))

    adapted = mapped.copy()
    adapted.putalpha(alpha_mask)
    emissive = Image.composite(
        emissive_palette,
        Image.new("RGBA", mapped.size, (0, 0, 0, 255)),
        emission_mask,
    )

    base_output = io.BytesIO()
    adapted.save(base_output, format="PNG", optimize=True)
    emissive_output = io.BytesIO()
    emissive.save(emissive_output, format="PNG", optimize=True)
    return base_output.getvalue(), emissive_output.getvalue()


def import_training_dome(
    binary, buffer_views, accessors, material_index, texture_index, emissive_texture_index
):
    """Import the training-arena dome and adapt it to the Radar scene."""
    source, source_binary = read_glb(DOME_SOURCE)
    dome_node = next(node for node in source["nodes"] if node.get("name") == "Sphere001")
    source_mesh = source["meshes"][dome_node["mesh"]]
    source_primitive = source_mesh["primitives"][0]
    copied_views = {}

    def copy_view(source_index):
        if source_index in copied_views:
            return copied_views[source_index]
        source_view = source["bufferViews"][source_index]
        start = source_view.get("byteOffset", 0)
        end = start + source_view["byteLength"]
        align4(binary)
        target_view = copy.deepcopy(source_view)
        target_view["buffer"] = 0
        target_view["byteOffset"] = len(binary)
        binary.extend(source_binary[start:end])
        target_index = len(buffer_views)
        buffer_views.append(target_view)
        copied_views[source_index] = target_index
        return target_index

    def copy_accessor(source_index):
        target_accessor = copy.deepcopy(source["accessors"][source_index])
        target_accessor["bufferView"] = copy_view(target_accessor["bufferView"])
        target_index = len(accessors)
        accessors.append(target_accessor)
        return target_index

    target_primitive = {
        "name": "RadarSkyDome",
        "attributes": {
            semantic: copy_accessor(accessor)
            for semantic, accessor in source_primitive["attributes"].items()
        },
        "indices": copy_accessor(source_primitive["indices"]),
        "material": material_index,
        "mode": source_primitive.get("mode", 4),
    }

    source_material = source["materials"][source_primitive["material"]]
    source_texture_index = source_material["pbrMetallicRoughness"]["baseColorTexture"]["index"]
    source_texture = source["textures"][source_texture_index]
    source_image = source["images"][source_texture["source"]]
    source_image_view = source["bufferViews"][source_image["bufferView"]]
    image_start = source_image_view.get("byteOffset", 0)
    image_end = image_start + source_image_view["byteLength"]
    adapted_image, emissive_image = adapt_radar_dome_texture(
        source_binary[image_start:image_end]
    )

    def append_image(image):
        align4(binary)
        image_view = len(buffer_views)
        buffer_views.append({
            "buffer": 0,
            "byteOffset": len(binary),
            "byteLength": len(image),
        })
        binary.extend(image)
        return image_view

    target_image = copy.deepcopy(source_image)
    target_image["name"] = "RadarSkyDome"
    target_image["bufferView"] = append_image(adapted_image)
    target_emissive_image = copy.deepcopy(source_image)
    target_emissive_image["name"] = "RadarSkyDomeEmission"
    target_emissive_image["bufferView"] = append_image(emissive_image)

    target_material = copy.deepcopy(source_material)
    target_material["name"] = "RadarSkyDome"
    target_material["alphaMode"] = "BLEND"
    target_material["pbrMetallicRoughness"]["baseColorFactor"] = [1.0, 1.0, 1.0, 1.0]
    target_material["pbrMetallicRoughness"]["baseColorTexture"]["index"] = texture_index
    target_material["emissiveTexture"]["index"] = emissive_texture_index
    target_material["emissiveFactor"] = [0.68, 0.68, 0.68]
    target_sampler = copy.deepcopy(source["samplers"][source_texture["sampler"]])
    return (
        {"name": "RadarSkyDome", "primitives": [target_primitive]},
        (target_image, target_emissive_image),
        target_material,
        target_sampler,
    )


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

    dark_image = voxel_texture(False)
    lit_image = voxel_texture(True)
    image_views = []
    for image in (dark_image, lit_image):
        align4(binary)
        image_offset = len(binary)
        binary.extend(image)
        image_views.append(len(buffer_views))
        buffer_views.append({"buffer": 0, "byteOffset": image_offset, "byteLength": len(image)})

    align4(binary)
    materials = [
        ("radar_floor", color_from_srgb("#0A0812"), "OPAQUE"),
        ("world_grid", color_from_srgb("#3C3260", 0.72), "BLEND"),
        ("battle_boundary", color_from_srgb("#2DE1FC", 0.65), "BLEND"),
    ]
    voxel_materials = [
        {
            "name": name,
            "doubleSided": True,
            "extensions": {"KHR_materials_unlit": {}},
            "pbrMetallicRoughness": {
                "baseColorFactor": [1.0, 1.0, 1.0, 1.0],
                "baseColorTexture": {"index": index, "texCoord": 0},
                "metallicFactor": 0.0,
                "roughnessFactor": 1.0,
            },
        }
        for index, name in enumerate(("voxel_block_dark_baked", "voxel_block_lit_baked"))
    ]
    meshes = [{"name": "WorldRadarArena", "primitives": primitives}]
    meshes.append(add_cube_mesh(binary, buffer_views, accessors, "RadarVoxelDark", 3))
    meshes.append(add_cube_mesh(binary, buffer_views, accessors, "RadarVoxelLit", 4))
    dome_mesh, dome_images, dome_material, dome_sampler = import_training_dome(
        binary, buffer_views, accessors, material_index=5,
        texture_index=2, emissive_texture_index=3,
    )
    meshes.append(dome_mesh)
    nodes = [
        {"name": "WorldRadarArena", "mesh": 0, "children": [1, 2]},
        {"name": "RadarVoxelFragments", "children": []},
        {"name": "Sphere001", "mesh": 3},
    ]
    for group_index, (position, yaw, group) in enumerate(VOXEL_CLUSTERS, 1):
        group_node_index = len(nodes)
        nodes.append({
            "name": f"RadarVoxelFragmentsFar{group_index:02}",
            "translation": list(position),
            "rotation": [0.0, math.sin(yaw / 2), 0.0, math.cos(yaw / 2)],
            "children": [],
        })
        nodes[1]["children"].append(group_node_index)
        for block_index, (x, y, z, sx, sy, sz, lit) in enumerate(group, 1):
            block_node_index = len(nodes)
            nodes.append({
                "name": f"RadarVoxelBlockFar{group_index:02}_{block_index:02}",
                "mesh": 2 if lit else 1,
                "translation": [x, y, z],
                "scale": [sx, sy, sz],
            })
            nodes[group_node_index]["children"].append(block_node_index)

    document = {
        "asset": {"version": "2.0", "generator": "VBHelper radar arena generator"},
        "extensionsUsed": ["KHR_materials_unlit"],
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": nodes,
        "meshes": meshes,
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
        ] + voxel_materials + [dome_material],
        "images": [
            {"name": "voxel_block_dark", "bufferView": image_views[0], "mimeType": "image/png"},
            {"name": "voxel_block_lit", "bufferView": image_views[1], "mimeType": "image/png"},
            *dome_images,
        ],
        "samplers": [
            {"magFilter": 9729, "minFilter": 9729, "wrapS": 33071, "wrapT": 33071},
            dome_sampler,
        ],
        "textures": [
            {"sampler": 0, "source": 0},
            {"sampler": 0, "source": 1},
            {"sampler": 1, "source": 2},
            {"sampler": 1, "source": 3},
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
