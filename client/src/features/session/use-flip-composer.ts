import { useLayoutEffect, useRef } from 'react'
import { useSessionMode } from './use-session-mode'

export function useFlipComposer() {
  const { mode } = useSessionMode()
  const lastRect = useRef<DOMRect | null>(null)

  useLayoutEffect(() => {
    const slot = document.getElementById('composer-slot')
    if (!slot) return
    const newRect = slot.getBoundingClientRect()
    if (lastRect.current) {
      const dx = lastRect.current.left - newRect.left
      const dy = lastRect.current.top - newRect.top
      const sx = lastRect.current.width / newRect.width
      const sy = lastRect.current.height / newRect.height
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        slot.style.opacity = '0'
        slot.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 150, fill: 'forwards' })
      } else {
        slot.style.transformOrigin = 'top left'
        slot.animate([
          { transform: `translate(${dx}px, ${dy}px) scale(${sx}, ${sy})` },
          { transform: 'translate(0, 0) scale(1, 1)' }
        ], { duration: 260, easing: 'cubic-bezier(.22,.61,.36,1)', fill: 'both' })
      }
    }
    lastRect.current = newRect
  }, [mode])
}
