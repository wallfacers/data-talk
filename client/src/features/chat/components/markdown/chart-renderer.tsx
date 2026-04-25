import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import ReactECharts from 'echarts-for-react'
import * as echarts from 'echarts/core'
import type { EChartsCoreOption } from 'echarts/core'
import {
  BarChart,
  CandlestickChart,
  LineChart,
  PieChart,
  RadarChart,
  ScatterChart,
} from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  MarkPointComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
  VisualMapComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  CHART_THEME_DARK,
  CHART_THEME_LIGHT,
  ensureChartThemesRegistered,
  injectOptionFix,
  refreshChartThemesForCurrentMode,
} from './chart-theme'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  ScatterChart,
  RadarChart,
  CandlestickChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
  VisualMapComponent,
  ToolboxComponent,
  MarkLineComponent,
  MarkPointComponent,
  MarkAreaComponent,
  CanvasRenderer,
])

const DEFAULT_HEIGHT = 320
const DEFAULT_ASPECT_RATIO = 16 / 9
const COMPACT_ASPECT_RATIO = 4 / 3
const COMPACT_ASPECT_THRESHOLD = 700

type AspectBadgeSize = {
  ratio: number
  compact: boolean
}

type ChartRendererProps = {
  option: Record<string, unknown>
  height?: number
  aspectRatio?: number
  compactAspectRatio?: number
  compactWidthThreshold?: number
  showAspectBadge?: boolean
}

type ChartSize = {
  width: number | string
  height: number
}

type MeasureBox = {
  clientWidth: number
  clientHeight: number
}

function formatRatioLabel(ratio: number): string {
  const closeTo = (target: number) => Math.abs(ratio - target) < 0.03
  if (closeTo(16 / 9)) return '16:9'
  if (closeTo(4 / 3)) return '4:3'
  if (closeTo(1)) return '1:1'
  return `${ratio.toFixed(2)}:1`
}

function readThemeName(root: HTMLElement | null = globalThis.document?.documentElement ?? null) {
  return root?.classList.contains('dark') ? CHART_THEME_DARK : CHART_THEME_LIGHT
}

function preferredRatio(width: number, ratio: number, compactRatio: number, threshold: number): AspectBadgeSize {
  if (width > 0 && width < threshold) {
    return { ratio: compactRatio, compact: true }
  }
  return { ratio, compact: false }
}

function computeSize(
  container: MeasureBox | null,
  fallbackHeight: number,
  ratio: number,
  compactRatio: number,
  compactWidthThreshold: number,
): { size: ChartSize; ratio: number; compact: boolean } {
  const containerWidth = container?.clientWidth ?? 0
  const containerHeight = container?.clientHeight ?? 0
  const target = preferredRatio(containerWidth, ratio, compactRatio, compactWidthThreshold)

  if (containerWidth <= 0) {
    return {
      size: {
        width: '100%',
        height: fallbackHeight,
      },
      ratio: target.ratio,
      compact: target.compact,
    }
  }

  const byWidth = containerWidth / target.ratio
  if (containerHeight > 0 && byWidth > containerHeight) {
    return {
      size: {
        width: Math.round(containerHeight * target.ratio),
        height: Math.max(1, Math.round(containerHeight)),
      },
      ratio: target.ratio,
      compact: target.compact,
    }
  }

  return {
    size: {
      width: containerWidth,
      height: Math.max(1, Math.round(byWidth)),
    },
    ratio: target.ratio,
    compact: target.compact,
  }
}

function measureSize(
  container: HTMLDivElement | null,
  fallbackHeight: number,
  ratio: number,
  compactRatio: number,
  compactWidthThreshold: number,
): ChartSize {
  return computeSize(container, fallbackHeight, ratio, compactRatio, compactWidthThreshold).size
}

export function ChartRenderer({
  option,
  height = DEFAULT_HEIGHT,
  aspectRatio = DEFAULT_ASPECT_RATIO,
  compactAspectRatio = COMPACT_ASPECT_RATIO,
  compactWidthThreshold = COMPACT_ASPECT_THRESHOLD,
  showAspectBadge = false,
}: ChartRendererProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [themeName, setThemeName] = useState(() => readThemeName())
  const [ratioState, setRatioState] = useState({
    ratio: aspectRatio,
    compact: false,
  })
  const [size, setSize] = useState<ChartSize>(() => ({
    width: '100%',
    height,
  }))

  ensureChartThemesRegistered()

  const fixedOption = useMemo(
    () => injectOptionFix(option) as EChartsCoreOption,
    [option],
  )
  const ratioLabel = useMemo(() => formatRatioLabel(ratioState.ratio), [ratioState.ratio])
  const aspectBadge = showAspectBadge
    ? ratioLabel + (ratioState.compact ? ' · 适配比例' : '')
    : ''

  useLayoutEffect(() => {
    setSize(measureSize(containerRef.current, height, aspectRatio, compactAspectRatio, compactWidthThreshold))
    setRatioState(preferredRatio(containerRef.current?.clientWidth ?? 0, aspectRatio, compactAspectRatio, compactWidthThreshold))
  }, [aspectRatio, compactAspectRatio, compactWidthThreshold, height])

  useEffect(() => {
    const container = containerRef.current
    if (!container || typeof ResizeObserver === 'undefined') {
      return
    }

    const observer = new ResizeObserver(([entry]) => {
      const nextWidth = entry?.contentRect.width ?? container.clientWidth
      const nextHeight = entry?.contentRect.height ?? container.clientHeight
      const measured = computeSize(
        {
          clientWidth: nextWidth,
          clientHeight: nextHeight,
        },
        height,
        aspectRatio,
        compactAspectRatio,
        compactWidthThreshold,
      )

      setSize((current) => {
        if (current.width === measured.size.width && current.height === measured.size.height) {
          return current
        }
        return {
          width: measured.size.width,
          height: measured.size.height,
        }
      })

      setRatioState((state) => {
        const nextCompact = measured.compact
        if (state.compact === nextCompact && state.ratio === measured.ratio) {
          return state
        }
        return { compact: nextCompact, ratio: measured.ratio }
      })
    })

    observer.observe(container)
    return () => observer.disconnect()
  }, [aspectRatio, compactAspectRatio, compactWidthThreshold, height])

  useEffect(() => {
    const root = globalThis.document?.documentElement ?? null
    if (!root) {
      return
    }

    const syncTheme = () => {
      refreshChartThemesForCurrentMode()
      setThemeName(readThemeName(root))
    }

    syncTheme()

    const observer = new MutationObserver((records) => {
      if (!records.some((record) => record.attributeName === 'class')) {
        return
      }
      syncTheme()
    })

    observer.observe(root, {
      attributes: true,
      attributeFilter: ['class'],
    })

    return () => observer.disconnect()
  }, [])

  return (
    <div ref={containerRef} style={{ width: '100%' }} className="relative">
      {showAspectBadge && (
        <div
          className="pointer-events-none absolute right-2 top-2 rounded-md border border-[var(--dt-border-subtle)] bg-[var(--dt-bg-overlay)] px-2 py-0.5 text-[11px] leading-[16px] tracking-wide text-[var(--dt-text-soft)]"
          aria-label="chart aspect ratio"
        >
          {aspectBadge}
        </div>
      )}
      <ReactECharts
        key={themeName}
        notMerge={true}
        option={fixedOption}
        opts={{ renderer: 'canvas' }}
        style={size}
        theme={themeName}
      />
    </div>
  )
}
