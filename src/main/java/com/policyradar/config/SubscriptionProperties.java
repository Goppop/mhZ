package com.policyradar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订阅配置属性
 *
 * 从 application.yml 读取订阅配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "policy-radar.subscriptions")
public class SubscriptionProperties {

    private boolean enabled;

    private List<PresetSubscription> presets;

    @Data
    public static class PresetSubscription {
        private String name;
        private String keyword;
        private String includeAny;
        private String matchFields;
    }
}