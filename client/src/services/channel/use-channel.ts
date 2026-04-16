import { useCallback, useMemo, useState } from 'react'
import { ChannelClient } from './channel-client'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useSessionStore } from '@/stores/session-store'
import { getClientHandler } from '@/features/actions/registry'

export function useChannel() {
  const [isStreaming, setIsStreaming] = useState(false)
  const sessionId = useSessionStore(s => s.activeSessionId)
  const enterSplit = useSessionStore(s => s.enterSplit)
  const upsertPart = useChatPartsStore(s => s.upsertPart)
  const upsertArtifact = useOntologyStore(s => s.upsertArtifact)
  const addArtifact = useTimelineStore(s => s.addArtifact)

  const client = useMemo(() => sessionId
      ? new ChannelClient({ baseUrl: '', sessionId, clientId: crypto.randomUUID() })
      : null, [sessionId])

  const sendMessage = useCallback(async (parts: any[]) => {
    if (!client || !sessionId) return
    setIsStreaming(true)
    enterSplit(sessionId)
    try {
      await client.sendMessage(parts, evt => {
        const { event, data } = evt
        if (event === 'message.part.created' || event === 'message.part.updated') {
          upsertPart(sessionId, (data as any).part)
        }
        if (event === 'ontology.updated') {
          const d = data as any
          if (d.objectType === 'datatalk.artifact') {
            upsertArtifact(sessionId, { id: d.id, version: d.patch?.version ?? 1,
              kind: d.patch?.kind ?? 'table',
              supersedesId: d.patch?.supersedesId,
              payload: d.patch })
            addArtifact(sessionId, d.id, d.patch?.supersedesId)
          }
        }
        if (event === 'action.invoke') {
          const { callId, actionId, input } = data as any
          const handler = getClientHandler(actionId)
          if (handler) {
            handler(input, { sessionId }).then(output => {
              client?.actionResult(callId, true, output)
            }).catch(err => {
              client?.actionResult(callId, false, undefined, { code: 'client_action_error', message: String(err) })
            })
          }
        }
      })
    } finally { setIsStreaming(false) }
  }, [client, sessionId, enterSplit, upsertPart, upsertArtifact, addArtifact])

  return { sendMessage, isStreaming }
}
