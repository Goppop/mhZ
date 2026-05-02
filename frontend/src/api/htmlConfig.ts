// ============================================================
// PRD V2 第 8 章 — 后端 API 封装
// ============================================================

import type {
  ApiResponse,
  CapabilitiesData,
  LoadPageRequest,
  LoadPageData,
  SuggestItemRequest,
  SuggestItemData,
  SuggestFieldRequest,
  SuggestFieldData,
  PreviewListRequest,
  PreviewListData,
  PreviewDetailRequest,
  PreviewDetailData,
  SaveSourceRequest,
} from '@/types/htmlConfig'

const BASE = '/api/html-config'

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const opts: RequestInit = {
    method: method,
    headers: { 'Content-Type': 'application/json' },
  }
  if (body !== undefined) {
    opts.body = JSON.stringify(body)
  }
  const resp = await fetch(BASE + path, opts)
  const json: ApiResponse<T> = await resp.json()
  if (!json.success) {
    const err = new Error(json.message || '请求失败') as any
    err.code = json.code
    err.errors = json.errors
    throw err
  }
  return json.data
}

// ===================== 阶段 A =====================

// GET /capabilities（8.2）
export function getCapabilities(): Promise<CapabilitiesData> {
  return request('GET', '/capabilities')
}

// POST /load-page（8.3）
export function loadPage(req: LoadPageRequest): Promise<LoadPageData> {
  return request('POST', '/load-page', req)
}

// POST /suggest-item-selector（8.4）
export function suggestItemSelector(req: SuggestItemRequest): Promise<SuggestItemData> {
  return request('POST', '/suggest-item-selector', req)
}

// POST /suggest-field-rule（8.5）
export function suggestFieldRule(req: SuggestFieldRequest): Promise<SuggestFieldData> {
  return request('POST', '/suggest-field-rule', req)
}

// POST /preview-list（8.6）
export function previewList(req: PreviewListRequest): Promise<PreviewListData> {
  return request('POST', '/preview-list', req)
}

// POST /preview-detail（8.7）
export function previewDetail(req: PreviewDetailRequest): Promise<PreviewDetailData> {
  return request('POST', '/preview-detail', req)
}

// ===================== 阶段 B =====================

// GET /sources
export function getSources(): Promise<any[]> {
  return request('GET', '/sources')
}

// GET /sources/{id}
export function getSource(id: number): Promise<any> {
  return request('GET', '/sources/' + id)
}

// POST /sources
export function createSource(req: SaveSourceRequest): Promise<{ id: number }> {
  return request('POST', '/sources', req)
}

// PUT /sources/{id}
export function updateSource(id: number, req: SaveSourceRequest): Promise<void> {
  return request('PUT', '/sources/' + id, req)
}

// POST /sources/{id}/copy
export function copySource(id: number): Promise<{ id: number }> {
  return request('POST', '/sources/' + id + '/copy')
}

// DELETE /sources/{id}
export function deleteSource(id: number): Promise<void> {
  return request('DELETE', '/sources/' + id)
}

// POST /sources/{id}/toggle
export function toggleSource(id: number): Promise<void> {
  return request('POST', '/sources/' + id + '/toggle')
}

// POST /api/crawl/run/{id} — 复用 CrawlController 现有试跑接口（非 html-config 前缀）
export async function runSource(id: number): Promise<{
  status: string
  fetched: number
  unique: number
  matched: number
  errors: number
  durationMs: number
}> {
  const resp = await fetch('/api/crawl/run/' + id, { method: 'POST' })
  const json = await resp.json()
  if (!json.success) throw new Error(json.error || '试跑失败')
  return json.data
}
