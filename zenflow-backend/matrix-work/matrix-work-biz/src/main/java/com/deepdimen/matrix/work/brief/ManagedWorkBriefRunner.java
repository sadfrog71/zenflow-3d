package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Business adapter: retains deterministic metrics and downstream review, never falls back
 * to old bindings.
 */
@Component
@RequiredArgsConstructor
public class ManagedWorkBriefRunner {

	private final ObjectProvider<SkillBindingResolver> resolvers;

	private final ObjectProvider<SkillExecutionService> executors;

	private final DeterministicWorkSummarySkill deterministic;

	private final ObjectMapper json;

	private final WorkBriefReviewer reviewer;

	public WorkBriefSkillRunner.Execution execute(WorkBriefIdentity identity, WorkBriefPolicyEntity policy,
			WorkBriefSnapshot snapshot, String businessRunId) {
		var resolver = resolvers.getIfAvailable();
		var executor = executors.getIfAvailable();
		if (resolver == null || executor == null || businessRunId == null)
			throw new IllegalStateException("EXECUTOR_UNAVAILABLE");
		var principal = new Identity(identity.tenantId().toString(), identity.ownerUserId().toString(),
				identity.ownerUserId().toString(), "work-brief:" + businessRunId);
		var consumer = new Consumer("WORK_BRIEF", policy.getId().toString(), "WORK_BRIEF", "default");
		var resolved = resolver.resolve(new ResolveRequest(principal, consumer, null, businessRunId, "{}"));
		ExecuteResult result = null;
		try {
			var request = new ExecuteRequest(principal, consumer, resolved.resolvedSnapshotToken(),
					json.writeValueAsString(snapshot), null, "work-brief:" + businessRunId, null);
			try {
				result = executor.execute(request);
			}
			catch (SkillExecutionFailure failure) {
				result = failure.result();
				if (canDegrade(result) && executor.fallbackAllowed(request, "DETERMINISTIC")) {
					return degraded(snapshot, resolved, result);
				}
				throw failure;
			}
			if (canDegrade(result) && executor.fallbackAllowed(request, "DETERMINISTIC")) {
				return degraded(snapshot, resolved, result);
			}
			if (result.status() != RunStatus.SUCCEEDED)
				throw new IllegalStateException("SKILL_RUN_" + result.status());
			WorkBriefDraft draft;
			try {
				draft = json.readerFor(WorkBriefDraft.class)
					.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
					.<WorkBriefDraft>readValue(result.resultJson());
				if (draft == null)
					throw new IllegalArgumentException("OUTPUT_INVALID");
			}
			catch (java.io.IOException | IllegalArgumentException invalidOutput) {
				if (executor.fallbackAllowed(request, "DETERMINISTIC"))
					return degraded(snapshot, resolved, result);
				throw new IllegalArgumentException("OUTPUT_INVALID", invalidOutput);
			}
			var metrics = deterministic.execute(snapshot).metrics();
			var authoritative = new WorkBriefDraft(draft.title(), draft.executiveSummary(), draft.continuityNote(),
					metrics, draft.focusItems(), draft.scheduleItems(), draft.informationItems(), draft.messageItems(),
					draft.emailItems(), draft.riskItems(), draft.citations());
			boolean degraded = false;
			if (!reviewer.review(snapshot, authoritative).approved()
					&& executor.fallbackAllowed(request, "DETERMINISTIC")) {
				authoritative = deterministic.execute(snapshot);
				degraded = true;
			}
			return new WorkBriefSkillRunner.Execution(authoritative, null, degraded, resolved.snapshot().skillId(),
					result.actualReleaseId(), result.skillRunId());
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("INPUT_INVALID", ex);
		}
		catch (RuntimeException ex) {
			if (result != null) {
				throw new ManagedWorkBriefFailure(resolved.snapshot().skillId(), result.actualReleaseId(),
						result.skillRunId(), ex);
			}
			throw ex;
		}
	}

	private boolean canDegrade(ExecuteResult result) {
		// An uncertain model result may use local rules, but is never retried or
		// relabelled as a successful Skill invocation.
		return result.status() == RunStatus.UNKNOWN
				|| (result.status() == RunStatus.FAILED && result.errorCode() == ErrorCode.OUTPUT_INVALID);
	}

	private WorkBriefSkillRunner.Execution degraded(WorkBriefSnapshot snapshot, Resolution resolution,
			ExecuteResult result) {
		return new WorkBriefSkillRunner.Execution(deterministic.execute(snapshot), null, true,
				resolution.snapshot().skillId(), result.actualReleaseId(), result.skillRunId());
	}

}
