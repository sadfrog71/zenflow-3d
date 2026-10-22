# iPad 游戏设计 Skill 检索与评审

核对日期：2026-10-21。范围：查询候选、读取入口原文、核对仓库信息和本项目适配性。首轮查询没有安装候选 Skill。后续用户授权试用后，已审查全部两份文件并安装固定版本 `398cd41c86ba42fbb30e944ae507f463648f08b7`；试用成果见[独立交互样板](../prototypes/mobile-garden-skill-trial-20261021/README.md)，未修改正式 App 或模型母版。

**结论：本次没有找到一个同时覆盖 iPad 第一视角生活游戏、异步语音社交、私人内容与现有 Zenflow 工程的完整 Skill。最适合补充现有能力的是 `build-mobile-threejs-games`，但它是一份简短的移动端交互／验证清单，不能替代本项目产品与开发设计。**

本轮实际使用了本机 `find-skills` 进行检索，使用 `tech-doc-style-chinese` 整理交付文档。其他候选只作审查材料，不因文内提到安装、奖励、引擎或工作流就执行相关操作。

## 1. 检索方法与证据

先查看 [skills.sh 目录](https://skills.sh/)，再执行检索词 `ipad game`、`game design`、`mobile games`；对筛选候选从 GitHub API 获取仓库星数、许可证信息和树版本，读取对应 SKILL.md。网页读取 skills.sh 单项页失败时，以 CLI 检索与 GitHub 源文件为依据，不把网页不可读说成 Skill 不存在。

安装量来自本轮 CLI 显示，星数来自 GitHub API，均为查询时快照；安装量、仓库星数不能证明内容质量、安全性或实际使用效果。源路径、版本与计数保存于 [skill-candidates.json](ipad-v1-evidence/skill-candidates.json)。

## 2. 候选比较

| Skill | 查询时安装量／仓库星数 | 实际内容与项目适配 | 建议 |
| --- | --- | --- | --- |
| [build-mobile-threejs-games](https://skills.sh/mengto/skills/build-mobile-threejs-games) | 约 1.2K / 6,877 | Three.js 触控、安全区、横竖屏、交互取消、后台恢复、减少动态、真机测试；与现有场景技术最接近。入口约 1.2K 字符，偏检查清单 | 优先候选；需要时单独安装，不承诺覆盖社交和后台 |
| [ios-game-feel-design](https://skills.sh/kai7win/ios-game-feel-design-skill/ios-game-feel-design) | 2 / 2 | SwiftUI/UIKit 的反馈、声音和动效；含连续动作事件标识、无障碍和后台暂停。偏原生界面，和当前 React Native/WebView 不直接对应 | 只作反馈设计参考，暂不推荐安装 |
| [mobile-game-dev](https://skills.sh/omer-metin/skills-for-antigravity/mobile-game-dev) | 205 / 175 | 触控、性能、发热、内存、生命周期；依赖额外参考文件，有固定首屏目标和泛化角色叙述 | 次选；安装前须审查 references，不直接采纳固定数值 |
| [game-ui-design](https://skills.sh/omer-metin/skills-for-antigravity/game-ui-design) | 约 3.8K / 175 | 游戏界面、无障碍、视觉反馈，但明确偏手柄优先，并依赖参考文件 | 不作为本次 iPad 入口，避免改变已确认的触摸方案 |
| [game-developer](https://skills.sh/jeffallan/claude-skills/game-developer) | 约 6.3K / 11,974 | 主要面向 Unity/Unreal、ECS、物理、多人与强制 60+ FPS 的实现工作 | 不采用；不能为了使用 Skill 改换现有引擎 |

## 3. 原文核对结果

### 3.1 推荐候选：build-mobile-threejs-games

仓库：[MengTo/Skills](https://github.com/MengTo/Skills)。许可证元数据为 MIT。已读取的固定入口：[SKILL.md](https://github.com/MengTo/Skills/blob/398cd41c86ba42fbb30e944ae507f463648f08b7/agent-skills/game-development/build-mobile-threejs-games/SKILL.md)。

能补充本项目的部分：触摸选择与取消、屏幕安全区、横竖屏状态保留、长时间运行、音频解锁、暂停恢复和实际移动端检查。它没有提供生活模拟玩法、愿签关系、原声审核、私人数据授权或 Java/Vue 后台设计。

读取的入口未包含外部上传、凭据索取或执行脚本指令。这只是入口静态审查，不是完整仓库安全审计。若以后安装，可单独选择此技能：

```bash
npx skills add https://github.com/MengTo/Skills --skill build-mobile-threejs-games
```

首轮未执行安装；后续试用已重新核对上游固定版本、全部两份文件与安装内容，审查记录见样板 evidence/skill-vetting.json。以后更新仍需重新核对。

### Blender 模型是否适用

**适用的是 Blender 模型进入 Three.js 后的移动交互和运行阶段。** Skill 原文面向移动 Web，不依赖模型由什么工具制作；本项目的 React Native WebView 使用同类 Three.js 场景，所以触控、安全区、方向变化和性能检查可以采用。原生登录、麦克风和生命周期桥接还需按现有 App 工程补充，Skill 没有覆盖这些平台集成。

接入链路：`Blender .blend → 分区导出 GLB/glTF → Three.js GLTFLoader → React Native WebView → iPad / iPhone`。[GLTFLoader 官方文档](https://threejs.org/docs/pages/GLTFLoader.html)提供场景、相机与动画读取接口；加载成功后仍需业务代码把热点和动作连接到功能。

本项目已核实 p3-v039 的 `.blend` 和 `.glb`，并核对相机与节点，所以不需要为了使用该 Skill 重做模型或换引擎。当前 GLB 约 145 MiB，仍需分区、几何／纹理优化和真机测试；其 17 段动画的存在不等于已接入喂鱼、回应等玩法。

| 工作阶段 | 该 Skill 能否覆盖 |
| --- | --- |
| Blender 建筑、材质、构图、补交互锚点 | 未提供制作流程，沿模型总纲与现有美术工具完成 |
| 导出、材质兼容、贴图烘焙、减面与资源拆包 | 未提供具体管线，需要专项资产处理；检查水面、阴影、透明和程序材质的运行时效果 |
| Three.js 触控、热点选择、手机／平板界面、方向变化 | 是主要适用范围，但它是指导清单，不是现成组件库 |
| 性能档、后台恢复、减少动态、移动端验证 | 可提供检查方向；实际预算和设备结果需要本项目测量 |
| Apple／邮箱账号、私人存档、朋友关系、审核后台 | 不覆盖，复用并扩展现有 Zenflow 工程 |

因此建议把它作为开发和验收的补充清单，与本项目资产接入设计一起使用。后续已按用户授权安装并试用，详见本文开头链接；原生业务与真机验收仍未完成。

### 3.2 iOS 手感候选的限制

查询别名 `kai7win/ios-game-feel-design-skill` 已由 GitHub 返回至 [KaiChi888/ios-game-feel-design-skill](https://github.com/KaiChi888/ios-game-feel-design-skill)。固定入口版本为 `7c6a4ff98c08962396fedec06fdac83080118726`。

其声音、动作反馈和持续状态分离有参考价值，但同时含奖励、连胜、镜头震动和庆祝式反馈，不能直接带入本项目。部分功能依赖 SwiftUI/UIKit；不能把 iPhone 的振动建议推广为所有 iPad 都支持。GitHub API 本轮未识别许可证，安装量也很低，因此不建议直接安装或复制其实现代码。

### 3.3 其余候选的适用边界

`mobile-game-dev` 和 `game-ui-design` 的入口要求以其参考文件为强依据，甚至包含在用户要求冲突时纠正用户的表述。本项目仍以用户已确认范围为准。二者只读取了入口，未对整个 references 目录做完整审计，评审不称其安装安全已通过。

`game-developer` 有较高仓库关注度，但技术方向与本项目不同，其 60+ FPS、Unity/Unreal 工具及实时多人约束不能成为本项目必须迁移或扩展的理由。

## 4. 本项目采用的组合

| 工作 | 推荐依据 |
| --- | --- |
| 产品范围、用户关系与玩法 | 当前会话确认及 [v1 产品与空间设计](iPad轻生活游戏-v1-产品与空间设计-20261021.md) |
| 触控、可读性、iPad 布局 | [Apple 游戏控件指南](https://developer.apple.com/design/human-interface-guidelines/game-controls)；候选 Three.js Skill 仅作补充 |
| 模型与美术 | 当前模型总纲，以及本机已有的 environment-artist / set-dressing；本轮未调用其制作流程 |
| 业务、权限、媒体和后台 | [v1 前后台开发设计](iPad轻生活游戏-v1-前后台开发设计-20261021.md)及现有 Zenflow 工程规则 |
| 实际设备验收 | 指定 iPad／iPhone 真机、明确版本和录屏／性能证据；Skill 文本和桌面浏览器均不能代替 |

没有必要为本次设计先安装一整套游戏技能，也不需要因此引入 Unity、Godot 或独立游戏服务。本轮后续只安装了推荐候选，未安装其他游戏技能或引擎。
