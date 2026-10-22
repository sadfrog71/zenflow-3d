package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.zenflow.admin.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class ZenflowPreviewRuntimeTest {

	final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	final ZenflowModelGateway gateway = mock(ZenflowModelGateway.class);

	@SuppressWarnings("unchecked")
	final ObjectProvider<SkillBindingResolver> resolver = mock(ObjectProvider.class);

	@SuppressWarnings("unchecked")
	final ObjectProvider<SkillExecutionService> execution = mock(ObjectProvider.class);

	ZenflowPreviewRuntime runtime() {
		var product = new ZenflowProperties();
		product.setTenantId(1);
		return new ZenflowPreviewRuntime(jdbc, new ObjectMapper(), product, mock(ZenflowAdminAccess.class),
				mock(ZenflowModelService.class), gateway, mock(ZenflowReferenceContextAdapter.class),
				mock(ZenflowReferenceSnapshots.class), resolver, execution, mock(PlatformTransactionManager.class));
	}

	IdentityEnvelope identity(String tenant, String subject, String purpose) {
		return new IdentityEnvelope(tenant, "42", null, List.of(), List.of(), List.of(), "test", purpose, subject);
	}

	@Test
	void onlyDedicatedAdministratorPurposeAndProductTenantMatch() {
		var runtime = runtime();
		assertTrue(runtime.supports(identity("1", "ADMIN_USER", "COMPANION_PREVIEW")));
		assertFalse(runtime.supports(identity("1", "APP_USER", "COMPANION_PREVIEW")));
		assertFalse(runtime.supports(identity("2", "ADMIN_USER", "COMPANION_PREVIEW")));
		assertFalse(runtime.supports(identity("1", "ADMIN_USER", "TEXT_CONVERSATION")));
	}

	@Test
	void suppliedIdentityAndRunIdsAloneNeverOpenTheThreadScopedLease() {
		var runtime = runtime();
		var identity = new Identity("1", "42", "42", "test");
		var consumer = ZenflowPreviewSkillPolicy.consumer("900");
		assertFalse(runtime.permits(identity, consumer, "forged"));
		assertThrows(AssistantModelException.class, () -> runtime.pinned(identity, consumer, "forged"));
		assertThrows(AssistantModelException.class, () -> runtime.authorizeInput(identity, consumer, "forged", "{}"));
		assertThrows(AssistantModelException.class,
				() -> runtime.invoke("forged", identity, consumer, null, "{}", () -> fail("must not invoke")));
		verifyNoMoreInteractions(jdbc, gateway, resolver, execution);
	}

}
