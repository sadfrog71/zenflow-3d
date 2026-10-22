package com.deepdimen.matrix.skill.api;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;

class KnowledgeSkillReleaseTest {

	ResourceRef ref(String id, String digest) {
		return new ResourceRef(id, digest, digest);
	}

	List<ResourceRef> refs() {
		return List.of(ref("agent:88", "a".repeat(64)), ref("model:77", "b".repeat(64)),
				ref("dataset:31", "c".repeat(64)));
	}

	ExecutorRef executor(List<ResourceRef> refs) {
		return new ExecutorRef("PROMPT", "KNOWLEDGE", "77", KnowledgeSkillRelease.configurationDigest(refs));
	}

	@Test
	void pinsAllThreeKindsWithoutDependingOnResourceOrder() {
		var refs = refs();
		var fixed = KnowledgeSkillRelease.from(executor(refs), refs);
		assertEquals("88", fixed.agentId());
		assertEquals("77", fixed.modelId());
		assertEquals("dataset:31", fixed.datasets().get(0).resourceId());
		assertEquals(fixed, KnowledgeSkillRelease.from(executor(refs), List.of(refs.get(2), refs.get(0), refs.get(1))));
	}

	@Test
	void refusesMissingDuplicatedForeignOrUnversionedReferences() {
		var good = refs();
		for (var bad : List.of(good.subList(0, 2), List.of(good.get(0), good.get(1), good.get(1)),
				List.of(ref("agent:2026", "a".repeat(64)), good.get(1), good.get(2)),
				List.of(good.get(0), ref("model:78", "b".repeat(64)), good.get(2)),
				List.of(good.get(0), good.get(1), ref("url:31", "c".repeat(64))),
				List.of(good.get(0), good.get(1), new ResourceRef("dataset:31", "latest", "c".repeat(64)))))
			assertThrows(IllegalArgumentException.class, () -> KnowledgeSkillRelease.from(executor(bad), bad));
	}

	@Test
	void changingOneResourceCannotReuseTheOldExecutorDigest() {
		var old = refs();
		var changed = List.of(old.get(0), old.get(1), ref("dataset:31", "d".repeat(64)));
		assertThrows(IllegalArgumentException.class, () -> KnowledgeSkillRelease.from(executor(old), changed));
		assertNotEquals(executor(old).versionDigest(), executor(changed).versionDigest());
	}

}
