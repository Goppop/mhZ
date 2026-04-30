<template>
  <div class="mode-toolbar">
    <h3 class="panel-title">点选模式</h3>
    <el-radio-group
      :model-value="currentMode"
      @update:model-value="handleChange"
      size="small"
    >
      <el-radio-button
        v-for="mode in modes"
        :key="mode"
        :value="mode"
      >
        {{ meta[mode].label }}
      </el-radio-button>
    </el-radio-group>
    <div class="mode-hint">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ meta[currentMode].hint }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import type { SelectionMode, ElementSelection } from '@/types/htmlConfig'
import { SELECTION_MODE_META, STEP_ORDER } from '@/types/htmlConfig'

defineProps<{
  currentMode: SelectionMode
  selections: Partial<Record<SelectionMode, ElementSelection>>
}>()

const emit = defineEmits<{
  'update:mode': [mode: SelectionMode]
}>()

const modes = STEP_ORDER
const meta = SELECTION_MODE_META

function handleChange(val: SelectionMode) {
  emit('update:mode', val)
}
</script>

<style scoped>
.mode-toolbar {
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.panel-title {
  margin: 0 0 10px 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.el-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mode-hint {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
  line-height: 1.4;
}
</style>
