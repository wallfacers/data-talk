import { useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { TrashIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { useDeleteCredentialMutation } from './hooks/use-credentials-query'
import type { CredentialView } from './api/credential-api'

function formatTimestamp(ts: number): string {
  return new Date(ts).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

const SCHEME_LABEL_MAP: Record<string, string> = {
  none: 'None',
  bearer: 'Bearer',
  api_key_header: 'API Key (Header)',
  api_key_query: 'API Key (Query)',
  basic: 'Basic',
}

interface Props {
  credentials: CredentialView[]
}

export function CredentialList({ credentials }: Props) {
  const { t } = useI18n()
  const del = useDeleteCredentialMutation()
  const [confirmDelete, setConfirmDelete] = useState<{
    id: string
    name: string
    refCount: number
  } | null>(null)

  function handleDeleteClick(cred: CredentialView) {
    setConfirmDelete({ id: cred.id, name: cred.name, refCount: 0 })
  }

  function executeDelete(id: string, force: boolean) {
    del.mutate(
      { id, force },
      {
        onSuccess: (result) => {
          if (result && 'referencingJobCount' in result && result.referencingJobCount > 0) {
            // Server returned 409 — show confirm dialog with ref count
            setConfirmDelete({
              id: result.credentialId,
              name: confirmDelete?.name ?? '',
              refCount: result.referencingJobCount,
            })
            return
          }
          setConfirmDelete(null)
          toast.success(t('common.deleted'))
        },
        onError: (err) => {
          toast.error(err instanceof Error ? err.message : t('common.unknownError'))
        },
      },
    )
  }

  return (
    <>
      <table className="w-full text-sm table-fixed">
        <thead className="text-left text-muted-foreground">
          <tr>
            <th className="pb-2 align-middle">{t('ingestion.credential.name')}</th>
            <th className="pb-2 w-36 align-middle">{t('ingestion.credential.scheme.label')}</th>
            <th className="pb-2 w-28 align-middle">Created</th>
            <th className="pb-2 w-20 align-middle pl-3">{t('dataSources.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {credentials.map((cred) => (
            <tr key={cred.id} data-testid={`credential-row-${cred.id}`} className="border-t">
              <td className="py-2 align-middle">
                <span className="truncate block">{cred.name}</span>
              </td>
              <td className="py-2 align-middle">
                <Badge variant="secondary" className="text-xs font-normal">
                  {SCHEME_LABEL_MAP[cred.authScheme] ?? cred.authScheme}
                </Badge>
              </td>
              <td className="py-2 align-middle text-muted-foreground">
                {formatTimestamp(cred.createdAt)}
              </td>
              <td className="py-2 align-middle">
                <Button
                  size="sm"
                  variant="ghost"
                  data-testid={`credential-delete-${cred.id}`}
                  aria-label={t('common.delete')}
                  onClick={() => handleDeleteClick(cred)}
                  className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                  disabled={del.isPending}
                >
                  <TrashIcon className="size-4" />
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Delete confirmation dialog */}
      <AlertDialog
        open={confirmDelete !== null}
        onOpenChange={(open) => {
          if (!open && !del.isPending) setConfirmDelete(null)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('common.delete')}</AlertDialogTitle>
            <AlertDialogDescription>
              {confirmDelete?.refCount
                ? t('ingestion.credential.delete.confirm', { n: confirmDelete.refCount })
                : t('dataSources.confirmDelete.description')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="border-t-0 bg-transparent pt-2">
            <AlertDialogCancel
              className="border-0 bg-transparent hover:bg-muted/50"
              disabled={del.isPending}
            >
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              className="border-0 bg-transparent"
              disabled={del.isPending}
              onClick={(event) => {
                event.preventDefault()
                if (confirmDelete) {
                  executeDelete(confirmDelete.id, confirmDelete.refCount > 0)
                }
              }}
            >
              {del.isPending ? t('common.loading') : t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
