package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.deepdimen.matrix.skill.api.AgentSkillTargetReader;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;

class KnowledgeMarketConfigurationSnapshotTest {

	final ObjectMapper json = new ObjectMapper();

	final String config = "{\"datasetIds\":[\"31\"],\"modelConfig\":{\"provider\":\"provider\",\"modelName\":\"model\"},\"systemPrompt\":\"original\"}";

	KnowledgeMarketConfigurationSnapshot capture(String id, String value) {
		return KnowledgeMarketConfigurationSnapshot.capture(new AgentSkillTargetReader.Target(id, "knowledge", value),
				json);
	}

	@Test
	void canonicalObjectOrderPreservesTheSameExistingConfiguration() {
		var first = capture("88", config);
		var reordered = capture("88",
				"{\"systemPrompt\":\"original\",\"modelConfig\":{\"modelName\":\"model\",\"provider\":\"provider\"},\"datasetIds\":[\"31\"]}");
		assertEquals(first.digest(), reordered.digest());
		assertEquals(64, first.digest().length());
		assertDoesNotThrow(() -> first.requireSame(reordered));
		assertEquals(java.util.List.of("31"), first.datasetIds());
	}

	@Test
	void agentScopeRulesAndModelChangesInvalidateTheFingerprint() {
		var first = capture("88", config);
		for (var changed : java.util.List.of(capture("89", config), capture("88", config.replace("31", "32")),
				capture("88", config.replace("original", "edited")),
				capture("88", config.replace("\"provider\":\"provider\"", "\"provider\":\"other\"")))) {
			assertThrows(IllegalArgumentException.class, () -> first.requireSame(changed));
		}
	}

	@Test
	void invalidOrUnboundedKnowledgeConfigurationCannotBePinned() {
		for (String invalid : java.util.List.of("{}", config.replace("[\"31\"]", "[]"), config.replace("31", "-1"),
				config.replace("[\"31\"]", "[\"31\",\"31\"]"), config.replace("31", "9999999999999999999"),
				config + "{}", config.replace("\"systemPrompt\":\"original\"",
						"\"systemPrompt\":\"original\",\"systemPrompt\":\"other\"")))
			assertThrows(IllegalArgumentException.class, () -> capture("88", invalid));
		assertThrows(IllegalArgumentException.class, () -> capture("2026", config));
		assertThrows(IllegalArgumentException.class, () -> KnowledgeMarketConfigurationSnapshot
			.capture(new AgentSkillTargetReader.Target("88", "dify", config), json));
	}

	@Test
	void readerUsesAuthorizedTargetAndDoesNotFallbackWhenBridgeIsMissing() {
		ObjectProvider<AgentSkillTargetReader> providers = mock(ObjectProvider.class);
		var reader = new KnowledgeMarketConfigurationReader(providers, json);
		var consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");
		assertThrows(IllegalStateException.class, () -> reader.read(consumer));
		var target = mock(AgentSkillTargetReader.class);
		when(providers.getIfAvailable()).thenReturn(target);
		when(target.read(consumer)).thenReturn(new AgentSkillTargetReader.Target("88", "knowledge", config));
		assertEquals(capture("88", config), reader.read(consumer));
		when(target.read(consumer)).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
		assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> reader.read(consumer));
	}

}
