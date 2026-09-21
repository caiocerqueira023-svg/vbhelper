import bpy,bmesh,math
from mathutils import Matrix,Vector
p=r'C:\Users\julye\AppData\Local\Temp\vbhelper-classic-src\Digi-Farm\farm_01.fbx'
bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.fbx(filepath=p)
o=bpy.data.objects['Island']; world=o.matrix_world.copy(); o.parent=None; o.matrix_world=Matrix.Scale(1.2,4)@world
bm=bmesh.new(); bm.from_mesh(o.data); bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-4); bm.normal_update(); pts={v:o.matrix_world@v.co for v in bm.verts}; top=max(p.z for p in pts.values()); xmin=min(p.x for p in pts.values())
print('bounds',xmin,max(p.x for p in pts.values()),top,min(p.z for p in pts.values()))
for e in bm.edges:
 if len(e.link_faces)!=1: continue
 a,b=[pts[v] for v in e.verts]
 if max(a.z,b.z)>top-.02 and (a.x < xmin+.25 or b.x<xmin+.25): print('edge',tuple(round(x,3) for x in a),tuple(round(x,3) for x in b),'len',round((a-b).length,3))
bm.free()
