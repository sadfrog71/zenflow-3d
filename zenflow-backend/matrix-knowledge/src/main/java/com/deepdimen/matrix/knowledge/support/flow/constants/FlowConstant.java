package com.deepdimen.matrix.knowledge.support.flow.constants;

/**
 * AI流程常量定义接口 定义了流程执行过程中使用的各种常量键名
 *
 * @author caoyuan
 * @date 2025/03/03
 */
public interface FlowConstant {

	/**
	 * 流程执行结果 - 用于存储整个流程的最终执行结果
	 */
	String RESULT = "result";

	/**
	 * SQL执行语句 - 用于存储数据库节点的SQL语句
	 */
	String SQL = "sql";

	/**
	 * 时间戳 - 记录操作或事件发生的时间
	 */
	String TIMESTAMP = "timestamp";

	/**
	 * 问题值标识 - 用于问答节点存储用户输入的值
	 */
	String QUESTION_VALUE = "value";

	/**
	 * 节点索引 - 标识节点在流程中的位置或分支索引
	 */
	String INDEX = "index";

	/**
	 * Token数量 - 记录大语言模型使用的Token消耗量
	 */
	String TOKENS = "tokens";

	/**
	 * 角色标识 - 在对话场景中标识消息发送者的角色（如用户、助手、系统）
	 */
	String ROLE = "role";

	/**
	 * 内容 - 存储消息、文本或其他内容数据
	 */
	String CONTENT = "content";

	/**
	 * HTTP 正文 - HTTP请求或响应的主体内容
	 */
	String HTTP_BODY = "body";

	/**
	 * HTTP 标头 - HTTP请求或响应的头部信息
	 */
	String HTTP_HEADERS = "headers";

	/**
	 * HTTP 状态 - HTTP响应的状态码
	 */
	String HTTP_STATUS = "status";

	/**
	 * 是否进行RAG搜索的标志 - 标记是否已执行检索增强生成(RAG)搜索
	 */
	String RAG_SEARCHED = "ragSearched";

	/**
	 * JSON数据 - 结构化输出节点生成的JSON对象
	 */
	String JSON = "json";

}
