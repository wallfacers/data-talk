import { AlertTriangleIcon, Loader2Icon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { useOpencodeHealth } from './hooks/use-opencode-health'

export type BannerKind = 'none' | 'starting' | 'degraded'

export function selectBannerKind(status: string | undefined): BannerKind {
  if (status === 'degraded') return 'degraded'
  if (status === 'starting') return 'starting'
  return 'none'
}

/**
 * Surfaces the OpenCode bridge status as a non-blocking banner: a neutral
 * "AI engine starting" notice while the bridge is coming up, and a distinct
 * problem notice (with reason) when it is degraded. Renders nothing when the
 * bridge is genuinely ready. State is conveyed by icon + text, not color alone.
 */
export function OpencodeStatusBanner() {
  const { t } = useI18n()
  const { data: health } = useOpencodeHealth()
  const kind = selectBannerKind(health?.status)

  if (kind === 'none') return null

  if (kind === 'starting') {
    return (
      <div
        data-opencode-health="starting"
        className="mx-auto mb-4 w-full max-w-3xl rounded border border-border bg-muted/50 p-3 text-sm text-muted-foreground"
      >
        <div className="flex items-center gap-2 font-medium text-foreground">
          <Loader2Icon className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />
          {t('session.opencodeStartingTitle')}
        </div>
        <p className="mt-1">{t('session.opencodeStartingBody')}</p>
      </div>
    )
  }

  const reason = health?.reason ?? health?.message ?? ''
  return (
    <div
      data-opencode-health="degraded"
      className="mx-auto mb-4 w-full max-w-3xl rounded border border-amber-500/40 bg-amber-50 p-3 text-sm text-amber-800 dark:bg-amber-950/20 dark:text-amber-300"
    >
      <div className="flex items-center gap-2 font-medium">
        <AlertTriangleIcon className="size-4" aria-hidden />
        {t('session.opencodeDegradedTitle')}
      </div>
      <p className="mt-1">{t('session.opencodeDegradedBody')}</p>
      {reason ? (
        <p className="mt-1 text-xs text-amber-700/80 dark:text-amber-200/80">
          {t('session.opencodeDegradedReason', { reason })}
        </p>
      ) : null}
    </div>
  )
}
