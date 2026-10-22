package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.skill.api.AgentSkillCapabilityAdapter;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketSkillCapabilitiesTest {

	@Test
	void typeNameDoesNotImplySupport() {
		var c = new MarketSkillCapabilities(List.of()).inspect("7", "8", "dify");
		assertEquals(CompatibilityStatus.UNAVAILABLE, c.status());
		assertTrue(c.runtimeKinds().isEmpty());
		assertFalse(c.instructions());
	}

	@Test
	void unavailableQueryIsUnknownNotUnsupported() {
		var a = mock(AgentSkillCapabilityAdapter.class);
		when(a.agentType()).thenReturn("kb");
		when(a.inspect("7", "8")).thenThrow(new IllegalStateException("private"));
		assertEquals(CompatibilityStatus.UNKNOWN,
				new MarketSkillCapabilities(List.of(a)).inspect("7", "8", "kb").status());
	}

	@Test
	void duplicateAdaptersAreNotSelectedArbitrarily() {
		var a = mock(AgentSkillCapabilityAdapter.class);
		when(a.agentType()).thenReturn("kb");
		var c = new MarketSkillCapabilities(List.of(a, a)).inspect("7", "8", "kb");
		assertEquals(ErrorCode.ADAPTER_AMBIGUOUS, c.reasonCode());
		verify(a, never()).inspect("7", "8");
	}

	@Test
	void actualCapabilitiesRemainLimitedToDeclaredRuntime() {
		var a = mock(AgentSkillCapabilityAdapter.class);
		when(a.agentType()).thenReturn("kb");
		var c = new AgentCapabilities(CompatibilityStatus.SUPPORTED, List.of(RuntimeKind.PROMPT), true, false, true,
				true, false, "{}", "{}", null);
		when(a.inspect("7", "8")).thenReturn(c);
		assertEquals(c, new MarketSkillCapabilities(List.of(a)).inspect("7", "8", "kb"));
	}

}
