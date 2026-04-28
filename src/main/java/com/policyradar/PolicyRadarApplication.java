package com.policyradar;

import com.policyradar.config.SubscriptionProperties;
import com.policyradar.infra.CrawlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        SubscriptionProperties.class,
        CrawlProperties.class,
})
public class PolicyRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyRadarApplication.class, args);
    }

}