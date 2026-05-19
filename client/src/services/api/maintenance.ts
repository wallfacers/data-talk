import { http } from '@/services/http'
import type { FileArtifact } from './file-artifacts'

export type ResourceDirName = 'dashboards' | 'reports' | 'exports' | 'semantic' | 'uploads'

export interface ResourceDirSummary {
  count: number
  sizeBytes: number
}

export interface StorageOverviewDto {
  workdir: string
  totalBytes: number
  breakdown: Record<string, { bytes: number; label: string }>
  lastHousekeepingRunAt: string | null
  resourceDirectories: Record<ResourceDirName, ResourceDirSummary>
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

export interface DashboardResourceDto {
  id: string
  title: string
  filename: string
  sizeBytes: number
  widgetCount: number
  originSessionId: string | null
  createdAt: number
  updatedAt: number
}

export interface ReportResourceDto {
  id: string
  title: string
  availableFormats: string[]
  sizeBytes: number
  originSessionId: string | null
  createdAt: number
  updatedAt: number
}

export interface ExportResourceDto {
  exportId: string
  filename: string
  format: string
  sizeBytes: number
  rowCount: number
  originSessionId: string | null
  createdAt: number
  expiresAt: number
}

export interface SemanticResourceDto {
  domain: string
  connectionId: string
  connectionName: string
  status: string
  sizeBytes: number
  updatedAt: number
}

export interface UploadResourceDto {
  id: string
  filename: string
  mimeType: string
  sizeBytes: number
  originSessionId: string | null
  createdAt: number
  expiresAt: number
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

// Resource directory list endpoints
export async function getDashboards(): Promise<DashboardResourceDto[]> {
  return http.get('maintenance/dashboards').json<DashboardResourceDto[]>()
}

export async function getReports(): Promise<ReportResourceDto[]> {
  return http.get('maintenance/reports').json<ReportResourceDto[]>()
}

export async function getExports(): Promise<ExportResourceDto[]> {
  return http.get('maintenance/exports').json<ExportResourceDto[]>()
}

export async function getSemantic(): Promise<SemanticResourceDto[]> {
  return http.get('maintenance/semantic').json<SemanticResourceDto[]>()
}

export async function getUploads(): Promise<UploadResourceDto[]> {
  return http.get('maintenance/uploads').json<UploadResourceDto[]>()
}

// Resource directory delete endpoints
export async function deleteDashboard(id: string): Promise<void> {
  return http.delete(`maintenance/dashboards/${id}`).json<void>()
}

export async function deleteReport(id: string): Promise<void> {
  return http.delete(`maintenance/reports/${id}`).json<void>()
}

export async function deleteExport(exportId: string): Promise<void> {
  return http.delete(`maintenance/exports/${exportId}`).json<void>()
}

export async function deleteSemantic(domain: string, connectionId: string): Promise<void> {
  return http.delete(`maintenance/semantic/${domain}`, {
    searchParams: { connectionId },
  }).json<void>()
}

export async function deleteUpload(id: string): Promise<void> {
  return http.delete(`maintenance/uploads/${id}`).json<void>()
}

// Resource preview endpoints
export async function previewDashboard(id: string): Promise<Response> {
  return http.get(`maintenance/dashboards/${id}/preview`)
}

export async function previewReport(id: string): Promise<Response> {
  return http.get(`maintenance/reports/${id}/preview`)
}

export async function previewExport(exportId: string): Promise<{ format: string; columns: string[]; rows: unknown[][]; totalRows: number; previewRows: number }> {
  return http.get(`maintenance/exports/${exportId}/preview`).json()
}

export async function previewSemantic(domain: string, connectionId: string): Promise<Response> {
  return http.get(`maintenance/semantic/${domain}/preview`, {
    searchParams: { connectionId },
  })
}

export async function previewUpload(id: string): Promise<Response> {
  return http.get(`maintenance/uploads/${id}/preview`)
}
