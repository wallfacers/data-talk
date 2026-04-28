import type { LucideIcon } from 'lucide-react'
import { BarChart2Icon, DatabaseIcon, FileTextIcon, LayoutIcon, SearchCodeIcon } from 'lucide-react'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'

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
