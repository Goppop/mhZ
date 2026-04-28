import { useHostTheme, H1, Stack, Text, Divider, Row } from 'cursor/canvas';

type ArchNode = {
  id: string;
  label: string;
  sub?: string;
  x: number;
  y: number;
  w: number;
  h: number;
  variant?: 'accent' | 'muted' | 'outline' | 'normal';
};

type ArchEdge = {
  from: string;
  to: string;
  fa?: 'bottom' | 'right' | 'left' | 'top';
  ta?: 'top' | 'left' | 'right' | 'bottom';
  dashed?: boolean;
  label?: string;
};

export default function PolicyRadarArch() {
  const theme = useHostTheme();

  const SVG_W = 1020;
  const SVG_H = 620;
  const NH = 40;

  // Layer bands
  const bands = [
    { y: 8,   h: 74, title: '① 来源输入' },
    { y: 100, h: 74, title: '② 调度 · 接入' },
    { y: 192, h: 74, title: '③ 采集 / 搜索流水线' },
    { y: 284, h: 74, title: '④ 候选队列 · 详情抓取' },
    { y: 376, h: 74, title: '⑤ 数据存储' },
    { y: 468, h: 74, title: '⑥ 下游消费' },
  ];

  const ly = (i: number) => bands[i].y + bands[i].h / 2;

  const nodes: ArchNode[] = [
    // ── Layer 0: Sources ──────────────────────────────────────────
    { id: 'rss',      label: 'RSS 订阅',       x: 20,  y: ly(0) - NH/2, w: 130, h: NH },
    { id: 'api',      label: '公开 API',        x: 165, y: ly(0) - NH/2, w: 130, h: NH },
    { id: 'web',      label: '公开网页',        x: 310, y: ly(0) - NH/2, w: 130, h: NH },
    { id: 'search_kw',label: '关键词订阅',      x: 455, y: ly(0) - NH/2, w: 130, h: NH, variant: 'accent' },
    { id: 'pysrc',    label: 'Python 脚本源',   x: 600, y: ly(0) - NH/2, w: 140, h: NH, variant: 'outline' },

    // ── Layer 1: Scheduler + Runner Matrix ────────────────────────
    { id: 'sched',    label: '调度引擎', sub: 'Cron / 手动触发', x: 20, y: ly(1) - NH/2, w: 160, h: NH },
    { id: 'conn',     label: 'RunnerRegistry  ·  Connector 适配层', x: 200, y: ly(1) - NH/2, w: 420, h: NH, variant: 'accent' },
    { id: 'guard',    label: 'DomainGuard', sub: '白名单 + QPS', x: 640, y: ly(1) - NH/2, w: 150, h: NH, variant: 'muted' },

    // ── Layer 2: Pipeline ─────────────────────────────────────────
    { id: 'pipe',     label: 'fetch  ›  SearchProvider  ›  normalize  ›  dedup  ›  enrich  ›  KeywordMatcher', x: 20, y: ly(2) - NH/2, w: 760, h: NH },
    { id: 'scripts',  label: 'scripts/', x: 800, y: ly(2) - NH/2, w: 100, h: NH, variant: 'muted' },

    // ── Layer 3: Frontier ─────────────────────────────────────────
    { id: 'frontier', label: 'policy_url_frontier', sub: '候选 URL 队列', x: 20,  y: ly(3) - NH/2, w: 200, h: NH, variant: 'accent' },
    { id: 'fc',       label: 'FrontierConsumer', sub: '每 30s 批量消费', x: 240, y: ly(3) - NH/2, w: 190, h: NH },
    { id: 'dr',       label: 'DetailRouter', sub: '模板优先 + readability 兜底', x: 450, y: ly(3) - NH/2, w: 260, h: NH },
    { id: 'sdk',      label: 'Python SDK', x: 730, y: ly(3) - NH/2, w: 110, h: NH, variant: 'muted' },

    // ── Layer 4: Storage ──────────────────────────────────────────
    { id: 'db_raw',   label: 'raw_payload',      x: 20,  y: ly(4) - NH/2, w: 150, h: NH, variant: 'muted' },
    { id: 'db_doc',   label: 'policy_document',  x: 185, y: ly(4) - NH/2, w: 165, h: NH, variant: 'accent' },
    { id: 'db_task',  label: 'ingest_task',       x: 365, y: ly(4) - NH/2, w: 145, h: NH },
    { id: 'db_trace', label: 'trace_log',         x: 525, y: ly(4) - NH/2, w: 120, h: NH, variant: 'muted' },
    { id: 'db_frt',   label: 'url_frontier',      x: 660, y: ly(4) - NH/2, w: 120, h: NH, variant: 'muted' },

    // ── Layer 5: Consumers ────────────────────────────────────────
    { id: 'search',   label: '检索 / 匹配', x: 60,  y: ly(5) - NH/2, w: 170, h: NH },
    { id: 'notify',   label: '系统通知',    x: 285, y: ly(5) - NH/2, w: 170, h: NH },
    { id: 'monitor',  label: '监控 / 告警', x: 510, y: ly(5) - NH/2, w: 170, h: NH },
  ];

  const nodeMap: Record<string, ArchNode> = {};
  for (const n of nodes) nodeMap[n.id] = n;

  const ncx  = (id: string) => nodeMap[id].x + nodeMap[id].w / 2;
  const ncy  = (id: string) => nodeMap[id].y + nodeMap[id].h / 2;
  const ntop = (id: string) => nodeMap[id].y;
  const nbot = (id: string) => nodeMap[id].y + nodeMap[id].h;
  const nlft = (id: string) => nodeMap[id].x;
  const nrgt = (id: string) => nodeMap[id].x + nodeMap[id].w;

  const edges: ArchEdge[] = [
    // Sources → Connector
    { from: 'rss',     to: 'conn',     fa: 'bottom', ta: 'top' },
    { from: 'api',     to: 'conn',     fa: 'bottom', ta: 'top' },
    { from: 'web',     to: 'conn',     fa: 'bottom', ta: 'top' },
    { from: 'search_kw', to: 'conn',   fa: 'bottom', ta: 'top' },
    { from: 'pysrc',   to: 'conn',     fa: 'bottom', ta: 'top', dashed: true },
    // Scheduler → Connector
    { from: 'sched',   to: 'conn',     fa: 'right',  ta: 'left' },
    // Connector → DomainGuard
    { from: 'conn',    to: 'guard',    fa: 'right',  ta: 'left', dashed: true },
    // Connector → Pipeline
    { from: 'conn',    to: 'pipe',     fa: 'bottom', ta: 'top' },
    // Pipeline → scripts (Python)
    { from: 'pipe',    to: 'scripts',  fa: 'right',  ta: 'left', dashed: true },
    // Pipeline → Frontier (SearchRunner 出候选)
    { from: 'pipe',    to: 'frontier', fa: 'bottom', ta: 'top' },
    // Frontier → FrontierConsumer → DetailRouter
    { from: 'frontier',to: 'fc',       fa: 'right',  ta: 'left' },
    { from: 'fc',      to: 'dr',       fa: 'right',  ta: 'left' },
    // DomainGuard → FrontierConsumer
    { from: 'guard',   to: 'fc',       fa: 'bottom', ta: 'top', dashed: true },
    // DetailRouter / Pipeline → Storage
    { from: 'pipe',    to: 'db_raw',   fa: 'bottom', ta: 'top', dashed: true },
    { from: 'pipe',    to: 'db_doc',   fa: 'bottom', ta: 'top' },
    { from: 'pipe',    to: 'db_task',  fa: 'bottom', ta: 'top', dashed: true },
    { from: 'pipe',    to: 'db_trace', fa: 'bottom', ta: 'top', dashed: true },
    { from: 'dr',      to: 'db_doc',   fa: 'bottom', ta: 'top' },
    { from: 'frontier',to: 'db_frt',   fa: 'bottom', ta: 'top', dashed: true },
    // Storage → Consumers
    { from: 'db_doc',  to: 'search',   fa: 'bottom', ta: 'top' },
    { from: 'db_doc',  to: 'notify',   fa: 'bottom', ta: 'top' },
    { from: 'db_task', to: 'monitor',  fa: 'bottom', ta: 'top' },
    { from: 'db_trace',to: 'monitor',  fa: 'bottom', ta: 'top', dashed: true },
  ];

  const edgePath = (e: ArchEdge): string => {
    const fa = e.fa ?? 'bottom';
    const ta = e.ta ?? 'top';
    const src = nodeMap[e.from];
    const tgt = nodeMap[e.to];

    if (fa === 'right' && ta === 'left') {
      return `M ${nrgt(e.from)} ${ncy(e.from)} L ${nlft(e.to)} ${ncy(e.to)}`;
    }
    if (fa === 'left' && ta === 'right') {
      return `M ${nlft(e.from)} ${ncy(e.from)} L ${nrgt(e.to)} ${ncy(e.to)}`;
    }

    const rawX2 = ncx(e.to);
    const x1 = Math.min(Math.max(rawX2, src.x + 6), src.x + src.w - 6);
    const y1 = fa === 'bottom' ? nbot(e.from) : ntop(e.from);
    const x2 = rawX2;
    const y2 = ta === 'top' ? ntop(e.to) : nbot(e.to);
    const mid = (y1 + y2) / 2;
    return `M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`;
  };

  const nodeFill = (v?: string) => {
    if (v === 'accent')  return theme.accent.primary;
    if (v === 'muted')   return theme.fill.tertiary;
    if (v === 'outline') return 'transparent';
    return theme.fill.secondary;
  };
  const nodeStroke = (v?: string) => {
    if (v === 'accent') return 'none';
    return theme.stroke.secondary;
  };
  const nodeText = (v?: string) =>
    v === 'accent' ? theme.text.onAccent : theme.text.primary;

  return (
    <Stack gap={16} style={{ padding: 24 }}>
      <H1>政策雷达 · 系统架构图（含全网关键词检索）</H1>
      <Row gap={12} wrap>
        {[
          '内部小团队', '定时 Cron 调度', 'DB 配置即真相',
          '关键词驱动主动搜索', '模板优先 + readability 兜底',
          'DomainGuard 白名单 + QPS', '新增源 = 一行 SQL'
        ].map(t => (
          <Text key={t} size="small" tone="secondary">{t}</Text>
        ))}
      </Row>
      <Divider />

      <div style={{ overflowX: 'auto' }}>
        <svg
          width={SVG_W}
          height={SVG_H}
          style={{ display: 'block', fontFamily: 'system-ui, -apple-system, sans-serif' }}
        >
          <defs>
            <marker id="arr" markerWidth={7} markerHeight={7} refX={6} refY={3.5} orient="auto">
              <path d="M0,1 L0,6 L6,3.5 z" fill={theme.stroke.secondary} />
            </marker>
          </defs>

          {/* Layer band backgrounds */}
          {bands.map((b, i) => (
            <g key={i}>
              <rect
                x={2} y={b.y} width={SVG_W - 4} height={b.h}
                rx={8}
                fill={theme.fill.tertiary}
                opacity={0.45}
              />
              <text
                x={SVG_W - 10} y={b.y + b.h / 2}
                dominantBaseline="middle" textAnchor="end"
                fontSize={10} fontWeight={600} letterSpacing={0.6}
                fill={theme.text.tertiary}
              >
                {b.title.toUpperCase()}
              </text>
            </g>
          ))}

          {/* Edges */}
          {edges.map((e, i) => (
            <path
              key={i}
              d={edgePath(e)}
              fill="none"
              stroke={theme.stroke.secondary}
              strokeWidth={1.5}
              strokeDasharray={e.dashed ? '4 3' : undefined}
              markerEnd="url(#arr)"
              opacity={0.6}
            />
          ))}

          {/* Nodes */}
          {nodes.map(n => (
            <g key={n.id}>
              <rect
                x={n.x} y={n.y} width={n.w} height={n.h}
                rx={6}
                fill={nodeFill(n.variant)}
                stroke={nodeStroke(n.variant)}
                strokeWidth={1}
              />
              <text
                x={n.x + n.w / 2}
                y={n.y + (n.sub ? n.h / 2 - 7 : n.h / 2)}
                textAnchor="middle" dominantBaseline="middle"
                fontSize={11}
                fontWeight={n.variant === 'accent' ? 600 : 400}
                fill={nodeText(n.variant)}
              >
                {n.label}
              </text>
              {n.sub && (
                <text
                  x={n.x + n.w / 2} y={n.y + n.h / 2 + 8}
                  textAnchor="middle" dominantBaseline="middle"
                  fontSize={9}
                  fill={n.variant === 'accent' ? theme.text.onAccent : theme.text.tertiary}
                >
                  {n.sub}
                </text>
              )}
            </g>
          ))}
        </svg>
      </div>

      <Divider />

      {/* Legend */}
      <Row gap={28} wrap align="center">
        <Text size="small" weight="semibold" tone="secondary">图例</Text>
        <Row gap={8} align="center">
          <svg width={32} height={12}>
            <line x1={0} y1={6} x2={32} y2={6} stroke={theme.stroke.secondary} strokeWidth={1.5} />
          </svg>
          <Text size="small" tone="secondary">主数据流</Text>
        </Row>
        <Row gap={8} align="center">
          <svg width={32} height={12}>
            <line x1={0} y1={6} x2={32} y2={6} stroke={theme.stroke.secondary} strokeWidth={1.5} strokeDasharray="4 3" />
          </svg>
          <Text size="small" tone="secondary">辅助写入 / 调用</Text>
        </Row>
        <Row gap={8} align="center">
          <div style={{ width: 14, height: 10, background: theme.accent.primary, borderRadius: 3 }} />
          <Text size="small" tone="secondary">核心节点</Text>
        </Row>
        <Row gap={8} align="center">
          <div style={{ width: 14, height: 10, background: theme.fill.tertiary, borderRadius: 3, border: `1px solid ${theme.stroke.secondary}` }} />
          <Text size="small" tone="secondary">存储 / 辅助节点</Text>
        </Row>
        <Row gap={8} align="center">
          <div style={{ width: 14, height: 10, background: 'transparent', borderRadius: 3, border: `1px solid ${theme.stroke.secondary}` }} />
          <Text size="small" tone="secondary">外部脚本来源</Text>
        </Row>
      </Row>

      <Divider />
      <Stack gap={6}>
        <Text size="small" weight="semibold" tone="secondary">新增检索源成本</Text>
        <Row gap={24} wrap>
          <Text size="small" tone="secondary">无反爬政府站 → 写 1 个 SearchProvider 子类（30~80 行）+ 1 行 SQL</Text>
          <Text size="small" tone="secondary">强反爬 / JS 渲染 → 1 个 Python 脚本 + 1 行 SQL（复用 PyScriptRunner）</Text>
        </Row>
      </Stack>
    </Stack>
  );
}
