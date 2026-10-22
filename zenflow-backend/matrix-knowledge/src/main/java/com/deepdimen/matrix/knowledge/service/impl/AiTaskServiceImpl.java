package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.admin.api.utils.ByteArrayMultipartFile;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.MineruBatchResponseDTO;
import com.deepdimen.matrix.knowledge.dto.UmiOcrPDFModelDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.service.AiDatasetService;
import com.deepdimen.matrix.knowledge.service.AiDocumentService;
import com.deepdimen.matrix.knowledge.service.AiMineruAssistantService;
import com.deepdimen.matrix.knowledge.service.AiOCRAssistantService;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import com.deepdimen.matrix.knowledge.service.AiTaskService;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.MineruParseStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SummaryStatusEnums;
import com.deepdimen.matrix.knowledge.support.handler.parse.MineruLocalTaskCache;
import com.deepdimen.matrix.knowledge.support.handler.parse.UploadFileParseHandler;
import com.deepdimen.matrix.knowledge.support.handler.source.UploadSourceTypeHandler;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.knowledge.support.util.MarkdownUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipFile;

/**
 * AI定时任务服务实现类
 * <p>
 * 处理文档解析、总结和切片的异步任务 支持OCR和Mineru解析结果的更新 提供失败任务的重试机制
 *
 * @author caoyuan
 * @date 2024/6/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskServiceImpl implements AiTaskService {

	/**
	 * 文档服务
	 */
	private final AiDocumentService documentService;

	/**
	 * 数据集服务
	 */
	private final AiDatasetService datasetService;

	/**
	 * 切片服务
	 */
	private final AiSliceService sliceService;

	/**
	 * OCR助手服务
	 */
	private final AiOCRAssistantService ocrAssistantService;

	/**
	 * PDF OCR文件解析处理器
	 */
	private final UploadFileParseHandler pdfOcrUploadFileParseHandler;

	/**
	 * 上传源类型处理器
	 */
	private final UploadSourceTypeHandler uploadSourceTypeHandler;

	/**
	 * Mineru助手服务
	 */
	private final AiMineruAssistantService mineruAssistantService;

	/**
	 * MinerU本地版解析结果缓存
	 */
	private final MineruLocalTaskCache mineruLocalTaskCache;

	/**
	 * 远程文件服务
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * 更新文档OCR结果
	 * <p>
	 * 查询OCR解析中的文档 检查解析状态并下载成功结果 将解析内容保存到知识库
	 */
	@Override
	public void updateDocumentOcrResult() {
		List<AiDocumentEntity> documentEntityList = documentService.list(Wrappers.<AiDocumentEntity>lambdaQuery()
			.eq(AiDocumentEntity::getSliceStatus, SliceStatusEnums.OCR_PARSING.getStatus())
			.isNotNull(AiDocumentEntity::getSliceFailReason));

		for (AiDocumentEntity documentEntity : documentEntityList) {
			UmiOcrPDFModelDTO.UmiOcrPDFModelResultRequest umiOcrPDFModelResultRequest = new UmiOcrPDFModelDTO.UmiOcrPDFModelResultRequest();
			umiOcrPDFModelResultRequest.setId(documentEntity.getSliceFailReason());
			UmiOcrPDFModelDTO.UmiOcrPDFModelResultResponse umiOcrPDFModelResultResponse = ocrAssistantService
				.docResult(umiOcrPDFModelResultRequest);

			// 如果后台是成功的，则下载
			if ("success".equals(umiOcrPDFModelResultResponse.getState())) {
				UmiOcrPDFModelDTO.UmiOcrPDFModelDownRequest umiOcrPDFModelDownRequest = new UmiOcrPDFModelDTO.UmiOcrPDFModelDownRequest();
				umiOcrPDFModelDownRequest.setId(documentEntity.getSliceFailReason());
				umiOcrPDFModelDownRequest.setFile_types(List.of("txtPlain"));
				umiOcrPDFModelDownRequest.setIngore_blank(true);
				UmiOcrPDFModelDTO.UmiOcrPDFModelDownResponse umiOcrPDFModelDownResponse = ocrAssistantService
					.downloadDoc(umiOcrPDFModelDownRequest);

				TextDocumentParser textDocumentParser = new TextDocumentParser();

				// 加载文档
				Document document = UrlDocumentLoader.load(umiOcrPDFModelDownResponse.getData(), textDocumentParser);

				// 保存解析结果到知识库
				AiDatasetEntity aiDatasetEntity = datasetService.getById(documentEntity.getDatasetId());
				uploadSourceTypeHandler.saveResult(pdfOcrUploadFileParseHandler,
						Pair.of(FileParserStatusEnums.PARSE_SUCCESS, document.text()), documentEntity, aiDatasetEntity,
						null);
			}
		}

	}

	/**
	 * 更新 Mineru 解析结果
	 * <p>
	 * 该方法查询所有正在解析中且包含批次ID的文档，根据配置分发到云版本或本地版本的处理逻辑。
	 * </p>
	 */
	@Override
	public void updateMineruParseResult() {
		// 获取知识库配置
		AiKnowledgeProperties knowledgeProperties = SpringUtil.getBean(AiKnowledgeProperties.class);
		if (!knowledgeProperties.getMineru().isEnabled()) {
			return;
		}

		// 查询全部解析中的文档（批次ID存储在 sliceFailReason 字段中）
		List<AiDocumentEntity> parsingDocumentList = documentService.list(Wrappers.<AiDocumentEntity>lambdaQuery()
			.eq(AiDocumentEntity::getSliceStatus, SliceStatusEnums.AI_PARSING.getStatus())
			.isNotNull(AiDocumentEntity::getSliceFailReason));

		// 根据配置分发到不同的处理逻辑
		for (AiDocumentEntity documentEntity : parsingDocumentList) {
			// 定时任务线程无租户上下文，从关联的数据集获取tenantId
			AiDatasetEntity dataset = datasetService.getById(documentEntity.getDatasetId());
			Long tenantId = dataset != null && StrUtil.isNotBlank(dataset.getTenantId())
					? Long.parseLong(dataset.getTenantId()) : null;

			if (tenantId == null) {
				log.warn("文档ID: {} 无法获取租户ID，跳过处理", documentEntity.getId());
				continue;
			}

			TenantBroker.runAs(tenantId, (id) -> {
				if (knowledgeProperties.getMineru().isPrivate()) {
					processMineruLocalResult(documentEntity);
				}
				else {
					processMineruCloudResult(documentEntity, knowledgeProperties);
				}
			});
		}
	}

	/**
	 * 处理 Mineru 解析失败的情况
	 * @param documentEntity 文档实体
	 * @param errorMessage 错误信息
	 */
	private void handleMineruParseFailed(AiDocumentEntity documentEntity, String errorMessage) {
		documentEntity.setFileStatus(FileStatusEnums.FAILED.getCode());
		documentEntity.setSliceStatus(SliceStatusEnums.FAILED.getStatus());
		documentEntity.setSliceFailReason(errorMessage);
		documentService.updateById(documentEntity);
		log.error("Mineru 解析失败，文档ID: {}, 错误信息: {}", documentEntity.getId(), errorMessage);
	}

	/**
	 * 处理 Mineru 解析成功后的流程
	 * @param documentEntity 文档实体
	 * @param mdContent 解析后的 Markdown 内容
	 */
	private void handleMineruParseSuccess(AiDocumentEntity documentEntity, String mdContent) {
		// 保存解析结果到知识库
		AiDatasetEntity aiDatasetEntity = datasetService.getById(documentEntity.getDatasetId());
		uploadSourceTypeHandler.saveResult(pdfOcrUploadFileParseHandler,
				Pair.of(FileParserStatusEnums.PARSE_SUCCESS, mdContent), documentEntity, aiDatasetEntity, null);

		// 总结文档
		uploadSourceTypeHandler.summaryDocument(documentEntity, null);

		// 向量化切片
		uploadSourceTypeHandler.embedSlice(documentEntity);
	}

	/**
	 * 下载并处理 Mineru Cloud 版本返回的 zip 文件
	 * @param zipUrl zip 文件下载地址
	 * @return 处理后的 Markdown 内容
	 */
	@SneakyThrows
	private String downloadAndProcessMineruZip(String zipUrl) {
		// 下载 zip 文件到临时目录
		File tempFile = FileUtil.createTempFile();
		HttpUtil.downloadFile(zipUrl, tempFile);

		// 使用 try-with-resources 自动关闭 ZipFile
		try (ZipFile zipFile = new ZipFile(tempFile)) {
			// 解压并读取 full.md 文件
			byte[] bytes = ZipUtil.unzipFileBytes(tempFile, "full.md");
			String mdContext = StrUtil.str(bytes, CharsetUtil.defaultCharsetName());

			// 为图片链接添加域名前缀
			String result = MarkdownUtil.addDomainPrefixToImages(mdContext, "/api/admin/sys-file/oss/file?fileName=");

			// 处理并上传图片资源
			for (String fileName : ZipUtil.listFileNames(zipFile, "images")) {
				byte[] imageBytes = ZipUtil.unzipFileBytes(tempFile, "images/" + fileName);
				remoteFileService.innerUpload(new ByteArrayMultipartFile(fileName, fileName, null, imageBytes),
						fileName);
			}

			return result;
		}
		finally {
			// ZipFile 已自动关闭，现在可以安全删除临时文件
			FileUtil.del(tempFile);
		}
	}

	/**
	 * 处理 Mineru Local 版本的解析结果
	 * @param documentEntity 文档实体
	 */
	private void processMineruLocalResult(AiDocumentEntity documentEntity) {
		String taskId = documentEntity.getSliceFailReason();

		MineruLocalTaskCache.MineruLocalTaskResult result = mineruLocalTaskCache.get(taskId);
		if (result == null) {
			return;
		}

		mineruLocalTaskCache.remove(taskId);

		if (result.isSuccess()) {
			String mdContent = processMineruLocalImages(result.getContent(), result.getImages());
			handleMineruParseSuccess(documentEntity, mdContent);
		}
		else {
			handleMineruParseFailed(documentEntity, result.getContent());
		}
	}

	/**
	 * 处理 Mineru Local 版本返回的图片资源
	 * <p>
	 * 将 base64(dataURL格式)图片解码后上传到文件服务，并把 Markdown 中的相对图片路径替换为可访问的完整 URL。
	 * 单张图片上传失败仅记录日志并跳过，不阻断整体解析流程。
	 * @param mdContent 原始 Markdown 内容
	 * @param images 图片资源映射：图片文件名 -> base64数据(dataURL格式)
	 * @return 处理后的 Markdown 内容
	 */
	private String processMineruLocalImages(String mdContent, Map<String, String> images) {
		if (StrUtil.isBlank(mdContent) || CollUtil.isEmpty(images)) {
			return mdContent;
		}

		// 为图片链接添加域名前缀，与 Word、Mineru 云版保持一致
		String result = MarkdownUtil.addDomainPrefixToImages(mdContent, "/api/admin/sys-file/oss/file?fileName=");

		for (Map.Entry<String, String> entry : images.entrySet()) {
			// 仅保留纯文件名，与 addDomainPrefixToImages 去除 images/ 前缀的逻辑对齐
			String fileName = FileUtil.getName(entry.getKey());
			String base64 = entry.getValue();
			if (StrUtil.isBlank(fileName) || StrUtil.isBlank(base64)) {
				continue;
			}
			try {
				// 剥离 dataURL 前缀(如 data:image/jpeg;base64,)
				int commaIndex = base64.indexOf(',');
				String pureBase64 = commaIndex >= 0 ? base64.substring(commaIndex + 1) : base64;
				byte[] imageBytes = Base64.decode(pureBase64);
				remoteFileService.innerUpload(new ByteArrayMultipartFile(fileName, fileName, null, imageBytes),
						fileName);
				log.info("MinerU本地图片上传成功: {}", fileName);
			}
			catch (Exception e) {
				log.warn("MinerU本地图片上传失败: {}, error={}", fileName, e.getMessage());
			}
		}

		return result;
	}

	/**
	 * 处理 Mineru Cloud 版本的解析结果
	 * @param documentEntity 文档实体
	 * @param knowledgeProperties 知识库配置
	 */
	private void processMineruCloudResult(AiDocumentEntity documentEntity, AiKnowledgeProperties knowledgeProperties) {
		// 获取云端解析结果
		MineruBatchResponseDTO batchResults = mineruAssistantService.getBatchResults(
				documentEntity.getSliceFailReason(),
				String.format("Bearer %s", knowledgeProperties.getMineru().getApiKey()));

		// 检查 API 调用是否成功
		if (Objects.isNull(batchResults) || batchResults.getCode() != 0) {
			String errorMessage = batchResults != null ? batchResults.getMsg() : "获取解析结果失败";
			handleMineruParseFailed(documentEntity, errorMessage);
			return;
		}

		// 获取第一个提取结果
		MineruBatchResponseDTO.ExtractResult extractResult = batchResults.getData().getExtractResult().get(0);

		// 检查解析状态
		if (MineruParseStatusEnums.FAILED.getStatus().equals(extractResult.getState())) {
			handleMineruParseFailed(documentEntity, extractResult.getErrMsg());
			return;
		}

		// 处理解析成功的情况
		if (MineruParseStatusEnums.DONE.getStatus().equals(extractResult.getState())) {
			String mdContent = downloadAndProcessMineruZip(extractResult.getFullZipUrl());
			handleMineruParseSuccess(documentEntity, mdContent);
		}
	}

	/**
	 * 更新文档总结
	 */
	@Override
	public void updateDocumentSummary() {
		// 查询全部未总结的文档
		List<AiDocumentEntity> failSummaryDocumentList = documentService.list(Wrappers.<AiDocumentEntity>lambdaQuery()
			.eq(AiDocumentEntity::getSummaryStatus, SummaryStatusEnums.FAILED.getStatus()));

		for (AiDocumentEntity documentEntity : failSummaryDocumentList) {
			documentService.summaryDocument(documentEntity);
		}

	}

	/**
	 * 更新文档切片
	 */
	@Override
	public void updateDocumentSlice() {
		List<AiSliceEntity> aiSliceEntityList = sliceService.list(Wrappers.<AiSliceEntity>lambdaQuery()
			.eq(AiSliceEntity::getSliceStatus, SliceStatusEnums.FAILED.getStatus()));

		if (CollUtil.isEmpty(aiSliceEntityList)) {
			return;
		}

		List<Long> documentId = aiSliceEntityList.stream().map(AiSliceEntity::getDocumentId).distinct().toList();

		documentService.listByIds(documentId).forEach(documentEntity -> {
			sliceService.embedSlice(documentEntity, SliceStatusEnums.FAILED);
		});

	}

}
