import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from './use-session-mode'
import { useFlipComposer } from './use-flip-composer'
import { useSessionHistory } from './hooks/use-session-history'
import { useSessionSubscribe } from './hooks/use-session-subscribe'
import { usePendingPromptResume } from './hooks/use-pending-prompt-resume'
import { HeroView } from './hero-view'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ConnectionOverlay } from './connection-overlay'
import { WelcomeEmpty } from './welcome-empty'

export function SessionCanvas() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const { mode } = useSessionMode()
  useFlipComposer()
  useSessionHistory(sessionId)
  useSessionSubscribe(sessionId)
  usePendingPromptResume()

  return (
    <div className="relative h-full">
      {mode === 'HERO' && <HeroView />}
      {mode === 'SPLIT' && <SplitView />}
      {mode === 'NOSESS' && <WelcomeEmpty />}
      <PromptComposer />
      <ConnectionOverlay />
    </div>
  )
}
