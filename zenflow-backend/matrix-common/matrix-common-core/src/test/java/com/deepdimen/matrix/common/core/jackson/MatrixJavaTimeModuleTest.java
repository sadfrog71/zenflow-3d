package com.deepdimen.matrix.common.core.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.deepdimen.matrix.common.core.config.JacksonConfiguration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class MatrixJavaTimeModuleTest {

	private ObjectMapper mapper() {
		return new ObjectMapper().registerModule(new MatrixJavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
	}

	@Test
	void serializesOffsetDateTimeUsingTheProductionIsoFormat() throws Exception {
		assertEquals("\"2026-09-24T15:00:01+08:00\"",
				mapper().writeValueAsString(OffsetDateTime.parse("2026-09-24T15:00:01+08:00")));
	}

	@Test
	void deserializesOffsetDateTimeWithoutDroppingItsInstant() throws Exception {
		for (String value : new String[] { "2026-09-24T15:00:01+08:00", "2026-09-24T07:00:01Z",
				"2026-09-24T02:00:01-05:00" }) {
			assertEquals(OffsetDateTime.parse(value), mapper().readValue("\"" + value + "\"", OffsetDateTime.class));
		}
	}

	@Test
	void preservesExistingLocalDateAndTimeFormats() throws Exception {
		var mapper = mapper();
		assertEquals("\"2026-09-24 15:00:01\"", mapper.writeValueAsString(LocalDateTime.of(2026, 9, 24, 15, 0, 1)));
		assertEquals("\"2026-09-24\"", mapper.writeValueAsString(LocalDate.of(2026, 9, 24)));
		assertEquals("\"15:00:01\"", mapper.writeValueAsString(LocalTime.of(15, 0, 1)));
	}

	@Test
	void productionJacksonCustomizerSupportsOffsetDatesAndKeepsLongIdsAsStrings() throws Exception {
		var builder = new Jackson2ObjectMapperBuilder();
		new JacksonConfiguration().customizer().customize(builder);
		ObjectMapper mapper = builder.build();
		var expected = OffsetDateTime.parse("2026-09-24T15:00:01+08:00");
		assertEquals(expected.toInstant(),
				mapper.readValue(mapper.writeValueAsString(expected), OffsetDateTime.class).toInstant());
		assertEquals("\"9007199254740993\"", mapper.writeValueAsString(Long.valueOf(9007199254740993L)));
	}

}
