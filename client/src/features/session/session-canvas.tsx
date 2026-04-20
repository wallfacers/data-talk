import { useMemo } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useFlipComposer } from './use-flip-composer'
import { useSessionHistory } from './hooks/use-session-history'
import { useSessionSubscribe } from './hooks/use-session-subscribe'
import { useBackgroundSessionSubscribe } from './hooks/use-background-session-subscribe'
import { usePendingConnectionResume } from './hooks/use-pending-connection-resume'
import { usePendingPromptResume } from './hooks/use-pending-prompt-resume'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ModelOverlay } from './model-overlay'

function BackgroundSubscriber({ sessionId }: { sessionId: string }) {
  useBackgroundSessionSubscribe(sessionId)
  return null
}

export function SessionCanvas() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const streamingBySession = useChatPartsStore((s) => s.streamingBySession)
  const backgroundSessionIds = useMemo(
    () => [...streamingBySession.keys()].filter((id) => id !== sessionId),
    [streamingBySession, sessionId],
  )

  useFlipComposer()
  useSessionHistory(sessionId)
  useSessionSubscribe(sessionId)
  usePendingConnectionResume()
  usePendingPromptResume()

  return (
    <div className="relative h-full">
      {backgroundSessionIds.map((id) => (
        <BackgroundSubscriber key={id} sessionId={id} />
      ))}
      <SplitView />
      <PromptComposer />
      <ModelOverlay />
    </div>
  )
}
