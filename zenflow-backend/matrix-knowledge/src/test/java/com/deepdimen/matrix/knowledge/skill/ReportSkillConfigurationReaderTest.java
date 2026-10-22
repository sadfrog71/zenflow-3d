package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.entity.AiReportConfEntity;
import com.deepdimen.matrix.knowledge.mapper.AiReportConfMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class ReportSkillConfigurationReaderTest {

	final AiReportConfMapper mapper = mock(AiReportConfMapper.class);

	final RemoteFileService files = mock(RemoteFileService.class);

	final ObjectProvider<AssistantIdentityResolver> identities = mock(ObjectProvider.class);

	final AssistantIdentityResolver identity = mock(AssistantIdentityResolver.class);

	final ReportSkillConfigurationReader reader = new ReportSkillConfigurationReader(mapper, files, identities,
			new ObjectMapper());

	AiReportConfEntity row;

	byte[] content = { 1, 2, 3 };

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("test", "unused",
					List.of(new SimpleGrantedAuthority("knowledge_aiReportConf_view"))));
		when(identities.getIfAvailable()).thenReturn(identity);
		when(identity.currentIdentity())
			.thenReturn(new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "test", "REPORT"));
		row = new AiReportConfEntity();
		row.setId(8L);
		row.setDelFlag("0");
		row.setReportResource("bucket/template.docx");
		row.setReportPrompt("规则");
		when(mapper.selectOne(any())).thenReturn(row);
		when(files.getFile(any())).thenAnswer(call -> feign.Response.builder()
			.status(200)
			.reason("OK")
			.headers(Map.of())
			.request(feign.Request.create(feign.Request.HttpMethod.GET, "http://fixture", Map.of(), null,
					java.nio.charset.StandardCharsets.UTF_8, null))
			.body(content)
			.build());
	}

	@AfterEach
	void cleanup() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void pinsActualBytesAndDefensivelyCopiesThem() {
		var capture = reader.capture(8L);
		assertEquals(capture.snapshot(), reader.requireSame(capture.snapshot()).snapshot());
		var bytes = capture.templateBytes();
		bytes[0] = 9;
		assertEquals(1, capture.templateBytes()[0]);
		content = new byte[] { 4, 5, 6 };
		assertThrows(IllegalArgumentException.class, () -> reader.requireSame(capture.snapshot()));
	}

	@Test
	void externalOrTraversalResourcesNeverReachFileService() {
		for (String path : List.of("https://example.com/a.docx", "../a.docx", "bucket/%2e%2e/a.docx")) {
			row.setReportResource(path);
			assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
		}
		verifyZeroInteractions(files);
	}

	@Test
	void refreshedCorrelationIdDoesNotChangeAuthorization() {
		when(identity.currentIdentity()).thenReturn(
				new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "first", "REPORT"),
				new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "second", "REPORT"));
		assertNotNull(reader.capture(8L));
	}

	@Test
	void changedActorOrOrganizationDuringDownloadIsRejected() {
		var before = new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "first", "REPORT");
		for (var after : List.of(
				new IdentityEnvelope("7", "4", null, List.of(), List.of(), List.of(), "second", "REPORT"),
				new IdentityEnvelope("7", "3", "9", List.of(), List.of(), List.of(), "second", "REPORT"))) {
			when(identity.currentIdentity()).thenReturn(before, after);
			assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
		}
	}

	@Test
	void missingAuthenticationCannotReadConfiguration() {
		SecurityContextHolder.clearContext();
		assertThrows(IllegalArgumentException.class, () -> reader.capture(8L));
		verifyZeroInteractions(mapper, files);
	}

}
