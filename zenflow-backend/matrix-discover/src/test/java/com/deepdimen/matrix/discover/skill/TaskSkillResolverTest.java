package com.deepdimen.matrix.discover.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TaskSkillResolverTest {

	@Test
	void rejectsUnapprovedAndDisabledPackages() {
		AiSkillMapper skills = mock(AiSkillMapper.class);
		AiSkillPackageMapper packages = mock(AiSkillPackageMapper.class);
		var resolver = new MatrixTaskSkillResolver(skills, packages, new ObjectMapper());
		var pkg = new AiSkillPackageEntity();
		pkg.setId(20L);
		pkg.setTenantId("1");
		pkg.setSkillId(10L);
		pkg.setPackageStatus("PENDING_REVIEW");
		when(packages.selectOne(any())).thenReturn(pkg);
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(1L, 20L));
		pkg.setPackageStatus("APPROVED");
		var skill = new AiSkillEntity();
		skill.setTenantId("1");
		skill.setIsEnabled(0);
		when(skills.selectOne(any())).thenReturn(skill);
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(1L, 20L));
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null, 20L));
	}

	@Test
	void usesTheFrozenPackageInsteadOfMutableCatalogPrompt() {
		AiSkillMapper skills = mock(AiSkillMapper.class);
		AiSkillPackageMapper packages = mock(AiSkillPackageMapper.class);
		var resolver = new MatrixTaskSkillResolver(skills, packages, new ObjectMapper());
		var pkg = new AiSkillPackageEntity();
		pkg.setId(20L);
		pkg.setSkillId(10L);
		pkg.setTenantId("1");
		pkg.setPackageStatus("APPROVED");
		pkg.setPackageVersion("1.0");
		pkg.setPackageSha256("abc");
		pkg.setManifestJson(
				"{\"runtimeKind\":\"PROMPT_INJECTION\",\"config\":{\"promptTemplate\":\"approved prompt\"}}");
		when(packages.selectOne(any())).thenReturn(pkg);
		var skill = new AiSkillEntity();
		skill.setTenantId("1");
		skill.setIsEnabled(1);
		skill.setConfig("mutable draft");
		when(skills.selectOne(any())).thenReturn(skill);
		assertEquals("approved prompt", resolver.resolve(1L, 20L).prompt());
		pkg.setTenantId("2");
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(1L, 20L));
	}

}
