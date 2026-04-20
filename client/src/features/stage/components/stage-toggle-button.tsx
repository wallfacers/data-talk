import { useRef } from 'react'
import { MonitorIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from '@/features/session/use-session-mode'

export function StageToggleButton() {
  const btnRef = useRef<HTMLButtonElement>(null)
  const sid = useSessionStore((s) => s.activeSessionId)
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const { mode } = useSessionMode()
  const open = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const openStage = useStageStore((s) => s.openStage)
  const toggle = useStageStore((s) => s.toggleStage)
  const setRevealOrigin = useStageStore((s) => s.setRevealOrigin)

  const title = open ? '关闭 Stage 面板' : '打开 Stage 面板'

  function handleClick() {
    if (!sid) return
    const rect = btnRef.current?.getBoundingClientRect()
    if (rect) {
      setRevealOrigin({
        x: rect.left + rect.width / 2,
        y: rect.top + rect.height / 2,
      })
    }
    if (mode === 'HERO') {
      enterSplit(sid)
      openStage(sid)
    } else {
      toggle(sid)
    }
  }

  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <Button
            ref={btnRef}
            type="button"
            size="icon-xs"
            variant="ghost"
            aria-pressed={open}
            aria-label={title}
            aria-disabled={!sid}
            onClick={handleClick}
            className={cn(
              'cursor-pointer rounded-md text-foreground hover:bg-accent/80 aria-disabled:cursor-not-allowed aria-disabled:opacity-50',
              open && 'bg-accent/70',
            )}
          >
            <MonitorIcon className="size-3.5" />
          </Button>
        }
      />
      <TooltipContent side="bottom" sideOffset={4}>
        {title}
      </TooltipContent>
    </Tooltip>
  )
}
