package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InformationSkillPolicyStateTest {

	@Test
	void clientCannotActivateOrResetManagedState() throws Exception {
		var input = new ObjectMapper().readValue(
				"{\"id\":7,\"skillManagementMode\":\"MANAGED\",\"skillPolicyVersion\":99}", InformationPolicy.class);
		assertEquals("LEGACY", input.getSkillManagementMode());
		assertEquals(0, input.getSkillPolicyVersion());
		var existing = new InformationPolicy();
		existing.setId(7L);
		existing.setSkillManagementMode("MANAGED");
		existing.setSkillPolicyVersion(4);
		InformationSkillPolicyState.prepareSave(input, existing);
		assertEquals("MANAGED", input.getSkillManagementMode());
		assertEquals(5, input.getSkillPolicyVersion());
	}

	@Test
	void newPolicyRemainsLegacyAndStaleOrUnknownStateCannotBeSaved() {
		var input = new InformationPolicy();
		input.setSkillManagementMode("MANAGED");
		InformationSkillPolicyState.prepareSave(input, null);
		assertEquals("LEGACY", input.getSkillManagementMode());
		input.setId(7L);
		assertThrows(IllegalArgumentException.class, () -> InformationSkillPolicyState.prepareSave(input, null));
		var existing = new InformationPolicy();
		existing.setId(7L);
		existing.setSkillManagementMode("invalid");
		assertThrows(IllegalArgumentException.class, () -> InformationSkillPolicyState.prepareSave(input, existing));
	}

}
