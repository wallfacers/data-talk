import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useConnections } from '../hooks/use-connections'
import { useConnectionStore } from '../store'

export function ConnectionList() {
  const { data, isLoading, isError } = useConnections()
  const { activeConnectionId: activeId, setActive } = useConnectionStore((s) => s)

  return (
    <section className="flex flex-col border-b">
      <header className="flex items-center justify-between px-3 py-2">
        <h3 className="text-xs font-medium uppercase text-muted-foreground">连接</h3>
        <Button size="sm" variant="ghost" className="h-6 text-xs">
          新建
        </Button>
      </header>
      <ul className="flex flex-col gap-0.5 px-2 pb-2">
        {isLoading &&
          Array.from({ length: 2 }).map((_, i) => (
            <li key={i}>
              <Skeleton className="h-7 w-full" />
            </li>
          ))}
        {isError && (
          <li className="px-2 text-xs text-muted-foreground">加载失败</li>
        )}
        {data?.length === 0 && (
          <li className="px-2 text-xs text-muted-foreground">暂无连接</li>
        )}
        {data?.map((conn) => {
          const active = conn.id === activeId
          return (
            <li key={conn.id}>
              <button
                onClick={() => setActive(conn.id)}
                className={cn(
                  'w-full truncate rounded-md px-2 py-1 text-left text-sm',
                  active ? 'bg-accent font-medium' : 'hover:bg-accent/50',
                )}
              >
                {conn.name}
                <span className="ml-2 text-xs text-muted-foreground">{conn.dbType}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
