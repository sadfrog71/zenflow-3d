package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.skill.api.SkillExecutionFailure;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZenflowCompanionFailuresTest {

	@Test
	void originalManagedWrapperPreservesSupplierAndRevocationCodes() {
		for (String code : List.of("MODEL_UPSTREAM_UNAVAILABLE", "PRIVATE_SOURCE_REVOKED", "RUN_CANCELLED")) {
			var nativeFailure = new AssistantModelException(code, "Safe failure");
			var result = new ExecuteResult("run", RunStatus.FAILED, null, null, List.of(), "release", null, null, null);
			assertSame(nativeFailure,
					ZenflowCompanionFailures.modelFailure(new SkillExecutionFailure(result, nativeFailure)));
		}
	}

	@Test
	void arbitraryWrappersDoNotBecomeTrustedModelFailures() {
		var failure = new AssistantModelException("MODEL_UPSTREAM_UNAVAILABLE", "Safe");
		assertNull(ZenflowCompanionFailures.modelFailure(new IllegalStateException("other", failure)));
		assertSame(failure, ZenflowCompanionFailures.modelFailure(failure));
	}

}
