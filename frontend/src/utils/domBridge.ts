// ============================================================
// PRD V2 第 10.4 节 — iframe 通信协议
// ============================================================

import type { ClickedElementInfo, ParentToFrameMessage, FrameToParentMessage } from '@/types/htmlConfig'

/**
 * 向 iframe 发送消息。
 */
export function postToFrame(iframe: HTMLIFrameElement, msg: ParentToFrameMessage): void {
  iframe.contentWindow?.postMessage(msg, '*')
}

/**
 * 在父窗口监听来自 iframe 的消息。
 */
export function onFrameMessage(
  handler: (msg: FrameToParentMessage) => void,
): () => void {
  const listener = function (e: MessageEvent) {
    const data = e.data
    if (!data || typeof data.source !== 'string') return
    if (data.source !== 'html-config-frame') return
    handler(data as FrameToParentMessage)
  }
  window.addEventListener('message', listener)
  return function () {
    window.removeEventListener('message', listener)
  }
}

// ============================================================
// 注入到 iframe 的点选脚本（字符串形式，PRD 10.4.3 + 11.2 节）
// ============================================================

export function buildInjectScript(): string {
  return '(' + (function () {
    var ATTR_PRIORITY = ['href', 'title', 'src', 'alt', 'content', 'value', 'name', 'role', 'aria-label']
    var CURRENT_MODE: string = 'item'
    var hoveredEl: Element | null = null
    var selectedEl: Element | null = null

    // ---- 注入高亮样式（11.2 节） ----
    var style = document.createElement('style')
    style.textContent =
      '.__crawler_hover__{outline:1px solid rgba(64,158,255,0.6)!important;cursor:crosshair!important}' +
      '.__crawler_selected__{outline:2px solid #409EFF!important;background:rgba(64,158,255,0.1)!important}' +
      '.__crawler_match__{background:rgba(103,194,58,0.15)!important}'
    document.head.appendChild(style)

    // ---- DOM 工具 ----
    function buildIndexPath(el: Element): number[] {
      var path: number[] = []
      var cur: Element | null = el
      while (cur && cur !== document.documentElement) {
        var elParent: Element | null = cur.parentElement
        if (!elParent) break
        var idx = 0
        for (var ci = 0; ci < elParent.children.length; ci++) {
          if (elParent.children[ci] === cur) { idx = ci; break }
        }
        path.unshift(idx)
        cur = elParent
      }
      return path
    }

    function isVisible(el: Element): boolean {
      var s = window.getComputedStyle(el)
      if (s.display === 'none') return false
      if (s.visibility === 'hidden') return false
      if (s.opacity === '0') return false
      var r = el.getBoundingClientRect()
      if (r.width <= 0 || r.height <= 0) return false
      return true
    }

    function trunc(s: string, max: number): string {
      if (!s) return ''
      s = s.replace(/\s+/g, ' ').trim()
      return s.length > max ? s.slice(0, max) + '...' : s
    }

    function collectAttrs(el: Element): Record<string, string> {
      var attrs: Record<string, string> = {}
      for (var i = 0; i < ATTR_PRIORITY.length; i++) {
        var v = el.getAttribute(ATTR_PRIORITY[i])
        if (v !== null && v !== '') attrs[ATTR_PRIORITY[i]] = v
      }
      // data-*
      for (var j = 0; j < el.attributes.length; j++) {
        var a = el.attributes[j]
        if (a.name.indexOf('data-') === 0) attrs[a.name] = a.value
      }
      return attrs
    }

    function buildCandidates(el: Element, attrs: Record<string, string>): any[] {
      var candidates: any[] = []
      var text = (el as HTMLElement).innerText || el.textContent || ''
      text = text.replace(/\s+/g, ' ').trim()

      // TEXT
      if (text) {
        candidates.push({
          type: 'TEXT',
          value: text.length > 200 ? text.slice(0, 200) + '...' : text,
          hidden: false,
        })
      }

      // ATTR
      var seen: Record<string, boolean> = {}
      for (var i = 0; i < ATTR_PRIORITY.length; i++) {
        var attrName = ATTR_PRIORITY[i]
        if (seen[attrName]) continue
        var val = attrs[attrName]
        if (val === undefined || val === null || val === '') continue
        seen[attrName] = true
        candidates.push({
          type: 'ATTR',
          attrName: attrName,
          value: val.length > 200 ? val.slice(0, 200) + '...' : val,
          hidden: !(isVisible(el)),
        })
      }
      // data-* 属性
      var attrKeys = Object.keys(attrs)
      for (var k = 0; k < attrKeys.length; k++) {
        var ak = attrKeys[k]
        if (seen[ak]) continue
        if (ak.indexOf('data-') === 0) {
          seen[ak] = true
          candidates.push({
            type: 'ATTR',
            attrName: ak,
            value: attrs[ak].length > 200 ? attrs[ak].slice(0, 200) + '...' : attrs[ak],
            hidden: true,
          })
        }
      }

      // HTML（仅元素含子节点时）
      if (el.children.length > 0) {
        var innerHtml = el.innerHTML
        candidates.push({
          type: 'HTML',
          value: innerHtml.length > 200 ? innerHtml.slice(0, 200) + '...' : innerHtml,
          hidden: false,
        })
      }

      return candidates
    }

    function buildInfo(el: Element): any {
      var rect = el.getBoundingClientRect()
      var attrs = collectAttrs(el)
      var text = (el as HTMLElement).innerText || el.textContent || ''
      text = text.replace(/\s+/g, ' ').trim()
      var truncatedText = text.length > 500 ? text.slice(0, 500) + '...' : text
      var innerHtml = el.innerHTML || ''
      var outerHtml = el.outerHTML || ''

      var truncatedInner = innerHtml.length > 5000 ? innerHtml.slice(0, 5000) + '...' : innerHtml
      var truncatedOuter = outerHtml.length > 8000 ? outerHtml.slice(0, 8000) + '...' : outerHtml

      return {
        tag: el.tagName.toLowerCase(),
        id: el.id || null,
        classNames: Array.prototype.slice.call(el.classList),
        attributes: attrs,
        innerText: truncatedText,
        innerHtml: truncatedInner,
        outerHtml: truncatedOuter,
        indexPath: buildIndexPath(el),
        cssPath: '',
        bounding: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) },
        computedVisible: isVisible(el),
        extractableCandidates: buildCandidates(el, attrs),
        __truncated: { innerText: text.length, innerHtml: innerHtml.length, outerHtml: outerHtml.length },
      }
    }

    function sendMsg(msg: FrameToParentMessage): void {
      ;(msg as any).source = 'html-config-frame'
      window.parent.postMessage(msg, '*')
    }

    // ---- 事件 ----
    document.addEventListener('mouseover', function (e: Event) {
      var target = e.target as Element
      if (!target || target === document.documentElement || target === document.body) return
      if (hoveredEl === target) return
      if (hoveredEl) hoveredEl.classList.remove('__crawler_hover__')
      hoveredEl = target
      target.classList.add('__crawler_hover__')
      sendMsg({ type: 'HOVER', click: buildInfo(target) })
    }, true)

    document.addEventListener('mouseout', function (e: Event) {
      if (e.target === hoveredEl) {
        (e.target as Element).classList.remove('__crawler_hover__')
        hoveredEl = null
      }
    }, true)

    document.addEventListener('click', function (e: Event) {
      e.preventDefault()
      e.stopPropagation()
      var target = e.target as Element
      if (!target || target === document.documentElement || target === document.body) return
      if (selectedEl) selectedEl.classList.remove('__crawler_selected__')
      selectedEl = target
      target.classList.add('__crawler_selected__')
      target.classList.remove('__crawler_hover__')
      sendMsg({ type: 'CLICK', click: buildInfo(target) })
    }, true)

    // 阻止链接跳转
    document.addEventListener('click', function (e: Event) {
      var a = (e.target as Element).closest('a')
      if (a && a.getAttribute('href') && a.getAttribute('href')!.indexOf('javascript:') !== 0) {
        e.preventDefault()
      }
    }, false)

    // 阻止表单提交
    document.addEventListener('submit', function (e: Event) {
      e.preventDefault()
    }, true)

    // ---- 监听父窗口消息 ----
    window.addEventListener('message', function (e: MessageEvent) {
      var msg = e.data
      if (!msg || msg.source !== 'html-config-parent') return

      if (msg.type === 'SET_MODE') {
        CURRENT_MODE = msg.mode
      } else if (msg.type === 'HIGHLIGHT_MATCHES') {
        // 清除旧高亮
        var oldMatches = document.querySelectorAll('.__crawler_match__')
        for (var j = 0; j < oldMatches.length; j++) {
          oldMatches[j].classList.remove('__crawler_match__')
        }
        // 按 indexPath 定位新匹配并高亮
        var paths: number[][] = msg.indexPaths || []
        for (var k = 0; k < paths.length; k++) {
          var el = resolveByIndexPath(document, paths[k])
          if (el) el.classList.add('__crawler_match__')
        }
      } else if (msg.type === 'CLEAR_HIGHLIGHT') {
        var clears = document.querySelectorAll('.__crawler_match__')
        for (var c = 0; c < clears.length; c++) {
          clears[c].classList.remove('__crawler_match__')
        }
      } else if (msg.type === 'CLEAR_SELECTION') {
        if (selectedEl) { selectedEl.classList.remove('__crawler_selected__'); selectedEl = null }
        if (hoveredEl) { hoveredEl.classList.remove('__crawler_hover__'); hoveredEl = null }
      }
    })

    function resolveByIndexPath(root: Document, indexPath: number[]): Element | null {
      var el: Element = root.documentElement
      for (var i = 0; i < indexPath.length; i++) {
        var idx = indexPath[i]
        if (idx >= el.children.length) return null
        el = el.children[idx]
      }
      return el
    }

    sendMsg({ type: 'READY' } as FrameToParentMessage)
  }.toString()) + ')()'
}
