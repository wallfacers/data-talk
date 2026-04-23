import { useMemo } from 'react'
import { ChevronRightIcon } from 'lucide-react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import type { MessageInfo, Part } from '@/services/channel/types'
import { UserBubble } from './user-bubble'
import { AssistantStream } from './assistant-stream'
import { ErrorCard } from './error-card'
import { TextShimmer } from '../effects/text-shimmer'
import { useI18n } from '@/i18n/use-i18n'
import { useUISettingsStore } from '@/stores/ui-settings-store'

export function SessionTurn(props: {
  sessionId: string
  userMessageId?: string
  assistantMessageIds: string[]
  userInfo?: MessageInfo
  isLastTurn: boolean
}) {
  const { t } = useI18n()
  const autoExpandReasoning = useUISettingsStore((s) => s.autoExpandReasoning)
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

  const streaming = useChatPartsStore((s) => s.streamingBySession.has(props.sessionId))
  const working = props.isLastTurn && (
    streaming ||
    assistantMessages.some((m) => typeof m.time.completed !== 'number')
  )
  const reserveAssistantSpace = props.isLastTurn && (
    working ||
    (!!props.userInfo?.__pending && assistantMessages.length === 0)
  )
  const interrupted = assistantMessages.some((m) => m.error?.name === 'MessageAbortedError')
  const err = assistantMessages.find((m) => m.error && m.error.name !== 'MessageAbortedError')?.error

  const anyVisiblePart = useMemo(
    () => assistantMessages.some((m) => (partsMap?.get(m.id) ?? []).some((p) => {
      if (p.type === 'text') return !!(p as any).text?.trim()
      // Reasoning parts are now visible immediately even if empty (they show their own "Thinking...")
      if (p.type === 'reasoning') return true
      return p.type === 'tool'
    })),
    [assistantMessages, partsMap],
  )

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

  // working 已含 streaming 分支，因此首包延迟期（assistant message 尚未创建，或没有可见 part 时）也能显示"思考中…"。
  const showThinking = working && !err && !anyVisiblePart

  const turnDurationMs = useMemo(() => {
    const start = props.userInfo?.time.created
    if (typeof start !== 'number') return undefined
    let end: number | undefined
    for (const m of assistantMessages) {
      const c = m.time.completed
      if (typeof c === 'number') end = end === undefined ? c : Math.max(end, c)
    }
    // 使用 Math.max(0, ...) 处理可能的时钟回拨或微小误差，确保耗时始终能显示
    return end !== undefined ? Math.max(0, end - start) : undefined
  }, [props.userInfo, assistantMessages])

  return (
    <div data-component="session-turn" className="py-2">
      {props.userInfo && <UserBubble info={props.userInfo} parts={userParts} />}
      {/* Reserve space equal to the thinking indicator so the swap from
          "Thinking…" to the first (tiny) streamed text part doesn't shrink
          scrollHeight, clamp scrollTop, and bobble the pinned user bubble. */}
      <div className={reserveAssistantSpace ? 'min-h-9' : undefined}>
        <AssistantStream
          sessionId={props.sessionId}
          messages={assistantMessages}
          working={working}
          showCopyPartID={working ? null : lastTextPartId}
          turnDurationMs={turnDurationMs}
        />
        {interrupted && (
          <div className="my-2 text-center text-xs text-muted-foreground">— {t('chat.interrupted')} —</div>
        )}
        {showThinking && (
          <div className="my-2 flex flex-col">
            <div className="flex w-fit items-center gap-1.5 text-sm font-medium text-muted-foreground">
              <ChevronRightIcon size={16} className={autoExpandReasoning ? 'rotate-90' : undefined} />
              <TextShimmer text={t('chat.thinking')} active />
            </div>
          </div>
        )}
      </div>
      {err?.data?.message && <ErrorCard message={err.data.message} />}
    </div>
  )
}
