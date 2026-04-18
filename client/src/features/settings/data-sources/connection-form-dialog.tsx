import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { createConnection, updateConnection, connectionsKey, type Connection } from './api'

export const DATABASE_TYPES = {
  mysql: { label: 'MySQL', port: 3306 },
  postgres: { label: 'PostgreSQL', port: 5432 },
  h2: { label: 'H2', port: 9092 },
} as const

export type DatabaseKind = keyof typeof DATABASE_TYPES

type Props = { editing: Connection | null; onCancel: () => void; onSaved: () => void }

export function ConnectionFormPanel({ editing, onCancel, onSaved }: Props) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    name: '', kind: 'mysql', host: 'localhost', port: 3306,
    database: '', username: '', password: '',
    connectTimeout: 3000,
  })

  useEffect(() => {
    if (editing) {
      setForm({
        name: editing.name ?? '',
        kind: editing.kind, host: editing.host,
        port: editing.port, database: editing.databaseName ?? '',
        username: editing.username, password: '',
        connectTimeout: editing.connectTimeout ?? 3000,
      })
    } else {
      setForm({ name: '', kind: 'mysql', host: 'localhost', port: 3306,
        database: '', username: '', password: '', connectTimeout: 3000 })
    }
  }, [editing])

  const save = useMutation({
    mutationFn: async () => {
      const dbName = form.database.trim() || null
      const connName = form.name.trim() || '未命名数据源'
      if (editing) {
        await updateConnection(editing.id, {
          name: connName, kind: form.kind, host: form.host, port: form.port,
          databaseName: dbName, username: form.username,
          password: form.password.length > 0 ? form.password : null,
          connectTimeout: form.connectTimeout,
        })
      } else {
        await createConnection({
          name: connName, kind: form.kind, host: form.host, port: form.port,
          databaseName: dbName, username: form.username, password: form.password,
          connectTimeout: form.connectTimeout,
        })
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: connectionsKey })
      toast.success(editing ? '已更新' : '已创建')
      onSaved()
    },
  })

  return (
    <div className="rounded-lg border bg-card p-6">
      <h2 className="mb-4 text-lg font-medium">
        {editing ? '编辑数据源' : '新增数据源'}
      </h2>
      <div className="grid gap-4">
        <Field label="名称">
          <Input value={form.name} placeholder="未命名数据源"
            onChange={(e) => setForm(f => ({ ...f, name: e.target.value }))} />
        </Field>
        <Field label="类型">
          <Select value={form.kind}
            onValueChange={(v) => { if (v && v in DATABASE_TYPES) setForm(f => ({ ...f, kind: v as DatabaseKind, port: DATABASE_TYPES[v as DatabaseKind].port })) }}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              {(Object.keys(DATABASE_TYPES) as DatabaseKind[]).map(k => (
                <SelectItem key={k} value={k}>{DATABASE_TYPES[k].label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
        <Field label="主机">
          <Input value={form.host}
            onChange={(e) => setForm(f => ({ ...f, host: e.target.value }))} />
        </Field>
        <Field label="端口">
          <Input type="number" value={form.port}
            onChange={(e) => setForm(f => ({ ...f, port: Number(e.target.value) }))} />
        </Field>
        <Field label="数据库（可选）">
          <Input value={form.database} placeholder="留空则连接服务器级别"
            onChange={(e) => setForm(f => ({ ...f, database: e.target.value }))} />
        </Field>
        <Field label="用户名">
          <Input value={form.username}
            onChange={(e) => setForm(f => ({ ...f, username: e.target.value }))} />
        </Field>
        <Field label="密码">
          <Input type="password" value={form.password}
            onChange={(e) => setForm(f => ({ ...f, password: e.target.value }))} />
        </Field>
        <Field label="连接超时（毫秒）">
          <Input type="number" value={form.connectTimeout}
            onChange={(e) => setForm(f => ({ ...f, connectTimeout: Number(e.target.value) }))} />
        </Field>
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel}>取消</Button>
        <Button onClick={() => save.mutate()} disabled={save.isPending}>
          {save.isPending ? '保存中…' : '保存'}
        </Button>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[120px_1fr] items-center gap-2">
      <Label>{label}</Label>{children}
    </div>
  )
}
