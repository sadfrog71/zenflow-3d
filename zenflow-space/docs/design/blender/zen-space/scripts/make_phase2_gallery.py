from pathlib import Path
import json,html
from PIL import Image,ImageOps,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'previews/phase2';source=json.loads((ROOT/'evidence/phase2-core-build.json').read_text())
refs={'CAM_ENTRY_SEATED':'01-waterside-veranda.png','CAM_LIBRARY_SEATED':'04-library-seat.png','CAM_LIBRARY_SHELF':'13-bookshelf-select.png','CAM_COPY_VIEW':'05-copy-hall-seated.png','CAM_ISLAND_SEATED':'03-island-meditation.png','CAM_P2_CORE_OVERVIEW':'00-overview-courtyard-blossoms.png'}
titles={'CAM_ENTRY_SEATED':'临水廊下','CAM_LIBRARY_SEATED':'临窗阅读位','CAM_LIBRARY_SHELF':'四本书与内容面','CAM_COPY_VIEW':'抄经厅案列','CAM_ISLAND_SEATED':'低岛坐姿','CAM_P2_CORE_OVERVIEW':'核心区域定位','CAM_COPY_RH':'右手抄写视点','CAM_COPY_LH':'左手抄写视点'}
labels={'day':'昼','dusk':'暮','night':'夜','off':'熄灯'}
sections=[];count=0
for cam in titles:
 photos=[]
 for state in labels:
  f=out/f'{cam}-{state}.png'
  if f.exists():photos.append(f'<figure><a href="{f.name}" target="_blank"><img loading="lazy" src="{f.name}" alt="{titles[cam]}·{labels[state]}"></a><figcaption>实际模型 · {labels[state]} · 1600×900</figcaption></figure>');count+=1
 if not photos:continue
 reference=f'<details><summary>查看定稿参考图</summary><img src="../../../../style-studies-20261020/v8-first-person/{refs[cam]}" alt="定稿参考·{titles[cam]}"><p>参考图说明视觉方向；本页上方为同一 Blender 模型的实际渲染。</p></details>' if cam in refs else ''
 sections.append(f'<section><h2>{titles[cam]}</h2><div class="grid">'+''.join(photos)+f'</div>{reference}</section>')
text='''<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Zenflow Space · 核心样板</title><style>*{box-sizing:border-box}body{margin:0;background:#eeece4;color:#293b32;font:16px/1.65 system-ui}header,main,footer{max-width:1500px;margin:auto;padding:24px 32px}header{padding-top:48px}h1{font-size:32px;margin:0}h2{font-size:22px;font-weight:500;margin:0 0 16px}p{max-width:920px}a{color:#405d4c}section{padding:26px 0 38px;border-top:1px solid #c8ccbf}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(390px,1fr));gap:18px}figure{margin:0}img{max-width:100%;display:block}figcaption{font-size:13px;padding:10px 0;color:#647165}summary{cursor:pointer;padding:12px 0}details{margin-top:8px}details img{max-width:1100px}.tag{font-size:13px;letter-spacing:.08em;color:#657364}@media(max-width:600px){header,main,footer{padding:20px}.grid{grid-template-columns:1fr}h1{font-size:27px}}</style><header><span class="tag">ZENFLOW SPACE / 实际模型预览</span><h1>第二期 · 核心样板</h1><p>木构、石岸、水面、书架与暖暗案灯。昼／暮／夜共用同一模型和对应相机；其他区域仍有灰模。本页不代表全园完成、手机性能或最终审美验收。</p><p>源版本：p2-v002 · Blender 5.2.2 LTS · 实际渲染 COUNT 张</p><p><a href="../../zen-space.blend">打开主模型</a>　<a href="../../evidence/phase2-review.md">制作记录与剩余项</a>　<a href="../index.html">第一期空间检查</a>　<a href="../runtime/index.html">GLB 小样（需本地服务）</a></p></header><main>SECTIONS</main><footer>独立项目 zenflow-space · 不包含真实私人内容或应用业务接入。</footer></html>'''
(out/'index.html').write_text(text.replace('COUNT',str(count)).replace('SECTIONS',''.join(sections)))
# Four visible actual renders, no reference images blended into the preview.
items=[('CAM_ENTRY_SEATED','day','临水廊下 · 昼'),('CAM_LIBRARY_SHELF','day','书架与内容面 · 昼'),('CAM_COPY_VIEW','dusk','抄经厅 · 暮'),('CAM_COPY_VIEW','night','抄经厅 · 夜')]
if all((out/f'{c}-{s}.png').exists() for c,s,t in items):
 sheet=Image.new('RGB',(1920,1180),'#eeece4');d=ImageDraw.Draw(sheet);font=ImageFont.truetype('/System/Library/Fonts/Hiragino Sans GB.ttc',24)
 for i,(cam,state,title) in enumerate(items):
  x=(i%2)*960;y=(i//2)*590;im=Image.open(out/f'{cam}-{state}.png');sheet.paste(ImageOps.fit(im,(960,540)),(x,y));d.text((x+20,y+552),title,font=font,fill='#293b32')
 sheet.save(out/'phase2-four-views.jpg',quality=94)
print('GALLERY_RENDER_COUNT',count)
