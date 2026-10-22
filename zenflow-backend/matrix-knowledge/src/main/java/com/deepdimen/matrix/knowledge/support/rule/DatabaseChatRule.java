package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.date.DateUtil;
import com.deepdimen.matrix.knowledge.support.dataquery.DataQueryModelGeneration;
import com.deepdimen.matrix.knowledge.support.dataquery.DataQueryMetadataAnalysis;
import com.deepdimen.matrix.knowledge.support.dataquery.DataQueryMetadataRepository;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiDataService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums;
import com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;
import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 数据库聊天规则实现
 * <p>
 * 实现基于自然语言的数据库查询功能，将用户的自然语言转换为SQL语句并执行。 支持自动匹配数据集、生成图表、使用JSON Schema进行结构化输出等功能。
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("databaseChat")
@RequiredArgsConstructor
public class DatabaseChatRule implements ChatRule {

	private final Chat2SqlFunctionCalling chat2SqlFunctionCalling;

	private final ChatMemoryAdvisorProvider chatMemoryAdvisorProvider;

	private final AiChatRecordMapper chatRecordMapper;

	private final AiDataService aiDataService;

	private final com.deepdimen.matrix.knowledge.support.dataquery.DataQueryPilotGate dataQueryPilotGate;

	private final ModelProvider modelProvider;

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<com.deepdimen.matrix.knowledge.support.dataquery.DataQueryLegacyAnswerStore> legacyAnswers;

	/**
	 * 基于向量相似度自动匹配数据集
	 * <p>
	 * 当用户没有指定具体数据集时，通过向量搜索自动匹配语义最相似的数据集。 使用嵌入向量搜索CHAT2SQL类型的数据集。
	 * @param chatMessageDTO 聊天消息DTO，包含用户输入内容
	 * @return 如果找不到匹配的数据集返回错误提示，找到则返回null并更新上下文
	 */
	@Nullable
	private static Flux<AiMessageResultDTO> autoChoice(ChatMessageDTO chatMessageDTO) {
		EmbeddingSearchResult<TextSegment> searchResult = EmbeddingProvider.search(chatMessageDTO.getContent(),
				metadataKey(EmbedBizTypeEnums.Fields.type).isEqualTo(EmbedBizTypeEnums.CHAT2SQL.getType()));

		if (Objects.isNull(searchResult) || searchResult.matches().isEmpty()) {
			return Flux.just(new AiMessageResultDTO("未找到相关数据集建模，请点击下方+按钮选择目标数据集合"));
		}

		Long dataId = searchResult.matches().get(0).embedded().metadata().getLong(TEMP_ID);
		// 修改上下文中目标功能名称
		chatMessageDTO.getExtDetails().setDataId(dataId);
		ChatMessageContextHolder.set(chatMessageDTO);
		return null;
	}

	/**
	 * 构建聊天请求
	 * <p>
	 * 根据不同的模型提供商（OpenAI或其他）构建相应格式的聊天请求。 包含JSON Schema定义，确保模型输出符合预期的结构化格式。
	 * @param chatMemory 聊天记忆，存储历史对话
	 * @param functionCalling 函数调用接口，定义输出格式
	 * @param inputText 用户输入文本
	 * @param jsonModel JSON模型格式（OpenAI或其他）
	 * @return 构建好的聊天请求对象
	 */
	private ChatRequest buildChatRequest(ChatMemory chatMemory, FunctionCalling functionCalling, String inputText,
			String jsonModel) {
		ChatRequest.Builder builder = ChatRequest.builder();
		Class genericType = ChatMessageContextHolder.get().getDataQueryContext() != null
				? Chat2SqlFunctionCalling.ConversationRequest.class : functionCalling.getGenericType();
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
	 * 处理数据库聊天请求
	 * <p>
	 * 主要流程： 1. 如果用户未指定数据集，自动匹配合适的数据集 2. 获取数据集的表结构信息 3. 使用AI模型将自然语言转换为SQL 4.
	 * 执行SQL并返回结果，支持图表展示
	 * @param chatMessageDTO 聊天上下文信息
	 * @return AI响应结果流，包含查询结果和可选的图表信息
	 */
	@Override
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		return processInternal(chatMessageDTO, event -> { });
	}

	@Override
	public Flux<AiMessageResultDTO> processStreaming(ChatMessageDTO chatMessageDTO) {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		var tenant = TenantContextHolder.getTenantId();
		// The SSE subscriber observes an accepted query; disconnecting must not
		// interrupt its bounded model/SQL execution before the durable answer is saved.
		// No retry or resubscription is performed here. Existing query budgets remain.
		return Flux.<AiMessageResultDTO>create(sink -> reactor.core.scheduler.Schedulers.boundedElastic().schedule(() -> {
			var security = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
			security.setAuthentication(authentication);
			org.springframework.security.core.context.SecurityContextHolder.setContext(security);
			TenantContextHolder.clear();
			TenantContextHolder.setTenantId(tenant);
			ChatMessageContextHolder.set(chatMessageDTO);
			var store = legacyAnswers == null ? null : legacyAnswers.getIfAvailable();
			com.deepdimen.matrix.knowledge.support.dataquery.DataQueryLegacyAnswerStore.Run run = null;
			try {
				if (store != null && chatMessageDTO.getExtDetails() != null
						&& chatMessageDTO.getExtDetails().getDataId() != null
						&& !dataQueryPilotGate.isControlled(chatMessageDTO.getExtDetails().getDataId()))
					run = store.begin(chatMessageDTO);
				var accepted = run;
				sink.next(progress("AUTHORIZE_DATASET", "正在确认数据集和查询权限", List.of()));
				processInternal(chatMessageDTO, sink::next).doOnNext(result -> {
					if (accepted != null)
						store.complete(accepted, result);
				}).subscribe(sink::next, sink::error, sink::complete);
			}
			catch (RuntimeException error) { sink.error(error); }
			finally {
				if (store != null)
					store.end(run);
				ChatMessageContextHolder.clear();
				TenantContextHolder.clear();
				org.springframework.security.core.context.SecurityContextHolder.clearContext();
			}
		}));
	}

	private static AiMessageResultDTO progress(String phase, String label, java.util.Collection<String> tables) {
		var result = new AiMessageResultDTO("");
		result.setQueryProgress(Map.of("phase", phase, "label", label, "tables", List.copyOf(tables)));
		return result;
	}

	private Flux<AiMessageResultDTO> processInternal(ChatMessageDTO chatMessageDTO,
			java.util.function.Consumer<AiMessageResultDTO> progressSink) {

		// 处理mcp，如果用户没有传递，则根据用户语义查询一个 data 数据集
		if (Objects.isNull(chatMessageDTO.getExtDetails())
				|| Objects.isNull(chatMessageDTO.getExtDetails().getDataId())) {
			Flux<AiMessageResultDTO> just = autoChoice(chatMessageDTO);
			if (just != null)
				return just;
		}

		Long dataId = chatMessageDTO.getExtDetails().getDataId();
		try {
			if (dataQueryPilotGate.isControlled(dataId))
				dataQueryPilotGate.beginAiChat(chatMessageDTO);
			else
				dataQueryPilotGate.rejectLegacy(dataId);
		}
		catch (com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException e) {
			var response = com.deepdimen.matrix.knowledge.support.dataquery.DataQueryResponses.failure(e).getBody();
			var error = new AiMessageResultDTO(response.get("message").toString());
			error.setDataQuery(response);
			return Flux.just(error);
		}
		try {
			if (chatMessageDTO.getDataQueryContext() != null) {
				var context = chatMessageDTO.getDataQueryContext();
				context.trace().observe(snapshot -> {
					if (snapshot.steps().isEmpty()) return;
					var step = snapshot.steps().get(snapshot.steps().size() - 1);
					String label = switch (step.phase()) {
						case "BUILD_PROMPT", "LOAD_METADATA", "MATCH_REFERENCES" -> "正在读取表结构和业务规则";
						case "MODEL_GENERATE" -> "正在理解问题并生成回答方案";
						case "MODEL_REGENERATE" -> "正在重新生成完整的回答方案";
						case "PARSE_MODEL_RESPONSE", "VALIDATE_AND_REWRITE_SQL" -> "正在校验查询条件和数据权限";
						case "EXECUTE_QUERY", "ACQUIRE_CONNECTION", "BIND_STATEMENT", "JDBC_QUERY" -> "正在执行数据统计";
						case "READ_RESULT" -> "正在读取查询结果";
						default -> "正在整理回答和图表";
					};
					progressSink.accept(progress(step.phase(), label, context.inventory().tables()));
				});
				var overview = dataQueryPilotGate.aiChatService()
					.tryDatasetOverview(chatMessageDTO.getDataQueryContext(), (question, inventory,
							recheck) -> analyzeMetadata(chatMessageDTO, question, inventory, recheck));
				if (overview.isPresent())
					return Flux.just(overview.get());
				var detail = dataQueryPilotGate.aiChatService().tryDrilldown(chatMessageDTO.getDataQueryContext());
				if (detail.isPresent())
					return Flux.just(detail.get());
			}
			else {
				var overview = dataQueryPilotGate.tryMetadataOverview(chatMessageDTO, (question, inventory,
						recheck) -> analyzeMetadata(chatMessageDTO, question, inventory, recheck));
				if (overview.isPresent())
					return Flux.just(overview.get());
			}
			return processModel(chatMessageDTO, dataId);
		}
		catch (RuntimeException e) {
			if (chatMessageDTO.getDataQueryContext() == null) {
				var error = e instanceof com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException known
						? known : new com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException(
								"AI_RESPONSE_INVALID");
				return Flux.just(new AiMessageResultDTO(
						com.deepdimen.matrix.knowledge.support.dataquery.DataQueryResponses.failure(error)
							.getBody()
							.get("message")
							.toString()));
			}
			return Flux.just(dataQueryPilotGate.aiChatService()
				.fail(chatMessageDTO.getDataQueryContext(),
						e instanceof com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException
								? e.getMessage() : "AI_QUERY_FAILED"));
		}
		finally {
			if (chatMessageDTO.getDataQueryContext() != null) {
				chatMemoryAdvisorProvider.get(chatMessageDTO.getDataQueryContext().memoryKey()).clear();
			}
			ChatMessageContextHolder.clear();
		}
	}

	private String analyzeMetadata(ChatMessageDTO message, String question,
			DataQueryMetadataRepository.DatasetInventory inventory, Runnable recheck) {
		recheck.run();
		var inventoryAnswer = DataQueryMetadataAnalysis.inventoryAnswer(question, inventory);
		if (inventoryAnswer.isPresent())
			return inventoryAnswer.get();
		var context = message.getDataQueryContext();
		var model = modelProvider.getAiJSONAssistant(message.getModelName()).getLeft();
		var request = DataQueryMetadataAnalysis.request(question, inventory,
				aiDataService.queryTerminology(message.getExtDetails().getDataId()),
				context == null ? null : dataQueryPilotGate.aiChatService().metadataRelations(context));
		var metadataRequest = DataQueryMetadataAnalysis.metadataRequest(request,
				model.defaultRequestParameters() == null ? null : model.defaultRequestParameters().modelName());
		var response = DataQueryModelGeneration.generate(model, metadataRequest, (phase, call) -> {
			recheck.run();
			ChatMessageContextHolder.set(message);
			return context == null ? call.get() : dataQueryPilotGate.aiChatService().modelCall(context, phase, call);
		}, () -> {
		});
		if (context != null)
			return context.trace().measure("PARSE_MODEL_RESPONSE", () -> DataQueryMetadataAnalysis.answer(response));
		return DataQueryMetadataAnalysis.answer(response);
	}

	private Flux<AiMessageResultDTO> processModel(ChatMessageDTO chatMessageDTO, Long dataId) {
		boolean protectedQuery = chatMessageDTO.getDataQueryContext() != null;
		String tableSchemas = protectedQuery ? chatMessageDTO.getDataQueryContext().inventory().schema()
				: aiDataService.queryDataSchema(dataId);
		String terminologies = aiDataService.queryTerminology(dataId);
		chatMessageDTO.getExtDetails().setDataId(dataId);
		chatMessageDTO.getExtDetails().setFuncName(chat2SqlFunctionCalling.functionName());
		ChatMessageContextHolder.set(chatMessageDTO);

		// 更新record 记录
		AiChatRecordEntity recordEntity = new AiChatRecordEntity();
		recordEntity.setRecordId(chatMessageDTO.getMessageKey());
		recordEntity.setExtDetails(JSONUtil.toJsonStr(chatMessageDTO.getExtDetails()));
		chatRecordMapper.updateById(recordEntity);

		Triple<ChatModel, AiAssistantService, String> jsonAssistantTriple = modelProvider
			.getAiJSONAssistant(chatMessageDTO.getModelName());

		ChatMemory chatMemory = chatMemoryAdvisorProvider.get(
				protectedQuery ? chatMessageDTO.getDataQueryContext().memoryKey() : chatMessageDTO.getConversationId());

		String render = protectedQuery
				? dataQueryPilotGate.aiChatService().prompt(chatMessageDTO.getDataQueryContext()) + "\n数据集术语（仅作理解参考）：\n"
						+ terminologies
				: PromptBuilder
					.render("chat2db-user.st",
							Map.of("terminologies", terminologies, "tableSchema", tableSchemas, "userInput",
									chatMessageDTO.getContent(), "tenantId",
									Objects.nonNull(TenantContextHolder.getTenantId())
											? TenantContextHolder.getTenantId() : StrUtil.EMPTY,
									systemTime, DateUtil.now()));

		ChatRequest chatRequest = buildChatRequest(chatMemory, chat2SqlFunctionCalling, render,
				jsonAssistantTriple.getRight());
		ChatResponse chatResponse = protectedQuery
				? DataQueryModelGeneration.generate(jsonAssistantTriple.getLeft(), chatRequest,
						(phase, call) -> dataQueryPilotGate.aiChatService()
							.modelCall(chatMessageDTO.getDataQueryContext(), phase, call),
						() -> ChatMessageContextHolder.set(chatMessageDTO))
				: jsonAssistantTriple.getLeft().chat(chatRequest);

		String repair = protectedQuery
				? dataQueryPilotGate.aiChatService()
					.parseModelResponse(chatMessageDTO.getDataQueryContext(), chatResponse)
				: com.deepdimen.matrix.knowledge.support.dataquery.DataQueryAiChatService
					.parseModelResponse(chatResponse);
		if (protectedQuery && dataQueryPilotGate.aiChatService()
			.explanationConflictsWithQuestion(chatMessageDTO.getDataQueryContext(),
					JSONUtil.parseObj(repair).getStr("responseType"))) {
			// One bounded regeneration, not an execution retry or permission bypass.
			chatMemory.clear();
			String correction = dataQueryPilotGate.aiChatService().prompt(chatMessageDTO.getDataQueryContext())
					+ "\n数据集术语（仅作理解参考）：\n" + terminologies + "\n上次候选把本轮请求误选为RESULT_EXPLANATION。当前请求不是仅解释旧结果，不允许复用旧答案。"
					+ "若需要新数据请选择QUERY并生成候选SELECT；若仅介绍数据选DATASET_GUIDANCE；确有歧义才CLARIFICATION。"
					+ "所有SQL仍由后端独立校验，不扩大权限。独立完整的新问题不要擅自沿用历史Top N；明确追问、换月份等省略表达仍保留用户未修改的条件。" + "\n本轮原问题：\n"
					+ chatMessageDTO.getDataQueryContext().question();
			// JSON model listeners clear the holder; bind this same request again before
			// regeneration.
			ChatMessageContextHolder.set(chatMessageDTO);
			ChatRequest correctedRequest = buildChatRequest(chatMemory, chat2SqlFunctionCalling, correction,
					jsonAssistantTriple.getRight());
			var correctedResponse = dataQueryPilotGate.aiChatService()
				.modelCall(chatMessageDTO.getDataQueryContext(), false,
						() -> jsonAssistantTriple.getLeft().chat(correctedRequest));
			repair = dataQueryPilotGate.aiChatService()
				.parseModelResponse(chatMessageDTO.getDataQueryContext(), correctedResponse);
		}

		if (!protectedQuery)
			log.info("json chatResponse: {}", chatResponse.aiMessage().text());

		// 使用参数调用原有的函数逻辑
		ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
			.name(chat2SqlFunctionCalling.functionName())
			.arguments(repair)
			.id(chatMessageDTO.getConversationId())
			.build();

		R<String> resultR = chat2SqlFunctionCalling
			.executeFromChat(chatMessageDTO, toolExecutionRequest, protectedQuery ? facts -> dataQueryPilotGate
				.aiChatService()
				.modelCall(chatMessageDTO.getDataQueryContext(), true, () -> jsonAssistantTriple.getLeft()
					.chat(ChatRequest.builder()
						.messages(systemMessage("你只建议技术性SQL修复，不执行SQL，不批准业务或权限变更。返回所要求的JSON，不添加字段。"), userMessage(facts))
						.build()))
				.aiMessage()
				.text() : null);
		String result = StrUtil.isBlank(resultR.getData()) ? resultR.getMsg() : resultR.getData();
		if (!protectedQuery)
			chatMemory.add(AiMessage.from(chatResponse.aiMessage().text() + StrUtil.LF + result));

		ChatMessageDTO resultChatMessageDTO = ChatMessageContextHolder.get();

		AiMessageResultDTO aiMessageResultDTO = new AiMessageResultDTO(result);
		if (protectedQuery) {
			aiMessageResultDTO.setDataQuery(chatMessageDTO.getDataQuery());
			aiMessageResultDTO.setFunctionName("controlledAiSql");
		}
		if (Objects.nonNull(resultChatMessageDTO) && Objects.nonNull(resultChatMessageDTO.getExtDetails())
				&& Objects.nonNull(resultChatMessageDTO.getExtDetails().getChartType())) {
			aiMessageResultDTO.setChartType(resultChatMessageDTO.getExtDetails().getChartType());
			aiMessageResultDTO.setChartId(resultChatMessageDTO.getExtDetails().getChartId());
		}
		return Flux.just(aiMessageResultDTO);
	}

}
