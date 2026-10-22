package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiDataFieldEntity;

import java.util.List;

/**
 * AI数据字段服务接口 管理数据源表字段的元数据，支持字段同步和智能评估
 */
public interface AiDataFieldService extends IService<AiDataFieldEntity> {

	/** Validate on the request thread before scheduling asynchronous assessment. */
	void validateTableSelection(String dsName, String tableName);

	/**
	 * 获取表字段信息（支持缓存） 优先从缓存获取，如果不存在则自动同步并返回
	 * @param dsName 数据源名称
	 * @param tableName 数据表名称
	 * @return 表字段信息列表
	 */
	List<AiDataFieldEntity> getOrSync(String dsName, String tableName);

	/**
	 * 同步数据表字段 从指定数据源读取表结构，同步字段元数据到系统
	 * @param dsName 数据源名称
	 * @param tableName 数据表名称
	 * @return 同步是否成功
	 */
	boolean syncTableField(String dsName, String tableName);

	/**
	 * AI智能评估表字段 使用AI分析字段名称和类型，自动生成或优化字段注释
	 * @param dsName 数据源名称
	 * @param tableName 数据表名称
	 */
	void assessTableField(String dsName, String tableName);

}
