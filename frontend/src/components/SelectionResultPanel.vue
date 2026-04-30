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

    <!-- 已选字段列表 -->
    <div class="section">
      <div class="section-header">
        <h3 class="panel-title">已选字段 ({{ selectionEntries.length }}/{{ STEP_ORDER.length }})</h3>
        <el-button
          v-if="hasSelections"
          size="small"
          text
          type="danger"
          @click="$emit('clearAll')"
        >
          清空全部
        </el-button>
      </div>

      <div v-if="!hasSelections" class="empty-hint">
        尚未选择任何字段
      </div>

      <div
        v-for="entry in selectionEntries"
        :key="entry.mode"
        class="selection-item"
        :class="{ 'selection-current': entry.mode === currentMode }"
      >
        <div class="selection-header">
          <div class="selection-title-row">
            <el-tag :type="entry.mode === currentMode ? 'primary' : 'success'" size="small">
              {{ entry.label }}
            </el-tag>
            <span class="selection-cand-count" v-if="entry.info.extractableCandidates.length">
              {{ entry.info.extractableCandidates.length }} 个可提取项
            </span>
          </div>
          <div class="selection-actions">
            <el-button size="small" text @click="$emit('reselect', entry.mode)">
              重选
            </el-button>
            <el-button size="small" text type="danger" @click="$emit('clear', entry.mode)">
              清除
            </el-button>
          </div>
        </div>
        <div class="selection-preview">
          <el-tag size="small" type="info">{{ entry.info.tagName }}</el-tag>
          <span class="selection-text">{{ entry.info.text || '(无文本)' }}</span>
        </div>

        <!-- 详情展开 -->
        <el-collapse v-if="showDetails === entry.mode" class="details-collapse">
          <el-collapse-item title="元素详情" name="details">
            <ElementInfoCard
                :info="entry.info"
                :all-selections="selections"
                :selection-mode="entry.mode"
                @pick-candidate="(mode: SelectionMode, idx: number) => $emit('pick-candidate', mode, idx)"
                @unpick-candidate="(mode: SelectionMode) => $emit('pick-candidate', mode, -1)"
              />
          </el-collapse-item>
        </el-collapse>
        <div class="detail-toggles">
          <el-button
            v-if="showDetails !== entry.mode"
            size="small"
            text
            type="primary"
            @click="showDetails = entry.mode"
          >
            查看详情
          </el-button>
          <el-button
            v-if="showDetails === entry.mode"
            size="small"
            text
            @click="showDetails = null"
          >
            收起详情
          </el-button>
        </div>
      </div>
    </div>

    <!-- 阶段三交付数据预览 -->
    <div class="section output-section" v-if="hasSelections">
      <h3 class="panel-title">交付阶段三的数据预览</h3>
      <div class="output-item" v-for="item in phase3Output" :key="item.mode">
        <div class="output-header">
          <el-tag :type="item.selectedCandidate ? 'success' : 'info'" size="small">
            {{ item.label }}
          </el-tag>
          <el-tag size="small" type="info">{{ item.elementTag }}</el-tag>
          <code v-if="item.elementId !== '(无)'" class="output-id">#{{ item.elementId }}</code>
        </div>
        <div class="output-element">
          <span class="output-label">元素:</span>
          <code class="output-path">{{ item.elementCssPath.join(' > ') }}</code>
        </div>
        <div class="output-candidate" v-if="item.selectedCandidate">
          <span class="output-label">取值:</span>
          <el-tag :type="item.selectedCandidate.type === 'TEXT' ? 'primary' : item.selectedCandidate.type === 'ATTR' ? 'warning' : 'info'" size="small">
            {{ item.selectedCandidate.type }}
          </el-tag>
          <span class="cand-val">{{ item.selectedCandidate.value }}</span>
        </div>
        <div class="output-candidate" v-else>
          <span class="output-empty">⚠ 未选择可提取项</span>
        </div>
      </div>
      <el-collapse class="output-raw-collapse">
        <el-collapse-item title="JSON（原始数据）" name="raw">
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
  'pick-candidate': [mode: SelectionMode, candidateIndex: number]
}>()

const modeMetaMap = SELECTION_MODE_META

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

const showDetails = ref<SelectionMode | null>(null)

const modeMeta = computed(() => SELECTION_MODE_META[props.currentMode])

const selectionEntries = computed(() =>
  STEP_ORDER
    .filter((mode) => props.selections[mode])
    .map((mode) => ({
      mode,
      label: SELECTION_MODE_META[mode].label,
      info: props.selections[mode]!.info,
      selectedAt: props.selections[mode]!.selectedAt,
      selectedCandidateIndex: props.selections[mode]!.selectedCandidateIndex,
    }))
)

const hasSelections = computed(() => selectionEntries.value.length > 0)

function recTagType(type: ExtractableCandidateType): string {
  switch (type) {
    case 'TEXT': return 'primary'
    case 'ATTR': return 'warning'
    case 'HTML': return 'info'
  }
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

/* Selection items */
.selection-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.selection-cand-count {
  font-size: 11px;
  color: #909399;
}

.detail-toggles {
  margin-top: 4px;
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

.selection-item {
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
}

.selection-item.selection-current {
  border-color: #409eff;
  background: #ecf5ff;
}

.selection-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.selection-actions {
  display: flex;
  gap: 2px;
}

.selection-preview {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selection-text {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}

.details-collapse {
  margin-top: 6px;
}

.raw-section {
  font-size: 12px;
}

.raw-section h4 {
  margin: 8px 0 4px;
  font-size: 12px;
  color: #606266;
}

.raw-json {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 11px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}

/* Phase 3 output preview */
.output-section {
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
}

.output-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.output-item:last-child {
  border-bottom: none;
}

.output-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.output-id {
  font-size: 11px;
  color: #909399;
}

.output-element,
.output-candidate {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
  font-size: 12px;
}

.output-label {
  color: #909399;
  flex-shrink: 0;
  font-size: 11px;
}

.output-path {
  font-size: 11px;
  word-break: break-all;
}

.cand-val {
  font-size: 11px;
  color: #606266;
  word-break: break-all;
}

.output-empty {
  font-size: 11px;
  color: #e6a23c;
}

.output-raw-collapse {
  margin-top: 8px;
}
</style>
