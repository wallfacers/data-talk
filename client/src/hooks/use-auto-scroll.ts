import { useEffect, useRef, useCallback } from 'react'

export function useAutoScroll<T extends HTMLElement>(deps: any[]) {
  const ref = useRef<T>(null)
  const isAtBottom = useRef(true)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    
    // 立即滚动，不使用 setTimeout 0，以防错过流式刷新的节奏
    if (ref.current) {
      ref.current.scrollTo({
        top: ref.current.scrollHeight,
        behavior
      })
    }
  }, [])

  const handleScroll = useCallback(() => {
    if (ref.current) {
      const { scrollTop, scrollHeight, clientHeight } = ref.current
      // 增加阈值到 150px，给流式输出留出足够的判定空间
      const atBottom = scrollHeight - scrollTop - clientHeight < 150
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

  // 监听 DOM 变化（最可靠的方式）
  useEffect(() => {
    const el = ref.current
    if (!el) return

    const observer = new MutationObserver(() => {
      if (isAtBottom.current) {
        // 流式输出时使用 'auto' 才能跟上速度，'smooth' 会有延迟且易被中断
        scrollToBottom('auto')
      }
    })

    observer.observe(el, {
      childList: true,
      subtree: true,
      characterData: true
    })

    return () => observer.disconnect()
  }, [scrollToBottom])

  // 处理依赖项变化（如切换会话）
  useEffect(() => {
    if (isAtBottom.current) {
      scrollToBottom('auto')
    }
  }, [scrollToBottom, ...deps])

  return { ref, scrollToBottom, isAtBottom }
}
