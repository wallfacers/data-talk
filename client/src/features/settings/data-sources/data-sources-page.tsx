import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { TrashIcon, PencilIcon, PlusIcon, CheckCircle2Icon, XCircleIcon } from 'lucide-react'
import { listConnections, deleteConnection, testConnection, connectionsKey, type Connection } from './api'
import { ConnectionFormPanel } from './connection-form-dialog'

export function DataSourcesPage() {
  const qc = useQueryClient()
  const { data: connections = [], isLoading } = useQuery({
    queryKey: connectionsKey, queryFn: listConnections,
  })
  const [editing, setEditing] = useState<Connection | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail' | 'loading'>>({})

  function getStatus(c: Connection): 'ok' | 'fail' | 'loading' | null {
    return testResult[c.id] ?? (c.lastTestStatus === 'ok' || c.lastTestStatus === 'fail' ? c.lastTestStatus : null)
  }

  const del = useMutation({
    mutationFn: deleteConnection,
    onSuccess: () => { qc.invalidateQueries({ queryKey: connectionsKey }); toast.success('已删除') },
  })

  async function runTest(id: string) {
    setTestResult(r => ({ ...r, [id]: 'loading' }))
    try {
      const r = await testConnection(id)
      setTestResult(s => ({ ...s, [id]: r.ok ? 'ok' : 'fail' }))
      toast[r.ok ? 'success' : 'error'](r.ok ? `连接成功 (${r.latencyMs}ms)` : r.reason ?? '失败')
    } catch {
      setTestResult(s => ({ ...s, [id]: 'fail' }))
    }
  }

  const listContent = (
    <>
      {isLoading ? (
        <div className="text-sm text-muted-foreground">加载中...</div>
      ) : connections.length === 0 ? (
        <div className="rounded border border-dashed p-8 text-center text-sm text-muted-foreground">
          还没有数据源，点击"新增"创建第一个
        </div>
      ) : (
        <table className="w-full text-sm">
          <thead className="text-left text-muted-foreground">
            <tr>
              <th className="pb-2">名称</th>
              <th className="pb-2">类型</th>
              <th className="pb-2">地址</th>
              <th className="pb-2">数据库</th>
              <th className="pb-2">用户</th>
              <th className="pb-2 w-fit whitespace-nowrap">操作</th>
            </tr>
          </thead>
          <tbody>
            {connections.map((c) => {
                const status = getStatus(c)
                return (
              <tr key={c.id} className="border-t">
                <td className="py-2">{c.name}</td>
                <td className="py-2">{c.kind}</td>
                <td>{c.host}:{c.port}</td>
                <td>{c.databaseName}</td>
                <td>{c.username}</td>
                <td className="py-2 w-fit whitespace-nowrap">
                  <Button size="sm" variant="ghost" onClick={() => runTest(c.id)} className="min-w-16 h-8">
                    {status === 'loading' ? '测试中…'
                      : status === 'ok' ? <CheckCircle2Icon className="size-4 text-green-600" />
                      : status === 'fail' ? <XCircleIcon className="size-4 text-red-600" />
                      : '测试'}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setEditing(c)}><PencilIcon className="size-4" /></Button>
                  <Button size="sm" variant="ghost" onClick={() => del.mutate(c.id)}><TrashIcon className="size-4" /></Button>
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
        <h1 className="text-2xl font-semibold">数据源</h1>
        <Button onClick={() => setShowForm(true)} size="sm" disabled={showForm || !!editing}>
          <PlusIcon className="size-4" /> 新增
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
