package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.support.flow.constants.ExecutionStatusEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiLLMNode;
import com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LLM节点处理器 负责处理流程中的大语言模型节点，包括消息模板渲染、模型调用和结果处理
 */
@Slf4j
@Component(NodeTypeConstants.LLM)
@RequiredArgsConstructor
public class LLMNodeProcessor extends AbstractNodeProcessor {

	/**
	 * 模板引擎，用于变量替换
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	private final ChatMemoryAdvisorProvider chatMemoryAdvisorProvider;

	private final ModelProvider modelProvider;

	private final RemoteFileService remoteFileService;

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 验证节点配置
			AiLLMNode config = validateNodeConfig(node);

			// 获取输入参数并处理消息模板
			Dict variables = getInputVariables(node, context);
			List<ChatMessage> chatMessages = processChatMessages(config.getMessages(), variables);

			// 根据模型类型（普通/视觉）调用不同的处理逻辑
			boolean stream = context.isStream();
			if (YesNoEnum.YES.getCode().equals(config.getModelConfig().getIsVision())) {
				if (stream) {
					return processVisionModelStream(chatMessages, config.getModelConfig(), context, node);
				}
				else {
					return processVisionModel(chatMessages, config.getModelConfig(), context);
				}
			}
			else if (stream) {
				return processRegularModel(chatMessages, config.getModelConfig(), context, node);
			}
			else {
				return processNonStreamModel(chatMessages, config.getModelConfig(), context, node);
			}
		}
		catch (Exception e) {
			throw FlowException.nodeError(node.getId(), "[LLM节点] -> " + e.getMessage());
		}
	}

	/**
	 * 验证LLM节点配置的有效性
	 * @param node 节点定义
	 * @return 有效的LLM节点配置
	 */
	private AiLLMNode validateNodeConfig(AiNodeDefinition node) {
		// 检查LLM节点配置
		AiLLMNode config = node.getLlmParams();
		if (config == null) {
			throw FlowException.invalidParam("LLM节点配置无效");
		}

		// 检查模型配置
		AiLLMNode.Model modelConfig = config.getModelConfig();
		if (modelConfig == null) {
			throw FlowException.invalidParam("LLM节点模型配置无效");
		}

		// 检查消息配置
		List<AiLLMNode.Message> messages = config.getMessages();
		if (messages == null || messages.isEmpty()) {
			throw FlowException.invalidParam("LLM节点消息配置无效");
		}

		return config;
	}

	/**
	 * 处理聊天消息，替换变量并转换为ChatMessage对象
	 * @param messages 原始消息配置
	 * @param variables 变量字典
	 * @return 处理后的ChatMessage列表
	 */
	private List<ChatMessage> processChatMessages(List<AiLLMNode.Message> messages, Dict variables) {
		return messages.stream().map(msg -> {
			// 使用模板引擎渲染消息内容，替换变量
			String renderedContent = engine.getTemplate(msg.getContent()).render(variables);

			// 根据消息角色创建对应类型的ChatMessage
			if (msg.getRole().equals(ChatMessageType.AI.name())) {
				return AiMessage.aiMessage(renderedContent);
			}

			if (msg.getRole().equals(ChatMessageType.SYSTEM.name())) {
				return SystemMessage.from(renderedContent);
			}
			return UserMessage.from(renderedContent);
		}).collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * 处理视觉模型请求。 本方法内部将获取视觉AI助手，解码参数，获取远程文件，添加图片消息至聊天消息列表， 并调用模型处理聊天消息，最终返回格式化的处理结果。
	 * @param chatMessages 处理后的聊天消息列表
	 * @param modelConfig 视觉模型配置信息
	 * @param context 流程上下文
	 * @return 处理后的结果字典
	 * @throws IOException 当获取远程文件或处理流时发生异常
	 */
	private Dict processVisionModel(List<ChatMessage> chatMessages, AiLLMNode.Model modelConfig,
			FlowContextHolder context) throws IOException {
		// 获取视觉AI助手
		Pair<ChatModel, AiAssistantService> aiVisionAssistant = modelProvider.getAiVisionAssistant();

		Object parameter = context.getParameter(modelConfig.getPicUrl());

		Response fileResponse = remoteFileService.getFile(parameter.toString());

		// 添加空图片消息（实际使用时应替换为实际图片）
		String encodeToString = Base64.getEncoder().encodeToString(fileResponse.body().asInputStream().readAllBytes());

		ArrayList<ChatMessage> messages = new ArrayList<>(chatMessages);
		messages.add(UserMessage.from(ImageContent.from(encodeToString, MediaType.IMAGE_PNG_VALUE)));
		// 调用模型并获取响应
		ChatResponse chatResponse = aiVisionAssistant.getKey().chat(messages);

		// 返回处理结果
		return formatResponse(chatResponse);
	}

	/**
	 * 处理视觉模型请求（流式）
	 * @param chatMessages 聊天消息列表
	 * @param modelConfig 模型配置信息
	 * @param context 流程上下文
	 * @param node 节点定义
	 * @return 处理结果字典
	 * @throws IOException 当获取远程文件或处理流时发生异常
	 */
	private Dict processVisionModelStream(List<ChatMessage> chatMessages, AiLLMNode.Model modelConfig,
			FlowContextHolder context, AiNodeDefinition node) throws IOException {

		// 获取流式视觉模型
		Pair<StreamingChatModel, AiStreamAssistantService> assistantServicePair = modelProvider
			.getAiVisionStreamAssistant();

		// 获取并处理图片
		Object parameter = context.getParameter(modelConfig.getPicUrl());
		Response fileResponse = remoteFileService.getFile(parameter.toString());
		String encodeToString = Base64.getEncoder().encodeToString(fileResponse.body().asInputStream().readAllBytes());

		// 分离 SystemMessage 和其他消息（参考 processRegularModel 的处理方式）
		Optional<SystemMessage> systemMsg = chatMessages.stream()
			.filter(msg -> msg instanceof SystemMessage)
			.map(msg -> (SystemMessage) msg)
			.findFirst();

		// 构建完整消息列表：SystemMessage + 其他非 system 消息 + 图片消息
		List<ChatMessage> allMessages = new ArrayList<>();
		systemMsg.ifPresent(allMessages::add);
		chatMessages.stream().filter(msg -> !(msg instanceof SystemMessage)).forEach(allMessages::add);
		allMessages.add(UserMessage.from(ImageContent.from(encodeToString, MediaType.IMAGE_PNG_VALUE)));

		CountDownLatch latch = new CountDownLatch(1);
		final ChatResponse[] finalResponse = new ChatResponse[1];

		assistantServicePair.getKey().chat(allMessages, new StreamingChatResponseHandler() {
			@Override
			public void onPartialResponse(String msg) {
				context.sendStreamContent(msg);
			}

			@Override
			public void onCompleteResponse(ChatResponse chatResponse) {
				finalResponse[0] = chatResponse;

				// 使用统一的流式结束方法，自动处理节点状态和token统计
				Integer tokens = (chatResponse != null && chatResponse.tokenUsage() != null)
						? chatResponse.tokenUsage().totalTokenCount() : null;
				context.sendStreamEnd(node, tokens);

				latch.countDown();
			}

			@Override
			public void onError(Throwable throwable) {
				log.error("流式视觉模型聊天发生错误", throwable);
				context.sendStreamError(throwable.getMessage());
				latch.countDown();
			}
		});

		try {
			latch.await(5, TimeUnit.MINUTES);
			return finalResponse[0] != null ? formatResponse(finalResponse[0])
					: Dict.create()
						.set(FlowConstant.CONTENT, "")
						.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Dict.create().set(FlowConstant.CONTENT, "").set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
	}

	/**
	 * 处理普通文本模型请求（流式）
	 * @param chatMessages 聊天消息列表
	 * @param modelConfig 模型配置信息
	 * @param context 流程上下文
	 * @param node 节点定义
	 * @return 处理结果字典
	 */
	private Dict processRegularModel(List<ChatMessage> chatMessages, AiLLMNode.Model modelConfig,
			FlowContextHolder context, AiNodeDefinition node) {

		Pair<StreamingChatModel, AiStreamAssistantService> assistantServicePair = modelProvider
			.getAiStreamAssistant(modelConfig.getModel());

		ChatMemory chatMemory = chatMemoryAdvisorProvider.get(context.getConversationId());

		// 分离 SystemMessage 和其他消息（参考 JsonChatRule 的处理方式）
		Optional<SystemMessage> systemMsg = chatMessages.stream()
			.filter(msg -> msg instanceof SystemMessage)
			.map(msg -> (SystemMessage) msg)
			.findFirst();

		// 将非 system 消息添加到 chatMemory
		chatMessages.stream().filter(msg -> !(msg instanceof SystemMessage)).forEach(chatMemory::add);

		// 构建完整消息列表：SystemMessage + chatMemory 中的所有消息
		List<ChatMessage> allMessages = new ArrayList<>();
		systemMsg.ifPresent(allMessages::add);
		allMessages.addAll(chatMemory.messages());

		CountDownLatch latch = new CountDownLatch(1);
		final ChatResponse[] finalResponse = new ChatResponse[1];

		assistantServicePair.getKey().chat(allMessages, new StreamingChatResponseHandler() {
			@Override
			public void onPartialResponse(String msg) {
				context.sendStreamContent(msg);
			}

			@Override
			public void onCompleteResponse(ChatResponse chatResponse) {
				finalResponse[0] = chatResponse;

				if (chatResponse != null) {
					chatMemory.add(chatResponse.aiMessage());
				}

				// 使用统一的流式结束方法，自动处理节点状态和token统计
				Integer tokens = (chatResponse != null && chatResponse.tokenUsage() != null)
						? chatResponse.tokenUsage().totalTokenCount() : null;
				context.sendStreamEnd(node, tokens);

				latch.countDown();
			}

			@Override
			public void onError(Throwable throwable) {
				log.error("流式聊天发生错误", throwable);
				context.sendStreamError(throwable.getMessage());
				latch.countDown();
			}
		});

		try {
			latch.await(5, TimeUnit.MINUTES);
			return finalResponse[0] != null ? formatResponse(finalResponse[0])
					: Dict.create()
						.set(FlowConstant.CONTENT, "")
						.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Dict.create().set(FlowConstant.CONTENT, "").set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
	}

	/**
	 * 格式化模型响应结果
	 * @param chatResponse 模型响应
	 * @return 格式化后的结果字典
	 */
	private Dict formatResponse(ChatResponse chatResponse) {
		// 提取响应信息
		Integer totalTokens = chatResponse.tokenUsage().totalTokenCount();
		String content = chatResponse.aiMessage().text();
		String role = chatResponse.aiMessage().type().name();

		// 构建结果字典
		return Dict.create()
			.set(FlowConstant.CONTENT, content)
			.set(FlowConstant.ROLE, role)
			.set(FlowConstant.TOKENS, totalTokens)
			.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
	}

	/**
	 * 处理非流式模型请求
	 * @param chatMessages 聊天消息列表
	 * @param modelConfig 模型配置信息
	 * @param context 流程上下文
	 * @param node 节点定义
	 * @return 处理结果字典
	 */
	private Dict processNonStreamModel(List<ChatMessage> chatMessages, AiLLMNode.Model modelConfig,
			FlowContextHolder context, AiNodeDefinition node) {
		// 获取非流式AI助手
		Pair<ChatModel, AiAssistantService> assistantServicePair = modelProvider.getAiAssistant(modelConfig.getModel());

		String userMessage = chatMessages.stream()
			.filter(chatMessage -> chatMessage.type().equals(ChatMessageType.USER))
			.findFirst()
			.map(chatMessage -> {
				UserMessage message = (UserMessage) chatMessage;
				return message.singleText();
			})
			.orElse(StrUtil.EMPTY);

		// 直接调用 AiAssistantService.chat()，内存由框架自动管理
		String response = assistantServicePair.getValue().chat(context.getConversationId(), userMessage);

		// 更新节点执行状态
		node.setStatus(ExecutionStatusEnums.SUCCESS.getValue());
		node.setDuration(context.getDuration());
		context.getExecutedNodes().add(node);

		// 返回简化的响应结果（不包含 token 信息）
		return Dict.create()
			.set(FlowConstant.CONTENT, response)
			.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
	}

}
