"""Publish actual v042 images with a genuinely matched v039 overview baseline."""
from pathlib import Path
import json,shutil,re
R=Path(__file__).resolve().parents[1];P=R/'previews';old=P/'final-review.html'
if not (P/'final-review-p3-v039.html').exists():shutil.copy2(old,P/'final-review-p3-v039.html')
s=old.read_text();a=s.index('const items=')+len('const items=');b=s.index(';let index=',a);rows=json.loads(s[a:b])
notes={
'CAM_WORLD_FULL':'东侧新增共用廊顶模块及院门，院心和前庭增加曲线种植区；高台以连续坡肩衔接，西岸路线与岸线共同弯曲。修改前图为 v039 按本轮相同相机重新渲染。桥跨仍偏长，屋顶体量及规则草地与参考仍有明显差距。',
'CAM_ENTRY_SEATED':'西岸路线、地形和岸边物件同步变形，保留桥端落点、标高和路线宽度要求。池岸仍偏工整，水面与植被细节尚未完全接近参考。',
'CAM_WATER_BRIDGE':'从原视点复核西岸变形及坡地衔接。两座桥本轮未缩短；桥跨占比仍是开放问题。',
'CAM_FRONT_GATE':'增加前庭低矮种植岛，复用原有屋顶构件建立院内围合。两座大殿的屋顶体量和瓦面尺度仍有差距。',
'CAM_LIBRARY_SEATED':'保持 v039 坐姿和观看方向，检查新增廊线及种植的实际遮挡。室内纵深和陈设还原仍待后续调整。',
'CAM_COPY_VIEW':'复核共用模型的院心与东廊调整；本轮未重做案前陈设。',
'CAM_POND_LIFE':'实际模型仍为五条锦鲤、三条带红色，两只猫；17 段生灵动作保留。短片由本轮源文件重新渲染。',
'CAM_ISLAND_SEATED':'保留一个低岛圆垫及原坐姿方向，西岸线路同步弯曲。桥跨与岸线的尺度关系仍未完全接近参考。',
'CAM_CHAT_SEATED':'复核同一场景中的新增围合与种植；小屋近景陈设未重做。',
'CAM_LOOKOUT_E':'本轮未改远山和云海几何；坡地过渡共用日间模型，不用更换背景冒充还原通过。',
'CAM_WISH_APPROACH':'许愿树冠加宽并增加坡肩灌木，保留原树根、低枝和可达挂签；减少孤立高台感，岩壁仍有规则感。',
'CAM_OLD_TREE_PATH':'后坡主树树冠加宽、坡脚植被补密。路径共用实际模型，保留原台顶标高。'}
for row in rows:
 name=row['name'];row['image']=row['image'].replace('p3-v039','p3-v042');row['before']=f'p3-v039/{"dawn" if row["state"]=="晨景" else "day"}-gpu/{name}.png';row['notes']=notes[name]
 if name=='CAM_WORLD_FULL':row['before']='p3-v039-matched-v041/day-gpu/CAM_WORLD_FULL.png'
for name,title,ref in [('CAM_WISH_APPROACH','08 许愿树 · 台地过渡','08-wish-tree-platform.png'),('CAM_OLD_TREE_PATH','09 后坡 · 主树与路径','09-old-tree-path.png')]:
 rows.append(dict(name=name,title=title,state='日间',image=f'p3-v042/day-gpu/{name}.png',before=f'p3-v039/day-gpu/{name}.png' if (P/f'p3-v039/day-gpu/{name}.png').exists() else None,reference=f'../../../style-studies-20261020/v8-first-person/{ref}',notes=notes[name]))
for row in rows:
 assert (P/row['image']).is_file(),row['image']
 if row['before']:assert (P/row['before']).is_file(),row['before']
s=s[:a]+json.dumps(rows,ensure_ascii=False)+s[b:]
s=s.replace('p3-v039 · 2026.10.21　｜　景观与屋顶修理 · 10 张实际渲染','p3-v042 · 2026.10.21　｜　围合、岸线与坡地修理 · 12 张实际渲染').replace('当前实际模型 · p3-v039','当前实际模型 · p3-v042').replace('修改前实际模型 · p3-v035','修改前实际模型 · p3-v039').replace('p3-v039/motion/living-garden.mp4','p3-v042/motion/living-garden.mp4').replace('fidelity-repair-review-v039.md','fidelity-repair-review-v042.md').replace('本轮总览相机与上轮一致；阅读位方向改变，单独展示。','本轮总览相机调整，修改前图已按同机位重渲染；其他视点与 v039 保持。')
s=s.replace('禅意空间 · 细节打磨','禅意空间 · 景观还原修理');old.write_text(s)
(R/'evidence/repair-view-inventory-v042.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
print('GALLERY',len(rows))
