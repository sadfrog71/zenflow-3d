package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.zenflow.companion.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.deepdimen.matrix.zenflow.identity.ZenflowUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Actual admin service, MySQL, Qdrant and original Engine/Skill; only vendor transport is
 * controlled.
 */
final class InvellePreviewIntegrationChecks {

	private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();

	private static volatile CountDownLatch entered;

	private static volatile CountDownLatch release;

	@TestConfiguration(proxyBeanMethods = false)
	static class Controlled {

		@Bean
		@Primary
		@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "zenflow.preview-qa",
				havingValue = "true")
		ZenflowModelGateway previewTransport(ZenflowModelService models, ZenflowProperties product, ObjectMapper json) {
			return new ZenflowModelGateway(models, product, json, (uri, key, body, sink, data) -> {
				if (REQUESTS.size() >= 8)
					throw new java.io.IOException("CONTROLLED_REQUEST_LIMIT");
				REQUESTS.add(body);
				var ready = entered;
				var gate = release;
				if (ready != null) {
					ready.countDown();
					if (!gate.await(15, TimeUnit.SECONDS))
						throw new java.io.IOException("CONTROLLED_GATE_TIMEOUT");
				}
				if (body.contains("DURABLE_PARTIAL_FAILURE_QA")) {
                    data.accept(json.writeValueAsString(Map.of("choices", List.of(Map.of("delta", Map.of("content", "Durable partial answer. ".repeat(80)))))));
                    throw new java.io.IOException("CONTROLLED_UPSTREAM_FAILURE");
                }
				data.accept(json.writeValueAsString(Map.of("choices",
						List.of(Map.of("delta", Map.of("content", "Controlled administrator preview answer."))))));
				data.accept("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
				data.accept("[DONE]");
			});
		}

	}

	static void verify(ConfigurableApplicationContext context, MatrixUser actor, String onlineRevision, String modelId,
			String sourceId) throws Exception {
		var jdbc = context.getBean(JdbcTemplate.class);
		assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("zenflow_core_rehearsal_"));
		assertEquals(23, jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zf_companion_preview'",
				Integer.class));
		var companions = context.getBean(ZenflowCompanionService.class);
		companions.publish(onlineRevision, Map.of("expectedBindingVersion", companions.detail().get("bindingVersion")));
		var draft = first(companions.createDraft(Map.of("fromRevisionId", onlineRevision)));
		String draftId = draft.get("id").toString();
		draft = first(companions.save(draftId,
				Map.of("expectedVersion", draft.get("version"), "authoring",
						Map.of("principles", "PREVIEW_READY_VARIANT: calm, truthful and present.", "coreSummary",
								"Dedicated comparison fixture", "citationMode", "ON_DEMAND", "examples", List.of()))));
		companions.prepare(draftId, Map.of("expectedVersion", draft.get("version"), "modelId", modelId));
		var preparation = context.getBean(ZenflowCompanionPrepareWorker.class);
		for (int i = 0; i < 20 && "PREPARING".equals(jdbc.queryForObject(
				"SELECT state FROM zf_companion_revision WHERE tenant_id=1 AND id=?", String.class, draftId)); i++)
			preparation.poll();
		assertEquals("READY", row(jdbc, draftId).get("state"));
		Object active = companions.detail().get("activeBindingRevisionId");
		int appSessions = jdbc.queryForObject(
				"SELECT COUNT(*) FROM ai_assistant_session WHERE tenant_id=1 AND owner_subject_type='APP_USER'",
				Integer.class);
		int contexts = jdbc.queryForObject("SELECT COUNT(*) FROM zf_companion_run_context", Integer.class);
		var previews = context.getBean(ZenflowPreviewService.class);
		var materials = Map.<String, Object>of("question", "public index fixture compare question", "locale", "en-US",
				"history", List.of(Map.of("role", "USER", "content", "MANUAL_PREVIEW_HISTORY"),
						Map.of("role", "ASSISTANT", "content", "Manual test response")));
		var firstRequest = new HashMap<>(materials);
		firstRequest.put("target", "ONLINE");
		var secondRequest = new HashMap<>(materials);
		secondRequest.putAll(Map.of("target", "REVISION", "revisionId", draftId, "expectedVersion",
				row(jdbc, draftId).get("version")));
		var online = previews.create(firstRequest);
		var selected = previews.create(secondRequest);
		await(jdbc, online.get("runId").toString());
		await(jdbc, selected.get("runId").toString());
		assertEquals(onlineRevision, online.get("revisionId"));
		assertEquals(draftId, selected.get("revisionId"));
		for (var trial : List.of(online, selected)) {
			String id = trial.get("previewId").toString(), run = trial.get("runId").toString();
			assertEquals("COMPLETED",
					jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?", String.class, run));
			assertEquals("SUCCEEDED", jdbc.queryForObject(
					"SELECT s.status FROM ai_skill_run s JOIN zf_companion_preview p ON p.skill_run_id=s.id WHERE p.id=?",
					String.class, id));
			assertEquals("ADMIN_USER", jdbc.queryForObject(
					"SELECT s.owner_subject_type FROM ai_assistant_session s JOIN zf_companion_preview p ON p.session_id=s.id WHERE p.id=?",
					String.class, id));
			var view = (Map<?, ?>) previews.detail(id).get("references");
			assertEquals("READY", view.get("state"));
			int expectedFragments = Math.min(6, jdbc.queryForObject(
					"SELECT COUNT(*) FROM zf_reference_snapshot WHERE tenant_id=1 AND companion_revision_id=? AND status='VALID'",
					Integer.class, trial.get("revisionId")));
			assertTrue(expectedFragments > 0);
			assertEquals(expectedFragments, ((List<?>) view.get("items")).size());
			for (Object raw : (List<?>) view.get("items")) {
				var item = (Map<?, ?>) raw;
				assertEquals(sourceId, item.get("documentId"));
				assertEquals("PUBLIC", item.get("citationVisibility"));
				assertTrue(item.get("excerpt").toString().contains("public index fixture"));
			}
			assertEquals(1,
					jdbc.queryForObject(
							"SELECT COUNT(*) FROM ai_assistant_run_event WHERE run_id=? AND event_type='RUN_COMPLETED'",
							Integer.class, run));
		}
		assertEquals(2, REQUESTS.size());
		assertTrue(REQUESTS.stream().allMatch(body -> body.contains("MANUAL_PREVIEW_HISTORY")));
		assertEquals(1, REQUESTS.stream().filter(body -> body.contains("PREVIEW_READY_VARIANT")).count());
		assertFalse(REQUESTS.stream().anyMatch(body -> body.contains("PRIVATE_CONTROLLED_ROOT")));
		assertEquals(active, companions.detail().get("activeBindingRevisionId"));
		assertEquals(appSessions,
				jdbc.queryForObject(
						"SELECT COUNT(*) FROM ai_assistant_session WHERE tenant_id=1 AND owner_subject_type='APP_USER'",
						Integer.class));
		assertEquals(contexts, jdbc.queryForObject("SELECT COUNT(*) FROM zf_companion_run_context", Integer.class));
		String owner = online.get("previewId").toString();
		jdbc.update("UPDATE zf_companion_preview SET administrator_id=administrator_id+100000 WHERE id=?", owner);
		try {
			assertEquals(404,
					assertThrows(ResponseStatusException.class, () -> previews.detail(owner)).getStatusCode().value());
			assertEquals(404,
					assertThrows(ResponseStatusException.class, () -> previews.cancel(owner)).getStatusCode().value());
		}
		finally {
			jdbc.update("UPDATE zf_companion_preview SET administrator_id=? WHERE id=?", actor.getId(), owner);
		}
		var user = (MatrixUser) context.getBean(ZenflowUserDetailsService.class)
			.loadUserByUsername(System.getenv("INVELLE_CORE_FIXTURE_EMAIL"));
		user.getAttributes().put(SecurityConstants.CLIENT_ID, "zenflow-ios");
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));
		try {
			assertThrows(ResponseStatusException.class, () -> previews.detail(owner));
		}
		finally {
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(actor, "", actor.getAuthorities()));
		}
		entered = new CountDownLatch(1);
		release = new CountDownLatch(1);
		try {
			var pending = previews.create(firstRequest);
			assertTrue(entered.await(15, TimeUnit.SECONDS));
			previews.cancel(pending.get("previewId").toString());
			release.countDown();
			await(jdbc, pending.get("runId").toString());
			assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?",
					String.class, pending.get("runId")));
			assertEquals(0,
					jdbc.queryForObject(
							"SELECT COUNT(*) FROM ai_assistant_run_event WHERE run_id=? AND event_type='RUN_COMPLETED'",
							Integer.class, pending.get("runId")));
		}
		finally {
			release.countDown();
			entered = null;
			release = null;
		}
		var partialRequest = new HashMap<>(firstRequest);
        partialRequest.put("question", "DURABLE_PARTIAL_FAILURE_QA public index fixture");
        var interrupted = previews.create(partialRequest);
        String failedRun = interrupted.get("runId").toString();
        await(jdbc, failedRun);
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?", String.class, failedRun));
        assertEquals("Durable partial answer. ".repeat(80), jdbc.queryForObject("SELECT m.content FROM ai_assistant_execution_run r JOIN ai_assistant_message m ON m.id=r.response_message_id WHERE r.id=?", String.class, failedRun));
        assertEquals("FAILED", jdbc.queryForObject("SELECT m.status FROM ai_assistant_execution_run r JOIN ai_assistant_message m ON m.id=r.response_message_id WHERE r.id=?", String.class, failedRun));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_assistant_run_event WHERE run_id=? AND event_type='RUN_COMPLETED'", Integer.class, failedRun));
        var restored = (com.deepdimen.matrix.assistant.api.AssistantRunContract.RunSnapshot) previews.detail(interrupted.get("previewId").toString()).get("run");
        assertEquals(com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus.FAILED, restored.status());
        assertEquals("Durable partial answer. ".repeat(80), restored.draftContent());
        System.out.println("INVELLE_DURABLE_PARTIAL_PASS: original MySQL atomic FAILED exchange retains validated partial after fresh administrator read, no COMPLETED event");
		var references = context.getBean(ZenflowReferenceAdmin.class);
		references.revoke(sourceId, Map.of("expectedSourceVersion", references.detail(sourceId).get("sourceVersion")));
		assertEquals(Map.of("state", "UNAVAILABLE", "items", List.of()), previews.detail(owner).get("references"));
		int before = REQUESTS.size();
		var denied = previews.create(firstRequest);
		await(jdbc, denied.get("runId").toString());
		assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?",
				String.class, denied.get("runId")));
		assertEquals(before, REQUESTS.size());

        var userAdmin=context.getBean(ZenflowUsers.class);
        var listed=userAdmin.list(null,"ALL",null,50);
        assertTrue(((List<?>)listed.get("items")).stream().anyMatch(item->((Map<?,?>)item).get("id").equals(user.getId().toString())));
        Long accountVersion=jdbc.queryForObject("SELECT auth_version FROM app_user WHERE tenant_id=1 AND user_id=?",Long.class,user.getId());
        try {
            var disabled=userAdmin.state(user.getId().toString(),Map.of("state","DISABLED","expectedVersion",accountVersion));
            assertEquals(accountVersion+1,disabled.get("version"));
            assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,()->context.getBean(ZenflowUserDetailsService.class).loadUserByUser(user));
            assertEquals(409,assertThrows(ResponseStatusException.class,()->userAdmin.state(user.getId().toString(),Map.of("state","ACTIVE","expectedVersion",accountVersion))).getStatusCode().value());
        } finally {
            Long latest=jdbc.queryForObject("SELECT auth_version FROM app_user WHERE tenant_id=1 AND user_id=?",Long.class,user.getId());
            userAdmin.state(user.getId().toString(),Map.of("state","ACTIVE","expectedVersion",latest));
        }
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,()->context.getBean(ZenflowUserDetailsService.class).loadUserByUser(user));
        System.out.println("INVELLE_USER_MANAGEMENT_PASS: actual scoped account projection, disable with auth generation, stale command denied, reenable leaves old identity invalid; fixture restored active");
		System.out.println(
				"INVELLE_ADMIN_PREVIEW_PASS: actual independent ADMIN Skill Engine, pinned online/READY variants, manual history, public recall, cancellation, ownership, revoked source denied before vendor transport, no APP session or run-context pollution");
	}

	private static void await(JdbcTemplate jdbc, String run) throws InterruptedException {
		for (int i = 0; i < 300; i++) {
			if (Set.of("COMPLETED", "FAILED", "CANCELLED")
				.contains(jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?", String.class,
						run)))
				return;
			Thread.sleep(100);
		}
		fail("Administrator preview did not become terminal");
	}

	private static Map<String, Object> row(JdbcTemplate jdbc, String id) {
		return jdbc.queryForMap("SELECT * FROM zf_companion_revision WHERE tenant_id=1 AND id=?", id);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> first(Map<String, Object> config) {
		return ((List<Map<String, Object>>) config.get("revisions")).get(0);
	}

}
