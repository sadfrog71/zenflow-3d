package com.deepdimen.matrix.capsule.context;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ErrorCode;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderScope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderWarning;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.vo.CapsuleDetailVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleVersionVO;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.capsule.service.CapsuleService;

/** Capsule's only Assistant-facing contract. Domain authorization remains in CapsuleService. */
@Component("capsuleAssistantContextProvider")
public class CapsuleContextProvider implements AssistantContextProvider {

	private static final String PURPOSE = "ASSISTANT_CONTEXT";

	private static final String RECORD_TYPE_FILTER = "recordType";

	private static final int MAX_EXPLICIT_IDS = 100;

	private static final ProviderLimits DEFAULT_LIMITS = new ProviderLimits(20, 12000, 3000);

	private final CapsuleService capsuleService;

	public CapsuleContextProvider(CapsuleService capsuleService) {
		this.capsuleService = capsuleService;
	}

	@Override
	public ProviderDescriptor describe() {
		return new ProviderDescriptor(AssistantContextContract.VERSION, ProviderId.CAPSULE, "闪念胶囊",
				"临时想法、会议要点和个人记录", "capsule",
				new ProviderCapabilities(true, true, true, true, true, true, false,
						List.of("IDEA", "DATA_FRAGMENT", "MEETING_POINT", "DECISION", "OTHER"),
						List.of(RECORD_TYPE_FILTER)),
				DEFAULT_LIMITS);
	}

	@Override
	public ProviderResult query(ProviderQuery request) {
		Validation validation = validate(request);
		if (!validation.valid()) {
			return empty(ProviderStatus.FORBIDDEN_SCOPE, validation.permissionFiltered(), validation.errorCode(),
					validation.warning());
		}
		ProviderScope scope = request.scope();
		List<Long> explicitIds = validation.explicitIds();
		if (!explicitIds.isEmpty()) {
			long owned = capsuleService.countOwnedExplicitIds(validation.identity(), explicitIds);
			int filtered = Math.toIntExact(explicitIds.size() - owned);
			if (filtered > 0) {
				return empty(ProviderStatus.FORBIDDEN_SCOPE, filtered, ErrorCode.FORBIDDEN_SCOPE,
						"显式胶囊不存在或当前用户无权访问。");
			}
		}

		int maxItems = Math.min(request.limits().maxItems(), DEFAULT_LIMITS.maxItems());
		int maxCharacters = Math.min(request.limits().maxCharacters(), DEFAULT_LIMITS.maxCharacters());
		List<CapsuleDetailVO> records = capsuleService.searchForContext(validation.identity(), request.query(),
				toLocalDateTime(scope.from()), toLocalDateTime(scope.to()), filterRecordType(scope), explicitIds,
				maxItems + 1);
		boolean truncated = records.size() > maxItems;
		List<ContextFragment> fragments = new ArrayList<>();
		int remainingCharacters = maxCharacters;
		for (CapsuleDetailVO record : records.stream().limit(maxItems).toList()) {
			if (remainingCharacters <= 0) {
				truncated = true;
				break;
			}
			String excerpt = record.getContent() == null ? "" : record.getContent();
			if (excerpt.length() > remainingCharacters) {
				excerpt = excerpt.substring(0, remainingCharacters);
				truncated = true;
			}
			remainingCharacters -= excerpt.length();
			String sourceType = record.getRecordType() == null ? CapsuleRecordType.OTHER.name()
					: record.getRecordType().name();
			String sourceVersion = String.valueOf(record.getRevisionNo());
			fragments.add(new ContextFragment(
					"capsule-fragment:" + record.getId() + ":" + record.getRevisionNo(), ProviderId.CAPSULE,
					sourceType, String.valueOf(record.getId()), sourceVersion, FactType.DOMAIN_FACT, record.getTitle(),
					excerpt, toInstant(record.getCapturedAt()), toInstant(record.getUpdateTime()),
					"sha256:" + record.getContentHash(), citation(record.getId(), sourceType, sourceVersion),
					DataClassification.SENSITIVE, 0));
		}
		ProviderStatus status = fragments.isEmpty() ? ProviderStatus.NO_MATCH : ProviderStatus.OK;
		return new ProviderResult(AssistantContextContract.VERSION, ProviderId.CAPSULE, status, Instant.now(),
				fragments.size(), 0, truncated, fragments, List.of());
	}

	@Override
	public CitationResolution resolve(CitationResolveRequest request) {
		if (!validCitationRequest(request)) {
			return forbidden(request == null ? null : request.citationId());
		}
		CitationParts parts = parseCitation(request.citationId());
		if (parts == null || !String.valueOf(parts.capsuleId()).equals(request.citation().sourceId())
				|| !String.valueOf(parts.revisionNo()).equals(request.citation().sourceVersion())) {
			return forbidden(request.citationId());
		}
		try {
			CapsuleIdentity identity = identity(request.identity());
			CapsuleDetailVO detail = capsuleService.detail(identity, parts.capsuleId());
			CapsuleVersionVO version = capsuleService.versions(identity, parts.capsuleId())
				.stream()
				.filter(item -> Objects.equals(item.getRevisionNo(), parts.revisionNo()))
				.findFirst()
				.orElse(null);
			if (detail == null || version == null) {
				return forbidden(request.citationId());
			}
			String sourceType = detail.getRecordType() == null ? CapsuleRecordType.OTHER.name()
					: detail.getRecordType().name();
			return new CitationResolution(request.citationId(), ProviderStatus.OK,
					citation(parts.capsuleId(), sourceType, String.valueOf(parts.revisionNo())), null);
		}
		catch (IllegalArgumentException ex) {
			return forbidden(request.citationId());
		}
	}

	private Validation validate(ProviderQuery request) {
		if (request == null || !AssistantContextContract.VERSION.equals(request.contractVersion())
				|| !validIdentity(request.identity())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "身份或契约版本无效。", 0);
		}
		ProviderScope scope = request.scope();
		if (scope.from() != null && scope.to() != null && scope.from().isAfter(scope.to())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "时间范围无效。", 0);
		}
		if (scope.filters().keySet().stream().anyMatch(key -> !RECORD_TYPE_FILTER.equals(key))) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "胶囊 Provider 不接受该过滤字段。", 0);
		}
		String recordType = scope.filters().get(RECORD_TYPE_FILTER);
		if (recordType != null) {
			try {
				CapsuleRecordType.valueOf(recordType);
			}
			catch (IllegalArgumentException ex) {
				return Validation.invalid(ErrorCode.INVALID_SCOPE, "胶囊类型无效。", 0);
			}
		}
		if (scope.explicitObjectIds().size() > MAX_EXPLICIT_IDS) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "显式对象数量超过上限。",
					scope.explicitObjectIds().size());
		}
		try {
			List<Long> ids = scope.explicitObjectIds().stream().map(Long::valueOf).toList();
			return Validation.valid(identity(request.identity()), new ArrayList<>(new LinkedHashSet<>(ids)));
		}
		catch (NumberFormatException ex) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "对象 ID 格式无效。",
					scope.explicitObjectIds().size());
		}
	}

	private boolean validIdentity(IdentityEnvelope identity) {
		return identity != null && notBlank(identity.tenantId()) && notBlank(identity.userId())
				&& PURPOSE.equals(identity.purpose());
	}

	private boolean validCitationRequest(CitationResolveRequest request) {
		return request != null && validIdentity(request.identity()) && ProviderId.CAPSULE.equals(request.citation().provider())
				&& request.citationId() != null;
	}

	private CapsuleIdentity identity(IdentityEnvelope identity) {
		return new CapsuleIdentity(Long.parseLong(identity.tenantId()), Long.parseLong(identity.userId()), identity.subjectType());
	}

	private CapsuleRecordType filterRecordType(ProviderScope scope) {
		String value = scope.filters().get(RECORD_TYPE_FILTER);
		return value == null ? null : CapsuleRecordType.valueOf(value);
	}

	private ProviderResult empty(ProviderStatus status, int permissionFiltered, ErrorCode errorCode, String warning) {
		return new ProviderResult(AssistantContextContract.VERSION, ProviderId.CAPSULE, status, Instant.now(), 0,
				permissionFiltered, false, List.of(),
				warning == null ? List.of() : List.of(new ProviderWarning(errorCode, warning)));
	}

	private CitationResolution forbidden(String citationId) {
		return CitationResolution.forbidden(citationId == null || citationId.isBlank() ? "invalid-citation" : citationId,
				"胶囊引用不存在或当前用户无权访问。");
	}

	private Citation citation(long id, String sourceType, String sourceVersion) {
		return new Citation(citationId(id, Integer.parseInt(sourceVersion)), ProviderId.CAPSULE, sourceType,
				String.valueOf(id), sourceVersion, "INTERNAL", "CapsuleDetail", Map.of("id", String.valueOf(id)));
	}

	private CitationParts parseCitation(String citationId) {
		String[] parts = citationId.split(":");
		if (parts.length != 3 || !"capsule".equals(parts[0])) {
			return null;
		}
		try {
			return new CitationParts(Long.valueOf(parts[1]), Integer.valueOf(parts[2]));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private String citationId(Long id, Integer revisionNo) {
		return "capsule:" + id + ":" + revisionNo;
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	private LocalDateTime toLocalDateTime(Instant value) {
		return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
	}

	private Instant toInstant(LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	private record CitationParts(Long capsuleId, Integer revisionNo) {
	}

	private record Validation(boolean valid, CapsuleIdentity identity, List<Long> explicitIds, int permissionFiltered,
			ErrorCode errorCode, String warning) {

		private static Validation valid(CapsuleIdentity identity, List<Long> explicitIds) {
			return new Validation(true, identity, explicitIds, 0, null, null);
		}

		private static Validation invalid(ErrorCode errorCode, String warning, int permissionFiltered) {
			return new Validation(false, null, List.of(), permissionFiltered, errorCode, warning);
		}
	}

}
