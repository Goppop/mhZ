<template>
  <div class="selection-panel">
    <!-- 当前模式指示 -->
    <div class="panel-section mode-info">
      <h3 class="section-title">当前模式</h3>
      <el-tag type="primary" size="small">{{ modeLabel }}</el-tag>
      <p class="mode-desc">{{ modeDesc }}</p>
    </div>

    <!-- 列表项选择器 -->
    <div class="panel-section">
      <h3 class="section-title">列表项</h3>
      <div v-if="itemSelector" class="item-sel-row">
        <code class="sel-code">{{ itemSelector }}</code>
        <el-tag size="small" type="success">{{ itemCount }} 条</el-tag>
        <el-button size="small" text type="primary" @click="$emit('reselect-item')">重选</el-button>
      </div>
      <span v-else class="empty-hint">切到列表项模式，点击列表卡片</span>
    </div>

    <!-- 可提取候选（选完 ITEM 后展示） -->
    <div
      v-if="clickedInfo && clickedInfo.extractableCandidates.length > 0"
      class="panel-section candidate-section"
    >
      <h3 class="section-title">可提取信息</h3>
      <div
        v-for="(c, i) in clickedInfo.extractableCandidates" :key="i"
        class="candidate-row"
      >
        <div class="candidate-info">
          <el-tag :type="candidateTypeTag(c.type)" size="small">
            {{ c.label || (c.type + (c.attrName ? ' ' + c.attrName : '')) }}
          </el-tag>
          <span class="candidate-val">{{ c.value }}</span>
        </div>

        <!-- 已绑定：显示字段标签，可点 X 删除 -->
        <template v-if="boundFieldName(i)">
          <el-tag size="small" type="success" closable @close="$emit('remove-field', boundFieldName(i)!)">
            {{ boundFieldName(i) }}
          </el-tag>
        </template>

        <!-- 未绑定：显示字段下拉框 -->
        <el-select
          v-else
          placeholder="绑定到..."
          size="small"
          style="width:110px"
          @change="(field: string) => $emit('bind-candidate', field, clickedInfo!, i)"
        >
          <el-option
            v-for="f in availableFieldOptions" :key="f.value"
            :label="f.label" :value="f.value"
          />
        </el-select>
      </div>
    </div>

    <!-- 已绑定字段 -->
    <div class="panel-section">
      <h3 class="section-title">列表字段 ({{ listFieldCount }})</h3>
      <div v-if="listFieldCount === 0" class="empty-hint">
        绑定候选或切模式点击元素
      </div>
      <div v-for="(rules, fieldName) in listFields" :key="fieldName" class="field-entry">
        <div class="field-header">
          <el-tag size="small" type="success">{{ fieldLabel(fieldName) }}</el-tag>
          <span class="rule-count">{{ (rules || []).length }} 条规则</span>
          <el-button size="small" text type="primary" @click="$emit('reselect-field', fieldName)">重选</el-button>
          <el-button size="small" text type="danger" @click="$emit('remove-field', fieldName)">删除</el-button>
        </div>
        <div v-if="advancedMode" class="field-rules-detail">
          <div v-for="(r, ri) in rules" :key="ri" class="rule-line">
            <code>{{ r.selector || '(item自身)' }}</code>
            <el-tag size="small" type="warning">{{ r.valueType }}</el-tag>
            <span v-if="r.attrName">{{ r.attrName }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情字段（可选） -->
    <div v-if="detailEnabled" class="panel-section">
      <h3 class="section-title">详情字段</h3>
      <div v-for="(rules, fieldName) in detailFields" :key="fieldName" class="field-entry">
        <div class="field-header">
          <el-tag size="small" type="success">{{ detailFieldLabel(fieldName) }}</el-tag>
          <span class="rule-count">{{ (rules || []).length }} 条规则</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * SelectionPanel — 右侧配置进度面板。
 *
 * 展示三块内容：
 * 1. 列表项选择器状态 + 可提取候选（选完 ITEM 后出现）
 * 2. 已绑定的列表字段规则
 * 3. 已绑定的详情字段规则（启用详情页时）
 */

import { computed } from 'vue'

import type {
  ModeKey, ListFieldName, DetailFieldName, FieldRuleDraft,
  ClickedElementInfo, ExtractableCandidate, ListModeKey,
} from '@/types/htmlConfig'
import {
  LIST_MODE_KEYS, LIST_MODE_LABELS, DETAIL_MODE_LABELS,
  LIST_FIELD_LABELS, DETAIL_FIELD_LABELS,
} from '@/types/htmlConfig'

// ===================================================================
// Props & Emits
// ===================================================================

const props = defineProps<{
  currentMode: ModeKey
  advancedMode: boolean
  itemSelector: string | null
  itemCount: number
  listFields: Partial<Record<ListFieldName, FieldRuleDraft[]>>
  detailEnabled: boolean
  detailFields: Partial<Record<DetailFieldName, FieldRuleDraft[]>>
  /** 最近一次点击的元素信息（包含 extractableCandidates） */
  clickedInfo: ClickedElementInfo | null
}>()

defineEmits<{
  'reselect-item': []
  'reselect-field': [fieldName: string]
  'remove-field': [fieldName: string]
  'bind-candidate': [fieldName: string, info: ClickedElementInfo, candidateIndex: number]
}>()

// ===================================================================
// 候选 → 字段 绑定映射
// ===================================================================

/**
 * 将已绑定的 listFields 反向映射到候选索引。
 * 匹配规则：同一 valueType 且 attrName 相同（或候选无 attrName）。
 */
const candidateBindings = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {}
  const clickedInfo = props.clickedInfo
  if (!clickedInfo) return map

  const candidates = clickedInfo.extractableCandidates
  const fieldNames = Object.keys(props.listFields) as ListFieldName[]

  for (const fieldName of fieldNames) {
    const rules = props.listFields[fieldName]
    if (!rules) continue

    for (const rule of rules) {
      for (let ci = 0; ci < candidates.length; ci++) {
        const cand = candidates[ci]
        if (rule.valueType === cand.type && (!cand.attrName || rule.attrName === cand.attrName)) {
          map[ci] = LIST_MODE_LABELS[fieldName as ListModeKey] || fieldName
        }
      }
    }
  }
  return map
})

/** 候选索引 → 已绑定字段标签（null = 未绑定） */
function boundFieldName(i: number): string | null {
  return candidateBindings.value[i] || null
}

/** 可选字段（排除已绑定和 ITEM 模式） */
const availableFieldOptions = computed(() => {
  const bound = new Set(Object.values(candidateBindings.value))
  return LIST_MODE_KEYS
    .filter(k => k !== 'item' && !bound.has(LIST_MODE_LABELS[k]))
    .map(k => ({ value: k, label: LIST_MODE_LABELS[k] }))
})

/** 候选类型 → Element Plus tag type */
function candidateTypeTag(type: ExtractableCandidate['type']): string {
  return type === 'TEXT' ? 'primary' : type === 'ATTR' ? 'warning' : 'info'
}

// ===================================================================
// 模式 & 字段标签
// ===================================================================

const modeLabel = computed(() => {
  const m = props.currentMode
  return (LIST_MODE_LABELS as Record<string, string>)[m]
    || (DETAIL_MODE_LABELS as Record<string, string>)[m]
    || (m === 'pagination' ? '分页配置' : m)
})

const modeDesc = computed(() => {
  switch (props.currentMode) {
    case 'item': return '请点击一条完整的列表卡片'
    case 'pagination': return '配置翻页方式'
    case 'view': return '查看模式，不响应点击'
    default: return '请点击列表项内对应的元素'
  }
})

const listFieldCount = computed(() => Object.keys(props.listFields).length)

function fieldLabel(name: string): string {
  return LIST_FIELD_LABELS[name as ListFieldName] || name
}

function detailFieldLabel(name: string): string {
  return DETAIL_FIELD_LABELS[name as DetailFieldName] || name
}
</script>

<style scoped>
.selection-panel { padding: 12px 16px; flex: 1; overflow-y: auto; }
.panel-section { margin-bottom: 14px; }
.section-title { margin: 0 0 6px 0; font-size: 13px; font-weight: 600; color: #303133; }
.mode-info { background: #ecf5ff; border-radius: 6px; padding: 8px 10px; }
.mode-desc { margin: 4px 0 0 0; font-size: 12px; color: #606266; }
.item-sel-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.sel-code { font-size: 11px; color: #409eff; background: #f5f7fa; padding: 2px 6px; border-radius: 3px; word-break: break-all; max-width: 200px; }
.empty-hint { font-size: 12px; color: #c0c4cc; }
.field-entry { padding: 8px; margin-bottom: 4px; background: #fafafa; border-radius: 4px; border: 1px solid #ebeef5; }
.field-header { display: flex; align-items: center; gap: 6px; }
.rule-count { font-size: 11px; color: #909399; }
.field-rules-detail { margin-top: 6px; padding-top: 4px; border-top: 1px solid #f0f0f0; }
.rule-line { display: flex; align-items: center; gap: 4px; font-size: 12px; margin-top: 3px; }
.rule-line code { font-size: 11px; color: #606266; }
.candidate-section { background: #f0f7ff; border-radius: 6px; padding: 8px 10px; }
.candidate-row { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #e4edf5; }
.candidate-row:last-child { border-bottom: none; }
.candidate-info { display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0; }
.candidate-val { font-size: 12px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
