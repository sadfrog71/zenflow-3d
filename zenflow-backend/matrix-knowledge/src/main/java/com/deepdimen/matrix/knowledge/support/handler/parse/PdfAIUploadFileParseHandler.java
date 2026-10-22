package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDF文档AI解析处理器
 * <p>
 * 该处理器专门处理PDF文档，优先使用Apache PDFBox进行解析，当遇到影印版PDF时， 自动切换到AI视觉模型进行OCR识别。处理流程如下： 1.
 * 首先尝试使用PDFBox直接提取文本 2. 如果失败（通常是影印版PDF），则将PDF按页转换为图片 3. 对每页图片调用AI视觉模型进行OCR识别 4.
 * 将所有页面的识别结果合并返回
 *
 * @author caoyuan
 * @date 2024/3/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfAIUploadFileParseHandler implements UploadFileParseHandler {

	private final AiKnowledgeProperties aiKnowledgeProperties;

	private final ModelProvider modelProvider;

	/**
	 * 判断是否支持处理指定的文档
	 * <p>
	 * 当数据集开启了AI OCR功能，且文档为PDF格式时，返回true。 该处理器专门处理需要AI OCR的PDF文档。
	 * @param aiDataset 数据集实体，包含OCR开关配置
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果支持处理返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {

		if (YesNoEnum.NO.getCode().equals(aiDataset.getAiOcrFlag())) {
			return false;
		}

		String contentType = FileUtil.extName(documentEntity.getFileUrl());
		return FileTypeEnums.PDF.getType().equals(contentType);
	}

	/**
	 * 解析PDF文档内容
	 * <p>
	 * 该方法首先尝试使用PDFBox直接提取PDF中的文本内容。 如果提取失败（通常是因为PDF是影印版），则调用AI OCR服务进行识别。
	 * 输入流会被复制到内存中，以便在PDFBox解析失败后仍可用于OCR处理。
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream PDF文件的输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和文本内容的Pair对象，成功时返回提取的文本，失败时返回错误信息
	 */
	@SneakyThrows
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		ApachePdfBoxDocumentParser pdfBoxDocumentParser = new ApachePdfBoxDocumentParser();
		@Cleanup
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IoUtil.copy(inputStream, baos);

		try {
			Document document = pdfBoxDocumentParser.parse(new ByteArrayInputStream(baos.toByteArray()));
			return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, document.text());
		}
		catch (Exception e) {
			log.info("文件 {} pdfbox 解析失败，正在调用 ai ocr", documentEntity.getName());
		}

		// 调用 OCR 服务处理影印版ben
		try (inputStream) {
			return getFileAIParser(documentEntity, new ByteArrayInputStream(baos.toByteArray()));
		}
	}

	/**
	 * 使用AI视觉模型对PDF进行OCR识别
	 * <p>
	 * 该方法将PDF文档按页转换为图片，然后对每页图片调用AI视觉模型进行OCR识别。 处理过程如下： 1. 加载PDF文档并获取页面渲染器 2.
	 * 循环处理每一页，将其渲染为指定DPI的图片 3. 将图片编码为Base64格式 4. 调用AI视觉模型进行OCR识别 5. 收集所有页面的识别结果并合并返回
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream PDF文件的输入流
	 * @return 包含解析状态和识别文本的Pair对象
	 * @throws IOException 当文件处理失败时抛出
	 */
	@NotNull
	public Pair<FileParserStatusEnums, String> getFileAIParser(AiDocumentEntity documentEntity, InputStream inputStream)
			throws IOException {

		PDDocument document = PDDocument.load(inputStream);
		PDFRenderer pdfRenderer = new PDFRenderer(document);

		// 循环处理每一页，将其转换为图片
		List<String> stringList = new ArrayList<>();
		for (int page = 0; page < document.getNumberOfPages(); page++) {
			// 渲染为 BufferedImage，DPI 可以根据需求调整，越高质量越好
			BufferedImage image = pdfRenderer.renderImageWithDPI(page, aiKnowledgeProperties.getVision().getDpi()); // 100
																													// DPI

			// 将图片保存为 PNG 文件

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, "PNG", baos);

			String encodeToString = Base64.getEncoder().encodeToString(baos.toByteArray());

			try {
				log.info("正在处理文件: {} AI OCR 解析， 处理 {} 页", documentEntity.getName(), page + 1);
				Pair<ChatModel, AiAssistantService> aiVisionAssistant = modelProvider.getAiVisionAssistant();
				UserMessage userMessage = UserMessage.from(
						TextContent.from(PromptBuilder.render("ocr-common.st", Map.of())),
						ImageContent.from(encodeToString, MediaType.IMAGE_PNG_VALUE));

				ChatResponse chatResponse = aiVisionAssistant.getKey().chat(userMessage);
				stringList.add(chatResponse.aiMessage().text());
			}
			catch (Exception exception) {
				log.error("文件: {} AI OCR 解析失败， 处理 {} 页", documentEntity.getName(), page + 1, exception);
				return Pair.of(FileParserStatusEnums.PARSE_FAIL,
						StrUtil.format("AI 解析失败， 处理 {} 页 ：{}", page + 1, ExceptionUtil.getMessage(exception)));
			}
			finally {
				baos.close();
			}
		}

		return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, stringList.stream().collect(Collectors.joining()));
	}

	/**
	 * 获取处理器的优先级顺序
	 * <p>
	 * 数值越大，优先级越高。该处理器的优先级为100， 在多个处理器都支持PDF文件时，会根据优先级决定使用顺序。
	 * @return 优先级值，返回100
	 */
	@Override
	public int getOrder() {
		return 100;
	}

}
