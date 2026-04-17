import { create } from 'zustand'

type Section = 'general' | 'data-sources' | 'providers' | 'models'

interface SettingsDialogState {
  open: boolean
  activeSection: Section
  openDialog: (section?: Section) => void
  closeDialog: () => void
  setActiveSection: (section: Section) => void
}

export const useSettingsDialogStore = create<SettingsDialogState>((set) => ({
  open: false,
  activeSection: 'general',
  openDialog: (section) => set({ open: true, activeSection: section ?? 'general' }),
  closeDialog: () => set({ open: false }),
  setActiveSection: (section) => set({ activeSection: section }),
}))
