package com.deepdimen.matrix.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.admin.api.entity.AiAuthConfigEntity;

/**
 * 认证配置服务接口
 *
 * @author matrix
 * @date 2026-03-12
 */
public interface AiAuthConfigService extends IService<AiAuthConfigEntity> {

	/**
	 * 根据租户ID和认证类型查询配置（secret 解密后返回）
	 * @param tenantId 租户ID
	 * @param authType 认证类型
	 * @return 认证配置（secret 已解密）
	 */
	AiAuthConfigEntity getByTenantAndType(Long tenantId, String authType);

	/**
	 * 保存认证配置（secret 加密存储）
	 * @param entity 认证配置
	 */
	void saveConfig(AiAuthConfigEntity entity);

	/**
	 * 更新认证配置（secret 加密存储）
	 * @param entity 认证配置
	 */
	void updateConfig(AiAuthConfigEntity entity);

}
