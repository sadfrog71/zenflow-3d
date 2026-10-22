package com.deepdimen.matrix.discover.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class TenantAgentRouteControllerSecurityTest {

	@Test
	void allowsSkillViewerToReadUnifiedAssistantRouteShownOnSkillPage() throws Exception {
		Method method = TenantAgentRouteController.class.getDeclaredMethod("tenantDefaultConfig", String.class);
		PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

		assertTrue(authorization.value().contains("knowledge_skill_view"),
				"Skill 管理页面会读取统一对话智能体路由，只有 Skill 查看权限时也必须允许读取");
	}

}
