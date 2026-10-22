package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 小米物联网配置属性
 *
 * @author caoyuan
 * @date 2024/12/22
 */
@Data
public class MiIotProperties {

	/**
	 * 是否启用小米物联网功能
	 */
	private boolean enabled;

	/**
	 * Home Assistant服务地址
	 */
	private String baseUrl = "http://127.0.0.1:8123";

	/**
	 * Home Assistant API密钥
	 */
	private String apiKey;

	/**
	 * 允许控制的设备实体ID列表
	 */
	private List<String> entityIdList = new ArrayList<>();

}
