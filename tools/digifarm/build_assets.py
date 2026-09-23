"""Build the single-island Digifarm GLB from the original FBX.

Run with Blender: blender -b --python build_assets.py -- --source-root DIR
--output-dir DIR. The source archives are never modified. Runtime is Y-up;
the grass is at y=0 and the cliff fades away before its lower edge.
"""

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix, Vector


def emission_mat(name, image_path):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.node_tree.nodes.clear()
    output = mat.node_tree.nodes.new('ShaderNodeOutputMaterial')
    emission = mat.node_tree.nodes.new('ShaderNodeEmission')
    texture = mat.node_tree.nodes.new('ShaderNodeTexImage')
    texture.image = bpy.data.images.load(str(image_path), check_existing=True)
    mat.node_tree.links.new(texture.outputs['Color'], emission.inputs['Color'])
    mat.node_tree.links.new(emission.outputs[0], output.inputs['Surface'])
    if name == 'island_side_baked':
        mat.surface_render_method = 'DITHERED'
        mat.blend_method = 'BLEND'
    return mat


def ordered_outer_boundary(bm, world):
    """The grass has one authored outer loop after duplicate vertices are welded."""
    boundary = [edge for edge in bm.edges if len(edge.link_faces) == 1]
    adjacency = {}
    for edge in boundary:
        for vertex in edge.verts:
            adjacency.setdefault(vertex, []).append(edge)
    if not boundary or any(len(edges) != 2 for edges in adjacency.values()):
        raise RuntimeError('Island grass does not have one simple outer boundary')
    first = boundary[0].verts[0]
    loop = [first]
    previous = None
    current = first
    while True:
        edge = next(edge for edge in adjacency[current] if edge is not previous)
        following = edge.other_vert(current)
        if following is first:
            break
        if following in loop or len(loop) > len(boundary):
            raise RuntimeError('Island grass boundary intersects itself')
        loop.append(following)
        previous, current = edge, following
    if len(loop) != len(boundary):
        raise RuntimeError('Island grass has more than one boundary loop')
    area = sum(
        world[a].x * world[b].y - world[b].x * world[a].y
        for a, b in zip(loop, loop[1:] + loop[:1])
    )
    if area < 0:
        loop.reverse()
    return loop


def build_island(o, tex_dir):
    """Keep the grass and build one continuous cliff around its full outline.

    The FBX side is incomplete behind the island and has a faint foot rim.
    The last rings of this wall use fully transparent texels, so no lower
    outline or cap is visible against the app's dark background.
    """
    mesh = o.data
    while mesh.uv_layers:
        mesh.uv_layers.remove(mesh.uv_layers[0])
    mesh.uv_layers.new(name='UVMap')
    mesh.materials.clear()
    mesh.materials.append(emission_mat('island_top_baked', tex_dir / 'island_top.png'))
    mesh.materials.append(emission_mat('island_side_baked', tex_dir / 'island_side.png'))

    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=1e-4)
    bm.normal_update()
    mw = o.matrix_world.copy()
    inv = mw.inverted()
    normal_matrix = inv.transposed().to_3x3()
    grass = [f for f in bm.faces if (normal_matrix @ f.normal).normalized().z > 0.95]
    if len(grass) < 100:
        bm.free()
        raise RuntimeError('Island grass surface was not found')
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if f not in grass], context='FACES')
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context='VERTS')
    world = {v: mw @ v.co for v in bm.verts}
    loop = ordered_outer_boundary(bm, world)
    top_z = max(point.z for point in world.values())
    if max(abs(world[v].z - top_z) for v in loop) > 1e-3:
        bm.free()
        raise RuntimeError('Island grass rim is not level')

    xs = [point.x for point in world.values()]
    ys = [point.y for point in world.values()]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    uv = bm.loops.layers.uv.active
    for face in bm.faces:
        face.material_index = 0
        for face_loop in face.loops:
            point = world[face_loop.vert]
            face_loop[uv].uv = (
                (point.x - minx) / (maxx - minx),
                1 - (point.y - miny) / (maxy - miny),
            )

    lengths = [0.0]
    for a, b in zip(loop, loop[1:] + loop[:1]):
        lengths.append(lengths[-1] + (world[b] - world[a]).length)
    perimeter = lengths[-1]
    depth = 0.36
    # (fraction of depth, horizontal offset towards the island center).
    # The rim keeps the authored contour; the lower wall tapers softly.
    profile = [(0.0, 0.0), (0.08, -0.004), (0.25, 0.008),
               (0.48, 0.027), (0.70, 0.060), (0.88, 0.105), (1.0, 0.14)]
    rings = [loop]
    for fraction, inset in profile[1:]:
        ring = []
        for vertex in loop:
            point = world[vertex]
            radial = Vector((point.x - cx, point.y - cy))
            radial.normalize()
            p = Vector((point.x - inset * radial.x,
                        point.y - inset * radial.y,
                        top_z - depth * fraction))
            new_vertex = bm.verts.new(inv @ p)
            world[new_vertex] = p
            ring.append(new_vertex)
        rings.append(ring)

    n = len(loop)
    for ring_index in range(len(rings) - 1):
        upper, lower = rings[ring_index], rings[ring_index + 1]
        upper_depth, lower_depth = profile[ring_index][0], profile[ring_index + 1][0]
        for i in range(n):
            j = (i + 1) % n
            face = bm.faces.new((upper[i], lower[i], lower[j], upper[j]))
            face.material_index = 1
            mapping = {
                upper[i]: (lengths[i] / perimeter, 1 - upper_depth),
                lower[i]: (lengths[i] / perimeter, 1 - lower_depth),
                lower[j]: (lengths[i + 1] / perimeter, 1 - lower_depth),
                upper[j]: (lengths[i + 1] / perimeter, 1 - upper_depth),
            }
            for face_loop in face.loops:
                face_loop[uv].uv = mapping[face_loop.vert]
    bm.normal_update()
    bm.to_mesh(mesh)
    mesh.update()
    bm.free()
    print('Built complete island:', len(grass), 'grass faces,', n, 'rim vertices,',
          (len(profile) - 1) * n, 'cliff faces')


def block_object(name, spec, materials, parent=None):
    """Build one textured cube with its own transform node."""
    mesh = bpy.data.meshes.new(name + 'Mesh')
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    x, y, z, sx, sy, sz, lit = spec
    obj.location = (x, y, z)
    for material in materials:
        mesh.materials.append(material)
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new('UVMap')
    created = bmesh.ops.create_cube(bm, size=1.0)
    for vertex in created['verts']:
        vertex.co = (vertex.co.x * sx, vertex.co.y * sy, vertex.co.z * sz)
    bm.normal_update()
    for face in bm.faces:
        face.material_index = 1 if lit and len(materials) > 1 else 0
        for face_loop in face.loops:
            p = face_loop.vert.co
            if abs(face.normal.z) > .5:
                u, v = p.x / sx + .5, p.y / sy + .5
            elif abs(face.normal.y) > .5:
                u, v = p.x / sx + .5, p.z / sz + .5
            else:
                u, v = p.y / sy + .5, p.z / sz + .5
            face_loop[uv].uv = (u, v)
    bm.to_mesh(mesh)
    mesh.update()
    bm.free()
    return obj


def add_voxel_fragments(tex_dir):
    """Keep 14 separately movable cubes under six moving group pivots."""
    root = bpy.data.objects.new('VoxelFragments', None)
    bpy.context.collection.objects.link(root)
    dark = emission_mat('voxel_block_dark_baked', tex_dir / 'voxel_block_dark.png')
    lit = emission_mat('voxel_block_lit_baked', tex_dir / 'voxel_block_lit.png')
    near_groups = (
        ((-.91, .80, -.09, .15, .15, .15, False),
         (-.91, .80, .07, .10, .10, .10, True),
         (-.73, .91, .01, .09, .09, .09, False)),
        ((.87, .80, -.09, .15, .15, .15, False),
         (.87, .80, .07, .10, .10, .10, True),
         (1.02, .72, .04, .08, .08, .08, False)),
        ((-1.12, .18, -.04, .12, .12, .12, False),
         (-1.12, .18, .09, .08, .08, .08, True)),
        ((1.12, .12, -.04, .12, .12, .12, False),
         (1.12, .12, .09, .08, .08, .08, True)),
        ((-.87, -.78, -.10, .11, .11, .11, False),
         (-1.00, -.73, .02, .08, .08, .08, True)),
        ((.86, -.79, -.10, .11, .11, .11, False),
         (.99, -.73, .02, .08, .08, .08, True)),
    )
    for index, group in enumerate(near_groups, 1):
        origin = tuple(sum(spec[axis] for spec in group) / len(group)
                       for axis in range(3))
        pivot = bpy.data.objects.new(f'VoxelFragmentsNear{index:02}', None)
        bpy.context.collection.objects.link(pivot)
        pivot.parent = root
        pivot.location = origin
        for block_index, (x, y, z, sx, sy, sz, light) in enumerate(group, 1):
            spec = (x - origin[0], y - origin[1], z - origin[2],
                    sx, sy, sz, light)
            block_object(
                f'VoxelBlockNear{index:02}_{block_index:02}',
                spec, [dark, lit], pivot,
            )

    print('Built', sum(map(len, near_groups)), 'original floating blocks in',
          len(near_groups), 'animated groups')


def finalize_glb(path):
    """Make the cliff texture drive glTF alpha, then validate the GLB header."""
    import struct
    raw = bytearray(path.read_bytes())
    json_len = struct.unpack_from('<I', raw, 12)[0]
    doc = json.loads(bytes(raw[20:20 + json_len]))
    for material in doc.get('materials', []):
        material.pop('doubleSided', None)
        if material.get('name') == 'island_side_baked':
            texture = material.get('emissiveTexture')
            if texture is None:
                raise RuntimeError('Cliff emissive texture missing')
            # Blender exports the shared sampler with glTF's default REPEAT.
            # At V=1 the GPU then blends the transparent foot with the pale
            # V=0 rim, producing a bright hairline around the island bottom.
            side_texture = doc['textures'][texture['index']]
            source_sampler = doc['samplers'][side_texture['sampler']]
            side_sampler = dict(source_sampler, wrapS=10497, wrapT=33071)
            doc['samplers'].append(side_sampler)
            side_texture['sampler'] = len(doc['samplers']) - 1
            pbr = material.setdefault('pbrMetallicRoughness', {})
            pbr['baseColorTexture'] = {'index': texture['index']}
            pbr['baseColorFactor'] = [1, 1, 1, 1]
            material['alphaMode'] = 'BLEND'
    encoded = json.dumps(doc, separators=(',', ':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    offset = 20 + json_len
    bin_len = struct.unpack_from('<I', raw, offset)[0]
    payload = bytes(raw[offset + 8:offset + 8 + bin_len])
    out = bytearray(raw[:12])
    out += struct.pack('<I', len(encoded)) + b'JSON' + encoded
    out += struct.pack('<I', len(payload)) + b'BIN\x00' + payload
    struct.pack_into('<I', out, 8, len(out))
    if out[:4] != b'glTF' or struct.unpack_from('<I', out, 4)[0] != 2:
        raise RuntimeError('Invalid GLB header')
    path.write_bytes(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-root', required=True)
    parser.add_argument('--output-dir', required=True)
    parser.add_argument('--preview')
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
    source = next(Path(args.source_root).rglob('farm_01.fbx'))
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=str(source), use_image_search=True)
    meshes = [(o, o.matrix_world.copy()) for o in bpy.context.scene.objects if o.type == 'MESH']
    canonical = Matrix.Scale(1.2, 4) @ Matrix.Rotation(-math.pi / 2, 4, 'Z')
    for o, world in meshes:
        o.parent = None
        o.matrix_world = canonical @ world
    for o in list(bpy.context.scene.objects):
        if o.type != 'MESH' or o.name != 'Island':
            bpy.data.objects.remove(o, do_unlink=True)
    bpy.context.view_layer.update()
    island = bpy.data.objects['Island']
    top = max((island.matrix_world @ v.co).z for v in island.data.vertices)
    island.location.z -= top
    build_island(island, source.parent)
    add_voxel_fragments(source.parent)
    bpy.context.view_layer.update()
    glb = output / 'digi_farm_3d.glb'
    bpy.ops.export_scene.gltf(filepath=str(glb), export_format='GLB',
                              export_animations=False, export_cameras=False,
                              export_lights=False)
    finalize_glb(glb)
    manifest = dict(
        schema=1, mapId='digi_farm_3d', mapVersion=1,
        asset='digifarm/3d/digi_farm_3d.glb', source=source.name,
        sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        coordinateSystem='canonical-y-up-ground-zero', groundHeight=0,
        playableBounds=dict(min=[-.68, -.43], max=[.68, .43]),
        safeSpawns=[[x, z] for z in [-.28, 0, .28]
                    for x in [-.48, -.16, .16, .48]],
        staticNodes=sorted(o.name for o in bpy.context.scene.objects),
        facilityNodes=[],
        notes='Single jade island with violet wire grid on top only, muted mauve cliff fading into a deep purple backdrop, and floating original voxel blocks; no stadium islets or sea.'
    )
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    if args.preview:
        bpy.ops.object.camera_add(location=(0, -3.4, 2.8))
        camera = bpy.context.object
        camera.rotation_euler = (Vector((0, 0, -0.05)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
        camera.data.type = 'ORTHO'
        camera.data.ortho_scale = 2.65
        scene = bpy.context.scene
        scene.camera = camera
        scene.render.engine = 'CYCLES'
        scene.cycles.samples = 8
        scene.render.resolution_x = 800
        scene.render.resolution_y = 700
        scene.render.resolution_percentage = 100
        scene.view_settings.view_transform = 'Standard'
        scene.world = bpy.data.worlds.new('PreviewVoid')
        scene.world.use_nodes = True
        scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.009, .0065, .020, 1)
        scene.render.filepath = args.preview
        bpy.ops.render.render(write_still=True)


if __name__ == '__main__':
    main()
