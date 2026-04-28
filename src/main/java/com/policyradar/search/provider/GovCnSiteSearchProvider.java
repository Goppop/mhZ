package com.policyradar.search.provider;

import com.policyradar.infra.DomainGuard;
import com.policyradar.persistence.entity.PolicyKeyword;
import com.policyradar.search.KeywordToQueryAdapter;
import com.policyradar.search.SearchContext;
import com.policyradar.search.SearchProvider;
import com.policyradar.search.UrlCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 中国政府网站内搜检索器
 *
 * 解析 https://sousuo.www.gov.cn/sousuo/search.html
 * 无反爬、官方权威、覆盖政府网下属站点，是起步首选。
 *
 * <p>新增其他政府/部委站内搜时，参照本类仿写即可（通常 30~80 行）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovCnSiteSearchProvider implements SearchProvider {

    private static final String SEARCH_URL = "https://sousuo.www.gov.cn/sousuo/search.html";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private final KeywordToQueryAdapter queryAdapter;
    private final DomainGuard domainGuard;

    @Override
    public String name() {
        return "gov_cn";
    }

    @Override
    public List<UrlCandidate> search(PolicyKeyword keyword, SearchContext ctx) {
        String query = queryAdapter.buildQuery(keyword, supportsBooleanQuery());
        if (query.isBlank()) {
            log.warn("[gov_cn] 关键词 {} 无法生成有效 query，跳过", keyword.getName());
            return Collections.emptyList();
        }

        String today = LocalDate.now().format(DATE_FMT);
        String since = LocalDate.now().minusDays(ctx.getSinceDays()).format(DATE_FMT);

        String url;
        try {
            url = SEARCH_URL
                    + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&t=zhengcelibrary_new"
                    + "&timetype=timeqb"
                    + "&mintime=" + since
                    + "&maxtime=" + today
                    + "&pagesize=" + Math.min(ctx.getMaxResults(), 30);
        } catch (Exception e) {
            log.error("[gov_cn] URL 构造失败", e);
            return Collections.emptyList();
        }

        if (!domainGuard.allow(url)) {
            log.warn("[gov_cn] 域名被 DomainGuard 拦截: {}", url);
            return Collections.emptyList();
        }

        try {
            domainGuard.acquireSlot(url);
            String ua = ctx.getUserAgent() != null ? ctx.getUserAgent() : DEFAULT_UA;
            Document doc = Jsoup.connect(url)
                    .userAgent(ua)
                    .timeout(15_000)
                    .get();

            return parseResults(doc, keyword, ctx.getMaxResults());
        } catch (Exception e) {
            log.error("[gov_cn] 搜索请求失败, query={}: {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<UrlCandidate> parseResults(Document doc, PolicyKeyword keyword, int limit) {
        List<UrlCandidate> candidates = new ArrayList<>();

        // 政府网搜索结果列表选择器（以实际页面结构为准，如变更请调整）
        Elements items = doc.select("li.res-list");
        if (items.isEmpty()) {
            // 备用选择器
            items = doc.select("div.result-item, div.search-result-item, ul.result-list li");
        }

        for (Element item : items) {
            if (candidates.size() >= limit) break;

            Element link = item.selectFirst("a[href]");
            if (link == null) continue;

            String href = link.absUrl("href");
            if (href.isBlank()) href = link.attr("href");
            if (href.isBlank()) continue;

            String title = link.text().trim();
            if (title.isBlank()) {
                Element h3 = item.selectFirst("h3, h4, .title");
                title = h3 != null ? h3.text().trim() : "";
            }

            LocalDate hintDate = parseHintDate(item);

            candidates.add(UrlCandidate.builder()
                    .url(href)
                    .titleSnippet(title)
                    .provider(name())
                    .keywordId(keyword.getId())
                    .hintedPublishDate(hintDate)
                    .build());
        }

        log.info("[gov_cn] 关键词「{}」搜索命中 {} 条候选", keyword.getName(), candidates.size());
        return candidates;
    }

    private LocalDate parseHintDate(Element item) {
        // 政府网结果中日期通常在 span.date 或 .time 等元素
        Element dateEl = item.selectFirst("span.date, span.time, .publish-date, .date");
        if (dateEl == null) return null;
        String text = dateEl.text().replaceAll("[^\\d-]", "");
        try {
            if (text.length() == 10) return LocalDate.parse(text, DATE_FMT);
        } catch (Exception ignored) {
        }
        return null;
    }
}
