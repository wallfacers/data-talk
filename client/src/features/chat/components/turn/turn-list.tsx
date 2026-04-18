import { SessionTurn } from './session-turn'
import { useSessionTurns } from '../helpers/use-session-turns'

export function TurnList(props: { sessionId: string | null }) {
  const turns = useSessionTurns(props.sessionId)
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
