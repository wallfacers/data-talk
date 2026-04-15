import { cn } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'
import { useSessions } from '../hooks/use-sessions'
import { useSessionStore } from '../store'
import { useConnectionStore } from '@/features/connection/store'

export function SessionList() {
  const connectionId = useConnectionStore((s) => s.activeConnectionId)
  const { data, isLoading, isError } = useSessions()
  const activeId = useSessionStore((s) => s.activeSessionId)
  const setActive = useSessionStore((s) => s.setActive)

  if (!connectionId) {
    return (
      <section className="flex flex-col">
        <header className="px-3 py-2">
          <h3 className="text-xs font-medium uppercase text-muted-foreground">会话</h3>
        </header>
        <p className="px-3 pb-3 text-xs text-muted-foreground">先选择一个连接</p>
      </section>
    )
  }

  return (
    <section className="flex flex-1 flex-col overflow-hidden">
      <header className="px-3 py-2">
        <h3 className="text-xs font-medium uppercase text-muted-foreground">会话</h3>
      </header>
      <ul className="flex flex-1 flex-col gap-0.5 overflow-y-auto px-2 pb-2">
        {isLoading &&
          Array.from({ length: 3 }).map((_, i) => (
            <li key={i}>
              <Skeleton className="h-7 w-full" />
            </li>
          ))}
        {isError && <li className="px-2 text-xs text-muted-foreground">加载失败</li>}
        {data?.length === 0 && <li className="px-2 text-xs text-muted-foreground">暂无会话</li>}
        {data?.map((s) => {
          const active = s.id === activeId
          return (
            <li key={s.id}>
              <button
                onClick={() => setActive(s.id)}
                className={cn(
                  'w-full truncate rounded-md px-2 py-1 text-left text-sm',
                  active ? 'bg-accent font-medium' : 'hover:bg-accent/50',
                )}
              >
                {s.title}
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
