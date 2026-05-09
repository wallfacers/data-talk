import { Responsive, WidthProvider } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'

import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { ChartWidget } from './widgets/chart-widget'
import { MarkdownWidget } from './widgets/markdown-widget'
import type { Widget } from './schema'

const ResponsiveGridLayout = WidthProvider(Responsive)

const BREAKPOINTS = { lg: 1024, md: 768, sm: 0 }
const COLS = { lg: 12, md: 6, sm: 1 }

interface DashboardCanvasProps {
  tabId: string
  mode: 'viewer' | 'editor'
}

function widgetKey(w: Widget): string {
  return `widget-${w.id}`
}

function renderWidget(w: Widget): React.ReactNode {
  switch (w.type) {
    case 'chart': {
      const opts = w.options as { echartsOption?: Record<string, unknown>; title?: string }
      return (
        <ChartWidget
          widgetId={w.id}
          title={opts.title}
          option={opts.echartsOption ?? {}}
        />
      )
    }
    case 'markdown': {
      const opts = w.options as { text: string; title?: string }
      return (
        <MarkdownWidget
          widgetId={w.id}
          title={opts.title}
          text={opts.text ?? ''}
        />
      )
    }
    default:
      return (
        <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
          {w.type} widget
        </div>
      )
  }
}

export function DashboardCanvas({ tabId, mode }: DashboardCanvasProps) {
  const tab = useDashboardTabsStore((s) => s.tabs.get(tabId))

  if (!tab) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        No dashboard loaded
      </div>
    )
  }

  const { dashboard } = tab
  const isStatic = mode === 'viewer'

  const layouts = {
    lg: dashboard.widgets.map((w) => ({
      i: widgetKey(w),
      x: w.position.x,
      y: w.position.y,
      w: w.position.w,
      h: w.position.h,
      static: isStatic,
    })),
    md: dashboard.widgets.map((w) => ({
      i: widgetKey(w),
      x: w.position.x,
      y: w.position.y,
      w: Math.min(w.position.w, 6),
      h: w.position.h,
      static: isStatic,
    })),
    sm: dashboard.widgets.map((w) => ({
      i: widgetKey(w),
      x: 0,
      y: w.position.y,
      w: 1,
      h: w.position.h,
      static: isStatic,
    })),
  }

  return (
    <div data-testid="dashboard-canvas" className="h-full">
      <ResponsiveGridLayout
        className="dashboard-canvas"
        layouts={layouts}
        breakpoints={BREAKPOINTS}
        cols={COLS}
        rowHeight={dashboard.layout.rowHeight}
        containerPadding={[0, 0]}
        margin={[dashboard.layout.gap, dashboard.layout.gap]}
        isDraggable={!isStatic}
        isResizable={!isStatic}
        draggableCancel="[data-component='dashboard-widget-shell'] button, [data-component='dashboard-widget-shell'] input"
      >
        {dashboard.widgets.map((w) => (
          <div key={widgetKey(w)} style={{ overflow: 'hidden' }}>
            {renderWidget(w)}
          </div>
        ))}
      </ResponsiveGridLayout>
    </div>
  )
}
