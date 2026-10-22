from pathlib import Path
import json,shutil
R=Path(__file__).resolve().parents[1];P=R/'previews';p=P/'final-review.html';history=P/'final-review-p3-v048.html'
if not history.exists():shutil.copy2(p,history)
s=history.read_text();a=s.index('const items=')+len('const items=');b=s.index(';let index=',a);previous={v['name']:v for v in json.loads(s[a:b])};rows=[]
notes={
'CAM_GARDEN_COMPOSITION':'新增较近俯视构图（正交范围90m），前图由未修改的v048源文件按本机位重新渲染。横向连廊补齐院心前缘，四座主屋顶檐角局部抬起，低岛改成向水下展开的石岸。该构图与下一个“原机位全园总览”分开展示，避免把拉近镜头当作模型修理。',
'CAM_WORLD_FULL':'原机位、原正交范围108m保持，直接比较模型变化：新增院心横廊，屋顶檐角调整，低岛紧凑化，右侧观景台周围地形与植被重新衔接。桥端点、路线和台阶标高保持。高台侧壁和部分直岸仍显人工。',
'CAM_FRONT_GATE':'新增院心横廊和柱列从门前检查，主通路中央留空；主屋顶、瓦、脊、檐板、椽木与墙顶封檐共同调整，避免只抬瓦面留下漏空。',
'CAM_WATER_BRIDGE':'两座桥位置及端点保持；从近景复核右侧台地地形与既有景观的关系。近岸切面及石形仍需继续打磨。',
'CAM_ISLAND_SEATED':'坐姿相机、一个圆垫与平地标高保持。低岛恢复紧凑轮廓，侧岸分层向水下展开，撤回中间候选的直立圆台形态。',
'CAM_LIBRARY_SEATED':'复核横廊新增后的真实遮挡与四座主屋顶檐角调整，保留v048修好的墙顶封檐。室内陈设沿用既有模型。',
'CAM_ENTRY_SEATED':'从原临水坐姿复核新横廊与岛岸连接；路线支撑和站立净空检查通过。没有按俯视相机另放一套几何。'}
order=['CAM_GARDEN_COMPOSITION','CAM_WORLD_FULL','CAM_FRONT_GATE','CAM_WATER_BRIDGE','CAM_ISLAND_SEATED','CAM_LIBRARY_SEATED','CAM_ENTRY_SEATED']
for name in order:
 if name=='CAM_GARDEN_COMPOSITION':v=dict(name=name,title='俯视构图 · 庭院与水园',reference=previous['CAM_WORLD_FULL']['reference'])
 else:v=dict(previous[name])
 v.update(state='日间',image=f'p3-v050/day-gpu/{name}.png',before=f'p3-v048/day-gpu/{name}.png',notes=notes[name])
 if name=='CAM_GARDEN_COMPOSITION':v['before']='p3-v048-matched-v050/day-gpu/CAM_GARDEN_COMPOSITION.png'
 for k in ['image','before']:assert (P/v[k]).is_file(),v[k]
 rows.append(v)
s=s[:a]+json.dumps(rows,ensure_ascii=False)+s[b:];s=s.replace('禅意空间 · 全园质感复盘','禅意空间 · 俯视精修');s=s.replace('p3-v048 · 2026.10.21　｜　桥岸比例、屋顶、材质与林缘 · 12 张实际渲染','p3-v050 · 2026.10.22　｜　俯视构图与实际模型精修 · 7 张本轮渲染').replace('当前实际模型 · p3-v048','当前实际模型 · p3-v050').replace('修改前实际模型 · p3-v042','修改前实际模型 · p3-v048').replace('quality-retrospective-v048.md','overhead-refinement-review-v050.md').replace('黑神话参照 · 整园复盘','上一轮整体复盘 · v048').replace('锦鲤与猫游动短片','上一轮生灵短片 · v048').replace('播放锦鲤与猫的实际模型短片（12 秒）','上一轮生灵动作短片（v048，12 秒）')
s=s.replace('本轮总览与 v042 同机位；低岛坐姿跟随岛体移动，单独展示。','本轮原机位总览保持；新增俯视构图已按相同机位重渲染 v048 前图。')
s=s.replace('<a href="final-review-p3-v028.html">历史完整交付</a>','<a href="final-review-p3-v048.html">上一轮12视点 · v048</a>');p.write_text(s);(R/'evidence/repair-view-inventory-v050.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2));print('OVERHEAD_REVIEW',len(rows))
