package com.policyradar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson 全局配置
 *
 * 配置支持 Java 8 日期时间类型（LocalDate、LocalDateTime 等）的序列化和反序列化
 */
@Configuration
public class JacksonConfig {

    /**
     * 配置全局 ObjectMapper，注册 JavaTimeModule 以支持 Java 8 日期时间类型
     *
     * @param builder Spring 提供的 Jackson2ObjectMapperBuilder
     * @return 配置好的 ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();

        // 注册 JavaTimeModule 以支持 Java 8 日期时间类型
        objectMapper.registerModule(new JavaTimeModule());

        // 禁用将日期写为时间戳的功能，使用 ISO-8601 格式
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return objectMapper;
    }
}