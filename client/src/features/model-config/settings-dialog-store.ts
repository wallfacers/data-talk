import { create } from 'zustand'

type SettingsTab = 'providers' | 'models' | 'general'

interface SettingsDialogState {
  open: boolean
  tab: SettingsTab
  openDialog: (tab?: SettingsTab) => void
  closeDialog: () => void
  setTab: (tab: SettingsTab) => void
}

export const useSettingsDialogStore = create<SettingsDialogState>((set) => ({
  open: false,
  tab: 'providers',
  openDialog: (tab) => set({ open: true, tab: tab ?? 'providers' }),
  closeDialog: () => set({ open: false }),
  setTab: (tab) => set({ tab }),
}))