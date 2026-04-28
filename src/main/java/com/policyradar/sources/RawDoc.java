package com.policyradar.sources;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/**
 * 原始文档数据结构
 *
 * 从数据源抓取到的原始数据，尚未经过规范化处理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawDoc {

    /**
     * 标题
     */
    private String title;

    /**
     * 原文链接
     */
    private String url;

    /**
     * 来源名称
     */
    private String source;

    /**
     * 发布日期
     */
    private LocalDate publishDate;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 正文内容（可能包含 HTML
     */
    private String content;

    /**
     * 发布机构
     */
    private String issuingAgency;

    /**
     * 文号
     */
    private String documentNumber;

    /**
     * 元数据（灵活扩展字段
     */
    private Map<String, Object> metadata;
}