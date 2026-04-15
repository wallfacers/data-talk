import type { WorkspaceTab } from '../types'

export function QueryResultTab({ tab }: { tab: WorkspaceTab }) {
  return (
    <div className="flex h-full flex-col p-4">
      <p className="text-sm text-muted-foreground">
        Tab: <span className="font-mono">{tab.title}</span>
      </p>
      <pre className="mt-4 overflow-auto rounded-md border bg-muted p-3 text-xs">
        {JSON.stringify(tab.payload ?? null, null, 2)}
      </pre>
    </div>
  )
}
