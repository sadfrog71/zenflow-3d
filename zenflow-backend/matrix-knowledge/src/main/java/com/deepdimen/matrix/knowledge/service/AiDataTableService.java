package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;

/**
 * AI数据表服务接口
 * <p>
 * 提供AI知识库数据表管理功能，支持数据表信息的同步和管理
 * </p>
 *
 * @author matrix
 */
public interface AiDataTableService extends IService<AiDataTableEntity> {

	/**
	 * 同步数据表信息
	 * @param dsName 数据源名称
	 * @return 同步是否成功
	 */
	boolean sync(String dsName);

}
