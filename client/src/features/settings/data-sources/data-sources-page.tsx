import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { TrashIcon, PencilIcon, PlusIcon, CheckCircle2Icon, XCircleIcon, LoaderIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { listConnections, deleteConnection, testConnection, connectionsKey, type Connection } from './api'
import { ConnectionFormPanel } from './connection-form-dialog'
import { useConnectionStore } from '@/features/connection/store'

export function DataSourcesPage() {
  const { t } = useI18n()
  const qc = useQueryClient()
  const { data: connectionsData, isLoading } = useQuery({
    queryKey: connectionsKey, queryFn: listConnections,
  })
  const connections = connectionsData ?? []
  const setConnections = useConnectionStore((s) => s.setConnections)
  const [editing, setEditing] = useState<Connection | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail' | 'loading'>>({})

  useEffect(() => {
    if (connectionsData === undefined) return
    setConnections(connectionsData)
  }, [connectionsData, setConnections])

  function getStatus(c: Connection): 'ok' | 'fail' | 'loading' | null {
    return testResult[c.id] ?? (c.lastTestStatus === 'ok' || c.lastTestStatus === 'fail' ? c.lastTestStatus : null)
  }

  const del = useMutation({
    mutationFn: deleteConnection,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: connectionsKey })
      qc.invalidateQueries({ queryKey: ['session-data-context'] })
      toast.success(t('common.deleted'))
    },
  })

  async function runTest(id: string) {
    setTestResult(r => ({ ...r, [id]: 'loading' }))
    try {
      const r = await testConnection(id)
      setTestResult(s => ({ ...s, [id]: r.ok ? 'ok' : 'fail' }))
      await qc.invalidateQueries({ queryKey: connectionsKey })
      toast[r.ok ? 'success' : 'error'](r.ok ? t('dataSources.testSuccess', { latencyMs: r.latencyMs }) : r.reason ?? t('dataSources.testFailure'))
    } catch (err) {
      setTestResult(s => ({ ...s, [id]: 'fail' }))
      toast.error(err instanceof Error ? err.message : t('dataSources.testFailed'))
    }
  }

  const listContent = (
    <>
      {isLoading ? (
        <div className="text-sm text-muted-foreground">{t('common.loading')}</div>
      ) : connections.length === 0 ? (
        <div className="rounded border border-dashed p-8 text-center text-sm text-muted-foreground">
          {t('dataSources.empty')}
        </div>
      ) : (
        <table className="w-full text-sm table-fixed">
          <thead className="text-left text-muted-foreground">
            <tr>
              <th className="pb-2 align-middle">{t('dataSources.name')}</th>
              <th className="pb-2 w-24 align-middle">{t('dataSources.type')}</th>
              <th className="pb-2 align-middle" style={{ width: '28%' }}>{t('dataSources.address')}</th>
              <th className="pb-2 align-middle" style={{ width: '15%' }}>{t('dataSources.database')}</th>
              <th className="pb-2 align-middle" style={{ width: '12%' }}>{t('dataSources.user')}</th>
              <th className="pb-2 w-[140px] align-middle pl-3">{t('dataSources.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {connections.map((c) => {
                const status = getStatus(c)
                const addr = `${c.host}:${c.port}`
                return (
              <tr key={c.id} className="border-t">
                <td className="py-2 align-middle">
                  <Tooltip><TooltipTrigger render={<span className="truncate block" />}>{c.name}</TooltipTrigger><TooltipContent side="bottom" sideOffset={4}>{c.name}</TooltipContent></Tooltip>
                </td>
                <td className="py-2 align-middle">{c.kind}</td>
                <td className="align-middle">
                  <Tooltip><TooltipTrigger render={<span className="truncate block" />}>{addr}</TooltipTrigger><TooltipContent side="bottom" sideOffset={4}>{addr}</TooltipContent></Tooltip>
                </td>
                <td className="align-middle">
                  <Tooltip><TooltipTrigger render={<span className="truncate block" />}>{c.databaseName}</TooltipTrigger><TooltipContent side="bottom" sideOffset={4}>{c.databaseName}</TooltipContent></Tooltip>
                </td>
                <td className="align-middle">
                  <Tooltip><TooltipTrigger render={<span className="truncate block" />}>{c.username}</TooltipTrigger><TooltipContent side="bottom" sideOffset={4}>{c.username}</TooltipContent></Tooltip>
                </td>
                <td className="py-2 align-middle">
                  <div className="flex gap-1 items-center">
                    <Button size="sm" variant="ghost" onClick={() => runTest(c.id)} className="h-8 min-w-[60px] px-3 justify-center" disabled={status === 'loading'}>
                      {status === 'loading' ? <LoaderIcon className="size-4 animate-spin" />
                        : status === 'ok' ? <CheckCircle2Icon className="size-4 text-green-600" />
                        : status === 'fail' ? <XCircleIcon className="size-4 text-red-600" />
                        : t('dataSources.test')}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(c)} className="h-8 w-8 p-0"><PencilIcon className="size-4" /></Button>
                    <Button size="sm" variant="ghost" onClick={() => del.mutate(c.id)} className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10" disabled={del.isPending}><TrashIcon className="size-4" /></Button>
                  </div>
                </td>
              </tr>
            )})}
          </tbody>
        </table>
      )}
    </>
  )

  return (
    <div className="max-w-4xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('dataSources.title')}</h1>
        <Button onClick={() => setShowForm(true)} size="sm" disabled={showForm || !!editing}>
          <PlusIcon className="size-4" /> {t('dataSources.add')}
        </Button>
      </div>

      {showForm ? (
        <ConnectionFormPanel
          editing={editing}
          onCancel={() => { setShowForm(false); setEditing(null) }}
          onSaved={() => { setShowForm(false); setEditing(null) }}
        />
      ) : listContent}

      {!showForm && editing && (
        <div className="mt-6">
          <ConnectionFormPanel
            editing={editing}
            onCancel={() => setEditing(null)}
            onSaved={() => setEditing(null)}
          />
        </div>
      )}
    </div>
  )
}
