package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * Office文档转Markdown配置
 *
 * @author caoyuan
 * @date 2024/12/22
 */
@Data
public class Office2MdProperties {

	/**
	 * 是否启用Office转Markdown服务
	 */
	private boolean enabled = false;

	/**
	 * 转换服务地址
	 */
	private String baseUrl = "http://127.0.0.1:8000";

}
