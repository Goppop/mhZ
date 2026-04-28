package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 推送目标配置表实体类
 *
 * 存储政策推送通知的目标配置
 */
@Data
@TableName("policy_notification")
public class PolicyNotification {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 类型：PUSHPLUS/EMAIL/DINGTALK
     */
    private String type;

    /**
     * JSON配置（token、邮箱等）
     */
    private String config;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}