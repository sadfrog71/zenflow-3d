package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.discover.model.Plugin;
import com.deepdimen.matrix.discover.model.PluginMeta;
import com.deepdimen.matrix.discover.model.PluginResponse;
import com.deepdimen.matrix.discover.service.PluginService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class PluginServiceImpl implements PluginService {

	private final ObjectMapper objectMapper;

	private final ResourceLoader resourceLoader;

	@Override
	public PluginResponse getAllPlugins(String locale) {
		try {
			log.info("Getting all plugins for locale: {}", locale);

			// 创建响应对象
			PluginResponse response = new PluginResponse();
			response.setSchemaVersion(1);

			// 获取所有插件
			List<Plugin> plugins = new ArrayList<>();
			Set<String> allTags = new HashSet<>();

			// 尝试直接从类路径加载资源
			try {
				// 获取类加载器
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
				if (classLoader == null) {
					classLoader = getClass().getClassLoader();
				}

				// 打印类加载器信息
				log.info("Using ClassLoader: {}", classLoader);

				// 使用ResourcePatternResolver
				ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);

				// 获取所有插件目录
				Resource[] pluginDirs = resolver.getResources("classpath*:plugins/*/index.json");

				if (pluginDirs.length == 0) {
					log.warn("No plugins found in classpath");
					return response;
				}

				log.info("Found {} plugin directories", pluginDirs.length);

				// 处理每个插件目录
				for (Resource indexResource : pluginDirs) {
					String pluginPath = indexResource.getURL().toString();
					String pluginName = extractPluginName(pluginPath);

					log.info("Processing plugin: {} at {}", pluginName, pluginPath);

					try (InputStream inputStream = indexResource.getInputStream()) {
						// 读取并解析index.json文件
						Plugin plugin = objectMapper.readValue(inputStream, Plugin.class);

						// 查找对应的语言文件
						String identifier = plugin.getIdentifier();
						String localeResourcePath = "classpath:plugins/" + pluginName + "/locales/" + identifier + "."
								+ locale + ".json";
						log.info("Looking for locale resource: {}", localeResourcePath);
						Resource localeResource = resourceLoader.getResource(localeResourcePath);

						if (localeResource.exists()) {
							log.info("Found locale resource: {} (exists: {})", localeResource.getFilename(),
									localeResource.exists());
							try (InputStream localeInputStream = localeResource.getInputStream()) {
								// 读取语言文件
								JsonNode localeNode = objectMapper.readTree(localeInputStream);

								// 如果存在meta节点，则只更新本地化文件中存在的字段
								if (localeNode.has("meta")) {
									JsonNode metaNode = localeNode.get("meta");
									PluginMeta originalMeta = plugin.getMeta();

									// 确保meta不为null
									if (originalMeta == null) {
										originalMeta = new PluginMeta();
										plugin.setMeta(originalMeta);
									}

									// 更新title（如果本地化文件中存在）
									if (metaNode.has("title")) {
										originalMeta.setTitle(metaNode.get("title").asText());
									}

									// 更新description（如果本地化文件中存在）
									if (metaNode.has("description")) {
										originalMeta.setDescription(metaNode.get("description").asText());
									}

									// 更新tags（如果本地化文件中存在）
									if (metaNode.has("tags")) {
										JsonNode tagsNode = metaNode.get("tags");
										String[] tags = new String[tagsNode.size()];
										for (int i = 0; i < tagsNode.size(); i++) {
											tags[i] = tagsNode.get(i).asText();
										}
										originalMeta.setTags(tags);
									}

									// 更新category（如果本地化文件中存在）
									if (metaNode.has("category")) {
										originalMeta.setCategory(metaNode.get("category").asText());
									}

									log.info("Updated plugin meta fields from locale file: {}",
											localeResource.getFilename());
								}
							}
							catch (IOException e) {
								log.error("Error reading locale plugin file: {}", localeResource.getFilename(), e);
							}
						}
						else {
							log.warn("Locale resource not found: {}", localeResourcePath);
						}

						plugins.add(plugin);
						log.info("Added plugin: {}", plugin.getIdentifier());

						// 收集所有标签
						if (plugin.getMeta() != null && plugin.getMeta().getTags() != null) {
							allTags.addAll(List.of(plugin.getMeta().getTags()));
						}
					}
					catch (IOException e) {
						// 记录错误但继续处理其他文件
						log.error("Error reading plugin file: {}", indexResource.getFilename(), e);
					}
				}
			}
			catch (Exception e) {
				log.error("Error loading plugin resources", e);
			}

			// 设置响应数据
			response.setPlugins(plugins);
			response.setTags(new ArrayList<>(allTags));

			log.info("Returning {} plugins and {} tags", plugins.size(), allTags.size());
			return response;

		}
		catch (Exception e) {
			log.error("Error processing plugin files", e);
			throw new RuntimeException("Error processing plugin files: " + e.getMessage(), e);
		}
	}

	@Override
	public JsonNode getPluginManifest(String pluginId) throws IOException {
		String resourcePath = "classpath:plugins/" + pluginId + "/manifest.json";
		log.info("Getting manifest.json for plugin: {}, path: {}", pluginId, resourcePath);

		Resource resource = resourceLoader.getResource(resourcePath);
		if (!resource.exists()) {
			log.warn("Manifest file not found: {}", resourcePath);
			return null;
		}

		try (InputStream inputStream = resource.getInputStream()) {
			JsonNode manifestNode = objectMapper.readTree(inputStream);
			log.info("Successfully read manifest.json for plugin: {}", pluginId);
			return manifestNode;
		}
		catch (IOException e) {
			log.error("Error reading manifest file for plugin: {}", pluginId, e);
			throw e;
		}
	}

	@Override
	public JsonNode getPluginOpenApi(String pluginId) throws IOException {
		String resourcePath = "classpath:plugins/" + pluginId + "/openapi.json";
		log.info("Getting openapi.json for plugin: {}, path: {}", pluginId, resourcePath);

		Resource resource = resourceLoader.getResource(resourcePath);
		if (!resource.exists()) {
			log.warn("OpenAPI file not found: {}", resourcePath);
			return null;
		}

		try (InputStream inputStream = resource.getInputStream()) {
			JsonNode openapiNode = objectMapper.readTree(inputStream);
			log.info("Successfully read openapi.json for plugin: {}", pluginId);
			return openapiNode;
		}
		catch (IOException e) {
			log.error("Error reading OpenAPI file for plugin: {}", pluginId, e);
			throw e;
		}
	}

	/**
	 * 从插件资源路径中提取插件名称
	 * @param pluginPath 插件资源路径
	 * @return 插件名称
	 */
	private String extractPluginName(String pluginPath) {
		// 路径格式：file:/path/to/resources/plugins/PluginName/index.json 或
		// jar:file:/path/to/jar!/plugins/PluginName/index.json

		// 首先提取plugins/之后的部分
		int pluginsIndex = pluginPath.indexOf("plugins/");
		if (pluginsIndex == -1) {
			// 尝试Windows风格的路径
			pluginsIndex = pluginPath.indexOf("plugins\\");
		}

		if (pluginsIndex == -1) {
			log.warn("Could not extract plugin name from path: {}", pluginPath);
			return "";
		}

		String afterPlugins = pluginPath.substring(pluginsIndex + "plugins/".length());

		// 然后获取第一级目录名
		int slashIndex = afterPlugins.indexOf("/");
		if (slashIndex == -1) {
			// 尝试Windows风格的路径
			slashIndex = afterPlugins.indexOf("\\");
		}

		if (slashIndex == -1) {
			log.warn("Could not find directory separator in path: {}", afterPlugins);
			return afterPlugins; // 返回整个字符串，可能不准确
		}

		return afterPlugins.substring(0, slashIndex);
	}

}