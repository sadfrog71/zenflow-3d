package com.deepdimen.matrix.discover.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.discover.entity.MarketSkillRequestEntity;
import com.deepdimen.matrix.discover.skill.*;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;

class MarketSkillRequestControllerTest {

	final MarketSkillRequestStore requests = mock(MarketSkillRequestStore.class);

	final SkillRunMetadataReader metadata = mock(SkillRunMetadataReader.class);

	final MarketSkillResultStore results = mock(MarketSkillResultStore.class);

	final MarketSkillRequestController controller = new MarketSkillRequestController(requests, metadata, results);

	final Identity identity = new Identity("7", "3", "3", "market-skill:99");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");

	void saved() {
		var row = new MarketSkillRequestEntity();
		row.setId(99L);
		row.setTenantId(7L);
		row.setActorId(3L);
		row.setAgentId(88L);
		row.setBindingKey("skill-1");
		when(requests.findByClientRequest(88L, "request")).thenReturn(row);
	}

	@Test
	void missingRequestOrRunIsUnknownAndNeverCreatesAnExecution() {
		var missing = controller.find(88L, "request").getData();
		assertFalse(missing.requestFound());
		assertEquals(RunStatus.UNKNOWN, missing.status());
		verifyZeroInteractions(metadata, results);
		saved();
		var pending = controller.find(88L, "request").getData();
		assertTrue(pending.requestFound());
		assertEquals(RunStatus.UNKNOWN, pending.status());
		assertNull(pending.skillRunId());
		verify(requests, never()).prepare(any(), any(), any(), any());
	}

	@Test
	void successReadsTheExistingAuthorizedResultAndReportsActualFixedVersion() {
		saved();
		when(metadata.find("7", "3", consumer, "99"))
			.thenReturn(new SkillRunMetadataReader.Metadata("10", "2", "9", "SUCCEEDED", null));
		when(results.read(identity, consumer, "99", "10", "market-skill:99:10")).thenReturn("{\"answer\":\"stored\"}");
		var view = controller.find(88L, "request").getData();
		assertEquals("2", view.actualReleaseId());
		assertEquals("9", view.bindingRevisionId());
		assertEquals("10", view.skillRunId());
		assertEquals("{\"answer\":\"stored\"}", view.resultJson());
		verify(requests).load(identity, consumer, "99");
	}

	@Test
	void unfinishedOrFailedRunsNeverExposeUnverifiedStoredOutput() {
		saved();
		for (String state : List.of("QUEUED", "RUNNING", "UNKNOWN", "FAILED", "CANCELLED", "DEGRADED")) {
			when(metadata.find("7", "3", consumer, "99"))
				.thenReturn(new SkillRunMetadataReader.Metadata("10", "2", "9", state, null));
			var view = controller.find(88L, "request").getData();
			assertEquals(state, view.status().name());
			assertNull(view.resultJson());
		}
		verifyZeroInteractions(results);
	}

	@Test
	void revokedReadAccessCannotReturnSuccess() {
		saved();
		when(metadata.find("7", "3", consumer, "99"))
			.thenReturn(new SkillRunMetadataReader.Metadata("10", "2", "9", "SUCCEEDED", null));
		when(results.read(any(), any(), any(), any(), any()))
			.thenThrow(new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED"));
		assertThrows(IllegalArgumentException.class, () -> controller.find(88L, "request"));
	}

}
