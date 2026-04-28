import { useEffect, useRef } from 'react'
import { MonitorIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionMode } from '@/features/session/use-session-mode'
import { useI18n } from '@/i18n/use-i18n'

export function StageToggleButton() {
  const { t } = useI18n()
  const btnRef = useRef<HTMLButtonElement>(null)
  const sid = useSessionStore((s) => s.activeSessionId)
  const setSessionMode = useSessionStore((s) => s.setSessionMode)
  const hasEverSent = useSessionStore((s) =>
    sid ? (s.hasEverSentBySession.get(sid) ?? false) : false,
  )
  const hasStoreMessages = useChatPartsStore((s) => {
    const info = sid ? s.infoBySession.get(sid) : undefined
    return info ? info.size > 0 : false
  })
  const hasMessages = hasEverSent || hasStoreMessages

  const { mode } = useSessionMode()
  const open = useStageStore((s) => s.open)
  const openStage = useStageStore((s) => s.openStage)
  const toggleStage = useStageStore((s) => s.toggleStage)
  const setRevealOrigin = useStageStore((s) => s.setRevealOrigin)

  const title = open ? t('stage.closePanel') : t('stage.openPanel')

  useEffect(() => {
    if (!sid) return
    const nextMode = open || hasMessages ? 'SPLIT' : 'HERO'
    if (mode !== nextMode) {
      setSessionMode(sid, nextMode)
    }
  }, [sid, open, hasMessages, mode, setSessionMode])

  function handleClick() {
    const rect = btnRef.current?.getBoundingClientRect()
    if (rect) {
      setRevealOrigin({
        x: rect.left + rect.width / 2,
        y: rect.top + rect.height / 2,
      })
    }
    if (open) {
      toggleStage()
      if (sid && !hasMessages) {
        setSessionMode(sid, 'HERO')
      }
      return
    }

    if (sid) {
      setSessionMode(sid, 'SPLIT')
    }
    openStage()
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
            onClick={handleClick}
            className={cn(
              'cursor-pointer rounded-md text-foreground hover:bg-accent/80',
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
