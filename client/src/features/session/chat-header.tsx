import { useState, useRef, useEffect, type KeyboardEvent } from 'react'
import { MoreHorizontalIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useSidebar } from '@/components/ui/sidebar'
import { useSessionStore } from '@/stores/session-store'
import { useSessions } from './hooks/use-sessions'
import { useOpenBlankSession } from './hooks/use-open-blank-session'
import { deleteSession, renameSession } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useI18n } from '@/i18n/use-i18n'

// OpenCode 生成的临时标题格式，不应展示
const OPENCODE_TEMP_TITLE_REGEX = /^New session - /

export function ChatHeader() {
  const { t } = useI18n()
  const sid = useSessionStore((s) => s.activeSessionId)
  const localHasEverSent = useSessionStore((s) => s.hasEverSentBySession)
  const { data: sessions } = useSessions()
  const session = sessions?.find((s) => s.id === sid)
  const rawTitle = session?.title ?? ''
  const title = OPENCODE_TEMP_TITLE_REGEX.test(rawTitle) ? t('workspace.nav.newSession') : rawTitle
  // 使用本地缓存优先判断：本地 hasEverSent=true 说明用户已发送消息
  const isBlankSession = sid ? !(localHasEverSent.get(sid) ?? session?.hasEverSent ?? false) : false
  const qc = useQueryClient()
  const connectionId = useConnectionStore((s) => s.activeConnectionId)
  const openBlankSession = useOpenBlankSession()
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
      toast.success(t('common.renamed'))
    },
  })

  const del = useMutation({
    mutationFn: (id: string) => deleteSession(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })
      void openBlankSession(id)
      toast.success(t('common.deleted'))
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
    if (!sid || isBlankSession) return
    setEditTitle(title)
    setEditing(true)
  }

  function handleDelete() {
    if (!sid || isBlankSession) return
    if (!window.confirm(t('chat.confirmDelete', { title }))) return
    del.mutate(sid)
  }

  // 空白会话不显示标题和操作按钮
  if (!sid || isBlankSession) {
    return (
      <div className={`flex h-9 shrink-0 items-center justify-end px-3 ${state === 'collapsed' ? 'pl-24' : ''}`} />
    )
  }

  return (
    <div className={`flex h-9 shrink-0 items-center justify-between px-3 ${state === 'collapsed' ? 'pl-24' : ''}`}>
      {editing ? (
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
        <Tooltip>
          <TooltipTrigger
            render={
              <span
                className="truncate text-sm font-medium cursor-text select-text"
                onDoubleClick={startRename}
              >
                {title}
              </span>
            }
          />
          <TooltipContent side="bottom" sideOffset={4}>
            {t('chat.doubleClickRename')}
          </TooltipContent>
        </Tooltip>
      )}
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" size="icon-xs" className="shrink-0">
              <MoreHorizontalIcon className="size-4" />
            </Button>
          }
        />
        <DropdownMenuContent align="end" className="w-32">
          <DropdownMenuItem onClick={startRename}>
            <PencilIcon />
            <span>{t('common.rename')}</span>
          </DropdownMenuItem>
          <DropdownMenuItem variant="destructive" onClick={handleDelete}>
            <Trash2Icon />
            <span>{t('common.delete')}</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
