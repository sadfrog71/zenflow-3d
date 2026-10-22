package com.deepdimen.matrix.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.deepdimen.matrix.common.data.tenant.MatrixTenantConfigProperties;
import com.deepdimen.matrix.common.data.tenant.MatrixTenantHandler;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryMoveDTO;
import com.deepdimen.matrix.knowledge.entity.*;
import com.deepdimen.matrix.knowledge.mapper.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅在显式允许的空白一次性MySQL库运行，绝不能指向开发/测试业务库。 */
@SpringJUnitConfig(AiDocumentCategoryTransactionIntegrationTest.Config.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "MATRIX_CATEGORY_TEST_JDBC_URL", matches = ".+")
class AiDocumentCategoryTransactionIntegrationTest {

	private final JdbcTemplate jdbc;

	private final AiDocumentCategoryService service;

	private final PlatformTransactionManager transactionManager;

	private final AiDocumentMapper documents;

	AiDocumentCategoryTransactionIntegrationTest(JdbcTemplate jdbc, AiDocumentCategoryService service,
			PlatformTransactionManager transactionManager, AiDocumentMapper documents) {
		this.jdbc = jdbc;
		this.service = service;
		this.transactionManager = transactionManager;
		this.documents = documents;
	}

	@BeforeAll
	void initializeDisposableSchema() throws Exception {
		assertEquals("1", System.getenv("MATRIX_CATEGORY_TEST_ALLOW_DISPOSABLE"));
		String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
		assertNotNull(database);
		assertTrue(database.matches("matrix_category_test_[0-9a-f]{16}"));
		assertEquals(System.getenv("MATRIX_CATEGORY_TEST_DATABASE_NAME"), database);
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
		createEntityTable(AiDatasetEntity.class);
		createEntityTable(AiDocumentEntity.class);
		createEntityTable(AiDatasetPermissionEntity.class);
		jdbc.update("INSERT INTO ai_document(id,tenant_id,dataset_id,name,del_flag) VALUES (999,10,20,'迁移前文档','0')");
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_document' AND column_name='category_id'",
				Integer.class));
		jdbc.execute(
				"CREATE TABLE ai_slice (id BIGINT PRIMARY KEY, document_id BIGINT, tenant_id BIGINT, del_flag CHAR(1), slice_status CHAR(1)) ENGINE=InnoDB");
		Path script = Path.of("../db/54matrix_document_category.sql");
		if (!Files.exists(script)) {
			script = Path.of("db/54matrix_document_category.sql");
		}
		String migration = Files.readString(script);
		jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
			try (var sql = connection.createStatement()) {
				for (int repeat = 0; repeat < 2; repeat++) {
					for (String statement : migration.split(";")) {
						if (!statement.isBlank()) {
							sql.execute(statement);
						}
					}
				}
			}
			return null;
		});
		assertEquals("迁移前文档", jdbc.queryForObject("SELECT name FROM ai_document WHERE id=999", String.class));
		assertNull(category(999));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_document", Integer.class));
	}

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM ai_document");
		jdbc.update("DELETE FROM ai_document_category");
		jdbc.update("DELETE FROM ai_dataset_permission");
		jdbc.update("DELETE FROM ai_dataset");
		jdbc.update(
				"INSERT INTO ai_dataset (id, tenant_id, visibility, del_flag) VALUES (20,10,'public','0'),(21,10,'public','0'),(30,11,'public','0')");
		jdbc.update(
				"INSERT INTO ai_document_category(id,tenant_id,dataset_id,parent_id,name,sort) VALUES (1,10,20,0,'政策',0),(2,10,20,1,'水价',0),(3,10,21,0,'其他库',0),(4,11,30,0,'其他租户',0)");
		jdbc.update(
				"INSERT INTO ai_document(id,tenant_id,dataset_id,category_id,del_flag) VALUES (101,10,20,NULL,'0'),(102,10,20,2,'0'),(103,10,21,3,'0'),(104,11,30,4,'0')");
		TenantContextHolder.setTenantId(10L);
	}

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void realMapperRejectsMixedDatasetAndTenantBatchesWithoutPartialUpdate() {
		assertThrows(IllegalArgumentException.class, () -> service.moveDocuments(move(1L, List.of(101L, 103L))));
		assertThrows(IllegalArgumentException.class, () -> service.moveDocuments(move(1L, List.of(101L, 104L))));
		assertNull(category(101));
		assertThrows(IllegalArgumentException.class, () -> service.list(30L));
	}

	@Test
	void successfulMoveParticipatesInTransactionAndRollsBackOnFailure() {
		assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
			service.moveDocuments(move(1L, List.of(101L, 102L)));
			throw new IllegalStateException("模拟后续事务失败");
		}));
		assertNull(category(101));
		assertEquals(2L, category(102));
		service.moveDocuments(move(1L, List.of(101L, 102L)));
		assertEquals(1L, category(101));
		assertEquals(1L, category(102));
	}

	@Test
	void descendantFilterUsesActualMapperAndTenantInterceptor() {
		AiDocumentEntity query = new AiDocumentEntity();
		query.setDatasetId(20L);
		query.setCategoryId(1L);
		service.applyFilter(query);
		var rows = documents.getDocumentPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(), query)
			.getRecords();
		assertEquals(List.of(102L), rows.stream().map(AiDocumentEntity::getId).toList());
		query.setCategoryId(null);
		query.setUncategorized(true);
		service.applyFilter(query);
		assertEquals(List.of(101L),
				documents.getDocumentPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(), query)
					.getRecords()
					.stream()
					.map(AiDocumentEntity::getId)
					.toList());
	}

	@Test
	void foreignKeyAndNonEmptyGuardPreserveDocuments() {
		assertThrows(IllegalArgumentException.class, () -> service.delete(20L, 2L));
		assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE ai_document SET category_id=999 WHERE id=101"));
		service.moveDocuments(move(null, List.of(102L)));
		assertTrue(service.delete(20L, 2L));
		assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM ai_document", Integer.class));
	}

	private Long category(long id) {
		return jdbc.queryForObject("SELECT category_id FROM ai_document WHERE id=?", Long.class, id);
	}

	private AiDocumentCategoryMoveDTO move(Long category, List<Long> ids) {
		AiDocumentCategoryMoveDTO dto = new AiDocumentCategoryMoveDTO();
		dto.setDatasetId(20L);
		dto.setCategoryId(category);
		dto.setDocumentIds(ids);
		return dto;
	}

	private void createEntityTable(Class<?> entity) {
		TableInfo table = TableInfoHelper.getTableInfo(entity);
		List<String> columns = new ArrayList<>();
		columns.add(table.getKeyColumn() + " BIGINT PRIMARY KEY");
		for (var field : table.getFieldList()) {
			// 模拟升级前的真实表，让迁移首次实际执行 ADD COLUMN。
			if (entity == AiDocumentEntity.class && field.getColumn().equals("category_id")) {
				continue;
			}
			Class<?> type = field.getPropertyType();
			String sqlType = type == Long.class ? "BIGINT" : type == Integer.class ? "INT"
					: type == LocalDateTime.class ? "DATETIME" : type.isArray() ? "TEXT" : "VARCHAR(255)";
			columns.add(field.getColumn() + " " + sqlType);
		}
		if (table.getFieldList().stream().noneMatch(field -> field.getColumn().equals("tenant_id"))) {
			columns.add("tenant_id BIGINT");
		}
		jdbc.execute("CREATE TABLE " + table.getTableName() + " (" + String.join(",", columns)
				+ ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
	}

	@Configuration
	@EnableTransactionManagement
	static class Config {

		@Bean
		DataSource dataSource() {
			return new DriverManagerDataSource(System.getenv("MATRIX_CATEGORY_TEST_JDBC_URL"),
					System.getenv("MATRIX_CATEGORY_TEST_JDBC_USER"),
					System.getenv("MATRIX_CATEGORY_TEST_JDBC_PASSWORD"));
		}

		@Bean
		JdbcTemplate jdbc(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean
		SqlSessionFactory sessionFactory(DataSource dataSource) throws Exception {
			var factory = new MybatisSqlSessionFactoryBean();
			factory.setDataSource(dataSource);
			var configuration = new MybatisConfiguration();
			configuration.setMapUnderscoreToCamelCase(true);
			configuration.addMapper(AiDatasetMapper.class);
			configuration.addMapper(AiDatasetPermissionMapper.class);
			configuration.addMapper(AiDocumentCategoryMapper.class);
			configuration.addMapper(AiDocumentMapper.class);
			factory.setConfiguration(configuration);
			factory.setMapperLocations(new ClassPathResource("mapper/AiDocumentMapper.xml"));
			var interceptor = new MybatisPlusInterceptor();
			interceptor.addInnerInterceptor(
					new TenantLineInnerInterceptor(new MatrixTenantHandler(new MatrixTenantConfigProperties())));
			factory.setPlugins(interceptor);
			return factory.getObject();
		}

		@Bean
		SqlSessionTemplate sessions(SqlSessionFactory factory) {
			return new SqlSessionTemplate(factory);
		}

		@Bean
		AiDatasetMapper datasets(SqlSessionTemplate sessions) {
			return sessions.getMapper(AiDatasetMapper.class);
		}

		@Bean
		AiDatasetPermissionMapper permissions(SqlSessionTemplate sessions) {
			return sessions.getMapper(AiDatasetPermissionMapper.class);
		}

		@Bean
		AiDocumentCategoryMapper categories(SqlSessionTemplate sessions) {
			return sessions.getMapper(AiDocumentCategoryMapper.class);
		}

		@Bean
		AiDocumentMapper documents(SqlSessionTemplate sessions) {
			return sessions.getMapper(AiDocumentMapper.class);
		}

		@Bean
		AiDocumentCategoryService service(AiDatasetMapper datasets, AiDatasetPermissionMapper permissions,
				AiDocumentCategoryMapper categories, AiDocumentMapper documents) {
			return new AiDocumentCategoryService(datasets, permissions, categories, documents);
		}

	}

}
