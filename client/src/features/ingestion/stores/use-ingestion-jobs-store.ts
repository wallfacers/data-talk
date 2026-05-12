import { create } from 'zustand'

import type { IngestionJobView } from '../api/ingestion-api'

interface IngestionJobsState {
  editingMapping: Map<string, MappingEditState>
  setMappingEdit: (jobId: string, edits: MappingEditState) => void
  clearMappingEdit: (jobId: string) => void
  hydrateFromJob: (job: IngestionJobView) => void
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
  hydrateFromJob: (job) =>
    set((s) => {
      if (s.editingMapping.has(job.id)) return s
      if (!job.mapping || job.mapping.columns.length === 0) return s
      const next = new Map(s.editingMapping)
      next.set(job.id, {
        columns: job.mapping.columns.map((c) => ({
          sourcePath: c.sourcePath,
          targetName: c.targetName,
          type: c.type,
          skip: c.skip,
          sampleValues: c.sampleValues,
          nullable: c.nullable,
        })),
        targetTable: job.targetTable ?? 'ingested_data',
        targetSchema: job.targetSchema,
      })
      return { editingMapping: next }
    }),
}))
