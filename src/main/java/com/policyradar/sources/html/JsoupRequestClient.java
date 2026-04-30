package com.policyradar.sources.html;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 Jsoup 的 HTML 请求客户端。
 *
 * 阶段一唯一实现，后续可新增 PlaywrightRequestClient 支持 JS 渲染。
 */
@Slf4j
@Component
public class JsoupRequestClient implements HtmlRequestClient {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private static final int MIN_TEXT_LENGTH_FOR_JS_WARNING = 200;

    @Override
    public HtmlPageResponse fetch(String url, Map<String, String> headers, int timeoutMs) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs 必须 > 0，当前值: " + timeoutMs);
        }

        String safeUrl = url.trim();
        validateUrl(safeUrl);

        if (headers == null) {
            headers = Collections.emptyMap();
        }

        log.info("请求页面: {}", maskUrl(safeUrl));

        try {
            Connection conn = Jsoup.connect(safeUrl)
                    .userAgent(UA)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .ignoreHttpErrors(true);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && !key.isBlank() && value != null) {
                    conn.header(key.trim(), value);
                }
            }

            Connection.Response response = conn.execute();
            Document doc = response.parse();

            String html = doc.html();
            List<String> warnings = new ArrayList<>();
            checkJsRendering(doc, warnings);
            String error = response.statusCode() >= 200 && response.statusCode() < 300
                    ? null
                    : "HTTP 状态码: " + response.statusCode();

            return HtmlPageResponse.builder()
                    .html(html)
                    .finalUrl(doc.location())
                    .statusCode(response.statusCode())
                    .title(doc.title())
                    .warnings(warnings)
                    .error(error)
                    .build();

        } catch (SocketTimeoutException e) {
            log.warn("请求超时: url={}, timeoutMs={}", maskUrl(safeUrl), timeoutMs);
            return HtmlPageResponse.builder()
                    .statusCode(0)
                    .error("请求超时: " + e.getMessage())
                    .build();
        } catch (IOException e) {
            log.error("请求失败: url={}", maskUrl(safeUrl), e);
            return HtmlPageResponse.builder()
                    .statusCode(0)
                    .error("请求失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 只允许 http 和 https 协议。
     */
    private void validateUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("不支持的协议，仅允许 http/https: " + maskUrl(url));
        }
    }

    /**
     * 检测疑似 JS 动态渲染页面：静态 HTML 中文本量极少。
     */
    private void checkJsRendering(Document doc, List<String> warnings) {
        String text = doc.text();
        if (text == null || text.length() < MIN_TEXT_LENGTH_FOR_JS_WARNING) {
            warnings.add("当前静态 HTML 中未发现明显内容，可能是 JS 动态渲染页面，第一版暂不支持");
        }
    }

    /**
     * 脱敏 URL，隐藏 query string 参数值。
     */
    private String maskUrl(String url) {
        if (url == null) {
            return "null";
        }
        int qIndex = url.indexOf('?');
        if (qIndex < 0) {
            return url;
        }
        StringBuilder masked = new StringBuilder(url.length());
        masked.append(url, 0, qIndex + 1);

        String query = url.substring(qIndex + 1);
        String[] params = query.split("&");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                masked.append('&');
            }
            String param = params[i];
            int eqIndex = param.indexOf('=');
            if (eqIndex > 0) {
                masked.append(param, 0, eqIndex + 1);
                masked.append("***");
            } else {
                masked.append(param);
            }
        }
        return masked.toString();
    }
}