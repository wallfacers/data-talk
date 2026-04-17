import { MonitorIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from '@/features/session/use-session-mode'

export function StageToggleButton() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const { mode } = useSessionMode()
  const sessionOpen = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const globalOpen = useStageStore((s) => s.globalOpen)
  const open = sid ? sessionOpen : globalOpen
  const openStage = useStageStore((s) => s.openStage)
  const toggle = useStageStore((s) => s.toggleStage)
  const toggleGlobal = useStageStore((s) => s.toggleGlobal)

  const title = open ? '关闭 Stage 面板' : '打开 Stage 面板'

  function handleClick() {
    if (!sid) {
      // TODO(test): 无 session 全局预览，接通正式 session 流程后可移除
      toggleGlobal()
      return
    }
    if (mode === 'HERO') {
      enterSplit(sid)
      openStage(sid)
    } else {
      toggle(sid)
    }
  }

  return (
    <Button
      type="button"
      size="icon-xs"
      variant="ghost"
      aria-pressed={open}
      aria-label={title}
      title={title}
      disabled={false}
      onClick={handleClick}
      className={cn(
        'cursor-pointer rounded-md text-black hover:bg-accent/50 disabled:cursor-not-allowed disabled:pointer-events-auto dark:text-white',
        open && 'bg-accent/70',
      )}
    >
      <MonitorIcon className="size-3.5" />
    </Button>
  )
}
