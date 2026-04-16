import { useSessionStore } from '@/stores/session-store'
export function useSessionMode() {
  const activeId = useSessionStore(s => s.activeSessionId)
  const mode = useSessionStore(s => activeId ? (s.modeBySession.get(activeId) ?? 'HERO') : 'NOSESS')
  return { sessionId: activeId, mode }
}
