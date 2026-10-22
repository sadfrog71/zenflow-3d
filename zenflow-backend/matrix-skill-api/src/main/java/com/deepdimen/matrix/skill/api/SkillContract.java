package com.deepdimen.matrix.skill.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Internal managed-Skill vocabulary. Identity must be established by the trusted service
 * boundary.
 */
public final class SkillContract {

	public static final String VERSION = "matrix.skill/managed-v1";

	private SkillContract() {
	}

	public enum ManagementMode {

		LEGACY, MANAGED

	}

	public enum RuntimeKind {

		PROMPT, NATIVE_CAPABILITY, WORKFLOW, SCRIPT

	}

	public enum RunStatus {

		QUEUED, RUNNING, SUCCEEDED, DEGRADED, FAILED, CANCELLED, UNKNOWN

	}

	public enum CompatibilityStatus {

		SUPPORTED, UNSUPPORTED, UNAVAILABLE, UNKNOWN

	}

	public enum ErrorCode {

		SKILL_NOT_FOUND, RELEASE_NOT_AVAILABLE, RELEASE_REVOKED, BINDING_NOT_ACTIVE, BINDING_REVISION_CONFLICT,
		CONSUMER_NOT_AUTHORIZED, FROZEN_CONSUMER, INPUT_INVALID, OUTPUT_INVALID, IDEMPOTENCY_CONFLICT,
		AGENT_SKILL_UNSUPPORTED, AGENT_ADAPTER_UNAVAILABLE, ADAPTER_AMBIGUOUS, EXECUTOR_VERSION_DRIFT,
		EXECUTOR_UNAVAILABLE, RESULT_UNKNOWN

	}

	/**
	 * Actor and data subject are distinct for authorized background jobs. Never trust
	 * client supplied identity.
	 */
	public record Identity(String tenantId, String actorId, String subjectId, String correlationId, String actorType, String subjectType) {
		public Identity {
			required(tenantId);
			required(actorId);
			required(subjectId);
			required(correlationId);
			subjectType=subjectType==null?"ADMIN_USER":subjectType;
			actorType=actorType==null?"ADMIN_USER":actorType;
			if (!java.util.Set.of("ADMIN_USER","APP_USER").contains(subjectType) || !java.util.Set.of("ADMIN_USER","APP_USER").contains(actorType)) throw new IllegalArgumentException("Invalid skill subject");
		}
		public Identity(String tenantId,String actorId,String subjectId,String correlationId) {
			this(tenantId,actorId,subjectId,correlationId,"ADMIN_USER","ADMIN_USER");
		}
		public Identity(String tenantId,String actorId,String subjectId,String correlationId,String subjectType) {
			this(tenantId,actorId,subjectId,correlationId,subjectType,subjectType);
		}
	}

	public record Consumer(String type, String id, String slotCode, String bindingKey) {
		public Consumer {
			required(type);
			required(id);
			required(slotCode);
			required(bindingKey);
		}
	}

	/**
	 * Provider-specific configuration is a server-owned reference, not a caller URL or
	 * class name.
	 */
	public record ExecutorRef(String executorKey, String provider, String resourceId, String versionDigest) {
		public ExecutorRef {
			required(executorKey);
		}
	}

	public record ResourceRef(String resourceId, String version, String digest) {
		public ResourceRef {
			required(resourceId);
			required(version);
			required(digest);
		}
	}

	/**
	 * JSON values remain immutable text; resolution validates schemas and allowed
	 * parameters before use.
	 */
	public record ResolvedSkillSnapshot(String skillId, String releaseId, String releaseDigest,
			String bindingRevisionId, RuntimeKind runtimeKind, ExecutorRef executorRef, String inputSchemaJson,
			String outputSchemaJson, String instructionSnapshot, List<ResourceRef> resourceRefs, String limitsJson,
			List<String> allowedParameters) {
		public ResolvedSkillSnapshot {
			required(skillId);
			required(releaseId);
			required(releaseDigest);
			required(bindingRevisionId);
			Objects.requireNonNull(runtimeKind);
			Objects.requireNonNull(executorRef);
			resourceRefs = resourceRefs == null ? List.of() : List.copyOf(resourceRefs);
			allowedParameters = allowedParameters == null ? List.of() : List.copyOf(allowedParameters);
		}
	}

	public record ResolveRequest(Identity identity, Consumer consumer, String bindingId, String businessRunId,
			String requestedParametersJson) {
		public ResolveRequest {
			Objects.requireNonNull(identity);
			Objects.requireNonNull(consumer);
			required(businessRunId);
		}
	}

	/** Opaque token binds the resolved snapshot to identity, consumer and expiry. */
	public record Resolution(ResolvedSkillSnapshot snapshot, String resolvedSnapshotToken, Instant expiresAt) {
		public Resolution {
			Objects.requireNonNull(snapshot);
			required(resolvedSnapshotToken);
			Objects.requireNonNull(expiresAt);
		}
	}

	public record ExecuteRequest(Identity identity, Consumer consumer, String resolvedSnapshotToken, String inputJson,
			String authorizedInputRef, String idempotencyKey, String parentRunId) {
		public ExecuteRequest {
			Objects.requireNonNull(identity);
			Objects.requireNonNull(consumer);
			required(resolvedSnapshotToken);
			required(idempotencyKey);
			if ((inputJson == null) == (authorizedInputRef == null)) {
				throw new IllegalArgumentException("Exactly one input or authorized input reference is required");
			}
		}
	}

	public record ExecuteResult(String skillRunId, RunStatus status, String resultJson, String resultRef,
			List<String> artifactRefs, String actualReleaseId, String executorVersion, String usageJson,
			ErrorCode errorCode) {
		public ExecuteResult {
			required(skillRunId);
			Objects.requireNonNull(status);
			required(actualReleaseId);
			artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
		}
	}

	public record AgentCapabilities(CompatibilityStatus status, List<RuntimeKind> runtimeKinds, boolean instructions,
			boolean independentActions, boolean retrievalContext, boolean streaming, boolean cancellation,
			String inputSchemaJson, String outputSchemaJson, ErrorCode reasonCode) {
		public AgentCapabilities {
			Objects.requireNonNull(status);
			runtimeKinds = runtimeKinds == null ? List.of() : List.copyOf(runtimeKinds);
			if (status != CompatibilityStatus.SUPPORTED && !runtimeKinds.isEmpty()) {
				throw new IllegalArgumentException("Unavailable capabilities cannot advertise supported runtime kinds");
			}
		}
	}

	private static void required(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Required Skill identifier is missing");
	}

}
