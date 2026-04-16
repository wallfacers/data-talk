import type { ReactNode } from 'react'
import { useStageStore } from '@/stores/stage-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'

type Props = {
  sessionId: string
  children: ReactNode
}

export function StageWindow({ sessionId, children }: Props) {
  const close = useStageStore((s) => s.closeStage)
  const { Icon, label } = useActiveArtifactTitle(sessionId)
  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-xl border bg-card shadow-sm">
      <div className="flex h-9 shrink-0 items-center gap-2 border-b bg-muted/40 px-3 select-none">
        <button
          type="button"
          aria-label="关闭 Stage"
          onClick={(e) => {
            e.stopPropagation()
            close(sessionId)
          }}
          className="size-3 rounded-full bg-[#ff5f56] hover:opacity-80"
        />
        <span aria-hidden className="size-3 rounded-full bg-[#ffbd2e]" />
        <span aria-hidden className="size-3 rounded-full bg-[#27c93f]" />
        <div className="flex-1" />
        <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
          {Icon ? <Icon className="size-3.5" /> : null}
          {label}
        </span>
      </div>
      <div className="flex flex-1 min-h-0 flex-col">{children}</div>
    </div>
  )
}
