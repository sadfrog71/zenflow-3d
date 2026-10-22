package com.deepdimen.matrix.work.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.work.mapper.WorkAiDatasourceReferenceMapper;

class WorkAiDatasourceReferenceReaderTest {

	private final WorkAiDatasourceReferenceMapper mapper = mock(WorkAiDatasourceReferenceMapper.class);

	private final WorkAiDatasourceReferenceReader reader = new WorkAiDatasourceReferenceReader(mapper);

	@Test
	void acceptsOnlyDatasourceReferencesVisibleInsideTheCurrentTenant() {
		when(mapper.countVisible(2052188107445968897L, 88L)).thenReturn(1);

		assertTrue(reader.exists(2052188107445968897L, 88L));
		assertFalse(reader.exists(2052188107445968897L, 89L));
		verify(mapper).countVisible(2052188107445968897L, 88L);
		verify(mapper).countVisible(2052188107445968897L, 89L);
	}

	@Test
	void rejectsIncompleteReferencesWithoutQueryingAcrossTenants() {
		assertFalse(reader.exists(null, 88L));
		assertFalse(reader.exists(2052188107445968897L, null));
	}

}
