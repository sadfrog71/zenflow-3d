# Invelle 移动端补充规则

继承[根规则](../AGENTS.md)，已在上下文中无需重读。页面任务读取[最新交互修订](../docs/Invelle原型与社区修复范围-20261007.md)；目录、资源和原生检查见[移动端说明](../docs/项目协作指南.md#mobile)，旧构建记录不覆盖后续修订。

- 复用 `src/app/`、`src/core/zenflow/`、`src/features/zenflow/` 和现有原生桥接；仅连产品 HTTPS 网关。授权码配合 PKCE，access token 在内存、refresh token 在 SecureStore；账号切换清理私人状态，阻断迟到回调。
- 最新首页通过入口进入庭院；沿现有 `scripts/zenflow/build-*.cjs` 修改源素材并生成 `*-source.json`，不只改生成物。本机原型缺失须说明；WebView 按内容指纹更新，尊重减少动态，离开 / 后台停声。
- 聊天语音结束后自动发送一次；取消、空结果、重复和迟到结果不发送。创作 / 记录只填草稿，不自动保存或公开；即时识别不等于已实现原音保存和长录音。
- 保持键盘避让、草稿、返回与导航恢复；SSE 断流不当完成。相关逻辑改动执行 `npm run typecheck` 和定向 `npm test -- --runTestsByPath <测试文件>`，页面变化补实际交互验证。
- 原生 / 依赖 / 资源变化按影响补导出和目标构建，核验实际 bundle；Pods、模拟器和 archive 不并发写同一产物。保留现有锁文件、补丁与用户数据，真人语音和真机体验单独验收。文档修改只作静态检查。
