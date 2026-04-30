import { useEffect, useMemo, useState } from 'react'
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

function readThemeName(root: HTMLElement | null = globalThis.document?.documentElement ?? null) {
  return root?.classList.contains('dark') ? CHART_THEME_DARK : CHART_THEME_LIGHT
}

export function ChartRenderer({ option, height = DEFAULT_HEIGHT }: ChartRendererProps) {
  const [themeName, setThemeName] = useState(() => readThemeName())

  ensureChartThemesRegistered()

  const fixedOption = useMemo(
    () => injectOptionFix(option) as EChartsCoreOption,
    [option],
  )

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

  // The ECharts wrapper MUST keep `width: 100%` here. Previously we measured
  // the container with a `ResizeObserver` and fed the px width back as
  // `style={size}`, but the wrapper is a `block` element and its px width
  // lagged the parent's `width: 100%` by one frame on every resize. A pie
  // chart, whose centre is anchored at the canvas mid-point, immediately
  // reveals that one-frame offset: the canvas sits left- or right-of-centre
  // inside the parent and gets clipped by the parent's `overflow-hidden`,
  // so the pie visually drifts off-centre. echarts-for-react ships with
  // `autoResize: true` (`size_sensor`) — letting both the wrapper div and
  // the echarts canvas track the parent at `100%` keeps the pie centred
  // through every split-pane resize.
  return (
    <div
      className="w-full min-w-0 max-w-full overflow-hidden"
      style={{ height }}
    >
      <ReactECharts
        key={themeName}
        notMerge={true}
        option={fixedOption}
        opts={{ renderer: 'canvas' }}
        style={{ width: '100%', height }}
        theme={themeName}
      />
    </div>
  )
}
