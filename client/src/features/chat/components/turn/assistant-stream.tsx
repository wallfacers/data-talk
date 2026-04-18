import { useMemo } from 'react'
import type { MessageInfo, Part, ToolPart, TextPart as TextPartType } from '@/services/channel/types'
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

  const flat = useMemo(() => {
    if (!partsMap || !infoMap) return []
    const arr: Array<{ part: Part; info: MessageInfo }> = []
    for (const m of props.messages) {
      const parts = partsMap.get(m.id) ?? []
      for (const p of parts) {
        if (p.type === 'reasoning' && !(p as any).text?.trim()) continue
        if (p.type === 'text' && !(p as TextPartType).text?.trim()) continue
        if (p.type === 'tool') {
          const s = (p as ToolPart).state?.status
          if ((p as ToolPart).tool === 'todowrite') continue
          if ((p as ToolPart).tool === 'question' && (s === 'pending' || s === 'running')) continue
        }
        arr.push({ part: p, info: m })
      }
    }
    return arr
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
        const info = flat.find((x) => x.part.id === g.ref.id)?.info
        if (!info) return null
        const showCopy = props.showCopyPartID === g.ref.id
        return <PartDispatcher key={g.ref.id} part={g.ref} info={info} showCopy={showCopy} turnDurationMs={props.turnDurationMs} />
      })}
    </div>
  )
}
