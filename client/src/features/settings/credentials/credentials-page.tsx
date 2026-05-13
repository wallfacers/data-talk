import { useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { Button } from '@/components/ui/button'
import { InfoIcon, PlusIcon } from 'lucide-react'
import { useCredentialsQuery } from './hooks/use-credentials-query'
import { CredentialFormPanel } from './credential-form-panel'
import { CredentialList } from './credential-list'
import type { CredentialView } from './api/credential-api'

export function CredentialsPage() {
  const { t } = useI18n()
  const credentials = useCredentialsQuery()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<CredentialView | null>(null)

  const items = credentials.data ?? []

  const listContent = (
    <>
      {/* Info banner */}
      <div className="mb-4 flex items-start gap-2 rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground">
        <InfoIcon className="mt-0.5 size-4 shrink-0" />
        <span>{t('ingestion.credential.dual_source_note')}</span>
      </div>

      {/* List */}
      <div data-testid="credentials-list">
      {credentials.isLoading ? (
        <div className="text-sm text-muted-foreground">{t('common.loading')}</div>
      ) : items.length === 0 ? (
        <div data-testid="credentials-empty-state" className="rounded border border-dashed p-8 text-center text-sm text-muted-foreground">
          {t('ingestion.credential.empty')}
        </div>
      ) : (
        <CredentialList credentials={items} onEdit={(c) => setEditing(c)} />
      )}
      </div>
    </>
  )

  return (
    <div className="max-w-4xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('ingestion.credential.title')}</h1>
        <Button
          data-testid="credentials-create-btn"
          onClick={() => { setEditing(null); setShowForm(true) }}
          size="sm"
          disabled={showForm || !!editing}
        >
          <PlusIcon className="size-4" /> {t('ingestion.credential.create')}
        </Button>
      </div>

      {showForm ? (
        <CredentialFormPanel
          editing={null}
          onCancel={() => setShowForm(false)}
          onSaved={() => setShowForm(false)}
        />
      ) : listContent}

      {!showForm && editing && (
        <div className="mt-6">
          <CredentialFormPanel
            editing={editing}
            onCancel={() => setEditing(null)}
            onSaved={() => setEditing(null)}
          />
        </div>
      )}
    </div>
  )
}
