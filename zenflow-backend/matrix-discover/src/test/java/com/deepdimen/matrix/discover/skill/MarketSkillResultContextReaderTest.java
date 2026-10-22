package com.deepdimen.matrix.discover.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.discover.entity.MarketSkillRequestEntity;
import com.deepdimen.matrix.skill.api.SkillRunMetadataReader;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class MarketSkillResultContextReaderTest {

	final MarketSkillRequestStore requests = mock(MarketSkillRequestStore.class);

	final SkillRunMetadataReader metadata = mock(SkillRunMetadataReader.class);

	final MarketSkillResultStore results = mock(MarketSkillResultStore.class);

	final MarketSkillResultContextReader reader = new MarketSkillResultContextReader(requests, metadata, results,
			new ObjectMapper());

	final Identity identity = new Identity("7", "3", "3", "market-skill:100");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "key");

	final ResolvedSkillSnapshot release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", "KNOWLEDGE", "77", "fixed"), "{}", "{}", "rules", List.of(), "{}", List.of());

	final MarketSkillRequestEntity row = new MarketSkillRequestEntity();

	@BeforeEach
	void setup() {
		row.setId(99L);
		row.setTenantId(7L);
		row.setActorId(3L);
		row.setAgentId(88L);
		row.setBindingKey("key");
		row.setInputSnapshot("{\"query\":\"question\"}");
		when(requests.findByClientRequest(88L, "previous")).thenReturn(row);
		when(metadata.find("7", "3", consumer, "99"))
			.thenReturn(new SkillRunMetadataReader.Metadata("10", "2", "9", "SUCCEEDED", null));
		when(results.read(any(), eq(consumer), eq("99"), eq("10"), eq("market-skill:99:10")))
			.thenReturn("{\"answer\":\"answer\"}");
		when(requests.load(any(), eq(consumer), eq("99"))).thenReturn(row);
	}

	@Test
	void readsOnlyExistingSuccessfulResultAndRechecksBusinessAccess() {
		var context = reader.read(identity, consumer, release, "previous");
		assertEquals("question", context.question());
		assertEquals("answer", context.answer());
		assertEquals("10", context.skillRunId());
		verify(requests).load(new Identity("7", "3", "3", "market-skill:99"), consumer, "99");
		verify(requests, never()).prepare(any(), any(), any(), any());
	}

	@Test
	void anotherActorAgentOrBindingCannotSupplyHistory() {
		row.setActorId(4L);
		assertThrows(IllegalArgumentException.class, () -> reader.read(identity, consumer, release, "previous"));
		row.setActorId(3L);
		row.setAgentId(89L);
		assertThrows(IllegalArgumentException.class, () -> reader.read(identity, consumer, release, "previous"));
		row.setAgentId(88L);
		row.setBindingKey("other");
		assertThrows(IllegalArgumentException.class, () -> reader.read(identity, consumer, release, "previous"));
		verifyZeroInteractions(results);
	}

	@Test
	void unknownOrDifferentVersionNeverBecomesContext() {
		for (var run : List.of(new SkillRunMetadataReader.Metadata("10", "2", "9", "UNKNOWN", null),
				new SkillRunMetadataReader.Metadata("10", "other", "9", "SUCCEEDED", null),
				new SkillRunMetadataReader.Metadata("10", "2", "other", "SUCCEEDED", null))) {
			when(metadata.find("7", "3", consumer, "99")).thenReturn(run);
			assertThrows(IllegalArgumentException.class, () -> reader.read(identity, consumer, release, "previous"));
		}
		verifyZeroInteractions(results);
	}

}
