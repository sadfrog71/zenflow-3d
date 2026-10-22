package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.dto.AiDatasetPermissionDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetPermissionEntity;
import com.deepdimen.matrix.knowledge.vo.AiDatasetPermissionVO;

/**
 * 知识库权限服务接口
 *
 * @author matrix
 * @date 2025-03-12
 */
public interface AiDatasetPermissionService extends IService<AiDatasetPermissionEntity> {

	/**
	 * 查询知识库权限配置
	 * @param datasetId 知识库ID
	 * @return 权限配置VO
	 */
	AiDatasetPermissionVO getPermission(Long datasetId);

	/**
	 * 设置知识库权限（先删后插）
	 * @param datasetId 知识库ID
	 * @param dto 权限设置请求
	 */
	void setPermission(Long datasetId, AiDatasetPermissionDTO dto);

	/**
	 * 创建默认权限（permission_type=all），在知识库创建时调用
	 * @param datasetId 知识库ID
	 */
	void createDefaultPermission(Long datasetId);

}
