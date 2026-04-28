package com.policyradar.sources;

import com.policyradar.persistence.entity.PolicyDataSource;

import java.util.List;

/**
 * 数据源执行器接口
 *
 * 定义通用的数据源抓取接口，将"接入方式"与"具体源"解耦
 */
public interface SourceRunner {

    /**
     * 获取支持的数据源类型
     *
     * @return 类型标识，如 "RSS"/"HTTP_JSON"/"HTML"/"PY_SCRIPT"
     */
    String type();

    /**
     * 执行数据抓取
     *
     * @param dataSource 数据源配置
     * @param context 抓取上下文
     * @return 抓取到的原始文档列表
     */
    List<RawDoc> fetch(PolicyDataSource dataSource, FetchContext context);
}