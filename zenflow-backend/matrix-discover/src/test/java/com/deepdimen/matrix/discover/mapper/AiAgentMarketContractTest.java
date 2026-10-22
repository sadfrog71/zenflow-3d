package com.deepdimen.matrix.discover.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import com.deepdimen.matrix.discover.vo.AiAgentMarketVO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

class AiAgentMarketContractTest {

	@Test
	void marketViewExposesDocumentedDifyMode() throws NoSuchFieldException {
		Field difyMode = AiAgentMarketVO.class.getDeclaredField("difyMode");

		assertEquals(String.class, difyMode.getType());
		Schema schema = difyMode.getAnnotation(Schema.class);
		assertNotNull(schema);
		assertTrue(schema.description().contains("Dify应用模式"));
	}

	@Test
	void marketQuerySelectsDifyMode() throws IOException {
		try (InputStream input = getClass().getResourceAsStream("/mapper/AiAgentMapper.xml")) {
			assertNotNull(input);
			String mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			int marketQueryStart = mapperXml.indexOf("<select id=\"selectMarketPage\"");
			int marketQueryEnd = mapperXml.indexOf("</select>", marketQueryStart);

			assertTrue(marketQueryStart >= 0 && marketQueryEnd > marketQueryStart);
			String marketQuery = mapperXml.substring(marketQueryStart, marketQueryEnd);
			assertTrue(marketQuery.contains("a.dify_mode AS difyMode"));
		}
	}

}
