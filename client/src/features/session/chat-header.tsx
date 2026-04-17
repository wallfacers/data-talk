import { MoreHorizontalIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useSessionStore } from '@/stores/session-store'
import { useSessions } from './hooks/use-sessions'

export function ChatHeader() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const { data: sessions } = useSessions()
  const session = sessions?.find((s) => s.id === sid)
  const title = session?.title ?? (sid ? '会话' : '演示模式')

  return (
    <div className="flex h-11 shrink-0 items-center justify-between px-3">
      <span className="truncate text-sm font-medium">{title}</span>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" size="icon-xs" className="shrink-0">
              <MoreHorizontalIcon className="size-4" />
            </Button>
          }
        />
        <DropdownMenuContent align="end" className="w-32">
          <DropdownMenuItem onClick={() => toast.info(`重命名"${title}"：占位`)}>
            <PencilIcon />
            <span>重命名</span>
          </DropdownMenuItem>
          <DropdownMenuItem
            variant="destructive"
            onClick={() => toast.info(`删除"${title}"：占位`)}
          >
            <Trash2Icon />
            <span>删除</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
