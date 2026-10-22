package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiDatasetEntityDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;

/**
 * AI数据集（知识库）服务接口
 * <p>
 * 提供知识库的创建、查询、更新等管理功能，支持文档、数据等多种数据源的知识管理
 * </p>
 *
 * @author matrix
 */
public interface AiDatasetService extends IService<AiDatasetEntity> {

	/**
	 * 分页查询AI数据集列表
	 * <p>
	 * 获取当前用户有权限访问的知识库列表，支持分页展示
	 * </p>
	 * @param page 分页参数
	 * @return 知识库分页数据
	 */
	Page<AiDatasetEntity> getAiDatasetPage(Page<AiDatasetEntity> page);

	/**
	 * 根据ID查询知识库详情
	 * <p>
	 * 获取指定知识库的完整信息，包括配置参数、权限设置等
	 * </p>
	 * @param datasetId 知识库ID
	 * @return 包含知识库信息的响应结果
	 */
	R getDatasetById(Long datasetId);

	/**
	 * 保存新知识库
	 * <p>
	 * 创建新的知识库，包括基本信息、向量存储配置、权限设置等
	 * </p>
	 * @param aiDatasetEntityDTO 知识库创建参数
	 * @return 创建结果
	 */
	R saveDataset(AiDatasetEntityDTO aiDatasetEntityDTO);

	/**
	 * 更新知识库信息
	 * <p>
	 * 修改知识库的配置信息，如名称、描述、嵌入模型、权限等
	 * </p>
	 * @param aiDatasetEntityDTO 知识库更新参数
	 * @return 更新结果
	 */
	R updateDataset(AiDatasetEntityDTO aiDatasetEntityDTO);

	/**
	 * 根据条件查询知识库信息
	 * <p>
	 * 支持按名称、类型等条件查询知识库
	 * </p>
	 * @param dataset 查询条件
	 * @return 符合条件的知识库信息
	 */
	R getAiDataset(AiDatasetEntity dataset);

	/**
	 * 获取知识库列表
	 * <p>
	 * 获取当前用户可访问的所有知识库，通常用于下拉选择等场景
	 * </p>
	 * @return 知识库列表
	 */
	R listDataset();

	/**
	 * 根据数据集实体获取AI数据集
	 * @param title AI数据集实体
	 * @return 对应的AI数据集
	 */
	R autoGen(String title);

}
