const STATUS_STYLES: Record<string, { label: string; textClass: string; bgClass: string }> = {
  pending: { label: 'Pending', textClass: 'text-status-info', bgClass: 'bg-status-infoSurface' },
  active: { label: 'Active', textClass: 'text-status-success', bgClass: 'bg-status-success-surface' },
  undone: { label: 'Undone', textClass: 'text-text-muted', bgClass: 'bg-bg-subtle' },
  expired: { label: 'Expired', textClass: 'text-status-warning', bgClass: 'bg-status-warning-surface' },
}

export function OpLogStatusBadge({ status }: { status: string }) {
  const style = STATUS_STYLES[status] ?? STATUS_STYLES.pending
  return (
    <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium ${style.textClass} ${style.bgClass}`}>
      {style.label}
    </span>
  )
}
