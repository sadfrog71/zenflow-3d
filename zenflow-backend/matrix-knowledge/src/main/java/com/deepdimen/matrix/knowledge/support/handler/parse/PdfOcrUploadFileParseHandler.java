package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import com.deepdimen.matrix.admin.api.utils.ByteArrayMultipartFile;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.UmiOcrPDFModelDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.service.AiOCRAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * PDF文档OCR解析处理器
 * <p>
 * 该处理器使用专门的OCR服务处理PDF文档，提供比AI视觉模型更专业的文档识别能力。 处理流程如下： 1. 首先尝试使用PDFBox直接提取文本（处理可搜索的PDF） 2.
 * 如果提取失败（影印版PDF），则上传到OCR服务进行异步处理 3. 返回OCR任务ID，后续可查询处理结果
 *
 * 注意：该处理器需要配置中启用CNOCR服务才能使用。
 *
 * @author caoyuan
 * @date 2024/3/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfOcrUploadFileParseHandler implements UploadFileParseHandler {

	private final AiKnowledgeProperties aiKnowledgeProperties;

	private final AiOCRAssistantService ocrAssistantService;

	/**
	 * 判断是否支持处理指定类型的文件
	 * <p>
	 * 当文档为PDF格式且系统配置中启用了CNOCR服务时，返回true。 该处理器专门使用OCR服务处理PDF文档。
	 * @param aiDataset 数据集实体
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果是PDF格式且OCR服务已启用返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());
		return FileTypeEnums.PDF.getType().equals(contentType) && aiKnowledgeProperties.getCnocr().isEnabled();
	}

	/**
	 * 解析PDF文档内容
	 * <p>
	 * 该方法首先尝试使用PDFBox直接提取PDF中的文本内容。 如果提取成功，直接返回文本内容。
	 * 如果提取失败（通常是影印版PDF），则将文件上传到OCR服务进行异步处理， 返回OCR_PARSING状态和任务ID，后续可通过任务ID查询处理结果。
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream PDF文件的输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和结果的Pair对象，成功时返回文本，OCR处理时返回任务ID，失败时返回错误信息
	 */
	@SneakyThrows
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		ApachePdfBoxDocumentParser pdfBoxDocumentParser = new ApachePdfBoxDocumentParser();
		try {
			// 解析PDF ，非影印版本
			Document document = pdfBoxDocumentParser.parse(inputStream);
			return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, document.text());
		}
		catch (Exception e) {
			ByteArrayMultipartFile byteArrayMultipartFile = new ByteArrayMultipartFile(documentEntity.getName(),
					documentEntity.getName(), null, inputStream);

			try {
				UmiOcrPDFModelDTO.UmiOcrPDFModelResponse umiOcrPDFModelResponse = ocrAssistantService
					.uploadDoc(byteArrayMultipartFile);
				return Pair.of(FileParserStatusEnums.OCR_PARSING, umiOcrPDFModelResponse.getData());
			}
			catch (Exception exception) {
				log.error("文件: {} OCR 解析失败", documentEntity.getName());
				return Pair.of(FileParserStatusEnums.PARSE_FAIL, String.format("OCR 解析失败:%s", e.getMessage()));
			}
		}
	}

}
