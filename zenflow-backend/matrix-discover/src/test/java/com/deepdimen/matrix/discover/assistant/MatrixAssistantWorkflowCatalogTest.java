package com.deepdimen.matrix.discover.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.PublishedWorkflowCapability;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;

class MatrixAssistantWorkflowCatalogTest {

	private static final IdentityEnvelope IDENTITY = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of("5"),
			List.of("3"), "catalog-test", "ASSISTANT_CONTEXT");

	@BeforeAll
	static void initializeTableInfo() {
		if (TableInfoHelper.getTableInfo(AiAgentEntity.class) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "workflow-catalog-test"),
					AiAgentEntity.class);
		}
	}

	@Test
	void exposesAccessiblePublishedMarketplaceWorkflowWithOperationGuide() {
		AiAgentMapper mapper = mock(AiAgentMapper.class);
		AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
		AiAgentEntity agent = workflowAgent();
		when(mapper.selectList(any())).thenReturn(List.of(agent));
		when(permissions.isAccessible(88L, IDENTITY)).thenReturn(true);
		MatrixAssistantWorkflowCatalog catalog = new MatrixAssistantWorkflowCatalog(mapper, permissions,
				new ObjectMapper());

		List<PublishedWorkflowCapability> result = catalog.listPublished(IDENTITY);

		assertEquals(1, result.size());
		assertTrue(result.get(0).authorized());
		assertEquals("market-agent-88", result.get(0).snapshot().capabilityId());
		assertEquals("水源水质监测智能体", result.get(0).snapshot().displayName());
		assertEquals("app-1", result.get(0).snapshot().publishedWorkflowId());
		assertTrue(result.get(0).snapshot().operationRules().contains("今天的水源水质如何？"));
		assertFalse(result.get(0).snapshot().toString().contains("server-secret"));
	}

	@Test
	void inaccessibleOrIncompleteMarketplaceAgentNeverEntersCatalog() {
		AiAgentMapper mapper = mock(AiAgentMapper.class);
		AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
		AiAgentEntity incomplete = workflowAgent();
		incomplete.setDifyApiKey(null);
		when(mapper.selectList(any())).thenReturn(List.of(incomplete));
		when(permissions.isAccessible(88L, IDENTITY)).thenReturn(true);
		MatrixAssistantWorkflowCatalog catalog = new MatrixAssistantWorkflowCatalog(mapper, permissions,
				new ObjectMapper());

		assertTrue(catalog.listPublished(IDENTITY).isEmpty());

		incomplete.setDifyApiKey("server-secret");
		when(permissions.isAccessible(88L, IDENTITY)).thenReturn(false);
		assertTrue(catalog.listPublished(IDENTITY).isEmpty());
	}

	@Test
	void exposesPublishedAdvancedChatAgentShownInMarketplace() {
		AiAgentMapper mapper = mock(AiAgentMapper.class);
		AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
		AiAgentEntity agent = workflowAgent();
		agent.setName("客服智能体");
		agent.setDifyMode("advanced-chat");
		agent.setOperationGuide("<p>按统一有效工单口径查询客服工单，支持水质投诉统计。</p>");
		when(mapper.selectList(any())).thenReturn(List.of(agent));
		when(permissions.isAccessible(88L, IDENTITY)).thenReturn(true);

		List<PublishedWorkflowCapability> result = new MatrixAssistantWorkflowCatalog(mapper, permissions,
				new ObjectMapper())
			.listPublished(IDENTITY);

		assertEquals(1, result.size());
		assertEquals("客服智能体", result.get(0).snapshot().displayName());
		assertTrue(result.get(0).snapshot().operationRules().get(0).contains("水质投诉"));
	}

	@Test
	void keepsNewestMarketplaceAgentWhenLegacyAndWorkflowShareTheSameName() {
		AiAgentMapper mapper = mock(AiAgentMapper.class);
		AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
		AiAgentEntity latest = workflowAgent();
		latest.setId(99L);
		latest.setDifyAppId("app-latest");
		latest.setCreateTime(LocalDateTime.of(2026, 9, 7, 15, 30));
		AiAgentEntity legacy = workflowAgent();
		legacy.setDifyMode("advanced-chat");
		legacy.setCreateTime(LocalDateTime.of(2026, 7, 7, 8, 58));
		when(mapper.selectList(any())).thenReturn(List.of(latest, legacy));
		when(permissions.isAccessible(99L, IDENTITY)).thenReturn(true);
		when(permissions.isAccessible(88L, IDENTITY)).thenReturn(true);

		List<PublishedWorkflowCapability> result = new MatrixAssistantWorkflowCatalog(mapper, permissions,
				new ObjectMapper())
			.listPublished(IDENTITY);

		assertEquals(1, result.size());
		assertEquals("market-agent-99", result.get(0).snapshot().capabilityId());
		assertEquals("app-latest", result.get(0).snapshot().publishedWorkflowId());
	}

	@Test
	void candidateRoutingPreservesSameNameAgentsAndFreezesAliases() throws Exception {
		AiAgentMapper mapper = mock(AiAgentMapper.class);
		AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
		AiAgentEntity first = workflowAgent();
		first.setTags(List.of("原水助手", "智能体"));
		AiAgentEntity second = workflowAgent();
		second.setId(99L);
		when(mapper.selectList(any())).thenReturn(List.of(first, second));
		when(permissions.isAccessible(88L, IDENTITY)).thenReturn(true);
		when(permissions.isAccessible(99L, IDENTITY)).thenReturn(true);
		MatrixAssistantWorkflowCatalog catalog = new MatrixAssistantWorkflowCatalog(mapper, permissions,
				new ObjectMapper());
		var field = MatrixAssistantWorkflowCatalog.class.getDeclaredField("candidateRetrievalEnabled");
		field.setAccessible(true);
		field.setBoolean(catalog, true);
		var result = catalog.listPublished(IDENTITY);
		assertEquals(2, result.size());
		assertEquals(List.of("原水助手", "智能体"), result.get(0).snapshot().aliases());
		when(permissions.isAccessible(99L, IDENTITY)).thenReturn(false);
		assertEquals(1, catalog.listPublished(IDENTITY).size());
	}

	private AiAgentEntity workflowAgent() {
		AiAgentEntity agent = new AiAgentEntity();
		agent.setId(88L);
		agent.setTenantId("11");
		agent.setName("水源水质监测智能体");
		agent.setDescription("查询当前水源水质");
		agent.setUseScenes("[\"查询水源水质\"]");
		agent.setOperationGuide("<p>功能定位</p><p>今天的水源水质如何？</p>");
		agent.setAgentType("dify");
		agent.setDifyMode("workflow");
		agent.setDifyAppId("app-1");
		agent.setDifyApiKey("server-secret");
		agent.setStatus("published");
		agent.setDelFlag("0");
		agent.setCreateTime(LocalDateTime.of(2026, 9, 7, 11, 0));
		agent.setUpdateTime(LocalDateTime.of(2026, 9, 7, 12, 0));
		return agent;
	}

}
