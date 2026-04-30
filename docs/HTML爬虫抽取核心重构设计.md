# HTML 爬虫抽取核心重构设计

> 本文档对应 PRD 中的**阶段一：统一后端抽取核心**。
> 阶段一只做后端核心解耦，不动前端，不做预览 API，不做配置 CRUD。
> 详细边界见第 6.1 节「阶段一边界声明」。

## 1. 现状分析

### 1.1 当前架构

`HtmlRunner` 是一个 366 行的 God Class，承载了所有抓取逻辑，且全部为 `private` 方法：

```
HtmlRunner (366行)
├── request()          private — HTTP 请求，预览时无法复用
├── applyRules()       private — 提取逻辑锁死在 Runner 内
├── applyField()       private — 字段映射逻辑无法独立测试
├── resolveUrl()       private — URL 补全逻辑散落
├── buildListUrls()    private — 分页逻辑无法单独预览
├── enrichFromDetail() private — 详情页抓取无法单独触发
└── shouldSkipByDate() private — 日期过滤逻辑与抓取耦合
```

`FieldExtractor` 相对独立，但存在两个问题：

- 返回 `Optional<ExtractedField>`，只取第一个命中，无诊断信息
- 直接耦合 Jsoup `Element`，无法替换 HTML 解析引擎

### 1.2 与 PRD 的差距

PRD 要求的核心原则：

- **前端只做交互，不做抽取** — 所有提取逻辑必须走统一后端
- **预览和正式抓取共用同一套抽取核心** — 不能预览一套、抓取一套
- **配置必须兼容 HtmlRunner** — 保存后的配置能被 `HtmlRunner.fetch()` 直接执行

当前代码无法满足以上要求。

---

## 2. 目标架构

### 2.1 分层总览

```
┌──────────────────────────────────────────────────────┐
│  HtmlConfigController   (预览 API + 配置 CRUD)         │
└────────────┬─────────────────────┬───────────────────┘
             │                     │
      ┌──────▼──────┐     ┌───────▼────────┐
      │ PreviewSvc  │     │  ConfigSvc      │
      └──────┬──────┘     └───────┬────────┘
             │                     │
    ┌────────▼────────┐           │
    │ ExtractionEngine│  ◄── 统一抽取核心（唯一真相源）
    └──────┬─────┬────┘
           │     │
    ┌──────▼┐ ┌──▼──────────┐
    │Request│ │RuleEvaluator│  每个组件职责单一
    │Client │ │              │
    └───────┘ └──────┬──────┘
                     │
              ┌──────▼────────┐
              │SelectorGen    │  点选 → CSS 选择器
              └───────────────┘
```

### 2.2 各层职责

| 层 | 组件 | 职责 | 输入 | 输出 | 阶段 |
|---|------|------|------|------|------|
| 1 | `HtmlRequestClient` | HTTP 请求，获取静态 HTML | URL + headers + timeout | `HtmlPageResponse` | 阶段一 |
| 2 | `HtmlRuleEvaluator` | 单条规则求值，带诊断 | Element + PolicyExtractRule | `RuleEvalResult` | 阶段一 |
| 3 | `HtmlExtractionEngine` | 编排列表/详情提取流程 | HTML + itemSelector + rules | `List<ExtractedItem>` | 阶段一 |
| - | `RawDocMapper` | `ExtractedItem` → `RawDoc` 映射 | `ExtractedItem` + dataSource 上下文 | `RawDoc` | 阶段一 |
| 4 | `HtmlSelectorGenerator` | 点选元素 → CSS 选择器 | `ClickedElementInfo` | `List<SelectorCandidate>` | 阶段三 |
| 5 | `HtmlConfigPreviewService` | 预览工作流编排 | 预览请求 | 预览结果 + 诊断 | 阶段三 |
| 6 | `HtmlConfigService` | 配置 CRUD + 校验 | 配置表单 | 持久化配置 | 阶段四 |

### 2.3 现有模型补充约定

阶段一允许在以下既有类上增加便捷方法或受控扩展，所有改动必须保持与现有调用方向后兼容：

`HtmlCrawlConfig` 增加：

- `List<PolicyExtractRule> getListRules()`：返回列表页规则，等价于 `rulesFor(getListPage())`。
- `List<PolicyExtractRule> getDetailRules()`：返回详情页规则，无详情页时返回空列表。
- `boolean hasDetail()`：等价于 `detailPage().isPresent() && !getDetailRules().isEmpty()`。
- `Integer getListTimeoutMs()` / `Integer getDetailTimeoutMs()`：从对应 `PolicyCrawlPage` 取超时，方便调用方避免链式 `getListPage().getTimeoutMs()`。

其他模型保持不变。`HtmlCrawlConfig.detailPage()` 与 `getDetailPage()` 同时存在，新增方法只为可读性服务，不删除原 API。

---

## 3. 接口设计

### 3.1 HtmlRequestClient

```java
public interface HtmlRequestClient {
    /**
     * 请求静态 HTML 页面。
     *
     * @param url       目标 URL
     * @param headers   自定义请求头（可为空）
     * @param timeoutMs 超时毫秒数
     * @return 页面响应，包含 HTML 正文、最终 URL、状态码、错误信息
     */
    HtmlPageResponse fetch(String url, Map<String, String> headers, int timeoutMs);
}
```

`HtmlPageResponse` 模型：

```java
@Data
@Builder
public class HtmlPageResponse {
    private String html;           // 页面 HTML 正文
    private String finalUrl;       // 重定向后的最终 URL
    private int statusCode;        // HTTP 状态码
    private String title;          // <title> 标签内容
    private List<String> warnings; // 警告（如疑似 JS 渲染）
    private String error;          // 错误信息（成功时为 null）

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
```

请求头流转约定：

- `JsoupRequestClient.fetch` 必须接收 `Map<String, String>` 形式的 headers，不允许在内部硬编码。
- `HtmlRunner` 调用前从 `PolicyCrawlPage.headers`（JSON 字符串）解析为 Map；解析失败记日志并使用空 Map，不要因为 headers 解析失败导致整次抓取失败。
- 阶段一即使 headers 真实场景下都是空，也要把链路打通；后续不需要再为加 Cookie/Referer 改接口签名。

实现：`JsoupRequestClient`（第一版），后续可扩展 `PlaywrightRequestClient` 支持 JS 渲染。

### 3.2 HtmlRuleEvaluator

```java
public interface HtmlRuleEvaluator {
    /**
     * 对指定元素执行单条提取规则。
     *
     * @param root 当前作用域根元素（列表 item 或详情页 document）
     * @param rule 提取规则
     * @return 求值结果，包含提取值、是否命中、诊断信息
     */
    RuleEvalResult evaluate(Element root, PolicyExtractRule rule);

    /**
     * 对同一字段的所有兜底规则依次尝试，返回第一条命中的结果。
     * 所有规则都不命中时返回空结果（含失败诊断）。
     *
     * @param root      当前作用域根元素
     * @param rules     该字段的全部规则（按 sortOrder 排序）
     * @param fieldName 字段名
     * @return 求值结果（可能为未命中状态）
     */
    RuleEvalResult evaluateFirst(Element root, List<PolicyExtractRule> rules, String fieldName);

    /**
     * 对同一字段所有规则执行求值，返回全部结果（含命中和未命中）。
     * 用于预览时展示每条规则的诊断。
     *
     * 仅供预览路径调用，正式抓取必须使用 {@link #evaluateFirst}。
     * 否则正式抓取会做无谓的全规则计算。
     *
     * @param root      当前作用域根元素
     * @param rules     该字段的全部规则
     * @param fieldName 字段名
     * @return 所有规则的求值结果列表
     */
    List<RuleEvalResult> evaluateAll(Element root, List<PolicyExtractRule> rules, String fieldName);
}
```

调用边界声明：

- 正式抓取（`HtmlRunner` → `HtmlExtractionEngine`）只允许调用 `evaluateFirst`。
- 预览（阶段三 `HtmlConfigPreviewService`）按需调用 `evaluateAll`，用于展示每条规则的命中诊断。
- 该约束通过代码评审保证，不在接口本身限制。

`RuleEvalResult` 模型（核心诊断载体）：

```java
@Data
@Builder
public class RuleEvalResult {
    private String fieldName;          // 字段名
    private String value;              // 提取到的值（null 表示未命中）
    private boolean matched;           // 是否提取成功
    private PolicyExtractRule rule;    // 生效的规则
    private RuleDiagnostic diagnostic; // 诊断详情
}

@Data
@Builder
public class RuleDiagnostic {
    private int elementsMatchedBySelector;  // CSS 选择器命中元素数
    private Integer extractedTextLength;    // 提取文本长度（TEXT 模式）
    private boolean dateParseSuccess;       // 日期解析是否成功（publishDate）
    private boolean urlResolved;            // URL 是否已补全为绝对地址
    private String failureReason;           // 失败原因（可展示给用户）
}
```

### 3.3 HtmlExtractionEngine

```java
public interface HtmlExtractionEngine {
    /**
     * 从 HTML 中按 itemSelector 切割列表项，对每项应用提取规则。
     *
     * @param html         页面 HTML
     * @param itemSelector 列表项 CSS 选择器
     * @param rules        列表字段提取规则
     * @param baseUrl      用于相对 URL 补全
     * @return 提取结果列表（含字段值和诊断）
     */
    List<ExtractedItem> extractList(String html, String itemSelector,
                                     List<PolicyExtractRule> rules, String baseUrl);

    /**
     * 从详情页 HTML 中提取字段。
     *
     * @param html    详情页 HTML
     * @param rules   详情页提取规则
     * @param baseUrl 用于相对 URL 补全
     * @return 提取结果（含字段值和诊断）
     */
    ExtractedItem extractDetail(String html, List<PolicyExtractRule> rules, String baseUrl);
}
```

`ExtractedItem` 模型：

```java
@Data
@Builder
public class ExtractedItem {
    private Map<String, String> fields;           // fieldName → value
    private Map<String, RuleEvalResult> details;  // fieldName → 完整求值结果（含诊断）
    private String itemText;                      // 列表项文本摘要（前 200 字符）
}
```

### 3.4 HtmlSelectorGenerator

```java
public interface HtmlSelectorGenerator {
    /**
     * 根据用户点击的列表项元素，生成候选 item_selector。
     *
     * @param html    页面 HTML
     * @param clicked 被点击元素的 DOM 信息
     * @return 候选选择器列表，按置信度降序
     */
    List<SelectorCandidate> suggestItemSelector(String html, ClickedElementInfo clicked);

    /**
     * 根据用户点击的字段元素，生成候选字段选择器（相对于 item）。
     *
     * @param html         页面 HTML
     * @param clicked      被点击元素的 DOM 信息
     * @param itemSelector 当前 item_selector（用于计算相对路径）
     * @return 候选选择器列表，按置信度降序
     */
    List<SelectorCandidate> suggestFieldSelector(String html, ClickedElementInfo clicked,
                                                  String itemSelector);
}
```

`SelectorCandidate` 模型：

```java
@Data
@Builder
public class SelectorCandidate {
    private String selector;     // CSS 选择器字符串
    private double confidence;   // 置信度 0-1
    private int matchCount;      // 在当前页面中的命中数
    private List<String> samples; // 命中元素文本样例（前 5 条）
    private String strategy;     // 生成策略名称（便于调试）
}
```

`ClickedElementInfo` 模型（前端传入）：

```java
@Data
@Builder
public class ClickedElementInfo {
    private String tagName;                     // 标签名
    private List<String> classList;             // class 列表
    private String id;                          // 元素 id
    private Map<String, String> attributes;     // 其他属性
    private String text;                        // 元素文本（截断）
    private List<String> cssPath;               // 从 body 到该元素的标签路径
    private List<String> classPath;             // 从 body 到该元素的 class 路径
}
```

### 3.5 HtmlConfigPreviewService

```java
public interface HtmlConfigPreviewService {
    /**
     * 加载页面并返回快照 + 基本信息。
     */
    LoadPageResult loadPage(String url, Map<String, String> headers, int timeoutMs);

    /**
     * 预览列表项命中情况（仅 itemSelector，不含字段）。
     */
    ItemPreviewResult previewItems(String html, String itemSelector);

    /**
     * 预览列表字段提取结果。
     */
    ListPreviewResult previewList(String html, String itemSelector,
                                   List<PolicyExtractRule> rules, String baseUrl);

    /**
     * 预览详情页字段提取结果。
     */
    DetailPreviewResult previewDetail(String html, List<PolicyExtractRule> rules, String baseUrl);
}
```

预览结果模型包含前端直接可用的统计数据：

```java
@Data
@Builder
public class ListPreviewResult {
    private int itemCount;                              // item 命中数
    private List<ExtractedItem> samples;                // 前 10 条提取结果
    private Map<String, FieldStat> fieldStats;          // 每个字段的统计
    private List<String> warnings;                      // 警告信息
    private List<String> errors;                        // 错误信息
}

@Data
@Builder
public class FieldStat {
    private String fieldName;
    private int hitCount;         // 命中数
    private int emptyCount;       // 空值数
    private int totalCount;       // 总 item 数
    private double hitRate;       // 命中率
    private Integer dateParseSuccessCount; // 日期解析成功数（仅 publishDate）
    private List<String> sampleValues;    // 前 5 条有效值
}
```

---

## 4. HtmlRunner 重构

### 4.1 重构前

```java
@Component
public class HtmlRunner implements SourceRunner {
    // 所有逻辑（HTTP、提取、URL补全、分页）都在这一个类里
    // 366 行，全部 private 方法
}
```

### 4.2 重构后

约束：

- 保留原有“列表页并行 + item 级并行”两级并发，避免详情页串行造成性能退化。
- 日期过滤前置到详情页抓取之前，避免为已过期的旧文档浪费一次详情请求。
- `HtmlRunner` 不再持有任何字段提取、URL 补全、日期解析逻辑，这些已下沉到 `HtmlExtractionEngine` 和 `HtmlRuleEvaluator`。
- `HtmlRunner` 只做：加载配置、构建分页 URL、调度并发、调用核心、组装 `RawDoc`、增量过滤。

```java
@Component
public class HtmlRunner implements SourceRunner {

    private final HtmlCrawlConfigLoader configLoader;
    private final HtmlRequestClient requestClient;        // ★ 注入
    private final HtmlExtractionEngine extractionEngine;  // ★ 注入
    private final RawDocMapper rawDocMapper;              // ★ 注入：fields → RawDoc
    private final ThreadPoolTaskExecutor crawlerThreadPool;

    @Override
    public List<RawDoc> fetch(PolicyDataSource ds, FetchContext ctx) {
        HtmlCrawlConfig config = configLoader.load(ds.getId());
        if (!config.hasStructuredConfig()) return Collections.emptyList();

        // 列表页并发
        List<CompletableFuture<List<RawDoc>>> pageFutures = buildListUrls(config).stream()
            .map(url -> CompletableFuture.supplyAsync(
                () -> fetchPage(ds, config, url, ctx), crawlerThreadPool))
            .toList();

        return pageFutures.stream()
            .flatMap(f -> safeJoin(f).stream())
            .toList();
    }

    private List<RawDoc> fetchPage(PolicyDataSource ds, HtmlCrawlConfig config,
                                   String listUrl, FetchContext ctx) {
        Map<String, String> headers = parseHeaders(config.getListPage().getHeaders());
        HtmlPageResponse page = requestClient.fetch(listUrl, headers, config.getListTimeoutMs());
        if (!page.isSuccess()) return Collections.emptyList();

        // ★ 核心变化：调 extractionEngine，不再自己写 applyRules / resolveUrl / parseDate
        List<ExtractedItem> items = extractionEngine.extractList(
            page.getHtml(), config.getListPage().getItemSelector(),
            config.getListRules(), listUrl
        );

        // item 级并行；详情页抓取放在每个 item future 内部
        List<CompletableFuture<RawDoc>> itemFutures = items.stream()
            .map(item -> CompletableFuture.supplyAsync(
                () -> processItem(ds, config, item, ctx), crawlerThreadPool))
            .toList();

        return itemFutures.stream()
            .map(this::safeJoinItem)
            .filter(Objects::nonNull)
            .toList();
    }

    private RawDoc processItem(PolicyDataSource ds, HtmlCrawlConfig config,
                                ExtractedItem listItem, FetchContext ctx) {
        // 列表字段先映射成 RawDoc，便于前置增量判断
        RawDoc raw = rawDocMapper.fromListItem(ds, listItem);

        // ★ 前置增量过滤：避免为旧文档拉详情页
        if (shouldSkipByDate(raw, ctx)) return null;

        if (config.hasDetail() && raw.getUrl() != null) {
            enrichDetail(raw, config);
        }

        if (raw.getTitle() == null && raw.getUrl() == null) return null;
        return shouldSkipByDate(raw, ctx) ? null : raw;
    }

    private void enrichDetail(RawDoc raw, HtmlCrawlConfig config) {
        Map<String, String> headers = parseHeaders(config.getDetailPage().getHeaders());
        HtmlPageResponse page = requestClient.fetch(raw.getUrl(), headers,
            config.getDetailTimeoutMs());
        if (!page.isSuccess()) return;

        ExtractedItem detail = extractionEngine.extractDetail(
            page.getHtml(), config.getDetailRules(), raw.getUrl());

        // 详情字段非空时才覆盖列表字段，避免空值覆盖
        rawDocMapper.mergeDetail(raw, detail);
    }

    // buildListUrls / parseHeaders / shouldSkipByDate / safeJoin 等辅助保留在 HtmlRunner
}
```

### 4.3 字段映射约定（统一抽取核心的责任划分）

为避免阶段一重构后字段处理逻辑再次散落，定下硬约定：

| 行为 | 责任组件 | 备注 |
|------|----------|------|
| CSS 选择器执行、属性/HTML/正则提取 | `HtmlRuleEvaluator` | 单条规则的所有取值都在这里完成 |
| 同字段多规则按 `sortOrder` 兜底 | `HtmlRuleEvaluator.evaluateFirst` | `HtmlExtractionEngine` 不直接遍历规则列表 |
| 日期解析（`publishDate` 字段） | `HtmlRuleEvaluator` | 解析结果通过 `RuleDiagnostic.parsedDate` 透出，`value` 仍是原文 |
| 相对 URL 补全 | `HtmlExtractionEngine` | 字段名为 `url` 时，使用 `baseUrl` 解析为绝对路径，写入 `ExtractedItem.fields` |
| `ExtractedItem.fields` → `RawDoc` 映射 | `RawDocMapper` | 字段名到 `setTitle/setUrl/setSummary/...` 的映射只此一处 |
| 详情字段覆盖列表字段 | `RawDocMapper.mergeDetail` | 仅在详情字段非空时覆盖；空值不得覆盖列表已有值 |
| `metadata` 兜底字段 | `RawDocMapper` | 未识别的 `fieldName` 全部进入 `metadata` |

`ExtractedItem.fields` 的内容契约：

- `url` 一定是绝对 URL（如果原始 href 解析失败，仍写入原始字符串并附带诊断警告）。
- `publishDate` 仍是字符串原文（解析后的 `LocalDate` 在 diagnostic 里）。
- 列表样例提取结果不直接持有 `RawDoc` 引用，方便预览阶段共用同一份数据结构。

### 4.4 Bean 注入关系

```
                    ┌─────────────────────┐
                    │ JsoupRequestClient   │ (唯一实例)
                    └──────┬──────┬───────┘
                           │      │
              ┌────────────▼┐ ┌───▼─────────────┐
              │HtmlRunner   │ │PreviewService    │
              │(正式抓取)   │ │(预览)            │
              └──────┬──────┘ └───┬─────────────┘
                     │            │
              ┌──────▼────────────▼──┐
              │ HtmlExtractionEngine  │ (唯一实例)
              └──────┬───────────────┘
                     │
              ┌──────▼────────┐
              │ RuleEvaluator  │ (唯一实例，重构后的 FieldExtractor)
              └───────────────┘
```

预览和正式抓取使用**同一个 Bean 实例**，不是同一套逻辑的复制。

---

## 5. HtmlSelectorGenerator 策略链

### 5.1 策略优先级

```java
@Component
public class DefaultSelectorGenerator implements HtmlSelectorGenerator {

    // 策略链，按优先级排列
    private final List<SelectorStrategy> strategies = List.of(
        new UniqueIdStrategy(),          // #id（最稳定，但网页中少见）
        new ClassCombinationStrategy(),  // .class1.class2（首选策略）
        new AttrValueStrategy(),         // [data-xxx="yyy"]（次选）
        new StructuralPathStrategy()     // div > ul > li:nth-child(3)（兜底）
    );

    public List<SelectorCandidate> suggestItemSelector(String html, ClickedElementInfo clicked) {
        List<SelectorCandidate> candidates = new ArrayList<>();
        for (SelectorStrategy strategy : strategies) {
            Optional<SelectorCandidate> candidate = strategy.tryGenerate(html, clicked);
            candidate.ifPresent(candidates::add);
        }
        // 每个策略返回一个候选，排序后返回
        candidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return candidates;
    }
}
```

### 5.2 各策略说明

| 策略 | 输入 | 输出示例 | 优点 | 缺点 |
|------|------|----------|------|------|
| UniqueIdStrategy | `id="item-123"` | `#item-123` | 极稳定 | 网页中很少用 id |
| ClassCombinationStrategy | `class="title news-item"` | `.title.news-item` | 较稳定，通用性好 | class 可能重复 |
| AttrValueStrategy | `data-id="abc"` | `[data-id="abc"]` | 属性稳定 | 需要页面有特殊属性 |
| StructuralPathStrategy | 标签路径 | `ul.news-list > li` | 总能生成 | 页面结构变化易失效 |

### 5.3 置信度计算规则

- 选择器命中数恰好等于候选 item 数（页面重复模式）：+0.3
- 选择器仅命中 1 个元素：-0.3（可能太窄）
- 选择器命中 0 个：置信度 = 0（无效）
- 选择器路径深度 ≤ 3：+0.1（路径短更稳定）
- 选择器包含 nth-child：-0.1（对顺序敏感）

---

## 6. 实施计划

### 6.1 阶段一边界声明

阶段一只做一件事：**把正式抓取的核心抽出来，让 `HtmlRunner` 变成编排器，并为后续预览服务暴露稳定 API**。

阶段一**做**：

- `HtmlRequestClient` 接口与 `JsoupRequestClient` 实现（含 headers 流转链路）。
- `HtmlRuleEvaluator` 接口（在现有 `FieldExtractor` 基础上演化）。
- `HtmlExtractionEngine` 接口与 `DefaultExtractionEngine` 实现。
- `RawDocMapper`：字段名 → `RawDoc` 映射 + 详情非空覆盖。
- `HtmlCrawlConfig` 增加便捷方法（详见 2.3）。
- `HtmlRunner` 重构为编排器，保留两级并发与日期前置过滤。
- 抓取行为回归测试。

阶段一**不做**：

- 不实现 `HtmlSelectorGenerator`（阶段三）。
- 不实现 `HtmlConfigPreviewService` 与任何预览 API（阶段三）。
- 不实现 HTML 快照缓存（阶段三再考虑用 Caffeine + TTL）。
- 不实现配置 CRUD（阶段四）。
- 不动前端（阶段二起步）。
- 不引入新的 valueType（保持 `TEXT/ATTR/HTML/CONST/REGEX` 五种）。
- 不实现 `NEXT_SELECTOR` 分页（阶段五）。
- 不实现 `required/scope/fallbackGroup` 的运行时行为（除非引入回归风险，否则阶段一保持现状，仅留接口位）。

### 6.2 阶段一开发步骤

1. **新增模型与接口**（不动现有调用方）：
   - `HtmlPageResponse`（含 `isSuccess()`）
   - `RuleEvalResult` + `RuleDiagnostic`
   - `ExtractedItem`
   - `HtmlRequestClient` 接口
   - `HtmlRuleEvaluator` 接口
   - `HtmlExtractionEngine` 接口
   - `RawDocMapper` 类

2. **`HtmlCrawlConfig` 增加便捷方法**：
   - `getListRules()` / `getDetailRules()` / `hasDetail()` / `getListTimeoutMs()` / `getDetailTimeoutMs()`
   - 不删除已有 API（`rulesFor` / `detailPage()` 保留）。

3. **实现 `JsoupRequestClient`**：
   - 迁移 `HtmlRunner.request()` 的 UA、超时、GET 逻辑。
   - 增加 `Map<String,String>` headers 应用。
   - 输出统一 `HtmlPageResponse`，捕获 `IOException` 转为 `error` 字段而不抛出。

4. **改造 `FieldExtractor` → 实现 `HtmlRuleEvaluator`**：
   - 主接口改为返回 `RuleEvalResult`，承载诊断信息。
   - 旧方法（`extractFirst` / `extract` / `parseDate`）保留 `@Deprecated`，委托给新方法，避免一次性破坏所有调用点。
   - 日期解析仍在此层完成，结果通过 `RuleDiagnostic.parsedDate` 透出。

5. **实现 `DefaultExtractionEngine`**：
   - 组合 `HtmlRuleEvaluator`。
   - 迁移 `HtmlRunner.applyRules()` 与 `resolveUrl()` 的逻辑。
   - 字段名 `url` 自动调用相对路径补全；写入 `ExtractedItem.fields` 的 url 必须是绝对地址或带诊断警告。
   - 正式抓取路径只调用 `evaluateFirst`。

6. **实现 `RawDocMapper`**：
   - 接收 `ExtractedItem`，按字段名映射到 `RawDoc`。
   - 提供 `fromListItem(...)` 与 `mergeDetail(rawDoc, detailItem)`：详情字段非空才覆盖。
   - 未识别字段全部写入 `RawDoc.metadata`。

7. **重构 `HtmlRunner`**：
   - 注入 `HtmlRequestClient` / `HtmlExtractionEngine` / `RawDocMapper`。
   - 删除已迁移的 private 方法（`request` / `applyRules` / `applyField` / `resolveUrl` / `enrichFromDetail` / `putMetadata`）。
   - 保留：`buildListUrls` / `shouldSkipByDate` / `parseHeaders`。
   - 保留两级并发：列表页并行 + item 级并行；详情页请求在 item future 内部完成。
   - 保留：日期增量过滤前置到详情请求之前。

8. **回归验证**：
   - 选择 1～2 条已配置好的真实 HTML 数据源（如发改委、统计局），执行重构前后两次抓取。
   - 比对 `RawDoc.title / url / publishDate / content` 一致。
   - 比对 `PipelineResult.fetchedCount` 一致。
   - 比对单页耗时不退化超过 20%。
   - 单元测试至少覆盖：`HtmlRuleEvaluator` 五种 valueType + 兜底链；`DefaultExtractionEngine` 列表/详情路径；`RawDocMapper` 详情非空覆盖。

### 6.3 阶段一文件变更清单

```
新增:
  src/main/java/com/policyradar/sources/html/
    ├── HtmlRequestClient.java          (接口)
    ├── JsoupRequestClient.java         (实现)
    ├── HtmlPageResponse.java           (模型)
    ├── HtmlRuleEvaluator.java          (接口)
    ├── HtmlExtractionEngine.java       (接口)
    ├── DefaultExtractionEngine.java    (实现)
    ├── RuleEvalResult.java             (模型)
    ├── RuleDiagnostic.java             (模型)
    ├── ExtractedItem.java              (模型)
    └── RawDocMapper.java               (字段映射)

修改:
  src/main/java/com/policyradar/sources/html/
    ├── FieldExtractor.java             (改造为实现 HtmlRuleEvaluator，旧方法保持兼容)
    └── HtmlCrawlConfig.java            (新增便捷方法，不删除原 API)

  src/main/java/com/policyradar/sources/runner/
    └── HtmlRunner.java                 (重构为编排器，注入新组件)
```

阶段三才会引入（阶段一不创建空类）：

```
src/main/java/com/policyradar/sources/html/
  ├── HtmlSelectorGenerator.java        (阶段三)
  ├── DefaultSelectorGenerator.java     (阶段三)
  ├── SelectorCandidate.java            (阶段三)
  ├── ClickedElementInfo.java           (阶段三)
  ├── HtmlConfigPreviewService.java     (阶段三)
  ├── DefaultPreviewService.java        (阶段三)
  └── preview/...                       (阶段三)
```

### 6.4 阶段一验收标准

阶段一合并前必须满足：

- 现有数据源抓取行为与重构前一致（数量、字段值、入库结果）。
- 现有数据源抓取耗时不退化超过 20%。
- `HtmlRunner.fetch()` 不再持有任何字段提取、URL 补全、日期解析逻辑。
- `HtmlRequestClient`、`HtmlExtractionEngine`、`HtmlRuleEvaluator` 均为单例 Spring Bean，且可被未来的预览服务直接注入复用。
- 现有 `FieldExtractor` 调用方（如有）继续可用，不强制升级。
- 单元测试覆盖率：`HtmlRuleEvaluator` 五种 valueType + 兜底链；`DefaultExtractionEngine` 列表/详情；`RawDocMapper` 详情非空覆盖。

### 6.5 后续阶段预告

| 阶段 | 内容 | 依赖 |
|------|------|------|
| 阶段二 | Vue3 前端 + iframe 快照 + 点选交互 | 阶段一接口 |
| 阶段三 | 规则建议 + 实时预览 API + HTML 快照缓存 + capabilities 接口 | 阶段一 + 阶段二 |
| 阶段四 | 配置 CRUD + 运行闭环 | 阶段三 |
| 阶段五 | 复杂能力增强（NEXT_SELECTOR、多页分页、required 运行时、JS 渲染等） | 阶段四 |

---

## 7. 阶段一刻意不做的优化

以下在阶段一中刻意不做，避免过度设计或越界：

- **不做 HTML 解析引擎抽象** — 阶段一只用 Jsoup，`HtmlRequestClient` 接口足够；后续需要 Selenium/Playwright 时，新增 `BrowserRequestClient` 实现即可。
- **不做通用规则引擎** — 五种 valueType（TEXT/ATTR/HTML/CONST/REGEX）用 switch-case，不引入表达式语言。
- **不做选择器 AI 推荐** — 选择器生成放阶段三，按规则策略链处理，不引入 ML。
- **不做多元素合并** — 阶段一字段提取只取首条命中元素，正文同样只支持单一选择器。
- **不做正文排除选择器** — 后续迭代再加。
- **不做 HTML 快照缓存** — 阶段一没有预览路径，不需要缓存；阶段三引入 Caffeine + TTL。
- **不做 capabilities 接口** — 阶段一没有前端调用方；阶段三再做，并强制由内核注解收集，不允许 controller 硬编码。
- **不做 `required / scope / fallbackGroup` 运行时语义** — 阶段一保持现状，只保留字段以便未来扩展；阶段五统一处理。
- **不做 `NEXT_SELECTOR` 分页** — 阶段一沿用 `NONE` 与 `URL_TEMPLATE`，阶段五补齐。
- **不做配置版本/审计** — 阶段四再考虑是否引入。

---

## 8. 编码规范与安全要求

本节约束阶段一所有新增与修改代码的写法，Code Review 时按本节逐条检查。

### 8.1 禁止 Lambda 表达式

所有代码必须使用传统写法，禁止使用 lambda 表达式（`->` 箭头语法）。

**禁止示例**：

```java
// ✗ 禁止：lambda
candidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
rulesByField.forEach((field, ruleList) -> { ... });
list.stream().map(item -> doSomething(item)).toList();
```

**要求写法**：

```java
// ✓ 正确：传统 Comparator
candidates.sort(new Comparator<SelectorCandidate>() {
    @Override
    public int compare(SelectorCandidate a, SelectorCandidate b) {
        return Double.compare(b.getConfidence(), a.getConfidence());
    }
});

// ✓ 正确：传统 for-each
for (Map.Entry<String, List<PolicyExtractRule>> entry : rulesByField.entrySet()) {
    String field = entry.getKey();
    List<PolicyExtractRule> ruleList = entry.getValue();
    // ...
}
```

### 8.2 禁止 Stream API

所有集合操作必须使用传统 for 循环、for-each 循环或显式迭代器，禁止使用 `stream()`、`.map()`、`.filter()`、`.flatMap()`、`.collect()`、`.toList()` 等 Stream API。

**禁止示例**：

```java
// ✗ 禁止：stream + lambda + toList
List<RawDoc> docs = items.stream()
    .map(item -> rawDocMapper.fromListItem(ds, item))
    .filter(Objects::nonNull)
    .toList();

// ✗ 禁止：stream + flatMap
return pageFutures.stream()
    .flatMap(f -> safeJoin(f).stream())
    .toList();
```

**要求写法**：

```java
// ✓ 正确：传统 for 循环
List<RawDoc> docs = new ArrayList<>();
for (ExtractedItem item : items) {
    RawDoc doc = rawDocMapper.fromListItem(ds, item);
    if (doc != null) {
        docs.add(doc);
    }
}

// ✓ 正确：嵌套 for 循环替代 flatMap
List<RawDoc> allDocs = new ArrayList<>();
for (CompletableFuture<List<RawDoc>> future : pageFutures) {
    List<RawDoc> pageDocs = safeJoin(future);
    for (RawDoc doc : pageDocs) {
        allDocs.add(doc);
    }
}
```

### 8.3 空值安全

所有 public 方法必须在方法入口处对可能为 null 的参数做防御性检查，失败时抛出 `IllegalArgumentException` 并给出明确消息，不允许 NPE 在运行时随机炸。

```java
public HtmlPageResponse fetch(String url, Map<String, String> headers, int timeoutMs) {
    if (url == null || url.isBlank()) {
        throw new IllegalArgumentException("url 不能为空");
    }
    if (timeoutMs <= 0) {
        throw new IllegalArgumentException("timeoutMs 必须 > 0，当前值: " + timeoutMs);
    }
    // headers 允许为 null，内部转为空 Map
    if (headers == null) {
        headers = Collections.emptyMap();
    }
    // ...
}
```

调用方从 Map 取值后必须判空，不允许对 null 值直接调用 `.trim()`、`.isEmpty()` 等方法。

### 8.4 异常处理

- 网络异常（`IOException`、`SocketTimeoutException` 等）必须捕获并转为 `HtmlPageResponse.error`，不允许向上抛出导致调用链断裂。
- 规则执行异常（选择器语法错误、DOM 遍历异常）必须捕获并记入 `RuleDiagnostic.failureReason`，不允许因单条规则错误导致整批 item 提取失败。
- 所有 catch 块必须打日志（级别 `WARN` 或 `ERROR`），不允许空 catch 吞异常。

```java
// ✓ 正确：捕获并转为结构化错误
try {
    Document doc = Jsoup.connect(url)
            .userAgent(UA)
            .timeout(timeoutMs)
            .headers(headers)
            .get();
    return HtmlPageResponse.builder()
            .html(doc.html())
            .finalUrl(doc.location())
            .statusCode(200)
            .title(doc.title())
            .build();
} catch (SocketTimeoutException e) {
    log.warn("请求超时: url={}, timeoutMs={}", url, timeoutMs);
    return HtmlPageResponse.builder()
            .statusCode(0)
            .error("请求超时: " + e.getMessage())
            .build();
} catch (IOException e) {
    log.error("请求失败: url={}", url, e);
    return HtmlPageResponse.builder()
            .statusCode(0)
            .error("请求失败: " + e.getMessage())
            .build();
}
```

### 8.5 URL 安全校验

`HtmlRequestClient` 在发起请求前必须校验 URL 协议：

- 只允许 `http://` 和 `https://` 协议。
- 禁止 `file://`、`ftp://`、`javascript:` 等协议。
- 校验失败直接返回 `HtmlPageResponse` 并置 error，不发起实际请求。

```java
private void validateUrl(String url) {
    if (url == null || url.isBlank()) {
        throw new IllegalArgumentException("url 不能为空");
    }
    String lower = url.trim().toLowerCase(Locale.ROOT);
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        throw new IllegalArgumentException("不支持的协议，仅允许 http/https: " + url);
    }
}
```

此方法不暴露到 public 接口，仅在 `JsoupRequestClient.fetch()` 内部调用。

### 8.6 线程安全

- `HtmlRunner` 中 `CompletableFuture.supplyAsync()` 提交给 `crawlerThreadPool` 的闭包**不得修改外部共享的可变状态**。每个 future 内只操作自己的局部变量或新创建的对象，结果通过返回值汇合。
- `HtmlRuleEvaluator` 和 `HtmlExtractionEngine` 的实现类必须是无状态的，所有状态通过方法参数传入。如果未来需要缓存（如选择器编译结果），必须使用 `ConcurrentHashMap` 或显式锁保护。
- `RawDocMapper` 必须是无状态的，仅做字段拷贝，不持有任何可变字段。

### 8.7 资源管理

- Jsoup `Connection` 和 `Response` 由 Jsoup 内部管理，不需要手动关闭。但 `JsoupRequestClient` 在超时或异常场景下确保不持有悬挂引用。
- 如果后续引入文件 IO（如 HTML 快照缓存），必须使用 try-with-resources。
- 线程池 `crawlerThreadPool` 的生命周期由 Spring 容器管理，不在阶段一代码中手动 shutdown。

### 8.8 日志规范

- 正常流程使用 `INFO` 级别，记录页面命中 item 数、提取成功数。
- 可恢复错误（单条规则失败、单个 item 提取失败）使用 `WARN` 级别，日志中必须包含数据源名称便于排查。
- 不可恢复错误（整页请求失败、配置加载失败）使用 `ERROR` 级别。
- 日志消息中**禁止拼接用户原始输入**（URL 中可能含敏感参数），先脱敏再记录。URL 中的 query string 参数值用 `***` 替换。

```java
// ✓ 正确：脱敏 URL
log.info("请求页面: {}", maskUrl(url));
// 输出示例：请求页面: https://example.com/list?page=***&token=***
```

`maskUrl` 由工具方法实现，截断 query string 值但不掩盖路径。

### 8.9 代码示例中的约定

本文档中第 3～5 节出现的代码示例为表达架构意图，部分使用了 stream/lambda 简写。**阶段一实际编写 Java 代码时，必须按本章要求全部改为传统写法**，不得以"设计文档里这么写"为理由保留 stream/lambda。