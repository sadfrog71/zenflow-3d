# Invelle 知识模块补充规则

继承[根规则](../../AGENTS.md)与[服务端规则](../AGENTS.md)，已加载内容无需重读。

- 复用现有解析、切片、模型、嵌入与存储适配器；产品发布快照、资料授权和引用回查由 `zenflow/zenflow-biz` 适配负责，公共资料与私人记忆保持隔离。
- 只有修改本模块 Flow 节点、RAG 或适配器时，读取[框架技术说明](FRAMEWORK_GUIDE.md)对应章节。其历史版本、Nacos、Milvus 等描述不代表产品当前部署或启用要求，核对实际 POM 与配置。
- `FlowContextHolder` 规范仅用于 Flow 节点；Invelle 对话继续沿助手运行引擎与托管 Skill，不另走一套工作流，不连接旧环境数据或密钥。
