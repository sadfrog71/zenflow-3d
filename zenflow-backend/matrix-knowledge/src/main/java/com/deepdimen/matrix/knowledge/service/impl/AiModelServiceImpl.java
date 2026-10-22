package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiModelService;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.AliyunModelCatalog;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionSupport;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * AI模型配置服务实现类
 * <p>
 * 管理AI模型的配置信息 支持模型的增删改查和同步操作 维护每种类型的默认模型设置
 *
 * @author matrix
 * @date 2024-09-27 23:37:54
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl extends ServiceImpl<AiModelMapper, AiModelEntity> implements AiModelService {

	private static final String VOICE_DEFAULT_UNIQUE_CONSTRAINT = "uk_ai_model_default_voice_capability";
	private static final String ASSISTANT_MODEL_UNIQUE_CONSTRAINT = "uk_ai_model_assistant_tenant";
	private static final String VOICE_DEFAULT_CONFLICT_MESSAGE =
			"当前租户的该语音能力默认模型已被并发修改，请刷新后重试";
	private static final String ASSISTANT_MODEL_CONFLICT_MESSAGE =
			"当前租户的统一助手模型已被并发修改，请刷新后重试";
	private static final Pattern VOICE_DEFAULT_DUPLICATE_KEY = Pattern.compile(
			"(?i)\\bfor\\s+key\\s+['`\"](?:[^'`\"]+\\.)*"
					+ Pattern.quote(VOICE_DEFAULT_UNIQUE_CONSTRAINT) + "['`\"]\\s*$");
	private static final Pattern ASSISTANT_MODEL_DUPLICATE_KEY = Pattern.compile(
			"(?i)\\bfor\\s+key\\s+['`\"](?:[^'`\"]+\\.)*"
					+ Pattern.quote(ASSISTANT_MODEL_UNIQUE_CONSTRAINT) + "['`\"]\\s*$");

	/**
	 * 模型提供者 管理模型实例的生命周期
	 */
	private final ModelProvider modelProvider;

	private final ModelEndpointPolicy endpointPolicy;

	/**
	 * 保存新模型
	 * <p>
	 * 自动处理默认模型设置 确保每种类型只有一个默认模型
	 * @param aiModel AI模型实体
	 * @return 操作结果
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R saveModel(AiModelEntity aiModel) {
		validateModelForWrite(aiModel);
		// 统一助手绑定只能通过专用接口修改，避免普通新增/编辑请求夹带切换绑定。
		aiModel.setAssistantModel(YesNoEnum.NO.getCode());
		setDefaultModel(aiModel);
		try {
			baseMapper.insert(aiModel);
		}
		catch (DuplicateKeyException exception) {
			throw translateModelConflict(exception);
		}
		return R.ok();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public R setAssistantModel(Long modelId, boolean enabled) {
		if (modelId == null) {
			throw new IllegalArgumentException("模型ID不能为空");
		}
		AiModelEntity existing = baseMapper.selectById(modelId);
		if (existing == null) {
			throw new IllegalArgumentException("模型不存在或无权访问");
		}
		if (!ModelTypeEnums.CHAT.getType().equalsIgnoreCase(existing.getModelType())) {
			throw new IllegalArgumentException("统一助手只能使用聊天模型");
		}
		AiModelEntity update = new AiModelEntity();
		update.setId(modelId);
		update.setAssistantModel(enabled ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode());
		try {
			if (enabled) {
				unsetAssistantModels(modelId);
			}
			if (baseMapper.updateById(update) != 1) {
				throw new IllegalStateException("统一助手模型更新失败");
			}
		}
		catch (DuplicateKeyException exception) {
			throw translateModelConflict(exception);
		}
		return R.ok();
	}

	/**
	 * 同步模型缓存
	 * <p>
	 * 清空模型缓存以重新加载 用于模型配置更新后的刷新
	 * @return 操作成功响应
	 */
	@Override
	public R syncModel() {
		modelProvider.clearModels();
		return R.ok();
	}

	/**
	 * 更新模型配置
	 * <p>
	 * 更新后清理对应的模型缓存 自动处理默认模型切换
	 * @param aiModel AI模型实体
	 * @return 操作成功响应
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R updateModel(AiModelEntity aiModel) {
		if (aiModel == null || aiModel.getId() == null) {
			return R.failed("模型ID不能为空");
		}
		AiModelEntity existing = baseMapper.selectById(aiModel.getId());
		if (existing == null) {
			return R.failed("模型不存在或无权访问");
		}
		preserveExistingSecrets(aiModel, existing);
		validateModelForWrite(aiModel);
		String effectiveModelType = StrUtil.blankToDefault(aiModel.getModelType(), existing.getModelType());
		if (YesNoEnum.YES.getCode().equals(existing.getAssistantModel())
				&& !ModelTypeEnums.CHAT.getType().equalsIgnoreCase(effectiveModelType)) {
			throw new IllegalArgumentException("请先取消统一助手模型后再修改模型类型");
		}
		// 普通编辑保留现有绑定，切换动作统一由 setAssistantModel 处理。
		aiModel.setAssistantModel(existing.getAssistantModel());
		setDefaultModel(aiModel);
		try {
			if (baseMapper.updateById(aiModel) != 1) {
				throw new IllegalStateException("模型更新失败");
			}
		}
		catch (DuplicateKeyException exception) {
			throw translateModelConflict(exception);
		}
		modelProvider.delete(existing.getName());
		if (!StrUtil.equals(existing.getName(), aiModel.getName())) {
			modelProvider.delete(aiModel.getName());
		}
		return R.ok();
	}

	/**
	 * The admin API deliberately returns masked secrets. When an edit submits an
	 * unchanged masked/blank value, keep the tenant-scoped value already stored instead
	 * of replacing a working credential with the mask or an empty string.
	 */
	private void preserveExistingSecrets(AiModelEntity aiModel, AiModelEntity existing) {
		boolean reuseApiKey = StrUtil.isBlank(aiModel.getApiKey()) || aiModel.getApiKey().contains("*");
		boolean reuseSecretKey = StrUtil.isBlank(aiModel.getSecretKey()) || aiModel.getSecretKey().contains("*");
		if ((reuseApiKey && StrUtil.isNotBlank(existing.getApiKey()))
				|| (reuseSecretKey && StrUtil.isNotBlank(existing.getSecretKey()))) {
			ensureStoredSecretEndpointUnchanged(aiModel, existing);
		}
		if (reuseApiKey) {
			aiModel.setApiKey(existing.getApiKey());
		}
		if (reuseSecretKey) {
			aiModel.setSecretKey(existing.getSecretKey());
		}
	}

	/**
	 * 批量删除模型
	 * <p>
	 * 删除前清理每个模型的缓存 确保资源正确释放
	 * @param idList 模型ID列表
	 * @return 操作成功响应
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R removeModel(ArrayList<Long> idList) {
		if (idList == null || idList.isEmpty()) {
			return R.failed("请选择要删除的模型");
		}
		Set<Long> uniqueIds = new LinkedHashSet<>(idList);
		List<AiModelEntity> existingModels = uniqueIds.stream().map(baseMapper::selectById).toList();
		if (existingModels.stream().anyMatch(java.util.Objects::isNull)) {
			return R.failed("部分模型不存在或无权访问");
		}
		existingModels.forEach(model -> modelProvider.delete(model.getName()));
		baseMapper.deleteBatchIds(uniqueIds);
		return R.ok();
	}

	/**
	 * 设置默认模型
	 * <p>
	 * 当新模型设为默认时 自动取消同类型的其他默认模型 确保每种类型只有一个默认模型
	 * @param aiModel 待设置的AI模型
	 */
	private void setDefaultModel(AiModelEntity aiModel) {
		if (YesNoEnum.YES.getCode().equals(aiModel.getDefaultModel())) {
			if (ModelTypeEnums.VOICE.getType().equals(aiModel.getModelType())) {
				unsetDefaultVoiceModelsWithSameCapability(aiModel);
				return;
			}
			AiModelEntity aiModelEntity = new AiModelEntity();
			aiModelEntity.setDefaultModel(YesNoEnum.NO.getCode());
			baseMapper.update(aiModelEntity,
					Wrappers.<AiModelEntity>lambdaUpdate()
						.eq(AiModelEntity::getModelType, aiModel.getModelType())
						.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode()));
		}
	}

	private void unsetDefaultVoiceModelsWithSameCapability(AiModelEntity aiModel) {
		String voiceType = SpeechRecognitionSupport.voiceType(aiModel);
		if (StrUtil.isBlank(voiceType)) {
			throw new IllegalArgumentException("Voice 模型缺少 extData.voiceType");
		}
		List<AiModelEntity> existingDefaults = baseMapper.selectList(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getModelType, ModelTypeEnums.VOICE.getType())
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode())
			.isNotNull(AiModelEntity::getExtData));
		existingDefaults.stream()
			.filter(existing -> existing.getId() != null && !existing.getId().equals(aiModel.getId()))
			.filter(existing -> voiceType.equals(SpeechRecognitionSupport.voiceType(existing)))
			.forEach(existing -> {
				existing.setDefaultModel(YesNoEnum.NO.getCode());
				baseMapper.updateById(existing);
			});
	}

	private void unsetAssistantModels(Long selectedModelId) {
		AiModelEntity update = new AiModelEntity();
		update.setAssistantModel(YesNoEnum.NO.getCode());
		var wrapper = Wrappers.<AiModelEntity>lambdaUpdate()
			.eq(AiModelEntity::getModelType, ModelTypeEnums.CHAT.getType())
			.eq(AiModelEntity::getAssistantModel, YesNoEnum.YES.getCode());
		if (selectedModelId != null) {
			wrapper.ne(AiModelEntity::getId, selectedModelId);
		}
		baseMapper.update(update, wrapper);
	}

	private RuntimeException translateModelConflict(DuplicateKeyException exception) {
		if (causeChainMatches(exception, ASSISTANT_MODEL_DUPLICATE_KEY)) {
			return new IllegalArgumentException(ASSISTANT_MODEL_CONFLICT_MESSAGE, exception);
		}
		if (causeChainMatches(exception, VOICE_DEFAULT_DUPLICATE_KEY)) {
			return new IllegalArgumentException(VOICE_DEFAULT_CONFLICT_MESSAGE, exception);
		}
		return exception;
	}

	private boolean causeChainMatches(Throwable cause, Pattern pattern) {
		Throwable current = cause;
		while (current != null) {
			if (current.getMessage() != null && pattern.matcher(current.getMessage()).find()) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private void validateModelForWrite(AiModelEntity aiModel) {
		if (aiModel == null) {
			throw new IllegalArgumentException("模型配置不能为空");
		}
		endpointPolicy.validate(aiModel.getBaseUrl());
		if (aiModel.getId() == null && ((StrUtil.isNotBlank(aiModel.getApiKey()) && aiModel.getApiKey().contains("*"))
				|| (StrUtil.isNotBlank(aiModel.getSecretKey()) && aiModel.getSecretKey().contains("*")))) {
			throw new IllegalArgumentException("新增模型不能使用脱敏后的密钥");
		}
	}

	private void ensureStoredSecretEndpointUnchanged(AiModelEntity request, AiModelEntity existing) {
		if (!endpointPolicy.sameEndpoint(request.getBaseUrl(), existing.getBaseUrl())) {
			throw new IllegalArgumentException("修改 baseUrl 后必须重新输入 API Key/Secret Key");
		}
	}

	private String ollamaBaseUrl(String baseUrl) {
		String normalized = baseUrl.trim().replaceAll("/+$", "");
		return normalized.endsWith("/v1") ? normalized.substring(0, normalized.length() - 3) : normalized;
	}

	/**
	 * 获取Ollama模型列表
	 * <p>
	 * 通过Hutool HttpUtil调用Ollama的 /api/tags 端点 直接返回原始响应
	 * @param baseUrl Ollama服务器的基础URL地址
	 * @return 操作结果，包含Ollama原始响应字符串
	 */
	@Override
	public R getOllamaModels(String baseUrl) {
		// 参数校验
		if (StrUtil.isBlank(baseUrl)) {
			log.warn("获取Ollama模型列表失败: baseUrl为空");
			return R.failed("baseUrl不能为空");
		}

		try {
			endpointPolicy.validate(baseUrl);
			// 构建Ollama API端点 (去除可能的 /v1 后缀)
			String apiUrl = ollamaBaseUrl(baseUrl) + "/api/tags";
			log.info("正在从Ollama服务器获取模型列表: {}", apiUrl);

			// 调用Ollama API并直接返回响应。禁止跟随重定向，避免绕过主机校验。
			HttpResponse httpResponse = HttpRequest.get(apiUrl).timeout(10000).setFollowRedirects(false).execute();
			ensureSuccess(httpResponse);
			String response = httpResponse.body();
			log.info("成功获取Ollama模型列表");

			return R.ok(response);
		}
		catch (Exception e) {
			log.error("获取Ollama模型列表失败: {}", e.getMessage(), e);
			return R.failed("获取Ollama模型列表失败: " + e.getMessage());
		}
	}

	/**
	 * 测试AI模型连接
	 * <p>
	 * 根据模型配置参数临时构建模型实例，验证连通性。 按模型类型执行最小化测试请求，10秒超时。
	 * @param aiModel AI模型实体，包含测试所需的配置信息
	 * @return 操作结果，包含 success/message/latency
	 */
	@Override
	public R testModel(AiModelEntity aiModel) {
		if (aiModel == null) {
			return R.failed("模型配置不能为空");
		}
		String modelType = aiModel.getModelType();
		if (StrUtil.isBlank(modelType)) {
			return R.failed("模型类型不能为空");
		}
		if (StrUtil.isBlank(aiModel.getBaseUrl())) {
			return R.failed("baseUrl不能为空");
		}
		try {
			endpointPolicy.validate(aiModel.getBaseUrl());
		}
		catch (IllegalArgumentException ex) {
			return R.failed(ex.getMessage());
		}

		// 如果有id且apiKey为空或脱敏值，从数据库读取真实apiKey
		try {
			resolveApiKey(aiModel);
		}
		catch (IllegalArgumentException ex) {
			return R.failed(ex.getMessage());
		}

		if (StrUtil.isBlank(aiModel.getApiKey())) {
			return R.failed("apiKey不能为空");
		}

		long startTime = System.currentTimeMillis();
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> doTestModel(aiModel));
		try {
			future.get(10, TimeUnit.SECONDS);
			long latency = System.currentTimeMillis() - startTime;
			Map<String, Object> data = new HashMap<>(4);
			data.put("success", true);
			data.put("message", "连接成功");
			data.put("latency", latency);
			return R.ok(data);
		}
		catch (TimeoutException e) {
			future.cancel(true);
			long latency = System.currentTimeMillis() - startTime;
			Map<String, Object> data = new HashMap<>(4);
			data.put("success", false);
			data.put("message", "连接超时(10s)");
			data.put("latency", latency);
			return R.ok(data);
		}
		catch (ExecutionException e) {
			long latency = System.currentTimeMillis() - startTime;
			Map<String, Object> data = new HashMap<>(4);
			data.put("success", false);
			data.put("message", parseTestErrorMessage(e.getCause()));
			data.put("latency", latency);
			return R.ok(data);
		}
		catch (Exception e) {
			long latency = System.currentTimeMillis() - startTime;
			Map<String, Object> data = new HashMap<>(4);
			data.put("success", false);
			data.put("message", "测试失败: " + e.getMessage());
			data.put("latency", latency);
			return R.ok(data);
		}
	}

	/**
	 * 动态拉取供应商模型列表
	 * <p>
	 * 按供应商分支处理：Ollama 走 /api/tags，百炼优先读取原生分页目录，其余按 OpenAI 兼容协议走 /models。 Azure/Gemini
	 * 等暂不支持的供应商返回空列表，由前端使用写死常量兜底。
	 * @param aiModel AI模型实体，需含 provider、baseUrl、apiKey
	 * @return 操作结果，data 为模型名列表
	 */
	@Override
	public R fetchProviderModels(AiModelEntity aiModel) {
		if (aiModel == null) {
			return R.failed("模型配置不能为空");
		}
		if (StrUtil.isBlank(aiModel.getBaseUrl())) {
			return R.failed("baseUrl不能为空");
		}
		String provider = aiModel.getProvider();
		try {
			endpointPolicy.validate(aiModel.getBaseUrl());
			// Ollama 单独走 /api/tags 端点，无需 apiKey
			if ("Ollama".equalsIgnoreCase(provider)) {
				return R.ok(parseOllamaModels(aiModel.getBaseUrl()));
			}
			// Azure/Gemini 协议与 OpenAI 兼容模式差异较大，暂不支持，返回空列表由前端兜底
			if ("Azure".equalsIgnoreCase(provider) || "Gemini".equalsIgnoreCase(provider)) {
				return R.ok(new ArrayList<String>());
			}
			// 其余按 OpenAI 兼容协议处理，需要真实 apiKey
			resolveApiKey(aiModel);
			if (StrUtil.isBlank(aiModel.getApiKey())) {
				return R.failed("apiKey不能为空");
			}
			if ("Aliyun".equalsIgnoreCase(provider)) {
				String catalog = AliyunModelCatalog.endpoint(aiModel.getBaseUrl());
				if (catalog != null) {
					endpointPolicy.validate(catalog);
					try {
						return R.ok(AliyunModelCatalog.readAll(catalog,
								(url, timeout) -> fetchAliyunCatalogPage(url, aiModel.getApiKey(), timeout)));
					}
					catch (AliyunModelCatalog.Unsupported unsupported) {
						// 仅旧地址未提供目录时回退兼容接口，认证、限流或分页失败不能冒充成功。
					}
				}
			}
			return R.ok(parseOpenAiModels(aiModel.getBaseUrl(), aiModel.getApiKey()));
		}
		catch (Exception e) {
			log.error("拉取供应商模型列表失败: provider={}, baseUrl={}, error={}", provider, aiModel.getBaseUrl(), e.getMessage());
			return R.failed(parseTestErrorMessage(e));
		}
	}

	private AliyunModelCatalog.Response fetchAliyunCatalogPage(String url, String apiKey, int timeout) {
		try (HttpResponse response = HttpRequest.get(url)
			.header("Authorization", "Bearer " + apiKey)
			.timeout(timeout)
			.setFollowRedirects(false)
			.executeAsync()) {
			if (response.getStatus() != 200) {
				return new AliyunModelCatalog.Response(response.getStatus(), null);
			}
			int limit = 4 * 1024 * 1024;
			byte[] bytes = response.bodyStream().readNBytes(limit + 1);
			if (bytes.length > limit) {
				throw new IllegalStateException("百炼模型目录响应超过大小限制");
			}
			String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
			if (!JSONUtil.isTypeJSONObject(body)) {
				throw new IllegalStateException("百炼模型目录响应不是有效JSON");
			}
			try {
				return new AliyunModelCatalog.Response(200, JSONUtil.parseObj(body));
			}
			catch (cn.hutool.json.JSONException ex) {
				throw new IllegalStateException("百炼模型目录响应不是有效JSON");
			}
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException("读取百炼模型目录失败，请重试");
		}
	}

	/**
	 * 解析 OpenAI 兼容协议的 /models 端点，返回模型名(data[].id)列表
	 */
	private List<String> parseOpenAiModels(String baseUrl, String apiKey) {
		String url = baseUrl.replaceAll("/+$", "") + "/models";
		HttpResponse response = HttpRequest.get(url)
			.header("Authorization", "Bearer " + apiKey)
			.timeout(10000)
			.setFollowRedirects(false)
			.execute();
		int status = response.getStatus();
		if (status == 401 || status == 403) {
			throw new RuntimeException("认证失败(HTTP " + status + ")");
		}
		ensureSuccess(response);
		List<String> models = new ArrayList<>();
		String body = response.body();
		if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
			return models;
		}
		JSONArray dataArray = JSONUtil.parseObj(body).getJSONArray("data");
		if (dataArray == null) {
			return models;
		}
		for (Object item : dataArray) {
			if (item instanceof JSONObject) {
				String id = ((JSONObject) item).getStr("id");
				if (StrUtil.isNotBlank(id)) {
					models.add(id);
				}
			}
		}
		return models;
	}

	/**
	 * 解析 Ollama 的 /api/tags 端点，返回模型名(models[].name)列表
	 */
	private List<String> parseOllamaModels(String baseUrl) {
		String url = ollamaBaseUrl(baseUrl) + "/api/tags";
		HttpResponse response = HttpRequest.get(url).timeout(10000).setFollowRedirects(false).execute();
		ensureSuccess(response);
		String body = response.body();
		List<String> models = new ArrayList<>();
		if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
			return models;
		}
		JSONArray modelArray = JSONUtil.parseObj(body).getJSONArray("models");
		if (modelArray == null) {
			return models;
		}
		for (Object item : modelArray) {
			if (item instanceof JSONObject) {
				String name = ((JSONObject) item).getStr("name");
				if (StrUtil.isNotBlank(name)) {
					models.add(name);
				}
			}
		}
		return models;
	}

	/**
	 * 解析真实 apiKey：当有 id 且 apiKey 为空或脱敏值(含*)时，从数据库读取真实值
	 */
	private void resolveApiKey(AiModelEntity aiModel) {
		if (aiModel.getId() != null && (StrUtil.isBlank(aiModel.getApiKey()) || aiModel.getApiKey().contains("*"))) {
			AiModelEntity dbModel = baseMapper.selectById(aiModel.getId());
			if (dbModel == null) {
				throw new IllegalArgumentException("模型不存在或无权访问");
			}
			ensureStoredSecretEndpointUnchanged(aiModel, dbModel);
			aiModel.setApiKey(dbModel.getApiKey());
		}
	}

	/**
	 * 按模型类型执行最小化测试
	 */
	private void doTestModel(AiModelEntity aiModel) {
		switch (aiModel.getModelType()) {
			case "Chat":
			case "Reason":
				// 限制响应token数，减少消耗
				aiModel.setResponseLimit(5);
				Pair<ChatModel, AiAssistantService> chatPair = modelProvider.buildAiAssistant(aiModel);
				chatPair.getKey().chat("hi");
				break;
			case "Embedding":
				DimensionAwareEmbeddingModel embeddingModel = modelProvider.buildEmbeddingModel(aiModel);
				embeddingModel.embed("test");
				break;
			case "Reranker":
				ScoringModel scoringModel = modelProvider.buildScoringModel(aiModel);
				scoringModel.scoreAll(Collections.singletonList(TextSegment.from("document")), "query");
				break;
			case "Vision":
			case "Image":
			case "Video":
			case "Search":
				// 多模态类型（视觉/图像/视频/搜索）覆盖的模型差异大（如 qwen-vl-ocr 要求 messages 含图片），
				// 统一用 /models 端点验证 apiKey 有效性，避免 chat("hi") 在部分专用模型上被后端校验拒绝
				testHttpAuth(aiModel);
				break;
			case "Voice":
				if ("1".equals(SpeechRecognitionSupport.voiceType(aiModel))) {
					throw new RuntimeException("语音识别模型需上传真实音频验证");
				}
				testHttpAuth(aiModel);
				break;
			default:
				throw new RuntimeException("该类型暂不支持连接测试");
		}
	}

	/**
	 * 通过HTTP请求验证API认证连通性 调用 /models 端点验证API Key是否有效
	 */
	private void testHttpAuth(AiModelEntity aiModel) {
		String baseUrl = aiModel.getBaseUrl().replaceAll("/+$", "");
		HttpResponse response = HttpRequest.get(baseUrl + "/models")
			.header("Authorization", "Bearer " + aiModel.getApiKey())
			.timeout(10000)
			.setFollowRedirects(false)
			.execute();
		int status = response.getStatus();
		if (status == 401 || status == 403) {
			throw new RuntimeException("认证失败(HTTP " + status + ")");
		}
		ensureSuccess(response);
	}

	private void ensureSuccess(HttpResponse response) {
		int status = response.getStatus();
		if (status < 200 || status >= 300) {
			throw new RuntimeException("模型服务调用失败(HTTP " + status + ")");
		}
	}

	/**
	 * 解析测试异常为友好错误信息
	 */
	private String parseTestErrorMessage(Throwable e) {
		if (e == null) {
			return "未知错误";
		}
		String msg = e.getMessage();
		if (msg == null) {
			msg = e.getClass().getSimpleName();
		}
		if (msg.contains("401") || msg.contains("Unauthorized")) {
			return "认证失败: API Key无效";
		}
		if (msg.contains("403") || msg.contains("Forbidden")) {
			return "认证失败: 无访问权限";
		}
		if (msg.contains("404") || msg.contains("Not Found")) {
			return "模型不存在或接口地址错误";
		}
		if (msg.contains("ConnectException") || msg.contains("Connection refused")) {
			return "网络不可达: 无法连接到服务器";
		}
		if (msg.contains("UnknownHostException")) {
			return "网络不可达: 域名解析失败";
		}
		if (msg.contains("SocketTimeoutException") || msg.contains("timed out")) {
			return "连接超时";
		}
		return "测试失败: " + msg;
	}

}
