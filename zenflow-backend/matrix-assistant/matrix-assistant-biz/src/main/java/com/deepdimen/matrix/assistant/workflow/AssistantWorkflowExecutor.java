package com.deepdimen.matrix.assistant.workflow;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStep;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.UsageSource;
import java.util.List;
import java.util.Map;

/** Server-side executor for an exact, published Dify Workflow Capability release. */
public interface AssistantWorkflowExecutor {

	WorkflowExecutionResult execute(IdentityEnvelope identity, String runId, ExecutionStep step,
			List<AnalysisArtifact> dependencyArtifacts);

	/**
	 * Execution hook used by durable runtimes to persist external identifiers as soon as
	 * the remote system acknowledges the call, and to observe cancellation while it is
	 * still running. Existing in-process/test executors keep the four-argument contract.
	 */
	default WorkflowExecutionResult execute(IdentityEnvelope identity, String runId, ExecutionStep step,
			List<AnalysisArtifact> dependencyArtifacts, WorkflowExecutionControl control) {
		return execute(identity, runId, step, dependencyArtifacts);
	}

	interface WorkflowExecutionControl {

		void externalIdentifiersAvailable(String externalTaskId, String workflowRunId);

		boolean cancellationRequested();

	}

	record WorkflowExecutionResult(Map<String, Object> payload, List<String> citationKeys, String externalTaskId,
			String workflowRunId, UsageSource usageSource) {

		public WorkflowExecutionResult {
			payload = payload == null ? Map.of() : Map.copyOf(payload);
			citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys);
			usageSource = usageSource == null ? UsageSource.NOT_REPORTED : usageSource;
		}
	}

}
