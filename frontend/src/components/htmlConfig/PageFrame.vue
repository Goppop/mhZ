<template>
  <div class="page-frame-wrapper">
    <div v-if="errorText" class="frame-error-banner">
      <el-alert :title="errorText" type="error" show-icon :closable="false" />
    </div>
    <div v-if="!html && !loading" class="frame-empty">
      <el-empty description="输入 URL 并点击「加载页面」" />
    </div>
    <div v-else-if="loading" class="frame-loading">
      <el-skeleton :rows="12" animated />
    </div>
    <iframe
      v-if="html && !loading"
      ref="iframeRef"
      class="page-iframe"
      sandbox="allow-scripts"
      :srcdoc="processedHtml"
      title="HTML 快照"
      @load="onFrameLoad"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * PageFrame — 中间 iframe 渲染组件。
 *
 * 通过 srcdoc 属性注入 HTML + 点选脚本，
 * postMessage 与 iframe 通信（SET_MODE / HIGHLIGHT_MATCHES 等）。
 * sandbox="allow-scripts" 确保隔离，不访问主站 cookie。
 */
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import type { ModeKey, ClickedElementInfo } from '@/types/htmlConfig'
import { postToFrame, onFrameMessage, buildInjectScript } from '@/utils/domBridge'

const props = defineProps<{
  html: string
  currentMode: ModeKey
  loading: boolean
  matchedIndexPaths: number[][]
}>()

const emit = defineEmits<{
  hover: [info: ClickedElementInfo]
  click: [info: ClickedElementInfo]
  ready: []
  error: [msg: string]
}>()

const iframeRef = ref<HTMLIFrameElement | null>(null)
const errorText = ref('')
let removeListener: (() => void) | null = null
let frameReady = false

function getFrame(): HTMLIFrameElement | null {
  return iframeRef.value
}

// 构建带注入脚本的 HTML，通过 srcdoc 传入（避免跨域访问 contentDocument）
const processedHtml = computed(function () {
  let html = props.html
  if (!html) return ''

  // 移除原始 script 标签（PRD 16.3：服务端净化，前端兜底）
  html = html.replace(/<script[\s\S]*?<\/script>/gi, '')

  const script = buildInjectScript()
  const injected = '<style>' +
    '.__crawler_hover__{outline:1px solid rgba(64,158,255,0.6)!important;cursor:crosshair!important}' +
    '.__crawler_selected__{outline:2px solid #409EFF!important;background:rgba(64,158,255,0.1)!important}' +
    '.__crawler_match__{background:rgba(103,194,58,0.15)!important}' +
    '</style>' +
    '<script>' + script + '<\/script>'

  if (html.indexOf('</body>') !== -1) {
    return html.replace('</body>', injected + '</body>')
  }
  if (html.indexOf('</BODY>') !== -1) {
    return html.replace('</BODY>', injected + '</BODY>')
  }
  return '<!DOCTYPE html><html><head></head><body>' + html + injected + '</body></html>'
})

function onFrameLoad() {
  if (frameReady) return
  frameReady = true

  removeListener = onFrameMessage(function (msg) {
    switch (msg.type) {
      case 'READY':
        postToFrame(getFrame()!, { type: 'SET_MODE', mode: props.currentMode })
        emit('ready')
        break
      case 'HOVER':
        emit('hover', msg.click)
        break
      case 'CLICK':
        emit('click', msg.click)
        break
    }
  })
}

function setMode(mode: ModeKey) {
  const iframe = getFrame()
  console.log('[PageFrame.setMode] mode=', mode, 'iframe=', !!iframe)
  if (!iframe) return
  postToFrame(iframe, { type: 'SET_MODE', mode })
}

function highlightMatches(indexPaths: number[][]) {
  const iframe = getFrame()
  if (!iframe) return
  // Vue reactive proxy 数组无法被 postMessage 结构化克隆，先转成纯数组
  const plain = indexPaths.map(p => [...p])
  postToFrame(iframe, { type: 'HIGHLIGHT_MATCHES', indexPaths: plain })
}

function clearHighlights() {
  const iframe = getFrame()
  if (!iframe) return
  postToFrame(iframe, { type: 'CLEAR_HIGHLIGHT' })
}

function clearSelection() {
  const iframe = getFrame()
  if (!iframe) return
  postToFrame(iframe, { type: 'CLEAR_SELECTION' })
}

watch(function () { return props.currentMode }, function (mode) {
  if (frameReady) setMode(mode)
})

watch(function () { return props.matchedIndexPaths }, function (paths) {
  if (frameReady && paths && paths.length > 0) highlightMatches(paths)
})

onBeforeUnmount(function () {
  if (removeListener) removeListener()
})

defineExpose({ setMode, highlightMatches, clearHighlights, clearSelection })
</script>

<style scoped>
.page-frame-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.frame-error-banner {
  padding: 8px 12px;
}

.frame-empty,
.frame-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}

.page-iframe {
  flex: 1;
  width: 100%;
  border: none;
  background: #fff;
}
</style>
