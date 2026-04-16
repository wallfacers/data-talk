import { create } from 'zustand'

type ChannelState = {
  isConnected: boolean
  lastEventId: number | undefined
  setConnected: (on: boolean) => void
  setLastEventId: (id: number | undefined) => void
}

export const useChannelStore = create<ChannelState>((set) => ({
  isConnected: false,
  lastEventId: undefined,
  setConnected: (on) => set({ isConnected: on }),
  setLastEventId: (id) => set({ lastEventId: id }),
}))
