import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import type { MessageInfo, Part } from '@/services/channel/types'
import { UserBubble } from './user-bubble'
import { AssistantStream } from './assistant-stream'
import { ErrorCard } from './error-card'
import { TextShimmer } from '../effects/text-shimmer'
import { TextReveal } from '../effects/text-reveal'
import { extractHeading } from '../helpers/reasoning-heading'

export function SessionTurn(props: {
  sessionId: string
  userMessageId?: string
  assistantMessageIds: string[]
  userInfo?: MessageInfo
  isLastTurn: boolean
}) {
  const partsMap = useChatPartsStore((s) => s.partsBySession.get(props.sessionId))
  const infoMap = useChatPartsStore((s) => s.infoBySession.get(props.sessionId))

  const assistantMessages = useMemo(
    () => props.assistantMessageIds.map((id) => infoMap?.get(id)).filter((x): x is MessageInfo => !!x),
    [props.assistantMessageIds, infoMap],
  )

  const userParts: Part[] = useMemo(() => {
    if (!props.userMessageId || !partsMap) return []
    return partsMap.get(props.userMessageId) ?? []
  }, [props.userMessageId, partsMap])

  const working = props.isLastTurn && assistantMessages.some((m) => typeof m.time.completed !== 'number')
  const interrupted = assistantMessages.some((m) => m.error?.name === 'MessageAbortedError')
  const err = assistantMessages.find((m) => m.error && m.error.name !== 'MessageAbortedError')?.error

  const anyVisiblePart = useMemo(
    () => assistantMessages.some((m) => (partsMap?.get(m.id) ?? []).some((p) => {
      if (p.type === 'text') return !!(p as any).text?.trim()
      if (p.type === 'reasoning') return !!(p as any).text?.trim()
      return p.type === 'tool'
    })),
    [assistantMessages, partsMap],
  )

  const reasoningHeading = useMemo(() => {
    for (const m of assistantMessages) {
      const parts = partsMap?.get(m.id) ?? []
      for (const p of parts) {
        if (p.type === 'reasoning') {
          const h = extractHeading((p as any).text ?? '')
          if (h) return h
        }
      }
    }
    return undefined
  }, [assistantMessages, partsMap])

  const lastTextPartId = useMemo(() => {
    for (let i = assistantMessages.length - 1; i >= 0; i--) {
      const m = assistantMessages[i]
      const parts = partsMap?.get(m.id) ?? []
      for (let j = parts.length - 1; j >= 0; j--) {
        const p = parts[j]
        if (p.type === 'text' && (p as any).text?.trim()) return p.id
      }
    }
    return null
  }, [assistantMessages, partsMap])

  const showThinking = working && !err && !anyVisiblePart

  const turnDurationMs = useMemo(() => {
    const start = props.userInfo?.time.created
    if (typeof start !== 'number') return undefined
    let end: number | undefined
    for (const m of assistantMessages) {
      const c = m.time.completed
      if (typeof c === 'number') end = end === undefined ? c : Math.max(end, c)
    }
    return end !== undefined && end >= start ? end - start : undefined
  }, [props.userInfo, assistantMessages])

  return (
    <div data-component="session-turn" className="py-2">
      {props.userInfo && <UserBubble info={props.userInfo} parts={userParts} />}
      <AssistantStream
        sessionId={props.sessionId}
        messages={assistantMessages}
        working={working}
        showCopyPartID={working ? null : lastTextPartId}
        turnDurationMs={turnDurationMs}
      />
      {interrupted && (
        <div className="my-2 text-center text-xs text-muted-foreground">— 已中断 —</div>
      )}
      {showThinking && (
        <div className="my-1 text-sm text-muted-foreground">
          <TextShimmer text="思考中…" active />
          {reasoningHeading && <div className="mt-1 text-xs"><TextReveal text={reasoningHeading} /></div>}
        </div>
      )}
      {err?.data?.message && <ErrorCard message={err.data.message} />}
    </div>
  )
}
