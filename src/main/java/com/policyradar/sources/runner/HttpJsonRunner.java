package com.policyradar.sources.runner;

import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.SourceRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;

/**
 * HTTP JSON API 源执行器
 *
 * 使用 RestTemplate + JSONPath 解析 API 响应
 * 支持自定义字段映射，通过 config 配置 items_path 和 field_map
 */
@Slf4j
@Component
public class HttpJsonRunner implements SourceRunner {

    private final RestTemplate restTemplate;

    public HttpJsonRunner() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String type() {
        return "HTTP_JSON";
    }

    @Override
    public List<RawDoc> fetch(PolicyDataSource dataSource, FetchContext context) {
        JsonApiConfig config = parseConfig(dataSource.getConfig());

        try {
            // 发送 HTTP 请求
            Map<String, Object> response = restTemplate.getForObject(
                    config.getUrl(), Map.class, config.getParams());

            if (response == null) {
                log.warn("API 返回空响应: {}", dataSource.getName());
                return Collections.emptyList();
            }

            // 使用 JSONPath 提取数据项
            List<Map<String, Object>> items = JsonPath.read(response, config.getItemsPath());

            List<RawDoc> docs = new ArrayList<>();
            for (Map<String, Object> item : items) {
                // 从 item 中提取字段
                String title = extractField(item, config.getFieldMap().getOrDefault("title", ""));
                String url = extractField(item, config.getFieldMap().getOrDefault("url", ""));

                // 解析发布日期
                String publishDateStr = extractField(item, config.getFieldMap().getOrDefault("publishDate", ""));
                LocalDate publishDate = null;
                if (publishDateStr != null && !publishDateStr.isEmpty()) {
                    try {
                        publishDate = LocalDate.parse(publishDateStr, DateTimeFormatter.ISO_DATE);
                    } catch (Exception e) {
                        log.warn("日期解析失败: {} - {}", dataSource.getName(), publishDateStr);
                    }
                }

                // 过滤发布时间
                if (context.getLastPublishedAt() != null && publishDate != null) {
                    if (publishDate.isBefore(context.getLastPublishedAt().toLocalDate())) {
                        continue;
                    }
                }

                RawDoc doc = RawDoc.builder()
                        .title(title)
                        .url(url)
                        .source(config.getSourceName() != null ? config.getSourceName() : dataSource.getName())
                        .publishDate(publishDate)
                        .summary(extractField(item, config.getFieldMap().getOrDefault("summary", "")))
                        .content(extractField(item, config.getFieldMap().getOrDefault("content", "")))
                        .issuingAgency(extractField(item, config.getFieldMap().getOrDefault("issuingAgency", "")))
                        .documentNumber(extractField(item, config.getFieldMap().getOrDefault("documentNumber", "")))
                        .metadata(Collections.singletonMap("raw_item", item))
                        .build();

                docs.add(doc);
            }

            log.debug("HTTP_JSON 源 {} 抓取成功，获取到 {} 条新文档",
                    dataSource.getName(), docs.size());
            return docs;

        } catch (Exception e) {
            log.error("HTTP_JSON 源 {} 抓取失败: {}", dataSource.getName(), e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    private String extractField(Map<String, Object> item, String jsonPath) {
        if (jsonPath == null || jsonPath.isEmpty()) {
            return null;
        }
        try {
            Object value = JsonPath.read(item, jsonPath);
            return value != null ? String.valueOf(value) : null;
        } catch (Exception e) {
            log.debug("字段提取失败: {}", jsonPath, e);
            return null;
        }
    }

    private JsonApiConfig parseConfig(String config) {
        // 解析 config JSON
        Map<String, Object> map = parseJson(config);

        // 解析字段映射
        Map<String, String> fieldMap = new HashMap<>();
        Object fieldMapObj = map.get("field_map");
        if (fieldMapObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fm = (Map<String, Object>) fieldMapObj;
            for (Map.Entry<String, Object> entry : fm.entrySet()) {
                fieldMap.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        return JsonApiConfig.builder()
                .url(String.valueOf(map.getOrDefault("url", "")))
                .method(String.valueOf(map.getOrDefault("method", "GET")))
                .itemsPath(String.valueOf(map.getOrDefault("items_path", "$")))
                .fieldMap(fieldMap)
                .sourceName(String.valueOf(map.getOrDefault("source_name", null)))
                .build();
    }

    private Map<String, Object> parseJson(String json) {
        // 简单的 JSON 解析实现
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> map = new HashMap<>();
        try {
            // 这里可以使用 Jackson 或 Gson 进行更严格的解析
            // 简单的字符串解析仅用于演示
            String content = json.trim().substring(1, json.trim().length() - 1);
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] kv = pair.trim().split(":");
                if (kv.length >= 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败，返回空配置", e);
        }
        return map;
    }

    @lombok.Data
    @lombok.Builder
    private static class JsonApiConfig {
        private String url;
        private String method = "GET";
        private Map<String, String> headers = new HashMap<>();
        private Map<String, Object> params = new HashMap<>();
        private String itemsPath;
        private Map<String, String> fieldMap = new HashMap<>();
        private String sourceName;
    }
}