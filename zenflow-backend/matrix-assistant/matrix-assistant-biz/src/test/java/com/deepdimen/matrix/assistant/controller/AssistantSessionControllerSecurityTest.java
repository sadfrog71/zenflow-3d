package com.deepdimen.matrix.assistant.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;

class AssistantSessionControllerSecurityTest {

	@Test
	void ownExecutionRunsRequireAuthentication() throws Exception {
		Method method = AssistantSessionController.class.getDeclaredMethod("executionRuns");

		assertEquals("isAuthenticated()", method.getAnnotation(PreAuthorize.class).value());
	}

	@Test
	void tenantWideExecutionRunsRequireExplicitAdministrativePermission() throws Exception {
		Method method = AssistantSessionController.class.getDeclaredMethod("tenantExecutionRuns");

		assertEquals("isAuthenticated() and @pms.hasPermission('knowledge_agent_view')",
				method.getAnnotation(PreAuthorize.class).value());

		Method serviceMethod = AssistantSessionService.class.getDeclaredMethod("tenantExecutionRuns",
				IdentityEnvelope.class);
		assertEquals("isAuthenticated() and @pms.hasPermission('knowledge_agent_view')",
				serviceMethod.getAnnotation(PreAuthorize.class).value());
	}

}
