package com.policyradar.sources.html;

import java.util.Map;

/**
 * HTML 静态页面请求客户端。
 *
 * 封装 HTTP 请求逻辑，与具体实现（Jsoup / Playwright）解耦。
 * 阶段一只提供 JsoupRequestClient 实现。
 */
public interface HtmlRequestClient {

    /**
     * 请求静态 HTML 页面。
     *
     * @param url       目标 URL
     * @param headers   自定义请求头（可为空 Map）
     * @param timeoutMs 超时毫秒数
     * @return 页面响应，包含 HTML 正文、最终 URL、状态码、错误信息
     */
    HtmlPageResponse fetch(String url, Map<String, String> headers, int timeoutMs);
}