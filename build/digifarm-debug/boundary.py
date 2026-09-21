import bpy,bmesh,math
from mathutils import Vector
source=r'C:\Users\julye\AppData\Local\Temp\vbhelper-digifarm-asset-extract-6a790d63968244d78893583500d2a6ab\Mobile_-_Digimon_Links_-_Maps_-_Digi-Farm\Digi-Farm\farm_01.fbx'
bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.fbx(filepath=source)
for name in ['Island','IlandColosseum.001']:
 o=bpy.data.objects.get(name); bm=bmesh.new(); bm.from_mesh(o.data); bm.normal_update();
 rem={e for e in bm.edges if len(e.link_faces)==1}; gs=[]
 while rem:
  e=rem.pop(); comp={e}; vs=set(e.verts); changed=True
  while changed:
   changed=False
   for x in list(rem):
    if x.verts[0] in vs or x.verts[1] in vs: rem.remove(x); comp.add(x); vs|=set(x.verts); changed=True
  pts=[o.matrix_world@v.co for v in vs]
  zs=[p.z for p in pts]; print(name,'boundary group',len(vs),'bounds',tuple(round(min(p[i] for p in pts),3) for i in range(3)),tuple(round(max(p[i] for p in pts),3) for i in range(3)),'zmean',round(sum(zs)/len(zs),3),'zrange',round(min(zs),3),round(max(zs),3))
 bm.free()
