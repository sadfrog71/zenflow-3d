package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.admin.api.utils.ByteArrayMultipartFile;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.AiMarkitdownDTO;
import com.deepdimen.matrix.knowledge.dto.MarkitdownResponseDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiMarkitdownAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.util.MarkdownUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Office文档转Markdown解析处理器
 * <p>
 * 该处理器使用Markitdown服务将Office文档（DOCX、PDF、PPTX、XLSX）转换为Markdown格式。
 * Markitdown服务可以保留文档的结构和格式信息，转换后的Markdown文本更适合进行后续的文本分析和处理。 如果配置了视觉模型，还可以对文档中的图片进行OCR识别。
 *
 * @author caoyuan
 * @date 2024/12/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Office2MdUploadFileParseHandler implements UploadFileParseHandler {

	private final AiKnowledgeProperties aiKnowledgeProperties;

	private final AiMarkitdownAssistantService markitdownAssistantService;

	private final AiModelMapper aiModelMapper;

	private final RemoteFileService remoteFileService;

	private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "svg", "tiff", "tif",
			"wmf", "emf");

	/**
	 * 判断是否支持处理指定类型的文件
	 * <p>
	 * 首先检查Markitdown服务是否启用，如果未启用则不支持任何文件。
	 * 当Markitdown服务启用时，支持处理DOCX、PDF、PPTX、XLSX格式的Office文档。
	 * @param aiDataset 数据集实体
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果支持该文件类型返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());
		return aiKnowledgeProperties.getMarkitdown().isEnabled() && (FileTypeEnums.DOCX.getType().equals(contentType)
				|| FileTypeEnums.PDF.getType().equals(contentType) || FileTypeEnums.PPTX.getType().equals(contentType)
				|| FileTypeEnums.XLSX.getType().equals(contentType));
	}

	/**
	 * 将Office文档转换为Markdown格式文本
	 * <p>
	 * 该方法执行以下步骤： 1. 将输入流转换为ByteArrayMultipartFile对象 2. 查询默认的视觉模型（如果有），用于处理文档中的图片 3.
	 * 如果找到视觉模型，设置模型参数和OCR提示词 4. 调用Markitdown服务进行文档转换
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和Markdown文本的Pair对象，成功时返回转换后的文本，失败时返回错误信息
	 */
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		try {

			ByteArrayMultipartFile file = new ByteArrayMultipartFile(documentEntity.getName(), documentEntity.getName(),
					null, inputStream.readAllBytes());

			AiMarkitdownDTO request = new AiMarkitdownDTO();

			// 查询默认的视觉模型
			AiModelEntity aiModelEntity = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
				.eq(AiModelEntity::getModelType, ModelTypeEnums.VISION.getType())
				.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode()), false);

			if (Objects.nonNull(aiModelEntity)) {
				request.setModel(aiModelEntity.getModelName());
				request.setApi_key(aiModelEntity.getApiKey());
				request.setBase_url(aiModelEntity.getBaseUrl());
				request.setPrompt(PromptBuilder.render("ocr-image.st"));
			}

			MarkitdownResponseDTO markitdownResponseDTO = markitdownAssistantService.upload(file, request);
			String markdownText = markitdownResponseDTO.getText();

			log.info("Office2Md解析完成, 文件={}, extName={}, markdown长度={}", documentEntity.getName(), extName,
					markdownText == null ? 0 : markdownText.length());

			if (FileTypeEnums.DOCX.getType().equals(extName)) {
				Map<String, String> imageMapping = extractAndUploadImages(file.getBytes());
				log.info("docx图片提取完成, 文件={}, 图片数={}", documentEntity.getName(), imageMapping.size());
				if (!imageMapping.isEmpty()) {
					markdownText = MarkdownUtil.replaceEmbeddedImages(markdownText, imageMapping,
							"/api/admin/sys-file/oss/file?fileName=");
				}
			}

			return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, markdownText);
		}
		catch (Exception e) {
			log.error("文件 {} 解析失败", documentEntity.getName(), e);
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, e.getMessage());
		}
	}

	/**
	 * 获取处理器的优先级顺序
	 * <p>
	 * 数值越大，优先级越高。该处理器的优先级为100， 在多个处理器都支持同一文件类型时的加载顺序。
	 * @return 优先级值，返回100
	 */
	@Override
	public int getOrder() {
		return 100;
	}

	/**
	 * 从docx文件中提取嵌入图片并上传到文件服务
	 * @param fileBytes docx文件的字节数组
	 * @return 图片文件名映射：原始文件名 -> 上传后的文件名
	 */
	private Map<String, String> extractAndUploadImages(byte[] fileBytes) {
		Map<String, String> imageMapping = new HashMap<>();
		File tempFile = null;
		try {
			tempFile = FileUtil.createTempFile(".docx", true);
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				fos.write(fileBytes);
			}

			try (ZipFile zipFile = new ZipFile(tempFile)) {
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String entryName = entry.getName();
					if (!entryName.startsWith("word/media/") || entry.isDirectory()) {
						continue;
					}
					String fileName = entryName.substring("word/media/".length());
					String ext = FileUtil.extName(fileName);
					if (ext == null || !IMAGE_EXTENSIONS.contains(ext.toLowerCase())) {
						log.debug("跳过非图片文件: {}", entryName);
						continue;
					}
					byte[] imageBytes = IoUtil.readBytes(zipFile.getInputStream(entry));
					String uploadName = IdUtil.fastSimpleUUID() + "_" + fileName;
					try {
						remoteFileService.innerUpload(
								new ByteArrayMultipartFile(uploadName, uploadName, null, imageBytes), uploadName);
						imageMapping.put(fileName, uploadName);
						log.info("docx图片上传成功: {} -> {}", fileName, uploadName);
					}
					catch (Exception uploadEx) {
						log.warn("docx图片上传失败: {}, error={}", fileName, uploadEx.getMessage());
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("docx图片提取上传失败: {}", e.getMessage(), e);
		}
		finally {
			FileUtil.del(tempFile);
		}
		return imageMapping;
	}

}
