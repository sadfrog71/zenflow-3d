package com.deepdimen.matrix.discover.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.skill.api.*;

class KnowledgeSkillConfigurationControllerTest {

	@Test
	void readsOnlyAuthorizedKnowledgeTarget() {
		var targets = mock(AgentSkillTargetReader.class);
		var adapter = mock(AgentSkillConfigurationAdapter.class);
		when(adapter.agentType()).thenReturn("knowledge");
		when(targets.read(any())).thenReturn(new AgentSkillTargetReader.Target("88", "knowledge", "{}"));
		var value = new AgentSkillConfigurationAdapter.Configuration(null, List.of());
		when(adapter.inspect(any())).thenReturn(value);
		assertSame(value, new KnowledgeSkillConfigurationController(targets, List.of(adapter)).get(88L).getData());
		verify(adapter).inspect(argThat(c -> "88".equals(c.id()) && "MARKET_AGENT".equals(c.type())));
	}

	@Test
	void refusesMissingAdapterAndWrongTarget() {
		var targets = mock(AgentSkillTargetReader.class);
		var controller = new KnowledgeSkillConfigurationController(targets, List.of());
		assertThrows(IllegalArgumentException.class, () -> controller.get(2026L));
		verifyZeroInteractions(targets);
		when(targets.read(any())).thenReturn(new AgentSkillTargetReader.Target("88", "dify", "{}"));
		assertThrows(IllegalArgumentException.class, () -> controller.get(88L));
		when(targets.read(any())).thenReturn(new AgentSkillTargetReader.Target("88", "knowledge", "{}"));
		assertThrows(IllegalStateException.class, () -> controller.get(88L));
	}

}
