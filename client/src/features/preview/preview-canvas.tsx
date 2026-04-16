// 预览画布：完全本地 state 驱动 HERO/SPLIT，不读 useSessionMode，也不用 portal。
// 保留生产的 MessageStream / ArtifactTimelineStrip / ArtifactCanvas / ChartArtifact 等组件渲染，
// 它们从 session-store.activeSessionId + chat/ontology/timeline stores 取数据。
// 种子脚本只负责把活跃 session 设成 MOCK_SESSION_ID，并把事件推进各个 store。

import { useEffect, useState } from 'react'
import { Panel, PanelGroup, PanelResizeHandle } from 'react-resizable-panels'
import { MessageStream } from '@/features/chat/components/message-stream'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { PreviewComposer } from './preview-composer'
import { MOCK_GREEN_PROMPT, MOCK_USER_PROMPT } from './preview-mock-data'
import { playGreenFollowup, playMainScript } from './preview-script'

const CLIP_STYLE_ID = 'manus-clip-style'
function injectClipStyle() {
  if (typeof document === 'undefined') return
  if (document.getElementById(CLIP_STYLE_ID)) return
  const style = document.createElement('style')
  style.id = CLIP_STYLE_ID
  style.textContent = `
    @keyframes manus-clip-unlock {
      from { clip-path: inset(0 50% 0 0) }
      to { clip-path: inset(0 0 0 0) }
    }
  `
  document.head.appendChild(style)
}

type LocalMode = 'HERO' | 'SPLIT'

export function PreviewCanvas({ resetToken }: { resetToken: number }) {
  const [mode, setMode] = useState<LocalMode>('HERO')
  const [showClip, setShowClip] = useState(false)
  const [isPlaying, setIsPlaying] = useState(false)
  const [hasRunMain, setHasRunMain] = useState(false)

  // 外部 resetToken 变化时回到 HERO
  useEffect(() => {
    setMode('HERO')
    setShowClip(false)
    setIsPlaying(false)
    setHasRunMain(false)
  }, [resetToken])

  const handleSubmit = async (text: string) => {
    if (isPlaying) return
    setIsPlaying(true)
    try {
      if (mode === 'HERO') {
        injectClipStyle()
        setMode('SPLIT')
        setShowClip(true)
        window.setTimeout(() => setShowClip(false), 260)
      }
      if (!hasRunMain) {
        setHasRunMain(true)
        await playMainScript(text)
      } else {
        await playGreenFollowup(text)
      }
    } finally {
      setIsPlaying(false)
    }
  }

  const placeholder = hasRunMain
    ? `追问示例："${MOCK_GREEN_PROMPT}"（Enter 发送，触发原地变色）`
    : `Enter 发送 · 例如 "${MOCK_USER_PROMPT}"`

  return (
    <div className="relative h-full">
      {showClip && (
        <div
          className="pointer-events-none absolute inset-0 z-20 bg-background"
          style={{ animation: 'manus-clip-unlock 260ms cubic-bezier(.22,.61,.36,1) forwards' }}
        />
      )}

      {mode === 'HERO' && (
        <div className="flex h-full items-center justify-center p-8">
          <div className="w-full max-w-2xl">
            <h1 className="mb-6 text-center text-xl font-light text-muted-foreground">
              问点什么，比如 "{MOCK_USER_PROMPT}"
            </h1>
            <PreviewComposer
              onSubmit={handleSubmit}
              isStreaming={isPlaying}
              placeholder={placeholder}
            />
          </div>
        </div>
      )}

      {mode === 'SPLIT' && (
        <PanelGroup direction="horizontal" className="h-full">
          <Panel defaultSize={48} minSize={25}>
            <div className="flex h-full flex-col">
              <div className="flex-1 overflow-y-auto p-4">
                <MessageStream />
              </div>
              <PreviewComposer
                onSubmit={handleSubmit}
                isStreaming={isPlaying}
                placeholder={placeholder}
              />
            </div>
          </Panel>
          <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
          <Panel defaultSize={52} minSize={25}>
            <div className="flex h-full flex-col">
              <ArtifactTimelineStrip />
              <div className="flex-1 overflow-hidden">
                <ArtifactCanvas />
              </div>
            </div>
          </Panel>
        </PanelGroup>
      )}
    </div>
  )
}
