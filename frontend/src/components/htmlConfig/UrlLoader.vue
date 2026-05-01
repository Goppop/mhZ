<template>
  <div class="url-loader">
    <div class="url-input-row">
      <el-input
        v-model="localUrl" placeholder="输入列表页 URL，如 https://www.gov.cn/..."
        clearable size="default" class="url-input" @keyup.enter="handleLoad"
      />
      <el-button type="primary" :loading="loading" @click="handleLoad">
        {{ loading ? '加载中...' : '加载页面' }}
      </el-button>
      <el-button v-if="!loading && statusCode > 0" text @click="showAdvanced = !showAdvanced">
        {{ showAdvanced ? '收起配置' : '高级配置' }}
      </el-button>
    </div>

    <div v-if="showAdvanced" class="advanced-row">
      <el-input v-model="localTimeout" placeholder="超时(ms)" size="small" style="width:130px" type="number" />
      <el-input v-model="headersText" placeholder='请求头 JSON，如 {"User-Agent":"..."}' size="small" style="width:300px" />
      <span v-if="statusCode > 0" class="status-line">HTTP {{ statusCode }} · {{ pageTitle || '无标题' }} · {{ elapsed }}ms</span>
      <span v-if="errorMsg" class="error-line">{{ errorMsg }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * UrlLoader — 顶部 URL 输入栏。
 *
 * 职责：URL 输入、超时/headers 配置、加载按钮、状态展示。
 */

import { ref, watch } from 'vue'

const props = defineProps<{
  loading: boolean
  statusCode: number
  pageTitle: string
  elapsed: number
  errorMsg: string
  initialUrl: string
  initialTimeout: number
  initialHeaders: Record<string, string>
}>()

const emit = defineEmits<{
  load: [url: string, timeoutMs: number, headers: Record<string, string>]
}>()

const localUrl = ref(props.initialUrl)
const localTimeout = ref(props.initialTimeout)
const headersText = ref(JSON.stringify(props.initialHeaders) || '')
const showAdvanced = ref(false)

watch(() => props.initialUrl, v => { localUrl.value = v })

function handleLoad(): void {
  let url = localUrl.value.trim()
  if (!url) return
  if (!url.startsWith('http://') && !url.startsWith('https://')) {
    url = 'https://' + url
  }

  let headers: Record<string, string> = {}
  try {
    if (headersText.value.trim()) headers = JSON.parse(headersText.value)
  } catch { /* 解析失败用空 headers */ }

  emit('load', url, localTimeout.value || 15000, headers)
}
</script>

<style scoped>
.url-loader { padding: 10px 16px; background: #fff; border-bottom: 1px solid #e4e7ed; }
.url-input-row { display: flex; align-items: center; gap: 10px; }
.url-input { flex: 1; max-width: 600px; }
.advanced-row { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
.status-line { font-size: 12px; color: #67c23a; }
.error-line { font-size: 12px; color: #f56c6c; }
</style>
