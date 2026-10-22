package com.deepdimen.matrix.discover.assistant.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class AssistantProviderLoadBalancerConfiguration {

	@Bean("assistantProviderLoadBalancedWebClientBuilder")
	@LoadBalanced
	@ConditionalOnMissingBean(name = "assistantProviderLoadBalancedWebClientBuilder")
	WebClient.Builder assistantProviderLoadBalancedWebClientBuilder() {
		return WebClient.builder();
	}

}
