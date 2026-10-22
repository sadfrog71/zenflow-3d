package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.discover.entity.MarketSkillRequestEntity;
import com.deepdimen.matrix.discover.mapper.MarketSkillRequestMapper;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketSkillResultStoreTest {

	final MarketSkillRequestStore requests = mock(MarketSkillRequestStore.class);

	final MarketSkillRequestMapper rows = mock(MarketSkillRequestMapper.class);

	final MarketSkillResultStore store = new MarketSkillResultStore(requests, rows, new ObjectMapper());

	final Identity identity = new Identity("7", "3", "3", "market-skill:10");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");

	final MarketSkillRequestEntity row = new MarketSkillRequestEntity();

	@BeforeEach
	void setup() {
		row.setId(10L);
		row.setTenantId(7L);
		row.setActorId(3L);
		row.setInputDigest("digest");
		when(requests.load(identity, consumer, "10")).thenReturn(row);
		when(rows.saveOnce(7L, 3L, 10L, "digest", 11L, "{\"answer\":\"hello\"}")).thenAnswer(call -> {
			if (row.getSkillRunId() != null)
				return 0;
			row.setSkillRunId(11L);
			row.setSkillOutputJson("{\"answer\":\"hello\"}");
			return 1;
		});
	}

	@Test
	void saveAndReadUseSameAuthorizedRun() {
		var ref = store.save(identity, consumer, "10", "11", "{\"answer\":\"hello\"}");
		assertEquals(ref, store.save(identity, consumer, "10", "11", "{\"answer\":\"hello\"}"));
		assertEquals("{\"answer\":\"hello\"}", store.read(identity, consumer, "10", "11", ref));
	}

	@Test
	void anotherRunCannotOverwriteOrRead() {
		row.setSkillRunId(12L);
		row.setSkillOutputJson("{\"answer\":\"old\"}");
		assertThrows(IllegalArgumentException.class,
				() -> store.save(identity, consumer, "10", "11", "{\"answer\":\"hello\"}"));
		assertThrows(IllegalArgumentException.class,
				() -> store.read(identity, consumer, "10", "11", "market-skill:10:11"));
	}

	@Test
	void malformedOutputNeverWrites() {
		assertThrows(IllegalArgumentException.class,
				() -> store.save(identity, consumer, "10", "11", "{\"answer\":1,\"answer\":2}"));
		assertThrows(IllegalArgumentException.class, () -> store.save(identity, consumer, "10", "11", "{} {}"));
		verifyZeroInteractions(rows);
	}

	@Test
	void revokedAuthorizationCannotRead() {
		doThrow(new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED")).when(requests).load(identity, consumer, "10");
		assertThrows(IllegalArgumentException.class,
				() -> store.read(identity, consumer, "10", "11", "market-skill:10:11"));
	}

}
