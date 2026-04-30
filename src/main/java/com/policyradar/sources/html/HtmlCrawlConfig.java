package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyCrawlPage;
import com.policyradar.persistence.entity.PolicyExtractRule;
import com.policyradar.persistence.entity.PolicyPaginationRule;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单个 HTML 数据源的结构化爬取配置。
 */
@Data
@Builder
public class HtmlCrawlConfig {

    private Long dataSourceId;

    private PolicyCrawlPage listPage;

    private PolicyCrawlPage detailPage;

    private Map<Long, List<PolicyExtractRule>> rulesByPageId;

    private PolicyPaginationRule paginationRule;

    public boolean hasStructuredConfig() {
        return listPage != null && listPage.getId() != null;
    }

    public Optional<PolicyCrawlPage> detailPage() {
        return Optional.ofNullable(detailPage);
    }

    public List<PolicyExtractRule> rulesFor(PolicyCrawlPage page) {
        if (page == null || page.getId() == null || rulesByPageId == null) {
            return Collections.emptyList();
        }
        return rulesByPageId.getOrDefault(page.getId(), Collections.emptyList());
    }

    /**
     * 返回列表页规则，等价于 rulesFor(getListPage())。
     */
    public List<PolicyExtractRule> getListRules() {
        return rulesFor(getListPage());
    }

    /**
     * 返回详情页规则，无详情页时返回空列表。
     */
    public List<PolicyExtractRule> getDetailRules() {
        PolicyCrawlPage dp = getDetailPage();
        if (dp == null) {
            return Collections.emptyList();
        }
        return rulesFor(dp);
    }

    /**
     * 是否有详情页配置：详情页存在且有关联规则。
     */
    public boolean hasDetail() {
        if (getDetailPage() == null) {
            return false;
        }
        return !getDetailRules().isEmpty();
    }

    /**
     * 列表页请求超时毫秒数，未配置时默认 15000。
     */
    public int getListTimeoutMs() {
        PolicyCrawlPage lp = getListPage();
        if (lp == null || lp.getTimeoutMs() == null) {
            return 15_000;
        }
        return lp.getTimeoutMs();
    }

    /**
     * 详情页请求超时毫秒数，未配置时默认 15000。
     */
    public int getDetailTimeoutMs() {
        Optional<PolicyCrawlPage> dpOpt = detailPage();
        if (dpOpt.isEmpty()) {
            return 15_000;
        }
        PolicyCrawlPage dp = dpOpt.get();
        if (dp.getTimeoutMs() == null) {
            return 15_000;
        }
        return dp.getTimeoutMs();
    }
}
