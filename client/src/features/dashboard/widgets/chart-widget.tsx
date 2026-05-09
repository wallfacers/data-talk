import { WidgetShell } from './widget-shell'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'
import { useI18n } from '@/i18n/use-i18n'

interface ChartWidgetProps {
  widgetId: string
  title?: string
  option: Record<string, unknown>
  height?: number
  loading?: boolean
  error?: string | null
}

export function ChartWidget({ title, option, height = 300, loading, error }: ChartWidgetProps) {
  const { t } = useI18n()
  return (
    <WidgetShell title={title}>
      {loading && (
        <div data-testid="chart-skeleton" className="flex items-center justify-center h-full">
          <div className="animate-pulse text-sm text-[var(--dt-muted-foreground)]">{t('chart.loading')}</div>
        </div>
      )}
      {error && !loading && (
        <div data-testid="chart-error" className="flex items-center justify-center h-full text-sm text-[var(--dt-danger)]">
          {error}
        </div>
      )}
      {!loading && !error && (
        <div data-testid="chart-canvas-host">
          <ChartRenderer option={option} height={height} />
        </div>
      )}
    </WidgetShell>
  )
}
