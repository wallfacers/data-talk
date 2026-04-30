import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import { createConnection, updateConnection, connectionsKey, type Connection } from './api'

export const DATABASE_TYPES = {
  mysql: { label: 'MySQL', port: 3306 },
  postgres: { label: 'PostgreSQL', port: 5432 },
  h2: { label: 'H2', port: 9092 },
  sqlite: { label: 'SQLite', port: 0 },
} as const

export type DatabaseKind = keyof typeof DATABASE_TYPES

type Props = { editing: Connection | null; onCancel: () => void; onSaved: () => void }

export function ConnectionFormPanel({ editing, onCancel, onSaved }: Props) {
  const { t } = useI18n()
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
      const connName = form.name.trim() || t('dataSources.unnamed')
      const sqlite = form.kind === 'sqlite'
      if (editing) {
        await updateConnection(editing.id, {
          name: connName, kind: form.kind, host: sqlite ? '' : form.host, port: sqlite ? 0 : form.port,
          databaseName: dbName, username: sqlite ? '' : form.username,
          password: form.password.length > 0 ? form.password : null,
          connectTimeout: form.connectTimeout,
        })
      } else {
        await createConnection({
          name: connName, kind: form.kind, host: sqlite ? '' : form.host, port: sqlite ? 0 : form.port,
          databaseName: dbName, username: sqlite ? '' : form.username, password: sqlite ? '' : form.password,
          connectTimeout: form.connectTimeout,
        })
      }
      qc.invalidateQueries({ queryKey: connectionsKey })
      qc.invalidateQueries({ queryKey: ['session-data-context'] })
      onSaved()
    },
    onSuccess: () => toast.success(editing ? t('dataSources.updated') : t('dataSources.created')),
  })
  const isSqlite = form.kind === 'sqlite'
  const databaseLabel = isSqlite ? t('dataSources.sqliteFilePath') : t('dataSources.databaseOptional')
  const databasePlaceholder = isSqlite ? t('dataSources.sqliteFilePathPlaceholder') : t('dataSources.databasePlaceholder')

  return (
    <div className="rounded-lg border bg-card p-6">
      <h2 className="mb-4 text-lg font-medium">
        {editing ? t('dataSources.edit') : t('dataSources.create')}
      </h2>
      <div className="grid gap-4">
        <Field label={t('dataSources.name')}>
          <Input aria-label={t('dataSources.name')} value={form.name} placeholder={t('dataSources.unnamed')}
            onChange={(e) => setForm(f => ({ ...f, name: e.target.value }))} />
        </Field>
        <Field label={t('dataSources.type')}>
          <Select value={form.kind}
            onValueChange={(v) => {
              if (v && v in DATABASE_TYPES) {
                const nextKind = v as DatabaseKind
                setForm(f => ({
                  ...f,
                  kind: nextKind,
                  host: nextKind === 'sqlite' ? '' : f.host || 'localhost',
                  port: DATABASE_TYPES[nextKind].port,
                  username: nextKind === 'sqlite' ? '' : f.username,
                  password: nextKind === 'sqlite' ? '' : f.password,
                }))
              }
            }}>
            <SelectTrigger aria-label={t('dataSources.type')}><SelectValue /></SelectTrigger>
            <SelectContent>
              {(Object.keys(DATABASE_TYPES) as DatabaseKind[]).map(k => (
                <SelectItem key={k} value={k}>{DATABASE_TYPES[k].label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
        {isSqlite ? null : (
          <>
            <Field label={t('dataSources.host')}>
              <Input aria-label={t('dataSources.host')} value={form.host}
                onChange={(e) => setForm(f => ({ ...f, host: e.target.value }))} />
            </Field>
            <Field label={t('dataSources.port')}>
              <Input aria-label={t('dataSources.port')} type="number" value={form.port}
                onChange={(e) => setForm(f => ({ ...f, port: Number(e.target.value) }))} />
            </Field>
          </>
        )}
        <Field label={databaseLabel}>
          <Input aria-label={databaseLabel} value={form.database} placeholder={databasePlaceholder}
            onChange={(e) => setForm(f => ({ ...f, database: e.target.value }))} />
        </Field>
        {isSqlite ? null : (
          <>
            <Field label={t('dataSources.username')}>
              <Input aria-label={t('dataSources.username')} value={form.username}
                onChange={(e) => setForm(f => ({ ...f, username: e.target.value }))} />
            </Field>
            <Field label={t('dataSources.password')}>
              <Input aria-label={t('dataSources.password')} type="password" value={form.password}
                onChange={(e) => setForm(f => ({ ...f, password: e.target.value }))} />
            </Field>
          </>
        )}
        <Field label={t('dataSources.connectTimeoutMs')}>
          <Input aria-label={t('dataSources.connectTimeoutMs')} type="number" value={form.connectTimeout}
            onChange={(e) => setForm(f => ({ ...f, connectTimeout: Number(e.target.value) }))} />
        </Field>
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel}>{t('common.cancel')}</Button>
        <Button onClick={() => save.mutate()} disabled={save.isPending}>
          {save.isPending ? t('common.saving') : t('common.save')}
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
