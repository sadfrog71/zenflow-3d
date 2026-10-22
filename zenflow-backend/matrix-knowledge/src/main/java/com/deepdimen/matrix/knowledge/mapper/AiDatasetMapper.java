package com.deepdimen.matrix.knowledge.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * AI知识库数据访问接口
 * <p>
 * 提供AI知识库表的数据库访问操作 核心配置表，用于管理知识库的基本信息、向量存储配置、模型选择等
 *
 * @author matrix
 * @date 2024-03-14
 */
@Mapper
public interface AiDatasetMapper extends MatrixBaseMapper<AiDatasetEntity> {

	/**
	 * “获取 AI 数据集”页面
	 * @param page 页
	 * @return {@link Page }<{@link AiDatasetEntity }>
	 */
	Page<AiDatasetEntity> getAiDatasetPage(Page<AiDatasetEntity> page);

	/**
	 * 查询当前用户已通过权限筛选的知识库，并补齐文档数量。
	 * listDataset 原先直接调用 selectList，导致 units 保持默认值，门户卡片无法显示文档数。
	 */
	List<AiDatasetEntity> listAccessibleWithUnits(@Param("datasetIds") Collection<Long> datasetIds,
			@Param("tenantId") Long tenantId);

}
