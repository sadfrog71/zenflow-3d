package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantAnalysisFrameParserTest {

	private final List<JsonNode> frames = new ArrayList<>();

	private final AssistantAnalysisFrameParser parser = new AssistantAnalysisFrameParser(new ObjectMapper(), "r",
			frames::add);

	private String meta(int count) {
		return "{\"type\":\"analysis_meta\",\"protocol\":\"assistant-analysis-stream/v1\",\"runId\":\"r\",\"analysisType\":\"business\",\"plannedSections\":"
				+ count + "}\n";
	}

	private String section(String id) {
		return "{\"type\":\"section\",\"section\":{\"sectionId\":\"" + id + "\"},\"claims\":[{}]}\n";
	}

	private String summary() {
		return "{\"type\":\"summary\",\"claim\":{},\"warnings\":[],\"sourceCoverage\":{}}\n";
	}

	private String end(int count) {
		return "{\"type\":\"final\",\"resultDigest\":\"" + "0".repeat(64) + "\",\"sectionCount\":" + count
				+ ",\"claimCount\":" + (count + 1) + ",\"complete\":true}\n";
	}

	@Test
	void emitsOnlyCompleteLinesAndAcceptsCrlf() {
		String value = meta(1).replace("\n", "\r\n");
		for (int i = 0; i < value.length() - 1; i++)
			parser.accept(value.substring(i, i + 1));
		assertTrue(frames.isEmpty());
		parser.accept("\n");
		assertEquals(1, frames.size());
		String block = section("s");
		parser.accept(block.substring(0, block.length() - 1));
		assertEquals(1, frames.size());
		parser.accept("\n" + summary() + end(1));
		assertEquals(4, frames.size());
		assertNull(parser.finish());
	}

	@Test
	void legacyIsReturnedExactlyAndNeverEmitted() {
		String legacy = "```json\n{\"contractVersion\":\"old\"}\n```";
		parser.accept(legacy.substring(0, 12));
		parser.accept(legacy.substring(12));
		assertEquals(legacy, parser.finish());
		assertTrue(frames.isEmpty());
	}

	@Test
	void unsupportedFirstFrameFallsBackWithoutLosingFollowingBytes() {
		String value = meta(1).replace("stream/v1", "stream/v2") + "remaining\n";
		parser.accept(value);
		parser.accept("tail");
		assertEquals(value + "tail", parser.finish());
		assertTrue(frames.isEmpty());
	}

	@Test
	void duplicateSectionsFailBeforeConsumerReceivesThemAndPoisonParser() {
		parser.accept(meta(2) + section("s"));
		assertThrows(IllegalArgumentException.class, () -> parser.accept(section("s")));
		assertEquals(2, frames.size());
		assertThrows(IllegalArgumentException.class, () -> parser.accept(section("other")));
	}

	@Test
	void rejectsTrailingJsonDuplicateFieldsAndUnknownFieldsAfterProtocolStarts() {
		for (String line : List.of("{} {}\n", "{\"type\":\"section\",\"type\":\"summary\"}\n",
				section("s").replace("\"claims\":", "\"extra\":true,\"claims\":"))) {
			var received = new ArrayList<JsonNode>();
			var instance = new AssistantAnalysisFrameParser(new ObjectMapper(), "r", received::add);
			instance.accept(meta(1));
			assertThrows(IllegalArgumentException.class, () -> instance.accept(line));
			assertEquals(1, received.size());
		}
	}

	@Test
	void requiresAllPlannedSectionsBeforeSummaryAndSummaryBeforeFinal() {
		parser.accept(meta(1));
		assertThrows(IllegalArgumentException.class, () -> parser.accept(summary()));
		var other = new AssistantAnalysisFrameParser(new ObjectMapper(), "r", frames::add);
		other.accept(meta(0));
		assertThrows(IllegalArgumentException.class, () -> other.accept(end(0)));
	}

	@Test
	void rejectsPostFinalContentAndTruncatedStream() {
		parser.accept(meta(0) + summary() + end(0));
		assertThrows(IllegalArgumentException.class, () -> parser.accept("{}\n"));
		var other = new AssistantAnalysisFrameParser(new ObjectMapper(), "r", frames::add);
		other.accept(meta(1) + section("s").stripTrailing());
		assertThrows(IllegalArgumentException.class, other::finish);
	}

	@Test
	void enforcesUtf8LineAndTotalLimits() {
		parser.accept(meta(1));
		assertThrows(IllegalArgumentException.class, () -> parser.accept("中".repeat(22000) + "\n"));
		var other = new AssistantAnalysisFrameParser(new ObjectMapper(), "r", frames::add);
		other.accept("legacy\n");
		assertThrows(IllegalArgumentException.class, () -> other.accept("x".repeat(1048576)));
	}

	@Test
	void downstreamValidationFailureCannotBeRetriedOrTurnIntoLegacy() {
		var instance = new AssistantAnalysisFrameParser(new ObjectMapper(), "r", frame -> {
			throw new IllegalArgumentException("unknown citation");
		});
		assertThrows(IllegalArgumentException.class, () -> instance.accept(meta(1)));
		assertEquals(AssistantAnalysisFrameParser.Mode.FAILED, instance.mode());
		assertThrows(IllegalArgumentException.class, instance::finish);
	}

}
