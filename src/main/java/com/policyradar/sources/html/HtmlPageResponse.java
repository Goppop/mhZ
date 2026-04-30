package com.policyradar.sources.html;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HTML 页面请求响应。
 *
 * 封装 HTTP 请求结果，包含页面正文和状态信息。
 * HtmlRunner 正式抓取和后续预览服务均使用此模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HtmlPageResponse {

    /** 页面 HTML 正文 */
    private String html;

    /** 重定向后的最终 URL */
    private String finalUrl;

    /** HTTP 状态码 */
    private int statusCode;

    /** &lt;title&gt; 标签内容 */
    private String title;

    /** 警告（如疑似 JS 渲染） */
    private List<String> warnings;

    /** 错误信息（成功时为 null） */
    private String error;

    /**
     * 是否成功：HTTP 状态码 2xx 且 error 为空且 html 非空。
     * 调用方应使用此方法而不是各自判断 error / statusCode。
     */
    public boolean isSuccess() {
        return error == null
                && statusCode >= 200 && statusCode < 300
                && html != null && !html.isEmpty();
    }
}