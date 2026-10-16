package com.campus.venue.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 统一时间序列化格式：日期 yyyy-MM-dd，日期时间 yyyy-MM-dd HH:mm:ss，时间 HH:mm:ss
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializerByType(java.time.LocalDate.class, new LocalDateSerializer(DATE));
            builder.serializerByType(java.time.LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME));
            builder.serializerByType(java.time.LocalTime.class, new LocalTimeSerializer(TIME));
            builder.deserializerByType(java.time.LocalDate.class, new LocalDateDeserializer(DATE));
            builder.deserializerByType(java.time.LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME));
            builder.deserializerByType(java.time.LocalTime.class, new LocalTimeDeserializer(TIME));
        };
    }
}
