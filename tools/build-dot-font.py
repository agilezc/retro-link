"""Build our own dot font from the approved preview patterns, not Nothing assets."""
from pathlib import Path
import ast
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
root=Path(__file__).resolve().parents[1]
patterns={}
for n in ast.walk(ast.parse((root/'outputs/ui-preview/render-nothing-preview-v5.py').read_text())):
 if isinstance(n,ast.Constant) and isinstance(n.value,str) and 'R:11110/' in n.value:
  for item in n.value.split():
   k,v=item.split(':',1) if not item.startswith('::') else (':',item[2:]);patterns[k]=v.split('/')
for k,v in {'6':'01110/10000/10000/11110/10001/10001/01110','7':'11111/00001/00010/00100/01000/01000/01000','8':'01110/10001/10001/01110/10001/10001/01110','A':'01110/10001/10001/11111/10001/10001/10001','B':'11110/10001/10001/11110/10001/10001/11110','C':'01111/10000/10000/10000/10000/10000/01111','D':'11110/10001/10001/10001/10001/10001/11110','F':'11111/10000/10000/11110/10000/10000/10000','G':'01111/10000/10000/10111/10001/10001/01111','H':'10001/10001/10001/11111/10001/10001/10001','J':'00111/00010/00010/00010/10010/10010/01100','M':'10001/11011/10101/10101/10001/10001/10001','P':'11110/10001/10001/11110/10000/10000/10000','Q':'01110/10001/10001/10001/10101/10010/01101','S':'01111/10000/10000/01110/00001/00001/11110','U':'10001/10001/10001/10001/10001/10001/01110','V':'10001/10001/10001/10001/10001/01010/00100','W':'10001/10001/10001/10101/10101/11011/10001','X':'10001/10001/01010/00100/01010/10001/10001','Y':'10001/10001/01010/00100/00100/00100/00100','Z':'11111/00001/00010/00100/01000/10000/11111'}.items():patterns[k]=v.split('/')
order=['.notdef','space']+['g'+str(ord(k)) for k in patterns];glyphs={};metrics={}
for name in order:
 pen=TTGlyphPen(None);ch=next((k for k in patterns if name=='g'+str(ord(k))),None)
 rows=patterns.get(ch,[])
 for y,row in enumerate(rows):
  for x,b in enumerate(row):
   if b=='1':
    cx=65+x*120;cy=790-y*120;rr=32
    pen.moveTo((cx+rr,cy));pen.qCurveTo((cx+rr,cy+rr),(cx,cy+rr));pen.qCurveTo((cx-rr,cy+rr),(cx-rr,cy));pen.qCurveTo((cx-rr,cy-rr),(cx,cy-rr));pen.qCurveTo((cx+rr,cy-rr),(cx+rr,cy));pen.closePath()
 glyphs[name]=pen.glyph();metrics[name]=(300 if ch==':' or name=='space' else 720,0)
f=FontBuilder(1000,isTTF=True);f.setupGlyphOrder(order);f.setupCharacterMap({32:'space',**{ord(k):'g'+str(ord(k)) for k in patterns}});f.setupGlyf(glyphs);f.setupHorizontalMetrics(metrics);f.setupHorizontalHeader(ascent=900,descent=-100);f.setupNameTable({'familyName':'Retro Dots','styleName':'Regular','uniqueFontIdentifier':'RetroDots2','fullName':'Retro Dots Regular','psName':'RetroDots-Regular'});f.setupOS2(sTypoAscender=900,sTypoDescender=-100,usWinAscent=900,usWinDescent=100);f.setupPost();f.setupMaxp();f.save(root/'legacy/src/main/assets/retro-dots.ttf')
assert all(ord(c) in f.font.getBestCmap() for c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:/ ')
print('Dot font coverage: A-Z, 0-9, punctuation PASS')
