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
  const open = useStageStore((s) => sid ? !!s.openBySession.get(sid) : s.globalOpen)
  const maximized = useStageStore((s) => s.maximized)
  const hasMessages = useChatPartsStore((s) => {
    const parts = sid ? s.partsBySession.get(sid) : undefined
    return parts ? parts.size > 0 : false
  })
  const hasDemoMessages = useStageStore((s) => s.demoMessages.length > 0)

  // Chat：width 从 100% 收缩到 46%（右侧让位），内容居中 → 视觉上整体平滑左移
  const chatWidth = maximized ? '0%' : open ? '46%' : '100%'
  // Stage：固定 54% 宽度，translateX 从屏幕右外侧滑入到原位
  const stageWidth = maximized ? '100%' : '54%'
  const stageTranslate = maximized ? 'translateX(0)' : open ? 'translateX(0)' : 'translateX(100%)'

  const transition = `width ${DURATION}ms ${EASE}, transform ${DURATION}ms ${EASE}`

  return (
    <div className="relative h-full overflow-hidden">
      {/* ── Chat 窗体：width 动画，右侧让出空间，内容随之平滑左移 ── */}
      <div
        style={{
          position: 'absolute',
          top: 0, left: 0, bottom: 0,
          width: chatWidth,
          transition,
          overflow: 'hidden',
          willChange: 'width',
        }}
      >
        {(hasMessages || hasDemoMessages) ? (
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

      {/* ── Stage 窗体：绝对定位，translateX 从屏幕外平滑滑入/滑出 ── */}
      <div
        style={{
          position: 'absolute',
          top: 0, right: 0, bottom: 0,
          width: stageWidth,
          transform: stageTranslate,
          transition,
          willChange: 'transform',
        }}
      >
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
