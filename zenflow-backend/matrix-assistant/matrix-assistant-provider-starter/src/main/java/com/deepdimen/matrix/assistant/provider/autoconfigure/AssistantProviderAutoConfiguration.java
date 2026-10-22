package com.deepdimen.matrix.assistant.provider.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.DelegationReplayGuard;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalAuthProperties;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.deepdimen.matrix.assistant.provider.security.DenyAssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.provider.security.HmacAssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.provider.security.InMemoryAssistantDelegationReplayGuard;
import com.deepdimen.matrix.assistant.provider.web.InnerAssistantProviderController;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableConfigurationProperties(AssistantInternalAuthProperties.class)
public class AssistantProviderAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AssistantProviderRegistry assistantProviderRegistry(List<AssistantContextProvider> providers) {
		return new AssistantProviderRegistry(providers);
	}

	@Bean
	@ConditionalOnMissingBean
	AssistantInternalRequestHasher assistantInternalRequestHasher(ObjectMapper objectMapper) {
		return new AssistantInternalRequestHasher(objectMapper);
	}

	@Bean
	@ConditionalOnMissingBean(AssistantInternalCredentialService.class)
	@ConditionalOnProperty(prefix = "matrix.assistant.internal-auth", name = "secret")
	AssistantInternalCredentialService hmacAssistantInternalCredentialService(ObjectMapper objectMapper,
			AssistantInternalAuthProperties properties, DelegationReplayGuard replayGuard,
			@Value("${spring.application.name:matrix-assistant}") String workloadId) {
		if (properties.getWorkloadId() == null || properties.getWorkloadId().isBlank()) {
			properties.setWorkloadId(workloadId);
		}
		return new HmacAssistantInternalCredentialService(objectMapper, properties, replayGuard);
	}

	@Bean
	@ConditionalOnMissingBean(DelegationReplayGuard.class)
	DelegationReplayGuard assistantDelegationReplayGuard() {
		return new InMemoryAssistantDelegationReplayGuard();
	}

	@Bean
	@ConditionalOnMissingBean(AssistantInternalCredentialService.class)
	AssistantInternalCredentialService denyAssistantInternalCredentialService() {
		return new DenyAssistantInternalCredentialService();
	}

	@Bean
	@ConditionalOnMissingBean
	InnerAssistantProviderController innerAssistantProviderController(AssistantProviderRegistry registry,
			AssistantInternalCredentialService credentials, AssistantInternalRequestHasher requestHasher,
			@Value("${spring.application.name:matrix-assistant}") String audience) {
		return new InnerAssistantProviderController(registry, credentials, requestHasher, audience);
	}

}
