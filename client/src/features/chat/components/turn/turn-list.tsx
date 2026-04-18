import { SessionTurn } from './session-turn'
import { useSessionTurns } from '../helpers/use-session-turns'
import { useSessionHistory } from '@/features/session/hooks/use-session-history'

export function TurnList(props: { sessionId: string | null; error?: Error | null | unknown }) {
  // React-query cache is shared with session-canvas' top-level prefetch;
  // this second call re-uses the cached result, no extra fetch.
  const { error: historyError } = useSessionHistory(props.sessionId)
  const effectiveError = props.error ?? historyError
  const turns = useSessionTurns(props.sessionId)

  if (effectiveError) {
    const message =
      effectiveError instanceof Error
        ? effectiveError.message
        : String(effectiveError)
    return (
      <div className="rounded border border-amber-500/40 bg-amber-50 dark:bg-amber-950/20 p-3 text-sm text-amber-800 dark:text-amber-300">
        AI 服务不可用：{message}
      </div>
    )
  }

  if (!props.sessionId || turns.length === 0) return null
  return (
    <div className="flex flex-col gap-2">
      {turns.map((t, i) => (
        <SessionTurn
          key={t.userMessageId ?? `orphan:${i}`}
          sessionId={props.sessionId!}
          userMessageId={t.userMessageId}
          userInfo={t.userInfo}
          assistantMessageIds={t.assistantMessageIds}
          isLastTurn={i === turns.length - 1}
        />
      ))}
    </div>
  )
}
