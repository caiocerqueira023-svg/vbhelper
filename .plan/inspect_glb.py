import json, struct, sys
p = r"app/src/main/assets/Arena/Radar/radar_grid.glb"
data = open(p,'rb').read()
magic, version, length = struct.unpack_from('<III', data, 0)
off = 12
chunks = []
while off < length:
    clen, ctype = struct.unpack_from('<II', data, off)
    chunks.append((ctype, data[off+8:off+8+clen]))
    off += 8 + clen
js = None
for ctype, payload in chunks:
    if ctype == 0x4E4F534A:
        js = json.loads(payload.decode('utf-8'))
print("nodes:")
for i,n in enumerate(js.get('nodes',[])):
    print(i, n.get('name'), 'mesh=',n.get('mesh'), 'children=',n.get('children'), 't=',n.get('translation'), 'scale=',n.get('scale'))
print("\nmeshes:")
for i,m in enumerate(js.get('meshes',[])):
    prims = m.get('primitives',[])
    print(i, m.get('name'), 'prims=',len(prims), 'materials=',[pr.get('material') for pr in prims])
print("\nmaterials:")
for i,m in enumerate(js.get('materials',[])):
    print(i, m.get('name'), list(m.keys()))
print("\nscene nodes:", js.get('scenes'))
