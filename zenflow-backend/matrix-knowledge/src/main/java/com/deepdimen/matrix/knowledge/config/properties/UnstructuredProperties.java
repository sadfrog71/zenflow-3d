package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * Unstructured文档解析服务配置
 *
 * @author caoyuan
 * @date 2024/3/20
 */
@Data
public class UnstructuredProperties {

	/**
	 * 是否启用Unstructured服务
	 */
	private boolean enabled;

	/**
	 * Unstructured服务主机地址
	 */
	private String host;

	/**
	 * Unstructured服务端口号
	 */
	private String port;

}
