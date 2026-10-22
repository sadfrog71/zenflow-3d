package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;

class KnowledgeSkillInputPolicyTest {

	final KnowledgeSkillResources resources = mock(KnowledgeSkillResources.class);

	final ObjectProvider<SensitiveWordBs> dictionaries = mock(ObjectProvider.class);

	final SensitiveWordBs dictionary = mock(SensitiveWordBs.class);

	final KnowledgeSkillInputPolicy policy = new KnowledgeSkillInputPolicy(resources, dictionaries);

	final Identity identity = new Identity("7", "3", "3", "run");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "key");

	final ResolvedSkillSnapshot release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", "KNOWLEDGE", "77", "fixed"), "{}", "{}", "rules", List.of(), "{}", List.of());

	final AiDatasetEntity first = new AiDatasetEntity();

	final AiDatasetEntity second = new AiDatasetEntity();

	@BeforeEach
	void setup() {
		var config = new KnowledgeMarketConfigurationSnapshot("88", List.of("31", "32"), "local", "model", "digest",
				"{}");
		when(resources.verify(identity, consumer, release))
			.thenReturn(new KnowledgeSkillResources.Capture(null, config, "77", List.of(), "digest"));
		when(resources.resolveRetrieval(identity, consumer, release, 31L))
			.thenReturn(new KnowledgeSkillResources.Retrieval(first, null, null, null, "digest"));
		when(resources.resolveRetrieval(identity, consumer, release, 32L))
			.thenReturn(new KnowledgeSkillResources.Retrieval(second, null, null, null, "digest"));
		when(dictionaries.getIfAvailable()).thenReturn(dictionary);
	}

	@Test
	void onlyExplicitlyDisabledDatasetsSkipExistingDictionary() {
		first.setSensitiveFlag(YesNoEnum.NO.getCode());
		second.setSensitiveFlag(YesNoEnum.NO.getCode());
		policy.fixedReply(identity, consumer, release, "question");
		verifyZeroInteractions(dictionary);
		second.setSensitiveFlag(null);
		policy.fixedReply(identity, consumer, release, "question");
		verify(dictionary).contains("question");
	}

	@Test
	void enabledDatasetsReuseOneDictionaryCheckAndRejectMatches() {
		first.setSensitiveFlag(YesNoEnum.YES.getCode());
		second.setSensitiveFlag(YesNoEnum.YES.getCode());
		policy.fixedReply(identity, consumer, release, "question");
		verify(dictionary, times(1)).contains("question");
		when(dictionary.contains("blocked")).thenReturn(true);
		first.setSensitiveMsg("请调整问题后再试");
		assertEquals("请调整问题后再试", policy.fixedReply(identity, consumer, release, "blocked"));
		first.setSensitiveMsg(" ");
		assertThrows(IllegalArgumentException.class, () -> policy.fixedReply(identity, consumer, release, "blocked"));
	}

	@Test
	void unavailableDictionaryOrRevokedDatasetCannotBypassPolicy() {
		when(dictionaries.getIfAvailable()).thenReturn(null);
		assertThrows(IllegalStateException.class, () -> policy.fixedReply(identity, consumer, release, "question"));
		when(dictionaries.getIfAvailable()).thenReturn(dictionary);
		when(resources.resolveRetrieval(identity, consumer, release, 32L)).thenThrow(new IllegalArgumentException("revoked"));
		assertThrows(IllegalArgumentException.class, () -> policy.fixedReply(identity, consumer, release, "question"));
	}

}
