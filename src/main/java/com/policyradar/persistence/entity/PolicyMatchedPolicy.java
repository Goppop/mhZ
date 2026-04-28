package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 关键词匹配结果表实体类
 *
 * 存储政策文档与关键词匹配的结果
 */
@Data
@TableName("policy_matched_policy")
public class PolicyMatchedPolicy {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联文档ID
     */
    private Long documentId;

    /**
     * 关联关键词ID
     */
    private Long keywordId;

    /**
     * 匹配到的关键词
     */
    private String matchedKeyword;

    /**
     * 匹配到的字段
     */
    private String matchedFields;

    /**
     * 是否已推送
     */
    private Boolean notified;

    /**
     * 匹配时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime matchedAt;
}