package com.deepdimen.matrix.common.api.encrypt.core;

import com.deepdimen.matrix.common.api.encrypt.config.ApiEncryptProperties;
import com.deepdimen.matrix.common.api.encrypt.enums.EncryptType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 默认的密钥处理器
 *
 * @author L.cm
 */
@RequiredArgsConstructor
public class DefaultSecretKeyResolver implements ISecretKeyResolver {

	private final ApiEncryptProperties properties;

	@Override
	public String getSecretKey(HttpServletRequest request, EncryptType encryptType) {
		return switch (encryptType) {
			case SM4 -> properties.getSm4Key();
			case DES -> properties.getDesKey();
			case RSA -> properties.getRsaPrivateKey();
			default -> properties.getAesKey();
		};
	}

}
