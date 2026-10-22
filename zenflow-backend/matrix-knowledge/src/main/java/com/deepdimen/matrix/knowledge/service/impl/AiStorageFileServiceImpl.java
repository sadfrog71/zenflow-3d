package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.config.DocumentParseAsyncConfiguration;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.AiMultimodalEmbeddingArkDTO;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.entity.AiStorageFileEntity;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.mapper.AiStorageFileMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantArkService;
import com.deepdimen.matrix.knowledge.service.AiDashscopeAssistantService;
import com.deepdimen.matrix.knowledge.service.AiStorageFileService;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.MultimodalModelEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import feign.Response;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.deepdimen.matrix.knowledge.support.constant.MultimodalModelEnums.ARK_DOUBAO_EMBEDDING_VISION;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * AI存储文件表
 *
 * @author matrix
 * @date 2025-09-30 13:56:22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiStorageFileServiceImpl extends ServiceImpl<AiStorageFileMapper, AiStorageFileEntity>
		implements AiStorageFileService {

	/**
	 * AI存储文件配置键
	 */
	public static final String AI_STORAGE_FILE = "AI_STORAGE_FILE_COLLECTION";

	/**
	 * 嵌入存储服务
	 */
	private final EmbeddingStoreService embeddingStoreService;

	/**
	 * AI助手ARK服务
	 */
	private final ModelProvider modelProvider;

	/**
	 * 远程文件服务
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * 知识库配置属性
	 */
	private final AiKnowledgeProperties knowledgeProperties;

	/**
	 * 向量库配置Mapper
	 */
	private final AiEmbedStoreMapper aiEmbedStoreMapper;

	/**
	 * AI模型Mapper
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * DashScope助手服务
	 */
	private final AiDashscopeAssistantService aiDashscopeAssistantService;

	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	@Override
	@SneakyThrows
	public void embedFile(Long fileId) {
		// 查询文件信息
		AiStorageFileEntity storageFileEntity = baseMapper.selectById(fileId);

		// 下载文件内容
		@Cleanup
		Response response = remoteFileService.getFile(storageFileEntity.getFileUrl());
		@Cleanup
		InputStream inputStream = response.body().asInputStream();

		// 将图片转为base64编码
		String encodeToString = ImgUtil.toBase64DataUri(ImageIO.read(inputStream), ImgUtil.IMAGE_TYPE_PNG);

		// 获取可用的多模态向量模型
		AiModelEntity modelEntity = getAvailableMultimodalModel();

		// 根据模型类型进行向量化
		List<Float> embeddingFloat = isArkModel(modelEntity) ? embedFileWithArk(encodeToString, modelEntity)
				: embedFileWithDashScope(encodeToString, modelEntity);

		// 保存向量化结果
		saveEmbeddingResult(fileId, storageFileEntity, embeddingFloat, modelEntity);
	}

	/**
	 * 获取可用的多模态向量模型
	 * <p>
	 * 优先返回ARK模型，如果ARK模型不可用则返回阿里云模型
	 * @return 可用的多模态向量模型配置
	 * @throws IllegalStateException 如果没有找到任何可用的模型
	 */
	private AiModelEntity getAvailableMultimodalModel() {
		// 尝试获取ARK模型
		AiModelEntity arkModelEntity = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getModelName, ARK_DOUBAO_EMBEDDING_VISION.getCode()));

		if (Objects.nonNull(arkModelEntity)) {
			return arkModelEntity;
		}

		// 尝试使用阿里云DashScope模型 (支持所有多模态向量模型)
		AiModelEntity dashscopeModelEntity = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.in(AiModelEntity::getModelName, MultimodalModelEnums.ALIYUN_MULTIMODAL_V1.getCode(),
					MultimodalModelEnums.ALIYUN_TONGYI_EMBEDDING_VISION_PLUS.getCode(),
					MultimodalModelEnums.ALIYUN_TONGYI_EMBEDDING_VISION_FLASH.getCode())
			.last("LIMIT 1"));

		if (Objects.isNull(dashscopeModelEntity)) {
			log.error("未找到可用的多模态向量模型配置(ARK或阿里云)");
			throw new IllegalStateException("未找到可用的多模态向量模型配置，请先配置ARK或阿里云模型");
		}

		return dashscopeModelEntity;
	}

	/**
	 * 判断是否为ARK模型
	 * @param modelEntity 模型配置
	 * @return true表示是ARK模型，false表示是阿里云模型
	 */
	private boolean isArkModel(AiModelEntity modelEntity) {
		return ARK_DOUBAO_EMBEDDING_VISION.getCode().equals(modelEntity.getModelName());
	}

	/**
	 * 执行向量搜索并返回文件列表
	 * @param queryEmbedding 查询向量
	 * @param embeddingStore 向量存储
	 * @return 匹配的文件列表
	 */
	private List<AiStorageFileEntity> executeVectorSearch(Embedding queryEmbedding,
			EmbeddingStore<TextSegment> embeddingStore) {
		// 构建向量搜索请求
		Filter typeFilter = metadataKey(EmbedBizTypeEnums.Fields.type)
			.isEqualTo(EmbedBizTypeEnums.STORAGE_FILE.getType());

		EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
			.queryEmbedding(queryEmbedding)
			.maxResults(knowledgeProperties.getInMemorySearch().getTopK())
			.minScore(knowledgeProperties.getInMemorySearch().getMinScore())
			.filter(typeFilter)
			.build();

		// 在向量库中搜索相似文件
		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
		List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

		// 提取文件ID并查询文件信息
		List<Long> fileIds = matches.stream().map(match -> {
			TextSegment segment = match.embedded();
			if (segment != null && segment.metadata() != null) {
				Object fileId = segment.metadata().toMap().get(AiStorageFileEntity.Fields.id);
				if (fileId != null) {
					return Long.parseLong(fileId.toString());
				}
			}
			return null;
		}).filter(Objects::nonNull).collect(Collectors.toList());

		if (fileIds.isEmpty()) {
			return new ArrayList<>();
		}

		// 批量查询文件信息
		return listByIds(fileIds);
	}

	/**
	 * 使用ARK模型进行图片向量化
	 * @param imageBase64 图片Base64编码
	 * @param arkModelEntity ARK模型配置
	 * @return 向量化结果
	 */
	private List<Float> embedFileWithArk(String imageBase64, AiModelEntity arkModelEntity) {
		AiMultimodalEmbeddingArkDTO.Request arkRequest = new AiMultimodalEmbeddingArkDTO.Request();
		arkRequest.setModel(ARK_DOUBAO_EMBEDDING_VISION.getCode());
		arkRequest.setInput(List.of(AiMultimodalEmbeddingArkDTO.ImageInput.builder()
			.imageUrl(AiMultimodalEmbeddingArkDTO.ImageUrl.builder().url(imageBase64).build())
			.build()));

		AiAssistantArkService arkAssistant = modelProvider.getArkAssistant(arkModelEntity.getName());
		AiMultimodalEmbeddingArkDTO.Response arkEmbeddingResponse = arkAssistant.multimodalEmbedding(arkRequest);
		return arkEmbeddingResponse.getData().getEmbedding();
	}

	/**
	 * 使用DashScope模型进行图片向量化
	 * <p>
	 * 支持阿里云所有多模态向量模型
	 * @param imageBase64 图片Base64编码
	 * @return 向量化结果
	 */
	@SneakyThrows
	private List<Float> embedFileWithDashScope(String imageBase64, AiModelEntity aiModelEntity) {
		List<Double> doubleEmbedding = aiDashscopeAssistantService.multimodal(imageBase64,
				ModelTypeEnums.IMAGE.getType());

		// 转换Double到Float
		return doubleEmbedding.stream().map(Double::floatValue).collect(Collectors.toList());
	}

	/**
	 * 使用ARK模型进行文本向量化
	 * @param text 文本内容
	 * @param arkModelEntity ARK模型配置
	 * @return 向量化结果
	 */
	private List<Float> embedTextWithArk(String text, AiModelEntity arkModelEntity) {
		AiMultimodalEmbeddingArkDTO.Request arkRequest = new AiMultimodalEmbeddingArkDTO.Request();
		arkRequest.setModel(ARK_DOUBAO_EMBEDDING_VISION.getCode());
		arkRequest.setInput(List.of(AiMultimodalEmbeddingArkDTO.TextInput.builder().text(text).build()));

		AiAssistantArkService arkAssistant = modelProvider.getArkAssistant(arkModelEntity.getName());
		AiMultimodalEmbeddingArkDTO.Response arkEmbeddingResponse = arkAssistant.multimodalEmbedding(arkRequest);
		return arkEmbeddingResponse.getData().getEmbedding();
	}

	/**
	 * 使用DashScope模型进行文本向量化
	 * <p>
	 * 使用阿里云多模态向量模型处理文本内容
	 * @param text 文本内容
	 * @param dashscopeModelEntity DashScope模型配置
	 * @return 向量化结果
	 */
	@SneakyThrows
	private List<Float> embedTextWithDashScope(String text, AiModelEntity dashscopeModelEntity) {
		// 阿里云多模态模型支持文本向量化,但建议图像/音频/视频使用多模态,文本使用专用嵌入模型
		log.debug("使用阿里云多模态模型 {} 进行文本向量化", dashscopeModelEntity.getModelName());

		// 将文本转换为向量 - 使用multimodal方法处理文本类型
		List<Double> doubleEmbedding = aiDashscopeAssistantService.multimodal(text, ModelTypeEnums.CHAT.getType());

		// 转换Double到Float
		return doubleEmbedding.stream().map(Double::floatValue).collect(Collectors.toList());
	}

	/**
	 * 保存向量化结果到向量库
	 * @param fileId 文件ID
	 * @param storageFileEntity 文件实体
	 * @param embeddingFloat 向量化结果
	 * @param usedModelEntity 使用的模型配置
	 */
	private void saveEmbeddingResult(Long fileId, AiStorageFileEntity storageFileEntity, List<Float> embeddingFloat,
			AiModelEntity usedModelEntity) {
		EmbeddingStore<TextSegment> embeddingStore = validateAndCreateEmbeddingStore(fileId, usedModelEntity);
		TextSegment textSegment = TextSegment.textSegment(storageFileEntity.getFileName(),
				Metadata.from(Map.of(EmbedBizTypeEnums.Fields.type, EmbedBizTypeEnums.STORAGE_FILE.getType(),
						AiStorageFileEntity.Fields.id, fileId)));

		Embedding embedding = Embedding.from(embeddingFloat);
		String embedId = embeddingStore.add(embedding, textSegment);
		storageFileEntity.setExtInfo(embedId);

		// 更新文件的向量化状态
		storageFileEntity.setAiAnalyzed(YesNoEnum.YES.getCode());
		baseMapper.updateById(storageFileEntity);
	}

	/**
	 * 验证并创建嵌入式存储
	 * @param fileId 文件ID
	 * @param aiModelEntity
	 * @return 嵌入式存储
	 */
	private EmbeddingStore<TextSegment> validateAndCreateEmbeddingStore(Long fileId, AiModelEntity aiModelEntity) {
		// 获取第一个默认的向量库配置
		AiEmbedStoreEntity embedStoreEntity = aiEmbedStoreMapper.selectOne(
				Wrappers.<AiEmbedStoreEntity>lambdaQuery().orderByAsc(AiEmbedStoreEntity::getStoreId).last("LIMIT 1"));

		// 判断向量库配置是否为空
		if (Objects.isNull(embedStoreEntity)) {
			log.error("未找到默认的向量库配置，无法进行文件向量化处理，文件ID: {}", fileId);
			throw new IllegalStateException("未找到默认的向量库配置，请先配置向量库");
		}

		// 判断模型配置是否为空
		if (Objects.isNull(aiModelEntity)) {
			throw new IllegalStateException("未找到多模态向量模型配置，请先配置模型");
		}

		// 根据模型名称从枚举中获取维度
		MultimodalModelEnums multimodalModel = MultimodalModelEnums.fromCode(aiModelEntity.getModelName());
		if (Objects.nonNull(multimodalModel)) {
			aiModelEntity.setDimensions(multimodalModel.getDimensions());
		}
		else {
			throw new IllegalStateException("不支持的多模态模型: " + aiModelEntity.getModelName());
		}

		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new IllegalStateException("文件向量操作缺少租户上下文");
		}
		return embeddingStoreService.embeddingStore(AI_STORAGE_FILE + '_' + tenantId, embedStoreEntity, aiModelEntity);
	}

	/**
	 * 根据关键词搜索相似文件
	 * <p>
	 * 支持ARK和阿里云所有多模态向量模型，优先使用ARK模型
	 * @param keyword 搜索关键词
	 * @return 相似文件列表，关键词为空时返回空列表
	 */
	@Override
	@SneakyThrows
	public List<AiStorageFileEntity> searchByVector(String keyword) {
		if (StrUtil.isBlank(keyword)) {
			return new ArrayList<>();
		}

		// 获取可用的多模态向量模型
		AiModelEntity modelEntity = getAvailableMultimodalModel();

		// 根据模型类型进行文本向量化
		List<Float> embeddingFloat = isArkModel(modelEntity) ? embedTextWithArk(keyword, modelEntity)
				: embedTextWithDashScope(keyword, modelEntity);

		// 执行向量搜索
		EmbeddingStore<TextSegment> embeddingStore = validateAndCreateEmbeddingStore(null, modelEntity);
		Embedding queryEmbedding = Embedding.from(embeddingFloat);
		return executeVectorSearch(queryEmbedding, embeddingStore);
	}

	/**
	 * 根据图片搜索相似文件
	 * <p>
	 * 支持ARK和阿里云所有多模态向量模型，优先使用ARK模型
	 * @param imageBase64 图片Base64编码
	 * @return 相似文件列表，图片为空时返回空列表
	 */
	@Override
	@SneakyThrows
	public List<AiStorageFileEntity> searchByImage(String imageBase64) {
		if (StrUtil.isBlank(imageBase64)) {
			return new ArrayList<>();
		}

		// 获取可用的多模态向量模型
		AiModelEntity modelEntity = getAvailableMultimodalModel();

		// 根据模型类型进行图片向量化
		List<Float> embeddingFloat = isArkModel(modelEntity) ? embedFileWithArk(imageBase64, modelEntity)
				: embedFileWithDashScope(imageBase64, modelEntity);

		// 执行向量搜索
		EmbeddingStore<TextSegment> embeddingStore = validateAndCreateEmbeddingStore(null, modelEntity);
		Embedding queryEmbedding = Embedding.from(embeddingFloat);
		return executeVectorSearch(queryEmbedding, embeddingStore);
	}

}
