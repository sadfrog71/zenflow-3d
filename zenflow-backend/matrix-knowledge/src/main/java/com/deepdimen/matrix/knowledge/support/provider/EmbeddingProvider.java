package com.deepdimen.matrix.knowledge.support.provider;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.config.AiEmbedInitRunner;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 向量存储提供者
 * <p>
 * 提供基于快速向量存储和检索功能，用于AI随航等轻量级场景 支持文本向量化、存储和相似度搜索，数据存储在内存中 TODO: 后续需要实现持久化功能，支持系统重启后的数据恢复
 *
 * @author caoyuan
 * @date 2025/03/21
 */
@Slf4j
@UtilityClass
public class EmbeddingProvider {

	/**
	 * 业务嵌入名称
	 */
	public static final String BIZ_EMBED_NAME = "AI_BIZ_EMBED_COLLECTION";

	/**
	 * 临时ID常量，用于临时存储场景
	 */
	public static final String TEMP_ID = "temp_id";

	/**
	 * 添加文本段到向量存储
	 * <p>
	 * 自动生成随机ID并进行向量化存储
	 * @param textSegment 要存储的文本段
	 */
	public void add(TextSegment textSegment) {
		DimensionAwareEmbeddingModel embeddingModel = getEmbeddingModel();
		if (embeddingModel == null) {
			return;
		}

		// 文本向量化
		Response<Embedding> embeddingResponse = embeddingModel.embed(textSegment.text());
		Embedding embedding = embeddingResponse.content();

		// 存储向量和原始文本
		EmbeddingStore<TextSegment> embeddingStore = SpringUtil.getBean(EmbeddingStoreService.class)
			.embeddingStore(currentTenantCollection());
		embeddingStore.add(embedding, textSegment);
	}

	/**
	 * 获取默认的嵌入模型
	 * <p>
	 * 从数据库配置中获取默认的向量模型
	 * @return 嵌入模型实例，如果没有配置则返回null
	 */
	@Nullable
	private static DimensionAwareEmbeddingModel getEmbeddingModel() {
		AiModelMapper modelMapper = SpringUtil.getBean(AiModelMapper.class);

		// 查询默认的向量模型，排除视觉和多模态模型
		AiModelEntity aiModelEntity = modelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.notLike(AiModelEntity::getModelName, "vision")
			.notLike(AiModelEntity::getModelName, "multimodal")
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode())
			.eq(AiModelEntity::getModelType, ModelTypeEnums.EMBEDDING.getType()), false);

		ModelProvider modelProvider = SpringUtil.getBean(ModelProvider.class);
		if (Objects.isNull(aiModelEntity)) {
			log.warn("当前租户没有默认向量模型，跳过业务向量初始化");
			return null;
		}

		return modelProvider.getEmbeddingModel(aiModelEntity.getName());
	}

	/**
	 * 相似度搜索（使用默认配置）
	 * <p>
	 * 使用系统默认的topK和最小分数进行搜索
	 * @param text 搜索文本
	 * @param filters 过滤条件
	 * @return 搜索结果
	 */
	public EmbeddingSearchResult<TextSegment> search(String text, Filter... filters) {
		AiKnowledgeProperties properties = SpringUtil.getBean(AiKnowledgeProperties.class);
		return search(text, properties.getInMemorySearch().getTopK(), properties.getInMemorySearch().getMinScore(),
				filters);
	}

	/**
	 * 相似度搜索（自定义参数）
	 * <p>
	 * 将文本向量化后在向量库中进行相似度搜索
	 * @param text 搜索文本
	 * @param topK 返回结果数量
	 * @param minScore 最低相似度分数
	 * @param filters 过滤条件
	 * @return 搜索结果
	 */
	public EmbeddingSearchResult<TextSegment> search(String text, int topK, double minScore, Filter... filters) {
		// 获取嵌入模型
		DimensionAwareEmbeddingModel embeddingModel = getEmbeddingModel();
		if (embeddingModel == null) {
			return null;
		}

		// 搜索文本向量化
		Embedding embedding = getEmbeddingModel().embed(text).content();

		// 构建搜索请求
		EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
			.queryEmbedding(embedding)
			.minScore(minScore)
			.maxResults(topK);

		// 添加过滤条件
		for (Filter filter : filters) {
			builder.filter(filter);
		}

		EmbeddingStore<TextSegment> embeddingStore = SpringUtil.getBean(EmbeddingStoreService.class)
			.embeddingStore(currentTenantCollection());

		// 执行向量搜索
		try {
			return embeddingStore.search(builder.build());
		}
		catch (IllegalArgumentException illegalArgumentException) {
			// 此异常意味着用户切换了模型，导致向量维度不匹配
			log.error("向量搜索失败，可能是模型切换导致向量维度不匹配，请检查模型配置", illegalArgumentException);
			// 清空内存向量，重新加载向量
			embeddingStore.removeAll();
			SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(embeddingModel));
			return null;
		}
	}

	/**
	 * 移除所有嵌入存储数据
	 */
	public void removeAll() {
		SpringUtil.getBean(EmbeddingStoreService.class).embeddingStore(currentTenantCollection());
	}

	private String currentTenantCollection() {
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new IllegalStateException("业务向量操作缺少租户上下文");
		}
		return BIZ_EMBED_NAME + '_' + tenantId;
	}

}
