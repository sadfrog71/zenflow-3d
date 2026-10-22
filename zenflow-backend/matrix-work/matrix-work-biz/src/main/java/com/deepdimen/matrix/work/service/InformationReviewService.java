package com.deepdimen.matrix.work.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InformationReviewService {

	private static final ZoneId REVIEW_ZONE = ZoneId.of("Asia/Shanghai");

	private final ObjectProvider<InformationReviewModel> modelProvider;

	private final ObjectMapper objectMapper;

	@org.springframework.beans.factory.annotation.Autowired
	private InformationSkillCollectionScope skillScope;

	@org.springframework.beans.factory.annotation.Autowired
	private InformationSkillReviewRunner skillRunner;

	public ReviewResult review(InformationPolicy policy, Long collectionRunId, String title, String url,
			String evidence) {
		if (policy == null || "LEGACY".equals(policy.getSkillManagementMode()))
			return review(policy, title, url, evidence);
		if (!"MANAGED".equals(policy.getSkillManagementMode()) || skillScope == null || skillRunner == null)
			return ReviewResult.quarantined("托管资讯审核暂不可用");
		try (var lease = skillScope.open(policy.getId(), collectionRunId)) {
			return skillRunner.review(new InformationSkillInput(title, url, evidence, LocalDate.now(REVIEW_ZONE)));
		}
		catch (InformationSkillReviewRunner.StageFailure failure) {
			return ReviewResult.quarantined("托管资讯" + ("ANALYZE".equals(failure.slot()) ? "分析" : "复核") + "未通过，已隔离");
		}
		catch (RuntimeException failure) {
			return ReviewResult.quarantined("托管资讯审核不可用或执行异常，已隔离");
		}
	}

	public ReviewResult review(InformationPolicy policy, String title, String url, String evidence) {
		if (policy != null && !"LEGACY".equals(policy.getSkillManagementMode()))
			return ReviewResult.quarantined("托管资讯审核须关联有效采集记录");
		try {
			InformationReviewModel provider = modelProvider.getIfAvailable();
			if (provider == null) {
				return ReviewResult.quarantined("未找到AI模型服务");
			}
			var input = new InformationSkillInput(title, url, evidence, LocalDate.now(REVIEW_ZONE));
			JsonNode analysis = parseJson(provider.chat("work-info-analyzer-" + UUID.randomUUID(),
					InformationSkillInstructions.ANALYZER_SYSTEM, input.analysisPrompt()));
			if (!analysis.path("eligible").asBoolean(false)) {
				return ReviewResult.quarantined(analysis.path("reason").asText("分析器判定不合格"));
			}
			String candidate = objectMapper.writeValueAsString(analysis);
			JsonNode review = parseJson(provider.chat("work-info-reviewer-" + UUID.randomUUID(),
					InformationSkillInstructions.REVIEWER_SYSTEM, input.reviewPrompt(candidate)));
			if (!review.path("approved").asBoolean(false)) {
				return ReviewResult.quarantined(review.path("reason").asText("复核未通过"));
			}
			String contentType = analysis.path("contentType").asText("CASE");
			if (!"CASE".equals(contentType) && !"PROJECT".equals(contentType)) {
				contentType = "CASE";
			}
			return new ReviewResult(true, contentType, analysis.path("title").asText(title),
					limit(analysis.path("summary").asText(""), 1000), limit(analysis.path("tags").asText(""), 500),
					analysis.path("confidence").asDouble(0), review.path("reason").asText(""));
		}
		catch (Exception ex) {
			return ReviewResult.quarantined("双阶段审核异常：" + limit(ex.getMessage(), 300));
		}
	}

	private JsonNode parseJson(String raw) throws Exception {
		String cleaned = raw == null ? "" : raw.trim();
		if (cleaned.startsWith("```")) {
			cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
		}
		return objectMapper.readTree(cleaned);
	}

	private static String limit(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	public record ReviewResult(boolean approved, String contentType, String title, String summary, String tags,
			double confidence, String reason) {
		static ReviewResult quarantined(String reason) {
			return new ReviewResult(false, "CASE", "", "", "", 0, reason);
		}
	}

}
