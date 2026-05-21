import { http } from '@/services/http'

export type SessionStatusType = 'idle' | 'busy' | 'retry'

export interface SessionStatusResponse {
  type: SessionStatusType
}

/**
 * Fetches the authoritative OpenCode session status for a DataTalk session.
 * Used on mount / after CTRL+R to reconcile the composer button's streaming
 * flag with the backend (BUG-0046).
 *
 * Fail-open: any network / parse error returns `{ type: 'idle' }`. The caller
 * treats idle as "show send button" — equivalent to having no information.
 */
export async function fetchSessionStatus(sessionId: string): Promise<SessionStatusResponse> {
  if (!sessionId) return { type: 'idle' }
  try {
    const data = await http
      .get(`sessions/${sessionId}/status`, { silent: true } as never)
      .json<{ type?: string }>()
    const type = data?.type
    if (type === 'busy' || type === 'retry') return { type }
    return { type: 'idle' }
  } catch {
    return { type: 'idle' }
  }
}
