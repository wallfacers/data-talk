import { XIcon } from 'lucide-react'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'

export function StageTabStrip() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const workspaceTabs = useStageStore((s) => s.workspaceTabs)
  const sessionTabs = useStageStore((s) => (sid ? (s.tabsBySession.get(sid) ?? null) : null))
  const activeTabId = useStageStore((s) => {
    if (!sid) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sid) ?? s.activeWorkspaceTabId
  })
  const focusTab = useStageStore((s) => s.focusTab)
  const closeTab = useStageStore((s) => s.closeTab)

  const tabs = sessionTabs ? [...workspaceTabs, ...sessionTabs] : workspaceTabs
  if (tabs.length === 0) return null

  return (
    <div className="flex items-center gap-1 border-b px-2 py-1 overflow-x-auto">
      {tabs.map((t) => (
        <div
          key={t.tabId}
          role="tab"
          aria-selected={t.tabId === activeTabId}
          onClick={() => focusTab(t.tabId)}
          className={`flex items-center gap-1 px-2 py-1 text-xs rounded cursor-pointer transition-colors ${
            t.tabId === activeTabId ? 'bg-muted' : 'hover:bg-muted/50'
          }`}
        >
          <span className="truncate max-w-[200px]">{t.title}</span>
          <button
            type="button"
            aria-label="Close tab"
            className="opacity-50 hover:opacity-100"
            onClick={(e) => { e.stopPropagation(); closeTab(t.tabId) }}
          >
            <XIcon className="size-3" />
          </button>
        </div>
      ))}
    </div>
  )
}
