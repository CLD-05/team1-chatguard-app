package com.chatguard.global.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

@Configuration
public class JacksonConfig {

    // A-2 시간 규약: 모든 시각은 UTC 저장·전송이며 API는 ISO-8601 Z 표기다(예: 2026-06-04T12:00:00Z).
    // LocalDateTime(=UTC 저장값)은 기본 직렬화 시 Z가 빠져 프론트가 로컬 타임존으로 오해석하므로,
    // 전역 직렬화기로 Z를 항상 붙인다. REST와 WS(주입된 동일 ObjectMapper)에 공통 적용된다.
    private static final DateTimeFormatter UTC_ISO_8601 =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcIso8601Customizer() {
        return builder -> builder.serializers(new LocalDateTimeSerializer(UTC_ISO_8601));
    }
}
