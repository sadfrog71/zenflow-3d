package com.deepdimen.matrix.admin.mobilepush;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PushSecrets {

	private final MobilePushProperties properties;

	private final SecureRandom random = new SecureRandom();

	public PushSecrets(MobilePushProperties properties) {
		this.properties = properties;
	}

	private byte[] key() {
		try {
			byte[] value = Base64.getDecoder().decode(properties.getEncryptionKey());
			if (value.length != 32)
				throw new IllegalArgumentException();
			return value;
		}
		catch (Exception ignored) {
			throw new IllegalStateException("移动推送加密密钥未正确配置");
		}
	}

	public String credential() {
		byte[] value = new byte[32];
		random.nextBytes(value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	public String hash(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key(), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ignored) {
			throw new IllegalStateException("移动推送凭证校验不可用");
		}
	}

	public boolean matches(String plain, String expected) {
		return plain != null && expected != null && MessageDigest.isEqual(hash(plain).getBytes(StandardCharsets.UTF_8),
				expected.getBytes(StandardCharsets.UTF_8));
	}

	public String encrypt(String value, String context) {
		try {
			byte[] nonce = new byte[12];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			return "v1." + Base64.getEncoder().encodeToString(nonce) + "."
					+ Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ignored) {
			throw new IllegalStateException("移动推送凭证加密失败");
		}
	}

	public String decrypt(String value, String context) {
		try {
			String[] parts = value.split("\\.");
			if (parts.length != 3 || !"v1".equals(parts[0]))
				throw new IllegalArgumentException();
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
					new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
		}
		catch (Exception ignored) {
			throw new IllegalStateException("移动推送凭证解密失败");
		}
	}

}
