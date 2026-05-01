package com.policyradar.sources.html.preview;

import com.policyradar.sources.html.FieldRuleDraft;
import com.policyradar.sources.html.PaginationDraft;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 预览服务 —— 将规则草稿翻译为对 HtmlExtractionEngine 的调用并组装预览结果。
 *
 * PRD V2 第 9.4 节。
 */
public interface HtmlConfigPreviewService {

    /**
     * 跑列表页全字段预览。
     *
     * @param snapshotId   HTML 快照 ID
     * @param itemSelector 列表项 CSS 选择器
     * @param rules        全部字段规则
     * @param pagination   分页规则（null = 只跑当前页）
     */
    ListPreviewResult previewList(
            String snapshotId,
            String itemSelector,
            List<FieldRuleDraft> rules,
            PaginationDraft pagination);

    /**
     * 跑详情页预览（单页、单字段）。
     */
    DetailPreviewResult previewDetail(
            String detailSnapshotId,
            List<FieldRuleDraft> rules);

    // ================================================================
    // 结果模型
    // ================================================================

    @Data
    class ListPreviewResult {
        private int itemCount;
        private List<SampleItem> samples;
        private Map<String, FieldStat> fieldStats;
        private PaginationPreview paginationPreview;
        private List<String> warnings;
    }

    @Data
    class DetailPreviewResult {
        private Map<String, SampleFieldValue> fields;
        private int contentLength;
        private String contentPreview;
        private Map<String, FieldStat> fieldStats;
        private List<String> warnings;
    }

    @Data
    class SampleItem {
        private int itemIndex;
        private Map<String, SampleFieldValue> fields;
    }

    @Data
    class SampleFieldValue {
        private String value;
        private String raw;
        private List<String> warnings;
    }

    @Data
    class FieldStat {
        private double hitRate;
        private int blankCount;
    }

    @Data
    class PaginationPreview {
        private List<String> urls;
        private List<Integer> perPageItemCount;
    }
}
