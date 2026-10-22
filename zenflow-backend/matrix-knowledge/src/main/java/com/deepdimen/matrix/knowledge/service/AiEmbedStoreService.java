package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;

/**
 * AI向量存储服务接口
 * <p>
 * 管理向量数据库的连接配置，支持多种向量数据库（如Milvus、Chroma等）
 * </p>
 *
 * @author matrix
 */
public interface AiEmbedStoreService extends IService<AiEmbedStoreEntity> {

	/**
	 * 更新向量存储配置
	 * <p>
	 * 更新向量数据库的连接参数、认证信息等配置
	 * </p>
	 * @param aiEmbedStore 向量存储配置信息
	 * @return 更新是否成功
	 */
	Boolean updateEmbedStore(AiEmbedStoreEntity aiEmbedStore);

}
