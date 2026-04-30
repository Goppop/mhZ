# 点选式 HTML 爬虫配置工作台 PRD

## 1. 产品背景

当前系统已有 `HtmlRunner`，可以通过数据库配置抓取指定静态 HTML 网站。现有能力基于 `policy_crawl_page`、`policy_extract_rule`、`policy_pagination_rule` 等配置表完成列表页、字段和详情页抽取。

当前问题：

- 配置依赖 SQL 和 CSS 选择器，对非技术用户学习成本高。
- 用户配置后无法即时知道是否选对，只能运行任务后看结果。
- `HtmlRunner` 内部逻辑较单一，复杂 HTML 页面支持不足。
- 如果前端预览、后端保存、正式抓取各写一套逻辑，后续维护会产生不一致和返工。

因此需要建设一个点选式 HTML 爬虫配置工作台，让用户通过页面点选完成配置，并实时看到系统按当前配置提取出的结果。

## 2. 产品目标

第一版目标：

- 用户输入静态网页 URL 后，可以看到后端抓取到的页面快照。
- 用户通过点击页面元素配置列表项、标题、链接、日期、正文等字段。
- 系统根据用户点选自动生成 `HtmlRunner` 可执行的配置。
- 系统实时展示当前配置对页面的提取结果、命中率和错误原因。
- 配置可保存、编辑、复制、删除，并可立即运行抓取。
- 预览逻辑、保存前校验逻辑、正式抓取逻辑必须共用后端同一套 HTML 抽取核心。

非目标：

- 第一版不支持 JS 动态渲染页面。
- 第一版不支持登录态、验证码、强反爬网站。
- 第一版不做 Playwright/Selenium 浏览器渲染。
- 第一版不做完全自动化爬虫生成，只做点选辅助配置。

## 3. 目标用户

### 3.1 默认用户

业务人员或非技术用户。

特点：

- 不理解 CSS 选择器。
- 不理解 SQL 配置表。
- 只知道自己想抓“标题、链接、日期、正文”等信息。

默认模式要求：

- 页面不暴露复杂技术概念。
- 用户通过点击网页元素完成配置。
- 系统用自然语言反馈是否配置成功。

### 3.2 高级用户

开发、运维或懂 HTML/CSS 的用户。

高级模式要求：

- 可以查看自动生成的 CSS 选择器。
- 可以手动修改 `item_selector` 和字段选择器。
- 可以查看 `valueType`、`attrName`、`regexPattern`、`dateFormat` 等规则参数。
- 可以查看原始错误信息和调试详情。

## 4. 核心产品原则

### 4.1 前端只做交互，不做抽取

前端不得实现独立的字段提取、日期解析、URL 解析、规则校验、分页解析或爬虫逻辑。

前端只负责：

- 展示页面快照。
- 捕获用户点选行为。
- 展示后端返回的规则建议。
- 展示后端返回的预览结果。
- 提交保存和运行请求。

### 4.2 后端统一抽取核心

正式抓取、实时预览、保存前校验必须共用后端同一套抽取逻辑。

推荐抽象：

- `HtmlExtractionEngine`：统一执行列表页、字段、详情页抽取。
- `HtmlRequestClient`：统一请求静态 HTML。
- `HtmlRuleEvaluator`：统一解释 `PolicyExtractRule`。
- `HtmlSelectorGenerator`：统一根据点选元素生成稳定选择器。
- `HtmlConfigPreviewService`：提供配置页预览，但内部调用统一抽取核心。

### 4.3 配置必须兼容 HtmlRunner

页面生成的配置最终必须保存为当前系统已有模型：

- `policy_data_source`
- `policy_crawl_page`
- `policy_extract_rule`
- `policy_pagination_rule`

保存后的配置必须能被 `HtmlRunner.fetch()` 直接执行。

## 5. 第一版功能范围

### 5.1 数据源配置管理

功能：

- 新建 HTML 数据源配置。
- 编辑已有 HTML 数据源配置。
- 复制已有配置。
- 删除配置。
- 查看配置列表。
- 启用或停用配置。

配置字段：

- 数据源名称。
- 列表页 URL。
- 请求超时时间。
- 请求头 JSON，可选。
- 是否启用。
- Cron 表达式，可选。

### 5.2 静态页面加载

用户输入列表页 URL，点击“加载页面”。

系统行为：

- 后端请求目标 URL。
- 后端返回静态 HTML、页面标题、最终 URL、状态码、错误信息。
- 前端使用 `iframe srcdoc` 渲染后端返回的 HTML。
- 前端向 iframe 注入点选脚本和高亮样式。

页面反馈：

- 加载成功：显示页面快照。
- 加载失败：显示 HTTP 状态码、超时、DNS 错误或其他错误。
- 疑似 JS 渲染：提示“当前静态 HTML 中未发现明显内容，可能是 JS 动态渲染页面，第一版暂不支持”。

### 5.3 列表项点选

用户选择“配置列表项”模式后，在页面中点击一条列表记录。

系统行为：

- 前端捕获被点击元素的 DOM 信息。
- 前端发送点选上下文给后端。
- 后端生成候选 `item_selector`。
- 后端用候选选择器在当前页面中匹配所有列表项。
- 后端返回命中数量、前 10 条 item 文本、候选选择器和置信度。

用户反馈：

- 页面高亮被点击元素。
- 页面高亮同类命中的所有列表项。
- 右侧显示“命中 N 条列表项”。
- 如果命中少于 2 条，提示用户可能选错了层级。

成功标准：

- `item_selector` 能稳定命中当前列表页的多条记录。

### 5.4 列表字段点选

支持字段：

- `title`：标题。
- `url`：原文链接。
- `publishDate`：发布日期。
- `source`：来源。
- `summary`：摘要。

用户选择字段类型后，在列表项中的对应元素上点击。

系统行为：

- 前端发送字段名、点选元素上下文、当前 `item_selector` 给后端。
- 后端生成相对于单个 item 的字段选择器。
- 后端生成 `PolicyExtractRule` 建议。
- 后端使用统一抽取核心对前 10 条 item 执行预览。

字段规则生成要求：

- `title` 默认优先 `TEXT`，如果元素有 `title` 属性，可建议 `ATTR title` 作为兜底或优先规则。
- `url` 默认使用 `ATTR href`。
- `publishDate` 默认使用 `TEXT`，并尝试解析日期。
- `source` 可以使用 `TEXT` 或 `CONST`。
- `summary` 默认使用 `TEXT`。

用户反馈：

- 展示前 10 条提取结果。
- 展示字段命中率。
- 展示空值数量。
- 展示日期解析成功率。
- 展示 URL 是否已补全为绝对地址。

### 5.5 详情页配置

详情页来源：

- 默认使用列表页预览中第一条有效 URL。
- 用户也可以手动输入详情页 URL。

支持字段：

- `title`：详情页完整标题。
- `content`：正文。
- `publishDate`：发布日期。
- `issuingAgency`：发布机构。
- `documentNumber`：文号。

系统行为：

- 后端请求详情页静态 HTML。
- 前端用 `iframe srcdoc` 展示详情页快照。
- 用户点击正文、机构、日期等元素。
- 后端生成详情页字段规则。
- 后端使用统一抽取核心预览详情页字段结果。

正文反馈：

- 正文长度。
- 正文前 1000 字预览。
- 是否命中正文选择器。
- 正文过短时提示可能选错区域。

### 5.6 分页配置

第一版支持：

- `NONE`：只抓当前列表页。
- `URL_TEMPLATE`：按 URL 模板生成分页。

预留但第一版可不完整实现：

- `NEXT_SELECTOR`：根据下一页按钮翻页。

页面表单：

- 分页模式。
- URL 模板。
- 起始页。
- 最大页数。
- 分页预览结果。

分页预览要求：

- 展示将要抓取的前几个分页 URL。
- 对前 1 到 3 页执行 item 命中预览。
- 如果某页 item 为 0，提示用户检查分页配置。

### 5.7 实时预览

配置过程中的每一步都应可触发预览。

预览内容：

- item 命中数量。
- 前 10 条 item 文本。
- 前 10 条字段提取结果。
- 字段命中率。
- 日期解析成功率。
- URL 补全结果。
- 详情页正文长度。
- 错误和警告。

预览必须由后端统一抽取核心生成，不能由前端自行计算最终结果。

### 5.8 保存配置

用户点击“保存配置”后，系统保存：

- `policy_data_source`
- `policy_crawl_page` 的 LIST 记录。
- `policy_crawl_page` 的 DETAIL 记录，可选。
- `policy_extract_rule` 的列表页规则。
- `policy_extract_rule` 的详情页规则。
- `policy_pagination_rule`

保存前校验：

- 数据源名称不能为空。
- 列表页 URL 不能为空。
- `item_selector` 不能为空。
- `title` 至少有一条有效规则。
- `url` 至少有一条有效规则。
- `ATTR` 类型必须有 `attrName`。
- `CONST` 类型必须有 `constValue`。
- `REGEX` 类型必须有 `regexPattern`。
- 至少一条样例 item 能提取出标题和 URL。

### 5.9 运行验证

保存后用户可以点击“立即运行”。

系统调用现有抓取能力，展示：

- 抓取数量。
- 去重数量。
- 入库数量。
- 匹配数量。
- 错误数量。
- 最近写入文档。

## 6. 页面结构

### 6.1 页面整体布局

推荐三栏布局：

- 左侧：步骤和配置状态。
- 中间：网页快照预览和点选区域。
- 右侧：预览结果、规则详情、高级配置。

### 6.2 步骤

步骤 1：输入 URL 并加载页面。

步骤 2：点选列表项。

步骤 3：点选列表字段。

步骤 4：配置分页。

步骤 5：点选详情页字段。

步骤 6：保存配置。

步骤 7：立即运行并查看结果。

### 6.3 默认模式

默认模式展示：

- 当前需要用户点击什么。
- 当前配置是否完成。
- 命中数量和样例结果。
- 自然语言错误提示。

默认模式不展示：

- 复杂 CSS 选择器。
- 数据库字段。
- 原始 JSON。

### 6.4 高级模式

高级模式展示：

- `item_selector`。
- 字段规则表。
- `valueType`。
- `attrName`。
- `regexPattern`。
- `dateFormat`。
- 预览接口原始响应。
- 保存前将写入的配置结构。

## 7. 点选交互定义

### 7.1 点选模式

页面必须有明确的当前点选模式：

- 选择列表项。
- 选择标题。
- 选择链接。
- 选择发布日期。
- 选择摘要。
- 选择详情正文。
- 选择发布机构。
- 选择文号。

不同模式下，同一次点击含义不同。

### 7.2 高亮规则

- 鼠标悬停元素时显示浅色边框。
- 点击选中元素时显示主色边框。
- 同类命中元素显示统一背景色。
- 已配置字段可以用不同颜色标记。

### 7.3 撤销和重选

每个字段必须支持：

- 重选。
- 删除。
- 禁用。
- 添加兜底规则。

## 8. 后端接口需求

### 8.1 能力描述

`GET /api/html-config/capabilities`

用途：

- 告诉前端当前后端支持哪些字段、值类型、分页模式和规则约束。

返回内容：

- 支持字段列表。
- 支持 `valueType` 列表。
- 支持分页模式。
- 是否支持详情页。
- 是否支持多元素合并。
- 是否支持动态渲染。
- 默认校验规则。

### 8.2 加载页面

`POST /api/html-config/load-page`

请求：

- `url`
- `headers`
- `timeoutMs`

返回：

- `finalUrl`
- `statusCode`
- `title`
- `html`
- `warnings`
- `errors`

### 8.3 建议列表项规则

`POST /api/html-config/suggest-item-selector`

请求：

- `url`
- `htmlSnapshotId` 或 `html`
- `selectedElementPath`
- `selectedElementInfo`

返回：

- `itemSelector`
- `itemCount`
- `sampleItems`
- `confidence`
- `warnings`

### 8.4 建议字段规则

`POST /api/html-config/suggest-field-rule`

请求：

- `pageRole`
- `fieldName`
- `itemSelector`
- `selectedElementPath`
- `selectedElementInfo`
- `currentRules`

返回：

- `suggestedRules`
- `preview`
- `warnings`
- `errors`

### 8.5 预览列表字段

`POST /api/html-config/preview-list`

请求：

- `url`
- `itemSelector`
- `listRules`
- `paginationRule`
- `headers`
- `timeoutMs`

返回：

- `itemCount`
- `samples`
- `fieldStats`
- `warnings`
- `errors`

### 8.6 预览详情页

`POST /api/html-config/preview-detail`

请求：

- `detailUrl`
- `detailRules`
- `headers`
- `timeoutMs`

返回：

- `fields`
- `contentLength`
- `contentPreview`
- `fieldStats`
- `warnings`
- `errors`

### 8.7 配置 CRUD

接口：

- `GET /api/html-config/sources`
- `GET /api/html-config/sources/{id}`
- `POST /api/html-config/sources`
- `PUT /api/html-config/sources/{id}`
- `POST /api/html-config/sources/{id}/copy`
- `DELETE /api/html-config/sources/{id}`

### 8.8 运行配置

可复用现有接口：

- `POST /api/crawl/run/{id}`
- `GET /api/crawl/documents`
- `GET /api/crawl/status`

## 9. 与 HtmlRunner 的适配规则

### 9.1 列表页

用户点选的列表项最终保存为：

- `PolicyCrawlPage.pageRole = LIST`
- `PolicyCrawlPage.url = 列表页 URL`
- `PolicyCrawlPage.itemSelector = 生成的 item_selector`

`HtmlRunner` 执行时必须通过该选择器获取列表项。

### 9.2 列表字段

列表字段规则保存为 `PolicyExtractRule`。

要求：

- 字段选择器必须相对于单个 item。
- 不能保存相对于整个 document 的长路径作为列表字段选择器。
- URL 字段必须支持相对路径补全。

### 9.3 详情页字段

详情页字段规则保存为 `PolicyExtractRule`。

要求：

- 字段选择器相对于整个详情页 `Document`。
- 详情页 URL 来源于列表页 `url` 字段。
- 详情页字段可以覆盖列表页已有字段。

### 9.4 统一逻辑

以下行为必须由统一后端逻辑完成：

- CSS 选择器执行。
- 字段值提取。
- URL 补全。
- 日期解析。
- 正则提取。
- 必填字段校验。
- 详情页补充。
- 预览诊断。

## 10. 状态和错误提示

### 10.1 页面加载失败

提示：

- URL 无法访问。
- 请求超时。
- 返回非 200 状态码。
- HTML 内容为空。

### 10.2 选择器无命中

提示：

- 当前选择器未匹配到任何元素。
- 建议重新点选更外层或更稳定的元素。

### 10.3 列表项命中过少

提示：

- 当前列表项只命中 1 条，可能不是列表容器。

### 10.4 字段提取为空

提示：

- 当前字段在样例中为空。
- 如果是链接字段，提示检查是否点击到了 `a` 标签或含有 `href` 的元素。

### 10.5 日期解析失败

提示：

- 已提取到文本，但无法解析为日期。
- 高级模式允许配置 `dateFormat` 或正则。

### 10.6 疑似 JS 渲染

提示：

- 后端静态 HTML 中没有检测到目标内容。
- 当前版本暂不支持 JS 动态渲染。
- 后续可使用浏览器渲染模式。

## 11. MVP 验收标准

第一版完成后必须满足：

- 可以新建、编辑、复制、删除一个 HTML 数据源配置。
- 可以输入静态列表页 URL 并看到页面快照。
- 可以点选列表项，并看到 item 命中数量和高亮。
- 可以点选标题、链接、日期，并看到前 10 条提取结果。
- 可以点选详情页正文，并看到正文长度和正文预览。
- 保存后的配置能写入现有配置表。
- 保存后的配置能被 `HtmlRunner` 正式执行。
- 预览结果和正式抓取结果使用同一套后端抽取核心。
- 失败时页面能明确告诉用户失败原因。

## 12. 迭代计划

### 12.1 阶段一：统一后端抽取核心

目标：

- 从 `HtmlRunner` 中抽出公共抽取逻辑。
- 让 `HtmlRunner` 调用统一核心。
- 为预览接口复用同一核心打基础。

输出：

- `HtmlExtractionEngine`
- `HtmlRequestClient`
- `HtmlRuleEvaluator`
- 基础预览结果模型

### 12.2 阶段二：页面加载和点选原型

目标：

- Vue3 前端加载 URL。
- iframe srcdoc 展示静态 HTML。
- 支持鼠标悬停、点击、高亮。
- 能把点选元素上下文发送给后端。

### 12.3 阶段三：规则建议和实时预览

目标：

- 点选列表项生成 `item_selector`。
- 点选字段生成 `PolicyExtractRule`。
- 实时展示字段提取结果和命中率。

### 12.4 阶段四：配置 CRUD 和运行闭环

目标：

- 保存配置。
- 编辑配置。
- 复制配置。
- 删除配置。
- 保存后立即运行。
- 查看最近入库文档。

### 12.5 阶段五：复杂能力增强

目标：

- 多页分页预览。
- `NEXT_SELECTOR`。
- 多详情页样例验证。
- 选择器推荐优化。
- 多元素合并。
- 正文排除选择器。
- JS 动态渲染支持。

## 13. 后续预留能力

后续可以扩展：

- 浏览器渲染模式。
- 登录态 Cookie 配置。
- 代理配置。
- 请求重试。
- 限速。
- 多字段组合提取。
- 附件链接提取。
- 正文清洗规则。
- 站点模板库。
- 配置导入导出。
- 一键生成 SQL。

