package com.deepdimen.matrix.work.controller;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.api.entity.InformationRun;
import com.deepdimen.matrix.work.entity.InformationSkillInvocationEntity;
import com.deepdimen.matrix.work.mapper.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class InformationSkillTraceControllerTest {

	final InformationRunMapper runs = mock(InformationRunMapper.class);

	final InformationSkillInvocationMapper invocations = mock(InformationSkillInvocationMapper.class);

	final InformationSkillTraceController controller = new InformationSkillTraceController(runs, invocations);

	@BeforeEach
	void tenant() {
		TenantContextHolder.setTenantId(7L);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	void run() {
		var row = new InformationRun();
		row.setId(8L);
		row.setPolicyId(9L);
		when(runs.selectOne(any())).thenReturn(row);
	}

	InformationSkillInvocationEntity row(long tenant) {
		var row = new InformationSkillInvocationEntity();
		row.setId(10L);
		row.setTenantId(tenant);
		row.setCollectionRunId(8L);
		row.setPolicyId(9L);
		row.setSlotCode("ANALYZE");
		row.setStatus("PREPARED");
		return row;
	}

	@Test
	void missingRunCannotReadAssociations() {
		assertThrows(IllegalArgumentException.class, () -> controller.read(8L, null));
		verifyZeroInteractions(invocations);
	}

	@Test
	void wrongTenantRowsRejected() {
		run();
		when(invocations.selectList(any())).thenReturn(List.of(row(88L)));
		assertThrows(IllegalArgumentException.class, () -> controller.read(8L, null));
	}

	@Test
	void unfinishedAssociationDoesNotInventSkillExecution() {
		run();
		when(invocations.selectList(any())).thenReturn(List.of(row(7L)));
		var result = controller.read(8L, null).get(0);
		assertNull(result.skillRunId());
		assertEquals("PREPARED", result.resultState());
	}

	@Test
	void invalidCursorRejectedBeforeQuery() {
		assertThrows(IllegalArgumentException.class, () -> controller.read(8L, 0L));
		verifyZeroInteractions(runs, invocations);
	}

}
