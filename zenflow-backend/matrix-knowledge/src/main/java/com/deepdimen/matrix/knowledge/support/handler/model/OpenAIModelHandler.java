package com.deepdimen.matrix.knowledge.support.handler.model;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.service.AiAssistantArkService;
import com.deepdimen.matrix.knowledge.service.AiAssistantGiteeService;
import com.deepdimen.matrix.knowledge.service.AiAssistantOpenRouterService;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiAssistantSiliconflowService;
import com.deepdimen.matrix.knowledge.service.AiFunctionAssistantService;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums;
import com.deepdimen.matrix.knowledge.support.handler.core.Base64FilterLogger;
import com.deepdimen.matrix.knowledge.support.handler.core.RestClientLoggingInterceptor;
import com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider;
import com.deepdimen.matrix.knowledge.support.rerank.RerankerScoringModel;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 支持 OpenAI 协议的模型处理器
 * <p>
 * 该类继承自 {@link AbstractModelBuildHandler}，负责构建与 OpenAI 协议兼容的各种 AI 模型服务。 它使用 Langchain4j
 * 库与 OpenAI API 进行交互。
 * </p>
 * <p>
 * 主要功能： 1. 构建各种类型的 OpenAI 模型（聊天、嵌入、重排序等） 2. 支持同步和流式响应 3. 支持 Function Calling 功能 4. 支持
 * JSON 输出模式 5. 兼容所有遵循 OpenAI API 协议的模型提供商
 * </p>
 *
 * @author caoyuan
 * @date 2024/09/29
 */
@Service
@RequiredArgsConstructor
public class OpenAIModelHandler extends AbstractModelBuildHandler {

	/**
	 * 聊天记忆顾问提供者
	 * <p>
	 * 用于管理对话历史，保持上下文的连贯性。 通过该提供者，助手服务可以记住之前的对话内容。
	 * </p>
	 */
	private final ChatMemoryAdvisorProvider chatMemoryAdvisorProvider;

	/**
	 * AI 知识库配置属性
	 * <p>
	 * 包含连接超时、重试次数、日志开关等全局配置。
	 * </p>
	 */
	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * RestClient 构建器（可选）
	 * <p>
	 * 用于自定义 HTTP 客户端配置，例如代理、超时等。
	 * </p>
	 */
	private final Optional<RestClient.Builder> restClientBuilderOptional;

	/**
	 * 聊天模型监听器列表
	 * <p>
	 * 用于监听模型的请求和响应事件，实现日志记录、计费等功能。
	 * </p>
	 */
	private final List<ChatModelListener> chatModelListenerList;

	/**
	 * 工具提供者
	 * <p>
	 * 用于 Function Calling 功能，提供 AI 可以调用的工具/函数。
	 * </p>
	 */
	private final ToolProvider toolProvider;

	/**
	 * 判断当前处理器是否支持给定的 AI 模型
	 * <p>
	 * 对于 OpenAI 协议，默认支持所有模型。 这意味着任何遵循 OpenAI API 协议的模型提供商都可以使用此处理器。
	 * </p>
	 * @param aiModel AI 模型实体
	 * @return 如果支持则返回 true，否则返回 false。目前始终返回 true。
	 */
	@Override
	public boolean support(AiModelEntity aiModel) {
		return true; // OpenAI 协议默认支持
	}

	/**
	 * JDK HTTP 客户端构建器实例
	 * <p>
	 * 默认使用 HTTP/1.1 版本，主要是为了兼容 vLLM 等一些 可能不完全支持 HTTP/2 的模型服务器。
	 * </p>
	 */
	private final JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
		.httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1));

	/**
	 * 构建同步调用的聊天助手服务
	 * <p>
	 * 创建一个 {@link OpenAiChatModel} 实例和一个封装了该模型的 {@link AiAssistantService}。 同步调用意味着会阻塞等待
	 * AI 返回完整的响应。
	 * </p>
	 * @param aiModel AI 模型配置实体，包含 API 密钥、基础 URL、模型参数等
	 * @return 包含 {@link ChatModel} 和 {@link AiAssistantService} 的 Pair 对象
	 */
	@Override
	public Pair<ChatModel, AiAssistantService> buildAiAssistant(AiModelEntity aiModel) {
		// 配置并构建 OpenAiChatModel
		OpenAiChatModel.OpenAiChatModelBuilder openAiChatModelBuilder = OpenAiChatModel.builder()//
			.apiKey(aiModel.getApiKey())// API 密钥
			.baseUrl(aiModel.getBaseUrl())// 模型服务的基础 URL
			.modelName(aiModel.getModelName())// 模型名称
			.temperature(aiModel.getTemperature())// 温度参数，控制生成文本的随机性
			.topP(aiModel.getTopP())// Top-P 参数，控制核心采样的概率阈值
			.maxCompletionTokens(aiModel.getResponseLimit())// 最大响应 token 数
			.listeners(chatModelListenerList)// 添加聊天模型监听器
			.timeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())) // 连接超时时间
			.logRequests(aiKnowledgeProperties.isShowLog())// 是否记录请求日志
			.logResponses(aiKnowledgeProperties.isShowLog())// 是否记录响应日志
			.maxRetries(aiKnowledgeProperties.getMaxRetry())// 最大重试次数
			.httpClientBuilder(jdkHttpClientBuilder)
			.logger(new Base64FilterLogger(OpenAIModelHandler.class))
			.returnThinking(true);

		// 给请求参数中增加扩展参数
		if (StrUtil.isNotBlank(aiModel.getExtData())) {
			openAiChatModelBuilder.defaultRequestParameters(OpenAiChatRequestParameters.builder()
				.customParameters(JSONUtil.toBean(aiModel.getExtData(), Map.class))
				.build());
		}

		OpenAiChatModel chatModel = openAiChatModelBuilder.build();

		// 使用 AiServices 构建 AiAssistantService
		return Pair.of(chatModel, AiServices.builder(AiAssistantService.class)
			.chatModel(chatModel) // 设置聊天模型
			.chatMemoryProvider(chatMemoryAdvisorProvider) // 设置聊天记忆提供者
			.build());
	}

	/**
	 * 构建流式调用的聊天助手服务
	 * <p>
	 * 创建一个 {@link OpenAiStreamingChatModel} 实例和一个封装了该模型的
	 * {@link AiStreamAssistantService}。 流式响应允许客户端实时接收 AI 生成的文本片段，提供更好的用户体验。
	 * </p>
	 * <p>
	 * 注意：流式模型通常不支持 Langchain4j 内置的重试机制， 重试需要在应用层面处理。
	 * </p>
	 * @param aiModel AI 模型配置实体
	 * @return 包含 {@link StreamingChatModel} 和 {@link AiStreamAssistantService} 的 Pair 对象
	 */
	@Override
	public Pair<StreamingChatModel, AiStreamAssistantService> buildAiStreamAssistant(AiModelEntity aiModel) {
		// 配置并构建 OpenAiStreamingChatModel
		OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder streamingChatModelBuilder = OpenAiStreamingChatModel
			.builder()//
			.apiKey(aiModel.getApiKey())// API 密钥
			.baseUrl(aiModel.getBaseUrl())// 模型服务的基础 URL
			.modelName(aiModel.getModelName())// 模型名称
			.temperature(aiModel.getTemperature())// 温度参数
			.topP(aiModel.getTopP())// Top-P 参数
			// .maxTokens(aiModel.getResponseLimit())// 最大生成 token 数，部分模型已弃用该参数
			.maxCompletionTokens(aiModel.getResponseLimit())// 最大响应 token 数
			.listeners(chatModelListenerList)// 添加聊天模型监听器
			.timeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())) // 连接超时时间
			.logRequests(aiKnowledgeProperties.isShowLog())// 是否记录请求日志
			.logResponses(aiKnowledgeProperties.isShowLog())// 是否记录响应日志
			.logger(new Base64FilterLogger(OpenAIModelHandler.class))
			.httpClientBuilder(jdkHttpClientBuilder)
			.returnThinking(true);
		// 注意：流式模型通常不支持 Langchain4j 内置的重试机制，重试需要在应用层面处理

		// 给请求参数中增加扩展参数
		if (StrUtil.isNotBlank(aiModel.getExtData())) {
			streamingChatModelBuilder.defaultRequestParameters(OpenAiChatRequestParameters.builder()
				.customParameters(JSONUtil.toBean(aiModel.getExtData(), Map.class))
				.build());
		}

		OpenAiStreamingChatModel streamingChatModel = streamingChatModelBuilder.build();

		// 使用 AiServices 构建 AiStreamAssistantService
		return Pair.of(streamingChatModel,
				AiServices.builder(AiStreamAssistantService.class)
					.streamingChatModel(streamingChatModel) // 设置流式聊天模型
					.chatMemoryProvider(chatMemoryAdvisorProvider) // 设置聊天记忆提供者
					.build());
	}

	/**
	 * 构建无记忆的流式聊天助手服务
	 * <p>
	 * 创建一个 {@link OpenAiStreamingChatModel} 实例和一个封装了该模型的
	 * {@link AiNoMemoryStreamAssistantService}。 与普通的流式助手不同，此服务不保存对话历史，每次请求都是独立的。
	 * </p>
	 * <p>
	 * 适用场景：一次性问答、单轮对话、不需要上下文的任务等。
	 * </p>
	 * @param aiModel AI 模型配置实体
	 * @return 包含 {@link StreamingChatModel} 和 {@link AiNoMemoryStreamAssistantService} 的
	 * Pair 对象
	 */
	@Override
	public Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> buildAiNoMemoryStreamAssistant(
			AiModelEntity aiModel) {
		// 配置并构建 OpenAiStreamingChatModel (与 buildAiStreamAssistant 类似)
		OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder streamingChatModelBuilder = OpenAiStreamingChatModel
			.builder()//
			.apiKey(aiModel.getApiKey())//
			.baseUrl(aiModel.getBaseUrl())//
			.modelName(aiModel.getModelName())//
			.temperature(aiModel.getTemperature())//
			.topP(aiModel.getTopP())//
			// .maxTokens(aiModel.getResponseLimit())// 最大生成 token 数 (过期，但部分模型仍支持)
			.maxCompletionTokens(aiModel.getResponseLimit())//
			.listeners(chatModelListenerList)//
			.timeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout()))
			.logRequests(aiKnowledgeProperties.isShowLog())//
			.logResponses(aiKnowledgeProperties.isShowLog())//
			.httpClientBuilder(jdkHttpClientBuilder)
			.returnThinking(true);

		// 给请求参数中增加扩展参数
		if (StrUtil.isNotBlank(aiModel.getExtData())) {
			streamingChatModelBuilder.defaultRequestParameters(OpenAiChatRequestParameters.builder()
				.customParameters(JSONUtil.toBean(aiModel.getExtData(), Map.class))
				.build());
		}

		OpenAiStreamingChatModel streamingChatModel = streamingChatModelBuilder.build();

		// 使用 AiServices 构建 AiNoMemoryStreamAssistantService (不设置 chatMemoryProvider)
		return Pair.of(streamingChatModel,
				AiServices.builder(AiNoMemoryStreamAssistantService.class)
					.streamingChatModel(streamingChatModel)
					.build());
	}

	/**
	 * 构建支持 Function Calling 的流式聊天助手服务
	 * <p>
	 * 创建一个 {@link OpenAiStreamingChatModel} 实例和一个封装了该模型及工具提供者的
	 * {@link AiFunctionAssistantService}。 Function Calling 允许 AI
	 * 模型调用预定义的函数/工具，实现更复杂的交互逻辑。
	 * </p>
	 * <p>
	 * 使用场景：查询数据库、调用外部 API、执行计算任务等。
	 * </p>
	 * @param aiModel AI 模型配置实体
	 * @return 包含 {@link StreamingChatModel} 和 {@link AiFunctionAssistantService} 的 Pair
	 * 对象
	 */
	@Override
	public Pair<StreamingChatModel, AiFunctionAssistantService> buildAiFunctionAssistant(AiModelEntity aiModel) {
		// 配置并构建 OpenAiStreamingChatModel (与 buildAiStreamAssistant 类似)
		OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder streamingChatModelBuilder = OpenAiStreamingChatModel
			.builder()//
			.apiKey(aiModel.getApiKey())//
			.baseUrl(aiModel.getBaseUrl())//
			.modelName(aiModel.getModelName())//
			.temperature(aiModel.getTemperature())//
			.topP(aiModel.getTopP())//
			.maxCompletionTokens(aiModel.getResponseLimit())//
			.listeners(chatModelListenerList)//
			.timeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout()))
			.logRequests(aiKnowledgeProperties.isShowLog())//
			.logResponses(aiKnowledgeProperties.isShowLog())//
			.httpClientBuilder(jdkHttpClientBuilder)
			.returnThinking(true);

		// 给请求参数中增加扩展参数
		if (StrUtil.isNotBlank(aiModel.getExtData())) {
			streamingChatModelBuilder.defaultRequestParameters(OpenAiChatRequestParameters.builder()
				.customParameters(JSONUtil.toBean(aiModel.getExtData(), Map.class))
				.build());
		}

		OpenAiStreamingChatModel streamingChatModel = streamingChatModelBuilder.build();

		// 使用 AiServices 构建 AiFunctionAssistantService
		return Pair.of(streamingChatModel,
				AiServices.builder(AiFunctionAssistantService.class)
					.streamingChatModel(streamingChatModel) // 设置流式聊天模型
					.toolProvider(toolProvider) // 设置工具提供者，用于 Function Calling
					.build());
	}

	/**
	 * 构建支持 JSON 输出模式的聊天助手服务
	 * <p>
	 * 创建一个配置为强制 JSON 输出的 {@link OpenAiChatModel} 实例和一个封装了该模型的 {@link AiAssistantService}。
	 * 这种模式确保 AI 输出的内容是结构化的 JSON 格式，方便程序解析和处理。
	 * </p>
	 * <p>
	 * 特点： 1. 开启 strictJsonSchema，强制使用 JSON Schema（如果模型支持） 2. 根据模型提供商设置适当的响应格式 3.
	 * 返回响应格式类型，便于后续处理
	 * </p>
	 * @param aiModel AI 模型配置实体
	 * @return 包含 {@link ChatModel}、{@link AiAssistantService} 和响应格式类型字符串的 Triple 对象
	 */
	@Override
	public Triple<ChatModel, AiAssistantService, String> buildAiJSONAssistant(AiModelEntity aiModel) {
		// 开始配置 OpenAiChatModel
		OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()//
			.apiKey(aiModel.getApiKey())//
			.baseUrl(aiModel.getBaseUrl())//
			.modelName(aiModel.getModelName())//
			.temperature(aiModel.getTemperature())//
			.topP(aiModel.getTopP())//
			.maxCompletionTokens(aiModel.getResponseLimit())//
			.listeners(chatModelListenerList)//
			.strictJsonSchema(true)// 强制使用 JSON Schema (如果模型支持)
			.timeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout()))
			.logRequests(aiKnowledgeProperties.isShowLog())//
			.logResponses(aiKnowledgeProperties.isShowLog())//
			.httpClientBuilder(jdkHttpClientBuilder)
			.maxRetries(aiKnowledgeProperties.getMaxRetry())
			.returnThinking(true);

		// 根据模型提供商获取并设置响应格式 (例如 "json_object")
		String formatType = ModelProviderFormatEnums.getFormat(aiModel.getProvider());
		builder.responseFormat(formatType);

		// 给请求参数中增加扩展参数
		if (StrUtil.isNotBlank(aiModel.getExtData())) {
			builder.defaultRequestParameters(OpenAiChatRequestParameters.builder()
				.customParameters(JSONUtil.toBean(aiModel.getExtData(), Map.class))
				.build());
		}

		// 构建 OpenAiChatModel
		OpenAiChatModel chatModel = builder.build();

		// 使用 AiServices 构建 AiAssistantService
		return Triple.of(chatModel, AiServices.builder(AiAssistantService.class)
			.chatModel(chatModel) // 设置聊天模型
			.chatMemoryProvider(chatMemoryAdvisorProvider) // 设置聊天记忆提供者
			.build(), formatType); // 返回响应格式类型
	}

	/**
	 * 构建文本嵌入模型
	 * <p>
	 * 创建一个 {@link OpenAiEmbeddingModel} 实例，用于将文本转换为向量表示。 这些向量可以用于： 1. 语义搜索：找到与查询最相关的文档 2.
	 * 文本相似度计算：判断两段文本的语义相似度 3. 文本分类和聚类
	 * </p>
	 * @param model AI 模型配置实体，可能包含向量维度设置
	 * @return 支持维度感知的嵌入模型实例
	 */
	@Override
	public DimensionAwareEmbeddingModel buildEmbeddingModel(AiModelEntity model) {
		// 配置并构建 OpenAiEmbeddingModel
		OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder modelBuilder = OpenAiEmbeddingModel.builder()
			.apiKey(model.getApiKey())// API 密钥
			.baseUrl(model.getBaseUrl())// 模型服务的基础 URL
			.maxRetries(aiKnowledgeProperties.getMaxRetry())// 最大重试次数
			.timeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())) // 连接超时时间
			.logger(new Base64FilterLogger(OpenAIModelHandler.class))
			.logRequests(aiKnowledgeProperties.isShowLog())// 向量模型只记录入参，出参都是数组没参考意义
			.httpClientBuilder(jdkHttpClientBuilder) // 使用 JDK HTTP 1.1客户端
			.modelName(model.getModelName());// 嵌入模型名称

		// 如果模型设置维度，则添加到模型构建器中
		if (Objects.nonNull(model.getDimensions())) {
			modelBuilder.dimensions(model.getDimensions());
		}

		return modelBuilder.build();
	}

	/**
	 * 构建重排序评分模型
	 * <p>
	 * 使用自定义的 {@link RerankerScoringModel} 来实现，它通常用于对检索结果进行重新排序。 重排序模型可以： 1.
	 * 根据查询和文档的相关性重新评分 2. 提高检索结果的准确性 3. 优化排序结果，将最相关的内容排在前面
	 * </p>
	 * @param model AI 模型实体对象，包含重排序模型的配置
	 * @return 构建好的评分模型 {@link ScoringModel} 对象
	 */
	@Override
	public ScoringModel buildScoringModel(AiModelEntity model) {
		// 配置并构建 RerankerScoringModel
		return RerankerScoringModel.builder()
			.apiKey(model.getApiKey())// API 密钥
			.baseUrl(model.getBaseUrl())// 模型服务的基础 URL
			.maxRetries(aiKnowledgeProperties.getMaxRetry())// 最大重试次数
			.timeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())) // 连接超时时间
			.modelName(model.getModelName()) // 评分模型名称
			.build();
	}

	/**
	 * 构建文件处理相关的助手服务
	 * <p>
	 * 使用 Spring 的 {@link HttpServiceProxyFactory} 创建一个 HTTP 客户端代理。 目前主要用于 Siliconflow
	 * 等服务的图像生成或文件处理。
	 * </p>
	 * <p>
	 * 注意：方法名 `buildFileAssistant` 可能存在拼写错误，应为 `buildFileAssistant`。 此方法似乎特定于 Siliconflow
	 * 或类似的文件处理服务，而不是通用的 OpenAI 文件处理。
	 * </p>
	 * @param model AI 模型配置实体，可能包含特定于文件服务的配置
	 * @return {@link AiAssistantSiliconflowService} 代理实例
	 */
	@Override
	public AiAssistantSiliconflowService buildFileAssistant(AiModelEntity model) {
		// 配置 JDK HTTP 客户端工厂
		JdkClientHttpRequestFactory jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory();
		jdkClientHttpRequestFactory.setReadTimeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())); // 设置读取超时

		// 构建 RestClient，配置基础 URL 和认证头
		RestClient.Builder builder = restClientBuilderOptional.orElseGet(RestClient::builder) // 获取或创建
			// RestClient.Builder
			.requestFactory(jdkClientHttpRequestFactory) // 设置请求工厂
			.baseUrl(model.getBaseUrl()) // 设置服务基础 URL
			.defaultHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", model.getApiKey()));// 设置认证头

		if (aiKnowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		// 创建 HttpServiceProxyFactory
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();

		// 创建 AiSiliconflowAssistantService 接口的代理客户端
		return factory.createClient(AiAssistantSiliconflowService.class);
	}

	/**
	 * 构建OpenRouter助手服务
	 * @param model AI模型实体
	 * @return OpenRouter助手服务实例
	 */
	@Override
	public AiAssistantOpenRouterService buildOpenRouterAssistant(AiModelEntity model) {
		// 配置 JDK HTTP 客户端工厂
		JdkClientHttpRequestFactory jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory();
		jdkClientHttpRequestFactory.setReadTimeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())); // 设置读取超时

		// 构建 RestClient，配置基础 URL 和认证头
		RestClient.Builder builder = restClientBuilderOptional.orElseGet(RestClient::builder) // 获取或创建
			// RestClient.Builder
			.requestFactory(jdkClientHttpRequestFactory) // 设置请求工厂
			.baseUrl(model.getBaseUrl()) // 设置服务基础 URL
			.defaultHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", model.getApiKey()));// 设置认证头

		if (aiKnowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		// 创建 HttpServiceProxyFactory
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();

		// 创建 AiOpenRouterAssistantService 接口的代理客户端
		return factory.createClient(AiAssistantOpenRouterService.class);
	}

	/**
	 * 构建ARK助手服务
	 * @param model AI模型实体
	 * @return ARK助手服务实例
	 */
	@Override
	public AiAssistantArkService buildArkAssistant(AiModelEntity model) {

		// 配置 JDK HTTP 客户端工厂
		JdkClientHttpRequestFactory jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory();
		jdkClientHttpRequestFactory.setReadTimeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())); // 设置读取超时

		// 构建 RestClient，配置基础 URL 和认证头
		RestClient.Builder builder = restClientBuilderOptional.orElseGet(RestClient::builder) // 获取或创建
			// RestClient.Builder
			.requestFactory(jdkClientHttpRequestFactory) // 设置请求工厂
			.baseUrl(model.getBaseUrl()) // 设置服务基础 URL
			.defaultHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", model.getApiKey()));// 设置认证头

		if (aiKnowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		// 创建 HttpServiceProxyFactory
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();

		// 创建 AiOpenRouterAssistantService 接口的代理客户端
		return factory.createClient(AiAssistantArkService.class);
	}

	/**
	 * 构建Gitee AI助手服务
	 * <p>
	 * 创建用于访问Gitee AI平台的HTTP客户端代理服务。 该服务支持音频转文本等多模态AI能力。
	 * </p>
	 * <p>
	 * 配置说明： 1. 从AiModelEntity中获取基础URL和API密钥 2. 设置Bearer Token认证 3. 配置读取超时时间 4. 可选启用请求日志记录
	 * </p>
	 * @param model AI模型配置实体，包含Gitee AI的连接信息
	 * @return {@link AiAssistantGiteeService} 代理实例
	 */
	@Override
	public AiAssistantGiteeService buildGiteeAssistant(AiModelEntity model) {
		// 配置 JDK HTTP 客户端工厂
		JdkClientHttpRequestFactory jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory();
		jdkClientHttpRequestFactory.setReadTimeout(Duration.ofSeconds(aiKnowledgeProperties.getConnectTimeout())); // 设置读取超时

		// 构建 RestClient，配置基础 URL 和认证头
		RestClient.Builder builder = restClientBuilderOptional.orElseGet(RestClient::builder) // 获取或创建
			// RestClient.Builder
			.requestFactory(jdkClientHttpRequestFactory) // 设置请求工厂
			.baseUrl(model.getBaseUrl()) // 设置服务基础 URL
			.defaultHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", model.getApiKey()));// 设置认证头

		if (aiKnowledgeProperties.isShowLog()) {
			builder.requestInterceptor(new RestClientLoggingInterceptor());
		}

		// 创建 HttpServiceProxyFactory
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
			.build();

		// 创建 AiAssistantGiteeService 接口的代理客户端
		return factory.createClient(AiAssistantGiteeService.class);
	}

}