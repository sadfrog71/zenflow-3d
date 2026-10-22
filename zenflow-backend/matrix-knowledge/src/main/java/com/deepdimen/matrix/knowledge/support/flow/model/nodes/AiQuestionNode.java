package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

import java.util.List;

/**
 * AI 问题分类节点 用于在工作流中对输入的问题进行智能分类，根据分类结果控制流程走向 通过大语言模型理解问题内容并匹配到预定义的分类选项
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiQuestionNode {

	/**
	 * 问题内容 需要进行分类的问题文本
	 */
	private String question;

	/**
	 * 分类选项列表 预定义的所有可能的分类类别
	 */
	private List<Category> categories;

	/**
	 * 模型配置 用于问题分类的大语言模型参数配置
	 */
	private Model modelConfig;

	/**
	 * 分类选项类 定义单个分类的属性
	 */
	@Data
	public static class Category {

		/**
		 * 分类条件值 用于匹配的条件或关键词
		 */
		private String value;

		/**
		 * 分类名称 该分类的可读名称
		 */
		private String name;

		@Override
		public String toString() {
			return String.format("条件：%s，对应分类： %s", value, name);
		}

	}

	/**
	 * 模型配置类 用于配置问题分类所使用的大语言模型参数
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

	}

}
