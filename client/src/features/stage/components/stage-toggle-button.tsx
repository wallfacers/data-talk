import { MonitorIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from '@/features/session/use-session-mode'

export function StageToggleButton() {
  const { mode } = useSessionMode()
  const sid = useSessionStore((s) => s.activeSessionId)
  const open = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const toggle = useStageStore((s) => s.toggleStage)

  const disabled = mode !== 'SPLIT' || !sid
  const title = disabled
    ? 'AI 还没产出工件'
    : open
      ? '关闭 Stage 面板'
      : '打开 Stage 面板'

  return (
    <Button
      type="button"
      size="icon-xs"
      variant="ghost"
      aria-pressed={open}
      aria-label={title}
      title={title}
      disabled={disabled}
      onClick={() => sid && toggle(sid)}
      className={cn(
        'rounded-md text-muted-foreground hover:bg-accent/50',
        open && 'bg-accent/70 text-foreground',
      )}
    >
      <MonitorIcon className="size-3.5" />
    </Button>
  )
}
