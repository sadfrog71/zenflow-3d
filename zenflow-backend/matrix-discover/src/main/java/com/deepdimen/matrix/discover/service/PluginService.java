package com.deepdimen.matrix.discover.service;

import com.deepdimen.matrix.discover.model.PluginResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Plugin 服务
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface PluginService {

	/**
	 * 获取所有插件
	 * @param locale 语言
	 * @return 插件响应
	 */
	PluginResponse getAllPlugins(String locale);

	/**
	 * 获取插件的manifest.json内容
	 * @param pluginId 插件ID
	 * @return manifest.json内容，如果不存在则返回null
	 * @throws IOException 读取文件发生错误时抛出
	 */
	JsonNode getPluginManifest(String pluginId) throws IOException;

	/**
	 * 获取插件的openapi.json内容
	 * @param pluginId 插件ID
	 * @return openapi.json内容，如果不存在则返回null
	 * @throws IOException 读取文件发生错误时抛出
	 */
	JsonNode getPluginOpenApi(String pluginId) throws IOException;

}