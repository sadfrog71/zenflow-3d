package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.IdUtil;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.admin.api.utils.ByteArrayMultipartFile;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import com.deepdimen.matrix.knowledge.support.util.MarkdownUtil;
import com.torchv.infra.unstructured.parser.word.UnstructuredWord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TorchVUploadFileParseHandler implements UploadFileParseHandler {

	private final RemoteFileService remoteFileService;

	private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "svg", "tiff", "tif",
			"wmf", "emf");

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
		return FileTypeEnums.DOC.getType().equals(contentType) || FileTypeEnums.DOCX.getType().equals(contentType);
	}

	/**
	 * 将Office文档转换为纯文本
	 * <p>
	 * 使用TorchV UnstructuredWord解析Word文档，输出Markdown内容。
	 * 对于docx文件，额外提取嵌入图片上传到文件服务，并替换markdown中的embedded:引用。
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和文本内容的Pair对象，成功时返回提取的文本，失败时返回错误信息
	 */
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		try {
			byte[] fileBytes = inputStream.readAllBytes();
			String content = UnstructuredWord.toMarkdown(new ByteArrayInputStream(fileBytes), documentEntity.getName());

			log.info("TorchV解析完成, 文件={}, extName={}, markdown长度={}", documentEntity.getName(), extName,
					content == null ? 0 : content.length());

			if (FileTypeEnums.DOCX.getType().equals(extName)) {
				Map<String, String> imageMapping = extractAndUploadImages(fileBytes);
				log.info("docx图片提取完成, 文件={}, 图片数={}", documentEntity.getName(), imageMapping.size());
				if (!imageMapping.isEmpty()) {
					content = MarkdownUtil.replaceEmbeddedImages(content, imageMapping,
							"/api/admin/sys-file/oss/file?fileName=");
				}
			}

			return Pair.of(FileParserStatusEnums.PARSE_SUCCESS, content);
		}
		catch (Exception e) {
			log.error("文件 {} torchv 解析失败", documentEntity.getName(), e);
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, e.getMessage());
		}
	}

	/**
	 * 获取处理器的优先级顺序
	 * <p>
	 * 数值越大，优先级越高。当多个处理器都支持同一文件类型时， 系统会优先使用优先级高的处理器。默认优先级为0。 实现类可以重写此方法来设置自定义优先级。
	 * @return 优先级值，默认返回0
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
