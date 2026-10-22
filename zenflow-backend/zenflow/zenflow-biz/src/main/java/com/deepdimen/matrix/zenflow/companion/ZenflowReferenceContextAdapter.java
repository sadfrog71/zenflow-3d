package com.deepdimen.matrix.zenflow.companion;

import java.util.*;
import static com.deepdimen.matrix.zenflow.companion.ZenflowReferenceIndexes.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vector payload selects identities only; authorized frozen SQL supplies the model text.
 */
@Component
public class ZenflowReferenceContextAdapter {

	private final ZenflowReferenceIndexes indexes;

	private final ZenflowReferenceSnapshots snapshots;

	public ZenflowReferenceContextAdapter(ZenflowReferenceIndexes indexes, ZenflowReferenceSnapshots snapshots) {
		this.indexes = indexes;
		this.snapshots = snapshots;
	}

	public List<ZenflowReferenceSnapshots.Snapshot> retrieve(Map<String, Object> revision,
			ZenflowReferencePolicy policy, String userInput, Runnable heartbeat) {
		var fixedDocuments=policy.references().stream().filter(ref->"FIXED".equals(ref.effectiveMode())).map(ZenflowReferencePolicy.Reference::documentId).collect(java.util.stream.Collectors.toSet());
        var fixed=fixedDocuments.isEmpty()?List.<ZenflowReferenceSnapshots.Snapshot>of():snapshots.verify(revision.get("id").toString(),policy,revision.get("reference_digest").toString()).snapshots().stream().filter(snapshot->fixedDocuments.contains(snapshot.documentId())).toList();
        var matches = policy.hasRetrieval()?indexes.search(revision, policy, userInput, heartbeat):List.<dev.langchain4j.store.embedding.EmbeddingMatch<dev.langchain4j.data.segment.TextSegment>>of();
		if (matches.size() > policy.maxFragments())
			throw invalid();
		var hashes = new LinkedHashMap<String, String>();
        for(var snapshot:fixed)hashes.put(snapshot.id(),snapshot.contentHash());
		for (var match : matches) {
			if (match == null || match.embedded() == null || match.score() == null || !Double.isFinite(match.score())
					|| match.score() < policy.minScore())
				throw invalid();
			var metadata = match.embedded().metadata();
			String id = metadata.getString(META_SNAPSHOT), hash = metadata.getString(META_SOURCE_HASH);
			if (id == null || hash == null
					|| !Objects.equals(metadata.getString(META_TENANT), revision.get("tenant_id").toString())
					|| !Objects.equals(metadata.getString(META_REVISION), revision.get("id").toString())
					|| !Objects.equals(metadata.getString(META_REFERENCE_DIGEST), revision.get("reference_digest"))
					|| !Objects.equals(metadata.getString(META_INDEX_DIGEST),
							revision.get("index_configuration_digest"))
					|| !indexes.pointId(revision.get("id").toString(), id).equals(match.embeddingId())
					|| hashes.putIfAbsent(id, hash) != null)
				throw invalid();
		}
		heartbeat.run();
		if (hashes.isEmpty())
			return List.of();
		var checked = snapshots.selected(revision.get("id").toString(), policy,
				revision.get("reference_digest").toString(), hashes);
		var byId = new HashMap<String, ZenflowReferenceSnapshots.Snapshot>();
		for (var snapshot : checked)
			byId.put(snapshot.id(), snapshot);
		var result = new ArrayList<ZenflowReferenceSnapshots.Snapshot>();
		int characters = 0;
		for (String id : hashes.keySet()) {
			var snapshot = byId.get(id);
			if (snapshot == null)
				throw invalid();
			if (characters + snapshot.content().length() > 12000)
				continue;
			characters += snapshot.content().length();
			result.add(snapshot);
		}
		heartbeat.run();
		return List.copyOf(result);
	}

	private static ResponseStatusException invalid() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "REFERENCE_RETRIEVAL_INVALID");
	}

}
