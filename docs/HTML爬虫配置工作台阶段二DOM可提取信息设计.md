# HTML 爬虫配置工作台阶段二 DOM 可提取信息设计

## 1. 背景

点选式配置工作台不能只基于“用户肉眼看到的页面效果”工作。

原因是：浏览器最终展示的是 HTML 渲染结果，但爬虫真正处理的是 HTML DOM。很多对爬虫有价值的信息并不直接显示在页面上，而是隐藏在元素属性、不可见节点或 HTML 结构中。

典型例子：

```html
<a href="/policy/2024/123.html" title="关于进一步做好数据要素市场建设的通知">
  关于进一步做好...
</a>
```

用户肉眼只能看到：

```text
关于进一步做好...
```

但真正更完整的标题可能在：

```text
title 属性
```

真正的详情页链接在：

```text
href 属性
```

如果前端只允许用户点击可见文本，而不展示 DOM 背后的属性信息，用户会不知道系统到底能提取什么，也会漏掉很多更准确的数据来源。

## 2. 核心原则

### 2.1 点选元素不是直接确定字段值

阶段二必须明确：

```text
用户点选元素 = 选择一个候选 DOM 节点
字段最终取值 = 从该 DOM 节点的可提取信息中选择
```

也就是说，用户点到 `<a>` 元素后，系统不应该立刻默认“标题取文本”或“链接取 href”。系统应该先展示这个元素有哪些可提取项。

例如：

```text
可见文本：关于进一步做好...
属性 title：关于进一步做好数据要素市场建设的通知
属性 href：/policy/2024/123.html
HTML 片段：<a href=\"...\" title=\"...\">关于进一步做好...</a>
```

阶段三再根据用户选择的可提取项生成 `PolicyExtractRule`。

### 2.2 页面视图和 DOM 信息视图必须并存

阶段二前端至少提供两层信息：

- 页面视图：用户像浏览普通网页一样点击元素。
- DOM 信息视图：用户点击元素后，右侧展示该元素背后的文本、属性、HTML 片段和可见性信息。

第一版不要求做完整 DOM 树浏览器，但必须做“当前点击元素的 DOM 可提取信息面板”。

## 3. 阶段二功能补充

阶段二在原有“点选元素 + 高亮 + 采集上下文”基础上，增加以下功能。

### 3.1 当前元素可提取信息面板

用户点击任意元素后，右侧面板展示：

- 元素标签名。
- 元素是否可见。
- 可见文本。
- `innerHTML` 摘要。
- `outerHTML` 摘要。
- 属性列表。
- 常用属性快捷展示。
- 元素路径。
- 元素尺寸。

面板标题建议：

```text
这个元素可以提取什么？
```

### 3.2 可提取项列表

右侧面板应把可提取项整理成用户能理解的候选项。

候选项类型：

| 类型 | 来源 | 阶段三对应规则 |
|---|---|---|
| 可见文本 | `element.innerText` / `textContent` | `valueType = TEXT` |
| 属性值 | `element.getAttribute(name)` | `valueType = ATTR`, `attrName = name` |
| 内部 HTML | `element.innerHTML` | `valueType = HTML` |
| 固定值 | 用户手动填写 | `valueType = CONST` |

阶段二只展示候选项，不生成最终规则。

阶段二可以在候选项旁边展示“阶段三将如何使用”的说明：

```text
用作标题文本
用作链接地址
用作日期文本
```

但不要在阶段二写入真实规则。

### 3.3 属性展示优先级

属性很多时，需要排序展示。

优先展示：

- `href`
- `title`
- `src`
- `alt`
- `content`
- `value`
- `name`
- `role`
- `aria-label`
- `data-*`

普通属性放在后面。

忽略或折叠：

- `style`
- `class`，可以单独展示，不放入普通属性候选。
- `id`，可以单独展示，不放入普通属性候选。
- `onclick`
- `onmouseover`
- 其他 `on*` 事件属性。

## 4. 数据结构调整

阶段二主文档中的 `ClickedElementInfo` 需要扩展。

```ts
export interface ClickedElementInfo {
  /** 标签名，小写，如 div / a / span / li / tr */
  tagName: string

  /** 元素 id，没有则为空 */
  id?: string

  /** class 列表 */
  classList: string[]

  /** 常用属性，href/title/src/name/data-* 等 */
  attributes: Record<string, string>

  /** 元素文本，去除连续空白并截断 */
  text: string

  /** 内部 HTML 摘要，截断展示 */
  innerHtml: string

  /** 外部 HTML 摘要，截断展示 */
  outerHtml: string

  /** 元素当前是否可见 */
  computedVisible: boolean

  /** 从 body 到当前元素的结构路径 */
  cssPath: string[]

  /** 从 body 到当前元素的同级索引路径，用于后端定位原始元素 */
  indexPath: number[]

  /** 当前元素在页面中的 nth-of-type 路径，可选 */
  nthPath?: string

  /** 元素在 iframe viewport 内的位置 */
  bounding?: {
    x: number
    y: number
    width: number
    height: number
  }

  /** 从当前元素整理出的可提取项 */
  extractableCandidates: ExtractableCandidate[]
}
```

新增：

```ts
export type ExtractableCandidateType = 'TEXT' | 'ATTR' | 'HTML'

export interface ExtractableCandidate {
  /** 候选类型 */
  type: ExtractableCandidateType

  /** 展示名称，如 可见文本 / href 属性 / title 属性 / 内部 HTML */
  label: string

  /** 候选值，截断后用于展示 */
  previewValue: string

  /** 候选值原始长度 */
  length: number

  /** ATTR 类型对应属性名 */
  attrName?: string

  /** 是否推荐优先展示 */
  recommended?: boolean
}
```

## 5. 采集规则

### 5.1 文本采集

文本取值：

```text
element.innerText || element.textContent
```

处理规则：

- 合并连续空白。
- 去掉首尾空白。
- 展示时截断到 300 字符。
- 仍保留原始长度。

### 5.2 HTML 采集

采集：

- `innerHTML`
- `outerHTML`

处理规则：

- 展示时截断到 1000 字符。
- 不在主页面直接渲染 HTML，只用代码块或纯文本展示。
- 不执行 HTML 中的脚本。

### 5.3 可见性判断

`computedVisible` 可按以下条件判断：

- `display !== 'none'`
- `visibility !== 'hidden'`
- `opacity !== '0'`
- `bounding.width > 0`
- `bounding.height > 0`

阶段二只做前端判断，不需要完全精确。

### 5.4 属性采集

采集属性时：

- 忽略 `on*` 事件属性。
- 忽略超长属性值，展示截断。
- 保留 `href/title/src/alt/content/value/name/role/aria-label/data-*`。
- `href` 不在前端解析为绝对 URL，阶段三/后端统一处理。

## 6. 交互设计

### 6.1 用户点击元素后

右侧面板展示：

```text
你选择了一个 a 元素

这个元素可以提取：
- 可见文本：关于进一步做好...
- href 属性：/policy/2024/123.html
- title 属性：关于进一步做好数据要素市场建设的通知
- 内部 HTML：关于进一步做好...
```

### 6.2 字段模式下的提示

如果当前模式是“标题”，面板可以提示：

```text
当前正在选择：标题
你可以选择：
- 用可见文本作为标题
- 用 title 属性作为标题
```

如果当前模式是“链接”，面板可以提示：

```text
当前正在选择：链接
推荐使用 href 属性作为链接地址
```

阶段二只展示推荐，不生成规则。

### 6.3 列表项模式下的提示

如果当前模式是“列表项”，面板提示：

```text
当前正在选择：一条完整列表记录
请尽量点击包含标题、日期和链接的整行或整块区域。
```

列表项模式下依然展示 DOM 可提取信息，但强调它的用途是“确定一条数据边界”，不是提取字段值。

## 7. 与阶段三的关系

阶段三生成规则时，需要基于阶段二的两个信息：

1. 用户点了哪个 DOM 元素。
2. 用户想从这个 DOM 元素里取哪个可提取项。

阶段二先保存：

```ts
ElementSelection {
  mode
  info
  selectedAt
}
```

阶段三可以扩展为：

```ts
FieldSelection {
  mode
  info
  selectedCandidate
  selectedAt
}
```

然后后端根据 `selectedCandidate` 生成：

```text
TEXT → valueType = TEXT
ATTR href → valueType = ATTR, attrName = href
ATTR title → valueType = ATTR, attrName = title
HTML → valueType = HTML
```

## 8. 阶段二验收补充

除阶段二主文档验收标准外，还必须满足：

- 点击元素后，右侧能看到该元素的文本、属性、`innerHTML`、`outerHTML` 摘要。
- 点击 `<a>` 元素时，能看到 `href`、`title` 等属性候选。
- 点击图片时，能看到 `src`、`alt` 等属性候选。
- 点击隐藏或尺寸为 0 的元素时，能展示 `computedVisible = false`。
- 右侧能展示“这个元素可以提取什么”的候选列表。
- 阶段二不把候选项保存成正式规则，只作为阶段三输入。

## 9. 当前阶段不做

以下能力暂不做：

- 全页面 DOM 树浏览器。
- 全页面隐藏元素搜索。
- `meta` 标签自动扫描。
- `input[type=hidden]` 全局扫描。
- `script` 变量解析。
- 不可见元素直接点选。

这些能力放到后续高级模式或阶段五扩展。

