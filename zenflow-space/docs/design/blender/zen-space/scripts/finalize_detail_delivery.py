"""Promote the checked local refinement and refresh the existing handoff without removing history."""
from pathlib import Path
import json,hashlib,shutil,re,zipfile
R=Path(__file__).resolve().parents[1];project=R.parents[3];E=R/'evidence';M=R/'manifest';V='p3-v031';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert len(list((R/'previews'/V).glob('*-gpu/*.png')))==44
checks=json.loads((E/'structure-check-p3.json').read_text());assert checks['version']==V and checks['technical_status']=='PASS'
audit=json.loads((E/'final-candidate-source-audit.json').read_text());assert audit['version']==V and not audit['missing_images'] and audit['degenerate_unique_source_polygons']==0
port=json.loads((E/'portability-check-p3-v031.json').read_text());assert port['all_used_images_packed'] and port['all_used_fonts_packed_or_builtin'] and not port['linked_libraries'] and port['render_exists']
source=R/(f'zen-space-{V}.blend');source_sha=sha(source);old_sha='57d7eee4fcb425c1f0de28398f7975c639014249c138b2f9c67e53ff3b55aa14';master=R/'zen-space.blend'
assert sha(master) in [old_sha,source_sha],'Outside master edits detected; promotion refused'
shutil.copy2(source,master);snap=R/'snapshots/20261021';snap.mkdir(exist_ok=True);shutil.copy2(source,snap/source.name);shutil.copy2(M/'asset-register-current.csv',M/'asset-register.csv')
previous=E/'current-work-manifest-p3-v028.json'
if not previous.exists():shutil.copy2(E/'current-work-manifest.json',previous)
manifest={'version':V,'status':'DETAIL_REFINEMENT_DELIVERED_VISUAL_PARTIAL','master':'zen-space.blend','source_snapshot':source.name,'dated_snapshot':str((snap/source.name).relative_to(R)),'sha256':source_sha,'previous_master_sha256':old_sha,'technical_checks':len(checks['checks']),'technical_status':'PASS','visual_fidelity':'PARTIAL','rendered_views':44,'cameras':29,'registered_actions':31,'device_application':'NOT_VERIFIED','report':'detail-review-20261021.md','portability':'PASS'};(E/'current-work-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
report='''# 细节打磨 · 2026-10-21

当前模型 **p3-v031**。承接 p3-v028，进行局部细化；全园布局及所有第一视角相机保留。新增石材、灯具、家具与器物的真实几何和贴图，所有画面均来自同一个源模型。

## 本轮改动

| 对象 | 修改 |
| --- | --- |
| 桥与庭院铺地 | 1260 个相关石面改用细颗粒石材，将人工铺装和自然裂石区分；两桥增加 144 块贴合弧度的铺石、约 6mm 板缝和侧面砌缝。 |
| 木构柱础 | 50 个柱础增加分层倒角，保留原落位和高度。 |
| 抄写纸灯 | 12 盏缩小约 17%，改为四片薄纸与真实透光；增加细框、木榫和纤维材质，保留原光源及动作对象。 |
| 坐垫与桌案 | 13 个圆垫改为圆润轮廓、细织物和双道包边；12 张案增加内收横撑和榫头。户外仍只有一个圆垫。 |
| 茶器与石钵 | 四件杯碟采用细斑釉面和清漆粗糙度；石钵改为细颗粒石材，不再使用大块开裂岩石纹理。 |
| 铜钟与钟槌 | 钟体增加铸铜色差与微表面；钟槌采用 64 边圆润木体。新增钟身细环跟随原钟摆动。 |
| 水岸与林下 | 增加 43 处近岸小植物、139 处林下植物簇，根部贴合实际地形；避开已登记通行范围。自然山石法线强度降低，减轻过深裂纹。 |
| 检查镜头 | 仅调整 `CAM_ISLAND_SECTION` 正交检查相机，消除边角进入地形后的黑色内部面；不作为同镜头前后对照。 |

## 检查结果

- **PASS**：24 项结构检查。路径支撑、站立通行、独处小屋遮挡、户外单圆垫、书籍与相机登记保持通过。新植物没有触发通行阻挡。
- **PASS**：源模型检查没有缺失图片、非正缩放或退化源多边形。约 812 万实例展开基础三角形；较 p3-v028 增加约 1.3%。这不是移动端预算验收。
- **PASS**：31 个登记动作仍存在，32 个 Blender Action 数据块保留；新增钟环的父级正确，所有新增材质图片打包。
- **PASS**：跨目录重开，并在内存中移除外部图片／字体路径后完成渲染；使用的图片和字体均已打包，无链接库。没有把探针修改保存回主模型。
- **PASS**：44 张同版实际渲染，覆盖 29 相机与日间、左右手、暮色、夜间、雨天、关灯、晨景、夕景。逐组检查后，检查视角的黑面已消除，关灯时新纸灯不继续自发光。
- **PASS（桌面加载范围）**：新 GLB 已导出，铸铜程序材质在交换副本烘焙成 PBR 贴图；没有把交换适配保存进源模型。纸灯透光和复杂光照在浏览器仍为近似。完整模型、左右手切换与资源释放重载的实际证据另附本轮运行记录。

## 仍有的差距

视觉仍为 **PARTIAL**。植被树形与组合、远山云雾、建筑屋面和自然水岸的真实感仍低于参考；这轮修正以近景构造与材质为主，没有把已知差异写成通过。全景约 1.2 万次绘制调用，GLB 约 174 MB，仍需运行批次与 LOD 优化，未做手机／真机体验验收。

[修改前后与 v8 参考对照](../previews/final-review.html)默认打开抄写厅，左侧 p3-v028、右侧 p3-v031；可切换「v8 参考」及「只看当前模型」。除低岛检查相机外，前后对比使用相同镜头、灯光状态与渲染参数。

[Blender 主文件](../zen-space.blend) · [源版本快照](../snapshots/20261021/zen-space-p3-v031.blend) · [GLB](../exports/zen-space-p3-v031.glb)

p3-v028 的原始交付包、预览、视频和报告保留。旧视频未冒充本轮新渲染；本轮不重复交付动作视频。未提交、推送、部署或安装设备。
''';(E/'detail-review-20261021.md').write_text(report)
# Existing final report remains a historical record, with a current-status pointer.
p=E/'final-review.md';txt=p.read_text();notice='> 2026-10-21 已更新为 p3-v031 细节版。当前改动与验证见 [本轮报告](detail-review-20261021.md)。下文保留 p3-v028 的交付记录。\n\n'
if not txt.startswith('> 2026-10-21'):p.write_text(notice+txt)
p=R/'README.md';txt=p.read_text();txt=txt.replace('**p3-v028**','**p3-v031**').replace('snapshots/20261020/zen-space-p3-v028.blend','snapshots/20261021/zen-space-p3-v031.blend').replace('exports/zen-space-p3-v028.glb','exports/zen-space-p3-v031.glb').replace('evidence/final-review.md','evidence/detail-review-20261021.md');txt=txt.replace('离线双击 `previews/final-review.html` 可看静态对照和视频。','离线双击 `previews/final-review.html` 可看本轮修改前后与 v8 参考；上轮视频保留在历史交付页。');p.write_text(txt)
p=project/'README.md';txt=p.read_text();start=txt.index('当前交付版本为');txt=txt[:start]+'当前交付版本为 **p3-v031**（2026-10-21 细节打磨）。新增石材与桥缝、纸灯薄纸透光、坐垫包边、桌案榫接、茶器釉面、铜钟及林下小植物；44 张同版渲染可与 p3-v028 前后对照。技术检查 PASS，视觉仍为 PARTIAL。详见场景目录的当前图册与本轮报告。\n';p.write_text(txt)
p=M/'LICENSES.md';txt=p.read_text();note='\n## 2026-10-21 新增细节资产\n\n`assets/textures/detail-20261021/` 中细石、纸纤维、织物、釉面为本次原创程序材质；配方、随机种子和尺寸见同目录 source.json。铜钟贴图来自本模型程序材质的交换副本烘焙，不新增外部素材授权。\n'
if '2026-10-21 新增细节资产' not in txt:p.write_text(txt+note)
# Verify all literal links plus the complete dynamic gallery dataset.
links=[]
for item in json.loads((E/'detail-view-inventory-v031.json').read_text()):
 for field in ['image','before','reference']:
  if item.get(field):links.append({'link':item[field],'exists':(R/'previews'/item[field]).exists()})
for ref in re.findall(r'(?:src|href)="([^"]+)"',(R/'previews/final-review.html').read_text()):
 if not ref.startswith(('http','#')):links.append({'link':ref,'exists':(R/'previews'/ref).exists()})
assert all(x['exists'] for x in links),[x for x in links if not x['exists']];(E/'current-work-links.json').write_text(json.dumps({'version':V,'links':links,'pass':True},indent=2))
# Package current source and directly reviewable current / before / reference images; preserve project-relative links.
files=[project/'README.md',R/'README.md',master,R/'previews/final-review.html',R/'previews/final-review-p3-v028.html']+list(M.glob('*'))
files+=list(E.glob('detail*'))+[E/'current-work-manifest.json',E/'current-work-links.json',E/'structure-check-p3.json',E/'final-candidate-source-audit.json',E/'portability-check-p3-v031.json',E/'final-review.md']
files+=list((R/'previews'/V).glob('*-gpu/*.png'))+list((R/'previews/p3-v028').glob('*-gpu/*.png'))+list((R/'previews/p3-v028/motion').glob('*.mp4'))+list((R/'previews/runtime').rglob('*'))
files+=[R/'exports'/n for n in ['zen-space-p3-v031.glb','writing-left-layout-v001.glb','surface-kit-v001.glb','states-kit-v001.glb']]+list((R/'assets/fonts').glob('*'))+list((R/'assets/textures/detail-20261021').rglob('*'))
files+=list((project/'docs/design/style-studies-20261020/v8-first-person').glob('*.png'));files+=[snap/source.name,project/'docs/design/禅意空间-设计总纲-v1.2-Blender制作版-20261020.md'];files=sorted(set(p for p in files if p.is_file()));dest=project/'deliverables/zen-space-p3-v031-20261021.zip'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
 for p in files:z.write(p,p.relative_to(project))
with zipfile.ZipFile(dest) as z:assert z.testzip() is None
record={'version':V,'file':str(dest.relative_to(project)),'bytes':dest.stat().st_size,'sha256':sha(dest),'files':len(files),'archive_crc':'PASS'};dest.with_suffix('.json').write_text(json.dumps(record,indent=2));print('DETAIL_DELIVERY_READY',record,flush=True)
