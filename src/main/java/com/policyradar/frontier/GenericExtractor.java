package com.policyradar.frontier;

import com.policyradar.sources.RawDoc;
import lombok.extern.slf4j.Slf4j;
import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用正文抽取器（readability 兜底）
 *
 * 适用于无已知模板的站点。
 * readability4j 自动去除导航/广告等噪声，抽取主正文和标题。
 * 启发式规则补充政策文档常见结构化字段（发布机构、文号、日期）。
 *
 * <p>已知模板 (DetailTemplate) 优先，本类仅在无模板时兜底。</p>
 */
@Slf4j
@Component
public class GenericExtractor {

    /** 政府公文文号正则：如"国发〔2024〕1号"、"发改产业〔2023〕1234号" */
    private static final Pattern DOC_NUMBER_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]{2,8}[〔\\[（(][\\d年]{4}[〕\\]）)][\\d]+号");

    /** 日期正则：2024-01-15 / 2024年1月15日 / 2024/01/15 */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[年/\\-](\\d{1,2})[月/\\-](\\d{1,2})日?");

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 从原始 HTML 中抽取 RawDoc
     *
     * @param url  页面 URL（用于 readability 上下文）
     * @param html 原始 HTML
     * @return 抽取结果（字段可能不完整）
     */
    public RawDoc extract(String url, String html) {
        RawDoc.RawDocBuilder builder = RawDoc.builder()
                .url(url)
                .source(extractDomainAsSource(url));

        try {
            Readability4J readability = new Readability4J(url, html);
            Article article = readability.parse();

            String title = article.getTitle();
            String content = article.getTextContent();
            if (content == null || content.isBlank()) {
                content = article.getContent();
            }

            builder.title(title);
            builder.content(content);
            builder.summary(truncate(content, 300));

            // 启发式抽取结构化字段
            if (content != null) {
                builder.publishDate(extractDate(content));
                builder.documentNumber(extractDocNumber(content));
                builder.issuingAgency(extractAgency(url, content));
            }

        } catch (Exception e) {
            // readability 失败时退化到 jsoup 粗提取
            log.warn("[GenericExtractor] readability 解析失败 {}，使用 jsoup 降级: {}", url, e.getMessage());
            fallbackJsoup(html, url, builder);
        }

        return builder.build();
    }

    private void fallbackJsoup(String html, String url, RawDoc.RawDocBuilder builder) {
        try {
            Document doc = Jsoup.parse(html, url);
            builder.title(doc.title());
            String text = doc.body() != null ? doc.body().text() : "";
            builder.content(text);
            builder.summary(truncate(text, 300));
            builder.publishDate(extractDate(text));
            builder.documentNumber(extractDocNumber(text));
        } catch (Exception ignored) {
        }
    }

    private LocalDate extractDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find()) {
            try {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int d = Integer.parseInt(m.group(3));
                if (y >= 2000 && y <= 2100 && mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
                    return LocalDate.of(y, mo, d);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractDocNumber(String text) {
        Matcher m = DOC_NUMBER_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String extractAgency(String url, String text) {
        // 从 URL 路径前缀猜测发布机构
        if (url.contains("ndrc.gov.cn")) return "国家发展和改革委员会";
        if (url.contains("miit.gov.cn")) return "工业和信息化部";
        if (url.contains("mof.gov.cn")) return "财政部";
        if (url.contains("mee.gov.cn")) return "生态环境部";
        if (url.contains("pbc.gov.cn")) return "中国人民银行";
        if (url.contains("samr.gov.cn")) return "国家市场监督管理总局";
        if (url.contains("www.gov.cn")) return "国务院";
        // 启发式：正文开头找"XX部""XX委"等
        Matcher m = Pattern.compile("^[\\s\\S]{0,100}?([\\u4e00-\\u9fa5]{2,10}[部委局办院厅局])").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String extractDomainAsSource(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        String stripped = s.replaceAll("\\s+", " ").trim();
        return stripped.length() <= maxLen ? stripped : stripped.substring(0, maxLen) + "…";
    }
}
