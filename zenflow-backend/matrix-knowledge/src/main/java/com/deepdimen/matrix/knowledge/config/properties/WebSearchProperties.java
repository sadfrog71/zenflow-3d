package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * 网络搜索配置
 *
 * @author caoyuan
 * @date 2025/02/24
 */
@Data
public class WebSearchProperties {

	/**
	 * 搜索超时时间（秒）
	 */
	private int duration = 10;

	/**
	 * 最大返回结果数
	 */
	private int maxResults = 5;

}
