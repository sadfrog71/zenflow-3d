package com.deepdimen.matrix.work.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ErrorCode;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderWarning;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.work.entity.WorkScheduleEntity;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.mapper.WorkScheduleMapper;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;

@Component("workAssistantContextProvider")
public class WorkAssistantContextProvider implements AssistantContextProvider {

	private static final int MAX_EXPLICIT_IDS = 100;

	private static final ProviderLimits DEFAULT_LIMITS = new ProviderLimits(20, 12000, 3000);

	private final WorkTaskMapper taskMapper;

	private final WorkScheduleMapper scheduleMapper;

	private final com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy taskVisibility;

	public WorkAssistantContextProvider(WorkTaskMapper taskMapper, WorkScheduleMapper scheduleMapper,
			com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy taskVisibility) {
		this.taskMapper = taskMapper;
		this.scheduleMapper = scheduleMapper;
		this.taskVisibility = taskVisibility;
	}

	@Override
	public ProviderDescriptor describe() {
		return new ProviderDescriptor(AssistantContextContract.VERSION, ProviderId.WORK, "工作任务", "待办任务、日程和工作记录", "work",
				new ProviderCapabilities(true, true, true, true, true, true, false, List.of("TASK", "SCHEDULE"),
						List.of("type", "status")),
				DEFAULT_LIMITS);
	}

	@Override
	public ProviderResult query(ProviderQuery request) {
		Validation validation = validate(request);
		if (!validation.valid()) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(), validation.filtered(), false,
					new ProviderWarning(validation.errorCode(), validation.warning()));
		}
		long userId = Long.parseLong(request.identity().userId());
		long tenantId = Long.parseLong(request.identity().tenantId());
		List<Long> explicitIds = validation.explicitIds();
		String type = requestedType(request);
		int maxItems = Math.min(request.limits().maxItems(), DEFAULT_LIMITS.maxItems());
		int maxCharacters = Math.min(request.limits().maxCharacters(), DEFAULT_LIMITS.maxCharacters());
		int authorizationLimit = explicitIds.isEmpty() ? maxItems : Math.max(maxItems, explicitIds.size());
		List<ContextFragment> candidates = new ArrayList<>();
		if (!"SCHEDULE".equals(type)) {
			candidates.addAll(tasks(request, tenantId, userId, explicitIds, authorizationLimit));
		}
		if (!"TASK".equals(type)) {
			candidates.addAll(schedules(request, tenantId, userId, explicitIds, authorizationLimit));
		}
		Set<String> matchedIds = candidates.stream()
			.map(ContextFragment::sourceId)
			.collect(java.util.stream.Collectors.toSet());
		long missingExplicitIds = explicitIds.stream()
			.map(String::valueOf)
			.distinct()
			.filter(id -> !matchedIds.contains(id))
			.count();
		if (missingExplicitIds > 0) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(), Math.toIntExact(missingExplicitIds), false,
					new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, "显式工作对象不存在或当前用户无权访问。"));
		}
		BoundedFragments bounded = applyLimits(candidates, maxItems, maxCharacters);
		return result(bounded.fragments().isEmpty() ? ProviderStatus.NO_MATCH : ProviderStatus.OK, bounded.fragments(),
				0, bounded.truncated(), null);
	}

	@Override
	public CitationResolution resolve(CitationResolveRequest request) {
		if (request == null || !validIdentity(request.identity()) || request.citationId() == null
				|| !ProviderId.WORK.equals(request.citation().provider())) {
			return forbidden(request == null ? null : request.citationId());
		}
		String[] parts = request.citationId().split(":");
		if (parts.length != 2) {
			return forbidden(request.citationId());
		}
		try {
			long id = Long.parseLong(parts[1]);
			long userId = Long.parseLong(request.identity().userId());
			long tenantId = Long.parseLong(request.identity().tenantId());
			if ("work-task".equals(parts[0]) && "TASK".equals(request.citation().sourceType())
					&& String.valueOf(id).equals(request.citation().sourceId())) {
				WorkTaskEntity task = taskMapper
					.selectOne(taskVisibility.queryFor(tenantId, userId).eq(WorkTaskEntity::getId, id));
				return task == null ? forbidden(request.citationId())
						: resolved(request.citationId(), citation(request.citationId(), "TASK", task.getId(),
								version(task.getVersion(), task.getUpdateTime()), "WorkTaskDetail"));
			}
			if ("work-schedule".equals(parts[0]) && "SCHEDULE".equals(request.citation().sourceType())
					&& String.valueOf(id).equals(request.citation().sourceId())) {
				WorkScheduleEntity schedule = scheduleMapper.selectOne(Wrappers.<WorkScheduleEntity>lambdaQuery()
					.eq(WorkScheduleEntity::getId, id)
					.eq(WorkScheduleEntity::getTenantId, tenantId)
					.eq(WorkScheduleEntity::getTargetUserId, userId));
				return schedule == null ? forbidden(request.citationId())
						: resolved(request.citationId(), citation(request.citationId(), "SCHEDULE", schedule.getId(),
								version(schedule.getVersion(), schedule.getUpdateTime()), "WorkScheduleDetail"));
			}
		}
		catch (NumberFormatException ignored) {
			return forbidden(request.citationId());
		}
		return forbidden(request.citationId());
	}

	private List<ContextFragment> tasks(ProviderQuery request, long tenantId, long userId, List<Long> explicitIds,
			int limit) {
		String status = request.scope().filters().get("status");
		var query = taskVisibility.queryFor(tenantId, userId)
			.in(!explicitIds.isEmpty(), WorkTaskEntity::getId, explicitIds)
			.eq(status != null && !status.isBlank(), WorkTaskEntity::getStatus, status)
			.and(request.query() != null && !request.query().isBlank(),
					keyword -> keyword.like(WorkTaskEntity::getTitle, request.query())
						.or()
						.like(WorkTaskEntity::getDescription, request.query()))
			.ge(request.scope().from() != null, WorkTaskEntity::getDueAt, local(request.scope().from()))
			.le(request.scope().to() != null, WorkTaskEntity::getDueAt, local(request.scope().to()))
			.orderByAsc(WorkTaskEntity::getDueAt)
			.last("LIMIT " + Math.max(1, limit + 1));
		return taskMapper.selectList(query)
			.stream()
			.map(task -> new ContextFragment("work-task-fragment:" + task.getId(), ProviderId.WORK, "TASK",
					String.valueOf(task.getId()), version(task.getVersion(), task.getUpdateTime()),
					FactType.DOMAIN_FACT, task.getTitle(),
					"状态：" + task.getStatus() + "；优先级：" + task.getPriority() + "；截止：" + task.getDueAt(), null,
					instant(task.getUpdateTime()),
					hash(task.getTitle() + "|" + task.getStatus() + "|" + task.getDueAt()),
					citation("work-task:" + task.getId(), "TASK", task.getId(),
							version(task.getVersion(), task.getUpdateTime()), "WorkTaskDetail")))
			.toList();
	}

	private List<ContextFragment> schedules(ProviderQuery request, long tenantId, long userId, List<Long> explicitIds,
			int limit) {
		String status = request.scope().filters().get("status");
		var query = Wrappers.<WorkScheduleEntity>lambdaQuery()
			.eq(WorkScheduleEntity::getTenantId, tenantId)
			.eq(WorkScheduleEntity::getTargetUserId, userId)
			.in(!explicitIds.isEmpty(), WorkScheduleEntity::getId, explicitIds)
			.eq(status != null && !status.isBlank(), WorkScheduleEntity::getStatus, status)
			.like(request.query() != null && !request.query().isBlank(), WorkScheduleEntity::getTitle, request.query())
			.ge(request.scope().from() != null, WorkScheduleEntity::getStartAt, local(request.scope().from()))
			.le(request.scope().to() != null, WorkScheduleEntity::getStartAt, local(request.scope().to()))
			.orderByAsc(WorkScheduleEntity::getStartAt)
			.last("LIMIT " + Math.max(1, limit + 1));
		return scheduleMapper.selectList(query)
			.stream()
			.map(schedule -> new ContextFragment("work-schedule-fragment:" + schedule.getId(), ProviderId.WORK,
					"SCHEDULE", String.valueOf(schedule.getId()),
					version(schedule.getVersion(), schedule.getUpdateTime()), FactType.DOMAIN_FACT, schedule.getTitle(),
					"状态：" + schedule.getStatus() + "；时间：" + schedule.getStartAt() + " 至 " + schedule.getEndAt() + "；地点："
							+ schedule.getLocation(),
					instant(schedule.getStartAt()), instant(schedule.getUpdateTime()),
					hash(schedule.getTitle() + "|" + schedule.getStartAt() + "|" + schedule.getStatus()),
					citation("work-schedule:" + schedule.getId(), "SCHEDULE", schedule.getId(),
							version(schedule.getVersion(), schedule.getUpdateTime()), "WorkScheduleDetail")))
			.toList();
	}

	private Validation validate(ProviderQuery request) {
		if (request == null || !AssistantContextContract.VERSION.equals(request.contractVersion())
				|| !validIdentity(request.identity())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "身份或契约版本无效。", 0);
		}
		if (request.scope() == null || request.limits() == null
				|| request.scope()
					.filters()
					.keySet()
					.stream()
					.anyMatch(key -> !List.of("type", "status").contains(key))) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "工作范围参数无效。", 0);
		}
		if (request.scope().from() != null && request.scope().to() != null
				&& request.scope().from().isAfter(request.scope().to())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "时间范围无效。", 0);
		}
		String type = request.scope().filters().get("type");
		if (type != null && !Set.of("TASK", "SCHEDULE").contains(type.toUpperCase())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "工作对象类型无效。", 0);
		}
		if (request.scope().explicitObjectIds().size() > MAX_EXPLICIT_IDS) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "显式对象数量超过上限。",
					request.scope().explicitObjectIds().size());
		}
		try {
			List<Long> ids = request.scope().explicitObjectIds().stream().map(Long::valueOf).toList();
			return Validation.valid(new ArrayList<>(new LinkedHashSet<>(ids)));
		}
		catch (NumberFormatException ex) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "对象 ID 格式无效。",
					request.scope().explicitObjectIds().size());
		}
	}

	private boolean validIdentity(
			com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope identity) {
		return identity != null && identity.tenantId() != null && identity.userId() != null
				&& "ASSISTANT_CONTEXT".equals(identity.purpose());
	}

	private String requestedType(ProviderQuery request) {
		String type = request.scope().filters().get("type");
		if (type != null) {
			return type.toUpperCase();
		}
		return request.scope().sourceRoute() != null && request.scope().sourceRoute().contains("schedule") ? "SCHEDULE"
				: null;
	}

	private ProviderResult result(ProviderStatus status, List<ContextFragment> fragments, int permissionFiltered,
			boolean truncated, ProviderWarning warning) {
		return new ProviderResult(AssistantContextContract.VERSION, ProviderId.WORK, status, Instant.now(),
				fragments.size(), permissionFiltered, truncated, fragments,
				warning == null ? List.of() : List.of(warning));
	}

	private BoundedFragments applyLimits(List<ContextFragment> candidates, int maxItems, int maxCharacters) {
		List<ContextFragment> accepted = new ArrayList<>();
		int remaining = maxCharacters;
		boolean truncated = candidates.size() > maxItems;
		for (ContextFragment fragment : candidates) {
			if (accepted.size() >= maxItems || remaining <= 0) {
				truncated = true;
				break;
			}
			String excerpt = fragment.excerpt() == null ? "" : fragment.excerpt();
			if (excerpt.length() > remaining) {
				excerpt = excerpt.substring(0, remaining);
				truncated = true;
			}
			accepted.add(new ContextFragment(fragment.fragmentId(), fragment.provider(), fragment.sourceType(),
					fragment.sourceId(), fragment.sourceVersion(), fragment.factType(), fragment.title(), excerpt,
					fragment.occurredAt(), fragment.updatedAt(), fragment.contentHash(), fragment.citation(),
					DataClassification.SENSITIVE, 0));
			remaining -= excerpt.length();
			if (truncated && remaining == 0) {
				break;
			}
		}
		return new BoundedFragments(List.copyOf(accepted), truncated || accepted.size() < candidates.size());
	}

	private CitationResolution resolved(String citationId, Citation citation) {
		return new CitationResolution(citationId, ProviderStatus.OK, citation, null);
	}

	private CitationResolution forbidden(String citationId) {
		return new CitationResolution(citationId, ProviderStatus.FORBIDDEN_SCOPE, null,
				new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, "工作引用不存在或当前用户无权访问。"));
	}

	private Citation citation(String id, String sourceType, long sourceId, String sourceVersion, String routeName) {
		return new Citation(id, ProviderId.WORK, sourceType, String.valueOf(sourceId), sourceVersion, "INTERNAL",
				routeName, Map.of("id", String.valueOf(sourceId)));
	}

	private java.time.LocalDateTime local(Instant instant) {
		// MyBatis-Plus condition arguments are evaluated before the condition is
		// applied, so this helper must also be null-safe for optional date bounds.
		return instant == null ? null : java.time.LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private Instant instant(java.time.LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	private String version(Integer version, java.time.LocalDateTime updatedAt) {
		return String.valueOf(version == null ? 0 : version) + ":" + String.valueOf(updatedAt);
	}

	private String hash(String value) {
		try {
			return "sha256:" + HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256")
					.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record BoundedFragments(List<ContextFragment> fragments, boolean truncated) {
	}

	private record Validation(boolean valid, List<Long> explicitIds, int filtered, ErrorCode errorCode,
			String warning) {

		private static Validation valid(List<Long> explicitIds) {
			return new Validation(true, explicitIds, 0, null, null);
		}

		private static Validation invalid(ErrorCode errorCode, String warning, int filtered) {
			return new Validation(false, List.of(), filtered, errorCode, warning);
		}
	}

}
