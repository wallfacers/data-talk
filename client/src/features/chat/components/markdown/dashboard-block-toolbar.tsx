import { ExternalLinkIcon } from 'lucide-react'
import type { Dashboard } from '@/features/dashboard/schema'

interface DashboardBlockToolbarProps {
  dashboard: Dashboard
  onPromote: (dashboard: Dashboard) => void
}

export function DashboardBlockToolbar({ dashboard, onPromote }: DashboardBlockToolbarProps) {
  return (
    <div className="flex items-center gap-2 mt-2">
      <button
        type="button"
        className="flex items-center gap-1 text-xs px-2 py-1 rounded border border-[var(--dt-border)] hover:bg-[var(--dt-accent)] transition-colors"
        onClick={() => onPromote(dashboard)}
      >
        <ExternalLinkIcon className="h-3 w-3" />
        Open to workbench
      </button>
    </div>
  )
}
