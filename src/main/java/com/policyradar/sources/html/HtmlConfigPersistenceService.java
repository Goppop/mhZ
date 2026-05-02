package com.policyradar.sources.html;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyradar.api.htmlconfig.HtmlConfigSaveRequest;
import com.policyradar.persistence.entity.PolicyCrawlPage;
import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.persistence.entity.PolicyExtractRule;
import com.policyradar.persistence.entity.PolicyPaginationRule;
import com.policyradar.persistence.mapper.PolicyCrawlPageMapper;
import com.policyradar.persistence.mapper.PolicyDataSourceMapper;
import com.policyradar.persistence.mapper.PolicyExtractRuleMapper;
import com.policyradar.persistence.mapper.PolicyPaginationRuleMapper;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.runner.HtmlRunner;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 阶段 B — HTML 数据源配置的持久化与试跑。
 *
 * 负责将前端 SelectionState 翻译为 policy_* 表行并写入，
 * 同时提供立即试跑能力（调 HtmlRunner.fetch）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HtmlConfigPersistenceService {

    private final PolicyDataSourceMapper dataSourceMapper;
    private final PolicyCrawlPageMapper pageMapper;
    private final PolicyExtractRuleMapper ruleMapper;
    private final PolicyPaginationRuleMapper paginationRuleMapper;
    private final HtmlRunner htmlRunner;
    private final ObjectMapper objectMapper;

    // ================================================================
    // 保存全量配置
    // ================================================================

    /**
     * 全新保存配置（事务内：DataSource → LIST page → DETAIL page → rules → pagination）。
     *
     * @return 新创建的数据源 ID
     */
    @Transactional
    public Long saveFullConfig(HtmlConfigSaveRequest req) {
        // 1. data source
        PolicyDataSource ds = new PolicyDataSource();
        ds.setName(req.getDataSource().getName());
        ds.setType("HTML");
        ds.setCronExpr(req.getDataSource().getCronExpr());
        ds.setEnabled(req.getDataSource().isEnabled());
        dataSourceMapper.insert(ds);
        Long dataSourceId = ds.getId();
        log.info("[save] 数据源已创建 id={} name={}", dataSourceId, ds.getName());

        // 2. LIST page
        PolicyCrawlPage listPage = buildPage(dataSourceId, "LIST", "列表页",
                req.getListPage().getUrl(),
                req.getListPage().getItemSelector(),
                req.getListPage().getHeaders(),
                req.getListPage().getTimeoutMs(),
                0);
        pageMapper.insert(listPage);
        log.info("[save] LIST page id={} itemSelector={}", listPage.getId(), listPage.getItemSelector());

        // 3. DETAIL page（可选）
        Long detailPageId = null;
        if (req.getDetailPage() != null && req.getDetailRules() != null && !req.getDetailRules().isEmpty()) {
            PolicyCrawlPage detailPage = buildPage(dataSourceId, "DETAIL", "详情页",
                    null, null,
                    req.getDetailPage().getHeaders(),
                    req.getDetailPage().getTimeoutMs(),
                    1);
            pageMapper.insert(detailPage);
            detailPageId = detailPage.getId();
            log.info("[save] DETAIL page id={}", detailPageId);
        }

        // 4. LIST rules
        int listRuleCount = 0;
        if (req.getListRules() != null) {
            for (HtmlConfigSaveRequest.RuleDraft rd : req.getListRules()) {
                PolicyExtractRule rule = buildRule(listPage.getId(), "ITEM", rd);
                ruleMapper.insert(rule);
                listRuleCount++;
            }
        }
        log.info("[save] LIST rules count={}", listRuleCount);

        // 5. DETAIL rules（可选）
        int detailRuleCount = 0;
        if (detailPageId != null && req.getDetailRules() != null) {
            for (HtmlConfigSaveRequest.RuleDraft rd : req.getDetailRules()) {
                PolicyExtractRule rule = buildRule(detailPageId, "DETAIL", rd);
                ruleMapper.insert(rule);
                detailRuleCount++;
            }
        }
        log.info("[save] DETAIL rules count={}", detailRuleCount);

        // 6. pagination rule
        HtmlConfigSaveRequest.PaginationMeta pag = req.getPaginationRule();
        if (pag == null) {
            pag = new HtmlConfigSaveRequest.PaginationMeta("NONE", null, 1, 1);
        }
        PolicyPaginationRule pagRule = new PolicyPaginationRule();
        pagRule.setPageId(listPage.getId());
        pagRule.setMode(pag.getMode());
        pagRule.setUrlTemplate(pag.getUrlTemplate());
        pagRule.setStartPage(pag.getStartPage());
        pagRule.setMaxPages(pag.getMaxPages());
        pagRule.setEnabled(true);
        paginationRuleMapper.insert(pagRule);
        log.info("[save] pagination mode={}", pag.getMode());

        return dataSourceId;
    }

    // ================================================================
    // 试跑
    // ================================================================

    /**
     * 立即触发一次抓取，返回统计结果。
     */
    public RunResult runNow(Long dataSourceId) {
        PolicyDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceId);
        }

        FetchContext ctx = FetchContext.builder()
                .lastPublishedAt(null)   // 全量抓取，不做增量过滤
                .timeoutSec(60)
                .build();

        log.info("[run] 开始试跑 dataSourceId={} name={}", dataSourceId, ds.getName());
        long t0 = System.currentTimeMillis();

        List<RawDoc> docs;
        try {
            docs = htmlRunner.fetch(ds, ctx);
        } catch (Exception e) {
            log.error("[run] 试跑异常 dataSourceId={}", dataSourceId, e);
            return RunResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }

        long elapsedMs = System.currentTimeMillis() - t0;
        log.info("[run] 试跑完成 fetchedCount={} elapsedMs={}", docs.size(), elapsedMs);

        return RunResult.builder()
                .success(true)
                .fetchedCount(docs.size())
                .recentDocs(buildDocSummaries(docs))
                .elapsedMs(elapsedMs)
                .build();
    }

    // ================================================================
    // 校验（PRD 7.7 节核心项）
    // ================================================================

    /**
     * 保存前校验，返回错误列表。空列表 = 通过。
     */
    public List<Map<String, String>> validate(HtmlConfigSaveRequest req) {
        List<Map<String, String>> errors = new ArrayList<>();

        // 1. name 非空
        if (req.getDataSource() == null || isBlank(req.getDataSource().getName())) {
            errors.add(err("dataSource.name", "REQUIRED", "数据源名称不能为空"));
        }

        // 2. url 非空且合法
        if (req.getListPage() == null || isBlank(req.getListPage().getUrl())) {
            errors.add(err("listPage.url", "REQUIRED", "列表页 URL 不能为空"));
        } else {
            try {
                new java.net.URI(req.getListPage().getUrl());
            } catch (Exception e) {
                errors.add(err("listPage.url", "INVALID", "请输入正确的网址"));
            }
        }

        // 3. itemSelector 非空
        if (req.getListPage() == null || isBlank(req.getListPage().getItemSelector())) {
            errors.add(err("listPage.itemSelector", "REQUIRED", "列表项选择器不能为空"));
        }

        // 4. 至少有一条 title 规则
        if (req.getListRules() == null || req.getListRules().stream().noneMatch(r -> "title".equals(r.getFieldName()))) {
            errors.add(err("listRules", "REQUIRED", "请先绑定标题"));
        }

        // 5. 至少有一条 url 规则
        if (req.getListRules() == null || req.getListRules().stream().noneMatch(r -> "url".equals(r.getFieldName()))) {
            errors.add(err("listRules", "REQUIRED", "请先绑定原文链接"));
        }

        // 6-7. 规则级校验
        List<HtmlConfigSaveRequest.RuleDraft> allRules = new ArrayList<>();
        if (req.getListRules() != null) allRules.addAll(req.getListRules());
        if (req.getDetailRules() != null) allRules.addAll(req.getDetailRules());

        for (HtmlConfigSaveRequest.RuleDraft r : allRules) {
            // ATTR 必须有 attrName
            if ("ATTR".equals(r.getValueType()) && isBlank(r.getAttrName())) {
                errors.add(err("rule." + r.getFieldName(), "REQUIRED",
                        r.getFieldName() + " 的取值类型为 ATTR，必须指定属性名"));
            }
            // CONST 必须有 constValue
            if ("CONST".equals(r.getValueType()) && isBlank(r.getConstValue())) {
                errors.add(err("rule." + r.getFieldName(), "REQUIRED",
                        r.getFieldName() + " 的取值类型为 CONST，必须填写常量值"));
            }
            // REGEX 必须有 regexPattern 且可编译
            if ("REGEX".equals(r.getValueType())) {
                if (isBlank(r.getRegexPattern())) {
                    errors.add(err("rule." + r.getFieldName(), "REQUIRED",
                            r.getFieldName() + " 的取值类型为 REGEX，必须填写正则表达式"));
                } else {
                    try {
                        Pattern.compile(r.getRegexPattern());
                    } catch (PatternSyntaxException e) {
                        errors.add(err("rule." + r.getFieldName(), "INVALID",
                                r.getFieldName() + " 的正则表达式无法解析: " + e.getMessage()));
                    }
                }
            }
        }

        // 详情页启用时必须至少有一条 detail rule
        if (req.getDetailPage() != null
                && (req.getDetailRules() == null || req.getDetailRules().isEmpty())) {
            errors.add(err("detailRules", "REQUIRED", "已启用详情页，请绑定至少一个详情字段"));
        }

        // pagination maxPages 范围
        HtmlConfigSaveRequest.PaginationMeta pag = req.getPaginationRule();
        if (pag != null) {
            if (pag.getMaxPages() < 1 || pag.getMaxPages() > 100) {
                errors.add(err("paginationRule.maxPages", "INVALID", "最大页数应在 1-100 之间"));
            }
            if ("URL_TEMPLATE".equals(pag.getMode())
                    && (isBlank(pag.getUrlTemplate()) || !pag.getUrlTemplate().contains("{page}"))) {
                errors.add(err("paginationRule.urlTemplate", "INVALID",
                        "URL_TEMPLATE 模式下 URL 模板必须包含 {page} 占位符"));
            }
        }

        return errors;
    }

    // ================================================================
    // 私有辅助
    // ================================================================

    private PolicyCrawlPage buildPage(Long dataSourceId, String role, String name,
                                       String url, String itemSelector,
                                       Map<String, String> headers, int timeoutMs,
                                       int sortOrder) {
        PolicyCrawlPage page = new PolicyCrawlPage();
        page.setDataSourceId(dataSourceId);
        page.setPageRole(role);
        page.setName(name);
        page.setUrl(url);
        page.setItemSelector(itemSelector);
        page.setRequestMethod("GET");
        page.setHeaders(toJson(headers));
        page.setTimeoutMs(timeoutMs);
        page.setSortOrder(sortOrder);
        page.setEnabled(true);
        return page;
    }

    private PolicyExtractRule buildRule(Long pageId, String scope, HtmlConfigSaveRequest.RuleDraft rd) {
        PolicyExtractRule rule = new PolicyExtractRule();
        rule.setPageId(pageId);
        rule.setFieldName(rd.getFieldName());
        rule.setScope(scope);
        rule.setSelector(rd.getSelector());
        rule.setValueType(rd.getValueType());
        rule.setAttrName(rd.getAttrName());
        rule.setConstValue(rd.getConstValue());
        rule.setRegexPattern(rd.getRegexPattern());
        rule.setDateFormat(rd.getDateFormat());
        rule.setRequired(rd.isRequired());
        rule.setFallbackGroup(rd.getFieldName());  // 默认与 fieldName 相同
        rule.setSortOrder(rd.getSortOrder());
        rule.setEnabled(true);
        return rule;
    }

    private String toJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.warn("序列化 headers 失败", e);
            return "{}";
        }
    }

    private List<DocSummary> buildDocSummaries(List<RawDoc> docs) {
        List<DocSummary> list = new ArrayList<>();
        int limit = Math.min(docs.size(), 10);
        for (int i = 0; i < limit; i++) {
            RawDoc d = docs.get(i);
            list.add(DocSummary.builder()
                    .title(d.getTitle())
                    .url(d.getUrl())
                    .publishDate(d.getPublishDate() != null ? d.getPublishDate().toString() : null)
                    .build());
        }
        return list;
    }

    private Map<String, String> err(String field, String code, String message) {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("field", field);
        e.put("code", code);
        e.put("message", message);
        return e;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ================================================================
    // 内部类型
    // ================================================================

    @Data
    @Builder
    public static class RunResult {
        private boolean success;
        private int fetchedCount;
        private List<DocSummary> recentDocs;
        private long elapsedMs;
        private String error;
    }

    @Data
    @Builder
    public static class DocSummary {
        private String title;
        private String url;
        private String publishDate;
    }
}
