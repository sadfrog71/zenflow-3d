package com.deepdimen.matrix.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DBGPT配置属性类
 * <p>
 * 统一管理DBGPT相关的配置参数，包括API连接配置和默认参数配置
 *
 * @author caoyuan
 * @date 2024/10/10
 */
@Data
@Component
@ConfigurationProperties(prefix = "matrix.dbgpt")
public class DbgptProperties {

	/**
	 * DBGPT API基础URL
	 */
	private String baseUrl = "http://106.14.140.127:5670";

	/**
	 * DBGPT API密钥
	 */
	private String key = "dbgpt";

	/**
	 * 请求超时时间（毫秒）
	 */
	private int timeout = 30000;

	/**
	 * 默认数据库名称
	 */
	private String database = "dbgpt_test";

	/**
	 * 默认模型名称
	 */
	private String model = "deepseek-ai/DeepSeek-V3.1-Terminus";

	/**
	 * 默认聊天模式
	 */
	private String chatMode = "chat_data";

}
