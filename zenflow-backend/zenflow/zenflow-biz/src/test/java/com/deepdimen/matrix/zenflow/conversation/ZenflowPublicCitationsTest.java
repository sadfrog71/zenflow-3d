package com.deepdimen.matrix.zenflow.conversation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.companion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class ZenflowPublicCitationsTest {

	private JdbcTemplate jdbc;

	private ZenflowReferenceSnapshots snapshots;

	private ZenflowCitations citations;

	private List<Map<String, Object>> publicRows;

	private boolean completed;

	private final ZenflowReferencePolicy policy = new ZenflowReferencePolicy(
			List.of(new ZenflowReferencePolicy.Reference("10", "MATERIAL", "en", "PUBLIC"),
					new ZenflowReferencePolicy.Reference("11", "MATERIAL", "en", "TITLE_ONLY"),
					new ZenflowReferencePolicy.Reference("12", "MATERIAL", "en", "INTERNAL")),
			6, .7);

	@BeforeEach
	void setup() throws Exception {
		jdbc = mock(JdbcTemplate.class);
		snapshots = mock(ZenflowReferenceSnapshots.class);
		var access = mock(ZenflowConsumerAccess.class);
		when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L, 9L, "APP_USER"));
		var json = new ObjectMapper();
		citations = new ZenflowCitations(jdbc, access, snapshots, json);
		completed = true;
		var rows = new ArrayList<Map<String, Object>>();
		for (int id = 20; id <= 22; id++)
			rows.add(
					Map.of("citation_key", "REFERENCE_" + id, "content_hash", ZenflowRunAuthorization.hash("body" + id),
							"reference_snapshot_id", (long) id, "revision_id", 100L, "reference_digest", "digest",
							"retrieval_policy_json", json.writeValueAsString(policy.projection())));
		publicRows = rows;
		when(jdbc.queryForList(anyString(), eq("run"), eq(1L), eq(9L))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (sql.startsWith("SELECT x.lineage_state"))
				return List.of(Map.of("lineage_state", "VALID", "status", completed ? "COMPLETED" : "MODEL_STREAMING"));
			if (sql.contains("s.reference_snapshot_id,r.id AS revision_id"))
				return publicRows;
			return List.of();
		});
		var fixed = new ArrayList<ZenflowReferenceSnapshots.Snapshot>();
		var visibility = List.of("PUBLIC", "TITLE_ONLY", "INTERNAL");
		var hashes = new HashMap<String, String>();
		for (int id = 20; id <= 22; id++) {
			String hash = ZenflowRunAuthorization.hash("body" + id);
			hashes.put(Integer.toString(id), hash);
			fixed.add(new ZenflowReferenceSnapshots.Snapshot(Integer.toString(id), "1", Integer.toString(id - 10), "1",
					"v1", hash, "MATERIAL", "Title" + id, "en", "body" + id, "https://example.org/" + id,
					visibility.get(id - 20)));
		}
		when(snapshots.selected("100", policy, "digest", hashes)).thenReturn(fixed);
	}

	@Test
	void visibleCardsUseApprovedSnapshotAndTitleOnlyNeverLeaksBodyOrLocator() {
		var cards = citations.list("run");
		assertEquals(2, cards.size());
		assertEquals("body20", cards.get(0).get("excerpt"));
		assertEquals("20", cards.get(0).get("snapshotId"));
		assertFalse(cards.get(0).containsKey("noteId"));
		assertEquals("", cards.get(1).get("excerpt"));
		assertFalse(cards.get(1).containsKey("sourceLocator"));
		assertEquals("TITLE_ONLY", cards.get(1).get("visibility"));
		assertThrows(ResponseStatusException.class, () -> citations.detail("run", "REFERENCE_22"));
	}

	@Test
	void incompleteRevokedAndUnknownIdentitySourcesProduceNoCard() {
		completed = false;
		assertEquals(List.of(), citations.list("run"));
		verifyZeroInteractions(snapshots);
		completed = true;
		when(snapshots.selected(eq("100"), eq(policy), eq("digest"), anyMap()))
			.thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "REFERENCE_SOURCE_REVOKED"));
		assertEquals(List.of(), citations.list("run"));
		publicRows = List.of(Map.of("citation_key", "REFERENCE_wrong", "content_hash", "hash", "reference_snapshot_id",
				20L, "revision_id", 100L, "reference_digest", "digest", "retrieval_policy_json", "{}"));
		assertEquals(List.of(), citations.list("run"));
	}

 @Test void moreThanSixFixedFragmentsRemainVisible() throws Exception {
  var rows=new ArrayList<Map<String,Object>>();var selected=new ArrayList<ZenflowReferenceSnapshots.Snapshot>();
  var json=new ObjectMapper();
  for(int id=30;id<37;id++){
   String hash=ZenflowRunAuthorization.hash("body"+id);
   rows.add(Map.of("citation_key","REFERENCE_"+id,"content_hash",hash,"reference_snapshot_id",(long)id,"revision_id",100L,"reference_digest","digest","retrieval_policy_json",json.writeValueAsString(policy.projection())));
   selected.add(new ZenflowReferenceSnapshots.Snapshot(Integer.toString(id),"1","10","1","v1",hash,"MATERIAL","Title"+id,"en","body"+id,"chapter","PUBLIC"));
  }
  publicRows=rows;when(snapshots.selected(eq("100"),eq(policy),eq("digest"),anyMap())).thenReturn(selected);
  assertEquals(7,citations.list("run").size());
 }

}
