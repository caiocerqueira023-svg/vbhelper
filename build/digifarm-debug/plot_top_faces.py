import bpy,bmesh,json
from mathutils import Matrix
src=r'C:\Users\julye\AppData\Local\Temp\vbhelper-classic-src\Digi-Farm\farm_01.fbx'
bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.import_scene.fbx(filepath=src,use_image_search=True)
canonical=Matrix.Scale(1.2,4)@Matrix.Rotation(-3.1415926535/2,4,'Z')
for o in [x for x in bpy.context.scene.objects if x.type=='MESH']:
 w=o.matrix_world.copy();o.parent=None;o.matrix_world=canonical@w
for o in list(bpy.context.scene.objects):
 if o.type!='MESH' or o.name!='Island':bpy.data.objects.remove(o,do_unlink=True)
o=bpy.data.objects['Island'];bm=bmesh.new();bm.from_mesh(o.data);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-4);bmesh.ops.delete(bm,geom=[f for f in bm.faces if f.calc_area()<1e-6],context='FACES');bmesh.ops.delete(bm,geom=[v for v in bm.verts if not v.link_faces],context='VERTS');bm.normal_update();mw=o.matrix_world.copy();
faces=[]
for f in bm.faces:
 w=[mw@v.co for v in f.verts]; n=f.normal
 if n.z>0.7: faces.append({'x':sum(p.x for p in w)/len(w),'y':sum(p.y for p in w)/len(w)})
open(r'C:\Users\julye\IdeaProjects\vbhelper\build\digifarm-debug\top_faces.json','w').write(json.dumps(faces));print('faces',len(faces))
