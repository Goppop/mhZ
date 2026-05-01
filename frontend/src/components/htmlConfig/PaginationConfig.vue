<template>
  <div class="pagination-config">
    <h3 class="section-title">分页配置</h3>

    <div class="form-row">
      <span class="form-label">分页模式</span>
      <el-radio-group v-model="localMode" size="small" @change="emitChange">
        <el-radio value="NONE">不分页（只抓当前页）</el-radio>
        <el-radio value="URL_TEMPLATE">按 URL 模板翻页</el-radio>
      </el-radio-group>
    </div>

    <template v-if="localMode === 'URL_TEMPLATE'">
      <div class="form-row">
        <span class="form-label">URL 模板</span>
        <el-input
          v-model="localUrlTemplate"
          placeholder="https://example.com/list_{page}.htm"
          size="small"
          style="width:380px"
          @change="emitChange"
        />
      </div>
      <div class="form-row-inline">
        <span class="form-label">起始页</span>
        <el-input-number v-model="localStartPage" :min="1" size="small" style="width:100px" @change="emitChange" />
        <span class="form-label" style="margin-left:20px">最大页</span>
        <el-input-number v-model="localMaxPages" :min="1" :max="100" size="small" style="width:100px" @change="emitChange" />
      </div>
      <div class="form-row">
        <el-button size="small" type="primary" :loading="previewing" @click="$emit('preview')">
          {{ previewing ? '预览中...' : '预览分页' }}
        </el-button>
        <span v-if="previewItemCounts.length > 0" class="preview-info">
          各页命中: {{ previewItemCounts.join(', ') }}
        </span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  mode: 'NONE' | 'URL_TEMPLATE'
  urlTemplate: string | null
  startPage: number
  maxPages: number
  previewing: boolean
  previewItemCounts: number[]
}>()

const emit = defineEmits<{
  change: [value: {
    mode: 'NONE' | 'URL_TEMPLATE'
    urlTemplate: string | null
    startPage: number
    maxPages: number
  }]
  preview: []
}>()

const localMode = ref(props.mode)
const localUrlTemplate = ref(props.urlTemplate || '')
const localStartPage = ref(props.startPage)
const localMaxPages = ref(props.maxPages)

watch(function () { return props.mode }, function (v) { localMode.value = v })
watch(function () { return props.urlTemplate }, function (v) { localUrlTemplate.value = v || '' })
watch(function () { return props.startPage }, function (v) { localStartPage.value = v })
watch(function () { return props.maxPages }, function (v) { localMaxPages.value = v })

function emitChange() {
  emit('change', {
    mode: localMode.value,
    urlTemplate: localMode.value === 'URL_TEMPLATE' ? localUrlTemplate.value : null,
    startPage: localStartPage.value,
    maxPages: localMaxPages.value,
  })
}
</script>

<style scoped>
.pagination-config {
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
}

.section-title {
  margin: 0 0 10px 0;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.form-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.form-row-inline {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.form-label {
  font-size: 12px;
  color: #606266;
  width: 80px;
  flex-shrink: 0;
}

.preview-info {
  margin-left: 12px;
  font-size: 12px;
  color: #67c23a;
}
</style>
