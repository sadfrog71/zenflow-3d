# Zenflow 3D

Invelle（此间）轻生活游戏 v1 的独立开发快照，整理于 2026-10-22。包含当前应用源码、设计文档、场景运行资源及 Blender 可编辑模型。

这是进行中的开发版本。登录、文字记事、聊天和部分园境操作已有验证；社区、来信、鱼塘编辑等仍未完成。原声代码已接入，但当前 iPad 模拟器录音准备失败，不能视为已完成。详见 [开发进度](docs/design/Invelle轻生活游戏-v1-开发进度-20261022.md)。

## 目录

| 路径 | 内容 |
| --- | --- |
| `zenflow-mobile/` | Expo / React Native 应用、原生模块、场景脚本及运行资源 |
| `zenflow-backend/` | 应用后端与构建所需的复用模块；新媒体和园境能力默认关闭 |
| `zenflow-admin/` | 管理端源码；其 esbuild 也供现有场景打包脚本使用 |
| `zenflow-space/docs/design/blender/zen-space/` | v053 主模型、v039 运行基线、对应 GLB、模型清单与制作脚本 |
| `docs/design/` | 需求、交互、工程契约、存储与开发进度 |
| `docs/import/` | 导入来源、文件清单及整理说明 |

## 获取完整模型

安装 Git LFS 后克隆仓库，并执行：

```sh
git lfs install
git lfs pull
```

`.blend` 与 `.glb` 使用 LFS。缺少 LFS 对象时不能以小型指针文件作为可用模型。

## 本地开发

需要 Node.js、npm；原生验证另需 Xcode / iOS Simulator。依赖沿原锁文件，不升级 Expo。

```sh
cd zenflow-admin
npm ci
cd ../zenflow-mobile
npm ci
node scripts/zenflow/build-life-garden.cjs
npm run typecheck
npm start
```

本项目已将原先依赖相邻 `zenflow-space` 目录的场景构建引用改为仓库内部路径。Python 资源导出另需 numpy 与 Pillow；不应将重新导出理解为原模型视觉验收。

复制 `zenflow-mobile/.env.example` 为本机配置后填写。仓库不提供真实账号、密钥或可直接登录的配置。后端按源码中的环境变量与分期脚本准备，缺配置应明确失败；不运行本地 Docker，不自动初始化远端数据库。

## 模型版本

- `zen-space.blend`：当前 v053 可编辑模型，技术检查 PASS、视觉 PARTIAL。
- `zen-space-p3-v039.blend` 与 `exports/zen-space-p3-v039.glb`：现有移动运行资源的来源基线。
- `exports/zen-space-p3-v053.glb`：最新模型导出，尚未替换移动运行基线。

第三方资产许可见模型目录中的 `manifest/LICENSES.md`。不在此快照中新增或推定对外授权。

## 导入边界

保留原目录未提交工作；新仓库使用独立 Git 历史。没有复制密钥、测试账号、私人数据、生产部署目录、依赖缓存、原生构建产物或全部历史模型。历史文档里的本机绝对路径和旧预览链接仅作来源记录，原始验证截图未一并上传，不能当作本仓库可重放证据。
