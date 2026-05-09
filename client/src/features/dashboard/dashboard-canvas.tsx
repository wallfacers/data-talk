import { Responsive, useContainerWidth } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'

import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { ChartWidget } from './widgets/chart-widget'
import { MarkdownWidget } from './widgets/markdown-widget'
import type { Widget } from './schema'
import { useI18n } from '@/i18n/use-i18n'

import type { TranslationFn } from '@/i18n/provider'

const BREAKPOINTS = { lg: 1024, md: 768, sm: 0 }
const COLS = { lg: 12, md: 6, sm: 1 }

interface DashboardCanvasProps {
  tabId: string
  mode: 'viewer' | 'editor'
}

function widgetKey(w: Widget): string {
  return `widget-${w.id}`
}

function renderWidget(w: Widget, t: TranslationFn): React.ReactNode {
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
          {t('dashboard.widgetUnknown', { type: w.type })}
        </div>
      )
  }
}

export function DashboardCanvas({ tabId, mode }: DashboardCanvasProps) {
  const { t } = useI18n()
  const tab = useDashboardTabsStore((s) => s.tabs.get(tabId))
  const { width, containerRef, mounted } = useContainerWidth()

  if (!tab) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        {t('dashboard.noDashboardLoaded')}
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
    <div data-testid="dashboard-canvas" className="h-full" ref={containerRef}>
      {mounted && (
        <Responsive
          className="dashboard-canvas"
          width={width}
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
              {renderWidget(w, t)}
            </div>
          ))}
        </Responsive>
      )}
    </div>
  )
}
