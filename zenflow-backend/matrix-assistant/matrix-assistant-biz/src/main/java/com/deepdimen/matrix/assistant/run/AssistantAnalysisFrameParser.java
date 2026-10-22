package com.deepdimen.matrix.assistant.run;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Incremental framing only. Consumers MUST validate evidence and permissions before
 * rendering.
 */
public final class AssistantAnalysisFrameParser {

	public static final String PROTOCOL = "assistant-analysis-stream/v1";

	private static final int MAX_LINE_BYTES = 65536;

	private static final int MAX_TOTAL_BYTES = 1048576;

	private final ObjectReader reader;

	private final String runId;

	private final Consumer<JsonNode> consumer;

	private final StringBuilder pending = new StringBuilder();

	private final StringBuilder legacy = new StringBuilder();

	private final Set<String> sectionIds = new HashSet<>();

	private Mode mode = Mode.UNDECIDED;

	private int totalBytes;

	private int claimCount;

	private int plannedSections;

	private boolean summarized;

	private boolean finished;

	public enum Mode {

		UNDECIDED, LEGACY, STREAM, COMPLETE, FAILED

	}

	public AssistantAnalysisFrameParser(ObjectMapper mapper, String runId, Consumer<JsonNode> consumer) {
		this.reader = mapper.reader()
			.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
		this.runId = java.util.Objects.requireNonNull(runId);
		this.consumer = java.util.Objects.requireNonNull(consumer);
	}

	public Mode mode() {
		return mode;
	}

	public void accept(String chunk) {
		if (finished || mode == Mode.FAILED)
			throw invalid("CLOSED");
		if (chunk == null || chunk.isEmpty())
			return;
		if (chunk.length() > MAX_TOTAL_BYTES
				|| totalBytes + chunk.getBytes(StandardCharsets.UTF_8).length > MAX_TOTAL_BYTES)
			throw invalid("TOTAL_LIMIT");
		totalBytes += chunk.getBytes(StandardCharsets.UTF_8).length;
		if (mode == Mode.LEGACY) {
			legacy.append(chunk);
			return;
		}
		if (mode == Mode.UNDECIDED)
			legacy.append(chunk);
		for (int i = 0; i < chunk.length(); i++) {
			char value = chunk.charAt(i);
			if (value == '\n') {
				consumeLine();
				if (mode == Mode.LEGACY)
					return;
			}
			else {
				pending.append(value);
				if (pending.length() > MAX_LINE_BYTES)
					throw invalid("LINE_LIMIT");
			}
		}
	}

	/**
	 * Requires newline-terminated protocol frames; legacy JSON is returned untouched for
	 * the old validator.
	 */
	public String finish() {
		if (finished || mode == Mode.FAILED)
			throw invalid("CLOSED");
		finished = true;
		if (mode == Mode.UNDECIDED)
			mode = Mode.LEGACY;
		if (mode == Mode.LEGACY)
			return legacy.toString();
		if (!pending.toString().isBlank() || mode != Mode.COMPLETE)
			throw invalid("TRUNCATED");
		return null;
	}

	private void consumeLine() {
		String line = pending.toString();
		pending.setLength(0);
		if (line.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES)
			throw invalid("LINE_LIMIT");
		if (line.isBlank())
			return;
		if (mode == Mode.COMPLETE)
			throw invalid("AFTER_FINAL");
		JsonNode frame;
		try {
			frame = reader.readTree(line);
		}
		catch (IOException ex) {
			if (mode == Mode.UNDECIDED) {
				mode = Mode.LEGACY;
				return;
			}
			throw invalid("JSON_INVALID");
		}
		if (mode == Mode.UNDECIDED) {
			if (!validMeta(frame)) {
				mode = Mode.LEGACY;
				return;
			}
			plannedSections = frame.path("plannedSections").intValue();
			mode = Mode.STREAM;
			legacy.setLength(0);
		}
		else {
			validateEnvelope(frame);
		}
		try {
			consumer.accept(frame);
		}
		catch (RuntimeException ex) {
			mode = Mode.FAILED;
			throw ex;
		}
	}

	private boolean validMeta(JsonNode frame) {
		return fields(frame, Set.of("type", "protocol", "runId", "analysisType", "plannedSections"))
				&& "analysis_meta".equals(frame.path("type").asText())
				&& PROTOCOL.equals(frame.path("protocol").asText()) && runId.equals(frame.path("runId").asText())
				&& text(frame.get("analysisType")) && frame.path("plannedSections").isIntegralNumber()
				&& frame.path("plannedSections").canConvertToInt() && frame.path("plannedSections").intValue() >= 0
				&& frame.path("plannedSections").intValue() <= 11;
	}

	private void validateEnvelope(JsonNode frame) {
		String type = frame.path("type").asText();
		switch (type) {
			case "section" -> {
				if (summarized || !fields(frame, Set.of("type", "section", "claims"))
						|| !frame.path("section").isObject() || !frame.path("claims").isArray()
						|| frame.path("claims").isEmpty() || frame.path("claims").size() > 100
						|| !text(frame.path("section").get("sectionId")))
					throw invalid("SECTION_INVALID");
				String id = frame.path("section").path("sectionId").textValue();
				if ("analysis-summary".equals(id) || !sectionIds.add(id) || sectionIds.size() > plannedSections)
					throw invalid("SECTION_ORDER");
				claimCount += frame.path("claims").size();
				if (claimCount >= 100)
					throw invalid("CLAIM_LIMIT");
			}
			case "summary" -> {
				if (summarized || sectionIds.size() != plannedSections
						|| !fields(frame, Set.of("type", "claim", "warnings", "sourceCoverage"))
						|| !frame.path("claim").isObject() || !frame.path("warnings").isArray()
						|| frame.path("warnings").size() > 20 || !frame.path("sourceCoverage").isObject())
					throw invalid("SUMMARY_ORDER");
				summarized = true;
				claimCount++;
			}
			case "final" -> {
				if (!summarized
						|| !fields(frame, Set.of("type", "resultDigest", "sectionCount", "claimCount", "complete"))
						|| !(frame.path("resultDigest").isNull() || (frame.path("resultDigest").isTextual()
								&& frame.path("resultDigest").textValue().matches("[0-9a-f]{64}")))
						|| !frame.path("sectionCount").isIntegralNumber()
						|| !frame.path("sectionCount").canConvertToInt()
						|| frame.path("sectionCount").intValue() != sectionIds.size()
						|| !frame.path("claimCount").isIntegralNumber() || !frame.path("claimCount").canConvertToInt()
						|| frame.path("claimCount").intValue() != claimCount || !frame.path("complete").isBoolean()
						|| !frame.path("complete").booleanValue())
					throw invalid("FINAL_INVALID");
				mode = Mode.COMPLETE;
			}
			case "error" -> {
				if (!fields(frame, Set.of("type", "code", "recoverable")) || !text(frame.get("code"))
						|| !frame.path("recoverable").isBoolean())
					throw invalid("ERROR_INVALID");
				// Model error text is never accepted as a user-facing message.
				throw invalid("MODEL_ERROR");
			}
			default -> throw invalid("TYPE_INVALID");
		}
	}

	private boolean fields(JsonNode frame, Set<String> allowed) {
		if (frame == null || !frame.isObject() || frame.size() != allowed.size())
			return false;
		var names = frame.fieldNames();
		while (names.hasNext())
			if (!allowed.contains(names.next()))
				return false;
		return true;
	}

	private boolean text(JsonNode value) {
		return value != null && value.isTextual() && !value.textValue().isBlank() && value.textValue().length() <= 256;
	}

	private IllegalArgumentException invalid(String code) {
		mode = Mode.FAILED;
		return new IllegalArgumentException("ANALYSIS_STREAM_INVALID:" + code);
	}

}
