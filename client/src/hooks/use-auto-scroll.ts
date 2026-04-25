import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

const FOLLOW_THRESHOLD_PX = 150

function getMaxScrollTop(el: HTMLElement): number {
  return Math.max(0, el.scrollHeight - el.clientHeight)
}

export function useAutoScroll<T extends HTMLElement>(
  deps: any[],
  resetDeps: any[] = [],
  _storageKey?: string,
) {
  const nodeRef = useRef<T | null>(null)
  const [node, setNode] = useState<T | null>(null)
  const isAtBottomRef = useRef(true)
  const [isAtBottom, setIsAtBottom] = useState(true)
  const followEnabled = useRef(true)
  const lastScrollTop = useRef(0)
  const lastFollowScrollHeight = useRef(0)
  // A deps-triggered layout scroll should own the entire current frame. Any
  // MutationObserver callbacks that fire from the same append/reflow wave must
  // be ignored, otherwise a newly sent user bubble can land low and then get
  // "corrected" by a second scroll a moment later.
  const suppressMutationScrolls = useRef(false)
  const pendingFollowAfterSuppression = useRef(false)
  const releaseMutationSuppressionFrame = useRef<number | null>(null)
  const scheduledFollowFrame = useRef<number | null>(null)

  const ref = useCallback((nextNode: T | null) => {
    if (nodeRef.current === nextNode) return
    nodeRef.current = nextNode
    setNode(nextNode)
  }, [])

  const cancelScheduledFollow = useCallback(() => {
    if (scheduledFollowFrame.current === null) return
    cancelAnimationFrame(scheduledFollowFrame.current)
    scheduledFollowFrame.current = null
  }, [])

  const setBottomState = useCallback((next: boolean) => {
    if (isAtBottomRef.current === next) return
    isAtBottomRef.current = next
    setIsAtBottom(next)
  }, [])

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    const el = nodeRef.current
    if (!el) return

    cancelScheduledFollow()
    followEnabled.current = true
    setBottomState(true)
    // Browsers clamp scrollTop to scrollHeight - clientHeight. Recording the
    // unclamped scrollHeight makes the next native scroll event look like a
    // user-initiated upward move and incorrectly disables streaming follow.
    lastScrollTop.current = getMaxScrollTop(el)
    lastFollowScrollHeight.current = el.scrollHeight

    // 流式输出时保持 auto，避免 smooth 跟不上更新节奏。
    el.scrollTo({
      top: el.scrollHeight,
      behavior,
    })
  }, [cancelScheduledFollow, setBottomState])

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
      const shouldFollowAfterSuppression = pendingFollowAfterSuppression.current && followEnabled.current
      pendingFollowAfterSuppression.current = false
      if (shouldFollowAfterSuppression) {
        scrollToBottom('auto')
      }
    })
  }, [scrollToBottom])

  const handleScroll = useCallback(() => {
    const el = nodeRef.current
    if (!el) return

    const { scrollTop, scrollHeight, clientHeight } = el
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight
    const nearBottom = distanceFromBottom <= FOLLOW_THRESHOLD_PX
    const movedUp = scrollTop < lastScrollTop.current

    setBottomState(nearBottom)

    // Once the user cancels follow, it stays off for the rest of the
    // current turn. Only a fresh user send (resetDeps bump → scrollToBottom)
    // re-attaches — manually scrolling back to the bottom is not enough,
    // per product spec.
    if (movedUp && distanceFromBottom > 0) {
      followEnabled.current = false
    }

    lastScrollTop.current = scrollTop
  }, [setBottomState])

  // Scroll-event listener + initial state snapshot.
  useEffect(() => {
    const el = node
    if (!el) return

    lastScrollTop.current = el.scrollTop
    setBottomState(el.scrollHeight - el.scrollTop - el.clientHeight <= FOLLOW_THRESHOLD_PX)

    el.addEventListener('scroll', handleScroll)
    return () => el.removeEventListener('scroll', handleScroll)
  }, [handleScroll, node, setBottomState])

  // Detect user upward-scroll intent via input events. Scroll events alone are
  // unreliable because the browser coalesces them with programmatic scrollTo
  // calls that happen in the same frame, hiding the user's intermediate
  // upward position. Wheel / touch events fire before scrollTop is modified,
  // so they give a guaranteed signal of user intent.
  useEffect(() => {
    const el = node
    if (!el) return

    let touchStartY = 0
    const onWheel = (e: WheelEvent) => {
      if (e.deltaY < 0) followEnabled.current = false
    }
    const onTouchStart = (e: TouchEvent) => {
      touchStartY = e.touches[0]?.clientY ?? 0
    }
    const onTouchMove = (e: TouchEvent) => {
      // Finger moves down on screen → content scrolls up.
      if ((e.touches[0]?.clientY ?? 0) > touchStartY) followEnabled.current = false
    }

    el.addEventListener('wheel', onWheel, { passive: true })
    el.addEventListener('touchstart', onTouchStart, { passive: true })
    el.addEventListener('touchmove', onTouchMove, { passive: true })
    return () => {
      el.removeEventListener('wheel', onWheel)
      el.removeEventListener('touchstart', onTouchStart)
      el.removeEventListener('touchmove', onTouchMove)
    }
  }, [node])

  useEffect(() => {
    const el = node
    if (!el) return

    const handleContentGrowth = () => {
      if (suppressMutationScrolls.current) {
        if (followEnabled.current && el.scrollHeight > lastFollowScrollHeight.current) {
          pendingFollowAfterSuppression.current = true
        }
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
  }, [cancelScheduledFollow, node, scheduleFollow])

  useEffect(() => {
    return () => {
      if (releaseMutationSuppressionFrame.current !== null) {
        cancelAnimationFrame(releaseMutationSuppressionFrame.current)
      }
      pendingFollowAfterSuppression.current = false
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
  }, [node, scrollToBottom, suppressMutationsUntilNextFrame, ...deps])

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
