import { http } from '@/services/http'

export type FileArtifactScope = 'session' | 'workspace'

export type FileArtifactStatus = 'temporary' | 'candidate' | 'archived' | 'discarded'

export type FileArtifactKind = 'report' | 'er_diagram' | 'sql_script' | 'dataset' | 'dashboard' | 'other'

export interface FileArtifact {
  id: string
  scope: FileArtifactScope
  status: FileArtifactStatus
  kind: FileArtifactKind
  sessionId: string | null
  connectionId: string | null
  filename: string
  physicalPath: string
  sizeBytes: number
  mimeType: string | null
  title: string | null
  summary: string | null
  createdAt: string
  updatedAt: string
  archivedAt: string | null
  metadata: Record<string, unknown>
}

export function listSessionFiles(sessionId: string): Promise<FileArtifact[]> {
  return http.get(`sessions/${sessionId}/files`).json<FileArtifact[]>()
}

export function listConnectionFiles(connectionId: string): Promise<FileArtifact[]> {
  return http.get(`connections/${connectionId}/files`).json<FileArtifact[]>()
}

export function markCandidate(fileArtifactId: string): Promise<void> {
  return http
    .post(`files/${fileArtifactId}/mark-candidate`)
    .then(() => undefined)
}

export function archiveFile(sessionId: string, fileArtifactId: string): Promise<void> {
  return http
    .post(`sessions/${sessionId}/files/${fileArtifactId}/archive`)
    .then(() => undefined)
}

export function discardFile(fileArtifactId: string): Promise<void> {
  return http
    .post(`files/${fileArtifactId}/discard`)
    .then(() => undefined)
}
