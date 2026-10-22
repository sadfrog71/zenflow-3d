package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.zenflow.admin.ZenflowEmbeddingGateway;
import com.deepdimen.matrix.zenflow.admin.ZenflowEmbeddingModels;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class ZenflowReferenceIndexesTest {

	private JdbcTemplate jdbc;

	private TransactionTemplate tx;

	private ZenflowReferenceIndexes indexes;

	private ZenflowEmbeddingModels models;

	private ZenflowEmbeddingGateway embeddings;

	private EmbeddingStoreService stores;

	private EmbeddingStore<TextSegment> vectorStore;

	private final Map<String, Object> model = Map.of("id", "42", "dimensions", 2, "version", 1);

	private final ZenflowReferencePolicy policy = new ZenflowReferencePolicy(
			List.of(new ZenflowReferencePolicy.Reference("10", "MATERIAL", "en", "PUBLIC")), 6, .7);

	@BeforeEach
	void setup() {
		var data = new JdbcDataSource();
		data.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		jdbc = new JdbcTemplate(data);
		tx = new TransactionTemplate(new DataSourceTransactionManager(data));
		jdbc.execute(
				"CREATE TABLE zf_companion_revision(id BIGINT,tenant_id BIGINT,state VARCHAR(16),retrieval_policy_json VARCHAR(10000),index_namespace VARCHAR(160),index_configuration_json VARCHAR(8000),index_configuration_digest VARCHAR(64),reference_digest VARCHAR(64))");
		jdbc.execute(
				"CREATE TABLE ai_dataset(id BIGINT,tenant_id BIGINT,collection_name VARCHAR(100),del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE ai_document(id BIGINT,dataset_id BIGINT,name VARCHAR(200),document_config VARCHAR(4000),del_flag CHAR(1),file_status CHAR(1),tenant_id BIGINT)");
		jdbc.execute(
				"CREATE TABLE ai_slice(id BIGINT,document_id BIGINT,content VARCHAR(20000),del_flag CHAR(1),tenant_id BIGINT)");
		jdbc.execute(
				"CREATE TABLE zf_reference_snapshot(id BIGINT,tenant_id BIGINT,document_id BIGINT,status VARCHAR(24),companion_revision_id BIGINT,dataset_id BIGINT,slice_id BIGINT,source_version VARCHAR(64),source_hash VARCHAR(64),reference_kind VARCHAR(24),title VARCHAR(200),locale VARCHAR(16),content CLOB,source_locator VARCHAR(512),citation_visibility VARCHAR(16))");
		jdbc.update("INSERT INTO ai_dataset VALUES(1,1,'invelle-public-1','0')");
		jdbc.update("INSERT INTO ai_document VALUES(10,1,'Public material',?,'0','1',1)",
				"{\"invelleAudience\":\"PUBLIC\",\"kind\":\"MATERIAL\",\"locale\":\"en\",\"citationVisibility\":\"PUBLIC\",\"invelleCompanionApproved\":true,\"invelleSourceVersion\":\"v1\"}");
		jdbc.execute(
				"CREATE TABLE ai_embed_store(store_id BIGINT,tenant_id BIGINT,store_type VARCHAR(20),host VARCHAR(100),port INT,use_tls CHAR(1),api_key VARCHAR(255),del_flag CHAR(1))");
		jdbc.update("INSERT INTO zf_companion_revision(id,tenant_id,state) VALUES(100,1,'DRAFT')");
		jdbc.update("INSERT INTO ai_embed_store VALUES(7,1,'qdrant','invelle-vector',6334,'0',?,'0')",
				"public-fixture-key-sentinel".repeat(2));
		models = mock(ZenflowEmbeddingModels.class);
		embeddings = mock(ZenflowEmbeddingGateway.class);
		stores = mock(EmbeddingStoreService.class);
		vectorStore = mock(EmbeddingStore.class);
		when(models.active()).thenReturn(model);
		when(models.publishedSnapshot("42")).thenReturn(model);
		when(models.digest(model)).thenReturn("a".repeat(64));
		when(stores.embeddingStoreFixed(anyString(), any(), eq(2), anyString())).thenReturn(vectorStore);
		var product = new ZenflowProperties();
		product.setTenantId(1);
		indexes = new ZenflowReferenceIndexes(jdbc, models, embeddings, stores, new ObjectMapper(),
				new MockEnvironment().withProperty("ZENFLOW_VECTOR_STORE_ID", "7"), product);
	}

	@Test
	void pinsWithoutCredentialAndRetryNeverUsesNewActiveModel() {
		pin();
		var row = row();
		String descriptor = row.get("index_configuration_json").toString();
		assertFalse(descriptor.contains("sentinel"));
		assertEquals("invelle_public_1_100", row.get("index_namespace"));
		reset(models);
		when(models.publishedSnapshot("42")).thenReturn(model);
		when(models.digest(model)).thenReturn("a".repeat(64));
		tx.executeWithoutResult(ignored -> indexes.pin(row(), policy));
		verify(models, never()).active();
		assertEquals(row.get("index_configuration_digest"), row().get("index_configuration_digest"));
	}

	@Test
	void rejectsUntrustedStoreTenantAndNamespaceOrDescriptorTampering() {
		jdbc.update("UPDATE ai_embed_store SET tenant_id=2");
		assertThrows(ResponseStatusException.class, this::pin);
		jdbc.update("UPDATE ai_embed_store SET tenant_id=1");
		pin();
		jdbc.update("UPDATE zf_companion_revision SET index_namespace='foreign'");
		assertThrows(ResponseStatusException.class, () -> indexes.verify(row()));
		jdbc.update(
				"UPDATE zf_companion_revision SET index_namespace='invelle_public_1_100',index_configuration_json='{}'");
		assertThrows(ResponseStatusException.class, () -> indexes.verify(row()));
		verifyZeroInteractions(embeddings, stores);
	}

	@Test
	void modelAndStoreChangesAreRejectedBeforeNetworkUse() {
		pin();
		when(models.digest(model)).thenReturn("b".repeat(64));
		assertEquals("EMBEDDING_CONFIGURATION_CHANGED",
				assertThrows(ResponseStatusException.class, () -> indexes.verify(row())).getReason());
		when(models.digest(model)).thenReturn("a".repeat(64));
		jdbc.update("UPDATE ai_embed_store SET host='foreign'");
		assertThrows(ResponseStatusException.class, () -> indexes.verify(row()));
		verifyZeroInteractions(embeddings, stores);
	}

	@Test
	void queryKeepsPinnedModelAndFiltersAllVersionBoundaries() {
		pin();
		jdbc.update("UPDATE zf_companion_revision SET state='READY',reference_digest=?", "r".repeat(64));
		when(embeddings.embed("42", "a".repeat(64), List.of("current question")))
			.thenReturn(List.of(new float[] { 1, 0 }));
		when(vectorStore.search(any()))
			.thenReturn(new dev.langchain4j.store.embedding.EmbeddingSearchResult<>(List.of()));
		var beats = new AtomicInteger();
		assertEquals(List.of(), indexes.search(row(), policy, "current question", beats::incrementAndGet));
		assertEquals(3, beats.get());
		var request = org.mockito.ArgumentCaptor.forClass(dev.langchain4j.store.embedding.EmbeddingSearchRequest.class);
		verify(vectorStore).search(request.capture());
		assertEquals(6, request.getValue().maxResults());
		assertEquals(.7, request.getValue().minScore());
		var metadata = new HashMap<String, Object>(
				Map.of("tenantId", "1", "companionRevisionId", "100", "referenceDigest", "r".repeat(64),
						"indexConfigurationDigest", row().get("index_configuration_digest")));
		assertTrue(request.getValue().filter().test(dev.langchain4j.data.document.Metadata.from(metadata)));
		for (String key : List.of("tenantId", "companionRevisionId", "referenceDigest", "indexConfigurationDigest")) {
			var tampered = new HashMap<>(metadata);
			tampered.put(key, "foreign");
			assertFalse(request.getValue().filter().test(dev.langchain4j.data.document.Metadata.from(tampered)));
		}
		assertThrows(IllegalStateException.class,
				() -> tx.execute(ignored -> indexes.search(row(), policy, "current question", () -> {
				})));
		verify(embeddings, times(1)).embed("42", "a".repeat(64), List.of("current question"));
	}

	@Test
	void boundedBatchesUseStableSnapshotIdsAndCorrectRevisionMetadata() {
		pin();
		var frozen = freeze(17);
		var batchSizes = new ArrayList<Integer>();
		var writes = new ArrayList<List<String>>();
		when(embeddings.embed(eq("42"), eq("a".repeat(64)), anyList())).thenAnswer(call -> {
			List<String> texts = call.getArgument(2);
			assertTrue(texts.size() <= 8);
			batchSizes.add(texts.size());
			return texts.stream().map(text -> new float[] { .1f, .2f }).toList();
		});
		doAnswer(call -> {
			List<String> ids = call.getArgument(0);
			List<TextSegment> segments = call.getArgument(2);
			writes.add(List.copyOf(ids));
			for (var segment : segments) {
				assertEquals("1", segment.metadata().getString("tenantId"));
				assertEquals("100", segment.metadata().getString("companionRevisionId"));
				assertEquals(frozen.digest(), segment.metadata().getString("referenceDigest"));
			}
			return null;
		}).when(vectorStore).addAll(anyList(), anyList(), anyList());
		var beats = new AtomicInteger();
		indexes.prepare("100", frozen, beats::incrementAndGet);
		assertEquals(List.of(8, 8, 1), batchSizes);
		assertEquals(10, beats.get());
		var first = List.copyOf(writes);
		indexes.prepare("100", frozen, () -> {
		});
		assertEquals(first, writes.subList(3, 6));
		assertNotEquals(indexes.pointId("100", "1"), indexes.pointId("101", "1"));
	}

	@Test
	void lostLeaseAndExternalTransactionPreventEmbeddingOrUpsert() {
		pin();
		var frozen = freeze(1);
		assertThrows(IllegalStateException.class, () -> indexes.prepare("100", frozen, () -> {
			throw new IllegalStateException("LEASE_LOST");
		}));
		verifyZeroInteractions(embeddings, stores);
		assertThrows(IllegalStateException.class,
				() -> tx.executeWithoutResult(ignored -> indexes.prepare("100", frozen, () -> {
				})));
		verifyZeroInteractions(embeddings, stores);
	}

	@Test
	void managedPackageDigestBindsIndexDescriptorAndRejectsCredentialFields() {
		pin();
		var frozen = freeze(1);
		var plan = indexes.verify(row());
		var compiler = new ZenflowCompanionPackage(new ObjectMapper());
		var chat = Map.<String, Object>of("modelName", "fixture", "provider", "FIXTURE", "baseUrl",
				"https://example.org/v1", "temperature", .6, "maxOutputTokens", 2048, "allowPrivateContext", false,
				"version", 1);
		var first = compiler.compile("100", "9", Map.of("principles", "Listen first."), chat, frozen, plan);
		assertEquals(row().get("index_configuration_digest"), first.indexConfigurationDigest());
		var changed = new HashMap<String, Object>(plan);
		changed.put("embeddingModelId", "43");
		var next = compiler.compile("100", "9", Map.of("principles", "Listen first."), chat, frozen, changed);
		assertNotEquals(first.release().getReleaseDigest(), next.release().getReleaseDigest());
		assertThrows(IllegalArgumentException.class,
				() -> compiler.compile("100", "9", Map.of("principles", "Listen first."), chat, frozen, Map.of()));
		changed.put("apiKey", "credential-sentinel");
		assertThrows(IllegalArgumentException.class,
				() -> compiler.compile("100", "9", Map.of("principles", "Listen first."), chat, frozen, changed));
	}

	private void pin() {
		tx.executeWithoutResult(ignored -> indexes.pin(row(), policy));
	}

	private Map<String, Object> row() {
		return jdbc.queryForMap("SELECT * FROM zf_companion_revision WHERE id=100");
	}

	private ZenflowReferenceSnapshots.Frozen freeze(int count) {
		try {
			jdbc.update("UPDATE zf_companion_revision SET state='PREPARING',retrieval_policy_json=?",
					new ObjectMapper().writeValueAsString(policy.projection()));
		}
		catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
		for (int i = 1; i <= count; i++)
			jdbc.update("INSERT INTO ai_slice VALUES(?,10,?,'0',1)", i, "Public text " + i);
		var product = new ZenflowProperties();
		product.setTenantId(1);
		var snapshots = new ZenflowReferenceSnapshots(jdbc, product, new ZenflowReferenceSources(jdbc, product));
		return tx.execute(ignored -> snapshots.capture("100", policy));
	}

}
