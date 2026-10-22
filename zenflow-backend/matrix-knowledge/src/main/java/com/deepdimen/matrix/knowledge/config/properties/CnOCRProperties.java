package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * 中文OCR配置属性
 *
 * @author caoyuan
 * @date 2024/3/20
 */
@Data
public class CnOCRProperties {

	/**
	 * 是否启用中文OCR服务
	 */
	private boolean enabled = false;

	/**
	 * 中文OCR服务地址
	 */
	private String baseUrl = "http://127.0.0.1:1224";

}
