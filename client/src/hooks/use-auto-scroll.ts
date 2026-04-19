import { useEffect, useRef, useCallback } from 'react'

export function useAutoScroll<T extends HTMLElement>(deps: any[]) {
  const ref = useRef<T>(null)
  const isAtBottom = useRef(true)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    
    // Use requestAnimationFrame or a small timeout to ensure DOM is updated
    timeoutRef.current = setTimeout(() => {
      if (ref.current) {
        ref.current.scrollTo({
          top: ref.current.scrollHeight,
          behavior
        })
      }
    }, 0)
  }, [])

  const handleScroll = useCallback(() => {
    if (ref.current) {
      const { scrollTop, scrollHeight, clientHeight } = ref.current
      // 阈值加大一点，防止因为 sub-pixel 或 缩放导致判断失败
      const atBottom = scrollHeight - scrollTop - clientHeight < 100
      isAtBottom.current = atBottom
    }
  }, [])

  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.addEventListener('scroll', handleScroll)
    return () => {
      el.removeEventListener('scroll', handleScroll)
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
    }
  }, [handleScroll])

  useEffect(() => {
    if (isAtBottom.current) {
      scrollToBottom('smooth')
    }
  }, [scrollToBottom, ...deps])

  return { ref, scrollToBottom, isAtBottom }
}
