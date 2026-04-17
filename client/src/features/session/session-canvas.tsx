import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from './use-session-mode'
import { useFlipComposer } from './use-flip-composer'
import { useSessionHistory } from './hooks/use-session-history'
import { useSessionSubscribe } from './hooks/use-session-subscribe'
import { usePendingPromptResume } from './hooks/use-pending-prompt-resume'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { useRef, useLayoutEffect, useState } from 'react'

// clip-path 裂开动画 keyframes（注入一次）
const CLIP_STYLE_ID = 'manus-clip-style'
function injectClipStyle() {
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

export function SessionCanvas() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const { mode } = useSessionMode()
  const prevMode = useRef(mode)
  const [showClipOverlay, setShowClipOverlay] = useState(false)

  // 检测 HERO → SPLIT 切换时触发裂开动画
  useLayoutEffect(() => {
    if (prevMode.current === 'HERO' && mode === 'SPLIT') {
      injectClipStyle()
      setShowClipOverlay(true)
      const timer = setTimeout(() => setShowClipOverlay(false), 260)
      return () => clearTimeout(timer)
    }
    prevMode.current = mode
  }, [mode])

  useFlipComposer()
  useSessionHistory(sessionId)
  useSessionSubscribe(sessionId)
  usePendingPromptResume()

  return (
    <div className="relative h-full">
      {/* clip-path 裂开覆盖层 */}
      {showClipOverlay && (
        <div
          className="absolute inset-0 z-20 bg-background pointer-events-none"
          style={{ animation: 'manus-clip-unlock 260ms cubic-bezier(.22,.61,.36,1) forwards' }}
        />
      )}
      <SplitView />
      <PromptComposer />
    </div>
  )
}
