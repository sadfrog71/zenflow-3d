package com.deepdimen.matrix.admin.service.impl;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SM4;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.entity.AiAuthConfigEntity;
import com.deepdimen.matrix.admin.mapper.AiAuthConfigMapper;
import com.deepdimen.matrix.admin.service.AiAuthConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证配置服务实现
 *
 * @author matrix
 * @date 2026-03-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuthConfigServiceImpl extends ServiceImpl<AiAuthConfigMapper, AiAuthConfigEntity>
		implements AiAuthConfigService {

	/**
	 * SM4 加密密钥（16字节，配置在 application.yml）
	 */
	@Value("${matrix.auth-config.sm4-key:deepdimen1234567}")
	private String sm4Key;

	private volatile SM4 sm4Instance;

	private SM4 getSm4() {
		if (sm4Instance == null) {
			synchronized (this) {
				if (sm4Instance == null) {
					sm4Instance = SmUtil.sm4(sm4Key.getBytes());
				}
			}
		}
		return sm4Instance;
	}

	@Override
	public AiAuthConfigEntity getByTenantAndType(Long tenantId, String authType) {
		AiAuthConfigEntity entity = getOne(
				new LambdaQueryWrapper<AiAuthConfigEntity>().eq(AiAuthConfigEntity::getTenantId, tenantId)
					.eq(AiAuthConfigEntity::getAuthType, authType));
		if (entity != null && StringUtils.hasText(entity.getSecretEncrypted())) {
			entity.setSecretEncrypted(decrypt(entity.getSecretEncrypted()));
		}
		return entity;
	}

	@Override
	public void saveConfig(AiAuthConfigEntity entity) {
		if (StringUtils.hasText(entity.getSecretEncrypted())) {
			entity.setSecretEncrypted(encrypt(entity.getSecretEncrypted()));
		}
		save(entity);
	}

	@Override
	public void updateConfig(AiAuthConfigEntity entity) {
		if (StringUtils.hasText(entity.getSecretEncrypted())) {
			entity.setSecretEncrypted(encrypt(entity.getSecretEncrypted()));
		}
		updateById(entity);
	}

	private String encrypt(String plainText) {
		return getSm4().encryptHex(plainText);
	}

	private String decrypt(String cipherText) {
		return getSm4().decryptStr(cipherText);
	}

}
