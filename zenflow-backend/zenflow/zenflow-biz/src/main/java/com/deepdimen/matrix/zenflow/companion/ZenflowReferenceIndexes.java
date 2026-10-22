package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.zenflow.admin.ZenflowEmbeddingGateway;
import com.deepdimen.matrix.zenflow.admin.ZenflowEmbeddingModels;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public-only indexing of immutable snapshots through the original explicitly configured
 * store.
 */
@Component
public class ZenflowReferenceIndexes implements ZenflowReferenceIndexPort {

	static final String META_TENANT = "tenantId";

	static final String META_REVISION = "companionRevisionId";

	static final String META_REFERENCE_DIGEST = "referenceDigest";

	static final String META_INDEX_DIGEST = "indexConfigurationDigest";

	static final String META_SNAPSHOT = "snapshotId";

	static final String META_SOURCE_HASH = "sourceHash";

	static final Set<String> CONFIGURATION_KEYS = Set.of("embeddingModelId", "embeddingModelDigest", "dimensions",
			"storeId", "storeDigest", "namespace");

	private final JdbcTemplate jdbc;

	private final ZenflowEmbeddingModels models;

	private final ZenflowEmbeddingGateway embeddings;

	private final EmbeddingStoreService stores;

	private final ObjectMapper json;

	private final Environment environment;

	private final long tenant;

	public ZenflowReferenceIndexes(JdbcTemplate jdbc, ZenflowEmbeddingModels models, ZenflowEmbeddingGateway embeddings,
			EmbeddingStoreService stores, ObjectMapper json, Environment environment, ZenflowProperties product) {
		this.jdbc = jdbc;
		this.models = models;
		this.embeddings = embeddings;
		this.stores = stores;
		this.json = json;
		this.environment = environment;
		this.tenant = product.getTenantId();
	}

	/**
	 * The caller locks the revision; retries preserve the complete original descriptor.
	 */
	void pin(Map<String, Object> revision, ZenflowReferencePolicy policy) {
		if (!TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("REFERENCE_TRANSACTION_REQUIRED");
		if (!policy.hasRetrieval())
			return;
		if (revision.get("index_configuration_json") != null) {
			verify(revision);
			return;
		}
		if (revision.get("reference_digest") != null)
			throw error("REFERENCE_INDEX_CONFIGURATION_MISSING");
		var model = models.active();
		String storeId = environment.getProperty("ZENFLOW_VECTOR_STORE_ID");
		var store = store(storeId);
		String namespace = "invelle_public_" + tenant + "_" + positive(revision.get("id"));
		var plan = new TreeMap<String, Object>();
		plan.put("embeddingModelId", model.get("id"));
		plan.put("embeddingModelDigest", models.digest(model));
		plan.put("dimensions", model.get("dimensions"));
		plan.put("storeId", storeId);
		plan.put("storeDigest", storeDigest(store));
		plan.put("namespace", namespace);
		String serialized = serialize(plan), digest = ZenflowRunAuthorization.hash(serialized);
		jdbc.update(
				"UPDATE zf_companion_revision SET index_namespace=?,index_configuration_json=?,index_configuration_digest=? WHERE tenant_id=? AND id=?",
				namespace, serialized, digest, tenant, positive(revision.get("id")));
	}

	public Map<String, Object> verify(Map<String, Object> revision) {
		if (!(revision.get("tenant_id") instanceof Number owner) || owner.longValue() != tenant)
			throw error("REFERENCE_INDEX_CONFIGURATION_INVALID");
		Map<String, Object> plan;
		try {
			plan = json.readValue(revision.get("index_configuration_json").toString(),
					new TypeReference<Map<String, Object>>() {
					});
		}
		catch (Exception invalid) {
			throw error("REFERENCE_INDEX_CONFIGURATION_INVALID");
		}
		if (!plan.keySet().equals(CONFIGURATION_KEYS)
				|| !ZenflowRunAuthorization.hash(serialize(plan)).equals(revision.get("index_configuration_digest")))
			throw error("REFERENCE_INDEX_CONFIGURATION_INVALID");
		String namespace = "invelle_public_" + tenant + "_" + positive(revision.get("id"));
		if (!namespace.equals(plan.get("namespace")) || !namespace.equals(revision.get("index_namespace")))
			throw error("REFERENCE_INDEX_CONFIGURATION_INVALID");
		verifyLive(plan);
		return Map.copyOf(plan);
	}

	private AiEmbedStoreEntity verifyLive(Map<String, Object> plan) {
		String modelId = positive(plan.get("embeddingModelId"));
		var model = models.publishedSnapshot(modelId);
		if (!models.digest(model).equals(plan.get("embeddingModelDigest"))
				|| !(plan.get("dimensions") instanceof Number dimension) || dimension.intValue() < 1
				|| dimension.intValue() > 4096 || dimension.doubleValue() != dimension.intValue()
				|| !model.get("dimensions").equals(dimension.intValue()))
			throw error("EMBEDDING_CONFIGURATION_CHANGED");
		var configuredStore = store(positive(plan.get("storeId")));
		if (!storeDigest(configuredStore).equals(plan.get("storeDigest")))
			throw error("REFERENCE_STORE_CONFIGURATION_CHANGED");
		return configuredStore;
	}

	/**
	 * Query text is the current user input, never capsule bodies or inherited context.
	 */
	public List<EmbeddingMatch<TextSegment>> search(Map<String, Object> revision, ZenflowReferencePolicy policy,
			String query, Runnable heartbeat) {
		if (TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("REFERENCE_INDEX_EXTERNAL_TRANSACTION_DENIED");
		if (!"READY".equals(revision.get("state")) || query == null || query.isBlank() || query.length() > 16000)
			throw error("REFERENCE_RETRIEVAL_INVALID");
		if (!policy.hasRetrieval())
			return List.of();
		var plan = verify(revision);
		heartbeat.run();
		var vector = embeddings
			.embed(plan.get("embeddingModelId").toString(), plan.get("embeddingModelDigest").toString(), List.of(query))
			.get(0);
		heartbeat.run();
		var configuredStore = verifyLive(plan);
		var store = stores.embeddingStoreFixed(plan.get("namespace").toString(), configuredStore,
				((Number) plan.get("dimensions")).intValue(), revision.get("index_configuration_digest").toString());
		var filter = metadataKey(META_TENANT).isEqualTo(Long.toString(tenant))
			.and(metadataKey(META_REVISION).isEqualTo(positive(revision.get("id"))))
			.and(metadataKey(META_REFERENCE_DIGEST).isEqualTo(revision.get("reference_digest").toString()))
			.and(metadataKey(META_INDEX_DIGEST).isEqualTo(revision.get("index_configuration_digest").toString()));
		var result = store.search(EmbeddingSearchRequest.builder()
			.queryEmbedding(Embedding.from(vector))
			.maxResults(policy.maxFragments())
			.minScore(policy.minScore())
			.filter(filter)
			.build());
		heartbeat.run();
		verifyLive(plan);
		return List.copyOf(result.matches());
	}

	@Override
	public void prepare(String revisionId, ZenflowReferenceSnapshots.Frozen references, Runnable heartbeat) {
		if (TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("REFERENCE_INDEX_EXTERNAL_TRANSACTION_DENIED");
		String validatedRevision = positive(revisionId);
		var rows = jdbc.queryForList(
				"SELECT * FROM zf_companion_revision WHERE tenant_id=? AND id=? AND state='PREPARING'", tenant,
				validatedRevision);
		if (rows.size() != 1 || !references.digest().equals(rows.get(0).get("reference_digest")))
			throw error("REFERENCE_PREPARE_STATE_INVALID");
		var plan = verify(rows.get(0));
		heartbeat.run();
		var store = stores.embeddingStoreFixed(plan.get("namespace").toString(), store(positive(plan.get("storeId"))),
				((Number) plan.get("dimensions")).intValue(), rows.get(0).get("index_configuration_digest").toString());
		ZenflowReferencePolicy policy;
        try{policy=ZenflowReferencePolicy.parse(json.readValue(rows.get(0).get("retrieval_policy_json").toString(),new TypeReference<Map<String,Object>>(){}));}catch(Exception invalid){throw error("REFERENCE_POLICY_INVALID");}
        var retrievalDocuments=policy.references().stream().filter(ref->"RETRIEVAL".equals(ref.effectiveMode())).map(ZenflowReferencePolicy.Reference::documentId).collect(java.util.stream.Collectors.toSet());
        var snapshots = references.snapshots().stream().filter(snapshot->retrievalDocuments.contains(snapshot.documentId())).toList();
		for (int start = 0; start < snapshots.size(); start += 8) {
			heartbeat.run();
			var batch = snapshots.subList(start, Math.min(start + 8, snapshots.size()));
			var vectors = embeddings.embed(plan.get("embeddingModelId").toString(),
					plan.get("embeddingModelDigest").toString(),
					batch.stream().map(ZenflowReferenceSnapshots.Snapshot::content).toList());
			heartbeat.run();
			verifyLive(plan);
			var ids = new ArrayList<String>();
			var segments = new ArrayList<TextSegment>();
			for (var snapshot : batch) {
				ids.add(pointIdValidated(validatedRevision, snapshot.id()));
				segments.add(TextSegment.from(snapshot.content(),
						Metadata.from(Map.of(META_TENANT, Long.toString(tenant), META_REVISION, revisionId,
								META_SNAPSHOT, snapshot.id(), META_SOURCE_HASH, snapshot.contentHash(),
								META_REFERENCE_DIGEST, references.digest(), META_INDEX_DIGEST,
								rows.get(0).get("index_configuration_digest").toString()))));
			}
			store.addAll(ids, vectors.stream().map(Embedding::from).toList(), segments);
			heartbeat.run();
		}
		verifyLive(plan);
	}

	private AiEmbedStoreEntity store(String id) {
		var rows = jdbc.queryForList(
				"SELECT store_id,store_type,host,port,use_tls,api_key FROM ai_embed_store WHERE tenant_id=? AND store_id=? AND del_flag='0'",
				tenant, positive(id));
		if (rows.size() != 1)
			throw error("REFERENCE_STORE_NOT_CONFIGURED");
		var row = rows.get(0);
		if (!"qdrant".equals(row.get("store_type")) || !"invelle-vector".equals(row.get("host"))
				|| !(row.get("port") instanceof Number port) || port.intValue() != 6334
				|| !"0".equals(row.get("use_tls")) || !(row.get("api_key") instanceof String key)
				|| !key.matches("[\\x21-\\x7E]{32,255}"))
			throw error("REFERENCE_STORE_CONFIGURATION_INVALID");
		var store = new AiEmbedStoreEntity();
		store.setStoreId(Long.parseLong(id));
		store.setStoreType("qdrant");
		store.setHost("invelle-vector");
		store.setPort(6334);
		store.setUseTls("0");
		store.setApiKey(key);
		return store;
	}

	private String storeDigest(AiEmbedStoreEntity store) {
		return ZenflowRunAuthorization.hash(serialize(Map.of("storeId", store.getStoreId().toString(), "type",
				store.getStoreType(), "host", store.getHost(), "port", store.getPort(), "useTls", store.getUseTls())));
	}

	String pointId(String revisionId, String snapshotId) {
		return pointIdValidated(positive(revisionId), snapshotId);
	}

	private String pointIdValidated(String revisionId, String snapshotId) {
		return UUID
			.nameUUIDFromBytes(("invelle-reference:" + tenant + ":" + revisionId + ":" + positive(snapshotId))
				.getBytes(StandardCharsets.UTF_8))
			.toString();
	}

	private String serialize(Map<String, Object> value) {
		try {
			return json.writeValueAsString(new TreeMap<>(value));
		}
		catch (Exception invalid) {
			throw error("REFERENCE_INDEX_CONFIGURATION_INVALID");
		}
	}

	private static String positive(Object value) {
		try {
			if (value == null || !value.toString().matches("[1-9][0-9]{0,18}") || Long.parseLong(value.toString()) <= 0)
				throw new IllegalArgumentException();
			return value.toString();
		}
		catch (Exception invalid) {
			throw error("REFERENCE_INDEX_CONFIGURATION_INVALID");
		}
	}

	private static ResponseStatusException error(String code) {
		return new ResponseStatusException(HttpStatus.CONFLICT, code);
	}

}
