package com.deepdimen.matrix.assistant.provider.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("matrix.assistant.internal-auth")
public class AssistantInternalAuthProperties {

	private String secret;

	private String issuer = "matrix-assistant";

	private String workloadId;

	private List<String> trustedWorkloadIds = List.of("matrix-discover", "matrix-boot");

	private Duration ttl = Duration.ofMinutes(3);

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	public String getWorkloadId() {
		return workloadId;
	}

	public void setWorkloadId(String workloadId) {
		this.workloadId = workloadId;
	}

	public List<String> getTrustedWorkloadIds() {
		return trustedWorkloadIds;
	}

	public void setTrustedWorkloadIds(List<String> trustedWorkloadIds) {
		this.trustedWorkloadIds = trustedWorkloadIds;
	}

	public Duration getTtl() {
		return ttl;
	}

	public void setTtl(Duration ttl) {
		this.ttl = ttl;
	}

}
