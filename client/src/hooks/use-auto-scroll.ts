import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'

const FOLLOW_THRESHOLD_PX = 150
const SAVE_SCROLL_DEBOUNCE_MS = 300

type ScrollSnapshot = { dfb: number; scrollHeight: number }

export function useAutoScroll<T extends HTMLElement>(
  deps: any[],
  resetDeps: any[] = [],
  storageKey?: string,
) {
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
  // Pending scroll-position restore from sessionStorage. Set on mount when a
  // saved position exists; cleared once applied or when scrollToBottom fires.
  const pendingScrollRestore = useRef<ScrollSnapshot | null>(null)
  const saveScrollTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  // Always holds the latest storageKey without adding it to every callback dep.
  const storageKeyRef = useRef(storageKey)
  storageKeyRef.current = storageKey

  const cancelScheduledFollow = useCallback(() => {
    if (scheduledFollowFrame.current === null) return
    cancelAnimationFrame(scheduledFollowFrame.current)
    scheduledFollowFrame.current = null
  }, [])

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    const el = ref.current
    if (!el) return

    cancelScheduledFollow()
    // Explicit scroll (user send, session switch) cancels any pending restoration.
    pendingScrollRestore.current = null
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
    const movedUp = scrollTop < lastScrollTop.current

    isAtBottom.current = nearBottom

    // Once the user cancels follow, it stays off for the rest of the
    // current turn. Only a fresh user send (resetDeps bump → scrollToBottom)
    // re-attaches — manually scrolling back to the bottom is not enough,
    // per product spec.
    if (movedUp && distanceFromBottom > 0) {
      followEnabled.current = false
    }

    lastScrollTop.current = scrollTop

    // Persist scroll position so Ctrl+R can restore it.
    const sk = storageKeyRef.current
    if (sk) {
      if (saveScrollTimer.current !== null) clearTimeout(saveScrollTimer.current)
      saveScrollTimer.current = setTimeout(() => {
        saveScrollTimer.current = null
        try {
          sessionStorage.setItem(sk, JSON.stringify({ dfb: distanceFromBottom, scrollHeight }))
        } catch { /* quota / private mode */ }
      }, SAVE_SCROLL_DEBOUNCE_MS)
    }
  }, [])

  // Scroll-event listener + initial state snapshot.
  useEffect(() => {
    const el = ref.current
    if (!el) return

    lastScrollTop.current = el.scrollTop
    isAtBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight <= FOLLOW_THRESHOLD_PX

    el.addEventListener('scroll', handleScroll)
    return () => el.removeEventListener('scroll', handleScroll)
  }, [handleScroll])

  // Detect user upward-scroll intent via input events. Scroll events alone are
  // unreliable because the browser coalesces them with programmatic scrollTo
  // calls that happen in the same frame, hiding the user's intermediate
  // upward position. Wheel / touch events fire before scrollTop is modified,
  // so they give a guaranteed signal of user intent.
  useEffect(() => {
    const el = ref.current
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
  }, [])

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
      if (saveScrollTimer.current !== null) clearTimeout(saveScrollTimer.current)
    }
  }, [cancelScheduledFollow])

  // On storageKey change (session switch), reload saved scroll position.
  // If the user was not at the bottom, set followEnabled=false and record a
  // pending restore target; scrollToBottom (triggered by session-switch or
  // user-send) clears it if the caller explicitly wants to go to bottom.
  useEffect(() => {
    pendingScrollRestore.current = null
    if (!storageKey) return
    try {
      const raw = sessionStorage.getItem(storageKey)
      if (raw !== null) {
        const snap = JSON.parse(raw) as ScrollSnapshot
        if (snap.dfb > FOLLOW_THRESHOLD_PX) {
          followEnabled.current = false
          isAtBottom.current = false
          pendingScrollRestore.current = snap
        }
        // dfb <= threshold → user was at bottom → keep followEnabled=true (default).
      }
    } catch { /* parse error */ }
  }, [storageKey])

  // Structural appends like a newly sent user bubble must land before paint,
  // otherwise the message renders at the old scroll position for one frame
  // and visibly jumps up to the bottom on the next frame.
  useLayoutEffect(() => {
    // If a saved scroll position is waiting to be restored, apply it once
    // enough content has loaded (≥90% of the stored scrollHeight). This lets
    // Ctrl+R re-land the user at their previous position rather than always
    // jumping to the bottom.
    if (pendingScrollRestore.current !== null) {
      const el = ref.current
      if (el && el.scrollHeight >= pendingScrollRestore.current.scrollHeight * 0.9) {
        const { dfb } = pendingScrollRestore.current
        pendingScrollRestore.current = null
        const targetTop = Math.max(0, el.scrollHeight - el.clientHeight - dfb)
        el.scrollTop = targetTop
        lastScrollTop.current = targetTop
        followEnabled.current = false
        isAtBottom.current = false
        return
      }
      // Content not loaded yet → skip auto-scroll; try again on next dep change.
      return
    }

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
