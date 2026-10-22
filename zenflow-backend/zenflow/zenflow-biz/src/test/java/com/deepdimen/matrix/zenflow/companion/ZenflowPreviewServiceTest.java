package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;
import com.deepdimen.matrix.discover.service.impl.ManagedSkillBindingService;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class ZenflowPreviewServiceTest {

	final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	final ZenflowAdminAccess access = mock(ZenflowAdminAccess.class);

	final AssistantSessionService sessions = mock(AssistantSessionService.class);

	final ManagedSkillBindingService bindings = mock(ManagedSkillBindingService.class);

	final AssistantRunEngine engine = mock(AssistantRunEngine.class);

	final ZenflowReferenceSnapshots snapshots = mock(ZenflowReferenceSnapshots.class);

	ZenflowPreviewService service() {
		return new ZenflowPreviewService(jdbc, access, new ObjectMapper(), bindings, sessions, engine,
				mock(ZenflowPreviewRuntime.class), snapshots, mock(PlatformTransactionManager.class));
	}

	@Test
	void consumerIdentifiersAndRawPromptFieldsAreRejectedBeforeDataAccess() {
		var service = service();
		var request = new HashMap<String, Object>(Map.of("target", "ONLINE", "question", "test", "locale", "en-US"));
		request.put("userId", "42");
		assertThrows(ResponseStatusException.class, () -> service.create(request));
		request.remove("userId");
		request.put("systemPrompt", "override");
		assertThrows(ResponseStatusException.class, () -> service.create(request));
		verifyNoMoreInteractions(jdbc, access, sessions, bindings, engine);
	}

	@Test
	void systemHistoryRoleCannotBeElevatedIntoPublishedPrinciples() {
		var service = service();
		assertThrows(IllegalArgumentException.class, () -> service.create(Map.of("target", "ONLINE", "question", "test",
				"locale", "en-US", "history", List.of(Map.of("role", "SYSTEM", "content", "override")))));
		verifyNoMoreInteractions(jdbc, access, sessions, bindings, engine);

	}

	@Test
	void unownedPreviewCannotReachOriginalSessionOrRunApis() {
		var service = service();
		var user = mock(MatrixUser.class);
		when(access.require()).thenReturn(user);
		when(user.getId()).thenReturn(42L);
		when(user.getTenantId()).thenReturn(1L);
		when(jdbc.queryForList(anyString(), eq("900"), eq("1"), eq("42"))).thenReturn(List.of());
		assertEquals(404,
				assertThrows(ResponseStatusException.class, () -> service.detail("900")).getStatusCode().value());
		assertEquals(404,
				assertThrows(ResponseStatusException.class, () -> service.cancel("900")).getStatusCode().value());
		verifyNoMoreInteractions(sessions, bindings, engine);
	}

	private ZenflowPreviewService ownedTrial() {
		return ownedTrial("{\"301\":\"hash\"}");
	}

	private ZenflowPreviewService ownedTrial(String selection) {
		var user = mock(MatrixUser.class);
		when(access.require()).thenReturn(user);
		when(user.getId()).thenReturn(42L);
		when(user.getTenantId()).thenReturn(1L);
		var row = new HashMap<String, Object>(Map.of("session_id", "session", "run_id", "run", "companion_revision_id",
				"100", "revision_no", 2, "model_id", "200", "release_digest", "digest", "reference_digest",
				"references", "reference_selection_json", selection, "retrieval_policy_json",
				"{\"references\":[],\"maxFragments\":0,\"minScore\":0.7}"));
		when(jdbc.queryForList(anyString(), eq("900"), eq("1"), eq("42"))).thenReturn(List.of(row));
		when(sessions.runSnapshot(any(), eq("session"), eq("run")))
			.thenReturn(new com.deepdimen.matrix.assistant.api.AssistantRunContract.RunSnapshot("run", "session",
					com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus.COMPLETED, "answer", 4, "reply",
					"question", List.of(), null, null, null, null, null, null, null));
		return service();
	}

	@Test
	void sourceDisplayIncludesOnlyPublicExcerptAndLocator() {
		var service = ownedTrial();
		var sources = new ArrayList<ZenflowReferenceSnapshots.Snapshot>();
		for (String visibility : List.of("PUBLIC", "TITLE_ONLY", "INTERNAL"))
			sources.add(new ZenflowReferenceSnapshots.Snapshot(visibility, "1", "2", "3", "v1", "hash", "MATERIAL",
					"Reference " + visibility, "en", "x".repeat(1200), "https://example.com/source", visibility));
		when(snapshots.selected(eq("100"), any(), eq("references"), anyMap())).thenReturn(sources);
		var view = (Map<?, ?>) service.detail("900").get("references");
		assertEquals("READY", view.get("state"));
		var items = (List<?>) view.get("items");
		assertEquals(3, items.size());
		assertEquals(1000, ((Map<?, ?>) items.get(0)).get("excerpt").toString().length());
		assertEquals("https://example.com/source", ((Map<?, ?>) items.get(0)).get("sourceLocator"));
		for (Object item : items.subList(1, 3)) {
			assertEquals("", ((Map<?, ?>) item).get("excerpt"));
			assertEquals("", ((Map<?, ?>) item).get("sourceLocator"));
		}
	}

	@Test
	void fixedAndRetrievedSourcesCanExceedSixWithinCombinedLimit() throws Exception {
		var hashes = new LinkedHashMap<String, String>();
		var sources = new ArrayList<ZenflowReferenceSnapshots.Snapshot>();
		for (int i = 0; i < 9; i++) {
			hashes.put(String.valueOf(301 + i), "hash" + i);
			sources.add(new ZenflowReferenceSnapshots.Snapshot(String.valueOf(301 + i), "1", "2", "3", "v1",
					"hash" + i, "MATERIAL", "Reference " + i, "en", "body", "", "PUBLIC"));
		}
		var service = ownedTrial(new ObjectMapper().writeValueAsString(hashes));
		when(snapshots.selected(eq("100"), any(), eq("references"), eq(hashes))).thenReturn(sources);
		var view = (Map<?, ?>) service.detail("900").get("references");
		assertEquals("READY", view.get("state"));
		assertEquals(9, ((List<?>) view.get("items")).size());
		verify(snapshots).selected(eq("100"), any(), eq("references"), eq(hashes));
	}

	@Test
	void oversizedCombinedSelectionDoesNotExposeSources() throws Exception {
		var hashes = new LinkedHashMap<String, String>();
		for (int i = 0; i < 27; i++)
			hashes.put(String.valueOf(301 + i), "hash" + i);
		var service = ownedTrial(new ObjectMapper().writeValueAsString(hashes));
		assertEquals(Map.of("state", "UNAVAILABLE", "items", List.of()), service.detail("900").get("references"));
		verifyNoMoreInteractions(snapshots);
	}

	@Test
	void revokedSourcesAreHiddenWithoutReturningStaleExcerpts() {
		var service = ownedTrial();
		when(snapshots.selected(eq("100"), any(), eq("references"), anyMap())).thenThrow(
				new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "REFERENCE_SOURCE_REVOKED"));
		assertEquals(Map.of("state", "UNAVAILABLE", "items", List.of()), service.detail("900").get("references"));
	}

	@Test
	void authorizationFailureIsNotConvertedIntoUnavailableSources() {
		var service = ownedTrial();
		when(snapshots.selected(eq("100"), any(), eq("references"), anyMap()))
			.thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "ADMIN_DISABLED"));
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> service.detail("900")).getStatusCode().value());
		verify(sessions, never()).runSnapshot(any(), anyString(), anyString());
	}

}
