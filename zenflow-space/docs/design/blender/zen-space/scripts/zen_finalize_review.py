from pathlib import Path
import json,html,hashlib,shutil
from PIL import Image,ImageDraw
R=Path('/Users/mark.ma/Documents/AI coding/zenflow-space/docs/design/blender/zen-space');P=R/'previews/p3-v028';refs=R.parent.parent/'style-studies-20261020/v8-first-person'
# Reuse the established camera titles and reference mapping without executing its writer.
s=(R/'scripts/make_review_gallery.py').read_text();ns={};exec(s[s.index('titles='):s.index('first=')],ns)
titles=ns['titles'];mapping=ns['refs'];mapping.pop('CAM_LOOKOUT_N',None);mapping['CAM_LOOKOUT_E']='10-right-lookout-dawn.png'
items=[]
for p in sorted(P.glob('*-gpu/*.png')):
 cam=p.stem;state=p.parent.name.replace('-gpu','');ref=mapping.get(cam) if state in ['day','dawn'] else None
 items.append(dict(camera=cam,state=state,image=p.relative_to(R/'previews').as_posix(),reference=ref))
css='body{background:#eeeae1;color:#26372f;font:15px system-ui;margin:30px}h1{font-weight:400}section{margin:40px 0} .pair{display:flex;gap:12px}figure{margin:0;flex:1;min-width:0}img{width:100%;display:block}figcaption{padding:8px}a{color:#365b47}video{width:80%;max-width:1000px} @media(max-width:800px){.pair{display:block}}'
parts=[f'<!doctype html><meta charset="utf-8"><title>禅意空间 · 最终模型对照</title><style>{css}</style><h1>禅意空间 · p3-v028 模型对照</h1><p>同一可编辑模型的真实渲染；概念参考不是测绘图。技术检查 PASS，视觉还原 PARTIAL，审美确认由用户完成。</p><p><a href="../zen-space.blend">Blender 主文件</a> · <a href="../evidence/final-review.md">自检报告</a> · <a href="runtime/full-model.html">交互预览（需本地服务）</a></p>']
for i in items:
 title=titles.get(i['camera'],i['camera']);parts.append(f'<section><h2>{html.escape(title)} · {i["state"]}</h2><div class="pair"><figure><img loading="lazy" src="{i["image"]}"><figcaption>实际模型 · p3-v028</figcaption></figure>')
 if i['reference']:parts.append(f'<figure><img loading="lazy" src="../../../style-studies-20261020/v8-first-person/{i["reference"]}"><figcaption>v8 概念参考</figcaption></figure>')
 parts.append('</div></section>')
for t in [20,60]:parts.append(f'<h2>{t} 秒动作预览</h2><video controls preload="metadata" src="p3-v028/motion/zen-space-{t}s.mp4"></video>')
(R/'previews/final-review.html').write_text('\n'.join(parts));(R/'evidence/final-view-inventory.json').write_text(json.dumps(items,ensure_ascii=False,indent=2))
for k in range(0,len(items),12):
 batch=items[k:k+12];sheet=Image.new('RGB',(1440,230*((len(batch)+2)//3)),'#eeeae1');d=ImageDraw.Draw(sheet)
 for j,i in enumerate(batch):
  im=Image.open(R/'previews'/i['image']).convert('RGB');im.thumbnail((472,205));x=j%3*480;y=j//3*230;sheet.paste(im,(x,y+23));d.text((x+4,y+4),i['camera']+' / '+i['state'],fill='#26372f')
 sheet.save(R/f'evidence/final-contact-{k//12+1}.jpg',quality=90)
print(len(items),'rendered views; gallery and contact sheets written')
