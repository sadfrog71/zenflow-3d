package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.skill.api.SkillResultStore;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.InformationSkillInvocationEntity;
import com.deepdimen.matrix.work.mapper.InformationSkillInvocationMapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Business snapshots and raw outputs only; publication remains in the information
 * service.
 */
@Component
@RequiredArgsConstructor
public class InformationSkillResultStore implements SkillResultStore {

	private final InformationSkillCollectionScope scope;

	private final InformationSkillInvocationMapper rows;

	private final ObjectMapper json;

	public String consumerType() {
		return "INFORMATION";
	}

	public boolean supportsConsumer(Consumer consumer) {
		return consumerType().equals(consumer.type()) && "default".equals(consumer.bindingKey())
				&& Set.of("ANALYZE", "REVIEW").contains(consumer.slotCode());
	}

	public InformationSkillInvocationEntity prepareAnalysis(InformationSkillInput input) {
		if (input.url() == null || input.url().isBlank())
			throw invalid();
		return prepare("ANALYZE", input.snapshot(json), null);
	}

	public InformationSkillInvocationEntity prepareReview(Long analysisId, ExecuteResult successfulAnalysis) {
		var parent = load(analysisId);
		if (!"ANALYZE".equals(parent.getSlotCode()) || !"RAW_RESULT".equals(parent.getStatus())
				|| successfulAnalysis == null || successfulAnalysis.status() != RunStatus.SUCCEEDED
				|| !Objects.equals(parent.getSkillRunId().toString(), successfulAnalysis.skillRunId())
				|| !ref(parent).equals(successfulAnalysis.resultRef())
				|| !parse(parent.getSkillOutputJson()).equals(parse(successfulAnalysis.resultJson()))
				|| !InformationSkillOutput.validate(json, "ANALYZE", parent.getSkillOutputJson())
					.path("eligible")
					.booleanValue())
			throw invalid();
		return prepare("REVIEW", parent.getEvidenceSnapshot(), parent);
	}

	private InformationSkillInvocationEntity prepare(String slot, String evidence,
			InformationSkillInvocationEntity parent) {
		var context = scope.require();
		var source = InformationSkillInput.restore(json, evidence);
		String document = digest(source.url());
		var row = rows.findStage(context.tenantId(), context.collectionRunId(), document, slot);
		if (row == null) {
			var proposed = new InformationSkillInvocationEntity();
			proposed.setId(IdWorker.getId());
			proposed.setTenantId(context.tenantId());
			proposed.setPolicyId(context.policyId());
			proposed.setCollectionRunId(context.collectionRunId());
			proposed.setOrgId(context.orgId());
			proposed.setActorId(context.actorId());
			proposed.setPolicySnapshot(context.policySnapshot());
			proposed.setSlotCode(slot);
			proposed.setDocumentDigest(document);
			proposed.setInstructionDigest(InformationSkillInstructions.source(slot).sha256());
			proposed.setEvidenceSnapshot(evidence);
			proposed.setParentInvocationId(parent == null ? null : parent.getId());
			proposed.setInputSnapshot(input(evidence, parent));
			proposed.setStatus("PREPARED");
			proposed.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
			try {
				if (rows.insert(proposed) != 1)
					throw new IllegalStateException("INFORMATION_INPUT_SAVE_FAILED");
				row = proposed;
			}
			catch (DuplicateKeyException race) {
				row = rows.findStage(context.tenantId(), context.collectionRunId(), document, slot);
			}
		}
		validate(row, scope.require());
		if (!parse(row.getEvidenceSnapshot()).equals(parse(evidence))
				|| !Objects.equals(row.getParentInvocationId(), parent == null ? null : parent.getId())
				|| !parse(row.getInputSnapshot()).equals(parse(input(evidence, parent))))
			throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");
		return row;
	}

	public String authorizeInput(Identity identity, Consumer consumer, String businessRunId, String inputJson) {
		var row = authorized(identity, consumer, businessRunId);
		if (!parse(row.getInputSnapshot()).equals(parse(inputJson)))
			throw invalid();
		return row.getInputSnapshot();
	}

	public String save(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String outputJson) {
		var row = authorized(identity, consumer, businessRunId);
		Long skillRun = positive(skillRunId);
		var output = InformationSkillOutput.validate(json, row.getSlotCode(), outputJson);
		rows.saveOnce(row.getTenantId(), row.getCollectionRunId(), row.getId(), row.getActorId(), skillRun,
				output.toString());
		row = authorized(identity, consumer, businessRunId);
		if (!"RAW_RESULT".equals(row.getStatus()) || !skillRun.equals(row.getSkillRunId())
				|| !output.equals(parse(row.getSkillOutputJson())))
			throw new IllegalArgumentException("INFORMATION_RESULT_CONFLICT");
		return ref(row);
	}

	public String read(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String resultRef) {
		var row = authorized(identity, consumer, businessRunId);
		if (!"RAW_RESULT".equals(row.getStatus()) || !positive(skillRunId).equals(row.getSkillRunId())
				|| !ref(row).equals(resultRef))
			throw invalid();
		return InformationSkillOutput.validate(json, row.getSlotCode(), row.getSkillOutputJson()).toString();
	}

	public void authorizeInvocation(Identity identity, Consumer consumer, String businessRunId) {
		authorized(identity, consumer, businessRunId);
	}

	private InformationSkillInvocationEntity authorized(Identity identity, Consumer consumer, String businessRunId) {
		if (!supportsConsumer(consumer))
			throw invalid();
		var row = load(positive(businessRunId));
		if (!row.getTenantId().toString().equals(identity.tenantId()) || !row.getActorId().equals(identity.actorId())
				|| !identity.actorId().equals(identity.subjectId())
				|| !row.getPolicyId().toString().equals(consumer.id()) || !row.getSlotCode().equals(consumer.slotCode())
				|| !("work-information:" + row.getId()).equals(identity.correlationId()))
			throw invalid();
		return row;
	}

	private InformationSkillInvocationEntity load(Long id) {
		if (id == null || id <= 0)
			throw invalid();
		var context = scope.require();
		var row = rows.findOwned(context.tenantId(), context.collectionRunId(), id);
		if (row == null || !id.equals(row.getId()))
			throw invalid();
		validate(row, context);
		return row;
	}

	private void validate(InformationSkillInvocationEntity row, InformationSkillCollectionScope.Context context) {
		if (row == null || !Objects.equals(row.getTenantId(), context.tenantId())
				|| !Objects.equals(row.getCollectionRunId(), context.collectionRunId())
				|| !Objects.equals(row.getPolicyId(), context.policyId())
				|| !Objects.equals(row.getOrgId(), context.orgId())
				|| !Objects.equals(row.getActorId(), context.actorId())
				|| !parse(context.policySnapshot()).equals(parse(row.getPolicySnapshot()))
				|| !("PREPARED".equals(row.getStatus()) || "RAW_RESULT".equals(row.getStatus()))
				|| !InformationSkillInstructions.source(row.getSlotCode()).sha256().equals(row.getInstructionDigest()))
			throw invalid();
		if ("RAW_RESULT".equals(row.getStatus())) {
			if (row.getSkillRunId() == null || row.getSkillRunId() <= 0)
				throw invalid();
			InformationSkillOutput.validate(json, row.getSlotCode(), row.getSkillOutputJson());
		}
		else if (row.getSkillRunId() != null || row.getSkillOutputJson() != null)
			throw invalid();
		var evidence = InformationSkillInput.restore(json, row.getEvidenceSnapshot());
		if (evidence.url() == null || !digest(evidence.url()).equals(row.getDocumentDigest()))
			throw invalid();
		InformationSkillInvocationEntity parent = null;
		if ("REVIEW".equals(row.getSlotCode())) {
			// Parent must be ANALYZE; validate that before recursive validation to
			// prevent cycles.
			parent = rows.findOwned(context.tenantId(), context.collectionRunId(), row.getParentInvocationId());
			if (parent == null || !"ANALYZE".equals(parent.getSlotCode())
					|| !Objects.equals(parent.getId(), row.getParentInvocationId())
					|| !"RAW_RESULT".equals(parent.getStatus()) || parent.getSkillRunId() == null)
				throw invalid();
			validate(parent, context);
			if (!parse(parent.getEvidenceSnapshot()).equals(parse(row.getEvidenceSnapshot()))
					|| !InformationSkillOutput.validate(json, "ANALYZE", parent.getSkillOutputJson())
						.path("eligible")
						.booleanValue())
				throw invalid();
		}
		else if (!"ANALYZE".equals(row.getSlotCode()) || row.getParentInvocationId() != null)
			throw invalid();
		if (!parse(row.getInputSnapshot()).equals(parse(input(row.getEvidenceSnapshot(), parent))))
			throw invalid();
	}

	private String input(String evidence, InformationSkillInvocationEntity parent) {
		ObjectNode input = (ObjectNode) parse(evidence);
		if (parent != null)
			input.set("analysis", InformationSkillOutput.validate(json, "ANALYZE", parent.getSkillOutputJson()));
		return input.toString();
	}

	private JsonNode parse(String value) {
		try {
			JsonNode result = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(value);
			if (result == null || !result.isObject())
				throw invalid();
			return result;
		}
		catch (Exception failure) {
			throw invalid();
		}
	}

	private static Long positive(String id) {
		try {
			long value = Long.parseLong(id);
			if (value <= 0 || !Long.toString(value).equals(id))
				throw invalid();
			return value;
		}
		catch (RuntimeException ex) {
			throw invalid();
		}
	}

	private static String ref(InformationSkillInvocationEntity row) {
		return "information-skill:" + row.getId() + ":" + row.getSkillRunId();
	}

	private static String digest(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception failure) {
			throw invalid();
		}
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("INFORMATION_INPUT_NOT_AUTHORIZED");
	}

}
