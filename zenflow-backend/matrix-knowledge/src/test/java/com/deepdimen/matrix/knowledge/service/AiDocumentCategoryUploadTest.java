package com.deepdimen.matrix.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.support.handler.source.AbstractFileSourceTypeHandler;
import com.deepdimen.matrix.knowledge.support.handler.source.ExcelQaSourceTypeHandler;
import com.deepdimen.matrix.knowledge.support.handler.source.InputTextSourceTypeHandler;
import com.deepdimen.matrix.knowledge.support.handler.source.UploadSourceTypeHandler;

class AiDocumentCategoryUploadTest {

	@Test
	void allSourcesPersistSelectedCategoryInInitialInsert() {
		AiDocumentMapper documents = mock(AiDocumentMapper.class);
		AiDocumentDTO request = new AiDocumentDTO();
		request.setDatasetId(20L);
		request.setCategoryId(2L);
		AiDocumentEntity file = new AiDocumentEntity();
		file.setName("政策");
		file.setFileUrl("policies.docx");
		request.setFiles(List.of(file));
		assertEquals(2L, new UploadSourceTypeHandler(null, List.of(), null, documents, null).saveDocument(request)
			.getCategoryId());
		assertEquals(2L,
				new InputTextSourceTypeHandler(documents, null, null, null).saveDocument(request).getCategoryId());
		assertEquals(2L, new ExcelQaSourceTypeHandler(null, documents, null).saveDocument(request).getCategoryId());
	}

	@Test
	void preparedDocumentIsNotSavedAgainByAsyncPipeline() {
		AiDocumentDTO request = new AiDocumentDTO();
		AiDocumentEntity prepared = new AiDocumentEntity();
		prepared.setId(123L);
		prepared.setCategoryId(2L);
		request.setPreparedDocument(prepared);
		AbstractFileSourceTypeHandler handler = new AbstractFileSourceTypeHandler() {
			@Override
			public boolean supports(String source) {
				return true;
			}

			@Override
			public AiDocumentEntity saveDocument(AiDocumentDTO dto) {
				throw new AssertionError("重复保存");
			}

			@Override
			public void saveSlice(AiDocumentEntity document, AiDocumentDTO dto) {
				assertSame(prepared, document);
			}

			@Override
			public void summaryDocument(AiDocumentEntity document, AiDocumentDTO dto) {
			}

			@Override
			public void tagsDocument(AiDocumentEntity document, AiDocumentDTO dto) {
			}

			@Override
			public void embedSlice(AiDocumentEntity document) {
			}
		};
		handler.handle(request);
	}

	@Test
	void preparedDocumentCannotBeInjectedThroughJson() throws Exception {
		AiDocumentDTO dto = new ObjectMapper()
			.readValue("{\"datasetId\":20,\"categoryId\":2,\"preparedDocument\":{\"id\":999}}", AiDocumentDTO.class);
		assertNull(dto.getPreparedDocument());
		assertEquals(2L, dto.getCategoryId());
	}

}
