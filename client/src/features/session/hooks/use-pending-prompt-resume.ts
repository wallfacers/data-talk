import { useEffect } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart } from '@/services/channel/types'
import { useHasActiveModel } from './use-has-active-model'

export function usePendingPromptResume() {
  const pendingPrompt = useSessionStore((s) => s.pendingPrompt)
  const modelOverlayOn = useSessionStore((s) => s.pendingModelPrompt)
  const connectionOverlayOn = useSessionStore((s) => s.pendingConnectionPrompt)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const setComposerRestoreDraft = useSessionStore((s) => s.setComposerRestoreDraft)
  const clearComposerDraft = useSessionStore((s) => s.clearComposerDraft)
  const { sendMessage, isStreaming } = useChannel()
  const hasActiveModel = useHasActiveModel()

  useEffect(() => {
    if (modelOverlayOn || connectionOverlayOn || !pendingPrompt || !hasActiveModel || !activeSessionId || isStreaming) return

    const draft = pendingPrompt
    setPendingPrompt(null)
    clearComposerDraft(activeSessionId)
    void sendMessage([createTextPart(activeSessionId, draft)]).then((ok) => {
      if (!ok) {
        setComposerRestoreDraft({ sessionId: activeSessionId, text: draft })
      }
    })
  }, [
    modelOverlayOn,
    connectionOverlayOn,
    pendingPrompt,
    hasActiveModel,
    activeSessionId,
    isStreaming,
    setPendingPrompt,
    setComposerRestoreDraft,
    clearComposerDraft,
    sendMessage,
  ])
}
