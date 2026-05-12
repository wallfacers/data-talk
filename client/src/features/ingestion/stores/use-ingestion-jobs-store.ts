import { create } from 'zustand'

interface IngestionJobsState {
  editingMapping: Map<string, MappingEditState>
  setMappingEdit: (jobId: string, edits: MappingEditState) => void
  clearMappingEdit: (jobId: string) => void
}

export interface MappingEditState {
  columns: MappingColumnEdit[]
  targetTable: string
  targetSchema: string | null
}

export interface MappingColumnEdit {
  sourcePath: string
  targetName: string
  type: string
  skip: boolean
  sampleValues: string[]
  nullable: boolean
}

export const useIngestionJobsStore = create<IngestionJobsState>((set) => ({
  editingMapping: new Map(),
  setMappingEdit: (jobId, edits) =>
    set((s) => {
      const next = new Map(s.editingMapping)
      next.set(jobId, edits)
      return { editingMapping: next }
    }),
  clearMappingEdit: (jobId) =>
    set((s) => {
      const next = new Map(s.editingMapping)
      next.delete(jobId)
      return { editingMapping: next }
    }),
}))
