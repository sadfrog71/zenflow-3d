package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ZenflowReferenceContextAdapterTest {

	private ZenflowReferenceIndexes indexes;

	private ZenflowReferenceSnapshots snapshots;

	private ZenflowReferenceContextAdapter adapter;

	private final Map<String, Object> revision = Map.of("id", 100L, "tenant_id", 1L, "reference_digest", "r",
			"index_configuration_digest", "i");

	private final ZenflowReferencePolicy policy = new ZenflowReferencePolicy(
			List.of(new ZenflowReferencePolicy.Reference("10", "MATERIAL", "en", "PUBLIC")), 6, .7);

	@BeforeEach
	void setup() {
		indexes = mock(ZenflowReferenceIndexes.class);
		snapshots = mock(ZenflowReferenceSnapshots.class);
		adapter = new ZenflowReferenceContextAdapter(indexes, snapshots);
		when(indexes.pointId(eq("100"), anyString())).thenAnswer(invocation -> "point-" + invocation.getArgument(1));
	}

	private ZenflowReferenceSnapshots.Snapshot snapshot(String id, String body) {
		return new ZenflowReferenceSnapshots.Snapshot(id, "1", "10", "20", "v1", ZenflowRunAuthorization.hash(body),
				"MATERIAL", "Public", "en", body, "https://example.org", "PUBLIC");
	}

	private EmbeddingMatch<TextSegment> match(ZenflowReferenceSnapshots.Snapshot snapshot, String tenant) {
		return new EmbeddingMatch<>(.9, "point-" + snapshot.id(), Embedding.from(new float[] { 1, 0 }),
				TextSegment.from("UNTRUSTED vector payload text",
						Metadata.from(Map.of("tenantId", tenant, "companionRevisionId", "100", "referenceDigest", "r",
								"indexConfigurationDigest", "i", "snapshotId", snapshot.id(), "sourceHash",
								snapshot.contentHash()))));
	}

	@Test
	void sqlFrozenBodyWinsOverVectorPayloadAndRankingIsPreserved() {
		var first = snapshot("20", "first approved body");
		var second = snapshot("21", "second approved body");
		var callback = (Runnable) () -> {
		};
		when(indexes.search(revision, policy, "current question", callback))
			.thenReturn(List.of(match(second, "1"), match(first, "1")));
		when(snapshots.selected("100", policy, "r",
				Map.of(first.id(), first.contentHash(), second.id(), second.contentHash())))
			.thenReturn(List.of(first, second));
		assertEquals(List.of(second, first), adapter.retrieve(revision, policy, "current question", callback));
	}

	@Test
	void wrongTenantDuplicatePointAndRevocationCannotBecomeModelContext() {
		var fixed = snapshot("20", "approved body");
		var callback = (Runnable) () -> {
		};
		when(indexes.search(revision, policy, "question", callback)).thenReturn(List.of(match(fixed, "2")));
		assertThrows(ResponseStatusException.class, () -> adapter.retrieve(revision, policy, "question", callback));
		verifyZeroInteractions(snapshots);
		var valid = match(fixed, "1");
		when(indexes.search(revision, policy, "question", callback)).thenReturn(List.of(valid, valid));
		assertThrows(ResponseStatusException.class, () -> adapter.retrieve(revision, policy, "question", callback));
		when(indexes.search(revision, policy, "question", callback)).thenReturn(List.of(valid));
		when(snapshots.selected("100", policy, "r", Map.of(fixed.id(), fixed.contentHash()))).thenThrow(
				new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "REFERENCE_SOURCE_REVOKED"));
		assertThrows(ResponseStatusException.class, () -> adapter.retrieve(revision, policy, "question", callback));
	}

	@Test
	void noHitDoesNotReadSourcesAndPublicBodiesAreBounded() {
		var callback = (Runnable) () -> {
		};
		when(indexes.search(revision, policy, "question", callback)).thenReturn(List.of());
		assertEquals(List.of(), adapter.retrieve(revision, policy, "question", callback));
		verifyZeroInteractions(snapshots);
		var first = snapshot("20", "a".repeat(8000));
		var second = snapshot("21", "b".repeat(8000));
		when(indexes.search(revision, policy, "question", callback))
			.thenReturn(List.of(match(first, "1"), match(second, "1")));
		when(snapshots.selected("100", policy, "r",
				Map.of(first.id(), first.contentHash(), second.id(), second.contentHash())))
			.thenReturn(List.of(first, second));
		assertEquals(List.of(first), adapter.retrieve(revision, policy, "question", callback));
	}

}
