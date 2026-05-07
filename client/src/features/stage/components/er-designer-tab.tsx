import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ComponentType } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from '@/components/ui/select'
import { useConnectionStore } from '@/features/connection/store'
import {
  listConnections,
  type Connection,
  getConnectionTargets,
} from '@/services/api/connection'
import type { ConnectionTargetsResponse } from '@/services/api/session-data-context'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErDesignerPayload, JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'
import { useI18n } from '@/i18n/use-i18n'
import { ErDesignerAdapter } from '../adapters/ErDesignerAdapter'
import { coordinator } from '../persistence/stage-persistence-bootstrap'
import { ErCanvas } from './er-canvas/ErCanvas'

type ErDesignerCanvasProps = {
  tabId: string
  mode: 'designer'
  payload: ErDesignerPayload
  onPatch: (ops: JsonPatchOp[]) => void
  onExec: (action: string, params?: unknown) => void
}

type BindTargetDraft = {
  connectionId: string
  database: string | null
  schema: string | null
}

const DesignerCanvas = ErCanvas as unknown as ComponentType<ErDesignerCanvasProps>
const EMPTY_SELECT_VALUE = '__empty__'

function isDesignerViewPath(path: string) {
  return path === '/collapsed'
    || path === '/viewport'
    || path === '/positions'
    || path.startsWith('/positions/')
}

function designerVersion(payload: ErDesignerPayload | null): number {
  const version = (payload as unknown as { __v?: unknown } | null | undefined)?.__v
  return typeof version === 'number' ? version : 0
}

function withCurrentBaseVersion(payload: ErDesignerPayload | null, ops: JsonPatchOp[]): JsonPatchOp[] {
  const baseVersion = designerVersion(payload)
  return ops.map((op) => {
    if (isDesignerViewPath(op.path)) return op
    if (op.baseVersion !== undefined || op.expectedVersion !== undefined) return op
    return { ...op, baseVersion }
  })
}

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
  const seen = new Set<string>()
  for (const value of values) {
    const normalized = normalizeValue(value)
    if (normalized) seen.add(normalized)
  }
  return Array.from(seen)
}

function normalizeConnectionKind(kind: string | null | undefined) {
  const normalized = kind?.trim().toLowerCase()
  if (!normalized) return null
  if (normalized === 'postgres') return 'postgresql'
  if (normalized === 'mssql') return 'sqlserver'
  return normalized
}

function isBindableConnection(dialect: ErDesignerPayload['dialect'], connection: Connection) {
  const kind = normalizeConnectionKind(connection.kind)
  switch (dialect) {
    case 'mysql':
      return kind === 'mysql'
    case 'postgresql':
      return kind === 'postgresql'
    case 'h2':
      return kind === 'h2'
    case 'sqlite':
      return kind === 'sqlite'
    case 'mariadb':
      return kind === 'mariadb' || kind === 'mysql'
    default:
      return false
  }
}

function filterBindableConnections(dialect: ErDesignerPayload['dialect'], connections: Connection[]) {
  return connections.filter((connection) => isBindableConnection(dialect, connection))
}

function pickDefaultConnectionId(
  connections: Connection[],
  currentConnectionId: string | null | undefined,
  activeConnectionId: string | null,
) {
  if (currentConnectionId && connections.some((connection) => connection.id === currentConnectionId)) {
    return currentConnectionId
  }
  if (activeConnectionId && connections.some((connection) => connection.id === activeConnectionId)) {
    return activeConnectionId
  }
  return connections[0]?.id ?? ''
}

function hasIndependentSchemaNamespace(kind: string | null | undefined) {
  const normalizedKind = normalizeConnectionKind(kind)
  return normalizedKind !== 'mysql'
    && normalizedKind !== 'sqlite'
    && normalizedKind !== 'mariadb'
    && normalizedKind !== 'apache_doris'
    && normalizedKind !== 'starrocks'
}

export function ErDesignerTab({ tabId }: { tabId: string }) {
  const { t } = useI18n()
  const payload = useErTabsStore((state) => state.designers.get(tabId) ?? null)
  const activeConnectionId = useConnectionStore((state) => state.activeConnectionId)
  const storedConnections = useConnectionStore((state) => state.connections)
  const setConnections = useConnectionStore((state) => state.setConnections)
  const [bindDialogOpen, setBindDialogOpen] = useState(false)
  const [bindDraft, setBindDraft] = useState<BindTargetDraft>({
    connectionId: '',
    database: null,
    schema: null,
  })
  const [bindConnections, setBindConnections] = useState<Connection[]>([])
  const [bindTargets, setBindTargets] = useState<ConnectionTargetsResponse | null>(null)
  const [isLoadingConnections, setIsLoadingConnections] = useState(false)
  const [isLoadingTargets, setIsLoadingTargets] = useState(false)
  const [isBindingTarget, setIsBindingTarget] = useState(false)

  useEffect(() => {
    void coordinator.ensureHydrated(tabId)
  }, [tabId])

  const adapter = useMemo(() => new ErDesignerAdapter(tabId, () => null), [tabId])
  const selectedConnection = useMemo(
    () => bindConnections.find((connection) => connection.id === bindDraft.connectionId) ?? null,
    [bindConnections, bindDraft.connectionId],
  )
  const showSchemaSelect = hasIndependentSchemaNamespace(selectedConnection?.kind)
  const connectionLabel = selectedConnection?.name
    ?? (isLoadingConnections && bindDraft.connectionId ? t('common.loading') : t('stage.context.value.empty'))
  const databaseLabel = normalizeValue(bindDraft.database) ?? t('stage.context.value.empty')
  const schemaLabel = normalizeValue(bindDraft.schema) ?? t('stage.context.value.empty')
  const databaseOptions = useMemo(
    () => dedupeValues([
      selectedConnection?.databaseName,
      ...(bindTargets?.databases ?? []),
      bindDraft.database,
    ]),
    [bindTargets?.databases, bindDraft.database, selectedConnection?.databaseName],
  )
  const schemaOptions = useMemo(
    () => dedupeValues([
      ...(bindTargets?.schemas ?? []),
      bindDraft.schema,
    ]),
    [bindTargets?.schemas, bindDraft.schema],
  )

  const onPatch = useCallback((ops: JsonPatchOp[]) => {
    const latestPayload = useErTabsStore.getState().designers.get(tabId) ?? null
    void adapter.patch(withCurrentBaseVersion(latestPayload, ops))
  }, [adapter, tabId])

  const executeAction = useCallback(async (action: string, params?: unknown) => {
    const result = await adapter.exec(action, params)
    if (!result.success) {
      toast.error(result.error ?? t('workspace.loadFailed'))
    }
    return result
  }, [adapter, t])

  const loadBindableConnections = useCallback(async () => {
    if (!payload) return
    setIsLoadingConnections(true)
    try {
      const nextConnections = await listConnections()
      setConnections(nextConnections)
      const compatibleConnections = filterBindableConnections(payload.dialect, nextConnections)
      setBindConnections(compatibleConnections)
      setBindDraft((current) => {
        const connectionId = pickDefaultConnectionId(
          compatibleConnections,
          current.connectionId || payload.targetConnectionId,
          activeConnectionId,
        )
        const defaultConnection = compatibleConnections.find((connection) => connection.id === connectionId) ?? null
        return {
          connectionId,
          database: current.database ?? payload.targetDatabase ?? normalizeValue(defaultConnection?.databaseName),
          schema: current.schema ?? payload.targetSchema ?? null,
        }
      })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('workspace.loadFailed'))
    } finally {
      setIsLoadingConnections(false)
    }
  }, [activeConnectionId, payload, setConnections, t])

  const openBindTargetDialog = useCallback(() => {
    if (!payload) return
    const initialConnections = filterBindableConnections(payload.dialect, storedConnections)
    const initialConnectionId = pickDefaultConnectionId(
      initialConnections,
      payload.targetConnectionId,
      activeConnectionId,
    )
    const initialConnection = initialConnections.find((connection) => connection.id === initialConnectionId) ?? null
    setBindConnections(initialConnections)
    setBindTargets(null)
    setBindDraft({
      connectionId: initialConnectionId,
      database: payload.targetDatabase ?? normalizeValue(initialConnection?.databaseName),
      schema: payload.targetSchema ?? null,
    })
    setBindDialogOpen(true)
    void loadBindableConnections()
  }, [activeConnectionId, loadBindableConnections, payload, storedConnections])

  useEffect(() => {
    if (!bindDialogOpen) return
    if (!bindDraft.connectionId) {
      setBindTargets(null)
      return
    }

    let cancelled = false
    setIsLoadingTargets(true)
    void getConnectionTargets(bindDraft.connectionId)
      .then((targets) => {
        if (cancelled) return
        setBindTargets(targets)
        setBindDraft((current) => {
          if (current.connectionId !== bindDraft.connectionId) return current
          const currentConnection = bindConnections.find((connection) => connection.id === current.connectionId) ?? null
          return {
            connectionId: current.connectionId,
            database: current.database ?? normalizeValue(currentConnection?.databaseName),
            schema: showSchemaSelect ? current.schema : null,
          }
        })
      })
      .catch((error) => {
        if (cancelled) return
        setBindTargets(null)
        toast.error(error instanceof Error ? error.message : t('workspace.loadFailed'))
      })
      .finally(() => {
        if (!cancelled) setIsLoadingTargets(false)
      })

    return () => {
      cancelled = true
    }
  }, [bindConnections, bindDialogOpen, bindDraft.connectionId, showSchemaSelect, t])

  const handleBindTargetConfirm = useCallback(async () => {
    const connectionId = normalizeValue(bindDraft.connectionId)
    if (!connectionId) return
    setIsBindingTarget(true)
    try {
      const result = await executeAction('bind_target', {
        connectionId,
        database: normalizeValue(bindDraft.database),
        schema: showSchemaSelect ? normalizeValue(bindDraft.schema) : null,
      })
      if (result.success) {
        setBindDialogOpen(false)
      }
    } finally {
      setIsBindingTarget(false)
    }
  }, [bindDraft.connectionId, bindDraft.database, bindDraft.schema, executeAction, showSchemaSelect])

  const onExec = useCallback((action: string, params?: unknown) => {
    if (action === 'bind_target' && params == null) {
      openBindTargetDialog()
      return
    }
    void executeAction(action, params)
  }, [executeAction, openBindTargetDialog])

  if (!payload) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-text-muted" data-er-tab-id={tabId}>
        {t('erCanvas.loading')}
      </div>
    )
  }

  return (
    <>
      <DesignerCanvas
        tabId={tabId}
        mode="designer"
        payload={payload}
        onPatch={onPatch}
        onExec={onExec}
      />
      <Dialog open={bindDialogOpen} onOpenChange={setBindDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('erCanvas.bindDialog.title')}</DialogTitle>
            <DialogDescription>{t('erCanvas.bindDialog.description')}</DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-2">
            {bindConnections.length === 0 && !isLoadingConnections ? (
              <p className="text-sm text-text-muted">
                {t('erCanvas.bindDialog.empty')}
              </p>
            ) : null}

            <div className="grid gap-1.5">
              <Label htmlFor="er-bind-target-connection">{t('stage.context.field.connection')}</Label>
              <Select
                value={toSelectValue(bindDraft.connectionId)}
                onValueChange={(value) => {
                  const connectionId = fromSelectValue(value) ?? ''
                  const connection = bindConnections.find((candidate) => candidate.id === connectionId) ?? null
                  setBindTargets(null)
                  setBindDraft({
                    connectionId,
                    database: normalizeValue(connection?.databaseName),
                    schema: null,
                  })
                }}
              >
                <SelectTrigger
                  id="er-bind-target-connection"
                  aria-label={t('stage.context.field.connection')}
                  disabled={isLoadingConnections}
                  className="w-full"
                >
                  <span className="flex flex-1 text-left">{connectionLabel}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={EMPTY_SELECT_VALUE}>{t('stage.context.value.empty')}</SelectItem>
                  {bindConnections.map((connection) => (
                    <SelectItem key={connection.id} value={connection.id}>
                      {connection.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="er-bind-target-database">{t('stage.context.field.database')}</Label>
              <Select
                value={toSelectValue(bindDraft.database)}
                onValueChange={(value) => {
                  setBindDraft((current) => ({ ...current, database: fromSelectValue(value) }))
                }}
              >
                <SelectTrigger
                  id="er-bind-target-database"
                  aria-label={t('stage.context.field.database')}
                  disabled={isLoadingTargets || !bindDraft.connectionId}
                  className="w-full"
                >
                  <span className="flex flex-1 text-left">{databaseLabel}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={EMPTY_SELECT_VALUE}>{t('stage.context.value.empty')}</SelectItem>
                  {databaseOptions.map((database) => (
                    <SelectItem key={database} value={database}>
                      {database}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {showSchemaSelect ? (
              <div className="grid gap-1.5">
                <Label htmlFor="er-bind-target-schema">{t('stage.context.field.schema')}</Label>
                <Select
                  value={toSelectValue(bindDraft.schema)}
                  onValueChange={(value) => {
                    setBindDraft((current) => ({ ...current, schema: fromSelectValue(value) }))
                  }}
                >
                  <SelectTrigger
                    id="er-bind-target-schema"
                    aria-label={t('stage.context.field.schema')}
                    disabled={isLoadingTargets || !bindDraft.connectionId}
                    className="w-full"
                  >
                    <span className="flex flex-1 text-left">{schemaLabel}</span>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={EMPTY_SELECT_VALUE}>{t('stage.context.value.empty')}</SelectItem>
                    {schemaOptions.map((schema) => (
                      <SelectItem key={schema} value={schema}>
                        {schema}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setBindDialogOpen(false)}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="button"
              onClick={() => void handleBindTargetConfirm()}
              disabled={isBindingTarget || !bindDraft.connectionId}
            >
              {t('erCanvas.toolbar.bindTarget')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
