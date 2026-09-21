import bpy, bmesh
p=r'app/src/main/assets/digifarm/3d/digi_farm_3d.glb'
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=p)
for o in bpy.context.scene.objects:
 if o.type!='MESH': continue
 bm=bmesh.new(); bm.from_mesh(o.data); bm.edges.ensure_lookup_table(); bm.faces.ensure_lookup_table()
 bd=[e for e in bm.edges if len(e.link_faces)==1]
 print(o.name,'verts',len(bm.verts),'faces',len(bm.faces),'boundary',len(bd),'materials',[m.name for m in o.data.materials])
 bm.free()
