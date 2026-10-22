package com.deepdimen.matrix.admin.mobilepush;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobilePushServiceImpl {

	private final JdbcTemplate jdbc;

	private final MobilePushProperties properties;

	private final PushSecrets secrets;

	public MobilePushServiceImpl(JdbcTemplate jdbc, MobilePushProperties properties, PushSecrets secrets) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.secrets = secrets;
	}

	public void requireEnabled() {
		if (!properties.isEnabled())
			throw new IllegalStateException("系统推送服务尚未配置，站内消息可正常查看");
	}

	public record BindingDTO(String installationId, String bindingId, String installationCredential) {
		@Override
		public String toString() {
			return "BindingDTO[redacted]";
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public BindingDTO register(PushIdentity identity, PushRegistrationDTO request, String credential) {
		requireEnabled();
		String env = properties.getEnvironment();
		if (!properties.applicationId().equals(request.getAppId()) || !"HMS_ANDROID".equals(request.getChannel())
				|| !"ANDROID".equals(request.getPlatform()))
			throw new AccessDeniedException("应用或推送渠道不匹配");
		String installationId = UUID.fromString(request.getInstallationId()).toString();
		jdbc.update(
				"INSERT INTO sys_push_session_fence (environment,session_id,installation_id,generation,updated_at) VALUES (?,?,?,0,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE generation=generation",
				env, identity.sessionId(), installationId);
		Long fence = jdbc.queryForObject(
				"SELECT generation FROM sys_push_session_fence WHERE environment=? AND session_id=? AND installation_id=? FOR UPDATE",
				Long.class, env, identity.sessionId(), installationId);
		if (fence == null || request.getGeneration() <= fence)
			throw new AccessDeniedException("安装绑定请求已撤销");
		List<Map<String, Object>> found = jdbc.queryForList(
				"SELECT credential_hash,token_hash,generation FROM sys_push_installation WHERE id=? AND environment=? FOR UPDATE",
				installationId, env);
		String issuedCredential = null;
		if (found.isEmpty()) {
			if (credential != null && !credential.matches("[a-f0-9]{64}"))
				throw new AccessDeniedException("安装绑定凭证无效");
			issuedCredential = credential == null ? installationCredential(installationId) : credential;
			jdbc.update(
					"INSERT INTO sys_push_installation (id,environment,app_id,channel,credential_hash,generation,token_ciphertext,token_hash,permission_state,status,app_version,os_version,last_seen_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(3))",
					installationId, env, request.getAppId(), request.getChannel(), secrets.hash(issuedCredential),
					request.getGeneration(), secrets.encrypt(request.getToken(), context(installationId)),
					secrets.hash(request.getToken()), request.getPermissionState(), "ACTIVE", request.getAppVersion(),
					request.getOsVersion());
		}
		else {
			Map<String, Object> installation = found.get(0);
			long storedGeneration = ((Number) installation.get("generation")).longValue();
			if (request.getGeneration() < storedGeneration)
				throw new AccessDeniedException("安装绑定请求已过期");
			if (credential == null) {
				Integer sameOwner = jdbc.queryForObject(
						"SELECT COUNT(*) FROM sys_push_subscription WHERE environment=? AND installation_id=? AND tenant_id=? AND user_id=? AND session_id=? AND enabled=1",
						Integer.class, env, installationId, identity.tenantId(), identity.userId(),
						identity.sessionId());
				if (sameOwner == null || sameOwner != 1
						|| !secrets.matches(request.getToken(), (String) installation.get("token_hash")))
					throw new AccessDeniedException("安装绑定凭证无效");
				issuedCredential = installationCredential(installationId);
				credential = issuedCredential;
			}
			if (!secrets.matches(credential, (String) installation.get("credential_hash")))
				throw new AccessDeniedException("安装绑定凭证无效");
			if (request.getGeneration() == storedGeneration
					&& !secrets.matches(request.getToken(), (String) installation.get("token_hash")))
				throw new AccessDeniedException("安装绑定请求已过期");
			jdbc.update(
					"UPDATE sys_push_installation SET generation=?,token_ciphertext=?,token_hash=?,permission_state=?,status='ACTIVE',app_version=?,os_version=?,last_seen_at=CURRENT_TIMESTAMP(3) WHERE id=? AND environment=? AND app_id=? AND channel=?",
					request.getGeneration(), secrets.encrypt(request.getToken(), context(installationId)),
					secrets.hash(request.getToken()), request.getPermissionState(), request.getAppVersion(),
					request.getOsVersion(), installationId, env, request.getAppId(), request.getChannel());
		}
		List<Map<String, Object>> existing = jdbc.queryForList(
				"SELECT binding_id,session_id,enabled FROM sys_push_subscription WHERE environment=? AND installation_id=? AND tenant_id=? AND user_id=? FOR UPDATE",
				env, installationId, identity.tenantId(), identity.userId());
		boolean sameSession = !existing.isEmpty() && identity.sessionId().equals(existing.get(0).get("session_id"))
				&& ((Number) existing.get(0).get("enabled")).intValue() == 1;
		String bindingId = sameSession ? (String) existing.get(0).get("binding_id") : UUID.randomUUID().toString();
		jdbc.update("UPDATE sys_push_subscription SET enabled=0 WHERE environment=? AND installation_id=?", env,
				installationId);
		jdbc.update(
				"INSERT INTO sys_push_subscription (binding_id,environment,installation_id,tenant_id,user_id,session_id,enabled,subscribed_at) VALUES (?,?,?,?,?,?,1,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE subscribed_at=CASE WHEN binding_id=VALUES(binding_id) THEN subscribed_at ELSE VALUES(subscribed_at) END,binding_id=VALUES(binding_id),session_id=VALUES(session_id),enabled=1",
				bindingId, env, installationId, identity.tenantId(), identity.userId(), identity.sessionId());
		return new BindingDTO(installationId, bindingId, issuedCredential);
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean unsubscribe(PushIdentity identity, String installationId, String credential, long generation) {
		requireEnabled();
		UUID.fromString(installationId);
		if (generation < 1)
			throw new AccessDeniedException("安装解绑请求无效");
		jdbc.update(
				"INSERT INTO sys_push_session_fence (environment,session_id,installation_id,generation,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE generation=GREATEST(generation,VALUES(generation)),updated_at=VALUES(updated_at)",
				properties.getEnvironment(), identity.sessionId(), installationId, generation);
		if (credential != null && !credential.isBlank()) {
			verifyInstallation(installationId, credential);
			jdbc.update(
					"UPDATE sys_push_installation SET generation=GREATEST(generation,?) WHERE id=? AND environment=?",
					generation, installationId, properties.getEnvironment());
		}
		jdbc.update(
				"UPDATE sys_push_subscription SET enabled=0 WHERE environment=? AND installation_id=? AND tenant_id=? AND user_id=? AND session_id=?",
				properties.getEnvironment(), installationId, identity.tenantId(), identity.userId(),
				identity.sessionId());
		return true;
	}

	private void verifyInstallation(String id, String credential) {
		List<String> hashes = jdbc.query(
				"SELECT credential_hash FROM sys_push_installation WHERE id=? AND environment=? FOR UPDATE",
				(rs, row) -> rs.getString(1), id, properties.getEnvironment());
		if (!hashes.isEmpty() && (hashes.size() != 1 || !secrets.matches(credential, hashes.get(0))))
			throw new AccessDeniedException("安装绑定凭证无效");
	}

	public PushPreferencesDTO preferences(long tenantId, long userId) {
		if (!properties.isEnabled())
			return PushPreferencesDTO.defaults();
		return jdbc.query(
				"SELECT enabled,task_enabled,information_enabled,system_enabled FROM sys_push_preference WHERE environment=? AND tenant_id=? AND user_id=?",
				(rs, row) -> new PushPreferencesDTO(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3),
						rs.getBoolean(4)),
				properties.getEnvironment(), tenantId, userId)
			.stream()
			.findFirst()
			.orElse(PushPreferencesDTO.defaults());
	}

	@Transactional(rollbackFor = Exception.class)
	public PushPreferencesDTO savePreferences(PushIdentity identity, PushPreferencesDTO value) {
		requireEnabled();
		jdbc.update(
				"INSERT INTO sys_push_preference (environment,tenant_id,user_id,enabled,task_enabled,information_enabled,system_enabled) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),task_enabled=VALUES(task_enabled),information_enabled=VALUES(information_enabled),system_enabled=VALUES(system_enabled)",
				properties.getEnvironment(), identity.tenantId(), identity.userId(), value.enabled(),
				value.taskEnabled(), value.informationEnabled(), value.systemEnabled());
		return value;
	}

	public record SettingsDTO(boolean configured, String installationNamespace, PushPreferencesDTO preferences) {
	}

	public SettingsDTO settings(PushIdentity identity) {
		return new SettingsDTO(properties.isEnabled(), properties.isEnabled()
				? secrets.hash("namespace|" + properties.getEnvironment() + "|" + properties.applicationId()) : null,
				preferences(identity.tenantId(), identity.userId()));
	}

	private String installationCredential(String id) {
		return secrets.hash("credential|" + properties.getEnvironment() + "|" + id);
	}

	public String context(String installationId) {
		return properties.getEnvironment() + "|" + properties.applicationId() + "|" + installationId;
	}

}
