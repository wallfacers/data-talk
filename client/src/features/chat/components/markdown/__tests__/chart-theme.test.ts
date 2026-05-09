import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const chartThemeModuleId = '../chart-theme'
const loadChartTheme = () => import(chartThemeModuleId)

function stubCssVars(map: Record<string, string>) {
  const root = document.documentElement
  for (const [key, value] of Object.entries(map)) {
    root.style.setProperty(key, value)
  }
}

describe('chart-theme', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('style')
  })

  afterEach(() => {
    vi.resetModules()
    vi.doUnmock('echarts/core')
  })

  it('reads --dt-chart-* and --dt-bg-* tokens from the document root', async () => {
    stubCssVars({
      '--dt-chart-focus': '#1D4ED8',
      '--dt-chart-compare': '#F59E0B',
      '--dt-chart-grid': '#E2E8F0',
      '--dt-bg-panel': '#FFFFFF',
      '--dt-text-strong': '#1E293B',
      '--dt-text-muted': '#475569',
    })

    const { readChartTokens } = await loadChartTheme()
    const tokens = readChartTokens()

    expect(tokens.focus).toBe('#1D4ED8')
    expect(tokens.compare).toBe('#F59E0B')
    expect(tokens.grid).toBe('#E2E8F0')
  })

  it('falls back when chart tokens are missing', async () => {
    const { readChartTokens } = await loadChartTheme()
    const tokens = readChartTokens()

    expect(tokens.focus).toBe('#1D4ED8')
    expect(tokens.compare).toBe('#F59E0B')
    expect(tokens.grid).toBe('#E2E8F0')
    expect(tokens.bg).toBe('#FFFFFF')
  })

  it('buildChartTheme produces a color[] starting with focus then compare', async () => {
    stubCssVars({
      '--dt-chart-focus': '#60A5FA',
      '--dt-chart-compare': '#FBBF24',
      '--dt-chart-grid': 'rgba(255,255,255,0.08)',
      '--dt-bg-panel': '#0F172A',
      '--dt-text-strong': '#F1F5F9',
      '--dt-text-muted': '#94A3B8',
    })

    const { buildChartTheme } = await loadChartTheme()
    const theme = buildChartTheme()

    expect(theme.color?.[0]).toBe('#60A5FA')
    expect(theme.color?.[1]).toBe('#FBBF24')
    expect(theme.backgroundColor).toBe('#0F172A')
  })

  it('injectOptionFix pins pie series center to [50%, 50%] regardless of source value', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      series: [
        { type: 'pie', center: ['40%', '50%'], radius: '60%', data: [] },
        { type: 'bar', data: [1, 2, 3] },
      ],
    })

    const series = fixed.series as Array<Record<string, unknown>>
    expect(series[0].center).toEqual(['50%', '50%'])
    // Non-pie series must be untouched (bar lays out via grid, not center).
    expect(series[1]).toEqual({ type: 'bar', data: [1, 2, 3] })
  })

  it('injectOptionFix flattens a pie chart legend to a horizontal bottom strip', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      series: [{ type: 'pie', data: [] }],
      legend: { orient: 'vertical', left: 'left', top: 'middle' },
    })

    const legend = fixed.legend as Record<string, unknown>
    expect(legend.orient).toBe('horizontal')
    expect(legend.left).toBe('center')
    expect(legend.bottom).toBe(8)
    expect(legend.top).toBeUndefined()
  })

  it('injectOptionFix leaves non-pie options untouched apart from the existing grid/title fixes', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      series: [{ type: 'line', data: [1, 2, 3] }],
      legend: { orient: 'vertical', left: 'left' },
    })

    expect(fixed.series).toEqual([{ type: 'line', data: [1, 2, 3] }])
    // Legend should not be flattened when no pie series is present.
    expect((fixed.legend as Record<string, unknown>).orient).toBe('vertical')
  })

  // Cartesian axis names default to nameLocation:'end', which paints the name
  // outside the grid edge. In a narrow chat-bubble the parent's overflow:hidden
  // (chart-block.tsx) clips that overhang, so a long xAxis name like '月份' or
  // 'Order Count' visibly truncates at the right edge of the canvas. We
  // normalise the name to nameLocation:'middle' so echarts always paints it
  // inside the grid — the same product-contract approach as withContainLabel
  // and withCenteredPie.
  it('injectOptionFix moves a non-empty xAxis name to the middle so it cannot be clipped at the right edge', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      xAxis: { type: 'category', name: '月份', data: ['2026-01', '2026-02'] },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: [1, 2] }],
    })

    const xAxis = fixed.xAxis as Record<string, unknown>
    expect(xAxis.nameLocation).toBe('middle')
    expect(typeof xAxis.nameGap).toBe('number')
    expect(xAxis.nameGap).toBeGreaterThanOrEqual(24)
    // Non-name fields must be preserved verbatim.
    expect(xAxis.type).toBe('category')
    expect(xAxis.data).toEqual(['2026-01', '2026-02'])
    expect(xAxis.name).toBe('月份')
  })

  it('injectOptionFix moves a non-empty yAxis name to the middle and rotates it so vertical text stays inside the canvas', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      xAxis: { type: 'category' },
      yAxis: { type: 'value', name: '订单量', min: 0 },
      series: [{ type: 'bar', data: [1, 2, 3] }],
    })

    const yAxis = fixed.yAxis as Record<string, unknown>
    expect(yAxis.nameLocation).toBe('middle')
    expect(yAxis.nameRotate).toBe(90)
    expect(typeof yAxis.nameGap).toBe('number')
    expect(yAxis.nameGap).toBeGreaterThanOrEqual(30)
    expect(yAxis.min).toBe(0)
    expect(yAxis.name).toBe('订单量')
  })

  it('injectOptionFix leaves an axis with no name (or empty name) untouched', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      xAxis: { type: 'category', data: ['a', 'b'] },
      yAxis: { type: 'value', name: '' },
      series: [{ type: 'line', data: [1, 2] }],
    })

    expect(fixed.xAxis).toEqual({ type: 'category', data: ['a', 'b'] })
    // Empty-name axis must not gain nameLocation/nameGap/nameRotate.
    expect(fixed.yAxis).toEqual({ type: 'value', name: '' })
  })

  // ECharts grid.containLabel:true only reserves space for axisLabel (tick text),
  // NOT for axis.name. After we move the name to nameLocation:'middle', the
  // default grid.bottom (~60px at height=320 with containLabel) is not enough to
  // hold axisLabel + nameGap + name text → the name gets clipped at the canvas
  // bottom. AI-generated grid values (e.g. "3%") are also too tight for the
  // axis-name band, so we always override grid.bottom/left when axis names are
  // present. v5: removed the "only when undefined" guard after discovering AI
  // explicitly sets small grid values that break axis-name rendering.
  it('injectOptionFix widens grid.bottom enough that axis.name is fully drawn below the axisLabel band, with comfortable margin against the canvas bottom', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      xAxis: { type: 'category', name: '月份', data: [] },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: [] }],
    })

    const grid = fixed.grid as Record<string, unknown>
    expect(grid.containLabel).toBe(true)
    expect(typeof grid.bottom).toBe('number')
    expect(grid.bottom).toBeGreaterThanOrEqual(88)
  })

  it('injectOptionFix widens grid.left enough that a rotated yAxis.name fits with comfortable margin from the canvas left edge', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      xAxis: { type: 'category' },
      yAxis: { type: 'value', name: '订单量' },
      series: [{ type: 'line', data: [] }],
    })

    const grid = fixed.grid as Record<string, unknown>
    expect(grid.containLabel).toBe(true)
    expect(typeof grid.left).toBe('number')
    expect(grid.left).toBeGreaterThanOrEqual(88)
  })

  it('injectOptionFix overrides AI-generated small grid values (e.g. "3%") when axis names need space', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      grid: { bottom: '3%', left: '3%', right: '4%' },
      xAxis: { type: 'category', name: '月份' },
      yAxis: { type: 'value', name: '订单量' },
      series: [{ type: 'line', data: [] }],
    })

    const grid = fixed.grid as Record<string, unknown>
    // AI "3%" is far too small for axis-name band → must be overridden
    expect(typeof grid.bottom).toBe('number')
    expect(grid.bottom).toBeGreaterThanOrEqual(88)
    expect(typeof grid.left).toBe('number')
    expect(grid.left).toBeGreaterThanOrEqual(88)
    // Non-competing axis dimension must be preserved
    expect(grid.right).toBe('4%')
  })

  it('injectOptionFix does not widen grid when no axis name is present', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      xAxis: { type: 'category' },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: [] }],
    })

    const grid = fixed.grid as Record<string, unknown>
    expect(grid.containLabel).toBe(true)
    // No name → echarts default padding remains untouched (no numeric bottom/left injected by us).
    expect(grid.bottom).toBeUndefined()
    expect(grid.left).toBeUndefined()
  })

  it('injectOptionFix normalises every named entry when xAxis is an array (dual-axis charts)', async () => {
    const { injectOptionFix } = await loadChartTheme()

    const fixed = injectOptionFix({
      xAxis: [
        { type: 'category', name: '月份', data: [] },
        { type: 'category' },
      ],
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: [] }],
    })

    const xAxis = fixed.xAxis as Array<Record<string, unknown>>
    expect(xAxis[0].nameLocation).toBe('middle')
    expect(xAxis[0].name).toBe('月份')
    // Second axis has no name, must stay as-is.
    expect(xAxis[1]).toEqual({ type: 'category' })
  })

  it('registerChartThemes registers both datatalk-light and datatalk-dark', async () => {
    const registerTheme = vi.fn()
    vi.doMock('echarts/core', async () => {
      const actual = await vi.importActual<typeof import('echarts/core')>('echarts/core')
      return {
        ...actual,
        registerTheme,
      }
    })

    const { registerChartThemes } = await loadChartTheme()

    registerChartThemes()

    const calls = registerTheme.mock.calls.map((call) => call[0])
    expect(calls).toContain('datatalk-light')
    expect(calls).toContain('datatalk-dark')
  })
})
