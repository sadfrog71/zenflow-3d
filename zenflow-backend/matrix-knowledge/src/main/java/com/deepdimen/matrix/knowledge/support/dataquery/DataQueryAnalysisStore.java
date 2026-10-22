package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.Identity;

/** Optimistic single-query lease; terminal writes cannot overwrite a newer run. */
final class DataQueryAnalysisStore {

	record Entry(int revision, String requestId, String requestDigest, String status,
			DataQueryResultAnalysis.Analysis attempt, DataQueryResultAnalysis.Analysis result) {
	}

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	DataQueryAnalysisStore(DataSource source, ObjectMapper json, Clock clock) {
		this.jdbc = new JdbcTemplate(source);
		jdbc.setQueryTimeout(5);
		this.json = json;
		this.clock = clock;
	}

	Entry read(Identity owner, String queryId) {
		var rows = jdbc.query(
				"SELECT revision,request_id,request_digest,status,attempt_json,result_json FROM ai_dataquery_analysis WHERE query_id=? AND tenant_id=? AND user_id=?",
				(rs, n) -> new Entry(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
						decode(rs.getString(5)), decode(rs.getString(6))),
				queryId, owner.tenantId(), owner.userId());
		return rows.isEmpty() ? new Entry(0, null, null, "NOT_RECORDED", null, null) : rows.get(0);
	}

	Entry begin(Identity owner, String queryId, int revision, String requestId, String digest,
			DataQueryResultAnalysis.Analysis attempt) {
		try {
			jdbc.update("INSERT INTO ai_dataquery_analysis(query_id,tenant_id,user_id,updated_at) VALUES(?,?,?,?)",
					queryId, owner.tenantId(), owner.userId(), now());
		}
		catch (DuplicateKeyException duplicate) {
		}
		var current = read(owner, queryId);
		if (Objects.equals(current.requestId(), requestId)) {
			if (!Objects.equals(current.requestDigest(), digest))
				throw new DataQueryException("ANALYSIS_REQUEST_CHANGED");
			throw new DataQueryException(
					"RUNNING".equals(current.status()) ? "ANALYSIS_IN_PROGRESS" : "ANALYSIS_CHANGED");
		}
		if ("RUNNING".equals(current.status()))
			throw new DataQueryException("ANALYSIS_IN_PROGRESS");
		if (current.revision() != revision)
			throw new DataQueryException("ANALYSIS_CHANGED");
		if (jdbc.update(
				"UPDATE ai_dataquery_analysis SET revision=revision+1,request_id=?,request_digest=?,status='RUNNING',attempt_json=?,updated_at=? WHERE query_id=? AND tenant_id=? AND user_id=? AND revision=? AND status<>'RUNNING'",
				requestId, digest, encode(attempt), now(), queryId, owner.tenantId(), owner.userId(), revision) != 1)
			throw new DataQueryException("ANALYSIS_CHANGED");
		return new Entry(revision + 1, requestId, digest, "RUNNING", attempt, current.result());
	}

	void finish(Identity owner, String queryId, int revision, DataQueryResultAnalysis.Analysis attempt) {
		String success = "SUCCEEDED".equals(attempt.status()) ? encode(attempt) : null;
		int changed = jdbc.update(
				"UPDATE ai_dataquery_analysis SET status=?,attempt_json=?,result_json=COALESCE(?,result_json),updated_at=? WHERE query_id=? AND tenant_id=? AND user_id=? AND revision=? AND request_id=? AND status='RUNNING'",
				attempt.status(), encode(attempt), success, now(), queryId, owner.tenantId(), owner.userId(), revision,
				attempt.requestId());
		if (changed != 1)
			throw new DataQueryException("ANALYSIS_CHANGED");
	}

	private String encode(DataQueryResultAnalysis.Analysis value) {
		try {
			return json.writeValueAsString(value);
		}
		catch (Exception e) {
			throw new DataQueryException("ANALYSIS_STORAGE_UNAVAILABLE");
		}
	}

	private DataQueryResultAnalysis.Analysis decode(String value) {
		if (value == null)
			return null;
		try {
			if (value.length() > 180000)
				throw new IllegalArgumentException();
			return json.readValue(value, DataQueryResultAnalysis.Analysis.class);
		}
		catch (Exception e) {
			throw new DataQueryException("ANALYSIS_STORAGE_UNAVAILABLE");
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

}
