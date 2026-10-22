package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 文本文件解析处理器
 * <p>
 * 该处理器用于解析纯文本格式的文件，支持TXT和Markdown（MD）格式。
 * 使用LangChain4j的TextDocumentParser直接读取文件内容，无需进行格式转换。 这是最简单的文件解析器，适用于已经是文本格式的文件。
 *
 * @author caoyuan
 * @date 2024/10/2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextUploadFileParseHandler implements UploadFileParseHandler {

	/**
	 * 判断是否支持处理指定类型的文件
	 * <p>
	 * 支持TXT和MD（Markdown）格式的纯文本文件。
	 * @param aiDataset 数据集实体
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果是TXT或MD格式返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());
		return FileTypeEnums.TXT.getType().equals(contentType) || FileTypeEnums.MD.getType().equals(contentType);
	}

	/**
	 * 将文本文件内容读取为字符串
	 * <p>
	 * 使用TextDocumentParser直接解析输入流，提取其中的文本内容。 该方法处理简单直接，不需要进行格式转换或特殊处理。
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和文本内容的Pair对象，始终返回成功状态
	 */
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		Document textDocument = new TextDocumentParser().parse(inputStream);
		return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, textDocument.text());
	}

}
