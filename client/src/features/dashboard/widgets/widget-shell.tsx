import type { ReactNode } from 'react'

interface WidgetShellProps {
  title?: string
  children: ReactNode
  className?: string
}

export function WidgetShell({ title, children, className }: WidgetShellProps) {
  return (
    <div
      className={`flex flex-col h-full rounded border border-[var(--dt-border)] bg-[var(--dt-card)] overflow-hidden ${className ?? ''}`}
      data-component="dashboard-widget-shell"
    >
      {title && (
        <div className="flex items-center px-3 py-1.5 border-b border-[var(--dt-border)] text-xs font-medium text-[var(--dt-muted-foreground)] truncate">
          {title}
        </div>
      )}
      <div className="flex-1 min-h-0 overflow-auto p-2">
        {children}
      </div>
    </div>
  )
}
