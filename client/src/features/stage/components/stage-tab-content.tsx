import { useEffect, useMemo } from 'react'
import { useShallow } from 'zustand/react/shallow'
import { useStageStore } from '@/stores/stage-store'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { ArtifactPreviewTab } from './artifact-preview-tab'
import { DiagnosticsTab } from './diagnostics/diagnostics-tab'
import { ErDesignerTab } from './er-designer-tab'
import { ErInspectorTab } from './er-inspector-tab'
import { FilePreviewTab } from './file-preview-tab'
import { FilesTab } from './files-tab'
import { FilesLibraryTab } from './files-library-tab'
import { SqlWorkbenchTab } from './sql-workbench-tab'
import { DashboardTab } from '@/features/dashboard/dashboard-tab'

export function StageTabContent() {
  const cleanupTabs = useSqlWorkbenchStore((s) => s.cleanupTabs)
  const activeTabId = useStageStore((s) => s.activeTabId)
  const tabs = useStageStore(useShallow((s) => s.tabs))
  const sqlTabIds = useMemo(
    () => tabs
      .filter((candidate) => candidate.type === 'query_editor')
      .map((candidate) => candidate.tabId),
    [tabs],
  )
  const tab = useStageStore((s) => {
    if (!activeTabId) return null
    return s.tabs.find((t) => t.tabId === activeTabId) ?? null
  })

  useEffect(() => {
    cleanupTabs(sqlTabIds)
  }, [cleanupTabs, sqlTabIds])

  if (!tab) return null

  if (tab.type === 'query_editor') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <SqlWorkbenchTab key={tab.tabId} tab={tab} />
      </div>
    )
  }

  if (tab.type === 'file_preview') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <FilePreviewTab key={tab.tabId} tab={tab} />
      </div>
    )
  }

  if (tab.type === 'artifact_preview') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <ArtifactPreviewTab key={tab.tabId} tab={tab} />
      </div>
    )
  }

  if (tab.type === 'diagnostic') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <DiagnosticsTab key={tab.tabId} payload={(tab.payload ?? {}) as import('./diagnostics/diagnostics-tab').DiagnosticsTabPayload} />
      </div>
    )
  }

  if (tab.type === 'er_inspector') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <ErInspectorTab key={tab.tabId} tabId={tab.tabId} />
      </div>
    )
  }

  if (tab.type === 'er_designer') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <ErDesignerTab key={tab.tabId} tabId={tab.tabId} />
      </div>
    )
  }

  if (tab.type === 'files') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <FilesTab key={tab.tabId} />
      </div>
    )
  }

  if (tab.type === 'files_library') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <FilesLibraryTab key={tab.tabId} />
      </div>
    )
  }

  if (tab.type === 'dashboard') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <DashboardTab key={tab.tabId} tabId={tab.tabId} />
      </div>
    )
  }

  return null
}
