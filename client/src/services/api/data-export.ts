import { getApiBaseUrl } from '../api-prefix'

export interface ExportDataRequest {
  columns: string[]
  rows: (string | null)[][]
  format: string
  tableName: string
}

export async function exportDataFile(req: ExportDataRequest): Promise<Blob> {
  const res = await fetch(`${getApiBaseUrl()}/api/exports/data`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: 'Export failed' }))
    throw new Error((err as any).message ?? 'Export failed')
  }
  return res.blob()
}
