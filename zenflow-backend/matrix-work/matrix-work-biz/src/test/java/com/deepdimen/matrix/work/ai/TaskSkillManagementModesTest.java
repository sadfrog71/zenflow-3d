package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskSkillManagementModesTest {

	@Test
	void ordinaryJsonInputCannotSetModesButResponsesExposeTheirStoredState() throws Exception {
		var json = new ObjectMapper();
		var input = json.readValue(
				"{\"parseSkillManagementMode\":\"MANAGED\",\"dispatchSkillManagementMode\":\"MANAGED\",\"enabled\":true}",
				WorkTaskPolicyEntity.class);
		assertEquals("LEGACY", input.getParseSkillManagementMode());
		assertEquals("LEGACY", input.getDispatchSkillManagementMode());
		assertTrue(input.getEnabled());
		input.setParseSkillManagementMode("MANAGED");
		var response = json.readTree(json.writeValueAsString(input));
		assertEquals("MANAGED", response.path("parseSkillManagementMode").asText());
		assertEquals("LEGACY", response.path("dispatchSkillManagementMode").asText());
	}

	@Test
	void savesCannotMigrateOrRollBackEitherSlotAndNewPoliciesStartLegacy() {
		var stored = new WorkTaskPolicyEntity();
		stored.setId(7L);
		var input = new WorkTaskPolicyEntity();
		for (String parse : new String[] { "LEGACY", "MANAGED" })
			for (String dispatch : new String[] { "LEGACY", "MANAGED" }) {
				stored.setParseSkillManagementMode(parse);
				stored.setDispatchSkillManagementMode(dispatch);
				input.setParseSkillManagementMode("MANAGED".equals(parse) ? "LEGACY" : "MANAGED");
				input.setDispatchSkillManagementMode("MANAGED".equals(dispatch) ? "LEGACY" : "MANAGED");
				TaskSkillManagementModes.preserveForSave(input, stored);
				assertEquals(parse, input.getParseSkillManagementMode());
				assertEquals(dispatch, input.getDispatchSkillManagementMode());
			}
		stored.setId(null);
		TaskSkillManagementModes.preserveForSave(input, stored);
		assertEquals("LEGACY", input.getParseSkillManagementMode());
		assertEquals("LEGACY", input.getDispatchSkillManagementMode());
	}

	@Test
	void corruptStoredModesAreRejectedInsteadOfSilentlyChoosingLegacy() {
		var stored = new WorkTaskPolicyEntity();
		stored.setId(7L);
		for (String invalid : new String[] { null, "", "managed", "INVALID" }) {
			stored.setParseSkillManagementMode(invalid);
			assertThrows(IllegalArgumentException.class,
					() -> TaskSkillManagementModes.preserveForSave(new WorkTaskPolicyEntity(), stored));
			stored.setParseSkillManagementMode("LEGACY");
			stored.setDispatchSkillManagementMode(invalid);
			assertThrows(IllegalArgumentException.class,
					() -> TaskSkillManagementModes.preserveForSave(new WorkTaskPolicyEntity(), stored));
			stored.setDispatchSkillManagementMode("LEGACY");
		}
	}

}
