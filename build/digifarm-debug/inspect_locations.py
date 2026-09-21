import bpy
from mathutils import Vector
p=r'app/src/main/assets/digifarm/3d/digi_farm_3d.glb'
bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.gltf(filepath=p)
for o in bpy.context.scene.objects:
 if o.type=='MESH':
  pts=[o.matrix_world@Vector(v) for v in o.bound_box]
  mn=[min(q[i] for q in pts) for i in range(3)]; mx=[max(q[i] for q in pts) for i in range(3)]
  print(o.name,'loc',tuple(round(x,3) for x in o.location),'bounds',tuple(round(x,3) for x in mn),tuple(round(x,3) for x in mx))
