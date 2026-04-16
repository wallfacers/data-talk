import { useSessionMode } from './use-session-mode'
import { useFlipComposer } from './use-flip-composer'
import { HeroView } from './hero-view'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ConnectionOverlay } from './connection-overlay'
import { WelcomeEmpty } from './welcome-empty'

export function SessionCanvas() {
  const { mode } = useSessionMode()
  useFlipComposer()
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
