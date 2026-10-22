package com.deepdimen.matrix.discover.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class MarketSkillStreamPortTest {

	final MarketSkillRequestStore requests = mock(MarketSkillRequestStore.class);

	final MarketSkillStreamPort port = new MarketSkillStreamPort(requests, new ObjectMapper());

	final Identity identity = new Identity("7", "3", "3", "market-skill:99");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "key");

	final String input = "{\"query\":\"question\",\"response_mode\":\"streaming\"}";

	@AfterEach
	void cleanup() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void flushesEscapedDeltasWithoutPublishingExecutionSuccess() throws Exception {
		when(requests.authorizeInput(identity, consumer, "99", input)).thenReturn(input);
		var response = new MockHttpServletResponse();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), response));
		var sink = port.open(identity, consumer, "99", input, "9", "1");
		RequestContextHolder.resetRequestAttributes();
		sink.phase(com.deepdimen.matrix.skill.api.AgentSkillStreamPort.Phase.RETRIEVING);
		sink.accept("第一段\n换行");
		assertTrue(response.isCommitted());
		assertTrue(response.getContentAsString().contains("RETRIEVING"));
		assertTrue(response.getContentAsString().contains("actualReleaseId"));
		assertEquals("text/event-stream;charset=UTF-8", response.getContentType());
		assertTrue(response.getContentAsString().contains("第一段\\n换行"));
		sink.accept("第二段");
		assertEquals(2, response.getContentAsString().split("event: message", -1).length - 1);
		assertFalse(response.getContentAsString().contains("skill_result"));
		assertEquals("9", response.getHeader("X-Skill-Run-Id"));
		verify(requests).authorizeInput(identity, consumer, "99", input);
	}

	@Test
	void blockingDoesNotRequireServletButUnauthorizedRequestCannotOpenStream() {
		when(requests.authorizeInput(identity, consumer, "99", input)).thenReturn("{\"response_mode\":\"blocking\"}");
		assertDoesNotThrow(() -> port.open(identity, consumer, "99", input, "9", "1").accept("answer"));
		when(requests.authorizeInput(identity, consumer, "99", input)).thenThrow(new IllegalArgumentException("denied"));
		assertThrows(IllegalArgumentException.class, () -> port.open(identity, consumer, "99", input, "9", "1"));
	}

}
