# -*- coding: utf-8 -*-
"""Convert the provided high-res icon renders to white RGBA PNGs (transparent bg, dark details
knocked out) plus app-ready density sets."""
import os, shutil
from PIL import Image, ImageDraw, ImageFont
import numpy as np

OUT=r"C:\Users\julye\IdeaProjects\vbhelper\analysis\vb-lab-assets\icons\nav-final"
SRC=os.path.join(OUT,"source")
base=r"C:\Users\julye\.dsh\attachments\v1\objects"
files={
 "scan": r"bb\bbba17b32fa67ef7196661e2beebe749cabda9970fd68cf525cfa30900170656",
 "log": r"9e\9e1d142e3ef1a78af695c1d64997b3412e52d1e6b3dafa01362091f3244358a4",
 "digidex": r"58\5809fb6f1b69faf34a320ec32d4407fe26c0a46bce55fcc8dc196c339a672b83",
 "items": r"bd\bddbd43a4e502dff22e4f1dcc922b8dfaae6b673d6b83d5e704ce1c4e71abd95",
 "battle": r"50\50d950572294e3c96e4a984419866990f0af91e482808dec8c84e26b06d88976",
}
for name,rel in files.items():
    shutil.copyfile(os.path.join(base,rel), os.path.join(SRC,name+"_source.png"))
print("sources copied")

def clip01(x): return np.clip(x,0.0,1.0)

def remove_small(mask,min_area=40):
    h,w=mask.shape; seen=np.zeros_like(mask,bool); out=np.zeros_like(mask)
    ys,xs=np.where(mask)
    for sx,sy in zip(xs.tolist(),ys.tolist()):
        if seen[sy,sx]: continue
        stack=[(sx,sy)]; seen[sy,sx]=True; comp=[(sx,sy)]
        while stack:
            x,y=stack.pop()
            for nx,ny in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
                if 0<=nx<w and 0<=ny<h and mask[ny,nx] and not seen[ny,nx]:
                    seen[ny,nx]=True; comp.append((nx,ny)); stack.append((nx,ny))
        if len(comp)>=min_area:
            for cx,cy in comp: out[cy,cx]=True
    return out

def fill_holes(mask):
    """holes fully enclosed by mask (4-connectivity), returned as a boolean hole map"""
    h,w=mask.shape
    outside=np.zeros_like(mask,bool)
    stack=[]
    for x in range(w):
        if not mask[0,x]: outside[0,x]=True; stack.append((x,0))
        if not mask[h-1,x]: outside[h-1,x]=True; stack.append((x,h-1))
    for y in range(h):
        if not mask[y,0] and not outside[y,0]: outside[y,0]=True; stack.append((0,y))
        if not mask[y,w-1] and not outside[y,w-1]: outside[y,w-1]=True; stack.append((w-1,y))
    while stack:
        x,y=stack.pop()
        for nx,ny in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
            if 0<=nx<w and 0<=ny<h and not mask[ny,nx] and not outside[ny,nx]:
                outside[ny,nx]=True; stack.append((nx,ny))
    return ~mask & ~outside

def keep_big(mask, frac=0.02, min_area=60):
    """keep only connected components that are at least `frac` of the largest one"""
    h,w=mask.shape; seen=np.zeros_like(mask,bool); comps=[]
    ys,xs=np.where(mask)
    for sx,sy in zip(xs.tolist(),ys.tolist()):
        if seen[sy,sx]: continue
        stack=[(sx,sy)]; seen[sy,sx]=True; comp=[(sx,sy)]
        while stack:
            x,y=stack.pop()
            for nx,ny in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
                if 0<=nx<w and 0<=ny<h and mask[ny,nx] and not seen[ny,nx]:
                    seen[ny,nx]=True; comp.append((nx,ny)); stack.append((nx,ny))
        comps.append(comp)
    if not comps: return mask
    biggest=max(len(c) for c in comps)
    out=np.zeros_like(mask)
    for c in comps:
        if len(c)>=max(min_area, biggest*frac):
            for x,y in c: out[y,x]=True
    return out

def build_alpha(name):
    im=Image.open(os.path.join(SRC,name+"_source.png")).convert("RGB")
    a=np.asarray(im).astype(int)
    R,G,B=a[:,:,0],a[:,:,1],a[:,:,2]
    lum=np.maximum(np.maximum(R,G),B); minc=np.minimum(np.minimum(R,G),B)
    if name=="log":
        red=(R>150)&(G<120)&(B<120)&((R-G)>70)
        # exclamation = white pixels enclosed by the red badge
        holes=fill_holes(red)
        excl=holes & (minc>120)
        alpha=np.zeros(lum.shape,dtype=float)
        white_a=clip01((minc-170)/70.0)          # 240..255 -> opaque, soft at the AA edge
        red_a=clip01((R-140)/70.0)*clip01((R-G-40)/60.0)
        alpha=np.maximum(white_a,red_a)
        alpha[excl]=0.0
        # remove AA fringe around the exclamation by shrinking the hole a touch
    else:
        # grey glyph on #323232: keep only pixels near the glyph colour, soft AA ramp
        alpha=clip01((lum-128)/20.0)
    fg=alpha>0.45
    fg=keep_big(fg, frac=0.02, min_area=60)
    alpha=np.where(fg,alpha,0.0)
    # smooth the alpha edge a touch (high-res sources: removes any stray pixel)
    ai=Image.fromarray((alpha*255).astype(np.uint8),mode="L").filter(ImageFilter.GaussianBlur(0.6))
    alpha=np.asarray(ai).astype(float)/255.0
    alpha=clip01((alpha-0.06)/0.90)
    return alpha

from PIL import ImageFilter

def make_master(name):
    alpha=build_alpha(name)
    ys,xs=np.where(alpha>0.02)
    x0,y0,x1,y1=xs.min(),ys.min(),xs.max()+1,ys.max()+1
    # small margin so the AA edge is not clipped
    pad=max(2,int(round(max(x1-x0,y1-y0)*0.02)))
    x0=max(0,x0-pad); y0=max(0,y0-pad); x1=min(alpha.shape[1],x1+pad); y1=min(alpha.shape[0],y1+pad)
    a=alpha[y0:y1,x0:x1]
    rgba=np.zeros((a.shape[0],a.shape[1],4),dtype=np.uint8)
    rgba[...,0:3]=255
    rgba[...,3]=(a*255).astype(np.uint8)
    im=Image.fromarray(rgba,"RGBA")
    im.save(os.path.join(OUT,"master",name+"_native.png"))
    return im

MASTERS={name:make_master(name) for name in files}
print("masters:", {k:v.size for k,v in MASTERS.items()})

SIZES=[24,36,48,72,96,192,512,1024]
DENSITY={24:"mdpi",36:"hdpi",48:"xhdpi",72:"xxhdpi",96:"xxxhdpi"}
for name,im in MASTERS.items():
    for s in SIZES:
        canvas=Image.new("RGBA",(s,s),(0,0,0,0))
        scale=(s*0.92)/max(im.width,im.height)
        w=max(1,int(round(im.width*scale))); h=max(1,int(round(im.height*scale)))
        small=im.resize((w,h),Image.LANCZOS)
        canvas.paste(small,((s-w)//2,(s-h)//2))
        canvas.save(os.path.join(OUT,"png","%s_%d.png"%(name,s)))
    for s,dens in DENSITY.items():
        d=os.path.join(OUT,"android","drawable-"+dens); os.makedirs(d,exist_ok=True)
        src=os.path.join(OUT,"png","%s_%d.png"%(name,s))
        shutil.copyfile(src, os.path.join(d,"ic_vblab_nav_%s.png"%name))
print("sizes + android densities done")
