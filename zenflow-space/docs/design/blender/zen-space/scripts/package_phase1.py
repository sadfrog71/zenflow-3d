"""Release local P1 review files after successful checks; protects hand-edited master."""
from pathlib import Path
import hashlib,json,shutil,re,os
ROOT=Path(__file__).resolve().parents[1];PROJECT=ROOT.parents[3]
m=json.loads((ROOT/'manifest/scene-manifest.json').read_text());r=json.loads((ROOT/'evidence/structure-check.json').read_text());rd=json.loads((ROOT/'evidence/render-all.json').read_text());inst=json.loads((ROOT/'evidence/instancing-report.json').read_text())
assert r['technical_status']=='PASS' and r['source'].endswith(m['source_snapshot'])
assert rd['geometry_unchanged'] and rd['source'].endswith(m['source_snapshot']) and len(rd['renders'])==28
old=json.loads((ROOT/'evidence/release-manifest.json').read_text()) if (ROOT/'evidence/release-manifest.json').exists() else None
master=ROOT/'zen-space.blend'
if master.exists() and (not old or hashlib.sha256(master.read_bytes()).hexdigest()!=old['sha256']):raise RuntimeError('Master changed outside this release; preserve it and reconcile manually')
snapshot=ROOT/m['source_snapshot'];temp=ROOT/'zen-space.blend.review-next';shutil.copy2(snapshot,temp);temp.replace(master)
dated=ROOT/'snapshots/20261020'/snapshot.name;dated.parent.mkdir(parents=True,exist_ok=True)
if dated.exists():raise RuntimeError('Dated snapshot exists; inspect before replacing')
shutil.copy2(snapshot,dated);sha=hashlib.sha256(snapshot.read_bytes()).hexdigest()
release={'version':m['version'],'date':'2026-10-20','master':'zen-space.blend','snapshot':m['source_snapshot'],'dated_snapshot':str(dated.relative_to(ROOT)),'sha256':sha,'master_matches_snapshot':sha==hashlib.sha256(master.read_bytes()).hexdigest(),'bytes':snapshot.stat().st_size,'packed_reference_images':16,'render_count':28,'technical_checks':len(r['checks']),'technical_status':'PASS','phase_status':'PARTIAL - user spatial/aesthetic review pending','source_blender':r['environment']}
(ROOT/'evidence/release-manifest.json').write_text(json.dumps(release,ensure_ascii=False,indent=2))
p=ROOT/'evidence/phase1-review.md';text=p.read_text()
start_table=text.index('| 本期检查 | 结果 |');end_table=text.index('\n\n完整记录：',start_table)
new_table='| 本期检查 | 结果 |\n| --- | --- |\n'+'\n'.join(f"| {c['requirement']} | {c['status']} |" for c in r['checks'])
text=text[:start_table]+new_table+text[end_table:]
if old:text=text.replace(old['version'],m['version']).replace(old['snapshot'],m['source_snapshot'])
text=re.sub(r'当前有 .*?部件共享遵循',f"当前有 {inst['mesh_objects']} 个网格对象，{inst['unique_mesh_data']} 份独立网格数据，{inst['linked_instances']} 个对象复用数据；位置和锚点仍独立。部件共享遵循",text)
text=text.replace('日期、版本快照为',f'带日期快照为 `snapshots/20261020/{snapshot.name}`，渲染采用的源快照为')
text=text.replace('参考图已打包；当前中文编辑字体来自本机系统，第四期完成字体替换或合法可迁移处理','16 张场景参考已打包；源文字使用 Blender Bfont Regular 内置字体，本机中文可读，第四期复核跨系统文字一致性')
text=text.replace('6. 许愿树取签盒侵路：','6. 观景台北向视点前的额外石座已移除，西北视角复用东侧石座；对话相机回到椅背内侧并保留扶手近缘；主院少量红花代理移到绿叶外表面。\n7. 许愿树取签盒侵路：')
text=text.replace('21 项',f"{len(r['checks'])} 项")
if old:text=text.replace(old['version'],m['version']).replace(old['snapshot'],m['source_snapshot'])
p.write_text(text)
p=ROOT/'README.md';text=p.read_text().replace('阶段：第一期全园灰模与空间校验，正在完成通路复验。制作版本、最终证据和剩余项将在本页更新。',f"第一期可编辑全园灰模已交付，版本 `{m['version']}`。21 项模型技术检查 PASS；空间与审美定稿待用户评审，第一期整体记为 PARTIAL。\n\n先打开[对照图册](previews/index.html)，再查看[校验与剩余项](evidence/phase1-review.md)。")
text=text.replace('主编辑源将统一为 `zen-space.blend`，版本快照保留为 `zen-space-p1-vNNN.blend`。',f"主编辑源为 [zen-space.blend](zen-space.blend)，带日期的交付快照为 [20261020／{m['version']}](snapshots/20261020/{snapshot.name})。中间版本保留用于复查；继续编辑时使用主文件。")
text+='\n当前渲染为 1600×1000 像素。16 张场景参考已打包；中文标签使用 Blender 内置 Bfont Regular，本机可读。目标设备画幅、跨系统文字、GLB 与运行加载在对应后续阶段复核。\n'
text=text.replace('21 项',f"{len(r['checks'])} 项")
if old:text=text.replace(old['version'],m['version']).replace(old['snapshot'],m['source_snapshot'])
p.write_text(text)
# Update the existing copied topical entry, retaining the confirmed design content.
p=PROJECT/'docs/design/禅意空间-设计总纲-v1.2-Blender制作版-20261020.md';text=p.read_text()
text=text.replace('本次只修订实施文档与图稿依据，不启动模型制作、软件安装或移动端开发。','本项目已根据 2026-10-20 用户授权开始 Blender 场景制作。制作在独立目录 `zenflow-space` 进行；当前主编辑源与阶段证据见[场景状态](blender/zen-space/README.md)。')
text=re.sub(r'^\| Blender 环境 \|.*$', '| Blender 环境 | `/Applications/Blender.app/Contents/MacOS/Blender`；5.2.2 LTS，已创建、保存、退出并重开模型 | 环境可用；不重复安装；后续导出能力仍按实际小样核验 |',text,flags=re.M)
start=text.index('### 9.4 本轮状态与待定项');tab=text.index('| 待核定项 |',start)
text=text[:start]+f'''### 9.4 本轮状态与待定项

2026-10-20 更新：按用户要求，在独立项目目录 `zenflow-space` 开始实施。第一期灰模已保存为 `zen-space.blend`，版本 `{m['version']}`，交付 28 张同模型视点，包含 4 张快速检查图和 13 个场所视点。

当前状态：Blender 环境、源文件重开、21 项模型技术检查及同模型出图 PASS。第一期整体为 PARTIAL：等待用户对空间尺度、构图和坐姿体验评审，当前不宣布审美认可或尺寸冻结。详见[阶段校验](blender/zen-space/evidence/phase1-review.md)与[对照图册](blender/zen-space/previews/index.html)。

下一期按 §9.1.2，先小型 GLB 导出，再制作临水廊下、水园近岸、读经厅和抄经厅暖暗样板。设备条件未取得；运行加载、日夜材质和应用业务各自保留后续验证。已安装选定的 `environment-artist` 与 `set-dressing`；项目总纲继续覆盖通用技能的网格、眼高和叙事默认值。

'''+text[tab:]
text=text.replace('21 项',f"{len(r['checks'])} 项")
if old:text=text.replace(old['version'],m['version']).replace(old['snapshot'],m['source_snapshot'])
p.write_text(text)
p=PROJECT/'docs/design/style-studies-20261020/v8-first-person/README.md';text=p.read_text();title,rest=text.split('\n',1);p.write_text(title+'\n\n独立项目制作更新：第一期 Blender 灰模与证据见[场景状态](../../blender/zen-space/README.md)。以下图册继续作为定稿概念参考，模型与图稿的差异另行登记。\n'+rest)
# Keep uncopied historical/app-source references useful without migrating the app.
original=Path('/Users/mark.ma/Documents/AI coding/zenflow')
for p in [PROJECT/'docs/design/禅意空间-设计总纲-v1.2-Blender制作版-20261020.md',PROJECT/'docs/design/style-studies-20261020/v8-first-person/README.md']:
 oldfile=original/p.relative_to(PROJECT)
 def repair(mm):
  href=mm.group(1)
  if '://' in href or href.startswith(('#','/')):return mm.group(0)
  path,sep,frag=href.partition('#')
  if not path or (p.parent/path).exists():return mm.group(0)
  target=(oldfile.parent/path).resolve()
  if target.exists():return ']('+os.path.relpath(target,p.parent)+(sep+frag if sep else '')+')'
  return mm.group(0)
 p.write_text(re.sub(r'\]\(([^)]+)\)',repair,p.read_text()))
p=PROJECT/'README.md';text=p.read_text();text=re.sub(r'\n当前交付：第一期灰模[^\n]+\n','\n',text);text+='\n当前交付：第一期灰模 `p1-v010`，28 张检查图，21 项技术检查 PASS，空间与审美定稿待评审。未复制的历史文档和应用源码链接指向相邻原项目，只作为复用依据；模型及预览文件均在本目录。\n';text=text.replace('21 项',f"{len(r['checks'])} 项")
if old:text=text.replace(old['version'],m['version']).replace(old['snapshot'],m['source_snapshot'])
p.write_text(text)
p=ROOT/'evidence/reference-analysis.md';text=p.read_text().replace('从同一平台设置北、东、西北三个石座站点','从同一平台的两个石座设置北、东、西北三个视角');text=text.replace('21 项',f"{len(r['checks'])} 项")
if old:text=text.replace(old['version'],m['version']).replace(old['snapshot'],m['source_snapshot'])
p.write_text(text)
print(json.dumps(release,ensure_ascii=False,indent=2))
