package com.deepdimen.matrix.assistant.provider.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.provider.web.InnerAssistantProviderController;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssistantProviderAutoConfigurationTest {

	@Test
	void standaloneProviderServiceGetsLocalRegistryAndInnerEndpoint() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.registerBean(ObjectMapper.class);
			context.register(AssistantProviderAutoConfiguration.class);
			context.refresh();
			assertNotNull(context.getBean(AssistantProviderRegistry.class));
			assertNotNull(context.getBean(InnerAssistantProviderController.class));
			assertEquals(0, context.getBean(AssistantProviderRegistry.class).providerIds().size());
		}
	}

}
