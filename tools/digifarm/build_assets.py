"""Canonical Digifarm scene. Blender -b --python build_assets.py -- --source-root DIR --output-dir DIR.
Original archives remain unchanged. Runtime is Y-up, island top y=0, width about 2 units.
"""
import argparse, hashlib, json, math, sys
from pathlib import Path
import bpy
from mathutils import Matrix, Vector


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--source-root', required=True)
    p.add_argument('--output-dir', required=True)
    p.add_argument('--preview')
    a = p.parse_args(sys.argv[sys.argv.index('--') + 1:])
    source = next(Path(a.source_root).rglob('farm_01.fbx'))
    out = Path(a.output_dir); out.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=str(source), use_image_search=True)
    # Snapshot BEFORE deleting parents; baking local transforms is insufficient.
    meshes = [(o, o.matrix_world.copy()) for o in bpy.context.scene.objects if o.type == 'MESH']
    keep = {'Island', 'Sea', 'IlandColosseum.001', 'FarmColosseum_O.001', 'FarmColosseum_C'}
    canonical = Matrix.Scale(1.2, 4) @ Matrix.Rotation(-math.pi / 2, 4, 'Z')
    for o, world in meshes:
        o.parent = None
        o.matrix_world = canonical @ world
    for o in list(bpy.context.scene.objects):
        if o.type != 'MESH' or o.name not in keep:
            bpy.data.objects.remove(o, do_unlink=True)
    bpy.context.view_layer.update()
    island = bpy.data.objects['Island']
    top = max((island.matrix_world @ Vector(c)).z for c in island.bound_box)
    for o in bpy.context.scene.objects:
        o.location.z -= top
    # Open and closed colosseums are overlapping variants, not collision shells.
    # Place the closed variant to the left on a second copy of the original islet.
    closed = bpy.data.objects['FarmColosseum_C']
    closed.location.x -= .58
    islet = bpy.data.objects['IlandColosseum.001']
    extra = islet.copy(); extra.data = islet.data.copy(); bpy.context.collection.objects.link(extra)
    extra.location.x -= .58
    for o in bpy.context.scene.objects:
        old = o.data.materials[0]
        image_name = ('stadium_close.png' if o == closed else 'stadium.png') if 'Colosseum_' in o.name else 'farm_base_tex01.png'
        mat = bpy.data.materials.new(o.name + '_baked_color'); mat.use_nodes = True
        mat.node_tree.nodes.clear()
        output = mat.node_tree.nodes.new('ShaderNodeOutputMaterial')
        emission = mat.node_tree.nodes.new('ShaderNodeEmission')
        mat.node_tree.links.new(emission.outputs[0], output.inputs['Surface'])
        if o.name == 'Sea':
            emission.inputs['Color'].default_value = (.12, .86, .88, 1)
        else:
            tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
            tex.image = bpy.data.images.load(str(source.parent / image_name), check_existing=True)
            mat.node_tree.links.new(tex.outputs['Color'], emission.inputs['Color'])
        o.data.materials.clear(); o.data.materials.append(mat)
    bpy.context.view_layer.update()
    bpy.ops.export_scene.gltf(filepath=str(out/'digi_farm_3d.glb'), export_format='GLB', export_animations=False, export_cameras=False, export_lights=False)
    manifest = dict(schema=1, mapId='digi_farm_3d', mapVersion=1,
        asset='digifarm/3d/digi_farm_3d.glb', source=source.name,
        sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        coordinateSystem='canonical-y-up-ground-zero', groundHeight=0,
        playableBounds=dict(min=[-.68, -.43], max=[.68, .43]),
        safeSpawns=[[x,z] for z in [-.28,0,.28] for x in [-.48,-.16,.16,.48]],
        staticNodes=[o.name for o in bpy.context.scene.objects], facilityNodes=[],
        notes='Unlit baked-color map; closed and open stadium variants separated; no auto-placed facilities.')
    (out/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    if a.preview:
        bpy.ops.object.camera_add(location=(0, -3.4, 2.8))
        cam = bpy.context.object
        cam.rotation_euler = (Vector((0,.22,-.05))-cam.location).to_track_quat('-Z','Y').to_euler()
        cam.data.type='ORTHO'; cam.data.ortho_scale=2.65
        scene=bpy.context.scene; scene.camera=cam
        scene.render.engine='CYCLES'; scene.cycles.samples=8
        scene.render.resolution_x=800; scene.render.resolution_y=700; scene.render.resolution_percentage=100
        scene.view_settings.view_transform='Standard'
        scene.render.filepath=a.preview
        bpy.ops.render.render(write_still=True)

if __name__ == '__main__': main()
