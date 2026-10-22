package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;

class ToolSkillPromptSourcesTest {

	@Test
	void allReportAndOcrTemplatesRenderTheSameInputWithoutChangingSharedResources() {
		for (var slot : ToolSkillPromptSources.Slot.values()) {
			Map<String, Object> parameters = switch (slot) {
				case WRITING_DOCUMENT -> Map.of("systemTime", "2026-09-27", "title", "公文", "doc_template", "<p>原模板</p>",
						"abstractContent", "原资料");
				case REPORT_POLISH -> Map.of("reportTitle", "报告", "reportPrompt", "现有业务规则", "prompt", "用户输入",
						"username", "测试用户", "systemTime", "2026-09-27");
				case REPORT_SYSTEM, OCR_SYSTEM, OCR_IMAGE -> Map.of("systemTime", "2026-09-27");
				case OCR_IMAGE_SCHEMA -> Map.of("jsonSchema", "{\"type\":\"object\"}");
				case REPORT_INPUT, OCR_INPUT ->
					Map.of("userInput", "材料 {systemPrompt}", "jsonSchema", "{\"type\":\"object\"}");
			};
			var source = ToolSkillPromptSources.capture(slot);
			assertEquals(PromptBuilder.render(slot.file(), parameters), source.render(parameters));
			assertEquals(source, ToolSkillPromptSources.capture(slot));
			assertTrue(source.sha256().matches("[a-f0-9]{64}"));
		}
	}

	@Test
	void arbitraryTemplateAndExecutorOverridesAreNotRenderParameters() {
		assertThrows(IllegalArgumentException.class, () -> ToolSkillPromptSources.capture(null));
		var source = ToolSkillPromptSources.capture(ToolSkillPromptSources.Slot.OCR_IMAGE);
		assertThrows(IllegalArgumentException.class, () -> source.render(Map.of("executorRef", "override")));
		assertThrows(IllegalArgumentException.class, () -> source.render(Map.of("systemPrompt", "override")));
	}

}
