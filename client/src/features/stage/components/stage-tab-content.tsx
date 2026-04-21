import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { BangQueryTab } from './bang-query-tab'
import { QueryEditorTab } from './query-editor-tab'
import { StagePlaceholderTab } from './stage-placeholder-tab'
import { useI18n } from '@/i18n/use-i18n'

export function StageTabContent() {
  const { t } = useI18n()
  const sid = useSessionStore((s) => s.activeSessionId)
  const activeTabId = useStageStore((s) => {
    if (!sid) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sid) ?? s.activeWorkspaceTabId
  })
  const tab = useStageStore((s) => {
    if (!activeTabId) return null
    return (
      s.workspaceTabs.find((t) => t.tabId === activeTabId) ??
      (sid ? s.tabsBySession.get(sid)?.find((t) => t.tabId === activeTabId) : undefined) ??
      null
    )
  })

  if (!tab) return null
  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden">
      {(() => {
        switch (tab.type) {
          case 'bang_query':
            return <BangQueryTab tabId={tab.tabId} />
          case 'query_editor':
            return <QueryEditorTab tab={tab} />
          case 'er_canvas':
            return (
              <StagePlaceholderTab
                kind="er"
                title={tab.title}
                description={t('stage.placeholder.erWorkbench')}
              />
            )
          case 'report':
            return (
              <StagePlaceholderTab
                kind="report"
                title={tab.title}
                description={t('stage.placeholder.reportWorkbench')}
              />
            )
          case 'dashboard':
            return (
              <StagePlaceholderTab
                kind="dashboard"
                title={tab.title}
                description={t('stage.placeholder.dashboardWorkbench')}
              />
            )
          default:
            return (
              <StagePlaceholderTab
                kind="unsupported"
                title={tab.title}
                description={t('stage.placeholder.unknownWorkbench')}
                details={t('stage.placeholder.unsupportedType', { type: tab.type })}
              />
            )
        }
      })()}
    </div>
  )
}
