package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.dto.BusinessMessageSnapshot;
import com.deepdimen.matrix.admin.api.service.BusinessMessageReader;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.work.api.enums.WorkScheduleStatus;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.api.vo.InformationDetailVO;
import com.deepdimen.matrix.work.api.vo.WorkBriefItemVO;
import com.deepdimen.matrix.work.api.vo.WorkBriefVO;
import com.deepdimen.matrix.work.brief.WorkBriefDraft;
import com.deepdimen.matrix.work.brief.WorkBriefIdentity;
import com.deepdimen.matrix.work.brief.WorkBriefReviewer;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.ScheduleFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.TaskFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.InformationFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.MessageFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.EmailFact;
import com.deepdimen.matrix.work.brief.WorkBriefSkillRunner;
import com.deepdimen.matrix.work.entity.WorkBriefReportEntity;
import com.deepdimen.matrix.work.entity.WorkBriefRunEntity;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;
import com.deepdimen.matrix.work.entity.WorkEvidenceEntity;
import com.deepdimen.matrix.work.entity.WorkScheduleEntity;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.mapper.WorkBriefReportMapper;
import com.deepdimen.matrix.work.mapper.WorkBriefRunMapper;
import com.deepdimen.matrix.work.mapper.WorkBriefPolicyMapper;
import com.deepdimen.matrix.work.mapper.WorkEvidenceMapper;
import com.deepdimen.matrix.work.mapper.InformationItemMapper;
import com.deepdimen.matrix.work.mapper.WorkScheduleMapper;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkBriefService {

	private static final int SNAPSHOT_LIMIT = 100;

	private final WorkTaskMapper taskMapper;

	private final com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy taskVisibility;

	private final WorkScheduleMapper scheduleMapper;

	private final WorkBriefRunMapper runMapper;

	private final WorkBriefReportMapper reportMapper;

	private final WorkBriefPolicyMapper policyMapper;

	private final InformationItemMapper informationItemMapper;

	private final WorkEvidenceMapper evidenceMapper;

	private final BusinessMessageReader messageReader;

	private final WorkBriefSkillRunner skillRunner;

	private final WorkBriefReviewer reviewer;

	private final ObjectMapper objectMapper;

	public WorkBriefVO run(WorkBriefIdentity identity, boolean force) {
		LocalDateTime now = LocalDateTime.now();
		WorkBriefPolicyEntity policy = resolvePolicy(identity);
		WorkBriefSnapshot snapshot = snapshot(identity, policy, now);
		String inputHash = inputHash(snapshot, policy);
		if (!force && !"MANAGED".equals(policy.getSkillManagementMode())) {
			WorkBriefVO replay = findReplay(identity, inputHash, now.toLocalDate());
			if (replay != null) {
				return replay;
			}
		}

		WorkBriefRunEntity run = createRun(identity, policy, snapshot, inputHash, now);
		try {
			WorkBriefSkillRunner.Execution execution = skillRunner.execute(identity, policy, snapshot,
					run.getId().toString());
			if (execution.skillRunId() != null) {
				run.setSkillRunId(Long.valueOf(execution.skillRunId()));
				run.setSkillId(Long.valueOf(execution.actualSkillId()));
				run.setSkillVersion("release:" + execution.actualReleaseId());
			}
			WorkBriefDraft draft = execution.draft();
			WorkBriefReviewer.ReviewResult review = reviewer.review(snapshot, draft);
			if (!review.approved() && !execution.deterministicFallback() && run.getSkillRunId() == null
					&& Boolean.TRUE.equals(policy.getAllowDeterministicFallback())) {
				log.warn("工作简报 Skill 输出未通过评审，改用租户策略允许的确定性降级，tenantId={}, userId={}, reason={}", identity.tenantId(),
						identity.ownerUserId(), review.reason());
				execution = skillRunner.deterministic(snapshot, execution.trace());
				draft = execution.draft();
				review = reviewer.review(snapshot, draft);
			}
			WorkBriefReportEntity report = saveReport(identity, policy, run, draft, review, now);
			captureUsage(run, execution);
			run.setReviewerStatus(review.status());
			run.setStatus(review.approved()
					? (run.getSkillRunId() != null && execution.deterministicFallback() ? "DEGRADED" : "SUCCESS")
					: "REVIEW_REJECTED");
			run.setFinishedAt(LocalDateTime.now());
			run.setUpdateTime(LocalDateTime.now());
			runMapper.updateById(run);
			return toVO(report);
		}
		catch (Exception ex) {
			if (ex instanceof com.deepdimen.matrix.work.brief.ManagedWorkBriefFailure failure) {
				run.setSkillRunId(Long.valueOf(failure.skillRunId()));
				run.setSkillId(Long.valueOf(failure.skillId()));
				run.setSkillVersion("release:" + failure.releaseId());
				run.setUsageSource("MANAGED_USAGE_UNAVAILABLE");
			}
			run.setStatus("FAILED");
			run.setErrorMessage(limit(ex.getMessage(), 1000));
			run.setFinishedAt(LocalDateTime.now());
			run.setUpdateTime(LocalDateTime.now());
			runMapper.updateById(run);
			log.error("工作简报生成失败 tenantId={}, userId={}, runId={}", identity.tenantId(), identity.ownerUserId(),
					run.getId(), ex);
			throw new IllegalStateException("工作简报生成失败：" + limit(ex.getMessage(), 300), ex);
		}
	}

	public WorkBriefVO latest(WorkBriefIdentity identity) {
		WorkBriefReportEntity report = reportMapper.selectOne(Wrappers.<WorkBriefReportEntity>lambdaQuery()
			.eq(WorkBriefReportEntity::getTenantId, identity.tenantId())
			.eq(WorkBriefReportEntity::getOwnerUserId, identity.ownerUserId())
			.eq(WorkBriefReportEntity::getOrgId, identity.orgId())
			.eq(WorkBriefReportEntity::getReviewerStatus, "APPROVED")
			.orderByDesc(WorkBriefReportEntity::getCreateTime)
			.last("LIMIT 1"));
		return report == null ? null : toVO(report);
	}

	public WorkBriefVO detail(WorkBriefIdentity identity, Long id) {
		WorkBriefReportEntity report = reportMapper.selectOne(Wrappers.<WorkBriefReportEntity>lambdaQuery()
			.eq(WorkBriefReportEntity::getId, id)
			.eq(WorkBriefReportEntity::getTenantId, identity.tenantId())
			.eq(WorkBriefReportEntity::getOwnerUserId, identity.ownerUserId())
			.eq(WorkBriefReportEntity::getOrgId, identity.orgId())
			.last("LIMIT 1"));
		if (report == null) {
			throw new IllegalArgumentException("未找到对应工作简报");
		}
		return toVO(report);
	}

	private WorkBriefSnapshot snapshot(WorkBriefIdentity identity, WorkBriefPolicyEntity policy, LocalDateTime now) {
		Set<String> enabledSources = enabledSources(policy);
		List<TaskFact> tasks = taskMapper
			.selectList(taskVisibility.queryFor(identity.tenantId(), identity.ownerUserId())
				.notIn(WorkTaskEntity::getStatus, WorkTaskStatus.DONE, WorkTaskStatus.CANCELLED)
				.orderByAsc(WorkTaskEntity::getDueAt)
				.last("LIMIT " + SNAPSHOT_LIMIT))
			.stream()
			.map(task -> new TaskFact(task.getId(), task.getTitle(), task.getDescription(), task.getPriority(),
					task.getStatus().name(), task.getDueAt(), task.getSourceType()))
			.toList();
		if (!enabledSources.contains("TASK")) {
			tasks = List.of();
		}
		LocalDateTime rangeStart = now.toLocalDate().atStartOfDay();
		LocalDateTime rangeEnd = now.toLocalDate().plusDays(7).atTime(LocalTime.MAX);
		List<ScheduleFact> schedules = scheduleMapper
			.selectList(Wrappers.<WorkScheduleEntity>lambdaQuery()
				.eq(WorkScheduleEntity::getTenantId, identity.tenantId())
				.eq(WorkScheduleEntity::getTargetUserId, identity.ownerUserId())
				.between(WorkScheduleEntity::getStartAt, rangeStart, rangeEnd)
				.notIn(WorkScheduleEntity::getStatus, WorkScheduleStatus.CANCELLED, WorkScheduleStatus.HIDDEN)
				.orderByAsc(WorkScheduleEntity::getStartAt)
				.last("LIMIT " + SNAPSHOT_LIMIT))
			.stream()
			.map(schedule -> new ScheduleFact(schedule.getId(), schedule.getTitle(), schedule.getStartAt(),
					schedule.getEndAt(), schedule.getLocation(), schedule.getStatus().name()))
			.toList();
		if (!enabledSources.contains("SCHEDULE")) {
			schedules = List.of();
		}
		List<InformationFact> information = enabledSources.contains("INFORMATION") ? informationItemMapper
			.selectContextItems(identity.tenantId(), identity.orgId(), identity.ownerUserId(), null, now.minusDays(7),
					now, List.of(), SNAPSHOT_LIMIT)
			.stream()
			.map(this::informationFact)
			.toList() : List.of();
		List<MessageFact> messages = enabledSources.contains("MESSAGE") ? messageReader
			.listUnread(identity.tenantId(), identity.ownerUserId(), identity.orgId(), now.minusDays(30),
					SNAPSHOT_LIMIT)
			.stream()
			.map(this::messageFact)
			.toList() : List.of();
		List<EmailFact> emails = enabledSources.contains("EMAIL") ? evidenceMapper
			.selectList(Wrappers.<WorkEvidenceEntity>lambdaQuery()
				.eq(WorkEvidenceEntity::getTenantId, identity.tenantId())
				.eq(WorkEvidenceEntity::getOwnerUserId, identity.ownerUserId())
				.eq(WorkEvidenceEntity::getOrgId, identity.orgId())
				.eq(WorkEvidenceEntity::getEvidenceType, "EMAIL")
				.ge(WorkEvidenceEntity::getOccurredAt, now.minusDays(7))
				.orderByDesc(WorkEvidenceEntity::getOccurredAt)
				.last("LIMIT " + SNAPSHOT_LIMIT))
			.stream()
			.map(this::emailFact)
			.toList() : List.of();
		WorkBriefReportEntity previous = reportMapper.selectOne(Wrappers.<WorkBriefReportEntity>lambdaQuery()
			.eq(WorkBriefReportEntity::getTenantId, identity.tenantId())
			.eq(WorkBriefReportEntity::getOwnerUserId, identity.ownerUserId())
			.eq(WorkBriefReportEntity::getOrgId, identity.orgId())
			.eq(WorkBriefReportEntity::getReviewerStatus, "APPROVED")
			.orderByDesc(WorkBriefReportEntity::getCreateTime)
			.last("LIMIT 1"));
		return new WorkBriefSnapshot(identity.tenantId(), identity.ownerUserId(), now, tasks, schedules, information,
				messages, emails, previous == null ? null : previous.getExecutiveSummary());
	}

	private WorkBriefVO findReplay(WorkBriefIdentity identity, String inputHash, LocalDate reportDate) {
		WorkBriefRunEntity previousRun = runMapper.selectOne(Wrappers.<WorkBriefRunEntity>lambdaQuery()
			.eq(WorkBriefRunEntity::getTenantId, identity.tenantId())
			.eq(WorkBriefRunEntity::getOwnerUserId, identity.ownerUserId())
			.eq(WorkBriefRunEntity::getOrgId, identity.orgId())
			.eq(WorkBriefRunEntity::getInputHash, inputHash)
			.eq(WorkBriefRunEntity::getStatus, "SUCCESS")
			.ge(WorkBriefRunEntity::getCreateTime, reportDate.atStartOfDay())
			.orderByDesc(WorkBriefRunEntity::getCreateTime)
			.last("LIMIT 1"));
		if (previousRun == null) {
			return null;
		}
		WorkBriefReportEntity report = reportMapper.selectOne(Wrappers.<WorkBriefReportEntity>lambdaQuery()
			.eq(WorkBriefReportEntity::getTenantId, identity.tenantId())
			.eq(WorkBriefReportEntity::getOwnerUserId, identity.ownerUserId())
			.eq(WorkBriefReportEntity::getOrgId, identity.orgId())
			.eq(WorkBriefReportEntity::getRunId, previousRun.getId())
			.eq(WorkBriefReportEntity::getReviewerStatus, "APPROVED")
			.last("LIMIT 1"));
		return report == null ? null : toVO(report);
	}

	private WorkBriefRunEntity createRun(WorkBriefIdentity identity, WorkBriefPolicyEntity policy,
			WorkBriefSnapshot snapshot, String inputHash, LocalDateTime now) {
		WorkBriefRunEntity run = new WorkBriefRunEntity();
		run.setOwnerUserId(identity.ownerUserId());
		run.setOrgId(identity.orgId());
		run.setPolicyId(policy.getId());
		run.setSkillId(policy.getSkillId());
		run.setTriggerType("MANUAL");
		run.setStatus("RUNNING");
		run.setSkillCode(WorkBriefSkillRunner.SKILL_CODE);
		run.setSkillVersion(policy.getSkillVersion());
		run.setInputHash(inputHash);
		run.setInputSnapshot(writeJson(snapshot));
		run.setTaskCount(snapshot.tasks().size());
		run.setScheduleCount(snapshot.schedules().size());
		run.setInformationCount(snapshot.information().size());
		run.setMessageCount(snapshot.messages().size());
		run.setEmailCount(snapshot.emails().size());
		run.setExternalTaskCount((int) snapshot.tasks()
			.stream()
			.filter(task -> task.sourceType() != null && !"MANUAL".equalsIgnoreCase(task.sourceType()))
			.count());
		run.setStartedAt(now);
		run.setCreateTime(now);
		run.setUpdateTime(now);
		run.setTenantId(identity.tenantId());
		runMapper.insert(run);
		return run;
	}

	private WorkBriefReportEntity saveReport(WorkBriefIdentity identity, WorkBriefPolicyEntity policy,
			WorkBriefRunEntity run, WorkBriefDraft draft, WorkBriefReviewer.ReviewResult review, LocalDateTime now) {
		WorkBriefReportEntity report = new WorkBriefReportEntity();
		report.setRunId(run.getId());
		report.setOwnerUserId(identity.ownerUserId());
		report.setOrgId(identity.orgId());
		report.setPolicyId(policy.getId());
		report.setSkillId(run.getSkillId());
		report.setReportDate(now.toLocalDate());
		report.setTitle(draft.title());
		report.setExecutiveSummary(draft.executiveSummary());
		report.setContinuityNote(draft.continuityNote());
		report.setMetricsJson(writeJson(draft.metrics()));
		report.setFocusItemsJson(writeJson(draft.focusItems()));
		report.setScheduleItemsJson(writeJson(draft.scheduleItems()));
		report.setInformationItemsJson(writeJson(draft.informationItems()));
		report.setMessageItemsJson(writeJson(draft.messageItems()));
		report.setEmailItemsJson(writeJson(draft.emailItems()));
		report.setRiskItemsJson(writeJson(draft.riskItems()));
		report.setCitationsJson(writeJson(draft.citations()));
		report.setReviewerStatus(review.status());
		report.setReviewerReason(review.reason());
		report.setSkillCode(WorkBriefSkillRunner.SKILL_CODE);
		report.setSkillVersion(run.getSkillVersion());
		report.setCreateTime(now);
		report.setUpdateTime(now);
		report.setDelFlag("0");
		report.setTenantId(identity.tenantId());
		reportMapper.insert(report);
		return report;
	}

	private void captureUsage(WorkBriefRunEntity run, WorkBriefSkillRunner.Execution execution) {
		AssistantExecutionTrace trace = execution.trace();
		if (trace != null) {
			run.setProvider(trace.provider());
			run.setModel(trace.model());
			run.setPromptTokens(trace.promptTokens());
			run.setCompletionTokens(trace.completionTokens());
			run.setTotalTokens(trace.totalTokens());
			run.setUsageSource(trace.usageSource());
			run.setRequestId(trace.requestId());
		}
		if (execution.skillRunId() != null) {
			// Managed model usage is held by the child run. A local fallback does not
			// establish that the preceding model call consumed zero tokens.
			run.setUsageSource(execution.deterministicFallback() ? "MANAGED_FALLBACK_USAGE_UNAVAILABLE"
					: "MANAGED_USAGE_UNAVAILABLE");
			return;
		}
		if (execution.deterministicFallback() && run.getUsageSource() == null) {
			run.setProvider("LOCAL");
			run.setModel("deterministic-work-summary");
			run.setPromptTokens(0L);
			run.setCompletionTokens(0L);
			run.setTotalTokens(0L);
			run.setUsageSource("DETERMINISTIC_FALLBACK");
		}
	}

	private WorkBriefVO toVO(WorkBriefReportEntity report) {
		try {
			WorkBriefVO vo = new WorkBriefVO();
			vo.setId(report.getId());
			vo.setRunId(report.getRunId());
			com.deepdimen.matrix.work.brief.WorkBriefExecutionMetadata.populate(vo, report, runMapper);
			vo.setReportDate(report.getReportDate());
			vo.setTitle(report.getTitle());
			vo.setExecutiveSummary(report.getExecutiveSummary());
			vo.setContinuityNote(report.getContinuityNote());
			vo.setMetrics(objectMapper.readValue(report.getMetricsJson(), new TypeReference<Map<String, Integer>>() {
			}));
			vo.setFocusItems(readItems(report.getFocusItemsJson()));
			vo.setScheduleItems(readItems(report.getScheduleItemsJson()));
			vo.setInformationItems(readItems(report.getInformationItemsJson()));
			vo.setMessageItems(readItems(report.getMessageItemsJson()));
			vo.setEmailItems(readItems(report.getEmailItemsJson()));
			vo.setRiskItems(readItems(report.getRiskItemsJson()));
			vo.setCitations(readItems(report.getCitationsJson()));
			vo.setReviewerStatus(report.getReviewerStatus());
			vo.setReviewerReason(report.getReviewerReason());
			vo.setSkillCode(report.getSkillCode());
			vo.setSkillVersion(report.getSkillVersion());
			vo.setCreateTime(report.getCreateTime());
			return vo;
		}
		catch (Exception ex) {
			throw new IllegalStateException("工作简报数据解析失败", ex);
		}
	}

	private List<WorkBriefItemVO> readItems(String json) throws Exception {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		return objectMapper.readValue(json, new TypeReference<List<WorkBriefItemVO>>() {
		});
	}

	private String inputHash(WorkBriefSnapshot snapshot, WorkBriefPolicyEntity policy) {
		return sha256(writeJson(Map.of("policyId", policy.getId(), "skillVersion", policy.getSkillVersion(), "tasks",
				snapshot.tasks(), "schedules", snapshot.schedules(), "information", snapshot.information(), "messages",
				snapshot.messages(), "emails", snapshot.emails())));
	}

	private WorkBriefPolicyEntity resolvePolicy(WorkBriefIdentity identity) {
		WorkBriefPolicyEntity policy = policyMapper.selectOne(Wrappers.<WorkBriefPolicyEntity>lambdaQuery()
			.eq(WorkBriefPolicyEntity::getTenantId, identity.tenantId())
			.eq(WorkBriefPolicyEntity::getOrgId, identity.orgId())
			.last("LIMIT 1"));
		if (policy == null) {
			policy = policyMapper.selectOne(Wrappers.<WorkBriefPolicyEntity>lambdaQuery()
				.eq(WorkBriefPolicyEntity::getTenantId, identity.tenantId())
				.eq(WorkBriefPolicyEntity::getOrgId, 0L)
				.last("LIMIT 1"));
		}
		if (policy == null || !Boolean.TRUE.equals(policy.getEnabled()) || policy.getSkillId() == null) {
			throw new IllegalStateException("当前租户或组织未发布工作简报 Skill 策略");
		}
		return policy;
	}

	private Set<String> enabledSources(WorkBriefPolicyEntity policy) {
		try {
			return Set.copyOf(objectMapper.readValue(policy.getEnabledSources(), new TypeReference<List<String>>() {
			}));
		}
		catch (Exception ex) {
			throw new IllegalStateException("工作简报数据源策略不合法", ex);
		}
	}

	private InformationFact informationFact(InformationDetailVO item) {
		return new InformationFact(item.getId(), item.getContentType(), item.getTitle(), item.getSummary(),
				item.getSourceName(), item.getPublishedAt());
	}

	private MessageFact messageFact(BusinessMessageSnapshot item) {
		return new MessageFact(item.getId(), item.getTitle(), item.getContent(), item.getSourceType(),
				item.getEventType(), item.getPriority(), item.getTargetPath(),
				Boolean.TRUE.equals(item.getActionRequired()), item.getOccurredAt());
	}

	private EmailFact emailFact(WorkEvidenceEntity item) {
		return new EmailFact(item.getId(), item.getSourceId(), item.getTitle(), item.getExcerpt(),
				item.getSenderDisplay(), item.getOccurredAt());
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (Exception ex) {
			throw new IllegalStateException("工作简报 JSON 处理失败", ex);
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException("工作简报快照哈希失败", ex);
		}
	}

	private String limit(String value, int max) {
		if (value == null) {
			return "未知错误";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

}
