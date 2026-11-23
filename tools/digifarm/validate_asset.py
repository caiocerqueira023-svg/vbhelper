"""Validate a built single-island Digifarm GLB (and optionally its APK copy)."""

import argparse
import hashlib
import io
import json
import struct
import zipfile
from pathlib import Path

from PIL import Image

# The authored 512 island top is static at runtime (no baked glow): the GLB must carry the
# committed texture byte for byte. The pulsing glow lives in a separate RGBA overlay
# primitive whose alpha marks the lightest grass tiles + wire lines.
ISLAND_TOP_SHA256 = "f3810df8d9b345f59854df82d1662e7104501ec9f21d49ddf92481f14e789896"
OVERLAY_MATERIAL = "island_glow_overlay_baked"
OVERLAY_OFFSET_RANGE = (0.0004, 0.001)  # world +Y, above the static floor


def node_matrix(node):
    translation = node.get('translation', [0, 0, 0])
    rotation = node.get('rotation', [0, 0, 0, 1])
    scale = node.get('scale', [1, 1, 1])
    x, y, z, w = rotation
    rot = [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ]
    return [[rot[row][col] * scale[col] for col in range(3)] + [translation[row]]
            for row in range(3)] + [[0, 0, 0, 1]]


def world_point(matrix, point):
    return [sum(matrix[row][col] * point[col] for col in range(3)) + matrix[row][3]
            for row in range(3)]


def position_accessor(doc, payload, primitive_index, primitives):
    accessor = doc['accessors'][primitives[primitive_index]['attributes']['POSITION']]
    view = doc['bufferViews'][accessor['bufferView']]
    start = view.get('byteOffset', 0) + accessor.get('byteOffset', 0)
    values = struct.unpack_from('<' + 'f' * (accessor['count'] * 3), payload, start)
    points = [values[index * 3:index * 3 + 3] for index in range(accessor['count'])]
    return values, points


def image_bytes(doc, payload, image_index):
    view = doc['bufferViews'][doc['images'][image_index]['bufferView']]
    start = view.get('byteOffset', 0)
    return payload[start:start + view['byteLength']]


def inspect_glb(raw):
    assert raw[:4] == b'glTF', 'GLB magic'
    assert struct.unpack_from('<I', raw, 4)[0] == 2, 'GLB version'
    assert struct.unpack_from('<I', raw, 8)[0] == len(raw), 'GLB length'
    json_length = struct.unpack_from('<I', raw, 12)[0]
    assert raw[16:20] == b'JSON', 'JSON chunk'
    doc = json.loads(raw[20:20 + json_length])
    bin_offset = 20 + json_length
    bin_length = struct.unpack_from('<I', raw, bin_offset)[0]
    assert raw[bin_offset + 4:bin_offset + 8] == b'BIN\x00', 'BIN chunk'
    binary_start = bin_offset + 8
    payload = raw[binary_start:binary_start + bin_length]
    names = [node.get('name', '') for node in doc.get('nodes', [])]
    assert any('Island' in name for name in names), names
    assert 'IslandVoxelEdge' not in names, names
    floating_names = {f'VoxelFragmentsNear{index:02}' for index in range(1, 7)}
    assert {name for name in names if name.startswith('VoxelFragmentsNear')} == floating_names, names
    assert all('translation' in node for node in doc['nodes'] if node.get('name') in floating_names), 'Floating nodes need movable transforms'
    block_counts = (3, 3, 2, 2, 2, 2)
    block_names = {
        f'VoxelBlockNear{group:02}_{block:02}'
        for group, count in enumerate(block_counts, 1)
        for block in range(1, count + 1)
    }
    blocks = {node.get('name'): node for node in doc['nodes']
              if node.get('name', '').startswith('VoxelBlockNear')}
    assert set(blocks) == block_names, blocks.keys()
    assert all('mesh' in blocks[name] and 'translation' in blocks[name]
               for name in block_names), 'Every cube needs its own mesh and transform'
    assert len({blocks[name]['mesh'] for name in block_names}) == len(block_names), 'Cubes must be independently renderable'
    for group, count in enumerate(block_counts, 1):
        parent = next(node for node in doc['nodes']
                      if node.get('name') == f'VoxelFragmentsNear{group:02}')
        children = {doc['nodes'][child].get('name') for child in parent.get('children', [])}
        expected = {f'VoxelBlockNear{group:02}_{block:02}' for block in range(1, count + 1)}
        assert children == expected, (parent.get('name'), children, expected)
    assert not any(name.startswith('VoxelFragmentsFar') for name in names), names
    assert not any('Colosseum' in name or 'Stadium' in name for name in names), names
    materials = {material.get('name'): material for material in doc['materials']}
    assert set(materials) == {
        'island_top_baked', 'island_side_baked', 'island_side_rim_baked',
        OVERLAY_MATERIAL,
        'voxel_block_dark_baked', 'voxel_block_lit_baked',
    }, materials.keys()
    # The opaque upper cliff bands must render in the opaque pass (alpha mask, depth write)
    # so the near side of the island hides the far side that used to show through.
    rim = materials['island_side_rim_baked']
    assert rim.get('alphaMode') == 'MASK', 'Upper cliff bands must be alpha-masked'
    # The authored strata lines are semi-transparent (alpha 110/150); a higher cutoff would
    # eat them and the island's side lines would read as broken aliasing.
    assert rim.get('alphaCutoff') == 0.25, rim
    assert 'baseColorTexture' in rim['pbrMetallicRoughness'], 'Upper cliff needs the alpha source'
    island = next(node for node in doc['nodes'] if node.get('name') == 'Island')
    island_primitives = doc['meshes'][island['mesh']]['primitives']
    assert len(island_primitives) == 4, island_primitives
    primitive_materials = [record['material'] for record in island_primitives]
    material_names = [doc['materials'][index]['name'] for index in primitive_materials]
    assert material_names == ['island_top_baked', 'island_side_baked',
                              'island_side_rim_baked', OVERLAY_MATERIAL], material_names
    for side_name in ('island_side_baked',):
        side = materials[side_name]
        assert side.get('alphaMode') == 'BLEND', f'{side_name} must blend'
        texture_index = side['pbrMetallicRoughness']['baseColorTexture']['index']
        texture = doc['textures'][texture_index]
        sampler = doc['samplers'][texture['sampler']]
        assert sampler.get('wrapT') == 33071, f'{side_name} V must clamp'
        image = Image.open(io.BytesIO(image_bytes(doc, payload, texture['source']))).convert('RGBA')
        alpha = image.getchannel('A')
        assert alpha.getextrema() == (0, 255), f'{side_name} needs alpha range'
        assert alpha.crop((0, image.height - 20, image.width, image.height)).getextrema() == (0, 0), f'{side_name} foot must disappear'
    # The static base island keeps the committed 512 art byte for byte (the glow pulses on
    # the overlay primitive, not in the baked texture).
    top_material = materials['island_top_baked']
    top_texture = doc['textures'][top_material['emissiveTexture']['index']]
    top_payload = image_bytes(doc, payload, top_texture['source'])
    assert hashlib.sha256(top_payload).hexdigest() == ISLAND_TOP_SHA256, 'Base island top changed'
    with Image.open(io.BytesIO(top_payload)) as top:
        assert top.size == (512, 512), top.size
    # The pulsing overlay drives colour + alpha from its own RGBA texture and shares the
    # island-top sampler. It sits just above the base surface and keeps the same footprint.
    overlay = materials[OVERLAY_MATERIAL]
    assert overlay.get('alphaMode') == 'BLEND', 'Glow overlay must blend'
    assert overlay.get('emissiveFactor') == [1, 1, 1], overlay.get('emissiveFactor')
    overlay_texture_index = overlay['emissiveTexture']['index']
    pbr = overlay['pbrMetallicRoughness']
    assert pbr.get('baseColorFactor') == [0, 0, 0, 1], pbr.get('baseColorFactor')
    assert pbr['baseColorTexture']['index'] == overlay_texture_index, 'Overlay alpha source'
    overlay_texture = doc['textures'][overlay_texture_index]
    top_sampler = doc['textures'][top_material['emissiveTexture']['index']].get('sampler', 0)
    assert overlay_texture.get('sampler', 0) == top_sampler, 'Overlay sampler must match island top'
    overlay_bytes = image_bytes(doc, payload, overlay_texture['source'])
    with Image.open(io.BytesIO(overlay_bytes)) as overlay_image:
        assert overlay_image.size == (512, 512), overlay_image.size
        assert overlay_image.mode in ('RGBA', 'P'), overlay_image.mode
        alpha = overlay_image.convert('RGBA').getchannel('A')
        assert alpha.getextrema() == (0, 255), 'Overlay needs a binary alpha mask'
    matrix = node_matrix(island)
    base_values, base_points = position_accessor(doc, payload, 0, island_primitives)
    overlay_values, overlay_points = position_accessor(doc, payload, 3, island_primitives)
    assert len(base_points) == len(overlay_points), 'Overlay must duplicate the top vertices'
    base_world = [world_point(matrix, point) for point in base_points]
    overlay_world = [world_point(matrix, point) for point in overlay_points]
    for axis in (0, 2):
        base_low = min(point[axis] for point in base_world)
        base_high = max(point[axis] for point in base_world)
        overlay_low = min(point[axis] for point in overlay_world)
        overlay_high = max(point[axis] for point in overlay_world)
        assert abs(base_low - overlay_low) < 1e-6 and abs(base_high - overlay_high) < 1e-6, \
            'Overlay footprint changed'
    offset = min(point[1] for point in overlay_world) - min(point[1] for point in base_world)
    assert OVERLAY_OFFSET_RANGE[0] <= offset <= OVERLAY_OFFSET_RANGE[1], \
        f'Overlay must sit 0.0004-0.001 above the base, got {offset}'
    print(f'OK: {len(raw)} bytes, {len(names)} nodes, 6 group pivots, 14 independent cubes, '
          f'{len(materials)} materials, {len(island_primitives)} island primitives '
          f'(overlay material={OVERLAY_MATERIAL}, primitive index=3), overlay BLEND '
          f'alpha 0..255, +Y offset {offset:.4f}, base island top byte-identical, '
          f'masked upper cliff, transparent cliff foot, V clamp')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('glb', type=Path)
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    raw = args.glb.read_bytes()
    inspect_glb(raw)
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            packaged = apk.read('assets/digifarm/3d/digi_farm_3d.glb')
        assert packaged == raw, 'APK contains a different GLB'
        print('OK: APK embeds the exact validated GLB')


if __name__ == '__main__':
    main()
