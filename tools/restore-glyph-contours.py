"""Retain approved bitmap dot centers, emit circle coordinates, leave bitmaps intact."""
from pathlib import Path
from PIL import Image
import json
r=Path(__file__).resolve().parents[1];out={}
for name,filename in [('music','music'),('note','note'),('camera','camera-dots'),('settings','settings-dots')]:
 im=Image.open(r/'legacy/src/main/assets'/str(filename+'.png'));a=im.getchannel('A');left,top,right,bottom=a.getbbox();factor=72/max(right-left,bottom-top);cx=(left+right-1)/2;cy=(top+bottom-1)/2;centers=[]
 if name in ['music','note']:
  pixels={(x,y) for y in range(im.height) for x in range(im.width) if a.getpixel((x,y))>32}
  while pixels:
   todo=[pixels.pop()];component=[]
   while todo:
    x,y=todo.pop();component.append((x,y))
    for p in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]:
     if p in pixels:pixels.remove(p);todo.append(p)
   centers.append((sum(p[0] for p in component)/len(component),sum(p[1] for p in component)/len(component)))
 else:
  centers=[(8+x*4,8+y*4) for y in range(21) for x in range(21) if a.getpixel((8+x*4,8+y*4))>128]
 out[name]=[[round((x-cx)*factor,3),round((y-cy)*factor,3)] for x,y in centers]
(r/'legacy/src/main/assets/home-round-dots.json').write_text(json.dumps(out))
