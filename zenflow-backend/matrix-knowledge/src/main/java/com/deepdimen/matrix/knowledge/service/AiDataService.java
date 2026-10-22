package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiDataEntity;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;

import java.util.List;

/**
 * AI数据集服务接口 管理AI应用的数据集配置，支持多数据源和表结构定义
 */
public interface AiDataService extends IService<AiDataEntity> {

	/**
	 * 查询数据集的完整结构定义 获取数据集下所有表的Schema信息，用于AI理解数据结构
	 * @param dataId 数据集ID
	 * @return 格式化的数据集Schema定义，包含所有表结构
	 */
	String queryDataSchema(Long dataId);

	/**
	 * 根据数据ID查询术语
	 * @param dataId 数据ID
	 * @return 查询到的术语
	 */
	String queryTerminology(Long dataId);

	/**
	 * 查询多个表的结构定义 批量获取表的字段、类型、注释等元数据信息
	 * @param tableEntityList 需要查询的表实体列表
	 * @return 格式化的表结构定义，用于SQL生成和数据分析
	 */
	String queryTableSchema(List<AiDataTableEntity> tableEntityList);

	/**
	 * 保存或更新数据集配置 维护数据集的基本信息和关联表配置
	 * @param aiData 数据集实体，包含名称、描述、数据源等信息
	 * @return 操作是否成功
	 */
	boolean saveOrUpdateData(AiDataEntity aiData);

}
