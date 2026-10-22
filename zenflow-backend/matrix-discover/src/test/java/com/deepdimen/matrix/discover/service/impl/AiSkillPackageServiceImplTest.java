package com.deepdimen.matrix.discover.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.deepdimen.matrix.discover.vo.AiSkillImportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class AiSkillPackageServiceImplTest {

	private static final long TENANT_ID = 2082273975596806145L;

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void restoresDeletedSkillInsteadOfInsertingDuplicateCode() throws Exception {
		TenantContextHolder.setTenantId(TENANT_ID);
		AiSkillMapper skillMapper = mock(AiSkillMapper.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		ObjectMapper objectMapper = new ObjectMapper();
		AiSkillPackageServiceImpl service = new AiSkillPackageServiceImpl(skillMapper, objectMapper,
				new AgentSkillPackageParser(objectMapper), mock(AiAgentSkillService.class));
		ReflectionTestUtils.setField(service, "baseMapper", packageMapper);

		AiSkillEntity deleted = new AiSkillEntity();
		deleted.setId(99L);
		deleted.setTenantId(String.valueOf(TENANT_ID));
		deleted.setSkillCode("artifact");
		deleted.setDelFlag("1");
		when(skillMapper.selectOne(any())).thenReturn(null);
		when(skillMapper.selectIncludingDeleted(String.valueOf(TENANT_ID), "artifact")).thenReturn(deleted);
		when(skillMapper.restoreDeleted(any())).thenReturn(1);
		when(packageMapper.selectOne(any(), eq(true))).thenReturn(null);
		when(packageMapper.selectCount(any())).thenReturn(0L);
		when(packageMapper.insertOrUpdate(any(AiSkillPackageEntity.class))).thenReturn(true);

		MockMultipartFile file = new MockMultipartFile("file", "artifact.zip", "application/zip",
				zip("---\nname: artifact\ndescription: Tenant artifact skill\n---\n\nRender a chart."));
		AiSkillImportVO result = service.importPackage(file);

		assertEquals(99L, result.getSkillId());
		assertEquals("artifact", result.getSkillCode());
		verify(skillMapper).restoreDeleted(eq(deleted));
		verify(skillMapper, never()).insert(any(AiSkillEntity.class));
		verify(packageMapper).insertOrUpdate(any(AiSkillPackageEntity.class));
	}

	@Test
	void reusesArchivedPackageWhenDeletedSkillIsImportedAgain() throws Exception {
		TenantContextHolder.setTenantId(TENANT_ID);
		AiSkillMapper skillMapper = mock(AiSkillMapper.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		ObjectMapper objectMapper = new ObjectMapper();
		AiSkillPackageServiceImpl service = new AiSkillPackageServiceImpl(skillMapper, objectMapper,
				new AgentSkillPackageParser(objectMapper), mock(AiAgentSkillService.class));
		ReflectionTestUtils.setField(service, "baseMapper", packageMapper);

		AiSkillEntity deleted = new AiSkillEntity();
		deleted.setId(99L);
		deleted.setTenantId(String.valueOf(TENANT_ID));
		deleted.setSkillCode("artifact");
		deleted.setDelFlag("1");
		AiSkillPackageEntity archived = new AiSkillPackageEntity();
		archived.setId(199L);
		archived.setTenantId(String.valueOf(TENANT_ID));
		archived.setSkillId(99L);
		archived.setPackageVersion("v1");
		archived.setPackageStatus("ARCHIVED");
		when(skillMapper.selectOne(any())).thenReturn(null);
		when(skillMapper.selectIncludingDeleted(String.valueOf(TENANT_ID), "artifact")).thenReturn(deleted);
		when(skillMapper.restoreDeleted(any())).thenReturn(1);
		when(packageMapper.selectOne(any(), eq(true))).thenReturn(archived);
		when(packageMapper.insertOrUpdate(any(AiSkillPackageEntity.class))).thenReturn(true);

		MockMultipartFile file = new MockMultipartFile("file", "artifact.zip", "application/zip",
				zip("---\nname: artifact\ndescription: Tenant artifact skill\n---\n\nRender a chart."));
		AiSkillImportVO result = service.importPackage(file);

		assertEquals(99L, result.getSkillId());
		assertEquals(199L, result.getPackageId());
		assertEquals("v1", result.getPackageVersion());
		assertEquals("PENDING_REVIEW", archived.getPackageStatus());
		verify(packageMapper, never()).selectCount(any());
		verify(packageMapper).insertOrUpdate(eq(archived));
	}

	@Test
	void reimportDisablesPreviouslyApprovedSkillUntilReview() throws Exception {
		TenantContextHolder.setTenantId(TENANT_ID);
		AiSkillMapper skillMapper = mock(AiSkillMapper.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		AiAgentSkillService agentSkillService = mock(AiAgentSkillService.class);
		ObjectMapper objectMapper = new ObjectMapper();
		AiSkillPackageServiceImpl service = new AiSkillPackageServiceImpl(skillMapper, objectMapper,
				new AgentSkillPackageParser(objectMapper), agentSkillService);
		ReflectionTestUtils.setField(service, "baseMapper", packageMapper);

		AiSkillEntity existing = new AiSkillEntity();
		existing.setId(99L);
		existing.setTenantId(String.valueOf(TENANT_ID));
		existing.setSkillCode("artifact");
		existing.setIsEnabled(1);
		when(skillMapper.selectOne(any())).thenReturn(existing);
		when(packageMapper.selectOne(any(), eq(true))).thenReturn(null);
		when(packageMapper.selectCount(any())).thenReturn(1L);
		when(packageMapper.insertOrUpdate(any(AiSkillPackageEntity.class))).thenReturn(true);

		MockMultipartFile file = new MockMultipartFile("file", "artifact.zip", "application/zip",
				zip("---\nname: artifact\ndescription: Updated tenant artifact skill\n---\n\nRender a new chart."));
		AiSkillImportVO result = service.importPackage(file);

		assertEquals("PENDING_REVIEW", result.getPackageStatus());
		assertEquals(0, existing.getIsEnabled());
		verify(skillMapper).updateById(existing);
		verify(agentSkillService).syncDefaultAgentSkill(99L, false);
	}

	@Test
	void downloadsArchivedBytesOnlyFromTenantAwareMapper() {
		TenantContextHolder.setTenantId(TENANT_ID);
		AiSkillMapper skillMapper = mock(AiSkillMapper.class);
		AiSkillPackageMapper packageMapper = mock(AiSkillPackageMapper.class);
		ObjectMapper objectMapper = new ObjectMapper();
		AiSkillPackageServiceImpl service = new AiSkillPackageServiceImpl(skillMapper, objectMapper,
				new AgentSkillPackageParser(objectMapper), mock(AiAgentSkillService.class));
		ReflectionTestUtils.setField(service, "baseMapper", packageMapper);
		AiSkillPackageEntity stored = new AiSkillPackageEntity();
		stored.setOriginalFilename("analysis.zip");
		stored.setPackageSha256("abc123");
		stored.setArchiveData(new byte[] { 1, 2, 3 });
		when(packageMapper.selectArchiveById(199L, String.valueOf(TENANT_ID))).thenReturn(stored);

		var result = service.download(199L);

		assertEquals("analysis.zip", result.filename());
		assertEquals("abc123", result.sha256());
		assertEquals(3, result.size());
	}

	@Test
	void legacyPackageSelectionIsScopedBeforeSortingAndCounting() {
		TenantContextHolder.setTenantId(TENANT_ID);
		var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
				new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test");
		com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AiSkillPackageEntity.class);
		AiSkillPackageMapper packages = mock(AiSkillPackageMapper.class);
		ObjectMapper json = new ObjectMapper();
		var service = new AiSkillPackageServiceImpl(mock(AiSkillMapper.class), json, new AgentSkillPackageParser(json),
				mock(AiAgentSkillService.class));
		ReflectionTestUtils.setField(service, "baseMapper", packages);
		when(packages.selectList(any())).thenAnswer(call -> {
			var query = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) call.getArgument(0);
			org.junit.jupiter.api.Assertions.assertTrue(query.getSqlSegment().contains("source_channel ="));
			org.junit.jupiter.api.Assertions.assertTrue(query.getParamNameValuePairs().containsValue("LEGACY"));
			return java.util.List.of();
		});
		when(packages.selectCount(any())).thenAnswer(call -> {
			var query = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) call.getArgument(0);
			org.junit.jupiter.api.Assertions.assertTrue(query.getSqlSegment().contains("source_channel ="));
			org.junit.jupiter.api.Assertions.assertTrue(query.getParamNameValuePairs().containsValue("LEGACY"));
			return 2L;
		});
		ReflectionTestUtils.invokeMethod(service, "latestPackage", 99L);
		AiSkillEntity skill = new AiSkillEntity();
		skill.setId(99L);
		ReflectionTestUtils.invokeMethod(service, "latestBySkill", java.util.List.of(skill));
		assertEquals("v3", ReflectionTestUtils.invokeMethod(service, "nextVersion", 99L));
		verify(packages, org.mockito.Mockito.times(2)).selectList(any());
	}

	@Test
	void legacyDeleteCannotRemoveSkillWithManagedPackage() {
		TenantContextHolder.setTenantId(TENANT_ID);
		AiSkillMapper skills = mock(AiSkillMapper.class);
		AiSkillPackageMapper packages = mock(AiSkillPackageMapper.class);
		AiAgentSkillService agents = mock(AiAgentSkillService.class);
		ObjectMapper json = new ObjectMapper();
		var service = new AiSkillPackageServiceImpl(skills, json, new AgentSkillPackageParser(json), agents);
		ReflectionTestUtils.setField(service, "baseMapper", packages);
		AiSkillEntity skill = new AiSkillEntity();
		skill.setId(99L);
		skill.setSkillType("custom");
		when(skills.selectOne(any())).thenReturn(skill);
		when(packages.selectCount(any())).thenReturn(1L);
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.delete(99L));
		verify(agents, never()).removeDefaultAgentSkill(any());
		verify(skills, never()).deleteById(any(java.io.Serializable.class));
	}

	@Test
	void managedDirectoryCannotUseLegacyMutationEntrypoints() {
		TenantContextHolder.setTenantId(TENANT_ID);
		AiSkillMapper skills = mock(AiSkillMapper.class);
		AiSkillPackageMapper packages = mock(AiSkillPackageMapper.class);
		AiAgentSkillService agents = mock(AiAgentSkillService.class);
		ObjectMapper json = new ObjectMapper();
		var service = new AiSkillPackageServiceImpl(skills, json, new AgentSkillPackageParser(json), agents);
		ReflectionTestUtils.setField(service, "baseMapper", packages);
		AiSkillEntity skill = new AiSkillEntity();
		skill.setId(99L);
		skill.setSkillType("custom");
		skill.setManagementMode("MANAGED");
		when(skills.selectOne(any())).thenReturn(skill);
		java.util.List<org.junit.jupiter.api.function.Executable> calls = java.util.List.of(() -> service.approve(99L),
				() -> service.publish(99L), () -> service.unpublish(99L), () -> service.delete(99L),
				() -> service.updatePackage(99L, null));
		for (var call : calls) {
			org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, call);
		}
		org.mockito.Mockito.verifyZeroInteractions(packages, agents);
		verify(skills, never()).updateById(any(AiSkillEntity.class));
	}

	@Test
	void legacyImportCannotRestoreDeletedManagedDirectory() throws Exception {
		TenantContextHolder.setTenantId(TENANT_ID);
		AiSkillMapper skills = mock(AiSkillMapper.class);
		AiSkillPackageMapper packages = mock(AiSkillPackageMapper.class);
		AiAgentSkillService agents = mock(AiAgentSkillService.class);
		ObjectMapper json = new ObjectMapper();
		var service = new AiSkillPackageServiceImpl(skills, json, new AgentSkillPackageParser(json), agents);
		ReflectionTestUtils.setField(service, "baseMapper", packages);
		AiSkillEntity skill = new AiSkillEntity();
		skill.setId(99L);
		skill.setManagementMode("MANAGED");
		skill.setDelFlag("1");
		when(skills.selectIncludingDeleted(String.valueOf(TENANT_ID), "artifact")).thenReturn(skill);
		var file = new MockMultipartFile("file", "artifact.zip", "application/zip",
				zip("---\nname: artifact\ndescription: Managed artifact\n---\n\nRender a chart."));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> service.importPackage(file));
		verify(skills, never()).restoreDeleted(any());
		verify(skills, never()).insert(any(AiSkillEntity.class));
		org.mockito.Mockito.verifyZeroInteractions(packages, agents);
	}

	private byte[] zip(String skillMarkdown) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream output = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
			output.putNextEntry(new ZipEntry("artifact/SKILL.md"));
			output.write(skillMarkdown.getBytes(StandardCharsets.UTF_8));
			output.closeEntry();
		}
		return bytes.toByteArray();
	}

}
