import { http } from '@/services/http'

export interface FileUploadResponse {
  fileId: string
  filename: string
  mimeType: string
  sizeBytes: number
  analysis: {
    type: string
    fullContent: boolean
    content?: string
    summary?: Record<string, unknown>
  }
}

export async function uploadFile(file: File, sessionId: string): Promise<FileUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('sessionId', sessionId)

  const response = await fetch('/api/files/upload', {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Upload failed' }))
    throw new Error(error.error || `Upload failed: ${response.status}`)
  }

  return response.json()
}

export async function deleteUploadedFile(fileId: string): Promise<void> {
  await http.delete(`files/${fileId}`)
}
