package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class ZenflowEmbeddingGatewayTest {

	private static Map<String, Object> row() {
		return Map.of("id", 42L, "model_name", "embedding-fixture", "base_url", "https://8.8.8.8/v1", "dimensions", 2,
				"api_key", "credential-sentinel", "ext_data", "{\"zenflowVersion\":1}");
	}

	private static String response(String data) {
		return "{\"data\":" + data + "}";
	}

	@Test
	void probeValidatesDimensionsOrderingAndNeverReturnsCredentialInRequestBody() {
		var models = mock(ZenflowEmbeddingModels.class);
		when(models.require("42")).thenReturn(row());
		var gateway = new ZenflowEmbeddingGateway(models, new ObjectMapper(), (endpoint, key, body) -> {
			assertEquals("https://8.8.8.8/v1/embeddings", endpoint.toString());
			assertEquals("credential-sentinel", key);
			assertFalse(body.contains(key));
			assertTrue(body.contains("Invelle public reference connection test."));
			return response("[{\"index\":0,\"embedding\":[0.5,1]}]");
		});
		assertThrows(ResponseStatusException.class, () -> gateway.probe("42", 2));
		verify(models, never()).recordTest(anyString(), anyInt());
		gateway.probe("42", 1);
		verify(models).recordTest("42", 1);
	}


	@Test
	void fixedBgeModelOmitsUnsupportedDimensionsButStillChecksReturnedVectorSize() {
		var models = mock(ZenflowEmbeddingModels.class);
		var fixed = new java.util.HashMap<String, Object>(row());
		fixed.put("model_name", "BAAI/bge-m3");
		fixed.put("dimensions", 1024);
		when(models.require("42")).thenReturn(fixed);
		var vector = java.util.Collections.nCopies(1024, 0.5);
		var gateway = new ZenflowEmbeddingGateway(models, new ObjectMapper(), (endpoint, key, body) -> {
			var request = new ObjectMapper().readTree(body);
			assertFalse(request.has("dimensions"), "BGE-M3 rejects a custom dimensions parameter");
			assertEquals("BAAI/bge-m3", request.path("model").asText());
			return new ObjectMapper().writeValueAsString(Map.of("data", List.of(Map.of("index", 0, "embedding", vector))));
		});
		gateway.probe("42", 1);
		verify(models).recordTest("42", 1);
		fixed.put("dimensions", 1536);
		assertEquals("EMBEDDING_INVALID_RESPONSE", assertThrows(ResponseStatusException.class,
				() -> gateway.probe("42", 1)).getReason());
		verify(models, times(1)).recordTest("42", 1);
	}

	@Test
	void invalidVectorsAndProviderExceptionsNeverMarkTestedOrLeakDetails() {
		for (String data : List.of("[]", "[{\"index\":0,\"embedding\":[1]}]", "[{\"index\":0,\"embedding\":[0,0]}]",
				"[{\"index\":0,\"embedding\":[\"secret\",1]}]", "[{\"index\":9,\"embedding\":[1,2]}]")) {
			var models = mock(ZenflowEmbeddingModels.class);
			when(models.require("42")).thenReturn(row());
			var gateway = new ZenflowEmbeddingGateway(models, new ObjectMapper(),
					(endpoint, key, body) -> response(data));
			var failure = assertThrows(ResponseStatusException.class, () -> gateway.probe("42", 1));
			assertEquals("EMBEDDING_INVALID_RESPONSE", failure.getReason());
			verify(models, never()).recordTest(anyString(), anyInt());
		}
		var models = mock(ZenflowEmbeddingModels.class);
		when(models.require("42")).thenReturn(row());
		var gateway = new ZenflowEmbeddingGateway(models, new ObjectMapper(), (endpoint, key, body) -> {
			throw new IllegalStateException("credential-sentinel upstream-secret");
		});
		var failure = assertThrows(ResponseStatusException.class, () -> gateway.probe("42", 1));
		assertEquals("EMBEDDING_UPSTREAM_UNAVAILABLE", failure.getReason());
		assertFalse(failure.getMessage().contains("credential-sentinel"));
	}

	@Test
	void fixedConfigurationAndTransactionBoundaryAreCheckedBeforeAnyEgress() {
		var models = mock(ZenflowEmbeddingModels.class);
		when(models.require("42")).thenReturn(row());
		when(models.publishedSnapshot("42")).thenReturn(Map.of("id", "42"));
		when(models.digest(anyMap())).thenReturn("fixed");
		var gateway = new ZenflowEmbeddingGateway(models, new ObjectMapper(),
				(endpoint, key, body) -> fail("must not call"));
		assertThrows(ResponseStatusException.class, () -> gateway.embed("42", "changed", List.of("public")));
		try {
			TransactionSynchronizationManager.setActualTransactionActive(true);
			assertThrows(IllegalStateException.class, () -> gateway.probe("42", 1));
		}
		finally {
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
		assertThrows(ResponseStatusException.class, () -> gateway.embed("42", "fixed", List.of("x".repeat(16001))));
	}

	@Test
	void batchIndicesAreUniqueCompleteAndSortedAndConfigurationIsRecheckedAfterCall() {
		var models = mock(ZenflowEmbeddingModels.class);
		when(models.require("42")).thenReturn(row());
		when(models.publishedSnapshot("42")).thenReturn(Map.of("id", "42"));
		when(models.digest(anyMap())).thenReturn("fixed");
		var gateway = new ZenflowEmbeddingGateway(models, new ObjectMapper(), (endpoint, key,
				body) -> response("[{\"index\":1,\"embedding\":[3,4]},{\"index\":0,\"embedding\":[1,2]}]"));
		var result = gateway.embed("42", "fixed", List.of("one", "two"));
		assertArrayEquals(new float[] { 1, 2 }, result.get(0));
		assertArrayEquals(new float[] { 3, 4 }, result.get(1));
		when(models.digest(anyMap())).thenReturn("fixed", "changed");
		assertThrows(ResponseStatusException.class, () -> gateway.embed("42", "fixed", List.of("one", "two")));
		when(models.digest(anyMap())).thenReturn("fixed");
		var duplicate = new ZenflowEmbeddingGateway(models, new ObjectMapper(), (endpoint, key,
				body) -> response("[{\"index\":0,\"embedding\":[1,2]},{\"index\":0,\"embedding\":[3,4]}]"));
		assertThrows(ResponseStatusException.class, () -> duplicate.embed("42", "fixed", List.of("one", "two")));
	}

}
