package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.entity.*;
import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MarketSkillRequestStoreTest {

	final MarketSkillRequestMapper requests = mock(MarketSkillRequestMapper.class);

	final DifyChatRecordMapper conversations = mock(DifyChatRecordMapper.class);

	final AiAgentService agents = mock(AiAgentService.class);

	final TenantUserDirectory users = mock(TenantUserDirectory.class);

	final MarketSkillRequestStore store = new MarketSkillRequestStore(requests, conversations, agents, users,
			new ObjectMapper());

	MarketSkillRequestEntity saved;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		when(user.getUsername()).thenReturn("user");
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
		when(users.isAssignableUser(7L, 3L)).thenReturn(true);
		var agent = new AiAgentEntity();
		agent.setId(88L);
		agent.setTenantId("7");
		agent.setStatus("published");
		when(agents.getById(88L)).thenReturn(agent);
		var detail = new AiAgentVO();
		detail.setId(88L);
		detail.setAccessible(true);
		when(agents.getAgentDetail(88L)).thenReturn(detail);
		when(requests.reserve(any())).thenAnswer(call -> {
			if (saved == null)
				saved = call.getArgument(0);
			return 1;
		});
		when(requests.findRequest(7L, 3L, "request")).thenAnswer(call -> saved);
	}

	@Test
	void onlyKnowledgeRequestsMayPersistAnExplicitResultReference() {
		String body = "{\"query\":\"question\",\"result_context_request_id\":\"previous\"}";
		agents.getById(88L).setAgentType("knowledge");
		assertTrue(store.prepare(88L, "skill-1", "request", body).getInputSnapshot().contains("previous"));
		agents.getById(88L).setAgentType("dify");
		assertThrows(IllegalArgumentException.class, () -> store.prepare(88L, "skill-1", "request", body));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void identicalReorderedInputReusesBusinessId() {
		var first = store.prepare(88L, "skill-1", "request", "{\"query\":\"hello\",\"inputs\":{\"b\":2,\"a\":1}}");
		var second = store.prepare(88L, "skill-1", "request", "{\"inputs\":{\"a\":1,\"b\":2},\"query\":\"hello\"}");
		assertEquals(first.getId(), second.getId());
	}

	@Test
	void changedInputOrBindingCannotReuseKey() {
		store.prepare(88L, "skill-1", "request", "{\"query\":\"hello\"}");
		assertThrows(IllegalArgumentException.class,
				() -> store.prepare(88L, "skill-1", "request", "{\"query\":\"changed\"}"));
		assertThrows(IllegalArgumentException.class,
				() -> store.prepare(88L, "skill-2", "request", "{\"query\":\"hello\"}"));
	}

	@Test
	void unownedConversationCannotBeSaved() {
		assertThrows(IllegalArgumentException.class,
				() -> store.prepare(88L, "skill-1", "request", "{\"query\":\"hello\",\"conversation_id\":\"other\"}"));
		verifyZeroInteractions(requests);
	}

	@Test
	void clientInstructionAndDuplicateFieldsRejected() {
		assertThrows(IllegalArgumentException.class, () -> store.prepare(88L, "skill-1", "request",
				"{\"query\":\"hello\",\"inputs\":{\"extended_system_prompt\":\"override\"}}"));
		assertThrows(IllegalArgumentException.class,
				() -> store.prepare(88L, "skill-1", "request", "{\"query\":\"hello\",\"query\":\"changed\"}"));
		verifyZeroInteractions(requests);
	}

	@Test
	void invalidTenantCannotSave() {
		TenantContextHolder.setTenantId(8L);
		assertThrows(IllegalArgumentException.class,
				() -> store.prepare(88L, "skill-1", "request", "{\"query\":\"hello\"}"));
		verifyZeroInteractions(requests);
	}

	com.deepdimen.matrix.skill.api.SkillContract.Identity identity(String id) {
		return new com.deepdimen.matrix.skill.api.SkillContract.Identity("7", "3", "3", "market-skill:" + id);
	}

	com.deepdimen.matrix.skill.api.SkillContract.Consumer consumer() {
		return new com.deepdimen.matrix.skill.api.SkillContract.Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");
	}

	String prepareOwned() {
		var row = store.prepare(88L, "skill-1", "request", "{\"query\":\"hello\"}");
		when(requests.findOwned(7L, 3L, row.getId())).thenReturn(row);
		return row.getId().toString();
	}

	@Test
	void authorizedInputIsReadOnlyAndMustMatchSavedBody() {
		String id = prepareOwned();
		assertEquals(saved.getInputSnapshot(),
				store.authorizeInput(identity(id), consumer(), id, "{\"query\":\"hello\"}"));
		assertThrows(IllegalArgumentException.class,
				() -> store.authorizeInput(identity(id), consumer(), id, "{\"query\":\"replace\"}"));
		verify(requests, times(1)).reserve(any());
	}

	@Test
	void membershipRevocationBlocksPreviouslySavedInput() {
		String id = prepareOwned();
		when(users.isAssignableUser(7L, 3L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class, () -> store.load(identity(id), consumer(), id));
	}

	@Test
	void alteredSavedBodyFailsDigestCheck() {
		String id = prepareOwned();
		saved.setInputSnapshot("{\"query\":\"altered\"}");
		assertThrows(IllegalArgumentException.class, () -> store.load(identity(id), consumer(), id));
	}

	@Test
	void correlationAloneDoesNotPermitOtherActor() {
		String id = prepareOwned();
		var other = new com.deepdimen.matrix.skill.api.SkillContract.Identity("7", "4", "4", "market-skill:" + id);
		assertThrows(IllegalArgumentException.class, () -> store.load(other, consumer(), id));
	}

	@Test
	void existingLocalUploadReferencesArePreservedWithoutCreatingAnotherFileRecord() {
		String body = "{\"query\":\"question\",\"files\":[{\"type\":\"document\",\"transfer_method\":\"local_file\",\"upload_file_id\":\"12345678-1234-1234-1234-123456789abc\"}]}";
		var row = store.prepare(88L, "skill-1", "request", body);
		assertTrue(row.getInputSnapshot().contains("12345678-1234-1234-1234-123456789abc"));
	}

	@Test
	void alternateFileLookupFieldsAndRemoteUrlsCannotBypassNativeUploadScope() {
		for (String file : List.of("{}",
				"{\"type\":\"document\",\"transfer_method\":\"remote_url\",\"url\":\"https://example.com/file\"}",
				"{\"type\":\"document\",\"transfer_method\":\"local_file\",\"upload_file_id\":\"other\"}",
				"{\"type\":\"document\",\"transfer_method\":\"local_file\",\"upload_file_id\":\"12345678-1234-1234-1234-123456789abc\",\"reference\":\"override\"}"))
			assertThrows(IllegalArgumentException.class, () -> store.prepare(88L, "skill-1", "request",
					"{\"query\":\"question\",\"files\":[" + file + "]}"));
		verifyZeroInteractions(requests);
	}

	@Test
	void recoveryReusesOwnedRequestAndRechecksCurrentAgentAccess() {
		String id = prepareOwned();
		assertEquals(saved, store.findByClientRequest(88L, "request"));
		when(users.isAssignableUser(7L, 3L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class, () -> store.findByClientRequest(88L, "request"));
	}

	@Test
	void recoveryCannotReadAnotherActorOrAgentUsingAClientKey() {
		prepareOwned();
		saved.setActorId(4L);
		assertThrows(IllegalArgumentException.class, () -> store.findByClientRequest(88L, "request"));
		saved.setActorId(3L);
		saved.setAgentId(89L);
		assertThrows(IllegalArgumentException.class, () -> store.findByClientRequest(88L, "request"));
	}

	@Test
	void recoveryOfAbsentRequestDoesNotReserveOrExecute() {
		assertNull(store.findByClientRequest(88L, "request"));
		verify(requests, never()).reserve(any());
		assertThrows(IllegalArgumentException.class, () -> store.findByClientRequest(88L, "../other"));
	}

	private com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot conversationRelease() {
		var c = com.deepdimen.matrix.skill.api.SkillContract.RuntimeKind.PROMPT;
		return new com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot("1", "2", "digest", "9", c,
				new com.deepdimen.matrix.skill.api.SkillContract.ExecutorRef("PROMPT", "DIFY", "app-1", "a".repeat(64)),
				"{}", "{}", "reviewed", List.of(new com.deepdimen.matrix.skill.api.SkillContract.ResourceRef("app-1",
						"config-1", "a".repeat(64))),
				"{}", List.of());
	}

	private MarketSkillRequestMapper.ConversationOrigin origin;

	private String prepareContinuation(String inputs) {
		var initial = store.prepare(88L, "skill-1", "request",
				"{\"query\":\"first\",\"inputs\":{\"topic\":\"water\"}}");
		origin = new MarketSkillRequestMapper.ConversationOrigin();
		origin.setInputSnapshot(initial.getInputSnapshot());
		origin.setInputDigest(initial.getInputDigest());
		origin.setBindingKey("skill-1");
		origin.setReleaseId("2");
		origin.setExecutorVersion("a".repeat(64));
		saved = null;
		when(conversations.selectCount(any())).thenReturn(1L);
		var current = store.prepare(88L, "skill-1", "request",
				"{\"query\":\"follow up\",\"conversation_id\":\"conversation\",\"inputs\":" + inputs + "}");
		when(requests.findOwned(7L, 3L, current.getId())).thenReturn(current);
		when(requests.findConversationOrigin(7L, 3L, 88L, "conversation")).thenReturn(List.of(origin));
		return current.getId().toString();
	}

	@Test
	void knownConversationContinuesWithSameReleaseConfigurationAndInputs() {
		String id = prepareContinuation("{\"topic\":\"water\"}");
		assertDoesNotThrow(() -> store.requireConversation(identity(id), consumer(), id, conversationRelease()));
		origin.setReleaseId("other");
		assertThrows(IllegalArgumentException.class,
				() -> store.requireConversation(identity(id), consumer(), id, conversationRelease()));
		origin.setReleaseId("2");
		origin.setExecutorVersion("b".repeat(64));
		assertThrows(IllegalArgumentException.class,
				() -> store.requireConversation(identity(id), consumer(), id, conversationRelease()));
	}

	@Test
	void changedInitialVariablesRequireNewConversation() {
		String id = prepareContinuation("{\"topic\":\"gas\"}");
		var failure = assertThrows(IllegalArgumentException.class,
				() -> store.requireConversation(identity(id), consumer(), id, conversationRelease()));
		assertEquals("DIFY_CONVERSATION_INPUTS_CHANGED", failure.getMessage());
	}

	@Test
	void unknownOrAmbiguousOriginCannotBorrowCurrentAppConfiguration() {
		String id = prepareContinuation("{\"topic\":\"water\"}");
		when(requests.findConversationOrigin(7L, 3L, 88L, "conversation")).thenReturn(List.of());
		assertThrows(IllegalArgumentException.class,
				() -> store.requireConversation(identity(id), consumer(), id, conversationRelease()));
		when(requests.findConversationOrigin(7L, 3L, 88L, "conversation")).thenReturn(List.of(origin, origin));
		assertThrows(IllegalArgumentException.class,
				() -> store.requireConversation(identity(id), consumer(), id, conversationRelease()));
	}

	@Test void legacyContinuationChecksOnlyCurrentTenantAndActorRecords(){
        when(requests.hasManagedConversation(7L,3L,88L,"conversation")).thenReturn(true);
        assertTrue(store.isManagedConversation(88L,"conversation",false));
        assertFalse(store.isManagedConversation(88L,"ordinary",false));
        verify(requests).hasManagedConversation(7L,3L,88L,"conversation");
        when(users.isAssignableUser(7L,3L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->store.isManagedConversation(88L,"conversation",false));
        verify(requests,times(1)).hasManagedConversation(7L,3L,88L,"conversation");
    }

	@Test
	void newChatAndFrozenAssistantNeverReadManagedRecords() {
		assertFalse(store.isManagedConversation(88L, "", false));
		assertFalse(store.isManagedConversation(AiAgentService.DEFAULT_AGENT_ID, "conversation", false));
		verifyZeroInteractions(requests);
	}

	@Test
	void observerCapturesAuthorizedScopeBeforeReactiveThreadAndRejectsMissingWrite() {
		String id = prepareOwned();
		var observe = store.conversationObserver(identity(id), consumer(), id);
		when(requests.observeConversation(saved.getId(), 7L, 3L, 88L, saved.getInputDigest(), "remote"))
			.thenAnswer(call -> {
				assertEquals(7L, TenantContextHolder.getTenantId());
				return 1;
			});
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
		assertDoesNotThrow(() -> observe.accept("remote"));
		assertNull(TenantContextHolder.getTenantId());
		assertThrows(IllegalStateException.class, () -> observe.accept("other"));
		verify(requests).observeConversation(saved.getId(), 7L, 3L, 88L, saved.getInputDigest(), "remote");
	}

	@Test
	void disabledLegacySchemaOnlyAllowsTheExactMissingTableCondition() {
		var missing = new org.springframework.jdbc.BadSqlGrammarException("inspect", "select",
				new java.sql.SQLException("missing table", "42S02", 1146));
		when(requests.hasManagedConversation(7L, 3L, 88L, "conversation")).thenThrow(missing);
		assertFalse(store.isManagedConversation(88L, "conversation", true));
		assertThrows(org.springframework.jdbc.BadSqlGrammarException.class,
				() -> store.isManagedConversation(88L, "conversation", false));
		doThrow(new org.springframework.jdbc.BadSqlGrammarException("inspect", "select",
				new java.sql.SQLException("missing column", "42S22", 1054)))
			.when(requests)
			.hasManagedConversation(7L, 3L, 88L, "conversation");
		assertThrows(org.springframework.jdbc.BadSqlGrammarException.class,
				() -> store.isManagedConversation(88L, "conversation", true));
		doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline")).when(requests)
			.hasManagedConversation(7L, 3L, 88L, "conversation");
		assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
				() -> store.isManagedConversation(88L, "conversation", true));
	}

}
