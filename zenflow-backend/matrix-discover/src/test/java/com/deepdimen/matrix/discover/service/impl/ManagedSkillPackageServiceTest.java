package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedSkillPackageServiceTest {

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void importingSharedLegacySkillDoesNotChangeLegacyContentOrSwitches() throws Exception {
		login();
		var skills = mock(AiSkillMapper.class);
		var packages = mock(AiSkillPackageMapper.class);
		var json = new ObjectMapper();
		var service = new ManagedSkillPackageService(skills, packages, new AgentSkillPackageParser(json), json);
		AiSkillEntity existing = new AiSkillEntity();
		existing.setId(9L);
		existing.setManagementMode("LEGACY");
		existing.setIsEnabled(1);
		existing.setConfig("frozen-config");
		when(skills.selectOne(any())).thenReturn(existing);
		when(packages.insert(any(AiSkillPackageEntity.class))).thenReturn(1);
		service.importPackage(archive(), "artifact.zip");
		assertEquals("LEGACY", existing.getManagementMode());
		assertEquals(1, existing.getIsEnabled());
		assertEquals("frozen-config", existing.getConfig());
		verify(skills, never()).updateById(any(AiSkillEntity.class));
		verify(skills, never()).insert(any(AiSkillEntity.class));
		var capture = ArgumentCaptor.forClass(AiSkillPackageEntity.class);
		verify(packages).insert(capture.capture());
		assertEquals(9L, capture.getValue().getSkillId());
		assertEquals("MANAGED", capture.getValue().getSourceChannel());
		assertEquals("PENDING_REVIEW", capture.getValue().getPackageStatus());
	}

	@Test
	void newManagedDirectoryStaysDisabledInLegacyRuntime() throws Exception {
		login();
		var skills = mock(AiSkillMapper.class);
		var packages = mock(AiSkillPackageMapper.class);
		var json = new ObjectMapper();
		when(skills.insert(any(AiSkillEntity.class))).thenReturn(1);
		when(packages.insert(any(AiSkillPackageEntity.class))).thenReturn(1);
		new ManagedSkillPackageService(skills, packages, new AgentSkillPackageParser(json), json)
			.importPackage(archive(), "artifact.zip");
		var capture = ArgumentCaptor.forClass(AiSkillEntity.class);
		verify(skills).insert(capture.capture());
		assertEquals("MANAGED", capture.getValue().getManagementMode());
		assertEquals(0, capture.getValue().getIsEnabled());
		assertEquals("{}", capture.getValue().getConfig());
	}

	@Test
	void reviewedArchivePublishesFixedContentAndRejectsChangedReviewEvidence() throws Exception {
		login();
		var skills = mock(AiSkillMapper.class);
		var packages = mock(AiSkillPackageMapper.class);
		var releases = mock(com.deepdimen.matrix.discover.mapper.AiSkillReleaseMapper.class);
		var json = new ObjectMapper();
		var parser = new AgentSkillPackageParser(json);
		byte[] bytes = archive();
		var parsed = parser.parse(bytes, "artifact.zip");
		var entry = new AiSkillPackageEntity();
		entry.setId(81L);
		entry.setSkillId(9L);
		entry.setTenantId("42");
		entry.setSourceChannel("MANAGED");
		entry.setPackageStatus("PENDING_REVIEW");
		entry.setArchiveData(bytes);
		entry.setOriginalFilename("artifact.zip");
		entry.setPackageSha256(parsed.sha256());
		when(packages.lockManagedPackage(81L, "42")).thenReturn(entry);
		when(packages.approveManaged(any(), any(), any(), any(), any())).thenAnswer(call -> {
			entry.setValidationReport((String) call.getArgument(3));
			entry.setPackageStatus("APPROVED");
			return 1;
		});
		new ManagedSkillPackageService(skills, packages, parser, json).approve(81L);
		assertEquals("reviewer", json.readTree(entry.getValidationReport()).path("reviewedBy").asText());
		when(releases.insertRelease(any())).thenReturn(1);
		var publishing = new ManagedSkillReleaseService(packages, releases, parser, json);
		var released = publishing.publish(81L, "v1");
		assertEquals("Summarize input.", released.getContentSnapshot().trim());
		assertEquals(9L, released.getSkillId());
		assertEquals("42", released.getTenantId());
		assertEquals("v1", released.getVersion());
		entry.setValidationReport("{\"releaseDigest\":\"changed\",\"reviewedBy\":\"reviewer\"}");
		assertThrows(IllegalArgumentException.class, () -> publishing.publish(81L, "v2"));
		verify(releases, times(1)).insertRelease(any());
		verifyZeroInteractions(skills);
	}

	@Test
	void previewUsesOwnedArchiveAndChecksDigestWithoutApprovingOrPublishing() throws Exception {
		login();
		var packages = mock(AiSkillPackageMapper.class);
		var releases = mock(com.deepdimen.matrix.discover.mapper.AiSkillReleaseMapper.class);
		var json = new ObjectMapper();
		var parser = new AgentSkillPackageParser(json);
		var bytes = archive();
		var entry = new AiSkillPackageEntity();
		entry.setId(81L);
		entry.setTenantId("42");
		entry.setSourceChannel("MANAGED");
		entry.setOriginalFilename("artifact.zip");
		entry.setArchiveData(bytes);
		entry.setPackageSha256(parser.parse(bytes, "artifact.zip").sha256());
		when(packages.findManagedArchive(81L, "42")).thenReturn(entry);
		var catalog = new ManagedSkillCatalogService(packages, releases, parser, json);
		assertEquals("Summarize input.", catalog.preview(81L).contentSnapshot().trim());
		entry.setPackageSha256("0".repeat(64));
		assertThrows(IllegalArgumentException.class, () -> catalog.preview(81L));
		entry.setTenantId("43");
		assertThrows(IllegalArgumentException.class, () -> catalog.preview(81L));
		verify(packages, never()).approveManaged(any(), any(), any(), any(), any());
		verifyZeroInteractions(releases);
	}

	private void login() {
		TenantContextHolder.setTenantId(42L);
		var user = mock(MatrixUser.class);
		when(user.getUsername()).thenReturn("reviewer");
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
	}

	private byte[] archive() throws Exception {
		var bytes = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
			zip.putNextEntry(new ZipEntry("artifact/SKILL.md"));
			zip.write("---\nname: artifact\ndescription: Managed test\n---\n\nSummarize input."
				.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("artifact/skill-package.json"));
			zip.write("""
					{"runtimeKind":"PROMPT","managedRelease":{
					  "inputSchema":{"type":"object","properties":{}},
					  "outputSchema":{"type":"object","properties":{}},
					  "executorRef":{"executorKey":"PROMPT"},"allowedParameters":[]}}
					""".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

}
