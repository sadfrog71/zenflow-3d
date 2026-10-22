"""Original tileable material maps for dressed stone, washi, woven flax and ceramic glaze."""
from pathlib import Path
import numpy as np
from PIL import Image,ImageFilter,ImageDraw
import json
R=Path(__file__).resolve().parents[1];O=R/'assets/textures/detail-20261021';O.mkdir(parents=True,exist_ok=True);N=1024;rng=np.random.default_rng(211029)
def field(scale):
 a=rng.random((scale,scale),dtype=np.float32);a=np.pad(a,((0,1),(0,1)),mode='wrap');im=Image.fromarray(a,'F').resize((N+N//scale,N+N//scale),Image.Resampling.BICUBIC);return np.asarray(im,dtype=np.float32)[:N,:N]
def save(name,col,h,rough,strength=1):
 Image.fromarray(np.uint8(np.clip(col,0,1)*255)).save(O/(name+'-base.png'))
 Image.fromarray(np.uint8(np.clip(rough,0,1)*255)).save(O/(name+'-rough.png'))
 dx=(np.roll(h,-1,1)-np.roll(h,1,1))*strength;dy=(np.roll(h,-1,0)-np.roll(h,1,0))*strength;v=np.stack([-dx,-dy,np.ones_like(dx)],-1);v/=np.linalg.norm(v,axis=-1,keepdims=True);Image.fromarray(np.uint8(np.clip(v*.5+.5,0,1)*255)).save(O/(name+'-normal.png'))
fine=field(512);mid=field(64);large=field(8);grain=(fine-.5)*.12+(mid-.5)*.08+(large-.5)*.06;pits=rng.random((N,N))<.007;grain-=pits*.12
col=np.stack([.59+grain,.585+grain*.98,.55+grain*.92],-1);save('dressed-limestone',col,grain,.78+(mid-.5)*.15,2.5)
# Delicate translucent paper fibres; both visible reflection and emission use the same nonuniform sheet.
im=Image.new('L',(N,N),0);d=ImageDraw.Draw(im)
for i in range(2700):
 x=int(rng.integers(N));y=int(rng.integers(N));a=rng.uniform(0,np.pi);length=rng.uniform(3,21);d.line([(x,y),(x+np.cos(a)*length,y+np.sin(a)*length)],fill=int(rng.integers(45,150)),width=1)
fibres=np.asarray(im.filter(ImageFilter.GaussianBlur(.5)),dtype=np.float32)/255;paper=.90+(field(16)-.5)*.04-fibres*.13;col=np.stack([paper,paper*.93,paper*.79],-1);save('washi',col,fibres*.08+fine*.015,.87+fine*.06,1.5)
y,x=np.indices((N,N));warp=(np.sin(x*np.pi/4)*.5+.5);weft=(np.sin(y*np.pi/4)*.5+.5);cross=((x//8+y//8)%2);height=np.where(cross==0,warp*.6+weft*.2,weft*.6+warp*.2);thread=.48+height*.09+(field(128)-.5)*.045;col=np.stack([thread,thread*.86,thread*.65],-1);save('flax-weave',col,height*.16,.91+fine*.06,2)
speck=rng.random((N,N));dark=np.where(speck<.0025,rng.uniform(.10,.34,(N,N)),0);tone=.78+(field(32)-.5)*.035-dark;col=np.stack([tone,tone*.94,tone*.80],-1);save('iron-speckled-glaze',col,dark*.03+fine*.006,.23+field(64)*.10,1)
(O/'source.json').write_text(json.dumps({'date':'2026-10-21','method':'original deterministic procedural material maps; no external images','seed':211029,'pixels':[N,N],'families':['dressed-limestone','washi','flax-weave','iron-speckled-glaze'],'channels':['base','rough','normal']},indent=2));print('Created 12 original packed-ready maps',O)
