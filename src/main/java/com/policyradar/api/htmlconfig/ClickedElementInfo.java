package com.policyradar.api.htmlconfig;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * PRD V2 第 11.1 节 — 前端点选元素信息。
 */
@Data
public class ClickedElementInfo {
    private String tag;
    private String id;
    private List<String> classNames;
    private Map<String, String> attributes;
    private String innerText;
    private String innerHtml;
    private String outerHtml;
    private int[] indexPath;
    private String cssPath;
    private Bounding bounding;
    private boolean computedVisible;
    private List<ExtractableCandidate> extractableCandidates;
    private TruncatedMeta __truncated;

    @Data
    public static class Bounding {
        private int x, y, width, height;
    }

    @Data
    public static class ExtractableCandidate {
        private String type;   // TEXT / ATTR / HTML
        private String attrName;
        private String value;
        private boolean hidden;
    }

    @Data
    public static class TruncatedMeta {
        private int innerText;
        private int innerHtml;
        private int outerHtml;
    }

    /**
     * 按 indexPath 在 Jsoup Document 中定位元素。
     * 对齐前端 sanitize 后的 DOM（移除 script 标签，从 &lt;html&gt; 开始计算索引）。
     *
     * 注意：此方法会修改传入的 Document（移除所有 script 元素）。
     */
    public org.jsoup.nodes.Element resolve(org.jsoup.nodes.Document doc) {
        doc.select("script").remove();
        org.jsoup.nodes.Element el = doc.selectFirst("html");
        if (el == null) return null;
        for (int idx : indexPath) {
            if (idx >= el.childrenSize()) return null;
            el = el.child(idx);
        }
        return el;
    }
}
