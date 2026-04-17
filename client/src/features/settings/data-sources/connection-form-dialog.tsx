import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { createConnection, updateConnection, connectionsKey, type Connection } from './api'

const DEFAULTS: Record<string, { port: number }> = {
  mysql: { port: 3306 }, postgres: { port: 5432 }, h2: { port: 9092 },
}

type Props = { open: boolean; editing: Connection | null; onClose: () => void }

export function ConnectionFormDialog({ open, editing, onClose }: Props) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    id: '', kind: 'mysql', host: 'localhost', port: 3306,
    database: '', username: '', password: '',
  })

  useEffect(() => {
    if (editing) {
      setForm({
        id: editing.id, kind: editing.kind, host: editing.host,
        port: editing.port, database: editing.databaseName,
        username: editing.username, password: '',
      })
    } else {
      setForm({ id: '', kind: 'mysql', host: 'localhost', port: 3306,
        database: '', username: '', password: '' })
    }
  }, [editing, open])

  const save = useMutation({
    mutationFn: async () => {
      if (editing) {
        await updateConnection(editing.id, {
          kind: form.kind, host: form.host, port: form.port,
          database: form.database, username: form.username,
          password: form.password.length > 0 ? form.password : null,
        })
      } else {
        await createConnection(form)
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: connectionsKey })
      toast.success(editing ? '已更新' : '已创建')
      onClose()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? '编辑数据源' : '新增数据源'}</DialogTitle>
        </DialogHeader>
        <div className="grid gap-3">
          <Field label="ID"><Input value={form.id} disabled={!!editing}
            onChange={(e) => setForm(f => ({ ...f, id: e.target.value }))} /></Field>
          <Field label="类型">
            <Select value={form.kind} onValueChange={(v) => { if (v) setForm(f => ({ ...f, kind: v, port: DEFAULTS[v]?.port ?? f.port })) }}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="mysql">MySQL</SelectItem>
                <SelectItem value="postgres">PostgreSQL</SelectItem>
                <SelectItem value="h2">H2</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="主机"><Input value={form.host}
            onChange={(e) => setForm(f => ({ ...f, host: e.target.value }))} /></Field>
          <Field label="端口"><Input type="number" value={form.port}
            onChange={(e) => setForm(f => ({ ...f, port: Number(e.target.value) }))} /></Field>
          <Field label="数据库"><Input value={form.database}
            onChange={(e) => setForm(f => ({ ...f, database: e.target.value }))} /></Field>
          <Field label="用户名"><Input value={form.username}
            onChange={(e) => setForm(f => ({ ...f, username: e.target.value }))} /></Field>
          <Field label={editing ? '密码（留空保持不变）' : '密码'}>
            <Input type="password" value={form.password}
              onChange={(e) => setForm(f => ({ ...f, password: e.target.value }))} />
          </Field>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>取消</Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? '保存中…' : '保存'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (<div className="grid grid-cols-[120px_1fr] items-center gap-2">
    <Label>{label}</Label>{children}
  </div>)
}
