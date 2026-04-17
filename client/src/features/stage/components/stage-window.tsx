import type { ReactNode } from 'react'
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
    <div className="flex h-full w-full flex-col overflow-hidden rounded-2xl border bg-muted shadow-xl ring-1 ring-black/5 dark:ring-white/10">
      <div className="flex h-10 shrink-0 items-center justify-between px-3 select-none">
        <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          {Icon && <Icon className="size-3.5" />}
          {label || '工作台'}
        </span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="size-3 rounded-full bg-[#ff5f56] transition-colors hover:bg-[#ff3b30]"
            aria-label="关闭"
            onClick={handleClose}
          />
          <button
            type="button"
            className="size-3 rounded-full bg-[#ffbd2e] opacity-60 cursor-default"
            aria-label="最小化（暂不可用）"
            disabled
          />
          <button
            type="button"
            className="size-3 rounded-full bg-[#27c93f] transition-colors hover:bg-[#1ebe2f]"
            aria-label={maximized ? '还原' : '放大'}
            onClick={handleToggleMaximized}
          />
        </div>
      </div>
      <div className="flex flex-1 min-h-0 flex-col">{children}</div>
    </div>
  )
}
