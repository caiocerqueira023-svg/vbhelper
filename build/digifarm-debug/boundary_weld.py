import bpy,bmesh
from mathutils import Vector
p=r'C:\Users\julye\AppData\Local\Temp\vbhelper-digifarm-asset-extract-6a790d63968244d78893583500d2a6ab\Mobile_-_Digimon_Links_-_Maps_-_Digi-Farm\Digi-Farm\farm_01.fbx'
bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.fbx(filepath=p)
for name in ['Island','IlandColosseum.001']:
 o=bpy.data.objects[name]; bm=bmesh.new(); bm.from_mesh(o.data); bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-4); bm.normal_update(); rem={e for e in bm.edges if len(e.link_faces)==1}; gs=[]
 while rem:
  e=rem.pop(); comp={e}; vs=set(e.verts); ch=True
  while ch:
   ch=False
   for x in list(rem):
    if x.verts[0] in vs or x.verts[1] in vs: rem.remove(x); comp.add(x); vs|=set(x.verts); ch=True
  pts=[o.matrix_world@v.co for v in vs]; print(name,'group',len(vs),'z',round(min(p.z for p in pts),3),round(max(p.z for p in pts),3),'xy',round(min(p.x for p in pts),3),round(max(p.x for p in pts),3),round(min(p.y for p in pts),3),round(max(p.y for p in pts),3))
 bm.free()
