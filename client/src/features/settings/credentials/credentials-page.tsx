import { useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { Button } from '@/components/ui/button'
import { InfoIcon, PlusIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { useCredentialsQuery, useCreateCredentialMutation } from './hooks/use-credentials-query'
import { CredentialForm } from './credential-form'
import { CredentialList } from './credential-list'

export function CredentialsPage() {
  const { t } = useI18n()
  const credentials = useCredentialsQuery()
  const createMut = useCreateCredentialMutation()
  const [formOpen, setFormOpen] = useState(false)

  const items = credentials.data ?? []

  return (
    <div className="max-w-4xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('ingestion.credential.title')}</h1>
        <Button data-testid="credentials-create-btn" onClick={() => setFormOpen(true)} size="sm">
          <PlusIcon className="size-4" /> {t('ingestion.credential.create')}
        </Button>
      </div>

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
        <CredentialList credentials={items} />
      )}
      </div>

      {/* Create dialog */}
      <Dialog open={formOpen} onOpenChange={(open) => { if (!createMut.isPending) setFormOpen(open) }}>
        <DialogContent className="w-[520px] bg-canvas p-6" showCloseButton={!createMut.isPending}>
          <DialogHeader>
            <DialogTitle>{t('ingestion.credential.create')}</DialogTitle>
            <DialogDescription>
              {t('ingestion.credential.scheme.label')}
            </DialogDescription>
          </DialogHeader>
          <CredentialForm
            onSubmit={(req) => {
              createMut.mutate(req, {
                onSuccess: () => {
                  setFormOpen(false)
                  toast.success(t('common.done'))
                },
                onError: (err) => {
                  toast.error(err instanceof Error ? err.message : t('common.unknownError'))
                },
              })
            }}
            submitting={createMut.isPending}
          />
        </DialogContent>
      </Dialog>
    </div>
  )
}
