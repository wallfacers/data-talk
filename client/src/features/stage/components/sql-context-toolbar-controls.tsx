import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useI18n } from '@/i18n/use-i18n'
import { SqlLimitSelect, type SqlLimitValue } from './sql-limit-select'

export type SqlContextValue = {
  connectionId: string
  connectionName: string | null
  database: string | null
  schema: string | null
}

export type SqlContextConnectionOption = {
  id: string
  name: string
  kind?: string | null
  databaseName?: string | null
}

export type SqlContextConnectionTargets = {
  databases: string[]
  schemas: string[]
}

export type SqlContextToolbarControlsProps = {
  useSessionContext: boolean
  context: SqlContextValue | null
  connections: SqlContextConnectionOption[]
  targets: SqlContextConnectionTargets | null
  limit: SqlLimitValue
  onUseSessionContextChange: (value: boolean) => void
  onConnectionChange: (connectionId: string) => void
  onDatabaseChange: (database: string | null) => void
  onSchemaChange: (schema: string | null) => void
  onLimitChange: (value: SqlLimitValue) => void
  onOpenConnections: () => Promise<void>
  onOpenTargets: () => Promise<void>
}

const EMPTY_SELECT_VALUE = '__empty__'

function normalizeValue(value: string | null | undefined) {
  if (value == null) return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function toSelectValue(value: string | null | undefined) {
  return normalizeValue(value) ?? EMPTY_SELECT_VALUE
}

function fromSelectValue(value: string | null) {
  return value == null || value === EMPTY_SELECT_VALUE ? null : value
}

function dedupeValues(values: Array<string | null | undefined>) {
  const normalizedValues = new Set<string>()
  for (const value of values) {
    const normalized = normalizeValue(value)
    if (normalized) {
      normalizedValues.add(normalized)
    }
  }
  return Array.from(normalizedValues)
}

export function SqlContextToolbarControls({
  useSessionContext,
  context,
  connections,
  targets,
  limit,
  onUseSessionContextChange,
  onConnectionChange,
  onDatabaseChange,
  onSchemaChange,
  onLimitChange,
  onOpenConnections,
  onOpenTargets,
}: SqlContextToolbarControlsProps) {
  const { t } = useI18n()
  const selectedConnection = context?.connectionId
    ? connections.find((connection) => connection.id === context.connectionId) ?? null
    : null
  const connectionValue = context?.connectionId ?? EMPTY_SELECT_VALUE
  const connectionLabel = context?.connectionName
    ?? selectedConnection?.name
    ?? normalizeValue(context?.connectionId)
    ?? t('stage.context.value.empty')
  const databaseOptions = dedupeValues([
    selectedConnection?.databaseName,
    ...(targets?.databases ?? []),
    context?.database,
  ])
  const schemaOptions = dedupeValues([
    ...(targets?.schemas ?? []),
    context?.schema,
  ])
  const databaseLabel = normalizeValue(context?.database) ?? t('stage.context.value.empty')
  const schemaLabel = normalizeValue(context?.schema) ?? t('stage.context.value.empty')

  function refreshConnectionsOnOpen(open: boolean) {
    if (!open) return
    void onOpenConnections().catch(() => {
      toast.error(t('stage.context.toast.connectionsRefreshFailed'))
    })
  }

  function refreshTargetsOnOpen(open: boolean) {
    if (!open) return
    if (!context?.connectionId) return
    void onOpenTargets().catch(() => {
      toast.error(t('stage.context.toast.targetsRefreshFailed'))
    })
  }

  return (
    <div
      data-testid="sql-context-toolbar-controls"
      className="flex flex-wrap items-center justify-end gap-2"
    >
      <div className="flex h-7 items-center gap-2 rounded-md border border-border/60 px-2">
        <Label
          id="sql-context-use-session-label"
          className="text-xs font-medium text-muted-foreground"
        >
          {t('stage.context.toolbar.useSession')}
        </Label>
        <Switch
          size="sm"
          checked={useSessionContext}
          onCheckedChange={(value) => onUseSessionContextChange(value)}
          aria-labelledby="sql-context-use-session-label"
        />
      </div>

      <ToolbarSelectFrame label={t('stage.context.field.connection')}>
        <Select
          value={connectionValue}
          disabled={useSessionContext}
          onOpenChange={refreshConnectionsOnOpen}
          onValueChange={(value) => {
            const connectionId = normalizeValue(value)
            if (connectionId && connectionId !== EMPTY_SELECT_VALUE) {
              onConnectionChange(connectionId)
            }
          }}
        >
          <SelectTrigger
            size="sm"
            aria-label={t('stage.context.field.connection')}
            className="w-36"
          >
            <span className="flex flex-1 text-left">{connectionLabel}</span>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={EMPTY_SELECT_VALUE}>
              {t('stage.context.value.empty')}
            </SelectItem>
            {connections.map((connection) => (
              <SelectItem key={connection.id} value={connection.id}>
                {connection.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </ToolbarSelectFrame>

      <ToolbarSelectFrame label={t('stage.context.field.database')}>
        <Select
          value={toSelectValue(context?.database)}
          disabled={useSessionContext}
          onOpenChange={refreshTargetsOnOpen}
          onValueChange={(value) => onDatabaseChange(fromSelectValue(value))}
        >
          <SelectTrigger
            size="sm"
            aria-label={t('stage.context.field.database')}
            className="w-32"
          >
            <span className="flex flex-1 text-left">{databaseLabel}</span>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={EMPTY_SELECT_VALUE}>
              {t('stage.context.value.empty')}
            </SelectItem>
            {databaseOptions.map((database) => (
              <SelectItem key={database} value={database}>
                {database}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </ToolbarSelectFrame>

      <ToolbarSelectFrame label={t('stage.context.field.schema')}>
        <Select
          value={toSelectValue(context?.schema)}
          disabled={useSessionContext}
          onOpenChange={refreshTargetsOnOpen}
          onValueChange={(value) => onSchemaChange(fromSelectValue(value))}
        >
          <SelectTrigger
            size="sm"
            aria-label={t('stage.context.field.schema')}
            className="w-28"
          >
            <span className="flex flex-1 text-left">{schemaLabel}</span>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={EMPTY_SELECT_VALUE}>
              {t('stage.context.value.empty')}
            </SelectItem>
            {schemaOptions.map((schema) => (
              <SelectItem key={schema} value={schema}>
                {schema}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </ToolbarSelectFrame>

      <ToolbarSelectFrame label={t('stage.limit.toolbarLabel')}>
        <SqlLimitSelect
          value={limit}
          onValueChange={onLimitChange}
          ariaLabel={t('stage.limit.toolbarLabel')}
          triggerClassName="w-28"
        />
      </ToolbarSelectFrame>
    </div>
  )
}

function ToolbarSelectFrame({
  label,
  children,
}: {
  label: string
  children: ReactNode
}) {
  return (
    <div className="flex h-7 items-center gap-1.5">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      {children}
    </div>
  )
}
