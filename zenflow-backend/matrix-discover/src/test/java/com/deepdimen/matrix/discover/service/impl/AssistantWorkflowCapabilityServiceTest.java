package com.deepdimen.matrix.discover.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.SideEffectClass;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityReleaseEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.mapper.AiAssistantWorkflowCapabilityMapper;
import com.deepdimen.matrix.discover.mapper.AiAssistantWorkflowCapabilityReleaseMapper;
import com.deepdimen.matrix.discover.service.AssistantWorkflowCapabilityService.WorkflowCapabilityDraftRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssistantWorkflowCapabilityServiceTest {

	private static final IdentityEnvelope IDENTITY = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(),
			List.of(), "capability-test", "ASSISTANT_CONTEXT");

	@BeforeAll
	static void initializeTableInfo() {
		initialize(AiAssistantWorkflowCapabilityEntity.class, "workflow-capability-test");
		initialize(AiAssistantWorkflowCapabilityReleaseEntity.class, "workflow-release-test");
	}

	@Test
	void publishesSanitizedImmutableOperationGuideAndExactDefinitionDigest() throws Exception {
		Fixtures fixtures = fixtures("""
				app:
				  mode: workflow
				workflow:
				  graph:
				    nodes:
				      - data:
				          type: start
				      - data:
				          type: end
				""");
		when(fixtures.difyAppApi.getApp("app-1")).thenReturn(fixtures.objectMapper.readTree("{\"mode\":\"workflow\"}"));
		when(fixtures.difyAppApi.publishWorkflow("app-1"))
			.thenReturn(fixtures.objectMapper.readTree("{\"id\":\"workflow-release-9\"}"));
		when(fixtures.difyAppApi.exportApp("app-1"))
			.thenReturn(fixtures.objectMapper.createObjectNode().put("data", fixtures.definition));

		AiAssistantWorkflowCapabilityReleaseEntity release = fixtures.service.publish(IDENTITY, 101L);

		assertEquals("workflow-release-9", release.getPublishedWorkflowId());
		assertEquals("water-quality", release.getCapabilityCode());
		assertFalse(release.getOperationGuideHtml().contains("script"));
		assertTrue(release.getOperationRulesJson().contains("查询水质"));
		assertEquals(64, release.getWorkflowDefinitionDigest().length());
		assertEquals(64, release.getOperationGuideHtmlDigest().length());
		assertEquals(64, release.getRoutingProfileDigest().length());
		assertEquals(64, release.getReleaseDigest().length());
		assertEquals("ACTIVE", release.getReleaseStatus());
		assertEquals(1, release.getPolicyAuditPassed());

		String frozenGuide = release.getOperationGuideText();
		fixtures.agent.setOperationGuide("<p>后来修改</p>");
		assertEquals(frozenGuide, release.getOperationGuideText());
		verify(fixtures.releaseMapper).insert(release);
	}

	@Test
	void rejectsDifyKnowledgeRetrievalBeforeCreatingARelease() throws Exception {
		Fixtures fixtures = fixtures("""
				workflow:
				  graph:
				    nodes:
				      - data:
				          type: knowledge-retrieval
				""");
		when(fixtures.difyAppApi.getApp("app-1")).thenReturn(fixtures.objectMapper.readTree("{\"mode\":\"workflow\"}"));
		when(fixtures.difyAppApi.publishWorkflow("app-1"))
			.thenReturn(fixtures.objectMapper.readTree("{\"id\":\"workflow-release-9\"}"));
		when(fixtures.difyAppApi.exportApp("app-1"))
			.thenReturn(fixtures.objectMapper.createObjectNode().put("data", fixtures.definition));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> fixtures.service.publish(IDENTITY, 101L));

		assertTrue(error.getMessage().contains("POLICY_AUDIT_FAILED"));
		verify(fixtures.difyAppApi, never()).publishWorkflow("app-1");
		verify(fixtures.releaseMapper, never()).insert(any(AiAssistantWorkflowCapabilityReleaseEntity.class));
	}

	@Test
	void publishesExplicitlyApprovedCodeAndReadOnlyToolNodes() throws Exception {
		Fixtures fixtures = fixtures("""
				workflow:
				  graph:
				    nodes:
				      - data:
				          variables:
				            - type: string
				          type: code
				      - data:
				          provider_id: my-mcp
				          tool_parameters:
				            query:
				              type: mixed
				          tool_name: Doris SQL查询
				          type: tool
				      - data:
				          type: end
				""");
		fixtures.draft.setAllowCodeNode(1);
		fixtures.draft.setToolAllowlistJson("[\"my-mcp/doris sql查询\"]");
		when(fixtures.difyAppApi.getApp("app-1")).thenReturn(fixtures.objectMapper.readTree("{\"mode\":\"workflow\"}"));
		when(fixtures.difyAppApi.publishWorkflow("app-1"))
			.thenReturn(fixtures.objectMapper.readTree("{\"id\":\"workflow-release-10\"}"));
		when(fixtures.difyAppApi.exportApp("app-1"))
			.thenReturn(fixtures.objectMapper.createObjectNode().put("data", fixtures.definition));

		AiAssistantWorkflowCapabilityReleaseEntity release = fixtures.service.publish(IDENTITY, 101L);

		assertEquals(1, release.getAllowCodeNode());
		assertEquals("[\"my-mcp/doris sql查询\"]", release.getToolAllowlistJson());
		assertTrue(release.getPolicyAuditJson().contains("\"codeNodePresent\":true"));
		assertTrue(release.getPolicyAuditJson().contains("my-mcp/doris sql查询"));
	}

	@Test
	void rejectsCodeNodeWithoutExplicitApprovalBeforePublishingDify() throws Exception {
		Fixtures fixtures = fixtures("""
				workflow:
				  graph:
				    nodes:
				      - data:
				          type: code
				""");
		when(fixtures.difyAppApi.getApp("app-1")).thenReturn(fixtures.objectMapper.readTree("{\"mode\":\"workflow\"}"));
		when(fixtures.difyAppApi.exportApp("app-1"))
			.thenReturn(fixtures.objectMapper.createObjectNode().put("data", fixtures.definition));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> fixtures.service.publish(IDENTITY, 101L));

		assertTrue(error.getMessage().contains("CODE_NODE_NOT_APPROVED"));
		verify(fixtures.difyAppApi, never()).publishWorkflow("app-1");
	}

	@Test
	void rejectsToolNodeMissingFromExactAllowlist() throws Exception {
		Fixtures fixtures = fixtures("""
				workflow:
				  graph:
				    nodes:
				      - data:
				          provider_id: my-mcp
				          tool_name: Doris SQL查询
				          type: tool
				""");
		when(fixtures.difyAppApi.getApp("app-1")).thenReturn(fixtures.objectMapper.readTree("{\"mode\":\"workflow\"}"));
		when(fixtures.difyAppApi.exportApp("app-1"))
			.thenReturn(fixtures.objectMapper.createObjectNode().put("data", fixtures.definition));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> fixtures.service.publish(IDENTITY, 101L));

		assertTrue(error.getMessage().contains("TOOL_NOT_APPROVED:my-mcp/doris sql查询"));
		verify(fixtures.difyAppApi, never()).publishWorkflow("app-1");
	}

	@Test
	void inspectionReturnsApprovalsNeededWithoutPublishing() throws Exception {
		Fixtures fixtures = fixtures("""
				workflow:
				  graph:
				    nodes:
				      - data:
				          type: code
				      - data:
				          provider_id: my-mcp
				          tool_name: Doris SQL查询
				          type: tool
				""");
		when(fixtures.difyAppApi.getApp("app-1")).thenReturn(fixtures.objectMapper.readTree("{\"mode\":\"workflow\"}"));
		when(fixtures.difyAppApi.exportApp("app-1"))
			.thenReturn(fixtures.objectMapper.createObjectNode().put("data", fixtures.definition));

		var inspection = fixtures.service.inspect(IDENTITY, 88L);

		assertTrue(inspection.codeNodePresent());
		assertEquals(List.of("my-mcp/doris sql查询"), inspection.toolIdentities());
		assertEquals(64, inspection.definitionDigest().length());
		verify(fixtures.difyAppApi, never()).publishWorkflow("app-1");
	}

	@Test
	void draftOnlyAcceptsDifyWorkflowAndMarksChangedPublishedSourceStale() {
		Fixtures fixtures = fixtures("unused");
		fixtures.draft.setActiveSourceDigest("old-source-digest");
		when(fixtures.capabilityMapper.selectOne(any())).thenReturn(fixtures.draft);
		WorkflowCapabilityDraftRequest request = new WorkflowCapabilityDraftRequest("water-quality", List.of("查询全流程水质"),
				"{\"type\":\"object\"}", "{\"type\":\"object\"}", List.of("KNOWLEDGE"), DataClassification.INTERNAL,
				SideEffectClass.READ_ONLY, List.of(), false, List.of(), 30000);

		AiAssistantWorkflowCapabilityEntity saved = fixtures.service.saveDraft(IDENTITY, 88L, request);

		assertEquals("STALE", saved.getStatus());
		assertEquals("[\"KNOWLEDGE\"]", saved.getRequiredProvidersJson());
		verify(fixtures.capabilityMapper).updateById(saved);
	}

	@Test
	void rejectsCrossTenantAgentWithoutLeakingIt() {
		Fixtures fixtures = fixtures("unused");
		fixtures.agent.setTenantId("other");
		WorkflowCapabilityDraftRequest request = new WorkflowCapabilityDraftRequest("water-quality", List.of("查询全流程水质"),
				"{\"type\":\"object\"}", "{\"type\":\"object\"}", List.of(), DataClassification.INTERNAL,
				SideEffectClass.READ_ONLY, List.of(), false, List.of(), 30000);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> fixtures.service.saveDraft(IDENTITY, 88L, request));

		assertEquals("WORKFLOW_CAPABILITY_NOT_FOUND", error.getMessage());
	}

	private Fixtures fixtures(String definition) {
		AiAssistantWorkflowCapabilityMapper capabilityMapper = mock(AiAssistantWorkflowCapabilityMapper.class);
		AiAssistantWorkflowCapabilityReleaseMapper releaseMapper = mock(
				AiAssistantWorkflowCapabilityReleaseMapper.class);
		AiAgentMapper agentMapper = mock(AiAgentMapper.class);
		DifyAppApi difyAppApi = mock(DifyAppApi.class);
		ObjectMapper objectMapper = new ObjectMapper();
		AiAgentEntity agent = new AiAgentEntity();
		agent.setId(88L);
		agent.setTenantId("11");
		agent.setName("水质分析工作流");
		agent.setDescription("水质全流程分析");
		agent.setOperationGuide("<p>查询水质并比较趋势</p><script>alert(1)</script>");
		agent.setAgentType("dify");
		agent.setDifyMode("workflow");
		agent.setDifyAppId("app-1");
		when(agentMapper.selectById(88L)).thenReturn(agent);
		AiAssistantWorkflowCapabilityEntity draft = new AiAssistantWorkflowCapabilityEntity();
		draft.setId(101L);
		draft.setTenantId("11");
		draft.setAgentId(88L);
		draft.setCapabilityCode("water-quality");
		draft.setIntentExamplesJson("[\"查询全流程水质\"]");
		draft.setInputSchemaJson("{\"type\":\"object\"}");
		draft.setOutputSchemaJson("{\"type\":\"object\"}");
		draft.setRequiredProvidersJson("[\"KNOWLEDGE\"]");
		draft.setDataClassification("INTERNAL");
		draft.setSideEffectClass("READ_ONLY");
		draft.setEgressAllowlistJson("[]");
		draft.setAllowCodeNode(0);
		draft.setToolAllowlistJson("[]");
		draft.setTimeoutMs(30000);
		when(capabilityMapper.selectOne(any())).thenReturn(draft);
		when(releaseMapper.selectList(any())).thenReturn(List.of());
		AssistantWorkflowCapabilityServiceImpl service = new AssistantWorkflowCapabilityServiceImpl(capabilityMapper,
				releaseMapper, agentMapper, difyAppApi, objectMapper);
		return new Fixtures(service, capabilityMapper, releaseMapper, difyAppApi, objectMapper, agent, draft,
				definition);
	}

	private static void initialize(Class<?> type, String resource) {
		if (TableInfoHelper.getTableInfo(type) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), resource), type);
		}
	}

	private record Fixtures(AssistantWorkflowCapabilityServiceImpl service,
			AiAssistantWorkflowCapabilityMapper capabilityMapper,
			AiAssistantWorkflowCapabilityReleaseMapper releaseMapper, DifyAppApi difyAppApi, ObjectMapper objectMapper,
			AiAgentEntity agent, AiAssistantWorkflowCapabilityEntity draft, String definition) {
	}

}
