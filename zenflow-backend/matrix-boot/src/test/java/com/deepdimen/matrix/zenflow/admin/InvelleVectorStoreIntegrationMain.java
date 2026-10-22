package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.MatrixBootApplication;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original fixed-store adapter against product-only Qdrant; no embedding vendor or
 * private content.
 */
public class InvelleVectorStoreIntegrationMain {

	private static final String FIRST = "invelle_qa_fixed_vector_a_20261007",
			SECOND = "invelle_qa_fixed_vector_b_20261007";

	private static final Embedding VECTOR = Embedding.from(new float[] { 0.1f, 0.2f, 0.3f });

	public static void main(String[] args) {
		if (!"1".equals(System.getenv("INVELLE_CORE_INTEGRATION")))
			throw new IllegalStateException("INTEGRATION_ENV_REQUIRED");
		assertEquals("invelle-vector", System.getenv("ZENFLOW_VECTOR_HOST"));
		assertEquals("6334", System.getenv("ZENFLOW_VECTOR_PORT"));
		String phase = System.getenv("INVELLE_VECTOR_QA_PHASE");
		assertTrue(List.of("WRITE", "VERIFY").contains(phase));
		int exit = 1;
		try (var context = new SpringApplication(MatrixBootApplication.class).run(args)) {
			String schema = context.getBean(JdbcTemplate.class).queryForObject("SELECT DATABASE()", String.class);
			assertNotNull(schema);
			assertTrue(schema.startsWith("zenflow_core_rehearsal_"));
			TenantContextHolder.setTenantId(1L);
			var services = context.getBean(EmbeddingStoreService.class);
			var configuration = new AiEmbedStoreEntity();
			configuration.setStoreId(710071L);
			configuration.setStoreType("qdrant");
			configuration.setHost("invelle-vector");
			configuration.setPort(6334);
			configuration.setUseTls("false");
			configuration.setApiKey(System.getenv("ZENFLOW_VECTOR_API_KEY"));
			assertNotNull(configuration.getApiKey());
			assertFalse(configuration.getApiKey().isBlank());
			var first = services.embeddingStoreFixed(FIRST, configuration, 3, "a".repeat(64));
			var second = services.embeddingStoreFixed(SECOND, configuration, 3, "b".repeat(64));
			assertTrue(first.getClass().getName().contains("Qdrant"));
			assertTrue(second.getClass().getName().contains("Qdrant"));
			if ("WRITE".equals(phase)) {
				first.addAll(List.of(point("first-r1"), point("first-r2")), List.of(VECTOR, VECTOR), List
					.of(segment("PUBLIC_FIXED_ONE", "revision-one"), segment("PUBLIC_FIXED_TWO", "revision-two")));
				second.addAll(List.of(point("second-r1")), List.of(VECTOR),
						List.of(segment("PUBLIC_SECOND_NAMESPACE", "revision-one")));
				// Identical retry overwrites the stable point, not a second copy.
				first.addAll(List.of(point("first-r1")), List.of(VECTOR),
						List.of(segment("PUBLIC_FIXED_ONE", "revision-one")));
			}
			assertRecall(first, "revision-one", "PUBLIC_FIXED_ONE");
			assertRecall(first, "revision-two", "PUBLIC_FIXED_TWO");
			assertRecall(second, "revision-one", "PUBLIC_SECOND_NAMESPACE");
			assertTrue(first.search(query("unselected-revision")).matches().isEmpty());
			if ("VERIFY".equals(phase)) {
				first.removeAll(List.of(point("first-r2")));
				assertTrue(first.search(query("revision-two")).matches().isEmpty());
				assertRecall(first, "revision-one", "PUBLIC_FIXED_ONE");
			}
			System.out.println("INVELLE_VECTOR_STORE_PASS: " + phase
					+ ", original fixed Qdrant adapter, actual upsert/search, stable retry, revision filter, namespace isolation"
					+ ("VERIFY".equals(phase) ? ", restart persistence and targeted removal" : ""));
			exit = 0;
		}
		catch (Throwable failure) {
			System.out.println("INVELLE_VECTOR_STORE_FAIL: " + failure.getClass().getSimpleName());
			for (var frame : failure.getStackTrace())
				System.out.println("QA_FAILURE_FRAME " + frame.getClassName() + "." + frame.getMethodName() + ":"
						+ frame.getLineNumber());
		}
		finally {
			TenantContextHolder.clear();
		}
		System.exit(exit);
	}

	private static String point(String value) {
		return UUID.nameUUIDFromBytes(("invelle-vector-qa:" + value).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private static TextSegment segment(String text, String revision) {
		return TextSegment.from(text, Metadata
			.from(Map.of("tenantId", "1", "companionRevisionId", revision, "sourceHash", "controlled-public-hash")));
	}

	private static EmbeddingSearchRequest query(String revision) {
		return EmbeddingSearchRequest.builder()
			.queryEmbedding(VECTOR)
			.minScore(0.9)
			.maxResults(6)
			.filter(new IsEqualTo("companionRevisionId", revision))
			.build();
	}

	private static void assertRecall(EmbeddingStore<TextSegment> store, String revision, String text) {
		var matches = store.search(query(revision)).matches();
		assertEquals(1, matches.size());
		assertEquals(text, matches.get(0).embedded().text());
		assertEquals(revision, matches.get(0).embedded().metadata().getString("companionRevisionId"));
	}

}
