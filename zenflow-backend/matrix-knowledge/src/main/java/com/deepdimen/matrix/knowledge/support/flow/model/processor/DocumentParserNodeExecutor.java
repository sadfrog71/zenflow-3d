package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.support.constant.DocumentParseStrategyEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.ExecutionStatusEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiDocumentParserNode;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档解析节点执行器 用于执行Office和PDF文档的文本提取操作 支持Word、Excel、PowerPoint、PDF等格式
 * 自动选择最佳解析策略：优先使用标准解析器，失败时降级到AI视觉识别
 *
 * @author caoyuan
 * @date 2025/01/25
 */
@Slf4j
@Component(NodeTypeConstants.DOCUMENT_PARSER)
@RequiredArgsConstructor
public class DocumentParserNodeExecutor extends AbstractNodeProcessor {

	/**
	 * AI知识库配置
	 */
	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * 远程文件服务
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * AI模型提供者，用于获取视觉模型进行AI解析
	 */
	private final ModelProvider modelProvider;

	/**
	 * 支持的Office文件扩展名
	 */
	private static final List<String> OFFICE_EXTENSIONS = List.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");

	/**
	 * 支持的PDF文件扩展名
	 */
	private static final String PDF_EXTENSION = "pdf";

	/**
	 * 执行文档解析节点的具体逻辑 1. 验证节点配置 2. 从输入参数获取 fileUrl 3. 下载文件到内存 4. 根据文件类型选择解析器 5.
	 * 优先使用标准解析器(Apache POI/PDFBox) 6. 失败时降级到AI视觉识别 7. 返回提取的文本内容
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 包含提取文本的字典
	 */
	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 1. 验证配置
			AiDocumentParserNode config = node.getDocumentParserParams();
			if (config == null) {
				config = new AiDocumentParserNode();
				config.setParseStrategy(DocumentParseStrategyEnums.AUTO.getStrategy());
			}

			// 2. 从输入参数获取 fileUrl
			Dict variables = getInputVariables(node, context);
			String fileUrl = variables.getStr("fileUrl");

			// 3. 验证 fileUrl
			if (StrUtil.isBlank(fileUrl)) {
				throw FlowException.invalidParam("文档解析节点未接收到 fileUrl 参数，请检查上游节点输出");
			}

			// 4. 获取文件扩展名
			String extName = FileUtil.extName(fileUrl).toLowerCase();
			if (!isSupportedFileType(extName)) {
				throw FlowException.invalidParam("不支持的文件格式: " + extName + "。支持的格式: Word, Excel, PowerPoint, PDF");
			}

			log.info("文档解析节点[{}]开始执行: fileUrl={}, 文件类型={}, 解析策略={}", node.getId(), fileUrl, extName,
					config.getParseStrategy());

			// 5. 下载文件到内存（一次性读取到字节数组，支持多次解析策略降级）
			Response response = remoteFileService.getFile(fileUrl);
			byte[] fileBytes = IoUtil.readBytes(response.body().asInputStream());

			// 6. 解析文档
			String extractedText = parseDocument(fileBytes, extName, config, node, context);

			// 7. 验证结果
			if (StrUtil.isBlank(extractedText)) {
				throw FlowException.nodeError(node.getId(), "文档解析结果为空");
			}

			log.info("文档解析节点[{}]执行成功，提取文本长度: {} 字符", node.getId(), extractedText.length());

			// 8. 更新节点执行状态
			node.setStatus(ExecutionStatusEnums.SUCCESS.getValue());
			node.setDuration(context.getDuration());
			context.getExecutedNodes().add(node);

			// 9. 如果是流式模式，发送流式回调
			if (context.isStream()) {
				context.sendStreamEnd(node, null);
			}

			// 10. 返回提取的文本
			return Dict.create().set(FlowConstant.RESULT, extractedText);

		}
		catch (FlowException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("文档解析节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[文档解析节点] -> " + e.getMessage());
		}
	}

	/**
	 * 判断文件类型是否支持
	 * @param extName 文件扩展名
	 * @return true-支持 false-不支持
	 */
	private boolean isSupportedFileType(String extName) {
		return OFFICE_EXTENSIONS.contains(extName) || PDF_EXTENSION.equals(extName);
	}

	/**
	 * 解析文档内容
	 * @param fileBytes 文件字节数组（支持多次创建流以实现解析策略降级）
	 * @param extName 文件扩展名
	 * @param config 节点配置
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 提取的文本内容
	 * @throws Exception 解析异常
	 */
	private String parseDocument(byte[] fileBytes, String extName, AiDocumentParserNode config, AiNodeDefinition node,
			FlowContextHolder context) throws Exception {

		DocumentParseStrategyEnums strategy = DocumentParseStrategyEnums.fromStrategy(config.getParseStrategy());

		// 如果策略为 AI，直接使用AI解析
		if (strategy == DocumentParseStrategyEnums.AI) {
			return parseWithAI(new ByteArrayInputStream(fileBytes), extName, config, node, context);
		}

		// 如果策略允许标准解析，优先使用标准解析器
		if (strategy.allowsStandard()) {
			try {
				String text = parseWithStandardParser(new ByteArrayInputStream(fileBytes), extName, context);
				if (StrUtil.isNotBlank(text)) {
					return text;
				}
			}
			catch (Exception e) {
				log.warn("标准解析器解析失败: {}", e.getMessage());
				// 如果策略为 STANDARD，不降级，直接抛出异常
				if (strategy == DocumentParseStrategyEnums.STANDARD) {
					throw e;
				}
			}
		}

		// 如果标准解析失败且策略允许AI，降级到AI解析
		if (strategy.requiresAI()) {
			log.info("标准解析失败，降级使用AI视觉识别");
			return parseWithAI(new ByteArrayInputStream(fileBytes), extName, config, node, context);
		}

		throw new RuntimeException("文档解析失败: 无可用的解析策略");
	}

	/**
	 * 使用标准解析器解析文档
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @param context 执行上下文
	 * @return 提取的文本内容
	 * @throws Exception 解析异常
	 */
	private String parseWithStandardParser(InputStream inputStream, String extName, FlowContextHolder context)
			throws Exception {

		Document document;
		if (OFFICE_EXTENSIONS.contains(extName)) {
			// 使用 Apache POI 解析 Office 文档
			document = new ApachePoiDocumentParser().parse(inputStream);
		}
		else if (PDF_EXTENSION.equals(extName)) {
			// 使用 PDFBox 解析 PDF
			document = new ApachePdfBoxDocumentParser().parse(inputStream);
		}
		else {
			throw new IllegalArgumentException("不支持的文件类型: " + extName);
		}

		return document.text();
	}

	/**
	 * 使用AI视觉模型解析文档 将文档转换为图片后，使用视觉模型进行OCR识别
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @param config 节点配置
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 提取的文本内容
	 * @throws Exception 解析异常
	 */
	private String parseWithAI(InputStream inputStream, String extName, AiDocumentParserNode config,
			AiNodeDefinition node, FlowContextHolder context) throws Exception {

		// 仅支持PDF的AI解析
		if (!PDF_EXTENSION.equals(extName)) {
			throw new UnsupportedOperationException("AI解析当前仅支持PDF格式，" + extName + "格式暂不支持");
		}

		// 加载PDF文档
		byte[] pdfBytes = IoUtil.readBytes(inputStream);
		PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes));
		PDFRenderer pdfRenderer = new PDFRenderer(document);

		// 循环处理每一页
		List<String> pageTexts = new ArrayList<>();
		int totalPages = document.getNumberOfPages();

		for (int page = 0; page < totalPages; page++) {
			// 渲染页面为图片
			BufferedImage image = pdfRenderer.renderImageWithDPI(page, aiKnowledgeProperties.getVision().getDpi());

			// 将图片转换为Base64
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, "PNG", baos);
			String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

			try {
				log.info("正在处理文档 AI OCR 解析，处理第 {} 页", page + 1);

				// 调用AI视觉模型
				ChatModel visionModel = modelProvider.getAiVisionAssistant().getKey();
				UserMessage userMessage = UserMessage.from(
						TextContent.from(PromptBuilder.render("ocr-common.st", Map.of())),
						ImageContent.from(base64Image, MediaType.IMAGE_PNG_VALUE));

				ChatResponse chatResponse = visionModel.chat(userMessage);
				String pageText = chatResponse.aiMessage().text();
				pageTexts.add(pageText);

				if (context.isStream()) {
					context.sendStreamContent(pageText);
				}
			}
			catch (Exception exception) {
				log.error("AI OCR 解析失败，处理第 {} 页", page + 1, exception);
				throw new RuntimeException(StrUtil.format("AI 解析失败，处理第 {} 页: {}", page + 1, exception.getMessage()));
			}
			finally {
				baos.close();
			}
		}

		document.close();
		return pageTexts.stream().collect(Collectors.joining("\n\n"));
	}

}
