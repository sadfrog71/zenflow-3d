package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.api.dto.InformationPublishRequest;
import com.deepdimen.matrix.work.api.entity.InformationEvidence;
import com.deepdimen.matrix.work.api.entity.InformationItem;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.api.entity.InformationRun;
import com.deepdimen.matrix.work.mapper.InformationEvidenceMapper;
import com.deepdimen.matrix.work.mapper.InformationItemMapper;
import com.deepdimen.matrix.work.mapper.InformationPolicyMapper;
import com.deepdimen.matrix.work.mapper.InformationRunMapper;
import com.deepdimen.matrix.work.provider.TavilyDiscoveryProvider;
import com.deepdimen.matrix.work.provider.TavilyDiscoveryProvider.DiscoveredDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InformationCollectionService {

	private static final int MAX_CONTENT_LENGTH = 60000;

	private static final List<String> WATER_TERMS = List.of("水务", "供水", "二次供水", "二供", "居民用水", "生活用水", "水厂", "净水厂", "泵站",
			"水源地", "水源保护", "水处理", "污水", "排水", "管网", "水质", "水生态", "水生生物", "水环境", "河湖", "water supply", "water utility",
			"drinking water", "water treatment", "wastewater");

	private static final List<String> AI_TERMS = List.of("人工智能", "机器学习", "深度学习", "强化学习", "大模型", "行业大模型", "智能算法", "智能体",
			"水务ai", "水务 fde", "水务专家", "智能加药", "精准加药", "智慧曝气", "水质预测", "泵组优化", "预测性维护", "数字孪生", "数字水厂", "边缘ai", "aiot",
			"计算机视觉", "视觉识别", "具身智能", "机器狗", "机器人", "无人机", "无人船", "无人割草船", "水生生物 ai", "ai", "artificial intelligence",
			"machine learning", "reinforcement learning", "computer vision", "digital twin", "embodied ai", "robot",
			"drone", "uav");

	private final InformationPolicyMapper policyMapper;

	private final InformationRunMapper runMapper;

	private final InformationItemMapper itemMapper;

	private final InformationEvidenceMapper evidenceMapper;

	private final TavilyDiscoveryProvider discoveryProvider;

	private final InformationReviewService reviewService;

	private final InformationAdminService adminService;

	public InformationRun runPolicy(Long policyId) {
		requireTenantContext();
		InformationPolicy policy = policyMapper.selectById(policyId);
		if (policy == null || !Integer.valueOf(1).equals(policy.getEnabled())) {
			throw new IllegalArgumentException("资讯策略不存在或未启用");
		}

		InformationRun run = createRun(policy);
		try {
			List<DiscoveredDocument> documents = discoveryProvider.discover(policy);
			run.setFoundCount(documents.size());
			int extracted = 0;
			int approved = 0;
			int published = 0;
			int processLimit = Math.max(1,
					Math.min(50, policy.getDailyLimit() == null ? 30 : policy.getDailyLimit() * 3));
			for (DiscoveredDocument document : documents.stream().limit(processLimit).toList()) {
				if (!isRelevant(document)) {
					continue;
				}
				extracted++;
				String canonicalUrl = canonicalUrl(document.url());
				String sourceHash = sha256(canonicalUrl);
				InformationItem existingItem = itemMapper.selectOne(Wrappers.<InformationItem>lambdaQuery()
					.eq(InformationItem::getContentHash, sourceHash)
					.last("LIMIT 1"));
				if (existingItem != null) {
					if ("APPROVED".equals(existingItem.getReviewStatus())) {
						published += publishToOrganization(existingItem.getId(), policy.getOrgId());
					}
					continue;
				}

				String content = limit(document.content(), MAX_CONTENT_LENGTH);
				InformationReviewService.ReviewResult review = "LEGACY".equals(policy.getSkillManagementMode())
						? reviewService.review(policy, document.title(), canonicalUrl, content)
						: reviewService.review(policy, run.getId(), document.title(), canonicalUrl, content);
				InformationItem item = createItem(policy, document, canonicalUrl, content, sourceHash, review);
				try {
					itemMapper.insert(item);
				}
				catch (DuplicateKeyException duplicate) {
					// Two manual/scheduled runs can discover the same item at the same
					// time.
					// The tenant+content_hash unique key is the final idempotency guard;
					// treat the losing insert as a duplicate instead of failing the whole
					// run.
					InformationItem concurrentItem = itemMapper.selectOne(Wrappers.<InformationItem>lambdaQuery()
						.eq(InformationItem::getContentHash, sourceHash)
						.last("LIMIT 1"));
					if (concurrentItem != null && "APPROVED".equals(concurrentItem.getReviewStatus())) {
						published += publishToOrganization(concurrentItem.getId(), policy.getOrgId());
					}
					log.info("跳过并发重复的AI资讯 itemHash={}, policyId={}", sourceHash, policyId);
					continue;
				}
				saveEvidence(run.getId(), item.getId(), canonicalUrl, document.title(), content, sourceHash);
				if (review.approved()) {
					approved++;
					published += publishToOrganization(item.getId(), policy.getOrgId());
				}
			}
			run.setExtractedCount(extracted);
			run.setApprovedCount(approved);
			run.setPublishedCount(published);
			run.setStatus("SUCCESS");
		}
		catch (Exception ex) {
			run.setStatus("FAILED");
			run.setErrorMessage(limit(ex.getMessage(), 1000));
			log.error("AI资讯采集失败 policyId={}, runId={}", policyId, run.getId(), ex);
		}
		run.setFinishedAt(LocalDateTime.now());
		runMapper.updateById(run);
		return run;
	}

	private void requireTenantContext() {
		if (TenantContextHolder.getTenantId() == null) {
			throw new IllegalStateException("AI资讯采集缺少租户上下文");
		}
	}

	private int publishToOrganization(Long itemId, Long orgId) {
		InformationPublishRequest publishRequest = new InformationPublishRequest();
		publishRequest.setItemId(itemId);
		publishRequest.setOrgIds(List.of(orgId));
		Object publishedValue = adminService.publish(publishRequest).get("published");
		return publishedValue instanceof Number number ? number.intValue() : 0;
	}

	private InformationRun createRun(InformationPolicy policy) {
		InformationRun run = new InformationRun();
		run.setPolicyId(policy.getId());
		run.setOrgId(policy.getOrgId());
		run.setStatus("RUNNING");
		run.setFoundCount(0);
		run.setExtractedCount(0);
		run.setApprovedCount(0);
		run.setPublishedCount(0);
		run.setStartedAt(LocalDateTime.now());
		run.setCreateTime(LocalDateTime.now());
		runMapper.insert(run);
		return run;
	}

	private InformationItem createItem(InformationPolicy policy, DiscoveredDocument document, String canonicalUrl,
			String content, String sourceHash, InformationReviewService.ReviewResult review) {
		InformationItem item = new InformationItem();
		item.setSourcePolicyId(policy.getId());
		item.setContentType(review.approved() ? review.contentType() : "CASE");
		item.setTitle(review.approved() && !review.title().isBlank() ? review.title() : limit(document.title(), 500));
		item.setSourceName(sourceName(canonicalUrl));
		item.setSourceUrl(limit(document.url(), 2000));
		item.setCanonicalUrl(limit(canonicalUrl, 2000));
		item.setSummary(review.approved() ? review.summary() : limit(content.replaceAll("\\s+", " "), 500));
		item.setContent(content);
		item.setTags(review.tags());
		item.setPublishedAt(parsePublishedAt(document.publishedDate()));
		item.setCollectedAt(LocalDateTime.now());
		item.setAiConfidence(BigDecimal.valueOf(review.confidence()));
		item.setReviewStatus(review.approved() ? "APPROVED" : "QUARANTINED");
		item.setReviewReason(review.reason());
		item.setPublishStatus("DRAFT");
		item.setContentHash(sourceHash);
		item.setCreateTime(LocalDateTime.now());
		item.setUpdateTime(LocalDateTime.now());
		item.setDelFlag("0");
		return item;
	}

	private void saveEvidence(Long runId, Long itemId, String sourceUrl, String title, String content,
			String sourceHash) {
		InformationEvidence evidence = new InformationEvidence();
		evidence.setRunId(runId);
		evidence.setItemId(itemId);
		evidence.setSourceUrl(limit(sourceUrl, 2000));
		evidence.setTitle(limit(title, 500));
		evidence.setRawContent(content);
		evidence.setContentHash(sourceHash);
		evidence.setCapturedAt(LocalDateTime.now());
		evidence.setCreateTime(LocalDateTime.now());
		evidenceMapper.insert(evidence);
	}

	private boolean isRelevant(DiscoveredDocument document) {
		String text = (document.title() + " " + document.content()).toLowerCase();
		return WATER_TERMS.stream().map(String::toLowerCase).anyMatch(text::contains)
				&& AI_TERMS.stream().map(String::toLowerCase).anyMatch(text::contains);
	}

	private String canonicalUrl(String value) {
		URI uri = URI.create(value).normalize();
		String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase();
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("来源URL缺少有效域名");
		}
		String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
		return URI.create(scheme + "://" + host.toLowerCase() + path).toString();
	}

	private String sourceName(String value) {
		String host = URI.create(value).getHost();
		return host == null ? "未知来源" : host;
	}

	private LocalDateTime parsePublishedAt(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toLocalDateTime();
		}
		catch (DateTimeParseException ignored) {
			try {
				return LocalDateTime.parse(value);
			}
			catch (DateTimeParseException ignoredAgain) {
				return null;
			}
		}
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException("内容哈希计算失败", ex);
		}
	}

	private static String limit(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

}
