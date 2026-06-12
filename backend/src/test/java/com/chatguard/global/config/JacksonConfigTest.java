package com.chatguard.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

class JacksonConfigTest {

    @Test
    void LocalDateTime은_ISO8601_Z표기로_직렬화된다() throws Exception {
        // P1-2: created_at(UTC LocalDateTime)은 항상 Z 표기로 직렬화되어야 한다(REST·WS 공통).
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().utcIso8601Customizer().customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(
            Map.of("created_at", LocalDateTime.of(2026, 6, 4, 12, 0, 0)));

        assertThat(json).contains("\"created_at\":\"2026-06-04T12:00:00Z\"");
    }
}
