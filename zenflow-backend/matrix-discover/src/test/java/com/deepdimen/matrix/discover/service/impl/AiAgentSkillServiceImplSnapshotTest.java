package com.deepdimen.matrix.discover.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.dify.DifyAgentBridge;
import com.deepdimen.matrix.discover.dto.AiAgentVersionGrayDTO;
import com.deepdimen.matrix.discover.entity.AiAgentSkillReleaseEntity;
import com.deepdimen.matrix.discover.entity.AiAgentSkillReleaseHistoryEntity;
import com.deepdimen.matrix.discover.entity.AiAgentSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentSkillReleaseHistoryMapper;
import com.deepdimen.matrix.discover.mapper.AiAgentSkillReleaseMapper;
import com.deepdimen.matrix.discover.mapper.AiAgentSkillMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.discover.mapper.AiTenantAgentRouteMapper;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiSkillService;
import com.deepdimen.matrix.discover.skill.PromptSkillReleaseSnapshot;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;
import com.deepdimen.matrix.discover.vo.AiSkillVO;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class AiAgentSkillServiceImplSnapshotTest {

	private static final long SYSTEM_SKILL_ID = 101L;

	@BeforeAll
	static void initializeTableInfo() {
		if (TableInfoHelper.getTableInfo(AiSkillPackageEntity.class) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "skill-package-test"),
					AiSkillPackageEntity.class);
		}
		if (TableInfoHelper.getTableInfo(AiAgentSkillReleaseEntity.class) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "skill-release-test"),
					AiAgentSkillReleaseEntity.class);
		}
		if (TableInfoHelper.getTableInfo(AiAgentSkillReleaseHistoryEntity.class) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "skill-history-test"),
					AiAgentSkillReleaseHistoryEntity.class);
		}
		if (TableInfoHelper.getTableInfo(AiAgentSkillEntity.class) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "agent-skill-test"),
					AiAgentSkillEntity.class);
		}
	}

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void freezesSystemOwnedPackageForTenantOwnedRelease() {
		AiSkillService skillService = mock(AiSkillService.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		AiSkillEntity systemSkill = new AiSkillEntity();
		systemSkill.setId(SYSTEM_SKILL_ID);
		systemSkill.setTenantId("0");
		AiSkillPackageEntity approvedPackage = new AiSkillPackageEntity();
		approvedPackage.setTenantId("0");
		approvedPackage.setSkillId(SYSTEM_SKILL_ID);
		approvedPackage.setPackageStatus("APPROVED");
		approvedPackage.setPackageSha256("package-sha-256");
		approvedPackage.setManifestJson("""
				{"runtimeKind":"PROMPT_INJECTION","config":{"promptTemplate":"System-owned prompt"},
				 "declared":{"requiredProviders":["KNOWLEDGE"]}}
				""");
		when(skillService.getById(SYSTEM_SKILL_ID)).thenReturn(systemSkill);
		when(packageMapper.selectOne(any())).thenReturn(approvedPackage);

		AiAgentSkillReleaseEntity release = new AiAgentSkillReleaseEntity();
		release.setTenantId("2082273975596806145");
		release.setSkillId(SYSTEM_SKILL_ID);

		service(skillService, packageMapper).freezeRuntimeSnapshot(release);

		assertEquals("PROMPT_INJECTION", release.getRuntimeKind());
		assertEquals("System-owned prompt", release.getPromptSnapshot());
		assertEquals("8a1dbba3cc4ed7b8363f6d6d6558bc91992e65f804c81cd040b83e73c7bd2285",
				release.getInstructionDigest());
		assertEquals("package-sha-256", release.getPackageSha256());
		assertEquals("[\"KNOWLEDGE\"]", release.getRequiredProvidersJson());

		ArgumentCaptor<LambdaQueryWrapper<AiSkillPackageEntity>> queryCaptor = ArgumentCaptor
			.forClass(LambdaQueryWrapper.class);
		verify(packageMapper).selectOne(queryCaptor.capture());
		queryCaptor.getValue().getSqlSegment();
		Map<String, Object> parameters = queryCaptor.getValue().getParamNameValuePairs();
		assertTrue(parameters.containsValue(SYSTEM_SKILL_ID));
		assertTrue(parameters.containsValue("0"));
		assertFalse(parameters.containsValue("2082273975596806145"));
	}

	@Test
	void rejectsPublicationWhenOwningSkillHasNoApprovedPackage() {
		AiSkillService skillService = mock(AiSkillService.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		AiSkillEntity customSkill = new AiSkillEntity();
		customSkill.setId(SYSTEM_SKILL_ID);
		customSkill.setTenantId("2082273975596806145");
		customSkill.setSkillType("custom");
		customSkill.setImplType("prompt_injection");
		customSkill.setConfig("{\"promptTemplate\":\"Custom prompt\"}");
		when(skillService.getById(SYSTEM_SKILL_ID)).thenReturn(customSkill);
		when(packageMapper.selectOne(any())).thenReturn(null);
		AiAgentSkillReleaseEntity release = new AiAgentSkillReleaseEntity();
		release.setTenantId("2082273975596806145");
		release.setSkillId(SYSTEM_SKILL_ID);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service(skillService, packageMapper).freezeRuntimeSnapshot(release));

		assertTrue(error.getMessage().contains("重新导入、审核并发布"));
	}

	@Test
	void freezesTrustedBuiltInPromptWithoutZipPackage() {
		AiSkillService skillService = mock(AiSkillService.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		AiSkillEntity builtIn = promptSkill(SYSTEM_SKILL_ID);
		builtIn.setSkillType("builtin");
		builtIn.setConfig("{\"promptTemplate\":\"Built-in prompt\",\"requiredProviders\":[\"WORK\"]}");
		when(skillService.getById(SYSTEM_SKILL_ID)).thenReturn(builtIn);
		when(packageMapper.selectOne(any())).thenReturn(null);
		AiAgentSkillReleaseEntity release = new AiAgentSkillReleaseEntity();
		release.setTenantId("2082273975596806145");
		release.setSkillId(SYSTEM_SKILL_ID);

		service(skillService, packageMapper).freezeRuntimeSnapshot(release);

		assertEquals("PROMPT_INJECTION", release.getRuntimeKind());
		assertEquals("Built-in prompt", release.getPromptSnapshot());
		assertEquals("9df9da17fc857b3751e3e77e4c8f8333796cf34c6410198bf7c9476ef2ffd01b",
				release.getInstructionDigest());
		assertEquals(64, release.getPackageSha256().length());
		assertEquals("[\"WORK\"]", release.getRequiredProvidersJson());
	}

	@Test
	void syncsTrustedBuiltInPromptToTenantAssistant() {
		AiSkillService skillService = mock(AiSkillService.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		AiAgentSkillMapper draftMapper = mock(AiAgentSkillMapper.class);
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		AiSkillEntity builtIn = promptSkill(SYSTEM_SKILL_ID);
		builtIn.setSkillType("builtin");
		builtIn.setConfig("{\"promptTemplate\":\"Built-in prompt\"}");
		AiSkillVO available = new AiSkillVO();
		available.setSkillCode(builtIn.getSkillCode());
		when(skillService.listAvailableSkills()).thenReturn(List.of(available));
		when(skillService.findEnabledByCodes(List.of(builtIn.getSkillCode()))).thenReturn(List.of(builtIn));
		when(skillService.getById(SYSTEM_SKILL_ID)).thenReturn(builtIn);
		when(packageMapper.selectOne(any())).thenReturn(null);
		when(draftMapper.selectOne(any())).thenReturn(null);
		when(releaseMapper.selectOne(any())).thenReturn(null);
		TenantContextHolder.setTenantId(2082273975596806145L);
		AiAgentSkillServiceImpl subject = service(skillService, releaseMapper,
				mock(AiAgentSkillReleaseHistoryMapper.class), packageMapper);
		ReflectionTestUtils.setField(subject, "baseMapper", draftMapper);

		subject.syncEnabledSkillsToAgent(3001L);

		verify(draftMapper).insert(any(AiAgentSkillEntity.class));
		ArgumentCaptor<AiAgentSkillReleaseEntity> releaseCaptor = ArgumentCaptor
			.forClass(AiAgentSkillReleaseEntity.class);
		verify(releaseMapper).insert(releaseCaptor.capture());
		assertEquals(3001L, releaseCaptor.getValue().getAgentId());
		assertEquals("ACTIVE", releaseCaptor.getValue().getReleaseStatus());
		assertEquals("Built-in prompt", releaseCaptor.getValue().getPromptSnapshot());
	}

	@Test
	void sharedDefaultAgentPublishedQueryIsLimitedToSystemAndCurrentTenant() {
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		when(releaseMapper.selectList(any())).thenReturn(List.of());
		TenantContextHolder.setTenantId(2082273975596806145L);

		service(mock(AiSkillService.class), releaseMapper, mock(AiAgentSkillReleaseHistoryMapper.class),
				mock(AiSkillPackageMapper.class))
			.getPublishedByAgentId(AiAgentService.DEFAULT_AGENT_ID);

		ArgumentCaptor<LambdaQueryWrapper<AiAgentSkillReleaseEntity>> queryCaptor = ArgumentCaptor
			.forClass(LambdaQueryWrapper.class);
		verify(releaseMapper).selectList(queryCaptor.capture());
		queryCaptor.getValue().getSqlSegment();
		Map<String, Object> parameters = queryCaptor.getValue().getParamNameValuePairs();
		assertTrue(parameters.containsValue("0"));
		assertTrue(parameters.containsValue("2082273975596806145"));
	}

	@Test
	void sharedDefaultAgentDraftQueryIsLimitedToSystemAndCurrentTenant() {
		AiAgentSkillMapper draftMapper = mock(AiAgentSkillMapper.class);
		when(draftMapper.selectList(any())).thenReturn(List.of());
		TenantContextHolder.setTenantId(2082273975596806145L);
		AiAgentSkillServiceImpl service = service(mock(AiSkillService.class), mock(AiAgentSkillReleaseMapper.class),
				mock(AiAgentSkillReleaseHistoryMapper.class), mock(AiSkillPackageMapper.class));
		ReflectionTestUtils.setField(service, "baseMapper", draftMapper);

		service.getByAgentId(AiAgentService.DEFAULT_AGENT_ID);

		ArgumentCaptor<LambdaQueryWrapper<AiAgentSkillEntity>> queryCaptor = ArgumentCaptor
			.forClass(LambdaQueryWrapper.class);
		verify(draftMapper).selectList(queryCaptor.capture());
		queryCaptor.getValue().getSqlSegment();
		Map<String, Object> parameters = queryCaptor.getValue().getParamNameValuePairs();
		assertTrue(parameters.containsValue("0"));
		assertTrue(parameters.containsValue("2082273975596806145"));
	}

	@Test
	void publishedQueryFiltersDisabledSkillByIdInsteadOfSameCode() {
		long tenantSkillId = 202L;
		AiSkillService skillService = mock(AiSkillService.class);
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		when(releaseMapper.selectList(any()))
			.thenReturn(List.of(release(2026L, SYSTEM_SKILL_ID, "v1"), release(2026L, tenantSkillId, "v1")));
		when(skillService.listByIds(any())).thenReturn(List.of(skill(SYSTEM_SKILL_ID, "0", "same-code", 1),
				skill(tenantSkillId, "2082273975596806145", "same-code", 0)));
		TenantContextHolder.setTenantId(2082273975596806145L);

		List<AiAgentSkillVO> published = service(skillService, releaseMapper,
				mock(AiAgentSkillReleaseHistoryMapper.class), mock(AiSkillPackageMapper.class))
			.getPublishedByAgentId(2026L);

		assertEquals(1, published.size());
		assertEquals(SYSTEM_SKILL_ID, published.get(0).getSkillId());
	}

	@Test
	void currentTenantSkillShadowsSystemSkillWithSameCode() {
		long tenantSkillId = 202L;
		AiSkillService skillService = mock(AiSkillService.class);
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		when(releaseMapper.selectList(any()))
			.thenReturn(List.of(release(2026L, SYSTEM_SKILL_ID, "v1"), release(2026L, tenantSkillId, "v1")));
		when(skillService.listByIds(any())).thenReturn(List.of(skill(SYSTEM_SKILL_ID, "0", "same-code", 1),
				skill(tenantSkillId, "2082273975596806145", "same-code", 1)));
		TenantContextHolder.setTenantId(2082273975596806145L);

		List<AiAgentSkillVO> published = service(skillService, releaseMapper,
				mock(AiAgentSkillReleaseHistoryMapper.class), mock(AiSkillPackageMapper.class))
			.getPublishedByAgentId(2026L);

		assertEquals(1, published.size());
		assertEquals(tenantSkillId, published.get(0).getSkillId());
	}

	@Test
	void publishedSkillProjectionPreservesDescription() {
		AiSkillService skillService = mock(AiSkillService.class);
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		AiSkillEntity publishedSkill = skill(SYSTEM_SKILL_ID, "0", "prompt-skill", 1);
		publishedSkill.setDescription("Answers with engineering context");
		when(releaseMapper.selectList(any())).thenReturn(List.of(release(2026L, SYSTEM_SKILL_ID, "v1")));
		when(skillService.listByIds(any())).thenReturn(List.of(publishedSkill));
		TenantContextHolder.setTenantId(2082273975596806145L);

		List<AiAgentSkillVO> published = service(skillService, releaseMapper,
				mock(AiAgentSkillReleaseHistoryMapper.class), mock(AiSkillPackageMapper.class))
			.getPublishedByAgentId(2026L);

		assertEquals("Answers with engineering context", published.get(0).getDescription());
	}

	@Test
	void rejectsGrayForLegacyPromptRelease() {
		long agentId = 2026L;
		AiSkillService skillService = mock(AiSkillService.class);
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		AiAgentSkillReleaseEntity legacyRelease = release(agentId, SYSTEM_SKILL_ID, "v1");
		legacyRelease.setRuntimeKind(PromptSkillReleaseSnapshot.LEGACY_RUNTIME_KIND);
		when(releaseMapper.selectList(any())).thenReturn(List.of(legacyRelease));
		when(skillService.listByIds(any())).thenReturn(List.of(promptSkill(SYSTEM_SKILL_ID)));
		AiAgentVersionGrayDTO gray = new AiAgentVersionGrayDTO();
		gray.setPercent(10);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service(skillService, releaseMapper, mock(AiAgentSkillReleaseHistoryMapper.class),
						mock(AiSkillPackageMapper.class))
					.setReleaseGray(agentId, "v1", gray));

		assertTrue(error.getMessage().contains("重新导入、审核并发布"));
		verify(releaseMapper, never()).updateById(any(AiAgentSkillReleaseEntity.class));
	}

	@Test
	void rejectsRollbackForIncompletePromptHistoryBeforeMutation() {
		long agentId = 2026L;
		AiSkillService skillService = mock(AiSkillService.class);
		AiAgentSkillReleaseHistoryMapper historyMapper = mock(AiAgentSkillReleaseHistoryMapper.class);
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		AiAgentSkillReleaseHistoryEntity incomplete = history(agentId, SYSTEM_SKILL_ID, "v1");
		incomplete.setRuntimeKind("PROMPT_INJECTION");
		incomplete.setPromptSnapshot("Frozen prompt");
		incomplete.setInstructionDigest("instruction-digest");
		when(historyMapper.selectList(any())).thenReturn(List.of(incomplete));
		when(skillService.listByIds(any())).thenReturn(List.of(promptSkill(SYSTEM_SKILL_ID)));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service(skillService, releaseMapper, historyMapper, mock(AiSkillPackageMapper.class))
					.rollbackRelease(agentId, "v1"));

		assertTrue(error.getMessage().contains("重新导入、审核并发布"));
		verify(releaseMapper, never()).delete(any());
		verify(releaseMapper, never()).insert(any(AiAgentSkillReleaseEntity.class));
		verify(historyMapper, never()).insert(any(AiAgentSkillReleaseHistoryEntity.class));
	}

	@Test
	void rollbackReplacesOnlySkillsPresentInRequestedHistoryVersion() {
		long agentId = 2026L;
		long restoredSkillId = 101L;
		long unrelatedSkillId = 202L;
		AiAgentSkillReleaseHistoryMapper historyMapper = mock(AiAgentSkillReleaseHistoryMapper.class);
		AiAgentSkillReleaseMapper releaseMapper = mock(AiAgentSkillReleaseMapper.class);
		AiAgentSkillReleaseHistoryEntity snapshot = history(agentId, restoredSkillId, "v1");
		AiAgentSkillReleaseEntity currentRestored = release(agentId, restoredSkillId, "v2");
		when(historyMapper.selectList(any())).thenReturn(List.of(snapshot));
		when(releaseMapper.selectList(any())).thenReturn(List.of(currentRestored));

		service(mock(AiSkillService.class), releaseMapper, historyMapper, mock(AiSkillPackageMapper.class))
			.rollbackRelease(agentId, "v1");

		ArgumentCaptor<LambdaQueryWrapper<AiAgentSkillReleaseEntity>> deleteCaptor = ArgumentCaptor
			.forClass(LambdaQueryWrapper.class);
		verify(releaseMapper).delete(deleteCaptor.capture());
		deleteCaptor.getValue().getSqlSegment();
		Map<String, Object> deleteParameters = deleteCaptor.getValue().getParamNameValuePairs();
		assertTrue(deleteParameters.containsValue(restoredSkillId));
		assertFalse(deleteParameters.containsValue(unrelatedSkillId));

		ArgumentCaptor<AiAgentSkillReleaseHistoryEntity> archivedCaptor = ArgumentCaptor
			.forClass(AiAgentSkillReleaseHistoryEntity.class);
		verify(historyMapper, times(1)).insert(archivedCaptor.capture());
		assertEquals(restoredSkillId, archivedCaptor.getValue().getSkillId());

		ArgumentCaptor<AiAgentSkillReleaseEntity> restoredCaptor = ArgumentCaptor
			.forClass(AiAgentSkillReleaseEntity.class);
		verify(releaseMapper, times(1)).insert(restoredCaptor.capture());
		assertEquals(restoredSkillId, restoredCaptor.getValue().getSkillId());
	}

	private AiAgentSkillReleaseHistoryEntity history(long agentId, long skillId, String version) {
		AiAgentSkillReleaseHistoryEntity history = new AiAgentSkillReleaseHistoryEntity();
		history.setTenantId("2082273975596806145");
		history.setAgentId(agentId);
		history.setSkillId(skillId);
		history.setReleaseVersion(version);
		return history;
	}

	private AiAgentSkillReleaseEntity release(long agentId, long skillId, String version) {
		AiAgentSkillReleaseEntity release = new AiAgentSkillReleaseEntity();
		release.setTenantId("2082273975596806145");
		release.setAgentId(agentId);
		release.setSkillId(skillId);
		release.setReleaseVersion(version);
		return release;
	}

	private AiSkillEntity skill(long id, String tenantId, String code, int enabled) {
		AiSkillEntity skill = new AiSkillEntity();
		skill.setId(id);
		skill.setTenantId(tenantId);
		skill.setSkillCode(code);
		skill.setSkillName(code);
		skill.setIsEnabled(enabled);
		return skill;
	}

	private AiSkillEntity promptSkill(long id) {
		AiSkillEntity skill = skill(id, "0", "prompt-skill", 1);
		skill.setImplType("prompt_injection");
		return skill;
	}

	private AiAgentSkillServiceImpl service(AiSkillService skillService, AiSkillPackageMapper packageMapper) {
		return service(skillService, mock(AiAgentSkillReleaseMapper.class),
				mock(AiAgentSkillReleaseHistoryMapper.class), packageMapper);
	}

	private AiAgentSkillServiceImpl service(AiSkillService skillService, AiAgentSkillReleaseMapper releaseMapper,
			AiAgentSkillReleaseHistoryMapper historyMapper, AiSkillPackageMapper packageMapper) {
		return new AiAgentSkillServiceImpl(skillService, releaseMapper, historyMapper, packageMapper,
				mock(AiTenantAgentRouteMapper.class), mock(AiAgentService.class), mock(DifyAgentBridge.class),
				mock(ObjectProvider.class));
	}

}
