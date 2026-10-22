package com.deepdimen.matrix.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.admin.api.entity.AiApiTokenEntity;

import java.time.LocalDateTime;

/**
 * API Token 服务接口
 *
 * @author matrix
 * @date 2025-03-12
 */
public interface AiApiTokenService extends IService<AiApiTokenEntity> {

	/**
	 * 分页查询当前租户的 API Token 列表
	 * @param page 分页参数
	 * @return 分页结果
	 */
	IPage<AiApiTokenEntity> pageTokens(Page<AiApiTokenEntity> page);

	/**
	 * 生成新的 API Token 每租户最多 50 个活跃 Token，超限抛出异常
	 * @param tokenName Token名称
	 * @param expireTime 过期时间，null 表示永不过期
	 * @return 生成的 Token 实体
	 */
	AiApiTokenEntity generateToken(String tokenName, LocalDateTime expireTime);

	/**
	 * 禁用 Token
	 * @param id Token ID
	 */
	void disableToken(Long id);

	/**
	 * 删除 Token（逻辑删除）
	 * @param id Token ID
	 */
	void deleteToken(Long id);

}
