package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Office文档解析处理器
 * <p>
 * 该处理器使用Apache POI库解析Office文档，支持旧版和新版Office格式： - Word文档：DOC、DOCX -
 * PowerPoint演示文稿：PPT、PPTX - Excel表格：XLS、XLSX
 *
 * 该处理器直接提取文档中的文本内容，不保留格式信息。
 *
 * @author caoyuan
 * @date 2024/10/2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeUploadFileParseHandler implements UploadFileParseHandler {

	/**
	 * 判断是否支持处理指定类型的文件
	 * <p>
	 * 支持所有常见的Office文档格式，包括旧版（.doc、.ppt、.xls） 和新版（.docx、.pptx、.xlsx）格式。
	 * @param aiDataset 数据集实体
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果是支持的Office格式返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());
		return FileTypeEnums.DOC.getType().equals(contentType) || FileTypeEnums.DOCX.getType().equals(contentType)
				|| FileTypeEnums.PPT.getType().equals(contentType) || FileTypeEnums.PPTX.getType().equals(contentType)
				|| FileTypeEnums.XLS.getType().equals(contentType) || FileTypeEnums.XLSX.getType().equals(contentType);
	}

	/**
	 * 将Office文档转换为纯文本
	 * <p>
	 * 使用Apache POI的DocumentParser解析Office文档，提取其中的文本内容。 该方法会忽略文档的格式信息，只返回纯文本内容。
	 * 如果解析失败，会记录错误日志并返回失败状态。
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和文本内容的Pair对象，成功时返回提取的文本，失败时返回错误信息
	 */
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		try {
			Document textDocument = new ApachePoiDocumentParser().parse(inputStream);
			return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, textDocument.text());
		}
		catch (Exception e) {
			log.error("文件 {} 解析失败", documentEntity.getName(), e);
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, e.getMessage());
		}
	}

}
