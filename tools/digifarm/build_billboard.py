"""Generate minimal unlit, depth-tested billboard + contact shadow (no Digimon art bundled)."""
import json, math, struct, zlib
from pathlib import Path

def png(size, shadow=False):
 def chunk(t,b): return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b))
 raw=bytearray()
 for y in range(size):
  raw.append(0)
  for x in range(size):
   r=((2*x/(size-1)-1)**2+(2*y/(size-1)-1)**2) if shadow else 0
   raw.extend((0,0,0,int(85*max(0,1-r)**2)) if shadow else (255,255,255,255))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')

def build(path):
 data=bytearray(); views=[]; accessors=[]
 def view(b):
  while len(data)%4: data.append(0)
  i=len(views); views.append(dict(buffer=0,byteOffset=len(data),byteLength=len(b))); data.extend(b); return i
 def acc(values,typ,n,component=5126):
  b=struct.pack('<'+('f' if component==5126 else 'H')*len(values),*values)
  d=dict(bufferView=view(b),componentType=component,count=len(values)//n,type=typ)
  if typ=='VEC3': d.update(min=[min(values[i::n]) for i in range(n)],max=[max(values[i::n]) for i in range(n)])
  accessors.append(d); return len(accessors)-1
 uv=acc([0,1,1,1,1,0,0,0],'VEC2',2); indices=acc([0,1,2,0,2,3],'SCALAR',1,5123)
 meshes=[]
 for i,verts in enumerate([[-.5,0,0,.5,0,0,.5,1,0,-.5,1,0],[-.55,.012,.3,.55,.012,.3,.55,.012,-.3,-.55,.012,-.3]]):
  pos=acc(verts,'VEC3',3)
  meshes.append(dict(primitives=[dict(attributes={'POSITION':pos,'TEXCOORD_0':uv},indices=indices,material=i)]))
 images=[dict(bufferView=view(png(32,i==1)),mimeType='image/png') for i in range(2)]
 mats=[dict(name=n,extensions={'KHR_materials_unlit':{}},doubleSided=True,alphaMode=mode,alphaCutoff=.5,
  pbrMetallicRoughness=dict(baseColorTexture=dict(index=i),metallicFactor=0,roughnessFactor=1)) for i,(n,mode) in enumerate([('sprite','MASK'),('shadow','BLEND')])]
 obj=dict(asset=dict(version='2.0'),extensionsUsed=['KHR_materials_unlit'],scene=0,scenes=[dict(nodes=[0,1])],nodes=[dict(name='sprite',mesh=0),dict(name='shadow',mesh=1)],meshes=meshes,materials=mats,images=images,textures=[dict(source=i,sampler=0) for i in range(2)],samplers=[dict(magFilter=9728,minFilter=9728,wrapS=33071,wrapT=33071)],bufferViews=views,accessors=accessors,buffers=[dict(byteLength=len(data))])
 j=json.dumps(obj,separators=(',',':')).encode(); j+=b' '*((-len(j))%4); data+=b'\0'*((-len(data))%4)
 Path(path).write_bytes(struct.pack('<III',0x46546c67,2,12+8+len(j)+8+len(data))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(data),0x004e4942)+data)
if __name__=='__main__': build('app/src/main/assets/digifarm/3d/resident.glb')
