package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.fasterxml.jackson.databind.JsonNode;

/** A reviewed Dify classic-chat release pins its app and active model configuration. */
public final class DifyMarketRelease {

	private DifyMarketRelease() {
	}

	public static void validateDeclaration(JsonNode executor, JsonNode resources) {
		if (executor == null || !executor.isObject() || executor.size() != 4
				|| !"PROMPT".equals(executor.path("executorKey").asText())
				|| !"DIFY".equals(executor.path("provider").asText()) || !identifier(executor.path("resourceId"))
				|| !digest(executor.path("versionDigest")) || resources == null || !resources.isArray()
				|| resources.size() != 1)
			throw invalid();
		var resource = resources.get(0);
		if (!resource.isObject() || resource.size() != 3 || !identifier(resource.path("resourceId"))
				|| !identifier(resource.path("version")) || !digest(resource.path("digest"))
				|| !executor.path("resourceId").equals(resource.path("resourceId"))
				|| !executor.path("versionDigest").equals(resource.path("digest")))
			throw invalid();
	}

	public static DifyChatConfigurationSnapshot configuration(ResolvedSkillSnapshot release) {
		if (release == null || release.runtimeKind() != com.deepdimen.matrix.skill.api.SkillContract.RuntimeKind.PROMPT
				|| !"PROMPT".equals(release.executorRef().executorKey())
				|| !"DIFY".equals(release.executorRef().provider()) || release.resourceRefs().size() != 1
				|| !release.allowedParameters().isEmpty())
			throw invalid();
		var resource = release.resourceRefs().get(0);
		if (!validId(resource.resourceId()) || !validId(resource.version()) || resource.digest() == null
				|| !resource.digest().matches("[a-f0-9]{64}")
				|| !resource.resourceId().equals(release.executorRef().resourceId())
				|| !resource.digest().equals(release.executorRef().versionDigest()))
			throw invalid();
		return new DifyChatConfigurationSnapshot(resource.resourceId(), resource.version(), resource.digest());
	}

	private static boolean identifier(JsonNode node) {
		return node.isTextual() && validId(node.textValue());
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
	}

	private static boolean digest(JsonNode node) {
		return node.isTextual() && node.textValue().matches("[a-f0-9]{64}");
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("DIFY_RELEASE_CONFIGURATION_REQUIRED");
	}

}
