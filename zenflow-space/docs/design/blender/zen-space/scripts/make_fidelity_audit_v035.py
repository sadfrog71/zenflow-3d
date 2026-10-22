"""Reference comparison only; never mutate Blender master or reference pixels."""
from pathlib import Path
import json,hashlib,html
R=Path(__file__).resolve().parents[1];V='p3-v035';E=R/'evidence/fidelity-audit-v035';E.mkdir(exist_ok=True)
# camera, reference, state, title, observed gap, proposed correction, visible acceptance
rows=[
('CAM_WORLD_FULL','00-overview-courtyard-blossoms.png','day','全园与屋顶','建筑之间是大片整齐铺地，外围是连续草坡；池岸呈折线墙，高台仍像独立平台。两座主屋顶是较整齐的大坡面，瓦面远看偏密，檐口与脊线层次弱于参考。','先重做池岸、后坡与建筑之间的景观断面；按主厅、廊顶、院门分别校正屋顶轮廓与瓦当尺度。','同一场景总览与廊下视角都读得出石岸—灌木—乔木层次；屋顶剪影、檐口和转角可辨，不靠色调掩盖。'),
('CAM_ENTRY_SEATED','01-waterside-veranda.png','day','01 临水廊下','近景大石占据水面，下方长桥横穿中心，瀑布贴着大直立石墙；参考是开阔水面、短桥、自然分层跌水与远山。当前天空偏深蓝，叶色整体偏暗。','优先调整岸石体量与桥、瀑布的视觉关系，保留通路和净空；校准日间天空、叶片透光和水面反射。','坐姿中水面仍是主体；前景、中景双桥和跌水、远景山体互不压成一面。'),
('CAM_WATER_BRIDGE','02-water-garden-bridge.png','day','02 双桥水园','桥横向跨度在画面中很长，岸墙与上游台阶侧面形成大直面；水体浅灰绿，池底几乎均匀可见，缺少参考的反射与深浅分区。','重构岩石断面，缩短视觉连续墙面；在实际水深与倒影之间建立材质层次，先用本视点做水面样板。','桥仍可通行，岸墙不主导画面；同帧可辨天空倒影、岸边浅水、较深水区和鱼。'),
('CAM_ISLAND_SEATED','03-island-meditation.png','day','03 低岛坐姿','近景大片平石和桥面，前方建筑窗格占据主要视线；参考是圆垫近缘、宽水面、对岸建筑全貌。当前这个取景没有呈现圆垫边缘。','在现有低岛和单圆垫约束下复核坐姿位置、朝向、镜头，以及桥与对岸廊的视觉遮挡。','单圆垫仍直接落在低平岛面；视点低、近水，有垫缘作为坐姿提示，不新增台座或靠背。'),
('CAM_LIBRARY_SEATED','04-library-seat.png','day','04 藏书阅读厅','当前以窗格和外部直岸墙为主，桌面只占角落，基本看不到沿墙书架与厅内柱列纵深；窗格密度和参考也不同。','复核阅读位朝向及开窗分段，让低案、书架、纵深和水园能在同一观看逻辑中成立。','在不把书架移到画外虚构位置的前提下，坐姿能辨认阅读厅，并保留取书距离和通路。'),
('CAM_COPY_VIEW','05-copy-hall-seated.png','day','05 抄经厅','纸灯在近景中央占比大，正前门洞把视线导出厅外；参考以案面、成排低案和柱列向厅内延伸。地板有光泽，但缺少参考中受光与暗部的层次。','先校正案前观看方向和灯在画面中的占比，再校正木面反射宽度、纸灯透光和座垫织物。','低案、笔墨、纸面为近景主体，柱列和至少两排低案提供纵深；纸灯为局部暖光。'),
('CAM_PRIVATE_SEATED','06-private-side-room.png','day','06 独处侧室','浅水槽、纸和门窗均可辨，但水槽像厚壁矩形石箱；窗外裸坡与规则树带明显。薄帘近似整块平板，缺少透光织纹。','保留浅水槽功能，细化槽口、水缘及薄帘；补窗外近庭叶石，校正局部日光。','近庭而非远处空坡成为窗景；水槽读得出水面与石边厚度，帘布可透光。'),
('CAM_BELL_COURT','07-bell-court.png','day','07 钟庭','钟、槌、石钵和背景墙存在，但铜钟偏亮金属、轮廓与纹饰简化；石钵更像厚实灰盆。围墙脚、台阶旁和庭地过于空。','校正钟的轮廓与铸铜粗糙度，补石钵水面、局部石缝与墙脚植物。','钟有暗铜与磨损高光的区别，钵口和水面可辨；背景墙保留且有庭院层次。'),
('CAM_WISH_APPROACH','08-wish-tree-platform.png','day','08 许愿树','已挂签并有带顶取签盒，但主干细长斜直，树冠与庭园背景很暗；取签盒瓦顶更像平滑帽，铺地和上坡边缘机械。','保持木签空白和低枝可达，调整主树剪影、根部、取签盒屋顶与台边石阶；以疏密变化组织背后景观。','不与中央景观树混用功能；树、低签、取签盒和石座在到达视点能共同辨认。'),
('CAM_OLD_TREE_PATH','09-old-tree-path.png','day','09 景观大树','巨大的近景树干截断画面，根部像树干直接插入土面；地被叶片有平面贴片感。参考可辨根系包石、主干分枝和左右分流山径。','保持无挂签，制作主树根盘、树干转折与岩土接触，再复核山径视角。','树根确实连接土石；两侧路径及周围空间可读，不以一根大树干遮住关键关系。'),
('CAM_LOOKOUT_E','10-right-lookout-dawn.png','dawn','10 观景台 · 晨景','本轮补渲染晨景后，太阳与雾确实存在；但雾面平整、山坡光滑、树带横向均齐，远处只有少量轮廓。近景台阶很抢眼，缺少参考石座/低石沿与层叠远山。','先塑造前中远山脊与谷地，再分层树群和雾密度，校正坐姿朝向及石座近缘。','晨景同时出现近林、中景山体、远山和谷雾四层；日间关雾后山形仍成立，不能只靠雾遮住。'),
('CAM_CHAT_SEATED','11-conversation-cottage-seat.png','day','11 对话小屋','有灯和窗，但整齐窗格后主要是裸坡和树带；参考是近处茂密叶石、斑驳日照和温暖室内。当前纸灯轮廓也偏几何体。','优先补东侧小屋自己的近庭景观，再校正窗扇、纸灯和光斑，保留椅中坐姿。','窗外是邻近叶石，不将西侧池塘搬来；灯保留纸质散射，室内外层次清晰。'),
('CAM_FRONT_GATE','12-front-court-threshold.png','day','12 南门前庭','双厅与中央通路已成立，但入口两侧绿化少、地面过整，门窗和屋檐构件显得重复；参考的檐下层次与门洞收景更丰富。','保留共用前庭和中央通路，校正门窗分段、檐口构件尺度及局部植栽，不照搬参考中可能冲突的台阶。','中央通路连续、没有多余矮墙；入口先读到双厅，再读到后院轴线，层次不依赖满铺装饰。'),
('CAM_LIBRARY_SHELF','13-bookshelf-select.png','day','13 书架选书','四本书名正确且可辨，这是相对接近的功能点；但当前书封近似平整厚盒，书页、布面和线装细节弱，正面平视也缺少参考侧向纵深。','保留真实书名和标识，补页口、包边、线装与布面；复核侧向选书镜头但不牺牲可读性。','四个书名仍可辨；翻页层次与封面材质有区别，不依赖把字做成不可编辑贴图。'),
]
findings=[
('F01','P1','整体景观仍显拼装','CAM_WORLD_FULL','大面积空铺地与草坡分割建筑、水园、高台。','先定景观断面与植物层次，再补小物。'),
('F02','P1','水园保留明显工程直面','CAM_WATER_BRIDGE','直岸、长桥和大平瀑布背墙主导水园。','不移动功能区，分段调整岸线、岩石与桥头过渡，重做水体样板。'),
('F03','P1','屋顶仍未达到参考轮廓与构造层次','CAM_FRONT_GATE','端部翘起及脊线简化；正面瓦片呈密集重复波浪，檐下椽条也像重复圆棒。','先选一座主厅样板，核对脊、坡、转角、瓦当、檐下的尺度和分层。'),
('F04','P1','远山与观景台晨景还原不足','CAM_LOOKOUT_E','晨景虽有雾，但树带、山坡与雾面缺少多层空间。','先山形与观看方向，后树群与大气。'),
('F05','P1','关键坐姿构图偏离参考','CAM_LIBRARY_SEATED','阅读、低岛、抄写的视觉主体及纵深与参考不同。','从真实坐姿重核相机和遮挡；禁止只为某镜头挪动世界物件。'),
('F06','P1','材质与照明没有统一目标风格','CAM_ENTRY_SEATED','室外深蓝天空、暗绿树冠与浅灰石材对比生硬，水色偏乳灰；参考为通透暖日光。','用廊下水园做统一照明样板，校正色彩、粗糙度、叶片透光和接触阴影。'),
('F07','P2','主树、近景石材和器物完成度不一','CAM_OLD_TREE_PATH','树根与地面衔接、地被卡片、铜钟、石钵等在近景仍明显简化。','按镜头距离确定细节密度；大树根盘优先于远处小物。'),
('F08','P2','新增动物与环境风格仍有差异','CAM_POND_LIFE','五条锦鲤与三条红色已存在；猫偏玩偶式，鱼尾部分角度有图集曲面感，交互水面与实际渲染差异明显。','保留数量和动作，环境样板确定后再校正猫体态、表面及鱼鳍表现。'),
]
data={'version':V,'date':'2026-10-21','source_sha256':hashlib.sha256((R/'zen-space.blend').read_bytes()).hexdigest(),'source_mutated':False,'reference':'v8 latest approved concept + later explicit constraints','method':'14 semantic view pairs, no pixel similarity score; cameras and projections differ; extra dawn render used for dawn review','status':'PARTIAL_MAJOR_VISUAL_GAPS','findings':[dict(zip(['id','priority','title','camera','evidence','action'],f)) for f in findings],'views':[dict(zip(['camera','reference','state','title','gap','action','acceptance'],r)) for r in rows]}
(E/'audit.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
md=['# p3-v035 还原度比对审查','日期：2026-10-21。结论：**PARTIAL，存在明显视觉差距**。屋顶穿插修补、锦鲤数量和动作已完成，不代表整体景观、建筑气质和材料还原已通过。','## 依据与边界','当前主文件 SHA256：`'+data['source_sha256']+'`。本轮模型只读；补渲染 10 个日间场所及 1 个晨景视点，使用已保存的同一源文件。参照 v8 最新总图及 13 张场所图，并服从后来确认的单圆垫、无台座、红色锦鲤与猫等要求。','概念图不是统一相机拍摄的三维真值，不能据此算一个可信的还原百分比。以下是用途、轮廓、构图、材质与气氛的定性审查；不把透视差别直接判为模型尺寸错误。现有 24 项结构 PASS 只说明对应技术检查，不能覆盖本次审美判断。','## 主要问题与顺序','| 编号 | 优先级 | 问题 | 可见证据 | 建议 |','| --- | --- | --- | --- | --- |']
for f in findings:md.append('| '+' | '.join([f[0],f[1],f[2],f[4],f[5]])+' |')
md+=['','P1 表示直接改变场所和风格辨识，应先修；P2 在整体样板通过后处理。不用功能存在或面数增加代替视觉完成度。','## 按场所复核']
for cam,ref,state,title,gap,action,accept in rows:
 md += ['### '+title,'[当前实际渲染](../../previews/p3-v035/'+state+'-gpu/'+cam+'.png) · [v8 参考](../../../../../style-studies-20261020/v8-first-person/'+ref+')','- 观察：'+gap,'- 修正建议：'+action,'- 复核条件：'+accept,'']
md+=['## 下一轮最有效的制作顺序','1. 先以总览和临水廊下为同一世界的两个验证视点，修池岸、跌水、建筑脚部与后坡，形成一段完整自然景观样板。','2. 一座主厅做屋顶样板：总览看轮廓，入口看瓦当/檐口，室内看椽梁，三者共用几何。','3. 统一暖日光、蓝绿水面、浅石与叶片透光；在相同视点锁定曝光，不用逐相机调灯掩盖问题。','4. 修远山与晨景；再校正阅读、低岛、抄写和小屋的坐姿构图。','5. 最后处理铜钟、石钵、猫、书册等近景细节。','', '本轮只交付审查与对照材料，未修改主模型、提交、推送或部署。']
(E/'review.md').write_text('\n\n'.join(md))
# Standalone image pairs; originals remain untouched. No fake aligned slider.
css='''*{box-sizing:border-box}body{margin:0;background:#ece9e0;color:#283b31;font:15px/1.7 -apple-system,BlinkMacSystemFont,"PingFang SC",sans-serif}header{padding:32px max(24px,5vw);border-bottom:1px solid #c8ccbe}h1{font:400 32px Georgia,"Songti SC",serif;margin:0 0 12px}h2{font:400 25px Georgia,"Songti SC",serif;margin:0}p{max-width:1100px}a{color:#3d624e}nav{display:flex;gap:10px;flex-wrap:wrap;padding:18px max(24px,5vw);position:sticky;top:0;background:#ece9e0ed;z-index:2;backdrop-filter:blur(8px)}nav a{border:1px solid #b9c3b2;padding:4px 10px;border-radius:4px;text-decoration:none}main{padding:0 max(24px,5vw)}article{scroll-margin-top:90px;padding:30px 0;border-top:1px solid #c8ccbe}.pair{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin:18px 0}figure{margin:0;background:#25382e;border-radius:5px;overflow:hidden}img{display:block;width:100%;aspect-ratio:16/9;object-fit:contain}figcaption{padding:7px 12px;color:#e1e5d8;font-size:12px}.notes{display:grid;grid-template-columns:1.5fr 1fr;gap:24px}strong{font-weight:600}summary{cursor:pointer}.tag{font:12px system-ui;color:#8d5039}.limitations{color:#637262;font-size:13px}.finding{padding:12px 0;border-bottom:1px solid #d5d8cd}.finding b{color:#88533c}footer{padding:28px max(24px,5vw)}@media(max-width:900px){.pair,.notes{grid-template-columns:1fr}nav{position:static}h1{font-size:27px}}'''
parts=['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>禅意空间 · 还原度审查 p3-v035</title><style>'+css+'</style><header><span class="tag">2026.10.21 · p3-v035 · 模型只读审查</span><h1>场所已经建立，景观与质感仍有明显差距</h1><p>对照 v8 总图及 13 个场所视点。主要差距集中在水园岸线、屋顶层次、植被组织、远山，以及坐姿构图。结构检查通过不能代替还原度验收。</p><p class="limitations">概念图与模型的相机、透视并不一致，因此采用并排全图和具体可见差异，不提供虚假的像素相似度百分比。晨景使用当前源文件另行渲染；锦鲤与猫按后来追加要求评审。</p><a href="../evidence/fidelity-audit-v035/review.md">完整审查记录</a> · <a href="final-review.html">当前模型图册</a></header><nav>']
for i,r in enumerate(rows):parts.append('<a href="#view'+str(i)+'">'+html.escape(r[3])+'</a>')
parts+=['</nav><main><section><h2>先处理的八项差距</h2>']
for f in findings:parts.append('<div class="finding"><b>'+f[0]+' · '+f[1]+'</b>　<strong>'+html.escape(f[2])+'</strong><br>'+html.escape(f[4])+'<br><span class="limitations">'+html.escape(f[5])+'</span></div>')
parts.append('</section>')
for i,(cam,ref,state,title,gap,action,accept) in enumerate(rows):
 image=f'p3-v035/{state}-gpu/{cam}.png';reference=f'../../../style-studies-20261020/v8-first-person/{ref}';assert (R/'previews'/image).exists();assert (R/'previews'/reference).exists()
 parts.append(f'<article id="view{i}"><h2>{html.escape(title)}</h2><div class="pair"><figure><a href="{reference}" target="_blank"><img loading="lazy" src="{reference}" alt="v8 概念参考"></a><figcaption>v8 概念参考 · 点击原图</figcaption></figure><figure><a href="{image}" target="_blank"><img loading="lazy" src="{image}" alt="当前实际模型"></a><figcaption>p3-v035 实际模型 · {state} · 点击原图</figcaption></figure></div><div class="notes"><div><strong>可见差距</strong><p>{html.escape(gap)}</p><strong>建议修改</strong><p>{html.escape(action)}</p></div><div><strong>下次怎样复核</strong><p>{html.escape(accept)}</p></div></div></article>')
parts.append('</main><footer>本轮补齐当前版本渲染，未修改 Blender 主模型；源文件校验值与逐项记录保存在审查文件中。</footer></html>');(R/'previews/fidelity-audit-v035.html').write_text(''.join(parts));print('FIDELITY_AUDIT',len(rows),'view pairs',len(findings),'findings')
