package com.deepdimen.matrix.zenflow.books;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class ZenflowBookReadingLayersTest {

	private JdbcTemplate jdbc;
	private ZenflowBookReader reader;

	@BeforeEach
	void setup() {
		var data = new JdbcDataSource();
		data.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		jdbc = new JdbcTemplate(data);
		jdbc.execute("CREATE TABLE zf_book(id BIGINT,tenant_id BIGINT,title VARCHAR,author VARCHAR,publisher VARCHAR,source_language VARCHAR,source_version_label VARCHAR,category VARCHAR,summary VARCHAR,source_url VARCHAR,license_name VARCHAR,rights_holder VARCHAR,license_term VARCHAR,license_expires_on DATE,state VARCHAR,authorization_verified INT,permission_display INT)");
		jdbc.execute("CREATE TABLE zf_book_translation(id BIGINT,book_id BIGINT,tenant_id BIGINT,target_language VARCHAR,revision_no INT,is_excerpt INT,excerpt_label VARCHAR,source_version_id BIGINT,rights_snapshot VARCHAR,translation_origin VARCHAR,translation_model_note VARCHAR,translator_note VARCHAR,status VARCHAR)");
		jdbc.execute("CREATE TABLE zf_book_source_version(id BIGINT,tenant_id BIGINT,source_reviewed INT,version_no INT)");
		jdbc.execute("CREATE TABLE zf_book_source_chapter(id BIGINT,tenant_id BIGINT,chapter_no INT,title VARCHAR,source_text VARCHAR)");
		jdbc.execute("CREATE TABLE zf_book_translation_chapter(tenant_id BIGINT,translation_id BIGINT,source_chapter_id BIGINT,chapter_no INT,included INT,translation_state VARCHAR,review_state VARCHAR,translation_text VARCHAR,commentary VARCHAR,annotations VARCHAR)");
		jdbc.update("INSERT INTO zf_book VALUES(1,1,'心经','玄奘','Invelle','zh-CN','汉文底本','classics','','','Public domain','','',NULL,'PUBLISHED',1,1)");
		jdbc.update("INSERT INTO zf_book_source_version VALUES(2,1,1,1)");
		jdbc.update("INSERT INTO zf_book_source_chapter VALUES(3,1,1,'全经','照見五蘊皆空。')");
		jdbc.update("INSERT INTO zf_book_translation VALUES(4,1,1,'zh-CN',1,0,'',2,'','LOCAL_UPLOAD','Codex','此间译注','PUBLISHED')");
		jdbc.update("INSERT INTO zf_book_translation_chapter VALUES(1,4,3,1,1,'COMPLETE','APPROVED','观照到五蕴没有独立不变的自性。','本章从身心经验说明空性。','[{\"phrase\":\"五蘊\",\"note\":\"五类身心活动\",\"tone\":\"word\"}]')");
		var product = new ZenflowProperties();
		product.setTenantId(1);
		reader = new ZenflowBookReader(jdbc, product, mock(ZenflowConsumerAccess.class), new DataSourceTransactionManager(data), new ObjectMapper());
	}

	@Test
	void classicalChineseKeepsOriginalVernacularAndNotes() {
		Map<String, Object> result = reader.publishedVersion("1", "4", "zh-CN");
		Map<?, ?> chapter = (Map<?, ?>) ((List<?>) result.get("chapters")).get(0);
		assertEquals("source", chapter.get("annotationLayer"));
		assertEquals("照見五蘊皆空。", chapter.get("text"));
		assertEquals("观照到五蕴没有独立不变的自性。", chapter.get("translationText"));
		assertEquals("本章从身心经验说明空性。", chapter.get("commentary"));
		assertEquals(1, ((List<?>) chapter.get("annotations")).size());
	}

	@Test
	void translatedEditionKeepsItsOwnLanguageNotes() {
		jdbc.update("UPDATE zf_book_translation SET target_language='en-US'");
		jdbc.update("UPDATE zf_book_translation_chapter SET translation_text='He saw the five aggregates as empty.',commentary='This chapter examines experience.',annotations='[{\"phrase\":\"five aggregates\",\"note\":\"Five groups of experience\"}]'");
		Map<String, Object> result = reader.publishedVersion("1", "4", "en-US");
		Map<?, ?> chapter = (Map<?, ?>) ((List<?>) result.get("chapters")).get(0);
		assertEquals("translation", chapter.get("annotationLayer"));
		assertEquals("He saw the five aggregates as empty.", chapter.get("text"));
		assertEquals("照見五蘊皆空。", chapter.get("sourceText"));
		assertEquals("This chapter examines experience.", chapter.get("commentary"));
	}

	@Test
	void aSingleChapterResponseKeepsTheTableOfContentsWithoutOtherChapterBodies() {
		jdbc.update("INSERT INTO zf_book_source_chapter VALUES(5,1,2,'第二章','不可提前下载的第二章正文')");
		jdbc.update("INSERT INTO zf_book_translation_chapter VALUES(1,4,5,2,1,'COMPLETE','APPROVED','第二章白话','第二章解读','[]')");
		Map<String, Object> result = reader.publishedVersion("1", "4", "zh-CN", 1);
		assertEquals(1, ((List<?>) result.get("chapters")).size());
		assertEquals(2, ((List<?>) result.get("chapterSummaries")).size());
		assertFalse(result.toString().contains("不可提前下载的第二章正文"));
		assertFalse(result.toString().contains("第二章解读"));
		Map<String, Object> second = reader.publishedVersion("1", "4", "zh-CN", 2);
		assertEquals("不可提前下载的第二章正文", ((Map<?, ?>) ((List<?>) second.get("chapters")).get(0)).get("sourceText"));
		assertEquals(2, ((List<?>) reader.publishedVersion("1", "4", "zh-CN").get("chapters")).size());
		assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> reader.publishedVersion("1", "4", "zh-CN", 99));
	}
}
