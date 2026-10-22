# 此间 iOS App（P0）

## 读经室本地资源（2026-10-16）

原生端的书室场景复用庭院的内嵌 WebView 方式：运行 `node scripts/zenflow/build-reading-room-native.cjs` 生成 `src/features/zenflow/reading-room-source.json`，包含页面、引擎、16 张场景贴图及花瓶模型的 5 个文件，共 21 个内嵌资源。构建使用本仓库 `zenflow-admin` 的既有 esbuild；资源编码脚本 `docs/prototypes/reading-room/optimize-textures.py` 只在原图变化时运行，需要 Pillow。`npm run build:rest` 已包含本地书室构建。

原生端不需要通过网络下载房间资源；Web 端继续从公开静态路径加载。书目、已发布语言版本和正文仍通过独立 API 更新，本地房间不携带测试读者账号或私人阅读状态。WebView 保持 `about:blank` 来源白名单；场景真实首帧消息用于结束加载计时，加载失败仍可进入书架或返回。

当前真机版本为 build 23，场景指纹 `8563d043bbad946f`；类型检查、3 项定向测试和实际真机 3D 显示通过，用户确认重新进入和书架正常。签名包及实测结果见 [测试部署证据](../docs/evidence/reading-classic-20261015/test-deployment.json)。

## 浏览器端（2026-10-08）

复用本工程的四个产品入口，新增 Web 平台适配与 Google 官方登录按钮。运行 `npm run build:web:preview`、`npm run preview:web`，在本机打开 `http://127.0.0.1:8792`。正式构建用 `npm run build:web`。产物、会话保存边界与配套后端发布要求见 [浏览器端实现与验收](../docs/Invelle浏览器端实现与验收-20261008.md)。浏览器版已部署，最新修复为 `20261009-web-restart`；各项实际验收边界见上述记录。

## 当前状态（2026-10-07）

独立服务已在 `https://invelleai.com` 接通，Google 登录与真机实际聊天已由用户确认。聊天、私人创作、记录与模型管理已实现；会员、账单、支付保留设计。本轮已按 `?v=080` 原型修复手机庭院、打坐、语音输入与书写页面，并安装 build9；手机实际交互待验收，详细状态见 [移动端修复记录](../docs/Invelle移动端原型对齐修复-20261007.md)。以下保留早期开发记录；请以最新修复记录的验证状态为准。

## Invelle 品牌测试包（2026-10-06）

英文品牌 Invelle，中文此间；API 根地址 `https://invelleai.com`。最新 v9 圆融折面图形已用于 App 图标及首页品牌标识。独立 Bundle ID 为 `com.avatartech.invellepro`，开发团队为用户授权借用的 `8S9YP5PPMR`。

打包配置示例见 `.env.invelle.example`。当前 `ZENFLOW_APP_ENV=test`，Google 专用提供方已建立并接入；Facebook、Apple 真实提供方配置仍待完成；Apple 登录签名权限与真实提供方登录配置分别验证。现有 `zenflow-ios` 客户端 ID 和 `zenflow://auth/callback` 与账号中心保持兼容。

以下为初始 P0 工程及配置说明，正式功能范围仍以实际验收为准。

独立的 Expo / React Native 工程，从锁定的移动端参考提交派生。当前有庭院、休息、聊天、创作、记录入口，以及邮箱账号登录入口；对话、创作和记录的真实能力属于后续阶段。上层此间仓库已连接私有 GitHub 远端，尚未提交或推送；当前 Invelle 测试包已使用独立 Bundle ID 和开发签名。

## 配置

复制 `.env.example` 填写此间专用 HTTPS 网关根地址和 OAuth 客户端 ID。App 请求 `/api/zenflow/v1/*` 与 `/api/admin/oauth2/*`，对应后端 `/admin/zenflow/v1/*` 与 `/admin/oauth2/*`；网关映射见上层 `docs/前后端实施设计-v0.3.md`。没有域名时，App 会显示“服务连接尚未配置”，庭院仍可离线浏览。

正式打包另需 `ZENFLOW_APP_ENV=production`、`ZENFLOW_IOS_BUNDLE_ID` 和 HTTPS API 地址。开发包使用本地标识 `com.zenflow.local`。验证码邮件由服务端发送，App 不保存邮箱密码；SecureStore 只保存刷新令牌，账号切换清除旧令牌及页面状态。

## 检查

```sh
npm ci
npm run build:rest
npm run typecheck
npm test -- --runTestsByPath src/core/zenflow/__tests__/session.test.ts
npx expo export --platform ios
npx expo prebuild --platform ios --no-install
npm run ios:pods
npx expo run:ios --device 'iPhone 17 Pro' --no-bundler
```

项目路径包含空格，因此 iOS 生成脚本通过配置插件引用构建脚本；两个锁定 Expo 包的 CocoaPods 脚本修复保存在 `patches/`，安装依赖时由 `patch-package` 应用。

## 海外首版登录（2026-10-06）

Google、Facebook、Apple 原生 SDK 及可选邮箱入口已接入。专用公开 ID 配置见 `.env.example`；缺配置时显示未配置。修改这些配置后需重新生成和构建原生工程。App 不保存私密服务端 secret。真实提供方认证与完整设备操作验收尚未通过，见 `../docs/海外登录开发与本地验收-20261006.md`。


### Chrome 桌面应用

正式 Web 构建自动包含 `public/manifest.json`、192/512图标、安装事件捕获及网络优先的根页面离线提示。用户可点击页头“安装应用”，或使用 Chrome 菜单的网页安装功能；安装后以独立窗口打开。私人接口和登录回调不经离线处理。Web访问令牌留在内存，续期凭据保存于当前浏览器，关闭重开后由服务器核验恢复；退出与切换清除保存状态。

定向检查：`node --test scripts/web/pwa.test.mjs`。正式构建使用 `npm run build:web`，以便在导出的 HTML 中注入 manifest 与注册脚本。

## 解压水庭与跟鱼交互（2026-10-10）

“解压一会儿”已接入休息页，与静坐庭院并列。场景源在 `scripts/zenflow/pond-*`，`npm run build:rest` 更新首页与水庭的带指纹资源；Web 构建会自动执行，iOS 导出/构建前请先运行该命令。生成物 `src/features/zenflow/pond-source.json` 不手改。

已发布 Web 版本 `20261010-water-garden`：点鱼、沿尾波划水、松手结束，支持暂停/后台清除和减少动态。21 项定向测试及 Web/iOS 资源导出通过，发布前扩展回归共 47 项；iOS 0.1.0/build19 已覆盖安装到 iPhone 15 并启动成功，真机触摸和主观听感仍待验收。详见 [水庭实现与验收](../docs/Invelle解压水庭实现与验收-20261009.md)。


### 2026-10-14 禅院与水庭美术修订（本地未发布）

庭院运行源码现位于 `scripts/zenflow/garden-scene.js`，通过既有 `build-garden.cjs` 生成；`build:rest` 与 Web 构建均包含庭院。移动端使用已打包 r180 引擎，新增扫描木石／湖景贴图复用既有 CC0 来源。图像变更计入庭院资源版本指纹。水庭仍保持原跟随与弱动态交互，新增曲面鱼身受光和岸石。

30 项定向测试、类型检查与 iOS 资源导出通过，未安装或发布。细节、版本、预览和真机边界见 [三场景美术升级](../docs/Invelle三场景美术升级-20261014.md)。

### 2026-10-17 书籍加载等待修复（本地与模拟器）

书架与正文请求增加 20 秒截止时间，覆盖响应正文读取；超时或失败可重新加载，返回书架取消旧请求，迟到结果不覆盖重试结果。6 项定向测试、类型检查及 iOS Release 构建通过。模拟器受控卡住请求后显示超时提示，重试从真实服务打开《金刚经》；正常版本验证新版正文、夹注及章节切换。手机原始请求未捕获，本次未安装真机或部署服务器。详见[排查与验证记录](../docs/evidence/classic-han-revision-20261016/book-loading-debug-20261017.json)。
