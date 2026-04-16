import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'

export function ConnectionOverlay() {
  const pending = useSessionStore(s => s.pendingConnectionPrompt)
  const pendingPrompt = useSessionStore(s => s.pendingPrompt)
  const setPending = useSessionStore(s => s.setPendingConnectionPrompt)
  const setPrompt = useSessionStore(s => s.setPendingPrompt)
  const connections = useConnectionStore(s => s.connections)
  const setActive = useConnectionStore(s => s.setActive)

  if (!pending) return null
  return (
    <div className="absolute left-1/2 top-24 z-50 w-96 -translate-x-1/2 rounded border bg-background p-3 shadow">
      <div className="text-sm">这是数据库相关问题但没选连接，选一个：</div>
      <ul className="mt-2 space-y-1">
        {connections.map(c => (
          <li key={c.id}>
            <button className="w-full rounded border px-2 py-1 text-left text-xs hover:bg-muted"
              onClick={() => {
                setActive(c.id)
                setPending(false)
                setPrompt(pendingPrompt)
              }}>{c.name} ({c.dbType})</button>
          </li>
        ))}
      </ul>
    </div>
  )
}
