import type { LucideIcon } from 'lucide-react'
import { BarChart2Icon, BrainIcon, DatabaseIcon, FileTextIcon, LayoutDashboardIcon, LayoutIcon, NetworkIcon, PackageIcon, SearchCodeIcon, TerminalIcon } from 'lucide-react'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { normalizeQueryEditorPayload } from '@/features/stage/utils/normalize-query-editor-payload'
import type { ErDesignerPayload, ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'
import { useDashboardTabsStore } from '@/features/dashboard/stores/dashboard-tabs-store'
import { dashboardSchema } from '@/features/dashboard/schema'

export interface TabTypeDescriptor {
  type: string
  persistent: boolean
  scope?: 'workspace' | 'session'
  payloadSource?: 'stage_tab' | 'sql_workbench' | 'er_tabs'
  icon: LucideIcon
  labelKey: string
  railLabelKey?: string
  extractContent: (payload: unknown) => string
  rehydrate?: (tabId: string, payload: unknown) => void
}

const NOOP: TabTypeDescriptor = {
  type: 'unknown',
  persistent: false,
  icon: FileTextIcon,
  labelKey: 'tabType.unknown',
  extractContent: () => '',
}

export const TAB_TYPE_REGISTRY: Record<string, TabTypeDescriptor> = {
  query_editor: {
    type: 'query_editor',
    persistent: true,
    scope: 'workspace',
    payloadSource: 'sql_workbench',
    icon: DatabaseIcon,
    labelKey: 'tabType.queryEditor',
    extractContent: (p) => {
      const o = p as { sqlText?: unknown } | null | undefined
      return typeof o?.sqlText === 'string' ? o.sqlText : ''
    },
    rehydrate: (tabId, p) => {
      const o = p as { sqlText?: unknown; version?: unknown } | null | undefined
      const normalizedPayload = normalizeQueryEditorPayload(p)
      useSqlWorkbenchStore.getState().hydrateTab(tabId, {
        sqlText: typeof o?.sqlText === 'string' ? o.sqlText : '',
        source: normalizedPayload.source,
        useSessionContext: normalizedPayload.useSessionContext,
        boundSessionId: normalizedPayload.boundSessionId,
        override: normalizedPayload.contextOverride
          ? {
              connectionId: normalizedPayload.contextOverride.connectionId,
              database: normalizedPayload.contextOverride.database,
              schema: normalizedPayload.contextOverride.schema,
              source: 'open_payload',
              setAt: Date.now(),
            }
          : null,
      })
    },
  },
  artifact_preview: {
    type: 'artifact_preview',
    persistent: true,
    scope: 'session',
    payloadSource: 'stage_tab',
    icon: BarChart2Icon,
    labelKey: 'tabType.artifactPreview',
    extractContent: (p) => {
      const o = p as { artifactTitle?: unknown } | null | undefined
      return typeof o?.artifactTitle === 'string' ? o.artifactTitle : ''
    },
  },
  file_preview: {
    type: 'file_preview',
    persistent: false,
    icon: FileTextIcon,
    labelKey: 'tabType.filePreview',
    extractContent: () => '',
  },
  workspace: {
    type: 'workspace',
    persistent: false,
    icon: LayoutIcon,
    labelKey: 'tabType.workspace',
    extractContent: () => '',
  },
  diagnostic: {
    type: 'diagnostic',
    persistent: true,
    scope: 'workspace',
    payloadSource: 'stage_tab',
    icon: SearchCodeIcon,
    labelKey: 'tabType.diagnostic',
    extractContent: (p) => {
      const o = p as { sql?: unknown } | null | undefined
      return typeof o?.sql === 'string' ? o.sql : ''
    },
  },
  er_inspector: {
    type: 'er_inspector',
    persistent: true,
    scope: 'workspace',
    payloadSource: 'er_tabs',
    icon: NetworkIcon,
    labelKey: 'tabType.erInspector',
    extractContent: (p) => {
      const payload = p as ErInspectorPayload | null | undefined
      if (!payload) return ''
      const selection = (payload.selection ?? []).join(' ')
      const snapshot = (payload.tablesSnapshot ?? [])
        .map((table) => {
          const columns = (table.columns ?? []).map((column) => `${column.name} ${column.type}`).join(' ')
          return `${table.name} ${columns} ${table.comment ?? ''}`.trim()
        })
        .join('\n')
      const virtualRelations = (payload.virtualRelations ?? [])
        .map((relation) => `${relation.from.table}.${relation.from.column} ${relation.to.table}.${relation.to.column}`)
        .join('\n')
      const notes = Object.values(payload.notes ?? {}).join('\n')
      return [selection, snapshot, virtualRelations, notes].filter(Boolean).join('\n')
    },
    rehydrate: (tabId, p) => {
      useErTabsStore.getState().hydrateInspector(tabId, p as ErInspectorPayload)
    },
  },
  er_designer: {
    type: 'er_designer',
    persistent: true,
    scope: 'workspace',
    payloadSource: 'er_tabs',
    icon: NetworkIcon,
    labelKey: 'tabType.erDesigner',
    railLabelKey: 'tabType.erDesigner.short',
    extractContent: (p) => {
      const payload = p as ErDesignerPayload | null | undefined
      if (!payload) return ''

      const tableById = new Map((payload.tables ?? []).map((table) => [table.id, table]))
      const columnById = new Map<string, { tableName: string; columnName: string }>()
      const target = [
        'target',
        payload.targetConnectionId,
        payload.targetDatabase,
        payload.targetSchema,
        payload.dialect,
      ].filter(Boolean).join(' ')

      const tables = (payload.tables ?? []).map((table) => {
        for (const column of table.columns ?? []) {
          columnById.set(column.id, { tableName: table.name, columnName: column.name })
        }
        const columns = (table.columns ?? [])
          .map((column) => [column.name, column.type].filter(Boolean).join(' '))
          .join(' ')
        const columnComments = (table.columns ?? [])
          .map((column) => column.comment)
          .filter(Boolean)
          .join(' ')
        const indexes = (table.indexes ?? [])
          .map((index) => `index ${index.name} ${(index.columns ?? []).join(' ')}`)
          .join(' ')
        const uniques = (table.uniques ?? [])
          .map((unique) => `unique ${(unique.columns ?? []).join(' ')}`)
          .join(' ')
        return [table.name, columns, table.comment, columnComments, indexes, uniques].filter(Boolean).join(' ')
      })

      const relations = (payload.relations ?? []).map((relation) => {
        const from = columnById.get(relation.fromColumnId)
        const to = columnById.get(relation.toColumnId)
        const fromTable = from?.tableName ?? tableById.get(relation.fromTableId)?.name ?? relation.fromTableId
        const fromColumn = from?.columnName ?? relation.fromColumnId
        const toTable = to?.tableName ?? tableById.get(relation.toTableId)?.name ?? relation.toTableId
        const toColumn = to?.columnName ?? relation.toColumnId
        return `relation ${fromTable}.${fromColumn} -> ${toTable}.${toColumn} ${relation.type} ${relation.constraintMethod}`
      })

      return [target, ...tables, ...relations].filter(Boolean).join('\n')
    },
    rehydrate: (tabId, p) => {
      useErTabsStore.getState().hydrateDesigner(tabId, p as ErDesignerPayload)
    },
  },
  dashboard: {
    type: 'dashboard',
    persistent: true,
    scope: 'workspace',
    payloadSource: 'stage_tab',
    icon: LayoutDashboardIcon,
    labelKey: 'tabType.dashboard',
    extractContent: (p) => {
      const o = p as { title?: unknown; widgets?: unknown[]; parameters?: unknown[] } | null | undefined
      if (!o) return ''
      const parts: string[] = []
      if (typeof o.title === 'string') parts.push(o.title)
      if (Array.isArray(o.widgets)) {
        for (const w of o.widgets) {
          const wg = w as { type?: unknown; id?: unknown; options?: Record<string, unknown> } | null
          if (!wg) continue
          if (typeof wg.id === 'string') parts.push(wg.id)
          if (wg.type === 'markdown' && wg.options && typeof wg.options.text === 'string') {
            parts.push(wg.options.text)
          }
          if ((wg.type === 'chart' || wg.type === 'table') && wg.options && typeof wg.options.title === 'string') {
            parts.push(wg.options.title)
          }
        }
      }
      if (Array.isArray(o.parameters)) {
        for (const param of o.parameters) {
          const pm = param as { name?: unknown; type?: unknown } | null
          if (!pm) continue
          if (typeof pm.name === 'string') parts.push(pm.name)
        }
      }
      const joined = parts.join('\n')
      return joined.length > 4096 ? joined.slice(0, 4096) : joined
    },
    rehydrate: (tabId, p) => {
      const o = p as { fileArtifactId?: string; schemaVersion?: unknown } | Record<string, unknown> | null | undefined
      if (!o) return
      // If payload is a pointer (fileArtifactId), skip — actual data loaded lazily
      if (o.fileArtifactId) return
      // Normalize v1 payloads to v2
      if (o.schemaVersion !== 2) {
        ;(o as Record<string, unknown>).schemaVersion = 2
        ;(o as Record<string, unknown>).theme = (o as Record<string, unknown>).theme ?? 'industry-default'
        ;(o as Record<string, unknown>).renderer = 'bezel'
        ;(o as Record<string, unknown>).layout = { engine: 'free' }
      }
      // Otherwise treat as inline dashboard JSON
      const parsed = dashboardSchema.safeParse(o)
      if (parsed.success) {
        useDashboardTabsStore.getState().hydrateTab(tabId, parsed.data)
      }
    },
  },
  files: {
    type: 'files',
    persistent: false,
    scope: 'session',
    icon: FileTextIcon,
    labelKey: 'tabType.files',
    extractContent: () => '',
  },
  files_library: {
    type: 'files_library',
    persistent: true,
    scope: 'workspace',
    icon: PackageIcon,
    labelKey: 'tabType.filesLibrary',
    extractContent: () => '',
  },
  script_editor: {
    type: 'script_editor',
    persistent: true,
    scope: 'workspace',
    payloadSource: 'stage_tab',
    icon: TerminalIcon,
    labelKey: 'tabType.scriptEditor',
    extractContent: (p) => {
      const o = p as { scriptText?: unknown } | null | undefined
      return typeof o?.scriptText === 'string' ? o.scriptText : ''
    },
  },
  semantic_model_editor: {
    type: 'semantic_model_editor',
    persistent: true,
    scope: 'workspace',
    payloadSource: 'stage_tab',
    icon: BrainIcon,
    labelKey: 'tabType.semanticModelEditor',
    extractContent: (p) => {
      const o = p as { domain?: unknown; description?: unknown } | null | undefined
      return [typeof o?.domain === 'string' ? o.domain : '', typeof o?.description === 'string' ? o.description : ''].filter(Boolean).join(' ')
    },
  },
}

export function getTabTypeDescriptor(type: string): TabTypeDescriptor {
  return TAB_TYPE_REGISTRY[type] ?? NOOP
}

export function isPersistent(type: string): boolean {
  return getTabTypeDescriptor(type).persistent
}

export function getScope(type: string): 'workspace' | 'session' | undefined {
  return getTabTypeDescriptor(type).scope
}
