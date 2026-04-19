import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createSession, type Session } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'

// 复用「创建会话」按钮的语义：优先选缓存里已有的空白会话，否则新建一个。
// excludeId 用于排除刚被删除但仍残留在 query cache 中的条目。
export function useOpenBlankSession() {
  const qc = useQueryClient()
  const connectionId = useConnectionStore((s) => s.activeConnectionId)
  const openSession = useSessionStore((s) => s.openSession)

  return useCallback(
    async (excludeId?: string) => {
      const key = ['sessions', connectionId ?? null] as const
      const cached = qc.getQueryData<Session[]>(key) ?? []
      const empty = cached.find((s) => !s.hasEverSent && s.id !== excludeId)
      if (empty) {
        openSession(empty.id, empty.hasEverSent)
        return
      }
      const sess = await createSession(connectionId ?? undefined)
      openSession(sess.id, sess.hasEverSent)
      qc.invalidateQueries({ queryKey: key })
    },
    [qc, connectionId, openSession],
  )
}
