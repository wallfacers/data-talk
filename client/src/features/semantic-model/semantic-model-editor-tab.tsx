import { useState } from 'react'
import { PendingList } from './components/pending-list'
import { VerifiedQueryPanel } from './components/verified-query-panel'
import { EmptyState } from './components/empty-state'
import { useSemanticDomainsQuery } from './hooks/use-semantic-queries'

interface SemanticModelEditorTabProps {
  connectionId: string
}

export function SemanticModelEditorTab({ connectionId }: SemanticModelEditorTabProps) {
  const [view, setView] = useState<'pending' | 'vqs'>('pending')
  const { data: domainsData } = useSemanticDomainsQuery(connectionId)
  const hasDomains = (domainsData?.domains?.length ?? 0) > 0

  if (!hasDomains) {
    return <EmptyState connectionId={connectionId} />
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex border-b">
        <button
          className={`px-4 py-2 text-sm font-medium ${view === 'pending' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-muted-foreground'}`}
          onClick={() => setView('pending')}
        >
          Pending Proposals
        </button>
        <button
          className={`px-4 py-2 text-sm font-medium ${view === 'vqs' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-muted-foreground'}`}
          onClick={() => setView('vqs')}
        >
          Verified Queries
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {view === 'pending' ? (
          <PendingList connectionId={connectionId} />
        ) : (
          <VerifiedQueryPanel connectionId={connectionId} />
        )}
      </div>
    </div>
  )
}
