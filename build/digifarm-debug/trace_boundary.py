import bpy,bmesh,math
from mathutils import Matrix,Vector
src=r'C:\Users\julye\AppData\Local\Temp\vbhelper-classic-src\Digi-Farm\farm_01.fbx'
bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.fbx(filepath=src,use_image_search=True)
canonical=Matrix.Scale(1.2,4)@Matrix.Rotation(-math.pi/2,4,'Z')
for o in [x for x in bpy.context.scene.objects if x.type=='MESH']:
 w=o.matrix_world.copy(); o.parent=None; o.matrix_world=canonical@w
for o in list(bpy.context.scene.objects):
 if o.type!='MESH' or o.name!='Island': bpy.data.objects.remove(o,do_unlink=True)
o=bpy.data.objects['Island']; bm=bmesh.new(); bm.from_mesh(o.data); bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-4); bm.normal_update(); mw=o.matrix_world.copy(); pts={v:mw@v.co for v in bm.verts}
es=[e for e in bm.edges if len(e.link_faces)==1]; adj={v:[] for e in es for v in e.verts}
for e in es:
 for v in e.verts: adj[v].append(e)
print('degrees',sorted({len(v) for v in adj.values()}),{k:sum(len(v)==k for v in adj.values()) for k in range(1,5)})
seed=es[0]; cur=seed.verts[0]; prev=None; seq=[]; e=seed
for i in range(len(es)+2):
 seq.append(cur); opts=[x for x in adj[cur] if x is not e]
 if not opts: break
 ne=opts[0]
 nxt=ne.verts[0] if ne.verts[1] is cur else ne.verts[1]
 prev,e,cur=e,ne,nxt
 if cur is seq[0]: break
print('seq len',len(seq))
top=max(p.z for p in pts.values());
for i,v in enumerate(seq):
 p=pts[v]; q=pts[seq[(i+1)%len(seq)]]
 if i%5==0 or (p.z>top-.03)!=(q.z>top-.03): print(i,tuple(round(a,3) for a in p), '->',tuple(round(a,3) for a in q),'zclass',round(p.z,3),round(q.z,3))
bm.free()
