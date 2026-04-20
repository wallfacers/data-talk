import { useLayoutEffect, useRef } from 'react'
import { useSessionMode } from './use-session-mode'

export function useFlipComposer() {
  const { mode } = useSessionMode()
  const lastRect = useRef<DOMRect | null>(null)

  useLayoutEffect(() => {
    const slot = document.getElementById('composer-slot')
    if (!slot) return
    const newRect = slot.getBoundingClientRect()
    const prev = lastRect.current
    // Skip FLIP when either snapshot has zero extent — prevents NaN scale keyframes
    const hasValidExtents = prev && prev.width > 0 && prev.height > 0 && newRect.width > 0 && newRect.height > 0
    if (hasValidExtents) {
      const dx = prev.left - newRect.left
      const dy = prev.top - newRect.top
      const sx = prev.width / newRect.width
      const sy = prev.height / newRect.height
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
