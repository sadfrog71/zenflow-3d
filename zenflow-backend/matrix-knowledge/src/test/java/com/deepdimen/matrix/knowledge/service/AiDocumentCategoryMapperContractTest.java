package com.deepdimen.matrix.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;

class AiDocumentCategoryMapperContractTest {

	@Test
	void documentSqlIncludesCategoryProjectionAndDescendantFilter() throws Exception {
		AiDocumentEntity query = new AiDocumentEntity();
		query.setDatasetId(20L);
		query.setCategoryIds(List.of(1L, 2L, 3L));
		String sql = sql(query);
		assertTrue(sql.contains("d.category_id,"));
		assertTrue(sql.contains("d.category_id IN"));
		assertTrue(sql.contains("d.dataset_id = ?"));
		assertFalse(sql.contains("d.category_id IS NULL"));
	}

	@Test
	void uncategorizedIsNullWhileAllDocumentsDoesNotFilterCategories() throws Exception {
		AiDocumentEntity query = new AiDocumentEntity();
		query.setDatasetId(20L);
		query.setUncategorized(true);
		assertTrue(sql(query).contains("d.category_id IS NULL"));
		query.setUncategorized(false);
		assertFalse(sql(query).contains("d.category_id IS NULL"));
		assertFalse(sql(query).contains("d.category_id IN"));
	}

	private String sql(AiDocumentEntity query) throws Exception {
		Configuration configuration = new Configuration();
		try (InputStream input = getClass().getResourceAsStream("/mapper/AiDocumentMapper.xml")) {
			assertNotNull(input);
			new XMLMapperBuilder(input, configuration, "mapper/AiDocumentMapper.xml", configuration.getSqlFragments())
				.parse();
		}
		return configuration
			.getMappedStatement("com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper.getDocumentPage")
			.getBoundSql(Map.of("query", query))
			.getSql();
	}

}
