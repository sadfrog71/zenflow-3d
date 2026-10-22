package com.deepdimen.matrix.admin.mobilepush;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "matrix.mobile-push")
public class MobilePushProperties {

	private boolean enabled;

	private String environment = "test";

	private String encryptionKey;

	private String hmsAppId;

	private String hmsClientSecret;

	public String applicationId() {
		if (!"test".equals(environment) && !"production".equals(environment)) {
			throw new IllegalStateException("移动推送环境配置无效");
		}
		return "production".equals(environment) ? "com.avatarai.mobile" : "com.avataraitest.mobile";
	}

}
