package com.deepdimen.matrix.knowledge.support.handler.source;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.SummaryStatusEnums;
import com.deepdimen.matrix.knowledge.support.handler.parse.UploadFileParseHandler;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 文件上传源处理器
 * <p>
 * 处理用户上传的各种格式文件 支持多种文件类型解析器，自动选择合适的解析器 包括OCR识别、AI解析等高级解析方式
 *
 * @author caoyuan
 * @date 2024/10/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadSourceTypeHandler extends AbstractFileSourceTypeHandler {

	/**
	 * 远程文件服务
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * 文件解析处理器列表 根据优先级自动选择合适的解析器
	 */
	private final List<UploadFileParseHandler> parseHandlerList;

	/**
	 * 数据集数据访问层
	 */
	private final AiDatasetMapper aiDatasetMapper;

	/**
	 * 文档数据访问层
	 */
	private final AiDocumentMapper documentMapper;

	/**
	 * 切片数据访问层
	 */
	private final AiSliceMapper sliceMapper;

	/**
	 * 判断是否支持指定的源类型
	 * @param sourceType 源类型
	 * @return 是否支持（仅支持UPLOAD类型）
	 */
	@Override
	public boolean supports(String sourceType) {
		return SourceTypeEnums.UPLOAD.getType().equals(sourceType);
	}

	/**
	 * 保存文档信息
	 * <p>
	 * 保存上传文件的元数据，设置解析状态
	 * @param aiDocumentDTO AI文档数据传输对象
	 * @return 保存后的文档实体
	 */
	@Override
	public AiDocumentEntity saveDocument(AiDocumentDTO aiDocumentDTO) {
		AiDocumentEntity file = aiDocumentDTO.getFiles().get(0);
		// 创建文档记录
		AiDocumentEntity document = new AiDocumentEntity();
		document.setId(aiDocumentDTO.getId());
		document.setCategoryId(aiDocumentDTO.getCategoryId());
		document.setDatasetId(aiDocumentDTO.getDatasetId());
		document.setFileStatus(aiDocumentDTO.getFileStatus());
		document.setSourceType(aiDocumentDTO.getSourceType());
		document.setDatasetId(aiDocumentDTO.getDatasetId());
		// 获取文件扩展名
		String extName = FileUtil.extName(file.getFileUrl());
		document.setFileType(extName);
		document.setName(file.getName());
		document.setFileUrl(file.getFileUrl());
		// 设置为AI解析中状态
		document.setSliceStatus(SliceStatusEnums.AI_PARSING.getStatus());
		documentMapper.insertOrUpdate(document);
		return document;
	}

	/**
	 * 保存文档切片
	 * <p>
	 * 下载文件内容，选择合适的解析器进行解析 将解析结果分割成切片并保存
	 * @param documentEntity 文档实体
	 * @param documentDTO 文档数据传输对象
	 */
	@Override
	@SneakyThrows
	public void saveSlice(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO) {
		// 从远程服务下载文件
		Response response = remoteFileService.getFile(documentEntity.getFileUrl());
		String extName = FileUtil.extName(documentEntity.getFileUrl());
		try (InputStream inputStream = response.body().asInputStream();
				BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
			bufferedInputStream.mark(512);
			byte[] headBytes = bufferedInputStream.readNBytes(512);
			bufferedInputStream.reset();
			String actualExtName = detectActualExtName(extName, headBytes);
			// 查询知识库配置
			AiDatasetEntity aiDatasetEntity = aiDatasetMapper.selectById(documentEntity.getDatasetId());
			if (aiDatasetEntity == null) {
				throw new IllegalStateException("知识库不存在或无权限访问，datasetId=" + documentEntity.getDatasetId());
			}

			// 选择优先级最高的解析器
			UploadFileParseHandler uploadFileParseHandler = parseHandlerList.stream()
				.filter(handler -> handler.supports(aiDatasetEntity, documentEntity, actualExtName))
				.max(Comparator.comparingInt(Ordered::getOrder))
				.orElseThrow(() -> new IllegalStateException("未找到支持的文件解析器，fileType=" + actualExtName));

			// 执行文件解析
			Pair<FileParserStatusEnums, String> parserFile = uploadFileParseHandler.file2String(documentEntity,
					bufferedInputStream, actualExtName);

			// 保存解析结果
			saveResult(uploadFileParseHandler, parserFile, documentEntity, aiDatasetEntity, documentDTO);
		}
	}

	/**
	 * 保存解析结果
	 * <p>
	 * 根据解析状态进行不同处理： - 解析失败：更新失败状态和原因 - 解析中：更新解析状态（OCR或AI解析） - 解析成功：清洗数据、分割切片并保存
	 * @param uploadFileParseHandler 文件解析处理器
	 * @param parserFile 解析结果（状态和内容）
	 * @param documentEntity 文档实体
	 * @param aiDatasetEntity 数据集配置
	 * @param documentDTO 文档数据传输对象
	 */
	public void saveResult(UploadFileParseHandler uploadFileParseHandler,
			Pair<FileParserStatusEnums, String> parserFile, AiDocumentEntity documentEntity,
			AiDatasetEntity aiDatasetEntity, AiDocumentDTO documentDTO) {
		// 处理解析失败情况
		if (FileParserStatusEnums.PARSE_FAIL.equals(parserFile.getKey())) {
			log.warn("文件:{}  解析失败", documentEntity.getName());
			documentEntity.setSliceStatus(SliceStatusEnums.FAILED.getStatus());
			documentEntity.setSliceFailReason(parserFile.getValue());
			documentMapper.updateById(documentEntity);
			throw new DocumentProcessingStoppedException(parserFile.getValue());
		}

		// 处理OCR解析中状态
		if (FileParserStatusEnums.OCR_PARSING.equals(parserFile.getKey())) {
			documentEntity.setSliceStatus(SliceStatusEnums.OCR_PARSING.getStatus());
			documentEntity.setSliceFailReason(parserFile.getValue());
			documentMapper.updateById(documentEntity);
			throw new DocumentProcessingStoppedException(parserFile.getValue());
		}

		// 处理AI解析中状态
		if (FileParserStatusEnums.AI_PARSING.equals(parserFile.getKey())) {
			documentEntity.setSliceStatus(SliceStatusEnums.AI_PARSING.getStatus());
			// 失败原因字段临时存储批次ID
			documentEntity.setSliceFailReason(parserFile.getValue());
			documentMapper.updateById(documentEntity);
			throw new DocumentProcessingStoppedException(parserFile.getValue());
		}

		// 清洗解析后的数据
		String cleanResultData = uploadFileParseHandler.cleanData(parserFile.getValue());

		// 根据配置分割数据
		List<String> contentList = uploadFileParseHandler.splitData(documentEntity, aiDatasetEntity, cleanResultData,
				documentDTO);

		// 检查解析结果是否为空
		if (CollUtil.isEmpty(contentList) || StrUtil.isBlank(contentList.get(0))) {
			log.warn("文件:{}  解析失败", documentEntity.getName());
			documentEntity.setSliceStatus(SliceStatusEnums.FAILED.getStatus());

			if (StrUtil.isBlank(documentEntity.getSliceFailReason())) {
				documentEntity.setSliceFailReason("文件解析失败，资源不包含文本内容");
			}

			documentMapper.updateById(documentEntity);
			throw new DocumentProcessingStoppedException(documentEntity.getSliceFailReason());
		}

		// 批量保存切片
		for (String content : contentList) {
			if (StrUtil.isBlank(content)) {
				continue;
			}
			AiSliceEntity slice = new AiSliceEntity();
			slice.setDocumentId(documentEntity.getId());
			slice.setContent(content);
			slice.setName(documentEntity.getName());
			slice.setSliceStatus(SliceStatusEnums.UNSLICED.getStatus());
			slice.setCharCount((long) content.length());
			slice.setCreateBy(documentEntity.getCreateBy());
			slice.setCreateTime(LocalDateTime.now());
			sliceMapper.insert(slice);
		}

		// 更新文档状态为已切片
		documentEntity.setSummaryStatus(SummaryStatusEnums.UNSUMMARY.getStatus());
		documentEntity.setSliceStatus(SliceStatusEnums.SLICED.getStatus());
		documentMapper.updateById(documentEntity);
	}

	private String detectActualExtName(String extName, byte[] headBytes) {
		if (FileTypeEnums.DOC.getType().equals(extName) && isHtmlContent(headBytes)) {
			log.info("识别到HTML伪装DOC文件，按HTML解析");
			return FileTypeEnums.HTML.getType();
		}
		return extName;
	}

	private boolean isHtmlContent(byte[] headBytes) {
		if (headBytes == null || headBytes.length == 0) {
			return false;
		}
		String head = new String(headBytes, 0, Math.min(headBytes.length, 512), StandardCharsets.UTF_8).trim()
			.toLowerCase(Locale.ROOT);
		return head.startsWith("<!doctype html") || head.startsWith("<html") || head.startsWith("<head")
				|| head.startsWith("<body") || head.startsWith("<table") || head.contains("<html")
				|| head.contains("<table");
	}

}
