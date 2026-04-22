import { useEffect, useId, useMemo, useState } from 'react'
import { DatabaseIcon, PinIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useI18n } from '@/i18n/use-i18n'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

export type SqlContextValue = {
  connectionId: string
  connectionName: string | null
  database: string | null
  schema: string | null
}

export type SqlContextConnectionOption = {
  id: string
  name: string
  databaseName?: string | null
}

type SqlContextChipProps = {
  mode: 'session' | 'override'
  context: SqlContextValue | null
  connections: SqlContextConnectionOption[]
  databaseOptions: string[]
  schemaOptions: string[]
  onSetTabContext: (context: SqlContextValue) => void
  onResetTabContext: () => void
}

function formatContextSummary(context: SqlContextValue | null) {
  if (!context) return null
  return [context.connectionName ?? context.connectionId, context.database, context.schema]
    .filter(Boolean)
    .join(' / ')
}

function normalizeValue(value: string | null | undefined) {
  if (value == null) return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function dedupeValues(values: Array<string | null | undefined>) {
  const set = new Set<string>()
  for (const value of values) {
    const normalized = normalizeValue(value)
    if (normalized) {
      set.add(normalized)
    }
  }
  return Array.from(set)
}

function toDraftContext(context: SqlContextValue | null) {
  return {
    connectionId: context?.connectionId ?? '',
    database: context?.database ?? '',
    schema: context?.schema ?? '',
  }
}

export function SqlContextChip({
  mode,
  context,
  connections,
  databaseOptions,
  schemaOptions,
  onSetTabContext,
  onResetTabContext,
}: SqlContextChipProps) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(() => toDraftContext(context))
  const databaseDatalistId = useId()
  const schemaDatalistId = useId()
  const isOverride = mode === 'override'
  const connectionMap = useMemo(
    () => new Map(connections.map((connection) => [connection.id, connection])),
    [connections],
  )
  const contextWithResolvedName = useMemo(() => {
    if (!context) return null
    const fallbackName = connectionMap.get(context.connectionId)?.name ?? null
    const resolvedName = context.connectionName ?? fallbackName
    if (resolvedName === context.connectionName) {
      return context
    }
    return {
      ...context,
      connectionName: resolvedName,
    }
  }, [connectionMap, context])
  const selectedDraftConnection = draft.connectionId ? connectionMap.get(draft.connectionId) ?? null : null
  const mergedDatabaseOptions = useMemo(
    () => dedupeValues([
      selectedDraftConnection?.databaseName ?? null,
      ...databaseOptions,
      context?.database ?? null,
      draft.database,
    ]),
    [context?.database, databaseOptions, draft.database, selectedDraftConnection?.databaseName],
  )
  const mergedSchemaOptions = useMemo(
    () => dedupeValues([...schemaOptions, context?.schema ?? null, draft.schema]),
    [context?.schema, draft.schema, schemaOptions],
  )
  const canApplyDraft = mode === 'session' && draft.connectionId.trim().length > 0
  const summary = formatContextSummary(contextWithResolvedName)
  const tooltipHint = isOverride ? t('stage.context.tooltip.override') : t('stage.context.tooltip.session')

  useEffect(() => {
    if (open) return
    setDraft(toDraftContext(context))
  }, [context, open])

  function handlePinCurrentContext() {
    if (!contextWithResolvedName) return
    onSetTabContext(contextWithResolvedName)
    setOpen(false)
  }

  function handleApplyDraftOverride() {
    const connectionId = normalizeValue(draft.connectionId)
    if (!connectionId) return
    const resolved = connectionMap.get(connectionId)
    onSetTabContext({
      connectionId,
      connectionName: resolved?.name ?? contextWithResolvedName?.connectionName ?? null,
      database: normalizeValue(draft.database),
      schema: normalizeValue(draft.schema),
    })
    setOpen(false)
  }

  function handleConnectionChange(nextConnectionId: string | null) {
    const nextId = nextConnectionId ?? ''
    setDraft((previous) => {
      if (previous.connectionId === nextId) return previous
      const nextConnection = connectionMap.get(nextId)
      return {
        connectionId: nextId,
        database: normalizeValue(previous.database) ?? normalizeValue(nextConnection?.databaseName) ?? '',
        schema: '',
      }
    })
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <Tooltip>
        <TooltipTrigger
          render={
            <PopoverTrigger
              render={
                <Button
                  variant={isOverride ? 'secondary' : 'outline'}
                  size="icon-sm"
                  className="relative"
                  aria-label={t('stage.context.tooltip.button')}
                >
                  <DatabaseIcon className="size-3.5" />
                  {isOverride ? (
                    <PinIcon className="absolute -right-0.5 -top-0.5 size-3 text-primary" />
                  ) : null}
                </Button>
              }
            />
          }
        />
        <TooltipContent side="bottom" sideOffset={4}>
          <div className="space-y-1">
            <p>{tooltipHint}</p>
            <p className="text-[11px] opacity-80">{summary ?? t('stage.context.value.empty')}</p>
          </div>
        </TooltipContent>
      </Tooltip>

      <PopoverContent className="w-[340px] space-y-3 p-3">
        <div className="space-y-1">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-medium">{t('stage.context.panel.title')}</span>
            <Badge variant={isOverride ? 'default' : 'outline'}>
              {isOverride ? t('stage.context.label.override') : t('stage.context.label.session')}
            </Badge>
          </div>
          <p className="text-[11px] text-muted-foreground">{tooltipHint}</p>
        </div>

        <div className="grid grid-cols-[88px,1fr] gap-x-2 gap-y-1 text-xs">
          <span className="text-muted-foreground">{t('stage.context.field.connection')}</span>
          <span className="truncate">
            {contextWithResolvedName?.connectionName ?? contextWithResolvedName?.connectionId ?? t('stage.context.value.empty')}
          </span>
          <span className="text-muted-foreground">{t('stage.context.field.database')}</span>
          <span className="truncate">{context?.database ?? t('stage.context.value.empty')}</span>
          <span className="text-muted-foreground">{t('stage.context.field.schema')}</span>
          <span className="truncate">{context?.schema ?? t('stage.context.value.empty')}</span>
        </div>

        {isOverride ? (
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="w-full justify-center"
            onClick={() => {
              onResetTabContext()
              setOpen(false)
            }}
          >
            {t('stage.context.action.useSession')}
          </Button>
        ) : (
          <div className="space-y-2">
            <p className="text-[11px] text-muted-foreground">{t('stage.context.panel.editHint')}</p>

            <div className="grid gap-1">
              <label className="text-xs text-muted-foreground" htmlFor={`${databaseDatalistId}-connection`}>
                {t('stage.context.field.connection')}
              </label>
              <Select value={draft.connectionId} onValueChange={handleConnectionChange}>
                <SelectTrigger
                  size="sm"
                  id={`${databaseDatalistId}-connection`}
                  className="w-full"
                  aria-label={t('stage.context.field.connection')}
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {connections.map((connection) => (
                    <SelectItem key={connection.id} value={connection.id}>
                      {connection.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1">
              <label className="text-xs text-muted-foreground" htmlFor={databaseDatalistId}>
                {t('stage.context.field.database')}
              </label>
              <Input
                id={databaseDatalistId}
                value={draft.database}
                list={`${databaseDatalistId}-list`}
                placeholder={t('stage.context.value.empty')}
                className="h-7 text-xs"
                onChange={(event) => setDraft((prev) => ({ ...prev, database: event.target.value }))}
              />
              <datalist id={`${databaseDatalistId}-list`}>
                {mergedDatabaseOptions.map((value) => (
                  <option key={value} value={value} />
                ))}
              </datalist>
            </div>

            <div className="grid gap-1">
              <label className="text-xs text-muted-foreground" htmlFor={schemaDatalistId}>
                {t('stage.context.field.schema')}
              </label>
              <Input
                id={schemaDatalistId}
                value={draft.schema}
                list={`${schemaDatalistId}-list`}
                placeholder={t('stage.context.value.empty')}
                className="h-7 text-xs"
                onChange={(event) => setDraft((prev) => ({ ...prev, schema: event.target.value }))}
              />
              <datalist id={`${schemaDatalistId}-list`}>
                {mergedSchemaOptions.map((value) => (
                  <option key={value} value={value} />
                ))}
              </datalist>
            </div>

            <div className="flex items-center justify-between gap-2 pt-1">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={handlePinCurrentContext}
                disabled={!context}
              >
                {t('stage.context.action.pinCurrent')}
              </Button>
              <Button
                type="button"
                size="sm"
                onClick={handleApplyDraftOverride}
                disabled={!canApplyDraft}
              >
                {t('stage.context.action.applyOverride')}
              </Button>
            </div>
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}
