import bpy, json
from mathutils import Vector
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=r'C:\Users\julye\AppData\Local\Temp\vbhelper-digifarm-asset-extract-6a790d63968244d78893583500d2a6ab\Mobile_-_Digimon_Links_-_Maps_-_Digi-Farm\Digi-Farm\farm_01.fbx')
for o in bpy.context.scene.objects:
 if o.type == 'MESH':
  p=[o.matrix_world @ Vector(v) for v in o.bound_box]
  print('MESH',o.name,'parent',o.parent.name if o.parent else None,'bounds',[[round(f(v[i] for v in p),4) for i in range(3)] for f in [min,max]],'mats', [m.name for m in o.data.materials])
for m in bpy.data.materials:
 print('MAT',m.name,[(n.type,n.image.filepath if n.type=='TEX_IMAGE' and n.image else '') for n in m.node_tree.nodes] if m.use_nodes else '')
