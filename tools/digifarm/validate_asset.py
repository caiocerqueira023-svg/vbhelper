"""Validate a built single-island Digifarm GLB (and optionally its APK copy)."""

import argparse
import io
import json
import struct
import zipfile
from pathlib import Path

from PIL import Image


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
    payload = raw[bin_offset + 8:bin_offset + 8 + bin_length]
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
        'island_top_baked', 'island_side_baked',
        'voxel_block_dark_baked', 'voxel_block_lit_baked',
    }, materials.keys()
    for side_name in ('island_side_baked',):
        side = materials[side_name]
        assert side.get('alphaMode') == 'BLEND', f'{side_name} must blend'
        texture_index = side['pbrMetallicRoughness']['baseColorTexture']['index']
        texture = doc['textures'][texture_index]
        sampler = doc['samplers'][texture['sampler']]
        assert sampler.get('wrapT') == 33071, f'{side_name} V must clamp'
        image_index = texture['source']
        view = doc['bufferViews'][doc['images'][image_index]['bufferView']]
        start = view.get('byteOffset', 0)
        image = Image.open(io.BytesIO(payload[start:start + view['byteLength']])).convert('RGBA')
        alpha = image.getchannel('A')
        assert alpha.getextrema() == (0, 255), f'{side_name} needs alpha range'
        assert alpha.crop((0, image.height - 20, image.width, image.height)).getextrema() == (0, 0), f'{side_name} foot must disappear'
    print(f'OK: {len(raw)} bytes, {len(names)} nodes, 6 group pivots, 14 independent cubes, 4 materials, transparent cliff foot, V clamp')


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
