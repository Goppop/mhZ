package com.policyradar.frontier;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.policyradar.sources.RawDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 详情页解析路由器
 *
 * 策略：模板优先（已知站点精确提取）+ GenericExtractor 兜底（未知站点通用抽取）
 *
 * <p>内置常见政府网站模板。后续可从 DB 表 policy_detail_template 加载。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DetailRouter {

    private final GenericExtractor genericExtractor;

    /** 内置模板：URL 域名关键词 → 选择器配置 */
    private static final List<DetailTemplate> BUILTIN_TEMPLATES = buildBuiltinTemplates();

    /**
     * 根据 URL 选择合适的解析策略，返回 RawDoc
     *
     * @param url  详情页 URL
     * @param html 已抓取的 HTML 内容
     */
    public RawDoc parse(String url, String html) {
        for (DetailTemplate t : BUILTIN_TEMPLATES) {
            if (t.matches(url)) {
                try {
                    RawDoc doc = t.extract(url, html);
                    log.debug("[DetailRouter] 模板命中: {} → {}", t.getName(), url);
                    return doc;
                } catch (Exception e) {
                    log.warn("[DetailRouter] 模板 {} 提取失败，降级到通用抽取: {}", t.getName(), e.getMessage());
                }
            }
        }
        log.debug("[DetailRouter] 无已知模板，使用 GenericExtractor: {}", url);
        return genericExtractor.extract(url, html);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 内置模板定义
    // ──────────────────────────────────────────────────────────────────────────

    private static List<DetailTemplate> buildBuiltinTemplates() {
        List<DetailTemplate> list = new ArrayList<>();

        // 中国政府网通用详情页
        list.add(new DetailTemplate(
                "gov_cn_generic",
                "www.gov.cn",
                Map.of(
                        "title", "div.article-title, h1.title, h1",
                        "content", "div.article, div#UCAP-CONTENT, div.pages-content",
                        "publishDate", "span.date, span.neirong-time, p.info span",
                        "issuingAgency", "div.source, span.source"
                )));

        // 国家发展改革委
        list.add(new DetailTemplate(
                "ndrc",
                "ndrc.gov.cn",
                Map.of(
                        "title", "h1, div.article-title",
                        "content", "div.TRS_Editor, div.article-content",
                        "publishDate", "div.article-time span, span.time",
                        "issuingAgency", "div.article-source"
                )));

        // 工业和信息化部
        list.add(new DetailTemplate(
                "miit",
                "miit.gov.cn",
                Map.of(
                        "title", "h1.title, div.con-title",
                        "content", "div.con-content, div.TRS_Editor",
                        "publishDate", "div.con-info span.time",
                        "issuingAgency", "div.con-info span.source"
                )));

        // 财政部
        list.add(new DetailTemplate(
                "mof",
                "mof.gov.cn",
                Map.of(
                        "title", "div.article h1, h1.atitle",
                        "content", "div.article-content, div.TRS_Editor",
                        "publishDate", "div.article-time, span.pubtime"
                )));

        // 中国人民银行
        list.add(new DetailTemplate(
                "pbc",
                "pbc.gov.cn",
                Map.of(
                        "title", "div.xTitle h1, h1",
                        "content", "div.xBody, div.article-content",
                        "publishDate", "div.xinxi span"
                )));

        return list;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 内部类：详情模板
    // ──────────────────────────────────────────────────────────────────────────

    public static class DetailTemplate {
        private final String name;
        private final String domainKeyword;
        private final Map<String, String> selectors;

        private static final DateTimeFormatter[] DATE_FORMATS = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy年M月d日"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        };

        public DetailTemplate(String name, String domainKeyword, Map<String, String> selectors) {
            this.name = name;
            this.domainKeyword = domainKeyword;
            this.selectors = selectors;
        }

        public String getName() { return name; }

        public boolean matches(String url) {
            return url != null && url.contains(domainKeyword);
        }

        public RawDoc extract(String url, String html) {
            Document doc = Jsoup.parse(html, url);
            return RawDoc.builder()
                    .url(url)
                    .title(selectText(doc, "title"))
                    .content(selectText(doc, "content"))
                    .summary(truncateSummary(selectText(doc, "content")))
                    .publishDate(parseDate(selectText(doc, "publishDate")))
                    .issuingAgency(selectText(doc, "issuingAgency"))
                    .documentNumber(selectText(doc, "documentNumber"))
                    .source(domainKeyword)
                    .build();
        }

        private String selectText(Document doc, String field) {
            String sel = selectors.get(field);
            if (sel == null) return null;
            for (String s : sel.split(",")) {
                Elements els = doc.select(s.trim());
                if (!els.isEmpty()) {
                    String text = els.first().text().trim();
                    if (!text.isEmpty()) return text;
                }
            }
            return null;
        }

        private LocalDate parseDate(String text) {
            if (text == null) return null;
            String cleaned = text.replaceAll("[\\s　]", "");
            for (DateTimeFormatter fmt : DATE_FORMATS) {
                try { return LocalDate.parse(cleaned, fmt); } catch (Exception ignored) {}
            }
            // 截取 10 位 yyyy-MM-dd
            if (cleaned.length() >= 10) {
                try { return LocalDate.parse(cleaned.substring(0, 10)); } catch (Exception ignored) {}
            }
            return null;
        }

        private String truncateSummary(String content) {
            if (content == null) return null;
            String s = content.replaceAll("\\s+", " ").trim();
            return s.length() <= 200 ? s : s.substring(0, 200) + "…";
        }
    }
}
