from pathlib import Path
import json,zipfile,hashlib,re
R=Path('/Users/mark.ma/Documents/AI coding/zenflow-space/docs/design/blender/zen-space');project=R.parents[3]
# Preserve this execution's review preparation scripts as reviewable delivery helpers.
for name in ['zen_finalize_review','zen_report_delivery','zen_portable_check','zen_package']:
 p=Path('/tmp')/(name+'.py')
 if p.exists():(R/'scripts'/(name+'.py')).write_text(p.read_text())
page=R/'previews/final-review.html';txt=page.read_text();txt=txt.replace('<h2>20 秒动作预览</h2>','<section><h2>全园总览 · 第二张概念参考</h2><div class="pair"><figure><img loading="lazy" src="p3-v028/day-gpu/CAM_WORLD_FULL.png"><figcaption>实际模型 · 同一个全园模型</figcaption></figure><figure><img loading="lazy" src="../../../style-studies-20261020/v8-first-person/00-overview-meditation-cushion.png"><figcaption>v8 第二张全园概念参考</figcaption></figure></div></section><h2>20 秒动作预览</h2>');page.write_text(txt)
lic=['# 第三方素材与许可','\n场景建筑、路径、程序化造景和交互承载面为本次制作；以下外部素材依据记录复用。概念参考为项目提供的设计资料，不作为可自由转售的第三方素材授权。\n','- Poly Haven HDRI、树、蕨类和木石／地面贴图：CC0。来源及下载校验值见下列原始清单。','- Noto Sans CJK SC：SIL OFL 1.1；字体已打包，授权全文随附 `../assets/fonts/OFL.txt`。','- Three.js r169：MIT；授权全文随运行预览 vendor 文件提供。\n']
for p in sorted((R/'manifest').glob('*.json')):
 if any(x in p.name for x in ['source','third-party','material','forest','ground']):
  lic.append('## '+p.name+'\n\n```json\n'+p.read_text()+'\n```\n')
(R/'manifest/LICENSES.md').write_text('\n'.join(lic))
# All source/render files included retain their project-relative paths, so offline links remain valid.
files=[project/'README.md',R/'README.md',R/'zen-space.blend']
files+=list((R/'manifest').glob('*'))
files+=list((R/'evidence').glob('final*'))+list((R/'evidence').glob('full-runtime*'))+[R/'evidence/current-work-manifest.json',R/'evidence/structure-check-p3.json',R/'evidence/portability-check-p3-v028.json']
files+=[R/'exports'/x for x in ['zen-space-p3-v028.glb','writing-left-layout-v001.glb','surface-kit-v001.glb','states-kit-v001.glb']]
files+=list((R/'previews/runtime').rglob('*'));files+=[R/'previews/final-review.html']+list((R/'previews/p3-v028').glob('*-gpu/*.png'))+list((R/'previews/p3-v028/motion').glob('*.mp4'))
files+=list((project/'docs/design/style-studies-20261020/v8-first-person').glob('*.png'))+list((R/'assets/fonts').glob('*'))
files+=[project/'docs/design/禅意空间-设计总纲-v1.2-Blender制作版-20261020.md']
files=sorted(set(p for p in files if p.is_file()))
links=[]
for match in re.finditer(r'(?:src|href)="([^"]+)"',page.read_text()):
 ref=match[1]
 if not ref.startswith(('http','#')):links.append({'link':ref,'exists':(page.parent/ref).exists()})
assert all(x['exists'] for x in links),[x for x in links if not x['exists']]
(R/'evidence/current-work-links.json').write_text(json.dumps({'version':'p3-v028','links':links,'pass':True},indent=2));files.append(R/'evidence/current-work-links.json')
out=project/'deliverables';out.mkdir(exist_ok=True);z=out/'zen-space-p3-v028-20261020.zip'
with zipfile.ZipFile(z,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as f:
 for p in files:f.write(p,p.relative_to(project))
manifest={'file':str(z.relative_to(project)),'bytes':z.stat().st_size,'sha256':hashlib.sha256(z.read_bytes()).hexdigest(),'files':len(files),'source_version':'p3-v028'};(out/'zen-space-p3-v028-20261020.json').write_text(json.dumps(manifest,indent=2));print(manifest)
