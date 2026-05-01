package com.policyradar.sources.html.selector;

import com.policyradar.api.htmlconfig.ClickedElementInfo;
import com.policyradar.persistence.entity.PolicyExtractRule;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * PRD V2 第 9.3 节 — 选择器生成器接口。
 */
public interface HtmlSelectorGenerator {

    ItemSelectorResult suggestItemSelector(
            String html,
            ClickedElementInfo click,
            int[] regionHintIndexPath);

    FieldRuleResult suggestFieldRule(
            String html,
            ClickedElementInfo click,
            FieldContext context);

    @Data
    @Builder
    class ItemSelectorResult {
        private SelectorCandidate primary;
        private List<SelectorCandidate> candidates;
        private List<SampleItem> sampleItems;
        private List<String> warnings;
    }

    @Data
    @Builder
    class FieldRuleCandidate {
        private String selector;
        private String valueType;
        private String attrName;
        private double confidence;
    }

    @Data
    @Builder
    class FieldRuleResult {
        private FieldRuleCandidate primary;
        private List<FieldRuleCandidate> candidates;
        private FieldPreview preview;
        private List<String> warnings;
    }

    @Data
    @Builder
    class SampleItem {
        private int index;
        private String textPreview;
        private int[] indexPath;
    }

    @Data
    @Builder
    class FieldContext {
        private String fieldName;
        private String pageRole;
        private String itemSelector;
        private List<PolicyExtractRule> currentRules;
    }

    @Data
    @Builder
    class FieldPreview {
        private FieldStats fieldStats;
        private List<FieldSample> samples;
    }

    @Data
    @Builder
    class FieldStats {
        private double hitRate;
        private int blankCount;
        private int successCount;
    }

    @Data
    @Builder
    class FieldSample {
        private int itemIndex;
        private String value;
        private String raw;
        private List<String> warnings;
    }
}
