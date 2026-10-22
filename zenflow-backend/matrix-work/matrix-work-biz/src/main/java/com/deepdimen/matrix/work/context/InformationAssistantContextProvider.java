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
import com.deepdimen.matrix.work.api.entity.InformationEvidence;
import com.deepdimen.matrix.work.api.vo.InformationDetailVO;
import com.deepdimen.matrix.work.mapper.InformationEvidenceMapper;
import com.deepdimen.matrix.work.mapper.InformationItemMapper;

@Component("informationAssistantContextProvider")
public class InformationAssistantContextProvider implements AssistantContextProvider {

	private static final int MAX_EXPLICIT_IDS = 100;

	private static final ProviderLimits DEFAULT_LIMITS = new ProviderLimits(20, 12000, 3000);

	private final InformationItemMapper itemMapper;

	private final InformationEvidenceMapper evidenceMapper;

	public InformationAssistantContextProvider(InformationItemMapper itemMapper,
			InformationEvidenceMapper evidenceMapper) {
		this.itemMapper = itemMapper;
		this.evidenceMapper = evidenceMapper;
	}

	@Override
	public ProviderDescriptor describe() {
		return new ProviderDescriptor(AssistantContextContract.VERSION, ProviderId.INFORMATION, "资讯信息",
				"已发布资讯、信息条目和相关证据", "information",
				new ProviderCapabilities(true, true, true, true, true, false, false,
						List.of("INFORMATION_ITEM", "INFORMATION_EVIDENCE"), List.of()),
				DEFAULT_LIMITS);
	}

	@Override
	public ProviderResult query(ProviderQuery request) {
		Validation validation = validate(request);
		if (!validation.valid()) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(), validation.filtered(), false,
					new ProviderWarning(validation.errorCode(), validation.warning()));
		}
		long orgId = Long.parseLong(request.identity().currentOrgId());
		long userId = Long.parseLong(request.identity().userId());
		int maxItems = Math.min(request.limits().maxItems(), DEFAULT_LIMITS.maxItems());
		int maxCharacters = Math.min(request.limits().maxCharacters(), DEFAULT_LIMITS.maxCharacters());
		int queryLimit = validation.explicitIds().isEmpty() ? maxItems + 1
				: Math.max(maxItems + 1, validation.explicitIds().size() + 1);
		List<InformationDetailVO> items = itemMapper.selectContextItems(Long.valueOf(request.identity().tenantId()),
				orgId, userId, request.query(), local(request.scope().from()), local(request.scope().to()),
				validation.explicitIds(), queryLimit);
		long matchedExplicitIds = items.stream()
			.map(InformationDetailVO::getId)
			.distinct()
			.filter(validation.explicitIds()::contains)
			.count();
		if (!validation.explicitIds().isEmpty() && matchedExplicitIds < validation.explicitIds().size()) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(),
					Math.toIntExact(validation.explicitIds().size() - matchedExplicitIds),
					false, new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, "显式资讯不存在或未投递给当前组织。"));
		}
		boolean truncated = items.size() > maxItems;
		List<InformationDetailVO> bounded = items.stream().limit(maxItems).toList();
		List<Long> itemIds = bounded.stream().map(InformationDetailVO::getId).toList();
		Map<Long, List<InformationEvidence>> evidenceByItem = itemIds.isEmpty() ? Map.of()
				: evidenceMapper
					.selectList(Wrappers.<InformationEvidence>lambdaQuery()
						.in(InformationEvidence::getItemId, itemIds)
						.orderByDesc(InformationEvidence::getCapturedAt))
					.stream()
					.collect(java.util.stream.Collectors.groupingBy(InformationEvidence::getItemId));
		List<ContextFragment> fragments = new ArrayList<>();
		int remainingCharacters = maxCharacters;
		for (InformationDetailVO item : bounded) {
			if (remainingCharacters <= 0 || fragments.size() >= maxItems) {
				truncated = true;
				break;
			}
			String summary = excerpt(item.getSummary() == null ? item.getContent() : item.getSummary(),
					remainingCharacters);
			remainingCharacters -= summary.length();
			fragments.add(new ContextFragment("information-summary:" + item.getId(), ProviderId.INFORMATION,
					"INFORMATION_ITEM", String.valueOf(item.getId()), version(item), FactType.AI_SUMMARY,
					item.getTitle(), summary, instant(item.getPublishedAt()), instant(item.getDeliveredAt()),
					hash(item.getTitle() + summary), citation(item.getId(), version(item)), DataClassification.SENSITIVE,
					0));
			for (InformationEvidence evidence : evidenceByItem.getOrDefault(item.getId(), List.of())
				.stream()
				.limit(2)
				.toList()) {
				if (remainingCharacters <= 0 || fragments.size() >= maxItems) {
					truncated = true;
					break;
				}
				String evidenceExcerpt = excerpt(evidence.getRawContent(), Math.min(800, remainingCharacters));
				remainingCharacters -= evidenceExcerpt.length();
				fragments.add(new ContextFragment("information-evidence:" + evidence.getId(), ProviderId.INFORMATION,
						"INFORMATION_EVIDENCE", String.valueOf(evidence.getId()), evidence.getContentHash(),
						FactType.SOURCE_EVIDENCE, evidence.getTitle(), evidenceExcerpt,
						instant(evidence.getCapturedAt()), instant(evidence.getCreateTime()),
						"sha256:" + evidence.getContentHash(), evidenceCitation(evidence, item.getId()),
						DataClassification.SENSITIVE, 0));
			}
		}
		return result(fragments.isEmpty() ? ProviderStatus.NO_MATCH : ProviderStatus.OK, fragments, 0, truncated, null);
	}

	@Override
	public CitationResolution resolve(CitationResolveRequest request) {
		if (request == null || request.identity() == null || request.citationId() == null
				|| request.identity().currentOrgId() == null || !ProviderId.INFORMATION.equals(request.citation().provider())
				|| !"ASSISTANT_CONTEXT".equals(request.identity().purpose())) {
			return forbidden(request == null ? null : request.citationId());
		}
		String[] parts = request.citationId().split(":");
		if ((parts.length != 2 || !"information".equals(parts[0]))
				&& (parts.length != 3 || !"information-evidence".equals(parts[0]))) {
			return forbidden(request.citationId());
		}
		try {
			long sourceId = Long.parseLong(parts[1]);
			long itemId = parts.length == 2 ? sourceId : Long.parseLong(parts[2]);
			String expectedSourceType = parts.length == 2 ? "INFORMATION_ITEM" : "INFORMATION_EVIDENCE";
			if (!expectedSourceType.equals(request.citation().sourceType())
					|| !String.valueOf(sourceId).equals(request.citation().sourceId())) {
				return forbidden(request.citationId());
			}
			InformationDetailVO item = itemMapper.selectPortalDetail(itemId,
					Long.parseLong(request.identity().tenantId()),
					Long.parseLong(request.identity().currentOrgId()), Long.parseLong(request.identity().userId()));
			if (item == null) {
				return forbidden(request.citationId());
			}
			Citation citation = citation(itemId, version(item));
			if (parts.length == 3) {
				InformationEvidence evidence = evidenceMapper.selectById(sourceId);
				if (evidence == null || !Long.valueOf(itemId).equals(evidence.getItemId())) {
					return forbidden(request.citationId());
				}
				citation = evidenceCitation(evidence, itemId);
			}
			return new CitationResolution(request.citationId(), ProviderStatus.OK, citation, null);
		}
		catch (NumberFormatException ex) {
			return forbidden(request.citationId());
		}
	}

	private Validation validate(ProviderQuery request) {
		if (request == null || !AssistantContextContract.VERSION.equals(request.contractVersion())
				|| request.identity() == null || request.identity().tenantId() == null
				|| request.identity().userId() == null || request.identity().currentOrgId() == null
				|| !"ASSISTANT_CONTEXT".equals(request.identity().purpose())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "身份或契约版本无效。", 0);
		}
		if (!request.identity().accessibleOrgIds().contains(request.identity().currentOrgId())) {
			return Validation.invalid(ErrorCode.FORBIDDEN_SCOPE, "当前组织不在身份授权范围内。", 0);
		}
		if (request.scope() == null || request.limits() == null || !request.scope().filters().isEmpty()) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "资讯 Provider 不接受自定义过滤字段。", 0);
		}
		if (request.scope().from() != null && request.scope().to() != null
				&& request.scope().from().isAfter(request.scope().to())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "时间范围无效。", 0);
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

	private ProviderResult result(ProviderStatus status, List<ContextFragment> fragments, int permissionFiltered,
			boolean truncated, ProviderWarning warning) {
		return new ProviderResult(AssistantContextContract.VERSION, ProviderId.INFORMATION, status, Instant.now(),
				fragments.size(), permissionFiltered, truncated, fragments,
				warning == null ? List.of() : List.of(warning));
	}

	private Citation citation(long itemId, String sourceVersion) {
		return new Citation("information:" + itemId, ProviderId.INFORMATION, "INFORMATION_ITEM", String.valueOf(itemId),
				sourceVersion, "INTERNAL", "InformationDetail", Map.of("id", String.valueOf(itemId)));
	}

	private Citation evidenceCitation(InformationEvidence evidence, long itemId) {
		return new Citation("information-evidence:" + evidence.getId() + ":" + itemId, ProviderId.INFORMATION,
				"INFORMATION_EVIDENCE", String.valueOf(evidence.getId()), evidence.getContentHash(), "INTERNAL",
				"InformationDetail", Map.of("id", String.valueOf(itemId)));
	}

	private CitationResolution forbidden(String citationId) {
		return new CitationResolution(citationId, ProviderStatus.FORBIDDEN_SCOPE, null,
				new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, "资讯引用不存在或未投递给当前组织。"));
	}

	private String excerpt(String value, int limit) {
		String safe = value == null ? "" : value;
		return safe.length() <= limit ? safe : safe.substring(0, Math.max(0, limit));
	}

	private String version(InformationDetailVO item) {
		return hash(item.getTitle() + "|" + item.getPublishedAt() + "|" + item.getDeliveredAt());
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

	private java.time.LocalDateTime local(Instant value) {
		return value == null ? null : java.time.LocalDateTime.ofInstant(value, ZoneOffset.UTC);
	}

	private Instant instant(java.time.LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	private record Validation(boolean valid, List<Long> explicitIds, int filtered, ErrorCode errorCode,
			String warning) {

		private static Validation valid(List<Long> ids) {
			return new Validation(true, ids, 0, null, null);
		}

		private static Validation invalid(ErrorCode code, String warning, int filtered) {
			return new Validation(false, List.of(), filtered, code, warning);
		}
	}

}
