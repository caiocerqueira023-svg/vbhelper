import bpy,bmesh,math
from mathutils import Matrix,Vector
p=r'C:\Users\julye\AppData\Local\Temp\vbhelper-classic-src\Digi-Farm\farm_01.fbx'; bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.fbx(filepath=p)
o=bpy.data.objects['Island']; world=o.matrix_world.copy(); o.parent=None; o.matrix_world=Matrix.Scale(1.2,4)@world
bm=bmesh.new(); bm.from_mesh(o.data); bm.normal_update(); mw=o.matrix_world; pts={v:mw@v.co for v in bm.verts}; top=max(p.z for p in pts.values());
# face classes and boundary verts
edgeverts={v for e in bm.edges if len(e.link_faces)==1 for v in e.verts}
topv=[v for v in edgeverts if pts[v].z>top-.02]
sidev=[v for v in bm.verts if pts[v].z>top-.08 and any(((mw.inverted().transposed().to_3x3()@f.normal).normalized().z)<.5 for f in v.link_faces)]
print('topv',len(topv),'sidev',len(sidev))
vals=[]
for v in topv:
 p0=pts[v]; d=min((pts[w]-p0).length for w in sidev if w is not v)
 vals.append(d)
print('nearest',min(vals),max(vals),sorted(vals)[:20],sorted(vals)[-20:])
bm.free()
