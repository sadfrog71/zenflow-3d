package com.deepdimen.matrix.zenflow.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * OpenAI-compatible embeddings with checked dimensions and no implicit default model or
 * local fallback.
 */
@Component
public class ZenflowEmbeddingGateway {

	@FunctionalInterface
	interface Transport {

		String request(URI endpoint, String key, String body) throws Exception;

	}

	private final ZenflowEmbeddingModels models;

	private final ObjectMapper json;

	private final Transport transport;

	@Autowired
	public ZenflowEmbeddingGateway(ZenflowEmbeddingModels models, ObjectMapper json) {
		this(models, json, ZenflowEmbeddingGateway::request);
	}

	ZenflowEmbeddingGateway(ZenflowEmbeddingModels models, ObjectMapper json, Transport transport) {
		this.models = models;
		this.json = json;
		this.transport = transport;
	}

	public void probe(String id, int expectedVersion) {
		var row = models.require(id);
		var metadata = metadata(row);
		if (metadata.path("zenflowVersion").asInt(1) != expectedVersion)
			throw error("VERSION_CONFLICT");
		invoke(row, List.of("Invelle public reference connection test."));
		models.recordTest(id, expectedVersion);
	}

	public List<float[]> embed(String id, String expectedDigest, List<String> publicTexts) {
		var snapshot = models.publishedSnapshot(id);
		if (expectedDigest == null || !models.digest(snapshot).equals(expectedDigest))
			throw error("EMBEDDING_CONFIGURATION_CHANGED");
		var vectors = invoke(models.require(id), publicTexts);
		if (!models.digest(models.publishedSnapshot(id)).equals(expectedDigest))
			throw error("EMBEDDING_CONFIGURATION_CHANGED");
		return vectors;
	}

	private List<float[]> invoke(Map<String, Object> row, List<String> texts) {
		if (TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("EMBEDDING_EXTERNAL_TRANSACTION_DENIED");
		if (texts == null || texts.isEmpty() || texts.size() > 16)
			throw error("EMBEDDING_BATCH_INVALID");
		int total = 0;
		for (String text : texts) {
			if (text == null || text.isBlank() || text.length() > 16000)
				throw error("EMBEDDING_BATCH_INVALID");
			total += text.length();
		}
		if (total > 128000)
			throw error("EMBEDDING_BATCH_INVALID");
		if (!(row.get("dimensions") instanceof Number size) || size.intValue() < 1 || size.intValue() > 4096
				|| size.doubleValue() != size.intValue())
			throw error("EMBEDDING_CONFIGURATION_INVALID");
		int dimensions = size.intValue();
		String key = (String) row.get("api_key");
		if (key == null || !key.matches("[\\x21-\\x7E]{1,255}"))
			throw error("MODEL_KEY_REQUIRED");
		URI base = ZenflowModelService.validateEndpoint(row.get("base_url").toString());
		URI endpoint = URI.create(base.toString().replaceAll("/+$", "") + "/embeddings");
		try {
			var payload = new java.util.LinkedHashMap<String, Object>(
					Map.of("model", row.get("model_name"), "input", texts, "encoding_format", "float"));
			// BGE and BCE return fixed dimensions; custom dimensions are rejected by
			// their compatible API. Still verify the actual output below.
			if (!java.util.Set.of("BAAI/bge-m3", "Pro/BAAI/bge-m3", "BAAI/bge-large-zh-v1.5",
					"BAAI/bge-large-en-v1.5", "netease-youdao/bce-embedding-base_v1")
					.contains(row.get("model_name").toString()))
				payload.put("dimensions", dimensions);
			String body = json.writeValueAsString(payload);
			String response = transport.request(endpoint, key, body);
			if (response == null || response.length() > 2097152)
				throw error("EMBEDDING_INVALID_RESPONSE");
			var root = json.readTree(response);
			var data = root.path("data");
			if (root.has("error") || !data.isArray() || data.size() != texts.size())
				throw error("EMBEDDING_INVALID_RESPONSE");
			var sorted = new float[texts.size()][];
			for (JsonNode item : data) {
				var index = item.path("index");
				var values = item.path("embedding");
				if (!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue() < 0
						|| index.intValue() >= sorted.length || sorted[index.intValue()] != null || !values.isArray()
						|| values.size() != dimensions)
					throw error("EMBEDDING_INVALID_RESPONSE");
				var vector = new float[dimensions];
				double norm = 0;
				for (int i = 0; i < dimensions; i++) {
					var number = values.get(i);
					if (!number.isNumber() || !Double.isFinite(number.doubleValue())
							|| !Float.isFinite(number.floatValue()))
						throw error("EMBEDDING_INVALID_RESPONSE");
					vector[i] = number.floatValue();
					norm += (double) vector[i] * vector[i];
				}
				if (!Double.isFinite(norm) || norm == 0)
					throw error("EMBEDDING_INVALID_RESPONSE");
				sorted[index.intValue()] = vector;
			}
			return java.util.Arrays.asList(sorted);
		}
		catch (ResponseStatusException failure) {
			throw failure;
		}
		catch (Exception invalid) {
			throw error("EMBEDDING_UPSTREAM_UNAVAILABLE");
		}
	}

	private JsonNode metadata(Map<String, Object> row) {
		try {
			return json.readTree(row.get("ext_data").toString());
		}
		catch (Exception invalid) {
			throw error("EMBEDDING_CONFIGURATION_INVALID");
		}
	}

	private static String request(URI endpoint, String key, String body) throws Exception {
		try (var client = ZenflowModelHttp.client(endpoint)) {
			var post = new HttpPost(endpoint);
			post.setHeader("Authorization", "Bearer " + key);
			post.setHeader("Accept", "application/json");
			post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
			var cancellation = reactor.core.scheduler.Schedulers.parallel()
				.schedule(post::cancel, 30, java.util.concurrent.TimeUnit.SECONDS);
			try {
				return client.execute(post, response -> {
					if (response.getCode() != 200 || response.getEntity() == null
							|| response.getEntity().getContentType() == null
							|| !response.getEntity()
								.getContentType()
								.toLowerCase(java.util.Locale.ROOT)
								.startsWith("application/json"))
						throw error("EMBEDDING_UPSTREAM_UNAVAILABLE");
					try (var stream = response.getEntity().getContent()) {
						byte[] bytes = stream.readNBytes(2097153);
						if (bytes.length > 2097152)
							throw error("EMBEDDING_INVALID_RESPONSE");
						return new String(bytes, StandardCharsets.UTF_8);
					}
				});
			}
			finally {
				cancellation.dispose();
			}
		}
	}

	private static ResponseStatusException error(String code) {
		return new ResponseStatusException(code
			.equals("EMBEDDING_BATCH_INVALID")
					? HttpStatus.BAD_REQUEST
					: java.util.Set.of("VERSION_CONFLICT", "EMBEDDING_CONFIGURATION_CHANGED",
							"EMBEDDING_CONFIGURATION_INVALID", "MODEL_KEY_REQUIRED")
						.contains(code) ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY,
				code);
	}

}
