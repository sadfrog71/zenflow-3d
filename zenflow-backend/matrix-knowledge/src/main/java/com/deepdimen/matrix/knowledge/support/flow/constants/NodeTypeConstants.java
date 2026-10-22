package com.deepdimen.matrix.knowledge.support.flow.constants;

/**
 * 节点类型常量定义接口 用于定义AI工作流中各种节点的类型标识 每种节点类型代表不同的功能和处理逻辑
 *
 * @author caoyuan
 * @date 2025/03/03
 */
public interface NodeTypeConstants {

	/**
	 * 代码节点 用于执行自定义代码逻辑的节点类型 支持JavaScript、Python等脚本语言
	 */
	String CODE = "code";

	/**
	 * 数据库节点 用于执行数据库操作的节点类型 支持查询、插入、更新、删除等SQL操作
	 */
	String DB = "db";

	/**
	 * 结束节点 工作流的终止节点，表示流程结束 用于标记流程的正常结束点
	 */
	String END = "end";

	/**
	 * HTTP节点 用于发送HTTP请求的节点类型 支持GET、POST等各种HTTP方法，可调用外部API
	 */
	String HTTP = "http";

	/**
	 * LLM(大语言模型)节点 用于调用AI语言模型的节点类型 支持OpenAI、Azure、阿里云等多种大模型服务
	 */
	String LLM = "llm";

	/**
	 * 通知节点 用于发送通知或消息的节点类型 支持邮件、短信、企业微信等多种通知渠道
	 */
	String NOTICE = "notice";

	/**
	 * 问题节点 用于处理用户交互或决策的节点类型 可以暂停流程等待用户输入或选择
	 */
	String QUESTION = "question";

	/**
	 * 开始节点 工作流的起始节点，表示流程开始 每个流程必须有且只有一个开始节点
	 */
	String START = "start";

	/**
	 * 切换节点 用于条件分支判断的节点类型 根据条件表达式决定流程走向，支持多分支
	 */
	String SWITCH = "switch";

	/**
	 * RAG知识库节点 用于检索增强生成(Retrieval Augmented Generation)的节点 从向量知识库中检索相关内容，增强AI回答的准确性
	 */
	String RAG = "rag";

	/**
	 * MCP节点 用于调用MCP（Model Context Protocol）服务的节点类型 支持与外部MCP服务器进行交互，扩展AI能力
	 */
	String MCP = "mcp";

	/**
	 * 文本节点 用于返回固定文本内容的节点类型 支持模板变量替换，方便生成动态文本内容
	 */
	String TEXT = "text";

	/**
	 * 结构化输出节点 用于调用大模型生成符合指定JSON Schema的结构化数据 支持字段类型验证、格式检查和自动重试机制
	 */
	String STRUCTURED = "structured";

	/**
	 * 循环节点 用于重复执行子流程的节点类型 支持设置最大循环次数，子流程中可访问当前迭代索引
	 */
	String LOOP = "loop";

	/**
	 * OCR识别节点 用于图像文字识别的节点类型 支持从图片中提取文字内容，可配置识别模板和提示词
	 */
	String OCR = "ocr";

	/**
	 * 文档解析节点 用于解析Office和PDF文档的节点类型 支持Word、Excel、PowerPoint、PDF等格式的文本提取
	 * 自动选择最佳解析策略，失败时可降级到AI视觉识别
	 */
	String DOCUMENT_PARSER = "documentParser";

}
