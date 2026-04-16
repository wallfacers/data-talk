import { useSessionMode } from './use-session-mode'
import { useFlipComposer } from './use-flip-composer'
import { HeroView } from './hero-view'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ConnectionOverlay } from './connection-overlay'

export function SessionCanvas() {
  const { mode } = useSessionMode()
  useFlipComposer()
  return (
    <div className="relative h-full">
      {mode === 'HERO' && <HeroView />}
      {mode === 'SPLIT' && <SplitView />}
      {mode === 'NOSESS' && <div className="flex h-full items-center justify-center text-muted-foreground">请从侧边栏选择或新建会话</div>}
      <PromptComposer />
      <ConnectionOverlay />
    </div>
  )
}
