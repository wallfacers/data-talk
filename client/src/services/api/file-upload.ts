import { http } from '@/services/http'
import { getApiBaseUrl } from '@/services/api-prefix'

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

export async function uploadFile(
  file: File,
  sessionId: string,
  signal?: AbortSignal,
): Promise<FileUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('sessionId', sessionId)

  let response: Response
  try {
    response = await fetch(`${getApiBaseUrl()}/api/files/upload`, {
      method: 'POST',
      body: formData,
      signal,
    })
  } catch (err) {
    // AbortError 是调用方主动取消（removeAttachment → controller.abort）；上层 silent
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw err
    }
    throw err
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Upload failed' }))
    throw new Error(error.error || `Upload failed: ${response.status}`)
  }

  return response.json()
}

export async function deleteUploadedFile(fileId: string): Promise<void> {
  await http.delete(`files/${fileId}`)
}

/**
 * 返回文件内容的 GET URL。供 `<img src>` / `<a href>` 等场景直接使用；
 * 浏览器原生 HTTP 缓存（后端响应 Cache-Control: private, max-age=300）。
 */
export function getFileContentUrl(fileId: string): string {
  return `${getApiBaseUrl()}/api/files/${encodeURIComponent(fileId)}/content`
}

/**
 * 拉取文件原始字节为 Blob。对 404 / 网络异常抛出带友好 message 的 Error，
 * 供调用方（如 FilePreviewDialog）显示友好错误而非崩溃。
 */
export async function fetchFileContent(fileId: string): Promise<Blob> {
  const url = getFileContentUrl(fileId)
  let response: Response
  try {
    response = await fetch(url)
  } catch (err) {
    throw new Error(`Network error: ${err instanceof Error ? err.message : String(err)}`)
  }
  if (response.status === 404) {
    throw new Error('File not found')
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.blob()
}
