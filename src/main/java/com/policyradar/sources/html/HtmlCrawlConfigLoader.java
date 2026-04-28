package com.policyradar.sources.html;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.policyradar.persistence.entity.PolicyCrawlPage;
import com.policyradar.persistence.entity.PolicyExtractRule;
import com.policyradar.persistence.entity.PolicyPaginationRule;
import com.policyradar.persistence.mapper.PolicyCrawlPageMapper;
import com.policyradar.persistence.mapper.PolicyExtractRuleMapper;
import com.policyradar.persistence.mapper.PolicyPaginationRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从结构化配置表加载 HTML 爬取规则。
 */
@Component
@RequiredArgsConstructor
public class HtmlCrawlConfigLoader {

    private final PolicyCrawlPageMapper pageMapper;
    private final PolicyExtractRuleMapper ruleMapper;
    private final PolicyPaginationRuleMapper paginationRuleMapper;

    public HtmlCrawlConfig load(Long dataSourceId) {
        List<PolicyCrawlPage> pages = pageMapper.selectList(
                new LambdaQueryWrapper<PolicyCrawlPage>()
                        .eq(PolicyCrawlPage::getDataSourceId, dataSourceId)
                        .eq(PolicyCrawlPage::getEnabled, true)
                        .orderByAsc(PolicyCrawlPage::getSortOrder)
                        .orderByAsc(PolicyCrawlPage::getId)
        );

        if (pages.isEmpty()) {
            return HtmlCrawlConfig.builder()
                    .dataSourceId(dataSourceId)
                    .rulesByPageId(Collections.emptyMap())
                    .build();
        }

        PolicyCrawlPage listPage = firstByRole(pages, "LIST");
        PolicyCrawlPage detailPage = firstByRole(pages, "DETAIL");

        List<Long> pageIds = pages.stream()
                .map(PolicyCrawlPage::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<PolicyExtractRule>> rulesByPageId = loadRules(pageIds);
        PolicyPaginationRule paginationRule = listPage == null ? null : loadPaginationRule(listPage.getId());

        return HtmlCrawlConfig.builder()
                .dataSourceId(dataSourceId)
                .listPage(listPage)
                .detailPage(detailPage)
                .rulesByPageId(rulesByPageId)
                .paginationRule(paginationRule)
                .build();
    }

    private PolicyCrawlPage firstByRole(List<PolicyCrawlPage> pages, String role) {
        return pages.stream()
                .filter(page -> role.equalsIgnoreCase(page.getPageRole()))
                .findFirst()
                .orElse(null);
    }

    private Map<Long, List<PolicyExtractRule>> loadRules(List<Long> pageIds) {
        if (pageIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<PolicyExtractRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<PolicyExtractRule>()
                        .in(PolicyExtractRule::getPageId, pageIds)
                        .eq(PolicyExtractRule::getEnabled, true)
                        .orderByAsc(PolicyExtractRule::getFieldName)
                        .orderByAsc(PolicyExtractRule::getSortOrder)
                        .orderByAsc(PolicyExtractRule::getId)
        );

        Comparator<PolicyExtractRule> ruleOrder = Comparator
                .comparing(PolicyExtractRule::getFieldName, Comparator.nullsLast(String::compareTo))
                .thenComparing(rule -> defaultInt(rule.getSortOrder()))
                .thenComparing(rule -> defaultLong(rule.getId()));

        return rules.stream()
                .sorted(ruleOrder)
                .collect(Collectors.groupingBy(PolicyExtractRule::getPageId));
    }

    private PolicyPaginationRule loadPaginationRule(Long pageId) {
        List<PolicyPaginationRule> rules = paginationRuleMapper.selectList(
                new LambdaQueryWrapper<PolicyPaginationRule>()
                        .eq(PolicyPaginationRule::getPageId, pageId)
                        .eq(PolicyPaginationRule::getEnabled, true)
                        .orderByAsc(PolicyPaginationRule::getId)
        );
        return rules.isEmpty() ? null : rules.get(0);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
