package com.policyradar.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索上下文
 *
 * 传递给 SearchProvider.search() 的参数封装，
 * 包括时间窗、结果上限、UA、代理等。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchContext {

    /** 搜索时间窗（天），只拿最近 N 天发布的结果 */
    @Builder.Default
    private int sinceDays = 7;

    /** 每关键词最多返回候选数 */
    @Builder.Default
    private int maxResults = 30;

    /** User-Agent */
    private String userAgent;

    /** 代理地址（可为 null） */
    private String proxyUrl;
}
