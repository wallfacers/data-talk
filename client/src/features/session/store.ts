import { create } from 'zustand'

type SessionUIState = {
  activeSessionId: string | null
  setActive: (id: string | null) => void
}

export const useSessionStore = create<SessionUIState>((set) => ({
  activeSessionId: null,
  setActive: (id) => set({ activeSessionId: id }),
}))
