package com.deepdimen.matrix.assistant.matrix;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ArtifactKind;
import com.deepdimen.matrix.assistant.run.AssistantAnalysisArtifactRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantAnalysisArtifactRepository implements AssistantAnalysisArtifactRepository {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
	};

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	public JdbcAssistantAnalysisArtifactRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public AnalysisArtifact saveIfAbsent(AnalysisArtifact artifact) {
		try {
			jdbcTemplate.update("""
					INSERT INTO ai_assistant_analysis_artifact
					(artifact_id, run_id, step_id, contract_version, artifact_kind, payload_json,
					 citation_keys_json, artifact_digest, created_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", artifact.artifactId(), artifact.runId(), artifact.stepId(), artifact.contractVersion(),
					artifact.kind().name(), write(artifact.payload()), write(artifact.citationKeys()),
					artifact.artifactDigest(), Timestamp.from(artifact.createdAt()));
			return artifact;
		}
		catch (DuplicateKeyException ex) {
			return findById(artifact.runId(), artifact.artifactId()).orElseThrow(() -> ex);
		}
	}

	@Override
	public Optional<AnalysisArtifact> findById(String runId, String artifactId) {
		return jdbcTemplate.query("SELECT * FROM ai_assistant_analysis_artifact WHERE run_id = ? AND artifact_id = ?",
				this::map, runId, artifactId).stream().findFirst();
	}

	@Override
	public List<AnalysisArtifact> findByRunId(String runId) {
		return jdbcTemplate.query(
				"SELECT * FROM ai_assistant_analysis_artifact WHERE run_id = ? ORDER BY created_at, artifact_id",
				this::map, runId);
	}

	private AnalysisArtifact map(ResultSet resultSet, int rowNum) throws SQLException {
		return new AnalysisArtifact(resultSet.getString("contract_version"), resultSet.getString("artifact_id"),
				resultSet.getString("run_id"), resultSet.getString("step_id"),
				ArtifactKind.valueOf(resultSet.getString("artifact_kind")),
				read(resultSet.getString("payload_json"), MAP_TYPE),
				read(resultSet.getString("citation_keys_json"), LIST_TYPE), resultSet.getString("artifact_digest"),
				resultSet.getTimestamp("created_at").toInstant());
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("ARTIFACT_SERIALIZATION_FAILED", ex);
		}
	}

	private <T> T read(String value, TypeReference<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("ARTIFACT_DESERIALIZATION_FAILED", ex);
		}
	}

}
