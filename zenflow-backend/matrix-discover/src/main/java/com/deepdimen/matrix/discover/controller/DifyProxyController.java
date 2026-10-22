package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.dify.DocumentTextExtractor;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.DifyChatRecordService;
import com.deepdimen.matrix.discover.service.DifyChatRecordService.DifyChatContext;
import com.deepdimen.matrix.discover.service.DifyChatRecordService.DifyChatResult;
import com.deepdimen.matrix.discover.service.SkillProcessor;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService;
import com.deepdimen.matrix.discover.vo.AiAgentPermissionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify Service API 通用透传控制器
 * <p>
 * 路径映射: /dify/** -> Dify /v1/** 鉴权: 从请求头 X-Agent-Id 查询智能体获取 Dify API Key 增强: 自动注入当前登录用户
 * ID 到请求体 user 字段
 *
 * @author matrix
 * @date 2026/03/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dify")
@ConditionalOnProperty(prefix = "matrix.dify", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(description = "dify", name = "Dify Service API 透传")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class DifyProxyController {

	private static final String RETIRED_EXTENDED_SYSTEM_PROMPT = "extended_system_prompt";

	private final DifyServiceClient difyServiceClient;

	private final AiAgentService aiAgentService;

	private final AiAgentPermissionService aiAgentPermissionService;

	private final DifyChatRecordService difyChatRecordService;

	private final DocumentTextExtractor documentTextExtractor;

	private final ObjectMapper objectMapper;

	private final SkillProcessor skillProcessor;

	private final TenantAgentRouteService tenantAgentRouteService;

	@org.springframework.beans.factory.annotation.Value("${matrix.skill.market.enabled:false}")
	private boolean marketSkillEnabled;

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<com.deepdimen.matrix.discover.skill.MarketSkillRequestStore> marketSkillRequests;

	private final Cache<Long, JsonNode> parametersCache = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofSeconds(60))
		.maximumSize(200)
		.build();

	/**
	 * 异常处理: 智能体不存在或未配置 API Key
	 */
	@ExceptionHandler(DifyProxyException.class)
	public void handleDifyProxyException(DifyProxyException e, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		log.warn("[Dify Proxy] {}", e.getMessage());
		writeErrorResponse(response, request, e.getStatus().value(), e.getMessage());
	}

	/**
	 * 异常处理: Dify API 返回的 HTTP 错误（4xx/5xx），透传错误信息给前端
	 */
	@ExceptionHandler(WebClientResponseException.class)
	public void handleWebClientResponseException(WebClientResponseException e, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		log.warn("[Dify Proxy] Dify API 错误: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
		String message = "Dify API 错误: " + e.getStatusCode().value();
		try {
			JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
			if (body.has("message")) {
				message = body.get("message").asText();
			}
		}
		catch (Exception ignored) {
		}
		writeErrorResponse(response, request, e.getStatusCode().value(), message);
	}

	/**
	 * 统一错误响应: SSE 请求以 event-stream 格式返回，其他请求以 JSON 格式返回
	 */
	private void writeErrorResponse(HttpServletResponse response, HttpServletRequest request, int statusCode,
			String message) throws IOException {
		String accept = request.getHeader(HttpHeaders.ACCEPT);
		if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
			response.setStatus(HttpStatus.OK.value());
			response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
			response.setCharacterEncoding("UTF-8");
			PrintWriter writer = response.getWriter();
			String errorJson = objectMapper.writeValueAsString(Map.of("message", message, "code", statusCode));
			writer.write("event: error\ndata: " + errorJson + "\n\n");
			writer.flush();
		}
		else {
			response.setStatus(statusCode);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(objectMapper.writeValueAsString(R.failed(message)));
		}
	}

	/**
	 * 获取 Dify 应用参数配置（user_input_form）
	 * <p>
	 * 透传 Dify /v1/parameters 接口的 user_input_form 字段，非 Dify 类型 Agent 返回空数组。 使用 Caffeine
	 * 本地缓存（60秒 TTL）降低 Dify 请求压力。
	 */
	@GetMapping("/chat/agent/{agentId}/parameters")
	@Operation(summary = "获取 Dify 应用参数配置")
	public R<JsonNode> getAgentParameters(@PathVariable Long agentId) {
		AiAgentEntity agent = aiAgentService.getById(agentId);
		if (agent == null) {
			throw new DifyProxyException(HttpStatus.NOT_FOUND, "智能体不存在: " + agentId);
		}
		if (!"dify".equals(agent.getAgentType())) {
			return R.ok(emptyInputForm());
		}
		if (!"published".equals(agent.getStatus())) {
			return R.ok(emptyInputForm());
		}
		checkAgentPermission(agentId);
		String apiKey = agent.getDifyApiKey();
		if (apiKey == null || apiKey.isEmpty()) {
			return R.ok(emptyInputForm());
		}
		JsonNode cached = parametersCache.get(agentId, id -> {
			try {
				JsonNode result = difyServiceClient.getParameters(apiKey).block();
				if (result != null && result.has("user_input_form")) {
					return filterRetiredInputFields(result);
				}
				return emptyInputForm();
			}
			catch (Exception e) {
				log.warn("[Dify Proxy] 获取 Dify 应用参数失败, agentId={}: {}", id, e.getMessage());
				return null;
			}
		});
		if (cached == null) {
			return R.ok(emptyInputForm());
		}
		return R.ok(cached);
	}

	private JsonNode filterRetiredInputFields(JsonNode parameters) {
		JsonNode forms = parameters.get("user_input_form");
		if (!(parameters instanceof ObjectNode source) || !forms.isArray()) {
			return parameters;
		}
		ObjectNode filtered = source.deepCopy();
		ArrayNode visibleForms = objectMapper.createArrayNode();
		for (JsonNode form : forms) {
			if (!form.findValuesAsText("variable").contains(RETIRED_EXTENDED_SYSTEM_PROMPT)) {
				visibleForms.add(form);
			}
		}
		filtered.set("user_input_form", visibleForms);
		return filtered;
	}

	private ObjectNode emptyInputForm() {
		ObjectNode node = objectMapper.createObjectNode();
		node.putArray("user_input_form");
		return node;
	}

	/**
	 * POST 透传（自动识别流式/非流式）
	 * <p>
	 * 根据请求体中 response_mode 字段判断: - "streaming" -> SSE 流式响应 - 其他 -> 普通 JSON 响应
	 */
	@PostMapping(value = "/{*path}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Dify POST 透传")
	public void post(@PathVariable String path, @RequestHeader("X-Agent-Id") Long agentId, @RequestBody String body,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		postInternal(path, agentId, body, request, response, null);
	}

	/**
	 * Internal Java entry only; the managed executor must authorize the frozen binding
	 * first.
	 */
	public void invokeManagedChat(Long agentId, String body, HttpServletRequest request, HttpServletResponse response,
			com.deepdimen.matrix.discover.skill.DifyManagedChatExchange exchange) throws IOException {
		java.util.Objects.requireNonNull(exchange);
		if (AiAgentService.DEFAULT_AGENT_ID.equals(agentId))
			throw new IllegalArgumentException("FROZEN_CONSUMER");
		var agent = aiAgentService.getById(agentId);
		if (agent == null || !"dify".equals(agent.getAgentType()) || !"chat".equals(agent.getDifyMode()))
			throw new IllegalArgumentException("AGENT_SKILL_UNSUPPORTED");
		postInternal("chat-messages", agentId, body, request, response, exchange);
		exchange.result();
	}

	private void postInternal(String path, Long agentId, String body, HttpServletRequest request,
			HttpServletResponse response, com.deepdimen.matrix.discover.skill.DifyManagedChatExchange managed)
			throws IOException {
		String apiKey = resolveApiKey(agentId);
		String difyPath = normalizePath(path);

		// 一次解析，同时完成 user 注入和 streaming 判断
		ObjectNode bodyNode = parseAndEnhance(body, agentId);

		boolean isChatMessages = isChatPath(difyPath);
		if (managed == null && isChatMessages && !AiAgentService.DEFAULT_AGENT_ID.equals(agentId)
				&& !bodyNode.path("conversation_id").asText("").isBlank()) {
			var requests = marketSkillRequests == null ? null : marketSkillRequests.getIfAvailable();
			if (requests == null)
				throw new DifyProxyException(HttpStatus.SERVICE_UNAVAILABLE, "Skill 会话检查暂不可用");
			if (requests.isManagedConversation(agentId, bodyNode.path("conversation_id").asText(), !marketSkillEnabled))
				throw new DifyProxyException(HttpStatus.CONFLICT, "该会话已使用 Skill，请选择原 Skill 或新建会话后发送");
		}

		// 先提取原始 query（用于记录），再注入文档文本
		String originalQuery = null;
		String sessionInputsJson = null;
		boolean newConversation = false;
		if (isChatMessages) {
			originalQuery = bodyNode.path("query").asText("");
			if (originalQuery.isEmpty()) {
				originalQuery = bodyNode.path("inputs").path("query").asText("");
			}

			newConversation = !bodyNode.has("conversation_id") || bodyNode.path("conversation_id").asText("").isEmpty();
			skillProcessor.apply(bodyNode);
			sessionInputsJson = resolveAndInjectInputs(bodyNode);
			// Managed attachments stay as native Dify references. Ownership must be
			// checked by Dify before content is read; do not copy the unscoped local
			// cache.
			if (managed == null)
				injectDocumentTexts(bodyNode);
			// Freeze user input history before adding the server-authorized instruction.
			if (managed != null)
				((ObjectNode) bodyNode.with("inputs")).put("extended_system_prompt", managed.instruction());
		}

		boolean streaming = "streaming".equals(bodyNode.path("response_mode").asText());
		String enhancedBody = objectMapper.writeValueAsString(bodyNode);

		DifyChatContext chatContext = null;
		if (isChatMessages) {
			chatContext = new DifyChatContext(agentId, getCurrentUsername(), originalQuery, getClientIp(request),
					request.getHeader("User-Agent"), System.currentTimeMillis(), TenantContextHolder.getTenantId(),
					newConversation, sessionInputsJson);
		}

		if (streaming) {
			log.debug("[Dify Proxy] POST SSE {} agentId={}", difyPath, agentId);
			response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("Connection", "keep-alive");
			response.flushBuffer();

			StringBuilder answerBuilder = isChatMessages ? new StringBuilder() : null;
			DifyChatResult[] chatResult = new DifyChatResult[1];
			DifyChatContext ctx = chatContext;

			PrintWriter writer = response.getWriter();
			if (managed != null)
				managed.start();
			var stream = difyServiceClient.postStream(difyPath, apiKey, enhancedBody);
			if (managed != null)
				stream = stream.timeout(Duration.ofSeconds(90));
			stream.doOnNext(sse -> {
				if (managed != null)
					managed.event(sse.event(), sse.data());
				// 透传 SSE 事件
				if (sse.event() != null) {
					writer.write("event: " + sse.event() + "\n");
				}
				if (sse.data() != null) {
					writer.write("data: " + sse.data() + "\n");
				}
				writer.write("\n");
				writer.flush();
				if (managed != null && writer.checkError())
					throw new IllegalStateException("DIFY_CLIENT_DISCONNECTED");

				// 旁路收集对话数据
				if (ctx != null && sse.data() != null) {
					// Dify SSE 事件类型可能在标准 event: 行，也可能在 JSON data 的 event 字段中
					String eventType = sse.event();
					if (eventType == null || eventType.isEmpty()) {
						eventType = extractEventFromData(sse.data());
					}
					if (eventType != null) {
						collectSseEvent(eventType, sse.data(), answerBuilder, chatResult);
					}
				}
			}).doOnComplete(() -> {
				if (managed != null)
					chatResult[0] = managedHistoryResult(chatResult[0], managed);
				if (managed == null)
					writer.close();
				if (ctx != null && chatResult[0] != null) {
					difyChatRecordService.asyncSaveChatRecord(ctx, chatResult[0]);
				}
			}).doOnError(e -> {
				if (managed != null)
					managed.fail(e);
				DifyStreamFailure failure = DifyStreamFailure.from(e, objectMapper);
				log.error("[Dify Proxy] SSE 流式传输异常 code={} path={} agentId={} correlationId={}", failure.code(),
						difyPath, agentId, request.getHeader("X-Correlation-ID"), e);
				try {
					String errMsg = objectMapper
						.writeValueAsString(Map.of("message", failure.message(), "code", failure.code()));
					writer.write("event: error\ndata: " + errMsg + "\n\n");
					writer.flush();
					if (managed == null)
						writer.close();
				}
				catch (Exception ignored) {
				}
				// 异常时如果已收集到完整对话数据，仍然写入
				if (managed == null && ctx != null && chatResult[0] != null) {
					difyChatRecordService.asyncSaveChatRecord(ctx, chatResult[0]);
				}
			}).onErrorComplete().blockLast();
		}
		else {
			log.debug("[Dify Proxy] POST {} agentId={}", difyPath, agentId);
			if (managed != null)
				managed.start();
			var pending = difyServiceClient.post(difyPath, apiKey, enhancedBody);
			JsonNode result = managed == null ? pending.block() : pending.block(Duration.ofSeconds(90));
			if (managed != null)
				managed.blocking(result);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			if (managed == null)
				response.getWriter().write(objectMapper.writeValueAsString(result));

			if (isChatMessages && chatContext != null && result != null) {
				DifyChatResult chatResult = extractBlockingResult(result);
				if (managed != null)
					chatResult = managedHistoryResult(chatResult, managed);
				if (chatResult != null) {
					difyChatRecordService.asyncSaveChatRecord(chatContext, chatResult);
				}
			}

			// 评价旁路回写: messages/{id}/feedbacks
			String feedbackMessageId = extractFeedbackMessageId(difyPath);
			if (feedbackMessageId != null) {
				String rating = bodyNode.path("rating").isNull() ? null : bodyNode.path("rating").asText(null);
				difyChatRecordService.asyncUpdateEvaluate(TenantContextHolder.getTenantId(), feedbackMessageId, rating);
			}
		}
	}

	private DifyChatResult managedHistoryResult(DifyChatResult prior,
			com.deepdimen.matrix.discover.skill.DifyManagedChatExchange managed) {
		try {
			JsonNode result = objectMapper.readTree(managed.result());
			return new DifyChatResult(result.path("answer").asText(), result.path("conversationId").asText(),
					result.path("messageId").asText(), prior == null ? null : prior.model(),
					prior == null ? null : prior.promptTokens(), prior == null ? null : prior.completionTokens(),
					prior == null ? null : prior.totalTokens());
		}
		catch (IOException failure) {
			throw new IllegalStateException("DIFY_RESULT_UNKNOWN", failure);
		}
	}

	/**
	 * POST Multipart 透传（文件上传等）
	 * <p>
	 * Dify /files/upload 要求 multipart/form-data 格式， 自动注入 user 字段到 multipart body 中
	 */
	@PostMapping(value = "/{*path}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Dify POST Multipart 透传")
	public ResponseEntity<JsonNode> postMultipart(@PathVariable String path, @RequestHeader("X-Agent-Id") Long agentId,
			@RequestParam("file") MultipartFile file) {
		String apiKey = resolveApiKey(agentId);
		String difyPath = normalizePath(path);
		log.debug("[Dify Proxy] POST Multipart {} agentId={}", difyPath, agentId);

		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		try {
			String fixedName = DocumentTextExtractor.fixFilename(file.getOriginalFilename());
			if (fixedName == null || fixedName.isEmpty()) {
				fixedName = "file";
			}
			byte[] bytes = file.getBytes();
			final String finalName = fixedName;
			org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(
					bytes) {
				@Override
				public String getFilename() {
					return finalName;
				}
			};
			builder.part("file", resource)
				.filename(fixedName)
				.header("Content-Type",
						file.getContentType() != null ? file.getContentType() : "application/octet-stream");
		}
		catch (Exception e) {
			throw new DifyProxyException(HttpStatus.BAD_REQUEST, "文件读取失败");
		}
		builder.part("user", getDifyUser(agentId));

		JsonNode result = difyServiceClient
			.postMultipart(difyPath, apiKey, BodyInserters.fromMultipartData(builder.build()))
			.block();

		// 文档类型文件: 提取文本并缓存，供对话时注入 query
		if (result != null && result.has("id") && documentTextExtractor.isDocumentType(file.getOriginalFilename())) {
			String uploadFileId = result.get("id").asText();
			documentTextExtractor.extractAndCache(uploadFileId, file);
		}

		return ResponseEntity.ok(result);
	}

	/**
	 * 语音播报透传（text-to-audio）
	 * <p>
	 * 精确路径映射，优先级高于通用 POST catch-all。Dify /v1/text-to-audio 返回二进制音频流
	 * （audio/mpeg），需以字节流原样写回，不能走通用 post() 的 JSON 处理逻辑。 鉴权与 user
	 * 注入复用通用透传逻辑，请求体（text/message_id/voice/streaming）原样透传。
	 */
	@PostMapping(value = "/text-to-audio", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Dify 语音播报透传（text-to-audio）")
	public void textToAudio(@RequestHeader("X-Agent-Id") Long agentId, @RequestBody(required = false) String body,
			HttpServletResponse response) throws IOException {
		String apiKey = resolveApiKey(agentId);
		ObjectNode bodyNode = parseAndEnhance(body, agentId);
		String enhancedBody = toJson(bodyNode);
		log.debug("[Dify Proxy] POST /text-to-audio agentId={}", agentId);

		ResponseEntity<byte[]> result = difyServiceClient.postRaw("/text-to-audio", apiKey, enhancedBody).block();
		if (result == null || result.getBody() == null) {
			throw new DifyProxyException(HttpStatus.INTERNAL_SERVER_ERROR, "语音合成失败：Dify 返回空响应");
		}
		MediaType contentType = result.getHeaders().getContentType();
		response.setContentType(contentType != null ? contentType.toString() : "audio/mpeg");
		response.getOutputStream().write(result.getBody());
		response.getOutputStream().flush();
	}

	/**
	 * GET 透传（messages、conversations、parameters、meta 等）
	 */
	@GetMapping("/{*path}")
	@Operation(summary = "Dify GET 透传")
	public ResponseEntity<JsonNode> get(@PathVariable String path, @RequestHeader("X-Agent-Id") Long agentId,
			HttpServletRequest request) {
		String apiKey = resolveApiKey(agentId);
		Map<String, String> queryParams = extractQueryParams(request);
		injectUserParam(queryParams, agentId);
		String difyPath = normalizePath(path);
		log.debug("[Dify Proxy] GET {} agentId={}", difyPath, agentId);
		JsonNode result = difyServiceClient.get(difyPath, apiKey, queryParams).block();
		if ("/parameters".equals(difyPath) && result != null && result.has("user_input_form")) {
			result = filterRetiredInputFields(result);
		}
		return ResponseEntity.ok(result);
	}

	/**
	 * DELETE 透传（删除会话等，Dify 要求 body 中包含 user 字段）
	 */
	@DeleteMapping(value = "/{*path}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Dify DELETE 透传")
	public ResponseEntity<JsonNode> delete(@PathVariable String path, @RequestHeader("X-Agent-Id") Long agentId,
			@RequestBody(required = false) String body) {
		String apiKey = resolveApiKey(agentId);
		String difyPath = normalizePath(path);
		log.debug("[Dify Proxy] DELETE {} agentId={}", difyPath, agentId);
		ObjectNode bodyNode = parseAndEnhance(body, agentId);
		String enhancedBody = toJson(bodyNode);
		JsonNode result = difyServiceClient.delete(difyPath, apiKey, enhancedBody).block();
		return ResponseEntity.ok(result);
	}

	/**
	 * PUT 透传（更新会话变量、更新分段等，body 注入 user 字段）
	 */
	@PutMapping(value = "/{*path}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Dify PUT 透传")
	public ResponseEntity<JsonNode> put(@PathVariable String path, @RequestHeader("X-Agent-Id") Long agentId,
			@RequestBody(required = false) String body) {
		String apiKey = resolveApiKey(agentId);
		String difyPath = normalizePath(path);
		log.debug("[Dify Proxy] PUT {} agentId={}", difyPath, agentId);
		ObjectNode bodyNode = parseAndEnhance(body, agentId);
		String enhancedBody = toJson(bodyNode);
		JsonNode result = difyServiceClient.put(difyPath, apiKey, enhancedBody).block();
		return ResponseEntity.ok(result);
	}

	/**
	 * PATCH 透传（变更文档状态等，body 注入 user 字段）
	 */
	@PatchMapping(value = "/{*path}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Dify PATCH 透传")
	public ResponseEntity<JsonNode> patch(@PathVariable String path, @RequestHeader("X-Agent-Id") Long agentId,
			@RequestBody(required = false) String body) {
		String apiKey = resolveApiKey(agentId);
		String difyPath = normalizePath(path);
		log.debug("[Dify Proxy] PATCH {} agentId={}", difyPath, agentId);
		ObjectNode bodyNode = parseAndEnhance(body, agentId);
		String enhancedBody = toJson(bodyNode);
		JsonNode result = difyServiceClient.patch(difyPath, apiKey, enhancedBody).block();
		return ResponseEntity.ok(result);
	}

	/**
	 * 判断路径是否为对话类请求（chat-messages / workflows/run / completion-messages）
	 */
	private boolean isChatPath(String path) {
		return path.contains("chat-messages") || path.contains("workflows/run") || path.contains("completion-messages");
	}

	/**
	 * 从路径中提取 feedbacks 的 messageId（格式: messages/{id}/feedbacks）
	 * @return messageId，非 feedbacks 路径返回 null
	 */
	private String extractFeedbackMessageId(String path) {
		if (path == null || !path.contains("/feedbacks")) {
			return null;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("messages/([^/]+)/feedbacks").matcher(path);
		return m.find() ? m.group(1) : null;
	}

	/**
	 * 从 SSE data JSON 中提取 event 字段（Dify 部分场景事件类型在 JSON 内而非标准 event: 行）
	 */
	private String extractEventFromData(String data) {
		try {
			if (data != null && data.startsWith("{")) {
				JsonNode node = objectMapper.readTree(data);
				if (node.has("event")) {
					return node.get("event").asText();
				}
			}
		}
		catch (Exception ignored) {
		}
		return null;
	}

	/**
	 * 收集 SSE 事件中的对话数据（兼容 chat/agent-chat/workflow/completion 模式）
	 */
	private void collectSseEvent(String event, String data, StringBuilder answerBuilder, DifyChatResult[] chatResult) {
		try {
			JsonNode node = objectMapper.readTree(data);
			switch (event) {
				case "message", "agent_message" -> {
					answerBuilder.append(node.path("answer").asText(""));
				}
				case "message_end", "agent_message_end" -> {
					JsonNode usage = node.path("metadata").path("usage");
					String model = usage.path("model").asText(null);
					if (model == null) {
						model = node.path("model").asText(null);
					}
					chatResult[0] = new DifyChatResult(answerBuilder.toString(),
							node.path("conversation_id").asText(null), node.path("id").asText(null), model,
							usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : null,
							usage.has("completion_tokens") ? usage.get("completion_tokens").asLong() : null,
							usage.has("total_tokens") ? usage.get("total_tokens").asLong() : null);
				}
				case "text_chunk" -> {
					String text = node.path("data").path("text").asText("");
					if (text.isEmpty()) {
						text = node.path("text").asText("");
					}
					answerBuilder.append(text);
				}
				case "workflow_finished" -> {
					JsonNode data2 = node.path("data");
					Long totalTokens = data2.has("total_tokens") ? data2.get("total_tokens").asLong() : null;
					String model = data2.path("model").asText(null);
					if (model == null) {
						model = node.path("model").asText(null);
					}
					chatResult[0] = new DifyChatResult(answerBuilder.toString(),
							node.path("conversation_id").asText(null),
							node.path("workflow_run_id").asText(node.path("id").asText(null)), model, null, null,
							totalTokens);
				}
				default -> {
				}
			}
		}
		catch (Exception e) {
			log.warn("[Dify Proxy] SSE 事件解析失败, event={}", event, e);
		}
	}

	/**
	 * 从 blocking 模式 JSON 响应中提取对话结果（兼容 chat/workflow/completion）
	 */
	private DifyChatResult extractBlockingResult(JsonNode result) {
		try {
			// chat/agent-chat/completion 模式
			String answer = result.path("answer").asText("");
			if (!answer.isEmpty()) {
				JsonNode usage = result.path("metadata").path("usage");
				String model = usage.path("model").asText(null);
				if (model == null) {
					model = result.path("model").asText(null);
				}
				return new DifyChatResult(answer, result.path("conversation_id").asText(null),
						result.path("id").asText(null), model,
						usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : null,
						usage.has("completion_tokens") ? usage.get("completion_tokens").asLong() : null,
						usage.has("total_tokens") ? usage.get("total_tokens").asLong() : null);
			}
			// workflow 模式: answer 在 data.outputs.text 中
			JsonNode data = result.path("data");
			if (!data.isMissingNode()) {
				String text = data.path("outputs").path("text").asText("");
				Long totalTokens = data.has("total_tokens") ? data.get("total_tokens").asLong() : null;
				return new DifyChatResult(text, null,
						data.path("workflow_run_id").asText(result.path("workflow_run_id").asText(null)), null, null,
						null, totalTokens);
			}
			return null;
		}
		catch (Exception e) {
			log.warn("[Dify Proxy] blocking 响应解析失败", e);
			return null;
		}
	}

	/**
	 * 获取当前登录用户名
	 */
	private String getCurrentUsername() {
		MatrixUser user = SecurityUtils.getUser();
		if (user != null) {
			return user.getUsername();
		}
		return "anonymous";
	}

	/**
	 * 获取客户端真实 IP
	 */
	private String getClientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
			return ip.split(",")[0].trim();
		}
		ip = request.getHeader("X-Real-IP");
		if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
			return ip;
		}
		return request.getRemoteAddr();
	}

	/**
	 * 根据智能体 ID 获取 Dify API Key，同时校验状态、类型和用户权限
	 */
	private String resolveApiKey(Long agentId) {
		AiAgentEntity agent = AiAgentService.DEFAULT_AGENT_ID.equals(agentId) ? resolvePublishedDefaultAgent(agentId)
				: aiAgentService.getById(agentId);
		if (agent == null) {
			throw new DifyProxyException(HttpStatus.NOT_FOUND, "智能体不存在: " + agentId);
		}
		if (!"dify".equals(agent.getAgentType())) {
			throw new DifyProxyException(HttpStatus.BAD_REQUEST, "该智能体不支持 Dify 透传: " + agentId);
		}
		if (!"published".equals(agent.getStatus())) {
			throw new DifyProxyException(HttpStatus.FORBIDDEN, "智能体未发布，无法使用: " + agentId);
		}
		checkAgentPermission(agentId);
		String apiKey = agent.getDifyApiKey();
		if (apiKey == null || apiKey.isEmpty()) {
			throw new DifyProxyException(HttpStatus.BAD_REQUEST, "智能体未配置 Dify API Key: " + agentId);
		}
		return apiKey;
	}

	private AiAgentEntity resolvePublishedDefaultAgent(Long requestedAgentId) {
		MatrixUser user = SecurityUtils.getUser();
		Long tenantId = TenantContextHolder.getTenantId();
		if (user == null || tenantId == null || !tenantId.equals(user.getTenantId())) {
			throw new DifyProxyException(HttpStatus.FORBIDDEN, "缺少有效租户身份");
		}
		String orgId = "0";
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			String requestedOrg = attributes.getRequest().getHeader("X-ORG-ID");
			if (requestedOrg != null && requestedOrg.matches("\\d+")) {
				orgId = requestedOrg;
			}
		}
		IdentityEnvelope identity = new IdentityEnvelope(tenantId.toString(), user.getId().toString(), orgId,
				List.of(orgId),
				user.getRoleIds() == null ? List.of() : user.getRoleIds().stream().map(String::valueOf).toList(),
				user.getDeptIds() == null ? List.of() : user.getDeptIds().stream().map(String::valueOf).toList(),
				java.util.UUID.randomUUID().toString(), "DIFY_PROXY");
		TenantAgentRouteService.ResolvedTenantAgentRoute route = tenantAgentRouteService.resolve(identity,
				TenantAgentRouteService.UNIFIED_ASSISTANT);
		if (!requestedAgentId.equals(route.agent().getId())) {
			throw new DifyProxyException(HttpStatus.FORBIDDEN, "该智能体不是当前租户已发布的默认路由");
		}
		return route.agent();
	}

	/**
	 * 校验当前用户是否有权限使用该智能体
	 */
	private void checkAgentPermission(Long agentId) {
		AiAgentPermissionVO permission = aiAgentPermissionService.getPermission(agentId);
		if (permission == null || "all".equals(permission.getPermissionType())) {
			return;
		}
		MatrixUser user = SecurityUtils.getUser();
		if (user == null) {
			throw new DifyProxyException(HttpStatus.UNAUTHORIZED, "用户未登录");
		}
		// restricted 模式: 用户ID匹配 或 角色ID匹配 或 部门ID匹配 任一命中即放行
		List<Long> userIds = permission.getUserIds();
		if (userIds != null && userIds.contains(user.getId())) {
			return;
		}
		List<Long> roleIds = permission.getRoleIds();
		List<Long> userRoleIds = user.getRoleIds();
		if (roleIds != null && userRoleIds != null && userRoleIds.stream().anyMatch(roleIds::contains)) {
			return;
		}
		List<Long> deptIds = permission.getDeptIds();
		if (deptIds != null && !deptIds.isEmpty()) {
			List<Long> userDeptIds = user.getDeptIds();
			if (userDeptIds != null && userDeptIds.stream().anyMatch(deptIds::contains)) {
				return;
			}
			if (user.getDeptId() != null && deptIds.contains(user.getDeptId())) {
				return;
			}
		}
		throw new DifyProxyException(HttpStatus.FORBIDDEN, "无权使用该智能体");
	}

	/**
	 * 获取当前登录用户标识
	 */
	private String getCurrentUserId() {
		MatrixUser user = SecurityUtils.getUser();
		if (user != null && user.getId() != null) {
			return String.valueOf(user.getId());
		}
		throw new DifyProxyException(HttpStatus.UNAUTHORIZED, "用户未登录");
	}

	/**
	 * 获取传给 Dify 的 user 字段。所有智能体均加租户前缀，防止多租户共用或误配同一 Dify App 时会话串扰。
	 */
	private String getDifyUser(Long agentId) {
		String userId = getCurrentUserId();
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new DifyProxyException(HttpStatus.FORBIDDEN, "缺少有效租户身份");
		}
		return tenantId + "_" + userId;
	}

	/**
	 * 解析请求体并注入 user 字段（一次解析，避免重复）
	 */
	private ObjectNode parseAndEnhance(String body, Long agentId) {
		String user = getDifyUser(agentId);
		if (body != null && !body.isEmpty()) {
			try {
				JsonNode node = objectMapper.readTree(body);
				if (node instanceof ObjectNode objectNode) {
					objectNode.put("user", user);
					return objectNode;
				}
			}
			catch (IOException e) {
				throw new DifyProxyException(HttpStatus.BAD_REQUEST, "请求体 JSON 格式无效");
			}
		}
		ObjectNode fallback = objectMapper.createObjectNode();
		fallback.put("user", user);
		return fallback;
	}

	/**
	 * 序列化 JSON 节点
	 */
	private String toJson(ObjectNode node) {
		try {
			return objectMapper.writeValueAsString(node);
		}
		catch (IOException e) {
			throw new DifyProxyException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化请求体失败");
		}
	}

	/**
	 * 向 GET 查询参数注入 user 参数
	 */
	private void injectUserParam(Map<String, String> queryParams, Long agentId) {
		queryParams.putIfAbsent("user", getDifyUser(agentId));
	}

	/**
	 * 提取请求中的查询参数
	 */
	private Map<String, String> extractQueryParams(HttpServletRequest request) {
		Map<String, String> params = new HashMap<>();
		Enumeration<String> names = request.getParameterNames();
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			params.put(name, request.getParameter(name));
		}
		return params;
	}

	/**
	 * 规范化路径（{*path} 捕获的值已包含前导 /）
	 */
	private String normalizePath(String path) {
		if (path == null || path.isEmpty()) {
			return "/";
		}
		return path.startsWith("/") ? path : "/" + path;
	}

	/**
	 * 提取请求体中的 inputs 并序列化（用于落库 session_inputs）
	 * <p>
	 * 每次请求都以前端传入的 inputs 为准，支持对话中随时修改参数。
	 * @return session_inputs JSON 字符串，null 表示无 inputs
	 */
	private String resolveAndInjectInputs(ObjectNode bodyNode) {
		JsonNode inputsNode = bodyNode.get("inputs");
		if (inputsNode == null || inputsNode.isNull() || inputsNode.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(inputsNode);
		}
		catch (Exception e) {
			log.warn("[Dify Proxy] 序列化 inputs 失败", e);
			return null;
		}
	}

	/**
	 * 将 document 类型文件的提取文本追加到 query 中（agent-chat 模式下 Dify 无法自动解析文档）
	 */
	private void injectDocumentTexts(ObjectNode bodyNode) {
		if (!bodyNode.has("files") || !bodyNode.get("files").isArray()) {
			return;
		}
		int maxTotal = documentTextExtractor.getMaxTotalTextLength();
		StringBuilder docTexts = new StringBuilder();
		for (JsonNode fileRef : bodyNode.get("files")) {
			String type = fileRef.path("type").asText("");
			if (!"document".equals(type)) {
				continue;
			}
			String uploadFileId = fileRef.path("upload_file_id").asText("");
			if (uploadFileId.isEmpty()) {
				continue;
			}
			String text = documentTextExtractor.getCachedText(uploadFileId);
			if (text != null && !text.isEmpty()) {
				if (docTexts.length() + text.length() > maxTotal) {
					int remaining = maxTotal - docTexts.length();
					if (remaining > 200) {
						docTexts.append("\n\n--- 附件内容 ---\n")
							.append(text, 0, remaining)
							.append("\n...(总文本量已达上限，后续附件已省略)");
					}
					break;
				}
				docTexts.append("\n\n--- 附件内容 ---\n").append(text);
			}
		}
		if (docTexts.isEmpty()) {
			return;
		}
		if (bodyNode.has("query")) {
			bodyNode.put("query", bodyNode.path("query").asText("") + docTexts);
		}
		else if (bodyNode.has("inputs") && bodyNode.get("inputs").has("query")) {
			String inputsQuery = bodyNode.get("inputs").path("query").asText("") + docTexts;
			((ObjectNode) bodyNode.get("inputs")).put("query", inputsQuery);
		}
		else {
			bodyNode.put("query", docTexts.toString().strip());
		}
	}

	/**
	 * Dify 透传异常
	 */
	@lombok.Getter
	static class DifyProxyException extends RuntimeException {

		private final HttpStatus status;

		DifyProxyException(HttpStatus status, String message) {
			super(message);
			this.status = status;
		}

	}

}
