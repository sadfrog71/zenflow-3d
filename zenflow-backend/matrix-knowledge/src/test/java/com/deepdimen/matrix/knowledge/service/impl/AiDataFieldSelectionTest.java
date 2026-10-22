package com.deepdimen.matrix.knowledge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.deepdimen.matrix.common.data.tenant.*;
import com.deepdimen.matrix.knowledge.controller.AiDataFieldController;
import com.deepdimen.matrix.knowledge.entity.AiDataFieldEntity;
import com.deepdimen.matrix.knowledge.mapper.*;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import org.apache.ibatis.session.SqlSession;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiDataFieldSelectionTest {

	private SqlSession session;

	private AiDataFieldServiceImpl service;

	private AiDataFieldMapper fields;

	private AiDatasourceService datasource;

	private ModelProvider model;

	private AiDataService data;

	@BeforeEach
	void setup() throws Exception {
		var source = new DriverManagerDataSource();
		source.setUrl("jdbc:h2:mem:field_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
		var jdbc = new JdbcTemplate(source);
		jdbc.execute("""
				CREATE TABLE ai_data_table (table_id BIGINT PRIMARY KEY, ds_name VARCHAR(128), ds_type VARCHAR(32),
				table_name VARCHAR(128), table_comment VARCHAR(255), virtual_comment VARCHAR(255),
				create_by VARCHAR(64), create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP,
				del_flag VARCHAR(1), tenant_id BIGINT)
				""");
		jdbc.update(
				"INSERT INTO ai_data_table(table_id,ds_name,table_name,virtual_comment,del_flag,tenant_id) VALUES(10,'source','patrol','saved table note','0',1),(20,'source','foreign_only','other tenant','0',2),(30,'source','deleted','deleted','1',1)");
		var config = new MybatisConfiguration();
		config.addMapper(AiDataTableMapper.class);
		var plugin = new MybatisPlusInterceptor();
		plugin.addInnerInterceptor(
				new TenantLineInnerInterceptor(new MatrixTenantHandler(new MatrixTenantConfigProperties())));
		var factory = new MybatisSqlSessionFactoryBean();
		factory.setDataSource(source);
		factory.setConfiguration(config);
		factory.setPlugins(plugin);
		session = factory.getObject().openSession(true);
		datasource = mock(AiDatasourceService.class);
		model = mock(ModelProvider.class);
		data = mock(AiDataService.class);
		fields = mock(AiDataFieldMapper.class);
		service = spy(new AiDataFieldServiceImpl(datasource, session.getMapper(AiDataTableMapper.class), model, data,
				fields));
		ReflectionTestUtils.setField(service, "baseMapper", fields);
		TenantContextHolder.setTenantId(1L);
	}

	@AfterEach
	void close() {
		if (session != null)
			session.close();
		TenantContextHolder.clear();
	}

	@Test
	void invalidNamesDenyEveryOperationWithoutSourceOrModel() {
		for (String name : new String[] { null, "", " ", "undefined", "null", "a,b", "a/b", "a\\b", "a?b", "x\n" }) {
			assertThatThrownBy(() -> service.getOrSync("source", name)).isInstanceOf(ResponseStatusException.class);
			assertThatThrownBy(() -> service.syncTableField(name, "patrol"))
				.isInstanceOf(ResponseStatusException.class);
			assertThatThrownBy(() -> service.assessTableField("source", name))
				.isInstanceOf(ResponseStatusException.class);
		}
		verifyZeroInteractions(fields, datasource, model, data);
	}

	@Test
	void missingAndSoftDeletedMetadataNeverSynchronize() {
		for (String table : List.of("missing", "deleted")) {
			assertThatThrownBy(() -> service.getOrSync("source", table)).isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404");
			assertThatThrownBy(() -> service.syncTableField("source", table))
				.isInstanceOf(ResponseStatusException.class);
			assertThatThrownBy(() -> service.assessTableField("source", table))
				.isInstanceOf(ResponseStatusException.class);
		}
		verifyZeroInteractions(fields, datasource, model, data);
	}

	@Test
	void realMapperAndExistingTenantInterceptorCannotFindOtherTenantTable() {
		assertThatThrownBy(() -> service.getOrSync("source", "foreign_only"))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("404");
		verifyZeroInteractions(fields, datasource, model, data);
		TenantContextHolder.setTenantId(2L);
		service.validateTableSelection("source", "foreign_only");
		assertThatThrownBy(() -> service.getOrSync("source", "patrol")).hasMessageContaining("404");
	}

	@Test
	void missingTenantAndBypassContextFailClosed() {
		TenantContextHolder.clear();
		assertThatThrownBy(() -> service.getOrSync("source", "patrol")).hasMessageContaining("403");
		TenantContextHolder.setTenantId(1L);
		TenantContextHolder.setTenantSkip();
		assertThatThrownBy(() -> service.getOrSync("source", "patrol")).hasMessageContaining("403");
		verifyZeroInteractions(fields, datasource, model, data);
	}

	@Test
	void existingFieldsAndNotesAreReturnedWithoutAnySynchronizationOrWrite() {
		var field = new AiDataFieldEntity();
		field.setFieldId(100L);
		field.setTableId(10L);
		field.setDsName("source");
		field.setVirtualComment("saved field note");
		when(fields.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(field));
		assertThat(service.getOrSync("source", "patrol")).containsExactly(field);
		assertThat(field.getVirtualComment()).isEqualTo("saved field note");
		verify(service, never()).syncTableField(anyString(), anyString());
		verify(fields).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
		verifyNoMoreInteractions(fields);
		verifyZeroInteractions(datasource, model, data);
	}

	@Test
	void exactSourceAndNameRequired() {
		assertThatThrownBy(() -> service.getOrSync("other-source", "patrol")).hasMessageContaining("404");
		assertThatThrownBy(() -> service.getOrSync("source", "PATROL")).hasMessageContaining("404");
		verifyZeroInteractions(fields, datasource, model, data);
	}

	@Test
	void originalUndefinedRequestIs400AndMissingRequest404Not500() throws Exception {
		var mvc = MockMvcBuilders.standaloneSetup(new AiDataFieldController(service)).build();
		mvc.perform(get("/aiDataField/table/undefined/undefined"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(1));
		mvc.perform(get("/aiDataField/table/source/missing")).andExpect(status().isNotFound());
		mvc.perform(post("/aiDataField/sync/source/missing")).andExpect(status().isNotFound());
		mvc.perform(post("/aiDataField/assess/source/missing")).andExpect(status().isNotFound());
		verify(service, never()).assessTableField(anyString(), anyString());
		verifyZeroInteractions(fields, datasource, model, data);
	}

}
