# 浅水庭院环境材质

`pond-floor.jpg` 是本次水庭实现使用的本地生成环境底图（1024 × 1536）；不是在线请求。通过内置 `image_gen` 由已确认的水庭原型生成并转存 JPEG。双鱼变形、水纹、触摸反馈和水花由代码实时绘制，底图不包含鱼和太极图形。

生成提示词：

> Create ONE production environment texture image derived from the approved water garden reference. Portrait 2:3, strict overhead orthographic view, one continuous clear shallow pond. Pale sage, muted gray-green and pearl warm limestone bottom, sparse smooth submerged pebbles toward the edges; curved pale limestone bank, tiny moss patches and a few leaves at lower left and upper right. Central 70% clear quiet open water, soft diffuse illumination and subdued refraction. Preserve the reference's restrained realistic 3D material language, softer, less busy, more spacious. Remove all fish, animals, yin-yang symbols, rings, ripples, splashes, floating leaves, UI, text, captions and borders. This static environment plate sits beneath separately rendered real-time 3D fish and water waves.

构建入口：`scripts/zenflow/build-pond.cjs`，图片嵌入带内容指纹的 `pond-source.json`。后续调整请修改源文件再构建，不手改生成物。固定镜头下使用环境底图减轻移动设备负担；本版不支持绕场景转动镜头。

## 双鱼材质（本轮修订）

`koi-atlas.webp` 是按确认稿生成的透明双鱼图集：左墨、右白，包含鳞片和半透明长鳍。固定俯视镜头下使用可弯曲网格承载材质，实时摆尾与游动；属于 2.5D 表现，不是可任意旋转查看的完整鱼模型。底图不变。

## 水声素材（本地打包）

- `water-drop.mp3`：florianreichelt 的 [Drop of Water](https://freesound.org/people/florianreichelt/sounds/683102/)，Freesound 标注 CC0。使用公开预览素材；高通 100 Hz、低通 6500 Hz、响度调整后保存。用于零散水滴、拨水及一分钟小水花。
- `water-lap.mp3`：Alex_hears_things 的 [Water lapping](https://freesound.org/people/Alex_hears_things/sounds/758858/)，Freesound 标注 CC0。使用公开预览素材的第 12–30 秒；高通 100 Hz、低通 3200 Hz、淡入淡出及响度调整后保存，作为水波循环。

音频嵌入构建资源，运行时不向 Freesound 发起请求。默认关闭，需明确点击开启。录音解码、播放状态与退出停止已验证；真人听感和 iOS 真机仍待验收。

### 2026-10-13 柔和水声修订

根据用户听感反馈，原独立水滴不再作为页面音源。`water-touch.mp3` 从上面的 CC0 `water-lap.mp3` 实录素材取第 6–8.4 秒，使用 140Hz 高通、1800Hz 低通、0.4 秒淡入、1 秒淡出及 1.25 增益处理（补偿滤波损失，播放时再由低增益控制），保留原始速度/音高。页面名称改为“轻拨水面 / Soft ripples”，不再宣称单滴落水。

连续拨水最短间隔由 0.35 秒改为 1.8 秒，零散自动播放间隔由 5.5–9 秒改为 9–15 秒，拨水与跟随反馈增益降低。暂停、后台和退出停声机制不变。原 `water-drop.mp3` 保留为旧版本来源，最新构建只嵌入 `water-touch.mp3` 与 `water-lap.mp3`。
