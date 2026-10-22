from pathlib import Path
import json,shutil,html
R=Path(__file__).resolve().parents[1];P=R/'previews';old=P/'final-review.html'
if not (P/'final-review-p3-v042.html').exists():shutil.copy2(old,P/'final-review-p3-v042.html')
s=old.read_text();a=s.index('const items=')+len('const items=');b=s.index(';let index=',a);rows=json.loads(s[a:b]);notes={
'CAM_WORLD_FULL':'同机位比较 v042：双桥缩短，岸线、低岛和路面一起变形；四座主屋顶收短檐部进深、提高屋脊，外缘新增成组林缘植被。屋面更深色、阴影更明确，但古建连接细节与地形切面仍未达到质量参照。',
'CAM_FRONT_GATE':'四座主屋顶的瓦、脊、檐板、椽子共同变形，补充椽木长度方向 UV，按实际屋面高度补齐四座建筑墙顶封檐。木材减少过亮增益。近景柱梁接缝及檐下构造仍待细化。',
'CAM_ENTRY_SEATED':'下桥从约10.5m缩到7.5m，低岛向东移动3m，实际路面与岸线跟随；水线附近岸石更暗、更润。部分直岸与重复石形仍保留差距。',
'CAM_WATER_BRIDGE':'上桥从约17.5m缩到8.25m，桥面石板、石栏与拱券接缝同步。日光与环境填光重新平衡，岸石具有随高度变化的干湿反光。',
'CAM_ISLAND_SEATED':'低岛移动，相机跟随，方向和坐姿高度保留；此前视图因位置不同，不作为同机位比较。保留一个低圆垫，低岛边界仍需继续修形。',
'CAM_POND_LIFE':'五条锦鲤、三条带红色，仍用原有体积模型与17段生灵动作。石材干湿关系改变，鱼体和水面使用实际模型渲染。',
'CAM_LIBRARY_SEATED':'同一场景中的屋顶与共用日照、石木材质调整；室内纵深和柱梁构造仍为开放问题。',
'CAM_COPY_VIEW':'共用屋顶和木材材质更新；本轮未重做案上陈设，避免把共用材质变化当作陈设还原完成。',
'CAM_CHAT_SEATED':'共用小屋屋顶比例和木材更新，建筑外缘补充成组林缘植被。室内接缝和陈设仍需进一步修理。',
'CAM_WISH_APPROACH':'全园石材、地面和日光调整；保留原台顶和可达挂签，路线结构复检通过。高台切面和石形仍有人工感。',
'CAM_OLD_TREE_PATH':'地面增加缓慢的色调和粗糙度变化，保留已有主树和台地标高；地表纹理重复仍未完全消除。',
'CAM_LOOKOUT_E':'原生晨景再次渲染，远山和云海仍为主要开放差距。本轮日间光照调整不代替晨景还原。'}
for row in rows:
 n=row['name'];state='dawn' if row['state']=='晨景' else 'day';row['image']=f'p3-v048/{state}-gpu/{n}.png';row['before']=f'p3-v042/{state}-gpu/{n}.png';row['notes']=notes[n]
 if n=='CAM_ISLAND_SEATED':row['before']=None
 assert (P/row['image']).is_file(),row['image']
 if row['before']:assert (P/row['before']).is_file(),row['before']
s=s[:a]+json.dumps(rows,ensure_ascii=False)+s[b:]
s=s.replace('p3-v047 · 2026.10.21　｜　桥岸比例、屋顶、材质与林缘 · 12 张实际渲染','p3-v048 · 2026.10.21　｜　桥岸比例、屋顶、材质与林缘 · 12 张实际渲染').replace('当前实际模型 · p3-v047','当前实际模型 · p3-v048').replace('修改前实际模型 · p3-v042','修改前实际模型 · p3-v042').replace('p3-v047/motion/living-garden.mp4','p3-v048/motion/living-garden.mp4').replace('quality-retrospective-v047.md','quality-retrospective-v048.md').replace('本轮总览相机调整，修改前图已按同机位重渲染；其他视点与 v039 保持。','本轮总览与 v042 同机位；低岛坐姿跟随岛体移动，单独展示。').replace('禅意空间 · 景观还原修理','禅意空间 · 全园质感复盘')
s=s.replace('quality-retrospective-v047.html','quality-retrospective-v048.html')
old.write_text(s);(R/'evidence/repair-view-inventory-v048.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
style='''*{box-sizing:border-box}body{margin:0;background:#eeeae1;color:#27392f;font:15px/1.8 -apple-system,BlinkMacSystemFont,"PingFang SC",sans-serif}main{max-width:1500px;margin:auto;padding:36px}h1,h2{font-family:Georgia,"Songti SC",serif;font-weight:400}h1{font-size:34px;margin:0}h2{margin-top:44px;font-size:25px}.meta{color:#697165}.grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.refs{display:grid;grid-template-columns:1fr 1fr 1fr;gap:16px}figure{margin:0;background:#26352d;color:#e3e8dd}img{width:100%;display:block;aspect-ratio:16/9;object-fit:contain}figcaption{font-size:13px;padding:12px}a{color:inherit;text-underline-offset:4px}table{border-collapse:collapse;width:100%;margin:20px 0}td,th{border-bottom:1px solid #bdc7b5;text-align:left;vertical-align:top;padding:16px 12px}.note{border-left:3px solid #718369;padding:12px 20px;max-width:1100px}.status{color:#7c5d33}@media(max-width:850px){.grid,.refs{grid-template-columns:1fr}main{padding:20px}table{font-size:13px}td,th{padding:10px 6px}}'''
def fig(src,caption):return '<figure><img loading="lazy" src="'+src+'"><figcaption>'+caption+'</figcaption></figure>'
parts=['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>全园复盘 · p3-v048</title><style>'+style+'</style><main><p class="meta">ZEN SPACE / P3-V048 / 2026.10.21</p><h1>从布局到质感，重新看整座园子</h1><p><a href="final-review.html">进入12视点对比图册 →</a>　<a href="../evidence/quality-retrospective-v048.md">完整复盘与证据</a>　<a href="runtime/full-model.html">交互模型</a></p><p class="note"><b class="status">视觉还原：PARTIAL</b><br>v8 决定布局；《黑神话：悟空》提供构造、材质、地形衔接和光照层次的质量参照。当前仍有明显差距，24项结构通过不能代表达到该游戏的成品质量。</p><h2>01　同机位看实际变化</h2><div class="grid">',fig('p3-v042/day-gpu/CAM_WORLD_FULL.png','修改前 · v042'),fig('p3-v048/day-gpu/CAM_WORLD_FULL.png','当前实际模型 · v048 · 相机未改变'),'</div><h2>02　质量参照来自实际游戏画面</h2><p class="meta">下列图片为游戏科学官方画面，经 Unreal Engine 访谈及 PlayStation 产品页引用。用于观察质量，不是本项目渲染，也不作为模型纹理。</p><div class="refs">',fig('../evidence/quality-benchmark-v043/wukong-playstation-temple.jpg','寺庙近景：檐口层次、厚重地面、植被沿缝隙与边缘生长'),fig('../evidence/quality-benchmark-v043/wukong-playstation-landscape.jpg','广域场景：前、中、远景的明暗分离与空气层次'),fig('../evidence/quality-benchmark-v043/wukong-ue-official.jpg','雪地场景：石、树、雪具有不同尺度的形状和反光'),'</div><p class="meta">来源：<a href="https://www.playstation.com/en-us/games/black-myth-wukong/">PlayStation 官方图库</a> · <a href="https://www.unrealengine.com/developer-interviews/black-myth-wukong-wows-with-ue5-early-access-visuals">Unreal Engine 访谈（2021）</a></p><h2>03　逐项复盘</h2><table><thead><tr><th>检查维度</th><th>本轮实际修改</th><th>仍存在的差距</th></tr></thead><tbody>']
for name,change,remain in [('空间比例','双桥约10.5→7.5m、17.5→8.25m；岸、岛、路面协同变形','低岛与池岸仍有规整切面'),('屋顶与构造','四座主屋顶收短进深、提高屋脊；瓦、檐、椽同步，补椽木UV与墙顶封檐','柱梁连接与檐下构造仍欠精细'),('材质与光泽','石、瓦、土增加多尺度变化；石材按实际水线形成干湿过渡','重复纹理、偏圆石形和缺少局部磨损仍可见'),('植被与地形','八组过渡区、305个共享林下植被实例、三棵小乔木','物种与地形轮廓仍有限'),('光照与空气','减少环境填光、保留日光方向并增强明暗层次','薄雾、远山空气透视和室内反射光仍不足'),('运行与验收','保留生灵动作，独立做源文件、路线和交换检查','未做移动端LOD与内存预算，桌面模型仍重')]:parts.append('<tr><td>'+name+'</td><td>'+change+'</td><td>'+remain+'</td></tr>')
parts+=['</tbody></table><h2>04　近景也需要成立</h2><div class="grid">',fig('p3-v048/day-gpu/CAM_FRONT_GATE.png','前庭近景 · 木构UV与瓦面，构造连接仍待细化'),fig('p3-v048/day-gpu/CAM_WATER_BRIDGE.png','水园近景 · 短桥与岸石干湿关系'),'</div><p class="note">后续优先：古建连接细节 → 池岸与低岛修形 → 局部纹理及远山空气 → 效果稳定后的 LOD 与设备预算。以实际多机位渲染判断改善，不用物件数和面数代替结论。</p></main></html>']
(P/'quality-retrospective-v048.html').write_text(''.join(parts));print('QUALITY_REVIEW',len(rows))
