package com.policyradar.sources.html;

import lombok.Builder;
import lombok.Data;

/**
 * 分页规则草稿 —— 前端传过来的分页配置。
 */
@Data
@Builder
public class PaginationDraft {
    /** NONE / URL_TEMPLATE */
    private String mode;
    /** URL 模板（含 {page} 占位符），mode=URL_TEMPLATE 时必填 */
    private String urlTemplate;
    /** 起始页码，默认 1 */
    @Builder.Default
    private int startPage = 1;
    /** 最大翻页数，默认 1，上限 100 */
    @Builder.Default
    private int maxPages = 1;
}
