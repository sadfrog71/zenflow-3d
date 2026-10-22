package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.config.DocumentParseAsyncConfiguration;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.EmbeddingStrategy;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.RagHelper;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.StrategyFactory;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl.StandardRetrievalStrategy;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI知识切片服务实现类
 * <p>
 * 管理知识库文档的切片和向量化 支持向量检索、全文检索和重排序 提供切片的增删改查和命中统计功能
 *
 * @author matrix
 * @date 2024-03-14 13:39:40
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSliceServiceImpl extends ServiceImpl<AiSliceMapper, AiSliceEntity> implements AiSliceService {

	/**
	 * 嵌入向量存储服务 用于存储和检索切片的向量数据
	 */
	private final EmbeddingStoreService embeddingStoreService;

	/**
	 * 文档数据访问层 用于查询和更新文档信息
	 */
	private final AiDocumentMapper documentMapper;

	/**
	 * 数据集数据访问层 用于获取知识库配置信息
	 */
	private final AiDatasetMapper aiDatasetMapper;

	/**
	 * 策略工厂 用于获取合适的向量化和检索策略
	 */
	@Lazy
	private final StrategyFactory strategyFactory;

	/**
	 * 批量删除切片及对应的嵌入向量
	 * <p>
	 * 删除数据库中的切片记录 同时从向量数据库中删除对应的向量数据 按文档分组批量处理，提高删除效率
	 * @param sliceIdList 切片ID列表
	 * @return 删除成功返回true
	 */
	@Override
	public Boolean removeSliceAndEmbeddingById(List<Long> sliceIdList) {
		List<AiSliceEntity> aiSliceEntityList = baseMapper.selectByIds(sliceIdList);

		baseMapper.deleteByIds(sliceIdList);

		// 分组 List<documentId,List<id>>
		Map<Long, List<AiSliceEntity>> listMap = aiSliceEntityList.stream()
			.collect(Collectors.groupingBy(AiSliceEntity::getDocumentId));

		listMap.forEach((documentId, sliceList) -> {
			AiDocumentEntity documentEntity = documentMapper.selectById(documentId);
			List<String> qdrantIdList = sliceList.stream()
				.map(AiSliceEntity::getQdrantId)
				.filter(Objects::nonNull)
				.toList();

			try {
				AiDatasetEntity aiDatasetEntity = aiDatasetMapper.selectById(documentEntity.getDatasetId());
				embeddingStoreService.delete(aiDatasetEntity.getCollectionName(), qdrantIdList);
			}
			catch (Exception e) {
				log.error("删除向量失败", e);
			}
		});

		return Boolean.TRUE;
	}

	/**
	 * 更新切片内容
	 * <p>
	 * 更新切片的文本内容 将切片状态重置为未向量化 自动触发重新向量化处理
	 * @param aiSlice 待更新的切片实体
	 * @return 更新成功返回true
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean updateSlice(AiSliceEntity aiSlice) {
		// 更新数据的数据 (状态设置为未向量化)
		aiSlice.setSliceStatus(SliceStatusEnums.UNSLICED.getStatus());
		this.updateById(aiSlice);
		// 更新向量 (状态为未向量化)
		AiDocumentEntity aiDocumentEntity = documentMapper.selectById(aiSlice.getDocumentId());
		this.embedSlice(aiDocumentEntity, SliceStatusEnums.UNSLICED);
		return Boolean.TRUE;
	}

	/**
	 * 知识召回接口
	 * <p>
	 * 使用策略模式支持向量检索、全文检索和重排序 根据用户查询内容从知识库中检索相关切片 返回相关度最高的知识片段供AI回答使用
	 * @param requestDTO 召回请求参数，包含查询内容、TopK等配置
	 * @return 召回的切片列表，包含内容、分数和元数据
	 */
	@Override
	public List<AiSliceReCallDTO> recall(AiSliceReCallRequestDTO requestDTO) {
		// 查询知识库配置
		AiDatasetEntity dataset = aiDatasetMapper.selectById(requestDTO.getDatasetId());
		if (Objects.isNull(dataset)) {
			log.warn("知识库不存在: {}", requestDTO.getDatasetId());
			return List.of();
		}

		// 获取向量存储实例
		EmbeddingStore<TextSegment> embeddingStore = embeddingStoreService.embeddingStore(dataset.getCollectionName());
		StandardRetrievalStrategy standardRetrievalStrategy = SpringUtil.getBean(StandardRetrievalStrategy.class);
		return standardRetrievalStrategy.performRetrieval(requestDTO, dataset, embeddingStore);
	}

	/**
	 * 根据文档ID删除切片和向量
	 * <p>
	 * 删除指定文档的所有切片 同时清理向量数据库中的对应数据 常用于文档删除或重新解析场景
	 * @param id 文档ID
	 */
	@Override
	public void removeSliceAndEmbeddingByDocumentId(Long id) {
		List<AiSliceEntity> sliceEntityList = baseMapper
			.selectList(Wrappers.<AiSliceEntity>lambdaQuery().eq(AiSliceEntity::getDocumentId, id));
		List<Long> idList = sliceEntityList.stream().map(AiSliceEntity::getId).toList();

		if (CollUtil.isNotEmpty(idList)) {
			this.removeSliceAndEmbeddingById(idList);
		}
	}

	/**
	 * 异步更新切片和文档的命中次数
	 * <p>
	 * 统计知识召回的使用情况 用于分析热门知识和优化检索策略 同时更新切片和对应文档的命中计数
	 * @param qdrantIdList 向量数据库中的ID列表
	 */
	@Override
	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	public void updateHitCount(List<String> qdrantIdList) {
		baseMapper.selectList(Wrappers.<AiSliceEntity>lambdaQuery().in(AiSliceEntity::getQdrantId, qdrantIdList))
			.forEach(slice -> {
				baseMapper.update(Wrappers.<AiSliceEntity>lambdaUpdate()
					.setSql("hit_count = hit_count + 1")
					.eq(AiSliceEntity::getId, slice.getId()));

				documentMapper.update(Wrappers.<AiDocumentEntity>lambdaUpdate()
					.setSql("hit_count = hit_count + 1")
					.eq(AiDocumentEntity::getId, slice.getDocumentId()));
			});
	}

	/**
	 * 将文档切片进行向量化处理
	 * <p>
	 * 使用策略模式处理不同类型向量存储的向量化逻辑 支持带摘要的向量化，提高检索效果 处理失败的切片会标记为失败状态等待重试
	 * @param documentEntity 文档实体对象
	 * @param sliceStatusEnums 要处理的切片状态
	 */
	@Override
	public void embedSlice(AiDocumentEntity documentEntity, SliceStatusEnums sliceStatusEnums) {
		// 查询指定文档下指定状态的所有切片
		List<AiSliceEntity> aiSliceEntityList = this.list(Wrappers.<AiSliceEntity>lambdaQuery()
			.eq(AiSliceEntity::getDocumentId, documentEntity.getId())
			.eq(AiSliceEntity::getSliceStatus, sliceStatusEnums.getStatus()));

		// 获取知识库配置，用于向量化参数
		AiDatasetEntity aiDataset = aiDatasetMapper.selectById(documentEntity.getDatasetId());

		if (Objects.isNull(aiDataset)) {
			// 知识库不存在，删除孤立文档
			log.warn("知识库不存在，删除孤立文档: {}", documentEntity.getId());
			documentMapper.deleteById(documentEntity.getId());
			return;
		}

		// 检查是否需要跳过摘要处理
		if (RagHelper.shouldSkipSummary(aiDataset, documentEntity)) {
			log.debug("跳过摘要处理: {}", documentEntity.getName());
			return;
		}

		// 获取向量存储实例
		EmbeddingStore<TextSegment> embeddingStore = embeddingStoreService
			.embeddingStore(aiDataset.getCollectionName());

		// 获取合适的向量化策略并执行处理
		EmbeddingStrategy embeddingStrategy = strategyFactory.getEmbeddingStrategy(embeddingStore);

		log.debug("使用向量化策略: {} 处理文档: {}", embeddingStrategy.getClass().getSimpleName(), documentEntity.getName());

		try {
			embeddingStrategy.processEmbedding(documentEntity, aiDataset, aiSliceEntityList, embeddingStore);

			// 更新文档状态
			documentMapper.updateById(documentEntity);

			log.debug("文档向量化完成: {}, 处理切片数: {}", documentEntity.getName(), aiSliceEntityList.size());

		}
		catch (Exception e) {
			log.error("文档向量化失败: {}", documentEntity.getName(), e);
		}
	}

}
