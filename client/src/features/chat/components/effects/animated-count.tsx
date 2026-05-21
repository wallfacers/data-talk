import { useEffect, useState, useRef } from 'react'

export function AnimatedCount(props: { value: number; duration?: number }) {
  const [display, setDisplay] = useState(props.value)
  const prevRef = useRef(props.value)
  const duration = props.duration ?? 400

  useEffect(() => {
    const from = prevRef.current
    const to = props.value
    if (from === to) return
    const start = performance.now()
    let raf = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      setDisplay(Math.round(from + (to - from) * eased))
      if (t < 1) raf = requestAnimationFrame(tick)
      else prevRef.current = to
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [props.value, duration])

  return <span>{display}</span>
}
