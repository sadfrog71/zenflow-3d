package com.deepdimen.matrix.discover.dify.api;

import com.deepdimen.matrix.discover.dify.DifyConsoleClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify Console API 应用管理接口封装
 * <p>
 * 基于 DifyConsoleClient 通用方法封装，返回值统一为 JsonNode 透传。 后续新增接口只需添加方法调用。
 *
 * @author matrix
 * @date 2026/03/14
 */
public class DifyAppApi {

	private static final String APPS_PATH = "/console/api/apps";

	private static final String WORKSPACE_PATH = "/console/api/workspaces/current";

	private static final String DATASETS_PATH = "/console/api/datasets";

	private static final String EXTERNAL_KB_API_PATH = "/console/api/datasets/external-knowledge-api";

	private static final String TAGS_PATH = "/console/api/tags";

	private static final String TAG_BINDINGS_PATH = "/console/api/tag-bindings";

	private final DifyConsoleClient client;

	public DifyAppApi(DifyConsoleClient client) {
		this.client = client;
	}

	/**
	 * 获取应用列表
	 * @param page 页码
	 * @param limit 每页数量
	 * @param mode 应用模式过滤（all/chat/agent-chat/workflow等），可为null
	 * @param name 名称搜索关键词，可为null
	 * @param tagIds 标签ID过滤（逗号分隔），可为null
	 * @return 应用列表 JSON
	 */
	public JsonNode listApps(int page, int limit, String mode, String name, String tagIds) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("page", String.valueOf(page));
		params.put("limit", String.valueOf(limit));
		if (StringUtils.hasText(mode)) {
			params.put("mode", mode);
		}
		if (StringUtils.hasText(name)) {
			params.put("name", name);
		}
		if (StringUtils.hasText(tagIds)) {
			params.put("tag_ids", tagIds);
		}
		return client.get(APPS_PATH, params);
	}

	/**
	 * 创建应用
	 * @param requestBody 创建参数（name, mode, icon, description等）
	 * @return 创建结果 JSON
	 */
	public JsonNode createApp(Object requestBody) {
		return client.post(APPS_PATH, requestBody);
	}

	/**
	 * 获取应用详情
	 * @param appId 应用ID
	 * @return 应用详情 JSON
	 */
	public JsonNode getApp(String appId) {
		Assert.hasText(appId, "appId 不能为空");
		return client.get(APPS_PATH + "/" + appId);
	}

	/**
	 * 更新应用
	 * @param appId 应用ID
	 * @param requestBody 更新参数
	 * @return 更新结果 JSON
	 */
	public JsonNode updateApp(String appId, Object requestBody) {
		Assert.hasText(appId, "appId 不能为空");
		return client.put(APPS_PATH + "/" + appId, requestBody);
	}

	/**
	 * 删除应用
	 * @param appId 应用ID
	 * @return 删除结果 JSON
	 */
	public JsonNode deleteApp(String appId) {
		Assert.hasText(appId, "appId 不能为空");
		return client.delete(APPS_PATH + "/" + appId);
	}

	/**
	 * 复制应用
	 * @param appId 应用ID
	 * @param requestBody 复制参数（name, icon, icon_background等）
	 * @return 复制结果 JSON
	 */
	public JsonNode copyApp(String appId, Object requestBody) {
		Assert.hasText(appId, "appId 不能为空");
		return client.post(APPS_PATH + "/" + appId + "/copy", requestBody);
	}

	/**
	 * 导出应用（YAML格式）
	 * @param appId 应用ID
	 * @return 导出内容 JSON
	 */
	public JsonNode exportApp(String appId) {
		Assert.hasText(appId, "appId 不能为空");
		return client.get(APPS_PATH + "/" + appId + "/export");
	}

	/**
	 * 导入应用
	 * @param requestBody 导入参数（data: YAML内容 或 url: 远程地址）
	 * @return 导入结果 JSON
	 */
	public JsonNode importApp(Object requestBody) {
		return client.post(APPS_PATH + "/imports", requestBody);
	}

	/**
	 * 确认导入应用
	 * @param importId 导入ID
	 * @param requestBody 确认参数
	 * @return 确认结果 JSON
	 */
	public JsonNode confirmImport(String importId, Object requestBody) {
		Assert.hasText(importId, "importId 不能为空");
		return client.post(APPS_PATH + "/imports/" + importId + "/confirm", requestBody);
	}

	/**
	 * 更新应用名称
	 * @param appId 应用ID
	 * @param requestBody 名称参数（name）
	 * @return 更新结果 JSON
	 */
	public JsonNode updateAppName(String appId, Object requestBody) {
		Assert.hasText(appId, "appId 不能为空");
		return client.post(APPS_PATH + "/" + appId + "/name", requestBody);
	}

	/**
	 * 更新应用图标
	 * @param appId 应用ID
	 * @param requestBody 图标参数（icon, icon_background）
	 * @return 更新结果 JSON
	 */
	public JsonNode updateAppIcon(String appId, Object requestBody) {
		Assert.hasText(appId, "appId 不能为空");
		return client.post(APPS_PATH + "/" + appId + "/icon", requestBody);
	}

	/**
	 * 更新应用模型配置（提示词、模型参数、开场白等）
	 * @param appId 应用ID
	 * @param requestBody 模型配置参数
	 * @return 更新结果 JSON
	 */
	public JsonNode updateModelConfig(String appId, Object requestBody) {
		Assert.hasText(appId, "appId 不能为空");
		return client.post(APPS_PATH + "/" + appId + "/model-config", requestBody);
	}

	/**
	 * 发布 Workflow（advanced-chat/workflow 类型应用必须发布后才能通过 Service API 调用）
	 * @param appId 应用ID
	 * @return 发布结果 JSON
	 */
	public JsonNode publishWorkflow(String appId) {
		Assert.hasText(appId, "appId 不能为空");
		return client.post(APPS_PATH + "/" + appId + "/workflows/publish", Map.of());
	}

	/**
	 * 启用/禁用应用站点
	 * @param appId 应用ID
	 * @param requestBody 状态参数（enable_site: true/false）
	 * @return 更新结果 JSON
	 */
	public JsonNode updateSiteStatus(String appId, Object requestBody) {
		Assert.hasText(appId, "appId 不能为空");
		return client.post(APPS_PATH + "/" + appId + "/site-enable", requestBody);
	}

	/**
	 * 获取应用 API 密钥列表
	 * @param appId 应用ID
	 * @return API 密钥列表 JSON，格式 {"data": [{"id":"...", "token":"app-xxx", ...}]}
	 */
	public JsonNode listApiKeys(String appId) {
		Assert.hasText(appId, "appId 不能为空");
		return client.get(APPS_PATH + "/" + appId + "/api-keys");
	}

	/**
	 * 为应用创建 API 密钥
	 * @param appId 应用ID
	 * @return 创建的 API 密钥 JSON，格式 {"id":"...", "token":"app-xxx", ...}
	 */
	public JsonNode createApiKey(String appId) {
		Assert.hasText(appId, "appId 不能为空");
		return client.post(APPS_PATH + "/" + appId + "/api-keys", Map.of());
	}

	// ==================== 外部知识库 API ====================

	/**
	 * 获取外部知识库 API 模板列表
	 * @param page 页码
	 * @param limit 每页数量
	 * @return 外部知识库 API 列表 JSON
	 */
	public JsonNode listExternalKnowledgeApis(int page, int limit) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("page", String.valueOf(page));
		params.put("limit", String.valueOf(limit));
		return client.get(EXTERNAL_KB_API_PATH, params);
	}

	/**
	 * 创建外部知识库 API 模板
	 * @param name 模板名称
	 * @param endpoint API 端点地址
	 * @param apiKey 认证密钥
	 * @return 创建结果 JSON（含 id 字段）
	 */
	public JsonNode createExternalKnowledgeApi(String name, String endpoint, String apiKey) {
		Assert.hasText(name, "name 不能为空");
		Assert.hasText(endpoint, "endpoint 不能为空");
		Assert.hasText(apiKey, "apiKey 不能为空");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("settings", Map.of("endpoint", endpoint, "api_key", apiKey));
		return client.post(EXTERNAL_KB_API_PATH, body);
	}

	/**
	 * 更新外部知识库 API 模板
	 * @param apiId 模板 ID
	 * @param name 模板名称
	 * @param endpoint API 端点地址
	 * @param apiKey 认证密钥
	 * @return 更新结果 JSON
	 */
	public JsonNode updateExternalKnowledgeApi(String apiId, String name, String endpoint, String apiKey) {
		Assert.hasText(apiId, "apiId 不能为空");
		Assert.hasText(name, "name 不能为空");
		Assert.hasText(endpoint, "endpoint 不能为空");
		Assert.hasText(apiKey, "apiKey 不能为空");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("settings", Map.of("endpoint", endpoint, "api_key", apiKey));
		return client.patch(EXTERNAL_KB_API_PATH + "/" + apiId, body);
	}

	/**
	 * 创建外部知识库数据集
	 * @param externalKnowledgeApiId 外部知识库 API 模板 ID
	 * @param externalKnowledgeId 外部知识库 ID（Matrix 的 datasetId）
	 * @param name 数据集名称
	 * @param topK 检索返回数量
	 * @param scoreThreshold 分数阈值
	 * @return 创建结果 JSON（含 id 字段）
	 */
	public JsonNode createExternalDataset(String externalKnowledgeApiId, String externalKnowledgeId, String name,
			int topK, double scoreThreshold) {
		Assert.hasText(externalKnowledgeApiId, "externalKnowledgeApiId 不能为空");
		Assert.hasText(externalKnowledgeId, "externalKnowledgeId 不能为空");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("external_knowledge_api_id", externalKnowledgeApiId);
		body.put("external_knowledge_id", externalKnowledgeId);
		body.put("name", name);
		body.put("external_retrieval_model",
				Map.of("top_k", topK, "score_threshold", scoreThreshold, "score_threshold_enabled", true));
		return client.post(DATASETS_PATH + "/external", body);
	}

	/**
	 * 获取数据集列表（用于查询已创建的外部知识库数据集）
	 * @param page 页码
	 * @param limit 每页数量
	 * @return 数据集列表 JSON
	 */
	public JsonNode listDatasets(int page, int limit) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("page", String.valueOf(page));
		params.put("limit", String.valueOf(limit));
		return client.get(DATASETS_PATH, params);
	}

	/**
	 * 更新 Dify 知识库名称
	 * @param datasetId Dify 知识库ID
	 * @param name 新名称
	 * @return 更新结果 JSON
	 */
	public JsonNode updateDatasetName(String datasetId, String name) {
		Assert.hasText(datasetId, "datasetId 不能为空");
		Assert.hasText(name, "name 不能为空");
		return client.patch(DATASETS_PATH + "/" + datasetId, Map.of("name", name));
	}

	/**
	 * 获取工作区自定义 API 工具列表
	 * @return API 工具列表 JSON（数组，每项含 id/name/tools 等字段）
	 */
	public JsonNode listApiTools() {
		return client.get(WORKSPACE_PATH + "/tools/api", Map.of());
	}

	// ==================== 标签管理 API ====================

	/**
	 * 获取标签列表
	 * @param type 标签类型（app/knowledge）
	 * @return 标签列表 JSON
	 */
	public JsonNode listTags(String type) {
		Map<String, String> params = new LinkedHashMap<>();
		if (StringUtils.hasText(type)) {
			params.put("type", type);
		}
		return client.get(TAGS_PATH, params);
	}

	/**
	 * 创建标签
	 * @param name 标签名称
	 * @param type 标签类型（app/knowledge）
	 * @return 创建结果 JSON（含 id 字段）
	 */
	public JsonNode createTag(String name, String type) {
		Assert.hasText(name, "标签名称不能为空");
		Assert.hasText(type, "标签类型不能为空");
		Map<String, String> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("type", type);
		return client.post(TAGS_PATH, body);
	}

	/**
	 * 绑定标签到目标对象
	 * @param tagIds 标签ID列表
	 * @param targetId 目标对象ID（app或dataset的ID）
	 * @param type 标签类型（app/knowledge）
	 * @return 绑定结果 JSON
	 */
	public JsonNode bindTag(List<String> tagIds, String targetId, String type) {
		Assert.notEmpty(tagIds, "tagIds不能为空");
		Assert.hasText(targetId, "targetId不能为空");
		Assert.hasText(type, "type不能为空");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("tag_ids", tagIds);
		body.put("target_id", targetId);
		body.put("type", type);
		return client.post(TAG_BINDINGS_PATH + "/create", body);
	}

}
