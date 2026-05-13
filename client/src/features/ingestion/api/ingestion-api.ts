import { http } from '@/services/http'

export interface MappingColumnView {
  sourcePath: string
  targetName: string
  type: string
  skip: boolean
  sampleValues: string[]
  nullable: boolean
}

export interface IngestionJobView {
  id: string
  sourceUrl: string
  status: string
  payloadFormat: string | null
  payloadArtifactId: string | null
  connectionId: string | null
  targetSchema: string | null
  targetTable: string | null
  rowCount: number | null
  rowsInserted: number | null
  bytesFetched: number | null
  mappingHash: string | null
  mapping: { mappingId: string; columns: MappingColumnView[] } | null
  createdAt: number
  updatedAt: number
  completedAt: number | null
  errorMessage: string | null
}

export interface IngestionJobsResponse {
  items: IngestionJobView[]
  total: number
}

export interface PayloadPreviewResponse {
  columns: string[]
  rows: Record<string, unknown>[]
  totalRows: number
}

export async function getIngestionJob(id: string): Promise<IngestionJobView> {
  return http.get(`ingestion/jobs/${id}`).json<IngestionJobView>()
}

export async function listIngestionJobs(params?: {
  status?: string
  connectionId?: string
  limit?: number
  offset?: number
}): Promise<IngestionJobsResponse> {
  const searchParams: Record<string, string> = {}
  if (params?.status) searchParams.status = params.status
  if (params?.connectionId) searchParams.connectionId = params.connectionId
  if (params?.limit !== undefined) searchParams.limit = String(params.limit)
  if (params?.offset !== undefined) searchParams.offset = String(params.offset)
  return http.get('ingestion/jobs', { searchParams }).json<IngestionJobsResponse>()
}

export async function getPayloadPreview(jobId: string, limit = 100): Promise<PayloadPreviewResponse> {
  return http.get(`ingestion/jobs/${jobId}/payload-preview`, { searchParams: { limit: String(limit) } }).json<PayloadPreviewResponse>()
}

export async function confirmIngestionJob(jobId: string): Promise<{
  tokenId: string
  expiresAt: number
  mappingHash: string
}> {
  return http.post(`ingestion/jobs/${jobId}/confirm`, { json: {} })
    .json<{ tokenId: string; expiresAt: number; mappingHash: string }>()
}

export async function cancelIngestionJob(jobId: string): Promise<void> {
  await http.post(`ingestion/jobs/${jobId}/cancel`)
}

export async function deleteIngestionJob(id: string): Promise<void> {
  await http.delete(`ingestion/jobs/${id}`)
}

export const ingestionJobsKey = ['ingestion-jobs'] as const
export const ingestionJobKey = (id: string) => ['ingestion-jobs', id] as const
export const payloadPreviewKey = (id: string) => ['ingestion-jobs', id, 'payload-preview'] as const
