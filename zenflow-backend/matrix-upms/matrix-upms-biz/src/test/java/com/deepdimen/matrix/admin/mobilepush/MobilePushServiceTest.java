package com.deepdimen.matrix.admin.mobilepush;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;

class MobilePushServiceTest {

	private JdbcTemplate jdbc;

	private MobilePushServiceImpl service;

	private PushSecrets secrets;

	private final PushIdentity identity = new PushIdentity(7, 8, "session-a");

	private MobilePushProperties properties;

	@BeforeEach
	void setup() throws Exception {
		var dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
		jdbc = new JdbcTemplate(dataSource);
		Path root = Path.of("").toAbsolutePath();
		while (root != null && !Files.exists(root.resolve("db/53matrix_mobile_push.sql")))
			root = root.getParent();
		assertNotNull(root);
		for (String statement : Files.readString(root.resolve("db/53matrix_mobile_push.sql")).split(";"))
			if (!statement.isBlank())
				jdbc.execute(statement);
		properties = new MobilePushProperties();
		properties.setEnabled(true);
		properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
		secrets = new PushSecrets(properties);
		var proxy = new org.springframework.aop.framework.ProxyFactory(
				new MobilePushServiceImpl(jdbc, properties, secrets));
		proxy.setProxyTargetClass(true);
		proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
				new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
				new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
		service = (MobilePushServiceImpl) proxy.getProxy();
	}

	private PushRegistrationDTO request(String id, long generation, String token) {
		PushRegistrationDTO value = new PushRegistrationDTO();
		value.setInstallationId(id);
		value.setGeneration(generation);
		value.setPlatform("ANDROID");
		value.setChannel("HMS_ANDROID");
		value.setAppId("com.avataraitest.mobile");
		value.setToken(token);
		value.setPermissionState("GRANTED");
		value.setAppVersion("1.0.0");
		value.setOsVersion("15");
		return value;
	}

	@Test
	void reauthorizesOwnerAndFencesLateRegistrationAfterUnsubscribe() {
		String id = UUID.randomUUID().toString();
		MobilePushServiceImpl.BindingDTO initial = service.register(identity, request(id, 1, "sensitive-token"), null);
		assertFalse(jdbc.queryForObject("SELECT token_ciphertext FROM sys_push_installation", String.class)
			.contains("sensitive-token"));
		assertThrows(AccessDeniedException.class, () -> service.register(new PushIdentity(9, 10, "session-b"),
				request(id, 2, "sensitive-token"), "wrong"));
		assertTrue(service.unsubscribe(identity, id, initial.installationCredential(), 3));
		assertThrows(AccessDeniedException.class,
				() -> service.register(identity, request(id, 2, "sensitive-token"), initial.installationCredential()));
		assertEquals(0, jdbc.queryForObject("SELECT enabled FROM sys_push_subscription", Integer.class));
	}

	@Test
	void retriesAnInitiallyLostResponseAndRotatesTokenWithoutChangingCurrentBinding() {
		String id = UUID.randomUUID().toString();
		MobilePushServiceImpl.BindingDTO initial = service.register(identity, request(id, 1, "token-one"), null);
		MobilePushServiceImpl.BindingDTO recovered = service.register(identity, request(id, 2, "token-one"), null);
		assertEquals(initial.installationCredential(), recovered.installationCredential());
		MobilePushServiceImpl.BindingDTO rotated = service.register(identity, request(id, 3, "token-two"),
				recovered.installationCredential());
		assertEquals(initial.bindingId(), rotated.bindingId());
		assertEquals("token-two",
				secrets.decrypt(jdbc.queryForObject("SELECT token_ciphertext FROM sys_push_installation", String.class),
						service.context(id)));
		assertEquals(1,
				jdbc.queryForObject("SELECT COUNT(*) FROM sys_push_subscription WHERE enabled=1", Integer.class));
	}

	@Test
	void rebindingChangesOpaqueContextAndDisablesThePreviousTenant() {
		String id = UUID.randomUUID().toString();
		MobilePushServiceImpl.BindingDTO initial = service.register(identity, request(id, 1, "token"), null);
		MobilePushServiceImpl.BindingDTO next = service.register(new PushIdentity(9, 8, "session-b"),
				request(id, 2, "token"), initial.installationCredential());
		assertNotEquals(initial.bindingId(), next.bindingId());
		assertEquals(0,
				jdbc.queryForObject("SELECT enabled FROM sys_push_subscription WHERE tenant_id=7", Integer.class));
		assertEquals(1,
				jdbc.queryForObject("SELECT enabled FROM sys_push_subscription WHERE tenant_id=9", Integer.class));
	}

	@Test
	void missingFirstResponseCannotResurrectASubscriptionAfterLogout() {
		String id = UUID.randomUUID().toString();
		assertTrue(service.unsubscribe(identity, id, null, 2));
		assertThrows(AccessDeniedException.class, () -> service.register(identity, request(id, 1, "token"), null));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_push_installation", Integer.class));
		assertNotNull(service.register(identity, request(id, 3, "token"), null));
	}

	@Test
	void persistedBootstrapProofSurvivesLostResponseAndSessionReplacement() {
		String id = UUID.randomUUID().toString();
		String proof = "a".repeat(64);
		service.register(identity, request(id, 1, "token"), proof); // Response never
																	// reaches client.
		service.unsubscribe(identity, id, proof, 2);
		assertThrows(AccessDeniedException.class, () -> service.register(identity, request(id, 1, "token"), proof));
		MobilePushServiceImpl.BindingDTO recovered = service.register(new PushIdentity(9, 8, "session-b"),
				request(id, 3, "token"), proof);
		assertNotNull(recovered.bindingId());
		assertEquals(0,
				jdbc.queryForObject("SELECT enabled FROM sys_push_subscription WHERE tenant_id=7", Integer.class));
		assertThrows(AccessDeniedException.class,
				() -> service.register(new PushIdentity(9, 8, "session-b"), request(id, 4, "token"), "b".repeat(64)));
	}

	@Test
	void proofBearingRevocationCanPrecedeInitialRegistration() {
		String id = UUID.randomUUID().toString();
		String proof = "a".repeat(64);
		assertTrue(service.unsubscribe(identity, id, proof, 2));
		assertThrows(AccessDeniedException.class, () -> service.register(identity, request(id, 1, "token"), proof));
		assertThrows(AccessDeniedException.class, () -> service.register(identity, request(id, 3, "token"), "short"));
		assertNotNull(service.register(identity, request(id, 3, "token"), proof));
	}

	@Test
	void preferencesAndNamespacesStayInTheirEnvironmentAndIdentity() {
		assertFalse(service.preferences(7, 8).enabled());
		service.savePreferences(identity, new PushPreferencesDTO(true, true, false, false));
		assertTrue(service.preferences(7, 8).allows("TASK"));
		assertFalse(service.preferences(7, 8).allows("INFORMATION"));
		assertFalse(service.preferences(9, 8).enabled());
		String namespace = service.settings(identity).installationNamespace();
		properties.setEnvironment("production");
		assertNotEquals(namespace, service.settings(identity).installationNamespace());
		assertFalse(service.preferences(7, 8).enabled());
		assertThrows(AccessDeniedException.class,
				() -> service.register(identity, request(UUID.randomUUID().toString(), 1, "token"), null));
	}

	@Test
	void authenticatedEncryptionRejectsTamperingAndWrongEnvironment() {
		String encrypted = secrets.encrypt("secret", "test|installation");
		assertNotEquals(encrypted, secrets.encrypt("secret", "test|installation"));
		assertEquals("secret", secrets.decrypt(encrypted, "test|installation"));
		assertThrows(IllegalStateException.class, () -> secrets.decrypt(encrypted, "production|installation"));
		assertThrows(IllegalStateException.class,
				() -> secrets.decrypt(encrypted.substring(0, encrypted.length() - 4) + "AAAA", "test|installation"));
	}

}
