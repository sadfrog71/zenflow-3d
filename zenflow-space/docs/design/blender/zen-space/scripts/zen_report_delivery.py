from pathlib import Path
import json,hashlib,shutil,csv
R=Path('/Users/mark.ma/Documents/AI coding/zenflow-space/docs/design/blender/zen-space');E=R/'evidence';M=R/'manifest'
a=json.loads((E/'final-candidate-source-audit.json').read_text());checks=json.loads((E/'structure-check-p3.json').read_text());print('checks',len(checks['checks']),[(c['requirement'],c['status']) for c in checks['checks'] if c['status']!='PASS'])
rows=[('全园总览（两张参考）','CAM_WORLD_FULL','院落、双厅、水园、山径与外部林地形成同一空间；保留真实屋顶。','场地轮廓、建筑比例与概念图不完全重合；植物配置更规则，远山层次仍简化。'),('临水廊下','CAM_ENTRY_SEATED','木柱、近岸茶具、桥、叠瀑与池水已实现。','瀑布与岸石仍有程序化重复，参考中自然湿润的植被密度更高。'),('水园桥','CAM_WATER_BRIDGE','桥与连续池岸可达，水层、浮叶和叠瀑相连。','石桥纹理偏均匀，岸线和水体光学未达到参考照片感。'),('低岛坐姿','CAM_ISLAND_SEATED','低岛圆垫、低桥、对岸厅堂和坐姿视线具备。','岛面偏平整，近景石材尺度和池岸植物与参考有差距。'),('阅读厅','CAM_LIBRARY_SEATED','临窗坐姿能见池水，木格、地板、茶杯与书架齐备。','木格密度和家具轮廓有所适配，细部磨损与布料褶皱较少。'),('抄经厅','CAM_COPY_VIEW','低案、木构、纸灯、留白纸面、左右手布局具备。','近景灯体偏突出，案列与参考透视不完全一致，纸面不含真实经文。'),('独处侧室','CAM_PRIVATE_SEATED','薄帘、石槽、矮桌与独处坐姿实现。','帘褶及水槽表现简化，材质精细度低于参考。'),('钟庭','CAM_BELL_COURT','悬钟、支架、石钵、庭院与连通路径实现。','钟体与木架纹饰简化；石铺地重复明显。'),('许愿树','CAM_WISH_APPROACH','可达台阶、低枝、空签、收纳盒与读签近景实现。','树冠形态与参考树不同；签为无私人内容的独立内容面。'),('老树山径','CAM_OLD_TREE_PATH','老树、树穴、石径与周围树林实现。','树种、树根及地被组合与参考有差距。'),('东向晨景','CAM_LOOKOUT_E','观景石台、连续山谷与晨雾实现。','山谷轮廓更简化，雾层和远景仍有明显渲染风格。'),('对话小屋','CAM_CHAT_SEATED','椅中视角、木格、桌灯和朝向林地的窗口实现。','室内织物和窗外庭景层次低于参考；不包含聊天业务实现。'),('前庭门槛','CAM_FRONT_GATE','共用前庭、台阶与左右厅入口实现。','建筑装饰和景观密度简化，尺度按可达性协调。'),('选书近景','CAM_LIBRARY_SHELF','四本书及选择锚点、书名和内容承载面实现。','封面纹理和纸张装订简化，不附带阅读业务。')]
text='''# 禅意空间 p3-v028：模型交付与还原度自检

## 结论

模型绘制、同版渲染、参考对照和自检已完成。技术检查 **PASS**；效果图还原 **PARTIAL**。这是可继续编辑的完整场景，不是照片级复刻；审美认可仍由用户确认。没有可靠、统一的像素对应关系，不给出还原率百分比。

源文件：`zen-space.blend`，对应保留版本 `zen-space-p3-v028.blend`。Blender 5.2.2 LTS；单位为米。29 个相机、12 个区域；44 张实际渲染覆盖日间全部视点、左右手布局、四核心视角的暮色／夜间／雨天、晨景、夕景和关灯。全园、剖视、第一视角共用几何；没有为每张图另搭场景。参考共有 15 张，其中两张是全园总览。

## 逐场所对照

| 场所 | 实际相机 | 已实现 | 剩余差异（均为 PARTIAL） |
| --- | --- | --- | --- |
'''
for name,cam,done,gap in rows:text+=f'| {name} | `{cam}` | {done} | {gap} |\n'
text+='''
## 技术与状态检查

- `structure-check-p3.json`：24 项 PASS，含区域、路径、站坐视线、私密遮挡、书名朝向、左右手占用与基础结构检查。此类检查不代表审美验收。
- `final-candidate-source-audit.json`：8892 个可见网格对象，2824 个独立网格；实例展开基础三角形约 802 万。无缺失图片、非正缩放或退化源多边形；薄叶、纸、水有意使用开放表面，不宣称全部封闭实体。
- 31 个命名动作保留；包括水、鸟、帘、灯、翻书、签、钟与交互对象。20／60 秒视频用于观察场景动态，不表示所有动作默认同时播放。内容面为空白或示例，无私人正文。
- 日暮、夜间、雨天、关灯、晨景和夕景均已有真实渲染。夜间外部较暗，保留适应夜景的方向；尚未做目标设备亮度与舒适度验收。减动态状态属于源模型／状态设计，应用设置联动未接入。
- 全园 GLB、左手布局、表面／状态小样均已导出。桌面 WebGL2 实测完成加载、视点切换、左右手切换、释放及重载。全景约 11424 次绘制调用、1745 万渲染三角形（包含透射等额外通道），浏览器帧间隔约 33–35ms；这是当前浏览器环境观察，不能视为手机性能结论。仍需 LOD、批次合并与设备预算优化。
- 浏览器光照使用近似照明及不同色彩处理，不能把运行预览与 Cycles 渲染视为完全一致。动作、天气和复杂材质的应用重建边界见状态清单；没有实现写字、阅读或聊天业务。
- 便携性独立检查见 `portability-check-p3-v028.json`：复制至不同目录重开，并在内存中把外部图片与字体路径改为不存在的位置后渲染；不保存探针修改。所有使用的图片和字体须已打包，且无链接库。此检查不替代其它 Blender 版本兼容测试。

## 观看与编辑

[实际模型与 v8 对照图册](../previews/final-review.html)提供全尺寸图；[交互预览](../previews/runtime/full-model.html)需要本地 HTTP 服务。Blender 为正式编辑源。GLB 是运行派生文件，不能替代源模型的可编辑集合、灯光、状态库及材质节点。

历史失败截图和中间版本保留在原目录。未提交、推送、部署或安装设备。
'''
(E/'final-review.md').write_text(text)
# Preserve historical registers, then promote the verified candidate only if the previous master is unchanged.
old='f25a828fc37f25bb59229defb23d4d41e72532b1e2b8cf4bc8a070b85be0c2f8';master=R/'zen-space.blend';candidate=R/'zen-space-p3-v028.blend';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();candidate_sha=sha(candidate)
if sha(master)!=candidate_sha:
 assert sha(master)==old,'Master has external changes; promotion refused'
 shutil.copy2(candidate,master)
snapshot=R/'snapshots/20261020/zen-space-p3-v028.blend';shutil.copy2(candidate,snapshot)
for name in ['camera-register.csv','asset-register.csv','path-register.csv']:
 hist=M/(name.replace('.csv','-before-final.csv'))
 if not hist.exists():shutil.copy2(M/name,hist)
shutil.copy2(M/'asset-register-current.csv',M/'asset-register.csv')
m=json.loads((M/'scene-manifest.json').read_text());cams=m['cameras']
with (M/'camera-register.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(cams[0]));w.writeheader();w.writerows(cams)
for name in ['object-animation-clips.json','weather-and-layout-states.json']:
 p=M/name;d=json.loads(p.read_text());d['verified_source_version']='p3-v028';p.write_text(json.dumps(d,ensure_ascii=False,indent=2))
d={'version':'p3-v028','status':'MODEL_AND_SELFCHECK_DELIVERED_VISUAL_PARTIAL','master':'zen-space.blend','source_snapshot':candidate.name,'dated_snapshot':str(snapshot.relative_to(R)),'sha256':candidate_sha,'snapshot_matches':sha(snapshot)==candidate_sha,'technical_checks':len(checks['checks']),'technical_status':'PASS','visual_fidelity':'PARTIAL','rendered_views':44,'device_application':'NOT_VERIFIED','report':'final-review.md'};(E/'current-work-manifest.json').write_text(json.dumps(d,ensure_ascii=False,indent=2))
readme='''# Zenflow Space · Blender 场景

当前交付版本 **p3-v028**：模型与自检完成，技术 PASS，视觉还原 PARTIAL。保留照片级细节与设备性能限制，详见报告。

- [实际渲染与参考对照图册](previews/final-review.html)：44 张同版渲染，29 个相机，左右手与多状态。
- [Blender 可编辑主文件](zen-space.blend) · [带日期快照](snapshots/20261020/zen-space-p3-v028.blend)
- [自检报告及逐场所差异](evidence/final-review.md) · [当前版本和校验值](evidence/current-work-manifest.json)
- [全园 GLB](exports/zen-space-p3-v028.glb) · [交互预览](previews/runtime/full-model.html)（需 HTTP 服务）
- [场景清单](manifest/scene-manifest.json) · [相机](manifest/camera-register.csv) · [当前资产](manifest/asset-register.csv) · [路线](manifest/path-register.csv)
- [动作库](manifest/object-animation-clips.json) · [天气和布局](manifest/weather-and-layout-states.json) · [第三方许可](manifest/LICENSES.md)

## 打开与编辑

Blender 5.2.2 LTS，米制，+X 东、+Y 北、+Z 上。使用主文件继续编辑；各中间版本及第一期灰模保留为历史。图片与使用的 Noto 字体已打包，无链接库。`WORLD_FULL` 保留全屋顶，`LAYOUT_CUTAWAY` 为指定集合剖视，第一视角保留实际遮挡。相机位于 `70_CAMERAS`，交互对象和内容面单独组织。

重复对象共享网格，修改单件前按需 Make Single User。修改后另存下一版本；生成脚本不应用于清空已有主文件。GLB 不包含完整 Blender 灯光、节点、状态与业务能力，不能替代源文件。

## 本地观看

离线双击 `previews/final-review.html` 可看静态对照和视频。交互 3D 需要从本项目根目录启动本地 HTTP 服务，再打开 `/docs/design/blender/zen-space/previews/runtime/full-model.html`。现有预览服务端口为 18764；若服务已关闭需重启。

全景模型较重，当前只验证桌面浏览器；未验收 iOS、真机触摸、声音或舒适度。没有应用业务实现、提交、推送或部署。独立目录不影响原 Zenflow 项目。
''';(R/'README.md').write_text(readme)
root=R.parents[3];p=root/'README.md';txt=p.read_text();idx=txt.find('当前制作主文件为');txt=txt[:idx] if idx>=0 else txt;txt+='当前交付版本为 **p3-v028**。模型、44 张实际渲染、参考对照、状态与动作预览及自检报告已整理。技术检查 PASS；视觉还原 PARTIAL，尚有植被、建筑及石材细节差距；桌面浏览器已验证，目标设备未验证。详见场景目录中的最终图册和报告。\n';p.write_text(txt)
(E/'model-goal-acceptance.md').write_text('# Goal 交付状态\n\n模型绘制、实际参考对照和自检已交付；视觉还原 PARTIAL，未宣称用户审美验收或照片级一致。\n\n详见 [最终报告](final-review.md) 与 [当前版本](current-work-manifest.json)。\n')
print(candidate_sha,'promoted; reports and registers updated')
