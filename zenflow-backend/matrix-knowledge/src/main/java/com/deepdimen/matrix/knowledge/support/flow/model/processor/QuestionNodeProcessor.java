package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiQuestionNode;
import com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

/**
 * 问题节点处理器
 *
 * @author caoyuan
 * @date 2025/03/04
 */
@Slf4j
@Component(NodeTypeConstants.QUESTION)
@RequiredArgsConstructor
public class QuestionNodeProcessor extends AbstractNodeProcessor {

	private final ModelProvider modelProvider;

	private final ChatMemoryAdvisorProvider chatMemoryAdvisorProvider;

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 节点配置判断
			AiQuestionNode config = node.getQuestionParams();
			if (config == null) {
				throw FlowException.invalidParam("问答节点配置无效");
			}
			AiQuestionNode.Model modelConfig = config.getModelConfig();
			if (modelConfig == null) {
				throw FlowException.invalidParam("问答节点模型配置无效");
			}
			List<AiQuestionNode.Category> categories = config.getCategories();
			if (categories == null || categories.isEmpty()) {
				throw FlowException.invalidParam("问答节点选项无效");
			}

			// 获取输入参数
			Dict variables = getInputVariables(node, context);

			Triple<ChatModel, AiAssistantService, String> jsonTriple = modelProvider
				.getAiJSONAssistant(modelConfig.getModel());

			// 获取会话历史
			ChatMemory chatMemory = chatMemoryAdvisorProvider.get(context.getConversationId());

			// 构建请求（用户消息会在 buildChatRequest 中添加到 chatMemory）
			ChatRequest chatRequest = buildChatRequest(variables, categories, jsonTriple.getRight(), chatMemory);

			ChatResponse chatResponse = jsonTriple.getLeft().chat(chatRequest);

			// 将 AI 响应添加到历史（用户消息已在 buildChatRequest 中添加）
			if (chatResponse != null && chatResponse.aiMessage() != null) {
				chatMemory.add(chatResponse.aiMessage());
			}

			log.info("Flow Question JSON: {}", chatResponse.aiMessage().text());

			String result = JSONUtil.parseObj(chatResponse.aiMessage().text()).getStr(FlowConstant.QUESTION_VALUE);
			// 查找匹配的分支
			int caseIndex = getCaseIndex(config, result);

			// 设置结果
			return Dict.create()
				.set(FlowConstant.INDEX, caseIndex)
				.set(FlowConstant.TOKENS, chatResponse.tokenUsage().totalTokenCount())
				.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());

		}
		catch (Exception e) {
			log.error("问答节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[问答节点] -> " + e.getMessage());
		}
	}

	private ChatRequest buildChatRequest(Dict input, List<AiQuestionNode.Category> categories, String jsonModel,
			ChatMemory chatMemory) {
		ChatRequest.Builder builder = ChatRequest.builder();
		JsonSchema jsonSchema = JsonSchema.builder()
			.name("ClassificationResult")
			.rootElement(JsonObjectSchema.builder().addProperties(new LinkedHashMap<>() {
				{
					JsonIntegerSchema valueJsonSchema = JsonIntegerSchema.builder()
						.description("根据问题描述，最合适的分类结果值")
						.build();
					put(FlowConstant.QUESTION_VALUE, valueJsonSchema);
				}
			}).required(FlowConstant.QUESTION_VALUE).additionalProperties(false).build())
			.build();

		// 构建系统消息
		ChatMessage systemMsg = systemMessage(PromptBuilder.render("flow-question-system-json.st"));

		// 构建用户消息
		ChatMessage userMsg;
		if (Objects.equals(jsonModel, ModelProviderFormatEnums.OPENAI.getFormat())) {
			userMsg = userMessage(PromptBuilder.render("flow-question-json.st",
					Map.of("input", input.toString(), "jsonSchema", StrUtil.EMPTY, "categories",
							categories.stream()
								.map(AiQuestionNode.Category::toString)
								.collect(Collectors.joining(StrUtil.LF)))));
		}
		else {
			userMsg = userMessage(PromptBuilder.render("flow-question-json.st", Map.of("input", input.toString(),
					"categories",
					categories.stream().map(AiQuestionNode.Category::toString).collect(Collectors.joining(StrUtil.LF)),
					"jsonSchema", jsonSchema.toString())));
		}

		// 将用户消息添加到 chatMemory（参考 JsonChatRule 的处理方式）
		chatMemory.add(userMsg);

		// 构建完整消息列表：system + chatMemory 中的所有消息
		List<ChatMessage> allMessages = new ArrayList<>();
		allMessages.add(systemMsg);
		allMessages.addAll(chatMemory.messages());

		builder.messages(allMessages);

		// 设置 JSON 响应格式（仅 OpenAI 格式）
		if (Objects.equals(jsonModel, ModelProviderFormatEnums.OPENAI.getFormat())) {
			builder.responseFormat(ResponseFormat.builder().type(JSON).jsonSchema(jsonSchema).build());
		}

		return builder.build();
	}

	/**
	 * 获取匹配的分支索引
	 */
	private static int getCaseIndex(AiQuestionNode config, String result) {
		int caseIndex = -1;
		if (config.getCategories() != null && !config.getCategories().isEmpty()) {
			for (int i = 0; i < config.getCategories().size(); i++) {
				AiQuestionNode.Category caseItem = config.getCategories().get(i);
				String caseValue = caseItem.getName();
				if (StringUtil.equalsIgnoreCase(result, caseValue)) {
					caseIndex = i;
					break;
				}
			}
		}
		return caseIndex;
	}

}
