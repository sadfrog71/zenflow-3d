package com.deepdimen.matrix.knowledge.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AssistantReasoningFilterTest {

	@Test
	void removesCompleteReasoningBlock() {
		assertEquals("## Result\n- done",
				AssistantReasoningFilter.sanitize("<think>private reasoning</think>## Result\n- done"));
	}

	@Test
	void removesReasoningWhenTagsAreSplitAcrossChunks() {
		AssistantReasoningFilter filter = new AssistantReasoningFilter();
		String visible = filter.accept("<thi") + filter.accept("nk>private") + filter.accept(" reasoning</thi")
				+ filter.accept("nk>## Result") + filter.finish();

		assertEquals("## Result", visible);
	}

	@Test
	void preservesOrdinaryMarkdownAcrossPossibleTagPrefixes() {
		AssistantReasoningFilter filter = new AssistantReasoningFilter();
		String visible = filter.accept("## MiniMax\n- status <") + filter.accept("ok") + filter.finish();

		assertEquals("## MiniMax\n- status <ok", visible);
	}

	@Test
	void dropsUnclosedReasoningInsteadOfLeakingIt() {
		assertEquals("", AssistantReasoningFilter.sanitize("<think>private reasoning"));
	}

}
