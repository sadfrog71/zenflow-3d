package com.deepdimen.matrix.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryMoveDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentCategoryEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetPermissionMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentCategoryMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.support.handler.source.FileSourceTypeHandler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiDocumentCategoryServiceTest {

	private final AiDatasetMapper datasets = mock(AiDatasetMapper.class);

	private final AiDatasetPermissionMapper permissions = mock(AiDatasetPermissionMapper.class);

	private final AiDocumentCategoryMapper categories = mock(AiDocumentCategoryMapper.class);

	private final AiDocumentMapper documents = mock(AiDocumentMapper.class);

	private final AiDocumentCategoryService service = new AiDocumentCategoryService(datasets, permissions, categories,
			documents);

	@BeforeEach
	void setup() {
		var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "category-test");
		TableInfoHelper.initTableInfo(assistant, AiDocumentCategoryEntity.class);
		TableInfoHelper.initTableInfo(assistant, AiDocumentEntity.class);
		TenantContextHolder.setTenantId(10L);
		AiDatasetEntity dataset = new AiDatasetEntity();
		dataset.setId(20L);
		dataset.setTenantId("10");
		dataset.setVisibility("public");
		when(datasets.selectOne(any())).thenReturn(dataset);
		when(categories.selectList(any())).thenReturn(List.of(node(1, 0, "收费"), node(2, 1, "水价")));
	}

	@AfterEach
	void cleanup() {
		TenantContextHolder.clear();
	}

	@Test
	void invalidBatchDoesNotWriteAnything() {
		when(documents.selectList(any())).thenReturn(List.of(document(101)));
		assertThrows(IllegalArgumentException.class, () -> service.moveDocuments(move(1L, List.of(101L, 102L))));
		verify(documents, never()).update(any(AiDocumentEntity.class), any());
		verify(documents, never()).update(isNull(), any());
	}

	@Test
	void validMoveUsesScopedAtomicUpdateAndAllowsUncategorized() {
		when(documents.selectList(any())).thenReturn(List.of(document(101), document(102)));
		assertTrue(service.moveDocuments(move(null, List.of(101L, 102L))));
		ArgumentCaptor<Wrapper<AiDocumentEntity>> capture = ArgumentCaptor.forClass(Wrapper.class);
		verify(documents).update(isNull(), capture.capture());
		assertTrue(capture.getValue().getSqlSegment().contains("tenant_id"));
		assertTrue(capture.getValue().getSqlSegment().contains("dataset_id"));
		assertTrue(capture.getValue().getSqlSet().contains("category_id"));
		verify(datasets).selectOne(argThat(query -> query.getSqlSegment().contains("FOR UPDATE")));
	}

	@Test
	void crossDatasetCategoryAndMissingTenantAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> service.moveDocuments(move(99L, List.of(101L))));
		verifyNoMoreInteractions(documents);
		TenantContextHolder.clear();
		assertThrows(IllegalArgumentException.class, () -> service.list(20L));
	}

	@Test
	void missingOrForeignDatasetIsRejectedBeforeCategoryQuery() {
		when(datasets.selectOne(any())).thenReturn(null);
		assertThrows(IllegalArgumentException.class, () -> service.list(21L));
		verifyNoMoreInteractions(categories);
	}

	@Test
	void refusesDuplicateSiblingNamesAndNonEmptyDeletion() {
		AiDocumentCategoryDTO create = new AiDocumentCategoryDTO();
		create.setDatasetId(20L);
		create.setName(" 收费 ");
		assertThrows(IllegalArgumentException.class, () -> service.create(create));
		assertThrows(IllegalArgumentException.class, () -> service.delete(20L, 1L));
		when(documents.selectCount(any())).thenReturn(1L);
		assertThrows(IllegalArgumentException.class, () -> service.delete(20L, 2L));
		verify(categories, never()).delete(any(Wrapper.class));
	}

	@Test
	void expandsDescendantsAndClearsInjectedFilterIds() {
		AiDocumentEntity query = new AiDocumentEntity();
		query.setDatasetId(20L);
		query.setCategoryId(1L);
		query.setCategoryIds(List.of(999L));
		service.applyFilter(query);
		assertEquals(List.of(1L, 2L), query.getCategoryIds());
		query.setUncategorized(true);
		assertThrows(IllegalArgumentException.class, () -> service.applyFilter(query));
		query.setCategoryId(null);
		service.applyFilter(query);
		assertNull(query.getCategoryIds());
	}

	@Test
	void preparesUploadSynchronouslyAndRejectsForeignCategoryBeforeSaving() {
		FileSourceTypeHandler handler = mock(FileSourceTypeHandler.class);
		AiDocumentDTO upload = new AiDocumentDTO();
		upload.setDatasetId(20L);
		upload.setCategoryId(99L);
		assertThrows(IllegalArgumentException.class, () -> service.prepareUpload(handler, upload));
		verifyNoMoreInteractions(handler);
		upload.setCategoryId(2L);
		AiDocumentEntity prepared = document(101L);
		when(handler.saveDocument(upload)).thenReturn(prepared);
		service.prepareUpload(handler, upload);
		assertSame(prepared, upload.getPreparedDocument());
		verify(handler).saveDocument(upload);
		verify(handler, never()).handle(any());
	}

	@Test
	void genericUpdateCannotMoveDatasetOrCategory() {
		AiDocumentEntity existing = document(101L);
		existing.setCategoryId(2L);
		when(documents.selectOne(any())).thenReturn(existing);
		AiDocumentEntity request = document(101L);
		request.setDatasetId(21L);
		assertThrows(IllegalArgumentException.class, () -> service.validateDocumentUpdate(request));
		request.setDatasetId(20L);
		request.setCategoryId(1L);
		assertThrows(IllegalArgumentException.class, () -> service.validateDocumentUpdate(request));
		request.setCategoryId(2L);
		service.validateDocumentUpdate(request);
		assertNull(request.getDatasetId());
		assertNull(request.getCategoryId());
	}

	@Test
	void normalMapperUpdatesNeverOverwriteCategory() {
		var field = TableInfoHelper.getTableInfo(AiDocumentEntity.class)
			.getFieldList()
			.stream()
			.filter(info -> info.getProperty().equals("categoryId"))
			.findFirst()
			.orElseThrow();
		assertNull(field.getSqlSet("et."));
	}

	private AiDocumentEntity document(long id) {
		AiDocumentEntity document = new AiDocumentEntity();
		document.setId(id);
		document.setDatasetId(20L);
		return document;
	}

	private AiDocumentCategoryMoveDTO move(Long categoryId, List<Long> ids) {
		AiDocumentCategoryMoveDTO request = new AiDocumentCategoryMoveDTO();
		request.setDatasetId(20L);
		request.setCategoryId(categoryId);
		request.setDocumentIds(ids);
		return request;
	}

	private AiDocumentCategoryEntity node(long id, long parent, String name) {
		AiDocumentCategoryEntity category = new AiDocumentCategoryEntity();
		category.setId(id);
		category.setParentId(parent);
		category.setName(name);
		return category;
	}

}
