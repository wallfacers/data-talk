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
  const [clipGeom, setClipGeom] = useState<{ r: number; x: number; y: number } | null>(null)
  const [translateLatched, setTranslateLatched] = useState<boolean>(!open)

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
  }, [revealOrigin, open])

  useEffect(() => {
    if (open) {
      setTranslateLatched(false)
      return
    }
    const t = setTimeout(() => setTranslateLatched(true), DURATION)
    return () => clearTimeout(t)
  }, [open])

  const chatWidth = maximized ? '0%' : open ? '46%' : '100%'
  const stageWidth = maximized ? '100%' : '54%'
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
    <div className="relative h-full overflow-hidden">
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
