<template>
  <div class="workbench">
    <UrlLoader
      :loading="loading"
      :status-code="state.list.statusCode"
      :page-title="state.list.pageTitle"
      :elapsed="elapsed"
      :error-msg="loadError"
      :initial-url="state.list.url"
      :initial-timeout="state.list.timeoutMs"
      :initial-headers="state.list.headers"
      @load="onLoadPage"
    />

    <div class="mode-bar">
      <div class="mode-buttons">
        <span class="mode-label">模式：</span>
        <el-button
          v-for="m in listModeOptions" :key="m.key" size="small"
          :type="state.ui.currentMode === m.key ? 'primary' : ''"
          @click="switchMode(m.key)"
        >{{ m.label }}</el-button>
        <span class="mode-divider">|</span>
        <el-button size="small"
          :type="state.ui.currentMode === 'pagination' ? 'primary' : ''"
          @click="switchMode('pagination')"
        >分页</el-button>
      </div>
      <div class="action-buttons">
        <el-button
          type="success" size="small"
          :disabled="!canSave"
          :loading="saving"
          @click="onSave"
        >保存配置</el-button>
        <el-button
          type="warning" size="small"
          :disabled="savedId === null"
          :loading="running"
          @click="onRun"
        >立即试跑</el-button>
      </div>
      <el-switch v-model="state.ui.advancedMode" size="small" active-text="高级" inactive-text="默认" />
    </div>

    <div class="workbench-body">
      <aside class="left-panel">
        <ModeSwitcher
          :current-mode="state.ui.currentMode"
          :list-fields="state.listFields"
          :item-selector="state.list.itemSelector"
          :detail-enabled="state.detail.enabled"
          :detail-fields="state.detail.fields"
          @switch-mode="switchMode"
          @toggle-detail="toggleDetail"
        />
      </aside>

      <main class="center-panel">
        <PageFrame ref="pageFrameRef"
          :html="pageHtml"
          :current-mode="state.ui.currentMode"
          :loading="loading"
          :matched-index-paths="state.list.matchedIndexPaths"
          @hover="onHover" @click="onIframeClick" @ready="onFrameReady" @error="onFrameError"
        />
      </main>

      <aside class="right-panel">
        <SelectionPanel
          :current-mode="state.ui.currentMode"
          :advanced-mode="state.ui.advancedMode"
          :item-selector="state.list.itemSelector"
          :item-count="state.list.itemCount"
          :list-fields="state.listFields"
          :detail-enabled="state.detail.enabled"
          :detail-fields="state.detail.fields"
          :clicked-info="lastClickedInfo"
          @reselect-item="reselectItem"
          @reselect-field="reselectField"
          @remove-field="removeField"
          @bind-candidate="onBindCandidate"
        />
        <ListPreviewPanel
          :loading="previewLoading"
          :item-count="previewData.itemCount"
          :samples="previewData.samples"
          :field-stats="previewData.fieldStats"
        />
        <PaginationConfig
          v-if="state.ui.currentMode === 'pagination'"
          :mode="state.pagination.mode" :url-template="state.pagination.urlTemplate"
          :start-page="state.pagination.startPage" :max-pages="state.pagination.maxPages"
          :previewing="paginationPreviewing" :preview-item-counts="paginationPreviewCounts"
          @change="onPaginationChange" @preview="onPaginationPreview"
        />
        <!-- 试跑结果 -->
        <div v-if="runResult" class="run-result-panel">
          <h3 class="section-title">试跑结果</h3>
          <div class="run-stats">
            <span>状态：<b :class="runResult.status === 'OK' ? 'status-ok' : 'status-err'">{{ runResult.status }}</b></span>
            <span>抓取 <b>{{ runResult.fetched }}</b> 条</span>
            <span>入库 <b>{{ runResult.unique }}</b> 条</span>
            <span>匹配 <b>{{ runResult.matched }}</b> 条</span>
            <span v-if="runResult.errors > 0" class="err-count">错误 <b>{{ runResult.errors }}</b></span>
            <span>耗时 <b>{{ runResult.durationMs }}ms</b></span>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * HtmlConfigWorkbench — 可视化爬虫配置工作台主页面。
 *
 * 职责：编排加载→点选→绑定→预览的完整流程。
 * 状态：SelectionState（PRD V2 10.3 节）驱动全部 UI。
 */

import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

// ---------- types ----------
import type { FieldRuleDraft, ClickedElementInfo, PaginationDraft, SaveSourceRequest } from '@/types/htmlConfig'
import {
  createDefaultSelectionState,
  LIST_MODE_LABELS, LIST_MODE_KEYS, DETAIL_MODE_LABELS,
  type ModeKey, type ListFieldName, type DetailFieldName,
  type DetailModeKey, type ListModeKey,
} from '@/types/htmlConfig'

// ---------- api ----------
import { loadPage, suggestItemSelector, suggestFieldRule, previewList, createSource, runSource } from '@/api/htmlConfig'

// ---------- components ----------
import UrlLoader from '@/components/htmlConfig/UrlLoader.vue'
import PageFrame from '@/components/htmlConfig/PageFrame.vue'
import ModeSwitcher from '@/components/htmlConfig/ModeSwitcher.vue'
import SelectionPanel from '@/components/htmlConfig/SelectionPanel.vue'
import ListPreviewPanel from '@/components/htmlConfig/ListPreviewPanel.vue'
import PaginationConfig from '@/components/htmlConfig/PaginationConfig.vue'

// ===================================================================
// 全局状态
// ===================================================================

const state = reactive(createDefaultSelectionState())
const loading = ref(false)
const elapsed = ref(0)
const loadError = ref('')
const pageHtml = ref('')
const pageFrameRef = ref<InstanceType<typeof PageFrame> | null>(null)

/** 最近一次点击的元素信息，用于右侧候选绑定面板 */
const lastClickedInfo = ref<ClickedElementInfo | null>(null)

/** 实时预览数据 */
const previewLoading = ref(false)
const previewData = reactive({
  itemCount: 0,
  samples: [] as Array<{
    itemIndex: number
    fields: Record<string, { value: string | null; raw: string; warnings: string[] } | null>
  }>,
  fieldStats: {} as Record<string, { hitRate: number; blankCount: number }>,
})

/** 分页预览状态 */
const paginationPreviewing = ref(false)
const paginationPreviewCounts = ref<number[]>([])

/** 保存 / 试跑状态 */
const saving = ref(false)
const running = ref(false)
const savedId = ref<number | null>(null)
const runResult = ref<{
  status: string
  fetched: number
  unique: number
  matched: number
  errors: number
  durationMs: number
} | null>(null)

/** 是否可以保存：至少已识别列表项 + 绑定 title + url */
const canSave = computed(() =>
  state.list.itemSelector != null
  && (state.listFields.title?.length ?? 0) > 0
  && (state.listFields.url?.length ?? 0) > 0
)

// ===================================================================
// 常量
// ===================================================================

/** 必填列表字段 */
const REQUIRED_LIST: ReadonlyArray<ListFieldName> = ['title', 'url']

/** 详情模式键值列表 */
const DETAIL_MODE_KEYS: ReadonlyArray<DetailModeKey> = [
  'detail.title', 'detail.content', 'detail.publishDate',
  'detail.issuingAgency', 'detail.documentNumber',
]

/** 顶部模式栏选项 */
const listModeOptions = LIST_MODE_KEYS.map(k => ({
  key: k as ModeKey,
  label: LIST_MODE_LABELS[k],
}))

// ===================================================================
// 工具函数
// ===================================================================

/** 判断 mode 是否为列表字段模式（非 item） */
function isListField(mode: string): mode is ListFieldName {
  return (LIST_MODE_KEYS as readonly string[]).includes(mode) && mode !== 'item'
}

/** 判断 mode 是否为详情字段模式 */
function isDetailField(mode: string): mode is DetailModeKey {
  return (DETAIL_MODE_KEYS as readonly string[]).includes(mode)
}

/** 将 state.listFields 展平为 FieldRuleDraft 数组，用于预览请求 */
function collectRules(): FieldRuleDraft[] {
  const out: FieldRuleDraft[] = []
  const names = Object.keys(state.listFields) as ListFieldName[]
  for (let i = 0; i < names.length; i++) {
    const frs = state.listFields[names[i]]
    if (frs) {
      for (let j = 0; j < frs.length; j++) out.push(frs[j])
    }
  }
  return out
}

/** 调用 preview-list 接口，更新右侧预览面板 */
async function runFullPreview(): Promise<void> {
  if (!state.list.snapshotId || !state.list.itemSelector) return
  const rules = collectRules()
  if (rules.length === 0) return
  try {
    const res = await previewList({
      snapshotId: state.list.snapshotId,
      itemSelector: state.list.itemSelector,
      rules,
    })
    previewData.itemCount = res.itemCount
    previewData.samples = res.samples.map(s => ({ itemIndex: s.itemIndex, fields: s.fields }))
    previewData.fieldStats = res.fieldStats
  } catch (e: unknown) {
    console.warn('[preview] 预览失败:', (e as Error).message)
  }
}

// ===================================================================
// 页面加载
// ===================================================================

/** 加载列表页 URL → 获取 HTML 快照 → 渲染 iframe */
async function onLoadPage(url: string, timeoutMs: number, headers: Record<string, string>): Promise<void> {
  loading.value = true
  loadError.value = ''
  const t0 = performance.now()

  try {
    const res = await loadPage({ url, headers, timeoutMs })
    elapsed.value = Math.round(performance.now() - t0)

    state.list.url = url
    state.list.snapshotId = res.snapshotId
    state.list.statusCode = res.statusCode
    state.list.pageTitle = res.title
    state.list.headers = headers
    state.list.timeoutMs = timeoutMs
    pageHtml.value = res.html

    // 重置
    state.list.itemSelector = null
    state.list.itemCount = 0
    state.list.matchedIndexPaths = []
    state.listFields = {}
    previewData.itemCount = 0
    previewData.samples = []
    previewData.fieldStats = {}
    paginationPreviewCounts.value = []
    state.ui.currentMode = 'item'

    if (res.error) {
      loadError.value = res.error
      ElMessage.error('加载失败: ' + res.error)
    } else if (res.warnings.length > 0) {
      ElMessage.warning(res.warnings[0])
    }
  } catch (e: unknown) {
    const msg = (e as Error).message || '加载失败'
    loadError.value = msg
    ElMessage.error('加载失败: ' + msg)
  } finally {
    loading.value = false
  }
}

// ===================================================================
// iframe 事件
// ===================================================================

function onFrameReady() { /* iframe 注入脚本就绪 */ }

function onFrameError(msg: string) { loadError.value = msg }

function onHover(_info: ClickedElementInfo) { /* 悬停暂不处理 */ }

/** 用户点击 iframe 内元素 → 根据当前模式分发到不同处理器 */
async function onIframeClick(info: ClickedElementInfo): Promise<void> {
  const mode = state.ui.currentMode
  if (mode === 'item') {
    await handleItemClick(info)
  } else if (isListField(mode)) {
    await handleFieldClick(mode, info)
  } else if (isDetailField(mode)) {
    await handleDetailFieldClick(mode, info)
  }
}

// ===================================================================
// 列表项识别
// ===================================================================

/** 调用 suggest-item-selector，识别列表项容器，展示可提取候选 */
async function handleItemClick(info: ClickedElementInfo): Promise<void> {
  if (!state.list.snapshotId) return

  try {
    const res = await suggestItemSelector({ snapshotId: state.list.snapshotId, click: info })
    state.list.itemSelector = res.primary.selector
    state.list.itemCount = res.primary.itemCount

    if (res.sampleItems) {
      state.list.matchedIndexPaths = res.sampleItems.map(s => [...s.indexPath])
    }

    // 保存点击元素信息，右侧面板据此展示可提取候选
    lastClickedInfo.value = info

    ElMessage.success(`识别到 ${res.primary.itemCount} 条列表项，请在右侧面板绑定字段`)

    if (state.list.matchedIndexPaths.length > 0) {
      pageFrameRef.value?.highlightMatches(state.list.matchedIndexPaths)
    }
  } catch (e: unknown) {
    const err = e as { code?: string; message?: string }
    if (err.code === 'SELECTOR_NOT_MATCH') {
      ElMessage.warning('没有识别出列表，请尝试点更外层的卡片')
    } else {
      ElMessage.error(err.message || '识别失败')
    }
  }
}

// ===================================================================
// 候选绑定（从右侧"可提取信息"面板直接绑定字段）
// ===================================================================

/**
 * 用户从候选中选择一个字段进行绑定。
 * 此时字段选择器为空（= 取列表项自身），valueType/attrName 来自候选。
 */
async function onBindCandidate(
  fieldName: string,
  info: ClickedElementInfo,
  candidateIndex: number,
): Promise<void> {
  const cand = info.extractableCandidates[candidateIndex]
  if (!cand) return

  const rule: FieldRuleDraft = {
    fieldName,
    selector: '',               // 空选择器 = 取 item 自身
    valueType: cand.type as FieldRuleDraft['valueType'],
    attrName: cand.attrName || null,
    constValue: null,
    regexPattern: null,
    dateFormat: null,
    required: REQUIRED_LIST.includes(fieldName as ListFieldName),
    sortOrder: (state.listFields[fieldName as ListFieldName] || []).length,
  }

  const existing = state.listFields[fieldName as ListFieldName] || []
  state.listFields = { ...state.listFields, [fieldName]: [...existing, rule] }

  ElMessage.success(`已绑定 ${LIST_MODE_LABELS[fieldName as ListModeKey]}`)
  await runFullPreview()
}

// ===================================================================
// 字段点击绑定（用户切换模式后点击 iframe 内子元素）
// ===================================================================

/**
 * 处理列表字段点击 —— 用户切换到"标题"/"链接"等模式后点击 iframe 内的子元素。
 * 调用 suggest-field-rule 获取后端推荐的字段选择器，然后跑全字段预览。
 */
async function handleFieldClick(fieldName: ListFieldName, info: ClickedElementInfo): Promise<void> {
  if (!state.list.snapshotId || !state.list.itemSelector) {
    ElMessage.warning('请先识别列表项')
    return
  }

  previewLoading.value = true
  try {
    const res = await suggestFieldRule({
      snapshotId: state.list.snapshotId,
      pageRole: 'LIST',
      fieldName,
      click: info,
      itemSelector: state.list.itemSelector,
      currentRules: state.listFields[fieldName],
    })

    const rule: FieldRuleDraft = {
      fieldName,
      selector: res.primary.selector,
      valueType: res.primary.valueType,
      attrName: res.primary.attrName ?? null,
      constValue: null,
      regexPattern: null,
      dateFormat: null,
      required: REQUIRED_LIST.includes(fieldName),
      sortOrder: (state.listFields[fieldName] || []).length,
    }

    const existing = state.listFields[fieldName] || []
    state.listFields = { ...state.listFields, [fieldName]: [...existing, rule] }

    await runFullPreview()
    ElMessage.success(`已绑定 ${LIST_MODE_LABELS[fieldName as ListModeKey]}`)

    // 自动推进到下一个常用字段
    if (fieldName === 'title') switchMode('url')
    else if (fieldName === 'url') switchMode('publishDate')
  } catch (e: unknown) {
    const err = e as { code?: string; message?: string }
    if (err.code === 'SELECTOR_NOT_MATCH') {
      ElMessage.warning('未取到任何值，请重选元素或换其他取值方式')
    } else {
      ElMessage.error(err.message || '绑定失败')
    }
  } finally {
    previewLoading.value = false
  }
}

// ===================================================================
// 详情字段绑定
// ===================================================================

async function handleDetailFieldClick(mode: DetailModeKey, info: ClickedElementInfo): Promise<void> {
  const fieldName = mode.replace('detail.', '') as DetailFieldName
  if (!state.detail.snapshotId) {
    ElMessage.warning('请先加载详情页')
    return
  }

  try {
    const res = await suggestFieldRule({
      snapshotId: state.detail.snapshotId,
      pageRole: 'DETAIL',
      fieldName,
      click: info,
    })

    const rule: FieldRuleDraft = {
      fieldName,
      selector: res.primary.selector,
      valueType: res.primary.valueType,
      attrName: res.primary.attrName ?? null,
      constValue: null,
      regexPattern: null,
      dateFormat: null,
      required: false,
      sortOrder: (state.detail.fields[fieldName] || []).length,
    }

    const existing = state.detail.fields[fieldName] || []
    state.detail.fields = { ...state.detail.fields, [fieldName]: [...existing, rule] }
    ElMessage.success(`已绑定 ${(DETAIL_MODE_LABELS as Record<string, string>)[mode]}`)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '绑定失败')
  }
}

// ===================================================================
// 模式切换
// ===================================================================

/** 切换当前配置模式，同步通知 iframe */
function switchMode(mode: ModeKey): void {
  state.ui.currentMode = mode
  pageFrameRef.value?.setMode(mode)
}

/** 启用/禁用详情页，首次启用时自动加载详情页 */
function toggleDetail(): void {
  state.detail.enabled = !state.detail.enabled
  if (state.detail.enabled && state.detail.snapshotId === null) {
    loadDetailPage()
  }
}

/** 从预览的第一条 URL 加载详情页 */
async function loadDetailPage(): Promise<void> {
  let sampleUrl: string | null = state.detail.sampleUrl
  if (!sampleUrl && previewData.samples.length > 0) {
    const urlField = previewData.samples[0].fields['url']
    if (urlField && urlField.value) sampleUrl = urlField.value
  }
  if (!sampleUrl) {
    ElMessage.warning('没有可用详情 URL，请先绑定链接字段')
    state.detail.enabled = false
    return
  }
  try {
    const res = await loadPage({
      url: sampleUrl,
      headers: state.list.headers,
      timeoutMs: state.detail.timeoutMs,
    })
    state.detail.sampleUrl = sampleUrl
    state.detail.snapshotId = res.snapshotId
    pageHtml.value = res.html
    ElMessage.success('已加载详情页')
  } catch (e: unknown) {
    ElMessage.error('加载详情页失败')
    state.detail.enabled = false
  }
}

// ===================================================================
// 字段操作（重选、删除）
// ===================================================================

function reselectItem(): void {
  state.list.itemSelector = null
  state.list.itemCount = 0
  state.list.matchedIndexPaths = []
  state.listFields = {}
  previewData.itemCount = 0
  previewData.samples = []
  previewData.fieldStats = {}
  switchMode('item')
  pageFrameRef.value?.clearSelection()
}

function reselectField(fieldName: string): void {
  const next: Record<string, FieldRuleDraft[]> = {}
  for (const key of Object.keys(state.listFields)) {
    if (key !== fieldName) {
      const fr = state.listFields[key as ListFieldName]
      if (fr) next[key] = fr
    }
  }
  state.listFields = next as Partial<Record<ListFieldName, FieldRuleDraft[]>>
  if (isListField(fieldName)) switchMode(fieldName as ModeKey)
  pageFrameRef.value?.clearSelection()
}

function removeField(fieldName: string): void {
  const next: Record<string, FieldRuleDraft[]> = {}
  for (const key of Object.keys(state.listFields)) {
    if (key !== fieldName) {
      const fr = state.listFields[key as ListFieldName]
      if (fr) next[key] = fr
    }
  }
  state.listFields = next as Partial<Record<ListFieldName, FieldRuleDraft[]>>
}

// ===================================================================
// 分页
// ===================================================================

function onPaginationChange(v: {
  mode: 'NONE' | 'URL_TEMPLATE'
  urlTemplate: string | null
  startPage: number
  maxPages: number
}): void {
  state.pagination.mode = v.mode
  state.pagination.urlTemplate = v.urlTemplate
  state.pagination.startPage = v.startPage
  state.pagination.maxPages = v.maxPages
}

async function onPaginationPreview(): Promise<void> {
  if (!state.list.snapshotId || !state.list.itemSelector) return
  paginationPreviewing.value = true
  try {
    const res = await previewList({
      snapshotId: state.list.snapshotId,
      itemSelector: state.list.itemSelector,
      rules: collectRules(),
      paginationRule: state.pagination.mode === 'URL_TEMPLATE'
        ? {
            mode: state.pagination.mode,
            urlTemplate: state.pagination.urlTemplate || undefined,
            startPage: state.pagination.startPage,
            maxPages: Math.min(state.pagination.maxPages, 3),
          }
        : null,
    })
    if (res.paginationPreview) {
      paginationPreviewCounts.value = res.paginationPreview.perPageItemCount
    }
    ElMessage.success('分页预览完成')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '分页预览失败')
  } finally {
    paginationPreviewing.value = false
  }
}

// ===================================================================
// 保存配置 + 立即试跑（阶段 B）
// ===================================================================

/** 从当前 SelectionState 组装 SaveSourceRequest */
function buildSaveRequest(): SaveSourceRequest {
  const listRules: FieldRuleDraft[] = []
  const names = Object.keys(state.listFields) as ListFieldName[]
  for (const fn of names) {
    const rules = state.listFields[fn]
    if (rules) listRules.push(...rules)
  }

  const detailRules: FieldRuleDraft[] = []
  if (state.detail.enabled) {
    const dnames = Object.keys(state.detail.fields) as DetailFieldName[]
    for (const fn of dnames) {
      const rules = state.detail.fields[fn]
      if (rules) detailRules.push(...rules)
    }
  }

  return {
    dataSource: {
      name: state.meta.sourceName || state.list.pageTitle || state.list.url,
      cronExpr: state.meta.sourceCron,
      enabled: true,
    },
    listPage: {
      url: state.list.url,
      itemSelector: state.list.itemSelector!,
      headers: state.list.headers,
      timeoutMs: state.list.timeoutMs,
    },
    listRules,
    detailPage: state.detail.enabled
      ? { headers: state.detail.headers, timeoutMs: state.detail.timeoutMs }
      : null,
    detailRules: detailRules.length > 0 ? detailRules : null,
    paginationRule: state.pagination.mode === 'URL_TEMPLATE'
      ? { mode: 'URL_TEMPLATE', urlTemplate: state.pagination.urlTemplate || undefined, startPage: state.pagination.startPage, maxPages: state.pagination.maxPages }
      : { mode: 'NONE', urlTemplate: undefined, startPage: 1, maxPages: 1 },
  }
}

async function onSave(): Promise<void> {
  if (!canSave.value) return
  saving.value = true
  try {
    const req = buildSaveRequest()
    const res = await createSource(req)
    savedId.value = res.id
    ElMessage.success('配置已保存，数据源 ID: ' + res.id)
  } catch (e: unknown) {
    const err = e as { code?: string; message?: string; errors?: Array<{ field: string; message: string }> }
    if (err.errors && err.errors.length > 0) {
      ElMessage.error(err.errors.map(er => er.message).join('；'))
    } else {
      ElMessage.error(err.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

async function onRun(): Promise<void> {
  if (savedId.value === null) return
  running.value = true
  runResult.value = null
  try {
    const data = await runSource(savedId.value)
    runResult.value = data
    ElMessage.success(`试跑完成：抓取 ${data.fetched} 条，入库 ${data.unique} 条，匹配 ${data.matched} 条`)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '试跑失败')
  } finally {
    running.value = false
  }
}
</script>

<style scoped>
.workbench { display: flex; flex-direction: column; height: 100vh; min-width: 1280px; }
.mode-bar { display: flex; align-items: center; justify-content: space-between; padding: 6px 16px; background: #fff; border-bottom: 1px solid #e4e7ed; }
.mode-buttons { display: flex; align-items: center; gap: 4px; }
.mode-label { font-size: 12px; color: #909399; margin-right: 2px; }
.mode-divider { color: #dcdfe6; margin: 0 6px; }
.workbench-body { display: flex; flex: 1; overflow: hidden; }
.left-panel { width: 200px; flex-shrink: 0; background: #fff; border-right: 1px solid #e4e7ed; overflow-y: auto; }
.center-panel { flex: 1; background: #fafafa; overflow: hidden; display: flex; flex-direction: column; }
.right-panel { width: 360px; flex-shrink: 0; background: #fff; border-left: 1px solid #e4e7ed; overflow-y: auto; display: flex; flex-direction: column; }
.action-buttons { display: flex; align-items: center; gap: 6px; margin-left: auto; margin-right: 12px; }
.run-result-panel { padding: 12px 16px; border-top: 1px solid #e4e7ed; background: #fafdf6; }
.run-stats { display: flex; gap: 16px; font-size: 13px; flex-wrap: wrap; }
.status-ok { color: #67c23a; }
.status-err { color: #f56c6c; }
.err-count { color: #f56c6c; }
.section-title { margin: 0 0 6px 0; font-size: 13px; font-weight: 600; color: #303133; }
</style>
