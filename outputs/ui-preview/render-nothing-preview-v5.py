from PIL import Image,ImageDraw,ImageFont
import gzip
from pathlib import Path
out=Path('retro-link/outputs/ui-preview'); bg='#eeeee8';ink='#20201e';muted='#777871';red='#d73729'
im=Image.new('RGB',(854,480),bg);d=ImageDraw.Draw(im)
f='/System/Library/Fonts/STHeiti Medium.ttc'
def t(x,y,s,size=20,c=ink):d.text((x,y),s,font=ImageFont.truetype(f,size),fill=c)
glyphs={}
for row in gzip.open(out/'unifont.hex.gz','rt'):
 a,b=row.strip().split(':');glyphs[int(a,16)]=b
def dots(x,y,s,step=3,c=ink):
 for ch in s:
  raw=glyphs.get(ord(ch),glyphs[63]);w=len(raw)//4;bits=bin(int(raw,16))[2:].zfill(w*16)
  for i,v in enumerate(bits):
   if v=='1':d.ellipse((x+i%w*step,y+i//w*step,x+i%w*step+max(1,step-2),y+i//w*step+max(1,step-2)),fill=c)
  x+=w*step

patterns={}
for item in 'R:11110/10001/10001/11110/10100/10010/10001 E:11111/10000/10000/11110/10000/10000/11111 T:11111/00100/00100/00100/00100/00100/00100 O:01110/10001/10001/10001/10001/10001/01110 L:10000/10000/10000/10000/10000/10000/11111 I:11111/00100/00100/00100/00100/00100/11111 N:10001/11001/11001/10101/10011/10011/10001 K:10001/10010/10100/11000/10100/10010/10001 0:01110/10001/10011/10101/11001/10001/01110 1:00100/01100/00100/00100/00100/00100/01110 2:01110/10001/00001/00010/00100/01000/11111 3:11110/00001/00001/01110/00001/00001/11110 4:00010/00110/01010/10010/11111/00010/00010 5:11111/10000/10000/11110/00001/00001/11110 9:01110/10001/10001/01111/00001/00001/01110 /:00001/00010/00010/00100/01000/01000/10000 ::0/1/0/0/1/0/0 <:0001/0010/0100/1000/0100/0010/0001 >:1000/0100/0010/0001/0010/0100/1000'.split():
 key,shape=item.split(':',1) if not item.startswith('::') else (':',item[2:]);patterns[key]=shape.split('/')
def labeldots(x,y,s,step=4,c=ink):
 for ch in s:
  rows=patterns.get(ch,[])
  for yy,row in enumerate(rows):
   for xx,v in enumerate(row):
    if v=='1':d.ellipse((x+xx*step,y+yy*step,x+xx*step+max(1,step-2),y+yy*step+max(1,step-2)),fill=c)
  x+=(3 if ch==' ' else (2 if ch==':' else 6))*step

# Header
labeldots(30,20,'RETRO LINK',3);labeldots(726,22,'15:09',3)
d.line((30,63,824,63),fill='#d2d3cb',width=1)

# Balanced carousel: three centers on a common grid; text baselines consistent.
# Peripheral circles 112px, central 152px; tight icon/label grouping.
d.ellipse((124,159,236,271),fill='#ddded6');dots(156,182,'♫',4)
d.rounded_rectangle((351,139,503,291),radius=32,fill=ink)
for row in range(13):
 for col in range(15):
  active=(row in (2,12) and 1<=col<=13) or (col in (1,13) and 2<=row<=12) or (row==5 and 1<=col<=13) or (col in (4,10) and row in (0,1,2,3)) or (row in (7,9) and col in (4,7,10))
  if active:d.ellipse((383+col*6,176+row*6,386+col*6,179+row*6),fill=bg)
d.ellipse((618,159,730,271),fill='#ddded6');dots(642,182,'✎',4)
# Shared baseline and restrained emphasis.
t(155,312,'音乐',24,muted);t(399,308,'日程',28);t(649,312,'速记',24,muted)
# Full 5-item position indicator, selected second.
for i in range(5):
 x=393+i*16
 d.ellipse((x,374,x+5,379),fill=red if i==1 else '#b6b7af')
d.line((30,421,824,421),fill='#d2d3cb');labeldots(30,444,'<  >',2,muted);labeldots(397,442,'ENTER',2)
# Footer uses a shared baseline, consistent outer gutters, right-aligned device state.
status='Nothing Phone (3a) · 已连接'
fstatus=ImageFont.truetype(f,15)
box=d.textbbox((0,0),status,font=fstatus)
sx=824-(box[2]-box[0])
d.text((sx,440),status,font=fstatus,fill='#565952')
d.ellipse((sx-15,447,sx-9,453),fill='#397653')
im.save(out/'landscape-nothing-home-v5.png')
