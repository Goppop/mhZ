package com.policyradar.sources.html.preview;

import com.policyradar.persistence.entity.PolicyExtractRule;
import com.policyradar.sources.html.*;
import com.policyradar.sources.html.snapshot.HtmlSnapshot;
import com.policyradar.sources.html.snapshot.HtmlSnapshotCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 预览服务实现 —— 委托 HtmlExtractionEngine 跑提取并统计命中率。
 *
 * 不得在此类中重新实现字段提取逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HtmlConfigPreviewServiceImpl implements HtmlConfigPreviewService {

    private static final int MAX_SAMPLE_COUNT = 10;
    private static final int PAGINATION_PREVIEW_LIMIT = 3;

    private final HtmlSnapshotCache snapshotCache;
    private final HtmlExtractionEngine extractionEngine;
    private final HtmlRequestClient requestClient;

    // ================================================================
    // 列表预览
    // ================================================================

    @Override
    public ListPreviewResult previewList(
            String snapshotId, String itemSelector,
            List<FieldRuleDraft> rules, PaginationDraft pagination
    ) {
        HtmlSnapshot snap = snapshotCache.get(snapshotId);
        if (snap == null) {
            return buildError("snapshotId 已过期: " + snapshotId);
        }

        List<PolicyExtractRule> extractRules = toExtractRules(rules);
        List<ExtractedItem> items = extractionEngine.extractList(
                snap.getHtml(), itemSelector, extractRules, snap.getFinalUrl());

        ListPreviewResult result = new ListPreviewResult();
        result.setItemCount(items.size());
        result.setSamples(new ArrayList<>());
        result.setFieldStats(new LinkedHashMap<>());
        result.setWarnings(new ArrayList<>());

        // 收集所有字段名
        Set<String> fieldNames = new LinkedHashSet<>();
        for (FieldRuleDraft r : rules) {
            fieldNames.add(r.getFieldName());
        }

        // 计算每个字段的命中率
        for (String fn : fieldNames) {
            int hit = 0;
            int blank = 0;
            for (ExtractedItem item : items) {
                String val = item.getFields().get(fn);
                if (val != null && !val.isEmpty()) {
                    hit++;
                } else {
                    blank++;
                }
            }
            FieldStat stat = new FieldStat();
            stat.setHitRate(computeHitRate(hit, blank));
            stat.setBlankCount(blank);
            result.getFieldStats().put(fn, stat);
        }

        // 采样前 N 条
        int sampleLimit = Math.min(items.size(), MAX_SAMPLE_COUNT);
        for (int i = 0; i < sampleLimit; i++) {
            ExtractedItem item = items.get(i);
            SampleItem si = new SampleItem();
            si.setItemIndex(i);
            Map<String, SampleFieldValue> fields = new LinkedHashMap<>();
            for (String fn : fieldNames) {
                String val = item.getFields().get(fn);
                SampleFieldValue sfv = new SampleFieldValue();
                sfv.setValue(val);
                sfv.setRaw(val);
                sfv.setWarnings(List.of());
                fields.put(fn, sfv);
            }
            si.setFields(fields);
            result.getSamples().add(si);
        }

        // 分页预览
        if (pagination != null && "URL_TEMPLATE".equals(pagination.getMode())
                && pagination.getUrlTemplate() != null) {
            result.setPaginationPreview(buildPaginationPreview(
                    pagination, itemSelector, extractRules));
        }

        return result;
    }

    // ================================================================
    // 详情预览
    // ================================================================

    @Override
    public DetailPreviewResult previewDetail(
            String detailSnapshotId, List<FieldRuleDraft> rules
    ) {
        HtmlSnapshot snap = snapshotCache.get(detailSnapshotId);
        if (snap == null) {
            DetailPreviewResult r = new DetailPreviewResult();
            r.setWarnings(List.of("detailSnapshotId 已过期: " + detailSnapshotId));
            return r;
        }

        List<PolicyExtractRule> extractRules = toExtractRules(rules);
        ExtractedItem item = extractionEngine.extractDetail(
                snap.getHtml(), extractRules, snap.getFinalUrl());

        DetailPreviewResult result = new DetailPreviewResult();
        result.setFields(new LinkedHashMap<>());
        result.setFieldStats(new LinkedHashMap<>());
        result.setWarnings(new ArrayList<>());

        if (item != null) {
            for (FieldRuleDraft draft : rules) {
                String fieldName = draft.getFieldName();
                String val = item.getFields().get(fieldName);

                SampleFieldValue sfv = new SampleFieldValue();
                sfv.setValue(val);
                sfv.setRaw(val);
                sfv.setWarnings(List.of());
                result.getFields().put(fieldName, sfv);

                FieldStat stat = new FieldStat();
                boolean hasValue = val != null && !val.isEmpty();
                stat.setHitRate(hasValue ? 1.0 : 0.0);
                stat.setBlankCount(hasValue ? 0 : 1);
                result.getFieldStats().put(fieldName, stat);

                if ("content".equals(fieldName) && val != null) {
                    String plain = Jsoup.parse(val).text();
                    result.setContentLength(plain.length());
                    result.setContentPreview(truncate(plain, 1000));
                }
            }
        }

        return result;
    }

    // ================================================================
    // 私有方法
    // ================================================================

    /** 将 FieldRuleDraft 列表转换为 PolicyExtractRule（不入库） */
    private List<PolicyExtractRule> toExtractRules(List<FieldRuleDraft> drafts) {
        List<PolicyExtractRule> rules = new ArrayList<>();
        for (FieldRuleDraft d : drafts) {
            PolicyExtractRule r = new PolicyExtractRule();
            r.setFieldName(d.getFieldName());
            r.setSelector(d.getSelector());
            r.setValueType(d.getValueType());
            r.setAttrName(d.getAttrName());
            r.setConstValue(d.getConstValue());
            r.setRegexPattern(d.getRegexPattern());
            r.setDateFormat(d.getDateFormat());
            r.setSortOrder(d.getSortOrder());
            r.setEnabled(true);
            rules.add(r);
        }
        return rules;
    }

    private PaginationPreview buildPaginationPreview(
            PaginationDraft pagination, String itemSelector,
            List<PolicyExtractRule> rules
    ) {
        PaginationPreview pp = new PaginationPreview();
        pp.setUrls(new ArrayList<>());
        pp.setPerPageItemCount(new ArrayList<>());

        int maxPages = Math.min(pagination.getMaxPages(), PAGINATION_PREVIEW_LIMIT);
        for (int page = pagination.getStartPage();
             page < pagination.getStartPage() + maxPages;
             page++) {
            String url = pagination.getUrlTemplate()
                    .replace("{page}", String.valueOf(page));
            pp.getUrls().add(url);

            try {
                HtmlPageResponse pageResp = requestClient.fetch(url, Map.of(), 15000);
                if (pageResp.isSuccess()) {
                    List<ExtractedItem> pageItems = extractionEngine.extractList(
                            pageResp.getHtml(), itemSelector, rules, url);
                    pp.getPerPageItemCount().add(pageItems.size());
                } else {
                    pp.getPerPageItemCount().add(0);
                }
            } catch (Exception e) {
                pp.getPerPageItemCount().add(0);
                log.warn("[preview] 分页预览失败 url={}", url, e);
            }
        }
        return pp;
    }

    private static double computeHitRate(int hit, int blank) {
        int total = hit + blank;
        if (total == 0) return 0.0;
        return Math.round((double) hit / total * 100.0) / 100.0;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static ListPreviewResult buildError(String message) {
        ListPreviewResult r = new ListPreviewResult();
        r.setWarnings(List.of(message));
        return r;
    }
}
