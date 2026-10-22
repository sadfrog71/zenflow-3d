package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI文本生成数据传输对象
 * <p>
 * 用于AI文本生成任务的参数传递，包含模型选择、提示词和生成条件
 *
 * @author caoyuan
 * @date 2024/6/17
 */
@Data
public class AiGenerateDTO {

	/**
	 * AI模型名称 指定使用的语言模型，如：gpt-3.5-turbo、gpt-4、qwen-max等
	 */
	private String modelName;

	/**
	 * 提示词 用于引导AI生成内容的输入文本
	 */
	private String prompt;

	/**
	 * 生成限制条件 用于控制生成内容的额外约束，如长度限制、格式要求等
	 */
	private String conditions;

}
