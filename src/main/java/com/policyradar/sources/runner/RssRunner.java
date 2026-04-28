package com.policyradar.sources.runner;

import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.SourceRunner;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RSS 源执行器
 *
 * 使用 ROME 库解析 RSS 源，从 dataSource.getConfig() 中读取 url
 * 支持增量抓取（通过 lastPublishedAt 过滤）
 */
@Slf4j
@Component
public class RssRunner implements SourceRunner {

    @Override
    public String type() {
        return "RSS";
    }

    @Override
    public List<RawDoc> fetch(PolicyDataSource dataSource, FetchContext context) {
        // 解析配置，config 中应该包含 {"url": "https://xxx/rss.xml", "since_field": "pubDate"}
        RssConfig config = parseConfig(dataSource.getConfig());

        try {
            // 使用 ROME 解析 RSS
            URL feedUrl = new URL(config.getUrl());
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(feedUrl));

            List<RawDoc> docs = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                // 过滤发布时间早于增量抓取游标的文档
                if (context.getLastPublishedAt() != null && entry.getPublishedDate() != null) {
                    LocalDate pubDate = entry.getPublishedDate().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    if (pubDate.isBefore(context.getLastPublishedAt().toLocalDate())) {
                        continue;
                    }
                }

                RawDoc doc = RawDoc.builder()
                        .title(entry.getTitle())
                        .url(entry.getLink())
                        .source(feed.getTitle())
                        .publishDate(entry.getPublishedDate() != null ?
                                entry.getPublishedDate().toInstant()
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate() : null)
                        .summary(entry.getDescription() != null ? entry.getDescription().getValue() : null)
                        .metadata(Collections.singletonMap("author", Optional.ofNullable(entry.getAuthor()).orElse("")))
                        .build();

                docs.add(doc);
            }

            log.debug("RSS 源 {} 抓取成功，获取到 {} 条新文档",
                    dataSource.getName(), docs.size());
            return docs;

        } catch (FeedException e) {
            log.error("RSS 解析失败: {} - {}", dataSource.getName(), e.getMessage(), e);
        } catch (IOException e) {
            log.error("RSS 连接失败: {} - {}", dataSource.getName(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("RSS 抓取异常: {} - {}", dataSource.getName(), e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    private RssConfig parseConfig(String config) {
        // 简单的 JSON 解析，实际项目中应该使用 Jackson 或 Gson
        // 假设 config 格式：{"url": "https://xxx/rss.xml"}
        Map<String, Object> map = parseJson(config);
        return RssConfig.builder()
                .url(String.valueOf(map.getOrDefault("url", "")))
                .build();
    }

    private Map<String, Object> parseJson(String json) {
        // 简单的 JSON 解析实现，生产环境应使用专业库
        // 这里仅做演示用
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> map = new HashMap<>();
        try {
            // 移除首尾的 {}
            String content = json.trim().substring(1, json.trim().length() - 1);
            // 简单分割键值对
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
    private static class RssConfig {
        private String url;
        private String sinceField = "pubDate";
    }
}