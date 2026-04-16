import { useChatPartsStore } from '@/stores/chat-parts-store'
import { PartRenderer } from './part-renderer'

export function MessageStream() {
  const partsByMessage = useChatPartsStore(s => s.partsByMessage)
  const all = Array.from(partsByMessage.values()).flat()
  return (
    <div className="flex flex-col gap-2">
      {all.map(p => <PartRenderer key={p.id} part={p} />)}
    </div>
  )
}
