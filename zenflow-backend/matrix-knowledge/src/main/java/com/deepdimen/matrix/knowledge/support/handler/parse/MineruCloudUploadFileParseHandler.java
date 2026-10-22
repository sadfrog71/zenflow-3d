package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.core.exception.CheckedException;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.MineruBatchResponseDTO;
import com.deepdimen.matrix.knowledge.dto.MineruBatchUploadDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.service.AiMineruAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Mineru API 云版本文档解析处理器
 * <p>
 * 该处理器使用Mineru服务对多种格式的文档进行解析。Mineru是一个强大的文档解析服务，
 * 支持DOCX、PDF、PPTX、PPT、PNG、JPG、JPEG等多种格式。该处理器采用异步处理模式， 先上传文件到Mineru服务，然后返回批次ID供后续查询解析结果。
 *
 * @author caoyuan
 * @date 2025/06/01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MineruCloudUploadFileParseHandler implements UploadFileParseHandler {

	private final AiKnowledgeProperties aiKnowledgeProperties;

	private final AiMineruAssistantService mineruAssistantService;

	/**
	 * 检查是否支持处理指定类型的文件
	 * <p>
	 * 首先检查Mineru服务是否启用，如果未启用则不支持任何文件。
	 * 当Mineru服务启用时，支持处理DOCX、PDF、PPTX、PPT、PNG、JPG、JPEG等格式的文件。
	 * @param aiDataset 数据集实体
	 * @param documentEntity 文档实体，包含文件信息
	 * @return 如果支持该文件类型返回true，否则返回false
	 */
	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());

		if (!aiKnowledgeProperties.getMineru().isEnabled()) {
			return false;
		}

		// 私有化部署不支持该处理器
		if (aiKnowledgeProperties.getMineru().isPrivate()) {
			return false;
		}

		return FileTypeEnums.DOCX.getType().equals(contentType) || FileTypeEnums.PDF.getType().equals(contentType)
				|| FileTypeEnums.PPTX.getType().equals(contentType) || FileTypeEnums.PNG.getType().equals(contentType)
				|| FileTypeEnums.PPT.getType().equals(contentType) || FileTypeEnums.JPG.getType().equals(contentType)
				|| FileTypeEnums.JPEG.getType().equals(contentType);
	}

	/**
	 * 将文件上传到Mineru服务并开始异步解析
	 * <p>
	 * 该方法执行以下步骤： 1. 创建文件信息并向Mineru服务申请上传链接 2. 使用获取到的上传链接将文件内容上传到Mineru服务 3.
	 * 返回批次ID，后续可通过该ID查询解析结果
	 * <p>
	 * 注意：该方法返回AI_PARSING状态，表示文件正在异步解析中
	 * @param documentEntity 文档实体对象，包含文档信息
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @return 包含处理状态和批次ID/错误信息的Pair对象
	 * @throws Exception 处理过程中可能抛出异常
	 */
	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		try {
			// 创建文件信息并申请上传链接
			MineruBatchUploadDTO request = getMineruBatchUploadDTO(documentEntity);
			MineruBatchResponseDTO uploadUrlsResult = applyUploadUrl(request);

			String batchId = uploadUrlsResult.getData().getBatchId();
			String uploadUrl = uploadUrlsResult.getData().getFileUrls().get(0);

			log.info("Mineru 申请上传链接成功，batchId: {}, uploadUrl: {}", batchId, uploadUrl);

			// 上传文件
			uploadFileToUrl(inputStream, uploadUrl);

			log.info("Mineru 文件上传完成，batchId: {}", batchId);
			return Pair.of(FileParserStatusEnums.AI_PARSING, batchId);

		}
		catch (Exception e) {
			log.error("Mineru 服务异常，文件: {}, 错误: {}", documentEntity.getName(), e.getMessage());
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, e.getMessage());
		}
	}

	/**
	 * 向Mineru服务申请文件上传链接
	 * <p>
	 * 该方法调用Mineru API申请批量上传链接，验证响应的有效性， 确保返回的数据包含有效的上传链接。
	 * @param request 批量上传请求对象，包含文件信息
	 * @return Mineru批量响应DTO，包含批次ID和上传链接
	 * @throws CheckedException 当申请失败或返回数据无效时抛出
	 */
	private MineruBatchResponseDTO applyUploadUrl(MineruBatchUploadDTO request) {
		try {
			String authToken = String.format("Bearer %s", aiKnowledgeProperties.getMineru().getApiKey());
			MineruBatchResponseDTO result = mineruAssistantService.applyBatchUploadUrls(JSONUtil.toJsonStr(request),
					authToken);

			// 验证响应
			if (result.getCode() != 0) {
				throw new CheckedException("申请上传链接失败: " + (result.getMsg() != null ? result.getMsg() : "未知错误"));
			}

			if (result.getData() == null || result.getData().getFileUrls() == null
					|| result.getData().getFileUrls().isEmpty()) {
				throw new CheckedException("申请上传链接失败: 返回数据为空");
			}

			return result;
		}
		catch (Exception e) {
			throw new CheckedException("申请上传链接时发生异常: " + e.getMessage(), e);
		}
	}

	/**
	 * 上传文件到指定URL
	 * <p>
	 * 使用HTTP PUT方法将文件内容上传到Mineru服务提供的预签名URL。 该方法会检查响应状态码，确保文件上传成功。
	 * @param inputStream 文件输入流
	 * @param uploadUrl 文件上传URL（预签名URL）
	 * @throws IOException 当读取文件流失败或上传失败时抛出
	 */
	private void uploadFileToUrl(InputStream inputStream, String uploadUrl) throws IOException {
		byte[] fileBytes;
		try {
			fileBytes = inputStream.readAllBytes();
		}
		catch (IOException e) {
			throw new IOException("读取文件流失败", e);
		}

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpPut put = new HttpPut(uploadUrl);

			// 这里一定不能设置 content-type，否则会上传失败
			put.setEntity(new ByteArrayEntity(fileBytes, null));

			try (CloseableHttpResponse response = httpClient.execute(put)) {
				int statusCode = response.getCode();
				log.debug("文件上传响应状态码: {}", statusCode);

				if (statusCode != 200) {
					throw new IOException(String.format("文件上传失败，状态码: %d", statusCode));
				}
			}
		}
	}

	/**
	 * 构建Mineru批量上传请求对象
	 * <p>
	 * 根据文档实体和配置信息创建Mineru上传请求，包括： - 文件基本信息（名称、数据ID） - OCR配置（是否启用OCR、OCR语言） -
	 * 解析选项（启用公式识别、启用表格识别）
	 * @param documentEntity 文档实体，包含文档信息
	 * @return Mineru批量上传DTO对象
	 */
	@NotNull
	private MineruBatchUploadDTO getMineruBatchUploadDTO(AiDocumentEntity documentEntity) {
		MineruBatchUploadDTO.FileInfo fileInfo = new MineruBatchUploadDTO.FileInfo();
		fileInfo.setName(documentEntity.getFileUrl());
		fileInfo.setIsOcr(aiKnowledgeProperties.getMineru().isDefaultEnableOcr());
		fileInfo.setDataId(documentEntity.getId().toString());

		// 创建批量上传请求
		MineruBatchUploadDTO request = new MineruBatchUploadDTO();
		request.setEnableFormula(true);
		request.setEnableTable(true);
		request.setLanguage(aiKnowledgeProperties.getMineru().getDefaultOcrLanguage());
		request.setFiles(Collections.singletonList(fileInfo));
		return request;
	}

	/**
	 * 获取处理器的优先级顺序
	 * <p>
	 * 数值越大，优先级越高。该处理器的优先级为200，高于markitdown处理器（100）， 在多个处理器都支持同一文件类型时，会优先使用Mineru服务进行解析。
	 * @return 优先级值，返回200
	 */
	@Override
	public int getOrder() {
		return 200; // 优先级略高于 markitdown (100)
	}

}