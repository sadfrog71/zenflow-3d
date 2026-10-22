package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Base64;
import java.util.Map;

/**
 * 图片AI解析处理器
 * <p>
 * 该处理器使用AI视觉模型对图片文件进行OCR识别，提取图片中的文本内容。 支持PNG、JPG、JPEG格式的图片文件，通过调用视觉模型API将图片转换为文本。
 *
 * @author caoyuan
 * @date 2024/3/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAIUploadFileParseHandler implements UploadFileParseHandler {

	private final ModelProvider modelProvider;

	/**
	 * 判断是否支持处理指定的文档
	 * <p>
	 * 当数据集开启了AI OCR功能，且文档为图片格式（PNG、JPG、JPEG）时，返回true
	 * @param aiDataset 数据集实体，包含OCR开关配置
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果支持处理返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());

		if (YesNoEnum.NO.getCode().equals(aiDataset.getAiOcrFlag())) {
			return false;
		}

		return FileTypeEnums.PNG.getType().equals(contentType) || FileTypeEnums.JPG.getType().equals(contentType)
				|| FileTypeEnums.JPEG.getType().equals(contentType);
	}

	/**
	 * 将图片文件转换为文本内容
	 * <p>
	 * 该方法将图片文件编码为Base64格式，然后调用AI视觉模型进行OCR识别， 提取图片中的文本内容。使用预定义的OCR提示词模板来指导AI进行文本提取。
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream 图片文件的输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和识别结果的Pair对象，成功时返回识别的文本，失败时返回错误信息
	 */
	@SneakyThrows
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		String encodeToString = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
		Pair<ChatModel, AiAssistantService> aiVisionAssistant = modelProvider.getAiVisionAssistant();

		UserMessage userMessage = UserMessage.from(TextContent.from(PromptBuilder.render("ocr-common.st", Map.of())),
				ImageContent.from(encodeToString, MediaType.IMAGE_PNG_VALUE));

		try {

			ChatResponse chatResponse = aiVisionAssistant.getKey().chat(userMessage);
			return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, chatResponse.aiMessage().text());
		}
		catch (Exception e) {
			log.error("ImageAIUploadFileParseHandler error", e);
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, e.getMessage());
		}
	}

	/**
	 * 获取处理器的优先级顺序
	 * <p>
	 * 数值越大，优先级越高。该处理器的优先级为100，在多个处理器都支持同一文件类型时， 会优先使用优先级高的处理器。
	 * @return 优先级值，返回100
	 */
	@Override
	public int getOrder() {
		return 100;
	}

}
