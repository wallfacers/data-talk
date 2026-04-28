import type { FilePreviewPayload } from '@/features/chat/components/tools/renderers/read-file-output'
import type { StageState, StageTab } from '@/stores/stage-store'

type Input = {
  getState: () => Pick<StageState, 'tabs' | 'openTab' | 'focusTab'>
  sessionId: string | null
  payload: FilePreviewPayload
}

type FilePreviewTabPayload = {
  sourceKey?: string
}

function hasFilePreviewSourceKey(payload: unknown): payload is FilePreviewTabPayload {
  return typeof payload === 'object' && payload !== null && 'sourceKey' in payload && typeof (payload as { sourceKey?: unknown }).sourceKey === 'string'
}

export function openOrFocusFilePreviewTab({ getState, sessionId, payload }: Input): { tabId: string; created: boolean } {
  if (!sessionId || sessionId.trim().length === 0) {
    throw new Error('sessionId is required to open a session file preview tab')
  }

  const latest = getState()
  const existing = latest.tabs.find((tab) =>
    tab.type === 'file_preview' &&
    hasFilePreviewSourceKey(tab.payload) &&
    tab.payload.sourceKey === payload.sourceKey
  )

  if (existing) {
    latest.focusTab(existing.tabId)
    return { tabId: existing.tabId, created: false }
  }

  const tabId = `file_preview_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const tab: StageTab = {
    tabId,
    type: 'file_preview',
    title: payload.filename,
    scope: 'session',
    originSessionId: sessionId,
    payload,
    createdAt: Date.now(),
  }

  latest.openTab(tab)
  return { tabId, created: true }
}
