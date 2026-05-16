import { useState, useCallback } from 'react'
import { uploadFile } from '@/services/api/file-upload'
import type { FileUploadResponse } from '@/services/api/file-upload'
import { useI18n } from '@/i18n/use-i18n'

export interface FileAttachment {
  file: File
  status: 'pending' | 'uploading' | 'done' | 'error'
  progress: number
  response?: FileUploadResponse
  error?: string
}

const ALLOWED_EXTENSIONS = ['.csv', '.xlsx', '.xls', '.json', '.jsonl', '.sql', '.txt', '.md', '.log', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp']
const MAX_SIZE_BYTES = 50 * 1024 * 1024 // 50MB

export function useFileUpload(sessionId: string) {
  const [attachments, setAttachments] = useState<FileAttachment[]>([])
  const { t } = useI18n()

  const validateFile = (file: File): string | null => {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase()
    if (!ALLOWED_EXTENSIONS.includes(ext)) return t('chat.fileUpload.unsupportedType', { ext })
    if (file.size === 0) return t('chat.fileUpload.emptyFile')
    if (file.size > MAX_SIZE_BYTES) return t('chat.fileUpload.exceedsLimit')
    return null
  }

  const addFiles = useCallback((files: FileList | File[]) => {
    const newAttachments: FileAttachment[] = []
    for (const file of Array.from(files)) {
      const error = validateFile(file)
      newAttachments.push({
        file,
        status: error ? 'error' : 'pending',
        progress: 0,
        error: error ?? undefined,
      })
    }
    setAttachments(prev => [...prev, ...newAttachments])
  }, [])

  const removeAttachment = useCallback((index: number) => {
    setAttachments(prev => prev.filter((_, i) => i !== index))
  }, [])

  const uploadAll = useCallback(async () => {
    const pending = attachments.filter(a => a.status === 'pending')
    if (pending.length === 0) return

    for (const attachment of pending) {
      setAttachments(prev =>
        prev.map(a => a === attachment ? { ...a, status: 'uploading', progress: 0 } : a)
      )
      try {
        const response = await uploadFile(attachment.file, sessionId)
        setAttachments(prev =>
          prev.map(a => a === attachment ? { ...a, status: 'done', progress: 100, response } : a)
        )
      } catch (err) {
        setAttachments(prev =>
          prev.map(a => a === attachment ? { ...a, status: 'error', error: String(err) } : a)
        )
      }
    }
  }, [attachments, sessionId])

  const clearDone = useCallback(() => {
    setAttachments(prev => prev.filter(a => a.status !== 'done'))
  }, [])

  const hasUploads = attachments.some(a => a.status === 'uploading')
  const allDone = attachments.length > 0 && attachments.every(a => a.status === 'done')
  const completedResponses = attachments.filter(a => a.status === 'done' && a.response).map(a => a.response!)

  return {
    attachments,
    addFiles,
    removeAttachment,
    uploadAll,
    clearDone,
    hasUploads,
    allDone,
    completedResponses,
  }
}
