const OP_STYLES: Record<string, { textClass: string; bgClass: string }> = {
  INSERT: { textClass: 'text-status-info', bgClass: 'bg-status-infoSurface' },
  UPDATE: { textClass: 'text-status-warning', bgClass: 'bg-status-warning-surface' },
  DELETE: { textClass: 'text-status-danger', bgClass: 'bg-status-dangerSurface' },
}

export function OpLogOperationBadge({ operation }: { operation: string }) {
  const style = OP_STYLES[operation] ?? OP_STYLES.INSERT
  return (
    <span className={`inline-flex items-center rounded px-1.5 py-0.5 font-mono text-xs font-medium ${style.textClass} ${style.bgClass}`}>
      {operation}
    </span>
  )
}
