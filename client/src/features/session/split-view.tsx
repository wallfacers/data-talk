import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { ArrowDownIcon, DatabaseIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { TurnList } from '@/features/chat/components/turn/turn-list'
import { TurnListErrorBoundary } from '@/features/chat/components/turn/turn-list-error-boundary'
import { StageWindow } from '@/features/stage/components/stage-window'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import { ChatHeader } from './chat-header'
import { useAutoScroll } from '@/hooks/use-auto-scroll'
import { useI18n } from '@/i18n/use-i18n'
import { useOpencodeHealth } from './hooks/use-opencode-health'

const DURATION = 240
const EASE = 'cubic-bezier(0.32, 0.72, 0.24, 1)'
const SPLIT_RATIO_KEY = 'split-view-ratio'

function loadSavedRatio(): number {
  try {
    const v = localStorage.getItem(SPLIT_RATIO_KEY)
    if (v) { const n = parseFloat(v); if (!isNaN(n) && n >= 0.2 && n <= 0.8) return n }
  } catch { /* ignore */ }
  return 0.46
}

export function SplitView() {
  const { t } = useI18n()
  const { data: health } = useOpencodeHealth()
  const sid = useSessionStore((s) => s.activeSessionId)
  const open = useStageStore((s) => s.open)
  const maximized = useStageStore((s) => s.maximized)
  // 用同步的 hasEverSent 作为主信号，避免会话切换时 infoBySession 还没被 fetch
  // 填充导致空态分支闪过一帧；chat-parts-store 的判断仅兜底"新空会话里用户刚敲第一条"。
  const hasEverSent = useSessionStore((s) =>
    sid ? (s.hasEverSentBySession.get(sid) ?? false) : false,
  )
  const hasStoreMessages = useChatPartsStore((s) => {
    const info = sid ? s.infoBySession.get(sid) : undefined
    return info ? info.size > 0 : false
  })
  const hasMessages = hasEverSent || hasStoreMessages

  const splitResizable = useUISettingsStore((s) => s.splitResizable)
  const rootRef = useRef<HTMLDivElement>(null)
  const [dragRatio, setDragRatio] = useState<number | null>(null)
  const isDraggingRef = useRef(false)

  // Track structural changes only; streamed token growth follows through observers.
  const layoutVersion = useChatPartsStore((s) => s.layoutVersion)
  // A user send must re-arm follow even if the user had scrolled up during
  // the previous assistant turn — otherwise their new message stays above
  // the viewport with no assistant response visible.
  const userSendVersion = useChatPartsStore((s) => s.userSendVersion)

  const { ref: scrollRef, scrollToBottom, isAtBottom } = useAutoScroll<HTMLDivElement>(
    [layoutVersion],
    [userSendVersion],
  )

  // Scroll to bottom on explicit session switch.
  const prevSidRef = useRef<string | null>(null)
  useEffect(() => {
    const prev = prevSidRef.current
    prevSidRef.current = sid ?? null
    if (sid && prev !== null && prev !== sid) {
      scrollToBottom('auto')
    }
  }, [sid, scrollToBottom])

  // Load saved ratio on mount
  useEffect(() => { setDragRatio(loadSavedRatio()) }, [])

  const effectiveRatio = dragRatio ?? 0.46
  // Chat width never collapses to 0 — use translateX to slide it off screen during
  // maximize so inner content doesn't reflow (which caused message jitter).
  const chatWidth = open ? `${effectiveRatio * 100}%` : '100%'
  const chatTransform = maximized ? 'translateX(-100%)' : 'translateX(0)'
  const stageWidth = maximized ? '100%' : `${(1 - effectiveRatio) * 100}%`
  const stageTransform = open ? 'translateX(0)' : 'translateX(100%)'

  const chatTransition = maximized
    ? 'transform 180ms ease-in'
    : `transform ${DURATION}ms ${EASE} 60ms, width ${DURATION}ms ${EASE}`
  const stageTransition = maximized
    ? `width ${DURATION}ms ${EASE} 60ms`
    : `transform ${DURATION}ms ${EASE}, width ${DURATION}ms ${EASE}`

  const stageStyle: CSSProperties = {
    position: 'absolute',
    top: 0, right: 0, bottom: 0,
    width: stageWidth,
    transform: stageTransform,
    transition: stageTransition,
    willChange: 'transform, width',
  }
  const degradedReason = health?.reason ?? health?.message ?? ''
  const scrollToBottomLabel = t('chat.scrollToBottom')
  const degradedNotice = health?.status === 'degraded' ? (
    <div
      data-opencode-health="degraded"
      className="mx-auto mb-4 w-full max-w-3xl rounded border border-amber-500/40 bg-amber-50 p-3 text-sm text-amber-800 dark:bg-amber-950/20 dark:text-amber-300"
    >
      <div className="font-medium">{t('session.opencodeDegradedTitle')}</div>
      <p className="mt-1">{t('session.opencodeDegradedBody')}</p>
      {degradedReason ? (
        <p className="mt-1 text-xs text-amber-700/80 dark:text-amber-200/80">
          {t('session.opencodeDegradedReason', { reason: degradedReason })}
        </p>
      ) : null}
    </div>
  ) : null

  return (
    <div ref={rootRef} className="relative h-full overflow-hidden">
      {/* chat 列 */}
      <div
        style={{
          position: 'absolute',
          top: 0, left: 0, bottom: 0,
          width: chatWidth,
          transform: chatTransform,
          transition: chatTransition,
          overflow: 'hidden',
          willChange: 'transform, width',
        }}
      >
        {hasMessages ? (
          <div className="flex h-full flex-col">
            <ChatHeader />
            <div className="relative flex min-h-0 flex-1 flex-col">
              <div
                ref={scrollRef}
                className="flex-1 overflow-y-auto px-2 py-4"
                // `overflow-anchor: auto` (browser default) lets the engine
                // compensate `scrollTop` when content above the viewport
                // shrinks — e.g. reasoning panel collapsing, code→chart fence
                // transition, SQL action bar appearing. Our auto-follow logic
                // still wins at the bottom because `scrollToBottom` runs after
                // layout and sets scrollTop = scrollHeight explicitly.
                style={{ scrollbarGutter: 'stable' }}
              >
                <div className="mx-auto w-full max-w-3xl">
                  {degradedNotice}
                  <TurnListErrorBoundary>
                    <TurnList sessionId={sid} />
                  </TurnListErrorBoundary>
                </div>
              </div>
              {!isAtBottom ? (
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <Button
                        type="button"
                        variant="outline"
                        size="icon-lg"
                        aria-label={scrollToBottomLabel}
                        onClick={() => scrollToBottom('smooth')}
                        className="absolute bottom-3 left-1/2 z-10 -translate-x-1/2 rounded-full border-border/80 bg-background/95 text-foreground shadow-md backdrop-blur hover:bg-muted"
                      >
                        <ArrowDownIcon className="size-4" />
                      </Button>
                    }
                  />
                  <TooltipContent side="top" sideOffset={6}>
                    {scrollToBottomLabel}
                  </TooltipContent>
                </Tooltip>
              ) : null}
            </div>
            <div className="overflow-y-auto px-2 pt-1 pb-4" style={{ scrollbarGutter: 'stable' }}>
              <div id="composer-slot" className="mx-auto w-full max-w-3xl" />
            </div>
          </div>
        ) : (
          <div className="flex h-full flex-col">
            <ChatHeader />
            <div className="flex flex-1 flex-col items-center justify-center overflow-y-auto px-2" style={{ scrollbarGutter: 'stable' }}>
              {degradedNotice}
              <div className="flex flex-col items-center gap-3 text-center">
                <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
                  <DatabaseIcon className="size-5" />
                </div>
                <h1 className="text-xl font-semibold tracking-tight">DataTalk</h1>
                <p className="text-sm text-muted-foreground">{t('session.heroSubtitle')}</p>
              </div>
              <div className="mt-8 w-full max-w-3xl mx-auto">
                <div id="composer-slot" className="w-full" />
              </div>
            </div>
          </div>
        )}
      </div>

      {/* drag handle：仅在通用设置中开启分栏拖拽调整时显示 */}
      {splitResizable && open && !maximized && (
        <div
          className="group absolute top-0 bottom-0 z-20 w-1 cursor-col-resize -translate-x-1/2 hover:bg-primary/20 transition-colors"
          style={{ left: chatWidth }}
          onPointerDown={(e) => {
            isDraggingRef.current = true
            e.currentTarget.setPointerCapture(e.pointerId)
          }}
          onPointerMove={(e) => {
            if (!isDraggingRef.current) return
            const container = rootRef.current
            if (!container) return
            const rect = container.getBoundingClientRect()
            const ratio = (e.clientX - rect.left) / rect.width
            setDragRatio(Math.min(0.8, Math.max(0.2, ratio)))
          }}
          onPointerUp={() => {
            if (!isDraggingRef.current) return
            isDraggingRef.current = false
            if (dragRatio !== null) {
              try { localStorage.setItem(SPLIT_RATIO_KEY, String(dragRatio)) } catch { /* ignore */ }
            }
          }}
        >
          <div className="absolute inset-y-0 left-1/2 w-px bg-border group-hover:bg-primary/50" />
        </div>
      )}

      {/* stage 列：translateX 滑入/滑出 */}
      <div data-stage-panel style={stageStyle}>
        <div className="h-full w-full p-2">
          <StageWindow />
        </div>
      </div>
    </div>
  )
}
