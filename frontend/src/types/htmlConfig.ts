export type SelectionMode =
  | 'ITEM'
  | 'TITLE'
  | 'URL'
  | 'PUBLISH_DATE'
  | 'SUMMARY'
  | 'DETAIL_CONTENT'
  | 'ISSUING_AGENCY'
  | 'DOCUMENT_NUMBER'

export interface ModeMeta {
  label: string
  hint: string
  /** 面板顶部提示：当前在选什么 */
  panelTitle: string
  /** 推荐的可提取项类型和说明 */
  recommendations: { type: ExtractableCandidateType; attrName?: string; desc: string }[]
}

export const SELECTION_MODE_META: Record<SelectionMode, ModeMeta> = {
  ITEM: {
    label: '列表项',
    hint: '请点击一条完整的列表记录',
    panelTitle: '正在选择：一条完整列表记录',
    recommendations: [
      { type: 'HTML', desc: '内部 HTML 用于确定列表项边界' },
    ],
  },
  TITLE: {
    label: '标题',
    hint: '请点击列表项中的标题文字',
    panelTitle: '正在选择：标题',
    recommendations: [
      { type: 'TEXT', desc: '用可见文本作为标题' },
      { type: 'ATTR', attrName: 'title', desc: '用 title 属性作为标题（通常更完整）' },
    ],
  },
  URL: {
    label: '链接',
    hint: '请点击可进入详情页的链接',
    panelTitle: '正在选择：链接',
    recommendations: [
      { type: 'ATTR', attrName: 'href', desc: '推荐使用 href 属性作为链接地址' },
    ],
  },
  PUBLISH_DATE: {
    label: '发布日期',
    hint: '请点击发布日期文字',
    panelTitle: '正在选择：发布日期',
    recommendations: [
      { type: 'TEXT', desc: '用可见文本作为日期' },
    ],
  },
  SUMMARY: {
    label: '摘要',
    hint: '请点击摘要内容，可选',
    panelTitle: '正在选择：摘要（可选）',
    recommendations: [
      { type: 'TEXT', desc: '用可见文本作为摘要' },
      { type: 'HTML', desc: '用内部 HTML 保留格式' },
    ],
  },
  DETAIL_CONTENT: {
    label: '详情正文',
    hint: '请点击详情页正文区域',
    panelTitle: '正在选择：详情正文',
    recommendations: [
      { type: 'HTML', desc: '用内部 HTML 保留正文格式' },
      { type: 'TEXT', desc: '用纯文本作为正文' },
    ],
  },
  ISSUING_AGENCY: {
    label: '发布机构',
    hint: '请点击发布机构文字',
    panelTitle: '正在选择：发布机构',
    recommendations: [
      { type: 'TEXT', desc: '用可见文本作为发布机构' },
    ],
  },
  DOCUMENT_NUMBER: {
    label: '文号',
    hint: '请点击文号文字',
    panelTitle: '正在选择：文号',
    recommendations: [
      { type: 'TEXT', desc: '用可见文本作为文号' },
    ],
  },
}

export const STEP_ORDER: SelectionMode[] = [
  'ITEM',
  'TITLE',
  'URL',
  'PUBLISH_DATE',
  'SUMMARY',
  'DETAIL_CONTENT',
  'ISSUING_AGENCY',
  'DOCUMENT_NUMBER',
]

export type ExtractableCandidateType = 'TEXT' | 'ATTR' | 'HTML'

export interface ExtractableCandidate {
  type: ExtractableCandidateType
  /** 展示名称，如 "可见文本" / "href 属性" / "内部 HTML" */
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

export interface ClickedElementInfo {
  tagName: string
  id?: string
  classList: string[]
  attributes: Record<string, string>
  text: string
  /** 内部 HTML 摘要，截断展示 */
  innerHtml: string
  /** 外部 HTML 摘要，截断展示 */
  outerHtml: string
  /** 元素当前是否可见 */
  computedVisible: boolean
  cssPath: string[]
  indexPath: number[]
  nthPath?: string
  bounding?: {
    x: number
    y: number
    width: number
    height: number
  }
  /** 从当前元素整理出的可提取项 */
  extractableCandidates: ExtractableCandidate[]
}

export interface ElementSelection {
  mode: SelectionMode
  info: ClickedElementInfo
  selectedAt: number
  /** 用户选择的候选项在 extractableCandidates 中的索引，未选时为 -1 */
  selectedCandidateIndex: number
}

export interface FrameMessage {
  source: 'html-config-frame'
  type: 'hover' | 'select' | 'ready' | 'error'
  payload?: unknown
}

export interface ParentMessage {
  source: 'html-config-parent'
  type: 'set-mode' | 'clear-hover' | 'set-selections' | 'clear-selection'
  payload?: unknown
}

export interface PageLoadResult {
  html: string
  finalUrl: string
  statusCode: number
  title: string
  warnings: string[]
  error: string | null
}

export interface PageState {
  sourceName: string
  listUrl: string
  html: string
  title: string
  finalUrl: string
  statusCode: number
  loading: boolean
  warnings: string[]
  errors: string[]
}

export interface SelectionState {
  currentMode: SelectionMode
  selections: Partial<Record<SelectionMode, ElementSelection>>
  hoveredElement: ClickedElementInfo | null
}