import type { ReactNode } from 'react'
import { Maximize2Icon, Minimize2Icon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'

type Props = {
  sessionId?: string
  children: ReactNode
}

export function StageWindow({ sessionId, children }: Props) {
  const closeStage = useStageStore((s) => s.closeStage)
  const maximized = useStageStore((s) => sessionId ? !!s.maximizedBySession.get(sessionId) : false)
  const toggleMaximized = useStageStore((s) => s.toggleMaximized)
  const { Icon, label } = useActiveArtifactTitle(sessionId ?? '')

  function handleClose() {
    if (sessionId) closeStage(sessionId)
  }

  function handleToggleMaximized() {
    if (sessionId) toggleMaximized(sessionId)
  }

  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-2xl bg-sidebar shadow-lg ring-1 ring-sidebar-border">
      <div className="flex h-9 shrink-0 items-center justify-between px-3 select-none">
        <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          {Icon && <Icon className="size-3.5" />}
          {label || '工作台'}
        </span>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon-xs"
            aria-label={maximized ? '还原' : '放大'}
            onClick={handleToggleMaximized}
          >
            {maximized
              ? <Minimize2Icon className="size-3.5" />
              : <Maximize2Icon className="size-3.5" />}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-xs"
            aria-label="关闭"
            onClick={handleClose}
          >
            <XIcon className="size-3.5" />
          </Button>
        </div>
      </div>
      <div className="flex flex-1 min-h-0 flex-col">{children}</div>
    </div>
  )
}
