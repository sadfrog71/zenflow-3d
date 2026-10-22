package com.deepdimen.matrix.knowledge.service.impl;

import java.util.*;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.knowledge.dto.AiTerminologyDTO;
import com.deepdimen.matrix.knowledge.entity.*;
import com.deepdimen.matrix.knowledge.mapper.AiTerminologyMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiTerminologyDatasetBindingsTest {

	JdbcTemplate jdbc;

	AiTerminologyDatasetBindings bindings;

	TransactionTemplate tx;

	DataSourceTransactionManager manager;

	@BeforeEach
	void setup() {
		var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
				"sa", "");
		jdbc = new JdbcTemplate(source);
		manager = new DataSourceTransactionManager(source);
		tx = new TransactionTemplate(manager);
		jdbc.execute(
				"CREATE TABLE ai_data(data_id BIGINT PRIMARY KEY,tenant_id BIGINT,dataset_name VARCHAR(100),terminology_ids VARCHAR(1000),del_flag CHAR(1),update_by VARCHAR(100),update_time TIMESTAMP)");
		jdbc.execute(
				"CREATE TABLE ai_terminology(term_id BIGINT PRIMARY KEY,tenant_id BIGINT,pid BIGINT,word VARCHAR(100),description VARCHAR(500),del_flag CHAR(1))");
		jdbc.update(
				"INSERT INTO ai_data VALUES(11,1,'巡视','5,9','0',NULL,NULL),(12,1,'标准','9','0',NULL,NULL),(13,1,'已删除','5','1',NULL,NULL),(21,2,'其他租户','5','0',NULL,NULL)");
		jdbc.update(
				"INSERT INTO ai_terminology VALUES(5,1,NULL,'巡视','按业务定义','0'),(9,1,NULL,'部门','业务部门','0'),(10,1,5,'巡查',NULL,'0'),(22,2,NULL,'另一租户',NULL,'0')");
		SecurityContextHolder.getContext()
			.setAuthentication(
					new TestingAuthenticationToken("editor", "", "knowledge_aiData_view", "knowledge_aiData_edit"));
		TenantContextHolder.setTenantId(1L);
		bindings = new AiTerminologyDatasetBindings(jdbc, new PermissionService());
	}

	@AfterEach
	void cleanup() {
		jdbc.execute("DROP ALL OBJECTS");
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
	}

	String value(long id) {
		return jdbc.queryForObject("SELECT terminology_ids FROM ai_data WHERE data_id=?", String.class, id);
	}

	@Test
	void optionsAndBindingChangesStayWithinTenantAndPreserveOtherTerms() {
		assertThat(bindings.options()).extracting(AiTerminologyDatasetBindings.Dataset::dataId)
			.containsExactly("11", "12");
		assertThat(bindings.selected(5L)).containsExactly(11L);
		tx.executeWithoutResult(s -> bindings.replace(5L, List.of(12L), List.of(11L)));
		assertThat(value(11)).isEqualTo("9");
		assertThat(value(12)).isEqualTo("9,5");
		assertThat(value(13)).isEqualTo("5");
		assertThat(value(21)).isEqualTo("5");
		assertThat(bindings.termsForDataset(12L)).containsExactly(9L, 5L);
	}

	@Test
	void foreignDeletedDuplicateAndStaleSelectionsDoNotOverwriteAnything() {
		for (var requested : List.of(List.of(21L), List.of(13L), List.of(11L, 11L))) {
			assertThatThrownBy(() -> tx.executeWithoutResult(s -> bindings.replace(5L, requested, List.of(11L))))
				.isInstanceOf(ResponseStatusException.class);
			assertThat(value(11)).isEqualTo("5,9");
		}
		bindings.replace(5L, List.of(12L), List.of(11L));
		assertThatThrownBy(() -> bindings.replace(5L, List.of(), List.of(11L))).hasMessageContaining("409");
		assertThat(bindings.selected(5L)).containsExactly(12L);
	}

	@Test
	void oldClientsPreserveBindingsAndEmptyListExplicitlyRemovesThem() {
		bindings.replace(5L, null, null);
		assertThat(value(11)).isEqualTo("5,9");
		bindings.replace(5L, List.of(), List.of(11L));
		assertThat(value(11)).isEqualTo("9");
	}

	@Test
	void absentIdentityAndMissingEditPermissionCannotChangeBindings() {
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> bindings.replace(5L, List.of(), List.of(11L)))
			.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("reader", "", "knowledge_aiData_view"));
		assertThat(bindings.options()).hasSize(2);
		assertThatThrownBy(() -> bindings.replace(5L, List.of(), List.of(11L)))
			.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
		assertThat(value(11)).isEqualTo("5,9");
	}

	@Test
	void datasetEditorRejectsStaleBindingsAndForeignOrChildTerms() {
		var data = new AiDataEntity();
		data.setDataId(11L);
		data.setTerminologyIds(new Long[] { 9L });
		data.setExpectedTerminologyIds(List.of(5L, 9L));
		tx.executeWithoutResult(s -> bindings.checkDatasetUpdate(data));
		bindings.replace(5L, List.of(), List.of(11L));
		assertThatThrownBy(() -> tx.executeWithoutResult(s -> bindings.checkDatasetUpdate(data)))
			.hasMessageContaining("409");
		data.setExpectedTerminologyIds(List.of(9L));
		for (Long id : List.of(22L, 10L, 999L)) {
			data.setTerminologyIds(new Long[] { id });
			assertThatThrownBy(() -> bindings.checkDatasetUpdate(data)).hasMessageContaining("400");
		}
	}

	@Test
	void legacyDatasetEditorCannotBypassSnapshotWhenChangingBindings() {
		var data = new AiDataEntity();
		data.setDataId(11L);
		data.setTerminologyIds(new Long[] { 5L, 9L });
		bindings.checkDatasetUpdate(data);
		data.setTerminologyIds(new Long[] {});
		assertThatThrownBy(() -> bindings.checkDatasetUpdate(data)).hasMessageContaining("409");
		assertThat(value(11)).isEqualTo("5,9");
	}

	@Test
	void savingNewTermAndSynonymsRollsBackWhenBindingIsInvalid() {
		var mapper = mock(AiTerminologyMapper.class);
		doAnswer(call -> {
			AiTerminologyEntity e = call.getArgument(0);
			if (e.getTermId() == null)
				e.setTermId(e.getPid() == null ? 100L : 101L);
			jdbc.update("INSERT INTO ai_terminology VALUES(?,?,?,?,?,'0')", e.getTermId(), 1, e.getPid(), e.getWord(),
					e.getDescription());
			return 1;
		}).when(mapper).insert(any(AiTerminologyEntity.class));
		var service = new AiTerminologyServiceImpl(bindings);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		var factory = new ProxyFactory(service);
		factory.setProxyTargetClass(true);
		factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
		var proxied = (AiTerminologyServiceImpl) factory.getProxy();
		var input = new AiTerminologyDTO();
		input.setWord("设备");
		input.setDescription("设备定义");
		input.setSynonymsList(List.of("装置"));
		input.setDatasetIds(List.of(21L));
		assertThatThrownBy(() -> proxied.saveAiTerminology(input)).isInstanceOf(ResponseStatusException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_terminology WHERE term_id IN (100,101)", Integer.class))
			.isZero();
		assertThat(value(11)).isEqualTo("5,9");
	}

	@Test
	void successfulRootUpdateHasReceiptAndDoesNotAcceptTenantOrParentFromClient() {
		var mapper = mock(AiTerminologyMapper.class);
		var root = new AiTerminologyEntity();
		root.setTermId(5L);
		root.setWord("巡视");
		root.setTenantId(1L);
		when(mapper.selectById(5L)).thenReturn(root);
		when(mapper.updateById(any(AiTerminologyEntity.class))).thenReturn(1);
		var service = new AiTerminologyServiceImpl(bindings);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		var input = new AiTerminologyDTO();
		input.setTermId(5L);
		input.setWord("巡视");
		input.setTenantId(2L);
		input.setPid(22L);
		assertThat(service.updateAiTerminology(input)).isNotNull();
		var captured = org.mockito.ArgumentCaptor.forClass(AiTerminologyEntity.class);
		verify(mapper).updateById(captured.capture());
		assertThat(captured.getValue().getTenantId()).isEqualTo(1L);
		assertThat(captured.getValue().getPid()).isNull();
	}

}
