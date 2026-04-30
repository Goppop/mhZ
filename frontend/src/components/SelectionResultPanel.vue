<template>
  <div class="result-panel">
    <!-- 当前模式提示 -->
    <div class="section mode-hint-section">
      <h3 class="panel-title mode-title">{{ modeMeta.panelTitle }}</h3>
      <p class="mode-hint-text">{{ modeMeta.hint }}</p>
      <div v-if="modeMeta.recommendations.length > 0" class="mode-recommendations">
        <div class="rec-title">推荐取值方式：</div>
        <div v-for="rec in modeMeta.recommendations" :key="rec.desc" class="rec-item">
          <el-tag :type="recTagType(rec.type)" size="small">{{ rec.type }}</el-tag>
          <span v-if="rec.attrName" class="rec-attr">{{ rec.attrName }}</span>
          <span class="rec-desc">{{ rec.desc }}</span>
        </div>
      </div>
    </div>

    <!-- Hover 预览 -->
    <div class="section">
      <h3 class="panel-title">悬停元素</h3>
      <div v-if="hoveredElement" class="hover-info">
        <el-tag size="small" type="info">{{ hoveredElement.tagName }}</el-tag>
        <span class="hover-text">{{ hoveredElement.text || '(无文本)' }}</span>
        <el-tag v-if="!hoveredElement.computedVisible" size="small" type="danger">隐藏</el-tag>
      </div>
      <span v-else class="empty-hint">鼠标悬停查看元素</span>
    </div>

    <!-- 列表项容器（ITEM 选择） -->
    <div class="section" v-if="itemEntry">
      <h3 class="panel-title">列表项容器</h3>
      <div class="item-container-box">
        <div class="item-selector-row">
          <el-tag size="small" type="primary">{{ itemEntry.info.tagName }}</el-tag>
          <code class="rule-selector item-sel">{{ itemEntry.info.cssPath.join(' > ') }}</code>
        </div>
        <div class="item-preview-text">{{ itemEntry.info.text || '(无文本)' }}</div>
        <div class="item-actions">
          <el-button size="small" text type="primary" @click="toggleDetail('ITEM')">
            {{ showDetails === 'ITEM' ? '收起详情' : '元素详情' }}
          </el-button>
          <el-button size="small" text @click="$emit('reselect', 'ITEM')">重选</el-button>
          <el-button size="small" text type="danger" @click="$emit('clear', 'ITEM')">删除</el-button>
        </div>
        <div v-if="showDetails === 'ITEM'" class="rule-detail">
          <ElementInfoCard
            :info="itemEntry.info"
            :all-selections="selections"
            :selection-mode="'ITEM'"
            @pick-candidate="(mode: SelectionMode, idx: number) => $emit('pick-candidate', mode, idx, itemEntry!.info)"
            @unpick-candidate="(mode: SelectionMode) => $emit('pick-candidate', mode, -1, itemEntry!.info)"
          />
        </div>
      </div>
    </div>

    <!-- 提取规则（字段级规则） -->
    <div class="section">
      <div class="section-header">
        <h3 class="panel-title">提取规则 ({{ fieldEntries.length }})</h3>
        <el-button
          v-if="hasRules"
          size="small"
          text
          type="danger"
          @click="$emit('clearAll')"
        >
          清空全部
        </el-button>
      </div>

      <div v-if="!hasRules" class="empty-hint">
        在页面中点击元素，然后从可提取项中绑定到字段
      </div>

      <div
        v-for="entry in fieldEntries"
        :key="entry.mode"
        class="rule-item"
        :class="{ 'rule-current': entry.mode === currentMode }"
      >
        <!-- 规则摘要行 -->
        <div class="rule-summary" @click="toggleDetail(entry.mode)">
          <div class="rule-field">
            <el-tag :type="entry.mode === currentMode ? 'primary' : 'success'" size="small">
              {{ entry.label }}
            </el-tag>
            <span v-if="entry.boundCandidate" class="rule-value">
              {{ entry.boundCandidate.previewValue }}
            </span>
            <span v-else class="rule-value-empty">等待绑定取值方式...</span>
          </div>
          <div class="rule-meta">
            <el-tag v-if="entry.boundCandidate" size="small" :type="candidateShortType(entry.boundCandidate.type)" class="rule-vtype">
              {{ entry.boundCandidate.type }}{{ entry.boundCandidate.attrName ? ' ' + entry.boundCandidate.attrName : '' }}
            </el-tag>
            <code class="rule-selector">{{ shortenPath(entry.info.cssPath) }}</code>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="rule-actions">
          <!-- 已绑定：仅展示，不需要再展开 -->
          <template v-if="entry.boundCandidate">
            <span class="rule-done">已配置</span>
          </template>
          <!-- 未绑定：可以展开选候选 -->
          <template v-else>
            <el-button size="small" text type="primary" @click.stop="toggleDetail(entry.mode)">
              {{ showDetails === entry.mode ? '收起' : '绑定取值' }}
            </el-button>
          </template>
          <el-button size="small" text @click.stop="$emit('reselect', entry.mode)">
            重选
          </el-button>
          <el-button size="small" text type="danger" @click.stop="$emit('clear', entry.mode)">
            删除
          </el-button>
        </div>

        <!-- 详情展开：仅未绑定规则显示，用于选择候选 -->
        <div v-if="!entry.boundCandidate && showDetails === entry.mode" class="rule-detail">
          <ElementInfoCard
            :info="entry.info"
            :all-selections="selections"
            :selection-mode="entry.mode"
            @pick-candidate="(mode: SelectionMode, idx: number) => $emit('pick-candidate', mode, idx, entry.info)"
            @unpick-candidate="(mode: SelectionMode) => $emit('pick-candidate', mode, -1, entry.info)"
          />
        </div>
      </div>
    </div>

    <!-- JSON 原始数据（折叠） -->
    <div class="section" v-if="hasRules">
      <el-collapse v-model="activeCollapseNames">
        <el-collapse-item title="JSON（阶段三交付）" name="raw">
          <pre class="raw-json">{{ JSON.stringify(phase3Output, null, 2) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SelectionMode, ElementSelection, ClickedElementInfo, ExtractableCandidateType } from '@/types/htmlConfig'
import { SELECTION_MODE_META, STEP_ORDER } from '@/types/htmlConfig'
import ElementInfoCard from './ElementInfoCard.vue'

const props = defineProps<{
  currentMode: SelectionMode
  selections: Partial<Record<SelectionMode, ElementSelection>>
  hoveredElement: ClickedElementInfo | null
}>()

defineEmits<{
  clear: [mode: SelectionMode]
  reselect: [mode: SelectionMode]
  clearAll: []
  'pick-candidate': [mode: SelectionMode, candidateIndex: number, info: ClickedElementInfo]
}>()

const modeMetaMap = SELECTION_MODE_META

const showDetails = ref<SelectionMode | null>(null)
const activeCollapseNames = ref<string[]>([])

function toggleDetail(mode: SelectionMode) {
  showDetails.value = showDetails.value === mode ? null : mode
}

function shortenPath(cssPath: string[]): string {
  if (!cssPath || cssPath.length === 0) return ''
  // 只取最后 4 段，前面用 … 代替
  if (cssPath.length <= 4) return cssPath.join(' > ')
  return '… > ' + cssPath.slice(-4).join(' > ')
}

const modeMeta = computed(() => SELECTION_MODE_META[props.currentMode])

// ITEM 单独作为容器，不作为提取规则
const itemEntry = computed(() => {
  const sel = props.selections['ITEM']
  if (!sel) return null
  return {
    mode: 'ITEM' as SelectionMode,
    label: '列表项',
    info: sel.info,
    selectedAt: sel.selectedAt,
    selectedCandidateIndex: sel.selectedCandidateIndex,
    boundCandidate: null as any,
  }
})

// 除 ITEM 外的字段级规则
const fieldEntries = computed(() =>
  STEP_ORDER
    .filter((mode) => mode !== 'ITEM' && props.selections[mode])
    .map((mode) => {
      const sel = props.selections[mode]!
      const candidate = sel.selectedCandidateIndex >= 0
        ? sel.info.extractableCandidates[sel.selectedCandidateIndex]
        : null
      return {
        mode,
        label: SELECTION_MODE_META[mode].label,
        info: sel.info,
        selectedAt: sel.selectedAt,
        selectedCandidateIndex: sel.selectedCandidateIndex,
        boundCandidate: candidate,
      }
    })
)

const hasRules = computed(() => fieldEntries.value.length > 0 || !!itemEntry.value)

const phase3Output = computed(() => {
  const items: any[] = []
  for (const mode of STEP_ORDER) {
    const sel = props.selections[mode]
    if (!sel) continue
    const candidate = sel.selectedCandidateIndex >= 0
      ? sel.info.extractableCandidates[sel.selectedCandidateIndex]
      : null
    items.push({
      mode,
      label: modeMetaMap[mode].label,
      elementTag: sel.info.tagName,
      elementId: sel.info.id || '(无)',
      elementText: sel.info.text,
      elementCssPath: sel.info.cssPath,
      selectedCandidate: candidate ? {
        type: candidate.type,
        label: candidate.label,
        value: candidate.previewValue,
        attrName: candidate.attrName,
      } : null,
    })
  }
  return items
})

function candidateShortType(type: ExtractableCandidateType): string {
  switch (type) {
    case 'TEXT': return 'primary'
    case 'ATTR': return 'warning'
    case 'HTML': return 'info'
  }
}
function recTagType(type: ExtractableCandidateType): string {
  return candidateShortType(type)
}
</script>

<style scoped>
.result-panel {
  padding: 12px 16px;
  flex: 1;
  overflow-y: auto;
}

.section {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* Mode hint */
.mode-hint-section {
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 6px;
  padding: 10px 12px;
}

.mode-title {
  color: #409eff;
  margin-bottom: 4px;
}

.mode-hint-text {
  margin: 0 0 8px 0;
  font-size: 12px;
  color: #606266;
}

.mode-recommendations {
  margin-top: 6px;
}

.rec-title {
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
}

.rec-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
  font-size: 12px;
}

.rec-attr {
  font-family: monospace;
  font-size: 11px;
  color: #e6a23c;
}

.rec-desc {
  color: #606266;
}

.hover-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hover-text {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.empty-hint {
  font-size: 12px;
  color: #c0c4cc;
}

/* Extraction Rules */
.rule-item {
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  transition: border-color 0.2s;
}

.rule-item.rule-current {
  border-color: #409eff;
  background: #ecf5ff;
}

.rule-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
}

.rule-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule-value {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
  line-height: 1.3;
}

.rule-value-empty {
  font-size: 12px;
  color: #e6a23c;
  font-style: italic;
}

.rule-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rule-vtype {
  flex-shrink: 0;
}

.rule-selector {
  font-size: 10px;
  color: #909399;
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.rule-actions {
  display: flex;
  gap: 2px;
  margin-top: 6px;
  padding-top: 4px;
  border-top: 1px solid #f0f0f0;
}

.rule-done {
  font-size: 11px;
  color: #67c23a;
  margin-right: 6px;
}

.rule-detail {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e4e7ed;
}

/* ITEM container box */
.item-container-box {
  border: 1px solid #b3d8ff;
  border-radius: 6px;
  padding: 10px;
  background: #f5faff;
}

.item-selector-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.item-sel {
  font-size: 11px;
  color: #409eff;
  max-width: 240px;
}

.item-preview-text {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-actions {
  display: flex;
  gap: 2px;
  padding-top: 4px;
  border-top: 1px solid #d9ecff;
}

/* JSON output */
.raw-json {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 11px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
</style>
