package com.deepdimen.matrix.admin.mobilepush;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushDispatchRepositoryTest {

	private JdbcTemplate jdbc;

	private PushDispatchRepository repository;

	private MobilePushOutboxServiceImpl publisher;

	private TransactionTemplate transaction;

	@BeforeEach
	void setup() throws Exception {
		var ds = new DriverManagerDataSource(
				"jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
		jdbc = new JdbcTemplate(ds);
		transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
		Path root = Path.of("").toAbsolutePath();
		while (root != null && !Files.exists(root.resolve("db/53matrix_mobile_push.sql")))
			root = root.getParent();
		assertNotNull(root);
		for (String sql : Files.readString(root.resolve("db/53matrix_mobile_push.sql")).split(";"))
			if (!sql.isBlank())
				jdbc.execute(sql);
		jdbc.execute(
				"CREATE TABLE sys_message (id BIGINT PRIMARY KEY, tenant_id BIGINT, source_type VARCHAR(20), send_flag CHAR(1), del_flag CHAR(1), expire_time TIMESTAMP)");
		jdbc.execute(
				"CREATE TABLE sys_message_relation (id BIGINT PRIMARY KEY,msg_id BIGINT,tenant_id BIGINT,user_id BIGINT,read_flag CHAR(1),del_flag CHAR(1))");
		jdbc.update("INSERT INTO sys_message VALUES (9,7,'TASK','1','0',NULL)");
		jdbc.update("INSERT INTO sys_message_relation VALUES (10,9,7,8,'0','0')");
		var properties = new MobilePushProperties();
		properties.setEnabled(true);
		repository = new PushDispatchRepository(jdbc, properties);
		publisher = new MobilePushOutboxServiceImpl(jdbc, properties);
	}

	private String enqueue() {
		transaction.executeWithoutResult(status -> publisher.enqueue(7L, 9L));
		return jdbc.queryForObject("SELECT id FROM sys_push_outbox", String.class);
	}

	@Test
	void enqueueIsTransactionalIdempotentAndRejectsWrongTenant() {
		assertThrows(IllegalStateException.class, () -> publisher.enqueue(7L, 9L));
		transaction.executeWithoutResult(status -> {
			publisher.enqueue(8L, 9L);
		});
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_push_outbox", Integer.class));
		transaction.executeWithoutResult(status -> {
			publisher.enqueue(7L, 9L);
			status.setRollbackOnly();
		});
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_push_outbox", Integer.class));
		enqueue();
		enqueue();
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_push_outbox", Integer.class));
	}

	@Test
	void leasesHaveSingleOwnershipCanBeReclaimedAndRejectStaleFinish() {
		String id = enqueue();
		assertEquals(java.util.List.of(id), repository.ready());
		var first = repository.claim(id, "first");
		assertNotNull(first);
		assertEquals(1, first.attempts());
		assertNull(repository.claim(id, "second"));
		jdbc.update("UPDATE sys_push_outbox SET lease_until=?", Timestamp.from(Instant.now().minusSeconds(1)));
		var second = repository.claim(id, "second");
		assertNotNull(second);
		assertEquals(2, second.attempts());
		repository.finish(first, "first", false, null);
		assertEquals("PROCESSING", jdbc.queryForObject("SELECT status FROM sys_push_outbox", String.class));
		repository.finish(second, "second", true, "RETRY_PENDING");
		assertEquals("RETRY", jdbc.queryForObject("SELECT status FROM sys_push_outbox", String.class));
		assertTrue(repository.ready().isEmpty());
		jdbc.update("UPDATE sys_push_outbox SET next_attempt_at=?, attempts=7",
				Timestamp.from(Instant.now().minusSeconds(1)));
		var last = repository.claim(id, "last");
		repository.finish(last, "last", true, "RETRY_PENDING");
		assertEquals("EXHAUSTED", jdbc.queryForObject("SELECT status FROM sys_push_outbox", String.class));
	}

	@Test
	void rateWindowResetsAndReadReceiptSuppressesDelivery() {
		String id = enqueue();
		var outbox = repository.claim(id, "owner");
		jdbc.update(
				"INSERT INTO sys_push_preference (environment,tenant_id,user_id,enabled,task_enabled,information_enabled,system_enabled) VALUES ('test',7,8,1,1,0,0)");
		for (int i = 0; i < 5; i++)
			assertTrue(repository.reserveRateSlot(outbox));
		assertFalse(repository.reserveRateSlot(outbox));
		jdbc.update("UPDATE sys_push_preference SET window_started_at=?",
				Timestamp.from(Instant.now().minusSeconds(601)));
		assertTrue(repository.reserveRateSlot(outbox));
		assertEquals(1, jdbc.queryForObject("SELECT window_count FROM sys_push_preference", Integer.class));
		assertEquals("TASK", repository.message(outbox).sourceType());
		jdbc.update("UPDATE sys_message_relation SET read_flag='1'");
		assertNull(repository.message(outbox));
	}

	@Test
	void expiredRowsAreTerminalBeforeClaim() {
		String id = enqueue();
		jdbc.update("UPDATE sys_push_outbox SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
		assertTrue(repository.ready().isEmpty());
		assertNull(repository.claim(id, "owner"));
		assertEquals("EXHAUSTED", jdbc.queryForObject("SELECT status FROM sys_push_outbox", String.class));
	}

}
