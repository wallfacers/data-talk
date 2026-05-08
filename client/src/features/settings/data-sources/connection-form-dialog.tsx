import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
  mariadb: { label: 'MariaDB', port: 3306 },
  oracle: { label: 'Oracle', port: 1521 },
  sqlserver: { label: 'SQL Server', port: 1433 },
  duckdb: { label: 'DuckDB', port: 0 },
  clickhouse: { label: 'ClickHouse', port: 8123 },
  apache_doris: { label: 'Apache Doris', port: 9030 },
  starrocks: { label: 'StarRocks', port: 9030 },
  trino: { label: 'Trino', port: 8080 },
  hive: { label: 'Apache Hive', port: 10000 },
  presto: { label: 'Presto', port: 8080 },
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
    oracleServiceType: 'service' as 'service' | 'sid',
    sqlserverEncrypt: true,
    sqlserverTrustServerCertificate: true,
    sqlserverInstanceName: '',
    duckdbMode: 'memory' as 'memory' | 'file',
    duckdbReadOnly: false,
    clickhouseSSL: false,
  })

  useEffect(() => {
    if (editing) {
      const isDuckdb = editing.kind === 'duckdb'
      const duckdbMode = isDuckdb
        ? (!editing.databaseName || editing.databaseName === ':memory:' ? 'memory' : 'file')
        : 'memory'
      setForm({
        name: editing.name ?? '',
        kind: editing.kind, host: editing.host,
        port: editing.port, database: editing.databaseName ?? '',
        username: editing.username, password: '',
        connectTimeout: editing.connectTimeout ?? 3000,
        oracleServiceType: editing.oracleServiceType === 'sid' ? 'sid' : 'service',
        sqlserverEncrypt: editing.sqlserverEncrypt !== false,
        sqlserverTrustServerCertificate: editing.sqlserverTrustServerCertificate !== false,
        sqlserverInstanceName: editing.sqlserverInstanceName ?? '',
        duckdbMode,
        duckdbReadOnly: isDuckdb ? (editing.readOnly === true) : false,
        clickhouseSSL: editing.kind === 'clickhouse' ? editing.port === 8443 : false,
      })
    } else {
      setForm({ name: '', kind: 'mysql', host: 'localhost', port: 3306,
        database: '', username: '', password: '', connectTimeout: 3000,
        oracleServiceType: 'service', sqlserverEncrypt: true,
        sqlserverTrustServerCertificate: true, sqlserverInstanceName: '',
        duckdbMode: 'memory', duckdbReadOnly: false, clickhouseSSL: false })
    }
  }, [editing])

  const save = useMutation({
    mutationFn: async () => {
      const connName = form.name.trim() || t('dataSources.unnamed')
      const sqlite = form.kind === 'sqlite'
      const isDuckdb = form.kind === 'duckdb'
      const embedded = sqlite || isDuckdb
      const dbName = isDuckdb
        ? (form.duckdbMode === 'memory' ? ':memory:' : (form.database.trim() || null))
        : (form.database.trim() || null)
      const base = {
        name: connName, kind: form.kind, host: embedded ? '' : form.host, port: embedded ? 0 : form.port,
        databaseName: dbName, username: embedded ? '' : form.username,
        password: form.password.length > 0 ? form.password : null,
        connectTimeout: form.connectTimeout,
      }
      const dialectExtras: Record<string, unknown> = {}
      if (form.kind === 'oracle') {
        dialectExtras.oracleServiceType = form.oracleServiceType
      }
      if (form.kind === 'sqlserver') {
        dialectExtras.sqlserverEncrypt = form.sqlserverEncrypt
        dialectExtras.sqlserverTrustServerCertificate = form.sqlserverTrustServerCertificate
        if (form.sqlserverInstanceName.trim()) {
          dialectExtras.sqlserverInstanceName = form.sqlserverInstanceName.trim()
        }
      }
      if (isDuckdb) {
        dialectExtras.readOnly = form.duckdbReadOnly
      }
      if (editing) {
        await updateConnection(editing.id, { ...base, ...dialectExtras })
      } else {
        await createConnection({
          ...base,
          password: embedded ? '' : form.password,
          ...dialectExtras,
        } as Parameters<typeof createConnection>[0])
      }
      qc.invalidateQueries({ queryKey: connectionsKey })
      qc.invalidateQueries({ queryKey: ['session-data-context'] })
      onSaved()
    },
    onSuccess: () => toast.success(editing ? t('dataSources.updated') : t('dataSources.created')),
  })
  const isSqlite = form.kind === 'sqlite'
  const isDuckdb = form.kind === 'duckdb'
  const isOracle = form.kind === 'oracle'
  const isSqlserver = form.kind === 'sqlserver'
  const isClickhouse = form.kind === 'clickhouse'
  const isStarrocks = form.kind === 'starrocks'
  const hideHostPort = isSqlite || isDuckdb
  const databaseLabel = isDuckdb
    ? (form.duckdbMode === 'file' ? t('dataSources.duckdbFilePath') : '')
    : (isSqlite ? t('dataSources.sqliteFilePath')
       : isStarrocks ? t('dataSources.databaseRequired') : t('dataSources.databaseOptional'))
  const databasePlaceholder = isDuckdb
    ? t('dataSources.duckdbFilePathPlaceholder')
    : (isSqlite ? t('dataSources.sqliteFilePathPlaceholder') : t('dataSources.databasePlaceholder'))

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
                const embedded = nextKind === 'sqlite' || nextKind === 'duckdb'
                setForm(f => ({
                  ...f,
                  kind: nextKind,
                  host: embedded ? '' : f.host || 'localhost',
                  port: DATABASE_TYPES[nextKind].port,
                  username: embedded ? '' : f.username,
                  password: embedded ? '' : f.password,
                  ...(nextKind === 'duckdb' ? { duckdbMode: 'memory' as const, duckdbReadOnly: false } : {}),
                  ...(nextKind === 'clickhouse' ? { clickhouseSSL: false as const, port: 8123 } : {}),
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
        {hideHostPort ? null : (
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
        {isClickhouse ? (
          <Field label={t('dataSources.clickhouseProtocol')}>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                size="sm"
                variant={!form.clickhouseSSL ? 'default' : 'outline'}
                onClick={() => setForm(f => ({ ...f, clickhouseSSL: false, port: 8123 }))}
              >
                HTTP
              </Button>
              <Button
                type="button"
                size="sm"
                variant={form.clickhouseSSL ? 'default' : 'outline'}
                onClick={() => setForm(f => ({ ...f, clickhouseSSL: true, port: 8443 }))}
              >
                HTTPS
              </Button>
            </div>
          </Field>
        ) : null}
        {isOracle ? (
          <Field label={t('dataSources.oracleServiceType')}>
            <Select value={form.oracleServiceType}
              onValueChange={(v) => {
                if (v === 'service' || v === 'sid') {
                  setForm(f => ({ ...f, oracleServiceType: v }))
                }
              }}>
              <SelectTrigger aria-label={t('dataSources.oracleServiceType')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="service">{t('dataSources.oracleServiceName')}</SelectItem>
                <SelectItem value="sid">{t('dataSources.oracleSid')}</SelectItem>
              </SelectContent>
            </Select>
          </Field>
        ) : null}
        {isSqlserver ? (
          <>
            <Field label="">
              <div className="flex items-center gap-4">
                <label className="flex items-center gap-2 text-sm">
                  <Checkbox checked={form.sqlserverEncrypt}
                    onCheckedChange={(v) => setForm(f => ({ ...f, sqlserverEncrypt: v === true }))} />
                  {t('dataSources.sqlserverEncrypt')}
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <Checkbox checked={form.sqlserverTrustServerCertificate}
                    onCheckedChange={(v) => setForm(f => ({ ...f, sqlserverTrustServerCertificate: v === true }))} />
                  {t('dataSources.sqlserverTrustCert')}
                </label>
              </div>
            </Field>
            <Field label={t('dataSources.sqlserverInstance')}>
              <Input aria-label={t('dataSources.sqlserverInstance')} value={form.sqlserverInstanceName}
                placeholder={t('dataSources.sqlserverInstance')}
                onChange={(e) => setForm(f => ({ ...f, sqlserverInstanceName: e.target.value }))} />
            </Field>
          </>
        ) : null}
        {isDuckdb ? (
          <>
            <Field label={t('dataSources.duckdbMode')}>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant={form.duckdbMode === 'memory' ? 'default' : 'outline'}
                  onClick={() => setForm(f => ({ ...f, duckdbMode: 'memory' }))}
                >
                  {t('dataSources.duckdbModeMemory')}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant={form.duckdbMode === 'file' ? 'default' : 'outline'}
                  onClick={() => setForm(f => ({ ...f, duckdbMode: 'file' }))}
                >
                  {t('dataSources.duckdbModeFile')}
                </Button>
              </div>
            </Field>
            {form.duckdbMode === 'file' ? (
              <Field label={t('dataSources.duckdbFilePath')}>
                <Input aria-label={t('dataSources.duckdbFilePath')} value={form.database}
                  placeholder={t('dataSources.duckdbFilePathPlaceholder')}
                  onChange={(e) => setForm(f => ({ ...f, database: e.target.value }))} />
              </Field>
            ) : null}
            <Field label="">
              <label className="flex items-center gap-2 text-sm">
                <Checkbox checked={form.duckdbReadOnly}
                  onCheckedChange={(v) => setForm(f => ({ ...f, duckdbReadOnly: v === true }))} />
                <span>{t('dataSources.duckdbReadOnly')}</span>
              </label>
              <span className="ml-2 text-xs text-muted-foreground">{t('dataSources.duckdbReadOnlyHint')}</span>
            </Field>
          </>
        ) : null}
        {!isDuckdb ? (
          <Field label={databaseLabel}>
            <Input aria-label={databaseLabel} value={form.database} placeholder={databasePlaceholder}
              onChange={(e) => setForm(f => ({ ...f, database: e.target.value }))} />
          </Field>
        ) : null}
        {hideHostPort ? null : (
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
