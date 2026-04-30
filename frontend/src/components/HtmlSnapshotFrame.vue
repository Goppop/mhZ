<template>
  <div class="snapshot-frame-wrapper">
    <div v-if="errorMsg" class="frame-error">
      <el-alert :title="errorMsg" type="error" show-icon :closable="false" />
    </div>
    <iframe
      ref="iframeRef"
      class="snapshot-iframe"
      title="HTML 快照"
      @load="onIframeLoad"
    ></iframe>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import type { ClickedElementInfo, ElementSelection, FrameMessage, SelectionMode } from '@/types/htmlConfig'
import { buildInjectScript, onFrameMessage } from '@/utils/iframeBridge'

const props = defineProps<{
  html: string
  currentMode: SelectionMode
  selections: Partial<Record<SelectionMode, ElementSelection>>
}>()

const emit = defineEmits<{
  hover: [info: ClickedElementInfo]
  select: [selection: ElementSelection]
  frameReady: []
  frameError: [message: string]
}>()

const iframeRef = ref<HTMLIFrameElement | null>(null)
const errorMsg = ref<string | null>(null)
let removeListener: (() => void) | null = null
let frameLoaded = false
// Store direct DOM reference as fallback when template ref isn't ready yet
let iframeEl: HTMLIFrameElement | null = null

function getIframe(): HTMLIFrameElement | null {
  return iframeRef.value || iframeEl
}

function buildInjectedHtml(): string {
  const script = buildInjectScript(props.currentMode)
  const injected = `<style>
.__crawler_hover__{outline:2px dashed #409eff!important;cursor:crosshair!important}
.__crawler_selected__{outline:3px solid #67c23a!important;background:rgba(103,194,58,0.12)!important}
.__crawler_same_group__{outline:2px solid #e6a23c!important;background:rgba(230,162,60,0.10)!important}
</style>
<script>${script}<\/script>`

  let html = props.html
  if (html.includes('</head>')) {
    html = html.replace('</head>', injected + '</head>')
  } else if (html.includes('<body>')) {
    html = html.replace('<body>', '<head>' + injected + '</head><body>')
  } else if (html.includes('<html>')) {
    html = html.replace('<html>', '<html><head>' + injected + '</head>')
  } else {
    html = '<!DOCTYPE html><html><head>' + injected + '</head><body>' + html + '</body></html>'
  }
  return html
}

function writeContent() {
  console.log('[SnapshotFrame] writeContent 调用, props.html长度=', props.html?.length)
  const iframe = getIframe()
  console.log('[SnapshotFrame] iframe 获取: ref=', !!iframeRef.value, 'domEl=', !!iframeEl, '最终=', !!iframe)
  if (!iframe) {
    console.error('[SnapshotFrame] iframe 为 null, 无法写入!')
    return
  }

  const doc = iframe.contentDocument || iframe.contentWindow?.document
  console.log('[SnapshotFrame] contentDocument=', !!doc)
  if (!doc) {
    errorMsg.value = '无法访问 iframe 文档'
    emit('frameError', errorMsg.value!)
    console.error('[SnapshotFrame] 无法访问 iframe 文档!')
    return
  }

  errorMsg.value = null
  try {
    const content = buildInjectedHtml()
    console.log('[SnapshotFrame] 准备写入, 内容长度=', content.length)
    doc.open()
    doc.write(content)
    doc.close()
    console.log('[SnapshotFrame] write 完成, doc.body.children=', doc.body?.children?.length)
  } catch (e: any) {
    errorMsg.value = '写入 HTML 失败: ' + (e.message || '未知错误')
    emit('frameError', errorMsg.value!)
    console.error('[SnapshotFrame] write 异常:', e)
  }
}

function onIframeLoad(e: Event) {
  console.log('[SnapshotFrame] iframe @load 触发, frameLoaded=', frameLoaded)
  // Capture direct DOM reference from event, in case template ref isn't bound yet
  iframeEl = e.target as HTMLIFrameElement
  console.log('[SnapshotFrame] e.target 捕获: iframeEl=', !!iframeEl)

  if (frameLoaded) return
  frameLoaded = true
  removeListener = onFrameMessage(handleFrameMessage)
  writeContent()
}

function handleFrameMessage(msg: FrameMessage) {
  console.log('[SnapshotFrame] 收到 iframe 消息, type=', msg.type)
  switch (msg.type) {
    case 'ready':
      console.log('[SnapshotFrame] iframe 脚本就绪')
      emit('frameReady')
      break
    case 'hover':
      emit('hover', msg.payload as ClickedElementInfo)
      break
    case 'select':
      console.log('[SnapshotFrame] 元素被选中, mode=', (msg.payload as any)?.mode)
      emit('select', msg.payload as ElementSelection)
      break
    case 'error':
      errorMsg.value = String(msg.payload || '未知错误')
      emit('frameError', errorMsg.value!)
      break
  }
}

function postToFrame(type: string, payload?: unknown) {
  const iframe = getIframe()
  if (!iframe) {
    console.warn('[SnapshotFrame] postToFrame 失败: iframe 为 null, type=' + type)
    return
  }
  if (!iframe.contentWindow) {
    console.warn('[SnapshotFrame] postToFrame 失败: contentWindow 为 null, type=' + type)
    return
  }
  console.log('[SnapshotFrame] postToFrame: type=' + type + ' payload=' + (payload !== undefined ? JSON.stringify(payload) : '(无)'))
  iframe.contentWindow.postMessage(
    { source: 'html-config-parent', type, payload },
    '*'
  )
}

function setMode(mode: SelectionMode) {
  postToFrame('set-mode', mode)
}

function clearSelection() {
  postToFrame('clear-selection')
}

// Also try to capture iframe via onMounted
onMounted(() => {
  console.log('[SnapshotFrame] onMounted, iframeRef.value=', !!iframeRef.value)
  if (iframeRef.value) {
    iframeEl = iframeRef.value
  }
})

watch(
  () => props.html,
  (newHtml, oldHtml) => {
    console.log('[SnapshotFrame] watch html: newLen=', newHtml?.length, 'oldLen=', oldHtml?.length, 'frameLoaded=', frameLoaded)
    if (newHtml) {
      if (frameLoaded) {
        writeContent()
      } else {
        console.log('[SnapshotFrame] frame未加载, nextTick 兜底')
        nextTick(() => {
          console.log('[SnapshotFrame] nextTick: frameLoaded=', frameLoaded, 'iframeRef=', !!iframeRef.value, 'iframeEl=', !!iframeEl)
          if (!frameLoaded) {
            if (iframeRef.value) iframeEl = iframeRef.value
            if (iframeEl) {
              frameLoaded = true
              removeListener = onFrameMessage(handleFrameMessage)
              writeContent()
            }
          }
        })
      }
      errorMsg.value = null
    }
  }
)

watch(
  () => props.currentMode,
  (mode) => {
    if (frameLoaded) {
      setMode(mode)
    }
  }
)

onBeforeUnmount(() => {
  removeListener?.()
  iframeEl = null
})

defineExpose({ setMode, clearSelection })
</script>

<style scoped>
.snapshot-frame-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.frame-error {
  padding: 12px;
}

.snapshot-iframe {
  flex: 1;
  width: 100%;
  border: none;
  background: #fff;
}
</style>
