from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import math,json
root=Path(__file__).resolve().parents[1];out=root/'outputs/host-design';assets=root/'legacy/src/main/assets'
bg='#eeeee8';ink='#20201e';muted='#777871';red='#d73729'
def font(size,dot=False):return ImageFont.truetype(str(assets/('retro-dots.ttf' if dot else 'retro-sans.ttf')),size)
def draw(t,state):
 im=Image.new('RGB',(390,800),bg);d=ImageDraw.Draw(im)
 d.text((26,31),'RETRO LINK',font=font(22,True),fill=ink)
 for y in [37,44,51]:d.ellipse((354,y,357,y+3),fill=ink)
 d.line((26,80,364,80),fill='#d2d3cb')
 # One motion graphic, not an illustration of a phone.
 for i in range(72):
  a=2*math.pi*i/72-math.pi/2;r=112
  x=195+r*math.cos(a);y=320+r*math.sin(a)
  active=state=='connected' or (state=='connecting' and (i/72-t*.6)%1<.18)
  color=ink if active else '#d2d3cb'
  d.ellipse((x-1.5,y-1.5,x+1.5,y+1.5),fill=color)
 # two clusters move together, then a calm solid link
 delta=0 if state=='connected' else 7+6*math.sin(t*5) if state=='connecting' else 14
 for sign in [-1,1]:
  cx=195+sign*(10 if state=='connected' else 20+delta)
  for yy in range(-3,4):
   for xx in range(-2,3):
    if abs(xx)==2 or abs(yy)==3:
     x=cx+xx*5;y=320+yy*5;d.ellipse((x-1,y-1,x+1,y+1),fill=red if state=='connected' else ink)
 status={'idle':'READY','connecting':'LINKING','connected':'LINKED','failed':'RETRY'}[state]
 def center(s,y,size=18,color=ink,dot=False):
  f=font(size,dot);w=d.textbbox((0,0),s,font=f)[2];d.text(((390-w)/2,y),s,font=f,fill=color)
 center(status,475,30,dot=True);center('Xperia pro MK16i',523,15,muted)
 if state!='connected':
  d.rounded_rectangle((89,620,301,672),radius=26,fill=red if state=='connecting' else ink)
  center({'idle':'连接','connecting':'取消','failed':'重试'}[state],632,19,'white')
 else:
  d.ellipse((190,638,200,648),fill=red)
 # outside demo caption, not a product claim
 center('交互动效预览',759,11,muted)
 return im
frames=[]
for i in range(100):
 state='idle' if i<16 else 'connecting' if i<64 else 'connected'
 frames.append(draw(i/20,state))
frames[0].save(out/'host-motion-v3.gif',save_all=True,append_images=frames[1:],duration=50,loop=0,optimize=True)
board=Image.new('RGB',(1234,856),'#deded8');d=ImageDraw.Draw(board)
for j,(state,label) in enumerate([('idle','待连接'),('connecting','连接中 · 可取消'),('connected','连接成功')]):
 x=16+j*406;d.text((x,8),label,font=font(15),fill=ink);board.paste(draw(1.8,state),(x,40))
board.save(out/'host-states-v3.png')
settings=json.loads((root/'outputs/ui-preview/reicon-settings.json').read_text())['docsSnippet']
power=json.loads((out/'reicon-power.json').read_text())['docsSnippet']
html='''<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Retro Link · 主机交互预览</title>
<style>
@font-face{font-family:Dots;src:url('../../legacy/src/main/assets/retro-dots.ttf')}@font-face{font-family:Body;src:url('../../legacy/src/main/assets/retro-sans.ttf')}
:root{--paper:#eeeee8;--ink:#20201e;--muted:#777871;--red:#d73729}*{box-sizing:border-box}body{margin:0;background:#deded8;font-family:Body,system-ui;color:var(--ink);display:grid;place-items:center;min-height:100vh}button{font:inherit;color:inherit;cursor:pointer;-webkit-tap-highlight-color:transparent}button:focus-visible{outline:2px solid var(--red);outline-offset:5px}.phone{width:min(390px,100vw);height:min(800px,100dvh);min-height:640px;background:var(--paper);position:relative;overflow:hidden}header{height:81px;margin:0 26px;border-bottom:1px solid #d2d3cb;display:flex;align-items:center;justify-content:space-between}.brand{font:22px Dots;letter-spacing:1px}.more{border:0;background:none;padding:12px;font-size:24px;line-height:1}.stage{position:absolute;top:24%;left:50%;transform:translateX(-50%);width:244px;height:244px}.ring{position:absolute;inset:10px;border:3px dotted #d2d3cb;border-radius:50%}.orbit{position:absolute;inset:10px;border:3px dotted transparent;border-top-color:var(--ink);border-radius:50%;opacity:0}.links{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:24px;transition:gap .32s}.link{width:25px;height:39px;border:3px dotted var(--ink);border-radius:12px;transition:transform .32s,border-color .32s}.connecting .orbit{opacity:1;animation:spin 1.6s linear infinite}.connecting .links{animation:gather 1.3s ease-in-out infinite}.connected .links{gap:0}.connected .link{border-color:var(--red)}.connected .link:first-child{transform:translateX(5px)}.connected .link:last-child{transform:translateX(-5px)}.connected .ring{border-color:var(--ink);animation:settle .36s ease-out}.failed .link:last-child{transform:translate(8px,6px)}.failed .ring{border-color:var(--red)}
.info{position:absolute;top:59%;width:100%;text-align:center}.state{font:30px Dots;letter-spacing:1px;margin:0 0 16px}.device{color:var(--muted);font-size:15px}.main{position:absolute;left:89px;right:89px;bottom:16%;height:52px;border:0;border-radius:30px;background:var(--ink);color:var(--paper);font-size:19px;transition:background .2s,transform .12s}.main:active{transform:scale(.97)}.connecting .main{background:var(--red)}.connected .main{background:transparent;color:var(--red);cursor:default}.preview{position:absolute;bottom:25px;width:100%;text-align:center;color:var(--muted);font-size:11px}.sheet{position:absolute;inset:0;background:#20201e44;display:flex;align-items:flex-end;z-index:5}.sheet[hidden]{display:none}.sheetbox{background:var(--paper);width:100%;padding:18px 26px 28px;border-radius:24px 24px 0 0}.handle{width:32px;height:3px;border-radius:3px;background:#bbb;margin:0 auto 18px}.row{width:100%;padding:16px 0;border:0;border-bottom:1px solid #d2d3cb;background:transparent;text-align:left;display:flex;justify-content:space-between;align-items:center;font-size:16px}.row svg{width:22px;height:22px}.row:last-child{border:0;color:var(--red)}.close{width:100%;background:var(--ink);color:white;border:0;border-radius:30px;padding:13px;margin-top:16px}.hint{padding:12px 0;font-size:13px;line-height:1.8;color:var(--muted)}.test{position:fixed;left:20px;bottom:20px;display:flex;gap:8px}.test button{border:1px solid #bbb;background:var(--paper);border-radius:20px;padding:7px 12px;font-size:12px}@keyframes spin{to{transform:rotate(360deg)}}@keyframes gather{50%{gap:4px}}@keyframes settle{from{transform:scale(1.04);opacity:.5}to{transform:scale(1);opacity:1}}@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation:none!important;transition:none!important}}@media(max-width:800px){.test{position:static;margin:12px}}
</style><main class="phone idle" id="phone"><header><span class="brand">RETRO LINK</span><button class="more" id="more" aria-label="更多操作">⋮</button></header><section class="stage" aria-hidden="true"><div class="ring"></div><div class="orbit"></div><div class="links"><i class="link"></i><i class="link"></i></div></section><section class="info"><p class="state" id="state" aria-live="polite">READY</p><span class="device" id="device">Xperia pro MK16i</span></section><button class="main" id="main">连接</button><div class="preview">交互动效预览 · 状态为模拟</div><div class="sheet" id="sheet" hidden><section class="sheetbox" role="dialog" aria-modal="true" aria-label="连接管理"><div class="handle"></div><div id="items"></div><button class="close" id="close">返回</button></section></div></main><aside class="test"><button id="simulate">模拟掉线</button><button id="first">首次使用</button></aside>
<script>
const phone=document.querySelector('#phone'),main=document.querySelector('#main'),state=document.querySelector('#state'),sheet=document.querySelector('#sheet'),items=document.querySelector('#items');let current='idle',timer,opener;
function set(s){clearTimeout(timer);current=s;phone.className='phone '+s;state.textContent={idle:'READY',connecting:'LINKING',connected:'LINKED',failed:'RETRY'}[s];main.textContent={idle:'连接',connecting:'取消',connected:'●',failed:'重试'}[s];main.setAttribute('aria-label',s==='connected'?'已连接，管理连接':main.textContent)}
main.onclick=()=>{if(current==='connecting'){set('idle');return}if(current==='connected'){menu();return}set('connecting');timer=setTimeout(()=>{set('connected')},2400)};
function dialog(html){opener=document.activeElement;items.innerHTML=html;sheet.hidden=false;sheet.querySelector('button').focus()}
function hide(){sheet.hidden=true;if(opener)opener.focus()}
function menu(){dialog('<button class="row" onclick="chooseDevice()">更换设备 <span>↗</span></button><button class="row" onclick="permission()">权限与后台 __SETTINGS__</button><button class="row" onclick="notes()">速记箱 <span>↗</span></button><button class="row" onclick="set(\'idle\');hide()">断开连接 __POWER__</button>')}
function chooseDevice(){dialog('<button class="row" onclick="document.querySelector(\'#device\').textContent=\'Xperia pro MK16i\';set(\'idle\');hide()">Xperia pro MK16i <span>●</span></button><p class="hint">正式版本只显示真实已配对设备。</p>')}
function permission(){dialog('<p class="hint">首次连接将按顺序引导：蓝牙 → 系统授权 → 等待老手机。这里只演示引导节奏。</p><button class="row" onclick="setup()">演示引导 <span>→</span></button>')}
let step=0;function setup(){const labels=['蓝牙','连接授权','音乐与日历'];dialog('<p class="state" style="font-size:23px">0'+(step+1)+' / 03</p><p class="hint">'+labels[step]+'</p><button class="row" onclick="nextStep()">继续 <span>→</span></button>')};function nextStep(){step++;if(step<3)setup();else{step=0;hide();main.click()}}
function notes(){dialog('<p class="hint">速记箱入口保留。此交互稿未接入真实笔记。</p>')}
document.querySelector('#more').onclick=menu;document.querySelector('#close').onclick=hide;sheet.onclick=e=>{if(e.target===sheet)hide()};document.addEventListener('keydown',e=>{if(e.key==='Escape')hide();if(e.key==='Tab'&&!sheet.hidden){const f=[...sheet.querySelectorAll('button')];const a=f[0],b=f[f.length-1];if(e.shiftKey&&document.activeElement===a){e.preventDefault();b.focus()}else if(!e.shiftKey&&document.activeElement===b){e.preventDefault();a.focus()}}});document.querySelector('#simulate').onclick=()=>set('failed');document.querySelector('#first').onclick=()=>{step=0;setup()};document.addEventListener('visibilitychange',()=>{phone.style.animationPlayState=document.hidden?'paused':'running';if(document.hidden&&current==='connecting')set('idle')});
</script></html>'''
import re
html=re.sub(r"dialog\('(.*)'\)",lambda m:'dialog(`'+m.group(1)+'`)',html)
html=html.replace('__SETTINGS__',settings.replace("'",'&#39;')).replace('__POWER__',power.replace("'",'&#39;'))

if not (out/'host-motion-v3.html').exists():
 (out/'host-motion-v3.html').write_text(html)
print(out/'host-motion-v3.html')
