package com.deepdimen.matrix.assistant.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Converts known chart artifacts to the cross-client, data-only ChartSpec contract. */
final class AssistantWorkflowContentPolicy {

	private static final Pattern ARTIFACT_BLOCK = Pattern.compile("(?is)```artifact\\s*\\n(.*?)```");

	private static final Pattern CHART_SPEC_BLOCK = Pattern.compile("(?is)```chart-spec\\s*\\n(.*?)```");

	private static final Pattern CHART_TYPE = Pattern.compile("(?i)type\\s*:\\s*['\"](line|bar|pie|scatter)['\"]");

	private static final int MAX_SERIES_PER_CHART = 3;

	private static final int MAX_POINTS = 2000;

	private AssistantWorkflowContentPolicy() {
	}

	static String sanitizeForDisplay(ObjectMapper objectMapper, String value) {
		if (!StringUtils.hasText(value)) {
			return value;
		}
		Matcher matcher = ARTIFACT_BLOCK.matcher(value);
		StringBuffer converted = new StringBuffer();
		while (matcher.find()) {
			String replacement = String.join("\n\n", chartSpecs(objectMapper, matcher.group(1)));
			matcher.appendReplacement(converted, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(converted);
		return converted.toString()
			.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "")
			.replaceAll("(?is)<canvas\\b[^>]*>.*?</canvas>", "")
			.replaceAll("(?is)<style\\b[^>]*>.*?</style>", "")
			.trim();
	}

	static List<String> chartBlocks(ObjectMapper objectMapper, Map<String, Object> payload) {
		String content = sourceContent(objectMapper, payload);
		if (!StringUtils.hasText(content)) {
			return List.of();
		}
		String sanitized = sanitizeForDisplay(objectMapper, content);
		Set<String> result = new LinkedHashSet<>();
		Matcher matcher = CHART_SPEC_BLOCK.matcher(sanitized);
		while (matcher.find() && result.size() < 12) {
			result.add("```chart-spec\n" + matcher.group(1).trim() + "\n```");
		}
		return List.copyOf(result);
	}

	private static List<String> chartSpecs(ObjectMapper objectMapper, String artifactBody) {
		String configJson = chartConfigJson(artifactBody);
		if (!StringUtils.hasText(configJson)) {
			return List.of();
		}
		try {
			JsonNode config = objectMapper.readTree(configJson);
			JsonNode labels = config.path("labels");
			JsonNode datasets = config.path("datasets");
			Matcher type = CHART_TYPE.matcher(artifactBody);
			if (!labels.isArray() || !datasets.isArray() || datasets.isEmpty() || !type.find()) {
				return List.of();
			}
			String kind = type.group(1).toLowerCase();
			String title = config.path("title").asText("").trim();
			List<JsonNode> usableDatasets = new ArrayList<>();
			datasets.forEach(dataset -> {
				if (dataset.path("data").isArray() && StringUtils.hasText(dataset.path("label").asText())) {
					usableDatasets.add(dataset);
				}
			});
			if (usableDatasets.isEmpty()) {
				return List.of();
			}
			List<String> result = new ArrayList<>();
			int groupSize = "pie".equals(kind) ? 1 : MAX_SERIES_PER_CHART;
			int groups = (usableDatasets.size() + groupSize - 1) / groupSize;
			for (int start = 0; start < usableDatasets.size(); start += groupSize) {
				List<JsonNode> group = usableDatasets.subList(start,
						Math.min(start + groupSize, usableDatasets.size()));
				ObjectNode spec = objectMapper.createObjectNode();
				spec.put("version", 1);
				spec.put("kind", kind);
				if (StringUtils.hasText(title)) {
					spec.put("title", groups == 1 ? title : title + "（" + (start / MAX_SERIES_PER_CHART + 1) + "/"
							+ groups + "）");
				}
				ObjectNode dataset = spec.putObject("dataset");
				ArrayNode dimensions = dataset.putArray("dimensions");
				dimensions.add("时间/类别");
				Set<String> dimensionNames = new LinkedHashSet<>();
				for (JsonNode series : group) {
					String name = uniqueName(series.path("label").asText(), dimensionNames);
					dimensions.add(name);
				}
				ArrayNode source = dataset.putArray("source");
				int pointCount = Math.min(labels.size(), MAX_POINTS);
				for (int index = 0; index < pointCount; index++) {
					ArrayNode row = source.addArray();
					appendCell(row, labels.get(index));
					for (JsonNode series : group) {
						JsonNode values = series.path("data");
						appendCell(row, index < values.size() ? values.get(index) : null);
					}
				}
				ArrayNode series = spec.putArray("series");
				dimensionNames.forEach(name -> series.addObject().put("name", name));
				result.add("```chart-spec\n" + objectMapper.writeValueAsString(spec) + "\n```");
			}
			return result;
		}
		catch (JsonProcessingException ex) {
			return List.of();
		}
	}

	private static String chartConfigJson(String value) {
		int marker = value.indexOf("Config=");
		if (marker < 0) {
			marker = value.indexOf("Config =");
		}
		int start = marker < 0 ? -1 : value.indexOf('{', marker);
		if (start < 0) {
			return null;
		}
		int depth = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int index = start; index < value.length(); index++) {
			char current = value.charAt(index);
			if (quoted) {
				if (escaped) {
					escaped = false;
				}
				else if (current == '\\') {
					escaped = true;
				}
				else if (current == '"') {
					quoted = false;
				}
				continue;
			}
			if (current == '"') {
				quoted = true;
			}
			else if (current == '{') {
				depth++;
			}
			else if (current == '}' && --depth == 0) {
				return value.substring(start, index + 1);
			}
		}
		return null;
	}

	private static String sourceContent(ObjectMapper objectMapper, Map<String, Object> payload) {
		Object text = payload.get("text");
		if (text != null && StringUtils.hasText(String.valueOf(text))) {
			return String.valueOf(text);
		}
		Object structured = payload.get("result_json");
		if (structured instanceof String json && StringUtils.hasText(json)) {
			try {
				return objectMapper.readTree(json).path("summary").asText("");
			}
			catch (JsonProcessingException ignored) {
				return "";
			}
		}
		if (structured instanceof Map<?, ?> map) {
			Object summary = map.get("summary");
			return summary == null ? "" : String.valueOf(summary);
		}
		return "";
	}

	private static String uniqueName(String requested, Set<String> used) {
		String base = requested.trim();
		String candidate = base;
		int suffix = 2;
		while (!used.add(candidate)) {
			candidate = base + " " + suffix++;
		}
		return candidate;
	}

	private static void appendCell(ArrayNode row, JsonNode value) {
		if (value == null || value.isNull() || value.isMissingNode()) {
			row.addNull();
		}
		else if (value.isNumber()) {
			row.add(value.decimalValue());
		}
		else if (value.isTextual() || value.isBoolean()) {
			row.add(value.asText());
		}
		else {
			row.add(value.toString());
		}
	}

}
