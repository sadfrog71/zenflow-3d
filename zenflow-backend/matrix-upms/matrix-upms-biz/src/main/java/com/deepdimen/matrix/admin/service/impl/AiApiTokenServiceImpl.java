package com.deepdimen.matrix.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.entity.AiApiTokenEntity;
import com.deepdimen.matrix.admin.mapper.AiApiTokenMapper;
import com.deepdimen.matrix.admin.service.AiApiTokenService;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API Token 服务实现
 *
 * @author matrix
 * @date 2025-03-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiApiTokenServiceImpl extends ServiceImpl<AiApiTokenMapper, AiApiTokenEntity>
		implements AiApiTokenService {

	/**
	 * 每租户最大活跃 Token 数量
	 */
	private static final int MAX_ACTIVE_TOKEN_COUNT = 50;

	@Override
	public IPage<AiApiTokenEntity> pageTokens(Page<AiApiTokenEntity> page) {
		return this.page(page, Wrappers.<AiApiTokenEntity>lambdaQuery().orderByDesc(AiApiTokenEntity::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiApiTokenEntity generateToken(String tokenName, LocalDateTime expireTime) {
		MatrixUser user = SecurityUtils.getUser();
		if (user == null) {
			throw new IllegalStateException("无法获取当前用户信息");
		}
		String tenantId = String.valueOf(user.getTenantId());
		long activeCount = this.baseMapper.countActiveByTenantId(tenantId);
		if (activeCount >= MAX_ACTIVE_TOKEN_COUNT) {
			throw new IllegalArgumentException(
					"当前租户活跃 Token 数量已达上限（" + MAX_ACTIVE_TOKEN_COUNT + " 个），请先删除或禁用不需要的 Token");
		}

		AiApiTokenEntity entity = new AiApiTokenEntity();
		entity.setTokenName(tokenName);
		entity.setTokenValue(UUID.randomUUID().toString().replace("-", ""));
		entity.setStatus("active");
		entity.setExpireTime(expireTime);
		this.save(entity);
		log.info("生成 API Token，名称：{}，租户：{}", tokenName, tenantId);
		return entity;
	}

	@Override
	public void disableToken(Long id) {
		AiApiTokenEntity entity = this.getById(id);
		if (entity == null) {
			throw new IllegalArgumentException("Token 不存在");
		}
		entity.setStatus("disabled");
		this.updateById(entity);
		log.info("禁用 API Token，ID：{}", id);
	}

	@Override
	public void deleteToken(Long id) {
		if (!this.removeById(id)) {
			throw new IllegalArgumentException("Token 不存在");
		}
		log.info("删除 API Token，ID：{}", id);
	}

}
