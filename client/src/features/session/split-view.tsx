import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { DatabaseIcon } from 'lucide-react'
import { TurnList } from '@/features/chat/components/turn/turn-list'
import { TurnListErrorBoundary } from '@/features/chat/components/turn/turn-list-error-boundary'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'
import { StageWindow } from '@/features/stage/components/stage-window'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import { ChatHeader } from './chat-header'
import { useAutoScroll } from '@/hooks/use-auto-scroll'

const DURATION = 400
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
  const sid = useSessionStore((s) => s.activeSessionId)
  const open = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const maximized = useStageStore((s) => (sid ? !!s.maximizedBySession.get(sid) : false))
  const hasMessages = useChatPartsStore((s) => {
    const info = sid ? s.infoBySession.get(sid) : undefined
    return info ? info.size > 0 : false
  })

  const splitResizable = useUISettingsStore((s) => s.splitResizable)
  const rootRef = useRef<HTMLDivElement>(null)
  const [dragRatio, setDragRatio] = useState<number | null>(null)
  const isDraggingRef = useRef(false)

  // Track store version to trigger auto-scroll on any change (including streaming text)
  const version = useChatPartsStore((s) => s.version)

  const { ref: scrollRef, scrollToBottom, isAtBottom } = useAutoScroll<HTMLDivElement>([version])

  // Scroll to bottom on session change
  useEffect(() => {
    if (sid) {
      isAtBottom.current = true
      scrollToBottom('auto')
    }
  }, [sid, scrollToBottom, isAtBottom])

  // Load saved ratio on mount
  useEffect(() => { setDragRatio(loadSavedRatio()) }, [])

  const effectiveRatio = dragRatio ?? 0.46
  // Chat width never collapses to 0 — use translateX to slide it off screen during
  // maximize so inner content doesn't reflow (which caused message jitter).
  const chatWidth = open ? `${effectiveRatio * 100}%` : '100%'
  const chatTransform = maximized ? 'translateX(-100%)' : 'translateX(0)'
  const stageWidth = maximized ? '100%' : `${(1 - effectiveRatio) * 100}%`
  const stageTransform = open ? 'translateX(0)' : 'translateX(100%)'

  // Maximize: chat slides out first (220ms), stage expands after 60ms delay (320ms).
  // Restore: stage shrinks immediately (DURATION), chat slides back after 80ms delay.
  const chatTransition = maximized
    ? 'transform 220ms ease-in'
    : `transform 350ms ${EASE} 80ms, width ${DURATION}ms ${EASE}`
  const stageTransition = maximized
    ? `width 320ms ${EASE} 60ms`
    : `transform ${DURATION}ms ${EASE}, width 320ms ${EASE}`

  const stageStyle: CSSProperties = {
    position: 'absolute',
    top: 0, right: 0, bottom: 0,
    width: stageWidth,
    transform: stageTransform,
    transition: stageTransition,
    willChange: 'transform, width',
  }

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
            <div ref={scrollRef} className="flex-1 overflow-y-auto px-2 py-4" style={{ scrollbarGutter: 'stable' }}>
              <div className="mx-auto w-full max-w-3xl">
                <TurnListErrorBoundary>
                  <TurnList sessionId={sid} />
                </TurnListErrorBoundary>
              </div>
            </div>
            <div className="px-2 pb-4 overflow-hidden" style={{ scrollbarGutter: 'stable' }}>
              <div id="composer-slot" className="mx-auto w-full max-w-3xl" />
            </div>
          </div>
        ) : (
          <div className="flex h-full flex-col">
            <ChatHeader />
            <div className="flex flex-1 flex-col items-center justify-center px-2 overflow-hidden" style={{ scrollbarGutter: 'stable' }}>
              <div className="flex flex-col items-center gap-3 text-center">
                <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
                  <DatabaseIcon className="size-5" />
                </div>
                <h1 className="text-xl font-semibold tracking-tight">DataTalk</h1>
                <p className="text-sm text-muted-foreground">用自然语言和你的数据库对话</p>
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
          <StageWindow sessionId={sid ?? undefined}>
            {sid && <ArtifactTimelineStrip />}
            {sid && (
              <div className="flex-1 min-h-0 overflow-hidden">
                <ArtifactCanvas />
              </div>
            )}
          </StageWindow>
        </div>
      </div>
    </div>
  )
}
