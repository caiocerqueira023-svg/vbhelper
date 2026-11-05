import json, struct
p = r"app/src/main/assets/Arena/Radar/radar_grid.glb"
data = open(p,'rb').read()
off = 12; js=None
length = struct.unpack_from('<III', data, 0)[2]
while off < length:
    clen, ctype = struct.unpack_from('<II', data, off)
    if ctype == 0x4E4F534A: js = json.loads(data[off+8:off+8+clen].decode('utf-8'))
    off += 8 + clen
m = js['meshes'][0]
for i,pr in enumerate(m['primitives']):
    acc = js['accessors'][pr['attributes']['POSITION']]
    print('prim', i, 'material', js['materials'][pr['material']]['name'], 'min', acc.get('min'), 'max', acc.get('max'), 'count', acc.get('count'))
