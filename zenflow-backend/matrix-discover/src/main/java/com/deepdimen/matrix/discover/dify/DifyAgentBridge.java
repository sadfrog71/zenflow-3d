package com.deepdimen.matrix.discover.dify;

import com.deepdimen.matrix.common.security.component.ApiTokenValidator;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.deepdimen.matrix.discover.dify.config.DifyProperties;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体 Dify 桥接服务
 * <p>
 * 封装智能体与 Dify 的所有交互，包括：
 * <ul>
 * <li>Dify 应用信息本地缓存（TTL 60秒，最大500条）</li>
 * <li>查询聚合：详情/列表接口聚合 Dify 应用信息</li>
 * <li>写操作联动：创建/更新/删除/发布时同步 Dify 应用</li>
 * </ul>
 * AiAgentServiceImpl 通过本类间接调用 DifyAppApi，不直接依赖底层 API。
 *
 * @author matrix
 * @date 2026/03/14
 */
@Slf4j
public class DifyAgentBridge {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final long DIFY_CACHE_TTL_MS = 60_000L;

	private static final int DIFY_CACHE_MAX_SIZE = 500;

	private static final String TENANT_TAG_PREFIX = "matrix-";

	private final DifyAppApi difyAppApi;

	private final ApiTokenValidator apiTokenValidator;

	private final DifyProperties difyProperties;

	private final ConcurrentHashMap<String, DifyCacheEntry> difyAppCache = new ConcurrentHashMap<>();

	public DifyAgentBridge(DifyAppApi difyAppApi, ApiTokenValidator apiTokenValidator, DifyProperties difyProperties) {
		this.difyAppApi = difyAppApi;
		this.apiTokenValidator = apiTokenValidator;
		this.difyProperties = difyProperties;
	}

	/**
	 * 从缓存获取 Dify 应用信息，未命中或过期时调用 API 并缓存
	 */
	public JsonNode getCachedDifyApp(String difyAppId) {
		DifyCacheEntry entry = difyAppCache.get(difyAppId);
		if (entry != null && !entry.isExpired()) {
			return entry.data();
		}
		synchronized (difyAppId.intern()) {
			entry = difyAppCache.get(difyAppId);
			if (entry != null && !entry.isExpired()) {
				return entry.data();
			}
			JsonNode result = difyAppApi.getApp(difyAppId);
			if (result != null) {
				if (difyAppCache.size() > DIFY_CACHE_MAX_SIZE) {
					difyAppCache.entrySet().removeIf(e -> e.getValue().isExpired());
				}
				difyAppCache.put(difyAppId, new DifyCacheEntry(result, System.currentTimeMillis() + DIFY_CACHE_TTL_MS));
			}
			return result;
		}
	}

	// ==================== 缓存 ====================

	/**
	 * 清除指定 Dify 应用的缓存
	 */
	public void evictCache(String difyAppId) {
		if (StringUtils.hasText(difyAppId)) {
			difyAppCache.remove(difyAppId);
		}
	}

	/**
	 * 单条智能体聚合 Dify 应用信息
	 */
	public void mergeDifyInfo(AiAgentVO vo) {
		if (!StringUtils.hasText(vo.getDifyAppId())) {
			return;
		}
		try {
			JsonNode difyApp = getCachedDifyApp(vo.getDifyAppId());
			applyDifyFields(vo, difyApp);
		}
		catch (Exception e) {
			log.warn("获取Dify应用信息失败, difyAppId={}, error={}", vo.getDifyAppId(), e.getMessage());
		}
	}

	// ==================== 查询聚合（任务3.2实现） ====================

	/**
	 * 批量聚合 Dify 应用信息（逐条调用 getApp + 本地缓存，保证字段完整且无数量限制）
	 */
	public void batchMergeDifyInfo(List<AiAgentVO> voList) {
		if (voList == null || voList.isEmpty()) {
			return;
		}
		for (AiAgentVO vo : voList) {
			if (!StringUtils.hasText(vo.getDifyAppId())) {
				continue;
			}
			try {
				JsonNode difyApp = getCachedDifyApp(vo.getDifyAppId());
				if (difyApp != null) {
					applyDifyFields(vo, difyApp);
				}
			}
			catch (Exception e) {
				log.warn("获取Dify应用信息失败, difyAppId={}, error={}", vo.getDifyAppId(), e.getMessage());
			}
		}
	}

	/**
	 * 将 Dify 应用字段合并到 VO 中，同名字段覆盖，其余放入 difyInfo
	 */
	private void applyDifyFields(AiAgentVO vo, JsonNode difyApp) {
		if (difyApp == null) {
			return;
		}
		// 同名字段覆盖（avatar 由 Matrix 独立管理，不从 Dify icon 同步）
		if (difyApp.has("name") && !difyApp.get("name").isNull()) {
			vo.setName(difyApp.get("name").asText());
		}
		if (difyApp.has("description") && !difyApp.get("description").isNull()) {
			vo.setDescription(difyApp.get("description").asText());
		}
		// 其余 Dify 字段放入 difyInfo 对象
		Map<String, Object> difyInfo = new HashMap<>();
		Set<String> skipFields = Set.of("name", "description", "icon");
		Iterator<Map.Entry<String, JsonNode>> fields = difyApp.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			if (skipFields.contains(entry.getKey())) {
				continue;
			}
			JsonNode value = entry.getValue();
			if (value.isNull()) {
				difyInfo.put(entry.getKey(), null);
			}
			else if (value.isTextual()) {
				difyInfo.put(entry.getKey(), value.asText());
			}
			else if (value.isNumber()) {
				difyInfo.put(entry.getKey(), value.numberValue());
			}
			else if (value.isBoolean()) {
				difyInfo.put(entry.getKey(), value.booleanValue());
			}
			else if (value.isArray()) {
				difyInfo.put(entry.getKey(), OBJECT_MAPPER.convertValue(value, List.class));
			}
			else if (value.isObject()) {
				difyInfo.put(entry.getKey(), OBJECT_MAPPER.convertValue(value, Map.class));
			}
		}
		vo.setDifyInfo(difyInfo);
	}

	/**
	 * 创建 Dify 应用，返回 appId；失败返回 null
	 * @param mode Dify 应用模式（agent-chat/advanced-chat/chat/workflow/completion），为空默认
	 * agent-chat
	 */
	public String createDifyApp(String name, String description, String mode) {
		try {
			Map<String, Object> body = new HashMap<>();
			body.put("name", name);
			body.put("mode", StringUtils.hasText(mode) ? mode : "agent-chat");
			if (StringUtils.hasText(description)) {
				body.put("description", description);
			}
			JsonNode result = difyAppApi.createApp(body);
			if (result != null && result.has("id")) {
				String appId = result.get("id").asText();
				log.info("联动创建Dify应用成功, difyAppId={}", appId);
				return appId;
			}
		}
		catch (Exception e) {
			log.warn("联动创建Dify应用失败, name={}, error={}", name, e.getMessage());
		}
		return null;
	}

	// ==================== 写操作联动（任务3.3实现） ====================

	/**
	 * 同步更新 Dify 应用信息；失败仅记录日志
	 */
	public void syncDifyApp(String difyAppId, String name, String description) {
		try {
			Map<String, Object> body = new HashMap<>();
			if (StringUtils.hasText(name)) {
				body.put("name", name);
			}
			// description 允许传空串以清空 Dify 侧的值，name 不允许清空
			if (description != null) {
				body.put("description", description);
			}
			if (!body.isEmpty()) {
				difyAppApi.updateApp(difyAppId, body);
				log.info("联动更新Dify应用成功, difyAppId={}", difyAppId);
				evictCache(difyAppId);
			}
		}
		catch (Exception e) {
			log.warn("联动更新Dify应用失败, difyAppId={}, error={}", difyAppId, e.getMessage());
		}
	}

	/**
	 * 发布时同步智能体配置到 Dify 应用（提示词、模型参数、开场白等）
	 * <p>
	 * 先获取 Dify 应用当前的 model_config 作为基础，再用本地 config 覆盖关键字段， 避免丢失 Dify 侧已有的配置（如
	 * agent_mode.tools 等）。
	 */
	@SuppressWarnings("unchecked")
	public void syncDifyModelConfig(String difyAppId, String configJson) {
		if (!StringUtils.hasText(difyAppId) || !StringUtils.hasText(configJson)) {
			return;
		}
		try {
			JsonNode localConfig = OBJECT_MAPPER.readTree(configJson);

			// 获取 Dify 应用当前配置作为基础
			JsonNode difyApp = difyAppApi.getApp(difyAppId);
			if (difyApp == null) {
				log.warn("获取Dify应用失败, 无法同步配置, difyAppId={}", difyAppId);
				return;
			}
			// advanced-chat / workflow 的配置在 workflow DSL 中，不应通过 model_config 覆盖
			String appMode = difyApp.path("mode").asText("");
			if ("advanced-chat".equals(appMode) || "workflow".equals(appMode)) {
				log.info("Dify应用为{}模式, 跳过model_config同步, difyAppId={}", appMode, difyAppId);
				return;
			}
			Map<String, Object> modelConfig;
			JsonNode existingConfig = difyApp.get("model_config");
			if (existingConfig != null && !existingConfig.isNull() && existingConfig.size() > 0) {
				modelConfig = OBJECT_MAPPER.convertValue(existingConfig, Map.class);
			}
			else {
				// Dify 1.13+ 要求完整的 model_config 结构，新建应用配置为空时填充默认值
				modelConfig = buildDefaultModelConfig();
			}

			// 覆盖提示词
			if (localConfig.has("systemPrompt")) {
				String systemPrompt = localConfig.get("systemPrompt").asText("");
				// 无条件注入 {{extended_system_prompt}} 占位符，确保发布后 Dify 能接收动态提示词。
				// 即使未绑定 artifact 技能，Dify 对未声明变量的占位符会替换为空，功能无影响。
				// 解绑 artifact 时由 syncArtifactVariable 清除占位符和变量声明。
				if (!systemPrompt.contains("{{extended_system_prompt}}")) {
					systemPrompt = systemPrompt + "\n\n{{extended_system_prompt}}";
				}
				modelConfig.put("pre_prompt", systemPrompt);
			}

			// 覆盖开场白
			if (localConfig.has("openingStatement")) {
				modelConfig.put("opening_statement", localConfig.get("openingStatement").asText(""));
			}

			// 覆盖示例问题
			if (localConfig.has("sampleQuestions") && localConfig.get("sampleQuestions").isArray()) {
				List<String> questions = new ArrayList<>();
				for (JsonNode q : localConfig.get("sampleQuestions")) {
					if (q.isTextual() && StringUtils.hasText(q.asText())) {
						questions.add(q.asText());
					}
				}
				modelConfig.put("suggested_questions", questions);
			}

			// 覆盖模型参数
			if (localConfig.has("modelConfig")) {
				JsonNode mc = localConfig.get("modelConfig");
				// Dify 1.13+ 要求 model.provider 和 model.name 为有效值，未配置时跳过同步
				if (!mc.has("modelName") || !StringUtils.hasText(mc.get("modelName").asText()) || !mc.has("provider")
						|| !StringUtils.hasText(mc.get("provider").asText())) {
					log.info("智能体未配置模型, 提示词等配置将在选择模型后同步到Dify, difyAppId={}", difyAppId);
					return;
				}
				Object modelRaw = modelConfig.get("model");
				Map<String, Object> model = (modelRaw instanceof Map) ? (Map<String, Object>) modelRaw
						: new HashMap<>();
				model.put("name", mc.get("modelName").asText());
				model.put("provider", mc.get("provider").asText());
				// agent-chat 模式必须为 chat，否则 Dify 会使用错误的 prompt 模板
				model.put("mode", "chat");
				Object paramsRaw = model.get("completion_params");
				Map<String, Object> params = (paramsRaw instanceof Map) ? (Map<String, Object>) paramsRaw
						: new HashMap<>();
				if (mc.has("temperature")) {
					params.put("temperature", mc.get("temperature").numberValue());
				}
				if (mc.has("maxTokens")) {
					params.put("max_tokens", mc.get("maxTokens").intValue());
				}
				// 清理废弃参数，避免 Dify 侧残留旧值
				params.remove("top_p");
				params.remove("frequency_penalty");
				params.remove("presence_penalty");
				// Dify 1.13+ 要求 completion_params.stop 字段存在
				params.putIfAbsent("stop", new ArrayList<>());
				model.put("completion_params", params);
				modelConfig.put("model", model);
			}
			else {
				// 没有 modelConfig 节点，跳过同步
				log.info("智能体配置中无modelConfig节点, 跳过同步配置到Dify, difyAppId={}", difyAppId);
				return;
			}

			// 确保 agent_mode 存在且启用（agent-chat 模式必需，否则 Dify 运行时 agent_entity 为 None）
			// 注意: 此方法不处理 dataset_configs，由 syncDatasets -> updateDifyAppDatasets 负责。
			// 调用顺序: syncDifyModelConfig -> syncDatasets，不可调换。
			Object agentModeRaw = modelConfig.get("agent_mode");
			if (!(agentModeRaw instanceof Map)) {
				Map<String, Object> agentMode = new HashMap<>();
				agentMode.put("enabled", true);
				agentMode.put("strategy", "function_call");
				agentMode.put("tools", new ArrayList<>());
				modelConfig.put("agent_mode", agentMode);
			}
			else {
				@SuppressWarnings("unchecked")
				Map<String, Object> agentMode = (Map<String, Object>) agentModeRaw;
				if (!Boolean.TRUE.equals(agentMode.get("enabled"))) {
					agentMode.put("enabled", true);
				}
				if (agentMode.get("strategy") == null) {
					agentMode.put("strategy", "function_call");
				}
				// 清理 agent_mode.tools 中残留的 dataset-retrieval 条目（历史数据兼容）
				// 注意: 不清理缺少 provider_type 的旧格式工具，Dify 1.13 仍支持旧格式
				Object toolsRaw = agentMode.get("tools");
				if (toolsRaw instanceof List) {
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsRaw;
					tools.removeIf(t -> "dataset-retrieval".equals(t.get("provider_type")));
				}
			}

			// 确保 Features 开关为期望的默认值（Dify 侧新建应用默认全部关闭）
			ensureFeatureDefaults(modelConfig);

			difyAppApi.updateModelConfig(difyAppId, modelConfig);
			log.info("同步配置到Dify成功, difyAppId={}", difyAppId);
			evictCache(difyAppId);
		}
		catch (Exception e) {
			log.error("同步配置到Dify失败, difyAppId={}, error={}", difyAppId, e.getMessage(), e);
		}
	}

	/**
	 * 获取或创建 Dify 应用的 API Key
	 * <p>
	 * 先查询已有的 API Key，如果存在则直接返回第一个 token； 不存在则创建新的 API Key 并返回 token。幂等安全。
	 * @param difyAppId Dify 应用 ID
	 * @return API Key token（如 "app-xxx"），失败返回 null
	 */
	public String getOrCreateApiKey(String difyAppId) {
		if (!StringUtils.hasText(difyAppId)) {
			return null;
		}
		try {
			// 先查询已有的 API Key
			JsonNode listResult = difyAppApi.listApiKeys(difyAppId);
			if (listResult != null && listResult.has("data") && listResult.get("data").isArray()
					&& !listResult.get("data").isEmpty()) {
				JsonNode firstKey = listResult.get("data").get(0);
				if (firstKey != null && firstKey.has("token")) {
					String token = firstKey.get("token").asText();
					log.info("Dify应用已有API Key, difyAppId={}", difyAppId);
					return token;
				}
			}
			// 没有则创建
			JsonNode createResult = difyAppApi.createApiKey(difyAppId);
			if (createResult != null && createResult.has("token")) {
				String token = createResult.get("token").asText();
				log.info("创建Dify API Key成功, difyAppId={}", difyAppId);
				return token;
			}
		}
		catch (Exception e) {
			log.warn("获取或创建Dify API Key失败, difyAppId={}, error={}", difyAppId, e.getMessage());
		}
		return null;
	}

	/**
	 * 联动删除 Dify 应用
	 */
	public void deleteDifyApp(String difyAppId) {
		if (!StringUtils.hasText(difyAppId)) {
			return;
		}
		try {
			difyAppApi.deleteApp(difyAppId);
			log.info("联动删除Dify应用成功, difyAppId={}", difyAppId);
			evictCache(difyAppId);
		}
		catch (Exception e) {
			log.warn("联动删除Dify应用失败, difyAppId={}, error={}", difyAppId, e.getMessage());
		}
	}

	/**
	 * 发布 Dify Workflow（advanced-chat/workflow 类型必须发布后才能通过 Service API 调用）
	 * @param difyAppId Dify 应用 ID
	 */
	public void publishDifyWorkflow(String difyAppId) {
		if (!StringUtils.hasText(difyAppId)) {
			return;
		}
		try {
			difyAppApi.publishWorkflow(difyAppId);
			log.info("发布Dify Workflow成功, difyAppId={}", difyAppId);
			evictCache(difyAppId);
		}
		catch (Exception e) {
			log.warn("发布Dify Workflow失败, difyAppId={}, error={}", difyAppId, e.getMessage());
		}
	}

	// ==================== DSL导入（任务3.10实现） ====================

	/**
	 * 导入 Dify DSL 创建应用，返回 [difyAppId, difyApiKey]；失败返回 null
	 * <p>
	 * 两步导入流程：importApp 获取 importId → confirmImport 确认导入获取 appId → getOrCreateApiKey 获取
	 * API Key
	 * @param dslContent YAML 格式的 DSL 内容
	 * @return String[]{difyAppId, difyApiKey}，失败返回 null
	 */
	public String[] importDifyApp(String dslContent) {
		try {
			// 第一步：导入 DSL（Dify 1.13+ 要求 mode + yaml_content 格式）
			Map<String, Object> importBody = new HashMap<>();
			importBody.put("mode", "yaml-content");
			importBody.put("yaml_content", dslContent);
			JsonNode importResult = difyAppApi.importApp(importBody);
			if (importResult == null) {
				log.warn("导入Dify DSL失败: importApp返回null");
				return null;
			}
			// Dify 1.x 导入接口可能直接返回 app_id（无需 confirm），也可能返回 import_id 需要二次确认
			String difyAppId = null;
			if (importResult.has("app_id") && StringUtils.hasText(importResult.get("app_id").asText(""))) {
				difyAppId = importResult.get("app_id").asText();
			}
			else if (importResult.has("id") && StringUtils.hasText(importResult.get("id").asText(""))) {
				// 需要确认导入
				String importId = importResult.get("id").asText();
				JsonNode confirmResult = difyAppApi.confirmImport(importId, Map.of());
				if (confirmResult != null && confirmResult.has("app_id")) {
					difyAppId = confirmResult.get("app_id").asText();
				}
			}
			if (!StringUtils.hasText(difyAppId)) {
				log.warn("导入Dify DSL失败: 无法获取appId, response={}", importResult);
				return null;
			}
			// 获取或创建 API Key
			String apiKey = getOrCreateApiKey(difyAppId);
			// advanced-chat/workflow 导入后自动发布 workflow（未发布的 workflow 无法通过 Service API 调用）
			JsonNode difyApp = getCachedDifyApp(difyAppId);
			if (difyApp != null) {
				String appMode = difyApp.path("mode").asText("");
				if ("advanced-chat".equals(appMode) || "workflow".equals(appMode)) {
					publishDifyWorkflow(difyAppId);
				}
			}
			log.info("导入Dify DSL成功, difyAppId={}", difyAppId);
			return new String[] { difyAppId, apiKey };
		}
		catch (Exception e) {
			log.error("导入Dify DSL失败, error={}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 从 Dify 应用详情反向读取配置，转换为本地 config JSON 格式
	 * <p>
	 * 字段映射（Dify → 本地）：
	 * <ul>
	 * <li>pre_prompt → systemPrompt</li>
	 * <li>opening_statement → openingStatement</li>
	 * <li>suggested_questions → sampleQuestions</li>
	 * <li>model.name → modelConfig.modelName</li>
	 * <li>model.provider → modelConfig.provider</li>
	 * <li>model.completion_params.* → modelConfig.*</li>
	 * </ul>
	 * @param difyAppId Dify 应用 ID
	 * @return 本地 config JSON 字符串，失败返回 null
	 */
	public String readDifyAppConfig(String difyAppId) {
		if (!StringUtils.hasText(difyAppId)) {
			return null;
		}
		try {
			JsonNode difyApp = difyAppApi.getApp(difyAppId);
			if (difyApp == null) {
				log.warn("读取Dify应用配置失败: getApp返回null, difyAppId={}", difyAppId);
				return null;
			}
			// advanced-chat / workflow 的核心配置在 workflow DSL 中，仅返回基本信息
			String appMode = difyApp.path("mode").asText("");
			if ("advanced-chat".equals(appMode) || "workflow".equals(appMode)) {
				Map<String, Object> config = new HashMap<>();
				config.put("difyMode", appMode);
				return OBJECT_MAPPER.writeValueAsString(config);
			}
			JsonNode modelConfigNode = difyApp.get("model_config");
			if (modelConfigNode == null || modelConfigNode.isNull()) {
				log.info("Dify应用无model_config, difyAppId={}", difyAppId);
				return null;
			}

			Map<String, Object> config = new HashMap<>();

			// 提示词
			if (modelConfigNode.has("pre_prompt")) {
				config.put("systemPrompt", modelConfigNode.get("pre_prompt").asText(""));
			}

			// 开场白
			if (modelConfigNode.has("opening_statement")) {
				config.put("openingStatement", modelConfigNode.get("opening_statement").asText(""));
			}

			// 示例问题
			if (modelConfigNode.has("suggested_questions") && modelConfigNode.get("suggested_questions").isArray()) {
				List<String> questions = new ArrayList<>();
				for (JsonNode q : modelConfigNode.get("suggested_questions")) {
					if (q.isTextual() && StringUtils.hasText(q.asText())) {
						questions.add(q.asText());
					}
				}
				config.put("sampleQuestions", questions);
			}

			// 模型参数
			JsonNode modelNode = modelConfigNode.get("model");
			if (modelNode != null && !modelNode.isNull()) {
				Map<String, Object> modelConfig = new HashMap<>();
				if (modelNode.has("name")) {
					modelConfig.put("modelName", modelNode.get("name").asText(""));
				}
				if (modelNode.has("provider")) {
					modelConfig.put("provider", modelNode.get("provider").asText(""));
				}
				JsonNode params = modelNode.get("completion_params");
				if (params != null && !params.isNull()) {
					if (params.has("temperature")) {
						modelConfig.put("temperature", params.get("temperature").numberValue());
					}
					if (params.has("max_tokens")) {
						modelConfig.put("maxTokens", params.get("max_tokens").intValue());
					}
				}
				config.put("modelConfig", modelConfig);
			}

			String configJson = OBJECT_MAPPER.writeValueAsString(config);
			log.info("反向读取Dify应用配置成功, difyAppId={}", difyAppId);
			return configJson;
		}
		catch (Exception e) {
			log.warn("反向读取Dify应用配置失败, difyAppId={}, error={}", difyAppId, e.getMessage());
			return null;
		}
	}

	/**
	 * 获取底层 DifyAppApi（供特殊场景直接调用）
	 */
	public DifyAppApi getApi() {
		return difyAppApi;
	}

	// ==================== 外部知识库同步 ====================

	/**
	 * 同步智能体关联的知识库到 Dify 外部知识库
	 * <p>
	 * 流程： 1. 从 config 解析 datasetIds 和 datasetNames 2. 获取租户活跃 API Token（无则跳过） 3. 确保 Dify
	 * 侧外部知识库 API 模板存在（租户级单例） 4. 为每个 datasetId 创建外部数据集（幂等，已存在则跳过） 5. 更新 Dify 应用的
	 * dataset_configs 绑定
	 * @param difyAppId Dify 应用 ID
	 * @param configJson 智能体 config JSON（含 datasetIds/datasetNames）
	 * @param tenantId 租户 ID
	 * @param createBy 当前操作用户 ID（用于自动创建 Token 时记录创建人）
	 */
	public void syncDatasets(String difyAppId, String configJson, String tenantId, String createBy) {
		if (!StringUtils.hasText(difyAppId) || !StringUtils.hasText(configJson)) {
			return;
		}
		String endpoint = difyProperties.getMatrixApiEndpoint();
		if (!StringUtils.hasText(endpoint)) {
			log.warn("未配置 matrix.dify.matrix-api-endpoint, 跳过知识库同步");
			return;
		}
		try {
			// advanced-chat / workflow 通过 workflow 节点绑定知识库，不走 agent_mode.tools
			JsonNode difyApp = getCachedDifyApp(difyAppId);
			if (difyApp != null) {
				String appMode = difyApp.path("mode").asText("");
				if ("advanced-chat".equals(appMode) || "workflow".equals(appMode)) {
					log.info("Dify应用为{}模式, 跳过agent_mode.tools知识库同步, difyAppId={}", appMode, difyAppId);
					return;
				}
			}
			JsonNode localConfig = OBJECT_MAPPER.readTree(configJson);
			String tenantCode = localConfig.path("tenantCode").asText("");
			// 1. 解析 datasetIds
			if (!localConfig.has("datasetIds") || !localConfig.get("datasetIds").isArray()
					|| localConfig.get("datasetIds").isEmpty()) {
				// 无关联知识库，清空 Dify 应用的 dataset_configs
				clearDifyDatasets(difyAppId);
				return;
			}
			List<String> datasetIds = new ArrayList<>();
			for (JsonNode id : localConfig.get("datasetIds")) {
				datasetIds.add(id.asText());
			}
			// 解析 datasetNames 映射
			Map<String, String> datasetNames = new HashMap<>();
			if (localConfig.has("datasetNames") && localConfig.get("datasetNames").isObject()) {
				Iterator<Map.Entry<String, JsonNode>> fields = localConfig.get("datasetNames").fields();
				while (fields.hasNext()) {
					Map.Entry<String, JsonNode> entry = fields.next();
					datasetNames.put(entry.getKey(), entry.getValue().asText(""));
				}
			}

			// 2. 获取或创建租户 API Token
			String apiToken = apiTokenValidator.getOrCreateTokenValue(tenantId, createBy);
			if (!StringUtils.hasText(apiToken)) {
				log.warn("获取或创建API Token失败, 跳过知识库同步到Dify, tenantId={}", tenantId);
				return;
			}

			// 3. 确保外部知识库 API 模板存在
			String externalApiId = ensureExternalKnowledgeApi(tenantId, endpoint, apiToken);
			if (!StringUtils.hasText(externalApiId)) {
				log.warn("创建外部知识库API模板失败, 跳过知识库同步, tenantId={}", tenantId);
				return;
			}

			// 4. 为每个 datasetId 创建外部数据集（幂等）
			Map<String, ExternalDatasetRef> existingMapping = getExistingExternalDatasetMapping();
			List<Map<String, Object>> difyDatasets = new ArrayList<>();
			for (String datasetId : datasetIds) {
				String name = datasetNames.getOrDefault(datasetId, "Matrix-KB-" + datasetId);
				ExternalDatasetRef existingDataset = existingMapping.get(datasetId);
				String difyDatasetId = existingDataset != null ? existingDataset.id() : null;
				if (!StringUtils.hasText(difyDatasetId)) {
					// 创建新的外部数据集
					difyDatasetId = createExternalDataset(externalApiId, datasetId, name);
				}
				else if (!name.equals(existingDataset.name())) {
					updateExternalDatasetName(difyDatasetId, datasetId, name);
				}
				if (StringUtils.hasText(difyDatasetId)) {
					bindKnowledgeTenantTag(difyDatasetId, tenantCode);
					Map<String, Object> ds = new HashMap<>();
					ds.put("dataset", Map.of("id", difyDatasetId, "enabled", true));
					difyDatasets.add(ds);
				}
			}

			// 5. 更新 Dify 应用 dataset_configs + agent_mode.tools
			if (updateDifyAppDatasets(difyAppId, difyDatasets)) {
				log.info("同步知识库到Dify成功, difyAppId={}, datasetIds={}", difyAppId, datasetIds);
			}
			else {
				log.warn("同步知识库到Dify失败(updateDifyAppDatasets), difyAppId={}, datasetIds={}", difyAppId, datasetIds);
			}
		}
		catch (Exception e) {
			log.error("同步知识库到Dify失败, difyAppId={}, error={}", difyAppId, e.getMessage(), e);
		}
	}

	/**
	 * 确保租户级外部知识库 API 模板存在（单例），返回模板 ID
	 */
	private String ensureExternalKnowledgeApi(String tenantId, String endpoint, String apiToken) {
		try {
			String templateName = "Matrix-KB-" + tenantId;
			// 查找已有模板
			JsonNode list = difyAppApi.listExternalKnowledgeApis(1, 100);
			if (list != null && list.has("data") && list.get("data").isArray()) {
				for (JsonNode item : list.get("data")) {
					if (templateName.equals(item.path("name").asText(""))) {
						String existingId = item.get("id").asText();
						// Token 或 endpoint 变更时更新 Dify 侧模板，保证跨机器迁移后回调地址同步
						JsonNode settings = item.get("settings");
						if (settings != null) {
							String existingKey = settings.path("api_key").asText("");
							String existingEndpoint = settings.path("endpoint").asText("");
							boolean tokenChanged = !apiToken.equals(existingKey);
							boolean endpointChanged = !endpoint.equals(existingEndpoint);
							if (tokenChanged || endpointChanged) {
								difyAppApi.updateExternalKnowledgeApi(existingId, templateName, endpoint, apiToken);
								log.info("更新Dify外部知识库API模板, name={}, id={}, tokenChanged={}, endpointChanged={}",
										templateName, existingId, tokenChanged, endpointChanged);
							}
						}
						return existingId;
					}
				}
			}
			// 不存在则创建
			JsonNode result = difyAppApi.createExternalKnowledgeApi(templateName, endpoint, apiToken);
			if (result != null && result.has("id")) {
				log.info("创建Dify外部知识库API模板成功, name={}, id={}", templateName, result.get("id").asText());
				return result.get("id").asText();
			}
		}
		catch (Exception e) {
			log.warn("确保外部知识库API模板失败, tenantId={}, error={}", tenantId, e.getMessage());
		}
		return null;
	}

	/**
	 * 获取 Dify 已有的外部数据集映射（external_knowledge_id -> dify_dataset_id）
	 */
	private Map<String, ExternalDatasetRef> getExistingExternalDatasetMapping() {
		Map<String, ExternalDatasetRef> mapping = new HashMap<>();
		try {
			JsonNode list = difyAppApi.listDatasets(1, 100);
			if (list != null && list.has("data") && list.get("data").isArray()) {
				for (JsonNode ds : list.get("data")) {
					if ("external".equals(ds.path("provider").asText(""))) {
						JsonNode extInfo = ds.get("external_knowledge_info");
						if (extInfo != null && extInfo.has("external_knowledge_id")) {
							mapping.put(extInfo.get("external_knowledge_id").asText(),
									new ExternalDatasetRef(ds.get("id").asText(), ds.path("name").asText("")));
						}
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("查询Dify外部数据集列表失败: {}", e.getMessage());
		}
		return mapping;
	}

	/**
	 * 已存在的 Dify 外部知识库名称不一致时同步真实名称。失败只记录告警，不影响既有绑定和检索。
	 */
	private void updateExternalDatasetName(String difyDatasetId, String datasetId, String name) {
		try {
			difyAppApi.updateDatasetName(difyDatasetId, name);
			log.info("更新Dify外部知识库名称成功, datasetId={}, difyId={}, name={}", datasetId, difyDatasetId, name);
		}
		catch (Exception e) {
			log.warn("更新Dify外部知识库名称失败, datasetId={}, difyId={}, error={}", datasetId, difyDatasetId,
					e.getMessage());
		}
	}

	/**
	 * 创建 Dify 外部数据集，返回 dify dataset ID
	 */
	private String createExternalDataset(String externalApiId, String datasetId, String name) {
		try {
			JsonNode result = difyAppApi.createExternalDataset(externalApiId, datasetId, name, 4, 0.5);
			if (result != null && result.has("id")) {
				log.info("创建Dify外部数据集成功, datasetId={}, difyId={}", datasetId, result.get("id").asText());
				return result.get("id").asText();
			}
		}
		catch (Exception e) {
			log.warn("创建Dify外部数据集失败, datasetId={}, error={}", datasetId, e.getMessage());
		}
		return null;
	}

	private record ExternalDatasetRef(String id, String name) {
	}

	/**
	 * 更新 Dify 应用的 dataset_configs 绑定
	 * <p>
	 * Dify 1.13 agent-chat 模式下，只需设置 dataset_configs 即可触发知识库检索， 不需要在 agent_mode.tools 中注册
	 * dataset-retrieval 工具（该类型工具会导致前端崩溃）。
	 * @return true 更新成功，false 更新失败
	 */
	@SuppressWarnings("unchecked")
	private boolean updateDifyAppDatasets(String difyAppId, List<Map<String, Object>> difyDatasets) {
		try {
			JsonNode difyApp = difyAppApi.getApp(difyAppId);
			if (difyApp == null) {
				return false;
			}
			Map<String, Object> modelConfig;
			JsonNode existingConfig = difyApp.get("model_config");
			if (existingConfig != null && !existingConfig.isNull() && existingConfig.size() > 0) {
				modelConfig = OBJECT_MAPPER.convertValue(existingConfig, Map.class);
			}
			else {
				modelConfig = buildDefaultModelConfig();
			}
			// 更新 dataset_configs（声明检索参数）
			// reranking_enabled 设为 false：外部知识库检索由 Matrix 侧完成，Dify 无法对外部结果做 rerank
			Map<String, Object> datasetConfigs = new HashMap<>();
			datasetConfigs.put("retrieval_model", "multiple");
			datasetConfigs.put("datasets", Map.of("strategy", "router", "datasets", difyDatasets));
			datasetConfigs.put("top_k", 4);
			datasetConfigs.put("score_threshold_enabled", true);
			datasetConfigs.put("score_threshold", 0.5);
			datasetConfigs.put("reranking_enabled", false);
			datasetConfigs.put("reranking_mode", "reranking_model");
			datasetConfigs.put("metadata_filtering_mode", "disabled");
			modelConfig.put("dataset_configs", datasetConfigs);

			// 清理 agent_mode.tools 中残留的 dataset-retrieval 条目（历史数据兼容）
			cleanupDatasetRetrievalTools(modelConfig);

			difyAppApi.updateModelConfig(difyAppId, modelConfig);
			evictCache(difyAppId);
			return true;
		}
		catch (Exception e) {
			log.error("更新Dify应用dataset_configs失败, difyAppId={}, error={}", difyAppId, e.getMessage(), e);
			return false;
		}
	}

	/**
	 * 清空 Dify 应用的 dataset_configs
	 */
	@SuppressWarnings("unchecked")
	private void clearDifyDatasets(String difyAppId) {
		try {
			JsonNode difyApp = difyAppApi.getApp(difyAppId);
			if (difyApp == null) {
				return;
			}
			JsonNode existingConfig = difyApp.get("model_config");
			if (existingConfig == null || existingConfig.isNull()) {
				return;
			}
			Map<String, Object> modelConfig = OBJECT_MAPPER.convertValue(existingConfig, Map.class);
			modelConfig.put("dataset_configs", Map.of("retrieval_model", "single"));

			// 清理 agent_mode.tools 中残留的 dataset-retrieval 条目（历史数据兼容）
			cleanupDatasetRetrievalTools(modelConfig);

			difyAppApi.updateModelConfig(difyAppId, modelConfig);
			evictCache(difyAppId);
			log.info("清空Dify应用知识库关联, difyAppId={}", difyAppId);
		}
		catch (Exception e) {
			log.warn("清空Dify应用dataset_configs失败, difyAppId={}, error={}", difyAppId, e.getMessage());
		}
	}

	/**
	 * 清理 agent_mode.tools 中残留的 dataset-retrieval 条目
	 * <p>
	 * Dify 1.13 不支持 dataset-retrieval 类型的工具条目，残留会导致前端崩溃。 此方法用于兼容清理历史数据。
	 */
	@SuppressWarnings("unchecked")
	private void cleanupDatasetRetrievalTools(Map<String, Object> modelConfig) {
		Object agentModeRaw = modelConfig.get("agent_mode");
		if (agentModeRaw instanceof Map) {
			Map<String, Object> agentMode = (Map<String, Object>) agentModeRaw;
			Object toolsRaw = agentMode.get("tools");
			if (toolsRaw instanceof List) {
				List<Map<String, Object>> tools = new ArrayList<>((List<Map<String, Object>>) toolsRaw);
				if (tools.removeIf(t -> "dataset-retrieval".equals(t.get("provider_type")))) {
					agentMode.put("tools", tools);
				}
			}
		}
	}

	/**
	 * 构建 Dify model_config 默认结构（Dify 1.13+ 要求完整字段）
	 */
	private Map<String, Object> buildDefaultModelConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("pre_prompt", "");
		config.put("opening_statement", "");
		config.put("suggested_questions", new ArrayList<>());
		config.put("user_input_form", new ArrayList<>());
		config.put("dataset_query_variable", "");
		config.put("more_like_this", Map.of("enabled", false));
		config.put("suggested_questions_after_answer", Map.of("enabled", true));
		config.put("speech_to_text", Map.of("enabled", false));
		config.put("text_to_speech", Map.of("enabled", false));
		config.put("retriever_resource", Map.of("enabled", true));
		config.put("sensitive_word_avoidance", Map.of("enabled", false));
		Map<String, Object> fileUpload = new HashMap<>();
		fileUpload.put("enabled", true);
		fileUpload.put("allowed_file_upload_methods", List.of("local_file", "remote_url"));
		fileUpload.put("number_limits", 5);
		fileUpload.put("allowed_file_types", List.of("image", "document"));
		fileUpload.put("image", Map.of("detail", "high"));
		config.put("file_upload", fileUpload);
		config.put("dataset_configs", Map.of("retrieval_model", "single"));
		Map<String, Object> agentMode = new HashMap<>();
		agentMode.put("enabled", true);
		agentMode.put("strategy", "function_call");
		agentMode.put("tools", new ArrayList<>());
		config.put("agent_mode", agentMode);
		config.put("model", new HashMap<>());
		return config;
	}

	/**
	 * 确保 model_config 中 Features 开关为期望的默认值。
	 * <p>
	 * Dify 新建应用默认全部关闭，sync 时从 Dify 读取的旧配置可能缺少或关闭了这些字段，
	 * 此方法补充平台侧需要默认开启的 Features。
	 */
	@SuppressWarnings("unchecked")
	private void ensureFeatureDefaults(Map<String, Object> modelConfig) {
		// file_upload: 确保开启并配置允许的文件类型
		Object fileUploadRaw = modelConfig.get("file_upload");
		if (!(fileUploadRaw instanceof Map) || !Boolean.TRUE.equals(((Map<String, Object>) fileUploadRaw).get("enabled"))) {
			Map<String, Object> fileUpload = new HashMap<>();
			fileUpload.put("enabled", true);
			fileUpload.put("allowed_file_upload_methods", List.of("local_file", "remote_url"));
			fileUpload.put("number_limits", 5);
			fileUpload.put("allowed_file_types", List.of("image", "document"));
			fileUpload.put("image", Map.of("detail", "high"));
			modelConfig.put("file_upload", fileUpload);
		}

		// retriever_resource: 开启引用来源展示
		Object retrieverRaw = modelConfig.get("retriever_resource");
		if (!(retrieverRaw instanceof Map) || !Boolean.TRUE.equals(((Map<String, Object>) retrieverRaw).get("enabled"))) {
			modelConfig.put("retriever_resource", Map.of("enabled", true));
		}

		// suggested_questions_after_answer: 开启回答后推荐问题
		Object suggestedRaw = modelConfig.get("suggested_questions_after_answer");
		if (!(suggestedRaw instanceof Map)
				|| !Boolean.TRUE.equals(((Map<String, Object>) suggestedRaw).get("enabled"))) {
			modelConfig.put("suggested_questions_after_answer", Map.of("enabled", true));
		}
	}

	/**
	 * 同步 Chat2BI 工具绑定到 Dify 应用
	 * <p>
	 * 仅对 dify_mode='agent-chat' 的智能体生效。 有数据集时绑定工具 + 注入 prompt，无数据集时解绑工具 + 清除
	 * prompt。
	 * @param difyAppId Dify 应用 ID
	 * @param difyMode Dify 应用模式
	 * @param dataId 数据集ID，null 时执行解绑
	 * @param dataName 数据集名称
	 */
	@SuppressWarnings("unchecked")
	public void syncChat2biTool(String difyAppId, String difyMode, Long dataId, String dataName) {
		if (!"agent-chat".equals(difyMode)) {
			log.info("Dify应用为{}模式, 跳过Chat2BI工具同步, difyAppId={}", difyMode, difyAppId);
			return;
		}
		if (!StringUtils.hasText(difyAppId)) {
			return;
		}

		if (dataId == null) {
			removeChatBiToolAndPrompt(difyAppId);
			return;
		}

		try {

			// 查找 Matrix Chat2DB 工具的 provider 信息并构建工具条目
			String toolProviderName = difyProperties.getChat2dbToolName();
			JsonNode apiTools = difyAppApi.listApiTools();
			String providerId = null;
			List<Map<String, Object>> toolEntries = new ArrayList<>();

			if (apiTools != null && apiTools.isArray()) {
				for (JsonNode provider : apiTools) {
					if (toolProviderName.equals(provider.path("name").asText(""))) {
						providerId = provider.path("id").asText(null);
						JsonNode tools = provider.get("tools");
						if (providerId != null && tools != null && tools.isArray()) {
							for (JsonNode tool : tools) {
								Map<String, Object> entry = new HashMap<>();
								entry.put("provider_type", "api");
								entry.put("provider_id", providerId);
								entry.put("tool_name", tool.path("name").asText(""));
								entry.put("tool_label", tool.path("label").path("en_US").asText(
										tool.path("name").asText("")));
								entry.put("tool_parameters", Map.of());
								entry.put("enabled", true);
								toolEntries.add(entry);
							}
						}
						break;
					}
				}
			}

			if (providerId == null) {
				log.warn("Dify工作区未找到名为'{}'的API工具, 跳过Chat2BI工具绑定, difyAppId={}", toolProviderName,
						difyAppId);
				return;
			}

			// 获取 Dify 应用当前配置
			JsonNode difyApp = difyAppApi.getApp(difyAppId);
			if (difyApp == null) {
				log.warn("获取Dify应用失败, 无法同步Chat2BI工具, difyAppId={}", difyAppId);
				return;
			}

			JsonNode existingConfig = difyApp.get("model_config");
			Map<String, Object> modelConfig;
			if (existingConfig != null && !existingConfig.isNull() && existingConfig.size() > 0) {
				modelConfig = OBJECT_MAPPER.convertValue(existingConfig, Map.class);
			}
			else {
				modelConfig = buildDefaultModelConfig();
			}

			// 更新 agent_mode.tools：先移除旧的 Chat2DB 工具，再添加新的
			Object agentModeRaw = modelConfig.get("agent_mode");
			Map<String, Object> agentMode;
			if (agentModeRaw instanceof Map) {
				agentMode = (Map<String, Object>) agentModeRaw;
			}
			else {
				agentMode = new HashMap<>();
				agentMode.put("enabled", true);
				agentMode.put("strategy", "function_call");
				agentMode.put("tools", new ArrayList<>());
				modelConfig.put("agent_mode", agentMode);
			}

			Object toolsRaw = agentMode.get("tools");
			List<Map<String, Object>> tools;
			if (toolsRaw instanceof List) {
				tools = new ArrayList<>((List<Map<String, Object>>) toolsRaw);
			}
			else {
				tools = new ArrayList<>();
			}

			String finalProviderId = providerId;
			tools.removeIf(t -> "api".equals(t.get("provider_type")) && finalProviderId.equals(t.get("provider_id")));
			tools.addAll(toolEntries);
			agentMode.put("tools", tools);

			// 注入 pre_prompt 中的数据集配置片段
			String prePrompt = modelConfig.getOrDefault("pre_prompt", "").toString();
			prePrompt = removeChatBiPromptSection(prePrompt);
			String promptSection = buildChatBiPromptSection(dataId, dataName);
			prePrompt = prePrompt.stripTrailing() + "\n\n" + promptSection;
			modelConfig.put("pre_prompt", prePrompt);

			difyAppApi.updateModelConfig(difyAppId, modelConfig);
			evictCache(difyAppId);
			log.info("同步Chat2BI工具到Dify成功, difyAppId={}, dataId={}", difyAppId, dataId);
		}
		catch (Exception e) {
			log.error("同步Chat2BI工具到Dify失败, difyAppId={}, error={}", difyAppId, e.getMessage(), e);
		}
	}

	private void removeChatBiToolAndPrompt(String difyAppId) {
		try {
			JsonNode difyApp = difyAppApi.getApp(difyAppId);
			if (difyApp == null) {
				return;
			}
			JsonNode existingConfig = difyApp.get("model_config");
			if (existingConfig == null || existingConfig.isNull()) {
				return;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> modelConfig = OBJECT_MAPPER.convertValue(existingConfig, Map.class);
			boolean changed = false;

			// 移除工具（通过 listApiTools 精确查找 providerId，与绑定逻辑一致）
			String providerId = findChat2biProviderId();
			Object agentModeRaw = modelConfig.get("agent_mode");
			if (providerId != null && agentModeRaw instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> agentMode = (Map<String, Object>) agentModeRaw;
				Object toolsRaw = agentMode.get("tools");
				if (toolsRaw instanceof List) {
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsRaw;
					int before = tools.size();
					tools.removeIf(t -> "api".equals(t.get("provider_type"))
							&& providerId.equals(t.get("provider_id")));
					if (tools.size() != before) {
						changed = true;
					}
				}
			}

			// 清除 prompt 片段
			String prePrompt = modelConfig.getOrDefault("pre_prompt", "").toString();
			String cleaned = removeChatBiPromptSection(prePrompt);
			if (!cleaned.equals(prePrompt)) {
				modelConfig.put("pre_prompt", cleaned);
				changed = true;
			}

			if (changed) {
				difyAppApi.updateModelConfig(difyAppId, modelConfig);
				evictCache(difyAppId);
				log.info("清除Dify应用Chat2BI工具和Prompt, difyAppId={}", difyAppId);
			}
		}
		catch (Exception e) {
			log.warn("清除Chat2BI工具失败, difyAppId={}, error={}", difyAppId, e.getMessage());
		}
	}

	/**
	 * 查找 Dify 工作区中 Matrix Chat2DB 工具的 provider_id
	 * @return provider_id，未找到返回 null
	 */
	private String findChat2biProviderId() {
		try {
			String toolProviderName = difyProperties.getChat2dbToolName();
			JsonNode apiTools = difyAppApi.listApiTools();
			if (apiTools != null && apiTools.isArray()) {
				for (JsonNode provider : apiTools) {
					if (toolProviderName.equals(provider.path("name").asText(""))) {
						return provider.path("id").asText(null);
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("查询Dify API工具列表失败: {}", e.getMessage());
		}
		return null;
	}

	private static final String CHAT2BI_PROMPT_MARKER = "[数据查询配置]";

	private String removeChatBiPromptSection(String prePrompt) {
		int idx = prePrompt.indexOf(CHAT2BI_PROMPT_MARKER);
		if (idx < 0) {
			return prePrompt;
		}
		return prePrompt.substring(0, idx).stripTrailing();
	}

	private String buildChatBiPromptSection(Long dataId, String dataName) {
		String dataIdStr = String.valueOf(dataId);
		return CHAT2BI_PROMPT_MARKER + "\n"
				+ "你可以使用 get_dataset_schema 和 execute_sql 工具查询数据库回答用户的数据问题。\n"
				+ "数据集ID: \"" + dataIdStr + "\"，数据集名称: " + dataName + "。\n\n"
				+ "工作流程：\n"
				+ "1. 调用 get_dataset_schema（dataset_id: \"" + dataIdStr + "\"）获取表结构\n"
				+ "2. 根据表结构和用户问题生成 SQL（充分利用 virtual_comment 理解字段含义）\n"
				+ "3. 调用 execute_sql（dataset_id: \"" + dataIdStr + "\", sql: 生成的SQL）执行查询\n"
				+ "4. 分析查询结果，用自然语言回答用户问题\n"
				+ "注意：只生成 SELECT 查询；dataset_id 必须作为字符串传递（带引号）；查询失败时检查错误信息修正 SQL 后重试。";
	}

	private record DifyCacheEntry(JsonNode data, long expireAt) {

		boolean isExpired() {
			return System.currentTimeMillis() > expireAt;
		}

	}

	private static final String ARTIFACT_VARIABLE = "extended_system_prompt";

	/**
	 * 同步 Artifact 技能变量到 Dify 应用的 user_input_form
	 * @param difyAppId Dify 应用 ID
	 * @param bind true=绑定（添加变量），false=解绑（移除变量）
	 */
	@SuppressWarnings("unchecked")
	public void syncArtifactVariable(String difyAppId, boolean bind) {
		if (!StringUtils.hasText(difyAppId)) {
			return;
		}
		try {
			JsonNode difyApp = difyAppApi.getApp(difyAppId);
			if (difyApp == null) {
				log.warn("[DifyAgentBridge] 获取Dify应用失败, 无法同步Artifact变量, difyAppId={}", difyAppId);
				return;
			}
			String appMode = difyApp.path("mode").asText("");
			if ("advanced-chat".equals(appMode) || "workflow".equals(appMode)) {
				log.info("[DifyAgentBridge] Dify应用为{}模式, 跳过Artifact变量同步, difyAppId={}", appMode, difyAppId);
				return;
			}
			JsonNode existingConfig = difyApp.get("model_config");
			Map<String, Object> modelConfig;
			if (existingConfig != null && !existingConfig.isNull() && existingConfig.size() > 0) {
				modelConfig = OBJECT_MAPPER.convertValue(existingConfig, Map.class);
			}
			else {
				modelConfig = buildDefaultModelConfig();
			}
			List<Map<String, Object>> inputForm = (List<Map<String, Object>>) modelConfig.getOrDefault(
					"user_input_form", new ArrayList<>());
			boolean hasVariable = inputForm.stream()
				.anyMatch(item -> item.containsKey("paragraph")
						&& ARTIFACT_VARIABLE.equals(((Map<String, Object>) item.get("paragraph")).get("variable")));
			if (bind && !hasVariable) {
				Map<String, Object> varDef = new HashMap<>();
				varDef.put("variable", ARTIFACT_VARIABLE);
				varDef.put("label", ARTIFACT_VARIABLE);
				varDef.put("max_length", 10000);
				varDef.put("default", "");
				varDef.put("required", false);
				varDef.put("hide", true);
				inputForm.add(Map.of("paragraph", varDef));
				modelConfig.put("user_input_form", inputForm);
				String prePrompt = modelConfig.getOrDefault("pre_prompt", "").toString();
				if (!prePrompt.contains("{{" + ARTIFACT_VARIABLE + "}}")) {
					modelConfig.put("pre_prompt", prePrompt + "\n\n{{" + ARTIFACT_VARIABLE + "}}");
				}
				difyAppApi.updateApp(difyAppId, Map.of("model_config", modelConfig));
				log.info("[DifyAgentBridge] Artifact变量已添加到Dify应用, difyAppId={}", difyAppId);
			}
			else if (!bind && hasVariable) {
				inputForm.removeIf(item -> item.containsKey("paragraph")
						&& ARTIFACT_VARIABLE.equals(((Map<String, Object>) item.get("paragraph")).get("variable")));
				modelConfig.put("user_input_form", inputForm);
				String prePrompt = modelConfig.getOrDefault("pre_prompt", "").toString();
				String cleaned = prePrompt.replace("\n\n{{" + ARTIFACT_VARIABLE + "}}", "")
					.replace("{{" + ARTIFACT_VARIABLE + "}}", "");
				if (!cleaned.equals(prePrompt)) {
					modelConfig.put("pre_prompt", cleaned);
				}
				difyAppApi.updateApp(difyAppId, Map.of("model_config", modelConfig));
				log.info("[DifyAgentBridge] Artifact变量已从Dify应用移除, difyAppId={}", difyAppId);
			}
		}
		catch (Exception e) {
			log.error("[DifyAgentBridge] 同步Artifact变量失败, difyAppId={}", difyAppId, e);
		}
	}

	// ==================== 租户标签绑定 ====================

	/**
	 * 查询可关联的 Dify 应用列表
	 * <p>
	 * 返回 tag 为空（未打租户标签）或 tag 包含当前租户标签（matrix-{tenantCode}）的应用，
	 * 过滤掉已打其他租户标签的应用。
	 * @param tenantCode 当前租户编码
	 * @return 应用列表，每项包含 id/name/mode
	 */
	public List<Map<String, Object>> listAvailableDifyApps(String tenantCode, String keyword) {
		List<Map<String, Object>> result = new ArrayList<>();
		if (!StringUtils.hasText(tenantCode)) {
			return result;
		}
		String tenantTagName = TENANT_TAG_PREFIX + tenantCode;
		java.util.Set<String> seen = new java.util.HashSet<>();
		try {
			// 1. 按租户 tag + keyword 拉一页；过滤同时带其他 matrix- 前缀标签的脏数据
			String tenantTagId = lookupTagIdByName(tenantTagName, "app");
			if (StringUtils.hasText(tenantTagId)) {
				collectOnePage(result, seen, tenantTagId, keyword, app -> !hasOtherTenantTag(app, tenantTagName));
			}
			// 2. 无 tag + keyword 再拉一页，过滤出"无 matrix- 前缀标签"的应用
			collectOnePage(result, seen, null, keyword, this::isAppUnassigned);
		}
		catch (Exception e) {
			log.warn("查询可关联Dify应用列表失败, tenantCode={}, keyword={}, error={}", tenantCode, keyword, e.getMessage());
		}
		return result;
	}

	/**
	 * 查询指定名称和类型的标签ID，未命中返回 null
	 */
	private String lookupTagIdByName(String tagName, String type) {
		JsonNode tags = difyAppApi.listTags(type);
		if (tags != null && tags.isArray()) {
			for (JsonNode tag : tags) {
				if (tagName.equals(tag.path("name").asText(""))) {
					return tag.get("id").asText();
				}
			}
		}
		return null;
	}

	/**
	 * 拉取单页应用并按条件加入结果集（去重按 app.id）
	 */
	private void collectOnePage(List<Map<String, Object>> result, java.util.Set<String> seen, String tagIds,
			String keyword, java.util.function.Predicate<JsonNode> filter) {
		JsonNode apps = difyAppApi.listApps(1, 100, "all", keyword, tagIds);
		if (apps == null || !apps.has("data") || !apps.get("data").isArray()) {
			return;
		}
		for (JsonNode app : apps.get("data")) {
			String id = app.path("id").asText("");
			if (!StringUtils.hasText(id) || seen.contains(id)) {
				continue;
			}
			if (!filter.test(app)) {
				continue;
			}
			seen.add(id);
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", id);
			item.put("name", app.path("name").asText(""));
			item.put("mode", app.path("mode").asText(""));
			result.add(item);
		}
	}

	/**
	 * 判断 Dify 应用是否未分配给任何租户（tags 为空 或 不含 matrix- 前缀标签）
	 */
	private boolean isAppUnassigned(JsonNode app) {
		JsonNode tags = app.get("tags");
		if (tags == null || !tags.isArray() || tags.isEmpty()) {
			return true;
		}
		for (JsonNode tag : tags) {
			if (tag.path("name").asText("").startsWith(TENANT_TAG_PREFIX)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 判断 Dify 应用是否带有当前租户以外的其他 matrix- 前缀标签（脏数据防御）
	 */
	private boolean hasOtherTenantTag(JsonNode app, String tenantTagName) {
		JsonNode tags = app.get("tags");
		if (tags == null || !tags.isArray()) {
			return false;
		}
		for (JsonNode tag : tags) {
			String tagName = tag.path("name").asText("");
			if (tagName.startsWith(TENANT_TAG_PREFIX) && !tenantTagName.equals(tagName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 为 Dify 应用绑定租户标签（格式: matrix-{tenantCode}）
	 * @param difyAppId Dify 应用 ID
	 * @param tenantCode 租户编码（sys_tenant.code）
	 */
	public void bindTenantTag(String difyAppId, String tenantCode) {
		if (!StringUtils.hasText(difyAppId) || !StringUtils.hasText(tenantCode)) {
			return;
		}
		String tagName = TENANT_TAG_PREFIX + tenantCode;
		try {
			String tagId = getOrCreateTenantTagId(tagName, "app");
			if (StringUtils.hasText(tagId)) {
				difyAppApi.bindTag(List.of(tagId), difyAppId, "app");
				log.info("绑定租户标签到Dify应用成功, difyAppId={}, tag={}", difyAppId, tagName);
			}
		}
		catch (Exception e) {
			log.warn("绑定租户标签到Dify应用失败, difyAppId={}, tag={}, error={}", difyAppId, tagName, e.getMessage());
		}
	}

	/**
	 * 为 Dify 知识库绑定租户标签（格式: matrix-{tenantCode}）
	 * @param difyDatasetId Dify 知识库 ID
	 * @param tenantCode 租户编码（sys_tenant.code）
	 */
	public void bindKnowledgeTenantTag(String difyDatasetId, String tenantCode) {
		if (!StringUtils.hasText(difyDatasetId) || !StringUtils.hasText(tenantCode)) {
			return;
		}
		String tagName = TENANT_TAG_PREFIX + tenantCode;
		try {
			String tagId = getOrCreateTenantTagId(tagName, "knowledge");
			if (StringUtils.hasText(tagId)) {
				difyAppApi.bindTag(List.of(tagId), difyDatasetId, "knowledge");
				log.info("绑定租户标签到Dify知识库成功, difyDatasetId={}, tag={}", difyDatasetId, tagName);
			}
		}
		catch (Exception e) {
			log.warn("绑定租户标签到Dify知识库失败, difyDatasetId={}, tag={}, error={}", difyDatasetId, tagName,
					e.getMessage());
		}
	}

	/**
	 * 获取或创建租户标签ID
	 */
	private String getOrCreateTenantTagId(String tagName, String type) {
		// 查询已有标签
		String existingId = lookupTagIdByName(tagName, type);
		if (StringUtils.hasText(existingId)) {
			return existingId;
		}
		// 不存在则创建
		JsonNode result = difyAppApi.createTag(tagName, type);
		if (result != null && result.has("id")) {
			String tagId = result.get("id").asText();
			log.info("创建Dify租户标签成功, tag={}, tagId={}", tagName, tagId);
			return tagId;
		}
		return null;
	}

}
