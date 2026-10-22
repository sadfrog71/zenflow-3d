package com.deepdimen.matrix.knowledge.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

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
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceTextSearchDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.service.AiSliceService;

@Component("knowledgeAssistantContextProvider")
public class KnowledgeAssistantContextProvider implements AssistantContextProvider {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[a-z0-9][a-z0-9._-]{1,31}");

	private static final List<String> QUERY_FILLERS = List.of("当前知识库", "知识库", "请从", "请在", "请帮我", "帮我", "帮忙", "检索", "搜索",
			"查询", "并给出", "给出", "提供", "文档引用", "引用", "根据", "基于");

	private final KnowledgeDatasetAccessService accessService;

	private final AiSliceService sliceService;

	private final AiSliceMapper sliceMapper;

	private final AiDocumentMapper documentMapper;

	public KnowledgeAssistantContextProvider(KnowledgeDatasetAccessService accessService, AiSliceService sliceService,
			AiSliceMapper sliceMapper, AiDocumentMapper documentMapper) {
		this.accessService = accessService;
		this.sliceService = sliceService;
		this.sliceMapper = sliceMapper;
		this.documentMapper = documentMapper;
	}

	@Override
	public ProviderDescriptor describe() {
		return new ProviderDescriptor(AssistantContextContract.VERSION, ProviderId.KNOWLEDGE, "企业知识库", "当前有权限访问的知识库文档",
				"knowledge",
				new ProviderCapabilities(true, true, true, false, true, true, false,
						List.of("DATASET", "DOCUMENT", "SLICE"), List.of("datasetId")),
				new ProviderLimits(20, 12000, 10000));
	}

	@Override
	public ProviderResult query(ProviderQuery request) {
		return query(request, validate(request, "ASSISTANT_CONTEXT"));
	}

	/**
	 * Internal market entry. Caller must supply a server-authorized identity and
	 * agent-bound dataset IDs.
	 */
	public ProviderResult queryMarketSkill(ProviderQuery request) {
		return queryMarketSkill(request, null);
	}

	/**
	 * Java-only fixed retrieval hook; normal context queries retain their original path.
	 */
	public ProviderResult queryMarketSkill(ProviderQuery request,
			BiFunction<AiDatasetEntity, AiSliceReCallRequestDTO, List<AiSliceReCallDTO>> retrieval) {
		Validation validation = validate(request, "MARKET_SKILL");
		if (!validation.valid() || validation.datasetIds().isEmpty() || validation.datasetIds().size() > 20
				|| validation.datasetIds().stream().anyMatch(id -> id <= 0)) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(), 0, false,
					new ProviderWarning(ErrorCode.INVALID_SCOPE, "市场 Skill 必须指定已授权的知识库范围。"));
		}
		ProviderQuery bounded = new ProviderQuery(request.contractVersion(), request.requestId(), request.runId(),
				request.correlationId(), request.identity(), request.query(), request.scope(),
				new ProviderLimits(Math.min(20, request.limits().maxItems()),
						Math.min(12000, request.limits().maxCharacters()), request.limits().timeoutMs()),
				request.queryTerms());
		ProviderResult evidence = query(bounded, validation, retrieval);
		if (evidence.fragments().isEmpty())
			return evidence;
		// Permissions may change during retrieval. Keep the whole evidence set out of
		// generation on revocation.
		Set<Long> allowed = accessService.accessibleDatasets(request.identity(), validation.datasetIds())
			.stream()
			.map(AiDatasetEntity::getId)
			.collect(java.util.stream.Collectors.toSet());
		if (!allowed.containsAll(validation.datasetIds())) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(), evidence.fragments().size(), false,
					new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, "知识库访问权限已变化，请重新发起请求。"));
		}
		for (ContextFragment fragment : evidence.fragments()) {
			AiSliceEntity current = sliceMapper.selectById(Long.valueOf(fragment.sourceId()));
			AiDocumentEntity document = current == null ? null : documentMapper.selectById(current.getDocumentId());
			if (document == null || !allowed.contains(document.getDatasetId())
					|| !version(current).equals(fragment.sourceVersion())
					|| !hash(current.getContent()).equals(fragment.contentHash())) {
				return ProviderResult.unavailable(ProviderId.KNOWLEDGE, "知识来源在检索期间发生变化，请重新发起请求。");
			}
		}
		return evidence;
	}

	private ProviderResult query(ProviderQuery request, Validation validation) {
		return query(request, validation, null);
	}

	private ProviderResult query(ProviderQuery request, Validation validation,
			BiFunction<AiDatasetEntity, AiSliceReCallRequestDTO, List<AiSliceReCallDTO>> retrieval) {
		if (!validation.valid()) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(), validation.filtered(), false,
					new ProviderWarning(validation.code(), validation.warning()));
		}
		List<AiDatasetEntity> datasets = accessService.accessibleDatasets(request.identity(), validation.datasetIds());
		if (!validation.datasetIds().isEmpty() && datasets.size() != validation.datasetIds().size()) {
			return result(ProviderStatus.FORBIDDEN_SCOPE, List.of(), validation.datasetIds().size() - datasets.size(),
					false, new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, "知识库不存在或当前用户无权访问。"));
		}
		if (request.query() == null || request.query().isBlank()) {
			return result(ProviderStatus.NO_MATCH, List.of(), 0, false, null);
		}
		int maxItems = request.limits().maxItems();
		List<Recall> recalls = new ArrayList<>();
		List<ProviderWarning> warnings = new ArrayList<>();
		for (AiDatasetEntity dataset : datasets.stream().limit(20).toList()) {
			try {
				AiSliceReCallRequestDTO recallRequest = new AiSliceReCallRequestDTO();
				recallRequest.setDatasetId(dataset.getId());
				recallRequest.setContent(request.query());
				recallRequest.setFullTextSearch("1");
				recallRequest.setReRanking("1");
				recallRequest
					.setTopK(Math.max(1, Math.min(maxItems, dataset.getTopK() == null ? 5 : dataset.getTopK())));
				for (AiSliceReCallDTO item : retrieval == null ? sliceService.recall(recallRequest)
						: retrieval.apply(dataset, recallRequest)) {
					recalls.add(new Recall(dataset, item));
				}
			}
			catch (RuntimeException ex) {
				if (retrieval != null)
					throw ex;
				List<Recall> fallback;
				boolean fallbackFailed = false;
				try {
					fallback = fallbackRecalls(request, dataset, maxItems);
				}
				catch (RuntimeException fallbackError) {
					fallback = List.of();
					fallbackFailed = true;
				}
				recalls.addAll(fallback);
				String warning = fallbackFailed ? "知识库 “" + dataset.getName() + "” 的向量索引和数据库文本检索当前均不可用。"
						: fallback.isEmpty() ? "知识库 “" + dataset.getName() + "” 的向量索引当前不可用，数据库文本检索未命中。"
								: "知识库 “" + dataset.getName() + "” 的向量索引当前不可用，已降级为数据库文本检索。";
				warnings.add(new ProviderWarning(ErrorCode.VECTOR_INDEX_UNAVAILABLE, warning));
			}
		}
		recalls.sort(Comparator.comparingDouble(Recall::score).reversed());
		List<ContextFragment> fragments = new ArrayList<>();
		int remainingCharacters = request.limits().maxCharacters();
		for (Recall recall : recalls.stream().limit(maxItems).toList()) {
			AiSliceEntity slice = recall.slice() == null ? sliceMapper.selectById(recall.sliceId()) : recall.slice();
			if (slice == null || remainingCharacters <= 0) {
				continue;
			}
			AiDocumentEntity document = recall.document() == null ? documentMapper.selectById(slice.getDocumentId())
					: recall.document();
			if (document == null || !document.getDatasetId().equals(recall.dataset().getId())) {
				continue;
			}
			String excerpt = bound(slice.getContent(), remainingCharacters);
			remainingCharacters -= excerpt.length();
			fragments.add(new ContextFragment("knowledge-slice:" + slice.getId(), ProviderId.KNOWLEDGE, "SLICE",
					String.valueOf(slice.getId()), version(slice), FactType.SOURCE_EVIDENCE, document.getName(),
					excerpt, instant(slice.getCreateTime()), instant(slice.getUpdateTime()), hash(slice.getContent()),
					citation(slice.getId(), version(slice), document)));
		}
		boolean truncated = recalls.size() > maxItems || remainingCharacters <= 0;
		ProviderStatus status = fragments.isEmpty()
				? (warnings.isEmpty() ? ProviderStatus.NO_MATCH : ProviderStatus.UNAVAILABLE)
				: (warnings.isEmpty() ? ProviderStatus.OK : ProviderStatus.PARTIAL);
		return new ProviderResult(AssistantContextContract.VERSION, ProviderId.KNOWLEDGE, status, Instant.now(),
				fragments.size(), 0, truncated, fragments, warnings);
	}

	private List<Recall> fallbackRecalls(ProviderQuery request, AiDatasetEntity dataset, int maxItems) {
		List<String> terms = searchTerms(request.query());
		if (terms.isEmpty()) {
			return List.of();
		}
		long tenantId;
		try {
			tenantId = Long.parseLong(request.identity().tenantId());
		}
		catch (NumberFormatException ex) {
			return List.of();
		}
		int candidateLimit = Math.max(20, Math.min(200, maxItems * 10));
		return sliceMapper.searchByText(tenantId, dataset.getId(), terms, candidateLimit)
			.stream()
			.map(hit -> fallbackRecall(dataset, hit))
			.toList();
	}

	private Recall fallbackRecall(AiDatasetEntity dataset, AiSliceTextSearchDTO hit) {
		AiSliceEntity slice = new AiSliceEntity();
		slice.setId(hit.getSliceId());
		slice.setDocumentId(hit.getDocumentId());
		slice.setContent(hit.getContent());
		slice.setCreateTime(hit.getCreateTime());
		slice.setUpdateTime(hit.getUpdateTime());
		AiDocumentEntity document = new AiDocumentEntity();
		document.setId(hit.getDocumentId());
		document.setDatasetId(dataset.getId());
		document.setName(hit.getDocumentName());
		double rawScore = hit.getScore() == null ? 0 : Math.max(0, hit.getScore());
		return new Recall(dataset, hit.getSliceId(), 1 - (1 / (1 + rawScore)), slice, document);
	}

	private List<String> searchTerms(String query) {
		String normalized = String.valueOf(query).toLowerCase(Locale.ROOT);
		for (String filler : QUERY_FILLERS) {
			normalized = normalized.replace(filler, " ");
		}
		normalized = normalized.replace('的', ' ')
			.replace('，', ' ')
			.replace(',', ' ')
			.replace('。', ' ')
			.replace('？', ' ')
			.replace('?', ' ')
			.replace('：', ' ')
			.replace(':', ' ');
		Set<String> terms = new LinkedHashSet<>();
		Matcher matcher = TOKEN_PATTERN.matcher(normalized);
		while (matcher.find() && terms.size() < 24) {
			String token = matcher.group();
			terms.add(token);
			if (token.codePoints()
				.allMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
				String[] characters = token.codePoints().mapToObj(Character::toString).toArray(String[]::new);
				for (int length = Math.min(6, characters.length - 1); length >= 4 && terms.size() < 24; length--) {
					for (int start = 0; start + length <= characters.length && terms.size() < 24; start++) {
						terms.add(String.join("", java.util.Arrays.copyOfRange(characters, start, start + length)));
					}
				}
			}
		}
		return terms.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
	}

	@Override
	public CitationResolution resolve(CitationResolveRequest request) {
		if (request == null || request.identity() == null || request.citationId() == null) {
			return forbidden(request == null ? null : request.citationId());
		}
		String[] parts = request.citationId().split(":");
		if (parts.length != 2 || !"knowledge".equals(parts[0])) {
			return forbidden(request.citationId());
		}
		try {
			long sliceId = Long.parseLong(parts[1]);
			AiSliceEntity slice = sliceMapper.selectById(sliceId);
			AiDocumentEntity document = slice == null ? null : documentMapper.selectById(slice.getDocumentId());
			if (document == null || !accessService.canAccess(request.identity(), document.getDatasetId())) {
				return forbidden(request.citationId());
			}
			return new CitationResolution(request.citationId(), ProviderStatus.OK, request.citation(), null);
		}
		catch (NumberFormatException ex) {
			return forbidden(request.citationId());
		}
	}

	private Validation validate(ProviderQuery request, String purpose) {
		if (request == null || !AssistantContextContract.VERSION.equals(request.contractVersion())
				|| request.identity() == null || request.identity().tenantId() == null
				|| request.identity().userId() == null || !purpose.equals(request.identity().purpose())) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "身份或契约版本无效。", 0);
		}
		if (request.scope() == null || request.limits() == null
				|| request.scope().filters().keySet().stream().anyMatch(key -> !"datasetId".equals(key))) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "知识库范围参数无效。", 0);
		}
		List<String> rawIds = new ArrayList<>(request.scope().explicitObjectIds());
		String filterDatasetId = request.scope().filters().get("datasetId");
		if (filterDatasetId != null && !filterDatasetId.isBlank()) {
			rawIds.add(filterDatasetId);
		}
		try {
			return Validation.valid(rawIds.stream().map(Long::valueOf).distinct().toList());
		}
		catch (NumberFormatException ex) {
			return Validation.invalid(ErrorCode.INVALID_SCOPE, "datasetId 格式无效。", rawIds.size());
		}
	}

	private ProviderResult result(ProviderStatus status, List<ContextFragment> fragments, int permissionFiltered,
			boolean truncated, ProviderWarning warning) {
		return new ProviderResult(AssistantContextContract.VERSION, ProviderId.KNOWLEDGE, status, Instant.now(),
				fragments.size(), permissionFiltered, truncated, fragments,
				warning == null ? List.of() : List.of(warning));
	}

	private Citation citation(long sliceId, String sourceVersion, AiDocumentEntity document) {
		return new Citation("knowledge:" + sliceId, ProviderId.KNOWLEDGE, "SLICE", String.valueOf(sliceId),
				sourceVersion, "INTERNAL", "KnowledgeDocument",
				Map.of("datasetId", String.valueOf(document.getDatasetId()), "documentId",
						String.valueOf(document.getId()), "sliceId", String.valueOf(sliceId)));
	}

	private CitationResolution forbidden(String citationId) {
		return new CitationResolution(citationId, ProviderStatus.FORBIDDEN_SCOPE, null,
				new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, "知识引用不存在或当前用户无权访问。"));
	}

	private String bound(String value, int remaining) {
		String safe = value == null ? "" : value;
		return safe.length() <= remaining ? safe : safe.substring(0, remaining);
	}

	private String version(AiSliceEntity slice) {
		return String.valueOf(slice.getUpdateTime()) + ":" + hash(slice.getContent());
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

	private Instant instant(java.time.LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	private record Recall(AiDatasetEntity dataset, long sliceId, double score, AiSliceEntity slice,
			AiDocumentEntity document) {

		private Recall(AiDatasetEntity dataset, AiSliceReCallDTO item) {
			this(dataset, item.getSliceId(), parseScore(item.getScore()), null, null);
		}

		private static double parseScore(String value) {
			try {
				return Double.parseDouble(value);
			}
			catch (NumberFormatException ex) {
				return 0;
			}
		}
	}

	private record Validation(boolean valid, List<Long> datasetIds, int filtered, ErrorCode code, String warning) {

		private static Validation valid(List<Long> ids) {
			return new Validation(true, ids, 0, null, null);
		}

		private static Validation invalid(ErrorCode code, String warning, int filtered) {
			return new Validation(false, List.of(), filtered, code, warning);
		}
	}

}
