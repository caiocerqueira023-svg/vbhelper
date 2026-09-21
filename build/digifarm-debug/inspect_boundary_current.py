import bpy, bmesh, math
from mathutils import Matrix, Vector

src = r'C:\Users\julye\AppData\Local\Temp\vbhelper-classic-src\Digi-Farm\farm_01.fbx'
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=src, use_image_search=True)
meshes=[(o,o.matrix_world.copy()) for o in bpy.context.scene.objects if o.type=='MESH']
canonical=Matrix.Scale(1.2,4) @ Matrix.Rotation(-math.pi/2,4,'Z')
for o,w in meshes:
    o.parent=None; o.matrix_world=canonical@w
for o in list(bpy.context.scene.objects):
    if o.type!='MESH' or o.name!='Island': bpy.data.objects.remove(o,do_unlink=True)
o=bpy.data.objects['Island']
bm=bmesh.new(); bm.from_mesh(o.data)
bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=1e-4)
bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.calc_area()<1e-6], context='FACES')
bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context='VERTS')
bm.normal_update(); bm.verts.ensure_lookup_table(); bm.edges.ensure_lookup_table()
mw=o.matrix_world.copy(); pts={v:mw@v.co for v in bm.verts}; top=max(p.z for p in pts.values()); foot=min(p.z for p in pts.values())
print('bounds',tuple(round(x,4) for x in [min(p.x for p in pts.values()),max(p.x for p in pts.values()),min(p.y for p in pts.values()),max(p.y for p in pts.values()),foot,top]))
edges=[e for e in bm.edges if len(e.link_faces)==1]
print('boundary edges',len(edges))
seen=set(); groups=[]
for seed in edges:
    if seed in seen: continue
    seen.add(seed); todo=list(seed.verts); vs=set(seed.verts); es=[seed]
    while todo:
        v=todo.pop()
        for e in edges:
            if e in seen or v not in e.verts: continue
            seen.add(e); es.append(e)
            for vv in e.verts:
                if vv not in vs: vs.add(vv); todo.append(vv)
    groups.append((es,vs))
for i,(es,vs) in enumerate(groups):
    ps=[pts[v] for v in vs]
    print('group',i,'edges',len(es),'verts',len(vs),'z',round(min(p.z for p in ps),4),round(max(p.z for p in ps),4),'xy',tuple(round(x,4) for x in [min(p.x for p in ps),max(p.x for p in ps),min(p.y for p in ps),max(p.y for p in ps)]))
    if max(p.z for p in ps)>top-.03:
        for e in es:
            a,b=[pts[v] for v in e.verts]
            if a.z>top-.03 and b.z>top-.03:
                print(' TOPEDGE',tuple(round(q,3) for q in a),tuple(round(q,3) for q in b),'len',round((a-b).length,3))
bm.free()
