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

type ChartRendererProps = {
  option: Record<string, unknown>
  height?: number
}

type ChartSize = {
  width: number | string
  height: number
}

function readThemeName(root: HTMLElement | null = globalThis.document?.documentElement ?? null) {
  return root?.classList.contains('dark') ? CHART_THEME_DARK : CHART_THEME_LIGHT
}

function measureSize(container: HTMLDivElement | null, fallbackHeight: number): ChartSize {
  const width = container?.clientWidth ?? 0
  const height = container?.clientHeight ?? fallbackHeight

  return {
    width: width > 0 ? width : '100%',
    height: height > 0 ? height : fallbackHeight,
  }
}

export function ChartRenderer({ option, height = DEFAULT_HEIGHT }: ChartRendererProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [themeName, setThemeName] = useState(() => readThemeName())
  const [size, setSize] = useState<ChartSize>(() => ({
    width: '100%',
    height,
  }))

  ensureChartThemesRegistered()

  const fixedOption = useMemo(
    () => injectOptionFix(option) as EChartsCoreOption,
    [option],
  )

  useLayoutEffect(() => {
    setSize(measureSize(containerRef.current, height))
  }, [height])

  useEffect(() => {
    const container = containerRef.current
    if (!container || typeof ResizeObserver === 'undefined') {
      return
    }

    const observer = new ResizeObserver(([entry]) => {
      const width = entry?.contentRect.width ?? container.clientWidth
      const nextHeight = entry?.contentRect.height ?? container.clientHeight ?? height

      setSize((current) => {
        const resolvedWidth = width > 0 ? width : current.width
        const resolvedHeight = nextHeight > 0 ? nextHeight : height

        if (current.width === resolvedWidth && current.height === resolvedHeight) {
          return current
        }

        return {
          width: resolvedWidth,
          height: resolvedHeight,
        }
      })
    })

    observer.observe(container)
    return () => observer.disconnect()
  }, [height])

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
    <div
      ref={containerRef}
      className="w-full min-w-0 max-w-full overflow-hidden"
      style={{ width: '100%', minWidth: 0, maxWidth: '100%', height }}
    >
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
