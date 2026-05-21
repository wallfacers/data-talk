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

// PALETTE_TAIL provides series colors 3+ when a chart has more than {focus, compare}.
// Slot 0 is a neutral (warm gray per new light-theme spine, dark-theme renders fine too);
// slots 1-3 are saturated semantic colors (info / success / danger) that read the same
// against either warm or cool background.
const PALETTE_TAIL = ['#858481', '#0EA5E9', '#22C55E', '#EF4444']

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

// grid.bottom / grid.left must be large enough to fit axisLabel band +
// nameGap + name text inside the grid (containLabel:true includes labels but
// NOT axis.name).  Visual margins are now handled by CSS padding on the
// container (chart-block.tsx p-3), so these values only need to prevent
// axis-name clipping at the canvas edge — no visual-margin responsibility.
const GRID_BOTTOM_FOR_X_NAME = 32
const GRID_LEFT_FOR_Y_NAME = 24
const GRID_RIGHT_FOR_Y_NAME = 24

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
    // AI-generated grid values are almost always too tight for the axis-name
    // band, so we always override bottom/left. grid.right aligns with the
    // left-side visual weight so the plot area looks centered.
    if (hasXName) {
      result.bottom = GRID_BOTTOM_FOR_X_NAME
    }
    if (hasYName) {
      result.left = GRID_LEFT_FOR_Y_NAME
      result.right = GRID_RIGHT_FOR_Y_NAME
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
//
// The rotated yAxis name sits in the left gutter (grid.left = GRID_LEFT_FOR_Y_NAME),
// to the LEFT of the numeric tick-label band. containLabel reserves the label
// band but NOT the name, and the band width grows with the data ('800 万' vs
// '1,200 万元'), so a static nameGap cannot reliably clear it. Y_AXIS_NAME_GAP
// is only the pre-layout fallback; adjustYAxisNameGapToClearLabels() measures
// the real band after render and pushes nameGap just past it.
const X_AXIS_NAME_GAP = 16
const Y_AXIS_NAME_GAP = 56

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

// Clearance between the right edge of the tick-label band and the rotated
// yAxis name, so the vertical title sits cleanly in the left gutter.
const Y_NAME_CLEARANCE = 12

type GridRect = { x: number; y: number; width: number; height: number }
type MinimalChart = {
  getOption: () => Record<string, unknown>
  setOption: (option: Record<string, unknown>) => void
  getModel?: () =>
    | {
        getComponent?: (
          name: string,
          idx: number,
        ) => { coordinateSystem?: { getRect?: () => GridRect | undefined } } | undefined
      }
    | undefined
}

function isLeftNamedYAxis(axis: unknown): boolean {
  if (!axis || typeof axis !== 'object') return false
  const record = axis as Record<string, unknown>
  return (
    typeof record.name === 'string' &&
    record.name.length > 0 &&
    record.position !== 'right'
  )
}

// Pin the rotated yAxis name to the far-left gutter, just left of the numeric
// tick labels, regardless of how wide those labels are. ECharts' containLabel
// reserves the label band but not the axis name, and the band widens with the
// data, so we measure the laid-out cartesian rect and set nameGap = band + gap.
// Idempotent: a no-op once nameGap already matches, so it is safe to call from
// the 'finished' event (which our own setOption re-triggers). Degrades to the
// static Y_AXIS_NAME_GAP if the layout APIs are unavailable.
export function adjustYAxisNameGapToClearLabels(chart: MinimalChart): void {
  let rect: GridRect | undefined
  try {
    rect = chart.getModel?.()?.getComponent?.('grid', 0)?.coordinateSystem?.getRect?.()
  } catch {
    return
  }
  if (!rect) return

  const rawY = chart.getOption().yAxis
  const yAxes = Array.isArray(rawY) ? rawY : rawY ? [rawY] : []
  if (!yAxes.some(isLeftNamedYAxis)) return

  const labelBand = Math.max(0, rect.x - GRID_LEFT_FOR_Y_NAME)
  const nameGap = Math.round(labelBand + Y_NAME_CLEARANCE)

  const current = (yAxes.find(isLeftNamedYAxis) as Record<string, unknown>).nameGap
  if (current === nameGap) return

  const nextY = yAxes.map((axis) =>
    isLeftNamedYAxis(axis) ? { ...(axis as Record<string, unknown>), nameGap } : axis,
  )
  chart.setOption({ yAxis: nextY })
}
