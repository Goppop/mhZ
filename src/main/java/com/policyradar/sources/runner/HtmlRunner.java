package com.policyradar.sources.runner;

import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.persistence.entity.PolicyExtractRule;
import com.policyradar.persistence.entity.PolicyPaginationRule;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.SourceRunner;
import com.policyradar.sources.html.ExtractedField;
import com.policyradar.sources.html.FieldExtractor;
import com.policyradar.sources.html.HtmlCrawlConfig;
import com.policyradar.sources.html.HtmlCrawlConfigLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HTML 静态页源执行器
 *
 * 使用 Jsoup 解析 HTML 页面，并通过结构化配置表提取列表和详情字段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
//todo 线程池管理 代理池管理 错误处理 放爬虫机制 重试机制
public class HtmlRunner implements SourceRunner {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private final HtmlCrawlConfigLoader configLoader;
    private final FieldExtractor fieldExtractor;

    /**
     * 返回此执行器支持的数据源类型标识
     *
     * @return 固定返回 "HTML" 表示这是HTML数据源执行器
     */
    @Override
    public String type() {
        return "HTML";
    }

    /**
     * 执行HTML数据源的抓取入口方法
     * 加载配置并判断是否有结构化规则，有则执行结构化抓取
     *
     * @param dataSource 数据源配置，包含数据源基本信息
     * @param context 抓取上下文，包含上次抓取时间等信息
     * @return 抓取到的原始文档列表，无数据时返回空列表
     */
    @Override
    public List<RawDoc> fetch(PolicyDataSource dataSource, FetchContext context) {
        HtmlCrawlConfig config = configLoader.load(dataSource.getId());
        if (!config.hasStructuredConfig()) {
            log.warn("HTML 源 {} 未配置结构化规则，跳过抓取", dataSource.getName());
            return Collections.emptyList();
        }

        return fetchByStructuredConfig(dataSource, context, config);
    }

    /**
     * 使用结构化配置执行HTML页面抓取
     * 这是核心抓取逻辑：遍历列表页 -> 提取条目 -> 应用提取规则 -> 抓取详情页 -> 过滤结果
     *
     * @param dataSource 数据源配置
     * @param context 抓取上下文，用于日期过滤
     * @param config HTML抓取配置，包含列表页、详情页和提取规则
     * @return 抓取并处理后的原始文档列表
     */
    private List<RawDoc> fetchByStructuredConfig(
            PolicyDataSource dataSource,
            FetchContext context,
            HtmlCrawlConfig config
    ) {
        //提取爬取规则配置
        List<RawDoc> docs = new ArrayList<>();
        List<PolicyExtractRule> listRules = config.rulesFor(config.getListPage());
        List<PolicyExtractRule> detailRules = config.detailPage()
                .map(config::rulesFor)
                .orElse(Collections.emptyList());

        try {
            //爬取当前配置的所有列表页URL
            for (String listUrl : buildListUrls(config)) {
                //获取url响应Document对象
                Document doc = request(listUrl, config.getListPage().getTimeoutMs());

                //依据config配置的爬取规则提取元素集
                Elements items = doc.select(config.getListPage().getItemSelector());

                log.info("HTML 源 {} 列表页 {} 命中 {} 个 item",
                        dataSource.getName(), listUrl, items.size());
                //对元素集中的每个元素，依据提取规则提取字段，构建 RawDoc 对像
                for (Element item : items) {
                    RawDoc rawDoc = RawDoc.builder()
                            .source(dataSource.getName())
                            .metadata(new LinkedHashMap<>())
                            .build();
                    //做规则映射处理到RawDoc对象
                    applyRules(rawDoc, item, listRules, listUrl);
                    if (shouldSkipByDate(rawDoc, context)) {
                        continue;
                    }

                    if (!detailRules.isEmpty() && !isBlank(rawDoc.getUrl())) {
                        enrichFromDetail(rawDoc, detailRules, config.getDetailPage().getTimeoutMs());
                    }

                    if (isBlank(rawDoc.getTitle()) && isBlank(rawDoc.getUrl())) {
                        log.debug("HTML 源 {} 跳过空 item: {}", dataSource.getName(), item.text());
                        continue;
                    }

                    if (!shouldSkipByDate(rawDoc, context)) {
                        docs.add(rawDoc);
                    }
                }
            }

            log.debug("HTML 源 {} 抓取成功，获取到 {} 条新文档",
                    dataSource.getName(), docs.size());
            return docs;
        } catch (IOException e) {
            log.error("HTML 源 {} 连接失败: {}", dataSource.getName(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("HTML 源 {} 抓取失败: {}", dataSource.getName(), e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    /**
     * 从详情页补充提取字段信息
     * 请求详情页URL并应用详情页提取规则，将提取到的字段合并到已有文档对象中
     *
     * @param rawDoc 原始文档对象，需包含有效的详情页URL
     * @param detailRules 详情页提取规则列表
     * @param timeoutMs 请求超时时间（毫秒）
     */
    private void enrichFromDetail(
            RawDoc rawDoc,
            List<PolicyExtractRule> detailRules,
            Integer timeoutMs
    ) {
        try {
            Document detail = request(rawDoc.getUrl(), timeoutMs);
            applyRules(rawDoc, detail, detailRules, rawDoc.getUrl());
        } catch (Exception e) {
            log.debug("详情页提取失败: {}", rawDoc.getUrl(), e);
        }
    }

    /**
     * 批量应用提取规则到HTML元素
     * 按字段名分组规则，对每个字段尝试提取，提取成功则应用到文档对象
     *
     * @param rawDoc 目标文档对象，提取结果将设置到此对象
     * @param root HTML根元素，从该元素开始选择
     * @param rules 提取规则列表
     * @param baseUrl 基础URL，用于解析相对路径链接
     */
    private void applyRules(RawDoc rawDoc, Element root, List<PolicyExtractRule> rules, String baseUrl) {
        Map<String, List<PolicyExtractRule>> rulesByField = rules.stream()
                .collect(Collectors.groupingBy(
                        PolicyExtractRule::getFieldName,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<String, List<PolicyExtractRule>> entry : rulesByField.entrySet()) {
            fieldExtractor.extractFirst(root, entry.getKey(), entry.getValue())
                    .ifPresent(field -> applyField(rawDoc, field, baseUrl));
        }
    }

    /**
     * 将单个提取字段应用到文档对象
     * 根据字段名将值设置到RawDoc对应的属性或metadata中
     * 特殊字段如url会做相对路径解析，publishDate会做日期解析
     *
     * @param rawDoc 目标文档对象
     * @param field 已提取的字段对象
     * @param baseUrl 基础URL，用于解析相对链接
     */
    private void applyField(RawDoc rawDoc, ExtractedField field, String baseUrl) {
        String fieldName = field.getFieldName();
        String value = field.getValue();

        switch (fieldName) {
            case "title" -> rawDoc.setTitle(value);
            case "url" -> rawDoc.setUrl(resolveUrl(baseUrl, value));
            case "source" -> rawDoc.setSource(value);
            case "summary" -> rawDoc.setSummary(value);
            case "content" -> rawDoc.setContent(value);
            case "issuingAgency" -> rawDoc.setIssuingAgency(value);
            case "documentNumber" -> rawDoc.setDocumentNumber(value);
            case "publishDate" -> rawDoc.setPublishDate(fieldExtractor.parseDate(value, field.getRule()));
            default -> putMetadata(rawDoc, fieldName, value);
        }
    }

    /**
     * 将自定义字段放入文档的metadata中
     * 确保metadata不为null后添加键值对
     *
     * @param rawDoc 目标文档对象
     * @param fieldName 字段名称
     * @param value 字段值
     */
    private void putMetadata(RawDoc rawDoc, String fieldName, String value) {
        if (rawDoc.getMetadata() == null) {
            rawDoc.setMetadata(new LinkedHashMap<>());
        }
        rawDoc.getMetadata().put(fieldName, value);
    }

    /**
     * 构建所有需要抓取的列表页URL
     * 根据分页规则配置生成URL列表，支持NONE（单页）和URL_TEMPLATE（模板分页）模式
     *
     * @param config HTML抓取配置，包含分页规则
     * @return 需要抓取的URL列表
     */
    private List<String> buildListUrls(HtmlCrawlConfig config) {
        String baseUrl = config.getListPage().getUrl();
        PolicyPaginationRule rule = config.getPaginationRule();
        if (rule == null || isBlank(rule.getMode()) || "NONE".equalsIgnoreCase(rule.getMode())) {
            return List.of(baseUrl);
        }

        if ("URL_TEMPLATE".equalsIgnoreCase(rule.getMode()) && !isBlank(rule.getUrlTemplate())) {
            int start = rule.getStartPage() == null ? 1 : rule.getStartPage();
            int max = rule.getMaxPages() == null ? 1 : Math.max(rule.getMaxPages(), 1);
            List<String> urls = new ArrayList<>();
            for (int page = start; page < start + max; page++) {
                urls.add(rule.getUrlTemplate().replace("{page}", String.valueOf(page)));
            }
            return urls;
        }

        log.warn("暂不支持分页模式 {}，仅抓取第一页", rule.getMode());
        return List.of(baseUrl);
    }

    /**
     * 发起HTTP请求获取HTML文档
     * 使用Jsoup库请求指定URL，设置User-Agent和超时时间
     *
     * @param url 目标URL
     * @param timeoutMs 超时时间（毫秒），null时默认15秒
     * @return 解析后的Jsoup Document对象
     * @throws IOException 网络请求失败时抛出
     */
    private Document request(String url, Integer timeoutMs) throws IOException {
        return Jsoup.connect(url)
                .userAgent(UA)
                .timeout(timeoutMs == null ? 15_000 : timeoutMs)
                .get();
    }

    /**
     * 根据发布日期判断是否跳过该文档
     * 对比文档发布日期与上次抓取时间，早于上次抓取时间则跳过
     *
     * @param rawDoc 待判断的文档对象
     * @param context 抓取上下文，包含上次抓取时间
     * @return true表示应跳过此文档，false表示应保留
     */
    private boolean shouldSkipByDate(RawDoc rawDoc, FetchContext context) {
        return context.getLastPublishedAt() != null
                && rawDoc.getPublishDate() != null
                && rawDoc.getPublishDate().isBefore(context.getLastPublishedAt().toLocalDate());
    }

    /**
     * 解析相对URL为绝对URL
     * 基于基础URL将相对路径转换为完整URL，处理失败时返回原URL
     *
     * @param baseUrl 基础URL
     * @param url 待解析的URL（可能是相对路径）
     * @return 解析后的绝对URL，解析失败返回原URL
     */
    private String resolveUrl(String baseUrl, String url) {
        if (isBlank(url)) {
            return url;
        }
        try {
            return URI.create(baseUrl).resolve(url.trim()).toString();
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 判断字符串是否为空或空白
     * null、空字符串或仅包含空白字符均返回true
     *
     * @param value 待检查的字符串
     * @return true表示为空或空白，false表示非空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}