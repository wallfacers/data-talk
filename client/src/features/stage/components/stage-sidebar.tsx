import type { ReactNode } from 'react'

export type StageSidebarProps = {
  sessionId?: string
  collapsed: boolean
  onToggleCollapsed?: () => void
  toolRowSlot?: ReactNode
  resourceBrowserSlot?: ReactNode
  className?: string
}

/** @deprecated Use StageActivityRail instead. */
export function StageSidebar(_props: StageSidebarProps) {
  return null
}
