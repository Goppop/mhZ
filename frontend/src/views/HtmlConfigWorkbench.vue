<template>
  <div class="workbench">
    <!-- 顶部工具栏 -->
    <header class="workbench-header">
      <div class="header-left">
        <el-input
          v-model="pageState.sourceName"
          placeholder="数据源名称（可选）"
          clearable
          style="width: 200px"
        />
        <el-input
          v-model="pageState.listUrl"
          placeholder="输入列表页 URL"
          clearable
          style="width: 420px"
        />
        <el-button type="primary" @click="handleLoad" :loading="pageState.loading">
          加载页面
        </el-button>
        <el-button @click="handleReload" :disabled="!pageState.html">
          重新加载
        </el-button>
      </div>
      <div class="header-right">
        <el-tag v-if="pageState.loading" type="warning">加载中...</el-tag>
        <el-tag v-else-if="pageState.html" type="success">
          已加载 · {{ pageState.statusCode }} · {{ pageState.title || '无标题' }}
        </el-tag>
        <el-tag v-else type="info">未加载</el-tag>
      </div>
    </header>

    <!-- 主内容区：三栏布局 -->
    <div class="workbench-body">
      <!-- 左侧步骤区 -->
      <aside class="left-panel">
        <SelectionStepPanel
          :current-mode="selectionState.currentMode"
          :selections="selectionState.selections"
          @select-step="handleSelectStep"
        />
      </aside>

      <!-- 中间网页快照区 -->
      <main class="center-panel">
        <div v-if="!pageState.html && !pageState.loading" class="empty-state">
          <el-empty description="输入 URL 并点击「加载页面」开始" />
        </div>
        <div v-else-if="pageState.loading" class="loading-state">
          <el-skeleton :rows="12" animated />
        </div>
        <HtmlSnapshotFrame
          v-else
          ref="frameRef"
          :html="pageState.html"
          :current-mode="selectionState.currentMode"
          :selections="selectionState.selections"
          @hover="handleHover"
          @select="handleSelect"
          @frame-ready="handleFrameReady"
          @frame-error="handleFrameError"
        />
      </main>

      <!-- 右侧结果区 -->
      <aside class="right-panel">
        <SelectionModeToolbar
          :current-mode="selectionState.currentMode"
          :selections="selectionState.selections"
          @update:mode="handleSwitchMode"
        />
        <SelectionResultPanel
          :current-mode="selectionState.currentMode"
          :selections="selectionState.selections"
          :hovered-element="selectionState.hoveredElement"
          @clear="handleClearField"
          @reselect="handleReselectField"
          @clear-all="handleClearAll"
          @pick-candidate="handlePickCandidate"
        />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  SelectionMode,
  ElementSelection,
  ClickedElementInfo,
  PageState,
  SelectionState,
} from '@/types/htmlConfig'
import { STEP_ORDER, SELECTION_MODE_META } from '@/types/htmlConfig'
import { loadPage } from '@/api/htmlConfig'
import { sanitizeHtml } from '@/utils/sanitizeHtml'
import HtmlSnapshotFrame from '@/components/HtmlSnapshotFrame.vue'
import SelectionStepPanel from '@/components/SelectionStepPanel.vue'
import SelectionModeToolbar from '@/components/SelectionModeToolbar.vue'
import SelectionResultPanel from '@/components/SelectionResultPanel.vue'

const frameRef = ref<InstanceType<typeof HtmlSnapshotFrame> | null>(null)

const pageState = reactive<PageState>({
  sourceName: '',
  listUrl: '',
  html: '',
  title: '',
  finalUrl: '',
  statusCode: 0,
  loading: false,
  warnings: [],
  errors: [],
})

const selectionState = reactive<SelectionState>({
  currentMode: 'ITEM',
  selections: {},
  hoveredElement: null,
})

async function handleLoad() {
  if (!pageState.listUrl.trim()) {
    ElMessage.warning('请输入列表页 URL')
    return
  }
  pageState.loading = true
  pageState.errors = []
  pageState.warnings = []
  clearAllSelections()

  try {
    const result = await loadPage(pageState.listUrl.trim())
    console.log('[Workbench] loadPage 返回, html长度=', result.html?.length, 'title=', result.title, 'error=', result.error)
    const rawHtml = result.html
    pageState.html = sanitizeHtml(result.html)
    console.log('[Workbench] sanitizeHtml 后 html长度=', pageState.html.length, '是否变化=', rawHtml !== pageState.html)
    pageState.finalUrl = result.finalUrl
    pageState.statusCode = result.statusCode
    pageState.title = result.title
    pageState.warnings = result.warnings
    console.log('[Workbench] pageState 已更新, loading 即将设为 false')
    if (result.error) {
      pageState.errors.push(result.error)
    }
  } catch (e: any) {
    console.error('[Workbench] 加载异常:', e)
    pageState.errors.push(e.message || '加载失败')
    ElMessage.error('页面加载失败: ' + (e.message || '未知错误'))
  } finally {
    pageState.loading = false
    console.log('[Workbench] loading=false, pageState.html 是否为空=', !pageState.html)
  }
}

function handleReload() {
  handleLoad()
}

function handleFrameReady() {
  // iframe 注入脚本就绪后，立即同步当前模式
  console.log('[Workbench] frameReady, 立即发送当前模式: ' + selectionState.currentMode)
  sendModeToFrame(selectionState.currentMode)
}

function handleFrameError(msg: string) {
  pageState.errors.push(msg)
  ElMessage.error('iframe 错误: ' + msg)
}

function handleHover(info: ClickedElementInfo) {
  selectionState.hoveredElement = info
}

function handleSelect(sel: ElementSelection) {
  console.log('[Workbench] handleSelect, mode=' + sel.mode + ' tag=' + (sel.info?.tagName || '?') + ' candidates=' + (sel.info?.extractableCandidates?.length || 0))
  selectionState.selections = {
    ...selectionState.selections,
    [sel.mode]: { ...sel, selectedCandidateIndex: sel.selectedCandidateIndex ?? -1 },
  }
  // Auto-advance to next step
  const idx = STEP_ORDER.indexOf(sel.mode)
  if (idx >= 0 && idx < STEP_ORDER.length - 1) {
    selectionState.currentMode = STEP_ORDER[idx + 1]
    sendModeToFrame(STEP_ORDER[idx + 1])
  }
}

function handlePickCandidate(mode: SelectionMode, candidateIndex: number, sourceInfo?: ClickedElementInfo) {
  console.log('[Workbench] handlePickCandidate, mode=' + mode + ' candIdx=' + candidateIndex + ' selExists=' + !!selectionState.selections[mode] + ' hasSourceInfo=' + !!sourceInfo)
  let sel = selectionState.selections[mode]
  if (!sel) {
    if (sourceInfo) {
      // 自动创建该字段的选择，使用当前详情面板中展示的元素信息
      const newSel: ElementSelection = {
        mode,
        info: sourceInfo,
        selectedAt: Date.now(),
        selectedCandidateIndex: candidateIndex,
      }
      selectionState.selections = {
        ...selectionState.selections,
        [mode]: newSel,
      }
      console.log('[Workbench] 自动创建并绑定: mode=' + mode + ' candIdx=' + candidateIndex)
      ElMessage.success('已自动创建「' + SELECTION_MODE_META[mode].label + '」并绑定')
      return
    }
    console.warn('[Workbench] 绑定失败: 尚未选择「' + mode + '」对应的元素且无可用元素信息')
    ElMessage.warning('请先在页面中点击对应元素')
    return
  }
  selectionState.selections = {
    ...selectionState.selections,
    [mode]: { ...sel, selectedCandidateIndex: candidateIndex },
  }
  console.log('[Workbench] selectedCandidateIndex 已更新, mode=' + mode + ' newIdx=' + candidateIndex)
  ElMessage.success('已绑定：' + SELECTION_MODE_META[mode].label)
}

function handleSwitchMode(mode: SelectionMode) {
  selectionState.currentMode = mode
  sendModeToFrame(mode)
}

function handleSelectStep(mode: SelectionMode) {
  selectionState.currentMode = mode
  sendModeToFrame(mode)
}

function handleClearField(mode: SelectionMode) {
  const next = { ...selectionState.selections }
  delete next[mode]
  selectionState.selections = next
  clearFrameSelection()
}

function handleReselectField(mode: SelectionMode) {
  selectionState.currentMode = mode
  sendModeToFrame(mode)
  const next = { ...selectionState.selections }
  delete next[mode]
  selectionState.selections = next
  clearFrameSelection()
}

function handleClearAll() {
  clearAllSelections()
  clearFrameSelection()
  selectionState.currentMode = 'ITEM'
  sendModeToFrame('ITEM')
}

function sendModeToFrame(mode: SelectionMode) {
  frameRef.value?.setMode(mode)
}

function clearFrameSelection() {
  frameRef.value?.clearSelection()
}

function clearAllSelections() {
  selectionState.selections = {}
  selectionState.hoveredElement = null
}
</script>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-width: 1280px;
}

.workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  gap: 10px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workbench-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.left-panel {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.center-panel {
  flex: 1;
  background: #fafafa;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.empty-state,
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}

.right-panel {
  width: 340px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
</style>
