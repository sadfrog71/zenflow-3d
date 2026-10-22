package com.deepdimen.matrix.assistant.orchestrator;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantQueryPlannerTest {

	@Test
	void extractsBusinessPhraseFromNaturalLanguageRequest() {
		List<String> terms = AssistantQueryPlanner.plan("请总结本地联调样例，并列出相关任务、闪念和 AI 资讯。", List.of());

		assertTrue(terms.contains("本地联调样例"));
		assertFalse(terms.contains("请总结本地联调样例"));
	}

	@Test
	void reusesPreviousUserTurnForFollowUpQuestion() {
		List<String> terms = AssistantQueryPlanner.plan("它的截止时间是什么？", List.of("请查看本地联调样例"));

		assertTrue(terms.contains("本地联调样例"));
	}

	@Test
	void genericListQuestionDoesNotBecomeAFalseLikePredicate() {
		List<String> terms = AssistantQueryPlanner.plan("有哪些待办任务？", List.of());

		assertTrue(terms.isEmpty());
	}

	@Test
	void genericCrossProviderAnalysisDoesNotFilterOutAuthorizedBusinessData() {
		List<String> terms = AssistantQueryPlanner.plan("基于所有业务数据做个分析", List.of("请查看本地联调样例"));

		assertTrue(terms.isEmpty());
	}

	@Test
	void genericCurrentBusinessDataAnalysisDoesNotFilterOutAuthorizedBusinessData() {
		List<String> terms = AssistantQueryPlanner.plan("分析下当前所有业务数据", List.of("请查看本地联调样例"));

		assertTrue(terms.isEmpty());
	}

	@Test
	void genericAllBusinessDataVariantDoesNotReusePreviousTopic() {
		List<String> terms = AssistantQueryPlanner.plan("请分析全部业务数据", List.of("请查看本地联调样例"));

		assertTrue(terms.isEmpty());
	}

	@Test
	void genericSelectedModulesSummaryDoesNotBecomeAFalseSearchTermOrReuseHistory() {
		List<String> terms = AssistantQueryPlanner.plan("总结下这些模块的内容", List.of("请查看本地联调样例"));

		assertTrue(terms.isEmpty());
	}

	@Test
	void stripsTheLongestAnalysisSuffixBeforeTheGenericSuffix() {
		List<String> terms = AssistantQueryPlanner.plan("本地联调样例做个分析", List.of());

		assertTrue(terms.contains("本地联调样例"));
		assertFalse(terms.contains("本地联调样例做个"));
	}

}
