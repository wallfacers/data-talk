import { useState, useMemo } from 'react'
import { LayoutDashboardIcon, ExternalLinkIcon, BarChart2Icon, FileTextIcon } from 'lucide-react'
import { dashboardSchema } from '@/features/dashboard/schema'
import type { Dashboard, Widget } from '@/features/dashboard/schema'
import { useDashboardTabsStore } from '@/features/dashboard/stores/dashboard-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { generateUuid } from '@/lib/uuid'
import { cn } from '@/lib/utils'
import { promoteDashboard as promoteDashboardApi } from '@/features/dashboard/services/dashboard-api'
import { useI18n } from '@/i18n/use-i18n'
import type { TranslationFn } from '@/i18n/provider'

type DashboardBlockState = 'streaming' | 'preview' | 'error'

interface DashboardBlockProps {
  json: string
  streaming: boolean
  messageId?: string
  partId?: string
}

function parseDashboard(json: string, t?: TranslationFn): { ok: true; dashboard: Dashboard } | { ok: false; error: string } {
  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch {
    return { ok: false, error: t ? t('dashboard.invalidJson') : 'Invalid JSON' }
  }
  // AI-generated dashboard IDs may contain hyphens (UUID format);
  // strip them to match the server schema ^dash_[a-zA-Z0-9_]{4,}$
  const obj = parsed as Record<string, unknown> | null
  if (obj && typeof obj === 'object' && 'id' in obj && typeof obj.id === 'string') {
    obj.id = obj.id.replace(/-/g, '')
  }
  const result = dashboardSchema.safeParse(parsed)
  if (result.success) {
    return { ok: true, dashboard: result.data }
  }
  return { ok: false, error: result.error.issues.map((i) => i.message).join('; ') }
}

async function promoteDashboard(dashboard: Dashboard) {
  const tabId = `dashboard_${generateUuid()}`
  useDashboardTabsStore.getState().hydrateTab(tabId, dashboard)
  useStageStore.getState().openTab({
    tabId,
    type: 'dashboard',
    title: dashboard.title,
    payload: dashboard,
    createdAt: Date.now(),
  })
  useStageStore.getState().openStage()
  await promoteDashboardApi(dashboard)
}

function WidgetTypeIcon({ type }: { type: string }) {
  switch (type) {
    case 'chart': return <BarChart2Icon className="h-3 w-3 shrink-0" />
    case 'markdown': return <FileTextIcon className="h-3 w-3 shrink-0" />
    default: return <LayoutDashboardIcon className="h-3 w-3 shrink-0" />
  }
}

function getWidgetLabel(w: Widget): string {
  const opts = w.options as Record<string, unknown>
  if (typeof opts?.title === 'string') return opts.title
  return w.type
}

export function DashboardBlock({ json, streaming }: DashboardBlockProps) {
  const { t } = useI18n()
  const [promoted, setPromoted] = useState(false)

  const state: DashboardBlockState = useMemo(() => {
    if (streaming) return 'streaming'
    const parsed = parseDashboard(json, t)
    if (parsed.ok) return 'preview'
    return 'error'
  }, [json, streaming, t])

  const parsedDashboard = useMemo(() => {
    if (state !== 'preview') return null
    const result = parseDashboard(json, t)
    return result.ok ? result.dashboard : null
  }, [json, state])

  if (state === 'streaming') {
    return (
      <div
        data-testid="dashboard-skeleton"
        className="flex items-center gap-2 p-4 rounded border border-[var(--dt-border)] bg-[var(--dt-muted)]"
      >
        <LayoutDashboardIcon className="h-5 w-5 animate-pulse text-[var(--dt-muted-foreground)]" />
        <span className="text-sm text-[var(--dt-muted-foreground)]">{t('dashboard.generating')}</span>
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div
        data-testid="dashboard-error"
        className="p-4 rounded border bg-[var(--dt-danger-surface)]"
        style={{ borderColor: 'var(--dt-danger-border)' }}
      >
        <div className="flex items-center gap-2 text-sm text-[var(--dt-danger)]">
          <LayoutDashboardIcon className="h-4 w-4" />
          <span className="font-medium">{t('dashboard.errorTitle')}</span>
        </div>
        <p className="mt-1 text-xs text-[var(--dt-danger)]">{parseDashboard(json, t).ok ? '' : (parseDashboard(json, t) as { error: string }).error}</p>
      </div>
    )
  }

  const dashboard = parsedDashboard!

  return (
    <div
      data-testid="dashboard-preview"
      className="p-4 rounded border border-[var(--dt-border)] bg-[var(--dt-card)]"
    >
      <div className="flex items-center gap-2 mb-2">
        <LayoutDashboardIcon className="h-4 w-4 text-[var(--dt-muted-foreground)]" />
        <span className="text-sm font-medium">{dashboard.title}</span>
        <span className="text-xs text-[var(--dt-muted-foreground)]">
          {t('dashboard.widgetCount', { count: dashboard.widgets.length })}
        </span>
      </div>
      {dashboard.widgets.length > 0 && (
        <div className="grid grid-cols-2 gap-1 mt-2">
          {dashboard.widgets.slice(0, 4).map((w) => (
            <div
              key={w.id}
              className="flex items-center gap-1 px-2 py-1 rounded text-xs bg-[var(--dt-surface)]"
            >
              <WidgetTypeIcon type={w.type} />
              <span className="truncate">{getWidgetLabel(w)}</span>
            </div>
          ))}
          {dashboard.widgets.length > 4 && (
            <div className="flex items-center px-2 py-1 rounded text-xs text-[var(--dt-muted-foreground)]">
              {t('dashboard.moreWidgets', { count: dashboard.widgets.length - 4 })}
            </div>
          )}
        </div>
      )}
      {!promoted && (
        <button
          type="button"
          className={cn(
            "flex items-center gap-1 text-xs px-2 py-1 rounded border transition-colors",
            "border-[var(--dt-border)] bg-transparent text-[var(--dt-text)]",
            "hover:bg-[var(--dt-accent-surface)] hover:text-[var(--dt-accent)]",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--dt-focus-ring)]",
            "active:translate-y-px"
          )}
          onClick={() => { promoteDashboard(dashboard); setPromoted(true) }}
        >
          <ExternalLinkIcon className="h-3 w-3" />
          {t('dashboard.openToWorkbench')}
        </button>
      )}
      {promoted && (
        <span className="text-xs text-[var(--dt-muted-foreground)]">{t('dashboard.openedInWorkbench')}</span>
      )}
    </div>
  )
}
