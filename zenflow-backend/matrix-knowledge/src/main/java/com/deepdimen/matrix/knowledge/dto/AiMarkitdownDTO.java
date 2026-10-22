package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI Markdown转换数据传输对象
 * <p>
 * 用于配置PDF文档转Markdown的处理参数，支持自定义API和处理策略
 *
 * @author caoyuan
 * @date 2025/3/14
 */
@Data
public class AiMarkitdownDTO {

	/**
	 * 自定义OpenAI API基础URL 可选，用于指定私有部署或代理的API地址
	 */
	private String base_url;

	/**
	 * 自定义OpenAI API密钥 可选，用于访问API的认证密钥
	 */
	private String api_key;

	/**
	 * 自定义OpenAI模型名称 可选，指定用于处理的AI模型，如：gpt-4-vision等
	 */
	private String model;

	/**
	 * 自定义提示词 可选，用于优化PDF转换效果的提示词模板
	 */
	private String prompt;

	/**
	 * 自定义PDF处理并发限制 可选，控制同时处理的PDF页面数量
	 */
	private Integer concurrent_limit;

	/**
	 * 自定义PDF批处理大小 可选，每批次处理的页面数量
	 */
	private Integer batch_size;

	/**
	 * 自定义文件删除延迟时间(秒) 可选，处理完成后延迟删除临时文件的时间
	 */
	private Integer delete_delay;

}
