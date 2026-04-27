import type { LucideIcon } from 'lucide-react'
import { FileEditIcon, FileTextIcon, ImageIcon, LayoutIcon } from 'lucide-react'

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
    icon: FileEditIcon,
    labelKey: 'tabType.queryEditor',
    extractContent: (p) => {
      const o = p as { sqlText?: unknown } | null | undefined
      return typeof o?.sqlText === 'string' ? o.sqlText : ''
    },
  },
  artifact_preview: {
    type: 'artifact_preview',
    persistent: true,
    scope: 'session',
    icon: ImageIcon,
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
