package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * HTML文件解析处理器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HtmlUploadFileParseHandler implements UploadFileParseHandler {

	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String extName = FileUtil.extName(documentEntity.getFileUrl());
		return supports(aiDataset, documentEntity, extName);
	}

	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity, String extName) {
		return FileTypeEnums.HTML.getType().equals(extName) || FileTypeEnums.HTM.getType().equals(extName);
	}

	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		try {
			org.jsoup.nodes.Document document = Jsoup.parse(inputStream, null, "");
			String bodyHtml = document.body() == null ? null : document.body().html();
			String content = StrUtil.blankToDefault(bodyHtml, document.text());
			return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, content);
		}
		catch (Exception e) {
			log.error("文件 {} HTML解析失败", documentEntity.getName(), e);
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, "HTML解析失败: " + e.getMessage());
		}
	}

	@Override
	public int getOrder() {
		return 200;
	}

}
