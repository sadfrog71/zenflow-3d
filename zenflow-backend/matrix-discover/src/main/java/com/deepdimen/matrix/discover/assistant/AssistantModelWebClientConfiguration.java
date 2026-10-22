package com.deepdimen.matrix.discover.assistant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class AssistantModelWebClientConfiguration {

	@Bean("assistantLoadBalancedWebClientBuilder")
	@LoadBalanced
	@ConditionalOnMissingBean(name = "assistantLoadBalancedWebClientBuilder")
	WebClient.Builder assistantLoadBalancedWebClientBuilder() {
		return WebClient.builder();
	}

}
