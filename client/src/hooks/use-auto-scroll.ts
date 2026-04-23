import { useCallback, useEffect, useRef } from 'react'

const FOLLOW_THRESHOLD_PX = 150
const REENABLE_THRESHOLD_PX = 4

export function useAutoScroll<T extends HTMLElement>(deps: any[]) {
  const ref = useRef<T>(null)
  const isAtBottom = useRef(true)
  const followEnabled = useRef(true)
  const lastScrollTop = useRef(0)

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    const el = ref.current
    if (!el) return

    followEnabled.current = true
    isAtBottom.current = true
    lastScrollTop.current = el.scrollHeight

    // 流式输出时保持 auto，避免 smooth 跟不上更新节奏。
    el.scrollTo({
      top: el.scrollHeight,
      behavior,
    })
  }, [])

  const handleScroll = useCallback(() => {
    const el = ref.current
    if (!el) return

    const { scrollTop, scrollHeight, clientHeight } = el
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight
    const nearBottom = distanceFromBottom <= FOLLOW_THRESHOLD_PX
    const backAtBottom = distanceFromBottom <= REENABLE_THRESHOLD_PX
    const movedUp = scrollTop < lastScrollTop.current

    isAtBottom.current = nearBottom

    if (movedUp && distanceFromBottom > 0) {
      followEnabled.current = false
    } else if (backAtBottom) {
      followEnabled.current = true
    }

    lastScrollTop.current = scrollTop
  }, [])

  useEffect(() => {
    const el = ref.current
    if (!el) return

    lastScrollTop.current = el.scrollTop
    isAtBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight <= FOLLOW_THRESHOLD_PX

    el.addEventListener('scroll', handleScroll)
    return () => el.removeEventListener('scroll', handleScroll)
  }, [handleScroll])

  useEffect(() => {
    const el = ref.current
    if (!el) return

    const observer = new MutationObserver(() => {
      if (followEnabled.current) {
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
    if (followEnabled.current) {
      scrollToBottom('auto')
    }
  }, [scrollToBottom, ...deps])

  return { ref, scrollToBottom, isAtBottom }
}
