import type { PageLoadResult } from '@/types/htmlConfig'

const USE_MOCK = false

export async function loadPage(url: string, timeoutMs = 15000): Promise<PageLoadResult> {
  console.log('[loadPage] 开始加载, url=', url, 'mock模式=', USE_MOCK)
  if (USE_MOCK) {
    return loadMockPage(url)
  }
  const res = await fetch('/api/html-config/load-page', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url, headers: {}, timeoutMs }),
  })
  if (!res.ok) {
    throw new Error(`加载页面失败: ${res.status} ${res.statusText}`)
  }
  return res.json()
}

async function loadMockPage(_url: string): Promise<PageLoadResult> {
  console.log('[loadMockPage] 开始 mock 加载')
  await new Promise((r) => setTimeout(r, 600))

  const mockHtml = await fetch('/mock/list.html')
  console.log('[loadMockPage] fetch /mock/list.html status=', mockHtml.status, 'ok=', mockHtml.ok)
  if (!mockHtml.ok) {
    console.log('[loadMockPage] 使用 FALLBACK_MOCK_HTML, 长度=', FALLBACK_MOCK_HTML.length)
    return {
      html: FALLBACK_MOCK_HTML,
      finalUrl: _url || 'https://example.gov.cn/list.html',
      statusCode: 200,
      title: '示例列表页',
      warnings: [],
      error: null,
    }
  }

  const htmlText = await mockHtml.text()
  console.log('[loadMockPage] mock HTML 获取成功, 长度=', htmlText.length, '前100字符=', htmlText.substring(0, 100))
  return {
    html: htmlText,
    finalUrl: _url || 'https://example.gov.cn/list.html',
    statusCode: 200,
    title: '示例列表页',
    warnings: [],
    error: null,
  }
}

const FALLBACK_MOCK_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>示例列表页</title>
<style>
  body { font-family: sans-serif; padding: 20px; background: #f5f5f5; }
  .news-list { max-width: 800px; margin: 0 auto; }
  .news-item { background: #fff; margin-bottom: 16px; padding: 16px; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
  .news-title { font-size: 18px; font-weight: bold; margin-bottom: 8px; }
  .news-title a { color: #1a1a1a; text-decoration: none; }
  .news-title a:hover { color: #409eff; }
  .news-meta { font-size: 13px; color: #999; margin-bottom: 6px; }
  .news-meta span { margin-right: 16px; }
  .news-summary { font-size: 14px; color: #555; line-height: 1.6; }
</style>
</head>
<body>
<div class="news-list">
  <div class="news-item" data-id="001">
    <div class="news-title"><a href="/detail/001.html">关于进一步做好数字经济相关工作的通知</a></div>
    <div class="news-meta">
      <span class="news-agency">国家发改委</span>
      <span class="news-date">2026-04-28</span>
      <span class="news-docnum">发改数字〔2026〕12号</span>
    </div>
    <div class="news-summary">为深入贯彻党中央、国务院关于发展数字经济的决策部署，进一步做好数字经济相关工作，现就有关事项通知如下……</div>
  </div>
  <div class="news-item" data-id="002">
    <div class="news-title"><a href="/detail/002.html">关于印发《数据安全管理办法》的通知</a></div>
    <div class="news-meta">
      <span class="news-agency">工业和信息化部</span>
      <span class="news-date">2026-04-25</span>
      <span class="news-docnum">工信部信管〔2026〕45号</span>
    </div>
    <div class="news-summary">为加强数据安全管理，保障数据安全，促进数据开发利用，制定本办法……</div>
  </div>
  <div class="news-item" data-id="003">
    <div class="news-title"><a href="/detail/003.html">2026年第一季度信息通信行业发展情况通报</a></div>
    <div class="news-meta">
      <span class="news-agency">工业和信息化部</span>
      <span class="news-date">2026-04-20</span>
      <span class="news-docnum">工信部通函〔2026〕88号</span>
    </div>
    <div class="news-summary">2026年第一季度，信息通信行业运行总体平稳，业务收入稳步增长，网络基础设施持续完善……</div>
  </div>
</div>
</body>
</html>`