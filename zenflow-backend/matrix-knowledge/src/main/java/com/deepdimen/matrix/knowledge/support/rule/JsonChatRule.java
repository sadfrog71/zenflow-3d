package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums;
import com.deepdimen.matrix.knowledge.support.function.FunctionCalling;
import com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider;
import com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import com.deepdimen.matrix.knowledge.support.util.JSONRepairUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import com.deepdimen.matrix.knowledge.support.util.ToolSpecificationsUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.util.*;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;
import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * JSON结构化聊天规则实现
 * <p>
 * 实现基于JSON Schema的结构化聊天功能，支持将用户输入转换为结构化JSON数据。 通过向量搜索自动匹配目标函数，并使用AI模型生成符合指定格式的JSON输出。
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("jsonChat")
@RequiredArgsConstructor
public class JsonChatRule implements ChatRule {

	private final ChatMemoryAdvisorProvider chatMemoryAdvisorProvider;

	private final List<FunctionCalling> functionCallingList;

	private final ModelProvider modelProvider;

	/**
	 * 基于向量相似度自动匹配函数
	 * <p>
	 * 当用户没有明确指定函数时，通过向量搜索自动匹配语义最相似的函数。 使用更严格的相似度阈值（0.6）以提高匹配准确度。
	 * @param chatMessageDTO 聊天消息DTO，包含用户输入
	 * @return 如果找不到匹配的函数返回错误提示，找到则返回null并更新上下文
	 */
	@Nullable
	private static Flux<AiMessageResultDTO> autoChoice(ChatMessageDTO chatMessageDTO) {
		EmbeddingSearchResult<TextSegment> searchResult = EmbeddingProvider.search(chatMessageDTO.getContent(), 1, 0.6,
				metadataKey(EmbedBizTypeEnums.Fields.type).isEqualTo(EmbedBizTypeEnums.FUNCTION.getType()));

		if (Objects.isNull(searchResult) || searchResult.matches().isEmpty()) {
			return Flux.just(new AiMessageResultDTO("未找到相关功能，请点击下方+按钮选择目标功能"));
		}

		String functionName = searchResult.matches().get(0).embedded().metadata().getString(TEMP_ID);
		// 修改上下文中目标功能名称
		chatMessageDTO.getExtDetails().setFuncName(functionName);
		ChatMessageContextHolder.set(chatMessageDTO);
		return null;
	}

	/**
	 * 构建JSON格式化聊天请求
	 * <p>
	 * 根据不同的模型提供商（OpenAI或其他）构建相应格式的聊天请求。 使用JSON Schema定义输出格式，确保模型生成符合预期结构的JSON数据。
	 * @param chatMemory 聊天记忆，存储历史对话
	 * @param functionCalling 函数调用接口，定义输出格式
	 * @param inputText 用户输入文本
	 * @param jsonModel JSON模型格式（OpenAI或其他）
	 * @return 构建好的聊天请求对象
	 */
	private ChatRequest buildChatRequest(ChatMemory chatMemory, FunctionCalling functionCalling, String inputText,
			String jsonModel) {
		ChatRequest.Builder builder = ChatRequest.builder();
		Class genericType = functionCalling.getGenericType();
		Field[] fields = ReflectUtil.getFields(genericType);
		JsonObjectSchema jsonObjectSchema = ToolSpecificationsUtils.parametersFrom(fields);
		JsonSchema jsonSchema = JsonSchema.builder()
			.name(functionCalling.functionName())
			.rootElement(jsonObjectSchema)
			.build();

		String metadata = PromptBuilder.render("knowledge-func-metadata.st", Map.of(BaseAiRequest.Fields.messageKey,
				ChatMessageContextHolder.get().getMessageKey(), systemTime, DateUtil.now()));

		// JSON Schema
		if (Objects.equals(jsonModel, ModelProviderFormatEnums.OPENAI.getFormat())) {
			chatMemory.add(userMessage(inputText + StrUtil.LF + metadata));
			List<ChatMessage> chatMessages = new ArrayList<>();
			chatMessages.add(systemMessage(PromptBuilder.render("ocr-system-json.st")));
			chatMessages.addAll(chatMemory.messages());
			builder.messages(chatMessages)
				.responseFormat(ResponseFormat.builder().type(JSON).jsonSchema(jsonSchema).build());
		}
		else {
			chatMemory.add(userMessage(PromptBuilder.render("user-json.st",
					Map.of("userInput", inputText + StrUtil.LF + metadata, "jsonSchema", jsonObjectSchema))));
			List<ChatMessage> chatMessages = new ArrayList<>();
			chatMessages.add(systemMessage(PromptBuilder.render("ocr-system-json.st")));
			chatMessages.addAll(chatMemory.messages());
			builder.messages(chatMessages);
		}

		return builder.build();
	}

	/**
	 * 处理JSON结构化聊天请求
	 * <p>
	 * 主要流程： 1. 如果用户未指定函数，通过向量搜索自动匹配 2. 查找对应的函数实现 3. 构建JSON Schema请求，使用AI模型生成结构化数据 4.
	 * 调用函数并返回结果，成功时附带路由信息
	 * @param chatMessageDTO 聊天上下文信息
	 * @return AI响应结果流，包含执行结果和可选的路由信息
	 */
	@Override
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {

		Triple<ChatModel, AiAssistantService, String> jsonAssistantTriple = modelProvider
			.getAiJSONAssistant(chatMessageDTO.getModelName());

		// 处理function，如果用户没有传递，则根据用户语义查询一个 function
		if (Objects.isNull(chatMessageDTO.getExtDetails())
				|| StrUtil.isBlank(chatMessageDTO.getExtDetails().getFuncName())) {
			Flux<AiMessageResultDTO> just = autoChoice(chatMessageDTO);
			if (just != null)
				return just;
		}

		// 判断是否包含函数名称
		Optional<FunctionCalling> functionCallingOptional = functionCallingList.stream()
			.filter(functionCalling -> StrUtil.equals(chatMessageDTO.getExtDetails().getFuncName(),
					functionCalling.functionName()))
			.findFirst();

		if (functionCallingOptional.isEmpty()) {
			return Flux.just(new AiMessageResultDTO("调用失败，未找到对应的函数"));
		}

		FunctionCalling functionCalling = functionCallingOptional.get();
		ChatMemory chatMemory = chatMemoryAdvisorProvider.get(chatMessageDTO.getConversationId());
		ChatRequest chatRequest = buildChatRequest(chatMemory, functionCalling, chatMessageDTO.getContent(),
				jsonAssistantTriple.getRight());
		ChatResponse chatResponse = jsonAssistantTriple.getLeft().chat(chatRequest);
		log.info("json chatResponse: {}", chatResponse.aiMessage().text());

		// 大模型参数解析错误
		String correctJSON = JSONRepairUtil.repair(chatResponse.aiMessage().text());
		if (StrUtil.isBlank(chatResponse.aiMessage().text()) || !JSONUtil.isTypeJSON(correctJSON)) {
			return Flux.just(new AiMessageResultDTO("大模型参数解析错误:" + chatResponse.aiMessage().text()));
		}

		// 使用参数调用原有的函数逻辑
		ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
			.name(functionCalling.functionName())
			.arguments(correctJSON)
			.id(chatMessageDTO.getConversationId())
			.build();

		R<String> resultR = functionCalling.execute(toolExecutionRequest);

		String result = StrUtil.isBlank(resultR.getData()) ? resultR.getMsg() : resultR.getData();
		if (StrUtil.isNotBlank(result) && FunctionCalling.CONFIRM_FORM == resultR.getCode()) {
			chatMemory.add(AiMessage.from(chatResponse.aiMessage().text() + StrUtil.LF + result));
		}

		// 返回结果 ,如果业务处理成功的话，增加跳转路由标识
		AiMessageResultDTO aiMessageResultDTO = new AiMessageResultDTO(result,
				resultR.isOk() ? functionCalling.routePath() : null);
		if (FunctionCalling.CONFIRM_FORM == resultR.getCode()) {
			aiMessageResultDTO.setMessage("请检查表单信息是否正确，无误后点击确认");
			aiMessageResultDTO.setConfirmForm(resultR.getData());
			aiMessageResultDTO.setFunctionName(functionCalling.functionName());
		}

		return Flux.just(aiMessageResultDTO);
	}

}
