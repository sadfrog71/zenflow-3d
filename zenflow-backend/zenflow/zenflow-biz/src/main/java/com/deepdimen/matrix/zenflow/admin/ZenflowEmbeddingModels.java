package com.deepdimen.matrix.zenflow.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.zenflow.companion.ZenflowRunAuthorization;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Embedding settings reuse ai_model, with immutable published configurations and a
 * separate active modality.
 */
@Service
public class ZenflowEmbeddingModels {

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final TransactionTemplate tx;

	private final long tenant;

	public ZenflowEmbeddingModels(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager,
			ZenflowProperties product) {
		this.jdbc = jdbc;
		this.json = json;
		this.tx = new TransactionTemplate(manager);
		this.tenant = product.getTenantId();
	}

	public List<Map<String, Object>> list() {
		return jdbc.queryForList(
				"SELECT id,name,model_name,provider,base_url,dimensions,ext_data,CASE WHEN api_key IS NULL OR api_key='' THEN 0 ELSE 1 END AS has_key FROM ai_model WHERE tenant_id=? AND model_type='Embedding' AND del_flag='0' ORDER BY update_time DESC,id DESC",
				tenant)
			.stream()
			.map(this::projection)
			.toList();
	}

	public Map<String, Object> save(String id, Map<String, Object> body) {
		if (body == null
				|| !Set.of("name", "modelName", "provider", "baseUrl", "apiKey", "dimensions", "expectedVersion")
					.containsAll(body.keySet()))
			throw error("EMBEDDING_INPUT_INVALID");
		String name = text(body, "name", 64), model = text(body, "modelName", 100),
				provider = text(body, "provider", 100), base = text(body, "baseUrl", 100);
		ZenflowModelService.validateEndpoint(base);
		int dimensions = integer(body, "dimensions", 1, 4096);
		return tx.execute(ignored -> {
			long key = id == null ? IdWorker.getId() : id(id);
			var rows = jdbc.queryForList(
					"SELECT * FROM ai_model WHERE tenant_id=? AND id=? AND model_type='Embedding' AND del_flag='0' FOR UPDATE",
					tenant, key);
			if (id != null && rows.size() != 1)
				throw missing();
			var previous = rows.isEmpty() ? Map.<String, Object>of() : rows.get(0);
			var metadata = metadata(previous);
			int version = rows.isEmpty() ? 0 : metadata.path("zenflowVersion").asInt(1);
			if (id != null && integer(body, "expectedVersion", 1, Integer.MAX_VALUE) != version)
				throw error("VERSION_CONFLICT");
			if (metadata.path("zenflowPublished").asBoolean(false))
				throw error("ACTIVE_MODEL_IMMUTABLE_CREATE_NEW_MODEL");
			Object supplied = body.get("apiKey");
			if (supplied != null && !(supplied instanceof String))
				throw error("MODEL_KEY_REQUIRED");
			String keyValue = supplied instanceof String value && !value.isBlank() ? value.trim()
					: (String) previous.get("api_key");
			if (keyValue == null || !keyValue.matches("[\\x21-\\x7E]{1,255}"))
				throw error("MODEL_KEY_REQUIRED");
			var next = json.createObjectNode();
			next.put("zenflowVersion", version + 1);
			next.put("zenflowTested", false);
			next.put("zenflowPublished", false);
			next.put("invelleEmbeddingActive", false);
			if (rows.isEmpty())
				jdbc.update(
						"INSERT INTO ai_model(id,tenant_id,model_type,name,model_name,provider,base_url,api_key,dimensions,assistant_model,default_model,del_flag,ext_data,create_time,update_time) VALUES(?,?,'Embedding',?,?,?,?,?,?,'0','0','0',?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",
						key, tenant, name, model, provider, base, keyValue, dimensions, next.toString());
			else
				jdbc.update(
						"UPDATE ai_model SET name=?,model_name=?,provider=?,base_url=?,api_key=?,dimensions=?,ext_data=?,update_time=UTC_TIMESTAMP() WHERE tenant_id=? AND id=?",
						name, model, provider, base, keyValue, dimensions, next.toString(), tenant, key);
			return projection(require(Long.toString(key)));
		});
	}

	/**
	 * Internal row includes the credential for the bounded transport; never returned by a
	 * controller.
	 */
	Map<String, Object> require(String id) {
		var rows = jdbc.queryForList(
				"SELECT * FROM ai_model WHERE tenant_id=? AND id=? AND model_type='Embedding' AND del_flag='0'", tenant,
				id(id));
		if (rows.size() != 1)
			throw missing();
		metadata(rows.get(0));
		return rows.get(0);
	}

	public Map<String, Object> publishedSnapshot(String id) {
		var row = require(id);
		var metadata = metadata(row);
		if (!metadata.path("zenflowTested").asBoolean(false) || !metadata.path("zenflowPublished").asBoolean(false))
			throw error("EMBEDDING_PUBLISH_REQUIRED");
		return projection(row);
	}

	public Map<String, Object> active() {
		var rows = jdbc.queryForList(
				"SELECT * FROM ai_model WHERE tenant_id=? AND model_type='Embedding' AND del_flag='0' AND JSON_EXTRACT(ext_data,'$.invelleEmbeddingActive')=true",
				tenant);
		if (rows.size() != 1)
			throw error("EMBEDDING_NOT_CONFIGURED");
		var row = rows.get(0);
		var metadata = metadata(row);
		if (!metadata.path("zenflowTested").asBoolean() || !metadata.path("zenflowPublished").asBoolean())
			throw error("EMBEDDING_PUBLISH_REQUIRED");
		return projection(row);
	}

	public String digest(Map<String, Object> snapshot) {
		var fixed = new TreeMap<String, Object>();
		for (String key : List.of("id", "modelName", "provider", "baseUrl", "dimensions", "version")) {
			if (snapshot.get(key) == null)
				throw error("EMBEDDING_CONFIGURATION_INVALID");
			fixed.put(key, snapshot.get(key));
		}
		try {
			return ZenflowRunAuthorization.hash(json.writeValueAsString(fixed));
		}
		catch (Exception invalid) {
			throw error("EMBEDDING_CONFIGURATION_INVALID");
		}
	}

	void recordTest(String id, int expectedVersion) {
		int changed = jdbc.update(
				"UPDATE ai_model SET ext_data=JSON_SET(ext_data,'$.zenflowTested',true),update_time=UTC_TIMESTAMP() WHERE tenant_id=? AND id=? AND model_type='Embedding' AND del_flag='0' AND JSON_EXTRACT(ext_data,'$.zenflowVersion')=?",
				tenant, id(id), expectedVersion);
		if (changed != 1)
			throw error("MODEL_CHANGED_DURING_TEST");
	}

	public Map<String, Object> activate(String id, int expectedVersion) {
		return tx.execute(ignored -> {
			jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE", tenant);
			var rows = jdbc.queryForList(
					"SELECT * FROM ai_model WHERE tenant_id=? AND id=? AND model_type='Embedding' AND del_flag='0' FOR UPDATE",
					tenant, id(id));
			if (rows.size() != 1)
				throw missing();
			var metadata = metadata(rows.get(0));
			if (metadata.path("zenflowVersion").asInt(1) != expectedVersion)
				throw error("VERSION_CONFLICT");
			if (!metadata.path("zenflowTested").asBoolean(false))
				throw error("MODEL_TEST_REQUIRED");
			jdbc.update(
					"UPDATE ai_model SET ext_data=JSON_SET(ext_data,'$.invelleEmbeddingActive',false) WHERE tenant_id=? AND model_type='Embedding' AND del_flag='0' AND JSON_EXTRACT(ext_data,'$.invelleEmbeddingActive')=true",
					tenant);
			jdbc.update(
					"UPDATE ai_model SET ext_data=JSON_SET(ext_data,'$.invelleEmbeddingActive',true,'$.zenflowPublished',true),update_time=UTC_TIMESTAMP() WHERE tenant_id=? AND id=?",
					tenant, id(id));
			return Map.of("modelId", id, "active", true);
		});
	}

	private Map<String, Object> projection(Map<String, Object> row) {
		var metadata = metadata(row);
		var value = new TreeMap<String, Object>();
		value.put("id", row.get("id").toString());
		value.put("name", row.get("name"));
		value.put("modelName", row.get("model_name"));
		value.put("provider", row.get("provider"));
		value.put("baseUrl", row.get("base_url"));
		value.put("dimensions", row.get("dimensions"));
		value.put("version", metadata.path("zenflowVersion").asInt(1));
		value.put("tested", metadata.path("zenflowTested").asBoolean(false));
		value.put("published", metadata.path("zenflowPublished").asBoolean(false));
		value.put("active", metadata.path("invelleEmbeddingActive").asBoolean(false));
		value.put("hasApiKey", row.containsKey("has_key") ? ((Number) row.get("has_key")).intValue() != 0
				: row.get("api_key") != null && !row.get("api_key").toString().isBlank());
		return value;
	}

	private JsonNode metadata(Map<String, Object> row) {
		if (row.isEmpty())
			return json.createObjectNode();
		try {
			var value = json.readTree(row.get("ext_data").toString());
			if (!value.isObject() || !value.path("zenflowVersion").isIntegralNumber()
					|| !value.path("zenflowVersion").canConvertToInt() || value.path("zenflowVersion").intValue() < 1
					|| !value.path("zenflowTested").isBoolean() || !value.path("zenflowPublished").isBoolean()
					|| !value.path("invelleEmbeddingActive").isBoolean())
				throw new IllegalArgumentException();
			return value;
		}
		catch (Exception invalid) {
			throw error("EMBEDDING_CONFIGURATION_INVALID");
		}
	}

	private static String text(Map<String, Object> body, String key, int max) {
		if (!(body.get(key) instanceof String value) || value.isBlank() || value.length() > max)
			throw error("EMBEDDING_INPUT_INVALID");
		return value.trim();
	}

	private static int integer(Map<String, Object> body, String key, int min, int max) {
		if (!(body.get(key) instanceof Number value) || !Double.isFinite(value.doubleValue())
				|| value.doubleValue() != value.intValue() || value.intValue() < min || value.intValue() > max)
			throw error("EMBEDDING_INPUT_INVALID");
		return ((Number) body.get(key)).intValue();
	}

	private static long id(String value) {
		try {
			if (value == null || !value.matches("[1-9][0-9]{0,18}"))
				throw new IllegalArgumentException();
			return Long.parseLong(value);
		}
		catch (Exception invalid) {
			throw error("INVALID_ID");
		}
	}

	private static ResponseStatusException error(String code) {
		return new ResponseStatusException(
				Set.of("EMBEDDING_INPUT_INVALID", "MODEL_KEY_REQUIRED", "INVALID_ID").contains(code)
						? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT,
				code);
	}

	private static ResponseStatusException missing() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "EMBEDDING_NOT_FOUND");
	}

}
