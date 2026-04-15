import { create } from 'zustand'

type ConnectionUIState = {
  activeConnectionId: string | null
  setActive: (id: string | null) => void
}

export const useConnectionStore = create<ConnectionUIState>((set) => ({
  activeConnectionId: null,
  setActive: (id) => set({ activeConnectionId: id }),
}))
