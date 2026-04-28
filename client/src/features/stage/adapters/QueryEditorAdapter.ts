import type { ActionDef, ExecResult, JsonPatchOp, PatchCapability, PatchResult, UIObject } from '@/services/ui-router'
import { execError, patchError } from '@/services/ui-router'
import { useConnectionStore } from '@/features/connection/store'
import { useStageStore } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from '@/features/stage/utils/normalize-query-editor-payload'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
import { useSessionStore } from '@/stores/session-store'
import { formatQueryEditorSql, runQueryEditorSql, setQueryEditorContext } from '@/features/stage/utils/query-editor-actions'
import { resolveTabDataContext } from '@/features/stage/utils/resolve-tab-data-context'

type ContentPatchOp = JsonPatchOp & { baseVersion?: number }

const ACTIONS: ActionDef[] = [
  {
    name: 'apply_text_edits',
    description: 'Apply versioned text edits to the SQL content',
    paramsSchema: {
      type: 'object',
      required: ['baseVersion', 'edits'],
      properties: {
        baseVersion: { type: 'number' },
        edits: {
          type: 'array',
          items: {
            type: 'object',
            required: ['range', 'text', 'expectedText'],
            properties: {
              range: { type: 'object' },
              text: { type: 'string' },
              expectedText: { type: 'string' },
            },
          },
        },
      },
    },
  },
  {
    name: 'set_context',
    description: 'Set the query execution context',
    paramsSchema: {
      type: 'object',
      properties: {
        connectionId: { type: ['string', 'null'] },
        database: { type: ['string', 'null'] },
        schema: { type: ['string', 'null'] },
      },
    },
  },
  {
    name: 'run_sql',
    description: 'Run the current SQL',
    paramsSchema: {
      type: 'object',
      properties: {
        limit: { type: ['number', 'null'] },
      },
    },
  },
  {
    name: 'format_sql',
    description: 'Format the current SQL',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'focus',
    description: 'Focus this query editor',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'close',
    description: 'Close this query editor',
    paramsSchema: { type: 'object', properties: {} },
  },
]

const PATCH_CAPABILITIES: PatchCapability[] = [
  { pathPattern: '/content', ops: ['replace'] },
  { pathPattern: '/connectionId', ops: ['replace'] },
  { pathPattern: '/database', ops: ['replace'] },
  { pathPattern: '/schema', ops: ['replace'] },
]

const CAPABILITIES = {
  editableContent: true,
  acceptsTextEdits: true,
  runnable: true,
  formattable: true,
  supportsContextBinding: true,
  supportsResults: true,
} as const

function clearSessionActiveTab(sessionId: string | null) {
  if (!sessionId) return
  useStageStore.getState().focusWorkspaceTabForSession(sessionId)
}

function isReplaceValue(value: unknown): value is string | null {
  return typeof value === 'string' || value === null
}

function hasBaseVersion(op: ContentPatchOp): op is ContentPatchOp & { baseVersion: number } {
  return typeof op.baseVersion === 'number'
}

function summarizeResult(result: {
  resultId: string
  statementIndex: number
  columns: string[]
  rowCount: number
  executionMs: number
  truncated: boolean
  errorMessage?: string | null
}) {
  const summary: {
    resultId: string
    statementIndex: number
    columns: string[]
    rowCount: number
    durationMs: number
    truncated: boolean
    error?: { message: string } | null
  } = {
    resultId: result.resultId,
    statementIndex: result.statementIndex,
    columns: result.columns,
    rowCount: result.rowCount,
    durationMs: result.executionMs,
    truncated: result.truncated,
  }
  if (result.errorMessage) {
    summary.error = { message: result.errorMessage }
  }
  return summary
}

export class QueryEditorAdapter implements UIObject {
  type = 'query_editor'
  patchCapabilities = PATCH_CAPABILITIES

  constructor(
    public objectId: string,
    private getSessionId: () => string | null = () => null,
  ) {}

  get tabId() {
    return this.objectId
  }

  get title() {
    return this.getTab()?.title ?? 'Query Editor'
  }

  get connectionId() {
    return this.getResolvedState().effectiveContext.connectionId ?? undefined
  }

  get database() {
    return this.getResolvedState().effectiveContext.database ?? undefined
  }

  private getTab() {
    const state = useStageStore.getState()
    return state.workspaceTabs.find((tab) => tab.tabId === this.objectId)
      ?? Array.from(state.tabsBySession.values()).flat().find((tab) => tab.tabId === this.objectId)
      ?? null
  }

  private getResolvedState() {
    const tab = this.getTab()
    const payload = normalizeQueryEditorPayload(tab?.payload)
    const workbenchTab = useSqlWorkbenchStore.getState().tabsById[this.objectId]
    const sessionId = tab?.originSessionId ?? this.getSessionId() ?? null
    const sessionContext = sessionId
      ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null
      : null
    const connectionState = useConnectionStore.getState()
    const resolvedContext = resolveTabDataContext(
      {
        originSessionId: sessionId,
        connectionId: payload.connectionId ?? tab?.connectionId ?? null,
        connectionName: payload.connectionName ?? tab?.connectionName ?? null,
        database: payload.database ?? tab?.database ?? null,
        schema: payload.schema ?? tab?.schema ?? null,
      },
      sessionContext,
      {
        inheritSessionContext: true,
        fallbackConnectionId: connectionState.activeConnectionId ?? null,
        connectionNameLookup: (connectionId) =>
          connectionState.connections.find((connection) => connection.id === connectionId)?.name ?? null,
      },
    )
    const resolvedExecutionContext = workbenchTab?.resolvedContext
      ? {
          sessionId: resolvedContext.sessionId ?? sessionId,
          connectionId: workbenchTab.resolvedContext.connectionId,
          connectionName: workbenchTab.resolvedContext.connectionName,
          database: workbenchTab.resolvedContext.database,
          schema: workbenchTab.resolvedContext.schema,
        }
      : {
          sessionId: resolvedContext.sessionId ?? sessionId,
          connectionId: resolvedContext.connectionId,
          connectionName: resolvedContext.connectionName,
          database: resolvedContext.database,
          schema: resolvedContext.schema,
        }
    const hydratedOverride = workbenchTab?.override ?? (payload.contextOverride
      ? {
          connectionId: payload.contextOverride.connectionId,
          connectionName: connectionState.connections.find(
            (connection) => connection.id === payload.contextOverride?.connectionId,
          )?.name ?? null,
          database: payload.contextOverride.database,
          schema: payload.contextOverride.schema,
          source: 'open_payload' as const,
          setAt: 0,
        }
      : null)
    const effectiveContext = hydratedOverride
      ? {
          sessionId: resolvedExecutionContext.sessionId ?? sessionId,
          connectionId: hydratedOverride.connectionId,
          connectionName: hydratedOverride.connectionName
            ?? (hydratedOverride.connectionId === resolvedExecutionContext.connectionId
              ? resolvedExecutionContext.connectionName
              : null),
          database: hydratedOverride.database ?? resolvedExecutionContext.database,
          schema: hydratedOverride.schema ?? resolvedExecutionContext.schema,
        }
      : {
          sessionId: resolvedExecutionContext.sessionId ?? sessionId,
          connectionId: resolvedExecutionContext.connectionId,
          connectionName: resolvedExecutionContext.connectionName,
          database: resolvedExecutionContext.database,
          schema: resolvedExecutionContext.schema,
        }

    return {
      tab,
      payload,
      workbenchTab,
      hydratedOverride,
      effectiveContext,
    }
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const { tab, payload, workbenchTab, hydratedOverride, effectiveContext } = this.getResolvedState()
    const fallbackResults = payload.lastRun
      ? [{
          resultId: 'last-run',
          statementIndex: 0,
          columns: payload.lastRun.columns,
          rowCount: payload.lastRun.rowCount,
          durationMs: payload.lastRun.executionMs,
          truncated: payload.lastRun.truncated,
        }]
      : []
    const state = {
      tabId: this.objectId,
      title: tab?.title ?? 'Query Editor',
      content: workbenchTab?.sqlText ?? payload.initialSql,
      language: 'sql' as const,
      version: workbenchTab?.version ?? 1,
      dirty: workbenchTab ? workbenchTab.sqlText !== workbenchTab.savedSqlText : false,
      cursor: workbenchTab?.cursor ?? { line: 1, column: 1 },
      selection: workbenchTab?.selection ?? null,
      connectionId: effectiveContext.connectionId,
      connectionName: effectiveContext.connectionName,
      database: effectiveContext.database,
      schema: effectiveContext.schema,
      contextOverride: hydratedOverride,
      entryMode: payload.entryMode,
      autoRun: payload.autoRun,
      executeStatus: workbenchTab?.executeStatus ?? (payload.lastRun ? 'success' : 'idle'),
      results: workbenchTab?.results.map(summarizeResult) ?? fallbackResults,
      activeResultId: workbenchTab?.activeResultId ?? (payload.lastRun ? 'last-run' : null),
      limit: workbenchTab?.limit ?? 100,
      inWorkset: tab !== null,
    }

    switch (mode) {
      case 'state':
        return state
      case 'actions':
        return ACTIONS
      case 'schema':
        return {
          type: 'object',
          properties: {
            tabId: { type: 'string' },
            title: { type: 'string' },
            content: { type: 'string' },
            language: { type: 'string' },
            version: { type: 'number' },
            dirty: { type: 'boolean' },
            cursor: { type: 'object' },
            selection: { type: ['object', 'null'] },
            connectionId: { type: ['string', 'null'] },
            connectionName: { type: ['string', 'null'] },
            database: { type: ['string', 'null'] },
            schema: { type: ['string', 'null'] },
            contextOverride: { type: ['object', 'null'] },
            entryMode: { type: 'string' },
            autoRun: { type: 'boolean' },
            executeStatus: { type: 'string' },
            results: { type: 'array' },
            activeResultId: { type: ['string', 'null'] },
            limit: { type: ['number', 'null'] },
            inWorkset: { type: 'boolean' },
          },
        }
      case 'full':
        return { state, schema: this.read('schema'), actions: ACTIONS, capabilities: CAPABILITIES }
    }
  }

  patch(ops: ContentPatchOp[] = [], _reason?: string): PatchResult {
    for (const op of ops) {
      if (op.op !== 'replace') {
        return patchError(`Unsupported patch op: ${op.op}`, 'Only replace is supported on query_editor')
      }

      switch (op.path) {
        case '/content': {
          if (typeof op.value !== 'string') {
            return patchError('Invalid /content value', 'Expected a string')
          }
          if (!hasBaseVersion(op)) {
            const { payload, workbenchTab } = this.getResolvedState()
            return patchError({
              code: 'version_conflict',
              message: 'Patch /content requires baseVersion',
              hint: "Re-read with `ui_read(mode='state')` to get the latest content and version, then retry with a fresh baseVersion.",
              currentState: {
                tabId: this.objectId,
                version: workbenchTab?.version ?? 1,
                content: workbenchTab?.sqlText ?? payload.initialSql,
              },
            })
          }
          const result = useStageStore.getState().replaceQueryEditorContent(this.objectId, op.value, op.baseVersion)
          if (!result.ok) {
            return patchError({
              code: result.code,
              message: `Editor content has advanced to version ${result.currentState.version}`,
              hint: "Re-read with `ui_read(mode='state')` to get the latest content and version, then retry with a fresh baseVersion.",
              currentState: {
                tabId: this.objectId,
                ...result.currentState,
              },
            })
          }
          break
        }
        case '/connectionId':
        case '/database':
        case '/schema': {
          if (!isReplaceValue(op.value)) {
            return patchError(`Invalid ${op.path} value`, 'Expected a string or null')
          }
          const field = op.path.slice(1) as 'connectionId' | 'database' | 'schema'
          setQueryEditorContext({
            tabId: this.objectId,
            [field]: op.value,
          })
          break
        }
        default:
          return patchError(`Unsupported patch path: ${op.path}`, 'Supported paths: [/content, /connectionId, /database, /schema]')
      }
    }

    return { status: 'applied' }
  }

  async exec(action: string, params?: unknown): Promise<ExecResult> {
    const store = useStageStore.getState()
    const tab = this.getTab()
    const p = (params ?? {}) as {
      baseVersion?: number
      edits?: Array<{
        range: {
          startLine: number
          startColumn: number
          endLine: number
          endColumn: number
        }
        text: string
        expectedText: string
      }>
      connectionId?: string | null
      database?: string | null
      schema?: string | null
      limit?: 10 | 100 | 1000 | null
    }

    switch (action) {
      case 'apply_text_edits': {
        if (
          typeof p.baseVersion !== 'number'
          || !Array.isArray(p.edits)
          || p.edits.some((edit) => typeof edit?.expectedText !== 'string')
        ) {
          return execError('Invalid params for apply_text_edits')
        }
        const result = store.applyQueryEditorTextEdits(this.objectId, {
          baseVersion: p.baseVersion,
          edits: p.edits,
        })
        if (!result.ok) {
          if (result.code === 'expected_text_mismatch') {
            return execError({
              code: result.code,
              message: `The expected text for edit ${result.details.editIndex} no longer matches the current content`,
              hint: "Re-read with `ui_read(mode='state')` to get the latest content and version, then recompute the edit against the current text.",
              currentState: {
                tabId: this.objectId,
                ...result.currentState,
              },
              details: result.details,
            } as Parameters<typeof execError>[0])
          }
          return execError({
            code: result.code,
            message: `Editor content has advanced to version ${result.currentState.version}`,
            hint: "Re-read with `ui_read(mode='state')` to get the latest content and version, then retry with a fresh baseVersion.",
            currentState: {
              tabId: this.objectId,
              ...result.currentState,
            },
          })
        }
        return { success: true, data: result }
      }
      case 'set_context': {
        if (p.connectionId === undefined && p.database === undefined && p.schema === undefined) {
          return execError('set_context requires at least one of connectionId, database, schema')
        }
        setQueryEditorContext({
          tabId: this.objectId,
          connectionId: p.connectionId,
          database: p.database,
          schema: p.schema,
        })
        return { success: true }
      }
      case 'run_sql':
        return {
          success: true,
          data: await runQueryEditorSql({
            tabId: this.objectId,
            sessionId: tab?.originSessionId ?? this.getSessionId(),
            limit: p.limit,
          }),
        }
      case 'format_sql':
        return { success: true, data: formatQueryEditorSql(this.objectId) }
      case 'focus':
        store.focusTab(this.objectId)
        if (tab?.scope === 'workspace') clearSessionActiveTab(this.getSessionId())
        return { success: true }
      case 'close':
        // Per docs/references/ui-objects-reference.md, MCP `close` is a
        // deprecated alias for archive(archived=true), not detach-from-workset.
        store.archiveTab(this.objectId, true)
        return { success: true }
      default:
        return execError(`Unknown action: ${action}`, `Available: [${ACTIONS.map((item) => item.name).join(', ')}]`)
    }
  }
}
