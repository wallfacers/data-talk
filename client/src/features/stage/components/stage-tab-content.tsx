import { lazy, Suspense, useEffect, useMemo } from 'react'
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
import { ScriptLibraryTab } from '@/features/script/components/script-library-tab'
import { SqlWorkbenchTab } from './sql-workbench-tab'
import { TabContentLoader } from './tab-content-loader'

const DashboardTab = lazy(() =>
  import('@/features/dashboard/dashboard-tab').then((m) => ({ default: m.DashboardTab }))
)

const ScriptEditorTab = lazy(() =>
  import('@/features/script/components/script-editor-tab').then((m) => ({ default: m.ScriptEditorTab }))
)

const OperationLogTab = lazy(() =>
  import('@/features/op-log/components/operation-log-tab').then((m) => ({ default: m.OperationLogTab }))
)

const ReportLibraryTab = lazy(() =>
  import('@/features/report/components/report-library-tab').then((m) => ({ default: m.ReportLibraryTab }))
)

const ReportViewerTab = lazy(() =>
  import('@/features/report/components/report-viewer-tab').then((m) => ({ default: m.ReportViewerTab }))
)


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

  if (tab.type === 'script_library') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <ScriptLibraryTab key={tab.tabId} />
      </div>
    )
  }

  if (tab.type === 'dashboard') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <Suspense fallback={<TabContentLoader />}>
          <DashboardTab key={tab.tabId} tab={tab} />
        </Suspense>
      </div>
    )
  }

  if (tab.type === 'script_editor') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <Suspense fallback={<TabContentLoader />}>
          <ScriptEditorTab key={tab.tabId} tabId={tab.tabId} />
        </Suspense>
      </div>
    )
  }

  if (tab.type === 'operation_log') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <Suspense fallback={<TabContentLoader />}>
          <OperationLogTab key={tab.tabId} tab={tab} />
        </Suspense>
      </div>
    )
  }

  if (tab.type === 'report_library') {
    const payload = (tab.payload ?? {}) as { workspaceId?: string }
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <Suspense fallback={<TabContentLoader />}>
          <ReportLibraryTab key={tab.tabId} workspaceId={payload.workspaceId ?? ''} />
        </Suspense>
      </div>
    )
  }

  if (tab.type === 'report_viewer') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <Suspense fallback={<TabContentLoader />}>
          <ReportViewerTab key={tab.tabId} tab={tab} />
        </Suspense>
      </div>
    )
  }

  return null
}
