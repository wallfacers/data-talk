import type { LucideIcon } from 'lucide-react'
import { BarChart2Icon, DatabaseIcon, FileTextIcon, LayoutIcon, NetworkIcon, SearchCodeIcon } from 'lucide-react'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErDesignerPayload, ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

export interface TabTypeDescriptor {
  type: string
  persistent: boolean
  scope?: 'workspace' | 'session'
  icon: LucideIcon
  labelKey: string
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
    icon: DatabaseIcon,
    labelKey: 'tabType.queryEditor',
    extractContent: (p) => {
      const o = p as { sqlText?: unknown } | null | undefined
      return typeof o?.sqlText === 'string' ? o.sqlText : ''
    },
    rehydrate: (tabId, p) => {
      const o = p as { sqlText?: unknown; version?: unknown } | null | undefined
      useSqlWorkbenchStore.getState().ensureTab(tabId, {
        sqlText: typeof o?.sqlText === 'string' ? o.sqlText : '',
        source: 'user',
      })
    },
  },
  artifact_preview: {
    type: 'artifact_preview',
    persistent: true,
    scope: 'session',
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
    icon: NetworkIcon,
    labelKey: 'tabType.erDesigner',
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
