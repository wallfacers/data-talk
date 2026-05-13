import { http } from '@/services/http'
import type { FileArtifact } from './file-artifacts'

export interface StorageOverviewDto {
  workdir: string
  totalBytes: number
  breakdown: Record<string, { bytes: number; label: string }>
  lastHousekeepingRunAt: string | null
}

export interface CleanupStatsDto {
  filesRemoved: number
  dbRowsDeleted: number
}

export interface OrphanedFileDto {
  id: string
  filename: string
  kind: string
  sizeBytes: number
  title: string | null
  summary: string | null
  orphanedFromConnection: string
  orphanedFromConnectionId: string
  orphanedAt: number
  archivedAt: string | null
}

export interface ReattachResponse {
  succeeded: string[]
  failed: Array<{ id: string; reason: string }>
}

export async function getStorageOverview(): Promise<StorageOverviewDto> {
  return http.get('maintenance/storage-overview').json<StorageOverviewDto>()
}

export async function cleanupTrash(): Promise<CleanupStatsDto> {
  return http.post('maintenance/cleanup-trash').json<CleanupStatsDto>()
}

export async function cleanupLegacy(): Promise<CleanupStatsDto> {
  return http.post('maintenance/cleanup-legacy').json<CleanupStatsDto>()
}

export async function getOrphanedFiles(): Promise<OrphanedFileDto[]> {
  return http.get('maintenance/orphaned-files').json<OrphanedFileDto[]>()
}

export async function reattachFile(fileArtifactId: string, connectionId: string): Promise<FileArtifact> {
  return http.post(`maintenance/files/${fileArtifactId}/reattach`, {
    json: { connectionId },
  }).json<FileArtifact>()
}
