package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyExtractRule;
import lombok.Builder;
import lombok.Data;

/**
 * 字段规则执行结果。
 */
@Data
@Builder
public class ExtractedField {

    private String fieldName;

    private String value;

    private PolicyExtractRule rule;
}
