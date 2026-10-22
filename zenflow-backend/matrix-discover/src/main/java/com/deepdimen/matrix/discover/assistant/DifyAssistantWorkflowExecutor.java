package com.deepdimen.matrix.discover.assistant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStep;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.UsageSource;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.run.AssistantJsonSchemaValidator;
import com.deepdimen.matrix.assistant.workflow.AssistantWorkflowExecutor;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityReleaseEntity;
import com.deepdimen.matrix.discover.mapper.AiAssistantWorkflowCapabilityReleaseMapper;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/** Reuses the existing Dify Service client while keeping credentials server-side. */
@Component
public class DifyAssistantWorkflowExecutor implements AssistantWorkflowExecutor {

	private static final WorkflowExecutionControl NOOP_CONTROL = new WorkflowExecutionControl() {
		@Override
		public void externalIdentifiersAvailable(String externalTaskId, String workflowRunId) {
		}

		@Override
		public boolean cancellationRequested() {
			return false;
		}
	};

	private final AiAssistantWorkflowCapabilityReleaseMapper releaseMapper;

	private final AiAgentService agentService;

	private final AiAgentPermissionService permissionService;

	private final DifyAppApi difyAppApi;

	private final DifyServiceClient difyServiceClient;

	private final ObjectMapper objectMapper;

	private final AssistantJsonSchemaValidator schemaValidator;

	public DifyAssistantWorkflowExecutor(AiAssistantWorkflowCapabilityReleaseMapper releaseMapper,
			AiAgentService agentService, AiAgentPermissionService permissionService, DifyAppApi difyAppApi,
			DifyServiceClient difyServiceClient, ObjectMapper objectMapper,
			AssistantJsonSchemaValidator schemaValidator) {
		this.releaseMapper = releaseMapper;
		this.agentService = agentService;
		this.permissionService = permissionService;
		this.difyAppApi = difyAppApi;
		this.difyServiceClient = difyServiceClient;
		this.objectMapper = objectMapper;
		this.schemaValidator = schemaValidator;
	}

	@Override
	public WorkflowExecutionResult execute(IdentityEnvelope identity, String runId, ExecutionStep step,
			List<AnalysisArtifact> dependencyArtifacts) {
		return execute(identity, runId, step, dependencyArtifacts, NOOP_CONTROL);
	}

	@Override
	public WorkflowExecutionResult execute(IdentityEnvelope identity, String runId, ExecutionStep step,
			List<AnalysisArtifact> dependencyArtifacts, WorkflowExecutionControl control) {
		if (identity == null || !StringUtils.hasText(identity.tenantId())) {
			throw new IllegalArgumentException("WORKFLOW_IDENTITY_REQUIRED");
		}
		WorkflowExecutionControl effectiveControl = control == null ? NOOP_CONTROL : control;
		Long previousTenant = TenantContextHolder.getTenantId();
		try {
			TenantContextHolder.setTenantId(Long.valueOf(identity.tenantId()));
			return executeWithinTenant(identity, runId, step, dependencyArtifacts, effectiveControl);
		}
		finally {
			if (previousTenant == null) {
				TenantContextHolder.clear();
			}
			else {
				TenantContextHolder.setTenantId(previousTenant);
			}
		}
	}

	private WorkflowExecutionResult executeWithinTenant(IdentityEnvelope identity, String runId, ExecutionStep step,
			List<AnalysisArtifact> dependencyArtifacts, WorkflowExecutionControl control) {
		if (isMarketplaceStep(step)) {
			return executeMarketplaceWorkflow(identity, runId, step, dependencyArtifacts, control);
		}
		AiAssistantWorkflowCapabilityReleaseEntity release = step.targetReleaseId() == null ? null
				: releaseMapper.selectById(step.targetReleaseId());
		if (!eligible(identity, step, release)) {
			throw new IllegalArgumentException("WORKFLOW_RELEASE_NOT_ALLOWED");
		}
		AiAgentEntity agent = agentService.getById(release.getAgentId());
		if (agent == null || !"dify".equals(agent.getAgentType()) || !"workflow".equals(agent.getDifyMode())
				|| !StringUtils.hasText(agent.getDifyApiKey())
				|| !release.getDifyAppId().equals(agent.getDifyAppId())) {
			throw new IllegalArgumentException("DIFY_WORKFLOW_NOT_PUBLISHED");
		}
		String definition = exportedDefinition(difyAppApi.exportApp(release.getDifyAppId()));
		if (!release.getWorkflowDefinitionDigest().equals(sha256(definition))) {
			throw new IllegalStateException("DIFY_WORKFLOW_RELEASE_DRIFTED");
		}
		Set<String> allowedCitationKeys = dependencyArtifacts.stream()
			.flatMap(item -> item.citationKeys().stream())
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		assertEgressClassification(release, dependencyArtifacts);
		Map<String, Object> availableInputs = new LinkedHashMap<>();
		Object query = step.inputProjection().getOrDefault("query", "");
		availableInputs.put("matrix_query", query);
		// Existing Dify workflows commonly expose their user question as `query`.
		// Keep the governed matrix_query contract while supporting that native alias.
		availableInputs.put("query", query);
		availableInputs.put("matrix_artifacts_json",
				write(dependencyArtifacts.stream().map(this::minimumArtifact).toList()));
		availableInputs.put("matrix_citation_keys", List.copyOf(allowedCitationKeys));
		availableInputs.put("matrix_run_id", runId);
		JsonNode inputSchema = readSchema(release.getInputSchemaJson(), "DIFY_INPUT_SCHEMA_INVALID");
		Map<String, Object> inputs = projectInputs(inputSchema, availableInputs);
		validateSchema(inputSchema, objectMapper.valueToTree(inputs), "DIFY_INPUT_CONTRACT_MISMATCH");
		String pseudonymousUser = pseudonymousUser(identity);
		Map<String, Object> body = Map.of("inputs", inputs, "response_mode", "streaming", "user", pseudonymousUser);
		int timeoutMs = release.getTimeoutMs() == null ? 30000 : release.getTimeoutMs();
		StreamingWorkflowResponse response = runStreamingWorkflow(agent.getDifyApiKey(), body, pseudonymousUser,
				Math.max(1000, Math.min(timeoutMs, 90000)), control);
		JsonNode outputs = response.outputs();
		if (!outputs.isObject()) {
			throw new IllegalStateException("DIFY_OUTPUT_SCHEMA_MISMATCH");
		}
		validateSchema(readSchema(release.getOutputSchemaJson(), "DIFY_OUTPUT_SCHEMA_INVALID"), outputs,
				"DIFY_OUTPUT_SCHEMA_MISMATCH");
		Map<String, Object> payload = objectMapper.convertValue(outputs, Map.class);
		List<String> reportedCitations = citationKeys(outputs).stream().distinct().toList();
		if (!allowedCitationKeys.containsAll(reportedCitations)) {
			throw new IllegalStateException("CLAIM_PROVENANCE_INVALID");
		}
		List<String> citations = List.copyOf(reportedCitations);
		return new WorkflowExecutionResult(payload, citations, response.taskId(), response.workflowRunId(),
				UsageSource.DIFY_RESPONSE);
	}

	private WorkflowExecutionResult executeMarketplaceWorkflow(IdentityEnvelope identity, String runId,
			ExecutionStep step, List<AnalysisArtifact> dependencyArtifacts, WorkflowExecutionControl control) {
		Long agentId = step.targetReleaseId();
		AiAgentEntity agent = agentId == null ? null : agentService.getById(agentId);
		if (!marketplaceEligible(identity, step, agent)) {
			throw new IllegalArgumentException("MARKET_WORKFLOW_NOT_ALLOWED");
		}
		Set<String> allowedCitationKeys = dependencyArtifacts.stream()
			.flatMap(item -> item.citationKeys().stream())
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		boolean workflowMode = "workflow".equals(agent.getDifyMode());
		Map<String, Object> inputs = marketplaceInputs(agent.getDifyApiKey(), step, runId, dependencyArtifacts,
				allowedCitationKeys, workflowMode || "completion".equals(agent.getDifyMode()));
		String pseudonymousUser = pseudonymousUser(identity);
		Map<String, Object> payload;
		List<String> reportedCitations;
		String taskId;
		String executionId;
		if (workflowMode) {
			Map<String, Object> body = Map.of("inputs", inputs, "response_mode", "streaming", "user",
					pseudonymousUser);
			StreamingWorkflowResponse response = runStreamingWorkflow(agent.getDifyApiKey(), body, pseudonymousUser,
					60000, control);
			JsonNode outputs = response.outputs();
			if (!outputs.isObject()) {
				throw new IllegalStateException("DIFY_OUTPUT_SCHEMA_MISMATCH");
			}
			payload = objectMapper.convertValue(outputs, Map.class);
			reportedCitations = citationKeys(outputs).stream().distinct().toList();
			taskId = response.taskId();
			executionId = response.workflowRunId();
		}
		else {
			String query = String.valueOf(step.inputProjection().getOrDefault("query", ""));
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("inputs", inputs);
			body.put("response_mode", "streaming");
			body.put("user", pseudonymousUser);
			String path;
			String stopPathPrefix;
			if ("completion".equals(agent.getDifyMode())) {
				path = "/completion-messages";
				stopPathPrefix = "/completion-messages/";
			}
			else {
				path = "/chat-messages";
				stopPathPrefix = "/chat-messages/";
				body.put("query", query);
			}
			StreamingTextResponse response = runStreamingTextApplication(agent.getDifyApiKey(), path, stopPathPrefix,
					body, pseudonymousUser, 60000, control);
			payload = new LinkedHashMap<>();
			payload.put("text", response.text());
			reportedCitations = List.of();
			taskId = response.taskId();
			executionId = response.executionId();
		}
		if (!allowedCitationKeys.containsAll(reportedCitations)) {
			throw new IllegalStateException("CLAIM_PROVENANCE_INVALID");
		}
		payload.put("_matrixMarketplaceAgentId", agent.getId());
		payload.put("_matrixDifyAppId", agent.getDifyAppId());
		payload.put("_matrixDifyMode", agent.getDifyMode());
		return new WorkflowExecutionResult(payload, reportedCitations, taskId, executionId,
				UsageSource.DIFY_RESPONSE);
	}

	private boolean isMarketplaceStep(ExecutionStep step) {
		return step != null && step.targetReleaseId() != null
				&& step.targetId().equals(MatrixAssistantWorkflowCatalog.MARKET_CAPABILITY_PREFIX
						+ step.targetReleaseId())
				&& MatrixAssistantWorkflowCatalog.MARKET_OUTPUT_SCHEMA_ID.equals(step.outputSchemaId());
	}

	private boolean marketplaceEligible(IdentityEnvelope identity, ExecutionStep step, AiAgentEntity agent) {
		return agent != null && identity.tenantId().equals(agent.getTenantId())
				&& "published".equals(agent.getStatus()) && "0".equals(agent.getDelFlag())
				&& "dify".equals(agent.getAgentType())
				&& MatrixAssistantWorkflowCatalog.MARKET_DIFY_MODES.contains(agent.getDifyMode())
				&& StringUtils.hasText(agent.getDifyApiKey()) && StringUtils.hasText(agent.getDifyAppId())
				&& permissionService.isAccessible(agent.getId(), identity) && isMarketplaceStep(step);
	}

	private Map<String, Object> marketplaceInputs(String apiKey, ExecutionStep step, String runId,
			List<AnalysisArtifact> dependencyArtifacts, Set<String> allowedCitationKeys, boolean queryInputRequired) {
		Object query = step.inputProjection().getOrDefault("query", "");
		Map<String, Object> known = new LinkedHashMap<>();
		known.put("query", query);
		known.put("question", query);
		known.put("input", query);
		known.put("matrix_query", query);
		known.put("matrix_artifacts_json", write(dependencyArtifacts.stream().map(this::minimumArtifact).toList()));
		known.put("matrix_citation_keys", List.copyOf(allowedCitationKeys));
		known.put("matrix_run_id", runId);
		Set<String> variables = marketplaceInputVariables(apiKey);
		if (variables.isEmpty()) {
			return queryInputRequired ? Map.of("query", query) : Map.of();
		}
		Map<String, Object> inputs = new LinkedHashMap<>();
		variables.stream().filter(known::containsKey).forEach(name -> inputs.put(name, known.get(name)));
		if (queryInputRequired
				&& inputs.keySet().stream().noneMatch(Set.of("query", "question", "input", "matrix_query")::contains)) {
			if (variables.size() == 1) {
				inputs.put(variables.iterator().next(), query);
			}
			else {
				throw new IllegalStateException("MARKET_WORKFLOW_QUERY_INPUT_NOT_FOUND");
			}
		}
		return inputs;
	}

	private StreamingTextResponse runStreamingTextApplication(String apiKey, String path, String stopPathPrefix,
			Map<String, Object> body, String user, int timeoutMs, WorkflowExecutionControl control) {
		if (control.cancellationRequested()) {
			throw new IllegalStateException("WORKFLOW_CANCELLED");
		}
		AtomicReference<String> taskId = new AtomicReference<>();
		AtomicReference<String> executionId = new AtomicReference<>();
		StringBuilder answer = new StringBuilder();
		Flux<Long> cancellationSignal = Flux.interval(Duration.ofMillis(100))
			.filter(ignored -> control.cancellationRequested())
			.take(1);
		boolean terminal = false;
		try {
			for (ServerSentEvent<String> event : difyServiceClient.postStream(path, apiKey, body)
				.timeout(Duration.ofMillis(timeoutMs))
				.takeUntilOther(cancellationSignal)
				.toIterable()) {
				JsonNode data = parseEventData(event.data());
				String eventType = StringUtils.hasText(event.event()) ? event.event() : data.path("event").asText("");
				String nextTaskId = firstText(data, "task_id");
				String nextExecutionId = firstText(data, "conversation_id", "message_id", "id");
				observeExternalIdentifiers(taskId, executionId, nextTaskId, nextExecutionId, control);
				if ("error".equals(eventType)) {
					throw new IllegalStateException("DIFY_APP_FAILED");
				}
				if (Set.of("message", "agent_message").contains(eventType)) {
					answer.append(data.path("answer").asText(""));
				}
				else if ("text_chunk".equals(eventType)) {
					answer.append(data.path("data").path("text").asText(data.path("text").asText("")));
				}
				else if ("message_replace".equals(eventType)) {
					answer.setLength(0);
					answer.append(data.path("answer").asText(""));
				}
				if (Set.of("message_end", "agent_message_end").contains(eventType)) {
					terminal = true;
				}
			}
		}
		catch (RuntimeException ex) {
			if (control.cancellationRequested()) {
				throw cancellationResult(apiKey, user, taskId.get(), stopPathPrefix);
			}
			if (causedByTimeout(ex)) {
				throw new IllegalStateException("DIFY_WORKFLOW_TIMEOUT", ex);
			}
			throw ex;
		}
		if (control.cancellationRequested() && !terminal) {
			throw cancellationResult(apiKey, user, taskId.get(), stopPathPrefix);
		}
		if (!terminal || !StringUtils.hasText(answer)) {
			throw new IllegalStateException("DIFY_APP_STREAM_INCOMPLETE");
		}
		return new StreamingTextResponse(answer.toString(), taskId.get(), executionId.get());
	}

	private Set<String> marketplaceInputVariables(String apiKey) {
		try {
			JsonNode parameters = difyServiceClient.getParameters(apiKey).block(Duration.ofSeconds(5));
			Set<String> variables = new LinkedHashSet<>();
			JsonNode forms = parameters == null ? null : parameters.path("user_input_form");
			if (forms == null || !forms.isArray()) {
				return variables;
			}
			for (JsonNode form : forms) {
				String variable = form.path("variable").asText(null);
				if (StringUtils.hasText(variable)) {
					variables.add(variable);
					continue;
				}
				form.elements().forEachRemaining(value -> {
					String nested = value.path("variable").asText(null);
					if (StringUtils.hasText(nested)) {
						variables.add(nested);
					}
				});
			}
			return variables;
		}
		catch (RuntimeException ex) {
			return Set.of();
		}
	}

	private StreamingWorkflowResponse runStreamingWorkflow(String apiKey, Map<String, Object> body, String user,
			int timeoutMs, WorkflowExecutionControl control) {
		if (control.cancellationRequested()) {
			throw new IllegalStateException("WORKFLOW_CANCELLED");
		}
		AtomicReference<String> taskId = new AtomicReference<>();
		AtomicReference<String> workflowRunId = new AtomicReference<>();
		AtomicReference<JsonNode> outputs = new AtomicReference<>();
		Flux<Long> cancellationSignal = Flux.interval(Duration.ofMillis(100))
			.filter(ignored -> control.cancellationRequested())
			.take(1);
		try {
			for (ServerSentEvent<String> event : difyServiceClient.postStream("/workflows/run", apiKey, body)
				.timeout(Duration.ofMillis(timeoutMs))
				.takeUntilOther(cancellationSignal)
				.toIterable()) {
				JsonNode data = parseEventData(event.data());
				String eventType = StringUtils.hasText(event.event()) ? event.event() : data.path("event").asText("");
				String nextTaskId = firstText(data, "task_id");
				String nextWorkflowRunId = firstText(data, "workflow_run_id");
				if (!StringUtils.hasText(nextWorkflowRunId) && "workflow_started".equals(eventType)) {
					nextWorkflowRunId = firstText(data.path("data"), "id", "workflow_run_id");
				}
				observeExternalIdentifiers(taskId, workflowRunId, nextTaskId, nextWorkflowRunId, control);
				if ("error".equals(eventType)) {
					throw new IllegalStateException("DIFY_WORKFLOW_FAILED");
				}
				if ("workflow_finished".equals(eventType)) {
					JsonNode finished = data.path("data");
					String status = finished.path("status").asText("succeeded");
					if (!"succeeded".equalsIgnoreCase(status)) {
						throw new IllegalStateException("DIFY_WORKFLOW_FAILED");
					}
					JsonNode result = finished.path("outputs");
					if (result.isObject()) {
						outputs.set(result.deepCopy());
					}
				}
			}
		}
		catch (RuntimeException ex) {
			if (control.cancellationRequested()) {
				throw cancellationResult(apiKey, user, taskId.get());
			}
			if (causedByTimeout(ex)) {
				throw new IllegalStateException("DIFY_WORKFLOW_TIMEOUT", ex);
			}
			throw ex;
		}
		if (control.cancellationRequested() && outputs.get() == null) {
			throw cancellationResult(apiKey, user, taskId.get());
		}
		if (outputs.get() == null) {
			throw new IllegalStateException("DIFY_WORKFLOW_STREAM_INCOMPLETE");
		}
		return new StreamingWorkflowResponse(outputs.get(), taskId.get(), workflowRunId.get());
	}

	private void observeExternalIdentifiers(AtomicReference<String> taskId, AtomicReference<String> workflowRunId,
			String nextTaskId, String nextWorkflowRunId, WorkflowExecutionControl control) {
		boolean changed = false;
		if (StringUtils.hasText(nextTaskId) && !nextTaskId.equals(taskId.get())) {
			taskId.set(nextTaskId);
			changed = true;
		}
		if (StringUtils.hasText(nextWorkflowRunId) && !nextWorkflowRunId.equals(workflowRunId.get())) {
			workflowRunId.set(nextWorkflowRunId);
			changed = true;
		}
		if (changed) {
			control.externalIdentifiersAvailable(taskId.get(), workflowRunId.get());
		}
	}

	private RuntimeException cancellationResult(String apiKey, String user, String taskId) {
		return cancellationResult(apiKey, user, taskId, "/workflows/tasks/");
	}

	private RuntimeException cancellationResult(String apiKey, String user, String taskId, String stopPathPrefix) {
		if (!StringUtils.hasText(taskId) || !taskId.matches("[A-Za-z0-9_-]{1,128}")) {
			return new IllegalStateException("WORKFLOW_CANCEL_STATUS_UNKNOWN");
		}
		try {
			JsonNode response = difyServiceClient
				.post(stopPathPrefix + taskId + "/stop", apiKey, Map.of("user", user))
				.block(Duration.ofSeconds(5));
			if (response != null && ("success".equalsIgnoreCase(response.path("result").asText())
					|| response.path("success").asBoolean(false))) {
				return new IllegalStateException("WORKFLOW_CANCELLED");
			}
		}
		catch (RuntimeException ignored) {
			// The durable caller must mark this step UNKNOWN; never retry the workflow
			// POST.
		}
		return new IllegalStateException("WORKFLOW_CANCEL_STATUS_UNKNOWN");
	}

	private JsonNode parseEventData(String value) {
		if (!StringUtils.hasText(value)) {
			return objectMapper.createObjectNode();
		}
		try {
			return objectMapper.readTree(value);
		}
		catch (Exception ex) {
			throw new IllegalStateException("DIFY_WORKFLOW_STREAM_INVALID", ex);
		}
	}

	private String firstText(JsonNode node, String... fields) {
		for (String field : fields) {
			JsonNode value = node.path(field);
			if (value.isValueNode() && StringUtils.hasText(value.asText())) {
				return value.asText();
			}
		}
		return null;
	}

	private boolean causedByTimeout(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof TimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private record StreamingWorkflowResponse(JsonNode outputs, String taskId, String workflowRunId) {
	}

	private record StreamingTextResponse(String text, String taskId, String executionId) {
	}

	private Map<String, Object> projectInputs(JsonNode schema, Map<String, Object> available) {
		JsonNode properties = schema.path("properties");
		if (!properties.isObject()) {
			return Map.of();
		}
		Map<String, Object> projected = new LinkedHashMap<>();
		properties.fieldNames().forEachRemaining(name -> {
			if (available.containsKey(name)) {
				projected.put(name, available.get(name));
			}
		});
		return projected;
	}

	private Map<String, Object> minimumArtifact(AnalysisArtifact artifact) {
		Map<String, Object> projected = new LinkedHashMap<>();
		projected.put("artifactId", artifact.artifactId());
		projected.put("kind", artifact.kind().name());
		projected.put("payload", artifact.payload());
		projected.put("citationKeys", artifact.citationKeys());
		projected.put("artifactDigest", artifact.artifactDigest());
		return projected;
	}

	private void assertEgressClassification(AiAssistantWorkflowCapabilityReleaseEntity release,
			List<AnalysisArtifact> artifacts) {
		DataClassification allowed;
		try {
			allowed = DataClassification.valueOf(release.getDataClassification());
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("DIFY_EGRESS_POLICY_INVALID", ex);
		}
		for (AnalysisArtifact artifact : artifacts) {
			DataClassification actual;
			try {
				actual = DataClassification
					.valueOf(String.valueOf(artifact.payload().getOrDefault("dataClassification", "SENSITIVE")));
			}
			catch (RuntimeException ex) {
				actual = DataClassification.SENSITIVE;
			}
			if (!allowed.allows(actual)) {
				throw new IllegalStateException("DIFY_EGRESS_POLICY_DENIED");
			}
		}
	}

	private JsonNode readSchema(String value, String errorCode) {
		try {
			JsonNode schema = objectMapper.readTree(value);
			if (schema == null || !schema.isObject()) {
				throw new IllegalStateException(errorCode);
			}
			return schema;
		}
		catch (Exception ex) {
			throw new IllegalStateException(errorCode, ex);
		}
	}

	private void validateSchema(JsonNode schema, JsonNode value, String errorCode) {
		try {
			schemaValidator.validate(schema, value);
		}
		catch (AssistantJsonSchemaValidator.SchemaViolationException ex) {
			throw new IllegalStateException(errorCode + ":" + ex.getMessage(), ex);
		}
	}

	private boolean eligible(IdentityEnvelope identity, ExecutionStep step,
			AiAssistantWorkflowCapabilityReleaseEntity release) {
		return identity != null && release != null && identity.tenantId().equals(release.getTenantId())
				&& "ACTIVE".equals(release.getReleaseStatus())
				&& Integer.valueOf(1).equals(release.getPolicyAuditPassed())
				&& "READ_ONLY".equals(release.getSideEffectClass()) && release.getRevokedAt() == null
				&& release.getCapabilityCode().equals(step.targetId())
				&& release.getOutputSchemaId().equals(step.outputSchemaId())
				&& permissionService.isAccessible(release.getAgentId(), identity);
	}

	private List<String> citationKeys(JsonNode node) {
		List<String> result = new ArrayList<>();
		collectCitationKeys(node, result);
		return result;
	}

	private void collectCitationKeys(JsonNode node, List<String> result) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			node.fields().forEachRemaining(field -> {
				if (("citationKey".equals(field.getKey()) || "citationKeys".equals(field.getKey()))) {
					if (field.getValue().isTextual()) {
						result.add(field.getValue().asText());
					}
					else if (field.getValue().isArray()) {
						field.getValue().forEach(value -> {
							if (value.isTextual()) {
								result.add(value.asText());
							}
						});
					}
				}
				collectCitationKeys(field.getValue(), result);
			});
		}
		else if (node.isArray()) {
			node.forEach(value -> collectCitationKeys(value, result));
		}
	}

	private String exportedDefinition(JsonNode exported) {
		if (exported == null || exported.isNull()) {
			throw new IllegalStateException("DIFY_PUBLISHED_WORKFLOW_NOT_INSPECTABLE");
		}
		if (exported.isTextual()) {
			return exported.asText();
		}
		for (String field : List.of("data", "yaml_content", "content")) {
			if (exported.path(field).isTextual()) {
				return exported.path(field).asText();
			}
		}
		throw new IllegalStateException("DIFY_PUBLISHED_WORKFLOW_NOT_INSPECTABLE");
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (Exception ex) {
			throw new IllegalStateException("DIFY_INPUT_CONTRACT_MISMATCH", ex);
		}
	}

	private String pseudonymousUser(IdentityEnvelope identity) {
		return "matrix-" + sha256(identity.tenantId() + ":" + identity.userId()).substring(0, 24);
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
