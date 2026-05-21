import { create } from 'zustand'

interface ReportStore {
  selectedReportId: string | null
  expandedGroupIds: Record<string, boolean>
  setSelected: (id: string | null) => void
  toggleGroup: (groupId: string) => void
  setGroupExpanded: (groupId: string, expanded: boolean) => void
}

export const useReportStore = create<ReportStore>((set) => ({
  selectedReportId: null,
  expandedGroupIds: {},
  setSelected: (id) => set({ selectedReportId: id }),
  toggleGroup: (groupId) =>
    set((s) => ({
      expandedGroupIds: { ...s.expandedGroupIds, [groupId]: !s.expandedGroupIds[groupId] },
    })),
  setGroupExpanded: (groupId, expanded) =>
    set((s) => ({
      expandedGroupIds: { ...s.expandedGroupIds, [groupId]: expanded },
    })),
}))
