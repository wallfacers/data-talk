import { useState, useRef, useEffect, type KeyboardEvent } from 'react'
import { MoreHorizontalIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { useConnectionStore } from '@/features/connection/store'

export function ChatHeader() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const { data: sessions } = useSessions()
  const session = sessions?.find((s) => s.id === sid)
  const title = session?.title ?? ''
  const qc = useQueryClient()
  const connectionId = useConnectionStore((s) => s.activeConnectionId)
  const { state } = useSidebar()

  const [editing, setEditing] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  const rename = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => renameSession(id, title),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })
      toast.success('已重命名')
    },
  })

  const del = useMutation({
    mutationFn: (id: string) => deleteSession(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })
      useSessionStore.getState().closeSession()
      toast.success('已删除')
    },
  })

  const commitRename = () => {
    const t = editTitle.trim()
    if (t && t !== title && sid) {
      rename.mutate({ id: sid, title: t })
    }
    setEditing(false)
  }

  function startRename() {
    if (!sid) return
    setEditTitle(title)
    setEditing(true)
  }

  function handleDelete() {
    if (!sid) return
    if (!window.confirm(`确定删除"${title}"？`)) return
    del.mutate(sid)
  }

  return (
    <div className={`flex h-9 shrink-0 items-center justify-between px-3 ${state === 'collapsed' ? 'pl-24' : ''}`}>
      {sid ? (
        editing ? (
          <Input
            ref={inputRef}
            value={editTitle}
            onChange={(e) => setEditTitle(e.target.value)}
            onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
              if (e.key === 'Enter') commitRename()
              if (e.key === 'Escape') setEditing(false)
            }}
            onBlur={commitRename}
            onClick={(e) => e.stopPropagation()}
            className="h-7 max-w-[240px] text-sm"
          />
        ) : (
          <span
            className="truncate text-sm font-medium cursor-text select-text"
            onDoubleClick={startRename}
            title="双击重命名"
          >
            {title}
          </span>
        )
      ) : (
        <span />
      )}
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" size="icon-xs" className="shrink-0" disabled={!sid}>
              <MoreHorizontalIcon className="size-4" />
            </Button>
          }
        />
        <DropdownMenuContent align="end" className="w-32">
          <DropdownMenuItem onClick={startRename}>
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
