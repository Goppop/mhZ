package com.policyradar.search;

import com.policyradar.persistence.entity.PolicyKeyword;

import java.util.List;

/**
 * 搜索 / 检索源适配器接口
 *
 * 每个实现类对应一个具体的检索入口（政府站内搜、必应 API、某部委公告列表…），
 * 负责把关键词组翻译成该引擎的查询语法，返回候选 URL 列表。
 *
 * <p>新增检索源 = 写一个实现类（30~80 行）+ policy_data_source 加一行 SQL，
 * 不动任何主链路代码。</p>
 *
 * <p>强反爬 / JS 渲染的场景使用 PyScriptRunner + scripts/search/xxx.py 替代，
 * 无需实现本接口。</p>
 */
public interface SearchProvider {

    /**
     * 检索器唯一名称，对应 policy_data_source.config 中的 "provider" 字段
     * 示例: "gov_cn", "miit_cn", "bing"
     */
    String name();

    /**
     * 执行搜索，返回候选 URL 列表
     *
     * @param keyword 关键词组（PolicyKeyword 的完整实体，包含 keyword/includeAny/includeAll/excludeAny）
     * @param ctx     搜索上下文（时间窗、UA 等）
     * @return 候选 URL 列表，空列表表示无结果
     */
    List<UrlCandidate> search(PolicyKeyword keyword, SearchContext ctx);

    /**
     * 该引擎是否支持布尔查询语法（AND/OR/NOT）
     * 返回 false 时 KeywordToQueryAdapter 会降级为单词搜索，
     * 由 Pipeline 中的 KeywordMatcher 做二次精筛。
     */
    default boolean supportsBooleanQuery() {
        return false;
    }
}
