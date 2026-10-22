# 树木参考与实际模型精修 · p3-v053

日期：2026-10-22。范围：独立资产项目，树木专项，沿用 v050 建筑、场地、相机和光照。

## 参考与生成

真实参考：[新华社 / 中国政府网拙政园航拍](https://english.www.gov.cn/news/202503/18/content_WS67d97e41c6d0868f4e8f0f17.html)。仅私有参考，不用作模型贴图。

内置 Image Gen 编辑模式：以 v050 的 CAM_GARDEN_COMPOSITION 为目标图，以真实航拍为树冠形态参考。完整提示词见 [imagegen-prompt.txt](tree-reference-study-20261022/imagegen-prompt.txt)。输出 [目标稿](tree-reference-study-20261022/tree-canopy-proposal-imagegen-v001.png)。生成稿是目标，不是 Blender 渲染。

采纳：枝干决定冠形、不对称树冠、疏密与冷暖差异、稀疏红花。未采纳生成稿额外改变的水面与种植密度；v8 和当前共享场景仍为布局依据。

## 实际修改

- 158 株树采用 7 套共享枝叶原型；两株地标树使用不同骨架；根位保持。
- 109 株背景树替换原重复模型；撤下一株与小屋主树同位重叠的背景树。42 株原程序松树与 3 株取景树也统一修理。
- 2463 个旧树木/叶簇/花对象保留但隐藏，保留源资产；没有删除历史模型。
- 连接的渐细枝干、略有弯曲的枝冠、受控根部展开；折叠卵形几何叶片，无树冠透明卡片。
- 树皮复用已有 CC0 资产材质，调整纹理尺度；叶片分色，原生薄叶透光权重 0.18。
- 交互交换材质采用 Principled 近似；不宣称原生透光完全一致。
- 保留 5 条锦鲤（3 条红色）、2 只猫、17 个活动片段和 131 段路径。

## 对照与限制

[对照页](../previews/tree-reference-v053.html)按真实参考 → Image Gen 目标 → v050/v053 同机位模型组织。6 张当前实际渲染来自同一共享模型，无逐相机替代几何。俯视、许愿树下、古树路径、南门、廊下复核。

技术检查 PASS，视觉 PARTIAL。树冠厚度、细叶尺度变化、树皮和分枝过渡仍有程序化痕迹；尚未达到目标稿或黑神话的环境质量。移动设备表现未验收。动作短片仍是 v048 的历史短片，本轮未输出新短片。

相关记录：botanical-trees-v051.json、botanical-canopy-finish-v052.json、botanical-leaf-finish-v053.json、structure-check-p3-v053.json、source-audit-v053.json、living-source-v053.json、portability-check-p3-v053.json、full-glb-export-check-v053.json。

## 最终交换预览复核

桌面 WebGL2 加载成功，17 个生灵动作片段齐全、时间推进、无加载错误。GLB 178,592,280 字节；约 2,172 万渲染三角形、7,387 次绘制，属于高负载桌面审阅资产，不能作为移动性能通过依据。交互水面与叶片透光仍为简化表现，水面明显弱于原生渲染；以 Blender 实际图为视觉依据。
