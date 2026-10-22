package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.lang.Pair;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.LlmOCRFieldDTO;
import com.deepdimen.matrix.knowledge.entity.AiOcrConfEntity;
import com.deepdimen.matrix.knowledge.mapper.AiOcrConfMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiOcrConfService;
import com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.JSONRepairUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.*;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

/**
 * AI OCR配置服务实现类
 * <p>
 * 提供图像文字识别（OCR）服务 结合视觉模型和文本模型进行结构化数据提取 支持自定义字段和JSON Schema约束
 *
 * @author caoyuan
 * @date 2024/09/10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOcrConfServiceImpl extends ServiceImpl<AiOcrConfMapper, AiOcrConfEntity> implements AiOcrConfService {

	/**
	 * 远程文件服务 用于获取待识别的图像文件
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * 模型提供者 管理视觉和文本模型
	 */
	private final ModelProvider modelProvider;

	/**
	 * 解析图像并提取结构化数据
	 * <p>
	 * 使用视觉模型识别图像内容 通过文本模型根据JSON Schema提取结构化数据
	 * @param aiOcrConf 包含OCR配置ID和图像资源的实体
	 * @return 结构化的JSON识别结果
	 */
	@SneakyThrows
	@Override
	public R parseImage(AiOcrConfEntity aiOcrConf) {
		// 查询底图设置和标记字段
		AiOcrConfEntity confEntity = baseMapper.selectById(aiOcrConf.getId());
		// 需要OCR 的字段列表
		List<LlmOCRFieldDTO> fieldDTOList = JSONUtil.parseObj(confEntity.getOcrMarked())
			.getBeanList("markerList", LlmOCRFieldDTO.class);

		// 用户输入提示词增加限制
		SystemMessage systemMessage = SystemMessage.from(PromptBuilder.render("ocr-image.st"));

		// 组装图像文件
		feign.Response response = remoteFileService.getFile(aiOcrConf.getImageResource());
		String encodeToString = Base64.getEncoder().encodeToString(response.body().asInputStream().readAllBytes());
		UserMessage userMessage = UserMessage.from(ImageContent.from(encodeToString, MimeTypeUtils.IMAGE_PNG_VALUE));

		ChatResponse chatResponse = null;
		if (YesNoEnum.NO.getCode().equals(aiOcrConf.getHigherAnalyze())) {
			// 普通模式依赖于 视觉模型自己的结构化能力
			UserMessage userJsonMessage = UserMessage.from(PromptBuilder.render("ocr-image-user-json.st",
					Map.of("jsonSchema", getJsonSchema(aiOcrConf, fieldDTOList))));

			// 使用视觉模型进行OCR识别
			Pair<ChatModel, AiAssistantService> aiAssistantServicePair = modelProvider.getAiVisionAssistant();
			chatResponse = aiAssistantServicePair.getKey().chat(systemMessage, userJsonMessage, userMessage);

			String text = chatResponse.aiMessage().text();
			String repaired = JSONRepairUtil.repair(text);
			return R.ok(repaired);
		}
		// 使用视觉模型进行OCR识别
		Pair<ChatModel, AiAssistantService> aiAssistantServicePair = modelProvider.getAiVisionAssistant();
		chatResponse = aiAssistantServicePair.getKey().chat(systemMessage, userMessage);

		log.info("OCR 结果: {}", chatResponse.aiMessage().text());

		// 调用文本模型按JSON Schema提取结构化数据
		Triple<ChatModel, AiAssistantService, String> aiJSONAssistant = modelProvider
			.getAiJSONAssistant(aiOcrConf.getChatModelName());

		ChatResponse chatted = aiJSONAssistant.getLeft()
			.chat(buildChatRequest(confEntity, chatResponse.aiMessage().text(), fieldDTOList,
					aiJSONAssistant.getRight()));
		log.info("OCR JSON: {}", chatted.aiMessage().text());

		String repair = JSONRepairUtil.repair(chatted.aiMessage().text());
		return R.ok(repair);
	}

	/**
	 * 构建结构化数据提取请求
	 * <p>
	 * 根据模型类型选择不同的JSON Schema传递方式 OpenAI模型使用原生响应格式，其他模型通过提示词传递
	 * @param confEntity OCR配置实体
	 * @param lllAiOcrResult 视觉模型的OCR识别结果
	 * @param fieldDTOList 需要提取的字段列表
	 * @param jsonModel JSON模型类型
	 * @return 构建好的聊天请求
	 */
	private ChatRequest buildChatRequest(AiOcrConfEntity confEntity, String lllAiOcrResult,
			List<LlmOCRFieldDTO> fieldDTOList, String jsonModel) {
		ChatRequest.Builder builder = ChatRequest.builder();
		JsonSchema jsonSchema = getJsonSchema(confEntity, fieldDTOList);

		// JSON Schema
		if (Objects.equals(jsonModel, ModelProviderFormatEnums.OPENAI.getFormat())) {
			builder.messages(systemMessage(PromptBuilder.render("ocr-system-json.st")), userMessage(lllAiOcrResult))
				.responseFormat(ResponseFormat.builder().type(JSON).jsonSchema(jsonSchema).build());
		}
		else {
			builder.messages(systemMessage(PromptBuilder.render("ocr-system-json.st")), userMessage(PromptBuilder
				.render("ocr-user-json.st", Map.of("userInput", lllAiOcrResult, "jsonSchema", jsonSchema))));
		}

		return builder.build();
	}

	/**
	 * 获取OCR结果的JSON Schema
	 * @param confEntity OCR配置实体
	 * @param fieldDTOList OCR字段列表
	 * @return 构建好的JSON Schema对象
	 */
	private static JsonSchema getJsonSchema(AiOcrConfEntity confEntity, List<LlmOCRFieldDTO> fieldDTOList) {
		List<String> fieldNames = new ArrayList<>();
		fieldNames.add("isContain");
		fieldNames.addAll(fieldDTOList.stream().map(LlmOCRFieldDTO::getName).toList());
		JsonSchema jsonSchema = JsonSchema.builder()
			.name("OCResult")
			.rootElement(JsonObjectSchema.builder().addProperties(new LinkedHashMap<>() {
				{
					// 是否包含
					put("isContain", JsonBooleanSchema.builder().description(confEntity.getOcrPrompt()).build());
					// 目标OCR 的字段列表
					for (LlmOCRFieldDTO llmOCRFieldDTO : fieldDTOList) {
						put(llmOCRFieldDTO.getName(),
								JsonStringSchema.builder().description(llmOCRFieldDTO.getDescription()).build());
					}
				}
			}).required(fieldNames).additionalProperties(false).build())
			.build();
		return jsonSchema;
	}

}
