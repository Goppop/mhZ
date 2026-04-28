package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列表页分页配置。
 */
@Data
@TableName("policy_pagination_rule")
public class PolicyPaginationRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 policy_crawl_page.id
     */
    private Long pageId;

    /**
     * 翻页模式：NONE/URL_TEMPLATE/NEXT_SELECTOR
     */
    private String mode;

    /**
     *  URL_TEMPLATE 模式，如 https://example/list_{page}.html
     */
    private String urlTemplate;

    /**
     * NEXT_SELECTOR 模式的下一页链接选择器
     */
    private String nextSelector;

    /**
     * 起始页码，默认为 1
     */
    private Integer startPage;

    /**
     * 最大页数
     */
    private Integer maxPages;

    /**
     * 是否启用
     */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
