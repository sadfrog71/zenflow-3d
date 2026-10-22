package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Bounded presentation snapshots, kept outside frequently-read audit/history payloads.
 */
public final class DataQueryVisualizations {

	static final int MAX_BYTES = 1024 * 1024;

	public record Selection(String view, int category, int metric) {
	}

	public record Update(Integer revision, Selection selection) {
	}

	public record Snapshot(List<String> columns, List<List<Object>> rows, boolean partial, String savedAt) {
	}

	public record View(String queryId, String status, Snapshot snapshot, Selection selection, int revision) {
	}

	public record Saved(String queryId, int revision) {
	}

	private final DataQueryMetadataRepository metadata;

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	private final org.springframework.transaction.support.TransactionTemplate transaction;

	DataQueryVisualizations(DataQueryMetadataRepository metadata, JdbcTemplate jdbc, ObjectMapper json, Clock clock,
			org.springframework.transaction.support.TransactionTemplate transaction) {
		this.metadata = metadata;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
		this.transaction = transaction;
	}

	/** Server result only. No API accepts result cells from a browser/model. */
	Map<String, Object> capture(DataQueryAiChatService.Context context, Map<String, Object> response) {
		String status;
		try {
			Snapshot snapshot = new Snapshot((List<String>) response.get("columns"),
					(List<List<Object>>) response.get("rows"), partial(response), clock.instant().toString());
			validate(snapshot);
			String encoded = json.writeValueAsString(snapshot);
			if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
				throw new DataQueryException("VISUALIZATION_TOO_LARGE");
			transaction.executeWithoutResult(ignored -> jdbc.update(
					"INSERT INTO ai_dataquery_visualization(query_id,tenant_id,user_id,snapshot_json,snapshot_digest,revision,created_at,updated_at) VALUES(?,?,?,?,?,0,?,?)",
					context.queryId(), Long.valueOf(context.identity().tenantId()),
					Long.valueOf(context.identity().userId()), encoded,
					DataQueryMetadataRepository.fingerprint(encoded), now(), now()));
			status = "AVAILABLE";
		}
		catch (Exception failure) {
			// Presentation persistence must not turn a successful business SELECT into an
			// error.
			// Do not log the exception: JDBC errors can contain result cells.
			status = failure instanceof DataQueryException && "VISUALIZATION_TOO_LARGE".equals(failure.getMessage())
					? "TOO_LARGE" : "UNAVAILABLE";
		}
		Map<String, Object> marker = Map.of("status", status, "revision", 0);
		try {
			metadata.recordEvidence(context.queryId(), context.identity(), "visualization", marker);
		}
		catch (RuntimeException unavailable) {
			marker = Map.of("status", "UNAVAILABLE", "revision", 0);
		}
		return marker;
	}

	private static boolean partial(Map<String, Object> response) {
		for (String key : List.of("resultPreview", "page", "quality")) {
			if (response.get(key) instanceof Map<?, ?> flags
					&& (Boolean.TRUE.equals(flags.get("hasMore")) || Boolean.TRUE.equals(flags.get("truncated"))))
				return true;
		}
		return false;
	}

	private void authorize(Identity identity, String queryId) {
		if (queryId == null || !queryId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
			throw new DataQueryException("QUERY_NOT_FOUND");
		var q = metadata.findOwnedQuery(identity, queryId).orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
		if (!"FINISHED".equals(q.status()) || !"OK".equals(q.resultCode()) || q.chatRecordId() == null)
			throw new DataQueryException("QUERY_NOT_FOUND");
		var owned = jdbc.queryForList("""
				SELECT c.question_text FROM ai_chat_record c JOIN sys_user u ON u.username=c.username
				WHERE c.record_id=? AND c.tenant_id=? AND u.user_id=? AND c.del_flag='0'
				AND u.del_flag='0' AND u.lock_flag='0'
				AND EXISTS (SELECT 1 FROM sys_tenant_user tu WHERE tu.user_id=u.user_id AND tu.tenant_id=c.tenant_id)
				""", String.class, q.chatRecordId(), Long.valueOf(identity.tenantId()),
				Long.valueOf(identity.userId()));
		if (owned.size() != 1 || !Objects.equals(owned.get(0), q.originalQuestion()))
			throw new DataQueryException("QUERY_NOT_FOUND");
		var scope = metadata.resolve(identity);
		if (!scope.revision().equals(q.scopeRevision()))
			throw new DataQueryException("AUTHORIZATION_CHANGED");
		try {
			var candidate = json.readTree(q.candidateJson());
			if (!"AI_SQL".equals(candidate.path("kind").asText())
					|| !Long.toString(scope.source().dataId()).equals(candidate.path("dataId").asText())
					|| !metadata.inventory(identity, scope.source())
						.digest()
						.equals(candidate.path("inventoryDigest").asText()))
				throw new DataQueryException("AUTHORIZATION_CHANGED");
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
			throw new DataQueryException("VISUALIZATION_UNAVAILABLE");
		}
	}

	public View read(Identity identity, String queryId) {
		authorize(identity, queryId);
		return load(identity, queryId);
	}

	private View load(Identity identity, String queryId) {
		var found = jdbc.query(
				"SELECT snapshot_json,snapshot_digest,selection_json,revision FROM ai_dataquery_visualization WHERE query_id=? AND tenant_id=? AND user_id=?",
				(rs, n) -> new String[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4) }, queryId,
				Long.valueOf(identity.tenantId()), Long.valueOf(identity.userId()));
		if (found.isEmpty())
			return new View(queryId, "NOT_RECORDED", null, null, 0);
		var row = found.get(0);
		try {
			if (row[0].getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
					|| !DataQueryMetadataRepository.fingerprint(row[0]).equals(row[1]))
				throw new IllegalArgumentException();
			Snapshot snapshot = json.readValue(row[0], Snapshot.class);
			validate(snapshot);
			Selection selection = row[2] == null ? null : json.readValue(row[2], Selection.class);
			validate(selection, snapshot);
			return new View(queryId, "AVAILABLE", snapshot, selection, Integer.parseInt(row[3]));
		}
		catch (Exception malformed) {
			throw new DataQueryException("VISUALIZATION_UNAVAILABLE");
		}
	}

	public Saved update(Identity identity, String queryId, Update update) {
		authorize(identity, queryId);
		if (update == null || update.revision() == null || update.revision() < 0)
			throw new DataQueryException("INVALID_REQUEST");
		var current = load(identity, queryId);
		if (!"AVAILABLE".equals(current.status()))
			throw new DataQueryException("VISUALIZATION_UNAVAILABLE");
		validate(update.selection(), current.snapshot());
		// Lost-response retry is idempotent, but another tab's different selection is
		// never silently overwritten.
		if (current.revision() != update.revision()) {
			if (current.revision() == update.revision() + 1 && Objects.equals(current.selection(), update.selection()))
				return new Saved(queryId, current.revision());
			throw new DataQueryException("VISUALIZATION_CHANGED");
		}
		String encoded;
		try {
			encoded = update.selection() == null ? null : json.writeValueAsString(update.selection());
		}
		catch (Exception invalid) {
			throw new DataQueryException("INVALID_REQUEST");
		}
		if (jdbc.update(
				"UPDATE ai_dataquery_visualization SET selection_json=?,revision=revision+1,updated_at=? WHERE query_id=? AND tenant_id=? AND user_id=? AND revision=?",
				encoded, now(), queryId, Long.valueOf(identity.tenantId()), Long.valueOf(identity.userId()),
				update.revision()) != 1)
			throw new DataQueryException("VISUALIZATION_CHANGED");
		return new Saved(queryId, update.revision() + 1);
	}

	private static void validate(Snapshot s) {
		if (s == null || s.columns() == null || s.columns().isEmpty() || s.columns().size() > 100
				|| s.columns().stream().anyMatch(Objects::isNull) || s.rows() == null || s.rows().size() > 500
				|| s.savedAt() == null)
			throw new DataQueryException("VISUALIZATION_TOO_LARGE");
		for (var row : s.rows())
			if (row == null || row.size() != s.columns().size() || row.stream()
				.anyMatch(v -> v != null && !(v instanceof String || v instanceof Number || v instanceof Boolean)))
				throw new DataQueryException("VISUALIZATION_TOO_LARGE");
	}

	private static void validate(Selection s, Snapshot snapshot) {
		if (s == null)
			return;
		if (!Set.of("table", "bar", "line", "pie").contains(s.view() == null ? "" : s.view()) || s.category() < -1
				|| s.metric() < -1 || s.category() >= snapshot.columns().size()
				|| s.metric() >= snapshot.columns().size() || (s.category() >= 0 && s.category() == s.metric()))
			throw new DataQueryException("INVALID_REQUEST");
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

}
