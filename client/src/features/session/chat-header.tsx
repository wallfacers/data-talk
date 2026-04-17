import { MoreHorizontalIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useSidebar } from '@/components/ui/sidebar'
import { useSessionStore } from '@/stores/session-store'
import { useSessions } from './hooks/use-sessions'
import { deleteSession, renameSession } from '@/services/api/session'

export function ChatHeader() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const { data: sessions } = useSessions()
  const session = sessions?.find((s) => s.id === sid)
  const title = session?.title ?? (sid ? '会话' : '演示模式')
  const qc = useQueryClient()
  const { state } = useSidebar()

  const rename = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => renameSession(id, title),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] })
      toast.success('已重命名')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const del = useMutation({
    mutationFn: (id: string) => deleteSession(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] })
      useSessionStore.getState().closeSession()
      toast.success('已删除')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  function handleRename() {
    if (!sid) return
    const next = window.prompt('新标题', title)
    if (!next || !next.trim() || next.trim() === title) return
    rename.mutate({ id: sid, title: next.trim() })
  }

  function handleDelete() {
    if (!sid) return
    if (!window.confirm(`确定删除"${title}"？`)) return
    del.mutate(sid)
  }

  return (
    <div className={`flex h-12 shrink-0 items-center justify-between px-3 ${state === 'collapsed' ? 'pl-24' : ''}`}>
      <span className="truncate text-sm font-medium">{title}</span>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" size="icon-xs" className="shrink-0" disabled={!sid}>
              <MoreHorizontalIcon className="size-4" />
            </Button>
          }
        />
        <DropdownMenuContent align="end" className="w-32">
          <DropdownMenuItem onClick={handleRename}>
            <PencilIcon />
            <span>重命名</span>
          </DropdownMenuItem>
          <DropdownMenuItem variant="destructive" onClick={handleDelete}>
            <Trash2Icon />
            <span>删除</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
