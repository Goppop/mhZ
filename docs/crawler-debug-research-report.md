# 爬虫选择器调试研究报告

日期: 2026-04-28

适用阶段: 当前阶段先调通“页面结构清晰、无需登录、无需复杂 JS 渲染”的政府/部委网站爬取。

## 一、结论

你现在遇到的核心问题不是“不会背选择器语法”，而是还没有形成一套稳定的定位方法:

1. 先判断数据到底在 HTML、接口 JSON、RSS，还是 JS 渲染后的 DOM 里。
2. 再确定“列表页的一条记录”是哪一个重复节点。
3. 然后只在这条记录内部找标题、链接、日期。
4. 最后再进入详情页找正文、发布日期、发布机构、文号。

所以你需要学习的不是一整套爬虫大课，而是下面这几个最小原理:

- DOM 树: 页面不是一段文字，而是一棵节点树。
- CSS Selector: 用选择器从 DOM 树中挑节点。
- 相对选择器: 在一条列表记录内部继续 `select`，避免选到页面别处的元素。
- 列表页和详情页分工: 列表页负责发现 URL，详情页负责拿完整正文。
- 静态 HTML、接口 JSON、JS 渲染的区别: 决定用 `HtmlRunner`、`HttpJsonRunner`、`SearchRunner` 还是未来的 Python/Playwright。

## 二、当前项目爬虫链路

项目当前已经具备“配置化接入清晰网站”的基础。

主要入口:

- `policy_data_source`: 数据源注册表，配置 URL、类型、选择器。
- `HtmlRunner`: 负责 `type=HTML` 的静态 HTML 列表页解析。
- `SelectorDebugTest`: 本地快速调试工具，不启动 Spring，直接调试一个目标网站选择器。
- `DetailRouter`: 对常见详情页做模板化解析，未知站点用 `GenericExtractor` 兜底。
- `GenericExtractor`: 使用 readability + 启发式规则从详情页抽正文、日期、文号、机构。

当前 `type=HTML` 的关键配置形态是:

```json
{
  "url": "https://example.gov.cn/list/",
  "list_selector": "ul.list > li",
  "detail_selectors": {
    "title": "a[href]",
    "url": "a[href]",
    "publishDate": "span.date",
    "summary": "p.summary"
  }
}
```

注意: 这里的 `detail_selectors` 在 `HtmlRunner` 里当前主要是从列表项内部提取字段，并不等同于一定会抓详情页正文。真正的详情页解析目前由 `DetailRouter` 和 `GenericExtractor` 这条 frontier 链路承担。

## 三、为什么你会“看得出匹配错了，但不知道写什么”

选择器调试通常错在四类地方。

### 1. 把页面里的所有 `li` 都当成政策列表

政府网站里导航栏、面包屑、友情链接、分页也常常是 `ul/li`。如果写:

```css
li
```

会把很多无关内容都选进来。

正确思路是先找到政策列表外层容器，再约束子节点:

```css
ul.u-list > li
div.news-list > ul > li
div.zcfg-list li
```

判断标准不是“能选到”，而是“数量、顺序、文本是否刚好等于页面上的政策列表”。

### 2. 在整个页面找日期，而不是在当前行里找日期

如果在整个 `Document` 上找 `span`，很容易拿到页头、导航、侧栏里的日期或别的文字。

当前调试工具的好处是它在每个 `item` 内部执行:

```java
Element dateEl = item.selectFirst(ITEM_DATE_SELECTOR);
```

所以你写日期选择器时，应当站在“当前这一行”的视角:

```css
span.date
span.time
time
li > span:last-child
```

不建议一开始就写太宽的:

```css
li span
span
```

因为它经常选到第一个无关 `span`。

### 3. `:last-child` 和你肉眼看到的“最后一个”不一定相同

`li > span:last-child` 的含义是“这个 span 必须是 li 的最后一个子元素”。如果最后还有隐藏元素、脚本、分页标签，或者日期并不是直接子元素，它就会失效。

更稳的选择顺序:

1. 有 class 优先: `span.date`、`.date`、`.time`。
2. 有标签语义优先: `time`。
3. 结构稳定时再用位置: `li > span:last-child`。
4. 实在没有结构时，用整行文本 + 日期正则兜底。

### 4. 列表页选择器和详情页选择器混在一起

列表页通常只适合拿:

- 标题
- 链接
- 列表页日期
- 摘要，如果列表上有

详情页才适合拿:

- 正文
- 发布机构
- 文号
- 更准确的发布日期

如果你在列表页强行找 `div.TRS_Editor`，大概率找不到，因为这个节点只在详情页存在。

## 四、你应该掌握的最小调试原理

### 1. DOM 树

浏览器页面可以理解为:

```text
document
└── body
    ├── header
    ├── nav
    ├── main
    │   └── ul.u-list
    │       ├── li
    │       │   ├── a
    │       │   └── span.date
    │       └── li
    └── footer
```

你要找的是 `main` 里的政策列表，不是整个页面里所有 `a` 或所有 `li`。

### 2. CSS Selector

常用选择器不需要多，当前阶段掌握这些就够:

```css
a[href]              /* 带 href 的链接 */
.date                /* class=date 的节点 */
span.date            /* span 且 class=date */
ul.u-list > li       /* ul.u-list 的直接 li 子元素 */
div.list li          /* div.list 下面任意层级的 li */
h1, div.article-title /* 多个备选选择器 */
```

### 3. 绝对定位与相对定位

列表行选择器是从整页找:

```java
doc.select(LIST_ITEM_SELECTOR)
```

字段选择器是从每条记录内部找:

```java
item.selectFirst(ITEM_LINK_SELECTOR)
```

这就是“相对定位”。理解这个以后，你就会知道:

- `LIST_ITEM_SELECTOR` 要写到一条记录的外层。
- `ITEM_LINK_SELECTOR` 要写当前记录内部的链接。
- `ITEM_DATE_SELECTOR` 要写当前记录内部的日期。

### 4. 页面来源判断

调试前先在浏览器确认数据来源:

- 查看网页源代码里能搜到标题: 静态 HTML，优先用 `HtmlRunner`。
- Network 里有 JSON 接口返回列表: 优先用 `HttpJsonRunner`。
- 源代码没有数据，但 Elements 面板有: JS 渲染，后续用 Python/Playwright。
- 有 RSS: 优先用 `RssRunner`，最稳。

## 五、标准调试流程

### 步骤 1: 判断页面类型

打开目标网站后做三件事:

1. 在浏览器页面复制一条政策标题。
2. 打开“查看网页源代码”，搜索标题。
3. 打开 DevTools 的 Network，刷新页面，搜索标题。

判断结果:

- 源代码能搜到: 静态 HTML。
- 某个 XHR/Fetch 响应能搜到: JSON 接口。
- 只有 Elements 面板里能看到: JS 渲染。
- 页面提供 RSS/XML: 直接走 RSS。

### 步骤 2: 找列表记录

在 Elements 面板中点中一条政策标题，向上找“重复出现的一整行”。

好的列表项通常长这样:

```html
<li>
  <a href="./202404/t20240401.html">政策标题</a>
  <span class="date">2024-04-01</span>
</li>
```

那么:

```java
LIST_ITEM_SELECTOR = "ul.u-list > li";
ITEM_LINK_SELECTOR = "a[href]";
ITEM_DATE_SELECTOR = "span.date";
```

### 步骤 3: 在浏览器 Console 里快速验证

先验证数量:

```js
document.querySelectorAll("ul.u-list > li").length
```

再验证前几条文本:

```js
[...document.querySelectorAll("ul.u-list > li")].slice(0, 5).map(x => x.innerText)
```

再验证链接:

```js
[...document.querySelectorAll("ul.u-list > li")].slice(0, 5).map(x => x.querySelector("a[href]")?.href)
```

再验证日期:

```js
[...document.querySelectorAll("ul.u-list > li")].slice(0, 5).map(x => x.querySelector("span.date")?.innerText)
```

### 步骤 4: 放入 `SelectorDebugTest`

把验证过的选择器填进去:

```java
static final String LIST_ITEM_SELECTOR = "ul.u-list > li";
static final String ITEM_LINK_SELECTOR = "a[href]";
static final String ITEM_DATE_SELECTOR = "span.date";
```

运行后看四个结果:

- 列表行数量是否合理。
- 前 5 条标题是否都是政策标题。
- 链接是否是详情页 URL。
- 日期是否能被解析成 `LocalDate`。

### 步骤 5: 调详情页

把 `TEST_DETAIL` 改为 `true`，检查:

```java
static final String DETAIL_TITLE_SEL = "h1.title, h1";
static final String DETAIL_CONTENT_SEL = "div.TRS_Editor, div.article-content";
static final String DETAIL_DATE_SEL = "div.article-time span, span.time";
static final String DETAIL_AGENCY_SEL = "div.article-source, span.source";
```

详情页通过标准:

- 标题不为空，且不是网站名。
- 正文前 200 字是正文，不是导航或版权。
- 日期能解析。
- 发布机构能拿到则拿，拿不到可由后续归一化兜底。

### 步骤 6: 注册 SQL

选择器稳定后，再写入 `policy_data_source`。不要一边猜选择器一边直接进数据库，否则调试反馈太慢。

## 六、当前阶段网站分级

### A 类: 当前优先支持

特征:

- 列表页 HTML 里直接有标题和链接。
- 每条记录结构重复。
- 详情页正文在固定容器里，例如 `div.TRS_Editor`。
- 无登录、无验证码、无复杂翻页状态。

策略:

- 用 `SelectorDebugTest` 调选择器。
- 用 `type=HTML` 注册数据源。
- 必要时给 `DetailRouter` 增加详情模板。

### B 类: 可以支持，但优先级第二

特征:

- 列表来自 JSON 接口。
- Network 里能看到稳定接口。
- 参数相对简单，比如 page、pageSize、channelId。

策略:

- 用 `HttpJsonRunner` 或新增类似配置。
- 不强行写 CSS 选择器。

### C 类: 后续再做

特征:

- 必须等待 JS 渲染。
- 有复杂搜索条件、token、验证码、登录态。
- 反爬明显。

策略:

- 用 Python/Playwright。
- 先输出标准 RawDoc 或 URL Candidate。
- 不放在当前“清晰格式网站”阶段硬啃。

## 七、选择器写法备忘

列表行:

```css
ul.u-list > li
div.list > ul > li
div.news-list li
div.zw-list .item
```

标题/链接:

```css
a[href]
h3 a[href]
.title a[href]
```

日期:

```css
span.date
span.time
.date
time
li > span:last-child
```

详情标题:

```css
h1
h1.title
div.article-title
```

详情正文:

```css
div.TRS_Editor
div.article-content
div#UCAP-CONTENT
div.pages-content
article
```

发布机构:

```css
span.source
div.source
div.article-source
```

## 八、我将如何帮你完成当前阶段

我会按“一个网站一张调试卡”的方式帮你推进，而不是一次性改一堆代码。

每个网站的交付物:

1. 网站类型判断: HTML / JSON / RSS / JS 渲染。
2. 列表页选择器:
   - `LIST_ITEM_SELECTOR`
   - `ITEM_LINK_SELECTOR`
   - `ITEM_DATE_SELECTOR`
3. 详情页选择器:
   - 标题
   - 正文
   - 日期
   - 发布机构
4. 调试输出结论:
   - 抽到多少条。
   - 前 5 条是否正确。
   - 日期是否能解析。
   - 详情页正文是否正确。
5. 最终 SQL:
   - 可直接插入 `policy_data_source`。

推荐工作节奏:

1. 你给我目标网站 URL。
2. 我先判断它属于 A/B/C 哪一类。
3. A 类我直接给出可运行选择器和 SQL。
4. B 类我找接口并整理 JSON 字段映射。
5. C 类我先标记为后续 Playwright，不拖慢当前阶段。

## 九、建议补强的调试工具能力

当前 `SelectorDebugTest` 已经能用，但为了降低你调试时的心智负担，建议后续增加这些能力:

### 1. 打印每条 item 的原始 HTML 片段

当标题或日期错了时，只看 text 不够。打印 `item.outerHtml()` 的前 500 字，可以马上看到选择器到底框住了什么。

### 2. 增加候选选择器探测

自动测试一组常见选择器:

```text
ul li
ul.u-list li
div.list li
.news-list li
.list li
```

输出每个选择器命中的数量和前 3 条文本，帮助你从“猜”变成“比较”。

### 3. 日期增加正则兜底

`SelectorDebugTest` 已经支持 `2024年1月1日`、`2024/1/1`、`2024-1-1`。建议让 `HtmlRunner` 的日期解析也保持一致，避免调试工具能解析，正式 Runner 却解析失败。

### 4. 区分列表字段和详情字段

当前 `HtmlRunner` 的 `detail_selectors` 实际用于列表项内部抽取字段，命名容易误导。后续可以拆成:

```json
{
  "list_selector": "ul.u-list > li",
  "list_field_selectors": {
    "title": "a[href]",
    "url": "a[href]",
    "publishDate": "span.date"
  },
  "detail_selectors": {
    "title": "h1",
    "content": "div.TRS_Editor",
    "publishDate": "span.time"
  }
}
```

这样更符合你的调试直觉。

## 十、当前阶段验收标准

一个清晰格式网站调通，至少满足:

- 列表页命中数量合理，不混入导航、分页、友情链接。
- 前 5 条标题、链接、日期与页面肉眼看到一致。
- 详情页正文命中正文区域，不包含大量导航、页脚、版权信息。
- 日期能解析为 `LocalDate`。
- 生成的 SQL 能注册为 `type=HTML` 数据源。
- 再次运行不会因为空标题、空 URL、错误日期导致脏数据进入库。

## 十一、你接下来最该练什么

按优先级:

1. 练 DevTools Elements: 会从标题节点向上找重复的列表行。
2. 练 Console 验证: 会用 `document.querySelectorAll(...).length` 看命中数量。
3. 练相对选择器: 明白 `item.selectFirst("a[href]")` 是在当前行内找。
4. 练 Network 判断: 能分辨 HTML、JSON、JS 渲染。
5. 练详情页正文容器: 熟悉 `TRS_Editor`、`article-content`、`UCAP-CONTENT` 这类政府网站常见正文节点。

只要掌握这些，就足够完成当前阶段“清晰格式网站”的爬取调试。
