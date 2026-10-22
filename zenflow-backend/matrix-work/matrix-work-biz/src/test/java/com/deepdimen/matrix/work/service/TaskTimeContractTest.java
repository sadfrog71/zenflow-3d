package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.common.core.config.JacksonConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskTimeContractTest {
    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfiguration().customizer().customize(builder);
        return builder.build();
    }

    @Test
    void writesTaskOffsetInThePlatformMapper() throws Exception {
        var mapper = mapper();
        var time = OffsetDateTime.parse("2026-09-16T00:30:00+08:00");
        var result = mapper.readTree(mapper.writeValueAsString(Map.of("asOf", time)));
        assertEquals(time.toInstant(), OffsetDateTime.parse(result.get("asOf").asText()).toInstant());
        assertEquals("+08:00", OffsetDateTime.parse(result.get("asOf").asText()).getOffset().toString());
    }

    @Test
    void readsOffsetWithoutChangingTheDeadlineInstant() throws Exception {
        var input = OffsetDateTime.parse("2026-09-15T23:30:00-05:00");
        var result = mapper().readValue("\"" + input + "\"", OffsetDateTime.class);
        assertEquals(input.toInstant(), result.toInstant());
    }

    @Test
    void preservesExistingLocalDateTimeFormat() throws Exception {
        assertEquals("\"2026-09-16 00:30:00\"", mapper().writeValueAsString(LocalDateTime.of(2026, 9, 16, 0, 30)));
    }
}
