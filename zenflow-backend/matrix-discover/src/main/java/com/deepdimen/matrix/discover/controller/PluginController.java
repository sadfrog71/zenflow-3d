package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.discover.model.PluginResponse;
import com.deepdimen.matrix.discover.service.PluginService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 插件控制器
 *
 * @author caoyuan
 * @date 2024/09/27
 */
@RestController
@AllArgsConstructor
@Tag(name = "插件接口")
@RequestMapping("/plugins")
@Slf4j
public class PluginController {

	private final PluginService pluginService;

	/**
	 * 获取所有插件
	 * @param locale 语言
	 * @return 插件响应
	 */
	@Inner(value = false)
	@GetMapping("/{locale}.json")
	@Operation(summary = "获取所有插件")
	public ResponseEntity<PluginResponse> getAllPlugins(@PathVariable String locale) {
		PluginResponse pluginResponse = pluginService.getAllPlugins(locale);
		return ResponseEntity.ok(pluginResponse);
	}

	/**
	 * 获取所有插件（支持index.{locale}.json格式）
	 * @param locale 语言
	 * @return 插件响应
	 */
	@Inner(value = false)
	@GetMapping("/index.{locale}.json")
	@Operation(summary = "获取所有插件（index格式）")
	public ResponseEntity<PluginResponse> getAllPluginsByIndex(@PathVariable String locale) {
		PluginResponse pluginResponse = pluginService.getAllPlugins(locale);
		return ResponseEntity.ok(pluginResponse);
	}

	/**
	 * 获取插件的manifest.json内容
	 * @param pluginId 插件ID
	 * @return manifest.json内容
	 */
	@Inner(value = false)
	@GetMapping("/{pluginId}/manifest.json")
	@Operation(summary = "获取插件的manifest.json内容")
	public ResponseEntity<JsonNode> getPluginManifest(@PathVariable String pluginId) {
		try {
			JsonNode manifestNode = pluginService.getPluginManifest(pluginId);
			if (manifestNode == null) {
				return ResponseEntity.notFound().build();
			}
			return ResponseEntity.ok(manifestNode);
		}
		catch (IOException e) {
			log.error("Error getting manifest.json for plugin: {}", pluginId, e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * 获取插件的openapi.json内容
	 * @param pluginId 插件ID
	 * @return openapi.json内容
	 */
	@Inner(value = false)
	@GetMapping("/{pluginId}/openapi.json")
	@Operation(summary = "获取插件的openapi.json内容")
	public ResponseEntity<JsonNode> getPluginOpenApi(@PathVariable String pluginId) {
		try {
			JsonNode openapiNode = pluginService.getPluginOpenApi(pluginId);
			if (openapiNode == null) {
				return ResponseEntity.notFound().build();
			}
			return ResponseEntity.ok(openapiNode);
		}
		catch (IOException e) {
			log.error("Error getting openapi.json for plugin: {}", pluginId, e);
			return ResponseEntity.internalServerError().build();
		}
	}

}