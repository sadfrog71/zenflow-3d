package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.knowledge.dto.UmiOcrImageModelDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.service.AiOCRAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * 图片OCR解析处理器
 * <p>
 * 该处理器使用第三方OCR服务（UmiOCR）对图片文件进行文字识别。 支持PNG、JPG、JPEG格式的图片文件，通过调用OCR服务API提取图片中的文本内容。
 * 相比AI视觉模型，OCR服务通常更快速、更专注于文字识别任务。
 *
 * @author caoyuan
 * @date 2024/3/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageOcrUploadFileParseHandler implements UploadFileParseHandler {

	private final AiOCRAssistantService ocrAssistantService;

	/**
	 * 判断是否支持处理指定的文档
	 * <p>
	 * 当文档为图片格式（PNG、JPG、JPEG）时，返回true。 该处理器不需要检查OCR开关，因为它本身就是专门的OCR处理器。
	 * @param aiDataset 数据集实体
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果是支持的图片格式返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());

		return FileTypeEnums.PNG.getType().equals(contentType) || FileTypeEnums.JPG.getType().equals(contentType)
				|| FileTypeEnums.JPEG.getType().equals(contentType);
	}

	/**
	 * 将图片文件转换为文本内容
	 * <p>
	 * 该方法将图片文件编码为Base64格式，然后调用UmiOCR服务进行文字识别。 OCR服务返回的响应包含识别的文本数据，该方法会将所有识别的文本片段拼接成完整的文本。
	 * 如果OCR服务返回错误码（非100），则认为解析失败。
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
		UmiOcrImageModelDTO.UmiOcrImageModelRequest request = new UmiOcrImageModelDTO.UmiOcrImageModelRequest();
		request.setBase64(encodeToString);

		UmiOcrImageModelDTO.UmiOcrImageModelResponse umiOcrImageModelResponse = null;
		try {
			String ocrImageResult = ocrAssistantService.image(request);
			umiOcrImageModelResponse = JSONUtil.toBean(ocrImageResult,
					UmiOcrImageModelDTO.UmiOcrImageModelResponse.class);
		}
		catch (Exception e) {
			log.error("文件: {} OCR 解析失败", documentEntity.getName());
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, String.format("OCR 解析失败:%s", e.getMessage()));
		}

		if (100 != umiOcrImageModelResponse.getCode()) {
			log.error("文件: {} OCR 解析失败", documentEntity.getName());
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, String.format("OCR 解析失败:%s", documentEntity.getName()));
		}

		String ocrContent = umiOcrImageModelResponse.getData()
			.stream()
			.map(UmiOcrImageModelDTO.UmiOcrImageModelResponse.TextData::getText)
			.collect(Collectors.joining());
		return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, ocrContent);
	}

}
