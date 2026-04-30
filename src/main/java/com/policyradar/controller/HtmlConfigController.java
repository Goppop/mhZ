package com.policyradar.controller;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * HTML 爬虫配置工作台接口
 *
 * 阶段二前端通过此接口加载目标页面 HTML 快照，
 * 不执行 JS 渲染，仅返回静态 HTML。
 */
@Slf4j
@RestController
@RequestMapping("/api/html-config")
public class HtmlConfigController {

    @PostMapping("/load-page")
    public ResponseEntity<?> loadPage(@RequestBody LoadPageRequest req) {
        log.info("[HtmlConfig] 加载页面 url={}", req.url());
        Map<String, Object> resp = new HashMap<>();

        try {
            Connection conn = Jsoup.connect(req.url())
                    .timeout(req.timeoutMs() > 0 ? req.timeoutMs() : 15000)
                    .userAgent("Mozilla/5.0 (compatible; PolicyRadar/1.0)")
                    .followRedirects(true)
                    .maxBodySize(5 * 1024 * 1024); // 5MB max

            // 传递自定义 headers（可选）
            if (req.headers() != null) {
                for (Map.Entry<String, String> e : req.headers().entrySet()) {
                    conn.header(e.getKey(), e.getValue());
                }
            }

            Connection.Response response = conn.execute();
            String html = response.body();

            resp.put("html", html);
            resp.put("finalUrl", response.url().toString());
            resp.put("statusCode", response.statusCode());
            resp.put("title", response.parse().title());
            resp.put("warnings", List.of());
            resp.put("error", null);

            log.info("[HtmlConfig] 加载成功 status={} htmlLength={}", response.statusCode(), html.length());
        } catch (Exception e) {
            log.error("[HtmlConfig] 加载失败 url={} err={}", req.url(), e.getMessage());
            resp.put("html", null);
            resp.put("finalUrl", req.url());
            resp.put("statusCode", 0);
            resp.put("title", "");
            resp.put("warnings", List.of());
            resp.put("error", "页面加载失败: " + e.getMessage());
        }

        return ResponseEntity.ok(resp);
    }

    public record LoadPageRequest(
            String url,
            Map<String, String> headers,
            int timeoutMs
    ) {}
}
