package com.deepdimen.matrix.assistant.workflow;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStep;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AssistantWorkflowExecutor.class)
public class UnavailableAssistantWorkflowExecutor implements AssistantWorkflowExecutor {

	@Override
	public WorkflowExecutionResult execute(IdentityEnvelope identity, String runId, ExecutionStep step,
			List<AnalysisArtifact> dependencyArtifacts) {
		throw new IllegalStateException("DIFY_WORKFLOW_EXECUTOR_UNAVAILABLE");
	}

}
