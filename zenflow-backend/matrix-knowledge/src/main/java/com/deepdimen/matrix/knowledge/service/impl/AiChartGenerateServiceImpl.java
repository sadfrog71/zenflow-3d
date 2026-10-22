package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.knowledge.config.DocumentParseAsyncConfiguration;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiChartGenerateService;
import com.deepdimen.matrix.knowledge.support.constant.AiChartTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.JSONRepairUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

/**
 * AI图表生成服务实现类
 * <p>
 * 基于AI模型自动生成ECharts图表配置 支持折线图、饼图、柱状图等多种图表类型 使用JSON Schema约束生成结构化的图表配置
 *
 * @author caoyuan
 * @date 2025/03/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChartGenerateServiceImpl implements AiChartGenerateService {

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<com.deepdimen.matrix.knowledge.support.dataquery.DataQueryAiChatService> dataQueryChats;

	/**
	 * 图表配置缓存 存储生成的图表JSON配置，支持异步获取
	 */
	private final static Cache<String, String> CHART_JSON_MAP = CacheUtil.newLRUCache(1024 * 1000);

	/**
	 * AI模型数据访问层
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * 模型提供者（延迟加载） 避免循环依赖
	 */
	@Lazy
	private final ModelProvider modelProvider;

	/**
	 * 构建折线图JSON Schema
	 * <p>
	 * 定义折线图的数据结构规范 包含X轴分类、Y轴数值和折线数据配置
	 * @return 折线图的JSON Schema定义
	 */
	private static JsonSchema buildLineJsonSchema() {
		return JsonSchema.builder()
			.name("LineJsonSchema")
			.rootElement(JsonObjectSchema.builder().addProperties(new LinkedHashMap<>() {
				{
					// 配置X轴
					put("xAxis",
							JsonObjectSchema.builder()
								.addStringProperty("type", "固定值:category")
								.addProperty("data", JsonArraySchema.builder()
									.items(JsonStringSchema.builder().description("X轴数据项，例如 ['A', 'B', 'C']").build())
									.description("X轴的分类标签数组")
									.build())
								.build());

					// 配置Y轴
					put("yAxis",
							JsonObjectSchema.builder()
								.addStringProperty("type", "固定值:value")
								.description("Y轴数据类型，代表数值轴")
								.build());

					// 配置折线图数据
					put("series",
							JsonArraySchema.builder()
								.description("折线图的数据项，支持多条折线")
								.items(JsonObjectSchema.builder()
									.addStringProperty("type", "固定值:line")
									.addProperty("data", JsonArraySchema.builder()
										.description("Y轴数据数组，例如 [10, 20, 30]，每个值对应xAxis中的类别")
										.items(JsonNumberSchema.builder().description("Y轴具体数值，代表对应类别的值").build())
										.build())
									.description("单条折线的数据配置")
									.build())
								.build());
				}
			}).required().additionalProperties(false).build())
			.build();
	}

	/**
	 * 获取生成的图表配置
	 * <p>
	 * 从缓存中获取已生成的图表JSON配置 用于异步生成场景的结果获取
	 * @param chartId 图表唯一标识
	 * @return 图表的JSON配置字符串，如果未找到则返回null
	 */
	@Override
	public String getChart(String chartId) {
		if (chartId != null && chartId.startsWith("dq-")) {
			var service = dataQueryChats == null ? null : dataQueryChats.getIfAvailable();
			if (service == null)
				throw new com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException(
						"CONTROLLED_SERVICE_UNAVAILABLE");
			return service.chart(com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.identity(),
					chartId);
		}
		return CHART_JSON_MAP.get(chartId);
	}

	/**
	 * 异步生成图表配置
	 * <p>
	 * 根据用户输入和数据，使用AI模型生成ECharts配置 支持折线图、饼图、柱状图等多种类型 生成结果存储在缓存中供异步获取
	 * @param chatMessageDTO 聊天消息，包含图表类型和生成要求
	 * @param data 用于生成图表的原始数据
	 */
	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	@Override
	public void generateChart(ChatMessageDTO chatMessageDTO, String data) {
		try {
			generateChartContent(chatMessageDTO, data);
		}
		catch (RuntimeException e) {
			if (chatMessageDTO.getDataQueryContext() == null)
				throw e;
			// Async exception handlers may print arguments, including saved tokens/result
			// rows.
			log.warn("受控问数图表生成失败，未输出模型结果或请求上下文");
		}
	}

	private void generateChartContent(ChatMessageDTO chatMessageDTO, String data) {
		if (chatMessageDTO.getDataQueryContext() != null) {
			var projected = com.deepdimen.matrix.knowledge.support.dataquery.DataQueryChartProjection
				.fromResult(chatMessageDTO.getDataQuery(), chatMessageDTO.getExtDetails().getChartType());
			if (projected.isPresent()) {
				var service = dataQueryChats.getIfAvailable();
				if (service == null)
					throw new com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException(
							"CONTROLLED_SERVICE_UNAVAILABLE");
				service.saveChart(chatMessageDTO.getDataQueryContext(), projected.get());
				return;
			}
		}
		// 获取 AI 助手服务
		Triple<ChatModel, AiAssistantService, String> jsonTriple = modelProvider
			.getAiJSONAssistant(chatMessageDTO.getModelName());

		String chartType = chatMessageDTO.getExtDetails().getChartType();

		// 渲染用户消息
		JsonSchema jsonSchema = null;
		if (AiChartTypeEnums.LINE.getCode().equals(chartType)) {
			jsonSchema = buildLineJsonSchema();
		}
		else if (AiChartTypeEnums.PIE.getCode().equals(chartType)) {
			jsonSchema = buildPieJsonSchema();
		}
		else if (AiChartTypeEnums.BAR.getCode().equals(chartType)) {
			jsonSchema = buildBarJsonSchema();
		}

		// 构造数据输入
		String result = String.format("%s:%s", chatMessageDTO.getContent(), data);

		ChatRequest.Builder builder = ChatRequest.builder();

		if (Objects.equals(jsonTriple.getRight(), ModelProviderFormatEnums.OPENAI.getFormat())) {
			String userMessage = PromptBuilder.render("chat2db-result-chart.st", Map.of(FlowConstant.RESULT, result,
					"jsonSchema", StrUtil.EMPTY, "chartType", AiChartTypeEnums.getEnumByCode(chartType).getDesc()));
			builder.messages(List.of(userMessage(userMessage)))
				.responseFormat(ResponseFormat.builder().type(JSON).jsonSchema(jsonSchema).build());
		}
		else {
			String userMessage = PromptBuilder.render("chat2db-result-chart.st", Map.of(FlowConstant.RESULT, result,
					"jsonSchema", jsonSchema, "chartType", AiChartTypeEnums.getEnumByCode(chartType).getDesc()));
			builder
				.messages(List.of(systemMessage(PromptBuilder.render("ocr-system-json.st")), userMessage(userMessage)));
		}

		String chartResult = jsonTriple.getLeft().chat(builder.build()).aiMessage().text();

		String repair = JSONRepairUtil.repair(chartResult);
		if (chatMessageDTO.getDataQueryContext() != null) {
			var service = dataQueryChats.getIfAvailable();
			if (service == null)
				throw new com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException(
						"CONTROLLED_SERVICE_UNAVAILABLE");
			service.saveChart(chatMessageDTO.getDataQueryContext(), repair);
			log.info("受控问数图表生成完成（类型: {}）", chatMessageDTO.getExtDetails().getChartType());
		}
		else {
			log.info("图表生成完成 (类型: {}): {}", chatMessageDTO.getExtDetails().getChartType(), chartResult);
			CHART_JSON_MAP.put(chatMessageDTO.getExtDetails().getChartId(), repair);
		}
	}

	/**
	 * 构建柱状图JSON Schema
	 * <p>
	 * 定义柱状图的数据结构规范 包含X轴分类、Y轴数值和柱状数据配置
	 * @return 柱状图的JSON Schema定义
	 */
	private JsonSchema buildBarJsonSchema() {
		return JsonSchema.builder()
			.name("BarJsonSchema")
			.rootElement(JsonObjectSchema.builder().addProperties(new LinkedHashMap<>() {
				{
					// 配置X轴
					put("xAxis", JsonObjectSchema.builder()
						.addStringProperty("type", "固定值:category")
						.addProperty("data", JsonArraySchema.builder()
							.items(JsonStringSchema.builder().description("X轴数据，必须是字符串数组，例如 ['A', 'B', 'C']").build())
							.description("X轴的分类标签数组")
							.build())
						.build());

					// 配置Y轴
					put("yAxis",
							JsonObjectSchema.builder()
								.addStringProperty("type", "固定值:value")
								.description("Y轴数据类型，固定为数值类型")
								.build());

					// 配置柱状图数据
					put("series",
							JsonArraySchema.builder()
								.description("柱状图的数据集合，支持多个系列")
								.items(JsonObjectSchema.builder()
									.addStringProperty("type", "固定值:bar")
									.addProperty("data", JsonArraySchema.builder()
										.description("Y轴数据数组，例如 [10, 20, 30]，每个值对应xAxis中的类别")
										.items(JsonNumberSchema.builder().description("Y轴具体数值，代表对应类别的值").build())
										.build())
									.description("单个系列的柱状图配置")
									.build())
								.build());
				}
			}).required().additionalProperties(false).build())
			.build();
	}

	/**
	 * 构建饼图JSON Schema
	 * <p>
	 * 定义饼图的数据结构规范 包含数据项的名称和数值配置
	 * @return 饼图的JSON Schema定义
	 */
	private JsonSchema buildPieJsonSchema() {
		return JsonSchema.builder()
			.name("PieJsonSchema")
			.rootElement(JsonObjectSchema.builder().addProperties(new LinkedHashMap<>() {
				{
					// 配置饼图数据
					put("series",
							JsonArraySchema.builder()
								.description("饼图的数据集合，支持多个系列")
								.items(JsonObjectSchema.builder()
									.addStringProperty("type", "固定值:pie")
									.addStringProperty("radius", "饼图半径固定值:50%")
									.addProperty("data",
											JsonArraySchema.builder()
												.description("饼图数据项数组，每个对象包含一个值和名称")
												.items(JsonObjectSchema.builder()
													.addNumberProperty("value", "数据值，表示该项的占比大小")
													.addStringProperty("name", "数据项名称，例如 '分类A'")
													.build())
												.build())
									.description("单个系列的饼图配置")
									.build())
								.build());
				}
			}).required().additionalProperties(false).build())
			.build();
	}

}
