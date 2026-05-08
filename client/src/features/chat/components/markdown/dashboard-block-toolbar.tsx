import { ExternalLinkIcon } from 'lucide-react'
import type { Dashboard } from '@/features/dashboard/schema'
import { cn } from '@/lib/utils'

interface DashboardBlockToolbarProps {
  dashboard: Dashboard
  onPromote: (dashboard: Dashboard) => void
}

export function DashboardBlockToolbar({ dashboard, onPromote }: DashboardBlockToolbarProps) {
  return (
    <div className="flex items-center gap-2 mt-2">
      <button
        type="button"
        className={cn(
          "flex items-center gap-1 text-xs px-2 py-1 rounded border transition-colors",
          "border-[var(--dt-border)] bg-transparent text-[var(--dt-text)]",
          "hover:bg-[var(--dt-accent-surface)] hover:text-[var(--dt-accent)]",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--dt-focus-ring)]",
          "active:translate-y-px"
        )}
        onClick={() => onPromote(dashboard)}
      >
        <ExternalLinkIcon className="h-3 w-3" />
        Open to workbench
      </button>
    </div>
  )
}
