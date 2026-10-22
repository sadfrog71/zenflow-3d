package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.MatrixBootApplication;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.zenflow.companion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Dedicated real MySQL/Qdrant preparation with a test-only embedding response transport.
 */
public class InvelleReferenceIndexIntegrationMain {

	private static final AtomicInteger BATCHES = new AtomicInteger();

	private static final List<String> REQUEST_MODELS = new java.util.concurrent.CopyOnWriteArrayList<>();

	private static final AtomicBoolean FAIL_SECOND = new AtomicBoolean(true);

	@TestConfiguration(proxyBeanMethods = false)
	static class Controlled {

		@Bean
		@Primary
		ZenflowEmbeddingGateway controlledEmbedding(ZenflowEmbeddingModels models, ObjectMapper json) {
			return new ZenflowEmbeddingGateway(models, json, (uri, key, body) -> {
				var texts = json.readTree(body).path("input");
				assertTrue(texts.isArray());
				assertTrue(texts.size() <= 8);
				if (!texts.get(0).asText().equals("Invelle public reference connection test.")) {
					for (var text : texts)
						assertTrue(text.asText().contains("public index fixture"));
					REQUEST_MODELS.add(json.readTree(body).path("model").asText());
					if (BATCHES.incrementAndGet() == 2 && FAIL_SECOND.getAndSet(false))
						throw new java.io.IOException("CONTROLLED_EMBEDDING_FAILURE");
				}
				var data = new ArrayList<Map<String, Object>>();
				for (int i = 0; i < texts.size(); i++)
					data.add(Map.of("index", i, "embedding", List.of(.1, .2, .3)));
				return json.writeValueAsString(Map.of("data", data));
			});
		}

	}

	public static void main(String[] args) {
		if (!"1".equals(System.getenv("INVELLE_CORE_INTEGRATION")))
			throw new IllegalStateException("INTEGRATION_ENV_REQUIRED");
		if ("1".equals(System.getenv("INVELLE_PREVIEW_QA")) && "1".equals(System.getenv("INVELLE_REFERENCE_CHAT_QA")))
			throw new IllegalStateException("INTEGRATION_MODES_MUST_BE_EXCLUSIVE");
		int exit = 1;
		try (var context = new SpringApplication(MatrixBootApplication.class, Controlled.class,
				"1".equals(System.getenv("INVELLE_PREVIEW_QA")) ? InvellePreviewIntegrationChecks.Controlled.class
						: InvelleCoreIntegrationTest.Controlled.class)
			.run(args)) {
			var jdbc = context.getBean(JdbcTemplate.class);
			assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("zenflow_core_rehearsal_"));
			TenantContextHolder.setTenantId(1L);
			var actor = (MatrixUser) context.getBean(ZenflowAdminUserDetailsService.class)
				.loadUserByUsername("invelle-admin");
			actor.getAttributes().put(SecurityConstants.CLIENT_ID, ZenflowAdminUserDetailsService.CLIENT);
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(actor, "", actor.getAuthorities()));
			String key = System.getenv("ZENFLOW_VECTOR_API_KEY"), storeId = System.getenv("ZENFLOW_VECTOR_STORE_ID");
			assertNotNull(key);
			assertTrue(key.length() >= 32);
			assertEquals("710071", storeId);
			assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_embed_store WHERE store_id=? AND tenant_id<>1",
					Integer.class, storeId));
			jdbc.update(
					"INSERT INTO ai_embed_store(store_id,tenant_id,name,store_type,host,port,use_tls,api_key,del_flag) VALUES(?,1,'Invelle rehearsal only','qdrant','invelle-vector',6334,'0',?,'0') ON DUPLICATE KEY UPDATE api_key=VALUES(api_key)",
					storeId, key);
			var embeddings = context.getBean(ZenflowEmbeddingModels.class);
			var gateway = context.getBean(ZenflowEmbeddingGateway.class);
			String embeddingId = embedding(embeddings, gateway, "controlled-index-primary");
			var models = context.getBean(ZenflowModelService.class);
			var model = models.save(null,
					Map.of("name", "Controlled indexed companion", "modelName", "controlled-only", "provider",
							"FIXTURE", "baseUrl", "https://api.openai.com/v1", "apiKey", "invalid-controlled-only-key",
							"temperature", .6, "maxOutputTokens", 2048, "allowPrivateContext", false));
			String modelId = model.get("id").toString();
			models.recordTest(modelId, ((Number) model.get("version")).intValue());
			jdbc.update(
					"UPDATE ai_model SET ext_data=JSON_SET(ext_data,'$.zenflowPublished',true) WHERE tenant_id=1 AND id=?",
					modelId);
			var references = context.getBean(ZenflowReferenceAdmin.class);
			String body = "public index fixture ".repeat(1800);
			var source = references.create(Map.of("title", "Controlled public index", "content", body, "kind",
					"MATERIAL", "locale", "en", "citationVisibility", "PUBLIC", "approved", true));
			String sourceId = source.get("id").toString();
			var companions = context.getBean(ZenflowCompanionService.class);
			var revision = first(companions.createDraft(Map.of()));
			String revisionId = revision.get("id").toString();
			var policy = new ZenflowReferencePolicy(
					List.of(new ZenflowReferencePolicy.Reference(sourceId, "MATERIAL", "en", "PUBLIC")), 6, .7);
			revision = first(companions.save(revisionId,
					Map.of("expectedVersion", revision.get("version"), "retrievalPolicy", policy.projection())));
			Object online = companions.detail().get("activeBindingRevisionId");
			companions.prepare(revisionId, Map.of("expectedVersion", revision.get("version"), "modelId", modelId));
			assertEquals(0, BATCHES.get());
			var worker = context.getBean(ZenflowCompanionPrepareWorker.class);
			drive(worker, jdbc, revisionId);
			var failed = row(jdbc, revisionId);
			assertEquals("FAILED", failed.get("state"));
			assertEquals("EMBEDDING_UPSTREAM_UNAVAILABLE", failed.get("error_code"));
			assertNull(failed.get("skill_release_id"));
			assertEquals(embeddingId,
					context.getBean(ZenflowReferenceIndexes.class).verify(failed).get("embeddingModelId"));
			String digest = failed.get("reference_digest").toString(),
					indexDigest = failed.get("index_configuration_digest").toString();
			var storeConfiguration = new AiEmbedStoreEntity();
			storeConfiguration.setStoreId(Long.parseLong(storeId));
			storeConfiguration.setStoreType("qdrant");
			storeConfiguration.setHost("invelle-vector");
			storeConfiguration.setPort(6334);
			storeConfiguration.setUseTls("0");
			storeConfiguration.setApiKey(key);
			var store = context.getBean(EmbeddingStoreService.class)
				.embeddingStoreFixed(failed.get("index_namespace").toString(), storeConfiguration, 3, indexDigest);
			var partial = store.search(query(revisionId)).matches();
			assertEquals(8, partial.size());
			assertTrue(partial.size() < jdbc.queryForObject(
					"SELECT COUNT(*) FROM zf_reference_snapshot WHERE tenant_id=1 AND companion_revision_id=?",
					Integer.class, revisionId));
			String nextEmbedding = embedding(embeddings, gateway, "controlled-index-secondary");
			assertNotEquals(embeddingId, nextEmbedding);
			references.update(sourceId,
					Map.of("title", "Controlled edited source", "content", "public index fixture edited future version",
							"kind", "MATERIAL", "locale", "en", "citationVisibility", "PUBLIC", "approved", true,
							"expectedSourceVersion", source.get("sourceVersion")));
			companions.prepare(revisionId, Map.of("expectedVersion", failed.get("version"), "modelId", modelId));
			drive(worker, jdbc, revisionId);
			var ready = row(jdbc, revisionId);
			assertEquals("READY", ready.get("state"));
			assertEquals(digest, ready.get("reference_digest"));
			assertEquals(indexDigest, ready.get("index_configuration_digest"));
			assertEquals(online, companions.detail().get("activeBindingRevisionId"));
			var frozen = context.getBean(ZenflowReferenceSnapshots.class).verify(revisionId, policy, digest);
			assertTrue(frozen.snapshots().size() > 8);
			assertEquals(body,
					frozen.snapshots()
						.stream()
						.map(ZenflowReferenceSnapshots.Snapshot::content)
						.collect(java.util.stream.Collectors.joining()));
			var matches = store.search(query(revisionId)).matches();
			assertEquals(frozen.snapshots().size(), matches.size());
			assertEquals(frozen.snapshots().size(),
					matches.stream().map(match -> match.embeddingId()).distinct().count());
			for (var match : matches) {
				var snapshot = frozen.snapshots()
					.stream()
					.filter(fragment -> fragment.id().equals(match.embedded().metadata().getString("snapshotId")))
					.findFirst()
					.orElseThrow();
				assertEquals(snapshot.content(), match.embedded().text());
				assertEquals(snapshot.contentHash(), match.embedded().metadata().getString("sourceHash"));
				assertEquals(digest, match.embedded().metadata().getString("referenceDigest"));
				assertEquals(indexDigest, match.embedded().metadata().getString("indexConfigurationDigest"));
			}
			assertEquals(nextEmbedding, embeddings.active().get("id"));
			assertFalse(REQUEST_MODELS.isEmpty());
			assertTrue(REQUEST_MODELS.stream().allMatch("controlled-index-primary"::equals));
			assertFalse(ready.get("index_configuration_json").toString().contains(key));
			System.out.println(
					"INVELLE_REFERENCE_INDEX_PASS: real fixed MySQL descriptor, actual Qdrant batches, failed partial index, stable retry, unchanged frozen bodies and model despite active edits, managed READY without online activation");
			if ("1".equals(System.getenv("INVELLE_PREVIEW_QA")))
				InvellePreviewIntegrationChecks.verify(context, actor, revisionId, modelId, sourceId);
			if ("1".equals(System.getenv("INVELLE_REFERENCE_CHAT_QA"))) {
				models.activate(modelId, ((Number) model.get("version")).intValue());
				companions.publish(revisionId,
						Map.of("expectedBindingVersion", companions.detail().get("bindingVersion")));
				var user = (MatrixUser) context
					.getBean(com.deepdimen.matrix.zenflow.identity.ZenflowUserDetailsService.class)
					.loadUserByUsername(System.getenv("INVELLE_CORE_FIXTURE_EMAIL"));
				user.getAttributes().put(SecurityConstants.CLIENT_ID, "zenflow-ios");
				SecurityContextHolder.getContext()
					.setAuthentication(new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));
				var conversations = context
					.getBean(com.deepdimen.matrix.zenflow.conversation.ZenflowConversationFacade.class);
				var citations = context.getBean(com.deepdimen.matrix.zenflow.conversation.ZenflowCitations.class);
				String session = conversations
					.create(Map.of("title", "Public reference real-chain fixture", "locale", "en-US"))
					.get("id")
					.toString();
				String run = conversations
					.send(session,
							Map.of("clientMessageId", UUID.randomUUID().toString(), "text",
									"public index fixture current question", "context", Map.of("capsules", List.of())))
					.get("runId")
					.toString();
				await(jdbc, run);
				assertEquals("COMPLETED", jdbc
					.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?", String.class, run));
				assertEquals("SUCCEEDED", jdbc.queryForObject(
						"SELECT s.status FROM ai_skill_run s JOIN zf_companion_run_context x ON x.skill_run_id=s.id WHERE x.run_id=?",
						String.class, run));
				var cards = citations.list(run);
				assertEquals(1, cards.size());
				assertEquals("REFERENCE", cards.get(0).get("sourceType"));
				assertEquals("PUBLIC", cards.get(0).get("visibility"));
				assertTrue(cards.get(0).get("excerpt").toString().contains("public index fixture"));
				assertFalse(cards.get(0).containsKey("noteId"));
				assertEquals(6, jdbc.queryForObject(
						"SELECT COUNT(*) FROM zf_run_source WHERE run_id=? AND source_kind='REFERENCE' AND use_role='DIRECT'",
						Integer.class, run));
				assertEquals(1, jdbc.queryForObject(
						"SELECT COUNT(*) FROM ai_assistant_run_event WHERE run_id=? AND event_type='CITATION_AVAILABLE'",
						Integer.class, run));
				assertTrue(REQUEST_MODELS.stream().allMatch("controlled-index-primary"::equals));
				SecurityContextHolder.getContext()
					.setAuthentication(new UsernamePasswordAuthenticationToken(actor, "", actor.getAuthorities()));
				references.revoke(sourceId,
						Map.of("expectedSourceVersion", references.detail(sourceId).get("sourceVersion")));
				SecurityContextHolder.getContext()
					.setAuthentication(new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));
				assertTrue(citations.list(run).isEmpty());
				int before = InvelleCoreIntegrationTest.invocations.get();
				String denied = conversations
					.send(session,
							Map.of("clientMessageId", UUID.randomUUID().toString(), "text",
									"public index fixture after revocation", "context", Map.of("capsules", List.of())))
					.get("runId")
					.toString();
				await(jdbc, denied);
				assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?",
						String.class, denied));
				assertEquals(before, InvelleCoreIntegrationTest.invocations.get());
				System.out.println(
						"INVELLE_PUBLIC_REFERENCE_CHAT_PASS: actual fixed Qdrant recall, Native Skill Engine completion, public egress without private grant, actual citation card and event, revoked source blocked before model transport");
			}
			exit = 0;
		}
		catch (Throwable failure) {
			System.out.println("INVELLE_REFERENCE_INDEX_FAIL: " + failure.getClass().getSimpleName());
			for (var frame : failure.getStackTrace())
				if (frame.getClassName().startsWith("com.deepdimen"))
					System.out.println("QA_FAILURE_FRAME " + frame.getClassName() + "." + frame.getMethodName() + ":"
							+ frame.getLineNumber());
		}
		finally {
			SecurityContextHolder.clearContext();
			TenantContextHolder.clear();
		}
		System.exit(exit);
	}

	private static void await(JdbcTemplate jdbc, String run) throws InterruptedException {
		for (int i = 0; i < 600; i++) {
			String status = jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?",
					String.class, run);
			if (Set.of("COMPLETED", "FAILED", "CANCELLED").contains(status))
				return;
			Thread.sleep(100);
		}
		fail("Public controlled run did not become terminal");
	}

	private static String embedding(ZenflowEmbeddingModels models, ZenflowEmbeddingGateway gateway, String name) {
		var model = models.save(null, Map.of("name", name, "modelName", name, "provider", "FIXTURE", "baseUrl",
				"https://api.openai.com/v1", "apiKey", "invalid-controlled-only-key", "dimensions", 3));
		String id = model.get("id").toString();
		int version = ((Number) model.get("version")).intValue();
		gateway.probe(id, version);
		models.activate(id, version);
		return id;
	}

	private static EmbeddingSearchRequest query(String revisionId) {
		return EmbeddingSearchRequest.builder()
			.queryEmbedding(Embedding.from(new float[] { .1f, .2f, .3f }))
			.maxResults(100)
			.minScore(.9)
			.filter(new IsEqualTo("companionRevisionId", revisionId))
			.build();
	}

	private static void drive(ZenflowCompanionPrepareWorker worker, JdbcTemplate jdbc, String id) {
		for (int i = 0; i < 20 && "PREPARING".equals(row(jdbc, id).get("state")); i++)
			worker.poll();
	}

	private static Map<String, Object> row(JdbcTemplate jdbc, String id) {
		return jdbc.queryForMap("SELECT * FROM zf_companion_revision WHERE tenant_id=1 AND id=?", id);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> first(Map<String, Object> configuration) {
		return ((List<Map<String, Object>>) configuration.get("revisions")).get(0);
	}

}
