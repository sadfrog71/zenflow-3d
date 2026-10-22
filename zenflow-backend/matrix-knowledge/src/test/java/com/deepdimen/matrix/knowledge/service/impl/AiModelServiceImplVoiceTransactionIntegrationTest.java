package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Proxy;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiModelService;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

@SpringJUnitConfig(AiModelServiceImplVoiceTransactionIntegrationTest.TestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIfEnvironmentVariable(named = "MATRIX_VOICE_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiModelServiceImplVoiceTransactionIntegrationTest {

	private static final String CONFLICT_MESSAGE = "当前租户的该语音能力默认模型已被并发修改，请刷新后重试";

	private final JdbcTemplate jdbcTemplate;

	private final AiModelService service;

	AiModelServiceImplVoiceTransactionIntegrationTest(JdbcTemplate jdbcTemplate, AiModelService service) {
		this.jdbcTemplate = jdbcTemplate;
		this.service = service;
	}

	@BeforeAll
	void verifyDisposableMigratedSchema() {
		assertEquals("1", System.getenv("MATRIX_VOICE_TEST_ALLOW_DISPOSABLE"));
		String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
		assertTrue(databaseName != null && databaseName.matches("matrix_voice_service_test_[0-9a-f]{16}"));
		assertEquals(System.getenv("MATRIX_VOICE_TEST_DATABASE_NAME"), databaseName);
		assertEquals(1, metadataCount("information_schema.columns", "column_name = 'default_voice_capability'"));
		assertEquals(1, metadataCount("information_schema.statistics",
				"index_name = 'uk_ai_model_default_voice_capability' AND non_unique = 0"));
		assertEquals(1, metadataCount("information_schema.table_constraints",
				"constraint_name = 'chk_ai_model_default_voice_capability' AND constraint_type = 'CHECK'"));
	}

	@BeforeEach
	void resetData() {
		jdbcTemplate.update("DELETE FROM ai_model");
	}

	@Test
	void saveConflictRollsBackThePreviouslyUnsetDefault() {
		insertVoiceModel(1L, "old-asr", "1");
		AiModelEntity replacement = voiceModel(2L, "new-asr", "1");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.saveModel(replacement));

		assertEquals(CONFLICT_MESSAGE, error.getMessage());
		assertEquals("1", defaultFlag(1L));
		assertEquals(0, countModel(2L));
		assertEquals(0, countModel(99L));
	}

	@Test
	void updateConflictRollsBackThePreviouslyUnsetDefault() {
		insertVoiceModel(1L, "old-asr", "1");
		insertVoiceModel(2L, "new-asr", "0");
		AiModelEntity replacement = voiceModel(2L, "new-asr", "1");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.updateModel(replacement));

		assertEquals(CONFLICT_MESSAGE, error.getMessage());
		assertEquals("1", defaultFlag(1L));
		assertEquals("0", defaultFlag(2L));
		assertEquals(0, countModel(99L));
	}

	private void insertVoiceModel(Long id, String name, String defaultModel) {
		jdbcTemplate.update(
				"INSERT INTO ai_model (id, name, model_type, default_model, del_flag, ext_data, tenant_id) VALUES (?, ?, 'Voice', ?, '0', JSON_OBJECT('voiceType', '1'), 1)",
				id, name, defaultModel);
	}

	private String defaultFlag(Long id) {
		return jdbcTemplate.queryForObject("SELECT default_model FROM ai_model WHERE id = ?", String.class, id);
	}

	private int countModel(Long id) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_model WHERE id = ?", Integer.class, id);
	}

	private int metadataCount(String table, String predicate) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
				+ " WHERE table_schema = DATABASE() AND table_name = 'ai_model' AND " + predicate, Integer.class);
	}

	private static AiModelEntity voiceModel(Long id, String name, String defaultModel) {
		AiModelEntity model = new AiModelEntity();
		model.setId(id);
		model.setName(name);
		model.setModelType("Voice");
		model.setDefaultModel(defaultModel);
		model.setExtData("{\"voiceType\":\"1\"}");
		return model;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	static class TestConfiguration {

		@Bean
		DataSource dataSource() {
			DriverManagerDataSource dataSource = new DriverManagerDataSource();
			dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
			dataSource.setUrl(System.getenv("MATRIX_VOICE_TEST_JDBC_URL"));
			dataSource.setUsername(System.getenv().getOrDefault("MATRIX_VOICE_TEST_DB_USER", "root"));
			dataSource.setPassword(System.getenv().getOrDefault("MATRIX_VOICE_TEST_DB_PASSWORD", ""));
			return dataSource;
		}

		@Bean
		JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean
		AiModelMapper aiModelMapper(JdbcTemplate jdbcTemplate) {
			return (AiModelMapper) Proxy.newProxyInstance(AiModelMapper.class.getClassLoader(),
					new Class<?>[] { AiModelMapper.class }, (proxy, method, arguments) -> {
						if (method.getDeclaringClass() == Object.class) {
							return method.invoke(this, arguments);
						}
						return switch (method.getName()) {
							case "selectList" -> defaultVoiceModels(jdbcTemplate);
							case "selectById" -> modelById(jdbcTemplate, (Long) arguments[0]);
							case "insert" -> insertWithCompetingDefault(jdbcTemplate, (AiModelEntity) arguments[0]);
							case "updateById" -> updateOrConflict(jdbcTemplate, (AiModelEntity) arguments[0]);
							default -> throw new UnsupportedOperationException(method.getName());
						};
					});
		}

		@Bean
		AiModelService aiModelService(AiModelMapper mapper) {
			AiModelServiceImpl service = new AiModelServiceImpl(mock(ModelProvider.class),
					mock(ModelEndpointPolicy.class));
			ReflectionTestUtils.setField(service, "baseMapper", mapper);
			return service;
		}

		private static List<AiModelEntity> defaultVoiceModels(JdbcTemplate jdbcTemplate) {
			return jdbcTemplate.query(
					"SELECT id, name, model_type, default_model, CAST(ext_data AS CHAR) ext_data FROM ai_model WHERE model_type = 'Voice' AND default_model = '1' AND del_flag = '0' AND tenant_id = 1",
					(resultSet, rowNumber) -> mapModel(resultSet.getLong("id"), resultSet.getString("name"),
							resultSet.getString("model_type"), resultSet.getString("default_model"),
							resultSet.getString("ext_data")));
		}

		private static AiModelEntity modelById(JdbcTemplate jdbcTemplate, Long id) {
			List<AiModelEntity> models = jdbcTemplate.query(
					"SELECT id, name, model_type, default_model, CAST(ext_data AS CHAR) ext_data FROM ai_model WHERE id = ?",
					(resultSet, rowNumber) -> mapModel(resultSet.getLong("id"), resultSet.getString("name"),
							resultSet.getString("model_type"), resultSet.getString("default_model"),
							resultSet.getString("ext_data")),
					id);
			return models.isEmpty() ? null : models.get(0);
		}

		private static int updateOrConflict(JdbcTemplate jdbcTemplate, AiModelEntity model) {
			if (Long.valueOf(2L).equals(model.getId()) && "1".equals(model.getDefaultModel())) {
				insertCompetingDefault(jdbcTemplate);
			}
			return jdbcTemplate.update("UPDATE ai_model SET name = ?, default_model = ? WHERE id = ?", model.getName(),
					model.getDefaultModel(), model.getId());
		}

		private static int insertWithCompetingDefault(JdbcTemplate jdbcTemplate, AiModelEntity model) {
			insertCompetingDefault(jdbcTemplate);
			return jdbcTemplate.update(
					"INSERT INTO ai_model (id, name, model_type, default_model, del_flag, ext_data, tenant_id) VALUES (?, ?, 'Voice', '1', '0', JSON_OBJECT('voiceType', '1'), 1)",
					model.getId(), model.getName());
		}

		private static void insertCompetingDefault(JdbcTemplate jdbcTemplate) {
			jdbcTemplate.update(
					"INSERT INTO ai_model (id, name, model_type, default_model, del_flag, ext_data, tenant_id) VALUES (99, 'competing-asr', 'Voice', '1', '0', JSON_OBJECT('voiceType', '1'), 1)");
		}

		private static AiModelEntity mapModel(long id, String name, String modelType, String defaultModel,
				String extData) {
			AiModelEntity model = new AiModelEntity();
			model.setId(id);
			model.setName(name);
			model.setModelType(modelType);
			model.setDefaultModel(defaultModel);
			model.setExtData(extData);
			return model;
		}

	}

}
