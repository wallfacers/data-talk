import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { DatabaseIcon, PinIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
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
  kind?: string | null
  databaseName?: string | null
}

export type SqlContextConnectionTargets = {
  databases: string[]
  schemas: string[]
}

type SqlContextChipProps = {
  mode: 'session' | 'override'
  context: SqlContextValue | null
  connections: SqlContextConnectionOption[]
  connectionTargetsByConnectionId: Record<string, SqlContextConnectionTargets | undefined>
  onRequestConnectionTargets?: (connectionId: string) => void | Promise<unknown>
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

const EMPTY_SELECT_VALUE = '__empty__'

function sameDraftContext(
  left: ReturnType<typeof toDraftContext>,
  right: ReturnType<typeof toDraftContext>,
) {
  return left.connectionId === right.connectionId
    && left.database === right.database
    && left.schema === right.schema
}

function normalizeConnectionKind(kind: string | null | undefined) {
  const normalized = normalizeValue(kind)
  return normalized ? normalized.toLowerCase() : null
}

function connectionSupportsSchema(kind: string | null | undefined) {
  const normalized = normalizeConnectionKind(kind)
  return normalized === 'postgres' || normalized === 'postgresql'
}

function toSelectValue(value: string | null | undefined) {
  return normalizeValue(value) ?? EMPTY_SELECT_VALUE
}

function fromSelectValue(value: string | null) {
  return value == null || value === EMPTY_SELECT_VALUE ? '' : value
}

export function SqlContextChip({
  mode,
  context,
  connections,
  connectionTargetsByConnectionId,
  onRequestConnectionTargets,
  onSetTabContext,
  onResetTabContext,
}: SqlContextChipProps) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const contextDraft = useMemo(() => toDraftContext(context), [
    context?.connectionId,
    context?.database,
    context?.schema,
  ])
  const [draft, setDraft] = useState(() => contextDraft)
  const lastSyncedContextDraftRef = useRef(contextDraft)
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
  const currentResolvedConnection = contextWithResolvedName
    ? connectionMap.get(contextWithResolvedName.connectionId) ?? null
    : null
  const selectedDraftConnection = draft.connectionId ? connectionMap.get(draft.connectionId) ?? null : null
  const selectedDraftConnectionTargets = draft.connectionId
    ? connectionTargetsByConnectionId[draft.connectionId] ?? null
    : null
  const selectedDraftConnectionMatchesContext = normalizeValue(draft.connectionId) === normalizeValue(context?.connectionId)
  const selectedDraftConnectionLabel = selectedDraftConnection?.name
    ?? normalizeValue(draft.connectionId)
    ?? t('stage.context.value.empty')
  const mergedDatabaseOptions = useMemo(
    () => dedupeValues([
      selectedDraftConnection?.databaseName ?? null,
      ...(selectedDraftConnectionTargets?.databases ?? []),
      selectedDraftConnectionMatchesContext ? context?.database ?? null : null,
      draft.database,
    ]),
    [
      context?.database,
      draft.database,
      selectedDraftConnection?.databaseName,
      selectedDraftConnectionMatchesContext,
      selectedDraftConnectionTargets?.databases,
    ],
  )
  const mergedSchemaOptions = useMemo(
    () => dedupeValues([
      ...(selectedDraftConnectionTargets?.schemas ?? []),
      selectedDraftConnectionMatchesContext ? context?.schema ?? null : null,
      draft.schema,
    ]),
    [context?.schema, draft.schema, selectedDraftConnectionMatchesContext, selectedDraftConnectionTargets?.schemas],
  )
  const currentSchemaVisible = connectionSupportsSchema(currentResolvedConnection?.kind)
    || normalizeValue(context?.schema) != null
  const draftSchemaVisible = connectionSupportsSchema(selectedDraftConnection?.kind ?? currentResolvedConnection?.kind)
    || normalizeValue(draft.schema) != null
  const selectedDatabaseLabel = normalizeValue(draft.database) ?? t('stage.context.value.empty')
  const selectedSchemaLabel = normalizeValue(draft.schema) ?? t('stage.context.value.empty')
  const canApplyDraft = mode === 'session' && draft.connectionId.trim().length > 0
  const summary = formatContextSummary(contextWithResolvedName)
  const tooltipHint = isOverride ? t('stage.context.tooltip.override') : t('stage.context.tooltip.session')

  useEffect(() => {
    setDraft((previous) => {
      const previousSyncedContext = lastSyncedContextDraftRef.current
      lastSyncedContextDraftRef.current = contextDraft
      if (!open || sameDraftContext(previous, previousSyncedContext)) {
        return contextDraft
      }
      return previous
    })
  }, [contextDraft, open])

  useEffect(() => {
    if (!open) return
    const connectionId = normalizeValue(draft.connectionId)
    if (!connectionId) return
    if (selectedDraftConnectionTargets != null) return
    void onRequestConnectionTargets?.(connectionId)
  }, [draft.connectionId, onRequestConnectionTargets, open, selectedDraftConnectionTargets])

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
        database: normalizeValue(nextConnection?.databaseName) ?? '',
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
          {currentSchemaVisible ? (
            <>
              <span className="text-muted-foreground">{t('stage.context.field.schema')}</span>
              <span className="truncate">{context?.schema ?? t('stage.context.value.empty')}</span>
            </>
          ) : null}
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
                  <span className="flex flex-1 text-left">{selectedDraftConnectionLabel}</span>
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
              <Select
                value={toSelectValue(draft.database)}
                onValueChange={(value) => setDraft((prev) => ({ ...prev, database: fromSelectValue(value) }))}
              >
                <SelectTrigger
                  size="sm"
                  id={databaseDatalistId}
                  className="w-full"
                  aria-label={t('stage.context.field.database')}
                >
                  <span className="flex flex-1 text-left">{selectedDatabaseLabel}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={EMPTY_SELECT_VALUE}>
                    {t('stage.context.value.empty')}
                  </SelectItem>
                  {mergedDatabaseOptions.map((value) => (
                    <SelectItem key={value} value={value}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {draftSchemaVisible ? (
              <div className="grid gap-1">
                <label className="text-xs text-muted-foreground" htmlFor={schemaDatalistId}>
                  {t('stage.context.field.schema')}
                </label>
                <Select
                  value={toSelectValue(draft.schema)}
                  onValueChange={(value) => setDraft((prev) => ({ ...prev, schema: fromSelectValue(value) }))}
                >
                  <SelectTrigger
                    size="sm"
                    id={schemaDatalistId}
                    className="w-full"
                    aria-label={t('stage.context.field.schema')}
                  >
                    <span className="flex flex-1 text-left">{selectedSchemaLabel}</span>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={EMPTY_SELECT_VALUE}>
                      {t('stage.context.value.empty')}
                    </SelectItem>
                    {mergedSchemaOptions.map((value) => (
                      <SelectItem key={value} value={value}>
                        {value}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}

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
