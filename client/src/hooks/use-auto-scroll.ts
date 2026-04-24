import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'

const FOLLOW_THRESHOLD_PX = 150
const REENABLE_THRESHOLD_PX = 4

export function useAutoScroll<T extends HTMLElement>(deps: any[], resetDeps: any[] = []) {
  const ref = useRef<T>(null)
  const isAtBottom = useRef(true)
  const followEnabled = useRef(true)
  const lastScrollTop = useRef(0)
  // A deps-triggered layout scroll should own the entire current frame. Any
  // MutationObserver callbacks that fire from the same append/reflow wave must
  // be ignored, otherwise a newly sent user bubble can land low and then get
  // "corrected" by a second scroll a moment later.
  const suppressMutationScrolls = useRef(false)
  const releaseMutationSuppressionFrame = useRef<number | null>(null)
  const scheduledFollowFrame = useRef<number | null>(null)

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

  const suppressMutationsUntilNextFrame = useCallback(() => {
    suppressMutationScrolls.current = true
    if (releaseMutationSuppressionFrame.current !== null) {
      cancelAnimationFrame(releaseMutationSuppressionFrame.current)
    }
    releaseMutationSuppressionFrame.current = requestAnimationFrame(() => {
      suppressMutationScrolls.current = false
      releaseMutationSuppressionFrame.current = null
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

    const handleContentGrowth = () => {
      if (suppressMutationScrolls.current) {
        return
      }
      if (followEnabled.current) {
        scheduleFollow()
      }
    }

    const observer = new MutationObserver(handleContentGrowth)
    const resizeObserver = typeof ResizeObserver === 'undefined'
      ? null
      : new ResizeObserver(handleContentGrowth)

    observer.observe(el, {
      childList: true,
      subtree: true,
      characterData: true,
    })
    resizeObserver?.observe(el)

    return () => {
      observer.disconnect()
      resizeObserver?.disconnect()
      cancelScheduledFollow()
    }
  }, [cancelScheduledFollow, scheduleFollow])

  useEffect(() => {
    return () => {
      if (releaseMutationSuppressionFrame.current !== null) {
        cancelAnimationFrame(releaseMutationSuppressionFrame.current)
      }
      cancelScheduledFollow()
    }
  }, [cancelScheduledFollow])

  // Structural appends like a newly sent user bubble must land before paint,
  // otherwise the message renders at the old scroll position for one frame
  // and visibly jumps up to the bottom on the next frame.
  useLayoutEffect(() => {
    if (followEnabled.current) {
      suppressMutationsUntilNextFrame()
      scrollToBottom('auto')
    }
  }, [scrollToBottom, suppressMutationsUntilNextFrame, ...deps])

  // A bump in resetDeps represents a user-initiated action (e.g. sending a
  // new message) that must override any earlier "user scrolled up" state.
  // Without this, once followEnabled is flipped off by an upward scroll, it
  // never re-enables for subsequent sends unless the user first scrolls back
  // to the bottom.
  const didMountReset = useRef(false)
  useLayoutEffect(() => {
    if (!didMountReset.current) {
      didMountReset.current = true
      return
    }
    suppressMutationsUntilNextFrame()
    scrollToBottom('auto')
  }, [scrollToBottom, suppressMutationsUntilNextFrame, ...resetDeps])

  return { ref, scrollToBottom, isAtBottom }
}
