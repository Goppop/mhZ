<template>
  <div class="mode-switcher">
    <h3 class="switcher-title">配置步骤</h3>

    <!-- 列表页步骤 -->
    <div class="step-group">
      <div class="group-label">列表页</div>
      <div
        v-for="s in listStepEntries" :key="s.key" class="step-item"
        :class="{ active: s.key === currentMode }"
        @click="$emit('switch-mode', s.key)"
      >
        <span class="step-dot" :class="{ done: s.done, active: s.key === currentMode }" />
        <span class="step-label">{{ s.label }}</span>
        <span v-if="s.done" class="step-check">&#10003;</span>
      </div>
    </div>

    <!-- 分页 -->
    <div class="step-group">
      <div class="group-label">分页</div>
      <div
        class="step-item" :class="{ active: currentMode === 'pagination' }"
        @click="$emit('switch-mode', 'pagination')"
      >
        <span class="step-dot" :class="{ active: currentMode === 'pagination' }" />
        <span class="step-label">分页配置</span>
      </div>
    </div>

    <!-- 详情页（可选） -->
    <div class="step-group">
      <div class="group-label">详情页</div>
      <div class="step-item" @click="$emit('toggle-detail')">
        <el-switch :model-value="detailEnabled" size="small" @change="$emit('toggle-detail')" />
        <span class="step-label" style="margin-left:8px">{{ detailEnabled ? '已启用' : '未启用' }}</span>
      </div>
      <div
        v-if="detailEnabled"
        v-for="d in detailStepEntries" :key="d.key" class="step-item"
        :class="{ active: d.key === currentMode }"
        @click="$emit('switch-mode', d.key)"
      >
        <span class="step-dot" :class="{ done: d.done, active: d.key === currentMode }" />
        <span class="step-label">{{ d.label }}</span>
        <span v-if="d.done" class="step-check">&#10003;</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ModeSwitcher — 左侧配置步骤面板。
 *
 * 步骤状态由实际数据驱动：
 * - ITEM done ⇔ itemSelector 有值
 * - 字段 done ⇔ listFields 有对应规则
 */

import { computed } from 'vue'

import type {
  ModeKey, ListFieldName, DetailFieldName, FieldRuleDraft,
  ListModeKey, DetailModeKey,
} from '@/types/htmlConfig'
import { LIST_MODE_KEYS, LIST_MODE_LABELS, DETAIL_MODE_LABELS } from '@/types/htmlConfig'

const props = defineProps<{
  currentMode: ModeKey
  listFields: Partial<Record<ListFieldName, FieldRuleDraft[]>>
  itemSelector: string | null
  detailEnabled: boolean
  detailFields: Partial<Record<DetailFieldName, FieldRuleDraft[]>>
}>()

defineEmits<{
  'switch-mode': [key: ModeKey]
  'toggle-detail': []
}>()

interface StepEntry { key: ModeKey; label: string; done: boolean }

/** 列表步骤：ITEM 看 itemSelector，字段看 listFields */
const listStepEntries = computed<StepEntry[]>(() =>
  LIST_MODE_KEYS.map((k: ListModeKey): StepEntry => ({
    key: k as ModeKey,
    label: LIST_MODE_LABELS[k],
    done: k === 'item'
      ? !!props.itemSelector
      : !!(props.listFields[k as ListFieldName]?.length),
  }))
)

/** 详情步骤 */
const detailStepEntries = computed<StepEntry[]>(() => {
  const keys: DetailModeKey[] = [
    'detail.title', 'detail.content', 'detail.publishDate',
    'detail.issuingAgency', 'detail.documentNumber',
  ]
  return keys.map((k: DetailModeKey): StepEntry => ({
    key: k as ModeKey,
    label: DETAIL_MODE_LABELS[k],
    done: !!(props.detailFields[k.replace('detail.', '') as DetailFieldName]?.length),
  }))
})
</script>

<style scoped>
.mode-switcher { padding: 12px 12px 12px 16px; }
.switcher-title { margin: 0 0 12px 0; font-size: 14px; font-weight: 600; color: #303133; }
.step-group { margin-bottom: 12px; }
.group-label { font-size: 11px; color: #909399; text-transform: uppercase; margin-bottom: 4px; padding-left: 4px; }
.step-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 4px; cursor: pointer; transition: background 0.15s; }
.step-item:hover { background: #f5f7fa; }
.step-item.active { background: #ecf5ff; }
.step-dot { width: 8px; height: 8px; border-radius: 50%; background: #dcdfe6; flex-shrink: 0; }
.step-dot.active { background: #409eff; }
.step-dot.done { background: #67c23a; }
.step-label { font-size: 13px; color: #303133; }
.step-check { font-size: 12px; color: #67c23a; margin-left: auto; }
</style>
