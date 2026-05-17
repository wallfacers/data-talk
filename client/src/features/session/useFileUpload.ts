import { useCallback, useLayoutEffect, useRef, useState } from 'react'
import { uploadFile } from '@/services/api/file-upload'
import type { FileUploadResponse } from '@/services/api/file-upload'
import { useI18n } from '@/i18n/use-i18n'

export interface FileAttachment {
  // Stable identifier used to address this attachment across state updates.
  // Reference equality (a === attachment) breaks once setAttachments replaces the
  // matching item with a new object via spread — see useFileUpload.test.ts.
  id: string
  file: File
  status: 'pending' | 'uploading' | 'done' | 'error'
  progress: number
  response?: FileUploadResponse
  error?: string
  // Per-attachment AbortController. Created in addFiles, consumed by removeAttachment
  // to cancel in-flight fetch. Pending attachments without an in-flight request still
  // carry an unused controller — abort() is idempotent and a no-op on uninitiated fetch.
  controller?: AbortController
}

const ALLOWED_EXTENSIONS = ['.csv', '.xlsx', '.xls', '.json', '.jsonl', '.sql', '.txt', '.md', '.log', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp']
const MAX_SIZE_BYTES = 50 * 1024 * 1024 // 50MB
const MAX_CONCURRENT = 3

let attachmentSeq = 0
function newAttachmentId(): string {
  const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto
  if (c?.randomUUID) return c.randomUUID()
  attachmentSeq += 1
  return `att-${Date.now()}-${attachmentSeq}`
}

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'AbortError'
}

export function useFileUpload(sessionId: string) {
  const [attachments, setAttachments] = useState<FileAttachment[]>([])
  const { t } = useI18n()

  // Mirror of attachments for synchronous reads inside async flows (scheduleUpload,
  // uploadAll). Updated via useLayoutEffect so we never write side effects inside
  // setAttachments updater callbacks.
  const attachmentsRef = useRef<FileAttachment[]>([])
  useLayoutEffect(() => {
    attachmentsRef.current = attachments
  }, [attachments])

  // Track sessionId in a ref so scheduleUpload picks up the latest value without
  // needing to re-create with each render.
  const sessionIdRef = useRef(sessionId)
  useLayoutEffect(() => {
    sessionIdRef.current = sessionId
  }, [sessionId])

  const validateFile = (file: File): string | null => {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase()
    if (!ALLOWED_EXTENSIONS.includes(ext)) return t('chat.fileUpload.unsupportedType', { ext })
    if (file.size === 0) return t('chat.fileUpload.emptyFile')
    if (file.size > MAX_SIZE_BYTES) return t('chat.fileUpload.exceedsLimit')
    return null
  }

  // Upload a single attachment (looked up by id at call time). All setAttachments
  // updates short-circuit when the target id is no longer in the array — i.e. the
  // user already removed the chip. This is the "silent rebuke" path the spec calls
  // for in Requirement "附件删除取消进行中上传".
  const uploadOne = useCallback(async (id: string): Promise<void> => {
    const current = attachmentsRef.current.find(a => a.id === id)
    if (!current || current.status !== 'pending') return

    const controller = current.controller ?? new AbortController()
    const file = current.file

    setAttachments(prev =>
      prev.some(a => a.id === id)
        ? prev.map(a => a.id === id ? { ...a, status: 'uploading', progress: 0, controller } : a)
        : prev,
    )

    try {
      const response = await uploadFile(file, sessionIdRef.current, controller.signal)
      setAttachments(prev =>
        prev.some(a => a.id === id)
          ? prev.map(a => a.id === id ? { ...a, status: 'done', progress: 100, response } : a)
          : prev,
      )
    } catch (err) {
      if (isAbortError(err)) {
        // removeAttachment already pulled this id; nothing to do.
        return
      }
      setAttachments(prev =>
        prev.some(a => a.id === id)
          ? prev.map(a => a.id === id ? { ...a, status: 'error', error: String(err) } : a)
          : prev,
      )
    }
  }, [])

  // Drive eager upload for the supplied ids with bounded concurrency.
  // Resolves after all targeted uploads have settled (done | error | aborted).
  const scheduleUpload = useCallback(async (ids: string[]): Promise<void> => {
    if (ids.length === 0) return
    const idSet = new Set(ids)
    // Filter to ids that are still pending in the latest snapshot. We read from the ref so
    // we always pick up the freshest attachment list, including any just appended in this tick.
    const pending = attachmentsRef.current.filter(a => idSet.has(a.id) && a.status === 'pending')
    if (pending.length === 0) return

    for (let i = 0; i < pending.length; i += MAX_CONCURRENT) {
      const chunk = pending.slice(i, i + MAX_CONCURRENT)
      await Promise.all(chunk.map(a => uploadOne(a.id)))
    }
  }, [uploadOne])

  const addFiles = useCallback((files: FileList | File[]) => {
    const newAttachments: FileAttachment[] = []
    for (const file of Array.from(files)) {
      const error = validateFile(file)
      newAttachments.push({
        id: newAttachmentId(),
        file,
        status: error ? 'error' : 'pending',
        progress: 0,
        error: error ?? undefined,
        controller: error ? undefined : new AbortController(),
      })
    }
    if (newAttachments.length === 0) return
    setAttachments(prev => [...prev, ...newAttachments])

    // Trigger eager upload in the next microtask — after React commits the setState,
    // useLayoutEffect refreshes attachmentsRef, then this microtask runs scheduleUpload.
    // Using queueMicrotask avoids fetch firing inside the render cycle which would risk
    // ordering issues with the immediate removeAttachment use case (D4 in design.md).
    const idsToUpload = newAttachments
      .filter(a => a.status === 'pending')
      .map(a => a.id)
    if (idsToUpload.length > 0) {
      queueMicrotask(() => { void scheduleUpload(idsToUpload) })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scheduleUpload, t])

  const removeAttachment = useCallback((id: string) => {
    // CRITICAL: abort() MUST precede setAttachments(filter); NEVER place await
    // between them. Synchronous order ensures the fetch sees the abort signal
    // before any subsequent state read.
    const current = attachmentsRef.current.find(a => a.id === id)
    if (current?.controller && !current.controller.signal.aborted) {
      current.controller.abort()
    }
    setAttachments(prev => prev.filter(a => a.id !== id))
  }, [])

  const uploadAll = useCallback(async (): Promise<FileUploadResponse[]> => {
    // Fallback path: in normal flow eager upload has already drained pending ids.
    // If anything is still pending (e.g. validation race or someone called addFiles
    // synchronously inside submitText), trigger a final scheduleUpload.
    const pendingIds = attachmentsRef.current
      .filter(a => a.status === 'pending')
      .map(a => a.id)
    if (pendingIds.length > 0) {
      await scheduleUpload(pendingIds)
    }

    // Also wait for any in-flight 'uploading' attachments to settle. We poll the ref
    // in a microtask loop with a tight upper bound to avoid runaway loops in tests.
    let safety = 1000
    while (attachmentsRef.current.some(a => a.status === 'uploading') && safety-- > 0) {
      await new Promise<void>(resolve => { queueMicrotask(resolve) })
    }

    return attachmentsRef.current
      .filter(a => a.status === 'done' && a.response)
      .map(a => a.response!)
  }, [scheduleUpload])

  const clearDone = useCallback(() => {
    setAttachments(prev => prev.filter(a => a.status !== 'done'))
  }, [])

  // Derived state. hasUploads retains its original meaning (true while any chip is
  // mid-fetch) for backwards compat with existing tests / callers.
  const hasUploads = attachments.some(a => a.status === 'uploading')
  const uploadingCount = attachments.filter(a => a.status === 'uploading').length
  const hasInflight = attachments.some(a => a.status === 'pending' || a.status === 'uploading')
  const allDone = attachments.length > 0 && attachments.every(a => a.status === 'done')
  const completedResponses = attachments.filter(a => a.status === 'done' && a.response).map(a => a.response!)

  return {
    attachments,
    addFiles,
    removeAttachment,
    uploadAll,
    clearDone,
    hasUploads,
    hasInflight,
    uploadingCount,
    allDone,
    completedResponses,
  }
}
