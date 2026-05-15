import type { StageState, StageTab } from '@/stores/stage-store'
import { translateMessage } from '@/i18n/messages'
import { useUISettingsStore } from '@/stores/ui-settings-store'

interface OpenOpLogTabInput {
  getState: () => Pick<StageState, 'tabs' | 'activeTabId' | 'openTab' | 'focusTab'>
  connectionId: string
  connectionName: string
}

export function openOrFocusOpLogTab({ getState, connectionId, connectionName }: OpenOpLogTabInput): { tabId: string; created: boolean } {
  const { tabs, openTab, focusTab } = getState()

  const identityKey = `operation_log::${connectionId}`
  const existing = tabs.find((tab) =>
    tab.type === 'operation_log' && tab.connectionId === connectionId
  )
  if (existing) {
    focusTab(existing.tabId)
    return { tabId: existing.tabId, created: false }
  }

  const language = useUISettingsStore.getState().language
  const title = translateMessage(language, 'opLog.tabTitle')

  const tabId = `operation_log_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const tab: StageTab = {
    tabId,
    type: 'operation_log',
    title,
    connectionId,
    connectionName,
    payload: {
      kind: 'operation_log',
      connectionId,
      connectionName,
      identity: identityKey,
    },
    createdAt: Date.now(),
  }
  openTab(tab)
  return { tabId, created: true }
}
