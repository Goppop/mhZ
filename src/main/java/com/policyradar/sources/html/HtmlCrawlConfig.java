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
}
