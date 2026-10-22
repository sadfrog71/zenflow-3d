package com.deepdimen.matrix.discover.skill;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.discover.entity.AiSkillRunEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillRunMapper;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence boundary only; callers authorize and validate before reserving or claiming.
 */
@Component
@RequiredArgsConstructor
public class ManagedSkillRunIndex {

	private final AiSkillRunMapper runs;

	private final ObjectMapper json;

	public AiSkillRunEntity reserve(ExecuteRequest request, SkillResolutionTokenStore.StoredResolution resolved,
			String effectiveInputJson) {
		if (!request.identity().equals(resolved.identity()) || !request.consumer().equals(resolved.consumer())) {
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
		if (request.idempotencyKey().length() > 128)
			throw new IllegalArgumentException("INPUT_INVALID");
		var identity = resolved.identity();
		var consumer = resolved.consumer();
		var snapshot = resolved.snapshot();
		AiSkillRunEntity proposed = new AiSkillRunEntity();
		proposed.setId(IdWorker.getId());
		proposed.setTenantId(identity.tenantId());
		proposed.setActorId(identity.actorId());
		proposed.setSubjectId(identity.subjectId());
		proposed.setSubjectType(identity.subjectType());
		proposed.setActorType(identity.actorType());
		proposed.setConsumerType(consumer.type());
		proposed.setConsumerId(consumer.id());
		proposed.setSlotCode(consumer.slotCode());
		proposed.setBindingKey(consumer.bindingKey());
		proposed.setBindingRevisionId(Long.valueOf(snapshot.bindingRevisionId()));
		proposed.setReleaseId(Long.valueOf(snapshot.releaseId()));
		proposed.setBusinessRunId(resolved.businessRunId());
		proposed.setCorrelationId(identity.correlationId());
		proposed.setParentRunId(request.parentRunId());
		proposed.setIdempotencyKey(request.idempotencyKey());
		proposed.setIdempotencyScopeDigest(digest(
				json.valueToTree(List.of(identity.tenantId(), identity.actorId(), identity.subjectId(), identity.actorType(),identity.subjectType(),consumer.type(),
						consumer.id(), consumer.slotCode(), consumer.bindingKey(), request.idempotencyKey()))));
		try {
			JsonNode input = json.readTree(effectiveInputJson);
			if (input == null)
				throw new IllegalArgumentException("INPUT_INVALID");
			proposed.setInputDigest(digest(input));
			var material = json.createObjectNode();
			material.put("inputDigest", proposed.getInputDigest());
			material.put("releaseId", snapshot.releaseId());
			material.put("releaseDigest", snapshot.releaseDigest());
			material.put("bindingRevisionId", snapshot.bindingRevisionId());
			material.put("businessRunId", resolved.businessRunId());
			material.put("parentRunId", request.parentRunId());
			material.put("authorizedInputRef", request.authorizedInputRef());
			material.set("parameters", json.readTree(resolved.parametersJson()));
			proposed.setRequestDigest(digest(material));
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("INPUT_INVALID", ex);
		}
		runs.reserve(proposed);
		var existing = runs.findByScope(proposed.getTenantId(), proposed.getIdempotencyScopeDigest());
		if (existing == null)
			throw new IllegalStateException("运行索引保存失败");
		if (!proposed.getRequestDigest().equals(existing.getRequestDigest()))
			throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");
		return existing;
	}

	/**
	 * A false result means no execution permission was acquired; never run the external
	 * action.
	 */
	public boolean claim(AiSkillRunEntity run) {
		return "QUEUED".equals(run.getStatus()) && runs.claim(run.getTenantId(), run.getId(), run.getRevision()) == 1;
	}

	public void finish(AiSkillRunEntity claimed, RunStatus status, String resultRef, ErrorCode error,
			String executorVersion, String usageJson) {
		if (!java.util.Set
			.of(RunStatus.SUCCEEDED, RunStatus.DEGRADED, RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.UNKNOWN)
			.contains(status)) {
			throw new IllegalArgumentException("运行结果必须为终态");
		}
		// claim increments the database version; the caller holds the pre-claim snapshot.
		if (runs.finish(claimed.getTenantId(), claimed.getId(), claimed.getRevision() + 1, status.name(), resultRef,
				error == null ? null : error.name(), executorVersion, usageJson) != 1) {
			throw new IllegalStateException("运行终态保存冲突，禁止重新执行外部动作");
		}
	}

	private String digest(JsonNode value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(sorted(value))));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("运行摘要生成失败", ex);
		}
	}

	private JsonNode sorted(JsonNode node) {
		if (node.isObject()) {
			var result = json.createObjectNode();
			var fields = new TreeMap<String, JsonNode>();
			node.fields().forEachRemaining(f -> fields.put(f.getKey(), sorted(f.getValue())));
			fields.forEach(result::set);
			return result;
		}
		if (node.isArray()) {
			var result = json.createArrayNode();
			node.forEach(v -> result.add(sorted(v)));
			return result;
		}
		return node;
	}

}
