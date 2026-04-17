import { useSessionStore } from '@/stores/session-store'

export function ModelOverlay() {
  const pending = useSessionStore((s) => s.pendingModelPrompt)
  const setPending = useSessionStore((s) => s.setPendingModelPrompt)
  const setPrompt = useSessionStore((s) => s.setPendingPrompt)

  if (!pending) return null
  return (
    <div className="absolute left-1/2 top-24 z-50 w-96 -translate-x-1/2 rounded border bg-background p-3 shadow">
      <div className="text-sm">请先配置一个 AI 模型才能发送消息。</div>
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
