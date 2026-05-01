package com.policyradar.sources.html.selector;

import com.policyradar.api.htmlconfig.ClickedElementInfo;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 选择器生成器实现 —— PRD V2 第 9.3 / 12.1 节。
 *
 * 4 种基础策略按优先级合并：
 * 1. ClassComboStrategy       — class 组合
 * 2. ParentChildStrategy      — 祖先 + 子元素
 * 3. AttributePathStrategy    — data-* / role 属性
 * 4. SimplifiedPathStrategy   — 纯 tag（兜底）
 */
@Slf4j
@Component
public class HtmlSelectorGeneratorImpl implements HtmlSelectorGenerator {

    private static final double BASE_CONFIDENCE = 0.5;
    private static final double CLASS_ID_BONUS = 0.2;
    private static final double LABEL_CHAIN_BONUS = 0.2;
    private static final double SHORT_SELECTOR_BONUS = 0.1;
    private static final int MIN_ITEM_COUNT = 2;
    private static final int MAX_SAMPLE = 10;

    // ================================================================
    // 列表项选择器
    // ================================================================

    @Override
    public ItemSelectorResult suggestItemSelector(
            String html, ClickedElementInfo click, int[] regionHintIndexPath
    ) {
        Document doc = Jsoup.parse(html);
        Element target = resolveElement(doc, click);
        if (target == null) {
            log.warn("[suggest-item] resolveElement 失败 indexPath={} htmlLen={}",
                    fmtIndexPath(click), html.length());
            return ItemSelectorResult.builder()
                    .warnings(List.of("无法在 HTML 中定位点击元素"))
                    .build();
        }
        log.info("[suggest-item] resolveElement 成功 target=<{}> text={}",
                target.tagName(), truncate(target.text(), 80));

        // 区域容器（PRD 12.4）
        Element region = regionHintIndexPath != null
                ? resolveByIndexPath(doc, regionHintIndexPath)
                : findRegionContainer(target);
        if (region == null) region = doc.body();

        // 从点击的叶子元素向上找真正的重复项（点击 a → item 是 li）
        Element itemTarget = findItemElement(target, region);
        log.info("[suggest-item] target=<{}> → itemTarget=<{}> region=<{}>",
                target.tagName(),
                itemTarget != null ? itemTarget.tagName() : "null",
                region != null ? region.tagName() : "null");

        // 生成候选
        List<SelectorCandidate> allCandidates = new ArrayList<>();
        for (SelectorStrategy strategy : allStrategies()) {
            allCandidates.addAll(strategy.generate(itemTarget, region));
        }

        // 去重 → 验证命中 → 置信度评分 → 排序
        List<SelectorCandidate> validated = dedupeValidateAndSort(allCandidates, doc, region);

        if (validated.isEmpty()) {
            return ItemSelectorResult.builder()
                    .warnings(List.of("无法生成命中 ≥ 2 的选择器候选"))
                    .build();
        }

        SelectorCandidate primary = validated.get(0);
        List<SelectorCandidate> candidates = validated.size() > 1
                ? validated.subList(1, Math.min(validated.size(), 5))
                : List.of();

        // 采样
        Elements items = region.select(primary.getSelector());
        List<SampleItem> samples = new ArrayList<>();
        int limit = Math.min(items.size(), MAX_SAMPLE);
        for (int i = 0; i < limit; i++) {
            Element item = items.get(i);
            samples.add(SampleItem.builder()
                    .index(i)
                    .textPreview(truncate(item.text(), 80))
                    .indexPath(buildIndexPath(item))
                    .build());
        }

        return ItemSelectorResult.builder()
                .primary(primary).candidates(candidates)
                .sampleItems(samples).warnings(List.of())
                .build();
    }

    // ================================================================
    // 字段规则
    // ================================================================

    @Override
    public FieldRuleResult suggestFieldRule(
            String html, ClickedElementInfo click, FieldContext context
    ) {
        Document doc = Jsoup.parse(html);
        Element target = resolveElement(doc, click);
        if (target == null) {
            return FieldRuleResult.builder()
                    .warnings(List.of("无法定位元素")).build();
        }

        List<SelectorCandidate> allCandidates = new ArrayList<>();

        if ("LIST".equals(context.getPageRole())) {
            // 列表字段：候选生成在 item 容器内
            String itemSelector = context.getItemSelector();
            if (itemSelector == null || itemSelector.isEmpty()) {
                return FieldRuleResult.builder()
                        .warnings(List.of("缺少 itemSelector")).build();
            }
            Elements items = doc.select(itemSelector);
            if (items.isEmpty()) {
                return FieldRuleResult.builder()
                        .warnings(List.of("itemSelector 无命中")).build();
            }

            Element sampleItem = items.first();
            for (SelectorStrategy strategy : allStrategies()) {
                for (SelectorCandidate c : strategy.generate(target, sampleItem)) {
                    c.setRegionContainerSelector(itemSelector);
                    if (c.getSelector() != null && !c.getSelector().isEmpty()) {
                        allCandidates.add(c);
                    }
                }
            }
        } else {
            // 详情字段：候选生成在整个 Document 上
            for (SelectorStrategy strategy : allStrategies()) {
                allCandidates.addAll(strategy.generate(target, doc.body()));
            }
        }

        // 去重
        Set<String> seen = new HashSet<>();
        List<SelectorCandidate> unique = new ArrayList<>();
        for (SelectorCandidate c : allCandidates) {
            if (seen.add(c.getSelector())) unique.add(c);
        }

        if (unique.isEmpty()) {
            return FieldRuleResult.builder()
                    .warnings(List.of("无法生成字段选择器候选")).build();
        }

        // 构建 FieldRuleCandidate（含 valueType）
        List<FieldRuleCandidate> fieldCandidates = new ArrayList<>();
        for (SelectorCandidate c : unique) {
            Elements els = doc.select(c.getSelector());
            double conf = BASE_CONFIDENCE;
            if (!els.isEmpty()) conf += 0.2;
            if (c.getSelector().length() <= 30) conf += SHORT_SELECTOR_BONUS;
            conf = Math.min(conf, 1.0);

            fieldCandidates.add(FieldRuleCandidate.builder()
                    .selector(c.getSelector())
                    .valueType("TEXT").attrName(null).confidence(conf)
                    .build());
        }

        fieldCandidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        FieldRuleCandidate primary = fieldCandidates.get(0);

        // 预览
        SelectorCandidate tmpPrimary = SelectorCandidate.builder()
                .selector(primary.getSelector()).confidence(primary.getConfidence()).build();
        FieldPreview preview = runFieldPreview(doc, context, tmpPrimary);

        return FieldRuleResult.builder()
                .primary(primary)
                .candidates(fieldCandidates.size() > 1
                        ? fieldCandidates.subList(1, Math.min(fieldCandidates.size(), 5))
                        : List.of())
                .preview(preview).warnings(List.of())
                .build();
    }

    // ================================================================
    // DOM 辅助
    // ================================================================

    private Element resolveElement(Document doc, ClickedElementInfo click) {
        return click.resolve(doc);
    }

    private Element resolveByIndexPath(Document doc, int[] indexPath) {
        Element el = doc.selectFirst("html");
        if (el == null) return null;
        for (int idx : indexPath) {
            if (idx >= el.childrenSize()) return null;
            el = el.child(idx);
        }
        return el;
    }

    /**
     * 从点击的叶子元素向上找到真正的列表项。
     * 例：点击 &lt;a&gt; → 向上找到 &lt;li&gt;（父级有 ≥ 2 个同名兄弟）→ 返回 &lt;li&gt;。
     */
    private Element findItemElement(Element target, Element region) {
        Element cur = target;
        Element body = target.ownerDocument().body();
        while (cur != null && cur != body && cur != region) {
            Element parent = cur.parent();
            if (parent == null) break;
            Map<String, Integer> tagCounts = new HashMap<>();
            for (Element sibling : parent.children()) {
                tagCounts.merge(sibling.tagName(), 1, Integer::sum);
            }
            if (tagCounts.getOrDefault(cur.tagName(), 0) >= MIN_ITEM_COUNT) {
                return cur;
            }
            cur = parent;
        }
        return target;
    }

    /**
     * 沿点选元素向上找到第一个"至少 2 个子元素 tag 相同"的祖先容器。
     * PRD 12.4 区域限定。
     */
    private Element findRegionContainer(Element target) {
        Element cur = target.parent();
        while (cur != null && !cur.tagName().equals("body")) {
            Map<String, Integer> tagCounts = new HashMap<>();
            for (Element child : cur.children()) {
                tagCounts.merge(child.tagName(), 1, Integer::sum);
            }
            if (tagCounts.getOrDefault(target.tagName(), 0) >= MIN_ITEM_COUNT) {
                return cur;
            }
            cur = cur.parent();
        }
        return target.ownerDocument().body();
    }

    // ================================================================
    // 候选验证 & 评分
    // ================================================================

    private List<SelectorCandidate> dedupeValidateAndSort(
            List<SelectorCandidate> all, Document doc, Element region
    ) {
        Set<String> seen = new HashSet<>();
        List<SelectorCandidate> validated = new ArrayList<>();

        for (SelectorCandidate c : all) {
            if (!seen.add(c.getSelector())) continue;
            Elements els = region.select(c.getSelector());
            c.setItemCount(els.size());
            if (els.size() < MIN_ITEM_COUNT) continue;

            double conf = BASE_CONFIDENCE;
            if (!c.getSelector().contains("nth-child")) conf += CLASS_ID_BONUS;
            if (checkLabelChainConsistency(els)) conf += LABEL_CHAIN_BONUS;
            if (c.getSelector().length() <= 30) conf += SHORT_SELECTOR_BONUS;
            c.setConfidence(Math.min(conf, 1.0));

            // 采样 indexPath 用于前端高亮
            List<int[]> paths = new ArrayList<>();
            for (int i = 0; i < Math.min(els.size(), MAX_SAMPLE); i++) {
                paths.add(buildIndexPath(els.get(i)));
            }
            c.setMatchedIndexPaths(paths);
            validated.add(c);
        }

        validated.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return validated;
    }

    private boolean checkLabelChainConsistency(Elements elements) {
        if (elements.size() < 2) return true;
        String first = getTagChain(elements.first());
        for (int i = 1; i < elements.size(); i++) {
            if (!first.equals(getTagChain(elements.get(i)))) return false;
        }
        return true;
    }

    private String getTagChain(Element el) {
        StringBuilder sb = new StringBuilder();
        Element cur = el;
        while (cur != null && !cur.tagName().equals("body")) {
            sb.insert(0, cur.tagName()).insert(cur.tagName().length(), ">");
            cur = cur.parent();
        }
        return sb.toString();
    }

    private int[] buildIndexPath(Element el) {
        List<Integer> path = new ArrayList<>();
        Element cur = el;
        while (cur.parent() != null) {
            path.add(0, cur.elementSiblingIndex());
            cur = cur.parent();
        }
        return path.stream().mapToInt(i -> i).toArray();
    }

    // ================================================================
    // 字段预览
    // ================================================================

    private FieldPreview runFieldPreview(
            Document doc, FieldContext context, SelectorCandidate candidate
    ) {
        Elements roots = "LIST".equals(context.getPageRole())
                && context.getItemSelector() != null
                ? doc.select(context.getItemSelector())
                : new Elements(doc.body());

        int success = 0, blank = 0;
        List<FieldSample> samples = new ArrayList<>();
        int limit = Math.min(roots.size(), MAX_SAMPLE);

        for (int i = 0; i < limit; i++) {
            Element root = roots.get(i);
            Elements els = root.select(candidate.getSelector());
            if (els.isEmpty()) {
                blank++;
                continue;
            }
            String raw = els.first().text();
            String val = (raw != null && !raw.isEmpty()) ? raw.trim() : null;
            if (val != null && !val.isEmpty()) success++;
            else blank++;
            samples.add(FieldSample.builder()
                    .itemIndex(i).value(val).raw(raw)
                    .warnings(List.of()).build());
        }

        int total = success + blank;
        double hitRate = total > 0
                ? Math.round((double) success / total * 100.0) / 100.0
                : 0.0;

        return FieldPreview.builder()
                .fieldStats(FieldStats.builder()
                        .hitRate(hitRate).blankCount(blank).successCount(success)
                        .build())
                .samples(samples).build();
    }

    // ================================================================
    // 工具
    // ================================================================

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String fmtIndexPath(ClickedElementInfo click) {
        int[] idx = click.getIndexPath();
        return idx != null ? Arrays.toString(idx) : "null";
    }

    private static List<SelectorStrategy> allStrategies() {
        return List.of(
                new ClassComboStrategy(),
                new ParentChildStrategy(),
                new AttributePathStrategy(),
                new SimplifiedPathStrategy()
        );
    }

    // ================================================================
    // 选择器策略实现
    // ================================================================

    /** 策略接口 */
    interface SelectorStrategy {
        List<SelectorCandidate> generate(Element target, Element container);
    }

    /** class 组合策略 */
    static class ClassComboStrategy implements SelectorStrategy {
        @Override
        public List<SelectorCandidate> generate(Element target, Element container) {
            List<SelectorCandidate> list = new ArrayList<>();
            String tag = target.tagName();
            List<String> classes = filterClasses(target.classNames());

            if (!classes.isEmpty()) {
                String sel = tag + "." + String.join(".",
                        classes.subList(0, Math.min(classes.size(), 2)));
                list.add(candidate(sel, "ClassCombo"));
            }
            if (classes.size() == 1) {
                list.add(candidate("." + classes.get(0), "ClassCombo-single"));
            }
            return list;
        }
    }

    /** 祖先+子元素策略 */
    static class ParentChildStrategy implements SelectorStrategy {
        @Override
        public List<SelectorCandidate> generate(Element target, Element container) {
            List<SelectorCandidate> list = new ArrayList<>();
            Element parent = target.parent();
            if (parent == null) return list;
            String parentSel = stableSelector(parent);
            String childSel = stableSelector(target);
            if (parentSel.isEmpty() || childSel.isEmpty()) return list;
            list.add(candidate(parentSel + " > " + childSel, "ParentChild"));
            return list;
        }

        private String stableSelector(Element el) {
            if (el.hasAttr("id")) return el.tagName() + "#" + el.attr("id");
            List<String> cls = filterClasses(el.classNames());
            if (cls.isEmpty()) return el.tagName();
            return el.tagName() + "." + cls.get(0);
        }
    }

    /** 属性路径策略（data-*, role） */
    static class AttributePathStrategy implements SelectorStrategy {
        @Override
        public List<SelectorCandidate> generate(Element target, Element container) {
            List<SelectorCandidate> list = new ArrayList<>();
            for (org.jsoup.nodes.Attribute attr : target.attributes()) {
                if (attr.getKey().startsWith("data-")) {
                    list.add(candidate("[" + attr.getKey() + "=\"" + attr.getValue() + "\"]",
                            "AttributePath"));
                }
            }
            if (target.hasAttr("role")) {
                list.add(candidate(target.tagName() + "[role=\"" + target.attr("role") + "\"]",
                        "AttributePath-role"));
            }
            return list;
        }
    }

    /** 纯 tag 兜底策略 */
    static class SimplifiedPathStrategy implements SelectorStrategy {
        @Override
        public List<SelectorCandidate> generate(Element target, Element container) {
            return List.of(candidate(target.tagName(), "SimplifiedPath"));
        }
    }

    // ================================================================
    // 策略工具
    // ================================================================

    private static List<String> filterClasses(Set<String> classNames) {
        return classNames.stream()
                .filter(c -> !c.startsWith("__crawler_")
                        && !c.equals("active") && !c.equals("hover"))
                .collect(Collectors.toList());
    }

    private static SelectorCandidate candidate(String selector, String strategy) {
        return SelectorCandidate.builder()
                .selector(selector).itemCount(0).confidence(0)
                .regionContainerSelector("").strategy(strategy)
                .build();
    }
}
