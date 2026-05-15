import { create } from 'zustand'

interface PendingItem {
  domain: string
}

interface SemanticModelStore {
  pendingByConnection: Record<string, PendingItem[]>
  selectedDomain: string | null
  setPending: (connectionId: string, items: PendingItem[]) => void
  removePending: (connectionId: string, domain: string) => void
  selectDomain: (domain: string | null) => void
}

export const useSemanticModelStore = create<SemanticModelStore>((set) => ({
  pendingByConnection: {},
  selectedDomain: null,
  setPending: (connectionId, items) =>
    set((s) => ({ pendingByConnection: { ...s.pendingByConnection, [connectionId]: items } })),
  removePending: (connectionId, domain) =>
    set((s) => ({
      pendingByConnection: {
        ...s.pendingByConnection,
        [connectionId]: (s.pendingByConnection[connectionId] ?? []).filter(
          (item) => item.domain !== domain
        ),
      },
    })),
  selectDomain: (domain) => set({ selectedDomain: domain }),
}))
