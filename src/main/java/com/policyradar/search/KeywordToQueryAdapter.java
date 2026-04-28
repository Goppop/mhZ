package com.policyradar.search;

import com.policyradar.persistence.entity.PolicyKeyword;
import org.springframework.stereotype.Component;

/**
 * 关键词组 → 搜索引擎 query 适配器
 *
 * 各搜索引擎对布尔查询能力差异大：
 * - 政府站内搜通常只支持单关键词
 * - 必应/Google 支持 AND/OR/NOT/site:
 *
 * 本适配器按引擎能力做降级：
 * - 不支持布尔 → 取主词或 includeAny[0]（Pipeline 二次精筛兜底）
 * - 支持布尔    → 拼 (A OR B) AND (C) NOT (D)
 */
@Component
public class KeywordToQueryAdapter {

    /**
     * 为指定引擎生成最优 query 字符串
     *
     * @param keyword         关键词组
     * @param supportsBool    引擎是否支持布尔查询
     * @return 搜索查询字符串
     */
    public String buildQuery(PolicyKeyword keyword, boolean supportsBool) {
        if (supportsBool) {
            return buildBooleanQuery(keyword);
        }
        return buildSimpleQuery(keyword);
    }

    /**
     * 简单模式：取主词，若无则取 includeAny 第一个
     * 搜索精度偏低，由 KeywordMatcher 在 Pipeline 二次过滤。
     */
    public String buildSimpleQuery(PolicyKeyword keyword) {
        if (keyword.getKeyword() != null && !keyword.getKeyword().isBlank()) {
            return keyword.getKeyword().trim();
        }
        if (keyword.getIncludeAny() != null && !keyword.getIncludeAny().isBlank()) {
            String[] parts = keyword.getIncludeAny().split(",");
            if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                return parts[0].trim();
            }
        }
        if (keyword.getName() != null && !keyword.getName().isBlank()) {
            return keyword.getName().trim();
        }
        return "";
    }

    /**
     * 布尔模式：拼 (A OR B) AND (C) NOT (D)
     * 适用于必应等支持布尔查询的引擎。
     */
    public String buildBooleanQuery(PolicyKeyword keyword) {
        StringBuilder sb = new StringBuilder();

        // 主词
        if (keyword.getKeyword() != null && !keyword.getKeyword().isBlank()) {
            sb.append(keyword.getKeyword().trim());
        }

        // includeAny → (A OR B OR C)
        if (keyword.getIncludeAny() != null && !keyword.getIncludeAny().isBlank()) {
            String[] parts = keyword.getIncludeAny().split(",");
            if (parts.length > 0) {
                if (!sb.isEmpty()) sb.append(" AND ");
                sb.append("(");
                for (int i = 0; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (!p.isEmpty()) {
                        if (i > 0) sb.append(" OR ");
                        sb.append(p);
                    }
                }
                sb.append(")");
            }
        }

        // includeAll → AND A AND B
        if (keyword.getIncludeAll() != null && !keyword.getIncludeAll().isBlank()) {
            for (String part : keyword.getIncludeAll().split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" AND ");
                    sb.append(p);
                }
            }
        }

        // excludeAny → NOT A NOT B
        if (keyword.getExcludeAny() != null && !keyword.getExcludeAny().isBlank()) {
            for (String part : keyword.getExcludeAny().split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" NOT ");
                    else sb.append("NOT ");
                    sb.append(p);
                }
            }
        }

        return sb.toString();
    }
}
