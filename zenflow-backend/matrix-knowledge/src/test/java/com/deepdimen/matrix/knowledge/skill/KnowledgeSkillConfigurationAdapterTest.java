package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.skill.api.KnowledgeSkillRelease;
import com.deepdimen.matrix.skill.api.SkillContract.*;

class KnowledgeSkillConfigurationAdapterTest {

	@Test
	void returnsOnlyValidatedFixedResourceReferences() {
		var resources = mock(KnowledgeSkillResources.class);
		var digest = "a".repeat(64);
		var refs = List.of(new ResourceRef("agent:88", digest, digest), new ResourceRef("model:77", digest, digest),
				new ResourceRef("dataset:6", digest, digest));
		var fixed = KnowledgeSkillRelease.configurationDigest(refs);
		var consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "configuration-check");
		when(resources.captureConfigured(consumer))
			.thenReturn(new KnowledgeSkillResources.Capture(null, null, "77", refs, fixed));
		var config = new KnowledgeSkillConfigurationAdapter(resources).inspect(consumer);
		assertEquals(refs, config.resourceRefs());
		assertEquals("77", config.executorRef().resourceId());
		assertEquals(fixed, config.executorRef().versionDigest());
		assertEquals("KNOWLEDGE", config.executorRef().provider());
		assertThrows(UnsupportedOperationException.class, () -> config.resourceRefs().clear());
	}

}
