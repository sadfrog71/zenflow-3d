package com.deepdimen.matrix.admin.service.impl;

import com.deepdimen.matrix.admin.api.entity.AiApiTokenEntity;
import com.deepdimen.matrix.admin.mapper.AiApiTokenMapper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.ApiTokenValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API Token 验证器实现（单体模式下注入到 AiApiTokenFilter）
 *
 * @author matrix
 * @date 2025-03-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiTokenValidatorImpl implements ApiTokenValidator {

	private static final String SYSTEM_TOKEN_NAME = "Dify-Knowledge-Sync";

	private final AiApiTokenMapper aiApiTokenMapper;

	@Override
	public ApiTokenInfo findValidToken(String tokenValue) {
		AiApiTokenEntity entity = aiApiTokenMapper.findValidByTokenValue(tokenValue);
		if (entity == null) {
			return null;
		}
		return new ApiTokenInfo(entity.getId(), entity.getTenantId(), entity.getCreateBy());
	}

	@Override
	public void updateLastUsedTime(Long tokenId) {
		AiApiTokenEntity entity = new AiApiTokenEntity();
		entity.setId(tokenId);
		entity.setLastUsedTime(LocalDateTime.now());
		aiApiTokenMapper.updateById(entity);
	}

	@Override
	public void setTenantContext(String tenantId) {
		if (tenantId != null) {
			TenantContextHolder.setTenantId(Long.parseLong(tenantId));
		}
	}

	@Override
	public void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Override
	public String findFirstActiveTokenValue(String tenantId) {
		AiApiTokenEntity entity = aiApiTokenMapper.findFirstActiveByTenantId(tenantId);
		return entity != null ? entity.getTokenValue() : null;
	}

	@Override
	public String getOrCreateTokenValue(String tenantId, String createBy) {
		// 先查询已有的活跃 Token
		String existing = findFirstActiveTokenValue(tenantId);
		if (existing != null) {
			return existing;
		}
		// 没有则自动创建一个系统级 Token
		try {
			AiApiTokenEntity entity = new AiApiTokenEntity();
			entity.setTenantId(tenantId);
			entity.setTokenName(SYSTEM_TOKEN_NAME);
			entity.setTokenValue(UUID.randomUUID().toString().replace("-", ""));
			entity.setStatus("active");
			entity.setCreateBy(createBy);
			entity.setCreateTime(LocalDateTime.now());
			aiApiTokenMapper.insert(entity);
			log.info("自动创建系统级 API Token, tenantId={}, tokenName={}", tenantId, SYSTEM_TOKEN_NAME);
			return entity.getTokenValue();
		}
		catch (Exception e) {
			log.error("自动创建 API Token 失败, tenantId={}, error={}", tenantId, e.getMessage(), e);
			return null;
		}
	}

}
