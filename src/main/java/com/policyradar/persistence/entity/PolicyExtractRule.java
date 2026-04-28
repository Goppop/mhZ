package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段提取规则。
 *
 * 每条规则描述一个 RawDoc 字段如何从当前 item 或详情页 Document 中取值。
 */
@Data
@TableName("policy_extract_rule")
public class PolicyExtractRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 policy_crawl_page.id
     */
    private Long pageId;

    /**
     * RawDoc 字段名：title/url/publishDate/summary/content/issuingAgency/documentNumber
     */
    private String fieldName;
    /**
     * 提取范围：ITEM/DETAIL
     */
    private String scope;

    /**
     * CSS 选择器；为空时表示使用当前根节点
     */
    private String selector;

    /**
     * 值类型：TEXT/ATTR/HTML/CONST/REGEX
     */
    private String valueType;

    /**
     * value_type=ATTR 时的属性名，如 href/title
     */
    private String attrName;

    /**
     * value_type=CONST 时的常量值
     */
    private String constValue;
    /**
     * value_type=ATTR 时的属性名，如 href/title；value_type=REGEX 时的正则表达式
     */
    private String regexPattern;

    /**
     *  正则提取表达式，默认取第一个分组或完整匹配
     */
    private String dateFormat;

    /**
     * 是否必填；为 true 时提取失败将导致整个 Document 失败并被丢弃
     */
    private Boolean required;

    /**
     * 同字段多规则兜底分组
     */
    private String fallbackGroup;

    /**
     *  同字段内尝试顺序
     */
    private Integer sortOrder;

    /**
     * 是否启用
     */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
