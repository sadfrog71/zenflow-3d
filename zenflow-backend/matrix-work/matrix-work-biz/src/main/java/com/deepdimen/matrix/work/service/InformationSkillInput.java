package com.deepdimen.matrix.work.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable evidence/date snapshot shared by analysis and its dependent review. */
public record InformationSkillInput(String title, String url, String evidence, LocalDate reviewDate) {
	public InformationSkillInput {
		Objects.requireNonNull(reviewDate, "reviewDate");
		evidence = evidence == null ? "" : evidence.substring(0, Math.min(evidence.length(), 12000));
	}

	public String analysisPrompt() {
		return dateContext() + "标题：" + title + "\nURL：" + url + "\n证据：\n" + evidence;
	}

	public String reviewPrompt(String persistedAnalysisJson) {
		return dateContext() + "候选结果：" + Objects.requireNonNull(persistedAnalysisJson) + "\n原始证据：\n" + evidence;
	}

	public String snapshot(ObjectMapper json) {
		var node = json.createObjectNode();
		node.put("title", title);
		node.put("url", url);
		node.put("evidence", evidence);
		node.put("reviewDate", reviewDate.toString());
		return node.toString();
	}

	public static InformationSkillInput restore(ObjectMapper json, String snapshot) {
		try {
			JsonNode node = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(snapshot);
			if (node == null || !node.isObject() || node.size() != 4 || !node.has("title") || !node.has("url")
					|| !textOrNull(node.get("title")) || !textOrNull(node.get("url"))
					|| !node.path("evidence").isTextual() || node.path("evidence").textValue().length() > 12000
					|| !node.path("reviewDate").isTextual())
				throw new IllegalArgumentException("INFORMATION_INPUT_INVALID");
			return new InformationSkillInput(node.get("title").isNull() ? null : node.get("title").textValue(),
					node.get("url").isNull() ? null : node.get("url").textValue(), node.get("evidence").textValue(),
					LocalDate.parse(node.get("reviewDate").textValue()));
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("INFORMATION_INPUT_INVALID", failure);
		}
	}

	private static boolean textOrNull(JsonNode node) {
		return node != null && (node.isNull() || node.isTextual());
	}

	private String dateContext() {
		return "当前日期（北京时间）：" + reviewDate + "\n" + "日期判断规则：早于或等于当前日期的日期不是未来日期。\n";
	}
}
