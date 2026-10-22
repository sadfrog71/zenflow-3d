package com.deepdimen.matrix.work.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InformationSkillInputTest {

	final ObjectMapper json = new ObjectMapper();

	@Test
	void restoredEvidenceUsesSameFrozenDateAndOriginalPromptFormatting() {
		var input = new InformationSkillInput("标题", "https://example.test", "证据", LocalDate.of(2026, 9, 26));
		var restored = InformationSkillInput.restore(json, input.snapshot(json));
		assertEquals(input, restored);
		String date = "当前日期（北京时间）：2026-09-26\n日期判断规则：早于或等于当前日期的日期不是未来日期。\n";
		assertEquals(date + "标题：标题\nURL：https://example.test\n证据：\n证据", restored.analysisPrompt());
		assertEquals(date + "候选结果：{\"eligible\":true}\n原始证据：\n证据", restored.reviewPrompt("{\"eligible\":true}"));
	}

	@Test
	void snapshotPreservesOriginalEvidenceLimitAndNullableInputSemantics() {
		var input = new InformationSkillInput(null, null, "x".repeat(12001), LocalDate.of(2026, 9, 26));
		assertEquals(12000, input.evidence().length());
		assertEquals(input, InformationSkillInput.restore(json, input.snapshot(json)));
		assertTrue(input.analysisPrompt().contains("标题：null\nURL：null"));
		assertEquals("", new InformationSkillInput("t", "u", null, LocalDate.of(2026, 9, 26)).evidence());
	}

	@Test
	void corruptOrSubstitutedSnapshotCannotSilentlyBeNormalized() {
		String valid = new InformationSkillInput("t", "u", "e", LocalDate.of(2026, 9, 26)).snapshot(json);
		for (String value : java.util.List.of(valid + "{}",
				valid.replace("\"title\":\"t\"", "\"title\":\"t\",\"title\":\"other\""),
				valid.replace("2026-09-26", "invalid"), valid.replace("\"e\"", "\"" + "x".repeat(12001) + "\""), "{}"))
			assertThrows(IllegalArgumentException.class, () -> InformationSkillInput.restore(json, value));
	}

}
