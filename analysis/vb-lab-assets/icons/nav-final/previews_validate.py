# -*- coding: utf-8 -*-
import os
from PIL import Image, ImageDraw, ImageFont
import numpy as np
OUT=r"C:\Users\julye\IdeaProjects\vbhelper\analysis\vb-lab-assets\icons\nav-final"
PNG=os.path.join(OUT,"png"); PREV=os.path.join(OUT,"preview")
icons=["scan","log","digidex","items","battle"]
try: F=ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf",18)
except Exception: F=ImageFont.load_default()
def bg(size): return Image.new("RGB",size,(50,50,50))
def checker(size,cell=16):
    im=Image.new("RGB",size,(214,214,214)); d=ImageDraw.Draw(im)
    for y in range(0,size[1],cell):
        for x in range(0,size[0],cell):
            if ((x//cell)+(y//cell))%2: d.rectangle([x,y,x+cell-1,y+cell-1],fill=(164,164,164))
    return im
def sheet(bgfunc,name,title,size=192):
    pad=24; cell=size+44; W=pad*2+cell*len(icons); H=size+86
    im=bgfunc((W,H)); d=ImageDraw.Draw(im); d.text((pad,10),title,fill=(140,210,255),font=F)
    for i,icon in enumerate(icons):
        tile=Image.open(os.path.join(PNG,"%s_%d.png"%(icon,size)))
        im.paste(tile,(pad+i*cell+(size-tile.width)//2,44),tile)
        d.text((pad+i*cell+16,44+size+8),icon,fill=(210,210,220),font=F)
    im.save(os.path.join(PREV,name))
sheet(bg,"white_on_dark.png","white PNGs on dark (192 px)")
sheet(checker,"white_on_checker.png","alpha check")
# masters preview
big=Image.new("RGB",(5*300+40,340),(50,50,50)); d=ImageDraw.Draw(big)
d.text((16,8),"1024 masters (shown at 280 px)",fill=(140,210,255),font=F)
for i,icon in enumerate(icons):
    tile=Image.open(os.path.join(PNG,"%s_1024.png"%icon)).resize((280,280),Image.LANCZOS)
    big.paste(tile,(20+i*300,44),tile)
big.save(os.path.join(PREV,"masters_preview.png"))
# source vs result
src=os.path.join(OUT,"source")
panels=[]
for icon in icons:
    s=Image.open(os.path.join(src,icon+"_source.png")).convert("RGB")
    S=1
    orig=s
    png=Image.open(os.path.join(PNG,"%s_1024.png"%icon))
    h=min(300,orig.height)
    png2=png.resize((int(png.width*h/png.height),h),Image.LANCZOS)
    panel=Image.new("RGB",(orig.width+png2.width+24,h+30),(24,24,30))
    panel.paste(orig,(0,26)); panel.paste(png2,(orig.width+24,26),png2)
    dd=ImageDraw.Draw(panel)
    dd.text((2,4),icon+" — recebido",fill=(140,210,255),font=F)
    dd.text((orig.width+26,4),"PNG branco",fill=(140,210,255),font=F)
    panels.append(panel)
maxw=max(p.width for p in panels); total=sum(p.height+8 for p in panels)+8
sheet2=Image.new("RGB",(maxw+16,total),(24,24,30)); y=4
for p in panels: sheet2.paste(p,(8,y)); y+=p.height+8
sheet2.save(os.path.join(PREV,"source_vs_result.png"))
# validation
print("VALIDACAO")
ok=True
for icon in icons:
    for s in (24,96,1024):
        p=os.path.join(OUT,"png","%s_%d.png"%(icon,s))
        rgba=np.asarray(Image.open(p).convert("RGBA")); a=rgba[...,3]
        white=bool(np.all(rgba[...,:3][a>0]==255)); bgtrans=bool(a[0,0]==0)
        print("%-8s %4dpx bbox=%-16s alphaMax=%d branco=%s fundo=%s"%(
              icon,s,str(Image.open(p).getbbox()),int(a.max()),white,bgtrans))
        ok=ok and white and bgtrans
print("OK" if ok else "FALHOU")
# check exclamation is transparent in log
log=np.asarray(Image.open(os.path.join(OUT,"png","log_1024.png")).convert("RGBA"))[...,3]
print("log alpha unique sample:", np.unique(log)[:10], "...", np.unique(log)[-5:])
