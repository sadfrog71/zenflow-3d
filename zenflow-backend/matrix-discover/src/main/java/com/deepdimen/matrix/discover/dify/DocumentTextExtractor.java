package com.deepdimen.matrix.discover.dify;

import cn.hutool.cache.impl.TimedCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 文档文本提取器 - 用于 Dify agent-chat 模式下的文档内容注入。
 * <p>
 * agent-chat 模式下 Dify 只能处理图片（Vision），无法自动提取文档文本。
 * 此组件在文件上传时用 Tika 提取文本并缓存，对话时将文本追加到 query 中。
 */
@Slf4j
@Component
public class DocumentTextExtractor {

	private static final int MAX_TEXT_LENGTH = 16000;

	private static final int MAX_TOTAL_TEXT_LENGTH = 32000;

	private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

	private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
			"txt", "md", "markdown", "csv", "html", "htm", "xml", "json", "yaml", "yml", "epub");

	private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "csv", "html", "htm",
			"xml", "json", "yaml", "yml");

	private final Tika tika = new Tika();

	private final TimedCache<String, String> textCache = new TimedCache<>(CACHE_TTL_MS);

	public boolean isDocumentType(String filename) {
		if (filename == null) {
			return false;
		}
		int dotIdx = filename.lastIndexOf('.');
		if (dotIdx < 0) {
			return false;
		}
		String ext = filename.substring(dotIdx + 1).toLowerCase();
		return DOCUMENT_EXTENSIONS.contains(ext);
	}

	/**
	 * 同步提取文档文本并缓存
	 * @param uploadFileId Dify 返回的文件 ID
	 * @param file 上传的文件
	 */
	public void extractAndCache(String uploadFileId, MultipartFile file) {
		if (uploadFileId == null || file == null) {
			return;
		}
		String filename = fixFilename(file.getOriginalFilename());
		String ext = extractExtension(filename);
		try {
			String text;
			if (PLAIN_TEXT_EXTENSIONS.contains(ext)) {
				text = new String(file.getBytes(), StandardCharsets.UTF_8);
				if (text.length() > MAX_TEXT_LENGTH + 1000) {
					text = text.substring(0, MAX_TEXT_LENGTH + 1000);
				}
			}
			else {
				try (InputStream is = file.getInputStream()) {
					text = tika.parseToString(is, new Metadata(), MAX_TEXT_LENGTH + 1000);
				}
			}
			if (text != null && !text.isBlank()) {
				int originalLength = text.length();
				if (text.length() > MAX_TEXT_LENGTH) {
					text = text.substring(0, MAX_TEXT_LENGTH) + "\n...(文档内容已截断)";
				}
				textCache.put(uploadFileId, text.strip());
				log.info("[DocumentTextExtractor] 文档文本提取成功, fileId={}, 文件名={}, 原始长度={}, 缓存长度={}",
						uploadFileId, filename, originalLength, text.length());
			}
			else {
				log.info("[DocumentTextExtractor] 文档文本为空, fileId={}, 文件名={}", uploadFileId, filename);
			}
		}
		catch (Exception e) {
			log.warn("[DocumentTextExtractor] 文档文本提取失败, fileId={}, 文件名={}, error={}", uploadFileId, filename,
					e.getMessage());
		}
	}

	private static String extractExtension(String filename) {
		if (filename == null) {
			return "";
		}
		int dotIdx = filename.lastIndexOf('.');
		return dotIdx >= 0 ? filename.substring(dotIdx + 1).toLowerCase() : "";
	}

	/**
	 * 修复 Undertow multipart 文件名编码（UTF-8 被当 ISO-8859-1 解码）
	 */
	public static String fixFilename(String filename) {
		if (filename == null) {
			return null;
		}
		try {
			byte[] bytes = filename.getBytes(StandardCharsets.ISO_8859_1);
			String fixed = new String(bytes, StandardCharsets.UTF_8);
			if (!fixed.equals(filename) && !fixed.contains("\uFFFD")) {
				return fixed;
			}
		}
		catch (Exception ignored) {
		}
		return filename;
	}

	/**
	 * 根据 upload_file_id 获取缓存的文档文本
	 * @return 文档文本，不存在则返回 null
	 */
	public String getCachedText(String uploadFileId) {
		return textCache.get(uploadFileId, false);
	}

	public int getMaxTotalTextLength() {
		return MAX_TOTAL_TEXT_LENGTH;
	}

}
