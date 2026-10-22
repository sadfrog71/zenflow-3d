package com.deepdimen.matrix.zenflow.companion;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.discover.service.impl.*;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.zenflow.admin.ZenflowModelService;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/**
 * Authoring is mutable only in DRAFT; the managed binding remains the sole online
 * pointer.
 */
@Service
public class ZenflowCompanionService {

	private final JdbcTemplate jdbc;

	private final ZenflowProperties product;

	private final ZenflowAdminAccess access;

	private final ZenflowModelService models;

	private final ManagedSkillPackageService packages;

	private final ManagedSkillReleaseService releases;

	private final ManagedSkillBindingService bindings;

	private final ObjectMapper json;

	private final TransactionTemplate tx;

	private final ZenflowCompanionPackage compiler;

	private final ZenflowReferenceSources references;

	private final ZenflowReferenceIndexes indexes;

	public ZenflowCompanionService(JdbcTemplate jdbc, ZenflowProperties product, ZenflowAdminAccess access,
			ZenflowModelService models, ManagedSkillPackageService packages, ManagedSkillReleaseService releases,
			ManagedSkillBindingService bindings, ObjectMapper json, PlatformTransactionManager manager,
			ZenflowReferenceSources references, ZenflowReferenceIndexes indexes) {
		this.jdbc = jdbc;
		this.product = product;
		this.access = access;
		this.models = models;
		this.packages = packages;
		this.releases = releases;
		this.bindings = bindings;
		this.references = references;
		this.indexes = indexes;
		this.json = json;
		this.tx = new TransactionTemplate(manager);
		this.compiler = new ZenflowCompanionPackage(json);
	}

	public Map<String, Object> detail() {
		access.require();
		var configs = jdbc.queryForList("SELECT * FROM zf_companion_config WHERE tenant_id=? AND config_key='default'",
				tenant());
		if (configs.isEmpty())
			return Map.of("configured", false, "status", "DISABLED", "revisions", List.of());
		var config = configs.get(0);
		var binding = jdbc.queryForList(
				"SELECT active_revision_id,revision FROM ai_skill_binding WHERE tenant_id=? AND id=?", tenant(),
				config.get("skill_binding_id"));
		if (binding.size() != 1)
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "COMPANION_BINDING_MISSING");
		var result = new LinkedHashMap<String, Object>();
		result.put("configured", true);
		result.put("id", config.get("id").toString());
		result.put("displayName", config.get("display_name"));
		result.put("status", config.get("status"));
		result.put("version", config.get("version"));
		result.put("bindingVersion", binding.get(0).get("revision"));
		result.put("activeBindingRevisionId", string(binding.get(0).get("active_revision_id")));
		var revisions = jdbc.queryForList(
				"SELECT * FROM zf_companion_revision WHERE tenant_id=? AND config_id=? ORDER BY revision_no DESC LIMIT 50",
				tenant(), config.get("id"));
		var active = jdbc.queryForList(
				"SELECT r.parameter_json,r.enabled FROM ai_skill_binding b JOIN ai_skill_binding_revision r ON r.id=b.active_revision_id AND r.binding_id=b.id AND r.tenant_id=b.tenant_id WHERE b.tenant_id=? AND b.id=?",
				tenant(), config.get("skill_binding_id"));
		result.put("revisions", revisions.stream().map(row -> projection(row, active)).toList());
		result.put("operations", publicationHistory(config));
		return result;
	}

	public Map<String, Object> createDraft(Map<String, Object> body) {
		access.require();
		fields(body, Set.of("authoring", "fromRevisionId"));
		return tx.execute(ignored -> {
			var config = initialize();
			Map<String, Object> authoring;
			var policy = ZenflowReferencePolicy.empty();
			var source = body.containsKey("fromRevisionId") ? revision(config, id(body.get("fromRevisionId")), false)
					: null;
			if (source != null)
				policy = readPolicy(source.get("retrieval_policy_json"));
			if (body.containsKey("authoring"))
				authoring = authoring(body.get("authoring"));
			else if (source != null)
				authoring = readAuthoring(source.get("authoring_json"));
			else
				authoring = Map.of("principles", "先倾听与理解。帮助用户觉察当下的身体感受与真实需要，保留选择的空间。", "coreSummary", "此间，给内在留一点空间。",
						"examples", List.of(), "citationMode", "ON_DEMAND");
			long key = IdWorker.getId();
			Long number = jdbc.queryForObject(
					"SELECT COALESCE(MAX(revision_no),0)+1 FROM zf_companion_revision WHERE tenant_id=? AND config_id=?",
					Long.class, tenant(), config.get("id"));
			jdbc.update(
					"INSERT INTO zf_companion_revision(id,tenant_id,config_id,revision_no,authoring_json,retrieval_policy_json,state,created_by) VALUES(?,?,?,?,?,?,'DRAFT',?)",
					key, tenant(), config.get("id"), number, serialize(compiler.normalize(authoring)),
					serialize(policy.projection()), access.require().getId().toString());
			return detail();
		});
	}

	public Map<String, Object> save(String revisionId, Map<String, Object> body) {
		access.require();
		fields(body, Set.of("expectedVersion", "authoring", "retrievalPolicy"));
		if (!body.containsKey("authoring") && !body.containsKey("retrievalPolicy"))
			throw error(HttpStatus.BAD_REQUEST, "COMPANION_UPDATE_REQUIRED");
		return tx.execute(ignored -> {
			var config = lockConfig();
			var row = revision(config, id(revisionId), true);
			expected(row, body);
			if (!"DRAFT".equals(row.get("state")))
				throw error(HttpStatus.CONFLICT, "COMPANION_REVISION_IMMUTABLE");
			var normalized = body.containsKey("authoring") ? compiler.normalize(authoring(body.get("authoring")))
					: readAuthoring(row.get("authoring_json"));
			var policy = body.containsKey("retrievalPolicy") ? readPolicy(serialize(body.get("retrievalPolicy")))
					: readPolicy(row.get("retrieval_policy_json"));
			for (var selection : policy.references())
				references.load(selection);
			jdbc.update(
					"UPDATE zf_companion_revision SET authoring_json=?,retrieval_policy_json=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
					serialize(normalized), serialize(policy.projection()), tenant(), revisionId);
			return detail();
		});
	}

	public Map<String, Object> prepare(String revisionId, Map<String, Object> body) {
		var actor = access.require();
		fields(body, Set.of("expectedVersion", "modelId"));
		return tx.execute(ignored -> {
			var config = lockConfig();
			var row = revision(config, id(revisionId), true);
			expected(row, body);
			if (!Set.of("DRAFT", "FAILED").contains(row.get("state")))
				throw error(HttpStatus.CONFLICT, "COMPANION_REVISION_IMMUTABLE");
			var policy = readPolicy(row.get("retrieval_policy_json"));
			// New preparations validate the editing sources; retries preserve their
			// frozen source versions.
			if (row.get("reference_digest") == null)
				for (var selection : policy.references())
					references.load(selection);
			indexes.pin(row, policy);
			String modelId = body.get("modelId") == null ? models.active().get("id").toString()
					: Long.toString(id(body.get("modelId")));
			var model = models.publishedSnapshot(modelId);
			String digest = compiler.modelDigest(modelId, model);
			if (row.get("model_id") != null
					&& (!modelId.equals(row.get("model_id")) || !digest.equals(row.get("model_config_digest"))))
				throw error(HttpStatus.CONFLICT, "COMPANION_RETRY_MODEL_CHANGED_COPY_DRAFT");
			jdbc.update(
					"UPDATE zf_companion_revision SET state='PREPARING',model_id=?,model_config_digest=?,error_code=NULL,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
					modelId, digest, tenant(), revisionId);
			long event = IdWorker.getId();
			jdbc.update(
					"INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,state,next_attempt_at) VALUES(?,?,'COMPANION_PREPARE','COMPANION_REVISION',?,?,?,'PENDING',UTC_TIMESTAMP(3))",
					event, tenant(), revisionId, "companion-prepare:" + event,
					serialize(Map.of("administratorId", actor.getId().toString())));
			return detail();
		});
	}

	/**
	 * Called only while the preparation worker holds and checks both task and revision
	 * leases. Does not activate online binding.
	 */
	void sealPreparation(Map<String, Object> row, ZenflowCompanionPackage.Prepared prepared) {
		access.require();
		String revisionId = row.get("id").toString();
		if (!prepared.modelConfigDigest().equals(row.get("model_config_digest"))
				|| !prepared.referenceDigest().equals(row.get("reference_digest"))
				|| !Objects.equals(prepared.indexConfigurationDigest(), row.get("index_configuration_digest")))
			throw error(HttpStatus.CONFLICT, "COMPANION_PREPARE_DIGEST_CHANGED");
		Long packageId = packages.importPackage(prepared.archive(), "invelle-companion.zip");
		packages.approve(packageId);
		var release = releases.publish(packageId, "invelle-" + revisionId);
		if (!release.getReleaseDigest().equals(prepared.release().getReleaseDigest()))
			throw error(HttpStatus.CONFLICT, "COMPANION_RELEASE_DIGEST_MISMATCH");
		jdbc.update(
				"UPDATE zf_companion_revision SET state='READY',skill_release_id=?,release_digest=?,index_namespace=COALESCE(index_namespace,?),sealed_at=UTC_TIMESTAMP(3),lease_owner=NULL,lease_until=NULL,error_code=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
				release.getId(), release.getReleaseDigest(), "invelle:" + tenant() + ":" + revisionId, tenant(),
				revisionId);
		var config = jdbc.queryForMap("SELECT skill_binding_id FROM zf_companion_config WHERE tenant_id=? AND id=?",
				tenant(), row.get("config_id"));
		long bindingId = ((Number) config.get("skill_binding_id")).longValue();
		bindings.revise(bindingId, bindingVersion(bindingId), new ManagedSkillBindingService.RevisionInput(
				release.getId(), true, "{}", "ON_DEMAND", serialize(Map.of("companionRevisionId", revisionId)), "{}"));
	}

	/**
	 * Publish and rollback both activate an already prepared immutable binding revision.
	 */
	public Map<String, Object> publish(String revisionId, Map<String, Object> body) {
		var administrator = access.require();
		fields(body, Set.of("expectedBindingVersion"));
		return tx.execute(ignored -> {
			var config = lockConfig();
			var row = revision(config, id(revisionId), true);
			if (!"READY".equals(row.get("state")))
				throw error(HttpStatus.CONFLICT, "COMPANION_PREPARE_REQUIRED");
			long bindingId = ((Number) config.get("skill_binding_id")).longValue();
			long version = number(body, "expectedBindingVersion");
			var matches = jdbc.queryForList(
					"SELECT id,parameter_json FROM ai_skill_binding_revision WHERE tenant_id=? AND binding_id=? AND release_id=? AND enabled=1 ORDER BY id DESC",
					tenant(), bindingId, row.get("skill_release_id"));
			var target = matches.stream()
				.filter(r -> revisionId.equals(parameterRevision(r.get("parameter_json"))))
				.findFirst()
				.orElseThrow(() -> error(HttpStatus.CONFLICT, "COMPANION_BINDING_MISSING"));
			var previous = activePublication(config);
			bindings.activate(bindingId, ((Number) target.get("id")).longValue(), version);
			String action = previous != null && ((Number) previous.get("revision_no"))
				.longValue() > ((Number) row.get("revision_no")).longValue() ? "ROLLBACK" : "PUBLISH";
			recordPublication(config, previous, row, ((Number) target.get("id")).longValue(), administrator.getId(),
					administrator.getUsername(), action);
			return detail();
		});
	}

	public Map<String, Object> disable(Map<String, Object> body) {
		var administrator = access.require();
		fields(body, Set.of("expectedBindingVersion"));
		return tx.execute(ignored -> {
			var config = lockConfig();
			long bindingId = ((Number) config.get("skill_binding_id")).longValue();
			long version = number(body, "expectedBindingVersion");
			if (version != bindingVersion(bindingId))
				throw error(HttpStatus.CONFLICT, "BINDING_REVISION_CONFLICT");
			var active = jdbc.queryForList(
					"SELECT r.* FROM ai_skill_binding b JOIN ai_skill_binding_revision r ON r.id=b.active_revision_id AND r.binding_id=b.id AND r.tenant_id=b.tenant_id WHERE b.tenant_id=? AND b.id=?",
					tenant(), bindingId);
			if (active.isEmpty() || ((Number) active.get(0).get("enabled")).intValue() == 0)
				return detail();
			var row = active.get(0);
			var previous = activePublication(config);
			Long revised = bindings.revise(bindingId, version,
					new ManagedSkillBindingService.RevisionInput(((Number) row.get("release_id")).longValue(), false,
							"{}", "ON_DEMAND", row.get("parameter_json").toString(), "{}"));
			bindings.activate(bindingId, revised, version);
			recordPublication(config, previous, null, revised, administrator.getId(), administrator.getUsername(),
					"DISABLE");
			return detail();
		});
	}

	public Map<String, Object> preview(String revisionId) {
		access.require();
		var configs = jdbc.queryForList("SELECT * FROM zf_companion_config WHERE tenant_id=? AND config_key='default'",
				tenant());
		if (configs.size() != 1)
			throw error(HttpStatus.NOT_FOUND, "COMPANION_NOT_FOUND");
		var row = revision(configs.get(0), id(revisionId), false);
		var value = new LinkedHashMap<String, Object>();
		value.put("revisionId", revisionId);
		value.put("authoring", readAuthoring(row.get("authoring_json")));
		value.put("state", row.get("state"));
		if (row.get("skill_release_id") != null) {
			var release = jdbc.queryForList("SELECT content_snapshot FROM ai_skill_release WHERE tenant_id=? AND id=?",
					tenant(), row.get("skill_release_id"));
			if (release.size() == 1)
				value.put("instructions", release.get(0).get("content_snapshot"));
		}
		var policy = readPolicy(row.get("retrieval_policy_json"));
		value.put("referenceCount", policy.references().size());
		value.put("retrievalPolicy", policy.projection());
		return value;
	}

	private Map<String, Object> activePublication(Map<String, Object> config) {
		var rows = jdbc.queryForList(
				"SELECT r.parameter_json FROM ai_skill_binding b JOIN ai_skill_binding_revision r ON r.id=b.active_revision_id AND r.binding_id=b.id AND r.tenant_id=b.tenant_id WHERE b.tenant_id=? AND b.id=? AND r.enabled=1",
				tenant(), config.get("skill_binding_id"));
		if (rows.isEmpty())
			return null;
		String revisionId = parameterRevision(rows.get(0).get("parameter_json"));
		if (revisionId.isBlank())
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "COMPANION_BINDING_MISSING");
		return revision(config, id(revisionId), false);
	}

	private void recordPublication(Map<String, Object> config, Map<String, Object> previous, Map<String, Object> target,
			long bindingRevisionId, long administratorId, String administratorName, String action) {
		List<String> changes;
		if (target == null)
			changes = List.of("停用陪伴");
		else if (previous == null)
			changes = List.of("启用陪伴版本");
		else {
			var before = new LinkedHashMap<String, Object>(readAuthoring(previous.get("authoring_json")));
			var after = new LinkedHashMap<String, Object>(readAuthoring(target.get("authoring_json")));
			before.put("retrievalPolicy", readPolicy(previous.get("retrieval_policy_json")).projection());
			after.put("retrievalPolicy", readPolicy(target.get("retrieval_policy_json")).projection());
			before.put("modelId", previous.get("model_id"));
			after.put("modelId", target.get("model_id"));
			changes = publicationChanges(before, after);
		}
		jdbc.update(
				"INSERT INTO zf_companion_operation(id,tenant_id,config_id,administrator_id,administrator_name,action,previous_revision_id,target_revision_id,binding_revision_id,changes_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",
				IdWorker.getId(), tenant(), config.get("id"), administratorId, administratorName, action,
				previous == null ? null : previous.get("id"), target == null ? null : target.get("id"),
				bindingRevisionId, serialize(changes));
	}

	static List<String> publicationChanges(Map<String, Object> before, Map<String, Object> after) {
		var labels = new LinkedHashMap<String, String>();
		labels.put("principles", "陪伴原则");
		labels.put("coreSummary", "核心提要");
		labels.put("examples", "回答示例");
		labels.put("citationMode", "引用方式");
		labels.put("retrievalPolicy", "参考内容与检索范围");
		labels.put("modelId", "固定模型");
		var changes = labels.entrySet()
			.stream()
			.filter(entry -> !Objects.equals(before.get(entry.getKey()), after.get(entry.getKey())))
			.map(Map.Entry::getValue)
			.toList();
		return changes.isEmpty() ? List.of("配置内容一致") : changes;
	}

	private List<Map<String, Object>> publicationHistory(Map<String, Object> config) {
		return jdbc.queryForList(
				"SELECT o.id,o.action,o.administrator_name,DATE_FORMAT(o.created_at,'%Y-%m-%dT%H:%i:%sZ') AS created_at,o.changes_json,p.revision_no AS previous_revision_no,t.revision_no AS target_revision_no FROM zf_companion_operation o LEFT JOIN zf_companion_revision p ON p.id=o.previous_revision_id AND p.tenant_id=o.tenant_id LEFT JOIN zf_companion_revision t ON t.id=o.target_revision_id AND t.tenant_id=o.tenant_id WHERE o.tenant_id=? AND o.config_id=? ORDER BY o.created_at DESC,o.id DESC LIMIT 50",
				tenant(), config.get("id"))
			.stream()
			.map(row -> {
				var value = new LinkedHashMap<String, Object>();
				value.put("id", row.get("id").toString());
				value.put("action", row.get("action"));
				value.put("administrator", row.get("administrator_name"));
				value.put("createdAt", row.get("created_at").toString());
				value.put("previousRevisionNo", row.get("previous_revision_no"));
				value.put("targetRevisionNo", row.get("target_revision_no"));
				try {
					value.put("changes", json.readValue(row.get("changes_json").toString(), List.class));
				}
				catch (java.io.IOException invalid) {
					throw error(HttpStatus.SERVICE_UNAVAILABLE, "COMPANION_HISTORY_INVALID");
				}
				return (Map<String, Object>) value;
			})
			.toList();
	}

	private Map<String, Object> initialize() {
		jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE", tenant());
		var existing = jdbc.queryForList(
				"SELECT * FROM zf_companion_config WHERE tenant_id=? AND config_key='default' FOR UPDATE", tenant());
		if (!existing.isEmpty())
			return existing.get(0);
		long key = IdWorker.getId();
		jdbc.update(
				"INSERT INTO zf_companion_config(id,tenant_id,config_key,display_name,skill_binding_id,status) VALUES(?,?,'default','此间陪伴',?,'DISABLED')",
				key, tenant(), key);
		Long bindingId = bindings.createIdentityDraft(ZenflowCompanionSkillPolicy.consumer(Long.toString(key)));
		jdbc.update("UPDATE zf_companion_config SET skill_binding_id=? WHERE tenant_id=? AND id=?", bindingId, tenant(),
				key);
		return lockConfig();
	}

	private Map<String, Object> lockConfig() {
		var rows = jdbc.queryForList(
				"SELECT * FROM zf_companion_config WHERE tenant_id=? AND config_key='default' FOR UPDATE", tenant());
		if (rows.size() != 1)
			throw error(HttpStatus.NOT_FOUND, "COMPANION_NOT_FOUND");
		return rows.get(0);
	}

	private Map<String, Object> revision(Map<String, Object> config, long id, boolean lock) {
		var rows = jdbc.queryForList("SELECT * FROM zf_companion_revision WHERE tenant_id=? AND config_id=? AND id=?"
				+ (lock ? " FOR UPDATE" : ""), tenant(), config.get("id"), id);
		if (rows.size() != 1)
			throw error(HttpStatus.NOT_FOUND, "COMPANION_REVISION_NOT_FOUND");
		return rows.get(0);
	}

	private long bindingVersion(long bindingId) {
		return jdbc.queryForObject("SELECT revision FROM ai_skill_binding WHERE tenant_id=? AND id=?", Long.class,
				tenant(), bindingId);
	}

	private Map<String, Object> projection(Map<String, Object> row, List<Map<String, Object>> active) {
		var result = new LinkedHashMap<String, Object>();
		for (String key : List.of("id", "revision_no", "state", "version", "error_code", "sealed_at", "created_at"))
			result.put(key.equals("revision_no") ? "revisionNo" : key,
					Objects.equals(key, "id") ? row.get(key).toString() : row.get(key));
		result.put("retrievalPolicy", readPolicy(row.get("retrieval_policy_json")).projection());
		result.put("authoring", readAuthoring(row.get("authoring_json")));
		result.put("modelId", string(row.get("model_id")));
		result.put("releaseDigest", string(row.get("release_digest")));
		result.put("active", active.size() == 1 && ((Number) active.get(0).get("enabled")).intValue() == 1
				&& row.get("id").toString().equals(parameterRevision(active.get(0).get("parameter_json"))));
		return result;
	}

	private ZenflowReferencePolicy readPolicy(Object value) {
		try {
			return ZenflowReferencePolicy.parse(json.readValue(value.toString(),
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
					}));
		}
		catch (Exception invalid) {
			throw error(HttpStatus.BAD_REQUEST, "REFERENCE_POLICY_INVALID");
		}
	}

	private String parameterRevision(Object value) {
		try {
			return json.readTree(value.toString()).path("companionRevisionId").asText();
		}
		catch (Exception error) {
			return "";
		}
	}

	private Map<String, Object> readAuthoring(Object value) {
		try {
			return json.readValue(value.toString(),
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
					});
		}
		catch (Exception error) {
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "COMPANION_AUTHORING_INVALID");
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> authoring(Object value) {
		if (!(value instanceof Map<?, ?>))
			throw error(HttpStatus.BAD_REQUEST, "COMPANION_AUTHORING_REQUIRED");
		return (Map<String, Object>) value;
	}

	private void expected(Map<String, Object> row, Map<String, Object> body) {
		if (((Number) row.get("version")).longValue() != number(body, "expectedVersion"))
			throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT");
	}

	private static long number(Map<String, Object> body, String field) {
		if (!(body.get(field) instanceof Number n) || n.longValue() < 0 || n.doubleValue() != n.longValue())
			throw error(HttpStatus.BAD_REQUEST, "EXPECTED_VERSION_REQUIRED");
		return ((Number) body.get(field)).longValue();
	}

	private static long id(Object value) {
		if (!(value instanceof String s) || !s.matches("[1-9][0-9]{0,18}"))
			throw error(HttpStatus.BAD_REQUEST, "INVALID_ID");
		try {
			return Long.parseLong((String) value);
		}
		catch (NumberFormatException e) {
			throw error(HttpStatus.BAD_REQUEST, "INVALID_ID");
		}
	}

	private static void fields(Map<String, Object> body, Set<String> allowed) {
		if (body == null || !allowed.containsAll(body.keySet()))
			throw error(HttpStatus.BAD_REQUEST, "UNKNOWN_FIELD");
	}

	private String serialize(Object value) {
		try {
			return json.writeValueAsString(value);
		}
		catch (Exception e) {
			throw error(HttpStatus.BAD_REQUEST, "COMPANION_AUTHORING_INVALID");
		}
	}

	private long tenant() {
		return product.getTenantId();
	}

	private static String string(Object value) {
		return value == null ? null : value.toString();
	}

	private static ResponseStatusException error(HttpStatus status, String code) {
		return new ResponseStatusException(status, code);
	}

}
