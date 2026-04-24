import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'

const FOLLOW_THRESHOLD_PX = 150
const REENABLE_THRESHOLD_PX = 4

export function useAutoScroll<T extends HTMLElement>(deps: any[]) {
  const ref = useRef<T>(null)
  const isAtBottom = useRef(true)
  const followEnabled = useRef(true)
  const lastScrollTop = useRef(0)
  const scheduledFollowFrame = useRef<number | null>(null)
  // Each deps-triggered layout-effect scroll primes this counter so the
  // MutationObserver callback (same commit, fires shortly after) is a no-op.
  // Prevents the one-frame "bubble appears low, then jumps up" flash that the
  // user sees as the message bubble jittering after Enter.
  const skipMutationScrolls = useRef(0)

  const cancelScheduledFollow = useCallback(() => {
    if (scheduledFollowFrame.current === null) return
    cancelAnimationFrame(scheduledFollowFrame.current)
    scheduledFollowFrame.current = null
  }, [])

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    const el = ref.current
    if (!el) return

    cancelScheduledFollow()
    followEnabled.current = true
    isAtBottom.current = true
    lastScrollTop.current = el.scrollHeight

    // 流式输出时保持 auto，避免 smooth 跟不上更新节奏。
    el.scrollTo({
      top: el.scrollHeight,
      behavior,
    })
  }, [cancelScheduledFollow])

  const scheduleFollow = useCallback(() => {
    if (scheduledFollowFrame.current !== null) return

    scheduledFollowFrame.current = requestAnimationFrame(() => {
      scheduledFollowFrame.current = null
      if (followEnabled.current) {
        scrollToBottom('auto')
      }
    })
  }, [scrollToBottom])

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
      if (skipMutationScrolls.current > 0) {
        skipMutationScrolls.current -= 1
        return
      }
      if (followEnabled.current) {
        scheduleFollow()
      }
    })

    observer.observe(el, {
      childList: true,
      subtree: true,
      characterData: true
    })

    return () => {
      observer.disconnect()
      cancelScheduledFollow()
    }
  }, [cancelScheduledFollow, scheduleFollow])

  // Structural appends like a newly sent user bubble must land before paint,
  // otherwise the message renders at the old scroll position for one frame
  // and visibly jumps up to the bottom on the next frame.
  useLayoutEffect(() => {
    if (followEnabled.current) {
      // Same commit also fires DOM mutations; dedupe the observer's follow-up.
      skipMutationScrolls.current += 1
      scrollToBottom('auto')
    }
  }, [scrollToBottom, ...deps])

  return { ref, scrollToBottom, isAtBottom }
}
