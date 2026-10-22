package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * Mineru文档解析服务配置
 *
 * @author caoyuan
 * @date 2025/06/01
 */
@Data
public class MineruProperties {

	/**
	 * 是否启用Mineru服务
	 */
	private boolean enabled = false;

	/**
	 * 是否使用云版本服务
	 */
	private boolean isPrivate = false;

	/**
	 * Mineru服务API地址
	 */
	private String baseUrl = "https://mineru.net/api/v4";

	/**
	 * API访问密钥
	 */
	private String apiKey;

	/**
	 * 默认输出格式（支持markdown、json等）
	 */
	private String defaultOutputFormat = "markdown";

	/**
	 * 是否默认启用OCR识别
	 */
	private boolean defaultEnableOcr = true;

	/**
	 * OCR识别语言（auto自动识别）
	 */
	private String defaultOcrLanguage = "auto";

	/**
	 * 解析后端(pipeline/hybrid-auto-engine/vlm-auto-engine)
	 */
	private String backend = "pipeline";

	/**
	 * OCR语言(ch/en/korean/japan等)
	 */
	private String lang = "ch";

	/**
	 * 最大并发解析线程数
	 */
	private int maxConcurrent = 2;

	/**
	 * 连接超时时间（秒）
	 */
	private int connectTimeout = 60;

	/**
	 * 读取超时时间（秒）
	 */
	private int readTimeout = 600;

}