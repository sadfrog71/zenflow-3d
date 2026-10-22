package com.deepdimen.matrix.skill.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

import com.deepdimen.matrix.skill.api.SkillContract.ExecutorRef;
import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.deepdimen.matrix.skill.api.SkillContract.ResourceRef;
import com.deepdimen.matrix.skill.api.SkillContract.RuntimeKind;

/**
 * Pins configuration references; retrieved source content retains its own evidence
 * versions.
 */
public final class KnowledgeSkillRelease {

	private KnowledgeSkillRelease() {
	}

	public record Pinned(String agentId, String modelId, ResourceRef agent, ResourceRef model,
			List<ResourceRef> datasets, String digest) {
		public Pinned {
			datasets = List.copyOf(datasets);
		}
	}

	public static Pinned from(ResolvedSkillSnapshot snapshot) {
		if (snapshot == null || snapshot.runtimeKind() != RuntimeKind.PROMPT || !snapshot.allowedParameters().isEmpty())
			throw invalid();
		return from(snapshot.executorRef(), snapshot.resourceRefs());
	}

	public static Pinned from(ExecutorRef executor, List<ResourceRef> resources) {
		if (executor == null || !"PROMPT".equals(executor.executorKey()) || !"KNOWLEDGE".equals(executor.provider())
				|| !positiveId(executor.resourceId()) || resources == null || resources.size() < 3
				|| resources.size() > 22)
			throw invalid();
		ResourceRef agent = null, model = null;
		var datasets = new ArrayList<ResourceRef>();
		var seen = new HashSet<String>();
		for (ResourceRef ref : resources) {
			if (ref == null || !seen.add(ref.resourceId()) || !ref.digest().matches("[a-f0-9]{64}")
					|| !ref.digest().equals(ref.version()))
				throw invalid();
			String[] parts = ref.resourceId().split(":", -1);
			if (parts.length != 2 || !positiveId(parts[1]))
				throw invalid();
			switch (parts[0]) {
				case "agent" -> {
					if (agent != null || "2026".equals(parts[1]))
						throw invalid();
					agent = ref;
				}
				case "model" -> {
					if (model != null || !parts[1].equals(executor.resourceId()))
						throw invalid();
					model = ref;
				}
				case "dataset" -> datasets.add(ref);
				default -> throw invalid();
			}
		}
		if (agent == null || model == null || datasets.isEmpty() || datasets.size() > 20)
			throw invalid();
		String digest = configurationDigest(resources);
		if (!digest.equals(executor.versionDigest()))
			throw invalid();
		datasets.sort(Comparator.comparing(ResourceRef::resourceId));
		return new Pinned(agent.resourceId().substring(6), executor.resourceId(), agent, model, datasets, digest);
	}

	public static String configurationDigest(List<ResourceRef> resources) {
		try {
			StringBuilder material = new StringBuilder("knowledge-config-v1\n");
			resources.stream()
				.sorted(Comparator.comparing(ResourceRef::resourceId))
				.forEach(ref -> material.append(ref.resourceId())
					.append('\n')
					.append(ref.version())
					.append('\n')
					.append(ref.digest())
					.append('\n'));
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256")
					.digest(material.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static boolean positiveId(String value) {
		if (value == null || !value.matches("[1-9][0-9]{0,18}"))
			return false;
		try {
			return Long.parseLong(value) > 0;
		}
		catch (NumberFormatException failure) {
			return false;
		}
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("KNOWLEDGE_RELEASE_CONFIGURATION_REQUIRED");
	}

}
