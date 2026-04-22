import type { StageState, StageTab } from '@/stores/stage-store'
import { resolveUniqueTabTitle } from './unique-tab-title'

type GlobalToolTarget = {
  kind: 'global_tool'
  tool: 'sql' | 'er' | 'report' | 'dashboard'
  title: string
}

type ResourceToolTarget = {
  kind: 'resource_tool'
  tool: 'sql' | 'er'
  title: string
  connectionId: string
  database?: string | null
  schema?: string | null
}

type Target = GlobalToolTarget | ResourceToolTarget

type Input = {
  getState: () => Pick<StageState, 'workspaceTabs' | 'tabsBySession' | 'activeWorkspaceTabId' | 'activeTabIdBySession' | 'openTab' | 'focusTab'>
  sessionId: string | null
  target: Target
  reuseExisting?: boolean
}

export function buildStageTabIdentity(target: Target): string {
  if (target.kind === 'global_tool') return target.tool
  return [target.tool, target.connectionId, target.database ?? '', target.schema ?? ''].join('::')
}

function resolveTabType(tool: Target['tool']): StageTab['type'] {
  switch (tool) {
    case 'sql':
      return 'query_editor'
    case 'er':
      return 'er_canvas'
    case 'report':
      return 'report'
    case 'dashboard':
      return 'dashboard'
  }
}

export function openOrFocusStageToolTab({ getState, sessionId, target, reuseExisting = true }: Input): { tabId: string; created: boolean } {
  const latest = getState()
  const tabType = resolveTabType(target.tool)
  const workspaceTitles = latest.workspaceTabs.map((tab) => tab.title)

  if (target.kind === 'global_tool') {
    if (reuseExisting) {
      const existing = latest.workspaceTabs.find((tab) => tab.type === tabType)
      if (existing) {
        latest.focusTab(existing.tabId)
        return { tabId: existing.tabId, created: false }
      }
    }

    const tabId = `${tabType}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const tab: StageTab = {
      tabId,
      type: tabType,
      title: resolveUniqueTabTitle(target.title, workspaceTitles),
      scope: 'workspace',
      payload: { identity: buildStageTabIdentity(target) },
      createdAt: Date.now(),
    }
    latest.openTab(tab)
    return { tabId, created: true }
  }

  if (!sessionId) throw new Error('session-scoped stage tool requires active session')

  const sessionTabs = latest.tabsBySession.get(sessionId) ?? []
  const visibleTitles = [
    ...workspaceTitles,
    ...sessionTabs.map((tab) => tab.title),
  ]
  const existing = sessionTabs.find((tab) =>
    tab.type === tabType &&
    tab.connectionId === target.connectionId &&
    (tab.database ?? null) === (target.database ?? null) &&
    (tab.schema ?? null) === (target.schema ?? null)
  )
  if (existing) {
    latest.focusTab(existing.tabId)
    return { tabId: existing.tabId, created: false }
  }

  const tabId = `${tabType}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const tab: StageTab = {
    tabId,
    type: tabType,
    title: resolveUniqueTabTitle(target.title, visibleTitles),
    scope: 'session',
    originSessionId: sessionId,
    connectionId: target.connectionId,
    database: target.database ?? undefined,
    schema: target.schema ?? undefined,
    payload: { identity: buildStageTabIdentity(target) },
    createdAt: Date.now(),
  }
  latest.openTab(tab)
  return { tabId, created: true }
}
