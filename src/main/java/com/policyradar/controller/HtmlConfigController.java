package com.policyradar.controller;

import com.policyradar.api.htmlconfig.ClickedElementInfo;
import com.policyradar.api.htmlconfig.HtmlConfigSaveRequest;
import com.policyradar.sources.html.FieldRuleDraft;
import com.policyradar.sources.html.HtmlConfigPersistenceService;
import com.policyradar.sources.html.HtmlPageResponse;
import com.policyradar.sources.html.HtmlRequestClient;
import com.policyradar.sources.html.PaginationDraft;
import com.policyradar.sources.html.preview.HtmlConfigPreviewService;
import com.policyradar.sources.html.selector.HtmlSelectorGenerator;
import com.policyradar.sources.html.selector.SelectorCandidate;
import com.policyradar.sources.html.snapshot.HtmlSnapshot;
import com.policyradar.sources.html.snapshot.HtmlSnapshotCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * PRD V2 第 8 章 — HTML 爬虫配置工作台后端接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/html-config")
@RequiredArgsConstructor
public class HtmlConfigController {

    private final HtmlRequestClient requestClient;
    private final HtmlSnapshotCache snapshotCache;
    private final HtmlSelectorGenerator selectorGenerator;
    private final HtmlConfigPreviewService previewService;
    private final HtmlConfigPersistenceService persistenceService;

    // ---- 通用响应包装 ----

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("code", "OK");
        resp.put("message", "");
        resp.put("data", data);
        resp.put("errors", List.of());
        return ResponseEntity.ok(resp);
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message, List<Map<String, String>> errors) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        resp.put("code", code);
        resp.put("message", message);
        resp.put("data", null);
        resp.put("errors", errors != null ? errors : List.of());
        return ResponseEntity.ok(resp);
    }

    // ==================== 8.2 GET /capabilities ====================

    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> capabilities() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 列表字段
        List<Map<String, Object>> listFields = List.of(
                fieldCap("title", "标题", true, "TEXT", null),
                fieldCap("url", "原文链接", true, "ATTR", "href"),
                fieldCap("publishDate", "发布日期", false, "TEXT", null),
                fieldCap("source", "来源", false, "TEXT", null),
                fieldCap("summary", "摘要", false, "TEXT", null)
        );
        // 详情字段
        List<Map<String, Object>> detailFields = List.of(
                fieldCap("title", "完整标题", false, "TEXT", null),
                fieldCap("content", "正文", false, "HTML", null),
                fieldCap("publishDate", "发布日期", false, "TEXT", null),
                fieldCap("issuingAgency", "发布机构", false, "TEXT", null),
                fieldCap("documentNumber", "文号", false, "TEXT", null)
        );

        data.put("listFields", listFields);
        data.put("detailFields", detailFields);
        data.put("valueTypes", List.of("TEXT", "ATTR", "HTML", "CONST", "REGEX"));
        data.put("paginationModes", List.of("NONE", "URL_TEMPLATE"));
        data.put("limits", Map.of(
                "maxPages", 100,
                "snapshotTtlSeconds", 1800,
                "maxSnapshotsInMemory", 200
        ));

        return ok(data);
    }

    private Map<String, Object> fieldCap(String name, String label, boolean required, String defaultValueType, String defaultAttrName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("label", label);
        m.put("required", required);
        m.put("defaultValueType", defaultValueType);
        if (defaultAttrName != null) m.put("defaultAttrName", defaultAttrName);
        return m;
    }

    // ==================== 8.3 POST /load-page ====================

    @PostMapping("/load-page")
    public ResponseEntity<Map<String, Object>> loadPage(@RequestBody Map<String, Object> req) {
        String url = (String) req.get("url");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) req.getOrDefault("headers", Map.of());
        int timeoutMs = req.get("timeoutMs") instanceof Number n ? n.intValue() : 15000;

        // URL 校验
        if (url == null || url.isBlank()) {
            return error("VALIDATION_ERROR", "URL 不能为空", List.of(err("url", "REQUIRED", "请输入网址")));
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return error("VALIDATION_ERROR", "不支持的协议", List.of(err("url", "INVALID", "仅支持 http/https")));
            }
        } catch (Exception e) {
            return error("VALIDATION_ERROR", "URL 格式不合法", List.of(err("url", "INVALID", "请输入正确的网址")));
        }

        HtmlPageResponse page = requestClient.fetch(url, headers, timeoutMs);

        // 缓存快照
        HtmlSnapshot snap = HtmlSnapshot.builder()
                .url(url)
                .finalUrl(page.getFinalUrl())
                .statusCode(page.getStatusCode())
                .html(page.isSuccess() ? page.getHtml() : "")
                .title(page.getTitle())
                .fetchedAt(Instant.now())
                .build();
        String snapshotId = snapshotCache.put(snap);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("snapshotId", snapshotId);
        data.put("finalUrl", page.getFinalUrl());
        data.put("statusCode", page.getStatusCode());
        data.put("title", page.getTitle());
        data.put("html", page.isSuccess() ? page.getHtml() : "");
        data.put("fetchedAt", Instant.now().toString());
        data.put("warnings", page.getWarnings() != null ? page.getWarnings() : List.of());
        data.put("error", page.isSuccess() ? null : page.getError());

        if (!page.isSuccess()) {
            return error("FETCH_FAILED", page.getError(), List.of());
        }

        return ok(data);
    }

    // ==================== 8.4 POST /suggest-item-selector ====================

    @PostMapping("/suggest-item-selector")
    public ResponseEntity<Map<String, Object>> suggestItemSelector(@RequestBody Map<String, Object> req) {
        String snapshotId = (String) req.get("snapshotId");
        @SuppressWarnings("unchecked")
        Map<String, Object> clickMap = (Map<String, Object>) req.get("click");
        ClickedElementInfo click = mapToClick(clickMap);

        HtmlSnapshot snap = snapshotCache.get(snapshotId);
        if (snap == null) {
            return error("SNAPSHOT_NOT_FOUND", "快照已过期，请重新加载页面", List.of());
        }

        @SuppressWarnings("unchecked")
        List<Integer> hintList = (List<Integer>) req.get("regionHintIndexPath");
        int[] hint = hintList != null ? hintList.stream().mapToInt(Integer::intValue).toArray() : null;

        log.info("[suggest-item] snapshotId={} tag={} indexPath={}",
                snapshotId, click.getTag(), click.getIndexPath() != null ? java.util.Arrays.toString(click.getIndexPath()) : "null");

        HtmlSelectorGenerator.ItemSelectorResult result = selectorGenerator.suggestItemSelector(snap.getHtml(), click, hint);

        if (result.getPrimary() == null) {
            log.warn("[suggest-item] SELECTOR_NOT_MATCH warnings={}", result.getWarnings());
            return error("SELECTOR_NOT_MATCH",
                    "没有识别出列表，请尝试点更外层的卡片",
                    List.of(err("click", "SELECTOR_NOT_MATCH", "无法生成命中 ≥ 2 的选择器")));
        }

        log.info("[suggest-item] 成功 primary={} itemCount={}", result.getPrimary().getSelector(), result.getPrimary().getItemCount());

        Map<String, Object> primary = mapSelectorCandidate(result.getPrimary());
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (SelectorCandidate c : result.getCandidates() != null ? result.getCandidates() : List.<SelectorCandidate>of()) {
            candidates.add(mapSelectorCandidate(c));
        }
        List<Map<String, Object>> samples = new ArrayList<>();
        if (result.getSampleItems() != null) {
            for (var s : result.getSampleItems()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("index", s.getIndex());
                sm.put("textPreview", s.getTextPreview());
                sm.put("indexPath", s.getIndexPath());
                samples.add(sm);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("primary", primary);
        data.put("candidates", candidates);
        data.put("sampleItems", samples);
        data.put("warnings", result.getWarnings() != null ? result.getWarnings() : List.of());

        return ok(data);
    }

    // ==================== 8.5 POST /suggest-field-rule ====================

    @PostMapping("/suggest-field-rule")
    public ResponseEntity<Map<String, Object>> suggestFieldRule(@RequestBody Map<String, Object> req) {
        String snapshotId = (String) req.get("snapshotId");
        String pageRole = (String) req.get("pageRole");
        String fieldName = (String) req.get("fieldName");
        @SuppressWarnings("unchecked")
        Map<String, Object> clickMap = (Map<String, Object>) req.get("click");
        ClickedElementInfo click = mapToClick(clickMap);
        String itemSelector = (String) req.get("itemSelector");

        HtmlSnapshot snap = snapshotCache.get(snapshotId);
        if (snap == null) {
            return error("SNAPSHOT_NOT_FOUND", "快照已过期", List.of());
        }

        HtmlSelectorGenerator.FieldContext ctx = HtmlSelectorGenerator.FieldContext.builder()
                .fieldName(fieldName)
                .pageRole(pageRole)
                .itemSelector(itemSelector)
                .build();

        log.info("[suggest-field] fieldName={} pageRole={} itemSelector={}", fieldName, pageRole, itemSelector);
        HtmlSelectorGenerator.FieldRuleResult result = selectorGenerator.suggestFieldRule(snap.getHtml(), click, ctx);

        if (result.getPrimary() == null) {
            log.warn("[suggest-field] SELECTOR_NOT_MATCH warnings={}", result.getWarnings());
            return error("SELECTOR_NOT_MATCH", "未取到任何值，请重选元素", List.of());
        }
        log.info("[suggest-field] 成功 primary.selector={}", result.getPrimary().getSelector());

        Map<String, Object> primary = mapFieldRuleCandidate(result.getPrimary());
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (result.getCandidates() != null) {
            for (HtmlSelectorGenerator.FieldRuleCandidate c : result.getCandidates()) {
                candidates.add(mapFieldRuleCandidate(c));
            }
        }

        Map<String, Object> preview = new LinkedHashMap<>();
        if (result.getPreview() != null) {
            var fs = result.getPreview().getFieldStats();
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("hitRate", fs.getHitRate());
            stats.put("blankCount", fs.getBlankCount());
            stats.put("successCount", fs.getSuccessCount());
            preview.put("fieldStats", stats);

            List<Map<String, Object>> previewSamples = new ArrayList<>();
            if (result.getPreview().getSamples() != null) {
                for (var s : result.getPreview().getSamples()) {
                    Map<String, Object> ps = new LinkedHashMap<>();
                    ps.put("itemIndex", s.getItemIndex());
                    ps.put("value", s.getValue());
                    ps.put("raw", s.getRaw());
                    ps.put("warnings", s.getWarnings() != null ? s.getWarnings() : List.of());
                    previewSamples.add(ps);
                }
            }
            preview.put("samples", previewSamples);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("primary", primary);
        data.put("candidates", candidates);
        data.put("preview", preview);
        data.put("warnings", result.getWarnings() != null ? result.getWarnings() : List.of());

        return ok(data);
    }

    // ==================== 8.6 POST /preview-list ====================

    @PostMapping("/preview-list")
    public ResponseEntity<Map<String, Object>> previewList(@RequestBody Map<String, Object> req) {
        String snapshotId = (String) req.get("snapshotId");
        String itemSelector = (String) req.get("itemSelector");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) req.get("rules");

        List<FieldRuleDraft> rules = new ArrayList<>();
        if (ruleMaps != null) {
            for (Map<String, Object> rm : ruleMaps) {
                rules.add(mapToRuleDraft(rm));
            }
        }

        PaginationDraft pagination = null;
        @SuppressWarnings("unchecked")
        Map<String, Object> pagMap = (Map<String, Object>) req.get("paginationRule");
        if (pagMap != null) {
            pagination = PaginationDraft.builder()
                    .mode((String) pagMap.get("mode"))
                    .urlTemplate((String) pagMap.get("urlTemplate"))
                    .startPage(pagMap.get("startPage") instanceof Number n ? n.intValue() : 1)
                    .maxPages(pagMap.get("maxPages") instanceof Number n ? n.intValue() : 1)
                    .build();
        }

        log.info("[preview-list] snapshotId={} rules={}", snapshotId, rules.size());
        HtmlConfigPreviewService.ListPreviewResult result;
        try {
            result = previewService.previewList(snapshotId, itemSelector, rules, pagination);
        } catch (Exception e) {
            log.error("[preview-list] 异常", e);
            return error("INTERNAL_ERROR", "预览失败: " + e.getMessage(), List.of());
        }

        if (result.getWarnings() != null && !result.getWarnings().isEmpty()) {
            return error("SNAPSHOT_NOT_FOUND", String.join("; ", result.getWarnings()), List.of());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemCount", result.getItemCount());

        List<Map<String, Object>> samples = new ArrayList<>();
        if (result.getSamples() != null) {
            for (var s : result.getSamples()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("itemIndex", s.getItemIndex());
                sm.put("fields", s.getFields());
                samples.add(sm);
            }
        }
        data.put("samples", samples);
        data.put("fieldStats", result.getFieldStats());

        if (result.getPaginationPreview() != null) {
            var ppObj = result.getPaginationPreview();
            Map<String, Object> pp = new LinkedHashMap<>();
            pp.put("urls", ppObj.getUrls());
            pp.put("perPageItemCount", ppObj.getPerPageItemCount());
            data.put("paginationPreview", pp);
        }

        data.put("warnings", result.getWarnings() != null ? result.getWarnings() : List.of());
        return ok(data);
    }

    // ==================== 8.7 POST /preview-detail ====================

    @PostMapping("/preview-detail")
    public ResponseEntity<Map<String, Object>> previewDetail(@RequestBody Map<String, Object> req) {
        String detailSnapshotId = (String) req.get("detailSnapshotId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) req.get("rules");

        List<FieldRuleDraft> rules = new ArrayList<>();
        if (ruleMaps != null) {
            for (Map<String, Object> rm : ruleMaps) {
                rules.add(mapToRuleDraft(rm));
            }
        }

        HtmlConfigPreviewService.DetailPreviewResult result = previewService.previewDetail(detailSnapshotId, rules);

        if (result.getWarnings() != null && !result.getWarnings().isEmpty()) {
            return error("SNAPSHOT_NOT_FOUND", String.join("; ", result.getWarnings()), List.of());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fields", result.getFields());
        data.put("contentLength", result.getContentLength());
        data.put("contentPreview", result.getContentPreview());
        data.put("fieldStats", result.getFieldStats());
        data.put("warnings", result.getWarnings() != null ? result.getWarnings() : List.of());
        return ok(data);
    }

    // ==================== 8.8 POST /sources（阶段 B） ====================

    @PostMapping("/sources")
    public ResponseEntity<Map<String, Object>> createSource(@RequestBody HtmlConfigSaveRequest req) {
        // 校验
        List<Map<String, String>> errors = persistenceService.validate(req);
        if (!errors.isEmpty()) {
            return error("VALIDATION_ERROR", errors.get(0).get("message"), errors);
        }

        try {
            Long dsId = persistenceService.saveFullConfig(req);
            log.info("[sources] 保存成功 dataSourceId={}", dsId);
            return ok(Map.of("id", dsId));
        } catch (Exception e) {
            log.error("[sources] 保存失败", e);
            return error("INTERNAL_ERROR", "保存失败: " + e.getMessage(), List.of());
        }
    }

    // ==================== 8.9 POST /sources/{id}/run（阶段 B） ====================

    @PostMapping("/sources/{id:\\d+}/run")
    public ResponseEntity<Map<String, Object>> runSource(@PathVariable Long id) {
        try {
            HtmlConfigPersistenceService.RunResult result = persistenceService.runNow(id);
            if (!result.isSuccess()) {
                return error("INTERNAL_ERROR", result.getError(), List.of());
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fetchedCount", result.getFetchedCount());
            data.put("elapsedMs", result.getElapsedMs());
            data.put("recentDocs", result.getRecentDocs());
            return ok(data);
        } catch (Exception e) {
            log.error("[run] 试跑失败 dataSourceId={}", id, e);
            return error("INTERNAL_ERROR", "试跑失败: " + e.getMessage(), List.of());
        }
    }

    // ---- 辅助方法 ----

    private Map<String, String> err(String field, String code, String message) {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("field", field);
        e.put("code", code);
        e.put("message", message);
        return e;
    }

    private ClickedElementInfo mapToClick(Map<String, Object> m) {
        if (m == null) return null;
        ClickedElementInfo c = new ClickedElementInfo();
        c.setTag((String) m.get("tag"));
        c.setId((String) m.get("id"));
        @SuppressWarnings("unchecked")
        List<String> classNames = (List<String>) m.get("classNames");
        c.setClassNames(classNames);
        @SuppressWarnings("unchecked")
        Map<String, String> attrs = (Map<String, String>) m.get("attributes");
        c.setAttributes(attrs);
        c.setInnerText((String) m.get("innerText"));
        c.setInnerHtml((String) m.get("innerHtml"));
        c.setOuterHtml((String) m.get("outerHtml"));
        @SuppressWarnings("unchecked")
        List<Integer> indexPathList = (List<Integer>) m.get("indexPath");
        if (indexPathList != null) {
            c.setIndexPath(indexPathList.stream().mapToInt(Integer::intValue).toArray());
        }
        c.setCssPath((String) m.get("cssPath"));
        c.setComputedVisible(m.get("computedVisible") instanceof Boolean b ? b : true);

        @SuppressWarnings("unchecked")
        Map<String, Object> bm = (Map<String, Object>) m.get("bounding");
        if (bm != null) {
            ClickedElementInfo.Bounding b = new ClickedElementInfo.Bounding();
            b.setX(((Number) bm.getOrDefault("x", 0)).intValue());
            b.setY(((Number) bm.getOrDefault("y", 0)).intValue());
            b.setWidth(((Number) bm.getOrDefault("width", 0)).intValue());
            b.setHeight(((Number) bm.getOrDefault("height", 0)).intValue());
            c.setBounding(b);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candList = (List<Map<String, Object>>) m.get("extractableCandidates");
        if (candList != null) {
            List<ClickedElementInfo.ExtractableCandidate> cands = new ArrayList<>();
            for (Map<String, Object> cm : candList) {
                ClickedElementInfo.ExtractableCandidate ec = new ClickedElementInfo.ExtractableCandidate();
                ec.setType((String) cm.get("type"));
                ec.setAttrName((String) cm.get("attrName"));
                ec.setValue((String) cm.get("value"));
                ec.setHidden(cm.get("hidden") instanceof Boolean h && h);
                cands.add(ec);
            }
            c.setExtractableCandidates(cands);
        }
        return c;
    }

    private Map<String, Object> mapSelectorCandidate(SelectorCandidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("selector", c.getSelector());
        m.put("itemCount", c.getItemCount());
        m.put("confidence", Math.round(c.getConfidence() * 100.0) / 100.0);
        m.put("regionContainerSelector", c.getRegionContainerSelector() != null ? c.getRegionContainerSelector() : "");
        return m;
    }

    private Map<String, Object> mapFieldRuleCandidate(HtmlSelectorGenerator.FieldRuleCandidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("selector", c.getSelector());
        m.put("valueType", c.getValueType());
        m.put("attrName", c.getAttrName());
        m.put("confidence", Math.round(c.getConfidence() * 100.0) / 100.0);
        return m;
    }

    private FieldRuleDraft mapToRuleDraft(Map<String, Object> m) {
        return FieldRuleDraft.builder()
                .fieldName((String) m.get("fieldName"))
                .selector((String) m.get("selector"))
                .valueType((String) m.get("valueType"))
                .attrName((String) m.get("attrName"))
                .constValue((String) m.get("constValue"))
                .regexPattern((String) m.get("regexPattern"))
                .dateFormat((String) m.get("dateFormat"))
                .sortOrder(m.get("sortOrder") instanceof Number n ? n.intValue() : 0)
                .build();
    }
}
