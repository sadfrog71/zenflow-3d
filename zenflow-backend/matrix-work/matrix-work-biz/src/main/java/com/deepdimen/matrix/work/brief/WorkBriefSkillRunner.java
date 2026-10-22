package com.deepdimen.matrix.work.brief;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter.GenerationRequest;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter.GenerationResult;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkBriefSkillRunner {

	public static final String SKILL_CODE = "work-summary";

	private final AssistantGenerationAdapter generationAdapter;

	private final DeterministicWorkSummarySkill deterministicFallback;

	private final ObjectMapper objectMapper;

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private ManagedWorkBriefRunner managedRunner;

	public Execution execute(WorkBriefIdentity identity, WorkBriefPolicyEntity policy, WorkBriefSnapshot snapshot,
			String businessRunId) {
		if ("MANAGED".equals(policy.getSkillManagementMode())) {
			if (managedRunner == null)
				throw new IllegalStateException("EXECUTOR_UNAVAILABLE");
			return managedRunner.execute(identity, policy, snapshot, businessRunId);
		}
		return execute(identity, policy, snapshot);
	}

	public Execution execute(WorkBriefIdentity identity, WorkBriefPolicyEntity policy, WorkBriefSnapshot snapshot) {
		if ("MANAGED".equals(policy.getSkillManagementMode()))
			throw new IllegalStateException("托管简报需要业务运行编号");
		String executionId = UUID.randomUUID().toString();
		AssistantExecutionTrace trace = null;
		try {
			AssistantContextPack contextPack = contextPack(identity, snapshot);
			GenerationResult result = generationAdapter.generate(new GenerationRequest(executionId,
					"work-brief:" + identity.tenantId() + ":" + identity.ownerUserId(), executionId,
					identity(identity, executionId), "请使用 work-summary Skill 将当前权限过滤后的工作快照生成严格 JSON 工作简报。", contextPack,
					List.of(SKILL_CODE), List.of(), "WORK_BRIEF"));
			trace = result.trace();
			if (result.available()) {
				WorkBriefDraft generated = parseDraft(result.content());
				WorkBriefDraft deterministic = deterministicFallback.execute(snapshot);
				return new Execution(withAuthoritativeMetrics(generated, deterministic.metrics()), trace, false);
			}
			if (!Boolean.TRUE.equals(policy.getAllowDeterministicFallback())) {
				throw new IllegalStateException("WORK_SUMMARY_SKILL_UNAVAILABLE");
			}
		}
		catch (RuntimeException ex) {
			if (!Boolean.TRUE.equals(policy.getAllowDeterministicFallback())) {
				throw ex;
			}
			log.warn("工作简报 Skill 不可用，使用租户策略允许的确定性降级，tenantId={}, userId={}: {}", identity.tenantId(),
					identity.ownerUserId(), ex.getMessage());
		}
		return new Execution(deterministicFallback.execute(snapshot), trace, true);
	}

	public Execution deterministic(WorkBriefSnapshot snapshot, AssistantExecutionTrace trace) {
		return new Execution(deterministicFallback.execute(snapshot), trace, true);
	}

	private WorkBriefDraft withAuthoritativeMetrics(WorkBriefDraft generated, Map<String, Integer> metrics) {
		return new WorkBriefDraft(generated.title(), generated.executiveSummary(), generated.continuityNote(), metrics,
				generated.focusItems(), generated.scheduleItems(), generated.informationItems(),
				generated.messageItems(), generated.emailItems(), generated.riskItems(), generated.citations());
	}

	private WorkBriefDraft parseDraft(String content) {
		try {
			String json = content == null ? "" : content.trim();
			int start = json.indexOf('{');
			int end = json.lastIndexOf('}');
			if (start < 0 || end <= start) {
				throw new IllegalArgumentException("WORK_SUMMARY_SKILL_OUTPUT_INVALID");
			}
			return objectMapper.readValue(json.substring(start, end + 1), WorkBriefDraft.class);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("WORK_SUMMARY_SKILL_OUTPUT_INVALID", ex);
		}
	}

	private IdentityEnvelope identity(WorkBriefIdentity identity, String correlationId) {
		return new IdentityEnvelope(identity.tenantId().toString(), identity.ownerUserId().toString(),
				identity.orgId().toString(), List.of(identity.orgId().toString()), strings(identity.roleIds()),
				strings(identity.deptIds()), correlationId, "WORK_BRIEF");
	}

	private List<String> strings(List<Long> values) {
		return values.stream().map(String::valueOf).toList();
	}

	private AssistantContextPack contextPack(WorkBriefIdentity identity, WorkBriefSnapshot snapshot) {
		Instant createdAt = Instant.now();
		List<ContextFragment> work = new ArrayList<>();
		List<ContextFragment> information = new ArrayList<>();
		work.add(new ContextFragment(UUID.randomUUID().toString(), ProviderId.WORK, "WORK_BRIEF_METRICS", "snapshot",
				"v1", FactType.DOMAIN_FACT, "工作快照统计", metricsJson(snapshot), createdAt, createdAt,
				sha256(metricsJson(snapshot)), null));
		snapshot.tasks()
			.forEach(item -> work.add(fragment(ProviderId.WORK, "TASK", item.id(), item.title(), item.description(),
					at(item.dueAt()), "task-detail")));
		snapshot.schedules()
			.forEach(item -> work.add(fragment(ProviderId.WORK, "SCHEDULE", item.id(), item.title(), item.location(),
					at(item.startAt()), "schedule-detail")));
		snapshot.messages()
			.forEach(item -> work.add(fragment(ProviderId.WORK, "MESSAGE", item.id(), item.title(), item.content(),
					at(item.occurredAt()), "message-center")));
		snapshot.emails()
			.forEach(item -> work.add(fragment(ProviderId.WORK, "EMAIL", item.id(), item.title(), item.excerpt(),
					at(item.occurredAt()), "work-evidence")));
		snapshot.information()
			.forEach(item -> information.add(fragment(ProviderId.INFORMATION, "INFORMATION", item.id(), item.title(),
					item.summary(), at(item.publishedAt()), "information-detail")));
		List<ContextFragment> all = new ArrayList<>(work);
		all.addAll(information);
		List<ProviderResult> results = new ArrayList<>();
		results.add(result(ProviderId.WORK, work, createdAt));
		results.add(result(ProviderId.INFORMATION, information, createdAt));
		return new AssistantContextPack(UUID.randomUUID().toString(),
				"work-brief:" + identity.tenantId() + ":" + identity.ownerUserId(), UUID.randomUUID().toString(),
				createdAt, createdAt.plusSeconds(300),
				sha256(identity.tenantId() + ":" + identity.ownerUserId() + ":" + identity.orgId()), results, all,
				List.of());
	}

	private ProviderResult result(ProviderId provider, List<ContextFragment> fragments, Instant at) {
		return new ProviderResult(AssistantContextContract.VERSION, provider,
				fragments.isEmpty() ? ProviderStatus.NO_MATCH : ProviderStatus.OK, at, fragments.size(), 0, false,
				fragments, List.of());
	}

	private ContextFragment fragment(ProviderId provider, String refType, Long refId, String title, String excerpt,
			Instant occurredAt, String routeName) {
		String citationId = refType.toLowerCase() + "-" + refId;
		String safeExcerpt = limit(excerpt == null ? "" : excerpt, 1200);
		return new ContextFragment(UUID.randomUUID().toString(), provider, refType, refId.toString(), "v1",
				FactType.SOURCE_EVIDENCE, title, safeExcerpt, occurredAt, occurredAt,
				sha256(title + "\n" + safeExcerpt), new Citation(citationId, provider, refType, refId.toString(), "v1",
						"INTERNAL_ROUTE", routeName, Map.of("refType", refType, "refId", refId.toString())));
	}

	private String metricsJson(WorkBriefSnapshot snapshot) {
		try {
			return objectMapper.writeValueAsString(Map.of("pendingTasks", snapshot.tasks().size(), "upcomingSchedules",
					snapshot.schedules().size(), "informationItems", snapshot.information().size(), "unreadMessages",
					snapshot.messages().size(), "recentEmails", snapshot.emails().size()));
		}
		catch (Exception ex) {
			throw new IllegalStateException("WORK_SUMMARY_CONTEXT_INVALID", ex);
		}
	}

	private Instant at(java.time.LocalDateTime value) {
		return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String limit(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}

	public record Execution(WorkBriefDraft draft, AssistantExecutionTrace trace, boolean deterministicFallback,
			String actualSkillId, String actualReleaseId, String skillRunId) {
		public Execution(WorkBriefDraft draft, AssistantExecutionTrace trace, boolean deterministicFallback) {
			this(draft, trace, deterministicFallback, null, null, null);
		}
	}

}
