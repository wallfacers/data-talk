import { useSessionStore } from '@/stores/session-store'
import { useFlipComposer } from './use-flip-composer'
import { useSessionHistory } from './hooks/use-session-history'
import { useSessionSubscribe } from './hooks/use-session-subscribe'
import { usePendingPromptResume } from './hooks/use-pending-prompt-resume'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ConnectionOverlay } from './connection-overlay'
import { ModelOverlay } from './model-overlay'

export function SessionCanvas() {
  const sessionId = useSessionStore((s) => s.activeSessionId)

  useFlipComposer()
  useSessionHistory(sessionId)
  useSessionSubscribe(sessionId)
  usePendingPromptResume()

  return (
    <div className="relative h-full">
      <SplitView />
      <PromptComposer />
      <ConnectionOverlay />
      <ModelOverlay />
    </div>
  )
}
