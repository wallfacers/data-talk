import { useState, useMemo, useCallback } from 'react'
import { LayoutDashboardIcon, ExternalLinkIcon, BarChart2Icon, FileTextIcon, ChevronDownIcon } from 'lucide-react'
import { dashboardSchema } from '@/features/dashboard/schema'
import type { Dashboard, Widget } from '@/features/dashboard/schema'
import { humanizeZodIssue, type HumanizedIssue } from '@/features/dashboard/zod-issue-humanizer'
import { useDashboardTabsStore } from '@/features/dashboard/stores/dashboard-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { generateUuid } from '@/lib/uuid'
import { cn, copyToClipboard } from '@/lib/utils'
import { promoteDashboard as promoteDashboardApi } from '@/features/dashboard/services/dashboard-api'
import { useI18n } from '@/i18n/use-i18n'
import type { TranslationFn } from '@/i18n/provider'

const COPY_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-copy"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>`
const CHECK_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-check"><path d="M20 6 9 17l-5-5"/></svg>`

type DashboardBlockState = 'streaming' | 'preview' | 'error'

interface DashboardBlockProps {
  json: string
  streaming: boolean
  messageId?: string
  partId?: string
}

type ParseResult =
  | { ok: true; dashboard: Dashboard }
  | { ok: false; issues: HumanizedIssue[] }

function parseDashboard(json: string, t: TranslationFn): ParseResult {
  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch {
    return { ok: false, issues: [{ path: '', friendly: t('dashboard.invalidJson') }] }
  }
  const obj = parsed as Record<string, unknown> | null
  if (obj && typeof obj === 'object' && 'id' in obj && typeof obj.id === 'string') {
    obj.id = obj.id.replace(/-/g, '')
  }
  const result = dashboardSchema.safeParse(obj)
  if (result.success) {
    return { ok: true, dashboard: result.data }
  }
  const issues = result.error.issues.map((issue) => humanizeZodIssue(issue, obj, t))
  return { ok: false, issues }
}

async function promoteDashboard(dashboard: Dashboard) {
  // AI typically only fills defaultConnectionId, leaving defaultDatabase/Schema blank,
  // which leaves widget SQL like `SELECT ... FROM users` ambiguous when the connection
  // has multiple databases. Fall back to the active session's data context so the
  // server-side TableContextAutoResolver lands on the same db/schema the chat was using.
  const sessionState = useSessionStore.getState()
  const sessionId = sessionState.activeSessionId
  const sessionContext = sessionId ? sessionState.dataContextBySession.get(sessionId) ?? null : null
  const enriched: Dashboard = {
    ...dashboard,
    defaultConnectionId: dashboard.defaultConnectionId ?? sessionContext?.connectionId ?? null,
    defaultDatabase: dashboard.defaultDatabase ?? sessionContext?.database ?? null,
    defaultSchema: dashboard.defaultSchema ?? sessionContext?.schema ?? null,
  }

  // Persist first so we know the server-assigned id; iframe shell calls
  // GET /api/dashboards/{id}/html with this exact id, so the client store
  // must mirror it.
  // Also pass sessionId so the server can backstop missing defaultDatabase/Schema
  // from the chat session's data-context if our client-side enrichment didn't fire.
  const result = await promoteDashboardApi(enriched, sessionId)
  const tabId = `dashboard_${generateUuid()}`
  const finalDashboard: Dashboard = result
    ? { ...enriched, id: result.id, version: result.version }
    : enriched
  useDashboardTabsStore.getState().hydrateTab(tabId, finalDashboard)
  useStageStore.getState().openTab({
    tabId,
    type: 'dashboard',
    title: finalDashboard.title,
    payload: finalDashboard,
    createdAt: Date.now(),
  })
  useStageStore.getState().openStage()
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
  const [errorOpen, setErrorOpen] = useState(false)
  const [rawJsonOpen, setRawJsonOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  const handleCopy = useCallback(async () => {
    const success = await copyToClipboard(json)
    if (success) {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }, [json])

  const parsed = useMemo(() => (streaming ? null : parseDashboard(json, t)), [json, streaming, t])
  const state: DashboardBlockState = streaming ? 'streaming' : parsed?.ok ? 'preview' : 'error'
  const parsedDashboard = parsed?.ok ? parsed.dashboard : null
  const issues = parsed && !parsed.ok ? parsed.issues : []

  if (state === 'streaming') {
    return (
      <div
        data-testid="dashboard-skeleton"
        data-component="basic-tool"
        data-status="running"
        className="my-2 flex items-center gap-2 rounded-md border px-3 py-2"
      >
        <LayoutDashboardIcon className="h-4 w-4 shrink-0 animate-pulse text-muted-foreground" />
        <span className="text-sm text-muted-foreground">{t('dashboard.generating')}</span>
      </div>
    )
  }

  if (state === 'error') {
    const summary = t('dashboard.errorSummary', { count: issues.length })

    return (
      <div
        role="alert"
        data-testid="dashboard-error"
        data-component="basic-tool"
        data-status="error"
        className="my-2 rounded-md border border-[var(--dt-status-danger)]"
      >
        <div className="flex w-full items-center gap-2 px-3 py-2">
          <button
            type="button"
            data-component="tool-trigger"
            data-open={errorOpen ? 'true' : 'false'}
            aria-expanded={errorOpen}
            onClick={() => setErrorOpen((v) => !v)}
            className="flex min-w-0 flex-1 select-text items-center gap-2 text-left"
          >
            <LayoutDashboardIcon className="h-4 w-4 shrink-0 text-[var(--dt-status-danger)]" />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1">
                <span className="font-medium text-[var(--dt-status-danger)] whitespace-nowrap">
                  {t('dashboard.errorTitle')}
                </span>
                <span
                  data-testid="dashboard-error-summary"
                  className="min-w-0 max-w-full truncate text-xs text-[var(--dt-text-muted)]"
                >
                  {summary}
                </span>
              </div>
            </div>
            <span
              className={cn(
                'flex size-4 shrink-0 items-center justify-center text-[var(--dt-text-muted)] duration-[120ms] [transition-property:transform] [transition-timing-function:cubic-bezier(0.2,0,0,1)]',
                errorOpen && 'rotate-180',
              )}
            >
              <ChevronDownIcon className="pointer-events-none size-4" />
            </span>
          </button>
          <div data-slot="markdown-code-actions" className="relative z-10 shrink-0">
            <button
              type="button"
              data-slot="markdown-copy-button"
              data-copied={copied ? 'true' : undefined}
              onClick={handleCopy}
              aria-label={t('common.copy')}
            >
              <span dangerouslySetInnerHTML={{ __html: copied ? CHECK_SVG : COPY_SVG }} />
            </button>
          </div>
        </div>
        {errorOpen && (
          <div className="border-t bg-[var(--dt-status-danger-surface)]">
            <ul
              data-testid="dashboard-error-issue-list"
              className="space-y-1.5 px-3 py-2"
            >
              {issues.map((it, i) => (
                <li
                  key={`${it.path}-${i}`}
                  data-testid="dashboard-error-issue"
                  className="flex select-text flex-col gap-0.5"
                >
                  {it.path ? (
                    <span className="font-mono text-[13px] leading-[18px] text-[var(--dt-text-muted)]">
                      {it.path}
                    </span>
                  ) : null}
                  <span className="text-[13px] leading-[18px] text-[var(--dt-text-base)]">
                    {it.friendly}
                  </span>
                </li>
              ))}
            </ul>
            <div className="border-t border-[var(--dt-status-danger)]/20">
              <button
                type="button"
                aria-expanded={rawJsonOpen}
                onClick={() => setRawJsonOpen((v) => !v)}
                className="flex w-full items-center justify-between px-3 py-1.5 text-xs text-[var(--dt-text-muted)] hover:text-[var(--dt-text-base)] duration-[120ms] [transition-property:color] [transition-timing-function:cubic-bezier(0.2,0,0,1)]"
              >
                <span>{t('dashboard.errorRawJsonToggle')}</span>
                <ChevronDownIcon
                  className={cn(
                    'size-3.5 duration-[120ms] [transition-property:transform] [transition-timing-function:cubic-bezier(0.2,0,0,1)]',
                    rawJsonOpen && 'rotate-180',
                  )}
                />
              </button>
              {rawJsonOpen && (
                <pre
                  data-testid="dashboard-error-raw-json"
                  className="max-h-[220px] select-text overflow-auto px-3 pb-2 font-mono text-[13px] leading-[18px] text-[var(--dt-text-muted)]"
                >
                  {json}
                </pre>
              )}
            </div>
          </div>
        )}
      </div>
    )
  }

  const dashboard = parsedDashboard!

  return (
    <div
      data-testid="dashboard-preview"
      data-component="basic-tool"
      data-status="completed"
      className="my-2 rounded-md border"
    >
      <div className="flex w-full items-start gap-2 px-3 py-2">
        <LayoutDashboardIcon className="mt-0.5 h-4 w-4 shrink-0 text-[var(--dt-muted-foreground)]" />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1">
            <span className="font-medium [overflow-wrap:anywhere]">{dashboard.title}</span>
            <span className="text-xs text-muted-foreground">
              {t('dashboard.widgetCount', { count: dashboard.widgets.length })}
            </span>
          </div>
        </div>
        <div data-slot="markdown-code-actions" className="relative z-10 shrink-0">
          {!promoted ? (
            <button
              type="button"
              onClick={() => { promoteDashboard(dashboard); setPromoted(true) }}
            >
              <ExternalLinkIcon className="h-3.5 w-3.5" />
              <span>{t('dashboard.openToWorkbench')}</span>
            </button>
          ) : (
            <span className="px-2 text-xs text-muted-foreground">
              {t('dashboard.openedInWorkbench')}
            </span>
          )}
        </div>
      </div>
      {dashboard.widgets.length > 0 && (
        <div className="grid grid-cols-2 gap-1 border-t px-3 py-2">
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
            <div className="flex items-center px-2 py-1 rounded text-xs text-muted-foreground">
              {t('dashboard.moreWidgets', { count: dashboard.widgets.length - 4 })}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
