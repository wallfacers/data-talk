import type { FileArtifactStatus } from '@/services/api/file-artifacts'
import { useI18n } from '@/i18n/use-i18n'

type FileArtifactStatusBadgeProps = {
  status: FileArtifactStatus
  className?: string
}

const STATUS_VISUAL: Record<
  Exclude<FileArtifactStatus, 'discarded'>,
  { icon: string; classes: string; labelKey: 'files.status.temporary' | 'files.status.candidate' | 'files.status.archived' }
> = {
  temporary: {
    icon: '📄',
    classes: 'inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs text-muted bg-transparent',
    labelKey: 'files.status.temporary',
  },
  candidate: {
    icon: '📌',
    classes:
      'inline-flex items-center gap-1 rounded border-l-2 border-l-accent-warn bg-status-warningSurface px-2 py-0.5 text-xs text-strong',
    labelKey: 'files.status.candidate',
  },
  archived: {
    icon: '📦',
    classes: 'inline-flex items-center gap-1 rounded bg-panel border border-subtle px-2 py-0.5 text-xs text-base',
    labelKey: 'files.status.archived',
  },
}

export function FileArtifactStatusBadge({ status, className }: FileArtifactStatusBadgeProps) {
  const { t } = useI18n()
  if (status === 'discarded') return null
  const visual = STATUS_VISUAL[status]
  return (
    <span
      data-testid="file-artifact-status-badge"
      role="status"
      aria-label={visual.labelKey}
      className={[visual.classes, className].filter(Boolean).join(' ')}
    >
      <span aria-hidden>{visual.icon}</span>
      <span>{t(visual.labelKey)}</span>
    </span>
  )
}
