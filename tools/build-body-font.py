"""Flatten upstream Noto Sans SC variable TTF for pre-variable Android."""
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools import subset
from fontTools.varLib.instancer import instantiateVariableFont
import sys
root=Path(__file__).resolve().parents[1]
f=TTFont(sys.argv[1]);opts=subset.Options();opts.layout_features=['*'];opts.recalc_timestamp=False
s=subset.Subsetter(options=opts);s.populate(unicodes=list(range(0x20,0x250))+list(range(0x2000,0x2070))+list(range(0x3000,0x3100))+list(range(0x3400,0xa000))+list(range(0xff00,0xfff0)));s.subset(f)
f=instantiateVariableFont(f,{'wght':400},inplace=True)
assert 'fvar' not in f and 'glyf' in f
for c in '音乐日程速记照片设置主手机已连接未保存返回':assert ord(c) in f.getBestCmap()
f.save(root/'legacy/src/main/assets/retro-sans.ttf');print('Static Chinese font: PASS; glyphs',len(f.getBestCmap()))
