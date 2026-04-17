import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { DatabaseIcon } from 'lucide-react'
import { MessageStream } from '@/features/chat/components/message-stream'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'
import { StageWindow } from '@/features/stage/components/stage-window'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { ChatHeader } from './chat-header'

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
  const revealOrigin = useStageStore((s) => s.revealOrigin)
  const hasMessages = useChatPartsStore((s) => {
    const parts = sid ? s.partsBySession.get(sid) : undefined
    return parts ? parts.size > 0 : false
  })

  const stageContainerRef = useRef<HTMLDivElement>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const [clipGeom, setClipGeom] = useState<{ r: number; x: number; y: number } | null>(null)
  const [translateLatched, setTranslateLatched] = useState<boolean>(!open)
  const [dragRatio, setDragRatio] = useState<number | null>(null)
  const isDraggingRef = useRef(false)

  useLayoutEffect(() => {
    if (!revealOrigin) { setClipGeom(null); return }
    const rect = stageContainerRef.current?.getBoundingClientRect()
    if (!rect) return
    const localX = revealOrigin.x - rect.left
    const localY = revealOrigin.y - rect.top
    const r = Math.max(
      Math.hypot(localX, localY),
      Math.hypot(rect.width - localX, localY),
      Math.hypot(localX, rect.height - localY),
      Math.hypot(rect.width - localX, rect.height - localY),
    )
    setClipGeom({ r, x: localX, y: localY })
  }, [revealOrigin])

  useEffect(() => {
    if (open) {
      setTranslateLatched(false)
      return
    }
    const t = setTimeout(() => setTranslateLatched(true), DURATION)
    return () => clearTimeout(t)
  }, [open])

  // Load saved ratio on mount
  useEffect(() => { setDragRatio(loadSavedRatio()) }, [])

  const effectiveRatio = dragRatio ?? 0.46
  const chatWidth = maximized ? '0%' : open ? `${effectiveRatio * 100}%` : '100%'
  const stageWidth = maximized ? '100%' : open ? `${(1 - effectiveRatio) * 100}%` : '0%'
  const stageTransform = translateLatched ? 'translateX(100%)' : 'translateX(0px)'

  const clipPath = clipGeom
    ? `circle(${open ? clipGeom.r : 0}px at ${clipGeom.x}px ${clipGeom.y}px)`
    : `circle(${open ? 2000 : 0}px at 100% 100%)`

  const transition = `clip-path ${DURATION}ms ${EASE}, width ${DURATION}ms ${EASE}`

  const stageStyle: CSSProperties = {
    position: 'absolute',
    top: 0, right: 0, bottom: 0,
    width: stageWidth,
    transform: stageTransform,
    clipPath,
    transition,
    willChange: 'clip-path, transform',
  }

  return (
    <div ref={rootRef} className="relative h-full overflow-hidden">
      {/* chat 列 */}
      <div
        style={{
          position: 'absolute',
          top: 0, left: 0, bottom: 0,
          width: chatWidth,
          transition: `width ${DURATION}ms ${EASE}`,
          overflow: 'hidden',
          willChange: 'width',
        }}
      >
        {hasMessages ? (
          <div className="flex h-full flex-col">
            <ChatHeader />
            <div className="flex-1 overflow-y-auto px-2 py-4">
              <div className="w-full max-w-3xl mx-auto">
                <MessageStream />
              </div>
            </div>
            <div className="px-2 pb-4">
              <div id="composer-slot" className="w-full max-w-3xl mx-auto" />
            </div>
          </div>
        ) : (
          <div className="flex h-full flex-col">
            <ChatHeader />
            <div className="flex flex-1 flex-col items-center justify-center px-2">
              <div className="flex flex-col items-center gap-3 text-center">
                <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
                  <DatabaseIcon className="size-5" />
                </div>
                <h1 className="text-xl font-semibold tracking-tight">DataTalk</h1>
                <p className="text-sm text-muted-foreground">用自然语言和你的数据库对话</p>
              </div>
              <div className="mt-8 w-full max-w-3xl">
                <div id="composer-slot" />
              </div>
            </div>
          </div>
        )}
      </div>

      {/* drag handle */}
      {open && !maximized && (
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

      {/* stage 列：clip-path 气泡 + translateLatched 双段 */}
      <div ref={stageContainerRef} data-stage-panel style={stageStyle}>
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
