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
import com.deepdimen.matrix.knowledge.entity.AiOcrConfEntity;
import com.deepdimen.matrix.knowledge.mapper.AiOcrConfMapper;

class OcrSkillConfigurationReaderTest {

	final AiOcrConfMapper mapper = mock(AiOcrConfMapper.class);

	final ObjectProvider<AssistantIdentityResolver> identities = mock(ObjectProvider.class);

	final AssistantIdentityResolver identity = mock(AssistantIdentityResolver.class);

	final OcrSkillConfigurationReader reader = new OcrSkillConfigurationReader(mapper, identities,
			new com.fasterxml.jackson.databind.ObjectMapper());

	AiOcrConfEntity row;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("test", "unused",
					List.of(new SimpleGrantedAuthority("knowledge_ocr_view"))));
		when(identities.getIfAvailable()).thenReturn(identity);
		when(identity.currentIdentity())
			.thenReturn(new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "test", "WRITING"));
		row = new AiOcrConfEntity();
		row.setId(8L);
		row.setDelFlag("0");
		row.setOcrPrompt("识别规则");
		row.setOcrMarked("{\"markerList\":[{\"name\":\"金额\",\"description\":\"原字段\"}]}");
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
		assertFalse(fixed.toString().contains("识别规则"));
		row.setOcrPrompt("修改后的识别规则");
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

	@Test
	void duplicateOrReservedFieldsCannotChangeOutputContract() {
		for (String fields : List.of("[{\"name\":\"isContain\"}]", "[{\"name\":\"a\"},{\"name\":\"a\"}]")) {
			row.setOcrMarked("{\"markerList\":" + fields + "}");
			assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
		}
	}

}
