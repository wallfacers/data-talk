import * as echarts from 'echarts/core'

export type ChartTokens = {
  focus: string
  compare: string
  grid: string
  bg: string
  textStrong: string
  textMuted: string
}

export type ChartTheme = Record<string, unknown>

const FALLBACK_TOKENS: ChartTokens = {
  focus: '#1D4ED8',
  compare: '#F59E0B',
  grid: '#E2E8F0',
  bg: '#FFFFFF',
  textStrong: '#1E293B',
  textMuted: '#475569',
}

const PALETTE_TAIL = ['#64748B', '#0EA5E9', '#22C55E', '#EF4444']

export const CHART_THEME_LIGHT = 'datatalk-light'
export const CHART_THEME_DARK = 'datatalk-dark'

function readCssVar(style: CSSStyleDeclaration, name: string, fallback: string) {
  const value = style.getPropertyValue(name).trim()
  return value || fallback
}

export function readChartTokens(
  root: HTMLElement | null = globalThis.document?.documentElement ?? null,
): ChartTokens {
  if (!root) {
    return { ...FALLBACK_TOKENS }
  }

  const style = getComputedStyle(root)
  return {
    focus: readCssVar(style, '--dt-chart-focus', FALLBACK_TOKENS.focus),
    compare: readCssVar(style, '--dt-chart-compare', FALLBACK_TOKENS.compare),
    grid: readCssVar(style, '--dt-chart-grid', FALLBACK_TOKENS.grid),
    bg: readCssVar(style, '--dt-bg-panel', FALLBACK_TOKENS.bg),
    textStrong: readCssVar(style, '--dt-text-strong', FALLBACK_TOKENS.textStrong),
    textMuted: readCssVar(style, '--dt-text-muted', FALLBACK_TOKENS.textMuted),
  }
}

export function buildChartTheme(tokens: ChartTokens = readChartTokens()): ChartTheme {
  return {
    color: [tokens.focus, tokens.compare, ...PALETTE_TAIL],
    backgroundColor: tokens.bg,
    textStyle: {
      color: tokens.textStrong,
      fontFamily: '"Source Sans 3", "Noto Sans SC", "PingFang SC", sans-serif',
      fontSize: 13,
    },
    title: {
      backgroundColor: 'transparent',
      textStyle: {
        color: tokens.textStrong,
        fontSize: 14,
        fontWeight: 600,
      },
    },
    legend: {
      textStyle: {
        color: tokens.textMuted,
      },
    },
    tooltip: {
      backgroundColor: tokens.bg,
      borderColor: tokens.grid,
      textStyle: {
        color: tokens.textStrong,
        fontSize: 12,
      },
    },
    categoryAxis: {
      axisLine: {
        lineStyle: {
          color: tokens.grid,
        },
      },
      axisTick: {
        lineStyle: {
          color: tokens.grid,
        },
      },
      axisLabel: {
        color: tokens.textMuted,
        fontFamily: '"JetBrains Mono", "SFMono-Regular", "Cascadia Mono", monospace',
      },
      splitLine: {
        lineStyle: {
          color: tokens.grid,
          type: 'dashed',
        },
      },
    },
    valueAxis: {
      axisLine: {
        lineStyle: {
          color: tokens.grid,
        },
      },
      axisTick: {
        lineStyle: {
          color: tokens.grid,
        },
      },
      axisLabel: {
        color: tokens.textMuted,
        fontFamily: '"JetBrains Mono", "SFMono-Regular", "Cascadia Mono", monospace',
      },
      splitLine: {
        lineStyle: {
          color: tokens.grid,
          type: 'dashed',
        },
      },
    },
  }
}

let registered = false

export function registerChartThemes() {
  const theme = buildChartTheme(readChartTokens())
  echarts.registerTheme(CHART_THEME_LIGHT, theme)
  echarts.registerTheme(CHART_THEME_DARK, theme)
  registered = true
}

export function ensureChartThemesRegistered() {
  if (!registered) {
    registerChartThemes()
  }
}

export function refreshChartThemesForCurrentMode() {
  registerChartThemes()
}

function withContainLabel(grid: unknown) {
  if (Array.isArray(grid)) {
    return grid.map((entry) => ({ containLabel: true, ...(entry as Record<string, unknown>) }))
  }

  if (grid && typeof grid === 'object') {
    return { containLabel: true, ...(grid as Record<string, unknown>) }
  }

  return { containLabel: true }
}

function withTransparentTitle(title: unknown) {
  if (Array.isArray(title)) {
    return title.map((entry) => ({
      ...(entry as Record<string, unknown>),
      backgroundColor: 'transparent',
    }))
  }

  if (title && typeof title === 'object') {
    return {
      ...(title as Record<string, unknown>),
      backgroundColor: 'transparent',
    }
  }

  return title
}

// AI-generated pie options frequently set `series.center` to a non-centred
// pair (e.g. ['40%', '50%']) or pin a vertical legend that pushes the pie
// off-axis. The product contract is that chart artifacts always read as a
// horizontally centred figure, so we hard-pin pie centres back to
// ['50%', '50%'] before handing the option to ECharts. Other series types
// (bar/line/scatter/...) lay out in a `grid`, not via `center`, so we only
// touch `pie`.
function withCenteredPie(series: unknown): unknown {
  const ensure = (entry: unknown): unknown => {
    if (!entry || typeof entry !== 'object') return entry
    const record = entry as Record<string, unknown>
    if (record.type !== 'pie') return entry
    return { ...record, center: ['50%', '50%'] }
  }

  if (Array.isArray(series)) {
    return series.map(ensure)
  }
  return ensure(series)
}

// Pie-with-vertical-legend frequently arrives with the pie centred but the
// legend stacked along one edge, which still leaves the figure visually
// off-axis. Rewriting any pie's legend to a centred horizontal strip at the
// bottom keeps the figure's visual centre aligned with the container's
// horizontal centre.
function hasPieSeries(series: unknown): boolean {
  const isPie = (entry: unknown) =>
    !!entry && typeof entry === 'object' && (entry as Record<string, unknown>).type === 'pie'
  if (Array.isArray(series)) return series.some(isPie)
  return isPie(series)
}

function withHorizontalLegend(legend: unknown): unknown {
  const flatten = (entry: unknown): unknown => {
    if (!entry || typeof entry !== 'object') return entry
    const record = entry as Record<string, unknown>
    return {
      ...record,
      orient: 'horizontal',
      left: 'center',
      top: undefined,
      right: undefined,
      bottom: 8,
    }
  }
  if (Array.isArray(legend)) return legend.map(flatten)
  return flatten(legend)
}

export function injectOptionFix(option: Record<string, unknown>): Record<string, unknown> {
  const fixed: Record<string, unknown> = {
    ...option,
    grid: withContainLabel(option.grid),
  }

  if (option.title !== undefined) {
    fixed.title = withTransparentTitle(option.title)
  }

  if (option.series !== undefined && hasPieSeries(option.series)) {
    fixed.series = withCenteredPie(option.series)
    if (option.legend !== undefined) {
      fixed.legend = withHorizontalLegend(option.legend)
    }
  }

  return fixed
}
