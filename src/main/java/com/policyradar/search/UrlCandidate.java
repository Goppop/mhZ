package com.policyradar.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 搜索结果候选 URL
 *
 * SearchProvider.search() 的输出单元，代表一条搜索命中。
 * 只含元信息，不含正文 —— 正文由 FrontierConsumer 后续抓取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlCandidate {

    /** 候选页面完整 URL */
    private String url;

    /** 搜索结果标题（片段，用于调试和前期过滤） */
    private String titleSnippet;

    /** 产出此候选的检索器名称，如 gov_cn */
    private String provider;

    /** 关联的 policy_keyword.id */
    private Long keywordId;

    /** 搜索结果中提示的发布日期（可能为 null） */
    private LocalDate hintedPublishDate;
}
