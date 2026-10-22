package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI提示词优化数据传输对象
 * <p>
 * 用于传输和优化AI模型提示词相关的参数，支持系统提示词、用户提示词、温度控制等配置
 *
 * @author caoyuan
 * @date 2024/10/16
 */
@Data
public class AiPromptDTO {

	/**
	 * AI模型名称
	 * <p>
	 * 指定要使用的AI模型（如：gpt-3.5-turbo、gpt-4、claude等）
	 */
	private String modelName;

	/**
	 * 用户提示词
	 * <p>
	 * 用户输入的主要提示内容，用于指导AI生成响应
	 */
	private String prompt;

	/**
	 * 系统提示词
	 * <p>
	 * 设定AI的角色和行为规范，影响AI的回复风格和内容方向
	 */
	private String systemPrompt;

	/**
	 * 温度参数
	 * <p>
	 * 控制AI输出的随机性，取值范围0-2，值越高输出越随机和创造性，值越低输出越确定和保守
	 */
	private Double temperature;

	/**
	 * 提示词模板
	 * <p>
	 * 预定义的提示词模板，可包含占位符，用于快速生成结构化的提示词
	 */
	private String template;

}
