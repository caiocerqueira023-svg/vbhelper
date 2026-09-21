import bpy,bmesh,json,math
from mathutils import Matrix
src=r'C:\Users\julye\AppData\Local\Temp\vbhelper-classic-src\Digi-Farm\farm_01.fbx'
bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.fbx(filepath=src,use_image_search=True)
canonical=Matrix.Scale(1.2,4)@Matrix.Rotation(-math.pi/2,4,'Z')
for o in [x for x in bpy.context.scene.objects if x.type=='MESH']:
    w=o.matrix_world.copy();o.parent=None;o.matrix_world=canonical@w
for o in list(bpy.context.scene.objects):
    if o.type!='MESH' or o.name!='Island': bpy.data.objects.remove(o,do_unlink=True)
o=bpy.data.objects['Island']; bm=bmesh.new();bm.from_mesh(o.data);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-4);bmesh.ops.delete(bm,geom=[f for f in bm.faces if f.calc_area()<1e-6],context='FACES');bmesh.ops.delete(bm,geom=[v for v in bm.verts if not v.link_faces],context='VERTS');bm.normal_update();mw=o.matrix_world.copy();pts={v:mw@v.co for v in bm.verts};edges=[e for e in bm.edges if len(e.link_faces)==1];adj={v:[] for e in edges for v in e.verts}
for e in edges:
    for v in e.verts:adj[v].append(e)
seed=edges[0];loop=[seed.verts[0]];prev=seed;cur=seed.verts[1]
while cur is not loop[0]:
    loop.append(cur);e=[x for x in adj[cur] if x is not prev][0];prev,cur=e,(e.verts[0] if e.verts[1] is cur else e.verts[1])
top=max(p.z for p in pts.values());toploop=[{'x':pts[v].x,'y':pts[v].y,'z':pts[v].z} for v in loop if pts[v].z>=top-.018]
open(r'C:\Users\julye\IdeaProjects\vbhelper\build\digifarm-debug\top_boundary.json','w').write(json.dumps(toploop))
print('top points',len(toploop))
