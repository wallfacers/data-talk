import { useCallback, useMemo } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { buildErrorMarkdown, type ErrorContext } from './error-to-ai-context'

export function useAskAIAboutError(errorContext: ErrorContext | null) {
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setComposerInsertText = useSessionStore((s) => s.setComposerInsertText)

  const isAvailable = useMemo(() => {
    if (!activeSessionId) return false
    const streaming = useChatPartsStore.getState().streamingBySession.has(activeSessionId)
    return !streaming
  }, [activeSessionId])

  const askAI = useCallback(() => {
    if (!activeSessionId || !errorContext) return
    const streaming = useChatPartsStore.getState().streamingBySession.has(activeSessionId)
    if (streaming) return

    const markdown = buildErrorMarkdown(errorContext)
    setComposerInsertText({ sessionId: activeSessionId, text: markdown })
  }, [activeSessionId, errorContext, setComposerInsertText])

  return { askAI, isAvailable }
}
