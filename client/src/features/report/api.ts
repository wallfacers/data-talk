import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  reportDetailSchema,
  reportListSchema,
  reportSystemStatusSchema,
  type ReportDetail,
  type ReportListItem,
  type ReportSystemStatus,
} from './schema'

const BASE = '/api/reports'

async function getJson<T>(url: string, parse: (raw: unknown) => T): Promise<T> {
  const resp = await fetch(url, { credentials: 'same-origin' })
  if (!resp.ok) {
    throw new Error(`Request failed: ${resp.status} ${url}`)
  }
  const raw = await resp.json()
  return parse(raw)
}

export const reportListQueryKey = (workspaceId: string, groupId?: string) =>
  ['reports', 'list', workspaceId, groupId ?? null] as const

export const reportDetailQueryKey = (id: string) => ['reports', 'detail', id] as const

export const reportSystemStatusQueryKey = ['reports', 'system-status'] as const

export function useReportList(workspaceId: string | undefined, groupId?: string) {
  return useQuery({
    queryKey: reportListQueryKey(workspaceId ?? '', groupId),
    queryFn: async () => {
      const params = new URLSearchParams()
      if (workspaceId) params.set('workspaceId', workspaceId)
      if (groupId) params.set('groupId', groupId)
      return getJson(`${BASE}?${params.toString()}`, (raw) => reportListSchema.parse(raw).items)
    },
  })
}

export function useReport(id: string | undefined) {
  return useQuery<ReportDetail>({
    queryKey: reportDetailQueryKey(id ?? ''),
    enabled: Boolean(id),
    queryFn: async () =>
      getJson(`${BASE}/${id}`, (raw) => reportDetailSchema.parse(raw)),
    refetchInterval: (query) => {
      const data = query.state.data as ReportDetail | undefined
      if (!data) return false
      if (data.pdfStatus === 'processing' || data.mdStatus === 'processing') return 3000
      return false
    },
  })
}

export function useSystemStatus() {
  return useQuery<ReportSystemStatus>({
    queryKey: reportSystemStatusQueryKey,
    queryFn: async () =>
      getJson(`${BASE}/system-status`, (raw) => reportSystemStatusSchema.parse(raw)),
    refetchInterval: (query) => {
      const data = query.state.data as ReportSystemStatus | undefined
      if (!data) return 5000
      return data.chromiumReady ? false : 5000
    },
  })
}

export function useDeleteReportMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      const resp = await fetch(`${BASE}/${id}`, { method: 'DELETE' })
      if (!resp.ok) throw new Error(`Delete failed: ${resp.status}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reports', 'list'] })
    },
  })
}

export function reportDownloadUrl(id: string, format: 'html' | 'pdf' | 'md' | 'json') {
  return `${BASE}/${id}/download/${format}`
}

export type { ReportListItem, ReportDetail, ReportSystemStatus }
