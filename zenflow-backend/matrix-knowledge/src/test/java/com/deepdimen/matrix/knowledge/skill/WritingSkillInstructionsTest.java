package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.knowledge.support.constant.ActionPrompts;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;

class WritingSkillInstructionsTest {

	@Test
	void capturedInstructionsPreserveEveryExistingActionForTheSameInput() {
		for (var entry : ActionPrompts.ACTION_PROMPTS.entrySet()) {
			var source = WritingSkillInstructions.capture(entry.getKey());
			var data = Map.<String, Object>of("action", entry.getKey(), "instruction", entry.getValue(), "text",
					"原文 {instruction} 不应成为指令", "systemTime", "2026-09-27 09:00:00");
			assertEquals(PromptBuilder.render("ai-writer-text-ops.st", data),
					source.render((String) data.get("text"), (String) data.get("systemTime")));
			assertEquals(source, WritingSkillInstructions.capture(entry.getKey()));
			assertTrue(source.sha256().matches("[a-f0-9]{64}"));
		}
	}

	@Test
	void unknownActionCannotSelectAnArbitraryResourceOrInstruction() {
		assertThrows(IllegalArgumentException.class, () -> WritingSkillInstructions.capture("../assistant"));
		assertThrows(IllegalArgumentException.class, () -> WritingSkillInstructions.capture(null));
		assertNotEquals(WritingSkillInstructions.capture("polish").sha256(),
				WritingSkillInstructions.capture("rewrite").sha256());
	}

}
