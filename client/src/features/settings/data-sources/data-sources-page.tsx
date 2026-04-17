import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { TrashIcon, PencilIcon, PlusIcon, CheckCircle2Icon, XCircleIcon } from 'lucide-react'
import { listConnections, deleteConnection, testConnection, connectionsKey, type Connection } from './api'
import { ConnectionFormDialog } from './connection-form-dialog'

export function DataSourcesPage() {
  const qc = useQueryClient()
  const { data: connections = [], isLoading } = useQuery({
    queryKey: connectionsKey, queryFn: listConnections,
  })
  const [editing, setEditing] = useState<Connection | null>(null)
  const [creating, setCreating] = useState(false)
  const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail' | 'loading'>>({})

  const del = useMutation({
    mutationFn: deleteConnection,
    onSuccess: () => { qc.invalidateQueries({ queryKey: connectionsKey }); toast.success('已删除') },
    onError: (e: Error) => toast.error(e.message),
  })

  async function runTest(id: string) {
    setTestResult(r => ({ ...r, [id]: 'loading' }))
    try {
      const r = await testConnection(id)
      setTestResult(s => ({ ...s, [id]: r.ok ? 'ok' : 'fail' }))
      toast[r.ok ? 'success' : 'error'](r.ok ? `连接成功 (${r.latencyMs}ms)` : r.reason ?? '失败')
    } catch (e) {
      setTestResult(s => ({ ...s, [id]: 'fail' }))
      toast.error((e as Error).message)
    }
  }

  return (
    <div className="max-w-4xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">数据源</h1>
        <Button onClick={() => setCreating(true)} size="sm">
          <PlusIcon className="size-4" /> 新增
        </Button>
      </div>

      {isLoading ? (
        <div className="text-sm text-muted-foreground">加载中...</div>
      ) : connections.length === 0 ? (
        <div className="rounded border border-dashed p-8 text-center text-sm text-muted-foreground">
          还没有数据源，点击"新增"创建第一个
        </div>
      ) : (
        <table className="w-full text-sm">
          <thead className="text-left text-muted-foreground">
            <tr><th className="pb-2">ID</th><th>类型</th><th>地址</th><th>数据库</th><th>用户</th><th></th></tr>
          </thead>
          <tbody>
            {connections.map((c) => (
              <tr key={c.id} className="border-t">
                <td className="py-2">{c.id}</td>
                <td>{c.kind}</td>
                <td>{c.host}:{c.port}</td>
                <td>{c.databaseName}</td>
                <td>{c.username}</td>
                <td className="text-right">
                  <Button size="sm" variant="ghost" onClick={() => runTest(c.id)}>
                    {testResult[c.id] === 'loading' ? '测试中…'
                      : testResult[c.id] === 'ok' ? <CheckCircle2Icon className="size-4 text-green-600" />
                      : testResult[c.id] === 'fail' ? <XCircleIcon className="size-4 text-red-600" />
                      : '测试'}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setEditing(c)}><PencilIcon className="size-4" /></Button>
                  <Button size="sm" variant="ghost" onClick={() => del.mutate(c.id)}><TrashIcon className="size-4" /></Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <ConnectionFormDialog
        open={creating || !!editing}
        editing={editing}
        onClose={() => { setCreating(false); setEditing(null) }}
      />
    </div>
  )
}
