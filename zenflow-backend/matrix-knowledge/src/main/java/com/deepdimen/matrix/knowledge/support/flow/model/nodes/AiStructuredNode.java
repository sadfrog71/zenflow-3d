package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

import java.util.List;

/**
 * AI 结构化输出节点 用于在工作流中调用大语言模型生成符合指定JSON Schema的结构化数据 支持字段类型定义、自动验证和重试机制
 *
 * @author caoyuan
 * @date 2025/01/24
 */
@Data
public class AiStructuredNode {

	/**
	 * 对话消息列表 包含用户和助手的历史对话记录，用于上下文理解
	 */
	private List<Message> messages;

	/**
	 * 模型配置信息 包含模型选择和各种生成参数的配置
	 */
	private Model modelConfig;

	/**
	 * JSON 字段定义列表 定义期望的 JSON 输出结构
	 */
	private List<JsonField> jsonFields;

	/**
	 * 消息实体类 表示对话中的单条消息
	 */
	@Data
	public static class Message {

		/**
		 * 消息角色 可选值：system（系统）、user（用户）、assistant（助手）
		 */
		private String role;

		/**
		 * 消息内容 具体的文本内容
		 */
		private String content;

	}

	/**
	 * 模型配置类 用于配置大语言模型的各种参数
	 */
	@Data
	public static class Model {

		/**
		 * AI模型的唯一标识符 例如：gpt-3.5-turbo, gpt-4等
		 */
		private String model;

		/**
		 * 生成文本的最大令牌数 控制模型回复的最大长度
		 */
		private String maxTokens;

		/**
		 * 温度参数，控制输出的随机性 较高的值（如0.8）会使输出更加随机，较低的值（如0.2）会使输出更加集中和确定
		 */
		private String temperature;

		/**
		 * 核采样参数，控制输出的多样性 与temperature类似，但使用不同的采样方法 建议不要同时调整temperature和topP
		 */
		private String topP;

		/**
		 * 频率惩罚参数 正值会根据新token在文本中的现有频率来惩罚它们，降低模型逐字重复同一行的可能性
		 */
		private String frequencyPenalty;

		/**
		 * 存在惩罚参数 正值会根据新token是否出现在文本中来惩罚它们，增加模型谈论新主题的可能性
		 */
		private String presencePenalty;

		/**
		 * 是否启用流式响应 true: 启用流式响应，服务器将持续发送响应 false: 禁用流式响应，等待完整响应后一次性返回
		 */
		private Boolean stream;

		/**
		 * 是否支持视觉能力 用于标识模型是否可以处理图像输入（结构化输出节点暂不支持）
		 */
		private String isVision;

	}

	/**
	 * JSON 字段定义类 描述期望的 JSON 输出字段
	 */
	@Data
	public static class JsonField {

		/**
		 * 字段名称 例如：name, age, address
		 */
		private String name;

		/**
		 * 字段类型 支持：String, Number, Boolean, Object, Array
		 */
		private String type;

		/**
		 * 字段描述 可选，用于生成更准确的 JSON Schema 和帮助模型理解字段含义
		 */
		private String description;

	}

}
