# HTML 爬虫使用手册

## 一句话说明

这个系统让你**不写代码**就能爬取静态 HTML 网页。你只需要打开浏览器开发者工具，找到目标元素的 CSS 选择器，然后在数据库里配几张表就行。

---

## 目录

- [1. 概念速览：爬虫怎么工作](#1-概念速览爬虫怎么工作)
- [2. 第一步：用浏览器审查目标网站](#2-第一步用浏览器审查目标网站)
- [3. 配置表详解](#3-配置表详解)
  - [3.1 policy_data_source — 注册一个站点](#31-policy_data_source--注册一个站点)
  - [3.2 policy_crawl_page — 定义列表页和详情页](#32-policy_crawl_page--定义列表页和详情页)
  - [3.3 policy_extract_rule — 定义提取规则](#33-policy_extract_rule--定义提取规则)
  - [3.4 policy_pagination_rule — 翻页配置](#34-policy_pagination_rule--翻页配置)
- [4. 实战案例：发改委通知公告](#4-实战案例发改委通知公告)
- [5. 常用提取技巧](#5-常用提取技巧)
- [6. 排错指南](#6-排错指南)

---

## 1. 概念速览：爬虫怎么工作

整个流程只有三步：

```text
列表页                          每个列表项                      最终产出
┌─────────────────┐      ┌──────────────────┐         ┌──────────────┐
│ <ul class="list">│      │ <li>              │         │ 标题: xxx     │
│   <li>           │  →   │   <a href="...">标题</a> │  →  │ URL: xxx      │
│     <a>标题</a>   │      │   <span>2024-01-01</span>│     │ 日期: 2024-..│
│     <span>日期</span>│     │ </li>              │         │ 正文: xxx     │
│   </li>           │      └──────────────────┘         └──────────────┘
│ </ul>             │          ↓ (有详情页的话)
└─────────────────┘      ┌──────────────────┐
                         │ 详情页正文、机构、  │
                         │ 文号等补充信息    │
                         └──────────────────┘
```

核心对应关系：

| 页面上的东西 | 配置表 | 关键字段 |
|---|---|---|
| 目标网站 | `policy_data_source` | `type = HTML` |
| 列表页 URL + 每条记录的容器 | `policy_crawl_page` | `page_role = LIST`, `url`, `item_selector` |
| 详情页配置 | `policy_crawl_page` | `page_role = DETAIL` |
| 从元素里取标题/链接/日期 | `policy_extract_rule` | `field_name`, `selector`, `value_type` |
| 翻页 | `policy_pagination_rule` | `mode`, `url_template` |

---

## 2. 第一步：用浏览器审查目标网站

这是最关键的一步。**配置对不对，全看选择器写没写对。**

### 2.1 找到列表容器

打开目标网站的列表页，按 `F12` 打开开发者工具，按 `Ctrl+Shift+C` 进入元素选取模式。

你需要找到**包含所有列表项的那个父元素**下面的**每个列表项**。

举例，假设页面结构是这样的：

```html
<ul class="news-list">
    <li>
        <a href="/article/123.html" title="关于XXX的通知">关于XXX的通知</a>
        <span class="date">2024-01-15</span>
    </li>
    <li>
        <a href="/article/124.html" title="关于YYY的意见">关于YYY的意见</a>
        <span class="date">2024-01-16</span>
    </li>
</ul>
```

**关键选择器：**

| 你想要的 | 选择器写法 | 说明 |
|---|---|---|
| 每个列表项 | `ul.news-list > li` | 这就是 `item_selector`，选中每个 `<li>` |
| 标题链接 | `a[href]` | 在 item 内找带 href 的 `<a>` |
| 日期 | `.date` | 在 item 内找 class 为 date 的元素 |

### 2.2 验证选择器

在 DevTools 的 Console 里输入这行来验证：

```javascript
document.querySelectorAll('ul.news-list > li').length
```

返回的数字就是你列表页有多少条记录。如果返回 0，说明选择器写错了。

### 2.3 找到详情页的正文区域

点进一篇文章，同样用 `Ctrl+Shift+C` 找到正文所在的 `<div>`，例如：

```html
<div class="article-content">
    <p>为贯彻落实...</p>
    <p>一、总体要求...</p>
</div>
```

那详情页正文的选择器就是 `div.article-content`。

---

## 3. 配置表详解

### 3.1 policy_data_source — 注册一个站点

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | 是 | 站点名称，比如"发改委通知公告"。会用作默认的来源字段 |
| `type` | 是 | 固定填 `HTML` |
| `config` | 否 | HTML 类型不读这个字段，留空 |
| `enabled` | 是 | 填 `1` 启用，填 `0` 停用 |
| `cron_expr` | 否 | 定时抓取，比如每天早 8 点：`0 0 8 * * ?` |
| `last_published_at` | 自动 | 增量抓取游标，系统自动维护。早于此日期的文章会被跳过 |

SQL 示例：

```sql
INSERT INTO policy_data_source (name, type, enabled, cron_expr)
VALUES ('某部委政策公告', 'HTML', 1, '0 0 8 * * ?');
```

### 3.2 policy_crawl_page — 定义列表页和详情页

每个站点至少需要一条 `page_role = LIST`，可选一条 `page_role = DETAIL`。

| 字段 | 必填 | 说明 |
|---|---|---|
| `data_source_id` | 是 | 对应 `policy_data_source.id` |
| `page_role` | 是 | `LIST` = 列表页，`DETAIL` = 详情页 |
| `name` | 否 | 给自己看的备注 |
| `url` | LIST必填 | 列表页地址。DETAIL 页一般留空（URL 从列表项提取） |
| `item_selector` | LIST必填 | CSS 选择器，用于选中每条列表项。DETAIL 页不需要 |
| `request_method` | 否 | 固定填 `GET` |
| `timeout_ms` | 否 | 超时毫秒数，默认 15000（15秒） |
| `sort_order` | 否 | 排序，数字小的优先 |
| `enabled` | 是 | `1` 启用 |

SQL 示例：

```sql
-- 列表页
INSERT INTO policy_crawl_page
(data_source_id, page_role, name, url, item_selector, request_method, timeout_ms, sort_order, enabled)
SELECT id, 'LIST', '列表页',
       'https://example.gov.cn/zcfg/',
       'ul.news-list > li',
       'GET', 15000, 1, 1
FROM policy_data_source WHERE name = '某部委政策公告';

-- 详情页（可选）
INSERT INTO policy_crawl_page
(data_source_id, page_role, name, url, item_selector, request_method, timeout_ms, sort_order, enabled)
SELECT id, 'DETAIL', '详情页',
       NULL, NULL,
       'GET', 15000, 2, 1
FROM policy_data_source WHERE name = '某部委政策公告';
```

### 3.3 policy_extract_rule — 定义提取规则

**这是最重要的表。** 每行规则 = 从一个页面元素中提取一个字段。

| 字段 | 必填 | 说明 |
|---|---|---|
| `page_id` | 是 | 对应 `policy_crawl_page.id`。决定这条规则在列表页生效还是详情页生效 |
| `field_name` | 是 | 提取的目标字段名（见下方列表） |
| `selector` | 否 | CSS 选择器。**相对于当前根元素**（列表页是每个 item，详情页是整个页面）。为空则直接取根元素 |
| `value_type` | 是 | 提取方式（见下方表格） |
| `attr_name` | 仅 ATTR | 当 `value_type = ATTR` 时，要取哪个属性 |
| `const_value` | 仅 CONST | 当 `value_type = CONST` 时，固定的值 |
| `regex_pattern` | 否 | 正则表达式。有两个用途：① `value_type = REGEX` 时做提取；② 其他类型时做后处理过滤 |
| `date_format` | 仅日期 | 日期格式，如 `yyyy-MM-dd`。仅对 `field_name = publishDate` 有效 |
| `sort_order` | 否 | 同字段多规则时的优先顺序，数字小的先试 |
| `enabled` | 是 | `1` 启用 |

#### value_type 怎么选

| value_type | 用途 | 需要配合 | 示例 |
|---|---|---|---|
| `TEXT` | 取元素文本内容 | 只需 `selector` | `<span>2024-01-01</span>` → `2024-01-01` |
| `ATTR` | 取 HTML 属性值 | `selector` + `attr_name` | `<a href="/a/123">` → `attr_name=href` 得到 `/a/123` |
| `HTML` | 取元素内部 HTML | 只需 `selector` | 正文带格式时用 |
| `CONST` | 固定值 | `const_value` | 所有文章来源都是"国务院"，直接写死 |
| `REGEX` | 从文本中正则提取 | `selector` + `regex_pattern` | "发布日期：2024-01-01" → 正则提取日期部分 |

#### 可提取的字段名

| field_name | 存入 RawDoc 的哪里 | 特殊处理 |
|---|---|---|
| `title` | 标题 | — |
| `url` | 原文链接 | 相对路径自动补全为绝对路径 |
| `source` | 来源名称 | 默认取 `policy_data_source.name`，配了这个会覆盖 |
| `summary` | 摘要 | — |
| `content` | 正文 | 通常从详情页提取 |
| `issuingAgency` | 发布机构 | 如"国务院办公厅" |
| `documentNumber` | 文号 | 如"国办发〔2024〕1号" |
| `publishDate` | 发布日期 | 自动解析为日期，需配 `date_format` |
| 其他任意名称 | 存入 `metadata` | 比如 `category`、`author` 等自定义字段 |

#### 多规则回退

同一个字段配多条规则、不同的 `sort_order`，系统会按顺序尝试，取第一个提取成功的。

```sql
-- 优先取 a 标签的 title 属性
INSERT INTO policy_extract_rule (page_id, field_name, selector, value_type, attr_name, sort_order, enabled)
SELECT p.id, 'title', 'a[href]', 'ATTR', 'title', 1, 1 FROM ...;

-- 如果 a 没有 title 属性，退而取 a 的文本
INSERT INTO policy_extract_rule (page_id, field_name, selector, value_type, sort_order, enabled)
SELECT p.id, 'title', 'a[href]', 'TEXT', 2, 1 FROM ...;
```

### 3.4 policy_pagination_rule — 翻页配置

| 字段 | 必填 | 说明 |
|---|---|---|
| `page_id` | 是 | 对应 LIST 页的 `policy_crawl_page.id` |
| `mode` | 是 | `NONE` 只抓一页 / `URL_TEMPLATE` 按模板翻页 |
| `url_template` | URL_TEMPLATE 必填 | 翻页 URL 模板，用 `{page}` 当页码占位符 |
| `start_page` | 否 | 起始页码，默认 1 |
| `max_pages` | 否 | 最多翻几页，默认 1 |

**按模板翻页示例：**

网站第 1 页是 `index.html`，第 2 页是 `index_2.html`：

```sql
INSERT INTO policy_pagination_rule (page_id, mode, url_template, start_page, max_pages, enabled)
SELECT p.id, 'URL_TEMPLATE', 'https://example.gov.cn/zcfg/index_{page}.html', 1, 5, 1
FROM ...;
```

这会生成：`index_1.html`, `index_2.html`, `index_3.html`, `index_4.html`, `index_5.html`

> **注意：** 如果网站第一页 URL 是 `index.html`（不带数字），模板里写 `index_{page}.html` 会生成 `index_1.html`，但有的网站第一页确实叫 `index.html` 没有后缀。这种情况暂时只能抓 `index_1.html` 起，第一页会漏掉。后续可以加 `first_page_url` 字段解决。

---

## 4. 实战案例：发改委通知公告

以国家发改委通知公告页面 `https://www.ndrc.gov.cn/xxgk/zcfb/tz/` 为例。

### 4.1 列表页结构

```html
<ul class="u-list">
    <li>
        <a href="/xxgk/zcfb/tz/202401/t20240115_1234567.html"
           title="关于进一步做好XX工作的通知">关于进一步做好XX工作的通知</a>
        <span>2024/01/15</span>
    </li>
    <li>
        <a href="/xxgk/zcfb/tz/202401/t20240116_1234568.html"
           title="关于印发YY方案的通知">关于印发YY方案的通知</a>
        <span>2024/01/16</span>
    </li>
</ul>
```

### 4.2 分析映射

| 网页元素 | 选择器 | 提取什么 | value_type |
|---|---|---|---|
| 每条列表项 | `ul.u-list > li` | — | 这是 `item_selector`，不提取字段 |
| 标题 | `a[href]` | title 属性或文本 | `ATTR` + `title`，回退 `TEXT` |
| 链接 | `a[href]` | href 属性 | `ATTR` + `href` |
| 日期 | `span` | 文本 `2024/01/15` | `TEXT`，配 `date_format = yyyy/MM/dd` |

### 4.3 完整配置 SQL

```sql
-- ① 注册数据源
INSERT INTO policy_data_source (name, type, enabled, cron_expr)
VALUES ('发改委通知公告', 'HTML', 1, '0 0 8 * * ?');

-- ② 配置列表页和详情页
INSERT INTO policy_crawl_page
    (data_source_id, page_role, name, url, item_selector, request_method, timeout_ms, sort_order, enabled)
SELECT id, 'LIST', '列表页',
       'https://www.ndrc.gov.cn/xxgk/zcfb/tz/',
       'ul.u-list > li',
       'GET', 15000, 1, 1
FROM policy_data_source WHERE name = '发改委通知公告'
UNION ALL
SELECT id, 'DETAIL', '详情页',
       NULL, NULL,
       'GET', 15000, 2, 1
FROM policy_data_source WHERE name = '发改委通知公告';

-- ③ 列表页提取规则（从每个 <li> 里取标题、链接、日期）
INSERT INTO policy_extract_rule
    (page_id, field_name, scope, selector, value_type, attr_name, date_format, required, sort_order, enabled)
-- 标题：优先取 a 的 title 属性
SELECT p.id, 'title', 'ITEM', 'a[href]', 'ATTR', 'title', NULL, 1, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST'
UNION ALL
-- 标题兜底：取 a 的文本
SELECT p.id, 'title', 'ITEM', 'a[href]', 'TEXT', NULL, NULL, 1, 2, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST'
UNION ALL
-- 链接
SELECT p.id, 'url', 'ITEM', 'a[href]', 'ATTR', 'href', NULL, 1, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST'
UNION ALL
-- 日期
SELECT p.id, 'publishDate', 'ITEM', 'span', 'TEXT', NULL, 'yyyy/MM/dd', 0, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST';

-- ④ 详情页提取规则（打开每篇文章补充正文、机构、日期等）
INSERT INTO policy_extract_rule
    (page_id, field_name, scope, selector, value_type, attr_name, regex_pattern, date_format, required, sort_order, enabled)
-- 正文（两个选择器用逗号分隔，匹配任意一个）
SELECT p.id, 'content', 'PAGE', 'div.TRS_Editor, div.article-content', 'TEXT', NULL, NULL, NULL, 0, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'DETAIL'
UNION ALL
-- 发布日期（从文本中用正则提取）
SELECT p.id, 'publishDate', 'PAGE', 'div.article-time span, span.time', 'TEXT',
       NULL, '(\\d{4}[年/-]\\d{1,2}[月/-]\\d{1,2}日?)', NULL, 0, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'DETAIL'
UNION ALL
-- 发布机构
SELECT p.id, 'issuingAgency', 'PAGE', 'div.article-source, span.source', 'TEXT',
       NULL, NULL, NULL, 0, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'DETAIL';

-- ⑤ 翻页（只抓第一页）
INSERT INTO policy_pagination_rule (page_id, mode, start_page, max_pages, enabled)
SELECT p.id, 'NONE', 1, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST';
```

---

## 5. 常用提取技巧

### 5.1 CSS 选择器速查

| 场景 | 选择器写法 |
|---|---|
| 按标签+class | `ul.news-list > li` |
| 按 class | `.article-title` |
| 按标签 | `h1` |
| 按属性 | `a[href]`，`a[target="_blank"]` |
| 多选一（或） | `div.content, div.article, div.TRS_Editor` |
| 子元素 | `div.meta > span.time` |
| 后代元素 | `div.meta span`（不限于直接子元素） |

### 5.2 日期提取的三种套路

**套路一：日期在独立标签里，格式规范**

```html
<span class="date">2024-01-15</span>
```
配置：`value_type = TEXT`，`date_format = yyyy-MM-dd`

**套路二：日期混在文字里**

```html
<span>发布时间：2024年01月15日 来源：办公厅</span>
```
配置：`value_type = TEXT`，`regex_pattern = (\d{4}年\d{1,2}月\d{1,2}日?)`

正则 `(\d{4}年\d{1,2}月\d{1,2}日?)` 会匹配并提取出 `2024年01月15日`，系统内置的日期解析器能处理这个格式。

**套路三：日期分散在多个标签里**

```html
<span class="year">2024</span>-<span class="month">01</span>-<span class="day">15</span>
```
这种情况需要先用 `item_selector` 选中父容器，然后用 `REGEX` 在整个容器文本上提取日期。或者，暂时不支持此格式，需要增加 `value_type = COMBINE` 等新规则。

### 5.3 链接的相对路径处理

系统**自动**把相对路径补全为绝对路径。

| 列表页是 | 提取到的 | 最终结果 |
|---|---|---|
| `https://example.gov.cn/zcfg/` | `/a/123.html` | `https://example.gov.cn/a/123.html` |
| `https://example.gov.cn/zcfg/` | `./2024/123.html` | `https://example.gov.cn/zcfg/2024/123.html` |
| `https://example.gov.cn/zcfg/` | `https://example.gov.cn/a/123.html` | `https://example.gov.cn/a/123.html`（不变） |

### 5.4 详情页也提取标题

列表页已经提了标题，为什么详情页还要再提一次？因为列表页可能只显示缩略标题（如"关于进一步做好..."），详情页才有完整标题。配置后详情页的标题会覆盖列表页的。

### 5.5 提取正文带 HTML 格式

如果希望保留段落格式，用 `value_type = HTML` 而非 `TEXT`：

```sql
SELECT p.id, 'content', 'PAGE', 'div.article-content', 'HTML', ...;
```

---

## 6. 排错指南

### 问题：抓了 0 条数据

1. **检查 `item_selector` 是否正确** — 打开目标页面，F12 Console 跑 `document.querySelectorAll('你的选择器').length`
2. **检查网站是否依赖 JavaScript 渲染** — 如果页面内容是用 JS 动态加载的，`Jsoup` 无法执行 JS，需要改用 `PY_SCRIPT` 类型的源，用 Selenium 或 Playwright
3. **检查 `enabled` 都是 `1`** — 数据源、页、规则三层都看

### 问题：标题/链接提取为空

1. **检查选择器相对关系** — `selector` 是相对 `item_selector` 选中元素的，不是相对整个页面的
2. **检查 `value_type`** — 链接要用 `ATTR` + `attr_name = href`，不是 `TEXT`

### 问题：日期总是差一天

数据库时区和网站时区不一致，检查 MySQL 的 `time_zone` 设置。

### 问题：部分文章被跳过了

系统有增量机制：如果 `publishDate` 早于 `policy_data_source.last_published_at`，文章会被跳过。如果这是你第一次抓取，确保 `last_published_at` 为 `NULL`。

### 问题：翻页抓不全

`NEXT_SELECTOR` 模式（自动找"下一页"按钮）尚未实现，暂时只能用 `URL_TEMPLATE`。

---

## 附录：最小配置 Checklist

配置一个新 HTML 站点，你只需要搞定这些：

- [ ] 浏览器里找到列表页，确认页面结构不是 JS 动态渲染的
- [ ] 找到 `item_selector`（每个列表项的容器选择器）
- [ ] 找到标题和链接的选择器
- [ ] 找到日期的选择器和格式
- [ ] （可选）找到详情页正文选择器
- [ ] （可选）找到发布机构选择器
- [ ] 确认翻页方式：不动（NONE）还是 URL 有规律（URL_TEMPLATE）
- [ ] 写 SQL 插入 4 张表
- [ ] 确认所有 `enabled = 1`
- [ ] 跑一次看结果
