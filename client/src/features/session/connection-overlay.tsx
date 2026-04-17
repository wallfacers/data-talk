import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'

/**
 * ConnectionOverlay — 当 AI 需要查询数据库但用户未选择连接时弹出。
 *
 * TODO (tech debt): 最终应由后端/运行时在数据库相关 action 执行时检测连接缺失，
 * 并返回结构化错误，前端据此展示提示。当前在 prompt-composer 层做前置拦截是临时方案。
 * See: docs/exec-plans/tech-debt-tracker.md
 */

export function ConnectionOverlay() {
  const pending = useSessionStore((s) => s.pendingConnectionPrompt)
  const setPending = useSessionStore((s) => s.setPendingConnectionPrompt)
  const setPrompt = useSessionStore((s) => s.setPendingPrompt)
  const { connections, setActive } = useConnectionStore()

  if (!pending) return null
  return (
    <div className="absolute left-1/2 top-24 z-50 w-96 -translate-x-1/2 rounded border bg-background p-3 shadow">
      <div className="text-sm">这是数据库相关问题但没选连接，选一个：</div>
      <ul className="mt-2 space-y-1">
        {connections.map((c) => (
          <li key={c.id}>
            <button
              className="w-full rounded border px-2 py-1 text-left text-xs hover:bg-muted"
              onClick={() => {
                setActive(c.id)
                setPending(false)
                // leave pendingPrompt untouched — resume hook will consume it
              }}
            >
              {c.kind} — {c.databaseName}
            </button>
          </li>
        ))}
        {connections.length === 0 && (
          <li className="text-xs text-muted-foreground">还没有连接。请先在侧边栏新建。</li>
        )}
      </ul>
      <div className="mt-2 text-right">
        <button
          className="text-xs text-muted-foreground hover:underline"
          onClick={() => { setPending(false); setPrompt(null) }}
        >
          取消
        </button>
      </div>
    </div>
  )
}
