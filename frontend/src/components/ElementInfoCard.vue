<template>
  <div class="info-card">
    <!-- 元素概要 -->
    <div class="info-section">
      <div class="info-row">
        <span class="info-label">标签</span>
        <el-tag size="small" :type="info.computedVisible ? '' : 'warning'">
          {{ info.tagName }}
        </el-tag>
        <el-tag v-if="info.id" size="small" type="info" class="id-tag">#{{ info.id }}</el-tag>
        <el-tag v-if="!info.computedVisible" size="small" type="danger">不可见</el-tag>
      </div>
    </div>

    <!-- 可见文本 -->
    <div class="info-section">
      <div class="section-title">可见文本</div>
      <div class="text-block">{{ info.text || '(空)' }}</div>
    </div>

    <!-- 属性列表 -->
    <div v-if="attrEntries.length > 0" class="info-section">
      <div class="section-title">属性</div>
      <div class="attrs">
        <div v-for="e in attrEntries" :key="e.key" class="attr-item">
          <code class="attr-key">{{ e.key }}</code>
          <span class="attr-eq">=</span>
          <code class="attr-val">{{ e.preview }}</code>
        </div>
      </div>
    </div>

    <!-- HTML 片段 -->
    <div class="info-section">
      <div class="section-title">内部 HTML</div>
      <pre class="html-block">{{ info.innerHtml || '(空)' }}</pre>
    </div>

    <div class="info-section">
      <div class="section-title">外部 HTML</div>
      <pre class="html-block">{{ info.outerHtml || '(空)' }}</pre>
    </div>

    <!-- 路径 -->
    <div class="info-section">
      <div class="section-title">路径</div>
      <div class="path-line">
        <span class="path-label">CSS:</span>
        <code>{{ info.cssPath.join(' > ') }}</code>
      </div>
      <div class="path-line">
        <span class="path-label">Index:</span>
        <code>{{ info.indexPath.join(' > ') }}</code>
      </div>
    </div>

    <!-- 尺寸 -->
    <div v-if="info.bounding" class="info-section">
      <div class="section-title">尺寸</div>
      <span class="info-text">
        {{ info.bounding.width }} × {{ info.bounding.height }}
        @ ({{ info.bounding.x }}, {{ info.bounding.y }})
      </span>
    </div>

    <!-- ====== 可提取项候选列表 ====== -->
    <div v-if="info.extractableCandidates.length > 0" class="info-section extractable-section">
      <div class="section-title highlight">这个元素可以提取什么？</div>
      <div
        v-for="(c, i) in info.extractableCandidates"
        :key="i"
        class="candidate-item"
        :class="{
          'candidate-recommended': c.recommended,
          'candidate-selected': boundModes[i] && boundModes[i]!.length > 0,
        }"
      >
        <div class="candidate-header">
          <el-tag
            :type="candidateTagType(c.type)"
            size="small"
            :effect="c.recommended ? 'dark' : 'plain'"
          >
            {{ c.type }}
          </el-tag>
          <span class="candidate-label">{{ c.label }}</span>
          <el-tag v-if="c.recommended" size="small" type="success" effect="plain">推荐</el-tag>
        </div>
        <div class="candidate-value">{{ c.previewValue }}</div>
        <div class="candidate-meta">{{ formatLength(c.length) }}</div>

        <!-- 绑定状态 + 模式选择 -->
        <div class="candidate-binding">
          <template v-if="boundModes[i] && boundModes[i]!.length > 0">
            <span class="bound-label">已绑定到：</span>
            <el-tag
              v-for="m in boundModes[i]"
              :key="m"
              size="small"
              type="success"
              class="bound-tag"
              closable
              @close="emit('unpick-candidate', m)"
            >
              {{ modeMetaMap[m].label }}
            </el-tag>
          </template>
          <div class="bind-action">
            <el-select
              :model-value="''"
              placeholder="绑定到字段..."
              size="small"
              class="bind-select"
              @change="(m: any) => emit('pick-candidate', m as SelectionMode, i)"
            >
              <el-option
                v-for="m in STEP_ORDER"
                :key="m"
                :label="modeMetaMap[m].label"
                :value="m"
              />
            </el-select>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ClickedElementInfo, ExtractableCandidateType, SelectionMode, ElementSelection } from '@/types/htmlConfig'
import { SELECTION_MODE_META, STEP_ORDER } from '@/types/htmlConfig'

const props = defineProps<{
  info: ClickedElementInfo
  /** 所有已选字段，用于查看跨模式绑定 */
  allSelections: Partial<Record<SelectionMode, ElementSelection>>
  /** 当前查看的字段模式 */
  selectionMode?: SelectionMode
}>()

const emit = defineEmits<{
  'pick-candidate': [mode: SelectionMode, candidateIndex: number]
  'unpick-candidate': [mode: SelectionMode]
}>()

const modeMetaMap = SELECTION_MODE_META

/**
 * Map candidate index → list of modes that have selected this candidate.
 * A mode "selects" a candidate if its selectedCandidateIndex matches AND
 * it was selected from the same element (same element means same selection info reference or matching key).
 * For simplicity: check if the selection's info matches and candidate index matches.
 */
const boundModes = computed(() => {
  const map: Record<number, SelectionMode[]> = {}
  for (let i = 0; i < props.info.extractableCandidates.length; i++) {
    map[i] = []
  }

  for (const mode of STEP_ORDER) {
    const sel = props.allSelections[mode]
    if (!sel) continue
    if (sel.selectedCandidateIndex < 0) continue
    // Check if this selection references the same element (by comparing tagName + indexPath as a simple identity)
    const sameEl = sel.info.tagName === props.info.tagName &&
      JSON.stringify(sel.info.indexPath) === JSON.stringify(props.info.indexPath)
    if (!sameEl) continue

    const idx = sel.selectedCandidateIndex
    if (idx >= 0 && idx < props.info.extractableCandidates.length) {
      if (!map[idx]) map[idx] = []
      map[idx]!.push(mode)
    }
  }
  return map
})

const attrEntries = computed(() => {
  const attrs = props.info.attributes
  const priority = ['href', 'title', 'src', 'alt', 'content', 'value', 'name', 'role', 'aria-label']
  const entries = Object.entries(attrs).map(([key, val]) => {
    const preview = val.length > 80 ? val.slice(0, 80) + '…' : val
    return { key, val: val as string, preview }
  })
  entries.sort((a, b) => {
    const aIdx = priority.indexOf(a.key)
    const bIdx = priority.indexOf(b.key)
    if (aIdx >= 0 && bIdx >= 0) return aIdx - bIdx
    if (aIdx >= 0) return -1
    if (bIdx >= 0) return 1
    if (a.key.startsWith('data-') && !b.key.startsWith('data-')) return -1
    if (!a.key.startsWith('data-') && b.key.startsWith('data-')) return 1
    return a.key.localeCompare(b.key)
  })
  return entries
})

function candidateTagType(type: ExtractableCandidateType): string {
  switch (type) {
    case 'TEXT': return 'primary'
    case 'ATTR': return 'warning'
    case 'HTML': return 'info'
  }
}

function formatLength(len: number): string {
  if (len >= 1000) return (len / 1000).toFixed(1) + 'k 字符'
  return len + ' 字符'
}
</script>

<style scoped>
.info-card {
  font-size: 13px;
}

.info-section {
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.info-section:last-child {
  border-bottom: none;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 6px;
}

.section-title.highlight {
  color: #409eff;
  font-size: 13px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.info-label {
  color: #909399;
  font-size: 12px;
}

.id-tag {
  margin-left: 2px;
}

.text-block {
  font-size: 12px;
  color: #303133;
  word-break: break-all;
  line-height: 1.5;
  background: #fafafa;
  padding: 6px 8px;
  border-radius: 3px;
  max-height: 80px;
  overflow-y: auto;
}

.html-block {
  font-size: 11px;
  color: #606266;
  word-break: break-all;
  white-space: pre-wrap;
  line-height: 1.4;
  background: #fafafa;
  padding: 6px 8px;
  border-radius: 3px;
  max-height: 100px;
  overflow-y: auto;
  margin: 0;
}

.attrs {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.attr-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.attr-key {
  color: #e6a23c;
  font-size: 11px;
}

.attr-eq {
  color: #c0c4cc;
}

.attr-val {
  color: #409eff;
  word-break: break-all;
  font-size: 11px;
}

.path-line {
  display: flex;
  gap: 4px;
  margin-bottom: 2px;
}

.path-label {
  font-size: 11px;
  color: #c0c4cc;
  flex-shrink: 0;
}

.path-line code {
  font-size: 11px;
  word-break: break-all;
}

code {
  font-size: 12px;
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  color: #303133;
}

.info-text {
  font-size: 12px;
  color: #606266;
}

/* Extractable Candidates */
.extractable-section {
  background: #f0f7ff;
  margin: 0 -4px 0 -4px;
  padding: 10px;
  border-radius: 6px;
  border-bottom: none;
}

.candidate-item {
  padding: 8px;
  margin-bottom: 6px;
  background: #fff;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.candidate-item:last-child {
  margin-bottom: 0;
}

.candidate-recommended {
  border-color: #b3d8ff;
  background: #f5faff;
}

.candidate-selected {
  border-color: #67c23a;
  background: #f0f9eb;
}

.candidate-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.candidate-label {
  font-size: 12px;
  color: #303133;
  font-weight: 500;
}

.candidate-value {
  font-size: 12px;
  color: #606266;
  word-break: break-all;
  line-height: 1.4;
  background: #fafafa;
  padding: 4px 6px;
  border-radius: 3px;
  max-height: 48px;
  overflow-y: auto;
}

.candidate-meta {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}

/* Binding */
.candidate-binding {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.bound-label {
  font-size: 11px;
  color: #909399;
}

.bound-tag {
  margin-right: 2px;
}

.bind-action {
  flex-shrink: 0;
}

.bind-select {
  width: 130px;
}
</style>
