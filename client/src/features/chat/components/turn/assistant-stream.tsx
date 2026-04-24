import { useMemo } from 'react'
import type {
  MessageInfo,
  Part,
  ReasoningPart as ReasoningPartType,
  ToolPart,
  TextPart as TextPartType,
} from '@/services/channel/types'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useActionRegistryStore } from '@/stores/action-registry-store'
import { PartDispatcher } from './part-dispatcher'
import { ContextToolGroup } from './context-tool-group'
import { groupParts, type PartGroup } from '../helpers/group-parts'

export function AssistantStream(props: {
  sessionId: string
  messages: MessageInfo[]
  working: boolean
  showCopyPartID: string | null
  turnDurationMs?: number
}) {
  const partsMap = useChatPartsStore((s) => s.partsBySession.get(props.sessionId))
  const infoMap = useChatPartsStore((s) => s.infoBySession.get(props.sessionId))
  const descriptors = useActionRegistryStore((s) => s.descriptors)

  const { flat, infoByPartId } = useMemo(() => {
    const arr: Array<{ part: Part; info: MessageInfo }> = []
    const byId = new Map<string, MessageInfo>()
    if (!partsMap || !infoMap) return { flat: arr, infoByPartId: byId }
    for (const m of props.messages) {
      const parts = partsMap.get(m.id) ?? []
      for (const p of parts) {
        if (p.type === 'text' && !(p as TextPartType).text?.trim()) continue
        if (p.type === 'reasoning' && !(p as ReasoningPartType).text?.trim()) continue
        if (p.type === 'tool') {
          const s = (p as ToolPart).state?.status
          if ((p as ToolPart).tool === 'todowrite') continue
          if ((p as ToolPart).tool === 'question' && (s === 'pending' || s === 'running')) continue
        }
        arr.push({ part: p, info: m })
        byId.set(p.id, m)
      }
    }
    return { flat: arr, infoByPartId: byId }
  }, [partsMap, infoMap, props.messages])

  const groups: PartGroup[] = useMemo(
    () => groupParts(flat.map((x) => x.part), (p) => {
      if (p.type !== 'tool') return false
      const desc = descriptors[(p as ToolPart).tool]
      return desc?.category === 'metadata'
    }),
    [flat, descriptors],
  )

  const lastKey = groups.at(-1) && (groups[groups.length - 1].type === 'context-group'
    ? (groups[groups.length - 1] as any).key
    : 'part:' + ((groups[groups.length - 1] as any).ref?.id))

  return (
    <div className="flex flex-col gap-1">
      {groups.map((g) => {
        if (g.type === 'context-group') {
          const busy = props.working && lastKey === g.key
          return (
            <ContextToolGroup
              key={g.key}
              parts={g.refs as ToolPart[]}
              infos={infoMap!}
              busy={busy}
            />
          )
        }
        const info = infoByPartId.get(g.ref.id)
        if (!info) return null
        const showCopy = props.showCopyPartID === g.ref.id
        return <PartDispatcher key={g.ref.id} part={g.ref} info={info} showCopy={showCopy} turnDurationMs={props.turnDurationMs} />
      })}
    </div>
  )
}
