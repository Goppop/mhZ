<template>
  <div class="step-panel">
    <h3 class="panel-title">配置步骤</h3>
    <div
      v-for="step in steps"
      :key="step.mode"
      class="step-item"
      :class="{
        'step-active': step.mode === currentMode,
        'step-done': step.status === 'done',
        'step-redo': step.status === 'redo',
      }"
      @click="handleClick(step.mode)"
    >
      <div class="step-indicator">
        <el-icon v-if="step.status === 'done'" class="step-icon done"><Check /></el-icon>
        <el-icon v-else-if="step.status === 'redo'" class="step-icon redo"><WarningFilled /></el-icon>
        <span v-else class="step-num">{{ step.index }}</span>
      </div>
      <div class="step-body">
        <span class="step-label">{{ step.label }}</span>
        <span class="step-status-text">
          {{ statusText(step.status) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check, WarningFilled } from '@element-plus/icons-vue'
import type { SelectionMode, ElementSelection } from '@/types/htmlConfig'
import { SELECTION_MODE_META, STEP_ORDER } from '@/types/htmlConfig'

const props = defineProps<{
  currentMode: SelectionMode
  selections: Partial<Record<SelectionMode, ElementSelection>>
}>()

const emit = defineEmits<{
  'select-step': [mode: SelectionMode]
}>()

interface StepEntry {
  mode: SelectionMode
  label: string
  index: number
  status: 'pending' | 'active' | 'done' | 'redo'
}

const steps = computed<StepEntry[]>(() =>
  STEP_ORDER.map((mode, i) => {
    const hasSelection = !!props.selections[mode]
    const isCurrent = mode === props.currentMode
    let status: StepEntry['status']
    if (hasSelection && isCurrent) {
      status = 'done'
    } else if (hasSelection) {
      status = 'done'
    } else if (isCurrent) {
      status = 'active'
    } else {
      status = 'pending'
    }
    return {
      mode,
      label: SELECTION_MODE_META[mode].label,
      index: i + 1,
      status,
    }
  })
)

function statusText(status: StepEntry['status']): string {
  switch (status) {
    case 'done': return '已选择'
    case 'active': return '正在选择'
    case 'redo': return '需重选'
    default: return ''
  }
}

function handleClick(mode: SelectionMode) {
  emit('select-step', mode)
}
</script>

<style scoped>
.step-panel {
  padding: 12px 12px 12px 16px;
}

.panel-title {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.step-item:hover {
  background: #f5f7fa;
}

.step-item.step-active {
  background: #ecf5ff;
}

.step-indicator {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #e4e7ed;
  color: #909399;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-active .step-num {
  background: #409eff;
  color: #fff;
}

.step-icon.done {
  color: #67c23a;
  font-size: 18px;
}

.step-icon.redo {
  color: #e6a23c;
  font-size: 18px;
}

.step-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.step-label {
  font-size: 13px;
  color: #303133;
  line-height: 1;
}

.step-status-text {
  font-size: 11px;
  color: #909399;
  line-height: 1;
}

.step-active .step-status-text {
  color: #409eff;
}

.step-done .step-status-text {
  color: #67c23a;
}
</style>
