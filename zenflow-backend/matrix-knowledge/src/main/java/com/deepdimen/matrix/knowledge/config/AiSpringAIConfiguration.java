package com.deepdimen.matrix.knowledge.config;

import com.deepdimen.matrix.common.core.factory.YamlPropertySourceFactory;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.service.AiJinaReaderAssistantService;
import com.deepdimen.matrix.knowledge.service.AiMarkitdownAssistantService;
import com.deepdimen.matrix.knowledge.service.AiMineruAssistantService;
import com.deepdimen.matrix.knowledge.service.AiOCRAssistantService;
import com.deepdimen.matrix.knowledge.service.MiIotHomeAssistantService;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.handler.core.RestClientLoggingInterceptor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Spring AI核心配置类 配置AI相关的基础设施，包括聊天记忆、令牌计数、各种AI助手服务
 *
 * @author caoyuan
 * @date 2024/08/08
 */
@Configuration(proxyBeanMethods = false)
@PropertySource(value = "classpath:lowcode/magic-api.yml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({ AiKnowledgeProperties.class })
public class AiSpringAIConfiguration {

	/**
	 * 配置消息窗口聊天记忆 用于在对话过程中保存历史消息，实现上下文感知的对话能力
	 * @param aiKnowledgeProperties AI知识库配置属性
	 * @return 配置了最大消息数量的消息窗口聊天记忆实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public MessageWindowChatMemory messageWindowChatMemory(AiKnowledgeProperties aiKnowledgeProperties) {
		return MessageWindowChatMemory.builder().maxMessages(aiKnowledgeProperties.getChatHistoryWindowSize()).build();
	}

	/**
	 * 配置令牌计数估算器 使用JTokkit实现，用于估算文本的令牌数量，帮助控制API调用成本
	 * @return JTokkit令牌计数估算器实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public TokenCountEstimator tokenCountEstimator() {
		return new JTokkitTokenCountEstimator();
	}

	/**
	 * 配置OCR（光学字符识别）助手服务 集成CnOCR服务，用于从图片中提取文字内容
	 * @param properties AI知识库配置属性
	 * @param knowledgeProperties 知识库配置属性（用于日志控制）
	 * @return OCR助手服务代理实例
	 */
	@Bean
	public AiOCRAssistantService aiOCRAssistantService(AiKnowledgeProperties properties,
			AiKnowledgeProperties knowledgeProperties) {
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.getCnocr().getBaseUrl());

		if (knowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();
		return factory.createClient(AiOCRAssistantService.class);
	}

	/**
	 * 配置小米IoT智能家居助手服务 用于控制和管理小米智能家居设备
	 * @param restClientBuilder REST客户端构建器
	 * @param knowledgeProperties 知识库配置属性
	 * @return 小米IoT家居助手服务代理实例
	 */
	@Bean
	public MiIotHomeAssistantService homeAssistantService(RestClient.Builder restClientBuilder,
			AiKnowledgeProperties knowledgeProperties) {
		RestClient.Builder builder = restClientBuilder.requestFactory(new JdkClientHttpRequestFactory())
			.baseUrl(knowledgeProperties.getMiIot().getBaseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION,
					String.format("Bearer %s", knowledgeProperties.getMiIot().getApiKey()));

		if (knowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();
		return factory.createClient(MiIotHomeAssistantService.class);
	}

	/**
	 * 配置Markitdown文档解析助手服务 用于将各种格式的文档转换为Markdown格式
	 * @param restClientBuilder REST客户端构建器
	 * @param knowledgeProperties 知识库配置属性
	 * @return Markitdown助手服务代理实例
	 */
	@Bean
	public AiMarkitdownAssistantService markitdownAssistantService(RestClient.Builder restClientBuilder,
			AiKnowledgeProperties knowledgeProperties) {
		RestClient.Builder builder = restClientBuilder.requestFactory(new JdkClientHttpRequestFactory())
			.baseUrl(knowledgeProperties.getMarkitdown().getBaseUrl());

		if (knowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();
		return factory.createClient(AiMarkitdownAssistantService.class);
	}

	/**
	 * 配置Mineru PDF解析助手服务 用于高精度解析PDF文档，支持复杂版式和表格的提取
	 * @param restClientBuilder REST客户端构建器
	 * @param knowledgeProperties 知识库配置属性
	 * @return Mineru助手服务代理实例
	 */
	@Bean
	public AiMineruAssistantService mineruAssistantService(RestClient.Builder restClientBuilder,
			AiKnowledgeProperties knowledgeProperties) {
		RestClient.Builder clientBuilder = restClientBuilder.requestFactory(new JdkClientHttpRequestFactory())
			.baseUrl(knowledgeProperties.getMineru().getBaseUrl());

		if (knowledgeProperties.isShowLog()) {
			clientBuilder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		RestClient client = clientBuilder.build();
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build();
		return factory.createClient(AiMineruAssistantService.class);
	}

	/**
	 * 配置Jina Reader网页内容提取助手服务 用于从URL中提取干净的文本内容，去除广告和无关元素
	 * @param restClientBuilder REST客户端构建器
	 * @param knowledgeProperties 知识库配置属性
	 * @return Jina Reader助手服务代理实例
	 */
	@Bean
	public AiJinaReaderAssistantService jinaReaderAssistantService(RestClient.Builder restClientBuilder,
			AiKnowledgeProperties knowledgeProperties) {
		RestClient.Builder builder = restClientBuilder.requestFactory(new JdkClientHttpRequestFactory())
			.baseUrl("https://r.jina.ai");

		if (knowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();
		return factory.createClient(AiJinaReaderAssistantService.class);
	}

}
