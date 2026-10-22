package com.deepdimen.matrix.zenflow.companion;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Frozen bodies use the reviewed P1 table; mutable knowledge slices are never an online
 * source.
 */
@Component
public class ZenflowReferenceSnapshots {

	private final JdbcTemplate jdbc;

	private final ZenflowProperties product;

	private final ZenflowReferenceSources sources;

	private final ObjectMapper json = new ObjectMapper();

	public record Snapshot(String id, String datasetId, String documentId, String sliceId, String sourceVersion,
			String contentHash, String kind, String title, String locale, String content, String sourceLocator,
			String citationVisibility) {
		public Map<String, Object> manifest() {
			var value = new TreeMap<String, Object>();
			value.put("id", id);
			value.put("datasetId", datasetId);
			value.put("documentId", documentId);
			value.put("sliceId", sliceId);
			value.put("sourceVersion", sourceVersion);
			value.put("sourceHash", contentHash);
			value.put("kind", kind);
			value.put("title", title);
			value.put("locale", locale);
			value.put("sourceLocator", sourceLocator);
			value.put("citationVisibility", citationVisibility);
			return value;
		}
	}

	public static final class Frozen {

		private final List<Snapshot> snapshots;

		private final String manifest, digest;
        private final boolean retrieval;

		private Frozen(List<Snapshot> snapshots, String manifest, String digest, boolean retrieval) {
            this.retrieval = retrieval;
			this.snapshots = List.copyOf(snapshots);
			this.manifest = manifest;
			this.digest = digest;
		}

		public List<Snapshot> snapshots() {
			return snapshots;
		}

		public boolean hasRetrieval(){return retrieval;}
        public String manifest() {
			return manifest;
		}

		public String digest() {
			return digest;
		}

		@Override
		public boolean equals(Object value) {
			return value instanceof Frozen fixed && snapshots.equals(fixed.snapshots) && manifest.equals(fixed.manifest)
					&& digest.equals(fixed.digest);
		}

		@Override
		public int hashCode() {
			return Objects.hash(snapshots, manifest, digest);
		}

	}

	public ZenflowReferenceSnapshots(JdbcTemplate jdbc, ZenflowProperties product, ZenflowReferenceSources sources) {
		this.jdbc = jdbc;
		this.product = product;
		this.sources = sources;
	}

	/**
	 * The caller owns the prepare transaction/lease; a retry reuses the original fixed
	 * bodies.
	 */
	public Frozen capture(String revisionId, ZenflowReferencePolicy policy) {
		if (!TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("REFERENCE_TRANSACTION_REQUIRED");
		var revision = jdbc.queryForList(
				"SELECT id,retrieval_policy_json,reference_digest FROM zf_companion_revision WHERE tenant_id=? AND id=? AND state='PREPARING' FOR UPDATE",
				tenant(), id(revisionId));
		if (revision.size() != 1)
			throw error("REFERENCE_PREPARE_STATE_INVALID");
		try {
			var stored = ZenflowReferencePolicy
				.parse(json.readValue(revision.get(0).get("retrieval_policy_json").toString(),
						new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
						}));
			if (!stored.equals(policy))
				throw error("REFERENCE_POLICY_CHANGED");
		}
		catch (ResponseStatusException error) {
			throw error;
		}
		catch (Exception invalid) {
			throw error("REFERENCE_POLICY_CHANGED");
		}
		var fixedExisting = readFrozen(revisionId, policy, true, true);
		if (fixedExisting != null) {
			var fixed = fixedExisting;
			if (!fixed.digest().equals(revision.get(0).get("reference_digest")))
				throw error("REFERENCE_SNAPSHOT_CHANGED");
			return fixed;
		}
		var captured = new ArrayList<Snapshot>();
		for (var selection : policy.references()) {
			var source = sources.loadForFreeze(selection);
			for (var fragment : source.fragments())
				captured.add(new Snapshot(Long.toString(IdWorker.getId()), source.datasetId(), source.documentId(),
						fragment.sliceId(), source.sourceVersion(), fragment.hash(), selection.kind(), source.title(),
						selection.locale(), fragment.content(), source.sourceLocator(),
						selection.citationVisibility()));
		}
        var fixedDocuments=policy.references().stream().filter(ref->"FIXED".equals(ref.effectiveMode())).map(ZenflowReferencePolicy.Reference::documentId).collect(java.util.stream.Collectors.toSet());
        var fixedFragments=captured.stream().filter(fragment->fixedDocuments.contains(fragment.documentId())).toList();
        if(fixedFragments.size()>20 || fixedFragments.stream().mapToInt(fragment->fragment.content().length()).sum()>12000)throw error("FIXED_REFERENCES_TOO_LARGE");
		jdbc.batchUpdate(
				"INSERT INTO zf_reference_snapshot(id,tenant_id,companion_revision_id,dataset_id,document_id,slice_id,source_version,source_hash,reference_kind,title,locale,content,source_locator,citation_visibility,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'VALID')",
				captured, 50, (statement, snapshot) -> {
					statement.setLong(1, id(snapshot.id()));
					statement.setLong(2, tenant());
					statement.setLong(3, id(revisionId));
					statement.setLong(4, id(snapshot.datasetId()));
					statement.setLong(5, id(snapshot.documentId()));
					statement.setLong(6, id(snapshot.sliceId()));
					statement.setString(7, snapshot.sourceVersion());
					statement.setString(8, snapshot.contentHash());
					statement.setString(9, snapshot.kind());
					statement.setString(10, snapshot.title());
					statement.setString(11, snapshot.locale());
					statement.setString(12, snapshot.content());
					statement.setString(13, snapshot.sourceLocator());
					statement.setString(14, snapshot.citationVisibility());
				});
		var fixed = describe(captured, policy);
		jdbc.update(
				"UPDATE zf_companion_revision SET reference_digest=? WHERE tenant_id=? AND id=? AND state='PREPARING'",
				fixed.digest(), tenant(), id(revisionId));
		return fixed;
	}

	/**
	 * Checks source revocation independently of normal source edits; verifies the pinned
	 * immutable digest.
	 */
	public Frozen verify(String revisionId, ZenflowReferencePolicy policy, String expectedDigest) {
		Long parents = jdbc.queryForObject(
				"SELECT COUNT(*) FROM zf_companion_revision WHERE tenant_id=? AND id=? AND state IN ('PREPARING','READY') AND reference_digest=?",
				Long.class, tenant(), id(revisionId), expectedDigest);
		if (parents == null || parents != 1)
			throw error("REFERENCE_PREPARE_STATE_INVALID");
		var frozen = readFrozen(revisionId, policy, false, false);
		if (!frozen.digest().equals(expectedDigest))
			throw error("REFERENCE_SNAPSHOT_CHANGED");
		return frozen;
	}

	/**
	 * Revalidates only the selected immutable roots during streaming, never the editable
	 * slices.
	 */
	public List<Snapshot> selected(String revisionId, ZenflowReferencePolicy policy, String expectedDigest,
			Map<String, String> hashes) {
		return selectedRoots(revisionId, policy, expectedDigest, hashes, 26);
	}

	List<Snapshot> historyRoots(String revisionId, ZenflowReferencePolicy policy, String expectedDigest,
			Map<String, String> hashes) {
		return selectedRoots(revisionId, policy, expectedDigest, hashes, 300);
	}

	private List<Snapshot> selectedRoots(String revisionId, ZenflowReferencePolicy policy, String expectedDigest,
			Map<String, String> hashes, int limit) {
		if (hashes == null || hashes.isEmpty() || hashes.size() > limit)
			throw error("REFERENCE_SELECTION_INVALID");
		hashes.forEach((key, value) -> {
			id(key);
			if (value == null || !value.matches("[a-f0-9]{64}"))
				throw error("REFERENCE_SELECTION_INVALID");
		});
		var parents = jdbc.queryForList(
				"SELECT retrieval_policy_json FROM zf_companion_revision WHERE tenant_id=? AND id=? AND state='READY' AND reference_digest=?",
				tenant(), id(revisionId), expectedDigest);
		if (parents.size() != 1)
			throw error("REFERENCE_PREPARE_STATE_INVALID");
		try {
			var stored = ZenflowReferencePolicy
				.parse(json.readValue(parents.get(0).get("retrieval_policy_json").toString(),
						new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
						}));
			if (!stored.equals(policy))
				throw error("REFERENCE_POLICY_CHANGED");
		}
		catch (ResponseStatusException failure) {
			throw failure;
		}
		catch (Exception invalid) {
			throw error("REFERENCE_POLICY_CHANGED");
		}
		var selected = readSnapshots(revisionId, policy, false, false, hashes.keySet());
		for (var snapshot : selected)
			if (!snapshot.contentHash().equals(hashes.get(snapshot.id())))
				throw error("REFERENCE_SNAPSHOT_CHANGED");
		return List.copyOf(selected);
	}

	private Frozen readFrozen(String revisionId, ZenflowReferencePolicy policy, boolean lock, boolean allowEmpty) {
		var snapshots = readSnapshots(revisionId, policy, lock, allowEmpty, null);
		return snapshots == null ? null : describe(snapshots, policy);
	}

	private List<Snapshot> readSnapshots(String revisionId, ZenflowReferencePolicy policy, boolean lock,
			boolean allowEmpty, Set<String> selectedIds) {
		var expected = new HashMap<String, ZenflowReferencePolicy.Reference>();
		for (var selection : policy.references())
			expected.put(selection.documentId(), selection);
		String sql = "SELECT f.id,f.dataset_id,f.document_id,f.slice_id,f.source_version,f.source_hash,f.reference_kind,f.title,f.locale,f.content,f.source_locator,f.citation_visibility,f.status,"
				+ "d.document_config,d.del_flag AS document_deleted,k.del_flag AS dataset_deleted,k.collection_name "
				+ "FROM zf_reference_snapshot f LEFT JOIN ai_document d ON d.id=f.document_id AND d.dataset_id=f.dataset_id AND d.tenant_id=f.tenant_id "
				+ "LEFT JOIN ai_dataset k ON k.id=f.dataset_id AND k.tenant_id=f.tenant_id WHERE f.tenant_id=? AND f.companion_revision_id=?";
		var parameters = new ArrayList<Object>();
		parameters.add(tenant());
		parameters.add(id(revisionId));
		if (selectedIds != null) {
			sql += " AND f.id IN (" + String.join(",", Collections.nCopies(selectedIds.size(), "?")) + ")";
			for (String selected : selectedIds)
				parameters.add(id(selected));
		}
		sql += " ORDER BY f.id LIMIT " + (selectedIds == null ? 20001 : selectedIds.size() + 1)
				+ (lock ? " FOR UPDATE" : "");
		return jdbc.query(sql, result -> {
			var snapshots = new ArrayList<Snapshot>();
			var found = new HashSet<String>();
			var foundIds = new HashSet<String>();
			var approved = new HashSet<String>();
			int total = 0;
			while (result.next()) {
				if (snapshots.size() == 20000)
					throw error("REFERENCE_SNAPSHOT_TOO_LARGE");
				String document = result.getString("document_id");
				var selection = expected.get(document);
				if (selection == null || !"VALID".equals(result.getString("status"))
						|| !"0".equals(result.getString("document_deleted"))
						|| !"0".equals(result.getString("dataset_deleted"))
						|| !ZenflowReferenceSources.collection(tenant()).equals(result.getString("collection_name")))
					throw error("REFERENCE_SOURCE_REVOKED");
				// All rows for this primary-key document share one SQL snapshot of its
				// approval.
				if (approved.add(document)) {
					try {
						var metadata = json.readTree(result.getString("document_config"));
						if (!"PUBLIC".equals(metadata.path("invelleAudience").asText())
								|| !metadata.path("invelleCompanionApproved").isBoolean()
								|| !metadata.path("invelleCompanionApproved").booleanValue())
							throw error("REFERENCE_SOURCE_REVOKED");
					}
					catch (ResponseStatusException error) {
						throw error;
					}
					catch (Exception invalid) {
						throw error("REFERENCE_SOURCE_REVOKED");
					}
				}
				String content = Objects.toString(result.getString("content"), ""),
						hash = result.getString("source_hash");
				total += content.length();
				if (content.isBlank() || content.length() > 16000 || total > 8000000
						|| !ZenflowRunAuthorization.hash(content).equals(hash))
					throw error("REFERENCE_SNAPSHOT_CHANGED");
				if (!selection.kind().equals(result.getString("reference_kind"))
						|| !selection.locale().equals(result.getString("locale"))
						|| !selection.citationVisibility().equals(result.getString("citation_visibility")))
					throw error("REFERENCE_POLICY_CHANGED");
				found.add(document);
				if (selectedIds != null)
					foundIds.add(result.getString("id"));
				snapshots.add(new Snapshot(result.getString("id"), result.getString("dataset_id"), document,
						result.getString("slice_id"), result.getString("source_version"), hash,
						result.getString("reference_kind"), result.getString("title"), result.getString("locale"),
						content, Objects.toString(result.getString("source_locator"), ""),
						result.getString("citation_visibility")));
			}
			if (snapshots.isEmpty() && allowEmpty)
				return null;
			if (selectedIds == null) {
				if (!found.equals(expected.keySet()))
					throw error("REFERENCE_SNAPSHOT_INCOMPLETE");
			}
			else if (!foundIds.equals(selectedIds))
				throw error("REFERENCE_SNAPSHOT_INCOMPLETE");
			return snapshots;
		}, parameters.toArray());
	}

	private Frozen describe(List<Snapshot> snapshots, ZenflowReferencePolicy policy) {
		var ordered = snapshots.stream().sorted(Comparator.comparingLong(s -> id(s.id()))).toList();
		String manifest;
		try {
			manifest = ordered.isEmpty() ? "[]"
					: json.writer()
						.with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
						.writeValueAsString(Map.of("snapshots", ordered.stream().map(Snapshot::manifest).toList(),
								"retrievalPolicy", policy.projection()));
		}
		catch (Exception invalid) {
			throw error("REFERENCE_MANIFEST_INVALID");
		}
		return new Frozen(ordered, manifest, ZenflowRunAuthorization.hash(manifest), policy.hasRetrieval());
	}

	private static long id(String value) {
		try {
			if (value == null || !value.matches("[1-9][0-9]{0,18}"))
				throw new IllegalArgumentException();
			return Long.parseLong(value);
		}
		catch (Exception invalid) {
			throw error("REFERENCE_ID_INVALID");
		}
	}

	private long tenant() {
		return product.getTenantId();
	}

	private static ResponseStatusException error(String code) {
		return new ResponseStatusException(HttpStatus.CONFLICT, code);
	}

}
