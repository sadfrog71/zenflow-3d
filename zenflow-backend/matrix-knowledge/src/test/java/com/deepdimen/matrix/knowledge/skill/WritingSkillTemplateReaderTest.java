package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.entity.AiDocumentTemplateEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentTemplateMapper;

class WritingSkillTemplateReaderTest {

	final AiDocumentTemplateMapper mapper = mock(AiDocumentTemplateMapper.class);

	final ObjectProvider<AssistantIdentityResolver> identities = mock(ObjectProvider.class);

	final AssistantIdentityResolver identity = mock(AssistantIdentityResolver.class);

	final WritingSkillTemplateReader reader = new WritingSkillTemplateReader(mapper, identities);

	AiDocumentTemplateEntity row;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("test", "unused",
					List.of(new SimpleGrantedAuthority("aiDocumentTemplate_view"))));
		when(identities.getIfAvailable()).thenReturn(identity);
		when(identity.currentIdentity())
			.thenReturn(new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "test", "WRITING"));
		row = new AiDocumentTemplateEntity();
		row.setId(8L);
		row.setDelFlag("0");
		row.setContent("<p>原模板</p>");
		when(mapper.selectOne(any())).thenReturn(row);
	}

	@AfterEach
	void cleanup() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void pinsContentAndRejectsMutation() {
		var fixed = reader.capture(8L);
		assertEquals(fixed, reader.requireSame(fixed));
		assertFalse(fixed.toString().contains("原模板"));
		row.setContent("<p>修改后的模板</p>");
		assertThrows(IllegalArgumentException.class, () -> reader.requireSame(fixed));
	}

	@Test
	void noAuthenticationNeverReadsTemplate() {
		SecurityContextHolder.clearContext();
		assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
		verifyZeroInteractions(mapper);
	}

	@Test
	void explicitQueryIsTenantScopedAndExcludesDeletedResources() {
		reader.capture(8L);
		verify(mapper).selectOne(argThat(wrapper -> wrapper.getSqlSegment().contains("tenant_id")
				&& wrapper.getSqlSegment().contains("del_flag")));
		row.setDelFlag("1");
		assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
	}

	@Test
	void mismatchedTenantAndMissingPermissionNeverReadTemplate() {
		TenantContextHolder.setTenantId(9L);
		assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
		TenantContextHolder.setTenantId(7L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("test", "unused", List.of()));
		assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
		verifyZeroInteractions(mapper);
	}

}
