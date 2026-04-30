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
