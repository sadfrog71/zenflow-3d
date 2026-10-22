# Invelle 服务端补充规则

继承[根规则](../AGENTS.md)，已在上下文中无需重读。改服务端时按需查[实现与检查说明](../docs/项目协作指南.md#backend)及实际 POM；来源见 [SOURCE_BASELINE.md](SOURCE_BASELINE.md)。

- 产品业务在 `zenflow/zenflow-biz/`，装配在 `matrix-boot/`；通过现有 Service / Port 复用框架，避免循环依赖。新增接口同时检查 `ZenflowEndpointBoundaryFilter`、安全配置与产品 gateway，不能开放旧企业旁路。
- `APP_USER` / `ADMIN_USER`、租户和所有者须服务端复核；`auth_version` 覆盖刷新、SSE 与异步任务，子资源回查父对象。保持对话幂等、固定修订、取消、恢复和持久化顺序，普通发布不打断已准入轮次，撤权仍及时生效。
- 改资料、摘要或记忆时读[数据与知识细则](../docs/项目协作指南.md#conversation)：索引回查获准 SQL 原文，摘要缓存复核身份与来源授权，删除传播至派生数据；后台预览不能读消费者私人内容。
- 迁移位于 `db/zenflow/`；先查已执行批次，既有库不重跑初始化，已应用脚本不就地改写。凭据只走独立配置，测试不能修改用户既有模型参数。
- 从本目录执行定向 Maven 检查，命令见实现说明；`-Dtest` 通配参数用单引号。格式基线失败单列，不全仓格式化；H2 / 替身结果不代替实际 MySQL / 供应商验收。文档修改无需业务构建。
