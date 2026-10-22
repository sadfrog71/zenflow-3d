package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.knowledge.service.AiEmbedStoreService;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 向量库配置服务实现类
 * <p>
 * 管理向量数据库的配置信息 支持向量库更新时级联清理相关数据 确保向量库配置变更后数据一致性
 *
 * @author matrix
 * @date 2025-02-11 15:07:46
 */
@Service
@RequiredArgsConstructor
public class AiEmbedStoreServiceImpl extends ServiceImpl<AiEmbedStoreMapper, AiEmbedStoreEntity>
		implements AiEmbedStoreService {

	/**
	 * 向量存储服务 用于管理向量数据库操作
	 */
	private final EmbeddingStoreService embeddingStoreService;

	/**
	 * 数据集数据访问层 查询使用该向量库的数据集
	 */
	private final AiDatasetMapper aiDatasetMapper;

	/**
	 * 更新向量库配置
	 * <p>
	 * 清理所有使用该向量库的数据集 删除相关的向量数据确保重新初始化 使用事务保证数据一致性
	 * @param aiEmbedStore 向量库配置实体
	 * @return 更新成功返回true
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean updateEmbedStore(AiEmbedStoreEntity aiEmbedStore) {
		if (aiEmbedStore == null || aiEmbedStore.getStoreId() == null) {
			return false;
		}
		AiEmbedStoreEntity existing = this.getById(aiEmbedStore.getStoreId());
		if (existing == null) {
			return false;
		}
		preserveExistingApiKey(aiEmbedStore, existing);
		if (!this.updateById(aiEmbedStore)) {
			return false;
		}
		// 查询所有使用该向量库的数据集
		List<AiDatasetEntity> datasetEntityList = aiDatasetMapper.selectList(
				Wrappers.<AiDatasetEntity>lambdaQuery().eq(AiDatasetEntity::getStoreId, aiEmbedStore.getStoreId()));
		if (CollUtil.isNotEmpty(datasetEntityList)) {
			// 清理每个数据集的向量数据
			datasetEntityList.forEach(datasetEntity -> {
				embeddingStoreService.deleteEmbeddingStore(datasetEntity.getCollectionName());
			});
		}
		return true;
	}

	/**
	 * The admin form clears a masked API key before submitting an otherwise
	 * unchanged vector-store edit. Preserve the tenant-scoped credential in that
	 * case; only an explicitly supplied non-masked value replaces it.
	 */
	private void preserveExistingApiKey(AiEmbedStoreEntity aiEmbedStore, AiEmbedStoreEntity existing) {
		if (!StrUtil.isBlank(aiEmbedStore.getApiKey()) && !aiEmbedStore.getApiKey().contains("*")) {
			return;
		}
		aiEmbedStore.setApiKey(existing.getApiKey());
	}

}
