import { useEffect, useState, useMemo, useCallback, type JSX } from 'react'
import { FileText, FileSpreadsheet, FileJson, ImageIcon } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Markdown } from '@/features/chat/components/markdown/markdown'
import type { FileAttachment } from '../useFileUpload'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const IMAGE_EXTENSIONS = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp'])
const MARKDOWN_EXTENSIONS = new Set(['.md', '.markdown'])
const CODE_EXTENSIONS = new Set(['.sql', '.json', '.jsonl', '.csv', '.log', '.txt'])

function getExt(filename: string): string {
  return '.' + filename.split('.').pop()?.toLowerCase()
}

function isImage(filename: string): boolean {
  return IMAGE_EXTENSIONS.has(getExt(filename))
}

function isMarkdown(filename: string): boolean {
  return MARKDOWN_EXTENSIONS.has(getExt(filename))
}

function isCode(filename: string): boolean {
  return CODE_EXTENSIONS.has(getExt(filename))
}

function fileTypeIcon(filename: string): JSX.Element {
  const ext = getExt(filename)
  if (IMAGE_EXTENSIONS.has(ext)) return <ImageIcon className="h-4 w-4" />
  if (['.csv', '.xlsx', '.xls'].includes(ext)) return <FileSpreadsheet className="h-4 w-4" />
  if (['.json', '.jsonl'].includes(ext)) return <FileJson className="h-4 w-4" />
  return <FileText className="h-4 w-4" />
}

function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsText(file)
  })
}

export function FilePreviewDialog({
  attachment,
  open,
  onOpenChange,
}: {
  attachment: FileAttachment | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useI18n()
  const [textContent, setTextContent] = useState<string | null>(null)
  const [readError, setReadError] = useState<string | null>(null)

  const file = attachment?.file ?? null
  const filename = file?.name ?? ''

  const fileTypeLabel = useMemo(() => {
    const ext = getExt(filename)
    if (IMAGE_EXTENSIONS.has(ext)) return t('chat.filePreview.image')
    if (MARKDOWN_EXTENSIONS.has(ext)) return t('chat.filePreview.markdown')
    if (CODE_EXTENSIONS.has(ext)) return t('chat.filePreview.code')
    return t('chat.filePreview.file')
  }, [filename, t])

  const imageUrl = useMemo(() => {
    if (!file || !isImage(filename)) return null
    return URL.createObjectURL(file)
  }, [file, filename])

  useEffect(() => {
    return () => {
      if (imageUrl) URL.revokeObjectURL(imageUrl)
    }
  }, [imageUrl])

  const loadText = useCallback(async () => {
    if (!file || isImage(filename)) return
    try {
      const text = await readFileAsText(file)
      setTextContent(text)
      setReadError(null)
    } catch (err) {
      setReadError(String(err))
    }
  }, [file, filename])

  useEffect(() => {
    if (open && file && !isImage(filename)) {
      loadText()
    }
    if (!open) {
      setTextContent(null)
      setReadError(null)
    }
  }, [open, file, filename, loadText])

  if (!file) return null

  const showImage = isImage(filename)
  const showMarkdown = isMarkdown(filename)
  const showCode = isCode(filename)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[80vh] max-w-3xl flex-col gap-0 p-0">
        {/* Header */}
        <div className="flex items-center gap-2 border-b px-4 py-3">
          <span className="text-text-muted">{fileTypeIcon(filename)}</span>
          <DialogTitle className="text-sm font-medium truncate flex-1">
            {filename}
          </DialogTitle>
          <span className="shrink-0 text-xs text-text-muted">
            {fileTypeLabel} · {formatSize(file.size)}
          </span>
        </div>
        <DialogDescription className="sr-only">
          {t('chat.filePreview.title', { filename })}
        </DialogDescription>

        {/* Body */}
        <div className="flex-1 overflow-auto min-h-0">
          {showImage && imageUrl && (
            <div className="flex items-center justify-center p-4">
              <img
                src={imageUrl}
                alt={filename}
                className="max-h-[65vh] max-w-full rounded-md object-contain"
              />
            </div>
          )}

          {showMarkdown && textContent !== null && (
            <div className="p-4">
              <Markdown text={textContent} variant="plain" />
            </div>
          )}

          {showCode && textContent !== null && (
            <div className="p-4">
              <pre className={cn(
                'overflow-auto rounded-lg bg-bg-subtle p-3 text-xs leading-relaxed',
                'font-mono text-text-base whitespace-pre-wrap break-all',
              )}>
                <code>{textContent}</code>
              </pre>
            </div>
          )}

          {!showImage && !showMarkdown && !showCode && textContent !== null && (
            <div className="p-4">
              <pre className="overflow-auto rounded-lg bg-bg-subtle p-3 text-xs leading-relaxed font-mono text-text-base whitespace-pre-wrap">
                {textContent}
              </pre>
            </div>
          )}

          {readError && (
            <div className="p-4 text-sm text-status-danger">
              {t('chat.filePreview.readError', { error: readError })}
            </div>
          )}

          {!showImage && textContent === null && !readError && (
            <div className="flex items-center justify-center py-12 text-sm text-text-muted">
              {t('chat.filePreview.loading')}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
