import { useEffect, useRef, useState } from 'react'
import { Markdown } from '../markdown/markdown'


const PACE_MS = 24
const SNAP = /[\s.,!?;:)\]]/
const CODE_FENCE = /```|~~~/

function step(size: number): number {
  if (size <= 12) return 2
  if (size <= 48) return 4
  if (size <= 96) return 8
  return Math.min(24, Math.ceil(size / 8))
}

function next(text: string, start: number): number {
  const end = Math.min(text.length, start + step(text.length - start))
  const max = Math.min(text.length, end + 8)
  for (let i = end; i < max; i++) {
    if (SNAP.test(text[i] ?? '')) return i + 1
  }
  return end
}

export function PacedMarkdown(props: {
  text: string
  cacheKey?: string
  streaming: boolean
  className?: string
  messageId?: string
  partId?: string
}) {
  const [shown, setShown] = useState(props.streaming ? '' : props.text)
  const shownRef = useRef(shown)
  shownRef.current = shown
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const bypassPacing = props.streaming && CODE_FENCE.test(props.text)

  useEffect(() => {
    const clear = () => { if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = undefined } }

    if (!props.streaming) {
      clear()
      setShown(props.text)
      return clear
    }

    if (bypassPacing) {
      clear()
      setShown(props.text)
      return clear
    }

    if (!props.text.startsWith(shownRef.current) || props.text.length < shownRef.current.length) {
      clear()
      setShown(props.text)
      return clear
    }

    if (props.text.length === shownRef.current.length) return clear
    if (timerRef.current) return clear

    const tick = () => {
      timerRef.current = undefined
      const current = shownRef.current
      const target = props.text
      if (!props.streaming) { setShown(target); return }
      if (!target.startsWith(current) || target.length <= current.length) { setShown(target); return }
      const nextEnd = next(target, current.length)
      setShown(target.slice(0, nextEnd))
      if (nextEnd < target.length) timerRef.current = setTimeout(tick, PACE_MS)
    }
    timerRef.current = setTimeout(tick, PACE_MS)

    return clear
  }, [bypassPacing, props.text, props.streaming])

  if (!shown) return null
  return (
    <Markdown
      text={shown}
      cacheKey={props.cacheKey}
      streaming={props.streaming}
      className={props.className}
      messageId={props.messageId}
      partId={props.partId}
    />
  )
}
