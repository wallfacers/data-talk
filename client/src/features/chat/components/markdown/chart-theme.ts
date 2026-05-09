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

// ECharts grid.containLabel:true reserves space for axisLabel (tick text)
// only — it does NOT reserve space for axis.name. After we move axis names
// to nameLocation:'middle' (see withInsetAxisName below), the default grid
// padding is too tight to hold axisLabel + nameGap + name and the centred
// name gets clipped at the canvas edge. We therefore widen grid.bottom when
// xAxis has a name and grid.left when yAxis has a name, but only if the
// user/AI did not specify those dimensions explicitly.
//
// Sizing rationale at chart-renderer DEFAULT_HEIGHT=360 (raised in v4):
//   bottom ≥ axisLabel band(~22) + nameGap(28) + name fontHeight(~14) + safety(~32) = 96
//   left   ≥ axisLabel band(~40) + nameGap(36) + name fontHeight(~14) + safety(~6)  = 96
// v1 (no padding) → name clipped at right edge (nameLocation:'end').
// v2 (56 / 72)    → bottom too tight, lower half of name clipped at canvas bottom.
// v3 (80 / 88)    → still ~10% visible per user regression — echarts' actual
//                   axisLabel + nameGap layout consumed more than the textbook
//                   estimate.
// v4 raises both to 96 and widens the canvas (DEFAULT_HEIGHT 320→360) so the
// axis-name band gets real breathing room instead of being squeezed.
const GRID_BOTTOM_FOR_X_NAME = 96
const GRID_LEFT_FOR_Y_NAME = 96

function hasAxisWithName(axis: unknown): boolean {
  const named = (entry: unknown) =>
    !!entry &&
    typeof entry === 'object' &&
    typeof (entry as Record<string, unknown>).name === 'string' &&
    ((entry as Record<string, unknown>).name as string).length > 0
  if (Array.isArray(axis)) return axis.some(named)
  return named(axis)
}

function withContainLabel(grid: unknown, option: Record<string, unknown>) {
  const hasXName = hasAxisWithName(option.xAxis)
  const hasYName = hasAxisWithName(option.yAxis)

  const inflate = (entry: Record<string, unknown>): Record<string, unknown> => {
    const result: Record<string, unknown> = { containLabel: true, ...entry }
    // We set nameLocation:'middle' + nameGap in withInsetAxisName, which
    // REQUIRES sufficient grid padding. AI-generated options (e.g. "3%") are
    // almost always too tight for the axis-name band, so we always override.
    if (hasXName) {
      result.bottom = GRID_BOTTOM_FOR_X_NAME
    }
    if (hasYName) {
      result.left = GRID_LEFT_FOR_Y_NAME
    }
    return result
  }

  if (Array.isArray(grid)) {
    return grid.map((entry) => inflate(entry as Record<string, unknown>))
  }

  if (grid && typeof grid === 'object') {
    return inflate(grid as Record<string, unknown>)
  }

  return inflate({})
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

// Cartesian axis names (xAxis.name / yAxis.name) default to nameLocation:'end'
// in ECharts, painting the name just past the grid edge. In a chat-bubble
// chart the parent has overflow:hidden and a non-negotiable max-width (the
// bubble width is part of the product contract), so a long axis name like
// '月份' or 'Order Count' gets clipped at the right/top edge of the canvas.
// We move the name to nameLocation:'middle' so echarts always paints it
// inside the grid — same product-contract approach as withContainLabel and
// withCenteredPie. We only touch entries that have a non-empty `name`.
const X_AXIS_NAME_GAP = 28
const Y_AXIS_NAME_GAP = 36

function withInsetAxisName(axis: unknown, dim: 'x' | 'y'): unknown {
  const inset = (entry: unknown): unknown => {
    if (!entry || typeof entry !== 'object') return entry
    const record = entry as Record<string, unknown>
    if (typeof record.name !== 'string' || record.name.length === 0) return entry
    return {
      ...record,
      nameLocation: 'middle',
      nameGap: dim === 'x' ? X_AXIS_NAME_GAP : Y_AXIS_NAME_GAP,
      ...(dim === 'y' ? { nameRotate: 90 } : null),
    }
  }
  if (Array.isArray(axis)) return axis.map(inset)
  return inset(axis)
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
    grid: withContainLabel(option.grid, option),
  }

  if (option.title !== undefined) {
    fixed.title = withTransparentTitle(option.title)
  }

  if (option.xAxis !== undefined) {
    fixed.xAxis = withInsetAxisName(option.xAxis, 'x')
  }
  if (option.yAxis !== undefined) {
    fixed.yAxis = withInsetAxisName(option.yAxis, 'y')
  }

  if (option.series !== undefined && hasPieSeries(option.series)) {
    fixed.series = withCenteredPie(option.series)
    if (option.legend !== undefined) {
      fixed.legend = withHorizontalLegend(option.legend)
    }
  }

  return fixed
}
