package com.deepdimen.matrix.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.controller.AiDocumentController;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentCategoryEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetPermissionMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentCategoryMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiDocumentCategoryExportTest {

	private final AiDocumentService documents = mock(AiDocumentService.class);

	private final AiDatasetMapper datasets = mock(AiDatasetMapper.class);

	private final AiDocumentCategoryMapper categories = mock(AiDocumentCategoryMapper.class);

	private final AiDocumentCategoryService categoryService = new AiDocumentCategoryService(datasets,
			mock(AiDatasetPermissionMapper.class), categories, mock(AiDocumentMapper.class));

	private final AiDocumentController controller = new AiDocumentController(List.of(), null, documents, null,
			categoryService);

	@BeforeEach
	void setup() {
		var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "category-export-test");
		TableInfoHelper.initTableInfo(assistant, AiDocumentEntity.class);
		TableInfoHelper.initTableInfo(assistant, AiDocumentCategoryEntity.class);
		TenantContextHolder.setTenantId(10L);
		AiDatasetEntity dataset = new AiDatasetEntity();
		dataset.setId(20L);
		dataset.setVisibility("public");
		when(datasets.selectOne(any())).thenReturn(dataset);
		when(categories.selectList(any())).thenReturn(List.of(node(1L, 0L), node(2L, 1L)));
	}

	@AfterEach
	void cleanup() {
		TenantContextHolder.clear();
	}

	@Test
	void parentExportUsesDescendantsAndSelectedDocumentIntersection() {
		AiDocumentEntity query = query();
		query.setCategoryId(1L);
		controller.export(query, new Long[] { 101L });
		Wrapper<AiDocumentEntity> wrapper = capturedQuery();
		assertEquals(List.of(1L, 2L), query.getCategoryIds());
		assertNull(wrapper.getEntity().getCategoryId(), "不能残留父分类等值条件");
		assertEquals(20L, wrapper.getEntity().getDatasetId());
		assertTrue(wrapper.getSqlSegment().contains("category_id IN"));
		assertTrue(wrapper.getSqlSegment().contains("id IN"));
	}

	@Test
	void uncategorizedExportUsesIsNull() {
		AiDocumentEntity query = query();
		query.setUncategorized(true);
		controller.export(query, null);
		assertTrue(capturedQuery().getSqlSegment().contains("category_id IS NULL"));
	}

	@Test
	void foreignCategoryCannotReachExportQuery() {
		AiDocumentEntity query = query();
		query.setCategoryId(999L);
		assertThrows(IllegalArgumentException.class, () -> controller.export(query, null));
		verifyNoMoreInteractions(documents);
	}

	private Wrapper<AiDocumentEntity> capturedQuery() {
		ArgumentCaptor<Wrapper<AiDocumentEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(documents).list(captor.capture());
		return captor.getValue();
	}

	private AiDocumentEntity query() {
		AiDocumentEntity query = new AiDocumentEntity();
		query.setDatasetId(20L);
		return query;
	}

	private AiDocumentCategoryEntity node(Long id, Long parentId) {
		AiDocumentCategoryEntity node = new AiDocumentCategoryEntity();
		node.setId(id);
		node.setParentId(parentId);
		return node;
	}

}
