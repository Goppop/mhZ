package com.policyradar.config;

import com.policyradar.persistence.entity.PolicyKeyword;
import com.policyradar.persistence.mapper.PolicyKeywordMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订阅数据初始化
 *
 * 应用启动时初始化预设的关键词订阅
 */
@Component
@ConditionalOnProperty(name = "policy-radar.subscriptions.enabled", havingValue = "true")
public class SubscriptionInitializer {

    private final PolicyKeywordMapper policyKeywordMapper;
    private final SubscriptionProperties subscriptionProperties;

    @Autowired
    public SubscriptionInitializer(PolicyKeywordMapper policyKeywordMapper, SubscriptionProperties subscriptionProperties) {
        this.policyKeywordMapper = policyKeywordMapper;
        this.subscriptionProperties = subscriptionProperties;
    }

    @PostConstruct
    public void init() {
        List<SubscriptionProperties.PresetSubscription> presetSubscriptions = subscriptionProperties.getPresets();
        if (presetSubscriptions != null && !presetSubscriptions.isEmpty()) {
            for (SubscriptionProperties.PresetSubscription preset : presetSubscriptions) {
                createOrUpdateSubscription(preset);
            }
        }
    }

    private void createOrUpdateSubscription(SubscriptionProperties.PresetSubscription preset) {
        PolicyKeyword existing = policyKeywordMapper.selectList(null).stream()
                .filter(k -> k.getName().equals(preset.getName()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // 更新现有订阅
            existing.setKeyword(preset.getKeyword());
            existing.setIncludeAny(preset.getIncludeAny());
            existing.setMatchFields(preset.getMatchFields());
            existing.setEnabled(true);
            policyKeywordMapper.updateById(existing);
            return;
        }

        // 创建新订阅
        PolicyKeyword newKeyword = new PolicyKeyword();
        newKeyword.setName(preset.getName());
        newKeyword.setKeyword(preset.getKeyword());
        newKeyword.setIncludeAny(preset.getIncludeAny());
        newKeyword.setMatchFields(preset.getMatchFields());
        newKeyword.setEnabled(true);
        policyKeywordMapper.insert(newKeyword);
    }
}