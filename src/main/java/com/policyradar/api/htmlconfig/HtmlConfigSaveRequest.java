package com.policyradar.api.htmlconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * PRD V2 8.8.1 — POST /sources 请求体。
 * 前端 SelectionState 序列化而来。
 */
@Data
@NoArgsConstructor
public class HtmlConfigSaveRequest {

    private DataSourceMeta dataSource;
    private ListPageMeta listPage;
    private List<RuleDraft> listRules;
    private DetailPageMeta detailPage;
    private List<RuleDraft> detailRules;
    private PaginationMeta paginationRule;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSourceMeta {
        private String name;
        private String cronExpr;
        private boolean enabled = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListPageMeta {
        private String url;
        private String itemSelector;
        private Map<String, String> headers;
        private int timeoutMs = 15000;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailPageMeta {
        private Map<String, String> headers;
        private int timeoutMs = 15000;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RuleDraft {
        private String fieldName;
        private String selector;
        private String valueType;
        private String attrName;
        private String constValue;
        private String regexPattern;
        private String dateFormat;
        private int sortOrder;
        private boolean required;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaginationMeta {
        private String mode;
        private String urlTemplate;
        private int startPage = 1;
        private int maxPages = 1;
    }
}
