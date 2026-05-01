// ============================================================
// PRD V2 第 10.3 节 — SelectionState
// ============================================================

export type PageRole = 'LIST' | 'DETAIL'

export type ListFieldName = 'title' | 'url' | 'publishDate' | 'source' | 'summary'
export type DetailFieldName = 'title' | 'content' | 'publishDate' | 'issuingAgency' | 'documentNumber'

export const LIST_FIELD_LABELS: Record<ListFieldName, string> = {
  title: '标题',
  url: '原文链接',
  publishDate: '发布日期',
  source: '来源',
  summary: '摘要',
}

export const DETAIL_FIELD_LABELS: Record<DetailFieldName, string> = {
  title: '完整标题',
  content: '正文',
  publishDate: '发布日期',
  issuingAgency: '发布机构',
  documentNumber: '文号',
}

export type ValueType = 'TEXT' | 'ATTR' | 'HTML' | 'CONST' | 'REGEX'

export interface FieldRuleDraft {
  fieldName: string
  selector: string
  valueType: ValueType
  attrName: string | null
  constValue: string | null
  regexPattern: string | null
  dateFormat: string | null
  required: boolean
  sortOrder: number
}

// ============================================================
// 模式键（PRD 11.3 节）
// ============================================================

export type ListModeKey = 'item' | 'title' | 'url' | 'publishDate' | 'source' | 'summary'
export type DetailModeKey = 'detail.title' | 'detail.content' | 'detail.publishDate' | 'detail.issuingAgency' | 'detail.documentNumber'
export type NavModeKey = 'pagination' | 'view'
export type ModeKey = ListModeKey | DetailModeKey | NavModeKey

export const LIST_MODE_KEYS: ListModeKey[] = ['item', 'title', 'url', 'publishDate', 'source', 'summary']
export const LIST_MODE_LABELS: Record<ListModeKey, string> = {
  item: '列表项',
  title: '标题',
  url: '原文链接',
  publishDate: '发布日期',
  source: '来源',
  summary: '摘要',
}

export const DETAIL_MODE_LABELS: Record<DetailModeKey, string> = {
  'detail.title': '完整标题',
  'detail.content': '正文',
  'detail.publishDate': '发布日期',
  'detail.issuingAgency': '发布机构',
  'detail.documentNumber': '文号',
}

// ============================================================
// SelectionState（PRD 10.3 节完整定义）
// ============================================================

export interface SelectionState {
  meta: {
    sourceName: string
    sourceCron: string | null
    enabled: boolean
  }

  list: {
    url: string
    snapshotId: string
    statusCode: number
    pageTitle: string
    headers: Record<string, string>
    timeoutMs: number
    itemSelector: string | null
    itemCount: number
    matchedIndexPaths: number[][]
  }

  listFields: Partial<Record<ListFieldName, FieldRuleDraft[]>>

  detail: {
    enabled: boolean
    sampleUrl: string | null
    snapshotId: string | null
    headers: Record<string, string>
    timeoutMs: number
    fields: Partial<Record<DetailFieldName, FieldRuleDraft[]>>
  }

  pagination: {
    mode: 'NONE' | 'URL_TEMPLATE'
    urlTemplate: string | null
    startPage: number
    maxPages: number
  }

  ui: {
    advancedMode: boolean
    currentMode: ModeKey
    currentStep: number
  }
}

export function createDefaultSelectionState(): SelectionState {
  return {
    meta: { sourceName: '', sourceCron: null, enabled: true },
    list: {
      url: '',
      snapshotId: '',
      statusCode: 0,
      pageTitle: '',
      headers: {},
      timeoutMs: 15000,
      itemSelector: null,
      itemCount: 0,
      matchedIndexPaths: [],
    },
    listFields: {},
    detail: {
      enabled: false,
      sampleUrl: null,
      snapshotId: null,
      headers: {},
      timeoutMs: 15000,
      fields: {},
    },
    pagination: { mode: 'NONE', urlTemplate: null, startPage: 1, maxPages: 1 },
    ui: { advancedMode: false, currentMode: 'item', currentStep: 1 },
  }
}

// ============================================================
// ClickedElementInfo（PRD 11.1 节）
// ============================================================

export interface ExtractableCandidate {
  type: 'TEXT' | 'ATTR' | 'HTML'
  attrName?: string
  value: string
  label?: string
  hidden: boolean
}

export interface TruncatedMeta {
  innerText: number
  innerHtml: number
  outerHtml: number
}

export interface ClickedElementInfo {
  tag: string
  id: string | null
  classNames: string[]
  attributes: Record<string, string>

  innerText: string
  innerHtml: string
  outerHtml: string

  indexPath: number[]
  cssPath: string
  bounding: { x: number; y: number; width: number; height: number }

  computedVisible: boolean

  extractableCandidates: ExtractableCandidate[]
  __truncated: TruncatedMeta
}

// ============================================================
// iframe 通信协议（PRD 10.4 节）
// ============================================================

export type ParentToFrameMessage =
  | { type: 'SET_MODE'; mode: ModeKey }
  | { type: 'HIGHLIGHT_MATCHES'; indexPaths: number[][] }
  | { type: 'CLEAR_HIGHLIGHT' }
  | { type: 'CLEAR_SELECTION' }

export type FrameToParentMessage =
  | { type: 'READY' }
  | { type: 'HOVER'; click: ClickedElementInfo }
  | { type: 'CLICK'; click: ClickedElementInfo }

// ============================================================
// 后端 API 类型（PRD 第 8 章）
// ============================================================

// 通用响应包装（8.1.3）
export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  errors: ApiError[]
}

export interface ApiError {
  field: string
  code: string
  message: string
}

// GET /capabilities（8.2）
export interface CapabilitiesData {
  listFields: FieldCap[]
  detailFields: FieldCap[]
  valueTypes: ValueType[]
  paginationModes: string[]
  limits: {
    maxPages: number
    snapshotTtlSeconds: number
    maxSnapshotsInMemory: number
  }
}

export interface FieldCap {
  name: string
  label: string
  required: boolean
  defaultValueType: ValueType
  defaultAttrName?: string
}

// POST /load-page（8.3）
export interface LoadPageRequest {
  url: string
  headers?: Record<string, string>
  timeoutMs?: number
}

export interface LoadPageData {
  snapshotId: string
  finalUrl: string
  statusCode: number
  title: string
  html: string
  fetchedAt: string
  warnings: string[]
  error: string | null
}

// POST /suggest-item-selector（8.4）
export interface SuggestItemRequest {
  snapshotId: string
  click: ClickedElementInfo
  regionHintIndexPath?: number[]
}

export interface SelectorCandidate {
  selector: string
  itemCount: number
  confidence: number
  regionContainerSelector: string
}

export interface SuggestItemData {
  primary: SelectorCandidate
  candidates: SelectorCandidate[]
  sampleItems: SampleItem[]
  warnings: string[]
}

export interface SampleItem {
  index: number
  textPreview: string
  indexPath: number[]
}

// POST /suggest-field-rule（8.5）
export interface SuggestFieldRequest {
  snapshotId: string
  pageRole: PageRole
  fieldName: string
  click: ClickedElementInfo
  itemSelector?: string
  currentRules?: FieldRuleDraft[]
}

export interface FieldRuleCandidate {
  selector: string
  valueType: ValueType
  attrName: string | null
  confidence: number
}

export interface FieldSample {
  itemIndex: number
  value: string | null
  raw: string
  warnings: string[]
}

export interface SuggestFieldData {
  primary: FieldRuleCandidate
  candidates: FieldRuleCandidate[]
  preview: {
    fieldStats: { hitRate: number; blankCount: number; successCount: number }
    samples: FieldSample[]
  }
  warnings: string[]
}

// POST /preview-list（8.6）
export interface PreviewListRequest {
  snapshotId: string
  itemSelector: string
  rules: FieldRuleDraft[]
  paginationRule?: PaginationDraft | null
}

export interface PaginationDraft {
  mode: 'NONE' | 'URL_TEMPLATE'
  urlTemplate?: string
  startPage: number
  maxPages: number
}

export interface SampleFieldValue {
  value: string | null
  raw: string
  warnings: string[]
}

export interface PreviewListData {
  itemCount: number
  samples: Array<{
    itemIndex: number
    fields: Record<string, SampleFieldValue | null>
  }>
  fieldStats: Record<string, { hitRate: number; blankCount: number }>
  paginationPreview: {
    urls: string[]
    perPageItemCount: number[]
  } | null
  warnings: string[]
}

// POST /preview-detail（8.7）
export interface PreviewDetailRequest {
  detailSnapshotId: string
  rules: FieldRuleDraft[]
}

export interface PreviewDetailData {
  fields: Record<string, SampleFieldValue>
  contentLength: number
  contentPreview: string
  fieldStats: Record<string, { hitRate: number }>
  warnings: string[]
}

// POST /sources（8.8，阶段 B）
export interface SaveSourceRequest {
  dataSource: {
    name: string
    cronExpr: string | null
    enabled: boolean
  }
  listPage: {
    url: string
    itemSelector: string
    headers: Record<string, string>
    timeoutMs: number
  }
  listRules: FieldRuleDraft[]
  detailPage: {
    headers: Record<string, string>
    timeoutMs: number
  } | null
  detailRules: FieldRuleDraft[] | null
  paginationRule: PaginationDraft
}
