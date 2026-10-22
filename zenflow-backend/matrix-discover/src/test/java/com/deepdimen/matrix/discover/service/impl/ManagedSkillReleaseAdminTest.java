package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedSkillReleaseAdminTest {

	private final AiSkillPackageMapper packages = mock(AiSkillPackageMapper.class);

	private final AiSkillReleaseMapper releases = mock(AiSkillReleaseMapper.class);

	private final AgentSkillPackageParser parser = new AgentSkillPackageParser(new ObjectMapper());

	private final ManagedSkillReleaseService service = new ManagedSkillReleaseService(packages, releases, parser,
			new ObjectMapper());

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		var user = mock(MatrixUser.class);
		when(user.getUsername()).thenReturn("manager");
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void packageFromDifferentSkillCannotPublishUnderRequestedIdentity() {
		var entry = new AiSkillPackageEntity();
		entry.setTenantId("42");
		entry.setSkillId(9L);
		entry.setSourceChannel("MANAGED");
		entry.setPackageStatus("APPROVED");
		entry.setArchiveData(new byte[] { 1 });
		when(packages.lockManagedPackage(7L, "42")).thenReturn(entry);
		assertThrows(IllegalArgumentException.class, () -> service.publishForSkill(8L, 7L, "v1"));
		verifyZeroInteractions(releases);
	}

	@Test
	void revocationUsesCurrentTenantAndExpectedDigestAndRejectsConflict() {
		String digest = "a".repeat(64);
		when(releases.revoke("42", 8L, digest, "manager", "invalid contract")).thenReturn(1, 0);
		service.revoke(8L, digest, "invalid contract");
		assertThrows(IllegalStateException.class, () -> service.revoke(8L, digest, "invalid contract"));
		TenantContextHolder.setTenantId(0L);
		assertThrows(IllegalArgumentException.class, () -> service.revoke(8L, digest, "invalid contract"));
		verify(releases, times(2)).revoke("42", 8L, digest, "manager", "invalid contract");
	}

}
