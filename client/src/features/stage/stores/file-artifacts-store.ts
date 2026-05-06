import { create } from 'zustand'
import {
  archiveFile,
  discardFile,
  listConnectionFiles,
  listSessionFiles,
  markCandidate,
  type FileArtifact,
  type FileArtifactKind,
} from '@/services/api/file-artifacts'

export type FileArtifactDtEvent =
  | {
      type: 'file_artifact.detected'
      data: {
        fileArtifactId: string
        sessionId: string
        filename: string
        kind: FileArtifactKind
        status: 'temporary' | 'candidate'
        sizeBytes: number
      }
    }
  | {
      type: 'file_artifact.archive_requested'
      data: {
        fileArtifactId: string
        sessionId: string
        kind: FileArtifactKind
        title: string
        summary: string
      }
    }
  | {
      type: 'file_artifact.archived'
      data: {
        fileArtifactId: string
        sessionId: string | null
        connectionId: string
        filename: string
        physicalPath: string
      }
    }
  | {
      type: 'file_artifact.discarded'
      data: { fileArtifactId: string; reason: string }
    }
  | {
      type: 'file_artifact.legacy_migrated'
      data: { filesMovedCount: number }
    }

export type SessionFileGroups = {
  temporary: FileArtifact[]
  candidate: FileArtifact[]
}

export type ConnectionFileGroups = Record<FileArtifactKind, FileArtifact[]>

const EMPTY_KIND_GROUPS: ConnectionFileGroups = {
  report: [],
  er_diagram: [],
  sql_script: [],
  dataset: [],
  other: [],
}

interface FileArtifactsState {
  bySessionId: Record<string, FileArtifact[]>
  byConnectionId: Record<string, FileArtifact[]>
  loading: boolean
  error: string | null

  fetchForSession: (sessionId: string) => Promise<void>
  fetchForConnection: (connectionId: string) => Promise<void>

  applyDtEvent: (event: FileArtifactDtEvent) => void

  archive: (sessionId: string, fileArtifactId: string) => Promise<void>
  discard: (fileArtifactId: string) => Promise<void>
  promote: (fileArtifactId: string) => Promise<void>

  selectSessionFiles: (sessionId: string) => SessionFileGroups
  selectConnectionFiles: (connectionId: string) => ConnectionFileGroups
}

function upsertById(list: FileArtifact[], next: FileArtifact): FileArtifact[] {
  const idx = list.findIndex((item) => item.id === next.id)
  if (idx === -1) return [...list, next]
  const copy = [...list]
  copy[idx] = next
  return copy
}

function removeById(list: FileArtifact[] | undefined, id: string): FileArtifact[] {
  if (!list) return []
  return list.filter((item) => item.id !== id)
}

export const useFileArtifactsStore = create<FileArtifactsState>((set, get) => ({
  bySessionId: {},
  byConnectionId: {},
  loading: false,
  error: null,

  fetchForSession: async (sessionId) => {
    set({ loading: true, error: null })
    try {
      const files = await listSessionFiles(sessionId)
      set((state) => ({
        bySessionId: { ...state.bySessionId, [sessionId]: files },
        loading: false,
      }))
    } catch (err) {
      set({ loading: false, error: err instanceof Error ? err.message : String(err) })
    }
  },

  fetchForConnection: async (connectionId) => {
    set({ loading: true, error: null })
    try {
      const files = await listConnectionFiles(connectionId)
      set((state) => ({
        byConnectionId: { ...state.byConnectionId, [connectionId]: files },
        loading: false,
      }))
    } catch (err) {
      set({ loading: false, error: err instanceof Error ? err.message : String(err) })
    }
  },

  applyDtEvent: (event) => {
    if (event.type === 'file_artifact.legacy_migrated') return

    set((state) => {
      const bySessionId = { ...state.bySessionId }
      const byConnectionId = { ...state.byConnectionId }

      switch (event.type) {
        case 'file_artifact.detected': {
          const sid = event.data.sessionId
          const placeholder: FileArtifact = {
            id: event.data.fileArtifactId,
            scope: 'session',
            status: event.data.status,
            kind: event.data.kind,
            sessionId: sid,
            connectionId: null,
            filename: event.data.filename,
            physicalPath: '',
            sizeBytes: event.data.sizeBytes,
            mimeType: null,
            title: null,
            summary: null,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            archivedAt: null,
            metadata: {},
          }
          const existing = bySessionId[sid] ?? []
          bySessionId[sid] = upsertById(existing, placeholder)
          break
        }
        case 'file_artifact.archive_requested': {
          const sid = event.data.sessionId
          const list = bySessionId[sid] ?? []
          bySessionId[sid] = list.map((file) =>
            file.id === event.data.fileArtifactId
              ? {
                  ...file,
                  status: 'candidate',
                  kind: event.data.kind,
                  title: event.data.title,
                  summary: event.data.summary,
                }
              : file,
          )
          break
        }
        case 'file_artifact.archived': {
          const { fileArtifactId, sessionId, connectionId, filename, physicalPath } = event.data
          // Session→connection archive path
          let previous: FileArtifact | undefined
          if (sessionId && bySessionId[sessionId]) {
            previous = bySessionId[sessionId].find((f) => f.id === fileArtifactId)
            bySessionId[sessionId] = removeById(bySessionId[sessionId], fileArtifactId)
          }
          // Reattach path: find in existing connection lists (orphan→new connection)
          if (!previous) {
            for (const cid of Object.keys(byConnectionId)) {
              const found = byConnectionId[cid].find((f) => f.id === fileArtifactId)
              if (found) {
                previous = found
                byConnectionId[cid] = removeById(byConnectionId[cid], fileArtifactId)
                break
              }
            }
          }
          const archived: FileArtifact = {
            id: fileArtifactId,
            scope: 'workspace',
            status: 'archived',
            kind: previous?.kind ?? 'other',
            sessionId: sessionId ?? previous?.sessionId ?? null,
            connectionId,
            filename,
            physicalPath,
            sizeBytes: previous?.sizeBytes ?? 0,
            mimeType: previous?.mimeType ?? null,
            title: previous?.title ?? null,
            summary: previous?.summary ?? null,
            createdAt: previous?.createdAt ?? new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            archivedAt: new Date().toISOString(),
            metadata: previous?.metadata ?? {},
          }
          byConnectionId[connectionId] = upsertById(byConnectionId[connectionId] ?? [], archived)
          break
        }
        case 'file_artifact.discarded': {
          const { fileArtifactId } = event.data
          for (const sid of Object.keys(bySessionId)) {
            bySessionId[sid] = removeById(bySessionId[sid], fileArtifactId)
          }
          for (const cid of Object.keys(byConnectionId)) {
            byConnectionId[cid] = removeById(byConnectionId[cid], fileArtifactId)
          }
          break
        }
      }

      return { bySessionId, byConnectionId }
    })
  },

  archive: async (sessionId, fileArtifactId) => {
    await archiveFile(sessionId, fileArtifactId)
  },

  discard: async (fileArtifactId) => {
    await discardFile(fileArtifactId)
  },

  promote: async (fileArtifactId) => {
    await markCandidate(fileArtifactId)
  },

  selectSessionFiles: (sessionId) => {
    const list = get().bySessionId[sessionId] ?? []
    return {
      temporary: list.filter((file) => file.status === 'temporary'),
      candidate: list.filter((file) => file.status === 'candidate'),
    }
  },

  selectConnectionFiles: (connectionId) => {
    const list = get().byConnectionId[connectionId] ?? []
    const groups: ConnectionFileGroups = {
      report: [],
      er_diagram: [],
      sql_script: [],
      dataset: [],
      other: [],
    }
    for (const file of list) {
      if (file.status !== 'archived') continue
      groups[file.kind].push(file)
    }
    return groups
  },
}))

export const EMPTY_CONNECTION_FILE_GROUPS = EMPTY_KIND_GROUPS
