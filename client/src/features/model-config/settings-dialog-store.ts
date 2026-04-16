import { create } from 'zustand'

type MainTab = 'general' | 'providers' | 'models'
type GeneralSubTab = 'general-settings' | 'account' | 'data' | 'terms'

interface SettingsDialogState {
  open: boolean
  tab: MainTab
  generalSubTab: GeneralSubTab
  openDialog: (tab?: MainTab) => void
  closeDialog: () => void
  setTab: (tab: MainTab) => void
  setGeneralSubTab: (subTab: GeneralSubTab) => void
}

export const useSettingsDialogStore = create<SettingsDialogState>((set) => ({
  open: false,
  tab: 'general',
  generalSubTab: 'general-settings',
  openDialog: (tab) => set({ open: true, tab: tab ?? 'general', generalSubTab: 'general-settings' }),
  closeDialog: () => set({ open: false }),
  setTab: (tab) => set({ tab, generalSubTab: 'general-settings' }),
  setGeneralSubTab: (generalSubTab) => set({ generalSubTab }),
}))
