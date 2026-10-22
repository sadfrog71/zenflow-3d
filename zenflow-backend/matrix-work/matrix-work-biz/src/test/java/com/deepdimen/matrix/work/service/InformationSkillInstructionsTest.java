package com.deepdimen.matrix.work.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InformationSkillInstructionsTest {

	@Test
	void originalInstructionBytesRemainStableForBothReleases() {
		assertEquals("8084850be7544370baaaec5db6cc30a98fba781414a86b4d2255527d26951201",
				InformationSkillInstructions.source("ANALYZE").sha256());
		assertEquals("5d57f965cc076099a5421c2d90316878f95bde0900d0526286d399a231c5cfed",
				InformationSkillInstructions.source("REVIEW").sha256());
		assertNotEquals(InformationSkillInstructions.source("ANALYZE").sha256(),
				InformationSkillInstructions.source("REVIEW").sha256());
	}

	@Test
	void unknownSlotsCannotSilentlyUseAnotherInstruction() {
		assertThrows(IllegalArgumentException.class, () -> InformationSkillInstructions.source("TASK_PARSE"));
	}

}
