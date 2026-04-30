import type { FrameMessage, ParentMessage, SelectionMode, ElementSelection } from '@/types/htmlConfig'

/**
 * Send a message from iframe to parent window.
 */
export function postToParent(msg: FrameMessage): void {
  window.parent.postMessage(msg, '*')
}

/**
 * Send a message from parent to iframe.
 */
export function postToFrame(iframe: HTMLIFrameElement, msg: ParentMessage): void {
  iframe.contentWindow?.postMessage(msg, '*')
}

/**
 * Listen for messages from iframe (in parent window).
 */
export function onFrameMessage(
  handler: (msg: FrameMessage) => void
): () => void {
  const listener = (e: MessageEvent) => {
    const msg = e.data as FrameMessage
    if (msg && msg.source === 'html-config-frame') {
      handler(msg)
    }
  }
  window.addEventListener('message', listener)
  return () => window.removeEventListener('message', listener)
}

/**
 * Build the script string to inject into the iframe.
 * This script handles hover highligh, click capture, and postMessage communication.
 */
export function buildInjectScript(currentMode: SelectionMode): string {
  return `
(function() {
  'use strict';

  var CURRENT_MODE = '${currentMode}';
  var hoveredEl = null;
  var selectedEl = null;

  // ---- styles ----
  var style = document.createElement('style');
  style.textContent = [
    '.__crawler_hover__ { outline: 2px dashed #409eff !important; cursor: crosshair !important; }',
    '.__crawler_selected__ { outline: 3px solid #67c23a !important; background: rgba(103,194,58,0.12) !important; }',
    '.__crawler_same_group__ { outline: 2px solid #e6a23c !important; background: rgba(230,162,60,0.10) !important; }',
    'a { pointer-events: auto; }'
  ].join('\\n');
  document.head.appendChild(style);

  // ---- helpers ----
  function buildCssPath(el) {
    var path = [];
    var cur = el;
    while (cur && cur !== document.body.parentElement) {
      var seg = cur.tagName.toLowerCase();
      if (cur.id) seg += '#' + cur.id;
      var classes = Array.from(cur.classList).filter(function(c) { return c.indexOf('__crawler_') !== 0; });
      if (classes.length > 0) seg += '.' + classes.slice(0, 2).join('.');
      path.unshift(seg);
      cur = cur.parentElement;
    }
    return path;
  }

  function buildIndexPath(el) {
    var path = [];
    var cur = el;
    while (cur && cur !== document.body.parentElement) {
      var parent = cur.parentElement;
      if (!parent) break;
      var idx = 0;
      for (var i = 0; i < parent.children.length; i++) {
        if (parent.children[i] === cur) { idx = i; break; }
      }
      path.unshift(idx);
      cur = parent;
    }
    return path;
  }

  var ATTR_PRIORITY = ['href','title','src','alt','content','value','name','role','aria-label'];

  function collectAttrs(el) {
    var attrs = {};
    for (var i = 0; i < ATTR_PRIORITY.length; i++) {
      var v = el.getAttribute(ATTR_PRIORITY[i]);
      if (v !== null && v !== '') attrs[ATTR_PRIORITY[i]] = v;
    }
    // Also collect data-* attributes but skip style/class/id/on*
    var allAttrs = el.attributes;
    for (var j = 0; j < allAttrs.length; j++) {
      var a = allAttrs[j];
      if (a.name.indexOf('data-') === 0) {
        attrs[a.name] = a.value;
      }
    }
    return attrs;
  }

  function computeVisible(el) {
    var style = window.getComputedStyle(el);
    if (style.display === 'none') return false;
    if (style.visibility === 'hidden') return false;
    if (style.opacity === '0') return false;
    var rect = el.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return false;
    return true;
  }

  function truncate(s, max) {
    if (!s) return '';
    s = s.replace(/\\s+/g, ' ').trim();
    return s.length > max ? s.slice(0, max) + '…' : s;
  }

  function buildCandidates(el, attrs, text, innerHtml) {
    var candidates = [];

    // TEXT candidate
    if (text) {
      candidates.push({
        type: 'TEXT',
        label: '可见文本',
        previewValue: text.length > 300 ? text.slice(0, 300) + '…' : text,
        length: text.length,
        recommended: true
      });
    }

    // ATTR candidates - priority attributes first, then others
    var seenAttr = {};
    function addAttrCandidate(attrName, recommended) {
      if (seenAttr[attrName]) return;
      var val = attrs[attrName];
      if (val === undefined || val === null || val === '') return;
      seenAttr[attrName] = true;
      var preview = val.length > 300 ? val.slice(0, 300) + '…' : val;
      candidates.push({
        type: 'ATTR',
        label: attrName + ' 属性',
        previewValue: preview,
        length: val.length,
        attrName: attrName,
        recommended: recommended || false
      });
    }

    // Priority attributes first
    for (var i = 0; i < ATTR_PRIORITY.length; i++) {
      addAttrCandidate(ATTR_PRIORITY[i], true);
    }
    // data-* attributes
    Object.keys(attrs).forEach(function(k) {
      if (ATTR_PRIORITY.indexOf(k) === -1) addAttrCandidate(k, false);
    });

    // HTML candidates
    if (innerHtml && innerHtml.trim()) {
      var innerPreview = innerHtml.length > 1000 ? innerHtml.slice(0, 1000) + '…' : innerHtml;
      candidates.push({
        type: 'HTML',
        label: '内部 HTML',
        previewValue: innerPreview,
        length: innerHtml.length,
        recommended: false
      });
    }

    return candidates;
  }

  function buildInfo(el) {
    var rect = el.getBoundingClientRect();
    var attrs = collectAttrs(el);
    var text = truncate(el.innerText || el.textContent || '', 300);
    var innerHtml = el.innerHTML || '';
    var outerHtml = el.outerHTML || '';

    return {
      tagName: el.tagName.toLowerCase(),
      id: el.id || undefined,
      classList: Array.from(el.classList),
      attributes: attrs,
      text: text,
      innerHtml: truncate(innerHtml, 1000),
      outerHtml: truncate(outerHtml, 1000),
      computedVisible: computeVisible(el),
      cssPath: buildCssPath(el),
      indexPath: buildIndexPath(el),
      bounding: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) },
      extractableCandidates: buildCandidates(el, attrs, text, innerHtml)
    };
  }

  function sendMessage(type, payload) {
    window.parent.postMessage({ source: 'html-config-frame', type: type, payload: payload }, '*');
  }

  // ---- event handlers ----
  document.addEventListener('mouseover', function(e) {
    var target = e.target;
    if (!target || target === document.documentElement || target === document.body) return;
    if (hoveredEl === target) return;
    if (hoveredEl) hoveredEl.classList.remove('__crawler_hover__');
    hoveredEl = target;
    target.classList.add('__crawler_hover__');
    sendMessage('hover', buildInfo(target));
  }, true);

  document.addEventListener('mouseout', function(e) {
    var target = e.target;
    if (target === hoveredEl) {
      target.classList.remove('__crawler_hover__');
      hoveredEl = null;
    }
  }, true);

  document.addEventListener('click', function(e) {
    e.preventDefault();
    e.stopPropagation();
    var target = e.target;
    if (!target || target === document.documentElement || target === document.body) return;
    if (selectedEl) selectedEl.classList.remove('__crawler_selected__');
    selectedEl = target;
    target.classList.add('__crawler_selected__');
    target.classList.remove('__crawler_hover__');
    var info = buildInfo(target);
    console.log('[iframe] click, CURRENT_MODE=' + CURRENT_MODE + ' target.tag=' + target.tagName + ' candidates=' + info.extractableCandidates.length);
    sendMessage('select', { mode: CURRENT_MODE, info: info, selectedAt: Date.now() });
  }, true);

  // Block link navigation
  document.addEventListener('click', function(e) {
    var a = e.target.closest('a');
    if (a && a.href && !a.href.startsWith('javascript:')) {
      e.preventDefault();
    }
  }, false);

  // Block form submission
  document.addEventListener('submit', function(e) {
    e.preventDefault();
  }, true);

  // ---- listen for parent messages ----
  window.addEventListener('message', function(e) {
    var msg = e.data;
    if (!msg || msg.source !== 'html-config-parent') return;
    if (msg.type === 'set-mode') {
      console.log('[iframe] set-mode 收到, 旧模式=' + CURRENT_MODE + ' 新模式=' + msg.payload);
      CURRENT_MODE = msg.payload;
    } else if (msg.type === 'clear-hover') {
      if (hoveredEl) { hoveredEl.classList.remove('__crawler_hover__'); hoveredEl = null; }
    } else if (msg.type === 'set-selections') {
      // payload: array of { mode, info } to mark
      if (selectedEl) selectedEl.classList.remove('__crawler_selected__');
      selectedEl = null;
    } else if (msg.type === 'clear-selection') {
      if (selectedEl) { selectedEl.classList.remove('__crawler_selected__'); selectedEl = null; }
    }
  });

  sendMessage('ready');
})();
`
}