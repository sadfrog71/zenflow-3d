package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import java.util.List;
import java.util.Optional;

/** Durable immutable evidence artifacts produced by Provider and Workflow steps. */
public interface AssistantAnalysisArtifactRepository {

	AnalysisArtifact saveIfAbsent(AnalysisArtifact artifact);

	Optional<AnalysisArtifact> findById(String runId, String artifactId);

	List<AnalysisArtifact> findByRunId(String runId);

}
