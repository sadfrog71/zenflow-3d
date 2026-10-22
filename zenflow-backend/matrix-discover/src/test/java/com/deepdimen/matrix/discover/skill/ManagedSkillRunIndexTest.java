package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.discover.entity.AiSkillRunEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillRunMapper;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedSkillRunIndexTest {

	private final AiSkillRunMapper mapper = mock(AiSkillRunMapper.class);

	private final ManagedSkillRunIndex index = new ManagedSkillRunIndex(mapper, new ObjectMapper());

	private final Identity identity = new Identity("42", "3", "3", "correlation");

	private final Consumer consumer = new Consumer("TEST", "7", "SUMMARY", "default");

	@Test
	void canonicalRetriesReuseRunAndChangedInputOrVersionConflicts() {
		var rows = new HashMap<String, AiSkillRunEntity>();
		when(mapper.reserve(any())).thenAnswer(call -> {
			AiSkillRunEntity row = call.getArgument(0);
			row.setStatus("QUEUED");
			row.setRevision(0L);
			rows.putIfAbsent(row.getIdempotencyScopeDigest(), row);
			return 1;
		});
		when(mapper.findByScope(any(), any())).thenAnswer(call -> rows.get(call.getArgument(1)));
		var request = new ExecuteRequest(identity, consumer, "opaque", "{}", null, "same-key", null);
		var first = index.reserve(request, resolved("11"), "{\"a\":1,\"b\":2}");
		assertEquals(first.getId(), index.reserve(request, resolved("11"), "{\"b\":2,\"a\":1}").getId());
		assertThrows(IllegalArgumentException.class, () -> index.reserve(request, resolved("11"), "{\"a\":9}"));
		assertThrows(IllegalArgumentException.class, () -> index.reserve(request, resolved("12"), "{\"a\":1,\"b\":2}"));
		assertEquals(1, rows.size());
	}

	@Test
	void onlyQueuedRunCanBeClaimedAndUnknownCannotReplay() {
		var row = new AiSkillRunEntity();
		row.setTenantId("42");
		row.setId(9L);
		row.setRevision(0L);
		row.setStatus("QUEUED");
		when(mapper.claim("42", 9L, 0L)).thenReturn(1).thenReturn(0);
		assertTrue(index.claim(row));
		assertFalse(index.claim(row));
		row.setStatus("UNKNOWN");
		assertFalse(index.claim(row));
		verify(mapper, times(2)).claim("42", 9L, 0L);
	}

	@Test
	void terminalPersistenceConflictDoesNotPretendSuccess() {
		var row = new AiSkillRunEntity();
		row.setTenantId("42");
		row.setId(9L);
		row.setRevision(0L);
		assertThrows(IllegalStateException.class,
				() -> index.finish(row, RunStatus.SUCCEEDED, "result", null, "v1", "{}"));
		verify(mapper).finish("42", 9L, 1L, "SUCCEEDED", "result", null, "v1", "{}");
	}

	private SkillResolutionTokenStore.StoredResolution resolved(String releaseId) {
		var snapshot = new ResolvedSkillSnapshot("1", releaseId, "digest", "5", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "frozen", List.of(), "{}", List.of());
		return new SkillResolutionTokenStore.StoredResolution(identity, consumer, "business", "{}", snapshot,
				Long.MAX_VALUE);
	}

}
