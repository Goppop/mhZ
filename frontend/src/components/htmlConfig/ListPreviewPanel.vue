<template>
  <div class="list-preview">
    <div class="preview-header">
      <h3 class="section-title">实时预览</h3>
      <el-tag v-if="loading" size="small" type="warning">加载中</el-tag>
      <span v-else-if="samples.length > 0" class="preview-meta">
        共 {{ itemCount }} 条 · 展示前 {{ samples.length }} 条
      </span>
    </div>

    <!-- 字段命中率 -->
    <div v-if="statFields.length > 0" class="stats-row">
      <div v-for="name in statFields" :key="name" class="stat-item">
        <span class="stat-name">{{ fieldLabel(name) }}</span>
        <el-tag
          :type="hitRateBadge(fieldStats[name]?.hitRate ?? 0)"
          size="small"
        >{{ hitRatePercent(fieldStats[name]?.hitRate) }}</el-tag>
        <span v-if="fieldStats[name]?.blankCount" class="stat-blank">
          {{ fieldStats[name].blankCount }} 空
        </span>
      </div>
    </div>

    <!-- 样例表格 -->
    <div v-if="samples.length > 0" class="sample-table-wrap">
      <table class="sample-table">
        <thead>
          <tr>
            <th class="col-idx">#</th>
            <th v-for="name in statFields" :key="name" class="col-field">{{ fieldLabel(name) }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in samples" :key="s.itemIndex">
            <td class="col-idx">{{ s.itemIndex + 1 }}</td>
            <td v-for="name in statFields" :key="name" class="col-field">
              <template v-if="s.fields[name]?.value">
                <span class="cell-value">{{ s.fields[name]!.value }}</span>
                <el-tooltip
                  v-if="s.fields[name]!.warnings?.length"
                  :content="s.fields[name]!.warnings.join('; ')"
                >
                  <span class="cell-warn">!</span>
                </el-tooltip>
              </template>
              <span v-else class="cell-empty">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="!loading" class="empty-preview">
      绑定字段后此处显示提取结果
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ListPreviewPanel — 实时预览面板。
 *
 * 展示各字段的命中率统计 + 前 10 条样本表格。
 */

import { computed } from 'vue'
import { LIST_FIELD_LABELS } from '@/types/htmlConfig'

type SampleFields = Record<string, { value: string | null; raw: string; warnings: string[] } | null>

const props = defineProps<{
  loading: boolean
  itemCount: number
  samples: Array<{ itemIndex: number; fields: SampleFields }>
  fieldStats: Record<string, { hitRate: number; blankCount: number }>
}>()

const statFields = computed(() => Object.keys(props.fieldStats))

function fieldLabel(name: string): string {
  return (LIST_FIELD_LABELS as Record<string, string>)[name] || name
}

function hitRatePercent(rate: number | undefined): string {
  if (rate == null) return '—'
  return Math.round(rate * 100) + '%'
}

function hitRateBadge(rate: number): string {
  if (rate >= 0.8) return 'success'
  if (rate > 0) return 'warning'
  return 'danger'
}
</script>

<style scoped>
.list-preview { padding: 12px 16px; border-top: 1px solid #e4e7ed; }
.preview-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.section-title { margin: 0; font-size: 13px; font-weight: 600; color: #303133; }
.preview-meta { font-size: 11px; color: #909399; }
.stats-row { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
.stat-item { display: flex; align-items: center; gap: 4px; }
.stat-name { font-size: 11px; color: #606266; }
.stat-blank { font-size: 11px; color: #e6a23c; }
.sample-table-wrap { max-height: 320px; overflow: auto; }
.sample-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.sample-table th { text-align: left; padding: 4px 8px; background: #f5f7fa; color: #909399; font-weight: 500; position: sticky; top: 0; }
.sample-table td { padding: 4px 8px; border-bottom: 1px solid #f0f0f0; }
.col-idx { width: 32px; color: #c0c4cc; }
.cell-value { color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 180px; display: inline-block; }
.cell-warn { color: #e6a23c; margin-left: 4px; font-weight: bold; cursor: help; }
.cell-empty { color: #c0c4cc; }
.empty-preview { font-size: 12px; color: #c0c4cc; padding: 20px 0; text-align: center; }
</style>
